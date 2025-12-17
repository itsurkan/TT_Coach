package com.ttcoach.analysis

import com.ttcoach.cv.KeyPoint
import com.ttcoach.cv.MediaPipePoseProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates pose analysis by combining MediaPipe pose tracking with technique analysis and hit detection
 */
class PoseAnalysisCoordinator(
    private val poseProcessor: MediaPipePoseProcessor,
    private val useRightArm: Boolean = true
) {
    
    private val techniqueAnalyzer = TechniqueAnalyzer()
    private val hitDetector = HitDetector()
    
    private val _techniqueAnalysis = MutableStateFlow<TechniqueAnalysis?>(null)
    val techniqueAnalysis: StateFlow<TechniqueAnalysis?> = _techniqueAnalysis.asStateFlow()
    
    private val _hitDetection = MutableStateFlow<HitDetectionResult?>(null)
    val hitDetection: StateFlow<HitDetectionResult?> = _hitDetection.asStateFlow()
    
    /**
     * Process a new frame with pose data
     * @param ballPosition Current ball position (if detected), null otherwise
     * @param timestamp Frame timestamp
     */
    fun processFrame(ballPosition: android.graphics.PointF?, timestamp: Long) {
        val keyPoints = poseProcessor.getKeyPoints() ?: return
        
        // Analyze technique
        val analysis = techniqueAnalyzer.analyzeTechnique(keyPoints, useRightArm)
        _techniqueAnalysis.value = analysis
        
        // Detect hit
        val hitResult = hitDetector.detectHit(keyPoints, ballPosition, timestamp, useRightArm)
        _hitDetection.value = hitResult
    }
    
    /**
     * Reset analysis state (call when starting new training session)
     */
    fun reset() {
        hitDetector.reset()
        _techniqueAnalysis.value = null
        _hitDetection.value = null
    }
}

