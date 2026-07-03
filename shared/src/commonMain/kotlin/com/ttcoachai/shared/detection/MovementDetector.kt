package com.ttcoachai.shared.detection

import com.ttcoachai.shared.analysis.SignalMath
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import kotlin.math.hypot

/**
 * Generic movement detection via signal-keypoint speed local maximum (context doc
 * §4), adapted from the phase-FSM approach of [StrokePhaseDetector] to the 2D COCO
 * topology. Batch API for the fixture-driven phase; the smoothed-speed core is
 * streaming-compatible (Phase 3). Tracked keypoint and tuning come from
 * [DetectionConfig] (docs/superpowers/specs/2026-07-02-generic-movement-pipeline-design.md)
 * — this is the generalized form of the former `StrokeDetector2D`, which is now a
 * thin delegating wrapper kept for API compatibility.
 *
 * Units (DESIGN_LIMITATIONS L-01/L-02):
 *  - speeds are in TORSO-LENGTHS PER SECOND — invariant to camera distance/zoom
 *    (L-01) and to capture fps (L-02); torso = median xScale-corrected
 *    shoulder-mid→hip-mid distance over the sequence;
 *  - all tuning windows are in MILLISECONDS, converted to frame counts via
 *    [detect]'s intervalMs — Phase 3 capture fps is configurable 30/60/120, so
 *    frame-count tuning would silently change meaning with every fps setting.
 */
class MovementDetector(private val config: DetectionConfig = DetectionConfig()) {

    /**
     * Detects movement reps from the configured-keypoint speed signal of [frames].
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
        val torsoLen = SignalMath.medianTorsoLength(frames, xScale, config.minScore) ?: return emptyList()

        val speed = smooth(
            rawSpeeds(frames, handedness, xScale, torsoLen, intervalMs),
            window = framesFor(config.smoothingWindowMs, intervalMs)
        )
        val peaks = findPeaks(
            speed,
            radius = framesFor(config.peakWindowRadiusMs, intervalMs),
            minGap = framesFor(config.minPeakGapMs, intervalMs)
        )
        val strokes = peaks.mapIndexedTo(mutableListOf()) { idx, p ->
            val floor = speed[p] * config.boundaryFraction
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

    private fun rawSpeeds(
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        xScale: Float,
        torsoLen: Float,
        intervalMs: Long
    ): FloatArray {
        val signalIdx = config.signalKeypoint.index(handedness)
        val dtSec = intervalMs / 1000f
        val raw = FloatArray(frames.size)
        for (i in 1 until frames.size) {
            val prev = frames[i - 1].keypoints.getOrNull(signalIdx)
            val curr = frames[i].keypoints.getOrNull(signalIdx)
            raw[i] = if (prev == null || curr == null || prev.score < config.minScore || curr.score < config.minScore) {
                0f
            } else {
                hypot((curr.x - prev.x) * xScale, curr.y - prev.y) / torsoLen / dtSec
            }
        }
        return raw
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
            if (speed[i] < config.minPeakSpeed) continue
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
}
