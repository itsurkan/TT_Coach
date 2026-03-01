#!/usr/bin/env python3
"""
export_poses.py

Runs MediaPipe Pose detection on a video file and exports a *_poses.json
in the same format the Android app produces.

Usage:
    python scripts/export_poses.py <video_path> [--interval 100] [--out-dir <dir>]

Examples:
    python scripts/export_poses.py app/src/main/assets/Videos/video_2026-03-01_13-59-49.mp4
    python scripts/export_poses.py my_video.mp4 --interval 50

Output:
    <video_name>_poses.json  written next to the video (or to --out-dir)

Requirements:
    pip install mediapipe opencv-python
"""

import argparse
import json
import os
import sys
import time
import urllib.request

try:
    import cv2
except ImportError:
    print("ERROR: opencv-python not installed. Run: pip install opencv-python", file=sys.stderr)
    sys.exit(1)

try:
    import mediapipe as mp
    from mediapipe.tasks.python import vision as mp_vision
    from mediapipe.tasks.python.core import base_options as mp_base
except ImportError:
    print("ERROR: mediapipe not installed. Run: pip install mediapipe", file=sys.stderr)
    sys.exit(1)

MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/"
    "pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task"
)
MODEL_PATH = os.path.join(os.path.dirname(__file__), "pose_landmarker_full.task")


def ensure_model() -> str:
    if os.path.isfile(MODEL_PATH):
        return MODEL_PATH
    print(f"Downloading pose landmarker model -> {MODEL_PATH}")
    urllib.request.urlretrieve(MODEL_URL, MODEL_PATH)
    print("  Done.")
    return MODEL_PATH


def export_poses(video_path: str, interval_ms: int, out_dir: str | None) -> str:
    video_name = os.path.basename(video_path)
    base = video_name.rsplit(".", 1)[0]

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"ERROR: cannot open video: {video_path}", file=sys.stderr)
        sys.exit(1)

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frame_count = cap.get(cv2.CAP_PROP_FRAME_COUNT)
    duration_ms = int(frame_count / fps * 1000)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    print(f"Video: {video_name}  {width}x{height}  {duration_ms} ms  ({fps:.1f} fps)")

    model_path = ensure_model()
    options = mp_vision.PoseLandmarkerOptions(
        base_options=mp_base.BaseOptions(model_asset_path=model_path),
        running_mode=mp_vision.RunningMode.IMAGE,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    landmarker = mp_vision.PoseLandmarker.create_from_options(options)

    frames = []
    frame_index = 0
    pos_ms = 0

    while pos_ms <= duration_ms:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if not ret:
            break

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
        result = landmarker.detect(mp_image)

        landmarks = []
        if result.pose_landmarks:
            for i, lm in enumerate(result.pose_landmarks[0]):
                landmarks.append({
                    "index": i,
                    "x": round(float(lm.x), 8),
                    "y": round(float(lm.y), 8),
                    "z": round(float(lm.z), 8),
                    "visibility": round(float(lm.visibility), 8),
                    "presence": round(float(lm.presence), 8),
                })

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
    out_path = os.path.join(dest_dir, base + "_poses.json")

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    n_detected = sum(1 for fr in frames if fr["landmarks"])
    print(f"\n-> {out_path}")
    print(f"  {frame_index} frames, {n_detected} with pose detected")
    return out_path


def main():
    parser = argparse.ArgumentParser(description="Export MediaPipe pose detections from a video.")
    parser.add_argument("video", help="Path to the input video file")
    parser.add_argument("--interval", type=int, default=100,
                        help="Sampling interval in milliseconds (default: 100)")
    parser.add_argument("--out-dir", default=None,
                        help="Output directory (default: same folder as video)")
    args = parser.parse_args()

    if not os.path.isfile(args.video):
        print(f"ERROR: file not found: {args.video}", file=sys.stderr)
        sys.exit(1)

    export_poses(args.video, args.interval, args.out_dir)


if __name__ == "__main__":
    main()
