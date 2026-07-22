package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import kotlin.math.abs

/**
 * Shoulder coil indicator — LOW-CONFIDENCE proxy, numeric only (no label/threshold here).
 *
 * On a side camera, trunk rotation cannot be measured in degrees. What IS visible is
 * projected shoulder WIDTH: the shoulder line foreshortens (narrows) when the player
 * coils into the backswing, then opens (widens) through the follow-through. The
 * within-rep ratio `width(drive.end) / width(drive.start)` is therefore a low-confidence
 * hint at whether the player coiled and released — computed here as a plain ratio; the
 * caller (a later DerivedMetrics/baseline path) decides what deviation means.
 *
 * Ported from `poses_viewer/src/drill2d/shoulderCoil.ts` (`estimateCoil`), simplified for
 * Kotlin: explicit [coilRatio] `startFrame`/`endFrame` anchors (drive.start → drive.end)
 * instead of a `StrokeCycle`, no `backswing === null` guard, and no `CoilLabel`/threshold
 * — this object returns the ratio only.
 */
object ShoulderCoil {

    /** Half-window (ms) around each anchor for the robust-median width estimate. Same as extractAtPeak. */
    const val DEFAULT_COIL_RADIUS_MS = 70L

    /**
     * Projected shoulder width at one frame: |RIGHT_SHOULDER.x - LEFT_SHOULDER.x| * xScale.
     * Null when either shoulder keypoint is missing or below [minScore].
     */
    fun shoulderWidth(
        kp: List<Keypoint2D>,
        xScale: Float,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE
    ): Float? {
        val ls = kp.getOrNull(Coco17.LEFT_SHOULDER)?.takeIf { it.score >= minScore } ?: return null
        val rs = kp.getOrNull(Coco17.RIGHT_SHOULDER)?.takeIf { it.score >= minScore } ?: return null
        return abs(rs.x - ls.x) * xScale
    }

    /**
     * Median shoulder width over a ±[radiusMs] window around [anchorFrame] (mirrors
     * MovementMetrics.extractAtPeak window logic). Null when no ungated width exists in the window.
     * Frames with empty keypoints are skipped.
     */
    fun medianWidthAround(
        frames: List<PoseFrame2D>,
        anchorFrame: Int,
        xScale: Float,
        intervalMs: Long,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
        radiusMs: Long = DEFAULT_COIL_RADIUS_MS
    ): Float? {
        require(intervalMs > 0) { "intervalMs must be > 0, got $intervalMs" }
        val radius = (radiusMs / intervalMs).toInt()
        val lo = (anchorFrame - radius).coerceAtLeast(0)
        val hi = (anchorFrame + radius).coerceAtMost(frames.lastIndex)
        val widths = mutableListOf<Float>()
        for (i in lo..hi) {
            val frame = frames[i]
            if (frame.keypoints.isEmpty()) continue
            val w = shoulderWidth(frame.keypoints, xScale, minScore) ?: continue
            widths.add(w)
        }
        if (widths.isEmpty()) return null
        return SignalMath.median(widths)
    }

    /**
     * Within-rep coil ratio = width([endFrame]) / width([startFrame]), each a ±[radiusMs] median
     * window. > 1 means the shoulders opened from coil (start=drive.start) to release (end=drive.end).
     * LOW-CONFIDENCE proxy (camera distance/yaw confounds partly cancel in the within-rep ratio).
     * Null when either anchor width is null OR zero (degenerate/unmeasurable — symmetric guard).
     */
    fun coilRatio(
        frames: List<PoseFrame2D>,
        startFrame: Int,
        endFrame: Int,
        xScale: Float,
        intervalMs: Long,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
        radiusMs: Long = DEFAULT_COIL_RADIUS_MS
    ): Float? {
        val widthStart = medianWidthAround(frames, startFrame, xScale, intervalMs, minScore, radiusMs)
        val widthEnd = medianWidthAround(frames, endFrame, xScale, intervalMs, minScore, radiusMs)
        if (widthStart == null || widthStart == 0f) return null
        if (widthEnd == null || widthEnd == 0f) return null
        return widthEnd / widthStart
    }
}
