// PixelBufferSampler.swift
//
// Shared CVPixelBuffer -> ORT tensor sampling utilities for the RTMPose two-stage
// pipeline. Stage 1 (YOLOX) uses `letterboxToTensor`; Stage 2 (RTMPose, next task)
// uses `affineWarpToTensor`. Both live here so the image-sampling conventions
// (channel order, coordinate mapping, bilinear interpolation) are reviewed in one
// place. The math itself is delegated to `RTMPoseMath` — this file only moves pixels.
//
// COLOR ORDER (see RTMPOSE_PARITY.md "Color order"): the camera/decoded frame is read
// as `kCVPixelFormatType_32BGRA`; in memory each pixel is B, G, R, A. rtmlib feeds
// OpenCV BGR straight through, so the produced CHW tensors are in **BGR** channel
// order (channel 0 = B, 1 = G, 2 = R). Do NOT swap to RGB.
//
// INTERPOLATION CHOICE: bilinear sampling is implemented manually in Float. rtmlib's
// preprocessing uses OpenCV `cv2.resize`/`cv2.warpAffine` with `INTER_LINEAR`; matching
// OpenCV's exact pixel-center convention (src = (dst + 0.5) * scale - 0.5) is far easier
// to get verifiably right with a small hand-written kernel than by wiring up vImage's
// affine/scale tiling (which has its own edge/centering conventions to reconcile). These
// run per detected person per frame, not per pixel-row of video; clarity and parity win
// over the vImage speedup at this milestone (on-device perf is measured later). Small
// sub-pixel deltas vs OpenCV are tolerated by the end-to-end ±1-rep parity gate.

import CoreVideo
import Foundation
import OnnxRuntimeBindings
import simd

enum PixelBufferSamplerError: Error, CustomStringConvertible {
    case unsupportedPixelFormat(OSType)
    case baseAddressUnavailable

    var description: String {
        switch self {
        case .unsupportedPixelFormat(let f):
            return "PixelBufferSampler requires kCVPixelFormatType_32BGRA; got OSType \(f)."
        case .baseAddressUnavailable:
            return "CVPixelBufferGetBaseAddress returned nil."
        }
    }
}

enum PixelBufferSampler {

    /// Letterbox-resize a BGRA frame into a `targetSize x targetSize` (default 640)
    /// CHW float32 tensor, BGR channel order, values 0-255, no mean/std — exactly
    /// `YOLOX.preprocess`. The image is bilinear-resized to `(int(w*ratio), int(h*ratio))`
    /// and pasted at the **top-left** of a canvas filled with 114; the rest stays 114.
    ///
    /// Returns the tensor (shape `[1, 3, targetSize, targetSize]`) plus the `ratio`
    /// used (caller divides detected box coords by it to return to image pixels).
    static func letterboxToTensor(
        pixelBuffer: CVPixelBuffer,
        targetSize: Int = 640
    ) throws -> (tensor: ORTValue, ratio: Float) {
        let (chw, ratio) = try letterboxToCHW(pixelBuffer: pixelBuffer, targetSize: targetSize)
        let tensor = try makeFloatTensor(chw, shape: [1, 3, targetSize, targetSize])
        return (tensor, ratio)
    }

    /// Pure pixel core of `letterboxToTensor` (no ORT types — unit-testable): produces
    /// the flat CHW float32 buffer (BGR, 0-255, length `3*targetSize*targetSize`) and the
    /// `ratio`. The image is bilinear-resized into the top-left; the rest stays 114.
    static func letterboxToCHW(
        pixelBuffer: CVPixelBuffer,
        targetSize: Int = 640
    ) throws -> (chw: [Float], ratio: Float) {
        try withLockedBGRA(pixelBuffer) { src in
            let ratio = RTMPoseMath.letterboxRatio(
                imageWidth: src.width, imageHeight: src.height, target: targetSize)
            let (rw, rh) = RTMPoseMath.letterboxResizedSize(
                imageWidth: src.width, imageHeight: src.height, target: targetSize)

            let plane = targetSize * targetSize
            // CHW, BGR. Channels 0=B,1=G,2=R. Fill the whole canvas with 114 first.
            var chw = [Float](repeating: 114.0, count: 3 * plane)

            // OpenCV INTER_LINEAR resize maps dst -> src by pixel centers.
            let scaleX = Float(src.width) / Float(rw)
            let scaleY = Float(src.height) / Float(rh)

            for dy in 0..<rh {
                let sy = (Float(dy) + 0.5) * scaleY - 0.5
                let rowOff = dy * targetSize
                for dx in 0..<rw {
                    let sx = (Float(dx) + 0.5) * scaleX - 0.5
                    let bgr = src.bilinearBGR(x: sx, y: sy)
                    let p = rowOff + dx
                    chw[p] = bgr.0                 // B plane
                    chw[plane + p] = bgr.1         // G plane
                    chw[2 * plane + p] = bgr.2     // R plane
                }
            }
            return (chw, ratio)
        }
    }

    /// Affine-warp a BGRA frame into an `outputW x outputH` CHW float32 tensor, then
    /// normalize `(v - mean) / std` per channel in **BGR** order — exactly the
    /// `cv2.warpAffine(INTER_LINEAR)` + normalize in `RTMPose.preprocess`.
    ///
    /// MATRIX CONVENTION: `warpMatrix` maps **output (destination) coords -> source
    /// (image) coords** (the inverse/sampling map). For each output pixel `(ox, oy)` the
    /// sampler computes `RTMPoseMath.affine2x3Apply(warpMatrix, (ox, oy))` to find where
    /// to read in the source. Callers produce this with
    /// `RTMPoseMath.getWarpMatrix(..., inverse: true)`. Out-of-bounds source samples read
    /// 0 (OpenCV BORDER_CONSTANT default) before normalization.
    static func affineWarpToTensor(
        pixelBuffer: CVPixelBuffer,
        warpMatrix: simd_float2x3,
        outputW: Int,
        outputH: Int,
        meanBGR: [Float],
        stdBGR: [Float]
    ) throws -> ORTValue {
        let chw = try affineWarpToCHW(
            pixelBuffer: pixelBuffer, warpMatrix: warpMatrix,
            outputW: outputW, outputH: outputH, meanBGR: meanBGR, stdBGR: stdBGR)
        return try makeFloatTensor(chw, shape: [1, 3, outputH, outputW])
    }

    /// Pure pixel core of `affineWarpToTensor` (no ORT types — unit-testable): produces
    /// the flat CHW float32 buffer (BGR, normalized, length `3*outputW*outputH`).
    ///
    /// MATRIX CONVENTION (same as `affineWarpToTensor`): `warpMatrix` maps **output
    /// (destination) coords -> source (image) coords** (the inverse/sampling map). For
    /// each output pixel `(ox, oy)`, `RTMPoseMath.affine2x3Apply(warpMatrix, (ox, oy))`
    /// gives the source read location. Out-of-bounds reads return 0 (OpenCV
    /// BORDER_CONSTANT default) BEFORE normalization. Callers produce the matrix with
    /// `RTMPoseMath.getWarpMatrix(..., inverse: true)`.
    static func affineWarpToCHW(
        pixelBuffer: CVPixelBuffer,
        warpMatrix: simd_float2x3,
        outputW: Int,
        outputH: Int,
        meanBGR: [Float],
        stdBGR: [Float]
    ) throws -> [Float] {
        precondition(meanBGR.count == 3 && stdBGR.count == 3, "meanBGR/stdBGR must have 3 elements")
        return try withLockedBGRA(pixelBuffer) { src in
            let plane = outputW * outputH
            var chw = [Float](repeating: 0, count: 3 * plane)
            let mB = meanBGR[0], mG = meanBGR[1], mR = meanBGR[2]
            let sB = stdBGR[0], sG = stdBGR[1], sR = stdBGR[2]

            for oy in 0..<outputH {
                let rowOff = oy * outputW
                for ox in 0..<outputW {
                    let srcPt = RTMPoseMath.affine2x3Apply(
                        warpMatrix, SIMD2<Float>(Float(ox), Float(oy)))
                    let bgr = src.bilinearBGR(x: srcPt.x, y: srcPt.y)
                    let p = rowOff + ox
                    chw[p] = (bgr.0 - mB) / sB
                    chw[plane + p] = (bgr.1 - mG) / sG
                    chw[2 * plane + p] = (bgr.2 - mR) / sR
                }
            }
            return chw
        }
    }

    // MARK: - Internals

    /// A read-only view over a locked BGRA pixel buffer's base address.
    private struct BGRAView {
        let base: UnsafePointer<UInt8>
        let width: Int
        let height: Int
        let bytesPerRow: Int

        /// Bilinear-sample BGR at fractional `(x, y)`. Neighbors outside the image read
        /// 0 (OpenCV BORDER_CONSTANT). 32BGRA memory layout: byte 0=B,1=G,2=R,3=A.
        @inline(__always)
        func bilinearBGR(x: Float, y: Float) -> (Float, Float, Float) {
            let x0 = Int(floor(x))
            let y0 = Int(floor(y))
            let x1 = x0 + 1
            let y1 = y0 + 1
            let wx = x - Float(x0)
            let wy = y - Float(y0)

            @inline(__always)
            func px(_ xi: Int, _ yi: Int) -> (Float, Float, Float) {
                guard xi >= 0, xi < width, yi >= 0, yi < height else { return (0, 0, 0) }
                let o = yi * bytesPerRow + xi * 4
                return (Float(base[o]), Float(base[o + 1]), Float(base[o + 2]))
            }

            let p00 = px(x0, y0)
            let p10 = px(x1, y0)
            let p01 = px(x0, y1)
            let p11 = px(x1, y1)

            @inline(__always)
            func lerp2(_ a: Float, _ b: Float, _ c: Float, _ d: Float) -> Float {
                let top = a + (b - a) * wx
                let bot = c + (d - c) * wx
                return top + (bot - top) * wy
            }
            return (
                lerp2(p00.0, p10.0, p01.0, p11.0),
                lerp2(p00.1, p10.1, p01.1, p11.1),
                lerp2(p00.2, p10.2, p01.2, p11.2)
            )
        }
    }

    /// Locks the buffer read-only, validates BGRA, and runs `body` with a base-address view.
    private static func withLockedBGRA<T>(
        _ pixelBuffer: CVPixelBuffer,
        _ body: (BGRAView) throws -> T
    ) throws -> T {
        let format = CVPixelBufferGetPixelFormatType(pixelBuffer)
        guard format == kCVPixelFormatType_32BGRA else {
            throw PixelBufferSamplerError.unsupportedPixelFormat(format)
        }
        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }
        guard let raw = CVPixelBufferGetBaseAddress(pixelBuffer) else {
            throw PixelBufferSamplerError.baseAddressUnavailable
        }
        let view = BGRAView(
            base: raw.assumingMemoryBound(to: UInt8.self),
            width: CVPixelBufferGetWidth(pixelBuffer),
            height: CVPixelBufferGetHeight(pixelBuffer),
            bytesPerRow: CVPixelBufferGetBytesPerRow(pixelBuffer)
        )
        return try body(view)
    }

    /// Wrap a CHW float array into an `ORTValue` of the given shape.
    private static func makeFloatTensor(_ floats: [Float], shape: [Int]) throws -> ORTValue {
        let data = NSMutableData(bytes: floats, length: floats.count * MemoryLayout<Float>.stride)
        return try ORTValue(
            tensorData: data,
            elementType: .float,
            shape: shape.map { NSNumber(value: $0) }
        )
    }
}
