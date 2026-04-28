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
}
