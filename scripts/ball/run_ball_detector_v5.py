"""
BallDetectorV5 — local runner (PyTorch + OpenCV).

Mirrors the Android BallDetectorV5 pipeline:
  1. Frame diff → motion bounding boxes
  2. Crop each motion box → resize 320×320 → ImageNet normalize
  3. MobileNetV3-Small regressor → (x, y, confidence)
  4. Map coords back to full-frame, pick best detection

Writes <video_name>_ball_v5.json into each video's asset folder.

Usage:
    python scripts/run_ball_detector_v5.py                          # all videos
    python scripts/run_ball_detector_v5.py IMG_6330                 # single video
    python scripts/run_ball_detector_v5.py IMG_6330 --evaluate      # + compare with labels
    python scripts/run_ball_detector_v5.py IMG_6330 --frames 30 50  # frame range
"""

import argparse
import json
import math
import os
import sys
import time
from pathlib import Path

import cv2
import numpy as np
import torch
import torch.nn as nn
from torchvision.models import mobilenet_v3_small, MobileNet_V3_Small_Weights

# ── Paths ──
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
VIDEOS_DIR = PROJECT_ROOT / "Videos"
MODEL_PATH = PROJECT_ROOT / "models" / "trained" / "best_model.pth"

MODEL_INPUT_SIZE = 320
FRAME_STEP_MS = 100
CONFIDENCE_THRESHOLD = 0.5

# ImageNet normalization
IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)

# Motion detection parameters (same as Android V3/V5)
MOTION_THRESHOLD = 25
MOTION_DILATE_PX = 30
MIN_RADIUS = 3
MAX_RADIUS = 35


# ── Model ──

class BallRegressor(nn.Module):
    def __init__(self):
        super().__init__()
        backbone = mobilenet_v3_small(weights=None)
        self.features = backbone.features
        self.avgpool = backbone.avgpool
        self.head = nn.Sequential(
            nn.Linear(576, 128),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(128, 3),
        )

    def forward(self, x):
        x = self.features(x)
        x = self.avgpool(x)
        x = x.flatten(1)
        x = self.head(x)
        return torch.sigmoid(x)


def load_model():
    model = BallRegressor()
    state = torch.load(str(MODEL_PATH), map_location="cpu", weights_only=True)
    model.load_state_dict(state)
    model.eval()
    return model


# ── Motion detection ──

def motion_bounding_rects(prev_gray, curr_gray, roi):
    """
    Compute bounding rects of moving regions within roi.
    roi: (x, y, w, h)
    Returns list of (x, y, w, h) in full-frame coords.
    """
    rx, ry, rw, rh = roi
    prev_roi = prev_gray[ry:ry + rh, rx:rx + rw]
    curr_roi = curr_gray[ry:ry + rh, rx:rx + rw]

    diff = cv2.absdiff(curr_roi, prev_roi)
    _, thresh = cv2.threshold(diff, MOTION_THRESHOLD, 255, cv2.THRESH_BINARY)

    kernel = cv2.getStructuringElement(
        cv2.MORPH_ELLIPSE, (MOTION_DILATE_PX, MOTION_DILATE_PX)
    )
    dilated = cv2.dilate(thresh, kernel)

    contours, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    min_short_side = MIN_RADIUS * 2
    max_short_side = int((MAX_RADIUS + MOTION_DILATE_PX) * 2)
    min_blob_area = 3.14159 * MIN_RADIUS * MIN_RADIUS

    rects = []
    for c in contours:
        area = cv2.contourArea(c)
        if area >= min_blob_area:
            bx, by, bw, bh = cv2.boundingRect(c)
            short_side = min(bw, bh)
            if min_short_side <= short_side <= max_short_side:
                rects.append((rx + bx, ry + by, bw, bh))

    return rects


# ── Inference ──

def preprocess_crop(frame_bgr, rect):
    """Crop rect from frame, resize to 320×320, normalize for model."""
    x, y, w, h = rect
    crop = frame_bgr[y:y + h, x:x + w]
    resized = cv2.resize(crop, (MODEL_INPUT_SIZE, MODEL_INPUT_SIZE))
    # BGR → RGB, uint8 → float32 [0,1], then ImageNet normalize
    rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    normalized = (rgb - IMAGENET_MEAN) / IMAGENET_STD
    # HWC → CHW, add batch dim
    tensor = torch.from_numpy(normalized.transpose(2, 0, 1)).unsqueeze(0)
    return tensor


def infer_in_rect(model, frame_bgr, rect, frame_w, frame_h):
    """
    Run model on a cropped rect, return (x, y, conf) in full-frame normalized coords.
    Returns None if confidence < threshold.
    """
    inp = preprocess_crop(frame_bgr, rect)
    with torch.no_grad():
        out = model(inp)  # [1, 3] → (x, y, conf)

    pred_x = out[0, 0].item()
    pred_y = out[0, 1].item()
    pred_conf = out[0, 2].item()

    if pred_conf < CONFIDENCE_THRESHOLD:
        return None

    # Map from crop-normalized → full-frame normalized
    rx, ry, rw, rh = rect
    full_x = (rx + pred_x * rw) / frame_w
    full_y = (ry + pred_y * rh) / frame_h

    return {
        "x": round(max(0.0, min(1.0, full_x)), 6),
        "y": round(max(0.0, min(1.0, full_y)), 6),
        "radiusPx": 0.0,
        "confidence": round(max(0.0, min(1.0, pred_conf)), 6),
        "status": "DETECTED",
    }


# ── Process one video ──

def process_video(model, video_path, out_path, frame_range=None):
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        print(f"  ERROR: Cannot open {video_path}")
        return []

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_frames_video = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration_ms = int(total_frames_video / fps * 1000)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    frame_start = frame_range[0] if frame_range else 0
    frame_end = frame_range[1] if frame_range else (duration_ms // FRAME_STEP_MS)

    print(f"  {width}x{height}, {fps:.1f}fps, ~{duration_ms}ms")
    print(f"  Frames: {frame_start} to {frame_end}")

    prev_gray = None
    frames_data = []
    frame_index = 0
    pos_ms = 0
    roi = (0, 0, width, height // 2)

    while pos_ms <= duration_ms and frame_index <= frame_end:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if not ret:
            pos_ms += FRAME_STEP_MS
            frame_index += 1
            continue

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

        if frame_index >= frame_start:
            # Determine search rects
            if prev_gray is None:
                search_rects = [roi]
            else:
                search_rects = motion_bounding_rects(prev_gray, gray, roi)

            # Run inference on each motion rect
            best_detection = None
            if search_rects:
                for rect in search_rects:
                    det = infer_in_rect(model, frame, rect, width, height)
                    if det is not None:
                        if best_detection is None or det["confidence"] > best_detection["confidence"]:
                            best_detection = det

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
                status = f"DETECTED conf={best_detection['confidence']:.2f}" if best_detection["status"] == "DETECTED" else "none"
                print(f"    frame {frame_index} @ {pos_ms}ms -> {status}")

        prev_gray = gray.copy()
        frame_index += 1
        pos_ms += FRAME_STEP_MS

    cap.release()

    # Write JSON
    result = {
        "videoName": video_path.name,
        "detector": "V5-Python",
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


# ── Evaluation ──

def evaluate(frames_data, labels_path, video_name):
    with open(labels_path) as f:
        labels = json.load(f)["labels"]

    dist_threshold = 0.05  # 5% of frame dimension

    tp = 0   # ball present, detected close enough
    fp = 0   # no ball but detected
    fn = 0   # ball present, not detected (or too far)
    tn = 0   # no ball, not detected

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

        elif label_type == "wrong":  # ball present, corrected position = ground truth
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
    # Avoid double-counting: far_detections add both fp and fn
    ball_present = tp + len(missed) + len(far_detections)
    no_ball = tn + len(false_positives)

    print(f"\n{'='*60}")
    print(f"  EVALUATION: {video_name}")
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

    print(f"{'='*60}")


# ── Main ──

def main():
    parser = argparse.ArgumentParser(description="BallDetectorV5 detection + export")
    parser.add_argument("video_folder", nargs="?", default=None,
                        help="Video folder name (e.g. IMG_6330). Omit to run all.")
    parser.add_argument("--frames", nargs=2, type=int, default=None,
                        metavar=("START", "END"), help="Frame range (default: all)")
    parser.add_argument("--evaluate", action="store_true",
                        help="Evaluate against <name>_labels.json")
    args = parser.parse_args()

    print("Loading model...")
    model = load_model()
    print("Model loaded.\n")

    if args.video_folder:
        # Single video
        video_dir = VIDEOS_DIR / args.video_folder
        if not video_dir.is_dir():
            print(f"ERROR: folder not found: {video_dir}")
            sys.exit(1)
        folders = [video_dir]
    else:
        # All videos
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
        out_path = folder / f"{folder_name}_ball_v5.json"
        print(f"Processing: {folder_name}/{video_path.name}")

        t0 = time.time()
        frames_data = process_video(model, video_path, out_path, args.frames)
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
