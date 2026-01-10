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
    private var isPlaying = false
    private var playbackSpeed = 1.0f
    private var videoDurationMs = 0L
    private var videoWidth = 0
    private var videoHeight = 0
    private var isPortraitMode = false
    private var isVideoReady = false
    private var pendingSeekPosition: Int? = null

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
        currentVideoUri = uri

        // Get video metadata
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            videoDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
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

            // Set up seek complete listener for accurate frame display
            mp.setOnSeekCompleteListener {
                pendingSeekPosition?.let { pos ->
                    updateDisplayAtPosition(pos)
                    pendingSeekPosition = null
                }
            }

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

        pendingSeekPosition = positionMs

        // Use MediaPlayer directly for more reliable seeking
        mediaPlayer?.let { mp ->
            mp.seekTo(positionMs)
            // If paused, we need to start briefly to render the frame
            if (!mp.isPlaying) {
                mp.start()
                binding.videoView.postDelayed({
                    mp.pause()
                }, 100)
            }
        } ?: run {
            // Fallback if mediaPlayer not available
            binding.videoView.seekTo(positionMs)
            updateDisplayAtPosition(positionMs)
        }
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        pausePlayback()
        videoDebugProcessor.close()
    }
}
