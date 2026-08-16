package com.ttcoachai.db

import android.util.Log
import androidx.room.TypeConverter
import com.ttcoachai.managers.RepPoseCapture
import com.ttcoachai.shared.analysis.FocusArea
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Keypoint2D
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room converters for [com.ttcoachai.models.SessionAnalyticsEntity] JSON columns.
 * Uses Android org.json (not kotlinx-serialization) to keep the shared model plain,
 * mirroring [BaselineConverters].
 */
object SessionAnalyticsConverters {

    private const val TAG = "SessionAnalyticsConverters"

    @TypeConverter
    @JvmStatic
    fun floatListToJson(list: List<Float>): String {
        val array = JSONArray()
        for (v in list) array.put(v.toDouble())
        return array.toString()
    }

    @TypeConverter
    @JvmStatic
    fun jsonToFloatList(json: String): List<Float> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getDouble(it).toFloat() }
        } catch (e: Exception) {
            Log.w(TAG, "Malformed accuracy timeline JSON, returning empty list", e)
            emptyList()
        }
    }

    @TypeConverter
    @JvmStatic
    fun focusAreasToJson(list: List<FocusArea>): String {
        val array = JSONArray()
        for (fa in list) {
            array.put(JSONObject().put("type", fa.type.name).put("count", fa.count))
        }
        return array.toString()
    }

    @TypeConverter
    @JvmStatic
    fun jsonToFocusAreas(json: String): List<FocusArea> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val out = ArrayList<FocusArea>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val type = runCatching { CorrectionType.valueOf(obj.getString("type")) }
                    .getOrDefault(CorrectionType.GENERAL)
                out.add(FocusArea(type, obj.getInt("count")))
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "Malformed focus areas JSON, returning empty list", e)
            emptyList()
        }
    }

    private fun pointsToJsonArray(keypoints: List<Keypoint2D>): JSONArray {
        val array = JSONArray()
        for (kp in keypoints) {
            array.put(
                JSONObject()
                    .put("x", kp.x.toDouble())
                    .put("y", kp.y.toDouble())
                    .put("score", kp.score.toDouble())
            )
        }
        return array
    }

    private fun jsonArrayToPoints(array: JSONArray): List<Keypoint2D> =
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            Keypoint2D(
                x = obj.getDouble("x").toFloat(),
                y = obj.getDouble("y").toFloat(),
                score = obj.getDouble("score").toFloat()
            )
        }

    /** COCO-17 keypoint list -> JSON, for the rep-start/rep-end snapshot columns. */
    @JvmStatic
    fun keypointsToJson(keypoints: List<Keypoint2D>): String = pointsToJsonArray(keypoints).toString()

    /** Inverse of [keypointsToJson]. Malformed/blank JSON degrades to an empty list. */
    @JvmStatic
    fun jsonToKeypoints(json: String?): List<Keypoint2D> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            jsonArrayToPoints(JSONArray(json))
        } catch (e: Exception) {
            Log.w(TAG, "Malformed keypoints JSON, returning empty list", e)
            emptyList()
        }
    }

    /**
     * Up to the last 10 [RepPoseCapture]s -> JSON, for `repCapturesJson` — the full-session rep
     * buffer that backs the last-10-strokes carousel (see `RepCarouselDataSource`,
     * `RepCarouselView`). Each capture's start/end points reuse [pointsToJsonArray]'s per-point
     * schema; `flaggedTypes` is stored as an array of [CorrectionType] enum names.
     */
    @JvmStatic
    fun repCapturesToJson(captures: List<RepPoseCapture>): String {
        val array = JSONArray()
        for (capture in captures.takeLast(10)) {
            val flaggedTypes = JSONArray()
            for (type in capture.flaggedTypes) flaggedTypes.put(type.name)
            array.put(
                JSONObject()
                    .put("atMs", capture.atMs)
                    .put("start", pointsToJsonArray(capture.start))
                    .put("end", pointsToJsonArray(capture.end))
                    .put("flaggedTypes", flaggedTypes)
            )
        }
        return array.toString()
    }

    /** Inverse of [repCapturesToJson]. Malformed/blank JSON, or an unrecognized flagged-type
     *  name, degrades gracefully rather than failing the whole row (unknown types are skipped). */
    @JvmStatic
    fun jsonToRepCaptures(json: String?): List<RepPoseCapture> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                val flaggedArray = obj.optJSONArray("flaggedTypes")
                val flaggedTypes = mutableSetOf<CorrectionType>()
                if (flaggedArray != null) {
                    for (j in 0 until flaggedArray.length()) {
                        runCatching { CorrectionType.valueOf(flaggedArray.getString(j)) }
                            .getOrNull()
                            ?.let { flaggedTypes.add(it) }
                    }
                }
                RepPoseCapture(
                    atMs = obj.optLong("atMs", 0L),
                    start = jsonArrayToPoints(obj.getJSONArray("start")),
                    end = jsonArrayToPoints(obj.getJSONArray("end")),
                    flaggedTypes = flaggedTypes
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Malformed rep captures JSON, returning empty list", e)
            emptyList()
        }
    }
}
