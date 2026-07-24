# Bundled models — Android pose backends

## MediaPipe PoseLandmarker (production live backend)

`pose_landmarker_lite.task` and `pose_landmarker_full.task` are git-committed and bundled in
the APK. The live 2D drill pipeline (training, calibration, standalone drill) resolves one of
them at runtime through `PoseBackendFactory`/`PoseBackendVariant`
(`app/src/main/java/com/ttcoachai/pose/PoseBackendFactory.kt`), selectable from the Settings
"Pose model" picker: Lite/Full x GPU (`Delegate.GPU`, default)/CPU (`Delegate.CPU`).

`pose_landmarker_heavy.task` may be present locally but is **not committed** and **not
referenced by any app code** — no `PoseBackendVariant` entry loads it, so it never ships in a
built APK. It exists here only if someone dropped it in for manual experimentation; there is no
fetch script for it.

Bundled raw/uncompressed (`noCompress 'task'` in `app/build.gradle`) so MediaPipe's native model
loader can read them directly instead of through Android's asset compression.

| File | Role | Size |
|---|---|---|
| `pose_landmarker_lite.task` | production default (Lite) | ~5.5 MB |
| `pose_landmarker_full.task` | production (Full) | ~9.0 MB |
| `pose_landmarker_heavy.task` | untracked, unused by app code | ~29 MB |

## MoveNet Thunder (FPS benchmark only)

`movenet_thunder.tflite` is git-tracked and bundled raw (`noCompress 'tflite'`). Used only by
`PoseBenchmarkActivity` (dev-only MoveNet vs. MediaPipe FPS A/B bench, FLAG_DEBUGGABLE-gated) —
its `<activity>` manifest entry is live (not commented out), so it's reachable via
`adb shell am start -n com.ttcoachai/.pose.PoseBenchmarkActivity` for local profiling builds.
