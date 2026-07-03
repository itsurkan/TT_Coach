// RtmposeEstimator.swift
//
// Stage 2 of the RTMPose pipeline: top-down keypoint estimation via ONNX Runtime.
// Given a person bounding box (image pixels, from `YoloxDetector`) and the camera
// frame, it produces 17 COCO keypoints in ORIGINAL image-pixel coordinates plus a
// per-keypoint score, by running the RTMPose ONNX model and decoding its SimCC output.
//
// All numeric parity math is delegated to `RTMPoseMath` and all pixel sampling to
// `PixelBufferSampler` — this file only wires preprocess -> inference -> postprocess
// per `RTMPOSE_PARITY.md` "Stage 2 — RTMPose".
//
// MATRIX CONVENTION: `PixelBufferSampler.affineWarpToTensor` samples output->source
// (it applies the matrix to each OUTPUT pixel to find the SOURCE read location). rtmlib
// builds the FORWARD warp (`get_warp_matrix(inv=False)`) and lets `cv2.warpAffine`
// invert it internally for sampling. To match that sampling, we pass the INVERSE
// matrix: `RTMPoseMath.getWarpMatrix(..., inverse: true)`. (Confirmed by reading the
// sampler header + its per-output-pixel `affine2x3Apply` loop.)
//
// Execution provider: RTMPose runs fine on the CoreML EP (unlike YOLOX, whose baked-in
// NMS the CoreML EP rejects), so the session is built with the factory default
// `coreML: true` (CPU fallback inside the factory).

import CoreVideo
import Foundation
import OnnxRuntimeBindings
import simd

struct RtmposeEstimator {

    private let session: ORTSession

    /// COCO-17: number of keypoints the model produces.
    static let keypointCount = 17

    /// Use an already-built session.
    init(session: ORTSession) {
        self.session = session
    }

    /// iOS: build a (CoreML-with-CPU-fallback) session from a bundled `.onnx` resource.
    init(bundleResource name: String, in bundle: Bundle = .main) throws {
        self.session = try ORTSessionFactory.makeSession(bundleResource: name, in: bundle, coreML: true)
    }

    /// CLI: build a session from an absolute file path.
    init(modelPath: String) throws {
        self.session = try ORTSessionFactory.makeSession(modelPath: modelPath, coreML: true)
    }

    /// Estimate 17 COCO keypoints for a single person box.
    ///
    /// Returns keypoints in ORIGINAL image-pixel coordinates and per-keypoint scores
    /// (the raw SimCC `val`; NOT clamped here — the backend/CLI clamps to [0,1] at
    /// output per the parity doc). Returns 17 zero keypoints / zero scores on inference
    /// failure (logged) so callers can treat it like a no-detection frame.
    func estimate(
        pixelBuffer: CVPixelBuffer,
        bbox: BoundingBox,
        imageWidth: Int,
        imageHeight: Int
    ) -> (keypoints: [SIMD2<Float>], scores: [Float]) {
        let empty: ([SIMD2<Float>], [Float]) = (
            Array(repeating: SIMD2<Float>(0, 0), count: Self.keypointCount),
            Array(repeating: 0, count: Self.keypointCount)
        )
        do {
            // 1-2. center + aspect-fixed scale (reused for warp AND decode).
            let (center, rawScale) = RTMPoseMath.bboxXyxyToCenterScale(
                x1: bbox.x1, y1: bbox.y1, x2: bbox.x2, y2: bbox.y2, padding: 1.25)
            let scale = RTMPoseMath.topDownAffineScale(
                scale: rawScale, outputW: RTMPoseMath.poseInputW, outputH: RTMPoseMath.poseInputH)

            // 3. inverse (output->source sampling) warp matrix for the sampler.
            let warp = RTMPoseMath.getWarpMatrix(
                center: center, scale: scale, rotDeg: 0,
                outputW: RTMPoseMath.poseInputW, outputH: RTMPoseMath.poseInputH, inverse: true)

            // 4. warp + normalize into the input tensor (BGR, (v-mean)/std).
            let tensor = try PixelBufferSampler.affineWarpToTensor(
                pixelBuffer: pixelBuffer, warpMatrix: warp,
                outputW: RTMPoseMath.poseInputW, outputH: RTMPoseMath.poseInputH,
                meanBGR: RTMPoseMath.meanBGR, stdBGR: RTMPoseMath.stdBGR)

            // 5. run; read simcc_x / simcc_y by name into [[Float]] (K rows each).
            let inputName = try session.inputNames().first ?? "input"
            let outputNames = try session.outputNames()
            let outputs = try session.run(
                withInputs: [inputName: tensor],
                outputNames: Set(outputNames),
                runOptions: nil
            )
            let simccX = try Self.readSimcc(outputs, preferring: "simcc_x", otherAxisName: "simcc_y")
            let simccY = try Self.readSimcc(outputs, preferring: "simcc_y", otherAxisName: "simcc_x")

            // 6-7. argmax decode -> image pixels (aspect-fixed scale + center).
            let (locs, vals) = RTMPoseMath.simccMaximum(simccX: simccX, simccY: simccY)
            let keypoints = RTMPoseMath.decodeKeypoints(
                locs: locs, center: center, scale: scale,
                modelInputW: RTMPoseMath.poseInputW, modelInputH: RTMPoseMath.poseInputH,
                simccSplitRatio: RTMPoseMath.simccSplitRatio)
            return (keypoints, vals)
        } catch {
            print("[RtmposeEstimator] estimate failed: \(error); returning zero keypoints.")
            return empty
        }
    }

    // MARK: - Output reading

    /// Read a SimCC axis output (`simcc_x` or `simcc_y`) into `K` rows of `[Float]`.
    /// Selects the value by exact name first, then by a name containing the axis suffix
    /// (`_x`/`_y`) while excluding the other axis name. Slices rows using the tensor's
    /// own shape `[1, K, W]` (does not hardcode W). Asserts `K == keypointCount`.
    private static func readSimcc(
        _ outputs: [String: ORTValue], preferring name: String, otherAxisName: String
    ) throws -> [[Float]] {
        guard let value = selectAxis(outputs, name: name, otherAxisName: otherAxisName) else {
            throw RtmposeEstimatorError.missingOutput(name)
        }
        let shape = try value.tensorTypeAndShapeInfo().shape.map { $0.intValue }
        guard shape.count == 3 else {
            throw RtmposeEstimatorError.unexpectedShape(name: name, shape: shape)
        }
        let k = shape[1]
        let w = shape[2]
        guard k == keypointCount else {
            throw RtmposeEstimatorError.unexpectedKeypointCount(name: name, k: k)
        }
        let nsData = try value.tensorData()
        let flat = Data(referencing: nsData).withUnsafeBytes { raw in
            Array(raw.bindMemory(to: Float.self))
        }
        guard flat.count >= k * w else {
            throw RtmposeEstimatorError.unexpectedShape(name: name, shape: shape)
        }
        var rows = [[Float]]()
        rows.reserveCapacity(k)
        for i in 0..<k {
            let start = i * w
            rows.append(Array(flat[start..<(start + w)]))
        }
        return rows
    }

    /// Pick the output for one SimCC axis: exact name match, else a name containing the
    /// axis suffix but not the other axis's name (so `simcc_x` doesn't match `simcc_y`).
    private static func selectAxis(
        _ outputs: [String: ORTValue], name: String, otherAxisName: String
    ) -> ORTValue? {
        if let exact = outputs[name] { return exact }
        let suffix = name.hasSuffix("_x") ? "_x" : (name.hasSuffix("_y") ? "_y" : name)
        return outputs.first { key, _ in
            let lower = key.lowercased()
            return lower.contains(suffix) && !lower.contains(otherAxisName.lowercased())
        }?.value
    }
}

enum RtmposeEstimatorError: Error, CustomStringConvertible {
    case missingOutput(String)
    case unexpectedShape(name: String, shape: [Int])
    case unexpectedKeypointCount(name: String, k: Int)

    var description: String {
        switch self {
        case .missingOutput(let n):
            return "RtmposeEstimator: required output '\(n)' not found."
        case .unexpectedShape(let n, let s):
            return "RtmposeEstimator: output '\(n)' has unexpected shape \(s) (want [1, 17, W])."
        case .unexpectedKeypointCount(let n, let k):
            return "RtmposeEstimator: output '\(n)' has K=\(k) keypoints, expected 17."
        }
    }
}
