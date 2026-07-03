package com.ttcoachai.shared.drill.movements

import com.ttcoachai.shared.drill.CoreMessageTemplates
import com.ttcoachai.shared.drill.CoreMetricSpecs
import com.ttcoachai.shared.drill.LocomotionFilter
import com.ttcoachai.shared.drill.MovementRepPipeline
import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Topology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * ForehandDriveGeneral.DEFINITION is the movement-tolerant general-practice
 * profile (docs/superpowers/specs/2026-07-02-generic-movement-pipeline-design.md):
 * identical detection tuning, metrics and messages to ForehandDrive, direction
 * gate and banding both stay on, and only the locomotion tolerance widens from
 * 0.4 to 0.8 torso-lengths so between-shot steps during free practice don't
 * silence feedback.
 */
class ForehandDriveGeneralTest {

    @Test
    fun idIsForehandDriveGeneral() {
        assertEquals("forehand_drive_general", ForehandDriveGeneral.DEFINITION.id)
    }

    @Test
    fun repValidationWidensLocomotionGateButKeepsOtherStagesOn() {
        val rv = ForehandDriveGeneral.DEFINITION.repValidation
        assertEquals(0.8f, rv.hipTravelMaxTorso)
        assertTrue(rv.directionGate, "direction gate must stay on for general practice")
        assertTrue(rv.banding, "banding must stay on for general practice")
    }

    @Test
    fun detectionTuningMatchesForehandDrive() {
        assertEquals(ForehandDrive.DEFINITION.detection, ForehandDriveGeneral.DEFINITION.detection)
    }

    @Test
    fun metricsAndMessagesAreTheSharedCoreInstances() {
        assertSame(CoreMetricSpecs.ALL, ForehandDriveGeneral.DEFINITION.metrics)
        assertSame(CoreMessageTemplates.TEMPLATES, ForehandDriveGeneral.DEFINITION.messages)
    }

    // Mirrors MovementRepPipelineTest.walkingSequence(), but with a smaller
    // hipShift step (0.03 instead of 0.05) so the stroke's hip-mid travel lands
    // between the two thresholds under test: gated out by ForehandDrive's
    // default 0.4 torso-length locomotion gate, but kept by
    // ForehandDriveGeneral's widened 0.8. Torso length is fixed at 0.25
    // (shoulder-mid to hip-mid vertical distance); the detected stroke window
    // is frames [5..10] (verified against MovementDetector's smoothing/peak/
    // boundary logic), so travel = (10-5) * hipShift(0.03) / 0.25 = 0.15/0.25
    // = 0.6 torso-lengths — inside [0.4, 0.8].
    private fun lightSteppingSequence(): PoseSequence2D {
        val wristXs = listOf(0.50f, 0.50f, 0.50f, 0.50f, 0.51f, 0.53f, 0.57f, 0.63f, 0.68f, 0.71f, 0.72f)
        val hipShiftStep = 0.03f
        val frames = wristXs.mapIndexed { i, wx ->
            val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
            val hipShift = i * hipShiftStep
            // Nose travels WITH the torso, ahead of shoulder-mid (+x): otherwise the
            // stepping shoulder-mid crosses the default-filled nose (x=0.5) mid-stroke
            // and ForwardStrokeFilter's head-facing fallback reads the stroke as a
            // recovery swing, dropping it before the locomotion gate is ever tested.
            kp[Coco17.NOSE] = Keypoint2D(0.56f + hipShift, 0.28f, 1f)
            kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.43f + hipShift, 0.30f, 1f)
            kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.45f + hipShift, 0.30f, 1f)
            kp[Coco17.LEFT_HIP] = Keypoint2D(0.43f + hipShift, 0.55f, 1f)
            kp[Coco17.RIGHT_HIP] = Keypoint2D(0.45f + hipShift, 0.55f, 1f)
            kp[Coco17.RIGHT_WRIST] = Keypoint2D(wx, 0.5f, 1f)
            PoseFrame2D(i, i * 100L, kp)
        }
        return PoseSequence2D(
            topology = Topology.COCO17, model = "synthetic", videoName = "synthetic.mp4",
            intervalMs = 100L, totalFrames = frames.size, videoDurationMs = frames.size * 100L,
            videoWidth = 1000, videoHeight = 1000, frames = frames
        )
    }

    @Test
    fun lightSteppingTravelRatioIsSixTenths() {
        val seq = lightSteppingSequence()
        val detected = com.ttcoachai.shared.detection.MovementDetector(ForehandDrive.DEFINITION.detection)
            .detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)
        val forward = com.ttcoachai.shared.drill.ForwardStrokeFilter.filter(detected, seq.frames, Handedness.RIGHT)
        val banded = com.ttcoachai.shared.drill.RepFilter.filter(forward)
        assertEquals(1, banded.size, "fixture must produce exactly one candidate stroke: $banded")
        val ratio = LocomotionFilter.hipMidTravelTorso(seq.frames, banded[0], seq.aspectRatio)
        // ~0.6 by construction (5 frames × 0.03 hipShift / 0.25 torso); float arithmetic
        // forbids exact equality. What matters is sitting strictly between the strict
        // (0.4) and tolerant (0.8) gates.
        assertTrue(ratio != null && ratio > 0.55f && ratio < 0.65f, "travel ratio ~0.6 expected, got $ratio")
    }

    @Test
    fun forehandDriveGatesOutLightSteppingButGeneralKeepsIt() {
        val seq = lightSteppingSequence()
        val strict = MovementRepPipeline(ForehandDrive.DEFINITION).detectReps(seq, Handedness.RIGHT)
        val tolerant = MovementRepPipeline(ForehandDriveGeneral.DEFINITION).detectReps(seq, Handedness.RIGHT)
        assertTrue(strict.isEmpty(), "ForehandDrive's 0.4 torso gate must drop the lightly-stepping stroke: $strict")
        assertTrue(tolerant.isNotEmpty(), "ForehandDriveGeneral's 0.8 torso gate must keep the same stroke: $tolerant")
    }
}
