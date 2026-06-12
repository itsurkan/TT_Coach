// RTMPoseBackend.swift
//
// The in-app `PoseBackend` that runs the two-stage RTMPose pipeline (YOLOX person
// detection → RTMPose top-down keypoints) per `RTMPOSE_PARITY.md`. It composes
// `YoloxDetector` (CPU EP — its baked-in NMS breaks the CoreML EP) and
// `RtmposeEstimator` (CoreML EP with CPU fallback), then selects the "best person"
// the way the desktop golden does: argmax over detections of MEAN keypoint score
// (NOT detection score), and normalizes that person's keypoints into the shared
// schema-v2 convention (per-axis normalize + clamp).
//
// Output contract (PoseBackend / docs/pose_json_schema_v2.md):
//   - 17 COCO keypoints in `Coco17` order (array index == joint index).
//   - x = clamp(px.x / frameWidth, 0, 1), y = clamp(px.y / frameHeight, 0, 1).
//   - score = clamp(rawScore, 0, 1).
//   - EMPTY array when no person is detected.
//
// The `normalize(...)` and `selectBest(...)` statics are PURE (no ORT, no Core*),
// so they are unit-tested directly; the per-frame `estimatePose` just wires the two
// inference stages to them.

import CoreVideo
import TTCoachShared
import simd

final class RTMPoseBackend: PoseBackend {

    private let detector: YoloxDetector
    private let estimator: RtmposeEstimator

    /// COCO-17 keypoint count (one Keypoint2D per joint, in index order).
    static let keypointCount = RtmposeEstimator.keypointCount

    // MARK: - Init

    /// DI / tests: compose already-built stages (no session loading here).
    init(detector: YoloxDetector, estimator: RtmposeEstimator) {
        self.detector = detector
        self.estimator = estimator
    }

    /// iOS: load both ONNX sessions from app-bundle resources. Throws if either
    /// model/session fails to load — `DrillSessionController` catches and falls
    /// back to `VisionPoseBackend`.
    init(
        yoloxResource: String = "yolox_m_8xb8-300e_humanart-c2c7a14a",
        rtmposeResource: String = "rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504",
        bundle: Bundle = .main
    ) throws {
        // YOLOX on CPU EP, RTMPose on CoreML EP — enforced inside the stage inits.
        self.detector = try YoloxDetector(bundleResource: yoloxResource, in: bundle)
        self.estimator = try RtmposeEstimator(bundleResource: rtmposeResource, in: bundle)
    }

    /// CLI / off-bundle: load both sessions from absolute file paths.
    init(yoloxPath: String, rtmposePath: String) throws {
        self.detector = try YoloxDetector(modelPath: yoloxPath)
        self.estimator = try RtmposeEstimator(modelPath: rtmposePath)
    }

    // MARK: - PoseBackend

    func estimatePose(in pixelBuffer: CVPixelBuffer, frameWidth: Int, frameHeight: Int) -> [Keypoint2D] {
        // Detect EVERY frame for parity with the desktop golden (which detects per
        // frame). M5: optional detector-skip for on-device latency could reuse the
        // previous box on skipped frames — deliberately NOT implemented here.
        let boxes = detector.detect(
            pixelBuffer: pixelBuffer, imageWidth: frameWidth, imageHeight: frameHeight)
        guard !boxes.isEmpty else { return [] }

        // Run RTMPose on EACH box; pick the detection with the highest mean score
        // (matches the export's `best_person`, which selects by pose score not det score).
        var candidates: [(keypoints: [SIMD2<Float>], scores: [Float])] = []
        candidates.reserveCapacity(boxes.count)
        for box in boxes {
            candidates.append(estimator.estimate(
                pixelBuffer: pixelBuffer, bbox: box,
                imageWidth: frameWidth, imageHeight: frameHeight))
        }

        guard let best = Self.selectBest(candidates: candidates) else { return [] }
        let chosen = candidates[best]
        return Self.normalize(
            keypoints: chosen.keypoints, scores: chosen.scores,
            frameWidth: frameWidth, frameHeight: frameHeight)
    }

    // MARK: - Pure, unit-testable helpers

    /// Per-axis normalize + clamp a person's pixel keypoints into the shared schema-v2
    /// convention: `x = clamp(px.x / frameWidth, 0, 1)`, `y = clamp(px.y / frameHeight, 0, 1)`,
    /// `score = clamp(scores[i], 0, 1)`. Emits exactly `keypoints.count` `Keypoint2D` in
    /// order (array index == COCO joint index). Guards against a short `scores` array.
    static func normalize(
        keypoints: [SIMD2<Float>],
        scores: [Float],
        frameWidth: Int,
        frameHeight: Int
    ) -> [Keypoint2D] {
        guard frameWidth > 0, frameHeight > 0 else { return [] }
        let w = Float(frameWidth)
        let h = Float(frameHeight)
        return keypoints.enumerated().map { i, px in
            let rawScore = i < scores.count ? scores[i] : 0
            return Keypoint2D(
                x: clamp01(px.x / w),
                y: clamp01(px.y / h),
                score: clamp01(rawScore)
            )
        }
    }

    /// Argmax over candidates of MEAN keypoint score (the export's `best_person`).
    /// Returns the index of the best candidate, or `nil` when the list is empty.
    /// A candidate with no scores contributes mean 0. Ties resolve to the first
    /// (lowest index) candidate — `>` keeps the earlier one.
    static func selectBest(candidates: [(keypoints: [SIMD2<Float>], scores: [Float])]) -> Int? {
        guard !candidates.isEmpty else { return nil }
        var bestIdx = 0
        var bestMean = meanScore(candidates[0].scores)
        for i in 1..<candidates.count {
            let m = meanScore(candidates[i].scores)
            if m > bestMean {
                bestMean = m
                bestIdx = i
            }
        }
        return bestIdx
    }

    // MARK: - Private

    private static func meanScore(_ scores: [Float]) -> Float {
        guard !scores.isEmpty else { return 0 }
        return scores.reduce(0, +) / Float(scores.count)
    }

    private static func clamp01(_ v: Float) -> Float {
        if v < 0 { return 0 }
        if v > 1 { return 1 }
        return v
    }
}
