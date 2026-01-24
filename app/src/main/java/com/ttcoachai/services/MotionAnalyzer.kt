/*
 * AI Coach for Table Tennis
 * Motion Analyzer - Rule-based техніки аналізу
 */

package com.ttcoachai.services

import com.ttcoachai.models.AnalysisResult
import com.ttcoachai.models.CorrectionType
import com.ttcoachai.models.ExerciseParameters
import com.ttcoachai.models.FeedbackItem
import com.ttcoachai.models.StrokePhase
import com.ttcoachai.models.TechniqueErrors
import com.ttcoachai.models.TechniqueRecommendations
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
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
        
        // Prefer world landmarks (3D in meters) for better accuracy
        val landmarks = if (poseLandmarkerResult.worldLandmarks().isNotEmpty()) {
            poseLandmarkerResult.worldLandmarks()[0]
        } else {
            poseLandmarkerResult.landmarks()[0]
        }
        
        return analyzeStroke(landmarks, phase)
    }

    /**
     * Дані з виміряними параметрами удару
     */
    private data class StrokeMetrics(
        val wristAngle: Float?,
        val bodyRotation: Float?,
        val followThroughAngle: Float?,
        val contactHeight: Float?,
        val elbowBodyDistance: Float?
    )

    /**
     * Аналізувати техніку удару на основі raw landmarks
     */
    fun analyzeStroke(
        landmarks: List<Any>, // Can be NormalizedLandmark or Landmark
        phase: StrokePhase = StrokePhase.CONTACT
    ): AnalysisResult {
        if (landmarks.isEmpty()) {
            return AnalysisResult(
                errors = listOf("Не вдалося виявити позу - перевірте освітлення та позицію камери")
            )
        }

        // 1. Calculate metrics
        val metrics = extractMetrics(landmarks)

        // 2. Validate metrics and generate feedback items
        val validations = performValidations(metrics)
        val feedbackItems = validations.filter { !it.isValid }.map { it.feedback }
        val errors = feedbackItems.map { it.message }
        val recommendations = validations.filter { !it.isValid }.map { it.recommendation }

        // 3. Calculate score
        val validCount = validations.count { it.isValid }
        val totalChecks = validations.size
        val overallScore = if (totalChecks > 0) (validCount.toFloat() / totalChecks) * 100f else 0f

        // 4. Add positive feedback if score is high
        val finalFeedbackItems = feedbackItems.toMutableList()
        if (overallScore >= 90f) {
            finalFeedbackItems.add(FeedbackItem("Чудова техніка!", CorrectionType.GENERAL, true))
        }

        return AnalysisResult(
            wristAngle = metrics.wristAngle,
            bodyRotation = metrics.bodyRotation,
            followThroughAngle = metrics.followThroughAngle,
            contactHeight = metrics.contactHeight,
            elbowBodyDistance = metrics.elbowBodyDistance,
            isWristAngleValid = validations.find { it.type == CorrectionType.WRIST }?.isValid ?: false,
            isBodyRotationValid = validations.find { it.type == CorrectionType.BODY_ROTATION }?.isValid ?: false,
            isFollowThroughValid = validations.find { it.type == CorrectionType.FOLLOW_THROUGH }?.isValid ?: false,
            isContactHeightValid = validations.find { it.type == CorrectionType.CONTACT_HEIGHT }?.isValid ?: false,
            isElbowPositionValid = validations.find { it.type == CorrectionType.ELBOW_POSITION }?.isValid ?: false,
            overallScore = overallScore,
            phase = phase,
            errors = errors,
            recommendations = recommendations,
            feedbackItems = finalFeedbackItems
        )
    }

    private fun extractMetrics(landmarks: List<Any>): StrokeMetrics {
        return StrokeMetrics(
            wristAngle = calculateWristAngle(landmarks),
            bodyRotation = calculateBodyRotation(landmarks),
            followThroughAngle = calculateFollowThroughAngle(landmarks),
            contactHeight = calculateContactHeight(landmarks),
            elbowBodyDistance = calculateElbowBodyDistance(landmarks)
        )
    }

    private data class ValidationResult(
        val type: CorrectionType,
        val isValid: Boolean,
        val feedback: FeedbackItem,
        val recommendation: String
    )

    private fun performValidations(metrics: StrokeMetrics): List<ValidationResult> {
        return listOfNotNull(
            validateWrist(metrics.wristAngle),
            validateRotation(metrics.bodyRotation),
            validateFollowThrough(metrics.followThroughAngle),
            validateHeight(metrics.contactHeight),
            validateElbow(metrics.elbowBodyDistance)
        )
    }

    private fun validateWrist(angle: Float?): ValidationResult? = angle?.let {
        val isValid = parameters.isWristAngleValid(it)
        ValidationResult(
            CorrectionType.WRIST,
            isValid,
            FeedbackItem(TechniqueErrors.WRIST_BENT, CorrectionType.WRIST),
            TechniqueRecommendations.STRAIGHTEN_WRIST
        )
    }

    private fun validateRotation(rotation: Float?): ValidationResult? = rotation?.let {
        val isValid = parameters.isBodyRotationValid(it)
        ValidationResult(
            CorrectionType.BODY_ROTATION,
            isValid,
            FeedbackItem(TechniqueErrors.LOW_ROTATION, CorrectionType.BODY_ROTATION),
            TechniqueRecommendations.ROTATE_MORE
        )
    }

    private fun validateFollowThrough(angle: Float?): ValidationResult? = angle?.let {
        val isValid = parameters.isFollowThroughValid(it)
        ValidationResult(
            CorrectionType.FOLLOW_THROUGH,
            isValid,
            FeedbackItem(TechniqueErrors.NO_FOLLOW_THROUGH, CorrectionType.FOLLOW_THROUGH),
            TechniqueRecommendations.COMPLETE_FOLLOW_THROUGH
        )
    }

    private fun validateHeight(height: Float?): ValidationResult? = height?.let {
        val isValid = parameters.isContactHeightValid(it)
        val errorMsg = if (height > parameters.contactHeightMax) TechniqueErrors.HIGH_CONTACT else TechniqueErrors.LOW_CONTACT
        ValidationResult(
            CorrectionType.CONTACT_HEIGHT,
            isValid,
            FeedbackItem(errorMsg, CorrectionType.CONTACT_HEIGHT),
            TechniqueRecommendations.ADJUST_CONTACT_HEIGHT
        )
    }

    private fun validateElbow(distance: Float?): ValidationResult? = distance?.let {
        val tooFar = !parameters.isElbowPositionValid(it)
        val tooClose = !parameters.isElbowNotTooClose(it)
        val isValid = !tooFar && !tooClose
        
        val errorMsg = when {
            tooFar -> TechniqueErrors.ELBOW_FAR
            tooClose -> TechniqueErrors.ELBOW_CLOSE
            else -> ""
        }
        
        val recommendation = when {
            tooFar -> TechniqueRecommendations.KEEP_ELBOW_CLOSE
            tooClose -> TechniqueRecommendations.MOVE_ELBOW_AWAY
            else -> ""
        }
        
        ValidationResult(
            CorrectionType.ELBOW_POSITION,
            isValid,
            FeedbackItem(errorMsg, CorrectionType.ELBOW_POSITION),
            recommendation
        )
    }

    /**
     * Розрахувати кут зап'ястя (лікоть-зап'ястя-кисть)
     */
    private fun calculateWristAngle(landmarks: List<Any>): Float? {
        try {
            // Right Hand: shoulder=12, elbow=14, wrist=16, index=20
            val elbow = getLandmark(landmarks, 14) ?: return null
            val wrist = getLandmark(landmarks, 16) ?: return null
            val index = getLandmark(landmarks, 20) ?: return null
            
            return calculate3DAngle(elbow, wrist, index)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Розрахувати ротацію корпусу (плечі відносно стегон)
     */
    private fun calculateBodyRotation(landmarks: List<Any>): Float? {
        try {
            // Shoulders: left=11, right=12
            // Hips: left=23, right=24
            val leftShoulder = getLandmark(landmarks, 11) ?: return null
            val rightShoulder = getLandmark(landmarks, 12) ?: return null
            val leftHip = getLandmark(landmarks, 23) ?: return null
            val rightHip = getLandmark(landmarks, 24) ?: return null
            
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
    private fun calculateFollowThroughAngle(landmarks: List<Any>): Float? {
        try {
            val shoulder = getLandmark(landmarks, 12) ?: return null
            val elbow = getLandmark(landmarks, 14) ?: return null
            val wrist = getLandmark(landmarks, 16) ?: return null
            
            return calculate3DAngle(shoulder, elbow, wrist)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Розрахувати відносну висоту контакту
     */
    private fun calculateContactHeight(landmarks: List<Any>): Float? {
        try {
            val wrist = getLandmark(landmarks, 16) ?: return null
            val hip = getLandmark(landmarks, 24) ?: return null
            
            // Relative height: 0 = hip level, 1 = shoulder height
            return 1f - wrist.y() / hip.y()
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Розрахувати відстань між лікоть та тілом
     */
    private fun calculateElbowBodyDistance(landmarks: List<Any>): Float? {
        try {
            val elbow = getLandmark(landmarks, 14) ?: return null
            val hip = getLandmark(landmarks, 24) ?: return null
            
            val dx = elbow.x() - hip.x()
            val dy = elbow.y() - hip.y()
            
            return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Get landmark safely and handle both types
     */
    private fun getLandmark(landmarks: List<Any>, index: Int): com.google.mediapipe.tasks.components.containers.Landmark? {
        if (index >= landmarks.size) return null
        val item = landmarks[index]
        return if (item is com.google.mediapipe.tasks.components.containers.Landmark) {
            item
        } else if (item is com.google.mediapipe.tasks.components.containers.NormalizedLandmark) {
            com.google.mediapipe.tasks.components.containers.Landmark.create(item.x(), item.y(), item.z(), item.visibility(), item.presence())
        } else {
            null
        }
    }

    /**
     * Calculate 3D angle between three points (A-B-C)
     */
    private fun calculate3DAngle(
        a: com.google.mediapipe.tasks.components.containers.Landmark,
        b: com.google.mediapipe.tasks.components.containers.Landmark,
        c: com.google.mediapipe.tasks.components.containers.Landmark
    ): Float {
        // Vector BA
        val bax = a.x() - b.x()
        val bay = a.y() - b.y()
        val baz = a.z() - b.z()
        
        // Vector BC
        val bcx = c.x() - b.x()
        val bcy = c.y() - b.y()
        val bcz = c.z() - b.z()
        
        val dotProduct = bax * bcx + bay * bcy + baz * bcz
        val magBA = Math.sqrt((bax * bax + bay * bay + baz * baz).toDouble())
        val magBC = Math.sqrt((bcx * bcx + bcy * bcy + bcz * bcz).toDouble())
        
        val cosAngle = dotProduct / (magBA * magBC)
        return Math.toDegrees(Math.acos(cosAngle.coerceIn(-1.0, 1.0))).toFloat()
    }

    /**
     * Calculate angle between three points (A-B-C) in 2D
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
