package com.ttcoachai.processors

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.ttcoachai.mappers.MediaPipeMapper
import com.ttcoachai.processors.VideoDebugProcessor.Companion.VIDEO_INTERVAL_MS
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame
import org.json.JSONObject

/** Local storage for pose frames parsed from JSON — uses Landmark3D for analysis. */
data class LocalPoseFrame(val landmarks: List<Landmark3D>, val timestampMs: Long)

/**
 * Handles conversion between JSON, MediaPipe results, and shared PoseFrame objects.
 */
class PoseDataMapper {
    fun parseJson(jsonString: String): List<LocalPoseFrame> {
        val framesArray = JSONObject(jsonString).getJSONArray("frames")
        return (0 until framesArray.length()).map { i ->
            val obj = framesArray.getJSONObject(i)
            val lms = obj.getJSONArray("landmarks")
            val landmarks = (0 until lms.length()).map { j ->
                val lm = lms.getJSONObject(j)
                Landmark3D(
                    x = lm.getDouble("x").toFloat(),
                    y = lm.getDouble("y").toFloat(),
                    z = lm.getDouble("z").toFloat(),
                    visibility = lm.optDouble("visibility", 0.0).toFloat(),
                    presence = lm.optDouble("presence", 0.0).toFloat()
                )
            }
            LocalPoseFrame(landmarks, obj.getLong("timestampMs"))
        }
    }

    fun toStrokeFrames(poseFrames: List<LocalPoseFrame>?, results: List<PoseLandmarkerResult>?): List<PoseFrame> {
        poseFrames?.let { frames ->
            return frames.mapIndexed { i, f ->
                PoseFrame(frameIndex = i, timestampMs = f.timestampMs, landmarks = f.landmarks)
            }
        }
        results?.let { bundle ->
            return bundle.mapIndexedNotNull { i, r ->
                if (r.landmarks().isNotEmpty()) {
                    PoseFrame(
                        frameIndex = i,
                        timestampMs = i * VIDEO_INTERVAL_MS,
                        landmarks = MediaPipeMapper.toLandmarkList(r)
                    )
                } else null
            }
        }
        return emptyList()
    }
}
