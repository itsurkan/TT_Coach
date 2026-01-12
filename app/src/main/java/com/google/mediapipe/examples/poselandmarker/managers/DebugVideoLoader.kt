/*
 * AI Coach for Table Tennis
 * Debug Video Loader - Handles video loading operations
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityDebugBinding
import com.google.mediapipe.examples.poselandmarker.processors.VideoDebugProcessor

class DebugVideoLoader(
    private val context: Context,
    private val binding: ActivityDebugBinding,
    private val videoDebugProcessor: VideoDebugProcessor,
    private val uiController: DebugUIController,
    private val playbackManager: DebugPlaybackManager
) {
    private var currentVideoUri: Uri? = null
    private var isVideoReady = false

    companion object {
        private const val TAG = "DebugVideoLoader"
    }

    fun getCurrentVideoUri(): Uri? = currentVideoUri

    fun isVideoReady(): Boolean = isVideoReady

    fun loadVideo(uri: Uri, onVideoReady: (Int, Int) -> Unit) {
        uiController.showProgress()
        isVideoReady = false
        currentVideoUri = uri

        var videoDurationMs = 0L
        var videoWidth = 0
        var videoHeight = 0

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            videoDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            retriever.release()

            val frameRetriever = MediaMetadataRetriever()
            frameRetriever.setDataSource(context, uri)
            playbackManager.setFrameRetriever(frameRetriever)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video metadata", e)
        }

        binding.videoView.setVideoURI(uri)
        binding.videoView.setOnPreparedListener { mp ->
            playbackManager.setMediaPlayer(mp)
            mp.isLooping = false
            mp.setVolume(0f, 0f)
            videoWidth = mp.videoWidth
            videoHeight = mp.videoHeight
            uiController.setVideoDimensions(videoWidth, videoHeight)
            val videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
            val isVideoPortrait = videoAspectRatio < 1.0f
            uiController.setToggleViewButtonVisibility(isVideoPortrait)
            Log.i(TAG, "Video prepared: ${videoWidth}x${videoHeight}, duration: ${videoDurationMs}ms")
        }

        videoDebugProcessor.processVideo(uri) { resultBundle ->
            (context as? android.app.Activity)?.runOnUiThread {
                if (resultBundle != null) {
                    isVideoReady = true
                    val (width, height) = videoDebugProcessor.getVideoDimensions()
                    if (width > 0 && height > 0) {
                        videoWidth = width
                        videoHeight = height
                        uiController.setVideoDimensions(width, height)
                    }
                    playbackManager.setVideoDuration(videoDurationMs)
                    uiController.hideProgress()
                    uiController.enableLogButton()
                    uiController.updateVideoInfo()
                    uiController.updateAnalysisInfo()
                    playbackManager.updateDisplayAtPosition(0)
                    onVideoReady(videoWidth, videoHeight)
                    Log.i(TAG, "Video ready: ${videoDebugProcessor.getTotalFrames()} frames processed")
                } else {
                    uiController.hideProgress()
                    Log.e(TAG, "Video processing failed")
                }
            }
        }
    }

    fun reset() {
        currentVideoUri?.let { loadVideo(it) { _, _ -> } }
    }
}
