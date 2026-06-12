/**
 * Metric vs external IDEAL range → prioritized feedback cues. DELIBERATELY diverges
 * from Kotlin DrillFeedbackEngine (which compares to the personal baseline mean±σ):
 * this tool grades "how close to a standard technique" (spec decision #2). No
 * personal baseline, no Kotlin golden — do not parity-test against shared/.
 */
import { FeedbackCue } from './feedbackCue'
import { precisionFor } from './metricPrecision'
import { ReferenceStandard } from './referenceStandard'

/**
 * EXP-5: a deviation smaller than this (degrees outside the ideal band) is within
 * the metric's own measurement noise (±a few degrees of keypoint jitter) — not worth
 * a coaching cue. A real coach doesn't nitpick "3° off". Suppresses the trivia, keeps
 * the meaningful faults.
 */
export const MIN_MEANINGFUL_DELTA_DEG = 5

export function evaluateRep(
  metrics: Record<string, number>,
  standard: ReferenceStandard,
  enabledKeys?: Set<string>,
): FeedbackCue[] {
  const cues: FeedbackCue[] = []
  for (const [key, range] of Object.entries(standard.ranges)) {
    if (enabledKeys && !enabledKeys.has(key)) continue
    const value = metrics[key]
    if (value === undefined) continue // no measurement → silent
    const halfWidth = (range.hi - range.lo) / 2
    let delta: number
    let direction: FeedbackCue['direction']
    if (value > range.hi) {
      delta = value - range.hi
      direction = 'too_high'
    } else if (value < range.lo) {
      delta = value - range.lo
      direction = 'too_low'
    } else {
      continue // inside the band — no cue
    }
    if (Math.abs(delta) < MIN_MEANINGFUL_DELTA_DEG) continue // EXP-5: within noise — don't nitpick
    const severity = halfWidth > 0 ? Math.abs(delta) / halfWidth : 0
    cues.push({ metricKey: key, direction, deltaFromRange: delta, severity, precision: precisionFor(key) })
  }
  return cues.sort((a, b) => b.severity - a.severity)
}
