package com.ttcoachai

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.json.JSONObject
import java.io.File
import java.util.Optional

/**
 * Utility class to load pose landmarks from JSON files for testing
 */
object JsonTestUtils {

    /**
     * Loads frames from a JSON file.
     */
    fun loadFramesFromJson(filePath: String): List<com.ttcoachai.services.JsonPoseFrame> {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }

        val jsonString = file.readText()
        val jsonObject = JSONObject(jsonString)
        val framesArray = jsonObject.getJSONArray("frames")
        
        val result = mutableListOf<com.ttcoachai.services.JsonPoseFrame>()
        
        for (i in 0 until framesArray.length()) {
            val frameObject = framesArray.getJSONObject(i)
            val landmarksArray = frameObject.getJSONArray("landmarks")
            val frameLandmarks = mutableListOf<com.ttcoachai.services.JsonLandmark>()
            
            for (j in 0 until landmarksArray.length()) {
                val lm = landmarksArray.getJSONObject(j)
                frameLandmarks.add(
                    com.ttcoachai.services.JsonLandmark(
                        index = lm.optInt("index", j),
                        x = lm.getDouble("x").toFloat(),
                        y = lm.getDouble("y").toFloat(),
                        z = lm.getDouble("z").toFloat(),
                        visibility = lm.optDouble("visibility", 1.0).toFloat(),
                        presence = lm.optDouble("presence", 1.0).toFloat()
                    )
                )
            }
            result.add(
                com.ttcoachai.services.JsonPoseFrame(
                    frameIndex = frameObject.getInt("frameIndex"),
                    timestampMs = frameObject.getLong("timestampMs"),
                    landmarks = frameLandmarks
                )
            )
        }
        
        return result
    }

    /**
     * Converts JsonLandmark to NormalizedLandmark
     */
    fun toNormalizedLandmarks(jsonLandmarks: List<com.ttcoachai.services.JsonLandmark>): List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> {
        return jsonLandmarks.map { lm ->
            NormalizedLandmark.create(
                lm.x, lm.y, lm.z, 
                Optional.of(lm.visibility), 
                Optional.of(lm.presence)
            )
        }
    }
}
