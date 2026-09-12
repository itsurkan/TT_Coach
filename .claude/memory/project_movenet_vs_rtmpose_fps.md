---
name: movenet-vs-rtmpose-fps
description: "On-device FPS A/B result — MoveNet Thunder ~18-20fps vs RTMPose ~1.2-1.5fps on Samsung S23, both CPU-only"
metadata: 
  node_type: memory
  type: project
  originSessionId: c4a72a81-7a12-4d1d-9f61-59d0f39c7355
  modified: 2026-07-24T11:49:39.892Z
---

Measured 2026-07-24 on the user's Samsung Galaxy S23 (SM-S911B) via a throwaway prototype
(`PoseBenchmarkActivity`, commit `b966933` cherry-picked onto `main`): **RTMPose ~1.2-1.5 fps**
vs **MoveNet Thunder ~18-20 fps** — roughly 13-15x. Both ran CPU-only, no GPU/NNAPI delegate on
either side (`OrtSessionFactory` forces `cpuOnly=true`; `MoveNetEstimator` uses a plain
TFLite `Interpreter` with no delegate) — a fair apples-to-apples comparison, not a hardware-accel
artifact.

**Why:** RTMPose is two-stage (YOLOX person-detect at 640×640 + RTMPose pose at 256×192, both
full ONNX Runtime passes every frame — see `RtmposeBackend.kt`'s "detect EVERY frame" comment).
MoveNet Thunder is single-stage, 256×256, no separate detector.

**How to apply:** At 1.2-1.5 fps RTMPose is not viable for the live 3-5s coaching-cue cadence
Phase 3 currently ships on. This is a real, decision-relevant finding for any future backend
work — but FPS alone isn't the full picture: MoveNet's keypoint *accuracy* on real drill footage
was NOT yet validated when this was measured (the benchmark activity has a skeleton overlay for
exactly that check — do that before treating MoveNet as a drop-in replacement). If accuracy holds
up, this reopens the on-device backend choice for the live path; RTMPose may still be worth
keeping for offline/desktop-parity export (`scripts/poses/export_poses_rtmpose.py`) where speed
doesn't matter as much.

Also surfaced two unrelated pre-existing bugs while testing this:
1. `RtmposeBackend.DEFAULT_YOLOX_ASSET_NAME` / `DEFAULT_RTMPOSE_ASSET_NAME` were missing the
   `.onnx` extension that `fetch_models.sh` actually saves files with — RTMPose could never
   have loaded on a real device with fetched assets before this was fixed (commit fixing this
   lands alongside the prototype). Broken since the constants were added 2026-07-03; apparently
   never runtime-tested until this session.
2. The RTMPose ONNX models (`.onnx`, gitignored, ~150MB combined) must be fetched manually via
   `app/src/main/assets/fetch_models.sh` before building — no gradle/CI task does this
   automatically, easy to forget on a fresh checkout.

Prototype backend seam used: `PoseBackend` interface (`app/src/main/java/com/ttcoachai/pose/PoseBackend.kt`)
already supports swapping implementations cleanly — see [[project_ios_future]] for why keeping
this in `shared/`-friendly, backend-agnostic shape matters for iOS too (iOS has its own native
pose detector option, separate from both of these).
