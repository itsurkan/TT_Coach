#!/usr/bin/env python3
"""
merge_poses_ball.py

Merges *_poses.json + *_ball.json -> *_poses_ball.json for videos in
app/src/main/assets/Videos/<base>/ subfolders.

Usage:
    python scripts/merge_poses_ball.py              # merge all videos
    python scripts/merge_poses_ball.py --video base # merge one video
"""

import argparse
import json
import os
import sys
import time

VIDEOS_DIR = os.path.normpath(os.path.join(
    os.path.dirname(__file__),
    "..", "app", "src", "main", "assets", "Videos"
))


def find_pairs(videos_dir: str, only_base: str | None) -> list[tuple[str, str]]:
    """Return [(poses_path, ball_path), ...] for every matched pair in subdirs."""
    pairs = []
    try:
        entries = sorted(os.scandir(videos_dir), key=lambda e: e.name)
    except OSError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)

    for entry in entries:
        if not entry.is_dir():
            continue
        base = entry.name
        if only_base and base != only_base:
            continue

        subdir = entry.path
        files = set(os.listdir(subdir))
        poses_file = f"{base}_poses.json"
        ball_file  = f"{base}_ball.json"

        if poses_file not in files and ball_file not in files:
            continue
        if poses_file not in files:
            print(f"  WARNING: no poses file in {base}/, skipping")
            continue
        if ball_file not in files:
            print(f"  WARNING: no ball file in {base}/, skipping")
            continue

        pairs.append((
            os.path.join(subdir, poses_file),
            os.path.join(subdir, ball_file),
        ))

    return pairs


def merge(poses_path: str, ball_path: str) -> dict:
    with open(poses_path, encoding="utf-8") as f:
        poses = json.load(f)
    with open(ball_path, encoding="utf-8") as f:
        ball = json.load(f)

    ball_by_ts: dict[int, dict | None] = {}
    for frame in ball.get("frames", []):
        ts = frame["timestampMs"]
        ball_by_ts[ts] = frame.get("ball")

    merged_frames = []
    for pose_frame in poses.get("frames", []):
        ts = pose_frame["timestampMs"]
        merged_frames.append({
            "frameIndex":  pose_frame["frameIndex"],
            "timestampMs": ts,
            "landmarks":   pose_frame.get("landmarks", []),
            "ball":        ball_by_ts.get(ts),
        })

    return {
        "videoUri":        poses.get("videoUri"),
        "videoName":       ball.get("videoName"),
        "intervalMs":      poses.get("intervalMs", ball.get("intervalMs")),
        "totalFrames":     poses.get("totalFrames", ball.get("totalFrames")),
        "videoDurationMs": ball.get("videoDurationMs"),
        "videoWidth":      ball.get("videoWidth"),
        "videoHeight":     ball.get("videoHeight"),
        "exportTimestamp": int(time.time() * 1000),
        "frames":          merged_frames,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--video", default=None,
        help="Base name of one video to merge (e.g. forehand_drive). Omit to merge all.")
    args = parser.parse_args()

    pairs = find_pairs(VIDEOS_DIR, args.video)
    if not pairs:
        print("No *_poses.json + *_ball.json pairs found.")
        return

    for poses_path, ball_path in pairs:
        subdir = os.path.dirname(poses_path)
        base   = os.path.basename(poses_path)[: -len("_poses.json")]
        out_path = os.path.join(subdir, base + "_poses_ball.json")

        print(f"Merging {base}...")
        merged = merge(poses_path, ball_path)

        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(merged, f, indent=2)

        n_frames   = len(merged["frames"])
        n_detected = sum(1 for fr in merged["frames"] if fr["ball"] is not None)
        print(f"  -> {out_path}")
        print(f"     {n_frames} frames, {n_detected} with ball detected")

    print("\nDone.")


if __name__ == "__main__":
    main()
