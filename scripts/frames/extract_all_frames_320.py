"""
Extract 320x320 frames from all videos using crop info from _labels.json.

Reads crop.y and crop.h from each video's labels JSON,
crops that vertical region (full width), then resizes to 320x320.
Frames sampled every 100ms.

Usage:
    python scripts/extract_all_frames_320.py
"""

import json
from pathlib import Path

import cv2

VIDEOS_DIR = Path(__file__).resolve().parent.parent.parent / "Videos"
STEP_MS = 100


def extract_video(video_path, out_dir, crop_y, crop_h):
    cap = cv2.VideoCapture(str(video_path))
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration_ms = int(total / fps * 1000)
    w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    # Use crop_h as square side, centered horizontally
    crop_size = crop_h
    x0 = (w - crop_size) // 2 if crop_size <= w else 0
    y0 = crop_y

    print(f"  {w}x{h}, crop: y={y0} h={crop_size}, x={x0} w={crop_size}")

    out_dir.mkdir(exist_ok=True)
    pos_ms = 0
    i = 0
    while pos_ms <= duration_ms:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if ret:
            cropped = frame[y0:y0 + crop_size, x0:x0 + crop_size]
            resized = cv2.resize(cropped, (320, 320))
            cv2.imwrite(str(out_dir / f"{i:05d}.png"), resized)
            i += 1
        pos_ms += STEP_MS

    cap.release()
    return i


def main():
    for folder in sorted(VIDEOS_DIR.iterdir()):
        if not folder.is_dir():
            continue

        labels_files = list(folder.glob("*_labels.json"))
        if not labels_files:
            continue

        data = json.load(open(labels_files[0]))
        crop = data.get("crop", {})
        crop_y = crop.get("y", 0)
        crop_h = crop.get("h", 0)
        if crop_h == 0:
            print(f"{folder.name}: no crop info, skipping")
            continue

        videos = [f for f in folder.iterdir() if f.suffix.lower() in (".mp4", ".mov", ".avi")]
        if not videos:
            continue

        out_dir = folder / "frames_320"
        print(f"{folder.name}: crop y={crop_y} h={crop_h}")
        n = extract_video(videos[0], out_dir, crop_y, crop_h)
        print(f"  -> {n} frames\n")


if __name__ == "__main__":
    main()
