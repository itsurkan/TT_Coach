import CoreVideo
import XCTest
import simd

@testable import TTCoach

/// Tests for the RTMPose Stage-2 estimator's deterministic pieces:
///  1. cv2-oracle parity for the affine-warp sampler (`affineWarpToCHW`), and
///  2. the SimCC argmax + decode post-processing wiring (`simccMaximum` +
///     `decodeKeypoints`) the estimator composes — proven without ORT inference
///     (real inference is the CLI parity gate in a later task).
final class RtmposeEstimatorTests: XCTestCase {

    // MARK: - 1. cv2-oracle warp parity
    //
    // Oracle generated with `.venv/bin/python` (cv2.warpAffine, INTER_LINEAR,
    // BORDER_CONSTANT 0, then (v-mean)/std in BGR), see the throwaway script in the
    // task notes. The 16x16 BGRA source uses a known gradient:
    //   B = min(255, 2*x + 10), G = min(255, 3*y + 5), R = min(255, x + y + 20).
    //
    // cv2.warpAffine takes a FORWARD (src->dst) matrix and inverts it internally for
    // sampling. Our sampler is output->source, so we feed the INVERSE of the forward
    // matrix. This catches gross errors (transposed axes, RGB/BGR swap, wrong border,
    // forward/inverse confusion). Tolerance is per-pixel 0.03 in normalized units
    // (~1.7 raw 0-255 levels) to absorb cv2-vs-hand-kernel sub-pixel rounding.

    func testAffineWarpToCHWMatchesCv2Oracle() throws {
        let outW = 8, outH = 8
        let pb = try Self.makeGradientBGRAPixelBuffer(width: 16, height: 16)

        // INVERSE of the forward matrix used by the oracle (row-major [a,b,tx,c,d,ty]).
        // simd_float2x3 column 0 = x-row (a,b,tx), column 1 = y-row (c,d,ty).
        let warp = simd_float2x3(
            SIMD3<Float>(0.6735751295336787, -0.10362694300518134, 2.227979274611399),
            SIMD3<Float>(-0.05181347150259067, 0.7772020725388601, -1.7098445595854923)
        )

        let chw = try PixelBufferSampler.affineWarpToCHW(
            pixelBuffer: pb, warpMatrix: warp, outputW: outW, outputH: outH,
            meanBGR: RTMPoseMath.meanBGR, stdBGR: RTMPoseMath.stdBGR)

        XCTAssertEqual(chw.count, Self.oracleCHW.count)
        var maxErr: Float = 0
        for i in 0..<chw.count {
            maxErr = max(maxErr, abs(chw[i] - Self.oracleCHW[i]))
        }
        XCTAssertLessThan(maxErr, 0.03, "max per-element error vs cv2 oracle = \(maxErr)")
    }

    // MARK: - 2. decode-wiring (simccMaximum + decodeKeypoints), no ORT
    //
    // Mirrors the estimator's postprocess composition: feed one-hot SimCC peaks at
    // known bins through `simccMaximum` then `decodeKeypoints` with a known
    // center/scale, and assert the image-pixel keypoints.

    func testDecodeWiringFromSyntheticSimcc() {
        let k = 17
        let wx = 384  // simcc_x width (192 * 2)
        let wy = 512  // simcc_y width (256 * 2)

        // Keypoint 0: peak at x-bin 192, y-bin 256 -> loc (192,256) -> /2 = (96,128),
        // which is the CENTER of the 192x256 input box.
        var simccX = Array(repeating: [Float](repeating: 0, count: wx), count: k)
        var simccY = Array(repeating: [Float](repeating: 0, count: wy), count: k)
        for i in 0..<k {
            // distinct peaks per keypoint to make sure rows are independent
            let xb = 192
            let yb = 256
            simccX[i][xb] = 1.0
            simccY[i][yb] = 1.0
        }
        // Keypoint 1: a different, off-center peak.
        simccX[1] = [Float](repeating: 0, count: wx); simccX[1][0] = 2.0
        simccY[1] = [Float](repeating: 0, count: wy); simccY[1][0] = 2.0

        let (locs, vals) = RTMPoseMath.simccMaximum(simccX: simccX, simccY: simccY)

        // val = 0.5*(1+1) = 1 for keypoint 0; 0.5*(2+2)=2 for keypoint 1.
        XCTAssertEqual(vals[0], 1.0, accuracy: 1e-6)
        XCTAssertEqual(vals[2], 1.0, accuracy: 1e-6)
        XCTAssertEqual(vals[1], 2.0, accuracy: 1e-6)
        XCTAssertEqual(locs[0], SIMD2<Float>(192, 256))
        XCTAssertEqual(locs[1], SIMD2<Float>(0, 0))

        // Known center/scale. With center at the box center, a loc at the input center
        // (96,128 after /2) should decode back to `center`.
        let center = SIMD2<Float>(500, 300)
        let scale = SIMD2<Float>(192, 256)  // already aspect-correct (0.75)

        let kps = RTMPoseMath.decodeKeypoints(
            locs: locs, center: center, scale: scale,
            modelInputW: 192, modelInputH: 256, simccSplitRatio: 2.0)

        // Keypoint 0: kp = (96,128); x = 96/192*192 + 500 - 96 = 96+500-96 = 500;
        //             y = 128/256*256 + 300 - 128 = 128+300-128 = 300 -> == center.
        XCTAssertEqual(kps[0].x, center.x, accuracy: 1e-3)
        XCTAssertEqual(kps[0].y, center.y, accuracy: 1e-3)

        // Keypoint 1: loc (0,0) -> kp (0,0); x = 0 + 500 - 96 = 404; y = 0 + 300 - 128 = 172.
        XCTAssertEqual(kps[1].x, 404, accuracy: 1e-3)
        XCTAssertEqual(kps[1].y, 172, accuracy: 1e-3)
    }

    // MARK: - helpers

    /// 16x16 32BGRA buffer with the same gradient the cv2 oracle used.
    static func makeGradientBGRAPixelBuffer(width: Int, height: Int) throws -> CVPixelBuffer {
        var pb: CVPixelBuffer?
        let attrs: [CFString: Any] = [kCVPixelBufferCGImageCompatibilityKey: true]
        let status = CVPixelBufferCreate(
            kCFAllocatorDefault, width, height,
            kCVPixelFormatType_32BGRA, attrs as CFDictionary, &pb)
        guard status == kCVReturnSuccess, let buffer = pb else {
            throw NSError(domain: "test", code: Int(status),
                          userInfo: [NSLocalizedDescriptionKey: "CVPixelBufferCreate failed"])
        }
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        let base = CVPixelBufferGetBaseAddress(buffer)!.assumingMemoryBound(to: UInt8.self)
        let bpr = CVPixelBufferGetBytesPerRow(buffer)
        for y in 0..<height {
            for x in 0..<width {
                let o = y * bpr + x * 4
                base[o] = UInt8(min(255, 2 * x + 10))     // B
                base[o + 1] = UInt8(min(255, 3 * y + 5))  // G
                base[o + 2] = UInt8(min(255, x + y + 20)) // R
                base[o + 3] = 255
            }
        }
        return buffer
    }

    /// CHW (B,G,R planes) flat float32 from the cv2 oracle for the 8x8 warp above.
    static let oracleCHW: [Float] = [
        -2.117904, -2.117904, -2.117904, -2.117904, -2.117904, -2.117904, -2.117904, -2.117904,
        -2.100779, -2.117904, -2.117904, -2.117904, -2.117904, -2.117904, -2.117904, -2.117904,
        -1.912407, -1.912407, -1.895282, -1.912407, -1.912407, -1.912407, -1.912407, -1.929532,
        -1.878157, -1.861033, -1.826783, -1.809658, -1.792534, -1.758284, -1.741159, -1.724035,
        -1.878157, -1.861033, -1.843908, -1.809658, -1.792534, -1.775409, -1.741159, -1.724035,
        -1.895282, -1.861033, -1.843908, -1.826783, -1.792534, -1.775409, -1.741159, -1.724035,
        -1.895282, -1.861033, -1.843908, -1.826783, -1.792534, -1.775409, -1.758284, -1.724035,
        -1.895282, -1.878157, -1.843908, -1.826783, -1.809658, -1.775409, -1.758284, -1.741159,
        -2.035714, -2.035714, -2.035714, -2.035714, -2.035714, -2.035714, -2.035714, -2.035714,
        -2.035714, -2.035714, -2.035714, -2.035714, -2.035714, -2.035714, -2.035714, -2.035714,
        -1.965686, -1.965686, -1.965686, -1.983193, -1.983193, -1.983193, -1.983193, -2.000700,
        -1.913165, -1.913165, -1.913165, -1.930672, -1.930672, -1.930672, -1.930672, -1.930672,
        -1.878151, -1.878151, -1.878151, -1.878151, -1.878151, -1.895658, -1.895658, -1.895658,
        -1.825630, -1.843137, -1.843137, -1.843137, -1.843137, -1.843137, -1.843137, -1.860644,
        -1.790616, -1.790616, -1.790616, -1.808123, -1.808123, -1.808123, -1.808123, -1.808123,
        -1.755602, -1.755602, -1.755602, -1.755602, -1.755602, -1.773109, -1.773109, -1.773109,
        -1.804444, -1.804444, -1.804444, -1.804444, -1.804444, -1.804444, -1.804444, -1.804444,
        -1.787015, -1.787015, -1.804444, -1.804444, -1.804444, -1.804444, -1.804444, -1.804444,
        -1.473290, -1.490719, -1.490719, -1.508148, -1.543007, -1.543007, -1.560436, -1.577865,
        -1.403573, -1.403573, -1.386144, -1.386144, -1.368715, -1.351285, -1.351285, -1.333856,
        -1.403573, -1.386144, -1.386144, -1.368715, -1.351285, -1.351285, -1.333856, -1.316427,
        -1.386144, -1.368715, -1.368715, -1.351285, -1.351285, -1.333856, -1.316427, -1.316427,
        -1.368715, -1.368715, -1.351285, -1.351285, -1.333856, -1.316427, -1.316427, -1.298998,
        -1.368715, -1.351285, -1.351285, -1.333856, -1.316427, -1.316427, -1.298998, -1.281569,
    ]
}
