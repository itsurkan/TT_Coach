package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * In-plane (2D) joint angles over COCO-17 keypoints. Adaptation of the dot-product
 * approach in [AngleCalculations] to the RTMPose 2D topology.
 *
 * All functions:
 *  - return null when any required keypoint is missing or below [minScore]
 *    (no feedback on low-confidence frames — spec quality gate);
 *  - take xScale = ViewGeometry.xScale, the combined horizontal correction
 *    (aspect ratio, because schema-v2 x and y are normalized by different axes,
 *    × 1/cos(cameraYaw) foreshortening compensation); x-deltas are multiplied
 *    by it before any trig.
 */
object AngleCalculations2D {

    const val DEFAULT_MIN_SCORE = 0.3f
    private const val RAD_TO_DEG = (180.0 / PI).toFloat()
    private const val EPSILON = 1e-9f
    private const val FACING_EPSILON = 1e-3f

    /** Inner angle at [b] formed by segments b→a and b→c, in degrees [0, 180]. */
    fun angleDeg(a: Keypoint2D, b: Keypoint2D, c: Keypoint2D, xScale: Float): Float {
        val baX = (a.x - b.x) * xScale
        val baY = a.y - b.y
        val bcX = (c.x - b.x) * xScale
        val bcY = c.y - b.y
        val mag = hypot(baX, baY) * hypot(bcX, bcY)
        if (mag < EPSILON) return 0f
        val cos = ((baX * bcX + baY * bcY) / mag).coerceIn(-1f, 1f)
        return acos(cos) * RAD_TO_DEG
    }

    /** Elbow angle: shoulder–elbow–wrist. 180° = straight arm. */
    fun elbowAngle(
        kp: List<Keypoint2D>,
        handedness: Handedness,
        xScale: Float,
        minScore: Float = DEFAULT_MIN_SCORE
    ): Float? = jointAngle(
        kp, Coco17.shoulder(handedness), Coco17.elbow(handedness), Coco17.wrist(handedness),
        xScale, minScore
    )

    /** Shoulder angle: hip–shoulder–elbow (upper arm vs torso). */
    fun shoulderAngle(
        kp: List<Keypoint2D>,
        handedness: Handedness,
        xScale: Float,
        minScore: Float = DEFAULT_MIN_SCORE
    ): Float? = jointAngle(
        kp, Coco17.hip(handedness), Coco17.shoulder(handedness), Coco17.elbow(handedness),
        xScale, minScore
    )

    /** Knee bend: hip–knee–ankle. 180° = straight leg. */
    fun kneeBend(
        kp: List<Keypoint2D>,
        handedness: Handedness,
        xScale: Float,
        minScore: Float = DEFAULT_MIN_SCORE
    ): Float? = jointAngle(
        kp, Coco17.hip(handedness), Coco17.knee(handedness), Coco17.ankle(handedness),
        xScale, minScore
    )

    /**
     * Torso lean: signed angle of the hip-mid → shoulder-mid line from vertical.
     * 0 = upright; POSITIVE = leaning toward the player's facing direction (forward
     * lean), independent of which way they face on screen (DESIGN_LIMITATIONS L-04:
     * an image-relative sign would give the opposite cue to a player standing the
     * other way). Facing comes from the nose (fallback: ear midpoint) relative to
     * hip-mid; returns null when facing is indeterminate (head keypoints gated or
     * dead-centered over the hips) — no measurement beats a possibly-flipped one.
     */
    fun torsoLean(
        kp: List<Keypoint2D>,
        xScale: Float,
        minScore: Float = DEFAULT_MIN_SCORE
    ): Float? {
        val ls = scored(kp, Coco17.LEFT_SHOULDER, minScore) ?: return null
        val rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore) ?: return null
        val lh = scored(kp, Coco17.LEFT_HIP, minScore) ?: return null
        val rh = scored(kp, Coco17.RIGHT_HIP, minScore) ?: return null
        val hipMidX = (lh.x + rh.x) / 2f
        val facing = facingSign(kp, hipMidX, minScore) ?: return null
        val dx = ((ls.x + rs.x) / 2f - hipMidX) * xScale
        // image y grows downward; -(shY - hpY) makes "up" positive
        val dy = -((ls.y + rs.y) / 2f - (lh.y + rh.y) / 2f)
        if (hypot(dx, dy) < EPSILON) return null
        return atan2(dx * facing, dy) * RAD_TO_DEG
    }

    /** +1 = facing +x, -1 = facing -x, null = indeterminate (L-04 sign normalizer). */
    private fun facingSign(kp: List<Keypoint2D>, hipMidX: Float, minScore: Float): Float? {
        val headX = scored(kp, Coco17.NOSE, minScore)?.x
            ?: run {
                val le = scored(kp, Coco17.LEFT_EAR, minScore)
                val re = scored(kp, Coco17.RIGHT_EAR, minScore)
                if (le != null && re != null) (le.x + re.x) / 2f else null
            }
            ?: return null
        val offset = headX - hipMidX
        if (abs(offset) < FACING_EPSILON) return null
        return if (offset > 0f) 1f else -1f
    }

    /**
     * Shoulder tilt vs horizon, folded to (-90°, 90°] so the result is independent
     * of which way the player faces. 0 = level shoulders.
     */
    fun shoulderTilt(
        kp: List<Keypoint2D>,
        xScale: Float,
        minScore: Float = DEFAULT_MIN_SCORE
    ): Float? {
        val ls = scored(kp, Coco17.LEFT_SHOULDER, minScore) ?: return null
        val rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore) ?: return null
        val dx = (rs.x - ls.x) * xScale
        val dy = rs.y - ls.y
        if (hypot(dx, dy) < EPSILON) return null
        var deg = atan2(dy, dx) * RAD_TO_DEG
        if (deg > 90f) deg -= 180f
        if (deg <= -90f) deg += 180f
        return deg
    }

    private fun jointAngle(
        kp: List<Keypoint2D>,
        aIdx: Int,
        bIdx: Int,
        cIdx: Int,
        xScale: Float,
        minScore: Float
    ): Float? {
        val a = scored(kp, aIdx, minScore) ?: return null
        val b = scored(kp, bIdx, minScore) ?: return null
        val c = scored(kp, cIdx, minScore) ?: return null
        return angleDeg(a, b, c, xScale)
    }

    private fun scored(kp: List<Keypoint2D>, idx: Int, minScore: Float): Keypoint2D? {
        val p = kp.getOrNull(idx) ?: return null
        return if (p.score >= minScore) p else null
    }
}
