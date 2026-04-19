package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.DetectedStroke
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame

/**
 * Collapses N detected strokes into a single "mean stroke" — a sequence of
 * averaged pose frames that represents the drill's canonical shape.
 *
 * Algorithm:
 *   1. Time-normalize each stroke's frames to a common length L (default 30).
 *      For each target index t ∈ [0, L), pick the source index
 *      `srcIdx = t / (L-1) * (len-1)` and linearly interpolate between the
 *      two nearest source frames.
 *   2. For each t, average all normalized landmarks across strokes component-wise
 *      (x, y, z, visibility, presence).
 *   3. Return a synthetic [PoseFrame] list with t as frame index and
 *      t * intervalMs as timestamp.
 *
 * Degenerate cases:
 * - N=1: time-normalization to L, no averaging needed.
 * - Stroke shorter than 2 frames: dropped (can't resample).
 */
object MeanStrokeBuilder {

    const val DEFAULT_TARGET_LENGTH: Int = 30

    fun build(
        strokes: List<DetectedStroke>,
        allFrames: List<PoseFrame>,
        targetLength: Int = DEFAULT_TARGET_LENGTH,
        intervalMs: Long = 33L
    ): List<PoseFrame> {
        require(targetLength >= 2) { "targetLength must be >= 2, got $targetLength" }
        if (allFrames.isEmpty() || strokes.isEmpty()) return emptyList()

        val strokeFrameSlices = strokes.mapNotNull { slice ->
            val start = slice.preparationStartFrame.coerceAtLeast(0)
            val end = slice.returnEndFrame.coerceAtMost(allFrames.size - 1)
            if (end <= start) return@mapNotNull null
            allFrames.subList(start, end + 1)
        }.filter { it.size >= 2 }

        if (strokeFrameSlices.isEmpty()) return emptyList()

        val normalized: List<List<PoseFrame>> = strokeFrameSlices.map { timeNormalize(it, targetLength) }

        val meanFrames = ArrayList<PoseFrame>(targetLength)
        for (t in 0 until targetLength) {
            val meanLandmarks = averageLandmarksAt(normalized, t)
            meanFrames += PoseFrame(
                frameIndex = t,
                timestampMs = t * intervalMs,
                landmarks = meanLandmarks
            )
        }
        return meanFrames
    }

    private fun timeNormalize(frames: List<PoseFrame>, targetLength: Int): List<PoseFrame> {
        val src = frames
        val srcLast = src.size - 1
        val out = ArrayList<PoseFrame>(targetLength)
        for (t in 0 until targetLength) {
            val progress = t.toDouble() / (targetLength - 1)
            val srcIdxFloat = progress * srcLast
            val lo = srcIdxFloat.toInt().coerceIn(0, srcLast)
            val hi = (lo + 1).coerceAtMost(srcLast)
            val alpha = (srcIdxFloat - lo).toFloat()
            out += interpolateFrame(src[lo], src[hi], alpha, t)
        }
        return out
    }

    private fun interpolateFrame(
        a: PoseFrame,
        b: PoseFrame,
        alpha: Float,
        targetIndex: Int
    ): PoseFrame {
        val interpolated = ArrayList<Landmark3D>(a.landmarks.size)
        val n = minOf(a.landmarks.size, b.landmarks.size)
        for (i in 0 until n) {
            val la = a.landmarks[i]; val lb = b.landmarks[i]
            interpolated += Landmark3D(
                x = la.x + (lb.x - la.x) * alpha,
                y = la.y + (lb.y - la.y) * alpha,
                z = la.z + (lb.z - la.z) * alpha,
                visibility = la.visibility + (lb.visibility - la.visibility) * alpha,
                presence = la.presence + (lb.presence - la.presence) * alpha
            )
        }
        return PoseFrame(
            frameIndex = targetIndex,
            timestampMs = a.timestampMs,
            landmarks = interpolated
        )
    }

    private fun averageLandmarksAt(
        normalizedStrokes: List<List<PoseFrame>>,
        t: Int
    ): List<Landmark3D> {
        val frames = normalizedStrokes.map { it[t] }
        val n = frames.first().landmarks.size
        val out = ArrayList<Landmark3D>(n)
        val strokeCount = frames.size.toFloat()
        for (i in 0 until n) {
            var sx = 0f; var sy = 0f; var sz = 0f; var sv = 0f; var sp = 0f
            for (f in frames) {
                val lm = f.landmarks[i]
                sx += lm.x; sy += lm.y; sz += lm.z
                sv += lm.visibility; sp += lm.presence
            }
            out += Landmark3D(
                x = sx / strokeCount,
                y = sy / strokeCount,
                z = sz / strokeCount,
                visibility = sv / strokeCount,
                presence = sp / strokeCount
            )
        }
        return out
    }
}
