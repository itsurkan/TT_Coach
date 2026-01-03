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
            result.errors.isEmpty() -> "Гарно! Продовжуйте в тому ж дусі!"
            else -> result.getPrimaryError() ?: "Працюйте над технікою"
        }
    }

    /**
     * Згенерувати детальний фідбек (для текстового відображення)
     */
    fun generateDetailedFeedback(result: AnalysisResult): String {
        val feedback = StringBuilder()
        
        feedback.append(result.getSummary()).append("\n\n")
        feedback.append("Оцінка: ${result.overallScore.toInt()}%\n\n")
        
        if (result.errors.isNotEmpty()) {
            feedback.append("⚠️ Помилки:\n")
            result.errors.forEach { error ->
                feedback.append("• $error\n")
            }
            feedback.append("\n")
        }
        
        if (result.recommendations.isNotEmpty()) {
            feedback.append("💡 Рекомендації:\n")
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
        if (measuredValue == null) return "❓ $parameterName: не визначено"
        
        return if (isValid) {
            "✅ $parameterName: ${measuredValue.toInt()}° (ідеально)"
        } else {
            "⚠️ $parameterName: ${measuredValue.toInt()}° (очікується ${idealValue.toInt()}°)"
        }
    }

    /**
     * Отримати рандомний позитивний фідбек
     */
    private fun getRandomPositiveFeedback(): String {
        val positiveFeedbacks = listOf(
            "✅ Чудово! Ідеальна техніка!",
            "✅ Відмінно! Так тримати!",
            "✅ Бездоганно! Продовжуйте!",
            "✅ Ідеально! Ви молодець!",
            "✅ Супер удар! Дуже добре!"
        )
        return positiveFeedbacks[random.nextInt(positiveFeedbacks.size)]
    }

    /**
     * Згенерувати мотиваційне повідомлення
     */
    fun generateMotivationalMessage(consecutiveGoodStrokes: Int): String? {
        return when (consecutiveGoodStrokes) {
            5 -> "🔥 5 гарних ударів підряд! Продовжуйте!"
            10 -> "🎯 10 відмінних ударів! Ви в ударі!"
            20 -> "⭐ 20 ударів поспіль! Неймовірно!"
            50 -> "🏆 50 ударів! Ви майстер!"
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
        feedback.append("📊 Підсумок тренування\n\n")
        feedback.append("Всього ударів: $totalStrokes\n")
        feedback.append("Успішних: $successfulStrokes\n")
        feedback.append("Точність: $accuracy%\n")
        feedback.append("Середня оцінка: ${averageScore.toInt()}%\n\n")
        
        feedback.append(when {
            accuracy >= 80 -> "🏆 Відмінна робота! Ви прогресуєте!"
            accuracy >= 60 -> "👍 Гарне тренування! Продовжуйте працювати!"
            accuracy >= 40 -> "💪 Є покращення! Тренуйтесь регулярно!"
            else -> "🎯 Не здавайтесь! Кожне тренування робить вас кращим!"
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
            mostCommonError.contains("зап'ястя", ignoreCase = true) -> 
                "💡 Порада: Уявіть, що тримаєте олівець - зап'ястя має бути продовженням передпліччя"
            
            mostCommonError.contains("ротація", ignoreCase = true) -> 
                "💡 Порада: Поверніть талію та плечі як єдине ціле - уявіть, що закручуєте пружину"
            
            mostCommonError.contains("контакт", ignoreCase = true) -> 
                "💡 Порада: Оптимальна точка контакту - на висоті пояса, трохи попереду тіла"
            
            mostCommonError.contains("проведення", ignoreCase = true) -> 
                "💡 Порада: Продовжуйте рух після контакту - ракетка має закінчити біля протилежного плеча"
            
            else -> null
        }
    }
}
