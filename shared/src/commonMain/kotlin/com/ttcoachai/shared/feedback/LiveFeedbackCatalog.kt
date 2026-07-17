package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.drill.FeedbackLang

/**
 * Verbatim port of the live-training string tables from
 * `app/src/main/res/values/strings.xml` (EN) and `values-uk/strings.xml` (UA).
 *
 * This is the "short_" / "_full" key family used by `FeedbackItem.message` and
 * `AnalysisResult.recommendations` (error_ and rec_ base keys), plus the positive-feedback
 * pool, motivational milestones, session summary template, parameter-feedback
 * templates and improvement tips — everything the old `FeedbackGenerator`
 * resolved via Android resource lookups (`getIdentifier`).
 *
 * IMPORTANT — fallback-to-EN quirk preserved on purpose: several key families
 * (`positive_feedback_*`, `feedback_score_format`, `feedback_param_*`,
 * `improvement_tip_*`) were never translated in `values-uk/strings.xml`. Android's
 * resource resolution silently falls back to the default (EN) string for a
 * missing UA override, so the live app has always shown these particular strings
 * in English even when the user's locale is Ukrainian. [resolve] and the
 * dedicated accessors below reproduce that fallback exactly — do not "fix" it
 * into a translation without confirming the product wants a behavior change.
 *
 * Lives in shared/commonMain (zero external deps) so Android, desktop tests and
 * the future iOS app all read the same table.
 */
object LiveFeedbackCatalog {

    // ---- short_*/*_full key-family table (error_*/rec_* base keys) ----------

    private data class ShortFull(val short: String, val full: String)

    private val EN: Map<String, ShortFull> = mapOf(
        "error_wrist_bent" to ShortFull("Straighten wrist", "Wrist bent - straighten it"),
        "error_low_rotation" to ShortFull("More rotation", "Insufficient body rotation - turn more"),
        "error_high_contact" to ShortFull("Lower contact", "Contact too high - lower strike point"),
        "error_low_contact" to ShortFull("Higher contact", "Contact too low - raise strike point"),
        "error_no_follow_through" to ShortFull("Complete stroke", "Insufficient follow-through - complete the movement"),
        "error_elbow_far" to ShortFull("Elbow closer", "Elbow far from body - keep it closer"),
        "error_elbow_close" to ShortFull("Elbow looser", "Elbow pressed to body - keep it looser"),
        "error_slow_stroke" to ShortFull("Faster", "Stroke too slow - add speed"),
        "error_fast_stroke" to ShortFull("Slower", "Stroke too fast - control speed"),
        "error_straight_legs" to ShortFull("Bend your knees", "Legs straighter than ideal - bend the knees more"),
        "error_legs_too_bent" to ShortFull("Rise a little", "Knees more bent than ideal - rise a little"),

        "rec_straighten_wrist" to ShortFull("Wrist straight", "Keep wrist straight during stroke"),
        "rec_rotate_more" to ShortFull("Body rotation", "More body rotation for power"),
        "rec_contact_height" to ShortFull("Contact height", "Contact at table level - optimal height"),
        "rec_complete_follow" to ShortFull("Finish movement", "Complete follow-through upward and forward"),
        "rec_keep_elbow_close" to ShortFull("Elbow close", "Keep elbow close to body"),
        "rec_move_elbow_away" to ShortFull("Release elbow", "Don't press your elbow too tightly"),
        "rec_increase_speed" to ShortFull("More speed", "Add speed for efficiency"),
        "rec_control_speed" to ShortFull("Less speed", "Reduce speed for better control"),
        "rec_bend_knees" to ShortFull("Stay down", "Stay in a slightly crouched stance, knees bent"),
        "rec_rise_stance" to ShortFull("Stand taller", "Don't crouch too low - rise slightly")
    )

    private val UA: Map<String, ShortFull> = mapOf(
        "error_wrist_bent" to ShortFull("Випряміть зап’ястя", "Зап’ястя зігнуте - випряміть його"),
        "error_low_rotation" to ShortFull("Більше ротації", "Недостатня ротація корпусу - поверніться більше"),
        "error_high_contact" to ShortFull("Нижче контакт", "Контакт надто високо - опустіть точку удару"),
        "error_low_contact" to ShortFull("Вище контакт", "Контакт надто низько - підніміть точку удару"),
        "error_no_follow_through" to ShortFull("Доведіть удар", "Недостатнє проведення - доведіть рух до кінця"),
        "error_elbow_far" to ShortFull("Лікоть ближче", "Лікоть далеко від тіла - тримайте його ближче"),
        "error_elbow_close" to ShortFull("Лікоть вільніше", "Лікоть притиснутий до тіла - тримайте його вільніше"),
        "error_slow_stroke" to ShortFull("Швидше", "Занадто повільний удар - додайте швидкості"),
        "error_fast_stroke" to ShortFull("Повільніше", "Занадто швидкий удар - контролюйте швидкість"),
        "error_straight_legs" to ShortFull("Зігни коліна", "Ноги пряміші за ідеал - зігни коліна більше"),
        "error_legs_too_bent" to ShortFull("Підведись трохи", "Коліна зігнуті більше за ідеал - підведись трохи"),

        "rec_straighten_wrist" to ShortFull("Зап’ястя рівно", "Тримайте зап’ястя рівно під час удару"),
        "rec_rotate_more" to ShortFull("Ротація корпусу", "Більше ротації корпусу для потужності"),
        "rec_contact_height" to ShortFull("Висота контакту", "Контакт на рівні столу - оптимальна висота"),
        "rec_complete_follow" to ShortFull("Завершіть рух", "Завершуйте проведення вгору-вперед"),
        "rec_keep_elbow_close" to ShortFull("Лікоть близько", "Тримайте лікоть близько до тіла"),
        "rec_move_elbow_away" to ShortFull("Відпустіть лікоть", "Не притискайте лікоть занадто сильно"),
        "rec_increase_speed" to ShortFull("Більше швидкості", "Додайте швидкості для ефективності"),
        "rec_control_speed" to ShortFull("Менше швидкості", "Зменшіть швидкість для кращого контролю"),
        "rec_bend_knees" to ShortFull("Тримай присід", "Тримай стійку з трохи зігнутими колінами"),
        "rec_rise_stance" to ShortFull("Стань вище", "Не присідай занадто низько - трохи випрямись")
    )

    /**
     * Resolve a `FeedbackItem.message` / recommendation base key (e.g. `error_wrist_bent`,
     * `rec_rotate_more`) to its display string.
     *
     * @param short true for the `short_$key` family (live TTS cue), false for the
     *   `${key}_full` family (detailed UI text).
     * @return null if the key is unknown (caller falls back to the key prettifier,
     *   matching the old `getIdentifier` miss path).
     */
    fun resolve(key: String, short: Boolean, lang: FeedbackLang): String? {
        if (key.isEmpty()) return null
        val table = if (lang == FeedbackLang.UA) UA else EN
        val entry = table[key] ?: return null
        return if (short) entry.short else entry.full
    }

    // ---- positive feedback pool (untranslated in UA -> always EN) -----------

    val POSITIVE_FEEDBACK: List<String> = listOf(
        "✅ Excellent! Perfect technique!",
        "✅ Great! Keep it up!",
        "✅ Flawless! Continue!",
        "✅ Perfect! Well done!",
        "✅ Super stroke! Very good!"
    )

    /** Positive feedback pool is EN-only in the source resources — [lang] is ignored, kept for API symmetry. */
    fun positiveFeedback(lang: FeedbackLang): List<String> = POSITIVE_FEEDBACK

    // ---- motivational milestones (translated) --------------------------------

    fun motivational(consecutiveGoodStrokes: Int, lang: FeedbackLang): String? {
        val ua = lang == FeedbackLang.UA
        return when (consecutiveGoodStrokes) {
            5 -> if (ua) "🔥 5 гарних ударів підряд! Продовжуйте!" else "🔥 5 good strokes in a row! Keep going!"
            10 -> if (ua) "🎯 10 відмінних ударів! Ви в ударі!" else "🎯 10 excellent strokes! You're on fire!"
            20 -> if (ua) "⭐ 20 ударів поспіль! Неймовірно!" else "⭐ 20 strokes in a row! Incredible!"
            50 -> if (ua) "🏆 50 ударів! Ви майстер!" else "🏆 50 strokes! You're a master!"
            else -> null
        }
    }

    // ---- session summary (translated) -----------------------------------------

    fun sessionSummaryTitle(lang: FeedbackLang): String =
        if (lang == FeedbackLang.UA) "📊 Підсумок тренування\n\n" else "📊 Training Summary\n\n"

    fun sessionTotalStrokes(totalStrokes: Int, lang: FeedbackLang): String =
        if (lang == FeedbackLang.UA) "Всього ударів: $totalStrokes\n" else "Total strokes: $totalStrokes\n"

    fun sessionSuccessful(successfulStrokes: Int, lang: FeedbackLang): String =
        if (lang == FeedbackLang.UA) "Успішних: $successfulStrokes\n" else "Successful: $successfulStrokes\n"

    fun sessionAccuracy(accuracy: Int, lang: FeedbackLang): String =
        if (lang == FeedbackLang.UA) "Точність: $accuracy%\n" else "Accuracy: $accuracy%\n"

    fun sessionAverageScore(averageScore: Int, lang: FeedbackLang): String =
        if (lang == FeedbackLang.UA) "Середня оцінка: $averageScore%\n\n" else "Average score: $averageScore%\n\n"

    // ---- score / parameter feedback (untranslated in UA -> always EN) --------

    /** EN-only in source resources — [lang] ignored, kept for API symmetry. */
    fun feedbackScoreFormat(score: Int, lang: FeedbackLang): String = "Score: $score%\n\n"

    /** EN-only in source resources — [lang] ignored, kept for API symmetry. */
    fun feedbackParamNotDetected(parameterName: String, lang: FeedbackLang): String =
        "❓ $parameterName: not detected"

    /** EN-only in source resources — [lang] ignored, kept for API symmetry. */
    fun feedbackParamPerfect(parameterName: String, measuredValue: Int, lang: FeedbackLang): String =
        "✅ $parameterName: $measuredValue° (perfect)"

    /** EN-only in source resources — [lang] ignored, kept for API symmetry. */
    fun feedbackParamExpected(parameterName: String, measuredValue: Int, idealValue: Int, lang: FeedbackLang): String =
        "⚠️ $parameterName: $measuredValue° (expected $idealValue°)"

    // ---- improvement tips (untranslated in UA -> always EN) -------------------

    fun improvementTip(mostCommonError: String?, lang: FeedbackLang): String? {
        if (mostCommonError == null) return null
        return when {
            mostCommonError.contains("wrist", ignoreCase = true) ->
                "💡 Tip: Imagine holding a pencil - wrist should be an extension of forearm"
            mostCommonError.contains("rotation", ignoreCase = true) ->
                "💡 Tip: Turn waist and shoulders as one - imagine winding a spring"
            mostCommonError.contains("contact", ignoreCase = true) ->
                "💡 Tip: Optimal contact point - waist height, slightly ahead of body"
            mostCommonError.contains("follow", ignoreCase = true) ->
                "💡 Tip: Complete stroke - racket should finish near opposite shoulder"
            else -> null
        }
    }

    /**
     * Fallback prettifier for an unresolved key, matching the old
     * `FeedbackGenerator.resolveString` miss path bit-for-bit:
     * strip `error_`/`rec_` prefixes, replace underscores with spaces, capitalize
     * the first character.
     */
    fun prettifyUnknownKey(key: String): String =
        key.replace("error_", "").replace("rec_", "").replace("_", " ").replaceFirstChar { it.uppercase() }
}
