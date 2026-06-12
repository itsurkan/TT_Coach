// YoloxDetector.swift
//
// Stage 1 of the RTMPose pipeline: YOLOX person detection via ONNX Runtime. The ONNX
// is an mmdeploy export with NMS baked into the graph (see RTMPOSE_PARITY.md). Its
// `dets` output is `[1, N, 5]` float — rows `[x1, y1, x2, y2, score]` in 640-input
// pixel space, already NMS-filtered and score-descending — and a `labels` `[1, N]`
// int64 output that rtmlib (and we) IGNORE.
//
// Postprocess is just rtmlib's `shape[-1] == 5` branch: divide box coords by the
// letterbox `ratio` (back to image pixels) and keep `score > 0.3`. No grid decode,
// no manual NMS.
//
// The session MUST be CPU-only: the baked-in NMS has a dynamic-shape node the CoreML
// EP rejects (hard-fails when N=0). Built via `ORTSessionFactory.makeSession(..., coreML: false)`.

import CoreVideo
import Foundation
import OnnxRuntimeBindings

/// A detected person box in ORIGINAL image pixel coordinates.
struct BoundingBox: Equatable {
    let x1: Float
    let y1: Float
    let x2: Float
    let y2: Float
    let score: Float
}

struct YoloxDetector {

    private let session: ORTSession
    private let scoreThreshold: Float

    /// Use an already-built (CPU-only) session.
    init(session: ORTSession, scoreThreshold: Float = RTMPoseMath.detScoreThreshold) {
        self.session = session
        self.scoreThreshold = scoreThreshold
    }

    /// iOS: build a CPU-only session from a bundled `.onnx` resource.
    init(
        bundleResource name: String,
        in bundle: Bundle = .main,
        scoreThreshold: Float = RTMPoseMath.detScoreThreshold
    ) throws {
        self.session = try ORTSessionFactory.makeSession(bundleResource: name, in: bundle, coreML: false)
        self.scoreThreshold = scoreThreshold
    }

    /// CLI: build a CPU-only session from an absolute file path.
    init(modelPath: String, scoreThreshold: Float = RTMPoseMath.detScoreThreshold) throws {
        self.session = try ORTSessionFactory.makeSession(modelPath: modelPath, coreML: false)
        self.scoreThreshold = scoreThreshold
    }

    /// Detect persons in a BGRA frame. Returns boxes in original image pixels,
    /// score-descending. Returns `[]` when no person clears the score threshold,
    /// when the model yields no detections, or on any inference error (logged).
    func detect(pixelBuffer: CVPixelBuffer, imageWidth: Int, imageHeight: Int) -> [BoundingBox] {
        do {
            let (tensor, ratio) = try PixelBufferSampler.letterboxToTensor(
                pixelBuffer: pixelBuffer, targetSize: RTMPoseMath.detInput)

            let inputName = try session.inputNames().first ?? "input"
            let outputNames = try session.outputNames()
            let outputs = try session.run(
                withInputs: [inputName: tensor],
                outputNames: Set(outputNames),
                runOptions: nil
            )

            guard let detsValue = try Self.selectDetsValue(outputs) else {
                print("[YoloxDetector] no float [..,5] 'dets' output found; returning [].")
                return []
            }
            let shape = try detsValue.tensorTypeAndShapeInfo().shape.map { $0.intValue }
            let boxCount = shape.count >= 2 ? shape[1] : 0
            guard boxCount > 0 else { return [] }

            let nsData = try detsValue.tensorData()
            let flat = Data(referencing: nsData).withUnsafeBytes { raw in
                Array(raw.bindMemory(to: Float.self))
            }
            return Self.parseDets(
                detsFlat: flat, boxCount: boxCount, ratio: ratio, scoreThreshold: scoreThreshold)
        } catch {
            print("[YoloxDetector] detect failed: \(error); returning [].")
            return []
        }
    }

    // MARK: - Pure, unit-testable postprocess

    /// rtmlib `YOLOX.postprocess` (`shape[-1] == 5` branch). For each of `boxCount`
    /// rows `[x1, y1, x2, y2, score]`: divide the 4 coords by `ratio` (back to image
    /// pixels), keep rows with `score > scoreThreshold`. Input is already score-
    /// descending, so order is preserved. Tolerates `boxCount == 0` (returns []).
    static func parseDets(
        detsFlat: [Float],
        boxCount: Int,
        ratio: Float,
        scoreThreshold: Float = RTMPoseMath.detScoreThreshold
    ) -> [BoundingBox] {
        guard boxCount > 0, detsFlat.count >= boxCount * 5, ratio > 0 else { return [] }
        var boxes: [BoundingBox] = []
        boxes.reserveCapacity(boxCount)
        for i in 0..<boxCount {
            let base = i * 5
            let score = detsFlat[base + 4]
            guard score > scoreThreshold else { continue }
            boxes.append(BoundingBox(
                x1: detsFlat[base] / ratio,
                y1: detsFlat[base + 1] / ratio,
                x2: detsFlat[base + 2] / ratio,
                y2: detsFlat[base + 3] / ratio,
                score: score
            ))
        }
        return boxes
    }

    /// Identify the `dets` output by name/shape (do not assume index): prefer an
    /// output whose name contains "det"; otherwise the first float-typed output whose
    /// last dimension is 5. `labels` (int64) is ignored.
    private static func selectDetsValue(_ outputs: [String: ORTValue]) throws -> ORTValue? {
        if let named = outputs.first(where: { $0.key.lowercased().contains("det") })?.value {
            return named
        }
        for (_, value) in outputs {
            let info = try value.tensorTypeAndShapeInfo()
            let shape = info.shape.map { $0.intValue }
            if info.elementType == .float, let last = shape.last, last == 5 {
                return value
            }
        }
        return nil
    }
}
