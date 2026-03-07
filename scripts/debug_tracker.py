#!/usr/bin/env python3
"""
debug_tracker.py

Debug script for BallTracker — shows ALL intermediate pipeline steps plus
candidates, chains, and final confirmed detection per frame.

For each frame writes:
  01_original.png        – raw frame
  02_motion_diff.png     – absolute difference with previous frame
  03_motion_mask.png     – thresholded + opened + dilated motion mask
  04_motion_rects.png    – motion bounding boxes on original
  05_hsv_mask.png        – HSV color mask inside motion rects
  06_morph_mask.png      – after morphological open/close
  07_play_zone.png       – playing zone rectangle + all contour candidates
  08_candidates.png      – top-N candidates after scoring + play zone boost
  09_chains.png          – active chains drawn as colored polylines
  10_result.png          – confirmed detection (green) vs unconfirmed (yellow)

Output goes to: scripts/debug_tracker_frames/frame_NNN/

Usage:
    python scripts/debug_tracker.py
"""

import math
import os
import sys

import cv2
import numpy as np

from ball_tracker import (
    BallTracker, Candidate, Chain,
    MIN_CHAIN_SPEED_PX, TOP_N_CANDIDATES,
    PLAY_ZONE_X_MIN, PLAY_ZONE_X_MAX,
    PLAY_ZONE_Y_MIN, PLAY_ZONE_Y_MAX,
    PLAY_ZONE_BONUS,
)
from ball_detector import (
    MOTION_THRESHOLD, MOTION_DILATE_PX, MORPH_KERNEL_SIZE,
    MIN_CIRCULARITY, CONFIDENCE_CIRC_W, CONFIDENCE_SIZE_W,
)

# ── Config ────────────────────────────────────────────────────────────────────

SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.normpath(os.path.join(SCRIPTS_DIR, ".."))
VIDEO_PATH  = os.path.join(
    PROJECT_DIR, "app", "src", "main", "assets", "Videos", "video_2", "video_2.mp4"
)
OUTPUT_DIR  = os.path.join(SCRIPTS_DIR, "debug_tracker_frames")

INTERVAL_MS = 100
FRAME_START = 5
FRAME_END   = 30   # inclusive

# Chain colors (BGR) — one per chain for visualization
CHAIN_COLORS = [
    (0, 255, 0),    # green
    (0, 165, 255),  # orange
    (255, 0, 255),  # magenta
    (255, 255, 0),  # cyan
    (0, 255, 255),  # yellow
    (255, 0, 0),    # blue
    (128, 0, 255),  # purple
    (0, 128, 255),  # gold
]


def save(out_dir: str, name: str, img: np.ndarray):
    cv2.imwrite(os.path.join(out_dir, name), img)


def compute_intermediate_steps(tracker, frame_bgr, frame_index):
    """
    Manually run the detector pipeline to capture intermediate images.
    Returns dict with motion_diff, motion_mask, motion_rects, hsv_mask,
    morph_mask, table_mask, all_contours.
    """
    det = tracker.detector
    h, w = frame_bgr.shape[:2]
    gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)

    result = {}

    # Motion diff
    if det.prev_gray is not None:
        diff = cv2.absdiff(gray, det.prev_gray)
        _, motion_mask = cv2.threshold(diff, MOTION_THRESHOLD, 255, cv2.THRESH_BINARY)
        # Remove thin/elongated structures before dilation
        motion_mask = cv2.morphologyEx(motion_mask, cv2.MORPH_OPEN, det.morph_kernel)
        motion_dilated = cv2.dilate(motion_mask, det.motion_kernel)

        contours_motion, _ = cv2.findContours(
            motion_dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

        min_blob_area = math.pi * det.radius_min ** 2
        min_short = det.radius_min * 2
        max_short = int((det.radius_max + MOTION_DILATE_PX) * 2)

        rects = []
        for c in contours_motion:
            area = cv2.contourArea(c)
            if area < min_blob_area:
                continue
            x, y, bw, bh = cv2.boundingRect(c)
            short_side = min(bw, bh)
            if min_short <= short_side <= max_short:
                rects.append((x, y, bw, bh))

        result["motion_diff"] = diff
        result["motion_mask"] = motion_dilated
        result["motion_rects"] = rects
    else:
        result["motion_diff"] = np.zeros((h, w), dtype=np.uint8)
        result["motion_mask"] = np.zeros((h, w), dtype=np.uint8)
        result["motion_rects"] = [(0, 0, w, h)]

    # HSV + morph masks (inside motion rects)
    hsv_full = np.zeros((h, w), dtype=np.uint8)
    morph_full = np.zeros((h, w), dtype=np.uint8)
    all_contours = []

    for (rx, ry, rw, rh) in result["motion_rects"]:
        sub = frame_bgr[ry:ry + rh, rx:rx + rw]
        hsv = cv2.cvtColor(sub, cv2.COLOR_BGR2HSV)
        mask = cv2.inRange(hsv, det.hsv_lower, det.hsv_upper)
        hsv_full[ry:ry + rh, rx:rx + rw] = np.maximum(
            hsv_full[ry:ry + rh, rx:rx + rw], mask)

        morph = cv2.morphologyEx(mask, cv2.MORPH_OPEN, det.morph_kernel)
        morph = cv2.morphologyEx(morph, cv2.MORPH_CLOSE, det.morph_kernel)
        morph_full[ry:ry + rh, rx:rx + rw] = np.maximum(
            morph_full[ry:ry + rh, rx:rx + rw], morph)

        contours_hsv, _ = cv2.findContours(
            morph, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

        min_area = math.pi * det.radius_min ** 2
        max_area = math.pi * det.radius_max ** 2

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
            # Offset to full-frame
            contour_abs = contour.copy()
            contour_abs[:, :, 0] += rx
            contour_abs[:, :, 1] += ry
            all_contours.append(contour_abs)

    result["hsv_mask"] = hsv_full
    result["morph_mask"] = morph_full
    result["all_contours"] = all_contours

    return result


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

    tracker = BallTracker(color="white")

    for frame_idx in range(FRAME_END + 1):
        pos_ms = frame_idx * INTERVAL_MS
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame_bgr = cap.read()
        if not ret:
            print(f"  frame {frame_idx}: read failed, stopping")
            break

        h, w = frame_bgr.shape[:2]

        in_range = FRAME_START <= frame_idx <= FRAME_END

        # Capture intermediate steps BEFORE tracker.process() updates prev_gray
        if in_range:
            intermediate = compute_intermediate_steps(tracker, frame_bgr, frame_idx)

        # Run the tracker (this updates internal state)
        result = tracker.process(frame_bgr, frame_idx, pos_ms)

        if not in_range:
            continue

        fdir = os.path.join(OUTPUT_DIR, f"frame_{frame_idx:03d}")
        os.makedirs(fdir, exist_ok=True)

        candidates: list[Candidate] = result["_candidates"]
        chains: list[Chain] = result["_chains"]
        ball = result["ball"]

        # 01 - original
        save(fdir, "01_original.png", frame_bgr)

        # 02 - motion diff
        save(fdir, "02_motion_diff.png", intermediate["motion_diff"])

        # 03 - motion mask
        save(fdir, "03_motion_mask.png", intermediate["motion_mask"])

        # 04 - motion rects on original
        vis_rects = frame_bgr.copy()
        for (rx, ry, rw, rh) in intermediate["motion_rects"]:
            cv2.rectangle(vis_rects, (rx, ry), (rx + rw, ry + rh), (0, 255, 0), 2)
        cv2.putText(vis_rects, f"{len(intermediate['motion_rects'])} motion rect(s)",
                    (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)
        save(fdir, "04_motion_rects.png", vis_rects)

        # 05 - HSV mask
        save(fdir, "05_hsv_mask.png", intermediate["hsv_mask"])

        # 06 - morph mask (after morphological open/close)
        save(fdir, "06_morph_mask.png", intermediate["morph_mask"])

        # 07 - play zone + all contour candidates
        vis_zone = frame_bgr.copy()
        # Draw play zone rectangle
        pz_x1 = int(PLAY_ZONE_X_MIN * w)
        pz_x2 = int(PLAY_ZONE_X_MAX * w)
        pz_y1 = int(PLAY_ZONE_Y_MIN * h)
        pz_y2 = int(PLAY_ZONE_Y_MAX * h)
        cv2.rectangle(vis_zone, (pz_x1, pz_y1), (pz_x2, pz_y2), (255, 200, 0), 2)
        cv2.putText(vis_zone, "PLAY ZONE", (pz_x1 + 5, pz_y1 + 20),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 200, 0), 2)
        # Draw all valid contours
        cv2.drawContours(vis_zone, intermediate["all_contours"], -1, (255, 0, 255), 2)
        # Mark which contours are inside/outside play zone
        for contour in intermediate["all_contours"]:
            moments = cv2.moments(contour)
            if moments["m00"] > 0:
                cx = int(moments["m10"] / moments["m00"])
                cy = int(moments["m01"] / moments["m00"])
                in_zone = (pz_x1 <= cx <= pz_x2 and pz_y1 <= cy <= pz_y2)
                color = (0, 255, 0) if in_zone else (0, 0, 255)
                marker = "IN" if in_zone else "OUT"
                cv2.circle(vis_zone, (cx, cy), 5, color, -1)
                cv2.putText(vis_zone, marker, (cx + 8, cy),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.35, color, 1)
        n_contours = len(intermediate["all_contours"])
        cv2.putText(vis_zone, f"{n_contours} contour(s) + play zone", (10, 30),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 200, 0), 2)
        save(fdir, "07_play_zone.png", vis_zone)

        # 08 - top-N candidates after scoring + play zone boost
        vis_cand = frame_bgr.copy()
        # Draw play zone faintly
        cv2.rectangle(vis_cand, (pz_x1, pz_y1), (pz_x2, pz_y2), (255, 200, 0), 1)
        for i, c in enumerate(candidates):
            cx, cy = int(c.x_px), int(c.y_px)
            r = max(int(c.radius_px), 3)
            color = (0, 255, 255)  # yellow
            cv2.circle(vis_cand, (cx, cy), r, color, 2)
            label = f"#{i+1} c={c.confidence:.2f}"
            cv2.putText(vis_cand, label, (cx + r + 3, cy),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.4, color, 1)
        cv2.putText(vis_cand, f"{len(candidates)} candidate(s) (top {TOP_N_CANDIDATES})",
                    (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 255), 2)
        save(fdir, "08_candidates.png", vis_cand)

        # 09 - chains as polylines
        vis_chains = frame_bgr.copy()
        active_chains = [ch for ch in chains if ch.length >= 2]
        for i, chain in enumerate(active_chains):
            color = CHAIN_COLORS[i % len(CHAIN_COLORS)]
            pts = [(int(c.x_px), int(c.y_px)) for c in chain.candidates]

            if len(pts) >= 2:
                for j in range(1, len(pts)):
                    cv2.line(vis_chains, pts[j-1], pts[j], color, 2)

            for j, pt in enumerate(pts):
                cv2.circle(vis_chains, pt, 4, color, -1)

            last_pt = pts[-1]
            label = f"chain#{i+1} len={chain.length} spd={chain.avg_speed:.0f}"
            cv2.putText(vis_chains, label, (last_pt[0] + 8, last_pt[1]),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.4, color, 1)

        n_confirmed = sum(1 for ch in chains
                          if ch.length >= 3 and ch.avg_speed >= MIN_CHAIN_SPEED_PX)
        cv2.putText(vis_chains, f"{len(active_chains)} chain(s), {n_confirmed} confirmed",
                    (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
        save(fdir, "09_chains.png", vis_chains)

        # 10 - final result
        vis_result = frame_bgr.copy()
        if ball is not None:
            bx = int(ball["x"] * w)
            by = int(ball["y"] * h)
            br = max(int(ball["radiusPx"]), 3)
            chain_len = ball.get("chainLen", 0)

            cv2.circle(vis_result, (bx, by), br, (0, 255, 0), 2)
            cv2.drawMarker(vis_result, (bx, by), (0, 0, 255),
                           cv2.MARKER_CROSS, 20, 2)
            label = f"conf={ball['confidence']:.2f} chain={chain_len}"
            cv2.putText(vis_result, label, (bx + br + 5, by),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)
            cv2.putText(vis_result, "CONFIRMED", (10, 30),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)
        else:
            if candidates:
                c = candidates[0]
                cx, cy = int(c.x_px), int(c.y_px)
                r = max(int(c.radius_px), 3)
                cv2.circle(vis_result, (cx, cy), r, (0, 255, 255), 1)
                cv2.putText(vis_result, "UNCONFIRMED", (10, 30),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 255), 2)
            else:
                cv2.putText(vis_result, "NO DETECTION", (10, 30),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)
        save(fdir, "10_result.png", vis_result)

        # Print summary
        status = "CONFIRMED" if ball else ("unconf" if candidates else "---")
        chain_info = f"chain={ball['chainLen']}" if ball else ""
        n_cand = len(candidates)
        n_chains = len(active_chains)
        print(f"  frame {frame_idx:3d}  t={pos_ms:5d}ms  {status:10s}  "
              f"cands={n_cand}  chains={n_chains}  {chain_info}")

    cap.release()
    print(f"\nDone. Output in: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
