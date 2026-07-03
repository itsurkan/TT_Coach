package com.ttcoachai.db

import androidx.room.TypeConverter
import com.ttcoachai.shared.models.MetricStats
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room type conversion helpers for the calibration persistence layer.
 *
 * Uses Android's built-in org.json rather than a generated serializer — keeps
 * the shared `MetricStats` model plain (no @Serializable), and avoids pulling
 * a serialization plugin into the shared KMP module.
 */
object BaselineConverters {

    @TypeConverter
    @JvmStatic
    fun metricStatsMapToJson(map: Map<String, MetricStats>): String {
        val root = JSONObject()
        for ((key, stats) in map) {
            root.put(key, statsToJson(stats))
        }
        return root.toString()
    }

    @TypeConverter
    @JvmStatic
    fun jsonToMetricStatsMap(json: String): Map<String, MetricStats> {
        if (json.isBlank()) return emptyMap()
        val root = JSONObject(json)
        val out = LinkedHashMap<String, MetricStats>(root.length())
        for (key in root.keys()) {
            out[key] = jsonToStats(root.getJSONObject(key))
        }
        return out
    }

    @TypeConverter
    @JvmStatic
    fun intListToJson(list: List<Int>): String {
        val array = JSONArray()
        for (value in list) array.put(value)
        return array.toString()
    }

    @TypeConverter
    @JvmStatic
    fun jsonToIntList(json: String): List<Int> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { array.getInt(it) }
    }

    private fun statsToJson(stats: MetricStats): JSONObject = JSONObject().apply {
        put("mean", stats.mean)
        put("std", stats.std)
        put("min", stats.min)
        put("max", stats.max)
        put("sampleCount", stats.sampleCount)
    }

    private fun jsonToStats(obj: JSONObject): MetricStats = MetricStats(
        mean = obj.getDouble("mean"),
        std = obj.getDouble("std"),
        min = obj.getDouble("min"),
        max = obj.getDouble("max"),
        sampleCount = obj.getInt("sampleCount")
    )
}
