/*
 * AI Coach for Table Tennis
 * Feedback Generator - Генерація фідбеку для користувача
 */

package com.google.mediapipe.examples.poselandmarker.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.ToneGenerator
import android.util.Log
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.managers.SettingsManager
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.FeedbackItem
import java.util.*

/**
 * Генератор фідбеку на основі результатів аналізу
 */
class FeedbackGenerator(private val context: Context) {
    
    private val random = Random()
    private val settingsManager = SettingsManager(context)
    
    fun getSettingsManager() = settingsManager
    
    private var loadedSounds = mutableSetOf<Int>()
    private var toneGenerator: ToneGenerator? = null
    private var soundPool: SoundPool? = null
    private var mediaPlayer: MediaPlayer? = null
    private var ticSoundId: Int = 0
    private var tacSoundId: Int = 0

    init {
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = android.media.SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()
            
        // Fallback tone generator
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            Log.e("FeedbackGenerator", "Failed to create ToneGenerator", e)
        }
            
        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds.add(sampleId)
                android.util.Log.d("FeedbackGenerator", "Sound loaded successfully: $sampleId")
            } else {
                android.util.Log.e("FeedbackGenerator", "Failed to load sound $sampleId, status: $status")
            }
        }

        try {
            ticSoundId = soundPool?.load(context, R.raw.tic, 1) ?: 0
            tacSoundId = soundPool?.load(context, R.raw.tac, 1) ?: 0
        } catch (e: Exception) {
            android.util.Log.e("FeedbackGenerator", "Error loading sound resources", e)
        }
    }

    /**
     * Play "tic" sound (start of stroke)
     */
    fun playTic() {
        var played = false
        if (ticSoundId != 0 && loadedSounds.contains(ticSoundId)) {
            Log.d("FeedbackGenerator", "Playing TIC via SoundPool")
            val streamId = soundPool?.play(ticSoundId, 1.0f, 1.0f, 1, 0, 1.0f) ?: 0
            if (streamId != 0) played = true
        }
        
        if (!played) {
            android.util.Log.w("FeedbackGenerator", "SoundPool failed for TIC")
            // Removed ToneGenerator fallback to avoid beeps
        }
    }

    /**
     * Play "tac" sound (peak of stroke)
     */
    fun playTac() {
        var played = false
        if (tacSoundId != 0 && loadedSounds.contains(tacSoundId)) {
            Log.d("FeedbackGenerator", "Playing TAC via SoundPool")
            val streamId = soundPool?.play(tacSoundId, 1.0f, 1.0f, 1, 0, 1.0f) ?: 0
            if (streamId != 0) played = true
        }
        
        if (!played) {
            android.util.Log.w("FeedbackGenerator", "SoundPool failed for TAC")
            // Removed ToneGenerator fallback to avoid beeps
        }
    }

    /**
     * Release audio resources
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        toneGenerator?.release()
        toneGenerator = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /**
     * Play audio feedback based on analysis results
     */
    fun playFeedbackAudio(result: AnalysisResult) {
        val isEnabled = settingsManager.isAudioFeedbackEnabled()
        if (!isEnabled) {
            Log.d("FeedbackGenerator", "Audio feedback disabled in settings")
            return
        }
        
        val isShort = settingsManager.getFeedbackType() == 0 // 0 = SHORT
        
        // 1. Filter recommendations by enabled types
        // Note: For now, recommendations in AnalysisResult are strings (keys), 
        // we need to map them back to types or check if they are disabled.
        // A better way is to filter the feedbackItems directly.
        
        val filteredFeedbackItems = result.feedbackItems.filter { item ->
            settingsManager.isCorrectionTypeEnabled(item.type)
        }
        
        Log.d("FeedbackGenerator", "playFeedbackAudio: Type=${if(isShort) "SHORT" else "FULL"}, Recs=${result.recommendations.size}, Filtered=${filteredFeedbackItems.size}")

        if (filteredFeedbackItems.isEmpty()) {
            Log.i("FeedbackGenerator", "All feedback items filtered out by settings or none present.")
            return
        }

        // Pick one (first one for now, as they are usually sorted by significance)
        val bestItem = filteredFeedbackItems.firstOrNull() ?: return
        
        // Use recommendation if it's a recommendation item, otherwise use error
        val key = if (bestItem.isPositive) {
             // Find corresponding recommendation key if possible
             // For now, let's look at result.recommendations that might match
             result.recommendations.firstOrNull() 
        } else {
             bestItem.message
        }

        if (key != null) {
            val resName = if (bestItem.isPositive) {
                if (isShort) "short_$key" else "${key}_full"
            } else {
                if (isShort) "short_$key" else "${key}_full"
            }
            
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            Log.d("FeedbackGenerator", "Attempting playback for: $resName (ID: $resId)")
            if (resId != 0) {
                playRawResource(resId)
            } else {
                Log.e("FeedbackGenerator", "Resource NOT FOUND for: $resName")
            }
        }
    }

    private fun playRawResource(resId: Int) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, resId)
            mediaPlayer?.let {
                Log.d("FeedbackGenerator", "Starting MediaPlayer for resource ID: $resId")
                it.start()
                it.setOnCompletionListener { player ->
                    player.release()
                    if (mediaPlayer == player) mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            Log.e("FeedbackGenerator", "Error playing raw resource", e)
        }
    }
    
    /**
     * Згенерувати короткий фідбек (для аудіо TTS)
     */
    fun generateShortFeedback(result: AnalysisResult): String {
        return when {
            result.overallScore >= 90f -> getRandomPositiveFeedback()
            result.errors.isEmpty() -> context.getString(R.string.feedback_good_keep_up)
            else -> {
                val primaryFeedback = result.feedbackItems.firstOrNull { !it.isPositive }
                if (primaryFeedback != null) {
                    resolveFeedbackItem(primaryFeedback, true)
                } else {
                    context.getString(R.string.feedback_work_technique)
                }
            }
        }
    }

    /**
     * Згенерувати детальний фідбек (для текстового відображення)
     */
    fun generateDetailedFeedback(result: AnalysisResult): String {
        val feedback = StringBuilder()
        val isShort = settingsManager.getFeedbackType() == 0 // 0 = SHORT
        
        feedback.append(getSummary(result.overallScore)).append("\n\n")
        feedback.append(context.getString(R.string.feedback_score_format, result.overallScore.toInt()))
        
        // Filter feedback items by correction type
        val filteredFeedbackItems = result.feedbackItems.filter { item ->
            settingsManager.isCorrectionTypeEnabled(item.type)
        }

        if (filteredFeedbackItems.any { !it.isPositive }) {
            feedback.append(context.getString(R.string.feedback_errors_header))
            filteredFeedbackItems.filter { !it.isPositive }.forEach { item ->
                feedback.append("• ${resolveFeedbackItem(item, isShort)}\n")
            }
            feedback.append("\n")
        }
        
        // recommendations in AnalysisResult are currently String keys. 
        // We'd ideally need a way to map them to CorrectionType to filter, 
        // but for now they usually accompany a FeedbackItem anyway.
        // If we want to be strict, we can skip them or try to match by key.
        if (result.recommendations.isNotEmpty()) {
            val enabledRecommendations = result.recommendations.filter { recKey ->
                // Heuristic: check if any enabled feedback item matches this recommendation
                // or just allow it if it's general. 
                // A better way is to ensure all recommendations have a type.
                // For now, let's keep it as is or filter if we can link them.
                true 
            }
            
            if (enabledRecommendations.isNotEmpty()) {
                feedback.append(context.getString(R.string.feedback_recommendations_header))
                enabledRecommendations.forEach { recKey ->
                    feedback.append("• ${resolveString(recKey, isShort)}\n")
                }
            }
        }
        
        return feedback.toString()
    }

    private fun resolveFeedbackItem(item: FeedbackItem, isShort: Boolean): String {
        return resolveString(item.message, isShort)
    }

    private fun resolveString(key: String, isShort: Boolean): String {
        if (key.isEmpty()) return ""
        
        val resName = if (isShort) {
            // "error_wrist_bent" -> "short_error_wrist_bent"
            // "rec_rotate_more" -> "short_rec_rotate_more"
            val prefix = if (key.startsWith("error_")) "short_" else "short_"
            prefix + key
        } else {
            // "error_wrist_bent" -> "error_wrist_bent_full"
            // "rec_rotate_more" -> "rec_rotate_more_full"
            key + "_full"
        }
        
        val resId = context.resources.getIdentifier(resName, "string", context.packageName)
        return if (resId != 0) {
            context.getString(resId)
        } else {
            // Fallback to key itself if not found
            android.util.Log.e("FeedbackGenerator", "Resource not found: $resName")
            key
        }
    }

    private fun getSummary(score: Float): String {
        return when {
            score >= 90f -> context.getString(R.string.session_excellent)
            score >= 80f -> context.getString(R.string.session_good)
            score >= 70f -> context.getString(R.string.session_improvement)
            else -> context.getString(R.string.feedback_work_on_technique)
        }
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
        if (measuredValue == null) return context.getString(R.string.feedback_param_not_detected, parameterName)
        
        return if (isValid) {
            context.getString(R.string.feedback_param_perfect, parameterName, measuredValue.toInt())
        } else {
            context.getString(R.string.feedback_param_expected, parameterName, measuredValue.toInt(), idealValue.toInt())
        }
    }

    /**
     * Отримати рандомний позитивний фідбек
     */
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

    /**
     * Згенерувати мотиваційне повідомлення
     */
    fun generateMotivationalMessage(consecutiveGoodStrokes: Int): String? {
        return when (consecutiveGoodStrokes) {
            5 -> context.getString(R.string.motivational_5_strokes)
            10 -> context.getString(R.string.motivational_10_strokes)
            20 -> context.getString(R.string.motivational_20_strokes)
            50 -> context.getString(R.string.motivational_50_strokes)
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
        feedback.append(context.getString(R.string.session_summary_title))
        feedback.append(context.getString(R.string.session_total_strokes, totalStrokes))
        feedback.append(context.getString(R.string.session_successful, successfulStrokes))
        feedback.append(context.getString(R.string.session_accuracy, accuracy))
        feedback.append(context.getString(R.string.session_average_score, averageScore.toInt()))
        
        feedback.append(when {
            accuracy >= 80 -> context.getString(R.string.session_excellent)
            accuracy >= 60 -> context.getString(R.string.session_good)
            accuracy >= 40 -> context.getString(R.string.session_improvement)
            else -> context.getString(R.string.session_keep_trying)
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
                context.getString(R.string.improvement_tip_wrist)
            
            mostCommonError.contains("rotation", ignoreCase = true) -> 
                context.getString(R.string.improvement_tip_rotation)
            
            mostCommonError.contains("contact", ignoreCase = true) -> 
                context.getString(R.string.improvement_tip_contact)
            
            mostCommonError.contains("follow", ignoreCase = true) -> 
                context.getString(R.string.improvement_tip_follow)
            
            else -> null
        }
    }

    companion object {
        private const val TAG = "FeedbackGenerator"
    }
}
