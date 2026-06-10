package com.ttcoachai.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Pose2DModelsTest {

    @Test
    fun aspectRatioIsWidthOverHeight() {
        val seq = PoseSequence2D(
            topology = Topology.COCO17,
            model = "rtmpose-m",
            videoName = "clip.mp4",
            intervalMs = 100L,
            totalFrames = 0,
            videoDurationMs = 0L,
            videoWidth = 712,
            videoHeight = 1280,
            frames = emptyList()
        )
        assertEquals(712f / 1280f, seq.aspectRatio, 1e-6f)
    }

    @Test
    fun coco17HandednessAccessors() {
        assertEquals(Coco17.RIGHT_WRIST, Coco17.wrist(Handedness.RIGHT))
        assertEquals(Coco17.LEFT_WRIST, Coco17.wrist(Handedness.LEFT))
        assertEquals(Coco17.RIGHT_ELBOW, Coco17.elbow(Handedness.RIGHT))
        assertEquals(Coco17.LEFT_SHOULDER, Coco17.shoulder(Handedness.LEFT))
        assertEquals(Coco17.RIGHT_HIP, Coco17.hip(Handedness.RIGHT))
        assertEquals(Coco17.LEFT_KNEE, Coco17.knee(Handedness.LEFT))
        assertEquals(Coco17.RIGHT_ANKLE, Coco17.ankle(Handedness.RIGHT))
    }

    @Test
    fun topologyFromJsonName() {
        assertEquals(Topology.COCO17, Topology.fromJsonName("coco17"))
        assertEquals(Topology.HALPE26, Topology.fromJsonName("halpe26"))
        assertEquals(null, Topology.fromJsonName("mediapipe33"))
        assertEquals(17, Topology.COCO17.keypointCount)
        assertEquals(26, Topology.HALPE26.keypointCount)
    }

    @Test
    fun viewGeometryXScaleCombinesAspectAndCameraYaw() {
        assertEquals(0.5f, ViewGeometry(aspectRatio = 0.5f).xScale, 1e-6f)
        // 1/cos(60°) = 2 → xScale doubles
        assertEquals(1.0f, ViewGeometry(aspectRatio = 0.5f, cameraYawDeg = 60f).xScale, 1e-4f)
        // sign-independent (cos is even)
        assertEquals(
            ViewGeometry(1f, cameraYawDeg = 30f).xScale,
            ViewGeometry(1f, cameraYawDeg = -30f).xScale,
            1e-6f
        )
        assertFailsWith<IllegalArgumentException> { ViewGeometry(1f, cameraYawDeg = 75f) }
    }
}
