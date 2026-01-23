/*
 * AI Coach for Table Tennis
 * Debug UI Controller - Manages UI updates for debug activity
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityDebugBinding
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
import com.google.mediapipe.examples.poselandmarker.processors.VideoDebugProcessor
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.examples.poselandmarker.R
import java.util.Locale

/**
 * Controller class for managing the UI of the Debug Activity.
 */
class DebugUIController(
    private val context: Context,
    private val binding: ActivityDebugBinding,
    private val videoDebugProcessor: VideoDebugProcessor,
    private val feedbackGenerator: FeedbackGenerator
) {
    // UI Helpers
    private val feedbackAdapter = FeedbackLogAdapter()
    private val feedbackEntries = mutableListOf<FeedbackLogEntry>()

    // State
    private var videoWidth = 0
    private var videoHeight = 0
    private var videoDurationMs = 0L
    private var lastDisplayedStroke = -1
    private var lastDisplayedPhase: StrokePhase? = null

    private val strokeColors = listOf(
        0xFFE91E63.toInt(), // Pink
        0xFF2196F3.toInt(), // Blue
        0xFF4CAF50.toInt(), // Green
        0xFFFF9800.toInt(), // Orange
        0xFF9C27B0.toInt(), // Purple
        0xFF00BCD4.toInt()  // Cyan
    )

    init {
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        binding.rvFeedbackHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = feedbackAdapter
        }
    }

    // --- Video & Overlay Management ---

    fun setVideoDimensions(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
    }

    fun setVideoDuration(durationMs: Long) {
        videoDurationMs = durationMs
    }

    fun updatePoseOverlay(poseResult: PoseLandmarkerResult) {
        if (videoHeight > 0 && videoWidth > 0) {
            binding.overlayView.setResults(poseResult, videoHeight, videoWidth, RunningMode.VIDEO)
            binding.overlayView.invalidate()
        }
    }

    fun updatePoseOverlay(landmarks: List<NormalizedLandmark>?) {
        if (videoHeight > 0 && videoWidth > 0 && landmarks != null) {
            binding.overlayView.setResults(listOf(landmarks), videoHeight, videoWidth, RunningMode.VIDEO)
            binding.overlayView.invalidate()
        }
    }

    fun updatePoseOverlayWithPhase(frameIndex: Int, landmarks: List<NormalizedLandmark>?) {
        if (videoHeight > 0 && videoWidth > 0 && landmarks != null) {
            if (videoDebugProcessor.hasStrokeDetection()) {
                val phase = videoDebugProcessor.getStrokePhaseForFrame(frameIndex)
                binding.overlayView.setPhaseColoringEnabled(true)
                binding.overlayView.setStrokePhase(phase)
            }
            binding.overlayView.setResults(listOf(landmarks), videoHeight, videoWidth, RunningMode.VIDEO)
            binding.overlayView.invalidate()
        }
    }

    fun setPhaseColoringEnabled(enabled: Boolean) {
        binding.overlayView.setPhaseColoringEnabled(enabled)
    }

    fun clearPoseOverlay() {
        binding.overlayView.clear()
    }

    // --- Frame Analysis UI ---

    fun updateFrameAnalysisUI(resultIndex: Int, result: AnalysisResult) {
        val totalFrames = videoDebugProcessor.getTotalFrames()
        binding.tvFrameNumber.text = context.getString(R.string.format_frame_counter, resultIndex, totalFrames)
        
        // Metrics
        binding.tvWristAngle.text = context.getString(R.string.format_wrist_angle, result.wristAngle, if (result.isWristAngleValid) " ✓" else " ✗")
        binding.tvBodyRotation.text = context.getString(R.string.format_body_rotation, result.bodyRotation, if (result.isBodyRotationValid) " ✓" else " ✗")
        binding.tvFollowThrough.text = context.getString(R.string.format_follow_through, result.followThroughAngle, if (result.isFollowThroughValid) " ✓" else " ✗")
        binding.tvContactHeight.text = context.getString(R.string.format_contact_height, result.contactHeight, if (result.isContactHeightValid) " ✓" else " ✗")
        binding.tvElbowDistance.text = context.getString(R.string.format_elbow_distance, result.elbowBodyDistance, if (result.isElbowPositionValid) " ✓" else " ✗")

        // Phase Info
        val strokePhase = if (videoDebugProcessor.hasStrokeDetection()) {
            videoDebugProcessor.getStrokePhaseForFrame(resultIndex)
        } else {
            result.phase
        }
        binding.tvPhase.text = context.getString(R.string.format_phase, strokePhase.name)
        binding.tvPhase.setTextColor(getPhaseColor(strokePhase))

        // Score
        binding.tvScore.text = context.getString(R.string.format_score, result.overallScore.toString())
        binding.tvScore.setTextColor(when {
            result.overallScore >= 80 -> context.getColor(android.R.color.holo_green_dark)
            result.overallScore >= 60 -> context.getColor(android.R.color.holo_orange_dark)
            else -> context.getColor(android.R.color.holo_red_dark)
        })

        // Feedback Logic
        if (videoDebugProcessor.hasStrokeDetection()) {
            updateStrokeInfo(resultIndex)
        } else {
            appendFeedbackEntry(0, result.phase, 0f)
        }
    }

    private fun updateStrokeInfo(frameIndex: Int) {
        val stroke = videoDebugProcessor.getStrokeForFrame(frameIndex)
        val strokes = videoDebugProcessor.getDetectedStrokes()
        val phase = videoDebugProcessor.getStrokePhaseForFrame(frameIndex)

        stroke?.let { s ->
            val strokeNum = s.strokeIndex + 1
            binding.tvStrokesDetected.text = context.getString(R.string.format_stroke_counter, strokeNum, strokes.size)
            appendFeedbackEntry(strokeNum, phase, s.peakVelocity)
        } ?: run {
            binding.tvStrokesDetected.text = context.getString(R.string.format_strokes_count_between, strokes.size)
        }
    }

    // --- Feedback Log Management ---

    private fun appendFeedbackEntry(strokeNum: Int, phase: StrokePhase, peakVelocity: Float) {
        if (strokeNum == lastDisplayedStroke && phase == lastDisplayedPhase) return

        lastDisplayedStroke = strokeNum
        lastDisplayedPhase = phase

        val feedback = generatePhaseFeedback(phase, peakVelocity)
        val phaseName = formatPhaseName(phase)
        val strokeLabel = if (strokeNum > 0) strokeNum.toString() else "RT"
        val label = "$strokeLabel-$phaseName:"

        val color = if (strokeNum > 0) {
            strokeColors[(strokeNum - 1) % strokeColors.size]
        } else {
            Color.BLACK
        }

        val entry = FeedbackLogEntry(label, feedback, color)
        feedbackEntries.add(entry)
        feedbackAdapter.updateList(feedbackEntries)
        binding.rvFeedbackHistory.scrollToPosition(feedbackEntries.size - 1)
    }

    fun clearFeedbackHistory() {
        feedbackEntries.clear()
        feedbackAdapter.updateList(feedbackEntries)
        lastDisplayedStroke = -1
        lastDisplayedPhase = null
    }

    private fun formatPhaseName(phase: StrokePhase): String {
        return phase.name.split("_").joinToString("") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun generatePhaseFeedback(phase: StrokePhase, peakVelocity: Float): String {
        return when (phase) {
            StrokePhase.READY -> "Ready position"
            StrokePhase.BACKSWING -> "Start rotation"
            StrokePhase.FORWARD_SWING -> "Accelerate (v=%.3f)".format(Locale.US, peakVelocity)
            StrokePhase.CONTACT -> "Ball contact"
            StrokePhase.FOLLOW_THROUGH -> "Complete swing"
            StrokePhase.RECOVERY -> "Return to ready"
        }
    }

    private fun getPhaseColor(phase: StrokePhase): Int {
        return when (phase) {
            StrokePhase.READY -> context.getColor(android.R.color.darker_gray)
            StrokePhase.BACKSWING -> context.getColor(android.R.color.holo_blue_dark)
            StrokePhase.FORWARD_SWING -> context.getColor(android.R.color.holo_green_dark)
            StrokePhase.CONTACT -> context.getColor(android.R.color.holo_orange_dark)
            StrokePhase.FOLLOW_THROUGH -> context.getColor(android.R.color.holo_purple)
            StrokePhase.RECOVERY -> context.getColor(android.R.color.darker_gray)
        }
    }

    // --- General Info Updates ---

    fun updateAnalysisInfo() {
        val summary = videoDebugProcessor.getAnalysisSummary()
        binding.tvTotalFrames.text = context.getString(R.string.format_total_frames, summary.totalFrames)

        if (videoDebugProcessor.hasStrokeDetection()) {
            val strokes = videoDebugProcessor.getDetectedStrokes()
            binding.tvStrokesDetected.text = context.getString(R.string.format_strokes_detected, strokes.size)
            
            videoDebugProcessor.getStrokeDetectionResult()?.let { result ->
                val phaseCount = result.framePhases.groupBy { it.phase }
                    .mapValues { it.value.size }
                binding.tvPhaseDistribution.text = buildString {
                    append("Phase Distribution:\n")
                    phaseCount.forEach { (phase, count) -> append("  ${phase.name}: $count frames\n") }
                }
            }
        } else {
            binding.tvStrokesDetected.text = context.getString(R.string.format_strokes_detected, summary.strokeCount)
            binding.tvPhaseDistribution.text = buildString {
                append("Phase Distribution:\n")
                summary.phaseDistribution.forEach { (phase, count) -> append("  ${phase.name}: $count frames\n") }
            }
        }

        binding.tvAvgScore.text = context.getString(R.string.format_avg_score, summary.averageScore)
        binding.tvGoodStrokes.text = context.getString(R.string.format_good_strokes, summary.goodStrokes, summary.successRate.toInt())
    }

    fun updateVideoInfo() {
        val totalFrames = videoDebugProcessor.getTotalFrames()
        val infoText = context.getString(R.string.format_video_info, videoDurationMs / 1000.0, totalFrames, VideoDebugProcessor.VIDEO_INTERVAL_MS)
        binding.tvVideoInfo.text = infoText
        binding.tvVideoInfoPortrait.text = infoText
    }

    // --- Playback UI Controls ---

    fun updatePlaybackButton(isPlaying: Boolean) {
        binding.btnPlayPause.text = if (isPlaying) context.getString(R.string.btn_pause_text) else context.getString(R.string.btn_play_text)
    }

    fun updateSpeedButtons(speed: Float) {
        binding.btnSpeed025x.isSelected = (speed == 0.25f)
        binding.btnSpeed05x.isSelected = (speed == 0.5f)
        binding.btnSpeed1x.isSelected = (speed == 1.0f)
        binding.btnSpeed2x.isSelected = (speed == 2.0f)
    }

    fun updateSeekBar(positionMs: Int) {
        binding.seekBarFrame.progress = positionMs
    }

    fun setupSeekBar(maxMs: Int) {
        binding.seekBarFrame.max = maxMs
    }

    fun showProgress() {
        binding.progressBar.visibility = View.VISIBLE
        binding.analysisPanel.visibility = View.GONE
    }

    fun hideProgress() {
        binding.progressBar.visibility = View.GONE
        binding.analysisPanel.visibility = View.VISIBLE
    }

    fun enableLogButton() {
        binding.btnLogPoses.isEnabled = true
        binding.btnLogPosesPortrait.isEnabled = true
    }

    fun showVideoView() {
        binding.frameImageView.visibility = View.GONE
        binding.videoView.visibility = View.VISIBLE
    }

    fun showFrameImage() {
        binding.frameImageView.visibility = View.VISIBLE
        binding.videoView.visibility = View.INVISIBLE
    }

    fun updateToggleViewButton(isPortraitMode: Boolean) {
        val text = if (isPortraitMode) context.getString(R.string.btn_landscape_mode) else context.getString(R.string.btn_portrait_mode)
        binding.btnToggleViewMode.text = text
        binding.btnToggleViewModePortrait.text = text
    }

    fun setPortraitMode(enabled: Boolean) {
        if (enabled) {
            binding.topBar.visibility = View.GONE
            binding.portraitTopControls.visibility = View.VISIBLE
            binding.analysisPanel.visibility = View.VISIBLE
            binding.tvVideoInfoPortrait.text = binding.tvVideoInfo.text
            val params = binding.mainContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            binding.mainContent.layoutParams = params
        } else {
            binding.topBar.visibility = View.VISIBLE
            binding.portraitTopControls.visibility = View.GONE
            val params = binding.mainContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            params.topToBottom = binding.topBar.id
            binding.mainContent.layoutParams = params
        }
    }

    fun setToggleViewButtonVisibility(visible: Boolean) {
        binding.btnToggleViewMode.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
