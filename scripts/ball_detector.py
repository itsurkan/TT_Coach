"""
ball_detector.py

Motion-first ball detector — Python port of BallDetectorV3.kt.

Detects a table tennis ball by:
  1. Detect table on first frame (static exclusion mask)
  2. Frame-differencing to find motion regions
  3. HSV color thresholding only inside those regions (minus table)
  4. Contour analysis (area, circularity) to pick the best candidate

Usage:
    from ball_detector import BallDetector

    detector = BallDetector(color="white")
    result = detector.detect(frame_bgr, frame_index=0, timestamp_ms=0)
    # result = {"frameIndex": 0, "timestampMs": 0, "ball": {...} or None}

Requirements:
    pip install opencv-python numpy
"""

import math

import numpy as np
import cv2


# ── Constants (matching BallDetectorV3.kt) ────────────────────────────────────

HSV_RANGES = {
    "white":  ((0, 0, 170),   (180, 70, 255)),
    "orange": ((5, 100, 100), (25, 255, 255)),
}

MIN_CIRCULARITY       = 0.20
CONFIDENCE_CIRC_W     = 0.6
CONFIDENCE_SIZE_W     = 0.4

MOTION_THRESHOLD      = 100
MOTION_DILATE_PX      = 10
MORPH_KERNEL_SIZE     = 5

# Table exclusion — detect table surface on first frame (auto-detect color)
# Supports green tables (H≈30-95) and blue/purple tables (H≈100-160)
TABLE_HSV_RANGES = [
    ((30, 15, 20),   (95, 255, 230)),    # green/teal
    ((100, 30, 30),  (160, 255, 255)),   # blue/purple
]
TABLE_DILATE_PX       = 15
TABLE_MIN_AREA_FRAC   = 0.03   # table must be at least 3% of frame area


# ── Ball Detector ─────────────────────────────────────────────────────────────

class BallDetector:
    """Motion-first ball detector — Python port of BallDetectorV3.kt."""

    def __init__(
        self,
        color: str = "white",
        radius_range: tuple[int, int] = (3, 35),
    ):
        self.hsv_lower = np.array(HSV_RANGES[color][0], dtype=np.uint8)
        self.hsv_upper = np.array(HSV_RANGES[color][1], dtype=np.uint8)
        self.radius_min, self.radius_max = radius_range

        self.morph_kernel  = cv2.getStructuringElement(
            cv2.MORPH_ELLIPSE, (MORPH_KERNEL_SIZE, MORPH_KERNEL_SIZE))
        self.motion_kernel = cv2.getStructuringElement(
            cv2.MORPH_ELLIPSE, (MOTION_DILATE_PX, MOTION_DILATE_PX))
        self.table_kernel  = cv2.getStructuringElement(
            cv2.MORPH_ELLIPSE, (TABLE_DILATE_PX, TABLE_DILATE_PX))
        self.table_hsv_ranges = [
            (np.array(lo, dtype=np.uint8), np.array(hi, dtype=np.uint8))
            for lo, hi in TABLE_HSV_RANGES
        ]

        self.prev_gray: np.ndarray | None = None
        self.table_mask: np.ndarray | None = None  # static, computed once

    def reset(self):
        self.prev_gray = None
        self.table_mask = None

    def detect(self, frame_bgr: np.ndarray, frame_index: int, timestamp_ms: int) -> dict:
        """
        Detect ball in a BGR frame.

        Returns a dict with keys: frameIndex, timestampMs, ball (dict or None).
        """
        h, w = frame_bgr.shape[:2]
        gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)

        # Detect table once on first frame
        if self.table_mask is None:
            self.table_mask = self._detect_table(frame_bgr)

        # Determine search rects — motion boxes or full frame on first frame
        if self.prev_gray is not None:
            search_rects = self._motion_rects(gray, w, h)
        else:
            search_rects = [(0, 0, w, h)]

        self.prev_gray = gray

        if not search_rects:
            return self._result(frame_index, timestamp_ms, None)

        # Run HSV detection inside each motion rect, keep best
        best = None
        for rect in search_rects:
            det = self._detect_in_rect(frame_bgr, rect, w, h)
            if det is not None and (best is None or det["confidence"] > best["confidence"]):
                best = det

        return self._result(frame_index, timestamp_ms, best)

    # ── Table detection (once) ────────────────────────────────────────────────

    def _detect_table(self, frame_bgr: np.ndarray) -> np.ndarray:
        """Detect the table surface on the first frame. Returns a binary mask (255=table).

        Tries multiple HSV ranges (green, blue/purple) and picks the one that
        produces the largest table region.
        """
        h, w = frame_bgr.shape[:2]
        hsv = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2HSV)
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7))
        min_area = h * w * TABLE_MIN_AREA_FRAC

        best_mask = np.zeros((h, w), dtype=np.uint8)
        best_coverage = 0

        for hsv_lo, hsv_hi in self.table_hsv_ranges:
            raw_mask = cv2.inRange(hsv, hsv_lo, hsv_hi)
            clean = cv2.morphologyEx(raw_mask, cv2.MORPH_OPEN, kernel)
            clean = cv2.morphologyEx(clean, cv2.MORPH_CLOSE, kernel)

            contours, _ = cv2.findContours(clean, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

            table_points = []
            for c in contours:
                if cv2.contourArea(c) >= min_area:
                    table_points.append(c)

            if not table_points:
                continue

            mask = np.zeros((h, w), dtype=np.uint8)
            all_pts = np.vstack(table_points)
            hull = cv2.convexHull(all_pts)
            cv2.drawContours(mask, [hull], -1, 255, cv2.FILLED)
            mask = cv2.dilate(mask, self.table_kernel)

            coverage = np.count_nonzero(mask)
            if coverage > best_coverage:
                best_coverage = coverage
                best_mask = mask

        return best_mask

    # ── Motion bounding rects ────────────────────────────────────────────────

    def _motion_rects(self, gray: np.ndarray, fw: int, fh: int) -> list[tuple[int, int, int, int]]:
        diff = cv2.absdiff(gray, self.prev_gray)
        _, motion_mask = cv2.threshold(diff, MOTION_THRESHOLD, 255, cv2.THRESH_BINARY)
        motion_mask = cv2.dilate(motion_mask, self.motion_kernel)

        contours, _ = cv2.findContours(motion_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

        min_blob_area = math.pi * self.radius_min ** 2
        min_short     = self.radius_min * 2
        max_short     = int((self.radius_max + MOTION_DILATE_PX) * 2)

        rects = []
        for c in contours:
            area = cv2.contourArea(c)
            if area < min_blob_area:
                continue
            x, y, bw, bh = cv2.boundingRect(c)
            short_side = min(bw, bh)
            if min_short <= short_side <= max_short:
                rects.append((x, y, bw, bh))

        return rects

    # ── HSV detection inside one rect ────────────────────────────────────────

    def _detect_in_rect(
        self,
        frame_bgr: np.ndarray,
        rect: tuple[int, int, int, int],
        fw: int,
        fh: int,
    ) -> dict | None:
        rx, ry, rw, rh = rect
        sub = frame_bgr[ry:ry + rh, rx:rx + rw]

        hsv = cv2.cvtColor(sub, cv2.COLOR_BGR2HSV)
        mask = cv2.inRange(hsv, self.hsv_lower, self.hsv_upper)

        # Exclude table region (static mask computed on first frame)
        if self.table_mask is not None:
            table_sub = self.table_mask[ry:ry + rh, rx:rx + rw]
            mask = cv2.bitwise_and(mask, cv2.bitwise_not(table_sub))

        # Morphological open then close
        morph = cv2.morphologyEx(mask, cv2.MORPH_OPEN, self.morph_kernel)
        morph = cv2.morphologyEx(morph, cv2.MORPH_CLOSE, self.morph_kernel)

        contours, _ = cv2.findContours(morph, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

        min_area = math.pi * self.radius_min ** 2
        max_area = math.pi * self.radius_max ** 2

        best_contour = None
        best_score   = -1.0

        for contour in contours:
            area = cv2.contourArea(contour)
            if area < min_area or area > max_area:
                continue

            perimeter = cv2.arcLength(contour, True)
            if perimeter <= 0:
                continue

            circularity = 4.0 * math.pi * area / (perimeter * perimeter)
            if circularity < MIN_CIRCULARITY:
                continue

            radius_est = math.sqrt(area / math.pi)
            radius_mid = (self.radius_min + self.radius_max) / 2.0
            size_score = 1.0 - abs(radius_est - radius_mid) / radius_mid
            score = CONFIDENCE_CIRC_W * circularity + CONFIDENCE_SIZE_W * max(0.0, min(1.0, size_score))

            if score > best_score:
                best_score   = score
                best_contour = contour

        if best_contour is None:
            return None

        moments = cv2.moments(best_contour)
        if moments["m00"] == 0:
            return None

        best_area = cv2.contourArea(best_contour)
        cx_sub = moments["m10"] / moments["m00"]
        cy_sub = moments["m01"] / moments["m00"]
        radius_px = math.sqrt(best_area / math.pi)

        cx_norm = (rx + cx_sub) / fw
        cy_norm = (ry + cy_sub) / fh

        return {
            "x":          round(max(0.0, min(1.0, cx_norm)), 7),
            "y":          round(max(0.0, min(1.0, cy_norm)), 7),
            "radiusPx":   round(radius_px, 3),
            "confidence": round(max(0.0, min(1.0, best_score)), 7),
            "status":     "DETECTED",
        }

    # ── Result formatting ────────────────────────────────────────────────────

    @staticmethod
    def _result(frame_index: int, timestamp_ms: int, ball: dict | None) -> dict:
        return {
            "frameIndex":  frame_index,
            "timestampMs": timestamp_ms,
            "ball":        ball,
        }
