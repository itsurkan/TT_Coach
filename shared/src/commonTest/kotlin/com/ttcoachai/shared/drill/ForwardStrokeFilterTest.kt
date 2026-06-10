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
}
