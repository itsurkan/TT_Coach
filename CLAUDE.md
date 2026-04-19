# TT_Coach_AI Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-04-18

## Active Technologies
- Kotlin 2.1.0 (KMP shared module + Android app module) + CameraX 1.5.3, OpenCV Android SDK 4.9.0, MediaPipe tasks-vision 0.10.14, kotlinx-coroutines 1.10.2 (002-ball-tracking)
- Room 2.6.1 (training sessions, ball detection, personal baselines) (002-ball-tracking, 003-stage1-calibration)
- `org.json` for Room `@TypeConverter` JSON serialization (003-stage1-calibration)

- Kotlin 2.1.0 (upgrade to KMP plugin from `org.jetbrains.kotlin.android`) + MediaPipe tasks-vision 0.10.14 (Android-only), kotlinx-coroutines 1.10.2, Firebase BOM 34.8.0 (001-kmp-shared-refactor)

## Project Structure

```text
src/
tests/
```

## Commands

# Add commands for Kotlin 2.1.0 (upgrade to KMP plugin from `org.jetbrains.kotlin.android`)

## Code Style

Kotlin 2.1.0 (upgrade to KMP plugin from `org.jetbrains.kotlin.android`): Follow standard conventions

## Recent Changes
- 003-stage1-calibration: Personal Baseline Calibration (Stage 1 · Phase 1). Shared: `PersonalBaseline`, `MetricStats`, `BaselineDeriver` (2σ single-pass outlier exclusion, qualityScore = 1 − mean(CV)), `BaselineRule` sealed type (data only). App: Room persistence (`PersonalBaselineEntity`, `BaselineConverters` via `org.json`, `PersonalBaselineDao` w/ `archiveAndInsert`, `PersonalBaselineRepository` local-only), capture pipeline (`CalibrationStateManager` w/ live 2σ outlier flagging on `outlierEvents`, `PoseAnalysisProcessor.Mode.CALIBRATION` with phase-boundary reconstruction), UI flow (`CalibrationActivity` + onboarding/capture/review fragments), dev tooling (`BaselineDebugActivity`, `BaselineDumpReceiver`).
- 002-ball-tracking: Added Kotlin 2.1.0 (KMP shared module + Android app module) + CameraX 1.5.3 (camera control + exposure), OpenCV Android SDK 4.9.0 (color/shape detection), MediaPipe tasks-vision 0.10.14 (existing pose detection), kotlinx-coroutines 1.10.2

- 001-kmp-shared-refactor: Added Kotlin 2.1.0 (upgrade to KMP plugin from `org.jetbrains.kotlin.android`) + MediaPipe tasks-vision 0.10.14 (Android-only), kotlinx-coroutines 1.10.2, Firebase BOM 34.8.0

<!-- MANUAL ADDITIONS START -->

## Commands

**Build**
- `./gradlew :app:assembleDebug` — build debug APK
- `./gradlew :app:assembleRelease` — build release APK

**Tests**
- `./gradlew test` — all JVM unit tests (no device needed)
- `./gradlew :shared:jvmTest` — shared KMP tests only
- `./gradlew :app:test` — Android unit tests only
- `./gradlew test --tests TrainingActivityTest` — run specific class
- `./gradlew connectedAndroidTest` — instrumented tests (device/emulator required)

**Poses viewer (React + Vite debug tool)**
- `cd poses_viewer && npm run dev` — http://localhost:5780, overlays pose/ball/contact/label JSON on video frames

**Python scripts (`scripts/`)**
- `training/train_ball_yolo.ipynb` — YOLOv11-nano training on Colab T4
- `contacts/detect_contacts.py` + `filter_contacts_by_pose.py` — audio contact detection, then wrist-velocity filter
- `frames/extract_frames_320.py` — 320×320 frames for training
- `poses/export_poses.py` — pose landmarks video→JSON
- `video/process_video.py` — end-to-end video processing

## Project structure

```
app/                         # Android app (UI, sensors, TFLite, Firebase)
  src/main/java/com/ttcoachai/
    processors/              # Frame-by-frame pipelines (PoseAnalysisProcessor)
    managers/                # Session state, camera, UI controllers
    repository/              # Dual-source (Room + Firestore) data access
    db/                      # AppDatabase, DAOs
    tracking/                # BallDetectorV1..V6 (V6 current, V5 deprecated)
    services/                # MotionAnalyzer, FeedbackGenerator, StrokeDetector
    helpers/                 # PoseLandmarkerProcessor (MediaPipe)
    core/logging/            # Logger/Analytics/CrashReporter interfaces
    models/                  # Room entities (TrainingSession, UserProgress, PersonalBaselineEntity)
    calibration/             # CalibrationActivity + onboarding/capture/review fragments (Stage 1 · Phase 1)
    debug/                   # BaselineDebugActivity, BaselineDumpReceiver, BaselinePreviewActivity, AssetPoseFrameLoader (runtime-gated by FLAG_DEBUGGABLE)
  src/main/assets/           # TFLite models (ball_yolo, pose_landmarker)
  src/test/                  # JVM unit tests (Robolectric)
  src/androidTest/           # Instrumented tests (Espresso)

shared/                      # KMP module (platform-independent)
  src/commonMain/kotlin/com/ttcoachai/shared/
    analysis/                # StrokeAnalyzer, AngleCalculations, MetricCalculations
    detection/               # JsonStrokeDetector, StrokePhaseDetector
    tracking/                # TimelineSynchronizer, TrajectoryFilter, TrajectorySegmenter
    models/                  # FeedbackItem, AnalysisResult, PoseFrame, Landmark3D
  src/commonTest/resources/fixtures/  # JSON pose/ball fixtures for unit tests

poses_viewer/                # React + Vite debug/labeling UI
models/trained/              # Python-trained models (best_yolo.pt)
Videos/                      # Test videos + per-video JSON (*_poses, *_ball_yolo, *_contacts, *_labels)
scripts/                     # Python data/training scripts
Mockups/                     # UI mockups
```

## Conventions

**Naming suffixes** (meaning drives placement):
- `*Manager` — stateful singleton (session, camera, settings, UI)
- `*Processor` — frame-by-frame stateful pipeline
- `*Analyzer` — pure business logic, no state
- `*Detector` — inference (versioned: `V6` current, `V5` deprecated)
- `*Repository` — dual-source data (Room offline-first → Firestore sync)

**KMP split rule:**
- Platform-independent logic (pose math, rule evaluation, models) → `shared/commonMain`
- Android-only (UI, CameraX, MediaPipe, TFLite, Firebase) → `app/`
- New analysis/detection logic goes in `shared/` by default; put in `app/` only if it needs Android APIs

**Room:** entities in `app/.../models/`, DAOs in `app/.../db/`, central `AppDatabase` singleton via `getDatabase(context)`. Currently uses `fallbackToDestructiveMigration()` — OK for dev, will wipe local data on schema bump.

**Feedback pipeline (trace this flow when debugging):**
`PoseLandmarkerProcessor.detectLiveStream()` → `PoseAnalysisProcessor.processResults()` → `MotionAnalyzer.analyze()` → `FeedbackGenerator.generateFeedback()` → `TrainingStateManager.recordFeedback()` → UI callback

**Tests:** unit tests under `app/src/test/` and `shared/src/jvmTest/`; shared JSON fixtures in `shared/src/commonTest/resources/fixtures/`; load pose frames via `JsonTestUtils`. Instrumented tests under `app/src/androidTest/`.

## Gotchas

- **BallDetectorV6 requires top-half ROI crop** — full-frame inference drops to 26.8% accuracy. Always crop via `ROIManager` before `detect()`. [app/.../tracking/BallDetectorV6.kt](app/src/main/java/com/ttcoachai/tracking/BallDetectorV6.kt), [README.md](README.md)
- **YOLO confidence threshold is 0.25, not 0.5** — tuned for this training set; changing without re-evaluation regresses precision/recall. [BallDetectorV6.kt:31](app/src/main/java/com/ttcoachai/tracking/BallDetectorV6.kt#L31)
- **Ball coordinates need dual transform** — model outputs in 320×320 ROI space, must convert back through ROI bounds to full-frame normalized coords before overlay. [BallDetectorV6.kt:182-183](app/src/main/java/com/ttcoachai/tracking/BallDetectorV6.kt#L182-L183)
- **BallDetectorV5 is deprecated** — kept for historical comparison (6.6% vs V6's 86.3%). Don't call from live pipeline. [README.md:60](README.md#L60)
- **MediaPipe landmarks are normalized [0,1] image coords** — not pixels. Rotation/centering handled in [PoseLandmarkerProcessor](app/src/main/java/com/ttcoachai/helpers/PoseLandmarkerProcessor.kt).
- **TrainingStateManager is a volatile singleton** — double-checked locking; not safe for concurrent in-place mutation. Use synchronized or coroutine-scoped updates. [TrainingStateManager.kt:35-39](app/src/main/java/com/ttcoachai/managers/TrainingStateManager.kt#L35-L39)
- **TFLite GPU delegate silently falls back to CPU** — no exception on GPU-unavailable devices; check logcat for `GPU delegate unavailable`. [BallDetectorV6.kt:60-69](app/src/main/java/com/ttcoachai/tracking/BallDetectorV6.kt#L60-L69)
- **Room uses `fallbackToDestructiveMigration()`** — schema changes wipe local DB. Switch to explicit migrations before Play Store release. [AppDatabase.kt:26](app/src/main/java/com/ttcoachai/db/AppDatabase.kt#L26)
- **Pose fixture schema** — `frames[].landmarks[].{x, y, z, visibility}`. `JsonTestUtils` will fail if schema drifts. [shared/src/commonTest/resources/fixtures/](shared/src/commonTest/resources/fixtures/)
- **GoogleSignIn relies on `default_web_client_id` string** — auto-generated by google-services plugin; init fails silently if missing. [AuthRepository.kt:32-36](app/src/main/java/com/ttcoachai/repository/AuthRepository.kt#L32-L36)
- **Live `DetectedStroke` is reconstructed, not detected** — the live `PoseAnalysisProcessor` only receives a current phase enum per frame from `StrokePhaseDetector`, so phase-boundary frames are tracked via transition observation and the `DetectedStroke` is assembled at stroke finalization. Only boundary frames + `strokeDurationMs` are populated; velocity/peak-value fields stay at 0f and are unused by `BaselineDeriver`. [PoseAnalysisProcessor.kt](app/src/main/java/com/ttcoachai/processors/PoseAnalysisProcessor.kt)
- **`CameraFragment` skips its own processor when hosted by a `LandmarkerListener` activity** — if you add a new activity that also implements `PoseLandmarkerHelper.LandmarkerListener` and hosts `CameraFragment`, add it to the carve-out check in [CameraFragment.kt:164](app/src/main/java/com/ttcoachai/fragment/CameraFragment.kt#L164) or you'll get double-processing. Current exemptions: `TrainingActivity`, `CalibrationActivity`.
- **`BaselineConverters` uses `org.json`, not kotlinx-serialization** — the shared module stays dependency-free, and `MetricStats` doesn't need `@Serializable`. When adding new Room columns, add explicit converters here. [BaselineConverters.kt](app/src/main/java/com/ttcoachai/db/BaselineConverters.kt)
- **`BaselineRuleFactory` is the single source of truth for default rule derivation** — `PersonalBaseline → List<BaselineRule>` only happens here (2σ consistency per metric, 25% rhythm per phase). When Stage 1 Phase 2 adds a production rule evaluator, promote its reverse operation (rule + frame → pass/fail) to absorb [FrameRuleEvaluator](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/FrameRuleEvaluator.kt). [BaselineRuleFactory.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineRuleFactory.kt)
- **Phase 7 editor replays bundled fixtures, not captured reps** — `CalibrationStateManager` intentionally doesn't persist raw pose frames (only derived strokes + analyses). `BaselinePreviewActivity` therefore loads from `assets/fixtures/forehand_drive.json` via [AssetPoseFrameLoader](app/src/main/java/com/ttcoachai/debug/AssetPoseFrameLoader.kt). When adding real captured-rep replay, you'll need a separate raw-frame persistence path — don't expect it through the state manager.
- **Editor renders the canonical stroke, not raw frames** — [CanonicalStrokeLoader](app/src/main/java/com/ttcoachai/debug/CanonicalStrokeLoader.kt) runs [JsonStrokeDetector](shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/JsonStrokeDetector.kt) on the fixture and collapses N detected strokes into a time-normalized mean via [MeanStrokeBuilder](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/MeanStrokeBuilder.kt). Today N=1 from the bundled fixture; the same path generalizes when real multi-rep calibration lands.
- **Editor viewport is 7:30, not 6** — `BaselinePreviewActivity` bakes a fixed 45° yaw into `PoseTransformer.apply(...)` so the figure renders at a three-quarter angle (right side visible, better for showing a right-handed forehand). Change via `PoseTransformer.DEFAULT_VIEW_CAMERA_YAW_DEG`.
- **Drill-shape overrides live in Room table `drill_configs`** — authored in the Phase 7 editor, keyed by `drillType`, stores the 7 slider deltas. Today read-only from the editor; once Stage 1 Phase 2 rule evaluator lands, apply these as overrides on top of the derived [PersonalBaseline] so drill feedback can compare against the coach-tuned shape rather than raw calibration. [DrillConfigEntity](app/src/main/java/com/ttcoachai/models/DrillConfigEntity.kt), [DrillConfigRepository](app/src/main/java/com/ttcoachai/repository/DrillConfigRepository.kt).
- **AppDatabase is v3** — `drill_configs` table added in v3. Still using `fallbackToDestructiveMigration()`; schema bump will wipe local data.

<!-- MANUAL ADDITIONS END -->

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->
