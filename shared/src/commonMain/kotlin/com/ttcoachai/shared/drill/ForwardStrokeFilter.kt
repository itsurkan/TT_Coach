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
 * stroke moves the wrist in the player's FACING direction during the final
 * ~100 ms approach into the speed peak; recovery moves opposite. (Direction is
 * NOT read start→peak — see [wristDx] and DESIGN_LIMITATIONS L-28.)
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

    /**
     * Each dx-direction group must hold at least this many strokes before the
     * speed-dominance vote may decide the session facing — a single junk detection
     * must not be able to flip the session's facing vote.
     */
    const val MIN_GROUP_SIZE = 2

    /**
     * Direction is read over this window of APPROACH into the peak. ~100 ms is
     * long enough to span RTMPose jitter at any supported fps (≥1 frame even at
     * 10 fps fixtures) and short enough to stay inside the drive's final
     * acceleration regardless of where the start boundary landed (L-28).
     */
    const val PEAK_APPROACH_WINDOW_MS = 100L

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

    /**
     * Wrist x-displacement over the ~[PEAK_APPROACH_WINDOW_MS] approach INTO the
     * peak: x[peak] − x[approach], where approach is reached by walking back
     * from the peak while each step stays within [PEAK_APPROACH_WINDOW_MS] of it
     * (the earliest frame still inside that window), clamped to startFrame; null
     * when the wrist is gated at
     * either end. Start→peak displacement is deliberately NOT used: on continuous
     * play the smoothed speed never drops below the boundary floor between swings,
     * startFrame bleeds into the previous follow-through, and true drives read
     * backward (L-28 — video_4 dropped 7 of 12 drives). The drive accelerates
     * into the peak, so the approach direction IS the stroke direction.
     */
    private fun wristDx(
        stroke: Stroke2D,
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        minScore: Float
    ): Float? {
        val wristIdx = Coco17.wrist(handedness)
        val peakFrame = frames.getOrNull(stroke.peakFrame) ?: return null
        var a = stroke.peakFrame
        while (a > stroke.startFrame &&
            peakFrame.timestampMs - frames[a - 1].timestampMs <= PEAK_APPROACH_WINDOW_MS
        ) a--
        val approach = frames.getOrNull(a)?.keypoints?.getOrNull(wristIdx)
            ?.takeIf { it.score >= minScore } ?: return null
        val peak = peakFrame.keypoints.getOrNull(wristIdx)
            ?.takeIf { it.score >= minScore } ?: return null
        return peak.x - approach.x
    }

    /**
     * Session facing from speed asymmetry: +1/−1 when the corresponding dx-sign
     * group's median peak speed dominates the other by [SPEED_DOMINANCE_RATIO];
     * null when either group has fewer than [MIN_GROUP_SIZE] strokes (covers the
     * empty/single-direction case) or neither dominates.
     */
    private fun speedDominantFacing(verified: List<Pair<Stroke2D, Float>>): Float? {
        val posSpeeds = verified.filter { it.second > 0f }.map { it.first.peakSpeed }
        val negSpeeds = verified.filter { it.second < 0f }.map { it.first.peakSpeed }
        if (posSpeeds.size < MIN_GROUP_SIZE || negSpeeds.size < MIN_GROUP_SIZE) return null
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
