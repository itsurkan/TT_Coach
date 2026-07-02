package com.ttcoachai.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ttcoachai.db.SessionAnalyticsConverters
import com.ttcoachai.shared.analysis.FocusArea
import com.ttcoachai.shared.analysis.SessionAnalytics

/**
 * 1:1 companion to a `training_sessions` row (PK = sessionId). Timeline and focus
 * areas are stored as JSON via [SessionAnalyticsConverters]. Rows are optional:
 * sessions recorded before this feature (or Firestore-synced) have none, and
 * Session Review degrades gracefully when absent.
 */
@Entity(tableName = "session_analytics")
data class SessionAnalyticsEntity(
    @PrimaryKey val sessionId: String,
    val accuracyTimelineJson: String,
    val focusAreasJson: String,
    val peakAccuracy: Float,
    val peakBucketIndex: Int,
    val cleanCount: Int,
    val errorCount: Int,
    val summaryText: String,
    val generatedAtMs: Long,
) {
    fun timeline(): List<Float> = SessionAnalyticsConverters.jsonToFloatList(accuracyTimelineJson)
    fun focusAreas(): List<FocusArea> = SessionAnalyticsConverters.jsonToFocusAreas(focusAreasJson)

    companion object {
        fun fromDomain(a: SessionAnalytics, generatedAtMs: Long): SessionAnalyticsEntity =
            SessionAnalyticsEntity(
                sessionId = a.sessionId,
                accuracyTimelineJson = SessionAnalyticsConverters.floatListToJson(a.accuracyTimeline),
                focusAreasJson = SessionAnalyticsConverters.focusAreasToJson(a.focusAreas),
                peakAccuracy = a.peakAccuracy,
                peakBucketIndex = a.peakBucketIndex,
                cleanCount = a.cleanCount,
                errorCount = a.errorCount,
                summaryText = a.summaryText,
                generatedAtMs = generatedAtMs,
            )
    }
}
