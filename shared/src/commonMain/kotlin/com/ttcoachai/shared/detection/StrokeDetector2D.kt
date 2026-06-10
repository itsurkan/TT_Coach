package com.ttcoachai.shared.detection

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import kotlin.math.hypot

/**
 * Stroke detection via wrist-speed local maximum (context doc §4), adapted from the
 * phase-FSM approach of [StrokePhaseDetector] to the 2D COCO topology. Batch API for
 * the fixture-driven phase; the smoothed-speed core is streaming-compatible (Phase 3).
 *
 * Units (DESIGN_LIMITATIONS L-01/L-02):
 *  - speeds are in TORSO-LENGTHS PER SECOND — invariant to camera distance/zoom
 *    (L-01) and to capture fps (L-02); torso = median xScale-corrected
 *    shoulder-mid→hip-mid distance over the sequence;
 *  - all tuning windows are in MILLISECONDS, converted to frame counts via
 *    [detect]'s intervalMs — Phase 3 capture fps is configurable 30/60/120, so
 *    frame-count tuning would silently change meaning with every fps setting.
 */
class StrokeDetector2D(
    private val minScore: Float = 0.3f,
    private val smoothingWindowMs: Long = 300,
    private val peakWindowRadiusMs: Long = 300,
    /**
     * Torso-lengths per second. Threshold applies to the SMOOTHED signal —
     * a raw peak of 2.4 torso/s smooths to ~2.0 with a 3-frame window.
     */
    private val minPeakSpeed: Float = 1.0f,
    private val boundaryFraction: Float = 0.3f,
    private val minPeakGapMs: Long = 500
) {

    /**
     * Detects strokes from the wrist-speed signal of [frames].
     *
     * Adjacent strokes never overlap: when boundary walks meet, both are clamped
     * at the inter-peak valley (they may share that single boundary frame).
     *
     * NOTE: [intervalMs] is integer milliseconds. At 120 fps (true 8.33 ms/frame),
     * truncation to 8 ms inflates computed speeds by ~4 %. Phase 3 live loop should
     * derive dt from actual frame timestamps rather than a fixed interval (deferred,
     * registry follow-up).
     */
    fun detect(
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        xScale: Float,
        intervalMs: Long
    ): List<Stroke2D> {
        require(intervalMs > 0) { "intervalMs must be > 0, got $intervalMs" }
        if (frames.size < 2) return emptyList()
        val torsoLen = medianTorsoLength(frames, xScale) ?: return emptyList()

        val speed = smooth(
            rawWristSpeeds(frames, handedness, xScale, torsoLen, intervalMs),
            window = framesFor(smoothingWindowMs, intervalMs)
        )
        val peaks = findPeaks(
            speed,
            radius = framesFor(peakWindowRadiusMs, intervalMs),
            minGap = framesFor(minPeakGapMs, intervalMs)
        )
        val strokes = peaks.mapIndexedTo(mutableListOf()) { idx, p ->
            val floor = speed[p] * boundaryFraction
            var start = p
            while (start > 0 && speed[start - 1] > floor) start--
            var end = p
            while (end < speed.lastIndex && speed[end + 1] > floor) end++
            Stroke2D(
                strokeIndex = idx,
                startFrame = start,
                peakFrame = p,
                endFrame = end,
                peakSpeed = speed[p]
            )
        }
        // Valley-clamp: ensure adjacent strokes never overlap.
        for (i in 0 until strokes.size - 1) {
            val a = strokes[i]
            val b = strokes[i + 1]
            if (a.endFrame >= b.startFrame) {
                // Find minimum smoothed speed in the open interval (a.peakFrame, b.peakFrame).
                var valley = a.peakFrame + 1
                for (j in a.peakFrame + 1..b.peakFrame) {
                    if (speed[j] < speed[valley]) valley = j
                }
                strokes[i] = a.copy(endFrame = valley)
                strokes[i + 1] = b.copy(startFrame = valley)
            }
        }
        return strokes
    }

    /** ms → frame count at the given interval, never below 1. */
    private fun framesFor(ms: Long, intervalMs: Long): Int =
        (ms / intervalMs).toInt().coerceAtLeast(1)

    private fun rawWristSpeeds(
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        xScale: Float,
        torsoLen: Float,
        intervalMs: Long
    ): FloatArray {
        val wristIdx = Coco17.wrist(handedness)
        val dtSec = intervalMs / 1000f
        val raw = FloatArray(frames.size)
        for (i in 1 until frames.size) {
            val prev = frames[i - 1].keypoints.getOrNull(wristIdx)
            val curr = frames[i].keypoints.getOrNull(wristIdx)
            raw[i] = if (prev == null || curr == null || prev.score < minScore || curr.score < minScore) {
                0f
            } else {
                hypot((curr.x - prev.x) * xScale, curr.y - prev.y) / torsoLen / dtSec
            }
        }
        return raw
    }

    /**
     * Median xScale-corrected shoulder-mid→hip-mid distance over the sequence;
     * null if never measurable (then no strokes can be detected — L-01 normalizer).
     * CameraAngleEstimator has a sibling computation parameterized by aspectRatio.
     */
    private fun medianTorsoLength(frames: List<PoseFrame2D>, xScale: Float): Float? {
        val lens = frames.mapNotNull { f ->
            val kp = f.keypoints
            val ls = kp.getOrNull(Coco17.LEFT_SHOULDER)?.takeIf { it.score >= minScore } ?: return@mapNotNull null
            val rs = kp.getOrNull(Coco17.RIGHT_SHOULDER)?.takeIf { it.score >= minScore } ?: return@mapNotNull null
            val lh = kp.getOrNull(Coco17.LEFT_HIP)?.takeIf { it.score >= minScore } ?: return@mapNotNull null
            val rh = kp.getOrNull(Coco17.RIGHT_HIP)?.takeIf { it.score >= minScore } ?: return@mapNotNull null
            val len = hypot(
                ((ls.x + rs.x) - (lh.x + rh.x)) / 2f * xScale,
                ((ls.y + rs.y) - (lh.y + rh.y)) / 2f
            )
            if (len < MIN_TORSO_LEN) null else len
        }
        if (lens.isEmpty()) return null
        val sorted = lens.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    /**
     * Centered box-average. The effective window is always odd: an even [window]
     * widens by one (e.g. window=2 behaves as 3, using half=1 on each side).
     */
    private fun smooth(raw: FloatArray, window: Int): FloatArray {
        if (window <= 1) return raw
        val half = window / 2
        val out = FloatArray(raw.size)
        for (i in raw.indices) {
            val lo = (i - half).coerceAtLeast(0)
            val hi = (i + half).coerceAtMost(raw.lastIndex)
            var sum = 0f
            for (j in lo..hi) sum += raw[j]
            out[i] = sum / (hi - lo + 1)
        }
        return out
    }

    /**
     * Local-maximum peak finding with keep-max NMS refractory.
     *
     * Candidates are admitted left-to-right. When a new candidate [i] is within
     * [minGap] of the previously admitted peak but [speed[i] > speed[peaks.last()]],
     * the previous peak is REPLACED by [i] (keep-max NMS) rather than [i] being
     * dropped. This prevents a small early bump from blocking a taller stroke peak.
     */
    private fun findPeaks(speed: FloatArray, radius: Int, minGap: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        for (i in speed.indices) {
            if (speed[i] < minPeakSpeed) continue
            val lo = (i - radius).coerceAtLeast(0)
            val hi = (i + radius).coerceAtMost(speed.lastIndex)
            var isPeak = true
            for (j in lo..hi) {
                // strictly greater than earlier frames → first index of a plateau wins
                if (j < i && speed[j] >= speed[i]) { isPeak = false; break }
                if (j > i && speed[j] > speed[i]) { isPeak = false; break }
            }
            if (!isPeak) continue
            if (peaks.isEmpty() || i - peaks.last() >= minGap) {
                peaks.add(i)
            } else if (speed[i] > speed[peaks.last()]) {
                // Keep-max NMS: replace the nearer but shorter peak with this taller one.
                peaks[peaks.lastIndex] = i
            }
        }
        return peaks
    }

    private companion object {
        const val MIN_TORSO_LEN = 1e-4f
    }
}
