/*
 * AI Coach for Table Tennis
 * Video Player Manager - Handles video playback and pose detection
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.content.Context
import android.net.Uri
import android.os.SystemClock
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
    
    fun playVideoWithPoseDetection(videoResId: Int) {
        val videoUri = Uri.parse("android.resource://${context.packageName}/$videoResId")
        videoView.setVideoURI(videoUri)
        
        // Set up video playback
        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.setVolume(0f, 0f) // Mute audio
            
            // Scale video to fill width
            val videoWidth = mediaPlayer.videoWidth
            val videoHeight = mediaPlayer.videoHeight
            val viewWidth = videoView.width
            val viewHeight = videoView.height
            
            if (videoWidth > 0 && videoHeight > 0 && viewWidth > 0 && viewHeight > 0) {
                val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
                val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
                
                if (videoRatio < viewRatio) {
                    val scale = viewWidth.toFloat() / videoWidth.toFloat()
                    videoView.scaleX = scale
                    videoView.scaleY = scale
                }
            }
        }
        
        videoView.setOnErrorListener { _, what, extra ->
            onStatusChange("❌ Error loading video: $what, $extra")
            false
        }
        
        // Process video with pose detection
        processVideoWithPoseDetection(videoUri)
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
                onStatusChange("🎬 Processing video with pose detection...")
            }
            
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
        onStatusChange("✅ Video processed - ${result.results.size} frames with poses")
        
        videoView.start()
        val videoStartTimeMs = SystemClock.uptimeMillis()
        
        backgroundExecutor?.scheduleAtFixedRate(
            {
                (context as? android.app.Activity)?.runOnUiThread {
                    val videoElapsedTimeMs = SystemClock.uptimeMillis() - videoStartTimeMs
                    val resultIndex = videoElapsedTimeMs.div(VIDEO_INTERVAL_MS).toInt()
                    
                    if (resultIndex >= result.results.size || videoView.visibility == View.GONE) {
                        // Video playback finished, loop it
                        if (videoView.isPlaying) {
                            videoView.seekTo(0)
                        }
                    } else {
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
