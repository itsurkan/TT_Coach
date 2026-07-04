package com.ttcoachai.shared.session

import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType

/**
 * Pure port of `TrainingStateManager`'s stats-aggregation logic (Android `app/`).
 * No storage, no locking, no singleton — just the arithmetic over a
 * `List<AnalysisResult>` / feedback-type-count map, so it can be unit-tested on
 * the JVM and reused from iOS later. `TrainingStateManager` keeps the mutable
 * state (lists, locks, timers) and delegates every computation here.
 */
object SessionStatsCalculator {

    /** Mirrors `TrainingStateManager.getTotalHits` / `getStrokeCount`. */
    fun totalHits(results: List<AnalysisResult>): Int = results.size

    /** Mirrors `TrainingStateManager.getSuccessfulHits` (score >= 70). */
    fun successfulHits(results: List<AnalysisResult>): Int = results.count { it.overallScore >= 70 }

    /** Mirrors `TrainingStateManager.getGoodStrokesCount` (score >= 80). */
    fun goodStrokesCount(results: List<AnalysisResult>): Int = results.count { it.overallScore >= 80 }

    /** Mirrors `TrainingStateManager.getAverageScore` (0.0 for an empty list). */
    fun averageScore(results: List<AnalysisResult>): Double {
        if (results.isEmpty()) return 0.0
        return results.map { it.overallScore }.average()
    }

    /** Mirrors `TrainingStateManager.getFeedbackCounts`: descending by count, ties keep insertion order. */
    fun sortedFeedbackCounts(feedbackTypeCounts: Map<CorrectionType, Int>): List<Pair<CorrectionType, Int>> =
        feedbackTypeCounts.entries.sortedByDescending { it.value }.map { it.key to it.value }

    /** Mirrors `TrainingStateManager.getFlaggedTotal`. */
    fun flaggedTotal(feedbackTypeCounts: Map<CorrectionType, Int>): Int = feedbackTypeCounts.values.sum()

    /**
     * Mirrors the consecutive-good-strokes update rule applied in
     * `TrainingStateManager.addAnalysisResult`: increment on score >= 80, else
     * reset to zero.
     */
    fun updateConsecutiveGoodStrokes(current: Int, latestResult: AnalysisResult): Int =
        if (latestResult.overallScore >= 80) current + 1 else 0

    /**
     * Mirrors `TrainingStateManager.getSessionTimeFormatted` / the shared MM:SS
     * math in `SessionStatsFormatter.formatDuration` — "MM:SS" from a duration
     * in milliseconds (whole seconds, floor).
     */
    fun formatSessionTime(durationMs: Long): String {
        val durationSec = (durationMs / 1000).toInt()
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        return "${minutes.pad2()}:${seconds.pad2()}"
    }

    private fun Int.pad2(): String = if (this < 10) "0$this" else this.toString()
}
