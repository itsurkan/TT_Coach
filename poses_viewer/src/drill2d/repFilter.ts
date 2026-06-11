import { Stroke2D } from './types'
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
