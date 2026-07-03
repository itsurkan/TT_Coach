import { Coco17, Handedness, PoseFrame2D, Stroke2D } from './types'
import { DEFAULT_MIN_SCORE, scored } from './facing'

/**
 * Phase-aligned DISPLAY window for an already-kept rep. A forehand drive travels
 * LOW→HIGH, so the band should start at the lowest wrist point (the load, just
 * before the forward swing) and end at the highest wrist point (the follow-through
 * finish) — not at the speed valleys the detector clamps to (those land mid-cycle,
 * so the band looks phase-shifted into the previous stroke's recovery).
 *
 * Image y grows DOWNWARD, so "lowest physical point" = MAX y and "highest" = MIN y.
 * Vertical only → xScale-independent. Wrist y is lightly smoothed so a single junk
 * frame can't grab the band edge. Search is bounded by [searchLo, searchHi] (the
 * caller passes neighbour peaks so adjacent windows don't cross) and additionally
 * capped to ±maxSpanMs around the peak (protects the first/last rep, whose neighbour
 * bound is the clip edge). Falls back to the stroke's own boundary when the wrist is
 * gated across a sub-range.
 *
 * COUNT-SAFE: this runs only on reps that already survived detect → forward → rep,
 * for display/loop. It must never run before RepFilter (which bands on duration).
 */
export interface CycleWindow {
  startFrame: number
  endFrame: number
}

const SMOOTH_WINDOW_MS = 100

export function cycleWindow(
  frames: PoseFrame2D[],
  stroke: Stroke2D,
  handedness: Handedness,
  searchLo: number,
  searchHi: number,
  intervalMs: number,
  minScore: number = DEFAULT_MIN_SCORE,
  maxSpanMs = 1200,
): CycleWindow {
  const wristIdx = Coco17.wrist(handedness)
  const p = stroke.peakFrame
  const cap = Math.max(1, Math.floor(maxSpanMs / intervalMs))
  const lo = Math.max(0, searchLo, p - cap)
  const hi = Math.min(frames.length - 1, searchHi, p + cap)
  const ys = smoothWristY(frames, wristIdx, minScore, Math.max(1, Math.floor(SMOOTH_WINDOW_MS / intervalMs)))

  // start = lowest physical point (max y) in [lo, p]
  let start = stroke.startFrame
  let maxY = -Infinity
  for (let i = lo; i <= p; i++) {
    const y = ys[i]
    if (y === null) continue
    if (y > maxY) { maxY = y; start = i }
  }
  // end = highest physical point (min y) in [p, hi]
  let end = stroke.endFrame
  let minY = Infinity
  for (let i = p; i <= hi; i++) {
    const y = ys[i]
    if (y === null) continue
    if (y < minY) { minY = y; end = i }
  }
  return { startFrame: Math.min(start, p), endFrame: Math.max(end, p) }
}

/** Box-averaged wrist y, gated frames excluded (null); window ≤ 1 → raw. */
function smoothWristY(
  frames: PoseFrame2D[], wristIdx: number, minScore: number, window: number,
): (number | null)[] {
  const raw: (number | null)[] = frames.map(f => {
    const w = scored(f.keypoints, wristIdx, minScore)
    return w === null ? null : w.y
  })
  if (window <= 1) return raw
  const half = Math.floor(window / 2)
  return raw.map((_, i) => {
    let sum = 0
    let n = 0
    for (let j = Math.max(0, i - half); j <= Math.min(raw.length - 1, i + half); j++) {
      const v = raw[j]
      if (v !== null) { sum += v; n++ }
    }
    return n === 0 ? null : sum / n
  })
}
