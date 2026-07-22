package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShoulderCoilTest {

    private val intervalMs = 17L

    /** All 17 keypoints at (0.5, 0.5) score 1.0, with shoulder x-positions overridden. */
    private fun kps(leftX: Float, rightX: Float, score: Float = 1f): List<Keypoint2D> {
        val base = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1.0f) }
        base[Coco17.LEFT_SHOULDER] = Keypoint2D(leftX, 0.5f, score)
        base[Coco17.RIGHT_SHOULDER] = Keypoint2D(rightX, 0.5f, score)
        return base
    }

    private fun frame(index: Int, keypoints: List<Keypoint2D>) =
        PoseFrame2D(frameIndex = index, timestampMs = index * intervalMs, keypoints = keypoints)

    /** 20 frames: 0-9 narrow shoulder span (0.1), 10-19 wide shoulder span (0.3). */
    private fun narrowThenWideFrames(): List<PoseFrame2D> = (0 until 20).map { i ->
        if (i < 10) frame(i, kps(0.4f, 0.5f)) else frame(i, kps(0.35f, 0.65f))
    }

    @Test
    fun coilRatioGreaterThanOneWhenShouldersWiden() {
        val frames = narrowThenWideFrames()
        // radius = trunc(70/17) = 4 -> window [1,9] narrow (span 0.1), window [11,19] wide (span 0.3)
        val ratio = ShoulderCoil.coilRatio(
            frames = frames,
            startFrame = 5,
            endFrame = 15,
            xScale = 1f,
            intervalMs = intervalMs
        )
        assertEquals(3.0f, ratio!!, 0.01f)
    }

    @Test
    fun shoulderWidthNullOnMissingOrLowScoreShoulders() {
        // Below-threshold score on both shoulders.
        val lowScore = kps(0.4f, 0.5f, score = 0.1f)
        assertNull(ShoulderCoil.shoulderWidth(lowScore, xScale = 1f))

        // Missing keypoints entirely (list too short to contain shoulder indices).
        val missing = listOf(Keypoint2D(0.5f, 0.5f, 1f))
        assertNull(ShoulderCoil.shoulderWidth(missing, xScale = 1f))
    }

    @Test
    fun coilRatioNullWhenAnchorWindowHasNoValidWidth() {
        // Frames around endFrame all have empty keypoints (no person detected).
        val frames = (0 until 20).map { i ->
            if (i in 11..19) PoseFrame2D(i, i * intervalMs, emptyList())
            else frame(i, kps(0.4f, 0.5f))
        }
        val ratio = ShoulderCoil.coilRatio(
            frames = frames,
            startFrame = 5,
            endFrame = 15,
            xScale = 1f,
            intervalMs = intervalMs
        )
        assertNull(ratio)
    }

    @Test
    fun coilRatioNullWhenAWidthIsZero() {
        // Both shoulders at the same x at the start anchor -> width 0 (degenerate).
        val frames = (0 until 20).map { i ->
            if (i < 10) frame(i, kps(0.5f, 0.5f)) else frame(i, kps(0.35f, 0.65f))
        }
        val ratio = ShoulderCoil.coilRatio(
            frames = frames,
            startFrame = 5,
            endFrame = 15,
            xScale = 1f,
            intervalMs = intervalMs
        )
        assertNull(ratio)
    }

    @Test
    fun shoulderWidthAppliesXScale() {
        val kp = kps(0.4f, 0.6f)
        assertEquals(0.2f, ShoulderCoil.shoulderWidth(kp, xScale = 1f)!!, 0.001f)
        assertEquals(0.4f, ShoulderCoil.shoulderWidth(kp, xScale = 2f)!!, 0.001f)
    }
}
