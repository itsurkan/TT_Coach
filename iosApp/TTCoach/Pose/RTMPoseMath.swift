import Foundation
import simd

/// Pure, platform-free parity math for the RTMPose two-stage pipeline.
///
/// Every function here mirrors a specific `rtmlib` routine numerically so that the iOS
/// `RTMPoseBackend` produces the same keypoints as the desktop golden
/// (`scripts/poses/export_poses_rtmpose.py`). No ONNX, no image sampling, no Vision here —
/// only deterministic functions on numbers/arrays. See `RTMPOSE_PARITY.md` for the contract.
///
/// References point at the exact rtmlib source:
///   - `rtmlib/tools/pose_estimation/pre_processings.py`
///   - `rtmlib/tools/pose_estimation/post_processings.py`
///   - `rtmlib/tools/object_detection/yolox.py`
enum RTMPoseMath {

    // MARK: - Constants

    /// RTMPose normalization mean, applied to channels in **B, G, R order** (rtmlib quirk:
    /// an RGB-valued mean is subtracted from BGR channels — copy it, do not "fix" it).
    static let meanBGR: [Float] = [123.675, 116.28, 103.53]

    /// RTMPose normalization std, applied to channels in **B, G, R order** (see `meanBGR`).
    static let stdBGR: [Float] = [58.395, 57.12, 57.375]

    /// SimCC split ratio (`simcc_split_ratio` in rtmlib): heatmap resolution / input resolution.
    static let simccSplitRatio: Float = 2.0

    /// YOLOX detection score threshold (`final_scores > 0.3` branch in `YOLOX.postprocess`).
    static let detScoreThreshold: Float = 0.3

    /// RTMPose model input width (`model_input_size[0]`).
    static let poseInputW: Int = 192

    /// RTMPose model input height (`model_input_size[1]`).
    static let poseInputH: Int = 256

    /// YOLOX square input size.
    static let detInput: Int = 640

    /// RTMPose input aspect ratio = w / h = 192 / 256 = 0.75.
    static let poseAspectRatio: Float = Float(poseInputW) / Float(poseInputH)

    // MARK: - 1. YOLOX letterbox (preprocess `ratio` only — no NMS/decode, that is baked in)

    /// `ratio = min(target / h, target / w)`. Mirrors `YOLOX.preprocess`.
    static func letterboxRatio(imageWidth: Int, imageHeight: Int, target: Int = 640) -> Float {
        let t = Float(target)
        return min(t / Float(imageHeight), t / Float(imageWidth))
    }

    /// Resized size pasted top-left on the 640x640 canvas.
    /// `(int(imgW*ratio), int(imgH*ratio))` — truncation, matching numpy `int()`.
    static func letterboxResizedSize(imageWidth: Int, imageHeight: Int, target: Int = 640) -> (w: Int, h: Int) {
        let ratio = letterboxRatio(imageWidth: imageWidth, imageHeight: imageHeight, target: target)
        // Int(_:) on a positive Float truncates toward zero, matching numpy int().
        return (Int(Float(imageWidth) * ratio), Int(Float(imageHeight) * ratio))
    }

    // MARK: - 2. bbox xyxy -> center, scale

    /// `bbox_xyxy2cs`: center = ((x1+x2)/2, (y1+y2)/2), scale = ((x2-x1)*padding, (y2-y1)*padding).
    static func bboxXyxyToCenterScale(
        x1: Float, y1: Float, x2: Float, y2: Float, padding: Float = 1.25
    ) -> (center: SIMD2<Float>, scale: SIMD2<Float>) {
        let center = SIMD2<Float>((x1 + x2) * 0.5, (y1 + y2) * 0.5)
        let scale = SIMD2<Float>((x2 - x1) * padding, (y2 - y1) * padding)
        return (center, scale)
    }

    // MARK: - 3. aspect-ratio fix

    /// `top_down_affine`'s reshape step. aspectRatio = w/h (= 0.75 for RTMPose).
    /// if scale.x > scale.y*aspect { (scale.x, scale.x/aspect) } else { (scale.y*aspect, scale.y) }
    static func fixAspectRatio(scale: SIMD2<Float>, aspectRatio: Float) -> SIMD2<Float> {
        if scale.x > scale.y * aspectRatio {
            return SIMD2<Float>(scale.x, scale.x / aspectRatio)
        } else {
            return SIMD2<Float>(scale.y * aspectRatio, scale.y)
        }
    }

    /// Convenience: the aspect-fix step inside `top_down_affine`, returning the adjusted
    /// bbox_scale (the warp itself is platform code elsewhere). aspect = outputW/outputH.
    static func topDownAffineScale(scale: SIMD2<Float>, outputW: Int, outputH: Int) -> SIMD2<Float> {
        let aspect = Float(outputW) / Float(outputH)
        return fixAspectRatio(scale: scale, aspectRatio: aspect)
    }

    // MARK: - 4. affine from three point pairs (cv2.getAffineTransform)

    /// Solve the 2x3 matrix M so that for each i, `dst[i] = M * [src[i].x, src[i].y, 1]`.
    /// Equivalent to `cv2.getAffineTransform(src, dst)` — exactly 3 point pairs.
    ///
    /// The x-row and y-row share the same 3x3 coefficient matrix
    /// `A = [[sx0, sy0, 1], [sx1, sy1, 1], [sx2, sy2, 1]]`; solve A·mx = dstX and A·my = dstY.
    /// Returns a column-major `simd_float2x3` whose `[c][r]` access matches matrix entry (r,c),
    /// i.e. columns = (a, b, tx)/(c, d, ty) split per row below.
    static func affineFromPointPairs(src: [SIMD2<Float>], dst: [SIMD2<Float>]) -> simd_float2x3 {
        precondition(src.count == 3 && dst.count == 3, "affineFromPointPairs requires exactly 3 point pairs")

        let a = simd_float3x3(rows: [
            SIMD3<Float>(src[0].x, src[0].y, 1),
            SIMD3<Float>(src[1].x, src[1].y, 1),
            SIMD3<Float>(src[2].x, src[2].y, 1),
        ])
        let inv = a.inverse
        let mx = inv * SIMD3<Float>(dst[0].x, dst[1].x, dst[2].x) // [a, b, tx]
        let my = inv * SIMD3<Float>(dst[0].y, dst[1].y, dst[2].y) // [c, d, ty]

        // simd_float2x3 has 2 columns of 3 rows. We store row 0 (x) in column 0,
        // row 1 (y) in column 1 — callers read via `affine2x3Apply` below.
        return simd_float2x3(SIMD3<Float>(mx.x, mx.y, mx.z),
                             SIMD3<Float>(my.x, my.y, my.z))
    }

    /// Apply a 2x3 affine produced by `affineFromPointPairs` / `getWarpMatrix` to a point.
    /// `out = (a*x + b*y + tx, c*x + d*y + ty)`.
    static func affine2x3Apply(_ m: simd_float2x3, _ p: SIMD2<Float>) -> SIMD2<Float> {
        let rx = m.columns.0 // (a, b, tx)
        let ry = m.columns.1 // (c, d, ty)
        return SIMD2<Float>(rx.x * p.x + rx.y * p.y + rx.z,
                            ry.x * p.x + ry.y * p.y + ry.z)
    }

    /// Flatten a 2x3 affine to `[a, b, tx, c, d, ty]` (row-major), matching the layout
    /// the oracle prints from `cv2.getAffineTransform(...).flatten()`.
    static func affine2x3Flat(_ m: simd_float2x3) -> [Float] {
        let rx = m.columns.0
        let ry = m.columns.1
        return [rx.x, rx.y, rx.z, ry.x, ry.y, ry.z]
    }

    // MARK: - 5. get_warp_matrix

    /// Rotate a 2D point by `angleRad`. Mirrors `_rotate_point`:
    /// `[[cos, -sin], [sin, cos]] @ pt`.
    static func rotatePoint(_ pt: SIMD2<Float>, _ angleRad: Float) -> SIMD2<Float> {
        let sn = sin(angleRad)
        let cs = cos(angleRad)
        return SIMD2<Float>(cs * pt.x - sn * pt.y, sn * pt.x + cs * pt.y)
    }

    /// `_get_3rd_point(a, b)`: rotate vector `a - b` by 90° CCW about b.
    /// `c = b + [-(a-b).y, (a-b).x]`.
    static func thirdPoint(_ a: SIMD2<Float>, _ b: SIMD2<Float>) -> SIMD2<Float> {
        let direction = a - b
        return SIMD2<Float>(b.x - direction.y, b.y + direction.x)
    }

    /// Faithful port of `get_warp_matrix`. `shift` is fixed at (0,0) as in the pose path.
    /// `src_w = scale.x`, `dst_w = outputW`, `dst_h = outputH`.
    /// `src_dir = rotatePoint([0, -0.5*src_w], rotRad)`, `dst_dir = [0, -0.5*dst_w]`.
    /// `inverse` swaps src/dst in the solve (inv=True: dst->src).
    static func getWarpMatrix(
        center: SIMD2<Float>, scale: SIMD2<Float>, rotDeg: Float,
        outputW: Int, outputH: Int, inverse: Bool
    ) -> simd_float2x3 {
        let srcW = scale.x
        let dstW = Float(outputW)
        let dstH = Float(outputH)

        let rotRad = rotDeg * Float.pi / 180.0
        let srcDir = rotatePoint(SIMD2<Float>(0, srcW * -0.5), rotRad)
        let dstDir = SIMD2<Float>(0, dstW * -0.5)

        // shift = (0, 0): center + scale*shift == center.
        let src0 = center
        let src1 = center + srcDir
        let src2 = thirdPoint(src0, src1)

        let dst0 = SIMD2<Float>(dstW * 0.5, dstH * 0.5)
        let dst1 = dst0 + dstDir
        let dst2 = thirdPoint(dst0, dst1)

        let src = [src0, src1, src2]
        let dst = [dst0, dst1, dst2]

        if inverse {
            return affineFromPointPairs(src: dst, dst: src)
        } else {
            return affineFromPointPairs(src: src, dst: dst)
        }
    }

    // MARK: - 7. get_simcc_maximum

    /// Per keypoint k: `x_loc = argmax(simccX[k])`, `y_loc = argmax(simccY[k])`,
    /// `val = 0.5*(max(simccX[k]) + max(simccY[k]))`; if `val <= 0` set loc to (-1, -1).
    /// Mirrors `get_simcc_maximum` (with the live `vals = 0.5*(x+y)` line, not the commented mask).
    static func simccMaximum(
        simccX: [[Float]], simccY: [[Float]]
    ) -> (locs: [SIMD2<Float>], vals: [Float]) {
        let k = simccX.count
        var locs = [SIMD2<Float>](repeating: SIMD2<Float>(0, 0), count: k)
        var vals = [Float](repeating: 0, count: k)

        for i in 0..<k {
            let (xIdx, xMax) = argmaxWithValue(simccX[i])
            let (yIdx, yMax) = argmaxWithValue(simccY[i])
            let v = 0.5 * (xMax + yMax)
            vals[i] = v
            if v <= 0 {
                locs[i] = SIMD2<Float>(-1, -1)
            } else {
                locs[i] = SIMD2<Float>(Float(xIdx), Float(yIdx))
            }
        }
        return (locs, vals)
    }

    /// numpy `argmax` semantics: returns the first index of the maximum value.
    private static func argmaxWithValue(_ row: [Float]) -> (index: Int, value: Float) {
        var bestIdx = 0
        var bestVal = row[0]
        for i in 1..<row.count where row[i] > bestVal {
            bestVal = row[i]
            bestIdx = i
        }
        return (bestIdx, bestVal)
    }

    // MARK: - 8. decode keypoints

    /// `kp = locs / splitRatio`; then per axis
    /// `kp.x = kp.x/modelInputW*scale.x + center.x - scale.x/2`,
    /// `kp.y = kp.y/modelInputH*scale.y + center.y - scale.y/2`.
    /// Mirrors `RTMPose.postprocess`'s rescale (`scale`,`center` are the aspect-fixed values).
    static func decodeKeypoints(
        locs: [SIMD2<Float>], center: SIMD2<Float>, scale: SIMD2<Float>,
        modelInputW: Int = 192, modelInputH: Int = 256, simccSplitRatio: Float = 2.0
    ) -> [SIMD2<Float>] {
        let w = Float(modelInputW)
        let h = Float(modelInputH)
        return locs.map { loc in
            let kp = loc / simccSplitRatio
            let x = kp.x / w * scale.x + center.x - scale.x / 2
            let y = kp.y / h * scale.y + center.y - scale.y / 2
            return SIMD2<Float>(x, y)
        }
    }
}
