import { MetricPrecision } from './metricPrecision'
// Type-only import avoids a runtime cycle (drillMetrics imports angle fns;
// feedbackCue is a leaf consumed downstream) — mirrors referenceStandard.ts.
import type { Phase } from './drillMetrics'

export type CueDirection = 'too_high' | 'too_low'

/** One actionable deviation of a rep metric from the external ideal band. */
export interface FeedbackCue {
  metricKey: string
  direction: CueDirection
  /** Signed degrees outside the band: + above hi, − below lo. */
  deltaFromRange: number
  /** |deltaFromRange| / band half-width — ranks cues for the cadence window. */
  severity: number
  precision: MetricPrecision
  /** Set for PATTERN-metric cues graded at a specific stroke phase (elbow). Absent
   *  for single-instant cues; phase-aware phrase lookup + staleness keys depend on it. */
  phase?: Phase
}
