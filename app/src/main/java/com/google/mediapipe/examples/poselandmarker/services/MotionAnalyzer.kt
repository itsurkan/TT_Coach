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
        return analyzeStroke(poseLandmarkerResult.landmarks()[0], phase)
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
        landmarks: List<NormalizedLandmark>,
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

    private fun extractMetrics(landmarks: List<NormalizedLandmark>): StrokeMetrics {
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
