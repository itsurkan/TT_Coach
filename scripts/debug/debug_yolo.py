#!/usr/bin/env python3
"""
debug_yolo.py

Debug script: runs YOLOv8-nano on video frames and saves annotated results.
Uses COCO pre-trained model — "sports ball" is class 32.

Output goes to: scripts/debug_yolo_frames/frame_NNN/

Usage:
    pip install ultralytics
    python scripts/debug_yolo.py
"""

import os
import sys

import cv2
import numpy as np

try:
    from ultralytics import YOLO
except ImportError:
    print("ERROR: ultralytics not installed. Run: pip install ultralytics", file=sys.stderr)
    sys.exit(1)

# ── Config ────────────────────────────────────────────────────────────────────

SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.normpath(os.path.join(SCRIPTS_DIR, "..", ".."))
VIDEO_PATH  = os.path.join(PROJECT_DIR, "Videos", "IMG_6370", "IMG_6370.MP4")
OUTPUT_DIR  = os.path.join(SCRIPTS_DIR, "debug_yolo_frames")
YOLO_MODEL_PATH = os.path.join(PROJECT_DIR, "models", "pretrained", "yolov8n.pt")

INTERVAL_MS = 100
FRAME_START = 120
FRAME_END   = 125   # inclusive

# COCO class 32 = "sports ball"
BALL_CLASS_ID   = 32
# Also check all detections — the ball might be detected as another class
CONF_THRESHOLD  = 0.1   # low threshold to see what YOLO finds


def save(out_dir: str, name: str, img: np.ndarray):
    cv2.imwrite(os.path.join(out_dir, name), img)


def main():
    if not os.path.isfile(VIDEO_PATH):
        print(f"ERROR: video not found: {VIDEO_PATH}", file=sys.stderr)
        sys.exit(1)

    # Load YOLOv8-nano (downloads automatically on first run)
    print("Loading YOLOv8-nano...")
    model = YOLO(YOLO_MODEL_PATH)

    cap = cv2.VideoCapture(VIDEO_PATH)
    if not cap.isOpened():
        print(f"ERROR: cannot open video: {VIDEO_PATH}", file=sys.stderr)
        sys.exit(1)

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    print(f"Video: {VIDEO_PATH}  fps={fps:.1f}")

    if os.path.isdir(OUTPUT_DIR):
        import shutil
        shutil.rmtree(OUTPUT_DIR)
    os.makedirs(OUTPUT_DIR)

    for frame_idx in range(FRAME_END + 1):
        pos_ms = frame_idx * INTERVAL_MS
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame_bgr = cap.read()
        if not ret:
            print(f"  frame {frame_idx}: read failed, stopping")
            break

        if frame_idx < FRAME_START:
            continue

        h, w = frame_bgr.shape[:2]

        # Run YOLO inference
        results = model(frame_bgr, conf=CONF_THRESHOLD, verbose=False)[0]

        fdir = os.path.join(OUTPUT_DIR, f"frame_{frame_idx:03d}")
        os.makedirs(fdir, exist_ok=True)

        # 1 - original
        save(fdir, "1_original.png", frame_bgr)

        # 2 - all detections (any class)
        vis_all = frame_bgr.copy()
        for box in results.boxes:
            cls_id = int(box.cls[0])
            conf   = float(box.conf[0])
            x1, y1, x2, y2 = map(int, box.xyxy[0])
            cls_name = model.names[cls_id]
            color = (0, 255, 0) if cls_id == BALL_CLASS_ID else (200, 200, 200)
            cv2.rectangle(vis_all, (x1, y1), (x2, y2), color, 2)
            label = f"{cls_name} {conf:.2f}"
            cv2.putText(vis_all, label, (x1, y1 - 5),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)
        save(fdir, "2_all_detections.png", vis_all)

        # 3 - only sports ball detections
        vis_ball = frame_bgr.copy()
        ball_count = 0
        for box in results.boxes:
            cls_id = int(box.cls[0])
            conf   = float(box.conf[0])
            x1, y1, x2, y2 = map(int, box.xyxy[0])
            if cls_id == BALL_CLASS_ID:
                ball_count += 1
                cv2.rectangle(vis_ball, (x1, y1), (x2, y2), (0, 255, 0), 2)
                cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
                cv2.drawMarker(vis_ball, (cx, cy), (0, 0, 255), cv2.MARKER_CROSS, 20, 2)
                label = f"ball {conf:.2f}"
                cv2.putText(vis_ball, label, (x1, y1 - 5),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)

        status = f"{ball_count} ball(s)" if ball_count > 0 else "NO BALL"
        cv2.putText(vis_ball, status, (10, 30),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.8,
                    (0, 255, 0) if ball_count > 0 else (0, 0, 255), 2)
        save(fdir, "3_ball_only.png", vis_ball)

        # Print summary
        n_total = len(results.boxes)
        classes_found = [f"{model.names[int(b.cls[0])]}({float(b.conf[0]):.2f})"
                         for b in results.boxes]
        print(f"  frame {frame_idx:3d}  t={pos_ms:5d}ms  "
              f"{n_total} det(s)  balls={ball_count}  [{', '.join(classes_found)}]")

    cap.release()
    print(f"\nDone. Output in: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
