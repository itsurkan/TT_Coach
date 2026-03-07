"""
ball_tracker.py

Trajectory-aware ball tracker — wraps BallDetector with multi-candidate
chain matching across frames.

Instead of trusting the single best detection per frame, it:
  1. Keeps the top N candidates per frame (by score)
  2. Links candidates across frames using position + velocity prediction
  3. Only confirms detections that form consistent 3-frame chains
  4. Uses a simple linear predictor (position + velocity) to predict next position

Usage:
    from ball_tracker import BallTracker

    tracker = BallTracker(color="white")
    for frame in frames:
        result = tracker.process(frame_bgr, frame_index, timestamp_ms)
        # result = {"frameIndex": 0, "timestampMs": 0, "ball": {...} or None}

Requirements:
    pip install opencv-python numpy
"""

import math
from dataclasses import dataclass, field

import numpy as np
import cv2

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


# ── Constants ─────────────────────────────────────────────────────────────────

TOP_N_CANDIDATES      = 5       # keep top N candidates per frame
MAX_DIST_PX           = 150     # max pixel distance between consecutive detections
MAX_PRED_ERROR_PX     = 100     # max distance from predicted position (velocity-based)
CHAIN_MIN_LENGTH      = 3       # minimum chain length to confirm a detection
CHAIN_MAX_GAP         = 2       # allow up to N missing frames in a chain
MAX_VELOCITY_PX       = 200     # max reasonable ball velocity in px/frame
MIN_CHAIN_SPEED_PX    = 15      # min avg speed to confirm — rejects slow background noise

# Playing zone — normalized (0-1) region where the ball is expected
# Roughly: between the two players, above the table
PLAY_ZONE_X_MIN       = 0.05
PLAY_ZONE_X_MAX       = 0.95
PLAY_ZONE_Y_MIN       = 0.10   # top of frame (ball can go high)
PLAY_ZONE_Y_MAX       = 0.75   # below this is the near player / ball box
PLAY_ZONE_BONUS       = 0.3    # score bonus for candidates inside the zone


# ── Data classes ──────────────────────────────────────────────────────────────

@dataclass
class Candidate:
    """A single ball candidate detection in one frame."""
    x_px: float          # pixel x
    y_px: float          # pixel y
    x_norm: float        # normalized x (0-1)
    y_norm: float        # normalized y (0-1)
    radius_px: float
    confidence: float
    frame_index: int
    timestamp_ms: int


@dataclass
class Chain:
    """A trajectory chain of linked candidates across frames."""
    candidates: list[Candidate] = field(default_factory=list)
    vx: float = 0.0      # estimated velocity x (px/frame)
    vy: float = 0.0      # estimated velocity y (px/frame)
    last_frame: int = -1
    gap_count: int = 0    # consecutive frames without a match

    @property
    def length(self) -> int:
        return len(self.candidates)

    @property
    def last(self) -> Candidate:
        return self.candidates[-1]

    @property
    def avg_speed(self) -> float:
        """Average speed in px/frame across the chain."""
        if len(self.candidates) < 2:
            return 0.0
        total_dist = 0.0
        for i in range(1, len(self.candidates)):
            dx = self.candidates[i].x_px - self.candidates[i-1].x_px
            dy = self.candidates[i].y_px - self.candidates[i-1].y_px
            dt = self.candidates[i].frame_index - self.candidates[i-1].frame_index
            if dt > 0:
                total_dist += math.hypot(dx, dy) / dt
        return total_dist / (len(self.candidates) - 1)

    def predict_next(self) -> tuple[float, float]:
        """Predict next position using linear velocity."""
        gap = self.gap_count + 1  # predict ahead by gap+1 frames
        return (self.last.x_px + self.vx * gap,
                self.last.y_px + self.vy * gap)

    def add(self, c: Candidate):
        """Add a candidate to the chain, update velocity."""
        if self.candidates:
            dt = c.frame_index - self.last.frame_index
            if dt > 0:
                self.vx = (c.x_px - self.last.x_px) / dt
                self.vy = (c.y_px - self.last.y_px) / dt
        self.candidates.append(c)
        self.last_frame = c.frame_index
        self.gap_count = 0


# ── Ball Tracker ──────────────────────────────────────────────────────────────

class BallTracker:
    """Trajectory-aware ball tracker using multi-candidate chain matching."""

    def __init__(
        self,
        color: str = "white",
        radius_range: tuple[int, int] = (3, 35),
    ):
        self.detector = BallDetector(color=color, radius_range=radius_range)
        self.chains: list[Chain] = []
        self.confirmed_chain: Chain | None = None
        self.frame_width = 0
        self.frame_height = 0

    def reset(self):
        self.detector.reset()
        self.chains = []
        self.confirmed_chain = None

    def process(
        self,
        frame_bgr: np.ndarray,
        frame_index: int,
        timestamp_ms: int,
    ) -> dict:
        """
        Process a frame and return the tracked ball position.

        Returns dict with keys: frameIndex, timestampMs, ball (dict or None),
        plus debug info: candidates, chains.
        """
        h, w = frame_bgr.shape[:2]
        self.frame_width = w
        self.frame_height = h

        # Get top N candidates from the detector
        candidates = self._get_candidates(frame_bgr, frame_index, timestamp_ms)

        # Try to extend existing chains with new candidates
        self._match_candidates(candidates, frame_index)

        # Age out chains that haven't been matched
        self._age_chains(frame_index)

        # Find the best confirmed detection
        ball = self._get_best_confirmed(frame_index)

        return {
            "frameIndex":  frame_index,
            "timestampMs": timestamp_ms,
            "ball":        ball,
            "_candidates": candidates,
            "_chains":     self.chains,
        }

    # ── Get top N candidates ──────────────────────────────────────────────────

    def _get_candidates(
        self,
        frame_bgr: np.ndarray,
        frame_index: int,
        timestamp_ms: int,
    ) -> list[Candidate]:
        """Run the detector and return top N candidates (not just the best)."""
        h, w = frame_bgr.shape[:2]
        gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)

        # Table mask disabled — was too aggressive
        # if self.detector.table_mask is None:
        #     self.detector.table_mask = self.detector._detect_table(frame_bgr)

        # Get motion rects
        if self.detector.prev_gray is not None:
            search_rects = self.detector._motion_rects(gray, w, h)
        else:
            search_rects = [(0, 0, w, h)]

        self.detector.prev_gray = gray

        if not search_rects:
            return []

        # Collect all valid contours from all rects
        all_scored: list[Candidate] = []

        for rect in search_rects:
            scored = self._detect_all_in_rect(
                frame_bgr, rect, w, h, frame_index, timestamp_ms)
            all_scored.extend(scored)

        # Boost candidates inside the playing zone
        for c in all_scored:
            if (PLAY_ZONE_X_MIN <= c.x_norm <= PLAY_ZONE_X_MAX
                    and PLAY_ZONE_Y_MIN <= c.y_norm <= PLAY_ZONE_Y_MAX):
                c.confidence = min(1.0, c.confidence + PLAY_ZONE_BONUS)

        # Sort by confidence descending, keep top N
        all_scored.sort(key=lambda c: c.confidence, reverse=True)
        return all_scored[:TOP_N_CANDIDATES]

    def _detect_all_in_rect(
        self,
        frame_bgr: np.ndarray,
        rect: tuple[int, int, int, int],
        fw: int,
        fh: int,
        frame_index: int,
        timestamp_ms: int,
    ) -> list[Candidate]:
        """Like BallDetector._detect_in_rect but returns ALL valid candidates."""
        rx, ry, rw, rh = rect
        sub = frame_bgr[ry:ry + rh, rx:rx + rw]

        hsv = cv2.cvtColor(sub, cv2.COLOR_BGR2HSV)
        mask = cv2.inRange(hsv, self.detector.hsv_lower, self.detector.hsv_upper)

        # Exclude table
        if self.detector.table_mask is not None:
            table_sub = self.detector.table_mask[ry:ry + rh, rx:rx + rw]
            mask = cv2.bitwise_and(mask, cv2.bitwise_not(table_sub))

        morph = cv2.morphologyEx(mask, cv2.MORPH_OPEN, self.detector.morph_kernel)
        morph = cv2.morphologyEx(morph, cv2.MORPH_CLOSE, self.detector.morph_kernel)

        contours, _ = cv2.findContours(morph, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

        min_area = math.pi * self.detector.radius_min ** 2
        max_area = math.pi * self.detector.radius_max ** 2

        results = []

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

            moments = cv2.moments(contour)
            if moments["m00"] == 0:
                continue

            radius_est = math.sqrt(area / math.pi)
            radius_mid = (self.detector.radius_min + self.detector.radius_max) / 2.0
            size_score = 1.0 - abs(radius_est - radius_mid) / radius_mid
            score = (CONFIDENCE_CIRC_W * circularity
                     + CONFIDENCE_SIZE_W * max(0.0, min(1.0, size_score)))

            cx_sub = moments["m10"] / moments["m00"]
            cy_sub = moments["m01"] / moments["m00"]

            cx_px = rx + cx_sub
            cy_px = ry + cy_sub

            results.append(Candidate(
                x_px=cx_px,
                y_px=cy_px,
                x_norm=round(max(0.0, min(1.0, cx_px / fw)), 7),
                y_norm=round(max(0.0, min(1.0, cy_px / fh)), 7),
                radius_px=round(radius_est, 3),
                confidence=round(max(0.0, min(1.0, score)), 7),
                frame_index=frame_index,
                timestamp_ms=timestamp_ms,
            ))

        return results

    # ── Chain matching ────────────────────────────────────────────────────────

    def _match_candidates(self, candidates: list[Candidate], frame_index: int):
        """Match candidates to existing chains, start new chains for unmatched."""
        used_candidates = set()
        used_chains = set()

        # First pass: match candidates to chains by predicted position
        matches: list[tuple[float, int, int]] = []  # (cost, chain_idx, cand_idx)

        for ci, chain in enumerate(self.chains):
            pred_x, pred_y = chain.predict_next()
            for cj, cand in enumerate(candidates):
                dist = math.hypot(cand.x_px - pred_x, cand.y_px - pred_y)
                if dist <= MAX_PRED_ERROR_PX:
                    # Also check raw distance from last detection
                    raw_dist = math.hypot(
                        cand.x_px - chain.last.x_px,
                        cand.y_px - chain.last.y_px)
                    if raw_dist <= MAX_DIST_PX:
                        # Check velocity is reasonable
                        dt = frame_index - chain.last.frame_index
                        if dt > 0:
                            vx = (cand.x_px - chain.last.x_px) / dt
                            vy = (cand.y_px - chain.last.y_px) / dt
                            speed = math.hypot(vx, vy)
                            if speed <= MAX_VELOCITY_PX:
                                cost = dist - cand.confidence * 50
                                matches.append((cost, ci, cj))

        # Greedy assignment: lowest cost first
        matches.sort()
        for cost, ci, cj in matches:
            if ci in used_chains or cj in used_candidates:
                continue
            self.chains[ci].add(candidates[cj])
            used_chains.add(ci)
            used_candidates.add(cj)

            # Promote to confirmed if long enough and fast enough
            chain = self.chains[ci]
            if (chain.length >= CHAIN_MIN_LENGTH
                    and chain.avg_speed >= MIN_CHAIN_SPEED_PX
                    and (self.confirmed_chain is None
                         or chain.length > self.confirmed_chain.length)):
                self.confirmed_chain = chain

        # Start new chains for unmatched candidates
        for cj, cand in enumerate(candidates):
            if cj not in used_candidates:
                chain = Chain()
                chain.add(cand)
                self.chains.append(chain)

    def _age_chains(self, frame_index: int):
        """Increment gap counters, remove dead chains."""
        alive = []
        for chain in self.chains:
            if chain.last_frame < frame_index:
                chain.gap_count += 1
                if chain.gap_count > CHAIN_MAX_GAP:
                    # Chain is dead
                    if chain is self.confirmed_chain:
                        self.confirmed_chain = None
                    continue
            alive.append(chain)
        self.chains = alive

    # ── Output ────────────────────────────────────────────────────────────────

    def _get_best_confirmed(self, frame_index: int) -> dict | None:
        """Return the detection from the best confirmed chain for this frame."""
        # Prefer the confirmed chain if it has a detection for this frame
        if self.confirmed_chain is not None:
            if self.confirmed_chain.avg_speed < MIN_CHAIN_SPEED_PX:
                self.confirmed_chain = None  # demote slow chains
            else:
                last = self.confirmed_chain.last
                if last.frame_index == frame_index:
                    return {
                        "x":          last.x_norm,
                        "y":          last.y_norm,
                        "radiusPx":   last.radius_px,
                        "confidence": last.confidence,
                        "status":     "DETECTED",
                        "chainLen":   self.confirmed_chain.length,
                    }

        # Otherwise check all chains with enough length and speed
        best_chain = None
        for chain in self.chains:
            if (chain.length >= CHAIN_MIN_LENGTH
                    and chain.avg_speed >= MIN_CHAIN_SPEED_PX
                    and chain.last.frame_index == frame_index):
                if best_chain is None or chain.length > best_chain.length:
                    best_chain = chain

        if best_chain is not None:
            self.confirmed_chain = best_chain
            last = best_chain.last
            return {
                "x":          last.x_norm,
                "y":          last.y_norm,
                "radiusPx":   last.radius_px,
                "confidence": last.confidence,
                "status":     "DETECTED",
                "chainLen":   best_chain.length,
            }

        return None
