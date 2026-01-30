/*
 * AI Coach for Table Tennis
 * Generic Stroke Detector for JSON Pose Data
 */

package com.ttcoachai.services

import android.util.Log
import com.ttcoachai.models.StrokePhase
import kotlin.math.abs

/**
 * Axis for tracking movement
 */
enum class TrackingAxis {
    X,  // Horizontal movement (forehand/backhand)
    Y,  // Vertical movement
    Z   // Depth movement
}

/**
 * Configuration for stroke detection thresholds
 */
data class StrokeDetectorConfig(
    val landmarkIndex: Int = 16,              // Right wrist (16) or Left wrist (15)
    val trackingAxis: TrackingAxis = TrackingAxis.X,
    val backswingThreshold: Float = 0.45f,    // Below = backswing detected
    val forwardPeakThreshold: Float = 0.60f,  // Above = peak reached
    val readyPositionThreshold: Float = 0.52f, // Neutral position
    val forwardVelocityThreshold: Float = 0.08f, // Per-frame velocity to detect forward
    val returnVelocityThreshold: Float = -0.05f, // Negative velocity for return
    val minBackswingDepth: Float = 0.08f,     // Minimum movement back
    val minForwardExtension: Float = 0.15f,   // Minimum forward movement
    val minStrokeFrames: Int = 3,             // 300ms minimum at 100ms interval
    val maxStrokeFrames: Int = 20,            // 2s timeout
    val smoothingWindow: Int = 3,             // Moving average frames
    val invertDirection: Boolean = false       // For backhand (left-to-right)
) {
    companion object {
        /** Default config for forehand drive */
        val FOREHAND = StrokeDetectorConfig(
            forwardVelocityThreshold = 0.075f
        )

        /** Config for backhand - inverted direction, left wrist */
        val BACKHAND = StrokeDetectorConfig(
            landmarkIndex = 15,  // Left wrist
            invertDirection = true
        )
    }
}

/**
 * Represents a landmark from JSON pose data
 */
data class JsonLandmark(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float
)

/**
 * Represents a single pose frame from JSON
 */
data class JsonPoseFrame(
    val frameIndex: Int,
    val timestampMs: Long,
    val landmarks: List<JsonLandmark>
)

/**
 * Detected stroke with phase boundaries and metrics
 */
data class DetectedStroke(
    val strokeIndex: Int,

    // Phase boundaries (frame indices)
    val preparationStartFrame: Int,
    val preparationEndFrame: Int,
    val forwardStartFrame: Int,
    val contactFrame: Int,
    val forwardEndFrame: Int,
    val returnStartFrame: Int,
    val returnEndFrame: Int,

    // Metrics
    val backswingMinValue: Float,
    val forwardPeakValue: Float,
    val peakVelocity: Float,
    val strokeDurationMs: Long,
    val forwardSwingDurationMs: Long,

    // Quality indicators
    val isComplete: Boolean
) {
    /** Check if a frame index is within this stroke */
    fun containsFrame(frameIndex: Int): Boolean {
        return frameIndex in preparationStartFrame..returnEndFrame
    }

    /** Get the phase for a specific frame */
    fun getPhaseForFrame(frameIndex: Int): StrokePhase {
        return when {
            frameIndex < preparationStartFrame -> StrokePhase.READY
            frameIndex <= preparationEndFrame -> StrokePhase.BACKSWING
            frameIndex <= forwardEndFrame -> {
                if (frameIndex == contactFrame) StrokePhase.CONTACT
                else StrokePhase.FORWARD_SWING
            }
            frameIndex <= returnEndFrame -> StrokePhase.FOLLOW_THROUGH
            else -> StrokePhase.RECOVERY
        }
    }
}

/**
 * Phase information for a single frame
 */
data class FramePhaseInfo(
    val frameIndex: Int,
    val phase: StrokePhase,
    val strokeIndex: Int?,
    val phaseProgress: Float  // 0.0-1.0 progress within phase
)

/**
 * Result of stroke detection analysis
 */
data class StrokeDetectionResult(
    val strokes: List<DetectedStroke>,
    val framePhases: List<FramePhaseInfo>,
    val totalFrames: Int
) {
    /** Get stroke that contains a specific frame */
    fun getStrokeForFrame(frameIndex: Int): DetectedStroke? {
        return strokes.find { it.containsFrame(frameIndex) }
    }

    /** Get phase for a specific frame */
    fun getPhaseForFrame(frameIndex: Int): StrokePhase {
        return framePhases.getOrNull(frameIndex)?.phase ?: StrokePhase.READY
    }

    /** Get stroke info for a specific frame */
    fun getFrameInfo(frameIndex: Int): FramePhaseInfo? {
        return framePhases.getOrNull(frameIndex)
    }
}

/**
 * Builder for constructing stroke data during detection
 */
private class StrokeBuilder(
    val strokeIndex: Int,
    val preparationStartFrame: Int,
    val intervalMs: Long
) {
    var preparationEndFrame: Int = -1
    var forwardStartFrame: Int = -1
    var contactFrame: Int = -1
    var forwardEndFrame: Int = -1
    var returnStartFrame: Int = -1
    var returnEndFrame: Int = -1

    var minBackswingValue: Float = Float.MAX_VALUE
    var maxForwardValue: Float = Float.MIN_VALUE
    var peakVelocity: Float = 0f

    fun build(endFrame: Int): DetectedStroke {
        return DetectedStroke(
            strokeIndex = strokeIndex,
            preparationStartFrame = preparationStartFrame,
            preparationEndFrame = if (preparationEndFrame >= 0) preparationEndFrame else preparationStartFrame,
            forwardStartFrame = if (forwardStartFrame >= 0) forwardStartFrame else preparationStartFrame,
            contactFrame = if (contactFrame >= 0) contactFrame else forwardEndFrame,
            forwardEndFrame = if (forwardEndFrame >= 0) forwardEndFrame else endFrame,
            returnStartFrame = if (returnStartFrame >= 0) returnStartFrame else forwardEndFrame,
            returnEndFrame = endFrame,
            backswingMinValue = minBackswingValue,
            forwardPeakValue = maxForwardValue,
            peakVelocity = peakVelocity,
            strokeDurationMs = (endFrame - preparationStartFrame) * intervalMs,
            forwardSwingDurationMs = if (forwardStartFrame >= 0 && forwardEndFrame >= 0)
                (forwardEndFrame - forwardStartFrame) * intervalMs else 0L,
            isComplete = forwardStartFrame >= 0 && maxForwardValue > Float.MIN_VALUE
        )
    }
}

/**
 * Generic stroke detector for JSON pose data
 * Uses a position-based state machine to detect stroke phases
 */
class JsonStrokeDetector(
    private val config: StrokeDetectorConfig = StrokeDetectorConfig.FOREHAND
) {
    companion object {
        private const val TAG = "JsonStrokeDetector"

        /**
         * Convert NormalizedLandmark list to JsonPoseFrame format
         */
        fun fromNormalizedLandmarks(
            landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
            frameIndex: Int,
            timestampMs: Long
        ): JsonPoseFrame {
            return JsonPoseFrame(
                frameIndex = frameIndex,
                timestampMs = timestampMs,
                landmarks = landmarks.mapIndexed { index, lm ->
                    JsonLandmark(
                        index = index,
                        x = lm.x(),
                        y = lm.y(),
                        z = lm.z(),
                        visibility = lm.visibility().orElse(0f),
                        presence = lm.presence().orElse(0f)
                    )
                }
            )
        }
    }

    /** Detection states */
    private enum class DetectionState {
        IDLE,       // Waiting for movement
        BACKSWING,  // Preparation phase detected
        FORWARD,    // Forward swing in progress
        PEAK,       // At or near peak extension
        RETURNING   // Returning to ready position
    }

    private var currentState = DetectionState.IDLE
    private var currentStrokeData: StrokeBuilder? = null
    private val detectedStrokes = mutableListOf<DetectedStroke>()
    private val framePhases = mutableListOf<FramePhaseInfo>()
    private var intervalMs: Long = 100L

    /**
     * Detect strokes from JSON pose frames
     */
    fun detectStrokes(frames: List<JsonPoseFrame>, intervalMs: Long = 100L): StrokeDetectionResult {
        reset()
        this.intervalMs = intervalMs

        if (frames.isEmpty()) {
            Log.w(TAG, "No frames to process")
            return StrokeDetectionResult(emptyList(), emptyList(), 0)
        }

        // Step 1: Extract tracked positions
        val positions = extractPositions(frames)

        // Step 2: Apply smoothing
        val smoothedPositions = applyMovingAverage(positions, config.smoothingWindow)

        // Step 3: Calculate velocities
        val velocities = calculateVelocities(smoothedPositions)

        Log.d(TAG, "Processing ${frames.size} frames, positions range: ${positions.minOrNull()} to ${positions.maxOrNull()}")

        // Step 4: Process each frame through state machine
        for (i in frames.indices) {
            processFrame(
                frameIndex = i,
                rawValue = positions[i],
                smoothedValue = smoothedPositions[i],
                velocity = velocities.getOrElse(i) { 0f },
                timestamp = frames[i].timestampMs
            )
        }

        // Step 5: Finalize any in-progress stroke
        if (currentState != DetectionState.IDLE) {
            finalizeCurrentStroke(frames.lastIndex)
        }

        Log.i(TAG, "Detected ${detectedStrokes.size} strokes from ${frames.size} frames")

        return StrokeDetectionResult(
            strokes = detectedStrokes.toList(),
            framePhases = framePhases.toList(),
            totalFrames = frames.size
        )
    }

    /**
     * Extract position values from frames based on config
     */
    private fun extractPositions(frames: List<JsonPoseFrame>): List<Float> {
        return frames.map { frame ->
            val landmark = frame.landmarks.find { it.index == config.landmarkIndex }
            val value = when (config.trackingAxis) {
                TrackingAxis.X -> landmark?.x ?: 0.5f
                TrackingAxis.Y -> landmark?.y ?: 0.5f
                TrackingAxis.Z -> landmark?.z ?: 0f
            }
            if (config.invertDirection) 1f - value else value
        }
    }

    /**
     * Apply moving average smoothing
     */
    private fun applyMovingAverage(values: List<Float>, windowSize: Int): List<Float> {
        if (windowSize <= 1) return values

        return values.mapIndexed { index, _ ->
            val start = maxOf(0, index - windowSize / 2)
            val end = minOf(values.size, index + windowSize / 2 + 1)
            values.subList(start, end).average().toFloat()
        }
    }

    /**
     * Calculate per-frame velocities
     */
    private fun calculateVelocities(positions: List<Float>): List<Float> {
        return positions.mapIndexed { index, position ->
            if (index == 0) 0f
            else position - positions[index - 1]
        }
    }

    /**
     * Process a single frame through the state machine
     */
    private fun processFrame(
        frameIndex: Int,
        rawValue: Float,
        smoothedValue: Float,
        velocity: Float,
        timestamp: Long
    ) {
        when (currentState) {
            DetectionState.IDLE -> handleIdle(frameIndex, smoothedValue, velocity, timestamp)
            DetectionState.BACKSWING -> handleBackswing(frameIndex, smoothedValue, velocity, timestamp)
            DetectionState.FORWARD -> handleForward(frameIndex, smoothedValue, velocity, timestamp)
            DetectionState.PEAK -> handlePeak(frameIndex, smoothedValue, velocity, timestamp)
            DetectionState.RETURNING -> handleReturning(frameIndex, smoothedValue, velocity, timestamp)
        }
    }

    private fun handleIdle(frameIndex: Int, value: Float, velocity: Float, timestamp: Long) {
        // Look for backswing initiation
        if (value < config.backswingThreshold || velocity < -0.02f) {
            // Start new stroke
            currentStrokeData = StrokeBuilder(
                strokeIndex = detectedStrokes.size,
                preparationStartFrame = frameIndex,
                intervalMs = intervalMs
            )
            currentState = DetectionState.BACKSWING
            recordPhase(frameIndex, StrokePhase.BACKSWING, detectedStrokes.size)
            Log.d(TAG, "Frame $frameIndex: IDLE -> BACKSWING (value=$value)")
        } else {
            recordPhase(frameIndex, StrokePhase.READY, null)
        }
    }

    private fun handleBackswing(frameIndex: Int, value: Float, velocity: Float, timestamp: Long) {
        val builder = currentStrokeData ?: return

        // Track minimum value (deepest backswing point)
        if (value < builder.minBackswingValue) {
            builder.minBackswingValue = value
            builder.preparationEndFrame = frameIndex
        }

        // Detect transition to forward swing
        if (velocity > config.forwardVelocityThreshold && value > builder.minBackswingValue + 0.03f) {
            builder.forwardStartFrame = frameIndex
            currentState = DetectionState.FORWARD
            recordPhase(frameIndex, StrokePhase.FORWARD_SWING, builder.strokeIndex)
            Log.d(TAG, "Frame $frameIndex: BACKSWING -> FORWARD (velocity=$velocity)")
        } else {
            recordPhase(frameIndex, StrokePhase.BACKSWING, builder.strokeIndex)
        }

        // Timeout check
        if (frameIndex - builder.preparationStartFrame > config.maxStrokeFrames) {
            Log.w(TAG, "Frame $frameIndex: Backswing timeout, canceling stroke")
            cancelCurrentStroke(frameIndex)
        }
    }

    private fun handleForward(frameIndex: Int, value: Float, velocity: Float, timestamp: Long) {
        val builder = currentStrokeData ?: return

        // Track peak velocity (estimated contact point)
        if (velocity > builder.peakVelocity) {
            builder.peakVelocity = velocity
            builder.contactFrame = frameIndex
        }

        // Track maximum value (peak extension)
        if (value > builder.maxForwardValue) {
            builder.maxForwardValue = value
            builder.forwardEndFrame = frameIndex
        }

        // Detect peak reached - EITHER high position with slowing velocity OR position starting to drop
        val reachedHighPosition = value > config.forwardPeakThreshold && velocity < builder.peakVelocity * 0.5f
        val positionDropping = value < builder.maxForwardValue - 0.03f && builder.maxForwardValue > builder.minBackswingValue + 0.10f
        
        if (reachedHighPosition || positionDropping) {
            currentState = DetectionState.PEAK
            recordPhase(frameIndex, StrokePhase.CONTACT, builder.strokeIndex)
            Log.d(TAG, "Frame $frameIndex: FORWARD -> PEAK (value=$value, max=${builder.maxForwardValue}, positionDropping=$positionDropping)")
        } else {
            recordPhase(frameIndex, StrokePhase.FORWARD_SWING, builder.strokeIndex)
        }

        // Timeout check
        if (frameIndex - builder.preparationStartFrame > config.maxStrokeFrames) {
            Log.w(TAG, "Frame $frameIndex: Forward timeout, finalizing stroke")
            finalizeCurrentStroke(frameIndex)
        }
    }

    private fun handlePeak(frameIndex: Int, value: Float, velocity: Float, timestamp: Long) {
        val builder = currentStrokeData ?: return

        // Detect return motion starting - either by velocity OR by position drop from peak
        val velocityIndicatesReturn = velocity < config.returnVelocityThreshold
        val positionDroppedFromPeak = value < builder.maxForwardValue - 0.05f
        
        if (velocityIndicatesReturn || positionDroppedFromPeak) {
            builder.returnStartFrame = frameIndex
            currentState = DetectionState.RETURNING
            recordPhase(frameIndex, StrokePhase.FOLLOW_THROUGH, builder.strokeIndex)
            Log.d(TAG, "Frame $frameIndex: PEAK -> RETURNING (velocity=$velocity, positionDrop=$positionDroppedFromPeak)")
        } else {
            // Still at peak / follow through
            recordPhase(frameIndex, StrokePhase.CONTACT, builder.strokeIndex)
        }
    }

    private fun handleReturning(frameIndex: Int, value: Float, velocity: Float, timestamp: Long) {
        val builder = currentStrokeData ?: return

        // Check if returned to ready position
        val returnedToReady = value < config.readyPositionThreshold && abs(velocity) < 0.03f
        val newBackswingStarting = value < config.backswingThreshold

        if (returnedToReady || newBackswingStarting) {
            builder.returnEndFrame = frameIndex
            finalizeCurrentStroke(frameIndex)

            // If new backswing already starting, begin new stroke
            if (newBackswingStarting) {
                handleIdle(frameIndex, value, velocity, timestamp)
            } else {
                recordPhase(frameIndex, StrokePhase.RECOVERY, null)
            }
        } else {
            recordPhase(frameIndex, StrokePhase.FOLLOW_THROUGH, builder.strokeIndex)
        }

        // Timeout check
        val returnStartFrame = builder.returnStartFrame
        if (returnStartFrame >= 0 && frameIndex - returnStartFrame > config.maxStrokeFrames) {
            Log.w(TAG, "Frame $frameIndex: Return timeout, finalizing stroke")
            finalizeCurrentStroke(frameIndex)
        }
    }

    private fun recordPhase(frameIndex: Int, phase: StrokePhase, strokeIndex: Int?) {
        // Avoid duplicates if frame already recorded
        if (framePhases.any { it.frameIndex == frameIndex }) return

        framePhases.add(FramePhaseInfo(
            frameIndex = frameIndex,
            phase = phase,
            strokeIndex = strokeIndex,
            phaseProgress = 0f  // Could calculate based on position within phase
        ))
    }

    private fun finalizeCurrentStroke(endFrame: Int) {
        val builder = currentStrokeData ?: return

        // Validate stroke completeness
        val hasForwardSwing = builder.forwardStartFrame >= 0
        val hasSufficientExtension = (builder.maxForwardValue - builder.minBackswingValue) > config.minForwardExtension
        val hasSufficientFrames = (endFrame - builder.preparationStartFrame) >= config.minStrokeFrames
        val hasSufficientBackswing = builder.minBackswingValue < config.backswingThreshold

        if (hasForwardSwing && hasSufficientExtension && hasSufficientFrames && hasSufficientBackswing) {
            val stroke = builder.build(endFrame)
            detectedStrokes.add(stroke)
            Log.i(TAG, "Finalized stroke ${stroke.strokeIndex + 1}: " +
                    "frames ${stroke.preparationStartFrame}-${stroke.returnEndFrame}, " +
                    "backswing=${String.format("%.3f", stroke.backswingMinValue)}, " +
                    "peak=${String.format("%.3f", stroke.forwardPeakValue)}, " +
                    "duration=${stroke.strokeDurationMs}ms")
        } else {
            Log.d(TAG, "Discarded incomplete stroke: forward=$hasForwardSwing, " +
                    "extension=$hasSufficientExtension, frames=$hasSufficientFrames")
        }

        currentStrokeData = null
        currentState = DetectionState.IDLE
    }

    private fun cancelCurrentStroke(frameIndex: Int) {
        currentStrokeData = null
        currentState = DetectionState.IDLE
        recordPhase(frameIndex, StrokePhase.READY, null)
    }

    private fun reset() {
        currentState = DetectionState.IDLE
        currentStrokeData = null
        detectedStrokes.clear()
        framePhases.clear()
    }
}
