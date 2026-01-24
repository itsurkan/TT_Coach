/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 * PoseLandmarker Helper - Main interface
 */
package com.ttcoachai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.MPImage
import com.ttcoachai.helpers.PoseLandmarkerConfig
import com.ttcoachai.helpers.PoseLandmarkerProcessor
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = PoseLandmarkerConfig.DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = PoseLandmarkerConfig.DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = PoseLandmarkerConfig.DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentModel: Int = PoseLandmarkerConfig.MODEL_POSE_LANDMARKER_FULL,
    var currentDelegate: Int = PoseLandmarkerConfig.DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val poseLandmarkerHelperListener: LandmarkerListener? = null
) {
    private var poseLandmarker: PoseLandmarker? = null
    private var processor: PoseLandmarkerProcessor? = null
    private val config = PoseLandmarkerConfig(
        minPoseDetectionConfidence,
        minPoseTrackingConfidence,
        minPosePresenceConfidence,
        currentModel,
        currentDelegate,
        runningMode
    )

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        processor?.cancel()
        processor = null
        poseLandmarker?.close()
        poseLandmarker = null
    }

    fun isClose(): Boolean = poseLandmarker == null

    fun setupPoseLandmarker() {
        poseLandmarker = config.createPoseLandmarker(
            context,
            onResult = ::returnLivestreamResult,
            onError = ::returnLivestreamError
        )
        
        if (poseLandmarker == null) {
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for details",
                if (currentDelegate == PoseLandmarkerConfig.DELEGATE_GPU) 
                    PoseLandmarkerConfig.GPU_ERROR 
                else 
                    PoseLandmarkerConfig.OTHER_ERROR
            )
        } else {
            processor = PoseLandmarkerProcessor(context, poseLandmarker, runningMode)
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        processor?.detectLiveStream(imageProxy, isFrontCamera)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        processor?.detectAsync(mpImage, frameTime)
    }

    fun detectVideoFile(videoUri: Uri, inferenceIntervalMs: Long): ResultBundle? {
        return processor?.detectVideoFile(
            videoUri,
            inferenceIntervalMs,
            onError = { error -> poseLandmarkerHelperListener?.onError(error) }
        )?.let { bundle ->
            ResultBundle(
                bundle.results,
                bundle.inferenceTime,
                bundle.inputImageHeight,
                bundle.inputImageWidth
            )
        }
    }

    fun detectImage(image: Bitmap): ResultBundle? {
        return processor?.detectImage(
            image,
            onError = { error -> poseLandmarkerHelperListener?.onError(error) }
        )?.let { bundle ->
            ResultBundle(
                bundle.results,
                bundle.inferenceTime,
                bundle.inputImageHeight,
                bundle.inputImageWidth
            )
        }
    }

    private fun returnLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        poseLandmarkerHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        poseLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"
        const val DELEGATE_CPU = PoseLandmarkerConfig.DELEGATE_CPU
        const val DELEGATE_GPU = PoseLandmarkerConfig.DELEGATE_GPU
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = PoseLandmarkerConfig.DEFAULT_POSE_DETECTION_CONFIDENCE
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = PoseLandmarkerConfig.DEFAULT_POSE_TRACKING_CONFIDENCE
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = PoseLandmarkerConfig.DEFAULT_POSE_PRESENCE_CONFIDENCE
        const val DEFAULT_NUM_POSES = 1
        const val OTHER_ERROR = PoseLandmarkerConfig.OTHER_ERROR
        const val GPU_ERROR = PoseLandmarkerConfig.GPU_ERROR
        const val MODEL_POSE_LANDMARKER_FULL = PoseLandmarkerConfig.MODEL_POSE_LANDMARKER_FULL
        const val MODEL_POSE_LANDMARKER_LITE = PoseLandmarkerConfig.MODEL_POSE_LANDMARKER_LITE
        const val MODEL_POSE_LANDMARKER_HEAVY = PoseLandmarkerConfig.MODEL_POSE_LANDMARKER_HEAVY
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
