package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineRule
import com.ttcoachai.shared.analysis.BaselineRuleFactory
import com.ttcoachai.shared.models.MetricStats
import com.ttcoachai.shared.models.PersonalBaseline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrillFeedbackEngineTest {

    private fun baseline(vararg stats: Pair<String, MetricStats>) = PersonalBaseline(
        drillType = "forehand_drive",
        metricStats = stats.toMap(),
        phaseDurationsMs = mapOf("stroke_total_ms" to MetricStats(800.0, 40.0, 750.0, 850.0, 10)),
        repCount = 10,
        excludedRepIndices = emptyList(),
        qualityScore = 0.9,
        createdAtMs = 0L,
        drillerHandedness = "right"
    )

    private val elbowStats = MetricStats(mean = 100.0, std = 5.0, min = 92.0, max = 108.0, sampleCount = 10)
    private val kneeStats = MetricStats(mean = 150.0, std = 4.0, min = 144.0, max = 156.0, sampleCount = 10)

    @Test
    fun withinTwoSigmaYieldsNoCues() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val rules = BaselineRuleFactory.defaultRules(b)
        val cues = DrillFeedbackEngine.evaluateRep(
            mapOf(DrillMetrics.METRIC_ELBOW_ANGLE to 108.0), b, rules
        )
        assertTrue(cues.isEmpty(), "108 is within 100±10, got $cues")
    }

    @Test
    fun beyondTwoSigmaYieldsDirectionalCue() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val rules = BaselineRuleFactory.defaultRules(b)
        val cues = DrillFeedbackEngine.evaluateRep(
            mapOf(DrillMetrics.METRIC_ELBOW_ANGLE to 115.0), b, rules
        )
        assertEquals(1, cues.size)
        val cue = cues[0]
        assertEquals(DrillMetrics.METRIC_ELBOW_ANGLE, cue.metricKey)
        assertEquals(CueDirection.TOO_HIGH, cue.direction)
        assertEquals(15.0, cue.deltaFromMean, 1e-9)
        assertEquals(3.0, cue.severity, 1e-9)
        assertEquals(MetricPrecision.PRECISE_DEGREES, cue.precision)
    }

    @Test
    fun belowMeanIsTooLow() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val cues = DrillFeedbackEngine.evaluateRep(
            mapOf(DrillMetrics.METRIC_ELBOW_ANGLE to 85.0), b, BaselineRuleFactory.defaultRules(b)
        )
        assertEquals(CueDirection.TOO_LOW, cues[0].direction)
    }

    @Test
    fun cuesSortedBySeverityDescending() {
        val b = baseline(
            DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats,   // 115 → 3σ
            DrillMetrics.METRIC_KNEE_BEND to kneeStats        // 160 → 2.5σ
        )
        val cues = DrillFeedbackEngine.evaluateRep(
            mapOf(
                DrillMetrics.METRIC_ELBOW_ANGLE to 115.0,
                DrillMetrics.METRIC_KNEE_BEND to 160.0
            ),
            b, BaselineRuleFactory.defaultRules(b)
        )
        assertEquals(2, cues.size)
        assertEquals(DrillMetrics.METRIC_ELBOW_ANGLE, cues[0].metricKey)
        assertTrue(cues[0].severity > cues[1].severity)
    }

    @Test
    fun missingMetricIsSilent() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val cues = DrillFeedbackEngine.evaluateRep(emptyMap(), b, BaselineRuleFactory.defaultRules(b))
        assertTrue(cues.isEmpty(), "no measurement → no feedback (trust rule)")
    }

    @Test
    fun rhythmRulesAreIgnoredPerRep() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val onlyRhythm = listOf(
            BaselineRule.RhythmRule(id = "rhythm:stroke_total_ms", metricKey = "stroke_total_ms", maxDurationDeviationPct = 0.25)
        )
        val cues = DrillFeedbackEngine.evaluateRep(
            mapOf(DrillMetrics.METRIC_ELBOW_ANGLE to 115.0), b, onlyRhythm
        )
        assertTrue(cues.isEmpty())
    }

    @Test
    fun unknownMetricGetsQualitativePrecision() {
        assertEquals(MetricPrecision.QUALITATIVE, MetricPrecisionPolicy.precisionFor("body_rotation"))
        assertEquals(MetricPrecision.PRECISE_DEGREES, MetricPrecisionPolicy.precisionFor(DrillMetrics.METRIC_ELBOW_ANGLE))
    }

    @Test
    fun fourArgOverloadUsesSuppliedPrecisionFunctionInsteadOfPolicy() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val rules = BaselineRuleFactory.defaultRules(b)
        val cues = DrillFeedbackEngine.evaluateRep(
            mapOf(DrillMetrics.METRIC_ELBOW_ANGLE to 115.0), b, rules
        ) { MetricPrecision.QUALITATIVE }
        assertEquals(1, cues.size)
        assertEquals(MetricPrecision.QUALITATIVE, cues[0].precision)
    }

    // ---- BaselineRule.RangeRule (custom-drill editor knee-bend targets, per-band override) ----

    @Test
    fun rangeRuleWithinBandYieldsNoCue() {
        val b = baseline(DrillMetrics.METRIC_KNEE_BEND to kneeStats) // mean 150, std 4
        val rules = listOf(
            BaselineRule.RangeRule(id = "range:knee_bend", metricKey = DrillMetrics.METRIC_KNEE_BEND, min = 110.0, max = 130.0)
        )
        val cues = DrillFeedbackEngine.evaluateRep(mapOf(DrillMetrics.METRIC_KNEE_BEND to 120.0), b, rules)
        assertTrue(cues.isEmpty(), "120 is inside [110,130], got $cues")
    }

    @Test
    fun rangeRuleBelowMinYieldsTooLowCue() {
        val b = baseline(DrillMetrics.METRIC_KNEE_BEND to kneeStats)
        val rules = listOf(
            BaselineRule.RangeRule(id = "range:knee_bend", metricKey = DrillMetrics.METRIC_KNEE_BEND, min = 110.0, max = 130.0)
        )
        val cues = DrillFeedbackEngine.evaluateRep(mapOf(DrillMetrics.METRIC_KNEE_BEND to 100.0), b, rules)
        assertEquals(1, cues.size)
        assertEquals(CueDirection.TOO_LOW, cues[0].direction)
        assertEquals(-10.0, cues[0].deltaFromMean, 1e-9)
    }

    @Test
    fun rangeRuleAboveMaxYieldsTooHighCue() {
        val b = baseline(DrillMetrics.METRIC_KNEE_BEND to kneeStats)
        val rules = listOf(
            BaselineRule.RangeRule(id = "range:knee_bend", metricKey = DrillMetrics.METRIC_KNEE_BEND, min = 110.0, max = 130.0)
        )
        val cues = DrillFeedbackEngine.evaluateRep(mapOf(DrillMetrics.METRIC_KNEE_BEND to 140.0), b, rules)
        assertEquals(1, cues.size)
        assertEquals(CueDirection.TOO_HIGH, cues[0].direction)
        assertEquals(10.0, cues[0].deltaFromMean, 1e-9)
    }

    @Test
    fun rangeRuleOverridesBaselineThatWouldOtherwisePass() {
        // The user's exact scenario: baseline mean 97 (well within 2 sigma, would pass a
        // ConsistencyRule) but the editor's explicit knee-bend strike band 110-130 must
        // still flag it.
        val looseKneeStats = com.ttcoachai.shared.models.MetricStats(mean = 97.0, std = 15.0, min = 70.0, max = 120.0, sampleCount = 10)
        val b = baseline(DrillMetrics.METRIC_KNEE_BEND to looseKneeStats)
        val consistencyRules = BaselineRuleFactory.defaultRules(b)
        assertTrue(
            DrillFeedbackEngine.evaluateRep(mapOf(DrillMetrics.METRIC_KNEE_BEND to 97.0), b, consistencyRules).isEmpty(),
            "sanity: 97 must pass the plain baseline consistency rule"
        )

        val bandRules = BaselineRuleFactory.applyRangeOverrides(
            consistencyRules,
            mapOf(DrillMetrics.METRIC_KNEE_BEND to 110.0..130.0)
        )
        val cues = DrillFeedbackEngine.evaluateRep(mapOf(DrillMetrics.METRIC_KNEE_BEND to 97.0), b, bandRules)
        assertEquals(1, cues.size, "band override must flag 97 even though baseline consistency alone would pass it")
        assertEquals(CueDirection.TOO_LOW, cues[0].direction)
        // baseline still has stats for this metric -> severity normalized by std, not the fixed fallback scale.
        assertEquals(13.0 / 15.0, cues[0].severity, 1e-9)
    }

    @Test
    fun rangeRuleWithNoBaselineStatsFallsBackToFixedSeverityScale() {
        val b = baseline() // no stats at all for knee_bend
        val rules = listOf(
            BaselineRule.RangeRule(id = "range:knee_bend", metricKey = DrillMetrics.METRIC_KNEE_BEND, min = 110.0, max = 130.0)
        )
        val cues = DrillFeedbackEngine.evaluateRep(mapOf(DrillMetrics.METRIC_KNEE_BEND to 140.0), b, rules)
        assertEquals(1, cues.size)
        assertEquals(10.0 / DrillFeedbackEngine.DEFAULT_RANGE_SEVERITY_SCALE_DEGREES, cues[0].severity, 1e-9)
    }

    @Test
    fun threeArgOverloadDelegatesToMetricPrecisionPolicy() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val rules = BaselineRuleFactory.defaultRules(b)
        val threeArg = DrillFeedbackEngine.evaluateRep(mapOf(DrillMetrics.METRIC_ELBOW_ANGLE to 115.0), b, rules)
        val fourArg = DrillFeedbackEngine.evaluateRep(
            mapOf(DrillMetrics.METRIC_ELBOW_ANGLE to 115.0), b, rules, MetricPrecisionPolicy::precisionFor
        )
        assertEquals(fourArg, threeArg)
    }
}
