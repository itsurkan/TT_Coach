import CoreVideo
import Vision
import TTCoachShared
import os.log

/// On-device pose inference using Apple Vision Framework's VNDetectHumanBodyPoseRequest.
/// Returns COCO-17 keypoints mapped from Vision's 18 joint output via VisionCoco17Mapper.
///
/// Synchronous execution on the camera frame queue; backpressure (frame drops)
/// is handled upstream by CameraSession. One reusable VNDetectHumanBodyPoseRequest
/// instance is initialized once and reused for every frame to avoid allocation churn.
///
/// Inference time target: < 33 ms (30 fps cadence). Debug logging via os_log
/// tracks p50/p95 latency; if exceeded, consider frame skipping in DrillSessionController.
final class VisionPoseBackend: PoseBackend {

    private var poseRequest: VNDetectHumanBodyPoseRequest
    private let logger = Logger(subsystem: "com.ttcoachai.ttcoach", category: "VisionPoseBackend")

    /// Ring buffer for inference latency tracking (p50/p95 reporting).
    private var latencyBufMs: [Double] = []
    private let latencyBufSize = 100  // Keep last 100 measurements

    init() {
        self.poseRequest = VNDetectHumanBodyPoseRequest()
    }

    /// Estimates pose from a video frame using Vision's human body pose detector.
    ///
    /// - Parameters:
    ///   - pixelBuffer: Raw camera frame in CVPixelBuffer format
    ///   - frameWidth: Frame width in pixels
    ///   - frameHeight: Frame height in pixels
    /// - Returns: Array of 17 COCO-17 keypoints (x, y, score in normalized [0,1]),
    ///           or empty array if no person detected or inference fails.
    func estimatePose(
        in pixelBuffer: CVPixelBuffer,
        frameWidth: Int,
        frameHeight: Int
    ) -> [Keypoint2D] {
        let startTime = Date()

        // Create handler with device orientation (iOS portrait mode typically expects
        // landscape calibration — adjust per camera orientation if needed for your workflow).
        // For MVP, assume portrait device orientation.
        let handler = VNImageRequestHandler(
            cvPixelBuffer: pixelBuffer,
            orientation: .right  // portrait device, home button right
        )

        // Run Vision inference synchronously (safe on camera queue).
        do {
            try handler.perform([poseRequest])
        } catch {
            logger.warning("Vision pose detection failed: \(error.localizedDescription)")
            return []
        }

        // Extract Vision observations and map to COCO-17.
        guard let observations = poseRequest.results as? [VNRecognizedPointsObservation],
              !observations.isEmpty else {
            // No person detected — empty array satisfies PoseBackend contract.
            logLatency(Date().timeIntervalSince(startTime))
            return []
        }

        // Multi-person: select the one with highest mean confidence.
        // (For MVP single-person drills, this is usually just one, but the
        // mapper handles multiple detections correctly per its bestPerson logic.)
        let allDetections = observations.compactMap { observation in
            extractVisionKeypoints(from: observation)
        }

        guard let bestDetection = selectBestPerson(allDetections) else {
            logLatency(Date().timeIntervalSince(startTime))
            return []
        }

        // Map Vision's 18 joints to COCO-17, apply y-flip, fill placeholders.
        guard let coco17 = VisionCoco17Mapper.mapToCoco17(
            visionKeypoints: bestDetection,
            frameWidth: frameWidth,
            frameHeight: frameHeight
        ) else {
            logLatency(Date().timeIntervalSince(startTime))
            return []
        }

        // Convert to Keypoint2D (shared module model).
        let keypoints = coco17.map { kp in
            Keypoint2D(x: kp.x, y: kp.y, score: kp.confidence)
        }

        logLatency(Date().timeIntervalSince(startTime))
        return keypoints
    }

    /// Extracts Vision's 18 joint observations as plain tuples (not Vision types)
    /// for compatibility with VisionCoco17Mapper.
    ///
    /// Vision's VNRecognizedPointsObservation.RecognizedPoint order (0–17):
    /// head, neck, nose, leftShoulder, rightShoulder, leftElbow, rightElbow,
    /// leftWrist, rightWrist, leftHip, rightHip, leftKnee, rightKnee,
    /// leftAnkle, rightAnkle, sternum, spine, pelvis
    private func extractVisionKeypoints(
        from observation: VNRecognizedPointsObservation
    ) -> [VisionCoco17Mapper.VisionKeypoint] {
        var keypoints: [VisionCoco17Mapper.VisionKeypoint] = []

        // Vision joint indices in order.
        let visionJointNames: [VNRecognizedPointGroupKey] = [
            .bodyLandmarkKeyHead,
            .bodyLandmarkKeyNeck,
            .bodyLandmarkKeyNose,
            .bodyLandmarkKeyLeftShoulder,
            .bodyLandmarkKeyRightShoulder,
            .bodyLandmarkKeyLeftElbow,
            .bodyLandmarkKeyRightElbow,
            .bodyLandmarkKeyLeftWrist,
            .bodyLandmarkKeyRightWrist,
            .bodyLandmarkKeyLeftHip,
            .bodyLandmarkKeyRightHip,
            .bodyLandmarkKeyLeftKnee,
            .bodyLandmarkKeyRightKnee,
            .bodyLandmarkKeyLeftAnkle,
            .bodyLandmarkKeyRightAnkle,
            // Note: sternum, spine, pelvis are NOT exposed in VNRecognizedPointsObservation
            // They're optional in Vision's v1 output; we'll synthesize them as zero-confidence.
        ]

        for (idx, jointName) in visionJointNames.enumerated() {
            if let point = try? observation.recognizedPoints(forGroupKey: jointName)?.first {
                let x = Float(point.location.x)
                let y = Float(point.location.y)
                let confidence = Float(point.confidence)
                keypoints.append(VisionCoco17Mapper.VisionKeypoint(
                    x: x,
                    y: y,
                    confidence: confidence
                ))
            } else {
                // Joint not detected or unavailable — zero-confidence placeholder.
                keypoints.append(VisionCoco17Mapper.VisionKeypoint(
                    x: 0,
                    y: 0,
                    confidence: 0
                ))
            }
        }

        // Synthesize sternum, spine, pelvis (indices 15–17) as zero-confidence
        // to reach 18 joints as expected by the mapper.
        for _ in 0..<3 {
            keypoints.append(VisionCoco17Mapper.VisionKeypoint(
                x: 0,
                y: 0,
                confidence: 0
            ))
        }

        return keypoints
    }

    /// Selects the person with the highest mean confidence from multiple detections.
    /// Reuses the mapper's multi-person selection logic.
    private func selectBestPerson(
        _ detections: [[VisionCoco17Mapper.VisionKeypoint]]
    ) -> [VisionCoco17Mapper.VisionKeypoint]? {
        VisionCoco17Mapper.bestPerson(from: detections)
    }

    /// Tracks inference latency for p50/p95 reporting.
    /// Logs a warning if latency exceeds 33 ms (30 fps target).
    private func logLatency(_ seconds: TimeInterval) {
        let ms = seconds * 1000
        latencyBufMs.append(ms)
        if latencyBufMs.count > latencyBufSize {
            latencyBufMs.removeFirst()
        }

        if ms > 33 {
            logger.warning("Inference latency \(String(format: "%.1f", ms)) ms exceeds 33 ms target")
        }

        // Log p50/p95 every 50 frames.
        if latencyBufMs.count >= 50 && latencyBufMs.count % 50 == 0 {
            let sorted = latencyBufMs.sorted()
            let p50 = sorted[sorted.count / 2]
            let p95 = sorted[Int(Double(sorted.count) * 0.95)]
            logger.debug("Inference latency p50=\(String(format: "%.1f", p50)) ms, "
                        + "p95=\(String(format: "%.1f", p95)) ms")
        }
    }
}
