package com.ttcoach.cv

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Kalman Filter for stable ball tracking
 * Predicts ball position and velocity, updates with measurements
 */
class KalmanTracker {
    
    // State: [x, y, vx, vy] - position and velocity
    private var state = FloatArray(4) // [x, y, vx, vy]
    private var covariance = Array(4) { FloatArray(4) }
    
    // Process noise (how much we trust our prediction)
    private val processNoise = 0.1f
    
    // Measurement noise (how much we trust measurements)
    private val measurementNoise = 0.5f
    
    private var isInitialized = false
    private var lastUpdateTime = 0L
    private val dt = 1f / 30f // Assume 30 FPS, will be updated with actual time
    
    init {
        // Initialize covariance matrix
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                covariance[i][j] = if (i == j) 1f else 0f
            }
        }
    }
    
    /**
     * Initialize tracker with first measurement
     */
    fun initialize(position: PointF, timestamp: Long) {
        state[0] = position.x
        state[1] = position.y
        state[2] = 0f // Initial velocity x
        state[3] = 0f // Initial velocity y
        
        lastUpdateTime = timestamp
        isInitialized = true
    }
    
    /**
     * Predict next position based on current state
     */
    fun predict(timestamp: Long): PointF {
        if (!isInitialized) {
            return PointF(0f, 0f)
        }
        
        val dt = ((timestamp - lastUpdateTime) / 1000f).coerceAtLeast(0.001f).coerceAtMost(0.1f)
        
        // State transition: x' = x + vx*dt, y' = y + vy*dt
        val predictedX = state[0] + state[2] * dt
        val predictedY = state[1] + state[3] * dt
        
        // Update covariance (simplified)
        for (i in 0 until 4) {
            covariance[i][i] += processNoise
        }
        
        return PointF(predictedX, predictedY)
    }
    
    /**
     * Update state with new measurement
     */
    fun update(measurement: PointF, timestamp: Long) {
        if (!isInitialized) {
            initialize(measurement, timestamp)
            return
        }
        
        val dt = ((timestamp - lastUpdateTime) / 1000f).coerceAtLeast(0.001f).coerceAtMost(0.1f)
        
        // Predict first
        val predicted = predict(timestamp)
        
        // Calculate innovation (difference between measurement and prediction)
        val innovationX = measurement.x - predicted.x
        val innovationY = measurement.y - predicted.y
        
        // Kalman gain (simplified 2D version)
        val gain = measurementNoise / (measurementNoise + processNoise)
        
        // Update state
        state[0] = predicted.x + gain * innovationX
        state[1] = predicted.y + gain * innovationY
        
        // Update velocity estimate
        state[2] = (state[0] - state[0]) / dt // Simplified
        state[3] = (state[1] - state[1]) / dt // Simplified
        
        // Better velocity calculation using history
        if (lastUpdateTime > 0) {
            val actualDt = ((timestamp - lastUpdateTime) / 1000f).coerceAtLeast(0.001f)
            state[2] = (state[0] - (state[0] - state[2] * actualDt)) / actualDt
            state[3] = (state[1] - (state[1] - state[3] * actualDt)) / actualDt
        }
        
        lastUpdateTime = timestamp
    }
    
    /**
     * Get current tracked position
     */
    fun getPosition(): PointF {
        return if (isInitialized) {
            PointF(state[0], state[1])
        } else {
            PointF(0f, 0f)
        }
    }
    
    /**
     * Get current velocity
     */
    fun getVelocity(): PointF {
        return if (isInitialized) {
            PointF(state[2], state[3])
        } else {
            PointF(0f, 0f)
        }
    }
    
    /**
     * Get speed (magnitude of velocity)
     */
    fun getSpeed(): Float {
        val vel = getVelocity()
        return sqrt(vel.x * vel.x + vel.y * vel.y)
    }
    
    /**
     * Reset tracker
     */
    fun reset() {
        isInitialized = false
        lastUpdateTime = 0L
        state.fill(0f)
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                covariance[i][j] = if (i == j) 1f else 0f
            }
        }
    }
    
    /**
     * Check if tracker is initialized
     */
    fun isTracking(): Boolean = isInitialized
}

