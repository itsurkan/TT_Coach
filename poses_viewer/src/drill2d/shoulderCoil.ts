/**
 * Shoulder coil indicator — QUALITATIVE PROXY ONLY, never a degree value.
 *
 * On a side camera, trunk rotation cannot be measured in degrees. What IS visible
 * is projected shoulder WIDTH: the shoulder line FORESHORTENS (narrows) when the
 * player coils into the backswing, then OPENS (widens) as they rotate through the
 * follow-through. The within-rep ratio of follow-through width to backswing width
 * is therefore a low-confidence hint at whether the player coiled and then released.
 *
 * Design constraints (trust rule):
 *   - Rotational cues are QUALITATIVE-ONLY or silent — no degree output.
 *   - This is LOW-CONFIDENCE: camera distance and residual yaw affect the absolute
 *     widths, though the within-rep ratio partly cancels those confounds.
 *   - Output is CoilLabel ('opened' | 'limited'), never a number shown to the user.
 *   - This module is intentionally SEPARATE from the numeric metric pipeline
 *     (ALL_KEYS / METRIC_PHASES / PER_PHASE_RANGES) to prevent the °-formatter and
 *     severity coloring from ever reaching it.
 */

import { Coco17, Keypoint2D, PoseFrame2D, StrokeCycle2D } from './types'
import { scored } from './facing'
import { DEFAULT_MIN_SCORE } from './facing'
import { median } from './median'

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/**
 * PROVISIONAL threshold: follow-through width / backswing width >= this → 'opened'.
 * Seed value ~1.25 — tune on protocol footage (side camera, known-good coil).
 * The within-rep ratio partially cancels the constant camera-yaw / distance confound,
 * but absolute width still depends on torso depth, so this is LOW-CONFIDENCE.
 */
export const COIL_OPENED_RATIO = 1.25

/** Half-window around each anchor for the robust-median width estimate (ms). */
export const DEFAULT_COIL_RADIUS_MS = 70

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** Qualitative coil label — the only value this module exposes to the UI. */
export type CoilLabel = 'opened' | 'limited'

// ---------------------------------------------------------------------------
// Core helpers
// ---------------------------------------------------------------------------

/**
 * Projected shoulder width at one frame: |RIGHT_SHOULDER.x - LEFT_SHOULDER.x| * xScale.
 * Returns null when either shoulder keypoint is missing or below minScore.
 * Uses the Coco17 LEFT_SHOULDER / RIGHT_SHOULDER accessors, mirroring how
 * shoulderTilt in angles2d.ts reads the same pair.
 */
export function shoulderWidth(
  kp: Keypoint2D[],
  xScale: number,
  minScore = DEFAULT_MIN_SCORE,
): number | null {
  const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
  const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
  if (ls === null || rs === null) return null
  return Math.abs(rs.x - ls.x) * xScale
}

/**
 * Median shoulder width over a ±radiusMs window around anchorFrame.
 * Mirrors the extractAtPeak window logic from drillMetrics.ts:
 *   radius = Math.trunc(radiusMs / intervalMs); window = [anchor - radius, anchor + radius].
 * Returns null when no valid (ungated) width exists in the window.
 */
function medianWidthAround(
  frames: PoseFrame2D[],
  anchorFrame: number,
  xScale: number,
  intervalMs: number,
  minScore: number,
  radiusMs: number,
): number | null {
  const radius = Math.trunc(radiusMs / intervalMs)
  const lo = Math.max(anchorFrame - radius, 0)
  const hi = Math.min(anchorFrame + radius, frames.length - 1)
  const widths: number[] = []
  for (let i = lo; i <= hi; i++) {
    const frame = frames[i]
    if (frame.keypoints.length === 0) continue
    const w = shoulderWidth(frame.keypoints, xScale, minScore)
    if (w !== null) widths.push(w)
  }
  if (widths.length === 0) return null
  return median(widths)
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Estimate whether the player coiled and opened for one stroke cycle.
 *
 * Anchors:
 *   backswing-end / drive-start → cycle.drive.startFrame  (narrowest expected width)
 *   follow-through              → cycle.drive.endFrame     (widest expected width)
 *
 * Each anchor uses a ±radiusMs median window (same strategy as extractAtPeak).
 *
 * Returns null when:
 *   - cycle.backswing is null (no real turn to measure against — unpaired drive only)
 *   - either anchor has no valid shoulder width in its window (gated or empty frames)
 *
 * @param cycle      Full stroke cycle (must have backswing != null to be meaningful)
 * @param frames     Full frame list for the sequence
 * @param xScale     ViewGeometry.xScale for this rep (correct x-axis normalization)
 * @param intervalMs Frame interval (ms) — used to compute the radius in frames
 * @param minScore   Score gate threshold (default 0.3)
 * @param radiusMs   Half-window around each anchor (default 70 ms, same as extractAtPeak)
 */
export function estimateCoil(
  cycle: StrokeCycle2D,
  frames: PoseFrame2D[],
  xScale: number,
  intervalMs: number,
  minScore = DEFAULT_MIN_SCORE,
  radiusMs = DEFAULT_COIL_RADIUS_MS,
): { ratio: number; label: CoilLabel } | null {
  // Without a backswing anchor there is no reference "coiled" state to compare against.
  if (cycle.backswing === null) return null

  const backswingAnchor = cycle.drive.startFrame   // drive-start = backswing-end
  const followthroughAnchor = cycle.drive.endFrame

  const widthBackswing = medianWidthAround(frames, backswingAnchor, xScale, intervalMs, minScore, radiusMs)
  const widthFollowthrough = medianWidthAround(frames, followthroughAnchor, xScale, intervalMs, minScore, radiusMs)

  // A zero projected width (or null) is unmeasurable — treat as a degenerate/glitch
  // case rather than genuine data. Guard is symmetric: either anchor being zero or
  // null aborts the coil estimate.
  if (widthBackswing === null || widthBackswing === 0) return null
  if (widthFollowthrough === null || widthFollowthrough === 0) return null

  const ratio = widthFollowthrough / widthBackswing
  const label: CoilLabel = ratio >= COIL_OPENED_RATIO ? 'opened' : 'limited'
  return { ratio, label }
}
