import { Handedness, Stroke2D } from './types'
import { xScaleFor } from './geometry'
import { detectStrokes, StrokeDetectorOptions } from './strokeDetector2d'
import { filterForwardStrokes } from './forwardStrokeFilter'
import { filterReps } from './repFilter'
import { filterStationaryStrokes, hipMidTravelTorso } from './locomotionFilter'
import { DEFAULT_MIN_SCORE } from './facing'
import { PoseSequence2D } from './parsePoseV2'

export interface StrokeCountConfig {
  handedness: Handedness
  /** Manual camera-yaw override. The estimator is NOT ported (saturates on
   *  non-protocol footage, L-25); Videos/ clips analyze with yaw 0. */
  cameraYawDeg: number
  detector?: StrokeDetectorOptions
  /** EXPERIMENTAL locomotion gate (L-30): reject reps whose hip-mid travels more
   *  than this many torso-lengths (walking). 0/undefined = off (goldens hold). */
  hipTravelMaxTorso?: number
}

export interface StrokeCountResult {
  /** Every wrist-speed peak (includes recovery swings + junk). */
  rawStrokes: Stroke2D[]
  /** After ForwardStrokeFilter (recovery swings dropped). */
  forwardStrokes: Stroke2D[]
  /** After RepFilter + locomotion gate — the countable reps. */
  reps: Stroke2D[]
  /** Reps the locomotion gate removed (walking); empty when the gate is off. */
  locomotionStrokes: Stroke2D[]
  xScale: number
}

/**
 * The M0 pipeline. Order is mandatory (CLAUDE.md gotcha):
 * detect → ForwardStrokeFilter → RepFilter → (optional) locomotion gate.
 */
export function countStrokes(seq: PoseSequence2D, config: StrokeCountConfig): StrokeCountResult {
  const xScale = xScaleFor(seq.aspectRatio, config.cameraYawDeg)
  const minScore = config.detector?.minScore ?? DEFAULT_MIN_SCORE
  const rawStrokes = detectStrokes(seq.frames, config.handedness, xScale, seq.intervalMs, config.detector)
  const forwardStrokes = filterForwardStrokes(rawStrokes, seq.frames, config.handedness, minScore)
  const repped = filterReps(forwardStrokes)
  const max = config.hipTravelMaxTorso
  const reps = max && max > 0 ? filterStationaryStrokes(repped, seq.frames, xScale, max, minScore) : repped
  const locomotionStrokes = max && max > 0
    ? repped.filter(s => { const t = hipMidTravelTorso(seq.frames, s, xScale, minScore); return t !== null && t > max })
    : []
  return { rawStrokes, forwardStrokes, reps, locomotionStrokes, xScale }
}
