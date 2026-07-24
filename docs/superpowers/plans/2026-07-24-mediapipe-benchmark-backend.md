# MediaPipe PoseLandmarker benchmark backend — implementation plan

## For agentic workers

**REQUIRED SUB-SKILL:** Execute this plan task-by-task via `superpowers:subagent-driven-development`
(fresh subagent per task, review between tasks). Do not execute inline in the orchestrating session.

## Goal

Add MediaPipe PoseLandmarker (Lite + Full model variants, CPU + GPU delegate = 4 new entries) into
`PoseBenchmarkActivity`'s existing RTMPose-lite / MoveNet FPS A/B toggle, purely as benchmark
options, and re-enable the activity's manifest entry so the screen is reachable via adb again. No
production/user-reachable code path is touched or restored — this is fresh benchmark-only code,
not a resurrection of the deleted MediaPipe calibration/inference UI.

Source of truth: `docs/superpowers/specs/2026-07-24-mediapipe-benchmark-backend-design.md` (read
first — this plan implements it verbatim, no redesign).

## Architecture

```
PoseBenchmarkActivity (dev-only, FLAG_DEBUGGABLE-gated, exported="true" for adb launch)
  toggleButton click -> switchBackend(nextKind)
    cycle (declaration order, wraps): RTMPOSE_LITE -> MOVENET -> MEDIAPIPE_LITE_CPU ->
                                       MEDIAPIPE_LITE_GPU -> MEDIAPIPE_FULL_CPU ->
                                       MEDIAPIPE_FULL_GPU -> (back to RTMPOSE_LITE)

  switchBackend(kind):
    closes old backend/processor, constructs new PoseBackend for `kind` (try/catch -> Toast+log
    on construction failure, `backend` stays null, FPS readout shows "--" — UNCHANGED behavior)
      RTMPOSE_LITE         -> RtmposeBackend(...)                    [existing, unmodified]
      MOVENET               -> MoveNetBackend(this)                  [existing, unmodified —
                                pre-existing missing-asset failure, out of scope, see Global
                                Constraints]
      MEDIAPIPE_*_CPU/GPU   -> MediaPipePoseLandmarkerBackend(context, modelAssetName, delegate) [NEW]
    wraps the backend in RtmposeFrameProcessor(newBackend, mirror=false, onPose)  [UNMODIFIED]

  camera frame -> RtmposeFrameProcessor.analyze(imageProxy)  [UNMODIFIED]
    -> backend.estimatePose(bitmap, frameWidth, frameHeight)
    -> onPoseResult(keypoints, timestampMs) -> FpsTracker.tick() + Coco17OverlayView.setKeypoints()  [UNMODIFIED]

MediaPipePoseLandmarkerBackend (NEW — app/src/main/java/com/ttcoachai/pose/)
  init:
    PoseLandmarker.createFromOptions(context, PoseLandmarkerOptions {
      baseOptions = BaseOptions { delegate; modelAssetPath = modelAssetName }
      runningMode = RunningMode.IMAGE
    })
    — throws on failure (bad model asset, GPU delegate unsupported); NOT caught here, propagates
      to switchBackend()'s existing try/catch by design (matches the design doc's error-handling
      section — no local fallback logic needed for a throwaway tool).

  estimatePose(bitmap, frameWidth, frameHeight):
    BitmapImageBuilder(bitmap).build() -> landmarker.detect(mpImage)   [synchronous, IMAGE mode]
    -> result.landmarks().firstOrNull()   (33 BlazePose NormalizedLandmarks, or null -> emptyList())
    -> COCO17_TO_BLAZEPOSE33 index table -> 17 Keypoint2D(x, y, score = visibility)
       NOTE: MediaPipe's NormalizedLandmark x/y are already normalized to the INPUT MPImage's own
       dimensions (i.e. to `bitmap`'s width/height = frameWidth/frameHeight) — unlike
       MoveNetEstimator, there is NO letterbox/padding correction to undo here; MediaPipe does its
       own internal resizing for inference but reports landmarks back in the original input frame.

  close(): landmarker.close()
```

## Tech Stack

- Kotlin 2.2.21, Android platform code only (`app/` — no `shared/` KMP involvement; this is a
  camera/inference-runtime concern, squarely Android per the repo's KMP split rule).
- **NEW dependency:** `com.google.mediapipe:tasks-vision:0.10.14` — this exact version is already
  present in the local Gradle cache
  (`~/.gradle/caches/modules-2/files-2.1/com.google.mediapipe/tasks-vision/0.10.14/`) from this
  repo's now-deleted production MediaPipe path, so re-adding it is a known-good, previously-proven
  version for this exact project (CameraX 1.5.3 + ONNX Runtime Mobile 1.20.0 + TFLite 2.16.1
  already coexisted with it before the 2026-07-24 cleanup) — do not "upgrade to latest" as part of
  this plan.
- `app/build.gradle` already has `androidResources { noCompress 'task', 'tflite', 'onnx' }` and
  `splits { abi { include 'arm64-v8a' } }` — both already correct for MediaPipe's `.task` assets
  and native libs; no changes needed there.
- `app/src/main/assets/pose_landmarker_lite.task` and `pose_landmarker_full.task` are already
  present on disk (currently **untracked** in git — see Global Constraints). `pose_landmarker_heavy.task`
  also present but stays unreferenced (out of scope, per design doc).

## Global Constraints

- **Freeze discipline:** do not modify `RtmposeFrameProcessor.kt`, `Coco17OverlayView.kt`,
  `FpsTracker.kt`, `RtmposeBackend.kt`, `MoveNetBackend.kt`, `MoveNetEstimator.kt`, or any other
  frozen pipeline code. This plan's changes are confined to `app/build.gradle`, one new file
  (`MediaPipePoseLandmarkerBackend.kt`), `PoseBenchmarkActivity.kt`, and `AndroidManifest.xml`.
- **`git add` explicit paths only — NEVER `git add -A`.** In particular, do **not** `git add` the
  `.task` model files in `app/src/main/assets/` (`pose_landmarker_lite.task` 5.7MB,
  `pose_landmarker_full.task` 9.4MB, `pose_landmarker_heavy.task` 30MB). They were deliberately
  removed from git tracking to slim the repo and are `??` (untracked) in `git status` — they only
  need to be present on disk for a local debug build to run. Committing them re-bloats the repo.
- **Commit after each task** (one commit per Task below, at that task's final step) — not batched,
  not per-step.
- **Build command:** `./gradlew :app:assembleDebug`. This is a dev-only, `FLAG_DEBUGGABLE`-gated
  throwaway tool with **no existing unit tests and none should be added** — verification is
  compile-clean + a manual on-device check (Task 4), consistent with existing precedent for this
  file. Do not invent JVM/instrumented tests for it.
- **Known pre-existing, out-of-scope issue:** `movenet_thunder.tflite` is missing from
  `app/src/main/assets/` (deleted on disk, not restored — unrelated prior cleanup). The existing
  `BackendKind.MOVENET` entry will therefore fail to construct and show the existing
  "Backend init failed: ..." Toast when toggled to. This is expected during Task 4's manual
  verification — do **not** fix it as part of this plan (out of scope per the design doc).
- **Concurrent-session risk on `app/build.gradle`:** as of this plan being written, the working
  tree carries **uncommitted** changes from a separate in-flight cleanup
  (`docs/superpowers/plans/2026-07-24-remove-mediapipe-legacy-pipeline.md`) that removed the
  `com.google.mediapipe:tasks-vision` dependency from `app/build.gradle` and commented out
  `MediaPipeMapper.kt` / `MotionAnalyzer.kt` / `StrokePhaseDetector.kt` (which fed the frozen 3D
  pipeline). That work and this plan are compatible in intent — production ends up with zero
  MediaPipe; this plan re-adds MediaPipe as a **benchmark-only** dependency on top — but **before
  Task 1's edit, re-read `app/build.gradle` fresh and confirm with a grep for `mediapipe`** rather
  than trusting this plan's quoted snippet as ground truth; the file may have moved on (committed,
  or further changed) by the time this plan executes.
- No `strings.xml`/`values-uk/strings.xml` changes needed — all of `PoseBenchmarkActivity`'s UI
  text is hardcoded Kotlin strings (existing precedent in the file; it is not a shippable/localized
  screen).

## File Structure

```
app/build.gradle                                                    [MODIFY — Task 1]

app/src/main/java/com/ttcoachai/pose/
  MediaPipePoseLandmarkerBackend.kt                                  [CREATE — Task 2]
  PoseBenchmarkActivity.kt                                           [MODIFY — Task 3]

app/src/main/AndroidManifest.xml                                    [MODIFY — Task 4]
```

---

### Task 1: Add the `com.google.mediapipe:tasks-vision` dependency

**Files:**
- Modify: `app/build.gradle`

**Interfaces:**
- Produces: Gradle dependency `com.google.mediapipe:tasks-vision:0.10.14` available to `app`
  module compilation (brings `com.google.mediapipe.tasks.core.{BaseOptions,Delegate}`,
  `com.google.mediapipe.tasks.vision.core.RunningMode`,
  `com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker`,
  `com.google.mediapipe.framework.image.{MPImage,BitmapImageBuilder}`,
  `com.google.mediapipe.tasks.components.containers.NormalizedLandmark`).
- Consumes: none.

#### Steps

- [ ] **Step 1: Re-verify the current dependencies block before editing**

  Run:
  ```bash
  grep -n "mediapipe" app/build.gradle
  ```
  Confirm it prints nothing (matches the "no MediaPipe dependency currently" baseline this plan
  assumes — see Global Constraints' concurrent-session note). If it prints an existing MediaPipe
  line, STOP and re-read the file in full before proceeding; the edit below assumes it is absent.

- [ ] **Step 2: Add the dependency**

  In `app/build.gradle`, find the end of the `dependencies { ... }` block:
  ```groovy
      // ONNX Runtime Mobile (Phase 3 RTMPose backend; arm64-v8a only)
      implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.20.0'
  }
  ```
  Replace with:
  ```groovy
      // ONNX Runtime Mobile (Phase 3 RTMPose backend; arm64-v8a only)
      implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.20.0'

      // MediaPipe Library (benchmark-only: PoseBenchmarkActivity / MediaPipePoseLandmarkerBackend,
      // dev-only FPS A/B tool — not used by any production/shipped code path)
      implementation 'com.google.mediapipe:tasks-vision:0.10.14'
  }
  ```

- [ ] **Step 3: Confirm the dependency resolves and the module still compiles**

  ```bash
  ./gradlew :app:assembleDebug
  ```
  Expect a normal successful build (nothing references the new dependency yet, so this only
  validates dependency resolution + that nothing else broke).

- [ ] **Step 4: Commit**

  ```bash
  git add app/build.gradle
  git commit -m "$(cat <<'EOF'
  chore(pose): add MediaPipe tasks-vision dependency for benchmark backend

  Benchmark-only re-add of com.google.mediapipe:tasks-vision (0.10.14, same version
  previously proven in this project) so PoseBenchmarkActivity can A/B MediaPipe
  PoseLandmarker against RTMPose-lite/MoveNet. No production code path uses it.
  EOF
  )"
  ```

---

### Task 2: Create `MediaPipePoseLandmarkerBackend`

**Files:**
- Create: `app/src/main/java/com/ttcoachai/pose/MediaPipePoseLandmarkerBackend.kt`

**Interfaces:**
- Produces: `class MediaPipePoseLandmarkerBackend(context: Context, modelAssetName: String, delegate: Delegate) : PoseBackend, AutoCloseable`
  - `override fun estimatePose(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): List<Keypoint2D>`
  - `override fun close()`
- Consumes:
  - `fun interface PoseBackend { fun estimatePose(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): List<Keypoint2D> }`
    (`app/src/main/java/com/ttcoachai/pose/PoseBackend.kt`, unmodified)
  - `data class Keypoint2D(val x: Float, val y: Float, val score: Float)`
    (`shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Keypoint2D.kt`, unmodified)
  - MediaPipe Tasks Vision API added in Task 1: `com.google.mediapipe.tasks.core.BaseOptions`,
    `com.google.mediapipe.tasks.core.Delegate`, `com.google.mediapipe.tasks.vision.core.RunningMode`,
    `com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker`,
    `com.google.mediapipe.framework.image.BitmapImageBuilder`,
    `com.google.mediapipe.tasks.components.containers.NormalizedLandmark`.

#### Steps

- [ ] **Step 1: Write the full backend file**

  Create `app/src/main/java/com/ttcoachai/pose/MediaPipePoseLandmarkerBackend.kt`:
  ```kotlin
  package com.ttcoachai.pose

  // MediaPipePoseLandmarkerBackend.kt — THROWAWAY PROTOTYPE (FPS A/B bench only,
  // PoseBenchmarkActivity). Wraps MediaPipe Tasks Vision's PoseLandmarker (BlazePose, 33
  // landmarks) behind the PoseBackend seam so it can be swapped in for RtmposeBackend /
  // MoveNetBackend behind RtmposeFrameProcessor unmodified. RunningMode.IMAGE (synchronous
  // detect()) matches PoseBackend.estimatePose()'s existing blocking contract — no LIVE_STREAM
  // callback bridging needed for a throwaway tool.
  //
  // This is NOT a restoration of the deleted MediaPipe production UI path (PoseLandmarkerHelper /
  // PoseLandmarkerProcessor / PoseLandmarkerConfig, deleted 2026-07-24) — those are gone for good.
  // This is fresh benchmark-only code with no callers outside PoseBenchmarkActivity.

  import android.content.Context
  import android.graphics.Bitmap
  import com.google.mediapipe.framework.image.BitmapImageBuilder
  import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
  import com.google.mediapipe.tasks.core.BaseOptions
  import com.google.mediapipe.tasks.core.Delegate
  import com.google.mediapipe.tasks.vision.core.RunningMode
  import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
  import com.ttcoachai.shared.models.Keypoint2D

  class MediaPipePoseLandmarkerBackend(
      context: Context,
      modelAssetName: String,
      delegate: Delegate
  ) : PoseBackend, AutoCloseable {

      companion object {
          // COCO-17 index -> BlazePose-33 index. BlazePose-only points (face-mesh detail,
          // fingers, heels, foot index 29-32) have no COCO-17 counterpart and are dropped.
          private val COCO17_TO_BLAZEPOSE33 = intArrayOf(
              0,  // 0  nose            <- BlazePose 0  nose
              2,  // 1  left_eye        <- BlazePose 2  left_eye
              5,  // 2  right_eye       <- BlazePose 5  right_eye
              7,  // 3  left_ear        <- BlazePose 7  left_ear
              8,  // 4  right_ear       <- BlazePose 8  right_ear
              11, // 5  left_shoulder   <- BlazePose 11 left_shoulder
              12, // 6  right_shoulder  <- BlazePose 12 right_shoulder
              13, // 7  left_elbow      <- BlazePose 13 left_elbow
              14, // 8  right_elbow     <- BlazePose 14 right_elbow
              15, // 9  left_wrist      <- BlazePose 15 left_wrist
              16, // 10 right_wrist     <- BlazePose 16 right_wrist
              23, // 11 left_hip        <- BlazePose 23 left_hip
              24, // 12 right_hip       <- BlazePose 24 right_hip
              25, // 13 left_knee       <- BlazePose 25 left_knee
              26, // 14 right_knee      <- BlazePose 26 right_knee
              27, // 15 left_ankle      <- BlazePose 27 left_ankle
              28  // 16 right_ankle     <- BlazePose 28 right_ankle
          )
      }

      private val landmarker: PoseLandmarker

      init {
          val baseOptions = BaseOptions.builder()
              .setDelegate(delegate)
              .setModelAssetPath(modelAssetName)
              .build()
          val options = PoseLandmarker.PoseLandmarkerOptions.builder()
              .setBaseOptions(baseOptions)
              .setRunningMode(RunningMode.IMAGE)
              .build()
          // Throws on failure (bad model asset, unsupported GPU delegate, etc.) — intentionally
          // not caught here; PoseBenchmarkActivity.switchBackend() already Toasts + logs it.
          landmarker = PoseLandmarker.createFromOptions(context, options)
      }

      override fun estimatePose(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): List<Keypoint2D> {
          val mpImage = BitmapImageBuilder(bitmap).build()
          val result = landmarker.detect(mpImage)
          val blazepose33: List<NormalizedLandmark> = result.landmarks().firstOrNull() ?: return emptyList()
          if (blazepose33.size < 29) return emptyList()

          return COCO17_TO_BLAZEPOSE33.map { blazeIndex ->
              val lm = blazepose33[blazeIndex]
              Keypoint2D(
                  x = lm.x().coerceIn(0f, 1f),
                  y = lm.y().coerceIn(0f, 1f),
                  score = lm.visibility().orElse(0f).coerceIn(0f, 1f)
              )
          }
      }

      override fun close() {
          landmarker.close()
      }
  }
  ```

- [ ] **Step 2: Compile check**

  ```bash
  ./gradlew :app:assembleDebug
  ```
  Expect success. The class has no callers yet (added in Task 3) — this only validates it compiles
  against the real MediaPipe API surface.

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/ttcoachai/pose/MediaPipePoseLandmarkerBackend.kt
  git commit -m "$(cat <<'EOF'
  feat(pose): add MediaPipePoseLandmarkerBackend for FPS benchmark

  PoseBackend wrapper around MediaPipe Tasks Vision's PoseLandmarker (RunningMode.IMAGE,
  synchronous detect()), mapping BlazePose-33 landmarks to COCO-17 via an explicit index
  table. Benchmark-only — no callers outside PoseBenchmarkActivity.
  EOF
  )"
  ```

---

### Task 3: Wire MediaPipe Lite/Full CPU/GPU into `PoseBenchmarkActivity`

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/pose/PoseBenchmarkActivity.kt`

**Interfaces:**
- Consumes: `MediaPipePoseLandmarkerBackend(context, modelAssetName, delegate)` (Task 2),
  `com.google.mediapipe.tasks.core.Delegate.{CPU,GPU}`.
- Produces: extended `private enum class BackendKind` (6 entries), extended
  `switchBackend(kind: BackendKind)` construction branches, extended
  `displayName(kind: BackendKind): String`, extended toggle-button cycle `when`.

#### Steps

- [ ] **Step 1: Add the `Delegate` import**

  In `app/src/main/java/com/ttcoachai/pose/PoseBenchmarkActivity.kt`:
  ```kotlin
  import androidx.core.content.ContextCompat
  import com.ttcoachai.shared.models.Keypoint2D
  ```
  Replace with:
  ```kotlin
  import androidx.core.content.ContextCompat
  import com.google.mediapipe.tasks.core.Delegate
  import com.ttcoachai.shared.models.Keypoint2D
  ```

- [ ] **Step 2: Extend `BackendKind`**

  Replace:
  ```kotlin
      private enum class BackendKind { RTMPOSE_LITE, MOVENET }
  ```
  With:
  ```kotlin
      private enum class BackendKind {
          RTMPOSE_LITE, MOVENET,
          MEDIAPIPE_LITE_CPU, MEDIAPIPE_LITE_GPU,
          MEDIAPIPE_FULL_CPU, MEDIAPIPE_FULL_GPU
      }
  ```

- [ ] **Step 3: Extend the toggle-button cycle**

  Replace:
  ```kotlin
          toggleButton.setOnClickListener {
              val next = when (activeKind) {
                  BackendKind.RTMPOSE_LITE -> BackendKind.MOVENET
                  BackendKind.MOVENET -> BackendKind.RTMPOSE_LITE
              }
              switchBackend(next)
          }
  ```
  With:
  ```kotlin
          toggleButton.setOnClickListener {
              val next = when (activeKind) {
                  BackendKind.RTMPOSE_LITE -> BackendKind.MOVENET
                  BackendKind.MOVENET -> BackendKind.MEDIAPIPE_LITE_CPU
                  BackendKind.MEDIAPIPE_LITE_CPU -> BackendKind.MEDIAPIPE_LITE_GPU
                  BackendKind.MEDIAPIPE_LITE_GPU -> BackendKind.MEDIAPIPE_FULL_CPU
                  BackendKind.MEDIAPIPE_FULL_CPU -> BackendKind.MEDIAPIPE_FULL_GPU
                  BackendKind.MEDIAPIPE_FULL_GPU -> BackendKind.RTMPOSE_LITE
              }
              switchBackend(next)
          }
  ```

- [ ] **Step 4: Extend the backend-construction `when`**

  Replace:
  ```kotlin
          val newBackend: PoseBackend? = try {
              when (kind) {
                  BackendKind.RTMPOSE_LITE -> RtmposeBackend(
                      context = this,
                      yoloxAssetName = "yolox_tiny_8xb8-300e_humanart-6f3252f9.onnx",
                      rtmposeAssetName = "rtmpose-s_simcc-body7_pt-body7_420e-256x192-acd4a1ef_20230504.onnx",
                      detInputSize = 416
                  )
                  BackendKind.MOVENET -> MoveNetBackend(this)
              }
          } catch (e: Exception) {
  ```
  With:
  ```kotlin
          val newBackend: PoseBackend? = try {
              when (kind) {
                  BackendKind.RTMPOSE_LITE -> RtmposeBackend(
                      context = this,
                      yoloxAssetName = "yolox_tiny_8xb8-300e_humanart-6f3252f9.onnx",
                      rtmposeAssetName = "rtmpose-s_simcc-body7_pt-body7_420e-256x192-acd4a1ef_20230504.onnx",
                      detInputSize = 416
                  )
                  BackendKind.MOVENET -> MoveNetBackend(this)
                  BackendKind.MEDIAPIPE_LITE_CPU -> MediaPipePoseLandmarkerBackend(
                      context = this, modelAssetName = "pose_landmarker_lite.task", delegate = Delegate.CPU)
                  BackendKind.MEDIAPIPE_LITE_GPU -> MediaPipePoseLandmarkerBackend(
                      context = this, modelAssetName = "pose_landmarker_lite.task", delegate = Delegate.GPU)
                  BackendKind.MEDIAPIPE_FULL_CPU -> MediaPipePoseLandmarkerBackend(
                      context = this, modelAssetName = "pose_landmarker_full.task", delegate = Delegate.CPU)
                  BackendKind.MEDIAPIPE_FULL_GPU -> MediaPipePoseLandmarkerBackend(
                      context = this, modelAssetName = "pose_landmarker_full.task", delegate = Delegate.GPU)
              }
          } catch (e: Exception) {
  ```

- [ ] **Step 5: Extend `displayName()`**

  Replace:
  ```kotlin
      private fun displayName(kind: BackendKind): String = when (kind) {
          BackendKind.RTMPOSE_LITE -> "RTMPose-lite"
          BackendKind.MOVENET -> "movenet"
      }
  ```
  With:
  ```kotlin
      private fun displayName(kind: BackendKind): String = when (kind) {
          BackendKind.RTMPOSE_LITE -> "RTMPose-lite"
          BackendKind.MOVENET -> "movenet"
          BackendKind.MEDIAPIPE_LITE_CPU -> "MediaPipe Lite (CPU)"
          BackendKind.MEDIAPIPE_LITE_GPU -> "MediaPipe Lite (GPU)"
          BackendKind.MEDIAPIPE_FULL_CPU -> "MediaPipe Full (CPU)"
          BackendKind.MEDIAPIPE_FULL_GPU -> "MediaPipe Full (GPU)"
      }
  ```

- [ ] **Step 6: Compile check**

  ```bash
  ./gradlew :app:assembleDebug
  ```
  Expect success — all 6 `when` branches now exhaustive in all three places (toggle cycle,
  construction, `displayName`).

- [ ] **Step 7: Commit**

  ```bash
  git add app/src/main/java/com/ttcoachai/pose/PoseBenchmarkActivity.kt
  git commit -m "$(cat <<'EOF'
  feat(pose): wire MediaPipe Lite/Full CPU/GPU into PoseBenchmarkActivity toggle

  Extends the benchmark toggle from 2 to 6 entries: RTMPose-lite, MoveNet, and four
  MediaPipePoseLandmarkerBackend combinations (Lite/Full x CPU/GPU).
  EOF
  )"
  ```

---

### Task 4: Re-enable the manifest entry and manually verify on-device

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `.pose.PoseBenchmarkActivity` (existing class, unmodified this task).
- Produces: a launchable `<activity>` entry for it.

#### Steps

- [ ] **Step 1: Replace the disabling comment with a real `<activity>` entry**

  In `app/src/main/AndroidManifest.xml`, replace:
  ```xml
          <!-- PoseBenchmarkActivity (RTMPose-lite vs MoveNet FPS A/B bench) is TEMPORARILY HIDDEN
               from the manifest to keep the shipped app lean (no MoveNet/balanced-RTMPose model
               assets bundled). Source is still at pose/PoseBenchmarkActivity.kt; re-add this
               <activity> entry (and the model assets) to bring the benchmark screen back. -->
  ```
  With (matching the existing `RtmposeDrillActivity` entry's pattern in this same file):
  ```xml
          <!-- PoseBenchmarkActivity (RTMPose-lite / MoveNet / MediaPipe Lite+Full CPU/GPU FPS A/B
               bench, dev-only, runtime FLAG_DEBUGGABLE gate protects release).
               Exported so `adb shell am start` can launch it directly. -->
          <activity
              android:name=".pose.PoseBenchmarkActivity"
              android:exported="true"
              android:screenOrientation="portrait"
              android:parentActivityName=".MainActivity" />
  ```

- [ ] **Step 2: Build and install the debug APK**

  ```bash
  ./gradlew :app:assembleDebug
  adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
  ```
  (Or `scripts/run_on_phone.sh`, which does build+install+launch-MainActivity in one step — either
  is fine here since Step 3 launches `PoseBenchmarkActivity` explicitly regardless.)

- [ ] **Step 3: Launch `PoseBenchmarkActivity` directly**

  ```bash
  adb shell pm grant com.ttcoachai android.permission.CAMERA
  adb shell am start -n com.ttcoachai/.pose.PoseBenchmarkActivity
  ```

- [ ] **Step 4: Manually cycle all 6 backends and verify**

  Tap "Switch backend" repeatedly (or `adb shell input tap <x> <y>` on the button) to cycle through
  all 6 `BackendKind` entries. For each, confirm:
  - The `backend: <name>` label updates to match (`RTMPose-lite`, `movenet`,
    `MediaPipe Lite (CPU)`, `MediaPipe Lite (GPU)`, `MediaPipe Full (CPU)`, `MediaPipe Full (GPU)`).
  - For the 4 MediaPipe entries: the `fps:` readout updates to a real number (not stuck at `--`)
    and the skeleton overlay tracks a person sanely (not garbage/empty) — use the
    `phone-screenshot` skill to capture evidence if useful.
  - For `MOVENET`: a "Backend init failed: ..." Toast is EXPECTED (pre-existing missing
    `movenet_thunder.tflite` asset, out of scope — see Global Constraints). This is not a
    regression from this plan; do not attempt to fix it here.
  - If a GPU delegate entry (`MEDIAPIPE_LITE_GPU` / `MEDIAPIPE_FULL_GPU`) fails to init on this
    device, the same "Backend init failed: ..." Toast is the correct, already-implemented handling
    per the design doc — not a bug to fix, just note it in the task-completion report.

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/AndroidManifest.xml
  git commit -m "$(cat <<'EOF'
  chore(pose): re-enable PoseBenchmarkActivity manifest entry

  Makes the benchmark screen launchable again now that MediaPipe Lite/Full CPU/GPU are
  wired into its backend toggle alongside RTMPose-lite/MoveNet.
  EOF
  )"
  ```
