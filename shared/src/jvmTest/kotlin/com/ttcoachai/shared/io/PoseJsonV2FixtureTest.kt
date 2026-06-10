package com.ttcoachai.shared.io

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.models.Topology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoseJsonV2FixtureTest {

    @Test
    fun andriiFixtureParsesCleanly() {
        val seq = TestFixturesV2.loadAndriiRtm()
        assertEquals(Topology.COCO17, seq.topology)
        // L-02 guard: fixtures must be full-fps re-exports (≥20 fps), not the old 10 fps ones
        assertTrue(seq.intervalMs in 1..50, "fixture is ${seq.intervalMs}ms/frame — re-export at native fps (L-02)")
        assertEquals(seq.totalFrames, seq.frames.size)
        assertTrue(seq.frames.isNotEmpty())
        seq.frames.forEach { f ->
            assertTrue(f.keypoints.size == 17 || f.keypoints.isEmpty(),
                "frame ${f.frameIndex}: ${f.keypoints.size} keypoints")
            f.keypoints.forEach { kp ->
                assertTrue(kp.x in 0f..1f && kp.y in 0f..1f, "frame ${f.frameIndex}: coords out of [0,1]")
                assertTrue(kp.score in 0f..1f, "frame ${f.frameIndex}: score out of [0,1]")
            }
        }
    }

    @Test
    fun video2FixtureParsesCleanly() {
        val seq = TestFixturesV2.loadVideo2Rtm()
        assertEquals(Topology.COCO17, seq.topology)
        assertTrue(seq.intervalMs in 1..50, "fixture is ${seq.intervalMs}ms/frame — re-export at native fps (L-02)")
        assertEquals(712, seq.videoWidth)
        assertEquals(1280, seq.videoHeight)
        assertEquals(seq.totalFrames, seq.frames.size)
    }
}
