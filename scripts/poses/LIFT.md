# 3D temporal lifting — `lift_pose3d.py`

**Throwaway visual experiment** (not a product feature, not in the drill pipeline). Lifts the
existing RTMPose 2D keypoints to temporally-coherent 3D with **MotionAGFormer**, for viewing as a
rotatable skeleton in `poses_viewer` (`#/pose3d`). Depth is reconstructed from a *temporal window*
of 2D keypoints — the context-doc §5 "correct" path, distinct from per-frame monocular 3D (Apple
Vision / MediaPipe-z, which the 2D pivot rejected; see DESIGN_LIMITATIONS L-21/L-22).

**Caveat:** MotionAGFormer is trained on Human3.6M (everyday motion). Fast table-tennis strokes on a
side camera are out-of-distribution → expect artifacts. Good for eyeballing 3D plausibility, **not**
for trustworthy angles. If depth looks degenerate, the in-scope next experiments are the `-L`
checkpoint or a longer window — they don't change the conclusion's status as exploratory.

## What's vendored (gitignored, re-fetchable)

- `scripts/poses/vendor/MotionAGFormer/` — clone of https://github.com/TaatiTeam/MotionAGFormer (WACV 2024)
- `scripts/poses/vendor/MotionAGFormer/checkpoint/motionagformer-b-h36m.pth.tr` — pretrained **-B** (243-frame) H36M weights, ~135 MB
- `.venv-lift/` — a torch-capable Python env (the project `.venv` is ONNX/rtmlib only, no torch)

## One-time setup

```bash
# 1. vendor the model code
git clone --depth 1 https://github.com/TaatiTeam/MotionAGFormer.git scripts/poses/vendor/MotionAGFormer

# 2. torch env (timm is NOT needed — lift_pose3d.py shims out DropPath; timm 0.6.11 won't import on py3.13)
python3 -m venv .venv-lift
.venv-lift/bin/pip install "torch>=2.5" numpy gdown

# 3. pretrained -B checkpoint (Google Drive id from the repo README "MotionAGFormer-B / H36M" download)
mkdir -p scripts/poses/vendor/MotionAGFormer/checkpoint
.venv-lift/bin/gdown 1Iii5EwsFFm9_9lKBUPfN8bV5LmfkNUMP \
  -O scripts/poses/vendor/MotionAGFormer/checkpoint/motionagformer-b-h36m.pth.tr
```

## Run

Accepts any of three 2D sources (auto-detected from the filename); output is tagged per source:

```bash
.venv-lift/bin/python scripts/poses/lift_pose3d.py Videos/<base>/<base>_poses_rtm.json     # -> <base>_pose3d_lift_rtm.json
.venv-lift/bin/python scripts/poses/lift_pose3d.py Videos/<base>/<base>_poses_vision.json  # -> <base>_pose3d_lift_vision.json   (Apple Vision 2D export)
.venv-lift/bin/python scripts/poses/lift_pose3d.py Videos/<base>/<base>_poses.json         # -> <base>_pose3d_lift_mediapipe.json (legacy MediaPipe-33 v1)
# default device cpu; --device mps to try Metal; --source to override detection
```

Then `cd poses_viewer && npm run dev` → open `http://localhost:5780/#/pose3d`, pick the video, use
the **RTM / Vision / MediaPipe** toggle to compare sources, drag to orbit. (`andrii_1`, `video_3`,
`video_4` are good clips; MediaPipe only exists for `andrii_1`.)

## Pipeline (mirrors `vendor/MotionAGFormer/demo/vis.py`)

2D pose JSON → COCO-17 px (RTM/Vision direct; MediaPipe-33 via `MP_TO_COCO`) → **standard
`coco_h36m`** (COCO-17 → H36M-17; 1-3=right leg, 4-6=left leg, 11-13=left arm, 14-16=right arm;
pelvis=hip-mid, thorax/spine/head synthesized) → `normalize_screen_coordinates` → 243-frame
windowed inference + horizontal-flip TTA →
`camera_to_world` (fixed demo rotation, upright) → self-describing `*_pose3d_lift.json`
(`topology:"h36m17"`, embeds `joints` + `bones`; model-world axes +x right / +y depth / **+z up**;
root at origin). Missing/empty 2D frames are linearly interpolated over time so the temporal model
sees a continuous signal; each output frame carries a `detected` flag.
