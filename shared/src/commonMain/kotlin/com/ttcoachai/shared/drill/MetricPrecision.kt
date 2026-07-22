package com.ttcoachai.shared.drill

/**
 * Trust rule (context doc §3): precise degrees ONLY for in-plane metrics;
 * everything else is qualitative-only or silent.
 */
enum class MetricPrecision { PRECISE_DEGREES, QUALITATIVE }

object MetricPrecisionPolicy {

    /**
     * Trust rule (context doc §3): precise degrees ONLY for these in-plane angle metrics.
     * Explicitly enumerated — NOT derived from [DrillMetrics.ALL_KEYS], because the derived
     * keys stroke_speed and coil_ratio are qualitative proxies, not degree measurements.
     */
    private val preciseKeys = setOf(
        DrillMetrics.METRIC_ELBOW_ANGLE,
        DrillMetrics.METRIC_SHOULDER_ANGLE,
        DrillMetrics.METRIC_KNEE_BEND,
        DrillMetrics.METRIC_TORSO_LEAN,
        DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D,
    )

    /** Unknown metrics default to QUALITATIVE — never overclaim precision. */
    fun precisionFor(metricKey: String): MetricPrecision =
        if (metricKey in preciseKeys) MetricPrecision.PRECISE_DEGREES else MetricPrecision.QUALITATIVE
}
