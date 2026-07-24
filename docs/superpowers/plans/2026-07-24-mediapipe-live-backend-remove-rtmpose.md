# MediaPipe live pose backend + full RTMPose removal — 2026-07-24

Make MediaPipe the pose backend for the 2D live drill pipeline, add a Settings picker
for the MediaPipe variant, and remove RTMPose entirely (no fallback, no dead code —
git history is the restore path). The 2D drill/feedback/recording logic does NOT
change: it consumes COCO-17 `List<Keypoint2D>` through the `PoseBackend` seam, and
`MediaPipePoseLandmarkerBackend` already emits exactly that.

Verified context (do not re-investigate):

- `PoseBackend` ([app/src/main/java/com/ttcoachai/pose/PoseBackend.kt](../../../app/src/main/java/com/ttcoachai/pose/PoseBackend.kt))
  is a `fun interface`: `estimatePose(bitmap, frameWidth, frameHeight): List<Keypoint2D>`
  returning 17 COCO-17-ordered, per-axis-normalized keypoints. This is the seam.
- `MediaPipePoseLandmarkerBackend.kt` already implements `PoseBackend` correctly
  (BlazePose-33 → COCO-17 mapping, `RunningMode.IMAGE`, synchronous `detect()`).
  Constructor knobs: `modelAssetName` (`"pose_landmarker_lite.task"` |
  `"pose_landmarker_full.task"`) and `delegate` (`Delegate.CPU` | `Delegate.GPU`).
- `com.google.mediapipe:tasks-vision` is already in `app/build.gradle` (line ~175).
  `.task` assets exist under `app/src/main/assets/` and are NOT gitignored (only
  `*.onnx` is, .gitignore line 42), currently untracked.
- Downstream is backend-agnostic and must NOT change: `RtmposeFrameProcessor`,
  `PoseSessionRecorder`, `Coco17OverlayView`, `LiveDrillSession`. Their "Rtmpose"
  class-name prefixes stay — renaming is out of scope, pure churn.
- The three live construction sites (currently `new RtmposeBackend(...)`):
  1. `RtmposeTrainingController.kt:161` — `RtmposeBackend(activity)` (LIVE training)
  2. `RtmposeCalibrationActivity.kt:115` — `RtmposeBackend(this)` (calibration)
  3. `RtmposeDrillActivity.kt:125` — `RtmposeBackend(this)` (standalone drill)
- ONNX Runtime imports (`ai.onnxruntime`) exist ONLY in `YoloxDetector.kt`,
  `RtmposeEstimator.kt`, `OrtSessionFactory.kt` (all deleted in Task 3). MoveNet
  bench backend is TFLite — unaffected by removing onnxruntime.
- `fetch_models.sh` (app/src/main/assets/) has zero callers outside build
  intermediates; it exists solely to fetch the RTMPose/YOLOX `.onnx` files.

## Global Constraints

1. **Seam contract unchanged:** `PoseBackend.estimatePose(bitmap, frameWidth,
   frameHeight): List<Keypoint2D>` — 17 keypoints, COCO-17 order, per-axis
   normalized. No changes to `shared/` or any drill/feedback/recording logic.
2. **Untouchable files:** `shared/**`, `RtmposeFrameProcessor.kt`,
   `PoseSessionRecorder.kt`, `Coco17OverlayView.kt`, `LiveDrillSession.kt`, the
   frozen 3D pipeline (`MediaPipeMapper.kt`, `MotionAnalyzer.kt`,
   `StrokePhaseDetector.kt`), `tracking/**` (frozen ball code). Kept "Rtmpose*"
   controller/activity names are NOT renamed.
3. **Variant set is exactly:** `MEDIAPIPE_LITE_GPU` (default), `MEDIAPIPE_LITE_CPU`,
   `MEDIAPIPE_FULL_GPU`, `MEDIAPIPE_FULL_CPU`. No RTMPose option, no HEAVY option.
4. **Calibration and live training resolve the backend through the SAME factory**
   so baseline keypoint characteristics match the live backend.
5. **DIRTY-TREE PROTOCOL (critical):** the working tree carries uncommitted
   concurrent-session edits in: `SettingsFragment.kt`, `BaseActivity.kt`,
   `LocaleHelper.kt`, `DrillsFragment.kt`, `fragment_drills.xml`,
   `MediaPipeMapper.kt`, `StrokePhaseDetector.kt`, `MotionAnalyzer.kt`,
   `MotionAnalyzerTest.kt`, `MotionAnalyzerJsonTest.kt`, `scripts/run_on_phone.sh`,
   `.claude/**`, `app/src/main/res/xml/locales_config.xml`, and
   `docs/superpowers/plans/2026-07-24-remove-mediapipe-legacy-pipeline.md`.
   NEVER revert/checkout/stash/reset any of it. NEVER `git add -A` or `git add .`
   — explicit paths only. Never `git add` any file from that list unless your task
   gives an explicit staging recipe for it. Leave `pose_landmarker_heavy.task`
   untracked — do not add it, do not delete it.
6. **Build gate after every task:** `./gradlew :app:assembleDebug` must pass.
   `./gradlew test` carries a pre-existing unrelated failure
   (`MotionAnalyzerJsonTest`) — never treat it as your regression; scope any test
   runs with `--tests` filters.
7. **No device available** (phone disconnected) — build-only verification, no adb.
8. **Commits:** one logical change per commit, explicit paths, message ends with:
   `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`

### Partial-staging recipe (only when a task must edit a dirty file)

To commit ONLY your hunks of FILE (worktree keeps both yours and the concurrent
session's edits; the commit contains only yours):

```bash
cp FILE /tmp/concurrent_version          # BEFORE your edits (their hunks present)
# ... make your edits to FILE ...
diff -u /tmp/concurrent_version FILE > /tmp/my.patch   # exactly your hunks
git show HEAD:FILE > /tmp/staged_candidate
(cd /tmp && patch staged_candidate < my.patch)         # HEAD + only your hunks
# If patch reports rejects (your hunks adjoin theirs, e.g. import block):
#   hand-edit /tmp/staged_candidate to add just your lines, nothing of theirs.
sha=$(git hash-object -w /tmp/staged_candidate)
git update-index --cacheinfo 100644,$sha,FILE
git diff --cached -- FILE                # MUST show only your hunks — verify!
```

If your hunks cannot be separated from theirs, STOP and report BLOCKED — do not
commit their work.

## Task 1 — PoseBackendFactory + persisted variant + wire the 3 live sites

Files to read first: `app/src/main/java/com/ttcoachai/pose/PoseBackend.kt`,
`app/src/main/java/com/ttcoachai/pose/MediaPipePoseLandmarkerBackend.kt`,
`app/src/main/java/com/ttcoachai/managers/SettingsManager.kt`, and the three
construction-site files listed in the plan header.

Before writing wiring, consult the proven MediaPipe integration in git history:
`git log --all --oneline | grep -iE "mediapipe|poselandmarker|benchmark"` and
`git show` the relevant commits (gradle setup, GPU delegate config, `.task` asset
loading, image conversion, threading). The CURRENT `MediaPipePoseLandmarkerBackend`
(this branch) IS that proven integration — reuse its patterns. Do NOT resurrect the
deleted 3D / 33-landmark path or the old MediaPipe calibration UI (they fed the
frozen 3D pipeline; out of scope).

1. **`app/src/main/java/com/ttcoachai/pose/PoseBackendFactory.kt`** (new):
   - `enum class PoseBackendVariant { MEDIAPIPE_LITE_GPU, MEDIAPIPE_LITE_CPU,
     MEDIAPIPE_FULL_GPU, MEDIAPIPE_FULL_CPU }` mapping to
     (`modelAssetName`, `delegate`): LITE → `"pose_landmarker_lite.task"`,
     FULL → `"pose_landmarker_full.task"`; GPU → `Delegate.GPU`, CPU → `Delegate.CPU`.
   - Factory (object or top-level fun): reads the persisted variant from
     `SettingsManager` at construction time and returns
     `MediaPipePoseLandmarkerBackend(context, modelAssetName, delegate)`.
     Return the concrete type if call sites need lifecycle API (e.g. `close()`);
     otherwise `PoseBackend`.
2. **SettingsManager:** add a persisted variant accessor following its existing
   getter/setter patterns exactly (same storage mechanism, no new one). Store the
   enum name as string; unknown/missing stored value falls back to
   `MEDIAPIPE_LITE_GPU`.
3. **Wire the 3 sites** (`RtmposeTrainingController.kt:161`,
   `RtmposeCalibrationActivity.kt:115`, `RtmposeDrillActivity.kt:125`): each
   resolves the backend via the factory instead of `RtmposeBackend(...)`. Adapt the
   local variable/field types as needed; if a site uses RTMPose-specific API
   (e.g. `close()`, warmup), map it to the MediaPipe backend's equivalent — you may
   add a small `close()` to `MediaPipePoseLandmarkerBackend` if it lacks one (it
   wraps a `PoseLandmarker`, which must be closed).
4. **`MediaPipePoseLandmarkerBackend.kt`:** remove the "THROWAWAY PROTOTYPE" header
   comment — it is now production code.
5. **Track the model assets:** `git add app/src/main/assets/pose_landmarker_lite.task
   app/src/main/assets/pose_landmarker_full.task` (currently untracked; NOT
   gitignored, so plain add works). Do NOT add `pose_landmarker_heavy.task`.
6. Do NOT delete anything RTMPose in this task — `RtmposeBackend` must still
   compile (deletion is Task 3).
7. Build gate, then commit (explicit paths: the new factory, SettingsManager, the
   3 site files, MediaPipePoseLandmarkerBackend, the 2 `.task` assets). Commit
   message must note: existing personal baselines were calibrated on RTMPose;
   BlazePose localizes keypoints differently, so users must re-calibrate — no
   baseline migration attempted (deliberate).

## Task 2 — Visible Settings picker for the MediaPipe variant (EN + UA)

1. **Survey where app/training settings are surfaced** — check
   `app/src/main/java/com/ttcoachai/fragment/SettingsFragment.kt` (the 8a Settings
   screen; DIRTY — see protocol), `AppSettingsActivity` (reached from Profile), and
   any Detection settings surface (`detection_*` strings, screen 11b). Place the
   picker where a pose-model/inference setting naturally belongs alongside existing
   training/detection settings. Report the placement choice + rationale.
2. **Picker row** bound to the Task 1 SettingsManager accessor: row label (e.g.
   "Pose model") + 4 options labeled "Lite (GPU)", "Lite (CPU)", "Full (GPU)",
   "Full (CPU)". Use a dropdown/segmented/radio control matching the existing
   setting-row style and `TTC.*` component styles of the chosen screen. Selecting
   an option persists immediately; the current value is reflected when the screen
   opens. Changing it takes effect on the next drill start (the factory reads it at
   construction) — no live rebind needed.
3. **Strings:** EN in `app/src/main/res/values/strings.xml`, Ukrainian in
   `app/src/main/res/values-uk/strings.xml`, following the neighboring key-prefix
   conventions of the chosen screen. Variant option labels may stay Latin
   ("Lite (GPU)") in both locales; the row label/description is translated.
4. If the chosen home file is dirty (`SettingsFragment.kt` / its layout), commit
   your hunks ONLY, via the partial-staging recipe in Global Constraints. If
   inseparable, STOP and report BLOCKED.
5. Build gate, then commit (explicit paths).

## Task 3 — Delete RTMPose entirely

1. **Delete source files:** `app/src/main/java/com/ttcoachai/pose/RtmposeBackend.kt`,
   `RtmposeEstimator.kt`, `YoloxDetector.kt`, `OrtSessionFactory.kt` (first re-verify
   via grep that nothing outside these four references `OrtSessionFactory` — if
   something does, stop and report instead of forcing it).
2. **Delete model assets:** `app/src/main/assets/rtmpose-s_simcc-body7_pt-body7_420e-256x192-acd4a1ef_20230504.onnx`
   and `app/src/main/assets/yolox_tiny_8xb8-300e_humanart-6f3252f9.onnx` (both
   gitignored+untracked → plain `rm`).
3. **`PoseBenchmarkActivity.kt`:** remove the RTMPose entry from the `BackendKind`
   enum, its `switchBackend()` branch (line ~176), and any toggle-UI references to
   it; make sure no default/initial selection references it. MoveNet and MediaPipe
   bench entries stay intact.
4. **`app/build.gradle`:** remove the
   `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` dependency (line ~171)
   and drop `'onnx'` from `noCompress` (line ~74 — keep `'task'`, `'tflite'`).
5. **RTMPose fetch infrastructure** (exists solely for the deleted `.onnx` files):
   delete `app/src/main/assets/fetch_models.sh`; remove .gitignore lines 41–42
   (the `app/src/main/assets/*.onnx` block — LEAVE line 39 `iosApp/...` alone);
   rewrite `app/src/main/assets/MODELS.md` to the post-removal reality: MediaPipe
   `.task` models (lite+full) are git-committed and bundled, `heavy` optional and
   untracked, `movenet_thunder.tflite` is git-tracked and the benchmark manifest
   entry is live (the current text claims otherwise — stale), all RTMPose/BALANCED
   content and URLs removed. Keep it short.
6. **Tests:** grep `app/src/test` and `app/src/androidTest` for
   `RtmposeBackend|RtmposeEstimator|YoloxDetector|OrtSession|onnxruntime` — delete
   test files that reference the deleted classes (report which).
7. **Residual sweep (paste outputs into your report):**
   - `grep -rn "RtmposeEstimator\|YoloxDetector\|OrtSession\|onnxruntime\|ai\.onnxruntime" app/ --include="*.kt" --include="*.gradle"` → must be empty
   - `grep -rn "RtmposeBackend" app/ --exclude-dir=build` → must be empty
   - `grep -rni "rtmpose" app/src/ --exclude-dir=build -l` → only the kept
     backend-agnostic files (`RtmposeFrameProcessor`, `RtmposeTrainingController`,
     `RtmposeCalibrationActivity`, `RtmposeDrillActivity`, their layouts/usages,
     `forehand_drive_rtm` baseline keys, strings) — nothing that references the
     deleted classes.
   If anything still imports onnxruntime after the deletions, STOP and report
   instead of forcing it.
8. Build gate (`./gradlew :app:assembleDebug`), then commit. Note in the commit
   message that git history is the restore path for RTMPose.
