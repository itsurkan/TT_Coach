package com.ttcoachai.db

import com.ttcoachai.managers.RepPoseCapture
import com.ttcoachai.shared.analysis.FocusArea
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Keypoint2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAnalyticsConvertersTest {

    @Test
    fun floatList_roundTrips() {
        val list = listOf(0f, 33.3f, 100f)
        val json = SessionAnalyticsConverters.floatListToJson(list)
        val back = SessionAnalyticsConverters.jsonToFloatList(json)
        assertEquals(3, back.size)
        assertEquals(0f, back[0], 0.001f)
        assertEquals(33.3f, back[1], 0.001f)
        assertEquals(100f, back[2], 0.001f)
    }

    @Test
    fun emptyFloatJson_returnsEmpty() {
        assertTrue(SessionAnalyticsConverters.jsonToFloatList("").isEmpty())
        assertTrue(SessionAnalyticsConverters.jsonToFloatList("[]").isEmpty())
    }

    @Test
    fun focusAreas_roundTrip_preservesTypeAndCount() {
        val list = listOf(
            FocusArea(CorrectionType.WRIST, 5),
            FocusArea(CorrectionType.ELBOW_POSITION, 2)
        )
        val json = SessionAnalyticsConverters.focusAreasToJson(list)
        val back = SessionAnalyticsConverters.jsonToFocusAreas(json)
        assertEquals(2, back.size)
        assertEquals(CorrectionType.WRIST, back[0].type)
        assertEquals(5, back[0].count)
        assertEquals(CorrectionType.ELBOW_POSITION, back[1].type)
        assertEquals(2, back[1].count)
    }

    @Test
    fun emptyFocusJson_returnsEmpty() {
        assertTrue(SessionAnalyticsConverters.jsonToFocusAreas("").isEmpty())
    }

    @Test
    fun keypoints_roundTrip_preservesFields() {
        val keypoints = listOf(
            Keypoint2D(x = 0.1f, y = 0.2f, score = 0.9f),
            Keypoint2D(x = 0.55f, y = 0.42f, score = 0.31f),
        )
        val json = SessionAnalyticsConverters.keypointsToJson(keypoints)
        val back = SessionAnalyticsConverters.jsonToKeypoints(json)
        assertEquals(2, back.size)
        assertEquals(0.1f, back[0].x, 0.0001f)
        assertEquals(0.2f, back[0].y, 0.0001f)
        assertEquals(0.9f, back[0].score, 0.0001f)
        assertEquals(0.55f, back[1].x, 0.0001f)
        assertEquals(0.42f, back[1].y, 0.0001f)
        assertEquals(0.31f, back[1].score, 0.0001f)
    }

    @Test
    fun nullOrBlankKeypointsJson_returnsEmpty() {
        assertTrue(SessionAnalyticsConverters.jsonToKeypoints(null).isEmpty())
        assertTrue(SessionAnalyticsConverters.jsonToKeypoints("").isEmpty())
    }

    @Test
    fun repCaptures_roundTrip_preservesAllFieldsForTenCaptures() {
        val captures = (1..10).map { i ->
            RepPoseCapture(
                atMs = i.toLong() * 100,
                start = listOf(Keypoint2D(x = 0.1f * i, y = 0.2f * i, score = 0.9f)),
                end = listOf(Keypoint2D(x = 0.3f * i, y = 0.4f * i, score = 0.8f)),
                flaggedTypes = if (i % 2 == 0) setOf(CorrectionType.ELBOW_BEND, CorrectionType.POSTURE) else emptySet(),
            )
        }
        val json = SessionAnalyticsConverters.repCapturesToJson(captures)
        val back = SessionAnalyticsConverters.jsonToRepCaptures(json)

        assertEquals(10, back.size)
        for (i in captures.indices) {
            val expected = captures[i]
            val actual = back[i]
            assertEquals(expected.atMs, actual.atMs)
            assertEquals(expected.start.size, actual.start.size)
            assertEquals(expected.start[0].x, actual.start[0].x, 0.0001f)
            assertEquals(expected.start[0].y, actual.start[0].y, 0.0001f)
            assertEquals(expected.start[0].score, actual.start[0].score, 0.0001f)
            assertEquals(expected.end[0].x, actual.end[0].x, 0.0001f)
            assertEquals(expected.end[0].y, actual.end[0].y, 0.0001f)
            assertEquals(expected.end[0].score, actual.end[0].score, 0.0001f)
            assertEquals(expected.flaggedTypes, actual.flaggedTypes)
        }
    }

    @Test
    fun repCaptures_moreThanTen_keepsOnlyLastTen() {
        val captures = (1..15).map { i ->
            RepPoseCapture(atMs = i.toLong(), start = listOf(Keypoint2D(0f, 0f, 1f)), end = listOf(Keypoint2D(0f, 0f, 1f)))
        }
        val back = SessionAnalyticsConverters.jsonToRepCaptures(SessionAnalyticsConverters.repCapturesToJson(captures))
        assertEquals(10, back.size)
        assertEquals(6L, back.first().atMs)
        assertEquals(15L, back.last().atMs)
    }

    @Test
    fun nullOrBlankRepCapturesJson_returnsEmpty() {
        assertTrue(SessionAnalyticsConverters.jsonToRepCaptures(null).isEmpty())
        assertTrue(SessionAnalyticsConverters.jsonToRepCaptures("").isEmpty())
    }
}
