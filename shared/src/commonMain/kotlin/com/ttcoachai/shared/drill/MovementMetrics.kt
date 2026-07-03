package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.AngleCalculations2D
import com.ttcoachai.shared.analysis.SignalMath
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseFrame2D

/**
 * Generic, spec-driven version of [DrillMetrics]: per-frame and per-rep metric
 * extraction over any [MetricSpec] list, not just the five Phase 2 forehand-drive
 * metrics (docs/superpowers/specs/2026-07-02-generic-movement-pipeline-design.md).
 * [DrillMetrics] now delegates here with [CoreMetricSpecs.ALL].
 */
object MovementMetrics {

    /** Per-spec extraction at one frame; score-gated per spec, sanity-bounded per value. */
    fun extractAtFrame(
        frame: PoseFrame2D,
        handedness: Handedness,
        xScale: Float,
        specs: List<MetricSpec>,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE
    ): Map<String, Double> {
        val kp = frame.keypoints
        if (kp.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, Double>()
        for (spec in specs) {
            val value = spec.extractor(kp, handedness, xScale, minScore) ?: continue
            val d = value.toDouble()
            if (spec.sanityBounds == null || spec.sanityBounds.contains(d)) {
                out[spec.key] = d
            }
        }
        return out
    }

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
        specs: List<MetricSpec>,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
        radiusMs: Long = DrillMetrics.DEFAULT_PEAK_RADIUS_MS
    ): Map<String, Double> {
        require(intervalMs > 0) { "intervalMs must be > 0, got $intervalMs" }
        require(peakFrame in frames.indices) { "peakFrame $peakFrame out of bounds for ${frames.size} frames" }
        val radius = (radiusMs / intervalMs).toInt()
        val lo = (peakFrame - radius).coerceAtLeast(0)
        val hi = (peakFrame + radius).coerceAtMost(frames.lastIndex)
        val byKey = mutableMapOf<String, MutableList<Double>>()
        for (i in lo..hi) {
            for ((key, value) in extractAtFrame(frames[i], handedness, xScale, specs, minScore)) {
                byKey.getOrPut(key) { mutableListOf() }.add(value)
            }
        }
        return byKey.mapValues { (_, values) -> SignalMath.median(values) }
    }
}
