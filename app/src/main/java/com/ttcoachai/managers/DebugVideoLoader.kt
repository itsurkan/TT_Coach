package com.ttcoachai.managers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.ttcoachai.databinding.ActivityDebugBinding
import com.ttcoachai.processors.VideoDebugProcessor
import java.io.File

class DebugVideoLoader(
    private val context: Context,
    private val binding: ActivityDebugBinding,
    private val videoDebugProcessor: VideoDebugProcessor,
    private val uiController: DebugUIController,
    private val playbackManager: DebugPlaybackManager
) {
    private var currentVideoUri: Uri? = null
    private var currentAssetPath: String? = null
    private var isVideoReady = false
    private val jsonMapper = DebugJsonMapper(context)

    companion object {
        private const val TAG = "DebugVideoLoader"
    }

    fun getCurrentVideoUri(): Uri? = currentVideoUri
    fun isVideoReady(): Boolean = isVideoReady

    fun loadVideoFromAssets(assetPath: String, onVideoReady: (Int, Int) -> Unit) {
        uiController.showProgress()
        isVideoReady = false
        currentAssetPath = assetPath

        try {
            val cacheFile = File(context.cacheDir, assetPath.replace("/", "_"))
            context.assets.open(assetPath).use { it.copyTo(cacheFile.outputStream()) }
            currentVideoUri = Uri.fromFile(cacheFile)

            val (width, height, duration) = extractMetadata(currentVideoUri!!)
            setupPlayer(cacheFile.absolutePath, width, height)

            jsonMapper.getJsonFromAssets(assetPath)?.let { json ->
                videoDebugProcessor.processVideoFromJson(json, width, height) { 
                    handleComplete(it, duration, onVideoReady) 
                }
            } ?: videoDebugProcessor.processVideo(currentVideoUri!!) { 
                handleComplete(it != null, duration, onVideoReady) 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading asset", e)
            uiController.hideProgress()
        }
    }

    fun loadVideo(uri: Uri, onVideoReady: (Int, Int) -> Unit) {
        uiController.showProgress()
        isVideoReady = false
        currentVideoUri = uri

        val (width, height, duration) = extractMetadata(uri)
        binding.videoView.setVideoURI(uri)
        binding.videoView.setOnPreparedListener { mp ->
            playbackManager.setMediaPlayer(mp)
            uiController.setVideoDimensions(if (width > 0) width else mp.videoWidth, if (height > 0) height else mp.videoHeight)
            uiController.setToggleViewButtonVisibility((width.toFloat() / height.toFloat()) < 1.0f)
        }

        jsonMapper.getJsonForUri(uri)?.let { json ->
            videoDebugProcessor.processVideoFromJson(json, width, height) { 
                handleComplete(it, duration, onVideoReady) 
            }
        } ?: videoDebugProcessor.processVideo(uri) { 
            handleComplete(it != null, duration, onVideoReady) 
        }
    }

    private fun extractMetadata(uri: Uri): Triple<Int, Int, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
            val r = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            
            val frameRetriever = MediaMetadataRetriever()
            if (uri.scheme == "file") frameRetriever.setDataSource(uri.path) else frameRetriever.setDataSource(context, uri)
            playbackManager.setFrameRetriever(frameRetriever)
            
            if (r == 90 || r == 270) Triple(h, w, duration) else Triple(w, h, duration)
        } catch (e: Exception) {
            Triple(0, 0, 0L)
        } finally {
            retriever.release()
        }
    }

    private fun setupPlayer(path: String, width: Int, height: Int) {
        binding.videoView.setVideoPath(path)
        binding.videoView.setOnPreparedListener { mp ->
            playbackManager.setMediaPlayer(mp)
            uiController.setVideoDimensions(if (width > 0) width else mp.videoWidth, if (height > 0) height else mp.videoHeight)
            uiController.setToggleViewButtonVisibility((width.toFloat() / height.toFloat()) < 1.0f)
        }
    }

    private fun handleComplete(success: Boolean, duration: Long, onVideoReady: (Int, Int) -> Unit) {
        (context as? android.app.Activity)?.runOnUiThread {
            if (success) {
                isVideoReady = true
                val dim = videoDebugProcessor.getVideoDimensions()
                uiController.setVideoDimensions(dim.first, dim.second)
                onVideoReady(dim.first, dim.second)
                videoDebugProcessor.runStrokeDetection()?.let { uiController.setPhaseColoringEnabled(true) }
                playbackManager.setVideoDuration(duration)
                uiController.hideProgress()
                uiController.enableLogButton()
                uiController.updateVideoInfo()
                uiController.updateAnalysisInfo()
                playbackManager.updateDisplayAtPosition(0)
            } else {
                uiController.hideProgress()
            }
        }
    }

    fun reset() {
        currentAssetPath?.let { loadVideoFromAssets(it) { _, _ -> } } 
            ?: currentVideoUri?.let { loadVideo(it) { _, _ -> } }
    }
}
