package com.ttcoachai.debug

import android.content.Context
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Loads a pose-frame fixture from the app's `assets/fixtures/` directory so
 * the dev-only parameter editor (Phase 7) has a replayable reference rep
 * without needing a recorded session. The same schema used in
 * `shared/src/commonTest/resources/fixtures/`.
 */
object AssetPoseFrameLoader {

    data class LoadedFixture(
        val frames: List<PoseFrame>,
        val intervalMs: Long
    )

    fun load(context: Context, assetPath: String): LoadedFixture {
        val raw = context.assets.open(assetPath).use { stream ->
            stream.readBytes().toString(StandardCharsets.UTF_8)
        }
        val root = JSONObject(raw)
        val intervalMs = root.optLong("intervalMs", 33L)
        val framesArr = root.getJSONArray("frames")
        val frames = ArrayList<PoseFrame>(framesArr.length())
        for (i in 0 until framesArr.length()) {
            val obj = framesArr.getJSONObject(i)
            val lmArr = obj.getJSONArray("landmarks")
            val landmarks = ArrayList<Landmark3D>(lmArr.length())
            for (j in 0 until lmArr.length()) {
                val l = lmArr.getJSONObject(j)
                landmarks += Landmark3D(
                    x = l.getDouble("x").toFloat(),
                    y = l.getDouble("y").toFloat(),
                    z = l.getDouble("z").toFloat(),
                    visibility = l.optDouble("visibility", 0.0).toFloat(),
                    presence = l.optDouble("presence", 0.0).toFloat()
                )
            }
            frames += PoseFrame(
                frameIndex = obj.getInt("frameIndex"),
                timestampMs = obj.getLong("timestampMs"),
                landmarks = landmarks
            )
        }
        return LoadedFixture(frames = frames, intervalMs = intervalMs)
    }
}
