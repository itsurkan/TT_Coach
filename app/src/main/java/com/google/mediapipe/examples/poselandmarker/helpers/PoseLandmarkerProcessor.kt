/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 * PoseLandmarker Detection Processors
 */
package com.google.mediapipe.examples.poselandmarker.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
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
    
    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        require(runningMode == RunningMode.LIVE_STREAM) {
            "Attempting to call detectLiveStream while not using RunningMode.LIVE_STREAM"
        }

        val frameTime = SystemClock.uptimeMillis()
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )

        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
            }
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        detectAsync(mpImage, frameTime)
    }

    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    fun detectVideoFile(videoUri: Uri, inferenceIntervalMs: Long, onError: ((String) -> Unit)?): ResultBundle? {
        require(runningMode == RunningMode.VIDEO) {
            "Attempting to call detectVideoFile while not using RunningMode.VIDEO"
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
            val timestampMs = i * inferenceIntervalMs

            retriever.getFrameAtTime(
                timestampMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST
            )?.let { frame ->
                val argb8888Frame = if (frame.config == Bitmap.Config.ARGB_8888) frame
                else frame.copy(Bitmap.Config.ARGB_8888, false)

                val mpImage = BitmapImageBuilder(argb8888Frame).build()
                poseLandmarker?.detectForVideo(mpImage, timestampMs)?.let { detectionResult ->
                    resultList.add(detectionResult)
                } ?: run {
                    didErrorOccurred = true
                    onError?.invoke("ResultBundle could not be returned in detectVideoFile")
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
        require(runningMode == RunningMode.IMAGE) {
            "Attempting to call detectImage while not using RunningMode.IMAGE"
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
