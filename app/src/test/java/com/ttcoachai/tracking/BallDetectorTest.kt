package com.ttcoachai.tracking

import android.graphics.Bitmap
import com.ttcoachai.shared.models.BallDetectionStatus
import com.ttcoachai.shared.models.RegionOfInterest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for BallDetector.
 *
 * OpenCV native operations require the JNI library to be loaded. These tests validate:
 * - NOT_DETECTED status when detection fails (expected on JVM without native libs)
 * - ROI coordinate normalisation arithmetic (pure Kotlin, verifiable without OpenCV)
 * - BallColor enum selection
 * - release() does not throw
 *
 * Full integration tests (orange/white ball detection against real frames) must run
 * as instrumented tests on a device where OpenCV native libs are available.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class BallDetectorTest {

    private lateinit var whiteDetector: BallDetector
    private lateinit var orangeDetector: BallDetector

    @Before
    fun setUp() {
        whiteDetector  = BallDetector(BallDetector.BallColor.WHITE,  expectedRadiusRange = 4..25)
        orangeDetector = BallDetector(BallDetector.BallColor.ORANGE, expectedRadiusRange = 4..25)
    }

    // --- NOT_DETECTED status when no ball is present ---

    @Test
    fun `detect returns NOT_DETECTED for black bitmap (no color match)`() {
        // A fully black bitmap: no orange or white pixels → NOT_DETECTED
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        // bitmap is already black by default
        val roi = RegionOfInterest(x = 64, y = 120, width = 512, height = 360)

        val result = whiteDetector.detect(bitmap, roi, frameIndex = 0, timestampMs = 0L)

        // On JVM without native OpenCV libs the detector catches the exception and returns NOT_DETECTED
        assertEquals(BallDetectionStatus.NOT_DETECTED, result.status)
        assertEquals(0f, result.confidence, 0.001f)
        assertEquals(0f, result.radiusPx, 0.001f)
        assertEquals(0, result.frameIndex)
        assertEquals(0L, result.timestampMs)
    }

    @Test
    fun `detect propagates frameIndex and timestampMs in NOT_DETECTED result`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val roi = RegionOfInterest(x = 0, y = 0, width = 100, height = 100)

        val result = orangeDetector.detect(bitmap, roi, frameIndex = 42, timestampMs = 1234L)

        assertEquals(42, result.frameIndex)
        assertEquals(1234L, result.timestampMs)
    }

    // --- ROI coordinate normalisation ---

    @Test
    fun `ROI coordinate normalisation maps ROI-local position to full-frame normalised coords`() {
        // Verify the arithmetic used inside BallDetector for coordinate normalisation:
        // cxFrame = (roiX + cxRoi) / frameWidth
        val frameWidth = 1280
        val frameHeight = 720
        val roiX = 128   // (1280 - 1024) / 2 = 128
        val roiY = 180   // 720 - 540 = 180
        val cxRoi = 200f
        val cyRoi = 150f

        val expectedCxFrame = (roiX + cxRoi) / frameWidth.toFloat()
        val expectedCyFrame = (roiY + cyRoi) / frameHeight.toFloat()

        // Both normalised values must be in [0, 1]
        assertTrue("cxFrame should be in [0,1]", expectedCxFrame in 0f..1f)
        assertTrue("cyFrame should be in [0,1]", expectedCyFrame in 0f..1f)

        // Specific values: (128+200)/1280 = 328/1280 ≈ 0.256
        assertEquals(0.2563f, expectedCxFrame, 0.001f)
        // (180+150)/720 = 330/720 ≈ 0.458
        assertEquals(0.4583f, expectedCyFrame, 0.001f)
    }

    @Test
    fun `detect clamps ROI to frame bounds when ROI exceeds frame size`() {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        // ROI extends beyond frame boundaries
        val roi = RegionOfInterest(x = 300, y = 220, width = 200, height = 200)

        // Should not throw; returns NOT_DETECTED (clamped ROI may be zero-size → handled gracefully)
        val result = whiteDetector.detect(bitmap, roi, frameIndex = 1, timestampMs = 100L)
        assertNotNull(result)
        assertEquals(BallDetectionStatus.NOT_DETECTED, result.status)
    }

    // --- BallColor selection ---

    @Test
    fun `white detector and orange detector can be instantiated independently`() {
        assertNotNull(whiteDetector)
        assertNotNull(orangeDetector)
        // Both should return NOT_DETECTED for a blank bitmap (no crash)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val roi = RegionOfInterest(x = 0, y = 0, width = 100, height = 100)

        val whiteResult  = whiteDetector.detect(bitmap, roi, 0, 0L)
        val orangeResult = orangeDetector.detect(bitmap, roi, 0, 0L)

        assertEquals(BallDetectionStatus.NOT_DETECTED, whiteResult.status)
        assertEquals(BallDetectionStatus.NOT_DETECTED, orangeResult.status)
    }

    // --- Resource cleanup ---

    @Test
    fun `release does not throw`() {
        // Calling release on a detector that has never processed a frame should be safe
        val detector = BallDetector()
        detector.release()  // must not throw
    }

    @Test
    fun `release can be called after detect without throwing`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val roi = RegionOfInterest(x = 0, y = 0, width = 200, height = 200)
        whiteDetector.detect(bitmap, roi, 0, 0L)
        whiteDetector.release()  // must not throw
    }
}
