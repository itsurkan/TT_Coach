package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.FeedbackItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveFeedbackFormatterTest {

    @Test
    fun shortFeedbackReturnsPositiveWhenScoreAtOrAbove85() {
        val result = AnalysisResult(overallScore = 85f, feedbackItems = listOf(
            FeedbackItem(message = "error_wrist_bent", type = CorrectionType.WRIST, isPositive = false)
        ))
        val picked = LiveFeedbackFormatter.shortFeedback(result, FeedbackLang.EN) { 0 }
        assertTrue(picked in LiveFeedbackCatalog.POSITIVE_FEEDBACK)
    }

    @Test
    fun shortFeedbackReturnsFirstNegativeItemsRawMessageKeyBelow85() {
        // Bit-for-bit port of the original: FeedbackGenerator.generateShortFeedback
        // returns FeedbackItem.message UNRESOLVED (not run through short_/full lookup).
        val result = AnalysisResult(overallScore = 50f, feedbackItems = listOf(
            FeedbackItem(message = "error_low_rotation", type = CorrectionType.BODY_ROTATION, isPositive = false),
            FeedbackItem(message = "error_wrist_bent", type = CorrectionType.WRIST, isPositive = false)
        ))
        assertEquals("error_low_rotation", LiveFeedbackFormatter.shortFeedback(result, FeedbackLang.EN) { 0 })
    }

    @Test
    fun shortFeedbackFallsBackToPositiveWhenNoNegativeItemsBelow85() {
        val result = AnalysisResult(overallScore = 50f, feedbackItems = listOf(
            FeedbackItem(message = "positive_note", type = CorrectionType.GENERAL, isPositive = true)
        ))
        val picked = LiveFeedbackFormatter.shortFeedback(result, FeedbackLang.EN) { 2 }
        assertEquals(LiveFeedbackCatalog.POSITIVE_FEEDBACK[2], picked)
    }

    @Test
    fun shortFeedbackRawMessagePassthroughIsLangIndependent() {
        // lang only matters for the positive-pool branch; the raw-message
        // passthrough branch ignores it entirely, same as the original.
        val result = AnalysisResult(overallScore = 50f, feedbackItems = listOf(
            FeedbackItem(message = "error_high_contact", type = CorrectionType.CONTACT_HEIGHT, isPositive = false)
        ))
        assertEquals("error_high_contact", LiveFeedbackFormatter.shortFeedback(result, FeedbackLang.UA) { 0 })
    }

    @Test
    fun shortFeedbackPositivePoolUsesUkrainianWhenRequested() {
        val result = AnalysisResult(overallScore = 90f, feedbackItems = emptyList())
        val picked = LiveFeedbackFormatter.shortFeedback(result, FeedbackLang.UA) { 0 }
        assertTrue(picked in LiveFeedbackCatalog.positiveFeedback(FeedbackLang.UA))
    }

    @Test
    fun detailedFeedbackAssemblesScoreHeaderFilteredItemsAndRecommendations() {
        val result = AnalysisResult(
            overallScore = 42f,
            feedbackItems = listOf(
                FeedbackItem(message = "error_wrist_bent", type = CorrectionType.WRIST, isPositive = false),
                FeedbackItem(message = "error_low_rotation", type = CorrectionType.BODY_ROTATION, isPositive = false),
                FeedbackItem(message = "positive_note", type = CorrectionType.GENERAL, isPositive = true)
            ),
            recommendations = listOf("rec_straighten_wrist")
        )
        // BODY_ROTATION disabled -> only WRIST negative item + recommendation should appear.
        val text = LiveFeedbackFormatter.detailedFeedback(
            result,
            isCorrectionTypeEnabled = { it != CorrectionType.BODY_ROTATION },
            short = true,
            lang = FeedbackLang.EN
        )
        val expected = "Score: 42%\n\n\n• Straighten wrist\n• Wrist straight\n"
        assertEquals(expected, text)
    }

    @Test
    fun detailedFeedbackFullVersionUsesFullStrings() {
        val result = AnalysisResult(
            overallScore = 10f,
            feedbackItems = listOf(
                FeedbackItem(message = "error_wrist_bent", type = CorrectionType.WRIST, isPositive = false)
            )
        )
        val text = LiveFeedbackFormatter.detailedFeedback(
            result,
            isCorrectionTypeEnabled = { true },
            short = false,
            lang = FeedbackLang.EN
        )
        assertEquals("Score: 10%\n\n\n• Wrist bent - straighten it\n", text)
    }

    @Test
    fun detailedFeedbackOmitsNegativeBulletsWhenNoneEnabledButKeepsRecommendations() {
        val result = AnalysisResult(
            overallScore = 20f,
            feedbackItems = listOf(
                FeedbackItem(message = "error_wrist_bent", type = CorrectionType.WRIST, isPositive = false)
            ),
            recommendations = listOf("rec_rotate_more")
        )
        val text = LiveFeedbackFormatter.detailedFeedback(
            result,
            isCorrectionTypeEnabled = { false },
            short = true,
            lang = FeedbackLang.EN
        )
        assertEquals("Score: 20%\n\n\n• Body rotation\n", text)
    }

    @Test
    fun resolveKeyOrFallbackUsesPrettifierForUnknownKey() {
        assertEquals("Weird key", LiveFeedbackFormatter.resolveKeyOrFallback("weird_key", short = true, lang = FeedbackLang.EN))
        assertEquals("", LiveFeedbackFormatter.resolveKeyOrFallback("", short = true, lang = FeedbackLang.EN))
    }

    @Test
    fun parameterFeedbackNotDetectedPerfectAndExpected() {
        assertEquals(
            "❓ Elbow: not detected",
            LiveFeedbackFormatter.parameterFeedback("Elbow", null, 90f, isValid = false, lang = FeedbackLang.EN)
        )
        assertEquals(
            "✅ Elbow: 90° (perfect)",
            LiveFeedbackFormatter.parameterFeedback("Elbow", 90f, 90f, isValid = true, lang = FeedbackLang.EN)
        )
        assertEquals(
            "⚠️ Elbow: 70° (expected 90°)",
            LiveFeedbackFormatter.parameterFeedback("Elbow", 70f, 90f, isValid = false, lang = FeedbackLang.EN)
        )
    }

    @Test
    fun motivationalDelegatesToCatalog() {
        assertEquals("🔥 5 good strokes in a row! Keep going!", LiveFeedbackFormatter.motivational(5, FeedbackLang.EN))
        assertEquals(null, LiveFeedbackFormatter.motivational(6, FeedbackLang.EN))
    }

    @Test
    fun sessionSummaryAssemblesAllPiecesWithAccuracyComputation() {
        val text = LiveFeedbackFormatter.sessionSummary(
            totalStrokes = 10,
            successfulStrokes = 7,
            averageScore = 82f,
            lang = FeedbackLang.EN
        )
        val expected = "📊 Training Summary\n\n\n" +
            "Total strokes: 10\n\n" +
            "Successful: 7\n\n" +
            "Accuracy: 70%\n\n" +
            "Average score: 82%\n\n\n"
        assertEquals(expected, text)
    }

    @Test
    fun sessionSummaryHandlesZeroTotalStrokesWithoutDividingByZero() {
        val text = LiveFeedbackFormatter.sessionSummary(0, 0, 0f, FeedbackLang.EN)
        assertTrue(text.contains("Accuracy: 0%"))
    }

    @Test
    fun improvementTipDelegatesToCatalog() {
        assertEquals(
            "💡 Tip: Turn waist and shoulders as one - imagine winding a spring",
            LiveFeedbackFormatter.improvementTip("error_low_rotation", FeedbackLang.EN)
        )
        assertEquals(null, LiveFeedbackFormatter.improvementTip(null, FeedbackLang.EN))
    }
}
