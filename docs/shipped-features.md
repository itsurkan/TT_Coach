# Shipped feature history

These are historical shipped-feature records moved out of CLAUDE.md on 2026-08-03 to keep the
always-loaded file lean; live rules and gotchas derived from these features live in CLAUDE.md's
§ Gotchas — current (2D pivot).

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
- Plan: [superpowers/plans/2026-07-22-community-drills.md](superpowers/plans/2026-07-22-community-drills.md).

## Pose data upload (shipped 2026-07-23)

Every RTM training session's full per-frame pose stream is captured to a local gzipped
schema-v2 JSON and uploaded to Firebase Storage in the background. Consent-gated, default ON.
- **Capture:** [PoseJsonV2Writer](../shared/src/commonMain/kotlin/com/ttcoachai/shared/io/PoseJsonV2Writer.kt)
  (streaming mirror of the parser; own `round4` since commonMain has no `String.format`) →
  [PoseSessionRecorder](../app/src/main/java/com/ttcoachai/pose/PoseSessionRecorder.kt) (frame lines to
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
- Spec: [superpowers/specs/2026-07-23-pose-upload-firebase-design.md](superpowers/specs/2026-07-23-pose-upload-firebase-design.md) ·
  plan: [superpowers/plans/2026-07-23-pose-upload-firebase.md](superpowers/plans/2026-07-23-pose-upload-firebase.md).

## MediaPipe legacy calibration/inference path removed (shipped 2026-07-24)

Root-cause bug fixed: `ExerciseEditorActivity`'s "Reference: Baseline" option used to launch the
legacy MediaPipe `CalibrationActivity`, which saved a baseline under a drillType that
`TrainingActivity.loadRtmBaseline()` (the live RTM trainer) never read — a baseline saved via
drill creation was invisible to the app that actually coaches the player. Fix: `ExerciseEditorActivity`
now launches `LiveCalibrationActivity`, the same screen `TrainingActivity`'s own "calibration
required" dialog already used, so both entry points agree on one baseline lineage
(`"forehand_drive_rtm"`).

**Known limitation surfaced (not a regression):** `LiveCalibrationActivity`/
`TrainingActivity.loadRtmBaseline()` support exactly ONE global personal baseline, not one per
custom drill (unlike the old MediaPipe `CalibrationActivity`, which was genuinely keyed by drill
type). "Reference: Baseline" now means "use your one calibrated RTM baseline" — per-drill
baselines would need `LiveCalibrationActivity`'s intent contract and `loadRtmBaseline()`'s
lookup key extended together. **Superseded 2026-07-29** — see "Training without calibration —
shipped Andrii baseline" in CLAUDE.md: personal calibration is no longer required to train at all —
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

**Kept, backend-agnostic** (names unchanged):
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
