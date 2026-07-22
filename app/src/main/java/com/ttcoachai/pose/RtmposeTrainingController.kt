package com.ttcoachai.pose

// RtmposeTrainingController.kt
//
// Encapsulates the RTMPose live-drill path for the MAIN training screen
// (TrainingActivity), so the Activity itself stays thin. This is the
// "production" sibling of RtmposeDrillActivity (the debug-only dev tool) —
// same backend/processor/CameraX wiring, but:
//   - runs inside a container the caller owns (no dedicated layout/Activity)
//   - always FEEDBACK mode against an already-calibrated baseline (no
//     in-screen calibration flow)
//   - speaks via PresetVoiceController (recorded clips + TTS fallback), not
//     the plain-TTS DrillTtsController
//   - bridges LiveDrillSession output into TrainingStateManager so the
//     existing stats/UI/session-save plumbing keeps working unchanged.
//
// Freeze discipline: does not call into PoseAnalysisProcessor, CameraFragment,
// PoseLandmarkerProcessor, or CalibrationStateManager. This is a parallel path.

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ttcoachai.LocaleHelper
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.managers.TrainingStateManager
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.drill.LiveDrillSession
import com.ttcoachai.shared.drill.RepEvent
import com.ttcoachai.shared.drill.SpokenFeedback
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.FeedbackItem
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PersonalBaseline
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the whole RTMPose live path for [com.ttcoachai.TrainingActivity]: builds the
 * preview + skeleton overlay, binds CameraX, runs [RtmposeBackend] through
 * [RtmposeFrameProcessor], drives a [LiveDrillSession] against [baseline], speaks
 * feedback via [PresetVoiceController], and bridges rep/feedback events into
 * [stateManager] so existing stats/session-save code needs no changes.
 *
 * [start] returns false (and logs) if the RTMPose backend fails to construct — the
 * caller is expected to fall back to the legacy MediaPipe pipeline in that case.
 */
class RtmposeTrainingController(
    private val activity: FragmentActivity,
    private val container: ViewGroup,
    private val stateManager: TrainingStateManager,
    private val settingsManager: SettingsManager,
    private val baseline: PersonalBaseline,
    private val onUiUpdate: () -> Unit,
    /**
     * Explicit min..max bands (e.g. custom-drill editor "knees · strike" target, decoded by
     * [com.ttcoachai.util.PerPhaseTargetsCodec] in [com.ttcoachai.TrainingActivity]) that
     * override the baseline consistency rule for the given metric key in [LiveDrillSession].
     * Empty (default) reproduces the exact pre-existing baseline-only behavior.
     */
    private val metricBands: Map<String, ClosedRange<Double>> = emptyMap(),
) {
    companion object {
        private const val TAG = "RtmposeTrainingCtrl"

        /**
         * Maps a [com.ttcoachai.shared.drill.FeedbackCue.metricKey] (or null, for positive
         * reinforcement) onto [CorrectionType] buckets for the live-feedback list/chips UI.
         * Each RTM metric now has a dedicated 1:1 mapping after Task 8 introduced dedicated
         * CorrectionType buckets (ELBOW_BEND, POSTURE, FOLLOW_THROUGH, STROKE_SPEED) to match
         * the derived metrics and distinct coaching feedback per-metric. Unmapped keys
         * (including the dropped shoulder_tilt, no longer coached) fall to GENERAL.
         */
        internal fun mapMetricToCorrectionType(metricKey: String?): CorrectionType = when (metricKey) {
            null -> CorrectionType.GENERAL
            "elbow_angle" -> CorrectionType.ELBOW_BEND               // forearm flexion (shoulder-elbow-wrist)
            "shoulder_angle" -> CorrectionType.ELBOW_POSITION         // upper-arm vs torso (hip-shoulder-elbow)
            "torso_lean" -> CorrectionType.POSTURE                    // spine vs vertical
            "knee_bend" -> CorrectionType.KNEE_BEND
            "follow_through_angle_2d" -> CorrectionType.FOLLOW_THROUGH
            "stroke_speed" -> CorrectionType.STROKE_SPEED
            "coil_ratio" -> CorrectionType.BODY_ROTATION             // trunk-coil proxy (qualitative)
            else -> CorrectionType.GENERAL                            // incl. dropped shoulder_tilt
        }

        /**
         * Synthesizes a minimal [AnalysisResult] for one completed rep so
         * [TrainingStateManager]-backed stats (stroke count, good-stroke count, average
         * score, session save) keep working without a full legacy-style per-rep analysis.
         * Score convention (see [SessionStatsCalculator] / existing >=80 "good" /
         * >=70 "successful" thresholds): 95f for a clean rep (no cues, placement OK),
         * 65f when there were cues to correct (still a counted stroke, but below the
         * "good"/80 and "successful"/70 thresholds), 0f when placement itself failed
         * (rep still counts toward total strokes so the count reflects real swings, but
         * never toward good/successful).
         */
        internal fun synthesizeAnalysisResult(rep: RepEvent): AnalysisResult {
            val score = when {
                !rep.placementOk -> 0f
                rep.cueCount == 0 -> 95f
                else -> 65f
            }
            return AnalysisResult(timestamp = rep.atMs, overallScore = score)
        }
    }

    private var previewView: PreviewView? = null
    private var overlayView: Coco17OverlayView? = null

    private var backend: PoseBackend? = null
    private var processor: RtmposeFrameProcessor? = null
    private var voiceController: PresetVoiceController? = null
    private var session: LiveDrillSession? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analysisExecutor: ExecutorService? = null

    /** Analysis-frame aspect ratio (rotated width/height), captured from the first
     *  analyzed frame. [session] is created lazily on the first frame using this value —
     *  see class doc on why (LiveDrillSession needs one fixed aspectRatio for its lifetime,
     *  and CameraX only tells us the real rotated size once frames start flowing). */
    @Volatile private var aspectRatio: Float = 3f / 4f

    private var sessionCreated = false

    /**
     * Builds the preview + overlay, constructs the RTMPose backend, and binds CameraX.
     * Returns true on success. Returns false (after logging) if the backend fails to
     * construct — the caller should fall back to the legacy pipeline; [start] leaves
     * nothing attached to [container] in that case.
     */
    fun start(): Boolean {
        val activeBackend = try {
            RtmposeBackend(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to construct RtmposeBackend", e)
            return false
        }
        backend = activeBackend

        val preview = PreviewView(activity)
        val overlay = Coco17OverlayView(activity)
        container.addView(preview, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        previewView = preview
        overlayView = overlay

        processor = RtmposeFrameProcessor(activeBackend, mirror = false) { keypoints, timestampMs ->
            activity.runOnUiThread { onPoseResult(keypoints, timestampMs) }
        }

        val lang = coachLang()
        val handedness = handedness()

        val voice = PresetVoiceController(activity, settingsManager.getVoiceStyleId(), lang) { text ->
            // On-screen text channel — feedback is already bridged into stateManager's
            // feedback history below; PresetVoiceController's onScreen callback has no
            // dedicated surface in this screen, so this is intentionally a no-op beyond
            // what addFeedback/addFeedbackItems already provide.
        }
        voice.init()
        voice.setMuted(!settingsManager.isAudioFeedbackEnabled())
        voiceController = voice

        analysisExecutor = Executors.newSingleThreadExecutor()
        startCamera()

        return true
    }

    private fun coachLang(): FeedbackLang {
        val coachCode = settingsManager.getCoachLanguage()
        val code = if (coachCode.isNotBlank()) coachCode else LocaleHelper.getSavedLanguage(activity)
        return if (code == "uk") FeedbackLang.UA else FeedbackLang.EN
    }

    // TODO: SettingsManager has no shared-Handedness getter — isPlayingHandRight() exists
    // but is a different value space (bool "hand" vs shared Handedness) used elsewhere for
    // feedback-zone tuning, not confirmed to mean the same thing here. Hardcoding RIGHT,
    // same caveat as RtmposeDrillActivity, until a pre-drill handedness picker exists.
    private fun handedness(): Handedness = Handedness.RIGHT

    // MARK: - CameraX (copied technique from RtmposeDrillActivity.bindCameraUseCases)

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(activity)
        )
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val preview = previewView ?: return
        val executor = analysisExecutor ?: return
        val rotation = preview.display?.rotation ?: android.view.Surface.ROTATION_0

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val previewUseCase = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(preview.surfaceProvider) }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(executor) { imageProxy ->
                    val rot = imageProxy.imageInfo.rotationDegrees
                    val rotatedWidth = if (rot % 180 != 0) imageProxy.height else imageProxy.width
                    val rotatedHeight = if (rot % 180 != 0) imageProxy.width else imageProxy.height
                    if (rotatedHeight > 0) {
                        aspectRatio = rotatedWidth.toFloat() / rotatedHeight.toFloat()
                    }
                    processor?.analyze(imageProxy) ?: imageProxy.close()
                }
            }

        // This controller uses its own ProcessCameraProvider instance and the legacy
        // CameraFragment is never attached while RTMPose mode is active (TrainingMediaManager
        // skips camera setup in this mode), so unbindAll() here cannot steal the legacy
        // pipeline's use cases.
        provider.unbindAll()
        try {
            camera = provider.bindToLifecycle(activity, cameraSelector, previewUseCase, imageAnalyzer)
        } catch (e: Exception) {
            Log.e(TAG, "CameraX bind failed", e)
        }
    }

    // MARK: - Frame dispatch (UI thread — runOnUiThread hop happens in the processor callback above)

    private fun onPoseResult(keypoints: List<com.ttcoachai.shared.models.Keypoint2D>, timestampMs: Long) {
        overlayView?.setKeypoints(keypoints)

        if (!stateManager.isTrainingActive) return

        val activeSession = ensureSession()
        val feedback: List<SpokenFeedback> = activeSession.onFrame(keypoints, timestampMs)
        for (item in feedback) {
            val isPositive = item.cue == null
            val type = mapMetricToCorrectionType(item.cue?.metricKey)
            // Positive reinforcement and GENERAL cues always pass; everything else is gated
            // live on the per-type Settings toggle so a disabled correction type produces no
            // voice cue, no on-screen text, and no feedback-list/count entry.
            val allowed = isPositive || type == CorrectionType.GENERAL || settingsManager.isCorrectionTypeEnabled(type)
            if (!allowed) continue

            voiceController?.speak(item)
            stateManager.addFeedback(item.message)
            stateManager.addFeedbackItems(
                listOf(
                    FeedbackItem(
                        message = item.message,
                        type = type,
                        isPositive = isPositive
                    )
                )
            )
        }
    }

    /** Creates [session] on first use, with the aspectRatio captured from analyzed frames
     *  so far (defaults to 3/4 like [RtmposeDrillActivity] until the first frame arrives). */
    private fun ensureSession(): LiveDrillSession {
        var current = session
        if (current == null || !sessionCreated) {
            current = LiveDrillSession(
                baseline = baseline,
                aspectRatio = aspectRatio,
                handedness = handedness(),
                lang = coachLang(),
                cameraYawDeg = 0f,
                metricBands = metricBands
            )
            // onRep fires synchronously inside onFrame, which we only ever call from the UI
            // thread (see the runOnUiThread hop in the processor callback in start()) — no
            // extra thread marshalling needed here.
            current.onRep = { rep ->
                stateManager.addAnalysisResult(synthesizeAnalysisResult(rep))
                onUiUpdate()
            }
            session = current
            sessionCreated = true
        }
        return current
    }

    // MARK: - Teardown

    fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
    }

    /** Releases all resources. Safe to call after [stop] or without a prior [stop]. */
    fun release() {
        stop()
        processor?.close()
        processor = null
        (backend as? AutoCloseable)?.close()
        backend = null
        voiceController?.shutdown()
        voiceController = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
        session = null
        sessionCreated = false
        previewView?.let { container.removeView(it) }
        overlayView?.let { container.removeView(it) }
        previewView = null
        overlayView = null
    }
}
