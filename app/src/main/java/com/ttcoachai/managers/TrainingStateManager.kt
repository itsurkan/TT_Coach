/*
 * AI Coach for Table Tennis
 * Training State Manager - Manages training session state and statistics
 */

package com.ttcoachai.managers

import android.content.Context
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.FeedbackItem
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
