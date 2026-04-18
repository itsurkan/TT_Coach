"""
TableDetector YOLO (predict model) — export to _table_yolo_predict.json.

Usage:
    python scripts/run_table_yolo_predict.py                    # all videos
    python scripts/run_table_yolo_predict.py IMG_6332           # single video
"""

import argparse
import json
import sys
import time
from pathlib import Path

import cv2
import numpy as np
from ultralytics import YOLO

# ── Config ──
PROJECT_ROOT = Path(__file__).resolve().parent.parent
VIDEOS_DIR = PROJECT_ROOT / "app/src/main/assets/Videos"
MODEL_PATH = PROJECT_ROOT / "yolo_table_keypoints_predict/best.pt"

NUM_KEYPOINTS = 6
CONFIDENCE_THRESHOLD = 0.3
NUM_SAMPLES = 3  # number of frames to sample and average


def detect_table(model, video_path, num_samples=NUM_SAMPLES):
    """Run table detection on several frames and return the best/averaged result."""
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        print(f"  ERROR: Cannot open {video_path}")
        return None

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    duration_ms = int(total_frames / fps * 1000)

    print(f"  {width}x{height}, {fps:.1f}fps, {total_frames} frames, {duration_ms}ms")

    # Sample frames evenly across the video
    sample_indices = np.linspace(0, total_frames - 1, num_samples + 2, dtype=int)[1:-1]

    all_detections = []

    for frame_idx in sample_indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, int(frame_idx))
        ret, frame = cap.read()
        if not ret:
            continue

        results = model(frame, imgsz=640, conf=CONFIDENCE_THRESHOLD, verbose=False)[0]

        if results.keypoints is not None and len(results.keypoints) > 0:
            # Pick highest confidence detection
            best_conf = 0
            best_kps = None
            best_box = None
            for i in range(len(results.boxes)):
                conf = float(results.boxes[i].conf[0])
                if conf > best_conf:
                    best_conf = conf
                    best_kps = results.keypoints[i].data[0].cpu().numpy()  # (6, 3)
                    best_box = results.boxes[i].xyxy[0].cpu().numpy()

            if best_kps is not None:
                # Normalize keypoints to 0-1
                kps_norm = []
                for kp in best_kps:
                    x, y, kp_conf = float(kp[0]), float(kp[1]), float(kp[2])
                    kps_norm.append({
                        "x": round(x / width, 6),
                        "y": round(y / height, 6),
                        "confidence": round(kp_conf, 4),
                    })

                all_detections.append({
                    "frameIndex": int(frame_idx),
                    "confidence": round(best_conf, 4),
                    "keypoints": kps_norm,
                    "bbox": {
                        "x1": round(float(best_box[0]) / width, 6),
                        "y1": round(float(best_box[1]) / height, 6),
                        "x2": round(float(best_box[2]) / width, 6),
                        "y2": round(float(best_box[3]) / height, 6),
                    },
                })
                print(f"    frame {frame_idx}: DETECTED conf={best_conf:.2f}")
            else:
                print(f"    frame {frame_idx}: no keypoints")
        else:
            print(f"    frame {frame_idx}: no detection")

    cap.release()

    if not all_detections:
        print("  No table detected in any frame!")
        return None

    # Use the detection with highest confidence
    best = max(all_detections, key=lambda d: d["confidence"])

    # Also compute averaged keypoints from all detections for stability
    if len(all_detections) >= 2:
        avg_kps = []
        for ki in range(NUM_KEYPOINTS):
            xs = [d["keypoints"][ki]["x"] for d in all_detections if d["keypoints"][ki]["confidence"] > 0.3]
            ys = [d["keypoints"][ki]["y"] for d in all_detections if d["keypoints"][ki]["confidence"] > 0.3]
            if xs:
                avg_kps.append({
                    "x": round(float(np.mean(xs)), 6),
                    "y": round(float(np.mean(ys)), 6),
                    "confidence": round(float(np.mean([
                        d["keypoints"][ki]["confidence"] for d in all_detections
                        if d["keypoints"][ki]["confidence"] > 0.3
                    ])), 4),
                })
            else:
                avg_kps.append(best["keypoints"][ki])
        best["keypoints_averaged"] = avg_kps
        best["num_samples"] = len(all_detections)

    return best


def refine_keypoints(kps):
    """Refine 6 keypoints using the constraint that all points lie on a flat table surface.

    The table is a rectangle in 3D, so in 2D image space the 4 corners form a
    quadrilateral that is a perspective projection of a rectangle. The 2 net points
    lie on the midline of the table.

    Strategy:
    - Use the 4 corners to define the table plane (homography)
    - A real table has known proportions: 274cm x 152.5cm, net at midpoint
    - Fit homography from detected corners to ideal rectangle
    - For each point, check reprojection error
    - Correct outliers by projecting from ideal space back to image space
    """
    if len(kps) < 6:
        return kps

    # Table proportions (cm): length=274, width=152.5, net at center
    TABLE_L = 274.0
    TABLE_W = 152.5

    # Ideal rectangle corners (in "table space"):
    # pt1=farL(0,0), pt2=farR(W,0), pt3=nearR(W,L), pt4=nearL(0,L)
    # pt5=netL(0,L/2), pt6=netR(W,L/2)
    ideal_pts = np.array([
        [0, 0],             # pt1: far-left
        [TABLE_W, 0],       # pt2: far-right
        [TABLE_W, TABLE_L], # pt3: near-right
        [0, TABLE_L],       # pt4: near-left
        [0, TABLE_L / 2],   # pt5: net-left
        [TABLE_W, TABLE_L / 2],  # pt6: net-right
    ], dtype=np.float32)

    # Detected points
    det_pts = np.array([[kp["x"], kp["y"]] for kp in kps], dtype=np.float32)
    confs = np.array([kp["confidence"] for kp in kps])

    # Try leaving out each point, fit homography from remaining 5, measure reprojection error
    best_inlier_set = None
    best_error = float("inf")
    best_H = None

    for leave_out in range(6):
        # Use remaining points
        mask = [i for i in range(6) if i != leave_out and confs[i] > 0.1]
        if len(mask) < 4:
            continue

        src = ideal_pts[mask]
        dst = det_pts[mask]

        H, status = cv2.findHomography(src, dst, 0)
        if H is None:
            continue

        # Reproject ALL 6 ideal points
        ideal_h = np.hstack([ideal_pts, np.ones((6, 1))]).T  # 3x6
        proj = H @ ideal_h  # 3x6
        proj = (proj[:2] / proj[2:]).T  # 6x2

        # Compute reprojection error for the used points
        errors = np.sqrt(np.sum((proj[mask] - dst) ** 2, axis=1))
        total_error = np.mean(errors)

        if total_error < best_error:
            best_error = total_error
            best_H = H
            best_inlier_set = mask

    if best_H is None:
        return kps

    # Reproject all points using best homography
    ideal_h = np.hstack([ideal_pts, np.ones((6, 1))]).T
    proj = best_H @ ideal_h
    projected = (proj[:2] / proj[2:]).T  # 6x2

    # For each point: if reprojection error is large OR confidence is low, use projected position
    refined = []
    for i in range(6):
        det = det_pts[i]
        prj = projected[i]
        error = np.sqrt(np.sum((det - prj) ** 2))
        conf = confs[i]

        # Only correct if clearly an outlier: large error AND low confidence
        table_diag = np.sqrt((det_pts[0][0] - det_pts[2][0])**2 + (det_pts[0][1] - det_pts[2][1])**2)
        threshold = max(0.03, table_diag * 0.15)  # 15% of table diagonal

        if error > threshold and conf < 0.5:
            refined.append({
                "x": round(float(prj[0]), 6),
                "y": round(float(prj[1]), 6),
                "confidence": round(float(conf), 4),
                "refined": True,
            })
            print(f"    pt{i+1}: REFINED (err={error:.4f}, conf={conf:.2f})")
        else:
            refined.append({
                "x": kps[i]["x"],
                "y": kps[i]["y"],
                "confidence": kps[i]["confidence"],
                "refined": False,
            })

    return refined


def process_video(model, video_dir, num_samples):
    """Process one video folder and write _table_yolo_predict.json."""
    folder_name = video_dir.name

    # Find video file
    video_path = None
    for f in video_dir.iterdir():
        if f.suffix.lower() in (".mp4", ".mov", ".avi", ".webm"):
            video_path = f
            break
    if video_path is None:
        print(f"  SKIP: no video in {video_dir}")
        return

    out_path = video_dir / f"{folder_name}_table_yolo_predict.json"
    print(f"Processing: {folder_name}/{video_path.name}")

    t0 = time.time()
    detection = detect_table(model, video_path, num_samples)
    elapsed = time.time() - t0

    if detection is None:
        print(f"  No table found. Skipping.")
        return

    # Write JSON
    result = {
        "videoName": folder_name,
        "detector": "YOLOv11-nano-pose",
        "modelPath": str(MODEL_PATH),
        "videoWidth": int(cv2.VideoCapture(str(video_path)).get(cv2.CAP_PROP_FRAME_WIDTH)),
        "videoHeight": int(cv2.VideoCapture(str(video_path)).get(cv2.CAP_PROP_FRAME_HEIGHT)),
        "exportTimestamp": int(time.time() * 1000),
        "detection": detection,
    }

    with open(out_path, "w") as f:
        json.dump(result, f, indent=2)

    print(f"  -> {out_path.name} (conf={detection['confidence']:.2f}, {elapsed:.1f}s)")

    # Print keypoints
    kps = detection.get("keypoints_averaged", detection["keypoints"])
    for i, kp in enumerate(kps):
        print(f"    pt{i+1}: ({kp['x']:.4f}, {kp['y']:.4f}) conf={kp['confidence']:.2f}")


def main():
    parser = argparse.ArgumentParser(description="TableDetector YOLO — detect table keypoints")
    parser.add_argument("video_folder", nargs="?", default=None,
                        help="Video folder name. Omit to run all.")
    parser.add_argument("--samples", type=int, default=NUM_SAMPLES,
                        help=f"Number of frames to sample (default: {NUM_SAMPLES})")
    args = parser.parse_args()

    if not MODEL_PATH.exists():
        print(f"ERROR: model not found: {MODEL_PATH}")
        sys.exit(1)

    print(f"Loading model: {MODEL_PATH}")
    model = YOLO(str(MODEL_PATH))
    print("Model loaded.\n")

    if args.video_folder:
        video_dir = VIDEOS_DIR / args.video_folder
        if not video_dir.is_dir():
            print(f"ERROR: folder not found: {video_dir}")
            sys.exit(1)
        process_video(model, video_dir, args.samples)
    else:
        for folder in sorted(d for d in VIDEOS_DIR.iterdir() if d.is_dir()):
            process_video(model, folder, args.samples)
            print()

    print("\nDone!")


if __name__ == "__main__":
    main()
