/*
 * AI Coach for Table Tennis
 * Motion Analyzer - Rule-based техніки аналізу
 */

package com.google.mediapipe.examples.poselandmarker.services

import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.ExerciseParameters
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
import com.google.mediapipe.examples.poselandmarker.models.TechniqueErrors
import com.google.mediapipe.examples.poselandmarker.models.TechniqueRecommendations
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Аналізатор руху для настільного тенісу
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

        val landmarks = poseLandmarkerResult.landmarks()[0]
        
        // Розрахунок кутів та параметрів
        val wristAngle = calculateWristAngle(landmarks)
        val bodyRotation = calculateBodyRotation(landmarks)
        val followThroughAngle = calculateFollowThroughAngle(landmarks)
        val contactHeight = calculateContactHeight(landmarks)
        val elbowBodyDistance = calculateElbowBodyDistance(landmarks)
        
        // Перевірка валідності кожного параметру
        val isWristValid = wristAngle?.let { parameters.isWristAngleValid(it) } ?: false
        val isRotationValid = bodyRotation?.let { parameters.isBodyRotationValid(it) } ?: false
        val isFollowThroughValid = followThroughAngle?.let { parameters.isFollowThroughValid(it) } ?: false
        val isHeightValid = contactHeight?.let { parameters.isContactHeightValid(it) } ?: false
        val isElbowValid = elbowBodyDistance?.let { parameters.isElbowPositionValid(it) } ?: false
        
        // Збір помилок та рекомендацій
        val errors = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        if (!isWristValid) {
            errors.add(TechniqueErrors.WRIST_BENT)
            recommendations.add(TechniqueRecommendations.STRAIGHTEN_WRIST)
        }
        
        if (!isRotationValid) {
            errors.add(TechniqueErrors.LOW_ROTATION)
            recommendations.add(TechniqueRecommendations.ROTATE_MORE)
        }
        
        if (!isFollowThroughValid) {
            errors.add(TechniqueErrors.NO_FOLLOW_THROUGH)
            recommendations.add(TechniqueRecommendations.COMPLETE_FOLLOW_THROUGH)
        }
        
        if (!isHeightValid) {
            contactHeight?.let {
                if (it > parameters.contactHeightMax) {
                    errors.add(TechniqueErrors.HIGH_CONTACT)
                } else {
                    errors.add(TechniqueErrors.LOW_CONTACT)
                }
            }
            recommendations.add(TechniqueRecommendations.ADJUST_CONTACT_HEIGHT)
        }
        
        if (!isElbowValid) {
            errors.add(TechniqueErrors.ELBOW_FAR)
            recommendations.add(TechniqueRecommendations.KEEP_ELBOW_CLOSE)
        }
        
        // Розрахунок загального скору
        val validCount = listOf(
            isWristValid, isRotationValid, isFollowThroughValid, 
            isHeightValid, isElbowValid
        ).count { it }
        val overallScore = (validCount.toFloat() / 5f) * 100f
        
        return AnalysisResult(
            wristAngle = wristAngle,
            bodyRotation = bodyRotation,
            followThroughAngle = followThroughAngle,
            contactHeight = contactHeight,
            elbowBodyDistance = elbowBodyDistance,
            isWristAngleValid = isWristValid,
            isBodyRotationValid = isRotationValid,
            isFollowThroughValid = isFollowThroughValid,
            isContactHeightValid = isHeightValid,
            isElbowPositionValid = isElbowValid,
            overallScore = overallScore,
            phase = phase,
            errors = errors,
            recommendations = recommendations
        )
    }

    /**
     * Розрахувати кут зап'ястя (лікоть-зап'ястя-кисть)
     */
    private fun calculateWristAngle(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float? {
        try {
            // Індекси для правої руки: плече=12, лікоть=14, зап'ястя=16
            val elbow = landmarks[14]
            val wrist = landmarks[16]
            val index = landmarks[20] // індексний палець
            
            return calculateAngle(
                elbow.x(), elbow.y(),
                wrist.x(), wrist.y(),
                index.x(), index.y()
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Розрахувати ротацію корпусу (плечі відносно стегон)
     */
    private fun calculateBodyRotation(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float? {
        try {
            // Плечі: ліве=11, праве=12
            // Стегна: ліве=23, праве=24
            val leftShoulder = landmarks[11]
            val rightShoulder = landmarks[12]
            val leftHip = landmarks[23]
            val rightHip = landmarks[24]
            
            val shoulderAngle = Math.toDegrees(
                Math.atan2(
                    (rightShoulder.y() - leftShoulder.y()).toDouble(),
                    (rightShoulder.x() - leftShoulder.x()).toDouble()
                )
            ).toFloat()
            
            val hipAngle = Math.toDegrees(
                Math.atan2(
                    (rightHip.y() - leftHip.y()).toDouble(),
                    (rightHip.x() - leftHip.x()).toDouble()
                )
            ).toFloat()
            
            return Math.abs(shoulderAngle - hipAngle)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Розрахувати кут проведення (follow-through)
     */
    private fun calculateFollowThroughAngle(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float? {
        try {
            val shoulder = landmarks[12]
            val elbow = landmarks[14]
            val wrist = landmarks[16]
            
            return calculateAngle(
                shoulder.x(), shoulder.y(),
                elbow.x(), elbow.y(),
                wrist.x(), wrist.y()
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Розрахувати відносну висоту контакту
     */
    private fun calculateContactHeight(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float? {
        try {
            val wrist = landmarks[16]
            val hip = landmarks[24]
            
            // Відносна висота: 0 = рівень стегон, 1 = на висоті плечей
            return 1f - wrist.y() / hip.y()
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Розрахувати відстань між лікоть та тілом
     */
    private fun calculateElbowBodyDistance(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float? {
        try {
            val elbow = landmarks[14]
            val hip = landmarks[24]
            
            val dx = elbow.x() - hip.x()
            val dy = elbow.y() - hip.y()
            
            return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Розрахувати кут між трьома точками (A-B-C)
     */
    private fun calculateAngle(
        ax: Float, ay: Float,
        bx: Float, by: Float,
        cx: Float, cy: Float
    ): Float {
        val ba = floatArrayOf(ax - bx, ay - by)
        val bc = floatArrayOf(cx - bx, cy - by)
        
        val dotProduct = ba[0] * bc[0] + ba[1] * bc[1]
        val magnitudeBA = Math.sqrt((ba[0] * ba[0] + ba[1] * ba[1]).toDouble())
        val magnitudeBC = Math.sqrt((bc[0] * bc[0] + bc[1] * bc[1]).toDouble())
        
        val cosAngle = dotProduct / (magnitudeBA * magnitudeBC)
        return Math.toDegrees(Math.acos(cosAngle.coerceIn(-1.0, 1.0))).toFloat()
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
