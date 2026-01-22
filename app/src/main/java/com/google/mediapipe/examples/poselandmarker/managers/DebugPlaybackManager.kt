/*
 * AI Coach for Table Tennis
 * Debug Playback Manager - Manages video playback, seeking, and speed control
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityDebugBinding
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
import com.google.mediapipe.examples.poselandmarker.processors.VideoDebugProcessor
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator

class DebugPlaybackManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityDebugBinding,
    private val videoDebugProcessor: VideoDebugProcessor,
    private val uiController: DebugUIController,
    private val feedbackGenerator: FeedbackGenerator
) {
    private var mediaPlayer: MediaPlayer? = null
    private var frameRetriever: MediaMetadataRetriever? = null
    private var isPlaying = false
    private var playbackSpeed = 1.0f
    private var videoDurationMs = 0L
    private var lastSeekPositionMs = 0
    private var previousPhase: StrokePhase = StrokePhase.READY

    companion object {
        private const val TAG = "DebugPlaybackManager"
    }

    fun setMediaPlayer(mp: MediaPlayer?) {
        mediaPlayer = mp
    }

    fun setFrameRetriever(retriever: MediaMetadataRetriever?) {
        frameRetriever = retriever
    }

    fun setVideoDuration(durationMs: Long) {
        videoDurationMs = durationMs
        uiController.setVideoDuration(durationMs)
        uiController.setupSeekBar(durationMs.toInt())
    }

    fun isPlaying(): Boolean = isPlaying

    fun togglePlayPause() {
        if (isPlaying) pausePlayback() else startPlayback()
    }

    fun startPlayback() {
        isPlaying = true
        uiController.updatePlaybackButton(true)
        uiController.showVideoView()
        binding.videoView.seekTo(lastSeekPositionMs)
        binding.videoView.start()

        videoDebugProcessor.scheduleResultDisplay(
            getVideoPositionMs = { binding.videoView.currentPosition },
            isVideoPlaying = { isPlaying && binding.videoView.isPlaying },
            onFrameUpdate = { resultIndex, landmarks, analysisResult ->
                activity.runOnUiThread {
                    // Use phase-aware overlay update for stroke detection visualization
                    uiController.updatePoseOverlayWithPhase(resultIndex, landmarks)
                    uiController.updateFrameAnalysisUI(resultIndex, analysisResult)
                    val currentPosition = binding.videoView.currentPosition
                    uiController.updateSeekBar(currentPosition)
                    if (currentPosition >= videoDurationMs - 100) pausePlayback()
                    
                    // Audio feedback on phase transitions - Removed to avoid noise
                    if (videoDebugProcessor.hasStrokeDetection()) {
                        val currentPhase = videoDebugProcessor.getStrokePhaseForFrame(resultIndex)
                        if (currentPhase != previousPhase) {
                            previousPhase = currentPhase
                        }
                    }
                }
            }
        )
    }

    fun pausePlayback() {
        isPlaying = false
        videoDebugProcessor.stopResultDisplay()
        uiController.updatePlaybackButton(false)
        binding.videoView.pause()
    }

    fun stepPosition(deltaMs: Int) {
        val currentPosition = binding.videoView.currentPosition
        val newPosition = (currentPosition + deltaMs).coerceIn(0, videoDurationMs.toInt())
        seekToPosition(newPosition)
    }

    fun seekToPosition(positionMs: Int) {
        uiController.updateSeekBar(positionMs)
        try {
            val frameTimeUs = positionMs * 1000L
            val bitmap = frameRetriever?.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            if (bitmap != null) {
                binding.frameImageView.setImageBitmap(bitmap)
                uiController.showFrameImage()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frame at $positionMs", e)
        }
        lastSeekPositionMs = positionMs
        updateDisplayAtPosition(positionMs)
    }

    fun updateDisplayAtPosition(positionMs: Int) {
        val (landmarks, analysisResult) = videoDebugProcessor.getResultAtPosition(positionMs)
        val resultIndex = (positionMs / VideoDebugProcessor.VIDEO_INTERVAL_MS).toInt()
        // Use phase-aware overlay update for stroke detection visualization
        if (landmarks != null) {
            uiController.updatePoseOverlayWithPhase(resultIndex, landmarks)
        } else {
            uiController.clearPoseOverlay()
        }
        if (analysisResult != null) uiController.updateFrameAnalysisUI(resultIndex, analysisResult)
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        uiController.updateSpeedButtons(speed)
        if (isPlaying) {
            pausePlayback()
            startPlayback()
        }
    }

    fun reset() {
        pausePlayback()
        seekToPosition(0)
    }

    fun release() {
        pausePlayback()
        try {
            frameRetriever?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing frame retriever", e)
        }
        frameRetriever = null
    }
}
