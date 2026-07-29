package com.ttcoachai.util

import com.ttcoachai.shared.drill.DrillMetrics
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON<->model codec for the custom-drill editor's per-metric target bands
 * (`CustomDrillEntity.perPhaseTargetsJson`). Keys are `DrillMetrics` metric keys
 * (`"elbow_angle"`, `"knee_bend"`, ...) as of the 7-row editor rework (docs/superpowers/specs/
 * 2026-07-27-no-calibration-shipped-baseline-design.md §4). [LEGACY_KEY_MAP] remaps the two
 * pre-rework phase-string keys that used to have a live binding so old community-shared JSON
 * blobs (`CommunityDrillRepository`/`CommunityDrillMapper` sync `perPhaseTargetsJson` verbatim)
 * keep decoding — see docs/DESIGN_LIMITATIONS.md L-40 for what an old blob does NOT recover.
 *
 * Values decode/encode as doubles-then-Float (not truncated ints) — `stroke_speed`
 * (torso-lengths/s) and `coil_ratio` need fractional resolution; the 5 degree metrics still
 * round-trip whole numbers fine since `round(intValue) == intValue`.
 */
object PerPhaseTargetsCodec {

    /** Legacy-only: no longer produced by the editor, kept so old JSON keeps decoding. */
    const val KEY_KNEES_BACKSWING = "knees · backswing"
    const val KEY_KNEES_STRIKE = "knees · strike"
    const val KEY_TORSO_STRIKE = "torso tilt · strike"

    private val LEGACY_KEY_MAP: Map<String, String> = mapOf(
        KEY_KNEES_STRIKE to DrillMetrics.METRIC_KNEE_BEND,
        KEY_TORSO_STRIKE to DrillMetrics.METRIC_TORSO_LEAN,
    )

    /**
     * Parses [json] into a generic key -> (min, max) map. Unknown/malformed keys, blank
     * input, or unparseable JSON all yield an empty map (silent — same tolerance the
     * original inline decode had). A key present in [LEGACY_KEY_MAP] is remapped to its
     * DrillMetrics key on the way out; every other key (new-format DrillMetrics keys, or a
     * genuinely unrecognized key) passes through unchanged.
     */
    fun parse(json: String): Map<String, Pair<Float, Float>> {
        if (json.isBlank()) return emptyMap()
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<String, Pair<Float, Float>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val rawKey = keys.next()
            val arr = obj.optJSONArray(rawKey)?.takeIf { it.length() >= 2 } ?: continue
            val key = LEGACY_KEY_MAP[rawKey] ?: rawKey
            result[key] = arr.optDouble(0).toFloat() to arr.optDouble(1).toFloat()
        }
        return result
    }

    /** Inverse of [parse]'s value encoding (keys are written as-is — callers pass DrillMetrics
     *  keys directly, never a legacy phase string). Empty map encodes to `""`, matching how
     *  [parse] treats blank input. */
    fun encode(bands: Map<String, Pair<Float, Float>>): String {
        if (bands.isEmpty()) return ""
        val json = JSONObject()
        for ((key, pair) in bands) {
            json.put(key, JSONArray().put(pair.first.toDouble()).put(pair.second.toDouble()))
        }
        return json.toString()
    }
}
