package com.ttcoachai.processors

import android.util.Log
import com.ttcoachai.models.StrokePhase
import com.ttcoachai.services.FeedbackGenerator

/**
 * Detects stroke phase based on wrist movement velocity using Z-coordinate.
 */
class StrokePhaseDetector(
    private val feedbackGenerator: FeedbackGenerator
) {
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

    data class DetectionResult(
        val phase: StrokePhase,
        val isPhaseTransition: Boolean
    )

    fun detect(
        poseLandmarkerResult: com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
    ): DetectionResult {
        val landmarks = poseLandmarkerResult.worldLandmarks().firstOrNull() ?: return DetectionResult(currentPhase, false)
        if (landmarks.size <= 16) return DetectionResult(currentPhase, false)
        
        val wristZ = landmarks[16].z()
        if (previousWristZ != 0f) {
            wristVelocity = wristZ - previousWristZ
            velocityHistory.addLast(wristVelocity)
            if (velocityHistory.size > 5) velocityHistory.removeFirst()
        }
        previousWristZ = wristZ
        
        val avgVelocity = if (velocityHistory.isNotEmpty()) velocityHistory.average().toFloat() else wristVelocity
        phaseFrameCounter++
        
        val newPhase = when (currentPhase) {
            StrokePhase.READY -> if (avgVelocity < BACKSWING_VELOCITY_THRESHOLD && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                phaseFrameCounter = 0; StrokePhase.BACKSWING
            } else StrokePhase.READY
            
            StrokePhase.BACKSWING -> if (avgVelocity > 0 && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                phaseFrameCounter = 0; StrokePhase.FORWARD_SWING
            } else StrokePhase.BACKSWING
            
            StrokePhase.FORWARD_SWING -> if (avgVelocity > 2.0f && phaseFrameCounter >= 1) {
                phaseFrameCounter = 0; StrokePhase.CONTACT
            } else StrokePhase.FORWARD_SWING
            
            StrokePhase.CONTACT -> if (phaseFrameCounter >= 2) {
                phaseFrameCounter = 0; StrokePhase.FOLLOW_THROUGH
            } else StrokePhase.CONTACT
            
            StrokePhase.FOLLOW_THROUGH -> if (avgVelocity < VELOCITY_THRESHOLD / 2 && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                phaseFrameCounter = 0; StrokePhase.RECOVERY
            } else StrokePhase.FOLLOW_THROUGH
            
            StrokePhase.RECOVERY -> if (Math.abs(avgVelocity) < 0.005f && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                phaseFrameCounter = 0; StrokePhase.READY
            } else StrokePhase.RECOVERY
        }
        
        val isTransition = newPhase != currentPhase
        if (isTransition) {
            Log.d(TAG, "Phase transition: $currentPhase -> $newPhase (velocity: $avgVelocity)")
            if (newPhase == StrokePhase.FORWARD_SWING) feedbackGenerator.playTic()
            else if (newPhase == StrokePhase.CONTACT) feedbackGenerator.playTac()
        }
        
        currentPhase = newPhase
        return DetectionResult(currentPhase, isTransition)
    }
    
    fun reset() {
        currentPhase = StrokePhase.READY
        previousWristZ = 0f
        wristVelocity = 0f
        velocityHistory.clear()
        phaseFrameCounter = 0
    }

    fun getCurrentPhase(): StrokePhase = currentPhase
}
