// RTMPoseExport — macOS CLI: video → schema-v2 pose JSON via the RTMPose two-stage
// pipeline (YOLOX person detection → RTMPose top-down keypoints).
//
// This runs the SAME pipeline the in-app `RTMPoseBackend` runs (detect → estimate
// each box → best_person by mean keypoint score → normalize+clamp), producing the
// artifact the parity gate checks against the desktop golden `_poses_rtm.json`.
//
// Usage:
//   RTMPoseExport <video_path> [<yolox.onnx> <rtmpose.onnx>]
// Output: <video_base>_poses_vision_rtm.json in the video's directory.
//
// TTCoachShared decision: this macOS tool does NOT import TTCoachShared or
// RTMPoseBackend. TTCoachShared is a KMP framework built only for iOS slices via
// gradle; pulling it (and RTMPoseBackend, which imports it) into a macOS tool target
// is fragile. Instead the CLI composes `YoloxDetector` + `RtmposeEstimator` directly
// and replicates the small pure normalize/select + JSON-writing logic here. The JSON
// writer mirrors VisionPoseExport exactly (field order the strict PoseJsonV2Parser
// requires), changing only `model` and the keypoint source.

import AVFoundation
import CoreVideo
import Foundation
import simd

let schemaVersion = 2
let coco17NumKeypoints = RtmposeEstimator.keypointCount

struct PoseLandmark {
    let x: Float      // normalized + clamped [0,1]
    let y: Float
    let score: Float
}

struct PoseFrame {
    let frameIndex: Int
    let timestampMs: Int64
    let landmarks: [PoseLandmark]
}

/// Formats a float with exactly 4 decimal places (schema v2 contract). Matches the
/// Python golden's `round(.., 4)` (which JSON-serializes to up to 4 places); fixed
/// 4-place formatting is a superset the strict parser accepts.
func fmt4(_ value: Float) -> String {
    return String(format: "%.4f", value)
}

func clamp01(_ v: Float) -> Float {
    if v < 0 { return 0 }
    if v > 1 { return 1 }
    return v
}

// MARK: - Frame extraction (keeps CVPixelBuffer; mirrors VisionPoseExport sorting)

/// Extracts all frames from a video using AVAssetReader as 32BGRA CVPixelBuffers,
/// sorted by presentation time, each tagged with its REAL presentation timestamp (ms).
/// (An earlier version used a synthetic `frameIndex * intervalMs` time base; that drifts
/// on videos whose fps doesn't divide the interval evenly — e.g. andrii_1 @59.27fps — so
/// we keep the true PTS the decoder reports. The stroke detector consumes per-frame
/// timestamps + a median intervalMs, both honest under real PTS.)
func extractFrames(from videoPath: String) -> [(buffer: CVPixelBuffer, timestampMs: Int64)] {
    let url = URL(fileURLWithPath: videoPath)
    let asset = AVAsset(url: url)

    guard let reader = try? AVAssetReader(asset: asset) else {
        fputs("ERROR: cannot create AVAssetReader for \(videoPath)\n", stderr)
        exit(1)
    }
    guard let videoTrack = asset.tracks(withMediaType: .video).first else {
        fputs("ERROR: no video track found in \(videoPath)\n", stderr)
        exit(1)
    }

    let settings: [String: Any] = [
        kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
    ]
    let output = AVAssetReaderTrackOutput(track: videoTrack, outputSettings: settings)
    reader.add(output)

    guard reader.startReading() else {
        fputs("ERROR: failed to start reading \(videoPath)\n", stderr)
        exit(1)
    }

    var raw: [(buffer: CVPixelBuffer, ptsMs: Int64)] = []
    var count = 0
    while let sampleBuffer = output.copyNextSampleBuffer() {
        guard let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { continue }
        let pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        let ptsMs = Int64(pts.value) * 1000 / Int64(pts.timescale)
        // copyNextSampleBuffer's image buffer is owned by the sample buffer; retain a
        // deep copy so it survives past this iteration. CVPixelBuffer is CF-retained
        // here by holding the sample-buffer-backed buffer in the array — but the sample
        // buffer is released at loop end, so copy the pixels into an independent buffer.
        if let copy = deepCopyPixelBuffer(imageBuffer) {
            raw.append((buffer: copy, ptsMs: ptsMs))
        }
        count += 1
        if count % 10 == 0 {
            fputs("  frame \(count) t=\(ptsMs) ms\n", stderr)
        }
    }
    reader.cancelReading()

    // Sort by PTS (decode order != presentation order with B-frames). Keep the real
    // presentation timestamp for each frame — no synthetic resampling.
    raw.sort { $0.ptsMs < $1.ptsMs }
    return raw.map { (buffer: $0.buffer, timestampMs: $0.ptsMs) }
}

/// Makes an independent 32BGRA copy of a pixel buffer (the reader recycles the source).
func deepCopyPixelBuffer(_ src: CVPixelBuffer) -> CVPixelBuffer? {
    let width = CVPixelBufferGetWidth(src)
    let height = CVPixelBufferGetHeight(src)
    let format = CVPixelBufferGetPixelFormatType(src)

    var dst: CVPixelBuffer?
    let attrs: [String: Any] = [
        kCVPixelBufferIOSurfacePropertiesKey as String: [:]
    ]
    let status = CVPixelBufferCreate(
        kCFAllocatorDefault, width, height, format, attrs as CFDictionary, &dst)
    guard status == kCVReturnSuccess, let out = dst else { return nil }

    CVPixelBufferLockBaseAddress(src, .readOnly)
    CVPixelBufferLockBaseAddress(out, [])
    defer {
        CVPixelBufferUnlockBaseAddress(out, [])
        CVPixelBufferUnlockBaseAddress(src, .readOnly)
    }

    let srcBytesPerRow = CVPixelBufferGetBytesPerRow(src)
    let dstBytesPerRow = CVPixelBufferGetBytesPerRow(out)
    guard let srcBase = CVPixelBufferGetBaseAddress(src),
          let dstBase = CVPixelBufferGetBaseAddress(out) else { return nil }

    let copyBytesPerRow = min(srcBytesPerRow, dstBytesPerRow)
    for row in 0..<height {
        memcpy(
            dstBase.advanced(by: row * dstBytesPerRow),
            srcBase.advanced(by: row * srcBytesPerRow),
            copyBytesPerRow)
    }
    return out
}

// MARK: - Pipeline (same as RTMPoseBackend, composed directly)

/// Run detect → estimate-per-box → best_person → normalize+clamp+round for one frame.
/// Returns 17 landmarks, or [] when no person is detected.
func runPipeline(
    detector: YoloxDetector,
    estimator: RtmposeEstimator,
    pixelBuffer: CVPixelBuffer,
    frameWidth: Int,
    frameHeight: Int
) -> [PoseLandmark] {
    let boxes = detector.detect(
        pixelBuffer: pixelBuffer, imageWidth: frameWidth, imageHeight: frameHeight)
    guard !boxes.isEmpty else { return [] }

    var candidates: [(keypoints: [SIMD2<Float>], scores: [Float])] = []
    candidates.reserveCapacity(boxes.count)
    for box in boxes {
        candidates.append(estimator.estimate(
            pixelBuffer: pixelBuffer, bbox: box,
            imageWidth: frameWidth, imageHeight: frameHeight))
    }

    // best_person = argmax mean keypoint score.
    var bestIdx = 0
    var bestMean: Float = meanScore(candidates[0].scores)
    for i in 1..<candidates.count {
        let m = meanScore(candidates[i].scores)
        if m > bestMean { bestMean = m; bestIdx = i }
    }

    let chosen = candidates[bestIdx]
    let w = Float(frameWidth)
    let h = Float(frameHeight)
    return chosen.keypoints.enumerated().map { i, px in
        let raw = i < chosen.scores.count ? chosen.scores[i] : 0
        return PoseLandmark(
            x: clamp01(px.x / w),
            y: clamp01(px.y / h),
            score: clamp01(raw))
    }
}

func meanScore(_ scores: [Float]) -> Float {
    guard !scores.isEmpty else { return 0 }
    return scores.reduce(0, +) / Float(scores.count)
}

// MARK: - JSON writer (mirrors VisionPoseExport field order exactly)

func buildPoseJSONString(
    videoName: String,
    videoWidth: Int,
    videoHeight: Int,
    videoDurationMs: Int64,
    frames: [PoseFrame]
) -> String {
    let intervalMs = frames.count > 1
        ? Int((Double(videoDurationMs) / Double(frames.count - 1)).rounded())
        : 1
    var out = "{\n"
    out += "  \"schemaVersion\": \(schemaVersion),\n"
    out += "  \"topology\": \"coco17\",\n"
    out += "  \"model\": \"rtmpose-m\",\n"
    out += "  \"videoName\": \"\(videoName)\",\n"
    out += "  \"intervalMs\": \(intervalMs),\n"
    out += "  \"totalFrames\": \(frames.count),\n"
    out += "  \"videoDurationMs\": \(videoDurationMs),\n"
    out += "  \"videoWidth\": \(videoWidth),\n"
    out += "  \"videoHeight\": \(videoHeight),\n"
    out += "  \"exportTimestamp\": \(Int64(Date().timeIntervalSince1970 * 1000)),\n"
    out += "  \"frames\": [\n"
    for (fi, frame) in frames.enumerated() {
        out += "    {\"frameIndex\": \(frame.frameIndex), \"timestampMs\": \(frame.timestampMs), \"landmarks\": ["
        if !frame.landmarks.isEmpty {
            out += "\n"
            for (li, kp) in frame.landmarks.enumerated() {
                out += "      {\"index\": \(li), \"x\": \(fmt4(kp.x)), \"y\": \(fmt4(kp.y)), \"score\": \(fmt4(kp.score))}"
                out += li < frame.landmarks.count - 1 ? ",\n" : "\n    "
            }
        }
        out += "]}"
        out += fi < frames.count - 1 ? ",\n" : "\n"
    }
    out += "  ]\n}\n"
    return out
}

// MARK: - Model path resolution

func defaultModelDir() -> String {
    let candidates = [
        "iosApp/TTCoach/Models",
        "TTCoach/Models",
        "../TTCoach/Models",
    ]
    let fm = FileManager.default
    for c in candidates where fm.fileExists(atPath: c) { return c }
    return "iosApp/TTCoach/Models"
}

// MARK: - Entry

func run() -> Int32 {
    let args = CommandLine.arguments
    guard args.count >= 2 else {
        fputs("Usage: RTMPoseExport <video_path> [<yolox.onnx> <rtmpose.onnx>]\n", stderr)
        return 1
    }
    let videoPath = args[1]
    let fm = FileManager.default
    guard fm.fileExists(atPath: videoPath) else {
        fputs("ERROR: file not found: \(videoPath)\n", stderr)
        return 1
    }

    let yoloxPath: String
    let rtmposePath: String
    if args.count >= 4 {
        yoloxPath = args[2]
        rtmposePath = args[3]
    } else {
        let dir = defaultModelDir()
        yoloxPath = "\(dir)/yolox_m_8xb8-300e_humanart-c2c7a14a.onnx"
        rtmposePath = "\(dir)/rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504.onnx"
    }
    guard fm.fileExists(atPath: yoloxPath), fm.fileExists(atPath: rtmposePath) else {
        fputs("ERROR: model not found:\n  YOLOX  : \(yoloxPath)\n  RTMPose: \(rtmposePath)\n", stderr)
        return 1
    }

    let videoURL = URL(fileURLWithPath: videoPath)
    let videoName = videoURL.lastPathComponent
    let videoBase = videoURL.deletingPathExtension().lastPathComponent

    fputs("Video: \(videoName)\n", stderr)
    fputs("  YOLOX  : \(yoloxPath)\n", stderr)
    fputs("  RTMPose: \(rtmposePath)\n", stderr)

    let detector: YoloxDetector
    let estimator: RtmposeEstimator
    do {
        detector = try YoloxDetector(modelPath: yoloxPath)
        estimator = try RtmposeEstimator(modelPath: rtmposePath)
    } catch {
        fputs("ERROR: failed to load ONNX models: \(error)\n", stderr)
        return 2
    }

    fputs("Extracting frames...\n", stderr)
    let frames = extractFrames(from: videoPath)
    fputs("  extracted \(frames.count) frames\n", stderr)
    guard !frames.isEmpty else {
        fputs("ERROR: no frames extracted\n", stderr)
        return 1
    }

    let videoWidth = CVPixelBufferGetWidth(frames[0].buffer)
    let videoHeight = CVPixelBufferGetHeight(frames[0].buffer)
    let videoDurationMs = frames.last?.timestampMs ?? 0
    fputs("  dimensions: \(videoWidth)x\(videoHeight)\n", stderr)

    fputs("Running RTMPose pipeline...\n", stderr)
    var poseFrames: [PoseFrame] = []
    poseFrames.reserveCapacity(frames.count)
    for (i, f) in frames.enumerated() {
        let landmarks = runPipeline(
            detector: detector, estimator: estimator,
            pixelBuffer: f.buffer, frameWidth: videoWidth, frameHeight: videoHeight)
        poseFrames.append(PoseFrame(
            frameIndex: i, timestampMs: f.timestampMs, landmarks: landmarks))
        if (i + 1) % 10 == 0 {
            fputs("  processed \(i + 1) frames (last landmarks=\(landmarks.count))\n", stderr)
        }
    }
    fputs("  processed \(poseFrames.count) frames\n", stderr)

    let json = buildPoseJSONString(
        videoName: videoName, videoWidth: videoWidth, videoHeight: videoHeight,
        videoDurationMs: videoDurationMs, frames: poseFrames)

    let outPath = videoURL.deletingLastPathComponent()
        .appendingPathComponent(videoBase + "_poses_vision_rtm.json")
    do {
        try Data(json.utf8).write(to: outPath, options: .atomic)
    } catch {
        fputs("ERROR: failed to write JSON: \(error)\n", stderr)
        return 1
    }

    let detected = poseFrames.filter { !$0.landmarks.isEmpty }.count
    fputs("\n-> \(outPath.path)\n", stderr)
    fputs("  \(poseFrames.count) frames, \(detected) with pose detected\n", stderr)
    return 0
}

exit(run())
