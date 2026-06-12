/**
 * Per-rep extraction of the five in-plane metrics at the stroke's wrist-speed peak
 * — 1:1 mirror of Kotlin DrillMetrics. Score-gated per joint, sanity-bounded per
 * value, then MEDIAN over a ±70 ms window (keypoints are unsmoothed — one junk
 * frame must not shift the rep value).
 */
import { Handedness, PoseFrame2D } from './types'
import { DEFAULT_MIN_SCORE } from './facing'
import { elbowAngle, kneeBend, shoulderAngle, shoulderTilt, torsoLean } from './angles2d'
import { isSane } from './sanityBounds'

export const METRIC = {
  ELBOW_ANGLE: 'elbow_angle',
  SHOULDER_ANGLE: 'shoulder_angle',
  KNEE_BEND: 'knee_bend',
  TORSO_LEAN: 'torso_lean',
  SHOULDER_TILT: 'shoulder_tilt',
} as const

export const ALL_KEYS: string[] = [
  METRIC.ELBOW_ANGLE, METRIC.SHOULDER_ANGLE, METRIC.KNEE_BEND,
  METRIC.TORSO_LEAN, METRIC.SHOULDER_TILT,
]

/** Half-width of the peak-metric smoothing window, in ms (±2 frames at 30 fps). */
export const DEFAULT_PEAK_RADIUS_MS = 70

/**
 * Extract the five in-plane metrics for a single frame.
 * Each metric is score-gated by the angle function (returns null when below minScore),
 * then sanity-bounded — matching Kotlin's collect-then-filter approach.
 */
export function extractAtFrame(
  frame: PoseFrame2D, handedness: Handedness, xScale: number, minScore = DEFAULT_MIN_SCORE,
): Record<string, number> {
  const kp = frame.keypoints
  if (kp.length === 0) return {}
  const raw: Record<string, number | null> = {
    [METRIC.ELBOW_ANGLE]: elbowAngle(kp, handedness, xScale, minScore),
    [METRIC.SHOULDER_ANGLE]: shoulderAngle(kp, handedness, xScale, minScore),
    [METRIC.KNEE_BEND]: kneeBend(kp, handedness, xScale, minScore),
    [METRIC.TORSO_LEAN]: torsoLean(kp, xScale, minScore),
    [METRIC.SHOULDER_TILT]: shoulderTilt(kp, xScale, minScore),
  }
  const out: Record<string, number> = {}
  for (const [key, value] of Object.entries(raw)) {
    if (value !== null && isSane(key, value)) out[key] = value
  }
  return out
}

function medianOf(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

/**
 * Per-rep metrics: MEDIAN of each metric over the frames within ±radiusMs of peakFrame.
 * Radius is computed as Math.trunc(radiusMs / intervalMs) — integer division matching Kotlin.
 * Window is inclusive [peakFrame - radius, peakFrame + radius], clamped to array bounds.
 * Each frame is independently score-gated and sanity-bounded via extractAtFrame.
 * At coarse intervals (radius < interval) this degrades to the single peak frame.
 */
export function extractAtPeak(
  frames: PoseFrame2D[],
  peakFrame: number,
  handedness: Handedness,
  xScale: number,
  intervalMs: number,
  minScore = DEFAULT_MIN_SCORE,
  radiusMs = DEFAULT_PEAK_RADIUS_MS,
): Record<string, number> {
  if (intervalMs <= 0) throw new Error(`intervalMs must be > 0, got ${intervalMs}`)
  if (peakFrame < 0 || peakFrame >= frames.length) {
    throw new Error(`peakFrame ${peakFrame} out of bounds for ${frames.length} frames`)
  }
  const radius = Math.trunc(radiusMs / intervalMs)
  const lo = Math.max(peakFrame - radius, 0)
  const hi = Math.min(peakFrame + radius, frames.length - 1)
  const byKey: Record<string, number[]> = {}
  for (let i = lo; i <= hi; i++) {
    for (const [key, value] of Object.entries(extractAtFrame(frames[i], handedness, xScale, minScore))) {
      ;(byKey[key] ??= []).push(value)
    }
  }
  const out: Record<string, number> = {}
  for (const [key, values] of Object.entries(byKey)) out[key] = medianOf(values)
  return out
}
