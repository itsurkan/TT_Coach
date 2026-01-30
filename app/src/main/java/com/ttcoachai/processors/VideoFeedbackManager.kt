package com.ttcoachai.processors

import android.util.Log
import com.ttcoachai.models.AnalysisResult
import com.ttcoachai.models.StrokePhase
import com.ttcoachai.services.FeedbackGenerator

/**
 * Manages audio feedback sequencing during debug video playback.
 */
class VideoFeedbackManager(private val feedbackGenerator: FeedbackGenerator) {
    private var lastPhase: StrokePhase? = null
    private var pendingFeedback: AnalysisResult? = null
    private var totalStrokes = 0
    private val strokeIndices = mutableListOf<Int>()

    fun processFrame(index: Int, result: AnalysisResult) {
        val phase = result.phase
        if (phase != StrokePhase.READY && !strokeIndices.contains(index)) strokeIndices.add(index)

        // Play tic/tac on phase transitions during playback
        feedbackGenerator.handlePhaseTransition(lastPhase ?: StrokePhase.READY, phase)

        if (phase == StrokePhase.CONTACT) {
            pendingFeedback = result
        }

        if (lastPhase == StrokePhase.FOLLOW_THROUGH && phase != StrokePhase.FOLLOW_THROUGH) {
            if (strokeIndices.isNotEmpty()) {
                totalStrokes++
                pendingFeedback?.let {
                    val freq = feedbackGenerator.getSettingsManager().getFeedbackFrequency()
                    if (totalStrokes > 0 && totalStrokes % freq == 0) feedbackGenerator.playFeedbackAudio(it)
                    pendingFeedback = null
                }
                strokeIndices.clear()
            }
        }
        lastPhase = phase
    }

    fun reset() {
        lastPhase = null
        pendingFeedback = null
        totalStrokes = 0
        strokeIndices.clear()
    }
}
