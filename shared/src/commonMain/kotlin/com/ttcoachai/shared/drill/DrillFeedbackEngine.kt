package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineRule
import com.ttcoachai.shared.analysis.FrameRuleEvaluator
import com.ttcoachai.shared.models.PersonalBaseline
import kotlin.math.abs

/**
 * Turns per-rep metric values + baseline rules into prioritized feedback cues.
 * Rule semantics live in [FrameRuleEvaluator]; rule derivation in BaselineRuleFactory
 * (single source of truth). Rhythm rules are session-level and skipped here.
 */
object DrillFeedbackEngine {

    /**
     * Severity normalization for [com.ttcoachai.shared.analysis.BaselineRule.RangeRule]
     * cues when the baseline has no stats for that metric (bands can be set on metrics
     * the baseline never derived, e.g. a coach-authored target with no matching baseline
     * sample) — chosen to sit in the same ballpark as typical in-plane-metric baseline σ
     * (roughly 5-15° in derived baselines) so band severities sort comparably against
     * consistency-rule severities.
     */
    const val DEFAULT_RANGE_SEVERITY_SCALE_DEGREES: Double = 10.0

    fun evaluateRep(
        metrics: Map<String, Double>,
        baseline: PersonalBaseline,
        rules: List<BaselineRule>
    ): List<FeedbackCue> = evaluateRep(metrics, baseline, rules, MetricPrecisionPolicy::precisionFor)

    /**
     * Generalized form: precision comes from [precisionFor] rather than the
     * hard-coded [MetricPrecisionPolicy] (docs/superpowers/specs/
     * 2026-07-02-generic-movement-pipeline-design.md) — lets a future
     * MovementDefinition supply its own per-metric trust-rule policy. The 3-arg
     * overload keeps [MetricPrecisionPolicy.precisionFor] as the default, unchanged.
     */
    fun evaluateRep(
        metrics: Map<String, Double>,
        baseline: PersonalBaseline,
        rules: List<BaselineRule>,
        precisionFor: (String) -> MetricPrecision
    ): List<FeedbackCue> {
        val cues = mutableListOf<FeedbackCue>()
        for (rule in rules) {
            if (rule is BaselineRule.RhythmRule) continue
            val value = metrics[rule.metricKey] ?: continue // no measurement → silent
            val passed = FrameRuleEvaluator.evaluate(rule, baseline, value) ?: continue
            if (passed) continue
            val stats = baseline.metricStats[rule.metricKey]
            val delta = if (rule is BaselineRule.RangeRule) {
                // Distance outside the explicit band, signed so direction falls out of
                // delta > 0 below exactly like the baseline-mean rules.
                when {
                    value > rule.max -> value - rule.max
                    value < rule.min -> value - rule.min
                    else -> 0.0 // unreachable: FrameRuleEvaluator would have reported passed=true
                }
            } else {
                stats?.let { value - it.mean } ?: continue
            }
            val severity = when {
                stats != null && stats.std > 0.0 -> abs(delta) / stats.std
                rule is BaselineRule.RangeRule -> abs(delta) / DEFAULT_RANGE_SEVERITY_SCALE_DEGREES
                else -> 0.0
            }
            cues += FeedbackCue(
                metricKey = rule.metricKey,
                direction = if (delta > 0) CueDirection.TOO_HIGH else CueDirection.TOO_LOW,
                deltaFromMean = delta,
                severity = severity,
                precision = precisionFor(rule.metricKey)
            )
        }
        return cues.sortedByDescending { it.severity }
    }
}
