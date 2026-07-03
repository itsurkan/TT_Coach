package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CameraAngleEstimatorTest {

    /** Torso: shoulder-mid (0.5, 0.3), hip-mid (0.5, 0.6) → torsoLen 0.3. */
    private fun frameWithShoulderSep(sepX: Float, score: Float = 1f): PoseFrame2D {
        val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
        kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.5f - sepX / 2, 0.3f, score)
        kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.5f + sepX / 2, 0.3f, score)
        kp[Coco17.LEFT_HIP] = Keypoint2D(0.5f, 0.6f, 1f)
        kp[Coco17.RIGHT_HIP] = Keypoint2D(0.5f, 0.6f, 1f)
        return PoseFrame2D(0, 0L, kp)
    }

    @Test
    fun perfectProfileIsZeroYaw() {
        val frames = List(10) { frameWithShoulderSep(0f) }
        assertEquals(0f, CameraAngleEstimator.estimateSideViewYawDeg(frames, 1f)!!, 0.5f)
    }

    @Test
    fun thirtyDegreeYawFromForeshortening() {
        // sin(30°) = sep / (0.9 × 0.3) → sep = 0.5 × 0.27 = 0.135
        val sep = (sin(30.0 * PI / 180.0) * 0.9 * 0.3).toFloat()
        val frames = List(10) { frameWithShoulderSep(sep) }
        assertEquals(30f, CameraAngleEstimator.estimateSideViewYawDeg(frames, 1f)!!, 1.5f)
    }

    @Test
    fun medianIgnoresOutlierFrames() {
        val frames = List(9) { frameWithShoulderSep(0f) } + frameWithShoulderSep(0.25f)
        assertEquals(0f, CameraAngleEstimator.estimateSideViewYawDeg(frames, 1f)!!, 0.5f)
    }

    @Test
    fun lowConfidenceShouldersYieldNull() {
        val frames = List(10) { frameWithShoulderSep(0.1f, score = 0.1f) }
        assertNull(CameraAngleEstimator.estimateSideViewYawDeg(frames, 1f))
    }

    @Test
    fun emptyInputYieldsNull() {
        assertNull(CameraAngleEstimator.estimateSideViewYawDeg(emptyList(), 1f))
    }

    @Test
    fun perStrokeYawUsesPreStrokeWindowNotTheSwing() {
        // Ready stance in profile (sep 0) frames 0..9; swing frames 10..15 with the
        // torso visibly rotated (sep 0.2 — the player's OWN rotation, not the camera).
        // At intervalMs=100 the 1000 ms lookback covers exactly frames 0..9.
        val frames = List(10) { frameWithShoulderSep(0f) } + List(6) { frameWithShoulderSep(0.2f) }
        val stroke = Stroke2D(strokeIndex = 0, startFrame = 10, peakFrame = 13, endFrame = 15, peakSpeed = 2f)
        assertEquals(0f, CameraAngleEstimator.estimateYawForStroke(frames, stroke, 1f, 100L)!!, 0.5f)
    }

    @Test
    fun perStrokeYawFallsBackToStrokeWindowAtRecordingStart() {
        // Stroke begins at frame 0 → no lookback window → falls back to the stroke itself.
        val sep = (sin(30.0 * PI / 180.0) * 0.9 * 0.3).toFloat()
        val frames = List(6) { frameWithShoulderSep(sep) }
        val stroke = Stroke2D(strokeIndex = 0, startFrame = 0, peakFrame = 3, endFrame = 5, peakSpeed = 2f)
        assertEquals(30f, CameraAngleEstimator.estimateYawForStroke(frames, stroke, 1f, 100L)!!, 1.5f)
    }
}
