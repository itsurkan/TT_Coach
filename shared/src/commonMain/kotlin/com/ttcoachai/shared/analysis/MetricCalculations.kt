/*
 * AI Coach for Table Tennis
 * Metric Calculations - Platform-independent distance/speed metrics
 */

package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Landmark3D
import kotlin.math.sqrt

/**
 * Platform-independent metric calculation functions extracted from MotionAnalyzer.
 */
object MetricCalculations {

    /**
     * Calculate contact height relative to hip:
     *   16 = right wrist, 24 = right hip
     * Returns null when required landmark indices are out of bounds.
     */
    fun calculateContactHeight(landmarks: List<Landmark3D>): Float? {
        val wrist = landmarks.getOrNull(16) ?: return null
        val hip = landmarks.getOrNull(24) ?: return null

        if (hip.y == 0f) return null

        // Relative height: 0 = hip level, positive = above hip
        val height = 1f - wrist.y / hip.y
        return if (height.isNaN() || height.isInfinite()) null else height
    }

    /**
     * Calculate Euclidean 2D distance between elbow (14) and hip (24).
     * Returns null when required landmark indices are out of bounds.
     */
    fun calculateElbowBodyDistance(landmarks: List<Landmark3D>): Float? {
        val elbow = landmarks.getOrNull(14) ?: return null
        val hip = landmarks.getOrNull(24) ?: return null

        val dx = elbow.x - hip.x
        val dy = elbow.y - hip.y

        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        return if (distance.isNaN() || distance.isInfinite()) null else distance
    }

    /**
     * Calculate stroke speed from wrist (16) displacement between two frames divided by time delta.
     * Returns null when required landmark indices are out of bounds or timeDeltaMs is zero.
     */
    fun calculateStrokeSpeed(
        currentLandmarks: List<Landmark3D>,
        previousLandmarks: List<Landmark3D>,
        timeDeltaMs: Long
    ): Float? {
        if (timeDeltaMs <= 0L) return null

        val currentWrist = currentLandmarks.getOrNull(16) ?: return null
        val previousWrist = previousLandmarks.getOrNull(16) ?: return null

        val dx = currentWrist.x - previousWrist.x
        val dy = currentWrist.y - previousWrist.y
        val dz = currentWrist.z - previousWrist.z

        val displacement = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        val speed = displacement / timeDeltaMs * 1000f  // units per second

        return if (speed.isNaN() || speed.isInfinite()) null else speed
    }
}
