# MediaPipe PoseLandmarker benchmark backend — design

Date: 2026-07-24

## Context

`PoseBenchmarkActivity` (`app/src/main/java/com/ttcoachai/pose/PoseBenchmarkActivity.kt`) is a
dev-only, `FLAG_DEBUGGABLE`-gated throwaway screen (manifest entry currently commented out by
`3eda388`) that A/B's `PoseBackend` implementations live on-device and reports rolling-average FPS
via `FpsTracker`. It currently cycles `RTMPOSE_LITE ↔ MOVENET`.

MediaPipe's live inference path (`tasks-vision`, `PoseLandmarker`, and all supporting Kotlin code)
was fully deleted from the app in the "MediaPipe legacy calibration/inference path removed"
cleanup (2026-07-24) — there is no old implementation to restore. However
`pose_landmarker_lite.task` / `pose_landmarker_full.task` / `pose_landmarker_heavy.task` are still
bundled (unreferenced) in `app/src/main/assets/`.

Goal: add MediaPipe PoseLandmarker back as benchmark-only backend options, to compare FPS against
RTMPose-lite and MoveNet Thunder, without resurrecting any of the deleted production UI/paths.

## Scope

In scope:
- `Lite` and `Full` PoseLandmarker model variants (not `Heavy` — dropped as a benchmark option;
  the `.task` asset can stay bundled unreferenced or be removed later, out of scope here).
- Both CPU and GPU delegate, as separate toggle entries (4 new entries total).
- Re-enabling the `PoseBenchmarkActivity` manifest entry so the screen is reachable again.

Out of scope:
- Any production/user-reachable code path. This stays confined to the benchmark activity and its
  new backend class.
- Restoring the old 33→COCO-17 BlazePose mapping from git history — none exists; write fresh.
- Automated tests — this tool has none today; verification is manual on-device FPS/overlay checks,
  consistent with existing precedent.

## Components

### 1. `app/build.gradle`
Add `com.google.mediapipe:tasks-vision` (version matching what was previously used, or latest
stable at implementation time — confirm no conflicting transitive deps with ONNX Runtime Mobile /
CameraX already in the project).

### 2. `MediaPipePoseLandmarkerBackend.kt` (new, `app/src/main/java/com/ttcoachai/pose/`)
Implements `PoseBackend`:
```kotlin
fun interface PoseBackend {
    fun estimatePose(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): List<Keypoint2D>
}
```
Constructor: `(context: Context, modelAssetName: String, delegate: Delegate)` where `Delegate` is
MediaPipe's `Delegate.CPU` / `Delegate.GPU`.

- Builds `PoseLandmarker` via `PoseLandmarkerOptions` with `RunningMode.IMAGE` (synchronous —
  matches `PoseBackend.estimatePose()`'s existing blocking contract, no changes needed elsewhere
  in the pipeline; simpler than bridging MediaPipe's async `LIVE_STREAM` mode with a latch for a
  throwaway tool).
- `estimatePose()`: wraps the input `Bitmap` in an `MPImage` (`BitmapImageBuilder`), calls
  `landmarker.detect(image)` synchronously, takes the first detected pose's 33 BlazePose
  landmarks, maps them to COCO-17 order + indices via a new mapping table (BlazePose and COCO-17
  overlap on ~17 shared joints; the mapping is a straightforward index lookup + the handful of
  BlazePose-only points dropped), and returns normalized `Keypoint2D`s using the same per-axis
  normalization convention (`x / frameWidth`, `y / frameHeight`) as the other backends.
- Implements `AutoCloseable`, closing the underlying `PoseLandmarker` — required because
  `PoseBenchmarkActivity.switchBackend()` already does `(backend as? AutoCloseable)?.close()`.
- No result caching or GPU-fallback logic needed: GPU init failure surfaces as a thrown exception,
  which `switchBackend()`'s existing `try/catch` already Toasts and logs.

### 3. `PoseBenchmarkActivity.kt`
- Extend `BackendKind`:
  ```kotlin
  private enum class BackendKind {
      RTMPOSE_LITE, MOVENET,
      MEDIAPIPE_LITE_CPU, MEDIAPIPE_LITE_GPU,
      MEDIAPIPE_FULL_CPU, MEDIAPIPE_FULL_GPU
  }
  ```
- `switchBackend()`'s `when` cycles through all 6 kinds in declaration order (toggle button already
  advances via a `when` returning "next" — extend it to the full cycle).
- Construct each MediaPipe entry as:
  ```kotlin
  BackendKind.MEDIAPIPE_LITE_CPU -> MediaPipePoseLandmarkerBackend(
      context = this, modelAssetName = "pose_landmarker_lite.task", delegate = Delegate.CPU)
  BackendKind.MEDIAPIPE_LITE_GPU -> MediaPipePoseLandmarkerBackend(
      context = this, modelAssetName = "pose_landmarker_lite.task", delegate = Delegate.GPU)
  BackendKind.MEDIAPIPE_FULL_CPU -> MediaPipePoseLandmarkerBackend(
      context = this, modelAssetName = "pose_landmarker_full.task", delegate = Delegate.CPU)
  BackendKind.MEDIAPIPE_FULL_GPU -> MediaPipePoseLandmarkerBackend(
      context = this, modelAssetName = "pose_landmarker_full.task", delegate = Delegate.GPU)
  ```
- `displayName()` extended with matching labels (e.g. `"MediaPipe Lite (CPU)"`).

### 4. `AndroidManifest.xml`
Uncomment the `PoseBenchmarkActivity` `<activity>` entry (currently disabled by `3eda388`) so the
screen is launchable via adb again.

### 5. Assets
No new asset fetch — `pose_landmarker_lite.task` and `pose_landmarker_full.task` are already
bundled in `app/src/main/assets/`. `pose_landmarker_heavy.task` stays bundled but unreferenced
(cleanup optional, out of scope).

## Error handling

Reuses `PoseBenchmarkActivity.switchBackend()`'s existing `try/catch`: any construction failure
(missing GPU delegate support, bad model asset, etc.) is caught, logged, and surfaced via `Toast`;
`backend` is left `null` and the FPS readout shows `--` until the next successful switch.

## Testing / verification

No unit tests (matches existing precedent for this throwaway tool). Manual verification: build
debug APK, launch `PoseBenchmarkActivity` via adb, cycle through all 6 `BackendKind` entries,
confirm the FPS readout updates for each and the skeleton overlay looks sane (not garbage/empty)
for both MediaPipe variants and both delegates.
