import { describe, expect, it } from 'vitest'
import { FOREHAND_DRIVE_STANDARD, METRIC_KEYS } from '../referenceStandard'

describe('referenceStandard', () => {
  it('covers all five in-plane metrics', () => {
    expect(Object.keys(FOREHAND_DRIVE_STANDARD.ranges).sort()).toEqual([...METRIC_KEYS].sort())
  })

  it('every range is well-formed (lo < hi, tagged with evidence + source)', () => {
    for (const [key, r] of Object.entries(FOREHAND_DRIVE_STANDARD.ranges)) {
      expect(r.lo, key).toBeLessThan(r.hi)
      expect(['measured', 'coach_opinion']).toContain(r.evidence)
      expect(r.source.length, key).toBeGreaterThan(0)
    }
  })

  it('flags torso lean and shoulder tilt as coach-opinion (no measured source)', () => {
    expect(FOREHAND_DRIVE_STANDARD.ranges.torso_lean.evidence).toBe('coach_opinion')
    expect(FOREHAND_DRIVE_STANDARD.ranges.shoulder_tilt.evidence).toBe('coach_opinion')
  })
})
