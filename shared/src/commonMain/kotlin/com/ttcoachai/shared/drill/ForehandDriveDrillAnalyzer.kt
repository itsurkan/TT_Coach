package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineRule
import com.ttcoachai.shared.analysis.BaselineRuleFactory
import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Stroke2D
import com.ttcoachai.shared.models.ViewGeometry

data class RepAnalysis(
    val stroke: Stroke2D,
    val metrics: Map<String, Double>,
    val cues: List<FeedbackCue>,
    /**
     * Camera yaw used for THIS rep (pre-stroke estimate or the override), degrees.
     * null = yaw was unmeasurable (no scored shoulders+hips in the pre-stroke or
     * stroke window) → placement unverifiable → [placementOk] is false.
     */
    val cameraYawDeg: Float?,
    /**
     * false → camera was too far off the required side view at this rep: cues and
     * spoken feedback were skipped (trust rule); metrics are diagnostics only.
     */
    val placementOk: Boolean
)

data class SpokenFeedback(
    val timestampMs: Long,
    val message: String,
    /** null = positive reinforcement, not a correction. */
    val cue: FeedbackCue?
)

data class DrillAnalysisReport(
    val reps: List<RepAnalysis>,
    val feedback: List<SpokenFeedback>,
    /**
     * Session summary: false → more than half the reps had bad camera placement;
     * the UI should surface a "reposition camera" prompt. Per-rep detail is on
     * [RepAnalysis.placementOk].
     */
    val placementOk: Boolean
)

/**
 * Phase 2 exit-gate orchestrator: pose sequence → strokes → per-rep metrics →
 * baseline-rule cues → cadenced UA/EN feedback. Batch over fixtures now; the same
 * per-rep flow drives the live Android loop in Phase 3.
 *
 * Forehand drive requires the side camera. Yaw is resolved PER REP (the player
 * moves their feet between reps): within [maxCameraYawDeg] it is corrected via that
 * rep's ViewGeometry.xScale; beyond it the rep gets no feedback.
 */
class ForehandDriveDrillAnalyzer(
    private val baseline: PersonalBaseline,
    private val rules: List<BaselineRule> = BaselineRuleFactory.defaultRules(baseline),
    private val handedness: Handedness = Handedness.RIGHT,
    private val lang: FeedbackLang = FeedbackLang.EN,
    private val cadence: FeedbackCadencePolicy = FeedbackCadencePolicy(),
    private val detector: StrokeDetector2D = StrokeDetector2D(),
    /** Explicit camera-yaw override applied to all reps; null → per-rep auto-estimate. */
    private val cameraYawDeg: Float? = null,
    private val maxCameraYawDeg: Float = DrillCalibrator.DEFAULT_MAX_CAMERA_YAW_DEG,
    /** Locomotion gate (L-30): drop reps whose hip-mid travels more than this many
     *  torso-lengths (walking). ≤ 0 disables. On by default so a walking step gets no feedback. */
    private val hipTravelMaxTorso: Float = LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO
) {

    init {
        require(maxCameraYawDeg <= ViewGeometry.MAX_YAW_DEG) {
            "maxCameraYawDeg must be <= ViewGeometry.MAX_YAW_DEG (${ViewGeometry.MAX_YAW_DEG}°), " +
                "got $maxCameraYawDeg — the 1/cos xScale correction is undefined beyond it"
        }
    }

    fun analyze(sequence: PoseSequence2D): DrillAnalysisReport {
        // analyze() is self-contained: a reused analyzer must not inherit the previous run's window
        cadence.reset()
        // Detection on plain aspect; per-rep corrected xScale below. ForwardStrokeFilter
        // keeps recovery swings out of feedback; RepFilter drops junk peaks (L-03).
        val detected = detector.detect(sequence.frames, handedness, sequence.aspectRatio, sequence.intervalMs)
        // detect → ForwardStrokeFilter → RepFilter → locomotion gate (xScale = aspectRatio
        // since detection runs on plain aspect / yaw 0).
        val strokes = LocomotionFilter.filterStationary(
            RepFilter.filter(ForwardStrokeFilter.filter(detected, sequence.frames, handedness)),
            sequence.frames, sequence.aspectRatio, hipTravelMaxTorso
        )

        val reps = strokes.map { stroke ->
            DrillRepProcessor.computeRep(
                frames = sequence.frames,
                stroke = stroke,
                aspectRatio = sequence.aspectRatio,
                intervalMs = sequence.intervalMs,
                handedness = handedness,
                baseline = baseline,
                rules = rules,
                cameraYawOverride = cameraYawDeg,
                maxCameraYawDeg = maxCameraYawDeg
            )
        }

        val feedback = mutableListOf<SpokenFeedback>()
        for (rep in reps) {
            val atMs = rep.stroke.endFrame * sequence.intervalMs
            val spoken = DrillRepProcessor.emitRepFeedback(rep, atMs, cadence, lang)
            if (spoken != null) feedback += spoken
        }

        val okCount = reps.count { it.placementOk }
        return DrillAnalysisReport(
            reps = reps,
            feedback = feedback,
            placementOk = reps.isEmpty() || okCount * 2 >= reps.size
        )
    }
}
