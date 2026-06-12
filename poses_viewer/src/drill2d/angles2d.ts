/**
 * In-plane (2D) joint angles over COCO-17 — 1:1 TS mirror of Kotlin
 * AngleCalculations2D. Kotlin is the source of truth (binding fix-flow rule).
 *
 * All functions: return null when any required keypoint is missing/below minScore
 * (no feedback on low-confidence frames); take xScale = ViewGeometry.xScale and
 * multiply x-deltas by it before any trig (schema v2 normalizes x and y by
 * different axes).
 */
import { Coco17, Handedness, Keypoint2D } from './types'
import { DEFAULT_MIN_SCORE, facingSign, scored } from './facing'

const RAD_TO_DEG = 180 / Math.PI
const EPSILON = 1e-9

/** Inner angle at b formed by b→a and b→c, in degrees [0, 180]. */
export function angleDeg(a: Keypoint2D, b: Keypoint2D, c: Keypoint2D, xScale: number): number {
  const baX = (a.x - b.x) * xScale
  const baY = a.y - b.y
  const bcX = (c.x - b.x) * xScale
  const bcY = c.y - b.y
  const mag = Math.hypot(baX, baY) * Math.hypot(bcX, bcY)
  if (mag < EPSILON) return 0
  const cos = Math.min(1, Math.max(-1, (baX * bcX + baY * bcY) / mag))
  return Math.acos(cos) * RAD_TO_DEG
}

function jointAngle(
  kp: Keypoint2D[], aIdx: number, bIdx: number, cIdx: number, xScale: number, minScore: number,
): number | null {
  const a = scored(kp, aIdx, minScore)
  const b = scored(kp, bIdx, minScore)
  const c = scored(kp, cIdx, minScore)
  if (a === null || b === null || c === null) return null
  // Degenerate geometry (coincident keypoints) is unmeasurable — null, not 0
  // (which downstream would read as "joint folded shut").
  if (Math.hypot((a.x - b.x) * xScale, a.y - b.y) < EPSILON) return null
  if (Math.hypot((c.x - b.x) * xScale, c.y - b.y) < EPSILON) return null
  return angleDeg(a, b, c, xScale)
}

/** Elbow: shoulder–elbow–wrist. 180° = straight arm. */
export function elbowAngle(
  kp: Keypoint2D[], h: Handedness, xScale: number, minScore = DEFAULT_MIN_SCORE,
): number | null {
  return jointAngle(kp, Coco17.shoulder(h), Coco17.elbow(h), Coco17.wrist(h), xScale, minScore)
}

/** Shoulder: hip–shoulder–elbow (upper arm vs torso). */
export function shoulderAngle(
  kp: Keypoint2D[], h: Handedness, xScale: number, minScore = DEFAULT_MIN_SCORE,
): number | null {
  return jointAngle(kp, Coco17.hip(h), Coco17.shoulder(h), Coco17.elbow(h), xScale, minScore)
}

/** Knee bend: hip–knee–ankle. 180° = straight leg. */
export function kneeBend(
  kp: Keypoint2D[], h: Handedness, xScale: number, minScore = DEFAULT_MIN_SCORE,
): number | null {
  return jointAngle(kp, Coco17.hip(h), Coco17.knee(h), Coco17.ankle(h), xScale, minScore)
}

/**
 * Torso lean: signed angle of hip-mid→shoulder-mid from vertical, facing-normalized
 * (0 = upright, + = forward lean toward facing direction). Null when facing is
 * indeterminate (head keypoints gated or dead-centered over the shoulders).
 */
export function torsoLean(
  kp: Keypoint2D[], xScale: number, minScore = DEFAULT_MIN_SCORE,
): number | null {
  const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
  const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
  const lh = scored(kp, Coco17.LEFT_HIP, minScore)
  const rh = scored(kp, Coco17.RIGHT_HIP, minScore)
  if (ls === null || rs === null || lh === null || rh === null) return null
  const hipMidX = (lh.x + rh.x) / 2
  const shoulderMidX = (ls.x + rs.x) / 2
  const facing = facingSign(kp, shoulderMidX, minScore)
  if (facing === null) return null
  const dx = (shoulderMidX - hipMidX) * xScale
  // image y grows downward; negate so "up" is positive
  const dy = -(((ls.y + rs.y) / 2) - ((lh.y + rh.y) / 2))
  if (Math.hypot(dx, dy) < EPSILON) return null
  return Math.atan2(dx * facing, dy) * RAD_TO_DEG
}

/**
 * Shoulder tilt vs horizon, folded to (−90°, 90°]. 0 = level. Magnitude is robust
 * to left/right label swaps; sign follows image x (compare only within a session
 * where the player faces one way — fixed-drill assumption).
 */
export function shoulderTilt(
  kp: Keypoint2D[], xScale: number, minScore = DEFAULT_MIN_SCORE,
): number | null {
  const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
  const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
  if (ls === null || rs === null) return null
  const dx = (rs.x - ls.x) * xScale
  const dy = rs.y - ls.y
  if (Math.hypot(dx, dy) < EPSILON) return null
  let deg = Math.atan2(dy, dx) * RAD_TO_DEG
  if (deg > 90) deg -= 180
  if (deg <= -90) deg += 180
  return deg
}
