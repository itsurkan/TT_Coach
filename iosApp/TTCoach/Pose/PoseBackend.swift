import CoreVideo
import TTCoachShared

/// Platform pose-inference seam — the iOS counterpart of the `PoseBackend`
/// interface planned for the Phase 3 Android port. Implementations run RTMPose
/// (or any COCO-17 estimator) on a camera frame and return keypoints in the
/// shared module's schema-v2 convention.
///
/// Contract (must match docs/pose_json_schema_v2.md):
/// - 17 COCO keypoints, indices per `Coco17` in the shared module.
/// - Coordinates NORMALIZED per axis: x / frameWidth, y / frameHeight.
///   (All shared-side geometry corrects x by `ViewGeometry.xScale`; never feed
///   pixel coords or square-normalized coords.)
/// - `score` is per-keypoint confidence in [0, 1]. Shared angle functions gate
///   on score >= 0.3 and return null below it — do not inflate scores.
/// - Return an empty array when no person is detected (maps to an empty
///   `PoseFrame2D`, which the shared detector tolerates).
protocol PoseBackend {
    /// Synchronous on purpose: called on the camera frame queue, which already
    /// applies backpressure by dropping late frames.
    func estimatePose(in pixelBuffer: CVPixelBuffer, frameWidth: Int, frameHeight: Int) -> [Keypoint2D]
}
