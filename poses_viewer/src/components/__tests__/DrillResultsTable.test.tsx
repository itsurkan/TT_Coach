import { describe, expect, it } from 'vitest'
import { metricStatus } from '../DrillResultsTable'
import { FOREHAND_DRIVE_STANDARD } from '../../drill2d/referenceStandard'

describe('metricStatus', () => {
  const r = FOREHAND_DRIVE_STANDARD.ranges
  it('flags over/under/ok/na', () => {
    expect(metricStatus(168, r.elbow_angle)).toBe('over')   // band 115–150
    expect(metricStatus(100, r.elbow_angle)).toBe('under')
    expect(metricStatus(130, r.elbow_angle)).toBe('ok')
    expect(metricStatus(undefined, r.elbow_angle)).toBe('n/a')
    expect(metricStatus(130, undefined)).toBe('n/a')
  })
})
