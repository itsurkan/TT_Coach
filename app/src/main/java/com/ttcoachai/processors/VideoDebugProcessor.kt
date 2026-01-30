package com.ttcoachai.processors

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ttcoachai.PoseLandmarkerHelper
import com.ttcoachai.core.logging.providers.LocalFileLogger
import com.ttcoachai.models.AnalysisResult
import com.ttcoachai.models.StrokePhase
import com.ttcoachai.services.*
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class PoseFrame(val landmarks: List<NormalizedLandmark>, val timestampMs: Long)

class VideoDebugProcessor(
    private val context: Context,
    private val motionAnalyzer: MotionAnalyzer,
    private val feedbackGenerator: FeedbackGenerator
) {
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var resultBundle: PoseLandmarkerHelper.ResultBundle? = null
    private var poseFrames: List<PoseFrame>? = null
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var analysisResults: MutableList<AnalysisResult> = mutableListOf()
    private val analyzedFrames = mutableSetOf<Int>()
    private var backgroundExecutor: ScheduledExecutorService? = null
    private var displayTask: ScheduledFuture<*>? = null
    private var strokeDetectionResult: StrokeDetectionResult? = null
    private val dataMapper = PoseDataMapper()
    private val feedbackManager by lazy { VideoFeedbackManager(feedbackGenerator) }

    companion object {
        private const val TAG = "VideoDebugProcessor"
        const val VIDEO_INTERVAL_MS = 100L
        private const val DISPLAY_UPDATE_INTERVAL_MS = 100L
    }

    fun isFromJson(): Boolean = poseFrames != null
    fun getTotalFrames(): Int = poseFrames?.size ?: resultBundle?.results?.size ?: 0
    fun getAllPoseResults(): List<PoseLandmarkerResult> = resultBundle?.results ?: emptyList()

    fun processVideo(videoUri: Uri, onComplete: (PoseLandmarkerHelper.ResultBundle?) -> Unit) {
        backgroundExecutor?.shutdownNow()
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor?.execute {
            try {
                poseLandmarkerHelper = PoseLandmarkerHelper(
                    context = context,
                    runningMode = RunningMode.VIDEO,
                    currentDelegate = PoseLandmarkerHelper.DELEGATE_GPU,
                    currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE
                )
                resultBundle = poseLandmarkerHelper?.detectVideoFile(videoUri, VIDEO_INTERVAL_MS)
                resultBundle?.let { analysisResults = MutableList(it.results.size) { AnalysisResult() } }
                poseLandmarkerHelper?.clearPoseLandmarker()
                onComplete(resultBundle)
            } catch (e: Exception) {
                onComplete(null)
            }
        }
    }

    fun processVideoFromJson(json: String, w: Int, h: Int, onComplete: (Boolean) -> Unit) {
        backgroundExecutor?.shutdownNow()
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor?.execute {
            try {
                poseFrames = dataMapper.parseJson(json)
                videoWidth = w
                videoHeight = h
                analysisResults = MutableList(poseFrames!!.size) { AnalysisResult() }
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun scheduleResultDisplay(getVideoPositionMs: () -> Int, isVideoPlaying: () -> Boolean, onFrameUpdate: (Int, List<NormalizedLandmark>?, AnalysisResult) -> Unit) {
        val total = getTotalFrames()
        if (total == 0) return
        displayTask?.cancel(false)
        displayTask = backgroundExecutor?.scheduleWithFixedDelay({
            if (!isVideoPlaying()) return@scheduleWithFixedDelay
            val idx = (getVideoPositionMs() / VIDEO_INTERVAL_MS.toFloat() + 0.5f).toInt().coerceIn(0, total - 1)
            val lms = getLandmarksAtIndex(idx)
            val res = analysisResults.getOrNull(idx) ?: AnalysisResult()
            feedbackManager.processFrame(idx, res)
            onFrameUpdate(idx, lms, res)
        }, 0, DISPLAY_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    fun stopResultDisplay() { displayTask?.cancel(false); displayTask = null }

    private fun getLandmarksAtIndex(idx: Int): List<NormalizedLandmark>? {
        return poseFrames?.getOrNull(idx)?.landmarks ?: resultBundle?.results?.getOrNull(idx)?.landmarks()?.getOrNull(0)
    }

    fun getResultAtPosition(posMs: Int): Pair<List<NormalizedLandmark>?, AnalysisResult?> {
        val total = getTotalFrames()
        if (total == 0) return Pair(null, null)
        val idx = (posMs / VIDEO_INTERVAL_MS.toFloat() + 0.5f).toInt().coerceIn(0, total - 1)
        val lms = getLandmarksAtIndex(idx)
        if (idx !in analyzedFrames && idx < analysisResults.size && lms != null) {
            analysisResults[idx] = motionAnalyzer.analyzeStroke(lms, strokeDetectionResult?.getPhaseForFrame(idx) ?: StrokePhase.CONTACT)
            analyzedFrames.add(idx)
        }
        return Pair(lms, analysisResults.getOrNull(idx))
    }

    fun getVideoDimensions(): Pair<Int, Int> = poseFrames?.let { videoWidth to videoHeight } ?: (resultBundle?.let { it.inputImageWidth to it.inputImageHeight } ?: (0 to 0))

    fun getAnalysisSummary(): AnalysisSummary {
        val total = getTotalFrames()
        (0 until total).forEach { getResultAtPosition(it * VIDEO_INTERVAL_MS.toInt()) }
        val phases = mutableMapOf<StrokePhase, Int>()
        var score = 0.0
        var good = 0
        analysisResults.forEach { 
            phases[it.phase] = phases.getOrDefault(it.phase, 0) + 1
            score += it.overallScore
            if (it.isSuccessful()) good++
        }
        return AnalysisSummary(total, total, good, if (total > 0) score / total else 0.0, if (total > 0) good * 100.0 / total else 0.0, phases)
    }

    fun hasStrokeDetection(): Boolean = strokeDetectionResult != null
    fun getStrokePhaseForFrame(idx: Int) = strokeDetectionResult?.getPhaseForFrame(idx) ?: StrokePhase.READY
    fun getStrokeForFrame(idx: Int) = strokeDetectionResult?.getStrokeForFrame(idx)
    fun getDetectedStrokes() = strokeDetectionResult?.strokes ?: emptyList()
    fun getStrokeDetectionResult() = strokeDetectionResult

    fun runStrokeDetection(config: StrokeDetectorConfig = StrokeDetectorConfig.FOREHAND): StrokeDetectionResult? {
        val frames = dataMapper.toStrokeFrames(poseFrames, resultBundle?.results)
        if (frames.isEmpty()) return null
        strokeDetectionResult = JsonStrokeDetector(config).detectStrokes(frames, VIDEO_INTERVAL_MS)
        return strokeDetectionResult
    }

    fun reset() {
        resultBundle = null
        poseFrames = null
        videoWidth = 0
        videoHeight = 0
        analysisResults.clear()
        analyzedFrames.clear()
        stopResultDisplay()
        strokeDetectionResult = null
        feedbackManager.reset()
    }

    fun close() {
        backgroundExecutor?.shutdown()
        poseLandmarkerHelper?.clearPoseLandmarker()
        reset()
    }

    data class AnalysisSummary(val totalFrames: Int, val strokeCount: Int, val goodStrokes: Int, val averageScore: Double, val successRate: Double, val phaseDistribution: Map<StrokePhase, Int>)
}
