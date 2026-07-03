package com.ttcoachai.pose

import com.ttcoachai.pose.Coco17OverlayScaling.OverlayPoint
import com.ttcoachai.pose.Coco17OverlayScaling.project
import com.ttcoachai.pose.Coco17OverlayScaling.BONES
import com.ttcoachai.pose.Coco17OverlayScaling.SCORE_THRESHOLD
import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Keypoint2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Coco17OverlayScalingTest {
    @Test
    fun projectCenterPoint() {
        val kp = Keypoint2D(0.5f, 0.5f, 0.9f)
        val result = project(kp, 200, 100)
        assertNotNull(result)
        assertEquals(100f, result!!.x, 0.001f)
        assertEquals(50f, result.y, 0.001f)
    }

    @Test
    fun projectOrigin() {
        val kp = Keypoint2D(0f, 0f, 0.9f)
        val result = project(kp, 200, 100)
        assertNotNull(result)
        assertEquals(0f, result!!.x, 0.001f)
        assertEquals(0f, result.y, 0.001f)
    }

    @Test
    fun projectCorner() {
        val kp = Keypoint2D(1f, 1f, 0.9f)
        val result = project(kp, 200, 100)
        assertNotNull(result)
        assertEquals(200f, result!!.x, 0.001f)
        assertEquals(100f, result.y, 0.001f)
    }

    @Test
    fun scoreGatedBelow() {
        val kp = Keypoint2D(0.5f, 0.5f, 0.2f)
        val result = project(kp, 200, 100)
        assertNull(result)
    }

    @Test
    fun scoreAtThreshold() {
        val kp = Keypoint2D(0.5f, 0.5f, SCORE_THRESHOLD)
        val result = project(kp, 200, 100)
        assertNotNull(result)
    }

    @Test
    fun bonesNonEmpty() {
        assertTrue("BONES list should not be empty", BONES.isNotEmpty())
    }

    @Test
    fun bonesValidIndices() {
        for ((a, b) in BONES) {
            assertTrue("Bone endpoint $a out of range [0..16]", a in 0..16)
            assertTrue("Bone endpoint $b out of range [0..16]", b in 0..16)
        }
    }

    @Test
    fun bonesNoSelfEdges() {
        for ((a, b) in BONES) {
            assertTrue("Self-edge detected: $a-$b", a != b)
        }
    }

    @Test
    fun bonesContainsExpectedEdges() {
        val bonesSet = BONES.toSet()
        val leftShoulderElbow = Coco17.LEFT_SHOULDER to Coco17.LEFT_ELBOW
        val rightShoulderElbow = Coco17.RIGHT_SHOULDER to Coco17.RIGHT_ELBOW
        assertTrue("Missing edge LEFT_SHOULDER–LEFT_ELBOW", bonesSet.contains(leftShoulderElbow))
        assertTrue("Missing edge RIGHT_SHOULDER–RIGHT_ELBOW", bonesSet.contains(rightShoulderElbow))
    }
}
