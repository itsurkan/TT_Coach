import { Handedness, Stroke2D } from './types'
import { xScaleFor } from './geometry'
import { detectStrokes, StrokeDetectorOptions } from './strokeDetector2d'
import { filterForwardStrokes } from './forwardStrokeFilter'
import { filterReps } from './repFilter'
import { DEFAULT_MIN_SCORE } from './facing'
import { PoseSequence2D } from './parsePoseV2'

export interface StrokeCountConfig {
  handedness: Handedness
  /** Manual camera-yaw override. The estimator is NOT ported (saturates on
   *  non-protocol footage, L-25); Videos/ clips analyze with yaw 0. */
  cameraYawDeg: number
  detector?: StrokeDetectorOptions
}

export interface StrokeCountResult {
  /** Every wrist-speed peak (includes recovery swings + junk). */
  rawStrokes: Stroke2D[]
  /** After ForwardStrokeFilter (recovery swings dropped). */
  forwardStrokes: Stroke2D[]
  /** After RepFilter (junk movement dropped) — the countable reps. */
  reps: Stroke2D[]
  xScale: number
}

/**
 * The M0 pipeline. Order is mandatory (CLAUDE.md gotcha):
 * detect → ForwardStrokeFilter → RepFilter.
 */
export function countStrokes(seq: PoseSequence2D, config: StrokeCountConfig): StrokeCountResult {
  const xScale = xScaleFor(seq.aspectRatio, config.cameraYawDeg)
  const minScore = config.detector?.minScore ?? DEFAULT_MIN_SCORE
  const rawStrokes = detectStrokes(seq.frames, config.handedness, xScale, seq.intervalMs, config.detector)
  const forwardStrokes = filterForwardStrokes(rawStrokes, seq.frames, config.handedness, minScore)
  const reps = filterReps(forwardStrokes)
  return { rawStrokes, forwardStrokes, reps, xScale }
}
