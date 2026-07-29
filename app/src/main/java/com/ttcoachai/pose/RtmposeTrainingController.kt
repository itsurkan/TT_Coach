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
import com.ttcoachai.shared.analysis.BaselineRule
import com.ttcoachai.shared.analysis.BaselineRuleFactory
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.drill.LiveDrillSession
import com.ttcoachai.shared.drill.LocomotionFilter
import com.ttcoachai.shared.drill.RepEvent
import com.ttcoachai.shared.drill.SpokenFeedback
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.FeedbackItem
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PersonalBaseline
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the whole RTMPose live path for [com.ttcoachai.TrainingActivity]: builds the
 * preview + skeleton overlay, binds CameraX, runs the [PoseBackendFactory]-resolved backend
 * through [RtmposeFrameProcessor], drives a [LiveDrillSession] against [baseline], speaks
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
    /** Starting rule set BEFORE metricBands overlay (see LiveDrillSession.metricBands kdoc) —
     *  defaults to today's exact behavior (baseline-derived consistency rules). Task H passes
     *  emptyList() explicitly for "standard" reference mode so ONLY the drill's configured
     *  bands produce cues. */
    private val rules: List<BaselineRule> = BaselineRuleFactory.defaultRules(baseline),
    /** Locomotion gate tolerance in torso-lengths (LiveDrillSession/LocomotionFilter). Task H
     *  passes ForehandDriveGeneral.MOVEMENT_TOLERANT_HIP_TRAVEL for the General movement
     *  profile, the default otherwise. */
    private val hipTravelMaxTorso: Float = LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO,
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

    private var poseRecorder: PoseSessionRecorder? = null
    private var poseRecordingStarted = false

    /**
     * Single latch guarding [PoseSessionRecorder]'s finish/abort lifecycle contract (its KDoc:
     * finish/abort must never run concurrently). [finishRecording] (normal save path, on the
     * main thread inside the cloud-save callback) and [abortRecording] (discard path, short-save
     * path, and the [release] safety net in onDestroy) can otherwise race — e.g. onDestroy firing
     * right after a successful save kicks off finishRecording. Whichever call wins the atomic
     * compareAndSet actually touches [poseRecorder]; the loser is a no-op.
     */
    private val finalizationClaimed = AtomicBoolean(false)
    @Volatile private var frameWidth: Int = 0
    @Volatile private var frameHeight: Int = 0

    /**
     * Builds the preview + overlay, constructs the RTMPose backend, and binds CameraX.
     * Returns true on success. Returns false (after logging) if the backend fails to
     * construct — the caller should fall back to the legacy pipeline; [start] leaves
     * nothing attached to [container] in that case.
     */
    fun start(): Boolean {
        val activeBackend = try {
            PoseBackendFactory.create(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to construct pose backend", e)
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

        if (settingsManager.isPoseUploadEnabled()) {
            poseRecorder = PoseSessionRecorder(PoseSessionRecorder.cacheDir(activity))
        }

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
                        frameWidth = rotatedWidth
                        frameHeight = rotatedHeight
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

        poseRecorder?.let { recorder ->
            if (!poseRecordingStarted) {
                recorder.start(frameWidth, frameHeight)
                poseRecordingStarted = true
            }
            recorder.onFrame(keypoints, timestampMs)
        }

        val activeSession = ensureSession()
        val feedback: List<SpokenFeedback> = activeSession.onFrame(keypoints, timestampMs)
        for (item in feedback) {
            val isPositive = item.cue == null
            val type = mapMetricToCorrectionType(item.cue?.metricKey)
            // Positive reinforcement and GENERAL cues always pass; everything else is gated
            // live on the per-type Settings toggle so a disabled correction type produces no
            // voice cue, no on-screen text, and no feedback-list/count entry.
            val allowed = isPositive || type == CorrectionType.GENERAL || settingsManager.isCorrectionTypeEnabled(type)
            if (!allowed) {
                Log.d(TAG, "SUPPRESSED metricKey=${item.cue?.metricKey} type=$type reason=type-disabled")
                continue
            }
            Log.i(TAG, "SPOKEN metricKey=${item.cue?.metricKey ?: "positive"} type=$type")

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
            // Marks the type on the rep this feedback item belongs to. Safe ordering: onFrame
            // fires onRep (which records the rep's capture) synchronously before returning this
            // feedback list, and both run on the UI thread — so the capture for THIS stroke is
            // already in stateManager by the time we get here.
            if (!isPositive) {
                stateManager.flagLatestRepPose(type)
            }
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
                rules = rules,
                handedness = handedness(),
                lang = coachLang(),
                cameraYawDeg = 0f,
                hipTravelMaxTorso = hipTravelMaxTorso,
                metricBands = metricBands
            )
            logBaselineOnce()
            // onRep fires synchronously inside onFrame, which we only ever call from the UI
            // thread (see the runOnUiThread hop in the processor callback in start()) — no
            // extra thread marshalling needed here.
            current.onRep = { rep ->
                logRep(rep)
                stateManager.addAnalysisResult(synthesizeAnalysisResult(rep))
                stateManager.addRepPoses(rep.atMs, rep.startKeypoints, rep.endKeypoints)
                onUiUpdate()
            }
            session = current
            sessionCreated = true
        }
        return current
    }

    // MARK: - Diagnostics (see task: never hearing knee-bend cue — no behavior change,
    // logging only). Filter with `adb logcat -s RtmposeTrainingCtrl`.

    /** Logged once when [session] is created: reveals a baseline missing `knee_bend`
     *  (or any other metric) outright — the #1 candidate for "cue never fires". */
    private fun logBaselineOnce() {
        val stats = baseline.metricStats.entries.joinToString(", ") { (key, s) ->
            "$key(mean=${round1(s.mean)},std=${round1(s.std)})"
        }
        val bands = if (metricBands.isEmpty()) {
            "none"
        } else {
            metricBands.entries.joinToString(", ") { (key, range) ->
                "$key=[${round1(range.start)}..${round1(range.endInclusive)}]"
            }
        }
        Log.i(
            TAG,
            "BASELINE handedness=${handedness()} repCount=${baseline.repCount} " +
                "qualityScore=${round1(baseline.qualityScore)} metricStats={$stats} metricBands={$bands}"
        )
    }

    /** Logged once per completed rep: metrics + cues actually derived for it, regardless
     *  of cadence suppression downstream — lets us tell "no cue generated" (baseline/rule
     *  gap) apart from "cue generated but not spoken" (cadence policy). */
    private fun logRep(rep: RepEvent) {
        val metrics = rep.metrics.entries.joinToString(", ") { (key, value) -> "$key=${round1(value)}" }
        val cues = rep.cues.sortedByDescending { it.severity }
            .joinToString(", ") { "${it.metricKey}:${it.direction}:${round1(it.severity)}" }
        Log.d(
            TAG,
            "REP atMs=${rep.atMs} placementOk=${rep.placementOk} cueCount=${rep.cueCount} " +
                "metrics={$metrics} cues=[$cues]"
        )
    }

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0
    private fun round1(value: Float): Double = round1(value.toDouble())

    // MARK: - Teardown

    fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
    }

    /** Finalizes the pose recording (if pose upload is enabled and recording started) and
     *  returns the resulting gzip file, or null if nothing was recorded — or if [abortRecording]
     *  (or a prior call to this method) already claimed finalization, see [finalizationClaimed]. */
    suspend fun finishRecording(): File? {
        if (!finalizationClaimed.compareAndSet(false, true)) return null
        return poseRecorder?.finish()
    }

    /** Aborts and deletes any in-progress pose recording (session discarded). No-op if pose
     *  upload is disabled, recording never started, or [finishRecording] (or a prior call to
     *  this method) already claimed finalization, see [finalizationClaimed]. */
    fun abortRecording() {
        if (!finalizationClaimed.compareAndSet(false, true)) return
        poseRecorder?.abort()
    }

    /** Closes [processor] and [backend]. Must only run as a task on [analysisExecutor] (or,
     *  when that executor was never created, on the calling thread directly) — see [release]. */
    private fun closeBackendNow() {
        processor?.close()
        (backend as? AutoCloseable)?.close()
        processor = null
        backend = null
    }

    /** Releases all resources. Safe to call after [stop] or without a prior [stop].
     *
     *  [stop] first unbinds the camera so no new frames are dispatched, then [closeBackendNow]
     *  runs as a task ON [analysisExecutor] — the SAME single-thread executor that runs
     *  `analyze()` for every frame (see [bindCameraUseCases]) — so it can never run concurrently
     *  with an in-flight `analyze()` call. Without this, a frame could still be inside
     *  `backend.estimatePose()` (native MediaPipe `detect()`) while close() destroys it: a fatal
     *  SIGSEGV, not a catchable exception (same hazard/fix as
     *  `PoseBenchmarkActivity.switchBackend()`). `shutdown()` (not `shutdownNow()`) lets this
     *  queued task run to completion before the executor terminates, without blocking the
     *  calling thread on it. */
    fun release() {
        stop()
        val executor = analysisExecutor
        if (executor != null) {
            executor.execute { closeBackendNow() }
            executor.shutdown()
        } else {
            closeBackendNow()
        }
        analysisExecutor = null
        voiceController?.shutdown()
        voiceController = null
        session = null
        sessionCreated = false
        previewView?.let { container.removeView(it) }
        overlayView?.let { container.removeView(it) }
        previewView = null
        overlayView = null
    }
}
