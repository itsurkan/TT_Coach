/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.poselandmarker.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Handles image and video processing for GalleryFragment
 */
class GalleryMediaProcessor(
    private val context: Context,
    private val onImageLoaded: (Bitmap) -> Unit,
    private val onImageResults: (PoseLandmarkerHelper.ResultBundle) -> Unit,
    private val onVideoResults: (PoseLandmarkerHelper.ResultBundle) -> Unit,
    private val onError: (String, Int) -> Unit
) {
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private lateinit var backgroundExecutor: ScheduledExecutorService

    companion object {
        private const val TAG = "GalleryMediaProcessor"
        const val VIDEO_INTERVAL_MS = 300L
    }

    /**
     * Process image from URI and run pose detection
     */
    fun processImage(
        uri: Uri,
        minPoseDetectionConfidence: Float,
        minPoseTrackingConfidence: Float,
        minPosePresenceConfidence: Float,
        currentDelegate: Int
    ) {
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()

        val bitmap = loadBitmapFromUri(uri) ?: run {
            Log.e(TAG, "Failed to load bitmap from URI")
            return
        }

        onImageLoaded(bitmap)

        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = context,
                runningMode = RunningMode.IMAGE,
                minPoseDetectionConfidence = minPoseDetectionConfidence,
                minPoseTrackingConfidence = minPoseTrackingConfidence,
                minPosePresenceConfidence = minPosePresenceConfidence,
                currentDelegate = currentDelegate
            )

            poseLandmarkerHelper.detectImage(bitmap)?.let { result ->
                onImageResults(result)
            } ?: run {
                Log.e(TAG, "Error running pose landmarker on image.")
            }

            poseLandmarkerHelper.clearPoseLandmarker()
        }
    }

    /**
     * Process video from URI and run pose detection
     */
    fun processVideo(
        uri: Uri,
        minPoseDetectionConfidence: Float,
        minPoseTrackingConfidence: Float,
        minPosePresenceConfidence: Float,
        currentDelegate: Int
    ) {
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()

        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = context,
                runningMode = RunningMode.VIDEO,
                minPoseDetectionConfidence = minPoseDetectionConfidence,
                minPoseTrackingConfidence = minPoseTrackingConfidence,
                minPosePresenceConfidence = minPosePresenceConfidence,
                currentDelegate = currentDelegate
            )

            poseLandmarkerHelper.detectVideoFile(uri, VIDEO_INTERVAL_MS)
                ?.let { resultBundle ->
                    onVideoResults(resultBundle)
                }
                ?: run {
                    Log.e(TAG, "Error running pose landmarker on video.")
                }

            poseLandmarkerHelper.clearPoseLandmarker()
        }
    }

    /**
     * Schedule video result display at fixed intervals
     */
    fun scheduleVideoResultDisplay(
        result: PoseLandmarkerHelper.ResultBundle,
        videoStartTimeMs: Long,
        isVideoPlaying: () -> Boolean,
        onFrameUpdate: (Int) -> Unit
    ) {
        backgroundExecutor.scheduleAtFixedRate(
            {
                val videoElapsedTimeMs = SystemClock.uptimeMillis() - videoStartTimeMs
                val resultIndex = videoElapsedTimeMs.div(VIDEO_INTERVAL_MS).toInt()

                if (resultIndex >= result.results.size || !isVideoPlaying()) {
                    // Video playback finished, stop drawing
                    backgroundExecutor.shutdown()
                } else {
                    onFrameUpdate(resultIndex)
                }
            },
            0,
            VIDEO_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * Load bitmap from URI based on Android version
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap: ${e.message}")
            null
        }
    }

    /**
     * Check if executor is active
     */
    fun isExecutorActive(): Boolean {
        return ::backgroundExecutor.isInitialized && !backgroundExecutor.isShutdown
    }

    /**
     * Shutdown executor
     */
    fun shutdown() {
        if (::backgroundExecutor.isInitialized && !backgroundExecutor.isShutdown) {
            backgroundExecutor.shutdown()
        }
    }
}
