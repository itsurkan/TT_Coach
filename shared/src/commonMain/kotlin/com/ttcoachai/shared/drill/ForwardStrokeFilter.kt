package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.AngleCalculations2D
import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D

/**
 * Drops wrist-speed peaks that are NOT forward strokes. Real footage shows ~half of
 * all speed peaks are recovery swings (return to ready) — same speed/duration class
 * as strokes, so RepFilter's banding cannot separate them. A forehand-drive forward
 * stroke moves the wrist in the player's FACING direction between startFrame and
 * peakFrame; recovery moves opposite.
 *
 * Facing is resolved at the SESSION level, not per stroke: on real footage the
 * per-frame head read (AngleCalculations2D.facingSign, nose vs shoulder-mid) is
 * noise — it oscillates with swing posture (andrii_1_rtm: 475 frames +1 vs 628 −1,
 * perfectly anti-correlated with swing phase at stroke-start frames, killing 23/23
 * strokes). Instead, strokes are grouped by wrist-dx sign and the group whose median
 * peak speed dominates by [SPEED_DOMINANCE_RATIO] is taken as forward — the drive
 * accelerates into contact, the recovery is a relaxed return (fixture: 8.1 vs 6.1).
 * When neither group dominates (or only one direction exists) the per-stroke head
 * read is the fallback, so synthetic/ambiguous inputs keep the conservative rule.
 *
 * Strokes whose direction cannot be verified (gated wrist at either end, or — in
 * the fallback — indeterminate facing) are dropped: an unverifiable rep must not
 * enter a baseline or trigger feedback.
 */
object ForwardStrokeFilter {

    /**
     * Forward swings must beat the opposite-direction group by this factor of
     * median peak speed before speed dominance decides the session facing.
     * Fixture ratio is ~1.33; ties (synthetic fixtures) fall back to head facing.
     */
    const val SPEED_DOMINANCE_RATIO = 1.2f

    fun filter(
        strokes: List<Stroke2D>,
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE
    ): List<Stroke2D> {
        val verified = strokes.mapNotNull { stroke ->
            wristDx(stroke, frames, handedness, minScore)?.let { dx -> stroke to dx }
        }
        val facing = speedDominantFacing(verified)
        return if (facing != null) {
            verified.filter { (_, dx) -> dx * facing > 0f }.map { it.first }
        } else {
            verified.filter { (stroke, dx) ->
                val headFacing = headFacingAtStart(stroke, frames, minScore)
                headFacing != null && dx * headFacing > 0f
            }.map { it.first }
        }
    }

    /** Wrist x-displacement start→peak; null when the wrist is gated at either end. */
    private fun wristDx(
        stroke: Stroke2D,
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        minScore: Float
    ): Float? {
        val wristIdx = Coco17.wrist(handedness)
        val start = frames.getOrNull(stroke.startFrame)?.keypoints?.getOrNull(wristIdx)
            ?.takeIf { it.score >= minScore } ?: return null
        val peak = frames.getOrNull(stroke.peakFrame)?.keypoints?.getOrNull(wristIdx)
            ?.takeIf { it.score >= minScore } ?: return null
        return peak.x - start.x
    }

    /**
     * Session facing from speed asymmetry: +1/−1 when the corresponding dx-sign
     * group's median peak speed dominates the other by [SPEED_DOMINANCE_RATIO];
     * null when either group is empty or neither dominates.
     */
    private fun speedDominantFacing(verified: List<Pair<Stroke2D, Float>>): Float? {
        val posSpeeds = verified.filter { it.second > 0f }.map { it.first.peakSpeed }
        val negSpeeds = verified.filter { it.second < 0f }.map { it.first.peakSpeed }
        if (posSpeeds.isEmpty() || negSpeeds.isEmpty()) return null
        val posMed = median(posSpeeds)
        val negMed = median(negSpeeds)
        return when {
            posMed >= negMed * SPEED_DOMINANCE_RATIO -> 1f
            negMed >= posMed * SPEED_DOMINANCE_RATIO -> -1f
            else -> null
        }
    }

    private fun headFacingAtStart(
        stroke: Stroke2D,
        frames: List<PoseFrame2D>,
        minScore: Float
    ): Float? {
        val kp = frames.getOrNull(stroke.startFrame)?.keypoints ?: return null
        val ls = kp.getOrNull(Coco17.LEFT_SHOULDER)?.takeIf { it.score >= minScore } ?: return null
        val rs = kp.getOrNull(Coco17.RIGHT_SHOULDER)?.takeIf { it.score >= minScore } ?: return null
        return AngleCalculations2D.facingSign(kp, (ls.x + rs.x) / 2f, minScore)
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
