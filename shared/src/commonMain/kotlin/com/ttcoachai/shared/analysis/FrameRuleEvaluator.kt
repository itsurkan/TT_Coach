package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.PersonalBaseline
import kotlin.math.abs

/**
 * Minimal rule evaluator used by the dev-only parameter editor (Phase 7).
 *
 * Evaluates a single rule against a single frame's measured metric value.
 * When Stage 1 Phase 2 lands a production evaluator, this should be absorbed
 * or replaced — for now it's scoped to what the editor needs (one rule,
 * one metric, one frame at a time; no stroke-phase-level aggregation).
 *
 * Phase duration rules ([BaselineRule.RhythmRule]) aren't evaluable per frame
 * (phase durations exist only over spans of frames), so [evaluate] returns
 * null for them — the editor uses that to gray out the rule's slider impact
 * on frame-level tinting.
 */
object FrameRuleEvaluator {

    /**
     * @return true if the rule passes at this frame, false if it fails,
     *         or null if the rule doesn't apply to per-frame evaluation
     *         (e.g., phase-duration rules without a stroke context).
     */
    fun evaluate(
        rule: BaselineRule,
        baseline: PersonalBaseline,
        metricValue: Double?
    ): Boolean? = when (rule) {
        is BaselineRule.ConsistencyRule -> evaluateConsistency(rule, baseline, metricValue)
        is BaselineRule.RegressionRule -> evaluateRegression(rule, baseline, metricValue)
        is BaselineRule.RhythmRule -> null
    }

    private fun evaluateConsistency(
        rule: BaselineRule.ConsistencyRule,
        baseline: PersonalBaseline,
        metricValue: Double?
    ): Boolean? {
        if (metricValue == null) return null
        val stats = baseline.metricStats[rule.metricKey] ?: return null
        if (stats.std <= 0.0) return true
        return abs(metricValue - stats.mean) <= rule.kSigma * stats.std
    }

    private fun evaluateRegression(
        rule: BaselineRule.RegressionRule,
        baseline: PersonalBaseline,
        metricValue: Double?
    ): Boolean? {
        if (metricValue == null) return null
        val stats = baseline.metricStats[rule.metricKey] ?: return null
        return metricValue >= stats.mean - rule.maxDropFromMean
    }
}
