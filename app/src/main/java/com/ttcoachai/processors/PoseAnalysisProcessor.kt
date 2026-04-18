package com.ttcoachai.processors

import android.util.Log
import com.ttcoachai.PoseLandmarkerHelper
import com.ttcoachai.TTCoachApplication
import com.ttcoachai.mappers.MediaPipeMapper
import com.ttcoachai.managers.CalibrationStateManager
import com.ttcoachai.managers.TrainingStateManager
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.DetectedStroke
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.StrokePhase
import com.ttcoachai.services.FeedbackGenerator
import com.ttcoachai.services.MotionAnalyzer

class PoseAnalysisProcessor(
    private val application: TTCoachApplication,
    private val motionAnalyzer: MotionAnalyzer,
    private val feedbackGenerator: FeedbackGenerator,
    private val stateManager: TrainingStateManager,
    private val onUIUpdate: () -> Unit,
    private val calibrationStateManager: CalibrationStateManager? = null
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var frameCounter: Int = 0

    private val phaseDetector = StrokePhaseDetector(feedbackGenerator)
    private val analysisLogger = PoseAnalysisLogger(application)

    private val currentStrokeResults = mutableListOf<AnalysisResult>()
    private val currentStrokeLandmarks = mutableListOf<List<Landmark3D>>()
    private var pendingFeedbackResult: AnalysisResult? = null
    private var totalCompletedStrokes = 0

    // Phase-boundary tracking — used in calibration mode to reconstruct DetectedStroke.
    // The live path (StrokePhaseDetector) only emits a current phase enum per frame,
    // so we observe phase transitions and record frame indices ourselves.
    private var preparationStartFrame = -1
    private var preparationEndFrame = -1
    private var forwardStartFrame = -1
    private var contactFrame = -1
    private var forwardEndFrame = -1
    private var returnStartFrame = -1
    private var returnEndFrame = -1
    private var strokeStartTimestampMs = 0L
    private var strokeEndTimestampMs = 0L

    enum class Mode { TRAINING, CALIBRATION }

    @Volatile
    var mode: Mode = Mode.TRAINING

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
        if (!isAcceptingFrames()) return

        val poseResult = resultBundle.results.firstOrNull() ?: return
        if (poseResult.landmarks().isEmpty()) return

        frameCounter++
        val startTime = System.currentTimeMillis()
        val frameTimestampMs = poseResult.timestampMs()

        if (mode == Mode.CALIBRATION) {
            calibrationStateManager?.onFrameProcessed(frameTimestampMs)
        }

        try {
            val previousPhase = phaseDetector.getCurrentPhase()
            val detection = phaseDetector.detect(poseResult)
            val detectedPhase = detection.phase

            val analysis = motionAnalyzer.analyzeStroke(poseResult, detectedPhase)
            val inferenceTime = System.currentTimeMillis() - startTime
            latestAnalysisResult = analysis

            if (detection.isPhaseTransition) {
                recordPhaseBoundary(previousPhase, detectedPhase, frameTimestampMs)
            }

            if (detectedPhase != StrokePhase.READY) {
                currentStrokeResults.add(analysis)
                currentStrokeLandmarks.add(MediaPipeMapper.toLandmarkList(poseResult))
            }

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

    private fun isAcceptingFrames(): Boolean = when (mode) {
        Mode.TRAINING -> stateManager.isTrainingActive
        Mode.CALIBRATION -> calibrationStateManager?.isCapturing == true
    }

    private fun recordPhaseBoundary(previous: StrokePhase, current: StrokePhase, timestampMs: Long) {
        when (current) {
            StrokePhase.BACKSWING -> if (preparationStartFrame < 0) {
                preparationStartFrame = frameCounter
                strokeStartTimestampMs = timestampMs
            }
            StrokePhase.FORWARD_SWING -> {
                if (preparationEndFrame < 0) preparationEndFrame = frameCounter
                if (forwardStartFrame < 0) forwardStartFrame = frameCounter
            }
            StrokePhase.CONTACT -> if (contactFrame < 0) contactFrame = frameCounter
            StrokePhase.FOLLOW_THROUGH -> {
                if (forwardEndFrame < 0) forwardEndFrame = frameCounter
                if (returnStartFrame < 0) returnStartFrame = frameCounter
            }
            else -> {
                if (previous == StrokePhase.FOLLOW_THROUGH && returnEndFrame < 0) {
                    returnEndFrame = frameCounter
                    strokeEndTimestampMs = timestampMs
                }
            }
        }
    }

    private fun handleDelayedFeedback(previousPhase: StrokePhase, currentPhase: StrokePhase) {
        if (mode == Mode.CALIBRATION) return
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
        if (previousPhase != StrokePhase.FOLLOW_THROUGH || currentPhase == StrokePhase.FOLLOW_THROUGH) return
        if (currentStrokeResults.isEmpty()) return

        totalCompletedStrokes++
        val bestResult = currentStrokeResults
            .filter { it.phase == StrokePhase.CONTACT }
            .maxByOrNull { it.overallScore }
            ?: currentStrokeResults.maxByOrNull { it.overallScore }
            ?: currentAnalysis

        when (mode) {
            Mode.TRAINING -> routeToTraining(bestResult)
            Mode.CALIBRATION -> routeToCalibration(bestResult)
        }

        mainHandler.post { onUIUpdate() }
        resetCurrentStrokeBuffers()
    }

    private fun routeToTraining(bestResult: AnalysisResult) {
        stateManager.addAnalysisResult(bestResult)
        pendingFeedbackResult = bestResult

        val feedbackText = if (application.settingsManager.getFeedbackType() == 0) {
            feedbackGenerator.generateShortFeedback(bestResult)
        } else {
            feedbackGenerator.generateDetailedFeedback(bestResult)
        }
        stateManager.addFeedback(feedbackText)

        val feedbackWithLandmarks = bestResult.feedbackItems.map { item ->
            item.copy(strokeLandmarks = currentStrokeLandmarks.toList())
        }
        stateManager.addFeedbackItems(feedbackWithLandmarks)
    }

    private fun routeToCalibration(bestResult: AnalysisResult) {
        val manager = calibrationStateManager ?: return
        val stroke = buildDetectedStrokeFromBoundaries(totalCompletedStrokes - 1, bestResult)
        manager.recordStroke(stroke, bestResult)
    }

    private fun buildDetectedStrokeFromBoundaries(
        strokeIndex: Int,
        bestResult: AnalysisResult
    ): DetectedStroke {
        val prepStart = preparationStartFrame.coerceAtLeast(0)
        val prepEnd = preparationEndFrame.coerceAtLeast(prepStart)
        val fwdStart = forwardStartFrame.coerceAtLeast(prepEnd)
        val contact = if (contactFrame >= 0) contactFrame else fwdStart
        val fwdEnd = forwardEndFrame.coerceAtLeast(contact)
        val retStart = returnStartFrame.coerceAtLeast(fwdEnd)
        val retEnd = if (returnEndFrame >= 0) returnEndFrame else frameCounter
        val durationMs = (strokeEndTimestampMs - strokeStartTimestampMs).coerceAtLeast(0)
        val forwardMs = 0L // swing-velocity metrics aren't tracked in the live path; not required by BaselineDeriver

        return DetectedStroke(
            strokeIndex = strokeIndex,
            preparationStartFrame = prepStart,
            preparationEndFrame = prepEnd,
            forwardStartFrame = fwdStart,
            contactFrame = contact,
            forwardEndFrame = fwdEnd,
            returnStartFrame = retStart,
            returnEndFrame = retEnd,
            backswingMinValue = 0f,
            forwardPeakValue = 0f,
            peakVelocity = 0f,
            strokeDurationMs = durationMs,
            forwardSwingDurationMs = forwardMs,
            isComplete = true
        )
    }

    private fun resetCurrentStrokeBuffers() {
        currentStrokeResults.clear()
        currentStrokeLandmarks.clear()
        preparationStartFrame = -1
        preparationEndFrame = -1
        forwardStartFrame = -1
        contactFrame = -1
        forwardEndFrame = -1
        returnStartFrame = -1
        returnEndFrame = -1
        strokeStartTimestampMs = 0L
        strokeEndTimestampMs = 0L
    }

    private fun resetStrokeDetection() {
        phaseDetector.reset()
        resetCurrentStrokeBuffers()
        pendingFeedbackResult = null
        latestAnalysisResult = null
    }

    fun getFrameCount(): Int = frameCounter
    fun getCurrentPhase(): StrokePhase = phaseDetector.getCurrentPhase()
}
