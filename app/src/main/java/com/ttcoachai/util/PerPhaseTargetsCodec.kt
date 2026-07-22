package com.ttcoachai.util

import org.json.JSONObject

/**
 * Pure JSON<->model parsing for the custom-drill editor's "advanced per-phase targets"
 * blob (`CustomDrillEntity.perPhaseTargetsJson`, keys like "knees · backswing" / "knees
 * · strike" -> `[min, max]`). Extracted so [com.ttcoachai.ui.ExerciseEditorActivity]'s
 * decode and [com.ttcoachai.TrainingActivity]'s decode (previously duplicated inline,
 * TrainingActivity.kt ~91-107) share one parser instead of two independent `JSONObject`
 * walks over the same format.
 */
object PerPhaseTargetsCodec {

    const val KEY_KNEES_BACKSWING = "knees · backswing"
    const val KEY_KNEES_STRIKE = "knees · strike"

    /**
     * Parses [json] into a generic key -> (min, max) map. Unknown/malformed keys, blank
     * input, or unparseable JSON all yield an empty map (silent — same tolerance the
     * inline decode it replaces had via `runCatching`/`optJSONArray`).
     */
    fun parse(json: String): Map<String, Pair<Float, Float>> {
        if (json.isBlank()) return emptyMap()
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<String, Pair<Float, Float>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val arr = obj.optJSONArray(key)?.takeIf { it.length() >= 2 } ?: continue
            result[key] = arr.optInt(0).toFloat() to arr.optInt(1).toFloat()
        }
        return result
    }
}
