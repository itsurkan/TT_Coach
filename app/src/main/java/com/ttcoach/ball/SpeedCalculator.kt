package com.ttcoach.ball

import android.graphics.PointF
import com.ttcoach.analysis.CalibrationManager

/**
 * Calculates ball speed in m/s using calibrated scale
 */
class SpeedCalculator(private val calibrationManager: CalibrationManager) {
    
    private var lastPosition: PointF? = null
    private var lastTimestamp: Long = 0
    
    /**
     * Calculate speed from position change
     * @param currentPosition Current ball position in pixels
     * @param timestamp Current timestamp
     * @return Speed in m/s, or null if calculation not possible
     */
    fun calculateSpeed(currentPosition: PointF, timestamp: Long): Float? {
        val lastPos = lastPosition ?: run {
            lastPosition = currentPosition
            lastTimestamp = timestamp
            return null
        }
        
        val timeDelta = (timestamp - lastTimestamp) / 1000f // Convert to seconds
        if (timeDelta <= 0) {
            return null
        }
        
        // Calculate distance in meters
        val distance = calibrationManager.pixelDistanceToMeters(lastPos, currentPosition)
            ?: return null
        
        // Speed = distance / time
        val speed = distance / timeDelta
        
        // Update state
        lastPosition = currentPosition
        lastTimestamp = timestamp
        
        return speed
    }
    
    /**
     * Calculate instantaneous speed from velocity vector
     * @param velocity Velocity in pixels per second
     * @return Speed in m/s
     */
    fun calculateSpeedFromVelocity(velocity: PointF): Float? {
        // Convert velocity to meters per second
        // This is a simplified conversion - assumes velocity is in pixels/second
        // For accurate conversion, we'd need to track velocity in meters
        val pixelSpeed = kotlin.math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
        
        // Approximate conversion (1 meter ≈ table width in pixels / TABLE_WIDTH)
        val bounds = calibrationManager.getTableBounds() ?: return null
        val scale = 1.525f / bounds.width() // TABLE_WIDTH / pixel width
        
        return pixelSpeed * scale
    }
    
    /**
     * Reset calculator
     */
    fun reset() {
        lastPosition = null
        lastTimestamp = 0
    }
}

