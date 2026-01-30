package com.ttcoachai.services

import android.content.Context
import android.util.Log
import com.ttcoachai.R
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.models.AnalysisResult
import com.ttcoachai.models.FeedbackItem
import java.util.*

/**
 * Generates feedback based on analysis results
 */
class FeedbackGenerator(private val context: Context) {
    
    private val random = Random()
    private val settingsManager = SettingsManager(context)
    private val audioManager = FeedbackAudioManager(context)
    
    fun getSettingsManager() = settingsManager
    fun playTic() = audioManager.playTic()
    fun playTac() = audioManager.playTac()
    fun release() = audioManager.release()

    fun handlePhaseTransition(oldPhase: com.ttcoachai.models.StrokePhase, newPhase: com.ttcoachai.models.StrokePhase) {
        if (oldPhase != newPhase) {
            when (newPhase) {
                com.ttcoachai.models.StrokePhase.FORWARD_SWING -> {
                    if (oldPhase == com.ttcoachai.models.StrokePhase.BACKSWING) playTic()
                }
                com.ttcoachai.models.StrokePhase.CONTACT -> playTac()
                else -> {}
            }
        }
    }

    /**
     * Play audio feedback based on analysis results
     */
    fun playFeedbackAudio(result: AnalysisResult) {
        if (!settingsManager.isAudioFeedbackEnabled()) return
        
        if (result.overallScore >= 80) {
            audioManager.playTone(android.media.ToneGenerator.TONE_PROP_ACK, 200)
        } else if (result.overallScore < 50) {
            audioManager.playTone(android.media.ToneGenerator.TONE_PROP_NACK, 300)
        }
    }
    
    /**
     * Generate short feedback for TTS
     */
    fun generateShortFeedback(result: AnalysisResult): String {
        return if (result.overallScore >= 85) getRandomPositiveFeedback()
        else result.feedbackItems.firstOrNull { !it.isPositive }?.message ?: getRandomPositiveFeedback()
    }

    /**
     * Generate detailed feedback for UI
     */
    fun generateDetailedFeedback(result: AnalysisResult): String {
        val feedback = StringBuilder()
        val isShort = settingsManager.getFeedbackType() == 0
        
        feedback.append(context.getString(R.string.feedback_score_format, result.overallScore.toInt())).append("\n")
        
        val filteredFeedbackItems = result.feedbackItems.filter { item ->
            settingsManager.isCorrectionTypeEnabled(item.type)
        }

        if (filteredFeedbackItems.any { !it.isPositive }) {
            filteredFeedbackItems.filter { !it.isPositive }.forEach { item ->
                feedback.append("• ${resolveFeedbackItem(item, isShort)}\n")
            }
        }
        
        if (result.recommendations.isNotEmpty()) {
            result.recommendations.forEach { recKey ->
                feedback.append("• ${resolveString(recKey, isShort)}\n")
            }
        }
        
        return feedback.toString()
    }

    private fun resolveFeedbackItem(item: FeedbackItem, isShort: Boolean): String {
        return resolveString(item.message, isShort)
    }

    private fun resolveString(key: String, isShort: Boolean): String {
        if (key.isEmpty()) return ""
        
        val resName = if (isShort) "short_$key" else "${key}_full"
        val resId = context.resources.getIdentifier(resName, "string", context.packageName)
        
        return if (resId != 0) {
            context.getString(resId)
        } else {
            Log.e(TAG, "Resource not found: $resName")
            key.replace("error_", "").replace("rec_", "").replace("_", " ").replaceFirstChar { it.uppercase() }
        }
    }

    fun generateParameterFeedback(
        parameterName: String,
        measuredValue: Float?,
        idealValue: Float,
        isValid: Boolean
    ): String {
        if (measuredValue == null) return context.getString(R.string.feedback_param_not_detected, parameterName)
        
        return if (isValid) {
            context.getString(R.string.feedback_param_perfect, parameterName, measuredValue.toInt())
        } else {
            context.getString(R.string.feedback_param_expected, parameterName, measuredValue.toInt(), idealValue.toInt())
        }
    }

    private fun getRandomPositiveFeedback(): String {
        val positiveFeedbacks = listOf(
            context.getString(R.string.positive_feedback_1),
            context.getString(R.string.positive_feedback_2),
            context.getString(R.string.positive_feedback_3),
            context.getString(R.string.positive_feedback_4),
            context.getString(R.string.positive_feedback_5)
        )
        return positiveFeedbacks[random.nextInt(positiveFeedbacks.size)]
    }

    fun generateMotivationalMessage(consecutiveGoodStrokes: Int): String? {
        return when (consecutiveGoodStrokes) {
            5 -> context.getString(R.string.motivational_5_strokes)
            10 -> context.getString(R.string.motivational_10_strokes)
            20 -> context.getString(R.string.motivational_20_strokes)
            50 -> context.getString(R.string.motivational_50_strokes)
            else -> null
        }
    }

    fun generateSessionSummary(
        totalStrokes: Int,
        successfulStrokes: Int,
        averageScore: Float
    ): String {
        val accuracy = if (totalStrokes > 0) (successfulStrokes * 100 / totalStrokes) else 0
        
        val summary = StringBuilder()
        summary.append(context.getString(R.string.session_summary_title)).append("\n")
        summary.append(context.getString(R.string.session_total_strokes, totalStrokes)).append("\n")
        summary.append(context.getString(R.string.session_successful, successfulStrokes)).append("\n")
        summary.append(context.getString(R.string.session_accuracy, accuracy)).append("\n")
        summary.append(context.getString(R.string.session_average_score, averageScore.toInt())).append("\n")
        
        return summary.toString()
    }

    fun generateImprovementTip(mostCommonError: String?, exerciseId: String): String? {
        if (mostCommonError == null) return null
        return when {
            mostCommonError.contains("wrist", ignoreCase = true) -> context.getString(R.string.improvement_tip_wrist)
            mostCommonError.contains("rotation", ignoreCase = true) -> context.getString(R.string.improvement_tip_rotation)
            mostCommonError.contains("contact", ignoreCase = true) -> context.getString(R.string.improvement_tip_contact)
            mostCommonError.contains("follow", ignoreCase = true) -> context.getString(R.string.improvement_tip_follow)
            else -> null
        }
    }

    companion object {
        private const val TAG = "FeedbackGenerator"
    }
}
