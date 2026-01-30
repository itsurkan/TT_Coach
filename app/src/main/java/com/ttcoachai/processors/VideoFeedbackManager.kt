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

        if (lastPhase == StrokePhase.BACKSWING && phase == StrokePhase.FORWARD_SWING) {
            pendingFeedback?.let {
                val freq = feedbackGenerator.getSettingsManager().getFeedbackFrequency()
                if (totalStrokes > 0 && totalStrokes % freq == 0) feedbackGenerator.playFeedbackAudio(it)
                pendingFeedback = null
            }
        }

        if (lastPhase == StrokePhase.FOLLOW_THROUGH && phase != StrokePhase.FOLLOW_THROUGH) {
            if (strokeIndices.isNotEmpty()) {
                totalStrokes++
                // Logic to find best result in strokeIndices would go here if we had access to all results
                // Simplified for now: just take the current result as pending if it's CONTACT
                if (result.phase == StrokePhase.CONTACT) pendingFeedback = result
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
