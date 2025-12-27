/*
 * AI Coach for Table Tennis
 * Training State Manager - Manages training session state and statistics
 */

package com.google.mediapipe.examples.poselandmarker.managers

import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult

class TrainingStateManager {
    var isTrainingActive = false
        private set
    
    private val feedbackHistory = mutableListOf<String>()
    private val analysisResults = mutableListOf<AnalysisResult>()
    var consecutiveGoodStrokes = 0
        private set
    
    fun startTraining() {
        isTrainingActive = true
    }
    
    fun stopTraining() {
        isTrainingActive = false
    }
    
    fun addFeedback(feedback: String) {
        feedbackHistory.add(feedback)
        if (feedbackHistory.size > 10) {
            feedbackHistory.removeAt(0)
        }
    }
    
    fun getFeedbackHistory(): List<String> = feedbackHistory.toList()
    
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
        
        return """
            Загальна кількість ударів: $totalStrokes
            Успішні удари: $goodStrokes (${if (totalStrokes > 0) (goodStrokes * 100 / totalStrokes) else 0}%)
            Середня точність: ${String.format("%.1f", avgScore)}%
        """.trimIndent()
    }
    
    fun getImprovementTip(): String {
        if (analysisResults.isEmpty()) {
            return "Почніть тренування, щоб отримати поради"
        }
        
        val avgScore = getAverageScore()
        return when {
            avgScore >= 85 -> "🎯 Відмінно! Продовжуйте тренування для закріплення техніки"
            avgScore >= 70 -> "👍 Добре! Зосередьтесь на стабільності ударів"
            avgScore >= 50 -> "💪 Непогано! Покращуйте положення тіла та ракетки"
            else -> "🎓 Потрібна практика. Зосередьтесь на базовій техніці"
        }
    }
    
    fun reset() {
        isTrainingActive = false
        feedbackHistory.clear()
        analysisResults.clear()
        consecutiveGoodStrokes = 0
    }
}
