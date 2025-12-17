package com.ttcoach.ball

import android.graphics.PointF
import kotlin.math.abs

/**
 * Estimates basic spin (top/back) from trajectory changes after bounce
 */
class SpinEstimator {
    
    companion object {
        private const val MIN_VELOCITY_CHANGE = 0.1f // Minimum change to detect spin
        private const val BOUNCE_WINDOW_MS = 100L // Window around bounce to analyze
    }
    
    private data class VelocitySample(
        val timestamp: Long,
        val velocity: PointF,
        val isBeforeBounce: Boolean
    )
    
    private val velocityHistory = mutableListOf<VelocitySample>()
    
    /**
     * Estimate spin type from trajectory changes around bounce
     * @param bounceTimestamp Timestamp when bounce occurred
     * @param currentVelocity Current velocity
     * @param currentTimestamp Current timestamp
     * @return SpinType (TOP, BACK, or NONE)
     */
    fun estimateSpin(
        bounceTimestamp: Long,
        currentVelocity: PointF,
        currentTimestamp: Long
    ): SpinType {
        // Add current velocity to history
        val isBeforeBounce = currentTimestamp < bounceTimestamp
        velocityHistory.add(
            VelocitySample(currentTimestamp, currentVelocity, isBeforeBounce)
        )
        
        // Keep only recent history
        val cutoffTime = currentTimestamp - BOUNCE_WINDOW_MS * 2
        velocityHistory.removeAll { it.timestamp < cutoffTime }
        
        // Find velocities before and after bounce
        val beforeBounce = velocityHistory.filter { it.isBeforeBounce && it.timestamp < bounceTimestamp }
            .lastOrNull()
        val afterBounce = velocityHistory.filter { !it.isBeforeBounce && it.timestamp > bounceTimestamp }
            .firstOrNull()
        
        if (beforeBounce == null || afterBounce == null) {
            return SpinType.NONE
        }
        
        // Analyze Y velocity change (vertical component)
        val beforeY = beforeBounce.velocity.y
        val afterY = afterBounce.velocity.y
        
        // Top spin: ball accelerates downward after bounce (Y velocity increases)
        // Back spin: ball decelerates or bounces higher (Y velocity decreases or becomes more negative)
        val yVelocityChange = afterY - beforeY
        
        return when {
            yVelocityChange > MIN_VELOCITY_CHANGE -> SpinType.TOP
            yVelocityChange < -MIN_VELOCITY_CHANGE -> SpinType.BACK
            else -> SpinType.NONE
        }
    }
    
    /**
     * Estimate spin from trajectory angle change
     * @param angleBeforeBounce Angle of trajectory before bounce (degrees)
     * @param angleAfterBounce Angle of trajectory after bounce (degrees)
     * @return SpinType
     */
    fun estimateSpinFromAngles(angleBeforeBounce: Float, angleAfterBounce: Float): SpinType {
        val angleChange = angleAfterBounce - angleBeforeBounce
        
        // Top spin: trajectory becomes steeper (more negative angle)
        // Back spin: trajectory becomes shallower (less negative angle)
        return when {
            angleChange < -5f -> SpinType.TOP
            angleChange > 5f -> SpinType.BACK
            else -> SpinType.NONE
        }
    }
    
    /**
     * Reset estimator
     */
    fun reset() {
        velocityHistory.clear()
    }
}

/**
 * Enum representing spin types
 */
enum class SpinType {
    TOP,    // Top spin
    BACK,   // Back spin
    NONE    // No significant spin
}

