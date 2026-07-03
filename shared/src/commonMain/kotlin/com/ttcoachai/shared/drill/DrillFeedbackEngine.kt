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

    fun evaluateRep(
        metrics: Map<String, Double>,
        baseline: PersonalBaseline,
        rules: List<BaselineRule>
    ): List<FeedbackCue> {
        val cues = mutableListOf<FeedbackCue>()
        for (rule in rules) {
            if (rule is BaselineRule.RhythmRule) continue
            val value = metrics[rule.metricKey] ?: continue // no measurement → silent
            val passed = FrameRuleEvaluator.evaluate(rule, baseline, value) ?: continue
            if (passed) continue
            val stats = baseline.metricStats[rule.metricKey] ?: continue
            val delta = value - stats.mean
            cues += FeedbackCue(
                metricKey = rule.metricKey,
                direction = if (delta > 0) CueDirection.TOO_HIGH else CueDirection.TOO_LOW,
                deltaFromMean = delta,
                severity = if (stats.std > 0.0) abs(delta) / stats.std else 0.0,
                precision = MetricPrecisionPolicy.precisionFor(rule.metricKey)
            )
        }
        return cues.sortedByDescending { it.severity }
    }
}
