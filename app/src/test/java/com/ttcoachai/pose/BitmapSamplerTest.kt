package com.ttcoachai.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [BitmapSampler]'s pure sampling core (`IntArray` ARGB pixels in, `FloatArray` CHW
 * tensors out — no `android.graphics.Bitmap`, so this runs as a plain JUnit `:app:testDebugUnitTest`
 * with no Robolectric).
 *
 * Ported 1:1 from the iOS oracle-parity tests in `iosApp/TTCoach/Pose/YoloxDetectorTests.swift`
 * (`testLetterboxToCHWShapeRatioPaddingAndBGROrder`) and
 * `iosApp/TTCoach/Pose/RtmposeEstimatorTests.swift` (`testAffineWarpToCHWMatchesCv2Oracle`). Both
 * iOS tests build a `kCVPixelFormatType_32BGRA` buffer with per-pixel bytes `[B, G, R, A]`; here we
 * build the ARGB_8888 packed-int equivalent (`(a shl 24) or (r shl 16) or (g shl 8) or b`) carrying
 * the exact same B/G/R values, so the expected CHW outputs (including the hard-coded cv2-oracle
 * array) are identical.
 */
class BitmapSamplerTest {

    private fun argb(b: Int, g: Int, r: Int, a: Int = 255): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    // MARK: - letterboxToCHW

    @Test
    fun testLetterboxToCHWShapeRatioPaddingAndBGROrder() {
        // 4x2 solid color, BGR = (10, 20, 30). ratio = min(640/2, 640/4) = 160.
        // resized = (4*160, 2*160) = (640, 320): full width, top 320 rows = image,
        // rows 320..639 = 114 padding.
        val width = 4
        val height = 2
        val pixels = IntArray(width * height) { argb(b = 10, g = 20, r = 30) }

        val (floats, ratio) = BitmapSampler.letterboxToCHW(pixels, width, height, targetSize = 640)

        assertEquals(RtmposeMath.letterboxRatio(width, height, 640), ratio, 1e-6f)
        assertEquals(160f, ratio, 1e-6f)

        // Flat CHW length corresponds to shape [1, 3, 640, 640].
        assertEquals(3 * 640 * 640, floats.size)

        val plane = 640 * 640
        // Interior image pixel (dx=320, dy=160): all neighbors in-bounds -> exact color.
        // BGR channel order: B plane = 10, G plane = 20, R plane = 30.
        val interior = 160 * 640 + 320
        assertEquals(10f, floats[interior], 1e-3f)             // B
        assertEquals(20f, floats[plane + interior], 1e-3f)     // G
        assertEquals(30f, floats[2 * plane + interior], 1e-3f) // R

        // Padded region (row 500 >= 320) is 114 in every channel.
        val pad = 500 * 640 + 10
        assertEquals(114f, floats[pad], 1e-6f)
        assertEquals(114f, floats[plane + pad], 1e-6f)
        assertEquals(114f, floats[2 * plane + pad], 1e-6f)
    }

    // MARK: - affineWarpToCHW (cv2 oracle parity)
    //
    // Oracle generated with `.venv/bin/python` (cv2.warpAffine, INTER_LINEAR, BORDER_CONSTANT 0,
    // then (v-mean)/std in BGR). The 16x16 source uses a known gradient:
    //   B = min(255, 2*x + 10), G = min(255, 3*y + 5), R = min(255, x + y + 20).
    //
    // cv2.warpAffine takes a FORWARD (src->dst) matrix and inverts it internally for sampling.
    // Our sampler is output->source, so we feed the INVERSE of the forward matrix directly.
    // Tolerance is per-pixel 0.03 in normalized units (~1.7 raw 0-255 levels) to absorb
    // cv2-vs-hand-kernel sub-pixel rounding.

    @Test
    fun testAffineWarpToCHWMatchesCv2Oracle() {
        val outW = 8
        val outH = 8
        val srcW = 16
        val srcH = 16
        val pixels = IntArray(srcW * srcH)
        for (y in 0 until srcH) {
            for (x in 0 until srcW) {
                val b = minOf(255, 2 * x + 10)
                val g = minOf(255, 3 * y + 5)
                val r = minOf(255, x + y + 20)
                pixels[y * srcW + x] = argb(b, g, r)
            }
        }

        // INVERSE of the forward matrix used by the oracle (row-major [a,b,tx,c,d,ty]).
        val warp = Affine2x3(
            a = 0.6735751295336787f, b = -0.10362694300518134f, tx = 2.227979274611399f,
            c = -0.05181347150259067f, d = 0.7772020725388601f, ty = -1.7098445595854923f
        )

        val chw = BitmapSampler.affineWarpToCHW(
            pixels, srcW, srcH, warp, outW, outH,
            RtmposeMath.meanBGR.toFloatArray(), RtmposeMath.stdBGR.toFloatArray()
        )

        assertEquals(oracleCHW.size, chw.size)
        var maxErr = 0f
        for (i in chw.indices) {
            maxErr = maxOf(maxErr, kotlin.math.abs(chw[i] - oracleCHW[i]))
        }
        assertTrue("max per-element error vs cv2 oracle = $maxErr", maxErr < 0.03f)
    }

    companion object {
        /** CHW (B,G,R planes) flat float32 from the cv2 oracle for the 8x8 warp above. */
        private val oracleCHW: FloatArray = floatArrayOf(
            -2.117904f, -2.117904f, -2.117904f, -2.117904f, -2.117904f, -2.117904f, -2.117904f, -2.117904f,
            -2.100779f, -2.117904f, -2.117904f, -2.117904f, -2.117904f, -2.117904f, -2.117904f, -2.117904f,
            -1.912407f, -1.912407f, -1.895282f, -1.912407f, -1.912407f, -1.912407f, -1.912407f, -1.929532f,
            -1.878157f, -1.861033f, -1.826783f, -1.809658f, -1.792534f, -1.758284f, -1.741159f, -1.724035f,
            -1.878157f, -1.861033f, -1.843908f, -1.809658f, -1.792534f, -1.775409f, -1.741159f, -1.724035f,
            -1.895282f, -1.861033f, -1.843908f, -1.826783f, -1.792534f, -1.775409f, -1.741159f, -1.724035f,
            -1.895282f, -1.861033f, -1.843908f, -1.826783f, -1.792534f, -1.775409f, -1.758284f, -1.724035f,
            -1.895282f, -1.878157f, -1.843908f, -1.826783f, -1.809658f, -1.775409f, -1.758284f, -1.741159f,
            -2.035714f, -2.035714f, -2.035714f, -2.035714f, -2.035714f, -2.035714f, -2.035714f, -2.035714f,
            -2.035714f, -2.035714f, -2.035714f, -2.035714f, -2.035714f, -2.035714f, -2.035714f, -2.035714f,
            -1.965686f, -1.965686f, -1.965686f, -1.983193f, -1.983193f, -1.983193f, -1.983193f, -2.000700f,
            -1.913165f, -1.913165f, -1.913165f, -1.930672f, -1.930672f, -1.930672f, -1.930672f, -1.930672f,
            -1.878151f, -1.878151f, -1.878151f, -1.878151f, -1.878151f, -1.895658f, -1.895658f, -1.895658f,
            -1.825630f, -1.843137f, -1.843137f, -1.843137f, -1.843137f, -1.843137f, -1.843137f, -1.860644f,
            -1.790616f, -1.790616f, -1.790616f, -1.808123f, -1.808123f, -1.808123f, -1.808123f, -1.808123f,
            -1.755602f, -1.755602f, -1.755602f, -1.755602f, -1.755602f, -1.773109f, -1.773109f, -1.773109f,
            -1.804444f, -1.804444f, -1.804444f, -1.804444f, -1.804444f, -1.804444f, -1.804444f, -1.804444f,
            -1.787015f, -1.787015f, -1.804444f, -1.804444f, -1.804444f, -1.804444f, -1.804444f, -1.804444f,
            -1.473290f, -1.490719f, -1.490719f, -1.508148f, -1.543007f, -1.543007f, -1.560436f, -1.577865f,
            -1.403573f, -1.403573f, -1.386144f, -1.386144f, -1.368715f, -1.351285f, -1.351285f, -1.333856f,
            -1.403573f, -1.386144f, -1.386144f, -1.368715f, -1.351285f, -1.351285f, -1.333856f, -1.316427f,
            -1.386144f, -1.368715f, -1.368715f, -1.351285f, -1.351285f, -1.333856f, -1.316427f, -1.316427f,
            -1.368715f, -1.368715f, -1.351285f, -1.351285f, -1.333856f, -1.316427f, -1.316427f, -1.298998f,
            -1.368715f, -1.351285f, -1.351285f, -1.333856f, -1.316427f, -1.316427f, -1.298998f, -1.281569f
        )
    }
}
