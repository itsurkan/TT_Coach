package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Synthetic-frame fixture: RIGHT-handed stroke, startFrame=5, peakFrame=15, endFrame=25,
 * intervalMs=17L (radius = trunc(70/17) = 4 for both [MovementMetrics.extractAtPeak] and
 * [ShoulderCoil]'s default windows).
 *
 * - Peak window [11,19]: shoulder-elbow-wrist angle = 90 deg.
 * - End window [21,29]: shoulder-elbow-wrist angle = 150 deg — DIFFERENT from the peak window,
 *   so a test asserting the endFrame value proves [DerivedMetrics.merge] did not accidentally
 *   sample the peak.
 * - Shoulder width: narrow (0.1) for i < 10 (covers the startFrame=5 window [1,9]), wide (0.4)
 *   for i >= 10 (covers the endFrame=25 window [21,29]) -> coil_ratio = 0.4 / 0.1 = 4.0.
 * - Frames 0-10 and 20 leave WRIST at its (0.5,0.5) default (not the 90/150 deg geometry); they
 *   are outside both sampled windows ([11,19] and [21,29]) so this is irrelevant to the assertions.
 */
class DerivedMetricsTest {

    private val intervalMs = 17L
    private val xScale = 1f

    private fun baseKeypoints(): MutableList<Keypoint2D> =
        MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }

    /**
     * Builds the 30-frame happy-path fixture.
     *
     * RIGHT_SHOULDER doubles as both an elbow-angle joint AND a shoulder-width endpoint, so its
     * x is driven by the width scheme (narrow 0.1 for i<10, wide 0.4 for i>=10) and ELBOW.x is
     * anchored to the same x — this keeps the elbow->shoulder vector a constant (0, -0.2)
     * regardless of which width band the frame is in, so the elbow-angle geometry below is
     * unaffected by the width scheme. WRIST is then placed relative to ELBOW to produce a
     * shoulder-elbow-wrist angle of exactly 90 deg in the peak window [11,19] and 150 deg in the
     * end window [21,29] — a different, unambiguous angle proves [DerivedMetrics.merge] samples
     * endFrame, not peakFrame. Frames outside both windows (0-10, 20) leave WRIST at its
     * (0.5,0.5) default; they aren't sampled by any assertion.
     *
     * [endArmScore] lets the gated test degrade the end-window WRIST score below
     * [com.ttcoachai.shared.analysis.AngleCalculations2D.DEFAULT_MIN_SCORE] while leaving shoulder
     * width (and therefore coil_ratio) untouched.
     */
    private fun buildFrames(endArmScore: Float = 1f): List<PoseFrame2D> = (0 until 30).map { i ->
        val kp = baseKeypoints()
        val narrow = i < 10
        val leftX = if (narrow) 0.45f else 0.3f
        val elbowX = if (narrow) 0.55f else 0.7f // also the RIGHT_SHOULDER x (width endpoint)
        val elbowY = 0.5f
        kp[Coco17.LEFT_SHOULDER] = Keypoint2D(leftX, 0.5f, 1f)
        kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(elbowX, elbowY - 0.2f, 1f)
        kp[Coco17.RIGHT_ELBOW] = Keypoint2D(elbowX, elbowY, 1f)
        when (i) {
            in 11..19 -> kp[Coco17.RIGHT_WRIST] = Keypoint2D(elbowX + 0.2f, elbowY, 1f) // 90 deg
            in 21..29 -> kp[Coco17.RIGHT_WRIST] = Keypoint2D(elbowX + 0.1f, elbowY + 0.1732f, endArmScore) // 150 deg
        }
        PoseFrame2D(frameIndex = i, timestampMs = i * intervalMs, keypoints = kp)
    }

    private fun stroke(peakSpeed: Float = 3.2f) = Stroke2D(
        strokeIndex = 0,
        startFrame = 5,
        peakFrame = 15,
        endFrame = 25,
        peakSpeed = peakSpeed
    )

    @Test
    fun followThroughSampledAtEndFrameNotPeak() {
        val frames = buildFrames()
        val result = DerivedMetrics.merge(frames, stroke(), Handedness.RIGHT, xScale, intervalMs)

        val followThrough = result[DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D]
        assertTrue(followThrough != null, "expected follow_through_angle_2d to be present")
        assertEquals(150.0, followThrough!!, 0.5)

        // Sanity: prove the peak window really is a different angle, so the assertion above is
        // meaningful (if the implementation regressed to sampling peakFrame, this would catch it).
        val peakWindowAngle = MovementMetrics.extractAtPeak(
            frames, stroke().peakFrame, Handedness.RIGHT, xScale, intervalMs,
            listOf(CoreMetricSpecs.FOLLOW_THROUGH)
        )[DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D]
        assertEquals(90.0, peakWindowAngle!!, 0.5)
        assertFalse(kotlin.math.abs(followThrough - peakWindowAngle) < 1.0)
    }

    @Test
    fun strokeSpeedEqualsPeakSpeed() {
        val frames = buildFrames()
        val s = stroke(peakSpeed = 4.75f)
        val result = DerivedMetrics.merge(frames, s, Handedness.RIGHT, xScale, intervalMs)

        assertEquals(s.peakSpeed.toDouble(), result[DrillMetrics.METRIC_STROKE_SPEED])
    }

    @Test
    fun coilRatioEqualsWidthRatio() {
        val frames = buildFrames()
        val result = DerivedMetrics.merge(frames, stroke(), Handedness.RIGHT, xScale, intervalMs)

        val coilRatio = result[DrillMetrics.METRIC_COIL_RATIO]
        assertTrue(coilRatio != null, "expected coil_ratio to be present")
        // narrow width 0.1 (0.55-0.45) -> wide width 0.4 (0.7-0.3): ratio 4.0
        assertEquals(4.0, coilRatio!!, 0.05)
    }

    @Test
    fun happyPathReturnsExactlyThreeKeys() {
        val frames = buildFrames()
        val result = DerivedMetrics.merge(frames, stroke(), Handedness.RIGHT, xScale, intervalMs)

        assertEquals(
            setOf(
                DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D,
                DrillMetrics.METRIC_STROKE_SPEED,
                DrillMetrics.METRIC_COIL_RATIO
            ),
            result.keys
        )
    }

    @Test
    fun followThroughOmittedWhenEndWindowArmScoreGated() {
        // End-window arm keypoints below DEFAULT_MIN_SCORE (0.3) -> elbowAngle returns null for
        // every frame in the endFrame window -> follow_through_angle_2d key omitted. Shoulder
        // width is untouched, so coil_ratio and stroke_speed remain present.
        val frames = buildFrames(endArmScore = 0.1f)
        val result = DerivedMetrics.merge(frames, stroke(), Handedness.RIGHT, xScale, intervalMs)

        assertNull(result[DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D])
        assertTrue(result.containsKey(DrillMetrics.METRIC_STROKE_SPEED))
        assertTrue(result.containsKey(DrillMetrics.METRIC_COIL_RATIO))
    }
}
