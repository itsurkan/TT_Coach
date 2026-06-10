package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackMessageCatalogTest {

    private fun cue(
        key: String,
        direction: CueDirection = CueDirection.TOO_HIGH,
        delta: Double = 14.7,
        precision: MetricPrecision = MetricPrecisionPolicy.precisionFor(key)
    ) = FeedbackCue(key, direction, if (direction == CueDirection.TOO_HIGH) delta else -delta, 2.5, precision)

    @Test
    fun everyKnownMetricDirectionAndLangHasAMessage() {
        for (key in DrillMetrics.ALL_KEYS) {
            for (dir in CueDirection.entries) {
                for (lang in FeedbackLang.entries) {
                    val msg = FeedbackMessageCatalog.format(cue(key, dir), lang)
                    assertTrue(msg.isNotBlank(), "$key/$dir/$lang must have a message")
                }
            }
        }
    }

    @Test
    fun preciseCuesContainRoundedDegrees() {
        val msg = FeedbackMessageCatalog.format(cue(DrillMetrics.METRIC_ELBOW_ANGLE, delta = 14.7), FeedbackLang.EN)
        assertTrue("15°" in msg, "rounded degrees expected in: $msg")
    }

    @Test
    fun qualitativeCuesNeverContainDegrees() {
        val q = cue("body_rotation", precision = MetricPrecision.QUALITATIVE)
        for (lang in FeedbackLang.entries) {
            val msg = FeedbackMessageCatalog.format(q, lang)
            assertFalse("°" in msg, "qualitative cue must not show degrees: $msg")
            assertTrue(msg.isNotBlank())
        }
    }

    @Test
    fun ukrainianMessagesAreCyrillic() {
        val msg = FeedbackMessageCatalog.format(cue(DrillMetrics.METRIC_ELBOW_ANGLE), FeedbackLang.UA)
        assertTrue(msg.any { it in 'А'..'я' || it == 'і' || it == 'ї' || it == 'є' }, "expected Cyrillic: $msg")
    }

    @Test
    fun positiveMessagesExistInBothLanguages() {
        assertTrue(FeedbackMessageCatalog.positive(FeedbackLang.EN).isNotBlank())
        assertTrue(FeedbackMessageCatalog.positive(FeedbackLang.UA).isNotBlank())
    }

    @Test
    fun directionsProduceDifferentMessages() {
        assertFalse(
            FeedbackMessageCatalog.format(cue(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_HIGH), FeedbackLang.EN) ==
                FeedbackMessageCatalog.format(cue(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_LOW), FeedbackLang.EN)
        )
    }
}
