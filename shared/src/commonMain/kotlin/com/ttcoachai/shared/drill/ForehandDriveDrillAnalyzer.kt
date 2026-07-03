package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineRule
import com.ttcoachai.shared.analysis.BaselineRuleFactory
import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.drill.movements.ForehandDrive
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.PoseSequence2D

/**
 * Phase 2 exit-gate orchestrator: pose sequence → strokes → per-rep metrics →
 * baseline-rule cues → cadenced UA/EN feedback. Batch over fixtures now; the same
 * per-rep flow drives the live Android loop in Phase 3.
 *
 * Forehand drive requires the side camera. Yaw is resolved PER REP (the player
 * moves their feet between reps): within [maxCameraYawDeg] it is corrected via that
 * rep's ViewGeometry.xScale; beyond it the rep gets no feedback.
 *
 * Thin delegating wrapper: the generalized orchestrator — any [MovementDefinition],
 * not just forehand drive — lives in [MovementAnalyzer], driven by
 * [ForehandDrive.DEFINITION] (docs/superpowers/specs/
 * 2026-07-02-generic-movement-pipeline-design.md). Kept for API compatibility;
 * behavior for forehand drive is unchanged bit-for-bit.
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

    private val definition = ForehandDrive.DEFINITION.copy(
        repValidation = ForehandDrive.DEFINITION.repValidation.copy(hipTravelMaxTorso = hipTravelMaxTorso)
    )

    private val delegate = MovementAnalyzer(
        definition = definition,
        baseline = baseline,
        rules = rules,
        handedness = handedness,
        lang = lang,
        cadence = cadence,
        cameraYawDeg = cameraYawDeg,
        maxCameraYawDeg = maxCameraYawDeg,
        pipeline = MovementRepPipeline(definition, detector.asMovementDetector())
    )

    fun analyze(sequence: PoseSequence2D): DrillAnalysisReport = delegate.analyze(sequence)
}
