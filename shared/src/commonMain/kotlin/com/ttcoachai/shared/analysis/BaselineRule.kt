package com.ttcoachai.shared.analysis

/**
 * Data-only shape for rules that compare a stroke to a PersonalBaseline.
 *
 * Phase 0 defines the types only — evaluation logic arrives in Stage 1 Phase 2.
 */
sealed class BaselineRule {
    abstract val id: String
    abstract val metricKey: String

    /** "stay within mean ± kSigma · σ" on a metric. */
    data class ConsistencyRule(
        override val id: String,
        override val metricKey: String,
        val kSigma: Double
    ) : BaselineRule()

    /** "don't drop more than maxDropFromMean below the baseline mean" on a metric. */
    data class RegressionRule(
        override val id: String,
        override val metricKey: String,
        val maxDropFromMean: Double
    ) : BaselineRule()

    /** "this phase's duration must stay within ±maxDurationDeviationPct of the baseline mean" (metricKey = phase name). */
    data class RhythmRule(
        override val id: String,
        override val metricKey: String,
        val maxDurationDeviationPct: Double
    ) : BaselineRule()
}
