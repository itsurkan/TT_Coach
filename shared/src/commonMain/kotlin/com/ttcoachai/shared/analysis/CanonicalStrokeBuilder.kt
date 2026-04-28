package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.DetectedStroke
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame
import kotlin.math.sqrt

/**
 * Builds a single "canonical" (representative) stroke from a batch of detected
 * strokes. Replaces the prior mean+smooth+freeze-legs pipeline.
 *
 * Key differences vs. [MeanStrokeBuilder] + UpperBodySmoother + LegCanonicalizer:
 *
 *   - **Phase-anchored resampling.** Each stroke is stretched so its detected
 *     phase boundaries (prep → forward → contact → return → end) land at
 *     fixed fractions of the output length. Without this, uniform time-
 *     normalization smears motion because stroke A's backswing peak lines up
 *     with stroke B's contact frame, and component-wise averaging cancels out
 *     the motion.
 *
 *   - **Method choice.** BEST_REP (default) picks the one detected stroke
 *     closest to the centroid — preserves real motion instead of smearing it.
 *     MEDIAN is per-landmark per-time median — robust to outlier reps.
 *     MEAN matches the old behavior.
 *
 *   - **Legs preserved.** No static-stance overwrite. Leg noise is handled by
 *     the chosen collapse method (BEST_REP inherits one rep's clean legs;
 *     MEDIAN/MEAN attenuate MediaPipe jitter naturally).
 *
 *   - **Light smoothing.** Optional radius-1 moving average instead of the
 *     old radius-2 upper-body-only smoother. Applied to all landmarks.
 *
 *   - **Diagnostics.** Result reports stroke count, selected rep index (for
 *     BEST_REP), and per-landmark motion amplitude so the editor can flag
 *     "this stroke is frozen" before the user has to guess.
 */
object CanonicalStrokeBuilder {

    /** Default output length — matches the old [MeanStrokeBuilder.DEFAULT_TARGET_LENGTH]. */
    const val DEFAULT_TARGET_LENGTH: Int = 30

    enum class Method {
        /** Pick the stroke closest to the centroid. Preserves real motion. */
        BEST_REP,

        /** Per-landmark per-time median across strokes. Robust to outliers. */
        MEDIAN,

        /** Component-wise mean. Smears motion if strokes aren't phase-aligned. */
        MEAN
    }

    data class Config(
        val targetLength: Int = DEFAULT_TARGET_LENGTH,
        val method: Method = Method.BEST_REP,
        /** Align strokes by detected phase boundaries before resampling. */
        val phaseAlign: Boolean = true,
        /** Phase fraction targets: [forwardStart, contact, returnStart] within [0, 1]. */
        val phaseFractions: PhaseFractions = PhaseFractions(),
        /** Moving-average radius on the output (0 = off). */
        val smoothingRadius: Int = 1,
        /** Drop this many leading frames (trims the ready-stance prefix). */
        val trimLeadingFrames: Int = 0,
        /** Blend the tail toward the head for seamless looping. */
        val loopBlend: Boolean = true,
        /** Number of frames blended at the seam when loopBlend=true. */
        val loopBlendLength: Int = 3
    )

    /**
     * Where to place phase-boundary anchors within the canonical stroke.
     * Defaults approximate a forehand drive: 30% backswing, 55% forward (25%),
     * 65% contact window (10%), 100% follow-through (35%).
     */
    data class PhaseFractions(
        val forwardStart: Float = 0.30f,
        val contact: Float = 0.55f,
        val returnStart: Float = 0.65f
    )

    data class Result(
        val frames: List<PoseFrame>,
        val intervalMs: Long,
        val strokeCount: Int,
        /** Non-null only when method=BEST_REP; the index within `strokes`. */
        val selectedStrokeIndex: Int?,
        /** Mean per-landmark (maxX−minX + maxY−minY) across the output. Near-zero = frozen. */
        val motionAmplitude: Float
    )

    fun build(
        allFrames: List<PoseFrame>,
        strokes: List<DetectedStroke>,
        intervalMs: Long = 33L,
        config: Config = Config()
    ): Result {
        require(config.targetLength >= 2) { "targetLength must be >= 2, got ${config.targetLength}" }
        if (allFrames.isEmpty() || strokes.isEmpty()) {
            return Result(emptyList(), intervalMs, 0, null, 0f)
        }

        val slices: List<Pair<DetectedStroke, List<PoseFrame>>> = strokes.mapNotNull { s ->
            val start = s.preparationStartFrame.coerceAtLeast(0)
            val end = s.returnEndFrame.coerceAtMost(allFrames.size - 1)
            if (end <= start + 1) null else s to allFrames.subList(start, end + 1)
        }
        if (slices.isEmpty()) return Result(emptyList(), intervalMs, 0, null, 0f)

        val normalized: List<List<PoseFrame>> = if (config.phaseAlign) {
            slices.map { (stroke, slice) ->
                phaseAlignedResample(slice, stroke, config.targetLength, config.phaseFractions)
            }
        } else {
            slices.map { (_, slice) -> uniformResample(slice, config.targetLength) }
        }

        val (canonical, selectedIndex) = when (config.method) {
            Method.BEST_REP -> pickBestRep(normalized)
            Method.MEDIAN -> medianStroke(normalized) to null
            Method.MEAN -> meanStroke(normalized) to null
        }

        var out = if (config.trimLeadingFrames in 1 until canonical.size) {
            canonical.drop(config.trimLeadingFrames)
        } else canonical

        if (config.smoothingRadius > 0) {
            out = smoothTime(out, config.smoothingRadius)
        }

        if (config.loopBlend && out.size > config.loopBlendLength * 2) {
            out = loopBlendTail(out, config.loopBlendLength)
        }

        val reindexed = out.mapIndexed { t, f ->
            f.copy(frameIndex = t, timestampMs = t * intervalMs)
        }

        return Result(
            frames = reindexed,
            intervalMs = intervalMs,
            strokeCount = strokes.size,
            selectedStrokeIndex = selectedIndex,
            motionAmplitude = motionAmplitude(reindexed)
        )
    }

    // ---------------- resampling ----------------

    private fun uniformResample(src: List<PoseFrame>, targetLength: Int): List<PoseFrame> {
        val lastIdx = src.size - 1
        return List(targetLength) { t ->
            val progress = t.toDouble() / (targetLength - 1)
            val srcIdxF = progress * lastIdx
            val lo = srcIdxF.toInt().coerceIn(0, lastIdx)
            val hi = (lo + 1).coerceAtMost(lastIdx)
            interpolateFrame(src[lo], src[hi], (srcIdxF - lo).toFloat(), t)
        }
    }

    private fun phaseAlignedResample(
        slice: List<PoseFrame>,
        stroke: DetectedStroke,
        targetLength: Int,
        frac: PhaseFractions
    ): List<PoseFrame> {
        val base = stroke.preparationStartFrame
        // Source-space anchors (indices within `slice`).
        val srcAnchors = intArrayOf(
            0,
            (stroke.forwardStartFrame - base).coerceIn(0, slice.size - 1),
            (stroke.contactFrame - base).coerceIn(0, slice.size - 1),
            (stroke.returnStartFrame - base).coerceIn(0, slice.size - 1),
            slice.size - 1
        )
        // Target-space anchors (indices within output).
        val last = targetLength - 1
        val tgtAnchors = intArrayOf(
            0,
            (frac.forwardStart * last).toInt(),
            (frac.contact * last).toInt(),
            (frac.returnStart * last).toInt(),
            last
        )

        // If any phase collapsed (missing or out of order), fall back to uniform.
        if (!isStrictlyIncreasing(srcAnchors) || !isStrictlyIncreasing(tgtAnchors)) {
            return uniformResample(slice, targetLength)
        }

        return List(targetLength) { t ->
            val segIdx = tgtAnchors.indexOfLast { it <= t }.coerceAtLeast(0)
                .coerceAtMost(tgtAnchors.size - 2)
            val tgtStart = tgtAnchors[segIdx]; val tgtEnd = tgtAnchors[segIdx + 1]
            val srcStart = srcAnchors[segIdx]; val srcEnd = srcAnchors[segIdx + 1]
            val localProgress = (t - tgtStart).toFloat() / (tgtEnd - tgtStart).coerceAtLeast(1)
            val srcIdxF = srcStart + localProgress * (srcEnd - srcStart)
            val lo = srcIdxF.toInt().coerceIn(0, slice.size - 1)
            val hi = (lo + 1).coerceAtMost(slice.size - 1)
            interpolateFrame(slice[lo], slice[hi], srcIdxF - lo, t)
        }
    }

    private fun isStrictlyIncreasing(arr: IntArray): Boolean {
        for (i in 1 until arr.size) if (arr[i] <= arr[i - 1]) return false
        return true
    }

    private fun interpolateFrame(a: PoseFrame, b: PoseFrame, alpha: Float, tIdx: Int): PoseFrame {
        val n = minOf(a.landmarks.size, b.landmarks.size)
        val out = ArrayList<Landmark3D>(n)
        for (i in 0 until n) {
            val la = a.landmarks[i]; val lb = b.landmarks[i]
            out += Landmark3D(
                x = la.x + (lb.x - la.x) * alpha,
                y = la.y + (lb.y - la.y) * alpha,
                z = la.z + (lb.z - la.z) * alpha,
                visibility = la.visibility + (lb.visibility - la.visibility) * alpha,
                presence = la.presence + (lb.presence - la.presence) * alpha
            )
        }
        return PoseFrame(tIdx, a.timestampMs, out)
    }

    // ---------------- collapse methods ----------------

    private fun pickBestRep(normalized: List<List<PoseFrame>>): Pair<List<PoseFrame>, Int> {
        if (normalized.size == 1) return normalized[0] to 0
        val centroid = meanStroke(normalized)
        var bestIdx = 0
        var bestDist = Float.POSITIVE_INFINITY
        for (i in normalized.indices) {
            val d = l2Distance(normalized[i], centroid)
            if (d < bestDist) { bestDist = d; bestIdx = i }
        }
        return normalized[bestIdx] to bestIdx
    }

    private fun l2Distance(a: List<PoseFrame>, b: List<PoseFrame>): Float {
        val n = minOf(a.size, b.size)
        var sumSq = 0f
        for (t in 0 until n) {
            val la = a[t].landmarks; val lb = b[t].landmarks
            val k = minOf(la.size, lb.size)
            for (i in 0 until k) {
                val dx = la[i].x - lb[i].x
                val dy = la[i].y - lb[i].y
                val dz = la[i].z - lb[i].z
                sumSq += dx * dx + dy * dy + dz * dz
            }
        }
        return sqrt(sumSq)
    }

    private fun meanStroke(normalized: List<List<PoseFrame>>): List<PoseFrame> {
        val len = normalized[0].size
        val landmarkCount = normalized[0][0].landmarks.size
        val n = normalized.size.toFloat()
        return List(len) { t ->
            val out = ArrayList<Landmark3D>(landmarkCount)
            for (i in 0 until landmarkCount) {
                var sx = 0f; var sy = 0f; var sz = 0f; var sv = 0f; var sp = 0f
                for (stroke in normalized) {
                    val lm = stroke[t].landmarks[i]
                    sx += lm.x; sy += lm.y; sz += lm.z
                    sv += lm.visibility; sp += lm.presence
                }
                out += Landmark3D(sx / n, sy / n, sz / n, sv / n, sp / n)
            }
            PoseFrame(t, 0L, out)
        }
    }

    private fun medianStroke(normalized: List<List<PoseFrame>>): List<PoseFrame> {
        val len = normalized[0].size
        val landmarkCount = normalized[0][0].landmarks.size
        val n = normalized.size
        return List(len) { t ->
            val out = ArrayList<Landmark3D>(landmarkCount)
            for (i in 0 until landmarkCount) {
                val xs = FloatArray(n); val ys = FloatArray(n); val zs = FloatArray(n)
                val vs = FloatArray(n); val ps = FloatArray(n)
                for (j in 0 until n) {
                    val lm = normalized[j][t].landmarks[i]
                    xs[j] = lm.x; ys[j] = lm.y; zs[j] = lm.z
                    vs[j] = lm.visibility; ps[j] = lm.presence
                }
                out += Landmark3D(
                    x = median(xs), y = median(ys), z = median(zs),
                    visibility = median(vs), presence = median(ps)
                )
            }
            PoseFrame(t, 0L, out)
        }
    }

    private fun median(arr: FloatArray): Float {
        arr.sort()
        val n = arr.size
        return if (n % 2 == 1) arr[n / 2] else (arr[n / 2 - 1] + arr[n / 2]) / 2f
    }

    // ---------------- post-processing ----------------

    private fun smoothTime(frames: List<PoseFrame>, radius: Int): List<PoseFrame> {
        if (frames.size < 3 || radius < 1) return frames
        val n = frames.size
        val landmarkCount = frames[0].landmarks.size
        return List(n) { i ->
            val start = (i - radius).coerceAtLeast(0)
            val end = (i + radius).coerceAtMost(n - 1)
            val count = (end - start + 1).toFloat()
            val out = ArrayList<Landmark3D>(landmarkCount)
            for (k in 0 until landmarkCount) {
                var sx = 0f; var sy = 0f; var sz = 0f
                for (j in start..end) {
                    val lm = frames[j].landmarks[k]
                    sx += lm.x; sy += lm.y; sz += lm.z
                }
                val base = frames[i].landmarks[k]
                out += base.copy(x = sx / count, y = sy / count, z = sz / count)
            }
            frames[i].copy(landmarks = out)
        }
    }

    private fun loopBlendTail(frames: List<PoseFrame>, blendLen: Int): List<PoseFrame> {
        val n = frames.size
        val head = frames[0].landmarks
        val out = frames.toMutableList()
        for (k in 0 until blendLen) {
            val alpha = (k + 1f) / (blendLen + 1f)
            val srcIdx = n - 1 - k
            val current = frames[srcIdx].landmarks
            val m = minOf(current.size, head.size)
            val mixed = ArrayList<Landmark3D>(current.size)
            for (i in 0 until m) {
                val c = current[i]; val h = head[i]
                mixed += Landmark3D(
                    x = c.x * (1 - alpha) + h.x * alpha,
                    y = c.y * (1 - alpha) + h.y * alpha,
                    z = c.z * (1 - alpha) + h.z * alpha,
                    visibility = c.visibility,
                    presence = c.presence
                )
            }
            out[srcIdx] = frames[srcIdx].copy(landmarks = mixed)
        }
        return out
    }

    // ---------------- diagnostics ----------------

    private fun motionAmplitude(frames: List<PoseFrame>): Float {
        if (frames.size < 2) return 0f
        val landmarkCount = frames[0].landmarks.size
        if (landmarkCount == 0) return 0f
        var sum = 0f
        for (i in 0 until landmarkCount) {
            var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
            for (f in frames) {
                val lm = f.landmarks.getOrNull(i) ?: continue
                if (lm.x < minX) minX = lm.x
                if (lm.x > maxX) maxX = lm.x
                if (lm.y < minY) minY = lm.y
                if (lm.y > maxY) maxY = lm.y
            }
            sum += (maxX - minX) + (maxY - minY)
        }
        return sum / landmarkCount
    }
}
