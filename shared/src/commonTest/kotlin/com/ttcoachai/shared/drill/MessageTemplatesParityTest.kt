package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Byte-equality guard: FeedbackMessageCatalog (kept for API compatibility) must
 * produce identical output to the new MessageTemplates/CoreMessageTemplates
 * dispatch for every existing (metric, direction, lang) combination, at
 * PRECISE_DEGREES (the only precision the five core metrics ever carry).
 */
class MessageTemplatesParityTest {

    private fun cue(key: String, direction: CueDirection, delta: Double = 14.7) =
        FeedbackCue(key, direction, if (direction == CueDirection.TOO_HIGH) delta else -delta, 2.5, MetricPrecision.PRECISE_DEGREES)

    @Test
    fun allFiveMetricsBothDirectionsBothLangsMatchLegacyCatalog() {
        for (key in DrillMetrics.ALL_KEYS) {
            for (dir in CueDirection.entries) {
                for (lang in FeedbackLang.entries) {
                    val c = cue(key, dir)
                    val legacy = FeedbackMessageCatalog.format(c, lang)
                    val generic = CoreMessageTemplates.TEMPLATES.format(c, lang)
                    assertEquals(legacy, generic, "$key/$dir/$lang mismatch")
                }
            }
        }
    }

    @Test
    fun positiveMatchesLegacyCatalog() {
        for (lang in FeedbackLang.entries) {
            assertEquals(FeedbackMessageCatalog.positive(lang), CoreMessageTemplates.TEMPLATES.positive(lang))
        }
    }
}
