/**
 * Trust rule — precise degrees ONLY for the five in-plane metrics; everything else
 * is qualitative-only. 1:1 mirror of Kotlin MetricPrecisionPolicy.
 */
import { METRIC_KEYS } from './referenceStandard'

export type MetricPrecision = 'precise_degrees' | 'qualitative'

const PRECISE = new Set<string>(METRIC_KEYS)

/** Unknown metrics default to qualitative — never overclaim precision. */
export function precisionFor(metricKey: string): MetricPrecision {
  return PRECISE.has(metricKey) ? 'precise_degrees' : 'qualitative'
}
