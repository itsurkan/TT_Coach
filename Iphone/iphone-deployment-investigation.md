# Deploying TT_Coach_AI to iPhone — Investigation Report

Date: 2026-06-11 (automated scheduled task). Based on a repo audit (branch `2d`, post-pivot state) and web research. iOS is already declared a "firm future target" in the 2D pivot design; this report covers what's actually needed to get the app running on an iPhone.

## TL;DR

The shared KMP module is essentially iOS-ready today (pure Kotlin, zero external deps, no `java.*` imports, no expect/actual). The real work is: (1) add iOS targets + XCFramework to `shared/build.gradle.kts`, (2) build an iOS app shell in Xcode (Swift/SwiftUI) with camera capture, RTMPose inference via ONNX Runtime + CoreML EP (or a CoreML-converted model), and TTS, and (3) Apple account/signing logistics — free Apple ID is enough for on-device testing, $99/yr Apple Developer Program for TestFlight/App Store. Estimated effort for a personal-device MVP: roughly 2–4 weeks of focused work, dominated by the pose-inference port, and it largely overlaps with Phase 3 (the planned Android RTMPose port), since both need the same `PoseBackend` abstraction.

## 1. What the repo audit found

**Ready for iOS:**

- `shared/` commonMain has 60 Kotlin files, zero external dependencies, no `java.*` imports, uses `kotlin.math` — compiles for Kotlin/Native as-is. All Phase 2 drill logic (parser, angle calcs, stroke detection, calibration, feedback engine, UA+EN message catalog) lives there.
- No expect/actual declarations exist yet, so nothing platform-specific to stub.
- Kotlin 2.2.21 and Gradle 9.3.1 are current enough for iOS targets (note: CLAUDE.md says Kotlin 2.1.0 — actual build files say 2.2.21).

**Missing for iOS:**

- `shared/build.gradle.kts` declares only `androidTarget()` and `jvm()`. No `iosArm64()` / `iosSimulatorArm64()`, no XCFramework config.
- There is no iOS app at all. Everything user-facing is in `app/` (Android): CameraX capture, MediaPipe live pipeline (frozen), Room persistence, Firebase auth/Firestore, Android TTS, UI.
- No `PoseBackend` interface yet (planned for Phase 3) — the seam that would let iOS plug in its own inference.
- The shared module's JSON parsing is hand-rolled (`PoseJsonV2Parser`) — good news: no kotlinx-serialization dependency to worry about on Native.

## 2. Hardware/account prerequisites

| Item | Requirement | Cost |
|---|---|---|
| Mac | You have an M4 Mac — fine. Needs current Xcode (free, App Store) | $0 |
| Run on your own iPhone | Free Apple ID "Personal Team" signing in Xcode | $0 |
| Personal Team limits | Provisioning expires every 7 days (rebuild from Xcode to renew), max 3 devices, 10 App IDs, no TestFlight, no push/CloudKit entitlements | — |
| TestFlight beta (testers, e.g. German club players) | Apple Developer Program | $99/yr |
| App Store release | Apple Developer Program + App Review | $99/yr |

For an MVP you test yourself, the free Personal Team path is enough and costs nothing — the 7-day re-sign cycle is the only annoyance. Enroll in the paid program only when you want testers who aren't plugging into your Mac. TestFlight supports up to 10,000 external testers; builds expire after 90 days.

iOS version floor: ONNX Runtime's CoreML execution provider needs iOS 13+; realistically target iOS 16+ to simplify SwiftUI and AVFoundation work.

## 3. Step 1 — add iOS targets to `shared/`

```kotlin
// shared/build.gradle.kts
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

kotlin {
    androidTarget { /* existing */ }
    jvm()

    val xcf = XCFramework("TTCoachShared")
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "TTCoachShared"
            xcf.add(this)
        }
    }
}
```

Then `./gradlew :shared:assembleTTCoachSharedXCFramework` produces an `.xcframework` to drop into Xcode (or use the `embedAndSignAppleFrameworkForXcode` direct-integration task / SPM export). Because commonMain is dependency-free, this step is low-risk — the main verification is `./gradlew :shared:iosSimulatorArm64Test` passing (commonTest runs on Native; the jvmTest fixture loaders stay JVM-only since Native has no ClassLoader — fixture-driven tests would need a path-based loader or stay JVM-gated).

Build caveat: iOS targets only compile on macOS — fine locally, but CI (if Linux) must skip or gate them.

## 4. Step 2 — pose inference on iOS (the hard part)

This is the same problem as Phase 3 (Android RTMPose port), with iOS-specific backends. Options, in recommended order:

**Option A — ONNX Runtime + CoreML execution provider (recommended).** ONNX Runtime ships prebuilt iOS binaries via CocoaPods/SPM with CoreML and XNNPACK acceleration; CoreML EP uses the Neural Engine on modern iPhones. You already export RTMPose to ONNX on desktop via MMPose/MMDeploy, so the model artifact pipeline is identical to the planned Android ONNX path — one model format for both platforms. RTMPose-s does 70+ FPS on a Snapdragon 865 via ncnn, so an iPhone Neural Engine will have ample headroom for your 3–5 s feedback cadence.

**Option B — native CoreML conversion.** MMDeploy supports a CoreML backend (RTMPose has been converted to CoreML per the RTMPose project docs), giving a `.mlmodel`/`.mlpackage` you run with Apple's CoreML API directly. Most "Apple-native" performance, but adds a second model-conversion path to maintain alongside Android, and MMDeploy's iOS documentation is thin (open GitHub issues asking how).

**Option C — ncnn on iOS.** ncnn builds for iOS and is the documented mobile path in the RTMPose project, but its iOS examples are sparse vs Android, and it duplicates the runtime choice if Android ends up on ONNX Runtime Mobile.

**Key recommendation:** when Phase 3 defines the `PoseBackend` interface, define it in `shared/` commonMain (expect/actual or plain interface) so the iOS app provides an ONNX-Runtime-Swift implementation and Android provides its own — the drill logic above the interface is already shared. Also note: both detector (RTMDet-nano) and pose (RTMPose) stages need porting, or replace detection with a cheaper person-tracking heuristic on-device.

## 5. Step 3 — iOS app shell (Swift/SwiftUI)

Android `app/` components and their iOS equivalents:

| Android (`app/`) | iOS equivalent | Effort |
|---|---|---|
| CameraX capture | AVFoundation (`AVCaptureSession`) | Low — well-trodden |
| MediaPipe live pipeline (frozen) | Not ported — RTMPose backend instead (matches Phase 3 plan) | — |
| Android TTS | `AVSpeechSynthesizer` (supports uk-UA and en voices) | Low |
| Room (sessions, baselines, drill_configs) | Room 2.7+ now supports KMP/iOS natively — or SQLDelight, or plain file/JSON persistence for MVP | Low–Med |
| Firebase auth/Firestore | Firebase iOS SDK exists, but consider skipping auth entirely for a personal-device MVP | Med (or $0 if skipped) |
| UI (calibration flow, training screen) | SwiftUI rewrite | Med |
| Ball tracking (frozen) | Out of scope (Stage 2) | — |

Required Info.plist entries: `NSCameraUsageDescription` (mandatory — app crashes without it), `NSSpeechRecognitionUsageDescription` only if voice input is ever added, microphone description if audio-contact detection (Stage 2) returns.

## 6. Suggested sequence

1. **Now (cheap, de-risks everything):** add `iosArm64()`/`iosSimulatorArm64()` + XCFramework to `shared/build.gradle.kts`; run commonTest on the iOS simulator target. Proves the 60-file shared module really is Native-clean. ~half a day.
2. **With Phase 3 design:** define `PoseBackend` in commonMain so Android and iOS ports share the contract.
3. **iOS spike:** bare Xcode project, free Personal Team signing, link the XCFramework, run `PoseJsonV2Parser` + drill analyzer on a bundled fixture JSON, print feedback messages. Proves the full shared stack on a real iPhone with zero inference work.
4. **Inference port:** ONNX Runtime iOS pod + CoreML EP, RTMDet-nano + RTMPose-s ONNX models, wire camera frames → keypoints → `PoseFrame2D`.
5. **App shell:** AVFoundation camera, `AVSpeechSynthesizer` feedback, minimal SwiftUI calibration/drill screens.
6. **When external testers are needed:** enroll in Apple Developer Program ($99/yr), distribute via TestFlight.

## 7. Risks / open questions

- **MMDeploy iOS path is under-documented** — ONNX export is solid, but if Option B (native CoreML) is chosen, expect to debug conversion yourself.
- **Native test infrastructure:** jvmTest fixture loading (ClassLoader) doesn't exist on Kotlin/Native; fixture-driven E2E tests stay JVM-only unless a path-based loader is added. Acceptable — JVM remains the agent feedback loop.
- **CLAUDE.md drift:** says Kotlin 2.1.0; build uses 2.2.21. Worth fixing when touching docs.
- **Per-frame performance:** RTMPose-s benchmarks suggest real-time is comfortable, but the detector+pose two-stage pipeline at 30 fps on older iPhones is unvalidated — the 3–5 s cadence means you can subsample frames if needed.
- **Firebase on iOS** adds setup weight (GoogleService-Info.plist, etc.); recommend deferring auth/cloud sync past the iOS MVP.

## Sources

- [Apple Developer Program — what's included](https://developer.apple.com/programs/whats-included/)
- [Apple Developer Program](https://developer.apple.com/programs/)
- [Choosing a Membership — Apple Developer](https://developer.apple.com/support/compare-memberships/)
- [iOS Distribution Guide 2026: TestFlight, App Store & Enterprise](https://foresightmobile.com/blog/ios-app-distribution-guide-2026)
- [TestFlight costs guide](https://www.metacto.com/blogs/the-complete-guide-to-testflight-costs-integration-and-maintenance)
- [ONNX Runtime — deploy on mobile](https://onnxruntime.ai/docs/tutorials/mobile/)
- [ONNX Runtime — CoreML execution provider](https://onnxruntime.ai/docs/execution-providers/CoreML-ExecutionProvider.html)
- [ONNX Runtime — build for iOS](https://onnxruntime.ai/docs/build/ios.html)
- [RTMPose project README (mmpose)](https://github.com/open-mmlab/mmpose/blob/main/projects/rtmpose/README.md)
- [MMDeploy iOS deployment issue #2367](https://github.com/open-mmlab/mmdeploy/issues/2367)
- [KMP — iOS integration methods](https://kotlinlang.org/docs/multiplatform/multiplatform-ios-integration-overview.html)
- [KMP — build final native binaries (XCFramework)](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html)
- [KMP — Swift package export](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-export.html)
- [Free personal team signing notes](https://takazudomodular.com/pj/zudo-tauri/docs/mobile/ios-signing-free-team/)
