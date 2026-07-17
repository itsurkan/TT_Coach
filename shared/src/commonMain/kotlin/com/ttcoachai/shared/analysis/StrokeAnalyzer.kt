/*
 * AI Coach for Table Tennis
 * Stroke Analyzer — Orchestrator: landmarks → AnalysisResult
 */

package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.ExerciseParameters
import com.ttcoachai.shared.models.FeedbackItem
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.StrokePhase
import com.ttcoachai.shared.models.TechniqueErrors
import com.ttcoachai.shared.models.TechniqueRecommendations

/**
 * Platform-independent stroke analysis orchestrator.
 * Calls AngleCalculations + MetricCalculations + ExerciseParameters validation
 * to produce an AnalysisResult.
 */
object StrokeAnalyzer {

    private data class StrokeMetrics(
        val wristAngle: Float?,
        val bodyRotation: Float?,
        val followThroughAngle: Float?,
        val contactHeight: Float?,
        val elbowBodyDistance: Float?,
        val kneeAngle: Float?
    )

    private data class ValidationResult(
        val type: CorrectionType,
        val isValid: Boolean,
        val feedback: FeedbackItem,
        val recommendation: String
    )

    /**
     * Analyze a single frame of pose landmarks against exercise parameters.
     *
     * @param landmarks List of 33 Landmark3D points from a single frame
     * @param parameters Exercise-specific thresholds
     * @param phase Current stroke phase (from StrokePhaseDetector)
     * @return AnalysisResult with all computed metrics and validation flags
     */
    fun analyzeStroke(
        landmarks: List<Landmark3D>,
        parameters: ExerciseParameters,
        phase: StrokePhase = StrokePhase.READY
    ): AnalysisResult {
        if (landmarks.isEmpty()) {
            return AnalysisResult(
                errors = listOf("Не вдалося виявити позу - перевірте освітлення та позицію камери")
            )
        }

        // 1. Calculate metrics
        val metrics = extractMetrics(landmarks)

        // 2. Validate metrics and generate feedback items
        val validations = performValidations(metrics, parameters, phase)
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
            kneeAngle = metrics.kneeAngle,
            isWristAngleValid = validations.find { it.type == CorrectionType.WRIST }?.isValid ?: false,
            isBodyRotationValid = validations.find { it.type == CorrectionType.BODY_ROTATION }?.isValid ?: false,
            isFollowThroughValid = validations.find { it.type == CorrectionType.FOLLOW_THROUGH }?.isValid ?: false,
            isContactHeightValid = validations.find { it.type == CorrectionType.CONTACT_HEIGHT }?.isValid ?: false,
            isElbowPositionValid = validations.find { it.type == CorrectionType.ELBOW_POSITION }?.isValid ?: false,
            isKneeBendValid = validations.find { it.type == CorrectionType.KNEE_BEND }?.isValid ?: false,
            overallScore = overallScore,
            phase = phase,
            errors = errors,
            recommendations = recommendations,
            feedbackItems = finalFeedbackItems
        )
    }

    private fun extractMetrics(landmarks: List<Landmark3D>): StrokeMetrics {
        return StrokeMetrics(
            wristAngle = AngleCalculations.calculateWristAngle(landmarks),
            bodyRotation = AngleCalculations.calculateBodyRotation(landmarks),
            followThroughAngle = AngleCalculations.calculateFollowThroughAngle(landmarks),
            contactHeight = MetricCalculations.calculateContactHeight(landmarks),
            elbowBodyDistance = MetricCalculations.calculateElbowBodyDistance(landmarks),
            kneeAngle = calculateKneeAngle(landmarks)
        )
    }

    /**
     * Calculate knee bend angle using landmarks:
     *   24 = right hip, 26 = right knee, 28 = right ankle
     * 180° = straight leg. Returns null when required landmark indices are out of bounds.
     */
    private fun calculateKneeAngle(landmarks: List<Landmark3D>): Float? {
        val hip = landmarks.getOrNull(24) ?: return null
        val knee = landmarks.getOrNull(26) ?: return null
        val ankle = landmarks.getOrNull(28) ?: return null

        val angle = AngleCalculations.calculate3DAngle(hip, knee, ankle)
        return if (angle.isNaN() || angle.isInfinite()) null else angle
    }

    private fun performValidations(
        metrics: StrokeMetrics,
        parameters: ExerciseParameters,
        phase: StrokePhase
    ): List<ValidationResult> {
        return listOfNotNull(
            validateWrist(metrics.wristAngle, parameters),
            validateRotation(metrics.bodyRotation, parameters),
            validateFollowThrough(metrics.followThroughAngle, parameters),
            validateHeight(metrics.contactHeight, parameters),
            validateElbow(metrics.elbowBodyDistance, parameters),
            validateKnees(metrics.kneeAngle, parameters, phase)
        )
    }

    private fun validateWrist(angle: Float?, parameters: ExerciseParameters): ValidationResult? = angle?.let {
        val isValid = parameters.isWristAngleValid(it)
        ValidationResult(
            CorrectionType.WRIST,
            isValid,
            FeedbackItem(TechniqueErrors.WRIST_BENT, CorrectionType.WRIST),
            TechniqueRecommendations.STRAIGHTEN_WRIST
        )
    }

    private fun validateRotation(rotation: Float?, parameters: ExerciseParameters): ValidationResult? = rotation?.let {
        val isValid = parameters.isBodyRotationValid(it)
        ValidationResult(
            CorrectionType.BODY_ROTATION,
            isValid,
            FeedbackItem(TechniqueErrors.LOW_ROTATION, CorrectionType.BODY_ROTATION),
            TechniqueRecommendations.ROTATE_MORE
        )
    }

    private fun validateFollowThrough(angle: Float?, parameters: ExerciseParameters): ValidationResult? = angle?.let {
        val isValid = parameters.isFollowThroughValid(it)
        ValidationResult(
            CorrectionType.FOLLOW_THROUGH,
            isValid,
            FeedbackItem(TechniqueErrors.NO_FOLLOW_THROUGH, CorrectionType.FOLLOW_THROUGH),
            TechniqueRecommendations.COMPLETE_FOLLOW_THROUGH
        )
    }

    private fun validateHeight(height: Float?, parameters: ExerciseParameters): ValidationResult? = height?.let {
        val isValid = parameters.isContactHeightValid(it)
        val errorMsg = if (height > parameters.contactHeightMax) TechniqueErrors.HIGH_CONTACT else TechniqueErrors.LOW_CONTACT
        ValidationResult(
            CorrectionType.CONTACT_HEIGHT,
            isValid,
            FeedbackItem(errorMsg, CorrectionType.CONTACT_HEIGHT),
            TechniqueRecommendations.ADJUST_CONTACT_HEIGHT
        )
    }

    private fun validateElbow(distance: Float?, parameters: ExerciseParameters): ValidationResult? = distance?.let {
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

    private fun validateKnees(
        angle: Float?,
        parameters: ExerciseParameters,
        phase: StrokePhase
    ): ValidationResult? = angle?.let {
        val isBackswingPhase = phase == StrokePhase.READY || phase == StrokePhase.BACKSWING
        val min = if (isBackswingPhase) parameters.kneeBendBackswingMin else parameters.kneeBendStrikeMin
        val max = if (isBackswingPhase) parameters.kneeBendBackswingMax else parameters.kneeBendStrikeMax
        val isValid = if (isBackswingPhase) {
            parameters.isKneeBendBackswingValid(it)
        } else {
            parameters.isKneeBendStrikeValid(it)
        }

        val errorMsg = when {
            it > max -> TechniqueErrors.STRAIGHT_LEGS
            it < min -> TechniqueErrors.LEGS_TOO_BENT
            else -> ""
        }

        val recommendation = when {
            it > max -> TechniqueRecommendations.BEND_KNEES
            it < min -> TechniqueRecommendations.RISE_STANCE
            else -> ""
        }

        ValidationResult(
            CorrectionType.KNEE_BEND,
            isValid,
            FeedbackItem(errorMsg, CorrectionType.KNEE_BEND),
            recommendation
        )
    }
}
