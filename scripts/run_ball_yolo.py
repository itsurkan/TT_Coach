"""
BallDetector YOLO — run YOLOv11-nano on a video and export to _ball_yolo.json.
Optionally evaluate against _labels.json ground truth.

Usage:
    python scripts/run_ball_yolo.py                                    # all videos
    python scripts/run_ball_yolo.py IMG_6330                            # single video
    python scripts/run_ball_yolo.py IMG_6330 --evaluate                 # + compare with labels
    python scripts/run_ball_yolo.py IMG_6330 --frames 30 50             # frame range
    python scripts/run_ball_yolo.py IMG_6330 --region top               # top half (default)
    python scripts/run_ball_yolo.py IMG_6330 --region bottom|center|full
"""

import argparse
import json
import math
import sys
import time
from pathlib import Path

import cv2
import numpy as np
from ultralytics import YOLO

# ── Config ──
PROJECT_ROOT = Path(__file__).resolve().parent.parent
VIDEOS_DIR = PROJECT_ROOT / "app/src/main/assets/Videos"
MODEL_PATH = PROJECT_ROOT / "trained/best_yolo.pt"

FRAME_STEP_MS = 100
CONFIDENCE_THRESHOLD = 0.25  # YOLO confidence threshold


# ── Process one video ──

def compute_region(region, height):
    """Returns (y_start, y_end) for the given region name."""
    if region == "top":
        return 0, height // 2
    elif region == "bottom":
        return height // 2, height
    elif region == "center":
        return height // 4, 3 * height // 4
    else:  # full
        return 0, height


def process_video(model, video_path, out_path, frame_range=None, region="top"):
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        print(f"  ERROR: Cannot open {video_path}")
        return []

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_video_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration_ms = int(total_video_frames / fps * 1000)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    frame_start = frame_range[0] if frame_range else 0
    frame_end = frame_range[1] if frame_range else (duration_ms // FRAME_STEP_MS)

    y_start, y_end = compute_region(region, height)
    region_h = y_end - y_start

    print(f"  {width}x{height}, {fps:.1f}fps, ~{duration_ms}ms")
    print(f"  Region: {region} (y={y_start}..{y_end})")
    print(f"  Frames: {frame_start} to {frame_end}")

    frames_data = []
    frame_index = 0
    pos_ms = 0

    while pos_ms <= duration_ms and frame_index <= frame_end:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if not ret:
            pos_ms += FRAME_STEP_MS
            frame_index += 1
            continue

        if frame_index >= frame_start:
            # Crop to selected region
            crop = frame[y_start:y_end]

            # Run YOLO inference on cropped region
            results = model(crop, imgsz=320, conf=CONFIDENCE_THRESHOLD, verbose=False)[0]

            best_detection = None
            if len(results.boxes) > 0:
                # Pick highest confidence detection
                for box in results.boxes:
                    conf = float(box.conf[0])
                    if best_detection is None or conf > best_detection["confidence"]:
                        x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
                        # Map from crop coords to full-frame normalized
                        cx = float((x1 + x2) / 2.0 / width)
                        cy = float((y_start + (y1 + y2) / 2.0) / height)
                        bw = float(x2 - x1)
                        bh = float(y2 - y1)
                        radius_px = max(bw, bh) / 2.0
                        best_detection = {
                            "x": round(max(0.0, min(1.0, cx)), 6),
                            "y": round(max(0.0, min(1.0, cy)), 6),
                            "radiusPx": round(float(radius_px), 1),
                            "confidence": round(conf, 6),
                            "status": "DETECTED",
                        }

            if best_detection is None:
                best_detection = {
                    "x": 0.0, "y": 0.0, "radiusPx": 0.0,
                    "confidence": 0.0, "status": "NOT_DETECTED",
                }

            frame_entry = {
                "frameIndex": frame_index,
                "timestampMs": pos_ms,
                "ball": best_detection,
            }
            frames_data.append(frame_entry)

            if frame_index % 20 == 0:
                if best_detection["status"] == "DETECTED":
                    status = f"DETECTED conf={best_detection['confidence']:.2f} at ({best_detection['x']:.3f}, {best_detection['y']:.3f})"
                else:
                    status = "none"
                print(f"    frame {frame_index} @ {pos_ms}ms -> {status}")

        frame_index += 1
        pos_ms += FRAME_STEP_MS

    cap.release()

    # Write JSON
    result = {
        "videoName": video_path.name,
        "detector": "YOLOv11-nano",
        "intervalMs": FRAME_STEP_MS,
        "videoDurationMs": duration_ms,
        "videoWidth": width,
        "videoHeight": height,
        "totalFrames": len(frames_data),
        "exportTimestamp": int(time.time() * 1000),
        "frames": frames_data,
    }

    with open(out_path, "w") as f:
        json.dump(result, f, indent=2)

    detected_count = sum(1 for fr in frames_data if fr["ball"]["status"] == "DETECTED")
    print(f"  -> {out_path.name}: {len(frames_data)} frames, {detected_count} detected")
    return frames_data


# ── Evaluation (shared with regressor) ──

def evaluate(frames_data, labels_path, video_name):
    with open(labels_path) as f:
        labels = json.load(f)["labels"]

    dist_threshold = 0.05

    tp = 0
    fp = 0
    fn = 0
    tn = 0

    errors = []
    false_positives = []
    missed = []
    far_detections = []

    for frame_data in frames_data:
        fi = str(frame_data["frameIndex"])
        if fi not in labels:
            continue

        lbl = labels[fi]
        label_type = lbl["label"]
        detected = frame_data["ball"]["status"] == "DETECTED"

        if label_type == "no_ball":
            if detected:
                fp += 1
                false_positives.append(frame_data["frameIndex"])
            else:
                tn += 1

        elif label_type == "wrong":
            gt_x = lbl["correctedX"]
            gt_y = lbl["correctedY"]

            if detected:
                dx = frame_data["ball"]["x"] - gt_x
                dy = frame_data["ball"]["y"] - gt_y
                dist = math.sqrt(dx * dx + dy * dy)
                errors.append(dist)

                if dist <= dist_threshold:
                    tp += 1
                else:
                    far_detections.append({
                        "frame": frame_data["frameIndex"],
                        "dist": round(dist, 4),
                        "pred": (round(frame_data["ball"]["x"], 3), round(frame_data["ball"]["y"], 3)),
                        "gt": (round(gt_x, 3), round(gt_y, 3)),
                    })
                    fp += 1
                    fn += 1
            else:
                fn += 1
                missed.append(frame_data["frameIndex"])

    total = tp + fp + fn + tn
    ball_present = tp + len(missed) + len(far_detections)
    no_ball = tn + len(false_positives)

    print(f"\n{'='*60}")
    print(f"  EVALUATION: {video_name} (YOLO)")
    print(f"{'='*60}")
    print(f"  Labeled frames:      {total}")
    print(f"    ball present:      {ball_present}")
    print(f"    no ball:           {no_ball}")
    print()
    print(f"  TP (hit <={dist_threshold}):      {tp}")
    print(f"  FP (false alarm):    {fp}  ({len(false_positives)} on no_ball + {len(far_detections)} too far)")
    print(f"  FN (missed):         {fn}  ({len(missed)} undetected + {len(far_detections)} too far)")
    print(f"  TN (correct reject): {tn}")
    print()

    precision = tp / (tp + fp) if (tp + fp) > 0 else 0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0
    accuracy = (tp + tn) / total if total > 0 else 0

    print(f"  Accuracy:    {accuracy:.1%}")
    print(f"  Precision:   {precision:.1%}")
    print(f"  Recall:      {recall:.1%}")
    print(f"  F1:          {f1:.1%}")

    if errors:
        print(f"\n  Position error (on {len(errors)} detected ball frames):")
        print(f"    mean:   {np.mean(errors):.4f}")
        print(f"    median: {np.median(errors):.4f}")
        print(f"    max:    {np.max(errors):.4f}")
        print(f"    <=0.02:  {sum(1 for e in errors if e <= 0.02)}/{len(errors)}")
        print(f"    <=0.05:  {sum(1 for e in errors if e <= 0.05)}/{len(errors)}")
        print(f"    <=0.10:  {sum(1 for e in errors if e <= 0.10)}/{len(errors)}")

    if false_positives:
        print(f"\n  FP on no_ball frames: {false_positives[:20]}{'...' if len(false_positives) > 20 else ''}")

    if missed:
        print(f"\n  Missed (undetected): {missed[:20]}{'...' if len(missed) > 20 else ''}")

    if far_detections:
        print(f"\n  Detected but too far (>{dist_threshold}):")
        for d in far_detections[:15]:
            print(f"    frame {d['frame']:3d}: dist={d['dist']:.4f}  pred={d['pred']}  gt={d['gt']}")
        if len(far_detections) > 15:
            print(f"    ... and {len(far_detections) - 15} more")

    # ── Side-by-side comparison with regressor ──
    v5_path = labels_path.parent / f"{video_name}_ball_v5.json"
    if v5_path.exists():
        print(f"\n  --- Regressor (V5) comparison ---")
        with open(v5_path) as f:
            v5_data = json.load(f)["frames"]
        v5_map = {str(fr["frameIndex"]): fr for fr in v5_data}

        v5_tp, v5_fp, v5_fn, v5_tn = 0, 0, 0, 0
        v5_errors = []
        for frame_data in v5_data:
            fi = str(frame_data["frameIndex"])
            if fi not in labels:
                continue
            lbl = labels[fi]
            det = frame_data["ball"]["status"] == "DETECTED" if frame_data["ball"] else False
            if lbl["label"] == "no_ball":
                if det:
                    v5_fp += 1
                else:
                    v5_tn += 1
            elif lbl["label"] == "wrong":
                gt_x, gt_y = lbl["correctedX"], lbl["correctedY"]
                if det:
                    dx = frame_data["ball"]["x"] - gt_x
                    dy = frame_data["ball"]["y"] - gt_y
                    d = math.sqrt(dx*dx + dy*dy)
                    v5_errors.append(d)
                    if d <= dist_threshold:
                        v5_tp += 1
                    else:
                        v5_fp += 1
                        v5_fn += 1
                else:
                    v5_fn += 1

        v5_total = v5_tp + v5_fp + v5_fn + v5_tn
        v5_prec = v5_tp / (v5_tp + v5_fp) if (v5_tp + v5_fp) > 0 else 0
        v5_rec = v5_tp / (v5_tp + v5_fn) if (v5_tp + v5_fn) > 0 else 0
        v5_f1 = 2 * v5_prec * v5_rec / (v5_prec + v5_rec) if (v5_prec + v5_rec) > 0 else 0
        v5_acc = (v5_tp + v5_tn) / v5_total if v5_total > 0 else 0

        print(f"                 Regressor    YOLO")
        print(f"  Accuracy:      {v5_acc:>7.1%}    {accuracy:>7.1%}")
        print(f"  Precision:     {v5_prec:>7.1%}    {precision:>7.1%}")
        print(f"  Recall:        {v5_rec:>7.1%}    {recall:>7.1%}")
        print(f"  F1:            {v5_f1:>7.1%}    {f1:>7.1%}")
        if v5_errors:
            print(f"  Mean error:    {np.mean(v5_errors):>7.4f}    {np.mean(errors):.4f}" if errors else "")
            print(f"  Median error:  {np.median(v5_errors):>7.4f}    {np.median(errors):.4f}" if errors else "")

    print(f"{'='*60}")


# ── Main ──

def main():
    parser = argparse.ArgumentParser(description="BallDetector YOLO — detection + export")
    parser.add_argument("video_folder", nargs="?", default=None,
                        help="Video folder name (e.g. IMG_6330). Omit to run all.")
    parser.add_argument("--frames", nargs=2, type=int, default=None,
                        metavar=("START", "END"), help="Frame range (default: all)")
    parser.add_argument("--region", choices=["top", "bottom", "center", "full"], default="top",
                        help="Which part of the frame to analyze (default: top)")
    parser.add_argument("--evaluate", action="store_true",
                        help="Evaluate against <name>_labels.json")
    args = parser.parse_args()

    if not MODEL_PATH.exists():
        print(f"ERROR: model not found: {MODEL_PATH}")
        print(f"Download best.pt from Colab to trained/best_yolo.pt")
        sys.exit(1)

    print(f"Loading YOLO model: {MODEL_PATH}")
    model = YOLO(str(MODEL_PATH))
    print("Model loaded.\n")

    if args.video_folder:
        video_dir = VIDEOS_DIR / args.video_folder
        if not video_dir.is_dir():
            print(f"ERROR: folder not found: {video_dir}")
            sys.exit(1)
        folders = [video_dir]
    else:
        folders = sorted(d for d in VIDEOS_DIR.iterdir() if d.is_dir())

    for folder in folders:
        video_path = None
        for f in folder.iterdir():
            if f.suffix.lower() in (".mp4", ".mov", ".avi"):
                video_path = f
                break
        if video_path is None:
            continue

        folder_name = folder.name
        out_path = folder / f"{folder_name}_ball_yolo.json"
        print(f"Processing: {folder_name}/{video_path.name}")

        t0 = time.time()
        frames_data = process_video(model, video_path, out_path, args.frames, args.region)
        elapsed = time.time() - t0
        print(f"  Time: {elapsed:.1f}s\n")

        if args.evaluate:
            labels_path = folder / f"{folder_name}_labels.json"
            if labels_path.exists():
                evaluate(frames_data, labels_path, folder_name)
            else:
                print(f"  WARNING: no labels file: {labels_path}")
        print()

    print("Done!")


if __name__ == "__main__":
    main()
