import { MetricPrecision } from './metricPrecision'

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
}
