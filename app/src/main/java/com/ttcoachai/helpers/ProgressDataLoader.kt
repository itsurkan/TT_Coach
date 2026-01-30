package com.ttcoachai.helpers

import com.ttcoachai.managers.CloudSyncManager
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.models.UserProgress

/**
 * Helper class to load and aggregate progress data from cloud.
 * Separates data loading logic from the ProgressFragment UI.
 */
class ProgressDataLoader(private val cloudSyncManager: CloudSyncManager) {

    /**
     * Loads all progress data from cloud.
     * Returns null if user is not authenticated.
     */
    suspend fun loadProgressData(): ProgressData? {
        if (!cloudSyncManager.isAuthenticated) {
            return null
        }
        
        return try {
            val sessions = cloudSyncManager.getRecentSessions(50)
            val progress = cloudSyncManager.getUserProgress()
            
            if (sessions.isEmpty() && progress == null) {
                return null
            }
            
            ProgressData(
                weeklyChartData = calculateWeeklyChartData(sessions),
                skillsData = calculateSkillsData(sessions),
                milestonesData = calculateMilestonesData(progress),
                userProgress = progress
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load cloud data", e)
            null
        }
    }
    
    private fun calculateWeeklyChartData(sessions: List<TrainingSession>): WeeklyChartData {
        val calendar = java.util.Calendar.getInstance()
        val dailyMinutes = mutableMapOf<Int, Float>()
        val dailyAccuracy = mutableMapOf<Int, MutableList<Float>>()
        
        // Initialize all days to 0
        for (i in 0..6) {
            dailyMinutes[i] = 0f
            dailyAccuracy[i] = mutableListOf()
        }
        
        // Get start of current week
        calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        val weekStart = calendar.timeInMillis
        
        sessions.filter { it.startTime >= weekStart }.forEach { session ->
            calendar.timeInMillis = session.startTime
            val dayOfWeek = (calendar.get(java.util.Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek + 7) % 7
            
            dailyMinutes[dayOfWeek] = (dailyMinutes[dayOfWeek] ?: 0f) + (session.durationSeconds / 60f)
            dailyAccuracy[dayOfWeek]?.add(session.accuracy * 100)
        }
        
        val trainingMinutes = (0..6).map { dailyMinutes[it] ?: 0f }
        val accuracyPercentages = (0..6).map { day ->
            dailyAccuracy[day]?.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
        }
        
        return WeeklyChartData(trainingMinutes, accuracyPercentages)
    }
    
    private fun calculateSkillsData(sessions: List<TrainingSession>): Map<String, Int> {
        return sessions.groupBy { it.exerciseId }
            .mapValues { (_, sessionList) ->
                sessionList.map { it.accuracy * 100 }.average().toInt()
            }
    }
    
    private fun calculateMilestonesData(progress: UserProgress?): List<MilestoneData> {
        val milestones = mutableListOf<MilestoneData>()
        
        progress?.let {
            if (it.totalStrokes >= 100) {
                milestones.add(MilestoneData(MilestoneType.HITS_100, true, null))
            }
            if (it.currentStreak >= 7) {
                milestones.add(MilestoneData(MilestoneType.STREAK_DAYS, true, it.currentStreak))
            }
            if (it.averageAccuracy >= 0.9f) {
                milestones.add(MilestoneData(MilestoneType.MASTER_SERVER, true, null))
            }
        }
        
        // Add placeholder if no milestones yet
        if (milestones.isEmpty()) {
            milestones.add(MilestoneData(MilestoneType.HITS_100, false, null))
        }
        
        return milestones
    }
    
    companion object {
        private const val TAG = "ProgressDataLoader"
    }
}

/**
 * Container for all progress data returned by the loader.
 */
data class ProgressData(
    val weeklyChartData: WeeklyChartData,
    val skillsData: Map<String, Int>,  // exerciseId -> accuracy percentage
    val milestonesData: List<MilestoneData>,
    val userProgress: UserProgress?
)

/**
 * Weekly chart data for training time and accuracy.
 */
data class WeeklyChartData(
    val trainingMinutes: List<Float>,  // 7 days of training minutes
    val accuracyPercentages: List<Float>  // 7 days of accuracy percentages
)

/**
 * Milestone achievement data.
 */
data class MilestoneData(
    val type: MilestoneType,
    val achieved: Boolean,
    val value: Int?  // For streaks, the actual streak count
)

enum class MilestoneType {
    HITS_100,
    STREAK_DAYS,
    MASTER_SERVER
}
