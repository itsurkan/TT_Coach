package com.ttcoachai.shared.models

/**
 * A player's personal baseline derived from a calibration session.
 *
 * Built by BaselineDeriver from the parallel output of JsonStrokeDetector
 * (per-rep phase boundaries) and StrokeAnalyzer (per-rep technique metrics).
 *
 * This is the reference point every downstream rule checks consistency against —
 * the player calibrates to their own technique once, then later strokes are
 * compared to `mean ± k·σ` for each metric rather than to a universal ideal.
 */
data class PersonalBaseline(
    val drillType: String,
    val metricStats: Map<String, MetricStats>,
    val phaseDurationsMs: Map<String, MetricStats>,
    val repCount: Int,
    val excludedRepIndices: List<Int>,
    val qualityScore: Double,
    val createdAtMs: Long,
    val drillerHandedness: String? = null
)
