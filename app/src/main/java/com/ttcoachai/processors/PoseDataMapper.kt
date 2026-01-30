package com.ttcoachai.processors

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.ttcoachai.services.JsonPoseFrame
import com.ttcoachai.services.JsonStrokeDetector
import com.ttcoachai.processors.VideoDebugProcessor.Companion.VIDEO_INTERVAL_MS
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.Optional
import org.json.JSONObject

/**
 * Handles conversion between JSON, MediaPipe results, and PoseFrame objects.
 */
class PoseDataMapper {
    fun parseJson(jsonString: String): List<PoseFrame> {
        val framesArray = JSONObject(jsonString).getJSONArray("frames")
        return (0 until framesArray.length()).map { i ->
            val obj = framesArray.getJSONObject(i)
            val lms = obj.getJSONArray("landmarks")
            val landmarks = (0 until lms.length()).map { j ->
                val lm = lms.getJSONObject(j)
                NormalizedLandmark.create(
                    lm.getDouble("x").toFloat(),
                    lm.getDouble("y").toFloat(),
                    lm.getDouble("z").toFloat(),
                    Optional.of(lm.optDouble("visibility", 0.0).toFloat()),
                    Optional.of(lm.optDouble("presence", 0.0).toFloat())
                )
            }
            PoseFrame(landmarks, obj.getLong("timestampMs"))
        }
    }

    fun toStrokeFrames(poseFrames: List<PoseFrame>?, results: List<PoseLandmarkerResult>?): List<JsonPoseFrame> {
        poseFrames?.let { frames ->
            return frames.mapIndexed { i, f -> JsonStrokeDetector.fromNormalizedLandmarks(f.landmarks, i, f.timestampMs) }
        }
        results?.let { bundle ->
            return bundle.mapIndexedNotNull { i, r ->
                if (r.landmarks().isNotEmpty()) JsonStrokeDetector.fromNormalizedLandmarks(r.landmarks()[0], i, i * VIDEO_INTERVAL_MS) else null
            }
        }
        return emptyList()
    }
}
