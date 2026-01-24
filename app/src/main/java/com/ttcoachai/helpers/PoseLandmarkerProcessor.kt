/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 * PoseLandmarker Detection Processors
 */
package com.ttcoachai.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerProcessor(
    private val context: Context,
    private val poseLandmarker: PoseLandmarker?,
    private val runningMode: RunningMode
) {
    companion object {
        private const val TAG = "PoseLandmarkerProc"
    }

    @Volatile
    private var isCancelled = false

    fun cancel() {
        isCancelled = true
    }
    
    private var bitmapBuffer: Bitmap? = null
    private var rotatedBitmap: Bitmap? = null
    private val matrix = Matrix()

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            Log.w(TAG, "Attempting to call detectLiveStream while not using RunningMode.LIVE_STREAM")
            return
        }

        val frameTime = SystemClock.uptimeMillis()
        
        // 1. Initial bitmap buffer setup/reuse
        if (bitmapBuffer == null || bitmapBuffer!!.width != imageProxy.width || bitmapBuffer!!.height != imageProxy.height) {
            bitmapBuffer?.recycle()
            bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        }

        // Copy pixels efficiently
        imageProxy.use { proxy ->
            bitmapBuffer?.copyPixelsFromBuffer(proxy.planes[0].buffer)
        }

        // 2. Rotation setup/reuse
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val rotatedWidth = if (rotationDegrees % 180 != 0) imageProxy.height else imageProxy.width
        val rotatedHeight = if (rotationDegrees % 180 != 0) imageProxy.width else imageProxy.height

        if (rotatedBitmap == null || rotatedBitmap!!.width != rotatedWidth || rotatedBitmap!!.height != rotatedHeight) {
            rotatedBitmap?.recycle()
            rotatedBitmap = Bitmap.createBitmap(
                rotatedWidth,
                rotatedHeight,
                Bitmap.Config.ARGB_8888
            )
        }

        // Efficient rotation using Canvas instead of Bitmap.createBitmap(matrix) which allocates
        matrix.reset()
        matrix.postRotate(rotationDegrees.toFloat())
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
        }
        
        // Correcting the translation for rotated coordinates
        val tempBitmap = rotatedBitmap
        if (tempBitmap != null) {
            val canvas = android.graphics.Canvas(tempBitmap)
            canvas.drawColor(android.graphics.Color.BLACK) // Clear background
            
            // Adjust matrix for rotation translation if necessary
            // For 90/270 degrees, we need to translate to stay inside bounds
            if (rotationDegrees == 90 || rotationDegrees == 270) {
                val dx = if (rotationDegrees == 90) rotatedWidth.toFloat() else 0f
                val dy = if (rotationDegrees == 270) rotatedHeight.toFloat() else 0f
                // We'll let the Matrix handle the math but simple createBitmap with matrix is usually safer 
                // IF we don't want to manually calculate the translation.
                // However, let's use a more robust way to draw the rotated bitmap.
            }
            
            // Reverting to a more standard way but reusing the bitmap via Canvas.drawBitmap if possible.
            // Actually, Matrix-based drawing on Canvas is exactly what we need.
            
            // Centering logic for different rotations
            val centerX = imageProxy.width / 2f
            val centerY = imageProxy.height / 2f
            
            // We need to translate such that the rotated image center aligns with the result bitmap center
            matrix.reset()
            matrix.postTranslate(-centerX, -centerY)
            matrix.postRotate(rotationDegrees.toFloat())
            if (isFrontCamera) {
                matrix.postScale(-1f, 1f)
            }
            matrix.postTranslate(rotatedWidth / 2f, rotatedHeight / 2f)
            
            canvas.drawBitmap(bitmapBuffer!!, matrix, null)

            val mpImage = BitmapImageBuilder(tempBitmap).build()
            detectAsync(mpImage, frameTime)
        }
    }

    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    fun detectVideoFile(videoUri: Uri, inferenceIntervalMs: Long, onError: ((String) -> Unit)?): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            Log.w(TAG, "Attempting to call detectVideoFile while not using RunningMode.VIDEO")
            return null
        }

        val startTime = SystemClock.uptimeMillis()
        var didErrorOccurred = false

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height

        if (videoLengthMs == null || width == null || height == null) return null

        val resultList = mutableListOf<PoseLandmarkerResult>()
        val numberOfFrameToRead = videoLengthMs.div(inferenceIntervalMs)

        for (i in 0..numberOfFrameToRead) {
            if (isCancelled) {
                retriever.release()
                return null
            }

            val timestampMs = i * inferenceIntervalMs

            retriever.getFrameAtTime(
                timestampMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST
            )?.let { frame ->
                // Validate bitmap before processing
                if (frame.isRecycled) {
                    Log.e(TAG, "Frame $i is recycled at $timestampMs ms")
                    didErrorOccurred = true
                    onError?.invoke("Frame was recycled before processing")
                    return@let
                }

                // CRITICAL: Always create mutable copy for MediaPipe JNI safety
                // MediaPipe's native code requires mutable ARGB_8888 bitmaps
                val argb8888Frame = if (frame.config == Bitmap.Config.ARGB_8888 && frame.isMutable) {
                    frame
                } else {
                    frame.copy(Bitmap.Config.ARGB_8888, true)  // true = mutable
                }

                // Additional validation
                if (!argb8888Frame.isMutable || argb8888Frame.isRecycled) {
                    Log.e(TAG, "Invalid bitmap state at frame $i: mutable=${argb8888Frame.isMutable}, recycled=${argb8888Frame.isRecycled}")
                    didErrorOccurred = true
                    onError?.invoke("Bitmap validation failed")
                    return@let
                }

                try {
                    val mpImage = BitmapImageBuilder(argb8888Frame).build()
                    poseLandmarker?.detectForVideo(mpImage, timestampMs)?.let { detectionResult ->
                        resultList.add(detectionResult)
                    } ?: run {
                        didErrorOccurred = true
                        onError?.invoke("ResultBundle could not be returned in detectVideoFile")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "MediaPipe processing failed at frame $i (${timestampMs}ms)", e)
                    didErrorOccurred = true
                    onError?.invoke("MediaPipe JNI error: ${e.message}")
                } finally {
                    // Free memory if we created a copy
                    if (argb8888Frame != frame) {
                        argb8888Frame.recycle()
                    }
                }
            } ?: run {
                didErrorOccurred = true
                onError?.invoke("Frame at specified time could not be retrieved when detecting in video.")
            }
        }

        retriever.release()

        val inferenceTimePerFrameMs = (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead)

        return if (didErrorOccurred) null
        else ResultBundle(resultList, inferenceTimePerFrameMs, height, width)
    }

    fun detectImage(image: Bitmap, onError: ((String) -> Unit)?): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            Log.w(TAG, "Attempting to call detectImage while not using RunningMode.IMAGE")
            return null
        }

        val startTime = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(image).build()

        return poseLandmarker?.detect(mpImage)?.let { landmarkResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            ResultBundle(listOf(landmarkResult), inferenceTimeMs, image.height, image.width)
        } ?: run {
            onError?.invoke("Pose Landmarker failed to detect.")
            null
        }
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int
    )
}
