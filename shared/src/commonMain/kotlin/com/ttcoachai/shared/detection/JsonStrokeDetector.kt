/*
 * AI Coach for Table Tennis
 * JsonStrokeDetector — Batch stroke detection from pose frame sequences (platform-independent)
 */

package com.ttcoachai.shared.detection

import com.ttcoachai.shared.models.DetectedStroke
import com.ttcoachai.shared.models.FramePhaseInfo
import com.ttcoachai.shared.models.PoseFrame
import com.ttcoachai.shared.models.StrokeDetectionResult
import com.ttcoachai.shared.models.StrokeDetectorConfig
import com.ttcoachai.shared.models.StrokePhase
import com.ttcoachai.shared.models.TrackingAxis
import kotlin.math.abs
import kotlin.math.roundToLong

/** KMP-portable replacement for JVM-only `"%.3f".format(v)` (log output only). */
private fun fmt3(value: Float): String {
    val milli = (value.toDouble() * 1000).roundToLong()
    val sign = if (milli < 0) "-" else ""
    val absMilli = if (milli < 0) -milli else milli
    return "$sign${absMilli / 1000}.${(absMilli % 1000).toString().padStart(3, '0')}"
}

/**
 * Generic stroke detector for JSON pose data.
 * Uses a position-based state machine to detect stroke phases.
 *
 * Extracted from app/src/main/java/com/ttcoachai/services/JsonStrokeDetector.kt.
 * Replaced JsonLandmark → Landmark3D (shared model) per migration note 1.
 * Replaced JsonPoseFrame → PoseFrame (shared model) per migration note 2.
 * Replaced android.util.Log with println() per research R3.
 * Removed fromNormalizedLandmarks() helper (now in MediaPipeMapper in Android module).
 */
class JsonStrokeDetector(
    private val config: StrokeDetectorConfig = StrokeDetectorConfig.FOREHAND
) {
    companion object {
        private const val TAG = "JsonStrokeDetector"
    }

    /** Detection states */
    private enum class DetectionState {
        IDLE,
        BACKSWING,
        FORWARD,
        PEAK,
        RETURNING
    }

    private var currentState = DetectionState.IDLE
    private var currentStrokeData: StrokeBuilder? = null
    private val detectedStrokes = mutableListOf<DetectedStroke>()
    private val framePhases = mutableListOf<FramePhaseInfo>()
    private var intervalMs: Long = 100L

    /**
     * Detect strokes from a sequence of pose frames.
     *
     * @param frames Ordered list of PoseFrame (by timestampMs)
     * @return StrokeDetectionResult with detected strokes and per-frame phases
     */
    fun detect(frames: List<PoseFrame>): StrokeDetectionResult {
        reset()

        if (frames.isEmpty()) {
            println("$TAG: No frames to process")
            return StrokeDetectionResult(emptyList(), emptyList(), 0)
        }

        // Compute frame interval from timestamps if possible
        if (frames.size >= 2) {
            intervalMs = (frames[1].timestampMs - frames[0].timestampMs).coerceAtLeast(1L)
        }

        // Step 1: Extract tracked positions
        val positions = extractPositions(frames)

        // Step 2: Apply smoothing
        val smoothedPositions = applyMovingAverage(positions, config.smoothingWindow)

        // Step 3: Calculate velocities
        val velocities = calculateVelocities(smoothedPositions)

        println("$TAG: Processing ${frames.size} frames, positions range: ${positions.minOrNull()} to ${positions.maxOrNull()}")

        // Step 4: Process each frame through state machine
        for (i in frames.indices) {
            processFrame(
                frameIndex = i,
                smoothedValue = smoothedPositions[i],
                velocity = velocities.getOrElse(i) { 0f },
                timestamp = frames[i].timestampMs
            )
        }

        // Step 5: Finalize any in-progress stroke
        if (currentState != DetectionState.IDLE) {
            finalizeCurrentStroke(frames.lastIndex)
        }

        println("$TAG: Detected ${detectedStrokes.size} strokes from ${frames.size} frames")

        return StrokeDetectionResult(
            strokes = detectedStrokes.toList(),
            framePhases = framePhases.toList(),
            totalFrames = frames.size
        )
    }

    /**
     * Extract position values from frames based on config using shared Landmark3D.
     */
    private fun extractPositions(frames: List<PoseFrame>): List<Float> {
        return frames.map { frame ->
            val landmark = frame.landmarks.getOrNull(config.landmarkIndex)
            val value = if (landmark != null) {
                when (config.trackingAxis) {
                    TrackingAxis.X -> landmark.x
                    TrackingAxis.Y -> landmark.y
                    TrackingAxis.Z -> landmark.z
                }
            } else {
                when (config.trackingAxis) {
                    TrackingAxis.X, TrackingAxis.Y -> 0.5f
                    TrackingAxis.Z -> 0f
                }
            }
            if (config.invertDirection) 1f - value else value
        }
    }

    private fun applyMovingAverage(values: List<Float>, windowSize: Int): List<Float> {
        if (windowSize <= 1) return values
        return values.mapIndexed { index, _ ->
            val start = maxOf(0, index - windowSize / 2)
            val end = minOf(values.size, index + windowSize / 2 + 1)
            values.subList(start, end).average().toFloat()
        }
    }

    private fun calculateVelocities(positions: List<Float>): List<Float> {
        return positions.mapIndexed { index, position ->
            if (index == 0) 0f else position - positions[index - 1]
        }
    }

    private fun processFrame(
        frameIndex: Int,
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
        if (value < config.backswingThreshold || velocity < -0.02f) {
            currentStrokeData = StrokeBuilder(
                strokeIndex = detectedStrokes.size,
                preparationStartFrame = frameIndex,
                intervalMs = intervalMs
            )
            currentState = DetectionState.BACKSWING
            recordPhase(frameIndex, StrokePhase.BACKSWING, detectedStrokes.size)
            println("$TAG: Frame $frameIndex: IDLE -> BACKSWING (value=$value)")
        } else {
            recordPhase(frameIndex, StrokePhase.READY, null)
        }
    }

    private fun handleBackswing(frameIndex: Int, value: Float, velocity: Float, timestamp: Long) {
        val builder = currentStrokeData ?: return

        if (value < builder.minBackswingValue) {
            builder.minBackswingValue = value
            builder.preparationEndFrame = frameIndex
        }

        if (velocity > config.forwardVelocityThreshold && value > builder.minBackswingValue + 0.03f) {
            builder.forwardStartFrame = frameIndex
            currentState = DetectionState.FORWARD
            recordPhase(frameIndex, StrokePhase.FORWARD_SWING, builder.strokeIndex)
            println("$TAG: Frame $frameIndex: BACKSWING -> FORWARD (velocity=$velocity)")
        } else {
            recordPhase(frameIndex, StrokePhase.BACKSWING, builder.strokeIndex)
        }

        if (frameIndex - builder.preparationStartFrame > config.maxStrokeFrames) {
            println("$TAG: Frame $frameIndex: Backswing timeout, canceling stroke")
            cancelCurrentStroke(frameIndex)
        }
    }

    private fun handleForward(frameIndex: Int, value: Float, velocity: Float, timestamp: Long) {
        val builder = currentStrokeData ?: return

        if (velocity > builder.peakVelocity) {
            builder.peakVelocity = velocity
            builder.contactFrame = frameIndex
        }

        if (value > builder.maxForwardValue) {
            builder.maxForwardValue = value
            builder.forwardEndFrame = frameIndex
        }

        val reachedHighPosition = value > config.forwardPeakThreshold && velocity < builder.peakVelocity * 0.5f
        val positionDropping = value < builder.maxForwardValue - 0.03f &&
                builder.maxForwardValue > builder.minBackswingValue + 0.10f

        if (reachedHighPosition || positionDropping) {
            currentState = DetectionState.PEAK
            recordPhase(frameIndex, StrokePhase.CONTACT, builder.strokeIndex)
            println("$TAG: Frame $frameIndex: FORWARD -> PEAK (value=$value, max=${builder.maxForwardValue}, positionDropping=$positionDropping)")
        } else {
            recordPhase(frameIndex, StrokePhase.FORWARD_SWING, builder.strokeIndex)
        }

        if (frameIndex - builder.preparationStartFrame > config.maxStrokeFrames) {
            println("$TAG: Frame $frameIndex: Forward timeout, finalizing stroke")
            finalizeCurrentStroke(frameIndex)
        }
    }

    private fun handlePeak(frameIndex: Int, value: Float, velocity: Float, timestamp: Long) {
        val builder = currentStrokeData ?: return

        val velocityIndicatesReturn = velocity < config.returnVelocityThreshold
        val positionDroppedFromPeak = value < builder.maxForwardValue - 0.05f

        if (velocityIndicatesReturn || positionDroppedFromPeak) {
            builder.returnStartFrame = frameIndex
            currentState = DetectionState.RETURNING
            recordPhase(frameIndex, StrokePhase.FOLLOW_THROUGH, builder.strokeIndex)
            println("$TAG: Frame $frameIndex: PEAK -> RETURNING (velocity=$velocity, positionDrop=$positionDroppedFromPeak)")
        } else {
            recordPhase(frameIndex, StrokePhase.CONTACT, builder.strokeIndex)
        }
    }

    private fun handleReturning(frameIndex: Int, value: Float, velocity: Float, timestamp: Long) {
        val builder = currentStrokeData ?: return

        val returnedToReady = value < config.readyPositionThreshold && abs(velocity) < 0.03f
        val newBackswingStarting = value < config.backswingThreshold

        if (returnedToReady || newBackswingStarting) {
            builder.returnEndFrame = frameIndex
            finalizeCurrentStroke(frameIndex)

            if (newBackswingStarting) {
                handleIdle(frameIndex, value, velocity, timestamp)
            } else {
                recordPhase(frameIndex, StrokePhase.RECOVERY, null)
            }
        } else {
            recordPhase(frameIndex, StrokePhase.FOLLOW_THROUGH, builder.strokeIndex)
        }

        val returnStartFrame = builder.returnStartFrame
        if (returnStartFrame >= 0 && frameIndex - returnStartFrame > config.maxStrokeFrames) {
            println("$TAG: Frame $frameIndex: Return timeout, finalizing stroke")
            finalizeCurrentStroke(frameIndex)
        }
    }

    private fun recordPhase(frameIndex: Int, phase: StrokePhase, strokeIndex: Int?) {
        if (framePhases.any { it.frameIndex == frameIndex }) return
        framePhases.add(
            FramePhaseInfo(
                frameIndex = frameIndex,
                phase = phase,
                strokeIndex = strokeIndex,
                phaseProgress = 0f
            )
        )
    }

    private fun finalizeCurrentStroke(endFrame: Int) {
        val builder = currentStrokeData ?: return

        val hasForwardSwing = builder.forwardStartFrame >= 0
        val hasSufficientExtension = (builder.maxForwardValue - builder.minBackswingValue) > config.minForwardExtension
        val hasSufficientFrames = (endFrame - builder.preparationStartFrame) >= config.minStrokeFrames
        val hasSufficientBackswing = builder.minBackswingValue < config.backswingThreshold

        if (hasForwardSwing && hasSufficientExtension && hasSufficientFrames && hasSufficientBackswing) {
            val stroke = builder.build(endFrame)
            detectedStrokes.add(stroke)
            println(
                "$TAG: Finalized stroke ${stroke.strokeIndex + 1}: " +
                        "frames ${stroke.preparationStartFrame}-${stroke.returnEndFrame}, " +
                        "backswing=${fmt3(stroke.backswingMinValue)}, " +
                        "peak=${fmt3(stroke.forwardPeakValue)}, " +
                        "duration=${stroke.strokeDurationMs}ms"
            )
        } else {
            println(
                "$TAG: Discarded incomplete stroke: forward=$hasForwardSwing, " +
                        "extension=$hasSufficientExtension, frames=$hasSufficientFrames"
            )
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

    /**
     * Internal builder for constructing stroke data during detection.
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
}
