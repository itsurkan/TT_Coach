package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovementMetricsTest {

    private fun frame(vararg overrides: Pair<Int, Keypoint2D>): PoseFrame2D {
        val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
        overrides.forEach { (i, p) -> kp[i] = p }
        return PoseFrame2D(0, 0L, kp)
    }

    /** Synthetic spec: always returns a fixed value, ignoring keypoints — for testing plumbing. */
    private fun constantSpec(key: String, value: Float, bounds: ClosedFloatingPointRange<Double>? = null) = MetricSpec(
        key = key,
        precision = MetricPrecision.QUALITATIVE,
        sanityBounds = bounds,
        extractor = { _, _, _, _ -> value }
    )

    /** Spec that reads score off a specific keypoint index and gates on minScore, else returns a constant. */
    private fun gatedSpec(key: String, idx: Int, value: Float, minScoreOverride: Float? = null) = MetricSpec(
        key = key,
        precision = MetricPrecision.QUALITATIVE,
        sanityBounds = null,
        extractor = { kp, _, _, minScore ->
            val gate = minScoreOverride ?: minScore
            val p = kp.getOrNull(idx)
            if (p != null && p.score >= gate) value else null
        }
    )

    @Test
    fun customSyntheticSpecExtractsConstantValue() {
        val f = frame()
        val specs = listOf(constantSpec("custom_metric", 42f))
        val m = MovementMetrics.extractAtFrame(f, Handedness.RIGHT, 1f, specs)
        assertEquals(mapOf("custom_metric" to 42.0), m)
    }

    @Test
    fun scoreGatedSpecDropsWhenBelowMinScore() {
        val f = frame(5 to Keypoint2D(0.5f, 0.5f, 0.1f))
        val specs = listOf(gatedSpec("gated_metric", 5, 99f))
        val m = MovementMetrics.extractAtFrame(f, Handedness.RIGHT, 1f, specs, minScore = 0.3f)
        assertTrue(m.isEmpty())
    }

    @Test
    fun sanityBoundDropsOutOfRangeValue() {
        val f = frame()
        val specs = listOf(constantSpec("bounded_metric", 999f, bounds = 0.0..10.0))
        val m = MovementMetrics.extractAtFrame(f, Handedness.RIGHT, 1f, specs)
        assertTrue(m.isEmpty())
    }

    @Test
    fun noSanityBoundPassesAnyValueThrough() {
        val f = frame()
        val specs = listOf(constantSpec("unbounded_metric", 999f, bounds = null))
        val m = MovementMetrics.extractAtFrame(f, Handedness.RIGHT, 1f, specs)
        assertEquals(999.0, m["unbounded_metric"])
    }

    @Test
    fun oneCoreSpecMatchesAngleCalculations2DBehavior() {
        val f = frame(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.50f, 0.30f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.55f, 0.42f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.65f, 0.40f, 0.1f) // gated
        )
        val elbowSpec = CoreMetricSpecs.ALL.first { it.key == DrillMetrics.METRIC_ELBOW_ANGLE }
        val m = MovementMetrics.extractAtFrame(f, Handedness.RIGHT, 1f, listOf(elbowSpec))
        assertFalse(DrillMetrics.METRIC_ELBOW_ANGLE in m, "elbow needs the wrist above minScore")
    }

    @Test
    fun emptyFrameYieldsNoMetrics() {
        assertTrue(
            MovementMetrics.extractAtFrame(PoseFrame2D(0, 0L, emptyList()), Handedness.RIGHT, 1f, CoreMetricSpecs.ALL)
                .isEmpty()
        )
    }

    /** 90°-elbow frame used by the peak-window tests (mirrors DrillMetricsTest.goodArmFrame). */
    private fun goodArmFrame(idx: Int, wrist: Keypoint2D = Keypoint2D(0.70f, 0.42f, 1f)): PoseFrame2D {
        val f = frame(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.50f, 0.22f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.50f, 0.42f, 1f),
            Coco17.RIGHT_WRIST to wrist
        )
        return f.copy(frameIndex = idx, timestampMs = idx * 33L)
    }

    @Test
    fun extractAtPeakMedianWindowParityWithDrillMetrics() {
        // Same fixture as DrillMetricsTest.peakMetricsAreMedianSmoothedOverTheWindow:
        // middle frame's wrist jitters to ~135°; the ±70ms/33ms window (5 frames) must
        // median it away, matching DrillMetrics.extractAtPeak exactly when specs = CoreMetricSpecs.ALL.
        val frames = listOf(
            goodArmFrame(0), goodArmFrame(1),
            goodArmFrame(2, wrist = Keypoint2D(0.64f, 0.56f, 1f)),
            goodArmFrame(3), goodArmFrame(4)
        )
        val generic = MovementMetrics.extractAtPeak(
            frames, peakFrame = 2, Handedness.RIGHT, 1f, intervalMs = 33L, specs = CoreMetricSpecs.ALL
        )
        val legacy = DrillMetrics.extractAtPeak(frames, peakFrame = 2, Handedness.RIGHT, 1f, intervalMs = 33L)
        assertEquals(legacy.keys, generic.keys)
        legacy.forEach { (k, v) -> assertEquals(v, generic[k]!!, 1e-9) }
        assertEquals(90.0, generic[DrillMetrics.METRIC_ELBOW_ANGLE]!!, 1.0)
    }

    @Test
    fun extractAtPeakDegradesToSingleFrameAtCoarseIntervals() {
        val frames = listOf(goodArmFrame(0), goodArmFrame(1), goodArmFrame(2))
        val atPeak = MovementMetrics.extractAtPeak(
            frames, peakFrame = 1, Handedness.RIGHT, 1f, intervalMs = 100L, specs = CoreMetricSpecs.ALL
        )
        val atFrame = MovementMetrics.extractAtFrame(frames[1], Handedness.RIGHT, 1f, CoreMetricSpecs.ALL)
        assertEquals(atFrame.keys, atPeak.keys)
        atFrame.forEach { (k, v) -> assertEquals(v, atPeak[k]!!, 1e-9) }
    }

    @Test
    fun outOfBoundsPeakFrameThrows() {
        val frames = listOf(goodArmFrame(0), goodArmFrame(1))
        assertFailsWith<IllegalArgumentException> {
            MovementMetrics.extractAtPeak(frames, peakFrame = frames.size, Handedness.RIGHT, 1f, intervalMs = 33L, specs = CoreMetricSpecs.ALL)
        }
        assertFailsWith<IllegalArgumentException> {
            MovementMetrics.extractAtPeak(emptyList(), peakFrame = 0, Handedness.RIGHT, 1f, intervalMs = 33L, specs = CoreMetricSpecs.ALL)
        }
    }
}
