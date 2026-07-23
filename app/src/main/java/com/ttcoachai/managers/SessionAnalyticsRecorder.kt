package com.ttcoachai.managers

import android.util.Log
import com.ttcoachai.db.SessionAnalyticsDao
import com.ttcoachai.models.SessionAnalyticsEntity
import com.ttcoachai.shared.analysis.SessionAnalyticsBuilder
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType

/**
 * Computes per-session analytics at the save boundary and persists them.
 * Reads already-retained state only — never touches frozen per-frame/per-stroke logic.
 */
class SessionAnalyticsRecorder(private val dao: SessionAnalyticsDao) {

    /**
     * [feedbackCounts] must be a full-session tally (e.g.
     * `TrainingStateManager.getFeedbackCounts()`), not just the most recent rep's feedback —
     * see [SessionAnalyticsBuilder.build] Map overload.
     */
    suspend fun record(
        sessionId: String,
        results: List<AnalysisResult>,
        feedbackCounts: Map<CorrectionType, Int>,
        isTypeEnabled: (CorrectionType) -> Boolean = { true },
    ) {
        if (sessionId.isBlank()) return
        val analytics = SessionAnalyticsBuilder.build(sessionId, results, feedbackCounts, isTypeEnabled)
        val entity = SessionAnalyticsEntity.fromDomain(analytics, System.currentTimeMillis())
        runCatching { dao.upsert(entity) }
            .onFailure { Log.e(TAG, "Failed to persist session analytics for $sessionId", it) }
    }

    companion object {
        private const val TAG = "SessionAnalyticsRecorder"
    }
}
