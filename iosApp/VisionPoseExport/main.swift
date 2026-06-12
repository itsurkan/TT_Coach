import AVFoundation
import Vision
import Foundation

/// macOS command-line tool: video file → pose JSON (schema v2, Vision framework)
/// Usage: VisionPoseExport <video_path>
/// Output: <video_base>_poses_vision.json in the same directory

let SCHEMA_VERSION = 2
let COCO17_NUM_KEYPOINTS = 17

struct PoseFrame {
    let frameIndex: Int
    let timestampMs: Int64
    let landmarks: [[String: Any]]
}

/// Rounds a float to 4 decimal places.
func roundTo4Decimals(_ value: Float) -> Float {
    return Float(Int(value * 10000)) / 10000
}

/// Maps Vision joint index to VNHumanBodyPoseObservation.JointName.
func jointNameForIndex(_ index: Int) -> VNHumanBodyPoseObservation.JointName {
    let names: [VNHumanBodyPoseObservation.JointName] = [
        .head, .neck, .nose, .leftShoulder, .rightShoulder,
        .leftElbow, .rightElbow, .leftWrist, .rightWrist,
        .leftHip, .rightHip, .leftKnee, .rightKnee,
        .leftAnkle, .rightAnkle, .sternum, .spine, .pelvis
    ]
    return index >= 0 && index < names.count ? names[index] : .nose
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

    guard let videoTrack = try? asset.loadTracks(withMediaType: .video).first else {
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

    while let sampleBuffer = output.copyNextSampleBuffer() {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { continue }

        let timestamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        let timestampMs = Int64(timestamp.value) * 1000 / Int64(timestamp.timescale)

        // Convert CVPixelBuffer to CGImage.
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        let context = CIContext()
        guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else { continue }

        frames.append((image: cgImage, timestampMs: timestampMs, frameIndex: frameIndex))
        frameIndex += 1

        if frameIndex % 10 == 0 {
            fputs("  frame \(frameIndex) t=\(timestampMs) ms\n", stderr)
        }
    }

    reader.cancelReading()
    return frames
}

/// Detects human body pose in a CGImage using Vision framework.
/// Returns array of detections, each as array of [VisionKeypoint] (one per joint 0-18).
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
        guard let bodyPoseObservation = observation as? VNHumanBodyPoseObservation else { continue }

        // Vision provides joints 0-18. Collect them in order.
        var visionKeypoints: [VisionCoco17Mapper.VisionKeypoint] = []
        for jointIndex in 0..<19 {
            let jointName = jointNameForIndex(jointIndex)
            let point = bodyPoseObservation.recognizedPoints[jointName]

            if let point = point {
                visionKeypoints.append(
                    VisionCoco17Mapper.VisionKeypoint(
                        x: Float(point.x),
                        y: Float(point.y),
                        confidence: Float(point.confidence)
                    )
                )
            } else {
                visionKeypoints.append(
                    VisionCoco17Mapper.VisionKeypoint(x: 0, y: 0, confidence: 0)
                )
            }
        }

        detections.append(visionKeypoints)
    }

    return detections
}

/// Builds a schema-v2 pose JSON dictionary.
func buildPoseJSON(
    videoName: String,
    videoWidth: Int,
    videoHeight: Int,
    videoDurationMs: Int64,
    frames: [PoseFrame]
) -> [String: Any] {
    var data: [String: Any] = [
        "schemaVersion": SCHEMA_VERSION,
        "topology": "coco17",
        "model": "vision-bodypose",
        "videoName": videoName,
        "intervalMs": 1,  // Full FPS export
        "totalFrames": frames.count,
        "videoDurationMs": videoDurationMs,
        "videoWidth": videoWidth,
        "videoHeight": videoHeight,
        "exportTimestamp": Int64(Date().timeIntervalSince1970 * 1000),
        "frames": frames.map { frame -> [String: Any] in
            [
                "frameIndex": frame.frameIndex,
                "timestampMs": frame.timestampMs,
                "landmarks": frame.landmarks
            ]
        }
    ]
    return data
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
    var landmarks: [[String: Any]] = []
    if let bestDetection = VisionCoco17Mapper.bestPerson(from: detections) {
        let mapped = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: bestDetection,
            frameWidth: videoWidth,
            frameHeight: videoHeight
        )

        if mapped.count == COCO17_NUM_KEYPOINTS {
            landmarks = mapped.enumerated().map { (idx, kp) -> [String: Any] in
                [
                    "index": idx,
                    "x": Double(roundTo4Decimals(kp.x)),
                    "y": Double(roundTo4Decimals(kp.y)),
                    "score": Double(roundTo4Decimals(kp.confidence))
                ]
            }
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
let poseJSON = buildPoseJSON(
    videoName: videoName,
    videoWidth: videoWidth,
    videoHeight: videoHeight,
    videoDurationMs: videoDurationMs,
    frames: poseFrames
)

let outPath = videoURL.deletingLastPathComponent()
    .appendingPathComponent(videoBase + "_poses_vision.json")

do {
    let jsonData = try JSONSerialization.data(withJSONObject: poseJSON, options: [.prettyPrinted, .sortedKeys])
    try jsonData.write(to: outPath, options: .atomic)
    fputs("\n-> \(outPath.path)\n", stderr)

    let detectedCount = poseFrames.filter { !$0.landmarks.isEmpty }.count
    fputs("  \(poseFrames.count) frames, \(detectedCount) with pose detected\n", stderr)
} catch {
    fputs("ERROR: failed to write JSON: \(error)\n", stderr)
    exit(1)
}
