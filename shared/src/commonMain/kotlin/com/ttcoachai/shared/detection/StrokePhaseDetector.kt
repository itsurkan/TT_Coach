/*
 * AI Coach for Table Tennis
 * StrokePhaseDetector — Real-time phase detection (platform-independent)
 */

package com.ttcoachai.shared.detection

import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.StrokePhase

/**
 * Detects stroke phase based on wrist movement velocity using Z-coordinate.
 * Stateful — call reset() between sessions.
 *
 * Extracted from app/src/main/java/com/ttcoachai/processors/StrokePhaseDetector.kt.
 * Replaced android.util.Log with println() per research R3.
 * Replaced MediaPipe PoseLandmarkerResult with List<Landmark3D> per contract.
 * Removed FeedbackGenerator dependency (caller handles phase transitions).
 */
class StrokePhaseDetector {

    private var currentPhase: StrokePhase = StrokePhase.READY
    private var previousWristZ: Float = 0f
    private var wristVelocity: Float = 0f
    private var phaseFrameCounter: Int = 0
    private val velocityHistory = ArrayDeque<Float>(5)

    companion object {
        private const val TAG = "StrokePhaseDetector"
        private const val VELOCITY_THRESHOLD = 0.02f
        private const val MIN_PHASE_FRAMES = 3
        private const val BACKSWING_VELOCITY_THRESHOLD = -0.015f
    }

    /**
     * Process a single frame and return the current stroke phase.
     * Stateful: maintains velocity history across calls.
     *
     * @param landmarks List of Landmark3D (world coordinates). Landmark index 16 = right wrist.
     * @param timestampMs Frame timestamp (currently unused; reserved for future frame-rate-aware velocity)
     * @return Current StrokePhase
     */
    fun detect(landmarks: List<Landmark3D>, timestampMs: Long): StrokePhase {
        if (landmarks.size <= 16) return currentPhase

        val wristZ = landmarks[16].z
        if (previousWristZ != 0f) {
            wristVelocity = wristZ - previousWristZ
            velocityHistory.addLast(wristVelocity)
            if (velocityHistory.size > 5) velocityHistory.removeFirst()
        }
        previousWristZ = wristZ

        val avgVelocity = if (velocityHistory.isNotEmpty()) {
            velocityHistory.average().toFloat()
        } else {
            wristVelocity
        }
        phaseFrameCounter++

        val newPhase = when (currentPhase) {
            StrokePhase.READY ->
                if (avgVelocity < BACKSWING_VELOCITY_THRESHOLD && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                    phaseFrameCounter = 0; StrokePhase.BACKSWING
                } else StrokePhase.READY

            StrokePhase.BACKSWING ->
                if (avgVelocity > 0 && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                    phaseFrameCounter = 0; StrokePhase.FORWARD_SWING
                } else StrokePhase.BACKSWING

            StrokePhase.FORWARD_SWING ->
                if (avgVelocity > 0.05f && phaseFrameCounter >= 1) {
                    phaseFrameCounter = 0; StrokePhase.CONTACT
                } else StrokePhase.FORWARD_SWING

            StrokePhase.CONTACT ->
                if (phaseFrameCounter >= 2) {
                    phaseFrameCounter = 0; StrokePhase.FOLLOW_THROUGH
                } else StrokePhase.CONTACT

            StrokePhase.FOLLOW_THROUGH ->
                if (avgVelocity < VELOCITY_THRESHOLD / 2 && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                    phaseFrameCounter = 0; StrokePhase.RECOVERY
                } else StrokePhase.FOLLOW_THROUGH

            StrokePhase.RECOVERY ->
                if (kotlin.math.abs(avgVelocity) < 0.005f && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                    phaseFrameCounter = 0; StrokePhase.READY
                } else StrokePhase.RECOVERY
        }

        val isTransition = newPhase != currentPhase
        if (isTransition) {
            println("$TAG: Phase transition: $currentPhase -> $newPhase (velocity: $avgVelocity)")
        }

        currentPhase = newPhase
        return currentPhase
    }

    /** Reset internal state — call between sessions. */
    fun reset() {
        currentPhase = StrokePhase.READY
        previousWristZ = 0f
        wristVelocity = 0f
        velocityHistory.clear()
        phaseFrameCounter = 0
    }

    /** Get the current detected phase. */
    fun getCurrentPhase(): StrokePhase = currentPhase
}
