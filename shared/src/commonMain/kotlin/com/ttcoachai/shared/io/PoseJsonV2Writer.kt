package com.ttcoachai.shared.io

import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Topology

/**
 * Writer for pose JSON schema v2 (docs/pose_json_schema_v2.md). Pure Kotlin, no dependencies
 * (shared-module convention) — a streaming mirror of [PoseJsonV2Parser], intentionally NOT a
 * shared abstraction with it: a bug in one must not be able to silently corrupt the other's
 * contract. Landmark field order (index, x, y, score) is load-bearing — see
 * [PoseJsonV2Parser.LANDMARK_RE] — and is hardcoded here, not configurable.
 *
 * Streaming usage: emit [header] once, then [frameLine] for every frame (isFirst=true only for
 * the very first one, to control the leading comma), then [footer] once. The caller never
 * needs the whole document in memory — see PoseSessionRecorder (app/.../pose/PoseSessionRecorder.kt).
 */
object PoseJsonV2Writer {

    /**
     * Reproduces Python's `round(v, 4)` + `json.dumps()` on a 4-decimal value: trailing zeros
     * trimmed ("0.896" not "0.8960"), "0.0"/"1.0" kept as a single trailing zero, negatives
     * handled, half-up rounding at the 4th decimal (0.99995 -> "1.0"). No `String.format`
     * (commonMain has none) — built from integer arithmetic only, via [kotlin.math.floor].
     *
     * Widening a Float straight to Double (`value.toDouble()`) preserves the Float's *binary*
     * value, not the decimal the caller meant — e.g. the literal `0.99995f` is actually stored
     * as `0.9999499917030334f`, so `*10000+0.5` lands at `9999.9999...`, which floors to `9999`
     * instead of crossing the boundary to `10000`. `Float.toString()` reconstructs the shortest
     * decimal string that round-trips to the same Float bits (the JVM/Kotlin contract used by
     * both source-literal parsing and JSON re-serialization), so reparsing through that string
     * recovers the intended decimal before scaling, matching Python's `round(v, 4)` semantics
     * (which operates on doubles, not on a truncated float32 binary value).
     */
    internal fun round4(value: Float): String {
        val negative = value < 0f
        val absValue = kotlin.math.abs(value).toString().toDouble()
        val scaled = kotlin.math.floor(absValue * 10000.0 + 0.5).toLong()
        val intPart = scaled / 10000L
        val fracPart = scaled % 10000L
        var fracStr = fracPart.toString().padStart(4, '0').trimEnd('0')
        if (fracStr.isEmpty()) fracStr = "0"
        val sign = if (negative && scaled != 0L) "-" else ""
        return "$sign$intPart.$fracStr"
    }

    fun header(
        topology: Topology,
        model: String,
        videoName: String,
        intervalMs: Long,
        totalFrames: Int,
        videoDurationMs: Long,
        videoWidth: Int,
        videoHeight: Int
    ): String {
        return "{" +
            "\"schemaVersion\":2," +
            "\"topology\":\"${topology.jsonName}\"," +
            "\"model\":\"$model\"," +
            "\"videoName\":\"$videoName\"," +
            "\"intervalMs\":$intervalMs," +
            "\"totalFrames\":$totalFrames," +
            "\"videoDurationMs\":$videoDurationMs," +
            "\"videoWidth\":$videoWidth," +
            "\"videoHeight\":$videoHeight," +
            "\"frames\":["
    }

    /** One frame object, compact JSON. [isFirst] must be true only for the very first frame in
     *  the document — every other frame gets a leading comma so a stream of these concatenates
     *  into a valid JSON array body. */
    fun frameLine(frame: PoseFrame2D, isFirst: Boolean): String {
        val landmarks = StringBuilder()
        frame.keypoints.forEachIndexed { index, kp ->
            if (index > 0) landmarks.append(',')
            landmarks.append("{\"index\":").append(index)
                .append(",\"x\":").append(round4(kp.x))
                .append(",\"y\":").append(round4(kp.y))
                .append(",\"score\":").append(round4(kp.score))
                .append('}')
        }
        val prefix = if (isFirst) "" else ","
        return "$prefix{\"frameIndex\":${frame.frameIndex},\"timestampMs\":${frame.timestampMs},\"landmarks\":[$landmarks]}"
    }

    fun footer(): String = "]}"
}
