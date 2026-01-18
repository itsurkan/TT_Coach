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
import com.google.mediapipe.examples.poselandmarker.services.JsonStrokeDetector
import com.google.mediapipe.examples.poselandmarker.services.StrokeDetectorConfig
import com.google.mediapipe.examples.poselandmarker.services.StrokeDetectionResult
import com.google.mediapipe.examples.poselandmarker.services.DetectedStroke
import com.google.mediapipe.examples.poselandmarker.services.JsonPoseFrame
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.Optional
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Processes video files using batch processing (same approach as GalleryFragment)
 * Processes entire video upfront, then syncs results with playback position
 */
/**
 * Holds a single frame's pose data (raw landmarks + timestamp)
 */
data class PoseFrame(
    val landmarks: List<NormalizedLandmark>,
    val timestampMs: Long
)

class VideoDebugProcessor(
    private val context: Context,
    private val motionAnalyzer: MotionAnalyzer,
    private val feedbackGenerator: FeedbackGenerator,
    private val fileLogger: LocalFileLogger
) {
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var resultBundle: PoseLandmarkerHelper.ResultBundle? = null
    private var poseFrames: List<PoseFrame>? = null  // Raw landmarks from JSON
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var analysisResults: MutableList<AnalysisResult> = mutableListOf()
    private val analyzedFrames = mutableSetOf<Int>()
    private var sessionId: String? = null
    private var backgroundExecutor: ScheduledExecutorService? = null
    private var displayTask: ScheduledFuture<*>? = null

    // Stroke detection
    private var strokeDetector: JsonStrokeDetector? = null
    private var strokeDetectionResult: StrokeDetectionResult? = null

    // Playback state for audio
    private var lastPlayedFrameIndex = -1
    private var lastContactFrameIndex = -1 // Deprecated but keeping for reference if needed
    private var audioPlayedForCurrentStroke = false
    private var lastPhase: StrokePhase? = null
    private var pendingFeedbackResult: AnalysisResult? = null
    private val currentStrokeIndices = mutableListOf<Int>()

    companion object {
        private const val TAG = "VideoDebugProcessor"
        const val VIDEO_INTERVAL_MS = 100L
        private const val DISPLAY_UPDATE_INTERVAL_MS = 33L
    }

    /** Check if data was loaded from JSON (raw landmarks) vs MediaPipe processing */
    fun isFromJson(): Boolean = poseFrames != null

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
                // Skip logging session for faster loading
                // sessionId = fileLogger.startTrainingSession(
                //     exerciseId = "video_debug",
                //     exerciseName = "Video Debug Analysis"
                // )

                // Create PoseLandmarkerHelper in VIDEO mode with optimizations for speed
                // Use LITE model + GPU for ~3x faster processing (matches 100ms interval speed to old 300ms)
                poseLandmarkerHelper = PoseLandmarkerHelper(
                    context = context,
                    runningMode = RunningMode.VIDEO,
                    minPoseDetectionConfidence = PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE,
                    minPoseTrackingConfidence = PoseLandmarkerHelper.DEFAULT_POSE_TRACKING_CONFIDENCE,
                    minPosePresenceConfidence = PoseLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE,
                    currentDelegate = PoseLandmarkerHelper.DELEGATE_GPU,  // GPU is faster than CPU
                    currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE  // LITE is 2-3x faster than FULL
                )

                // Batch process entire video (same as GalleryMediaProcessor)
                resultBundle = poseLandmarkerHelper?.detectVideoFile(videoUri, VIDEO_INTERVAL_MS)

                resultBundle?.let { bundle ->
                    Log.i(TAG, "Video processed: ${bundle.results.size} frames at ${VIDEO_INTERVAL_MS}ms intervals")
                    Log.i(TAG, "Video dimensions: ${bundle.inputImageWidth}x${bundle.inputImageHeight}")

                    // Don't analyze all frames upfront - too slow!
                    // Initialize empty list, analyze on-demand when frames are accessed
                    analysisResults = List(bundle.results.size) { AnalysisResult() }.toMutableList()

                    Log.i(TAG, "Video ready: analysis will be done on-demand for better performance")
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
     * Process video from pre-computed JSON poses
     * Stores raw landmarks directly without creating PoseLandmarkerResult objects
     */
    fun processVideoFromJson(
        jsonString: String,
        width: Int,
        height: Int,
        onComplete: (Boolean) -> Unit
    ) {
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()

        backgroundExecutor?.execute {
            try {
                Log.i(TAG, "Loading poses from JSON...")
                val jsonRoot = org.json.JSONObject(jsonString)
                val framesArray = jsonRoot.getJSONArray("frames")

                val frames = mutableListOf<PoseFrame>()

                for (i in 0 until framesArray.length()) {
                    val frameObj = framesArray.getJSONObject(i)
                    val timestampMs = frameObj.getLong("timestampMs")
                    val landmarksArray = frameObj.getJSONArray("landmarks")

                    val landmarks = mutableListOf<NormalizedLandmark>()
                    for (j in 0 until landmarksArray.length()) {
                        val lmObj = landmarksArray.getJSONObject(j)
                        landmarks.add(
                            NormalizedLandmark.create(
                                lmObj.getDouble("x").toFloat(),
                                lmObj.getDouble("y").toFloat(),
                                lmObj.getDouble("z").toFloat(),
                                Optional.of(lmObj.optDouble("visibility", 0.0).toFloat()),
                                Optional.of(lmObj.optDouble("presence", 0.0).toFloat())
                            )
                        )
                    }
                    frames.add(PoseFrame(landmarks, timestampMs))
                }

                poseFrames = frames
                videoWidth = width
                videoHeight = height
                resultBundle = null
                analysisResults = MutableList(frames.size) { AnalysisResult() }

                Log.i(TAG, "Loaded ${frames.size} frames from JSON")
                onComplete(true)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing JSON", e)
                fileLogger.logError(e, "processVideoFromJson")
                onComplete(false)
            }
        }
    }

    /**
     * Analyze a single frame for stroke technique (from PoseLandmarkerResult)
     */
    private fun analyzeFrame(index: Int, poseResult: PoseLandmarkerResult): AnalysisResult {
        val landmarks = if (poseResult.landmarks().isNotEmpty()) poseResult.landmarks()[0] else emptyList()
        val phase = strokeDetectionResult?.getPhaseForFrame(index) ?: StrokePhase.CONTACT
        val result = motionAnalyzer.analyzeStroke(landmarks, phase)
        analysisResults[index] = result
        analyzedFrames.add(index)
        return result
    }

    /**
     * Analyze a single frame for stroke technique (from raw landmarks)
     */
    private fun analyzeFrame(index: Int, landmarks: List<NormalizedLandmark>): AnalysisResult {
        if (analysisResults.getOrNull(index)?.overallScore ?: 0f > 0f && analyzedFrames.contains(index)) {
            return analysisResults[index]
        }
        
        // Use pre-processed phase if available, otherwise default to CONTACT
        val phase = strokeDetectionResult?.getPhaseForFrame(index) ?: StrokePhase.CONTACT
        val result = motionAnalyzer.analyzeStroke(landmarks, phase)
        analysisResults[index] = result
        analyzedFrames.add(index)
        return result
    }

    /**
     * Schedule result display synced with video playback
     * Supports both MediaPipe results and raw landmarks from JSON
     */
    fun scheduleResultDisplay(
        getVideoPositionMs: () -> Int,
        isVideoPlaying: () -> Boolean,
        onFrameUpdate: (Int, List<NormalizedLandmark>?, AnalysisResult) -> Unit
    ) {
        val totalFrames = getTotalFrames()
        if (totalFrames == 0) return

        displayTask?.cancel(false)
        displayTask = backgroundExecutor?.scheduleAtFixedRate(
            {
                if (!isVideoPlaying()) return@scheduleAtFixedRate

                val currentPositionMs = getVideoPositionMs()
                val resultIndex = ((currentPositionMs.toFloat() / VIDEO_INTERVAL_MS) + 0.5f).toInt()
                    .coerceIn(0, totalFrames - 1)

                val landmarks = getLandmarksAtIndex(resultIndex)
                val analysisResult = analysisResults.getOrNull(resultIndex) ?: AnalysisResult()

                // Audio Feedback Trigger logic for Playback
                if (resultIndex != lastPlayedFrameIndex) {
                    val currentPhase = analysisResult.phase
                    
                    // 1. Collect indices for the current stroke
                    if (currentPhase != StrokePhase.READY) {
                        currentStrokeIndices.add(resultIndex)
                    }
                    
                    // 2. Trigger DELAYED feedback from previous stroke at start of FORWARD_SWING
                    if (lastPhase == StrokePhase.BACKSWING && currentPhase == StrokePhase.FORWARD_SWING) {
                        pendingFeedbackResult?.let { result ->
                            android.util.Log.i(TAG, "Debug Playback: Playing DELAYED Audio Feedback")
                            
                            // Pick ONLY ONE recommendation/error
                            val singleRecResult = result.copy(
                                recommendations = if (result.recommendations.isNotEmpty()) 
                                    listOf(result.recommendations[0]) else emptyList(),
                                feedbackItems = if (result.feedbackItems.isNotEmpty()) 
                                    listOf(result.feedbackItems[0]) else emptyList(),
                                errors = if (result.errors.isNotEmpty()) 
                                    listOf(result.errors[0]) else emptyList()
                            )
                            
                            feedbackGenerator.playFeedbackAudio(singleRecResult)
                            pendingFeedbackResult = null // Clear after playing
                            audioPlayedForCurrentStroke = true
                        }
                    }
                    
                    // 3. Finalize stroke data when it ends
                    if (lastPhase == StrokePhase.FOLLOW_THROUGH && currentPhase != StrokePhase.FOLLOW_THROUGH) {
                        if (currentStrokeIndices.isNotEmpty()) {
                            // Find the best result for the completed stroke
                            pendingFeedbackResult = currentStrokeIndices
                                .mapNotNull { analysisResults.getOrNull(it) }
                                .filter { it.phase == StrokePhase.CONTACT }
                                .maxByOrNull { it.overallScore }
                                ?: currentStrokeIndices
                                    .mapNotNull { analysisResults.getOrNull(it) }
                                    .maxByOrNull { it.overallScore }
                            
                            currentStrokeIndices.clear()
                            android.util.Log.d(TAG, "Debug Playback: Stroke finalized, feedback pending for next stroke.")
                        }
                    }
                    
                    // Reset tracking for current stroke audio
                    if (currentPhase == StrokePhase.BACKSWING && lastPhase != StrokePhase.BACKSWING) {
                        audioPlayedForCurrentStroke = false
                    }
                    
                    // 4. Rhythm sounds (Tic/Tac)
                    if (currentPhase == StrokePhase.FORWARD_SWING && lastPhase != StrokePhase.FORWARD_SWING) {
                        feedbackGenerator.playTic()
                    }
                    if (currentPhase == StrokePhase.CONTACT && lastPhase != StrokePhase.CONTACT) {
                        feedbackGenerator.playTac()
                    }

                    lastPhase = currentPhase
                    lastPlayedFrameIndex = resultIndex
                }

                onFrameUpdate(resultIndex, landmarks, analysisResult)
            },
            0,
            DISPLAY_UPDATE_INTERVAL_MS,
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
     * Get landmarks at a specific frame index
     */
    private fun getLandmarksAtIndex(index: Int): List<NormalizedLandmark>? {
        poseFrames?.let { frames ->
            return frames.getOrNull(index)?.landmarks
        }
        resultBundle?.let { bundle ->
            val result = bundle.results.getOrNull(index)
            return if (result != null && result.landmarks().isNotEmpty()) {
                result.landmarks()[0]
            } else null
        }
        return null
    }

    /**
     * Get result at specific video position
     * Returns raw landmarks and analysis result
     */
    fun getResultAtPosition(positionMs: Int): Pair<List<NormalizedLandmark>?, AnalysisResult?> {
        val totalFrames = getTotalFrames()
        if (totalFrames == 0) return Pair(null, null)

        val resultIndex = ((positionMs.toFloat() / VIDEO_INTERVAL_MS) + 0.5f).toInt()
            .coerceIn(0, totalFrames - 1)

        val landmarks = getLandmarksAtIndex(resultIndex)

        // Analyze on-demand if not yet done
        if (resultIndex !in analyzedFrames && resultIndex < analysisResults.size && landmarks != null) {
            analysisResults[resultIndex] = analyzeFrame(landmarks)
            analyzedFrames.add(resultIndex)
        }

        return Pair(landmarks, analysisResults.getOrNull(resultIndex))
    }

    /**
     * Get video dimensions
     */
    fun getVideoDimensions(): Pair<Int, Int> {
        if (poseFrames != null) {
            return Pair(videoWidth, videoHeight)
        }
        resultBundle?.let { bundle ->
            return Pair(bundle.inputImageWidth, bundle.inputImageHeight)
        }
        return Pair(0, 0)
    }

    /**
     * Get total number of processed frames
     */
    fun getTotalFrames(): Int {
        poseFrames?.let { return it.size }
        return resultBundle?.results?.size ?: 0
    }

    /**
     * Get overall analysis summary
     * Analyzes all frames if not yet done
     */
    fun getAnalysisSummary(): AnalysisSummary {
        val totalFrames = getTotalFrames()
        for (i in 0 until totalFrames) {
            val result = if (isFromJson()) {
                val landmarks = getLandmarksAtIndex(i)
                if (landmarks != null) analyzeFrame(i, landmarks) else AnalysisResult()
            } else {
                val poseResult = resultBundle?.results?.getOrNull(i)
                if (poseResult != null) analyzeFrame(i, poseResult) else AnalysisResult()
            }
            
            // The analyzeFrame calls already add to analysisResults and analyzedFrames
            // No need to re-add here.
        }
        
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
     * Get all pose results for export (only available for MediaPipe-processed videos)
     */
    fun getAllPoseResults(): List<PoseLandmarkerResult> {
        return resultBundle?.results ?: emptyList()
    }

    /**
     * Get all raw landmarks for export
     */
    fun getAllLandmarks(): List<List<NormalizedLandmark>> {
        poseFrames?.let { frames ->
            return frames.map { it.landmarks }
        }
        resultBundle?.let { bundle ->
            return bundle.results.mapNotNull { result ->
                if (result.landmarks().isNotEmpty()) result.landmarks()[0] else null
            }
        }
        return emptyList()
    }

    // ==================== STROKE DETECTION ====================

    /**
     * Run stroke detection on loaded pose data
     * Call this after processVideoFromJson() or processVideo() completes
     */
    fun runStrokeDetection(config: StrokeDetectorConfig = StrokeDetectorConfig.FOREHAND): StrokeDetectionResult? {
        val frames = convertToJsonPoseFrames()
        if (frames.isEmpty()) {
            Log.w(TAG, "No frames available for stroke detection")
            return null
        }

        strokeDetector = JsonStrokeDetector(config)
        strokeDetectionResult = strokeDetector?.detectStrokes(frames, VIDEO_INTERVAL_MS)

        strokeDetectionResult?.let { result ->
            Log.i(TAG, "Stroke detection complete: ${result.strokes.size} strokes detected")
            result.strokes.forEachIndexed { index, stroke ->
                Log.d(TAG, "  Stroke ${index + 1}: frames ${stroke.preparationStartFrame}-${stroke.returnEndFrame}, " +
                        "duration=${stroke.strokeDurationMs}ms")
            }
        }

        return strokeDetectionResult
    }

    /**
     * Convert loaded pose data to JsonPoseFrame format for stroke detection
     */
    private fun convertToJsonPoseFrames(): List<JsonPoseFrame> {
        poseFrames?.let { frames ->
            return frames.mapIndexed { index, poseFrame ->
                JsonStrokeDetector.fromNormalizedLandmarks(
                    landmarks = poseFrame.landmarks,
                    frameIndex = index,
                    timestampMs = poseFrame.timestampMs
                )
            }
        }

        resultBundle?.let { bundle ->
            return bundle.results.mapIndexedNotNull { index, result ->
                if (result.landmarks().isNotEmpty()) {
                    JsonStrokeDetector.fromNormalizedLandmarks(
                        landmarks = result.landmarks()[0],
                        frameIndex = index,
                        timestampMs = index * VIDEO_INTERVAL_MS
                    )
                } else null
            }
        }

        return emptyList()
    }

    /**
     * Get detected strokes
     */
    fun getDetectedStrokes(): List<DetectedStroke> {
        return strokeDetectionResult?.strokes ?: emptyList()
    }

    /**
     * Get stroke phase for a specific frame index
     */
    fun getStrokePhaseForFrame(frameIndex: Int): StrokePhase {
        return strokeDetectionResult?.getPhaseForFrame(frameIndex) ?: StrokePhase.READY
    }

    /**
     * Get stroke that contains a specific frame
     */
    fun getStrokeForFrame(frameIndex: Int): DetectedStroke? {
        return strokeDetectionResult?.getStrokeForFrame(frameIndex)
    }

    /**
     * Get stroke detection result
     */
    fun getStrokeDetectionResult(): StrokeDetectionResult? {
        return strokeDetectionResult
    }

    /**
     * Check if stroke detection has been run
     */
    fun hasStrokeDetection(): Boolean {
        return strokeDetectionResult != null
    }

    // ==================== END STROKE DETECTION ====================

    /**
     * Reset processor state
     */
    fun reset() {
        resultBundle = null
        poseFrames = null
        videoWidth = 0
        videoHeight = 0
        analysisResults = mutableListOf()
        analyzedFrames.clear()
        displayTask?.cancel(false)
        displayTask = null
        // Reset stroke detection
        strokeDetector = null
        strokeDetectionResult = null
        lastPlayedFrameIndex = -1
        lastContactFrameIndex = -1
        audioPlayedForCurrentStroke = false
        lastPhase = null
        pendingFeedbackResult = null
        currentStrokeIndices.clear()
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
