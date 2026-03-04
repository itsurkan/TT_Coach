#!/usr/bin/env python3
"""
debug_steps.py

Saves per-step visualisation images for a range of frames.

For each frame writes:
  1_original.png        – raw BGR frame
  2_motion_diff.png     – absolute difference with previous frame (grayscale)
  3_motion_mask.png     – thresholded + dilated motion mask
  4_motion_rects.png    – original with motion bounding boxes drawn
  5_hsv_mask.png        – HSV color mask (inside motion rects only)
  5b_table_mask.png     – static table exclusion mask (computed once on frame 0)
  6_morph_mask.png      – after table exclusion + morphological open/close
  7_contours.png        – all valid contours drawn on original
  8_result.png          – final detection: circle + crosshair on original

Also saves 0_table_mask.png once in the output root.

Output goes to:  scripts/debug_frames/frame_NNN/

Usage:
    python scripts/debug_steps.py
"""

import math
import os
import sys

import cv2
import numpy as np

from ball_detector import (
    BallDetector,
    HSV_RANGES,
    MIN_CIRCULARITY,
    CONFIDENCE_CIRC_W,
    CONFIDENCE_SIZE_W,
    MOTION_THRESHOLD,
    MOTION_DILATE_PX,
    MORPH_KERNEL_SIZE,
)

# ── Config ────────────────────────────────────────────────────────────────────

SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.normpath(os.path.join(SCRIPTS_DIR, ".."))
VIDEO_PATH  = os.path.join(
    PROJECT_DIR, "app", "src", "main", "assets", "Videos", "IMG_6370", "IMG_6370.MP4"
)
OUTPUT_DIR  = os.path.join(SCRIPTS_DIR, "debug_frames")

COLOR       = "white"
INTERVAL_MS = 100
FRAME_START = 120
FRAME_END   = 125   # inclusive

RADIUS_MIN  = 3
RADIUS_MAX  = 35


# ── Helpers ───────────────────────────────────────────────────────────────────

def save(out_dir: str, name: str, img: np.ndarray):
    path = os.path.join(out_dir, name)
    cv2.imwrite(path, img)


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    if not os.path.isfile(VIDEO_PATH):
        print(f"ERROR: video not found: {VIDEO_PATH}", file=sys.stderr)
        sys.exit(1)

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

    hsv_lower = np.array(HSV_RANGES[COLOR][0], dtype=np.uint8)
    hsv_upper = np.array(HSV_RANGES[COLOR][1], dtype=np.uint8)

    morph_kernel  = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (MORPH_KERNEL_SIZE, MORPH_KERNEL_SIZE))
    motion_kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (MOTION_DILATE_PX, MOTION_DILATE_PX))

    min_area = math.pi * RADIUS_MIN ** 2
    max_area = math.pi * RADIUS_MAX ** 2

    # Use BallDetector just to compute the static table mask from frame 0
    detector = BallDetector(color=COLOR, radius_range=(RADIUS_MIN, RADIUS_MAX))

    prev_gray = None
    table_mask_static = None

    for frame_idx in range(FRAME_END + 1):
        pos_ms = frame_idx * INTERVAL_MS
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame_bgr = cap.read()
        if not ret:
            print(f"  frame {frame_idx}: read failed, stopping")
            break

        h, w = frame_bgr.shape[:2]
        gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)

        # Compute table mask once on first frame
        if table_mask_static is None:
            table_mask_static = detector._detect_table(frame_bgr)
            # Save the table mask visualization
            vis_table = frame_bgr.copy()
            table_overlay = np.zeros_like(frame_bgr)
            table_overlay[:, :, 2] = table_mask_static  # red channel
            vis_table = cv2.addWeighted(vis_table, 0.7, table_overlay, 0.3, 0)
            # Draw contour outline
            contours_t, _ = cv2.findContours(table_mask_static, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            cv2.drawContours(vis_table, contours_t, -1, (0, 0, 255), 2)
            cv2.putText(vis_table, "TABLE EXCLUSION ZONE", (10, 30),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)
            save(OUTPUT_DIR, "0_table_mask.png", table_mask_static)
            save(OUTPUT_DIR, "0_table_overlay.png", vis_table)
            print(f"  Table mask computed from frame 0 — saved to 0_table_mask.png")

        in_range = FRAME_START <= frame_idx <= FRAME_END

        # ── Motion diff ───────────────────────────────────────────────────
        if prev_gray is not None:
            diff = cv2.absdiff(gray, prev_gray)
            _, motion_mask = cv2.threshold(diff, MOTION_THRESHOLD, 255, cv2.THRESH_BINARY)
            motion_dilated = cv2.dilate(motion_mask, motion_kernel)

            contours_motion, _ = cv2.findContours(motion_dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

            min_blob_area = math.pi * RADIUS_MIN ** 2
            min_short     = RADIUS_MIN * 2
            max_short     = int((RADIUS_MAX + MOTION_DILATE_PX) * 2)

            rects = []
            for c in contours_motion:
                area = cv2.contourArea(c)
                if area < min_blob_area:
                    continue
                x, y, bw, bh = cv2.boundingRect(c)
                short_side = min(bw, bh)
                if min_short <= short_side <= max_short:
                    rects.append((x, y, bw, bh))
        else:
            diff = np.zeros_like(gray)
            motion_mask = np.zeros_like(gray)
            motion_dilated = np.zeros_like(gray)
            rects = [(0, 0, w, h)]

        if in_range:
            fdir = os.path.join(OUTPUT_DIR, f"frame_{frame_idx:03d}")
            os.makedirs(fdir, exist_ok=True)

            # 1 - original
            save(fdir, "1_original.png", frame_bgr)

            # 2 - motion diff
            save(fdir, "2_motion_diff.png", diff)

            # 3 - motion mask (after threshold + dilate)
            save(fdir, "3_motion_mask.png", motion_dilated)

            # 4 - motion rects on original
            vis_rects = frame_bgr.copy()
            for (rx, ry, rw, rh) in rects:
                cv2.rectangle(vis_rects, (rx, ry), (rx + rw, ry + rh), (0, 255, 0), 2)
            cv2.putText(vis_rects, f"{len(rects)} motion rect(s)", (10, 30),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)
            save(fdir, "4_motion_rects.png", vis_rects)

            # 5, 5b, 6 - HSV mask, table exclusion applied, morph mask
            hsv_full   = np.zeros((h, w), dtype=np.uint8)
            morph_full = np.zeros((h, w), dtype=np.uint8)

            all_contours = []
            best_contour = None
            best_score   = -1.0
            best_rect_offset = (0, 0)

            for (rx, ry, rw, rh) in rects:
                sub = frame_bgr[ry:ry + rh, rx:rx + rw]
                hsv = cv2.cvtColor(sub, cv2.COLOR_BGR2HSV)
                mask = cv2.inRange(hsv, hsv_lower, hsv_upper)
                hsv_full[ry:ry + rh, rx:rx + rw] = np.maximum(
                    hsv_full[ry:ry + rh, rx:rx + rw], mask)

                # Apply static table mask
                table_sub = table_mask_static[ry:ry + rh, rx:rx + rw]
                mask = cv2.bitwise_and(mask, cv2.bitwise_not(table_sub))

                morph = cv2.morphologyEx(mask, cv2.MORPH_OPEN, morph_kernel)
                morph = cv2.morphologyEx(morph, cv2.MORPH_CLOSE, morph_kernel)
                morph_full[ry:ry + rh, rx:rx + rw] = np.maximum(
                    morph_full[ry:ry + rh, rx:rx + rw], morph)

                contours_hsv, _ = cv2.findContours(morph, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

                for contour in contours_hsv:
                    area = cv2.contourArea(contour)
                    if area < min_area or area > max_area:
                        continue
                    perimeter = cv2.arcLength(contour, True)
                    if perimeter <= 0:
                        continue
                    circularity = 4.0 * math.pi * area / (perimeter * perimeter)
                    if circularity < MIN_CIRCULARITY:
                        continue

                    # Offset contour to full-frame coords
                    contour_abs = contour.copy()
                    contour_abs[:, :, 0] += rx
                    contour_abs[:, :, 1] += ry
                    all_contours.append(contour_abs)

                    radius_est = math.sqrt(area / math.pi)
                    radius_mid = (RADIUS_MIN + RADIUS_MAX) / 2.0
                    size_score = 1.0 - abs(radius_est - radius_mid) / radius_mid
                    score = (CONFIDENCE_CIRC_W * circularity
                             + CONFIDENCE_SIZE_W * max(0.0, min(1.0, size_score)))

                    if score > best_score:
                        best_score   = score
                        best_contour = contour
                        best_rect_offset = (rx, ry)

            save(fdir, "5_hsv_mask.png", hsv_full)
            save(fdir, "6_morph_mask.png", morph_full)

            # 7 - all valid contours
            vis_contours = frame_bgr.copy()
            cv2.drawContours(vis_contours, all_contours, -1, (255, 0, 255), 2)
            cv2.putText(vis_contours, f"{len(all_contours)} candidate(s)", (10, 30),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 0, 255), 2)
            save(fdir, "7_contours.png", vis_contours)

            # 8 - final result
            vis_result = frame_bgr.copy()
            if best_contour is not None:
                moments = cv2.moments(best_contour)
                if moments["m00"] > 0:
                    cx = int(moments["m10"] / moments["m00"]) + best_rect_offset[0]
                    cy = int(moments["m01"] / moments["m00"]) + best_rect_offset[1]
                    best_area = cv2.contourArea(best_contour)
                    radius_px = int(math.sqrt(best_area / math.pi))

                    cv2.circle(vis_result, (cx, cy), radius_px, (0, 255, 0), 2)
                    cv2.drawMarker(vis_result, (cx, cy), (0, 0, 255), cv2.MARKER_CROSS, 20, 2)
                    label = f"conf={best_score:.2f}  r={radius_px}px"
                    cv2.putText(vis_result, label, (cx + radius_px + 5, cy),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
                    cv2.putText(vis_result, "DETECTED", (10, 30),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)
                else:
                    cv2.putText(vis_result, "NOT DETECTED", (10, 30),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)
            else:
                cv2.putText(vis_result, "NOT DETECTED", (10, 30),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)
            save(fdir, "8_result.png", vis_result)

            status = "DETECTED" if best_contour is not None else "---"
            print(f"  frame {frame_idx:3d}  t={pos_ms:5d}ms  {status:8s}  "
                  f"rects={len(rects)}  candidates={len(all_contours)}")

        prev_gray = gray

    cap.release()
    print(f"\nDone. Output in: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
