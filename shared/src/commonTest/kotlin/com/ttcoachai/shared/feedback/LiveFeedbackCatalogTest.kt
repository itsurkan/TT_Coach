package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.drill.FeedbackLang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveFeedbackCatalogTest {

    @Test
    fun resolveShortEnglishMatchesStringsXml() {
        assertEquals("Straighten wrist", LiveFeedbackCatalog.resolve("error_wrist_bent", short = true, lang = FeedbackLang.EN))
        assertEquals("More rotation", LiveFeedbackCatalog.resolve("error_low_rotation", short = true, lang = FeedbackLang.EN))
        assertEquals("Wrist straight", LiveFeedbackCatalog.resolve("rec_straighten_wrist", short = true, lang = FeedbackLang.EN))
    }

    @Test
    fun resolveFullEnglishMatchesStringsXml() {
        assertEquals("Wrist bent - straighten it", LiveFeedbackCatalog.resolve("error_wrist_bent", short = false, lang = FeedbackLang.EN))
        assertEquals(
            "Insufficient body rotation - turn more",
            LiveFeedbackCatalog.resolve("error_low_rotation", short = false, lang = FeedbackLang.EN)
        )
        assertEquals(
            "Don't press your elbow too tightly",
            LiveFeedbackCatalog.resolve("rec_move_elbow_away", short = false, lang = FeedbackLang.EN)
        )
    }

    @Test
    fun resolveShortUkrainianMatchesValuesUk() {
        assertEquals("Випряміть зап’ястя", LiveFeedbackCatalog.resolve("error_wrist_bent", short = true, lang = FeedbackLang.UA))
        assertEquals("Ротація корпусу", LiveFeedbackCatalog.resolve("rec_rotate_more", short = true, lang = FeedbackLang.UA))
    }

    @Test
    fun resolveFullUkrainianMatchesValuesUk() {
        assertEquals(
            "Контакт надто високо - опустіть точку удару",
            LiveFeedbackCatalog.resolve("error_high_contact", short = false, lang = FeedbackLang.UA)
        )
        assertEquals(
            "Тримайте зап’ястя рівно під час удару",
            LiveFeedbackCatalog.resolve("rec_straighten_wrist", short = false, lang = FeedbackLang.UA)
        )
    }

    @Test
    fun resolveReturnsNullForUnknownKey() {
        assertNull(LiveFeedbackCatalog.resolve("not_a_real_key", short = true, lang = FeedbackLang.EN))
        assertNull(LiveFeedbackCatalog.resolve("", short = true, lang = FeedbackLang.EN))
    }

    @Test
    fun positiveFeedbackPoolMatchesStringsXmlAndIsEnOnlyEvenForUkrainian() {
        val expected = listOf(
            "✅ Excellent! Perfect technique!",
            "✅ Great! Keep it up!",
            "✅ Flawless! Continue!",
            "✅ Perfect! Well done!",
            "✅ Super stroke! Very good!"
        )
        assertEquals(expected, LiveFeedbackCatalog.positiveFeedback(FeedbackLang.EN))
        // values-uk/strings.xml has no positive_feedback_* overrides -> Android falls back to EN.
        assertEquals(expected, LiveFeedbackCatalog.positiveFeedback(FeedbackLang.UA))
    }

    @Test
    fun motivationalMilestonesMatchBothLanguages() {
        assertEquals("🔥 5 good strokes in a row! Keep going!", LiveFeedbackCatalog.motivational(5, FeedbackLang.EN))
        assertEquals("🔥 5 гарних ударів підряд! Продовжуйте!", LiveFeedbackCatalog.motivational(5, FeedbackLang.UA))
        assertEquals("🎯 10 excellent strokes! You're on fire!", LiveFeedbackCatalog.motivational(10, FeedbackLang.EN))
        assertEquals("⭐ 20 strokes in a row! Incredible!", LiveFeedbackCatalog.motivational(20, FeedbackLang.EN))
        assertEquals("🏆 50 strokes! You're a master!", LiveFeedbackCatalog.motivational(50, FeedbackLang.EN))
        assertEquals(null, LiveFeedbackCatalog.motivational(7, FeedbackLang.EN))
    }

    @Test
    fun sessionSummaryPiecesMatchBothLanguages() {
        assertEquals("📊 Training Summary\n\n", LiveFeedbackCatalog.sessionSummaryTitle(FeedbackLang.EN))
        assertEquals("📊 Підсумок тренування\n\n", LiveFeedbackCatalog.sessionSummaryTitle(FeedbackLang.UA))
        assertEquals("Total strokes: 12\n", LiveFeedbackCatalog.sessionTotalStrokes(12, FeedbackLang.EN))
        assertEquals("Всього ударів: 12\n", LiveFeedbackCatalog.sessionTotalStrokes(12, FeedbackLang.UA))
        assertEquals("Successful: 8\n", LiveFeedbackCatalog.sessionSuccessful(8, FeedbackLang.EN))
        assertEquals("Точність: 66%\n", LiveFeedbackCatalog.sessionAccuracy(66, FeedbackLang.UA))
        assertEquals("Average score: 77%\n\n", LiveFeedbackCatalog.sessionAverageScore(77, FeedbackLang.EN))
    }

    @Test
    fun parameterAndScoreFeedbackAreEnOnlyEvenForUkrainian() {
        assertEquals("Score: 90%\n\n", LiveFeedbackCatalog.feedbackScoreFormat(90, FeedbackLang.EN))
        assertEquals("Score: 90%\n\n", LiveFeedbackCatalog.feedbackScoreFormat(90, FeedbackLang.UA))

        assertEquals("❓ Wrist: not detected", LiveFeedbackCatalog.feedbackParamNotDetected("Wrist", FeedbackLang.UA))
        assertEquals("✅ Wrist: 45° (perfect)", LiveFeedbackCatalog.feedbackParamPerfect("Wrist", 45, FeedbackLang.UA))
        assertEquals(
            "⚠️ Wrist: 45° (expected 60°)",
            LiveFeedbackCatalog.feedbackParamExpected("Wrist", 45, 60, FeedbackLang.UA)
        )
    }

    @Test
    fun improvementTipsMatchOriginalKeywordDispatchAndAreEnOnly() {
        assertEquals(
            "💡 Tip: Imagine holding a pencil - wrist should be an extension of forearm",
            LiveFeedbackCatalog.improvementTip("error_wrist_bent", FeedbackLang.UA)
        )
        assertEquals(
            "💡 Tip: Turn waist and shoulders as one - imagine winding a spring",
            LiveFeedbackCatalog.improvementTip("Low ROTATION detected", FeedbackLang.EN)
        )
        assertEquals(
            "💡 Tip: Optimal contact point - waist height, slightly ahead of body",
            LiveFeedbackCatalog.improvementTip("contact too high", FeedbackLang.EN)
        )
        assertEquals(
            "💡 Tip: Complete stroke - racket should finish near opposite shoulder",
            LiveFeedbackCatalog.improvementTip("no_follow_through", FeedbackLang.EN)
        )
        assertEquals(null, LiveFeedbackCatalog.improvementTip("some_other_error", FeedbackLang.EN))
        assertEquals(null, LiveFeedbackCatalog.improvementTip(null, FeedbackLang.EN))
    }

    @Test
    fun prettifyUnknownKeyMatchesOriginalFallback() {
        assertEquals("Wrist bent", LiveFeedbackCatalog.prettifyUnknownKey("error_wrist_bent"))
        assertEquals("Rotate more", LiveFeedbackCatalog.prettifyUnknownKey("rec_rotate_more"))
        assertEquals("Something weird", LiveFeedbackCatalog.prettifyUnknownKey("something_weird"))
    }
}
