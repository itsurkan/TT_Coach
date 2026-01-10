/*
 * AI Coach for Table Tennis
 * Video Debug Processor - Batch processes video through MediaPipe (like GalleryFragment)
 */

package com.google.mediapipe.examples.poselandmarker.processors

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.core.logging.providers.LocalFileLogger
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Processes video files using batch processing (same approach as GalleryFragment)
 * Processes entire video upfront, then syncs results with playback position
 */
class VideoDebugProcessor(
    private val context: Context,
    private val motionAnalyzer: MotionAnalyzer,
    private val feedbackGenerator: FeedbackGenerator,
    private val fileLogger: LocalFileLogger
) {
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var resultBundle: PoseLandmarkerHelper.ResultBundle? = null
    private var analysisResults: List<AnalysisResult> = emptyList()
    private var sessionId: String? = null
    private var backgroundExecutor: ScheduledExecutorService? = null
    private var displayTask: ScheduledFuture<*>? = null

    companion object {
        private const val TAG = "VideoDebugProcessor"
        const val VIDEO_INTERVAL_MS = 300L  // Interval for pose detection frames (100ms = 10 FPS)
        private const val DISPLAY_UPDATE_INTERVAL_MS = 33L  // ~30 FPS for smooth overlay sync
    }

    /**
     * Process entire video upfront using PoseLandmarkerHelper.detectVideoFile()
     * Same approach as GalleryMediaProcessor
     */
    fun processVideo(
        videoUri: Uri,
        onComplete: (PoseLandmarkerHelper.ResultBundle?) -> Unit
    ) {
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()

        backgroundExecutor?.execute {
            try {
                // Start logging session
                sessionId = fileLogger.startTrainingSession(
                    exerciseId = "video_debug",
                    exerciseName = "Video Debug Analysis"
                )

                // Create PoseLandmarkerHelper in VIDEO mode (same as GalleryMediaProcessor)
                poseLandmarkerHelper = PoseLandmarkerHelper(
                    context = context,
                    runningMode = RunningMode.VIDEO,
                    minPoseDetectionConfidence = PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE,
                    minPoseTrackingConfidence = PoseLandmarkerHelper.DEFAULT_POSE_TRACKING_CONFIDENCE,
                    minPosePresenceConfidence = PoseLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE,
                    currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU
                )

                // Batch process entire video (same as GalleryMediaProcessor)
                resultBundle = poseLandmarkerHelper?.detectVideoFile(videoUri, VIDEO_INTERVAL_MS)

                resultBundle?.let { bundle ->
                    Log.i(TAG, "Video processed: ${bundle.results.size} frames at ${VIDEO_INTERVAL_MS}ms intervals")
                    Log.i(TAG, "Video dimensions: ${bundle.inputImageWidth}x${bundle.inputImageHeight}")

                    // Analyze all frames for stroke technique
                    analysisResults = bundle.results.mapIndexed { index, poseResult ->
                        analyzeFrame(poseResult, index)
                    }

                    Log.i(TAG, "Analysis complete: ${analysisResults.size} frames analyzed")
                } ?: run {
                    Log.e(TAG, "Error: detectVideoFile returned null")
                }

                poseLandmarkerHelper?.clearPoseLandmarker()
                onComplete(resultBundle)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing video", e)
                fileLogger.logError(e, "processVideo")
                onComplete(null)
            }
        }
    }

    /**
     * Analyze a single frame for stroke technique
     */
    private fun analyzeFrame(poseResult: PoseLandmarkerResult, frameIndex: Int): AnalysisResult {
        return if (poseResult.landmarks().isNotEmpty()) {
            val result = motionAnalyzer.analyzeStroke(
                poseLandmarkerResult = poseResult,
                phase = StrokePhase.CONTACT
            )

            // Log analysis
            sessionId?.let { sid ->
                fileLogger.logStrokeAnalysis(
                    result = result,
                    sessionId = sid,
                    inferenceTimeMs = 0L,
                    frameNumber = frameIndex
                )
            }

            result
        } else {
            AnalysisResult() // Default empty result
        }
    }

    /**
     * Schedule result display synced with video playback
     * Same approach as GalleryMediaProcessor.scheduleVideoResultDisplay()
     */
    fun scheduleResultDisplay(
        getVideoPositionMs: () -> Int,
        isVideoPlaying: () -> Boolean,
        onFrameUpdate: (Int, PoseLandmarkerResult, AnalysisResult) -> Unit
    ) {
        val bundle = resultBundle ?: return

        displayTask?.cancel(false)
        displayTask = backgroundExecutor?.scheduleAtFixedRate(
            {
                val currentPositionMs = getVideoPositionMs()
                val resultIndex = (currentPositionMs / VIDEO_INTERVAL_MS).toInt()

                if (resultIndex >= bundle.results.size || !isVideoPlaying()) {
                    return@scheduleAtFixedRate
                }

                val poseResult = bundle.results[resultIndex]
                val analysisResult = analysisResults.getOrNull(resultIndex) ?: AnalysisResult()

                onFrameUpdate(resultIndex, poseResult, analysisResult)
            },
            0,
            VIDEO_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * Stop scheduled display
     */
    fun stopResultDisplay() {
        displayTask?.cancel(false)
        displayTask = null
    }

    /**
     * Get result at specific video position
     */
    fun getResultAtPosition(positionMs: Int): Pair<PoseLandmarkerResult?, AnalysisResult?> {
        val bundle = resultBundle ?: return Pair(null, null)
        val resultIndex = (positionMs / VIDEO_INTERVAL_MS).toInt()

        if (resultIndex >= bundle.results.size) {
            return Pair(null, null)
        }

        val poseResult = bundle.results[resultIndex]
        val analysisResult = analysisResults.getOrNull(resultIndex)

        return Pair(poseResult, analysisResult)
    }

    /**
     * Get video dimensions from result bundle
     */
    fun getVideoDimensions(): Pair<Int, Int> {
        val bundle = resultBundle ?: return Pair(0, 0)
        return Pair(bundle.inputImageWidth, bundle.inputImageHeight)
    }

    /**
     * Get total number of processed frames
     */
    fun getTotalFrames(): Int {
        return resultBundle?.results?.size ?: 0
    }

    /**
     * Get overall analysis summary
     */
    fun getAnalysisSummary(): AnalysisSummary {
        val phaseDistribution = mutableMapOf<StrokePhase, Int>()
        var totalScore = 0.0
        var goodStrokes = 0

        analysisResults.forEach { result ->
            phaseDistribution[result.phase] = phaseDistribution.getOrDefault(result.phase, 0) + 1
            totalScore += result.overallScore
            if (result.isSuccessful()) {
                goodStrokes++
            }
        }

        val totalFrames = analysisResults.size
        val averageScore = if (totalFrames > 0) totalScore / totalFrames else 0.0

        return AnalysisSummary(
            totalFrames = totalFrames,
            strokeCount = totalFrames,
            goodStrokes = goodStrokes,
            averageScore = averageScore,
            successRate = if (totalFrames > 0) (goodStrokes * 100.0 / totalFrames) else 0.0,
            phaseDistribution = phaseDistribution
        )
    }

    /**
     * Reset processor state
     */
    fun reset() {
        resultBundle = null
        analysisResults = emptyList()
        displayTask?.cancel(false)
        displayTask = null
    }

    /**
     * Clean up resources
     */
    fun close() {
        displayTask?.cancel(true)
        backgroundExecutor?.shutdown()
        poseLandmarkerHelper?.clearPoseLandmarker()
        poseLandmarkerHelper = null
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
