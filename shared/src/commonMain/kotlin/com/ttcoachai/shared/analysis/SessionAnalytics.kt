package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.CorrectionType

/**
 * Immutable per-session analytics derived from the retained AnalysisResults +
 * feedback. Pure data; produced by [SessionAnalyticsBuilder], persisted in app/.
 */
data class SessionAnalytics(
    val sessionId: String,
    /** Bucketed accuracy %, 0..100, up to 12 points, oldest-first. */
    val accuracyTimeline: List<Float>,
    val peakAccuracy: Float,
    val peakBucketIndex: Int,
    /** Strokes with overallScore >= 80. */
    val cleanCount: Int,
    /** Strokes with overallScore < 80. */
    val errorCount: Int,
    /** Ranked desc by count, GENERAL dropped. */
    val focusAreas: List<FocusArea>,
    /** Deterministic English template over the real stats. */
    val summaryText: String,
)

data class FocusArea(val type: CorrectionType, val count: Int)
