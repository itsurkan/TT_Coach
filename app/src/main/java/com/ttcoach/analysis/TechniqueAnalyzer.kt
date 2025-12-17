package com.ttcoach.analysis

import android.graphics.PointF
import com.ttcoach.cv.KeyPoint
import kotlin.math.*

/**
 * Analyzes pose technique by calculating angles and comparing with ideal model
 */
class TechniqueAnalyzer {
    
    // MediaPipe Pose key point indices (33 key points)
    companion object {
        // Upper body key points
        private const val LEFT_SHOULDER = 11
        private const val RIGHT_SHOULDER = 12
        private const val LEFT_ELBOW = 13
        private const val RIGHT_ELBOW = 14
        private const val LEFT_WRIST = 15
        private const val RIGHT_WRIST = 16
        
        // Body rotation points
        private const val LEFT_HIP = 23
        private const val RIGHT_HIP = 24
        
        // Ideal angle thresholds (in degrees)
        private const val IDEAL_ELBOW_ANGLE_MIN = 140f
        private const val IDEAL_ELBOW_ANGLE_MAX = 160f
        private const val IDEAL_SHOULDER_ANGLE_MIN = 80f
        private const val IDEAL_SHOULDER_ANGLE_MAX = 100f
        private const val IDEAL_BODY_ROTATION_MIN = 10f
        private const val IDEAL_BODY_ROTATION_MAX = 30f
    }
    
    /**
     * Calculate elbow angle from shoulder-elbow-wrist points
     * @param keyPoints List of 33 key points from MediaPipe
     * @param useRightArm Whether to use right arm (true) or left arm (false)
     * @return Elbow angle in degrees, or null if points are not visible
     */
    fun calculateElbowAngle(keyPoints: List<KeyPoint>, useRightArm: Boolean = true): Float? {
        val shoulderIdx = if (useRightArm) RIGHT_SHOULDER else LEFT_SHOULDER
        val elbowIdx = if (useRightArm) RIGHT_ELBOW else LEFT_ELBOW
        val wristIdx = if (useRightArm) RIGHT_WRIST else LEFT_WRIST
        
        if (keyPoints.size <= maxOf(shoulderIdx, elbowIdx, wristIdx)) {
            return null
        }
        
        val shoulder = keyPoints[shoulderIdx]
        val elbow = keyPoints[elbowIdx]
        val wrist = keyPoints[wristIdx]
        
        // Check visibility threshold
        if (shoulder.visibility < 0.5f || elbow.visibility < 0.5f || wrist.visibility < 0.5f) {
            return null
        }
        
        return calculateAngle(
            PointF(shoulder.x, shoulder.y),
            PointF(elbow.x, elbow.y),
            PointF(wrist.x, wrist.y)
        )
    }
    
    /**
     * Calculate shoulder angle
     * @param keyPoints List of 33 key points from MediaPipe
     * @param useRightArm Whether to use right arm (true) or left arm (false)
     * @return Shoulder angle in degrees, or null if points are not visible
     */
    fun calculateShoulderAngle(keyPoints: List<KeyPoint>, useRightArm: Boolean = true): Float? {
        val shoulderIdx = if (useRightArm) RIGHT_SHOULDER else LEFT_SHOULDER
        val elbowIdx = if (useRightArm) RIGHT_ELBOW else LEFT_ELBOW
        val hipIdx = if (useRightArm) RIGHT_HIP else LEFT_HIP
        
        if (keyPoints.size <= maxOf(shoulderIdx, elbowIdx, hipIdx)) {
            return null
        }
        
        val shoulder = keyPoints[shoulderIdx]
        val elbow = keyPoints[elbowIdx]
        val hip = keyPoints[hipIdx]
        
        // Check visibility threshold
        if (shoulder.visibility < 0.5f || elbow.visibility < 0.5f || hip.visibility < 0.5f) {
            return null
        }
        
        return calculateAngle(
            PointF(hip.x, hip.y),
            PointF(shoulder.x, shoulder.y),
            PointF(elbow.x, elbow.y)
        )
    }
    
    /**
     * Calculate body rotation angle (shoulder line relative to hip line)
     * @param keyPoints List of 33 key points from MediaPipe
     * @return Body rotation angle in degrees, or null if points are not visible
     */
    fun calculateBodyRotation(keyPoints: List<KeyPoint>): Float? {
        if (keyPoints.size <= maxOf(LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP)) {
            return null
        }
        
        val leftShoulder = keyPoints[LEFT_SHOULDER]
        val rightShoulder = keyPoints[RIGHT_SHOULDER]
        val leftHip = keyPoints[LEFT_HIP]
        val rightHip = keyPoints[RIGHT_HIP]
        
        // Check visibility threshold
        if (leftShoulder.visibility < 0.5f || rightShoulder.visibility < 0.5f ||
            leftHip.visibility < 0.5f || rightHip.visibility < 0.5f) {
            return null
        }
        
        // Calculate shoulder line angle
        val shoulderAngle = atan2(
            rightShoulder.y - leftShoulder.y,
            rightShoulder.x - leftShoulder.x
        ) * 180f / PI.toFloat()
        
        // Calculate hip line angle
        val hipAngle = atan2(
            rightHip.y - leftHip.y,
            rightHip.x - leftHip.x
        ) * 180f / PI.toFloat()
        
        // Body rotation is the difference
        val rotation = shoulderAngle - hipAngle
        
        // Normalize to -180 to 180 range
        return when {
            rotation > 180f -> rotation - 360f
            rotation < -180f -> rotation + 360f
            else -> rotation
        }
    }
    
    /**
     * Calculate angle between three points (point2 is the vertex)
     */
    private fun calculateAngle(point1: PointF, vertex: PointF, point2: PointF): Float {
        val v1 = PointF(point1.x - vertex.x, point1.y - vertex.y)
        val v2 = PointF(point2.x - vertex.x, point2.y - vertex.y)
        
        val dot = v1.x * v2.x + v1.y * v2.y
        val mag1 = sqrt(v1.x * v1.x + v1.y * v1.y)
        val mag2 = sqrt(v2.x * v2.x + v2.y * v2.y)
        
        if (mag1 == 0f || mag2 == 0f) return 0f
        
        val cosAngle = dot / (mag1 * mag2)
        val angle = acos(cosAngle.coerceIn(-1f, 1f))
        
        return angle * 180f / PI.toFloat()
    }
    
    /**
     * Compare calculated angles with ideal model and return technique score
     * @param keyPoints List of 33 key points from MediaPipe
     * @param useRightArm Whether to use right arm (true) or left arm (false)
     * @return TechniqueAnalysis with angles and scores
     */
    fun analyzeTechnique(keyPoints: List<KeyPoint>, useRightArm: Boolean = true): TechniqueAnalysis {
        val elbowAngle = calculateElbowAngle(keyPoints, useRightArm)
        val shoulderAngle = calculateShoulderAngle(keyPoints, useRightArm)
        val bodyRotation = calculateBodyRotation(keyPoints)
        
        val elbowScore = elbowAngle?.let { scoreAngle(it, IDEAL_ELBOW_ANGLE_MIN, IDEAL_ELBOW_ANGLE_MAX) } ?: 0f
        val shoulderScore = shoulderAngle?.let { scoreAngle(it, IDEAL_SHOULDER_ANGLE_MIN, IDEAL_SHOULDER_ANGLE_MAX) } ?: 0f
        val bodyRotationScore = bodyRotation?.let { 
            scoreAngle(abs(it), IDEAL_BODY_ROTATION_MIN, IDEAL_BODY_ROTATION_MAX) 
        } ?: 0f
        
        val overallScore = (elbowScore + shoulderScore + bodyRotationScore) / 3f
        
        return TechniqueAnalysis(
            elbowAngle = elbowAngle,
            shoulderAngle = shoulderAngle,
            bodyRotation = bodyRotation,
            elbowScore = elbowScore,
            shoulderScore = shoulderScore,
            bodyRotationScore = bodyRotationScore,
            overallScore = overallScore
        )
    }
    
    /**
     * Score an angle based on how close it is to ideal range
     * @param angle Angle in degrees
     * @param min Ideal minimum
     * @param max Ideal maximum
     * @return Score from 0.0 to 1.0
     */
    private fun scoreAngle(angle: Float, min: Float, max: Float): Float {
        return when {
            angle in min..max -> 1.0f
            angle < min -> {
                val diff = min - angle
                val range = min - (min - 20f) // 20 degree tolerance
                (1.0f - (diff / range)).coerceIn(0f, 1f)
            }
            else -> {
                val diff = angle - max
                val range = (max + 20f) - max // 20 degree tolerance
                (1.0f - (diff / range)).coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * Data class containing technique analysis results
 */
data class TechniqueAnalysis(
    val elbowAngle: Float?,
    val shoulderAngle: Float?,
    val bodyRotation: Float?,
    val elbowScore: Float,
    val shoulderScore: Float,
    val bodyRotationScore: Float,
    val overallScore: Float
)

