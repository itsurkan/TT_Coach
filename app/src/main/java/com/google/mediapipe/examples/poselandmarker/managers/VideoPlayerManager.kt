/*
 * AI Coach for Table Tennis
 * Video Player Manager - Handles video playback and pose detection
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.VideoView
import com.google.mediapipe.examples.poselandmarker.OverlayView
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class VideoPlayerManager(
    private val context: Context,
    private val videoView: VideoView,
    private val overlayView: OverlayView,
    private val onStatusChange: (String) -> Unit
) {
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var backgroundExecutor: ScheduledExecutorService? = null
    
    companion object {
        private const val TAG = "VideoPlayerManager"
        private const val VIDEO_INTERVAL_MS = 300L
    }
    
    fun playVideoWithPoseDetection(assetPath: String) {
        try {
            // Copy asset to cache for VideoView
            val cacheFile = java.io.File(context.cacheDir, assetPath.replace("/", "_"))
            context.assets.open(assetPath).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val videoUri = Uri.fromFile(cacheFile)

            // Set up video playback
            with(videoView) {
                setVideoPath(cacheFile.absolutePath)
                setOnPreparedListener {
                    it.setVolume(0f, 0f)
                    it.isLooping = true
                }
                requestFocus()
            }

            videoView.setOnErrorListener { _, what, extra ->
                onStatusChange("❌ Error loading video: $what, $extra")
                false
            }

            onStatusChange("⏳ Processing video...")
            processVideoWithPoseDetection(videoUri)
        } catch (e: Exception) {
            onStatusChange("❌ Error loading video: ${e.message}")
        }
    }
    
    private fun processVideoWithPoseDetection(uri: Uri) {
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        
        backgroundExecutor?.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = context,
                runningMode = RunningMode.VIDEO,
                minPoseDetectionConfidence = 0.5f,
                minPoseTrackingConfidence = 0.5f,
                minPosePresenceConfidence = 0.5f,
                currentDelegate = 0 // CPU
            )
            
            (context as? android.app.Activity)?.runOnUiThread {
                onStatusChange("🎬 Analyzing video frames...")
            }
            
            // Process entire video first (MediaPipe standard approach)
            poseLandmarkerHelper?.detectVideoFile(uri, VIDEO_INTERVAL_MS)
                ?.let { resultBundle ->
                    (context as? android.app.Activity)?.runOnUiThread { 
                        displayVideoResult(resultBundle)
                    }
                }
                ?: run { 
                    Log.e(TAG, "Error running pose landmarker on video")
                    (context as? android.app.Activity)?.runOnUiThread {
                        onStatusChange("❌ Failed to detect poses in video")
                    }
                }
        }
    }
    
    private fun displayVideoResult(result: PoseLandmarkerHelper.ResultBundle) {
        videoView.visibility = View.VISIBLE
        onStatusChange("✅ Video processed - ${result.results.size} frames")

        videoView.start()

        backgroundExecutor?.scheduleAtFixedRate(
            {
                (context as? android.app.Activity)?.runOnUiThread {
                    if (videoView.visibility == View.GONE) {
                        // Video view is hidden, stop updating
                        backgroundExecutor?.shutdown()
                        return@runOnUiThread
                    }

                    // Use actual video position for accurate sync
                    val currentPositionMs = videoView.currentPosition.toLong()
                    val resultIndex = (currentPositionMs / VIDEO_INTERVAL_MS).toInt()
                    
                    if (resultIndex >= result.results.size) {
                        // Reached end of results - stop updating
                        // The video will continue looping but poses won't update
                        // This matches MediaPipe sample behavior
                        backgroundExecutor?.shutdown()
                    } else {
                        // Update overlay with current frame's pose
                        overlayView.setResults(
                            result.results[resultIndex],
                            result.inputImageHeight,
                            result.inputImageWidth,
                            RunningMode.VIDEO
                        )
                    }
                }
            },
            0,
            VIDEO_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }
    
    fun release() {
        backgroundExecutor?.shutdown()
        poseLandmarkerHelper?.clearPoseLandmarker()
    }
}
