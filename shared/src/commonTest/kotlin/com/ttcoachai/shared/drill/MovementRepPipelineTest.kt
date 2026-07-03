package com.ttcoachai.shared.drill

import com.ttcoachai.shared.detection.DetectionConfig
import com.ttcoachai.shared.detection.MovementDetector
import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Topology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovementRepPipelineTest {

    // Same fixture shape as StrokeDetector2DTest/DrillCalibratorTest: only the right
    // wrist x moves; shoulders/hips fixed so torso length and yaw are well-defined.
    private fun repFrames(startIndex: Int): List<PoseFrame2D> {
        val wristXs = listOf(0.50f, 0.50f, 0.50f, 0.50f, 0.51f, 0.53f, 0.57f, 0.63f, 0.68f, 0.71f, 0.72f)
        return wristXs.mapIndexed { i, wx ->
            val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
            kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.49f, 0.30f, 1f)
            kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.51f, 0.30f, 1f)
            kp[Coco17.LEFT_HIP] = Keypoint2D(0.49f, 0.55f, 1f)
            kp[Coco17.RIGHT_HIP] = Keypoint2D(0.51f, 0.55f, 1f)
            kp[Coco17.RIGHT_WRIST] = Keypoint2D(wx, 0.5f, 1f)
            PoseFrame2D(startIndex + i, (startIndex + i) * 100L, kp)
        }
    }

    /** [reps] forward strokes, each identical, separated by a still gap. */
    private fun sequenceOf(reps: Int): PoseSequence2D {
        val frames = mutableListOf<PoseFrame2D>()
        repeat(reps) { frames += repFrames(frames.size) }
        return PoseSequence2D(
            topology = Topology.COCO17, model = "synthetic", videoName = "synthetic.mp4",
            intervalMs = 100L, totalFrames = frames.size, videoDurationMs = frames.size * 100L,
            videoWidth = 1000, videoHeight = 1000, frames = frames
        )
    }

    /** Locomotion: hip-mid also translates across the stroke window (walking). */
    private fun walkingSequence(): PoseSequence2D {
        val wristXs = listOf(0.50f, 0.50f, 0.50f, 0.50f, 0.51f, 0.53f, 0.57f, 0.63f, 0.68f, 0.71f, 0.72f)
        val frames = wristXs.mapIndexed { i, wx ->
            val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
            val hipShift = i * 0.05f // large horizontal travel vs torso length (~0.25)
            kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.49f + hipShift, 0.30f, 1f)
            kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.51f + hipShift, 0.30f, 1f)
            kp[Coco17.LEFT_HIP] = Keypoint2D(0.49f + hipShift, 0.55f, 1f)
            kp[Coco17.RIGHT_HIP] = Keypoint2D(0.51f + hipShift, 0.55f, 1f)
            kp[Coco17.RIGHT_WRIST] = Keypoint2D(wx, 0.5f, 1f)
            PoseFrame2D(i, i * 100L, kp)
        }
        return PoseSequence2D(
            topology = Topology.COCO17, model = "synthetic", videoName = "synthetic.mp4",
            intervalMs = 100L, totalFrames = frames.size, videoDurationMs = frames.size * 100L,
            videoWidth = 1000, videoHeight = 1000, frames = frames
        )
    }

    private val definition = MovementDefinition(
        id = "test_movement",
        metrics = CoreMetricSpecs.ALL,
        messages = CoreMessageTemplates.TEMPLATES
    )

    @Test
    fun defaultConfigMatchesManualChainResult() {
        val seq = sequenceOf(3)
        val manual = LocomotionFilter.filterStationary(
            RepFilter.filter(
                ForwardStrokeFilter.filter(
                    MovementDetector(definition.detection).detect(
                        seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs
                    ),
                    seq.frames, Handedness.RIGHT
                )
            ),
            seq.frames, seq.aspectRatio, definition.repValidation.hipTravelMaxTorso
        )
        val pipeline = MovementRepPipeline(definition)
        val piped = pipeline.detectReps(seq, Handedness.RIGHT)
        assertEquals(manual, piped)
        assertTrue(piped.isNotEmpty(), "fixture must actually detect reps")
    }

    @Test
    fun directionGateDisabledSkipsForwardStrokeFilter() {
        val seq = sequenceOf(3)
        val detected = MovementDetector(definition.detection)
            .detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)
        val withoutGateExpected = LocomotionFilter.filterStationary(
            RepFilter.filter(detected),
            seq.frames, seq.aspectRatio, definition.repValidation.hipTravelMaxTorso
        )
        val def = definition.copy(repValidation = definition.repValidation.copy(directionGate = false))
        val piped = MovementRepPipeline(def).detectReps(seq, Handedness.RIGHT)
        assertEquals(withoutGateExpected, piped)
    }

    @Test
    fun bandingDisabledSkipsRepFilter() {
        val seq = sequenceOf(3)
        val detected = MovementDetector(definition.detection)
            .detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)
        val withoutBandingExpected = LocomotionFilter.filterStationary(
            ForwardStrokeFilter.filter(detected, seq.frames, Handedness.RIGHT),
            seq.frames, seq.aspectRatio, definition.repValidation.hipTravelMaxTorso
        )
        val def = definition.copy(repValidation = definition.repValidation.copy(banding = false))
        val piped = MovementRepPipeline(def).detectReps(seq, Handedness.RIGHT)
        assertEquals(withoutBandingExpected, piped)
    }

    @Test
    fun hipTravelMaxTorsoDisablesLocomotionGate() {
        val seq = walkingSequence()
        val def = definition.copy(repValidation = definition.repValidation.copy(hipTravelMaxTorso = 0f))
        val gated = MovementRepPipeline(definition).detectReps(seq, Handedness.RIGHT)
        val ungated = MovementRepPipeline(def).detectReps(seq, Handedness.RIGHT)
        assertTrue(gated.isEmpty(), "walking stroke must be gated out by default: $gated")
        assertTrue(ungated.isNotEmpty(), "disabling the gate (<=0) must let the walking stroke through")
    }
}
