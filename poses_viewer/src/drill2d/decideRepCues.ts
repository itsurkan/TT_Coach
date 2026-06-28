/**
 * The single feedback-decision engine. Compares each rep's metrics to the external
 * ideal bands, WIDENED by settings.bandWidthMult, and returns every out-of-band cue
 * (severity-desc). Drives BOTH the table ("Всі зауваження") and the voice
 * (buildSpokenSchedule). With bandWidthMult=1, minMeaningfulDeltaDeg=5 and all
 * metrics enabled it reproduces the retired feedbackEngine.evaluateRep exactly.
 */
import { FeedbackCue } from './feedbackCue'
import { precisionFor } from './metricPrecision'
import { ReferenceStandard, perPhaseRange, type PerPhaseRanges } from './referenceStandard'
import { FeedbackSettings } from './feedbackSettings'
import { MetricKey } from './voiceStyle'
import type { Phase } from './drillMetrics'

/**
 * Pattern metrics describe a movement ACROSS the stroke, not a single target pose, so a
 * single contact-instant value has no meaningful static ideal to grade against.
 *
 * Movement-bracketing rule:
 *   - Arm metrics (elbow, shoulder): graded at backswing + followthrough (the swing arc).
 *     elbow extends on the take-back (~165°) and folds at the finish (~70°); shoulder sweeps
 *     up through the arc. Grading either at the noisy contact instant produces nonsense cues.
 *   - Legs/trunk metrics (knee, hip, torso): graded at backswing + contact (load → drive);
 *     followthrough is excluded — it is a recovery phase that rotates out of the camera
 *     plane, so no in-plane coaching ideal exists there.
 *
 * `shoulder_tilt` is the ONLY single-instant metric remaining in decideRepCues — it is an
 * axial rotation proxy measured at contact (the most stable instant for that cue).
 *
 * decideRepCues (single-instant) skips all five pattern metrics. Instead, `decidePatternCues`
 * grades each per phase against the per-phase bands in referenceStandard (PER_PHASE_RANGES).
 * The single-instant literature bands in referenceStandard.ranges are retained for reference
 * + sessionStrengths.
 */
const PATTERN_METRICS = new Set<string>(['elbow_angle', 'shoulder_angle', 'knee_bend', 'hip_flexion', 'torso_lean'])

export { PATTERN_METRICS }

/** True for movement-pattern metrics graded per-phase, not at the single contact instant. */
export function isPatternMetric(metricKey: string): boolean {
  return PATTERN_METRICS.has(metricKey)
}

export function decideRepCues(
  metrics: Record<string, number>,
  standard: ReferenceStandard,
  settings: FeedbackSettings,
): FeedbackCue[] {
  const enabled = new Set<string>(settings.enabledMetrics as MetricKey[])
  const cues: FeedbackCue[] = []
  for (const [key, range] of Object.entries(standard.ranges)) {
    if (PATTERN_METRICS.has(key)) continue
    if (!enabled.has(key)) continue
    const value = metrics[key]
    if (value === undefined) continue
    const half = (range.hi - range.lo) / 2
    const center = (range.lo + range.hi) / 2
    const wLo = center - half * settings.bandWidthMult
    const wHi = center + half * settings.bandWidthMult
    let delta: number
    let direction: FeedbackCue['direction']
    if (value > wHi) { delta = value - wHi; direction = 'too_high' }
    else if (value < wLo) { delta = value - wLo; direction = 'too_low' }
    else continue
    if (Math.abs(delta) < settings.minMeaningfulDeltaDeg) continue
    const severity = half > 0 ? Math.abs(delta) / half : 0
    cues.push({ metricKey: key, direction, deltaFromRange: delta, severity, precision: precisionFor(key) })
  }
  return cues.sort((a, b) => b.severity - a.severity)
}

/**
 * Grade all five PATTERN metrics (elbow_angle, shoulder_angle, knee_bend, hip_flexion,
 * torso_lean) at each stroke phase against their per-phase ideal bands. Same widening /
 * minMeaningfulDeltaDeg / severity math as decideRepCues, but the ideal comes from
 * perPhaseRange(metric, phase) and each cue carries its `phase`.
 *
 * Movement-bracketing rule applied here:
 *   - Arm metrics (elbow, shoulder): phases backswing + followthrough only.
 *   - Legs/trunk metrics (knee, hip, torso): phases backswing + contact only.
 * Phases with no per-phase range (e.g. elbow at contact) or a null/undefined value
 * are skipped. Returns severity-desc (analyzeDrill re-sorts).
 *
 * `ranges` optionally overrides the per-phase ideal bands (per-exercise overrides or
 * personal-baseline recentering). When omitted, the global PER_PHASE_RANGES are used —
 * so the default path is byte-identical and the golden suites stay green.
 */
export function decidePatternCues(
  perPhase: Record<string, Partial<Record<Phase, number | null>>>,
  settings: FeedbackSettings,
  ranges?: PerPhaseRanges,
): FeedbackCue[] {
  const enabled = new Set<string>(settings.enabledMetrics as MetricKey[])
  const lookup = (metricKey: string, phase: Phase) =>
    ranges ? (ranges[metricKey as MetricKey]?.[phase] ?? null) : perPhaseRange(metricKey, phase)
  const cues: FeedbackCue[] = []
  for (const metricKey of PATTERN_METRICS) {
    if (!enabled.has(metricKey)) continue
    const phaseValues = perPhase[metricKey]
    if (phaseValues === undefined) continue
    for (const [phaseStr, value] of Object.entries(phaseValues)) {
      const phase = phaseStr as Phase
      if (value === null || value === undefined) continue
      const range = lookup(metricKey, phase)
      if (range === null) continue
      const half = (range.hi - range.lo) / 2
      const center = (range.lo + range.hi) / 2
      const wLo = center - half * settings.bandWidthMult
      const wHi = center + half * settings.bandWidthMult
      let delta: number
      let direction: FeedbackCue['direction']
      if (value > wHi) { delta = value - wHi; direction = 'too_high' }
      else if (value < wLo) { delta = value - wLo; direction = 'too_low' }
      else continue
      if (Math.abs(delta) < settings.minMeaningfulDeltaDeg) continue
      const severity = half > 0 ? Math.abs(delta) / half : 0
      cues.push({ metricKey, direction, deltaFromRange: delta, severity, precision: precisionFor(metricKey), phase })
    }
  }
  return cues.sort((a, b) => b.severity - a.severity)
}
