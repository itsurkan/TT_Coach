# iOS RTMPose Backend — replace Apple Vision with the desktop-golden pipeline

Date: 2026-06-12 · Branch `2d` · Supersedes the Vision backend as the default on iOS.

## Context

Milestones 1–2 of the iOS port shipped an Apple Vision (`VNDetectHumanBodyPoseRequest`)
pose backend and verified it end-to-end (18 XCTests green on the iPhone 17 Pro simulator).
On-footage QA then showed Vision detection is materially worse than the RTMPose desktop
golden — e.g. `video_1`: Vision found a person in **44 / 207** frames vs RTMPose's near-full
coverage; `video_3`: 1309 / 1517; `video_4`: 829 / 1012. Sparse detection starves the
stroke detector and corrupts baselines.

Decision: make **RTMPose** the iOS pose backend, behind the existing `PoseBackend`
protocol. Vision stays compiled as a selectable fallback. The shared KMP module needs
**zero changes** — it already consumes schema-v2 COCO-17 keypoints regardless of producer.

## Firm decisions

- **Runtime: ONNX Runtime (Objective-C pod/SPM) with the CoreML Execution Provider.**
  Loads the *exact* ONNX files rtmlib runs on desktop → parity is byte-level on the models
  themselves; only preprocessing can differ. CoreML EP routes ops to ANE/GPU with automatic
  CPU fallback. Rejected: CoreML-native conversion (coremltools can subtly alter the SimCC
  head → forces full re-validation, no parity guarantee) and ncnn (most integration work, no
  parity benefit unless Android adopts the same engine).
- **Models: byte-exact match to the desktop golden** (`Body(mode="balanced")`):
  - Detector: **YOLOX-m** — `yolox_m_8xb8-300e_humanart-c2c7a14a.onnx` (97 MB), input 640×640.
  - Pose: **RTMPose-m** — `rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504.onnx`
    (52 MB), input 256×192, SimCC head.
  - Rationale: RTMPose is top-down and *requires* a person bbox; the desktop golden produces
    that box with YOLOX-m. Matching both models removes every detection-side variable, so the
    only thing the parity gate must catch is preprocessing drift. App-size/latency cost of the
    97 MB detector is accepted now and **measured on-device in Milestone 5**; swapping to a
    lighter detector is a known, gated fallback if M5 shows it's too slow — done with real
    numbers, not guessed up front.
- **Vision backend: kept** as a debug-selectable fallback. `VisionPoseBackend`,
  `VisionCoco17Mapper`, and their tests stay. The macOS `VisionPoseExport` tool stays.
- **Model size: RTMPose-m** (not `-s`), identical to the golden → existing fixtures, rep-count
  gates, and baselines transfer 1:1.

## Architecture

```
CVPixelBuffer ──▶ RTMPoseBackend.estimatePose(in:frameWidth:frameHeight:)
                    │
                    ├─ 1. YoloxDetector   : letterbox→640²  ▶ ORT ▶ decode+NMS ▶ best-person bbox
                    ├─ 2. bbox expand 1.25×  (rtmlib parity)
                    ├─ 3. RtmposeEstimator: affine-crop→256×192 ▶ ORT ▶ SimCC decode ▶ 17 kp (crop space)
                    ├─ 4. invert affine ▶ full-frame pixels ▶ normalize per axis (x/W, y/H)
                    └─ 5. [Keypoint2D] (COCO-17, score in [0,1])  ── unchanged shared contract
```

### Components (single-responsibility, unit-testable)

- **`RTMPoseBackend.swift`** — conforms to `PoseBackend`. Owns the two ORT sessions (created
  once, reused per frame). Orchestrates detect → crop → pose → un-map. Synchronous, runs on the
  camera frame queue (existing backpressure contract). Holds the detector-skip state machine.
- **`ORTSessionFactory.swift`** — builds an `ORTSession` from a bundled `.onnx` with the CoreML
  EP enabled and CPU fallback; central place for EP config. Throws on missing model → backend
  init fails cleanly (controller falls back to Vision).
- **`YoloxDetector.swift`** — pure-ish: pixel buffer → letterbox params → ORT run → grid/stride
  decode + score threshold + IoU NMS → highest-score person box in full-frame coords. The
  letterbox math, decode, and NMS are pure functions tested in isolation.
- **`RtmposeEstimator.swift`** — bbox + pixel buffer → affine warp params → ORT run → **SimCC
  decode** (argmax over x/y logit bins ÷ split ratio 2.0) → keypoints in crop space → invert the
  affine to full-frame pixels. Affine + SimCC decode are pure functions, tested with synthetic
  logits.
- **Detector-skip state machine** (inside `RTMPoseBackend`): mirrors rtmlib tracking — reuse the
  last expanded bbox while mean pose score stays high; re-detect on low score or every N frames
  (default N configurable, start at re-detect-every-frame for parity, relax in M5). Keeps the
  97 MB detector off the critical path most frames.

### Models in the bundle

- Copy the two `.onnx` files from `~/.cache/rtmlib/hub/checkpoints/` into
  `iosApp/TTCoach/Models/` and add them as bundle resources in `project.yml`.
- `iosApp/TTCoach/Models/MODELS.md` records source URLs (the rtmlib `body.py` `balanced`
  entries) + SHA-256 of each file, so the provenance is auditable and re-downloadable. No
  build-time download.

### Runtime integration (`project.yml`)

- Add `onnxruntime-objc` (CoreML EP included) via SPM package dependency to the `TTCoach`
  target (and to the new `RTMPoseExport` macOS tool target).
- Add the two `.onnx` files as resources of `TTCoach`.

## Parity gate (the real safety net)

A macOS CLI mirrors the desktop export but runs **the same Swift backend code**, proving the
Swift preprocessing matches rtmlib — not just that the models match.

- **`RTMPoseExport` macOS tool target** (sibling of `VisionPoseExport`): sources = the shared
  RTMPose Swift files + a `main.swift` driver. `AVAssetReader` full-fps frame extraction →
  `RTMPoseBackend` → schema-v2 JSON (`model: "rtmpose-m"`, empty `landmarks: []` = no person,
  4-decimal rounding), written as `<base>_poses_vision_rtm.json` (distinct suffix; never
  overwrites the Python golden `<base>_poses_rtm.json`).
- **jvmTest gate** `IosRtmposeParityTest.kt`: fixtures
  `shared/src/commonTest/resources/fixtures/andrii_1_ios_rtm.json` (+ `video_2_ios_rtm.json`),
  loaded via new `TestFixturesV2` loaders. Asserts:
  - `PoseJsonV2Parser` parses the iOS export unmodified.
  - detect→`ForwardStrokeFilter`→`RepFilter` forward-rep count is **within ±1** of the Python-RTM
    golden (tighter than Vision's ±2 — same models, so only preprocessing can drift).
  - `DrillCalibrator.calibrate` yields a plausible baseline (quality floor); log an
    iOS-RTM-vs-Python-RTM per-metric comparison table.
- **If it fails:** fix Swift preprocessing (letterbox/affine/SimCC/normalization) in the backend
  — never touch shared thresholds or the 0.3 score gate.

## Backend selection & fallback

- `DrillSessionController` default backend → `RTMPoseBackend()` (was `VisionPoseBackend()`).
- If `RTMPoseBackend` init throws (models missing / ORT EP unavailable), controller logs a
  warning and falls back to `VisionPoseBackend()` rather than crashing.
- A debug setting can force Vision (kept for A/B and the M5 latency comparison).

## Testing

- **XCTest (pure units):** letterbox forward+inverse, YOLOX decode + NMS (synthetic grid),
  bbox 1.25× expansion + clamp, affine warp forward+inverse, SimCC decode (synthetic logits →
  known peak), per-axis normalization. Mock `CVPixelBuffer` factory reused from the Vision tests.
- **XCTest (integration, simulator):** `RTMPoseBackend` returns 0 or 17 keypoints, coords in
  [0,1], repeated-call safety, no-person → empty array.
- **jvmTest:** the parity gate above (`./gradlew :shared:jvmTest`).
- **On-device smoke (Personal Team):** calibrate forehand drive from live reps → baseline
  derived → coaching speaks UA/EN feedback; confirm detection coverage is dense (the Vision
  failure mode is gone).

## Out of scope

- No shared KMP changes. No CoreML-native conversion. No detector swap (that's the M5-gated
  fallback, separate work). No Android changes. App Store / model-size optimization deferred.

## Risks

| Risk | Mitigation |
|---|---|
| Swift preprocessing drifts from rtmlib (letterbox/affine/SimCC) | Parity gate runs the *same* Swift code on desktop footage; ±1 rep tolerance catches drift before any device work |
| 97 MB detector too slow on phone | Measured on-device in M5; detector-skip state machine; lighter-detector fallback behind unchanged `PoseBackend` |
| ORT CoreML EP op-unsupported → silent CPU fallback (slow) | EP config logs which ops fell back; latency surfaced by M5 PerfMonitor |
| App size ~150 MB | Accepted for personal-device build; revisit at App Store milestone |
| ONNX Runtime SPM/pod integration friction with XcodeGen | Isolated to `project.yml`; Vision fallback keeps the app shippable if integration stalls |

## Verification

- Shared gate: `./gradlew :shared:jvmTest` green (incl. new parity test).
- iOS gates: `xcodegen generate` + `xcodebuild -scheme TTCoach -destination 'generic/platform=iOS Simulator' build` + XCTest.
- Desktop CLI: `xcodebuild -scheme RTMPoseExport build`; run on `Videos/andrii_1`, `video_2`,
  exports validate (frame count, 0-or-17 landmark arrays, head above hips).
- On-device smoke: dense detection + spoken feedback, no Vision sparsity.
