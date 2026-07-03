package com.ttcoachai.shared.drill

/**
 * Hand-coded anatomical sanity bounds (design decision 2: hand-coded values survive
 * only as sanity bounds). A value outside its band is a tracking glitch — the metric
 * is dropped for that frame, never coached on. Personal baselines, not these bounds,
 * define "correct".
 */
object SanityBounds {

    private val bounds: Map<String, ClosedFloatingPointRange<Double>> = mapOf(
        DrillMetrics.METRIC_ELBOW_ANGLE to 20.0..170.0,    // spec example
        DrillMetrics.METRIC_SHOULDER_ANGLE to 5.0..175.0,
        DrillMetrics.METRIC_KNEE_BEND to 60.0..180.0,
        DrillMetrics.METRIC_TORSO_LEAN to -60.0..60.0,
        DrillMetrics.METRIC_SHOULDER_TILT to -60.0..60.0
    )

    /** Metrics without a registered band pass through (bounds are opt-in). */
    fun isSane(metricKey: String, value: Double): Boolean =
        bounds[metricKey]?.contains(value) ?: true
}
