/*
 * AI Coach for Table Tennis
 * User Progress Model - Aggregated training statistics
 */

package com.ttcoachai.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

/**
 * Aggregated progress statistics for a user.
 * Updated after each training session.
 */
@Entity(tableName = "user_progress")
data class UserProgress(
    @PrimaryKey
    @DocumentId
    val userId: String = "",
    // Totals
    val totalSessions: Int = 0,
    val totalStrokes: Int = 0,
    val totalCorrectStrokes: Int = 0,
    val totalTrainingMinutes: Int = 0,
    // Averages
    val averageAccuracy: Float = 0f,
    val averageSessionDuration: Int = 0, // seconds
    // Streaks
    val currentStreak: Int = 0, // consecutive training days
    val longestStreak: Int = 0,
    val lastTrainingDate: Long = 0L,
    // Weekly/Monthly progress (JSON-encoded maps for simplicity)
    val weeklySessionsCount: Int = 0,
    val monthlySessionsCount: Int = 0,
    // Per-exercise stats (exerciseId -> count, stored as JSON string)
    val exerciseSessionCounts: String = "{}", // JSON map
    // Last updated
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    
    // Sync status for offline-first logic
    @get:Exclude
    val isSynced: Boolean = true
) {
    @get:Exclude
    val totalHours: Int get() = totalTrainingMinutes / 60
    /**
     * No-arg constructor required for Firestore deserialization
     */
    constructor() : this(userId = "")

    companion object {
        const val DOCUMENT_ID = "progress"

        /**
         * Calculate overall accuracy percentage
         */
        fun calculateAccuracy(correctStrokes: Int, totalStrokes: Int): Float {
            if (totalStrokes == 0) return 0f
            return correctStrokes.toFloat() / totalStrokes.toFloat()
        }

        /**
         * Helper to recalculate progress from a list of sessions.
         * Useful for fixing inconsistent streaks or totals.
         */
        fun fromSessions(userId: String, sessions: List<TrainingSession>): UserProgress {
            var progress = UserProgress(userId = userId)
            // Sort by start time ascending to build streak correctly
            val sortedSessions = sessions.sortedBy { it.startTime }
            for (session in sortedSessions) {
                progress = progress.withNewSession(session)
            }
            return progress
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "totalSessions" to totalSessions,
        "totalStrokes" to totalStrokes,
        "totalCorrectStrokes" to totalCorrectStrokes,
        "totalTrainingMinutes" to totalTrainingMinutes,
        "averageAccuracy" to averageAccuracy,
        "averageSessionDuration" to averageSessionDuration,
        "currentStreak" to currentStreak,
        "longestStreak" to longestStreak,
        "lastTrainingDate" to lastTrainingDate,
        "weeklySessionsCount" to weeklySessionsCount,
        "monthlySessionsCount" to monthlySessionsCount,
        "exerciseSessionCounts" to exerciseSessionCounts,
        "lastUpdatedAt" to lastUpdatedAt
    )

    /**
     * Create updated progress after completing a new session
     */
    fun withNewSession(session: TrainingSession): UserProgress {
        val newTotalStrokes = totalStrokes + session.strokeCount
        val newCorrectStrokes = totalCorrectStrokes + session.correctStrokes
        val newTotalSessions = totalSessions + 1
        val newTotalMinutes = totalTrainingMinutes + (session.durationSeconds / 60)

        // Calculate new average accuracy
        val newAvgAccuracy = calculateAccuracy(newCorrectStrokes, newTotalStrokes)

        // Calculate new average session duration
        val newAvgDuration = if (newTotalSessions > 0) {
            (totalTrainingMinutes * 60 + session.durationSeconds) / newTotalSessions
        } else {
            session.durationSeconds
        }

        // Update streak based on the SESSION time, not sync time
        val sessionCalendar = java.util.Calendar.getInstance().apply {
            timeInMillis = session.startTime
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val sessionDateMidnight = sessionCalendar.timeInMillis

        val lastDateMidnight = if (lastTrainingDate > 0) {
            java.util.Calendar.getInstance().apply {
                timeInMillis = lastTrainingDate
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else 0L

        val daysDiff = if (lastDateMidnight > 0) {
            ((sessionDateMidnight - lastDateMidnight) / (24 * 60 * 60 * 1000)).toInt()
        } else -1

        val newStreak = when {
            daysDiff == 0 -> currentStreak // Same day session
            daysDiff == 1 -> currentStreak + 1 // Consecutive day
            daysDiff < 0 -> currentStreak // Older session being synced, don't break/inc streak for now
            else -> 1 // Streak broken (>1 day gap)
        }

        val newLongestStreak = maxOf(longestStreak, newStreak)

        return copy(
            totalSessions = newTotalSessions,
            totalStrokes = newTotalStrokes,
            totalCorrectStrokes = newCorrectStrokes,
            totalTrainingMinutes = newTotalMinutes,
            averageAccuracy = newAvgAccuracy,
            averageSessionDuration = newAvgDuration,
            currentStreak = newStreak,
            longestStreak = newLongestStreak,
            lastTrainingDate = maxOf(lastTrainingDate, session.endTime),
            weeklySessionsCount = weeklySessionsCount + 1,
            monthlySessionsCount = monthlySessionsCount + 1,
            lastUpdatedAt = System.currentTimeMillis()
        )
    }

    fun getAccuracyPercent(): Int = (averageAccuracy * 100).toInt()
}
