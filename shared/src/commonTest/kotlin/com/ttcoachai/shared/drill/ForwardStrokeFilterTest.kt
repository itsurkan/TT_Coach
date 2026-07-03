package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForwardStrokeFilterTest {

    /** Player faces +x: nose ahead of shoulder-mid. Wrist x per frame from [wristXs]. */
    private fun frames(wristXs: List<Float>, noseX: Float = 0.55f): List<PoseFrame2D> =
        wristXs.mapIndexed { i, wx ->
            val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
            kp[Coco17.NOSE] = Keypoint2D(noseX, 0.15f, 1f)
            kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.49f, 0.30f, 1f)
            kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.51f, 0.30f, 1f)
            kp[Coco17.LEFT_HIP] = Keypoint2D(0.49f, 0.55f, 1f)
            kp[Coco17.RIGHT_HIP] = Keypoint2D(0.51f, 0.55f, 1f)
            kp[Coco17.RIGHT_WRIST] = Keypoint2D(wx, 0.5f, 1f)
            PoseFrame2D(i, i * 100L, kp)
        }

    private fun stroke(start: Int, peak: Int, end: Int) =
        Stroke2D(strokeIndex = 0, startFrame = start, peakFrame = peak, endFrame = end, peakSpeed = 2.4f)

    /**
     * Builds a session of strokes with controlled dx sign + peakSpeed: stroke i owns
     * frames [3i, 3i+2], the wrist sweeping 0.5 → 0.5 + sign·0.1 → 0.5 so that
     * dx(start→peak) carries exactly the requested sign.
     */
    private fun session(
        specs: List<Pair<Float, Float>>, // (dxSign, peakSpeed)
        noseX: Float
    ): Pair<List<PoseFrame2D>, List<Stroke2D>> {
        val xs = mutableListOf<Float>()
        val strokes = mutableListOf<Stroke2D>()
        specs.forEachIndexed { i, (sign, speed) ->
            val base = 3 * i
            xs += listOf(0.5f, 0.5f + sign * 0.1f, 0.5f)
            strokes += Stroke2D(
                strokeIndex = i, startFrame = base, peakFrame = base + 1,
                endFrame = base + 2, peakSpeed = speed
            )
        }
        return frames(xs, noseX) to strokes
    }

    @Test
    fun keepsForwardStrokeDropsRecoverySwing() {
        // wrist sweeps +x (forward, facing +x) then back -x (recovery)
        val xs = listOf(0.50f, 0.55f, 0.62f, 0.70f, 0.72f, 0.68f, 0.60f, 0.53f, 0.50f)
        val f = frames(xs)
        val forward = stroke(start = 0, peak = 3, end = 4)
        val recovery = stroke(start = 4, peak = 7, end = 8)
        val kept = ForwardStrokeFilter.filter(listOf(forward, recovery), f, Handedness.RIGHT)
        assertEquals(listOf(forward), kept)
    }

    @Test
    fun mirroredPlayerKeepsMirroredForwardStroke() {
        // Player faces -x (nose left of shoulders): forward stroke moves wrist -x
        val xs = listOf(0.50f, 0.45f, 0.38f, 0.30f, 0.28f, 0.32f, 0.40f, 0.47f, 0.50f)
        val f = frames(xs, noseX = 0.45f)
        val forward = stroke(start = 0, peak = 3, end = 4)
        val recovery = stroke(start = 4, peak = 7, end = 8)
        assertEquals(listOf(forward), ForwardStrokeFilter.filter(listOf(forward, recovery), f, Handedness.RIGHT))
    }

    @Test
    fun indeterminateDirectionIsDropped() {
        // Nose dead-centered over shoulder-mid → facing unknown → unverifiable rep
        val xs = listOf(0.50f, 0.55f, 0.62f, 0.70f, 0.72f)
        val f = frames(xs, noseX = 0.50f)
        val s = stroke(start = 0, peak = 3, end = 4)
        assertTrue(ForwardStrokeFilter.filter(listOf(s), f, Handedness.RIGHT).isEmpty())
    }

    @Test
    fun speedDominanceKeepsFastGroupPositiveX() {
        // 3 fast dx>0 strokes (~8f) vs 2 slow dx<0 (~6f) → ratio ~1.33 > 1.2.
        // Head facing CONTRADICTS (nose on the -x side): dominance must override it.
        val (f, strokes) = session(
            specs = listOf(1f to 8.0f, 1f to 8.2f, 1f to 7.9f, -1f to 6.0f, -1f to 6.1f),
            noseX = 0.45f
        )
        val kept = ForwardStrokeFilter.filter(strokes, f, Handedness.RIGHT)
        assertEquals(strokes.take(3), kept)
    }

    @Test
    fun speedDominanceKeepsFastGroupNegativeX() {
        // Mirror polarity: fast group moves -x, head facing contradicts (+x side).
        val (f, strokes) = session(
            specs = listOf(-1f to 8.0f, -1f to 8.2f, -1f to 7.9f, 1f to 6.0f, 1f to 6.1f),
            noseX = 0.55f
        )
        val kept = ForwardStrokeFilter.filter(strokes, f, Handedness.RIGHT)
        assertEquals(strokes.take(3), kept)
    }

    @Test
    fun speedTieFallsBackToHeadFacing() {
        // Near-equal medians (6.55/6.05 ≈ 1.08 < 1.2) → no dominance → head facing
        // (+x) decides and keeps the (slightly slower!) positive group.
        val (f, strokes) = session(
            specs = listOf(1f to 6.0f, 1f to 6.1f, -1f to 6.5f, -1f to 6.6f),
            noseX = 0.55f
        )
        val kept = ForwardStrokeFilter.filter(strokes, f, Handedness.RIGHT)
        assertEquals(strokes.take(2), kept)
    }

    @Test
    fun singleJunkSpikeCannotFlipTheVote() {
        // 4 forward strokes dx>0 @6f plus ONE dx<0 junk spike @9f: minority group
        // size 1 < MIN_GROUP_SIZE → no dominance verdict → fallback to head facing
        // (nose +x) → the 4 forward strokes kept, junk dropped.
        val (f, strokes) = session(
            specs = listOf(1f to 6.0f, 1f to 6.0f, 1f to 6.0f, 1f to 6.0f, -1f to 9.0f),
            noseX = 0.55f
        )
        val kept = ForwardStrokeFilter.filter(strokes, f, Handedness.RIGHT)
        assertEquals(strokes.take(4), kept)
    }

    @Test
    fun gatedWristIsDropped() {
        val xs = listOf(0.50f, 0.55f, 0.62f, 0.70f, 0.72f)
        val f = frames(xs).map { fr ->
            val kp = fr.keypoints.toMutableList()
            kp[Coco17.RIGHT_WRIST] = kp[Coco17.RIGHT_WRIST].copy(score = 0.1f)
            fr.copy(keypoints = kp)
        }
        val s = stroke(start = 0, peak = 3, end = 4)
        assertTrue(ForwardStrokeFilter.filter(listOf(s), f, Handedness.RIGHT).isEmpty())
    }

    @Test
    fun continuousPlayBledStartBoundaryStillReadsForward() {
        // L-28: on continuous play the boundary walk bleeds startFrame back into
        // the previous follow-through (x already forward of the backswing trough),
        // so start→peak displacement reads BACKWARD on a true forward drive.
        // The ~100 ms approach INTO the peak must read forward instead.
        // x: follow-through 0.72 → backswing trough 0.50 → drive up to 0.71
        val xs = listOf(0.72f, 0.60f, 0.50f, 0.55f, 0.65f, 0.70f, 0.71f)
        val f = frames(xs) // nose +x (facing forward = +x)
        val drive = stroke(start = 0, peak = 4, end = 6) // x[4]−x[0] = −0.07 < 0
        val kept = ForwardStrokeFilter.filter(listOf(drive), f, Handedness.RIGHT)
        assertEquals(listOf(drive), kept)
    }
}
