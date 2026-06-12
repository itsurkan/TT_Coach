import { Coco17, Handedness, PoseFrame2D, Stroke2D } from './types'
import { DEFAULT_MIN_SCORE, facingSign, scored } from './facing'
import { median } from './median'

/**
 * Mirrors ForwardStrokeFilter.kt: drops wrist-speed peaks that are NOT forward
 * strokes (~half of all peaks on real footage are recovery swings). Session
 * facing comes from the speed-dominance vote over wrist-dx groups; the noisy
 * per-frame head read is only the fallback. Unverifiable strokes are dropped.
 * Direction is read over the ~100 ms approach into the peak, not start→peak
 * (L-28 — bled boundaries on continuous play).
 */
export const SPEED_DOMINANCE_RATIO = 1.2
export const MIN_GROUP_SIZE = 2

/** Direction is read over this window of APPROACH into the peak (L-28). */
export const PEAK_APPROACH_WINDOW_MS = 100

export function filterForwardStrokes(
  strokes: Stroke2D[],
  frames: PoseFrame2D[],
  handedness: Handedness,
  minScore: number = DEFAULT_MIN_SCORE,
): Stroke2D[] {
  const verified: Array<{ stroke: Stroke2D; dx: number }> = []
  for (const stroke of strokes) {
    const dx = wristDx(stroke, frames, handedness, minScore)
    if (dx !== null) verified.push({ stroke, dx })
  }
  const facing = speedDominantFacing(verified)
  if (facing !== null) {
    return verified.filter(v => v.dx * facing > 0).map(v => v.stroke)
  }
  return verified
    .filter(v => {
      const head = headFacingAtStart(v.stroke, frames, minScore)
      return head !== null && v.dx * head > 0
    })
    .map(v => v.stroke)
}

/**
 * Wrist x-displacement over the ~100 ms approach INTO the peak: x[peak] −
 * x[approach], where approach is the earliest frame still within ~100 ms of the
 * peak (walk back while each step stays inside the window), clamped to startFrame;
 * null when the wrist is gated at either end. Mirrors
 * ForwardStrokeFilter.wristDx (L-28): start→peak is NOT used — on continuous
 * play startFrame bleeds into the previous follow-through and true drives
 * read backward.
 */
function wristDx(
  stroke: Stroke2D,
  frames: PoseFrame2D[],
  handedness: Handedness,
  minScore: number,
): number | null {
  const wristIdx = Coco17.wrist(handedness)
  const peakFrame = frames[stroke.peakFrame]
  if (peakFrame === undefined) return null
  let a = stroke.peakFrame
  while (a > stroke.startFrame && peakFrame.timestampMs - frames[a - 1].timestampMs <= PEAK_APPROACH_WINDOW_MS) a--
  const approachKp = frames[a] !== undefined ? scored(frames[a].keypoints, wristIdx, minScore) : null
  const peakKp = scored(peakFrame.keypoints, wristIdx, minScore)
  if (approachKp === null || peakKp === null) return null
  return peakKp.x - approachKp.x
}

/**
 * Session facing from speed asymmetry: ±1 when one dx-sign group's median peak
 * speed dominates the other by SPEED_DOMINANCE_RATIO; null when either group
 * has fewer than MIN_GROUP_SIZE strokes or neither dominates.
 */
function speedDominantFacing(verified: Array<{ stroke: Stroke2D; dx: number }>): number | null {
  const posSpeeds = verified.filter(v => v.dx > 0).map(v => v.stroke.peakSpeed)
  const negSpeeds = verified.filter(v => v.dx < 0).map(v => v.stroke.peakSpeed)
  if (posSpeeds.length < MIN_GROUP_SIZE || negSpeeds.length < MIN_GROUP_SIZE) return null
  const posMed = median(posSpeeds)
  const negMed = median(negSpeeds)
  if (posMed >= negMed * SPEED_DOMINANCE_RATIO) return 1
  if (negMed >= posMed * SPEED_DOMINANCE_RATIO) return -1
  return null
}

function headFacingAtStart(stroke: Stroke2D, frames: PoseFrame2D[], minScore: number): number | null {
  const kp = frames[stroke.startFrame]?.keypoints
  if (kp === undefined) return null
  const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
  const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
  if (ls === null || rs === null) return null
  return facingSign(kp, (ls.x + rs.x) / 2, minScore)
}
