import { Handedness, Stroke2D, StrokeCycle2D } from './types'
import { xScaleFor } from './geometry'
import { detectStrokes, StrokeDetectorOptions } from './strokeDetector2d'
import { filterForwardStrokes } from './forwardStrokeFilter'
import { filterCycleReps } from './repFilter'
import { pairCycles, MAX_PAIR_GAP_MS } from './cyclePairing'
import { filterStationaryCycles, hipMidTravelTorso, DEFAULT_MAX_TRAVEL_TORSO } from './locomotionFilter'
import { DEFAULT_MIN_SCORE } from './facing'
import { PoseSequence2D } from './parsePoseV2'

export interface StrokeCountConfig {
  handedness: Handedness
  /** Manual camera-yaw override. The estimator is NOT ported (saturates on
   *  non-protocol footage, L-25); Videos/ clips analyze with yaw 0. */
  cameraYawDeg: number
  detector?: StrokeDetectorOptions
  /** Locomotion gate (L-30): reject reps whose hip-mid travels more than this many
   *  torso-lengths (walking). undefined = default (DEFAULT_MAX_TRAVEL_TORSO, on);
   *  0 = explicitly off. */
  hipTravelMaxTorso?: number
  /** Max gap (ms) between a dropped backswing peak and its drive for cycle pairing.
   *  undefined → MAX_PAIR_GAP_MS (800). */
  maxPairGapMs?: number
}

export interface StrokeCountResult {
  /** Every wrist-speed peak (includes recovery swings + junk). */
  rawStrokes: Stroke2D[]
  /** After ForwardStrokeFilter (recovery swings dropped). */
  forwardStrokes: Stroke2D[]
  /** One cycle per forward drive (backswing+drive paired), before RepFilter/loco. */
  cycles: StrokeCycle2D[]
  /** After cycle-RepFilter + locomotion gate — the countable reps (drive strokes). */
  reps: Stroke2D[]
  /** Reps the locomotion gate removed (walking); empty when the gate is off. */
  locomotionStrokes: Stroke2D[]
  xScale: number
}

/**
 * The pipeline. Order is mandatory (CLAUDE.md gotcha):
 * detect → ForwardStrokeFilter → CyclePairing → cycle-RepFilter → (optional) locomotion gate.
 * A counted "stroke" is a full cycle (backswing + forward drive); banding on the
 * near-uniform cycle duration keeps fast/short drives the forward-half filter dropped.
 */
export function countStrokes(seq: PoseSequence2D, config: StrokeCountConfig): StrokeCountResult {
  const xScale = xScaleFor(seq.aspectRatio, config.cameraYawDeg)
  const minScore = config.detector?.minScore ?? DEFAULT_MIN_SCORE
  const rawStrokes = detectStrokes(seq.frames, config.handedness, xScale, seq.intervalMs, config.detector)
  const forwardStrokes = filterForwardStrokes(rawStrokes, seq.frames, config.handedness, minScore)
  const cycles = pairCycles(rawStrokes, forwardStrokes, seq.frames, seq.intervalMs, config.maxPairGapMs ?? MAX_PAIR_GAP_MS)
  const banded = filterCycleReps(cycles)
  // undefined → default-on; explicit 0 (the «Гейт ходьби» knob) → off.
  const max = config.hipTravelMaxTorso ?? DEFAULT_MAX_TRAVEL_TORSO
  const keptCycles = max > 0 ? filterStationaryCycles(banded, seq.frames, xScale, max, minScore) : banded
  const locoCycles = max > 0
    ? banded.filter(c => { const t = hipMidTravelTorso(seq.frames, c, xScale, minScore); return t !== null && t > max })
    : []
  return {
    rawStrokes,
    forwardStrokes,
    cycles,
    reps: keptCycles.map(c => c.drive),
    locomotionStrokes: locoCycles.map(c => c.drive),
    xScale,
  }
}
