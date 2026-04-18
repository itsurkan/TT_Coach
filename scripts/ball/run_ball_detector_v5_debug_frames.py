"""
BallDetectorV5 debug — motion + regressor pipeline with visual debug output.

For each frame, saves ONE image showing:
  - The full frame (top-half ROI)
  - Green rectangles = motion regions
  - Red circle + label = model's best ball detection
  - Frame info overlay (frame index, timestamp, confidence)

No crop images — just annotated full frames.

Usage:
    python scripts/run_ball_detector_v5_debug_frames.py <video_folder> [--frames START END]

Examples:
    python scripts/run_ball_detector_v5_debug_frames.py IMG_6330
    python scripts/run_ball_detector_v5_debug_frames.py IMG_6330 --frames 20 50
"""

import argparse
from pathlib import Path

import cv2
import numpy as np
import torch
import torch.nn as nn
from torchvision.models import mobilenet_v3_small

# ── Config ──
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
VIDEOS_DIR = PROJECT_ROOT / "Videos"
PTH_PATH = PROJECT_ROOT / "models/trained/best_model.pth"

FRAME_STEP_MS = 100
MODEL_INPUT_SIZE = 320
CONFIDENCE_THRESHOLD = 0.5

# ImageNet normalization
IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)

# Motion detection
MOTION_THRESHOLD = 25
MOTION_DILATE_PX = 30
OPEN_KERNEL_PX = 3
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
    state = torch.load(str(PTH_PATH), map_location="cpu", weights_only=True)
    model.load_state_dict(state)
    model.eval()
    return model


# ── Motion detection ──

def motion_bounding_rects(prev_gray, curr_gray, roi):
    rx, ry, rw, rh = roi
    prev_roi = prev_gray[ry:ry + rh, rx:rx + rw]
    curr_roi = curr_gray[ry:ry + rh, rx:rx + rw]

    diff = cv2.absdiff(curr_roi, prev_roi)
    _, thresh = cv2.threshold(diff, MOTION_THRESHOLD, 255, cv2.THRESH_BINARY)

    open_kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (OPEN_KERNEL_PX, OPEN_KERNEL_PX))
    opened = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, open_kernel)

    dilate_kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (MOTION_DILATE_PX, MOTION_DILATE_PX))
    dilated = cv2.dilate(opened, dilate_kernel)

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

    return rects, dilated, thresh


# ── Inference ──

def preprocess_crop(frame_bgr, rect):
    x, y, w, h = rect
    crop = frame_bgr[y:y + h, x:x + w]
    resized = cv2.resize(crop, (MODEL_INPUT_SIZE, MODEL_INPUT_SIZE))
    rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    normalized = (rgb - IMAGENET_MEAN) / IMAGENET_STD
    tensor = torch.from_numpy(normalized.transpose(2, 0, 1)).unsqueeze(0)
    return tensor


def infer_in_rect(model, frame_bgr, rect, frame_w, frame_h):
    inp = preprocess_crop(frame_bgr, rect)
    with torch.no_grad():
        out = model(inp)

    pred_x = out[0, 0].item()
    pred_y = out[0, 1].item()
    pred_conf = out[0, 2].item()

    # Map from crop-normalized -> full-frame pixel coords
    rx, ry, rw, rh = rect
    full_px = rx + pred_x * rw
    full_py = ry + pred_y * rh

    return {
        "x_px": full_px,
        "y_px": full_py,
        "x_norm": full_px / frame_w,
        "y_norm": full_py / frame_h,
        "confidence": pred_conf,
        "rect": rect,
        "pred_in_crop": (pred_x, pred_y),
    }


# ── Main ──

def find_video_file(folder: Path) -> Path:
    for f in folder.iterdir():
        if f.suffix.lower() in (".mp4", ".mov", ".avi"):
            return f
    raise FileNotFoundError(f"No video file found in {folder}")


def main():
    parser = argparse.ArgumentParser(description="BallDetectorV5 debug with model inference")
    parser.add_argument("video_folder", help="Video folder name inside assets/Videos/ (e.g. IMG_6330)")
    parser.add_argument("--frames", nargs=2, type=int, default=[0, 50],
                        metavar=("START", "END"), help="Frame range to debug (default: 0 50)")
    args = parser.parse_args()

    frame_start, frame_end = args.frames

    video_dir = VIDEOS_DIR / args.video_folder
    if not video_dir.is_dir():
        print(f"ERROR: folder not found: {video_dir}")
        return

    video_path = find_video_file(video_dir)
    debug_dir = video_dir / "debug_v5_model"
    debug_dir.mkdir(parents=True, exist_ok=True)

    print("Loading model...")
    model = load_model()
    print("Model loaded.\n")

    cap = cv2.VideoCapture(str(video_path))
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_video_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration_ms = int(total_video_frames / fps * 1000)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"Video: {video_path.name} {width}x{height}, {fps:.1f}fps, ~{duration_ms}ms")

    # Use top half as ROI
    roi = (0, 0, width, height // 2)
    print(f"ROI: top half y=0..{height // 2}")
    print(f"Frames: {frame_start} to {frame_end}\n")

    prev_gray = None
    frame_index = 0
    pos_ms = 0
    detected_frames = []  # collect (frame_index, viz) for result montage

    while pos_ms <= duration_ms and frame_index <= frame_end:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if not ret:
            pos_ms += FRAME_STEP_MS
            frame_index += 1
            continue

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

        if frame_index >= frame_start and prev_gray is not None:
            # Get motion rects + debug masks
            rects, dilated_mask, thresh_mask = motion_bounding_rects(prev_gray, gray, roi)

            # Run model on each motion rect
            detections = []
            for rect in rects:
                det = infer_in_rect(model, frame, rect, width, height)
                detections.append(det)

            # Pick best detection (highest confidence above threshold)
            best = None
            for d in detections:
                if d["confidence"] >= CONFIDENCE_THRESHOLD:
                    if best is None or d["confidence"] > best["confidence"]:
                        best = d

            # ── Draw debug visualization ──
            viz = frame[:height // 2].copy()  # top half only

            # Draw all motion rects in green
            for i, (rx, ry, rw, rh) in enumerate(rects):
                cv2.rectangle(viz, (rx, ry), (rx + rw, ry + rh), (0, 255, 0), 2)
                # Show detection confidence for this rect if available
                if i < len(detections):
                    d = detections[i]
                    conf_color = (0, 255, 0) if d["confidence"] >= CONFIDENCE_THRESHOLD else (0, 165, 255)
                    cv2.putText(viz, f"c={d['confidence']:.2f}", (rx, ry - 5),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.5, conf_color, 1)

            # Draw best detection as red circle
            if best is not None:
                cx = int(best["x_px"])
                cy = int(best["y_px"])
                cv2.circle(viz, (cx, cy), 12, (0, 0, 255), 2)
                cv2.circle(viz, (cx, cy), 2, (0, 0, 255), -1)
                label = f"BALL ({best['x_norm']:.3f}, {best['y_norm']:.3f}) conf={best['confidence']:.2f}"
                cv2.putText(viz, label, (cx + 15, cy), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 1)
                status = f"DETECTED conf={best['confidence']:.2f} at ({cx},{cy})"
            else:
                status = f"NO BALL ({len(rects)} motion rects, {len(detections)} inferred)"

            # Frame info
            cv2.putText(viz, f"Frame {frame_index} @ {pos_ms}ms", (10, 25),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
            cv2.putText(viz, f"{len(rects)} motion rects", (10, 50),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1)

            cv2.imwrite(str(debug_dir / f"frame_{frame_index:03d}.png"), viz)
            if best is not None:
                detected_frames.append((frame_index, viz.copy()))
            print(f"  frame {frame_index:3d} @ {pos_ms:5d}ms -> {status}")

        elif frame_index >= frame_start:
            print(f"  frame {frame_index:3d} @ {pos_ms:5d}ms -> first frame (no prev)")

        prev_gray = gray.copy()
        frame_index += 1
        pos_ms += FRAME_STEP_MS

    cap.release()

    # ── Build result montage of detected frames ──
    if detected_frames:
        thumb_w, thumb_h = 400, 300
        cols = min(len(detected_frames), 5)
        rows = (len(detected_frames) + cols - 1) // cols
        montage = np.zeros((rows * thumb_h, cols * thumb_w, 3), dtype=np.uint8)

        for idx, (fidx, viz_img) in enumerate(detected_frames):
            r, c = divmod(idx, cols)
            thumb = cv2.resize(viz_img, (thumb_w, thumb_h))
            cv2.putText(thumb, f"#{fidx}", (5, 20),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)
            montage[r * thumb_h:(r + 1) * thumb_h, c * thumb_w:(c + 1) * thumb_w] = thumb

        result_path = debug_dir / "result_detected.png"
        cv2.imwrite(str(result_path), montage)
        print(f"\nResult montage ({len(detected_frames)} detections): {result_path}")

    print(f"\nDone! Debug images in: {debug_dir}")


if __name__ == "__main__":
    main()
