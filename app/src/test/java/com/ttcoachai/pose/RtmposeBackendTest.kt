package com.ttcoachai.pose

import com.ttcoachai.shared.models.Keypoint2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for `RtmposeBackend`'s PURE pieces — `normalize` (per-axis divide + [0,1] clamp) and
 * `selectBest` (argmax mean-score person selection). No ORT, no Bitmap, no inference: these are
 * the only parts decoupled from the ONNX sessions, and they encode the schema-v2 output contract
 * + the export's `best_person` rule, so they are worth pinning directly. 1:1 Kotlin port of
 * `iosApp/TTCoach/Pose/RTMPoseBackendTests.swift`.
 */
class RtmposeBackendTest {

    // MARK: - normalize

    @Test
    fun `normalize divides per axis`() {
        val kps = listOf(Vec2(320f, 240f), Vec2(640f, 480f))
        val scores = floatArrayOf(0.5f, 0.9f)
        val out = RtmposeBackend.normalize(kps, scores, frameWidth = 640, frameHeight = 480)

        assertEquals(2, out.size)
        assertEquals(0.5f, out[0].x, 1e-6f)   // 320 / 640
        assertEquals(0.5f, out[0].y, 1e-6f)   // 240 / 480
        assertEquals(0.5f, out[0].score, 1e-6f)
        assertEquals(1.0f, out[1].x, 1e-6f)   // 640 / 640
        assertEquals(1.0f, out[1].y, 1e-6f)   // 480 / 480
        assertEquals(0.9f, out[1].score, 1e-6f)
    }

    @Test
    fun `normalize clamps coords and scores to unit range`() {
        // Out-of-frame pixels (negative + beyond extent) and out-of-range scores.
        val kps = listOf(Vec2(-10f, -5f), Vec2(1000f, 1000f))
        val scores = floatArrayOf(-0.2f, 1.7f)
        val out = RtmposeBackend.normalize(kps, scores, frameWidth = 100, frameHeight = 100)

        assertEquals(0.0f, out[0].x, 1e-6f)   // clamp low
        assertEquals(0.0f, out[0].y, 1e-6f)
        assertEquals(0.0f, out[0].score, 1e-6f)
        assertEquals(1.0f, out[1].x, 1e-6f)   // clamp high
        assertEquals(1.0f, out[1].y, 1e-6f)
        assertEquals(1.0f, out[1].score, 1e-6f)
    }

    @Test
    fun `normalize boundaries stay in range`() {
        val kps = listOf(Vec2(0f, 0f), Vec2(640f, 480f))
        val scores = floatArrayOf(0f, 1f)
        val out = RtmposeBackend.normalize(kps, scores, frameWidth = 640, frameHeight = 480)
        assertEquals(0.0f, out[0].x, 1e-6f)
        assertEquals(1.0f, out[1].x, 1e-6f)
        assertEquals(1.0f, out[1].y, 1e-6f)
    }

    @Test
    fun `normalize short scores array defaults missing to zero`() {
        val kps = listOf(Vec2(10f, 10f), Vec2(20f, 20f))
        val scores = floatArrayOf(0.7f) // only one score for two keypoints
        val out = RtmposeBackend.normalize(kps, scores, frameWidth = 100, frameHeight = 100)
        assertEquals(2, out.size)
        assertEquals(0.7f, out[0].score, 1e-6f)
        assertEquals(0.0f, out[1].score, 1e-6f)
    }

    @Test
    fun `normalize zero dimensions returns empty`() {
        val kps = listOf(Vec2(10f, 10f))
        assertTrue(
            RtmposeBackend.normalize(kps, floatArrayOf(0.5f), frameWidth = 0, frameHeight = 100).isEmpty()
        )
        assertTrue(
            RtmposeBackend.normalize(kps, floatArrayOf(0.5f), frameWidth = 100, frameHeight = 0).isEmpty()
        )
    }

    // MARK: - selectBest

    @Test
    fun `selectBest picks highest mean score`() {
        val a = candidate(scores = FloatArray(3) { 0.2f })
        val b = candidate(scores = FloatArray(3) { 0.9f })
        val c = candidate(scores = FloatArray(3) { 0.5f })
        assertEquals(1, RtmposeBackend.selectBest(listOf(a, b, c)))
    }

    @Test
    fun `selectBest uses mean not max`() {
        // Candidate 0 has a higher single peak but lower mean than candidate 1.
        val peaky = candidate(scores = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f))
        val even = candidate(scores = floatArrayOf(0.4f, 0.4f, 0.4f, 0.4f))
        assertEquals(1, RtmposeBackend.selectBest(listOf(peaky, even)))
    }

    @Test
    fun `selectBest empty returns nil`() {
        assertNull(RtmposeBackend.selectBest(emptyList()))
    }

    @Test
    fun `selectBest tie resolves to first`() {
        val a = candidate(scores = FloatArray(2) { 0.5f })
        val b = candidate(scores = FloatArray(2) { 0.5f })
        assertEquals(0, RtmposeBackend.selectBest(listOf(a, b)))
    }

    @Test
    fun `selectBest candidate with no scores counts as zero`() {
        val none = RtmposeEstimator.EstimateResult(keypoints = emptyList(), scores = FloatArray(0))
        val some = candidate(scores = floatArrayOf(0.1f))
        assertEquals(1, RtmposeBackend.selectBest(listOf(none, some)))
    }

    @Test
    fun `selectBest single candidate`() {
        val only = candidate(scores = floatArrayOf(0.3f))
        assertEquals(0, RtmposeBackend.selectBest(listOf(only)))
    }

    private fun candidate(scores: FloatArray): RtmposeEstimator.EstimateResult =
        RtmposeEstimator.EstimateResult(keypoints = listOf(Vec2(0f, 0f)), scores = scores)
}
