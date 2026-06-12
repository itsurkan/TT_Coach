import AVFoundation
import CoreVideo

/// Thin AVFoundation wrapper: configures an `AVCaptureSession` (back camera,
/// 720p — RTMPose input is 256×192, so higher capture res is wasted work) and
/// delivers frames as `CVPixelBuffer` + millisecond timestamps on a dedicated
/// queue. iOS counterpart of the Android CameraX analysis stream.
///
/// Frame consumers must be fast or drop frames; `alwaysDiscardsLateVideoFrames`
/// is enabled so a slow pose backend degrades to a lower effective FPS instead
/// of building latency — acceptable for the 3–5 s feedback cadence.
final class CameraSession: NSObject {

    /// (pixelBuffer, presentation timestamp in ms, frame width px, frame height px)
    var onFrame: ((CVPixelBuffer, Int64, Int, Int) -> Void)?

    let session = AVCaptureSession()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let sessionQueue = DispatchQueue(label: "camera.session")
    private let frameQueue = DispatchQueue(label: "camera.frames")

    func requestAccessAndConfigure(completion: @escaping (Bool) -> Void) {
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            guard granted, let self else {
                DispatchQueue.main.async { completion(false) }
                return
            }
            self.sessionQueue.async {
                let ok = self.configure()
                DispatchQueue.main.async { completion(ok) }
            }
        }
    }

    private func configure() -> Bool {
        session.beginConfiguration()
        defer { session.commitConfiguration() }

        session.sessionPreset = .hd1280x720

        guard
            let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
            let input = try? AVCaptureDeviceInput(device: device),
            session.canAddInput(input)
        else { return false }
        session.addInput(input)

        videoOutput.videoSettings =
            [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.setSampleBufferDelegate(self, queue: frameQueue)
        guard session.canAddOutput(videoOutput) else { return false }
        session.addOutput(videoOutput)

        return true
    }

    func start() {
        sessionQueue.async { [self] in
            if !session.isRunning { session.startRunning() }
        }
    }

    func stop() {
        sessionQueue.async { [self] in
            if session.isRunning { session.stopRunning() }
        }
    }
}

extension CameraSession: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        let timestampMs = Int64(CMTimeGetSeconds(pts) * 1000.0)
        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        onFrame?(pixelBuffer, timestampMs, width, height)
    }
}
