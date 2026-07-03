package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.PoseFrame2D
import kotlin.math.hypot

/**
 * Consolidation point for the small numeric helpers previously duplicated across
 * `detection/StrokeDetector2D`, `drill/RepFilter`, `drill/ForwardStrokeFilter`,
 * `drill/LocomotionFilter`, and `drill/DrillMetrics` (docs/superpowers/specs/
 * 2026-07-02-generic-movement-pipeline-design.md, weakness #6). Behavior is
 * unchanged from every prior private copy — this is a pure move, not a rewrite.
 */
object SignalMath {

    /** Sorted-median: odd count → middle element, even count → average of the two middles. */
    fun median(values: List<Float>): Float {
        require(values.isNotEmpty()) { "median() requires a non-empty list" }
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    /** Double overload — same semantics as [median] for `List<Float>`. */
    fun median(values: List<Double>): Double {
        require(values.isNotEmpty()) { "median() requires a non-empty list" }
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /**
     * Median xScale-corrected shoulder-mid→hip-mid distance over the sequence;
     * null if never measurable (then no strokes can be detected — L-01 normalizer).
     * CameraAngleEstimator has a sibling computation parameterized by aspectRatio —
     * different signature/purpose, not a duplicate of this one.
     */
    fun medianTorsoLength(frames: List<PoseFrame2D>, xScale: Float, minScore: Float): Float? {
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
        return median(lens)
    }

    private const val MIN_TORSO_LEN = 1e-4f
}
