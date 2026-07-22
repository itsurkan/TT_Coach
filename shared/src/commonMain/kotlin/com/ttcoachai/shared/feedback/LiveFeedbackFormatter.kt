package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType

/**
 * Pure port of `FeedbackGenerator`'s text-assembly logic (Android `app/`), so it
 * can run identically on Android, in JVM tests and on iOS. No Android APIs, no
 * randomness, no I/O — callers inject the bits that differ per platform
 * (an index picker instead of `java.util.Random`, a correction-type-enabled
 * predicate instead of reading `SettingsManager` directly).
 *
 * Behavior is bit-for-bit identical to the original: same string content
 * (via [LiveFeedbackCatalog]), same selection/filtering order, same fallback
 * prettifier for unresolved keys.
 */
object LiveFeedbackFormatter {

    /**
     * Mirrors `FeedbackGenerator.generateShortFeedback` bit-for-bit: positive
     * pool if the score is >= 85, else the first non-positive feedback item's
     * RAW `message` key (the original returns `item.message` unresolved — it
     * does not run it through the short_/full resource lookup — so we
     * preserve that behavior here rather than "fixing" it), else the positive
     * pool as a fallback.
     *
     * @param isCorrectionTypeEnabled same semantics as [detailedFeedback]'s predicate:
     *   negative items whose [CorrectionType] is disabled are skipped when picking the
     *   "first negative item"; defaults to allowing everything (legacy unfiltered
     *   behavior) for callers that don't care about per-type toggles.
     * @param pickIndex given the pool size, returns the index to use (caller
     *   supplies e.g. `Random::nextInt` on Android).
     */
    fun shortFeedback(
        result: AnalysisResult,
        lang: FeedbackLang,
        isCorrectionTypeEnabled: (CorrectionType) -> Boolean = { true },
        pickIndex: (Int) -> Int
    ): String {
        if (result.overallScore >= 85) return randomPositive(lang, pickIndex)
        val firstNegative = result.feedbackItems.firstOrNull { !it.isPositive && isCorrectionTypeEnabled(it.type) }
        return firstNegative?.message ?: randomPositive(lang, pickIndex)
    }

    private fun randomPositive(lang: FeedbackLang, pickIndex: (Int) -> Int): String {
        val pool = LiveFeedbackCatalog.positiveFeedback(lang)
        return pool[pickIndex(pool.size)]
    }

    /**
     * Mirrors `FeedbackGenerator.generateDetailedFeedback`: score header, then
     * bullet lines for every non-positive feedback item whose [CorrectionType]
     * is enabled, then bullet lines for every recommendation key. `short`
     * controls whether bullets resolve via the `short_*` or `*_full` family
     * (mirrors the `settingsManager.getFeedbackType() == 0` check).
     */
    fun detailedFeedback(
        result: AnalysisResult,
        isCorrectionTypeEnabled: (CorrectionType) -> Boolean,
        short: Boolean,
        lang: FeedbackLang
    ): String {
        val feedback = StringBuilder()
        feedback.append(LiveFeedbackCatalog.feedbackScoreFormat(result.overallScore.toInt(), lang)).append("\n")

        val filteredFeedbackItems = result.feedbackItems.filter { isCorrectionTypeEnabled(it.type) }

        if (filteredFeedbackItems.any { !it.isPositive }) {
            filteredFeedbackItems.filter { !it.isPositive }.forEach { item ->
                feedback.append("• ${resolveKeyOrFallback(item.message, short, lang)}\n")
            }
        }

        if (result.recommendations.isNotEmpty()) {
            result.recommendations.forEach { recKey ->
                feedback.append("• ${resolveKeyOrFallback(recKey, short, lang)}\n")
            }
        }

        return feedback.toString()
    }

    /** Mirrors `FeedbackGenerator.resolveString`: catalog lookup, else the key prettifier. */
    fun resolveKeyOrFallback(key: String, short: Boolean, lang: FeedbackLang): String {
        if (key.isEmpty()) return ""
        return LiveFeedbackCatalog.resolve(key, short, lang) ?: LiveFeedbackCatalog.prettifyUnknownKey(key)
    }

    /** Mirrors `FeedbackGenerator.generateParameterFeedback`. */
    fun parameterFeedback(
        parameterName: String,
        measuredValue: Float?,
        idealValue: Float,
        isValid: Boolean,
        lang: FeedbackLang
    ): String {
        if (measuredValue == null) return LiveFeedbackCatalog.feedbackParamNotDetected(parameterName, lang)
        return if (isValid) {
            LiveFeedbackCatalog.feedbackParamPerfect(parameterName, measuredValue.toInt(), lang)
        } else {
            LiveFeedbackCatalog.feedbackParamExpected(parameterName, measuredValue.toInt(), idealValue.toInt(), lang)
        }
    }

    /** Mirrors `FeedbackGenerator.generateMotivationalMessage`. */
    fun motivational(consecutiveGoodStrokes: Int, lang: FeedbackLang): String? =
        LiveFeedbackCatalog.motivational(consecutiveGoodStrokes, lang)

    /** Mirrors `FeedbackGenerator.generateSessionSummary`. */
    fun sessionSummary(totalStrokes: Int, successfulStrokes: Int, averageScore: Float, lang: FeedbackLang): String {
        val accuracy = if (totalStrokes > 0) (successfulStrokes * 100 / totalStrokes) else 0

        val summary = StringBuilder()
        summary.append(LiveFeedbackCatalog.sessionSummaryTitle(lang)).append("\n")
        summary.append(LiveFeedbackCatalog.sessionTotalStrokes(totalStrokes, lang)).append("\n")
        summary.append(LiveFeedbackCatalog.sessionSuccessful(successfulStrokes, lang)).append("\n")
        summary.append(LiveFeedbackCatalog.sessionAccuracy(accuracy, lang)).append("\n")
        summary.append(LiveFeedbackCatalog.sessionAverageScore(averageScore.toInt(), lang)).append("\n")

        return summary.toString()
    }

    /** Mirrors `FeedbackGenerator.generateImprovementTip` (the `exerciseId` param was unused in the original — dropped here). */
    fun improvementTip(mostCommonError: String?, lang: FeedbackLang): String? =
        LiveFeedbackCatalog.improvementTip(mostCommonError, lang)
}
