package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreMessageTemplatesTest {

    private fun cue(
        key: String,
        direction: CueDirection = CueDirection.TOO_HIGH,
        delta: Double = 14.7,
        precision: MetricPrecision = MetricPrecisionPolicy.precisionFor(key)
    ) = FeedbackCue(key, direction, if (direction == CueDirection.TOO_HIGH) delta else -delta, 2.5, precision)

    @Test
    fun followThroughAngle2dTooHighEnIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D, CueDirection.TOO_HIGH),
            FeedbackLang.EN
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_HIGH),
            FeedbackLang.EN
        )
        assertFalse(msg == fallback, "follow_through_angle_2d TOO_HIGH EN must be dedicated, not fallback")
        assertTrue("fold in sooner" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun followThroughAngle2dTooHighUaIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D, CueDirection.TOO_HIGH),
            FeedbackLang.UA
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_HIGH),
            FeedbackLang.UA
        )
        assertFalse(msg == fallback, "follow_through_angle_2d TOO_HIGH UA must be dedicated, not fallback")
        assertTrue("дай руці" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun followThroughAngle2dTooLowEnIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D, CueDirection.TOO_LOW),
            FeedbackLang.EN
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_LOW),
            FeedbackLang.EN
        )
        assertFalse(msg == fallback, "follow_through_angle_2d TOO_LOW EN must be dedicated, not fallback")
        assertTrue("finish higher" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun followThroughAngle2dTooLowUaIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D, CueDirection.TOO_LOW),
            FeedbackLang.UA
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_LOW),
            FeedbackLang.UA
        )
        assertFalse(msg == fallback, "follow_through_angle_2d TOO_LOW UA must be dedicated, not fallback")
        assertTrue("закінчи вище" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun strokeSpeedTooHighEnIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_STROKE_SPEED, CueDirection.TOO_HIGH),
            FeedbackLang.EN
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_HIGH),
            FeedbackLang.EN
        )
        assertFalse(msg == fallback, "stroke_speed TOO_HIGH EN must be dedicated, not fallback")
        assertTrue("ease off" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun strokeSpeedTooHighUaIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_STROKE_SPEED, CueDirection.TOO_HIGH),
            FeedbackLang.UA
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_HIGH),
            FeedbackLang.UA
        )
        assertFalse(msg == fallback, "stroke_speed TOO_HIGH UA must be dedicated, not fallback")
        assertTrue("стримай" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun strokeSpeedTooLowEnIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_STROKE_SPEED, CueDirection.TOO_LOW),
            FeedbackLang.EN
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_LOW),
            FeedbackLang.EN
        )
        assertFalse(msg == fallback, "stroke_speed TOO_LOW EN must be dedicated, not fallback")
        assertTrue("commit and swing" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun strokeSpeedTooLowUaIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_STROKE_SPEED, CueDirection.TOO_LOW),
            FeedbackLang.UA
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_LOW),
            FeedbackLang.UA
        )
        assertFalse(msg == fallback, "stroke_speed TOO_LOW UA must be dedicated, not fallback")
        assertTrue("сміливіше" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun coilRatioTooHighEnIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_COIL_RATIO, CueDirection.TOO_HIGH),
            FeedbackLang.EN
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_HIGH),
            FeedbackLang.EN
        )
        assertFalse(msg == fallback, "coil_ratio TOO_HIGH EN must be dedicated, not fallback")
        assertTrue("stay a touch more compact" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun coilRatioTooHighUaIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_COIL_RATIO, CueDirection.TOO_HIGH),
            FeedbackLang.UA
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_HIGH),
            FeedbackLang.UA
        )
        assertFalse(msg == fallback, "coil_ratio TOO_HIGH UA must be dedicated, not fallback")
        assertTrue("тримайся трохи компактніше" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun coilRatioTooLowEnIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_COIL_RATIO, CueDirection.TOO_LOW),
            FeedbackLang.EN
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_LOW),
            FeedbackLang.EN
        )
        assertFalse(msg == fallback, "coil_ratio TOO_LOW EN must be dedicated, not fallback")
        assertTrue("rotate through" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun coilRatioTooLowUaIsDedicated() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_COIL_RATIO, CueDirection.TOO_LOW),
            FeedbackLang.UA
        )
        val fallback = CoreMessageTemplates.TEMPLATES.format(
            cue("unknown_metric", CueDirection.TOO_LOW),
            FeedbackLang.UA
        )
        assertFalse(msg == fallback, "coil_ratio TOO_LOW UA must be dedicated, not fallback")
        assertTrue("розкрийся" in msg, "expected distinctive phrase in: $msg")
    }

    @Test
    fun qualitativeKeysNeverShowDegrees() {
        for (key in listOf(DrillMetrics.METRIC_STROKE_SPEED, DrillMetrics.METRIC_COIL_RATIO)) {
            for (dir in CueDirection.entries) {
                for (lang in FeedbackLang.entries) {
                    val msg = CoreMessageTemplates.TEMPLATES.format(cue(key, dir), lang)
                    assertFalse("°" in msg, "$key/$dir/$lang must not contain degrees: $msg")
                }
            }
        }
    }

    @Test
    fun preciseKeyShowsDegrees() {
        for (dir in CueDirection.entries) {
            for (lang in FeedbackLang.entries) {
                val msg = CoreMessageTemplates.TEMPLATES.format(cue(DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D, dir), lang)
                assertTrue("°" in msg, "follow_through_angle_2d/$dir/$lang must contain degrees: $msg")
            }
        }
    }
}
