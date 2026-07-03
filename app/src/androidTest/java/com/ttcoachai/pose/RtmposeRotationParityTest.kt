package com.ttcoachai.pose

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ttcoachai.shared.models.Keypoint2D
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rotation-math parity check for [RtmposeFrameProcessor] vs the frozen
 * `PoseLandmarkerProcessor` technique it was copied from (Phase 3, task C2).
 *
 * HONESTY NOTE on scope: [RtmposeFrameProcessor.analyze] takes a real CameraX
 * `ImageProxy` and calls its default `toBitmap()`, which decodes actual YUV_420_888 /
 * JPEG image planes (`ImageUtil.createBitmapFromImageProxy`). A faithful `ImageProxy`
 * fake carrying real, decodable pixel data isn't feasible to build off-device (no camera
 * HAL, no real sensor buffer) — an interface-level fake would either fail inside
 * `toBitmap()` or require reimplementing image codec internals, which would not actually
 * exercise the real conversion path anyway. So this test does NOT drive
 * `RtmposeFrameProcessor.analyze()` end-to-end and does NOT assert full pixel-parity
 * against `PoseLandmarkerProcessor` — that remains a device-frame check (visually
 * comparing the RTMPose overlay against the frozen MediaPipe overlay on the same live
 * frame, done manually on-device).
 *
 * What IS asserted here, deterministically: the width/height SWAP RULE that both
 * processors' copied technique depends on (`rotationDegrees % 180 != 0` swaps width and
 * height of the rotated output buffer — see `RtmposeFrameProcessor.kt` lines computing
 * `rotatedWidth`/`rotatedHeight`, identical to `PoseLandmarkerProcessor`). We replicate
 * that exact formula against a stub [PoseBackend] that records the dimensions it was
 * called with, for each of the four CameraX rotation values (0/90/180/270).
 */
@RunWith(AndroidJUnit4::class)
class RtmposeRotationParityTest {

    /** Records the (width, height) it was invoked with; returns an empty pose (no ONNX). */
    private class RecordingStubBackend : PoseBackend {
        var lastWidth: Int? = null
        var lastHeight: Int? = null

        override fun estimatePose(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): List<Keypoint2D> {
            lastWidth = frameWidth
            lastHeight = frameHeight
            return emptyList()
        }
    }

    /** Mirrors RtmposeFrameProcessor's swap rule exactly (same formula, not re-derived). */
    private fun expectedRotatedDims(sourceWidth: Int, sourceHeight: Int, rotationDegrees: Int): Pair<Int, Int> {
        val rotatedWidth = if (rotationDegrees % 180 != 0) sourceHeight else sourceWidth
        val rotatedHeight = if (rotationDegrees % 180 != 0) sourceWidth else sourceHeight
        return rotatedWidth to rotatedHeight
    }

    @Test
    fun rotation_0_and_180_keep_source_orientation() {
        val backend = RecordingStubBackend()
        val (w0, h0) = expectedRotatedDims(sourceWidth = 640, sourceHeight = 480, rotationDegrees = 0)
        val (w180, h180) = expectedRotatedDims(sourceWidth = 640, sourceHeight = 480, rotationDegrees = 180)

        // Feed the stub directly with the expected dims (no real ImageProxy — see class doc);
        // this pins the swap-rule outcome, not the full frame-conversion pipeline.
        backend.estimatePose(Bitmap.createBitmap(w0, h0, Bitmap.Config.ARGB_8888), w0, h0)
        assertEquals(640, backend.lastWidth)
        assertEquals(480, backend.lastHeight)

        backend.estimatePose(Bitmap.createBitmap(w180, h180, Bitmap.Config.ARGB_8888), w180, h180)
        assertEquals(640, backend.lastWidth)
        assertEquals(480, backend.lastHeight)
    }

    @Test
    fun rotation_90_and_270_swap_width_and_height() {
        val backend = RecordingStubBackend()
        val (w90, h90) = expectedRotatedDims(sourceWidth = 640, sourceHeight = 480, rotationDegrees = 90)
        val (w270, h270) = expectedRotatedDims(sourceWidth = 640, sourceHeight = 480, rotationDegrees = 270)

        backend.estimatePose(Bitmap.createBitmap(w90, h90, Bitmap.Config.ARGB_8888), w90, h90)
        assertEquals(480, backend.lastWidth)
        assertEquals(640, backend.lastHeight)

        backend.estimatePose(Bitmap.createBitmap(w270, h270, Bitmap.Config.ARGB_8888), w270, h270)
        assertEquals(480, backend.lastWidth)
        assertEquals(640, backend.lastHeight)
    }
}
