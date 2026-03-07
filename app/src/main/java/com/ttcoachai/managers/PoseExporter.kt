package com.ttcoachai.managers

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.ttcoachai.R
import com.ttcoachai.processors.VideoDebugProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles exporting pose detection results to JSON files.
 */
class PoseExporter(private val context: Context) {
    private val TAG = "PoseExporter"

    suspend fun exportPoses(videoUriString: String, videoDebugProcessor: VideoDebugProcessor) {
        val poses = videoDebugProcessor.getAllPoseResults()
        if (poses.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.toast_no_poses_export), Toast.LENGTH_SHORT).show()
            }
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val jsonRoot = JSONObject().apply {
                    put("videoUri", videoUriString)
                    val interval = 100
                    put("intervalMs", interval)
                    put("totalFrames", poses.size)
                    put("exportTimestamp", System.currentTimeMillis())
                }

                val framesArray = JSONArray()
                poses.forEachIndexed { index, poseResult ->
                    val frameObj = JSONObject().apply {
                        put("frameIndex", index)
                        put("timestampMs", index * 100L)
                    }

                    if (poseResult.landmarks().isNotEmpty()) {
                        val landmarksArray = JSONArray()
                        poseResult.landmarks()[0].forEach { landmark ->
                            landmarksArray.put(JSONObject().apply {
                                put("x", landmark.x())
                                put("y", landmark.y())
                                put("z", landmark.z())
                                put("visibility", landmark.visibility().orElse(0f))
                                put("presence", landmark.presence().orElse(0f))
                            })
                        }
                        frameObj.put("landmarks", landmarksArray)
                    } else {
                        frameObj.put("landmarks", JSONArray())
                    }
                    framesArray.put(frameObj)
                }

                jsonRoot.put("frames", framesArray)

                val filesDir = context.getExternalFilesDir(null)
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val file = File(filesDir, "poses_$timestamp.json")
                file.writeText(jsonRoot.toString(2))

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.toast_export_success, file.absolutePath, poses.size), Toast.LENGTH_LONG).show()
                    Log.i(TAG, "Exported ${poses.size} poses to ${file.absolutePath}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error exporting poses", e)
                    Toast.makeText(context, context.getString(R.string.toast_export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
