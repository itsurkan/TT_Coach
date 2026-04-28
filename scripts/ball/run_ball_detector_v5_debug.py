"""
BallDetectorV5 debug — frame diff pipeline only, bottom half.

Outputs to debug_v5/:
  frame_NNN_diff.png    — absdiff
  frame_NNN_thresh.png  — thresholded
  frame_NNN_dilated.png — dilated
  frame_NNN_rects.png   — motion rects on frame

Usage:
    python scripts/run_ball_detector_v5_debug.py
"""

from pathlib import Path

import cv2
import numpy as np

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
VIDEO_PATH = PROJECT_ROOT / "Videos/IMG_6330/IMG_6330.MOV"
DEBUG_DIR = PROJECT_ROOT / "Videos/IMG_6330/debug_v5"

FRAME_STEP_MS = 100

# Motion parameters
MOTION_THRESHOLD = 25
MOTION_DILATE_PX = 8
# Morphological open to remove small noise before dilation
OPEN_KERNEL_PX = 3
# Min/max side length for bounding rect
MIN_SIDE_PX = 13
MAX_SIDE_PX = 50


def main():
    cap = cv2.VideoCapture(str(VIDEO_PATH))
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_video_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration_ms = int(total_video_frames / fps * 1000)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"Video: {width}x{height}, {fps:.1f}fps, ~{duration_ms}ms")

    # Top half ROI
    roi_y = 0
    roi_h = height // 2
    print(f"ROI: top half y={roi_y}..{roi_h}")

    open_kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (OPEN_KERNEL_PX, OPEN_KERNEL_PX))
    dilate_kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (MOTION_DILATE_PX, MOTION_DILATE_PX))

    prev_gray = None
    frame_index = 0
    pos_ms = 0

    while pos_ms <= duration_ms:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if not ret:
            pos_ms += FRAME_STEP_MS
            continue

        prefix = f"frame_{frame_index:03d}"

        # Crop top half
        bottom = frame[roi_y:roi_h, :, :]
        gray = cv2.cvtColor(bottom, cv2.COLOR_BGR2GRAY)

        if prev_gray is not None:
            # Diff
            diff = cv2.absdiff(gray, prev_gray)
            cv2.imwrite(str(DEBUG_DIR / f"{prefix}_diff.png"), diff)

            # Threshold
            _, thresh = cv2.threshold(diff, MOTION_THRESHOLD, 255, cv2.THRESH_BINARY)
            cv2.imwrite(str(DEBUG_DIR / f"{prefix}_thresh.png"), thresh)

            # Open (remove noise) then dilate (merge nearby blobs)
            opened = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, open_kernel)
            dilated = cv2.dilate(opened, dilate_kernel)
            cv2.imwrite(str(DEBUG_DIR / f"{prefix}_dilated.png"), dilated)

            # Find contours, filter by area
            contours, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            rects = []
            for c in contours:
                bx, by, bw, bh = cv2.boundingRect(c)
                if bw < MIN_SIDE_PX or bh < MIN_SIDE_PX:
                    continue
                if bw > MAX_SIDE_PX or bh > MAX_SIDE_PX:
                    continue
                rects.append((bx, by, bw, bh))

            # Draw on bottom-half crop
            viz = bottom.copy()
            for i, (rx, ry, rw, rh) in enumerate(rects):
                cv2.rectangle(viz, (rx, ry), (rx + rw, ry + rh), (0, 255, 0), 2)
                area = rw * rh
                cv2.putText(viz, f"{rw}x{rh} a={area}", (rx, ry - 5),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.4, (0, 255, 0), 1)
            if not rects:
                cv2.putText(viz, "NO RECTS", (10, 30),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2)
            cv2.imwrite(str(DEBUG_DIR / f"{prefix}_rects.png"), viz)

            print(f"  frame {frame_index:3d} @ {pos_ms:5d}ms -> {len(rects)} rects")
        else:
            print(f"  frame {frame_index:3d} @ {pos_ms:5d}ms -> first frame")

        prev_gray = gray.copy()
        frame_index += 1
        pos_ms += FRAME_STEP_MS

    cap.release()
    print(f"\nDone: {frame_index} frames")


if __name__ == "__main__":
    main()
