package com.ttcoachai.shared.drill

/**
 * Hand-coded anatomical sanity bounds (design decision 2: hand-coded values survive
 * only as sanity bounds). A value outside its band is a tracking glitch — the metric
 * is dropped for that frame, never coached on. Personal baselines, not these bounds,
 * define "correct".
 *
 * Bounds are sourced from [CoreMetricSpecs] (single source of truth); this object's
 * public `isSane` contract is unchanged.
 */
object SanityBounds {

    private val bounds: Map<String, ClosedFloatingPointRange<Double>> =
        CoreMetricSpecs.ALL.mapNotNull { spec -> spec.sanityBounds?.let { spec.key to it } }.toMap()

    /** Metrics without a registered band pass through (bounds are opt-in). */
    fun isSane(metricKey: String, value: Double): Boolean =
        bounds[metricKey]?.contains(value) ?: true
}
