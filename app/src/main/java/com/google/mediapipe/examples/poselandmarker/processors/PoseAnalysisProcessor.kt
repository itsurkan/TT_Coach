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
import com.google.mediapipe.examples.poselandmarker.managers.TrainingUIController
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
    private val uiController: TrainingUIController
) {
    private var frameCounter: Int = 0
    private var currentSessionId: String? = null
    
    companion object {
        private const val TAG = "PoseAnalysisProcessor"
        private const val LOG_INTERVAL_FRAMES = 30 // Log every 30 frames (~1 sec at 30 FPS)
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
        Log.i(TAG, "Training session started: $currentSessionId")
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
            // Analyze stroke technique using MotionAnalyzer
            val analysisResult: AnalysisResult = motionAnalyzer.analyzeStroke(
                poseLandmarkerResult = poseLandmarkerResult,
                phase = StrokePhase.CONTACT // TODO: Implement phase detection
            )
            
            val inferenceTime = System.currentTimeMillis() - startTime
            
            // Generate feedback based on analysis
            val feedback = if (analysisResult.isSuccessful()) {
                feedbackGenerator.generateShortFeedback(analysisResult)
            } else {
                feedbackGenerator.generateDetailedFeedback(analysisResult)
            }
            
            // Update state and UI
            stateManager.addAnalysisResult(analysisResult)
            stateManager.addFeedback(feedback)
            uiController.updateFeedbackText(feedback)
            uiController.updateStats()
            
            // Trigger UI update callback
            onUIUpdate()
            
            // Log stroke analysis asynchronously (zero latency impact)
            logAnalysisResults(analysisResult, inferenceTime)
            
            // 🆕 Log raw pose landmarks (enable for data collection)
            // Uncomment the line below to enable raw pose logging:
            // logRawPoseLandmarks(poseLandmarkerResult, inferenceTime)
            
            // Debug log every LOG_INTERVAL_FRAMES
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
}
