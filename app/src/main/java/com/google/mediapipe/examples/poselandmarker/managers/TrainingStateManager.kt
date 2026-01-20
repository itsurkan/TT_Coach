/*
 * AI Coach for Table Tennis
 * Training State Manager - Manages training session state and statistics
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.content.Context
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.FeedbackItem

class TrainingStateManager(private val context: Context) {
    var isTrainingActive = false
        private set
    
    private val feedbackHistory = mutableListOf<String>()
    private val feedbackItemsHistory = mutableListOf<List<FeedbackItem>>()
    private val analysisResults = mutableListOf<AnalysisResult>()
    var consecutiveGoodStrokes = 0
        private set
    
    fun startTraining() {
        isTrainingActive = true
    }
    
    fun stopTraining() {
        isTrainingActive = false
    }
    
    fun pauseTraining() {
        isTrainingActive = false
    }
    
    fun resumeTraining() {
        isTrainingActive = true
    }
    
    fun addFeedback(feedback: String) {
        feedbackHistory.add(feedback)
        if (feedbackHistory.size > 10) {
            feedbackHistory.removeAt(0)
        }
    }
    
    fun getFeedbackHistory(): List<String> = feedbackHistory.toList()
    
    fun addFeedbackItems(items: List<FeedbackItem>) {
        feedbackItemsHistory.add(items)
        if (feedbackItemsHistory.size > 10) {
            feedbackItemsHistory.removeAt(0)
        }
    }
    
    fun getLatestFeedbackItems(): List<FeedbackItem> {
        return feedbackItemsHistory.lastOrNull() ?: emptyList()
    }
    
    fun addAnalysisResult(result: AnalysisResult) {
        analysisResults.add(result)
        
        // Update consecutive good strokes
        if (result.overallScore >= 80) {
            consecutiveGoodStrokes++
        } else {
            consecutiveGoodStrokes = 0
        }
    }
    
    fun getAnalysisResults(): List<AnalysisResult> = analysisResults.toList()
    
    fun getAverageScore(): Double {
        if (analysisResults.isEmpty()) return 0.0
        return analysisResults.map { it.overallScore }.average()
    }
    
    fun getStrokeCount(): Int = analysisResults.size
    
    fun getGoodStrokesCount(): Int = analysisResults.count { it.overallScore >= 80 }
    
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
    
    fun reset() {
        isTrainingActive = false
        feedbackHistory.clear()
        analysisResults.clear()
        consecutiveGoodStrokes = 0
    }
}
