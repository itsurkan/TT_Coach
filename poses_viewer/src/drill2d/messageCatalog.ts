/**
 * Cue → English message string. Adapted from Kotlin FeedbackMessageCatalog but
 * worded against the external IDEAL ("than ideal / off the standard"), not the
 * personal baseline. EN only (spec non-goal: no UA toggle here). Trust rule: the
 * degree number is inserted ONLY for precise-degrees cues.
 */
import { FeedbackCue } from './feedbackCue'
import { METRIC } from './drillMetrics'

export function formatCue(cue: FeedbackCue): string {
  const d = Math.round(Math.abs(cue.deltaFromRange))
  const high = cue.direction === 'too_high'
  const base = phrase(cue.metricKey, high)
  return cue.precision === 'precise_degrees' ? `${base} (about ${d}° off the standard)` : base
}

function phrase(metricKey: string, high: boolean): string {
  switch (metricKey) {
    case METRIC.ELBOW_ANGLE:
      return high ? 'Elbow straighter than ideal — bend it a bit more'
                  : 'Elbow more bent than ideal — open it up a bit'
    case METRIC.SHOULDER_ANGLE:
      return high ? 'Upper arm higher than ideal — drop the elbow a bit'
                  : 'Upper arm lower than ideal — lift the elbow a bit'
    case METRIC.KNEE_BEND:
      return high ? 'Legs straighter than ideal — bend the knees more'
                  : 'Knees more bent than ideal — rise a little'
    case METRIC.TORSO_LEAN:
      return high ? 'Leaning further than ideal — straighten up a bit'
                  : 'More upright than ideal — lean in a little'
    case METRIC.SHOULDER_TILT:
      return high ? 'Shoulders more tilted than ideal — level them'
                  : 'Shoulder line flatter than ideal — let the playing shoulder drop a touch'
    default:
      // Unknown metric (future rotational cues): qualitative-only, never degrees.
      return high ? 'A bit more than ideal on that move — ease off'
                  : 'A bit less than ideal on that move'
  }
}

/** EXP-6: rotate positive reinforcement so a clean streak isn't the same line repeated. */
const POSITIVE_MESSAGES = [
  'Good rep — close to the standard',
  'Nice — that one looked solid',
  'Clean technique, keep it going',
  "That's the shape — repeat it",
  'Looking sharp — stay with it',
]

export function positiveMessage(index = 0): string {
  const n = POSITIVE_MESSAGES.length
  return POSITIVE_MESSAGES[((index % n) + n) % n]
}
