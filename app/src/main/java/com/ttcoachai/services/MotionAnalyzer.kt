/*
 * AI Coach for Table Tennis
 * Motion Analyzer - Delegates to shared StrokeAnalyzer
 */

package com.ttcoachai.services

import com.ttcoachai.mappers.MediaPipeMapper
import com.ttcoachai.shared.analysis.StrokeAnalyzer
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.ExerciseParameters
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.StrokePhase
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Аналізатор руху для настільного тенісу.
 * Delegates all analysis to the shared StrokeAnalyzer.
 * MediaPipe types are converted at the boundary via MediaPipeMapper.
 */
class MotionAnalyzer(
    private val parameters: ExerciseParameters
) {

    /**
     * Аналізувати техніку удару на основі MediaPipe keypoints
     */
    fun analyzeStroke(
        poseLandmarkerResult: PoseLandmarkerResult?,
        phase: StrokePhase = StrokePhase.CONTACT
    ): AnalysisResult {
        if (poseLandmarkerResult == null || poseLandmarkerResult.landmarks().isEmpty()) {
            return AnalysisResult(
                errors = listOf("Не вдалося виявити позу - перевірте освітлення та позицію камери")
            )
        }

        // Prefer world landmarks (3D in meters) for better accuracy
        val landmarks = MediaPipeMapper.toWorldLandmarkList(poseLandmarkerResult)
        return StrokeAnalyzer.analyzeStroke(landmarks, parameters, phase)
    }

    /**
     * Аналізувати техніку удару на основі raw Landmark3D list
     */
    fun analyzeStroke(
        landmarks: List<Landmark3D>,
        phase: StrokePhase = StrokePhase.CONTACT
    ): AnalysisResult {
        return StrokeAnalyzer.analyzeStroke(landmarks, parameters, phase)
    }

    /**
     * Генерувати текстовий фідбек на основі результату аналізу
     */
    fun generateFeedback(result: AnalysisResult): String {
        return when {
            result.overallScore >= 90f -> "✅ Чудово! Ідеальна техніка!"
            result.errors.isEmpty() -> "✅ Гарний удар! Продовжуйте!"
            else -> "⚠️ ${result.getPrimaryError() ?: "Працюйте над технікою"}"
        }
    }
}
