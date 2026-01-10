/*
 * AI Coach for Table Tennis
 * Debug UI Controller - Manages UI updates for debug activity
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.content.Context
import android.view.View
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityDebugBinding
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.processors.VideoDebugProcessor
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
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
        binding.tvPhase.text = "Phase: ${result.phase.name}"
        binding.tvScore.text = "Score: ${result.overallScore}%"
        binding.tvScore.setTextColor(when {
            result.overallScore >= 80 -> context.getColor(android.R.color.holo_green_dark)
            result.overallScore >= 60 -> context.getColor(android.R.color.holo_orange_dark)
            else -> context.getColor(android.R.color.holo_red_dark)
        })
        binding.tvCurrentFeedback.text = feedbackGenerator.generateShortFeedback(result)
    }

    fun updateAnalysisInfo() {
        val summary = videoDebugProcessor.getAnalysisSummary()
        binding.tvTotalFrames.text = "Total Frames: ${summary.totalFrames}"
        binding.tvStrokesDetected.text = "Strokes Detected: ${summary.strokeCount}"
        binding.tvAvgScore.text = "Avg Score: %.1f%%".format(Locale.US, summary.averageScore)
        binding.tvGoodStrokes.text = "Good Strokes: ${summary.goodStrokes} (${summary.successRate.toInt()}%)"
        binding.tvPhaseDistribution.text = buildString {
            append("Phase Distribution:\n")
            summary.phaseDistribution.forEach { (phase, count) -> append("  ${phase.name}: $count frames\n") }
        }
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
        binding.btnPlayPausePortrait.text = text
    }

    fun updateSpeedButtons(speed: Float) {
        binding.btnSpeed025x.isSelected = (speed == 0.25f)
        binding.btnSpeed05x.isSelected = (speed == 0.5f)
        binding.btnSpeed1x.isSelected = (speed == 1.0f)
        binding.btnSpeed2x.isSelected = (speed == 2.0f)
        binding.btnSpeed025xPortrait.isSelected = (speed == 0.25f)
        binding.btnSpeed05xPortrait.isSelected = (speed == 0.5f)
        binding.btnSpeed1xPortrait.isSelected = (speed == 1.0f)
        binding.btnSpeed2xPortrait.isSelected = (speed == 2.0f)
    }

    fun updateSeekBar(positionMs: Int) {
        binding.seekBarFrame.progress = positionMs
        binding.seekBarFramePortrait.progress = positionMs
    }

    fun setupSeekBar(maxMs: Int) {
        binding.seekBarFrame.max = maxMs
        binding.seekBarFramePortrait.max = maxMs
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
            binding.controlsPanel.visibility = View.GONE
            binding.portraitTopControls.visibility = View.VISIBLE
            binding.portraitControls.visibility = View.VISIBLE
            binding.tvVideoInfoPortrait.text = binding.tvVideoInfo.text
            val params = binding.mainContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            binding.mainContent.layoutParams = params
        } else {
            binding.topBar.visibility = View.VISIBLE
            binding.controlsPanel.visibility = View.VISIBLE
            binding.portraitTopControls.visibility = View.GONE
            binding.portraitControls.visibility = View.GONE
            val params = binding.mainContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            params.topToBottom = binding.topBar.id
            params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            params.bottomToTop = binding.controlsPanel.id
            binding.mainContent.layoutParams = params
        }
    }

    fun setToggleViewButtonVisibility(visible: Boolean) {
        binding.btnToggleViewMode.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
