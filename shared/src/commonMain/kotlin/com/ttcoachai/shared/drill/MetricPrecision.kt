package com.ttcoachai.shared.drill

/**
 * Trust rule (context doc §3): precise degrees ONLY for in-plane metrics;
 * everything else is qualitative-only or silent.
 */
enum class MetricPrecision { PRECISE_DEGREES, QUALITATIVE }

object MetricPrecisionPolicy {

    private val preciseKeys = DrillMetrics.ALL_KEYS.toSet()

    /** Unknown metrics default to QUALITATIVE — never overclaim precision. */
    fun precisionFor(metricKey: String): MetricPrecision =
        if (metricKey in preciseKeys) MetricPrecision.PRECISE_DEGREES else MetricPrecision.QUALITATIVE
}
