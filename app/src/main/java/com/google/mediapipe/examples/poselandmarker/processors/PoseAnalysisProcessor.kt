/*
 * AI Coach for Table Tennis
 * Pose Analysis Processor - Handles real-time pose analysis and feedback generation
 */

package com.google.mediapipe.examples.poselandmarker.processors

import android.util.Log
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.TTCoachApplication
import com.google.mediapipe.examples.poselandmarker.core.logging.LandmarkData
import com.google.mediapipe.examples.poselandmarker.managers.TrainingStateManager
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer

/**
 * Processes pose analysis results in real-time
 * Handles: MediaPipe → MotionAnalyzer → FeedbackGenerator → UI → Logger pipeline
 */
class PoseAnalysisProcessor(
    private val application: TTCoachApplication,
    private val motionAnalyzer: MotionAnalyzer,
    private val feedbackGenerator: FeedbackGenerator,
    private val stateManager: TrainingStateManager,
    private val onUIUpdate: () -> Unit
) {
    private var frameCounter: Int = 0
    private var currentSessionId: String? = null
    
    // Stroke detection state
    private var currentPhase: StrokePhase = StrokePhase.READY
    private var previousWristZ: Float = 0f
    private var wristVelocity: Float = 0f
    private var phaseFrameCounter: Int = 0
    private val velocityHistory = ArrayDeque<Float>(5)
    private var frameIndex: Int = 0
    private var isInsideStroke: Boolean = false
    private val currentStrokeResults = mutableListOf<AnalysisResult>()
    private var pendingFeedbackResult: AnalysisResult? = null
    private var audioPlayedForCurrentStroke = false
    private var totalCompletedStrokes = 0 // Counter for frequency logic
    
    companion object {
        private const val TAG = "PoseAnalysisProcessor"
        private const val LOG_INTERVAL_FRAMES = 30 // Log every 30 frames (~1 sec at 30 FPS)
        
        // Stroke detection thresholds
        private const val VELOCITY_THRESHOLD = 0.02f // Threshold for forward motion
        private const val MIN_PHASE_FRAMES = 3 // Minimum frames to stay in a phase
        private const val BACKSWING_VELOCITY_THRESHOLD = -0.015f // Negative velocity for backswing
    }
    
    /**
     * Start a new training session
     */
    fun startSession(exerciseId: String, exerciseName: String) {
        val fileLogger = application.getFileLogger()
        currentSessionId = fileLogger.startTrainingSession(
            exerciseId = exerciseId,
            exerciseName = exerciseName
        )
        frameCounter = 0
        resetStrokeDetection()
        totalCompletedStrokes = 0 // Reset stroke counter for new session
        Log.i(TAG, "Training session started: $currentSessionId")
    }

    /**
     * Test audio feedback manually
     */
    fun testAudio() {
        Log.i(TAG, "Testing audio: Playing TIC")
        feedbackGenerator.playTic()
        
        // Schedule TAC to play after 500ms
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.i(TAG, "Testing audio: Playing TAC")
            feedbackGenerator.playTac()
        }, 500)
    }
    
    /**
     * End current training session
     */
    fun endSession() {
        currentSessionId?.let { sessionId ->
            val fileLogger = application.getFileLogger()
            fileLogger.endTrainingSession(
                totalStrokes = stateManager.getStrokeCount(),
                goodStrokes = stateManager.getGoodStrokesCount(),
                averageScore = stateManager.getAverageScore().toFloat()
            )
            Log.i(TAG, "Training session ended: $sessionId")
        }
        currentSessionId = null
        frameCounter = 0
        totalCompletedStrokes = 0
    }

    /**
     * Release resources
     */
    fun release() {
        feedbackGenerator.release()
    }
    
    /**
     * Process pose detection results from MediaPipe
     * This is the main pipeline: MediaPipe → Analyzer → Generator → UI → Logger
     */
    fun processResults(resultBundle: PoseLandmarkerHelper.ResultBundle, onUIUpdate: () -> Unit) {
        // Skip if training not active
        if (!stateManager.isTrainingActive) {
            return
        }
        
        // Get pose landmarks from MediaPipe
        val poseLandmarkerResult = resultBundle.results.firstOrNull() ?: return
        if (poseLandmarkerResult.landmarks().isEmpty()) return
        
        // Track frame for logging
        frameCounter++
        val startTime = System.currentTimeMillis()
        
        try {
            // Detect stroke phase based on wrist movement
            // Detect phase and run analyze
            val previousPhase = currentPhase // Capture before detection update
            val detectionResult = detectStrokePhase(poseLandmarkerResult)
            val detectedPhase = detectionResult.phase
            
            // Analyze stroke technique using MotionAnalyzer
            val analysisResult: AnalysisResult = motionAnalyzer.analyzeStroke(
                poseLandmarkerResult = poseLandmarkerResult,
                phase = detectedPhase
            )
            
            val inferenceTime = System.currentTimeMillis() - startTime
            
            // Update state (real-time stats if needed, but we might want to move this to finalizeStroke)
            // For now, let's keep adding results to stateManager for real-time graphs if they exist
            // stateManager.addAnalysisResult(analysisResult)
            
            // Collect results during active stroke phases
            if (detectedPhase != StrokePhase.READY) {
                currentStrokeResults.add(analysisResult)
            }
            
            // Trigger UI update callback (for animations/overlays)
            onUIUpdate()
            
            // -------------------------------------------------------------------------
            // 1. DELAYED FEEDBACK (Trigger after NEXT BACKSWING completion)
            // -------------------------------------------------------------------------
            if (previousPhase == StrokePhase.BACKSWING && detectedPhase == StrokePhase.FORWARD_SWING) {
                pendingFeedbackResult?.let { result ->
                    val frequency = application.settingsManager.getFeedbackFrequency()
                    if (totalCompletedStrokes > 0 && totalCompletedStrokes % frequency == 0) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Log.i(TAG, "Playing DELAYED Audio Feedback from previous stroke (Frequency: $frequency, Stroke: $totalCompletedStrokes)")
                            
                            feedbackGenerator.playFeedbackAudio(result)
                        }
                    }
                    pendingFeedbackResult = null // Clear after playing
                    audioPlayedForCurrentStroke = true
                }
            }
            
            // Reset for current stroke audio tracking
            if (detectionResult.isPhaseTransition && detectedPhase == StrokePhase.BACKSWING) {
                audioPlayedForCurrentStroke = false
            }
            
            // -------------------------------------------------------------------------
            // 2. STROKE FINALIZATION (Trigger after FOLLOW_THROUGH)
            // -------------------------------------------------------------------------
            if (previousPhase == StrokePhase.FOLLOW_THROUGH && detectedPhase != StrokePhase.FOLLOW_THROUGH) {
                if (currentStrokeResults.isNotEmpty()) {
                    totalCompletedStrokes++
                    
                    // Find the best representative result for the stroke
                    val bestResult = currentStrokeResults
                        .filter { it.phase == StrokePhase.CONTACT }
                        .maxByOrNull { it.overallScore }
                        ?: currentStrokeResults.maxByOrNull { it.overallScore }
                        ?: analysisResult

                    // Update state and UI with the finalized stroke result
                    stateManager.addAnalysisResult(bestResult)
                    
                    // Store for playback during the NEXT stroke's backswing
                    pendingFeedbackResult = bestResult
                    
                    // Generate and add textual feedback
                    val isShort = application.settingsManager.getFeedbackType() == 0
                    val feedbackText = if (isShort) {
                        feedbackGenerator.generateShortFeedback(bestResult)
                    } else {
                        feedbackGenerator.generateDetailedFeedback(bestResult)
                    }
                    stateManager.addFeedback(feedbackText)
                    stateManager.addFeedbackItems(bestResult.feedbackItems)
                    
                    // Update UI on main thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onUIUpdate()
                    }
                    
                    currentStrokeResults.clear()
                    isInsideStroke = false
                    Log.d(TAG, "Stroke finalized: $totalCompletedStrokes")
                }
            }
            
            // Log stroke analysis
            logAnalysisResults(analysisResult, inferenceTime)
            logRawPoseLandmarks(poseLandmarkerResult, inferenceTime)
            
            // Periodic debug log
            if (frameCounter % LOG_INTERVAL_FRAMES == 0) {
                Log.d(TAG, "Frame $frameCounter: score=${analysisResult.overallScore}%, inference=${inferenceTime}ms")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing pose", e)
            val fileLogger = application.getFileLogger()
            fileLogger.logError(e, "processResults - pose analysis")
        }
    }
    
    /**
     * Log analysis results asynchronously
     */
    private fun logAnalysisResults(analysisResult: AnalysisResult, inferenceTime: Long) {
        currentSessionId?.let { sessionId ->
            val fileLogger = application.getFileLogger()
            
            // Log processed stroke analysis (angles, scores, errors)
            fileLogger.logStrokeAnalysis(
                result = analysisResult,
                sessionId = sessionId,
                inferenceTimeMs = inferenceTime,
                frameNumber = frameCounter
            )
            
            fileLogger.logPerformanceMetric(
                metricName = "inference_time_ms",
                value = inferenceTime.toFloat(),
                sessionId = sessionId
            )
        }
    }
    
    /**
     * Log raw pose landmarks from MediaPipe (33 keypoints)
     * Enable this for debugging or data collection
     */
    private fun logRawPoseLandmarks(
        poseLandmarkerResult: com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult,
        inferenceTime: Long
    ) {
        currentSessionId?.let { sessionId ->
            val fileLogger = application.getFileLogger()
            
            // Extract landmarks (normalized coordinates 0.0-1.0)
            val landmarks = poseLandmarkerResult.landmarks().firstOrNull()?.map { landmark ->
                LandmarkData(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z(),
                    visibility = landmark.visibility().orElse(0f),
                    presence = landmark.presence().orElse(0f)
                )
            } ?: return
            
            // Extract world landmarks (3D coordinates in meters)
            val worldLandmarks = poseLandmarkerResult.worldLandmarks().firstOrNull()?.map { landmark ->
                LandmarkData(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z(),
                    visibility = landmark.visibility().orElse(0f),
                    presence = landmark.presence().orElse(0f)
                )
            }
            
            fileLogger.logRawPose(
                sessionId = sessionId,
                frameNumber = frameCounter,
                inferenceTimeMs = inferenceTime,
                landmarks = landmarks,
                worldLandmarks = worldLandmarks
            )
        }
    }
    
    /**
     * Get current frame counter
     */
    fun getFrameCount(): Int = frameCounter
    
    /**
     * Get current session ID
     */
    fun getSessionId(): String? = currentSessionId
    
    /**
     * Check if session is active
     */
    fun hasActiveSession(): Boolean = currentSessionId != null
    
    /**
     * Detection Result Wrapper
     */
    data class PhaseDetectionResult(
        val phase: StrokePhase,
        val isPhaseTransition: Boolean
    )

    /**
     * Detect stroke phase based on wrist movement velocity
     * Uses Z-coordinate (forward/backward motion) to identify phases
     */
    private fun detectStrokePhase(
        poseLandmarkerResult: com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
    ): PhaseDetectionResult {
        val landmarks = poseLandmarkerResult.worldLandmarks().firstOrNull() ?: return PhaseDetectionResult(currentPhase, false)
        
        // Get right wrist position (landmark 16)
        if (landmarks.size <= 16) return PhaseDetectionResult(currentPhase, false)
        val wristZ = landmarks[16].z()
        
        // Calculate velocity (difference from previous frame)
        if (previousWristZ != 0f) {
            wristVelocity = wristZ - previousWristZ
            velocityHistory.addLast(wristVelocity)
            if (velocityHistory.size > 5) velocityHistory.removeFirst()
        }
        previousWristZ = wristZ
        
        // Smooth velocity using moving average
        val avgVelocity = if (velocityHistory.isNotEmpty()) {
            velocityHistory.average().toFloat()
        } else {
            wristVelocity
        }
        
        // Phase transition logic with minimum frame requirements
        phaseFrameCounter++
        
        val newPhase = when (currentPhase) {
            StrokePhase.READY -> {
                // Transition to backswing when backward motion detected
                if (avgVelocity < BACKSWING_VELOCITY_THRESHOLD && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                    phaseFrameCounter = 0
                    StrokePhase.BACKSWING
                } else {
                    StrokePhase.READY
                }
            }
            
            StrokePhase.BACKSWING -> {
                // Transition to forward swing when motion reverses
                if (avgVelocity > 0 && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                    phaseFrameCounter = 0
                    StrokePhase.FORWARD_SWING
                } else {
                    StrokePhase.BACKSWING
                }
            }
            
            StrokePhase.FORWARD_SWING -> {
                // Transition to contact when velocity peaks
                // Relaxed threshold for Contact for faster detection
                if (avgVelocity > 2.0f && phaseFrameCounter >= 1) { // lowered from 3.0/2
                    phaseFrameCounter = 0
                    StrokePhase.CONTACT
                } else {
                    StrokePhase.FORWARD_SWING
                }
            }
            
            StrokePhase.CONTACT -> {
                // Transition to follow-through detection
                // Quick transition out of Contact
                if (phaseFrameCounter >= 2) { // just a few frames for contact
                     phaseFrameCounter = 0
                     StrokePhase.FOLLOW_THROUGH
                } else {
                    StrokePhase.CONTACT
                }
            }
            
            StrokePhase.FOLLOW_THROUGH -> {
                // Transition to recovery when motion stops OR slows down significantly
                 if (avgVelocity < VELOCITY_THRESHOLD / 2 && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                    phaseFrameCounter = 0
                    StrokePhase.RECOVERY
                } else {
                    StrokePhase.FOLLOW_THROUGH
                }
            }
            
            StrokePhase.RECOVERY -> {
                // Return to ready position
                if (Math.abs(avgVelocity) < 0.005f && phaseFrameCounter >= MIN_PHASE_FRAMES) {
                    phaseFrameCounter = 0
                    StrokePhase.READY
                } else {
                    StrokePhase.RECOVERY
                }
            }
        }
        
        // Log phase transitions
        if (newPhase != currentPhase) {
            Log.d(TAG, "Phase transition: $currentPhase -> $newPhase (velocity: $avgVelocity)")
            
            // Audio feedback for rhythm
            if (newPhase == StrokePhase.FORWARD_SWING) {
                feedbackGenerator.playTic()
            } else if (newPhase == StrokePhase.CONTACT) {
                feedbackGenerator.playTac()
            }
        }
        
        currentPhase = newPhase
        return PhaseDetectionResult(currentPhase, newPhase != currentPhase)
    }
    
    /**
     * Reset stroke detection state
     */
    private fun resetStrokeDetection() {
        currentPhase = StrokePhase.READY
        isInsideStroke = false
        frameIndex = 0
        currentStrokeResults.clear()
        pendingFeedbackResult = null
        velocityHistory.clear()
        phaseFrameCounter = 0
        audioPlayedForCurrentStroke = false
    }
    
    /**
     * Get current detected phase
     */
    fun getCurrentPhase(): StrokePhase = currentPhase
}
