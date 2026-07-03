import { Coco17, Keypoint2D } from './types'

/** Score gate threshold — angle/speed functions ignore keypoints below this. */
export const DEFAULT_MIN_SCORE = 0.3
const FACING_EPSILON = 1e-3

/** The keypoint at idx if present and score ≥ minScore, else null. */
export function scored(kp: Keypoint2D[], idx: number, minScore: number): Keypoint2D | null {
  const k = kp[idx]
  return k !== undefined && k.score >= minScore ? k : null
}

/**
 * Mirrors AngleCalculations2D.facingSign: +1 when the head (nose, or ear
 * midpoint fallback) is right of shoulderMidX, −1 when left, null when gated
 * or dead-centered. KNOWN NOISY on real footage (L-04) — used only as the
 * ForwardStrokeFilter fallback when speed dominance is inconclusive.
 */
export function facingSign(kp: Keypoint2D[], shoulderMidX: number, minScore: number): number | null {
  const nose = scored(kp, Coco17.NOSE, minScore)
  let headX: number | null = nose !== null ? nose.x : null
  if (headX === null) {
    const le = scored(kp, Coco17.LEFT_EAR, minScore)
    const re = scored(kp, Coco17.RIGHT_EAR, minScore)
    headX = le !== null && re !== null ? (le.x + re.x) / 2 : null
  }
  if (headX === null) return null
  const offset = headX - shoulderMidX
  if (Math.abs(offset) < FACING_EPSILON) return null
  return offset > 0 ? 1 : -1
}
