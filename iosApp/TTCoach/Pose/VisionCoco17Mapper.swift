import Foundation
import Vision

/// Maps Apple Vision Framework body pose keypoints to COCO-17 indices.
/// Input: plain value structs (not Vision types) for testability.
/// Output: 17 COCO-17 keypoints in normalized image coords, or empty array if no person.
///
/// Vision coordinates: bottom-left origin [0,1]
/// COCO-17 coordinates: top-left origin [0,1] (y-flip applied)
///
/// Vision's `VNHumanBodyPoseObservation` provides 19 joints, including eyes and
/// ears — so COCO-17 indices 1–4 (left_eye, right_eye, left_ear, right_ear) map
/// directly from real Vision joints. Vision's `neck` and `root` joints have no
/// COCO-17 counterpart and are dropped.
public struct VisionCoco17Mapper {

    /// A single Vision keypoint (plain value struct for testability).
    public struct VisionKeypoint {
        public let x: Float
        public let y: Float
        public let confidence: Float

        public init(x: Float, y: Float, confidence: Float) {
            self.x = x
            self.y = y
            self.confidence = confidence
        }
    }

    /// Canonical Vision joint order (indices 0–18). Callers must build the
    /// `[VisionKeypoint]` input array in exactly this order, substituting a
    /// zero-confidence placeholder for joints Vision did not detect.
    public static let visionJointNames: [VNHumanBodyPoseObservation.JointName] = [
        .nose,           // 0
        .leftEye,        // 1
        .rightEye,       // 2
        .leftEar,        // 3
        .rightEar,       // 4
        .neck,           // 5  (dropped — no COCO-17 counterpart)
        .leftShoulder,   // 6
        .rightShoulder,  // 7
        .leftElbow,      // 8
        .rightElbow,     // 9
        .leftWrist,      // 10
        .rightWrist,     // 11
        .root,           // 12 (dropped — no COCO-17 counterpart)
        .leftHip,        // 13
        .rightHip,       // 14
        .leftKnee,       // 15
        .rightKnee,      // 16
        .leftAnkle,      // 17
        .rightAnkle,     // 18
    ]

    /// Number of joints in the canonical Vision order.
    public static let visionJointCount = 19

    /// COCO-17 index → canonical Vision index (per `visionJointNames`).
    /// COCO order: nose, left_eye, right_eye, left_ear, right_ear,
    /// left_shoulder, right_shoulder, left_elbow, right_elbow,
    /// left_wrist, right_wrist, left_hip, right_hip,
    /// left_knee, right_knee, left_ankle, right_ankle.
    private static let COCO_TO_VISION: [Int] = [
        0,   // COCO 0  nose          ← Vision 0  nose
        1,   // COCO 1  left_eye      ← Vision 1  leftEye
        2,   // COCO 2  right_eye     ← Vision 2  rightEye
        3,   // COCO 3  left_ear      ← Vision 3  leftEar
        4,   // COCO 4  right_ear     ← Vision 4  rightEar
        6,   // COCO 5  left_shoulder ← Vision 6  leftShoulder
        7,   // COCO 6  right_shoulder← Vision 7  rightShoulder
        8,   // COCO 7  left_elbow    ← Vision 8  leftElbow
        9,   // COCO 8  right_elbow   ← Vision 9  rightElbow
        10,  // COCO 9  left_wrist    ← Vision 10 leftWrist
        11,  // COCO 10 right_wrist   ← Vision 11 rightWrist
        13,  // COCO 11 left_hip      ← Vision 13 leftHip
        14,  // COCO 12 right_hip     ← Vision 14 rightHip
        15,  // COCO 13 left_knee     ← Vision 15 leftKnee
        16,  // COCO 14 right_knee    ← Vision 16 rightKnee
        17,  // COCO 15 left_ankle    ← Vision 17 leftAnkle
        18,  // COCO 16 right_ankle   ← Vision 18 rightAnkle
    ]

    /// Maps Vision keypoints (one person) to COCO-17 order.
    /// - Parameters:
    ///   - visionKeypoints: Vision joints in the canonical order (`visionJointNames`).
    ///     Length must be >= 19; shorter input is treated as malformed (empty result).
    ///   - frameWidth: Video frame width in pixels (reserved for validation)
    ///   - frameHeight: Video frame height in pixels (reserved for validation)
    /// - Returns: 17 COCO keypoints in COCO-17 order, or empty array if input is
    ///   empty or malformed.
    ///
    /// Coordinate transform:
    /// - Vision: x,y in [0,1], y=0 at bottom (bottom-left origin)
    /// - Output: x,y in [0,1], y=0 at top (top-left origin) — y' = 1 - y
    ///
    /// Confidence passes through raw. Joints Vision did not detect arrive as
    /// (0, 0, confidence 0) placeholders from the caller; after the y-flip their
    /// coordinates are meaningless but harmless — the shared drill pipeline gates
    /// every angle computation on score >= 0.3.
    public static func mapToCoco17(
        visionKeypoints: [VisionKeypoint],
        frameWidth: Int,
        frameHeight: Int
    ) -> [VisionKeypoint] {
        guard visionKeypoints.count >= visionJointCount else {
            // Empty or malformed input — signal no person detected.
            return []
        }

        return COCO_TO_VISION.map { visionIdx in
            let kp = visionKeypoints[visionIdx]
            // Y-flip: Vision has y=0 at bottom, COCO expects y=0 at top.
            return VisionKeypoint(
                x: kp.x,
                y: 1.0 - kp.y,
                confidence: kp.confidence
            )
        }
    }

    /// Computes the mean confidence of a detection array.
    private static func meanConfidence(_ keypoints: [VisionKeypoint]) -> Float {
        guard !keypoints.isEmpty else { return 0 }
        return keypoints.map { $0.confidence }.reduce(0, +) / Float(keypoints.count)
    }

    /// Selects the best person from multiple detections based on mean confidence.
    /// - Parameter detections: Array of [VisionKeypoint] arrays (one per detected person).
    /// - Returns: The detection with the highest mean confidence, or nil if detections is empty.
    public static func bestPerson(from detections: [[VisionKeypoint]]) -> [VisionKeypoint]? {
        guard !detections.isEmpty else { return nil }
        return detections.max { a, b in
            meanConfidence(a) < meanConfidence(b)
        }
    }
}
