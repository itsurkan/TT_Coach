package com.ttcoachai.shared.session

import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionStatsCalculatorTest {

    private fun result(score: Float) = AnalysisResult(overallScore = score)

    @Test
    fun totalHitsCountsAllResults() {
        assertEquals(0, SessionStatsCalculator.totalHits(emptyList()))
        assertEquals(3, SessionStatsCalculator.totalHits(listOf(result(10f), result(90f), result(50f))))
    }

    @Test
    fun successfulHitsCountsScoreAtLeast70() {
        val results = listOf(result(69f), result(70f), result(100f), result(0f))
        assertEquals(2, SessionStatsCalculator.successfulHits(results))
    }

    @Test
    fun goodStrokesCountsScoreAtLeast80() {
        val results = listOf(result(79f), result(80f), result(100f))
        assertEquals(2, SessionStatsCalculator.goodStrokesCount(results))
    }

    @Test
    fun averageScoreIsZeroForEmptyList() {
        assertEquals(0.0, SessionStatsCalculator.averageScore(emptyList()))
    }

    @Test
    fun averageScoreComputesMean() {
        val results = listOf(result(50f), result(100f))
        assertEquals(75.0, SessionStatsCalculator.averageScore(results))
    }

    @Test
    fun sortedFeedbackCountsOrdersDescending() {
        val counts = linkedMapOf(
            CorrectionType.WRIST to 2,
            CorrectionType.BODY_ROTATION to 5,
            CorrectionType.ELBOW_POSITION to 5,
            CorrectionType.CONTACT_HEIGHT to 1
        )
        val sorted = SessionStatsCalculator.sortedFeedbackCounts(counts)
        assertEquals(
            listOf(
                CorrectionType.BODY_ROTATION to 5,
                CorrectionType.ELBOW_POSITION to 5,
                CorrectionType.WRIST to 2,
                CorrectionType.CONTACT_HEIGHT to 1
            ),
            sorted
        )
    }

    @Test
    fun flaggedTotalSumsAllCounts() {
        val counts = mapOf(CorrectionType.WRIST to 2, CorrectionType.BODY_ROTATION to 3)
        assertEquals(5, SessionStatsCalculator.flaggedTotal(counts))
    }

    @Test
    fun flaggedTotalIsZeroForEmptyMap() {
        assertEquals(0, SessionStatsCalculator.flaggedTotal(emptyMap()))
    }

    @Test
    fun updateConsecutiveGoodStrokesIncrementsOnGoodScore() {
        assertEquals(4, SessionStatsCalculator.updateConsecutiveGoodStrokes(3, result(80f)))
        assertEquals(1, SessionStatsCalculator.updateConsecutiveGoodStrokes(0, result(100f)))
    }

    @Test
    fun updateConsecutiveGoodStrokesResetsOnBadScore() {
        assertEquals(0, SessionStatsCalculator.updateConsecutiveGoodStrokes(5, result(79f)))
        assertEquals(0, SessionStatsCalculator.updateConsecutiveGoodStrokes(5, result(0f)))
    }

    @Test
    fun formatSessionTimeFormatsMinutesAndSeconds() {
        assertEquals("00:00", SessionStatsCalculator.formatSessionTime(0L))
        assertEquals("00:05", SessionStatsCalculator.formatSessionTime(5_000L))
        assertEquals("01:00", SessionStatsCalculator.formatSessionTime(60_000L))
        assertEquals("02:03", SessionStatsCalculator.formatSessionTime(123_000L))
        assertEquals("10:09", SessionStatsCalculator.formatSessionTime(609_999L))
    }
}
