/*
 * AI Coach for Table Tennis
 * Angle Calculations - Platform-independent 3D/2D angle math
 */

package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Landmark3D
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Platform-independent angle calculation functions extracted from MotionAnalyzer.
 */
object AngleCalculations {

    /**
     * Calculate 3D angle at point b formed by segments ba and bc (degrees, via dot product).
     */
    fun calculate3DAngle(a: Landmark3D, b: Landmark3D, c: Landmark3D): Float {
        // Vector BA
        val bax = a.x - b.x
        val bay = a.y - b.y
        val baz = a.z - b.z

        // Vector BC
        val bcx = c.x - b.x
        val bcy = c.y - b.y
        val bcz = c.z - b.z

        val dotProduct = bax * bcx + bay * bcy + baz * bcz
        val magBA = sqrt((bax * bax + bay * bay + baz * baz).toDouble())
        val magBC = sqrt((bcx * bcx + bcy * bcy + bcz * bcz).toDouble())

        if (magBA == 0.0 || magBC == 0.0) return 0f

        val cosAngle = dotProduct / (magBA * magBC)
        return Math.toDegrees(Math.acos(cosAngle.coerceIn(-1.0, 1.0))).toFloat()
    }

    /**
     * Calculate wrist angle using landmarks:
     *   14 = right elbow, 16 = right wrist, 20 = right index finger
     * Returns null when required landmark indices are out of bounds.
     */
    fun calculateWristAngle(landmarks: List<Landmark3D>): Float? {
        val elbow = landmarks.getOrNull(14) ?: return null
        val wrist = landmarks.getOrNull(16) ?: return null
        val index = landmarks.getOrNull(20) ?: return null

        val angle = calculate3DAngle(elbow, wrist, index)
        return if (angle.isNaN() || angle.isInfinite()) null else angle
    }

    /**
     * Calculate body rotation using landmarks:
     *   11 = left shoulder, 12 = right shoulder, 23 = left hip, 24 = right hip
     * Returns null when required landmark indices are out of bounds.
     */
    fun calculateBodyRotation(landmarks: List<Landmark3D>): Float? {
        val leftShoulder = landmarks.getOrNull(11) ?: return null
        val rightShoulder = landmarks.getOrNull(12) ?: return null
        val leftHip = landmarks.getOrNull(23) ?: return null
        val rightHip = landmarks.getOrNull(24) ?: return null

        val shoulderAngle = Math.toDegrees(
            atan2(
                (rightShoulder.y - leftShoulder.y).toDouble(),
                (rightShoulder.x - leftShoulder.x).toDouble()
            )
        ).toFloat()

        val hipAngle = Math.toDegrees(
            atan2(
                (rightHip.y - leftHip.y).toDouble(),
                (rightHip.x - leftHip.x).toDouble()
            )
        ).toFloat()

        val rotation = abs(shoulderAngle - hipAngle)
        return if (rotation.isNaN() || rotation.isInfinite()) null else rotation
    }

    /**
     * Calculate follow-through angle using landmarks:
     *   12 = right shoulder, 14 = right elbow, 16 = right wrist
     * Returns null when required landmark indices are out of bounds.
     */
    fun calculateFollowThroughAngle(landmarks: List<Landmark3D>): Float? {
        val shoulder = landmarks.getOrNull(12) ?: return null
        val elbow = landmarks.getOrNull(14) ?: return null
        val wrist = landmarks.getOrNull(16) ?: return null

        val angle = calculate3DAngle(shoulder, elbow, wrist)
        return if (angle.isNaN() || angle.isInfinite()) null else angle
    }
}
