package com.ttcoachai.services

import android.content.Context
import com.ttcoachai.LocaleHelper
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.feedback.LiveFeedbackFormatter
import com.ttcoachai.shared.models.AnalysisResult
import java.util.Random

/**
 * Generates feedback based on analysis results.
 *
 * Thin Android adapter: all text-generation logic (string tables + selection/
 * assembly rules) lives in [LiveFeedbackFormatter] / `LiveFeedbackCatalog`
 * (shared/commonMain), so it runs identically on iOS later. This class only
 * supplies the Android-specific bits — current locale, settings, audio/TTS —
 * and forwards to the shared formatter. Runtime output (same strings, same
 * selection logic) is unchanged from the pre-refactor implementation.
 */
class FeedbackGenerator(private val context: Context) {

    private val random = Random()
    private val settingsManager = SettingsManager(context)
    private val audioManager = FeedbackAudioManager(context)

    fun getSettingsManager() = settingsManager
    fun playTic() = audioManager.playTic()
    fun playTac() = audioManager.playTac()
    fun release() = audioManager.release()

    /** Maps the app's current locale to the shared catalog's language enum ("uk" -> UA, else EN). */
    private fun currentLang(): FeedbackLang =
        if (LocaleHelper.getSavedLanguage(context) == "uk") FeedbackLang.UA else FeedbackLang.EN

    fun handlePhaseTransition(oldPhase: com.ttcoachai.shared.models.StrokePhase, newPhase: com.ttcoachai.shared.models.StrokePhase) {
        if (oldPhase != newPhase) {
            when (newPhase) {
                com.ttcoachai.shared.models.StrokePhase.BACKSWING -> playTic()
                com.ttcoachai.shared.models.StrokePhase.FORWARD_SWING -> playTac()
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
    fun generateShortFeedback(result: AnalysisResult): String =
        LiveFeedbackFormatter.shortFeedback(result, currentLang()) { size -> random.nextInt(size) }

    /**
     * Generate detailed feedback for UI
     */
    fun generateDetailedFeedback(result: AnalysisResult): String {
        val isShort = settingsManager.getFeedbackType() == 0
        return LiveFeedbackFormatter.detailedFeedback(
            result = result,
            isCorrectionTypeEnabled = { type -> settingsManager.isCorrectionTypeEnabled(type) },
            short = isShort,
            lang = currentLang()
        )
    }

    fun generateParameterFeedback(
        parameterName: String,
        measuredValue: Float?,
        idealValue: Float,
        isValid: Boolean
    ): String = LiveFeedbackFormatter.parameterFeedback(parameterName, measuredValue, idealValue, isValid, currentLang())

    fun generateMotivationalMessage(consecutiveGoodStrokes: Int): String? =
        LiveFeedbackFormatter.motivational(consecutiveGoodStrokes, currentLang())

    fun generateSessionSummary(
        totalStrokes: Int,
        successfulStrokes: Int,
        averageScore: Float
    ): String = LiveFeedbackFormatter.sessionSummary(totalStrokes, successfulStrokes, averageScore, currentLang())

    fun generateImprovementTip(mostCommonError: String?, exerciseId: String): String? =
        LiveFeedbackFormatter.improvementTip(mostCommonError, currentLang())
}