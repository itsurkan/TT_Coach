import { Coco17, PoseFrame2D, Stroke2D, StrokeCycle2D } from './types'
import { scored } from './facing'

/**
 * Locomotion gate (EXPERIMENTAL, viewer-first prototype — NOT yet mirrored in
 * Kotlin StrokeDetector2D; see DESIGN_LIMITATIONS L-30). A real forehand drive
 * keeps the base (hip-mid) roughly planted — weight transfers, the feet don't
 * travel. Walking/stepping slides the whole torso sideways, yet still swings the
 * arm forward fast enough to clear the detector + ForwardStrokeFilter + RepFilter.
 * This measures hip-mid horizontal excursion over a stroke's window, normalized
 * by torso-length (camera-distance invariant), so such strokes can be rejected.
 *
 * Mirrors Kotlin LocomotionFilter (source of truth). On by default at
 * DEFAULT_MAX_TRAVEL_TORSO; the «Гейт ходьби» knob can set 0 to disable.
 */

/**
 * Strokes whose hip-mid travels more than this many torso-lengths are locomotion
 * (walking), not strokes. Tuned on non-protocol footage: real drives in the
 * andrii_1 / video_4 fixtures peak at ~0.25 hip travel, a walking step at ~0.68.
 * Kept in sync with Kotlin LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO.
 */
export const DEFAULT_MAX_TRAVEL_TORSO = 0.4

const MIN_TORSO_LEN = 1e-4

/**
 * Peak-to-peak horizontal travel of hip-mid over [startFrame, endFrame], in
 * torso-lengths. x-deltas are xScale-corrected (schema v2 normalizes x by width,
 * y by height). null when hip-mid or torso-length is never measurable in-window.
 */
export function hipMidTravelTorso(
  frames: PoseFrame2D[],
  window: { startFrame: number; endFrame: number },
  xScale: number,
  minScore: number,
): number | null {
  const xs: number[] = []
  const torsos: number[] = []
  for (let i = window.startFrame; i <= window.endFrame; i++) {
    const kp = frames[i]?.keypoints
    if (kp === undefined) continue
    const lh = scored(kp, Coco17.LEFT_HIP, minScore)
    const rh = scored(kp, Coco17.RIGHT_HIP, minScore)
    if (lh === null || rh === null) continue
    xs.push((lh.x + rh.x) / 2)
    const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
    const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
    if (ls === null || rs === null) continue
    const len = Math.hypot(
      ((ls.x + rs.x - (lh.x + rh.x)) / 2) * xScale,
      (ls.y + rs.y - (lh.y + rh.y)) / 2,
    )
    if (len >= MIN_TORSO_LEN) torsos.push(len)
  }
  if (xs.length === 0 || torsos.length === 0) return null
  const torsoLen = median(torsos)
  const travel = (Math.max(...xs) - Math.min(...xs)) * xScale
  return travel / torsoLen
}

/**
 * Drops strokes whose hip-mid travels more than maxTravelTorso torso-lengths
 * (locomotion). Strokes whose travel can't be measured are KEPT — the gate can
 * never prove locomotion, so it doesn't reject on absence of evidence.
 */
export function filterStationaryStrokes(
  strokes: Stroke2D[],
  frames: PoseFrame2D[],
  xScale: number,
  maxTravelTorso: number,
  minScore: number,
): Stroke2D[] {
  return strokes.filter(s => {
    const travel = hipMidTravelTorso(frames, s, xScale, minScore)
    return travel === null || travel <= maxTravelTorso
  })
}

/**
 * Cycle-aware locomotion gate. Hip-mid travel is measured over the FULL cycle
 * window [cycle.startFrame, cycle.endFrame] (backswing + drive), so a walking
 * step that slides the body across the whole cycle is still rejected. Cycles
 * whose travel can't be measured are KEPT (no reject on absence of evidence).
 */
export function filterStationaryCycles(
  cycles: StrokeCycle2D[],
  frames: PoseFrame2D[],
  xScale: number,
  maxTravelTorso: number,
  minScore: number,
): StrokeCycle2D[] {
  return cycles.filter(c => {
    const travel = hipMidTravelTorso(frames, c, xScale, minScore)
    return travel === null || travel <= maxTravelTorso
  })
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}
