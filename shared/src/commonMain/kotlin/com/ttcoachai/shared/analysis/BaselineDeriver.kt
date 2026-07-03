package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.DetectedStroke
import com.ttcoachai.shared.models.MetricStats
import com.ttcoachai.shared.models.PersonalBaseline
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Derives a PersonalBaseline from the parallel output of the stroke-detection
 * pipeline: one DetectedStroke and one AnalysisResult per rep.
 *
 * Algorithm (v1, one-pass):
 *   1. Extract per-rep technique metric values from AnalysisResult.
 *   2. Extract per-rep phase durations from DetectedStroke boundary frames.
 *   3. Compute initial mean/std per metric.
 *   4. Mark a rep as an outlier if any of its metric values (technique OR phase)
 *      lies more than `outlierSigmaThreshold` σ from that metric's initial mean.
 *   5. Recompute stats from non-outlier reps only.
 *   6. qualityScore = 1 - mean(coefficient of variation) across technique metrics,
 *      clamped to [0, 1]. Metrics with `|mean| < 1e-9` are skipped (undefined CV).
 *
 * The minimum rep threshold is checked AFTER outlier exclusion.
 */
object BaselineDeriver {

    private const val DEFAULT_OUTLIER_SIGMA = 2.0
    private const val DEFAULT_MIN_REPS = 10
    private const val CV_EPSILON = 1e-9

    /** Technique metric keys extracted from AnalysisResult (non-null fields only). */
    const val METRIC_WRIST_ANGLE = "wrist_angle"
    const val METRIC_BODY_ROTATION = "body_rotation"
    const val METRIC_FOLLOW_THROUGH_ANGLE = "follow_through_angle"
    const val METRIC_CONTACT_HEIGHT = "contact_height"
    const val METRIC_ELBOW_BODY_DISTANCE = "elbow_body_distance"

    /** Phase duration keys derived from DetectedStroke boundaries. */
    const val PHASE_BACKSWING_MS = "backswing_ms"
    const val PHASE_FORWARD_SWING_MS = "forward_swing_ms"
    const val PHASE_FOLLOW_THROUGH_MS = "follow_through_ms"
    const val PHASE_STROKE_TOTAL_MS = "stroke_total_ms"

    /**
     * Derive a PersonalBaseline from aligned per-rep pipeline output.
     *
     * @param strokes detected strokes, one per rep
     * @param analyses analysis result, one per rep (same indexing as `strokes`)
     * @param frameIntervalMs frame-to-frame interval used to convert boundary frames → ms
     * @param drillType drill identifier, e.g. "forehand_shadow"
     * @param createdAtMs epoch millis for baseline creation (caller supplies for testability)
     * @param drillerHandedness optional "right"/"left"; not auto-detected in Phase 0
     * @param minRepCount minimum number of non-outlier reps required (throws if fewer)
     * @param outlierSigmaThreshold σ-multiplier above which a rep is flagged as an outlier
     */
    fun derive(
        strokes: List<DetectedStroke>,
        analyses: List<AnalysisResult>,
        frameIntervalMs: Long,
        drillType: String,
        createdAtMs: Long,
        drillerHandedness: String? = null,
        minRepCount: Int = DEFAULT_MIN_REPS,
        outlierSigmaThreshold: Double = DEFAULT_OUTLIER_SIGMA
    ): PersonalBaseline {
        require(strokes.isNotEmpty()) { "Cannot derive baseline from empty stroke list" }
        require(strokes.size == analyses.size) {
            "strokes (${strokes.size}) and analyses (${analyses.size}) must be parallel lists"
        }
        require(frameIntervalMs > 0) { "frameIntervalMs must be > 0, got $frameIntervalMs" }

        return deriveFromMetrics(
            repMetrics = analyses.map { extractMetricValues(it) },
            repPhaseDurations = strokes.map { extractPhaseDurations(it, frameIntervalMs) },
            drillType = drillType,
            createdAtMs = createdAtMs,
            drillerHandedness = drillerHandedness,
            minRepCount = minRepCount,
            outlierSigmaThreshold = outlierSigmaThreshold
        )
    }

    /**
     * Source-agnostic core of baseline derivation: one metric map + one phase-duration
     * map per rep. The 2D drill pipeline (2D pivot Phase 2) feeds this directly;
     * the legacy 003 path feeds it via [derive].
     */
    fun deriveFromMetrics(
        repMetrics: List<Map<String, Double>>,
        repPhaseDurations: List<Map<String, Double>>,
        drillType: String,
        createdAtMs: Long,
        drillerHandedness: String? = null,
        minRepCount: Int = DEFAULT_MIN_REPS,
        outlierSigmaThreshold: Double = DEFAULT_OUTLIER_SIGMA
    ): PersonalBaseline {
        require(repMetrics.isNotEmpty()) { "Cannot derive baseline from zero reps" }
        require(repMetrics.size == repPhaseDurations.size) {
            "repMetrics (${repMetrics.size}) and repPhaseDurations (${repPhaseDurations.size}) " +
                "must be parallel lists"
        }

        val initialMetricStats = computeStatsPerKey(repMetrics)
        val initialPhaseStats = computeStatsPerKey(repPhaseDurations)

        val outlierIndices = findOutlierRepIndices(
            repMetrics, repPhaseDurations,
            initialMetricStats, initialPhaseStats,
            outlierSigmaThreshold
        )

        val keptIndices = repMetrics.indices.filter { it !in outlierIndices }
        if (keptIndices.size < minRepCount) {
            throw IllegalArgumentException(
                "Insufficient valid reps after outlier exclusion: " +
                    "${keptIndices.size} < $minRepCount (input=${repMetrics.size}, excluded=${outlierIndices.size})"
            )
        }

        val finalMetricStats = computeStatsPerKey(keptIndices.map { repMetrics[it] })
        val finalPhaseStats = computeStatsPerKey(keptIndices.map { repPhaseDurations[it] })

        return PersonalBaseline(
            drillType = drillType,
            metricStats = finalMetricStats,
            phaseDurationsMs = finalPhaseStats,
            repCount = keptIndices.size,
            excludedRepIndices = outlierIndices.toList().sorted(),
            qualityScore = computeQualityScore(finalMetricStats),
            createdAtMs = createdAtMs,
            drillerHandedness = drillerHandedness
        )
    }

    private fun extractMetricValues(result: AnalysisResult): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        result.wristAngle?.let { out[METRIC_WRIST_ANGLE] = it.toDouble() }
        result.bodyRotation?.let { out[METRIC_BODY_ROTATION] = it.toDouble() }
        result.followThroughAngle?.let { out[METRIC_FOLLOW_THROUGH_ANGLE] = it.toDouble() }
        result.contactHeight?.let { out[METRIC_CONTACT_HEIGHT] = it.toDouble() }
        result.elbowBodyDistance?.let { out[METRIC_ELBOW_BODY_DISTANCE] = it.toDouble() }
        return out
    }

    private fun extractPhaseDurations(stroke: DetectedStroke, intervalMs: Long): Map<String, Double> {
        val interval = intervalMs.toDouble()
        val backswingFrames = (stroke.preparationEndFrame - stroke.preparationStartFrame).coerceAtLeast(0)
        val forwardFrames = (stroke.forwardEndFrame - stroke.forwardStartFrame).coerceAtLeast(0)
        val followThroughFrames = (stroke.returnEndFrame - stroke.returnStartFrame).coerceAtLeast(0)
        return mapOf(
            PHASE_BACKSWING_MS to backswingFrames * interval,
            PHASE_FORWARD_SWING_MS to forwardFrames * interval,
            PHASE_FOLLOW_THROUGH_MS to followThroughFrames * interval,
            PHASE_STROKE_TOTAL_MS to stroke.strokeDurationMs.toDouble().coerceAtLeast(0.0)
        )
    }

    private fun computeStatsPerKey(reps: List<Map<String, Double>>): Map<String, MetricStats> {
        val byKey = mutableMapOf<String, MutableList<Double>>()
        for (rep in reps) {
            for ((key, value) in rep) {
                byKey.getOrPut(key) { mutableListOf() }.add(value)
            }
        }
        return byKey.mapValues { (_, values) -> statsOf(values) }
    }

    private fun statsOf(values: List<Double>): MetricStats {
        if (values.isEmpty()) return MetricStats(0.0, 0.0, 0.0, 0.0, 0)
        val n = values.size
        val mean = values.sum() / n
        val std = if (n <= 1) {
            0.0
        } else {
            val sumSqDev = values.sumOf { (it - mean) * (it - mean) }
            sqrt(sumSqDev / (n - 1))
        }
        return MetricStats(
            mean = mean,
            std = std,
            min = values.min(),
            max = values.max(),
            sampleCount = n
        )
    }

    private fun findOutlierRepIndices(
        repMetrics: List<Map<String, Double>>,
        repPhases: List<Map<String, Double>>,
        metricStats: Map<String, MetricStats>,
        phaseStats: Map<String, MetricStats>,
        sigmaThreshold: Double
    ): Set<Int> {
        val outliers = mutableSetOf<Int>()
        for (i in repMetrics.indices) {
            if (isOutlier(repMetrics[i], metricStats, sigmaThreshold) ||
                isOutlier(repPhases[i], phaseStats, sigmaThreshold)
            ) {
                outliers.add(i)
            }
        }
        return outliers
    }

    private fun isOutlier(
        repValues: Map<String, Double>,
        stats: Map<String, MetricStats>,
        sigmaThreshold: Double
    ): Boolean {
        for ((key, value) in repValues) {
            val s = stats[key] ?: continue
            if (s.std > 0.0 && abs(value - s.mean) > sigmaThreshold * s.std) return true
        }
        return false
    }

    private fun computeQualityScore(metricStats: Map<String, MetricStats>): Double {
        val cvs = metricStats.values.mapNotNull { s ->
            if (abs(s.mean) < CV_EPSILON) null else s.std / abs(s.mean)
        }
        if (cvs.isEmpty()) return 1.0
        val meanCv = cvs.sum() / cvs.size
        return (1.0 - meanCv).coerceIn(0.0, 1.0)
    }
}
