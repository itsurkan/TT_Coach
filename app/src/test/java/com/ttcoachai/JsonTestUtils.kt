package com.ttcoachai

import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame
import org.json.JSONObject
import java.io.File

/**
 * Utility class to load pose landmarks from JSON files for testing
 */
object JsonTestUtils {

    /**
     * Loads frames from a JSON file.
     */
    fun loadFramesFromJson(filePath: String): List<PoseFrame> {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }

        val jsonString = file.readText()
        val jsonObject = JSONObject(jsonString)
        val framesArray = jsonObject.getJSONArray("frames")

        val result = mutableListOf<PoseFrame>()

        for (i in 0 until framesArray.length()) {
            val frameObject = framesArray.getJSONObject(i)
            val landmarksArray = frameObject.getJSONArray("landmarks")
            val frameLandmarks = mutableListOf<Landmark3D>()

            for (j in 0 until landmarksArray.length()) {
                val lm = landmarksArray.getJSONObject(j)
                frameLandmarks.add(
                    Landmark3D(
                        x = lm.getDouble("x").toFloat(),
                        y = lm.getDouble("y").toFloat(),
                        z = lm.getDouble("z").toFloat(),
                        visibility = lm.optDouble("visibility", 1.0).toFloat(),
                        presence = lm.optDouble("presence", 1.0).toFloat()
                    )
                )
            }
            result.add(
                PoseFrame(
                    frameIndex = frameObject.getInt("frameIndex"),
                    timestampMs = frameObject.getLong("timestampMs"),
                    landmarks = frameLandmarks
                )
            )
        }

        return result
    }
}
