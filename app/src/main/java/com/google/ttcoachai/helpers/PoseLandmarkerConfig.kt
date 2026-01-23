/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 * PoseLandmarker Configuration Helper
 */
package com.ttcoachai.helpers

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

class PoseLandmarkerConfig(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentModel: Int = MODEL_POSE_LANDMARKER_FULL,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE
) {
    
    fun createPoseLandmarker(
        context: Context,
        onResult: ((com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult, com.google.mediapipe.framework.image.MPImage) -> Unit)? = null,
        onError: ((RuntimeException) -> Unit)? = null
    ): PoseLandmarker? {
        val baseOptionBuilder = BaseOptions.builder()

        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
        }

        val modelName = when (currentModel) {
            MODEL_POSE_LANDMARKER_FULL -> "pose_landmarker_full.task"
            MODEL_POSE_LANDMARKER_LITE -> "pose_landmarker_lite.task"
            MODEL_POSE_LANDMARKER_HEAVY -> "pose_landmarker_heavy.task"
            else -> "pose_landmarker_full.task"
        }

        baseOptionBuilder.setModelAssetPath(modelName)

        // Validate running mode
        if (runningMode == RunningMode.LIVE_STREAM && onResult == null) {
            throw IllegalStateException(
                "Result listener must be set when runningMode is LIVE_STREAM."
            )
        }

        return try {
            val baseOptions = baseOptionBuilder.build()
            val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                .setMinTrackingConfidence(minPoseTrackingConfidence)
                .setMinPosePresenceConfidence(minPosePresenceConfidence)
                .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM && onResult != null && onError != null) {
                optionsBuilder
                    .setResultListener(onResult)
                    .setErrorListener(onError)
            }

            PoseLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaPipe failed to load the task with error: ${e.message}")
            null
        } catch (e: RuntimeException) {
            Log.e(TAG, "Pose Landmarker failed to load model with error: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "PoseLandmarkerConfig"
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val MODEL_POSE_LANDMARKER_FULL = 0
        const val MODEL_POSE_LANDMARKER_LITE = 1
        const val MODEL_POSE_LANDMARKER_HEAVY = 2
        const val GPU_ERROR = 1
        const val OTHER_ERROR = 0
    }
}
