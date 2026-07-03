package com.ttcoachai.shared.drill

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Data-driven message catalog: generalizes [FeedbackMessageCatalog]'s hard-coded
 * `when (cue.metricKey)` dispatch to a template map keyed by (metric, direction,
 * language), so new movements can register their own phrases without editing this
 * class (docs/superpowers/specs/2026-07-02-generic-movement-pipeline-design.md).
 * [FeedbackMessageCatalog] now delegates to [CoreMessageTemplates.TEMPLATES].
 *
 * Trust rule (context doc §3): the degree number is appended ONLY when a specific
 * template was found for the cue's metric AND the cue is PRECISE_DEGREES — unknown
 * metrics fall back to qualitative-only phrasing, never degrees, regardless of the
 * cue's declared precision.
 */
data class MessageTemplates(
    /** (metricKey, direction, lang) -> base phrase (no degree suffix — appended by [format]). */
    val templates: Map<TemplateKey, String>,
    val positiveMessages: Map<FeedbackLang, String>,
    val fallbackHigh: Map<FeedbackLang, String>,
    val fallbackLow: Map<FeedbackLang, String>
) {
    data class TemplateKey(val metricKey: String, val direction: CueDirection, val lang: FeedbackLang)

    fun format(cue: FeedbackCue, lang: FeedbackLang): String {
        val key = TemplateKey(cue.metricKey, cue.direction, lang)
        val base = templates[key]
        if (base != null) {
            return withDeg(base, cue, lang)
        }
        val fallbackMap = if (cue.direction == CueDirection.TOO_HIGH) fallbackHigh else fallbackLow
        return fallbackMap.getValue(lang)
    }

    fun positive(lang: FeedbackLang): String = positiveMessages.getValue(lang)

    private fun withDeg(base: String, cue: FeedbackCue, lang: FeedbackLang): String {
        if (cue.precision != MetricPrecision.PRECISE_DEGREES) return base
        val d = abs(cue.deltaFromMean).roundToInt()
        return when (lang) {
            FeedbackLang.EN -> "$base (about $d° off your baseline)"
            FeedbackLang.UA -> "$base (близько $d° від твого еталону)"
        }
    }
}
