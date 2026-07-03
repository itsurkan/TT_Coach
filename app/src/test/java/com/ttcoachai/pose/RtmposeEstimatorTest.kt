package com.ttcoachai.pose

import com.ttcoachai.pose.RtmposeEstimator.SimccAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the RTMPose Stage-2 estimator's deterministic pieces: the SimCC argmax + decode
 * post-processing wiring ([RtmposeMath.simccMaximum] + [RtmposeMath.decodeKeypoints]) and the
 * estimator's own output-name-selection/slicing ([RtmposeEstimator.readSimccAxis]) and full
 * warp->run->decode composition via a stub [RtmposeEstimator.SimccRunner] — all proven WITHOUT a
 * native ONNX runtime. 1:1 Kotlin port of `iosApp/TTCoach/Pose/RtmposeEstimatorTests.swift`
 * (`testDecodeWiringFromSyntheticSimcc` case only — the cv2-oracle warp-parity case already has
 * Kotlin coverage in `BitmapSamplerTest`). Real `OrtSession.run` inference is exercised on-device
 * later (T7), not here.
 */
class RtmposeEstimatorTest {

    // MARK: - 1. decode-wiring (simccMaximum + decodeKeypoints), no ORT — ported verbatim.

    @Test
    fun `decode wiring from synthetic simcc`() {
        val k = 17
        val wx = 384 // simcc_x width (192 * 2)
        val wy = 512 // simcc_y width (256 * 2)

        // Keypoint 0: peak at x-bin 192, y-bin 256 -> loc (192,256) -> /2 = (96,128),
        // which is the CENTER of the 192x256 input box.
        val simccX = Array(k) { FloatArray(wx) }
        val simccY = Array(k) { FloatArray(wy) }
        for (i in 0 until k) {
            val xb = 192
            val yb = 256
            simccX[i][xb] = 1.0f
            simccY[i][yb] = 1.0f
        }
        // Keypoint 1: a different, off-center peak.
        simccX[1] = FloatArray(wx)
        simccX[1][0] = 2.0f
        simccY[1] = FloatArray(wy)
        simccY[1][0] = 2.0f

        val (locs, vals) = RtmposeMath.simccMaximum(simccX, simccY)

        // val = 0.5*(1+1) = 1 for keypoint 0; 0.5*(2+2)=2 for keypoint 1.
        assertEquals(1.0f, vals[0], 1e-6f)
        assertEquals(1.0f, vals[2], 1e-6f)
        assertEquals(2.0f, vals[1], 1e-6f)
        assertEquals(Vec2(192f, 256f), locs[0])
        assertEquals(Vec2(0f, 0f), locs[1])

        // Known center/scale. With center at the box center, a loc at the input center
        // (96,128 after /2) should decode back to `center`.
        val center = Vec2(500f, 300f)
        val scale = Vec2(192f, 256f) // already aspect-correct (0.75)

        val kps = RtmposeMath.decodeKeypoints(
            locs = locs, center = center, scale = scale,
            modelInputW = 192, modelInputH = 256, simccSplitRatio = 2.0f
        )

        // Keypoint 0: kp = (96,128); x = 96/192*192 + 500 - 96 = 96+500-96 = 500;
        //             y = 128/256*256 + 300 - 128 = 128+300-128 = 300 -> == center.
        assertEquals(center.x, kps[0].x, 1e-3f)
        assertEquals(center.y, kps[0].y, 1e-3f)

        // Keypoint 1: loc (0,0) -> kp (0,0); x = 0 + 500 - 96 = 404; y = 0 + 300 - 128 = 172.
        assertEquals(404f, kps[1].x, 1e-3f)
        assertEquals(172f, kps[1].y, 1e-3f)
    }

    // MARK: - 2. readSimccAxis selection/slicing (T4's new surface)

    @Test
    fun `readSimccAxis selects by exact name and slices K rows of the tensor's own width`() {
        val k = 17
        val wx = 384
        val wy = 512
        val xData = FloatArray(k * wx) { it.toFloat() }
        val yData = FloatArray(k * wy) { -it.toFloat() }
        val outputs = mapOf(
            "simcc_x" to SimccAxis(intArrayOf(1, k, wx), xData),
            "simcc_y" to SimccAxis(intArrayOf(1, k, wy), yData),
        )

        val rowsX = RtmposeEstimator.readSimccAxis(outputs, preferring = "simcc_x", otherAxisName = "simcc_y")
        val rowsY = RtmposeEstimator.readSimccAxis(outputs, preferring = "simcc_y", otherAxisName = "simcc_x")

        assertEquals(k, rowsX.size)
        assertEquals(k, rowsY.size)
        assertTrue(rowsX.all { it.size == wx })
        assertTrue(rowsY.all { it.size == wy })
        // Row 0 of simcc_x is the first wx elements of xData (never picks the _y value).
        assertEquals(xData.copyOfRange(0, wx).toList(), rowsX[0].toList())
        assertEquals(yData.copyOfRange(0, wy).toList(), rowsY[0].toList())
        // Row 3 slices the correct offset using the tensor's OWN width.
        assertEquals(xData.copyOfRange(3 * wx, 4 * wx).toList(), rowsX[3].toList())
    }

    @Test
    fun `readSimccAxis selects by suffix-contains without picking the other axis`() {
        val k = 17
        val wx = 384
        val wy = 512
        val outputs = mapOf(
            "out_simcc_x" to SimccAxis(intArrayOf(1, k, wx), FloatArray(k * wx) { 1f }),
            "out_simcc_y" to SimccAxis(intArrayOf(1, k, wy), FloatArray(k * wy) { 2f }),
        )

        val rowsX = RtmposeEstimator.readSimccAxis(outputs, preferring = "simcc_x", otherAxisName = "simcc_y")
        val rowsY = RtmposeEstimator.readSimccAxis(outputs, preferring = "simcc_y", otherAxisName = "simcc_x")

        assertEquals(k, rowsX.size)
        assertTrue(rowsX.all { row -> row.all { it == 1f } })
        assertTrue(rowsY.all { row -> row.all { it == 2f } })
    }

    @Test
    fun `readSimccAxis throws missingOutput when neither name matches`() {
        val outputs = mapOf("other" to SimccAxis(intArrayOf(1, 17, 10), FloatArray(170)))
        assertThrows(RtmposeEstimatorError.MissingOutput::class.java) {
            RtmposeEstimator.readSimccAxis(outputs, preferring = "simcc_x", otherAxisName = "simcc_y")
        }
    }

    @Test
    fun `readSimccAxis throws unexpectedShape when shape does not have 3 dims`() {
        val outputs = mapOf("simcc_x" to SimccAxis(intArrayOf(17, 10), FloatArray(170)))
        assertThrows(RtmposeEstimatorError.UnexpectedShape::class.java) {
            RtmposeEstimator.readSimccAxis(outputs, preferring = "simcc_x", otherAxisName = "simcc_y")
        }
    }

    @Test
    fun `readSimccAxis throws unexpectedKeypointCount when K is not 17`() {
        val outputs = mapOf("simcc_x" to SimccAxis(intArrayOf(1, 16, 10), FloatArray(160)))
        assertThrows(RtmposeEstimatorError.UnexpectedKeypointCount::class.java) {
            RtmposeEstimator.readSimccAxis(outputs, preferring = "simcc_x", otherAxisName = "simcc_y")
        }
    }

    @Test
    fun `readSimccAxis throws unexpectedShape when data is too small for K times W`() {
        val outputs = mapOf("simcc_x" to SimccAxis(intArrayOf(1, 17, 10), FloatArray(50)))
        assertThrows(RtmposeEstimatorError.UnexpectedShape::class.java) {
            RtmposeEstimator.readSimccAxis(outputs, preferring = "simcc_x", otherAxisName = "simcc_y")
        }
    }

    // MARK: - 3. full estimate() happy path via a stub SimccRunner (warp -> run -> decode wiring)

    @Test
    fun `estimate decodes keypoints from a stub runner`() {
        val k = 17
        val wx = 384
        val wy = 512
        // One-hot peak at (xb=192, yb=256) for every keypoint -> decodes to the bbox center
        // (same numbers as the decode-wiring case above, keypoint 0).
        val simccX = FloatArray(k * wx)
        val simccY = FloatArray(k * wy)
        for (i in 0 until k) {
            simccX[i * wx + 192] = 1.0f
            simccY[i * wy + 256] = 1.0f
        }
        val stubRunner = RtmposeEstimator.SimccRunner {
            mapOf(
                "simcc_x" to SimccAxis(intArrayOf(1, k, wx), simccX),
                "simcc_y" to SimccAxis(intArrayOf(1, k, wy), simccY),
            )
        }
        val estimator = RtmposeEstimator(stubRunner)

        // Solid-color 16x16 ARGB pixel buffer (pure IntArray core, no android.graphics.Bitmap) so
        // the warp step doesn't throw. bbox is a centered square box.
        val pixels = solidArgbPixels(16, 16)
        val bbox = BoundingBox(x1 = 2f, y1 = 2f, x2 = 14f, y2 = 14f, score = 0.9f)

        val (keypoints, scores) = estimator.estimate(pixels, 16, 16, bbox, imageWidth = 16, imageHeight = 16)

        assertEquals(k, keypoints.size)
        assertEquals(k, scores.size)
        // Every keypoint has the same one-hot peak -> same decoded location, and val = 1.0
        // (raw SimCC val, NOT clamped here).
        assertTrue(scores.all { it == 1.0f })
        for (i in 1 until k) {
            assertEquals(keypoints[0].x, keypoints[i].x, 1e-3f)
            assertEquals(keypoints[0].y, keypoints[i].y, 1e-3f)
        }
    }

    // MARK: - 4. failure path

    @Test
    fun `estimate returns 17 zero keypoints and scores when the runner throws`() {
        val throwingRunner = RtmposeEstimator.SimccRunner { throw RuntimeException("boom") }
        val estimator = RtmposeEstimator(throwingRunner)

        val pixels = solidArgbPixels(16, 16)
        val bbox = BoundingBox(x1 = 2f, y1 = 2f, x2 = 14f, y2 = 14f, score = 0.9f)

        val (keypoints, scores) = estimator.estimate(pixels, 16, 16, bbox, imageWidth = 16, imageHeight = 16)

        assertEquals(17, keypoints.size)
        assertEquals(17, scores.size)
        assertTrue(keypoints.all { it == Vec2(0f, 0f) })
        assertTrue(scores.all { it == 0f })
    }

    private fun solidArgbPixels(width: Int, height: Int): IntArray =
        IntArray(width * height) { 0xFF808080.toInt() }
}
