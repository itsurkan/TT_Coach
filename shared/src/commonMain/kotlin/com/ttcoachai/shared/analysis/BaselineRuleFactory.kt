package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.PersonalBaseline

/**
 * Default `PersonalBaseline` → `List<BaselineRule>` mapping used as a seed
 * for the dev-only parameter editor (Phase 7) and as the starting rule set
 * a real rule evaluator would consume once Stage 1 Phase 2 lands.
 *
 * Layout:
 *  - Every technique metric that has non-zero std gets one [BaselineRule.ConsistencyRule]
 *    at 2σ (matches the outlier threshold used during derivation).
 *  - Every phase duration gets one [BaselineRule.RhythmRule] at 25% tolerance.
 *  - Regression rules are not auto-derived — they only make sense for metrics
 *    where "lower is worse" (e.g., contact height for a rising stroke), which
 *    is drill-specific and belongs in a drill preset rather than a global default.
 */
object BaselineRuleFactory {

    const val DEFAULT_CONSISTENCY_K_SIGMA: Double = 2.0
    const val DEFAULT_RHYTHM_DEVIATION_PCT: Double = 0.25

    fun defaultRules(baseline: PersonalBaseline): List<BaselineRule> {
        val rules = mutableListOf<BaselineRule>()
        for ((metricKey, stats) in baseline.metricStats) {
            if (stats.std <= 0.0) continue
            rules += BaselineRule.ConsistencyRule(
                id = "consistency:$metricKey",
                metricKey = metricKey,
                kSigma = DEFAULT_CONSISTENCY_K_SIGMA
            )
        }
        for ((phaseKey, stats) in baseline.phaseDurationsMs) {
            if (stats.std <= 0.0) continue
            rules += BaselineRule.RhythmRule(
                id = "rhythm:$phaseKey",
                metricKey = phaseKey,
                maxDurationDeviationPct = DEFAULT_RHYTHM_DEVIATION_PCT
            )
        }
        return rules
    }

    /**
     * Overlays explicit min..max bands on top of an existing rule list. For each metric key
     * present in [bands]: drops that metric's [BaselineRule.ConsistencyRule] (if any) and
     * appends a [BaselineRule.RangeRule] for the band instead. Rules for every other metric
     * pass through unchanged — this is how a coach-authored per-phase target (custom-drill
     * editor "knees · strike" range) overrides the derived-baseline consistency check for
     * ONLY that metric, without needing a parallel rule-evaluation mechanism.
     */
    fun applyRangeOverrides(
        rules: List<BaselineRule>,
        bands: Map<String, ClosedRange<Double>>
    ): List<BaselineRule> {
        if (bands.isEmpty()) return rules
        val withoutOverriddenConsistency = rules.filterNot {
            it is BaselineRule.ConsistencyRule && it.metricKey in bands
        }
        val rangeRules = bands.map { (metricKey, band) ->
            BaselineRule.RangeRule(id = "range:$metricKey", metricKey = metricKey, min = band.start, max = band.endInclusive)
        }
        return withoutOverriddenConsistency + rangeRules
    }
}
