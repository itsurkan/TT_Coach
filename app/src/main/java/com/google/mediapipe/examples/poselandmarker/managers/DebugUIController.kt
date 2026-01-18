/*
 * AI Coach for Table Tennis
 * Debug UI Controller - Manages UI updates for debug activity
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.content.Context
import android.view.View
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityDebugBinding
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
import com.google.mediapipe.examples.poselandmarker.processors.VideoDebugProcessor
import com.google.mediapipe.examples.poselandmarker.services.DetectedStroke
import com.google.mediapipe.examples.poselandmarker.services.StrokeDetectionResult
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.Locale

class DebugUIController(
    private val context: Context,
    private val binding: ActivityDebugBinding,
    private val videoDebugProcessor: VideoDebugProcessor,
    private val feedbackGenerator: FeedbackGenerator
) {
    private var videoWidth = 0
    private var videoHeight = 0
    private var videoDurationMs = 0L

    // Feedback history for accumulated display
    private val feedbackHistory = StringBuilder()
    private var lastDisplayedStroke = -1
    private var lastDisplayedPhase: StrokePhase? = null

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

    /**
     * Update pose overlay with phase coloring based on frame index
     */
    fun updatePoseOverlayWithPhase(frameIndex: Int, landmarks: List<NormalizedLandmark>?) {
        if (videoHeight > 0 && videoWidth > 0 && landmarks != null) {
            // Enable phase coloring if stroke detection is available
            if (videoDebugProcessor.hasStrokeDetection()) {
                val phase = videoDebugProcessor.getStrokePhaseForFrame(frameIndex)
                binding.overlayView.setPhaseColoringEnabled(true)
                binding.overlayView.setStrokePhase(phase)
            }
            binding.overlayView.setResults(listOf(landmarks), videoHeight, videoWidth, RunningMode.VIDEO)
            binding.overlayView.invalidate()
        }
    }

    /**
     * Enable/disable phase coloring on overlay
     */
    fun setPhaseColoringEnabled(enabled: Boolean) {
        binding.overlayView.setPhaseColoringEnabled(enabled)
    }

    fun clearPoseOverlay() {
        binding.overlayView.clear()
    }

    fun updateFrameAnalysisUI(resultIndex: Int, result: AnalysisResult) {
        val totalFrames = videoDebugProcessor.getTotalFrames()
        binding.tvFrameNumber.text = "Frame: $resultIndex / $totalFrames"
        binding.tvWristAngle.text = "Wrist Angle: %.1f°%s".format(Locale.US, result.wristAngle, if (result.isWristAngleValid) " ✓" else " ✗")
        binding.tvBodyRotation.text = "Body Rotation: %.1f°%s".format(Locale.US, result.bodyRotation, if (result.isBodyRotationValid) " ✓" else " ✗")
        binding.tvFollowThrough.text = "Follow-Through: %.1f°%s".format(Locale.US, result.followThroughAngle, if (result.isFollowThroughValid) " ✓" else " ✗")
        binding.tvContactHeight.text = "Contact Height: %.2f%s".format(Locale.US, result.contactHeight, if (result.isContactHeightValid) " ✓" else " ✗")
        binding.tvElbowDistance.text = "Elbow Distance: %.2fm%s".format(Locale.US, result.elbowBodyDistance, if (result.isElbowPositionValid) " ✓" else " ✗")

        // Use stroke detection phase if available, otherwise use analysis result phase
        val strokePhase = if (videoDebugProcessor.hasStrokeDetection()) {
            videoDebugProcessor.getStrokePhaseForFrame(resultIndex)
        } else {
            result.phase
        }
        binding.tvPhase.text = "Phase: ${strokePhase.name}"
        binding.tvPhase.setTextColor(getPhaseColor(strokePhase))

        binding.tvScore.text = "Score: ${result.overallScore}%"
        binding.tvScore.setTextColor(when {
            result.overallScore >= 80 -> context.getColor(android.R.color.holo_green_dark)
            result.overallScore >= 60 -> context.getColor(android.R.color.holo_orange_dark)
            else -> context.getColor(android.R.color.holo_red_dark)
        })

        // Update stroke info if stroke detection is available, otherwise show short feedback
        if (videoDebugProcessor.hasStrokeDetection()) {
            updateStrokeInfo(resultIndex)
        } else {
            binding.tvCurrentFeedback.text = feedbackGenerator.generateShortFeedback(result)
        }
    }

    /**
     * Update stroke detection info display
     */
    fun updateStrokeInfo(frameIndex: Int) {
        val stroke = videoDebugProcessor.getStrokeForFrame(frameIndex)
        val strokes = videoDebugProcessor.getDetectedStrokes()
        val phase = videoDebugProcessor.getStrokePhaseForFrame(frameIndex)

        stroke?.let { s ->
            val strokeNum = s.strokeIndex + 1
            binding.tvStrokesDetected.text = "Stroke: $strokeNum / ${strokes.size}"

            // Append new feedback line when stroke or phase changes
            appendFeedbackEntry(strokeNum, phase, s.peakVelocity)
        } ?: run {
            binding.tvStrokesDetected.text = "Strokes: ${strokes.size} (between strokes)"
        }
    }

    /**
     * Append a feedback entry to the history
     * Format: stroke: N, phase: PhaseName, Feedback: details
     */
    private fun appendFeedbackEntry(strokeNum: Int, phase: StrokePhase, peakVelocity: Float) {
        // Only add new entry when stroke or phase changes
        if (strokeNum == lastDisplayedStroke && phase == lastDisplayedPhase) {
            return
        }

        lastDisplayedStroke = strokeNum
        lastDisplayedPhase = phase

        // Generate feedback based on phase
        val feedback = generatePhaseFeedback(phase, peakVelocity)

        // Append new line to history
        if (feedbackHistory.isNotEmpty()) {
            feedbackHistory.append("\n")
        }
        feedbackHistory.append("stroke: $strokeNum, phase: ${phase.name}, Feedback: $feedback")

        // Update the display
        binding.tvCurrentFeedback.text = feedbackHistory.toString()
    }

    /**
     * Generate feedback message based on phase
     */
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

    /**
     * Clear the feedback history (call when loading new video)
     */
    fun clearFeedbackHistory() {
        feedbackHistory.clear()
        lastDisplayedStroke = -1
        lastDisplayedPhase = null
        binding.tvCurrentFeedback.text = ""
    }

    /**
     * Get color for stroke phase visualization
     */
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

    fun updateAnalysisInfo() {
        val summary = videoDebugProcessor.getAnalysisSummary()
        binding.tvTotalFrames.text = "Total Frames: ${summary.totalFrames}"

        // Use stroke detection count if available
        if (videoDebugProcessor.hasStrokeDetection()) {
            val strokes = videoDebugProcessor.getDetectedStrokes()
            binding.tvStrokesDetected.text = "Strokes Detected: ${strokes.size}"

            // Calculate stroke-based phase distribution
            val result = videoDebugProcessor.getStrokeDetectionResult()
            if (result != null) {
                val phaseCount = result.framePhases.groupBy { it.phase }
                    .mapValues { it.value.size }
                binding.tvPhaseDistribution.text = buildString {
                    append("Phase Distribution:\n")
                    phaseCount.forEach { (phase, count) ->
                        append("  ${phase.name}: $count frames\n")
                    }
                }
            }
        } else {
            binding.tvStrokesDetected.text = "Strokes Detected: ${summary.strokeCount}"
            binding.tvPhaseDistribution.text = buildString {
                append("Phase Distribution:\n")
                summary.phaseDistribution.forEach { (phase, count) -> append("  ${phase.name}: $count frames\n") }
            }
        }

        binding.tvAvgScore.text = "Avg Score: %.1f%%".format(Locale.US, summary.averageScore)
        binding.tvGoodStrokes.text = "Good Strokes: ${summary.goodStrokes} (${summary.successRate.toInt()}%)"
    }

    fun updateVideoInfo() {
        val totalFrames = videoDebugProcessor.getTotalFrames()
        val infoText = "Duration: %.1fs | Frames: %d | Interval: %dms".format(Locale.US, videoDurationMs / 1000.0, totalFrames, VideoDebugProcessor.VIDEO_INTERVAL_MS)
        binding.tvVideoInfo.text = infoText
        binding.tvVideoInfoPortrait.text = infoText
    }

    fun updatePlaybackButton(isPlaying: Boolean) {
        val text = if (isPlaying) "⏸ Pause" else "▶ Play"
        binding.btnPlayPause.text = text
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
        val text = if (isPortraitMode) "🖥 Landscape Mode" else "📱 Portrait Mode"
        binding.btnToggleViewMode.text = text
        binding.btnToggleViewModePortrait.text = text
    }

    fun setPortraitMode(enabled: Boolean) {
        if (enabled) {
            binding.topBar.visibility = View.GONE
            binding.portraitTopControls.visibility = View.VISIBLE
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
