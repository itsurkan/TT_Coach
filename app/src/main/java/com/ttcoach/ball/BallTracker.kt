package com.ttcoach.ball

import android.graphics.Bitmap
import android.graphics.PointF
import com.ttcoach.analysis.CalibrationManager
import com.ttcoach.cv.BallDetection
import com.ttcoach.cv.KalmanTracker
import com.ttcoach.cv.YOLOBallDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main coordinator for ball tracking, combining YOLO detection, Kalman filtering,
 * and ball analysis modules
 */
class BallTracker(
    private val ballDetector: YOLOBallDetector,
    private val calibrationManager: CalibrationManager
) {
    
    private val kalmanTracker = KalmanTracker()
    private val inOutDetector = InOutDetector(calibrationManager)
    private val speedCalculator = SpeedCalculator(calibrationManager)
    private val spinEstimator = SpinEstimator()
    private val trajectoryAnalyzer = TrajectoryAnalyzer()
    
    private var lastBallPosition: PointF? = null
    private var lastVelocity: PointF? = null
    private var lastTimestamp: Long = 0
    private var lastBounceTimestamp: Long = 0
    
    private val _ballAnalysis = MutableStateFlow<BallAnalysis?>(null)
    val ballAnalysis: StateFlow<BallAnalysis?> = _ballAnalysis.asStateFlow()
    
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    /**
     * Process a new frame for ball tracking
     * @param bitmap Camera frame
     * @param timestamp Frame timestamp
     */
    fun processFrame(bitmap: Bitmap, timestamp: Long) {
        // Detect ball using YOLO
        val detections = ballDetector.detect(bitmap)
        
        if (detections.isEmpty()) {
            // No ball detected - use Kalman prediction
            if (kalmanTracker.isTracking()) {
                val predicted = kalmanTracker.predict(timestamp)
                updateAnalysis(
                    predicted, 
                    null, 
                    timestamp, 
                    isDetected = false,
                    speedMs = null,
                    spin = SpinType.NONE,
                    trajectoryDeviation = trajectoryAnalyzer.calculateDeviation(),
                    lateralDrift = trajectoryAnalyzer.detectLateralDrift()
                )
            } else {
                _isTracking.value = false
                _ballAnalysis.value = null
            }
            return
        }
        
        // Use highest confidence detection
        val bestDetection = detections.maxByOrNull { it.confidence } ?: return
        val ballCenter = bestDetection.center
        
        // Update Kalman filter with detection
        kalmanTracker.update(ballCenter, timestamp)
        
        // Get tracked position (may differ from detection due to filtering)
        val trackedPosition = kalmanTracker.getPosition()
        val velocity = kalmanTracker.getVelocity()
        
        // Add to trajectory
        trajectoryAnalyzer.addPoint(trackedPosition)
        
        // Detect bounce point
        val bouncePoint = if (lastBallPosition != null && lastVelocity != null) {
            inOutDetector.detectBouncePoint(
                lastBallPosition!!,
                trackedPosition,
                lastVelocity!!,
                velocity
            )
        } else {
            null
        }
        
        // Update bounce timestamp if detected
        if (bouncePoint != null) {
            lastBounceTimestamp = timestamp
        }
        
        // Check In/Out for bounce point or current position
        val inOut = if (bouncePoint != null) {
            inOutDetector.checkInOut(bouncePoint)
        } else {
            inOutDetector.isInBounds(trackedPosition)
        }
        
        // Calculate speed in m/s
        val speedMs = speedCalculator.calculateSpeed(trackedPosition, timestamp)
        
        // Estimate spin (if bounce occurred recently)
        val spin = if (bouncePoint != null && (timestamp - lastBounceTimestamp) < 200) {
            spinEstimator.estimateSpin(lastBounceTimestamp, velocity, timestamp)
        } else {
            SpinType.NONE
        }
        
        // Calculate trajectory deviation
        val trajectoryDeviation = trajectoryAnalyzer.calculateDeviation()
        val lateralDrift = trajectoryAnalyzer.detectLateralDrift()
        
        // Update analysis
        updateAnalysis(
            trackedPosition, 
            bouncePoint, 
            timestamp, 
            isDetected = true, 
            inOut = inOut,
            speedMs = speedMs,
            spin = spin,
            trajectoryDeviation = trajectoryDeviation,
            lateralDrift = lateralDrift
        )
        
        // Update state
        lastBallPosition = trackedPosition
        lastVelocity = velocity
        lastTimestamp = timestamp
        _isTracking.value = true
    }
    
    private fun updateAnalysis(
        position: PointF,
        bouncePoint: PointF?,
        timestamp: Long,
        isDetected: Boolean,
        inOut: Boolean = true,
        speedMs: Float? = null,
        spin: SpinType = SpinType.NONE,
        trajectoryDeviation: Float = 0f,
        lateralDrift: Float = 0f
    ) {
        _ballAnalysis.value = BallAnalysis(
            position = position,
            bouncePoint = bouncePoint,
            inOut = inOut,
            speed = speedMs ?: 0f, // Speed in m/s
            spin = spin,
            trajectoryDeviation = trajectoryDeviation,
            lateralDrift = lateralDrift,
            timestamp = timestamp,
            isDetected = isDetected
        )
    }
    
    /**
     * Get current ball position
     */
    fun getCurrentPosition(): PointF? {
        return if (kalmanTracker.isTracking()) {
            kalmanTracker.getPosition()
        } else {
            null
        }
    }
    
    /**
     * Reset tracking
     */
    fun reset() {
        kalmanTracker.reset()
        speedCalculator.reset()
        spinEstimator.reset()
        trajectoryAnalyzer.reset()
        lastBallPosition = null
        lastVelocity = null
        lastTimestamp = 0
        lastBounceTimestamp = 0
        _isTracking.value = false
        _ballAnalysis.value = null
    }
}

/**
 * Data class containing ball analysis results
 */
data class BallAnalysis(
    val position: PointF,
    val bouncePoint: PointF?,
    val inOut: Boolean,
    val speed: Float, // Speed in m/s
    val spin: SpinType = SpinType.NONE,
    val trajectoryDeviation: Float = 0f,
    val lateralDrift: Float = 0f,
    val timestamp: Long,
    val isDetected: Boolean
)

