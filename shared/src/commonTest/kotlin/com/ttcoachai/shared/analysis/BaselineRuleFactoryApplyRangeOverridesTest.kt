package com.ttcoachai.shared.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [BaselineRuleFactory.applyRangeOverrides] — overlays explicit min..max bands (custom-drill
 * editor targets) on top of a derived rule list, replacing only the [BaselineRule.ConsistencyRule]
 * for the overridden metric(s) and leaving every other rule untouched.
 */
class BaselineRuleFactoryApplyRangeOverridesTest {

    private val elbowConsistency = BaselineRule.ConsistencyRule(id = "consistency:elbow_angle", metricKey = "elbow_angle", kSigma = 2.0)
    private val kneeConsistency = BaselineRule.ConsistencyRule(id = "consistency:knee_bend", metricKey = "knee_bend", kSigma = 2.0)
    private val rhythm = BaselineRule.RhythmRule(id = "rhythm:stroke_total_ms", metricKey = "stroke_total_ms", maxDurationDeviationPct = 0.25)

    @Test
    fun emptyBandsReturnsRulesUnchanged() {
        val rules = listOf(elbowConsistency, kneeConsistency, rhythm)
        val result = BaselineRuleFactory.applyRangeOverrides(rules, emptyMap())
        assertEquals(rules, result)
    }

    @Test
    fun bandReplacesConsistencyRuleForThatMetricOnly() {
        val rules = listOf(elbowConsistency, kneeConsistency, rhythm)
        val result = BaselineRuleFactory.applyRangeOverrides(rules, mapOf("knee_bend" to 110.0..130.0))

        assertTrue(elbowConsistency in result, "elbow rule must pass through untouched")
        assertTrue(rhythm in result, "rhythm rule must pass through untouched")
        assertTrue(kneeConsistency !in result, "knee consistency rule must be dropped")

        val rangeRule = result.filterIsInstance<BaselineRule.RangeRule>().single()
        assertEquals("knee_bend", rangeRule.metricKey)
        assertEquals(110.0, rangeRule.min, 1e-9)
        assertEquals(130.0, rangeRule.max, 1e-9)
    }

    @Test
    fun bandForMetricWithNoExistingConsistencyRuleIsAppended() {
        val result = BaselineRuleFactory.applyRangeOverrides(listOf(rhythm), mapOf("knee_bend" to 110.0..130.0))
        assertEquals(2, result.size)
        assertTrue(rhythm in result)
        assertTrue(result.any { it is BaselineRule.RangeRule && it.metricKey == "knee_bend" })
    }
}
