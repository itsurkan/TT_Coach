import { Stroke2D, StrokeCycle2D } from './types'
import { median } from './median'

/**
 * Mirrors RepFilter.kt: keeps strokes whose peak speed AND duration lie within
 * [median/BAND, median×BAND] of the session medians. Runs AFTER
 * filterForwardStrokes, so the medians describe forward strokes only — do not
 * reorder (CLAUDE.md gotcha: reordering silently corrupts results).
 */
export const MIN_STROKES_TO_FILTER = 4
export const SPEED_BAND = 2.0
export const DURATION_BAND = 2.0

export function filterReps(strokes: Stroke2D[]): Stroke2D[] {
  if (strokes.length < MIN_STROKES_TO_FILTER) return strokes
  const medSpeed = median(strokes.map(s => s.peakSpeed))
  const medDur = median(strokes.map(s => s.endFrame - s.startFrame))
  return strokes.filter(s => {
    const dur = s.endFrame - s.startFrame
    return (
      s.peakSpeed >= medSpeed / SPEED_BAND &&
      s.peakSpeed <= medSpeed * SPEED_BAND &&
      dur >= medDur / DURATION_BAND &&
      dur <= medDur * DURATION_BAND
    )
  })
}

/**
 * Speed below this fraction of the session median counts as "slow" for the
 * short-cycle junk test. Chosen on a wide stable plateau (0.75–0.95 all give the
 * same counts on video_3/4 + andrii), so it is not a knife-edge tuned value.
 */
export const SHORT_STRONG_SPEED_FRACTION = 0.85

/**
 * Cycle-aware RepFilter. Duration is the FULL cycle span (backswing.start →
 * drive.end). Speed bands on the drive peak (cycle.peakSpeed).
 *
 * The duration LOWER bound is RELAXED: a short cycle (below the band) is dropped
 * only when it is ALSO slow (< SHORT_STRONG_SPEED_FRACTION × median speed). A
 * short-but-strong cycle is a real drive whose span is short only because no
 * backswing was paired (unpaired cycle = drive-half only) — keeping it recovers
 * andrii_1's two backswing-less drives (L-31) while still dropping video_4's slow
 * trailing junk (@15.74s, 0.68× median). The duration UPPER bound and the speed
 * band still drop long/very-slow/very-fast non-strokes.
 */
export function filterCycleReps(cycles: StrokeCycle2D[]): StrokeCycle2D[] {
  if (cycles.length < MIN_STROKES_TO_FILTER) return cycles
  const medSpeed = median(cycles.map(c => c.peakSpeed))
  const medDur = median(cycles.map(c => c.endFrame - c.startFrame))
  return cycles.filter(c => {
    const dur = c.endFrame - c.startFrame
    if (c.peakSpeed < medSpeed / SPEED_BAND || c.peakSpeed > medSpeed * SPEED_BAND) return false
    if (dur > medDur * DURATION_BAND) return false
    if (dur < medDur / DURATION_BAND && c.peakSpeed < medSpeed * SHORT_STRONG_SPEED_FRACTION) return false
    return true
  })
}
