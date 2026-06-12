import SwiftUI

/// App entry point. Mirrors the Android MVP flow (2D drill coaching):
/// camera capture → PoseBackend → shared KMP drill logic → voice/text feedback.
///
/// All drill/analysis logic lives in the shared Kotlin module (TTCoachShared
/// framework); Swift code here is orchestration + platform I/O only.
@main
struct TTCoachApp: App {
    var body: some Scene {
        WindowGroup {
            CoachingView()
        }
    }
}
