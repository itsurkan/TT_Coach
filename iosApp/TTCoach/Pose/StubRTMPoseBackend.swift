import CoreVideo
import TTCoachShared

/// Placeholder pose backend so the full pipeline (camera → pose → shared drill
/// logic → TTS) compiles and runs end-to-end before real inference is wired in.
/// Always reports "no person detected".
///
/// Replacing this with real RTMPose is the main remaining engineering task.
/// Recommended path (see Iphone/iphone-deployment-investigation.md §4 and
/// iosApp/IOS_PORT_REPORT.md):
///
/// 1. ONNX Runtime + CoreML execution provider (recommended — same .onnx
///    artifact as the planned Android Phase 3 port):
///    - Add the `onnxruntime-objc` (or Swift package `onnxruntime`) dependency
///      in project.yml.
///    - Export RTMPose-s (+ RTMDet-nano or a cheaper person-ROI heuristic) to
///      ONNX via MMDeploy on the desktop, bundle the .onnx in the app.
///    - Preprocess: crop person ROI → resize 256×192 → normalize → NCHW floats.
///    - Postprocess: SimCC x/y bins → argmax → keypoint px → divide by frame
///      width/height for the normalized coords this protocol requires.
/// 2. Alternative: convert to CoreML (.mlpackage) via MMDeploy's CoreML
///    backend and run with Vision/CoreML directly.
///
/// Keep ALL drill/analysis math in the shared KMP module — this layer only
/// produces keypoints.
final class StubRTMPoseBackend: PoseBackend {

    func estimatePose(in pixelBuffer: CVPixelBuffer, frameWidth: Int, frameHeight: Int) -> [Keypoint2D] {
        // No inference yet: behave like "no person in frame". The shared
        // pipeline handles empty frames gracefully (no strokes detected).
        return []
    }
}
