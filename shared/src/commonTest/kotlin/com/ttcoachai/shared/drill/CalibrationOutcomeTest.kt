package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Topology
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CalibrationOutcomeTest {

    /**
     * Helper: create a rep with the same structure as DrillCalibratorTest.
     * One rep = 4 still frames + 7 swing frames.
     */
    private fun repFrames(
        startIndex: Int,
        wristYAtPeak: Float,
        shoulderSepX: Float = 0.02f,
        hipScore: Float = 1f
    ): List<PoseFrame2D> {
        val wristXs = listOf(0.50f, 0.50f, 0.50f, 0.50f, 0.51f, 0.53f, 0.57f, 0.63f, 0.68f, 0.71f, 0.72f)
        return wristXs.mapIndexed { i, wx ->
            val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
            kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.44f + shoulderSepX / 2, 0.30f, 1f)
            kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.44f - shoulderSepX / 2, 0.30f, 1f)
            kp[Coco17.RIGHT_ELBOW] = Keypoint2D(0.50f, 0.42f, 1f)
            kp[Coco17.RIGHT_WRIST] = Keypoint2D(wx, wristYAtPeak, 1f)
            kp[Coco17.RIGHT_HIP] = Keypoint2D(0.45f, 0.55f, hipScore)
            kp[Coco17.LEFT_HIP] = Keypoint2D(0.43f, 0.55f, hipScore)
            kp[Coco17.RIGHT_KNEE] = Keypoint2D(0.46f, 0.72f, 1f)
            kp[Coco17.RIGHT_ANKLE] = Keypoint2D(0.48f, 0.90f, 1f)
            PoseFrame2D(startIndex + i, (startIndex + i) * 100L, kp)
        }
    }

    private fun sequenceOf(
        wristYs: List<Float>,
        shoulderSeps: List<Float> = List(wristYs.size) { 0.02f },
        hipScores: List<Float> = List(wristYs.size) { 1f }
    ): PoseSequence2D {
        val frames = mutableListOf<PoseFrame2D>()
        wristYs.forEachIndexed { i, y -> frames += repFrames(frames.size, y, shoulderSeps[i], hipScores[i]) }
        return PoseSequence2D(
            topology = Topology.COCO17, model = "synthetic", videoName = "synthetic.mp4",
            intervalMs = 100L, totalFrames = frames.size, videoDurationMs = frames.size * 100L,
            videoWidth = 1000, videoHeight = 1000, frames = frames
        )
    }

    @Test
    fun calibrateCheckedSuccessOnValidStrokes() {
        // Happy path: 5 near-identical reps, passes all gates
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val outcome = DrillCalibrator.calibrateChecked(
            sequence = seq,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 3
        )
        assertIs<CalibrationOutcome.Success>(outcome)
        val baseline = outcome.baseline
        assertEquals("forehand_drive", baseline.drillType)
        assertEquals("right", baseline.drillerHandedness)
        assertTrue(baseline.repCount >= 3, "baseline must have at least 3 reps")
        assertTrue(baseline.qualityScore > 0, "quality score must be positive")
    }

    @Test
    fun calibrateCheckedPlacementErrorOnHighYaw() {
        // Camera placement gate: explicit cameraYawDeg override beyond 30°
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val outcome = DrillCalibrator.calibrateChecked(
            sequence = seq,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 3,
            cameraYawDeg = 45f
        )
        assertIs<CalibrationOutcome.PlacementError>(outcome)
        assertContains(outcome.message.lowercase(), "camera")
        assertContains(outcome.message.lowercase(), "reposition")
    }

    @Test
    fun calibrateCheckedPlacementErrorOnUnmeasurableYaw() {
        // Unmeasurable yaw (no scored hips in pre-stroke or stroke window)
        // counted toward camera placement exception when it drops below minRepCount
        val seq = sequenceOf(
            wristYs = listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f, 0.40f),
            hipScores = listOf(1f, 1f, 1f, 0.1f, 0.1f, 0.1f)
        )
        val outcome = DrillCalibrator.calibrateChecked(
            sequence = seq,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 6 // tight threshold so placement gate fires
        )
        assertIs<CalibrationOutcome.PlacementError>(outcome)
        assertContains(outcome.message.lowercase(), "reposition")
    }

    @Test
    fun calibrateCheckedFailedOnInsufficientReps() {
        // Too few reps from the start (not filtered by placement gate)
        val seq = sequenceOf(listOf(0.40f, 0.401f)) // Only 2 reps
        val outcome = DrillCalibrator.calibrateChecked(
            sequence = seq,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 3
        )
        assertIs<CalibrationOutcome.Failed>(outcome)
        assertContains(outcome.message, "Calibration failed")
    }

    @Test
    fun successOutcomeExportsToSwift() {
        // Verify the Success variant is properly structured for Swift interop
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val outcome = DrillCalibrator.calibrateChecked(
            sequence = seq,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 3
        )
        assertIs<CalibrationOutcome.Success>(outcome)
    }

    @Test
    fun placementErrorOutcomeExportsToSwift() {
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val outcome = DrillCalibrator.calibrateChecked(
            sequence = seq,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 3,
            cameraYawDeg = 45f
        )
        assertIs<CalibrationOutcome.PlacementError>(outcome)
        assertTrue(outcome.message.isNotEmpty())
    }

    @Test
    fun failedOutcomeExportsToSwift() {
        val seq = sequenceOf(listOf(0.40f, 0.401f))
        val outcome = DrillCalibrator.calibrateChecked(
            sequence = seq,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 3
        )
        assertIs<CalibrationOutcome.Failed>(outcome)
        assertTrue(outcome.message.isNotEmpty())
    }

    @Test
    fun checkedFunctionDoesNotThrow() {
        // Core requirement: calibrateChecked() never throws, always returns an outcome
        val badSeq = sequenceOf(listOf(0.40f))
        // This should not throw; we're testing that the contract is satisfied
        val outcome = DrillCalibrator.calibrateChecked(
            sequence = badSeq,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 10
        )
        // If we got here without an exception, the contract is satisfied.
        // Verify it's a valid outcome state.
        when (outcome) {
            is CalibrationOutcome.Success -> assertTrue(true)
            is CalibrationOutcome.PlacementError -> assertTrue(true)
            is CalibrationOutcome.Failed -> assertTrue(true)
        }
    }

    @Test
    fun messageFieldsNeverNull() {
        // PlacementError and Failed both carry a String message;
        // even on edge-case exceptions, we must have a non-null fallback
        val seq = sequenceOf(
            wristYs = listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f, 0.40f),
            hipScores = listOf(1f, 1f, 1f, 0.1f, 0.1f, 0.1f)
        )
        val outcome = DrillCalibrator.calibrateChecked(
            sequence = seq,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 6
        )
        if (outcome is CalibrationOutcome.PlacementError) {
            assertTrue(outcome.message.isNotEmpty(), "PlacementError message must be non-empty")
        }
        if (outcome is CalibrationOutcome.Failed) {
            assertTrue(outcome.message.isNotEmpty(), "Failed message must be non-empty")
        }
    }
}
