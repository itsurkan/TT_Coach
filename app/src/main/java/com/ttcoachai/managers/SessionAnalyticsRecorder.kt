package com.ttcoachai.managers

import android.util.Log
import com.ttcoachai.db.SessionAnalyticsDao
import com.ttcoachai.models.SessionAnalyticsEntity
import com.ttcoachai.shared.analysis.SessionAnalyticsBuilder
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.util.RepresentativeRepSelector

/**
 * Computes per-session analytics at the save boundary and persists them.
 * Reads already-retained state only — never touches frozen per-frame/per-stroke logic.
 */
class SessionAnalyticsRecorder(private val dao: SessionAnalyticsDao) {

    /**
     * [feedbackCounts] must be a full-session tally (e.g.
     * `TrainingStateManager.getFeedbackCounts()`), not just the most recent rep's feedback —
     * see [SessionAnalyticsBuilder.build] Map overload.
     *
     * [repPoses] is the session's full rep-pose capture buffer (e.g.
     * `TrainingStateManager.getRepPoses()`, chronological, RTM path only — empty for
     * legacy/video sessions). The representative rep for the persisted "stroke snapshot"
     * skeleton is chosen via [RepresentativeRepSelector], keyed on this session's own
     * top focus type (the first entry of the [SessionAnalyticsBuilder]-computed focus areas),
     * so the persisted snapshot matches what Session Review highlights.
     */
    suspend fun record(
        sessionId: String,
        results: List<AnalysisResult>,
        feedbackCounts: Map<CorrectionType, Int>,
        isTypeEnabled: (CorrectionType) -> Boolean = { true },
        repPoses: List<RepPoseCapture> = emptyList(),
    ) {
        if (sessionId.isBlank()) return
        val analytics = SessionAnalyticsBuilder.build(sessionId, results, feedbackCounts, isTypeEnabled)
        val topFocusType = analytics.focusAreas.firstOrNull()?.type
        val representative = RepresentativeRepSelector.select(repPoses, topFocusType)
        val entity = SessionAnalyticsEntity.fromDomain(
            analytics,
            System.currentTimeMillis(),
            repStartPose = representative?.start ?: emptyList(),
            repEndPose = representative?.end ?: emptyList(),
        )
        runCatching { dao.upsert(entity) }
            .onFailure { Log.e(TAG, "Failed to persist session analytics for $sessionId", it) }
    }

    companion object {
        private const val TAG = "SessionAnalyticsRecorder"
    }
}
