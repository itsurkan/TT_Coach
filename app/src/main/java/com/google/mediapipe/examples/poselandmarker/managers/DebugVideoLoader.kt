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
    private var currentAssetPath: String? = null
    private var isVideoReady = false

    companion object {
        private const val TAG = "DebugVideoLoader"
    }

    fun getCurrentVideoUri(): Uri? = currentVideoUri

    fun isVideoReady(): Boolean = isVideoReady

    /**
     * Load video from assets folder (e.g., "Videos/forehand_drive.mp4")
     */
    fun loadVideoFromAssets(assetPath: String, onVideoReady: (Int, Int) -> Unit) {
        uiController.showProgress()
        isVideoReady = false
        currentAssetPath = assetPath

        var videoDurationMs = 0L
        var videoWidth = 0
        var videoHeight = 0

        try {
            // Copy asset to cache for VideoView (it can't play assets directly)
            val cacheFile = java.io.File(context.cacheDir, assetPath.replace("/", "_"))
            context.assets.open(assetPath).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            currentVideoUri = Uri.fromFile(cacheFile)

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(cacheFile.absolutePath)
            videoDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            if (rotation == "90" || rotation == "270") {
                videoWidth = heightStr?.toInt() ?: 0
                videoHeight = widthStr?.toInt() ?: 0
            } else {
                videoWidth = widthStr?.toInt() ?: 0
                videoHeight = heightStr?.toInt() ?: 0
            }
            retriever.release()

            val frameRetriever = MediaMetadataRetriever()
            frameRetriever.setDataSource(cacheFile.absolutePath)
            playbackManager.setFrameRetriever(frameRetriever)

            binding.videoView.setVideoPath(cacheFile.absolutePath)
            binding.videoView.setOnPreparedListener { mp ->
                playbackManager.setMediaPlayer(mp)
                mp.isLooping = false
                mp.setVolume(0f, 0f)

                if (videoWidth == 0 || videoHeight == 0) {
                    videoWidth = mp.videoWidth
                    videoHeight = mp.videoHeight
                }

                uiController.setVideoDimensions(videoWidth, videoHeight)
                val videoAspectRatio = if (videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat() else 1.0f
                uiController.setToggleViewButtonVisibility(videoAspectRatio < 1.0f)
                Log.i(TAG, "Video prepared from assets: ${videoWidth}x${videoHeight}, duration: ${videoDurationMs}ms")
            }

            // Check for poses JSON in assets
            val jsonString = getJsonFromAssets(assetPath)
            if (jsonString != null) {
                Log.i(TAG, "Found JSON poses in assets, skipping video analysis")
                videoDebugProcessor.processVideoFromJson(jsonString, videoWidth, videoHeight) { success ->
                    handleProcessingComplete(success, videoDurationMs, onVideoReady)
                }
            } else {
                Log.i(TAG, "No JSON poses found in assets, analyzing video...")
                processVideoNormal(currentVideoUri!!, videoDurationMs, onVideoReady)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading video from assets: $assetPath", e)
            uiController.hideProgress()
        }
    }

    private fun getJsonFromAssets(videoAssetPath: String): String? {
        // Convert video path to poses path: "Videos/forehand_drive.mp4" -> "Videos/forehand_drive_poses.json"
        val posesPath = videoAssetPath.substringBeforeLast(".") + "_poses.json"
        return try {
            context.assets.open(posesPath).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.d(TAG, "No poses file found at: $posesPath")
            null
        }
    }

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
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            
            // Handle rotation (if video is rotated 90 or 270, swap width/height)
            if (rotation == "90" || rotation == "270") {
                 videoWidth = heightStr?.toInt() ?: 0
                 videoHeight = widthStr?.toInt() ?: 0
            } else {
                 videoWidth = widthStr?.toInt() ?: 0
                 videoHeight = heightStr?.toInt() ?: 0
            }
            
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
            
            // If we didn't get dimensions from metadata, get from player
            if (videoWidth == 0 || videoHeight == 0) {
                videoWidth = mp.videoWidth
                videoHeight = mp.videoHeight
            }
            
            uiController.setVideoDimensions(videoWidth, videoHeight)
            val videoAspectRatio = if (videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat() else 1.0f
            val isVideoPortrait = videoAspectRatio < 1.0f
            uiController.setToggleViewButtonVisibility(isVideoPortrait)
            Log.i(TAG, "Video prepared: ${videoWidth}x${videoHeight}, duration: ${videoDurationMs}ms")
        }

        // Check for sidecar JSON - first try raw resource, then external file
        val jsonString = getJsonForUri(uri)
        if (jsonString != null) {
            Log.i(TAG, "Found JSON poses data, skipping video analysis")
            videoDebugProcessor.processVideoFromJson(jsonString, videoWidth, videoHeight) { success ->
                handleProcessingComplete(success, videoDurationMs, onVideoReady)
            }
        } else {
            Log.i(TAG, "No JSON poses found, analyzing video...")
            processVideoNormal(uri, videoDurationMs, onVideoReady)
        }
    }

    private fun getJsonForUri(uri: Uri): String? {
        // For raw resources, check if there's a corresponding _poses raw resource
        if (uri.scheme == android.content.ContentResolver.SCHEME_ANDROID_RESOURCE) {
            try {
                val id = uri.lastPathSegment?.toIntOrNull()
                if (id != null) {
                    val videoName = context.resources.getResourceEntryName(id)
                    val posesResourceName = "${videoName}_poses"
                    val posesResId = context.resources.getIdentifier(
                        posesResourceName, "raw", context.packageName
                    )
                    if (posesResId != 0) {
                        Log.i(TAG, "Found raw resource: $posesResourceName")
                        return context.resources.openRawResource(posesResId)
                            .bufferedReader().use { it.readText() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading raw JSON resource", e)
            }
        }

        // Fallback to external file
        val jsonFile = getJsonFileForUri(uri)
        if (jsonFile != null && jsonFile.exists()) {
            try {
                return jsonFile.readText()
            } catch (e: Exception) {
                Log.e(TAG, "Error reading JSON file", e)
            }
        }
        return null
    }

    private fun processVideoNormal(uri: Uri, durationMs: Long, onVideoReady: (Int, Int) -> Unit) {
        videoDebugProcessor.processVideo(uri) { resultBundle ->
            handleProcessingComplete(resultBundle != null, durationMs, onVideoReady)
        }
    }

    private fun handleProcessingComplete(
        success: Boolean,
        durationMs: Long,
        onVideoReady: (Int, Int) -> Unit
    ) {
        (context as? android.app.Activity)?.runOnUiThread {
            if (success) {
                isVideoReady = true
                val (width, height) = videoDebugProcessor.getVideoDimensions()

                if (width > 0 && height > 0) {
                    uiController.setVideoDimensions(width, height)
                }
                onVideoReady(width, height)

                // Run stroke detection after poses are loaded
                val strokeResult = videoDebugProcessor.runStrokeDetection()
                if (strokeResult != null) {
                    Log.i(TAG, "Stroke detection: ${strokeResult.strokes.size} strokes found")
                    uiController.setPhaseColoringEnabled(true)
                }

                playbackManager.setVideoDuration(durationMs)
                uiController.hideProgress()
                uiController.enableLogButton()
                uiController.updateVideoInfo()
                uiController.updateAnalysisInfo()
                playbackManager.updateDisplayAtPosition(0)
                Log.i(TAG, "Video ready: ${videoDebugProcessor.getTotalFrames()} frames processed")
            } else {
                uiController.hideProgress()
                Log.e(TAG, "Video processing failed")
            }
        }
    }

    private fun getJsonFileForUri(uri: Uri): java.io.File? {
        var filename: String? = null
        try {
            if (uri.scheme == android.content.ContentResolver.SCHEME_ANDROID_RESOURCE) {
                val id = uri.lastPathSegment?.toIntOrNull()
                if (id != null) {
                    filename = context.resources.getResourceEntryName(id)
                }
            } else if (uri.scheme == android.content.ContentResolver.SCHEME_FILE) {
                 filename = java.io.File(uri.path!!).nameWithoutExtension
                 // Check sibling
                 val sibling = java.io.File(java.io.File(uri.path!!).parent, "${filename}_poses.json")
                 if (sibling.exists()) return sibling
            }
            
            if (filename == null) {
                 filename = uri.lastPathSegment
                 if (filename?.contains(".") == true) {
                     filename = filename.substringBeforeLast(".")
                 }
            }

            if (filename != null) {
                 val filesDir = context.getExternalFilesDir(null)
                 val jsonFile = java.io.File(filesDir, "${filename}_poses.json")
                 if (jsonFile.exists()) return jsonFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving JSON file", e)
        }
        return null
    }

    fun reset() {
        currentAssetPath?.let {
            loadVideoFromAssets(it) { _, _ -> }
            return
        }
        currentVideoUri?.let { loadVideo(it) { _, _ -> } }
    }
}
