package com.ttcoachai.debug

import com.ttcoachai.shared.models.PoseFrame

/**
 * Makes the tail of a mean stroke morph smoothly toward frame 0 so the editor
 * playback loops without a visible jump.
 *
 * Why: the captured stroke ends in the follow-through, not the ready stance,
 * so the last frame's pose doesn't match the first frame's. Over the last
 * [BLEND_FRAMES] frames we interpolate landmarks from the stroke pose toward
 * frame 0's pose using a smoothstep curve, so the loop seam disappears.
 */
object LoopBlender {

    private const val BLEND_FRAMES = 5

    fun blend(frames: List<PoseFrame>): List<PoseFrame> {
        val n = frames.size
        if (n < BLEND_FRAMES + 2) return frames
        val first = frames.first()

        return List(n) { i ->
            if (i < n - BLEND_FRAMES) {
                frames[i]
            } else {
                val k = i - (n - BLEND_FRAMES) // 0..BLEND_FRAMES-1
                val t = (k + 1).toFloat() / BLEND_FRAMES.toFloat()
                val w = smoothstep(t)
                val base = frames[i]
                val blended = base.landmarks.mapIndexed { idx, lm ->
                    val firstLm = first.landmarks.getOrNull(idx) ?: return@mapIndexed lm
                    lm.copy(
                        x = lm.x * (1f - w) + firstLm.x * w,
                        y = lm.y * (1f - w) + firstLm.y * w,
                        z = lm.z * (1f - w) + firstLm.z * w
                    )
                }
                base.copy(landmarks = blended)
            }
        }
    }

    private fun smoothstep(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }
}
