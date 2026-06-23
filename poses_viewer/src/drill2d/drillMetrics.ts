/**
 * Per-rep extraction of the in-plane metrics at the stroke's wrist-speed peak
 * — 1:1 mirror of Kotlin DrillMetrics. Score-gated per joint, sanity-bounded per
 * value, then MEDIAN over a ±70 ms window (keypoints are unsmoothed — one junk
 * frame must not shift the rep value).
 */
import { Handedness, PoseFrame2D, StrokeCycle2D } from './types'
import { DEFAULT_MIN_SCORE } from './facing'
import { elbowAngle, hipFlexion, kneeBend, shoulderAngle, shoulderTilt, torsoLean } from './angles2d'
import { isSane } from './sanityBounds'

export const METRIC = {
  ELBOW_ANGLE: 'elbow_angle',
  SHOULDER_ANGLE: 'shoulder_angle',
  KNEE_BEND: 'knee_bend',
  TORSO_LEAN: 'torso_lean',
  SHOULDER_TILT: 'shoulder_tilt',
  HIP_FLEXION: 'hip_flexion',
} as const

export const ALL_KEYS: string[] = [
  METRIC.ELBOW_ANGLE, METRIC.SHOULDER_ANGLE, METRIC.KNEE_BEND,
  METRIC.TORSO_LEAN, METRIC.SHOULDER_TILT, METRIC.HIP_FLEXION,
]

/** Half-width of the peak-metric smoothing window, in ms (±2 frames at 30 fps). */
export const DEFAULT_PEAK_RADIUS_MS = 70

/**
 * Extract the in-plane metrics for a single frame.
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
    [METRIC.HIP_FLEXION]: hipFlexion(kp, handedness, xScale, minScore),
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

// ---------------------------------------------------------------------------
// Per-phase extraction
// ---------------------------------------------------------------------------

/** The three stroke phases at which metrics are measured. */
export type Phase = 'backswing' | 'contact' | 'followthrough'

/**
 * Declares which phases each metric should be measured at.
 * Exported so table UI and reference-range modules can import it without
 * duplicating the mapping.
 *
 * This is a CURATED SUBSET of all metrics — not every metric gets per-phase
 * treatment. The `satisfies Partial<...>` check enforces that any key present
 * is a valid MetricKey (typo'd keys are compile-time errors), while still
 * allowing the subset to omit metrics.
 *
 * Movement-bracketing rule:
 *   - Arm metrics (elbow, shoulder): backswing + followthrough (the swing arc).
 *     Contact is the noisiest instant for both — RTMPose mislocates the fast forearm
 *     and the shoulder sweeps through its full range, so no stable ideal exists at contact.
 *   - Legs/trunk metrics (knee, hip, torso): backswing + contact (load → drive).
 *     Followthrough is excluded — it is a recovery phase that rotates out of the camera
 *     plane, so 2D coaching values are unreliable there (no ideal to grade against).
 *
 * shoulder_tilt is intentionally excluded from METRIC_PHASES — it remains a
 * single-instant colored cell (using standard.ranges directly).
 */
export const METRIC_PHASES = {
  // Legs/trunk — load → drive; followthrough excluded (rotation-corrupted)
  [METRIC.KNEE_BEND]:       ['backswing', 'contact'],
  [METRIC.HIP_FLEXION]:     ['backswing', 'contact'],
  [METRIC.TORSO_LEAN]:      ['backswing', 'contact'],
  // Arm — swing arc (замах → завершення); contact excluded (noisiest instant)
  [METRIC.ELBOW_ANGLE]:     ['backswing', 'followthrough'],
  [METRIC.SHOULDER_ANGLE]:  ['backswing', 'followthrough'],
} satisfies Partial<Record<(typeof METRIC)[keyof typeof METRIC], Phase[]>>

/**
 * Extract per-metric, per-phase values for a full stroke cycle.
 *
 * Phase anchor frames:
 *   backswing   → cycle.drive.startFrame  (the turn; only emitted when cycle.backswing != null)
 *   contact     → cycle.drive.peakFrame   (same anchor as extractAtPeak today)
 *   followthrough → cycle.drive.endFrame
 *
 * Each phase is computed via the existing extractAtPeak (±radiusMs median window).
 * extractAtPeak is called AT MOST ONCE per distinct anchor frame — results are
 * memoized by frame index so metrics sharing the same anchor reuse the window.
 *
 * A phase value is `number | null` (null when extractAtPeak gated the metric at
 * that frame). The `backswing` key is omitted entirely from a metric's record
 * when cycle.backswing is null.
 */
export function extractPerPhase(
  cycle: StrokeCycle2D,
  frames: PoseFrame2D[],
  handedness: Handedness,
  xScale: number,
  intervalMs: number,
  minScore = DEFAULT_MIN_SCORE,
  radiusMs = DEFAULT_PEAK_RADIUS_MS,
): Record<string, Partial<Record<Phase, number | null>>> {
  // Map phase → anchor frame index (omit backswing when unpaired).
  const phaseAnchors: Partial<Record<Phase, number>> = {
    contact:       cycle.drive.peakFrame,
    followthrough: cycle.drive.endFrame,
  }
  if (cycle.backswing !== null) {
    phaseAnchors.backswing = cycle.drive.startFrame
  }

  // Pre-flight: validate each anchor frame that will actually be used.
  for (const [phase, frameIndex] of Object.entries(phaseAnchors) as [Phase, number][]) {
    if (frameIndex < 0 || frameIndex >= frames.length) {
      throw new Error(
        `extractPerPhase: ${phase} anchor frame ${frameIndex} out of bounds for ${frames.length} frames`,
      )
    }
  }

  // Memoize extractAtPeak calls by frame index so shared anchors are not
  // recomputed (e.g. two metrics at 'contact' → same window, one call).
  const cache = new Map<number, Record<string, number>>()
  function peakAt(frameIndex: number): Record<string, number> {
    let cached = cache.get(frameIndex)
    if (cached === undefined) {
      cached = extractAtPeak(frames, frameIndex, handedness, xScale, intervalMs, minScore, radiusMs)
      cache.set(frameIndex, cached)
    }
    return cached
  }

  const result: Record<string, Partial<Record<Phase, number | null>>> = {}

  for (const [metricKey, phases] of Object.entries(METRIC_PHASES)) {
    const phaseRecord: Partial<Record<Phase, number | null>> = {}
    for (const phase of phases) {
      const anchorFrame = phaseAnchors[phase]
      if (anchorFrame === undefined) {
        // backswing phase requested but cycle is unpaired — omit entirely.
        continue
      }
      const extracted = peakAt(anchorFrame)
      // Keep null explicitly when extractAtPeak gated this metric out.
      phaseRecord[phase] = extracted[metricKey] !== undefined ? extracted[metricKey] : null
    }
    result[metricKey] = phaseRecord
  }

  return result
}
