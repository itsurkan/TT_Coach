/*
 * AI Coach for Table Tennis
 * Stroke Detector - Segments video frames into individual strokes
 */

package com.google.mediapipe.examples.poselandmarker.services

import android.util.Log
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.sqrt

/**
 * Detects individual strokes from continuous video frames
 * Differentiates between actual strokes and frames
 */
class StrokeDetector {
    
    companion object {
        private const val TAG = "StrokeDetector"
        
        // Velocity thresholds for stroke detection
        private const val STROKE_START_VELOCITY = 0.15f  // Movement starts (m/s equivalent)
        private const val STROKE_END_VELOCITY = 0.05f    // Movement ends
        
        // Position thresholds
        private const val MIN_STROKE_DISTANCE = 0.2f     // Minimum hand travel distance
        private const val RETURN_POSITION_THRESHOLD = 0.15f // How close to start position for reset
        
        // Timing
        private const val MIN_STROKE_DURATION_MS = 200L   // Minimum stroke duration
        private const val MAX_STROKE_DURATION_MS = 2000L  // Maximum stroke duration
    }
    
    enum class StrokeState {
        IDLE,           // Ready position, no movement
        FORWARD,        // Forward stroke motion
        RETURNING       // Returning to ready position
    }
    
    data class Stroke(
        val startFrameIndex: Int,
        val endFrameIndex: Int,
        val peakFrameIndex: Int,       // Frame with maximum velocity
        val returnStartIndex: Int,      // When return motion starts
        val returnEndIndex: Int,        // When back at ready position
        val maxVelocity: Float,
        val totalDistance: Float
    )
    
    private var currentState = StrokeState.IDLE
    private var strokeStartFrame = -1
    private var strokePeakFrame = -1
    private var returnStartFrame = -1
    private var startPosition: Pair<Float, Float>? = null
    private var previousPosition: Pair<Float, Float>? = null
    private var maxVelocity = 0f
    private var totalDistance = 0f
    
    private val detectedStrokes = mutableListOf<Stroke>()
    
    /**
     * Process a sequence of frames and detect strokes
     */
    fun detectStrokes(
        results: List<PoseLandmarkerResult>,
        intervalMs: Long
    ): List<Stroke> {
        detectedStrokes.clear()
        reset()
        
        for (i in results.indices) {
            processFrame(results[i], i, intervalMs)
        }
        
        // Finalize any ongoing stroke
        if (currentState != StrokeState.IDLE) {
            finalizeStroke(results.size - 1)
        }
        
        Log.i(TAG, "Detected ${detectedStrokes.size} strokes from ${results.size} frames")
        return detectedStrokes.toList()
    }
    
    private fun processFrame(result: PoseLandmarkerResult, frameIndex: Int, intervalMs: Long) {
        if (result.landmarks().isEmpty()) {
            return
        }
        
        val landmarks = result.landmarks()[0]
        
        // Get wrist position (right hand for forehand)
        val wrist = landmarks[16] // Right wrist
        val currentPosition = Pair(wrist.x(), wrist.y())
        
        // Initialize starting position on first valid frame
        if (startPosition == null && currentState == StrokeState.IDLE) {
            startPosition = currentPosition
            previousPosition = currentPosition
            return
        }
        
        // Calculate velocity (distance per frame)
        val velocity = previousPosition?.let { prev ->
            calculateDistance(prev, currentPosition)
        } ?: 0f
        
        // State machine for stroke detection
        when (currentState) {
            StrokeState.IDLE -> {
                // Detect stroke start
                if (velocity > STROKE_START_VELOCITY) {
                    strokeStartFrame = frameIndex
                    currentState = StrokeState.FORWARD
                    maxVelocity = velocity
                    totalDistance = 0f
                    Log.d(TAG, "Stroke started at frame $frameIndex")
                }
            }
            
            StrokeState.FORWARD -> {
                totalDistance += velocity
                
                // Track peak velocity
                if (velocity > maxVelocity) {
                    maxVelocity = velocity
                    strokePeakFrame = frameIndex
                }
                
                // Detect stroke end (velocity drops significantly)
                if (velocity < STROKE_END_VELOCITY && totalDistance > MIN_STROKE_DISTANCE) {
                    returnStartFrame = frameIndex
                    currentState = StrokeState.RETURNING
                    Log.d(TAG, "Stroke forward phase ended at frame $frameIndex, distance: $totalDistance")
                }
                
                // Timeout - stroke too long
                val duration = (frameIndex - strokeStartFrame) * intervalMs
                if (duration > MAX_STROKE_DURATION_MS) {
                    Log.w(TAG, "Stroke timeout at frame $frameIndex")
                    finalizeStroke(frameIndex)
                }
            }
            
            StrokeState.RETURNING -> {
                // Check if returned to start position
                val distanceFromStart = startPosition?.let { start ->
                    calculateDistance(start, currentPosition)
                } ?: Float.MAX_VALUE
                
                if (distanceFromStart < RETURN_POSITION_THRESHOLD) {
                    // Completed full stroke cycle
                    finalizeStroke(frameIndex)
                    Log.d(TAG, "Stroke completed, returned to start at frame $frameIndex")
                }
                
                // Timeout for return
                val returnDuration = (frameIndex - returnStartFrame) * intervalMs
                if (returnDuration > MAX_STROKE_DURATION_MS) {
                    Log.w(TAG, "Return timeout at frame $frameIndex")
                    finalizeStroke(frameIndex)
                }
            }
        }
        
        previousPosition = currentPosition
    }
    
    private fun finalizeStroke(endFrame: Int) {
        if (strokeStartFrame >= 0 && totalDistance > MIN_STROKE_DISTANCE) {
            val stroke = Stroke(
                startFrameIndex = strokeStartFrame,
                endFrameIndex = endFrame,
                peakFrameIndex = strokePeakFrame,
                returnStartIndex = returnStartFrame,
                returnEndIndex = endFrame,
                maxVelocity = maxVelocity,
                totalDistance = totalDistance
            )
            detectedStrokes.add(stroke)
            Log.i(TAG, "Finalized stroke ${detectedStrokes.size}: frames $strokeStartFrame-$endFrame, " +
                    "peak at $strokePeakFrame, distance: $totalDistance")
        }
        
        reset()
    }
    
    private fun reset() {
        currentState = StrokeState.IDLE
        strokeStartFrame = -1
        strokePeakFrame = -1
        returnStartFrame = -1
        maxVelocity = 0f
        totalDistance = 0f
        // Keep startPosition for next stroke
    }
    
    private fun calculateDistance(p1: Pair<Float, Float>, p2: Pair<Float, Float>): Float {
        val dx = p2.first - p1.first
        val dy = p2.second - p1.second
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Get stroke that contains a specific frame
     */
    fun getStrokeForFrame(frameIndex: Int): Stroke? {
        return detectedStrokes.find { stroke ->
            frameIndex in stroke.startFrameIndex..stroke.returnEndIndex
        }
    }
    
    /**
     * Check if frame is in forward stroke phase
     */
    fun isForwardStroke(frameIndex: Int): Boolean {
        val stroke = getStrokeForFrame(frameIndex) ?: return false
        return frameIndex in stroke.startFrameIndex..stroke.returnStartIndex
    }
    
    /**
     * Check if frame is in return phase
     */
    fun isReturnPhase(frameIndex: Int): Boolean {
        val stroke = getStrokeForFrame(frameIndex) ?: return false
        return frameIndex in stroke.returnStartIndex..stroke.returnEndIndex
    }
}
