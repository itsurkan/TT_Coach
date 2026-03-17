#!/usr/bin/env python3
"""
extract_crops.py

Extracts training crops from labeled videos for ball detection fine-tuning.

For each labeled frame:
  - ball position known → 64x64 crop centered on ball → data/ball/
  - no_ball → random 64x64 crops → data/not_ball/

Also generates augmented copies of positive samples (flip, brightness, contrast).

Usage:
    python scripts/extract_crops.py [--crop-size 64] [--augment 3] [--split 0.8]

Output:
    data/
      train/
        ball/         ~900 crops
        not_ball/     ~600 crops
      val/
        ball/         ~225 crops
        not_ball/     ~150 crops

Requirements:
    pip install opencv-python numpy
"""

import argparse
import json
import os
import random
import sys

import cv2
import numpy as np

SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.normpath(os.path.join(SCRIPTS_DIR, ".."))
VIDEOS_DIR = os.path.join(PROJECT_DIR, "app", "src", "main", "assets", "Videos")
DATA_DIR = os.path.join(PROJECT_DIR, "data")


def read_frame(cap: cv2.VideoCapture, timestamp_ms: int) -> np.ndarray | None:
    """Seek to timestamp and read a frame."""
    cap.set(cv2.CAP_PROP_POS_MSEC, timestamp_ms)
    ret, frame = cap.read()
    return frame if ret else None


def crop_centered(frame: np.ndarray, cx: float, cy: float, size: int) -> np.ndarray | None:
    """Extract a square crop centered on normalized (cx, cy). Returns None if out of bounds."""
    h, w = frame.shape[:2]
    px = int(cx * w)
    py = int(cy * h)
    half = size // 2

    # Clamp to frame bounds
    x1 = max(0, px - half)
    y1 = max(0, py - half)
    x2 = min(w, x1 + size)
    y2 = min(h, y1 + size)

    # Ensure full crop size
    if x2 - x1 < size:
        x1 = max(0, x2 - size)
    if y2 - y1 < size:
        y1 = max(0, y2 - size)

    crop = frame[y1:y2, x1:x2]
    if crop.shape[0] < size or crop.shape[1] < size:
        return None
    return crop


def random_crops(frame: np.ndarray, size: int, n: int) -> list[np.ndarray]:
    """Extract n random crops from a frame."""
    h, w = frame.shape[:2]
    if h < size or w < size:
        return []
    crops = []
    for _ in range(n):
        x = random.randint(0, w - size)
        y = random.randint(0, h - size)
        crops.append(frame[y:y + size, x:x + size])
    return crops


def augment(crop: np.ndarray) -> list[np.ndarray]:
    """Generate augmented versions of a crop."""
    results = []

    # Horizontal flip
    results.append(cv2.flip(crop, 1))

    # Brightness +20%
    bright = cv2.convertScaleAbs(crop, alpha=1.2, beta=10)
    results.append(bright)

    # Brightness -20%
    dark = cv2.convertScaleAbs(crop, alpha=0.8, beta=-10)
    results.append(dark)

    return results


def process_video(
    video_name: str,
    crop_size: int,
    neg_per_frame: int,
) -> tuple[list[np.ndarray], list[np.ndarray]]:
    """Process one video, return (positive_crops, negative_crops)."""
    video_dir = os.path.join(VIDEOS_DIR, video_name)
    labels_path = os.path.join(video_dir, f"{video_name}_labels.json")

    if not os.path.isfile(labels_path):
        return [], []

    with open(labels_path, encoding="utf-8") as f:
        labels_data = json.load(f)

    labels = labels_data.get("labels", {})
    if not labels:
        return [], []

    # Find video file
    video_file = None
    for ext in [".mp4", ".MP4", ".mov", ".MOV", ".webm"]:
        candidate = os.path.join(video_dir, video_name + ext)
        if os.path.isfile(candidate):
            video_file = candidate
            break

    if not video_file:
        print(f"  WARNING: no video file found for {video_name}")
        return [], []

    cap = cv2.VideoCapture(video_file)
    if not cap.isOpened():
        print(f"  WARNING: cannot open {video_file}")
        return [], []

    interval_ms = labels_data.get("intervalMs", 100)
    # Also try to get from poses json
    poses_path = os.path.join(video_dir, f"{video_name}_poses.json")
    if os.path.isfile(poses_path):
        with open(poses_path, encoding="utf-8") as f:
            poses_data = json.load(f)
        interval_ms = poses_data.get("intervalMs", interval_ms)

    positives = []
    negatives = []

    for key, label in labels.items():
        fi = label["frameIndex"]
        timestamp_ms = fi * interval_ms

        frame = read_frame(cap, timestamp_ms)
        if frame is None:
            continue

        if label.get("correctedX") is not None and label.get("correctedY") is not None:
            # Positive: crop centered on ball
            crop = crop_centered(frame, label["correctedX"], label["correctedY"], crop_size)
            if crop is not None:
                positives.append(crop)
        elif label["label"] == "no_ball":
            # Negative: random crops
            crops = random_crops(frame, crop_size, neg_per_frame)
            negatives.extend(crops)

    cap.release()
    return positives, negatives


def main():
    parser = argparse.ArgumentParser(description="Extract training crops from labeled videos")
    parser.add_argument("--crop-size", type=int, default=64, help="Crop size in pixels (default: 64)")
    parser.add_argument("--augment", type=int, default=3, help="Augmented copies per positive (default: 3)")
    parser.add_argument("--split", type=float, default=0.8, help="Train/val split ratio (default: 0.8)")
    parser.add_argument("--neg-per-frame", type=int, default=2, help="Random negative crops per no_ball frame (default: 2)")
    args = parser.parse_args()

    all_positives = []
    all_negatives = []

    # Process all videos
    for entry in sorted(os.listdir(VIDEOS_DIR)):
        video_dir = os.path.join(VIDEOS_DIR, entry)
        if not os.path.isdir(video_dir):
            continue
        labels_path = os.path.join(video_dir, f"{entry}_labels.json")
        if not os.path.isfile(labels_path):
            continue

        print(f"Processing {entry}...")
        pos, neg = process_video(entry, args.crop_size, args.neg_per_frame)
        print(f"  positives: {len(pos)}, negatives: {len(neg)}")
        all_positives.extend(pos)
        all_negatives.extend(neg)

    print(f"\nRaw totals: {len(all_positives)} positives, {len(all_negatives)} negatives")

    # Augment positives
    if args.augment > 0:
        augmented = []
        for crop in all_positives:
            augmented.extend(augment(crop)[:args.augment])
        all_positives.extend(augmented)
        print(f"After augmentation: {len(all_positives)} positives")

    # Balance: cap negatives to match positives
    if len(all_negatives) > len(all_positives):
        random.shuffle(all_negatives)
        all_negatives = all_negatives[:len(all_positives)]
        print(f"Balanced negatives to: {len(all_negatives)}")

    # Shuffle and split
    random.shuffle(all_positives)
    random.shuffle(all_negatives)

    split_pos = int(len(all_positives) * args.split)
    split_neg = int(len(all_negatives) * args.split)

    splits = {
        "train": {
            "ball": all_positives[:split_pos],
            "not_ball": all_negatives[:split_neg],
        },
        "val": {
            "ball": all_positives[split_pos:],
            "not_ball": all_negatives[split_neg:],
        },
    }

    # Write crops to disk
    for split_name, classes in splits.items():
        for class_name, crops in classes.items():
            out_dir = os.path.join(DATA_DIR, split_name, class_name)
            os.makedirs(out_dir, exist_ok=True)
            for i, crop in enumerate(crops):
                path = os.path.join(out_dir, f"{i:05d}.png")
                cv2.imwrite(path, crop)

    # Summary
    print(f"\nDataset written to: {DATA_DIR}")
    for split_name, classes in splits.items():
        for class_name, crops in classes.items():
            print(f"  {split_name}/{class_name}: {len(crops)}")


if __name__ == "__main__":
    main()
