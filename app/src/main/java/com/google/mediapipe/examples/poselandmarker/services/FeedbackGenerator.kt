/*
 * AI Coach for Table Tennis
 * Feedback Generator - Генерація фідбеку для користувача
 */

package com.google.mediapipe.examples.poselandmarker.services

import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import java.util.*

/**
 * Генератор фідбеку на основі результатів аналізу
 */
class FeedbackGenerator {
    
    private val random = Random()
    
    /**
     * Згенерувати короткий фідбек (для аудіо TTS)
     */
    fun generateShortFeedback(result: AnalysisResult): String {
        return when {
            result.overallScore >= 90f -> getRandomPositiveFeedback()
            result.errors.isEmpty() -> "Good! Keep it up!"
            else -> result.getPrimaryError() ?: "Work on technique"
        }
    }

    /**
     * Згенерувати детальний фідбек (для текстового відображення)
     */
    fun generateDetailedFeedback(result: AnalysisResult): String {
        val feedback = StringBuilder()
        
        feedback.append(result.getSummary()).append("\n\n")
        feedback.append("Score: ${result.overallScore.toInt()}%\n\n")
        
        if (result.errors.isNotEmpty()) {
            feedback.append("⚠️ Errors:\n")
            result.errors.forEach { error ->
                feedback.append("• $error\n")
            }
            feedback.append("\n")
        }
        
        if (result.recommendations.isNotEmpty()) {
            feedback.append("💡 Recommendations:\n")
            result.recommendations.forEach { recommendation ->
                feedback.append("• $recommendation\n")
            }
        }
        
        return feedback.toString()
    }

    /**
     * Згенерувати фідбек для конкретного параметру
     */
    fun generateParameterFeedback(
        parameterName: String,
        measuredValue: Float?,
        idealValue: Float,
        isValid: Boolean
    ): String {
        if (measuredValue == null) return "❓ $parameterName: not detected"
        
        return if (isValid) {
            "✅ $parameterName: ${measuredValue.toInt()}° (perfect)"
        } else {
            "⚠️ $parameterName: ${measuredValue.toInt()}° (expected ${idealValue.toInt()}°)"
        }
    }

    /**
     * Отримати рандомний позитивний фідбек
     */
    private fun getRandomPositiveFeedback(): String {
        val positiveFeedbacks = listOf(
            "✅ Excellent! Perfect technique!",
            "✅ Great! Keep it up!",
            "✅ Flawless! Continue!",
            "✅ Perfect! Well done!",
            "✅ Super stroke! Very good!"
        )
        return positiveFeedbacks[random.nextInt(positiveFeedbacks.size)]
    }

    /**
     * Згенерувати мотиваційне повідомлення
     */
    fun generateMotivationalMessage(consecutiveGoodStrokes: Int): String? {
        return when (consecutiveGoodStrokes) {
            5 -> "🔥 5 good strokes in a row! Keep going!"
            10 -> "🎯 10 excellent strokes! You're on fire!"
            20 -> "⭐ 20 strokes in a row! Incredible!"
            50 -> "🏆 50 strokes! You're a master!"
            else -> null
        }
    }

    /**
     * Згенерувати підсумковий фідбек сесії
     */
    fun generateSessionSummary(
        totalStrokes: Int,
        successfulStrokes: Int,
        averageScore: Float
    ): String {
        val accuracy = if (totalStrokes > 0) {
            (successfulStrokes * 100 / totalStrokes)
        } else {
            0
        }
        
        val feedback = StringBuilder()
        feedback.append("📊 Training Summary\n\n")
        feedback.append("Total strokes: $totalStrokes\n")
        feedback.append("Successful: $successfulStrokes\n")
        feedback.append("Accuracy: $accuracy%\n")
        feedback.append("Average score: ${averageScore.toInt()}%\n\n")
        
        feedback.append(when {
            accuracy >= 80 -> "🏆 Excellent work! You're making progress!"
            accuracy >= 60 -> "👍 Good training! Keep working!"
            accuracy >= 40 -> "💪 There's improvement! Train regularly!"
            else -> "🎯 Don't give up! Every training makes you better!"
        })
        
        return feedback.toString()
    }

    /**
     * Згенерувати підказку для покращення
     */
    fun generateImprovementTip(
        mostCommonError: String?,
        exerciseId: String
    ): String? {
        if (mostCommonError == null) return null
        
        return when {
            mostCommonError.contains("wrist", ignoreCase = true) -> 
                "💡 Tip: Imagine holding a pencil - wrist should be an extension of forearm"
            
            mostCommonError.contains("rotation", ignoreCase = true) -> 
                "💡 Tip: Turn waist and shoulders as one - imagine winding a spring"
            
            mostCommonError.contains("contact", ignoreCase = true) -> 
                "💡 Tip: Optimal contact point - waist height, slightly ahead of body"
            
            mostCommonError.contains("follow", ignoreCase = true) -> 
                "💡 Tip: Continue movement after contact - racket should finish near opposite shoulder"
            
            else -> null
        }
    }
}
