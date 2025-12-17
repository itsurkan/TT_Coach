package com.ttcoach.analysis

import android.graphics.PointF
import com.ttcoach.cv.KeyPoint
import kotlin.math.*

/**
 * Detects table tennis stroke (hit) by tracking wrist velocity and racket proximity to ball
 */
class HitDetector {
    
    companion object {
        private const val RIGHT_WRIST = 16
        private const val LEFT_WRIST = 15
        
        // Velocity thresholds (normalized coordinates per frame)
        private const val MIN_HIT_VELOCITY = 0.01f // Minimum wrist velocity to detect hit
        private const val MAX_HIT_VELOCITY = 0.5f // Maximum reasonable velocity
        
        // Proximity threshold for racket-ball detection (normalized coordinates)
        private const val RACKET_BALL_PROXIMITY_THRESHOLD = 0.05f
        
        // Time window for hit detection (milliseconds)
        private const val HIT_DETECTION_WINDOW_MS = 200L
        
        // Minimum frames between hits
        private const val MIN_FRAMES_BETWEEN_HITS = 10
    }
    
    private data class WristPosition(
        val timestamp: Long,
        val position: PointF,
        val velocity: Float = 0f
    )
    
    private val wristHistory = mutableListOf<WristPosition>()
    private var lastHitTimestamp: Long = 0
    private var frameCountSinceLastHit = 0
    
    /**
     * Detect if a hit (stroke) occurred based on wrist velocity and ball proximity
     * @param keyPoints Current frame key points
     * @param ballPosition Current ball position (if detected), null if not detected
     * @param timestamp Current frame timestamp
     * @param useRightArm Whether to use right arm (true) or left arm (false)
     * @return HitDetectionResult with hit status and details
     */
    fun detectHit(
        keyPoints: List<KeyPoint>,
        ballPosition: PointF?,
        timestamp: Long,
        useRightArm: Boolean = true
    ): HitDetectionResult {
        val wristIdx = if (useRightArm) RIGHT_WRIST else LEFT_WRIST
        
        if (keyPoints.size <= wristIdx) {
            return HitDetectionResult(
                hitDetected = false,
                hitStart = false,
                hitEnd = false,
                wristVelocity = 0f,
                racketBallProximity = null
            )
        }
        
        val wrist = keyPoints[wristIdx]
        
        // Check visibility
        if (wrist.visibility < 0.5f) {
            return HitDetectionResult(
                hitDetected = false,
                hitStart = false,
                hitEnd = false,
                wristVelocity = 0f,
                racketBallProximity = null
            )
        }
        
        val currentPosition = PointF(wrist.x, wrist.y)
        
        // Calculate velocity from previous position
        val velocity = if (wristHistory.isNotEmpty()) {
            val lastPosition = wristHistory.last()
            val timeDelta = (timestamp - lastPosition.timestamp).coerceAtLeast(1L)
            val distance = sqrt(
                (currentPosition.x - lastPosition.position.x).pow(2) +
                (currentPosition.y - lastPosition.position.y).pow(2)
            )
            distance / timeDelta * 1000f // Normalize to per second
        } else {
            0f
        }
        
        // Add to history
        wristHistory.add(WristPosition(timestamp, currentPosition, velocity))
        
        // Keep only recent history (within detection window)
        val cutoffTime = timestamp - HIT_DETECTION_WINDOW_MS
        wristHistory.removeAll { it.timestamp < cutoffTime }
        
        // Calculate racket-ball proximity if ball is detected
        val racketBallProximity = ballPosition?.let { ball ->
            val distance = sqrt(
                (currentPosition.x - ball.x).pow(2) +
                (currentPosition.y - ball.y).pow(2)
            )
            distance
        }
        
        // Detect hit based on velocity spike
        val hitDetected = velocity > MIN_HIT_VELOCITY && 
                         velocity < MAX_HIT_VELOCITY &&
                         (timestamp - lastHitTimestamp) > (MIN_FRAMES_BETWEEN_HITS * 33) // ~30 FPS
        
        // Detect hit start (velocity increasing rapidly)
        val hitStart = hitDetected && wristHistory.size >= 2 && 
                      velocity > MIN_HIT_VELOCITY &&
                      wristHistory[wristHistory.size - 2].velocity < velocity * 0.7f
        
        // Detect hit end (velocity decreasing rapidly)
        val hitEnd = wristHistory.size >= 2 &&
                    velocity < MIN_HIT_VELOCITY &&
                    wristHistory[wristHistory.size - 2].velocity > MIN_HIT_VELOCITY
        
        // Update last hit timestamp if hit detected
        if (hitDetected) {
            lastHitTimestamp = timestamp
            frameCountSinceLastHit = 0
        } else {
            frameCountSinceLastHit++
        }
        
        return HitDetectionResult(
            hitDetected = hitDetected,
            hitStart = hitStart,
            hitEnd = hitEnd,
            wristVelocity = velocity,
            racketBallProximity = racketBallProximity,
            isRacketNearBall = racketBallProximity?.let { it < RACKET_BALL_PROXIMITY_THRESHOLD } ?: false
        )
    }
    
    /**
     * Reset hit detection state (call when starting new session)
     */
    fun reset() {
        wristHistory.clear()
        lastHitTimestamp = 0
        frameCountSinceLastHit = 0
    }
    
    /**
     * Get current wrist velocity
     */
    fun getCurrentWristVelocity(): Float {
        return wristHistory.lastOrNull()?.velocity ?: 0f
    }
}

/**
 * Data class containing hit detection results
 */
data class HitDetectionResult(
    val hitDetected: Boolean,
    val hitStart: Boolean,
    val hitEnd: Boolean,
    val wristVelocity: Float,
    val racketBallProximity: Float?,
    val isRacketNearBall: Boolean = false
)

