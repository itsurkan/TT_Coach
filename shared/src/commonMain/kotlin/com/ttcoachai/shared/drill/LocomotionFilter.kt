package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.AngleCalculations2D
import com.ttcoachai.shared.analysis.SignalMath
import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import kotlin.math.hypot

/**
 * Locomotion gate (L-30). A real forehand drive keeps the base (hip-mid) roughly
 * planted — weight transfers, the feet don't travel. Walking/stepping slides the
 * whole torso sideways yet still swings the arm forward fast enough to clear the
 * detector + [ForwardStrokeFilter] + [RepFilter]. This measures hip-mid horizontal
 * excursion over a stroke's window, normalized by torso-length (camera-distance
 * invariant), so such strokes can be rejected.
 *
 * Mirrored 1:1 by poses_viewer/src/drill2d/locomotionFilter.ts — per the binding
 * fix-flow rule this Kotlin object is the source of truth. The default threshold
 * (0.4 torso-lengths) is tuned on non-protocol footage: real drives in the andrii_1
 * / video_4 fixtures peak at ~0.25 hip travel, a walking step at ~0.68 — a wide gap.
 */
object LocomotionFilter {

    /**
     * Strokes whose hip-mid travels more than this many torso-lengths are
     * locomotion (walking), not strokes. A threshold ≤ 0 disables the gate.
     */
    const val DEFAULT_MAX_TRAVEL_TORSO = 0.4f

    private const val MIN_TORSO_LEN = 1e-4f

    /**
     * Peak-to-peak horizontal travel of hip-mid over [startFrame, endFrame], in
     * torso-lengths. x-deltas are xScale-corrected (schema v2 normalizes x by
     * width, y by height). null when hip-mid or torso-length is never measurable
     * in-window — the gate cannot prove locomotion, so callers keep such strokes.
     */
    fun hipMidTravelTorso(
        frames: List<PoseFrame2D>,
        stroke: Stroke2D,
        xScale: Float,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE
    ): Float? {
        val xs = ArrayList<Float>()
        val torsos = ArrayList<Float>()
        for (i in stroke.startFrame..stroke.endFrame) {
            val kp = frames.getOrNull(i)?.keypoints ?: continue
            val lh = kp.getOrNull(Coco17.LEFT_HIP)?.scoredOrNull(minScore) ?: continue
            val rh = kp.getOrNull(Coco17.RIGHT_HIP)?.scoredOrNull(minScore) ?: continue
            xs.add((lh.x + rh.x) / 2f)
            val ls = kp.getOrNull(Coco17.LEFT_SHOULDER)?.scoredOrNull(minScore) ?: continue
            val rs = kp.getOrNull(Coco17.RIGHT_SHOULDER)?.scoredOrNull(minScore) ?: continue
            val len = hypot(
                ((ls.x + rs.x - (lh.x + rh.x)) / 2f) * xScale,
                (ls.y + rs.y - (lh.y + rh.y)) / 2f
            )
            if (len >= MIN_TORSO_LEN) torsos.add(len)
        }
        if (xs.isEmpty() || torsos.isEmpty()) return null
        val torsoLen = SignalMath.median(torsos)
        val travel = (xs.max() - xs.min()) * xScale
        return travel / torsoLen
    }

    /**
     * Drops strokes whose hip-mid travels more than [maxTravelTorso] torso-lengths
     * (locomotion). Strokes whose travel can't be measured are KEPT — the gate can
     * never prove locomotion, so it doesn't reject on absence of evidence. A
     * threshold ≤ 0 disables the gate (all strokes returned unchanged).
     */
    fun filterStationary(
        strokes: List<Stroke2D>,
        frames: List<PoseFrame2D>,
        xScale: Float,
        maxTravelTorso: Float = DEFAULT_MAX_TRAVEL_TORSO,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE
    ): List<Stroke2D> {
        if (maxTravelTorso <= 0f) return strokes
        return strokes.filter { s ->
            val travel = hipMidTravelTorso(frames, s, xScale, minScore)
            travel == null || travel <= maxTravelTorso
        }
    }

    private fun Keypoint2D.scoredOrNull(minScore: Float): Keypoint2D? =
        if (score >= minScore) this else null
}
