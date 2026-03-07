package com.ttcoachai.managers

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.ttcoachai.databinding.ActivityDebugBinding
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.StrokePhase
import com.ttcoachai.processors.VideoDebugProcessor
import com.ttcoachai.services.FeedbackGenerator
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.ttcoachai.R

class DebugUIController(
    private val context: Context,
    private val binding: ActivityDebugBinding,
    private val videoDebugProcessor: VideoDebugProcessor,
    private val feedbackGenerator: FeedbackGenerator
) {
    private val feedbackAdapter = FeedbackLogAdapter()
    private val logManager = FeedbackLogManager(feedbackAdapter)
    private var videoWidth = 0
    private var videoHeight = 0
    private var videoDurationMs = 0L

    init {
        binding.rvFeedbackHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = feedbackAdapter
        }
    }

    fun setVideoDimensions(w: Int, h: Int) { videoWidth = w; videoHeight = h }
    fun setVideoDuration(ms: Long) { videoDurationMs = ms }

    fun updatePoseOverlay(res: PoseLandmarkerResult) {
        if (videoHeight > 0 && videoWidth > 0) {
            binding.overlayView.setResults(res, videoHeight, videoWidth, RunningMode.VIDEO)
        }
    }

    fun updatePoseOverlay(lms: List<NormalizedLandmark>?) {
        if (videoHeight > 0 && videoWidth > 0 && lms != null) {
            binding.overlayView.setResults(listOf(lms), videoHeight, videoWidth, RunningMode.VIDEO)
            binding.overlayView.invalidate()
        }
    }

    fun updatePoseOverlayWithPhase(idx: Int, lms: List<NormalizedLandmark>?) {
        if (videoHeight > 0 && videoWidth > 0 && lms != null) {
            if (videoDebugProcessor.hasStrokeDetection()) {
                binding.overlayView.setPhaseColoringEnabled(true)
                binding.overlayView.setStrokePhase(videoDebugProcessor.getStrokePhaseForFrame(idx))
            }
            binding.overlayView.setResults(listOf(lms), videoHeight, videoWidth, RunningMode.VIDEO)
            binding.overlayView.invalidate()
        }
    }

    fun setPhaseColoringEnabled(en: Boolean) { binding.overlayView.setPhaseColoringEnabled(en) }
    fun clearPoseOverlay() { binding.overlayView.clear() }

    fun updateFrameAnalysisUI(idx: Int, res: AnalysisResult) {
        val total = videoDebugProcessor.getTotalFrames()
        binding.tvFrameNumber.text = "Frame: $idx / $total"
        
        binding.tvWristAngle.text = "Wrist: %.1f%s".format(res.wristAngle, if (res.isWristAngleValid) " ✓" else " ✗")
        binding.tvBodyRotation.text = "Body: %.1f%s".format(res.bodyRotation, if (res.isBodyRotationValid) " ✓" else " ✗")
        binding.tvFollowThrough.text = "Follow: %.1f%s".format(res.followThroughAngle, if (res.isFollowThroughValid) " ✓" else " ✗")
        binding.tvContactHeight.text = "Height: %.1f%s".format(res.contactHeight, if (res.isContactHeightValid) " ✓" else " ✗")
        binding.tvElbowDistance.text = "Elbow: %.1f%s".format(res.elbowBodyDistance, if (res.isElbowPositionValid) " ✓" else " ✗")

        val phase = if (videoDebugProcessor.hasStrokeDetection()) videoDebugProcessor.getStrokePhaseForFrame(idx) else res.phase
        binding.tvPhase.text = "Phase: ${phase.name}"
        binding.tvPhase.setTextColor(getPhaseColor(phase))

        binding.tvScore.text = "Score: ${res.overallScore}"
        binding.tvScore.setTextColor(context.getColor(if (res.overallScore >= 80) android.R.color.holo_green_dark else if (res.overallScore >= 60) android.R.color.holo_orange_dark else android.R.color.holo_red_dark))

        if (videoDebugProcessor.hasStrokeDetection()) {
            val stroke = videoDebugProcessor.getStrokeForFrame(idx)
            val strokes = videoDebugProcessor.getDetectedStrokes()
            if (stroke != null) {
                binding.tvStrokesDetected.text = "Stroke: ${stroke.strokeIndex + 1} / ${strokes.size}"
                logManager.append(stroke.strokeIndex + 1, phase, stroke.peakVelocity)
            } else {
                binding.tvStrokesDetected.text = "Strokes: ${strokes.size}"
            }
        } else {
            logManager.append(0, res.phase, 0f)
        }
    }

    fun clearFeedbackHistory() { logManager.clear() }

    private fun getPhaseColor(p: StrokePhase) = context.getColor(when(p) {
        StrokePhase.READY -> android.R.color.darker_gray
        StrokePhase.BACKSWING -> android.R.color.holo_blue_dark
        StrokePhase.FORWARD_SWING -> android.R.color.holo_green_dark
        StrokePhase.CONTACT -> android.R.color.holo_orange_dark
        StrokePhase.FOLLOW_THROUGH -> android.R.color.holo_purple
        StrokePhase.RECOVERY -> android.R.color.darker_gray
    })

    fun updateAnalysisInfo() {
        val sum = videoDebugProcessor.getAnalysisSummary()
        binding.tvTotalFrames.text = "Total Frames: ${sum.totalFrames}"
        if (videoDebugProcessor.hasStrokeDetection()) {
            val res = videoDebugProcessor.getStrokeDetectionResult()
            binding.tvStrokesDetected.text = "Strokes: ${res?.strokes?.size ?: 0}"
            binding.tvPhaseDistribution.text = "Phase distribution available in strokes"
        } else {
            binding.tvStrokesDetected.text = "Strokes: ${sum.strokeCount}"
            binding.tvPhaseDistribution.text = "Score: %.1f".format(sum.averageScore)
        }
        binding.tvAvgScore.text = "Avg Score: %.1f".format(sum.averageScore)
        binding.tvGoodStrokes.text = "Good: ${sum.goodStrokes} (${sum.successRate.toInt()}%)"
    }

    fun updateVideoInfo() {
        val text = "Video: %.1fs (%d frames @ %dms)".format(videoDurationMs / 1000.0, videoDebugProcessor.getTotalFrames(), VideoDebugProcessor.VIDEO_INTERVAL_MS)
        binding.tvVideoInfo.text = text
        binding.tvVideoInfoPortrait.text = text
    }

    fun updatePlaybackButton(playing: Boolean) { binding.btnPlayPause.text = if (playing) "Pause" else "Play" }
    fun updateSpeedButtons(s: Float) {
        binding.btnSpeed025x.isSelected = (s == 0.25f); binding.btnSpeed05x.isSelected = (s == 0.5f)
        binding.btnSpeed1x.isSelected = (s == 1.0f); binding.btnSpeed2x.isSelected = (s == 2.0f)
    }
    fun updateSeekBar(pos: Int) { binding.seekBarFrame.progress = pos }
    fun setupSeekBar(max: Int) { binding.seekBarFrame.max = max }
    fun showProgress() { binding.progressBar.visibility = View.VISIBLE; binding.analysisPanel.visibility = View.GONE }
    fun hideProgress() { binding.progressBar.visibility = View.GONE; binding.analysisPanel.visibility = View.VISIBLE }
    fun enableLogButton() { binding.btnLogPoses.isEnabled = true; binding.btnLogPosesPortrait.isEnabled = true }
    fun showVideoView() { binding.frameImageView.visibility = View.GONE; binding.videoView.visibility = View.VISIBLE }
    fun showFrameImage() { binding.frameImageView.visibility = View.VISIBLE; binding.videoView.visibility = View.INVISIBLE }
    fun updateToggleViewButton(port: Boolean) { val t = if (port) "Landscape" else "Portrait"; binding.btnToggleViewMode.text = t; binding.btnToggleViewModePortrait.text = t }

    fun setPortraitMode(en: Boolean) {
        if (en) {
            binding.topBar.visibility = View.GONE; binding.portraitTopControls.visibility = View.VISIBLE
            val lp = binding.mainContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            lp.topToBottom = -1; lp.topToTop = 0; binding.mainContent.layoutParams = lp
        } else {
            binding.topBar.visibility = View.VISIBLE; binding.portraitTopControls.visibility = View.GONE
            binding.analysisPanel.visibility = View.VISIBLE
            val lp = binding.mainContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            lp.topToTop = -1; lp.topToBottom = binding.topBar.id; binding.mainContent.layoutParams = lp
        }
    }
    fun setToggleViewButtonVisibility(vis: Boolean) { binding.btnToggleViewMode.visibility = if (vis) View.VISIBLE else View.GONE }
}
