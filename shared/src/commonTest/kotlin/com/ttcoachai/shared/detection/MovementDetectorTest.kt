package com.ttcoachai.shared.detection

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovementDetectorTest {

    /**
     * Frames where only the given wrist/ankle x moves; shoulders/hips fixed with
     * torso length 0.25, so speeds are well-defined in torso-lengths/sec.
     */
    private fun framesFromMovingKeypoint(
        movingIndex: Int,
        xs: List<Float>,
        intervalMs: Long = 100L
    ): List<PoseFrame2D> =
        xs.mapIndexed { i, wx ->
            val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
            kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.49f, 0.30f, 1f)
            kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.51f, 0.30f, 1f)
            kp[Coco17.LEFT_HIP] = Keypoint2D(0.49f, 0.55f, 1f)
            kp[Coco17.RIGHT_HIP] = Keypoint2D(0.51f, 0.55f, 1f)
            kp[movingIndex] = Keypoint2D(wx, 0.5f, 1f)
            PoseFrame2D(frameIndex = i, timestampMs = i * intervalMs, keypoints = kp)
        }

    // still — accelerate to peak — decelerate — still
    private val singleStrokeXs = listOf(
        0.50f, 0.50f, 0.50f, 0.50f,
        0.51f, 0.53f, 0.57f, 0.63f, 0.68f, 0.71f, 0.72f,
        0.72f, 0.72f, 0.72f, 0.72f
    )

    // ---- (a) representative StrokeDetector2D-equivalent test, ported to MovementDetector ----

    @Test
    fun detectsSingleStrokeWithDefaultConfig() {
        val frames = framesFromMovingKeypoint(Coco17.RIGHT_WRIST, singleStrokeXs)
        val strokes = MovementDetector().detect(frames, Handedness.RIGHT, 1f, 100L)
        assertEquals(1, strokes.size)
        val s = strokes[0]
        // raw speed peaks at frame 7 (0.057→0.063 = 0.06 → 2.4 torso/s); smoothing may shift ±1
        assertTrue(s.peakFrame in 6..8, "peak at ${s.peakFrame}")
        assertTrue(s.startFrame < s.peakFrame, "start ${s.startFrame} before peak")
        assertTrue(s.endFrame > s.peakFrame, "end ${s.endFrame} after peak")
        assertEquals(0, s.strokeIndex)
    }

    // ---- (b) signalKeypoint selection: ankle burst detected only when configured to track the ankle ----

    @Test
    fun ankleSignalKeypointDetectsAnkleBurstWristStill() {
        // Wrist stays fixed at 0.5 the whole sequence; only the right ankle moves
        // in a stroke-like burst.
        val ankleFrames = framesFromMovingKeypoint(Coco17.RIGHT_ANKLE, singleStrokeXs)

        val ankleConfig = DetectionConfig(signalKeypoint = SignalKeypoint.DOMINANT_ANKLE)
        val ankleStrokes = MovementDetector(ankleConfig).detect(ankleFrames, Handedness.RIGHT, 1f, 100L)
        assertEquals(1, ankleStrokes.size, "ankle-tracking config must detect the ankle burst")

        val wristStrokes = MovementDetector(DetectionConfig()).detect(ankleFrames, Handedness.RIGHT, 1f, 100L)
        assertEquals(0, wristStrokes.size, "default wrist-tracking config must see a still wrist")
    }

    // ---- (c) SignalKeypoint.index mapping for both handedness values ----

    @Test
    fun signalKeypointIndexResolvesDominantSideForRightHanded() {
        assertEquals(Coco17.RIGHT_WRIST, SignalKeypoint.DOMINANT_WRIST.index(Handedness.RIGHT))
        assertEquals(Coco17.LEFT_WRIST, SignalKeypoint.NON_DOMINANT_WRIST.index(Handedness.RIGHT))
        assertEquals(Coco17.RIGHT_ELBOW, SignalKeypoint.DOMINANT_ELBOW.index(Handedness.RIGHT))
        assertEquals(Coco17.LEFT_ELBOW, SignalKeypoint.NON_DOMINANT_ELBOW.index(Handedness.RIGHT))
        assertEquals(Coco17.RIGHT_ANKLE, SignalKeypoint.DOMINANT_ANKLE.index(Handedness.RIGHT))
        assertEquals(Coco17.LEFT_ANKLE, SignalKeypoint.NON_DOMINANT_ANKLE.index(Handedness.RIGHT))
    }

    @Test
    fun signalKeypointIndexResolvesDominantSideForLeftHanded() {
        assertEquals(Coco17.LEFT_WRIST, SignalKeypoint.DOMINANT_WRIST.index(Handedness.LEFT))
        assertEquals(Coco17.RIGHT_WRIST, SignalKeypoint.NON_DOMINANT_WRIST.index(Handedness.LEFT))
        assertEquals(Coco17.LEFT_ELBOW, SignalKeypoint.DOMINANT_ELBOW.index(Handedness.LEFT))
        assertEquals(Coco17.RIGHT_ELBOW, SignalKeypoint.NON_DOMINANT_ELBOW.index(Handedness.LEFT))
        assertEquals(Coco17.LEFT_ANKLE, SignalKeypoint.DOMINANT_ANKLE.index(Handedness.LEFT))
        assertEquals(Coco17.RIGHT_ANKLE, SignalKeypoint.NON_DOMINANT_ANKLE.index(Handedness.LEFT))
    }
}
