package com.ttcoachai.helpers

import com.ttcoachai.models.TrainingSession
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure presentation helpers for the Session History screen: bucket sessions by week
 * (This week / Last week / Earlier), compute each row's trend vs the previous session
 * of the same exercise, and compute the last-30-days KPI strip. `now` is injected for
 * deterministic tests. java.time is allowed here (app-layer presentation, not drill logic).
 */
object SessionHistoryGrouper {

    enum class WeekGroup { THIS_WEEK, LAST_WEEK, EARLIER }
    enum class Trend { UP, DOWN, FLAT }

    data class SessionRow(
        val session: TrainingSession,
        val group: WeekGroup,
        val trend: Trend,
        val trendDelta: Int,
    )

    data class Kpi(val sessionCount: Int, val avgAccuracyPercent: Int, val totalHours: Float)

    fun group(sessions: List<TrainingSession>, nowMs: Long, zone: ZoneId): List<SessionRow> {
        val sorted = sessions.sortedByDescending { it.startTime }
        val weekFields = WeekFields.ISO
        val nowDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val nowWeek = nowDate.get(weekFields.weekOfWeekBasedYear())
        val nowYear = nowDate.get(weekFields.weekBasedYear())

        // previous same-exercise accuracy (older neighbour), walking oldest->newest
        val trendByIndex = HashMap<String, Pair<Trend, Int>>()
        val lastAccByExercise = HashMap<String, Float>()
        for (s in sorted.reversed()) {
            val prev = lastAccByExercise[s.exerciseName]
            if (prev == null) {
                trendByIndex[s.id] = Trend.FLAT to 0
            } else {
                val delta = ((s.accuracy - prev) * 100).roundToInt()
                val trend = when {
                    delta > 0 -> Trend.UP
                    delta < 0 -> Trend.DOWN
                    else -> Trend.FLAT
                }
                trendByIndex[s.id] = trend to abs(delta)
            }
            lastAccByExercise[s.exerciseName] = s.accuracy
        }

        return sorted.map { s ->
            val date = Instant.ofEpochMilli(s.startTime).atZone(zone).toLocalDate()
            val week = date.get(weekFields.weekOfWeekBasedYear())
            val year = date.get(weekFields.weekBasedYear())
            val group = when {
                year == nowYear && week == nowWeek -> WeekGroup.THIS_WEEK
                year == nowYear && week == nowWeek - 1 -> WeekGroup.LAST_WEEK
                else -> WeekGroup.EARLIER
            }
            val (trend, delta) = trendByIndex[s.id] ?: (Trend.FLAT to 0)
            SessionRow(s, group, trend, delta)
        }
    }

    fun last30DaysKpi(sessions: List<TrainingSession>, nowMs: Long): Kpi {
        val cutoff = nowMs - 30L * 24 * 60 * 60 * 1000
        val recent = sessions.filter { it.startTime >= cutoff }
        if (recent.isEmpty()) return Kpi(0, 0, 0f)
        val avg = (recent.map { it.accuracy }.average() * 100).roundToInt()
        val hours = recent.sumOf { it.durationSeconds.toLong() } / 3600f
        return Kpi(recent.size, avg, hours)
    }
}
