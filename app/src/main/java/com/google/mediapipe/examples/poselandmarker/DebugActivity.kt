/*
 * AI Coach for Table Tennis
 * Debug Activity - Video-based debugging for PoseAnalysisProcessor
 */

package com.google.mediapipe.examples.poselandmarker

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityDebugBinding
import com.google.mediapipe.examples.poselandmarker.models.ExerciseParameters
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
import com.google.mediapipe.examples.poselandmarker.processors.VideoDebugProcessor
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Debug Activity for video-based analysis testing
 * Features:
 * - Load test videos from res/raw
 * - Frame-by-frame playback with pose overlay
 * - Real-time analysis panel showing angles, scores, phases
 * - Playback controls (play/pause/step, speed adjustment)
 * - Stroke count validation
 */
class DebugActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDebugBinding
    private lateinit var videoDebugProcessor: VideoDebugProcessor
    private lateinit var motionAnalyzer: MotionAnalyzer
    private lateinit var feedbackGenerator: FeedbackGenerator
    
    private var currentVideoUri: Uri? = null
    private var isPlaying = false
    private var playbackSpeed = 1.0f
    private var currentFrameIndex = 0
    private var totalFrames = 0
    private var videoDurationMs = 0L
    private var videoWidth = 0
    private var videoHeight = 0
    private var isPortraitMode = false
    
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val playbackExecutor = Executors.newSingleThreadScheduledExecutor()
    private var playbackTask: java.util.concurrent.ScheduledFuture<*>? = null
    
    companion object {
        private const val TAG = "DebugActivity"
        private const val DEFAULT_FPS = 30
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
        // Initialize analyzers
        val parameters = ExerciseParameters.forehandDrive()
        motionAnalyzer = MotionAnalyzer(parameters)
        feedbackGenerator = FeedbackGenerator(this)
        
        // Initialize video processor
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
        binding.btnLoadVideo.setOnClickListener {
            showVideoSelectionDialog()
        }
        binding.btnLoadVideoPortrait.setOnClickListener {
            showVideoSelectionDialog()
        }
        
        // View mode toggle
        binding.btnToggleViewMode.setOnClickListener {
            toggleViewMode()
        }
        binding.btnToggleViewModePortrait.setOnClickListener {
            toggleViewMode()
        }
        
        // Playback controls
        binding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }
        
        // Portrait mode duplicate controls
        binding.btnPlayPausePortrait.setOnClickListener {
            togglePlayPause()
        }
        
        binding.btnStepBack.setOnClickListener {
            stepFrame(-1)
        }
        
        binding.btnStepForward.setOnClickListener {
            stepFrame(1)
        }
        
        binding.btnStepBackPortrait.setOnClickListener {
            stepFrame(-1)
        }
        
        binding.btnStepForwardPortrait.setOnClickListener {
            stepFrame(1)
        }
        
        // Speed control
        binding.btnSpeed025x.setOnClickListener { setPlaybackSpeed(0.25f) }
        binding.btnSpeed05x.setOnClickListener { setPlaybackSpeed(0.5f) }
        binding.btnSpeed1x.setOnClickListener { setPlaybackSpeed(1.0f) }
        binding.btnSpeed2x.setOnClickListener { setPlaybackSpeed(2.0f) }
        
        binding.btnSpeed025xPortrait.setOnClickListener { setPlaybackSpeed(0.25f) }
        binding.btnSpeed05xPortrait.setOnClickListener { setPlaybackSpeed(0.5f) }
        binding.btnSpeed1xPortrait.setOnClickListener { setPlaybackSpeed(1.0f) }
        binding.btnSpeed2xPortrait.setOnClickListener { setPlaybackSpeed(2.0f) }
        
        // Frame scrubber
        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekToFrame(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                pausePlayback()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        binding.seekBarFrame.setOnSeekBarChangeListener(seekBarListener)
        binding.seekBarFramePortrait.setOnSeekBarChangeListener(seekBarListener)
        
        // Reset button
        binding.btnReset.setOnClickListener {
            resetAnalysis()
        }
    }
    
    private fun loadDefaultVideo() {
        // Load default test video from res/raw
        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.forehand_drive}")
        loadVideo(videoUri)
    }
    
    private fun loadVideo(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.analysisPanel.visibility = View.GONE
        
        currentVideoUri = uri
        
        backgroundExecutor.execute {
            try {
                // Get video metadata
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                videoDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
                val fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloat() ?: DEFAULT_FPS.toFloat()
                retriever.release()
                
                totalFrames = ((videoDurationMs / 1000.0) * fps).toInt()
                
                // Setup video view
                runOnUiThread {
                    binding.videoView.setVideoURI(uri)
                    binding.videoView.setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.isLooping = false
                        
                        // Capture actual video dimensions
                        videoWidth = mediaPlayer.videoWidth
                        videoHeight = mediaPlayer.videoHeight
                        
                        // Detect if video is portrait (9:16 or similar)
                        val videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
                        val isVideoPortrait = videoAspectRatio < 1.0f
                        
                        // Show view mode toggle for portrait videos
                        if (isVideoPortrait) {
                            binding.btnToggleViewMode.visibility = View.VISIBLE
                            // Auto-enable portrait mode for portrait videos
                            if (!isPortraitMode) {
                                enablePortraitMode()
                            }
                        } else {
                            binding.btnToggleViewMode.visibility = View.GONE
                            if (isPortraitMode) {
                                disablePortraitMode()
                            }
                        }
                        
                        // Adjust overlay view to match video aspect ratio
                        val overlayParams = binding.overlayView.layoutParams
                        overlayParams.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        overlayParams.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        binding.overlayView.layoutParams = overlayParams
                        
                        binding.seekBarFrame.max = totalFrames
                        binding.seekBarFramePortrait.max = totalFrames
                        binding.progressBar.visibility = View.GONE
                        binding.analysisPanel.visibility = View.VISIBLE
                        updateVideoInfo()
                        
                        Log.i(TAG, "Video dimensions: ${videoWidth}x${videoHeight}, aspect ratio: $videoAspectRatio, portrait: $isVideoPortrait")
                    }
                }
                
                // Process video through MediaPipe
                videoDebugProcessor.processVideo(uri) { frameResults ->
                    runOnUiThread {
                        updateAnalysisInfo()
                        Log.i(TAG, "Video processed: ${frameResults.size} frames analyzed")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading video", e)
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
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
        // Cancel any existing playback task
        playbackTask?.cancel(false)
        
        isPlaying = true
        binding.btnPlayPause.text = "⏸ Pause"
        binding.btnPlayPausePortrait.text = "⏸ Pause"
        
        binding.videoView.start()
        
        // Schedule frame updates
        val frameDelayMs = (1000.0 / (DEFAULT_FPS * playbackSpeed)).toLong()
        playbackTask = playbackExecutor.scheduleAtFixedRate({
            if (isPlaying) {
                runOnUiThread {
                    val currentPosition = binding.videoView.currentPosition
                    val frameIndex = (currentPosition / 1000.0 * DEFAULT_FPS).toInt()
                    
                    if (frameIndex != currentFrameIndex) {
                        currentFrameIndex = frameIndex
                        updateFrameAnalysis(currentFrameIndex)
                        binding.seekBarFrame.progress = currentFrameIndex
                        binding.seekBarFramePortrait.progress = currentFrameIndex
                    }
                    
                    // Stop at end
                    if (currentPosition >= videoDurationMs - 100) {
                        pausePlayback()
                    }
                }
            }
        }, 0, frameDelayMs, TimeUnit.MILLISECONDS)
    }
    
    private fun pausePlayback() {
        isPlaying = false
        playbackTask?.cancel(false)
        playbackTask = null
        binding.btnPlayPause.text = "▶ Play"
        binding.btnPlayPausePortrait.text = "▶ Play"
        binding.videoView.pause()
    }
    
    private fun stepFrame(direction: Int) {
        val newFrame = (currentFrameIndex + direction).coerceIn(0, totalFrames - 1)
        seekToFrame(newFrame)
    }
    
    private fun seekToFrame(frameIndex: Int) {
        currentFrameIndex = frameIndex
        val positionMs = (frameIndex * 1000.0 / DEFAULT_FPS).toInt()
        binding.seekBarFrame.progress = frameIndex
        binding.seekBarFramePortrait.progress = frameIndex
        
        // Seek and wait for completion before updating overlay
        binding.videoView.seekTo(positionMs)
        binding.videoView.postDelayed({
            updateFrameAnalysis(frameIndex)
        }, 100) // Delay to ensure seek completes
    }
    
    private fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        
        // Update button states for both control sets
        binding.btnSpeed025x.isSelected = (speed == 0.25f)
        binding.btnSpeed05x.isSelected = (speed == 0.5f)
        binding.btnSpeed1x.isSelected = (speed == 1.0f)
        binding.btnSpeed2x.isSelected = (speed == 2.0f)
        
        binding.btnSpeed025xPortrait.isSelected = (speed == 0.25f)
        binding.btnSpeed05xPortrait.isSelected = (speed == 0.5f)
        binding.btnSpeed1xPortrait.isSelected = (speed == 1.0f)
        binding.btnSpeed2xPortrait.isSelected = (speed == 2.0f)
        
        // Restart playback if playing
        if (isPlaying) {
            pausePlayback()
            startPlayback()
        }
    }
    
    private fun updateFrameAnalysis(frameIndex: Int) {
        val result = videoDebugProcessor.getFrameResult(frameIndex) ?: return
        
        // Update pose overlay with actual video dimensions
        val poseResult = videoDebugProcessor.getFramePoseResult(frameIndex)
        if (poseResult != null && videoHeight > 0 && videoWidth > 0) {
            binding.overlayView.setResults(
                poseResult,
                videoHeight,
                videoWidth,
                RunningMode.VIDEO
            )
            binding.overlayView.invalidate()
        }
        
        // Update analysis text
        binding.tvFrameNumber.text = "Frame: $frameIndex / $totalFrames"
        binding.tvWristAngle.text = "Wrist Angle: %.1f°%s".format(
            Locale.US,
            result.wristAngle,
            if (result.isWristAngleValid) " ✓" else " ✗"
        )
        binding.tvBodyRotation.text = "Body Rotation: %.1f°%s".format(
            Locale.US,
            result.bodyRotation,
            if (result.isBodyRotationValid) " ✓" else " ✗"
        )
        binding.tvFollowThrough.text = "Follow-Through: %.1f°%s".format(
            Locale.US,
            result.followThroughAngle,
            if (result.isFollowThroughValid) " ✓" else " ✗"
        )
        binding.tvContactHeight.text = "Contact Height: %.2f%s".format(
            Locale.US,
            result.contactHeight,
            if (result.isContactHeightValid) " ✓" else " ✗"
        )
        binding.tvElbowDistance.text = "Elbow Distance: %.2fm%s".format(
            Locale.US,
            result.elbowBodyDistance,
            if (result.isElbowPositionValid) " ✓" else " ✗"
        )
        binding.tvPhase.text = "Phase: ${result.phase.name}"
        binding.tvScore.text = "Score: ${result.overallScore}%"
        
        // Color code score
        binding.tvScore.setTextColor(when {
            result.overallScore >= 80 -> getColor(android.R.color.holo_green_dark)
            result.overallScore >= 60 -> getColor(android.R.color.holo_orange_dark)
            else -> getColor(android.R.color.holo_red_dark)
        })
        
        // Display feedback
        val feedback = feedbackGenerator.generateShortFeedback(result)
        binding.tvCurrentFeedback.text = feedback
    }
    
    private fun updateAnalysisInfo() {
        val summary = videoDebugProcessor.getAnalysisSummary()
        
        binding.tvTotalFrames.text = "Total Frames: ${summary.totalFrames}"
        binding.tvStrokesDetected.text = "Strokes Detected: ${summary.strokeCount}"
        binding.tvAvgScore.text = "Avg Score: %.1f%%".format(Locale.US, summary.averageScore)
        binding.tvGoodStrokes.text = "Good Strokes: ${summary.goodStrokes} (${summary.successRate.toInt()}%)"
        
        // Phase distribution
        val phaseText = buildString {
            append("Phase Distribution:\n")
            summary.phaseDistribution.forEach { (phase, count) ->
                append("  ${phase.name}: $count frames\n")
            }
        }
        binding.tvPhaseDistribution.text = phaseText
    }
    
    private fun updateVideoInfo() {
        val infoText = "Duration: %.1fs | Frames: %d | FPS: %d".format(
            Locale.US,
            videoDurationMs / 1000.0,
            totalFrames,
            DEFAULT_FPS
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
        
        // Hide top bar and bottom controls panel
        binding.topBar.visibility = View.GONE
        binding.controlsPanel.visibility = View.GONE
        
        // Show portrait controls in analysis panel
        binding.portraitTopControls.visibility = View.VISIBLE
        binding.portraitControls.visibility = View.VISIBLE
        
        // Sync video info text
        binding.tvVideoInfoPortrait.text = binding.tvVideoInfo.text
        
        // Make video container full height
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
        
        // Show top bar and bottom controls panel
        binding.topBar.visibility = View.VISIBLE
        binding.controlsPanel.visibility = View.VISIBLE
        
        // Hide portrait controls in analysis panel
        binding.portraitTopControls.visibility = View.GONE
        binding.portraitControls.visibility = View.GONE
        
        // Restore video container to split with controls
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
                    1 -> {
                        // TODO: Implement gallery picker
                        // For now, just use default
                        loadDefaultVideo()
                    }
                }
            }
            .show()
    }
    
    private fun resetAnalysis() {
        pausePlayback()
        currentFrameIndex = 0
        seekToFrame(0)
        videoDebugProcessor.reset()
        updateAnalysisInfo()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        pausePlayback()
        playbackTask?.cancel(true)
        playbackExecutor.shutdown()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        videoDebugProcessor.close()
    }
}
