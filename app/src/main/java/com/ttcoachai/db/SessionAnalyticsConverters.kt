package com.ttcoachai.db

import android.util.Log
import androidx.room.TypeConverter
import com.ttcoachai.shared.analysis.FocusArea
import com.ttcoachai.shared.models.CorrectionType
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
}
