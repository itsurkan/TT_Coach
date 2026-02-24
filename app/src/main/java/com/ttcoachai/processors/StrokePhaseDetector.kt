package com.ttcoachai.processors

import com.ttcoachai.mappers.MediaPipeMapper
import com.ttcoachai.services.FeedbackGenerator
import com.ttcoachai.shared.models.StrokePhase
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Android wrapper for the shared StrokePhaseDetector.
 * Converts MediaPipe PoseLandmarkerResult to shared Landmark3D via MediaPipeMapper,
 * then delegates to com.ttcoachai.shared.detection.StrokePhaseDetector.
 */
class StrokePhaseDetector(
    private val feedbackGenerator: FeedbackGenerator
) {
    private val sharedDetector = com.ttcoachai.shared.detection.StrokePhaseDetector()

    data class DetectionResult(
        val phase: StrokePhase,
        val isPhaseTransition: Boolean
    )

    fun detect(
        poseLandmarkerResult: PoseLandmarkerResult
    ): DetectionResult {
        val previousPhase = sharedDetector.getCurrentPhase()
        val landmarks = MediaPipeMapper.toWorldLandmarkList(poseLandmarkerResult)
        val newPhase = sharedDetector.detect(landmarks, poseLandmarkerResult.timestampMs())

        val isTransition = newPhase != previousPhase
        if (isTransition) {
            feedbackGenerator.handlePhaseTransition(previousPhase, newPhase)
        }

        return DetectionResult(newPhase, isTransition)
    }

    fun reset() {
        sharedDetector.reset()
    }

    fun getCurrentPhase(): StrokePhase = sharedDetector.getCurrentPhase()
}
