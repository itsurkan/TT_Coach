package com.ttcoachai.processors

import android.util.Log
import com.ttcoachai.PoseLandmarkerHelper
import com.ttcoachai.TTCoachApplication
import com.ttcoachai.managers.TrainingStateManager
import com.ttcoachai.models.AnalysisResult
import com.ttcoachai.models.StrokePhase
import com.ttcoachai.services.FeedbackGenerator
import com.ttcoachai.services.MotionAnalyzer

class PoseAnalysisProcessor(
    private val application: TTCoachApplication,
    private val motionAnalyzer: MotionAnalyzer,
    private val feedbackGenerator: FeedbackGenerator,
    private val stateManager: TrainingStateManager,
    private val onUIUpdate: () -> Unit
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var frameCounter: Int = 0
    
    private val phaseDetector = StrokePhaseDetector(feedbackGenerator)
    private val analysisLogger = PoseAnalysisLogger(application)
    
    private val currentStrokeResults = mutableListOf<AnalysisResult>()
    private var pendingFeedbackResult: AnalysisResult? = null
    private var totalCompletedStrokes = 0
    
    var latestAnalysisResult: AnalysisResult? = null
        private set

    companion object {
        private const val TAG = "PoseAnalysisProcessor"
        private const val LOG_INTERVAL_FRAMES = 30
    }
    
    fun startSession(exerciseId: String, exerciseName: String) {
        frameCounter = 0
        resetStrokeDetection()
        totalCompletedStrokes = 0
        Log.i(TAG, "Training session started for $exerciseName")
    }

    fun endSession() {
        frameCounter = 0
        totalCompletedStrokes = 0
    }

    fun release() {
        feedbackGenerator.release()
    }
    
    fun processResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        if (!stateManager.isTrainingActive) return
        
        val poseResult = resultBundle.results.firstOrNull() ?: return
        if (poseResult.landmarks().isEmpty()) return
        
        frameCounter++
        val startTime = System.currentTimeMillis()
        
        try {
            val previousPhase = phaseDetector.getCurrentPhase()
            val detection = phaseDetector.detect(poseResult)
            val detectedPhase = detection.phase
            
            val analysis = motionAnalyzer.analyzeStroke(poseResult, detectedPhase)
            val inferenceTime = System.currentTimeMillis() - startTime
            latestAnalysisResult = analysis
            
            if (detectedPhase != StrokePhase.READY) currentStrokeResults.add(analysis)
            
            mainHandler.post { onUIUpdate() }
            
            handleDelayedFeedback(previousPhase, detectedPhase)
            handleStrokeFinalization(previousPhase, detectedPhase, analysis)
            
            if (application.settingsManager.isDeveloperModeEnabled()) {
                analysisLogger.logAnalysis(null, frameCounter, analysis, inferenceTime)
                analysisLogger.logRawPose(null, frameCounter, poseResult, inferenceTime)
            }
            
            if (frameCounter % LOG_INTERVAL_FRAMES == 0) {
                Log.d(TAG, "Frame $frameCounter: score=${analysis.overallScore}%, inference=${inferenceTime}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing pose", e)
        }
    }

    private fun handleDelayedFeedback(previousPhase: StrokePhase, currentPhase: StrokePhase) {
        if (previousPhase == StrokePhase.BACKSWING && currentPhase == StrokePhase.FORWARD_SWING) {
            pendingFeedbackResult?.let { result ->
                val frequency = application.settingsManager.getFeedbackFrequency()
                if (totalCompletedStrokes > 0 && totalCompletedStrokes % frequency == 0) {
                    mainHandler.post { feedbackGenerator.playFeedbackAudio(result) }
                }
                pendingFeedbackResult = null
            }
        }
    }

    private fun handleStrokeFinalization(previousPhase: StrokePhase, currentPhase: StrokePhase, currentAnalysis: AnalysisResult) {
        if (previousPhase == StrokePhase.FOLLOW_THROUGH && currentPhase != StrokePhase.FOLLOW_THROUGH) {
            if (currentStrokeResults.isNotEmpty()) {
                totalCompletedStrokes++
                val bestResult = currentStrokeResults
                    .filter { it.phase == StrokePhase.CONTACT }
                    .maxByOrNull { it.overallScore }
                    ?: currentStrokeResults.maxByOrNull { it.overallScore }
                    ?: currentAnalysis

                stateManager.addAnalysisResult(bestResult)
                pendingFeedbackResult = bestResult
                
                val feedbackText = if (application.settingsManager.getFeedbackType() == 0) {
                    feedbackGenerator.generateShortFeedback(bestResult)
                } else {
                    feedbackGenerator.generateDetailedFeedback(bestResult)
                }
                stateManager.addFeedback(feedbackText)
                stateManager.addFeedbackItems(bestResult.feedbackItems)
                
                mainHandler.post { onUIUpdate() }
                currentStrokeResults.clear()
            }
        }
    }
    
    private fun resetStrokeDetection() {
        phaseDetector.reset()
        currentStrokeResults.clear()
        pendingFeedbackResult = null
        latestAnalysisResult = null
    }
    
    fun getFrameCount(): Int = frameCounter
    fun getCurrentPhase(): StrokePhase = phaseDetector.getCurrentPhase()
}
