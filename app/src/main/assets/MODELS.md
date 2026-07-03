# Bundled ONNX models — Android RTMPose backend

These two models are the **exact files** the desktop golden pipeline runs
(`rtmlib` `Body(mode="balanced")`, see `scripts/poses/export_poses_rtmpose.py`).
Matching them byte-for-byte is what lets the Android exports reuse the same fixtures,
rep-count gates, and baselines as the desktop RTMPose pipeline and the iOS backend
(mirrors `iosApp/TTCoach/Models/MODELS.md`).

They are **git-ignored** (YOLOX-m is 97 MB, over GitHub's comfort threshold). Fetch
them with `./fetch_models.sh` before building. The build bundles them as raw (uncompressed,
via `noCompress 'onnx'`) assets of the `app` module so ONNX Runtime can mmap them.

| File | Role | Input | Size | SHA-256 |
|---|---|---|---|---|
| `yolox_m_8xb8-300e_humanart-c2c7a14a.onnx` | person detector | 640×640 | 97 MB | `3dea6513388889f0fff4b77bf7a26013600321b9eb9ceb0e9a400a82572f5f23` |
| `rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504.onnx` | pose (SimCC) | 256×192 | 52 MB | `5c0a4bf67953e6d2ac43ce15e77dc9d5d354ae18430a47d2c5963a7bc5683e3c` |

## Source URLs

From `rtmlib/tools/solution/body.py`, `MODE['balanced']` (the `.onnx` is inside each `.zip`):

- Detector: https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/yolox_m_8xb8-300e_humanart-c2c7a14a.zip
- Pose: https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504.zip

`fetch_models.sh` first tries to copy the files from the iOS model directory
(`iosApp/TTCoach/Models/`, main working tree), then falls back to `~/.cache/rtmlib/hub/checkpoints/`,
then downloads + unzips from the URLs above, then verifies the SHA-256s.
