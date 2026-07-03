#!/usr/bin/env python3
"""
export_poses_rtmpose.py

Runs RTMPose-m (via rtmlib + ONNX Runtime) on a video file and exports a
*_poses_rtm.json — pose JSON schema v2. Default topology is COCO-17;
--feet switches to Halpe26 (COCO-17 + head/neck/hip-mid + 6 foot keypoints).
Schema reference: docs/pose_json_schema_v2.md

Usage:
    python scripts/poses/export_poses_rtmpose.py <video_path> [--interval 100] [--out-dir <dir>] [--feet]

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
    from rtmlib import Body, BodyWithFeet
except ImportError:
    print("ERROR: rtmlib not installed. Run: pip install rtmlib onnxruntime", file=sys.stderr)
    sys.exit(1)

SCHEMA_VERSION = 2


def best_person(keypoints, scores):
    """Pick the detection with the highest mean keypoint score (single-player videos)."""
    if keypoints is None or len(keypoints) == 0:
        return None, None
    idx = int(np.argmax(scores.mean(axis=1)))
    return keypoints[idx], scores[idx]


def export_poses(video_path: str, interval_ms: int, out_dir: str | None, with_feet: bool = False) -> str:
    if with_feet:
        topology, model_name, num_keypoints = "halpe26", "rtmpose-m-halpe26", 26
    else:
        topology, model_name, num_keypoints = "coco17", "rtmpose-m", 17

    video_name = os.path.basename(video_path)
    base = video_name.rsplit(".", 1)[0]

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"ERROR: cannot open video: {video_path}", file=sys.stderr)
        sys.exit(1)

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frame_count = cap.get(cv2.CAP_PROP_FRAME_COUNT)
    duration_ms = int(frame_count / fps * 1000)
    # L-08: take dimensions from the DECODED frame, not header props — rotation
    # metadata (portrait phone video) can swap them, which would invert the
    # aspect-ratio correction all downstream angle math depends on.
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

    print(f"Video: {video_name}  {width}x{height}  {duration_ms} ms  ({fps:.1f} fps)")

    model_cls = BodyWithFeet if with_feet else Body
    body = model_cls(mode="balanced", backend="onnxruntime", device="cpu")

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
            for i in range(num_keypoints):
                landmarks.append({
                    "index": i,
                    "x": round(min(1.0, max(0.0, float(person_kpts[i][0]) / width)), 4),
                    "y": round(min(1.0, max(0.0, float(person_kpts[i][1]) / height)), 4),
                    "score": round(min(1.0, max(0.0, float(person_scores[i]))), 4),
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
    out_path = os.path.join(dest_dir, base + "_poses_rtm.json")

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, allow_nan=False)

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
    parser.add_argument("--feet", action="store_true",
                        help="Use the Halpe26 model: COCO-17 plus 6 foot keypoints (heels + toes)")
    args = parser.parse_args()

    if not os.path.isfile(args.video):
        print(f"ERROR: file not found: {args.video}", file=sys.stderr)
        sys.exit(1)

    export_poses(args.video, args.interval, args.out_dir, args.feet)


if __name__ == "__main__":
    main()
