package com.ttcoachai.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ttcoachai.db.SessionAnalyticsConverters
import com.ttcoachai.shared.analysis.FocusArea
import com.ttcoachai.shared.analysis.SessionAnalytics
import com.ttcoachai.shared.models.Keypoint2D

/**
 * 1:1 companion to a `training_sessions` row (PK = sessionId). Timeline and focus
 * areas are stored as JSON via [SessionAnalyticsConverters]. Rows are optional:
 * sessions recorded before this feature (or Firestore-synced) have none, and
 * Session Review degrades gracefully when absent.
 *
 * [repStartPoseJson]/[repEndPoseJson] are the representative rep's start/end COCO-17
 * keypoints (see [com.ttcoachai.util.RepresentativeRepSelector]), persisted so the
 * Session Review "stroke snapshot" skeleton still renders when the screen is opened later
 * from History, not just right after the session (when in-memory `TrainingStateManager`
 * state is available). Null on rows written before this feature, or when the session had
 * no usable rep captures — Session Review degrades gracefully (no skeleton) in that case.
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
    val repStartPoseJson: String? = null,
    val repEndPoseJson: String? = null,
) {
    fun timeline(): List<Float> = SessionAnalyticsConverters.jsonToFloatList(accuracyTimelineJson)
    fun focusAreas(): List<FocusArea> = SessionAnalyticsConverters.jsonToFocusAreas(focusAreasJson)
    fun repStartPose(): List<Keypoint2D> = SessionAnalyticsConverters.jsonToKeypoints(repStartPoseJson)
    fun repEndPose(): List<Keypoint2D> = SessionAnalyticsConverters.jsonToKeypoints(repEndPoseJson)

    companion object {
        fun fromDomain(
            a: SessionAnalytics,
            generatedAtMs: Long,
            repStartPose: List<Keypoint2D> = emptyList(),
            repEndPose: List<Keypoint2D> = emptyList(),
        ): SessionAnalyticsEntity =
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
                repStartPoseJson = repStartPose.takeIf { it.isNotEmpty() }
                    ?.let { SessionAnalyticsConverters.keypointsToJson(it) },
                repEndPoseJson = repEndPose.takeIf { it.isNotEmpty() }
                    ?.let { SessionAnalyticsConverters.keypointsToJson(it) },
            )
    }
}
