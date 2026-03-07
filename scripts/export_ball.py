#!/usr/bin/env python3
"""
export_ball.py

Runs ball detection on a video file using Python OpenCV (no Android device needed).

Usage:
    python scripts/export_ball.py <video_path> [--interval 100] [--out-dir <dir>]
    python scripts/export_ball.py <video_path> --color orange

Examples:
    python scripts/export_ball.py app/src/main/assets/Videos/IMG_6370/IMG_6370.MP4
    python scripts/export_ball.py my_video.mp4 --interval 50

Output:
    <video_name>_ball.json  written next to the video (or to --out-dir)

Requirements:
    pip install opencv-python numpy
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

from ball_detector import BallDetector


# ── Video processing ──────────────────────────────────────────────────────────

def export_ball(video_path: str, interval_ms: int, out_dir: str | None, color: str) -> str:
    video_name = os.path.basename(video_path)
    base = video_name.rsplit(".", 1)[0]

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"ERROR: cannot open video: {video_path}", file=sys.stderr)
        sys.exit(1)

    fps         = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frame_count = cap.get(cv2.CAP_PROP_FRAME_COUNT)
    duration_ms = int(frame_count / fps * 1000)
    width       = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height      = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    print(f"Video: {video_name}  {width}x{height}  {duration_ms} ms  ({fps:.1f} fps)")

    detector = BallDetector(color=color)
    frames   = []
    frame_index = 0
    pos_ms      = 0

    total_frames = (duration_ms // interval_ms) + 1
    t0 = time.time()

    while pos_ms <= duration_ms:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if not ret:
            break

        result = detector.detect(frame, frame_index, pos_ms)
        frames.append(result)

        if frame_index % 10 == 0:
            elapsed = time.time() - t0
            frac    = (frame_index + 1) / total_frames if total_frames > 0 else 0
            eta     = (elapsed / frac - elapsed) if frac > 0 else 0
            status  = "DETECTED" if result["ball"] else "---"
            print(f"  frame {frame_index:3d}/{total_frames}  t={pos_ms:6d} ms  "
                  f"{status:8s}  [{elapsed:.0f}s  ETA {eta:.0f}s]")

        frame_index += 1
        pos_ms += interval_ms

    cap.release()

    n_detected = sum(1 for f in frames if f["ball"] is not None)

    data = {
        "videoName":       video_name,
        "intervalMs":      interval_ms,
        "totalFrames":     frame_index,
        "videoDurationMs": duration_ms,
        "videoWidth":      width,
        "videoHeight":     height,
        "exportTimestamp":  int(time.time() * 1000),
        "frames":          frames,
    }

    dest_dir = out_dir if out_dir else os.path.dirname(os.path.abspath(video_path))
    out_path = os.path.join(dest_dir, base + "_ball.json")

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    print(f"\n-> {out_path}")
    print(f"  {frame_index} frames, {n_detected} with ball detected")
    return out_path


# ── CLI ───────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Export ball detections from a video (local OpenCV).")
    parser.add_argument("video", help="Path to the input video file")
    parser.add_argument("--interval", type=int, default=100,
                        help="Sampling interval in milliseconds (default: 100)")
    parser.add_argument("--out-dir", default=None,
                        help="Output directory (default: same folder as video)")
    parser.add_argument("--color", choices=["white", "orange"], default="white",
                        help="Ball color to detect (default: white)")
    args = parser.parse_args()

    if not os.path.isfile(args.video):
        print(f"ERROR: file not found: {args.video}", file=sys.stderr)
        sys.exit(1)

    export_ball(args.video, args.interval, args.out_dir, args.color)


if __name__ == "__main__":
    main()
