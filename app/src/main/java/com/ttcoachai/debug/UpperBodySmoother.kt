package com.ttcoachai.debug

import com.ttcoachai.shared.models.PoseFrame

/**
 * Temporal moving-average smoother for upper-body landmarks (indices 0..22).
 *
 * Why: even after rep-averaging, time-normalization leaves small kinks in the
 * curve because reps don't align perfectly frame-by-frame. A 5-frame centered
 * moving average removes those kinks so the looped preview reads as smooth
 * continuous motion. Applied only to upper body; legs are handled separately
 * by [LegCanonicalizer] (locked static stance).
 */
object UpperBodySmoother {

    private const val KERNEL_RADIUS = 2 // centered 5-frame moving average
    private const val UPPER_BODY_LAST_INDEX = 22

    fun smooth(frames: List<PoseFrame>): List<PoseFrame> {
        if (frames.size < 3) return frames
        val n = frames.size

        return List(n) { i ->
            val base = frames[i]
            val out = base.landmarks.toMutableList()
            val start = (i - KERNEL_RADIUS).coerceAtLeast(0)
            val end = (i + KERNEL_RADIUS).coerceAtMost(n - 1)
            val count = (end - start + 1).toFloat()

            for (idx in 0..UPPER_BODY_LAST_INDEX) {
                if (idx >= out.size) continue
                var sx = 0f; var sy = 0f; var sz = 0f
                for (j in start..end) {
                    val lm = frames[j].landmarks[idx]
                    sx += lm.x; sy += lm.y; sz += lm.z
                }
                out[idx] = out[idx].copy(x = sx / count, y = sy / count, z = sz / count)
            }
            base.copy(landmarks = out)
        }
    }
}
