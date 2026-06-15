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
 * Cycle-aware RepFilter. Same banding, but duration is the FULL cycle span
 * (backswing.start → drive.end), not the forward-half. Cycle durations are near
 * uniform (~1.0 s), so fast/short drives whose forward-half fell below the
 * forward-half lower band are kept — fixing the video_4 8-vs-10 undercount.
 * Speed still bands on the drive peak (cycle.peakSpeed → drive.peakSpeed).
 */
export function filterCycleReps(cycles: StrokeCycle2D[]): StrokeCycle2D[] {
  if (cycles.length < MIN_STROKES_TO_FILTER) return cycles
  const medSpeed = median(cycles.map(c => c.peakSpeed))
  const medDur = median(cycles.map(c => c.endFrame - c.startFrame))
  return cycles.filter(c => {
    const dur = c.endFrame - c.startFrame
    return (
      c.peakSpeed >= medSpeed / SPEED_BAND &&
      c.peakSpeed <= medSpeed * SPEED_BAND &&
      dur >= medDur / DURATION_BAND &&
      dur <= medDur * DURATION_BAND
    )
  })
}
