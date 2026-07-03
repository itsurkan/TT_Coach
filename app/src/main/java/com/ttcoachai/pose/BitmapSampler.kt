package com.ttcoachai.pose

import android.graphics.Bitmap
import kotlin.math.floor

/**
 * Shared image -> CHW float tensor sampling utilities for the RTMPose two-stage pipeline. Stage 1
 * (YOLOX) uses [letterboxToCHW]; Stage 2 (RTMPose) uses [affineWarpToCHW]. Both live here so the
 * image-sampling conventions (channel order, coordinate mapping, bilinear interpolation) are
 * reviewed in one place. The math itself is delegated to [RtmposeMath] — this file only moves
 * pixels. 1:1 Kotlin port of `iosApp/TTCoach/Pose/PixelBufferSampler.swift`.
 *
 * COLOR ORDER: ARGB_8888 packed pixels are unpacked as
 * `a=(argb ushr 24) and 0xFF, r=(argb ushr 16) and 0xFF, g=(argb ushr 8) and 0xFF, b=argb and 0xFF`.
 * rtmlib feeds OpenCV BGR straight through, so the produced CHW tensors are in **BGR** channel
 * order (channel 0 = B, 1 = G, 2 = R). Do NOT swap to RGB.
 *
 * INTERPOLATION CHOICE: bilinear sampling is implemented manually in Float, matching OpenCV's
 * `INTER_LINEAR` pixel-center convention (`src = (dst + 0.5) * scale - 0.5`) rather than using
 * `android.graphics.Canvas`/`Matrix` (whose scaling/warp conventions don't guarantee cv2 parity).
 *
 * Pure sampling core operates on `IntArray` ARGB pixels + `width`/`height` — no Android `Bitmap`
 * type — so it is unit-testable as a plain JUnit test with no Robolectric. [Bitmap]-taking
 * overloads below are thin adapters that copy pixels via [Bitmap.getPixels] and delegate.
 */
object BitmapSampler {

    // MARK: - Bitmap adapters (thin; not unit-tested here)

    /** [Bitmap] adapter for [letterboxToCHW]. */
    fun letterboxToCHW(bitmap: Bitmap, targetSize: Int = 640): Pair<FloatArray, Float> {
        val (pixels, width, height) = bitmap.toArgbPixels()
        return letterboxToCHW(pixels, width, height, targetSize)
    }

    /** [Bitmap] adapter for [affineWarpToCHW]. */
    fun affineWarpToCHW(
        bitmap: Bitmap,
        warpMatrix: Affine2x3,
        outputW: Int,
        outputH: Int,
        meanBGR: FloatArray,
        stdBGR: FloatArray
    ): FloatArray {
        val (pixels, width, height) = bitmap.toArgbPixels()
        return affineWarpToCHW(pixels, width, height, warpMatrix, outputW, outputH, meanBGR, stdBGR)
    }

    private fun Bitmap.toArgbPixels(): Triple<IntArray, Int, Int> {
        val w = width
        val h = height
        val pixels = IntArray(w * h)
        getPixels(pixels, 0, w, 0, 0, w, h)
        return Triple(pixels, w, h)
    }

    // MARK: - Pure sampling core

    /**
     * Letterbox-resize an ARGB frame into a `targetSize x targetSize` CHW float array, BGR
     * channel order, values 0-255, no mean/std — exactly `YOLOX.preprocess`. The image is
     * bilinear-resized to `(int(w*ratio), int(h*ratio))` and pasted at the **top-left** of a
     * canvas filled with 114; the rest stays 114.
     *
     * Returns the flat CHW float32 buffer (length `3*targetSize*targetSize`) plus the `ratio`
     * used (caller divides detected box coords by it to return to image pixels).
     */
    fun letterboxToCHW(
        pixels: IntArray,
        width: Int,
        height: Int,
        targetSize: Int = 640
    ): Pair<FloatArray, Float> {
        val src = ArgbView(pixels, width, height)
        val ratio = RtmposeMath.letterboxRatio(width, height, targetSize)
        val (rw, rh) = RtmposeMath.letterboxResizedSize(width, height, targetSize)

        val plane = targetSize * targetSize
        // CHW, BGR. Channels 0=B,1=G,2=R. Fill the whole canvas with 114 first.
        val chw = FloatArray(3 * plane) { 114.0f }

        // OpenCV INTER_LINEAR resize maps dst -> src by pixel centers.
        val scaleX = width.toFloat() / rw.toFloat()
        val scaleY = height.toFloat() / rh.toFloat()

        for (dy in 0 until rh) {
            val sy = (dy.toFloat() + 0.5f) * scaleY - 0.5f
            val rowOff = dy * targetSize
            for (dx in 0 until rw) {
                val sx = (dx.toFloat() + 0.5f) * scaleX - 0.5f
                val (b, g, r) = src.bilinearBGR(sx, sy)
                val p = rowOff + dx
                chw[p] = b                 // B plane
                chw[plane + p] = g         // G plane
                chw[2 * plane + p] = r     // R plane
            }
        }
        return Pair(chw, ratio)
    }

    /**
     * Affine-warp an ARGB frame into an `outputW x outputH` CHW float array, then normalize
     * `(v - mean) / std` per channel in **BGR** order — exactly the
     * `cv2.warpAffine(INTER_LINEAR)` + normalize in `RTMPose.preprocess`.
     *
     * MATRIX CONVENTION: `warpMatrix` maps **output (destination) coords -> source (image)
     * coords** (the inverse/sampling map). For each output pixel `(ox, oy)` the sampler computes
     * `RtmposeMath.affine2x3Apply(warpMatrix, (ox, oy))` to find where to read in the source.
     * Callers produce this with `RtmposeMath.getWarpMatrix(..., inverse = true)`. Out-of-bounds
     * source samples read 0 (OpenCV BORDER_CONSTANT default) before normalization.
     */
    fun affineWarpToCHW(
        pixels: IntArray,
        width: Int,
        height: Int,
        warpMatrix: Affine2x3,
        outputW: Int,
        outputH: Int,
        meanBGR: FloatArray,
        stdBGR: FloatArray
    ): FloatArray {
        require(meanBGR.size == 3 && stdBGR.size == 3) { "meanBGR/stdBGR must have 3 elements" }
        val src = ArgbView(pixels, width, height)
        val plane = outputW * outputH
        val chw = FloatArray(3 * plane)
        val mB = meanBGR[0]; val mG = meanBGR[1]; val mR = meanBGR[2]
        val sB = stdBGR[0]; val sG = stdBGR[1]; val sR = stdBGR[2]

        for (oy in 0 until outputH) {
            val rowOff = oy * outputW
            for (ox in 0 until outputW) {
                val srcPt = RtmposeMath.affine2x3Apply(warpMatrix, Vec2(ox.toFloat(), oy.toFloat()))
                val (b, g, r) = src.bilinearBGR(srcPt.x, srcPt.y)
                val p = rowOff + ox
                chw[p] = (b - mB) / sB
                chw[plane + p] = (g - mG) / sG
                chw[2 * plane + p] = (r - mR) / sR
            }
        }
        return chw
    }

    // MARK: - Internals

    /** A read-only view over ARGB_8888 packed pixels, row-major. */
    private class ArgbView(private val pixels: IntArray, val width: Int, val height: Int) {

        /** Bilinear-sample BGR at fractional `(x, y)`. Neighbors outside the image read 0
         * (OpenCV BORDER_CONSTANT). */
        fun bilinearBGR(x: Float, y: Float): FloatArray {
            val x0 = floor(x).toInt()
            val y0 = floor(y).toInt()
            val x1 = x0 + 1
            val y1 = y0 + 1
            val wx = x - x0.toFloat()
            val wy = y - y0.toFloat()

            val p00 = bgrAt(x0, y0)
            val p10 = bgrAt(x1, y0)
            val p01 = bgrAt(x0, y1)
            val p11 = bgrAt(x1, y1)

            return floatArrayOf(
                lerp2(p00[0], p10[0], p01[0], p11[0], wx, wy),
                lerp2(p00[1], p10[1], p01[1], p11[1], wx, wy),
                lerp2(p00[2], p10[2], p01[2], p11[2], wx, wy)
            )
        }

        private fun bgrAt(xi: Int, yi: Int): FloatArray {
            if (xi < 0 || xi >= width || yi < 0 || yi >= height) {
                return floatArrayOf(0f, 0f, 0f)
            }
            val argb = pixels[yi * width + xi]
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            return floatArrayOf(b.toFloat(), g.toFloat(), r.toFloat())
        }

        private fun lerp2(a: Float, b: Float, c: Float, d: Float, wx: Float, wy: Float): Float {
            val top = a + (b - a) * wx
            val bot = c + (d - c) * wx
            return top + (bot - top) * wy
        }
    }
}
