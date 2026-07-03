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
import com.ttcoachai.shared.models.BallDetection
import com.ttcoachai.shared.models.RegionOfInterest
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.tracking.BallDetector
import com.ttcoachai.tracking.ROIManager

class PoseLandmarkerProcessor(
    private val context: Context,
    private val poseLandmarker: PoseLandmarker?,
    private val runningMode: RunningMode,
    /** Optional callback invoked on the background executor after each frame's ball detection. */
    private val onBallDetected: ((BallDetection) -> Unit)? = null
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

    private var lastProcessedFrameTime = 0L
    private val frameIntervalMs: Long = (1000L / SettingsManager(context).getBallDetectionFps())

    // Ball detection components — lazily initialised on first use
    private val ballDetector: BallDetector by lazy { BallDetector() }
    private val roiManager: ROIManager by lazy { ROIManager() }
    private var currentRoi: RegionOfInterest? = null
    private var frameIndex = 0

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            Log.w(TAG, "Attempting to call detectLiveStream while not using RunningMode.LIVE_STREAM")
            return
        }

        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastProcessedFrameTime < frameIntervalMs) {
            imageProxy.close()
            return
        }
        lastProcessedFrameTime = currentTime

        val frameTime = SystemClock.uptimeMillis()
        val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000L  // ns → ms

        // Use safe translation to bitmap (handles YUV to RGBA correctly)
        val bitmap = try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert image to bitmap", e)
            imageProxy.close()
            return
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

        val tempBitmap = rotatedBitmap ?: return

        // 3. Robust Rotation and Centering logic
        val centerX = imageProxy.width / 2f
        val centerY = imageProxy.height / 2f

        matrix.reset()
        matrix.postTranslate(-centerX, -centerY)
        matrix.postRotate(rotationDegrees.toFloat())

        if (isFrontCamera) {
            matrix.postScale(-1f, 1f)
        }

        matrix.postTranslate(rotatedWidth / 2f, rotatedHeight / 2f)

        val canvas = android.graphics.Canvas(tempBitmap)
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawBitmap(bitmap, matrix, null)

        // Recycle the intermediate bitmap from toBitmap()
        bitmap.recycle()
        imageProxy.close()

        // 4. Ball detection (after rotation, before MediaPipe async call — research R6)
        if (onBallDetected != null) {
            val roi = currentRoi ?: roiManager.createDefault(rotatedWidth, rotatedHeight)
                .also { currentRoi = it }
            val currentFrameIndex = frameIndex++
            val ballResult = ballDetector.detect(tempBitmap, roi, currentFrameIndex, timestampMs)
            onBallDetected.invoke(ballResult)
        }

        try {
            val mpImage = BitmapImageBuilder(tempBitmap).build()
            detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe JNI Error in detectLiveStream", e)
            // Fallback: Notify app of processing failure without crashing
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

            try {
                retriever.getFrameAtTime(
                    timestampMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )?.let { frame ->
                    // Validate bitmap before processing
                    if (frame.isRecycled) {
                        Log.e(TAG, "Frame $i is recycled at $timestampMs ms")
                        didErrorOccurred = true
                        onError?.invoke("Frame was recycled before processing")
                        return@let
                    }

                    // CRITICAL: Always create mutable copy for MediaPipe JNI safety
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
                            Log.w(TAG, "Pose detection returned null for frame $i")
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
                    Log.w(TAG, "Could not retrieve frame $i at ${timestampMs}ms, skipping")
                }
            } catch (e: Exception) {
                Log.e(TAG, "System error during frame extraction at $timestampMs ms", e)
                // Don't stop the whole process for one frame extraction error unless it's consistent
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

    fun release() {
        bitmapBuffer?.recycle()
        bitmapBuffer = null
        rotatedBitmap?.recycle()
        rotatedBitmap = null
        if (onBallDetected != null) {
            ballDetector.release()
        }
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int
    )
}
