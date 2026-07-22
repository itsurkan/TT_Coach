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

    /**
     * "stay within an explicit [min, max] band" — independent of the personal baseline's
     * mean/std. Used to enforce coach-authored/editor targets (e.g. custom-drill knee-bend
     * strike range) as an absolute degree band rather than a relative-to-this-player
     * consistency check. See [BaselineRuleFactory.applyRangeOverrides].
     */
    data class RangeRule(
        override val id: String,
        override val metricKey: String,
        val min: Double,
        val max: Double
    ) : BaselineRule()
}
