package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrillMetricsTest {

    private fun frame(vararg overrides: Pair<Int, Keypoint2D>): PoseFrame2D {
        val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
        overrides.forEach { (i, p) -> kp[i] = p }
        return PoseFrame2D(0, 0L, kp)
    }

    @Test
    fun extractsAllFiveMetricsFromGoodFrame() {
        val f = frame(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.50f, 0.30f, 1f),
            Coco17.LEFT_SHOULDER to Keypoint2D(0.48f, 0.30f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.55f, 0.42f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.65f, 0.40f, 1f),
            Coco17.RIGHT_HIP to Keypoint2D(0.50f, 0.55f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.48f, 0.55f, 1f),
            Coco17.RIGHT_KNEE to Keypoint2D(0.50f, 0.72f, 1f),
            Coco17.RIGHT_ANKLE to Keypoint2D(0.52f, 0.90f, 1f)
        )
        val m = DrillMetrics.extractAtFrame(f, Handedness.RIGHT, 1f)
        assertEquals(
            setOf(
                DrillMetrics.METRIC_ELBOW_ANGLE,
                DrillMetrics.METRIC_SHOULDER_ANGLE,
                DrillMetrics.METRIC_KNEE_BEND,
                DrillMetrics.METRIC_TORSO_LEAN,
                DrillMetrics.METRIC_SHOULDER_TILT
            ),
            m.keys
        )
    }

    @Test
    fun lowScoreJointDropsOnlyAffectedMetrics() {
        val f = frame(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.50f, 0.30f, 1f),
            Coco17.LEFT_SHOULDER to Keypoint2D(0.48f, 0.30f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.55f, 0.42f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.65f, 0.40f, 0.1f), // gated
            Coco17.RIGHT_HIP to Keypoint2D(0.50f, 0.55f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.48f, 0.55f, 1f),
            Coco17.RIGHT_KNEE to Keypoint2D(0.50f, 0.72f, 1f),
            Coco17.RIGHT_ANKLE to Keypoint2D(0.52f, 0.90f, 1f)
        )
        val m = DrillMetrics.extractAtFrame(f, Handedness.RIGHT, 1f)
        assertFalse(DrillMetrics.METRIC_ELBOW_ANGLE in m, "elbow needs the wrist")
        assertTrue(DrillMetrics.METRIC_KNEE_BEND in m, "knee unaffected by wrist score")
    }

    @Test
    fun insaneValueIsDropped() {
        // Fully straight arm = 180° elbow — outside the 20–170° sanity band → dropped
        val f = frame(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.50f, 0.20f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.50f, 0.40f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.50f, 0.60f, 1f)
        )
        val m = DrillMetrics.extractAtFrame(f, Handedness.RIGHT, 1f)
        assertFalse(DrillMetrics.METRIC_ELBOW_ANGLE in m)
    }

    @Test
    fun emptyFrameYieldsNoMetrics() {
        assertTrue(DrillMetrics.extractAtFrame(PoseFrame2D(0, 0L, emptyList()), Handedness.RIGHT, 1f).isEmpty())
    }

    @Test
    fun sanityBoundsSpotChecks() {
        assertTrue(SanityBounds.isSane(DrillMetrics.METRIC_ELBOW_ANGLE, 90.0))
        assertFalse(SanityBounds.isSane(DrillMetrics.METRIC_ELBOW_ANGLE, 19.0))
        assertFalse(SanityBounds.isSane(DrillMetrics.METRIC_ELBOW_ANGLE, 171.0))
        assertTrue(SanityBounds.isSane("unknown_metric", 12345.0), "unknown metrics pass through")
    }

    /** 90°-elbow frame used by the peak-window tests. */
    private fun goodArmFrame(idx: Int, wrist: Keypoint2D = Keypoint2D(0.70f, 0.42f, 1f)): PoseFrame2D {
        val f = frame(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.50f, 0.22f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.50f, 0.42f, 1f),
            Coco17.RIGHT_WRIST to wrist
        )
        return f.copy(frameIndex = idx, timestampMs = idx * 33L)
    }

    @Test
    fun peakMetricsAreMedianSmoothedOverTheWindow() {
        // L-05: middle frame (the peak) has a jittered wrist that alone reads ~135°;
        // the ±70 ms window at 33 ms holds 5 frames and the median must ignore it.
        val frames = listOf(
            goodArmFrame(0), goodArmFrame(1),
            goodArmFrame(2, wrist = Keypoint2D(0.64f, 0.56f, 1f)), // jitter → ≈135°
            goodArmFrame(3), goodArmFrame(4)
        )
        val m = DrillMetrics.extractAtPeak(frames, peakFrame = 2, Handedness.RIGHT, 1f, intervalMs = 33L)
        assertEquals(90.0, m[DrillMetrics.METRIC_ELBOW_ANGLE]!!, 1.0)
    }

    @Test
    fun peakWindowDegradesToSingleFrameAtCoarseIntervals() {
        // radius = 70 ms / 100 ms = 0 frames → identical to extractAtFrame
        val frames = listOf(goodArmFrame(0), goodArmFrame(1), goodArmFrame(2))
        val atPeak = DrillMetrics.extractAtPeak(frames, peakFrame = 1, Handedness.RIGHT, 1f, intervalMs = 100L)
        val atFrame = DrillMetrics.extractAtFrame(frames[1], Handedness.RIGHT, 1f)
        assertEquals(atFrame.keys, atPeak.keys)
        atFrame.forEach { (k, v) -> assertEquals(v, atPeak[k]!!, 1e-9) }
    }
}
