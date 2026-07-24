# Bundled models — Android pose backends

## MediaPipe PoseLandmarker (production live backend)

`pose_landmarker_lite.task` and `pose_landmarker_full.task` are git-committed and bundled in
the APK. The live 2D drill pipeline (training, calibration, standalone drill) resolves one of
them at runtime through `PoseBackendFactory`/`PoseBackendVariant`
(`app/src/main/java/com/ttcoachai/pose/PoseBackendFactory.kt`), selectable from the Settings
"Pose model" picker: Lite/Full x GPU (`Delegate.GPU`, default)/CPU (`Delegate.CPU`).

`pose_landmarker_heavy.task` is not present and not fetched — no `PoseBackendVariant` entry
loads it. `app/download_tasks.gradle` used to force-fetch it into `app/src/main/assets/` on
every `preBuild`; since Android bundles the entire `assets/` directory into the APK regardless
of whether app code references a given file, that shipped ~29 MB of unused model in every build
despite no code path loading it. That download task was removed — heavy no longer exists
anywhere in the build (source tree or APK).

Bundled raw/uncompressed (`noCompress 'task', 'tflite'` in `app/build.gradle`) so MediaPipe's
native model loader can read them directly instead of through Android's asset compression.

| File | Role | Size |
|---|---|---|
| `pose_landmarker_lite.task` | production default (Lite) | ~5.5 MB |
| `pose_landmarker_full.task` | production (Full) | ~9.0 MB |

## MoveNet Thunder (FPS benchmark only)

`movenet_thunder.tflite` is git-tracked and bundled raw (`noCompress 'tflite'`). Used only by
`PoseBenchmarkActivity` (dev-only MoveNet vs. MediaPipe FPS A/B bench, FLAG_DEBUGGABLE-gated) —
its `<activity>` manifest entry is live (not commented out), so it's reachable via
`adb shell am start -n com.ttcoachai/.pose.PoseBenchmarkActivity` for local profiling builds.
