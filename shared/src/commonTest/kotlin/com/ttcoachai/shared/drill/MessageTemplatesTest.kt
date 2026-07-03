package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageTemplatesTest {

    private fun cue(
        key: String,
        direction: CueDirection = CueDirection.TOO_HIGH,
        delta: Double = 14.7,
        precision: MetricPrecision = MetricPrecisionPolicy.precisionFor(key)
    ) = FeedbackCue(key, direction, if (direction == CueDirection.TOO_HIGH) delta else -delta, 2.5, precision)

    @Test
    fun knownMetricPreciseGetsDegreeSuffix() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_ELBOW_ANGLE, delta = 14.7, precision = MetricPrecision.PRECISE_DEGREES),
            FeedbackLang.EN
        )
        assertTrue("15°" in msg, "expected rounded degrees in: $msg")
    }

    @Test
    fun knownMetricQualitativeGetsNoSuffix() {
        val msg = CoreMessageTemplates.TEMPLATES.format(
            cue(DrillMetrics.METRIC_ELBOW_ANGLE, precision = MetricPrecision.QUALITATIVE),
            FeedbackLang.EN
        )
        assertFalse("°" in msg, "qualitative precision must suppress degrees: $msg")
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun unknownMetricFallsBackAndNeverShowsSuffixEvenWhenPrecise() {
        val q = cue("body_rotation", precision = MetricPrecision.QUALITATIVE)
        val p = cue("body_rotation", precision = MetricPrecision.PRECISE_DEGREES)
        for (c in listOf(q, p)) {
            for (lang in FeedbackLang.entries) {
                val msg = CoreMessageTemplates.TEMPLATES.format(c, lang)
                assertFalse("°" in msg, "unknown metric must never show degrees: $msg")
                assertTrue(msg.isNotBlank())
            }
        }
    }

    @Test
    fun unknownMetricUsesDirectionalFallback() {
        val high = CoreMessageTemplates.TEMPLATES.format(
            cue("body_rotation", CueDirection.TOO_HIGH), FeedbackLang.EN
        )
        val low = CoreMessageTemplates.TEMPLATES.format(
            cue("body_rotation", CueDirection.TOO_LOW), FeedbackLang.EN
        )
        assertFalse(high == low, "TOO_HIGH and TOO_LOW fallbacks must differ")
    }

    @Test
    fun ukrainianAndEnglishBothProduceNonBlankMessages() {
        for (key in DrillMetrics.ALL_KEYS) {
            for (dir in CueDirection.entries) {
                for (lang in FeedbackLang.entries) {
                    val msg = CoreMessageTemplates.TEMPLATES.format(cue(key, dir), lang)
                    assertTrue(msg.isNotBlank(), "$key/$dir/$lang must have a message")
                }
            }
        }
    }

    @Test
    fun positiveMessagesExistInBothLanguages() {
        assertEquals("Good rep — keep that rhythm", CoreMessageTemplates.TEMPLATES.positive(FeedbackLang.EN))
        assertEquals("Гарний повтор — так тримати", CoreMessageTemplates.TEMPLATES.positive(FeedbackLang.UA))
    }
}
