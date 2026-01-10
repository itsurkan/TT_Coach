/*
 * AI Coach for Table Tennis
 * Debug Activity - Video-based debugging using batch processing (like GalleryFragment)
 */

package com.google.mediapipe.examples.poselandmarker

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityDebugBinding
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.ExerciseParameters
import com.google.mediapipe.examples.poselandmarker.processors.VideoDebugProcessor
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.Locale

/**
 * Debug Activity for video-based analysis testing
 * Uses batch processing like GalleryFragment for reliable pose detection
 */
class DebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugBinding
    private lateinit var videoDebugProcessor: VideoDebugProcessor
    private lateinit var motionAnalyzer: MotionAnalyzer
    private lateinit var feedbackGenerator: FeedbackGenerator

    private var currentVideoUri: Uri? = null
    private var mediaPlayer: MediaPlayer? = null
    private var frameRetriever: MediaMetadataRetriever? = null
    private var isPlaying = false
    private var playbackSpeed = 1.0f
    private var videoDurationMs = 0L
    private var videoWidth = 0
    private var videoHeight = 0
    private var isPortraitMode = false
    private var isVideoReady = false
    private var lastSeekPositionMs = 0

    companion object {
        private const val TAG = "DebugActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Video Debug"
        }

        initializeComponents()
        setupUI()
        loadDefaultVideo()
    }

    private fun initializeComponents() {
        val parameters = ExerciseParameters.forehandDrive()
        motionAnalyzer = MotionAnalyzer(parameters)
        feedbackGenerator = FeedbackGenerator(this)

        val application = application as TTCoachApplication
        val fileLogger = application.getFileLogger()
        videoDebugProcessor = VideoDebugProcessor(
            context = this,
            motionAnalyzer = motionAnalyzer,
            feedbackGenerator = feedbackGenerator,
            fileLogger = fileLogger
        )
    }

    private fun setupUI() {
        // Video selection
        binding.btnLoadVideo.setOnClickListener { showVideoSelectionDialog() }
        binding.btnLoadVideoPortrait.setOnClickListener { showVideoSelectionDialog() }
        
        // Log poses to file
        binding.btnLogPoses.setOnClickListener { exportPosesToFile() }
        binding.btnLogPosesPortrait.setOnClickListener { exportPosesToFile() }
        binding.btnLogPosesPortrait.setOnClickListener { exportPosesToFile() }

        // View mode toggle
        binding.btnToggleViewMode.setOnClickListener { toggleViewMode() }
        binding.btnToggleViewModePortrait.setOnClickListener { toggleViewMode() }

        // Playback controls
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }
        binding.btnPlayPausePortrait.setOnClickListener { togglePlayPause() }

        binding.btnStepBack.setOnClickListener { stepPosition(-300) }
        binding.btnStepForward.setOnClickListener { stepPosition(300) }
        binding.btnStepBackPortrait.setOnClickListener { stepPosition(-300) }
        binding.btnStepForwardPortrait.setOnClickListener { stepPosition(300) }

        // Speed control
        binding.btnSpeed025x.setOnClickListener { setPlaybackSpeed(0.25f) }
        binding.btnSpeed05x.setOnClickListener { setPlaybackSpeed(0.5f) }
        binding.btnSpeed1x.setOnClickListener { setPlaybackSpeed(1.0f) }
        binding.btnSpeed2x.setOnClickListener { setPlaybackSpeed(2.0f) }

        binding.btnSpeed025xPortrait.setOnClickListener { setPlaybackSpeed(0.25f) }
        binding.btnSpeed05xPortrait.setOnClickListener { setPlaybackSpeed(0.5f) }
        binding.btnSpeed1xPortrait.setOnClickListener { setPlaybackSpeed(1.0f) }
        binding.btnSpeed2xPortrait.setOnClickListener { setPlaybackSpeed(2.0f) }

        // Seek bar - use video position in ms
        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekToPosition(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { pausePlayback() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        binding.seekBarFrame.setOnSeekBarChangeListener(seekBarListener)
        binding.seekBarFramePortrait.setOnSeekBarChangeListener(seekBarListener)

        // Reset button
        binding.btnReset.setOnClickListener { resetAnalysis() }
    }

    private fun loadDefaultVideo() {
        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.forehand_drive}")
        loadVideo(videoUri)
    }

    private fun loadVideo(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.analysisPanel.visibility = View.GONE
        isVideoReady = false
        lastSeekPositionMs = 0
        currentVideoUri = uri

        // Get video metadata and setup frame retriever for seeking
        try {
            // Release previous retriever if exists
            frameRetriever?.release()

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            videoDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0

            // Keep retriever for frame extraction during seeking
            frameRetriever = MediaMetadataRetriever()
            frameRetriever?.setDataSource(this, uri)

            retriever.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video metadata", e)
        }

        // Setup video view
        binding.videoView.setVideoURI(uri)
        binding.videoView.setOnPreparedListener { mp ->
            mediaPlayer = mp
            mp.isLooping = false
            mp.setVolume(0f, 0f)

            videoWidth = mp.videoWidth
            videoHeight = mp.videoHeight

            val videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
            val isVideoPortrait = videoAspectRatio < 1.0f

            if (isVideoPortrait) {
                binding.btnToggleViewMode.visibility = View.VISIBLE
                if (!isPortraitMode) enablePortraitMode()
            } else {
                binding.btnToggleViewMode.visibility = View.GONE
                if (isPortraitMode) disablePortraitMode()
            }

            Log.i(TAG, "Video prepared: ${videoWidth}x${videoHeight}, duration: ${videoDurationMs}ms")
        }

        // Process video through MediaPipe (batch processing like GalleryFragment)
        videoDebugProcessor.processVideo(uri) { resultBundle ->
            runOnUiThread {
                if (resultBundle != null) {
                    isVideoReady = true

                    // Get dimensions from processor (accurate from MediaPipe)
                    val (width, height) = videoDebugProcessor.getVideoDimensions()
                    if (width > 0 && height > 0) {
                        videoWidth = width
                        videoHeight = height
                    }

                    // Setup seek bar with video duration
                    binding.seekBarFrame.max = videoDurationMs.toInt()
                    binding.seekBarFramePortrait.max = videoDurationMs.toInt()

                    binding.progressBar.visibility = View.GONE
                    binding.btnLogPoses.isEnabled = true
                    binding.btnLogPosesPortrait.isEnabled = true
                    binding.analysisPanel.visibility = View.VISIBLE

                    updateVideoInfo()
                    updateAnalysisInfo()

                    // Show first frame result
                    updateDisplayAtPosition(0)

                    Log.i(TAG, "Video ready: ${videoDebugProcessor.getTotalFrames()} frames processed")
                } else {
                    binding.progressBar.visibility = View.GONE
                    Log.e(TAG, "Video processing failed")
                }
            }
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        if (!isVideoReady) return

        isPlaying = true
        binding.btnPlayPause.text = "⏸ Pause"
        binding.btnPlayPausePortrait.text = "⏸ Pause"

        // Show VideoView, hide ImageView for playback
        binding.frameImageView.visibility = View.GONE
        binding.videoView.visibility = View.VISIBLE

        // Seek to the position user navigated to, then start
        binding.videoView.seekTo(lastSeekPositionMs)
        binding.videoView.start()

        // Schedule pose overlay updates synced with video position (like GalleryFragment)
        videoDebugProcessor.scheduleResultDisplay(
            getVideoPositionMs = { binding.videoView.currentPosition },
            isVideoPlaying = { isPlaying && binding.videoView.isPlaying },
            onFrameUpdate = { resultIndex, poseResult, analysisResult ->
                runOnUiThread {
                    updatePoseOverlay(poseResult)
                    updateFrameAnalysisUI(resultIndex, analysisResult)

                    val currentPosition = binding.videoView.currentPosition
                    binding.seekBarFrame.progress = currentPosition
                    binding.seekBarFramePortrait.progress = currentPosition

                    // Stop at end
                    if (currentPosition >= videoDurationMs - 100) {
                        pausePlayback()
                    }
                }
            }
        )
    }

    private fun pausePlayback() {
        isPlaying = false
        videoDebugProcessor.stopResultDisplay()
        binding.btnPlayPause.text = "▶ Play"
        binding.btnPlayPausePortrait.text = "▶ Play"
        binding.videoView.pause()
    }

    private fun stepPosition(deltaMs: Int) {
        val currentPosition = binding.videoView.currentPosition
        val newPosition = (currentPosition + deltaMs).coerceIn(0, videoDurationMs.toInt())
        seekToPosition(newPosition)
    }

    private fun seekToPosition(positionMs: Int) {
        binding.seekBarFrame.progress = positionMs
        binding.seekBarFramePortrait.progress = positionMs

        // Extract frame using MediaMetadataRetriever (VideoView can't display frames when paused)
        try {
            val frameTimeUs = positionMs * 1000L
            val bitmap = frameRetriever?.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)

            if (bitmap != null) {
                // Show frame in ImageView, hide VideoView
                binding.frameImageView.setImageBitmap(bitmap)
                binding.frameImageView.visibility = View.VISIBLE
                binding.videoView.visibility = View.INVISIBLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frame at $positionMs", e)
        }

        // Store the current seek position for when playback starts
        lastSeekPositionMs = positionMs

        // Update pose overlay and analysis
        updateDisplayAtPosition(positionMs)
    }

    private fun updateDisplayAtPosition(positionMs: Int) {
        val (poseResult, analysisResult) = videoDebugProcessor.getResultAtPosition(positionMs)

        if (poseResult != null) {
            updatePoseOverlay(poseResult)
        } else {
            binding.overlayView.clear()
        }

        val resultIndex = (positionMs / VideoDebugProcessor.VIDEO_INTERVAL_MS).toInt()
        if (analysisResult != null) {
            updateFrameAnalysisUI(resultIndex, analysisResult)
        }
    }

    private fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed

        binding.btnSpeed025x.isSelected = (speed == 0.25f)
        binding.btnSpeed05x.isSelected = (speed == 0.5f)
        binding.btnSpeed1x.isSelected = (speed == 1.0f)
        binding.btnSpeed2x.isSelected = (speed == 2.0f)

        binding.btnSpeed025xPortrait.isSelected = (speed == 0.25f)
        binding.btnSpeed05xPortrait.isSelected = (speed == 0.5f)
        binding.btnSpeed1xPortrait.isSelected = (speed == 1.0f)
        binding.btnSpeed2xPortrait.isSelected = (speed == 2.0f)

        if (isPlaying) {
            pausePlayback()
            startPlayback()
        }
    }

    private fun updatePoseOverlay(poseResult: PoseLandmarkerResult) {
        if (videoHeight > 0 && videoWidth > 0) {
            binding.overlayView.setResults(
                poseResult,
                videoHeight,
                videoWidth,
                RunningMode.VIDEO
            )
            binding.overlayView.invalidate()
        }
    }

    private fun updateFrameAnalysisUI(resultIndex: Int, result: AnalysisResult) {
        val totalFrames = videoDebugProcessor.getTotalFrames()

        binding.tvFrameNumber.text = "Frame: $resultIndex / $totalFrames"
        binding.tvWristAngle.text = "Wrist Angle: %.1f°%s".format(
            Locale.US, result.wristAngle, if (result.isWristAngleValid) " ✓" else " ✗"
        )
        binding.tvBodyRotation.text = "Body Rotation: %.1f°%s".format(
            Locale.US, result.bodyRotation, if (result.isBodyRotationValid) " ✓" else " ✗"
        )
        binding.tvFollowThrough.text = "Follow-Through: %.1f°%s".format(
            Locale.US, result.followThroughAngle, if (result.isFollowThroughValid) " ✓" else " ✗"
        )
        binding.tvContactHeight.text = "Contact Height: %.2f%s".format(
            Locale.US, result.contactHeight, if (result.isContactHeightValid) " ✓" else " ✗"
        )
        binding.tvElbowDistance.text = "Elbow Distance: %.2fm%s".format(
            Locale.US, result.elbowBodyDistance, if (result.isElbowPositionValid) " ✓" else " ✗"
        )
        binding.tvPhase.text = "Phase: ${result.phase.name}"
        binding.tvScore.text = "Score: ${result.overallScore}%"

        binding.tvScore.setTextColor(when {
            result.overallScore >= 80 -> getColor(android.R.color.holo_green_dark)
            result.overallScore >= 60 -> getColor(android.R.color.holo_orange_dark)
            else -> getColor(android.R.color.holo_red_dark)
        })

        val feedback = feedbackGenerator.generateShortFeedback(result)
        binding.tvCurrentFeedback.text = feedback
    }

    private fun updateAnalysisInfo() {
        val summary = videoDebugProcessor.getAnalysisSummary()

        binding.tvTotalFrames.text = "Total Frames: ${summary.totalFrames}"
        binding.tvStrokesDetected.text = "Strokes Detected: ${summary.strokeCount}"
        binding.tvAvgScore.text = "Avg Score: %.1f%%".format(Locale.US, summary.averageScore)
        binding.tvGoodStrokes.text = "Good Strokes: ${summary.goodStrokes} (${summary.successRate.toInt()}%)"

        val phaseText = buildString {
            append("Phase Distribution:\n")
            summary.phaseDistribution.forEach { (phase, count) ->
                append("  ${phase.name}: $count frames\n")
            }
        }
        binding.tvPhaseDistribution.text = phaseText
    }

    private fun updateVideoInfo() {
        val totalFrames = videoDebugProcessor.getTotalFrames()
        val infoText = "Duration: %.1fs | Frames: %d | Interval: %dms".format(
            Locale.US,
            videoDurationMs / 1000.0,
            totalFrames,
            VideoDebugProcessor.VIDEO_INTERVAL_MS
        )
        binding.tvVideoInfo.text = infoText
        binding.tvVideoInfoPortrait.text = infoText
    }

    private fun toggleViewMode() {
        if (isPortraitMode) {
            disablePortraitMode()
        } else {
            enablePortraitMode()
        }
    }

    private fun enablePortraitMode() {
        isPortraitMode = true
        binding.btnToggleViewMode.text = "🖥 Landscape Mode"
        binding.btnToggleViewModePortrait.text = "🖥 Landscape Mode"

        binding.topBar.visibility = View.GONE
        binding.controlsPanel.visibility = View.GONE
        binding.portraitTopControls.visibility = View.VISIBLE
        binding.portraitControls.visibility = View.VISIBLE
        binding.tvVideoInfoPortrait.text = binding.tvVideoInfo.text

        val mainContentParams = binding.mainContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        mainContentParams.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        mainContentParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        mainContentParams.bottomToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        mainContentParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        binding.mainContent.layoutParams = mainContentParams

        Log.i(TAG, "Portrait mode enabled")
    }

    private fun disablePortraitMode() {
        isPortraitMode = false
        binding.btnToggleViewMode.text = "📱 Portrait Mode"
        binding.btnToggleViewModePortrait.text = "📱 Portrait Mode"

        binding.topBar.visibility = View.VISIBLE
        binding.controlsPanel.visibility = View.VISIBLE
        binding.portraitTopControls.visibility = View.GONE
        binding.portraitControls.visibility = View.GONE

        val mainContentParams = binding.mainContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        mainContentParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        mainContentParams.topToBottom = binding.topBar.id
        mainContentParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        mainContentParams.bottomToTop = binding.controlsPanel.id
        binding.mainContent.layoutParams = mainContentParams

        Log.i(TAG, "Portrait mode disabled")
    }

    private fun showVideoSelectionDialog() {
        val videoOptions = arrayOf(
            "forehand_drive.mp4 (Default)",
            "Choose from gallery..."
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Test Video")
            .setItems(videoOptions) { _, which ->
                when (which) {
                    0 -> loadDefaultVideo()
                    1 -> loadDefaultVideo() // TODO: Implement gallery picker
                }
            }
            .show()
    }

    private fun resetAnalysis() {
        pausePlayback()
        seekToPosition(0)
        videoDebugProcessor.reset()
        currentVideoUri?.let { loadVideo(it) }
    }

    private fun exportPosesToFile() {
        if (!isVideoReady) {
            Toast.makeText(this, "No video loaded", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Get all pose results from processor
            val poses = videoDebugProcessor.getAllPoseResults()
            if (poses.isEmpty()) {
                Toast.makeText(this, "No poses to export", Toast.LENGTH_SHORT).show()
                return
            }

            // Create JSON structure
            val jsonRoot = JSONObject()
            jsonRoot.put("videoUri", currentVideoUri.toString())
            jsonRoot.put("intervalMs", 100)
            jsonRoot.put("totalFrames", poses.size)
            jsonRoot.put("videoDurationMs", videoDurationMs)
            jsonRoot.put("videoWidth", videoWidth)
            jsonRoot.put("videoHeight", videoHeight)
            jsonRoot.put("exportTimestamp", System.currentTimeMillis())

            val framesArray = JSONArray()
            poses.forEachIndexed { index, poseResult ->
                val frameObj = JSONObject()
                frameObj.put("frameIndex", index)
                frameObj.put("timestampMs", index * 100L)

                if (poseResult.landmarks().isNotEmpty()) {
                    val landmarks = poseResult.landmarks()[0]
                    val landmarksArray = JSONArray()

                    for (i in 0 until landmarks.size) {
                        val landmark = landmarks[i]
                        val landmarkObj = JSONObject()
                        landmarkObj.put("index", i)
                        landmarkObj.put("x", landmark.x())
                        landmarkObj.put("y", landmark.y())
                        landmarkObj.put("z", landmark.z())
                        landmarkObj.put("visibility", landmark.visibility().orElse(0f))
                        landmarkObj.put("presence", landmark.presence().orElse(0f))
                        landmarksArray.put(landmarkObj)
                    }

                    frameObj.put("landmarks", landmarksArray)
                } else {
                    frameObj.put("landmarks", JSONArray())
                }

                framesArray.put(frameObj)
            }

            jsonRoot.put("frames", framesArray)

            // Save to external files directory
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val fileName = "poses_$timestamp.json"
            val directory = getExternalFilesDir(null)
            val file = File(directory, fileName)

            file.writeText(jsonRoot.toString(2)) // Pretty print with 2-space indent

            val message = "Poses exported to:\n${file.absolutePath}\n\nFrames: ${poses.size}"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.i(TAG, "Exported ${poses.size} poses to ${file.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting poses", e)
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        pausePlayback()
        videoDebugProcessor.close()
        try {
            frameRetriever?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing frame retriever", e)
        }
        frameRetriever = null
    }
}
