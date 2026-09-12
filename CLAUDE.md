# TT_Coach_AI Development Guidelines

Last updated: 2026-07-25 (Phase 3 live backend swap). Read the "Current direction" section first — it overrides older context below.

## Current direction — 2D PIVOT (2026-06-10, branch `2d`)

The project pivoted from MediaPipe-3D + ball-tracking to a **2D in-plane joint-angle coaching MVP on RTMPose, desktop-first**. Fixed/structured drills (first: forehand drive, side camera), voice/text feedback at 3–5 s cadence, reference angles derived from the player's **personal baseline** (003 calibration path) — calibrate to the player's technique, don't re-teach.

**Frozen, not deleted** (returns post-MVP as Stage 2 — don't modify, don't delete, don't call from new code): `BallDetectorV1–V6`, `ROIManager`, trajectory code (`TimelineSynchronizer`/`TrajectoryFilter`/`TrajectorySegmenter`), audio-contact + frame-extraction Python scripts, YOLO training.

**Phase status:**
- **Phase 1 — desktop pose pipeline: DONE.** `scripts/poses/export_poses_rtmpose.py` (RTMPose-m + RTMDet-nano via MMPose, Mac M4) exports pose JSON **schema v2** (COCO-17; `--feet` flag → Halpe26 with foot keypoints). poses_viewer renders COCO-17/Halpe26 skeletons with an RTM header toggle.
- **Phase 2 — drill logic in shared KMP: DONE (executed).** `models/` 2D types (Keypoint2D, PoseFrame2D, PoseSequence2D, Topology, Coco17, Handedness, Stroke2D, ViewGeometry w/ xScale); `io/PoseJsonV2Parser` (strict, field-order tripwire); `analysis/AngleCalculations2D` (xScale-corrected in-plane angles, facing-normalized torso lean), `analysis/CameraAngleEstimator` (per-stroke |yaw| from shoulder foreshortening); `detection/StrokeDetector2D` (torso-lengths/sec, ms windows, keep-max NMS, valley-clamped boundaries); `BaselineDeriver.deriveFromMetrics`; `drill/` (DrillMetrics extractAtPeak ±70ms median, SanityBounds, ForwardStrokeFilter speed-dominance, RepFilter banding, DrillFeedbackEngine, FeedbackMessageCatalog UA+EN, FeedbackCadencePolicy 3–5s, DrillCalibrator w/ per-rep yaw gate + CameraPlacementException, ForehandDriveDrillAnalyzer). Fixtures: full-fps `*_rtm.json` (andrii_1 @17ms, video_2 @20ms) + TestFixturesV2. E2E exit gate green (15 forward reps from 23 raw peaks on andrii_1).
- **Phase 3 — Android port: DONE (2026-07-03); live backend swapped RTMPose → MediaPipe 2026-07-25 (see "MediaPipe replaces RTMPose as the live pose backend" below).** `app/src/main/java/com/ttcoachai/pose/`: `PoseBackend` interface (unchanged seam) — originally implemented by `RtmposeBackend` orchestration on ONNX Runtime Mobile 1.20 (arm64-v8a) with `YoloxDetector` person detect + `RtmposeEstimator` keypoint decode (`OrtSessionFactory`), all now deleted and replaced by `PoseBackendFactory` → `MediaPipePoseLandmarkerBackend`; `LivePoseFrameProcessor` camera bridge; `LiveDrillActivity`/`LiveTrainingController` live drill with baseline save — the main training screen has run this live drill since `523161f`; `Coco17OverlayView` skeleton overlay; voice feedback via `PresetVoiceController` (recorded preset clips) with `DrillTtsController` TTS fallback. Later features build on it (e.g. knee-bend live analysis).
- **Phase 4 — AI Coach (cloud-LLM premium): VALIDATED 2026-07-22, NOT STARTED (Phase 3 prerequisite met; parked while current-state delivery is the focus).**
  Post-session LLM coach report + "Ask the coach" chat, grounded in the player's PersonalBaseline
  (calibrate-don't-re-teach positioning). Subscription-gated: $11.99–12.99/mo + annual ~$79/yr
  (unit econ via `pitch/unit_economics.py`: $12 ARPU + annual-plan churn ~8% → LTV:CAC 3.0×;
  COGS ≈ $0.4–0.5/user/mo on Sonnet 5 with prompt caching). **Real-time cloud-LLM feedback
  REJECTED — do not re-propose:** no shipped competitor does it (SwingVision/Sportsbox/Mustard/
  SpinCoach are all post-session; Whoop/Strava LLMs are async-only), a 1.5–5s cloud round-trip vs
  ~200ms motor reaction lands cues 1–2 strokes late (negative transfer), and per-user cost kills
  margin; real-time stays on-device (existing 3–5s cue catalog). Payload = derived per-rep metrics
  + baseline (~KB per session), **never raw poses** (2–3M tokens/session — economically
  impossible). Ball/table/racket detection stays deferred to Stage 2 (no value post-session
  without ball-interaction data). Remaining prerequisites before implementation: real Google Play
  Billing (`SubscribeActivity` is currently a mock, nothing is gated), a thin backend proxy
  holding the Anthropic API key (repo has zero backend code), server-side entitlement (Firestore
  `isPremium` exists but unused). Prompt must mirror the trust rule (precise degrees only for the
  5 in-plane metrics). Flow when picked up: brainstorming → spec → plan → subagent execution,
  sliced billing → backend proxy → report → chat → UI (Session Review `6b`).

**Canonical docs (read in this order when orienting):**
1. [docs/superpowers/specs/2026-06-10-2d-pivot-design.md](docs/superpowers/specs/2026-06-10-2d-pivot-design.md) — pivot decisions + phase plan
2. [docs/tt-coach-ai-context.md](docs/tt-coach-ai-context.md) — consolidated research & rationale (RTMPose vs MediaPipe, 2D sufficiency, trust rule, camera placement)
3. [docs/pose_json_schema_v2.md](docs/pose_json_schema_v2.md) — pose JSON schema v2 contract
4. [docs/superpowers/plans/2026-06-10-phase2-drill-logic-shared-kmp.md](docs/superpowers/plans/2026-06-10-phase2-drill-logic-shared-kmp.md) — current implementation plan (task-by-task, TDD)

Docs dated April 2026 and earlier (ball tracking, MediaPipe calibration UI, MVP trackers) describe the **pre-pivot** state — still accurate as history and for frozen code, but not current direction.

## Android UI redesign (gold-dark) — Slice 1 DONE (2026-07-01)

Parallel track: restyling the existing Material 3 Android `app/` to a gold-on-dark "house system"
from the claude.ai/design project **"Table Tennis Coach AI Redesign"** (project UUID
`feb1eaea-d763-41c9-86fe-1262790d7291`, read via the DesignSync tool). **`app/` UI resources are
therefore no longer strictly frozen** — but the pose/ball/trajectory pipeline code stays frozen
(this track touches presentation only: colors/type/shape/styles/layouts + debug UI). Built in slices:
- **Slice 1 — design-system foundation: DONE.** Colors (dark+light), bundled Inter Tight +
  JetBrains Mono, shape appearances, `TTC.*` component styles, and a debug preview harness
  `DesignSystemPreviewActivity` (`adb shell am start -n com.ttcoachai/.debug.DesignSystemPreviewActivity`,
  FLAG_DEBUGGABLE-gated). Spec: [docs/superpowers/specs/2026-07-01-android-gold-dark-foundation-design.md](docs/superpowers/specs/2026-07-01-android-gold-dark-foundation-design.md);
  plan: [docs/superpowers/plans/2026-07-01-android-gold-dark-foundation.md](docs/superpowers/plans/2026-07-01-android-gold-dark-foundation.md);
  verbatim tokens: [docs/design/design-tokens-source.md](docs/design/design-tokens-source.md).
- **Slices 2–4 — PENDING.** Slice 2: restyle existing screens (Dashboard/Progress/Drills/Settings/Profile/History).
  Slice 3: new screens (Session Review `6b`, Feedback `11a`, Detection `11b`, New/Clone exercise forms `10c`/`10d`).
  Slice 4: Live Session `1a` (needs the **parent** design doc — the `Live Session.dc.html` canvas only
  contains the surrounding screens, not the `1a`/`2a` capture screen itself).

## Community Drills (shipped 2026-07-23)

Not frozen — a live Android app feature. Users publish their custom drills to a public Firestore
collection, browse/search/sort, rate 1–5 stars, preview, and copy into their own local drills.
Direct-from-client Firestore, **no backend** (consistent with repo's zero-backend state).
- **Firestore:** collection `community_drills/{docId}` + `ratings/{uid}` subcollection; rating aggregates
  (`ratingSum`/`ratingCount`) maintained by a client `runTransaction` — **client-trusted aggregates**
  (accepted v1 risk; rules constrain field-set + star range only). `firestore.rules` at repo root is
  **documentation-as-code — manual deploy** (`firebase deploy --only firestore:rules`, or console);
  `firebase.json`/`.firebaserc` now exist (project `ttcoachai`).
- **Room:** `CustomDrillEntity.sharedCommunityId` (null=private) → AppDatabase **v8** (still destructive fallback).
- **Code:** pure logic in `app/.../util/{CommunityDrillSort,RatingAggregate,CommunityDrillCopier}` +
  `models/{CommunityDrill,CommunityDrillMapper}` (JVM-tested); Firestore I/O in
  `repository/CommunityDrillRepository`; UI = publish/unshare rows in the Drills long-press menu
  (`DrillsFragment`), `ui/CommunityDrillsActivity` (browse) → `ui/dialogs/CommunityDrillDetailSheet`
  (preview/rate/copy); entry card on the Drills tab. Local-only fields (`drillType`, `baselineId`)
  **never travel to Firestore**; a copied drill is fresh/unlinked (`custom_<ms>` id).
- Plan: [docs/superpowers/plans/2026-07-22-community-drills.md](docs/superpowers/plans/2026-07-22-community-drills.md).

## Pose data upload (shipped 2026-07-23)

Every RTM training session's full per-frame pose stream is captured to a local gzipped
schema-v2 JSON and uploaded to Firebase Storage in the background. Consent-gated, default ON.
- **Capture:** [PoseJsonV2Writer](shared/src/commonMain/kotlin/com/ttcoachai/shared/io/PoseJsonV2Writer.kt)
  (streaming mirror of the parser; own `round4` since commonMain has no `String.format`) →
  [PoseSessionRecorder](app/src/main/java/com/ttcoachai/pose/PoseSessionRecorder.kt) (frame lines to
  a PLAIN temp file on a single-thread dispatcher, gzip only at `finish()` — the header needs
  `totalFrames`/`videoDurationMs`, which are end-of-session facts). Cap 60k frames.
- **Upload:** `app/.../work/{PoseUploadTask,PoseUploadWorker,PoseUploadQueue}` on WorkManager
  (first `androidx.work` use). Unique work `pose-upload-<sessionId>`, ANY network, backoff.
  `PoseUploadTask` holds all decision logic so it is JVM-testable without Robolectric.
- **Size:** measured 886 B/frame compact, 158 B/frame gzipped → 15 min @15fps ≈ 12 MB raw /
  2.1 MB gzipped. Uncompressed is NOT viable; gzip is load-bearing.
- **The recorder is NOT `LiveDrillSession`.** That keeps a ~4s rolling buffer and
  `TrainingStateManager` keeps 10 reps — neither may be repurposed for full-session capture.
- **Every non-saving exit path must abort the recording** (discard, <5s guard, unauthenticated,
  failed save, `onDestroy`) or the sweep uploads an orphan with no session. Finalization is
  claimed by an `AtomicBoolean` CAS so `onDestroy`'s safety net can't pre-empt a real save —
  it did exactly that once and silently broke the entire happy path.
- **`storage.rules` is documentation-as-code, MANUAL deploy** (`firebase deploy --only storage`),
  same as `firestore.rules`. Cloud Storage ≠ Cloud Firestore: separate file, separate
  `firebase.json` key, separate deploy. **NOT YET DEPLOYED — release gate.**
  Split `read, delete` from `write`: `request.resource` is null on reads/deletes, so folding
  them together denies every download and deletion.
- Spec: [docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md](docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md) ·
  plan: [docs/superpowers/plans/2026-07-23-pose-upload-firebase.md](docs/superpowers/plans/2026-07-23-pose-upload-firebase.md).

## RTM correction taxonomy (shipped 2026-07-23)

The RTM live-drill coaching set is **7 honest per-axis cues**, each backed by exactly one metric:

| Chip | CorrectionType | Metric key | Precision |
|---|---|---|---|
| Elbow bend | `ELBOW_BEND` | `elbow_angle` (shoulder-elbow-wrist) | precise ° |
| Elbow position | `ELBOW_POSITION` | `shoulder_angle` (hip-shoulder-elbow) | precise ° |
| Body rotation | `BODY_ROTATION` | `coil_ratio` | **qualitative** |
| Posture | `POSTURE` | `torso_lean` | precise ° |
| Knee bend | `KNEE_BEND` | `knee_bend` | precise ° |
| Follow-through | `FOLLOW_THROUGH` | `follow_through_angle_2d` | precise ° |
| Stroke speed | `STROKE_SPEED` | `stroke_speed` | **qualitative** |

- **Key model:** `DrillMetrics.PEAK_KEYS` (4, = `CoreMetricSpecs.ALL`) + `DERIVED_KEYS` (3) =
  `ALL_KEYS` (7). Derived metrics come from **ONE** function — `DerivedMetrics.merge` — called from
  `DrillRepProcessor.computeRep` (live), `MovementCalibrator.calibrate` (baseline),
  `MovementAnalyzer.analyze` (batch), always with the SAME `view.xScale` as the peak metrics. Keep it
  that way: three hand-rolled copies is how the paths silently diverge.
- `shoulder_tilt` is **dropped from coaching** (the constant and `AngleCalculations2D.shoulderTilt`
  stay — the viewer still uses them).
- `coil_ratio` = shoulder-width foreshortening drive.start→drive.end ([ShoulderCoil.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/ShoulderCoil.kt),
  ported from `poses_viewer/src/drill2d/shoulderCoil.ts`) — a LOW-CONFIDENCE, yaw-confounded proxy,
  deliberately qualitative-only; still needs protocol-footage tuning.
- **Per-path chips:** `CorrectionTypeAvailability.visibleFor(livePath)` — RTM = the 7 above; LEGACY =
  the original 6 (WRIST/CONTACT_HEIGHT are RTM-hidden — Stage 2, they need hand keypoints / the ball).
  Visibility only; it never mutates stored per-type enabled settings. Wired in `TrainingUIController`
  (`setCorrectionChipsForPath`, 4 call sites in `TrainingActivity.decideCameraModeAndStart`) and
  `FeedbackFragment` (RTM-default).
- Baselines are schemaless JSON — **no Room migration**; pre-change baselines lack the new keys, so
  players must re-calibrate to unlock the new cues.

## MediaPipe legacy calibration/inference path removed (shipped 2026-07-24)

Root-cause bug fixed: `ExerciseEditorActivity`'s "Reference: Baseline" option used to launch the
legacy MediaPipe `CalibrationActivity`, which saved a baseline under a drillType that
`TrainingActivity.loadRtmBaseline()` (the live RTM trainer) never read — a baseline saved via
drill creation was invisible to the app that actually coaches the player. Fix: `ExerciseEditorActivity`
now launches `LiveCalibrationActivity`, the same screen `TrainingActivity`'s own "calibration
required" dialog already used, so both entry points agree on one baseline lineage
(`"forehand_drive_rtm"`).

**Deleted** (the entire live, user-reachable MediaPipe calibration/inference UI path):
`calibration.CalibrationActivity` + its onboarding/capture/review fragments; a second dead screen
subtree (`CameraActivity`, `ActivitySettingsActivity`, `fragment.GalleryFragment` + managers);
`fragment.CameraFragment` + `managers.CameraManager`/`CameraUIController`; the MediaPipe inference
core (`PoseLandmarkerHelper`, `processors.PoseAnalysisProcessor`, `processors.PoseAnalysisLogger`,
`helpers.PoseLandmarkerProcessor`, `helpers.PoseLandmarkerConfig`, `managers.VideoPlayerManager`);
dead view/detector code found during cleanup (`OverlayView`, `views.PoseVisualizer`,
`services.StrokeDetector`, orphaned `fragment_camera.xml`); `TrainingActivity`'s dead
`useVideo`/`USE_VIDEO` legacy video-mode path and its 3 call sites in `ExerciseSelectionActivity`/
`DrillsFragment`; all orphaned tests for the above.

**Known limitation surfaced (not a regression):** `LiveCalibrationActivity`/
`TrainingActivity.loadRtmBaseline()` support exactly ONE global personal baseline, not one per
custom drill (unlike the old MediaPipe `CalibrationActivity`, which was genuinely keyed by drill
type). "Reference: Baseline" now means "use your one calibrated RTM baseline" — per-drill
baselines would need `LiveCalibrationActivity`'s intent contract and `loadRtmBaseline()`'s
lookup key extended together. **Superseded 2026-07-29** — see "Training without calibration —
shipped Andrii baseline" below: personal calibration is no longer required to train at all —
`referenceType="standard"` (the default) trains immediately against `ShippedBaselines.FOREHAND_ANDRII`;
"Reference: Baseline" and this global-baseline limitation now apply only to the
`referenceType="baseline"` path.

**MediaPipe is NOT fully gone from the repo.** The `com.google.mediapipe:tasks-vision` Gradle
dependency is still present in `app/build.gradle`, and `mappers/MediaPipeMapper.kt`,
`services/MotionAnalyzer.kt`, `processors/StrokePhaseDetector.kt` still import `com.google.mediapipe`
types — these feed the already-frozen 3D trajectory/ball-tracking pipeline, genuinely out of scope
for this cleanup. Removing the Gradle dependency was considered and correctly deferred: MediaPipe
now has zero live/reachable code paths outside that frozen pipeline. **Superseded 2026-07-25** — see
"MediaPipe replaces RTMPose as the live pose backend" below: MediaPipe now also has a live,
production 2D pose-inference code path, so "zero live/reachable code paths outside that frozen
pipeline" no longer holds.

## MediaPipe replaces RTMPose as the live pose backend (shipped 2026-07-25)

The live 2D pose backend switched from RTMPose (ONNX Runtime Mobile) to MediaPipe Pose Landmarker
(BlazePose-33 → COCO-17 mapping). `PoseBackendFactory` in
`app/src/main/java/com/ttcoachai/pose/PoseBackendFactory.kt`, plus
`enum PoseBackendVariant { MEDIAPIPE_LITE_GPU (default), MEDIAPIPE_LITE_CPU, MEDIAPIPE_FULL_GPU, MEDIAPIPE_FULL_CPU }`,
build `MediaPipePoseLandmarkerBackend(context, modelAssetName, delegate)` behind the unchanged
`PoseBackend` seam; the chosen variant persists via `SettingsManager` key `pose_backend_variant`. All
3 live sites — `LiveTrainingController`, `LiveCalibrationActivity`, `LiveDrillActivity` —
construct their backend through this one factory, so live drill and calibration can never disagree on
model/delegate (the same class of split that the 2026-07-24 fix above closed for baseline lineage).
Settings → Detection (11b, `DetectionFragment`) exposes a 2×2 "Pose model" picker (Lite/Full × GPU/CPU,
EN+UA), effective next drill start. Backend teardown is serialized onto the analysis executor at all 3
sites — MediaPipe's native `close()` racing an in-flight `detect()` is a SIGSEGV hazard.

**Deleted** (the entire RTMPose/ONNX Runtime stack): `RtmposeBackend`, `RtmposeEstimator`,
`YoloxDetector`, `OrtSessionFactory`, `RtmposeMath`, `BitmapSampler` (+5 test files), the rtmpose/yolox
`.onnx` assets, `fetch_models.sh`, the onnxruntime-android Gradle dep, `'onnx'` from noCompress, the
`.gitignore` onnx block, `PoseBenchmarkActivity`'s RTMPose bench entry, and
`pose_landmarker_heavy.task`'s auto-download in `download_tasks.gradle` (heavy shipped ~29MB dead
weight in every APK); git history is the restore path. **Kept, backend-agnostic** (names unchanged):
`LivePoseFrameProcessor`, `LiveTrainingController`, `LiveCalibrationActivity`,
`LiveDrillActivity`, `PoseSessionRecorder`, `Coco17OverlayView`, `LiveDrillSession`; baseline key
`"forehand_drive_rtm"` unchanged. Desktop Python RTMPose (`scripts/poses/export_poses_rtmpose.py`) and
shared-KMP fixtures are **not affected** — that pipeline is still RTMPose; this change is Android
live-inference only.

**Consequences:** existing personal baselines were calibrated against RTMPose keypoints and don't
transfer to MediaPipe's — players must re-calibrate (no migration, deliberate).
`pose_landmarker_lite.task` + `pose_landmarker_full.task` are git-committed and bundled in the APK.

**Known limitations:** `PoseSessionRecorder.MODEL_NAME` still stamps `"rtmpose-m"` into uploaded
session JSON (that file was on this task's must-NOT-change list — deferred; provenance-only impact,
nothing parses the field). This change is **build-verified only** — no device smoke test this session;
confirm the picker and all 3 live sites on a real phone before treating this as proven in the field.

## Training without calibration — shipped Andrii baseline (shipped 2026-07-29)

The calibration gate is no longer universal: `CustomDrillEntity.referenceType` is finally read (the
first production caller of `isCalibrationRequired`). `"standard"` (default) trains immediately —
`LiveDrillSession` gets `baseline = ShippedBaselines.FOREHAND_ANDRII` (σ-carrier for severity only)
and `rules = applyRangeOverrides(emptyList(), drillBands)`, so only the drill's own configured bands
cue (a blank band stays silent for that metric). `"baseline"` keeps today's personal-calibration gate,
unchanged.

`ShippedBaselines.FOREHAND_ANDRII` (`shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ShippedBaselines.kt`)
is a curated one-time derivation from `Videos/andrii_1` via MediaPipe-lite at full fps (`--interval 17`;
an earlier 100ms-interval pass showed peak-phase jitter and is superseded), with `cameraYawDeg` pinned
`0f` — the yaw gate is consciously relaxed here, a deliberate editorial exception, not a bug. Full
derivation record: [docs/shipped-baseline-derivation.md](docs/shipped-baseline-derivation.md) (+ `.uk.md`
translation); `ShippedBaselineDerivationHarness` (jvmTest) re-derives it for verification.
`defaultBands()` = mean ± 2σ.

The 3 hardcoded unlocked forehand drills are replaced by 2 idempotently-seeded `CustomDrillEntity`
rows (`"custom_seed_forehand_andrii"`, `"custom_seed_forehand_general"`), seeded in
`TTCoachApplication` via `SeededDrillsPolicy` (flag `"seeded_drills_v1"` OR empty table; check-before-write
so user edits are never clobbered) — ordinary editable/deletable/shareable custom drills from then on.
The general row carries a new column `movementProfile="general"` (`AppDatabase` v10, destructive
migration) that widens `hipTravelMaxTorso` through `LiveTrainingController`.

Editor: the 10 advanced rows are now 7, bound to `DrillMetrics.ALL_KEYS` (5 precise-degree metrics,
`stroke_speed` in torso-lengths/s, `coil_ratio` as a ratio; EN+UA labels). `PerPhaseTargetsCodec` keys
on `DrillMetrics` keys with decimal values plus a legacy-key map (`"knees · strike"` → `knee_bend`,
`"torso tilt · strike"` → `torso_lean`) so old community-shared drill blobs still decode.

Launch extras `REFERENCE_TYPE`/`MOVEMENT_PROFILE` now flow from both `DrillsFragment` and
`SessionReviewFragment`'s "Train Again" (fixed in final review — that path was silently dropping
reference mode). An id with no backing row falls back to standard + `defaultBands()` and never gates.

Limitations registered: L-37..L-40 (yaw-carried bands, σ-carrier semantics, seed resurrection, old
blobs covering only 2 of 7 bands).

**Build-and-JVM-test verified only — NO device smoke this session** (no device connected). The spec's
three-scenario device smoke (seeded drill trains uncalibrated + gives voice feedback; an edited band
takes effect; flipping a drill to baseline mode re-gates it) is still pending before treating this as
field-proven.

## Active Technologies

**Current (2D pivot):**
- Python 3.13 (`.venv`): MMPose, RTMPose-m + RTMDet-nano — desktop pose extraction (`scripts/poses/export_poses_rtmpose.py`); this is the desktop/golden pipeline only — Android's live backend switched to MediaPipe 2026-07-25 (see below)
- Kotlin 2.1.0 KMP `shared/` module, **zero external deps** (repo convention) — all drill logic lives here; iOS is a firm future target
- poses_viewer: React + Vite + vitest — visual QA for RTMPose output, COCO-17/Halpe26 skeleton rendering
- MediaPipe tasks-vision 0.10.14 — since 2026-07-25 this is the **live Android pose backend** (`PoseBackendFactory` → `MediaPipePoseLandmarkerBackend`, BlazePose-33 → COCO-17 mapping), replacing RTMPose/ONNX Runtime for on-device inference (see "MediaPipe replaces RTMPose as the live pose backend" below). Same Gradle dependency also still feeds the frozen 3D pipeline (next section) — one dependency, two call sites, only one of them frozen.

**Carried over / frozen in `app/`:**
- CameraX 1.5.3, OpenCV 4.9.0 + TFLite YOLO (frozen ball tracking). MediaPipe tasks-vision 0.10.14 is still a Gradle dependency for the frozen 3D pipeline (`MediaPipeMapper`, `MotionAnalyzer`, `StrokePhaseDetector`) — the live MediaPipe calibration/inference UI it used to power was deleted 2026-07-24 (see "MediaPipe legacy calibration/inference path removed" below); as of 2026-07-25 the same dependency is ALSO the live 2D pose backend (see "Current" above) — no longer frozen-only.
- Room 2.6.1 (sessions, baselines, `drill_configs`), `org.json` for `@TypeConverter`s, Firebase BOM 34.8.0

## Commands

**Tests (primary agent feedback loop)**
- `./gradlew :shared:jvmTest` — shared KMP tests (commonTest classes run on JVM via this task; no device)
- `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2ParserTest"` — single class
- `./gradlew test` — all JVM unit tests (shared + app). NB: the full `:app:testDebugUnitTest` / `./gradlew test` currently carries a **pre-existing failure in frozen legacy code** (`MotionAnalyzerJsonTest`), unrelated to current work — scope with `--tests "<Class>"` filters and don't read a red full app suite as your own regression.
- `cd poses_viewer && npx vitest run` — viewer/FK math tests; `npx tsc -b --noEmit` — typecheck
- `./gradlew connectedAndroidTest` — instrumented (device required; frozen-pipeline coverage)

**Desktop pose pipeline (Phase 1)**
- `.venv/bin/python scripts/poses/export_new.py` — new-clip flow: tidies loose `Videos/*.mp4` into `Videos/<base>/`, exports full-fps `_poses_rtm.json` (see `video-added` skill)
- `.venv/bin/python scripts/poses/export_poses_rtmpose.py <video>` — RTMPose video→JSON schema v2 (COCO-17); `--feet` → Halpe26
- `cd poses_viewer && npm run dev` — http://localhost:5780, overlays pose JSON on video frames; RTM header toggle for schema-v2 exports

**Build (Android, frozen pipeline)**
- `./gradlew :app:assembleDebug` / `:app:assembleRelease`

**Python scripts (`scripts/`) — frozen except poses/**
- `poses/export_poses_rtmpose.py` — CURRENT exporter (schema v2)
- `poses/export_poses.py` — legacy MediaPipe-33 exporter (schema v1, kept for old fixtures)
- `training/`, `contacts/`, `frames/`, `video/` — frozen (ball/audio/frame tooling, Stage 2)

## Project structure

```
shared/                      # KMP module — ALL NEW LOGIC GOES HERE (Phase 2)
  src/commonMain/kotlin/com/ttcoachai/shared/
    models/                  # PoseFrame, Landmark3D (legacy) + Keypoint2D, PoseFrame2D, Topology, Coco17 (Phase 2)
    io/                      # PoseJsonV2Parser (Phase 2)
    analysis/                # BaselineDeriver, BaselineRuleFactory, FrameRuleEvaluator, AngleCalculations (legacy 3D) + AngleCalculations2D, CameraAngleEstimator (Phase 2)
    detection/               # JsonStrokeDetector, StrokePhaseDetector (legacy) + StrokeDetector2D (Phase 2)
    drill/                   # DrillFeedbackEngine, FeedbackMessageCatalog UA+EN, cadence policy (Phase 2)
    tracking/                # FROZEN: TimelineSynchronizer, TrajectoryFilter, TrajectorySegmenter
  src/commonTest/            # pure-Kotlin tests + resources/fixtures/ (JSON pose fixtures, v1 + v2)
  src/jvmTest/               # fixture loaders (TestFixtures v1, TestFixturesV2) + fixture-driven tests

app/                         # Android app — live pose pipeline in pose/ (Phase 3; MediaPipe since 2026-07-25, was RTMPose); separate legacy MediaPipe calib/inference UI deleted 2026-07-24
  src/main/java/com/ttcoachai/
    pose/                    # PoseBackend seam; PoseBackendFactory → MediaPipePoseLandmarkerBackend (since 2026-07-25; Phase 3 orig. RtmposeBackend/ONNX Runtime, now deleted); LiveDrillActivity, PresetVoiceController/DrillTtsController
    managers/                # TrainingStateManager, CalibrationStateManager — frozen
    tracking/                # FROZEN: BallDetectorV1..V6, ROIManager
    mappers/ services/       # MediaPipeMapper, MotionAnalyzer, StrokePhaseDetector — frozen, still import com.google.mediapipe, feed the frozen 3D pipeline only
    debug/                   # BaselineDebugActivity (FLAG_DEBUGGABLE-gated)
    repository/ db/ models/  # Room + Firestore; PersonalBaselineEntity, DrillConfigEntity
  src/test/                  # JVM unit tests; src/androidTest/ — instrumented

poses_viewer/                # React + Vite QA/labeling UI — has its own CLAUDE.md, read it before editing
scripts/                     # Python: poses/ current, rest frozen
docs/                        # canonical context + superpowers/{specs,plans}
Videos/                      # test footage + per-video JSON (*_poses, *_poses_rtm, *_ball_yolo, *_contacts, *_labels)
models/trained/              # frozen YOLO weights
```

## Conventions

**KMP split rule (now stricter):** all drill/analysis/detection logic → `shared/commonMain`, developed TDD against JSON fixtures, proven on JVM before any Android work. `app/` only for Android APIs (camera, inference runtime, TTS, Room, UI). iOS is a firm future target.

**`shared/` has zero external dependencies** — no kotlinx-serialization, no org.json. JSON parsing is hand-rolled (regex-anchored, see `PoseJsonV2Parser`); Room converters in `app/` use `org.json`.

**Naming suffixes:** `*Manager` stateful singleton · `*Processor` frame-by-frame pipeline · `*Analyzer` pure logic · `*Detector` inference/signal detection (versioned when iterating) · `*Repository` dual-source data. 2D-pivot classes take a `2D` suffix when a legacy 3D counterpart exists (`AngleCalculations2D`, `StrokeDetector2D`).

**Freeze discipline:** frozen code (ball tracking, `MediaPipeMapper`/`MotionAnalyzer`/`StrokePhaseDetector`, trajectory) is modified only to keep the build green. New code must not call into it; adapt techniques by copying into new `2D` classes instead (e.g. `AngleCalculations2D` adapts `AngleCalculations` rather than editing it).

**Commit hygiene:** `git add` explicit paths, never `git add -A` (working tree carries unrelated artifacts: `node_modules/.vite/`, `tsconfig.tsbuildinfo`). Commit after each logical change.

**Plan execution:** always execute written implementation plans via subagent-driven development (`superpowers:subagent-driven-development`) — fresh subagent per task, review between tasks. Never execute plan tasks inline in the main session. **Never ask the user which execution approach to use — subagent-driven is the standing default; proceed without asking.** This OVERRIDES the `writing-plans` / `executing-plans` skills' "offer execution choice" step: skip that prompt and go straight to `subagent-driven-development`. **Run implementation/exploration subagents on `sonnet` (or `haiku` for cheap mechanical tasks), not opus** — pass `model: "sonnet"` / `model: "haiku"` to the Agent tool; reserve the main opus session for orchestration and review.

**Never work in a worktree — preserve all changes:** do NOT create git worktrees (no `superpowers:using-git-worktrees`, no `.claude/worktrees/`). Work directly on a branch of the current working branch. Still assume other Claude sessions may touch the same repo concurrently, so never do anything that could discard in-flight edits (no `git checkout -- .`, no `git reset --hard`, no `git stash drop`, no force-anything, no `git add -A`) — `git add` explicit paths only, keep the tree clean, and commit each logical change so nothing is lost.

**Minimize opus output:** opus (main session) orchestrates, reviews, and decides — it generates nothing long itself. All code, docs, and file writes go to sonnet/haiku subagents; opus never writes them inline. Keep main-session prose terse: no preamble, no recap of what was just done, no surveying options you won't take — decide and act. Review subagent work as short verdicts ("line X: do Y"), not rewritten code blocks.

**Room:** entities in `app/.../models/`, DAOs in `app/.../db/`, `AppDatabase` v3 (added `drill_configs`), still `fallbackToDestructiveMigration()` — schema bump wipes local data.

**Tests:** `shared/src/commonTest` for pure-Kotlin unit tests, `shared/src/jvmTest` for fixture-driven tests (ClassLoader resource loading is JVM-only). Fixtures in `shared/src/commonTest/resources/fixtures/` — legacy v1 (MediaPipe-33) and v2 (`*_rtm.json`) coexist; load via `JsonTestUtils`/`TestFixtures` (v1) or `TestFixturesV2` (v2).

## File map (top repeat-reads for current work)

Phase 2 files land per the plan; legacy entries below are what Phase 2 reuses or adapts.

- **[Phase 2 plan](docs/superpowers/plans/2026-06-10-phase2-drill-logic-shared-kmp.md)** — task-by-task TDD plan with full code listings; the source of truth for what exists vs is pending in `shared/`.
- **[BaselineDeriver](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineDeriver.kt)** — pure KMP: strokes + analyses → `PersonalBaseline`. 2σ single-pass outlier exclusion, qualityScore = `1 − mean(CV)`, min-rep check **after** exclusion. Phase 2 extracts a public `deriveFromMetrics(...)`; the existing `derive(...)` must keep delegating to it (003 path stays green).
- **[BaselineRuleFactory](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineRuleFactory.kt)** — single source of rule derivation (`PersonalBaseline → List<BaselineRule>`: 2σ consistency, 25% rhythm). Drill feedback evaluates via [FrameRuleEvaluator](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/FrameRuleEvaluator.kt).
- **[AngleCalculations](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/AngleCalculations.kt)** — legacy MediaPipe-33/`Landmark3D` dot-product angles; frozen (feeds the frozen 3D trajectory pipeline). Phase 2's `AngleCalculations2D` copies the technique for `Keypoint2D` + COCO indices.
- **[StrokePhaseDetector](shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/StrokePhaseDetector.kt)** / **[JsonStrokeDetector](shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/JsonStrokeDetector.kt)** — legacy phase/stroke detection over 33-landmark frames; `StrokeDetector2D` (wrist-speed local maximum) is the 2D adaptation.
- **[TestFixtures](shared/src/jvmTest/kotlin/com/ttcoachai/shared/TestFixtures.kt)** — jvmTest fixture loader pattern (ClassLoader + regex parsing) that `TestFixturesV2`/`PoseJsonV2Parser` productionize.
- **[export_poses_rtmpose.py](scripts/poses/export_poses_rtmpose.py)** — schema-v2 producer. Any schema change must update [docs/pose_json_schema_v2.md](docs/pose_json_schema_v2.md), the KMP parser, and poses_viewer together.
- **[poses_viewer/CLAUDE.md](poses_viewer/CLAUDE.md)** — own detailed guide (file map, gotchas, conventions). Read before touching the viewer.
- **[PersonalBaseline](shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PersonalBaseline.kt)** + **[DrillConfigEntity](app/src/main/java/com/ttcoachai/models/DrillConfigEntity.kt)** — baseline model + coach-tuned drill-shape overrides (Room `drill_configs`, authored in the Phase 7 editor) that Phase 2's evaluator applies on top of derived baselines.

### Hot UI files (ui-wiring reference)
Summarized below to avoid re-reads during UI-wiring work — check here before reopening.

- **[fragment_profile.xml](app/src/main/res/layout/fragment_profile.xml)** — root `NestedScrollView#profile_scroll_view` → `LinearLayout`; key IDs: `iv_profile_image`/`tv_profile_initials` (avatar), `tv_profile_name`, `tv_profile_email`, `tv_profile_hours`, `tv_profile_streak`, `card_subscription_active`/`card_subscription_upgrade`, `tv_renewal_date`, `toggle_group_theme` + `btn_theme_light`/`btn_theme_dark`/`btn_theme_system`, `layout_edit_profile` (gone), `layout_app_settings`, `layout_help_support`, `btn_log_out`. No include/merge.
- **[DrillsFragment.kt](app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt)** — binds `FragmentDrillsBinding` (`rvDrills`, `fabAddDrill`, `sectionRecent`, `tvRecentName`/`ivRecentIcon`/`tvRecentDate`/`tvRecentAccuracy`/`btnRecentContinue`); builds built-in `Exercise` list + custom drills via `CustomDrillRepository`/`AppDatabase`; `ExerciseAdapter` (click/long-click/clone/delete/toggle-locked callbacks); long-press → `dialog_drill_menu` rows gated by `DrillActions`; launches `ExerciseEditorActivity` (new/edit/clone) via `exerciseEditorLauncher`; navigates to `TrainingActivity`. Session-level `cachedCustomExercises` companion cache avoids pop-in on refragment.
- **[styles.xml](app/src/main/res/values/styles.xml)** — base `AppTheme` (Material3 DayNight). `TTC.*` families: `TextAppearance.TTC.{Stat.Hero/Large/Medium/Small, Mono.Meta, Title.Screen/Card, Body/Body.Secondary, Eyebrow(.Gold), Nav.Label}`; `TTC.Card(.Highlighted/.GoldTint)`; `TTC.Segment{Track,.Inactive,.Active,.Button(.Paywall)}`; `TTC.Button.{Primary,Ghost,Danger}`; `TTC.Fab.Extended`; `TTC.SectionHeader(.Gold)`; `TTC.StatNumber(.Gold/.Positive)`; `TTC.TrendChip.{Positive,Negative}`; `TTC.Toggle`; `TTC.Slider`; `TTC.Chip.{Filter,Focus}`; `TtcStepper.{Button,Value}`; `TTC.Dialog.{Title.Panel,Body.Panel,Button.Positive/Neutral}` + `ThemeOverlay.TTC.Dialog`; `TTC.BottomSheet.Modal` + `ThemeOverlay.TTC.BottomSheet`; `TTC.Button.Confirm.{Cancel,Destructive,Neutral}`, `TTC.Button.Discard`.
- **[strings.xml](app/src/main/res/values/strings.xml)** — ~935 strings (EN). Groups by prefix: `exercise_*`/`cat_*`/`difficulty_*` (drill catalog), `training_*`/`btn_*` (Training), `settings_*`/`feedback_*`/`detection_*` (8a/11a/11b), `profile_*`/`subscription_*`/`premium_*` (Profile), `drills_*`/`drill_action_*` (Drills tab + long-press menu), `calibration_*`, `review_*`/`history_*`, `live_*` (1a/1e), `exercise_editor_*` (10c/10d), `dow_*`/`day_*`/`greeting_*` (Dashboard), `format_*` (shared), `placeholder_*` (debug).
- **[TrainingUIController.kt](app/src/main/java/com/ttcoachai/managers/TrainingUIController.kt)** — wraps `ActivityTrainingBinding`; wires bottom sheet (`binding.bottomSheet`, collapsed/not-hideable), `drillMenu.btnPauseResume`/`btnEndSession`/`cardFullReport`, `fab_pause_play`; `rvFeedbackList` + `FeedbackListAdapter`; cues-per-session segment (`btnCues3/5/10`) persisted via `SettingsManager`; correction chips (9: `chipWrist/Rotation/FollowThrough/ContactHeight/ElbowBend/Elbow/KneeBend/Posture/Speed`) → `CorrectionType` via the lazy `correctionChipPairs`, with per-path show/hide through `setCorrectionChipsForPath(livePath)` (visibility only — stored enabled-settings untouched); collapsible `headerFeedbackSettings`/`groupFeedbackSettingsContent`/`ivFeedbackSettingsChevron`; `updateStats()` writes `tv_hits_count`/`tv_accuracy_percent` + `drillMenu.tvTotalHits/tvAccuracy/progressDrill/tvDrillProgress/tvFlagged`; `showFeedbackExplanation` → `FeedbackExplanationSheet`. Collaborators: `TrainingActivity`, `SettingsManager`, `TrainingStateManager`.
- **[ProfileFragment.kt](app/src/main/java/com/ttcoachai/fragment/ProfileFragment.kt)** — binds `FragmentProfileBinding`; IDs: `tvProfileName`, `tvProfileEmail`, `ivProfileImage`/`tvProfileInitials` (Coil + initials fallback), `cardSubscriptionActive`/`cardSubscriptionUpgrade`, `tvRenewalDate`, `toggleGroupTheme`, `layoutAppSettings`, `layoutHelpSupport`, `btnLogOut`, `tvProfileStreak`/`tvProfileHours`, `profileScrollView` (scroll restore). Collaborators: `SettingsManager`, `AuthViewModel`(+`AuthRepository`), `CloudSyncManager`, `ProgressDataLoader`; navigates to `AppSettingsActivity`, `HelpSupportActivity`, `SubscribeActivity`, `LoginActivity` (logout).
- **[values-uk/strings.xml](app/src/main/res/values-uk/strings.xml)** — ~795 strings, Ukrainian mirror of `values/strings.xml` (same keys/prefixes; fewer entries — some newer EN strings not yet translated). Format placeholders (`%1$d`/`%s`) preserved.
- **[item_exercise.xml](app/src/main/res/layout/item_exercise.xml)** — root `com.ttcoachai.ui.SwipeRevealLayout#swipe_root` with `swipe_delete_panel` (start) + `swipe_clone_panel` (end) reveal panels, foreground `MaterialCardView#swipe_foreground` (`TTC.Card`); adapter-bound IDs: `fl_icon_container`/`iv_exercise_icon`, `tv_exercise_name`, `tv_exercise_description`, `tv_duration`, `tv_category`, `iv_chevron`.

## Gotchas — current (2D pivot)

- **All 2D geometry takes ONE xScale factor** (`ViewGeometry.xScale` = aspectRatio / cos(cameraYaw)) — never compute angles/speeds on raw normalized coords: schema v2 normalizes x and y by different axes (`x / videoWidth`, `y / videoHeight`). Multiply x-deltas by `xScale` before any trig. Synthetic tests use `xScale = 1f`. [ViewGeometry.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/models/ViewGeometry.kt)
- **Two topologies, one parser:** `"topology": "coco17"` (17 kp) vs `"halpe26"` (26 kp, `--feet` export); indices 0–16 identical. Legacy v1 (MediaPipe-33, `x,y,z,visibility`, no `schemaVersion`) is a *different format* — `PoseJsonV2Parser` rejects it explicitly; old fixtures still load via `JsonTestUtils`/`TestFixtures`.
- **Score gating:** angle functions return `null` when any required keypoint `score < 0.3` — no feedback on low-confidence frames. Don't "fix" nulls by lowering the threshold.
- **Camera yaw is per-rep, |yaw| only, from the PRE-stroke window** — player moves their feet between reps; estimating during the swing reads the player's own rotation as camera placement. `|yaw| > ~30°` → rep excluded from calibration / no feedback in analysis (`placementOk = false`), don't correct. Estimator currently saturates (90°) on non-protocol footage — see [docs/DESIGN_LIMITATIONS.md](docs/DESIGN_LIMITATIONS.md) L-25. [CameraAngleEstimator.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/CameraAngleEstimator.kt)
- **Wrist-speed peaks ≠ reps** — ~half are recovery swings on real footage. Pipeline order is detect → `ForwardStrokeFilter` (speed-dominance direction vote) → `RepFilter` (median banding); reordering or skipping silently corrupts baselines. [ForwardStrokeFilter.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilter.kt), [DrillCalibrator.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillCalibrator.kt)
- **SanityBounds drops, never coaches** — out-of-band values are tracking glitches removed from the rep; feedback always compares against the personal baseline. [SanityBounds.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/SanityBounds.kt)
- **Head-facing (facingSign) is noisy on real footage** — fine for synthetic tests; torso-lean sign and the `ForwardStrokeFilter` fallback both depend on it (L-04 still OPEN). [AngleCalculations2D.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/AngleCalculations2D.kt)
- **Trust rule:** precise degree numbers only for the 5 in-plane angle metrics (`elbow_angle`, `shoulder_angle`, `knee_bend`, `torso_lean`, `follow_through_angle_2d`); `stroke_speed` and `coil_ratio` are qualitative, rotational cues qualitative-only or silent. Encoded as an explicit allowlist in `MetricPrecisionPolicy` (NOT derived from `ALL_KEYS` — that's what wrongly made the derived proxies "precise").
- **Adding a `CorrectionType` value breaks 6 places — one of them silently.** Five exhaustive `when`s fail to compile (`SnapshotGeometry.highlightFor`, `StrokeSnapshotSelector.snapshotFrameFor`, `SessionAnalyticsBuilder.displayNameKey` + `displayName`, `FeedbackListAdapter.labelRes`), but `FeedbackExplanationCatalog.TABLE` is a `Map` read via `getValue` — a missing entry compiles fine and **crashes at runtime** the first time a user taps "explain". Add EN+UA entries there too. (`FeedbackExplanationSheet.captionRes` and `PoseSnapshotView` have `else` arms and are safe.)
- **`DrillMetrics.ALL_KEYS` membership is load-bearing for tests.** The message catalogs (`CoreMessageTemplates`/`FeedbackMessageCatalog`) have a fallback so they stay green when a key is added, but `VoicePresetCatalog` has **no fallback** and its test asserts a non-null phrase per key — a new metric key needs phrases added to `poses_viewer/src/drill2d/voiceStyle.ts` (the hashed source of truth for clip lookup) and ported **verbatim** into `VoicePresetCatalog`.
- **The in-app language toggle doesn't re-render live** — Settings → «Мова інтерфейсу» persists but only takes effect after `adb shell am force-stop com.ttcoachai` + relaunch. Budget for that when screenshotting the other locale.
- **`Videos/` footage was not shot to the camera-placement protocol** — fine for pipeline bring-up and mechanics tests, not for tuning reference ranges. End-to-end tests prove mechanics, not tuned thresholds.
- **commonMain has no `java.lang.Math` / no ClassLoader** — use `kotlin.math`; resource-loading fixture tests go in `jvmTest`, not `commonTest`.

## Gotchas — frozen legacy pipeline (relevant when build breaks or for Stage 2)

- **TrainingStateManager is a volatile singleton** — not safe for concurrent mutation; synchronized/coroutine-scoped updates only. [TrainingStateManager.kt:35-39](app/src/main/java/com/ttcoachai/managers/TrainingStateManager.kt#L35-L39)
- **`BaselineConverters` uses `org.json`, not kotlinx-serialization** — add explicit converters there for new Room columns. [BaselineConverters.kt](app/src/main/java/com/ttcoachai/db/BaselineConverters.kt)
- **Room uses `fallbackToDestructiveMigration()`** — schema changes wipe local DB (AppDatabase v3). Switch to explicit migrations before release. [AppDatabase.kt:26](app/src/main/java/com/ttcoachai/db/AppDatabase.kt#L26)
- **GoogleSignIn relies on `default_web_client_id`** — auto-generated by google-services plugin; init fails silently if missing. [AuthRepository.kt:32-36](app/src/main/java/com/ttcoachai/repository/AuthRepository.kt#L32-L36)
- **Ball tracking (all frozen):** BallDetectorV6 needs top-half ROI crop (full-frame drops to 26.8%); conf threshold 0.25, not 0.5; dual coord transform ROI→full-frame; V5 deprecated (6.6% vs 86.3%); TFLite GPU delegate silently falls back to CPU. [BallDetectorV6.kt](app/src/main/java/com/ttcoachai/tracking/BallDetectorV6.kt)
