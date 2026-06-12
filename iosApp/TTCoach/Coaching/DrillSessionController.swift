import Foundation
import CoreVideo
import TTCoachShared

/// Orchestrates the MVP coaching loop, mirroring the Android flow:
/// camera frames → PoseBackend → shared KMP drill logic → spoken/text feedback.
///
/// ALL analysis lives in the shared module: stroke detection
/// (`StrokeDetector2D` → `ForwardStrokeFilter` → `RepFilter` — order is
/// load-bearing, see CLAUDE.md), per-rep camera-yaw gating, metrics, baseline
/// rules, message catalog, and the 3–5 s cadence policy. Swift only buffers
/// frames and forwards results.
///
/// NOTE on liveness: Phase 2's `ForehandDriveDrillAnalyzer.analyze()` is a
/// batch API. Until Phase 3 lands an incremental loop, this controller re-runs
/// the batch analyzer over the growing session buffer every few seconds and
/// speaks only feedback items newer than the last spoken timestamp. Correct,
/// just not maximally cheap — fine at MVP frame counts.
///
/// NOTE on bindings: written against the Kotlin/Native ObjC export of the
/// shared module (Kotlin defaults are NOT exported — every parameter is passed
/// explicitly). Verify exact labels against the generated TTCoachShared.h on
/// first build; cosmetic renames may be needed.
@MainActor
final class DrillSessionController: ObservableObject {

    enum Mode: String {
        case idle
        case calibrating   // recording reps to derive the personal baseline (003 path)
        case coaching      // live drill feedback against the baseline
    }

    // MARK: - Published UI state

    @Published private(set) var mode: Mode = .idle
    @Published private(set) var statusText: String = "Ready"
    @Published private(set) var lastSpokenMessage: String = ""
    @Published private(set) var feedbackLog: [String] = []
    @Published private(set) var cameraPlacementOk: Bool = true
    @Published private(set) var hasBaseline: Bool = false

    // MARK: - Pipeline pieces

    let camera = CameraSession()
    private let poseBackend: PoseBackend
    private let speech: SpeechFeedback
    private let lang: FeedbackLang
    private let handedness: Handedness

    /// In-memory only for the scaffold; persistence (UserDefaults/JSON or a
    /// future shared serializer) is listed in IOS_PORT_REPORT.md.
    private var baseline: PersonalBaseline?

    // MARK: - Frame buffer (accessed from the camera frame queue via Task hops)

    private var frames: [PoseFrame2D] = []
    private var frameWidth: Int32 = 0
    private var frameHeight: Int32 = 0
    private var firstTimestampMs: Int64 = 0
    private var lastTimestampMs: Int64 = 0
    private var nextFrameIndex: Int32 = 0
    private var lastSpokenSessionMs: Int64 = -1
    private var analysisTimer: Timer?

    /// Cap the live buffer (~4 min at 30 fps) so memory stays bounded.
    private let maxBufferedFrames = 8000
    /// Re-run the shared batch analyzer this often while coaching.
    private let analysisIntervalS: TimeInterval = 3.0

    init(
        poseBackend: PoseBackend = VisionPoseBackend(),
        lang: FeedbackLang = .en,
        handedness: Handedness = .right
    ) {
        self.poseBackend = poseBackend
        self.lang = lang
        self.handedness = handedness
        self.speech = SpeechFeedback(lang: lang)

        camera.onFrame = { [weak self] pixelBuffer, timestampMs, width, height in
            // Pose inference runs synchronously on the camera frame queue
            // (late frames are dropped upstream — natural backpressure).
            guard let self else { return }
            let keypoints = self.poseBackend.estimatePose(
                in: pixelBuffer, frameWidth: width, frameHeight: height
            )
            Task { @MainActor in
                self.append(keypoints: keypoints, timestampMs: timestampMs,
                            width: Int32(width), height: Int32(height))
            }
        }
    }

    // MARK: - Session control

    func startCamera() {
        camera.requestAccessAndConfigure { [weak self] ok in
            guard let self else { return }
            if ok {
                self.camera.start()
                self.statusText = "Camera ready — calibrate first (≥10 reps)"
            } else {
                self.statusText = "Camera unavailable — check permissions in Settings"
            }
        }
    }

    /// Record forehand-drive reps to derive the personal baseline (the 003
    /// calibration path — calibrate to the player's technique, don't re-teach).
    func startCalibration() {
        resetBuffer()
        mode = .calibrating
        statusText = "Calibrating: do ~15 forehand drives, side camera"
    }

    func finishCalibration() {
        guard mode == .calibrating else { return }

        guard frames.count > 1 else {
            statusText = "No frames captured — is the pose backend wired in?"
            mode = .idle
            return
        }

        // Kotlin defaults aren't exported to Swift — pass everything explicitly,
        // using the same values as the Kotlin signature defaults.
        // Use calibrateChecked() for safe exception handling (M2.1).
        let outcome = DrillCalibrator.shared.calibrateChecked(
            sequence: makeSequence(),
            drillType: "forehand_drive",
            createdAtMs: Int64(Date().timeIntervalSince1970 * 1000),
            handedness: handedness,
            minRepCount: 10,
            outlierSigmaThreshold: 2.0,
            detector: defaultDetector(),
            cameraYawDeg: nil,        // per-rep auto-estimate from the PRE-stroke window
            maxCameraYawDeg: DrillCalibrator.shared.DEFAULT_MAX_CAMERA_YAW_DEG
        )

        // Pattern-match on CalibrationOutcome sealed class (M2.1).
        // Kotlin-Native exports sealed classes as ObjC class hierarchy.
        if let success = outcome as? CalibrationOutcomeSuccess {
            // Happy path: save baseline, activate it, move to coaching
            self.baseline = success.baseline
            hasBaseline = true
            mode = .coaching
            statusText = "Baseline ready (\(success.baseline.repCount) reps, " +
                         "quality \(String(format: "%.2f", success.baseline.qualityScore)))"
            startCoaching()
        } else if let placementError = outcome as? CalibrationOutcomePlacementError {
            // Camera placement issue — ask user to reposition
            statusText = "Camera placement issue — repositioning needed"
            speech.speak(placementError.message)
            mode = .calibrating  // Stay in calibration, allow retry
        } else if let failed = outcome as? CalibrationOutcomeFailed {
            // Other error — suggest retry
            statusText = "Calibration failed: \(failed.message)"
            speech.speak("Calibration failed. Try again.")
            mode = .calibrating  // Stay in calibration, allow retry from start
        }
    }

    func startCoaching() {
        guard let _ = baseline else {
            statusText = "Calibrate first — coaching needs your personal baseline"
            return
        }
        resetBuffer()
        mode = .coaching
        statusText = "Coaching: forehand drive"
        analysisTimer = Timer.scheduledTimer(withTimeInterval: analysisIntervalS, repeats: true) {
            [weak self] _ in
            Task { @MainActor in self?.analyzeBufferedSession() }
        }
    }

    func stopCoaching() {
        analysisTimer?.invalidate()
        analysisTimer = nil
        speech.stop()
        mode = .idle
        statusText = "Session stopped"
    }

    // MARK: - Pipeline internals

    private func append(keypoints: [Keypoint2D], timestampMs: Int64, width: Int32, height: Int32) {
        guard mode == .calibrating || mode == .coaching else { return }
        if frames.isEmpty { firstTimestampMs = timestampMs }
        frameWidth = width
        frameHeight = height
        lastTimestampMs = timestampMs
        frames.append(PoseFrame2D(
            frameIndex: nextFrameIndex,
            timestampMs: timestampMs - firstTimestampMs,
            keypoints: keypoints
        ))
        nextFrameIndex += 1
        if frames.count > maxBufferedFrames {
            // Drop oldest; fine for the scaffold (stroke windows are ~1 s).
            frames.removeFirst(frames.count - maxBufferedFrames)
        }
    }

    private func resetBuffer() {
        frames = []
        nextFrameIndex = 0
        firstTimestampMs = 0
        lastTimestampMs = 0
        lastSpokenSessionMs = -1
        feedbackLog = []
    }

    /// Schema-v2 sequence over the live buffer. `intervalMs` is the mean frame
    /// spacing — the shared detector expects a fixed interval (Phase 3 will
    /// derive dt from real timestamps; see StrokeDetector2D doc note).
    private func makeSequence() -> PoseSequence2D {
        let count = frames.count
        let spanMs = lastTimestampMs - firstTimestampMs
        let intervalMs = count > 1 ? max(1, spanMs / Int64(count - 1)) : 33
        return PoseSequence2D(
            topology: .coco17,
            model: "rtmpose-ios-stub",
            videoName: "live-session",
            intervalMs: intervalMs,
            totalFrames: Int32(count),
            videoDurationMs: spanMs,
            videoWidth: frameWidth,
            videoHeight: frameHeight,
            frames: frames
        )
    }

    private func defaultDetector() -> StrokeDetector2D {
        // Values mirror the Kotlin constructor defaults (not exported to ObjC).
        StrokeDetector2D(
            minScore: 0.3,            // AngleCalculations2D.DEFAULT_MIN_SCORE
            smoothingWindowMs: 300,
            peakWindowRadiusMs: 300,
            minPeakSpeed: 1.0,        // torso-lengths/sec, on the smoothed signal
            boundaryFraction: 0.3,
            minPeakGapMs: 500
        )
    }

    private func analyzeBufferedSession() {
        guard mode == .coaching, let baseline, frames.count > 1 else { return }

        let analyzer = ForehandDriveDrillAnalyzer(
            baseline: baseline,
            rules: BaselineRuleFactory.shared.defaultRules(baseline: baseline),
            handedness: handedness,
            lang: lang,
            cadence: FeedbackCadencePolicy(minIntervalMs: 3000, maxIntervalMs: 5000),
            detector: defaultDetector(),
            cameraYawDeg: nil,        // per-rep auto-estimate (player moves their feet)
            maxCameraYawDeg: DrillCalibrator.shared.DEFAULT_MAX_CAMERA_YAW_DEG
        )
        let report = analyzer.analyze(sequence: makeSequence())

        // Session-level placement flag: UI shows "reposition camera" prompt.
        cameraPlacementOk = report.placementOk

        // The batch analyzer replays the whole buffer; only voice items newer
        // than what we already spoke. Cadence inside a batch is shared-side;
        // across batches the timestamp watermark keeps spacing correct.
        let fresh = report.feedback
            .compactMap { $0 as? SpokenFeedback }
            .filter { $0.timestampMs > lastSpokenSessionMs }
            .sorted { $0.timestampMs < $1.timestampMs }

        guard let latest = fresh.last else { return }
        lastSpokenSessionMs = latest.timestampMs
        lastSpokenMessage = latest.message
        feedbackLog.append(latest.message)
        speech.speak(latest.message)
    }
}
