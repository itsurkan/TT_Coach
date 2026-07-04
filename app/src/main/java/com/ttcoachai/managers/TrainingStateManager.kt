/*
 * AI Coach for Table Tennis
 * Training State Manager - Manages training session state and statistics
 */

package com.ttcoachai.managers

import android.content.Context
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.FeedbackItem
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.session.SessionStatsCalculator

/**
 * Storage/locking/singleton/timer state for a live training session. All pure
 * stats arithmetic (counts, average score, sorted feedback counts, the
 * consecutive-good-streak rule, MM:SS formatting) is delegated to
 * [SessionStatsCalculator] (shared/commonMain) so it's covered by JVM tests
 * and reusable from iOS later. Public API is unchanged.
 */
class TrainingStateManager internal constructor(private val context: Context) {
    var isTrainingActive = false
        private set
    
    private val lock = Any()
    private val feedbackHistory = mutableListOf<String>()
    private val feedbackItemsHistory = mutableListOf<List<FeedbackItem>>()
    private val feedbackTypeCounts = LinkedHashMap<CorrectionType, Int>()
    private val analysisResults = mutableListOf<AnalysisResult>()
    private var currentFeedbackItems = listOf<FeedbackItem>()
    var consecutiveGoodStrokes = 0
        private set
    
    // Timer logic
    private var startTime: Long = 0
    private var endTime: Long = 0
    private var pausedTime: Long = 0
    private var totalPausedDuration: Long = 0
    
    companion object {
        @Volatile
        private var instance: TrainingStateManager? = null
        
        fun getInstance(context: Context): TrainingStateManager {
            return instance ?: synchronized(this) {
                instance ?: TrainingStateManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    fun startTraining() {
        isTrainingActive = true
        startTime = System.currentTimeMillis()
        totalPausedDuration = 0
        pausedTime = 0
    }
    
    fun stopTraining() {
        if (isTrainingActive) {
            endTime = System.currentTimeMillis()
        } else if (pausedTime > 0) {
            // If training was paused when stopped, use pausedTime as endTime
            endTime = pausedTime
        } else {
            endTime = System.currentTimeMillis()
        }
        isTrainingActive = false
    }
    
    fun pauseTraining() {
        if (isTrainingActive) {
            isTrainingActive = false
            pausedTime = System.currentTimeMillis()
        }
    }
    
    fun resumeTraining() {
        if (!isTrainingActive) {
            isTrainingActive = true
            if (pausedTime > 0) {
                totalPausedDuration += System.currentTimeMillis() - pausedTime
                pausedTime = 0
            }
        }
    }
    
    fun getSessionTimeFormatted(): String {
        if (startTime == 0L) return "00:00"

        val currentTime = if (isTrainingActive) System.currentTimeMillis() else (if (pausedTime > 0L) pausedTime else System.currentTimeMillis())
        val durationMs = currentTime - startTime - totalPausedDuration
        return SessionStatsCalculator.formatSessionTime(durationMs)
    }
    
    fun getStartTime(): Long = startTime
    
    fun getSessionDurationSeconds(): Int {
        if (startTime == 0L) return 0
        val currentTime = when {
            endTime > 0L -> endTime  // Training finished
            pausedTime > 0L -> pausedTime  // Training paused
            isTrainingActive -> System.currentTimeMillis()  // Training active
            else -> System.currentTimeMillis()
        }
        val durationMs = currentTime - startTime - totalPausedDuration
        return (durationMs / 1000).toInt()
    }
    
    fun getEndTime(): Long = if (endTime > 0L) endTime else System.currentTimeMillis()
    
    fun addFeedback(feedback: String) = synchronized(lock) {
        feedbackHistory.add(feedback)
        if (feedbackHistory.size > 10) {
            feedbackHistory.removeAt(0)
        }
    }
    
    fun getFeedbackHistory(): List<String> = synchronized(lock) { feedbackHistory.toList() }
    
    fun addFeedbackItems(items: List<FeedbackItem>) = synchronized(lock) {
        feedbackItemsHistory.add(items)
        if (feedbackItemsHistory.size > 10) {
            feedbackItemsHistory.removeAt(0)
        }
        items.forEach {
            if (!it.isPositive) {
                feedbackTypeCounts.merge(it.type, 1) { a, b -> a + b }
            }
        }
    }

    fun getLatestFeedbackItems(): List<FeedbackItem> = synchronized(lock) {
        feedbackItemsHistory.lastOrNull() ?: emptyList()
    }

    /**
     * Distinct non-positive messages recorded for [type] across the last 10 strokes'
     * feedback history, newest first. Backs the "Recent observations" section of the
     * tap-to-explain sheet on the live-feedback list.
     */
    fun getRecentMessagesFor(type: CorrectionType, limit: Int = 5): List<String> = synchronized(lock) {
        feedbackItemsHistory.asReversed()
            .flatten()
            .filter { !it.isPositive && it.type == type }
            .map { it.message }
            .distinct()
            .take(limit)
    }

    /**
     * Chronological (oldest -> newest) per-rep pass/fail flags for [type]: true if that rep's
     * feedback items include any non-positive item of [type]. Backs the rep-strip visual on the
     * tap-to-explain sheet.
     */
    fun getRepFlagsFor(type: CorrectionType): List<Boolean> = synchronized(lock) {
        feedbackItemsHistory.map { items ->
            items.any { it.type == type && !it.isPositive }
        }
    }

    /**
     * Chronological (oldest -> newest) per-rep `strokeLandmarks`, index-aligned with
     * [getRepFlagsFor]: for each rep in [feedbackItemsHistory], the rep's first [FeedbackItem]
     * with non-empty `strokeLandmarks`, else an empty list. Backs the tappable rep-strip ->
     * snapshot wiring on the tap-to-explain sheet, where a tap on rep i needs that rep's own
     * pose regardless of which correction type is currently displayed.
     */
    fun getRepStrokeLandmarks(): List<List<List<Landmark3D>>> = synchronized(lock) {
        feedbackItemsHistory.map { items ->
            items.firstOrNull { it.strokeLandmarks.isNotEmpty() }?.strokeLandmarks ?: emptyList()
        }
    }

    /**
     * `strokeLandmarks` of the most recent rep that had a flagged (non-positive) item of [type]
     * with non-empty landmarks; empty if no such rep exists. Backs the pose-snapshot visual on
     * the tap-to-explain sheet.
     */
    fun getLatestStrokeLandmarksFor(type: CorrectionType): List<List<Landmark3D>> = synchronized(lock) {
        feedbackItemsHistory.asReversed()
            .flatten()
            .firstOrNull { it.type == type && !it.isPositive && it.strokeLandmarks.isNotEmpty() }
            ?.strokeLandmarks
            ?: emptyList()
    }

    fun getFeedbackCounts(): List<Pair<CorrectionType, Int>> = synchronized(lock) {
        SessionStatsCalculator.sortedFeedbackCounts(feedbackTypeCounts)
    }

    fun getFlaggedTotal(): Int = synchronized(lock) { SessionStatsCalculator.flaggedTotal(feedbackTypeCounts) }

    fun addAnalysisResult(result: AnalysisResult) = synchronized(lock) {
        analysisResults.add(result)
        consecutiveGoodStrokes = SessionStatsCalculator.updateConsecutiveGoodStrokes(consecutiveGoodStrokes, result)
    }

    fun getAnalysisResults(): List<AnalysisResult> = synchronized(lock) { analysisResults.toList() }

    // Helper methods for UI
    fun getTotalHits(): Int = synchronized(lock) { SessionStatsCalculator.totalHits(analysisResults) }
    fun getSuccessfulHits(): Int = synchronized(lock) { SessionStatsCalculator.successfulHits(analysisResults) } // Threshold for "successful"

    fun getAverageScore(): Double = synchronized(lock) { SessionStatsCalculator.averageScore(analysisResults) }

    fun getStrokeCount(): Int = synchronized(lock) { SessionStatsCalculator.totalHits(analysisResults) }
    fun getGoodStrokesCount(): Int = synchronized(lock) { SessionStatsCalculator.goodStrokesCount(analysisResults) }
    
    fun reset() = synchronized(lock) {
        isTrainingActive = false
        feedbackHistory.clear()
        analysisResults.clear()
        feedbackTypeCounts.clear()
        consecutiveGoodStrokes = 0
        startTime = 0
        endTime = 0
        pausedTime = 0
        totalPausedDuration = 0
    }
}
