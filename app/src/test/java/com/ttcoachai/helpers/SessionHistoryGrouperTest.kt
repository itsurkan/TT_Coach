package com.ttcoachai.helpers

import com.ttcoachai.models.TrainingSession
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class SessionHistoryGrouperTest {

    private val zone = ZoneId.of("UTC")
    // Fixed 'now' = Thursday 2026-07-02 12:00:00 UTC
    private val now = 1_782_993_600_000L // 2026-07-02T12:00:00Z (verified: Thu, ISO week 27 2026)
    private val dayMs = 24L * 60 * 60 * 1000

    private fun session(id: String, startMs: Long, name: String, accuracy: Float, durationSec: Int = 600) =
        TrainingSession(
            id = id, userId = "u", exerciseId = "e", exerciseName = name,
            startTime = startMs, endTime = startMs + durationSec * 1000L,
            durationSeconds = durationSec, strokeCount = 100, correctStrokes = 80, accuracy = accuracy
        )

    @Test
    fun group_assignsThisWeek_lastWeek_earlier() {
        val sessions = listOf(
            session("a", now - 1 * dayMs, "Forehand", 0.85f),      // this week
            session("b", now - 9 * dayMs, "Forehand", 0.80f),      // last week
            session("c", now - 30 * dayMs, "Forehand", 0.70f)      // earlier
        )
        val rows = SessionHistoryGrouper.group(sessions, now, zone).associateBy { it.session.id }
        assertEquals(SessionHistoryGrouper.WeekGroup.THIS_WEEK, rows["a"]!!.group)
        assertEquals(SessionHistoryGrouper.WeekGroup.LAST_WEEK, rows["b"]!!.group)
        assertEquals(SessionHistoryGrouper.WeekGroup.EARLIER, rows["c"]!!.group)
    }

    @Test
    fun trend_isSignOf_thisMinusPrevSameExercise() {
        // newest-first ordering; prev same-exercise session is the older one
        val sessions = listOf(
            session("new", now - 1 * dayMs, "Forehand", 0.90f),
            session("old", now - 3 * dayMs, "Forehand", 0.80f)
        )
        val rows = SessionHistoryGrouper.group(sessions, now, zone).associateBy { it.session.id }
        assertEquals(SessionHistoryGrouper.Trend.UP, rows["new"]!!.trend)
        assertEquals(10, rows["new"]!!.trendDelta)
        // oldest same-exercise session has no predecessor -> FLAT, delta 0
        assertEquals(SessionHistoryGrouper.Trend.FLAT, rows["old"]!!.trend)
        assertEquals(0, rows["old"]!!.trendDelta)
    }

    @Test
    fun trend_downWhenAccuracyDrops() {
        val sessions = listOf(
            session("new", now - 1 * dayMs, "Backhand", 0.70f),
            session("old", now - 3 * dayMs, "Backhand", 0.85f)
        )
        val rows = SessionHistoryGrouper.group(sessions, now, zone).associateBy { it.session.id }
        assertEquals(SessionHistoryGrouper.Trend.DOWN, rows["new"]!!.trend)
        assertEquals(15, rows["new"]!!.trendDelta)
    }

    @Test
    fun kpi_last30Days_countsAveragesAndHours() {
        val sessions = listOf(
            session("a", now - 1 * dayMs, "Forehand", 0.80f, durationSec = 1800),  // 0.5h
            session("b", now - 5 * dayMs, "Forehand", 0.90f, durationSec = 1800),  // 0.5h
            session("c", now - 40 * dayMs, "Forehand", 1.00f, durationSec = 3600)  // excluded (>30d)
        )
        val kpi = SessionHistoryGrouper.last30DaysKpi(sessions, now)
        assertEquals(2, kpi.sessionCount)
        assertEquals(85, kpi.avgAccuracyPercent)
        assertEquals(1.0f, kpi.totalHours, 0.001f)
    }
}
