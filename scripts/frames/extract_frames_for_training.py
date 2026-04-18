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
PROJECT_DIR = os.path.normpath(os.path.join(SCRIPTS_DIR, "..", ".."))
VIDEOS_DIR = os.path.join(PROJECT_DIR, "Videos")
DATA_DIR = os.path.join(PROJECT_DIR, "data_regressor")


def crop_square(frame: np.ndarray, crop_cfg: dict | None) -> tuple[np.ndarray, int, int, int, int]:
    """Crop a frame to a square region.

    Handles both portrait (h > w) and landscape (w > h) videos.

    Args:
        frame: BGR image
        crop_cfg: {"y": int, "h": int} and/or {"x": int, "w": int} in pixels, or None for center crop

    Returns:
        (cropped_frame, crop_x, crop_y, crop_w, crop_h)
    """
    h, w = frame.shape[:2]
    side = min(h, w)

    if crop_cfg:
        cx = int(crop_cfg.get("x", (w - side) // 2 if w > h else 0))
        cy = int(crop_cfg.get("y", (h - side) // 2 if h > w else 0))
        cw = int(crop_cfg.get("w", side if w > h else w))
        ch = int(crop_cfg.get("h", side if h > w else h))
    else:
        # Default: center crop to square
        cx = (w - side) // 2
        cy = (h - side) // 2
        cw = side
        ch = side

    # Clamp
    cx = max(0, min(cx, w - 1))
    cy = max(0, min(cy, h - 1))
    cw = min(cw, w - cx)
    ch = min(ch, h - cy)

    return frame[cy:cy + ch, cx:cx + cw], cx, cy, cw, ch


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

    # Crop config: {"y": pixel_offset, "h": crop_height}
    crop_cfg = labels_data.get("crop", None)

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

    frame_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    frame_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))

    # Get interval
    interval_ms = 100
    poses_path = os.path.join(video_dir, f"{video_name}_poses.json")
    if os.path.isfile(poses_path):
        with open(poses_path, encoding="utf-8") as f:
            poses_data = json.load(f)
        interval_ms = poses_data.get("intervalMs", interval_ms)

    results = []
    skipped = 0

    for key, label in labels.items():
        fi = label["frameIndex"]
        timestamp_ms = fi * interval_ms

        cap.set(cv2.CAP_PROP_POS_MSEC, timestamp_ms)
        ret, frame = cap.read()
        if not ret or frame is None:
            continue

        # Crop to square
        cropped, crop_x, crop_y, crop_w, crop_h = crop_square(frame, crop_cfg)
        resized = cv2.resize(cropped, (img_size, img_size))

        if label.get("correctedX") is not None and label.get("correctedY") is not None:
            # Remap normalized coords from full frame to cropped region
            x_px = float(label["correctedX"]) * frame_w
            y_px = float(label["correctedY"]) * frame_h
            x_in_crop = (x_px - crop_x) / crop_w
            y_in_crop = (y_px - crop_y) / crop_h

            # Skip if ball falls outside crop
            if x_in_crop < 0 or x_in_crop > 1 or y_in_crop < 0 or y_in_crop > 1:
                skipped += 1
                continue

            results.append((resized, x_in_crop, y_in_crop, 1.0))
        elif label["label"] == "no_ball":
            results.append((resized, 0.0, 0.0, 0.0))

    cap.release()
    if skipped:
        print(f"  skipped {skipped} frames (ball outside crop)")
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
    parser.add_argument("--video", type=str, default=None, help="Process only this video (e.g. IMG_6370)")
    args = parser.parse_args()

    do_augment = args.augment and not args.no_augment

    all_samples: list[tuple[np.ndarray, float, float, float]] = []

    entries = [args.video] if args.video else sorted(os.listdir(VIDEOS_DIR))
    for entry in entries:
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

    # Write to disk (clean old data first)
    import shutil
    if os.path.exists(DATA_DIR):
        shutil.rmtree(DATA_DIR)
        print(f"Cleaned old data at {DATA_DIR}")

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
