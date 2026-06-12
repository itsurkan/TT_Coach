/**
 * Anatomical sanity bounds — 1:1 mirror of Kotlin SanityBounds. A value outside
 * its band is a tracking glitch: the metric is DROPPED for that frame, never
 * coached on. Bounds are opt-in (unregistered metrics pass through).
 */
import { METRIC_KEYS } from './referenceStandard'

const BOUNDS: Record<string, readonly [number, number]> = {
  elbow_angle: [20, 170],
  shoulder_angle: [5, 175],
  knee_bend: [60, 180],
  torso_lean: [-60, 60],
  shoulder_tilt: [-60, 60],
}

export function isSane(metricKey: string, value: number): boolean {
  const b = BOUNDS[metricKey]
  if (b === undefined) return true
  return value >= b[0] && value <= b[1]
}

// Compile-time reminder that bounds cover the metric set (no runtime cost).
void METRIC_KEYS
