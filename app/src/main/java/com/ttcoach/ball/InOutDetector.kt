package com.ttcoach.ball

import android.graphics.PointF
import com.ttcoach.analysis.CalibrationManager

/**
 * Detects if ball bounce point is within table boundaries (In/Out detection)
 */
class InOutDetector(private val calibrationManager: CalibrationManager) {
    
    /**
     * Check if a point is within table boundaries
     * @param point Bounce point in pixel coordinates
     * @return true if point is within table (In), false if outside (Out)
     */
    fun isInBounds(point: PointF): Boolean {
        val bounds = calibrationManager.getTableBounds() ?: return false
        
        return point.x >= bounds.left &&
               point.x <= bounds.right &&
               point.y >= bounds.top &&
               point.y <= bounds.bottom
    }
    
    /**
     * Detect bounce point from ball trajectory
     * Bounce is detected when ball changes direction (velocity reverses in Y direction)
     * @param previousPosition Previous ball position
     * @param currentPosition Current ball position
     * @param previousVelocity Previous velocity
     * @param currentVelocity Current velocity
     * @return Bounce point if detected, null otherwise
     */
    fun detectBouncePoint(
        previousPosition: PointF,
        currentPosition: PointF,
        previousVelocity: PointF,
        currentVelocity: PointF
    ): PointF? {
        // Bounce detected when Y velocity changes sign (ball was going down, now going up)
        val wasGoingDown = previousVelocity.y > 0
        val isGoingUp = currentVelocity.y < 0
        
        if (wasGoingDown && isGoingUp) {
            // Bounce occurred between previous and current position
            // Estimate bounce point (midpoint or based on velocity change)
            val bounceY = (previousPosition.y + currentPosition.y) / 2f
            val bounceX = if (abs(currentPosition.x - previousPosition.x) < 0.01f) {
                currentPosition.x
            } else {
                // Interpolate based on velocity change
                val t = abs(previousVelocity.y) / (abs(previousVelocity.y) + abs(currentVelocity.y))
                previousPosition.x + (currentPosition.x - previousPosition.x) * t
            }
            
            return PointF(bounceX, bounceY)
        }
        
        return null
    }
    
    /**
     * Check if bounce point is In or Out
     * @param bouncePoint Detected bounce point
     * @return true if In, false if Out
     */
    fun checkInOut(bouncePoint: PointF): Boolean {
        return isInBounds(bouncePoint)
    }
    
    private fun abs(value: Float): Float = if (value < 0) -value else value
}

