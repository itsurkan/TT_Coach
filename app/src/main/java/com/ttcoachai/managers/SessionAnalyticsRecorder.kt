package com.ttcoachai.managers

import android.util.Log
import com.ttcoachai.db.SessionAnalyticsDao
import com.ttcoachai.models.SessionAnalyticsEntity
import com.ttcoachai.shared.analysis.SessionAnalyticsBuilder
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.FeedbackItem

/**
 * Computes per-session analytics at the save boundary and persists them.
 * Reads already-retained state only — never touches frozen per-frame/per-stroke logic.
 */
class SessionAnalyticsRecorder(private val dao: SessionAnalyticsDao) {

    suspend fun record(
        sessionId: String,
        results: List<AnalysisResult>,
        feedback: List<FeedbackItem>,
    ) {
        if (sessionId.isBlank()) return
        val analytics = SessionAnalyticsBuilder.build(sessionId, results, feedback)
        val entity = SessionAnalyticsEntity.fromDomain(analytics, System.currentTimeMillis())
        runCatching { dao.upsert(entity) }
            .onFailure { Log.e(TAG, "Failed to persist session analytics for $sessionId", it) }
    }

    companion object {
        private const val TAG = "SessionAnalyticsRecorder"
    }
}
