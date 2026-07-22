package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.AngleCalculations2D
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseFrame2D

/**
 * Per-frame extraction of the five Phase 2 in-plane metrics (context doc §3) at the
 * stroke's wrist-speed peak. Score-gated per joint; sanity-bounded per value.
 *
 * Thin wrapper: the generalized algorithm — any [MetricSpec] list, not just these
 * five — lives in [MovementMetrics]. Kept for API compatibility; behavior for the
 * five core metrics is unchanged bit-for-bit.
 */
object DrillMetrics {

    const val METRIC_ELBOW_ANGLE = "elbow_angle"
    const val METRIC_SHOULDER_ANGLE = "shoulder_angle"
    /**
     * hip-knee-ankle angle at the stroke's wrist-speed peak (strike), NOT the backswing.
     * [extractAtPeak] only ever computes one value per rep from the frames around
     * [peakFrame] — there is no separate backswing-phase extraction — so an editor-authored
     * "knees · backswing" target has no metric to bind to; only "knees · strike" is wireable
     * via a [com.ttcoachai.shared.analysis.BaselineRule.RangeRule] against this key.
     */
    const val METRIC_KNEE_BEND = "knee_bend"
    const val METRIC_TORSO_LEAN = "torso_lean"
    /**
     * Still defined and used by [com.ttcoachai.shared.analysis.AngleCalculations2D.shoulderTilt],
     * [VoicePresetCatalog], and [CoreMessageTemplates] — just no longer part of [PEAK_KEYS]/[ALL_KEYS]
     * (removed from [CoreMetricSpecs.ALL], the peak-extracted spec list).
     */
    const val METRIC_SHOULDER_TILT = "shoulder_tilt"
    /** Legacy 3D owns `"follow_through_angle"` ([MetricKeyDisjointTest]) — this 2D key takes the `_2d` suffix. */
    const val METRIC_FOLLOW_THROUGH_ANGLE_2D = "follow_through_angle_2d"
    const val METRIC_STROKE_SPEED = "stroke_speed"
    const val METRIC_COIL_RATIO = "coil_ratio"

    /** The four metrics extracted at the stroke's wrist-speed peak (see [CoreMetricSpecs.ALL]). */
    val PEAK_KEYS = listOf(
        METRIC_ELBOW_ANGLE, METRIC_SHOULDER_ANGLE, METRIC_KNEE_BEND, METRIC_TORSO_LEAN
    )

    /** The three derived metrics computed from the peak metrics + stroke window (later tasks own the computation). */
    val DERIVED_KEYS = listOf(
        METRIC_FOLLOW_THROUGH_ANGLE_2D, METRIC_STROKE_SPEED, METRIC_COIL_RATIO
    )

    val ALL_KEYS = PEAK_KEYS + DERIVED_KEYS

    /** Half-width of the peak-metric smoothing window, in ms (±2 frames at 30 fps). */
    const val DEFAULT_PEAK_RADIUS_MS = 70L

    fun extractAtFrame(
        frame: PoseFrame2D,
        handedness: Handedness,
        xScale: Float,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE
    ): Map<String, Double> =
        MovementMetrics.extractAtFrame(frame, handedness, xScale, CoreMetricSpecs.ALL, minScore)

    /**
     * Per-rep metrics: MEDIAN of each metric over the frames within ±[radiusMs] of
     * [peakFrame] (DESIGN_LIMITATIONS L-05 — keypoints are unsmoothed, so a single
     * raw frame feeds RTMPose jitter straight into the baseline). Median, not mean:
     * one junk frame inside the window must not shift the rep's value. Each frame
     * is score-gated and sanity-bounded independently via [extractAtFrame]; at
     * coarse intervals (radius < interval) this degrades to the single peak frame.
     */
    fun extractAtPeak(
        frames: List<PoseFrame2D>,
        peakFrame: Int,
        handedness: Handedness,
        xScale: Float,
        intervalMs: Long,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
        radiusMs: Long = DEFAULT_PEAK_RADIUS_MS
    ): Map<String, Double> =
        MovementMetrics.extractAtPeak(frames, peakFrame, handedness, xScale, intervalMs, CoreMetricSpecs.ALL, minScore, radiusMs)
}
