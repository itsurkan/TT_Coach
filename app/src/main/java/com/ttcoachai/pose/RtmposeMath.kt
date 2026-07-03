package com.ttcoachai.pose

import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure, platform-free parity math for the RTMPose two-stage pipeline.
 *
 * Every function here mirrors a specific `rtmlib` routine numerically so that the Android
 * pose backend produces the same keypoints as the desktop golden
 * (`scripts/poses/export_poses_rtmpose.py`). No ONNX, no image sampling here — only
 * deterministic functions on numbers/arrays. See `iosApp/TTCoach/Pose/RTMPOSE_PARITY.md` for
 * the contract; this is a 1:1 Kotlin port of `iosApp/TTCoach/Pose/RTMPoseMath.swift`.
 *
 * References point at the exact rtmlib source:
 *   - `rtmlib/tools/pose_estimation/pre_processings.py`
 *   - `rtmlib/tools/pose_estimation/post_processings.py`
 *   - `rtmlib/tools/object_detection/yolox.py`
 */
object RtmposeMath {

    // MARK: - Constants

    /** RTMPose normalization mean, applied to channels in **B, G, R order** (rtmlib quirk: an
     * RGB-valued mean is subtracted from BGR channels — copy it, do not "fix" it). */
    val meanBGR: List<Float> = listOf(123.675f, 116.28f, 103.53f)

    /** RTMPose normalization std, applied to channels in **B, G, R order** (see [meanBGR]). */
    val stdBGR: List<Float> = listOf(58.395f, 57.12f, 57.375f)

    /** SimCC split ratio (`simcc_split_ratio` in rtmlib): heatmap resolution / input resolution. */
    const val simccSplitRatio: Float = 2.0f

    /** YOLOX detection score threshold (`final_scores > 0.3` branch in `YOLOX.postprocess`). */
    const val detScoreThreshold: Float = 0.3f

    /** RTMPose model input width (`model_input_size[0]`). */
    const val poseInputW: Int = 192

    /** RTMPose model input height (`model_input_size[1]`). */
    const val poseInputH: Int = 256

    /** YOLOX square input size. */
    const val detInput: Int = 640

    /** RTMPose input aspect ratio = w / h = 192 / 256 = 0.75. */
    val poseAspectRatio: Float = poseInputW.toFloat() / poseInputH.toFloat()

    // MARK: - 1. YOLOX letterbox (preprocess `ratio` only — no NMS/decode, that is baked in)

    /** `ratio = min(target / h, target / w)`. Mirrors `YOLOX.preprocess`.
     * Assumes a SQUARE model input (640x640) — uses one `target` for both axes,
     * matching rtmlib's `min(input[0]/h, input[1]/w)` only because input is square. */
    fun letterboxRatio(imageWidth: Int, imageHeight: Int, target: Int = 640): Float {
        val t = target.toFloat()
        return minOf(t / imageHeight.toFloat(), t / imageWidth.toFloat())
    }

    /** Resized size pasted top-left on the 640x640 canvas.
     * `(int(imgW*ratio), int(imgH*ratio))` — truncation, matching numpy `int()`. */
    fun letterboxResizedSize(imageWidth: Int, imageHeight: Int, target: Int = 640): Pair<Int, Int> {
        val ratio = letterboxRatio(imageWidth, imageHeight, target)
        // toInt() on a positive Float truncates toward zero, matching numpy int().
        return Pair((imageWidth.toFloat() * ratio).toInt(), (imageHeight.toFloat() * ratio).toInt())
    }

    // MARK: - 2. bbox xyxy -> center, scale

    /** `bbox_xyxy2cs`: center = ((x1+x2)/2, (y1+y2)/2), scale = ((x2-x1)*padding, (y2-y1)*padding). */
    fun bboxXyxyToCenterScale(
        x1: Float, y1: Float, x2: Float, y2: Float, padding: Float = 1.25f
    ): CenterScale {
        val center = Vec2((x1 + x2) * 0.5f, (y1 + y2) * 0.5f)
        val scale = Vec2((x2 - x1) * padding, (y2 - y1) * padding)
        return CenterScale(center, scale)
    }

    // MARK: - 3. aspect-ratio fix

    /** `top_down_affine`'s reshape step. aspectRatio = w/h (= 0.75 for RTMPose).
     * if scale.x > scale.y*aspect { (scale.x, scale.x/aspect) } else { (scale.y*aspect, scale.y) } */
    fun fixAspectRatio(scale: Vec2, aspectRatio: Float): Vec2 {
        return if (scale.x > scale.y * aspectRatio) {
            Vec2(scale.x, scale.x / aspectRatio)
        } else {
            Vec2(scale.y * aspectRatio, scale.y)
        }
    }

    /** Convenience: the aspect-fix step inside `top_down_affine`, returning the adjusted
     * bbox_scale (the warp itself is platform code elsewhere). aspect = outputW/outputH. */
    fun topDownAffineScale(scale: Vec2, outputW: Int, outputH: Int): Vec2 {
        val aspect = outputW.toFloat() / outputH.toFloat()
        return fixAspectRatio(scale, aspect)
    }

    // MARK: - 4. affine from three point pairs (cv2.getAffineTransform)

    /** Solve the 2x3 matrix M so that for each i, `dst[i] = M * [src[i].x, src[i].y, 1]`.
     * Equivalent to `cv2.getAffineTransform(src, dst)` — exactly 3 point pairs.
     *
     * The x-row and y-row share the same 3x3 coefficient matrix
     * `A = [[sx0, sy0, 1], [sx1, sy1, 1], [sx2, sy2, 1]]`; solve A·mx = dstX and A·my = dstY. */
    fun affineFromPointPairs(src: List<Vec2>, dst: List<Vec2>): Affine2x3 {
        require(src.size == 3 && dst.size == 3) { "affineFromPointPairs requires exactly 3 point pairs" }

        // 3x3 matrix rows: [sx_i, sy_i, 1]
        val a00 = src[0].x; val a01 = src[0].y; val a02 = 1f
        val a10 = src[1].x; val a11 = src[1].y; val a12 = 1f
        val a20 = src[2].x; val a21 = src[2].y; val a22 = 1f

        val det = a00 * (a11 * a22 - a12 * a21) -
            a01 * (a10 * a22 - a12 * a20) +
            a02 * (a10 * a21 - a11 * a20)
        val invDet = 1f / det

        // Inverse of A (adjugate / det), row-major 3x3.
        val i00 = (a11 * a22 - a12 * a21) * invDet
        val i01 = (a02 * a21 - a01 * a22) * invDet
        val i02 = (a01 * a12 - a02 * a11) * invDet
        val i10 = (a12 * a20 - a10 * a22) * invDet
        val i11 = (a00 * a22 - a02 * a20) * invDet
        val i12 = (a02 * a10 - a00 * a12) * invDet
        val i20 = (a10 * a21 - a11 * a20) * invDet
        val i21 = (a01 * a20 - a00 * a21) * invDet
        val i22 = (a00 * a11 - a01 * a10) * invDet

        val dstX0 = dst[0].x; val dstX1 = dst[1].x; val dstX2 = dst[2].x
        val dstY0 = dst[0].y; val dstY1 = dst[1].y; val dstY2 = dst[2].y

        // mx = inv * dstX -> [a, b, tx]
        val mxA = i00 * dstX0 + i01 * dstX1 + i02 * dstX2
        val mxB = i10 * dstX0 + i11 * dstX1 + i12 * dstX2
        val mxTx = i20 * dstX0 + i21 * dstX1 + i22 * dstX2

        // my = inv * dstY -> [c, d, ty]
        val myC = i00 * dstY0 + i01 * dstY1 + i02 * dstY2
        val myD = i10 * dstY0 + i11 * dstY1 + i12 * dstY2
        val myTy = i20 * dstY0 + i21 * dstY1 + i22 * dstY2

        return Affine2x3(a = mxA, b = mxB, tx = mxTx, c = myC, d = myD, ty = myTy)
    }

    /** Apply a 2x3 affine produced by [affineFromPointPairs] / [getWarpMatrix] to a point.
     * `out = (a*x + b*y + tx, c*x + d*y + ty)`. */
    fun affine2x3Apply(m: Affine2x3, p: Vec2): Vec2 {
        return Vec2(m.a * p.x + m.b * p.y + m.tx, m.c * p.x + m.d * p.y + m.ty)
    }

    /** Flatten a 2x3 affine to `[a, b, tx, c, d, ty]` (row-major), matching the layout
     * the oracle prints from `cv2.getAffineTransform(...).flatten()`. */
    fun affine2x3Flat(m: Affine2x3): FloatArray {
        return floatArrayOf(m.a, m.b, m.tx, m.c, m.d, m.ty)
    }

    // MARK: - 5. get_warp_matrix

    /** Rotate a 2D point by `angleRad`. Mirrors `_rotate_point`:
     * `[[cos, -sin], [sin, cos]] @ pt`. */
    fun rotatePoint(pt: Vec2, angleRad: Float): Vec2 {
        val sn = sin(angleRad)
        val cs = cos(angleRad)
        return Vec2(cs * pt.x - sn * pt.y, sn * pt.x + cs * pt.y)
    }

    /** `_get_3rd_point(a, b)`: rotate vector `a - b` by 90° CCW about b.
     * `c = b + [-(a-b).y, (a-b).x]`. */
    fun thirdPoint(a: Vec2, b: Vec2): Vec2 {
        val direction = Vec2(a.x - b.x, a.y - b.y)
        return Vec2(b.x - direction.y, b.y + direction.x)
    }

    /** Faithful port of `get_warp_matrix`. `shift` is fixed at (0,0) as in the pose path.
     * `src_w = scale.x`, `dst_w = outputW`, `dst_h = outputH`.
     * `src_dir = rotatePoint([0, -0.5*src_w], rotRad)`, `dst_dir = [0, -0.5*dst_w]`.
     * `inverse` swaps src/dst in the solve (inv=True: dst->src). */
    fun getWarpMatrix(
        center: Vec2, scale: Vec2, rotDeg: Float,
        outputW: Int, outputH: Int, inverse: Boolean
    ): Affine2x3 {
        val srcW = scale.x
        val dstW = outputW.toFloat()
        val dstH = outputH.toFloat()

        val rotRad = rotDeg * kotlin.math.PI.toFloat() / 180.0f
        val srcDir = rotatePoint(Vec2(0f, srcW * -0.5f), rotRad)
        val dstDir = Vec2(0f, dstW * -0.5f)

        // shift = (0, 0): center + scale*shift == center.
        val src0 = center
        val src1 = Vec2(center.x + srcDir.x, center.y + srcDir.y)
        val src2 = thirdPoint(src0, src1)

        val dst0 = Vec2(dstW * 0.5f, dstH * 0.5f)
        val dst1 = Vec2(dst0.x + dstDir.x, dst0.y + dstDir.y)
        val dst2 = thirdPoint(dst0, dst1)

        val src = listOf(src0, src1, src2)
        val dst = listOf(dst0, dst1, dst2)

        return if (inverse) {
            affineFromPointPairs(src = dst, dst = src)
        } else {
            affineFromPointPairs(src = src, dst = dst)
        }
    }

    // MARK: - 7. get_simcc_maximum

    /** Per keypoint k: `x_loc = argmax(simccX[k])`, `y_loc = argmax(simccY[k])`,
     * `val = 0.5*(max(simccX[k]) + max(simccY[k]))`; if `val <= 0` set loc to (-1, -1).
     * Mirrors `get_simcc_maximum` (with the live `vals = 0.5*(x+y)` line, not the commented mask). */
    fun simccMaximum(simccX: Array<FloatArray>, simccY: Array<FloatArray>): SimccResult {
        val k = simccX.size
        val locs = ArrayList<Vec2>(k)
        val vals = FloatArray(k)

        for (i in 0 until k) {
            val (xIdx, xMax) = argmaxWithValue(simccX[i])
            val (yIdx, yMax) = argmaxWithValue(simccY[i])
            val v = 0.5f * (xMax + yMax)
            vals[i] = v
            if (v <= 0f) {
                locs.add(Vec2(-1f, -1f))
            } else {
                locs.add(Vec2(xIdx.toFloat(), yIdx.toFloat()))
            }
        }
        return SimccResult(locs, vals)
    }

    /** numpy `argmax` semantics: returns the first index of the maximum value. */
    private fun argmaxWithValue(row: FloatArray): Pair<Int, Float> {
        var bestIdx = 0
        var bestVal = row[0]
        for (i in 1 until row.size) {
            if (row[i] > bestVal) {
                bestVal = row[i]
                bestIdx = i
            }
        }
        return Pair(bestIdx, bestVal)
    }

    // MARK: - 8. decode keypoints

    /** `kp = locs / splitRatio`; then per axis
     * `kp.x = kp.x/modelInputW*scale.x + center.x - scale.x/2`,
     * `kp.y = kp.y/modelInputH*scale.y + center.y - scale.y/2`.
     * Mirrors `RTMPose.postprocess`'s rescale (`scale`,`center` are the aspect-fixed values). */
    fun decodeKeypoints(
        locs: List<Vec2>, center: Vec2, scale: Vec2,
        modelInputW: Int = 192, modelInputH: Int = 256, simccSplitRatio: Float = 2.0f
    ): List<Vec2> {
        val w = modelInputW.toFloat()
        val h = modelInputH.toFloat()
        return locs.map { loc ->
            val kpX = loc.x / simccSplitRatio
            val kpY = loc.y / simccSplitRatio
            val x = kpX / w * scale.x + center.x - scale.x / 2
            val y = kpY / h * scale.y + center.y - scale.y / 2
            Vec2(x, y)
        }
    }
}

/** Local stand-in for `SIMD2<Float>`. */
data class Vec2(val x: Float, val y: Float)

/** Result of [RtmposeMath.bboxXyxyToCenterScale]. */
data class CenterScale(val center: Vec2, val scale: Vec2)

/** A 2x3 affine matrix: `out = (a*x + b*y + tx, c*x + d*y + ty)`. */
data class Affine2x3(val a: Float, val b: Float, val tx: Float, val c: Float, val d: Float, val ty: Float)

/** Result of [RtmposeMath.simccMaximum]. */
data class SimccResult(val locs: List<Vec2>, val vals: FloatArray)
