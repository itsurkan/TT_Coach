package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.AngleCalculations2D
import com.ttcoachai.shared.analysis.ShoulderCoil
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D

/**
 * Computes the three DERIVED metric keys ([DrillMetrics.DERIVED_KEYS]) for one stroke — the metrics
 * that are NOT peak-extracted angles: follow-through angle (sampled at the stroke's endFrame, not the
 * peak), stroke speed (the detector's peak wrist speed), and the shoulder-coil ratio (a low-confidence
 * rotation proxy). ONE function feeds all three call sites (live rep processor, calibrator, batch
 * analyzer) so the derived metrics are identical across paths by construction.
 *
 * Keys are OMITTED (not zero/NaN) when a metric can't be computed for this stroke (gated keypoints,
 * out-of-band follow-through, degenerate coil width) — same contract as peak metrics.
 */
object DerivedMetrics {

    fun merge(
        frames: List<PoseFrame2D>,
        stroke: Stroke2D,
        handedness: Handedness,
        xScale: Float,
        intervalMs: Long,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
        radiusMs: Long = DrillMetrics.DEFAULT_PEAK_RADIUS_MS
    ): Map<String, Double> {
        val out = mutableMapOf<String, Double>()

        // follow-through angle at endFrame (reuses the ±radiusMs median + sanity-bound machinery)
        MovementMetrics.extractAtPeak(
            frames, stroke.endFrame, handedness, xScale, intervalMs,
            listOf(CoreMetricSpecs.FOLLOW_THROUGH), minScore, radiusMs
        )[DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D]?.let {
            out[DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D] = it
        }

        // stroke speed — always present
        out[DrillMetrics.METRIC_STROKE_SPEED] = stroke.peakSpeed.toDouble()

        // shoulder-coil ratio (drive.start → drive.end); omitted when unmeasurable
        ShoulderCoil.coilRatio(frames, stroke.startFrame, stroke.endFrame, xScale, intervalMs, minScore, radiusMs)
            ?.let { out[DrillMetrics.METRIC_COIL_RATIO] = it.toDouble() }

        return out
    }
}
