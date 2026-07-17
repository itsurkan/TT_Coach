package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.FeedbackItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionAnalyticsBuilderTest {

    private fun result(ts: Long, score: Float) = AnalysisResult(timestamp = ts, overallScore = score)
    private fun fb(type: CorrectionType) = FeedbackItem(message = "m", type = type)

    @Test
    fun emptyResults_yieldEmptyTimelineZeroCounts() {
        val a = SessionAnalyticsBuilder.build("s1", emptyList(), emptyList())
        assertEquals("s1", a.sessionId)
        assertTrue(a.accuracyTimeline.isEmpty())
        assertEquals(0f, a.peakAccuracy)
        assertEquals(0, a.peakBucketIndex)
        assertEquals(0, a.cleanCount)
        assertEquals(0, a.errorCount)
        assertTrue(a.focusAreas.isEmpty())
        assertTrue(a.summaryText.isNotBlank())
    }

    @Test
    fun cleanAndErrorCounts_useEightyThreshold() {
        val results = listOf(
            result(1, 90f), result(2, 80f), result(3, 79.9f), result(4, 50f)
        )
        val a = SessionAnalyticsBuilder.build("s", results, emptyList())
        assertEquals(2, a.cleanCount)
        assertEquals(2, a.errorCount)
    }

    @Test
    fun timeline_bucketsCappedAtTwelve_peakIsMaxBucket() {
        // 24 results sorted by ts: first 12 score 100, last 12 score 0 -> bucket means 100/0
        val results = (1..24).map { i -> result(i.toLong(), if (i <= 12) 100f else 0f) }
        val a = SessionAnalyticsBuilder.build("s", results, emptyList())
        assertEquals(12, a.accuracyTimeline.size)
        assertEquals(100f, a.accuracyTimeline.first())
        assertEquals(0f, a.accuracyTimeline.last())
        assertEquals(100f, a.peakAccuracy)
        assertEquals(0, a.peakBucketIndex)
    }

    @Test
    fun timeline_bucketValue_isMeanOverallScore_notCleanShare() {
        // 13 results over the 12-bucket cap: the last bucket gets the extra result (2 total:
        // scores 40, 60 -- mean 50), the rest get 1 each. Neither 40 nor 60 is "clean" (>=80),
        // so the old clean-share semantics would have produced 0 for the last bucket, not 50.
        val results = (1..11).map { i -> result(i.toLong(), 100f) } +
            listOf(result(12, 40f), result(13, 60f))
        val a = SessionAnalyticsBuilder.build("s", results, emptyList())
        assertEquals(12, a.accuracyTimeline.size)
        assertEquals(50f, a.accuracyTimeline.last())
    }

    @Test
    fun timeline_sortsByTimestampBeforeBucketing() {
        // out-of-order input; earliest ts (score 100) must land in bucket 0
        val results = listOf(result(30, 0f), result(10, 100f), result(20, 0f))
        val a = SessionAnalyticsBuilder.build("s", results, emptyList())
        assertEquals(3, a.accuracyTimeline.size)
        assertEquals(100f, a.accuracyTimeline.first())
    }

    @Test
    fun focusAreas_dropGeneral_rankDescByCount() {
        val feedback = listOf(
            fb(CorrectionType.WRIST), fb(CorrectionType.WRIST), fb(CorrectionType.WRIST),
            fb(CorrectionType.BODY_ROTATION), fb(CorrectionType.BODY_ROTATION),
            fb(CorrectionType.ELBOW_POSITION),
            fb(CorrectionType.GENERAL), fb(CorrectionType.GENERAL)
        )
        val a = SessionAnalyticsBuilder.build("s", listOf(result(1, 50f)), feedback)
        assertEquals(3, a.focusAreas.size)
        assertEquals(CorrectionType.WRIST, a.focusAreas[0].type)
        assertEquals(3, a.focusAreas[0].count)
        assertEquals(CorrectionType.BODY_ROTATION, a.focusAreas[1].type)
        assertEquals(CorrectionType.ELBOW_POSITION, a.focusAreas[2].type)
        assertTrue(a.focusAreas.none { it.type == CorrectionType.GENERAL })
    }

    @Test
    fun summaryText_mentionsPeakPercent() {
        val results = listOf(result(1, 100f), result(2, 100f))
        val a = SessionAnalyticsBuilder.build("s", results, emptyList())
        assertTrue(a.summaryText.contains("100%"))
    }

    @Test
    fun displayNameKey_mapsAllTypes() {
        assertEquals("focus_wrist", SessionAnalyticsBuilder.displayNameKey(CorrectionType.WRIST))
        assertEquals("focus_body_rotation", SessionAnalyticsBuilder.displayNameKey(CorrectionType.BODY_ROTATION))
        assertEquals("focus_knee_bend", SessionAnalyticsBuilder.displayNameKey(CorrectionType.KNEE_BEND))
        assertEquals("focus_general", SessionAnalyticsBuilder.displayNameKey(CorrectionType.GENERAL))
    }
}
