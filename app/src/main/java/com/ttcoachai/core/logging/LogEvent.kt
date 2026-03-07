package com.ttcoachai.core.logging

import org.json.JSONArray
import org.json.JSONObject

/**
 * Base sealed class for all log events.
 * Each event knows how to serialize itself to JSON.
 */
sealed class LogEvent {
    abstract fun toJson(): String
    
    data class TrainingSession(val data: TrainingSessionData) : LogEvent() {
        override fun toJson() = data.toJson()
    }
    
    data class StrokeAnalysis(val data: StrokeAnalysisData) : LogEvent() {
        override fun toJson() = data.toJson()
    }
    
    data class RuleEvaluation(val data: RuleEvaluationData) : LogEvent() {
        override fun toJson() = data.toJson()
    }
    
    data class PerformanceMetric(val data: PerformanceMetricData) : LogEvent() {
        override fun toJson() = data.toJson()
    }
    
    data class Error(val data: ErrorData) : LogEvent() {
        override fun toJson() = data.toJson()
    }
    
    data class Generic(
        val eventName: String,
        val params: Map<String, Any>,
        val timestamp: Long
    ) : LogEvent() {
        override fun toJson(): String {
            return JSONObject().apply {
                put("event", eventName)
                put("timestamp", timestamp)
                put("params", JSONObject(params))
            }.toString()
        }
    }
    
    data class RawPose(val data: RawPoseData) : LogEvent() {
        override fun toJson() = data.toJson()
    }
}

// === DATA MODELS ===

data class TrainingSessionData(
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val totalStrokes: Int = 0,
    val goodStrokes: Int = 0,
    val averageScore: Float = 0f
) {
    fun toJson(): String = JSONObject().apply {
        put("type", "training_session")
        put("session_id", sessionId)
        put("exercise_id", exerciseId)
        put("exercise_name", exerciseName)
        put("start_time", startTime)
        endTime?.let { put("end_time", it) }
        put("total_strokes", totalStrokes)
        put("good_strokes", goodStrokes)
        put("average_score", averageScore)
    }.toString()
}

data class StrokeAnalysisData(
    val sessionId: String,
    val strokeId: String,
    val timestamp: Long,
    val wristAngle: Float?,
    val bodyRotation: Float?,
    val followThroughAngle: Float?,
    val contactHeight: Float?,
    val elbowBodyDistance: Float?,
    val overallScore: Float,
    val isSuccessful: Boolean,
    val phase: String,
    val errors: List<String>,
    val inferenceTimeMs: Long,
    val frameNumber: Int
) {
    fun toJson(): String = JSONObject().apply {
        put("type", "stroke_analysis")
        put("session_id", sessionId)
        put("stroke_id", strokeId)
        put("timestamp", timestamp)
        put("wrist_angle", wristAngle)
        put("body_rotation", bodyRotation)
        put("follow_through_angle", followThroughAngle)
        put("contact_height", contactHeight)
        put("elbow_body_distance", elbowBodyDistance)
        put("overall_score", overallScore)
        put("is_successful", isSuccessful)
        put("phase", phase)
        put("errors", JSONArray(errors))
        put("inference_time_ms", inferenceTimeMs)
        put("frame_number", frameNumber)
    }.toString()
}

data class RuleEvaluationData(
    val strokeId: String,
    val timestamp: Long,
    val ruleName: String,
    val ruleCategory: String,
    val measuredValue: Float?,
    val expectedValue: Float?,
    val threshold: Float?,
    val passed: Boolean,
    val errorGenerated: String?,
    val explanation: String
) {
    fun toJson(): String = JSONObject().apply {
        put("type", "rule_evaluation")
        put("stroke_id", strokeId)
        put("timestamp", timestamp)
        put("rule_name", ruleName)
        put("rule_category", ruleCategory)
        put("measured_value", measuredValue)
        put("expected_value", expectedValue)
        put("threshold", threshold)
        put("passed", passed)
        put("error_generated", errorGenerated)
        put("explanation", explanation)
    }.toString()
}

data class PerformanceMetricData(
    val timestamp: Long,
    val metricName: String,
    val value: Float,
    val sessionId: String?
) {
    fun toJson(): String = JSONObject().apply {
        put("type", "performance_metric")
        put("timestamp", timestamp)
        put("metric_name", metricName)
        put("value", value)
        put("session_id", sessionId)
    }.toString()
}

data class ErrorData(
    val timestamp: Long,
    val errorType: String,
    val errorMessage: String,
    val stackTrace: String?,
    val context: String?
) {
    fun toJson(): String = JSONObject().apply {
        put("type", "error")
        put("timestamp", timestamp)
        put("error_type", errorType)
        put("error_message", errorMessage)
        put("stack_trace", stackTrace)
        put("context", context)
    }.toString()
}

/**
 * Raw pose landmarks data directly from MediaPipe (33 keypoints).
 * Stores unprocessed 3D coordinates for each body landmark.
 */
data class RawPoseData(
    val sessionId: String,
    val timestamp: Long,
    val frameNumber: Int,
    val inferenceTimeMs: Long,
    val landmarks: List<LandmarkData>,
    val worldLandmarks: List<LandmarkData>? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("type", "raw_pose")
        put("session_id", sessionId)
        put("timestamp", timestamp)
        put("frame_number", frameNumber)
        put("inference_time_ms", inferenceTimeMs)
        
        // Serialize landmarks array
        val landmarksArray = JSONArray()
        landmarks.forEach { landmark ->
            landmarksArray.put(JSONObject().apply {
                put("x", landmark.x)
                put("y", landmark.y)
                put("z", landmark.z)
                put("visibility", landmark.visibility)
                put("presence", landmark.presence)
            })
        }
        put("landmarks", landmarksArray)
        
        // Optionally serialize world landmarks (3D coordinates in meters)
        worldLandmarks?.let { worldLms ->
            val worldArray = JSONArray()
            worldLms.forEach { landmark ->
                worldArray.put(JSONObject().apply {
                    put("x", landmark.x)
                    put("y", landmark.y)
                    put("z", landmark.z)
                    put("visibility", landmark.visibility)
                    put("presence", landmark.presence)
                })
            }
            put("world_landmarks", worldArray)
        }
    }.toString()
}

/**
 * Single landmark (keypoint) data.
 * x, y: normalized coordinates (0.0 - 1.0)
 * z: depth relative to hips (meters)
 * visibility: likelihood the landmark is visible [0.0 - 1.0]
 * presence: likelihood the landmark is present in the frame [0.0 - 1.0]
 */
data class LandmarkData(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float
)
