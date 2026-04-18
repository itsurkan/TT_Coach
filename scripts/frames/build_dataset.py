"""
Build data_regressor/ dataset from frames_320/ and _labels.json.

Reads each video's labels, maps correctedX/correctedY to the 320x320 crop space,
copies frames to train/ or val/ with sequential naming, and writes labels.csv.

80/20 train/val split per video.

Usage:
    python scripts/build_dataset.py
"""

import csv
import json
import random
import shutil
from pathlib import Path

random.seed(42)

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
VIDEOS_DIR = PROJECT_ROOT / "Videos"
OUT_DIR = PROJECT_ROOT / "data_regressor"

TRAIN_RATIO = 0.8


def main():
    # Collect all samples from all videos
    all_samples = []

    for folder in sorted(VIDEOS_DIR.iterdir()):
        if not folder.is_dir():
            continue

        labels_files = list(folder.glob("*_labels.json"))
        frames_dir = folder / "frames_320"
        if not labels_files or not frames_dir.exists():
            continue

        data = json.load(open(labels_files[0]))
        labels = data.get("labels", {})
        crop = data.get("crop", {})
        crop_y = crop.get("y", 0)
        crop_h = crop.get("h", 0)

        # Get video dimensions to compute coordinate mapping
        videos = [f for f in folder.iterdir() if f.suffix.lower() in (".mp4", ".mov", ".avi")]
        if not videos:
            continue

        import cv2
        cap = cv2.VideoCapture(str(videos[0]))
        vid_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        vid_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        cap.release()

        crop_size = crop_h
        x0 = (vid_w - crop_size) // 2 if crop_size <= vid_w else 0

        video_samples = 0
        ball_samples = 0

        for frame_idx_str, lbl in labels.items():
            frame_idx = int(frame_idx_str)
            frame_file = frames_dir / f"{frame_idx:05d}.png"
            if not frame_file.exists():
                continue

            if lbl.get("label") == "no_ball":
                x, y, conf = 0.0, 0.0, 0.0
            else:
                # correctedX/Y are in full-frame normalized coords
                # Map to crop-space normalized coords
                cx = lbl.get("correctedX", 0.0)
                cy = lbl.get("correctedY", 0.0)

                # Full frame pixel coords
                px = cx * vid_w
                py = cy * vid_h

                # Crop-space pixel coords
                crop_px = px - x0
                crop_py = py - crop_y

                # Normalize to [0,1] within crop
                x = crop_px / crop_size
                y = crop_py / crop_size

                # Skip if ball is outside the crop region
                if x < 0 or x > 1 or y < 0 or y > 1:
                    continue

                conf = 1.0
                ball_samples += 1

            all_samples.append({
                "src": frame_file,
                "x": round(x, 6),
                "y": round(y, 6),
                "confidence": conf,
                "video": folder.name,
            })
            video_samples += 1

        print(f"{folder.name}: {video_samples} samples ({ball_samples} ball, {video_samples - ball_samples} no_ball)")

    # Shuffle and split
    random.shuffle(all_samples)
    split_idx = int(len(all_samples) * TRAIN_RATIO)
    train_samples = all_samples[:split_idx]
    val_samples = all_samples[split_idx:]

    print(f"\nTotal: {len(all_samples)} samples")
    print(f"Train: {len(train_samples)}, Val: {len(val_samples)}")

    # Write dataset
    for split_name, samples in [("train", train_samples), ("val", val_samples)]:
        split_dir = OUT_DIR / split_name
        img_dir = split_dir / "images"
        img_dir.mkdir(parents=True, exist_ok=True)

        # Clear old images
        for old in img_dir.glob("*.png"):
            old.unlink()

        csv_path = split_dir / "labels.csv"
        with open(csv_path, "w", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["filename", "x", "y", "confidence"])

            for i, sample in enumerate(samples):
                fname = f"{i:05d}.png"
                shutil.copy2(sample["src"], img_dir / fname)
                writer.writerow([fname, sample["x"], sample["y"], sample["confidence"]])

        ball_count = sum(1 for s in samples if s["confidence"] > 0)
        print(f"  {split_name}: {len(samples)} images ({ball_count} ball, {len(samples) - ball_count} no_ball) -> {csv_path}")

    print("\nDone!")


if __name__ == "__main__":
    main()
