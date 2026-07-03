package com.ttcoachai.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure YOLOX postprocess ([YoloxDetector.parseDets]). Real ORT inference is
 * exercised on-device later (T7), not here. 1:1 Kotlin port of
 * `iosApp/TTCoach/Pose/YoloxDetectorTests.swift` (parseDets cases only — the letterbox/sampler
 * cases already have Kotlin coverage in `BitmapSamplerTest`).
 */
class YoloxDetectorTest {

    @Test
    fun `parseDets divides coords by ratio`() {
        // One box, ratio 0.5 -> coords double, score passes.
        val flat = floatArrayOf(100f, 50f, 300f, 450f, 0.9f)
        val boxes = YoloxDetector.parseDets(detsFlat = flat, boxCount = 1, ratio = 0.5f)
        assertEquals(1, boxes.size)
        assertEquals(BoundingBox(x1 = 200f, y1 = 100f, x2 = 600f, y2 = 900f, score = 0.9f), boxes[0])
    }

    @Test
    fun `parseDets ratio one`() {
        val flat = floatArrayOf(10f, 20f, 30f, 40f, 0.5f)
        val boxes = YoloxDetector.parseDets(detsFlat = flat, boxCount = 1, ratio = 1.0f)
        assertEquals(listOf(BoundingBox(x1 = 10f, y1 = 20f, x2 = 30f, y2 = 40f, score = 0.5f)), boxes)
    }

    @Test
    fun `parseDets score threshold boundary`() {
        // Exactly 0.3 is EXCLUDED (rtmlib: score > 0.3); 0.31 is kept.
        val flat = floatArrayOf(
            0f, 0f, 1f, 1f, 0.30f,
            0f, 0f, 1f, 1f, 0.31f,
            0f, 0f, 1f, 1f, 0.299f,
        )
        val boxes = YoloxDetector.parseDets(detsFlat = flat, boxCount = 3, ratio = 1.0f)
        assertEquals(1, boxes.size)
        assertEquals(0.31f, boxes[0].score, 1e-6f)
    }

    @Test
    fun `parseDets empty when zero boxes`() {
        assertEquals(emptyList<BoundingBox>(), YoloxDetector.parseDets(detsFlat = floatArrayOf(), boxCount = 0, ratio = 1.0f))
    }

    @Test
    fun `parseDets ordering preserved`() {
        // Input is score-descending; output preserves it (no re-sort).
        val flat = floatArrayOf(
            0f, 0f, 10f, 10f, 0.95f,
            0f, 0f, 20f, 20f, 0.80f,
            0f, 0f, 30f, 30f, 0.40f,
        )
        val boxes = YoloxDetector.parseDets(detsFlat = flat, boxCount = 3, ratio = 1.0f)
        assertEquals(listOf(0.95f, 0.80f, 0.40f), boxes.map { it.score })
        assertEquals(listOf(10f, 20f, 30f), boxes.map { it.x2 })
    }

    @Test
    fun `parseDets all below threshold`() {
        val flat = floatArrayOf(0f, 0f, 1f, 1f, 0.1f, 0f, 0f, 1f, 1f, 0.2f)
        assertEquals(emptyList<BoundingBox>(), YoloxDetector.parseDets(detsFlat = flat, boxCount = 2, ratio = 1.0f))
    }

    @Test
    fun `parseDets guards short array`() {
        // boxCount claims 2 but only one row of data -> guard returns [].
        val flat = floatArrayOf(0f, 0f, 1f, 1f, 0.9f)
        assertEquals(emptyList<BoundingBox>(), YoloxDetector.parseDets(detsFlat = flat, boxCount = 2, ratio = 1.0f))
    }

    @Test
    fun `parseDets guards non-positive ratio`() {
        val flat = floatArrayOf(0f, 0f, 1f, 1f, 0.9f)
        assertTrue(YoloxDetector.parseDets(detsFlat = flat, boxCount = 1, ratio = 0f).isEmpty())
    }
}
