package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AngleCalculations2DTest {

    /** All 17 keypoints at (0.5, 0.5) score 1.0, with positional overrides. */
    private fun kps(vararg overrides: Pair<Int, Keypoint2D>): List<Keypoint2D> {
        val base = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1.0f) }
        overrides.forEach { (i, kp) -> base[i] = kp }
        return base
    }

    @Test
    fun elbowAngleRightAngle() {
        // shoulder above elbow, wrist to the right of elbow → 90°
        val kp = kps(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.5f, 0.2f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.7f, 0.4f, 1f)
        )
        assertEquals(90f, AngleCalculations2D.elbowAngle(kp, Handedness.RIGHT, 1f)!!, 0.1f)
    }

    @Test
    fun elbowAngleStraightArm() {
        val kp = kps(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.5f, 0.2f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.5f, 0.6f, 1f)
        )
        assertEquals(180f, AngleCalculations2D.elbowAngle(kp, Handedness.RIGHT, 1f)!!, 0.1f)
    }

    @Test
    fun elbowAngleLeftHand() {
        val kp = kps(
            Coco17.LEFT_SHOULDER to Keypoint2D(0.5f, 0.2f, 1f),
            Coco17.LEFT_ELBOW to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.LEFT_WRIST to Keypoint2D(0.3f, 0.4f, 1f)
        )
        assertEquals(90f, AngleCalculations2D.elbowAngle(kp, Handedness.LEFT, 1f)!!, 0.1f)
    }

    @Test
    fun xScaleChangesTheAngle() {
        // 135° at xScale 1: elbow→shoulder (0,-0.2), elbow→wrist (0.2, 0.2)
        val kp = kps(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.5f, 0.2f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.7f, 0.6f, 1f)
        )
        assertEquals(135f, AngleCalculations2D.elbowAngle(kp, Handedness.RIGHT, 1f)!!, 0.1f)
        // xScale 0.5 → elbow→wrist becomes (0.1, 0.2) → cos = -0.04/(0.2·0.2236) → 153.43°
        assertEquals(153.43f, AngleCalculations2D.elbowAngle(kp, Handedness.RIGHT, 0.5f)!!, 0.5f)
    }

    @Test
    fun lowScoreGatesToNull() {
        val kp = kps(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.5f, 0.2f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.7f, 0.4f, 0.1f) // below default 0.3
        )
        assertNull(AngleCalculations2D.elbowAngle(kp, Handedness.RIGHT, 1f))
    }

    @Test
    fun missingKeypointsGateToNull() {
        assertNull(AngleCalculations2D.elbowAngle(emptyList(), Handedness.RIGHT, 1f))
    }

    @Test
    fun shoulderAngle() {
        // at shoulder: →hip (0, 0.3), →elbow (0.15, 0.05) → 71.57°
        val kp = kps(
            Coco17.RIGHT_HIP to Keypoint2D(0.5f, 0.6f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.5f, 0.3f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.65f, 0.35f, 1f)
        )
        assertEquals(71.57f, AngleCalculations2D.shoulderAngle(kp, Handedness.RIGHT, 1f)!!, 0.5f)
    }

    @Test
    fun kneeBendStraightAndBent() {
        val straight = kps(
            Coco17.RIGHT_HIP to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_KNEE to Keypoint2D(0.5f, 0.6f, 1f),
            Coco17.RIGHT_ANKLE to Keypoint2D(0.5f, 0.8f, 1f)
        )
        assertEquals(180f, AngleCalculations2D.kneeBend(straight, Handedness.RIGHT, 1f)!!, 0.1f)

        // knee→hip (0,-0.2), knee→ankle (0.1, 0.15) → 146.31°
        val bent = kps(
            Coco17.RIGHT_HIP to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_KNEE to Keypoint2D(0.5f, 0.6f, 1f),
            Coco17.RIGHT_ANKLE to Keypoint2D(0.6f, 0.75f, 1f)
        )
        assertEquals(146.31f, AngleCalculations2D.kneeBend(bent, Handedness.RIGHT, 1f)!!, 0.5f)
    }

    @Test
    fun torsoLeanVerticalIsZero() {
        val kp = kps(
            Coco17.NOSE to Keypoint2D(0.6f, 0.15f, 1f), // facing +x
            Coco17.LEFT_SHOULDER to Keypoint2D(0.45f, 0.2f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.55f, 0.2f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.45f, 0.6f, 1f),
            Coco17.RIGHT_HIP to Keypoint2D(0.55f, 0.6f, 1f)
        )
        assertEquals(0f, AngleCalculations2D.torsoLean(kp, 1f)!!, 0.1f)
    }

    @Test
    fun torsoLeanForwardIsPositive() {
        // shoulder-mid (0.6, 0.2), hip-mid (0.5, 0.6), facing +x → atan2(0.1, 0.4) = +14.04°
        val kp = kps(
            Coco17.NOSE to Keypoint2D(0.65f, 0.15f, 1f),
            Coco17.LEFT_SHOULDER to Keypoint2D(0.55f, 0.2f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.65f, 0.2f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.45f, 0.6f, 1f),
            Coco17.RIGHT_HIP to Keypoint2D(0.55f, 0.6f, 1f)
        )
        assertEquals(14.04f, AngleCalculations2D.torsoLean(kp, 1f)!!, 0.5f)
    }

    @Test
    fun torsoLeanSignIsFacingIndependent() {
        // L-04: the SAME forward lean, mirrored (player faces -x): shoulder-mid
        // (0.4, 0.2), hip-mid (0.5, 0.6), nose toward -x → still +14.04°, not -14.04°.
        val kp = kps(
            Coco17.NOSE to Keypoint2D(0.35f, 0.15f, 1f),
            Coco17.LEFT_SHOULDER to Keypoint2D(0.35f, 0.2f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.45f, 0.2f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.45f, 0.6f, 1f),
            Coco17.RIGHT_HIP to Keypoint2D(0.55f, 0.6f, 1f)
        )
        assertEquals(14.04f, AngleCalculations2D.torsoLean(kp, 1f)!!, 0.5f)
    }

    @Test
    fun torsoLeanNullWhenFacingIndeterminate() {
        // Nose dead-centered over the hips and ears at the same x → cannot orient;
        // a possibly-flipped sign must not be reported (trust rule).
        val kp = kps(
            Coco17.LEFT_SHOULDER to Keypoint2D(0.45f, 0.2f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.55f, 0.2f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.45f, 0.6f, 1f),
            Coco17.RIGHT_HIP to Keypoint2D(0.55f, 0.6f, 1f)
            // nose/ears stay at the kps() default x = 0.5 = hip-mid x
        )
        assertNull(AngleCalculations2D.torsoLean(kp, 1f))
    }

    @Test
    fun shoulderTiltLevelIsZeroAndFoldsToHalfPlane() {
        val level = kps(
            Coco17.LEFT_SHOULDER to Keypoint2D(0.4f, 0.5f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.6f, 0.5f, 1f)
        )
        assertEquals(0f, AngleCalculations2D.shoulderTilt(level, 1f)!!, 0.1f)

        // left→right (0.2, 0.1) → 26.57°
        val tilted = kps(
            Coco17.LEFT_SHOULDER to Keypoint2D(0.4f, 0.5f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.6f, 0.6f, 1f)
        )
        assertEquals(26.57f, AngleCalculations2D.shoulderTilt(tilted, 1f)!!, 0.5f)

        // reversed direction (player facing other way): left→right (-0.2, -0.1) → raw -153.43° → folds to 26.57°
        val reversed = kps(
            Coco17.LEFT_SHOULDER to Keypoint2D(0.6f, 0.5f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.4f, 0.4f, 1f)
        )
        assertEquals(26.57f, AngleCalculations2D.shoulderTilt(reversed, 1f)!!, 0.5f)
    }
}
