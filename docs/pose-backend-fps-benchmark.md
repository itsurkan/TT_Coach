# Pose backend FPS benchmark

**Date:** 2026-07-24

## Intro

Live on-device FPS comparison of the `PoseBackend` implementations wired into the dev-only `PoseBenchmarkActivity` (app/src/main/java/com/ttcoachai/pose/PoseBenchmarkActivity.kt). Launch via `adb shell am start -n com.ttcoachai/.pose.PoseBenchmarkActivity` (FLAG_DEBUGGABLE-gated debug builds only); the "Switch backend" button cycles all six backends and the on-screen readout shows a rolling-average FPS (FpsTracker, wall-clock from CameraX frame arrival to pose-result callback).

## Device / conditions

- Device: Samsung Galaxy S23
- Single on-device run, 2026-07-24
- CameraX 4:3 back camera, one person in frame
- FPS = FpsTracker rolling 30-sample average

## Results

| Backend | Model | Delegate | FPS |
|---|---|---|---|
| MediaPipe Full (GPU) | pose_landmarker_full.task | GPU | 34.6 |
| MediaPipe Lite (GPU) | pose_landmarker_lite.task | GPU | 34.4 |
| MediaPipe Lite (CPU) | pose_landmarker_lite.task | CPU | 29.5 |
| MediaPipe Full (CPU) | pose_landmarker_full.task | CPU | 28.0 |
| RTMPose-lite | rtmpose-s (ONNX Runtime Mobile) | CPU | 8.4 |
| MoveNet Thunder | movenet_thunder.tflite | CPU (TFLite) | not measured this run |

## Observations

- MediaPipe PoseLandmarker is ~3.5–4× faster than RTMPose-lite on this device.
- Lite vs Full made a negligible FPS difference here; the GPU delegate bought roughly 20% over CPU.
- MoveNet did not produce a number in this run because its `movenet_thunder.tflite` asset was missing at build time (later restored). A prior measurement on the S23 put MoveNet Thunder at ~18–20 fps (see project memory), but that is not from this run.

## Caveats

- This is a THROUGHPUT benchmark only. Keypoint/skeleton QUALITY was not evaluated — fast FPS does not imply the pose output is accurate enough for coaching.
- Single run, one device, one lighting/scene. Treat as indicative, not authoritative.
- Numbers were read off the on-screen FPS readout by eye, not logged programmatically.
- `PoseBenchmarkActivity` is a dev-only throwaway measurement tool, not shipped UI.
