package com.ttcoachai.core.logging

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Asynchronous file logger with zero latency impact on main operations.
 * 
 * Features:
 * - Non-blocking logging (< 0.01ms per call)
 * - Buffered writes (flush every 50 events or 5 seconds)
 * - Automatic cleanup (deletes logs older than 7 days)
 * - JSONL format (one JSON object per line)
 * - Thread-safe via Kotlin Channels
 */
class AsyncFileLogger(
    private val context: Context,
    private val bufferSize: Int = 50,
    private val flushIntervalMs: Long = 5000
) {
    // External storage (Download folder) - visible in File Manager
    private val logDir: File = File(
        context.getExternalFilesDir(null),
        "TT_Coach_AI/logs"
    )
    
    // Non-blocking queue (thread-safe, unlimited capacity)
    private val eventQueue = Channel<LogEvent>(capacity = Channel.UNLIMITED)
    
    // Background coroutine scope with supervisor (one failure doesn't crash others)
    private val loggerScope = CoroutineScope(
        Dispatchers.IO + 
        SupervisorJob() + 
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Logger error (non-fatal)", throwable)
        }
    )
    
    private var isRunning = true
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    init {
        // Create log directory structure
        logDir.mkdirs()
        File(logDir, "training_sessions").mkdirs()
        File(logDir, "performance_metrics").mkdirs()
        File(logDir, "errors").mkdirs()
        File(logDir, "events").mkdirs()
        File(logDir, "raw_poses").mkdirs()
        
        // Start background workers
        startLogConsumer()
        startCleanupWorker()
        
        Log.i(TAG, "AsyncFileLogger initialized. Log dir: ${logDir.absolutePath}")
    }
    
    // === PUBLIC API (all non-blocking!) ===
    
    fun logTrainingSession(sessionData: TrainingSessionData) {
        offerEvent(LogEvent.TrainingSession(sessionData))
    }
    
    fun logStrokeAnalysis(strokeData: StrokeAnalysisData) {
        offerEvent(LogEvent.StrokeAnalysis(strokeData))
    }
    
    fun logRuleEvaluation(ruleData: RuleEvaluationData) {
        offerEvent(LogEvent.RuleEvaluation(ruleData))
    }
    
    fun logPerformanceMetric(metricData: PerformanceMetricData) {
        offerEvent(LogEvent.PerformanceMetric(metricData))
    }
    
    fun logError(errorData: ErrorData) {
        offerEvent(LogEvent.Error(errorData))
    }
    
    fun logEvent(eventName: String, params: Map<String, Any>) {
        offerEvent(LogEvent.Generic(eventName, params, System.currentTimeMillis()))
    }
    
    fun logRawPose(rawPoseData: RawPoseData) {
        offerEvent(LogEvent.RawPose(rawPoseData))
    }
    
    @Volatile
    private var isFileLoggingEnabled = false

    fun setFileLoggingEnabled(enabled: Boolean) {
        isFileLoggingEnabled = enabled
        Log.i(TAG, "File logging ${if (enabled) "enabled" else "disabled"}")
    }

    // Non-blocking offer (returns immediately)
    private fun offerEvent(event: LogEvent) {
        if (!isFileLoggingEnabled && event !is LogEvent.Error) {
            return // Skip logging if disabled, but always log errors
        }
        
        val result = eventQueue.trySend(event)
        if (result.isFailure) {
            Log.w(TAG, "Event queue full, dropping event: ${event.javaClass.simpleName}")
        }
    }
    
    // === BACKGROUND PROCESSING ===
    
    private fun startLogConsumer() {
        loggerScope.launch {
            val buffer = mutableListOf<LogEvent>()
            
            while (isRunning) {
                try {
                    // Wait for event with timeout
                    val event = withTimeoutOrNull(flushIntervalMs) {
                        eventQueue.receive()
                    }
                    
                    if (event != null) {
                        buffer.add(event)
                    }
                    
                    // Flush if buffer full or timeout reached
                    if (buffer.size >= bufferSize || event == null) {
                        if (buffer.isNotEmpty()) {
                            flushBuffer(buffer)
                            buffer.clear()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in log consumer", e)
                }
            }
        }
    }
    
    private fun startCleanupWorker() {
        loggerScope.launch {
            // Run cleanup daily
            while (isRunning) {
                delay(24 * 60 * 60 * 1000)
                cleanupOldLogs()
            }
        }
    }
    
    private suspend fun flushBuffer(buffer: List<LogEvent>) = withContext(Dispatchers.IO) {
        try {
            // Group events by target file for efficient writing
            val grouped = buffer.groupBy { it.getLogFile() }
            
            grouped.forEach { (file, events) ->
                appendToFile(file, events)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush buffer", e)
        }
    }
    
    private fun appendToFile(file: File, events: List<LogEvent>) {
        try {
            file.parentFile?.mkdirs()
            
            // Use FileOutputStream with append=true
            java.io.FileOutputStream(file, true).bufferedWriter(charset = Charsets.UTF_8).use { writer ->
                events.forEach { event ->
                    writer.write(event.toJson())
                    writer.newLine()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to ${file.name}", e)
        }
    }
    
    private fun cleanupOldLogs() {
        try {
            val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            var deletedCount = 0
            var deletedSize = 0L
            
            logDir.walkTopDown().forEach { file ->
                if (file.isFile && file.lastModified() < weekAgo) {
                    deletedSize += file.length()
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.i(TAG, "Cleanup: deleted $deletedCount files (${deletedSize / 1024}KB)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    // === SHUTDOWN ===
    
    suspend fun shutdown() {
        Log.i(TAG, "Shutting down logger...")
        isRunning = false
        
        // Close channel and wait for pending events
        eventQueue.close()
        
        // Wait for completion with timeout
        withTimeoutOrNull(5000) {
            loggerScope.coroutineContext[Job]?.children?.forEach { it.join() }
        }
        
        loggerScope.cancel()
        Log.i(TAG, "Logger shutdown complete")
    }
    
    // === HELPERS ===
    
    private fun LogEvent.getLogFile(): File {
        val today = dateFormat.format(Date())
        val filename = when (this) {
            is LogEvent.TrainingSession -> "training_sessions/${today}_sessions.jsonl"
            is LogEvent.StrokeAnalysis -> "training_sessions/${today}_strokes.jsonl"
            is LogEvent.RuleEvaluation -> "training_sessions/${today}_rules.jsonl"
            is LogEvent.PerformanceMetric -> "performance_metrics/${today}_metrics.jsonl"
            is LogEvent.Error -> "errors/${today}_errors.jsonl"
            is LogEvent.Generic -> "events/${today}_events.jsonl"
            is LogEvent.RawPose -> "raw_poses/${today}_raw_poses.jsonl"
        }
        val file = File(logDir, filename)
        
        // Ensure parent directory exists before writing
        file.parentFile?.mkdirs()
        
        return file
    }
    
    fun getLogDirectory(): File = logDir
    
    fun getStorageSize(): Long {
        var size = 0L
        logDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }
    
    companion object {
        private const val TAG = "AsyncFileLogger"
    }
}
