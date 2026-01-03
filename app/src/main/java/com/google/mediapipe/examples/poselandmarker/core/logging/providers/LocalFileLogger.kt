package com.google.mediapipe.examples.poselandmarker.core.logging.providers

import android.content.Context
import android.util.Log
import com.google.mediapipe.examples.poselandmarker.core.logging.*
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import java.util.UUID

/**
 * Adapter that implements Logger, AnalyticsProvider, and CrashReporter interfaces
 * using AsyncFileLogger as the backend.
 * 
 * This provides compatibility with existing logging interfaces while using
 * zero-latency async file logging under the hood.
 */
class LocalFileLogger(context: Context) : Logger, AnalyticsProvider, CrashReporter {
    private val asyncLogger = AsyncFileLogger(context)
    private var currentSessionId: String? = null
    
    // === Logger Interface ===
    
    override fun logEvent(eventName: String, params: Map<String, Any>) {
        asyncLogger.logEvent(eventName, params)
    }
    
    override fun logError(throwable: Throwable, context: String?) {
        asyncLogger.logError(ErrorData(
            timestamp = System.currentTimeMillis(),
            errorType = throwable.javaClass.simpleName,
            errorMessage = throwable.message ?: "Unknown error",
            stackTrace = throwable.stackTraceToString(),
            context = context
        ))
    }
    
    override fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
        asyncLogger.logEvent("log_info", mapOf("tag" to tag, "message" to message))
    }
    
    override fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }
    
    // === AnalyticsProvider Interface ===
    
    override fun trackEvent(eventName: String, properties: Map<String, Any>) {
        asyncLogger.logEvent(eventName, properties)
    }
    
    override fun trackScreen(screenName: String) {
        asyncLogger.logEvent("screen_view", mapOf("screen_name" to screenName))
    }
    
    override fun setUserId(userId: String) {
        asyncLogger.logEvent("set_user_id", mapOf("user_id" to userId))
    }
    
    override fun setUserProperty(key: String, value: String) {
        asyncLogger.logEvent("set_user_property", mapOf("key" to key, "value" to value))
    }
    
    // === CrashReporter Interface ===
    
    override fun logException(throwable: Throwable) {
        logError(throwable, null)
    }
    
    override fun logMessage(message: String, level: LogLevel) {
        asyncLogger.logEvent("log_message", mapOf("message" to message, "level" to level.name))
    }
    
    override fun setCustomKey(key: String, value: String) {
        asyncLogger.logEvent("custom_key", mapOf("key" to key, "value" to value))
    }
    
    // === Training-specific methods ===
    
    fun startTrainingSession(exerciseId: String, exerciseName: String): String {
        val sessionId = UUID.randomUUID().toString()
        currentSessionId = sessionId
        
        asyncLogger.logTrainingSession(TrainingSessionData(
            sessionId = sessionId,
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            startTime = System.currentTimeMillis()
        ))
        
        Log.i(TAG, "Training session started: $sessionId")
        return sessionId
    }
    
    fun endTrainingSession(totalStrokes: Int, goodStrokes: Int, averageScore: Float) {
        currentSessionId?.let { sessionId ->
            asyncLogger.logTrainingSession(TrainingSessionData(
                sessionId = sessionId,
                exerciseId = "",
                exerciseName = "",
                startTime = 0,
                endTime = System.currentTimeMillis(),
                totalStrokes = totalStrokes,
                goodStrokes = goodStrokes,
                averageScore = averageScore
            ))
            
            Log.i(TAG, "Training session ended: $sessionId (strokes: $totalStrokes, score: $averageScore)")
        }
        currentSessionId = null
    }
    
    fun logStrokeAnalysis(
        result: AnalysisResult,
        sessionId: String,
        inferenceTimeMs: Long,
        frameNumber: Int
    ): String {
        val strokeId = UUID.randomUUID().toString()
        
        asyncLogger.logStrokeAnalysis(StrokeAnalysisData(
            sessionId = sessionId,
            strokeId = strokeId,
            timestamp = System.currentTimeMillis(),
            wristAngle = result.wristAngle,
            bodyRotation = result.bodyRotation,
            followThroughAngle = result.followThroughAngle,
            contactHeight = result.contactHeight,
            elbowBodyDistance = result.elbowBodyDistance,
            overallScore = result.overallScore,
            isSuccessful = result.isSuccessful(),
            phase = result.phase.name,
            errors = result.errors,
            inferenceTimeMs = inferenceTimeMs,
            frameNumber = frameNumber
        ))
        
        return strokeId
    }
    
    fun logRuleEvaluation(evaluation: RuleEvaluationData) {
        asyncLogger.logRuleEvaluation(evaluation)
    }
    
    fun logPerformanceMetric(metricName: String, value: Float, sessionId: String? = null) {
        asyncLogger.logPerformanceMetric(PerformanceMetricData(
            timestamp = System.currentTimeMillis(),
            metricName = metricName,
            value = value,
            sessionId = sessionId ?: currentSessionId
        ))
    }
    
    fun logRawPose(
        sessionId: String,
        frameNumber: Int,
        inferenceTimeMs: Long,
        landmarks: List<LandmarkData>,
        worldLandmarks: List<LandmarkData>? = null
    ) {
        asyncLogger.logRawPose(RawPoseData(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            frameNumber = frameNumber,
            inferenceTimeMs = inferenceTimeMs,
            landmarks = landmarks,
            worldLandmarks = worldLandmarks
        ))
    }
    
    // === Management ===
    
    suspend fun shutdown() {
        asyncLogger.shutdown()
    }
    
    fun getStorageInfo(): StorageInfo {
        val size = asyncLogger.getStorageSize()
        val dir = asyncLogger.getLogDirectory()
        return StorageInfo(
            sizeBytes = size,
            sizeMB = size / (1024f * 1024f),
            directory = dir.absolutePath
        )
    }
    
    companion object {
        private const val TAG = "LocalFileLogger"
    }
}

data class StorageInfo(
    val sizeBytes: Long,
    val sizeMB: Float,
    val directory: String
)
