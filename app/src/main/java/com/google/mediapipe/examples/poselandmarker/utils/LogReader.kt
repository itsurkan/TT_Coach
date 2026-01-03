package com.google.mediapipe.examples.poselandmarker.utils

import android.content.Context
import android.util.Log
import com.google.mediapipe.examples.poselandmarker.core.logging.TrainingSessionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Utility for reading and exporting logs written by AsyncFileLogger.
 */
class LogReader(context: Context) {
    private val logDir = File(context.filesDir, "logs")
    
    /**
     * Read all training sessions from a specific date.
     * @param date Format: "yyyy-MM-dd"
     */
    suspend fun readSessionLogs(date: String): List<TrainingSessionData> = withContext(Dispatchers.IO) {
        val file = File(logDir, "training_sessions/${date}_sessions.jsonl")
        if (!file.exists()) return@withContext emptyList()
        
        file.readLines().mapNotNull { line ->
            try {
                parseTrainingSession(line)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse session log line: $line", e)
                null
            }
        }
    }
    
    /**
     * Read all stroke analyses from a specific date.
     */
    suspend fun readStrokeLogs(date: String): List<StrokeLog> = withContext(Dispatchers.IO) {
        val file = File(logDir, "training_sessions/${date}_strokes.jsonl")
        if (!file.exists()) return@withContext emptyList()
        
        file.readLines().mapNotNull { line ->
            try {
                parseStrokeLog(line)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse stroke log line", e)
                null
            }
        }
    }
    
    /**
     * Get all available log dates.
     */
    suspend fun getAvailableDates(): List<String> = withContext(Dispatchers.IO) {
        val dates = mutableSetOf<String>()
        
        logDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "jsonl") {
                // Extract date from filename (e.g., "2026-01-03_sessions.jsonl")
                val filename = file.nameWithoutExtension
                val datePart = filename.substringBefore("_")
                if (datePart.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    dates.add(datePart)
                }
            }
        }
        
        dates.sorted()
    }
    
    /**
     * Export all logs to a ZIP file in External Storage (accessible via file manager).
     * Location: /sdcard/Android/data/[package]/files/logs_export_[timestamp].zip
     */
    suspend fun exportAllLogs(context: Context): File = withContext(Dispatchers.IO) {
        // Ensure external files directory exists
        val externalDir = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("External storage not available")
        externalDir.mkdirs()
        
        val exportFile = File(externalDir, "logs_export_${System.currentTimeMillis()}.zip")
        
        ZipOutputStream(FileOutputStream(exportFile)).use { zip ->
            logDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(logDir).path
                    zip.putNextEntry(ZipEntry(relativePath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        
        Log.i(TAG, "Exported logs to: ${exportFile.absolutePath}")
        exportFile
    }
    
    /**
     * Export logs to a readable folder structure in External Storage.
     * Copies all JSONL files to accessible location without ZIP compression.
     * Location: /sdcard/Android/data/[package]/files/logs/
     */
    suspend fun exportLogsToFolder(context: Context): File = withContext(Dispatchers.IO) {
        // Ensure external files directory exists
        val externalDir = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("External storage not available")
        val exportDir = File(externalDir, "logs")
        exportDir.mkdirs()
        
        var fileCount = 0
        logDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "jsonl") {
                val relativePath = file.relativeTo(logDir).path
                val targetFile = File(exportDir, relativePath)
                
                // Ensure parent directory exists
                targetFile.parentFile?.mkdirs()
                
                // Copy file
                file.copyTo(targetFile, overwrite = true)
                fileCount++
            }
        }
        
        Log.i(TAG, "Exported $fileCount log files to: ${exportDir.absolutePath}")
        exportDir
    }
    
    /**
     * Get storage statistics.
     */
    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        var totalSize = 0L
        var fileCount = 0
        val filesByType = mutableMapOf<String, Int>()
        
        logDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                totalSize += file.length()
                fileCount++
                
                val type = file.parentFile?.name ?: "other"
                filesByType[type] = filesByType.getOrDefault(type, 0) + 1
            }
        }
        
        StorageStats(
            totalSizeBytes = totalSize,
            totalSizeMB = totalSize / (1024f * 1024f),
            fileCount = fileCount,
            filesByType = filesByType
        )
    }
    
    // === Parsing helpers ===
    
    private fun parseTrainingSession(jsonLine: String): TrainingSessionData {
        val json = JSONObject(jsonLine)
        return TrainingSessionData(
            sessionId = json.getString("session_id"),
            exerciseId = json.getString("exercise_id"),
            exerciseName = json.getString("exercise_name"),
            startTime = json.getLong("start_time"),
            endTime = json.optLong("end_time").takeIf { it != 0L },
            totalStrokes = json.optInt("total_strokes"),
            goodStrokes = json.optInt("good_strokes"),
            averageScore = json.optDouble("average_score", 0.0).toFloat()
        )
    }
    
    private fun parseStrokeLog(jsonLine: String): StrokeLog {
        val json = JSONObject(jsonLine)
        return StrokeLog(
            strokeId = json.getString("stroke_id"),
            sessionId = json.getString("session_id"),
            timestamp = json.getLong("timestamp"),
            overallScore = json.getDouble("overall_score").toFloat(),
            isSuccessful = json.getBoolean("is_successful"),
            phase = json.getString("phase"),
            inferenceTimeMs = json.getLong("inference_time_ms"),
            frameNumber = json.getInt("frame_number")
        )
    }
    
    companion object {
        private const val TAG = "LogReader"
    }
}

data class StorageStats(
    val totalSizeBytes: Long,
    val totalSizeMB: Float,
    val fileCount: Int,
    val filesByType: Map<String, Int>
)

data class StrokeLog(
    val strokeId: String,
    val sessionId: String,
    val timestamp: Long,
    val overallScore: Float,
    val isSuccessful: Boolean,
    val phase: String,
    val inferenceTimeMs: Long,
    val frameNumber: Int
)
