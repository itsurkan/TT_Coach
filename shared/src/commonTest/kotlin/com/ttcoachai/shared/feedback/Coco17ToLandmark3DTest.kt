package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.Keypoint2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies [Coco17ToLandmark3D] against the mapping table justified in the class KDoc:
 * every MediaPipe-33 index actually read by [SnapshotGeometry] / PoseSnapshotView is
 * covered, unmapped slots stay invisible, and score becomes visibility unchanged.
 */
class Coco17ToLandmark3DTest {

    /** 17 distinct keypoints so each mapped index is independently identifiable by x. */
    private fun distinctCoco17(): List<Keypoint2D> =
        (0 until 17).map { i -> Keypoint2D(x = i.toFloat(), y = i * 10f, score = i / 16f) }

    @Test
    fun outputIsAlwaysThirtyThreeLong() {
        assertEquals(33, Coco17ToLandmark3D.map(distinctCoco17()).size)
        assertEquals(33, Coco17ToLandmark3D.map(emptyList()).size)
        assertEquals(33, Coco17ToLandmark3D.map(listOf(Keypoint2D(0f, 0f, 1f))).size)
    }

    @Test
    fun mappedIndicesCopyXYAndScoreToVisibilityWithZeroZ() {
        val coco = distinctCoco17()
        val out = Coco17ToLandmark3D.map(coco)

        val expected = mapOf(
            0 to 0,   // nose
            1 to 2,   // left eye -> MP left eye (center)
            2 to 5,   // right eye -> MP right eye (center)
            3 to 7,   // left ear
            4 to 8,   // right ear
            5 to 11,  // left shoulder
            6 to 12,  // right shoulder
            7 to 13,  // left elbow
            8 to 14,  // right elbow
            9 to 15,  // left wrist
            10 to 16, // right wrist
            11 to 23, // left hip
            12 to 24, // right hip
            13 to 25, // left knee
            14 to 26, // right knee
            15 to 27, // left ankle
            16 to 28  // right ankle
        )

        for ((cocoIdx, mpIdx) in expected) {
            val src = coco[cocoIdx]
            val dst = out[mpIdx]
            assertEquals(src.x, dst.x, "x mismatch for coco $cocoIdx -> mp $mpIdx")
            assertEquals(src.y, dst.y, "y mismatch for coco $cocoIdx -> mp $mpIdx")
            assertEquals(src.score, dst.visibility, "score->visibility mismatch for coco $cocoIdx -> mp $mpIdx")
            assertEquals(0f, dst.z, "z must always be 0f (RTM is 2D)")
        }
    }

    @Test
    fun unmappedSlotsAreInvisible() {
        val out = Coco17ToLandmark3D.map(distinctCoco17())
        // Face detail (1,3,4,6,9,10), fingers (17-22), heel/foot-index (29-32) have no
        // COCO-17 source.
        val unmapped = listOf(1, 3, 4, 6, 9, 10) + (17..22) + (29..32)
        for (idx in unmapped) {
            assertTrue(out[idx].visibility == 0f, "slot $idx must be invisible (no COCO-17 source)")
        }
    }

    @Test
    fun shortOrEmptyInputDoesNotCrashAndLeavesUnfilledSlotsInvisible() {
        val out = Coco17ToLandmark3D.map(listOf(Keypoint2D(0.1f, 0.2f, 0.9f))) // only nose present
        assertEquals(33, out.size)
        assertEquals(0.1f, out[0].x)
        assertEquals(0.9f, out[0].visibility)
        assertTrue(out[11].visibility == 0f, "left shoulder must be invisible when input is too short")
    }
}
