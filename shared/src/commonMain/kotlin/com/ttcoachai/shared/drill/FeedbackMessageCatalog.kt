package com.ttcoachai.shared.drill

enum class FeedbackLang { EN, UA }

/**
 * UA + EN feedback strings. Lives in shared code (no Android resources) so the same
 * catalog serves Android, desktop fixture runs, and the future iOS app.
 *
 * Trust rule: the degree number is inserted ONLY for PRECISE_DEGREES cues;
 * qualitative cues get direction-only phrasing.
 *
 * Thin wrapper: the phrase data now lives in [CoreMessageTemplates.TEMPLATES],
 * dispatched generically by [MessageTemplates]. Kept for API compatibility;
 * output is unchanged bit-for-bit for every existing (metric, direction, lang,
 * precision) combination.
 */
object FeedbackMessageCatalog {

    fun format(cue: FeedbackCue, lang: FeedbackLang): String =
        CoreMessageTemplates.TEMPLATES.format(cue, lang)

    fun positive(lang: FeedbackLang): String =
        CoreMessageTemplates.TEMPLATES.positive(lang)
}
