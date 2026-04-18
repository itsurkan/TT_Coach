"""
BallDetector YOLO debug — saves annotated frames with detection results.

For each frame, saves ONE image showing:
  - The full frame (top-half ROI)
  - Green bboxes = all detections
  - Lime circle + label = best ball detection
  - Frame info overlay (frame index, timestamp, confidence)

Usage:
    python scripts/run_ball_yolo_debug.py IMG_6330
    python scripts/run_ball_yolo_debug.py IMG_6330 --frames 30 50
    python scripts/run_ball_yolo_debug.py IMG_6330 --region top       # top half (default)
    python scripts/run_ball_yolo_debug.py IMG_6330 --region bottom    # bottom half
    python scripts/run_ball_yolo_debug.py IMG_6330 --region center    # center 50%
    python scripts/run_ball_yolo_debug.py IMG_6330 --region full      # full frame
    python scripts/run_ball_yolo_debug.py IMG_6330 --coco             # use base COCO model (80 classes)
    python scripts/run_ball_yolo_debug.py IMG_6330 --world            # YOLO-World (open vocabulary)
"""

import argparse
from pathlib import Path

import cv2
import numpy as np
from ultralytics import YOLO

# ── Config ──
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
VIDEOS_DIR = PROJECT_ROOT / "Videos"
YOLO_MODEL_PATH = PROJECT_ROOT / "models/trained/best_yolo.pt"
COCO_MODEL_PATH = PROJECT_ROOT / "models/pretrained/yolo11n.pt"

FRAME_STEP_MS = 100
CONFIDENCE_THRESHOLD = 0.25

# Colors (BGR)
COLOR_LIME = (53, 230, 163)
COLOR_GREEN = (0, 255, 0)
COLOR_RED = (0, 0, 255)
COLOR_CYAN = (212, 182, 6)
COLOR_YELLOW = (0, 255, 255)
COLOR_WHITE = (255, 255, 255)
COLOR_GRAY = (200, 200, 200)


def find_video_file(folder: Path) -> Path:
    for f in folder.iterdir():
        if f.suffix.lower() in (".mp4", ".mov", ".avi"):
            return f
    raise FileNotFoundError(f"No video file found in {folder}")


def main():
    parser = argparse.ArgumentParser(description="BallDetector YOLO debug frames")
    parser.add_argument("video_folder", help="Video folder name (e.g. IMG_6330)")
    parser.add_argument("--frames", nargs=2, type=int, default=[0, 50],
                        metavar=("START", "END"), help="Frame range (default: 0 50)")
    parser.add_argument("--region", choices=["top", "bottom", "center", "full"], default="top",
                        help="Which part of the frame to analyze (default: top)")
    parser.add_argument("--coco", action="store_true",
                        help="Use base COCO model (80 classes: person, table, racket, etc.)")
    parser.add_argument("--world", action="store_true",
                        help="Use YOLO-World open-vocabulary model (TT ball, paddle, table, person)")
    parser.add_argument("--conf", type=float, default=CONFIDENCE_THRESHOLD,
                        help=f"Confidence threshold (default: {CONFIDENCE_THRESHOLD})")
    args = parser.parse_args()

    frame_start, frame_end = args.frames

    video_dir = VIDEOS_DIR / args.video_folder
    if not video_dir.is_dir():
        print(f"ERROR: folder not found: {video_dir}")
        return

    video_path = find_video_file(video_dir)

    # Choose model
    if args.world:
        print("Loading YOLO-World (open vocabulary)...")
        model = YOLO("yolov8s-world.pt")
        model.set_classes(["table tennis ball", "table tennis paddle", "table tennis table", "person"])
        debug_dir = video_dir / f"debug_yolo_world_{args.region}"
        print(f"Classes: {model.names}")
    elif args.coco:
        model_path = COCO_MODEL_PATH
        if not model_path.exists():
            print(f"Downloading COCO model...")
            model = YOLO("yolo11n.pt")
        else:
            model = YOLO(str(model_path))
        debug_dir = video_dir / f"debug_yolo_coco_{args.region}"
        print(f"Using COCO model (80 classes)")
    else:
        if not YOLO_MODEL_PATH.exists():
            print(f"ERROR: model not found: {YOLO_MODEL_PATH}")
            return
        model = YOLO(str(YOLO_MODEL_PATH))
        debug_dir = video_dir / f"debug_yolo_{args.region}"
        print(f"Using ball model: {YOLO_MODEL_PATH}")

    debug_dir.mkdir(parents=True, exist_ok=True)

    # Get class names
    class_names = model.names
    print(f"Classes: {class_names}")

    cap = cv2.VideoCapture(str(video_path))
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_video_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration_ms = int(total_video_frames / fps * 1000)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"Video: {video_path.name} {width}x{height}, {fps:.1f}fps, ~{duration_ms}ms")
    # Compute crop region
    if args.region == "top":
        y_start, y_end = 0, height // 2
    elif args.region == "bottom":
        y_start, y_end = height // 2, height
    elif args.region == "center":
        y_start, y_end = height // 4, 3 * height // 4
    else:  # full
        y_start, y_end = 0, height

    print(f"Region: {args.region} (y={y_start}..{y_end})")
    print(f"Frames: {frame_start} to {frame_end}\n")

    frame_index = 0
    pos_ms = 0
    detected_frames = []

    while pos_ms <= duration_ms and frame_index <= frame_end:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if not ret:
            pos_ms += FRAME_STEP_MS
            frame_index += 1
            continue

        if frame_index >= frame_start:
            # Crop region
            region = frame[y_start:y_end]

            # Run YOLO
            results = model(region, imgsz=320, conf=args.conf, verbose=False)[0]

            # Draw on visualization
            viz = region.copy()

            detections = []
            for box in results.boxes:
                x1, y1, x2, y2 = box.xyxy[0].cpu().numpy().astype(int)
                conf = float(box.conf[0])
                cls_id = int(box.cls[0])
                cls_name = class_names.get(cls_id, f"cls{cls_id}")
                detections.append({
                    "x1": x1, "y1": y1, "x2": x2, "y2": y2,
                    "conf": conf, "cls_id": cls_id, "cls_name": cls_name,
                })

            # Draw all detections
            for det in detections:
                x1, y1, x2, y2 = det["x1"], det["y1"], det["x2"], det["y2"]
                conf = det["conf"]
                cls_name = det["cls_name"]

                # Color by class
                cls_lower = cls_name.lower()
                if "ball" in cls_lower:
                    color = COLOR_LIME
                elif "person" in cls_lower:
                    color = COLOR_CYAN
                elif "paddle" in cls_lower or "racket" in cls_lower:
                    color = COLOR_YELLOW
                elif "table" in cls_lower:
                    color = COLOR_RED
                else:
                    color = COLOR_GREEN

                # Bbox
                cv2.rectangle(viz, (x1, y1), (x2, y2), color, 2)

                # Label
                label = f"{cls_name} {conf:.2f}"
                label_size, _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
                cv2.rectangle(viz, (x1, y1 - label_size[1] - 6), (x1 + label_size[0], y1), color, -1)
                cv2.putText(viz, label, (x1, y1 - 4),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1)

                # Center dot for ball class
                if cls_name == "ball":
                    cx = (x1 + x2) // 2
                    cy = (y1 + y2) // 2
                    cv2.circle(viz, (cx, cy), 4, COLOR_LIME, -1)

            # Frame info overlay
            cv2.putText(viz, f"Frame {frame_index} @ {pos_ms}ms", (10, 25),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, COLOR_WHITE, 2)
            cv2.putText(viz, f"{len(detections)} detections", (10, 50),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.5, COLOR_GRAY, 1)

            cv2.imwrite(str(debug_dir / f"frame_{frame_index:03d}.png"), viz)

            # Status
            ball_dets = [d for d in detections if d["cls_name"] == "ball"]
            if ball_dets:
                best = max(ball_dets, key=lambda d: d["conf"])
                cx = (best["x1"] + best["x2"]) // 2
                cy = (best["y1"] + best["y2"]) // 2
                status = f"BALL conf={best['conf']:.2f} at ({cx},{cy})"
                detected_frames.append((frame_index, viz.copy()))
            else:
                other = [d["cls_name"] for d in detections]
                status = f"NO BALL ({', '.join(other) if other else 'nothing'})"

            print(f"  frame {frame_index:3d} @ {pos_ms:5d}ms -> {status}")

        frame_index += 1
        pos_ms += FRAME_STEP_MS

    cap.release()

    # Build result montage of detected frames
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
