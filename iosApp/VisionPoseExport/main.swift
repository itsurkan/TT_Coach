import AVFoundation
import CoreImage
import Vision
import Foundation

/// macOS command-line tool: video file → pose JSON (schema v2, Vision framework)
/// Usage: VisionPoseExport <video_path>
/// Output: <video_base>_poses_vision.json in the same directory

let schemaVersion = 2
let coco17NumKeypoints = 17

struct PoseFrame {
    let frameIndex: Int
    let timestampMs: Int64
    let landmarks: [VisionCoco17Mapper.VisionKeypoint]
}

/// Formats a float with exactly 4 decimal places (schema v2 contract).
func fmt4(_ value: Float) -> String {
    return String(format: "%.4f", value)
}

/// Extracts all frames from a video using AVAssetReader.
/// Returns array of (CGImage, presentationTimeMs, frameIndex).
func extractFrames(from videoPath: String) -> [(image: CGImage, timestampMs: Int64, frameIndex: Int)] {
    let url = URL(fileURLWithPath: videoPath)
    let asset = AVAsset(url: url)

    guard let reader = try? AVAssetReader(asset: asset) else {
        fputs("ERROR: cannot create AVAssetReader for \(videoPath)\n", stderr)
        exit(1)
    }

    // Synchronous track access — deprecation warning acceptable for a CLI tool.
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

    var frames: [(image: CGImage, timestampMs: Int64, frameIndex: Int)] = []
    var frameIndex = 0

    let context = CIContext()  // Create once outside the loop for efficiency
    while let sampleBuffer = output.copyNextSampleBuffer() {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { continue }

        let timestamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        let timestampMs = Int64(timestamp.value) * 1000 / Int64(timestamp.timescale)

        // Convert CVPixelBuffer to CGImage.
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else { continue }

        frames.append((image: cgImage, timestampMs: timestampMs, frameIndex: frameIndex))
        frameIndex += 1

        if frameIndex % 10 == 0 {
            fputs("  frame \(frameIndex) t=\(timestampMs) ms\n", stderr)
        }
    }

    reader.cancelReading()

    // AVAssetReader emits frames in DECODE order (B-frames arrive out of sequence).
    // The drill pipeline needs a monotonic time series — sort by presentation time
    // and renumber frame indices.
    frames.sort { $0.timestampMs < $1.timestampMs }

    // Match the RTM exporter's timestamp convention: uniform frameIndex * intervalMs.
    // Phone footage is variable-frame-rate; raw presentation times carry gaps that
    // the ms-window stroke detector reads as slow strokes. The RTM golden fixtures
    // use uniform synthetic timestamps, so parity requires the same time base.
    let durationMs = frames.last?.timestampMs ?? 0
    let intervalMs = frames.count > 1
        ? Int64((Double(durationMs) / Double(frames.count - 1)).rounded())
        : 1
    return frames.enumerated().map { (i, f) in
        (image: f.image, timestampMs: Int64(i) * intervalMs, frameIndex: i)
    }
}

/// Detects human body pose in a CGImage using Vision framework.
/// Returns array of detections, each as 19 keypoints in the canonical Vision
/// joint order (`VisionCoco17Mapper.visionJointNames`).
func detectPose(in cgImage: CGImage) -> [[VisionCoco17Mapper.VisionKeypoint]] {
    let request = VNDetectHumanBodyPoseRequest()
    let handler = VNImageRequestHandler(cgImage: cgImage)

    do {
        try handler.perform([request])
    } catch {
        return []
    }

    var detections: [[VisionCoco17Mapper.VisionKeypoint]] = []

    for observation in request.results ?? [] {
        let visionKeypoints = VisionCoco17Mapper.visionJointNames.map { jointName -> VisionCoco17Mapper.VisionKeypoint in
            if let point = try? observation.recognizedPoint(jointName) {
                return VisionCoco17Mapper.VisionKeypoint(
                    x: Float(point.location.x),
                    y: Float(point.location.y),
                    confidence: Float(point.confidence)
                )
            } else {
                return VisionCoco17Mapper.VisionKeypoint(x: 0, y: 0, confidence: 0)
            }
        }
        detections.append(visionKeypoints)
    }

    return detections
}

/// Serializes schema-v2 pose JSON by hand. JSONSerialization cannot guarantee
/// key order, but the KMP parser (PoseJsonV2Parser) tripwires on landmark
/// field-order drift — landmarks MUST serialize as {index, x, y, score}.
func buildPoseJSONString(
    videoName: String,
    videoWidth: Int,
    videoHeight: Int,
    videoDurationMs: Int64,
    frames: [PoseFrame]
) -> String {
    // Mean frame interval, like the RTM exporter (17 ms @ 60 fps, 20 ms @ 50 fps).
    // Timestamps are uniform (frameIndex * intervalMs), so this divides exactly.
    let intervalMs = frames.count > 1
        ? Int((Double(videoDurationMs) / Double(frames.count - 1)).rounded())
        : 1
    var out = "{\n"
    out += "  \"schemaVersion\": \(schemaVersion),\n"
    out += "  \"topology\": \"coco17\",\n"
    out += "  \"model\": \"vision-bodypose\",\n"
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
                out += "      {\"index\": \(li), \"x\": \(fmt4(kp.x)), \"y\": \(fmt4(kp.y)), \"score\": \(fmt4(kp.confidence))}"
                out += li < frame.landmarks.count - 1 ? ",\n" : "\n    "
            }
        }
        out += "]}"
        out += fi < frames.count - 1 ? ",\n" : "\n"
    }
    out += "  ]\n}\n"
    return out
}

/// Main entry point.
guard CommandLine.arguments.count == 2 else {
    fputs("Usage: VisionPoseExport <video_path>\n", stderr)
    exit(1)
}

let videoPath = CommandLine.arguments[1]
let fileManager = FileManager.default

guard fileManager.fileExists(atPath: videoPath) else {
    fputs("ERROR: file not found: \(videoPath)\n", stderr)
    exit(1)
}

let videoURL = URL(fileURLWithPath: videoPath)
let videoName = videoURL.lastPathComponent
let videoBase = videoURL.deletingPathExtension().lastPathComponent

fputs("Video: \(videoName)\n", stderr)

// Extract all frames.
fputs("Extracting frames...\n", stderr)
let frames = extractFrames(from: videoPath)
fputs("  extracted \(frames.count) frames\n", stderr)

// Get video dimensions from the first frame.
guard !frames.isEmpty else {
    fputs("ERROR: no frames extracted\n", stderr)
    exit(1)
}

let videoWidth = frames[0].image.width
let videoHeight = frames[0].image.height
let videoDurationMs = frames.last?.timestampMs ?? 0

fputs("  dimensions: \(videoWidth)x\(videoHeight)\n", stderr)

// Run Vision inference on all frames.
fputs("Running Vision inference...\n", stderr)
var poseFrames: [PoseFrame] = []

for (index, frameData) in frames.enumerated() {
    let detections = detectPose(in: frameData.image)

    // Pick the best person if multiple detected.
    var landmarks: [VisionCoco17Mapper.VisionKeypoint] = []
    if let bestDetection = VisionCoco17Mapper.bestPerson(from: detections) {
        let mapped = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: bestDetection,
            frameWidth: videoWidth,
            frameHeight: videoHeight
        )
        if mapped.count == coco17NumKeypoints {
            landmarks = mapped
        }
    }

    poseFrames.append(
        PoseFrame(
            frameIndex: frameData.frameIndex,
            timestampMs: frameData.timestampMs,
            landmarks: landmarks
        )
    )

    if (index + 1) % 10 == 0 {
        fputs("  processed \(index + 1) frames\n", stderr)
    }
}

fputs("  processed \(poseFrames.count) frames\n", stderr)

// Build and write JSON.
let poseJSONString = buildPoseJSONString(
    videoName: videoName,
    videoWidth: videoWidth,
    videoHeight: videoHeight,
    videoDurationMs: videoDurationMs,
    frames: poseFrames
)

let outPath = videoURL.deletingLastPathComponent()
    .appendingPathComponent(videoBase + "_poses_vision.json")

do {
    try Data(poseJSONString.utf8).write(to: outPath, options: .atomic)
    fputs("\n-> \(outPath.path)\n", stderr)

    let detectedCount = poseFrames.filter { !$0.landmarks.isEmpty }.count
    fputs("  \(poseFrames.count) frames, \(detectedCount) with pose detected\n", stderr)
} catch {
    fputs("ERROR: failed to write JSON: \(error)\n", stderr)
    exit(1)
}
