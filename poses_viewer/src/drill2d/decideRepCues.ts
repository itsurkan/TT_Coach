/**
 * The single feedback-decision engine. Compares each rep's metrics to the external
 * ideal bands, WIDENED by settings.bandWidthMult, and returns every out-of-band cue
 * (severity-desc). Drives BOTH the table ("Всі зауваження") and the voice
 * (buildSpokenSchedule). With bandWidthMult=1, minMeaningfulDeltaDeg=5 and all
 * metrics enabled it reproduces the retired feedbackEngine.evaluateRep exactly.
 */
import { FeedbackCue } from './feedbackCue'
import { precisionFor } from './metricPrecision'
import { ReferenceStandard } from './referenceStandard'
import { FeedbackSettings } from './feedbackSettings'
import { MetricKey } from './voiceStyle'

/**
 * Pattern metrics describe a movement ACROSS the stroke, not a single target pose, so a
 * single contact-instant value has no meaningful static ideal to grade against.
 *
 * `elbow_angle` is the case in point: it extends on the backswing (~165°) and flexes at
 * contact (~97°), so grading the contact frame against a fixed band produces a cue that
 * reads as nonsense ("extend your arm" while the backswing arm is already fully extended).
 * The literature band stays in referenceStandard.ranges for reference + sessionStrengths,
 * and the per-phase columns still SHOW the start/end angles, but we never emit a cue for it.
 */
const PATTERN_METRICS = new Set<string>(['elbow_angle'])

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
