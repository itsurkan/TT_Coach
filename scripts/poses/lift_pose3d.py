#!/usr/bin/env python
"""
Temporal 2D->3D lifting of pose keypoints with MotionAGFormer (throwaway viz experiment).

Input:  one of
          Videos/<base>/<base>_poses_rtm.json     (schema v2, COCO-17/Halpe26 — RTMPose)
          Videos/<base>/<base>_poses_vision.json  (schema v2, COCO-17 — Apple Vision export)
          Videos/<base>/<base>_poses.json         (legacy v1, MediaPipe-33)
Output: Videos/<base>/<base>_pose3d_lift_<source>.json  (self-describing 3D skeleton, H36M-17)

Mirrors the wild-inference pipeline in vendor/MotionAGFormer/demo/vis.py:
  COCO-17 -> H36M-17 (standard coco_h36m) -> normalize_screen_coordinates ->
  243-frame windowed inference (+ horizontal-flip TTA) -> camera_to_world rotation.

Why this is the context-doc "correct" 3D and NOT Apple Vision: depth is reconstructed from a
temporal *window* of 2D keypoints, far more stable than per-frame monocular z. See
scripts/poses/LIFT.md and docs/tt-coach-ai-context.md sec 5. Caveat: MotionAGFormer is trained on
Human3.6M (everyday motion), so fast table-tennis strokes on a side camera are out of distribution
-- expect artifacts; this is a visualization experiment, not trustworthy angles.

Setup / run: see scripts/poses/LIFT.md.
"""
import sys
import os
import json
import copy
import types
import argparse

import numpy as np
import torch
import torch.nn as nn

HERE = os.path.dirname(os.path.abspath(__file__))
VENDOR = os.path.join(HERE, "vendor", "MotionAGFormer")
CHECKPOINT = os.path.join(VENDOR, "checkpoint", "motionagformer-b-h36m.pth.tr")

# --- Shim out timm.models.layers.DropPath ---------------------------------------------------
# MotionAGFormer only needs DropPath, and the -B model runs with drop_path=0.0, where DropPath
# is an identity in eval mode. timm==0.6.11 (the repo's pin) does not import on Python 3.13, so
# we inject a tiny identity module instead of installing timm.
class _DropPath(nn.Module):
    def __init__(self, drop_prob=0.0):
        super().__init__()
        self.drop_prob = drop_prob

    def forward(self, x):
        return x


for _name in ("timm", "timm.models", "timm.models.layers"):
    sys.modules.setdefault(_name, types.ModuleType(_name))
sys.modules["timm.models.layers"].DropPath = _DropPath

sys.path.insert(0, VENDOR)
from model.MotionAGFormer import MotionAGFormer  # noqa: E402

# --- MotionAGFormer-B (243-frame) args, copied verbatim from demo/vis.py get_pose3D ----------
MODEL_ARGS = dict(
    n_layers=16, dim_in=3, dim_feat=128, dim_rep=512, dim_out=3, mlp_ratio=4,
    act_layer=nn.GELU, attn_drop=0.0, drop=0.0, drop_path=0.0, use_layer_scale=True,
    layer_scale_init_value=1e-5, use_adaptive_fusion=True, num_heads=8, qkv_bias=False,
    qkv_scale=None, hierarchical=False, use_temporal_similarity=True, neighbour_num=2,
    temporal_connection_len=1, use_tcn=False, graph_only=False, n_frames=243,
)
N_FRAMES = 243
MODEL_NAME = "motionagformer-b-h36m"

# H36M-17 bone edges (parent->child), identical to demo/vis.py show2Dpose `connections`.
H36M_BONES = [[0, 1], [1, 2], [2, 3], [0, 4], [4, 5], [5, 6], [0, 7], [7, 8], [8, 9], [9, 10],
              [8, 11], [11, 12], [12, 13], [8, 14], [14, 15], [15, 16]]
# Standard Human3.6M-17 joint order (matches `coco_h36m` below): 1-3 = RIGHT leg, 4-6 = LEFT leg,
# 11-13 = LEFT arm, 14-16 = RIGHT arm.
H36M_JOINTS = ["pelvis", "rightHip", "rightKnee", "rightAnkle", "leftHip", "leftKnee", "leftAnkle",
               "spine", "thorax", "nose", "head", "leftShoulder", "leftElbow", "leftWrist",
               "rightShoulder", "rightElbow", "rightWrist"]
# Horizontal-flip TTA: the two mirror-symmetric joint sets (demo flip_data; correct for standard H36M).
FLIP_L = [1, 2, 3, 14, 15, 16]
FLIP_R = [4, 5, 6, 11, 12, 13]
# camera->world rotation quaternion the demo uses for an upright view.
CAM2WORLD_ROT = np.array(
    [0.1407056450843811, -0.1500701755285263, -0.755240797996521, 0.6223280429840088],
    dtype="float32")

# COCO-17 -> H36M-17 scatter (demo/lib/preprocess.py). The earlier version used the repo's *dead*
# `turn_into_h36m`, which swaps left/right legs+arms -> crossed legs. This is the demo's ACTIVE path.
H36M_COCO_ORDER = [9, 11, 14, 12, 15, 13, 16, 4, 1, 5, 2, 6, 3]
COCO_ORDER = [0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]
SPPLE = [10, 8, 0, 7]  # head, thorax, pelvis, spine (synthesized)

# MediaPipe-33 -> COCO-17 index map (nose, L/R eye+ear, shoulders, elbows, wrists, hips, knees, ankles).
MP_TO_COCO = [0, 2, 5, 7, 8, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28]


# --- helpers inlined from demo/lib/utils.py (avoid the demo package import path) --------------
def normalize_screen_coordinates(X, w, h):
    assert X.shape[-1] in (2, 3)
    result = np.copy(X)
    result[..., :2] = X[..., :2] / w * 2 - [1, h / w]
    return result


def _qrot(q, v):
    qvec = q[..., 1:]
    uv = np.cross(qvec, v)
    uuv = np.cross(qvec, uv)
    return v + 2 * (q[..., :1] * uv + uuv)


def camera_to_world(X, R):
    return _qrot(np.tile(R, (*X.shape[:-1], 1)), X)


# --- COCO-17 -> H36M-17 (standard coco_h36m from demo/lib/preprocess.py) ----------------------
def coco_h36m_xy(xy):
    """xy: [F, 17, 2] COCO -> [F, 17, 2] H36M (correct left/right)."""
    F = xy.shape[0]
    out = np.zeros_like(xy, dtype=np.float32)
    htps = np.zeros((F, 4, 2), dtype=np.float32)  # head, thorax, pelvis, spine
    htps[:, 0, 0] = np.mean(xy[:, 1:5, 0], axis=1)
    htps[:, 0, 1] = np.sum(xy[:, 1:3, 1], axis=1) - xy[:, 0, 1]
    htps[:, 1, :] = np.mean(xy[:, 5:7, :], axis=1)
    htps[:, 1, :] += (xy[:, 0, :] - htps[:, 1, :]) / 3
    htps[:, 2, :] = np.mean(xy[:, 11:13, :], axis=1)
    htps[:, 3, :] = np.mean(xy[:, [5, 6, 11, 12], :], axis=1)
    out[:, SPPLE, :] = htps
    out[:, H36M_COCO_ORDER, :] = xy[:, COCO_ORDER, :]
    out[:, 9, :] -= (out[:, 9, :] - np.mean(xy[:, 5:7, :], axis=1)) / 4
    out[:, 7, 0] += 2 * (out[:, 7, 0] - np.mean(out[:, [0, 8], 0], axis=1))
    out[:, 8, 1] -= (np.mean(xy[:, 1:3, 1], axis=1) - xy[:, 0, 1]) * 2 / 3
    return out


def coco_h36m_score(score):
    """score: [F, 17] COCO -> [F, 17] H36M (demo h36m_coco_format score remap)."""
    out = np.zeros_like(score, dtype=np.float32)
    out[:, H36M_COCO_ORDER] = score[:, COCO_ORDER]
    out[:, 0] = np.mean(score[:, [11, 12]], axis=1)
    out[:, 8] = np.mean(score[:, [5, 6]], axis=1)
    out[:, 7] = np.mean(out[:, [0, 8]], axis=1)
    out[:, 10] = np.mean(score[:, [1, 2, 3, 4]], axis=1)
    return out


def flip_data(data):
    """Horizontal flip + L/R joint swap. data: [..., 17, D] (D>=1). demo flip_data."""
    f = copy.deepcopy(data)
    f[..., 0] *= -1
    f[..., FLIP_L + FLIP_R, :] = f[..., FLIP_R + FLIP_L, :]
    return f


def resample(n_frames):
    """243 frame indices spanning [0, n_frames) (demo resample)."""
    even = np.linspace(0, n_frames, num=N_FRAMES, endpoint=False)
    return np.clip(np.floor(even), 0, n_frames - 1).astype(np.int64)


# --- IO --------------------------------------------------------------------------------------
SOURCE_SUFFIX = {"rtm": "_poses_rtm.json", "vision": "_poses_vision.json", "mediapipe": "_poses.json"}


def detect_source(path):
    b = os.path.basename(path)
    if b.endswith("_poses_rtm.json"):
        return "rtm"
    if b.endswith("_poses_vision.json"):
        return "vision"
    if b.endswith("_poses.json"):
        return "mediapipe"
    return "rtm"


def default_out(path, source):
    for suf in ("_poses_rtm.json", "_poses_vision.json", "_poses.json"):
        if path.endswith(suf):
            return path[: -len(suf)] + f"_pose3d_lift_{source}.json"
    return os.path.splitext(path)[0] + f"_pose3d_lift_{source}.json"


def load_2d(path, source):
    """Load a pose JSON (rtm/vision schema-v2 COCO-17, or MediaPipe-33 v1) into COCO-17 pixels.
    Returns (kp[F,17,3] in px, W, H, intervalMs, ts[F], detected[F], video_name).
    Missing/empty frames are linearly interpolated over time; `detected` records real detections.
    """
    with open(path) as fh:
        d = json.load(fh)
    W, H = int(d["videoWidth"]), int(d["videoHeight"])
    interval = int(d.get("intervalMs", 33))
    frames = sorted(d["frames"], key=lambda f: f["frameIndex"])
    F = len(frames)
    kp = np.full((F, 17, 3), np.nan, dtype="float32")
    ts = np.zeros(F, dtype=np.int64)
    detected = np.zeros(F, dtype=bool)
    is_mp = source == "mediapipe"
    for i, fr in enumerate(frames):
        ts[i] = int(fr.get("timestampMs", fr["frameIndex"] * interval))
        lms = fr.get("landmarks", [])
        if is_mp:
            if len(lms) >= 33:
                detected[i] = True
                by_idx = {int(lm["index"]): lm for lm in lms}
                for c, mp in enumerate(MP_TO_COCO):
                    lm = by_idx.get(mp)
                    if lm is not None:
                        kp[i, c, 0] = float(lm["x"]) * W
                        kp[i, c, 1] = float(lm["y"]) * H
                        kp[i, c, 2] = float(lm.get("visibility", lm.get("score", 1.0)))
        else:
            if len(lms) >= 17:
                detected[i] = True
                for lm in lms:
                    j = int(lm["index"])
                    if 0 <= j < 17:
                        kp[i, j, 0] = float(lm["x"]) * W
                        kp[i, j, 1] = float(lm["y"]) * H
                        kp[i, j, 2] = float(lm.get("score", 1.0))
    # Interpolate NaN gaps per joint/coordinate across time.
    t = np.arange(F)
    for j in range(17):
        for c in range(3):
            col = kp[:, j, c]
            valid = ~np.isnan(col)
            if valid.sum() == 0:
                kp[:, j, c] = 0.0
            elif valid.sum() < F:
                kp[:, j, c] = np.interp(t, t[valid], col[valid])
    return kp, W, H, interval, ts, detected, d.get("videoName", os.path.basename(path))


# --- lifting ---------------------------------------------------------------------------------
def build_model(device):
    model = MotionAGFormer(**MODEL_ARGS)
    ckpt = torch.load(CHECKPOINT, map_location="cpu", weights_only=False)
    state = ckpt["model"] if "model" in ckpt else ckpt
    state = {k.replace("module.", ""): v for k, v in state.items()}  # saved under DataParallel
    model.load_state_dict(state, strict=True)
    return model.to(device).eval()


@torch.no_grad()
def _run_window(model, win_norm, device):
    """win_norm: [243, 17, 3] normalized 2D -> [243, 17, 3] root-relative 3D (flip-TTA averaged)."""
    x = torch.from_numpy(win_norm[None].astype("float32")).to(device)
    o1 = model(x).cpu().numpy()
    o2 = model(torch.from_numpy(flip_data(win_norm)[None].astype("float32")).to(device)).cpu().numpy()
    o = (o1 + flip_data(o2)) / 2.0
    o = o[0]
    o[:, 0, :] = 0.0  # demo zeroes the root (pelvis) each frame
    return o


def lift_sequence(model, kp_norm, device):
    """kp_norm: [F, 17, 3] normalized 2D -> out3d [F, 17, 3] (model camera space, root at origin)."""
    F = kp_norm.shape[0]
    out = np.zeros((F, 17, 3), dtype="float32")
    if F <= N_FRAMES:
        idx = resample(F)
        _, ds = np.unique(idx, return_index=True)  # first 243-slot for each original frame
        o = _run_window(model, kp_norm[idx], device)
        out[:] = o[ds]
    else:
        s = 0
        while s < F:
            chunk = kp_norm[s:s + N_FRAMES]
            L = chunk.shape[0]
            if L == N_FRAMES:
                out[s:s + L] = _run_window(model, chunk, device)
            else:
                idx = resample(L)
                _, ds = np.unique(idx, return_index=True)
                o = _run_window(model, chunk[idx], device)
                out[s:s + L] = o[ds]
            s += N_FRAMES
    return out


def to_world(out3d):
    """Apply the demo's fixed camera->world rotation per frame (upright view). Root stays at origin."""
    world = np.empty_like(out3d)
    for k in range(out3d.shape[0]):
        world[k] = camera_to_world(out3d[k], CAM2WORLD_ROT)
    world -= world[:, 0:1, :]  # re-center root at origin
    return world


# --- output ----------------------------------------------------------------------------------
def write_json(out_path, world3d, ts, detected, video_name, interval, source):
    F = world3d.shape[0]
    frames = []
    for i in range(F):
        joints3d = [
            {"index": j,
             "x": round(float(world3d[i, j, 0]), 4),
             "y": round(float(world3d[i, j, 1]), 4),
             "z": round(float(world3d[i, j, 2]), 4)}
            for j in range(17)
        ]
        frames.append({
            "frameIndex": i,
            "timestampMs": int(ts[i]),
            "detected": bool(detected[i]),
            "joints3d": joints3d,
        })
    doc = {
        "schemaVersion": "pose3d-lift-1",
        "topology": "h36m17",
        "model": MODEL_NAME,
        "source": source,
        "videoName": video_name,
        "intervalMs": int(interval),
        "totalFrames": F,
        "axis": "model camera->world (demo rot); root at origin; H36M metric scale; +z up",
        "joints": H36M_JOINTS,
        "bones": H36M_BONES,
        "frames": frames,
    }
    with open(out_path, "w") as fh:
        json.dump(doc, fh, separators=(",", ":"))
    return doc


def main():
    ap = argparse.ArgumentParser(description="Lift 2D pose keypoints to 3D with MotionAGFormer.")
    ap.add_argument("input", help="path to <base>_poses_rtm.json / _poses_vision.json / _poses.json")
    ap.add_argument("-o", "--out", default=None, help="output path (default: <base>_pose3d_lift_<source>.json)")
    ap.add_argument("--source", default=None, choices=["rtm", "vision", "mediapipe"],
                    help="override the pose source (default: inferred from filename)")
    ap.add_argument("--device", default="cpu", choices=["cpu", "mps"],
                    help="cpu (default, most reliable) or mps")
    args = ap.parse_args()

    if not os.path.exists(CHECKPOINT):
        sys.exit(f"checkpoint not found: {CHECKPOINT}\nSee scripts/poses/LIFT.md for the download.")

    source = args.source or detect_source(args.input)
    out_path = args.out or default_out(args.input, source)

    device = torch.device(args.device)
    print(f"[lift] source={source}  input={args.input}")
    kp_px, W, H, interval, ts, detected, video_name = load_2d(args.input, source)
    F = kp_px.shape[0]
    print(f"[lift] {F} frames | {int(detected.sum())} detected | {W}x{H} | interval {interval}ms")

    kp_h36m = np.concatenate([coco_h36m_xy(kp_px[..., :2]), coco_h36m_score(kp_px[..., 2])[..., None]], axis=-1)
    kp_norm = normalize_screen_coordinates(kp_h36m, w=W, h=H)

    print(f"[lift] building MotionAGFormer-B on {device} ...")
    model = build_model(device)

    print("[lift] running temporal lifting ...")
    out3d = lift_sequence(model, kp_norm, device)
    world3d = to_world(out3d)

    doc = write_json(out_path, world3d, ts, detected, video_name, interval, source)

    span = world3d.max(axis=(0, 1)) - world3d.min(axis=(0, 1))
    print(f"[lift] wrote {out_path}")
    print(f"[lift] xyz span: x={span[0]:.3f} y={span[1]:.3f} z={span[2]:.3f} (non-degenerate if all > ~0.1)")
    print(f"[lift] frames={doc['totalFrames']} joints/frame=17 topology={doc['topology']} source={source}")


if __name__ == "__main__":
    main()
