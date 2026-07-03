package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.AngleCalculations2D
import com.ttcoachai.shared.analysis.SignalMath
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseFrame2D

/**
 * Per-frame extraction of the five Phase 2 in-plane metrics (context doc §3) at the
 * stroke's wrist-speed peak. Score-gated per joint; sanity-bounded per value.
 */
object DrillMetrics {

    const val METRIC_ELBOW_ANGLE = "elbow_angle"
    const val METRIC_SHOULDER_ANGLE = "shoulder_angle"
    const val METRIC_KNEE_BEND = "knee_bend"
    const val METRIC_TORSO_LEAN = "torso_lean"
    const val METRIC_SHOULDER_TILT = "shoulder_tilt"

    val ALL_KEYS = listOf(
        METRIC_ELBOW_ANGLE, METRIC_SHOULDER_ANGLE, METRIC_KNEE_BEND,
        METRIC_TORSO_LEAN, METRIC_SHOULDER_TILT
    )

    fun extractAtFrame(
        frame: PoseFrame2D,
        handedness: Handedness,
        xScale: Float,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE
    ): Map<String, Double> {
        val kp = frame.keypoints
        if (kp.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, Double>()
        AngleCalculations2D.elbowAngle(kp, handedness, xScale, minScore)
            ?.let { out[METRIC_ELBOW_ANGLE] = it.toDouble() }
        AngleCalculations2D.shoulderAngle(kp, handedness, xScale, minScore)
            ?.let { out[METRIC_SHOULDER_ANGLE] = it.toDouble() }
        AngleCalculations2D.kneeBend(kp, handedness, xScale, minScore)
            ?.let { out[METRIC_KNEE_BEND] = it.toDouble() }
        AngleCalculations2D.torsoLean(kp, xScale, minScore)
            ?.let { out[METRIC_TORSO_LEAN] = it.toDouble() }
        AngleCalculations2D.shoulderTilt(kp, xScale, minScore)
            ?.let { out[METRIC_SHOULDER_TILT] = it.toDouble() }
        return out.filter { (k, v) -> SanityBounds.isSane(k, v) }
    }

    /** Half-width of the peak-metric smoothing window, in ms (±2 frames at 30 fps). */
    const val DEFAULT_PEAK_RADIUS_MS = 70L

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
    ): Map<String, Double> {
        require(intervalMs > 0) { "intervalMs must be > 0, got $intervalMs" }
        require(peakFrame in frames.indices) { "peakFrame $peakFrame out of bounds for ${frames.size} frames" }
        val radius = (radiusMs / intervalMs).toInt()
        val lo = (peakFrame - radius).coerceAtLeast(0)
        val hi = (peakFrame + radius).coerceAtMost(frames.lastIndex)
        val byKey = mutableMapOf<String, MutableList<Double>>()
        for (i in lo..hi) {
            for ((key, value) in extractAtFrame(frames[i], handedness, xScale, minScore)) {
                byKey.getOrPut(key) { mutableListOf() }.add(value)
            }
        }
        return byKey.mapValues { (_, values) -> SignalMath.median(values) }
    }
}
