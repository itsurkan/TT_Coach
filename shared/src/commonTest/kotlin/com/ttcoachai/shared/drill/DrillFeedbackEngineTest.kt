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
