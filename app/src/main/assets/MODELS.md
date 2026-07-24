# Bundled ONNX models — Android RTMPose backend

Two presets are bundled, selectable in one line via `RtmposeBackend.ACTIVE_PRESET`
(`app/src/main/java/com/ttcoachai/pose/RtmposeBackend.kt`):

- **LITE** (production default) — yolox_tiny (416×416) + rtmpose-s (256×192), from `rtmlib`
  `Body(mode="lightweight")`. Benchmarked ~8fps on-device.
- **BALANCED** — yolox_m (640×640) + rtmpose-m (256×192), from `rtmlib` `Body(mode="balanced")`.
  This is the exact pairing the desktop golden pipeline runs
  (`scripts/poses/export_poses_rtmpose.py`), matching it byte-for-byte lets Android exports reuse
  the same fixtures, rep-count gates, and baselines as the desktop pipeline and the iOS backend
  (mirrors `iosApp/TTCoach/Models/MODELS.md`). Benchmarked 1.2–1.5fps on-device — too slow for a
  live drill. **NOT auto-fetched** (see below) to keep the app lean; fetch it manually if you swap
  `ACTIVE_PRESET` back.

They are **git-ignored** (yolox_m is 97 MB, over GitHub's comfort threshold). `./fetch_models.sh`
fetches ONLY the LITE preset (the active default) to keep the built app small — the BALANCED
files are documented below (URLs + SHAs) but must be fetched by hand (e.g. `curl` the URL, unzip,
verify against the SHA in the table, drop into this directory) if you ever swap `ACTIVE_PRESET`
back to `Preset.BALANCED`. The build bundles assets as raw (uncompressed, via `noCompress 'onnx'`)
so ONNX Runtime can mmap them.

Also **not bundled**: `movenet_thunder.tflite` (MoveNet Thunder, used only by the now-hidden
`PoseBenchmarkActivity` FPS A/B bench, `app/src/main/java/com/ttcoachai/pose/PoseBenchmarkActivity.kt`
— its `<activity>` manifest entry is commented out). Re-fetch it from TF Hub
(`tfhub.dev/google/lite-model/movenet/singlepose/thunder/tflite/float16/4`) and re-add the
manifest entry if the benchmark screen is needed again.

| File | Preset | Role | Input | Size | SHA-256 |
|---|---|---|---|---|---|
| `yolox_tiny_8xb8-300e_humanart-6f3252f9.onnx` | LITE | person detector | 416×416 | 20 MB | `ceb11c07298f95c50d7c5abeb906d03340c85f23aa79e3e66966e7fb6c307250` |
| `rtmpose-s_simcc-body7_pt-body7_420e-256x192-acd4a1ef_20230504.onnx` | LITE | pose (SimCC) | 256×192 | 21 MB | `9aeb635b83f86aea45cf45d85798f7eba1a162de8e0d721c44e54fe5eebaf47d` |
| `yolox_m_8xb8-300e_humanart-c2c7a14a.onnx` | BALANCED | person detector | 640×640 | 97 MB | `3dea6513388889f0fff4b77bf7a26013600321b9eb9ceb0e9a400a82572f5f23` |
| `rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504.onnx` | BALANCED | pose (SimCC) | 256×192 | 52 MB | `5c0a4bf67953e6d2ac43ce15e77dc9d5d354ae18430a47d2c5963a7bc5683e3c` |

## Source URLs

From `rtmlib/tools/solution/body.py`:

`MODE['lightweight']`:
- Detector: https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/yolox_tiny_8xb8-300e_humanart-6f3252f9.zip
- Pose: https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/rtmpose-s_simcc-body7_pt-body7_420e-256x192-acd4a1ef_20230504.zip

`MODE['balanced']`:
- Detector: https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/yolox_m_8xb8-300e_humanart-c2c7a14a.zip
- Pose: https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504.zip

The `.onnx` is inside each `.zip`.

`fetch_models.sh` first tries to copy each file from the iOS model directory
(`iosApp/TTCoach/Models/`, main working tree), then falls back to `~/.cache/rtmlib/hub/checkpoints/`,
then downloads + unzips from the URLs above, then verifies the SHA-256s.
