#!/usr/bin/env python3
"""
merge_labels_with_ball.py

Merges ball detection positions into labels JSON.
For "correct" labels, copies (x, y) from *_ball.json into correctedX/correctedY.
This makes labels self-contained for training data extraction.

Usage:
    python scripts/merge_labels_with_ball.py <video_dir>
    python scripts/merge_labels_with_ball.py app/src/main/assets/Videos/IMG_6370

Processes all video dirs if given the Videos root:
    python scripts/merge_labels_with_ball.py app/src/main/assets/Videos
"""

import json
import os
import sys


def merge_one(video_dir: str) -> bool:
    base = os.path.basename(video_dir)
    labels_path = os.path.join(video_dir, f"{base}_labels.json")
    ball_path = os.path.join(video_dir, f"{base}_ball.json")

    if not os.path.isfile(labels_path):
        return False

    with open(labels_path, encoding="utf-8") as f:
        labels_data = json.load(f)

    # Build frame -> ball position lookup
    ball_by_frame = {}
    if os.path.isfile(ball_path):
        with open(ball_path, encoding="utf-8") as f:
            ball_data = json.load(f)
        for frame in ball_data.get("frames", []):
            fi = frame.get("frameIndex", frame.get("frame_index"))
            ball = frame.get("ball")
            if ball and ball.get("x") is not None:
                ball_by_frame[fi] = ball

    updated = 0
    warnings = 0
    for label in labels_data["labels"].values():
        if label["label"] == "correct":
            fi = label["frameIndex"]
            ball = ball_by_frame.get(fi)
            if ball:
                label["correctedX"] = ball["x"]
                label["correctedY"] = ball["y"]
                updated += 1
            else:
                print(f"  WARNING: {base} frame {fi} labeled correct but no ball detection")
                warnings += 1

    total = len(labels_data["labels"])
    has_pos = sum(1 for l in labels_data["labels"].values() if "correctedX" in l)

    with open(labels_path, "w", encoding="utf-8") as f:
        json.dump(labels_data, f, indent=2)

    print(f"  {base}: {updated} correct labels updated, {has_pos}/{total} have positions"
          + (f", {warnings} warnings" if warnings else ""))
    return True


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <video_dir_or_videos_root>")
        sys.exit(1)

    path = os.path.abspath(sys.argv[1])

    # Check if this is a single video dir or the Videos root
    base = os.path.basename(path)
    if os.path.isfile(os.path.join(path, f"{base}_labels.json")):
        # Single video dir
        merge_one(path)
    else:
        # Process all subdirs that have labels
        found = False
        for entry in sorted(os.listdir(path)):
            subdir = os.path.join(path, entry)
            if os.path.isdir(subdir):
                if merge_one(subdir):
                    found = True
        if not found:
            print("No *_labels.json files found.")


if __name__ == "__main__":
    main()
