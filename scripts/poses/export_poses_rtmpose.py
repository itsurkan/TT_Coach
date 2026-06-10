#!/usr/bin/env python3
"""
export_poses_rtmpose.py

Runs RTMPose-m (via rtmlib + ONNX Runtime) on a video file and exports a
*_poses_rtm.json with COCO-17 keypoints — pose JSON schema v2.
Schema reference: docs/pose_json_schema_v2.md

Usage:
    python scripts/poses/export_poses_rtmpose.py <video_path> [--interval 100] [--out-dir <dir>]

Output:
    <video_name>_poses_rtm.json  written next to the video (or to --out-dir)

Requirements:
    pip install rtmlib onnxruntime opencv-python
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
    import numpy as np
    from rtmlib import Body
except ImportError:
    print("ERROR: rtmlib not installed. Run: pip install rtmlib onnxruntime", file=sys.stderr)
    sys.exit(1)

SCHEMA_VERSION = 2
TOPOLOGY = "coco17"
MODEL_NAME = "rtmpose-m"
NUM_KEYPOINTS = 17


def best_person(keypoints, scores):
    """Pick the detection with the highest mean keypoint score (single-player videos)."""
    if keypoints is None or len(keypoints) == 0:
        return None, None
    idx = int(np.argmax(scores.mean(axis=1)))
    return keypoints[idx], scores[idx]


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

    body = Body(mode="balanced", backend="onnxruntime", device="cpu")

    frames = []
    frame_index = 0
    pos_ms = 0

    while pos_ms <= duration_ms:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if not ret:
            break

        keypoints, scores = body(frame)  # rtmlib takes BGR (cv2) frames; returns pixel coords
        person_kpts, person_scores = best_person(keypoints, scores)

        landmarks = []
        if person_kpts is not None:
            for i in range(NUM_KEYPOINTS):
                landmarks.append({
                    "index": i,
                    "x": round(float(person_kpts[i][0]) / width, 4),
                    "y": round(float(person_kpts[i][1]) / height, 4),
                    "score": round(float(person_scores[i]), 4),
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

    data = {
        "schemaVersion":   SCHEMA_VERSION,
        "topology":        TOPOLOGY,
        "model":           MODEL_NAME,
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
    out_path = os.path.join(dest_dir, base + "_poses_rtm.json")

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    n_detected = sum(1 for fr in frames if fr["landmarks"])
    print(f"\n-> {out_path}")
    print(f"  {frame_index} frames, {n_detected} with pose detected")
    return out_path


def main():
    parser = argparse.ArgumentParser(description="Export RTMPose COCO-17 detections from a video (schema v2).")
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
