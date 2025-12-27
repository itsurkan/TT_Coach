/*
 * AI Coach for Table Tennis
 * Video Player Manager - Handles video playback and pose detection
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
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
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    
    companion object {
        private const val TAG = "VideoPlayerManager"
        private const val FRAME_INTERVAL_MS = 100L // Process frames every 100ms
    }
    
    fun playVideoWithPoseDetection(videoResId: Int) {
        val videoUri = Uri.parse("android.resource://${context.packageName}/$videoResId")
        videoView.setVideoURI(videoUri)
        
        // Set up video playback
        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.setVolume(0f, 0f) // Mute audio
            mediaPlayer.isLooping = true // Loop video
            
            // Get video dimensions
            videoWidth = mediaPlayer.videoWidth
            videoHeight = mediaPlayer.videoHeight
            
            Log.d(TAG, "Video dimensions: ${videoWidth}x${videoHeight}")
            
            // Scale VideoView to fill container
            scaleVideoViewToFill()
            
            // Start video immediately
            videoView.start()
            videoView.visibility = View.VISIBLE
            onStatusChange("▶️ Video playing - processing poses...")
            
            // Initialize pose detector and start processing
            initializePoseDetector(videoUri)
        }
        
        videoView.setOnErrorListener { _, what, extra ->
            onStatusChange("❌ Error loading video: $what, $extra")
            false
        }
        
        onStatusChange("⏳ Loading video...")
    }
    
    private fun scaleVideoViewToFill() {
        videoView.post {
            val viewWidth = videoView.width
            val viewHeight = videoView.height
            
            if (videoWidth > 0 && videoHeight > 0 && viewWidth > 0 && viewHeight > 0) {
                val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
                val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
                
                val scaleX: Float
                val scaleY: Float
                
                if (videoRatio > viewRatio) {
                    // Video is wider than view - scale to fill height
                    scaleY = viewHeight.toFloat() / videoHeight.toFloat()
                    scaleX = scaleY
                } else {
                    // Video is taller than view - scale to fill width
                    scaleX = viewWidth.toFloat() / videoWidth.toFloat()
                    scaleY = scaleX
                }
                
                // Apply scaling to fill the view
                videoView.scaleX = scaleX * videoRatio / viewRatio
                videoView.scaleY = scaleY * viewRatio / videoRatio
                
                Log.d(TAG, "View: ${viewWidth}x${viewHeight}, Scale: ${videoView.scaleX}x${videoView.scaleY}")
            }
        }
    }
    
    private fun initializePoseDetector(videoUri: Uri) {
        backgroundExecutor = Executors.newScheduledThreadPool(2)
        
        // Initialize PoseLandmarkerHelper in background
        backgroundExecutor?.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = context,
                runningMode = RunningMode.IMAGE, // Use IMAGE mode for frame-by-frame
                minPoseDetectionConfidence = 0.5f,
                minPoseTrackingConfidence = 0.5f,
                minPosePresenceConfidence = 0.5f,
                currentDelegate = 0 // CPU
            )
            
            // Start processing frames
            startFrameProcessing(videoUri)
        }
    }
    
    private fun startFrameProcessing(videoUri: Uri) {
        val retriever = MediaMetadataRetriever()
        
        try {
            retriever.setDataSource(context, videoUri)
            
            // Schedule frame extraction and processing
            backgroundExecutor?.scheduleAtFixedRate(
                {
                    try {
                        if (!videoView.isPlaying || videoView.visibility != View.VISIBLE) {
                            return@scheduleAtFixedRate
                        }
                        
                        val currentPositionMs = videoView.currentPosition.toLong() * 1000 // Convert to microseconds
                        val frame = retriever.getFrameAtTime(currentPositionMs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        
                        frame?.let { bitmap ->
                            // Detect pose in current frame
                            poseLandmarkerHelper?.detectImage(bitmap)?.let { result ->
                                (context as? android.app.Activity)?.runOnUiThread {
                                    if (result.results.isNotEmpty()) {
                                        overlayView.setResults(
                                            result.results[0],
                                            videoHeight, // Use actual video dimensions
                                            videoWidth,
                                            RunningMode.IMAGE
                                        )
                                        overlayView.invalidate()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing frame: ${e.message}")
                    }
                },
                0,
                FRAME_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up frame processing: ${e.message}")
            (context as? android.app.Activity)?.runOnUiThread {
                onStatusChange("❌ Failed to process video")
            }
        }
    }
    
    fun release() {
        backgroundExecutor?.shutdown()
        poseLandmarkerHelper?.clearPoseLandmarker()
    }
}
