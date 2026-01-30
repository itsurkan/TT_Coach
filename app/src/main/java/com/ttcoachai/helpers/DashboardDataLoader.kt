package com.ttcoachai.helpers

import com.ttcoachai.managers.CloudSyncManager
import com.ttcoachai.models.TrainingSession
import java.util.Calendar

/**
 * Helper class to load dashboard data from cloud.
 * Provides today's statistics and weekly progress.
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
            
            val todayStats = calculateTodayStats(sessions)
            val weeklyStats = calculateWeeklyStats(sessions)
            val latestAchievement = getLatestAchievement(progress)
            
            DashboardData(
                todayMinutes = todayStats.minutes,
                todayAccuracy = todayStats.accuracy,
                todayStrokes = todayStats.strokes,
                improvementVsYesterday = calculateImprovement(sessions),
                weeklyDaysTrained = weeklyStats.daysTrained,
                weeklyGoal = 7, // Default goal
                latestAchievement = latestAchievement
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load dashboard data", e)
            null
        }
    }
    
    private fun calculateTodayStats(sessions: List<TrainingSession>): TodayStats {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val todayStart = calendar.timeInMillis
        
        val todaySessions = sessions.filter { it.startTime >= todayStart }
        
        if (todaySessions.isEmpty()) {
            return TodayStats(0, 0, 0)
        }
        
        val totalMinutes = todaySessions.sumOf { it.durationSeconds } / 60
        val avgAccuracy = (todaySessions.map { it.accuracy * 100 }.average()).toInt()
        val totalStrokes = todaySessions.sumOf { it.strokeCount }
        
        return TodayStats(totalMinutes, avgAccuracy, totalStrokes)
    }
    
    private fun calculateWeeklyStats(sessions: List<TrainingSession>): WeeklyStats {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val weekStart = calendar.timeInMillis
        
        val weekSessions = sessions.filter { it.startTime >= weekStart }
        
        // Count unique days with training
        val uniqueDays = weekSessions.map { session ->
            calendar.timeInMillis = session.startTime
            calendar.get(Calendar.DAY_OF_YEAR)
        }.toSet().size
        
        return WeeklyStats(uniqueDays)
    }
    
    private fun calculateImprovement(sessions: List<TrainingSession>): Int {
        val calendar = Calendar.getInstance()
        
        // Today's start
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val todayStart = calendar.timeInMillis
        
        // Yesterday's start
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = calendar.timeInMillis
        
        val todaySessions = sessions.filter { it.startTime >= todayStart }
        val yesterdaySessions = sessions.filter { 
            it.startTime >= yesterdayStart && it.startTime < todayStart 
        }
        
        if (yesterdaySessions.isEmpty() || todaySessions.isEmpty()) {
            return 0
        }
        
        val todayAccuracy = todaySessions.map { it.accuracy * 100 }.average()
        val yesterdayAccuracy = yesterdaySessions.map { it.accuracy * 100 }.average()
        
        return (todayAccuracy - yesterdayAccuracy).toInt()
    }
    
    private fun getLatestAchievement(progress: com.ttcoachai.models.UserProgress?): String? {
        progress ?: return null
        
        return when {
            progress.totalStrokes >= 1000 -> "1000 strokes milestone reached!"
            progress.totalStrokes >= 500 -> "500 strokes milestone reached!"
            progress.totalStrokes >= 100 -> "100 strokes milestone reached!"
            progress.currentStreak >= 7 -> "7-day training streak!"
            progress.averageAccuracy >= 0.9f -> "90% average accuracy achieved!"
            else -> null
        }
    }
    
    private data class TodayStats(val minutes: Int, val accuracy: Int, val strokes: Int)
    private data class WeeklyStats(val daysTrained: Int)
    
    companion object {
        private const val TAG = "DashboardDataLoader"
    }
}

/**
 * Container for dashboard data.
 */
data class DashboardData(
    val todayMinutes: Int,
    val todayAccuracy: Int,
    val todayStrokes: Int,
    val improvementVsYesterday: Int,
    val weeklyDaysTrained: Int,
    val weeklyGoal: Int,
    val latestAchievement: String?
)
