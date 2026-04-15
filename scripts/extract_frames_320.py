"""
Extract 320x320 frames from a video.

Crops a square region (top/center/bottom) then resizes to 320x320.
Frames sampled every 100ms.

Usage:
    python scripts/extract_frames_320.py <video_path> <top|center|bottom>

Examples:
    python scripts/extract_frames_320.py app/src/main/assets/Videos/IMG_6330/IMG_6330.MOV top
    python scripts/extract_frames_320.py app/src/main/assets/Videos/IMG_6414/IMG_6414.MOV center
"""

import sys
from pathlib import Path

import cv2


def main():
    if len(sys.argv) != 3:
        print("Usage: python extract_frames_320.py <video_path> <top|center|bottom>")
        sys.exit(1)

    video_path = Path(sys.argv[1])
    part = sys.argv[2].lower()
    if part not in ("top", "center", "bottom"):
        print(f"Invalid part '{part}'. Use top, center, or bottom.")
        sys.exit(1)

    if not video_path.exists():
        print(f"Video not found: {video_path}")
        sys.exit(1)

    out_dir = video_path.parent / "frames_320"
    out_dir.mkdir(exist_ok=True)

    cap = cv2.VideoCapture(str(video_path))
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration_ms = int(total / fps * 1000)
    w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    crop_size = min(w, h)

    # Horizontal centering
    x0 = (w - crop_size) // 2

    # Vertical position
    if part == "top":
        y0 = 0
    elif part == "center":
        y0 = (h - crop_size) // 2
    else:
        y0 = h - crop_size

    print(f"Video: {w}x{h}, {fps:.1f}fps, ~{duration_ms}ms")
    print(f"Crop: {part} {crop_size}x{crop_size} at ({x0},{y0})")
    print(f"Output: {out_dir}")

    step_ms = 100
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
        pos_ms += step_ms

    cap.release()
    print(f"Exported {i} frames")


if __name__ == "__main__":
    main()
