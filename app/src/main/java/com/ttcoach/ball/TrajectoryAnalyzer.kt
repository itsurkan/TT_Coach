package com.ttcoach.ball

import android.graphics.PointF
import kotlin.math.*

/**
 * Analyzes ball flight path for deviation from expected straight trajectory
 */
class TrajectoryAnalyzer {
    
    companion object {
        private const val TRAJECTORY_HISTORY_SIZE = 10 // Number of points to track
        private const val MIN_DEVIATION_THRESHOLD = 0.01f // Minimum deviation to report
    }
    
    private val trajectoryPoints = mutableListOf<PointF>()
    
    /**
     * Add a point to trajectory history
     */
    fun addPoint(point: PointF) {
        trajectoryPoints.add(point)
        if (trajectoryPoints.size > TRAJECTORY_HISTORY_SIZE) {
            trajectoryPoints.removeAt(0)
        }
    }
    
    /**
     * Calculate deviation from expected straight path
     * @return Deviation value (0.0 = straight, >0 = curved)
     */
    fun calculateDeviation(): Float {
        if (trajectoryPoints.size < 3) {
            return 0f
        }
        
        // Fit a line to the trajectory points
        val startPoint = trajectoryPoints.first()
        val endPoint = trajectoryPoints.last()
        
        // Calculate expected straight line
        val dx = endPoint.x - startPoint.x
        val dy = endPoint.y - startPoint.y
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance == 0f) {
            return 0f
        }
        
        // Calculate average deviation from straight line
        var totalDeviation = 0f
        for (point in trajectoryPoints) {
            val deviation = pointToLineDistance(point, startPoint, endPoint)
            totalDeviation += deviation
        }
        
        return totalDeviation / trajectoryPoints.size
    }
    
    /**
     * Detect left/right drift
     * @return Positive value = right drift, negative = left drift, 0 = no drift
     */
    fun detectLateralDrift(): Float {
        if (trajectoryPoints.size < 2) {
            return 0f
        }
        
        val startPoint = trajectoryPoints.first()
        val endPoint = trajectoryPoints.last()
        
        // Calculate expected direction
        val expectedAngle = atan2(endPoint.y - startPoint.y, endPoint.x - startPoint.x)
        
        // Calculate actual average direction
        var totalAngle = 0f
        for (i in 1 until trajectoryPoints.size) {
            val angle = atan2(
                trajectoryPoints[i].y - trajectoryPoints[i-1].y,
                trajectoryPoints[i].x - trajectoryPoints[i-1].x
            )
            totalAngle += angle
        }
        val avgAngle = totalAngle / (trajectoryPoints.size - 1)
        
        // Drift is the difference in angle
        return avgAngle - expectedAngle
    }
    
    /**
     * Calculate distance from point to line segment
     */
    private fun pointToLineDistance(point: PointF, lineStart: PointF, lineEnd: PointF): Float {
        val A = point.x - lineStart.x
        val B = point.y - lineStart.y
        val C = lineEnd.x - lineStart.x
        val D = lineEnd.y - lineStart.y
        
        val dot = A * C + B * D
        val lenSq = C * C + D * D
        
        if (lenSq == 0f) {
            return sqrt(A * A + B * B)
        }
        
        val param = dot / lenSq
        
        val xx: Float
        val yy: Float
        
        if (param < 0) {
            xx = lineStart.x
            yy = lineStart.y
        } else if (param > 1) {
            xx = lineEnd.x
            yy = lineEnd.y
        } else {
            xx = lineStart.x + param * C
            yy = lineStart.y + param * D
        }
        
        val dx = point.x - xx
        val dy = point.y - yy
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Get trajectory curvature
     * @return Curvature value (positive = curves right, negative = curves left)
     */
    fun getCurvature(): Float {
        if (trajectoryPoints.size < 3) {
            return 0f
        }
        
        // Use three points to calculate curvature
        val p1 = trajectoryPoints[trajectoryPoints.size - 3]
        val p2 = trajectoryPoints[trajectoryPoints.size - 2]
        val p3 = trajectoryPoints.last()
        
        // Calculate vectors
        val v1x = p2.x - p1.x
        val v1y = p2.y - p1.y
        val v2x = p3.x - p2.x
        val v2y = p3.y - p2.y
        
        // Cross product indicates curvature direction
        val cross = v1x * v2y - v1y * v2x
        
        return cross
    }
    
    /**
     * Reset analyzer
     */
    fun reset() {
        trajectoryPoints.clear()
    }
}

