#!/usr/bin/env python3
"""
extract_frames_for_training.py

Extracts full frames (resized to 320x320) with position labels
for training a ball position regressor.

For each labeled frame:
  - ball position known → frame + (x, y, 1.0)
  - no_ball → frame + (0, 0, 0.0)

Usage:
    python scripts/extract_frames_for_training.py [--size 320] [--split 0.8]

Output:
    data_regressor/
      train/
        images/    *.png (320x320)
        labels.csv  (filename, x, y, conf)
      val/
        images/
        labels.csv
"""

import argparse
import csv
import json
import os
import random
import sys

import cv2
import numpy as np

SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.normpath(os.path.join(SCRIPTS_DIR, ".."))
VIDEOS_DIR = os.path.join(PROJECT_DIR, "app", "src", "main", "assets", "Videos")
DATA_DIR = os.path.join(PROJECT_DIR, "data_regressor")


def process_video(
    video_name: str,
    img_size: int,
) -> list[tuple[np.ndarray, float, float, float]]:
    """Returns list of (frame_resized, x, y, conf)."""
    video_dir = os.path.join(VIDEOS_DIR, video_name)
    labels_path = os.path.join(video_dir, f"{video_name}_labels.json")

    if not os.path.isfile(labels_path):
        return []

    with open(labels_path, encoding="utf-8") as f:
        labels_data = json.load(f)

    labels = labels_data.get("labels", {})
    if not labels:
        return []

    # Find video file
    video_file = None
    for ext in [".mp4", ".MP4", ".mov", ".MOV", ".webm"]:
        candidate = os.path.join(video_dir, video_name + ext)
        if os.path.isfile(candidate):
            video_file = candidate
            break

    if not video_file:
        print(f"  WARNING: no video file found for {video_name}")
        return []

    cap = cv2.VideoCapture(video_file)
    if not cap.isOpened():
        print(f"  WARNING: cannot open {video_file}")
        return []

    # Get interval
    interval_ms = 100
    poses_path = os.path.join(video_dir, f"{video_name}_poses.json")
    if os.path.isfile(poses_path):
        with open(poses_path, encoding="utf-8") as f:
            poses_data = json.load(f)
        interval_ms = poses_data.get("intervalMs", interval_ms)

    results = []

    for key, label in labels.items():
        fi = label["frameIndex"]
        timestamp_ms = fi * interval_ms

        cap.set(cv2.CAP_PROP_POS_MSEC, timestamp_ms)
        ret, frame = cap.read()
        if not ret or frame is None:
            continue

        # Resize to square
        resized = cv2.resize(frame, (img_size, img_size))

        if label.get("correctedX") is not None and label.get("correctedY") is not None:
            # Ball visible — position is already normalized (0-1)
            x = float(label["correctedX"])
            y = float(label["correctedY"])
            results.append((resized, x, y, 1.0))
        elif label["label"] == "no_ball":
            results.append((resized, 0.0, 0.0, 0.0))

    cap.release()
    return results


def augment_frame(
    img: np.ndarray, x: float, y: float, conf: float,
) -> list[tuple[np.ndarray, float, float, float]]:
    """Generate augmented versions. Flips adjust x coordinate."""
    results = []

    # Horizontal flip
    flipped = cv2.flip(img, 1)
    fx = 1.0 - x if conf > 0 else 0.0
    results.append((flipped, fx, y, conf))

    # Brightness +15%
    bright = cv2.convertScaleAbs(img, alpha=1.15, beta=10)
    results.append((bright, x, y, conf))

    # Brightness -15%
    dark = cv2.convertScaleAbs(img, alpha=0.85, beta=-10)
    results.append((dark, x, y, conf))

    return results


def main():
    parser = argparse.ArgumentParser(description="Extract training frames for ball regressor")
    parser.add_argument("--size", type=int, default=320, help="Image size (default: 320)")
    parser.add_argument("--split", type=float, default=0.8, help="Train/val split (default: 0.8)")
    parser.add_argument("--augment", action="store_true", default=True, help="Enable augmentation")
    parser.add_argument("--no-augment", action="store_true", help="Disable augmentation")
    args = parser.parse_args()

    do_augment = args.augment and not args.no_augment

    all_samples: list[tuple[np.ndarray, float, float, float]] = []

    for entry in sorted(os.listdir(VIDEOS_DIR)):
        video_dir = os.path.join(VIDEOS_DIR, entry)
        if not os.path.isdir(video_dir):
            continue
        labels_path = os.path.join(video_dir, f"{entry}_labels.json")
        if not os.path.isfile(labels_path):
            continue

        print(f"Processing {entry}...")
        samples = process_video(entry, args.size)
        pos = sum(1 for s in samples if s[3] > 0)
        neg = len(samples) - pos
        print(f"  frames: {len(samples)} (ball={pos}, no_ball={neg})")
        all_samples.extend(samples)

    print(f"\nRaw total: {len(all_samples)}")
    pos_count = sum(1 for s in all_samples if s[3] > 0)
    neg_count = len(all_samples) - pos_count
    print(f"  ball={pos_count}, no_ball={neg_count}")

    # Augment
    if do_augment:
        augmented = []
        for img, x, y, conf in all_samples:
            augmented.extend(augment_frame(img, x, y, conf))
        all_samples.extend(augmented)
        print(f"After augmentation: {len(all_samples)}")

    # Shuffle and split
    random.shuffle(all_samples)
    split_idx = int(len(all_samples) * args.split)

    splits = {
        "train": all_samples[:split_idx],
        "val": all_samples[split_idx:],
    }

    # Write to disk
    for split_name, samples in splits.items():
        img_dir = os.path.join(DATA_DIR, split_name, "images")
        os.makedirs(img_dir, exist_ok=True)

        csv_path = os.path.join(DATA_DIR, split_name, "labels.csv")
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["filename", "x", "y", "confidence"])
            for i, (img, x, y, conf) in enumerate(samples):
                fname = f"{i:05d}.png"
                cv2.imwrite(os.path.join(img_dir, fname), img)
                writer.writerow([fname, f"{x:.6f}", f"{y:.6f}", f"{conf:.1f}"])

        print(f"  {split_name}: {len(samples)} frames -> {img_dir}")

    print(f"\nDataset ready at: {DATA_DIR}")
    print("Upload this folder to Google Drive for Colab training.")


if __name__ == "__main__":
    main()
