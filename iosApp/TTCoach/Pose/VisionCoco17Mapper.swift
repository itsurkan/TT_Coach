import Foundation

/// Maps Apple Vision Framework body pose keypoints to COCO-17 indices.
/// Input: plain tuples (not Vision types) for testability.
/// Output: 17 COCO-17 keypoints in normalized image coords, or empty array if no person.
///
/// Vision coordinates: bottom-left origin [0,1]
/// COCO-17 coordinates: top-left origin [0,1] (y-flip applied)
///
/// Mapping rule: Vision provides 19 joints; COCO-17 uses 17. We map the 17 COCO joints
/// and drop Vision's neck/sternum/spine/pelvis (redundant with shoulders/hips).
public struct VisionCoco17Mapper {

    /// A single Vision keypoint (plain tuple for testability).
    public struct VisionKeypoint {
        let x: Float
        let y: Float
        let confidence: Float
    }

    /// Maps Vision joints (0-18) to COCO-17 indices, or nil if that Vision joint is not used.
    /// Vision joint order (as per AVFoundation VNHumanBodyPoseObservation.RecognizedPoint):
    /// 0: head, 1: neck, 2: nose, 3: leftShoulder, 4: rightShoulder,
    /// 5: leftElbow, 6: rightElbow, 7: leftWrist, 8: rightWrist,
    /// 9: leftHip, 10: rightHip, 11: leftKnee, 12: rightKnee,
    /// 13: leftAnkle, 14: rightAnkle, 15: sternum, 16: spine, 17: pelvis, 18: (unused)
    private static let VISION_TO_COCO: [Int?] = [
        nil,        // 0: head → dropped (Halpe26 only; COCO-17 uses eye/ear landmarks)
        nil,        // 1: neck → dropped
        0,          // 2: nose → COCO 0
        5,          // 3: leftShoulder → COCO 5
        6,          // 4: rightShoulder → COCO 6
        7,          // 5: leftElbow → COCO 7
        8,          // 6: rightElbow → COCO 8
        9,          // 7: leftWrist → COCO 9
        10,         // 8: rightWrist → COCO 10
        11,         // 9: leftHip → COCO 11
        12,         // 10: rightHip → COCO 12
        13,         // 11: leftKnee → COCO 13
        14,         // 12: rightKnee → COCO 14
        15,         // 13: leftAnkle → COCO 15
        16,         // 14: rightAnkle → COCO 16
        nil,        // 15: sternum → dropped
        nil,        // 16: spine → dropped
        nil,        // 17: pelvis → dropped
        nil,        // 18: unused
    ]

    /// Maps Vision keypoints (one person) to COCO-17 order.
    /// - Parameters:
    ///   - visionKeypoints: Vision joints in order [0..18]. Length must be 19 or greater.
    ///   - frameWidth: Video frame width in pixels (used only for validation)
    ///   - frameHeight: Video frame height in pixels (used only for validation)
    /// - Returns: 17 COCO keypoints in COCO-17 order, or empty array if visionKeypoints is empty.
    ///
    /// Coordinate transform:
    /// - Vision: x,y in [0,1], y=0 at bottom, y=1 at top (bottom-left origin)
    /// - Output: x,y in [0,1], y=0 at top, y=1 at bottom (top-left origin) — y' = 1 - y
    public static func mapToCoco17(
        visionKeypoints: [VisionKeypoint],
        frameWidth: Int,
        frameHeight: Int
    ) -> [VisionKeypoint] {
        guard !visionKeypoints.isEmpty else { return [] }
        guard visionKeypoints.count >= 19 else {
            // Malformed input — return empty to signal no person detected.
            return []
        }

        // Build COCO-17 array with nil placeholders.
        var coco17: [VisionKeypoint?] = Array(repeating: nil, count: 17)

        // Map Vision joints to COCO indices, applying y-flip.
        for (visionIdx, cocoIdx) in VISION_TO_COCO.enumerated() {
            guard let cocoIdx = cocoIdx, visionIdx < visionKeypoints.count else { continue }
            let visionKp = visionKeypoints[visionIdx]
            // Y-flip: Vision has y=0 at bottom, COCO expects y=0 at top.
            let flippedY = 1.0 - visionKp.y
            coco17[cocoIdx] = VisionKeypoint(
                x: visionKp.x,
                y: flippedY,
                confidence: visionKp.confidence
            )
        }

        // All 17 COCO indices must be mapped (no nils).
        guard coco17.allSatisfy({ $0 != nil }) else { return [] }

        return coco17.compactMap { $0 }
    }

    /// Selects the best person from multiple detections based on mean confidence.
    /// - Parameter detections: Array of [VisionKeypoint] arrays (one per detected person).
    /// - Returns: The detection with the highest mean confidence, or nil if detections is empty.
    public static func bestPerson(from detections: [[VisionKeypoint]]) -> [VisionKeypoint]? {
        guard !detections.isEmpty else { return nil }
        return detections.max { a, b in
            let meanConfA = a.map { $0.confidence }.reduce(0, +) / Float(a.count)
            let meanConfB = b.map { $0.confidence }.reduce(0, +) / Float(b.count)
            return meanConfA < meanConfB
        }
    }
}
