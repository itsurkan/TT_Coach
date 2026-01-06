/*
 * AI Coach for Table Tennis
 * Video Debug Processor - Extracts frames from video and processes through MediaPipe
 */

package com.google.mediapipe.examples.poselandmarker.processors

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.core.logging.providers.LocalFileLogger
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer
import java.util.concurrent.ConcurrentHashMap

/**
 * Processes video files frame-by-frame for debugging
 * Extracts frames, runs through MediaPipe, stores results for playback
 */
class VideoDebugProcessor(
    private val context: Context,
    private val motionAnalyzer: MotionAnalyzer,
    private val feedbackGenerator: FeedbackGenerator,
    private val fileLogger: LocalFileLogger
) {
    
    private val frameResults = ConcurrentHashMap<Int, AnalysisResult>()
    private val framePoseResults = ConcurrentHashMap<Int, PoseLandmarkerResult>()
    private var poseLandmarker: PoseLandmarker? = null
    private var sessionId: String? = null
    private var videoUri: Uri? = null
    private val frameRetriever = MediaMetadataRetriever()
    
    private var totalFramesProcessed = 0
    private var strokeCount = 0
    private var goodStrokesCount = 0
    private var totalScore = 0.0
    
    companion object {
        private const val TAG = "VideoDebugProcessor"
        private const val DEFAULT_FPS = 30
        private const val FRAME_EXTRACTION_INTERVAL_MS = 33L // ~30 FPS
    }
    
    /**
     * Initialize video processing (no longer pre-processes all frames)
     */
    fun processVideo(videoUri: Uri, onComplete: (Map<Int, AnalysisResult>) -> Unit) {
        Thread {
            try {
                // Store video URI for on-demand processing
                this.videoUri = videoUri
                
                // Initialize MediaPipe PoseLandmarker
                initializePoseLandmarker()
                
                // Setup frame retriever
                frameRetriever.setDataSource(context, videoUri)
                
                // Start logging session
                sessionId = fileLogger.startTrainingSession(
                    exerciseId = "video_debug",
                    exerciseName = "Video Debug Analysis"
                )
                
                Log.i(TAG, "Video initialized for on-demand processing")
                onComplete(frameResults)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing video", e)
                fileLogger.logError(e, "processVideo")
                onComplete(emptyMap())
            }
        }.start()
    }
    
    /**
     * Process frame on-demand during playback (called from UI)
     */
    fun processFrameOnDemand(frameIndex: Int) {
        // Skip if already processed
        if (framePoseResults.containsKey(frameIndex)) {
            return
        }
        
        try {
            val timestampUs = frameIndex * FRAME_EXTRACTION_INTERVAL_MS * 1000
            val bitmap = frameRetriever.getFrameAtTime(
                timestampUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC  // Use SYNC for faster keyframe extraction
            )
            
            if (bitmap != null) {
                processFrame(bitmap, frameIndex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame on-demand $frameIndex", e)
        }
    }
    
    /**
     * Pre-fetch frames ahead for smoother playback
     */
    fun preFetchFrames(startFrame: Int, count: Int) {
        Thread {
            for (i in 0 until count) {
                val frameIndex = startFrame + i
                if (!framePoseResults.containsKey(frameIndex)) {
                    processFrameOnDemand(frameIndex)
                }
            }
        }.start()
    }
    
    /**
     * Process a single frame through MediaPipe and analysis pipeline
     */
    private fun processFrame(bitmap: Bitmap, frameIndex: Int) {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = frameIndex * FRAME_EXTRACTION_INTERVAL_MS
            
            // Run MediaPipe pose detection
            val poseLandmarkerResult = poseLandmarker?.detectForVideo(mpImage, timestampMs)
            
            if (poseLandmarkerResult != null && poseLandmarkerResult.landmarks().isNotEmpty()) {
                // Store pose result for visualization
                framePoseResults[frameIndex] = poseLandmarkerResult
                
                // Analyze stroke technique
                val analysisResult = motionAnalyzer.analyzeStroke(
                    poseLandmarkerResult = poseLandmarkerResult,
                    phase = StrokePhase.CONTACT // TODO: Implement phase detection
                )
                
                // Store analysis result
                frameResults[frameIndex] = analysisResult
                
                // Update statistics
                totalScore += analysisResult.overallScore
                if (analysisResult.isSuccessful()) {
                    goodStrokesCount++
                }
                
                // Log to file (async)
                sessionId?.let { sessionId ->
                    fileLogger.logStrokeAnalysis(
                        result = analysisResult,
                        sessionId = sessionId,
                        inferenceTimeMs = 0L, // Not measuring inference time in batch mode
                        frameNumber = frameIndex
                    )
                }
                
                if (frameIndex % 30 == 0) {
                    Log.d(TAG, "Frame $frameIndex: score=${analysisResult.overallScore}%")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame $frameIndex", e)
        }
    }
    
    /**
     * Initialize MediaPipe PoseLandmarker for video processing
     */
    private fun initializePoseLandmarker() {
        try {
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    com.google.mediapipe.tasks.core.BaseOptions.builder()
                        .setModelAssetPath("pose_landmarker_full.task")
                        .build()
                )
                .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.VIDEO)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.i(TAG, "PoseLandmarker initialized for VIDEO mode")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing PoseLandmarker", e)
            throw e
        }
    }
    
    /**
     * Get analysis result for a specific frame
     */
    fun getFrameResult(frameIndex: Int): AnalysisResult? {
        return frameResults[frameIndex]
    }
    
    /**
     * Get pose landmarks for a specific frame
     */
    fun getFramePoseResult(frameIndex: Int): PoseLandmarkerResult? {
        return framePoseResults[frameIndex]
    }
    
    /**
     * Get overall analysis summary
     */
    fun getAnalysisSummary(): AnalysisSummary {
        val phaseDistribution = mutableMapOf<StrokePhase, Int>()
        
        frameResults.values.forEach { result ->
            phaseDistribution[result.phase] = phaseDistribution.getOrDefault(result.phase, 0) + 1
        }
        
        return AnalysisSummary(
            totalFrames = totalFramesProcessed,
            strokeCount = strokeCount,
            goodStrokes = goodStrokesCount,
            averageScore = if (totalFramesProcessed > 0) totalScore / totalFramesProcessed else 0.0,
            successRate = if (strokeCount > 0) (goodStrokesCount * 100.0 / strokeCount) else 0.0,
            phaseDistribution = phaseDistribution
        )
    }
    
    /**
     * Reset processor state
     */
    fun reset() {
        frameResults.clear()
        framePoseResults.clear()
        totalFramesProcessed = 0
        strokeCount = 0
        goodStrokesCount = 0
        totalScore = 0.0
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        try {
            frameRetriever.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing frame retriever", e)
        }
        poseLandmarker?.close()
        poseLandmarker = null
        videoUri = null
        reset()
    }
    
    /**
     * Data class for analysis summary
     */
    data class AnalysisSummary(
        val totalFrames: Int,
        val strokeCount: Int,
        val goodStrokes: Int,
        val averageScore: Double,
        val successRate: Double,
        val phaseDistribution: Map<StrokePhase, Int>
    )
}
