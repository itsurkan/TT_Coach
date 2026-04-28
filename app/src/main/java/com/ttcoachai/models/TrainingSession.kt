/*
 * AI Coach for Table Tennis
 * Training Session Model - Individual training session data
 */

package com.ttcoachai.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

/**
 * Represents a single training session.
 * Stored in Firestore and locally in Room for offline access.
 */
@Entity(tableName = "training_sessions")
data class TrainingSession(
    @PrimaryKey
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val exerciseId: String = "",
    val exerciseName: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val durationSeconds: Int = 0,
    val strokeCount: Int = 0,
    val correctStrokes: Int = 0,
    val accuracy: Float = 0f,
    // Cloud Storage path for large pose data (optional)
    val poseDataPath: String? = null,
    // Device info for debugging
    val deviceModel: String = android.os.Build.MODEL,
    val appVersion: String = "",
    
    // Sync status for offline-first logic
    @get:Exclude
    val isSynced: Boolean = true
) {
    /**
     * No-arg constructor required for Firestore deserialization
     */
    constructor() : this(id = "")

    companion object {
        const val COLLECTION = "sessions"
        const val FIELD_USER_ID = "userId"
        const val FIELD_START_TIME = "startTime"
        const val FIELD_EXERCISE_ID = "exerciseId"

        fun generateId(): String = java.util.UUID.randomUUID().toString()
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "exerciseId" to exerciseId,
        "exerciseName" to exerciseName,
        "startTime" to startTime,
        "endTime" to endTime,
        "durationSeconds" to durationSeconds,
        "strokeCount" to strokeCount,
        "correctStrokes" to correctStrokes,
        "accuracy" to accuracy,
        "poseDataPath" to poseDataPath,
        "deviceModel" to deviceModel,
        "appVersion" to appVersion
    )

    fun getFormattedDuration(): String {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    fun getAccuracyPercent(): Int = (accuracy * 100).toInt()
}
