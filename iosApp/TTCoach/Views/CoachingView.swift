import SwiftUI

/// Main (and only) MVP screen: live camera preview with drill controls and
/// the latest coaching feedback overlaid. Mirrors the Android TrainingActivity
/// flow at MVP scope: calibrate → coach → spoken + on-screen cues.
struct CoachingView: View {

    @StateObject private var controller = DrillSessionController()

    var body: some View {
        ZStack {
            CameraPreviewView(session: controller.camera.session)
                .ignoresSafeArea()

            VStack {
                statusBar
                Spacer()
                if !controller.cameraPlacementOk {
                    placementWarning
                }
                feedbackPanel
                controls
            }
            .padding()
        }
        .onAppear { controller.startCamera() }
    }

    private var statusBar: some View {
        Text(controller.statusText)
            .font(.footnote)
            .padding(8)
            .background(.black.opacity(0.6))
            .foregroundColor(.white)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    /// Surfaced when the shared analyzer reports placementOk = false for the
    /// session (camera too far off the side view → cues are withheld).
    private var placementWarning: some View {
        Label("Move the camera to a side view", systemImage: "camera.metering.unknown")
            .font(.callout.bold())
            .padding(10)
            .background(.orange.opacity(0.9))
            .foregroundColor(.black)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .padding(.bottom, 4)
    }

    private var feedbackPanel: some View {
        Group {
            if !controller.lastSpokenMessage.isEmpty {
                Text(controller.lastSpokenMessage)
                    .font(.title3.bold())
                    .multilineTextAlignment(.center)
                    .padding(12)
                    .background(.black.opacity(0.7))
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.bottom, 8)
            }
        }
    }

    private var controls: some View {
        HStack(spacing: 12) {
            switch controller.mode {
            case .idle:
                Button("Calibrate") { controller.startCalibration() }
                    .buttonStyle(.borderedProminent)
                Button("Coach") { controller.startCoaching() }
                    .buttonStyle(.borderedProminent)
                    .disabled(!controller.hasBaseline)
            case .calibrating:
                Button("Finish calibration") { controller.finishCalibration() }
                    .buttonStyle(.borderedProminent)
                    .tint(.green)
            case .coaching:
                Button("Stop") { controller.stopCoaching() }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
            }
        }
        .padding(.bottom, 16)
    }
}

#Preview {
    CoachingView()
}
