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
 * Strokes whose direction cannot be verified (gated wrist at either end, or
 * indeterminate facing) are dropped too: an unverifiable rep must not enter a
 * baseline or trigger feedback. Facing comes from AngleCalculations2D.facingSign
 * (nose vs shoulder-mid, lean-invariant) evaluated at the stroke's START frame.
 */
object ForwardStrokeFilter {

    fun filter(
        strokes: List<Stroke2D>,
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE
    ): List<Stroke2D> = strokes.filter { isForward(it, frames, handedness, minScore) }

    private fun isForward(
        stroke: Stroke2D,
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        minScore: Float
    ): Boolean {
        val startKp = frames.getOrNull(stroke.startFrame)?.keypoints ?: return false
        val peakKp = frames.getOrNull(stroke.peakFrame)?.keypoints ?: return false

        val wristIdx = Coco17.wrist(handedness)
        val wristStart = startKp.getOrNull(wristIdx)?.takeIf { it.score >= minScore } ?: return false
        val wristPeak = peakKp.getOrNull(wristIdx)?.takeIf { it.score >= minScore } ?: return false

        val ls = startKp.getOrNull(Coco17.LEFT_SHOULDER)?.takeIf { it.score >= minScore } ?: return false
        val rs = startKp.getOrNull(Coco17.RIGHT_SHOULDER)?.takeIf { it.score >= minScore } ?: return false
        val facing = AngleCalculations2D.facingSign(startKp, (ls.x + rs.x) / 2f, minScore) ?: return false

        return (wristPeak.x - wristStart.x) * facing > 0f
    }
}
