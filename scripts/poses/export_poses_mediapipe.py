#!/usr/bin/env python3
"""
export_poses_mediapipe.py

Runs MediaPipe Tasks' PoseLandmarker (BlazePose-33, IMAGE running mode) on a
video file and exports a *_poses_mediapipe_<model>.json — pose JSON schema
v2, COCO-17. Written as a desktop reference implementation that mirrors the
ON-DEVICE Android backend exactly, so the export reflects true on-device
MediaPipe Lite/Full/Heavy quality:

  - BlazePose-33 -> COCO-17 remap uses the SAME COCO17_TO_BLAZEPOSE33 index
    table as app/src/main/java/com/ttcoachai/pose/MediaPipePoseLandmarkerBackend.kt.
  - score = landmark.visibility (not presence), coerced to [0,1] — same as
    the Kotlin backend.
  - RunningMode.IMAGE (independent per-frame detection, no temporal
    smoothing) — same as the Kotlin backend's estimatePose() contract.

Schema reference: docs/pose_json_schema_v2.md

Usage:
    python scripts/poses/export_poses_mediapipe.py <video_path> [--model lite|full|heavy] [--interval 100] [--out-dir <dir>]

Output:
    <video_name>_poses_mediapipe_<model>.json  written next to the video (or to --out-dir)

Requirements:
    pip install opencv-python mediapipe numpy

Model:
    Reuses app/src/main/assets/pose_landmarker_{lite,full,heavy}.task (default: lite).
"""

import argparse
import json
import os
import sys
import time

try:
    import cv2
except ImportError:
    print("ERROR: opencv-python not installed. Run: pip install opencv-python", file=sys.stderr)
    sys.exit(1)

try:
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions
    from mediapipe.tasks.python.vision import (
        PoseLandmarker,
        PoseLandmarkerOptions,
        RunningMode,
    )
except ImportError:
    print("ERROR: mediapipe not installed. Run: pip install mediapipe", file=sys.stderr)
    sys.exit(1)

SCHEMA_VERSION = 2

REPO_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
MODEL_PATHS = {
    "lite":  os.path.join(REPO_ROOT, "app", "src", "main", "assets", "pose_landmarker_lite.task"),
    "full":  os.path.join(REPO_ROOT, "app", "src", "main", "assets", "pose_landmarker_full.task"),
    "heavy": os.path.join(REPO_ROOT, "app", "src", "main", "assets", "pose_landmarker_heavy.task"),
}

# COCO-17 index -> BlazePose-33 index. Copied verbatim from
# app/src/main/java/com/ttcoachai/pose/MediaPipePoseLandmarkerBackend.kt
# (COCO17_TO_BLAZEPOSE33) so this desktop export matches the on-device
# remap exactly. BlazePose-only points (face-mesh detail, fingers, heels,
# foot index 29-32) have no COCO-17 counterpart and are dropped.
COCO17_TO_BLAZEPOSE33 = [
    0,   # 0  nose            <- BlazePose 0  nose
    2,   # 1  left_eye        <- BlazePose 2  left_eye
    5,   # 2  right_eye       <- BlazePose 5  right_eye
    7,   # 3  left_ear        <- BlazePose 7  left_ear
    8,   # 4  right_ear       <- BlazePose 8  right_ear
    11,  # 5  left_shoulder   <- BlazePose 11 left_shoulder
    12,  # 6  right_shoulder  <- BlazePose 12 right_shoulder
    13,  # 7  left_elbow      <- BlazePose 13 left_elbow
    14,  # 8  right_elbow     <- BlazePose 14 right_elbow
    15,  # 9  left_wrist      <- BlazePose 15 left_wrist
    16,  # 10 right_wrist     <- BlazePose 16 right_wrist
    23,  # 11 left_hip        <- BlazePose 23 left_hip
    24,  # 12 right_hip       <- BlazePose 24 right_hip
    25,  # 13 left_knee       <- BlazePose 25 left_knee
    26,  # 14 right_knee      <- BlazePose 26 right_knee
    27,  # 15 left_ankle      <- BlazePose 27 left_ankle
    28,  # 16 right_ankle     <- BlazePose 28 right_ankle
]


def export_poses(video_path: str, model_key: str, interval_ms: int, out_dir: str | None,
                  model_path: str) -> str:
    topology, model_name, num_keypoints = "coco17", f"mediapipe-{model_key}", 17

    video_name = os.path.basename(video_path)
    base = video_name.rsplit(".", 1)[0]

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"ERROR: cannot open video: {video_path}", file=sys.stderr)
        sys.exit(1)

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frame_count = cap.get(cv2.CAP_PROP_FRAME_COUNT)
    duration_ms = int(frame_count / fps * 1000)
    # Take dimensions from the DECODED frame, not header props (rotation
    # metadata can swap them) — same rationale as export_poses_rtmpose.py L-08.
    ok, probe = cap.read()
    if not ok:
        print(f"ERROR: cannot decode first frame: {video_path}", file=sys.stderr)
        sys.exit(1)
    height, width = probe.shape[:2]
    header_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    header_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    if (width, height) != (header_w, header_h):
        print(f"NOTE: rotation metadata applied: header {header_w}x{header_h} -> decoded {width}x{height}")
    cap.set(cv2.CAP_PROP_POS_MSEC, 0)  # rewind after the probe read

    print(f"Video: {video_name}  {width}x{height}  {duration_ms} ms  ({fps:.1f} fps)  model={model_name}")

    if not os.path.isfile(model_path):
        print(f"ERROR: model file not found: {model_path}", file=sys.stderr)
        sys.exit(1)

    options = PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=model_path),
        running_mode=RunningMode.IMAGE,
    )
    landmarker = PoseLandmarker.create_from_options(options)

    frames = []
    frame_index = 0
    pos_ms = 0
    sanity_printed = False

    while pos_ms <= duration_ms:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if not ret:
            break

        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        result = landmarker.detect(mp_image)

        landmarks = []
        if result.pose_landmarks:
            blazepose33 = result.pose_landmarks[0]
            if len(blazepose33) >= 29:
                for i, blaze_index in enumerate(COCO17_TO_BLAZEPOSE33):
                    lm = blazepose33[blaze_index]
                    x = min(1.0, max(0.0, lm.x))
                    y = min(1.0, max(0.0, lm.y))
                    score = min(1.0, max(0.0, lm.visibility if lm.visibility is not None else 0.0))
                    landmarks.append({
                        "index": i,
                        "x": round(x, 4),
                        "y": round(y, 4),
                        "score": round(score, 4),
                    })

        if not sanity_printed and any(lm["score"] > 0.3 for lm in landmarks):
            by_index = {lm["index"]: lm for lm in landmarks}
            print(f"  SANITY frame {frame_index} t={pos_ms}ms:")
            for idx, label in ((0, "nose"), (5, "l_shoulder"), (6, "r_shoulder"), (11, "l_hip"), (12, "r_hip")):
                lm = by_index.get(idx)
                if lm:
                    print(f"    {label:11s} x={lm['x']:.3f} y={lm['y']:.3f} score={lm['score']:.3f}")
            sanity_printed = True

        frames.append({
            "frameIndex": frame_index,
            "timestampMs": pos_ms,
            "landmarks": landmarks,
        })

        if frame_index % 10 == 0:
            print(f"  frame {frame_index:3d}  t={pos_ms:6d} ms  landmarks={len(landmarks)}")

        frame_index += 1
        pos_ms += interval_ms

    cap.release()
    landmarker.close()

    data = {
        "schemaVersion":   SCHEMA_VERSION,
        "topology":        topology,
        "model":           model_name,
        "videoName":       video_name,
        "intervalMs":      interval_ms,
        "totalFrames":     frame_index,
        "videoDurationMs": duration_ms,
        "videoWidth":      width,
        "videoHeight":     height,
        "exportTimestamp": int(time.time() * 1000),
        "frames":          frames,
    }

    dest_dir = out_dir if out_dir else os.path.dirname(os.path.abspath(video_path))
    os.makedirs(dest_dir, exist_ok=True)
    out_path = os.path.join(dest_dir, base + f"_poses_mediapipe_{model_key}.json")

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, allow_nan=False)

    n_detected = sum(1 for fr in frames if fr["landmarks"])
    print(f"\n-> {out_path}")
    print(f"  {frame_index} frames, {n_detected} with pose detected")
    return out_path


def main():
    parser = argparse.ArgumentParser(
        description="Export MediaPipe PoseLandmarker (BlazePose-33 -> COCO-17) detections from a video (schema v2), matching the on-device backend's remap exactly.")
    parser.add_argument("video", help="Path to the input video file")
    parser.add_argument("--model", choices=["lite", "full", "heavy"], default="lite",
                        help="Which pose_landmarker_<model>.task to use (default: lite)")
    parser.add_argument("--interval", type=int, default=100,
                        help="Sampling interval in milliseconds (default: 100)")
    parser.add_argument("--out-dir", default=None,
                        help="Output directory (default: same folder as video)")
    args = parser.parse_args()

    if not os.path.isfile(args.video):
        print(f"ERROR: file not found: {args.video}", file=sys.stderr)
        sys.exit(1)

    model_path = MODEL_PATHS[args.model]
    export_poses(args.video, args.model, args.interval, args.out_dir, model_path)


if __name__ == "__main__":
    main()
