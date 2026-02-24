/*
 * AI Coach for Table Tennis
 * Training State Manager - Manages training session state and statistics
 */

package com.ttcoachai.managers

import android.content.Context
import com.ttcoachai.R
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.FeedbackItem

class TrainingStateManager internal constructor(private val context: Context) {
    var isTrainingActive = false
        private set
    
    private val lock = Any()
    private val feedbackHistory = mutableListOf<String>()
    private val feedbackItemsHistory = mutableListOf<List<FeedbackItem>>()
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
        val durationSec = (durationMs / 1000).toInt()
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        return String.format("%02d:%02d", minutes, seconds)
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
    }
    
    fun getLatestFeedbackItems(): List<FeedbackItem> = synchronized(lock) {
        feedbackItemsHistory.lastOrNull() ?: emptyList()
    }
    
    fun addAnalysisResult(result: AnalysisResult) = synchronized(lock) {
        analysisResults.add(result)
        
        // Update consecutive good strokes
        if (result.overallScore >= 80) {
            consecutiveGoodStrokes++
        } else {
            consecutiveGoodStrokes = 0
        }
    }
    
    fun getAnalysisResults(): List<AnalysisResult> = synchronized(lock) { analysisResults.toList() }
    
    // Helper methods for UI
    fun getTotalHits(): Int = synchronized(lock) { analysisResults.size }
    fun getSuccessfulHits(): Int = synchronized(lock) { analysisResults.count { it.overallScore >= 70 } } // Threshold for "successful"
    
    fun getAverageScore(): Double = synchronized(lock) {
        if (analysisResults.isEmpty()) return 0.0
        analysisResults.map { it.overallScore }.average()
    }
    
    fun getStrokeCount(): Int = synchronized(lock) { analysisResults.size }
    fun getGoodStrokesCount(): Int = synchronized(lock) { analysisResults.count { it.overallScore >= 80 } }
    
    fun getSummaryText(): String {
        val totalStrokes = getStrokeCount()
        val goodStrokes = getGoodStrokesCount()
        val avgScore = getAverageScore()
        val percentage = if (totalStrokes > 0) (goodStrokes * 100 / totalStrokes) else 0
        
        return """
            ${context.getString(R.string.summary_total_strokes, totalStrokes)}
            ${context.getString(R.string.summary_successful_strokes, goodStrokes, percentage)}
            ${context.getString(R.string.summary_average_accuracy, avgScore)}
        """.trimIndent()
    }
    
    fun getImprovementTip(): String {
        if (analysisResults.isEmpty()) {
            return context.getString(R.string.start_training_advice)
        }
        
        val avgScore = getAverageScore()
        return when {
            avgScore >= 85 -> context.getString(R.string.tip_excellent)
            avgScore >= 70 -> context.getString(R.string.tip_good)
            avgScore >= 50 -> context.getString(R.string.tip_not_bad)
            else -> context.getString(R.string.tip_needs_practice)
        }
    }
    
    fun reset() = synchronized(lock) {
        isTrainingActive = false
        feedbackHistory.clear()
        analysisResults.clear()
        consecutiveGoodStrokes = 0
        startTime = 0
        endTime = 0
        pausedTime = 0
        totalPausedDuration = 0
    }
}
