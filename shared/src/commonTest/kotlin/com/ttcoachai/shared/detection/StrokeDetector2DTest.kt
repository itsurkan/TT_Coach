package com.ttcoachai.shared.detection

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeDetector2DTest {

    /**
     * Frames where only the right wrist x moves; shoulders/hips fixed with torso
     * length 0.25, so speeds are well-defined in torso-lengths/sec. Peak raw speed
     * of [singleStrokeXs] at 100 ms interval: 0.06 / 0.25 / 0.1 s = 2.4 torso/s.
     */
    private fun framesFromWristXs(xs: List<Float>, intervalMs: Long = 100L): List<PoseFrame2D> =
        xs.mapIndexed { i, wx ->
            val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
            kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.49f, 0.30f, 1f)
            kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.51f, 0.30f, 1f)
            kp[Coco17.LEFT_HIP] = Keypoint2D(0.49f, 0.55f, 1f)
            kp[Coco17.RIGHT_HIP] = Keypoint2D(0.51f, 0.55f, 1f)
            kp[Coco17.RIGHT_WRIST] = Keypoint2D(wx, 0.5f, 1f)
            PoseFrame2D(frameIndex = i, timestampMs = i * intervalMs, keypoints = kp)
        }

    // still — accelerate to peak — decelerate — still
    private val singleStrokeXs = listOf(
        0.50f, 0.50f, 0.50f, 0.50f,
        0.51f, 0.53f, 0.57f, 0.63f, 0.68f, 0.71f, 0.72f,
        0.72f, 0.72f, 0.72f, 0.72f
    )

    @Test
    fun detectsSingleStroke() {
        val strokes = StrokeDetector2D().detect(framesFromWristXs(singleStrokeXs), Handedness.RIGHT, 1f, 100L)
        assertEquals(1, strokes.size)
        val s = strokes[0]
        // raw speed peaks at frame 7 (0.057→0.063 = 0.06 → 2.4 torso/s); smoothing may shift ±1
        assertTrue(s.peakFrame in 6..8, "peak at ${s.peakFrame}")
        assertTrue(s.startFrame < s.peakFrame, "start ${s.startFrame} before peak")
        assertTrue(s.endFrame > s.peakFrame, "end ${s.endFrame} after peak")
        assertEquals(0, s.strokeIndex)
    }

    @Test
    fun detectsTwoStrokesWithGap() {
        val back = singleStrokeXs.reversed() // return swing of equal magnitude
        val xs = singleStrokeXs + back
        val strokes = StrokeDetector2D().detect(framesFromWristXs(xs), Handedness.RIGHT, 1f, 100L)
        assertEquals(2, strokes.size)
        assertTrue(strokes[1].peakFrame - strokes[0].peakFrame >= 5)
        assertEquals(listOf(0, 1), strokes.map { it.strokeIndex })
    }

    @Test
    fun subThresholdJitterYieldsNoStrokes() {
        // 0.004/frame jitter = 0.16 torso/s — far below the 1.0 torso/s default
        val xs = List(30) { 0.5f + (if (it % 2 == 0) 0.002f else -0.002f) }
        val strokes = StrokeDetector2D().detect(framesFromWristXs(xs), Handedness.RIGHT, 1f, 100L)
        assertEquals(0, strokes.size)
    }

    @Test
    fun msBasedTuningSurvivesFpsChange() {
        // The same motion sampled at 50 ms (linear 2× resample): displacement per
        // frame halves but torso-lengths/SEC are unchanged, and ms windows convert
        // to 2× the frame counts — still exactly one stroke. Frame-based tuning
        // would halve every window's time span and break this (L-02).
        val xs50 = singleStrokeXs.flatMapIndexed { i, x ->
            if (i == singleStrokeXs.lastIndex) listOf(x)
            else listOf(x, (x + singleStrokeXs[i + 1]) / 2f)
        }
        val strokes = StrokeDetector2D()
            .detect(framesFromWristXs(xs50, intervalMs = 50L), Handedness.RIGHT, 1f, 50L)
        assertEquals(1, strokes.size)
    }

    @Test
    fun lowScoreWristFramesContributeZeroSpeed() {
        val frames = framesFromWristXs(singleStrokeXs).map { f ->
            val kp = f.keypoints.toMutableList()
            kp[Coco17.RIGHT_WRIST] = kp[Coco17.RIGHT_WRIST].copy(score = 0.1f)
            f.copy(keypoints = kp)
        }
        assertEquals(0, StrokeDetector2D().detect(frames, Handedness.RIGHT, 1f, 100L).size)
    }

    @Test
    fun emptyAndTinyInputsAreSafe() {
        assertEquals(0, StrokeDetector2D().detect(emptyList(), Handedness.RIGHT, 1f, 100L).size)
        assertEquals(0, StrokeDetector2D().detect(framesFromWristXs(listOf(0.5f)), Handedness.RIGHT, 1f, 100L).size)
    }

    @Test
    fun detectionIsDeterministic() {
        val frames = framesFromWristXs(singleStrokeXs)
        val a = StrokeDetector2D().detect(frames, Handedness.RIGHT, 1f, 100L)
        val b = StrokeDetector2D().detect(frames, Handedness.RIGHT, 1f, 100L)
        assertEquals(a, b)
    }
}
