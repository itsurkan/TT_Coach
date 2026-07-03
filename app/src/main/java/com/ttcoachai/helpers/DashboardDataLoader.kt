package com.ttcoachai.helpers

import com.google.firebase.auth.FirebaseAuth
import com.ttcoachai.managers.CloudSyncManager
import com.ttcoachai.models.TrainingSession
import java.util.Calendar

/**
 * Helper class to load dashboard data from cloud.
 * Provides last-session summary, this-week aggregation and streak/goal progress
 * for the gold-dark Home/Dashboard screen.
 */
class DashboardDataLoader(private val cloudSyncManager: CloudSyncManager) {

    /**
     * Loads dashboard data from cloud.
     * Returns null if user is not authenticated.
     */
    suspend fun loadDashboardData(): DashboardData? {
        if (!cloudSyncManager.isAuthenticated) {
            return null
        }

        return try {
            val sessions = cloudSyncManager.getRecentSessions(100)
            val progress = cloudSyncManager.getUserProgress()

            val user = FirebaseAuth.getInstance().currentUser
            val displayName = user?.displayName
            val greetingName = firstName(displayName)
            val avatarInitial = initial(displayName)
            val avatarPhotoUrl = user?.photoUrl?.toString()

            val lastSession = buildLastSession(sessions)
            val week = buildWeek(sessions)

            val weeklyGoal = 7
            val weeklyDone = progress?.weeklySessionsCount ?: 0
            val weeklyProgressPct = if (weeklyGoal > 0) {
                ((weeklyDone * 100) / weeklyGoal).coerceIn(0, 100)
            } else 0

            DashboardData(
                greetingName = greetingName,
                avatarInitial = avatarInitial,
                avatarPhotoUrl = avatarPhotoUrl,
                lastSession = lastSession,
                weekBars = week.bars,
                todayDowIndex = week.todayDowIndex,
                streakDays = progress?.currentStreak ?: 0,
                weeklyDone = weeklyDone,
                weeklyGoal = weeklyGoal,
                weeklyProgressPct = weeklyProgressPct
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load dashboard data", e)
            null
        }
    }

    /** First word of the display name, or null if unavailable. Fallback handled in UI. */
    private fun firstName(displayName: String?): String? {
        val trimmed = displayName?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return trimmed.split(" ").firstOrNull { it.isNotBlank() } ?: trimmed
    }

    private fun initial(displayName: String?): String {
        val trimmed = displayName?.trim().orEmpty()
        val c = trimmed.firstOrNull { it.isLetterOrDigit() }
        return c?.uppercaseChar()?.toString() ?: "U"
    }

    private fun buildLastSession(sessions: List<TrainingSession>): LastSessionUi? {
        val last = sessions.maxByOrNull { it.startTime } ?: return null
        val strokes = last.strokeCount
        val accuracyPct = (last.accuracy * 100).toInt().coerceIn(0, 100)
        val consistencyPct = if (last.strokeCount > 0) {
            (last.correctStrokes * 100 / last.strokeCount).coerceIn(0, 100)
        } else 0

        return LastSessionUi(
            sessionId = last.id,
            exerciseName = last.exerciseName,
            startTime = last.startTime,
            strokes = strokes,
            accuracyPct = accuracyPct,
            consistencyPct = consistencyPct
        )
    }

    private data class WeekResult(val bars: List<Float>, val todayDowIndex: Int)

    /**
     * Aggregates this week's sessions into 7 normalized bars (Mon..Sun), scaled by
     * the busiest day's stroke count. Also returns today's day-of-week index (0=Mon).
     */
    private fun buildWeek(sessions: List<TrainingSession>): WeekResult {
        val cal = Calendar.getInstance()
        val todayDow = mondayIndex(cal.get(Calendar.DAY_OF_WEEK))

        // Monday 00:00 of the current week.
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -todayDow)
        val weekStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 7)
        val weekEnd = cal.timeInMillis

        val perDay = IntArray(7)
        sessions.forEach { s ->
            if (s.startTime in weekStart until weekEnd) {
                val c = Calendar.getInstance().apply { timeInMillis = s.startTime }
                val idx = mondayIndex(c.get(Calendar.DAY_OF_WEEK))
                perDay[idx] += s.strokeCount
            }
        }

        val max = perDay.maxOrNull() ?: 0
        val bars = if (max <= 0) {
            List(7) { 0f }
        } else {
            perDay.map { it.toFloat() / max.toFloat() }
        }

        return WeekResult(bars, todayDow)
    }

    /** Convert Calendar.DAY_OF_WEEK (Sun=1..Sat=7) to Monday-first index (Mon=0..Sun=6). */
    private fun mondayIndex(dayOfWeek: Int): Int = (dayOfWeek + 5) % 7

    companion object {
        private const val TAG = "DashboardDataLoader"
    }
}

/**
 * Summary of the most recent training session for the Last Session card.
 */
data class LastSessionUi(
    val sessionId: String,
    val exerciseName: String,
    val startTime: Long,
    val strokes: Int,
    val accuracyPct: Int,
    val consistencyPct: Int
)

/**
 * Container for dashboard data (gold-dark Home screen).
 */
data class DashboardData(
    val greetingName: String?,
    val avatarInitial: String,
    val avatarPhotoUrl: String?,
    val lastSession: LastSessionUi?,
    val weekBars: List<Float>,
    val todayDowIndex: Int,
    val streakDays: Int,
    val weeklyDone: Int,
    val weeklyGoal: Int,
    val weeklyProgressPct: Int
)
