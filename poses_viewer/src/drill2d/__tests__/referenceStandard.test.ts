import { describe, expect, it } from 'vitest'
import { FOREHAND_DRIVE_STANDARD, METRIC_KEYS, PER_PHASE_RANGES, perPhaseRange } from '../referenceStandard'

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

describe('PER_PHASE_RANGES', () => {
  it('has knee_bend entries for backswing and contact', () => {
    expect(PER_PHASE_RANGES.knee_bend).toBeDefined()
    expect(PER_PHASE_RANGES.knee_bend!.backswing).toBeDefined()
    expect(PER_PHASE_RANGES.knee_bend!.contact).toBeDefined()
  })

  it('has hip_flexion entries for backswing and contact', () => {
    expect(PER_PHASE_RANGES.hip_flexion).toBeDefined()
    expect(PER_PHASE_RANGES.hip_flexion!.backswing).toBeDefined()
    expect(PER_PHASE_RANGES.hip_flexion!.contact).toBeDefined()
  })

  it('has shoulder_angle entries for backswing and followthrough (NOT contact)', () => {
    expect(PER_PHASE_RANGES.shoulder_angle).toBeDefined()
    expect(PER_PHASE_RANGES.shoulder_angle!.backswing).toBeDefined()
    expect(PER_PHASE_RANGES.shoulder_angle!.followthrough).toBeDefined()
    expect(PER_PHASE_RANGES.shoulder_angle!.contact).toBeUndefined()
  })

  it('has torso_lean entries for backswing and contact (NOT followthrough)', () => {
    expect(PER_PHASE_RANGES.torso_lean).toBeDefined()
    expect(PER_PHASE_RANGES.torso_lean!.backswing).toBeDefined()
    expect(PER_PHASE_RANGES.torso_lean!.contact).toBeDefined()
    expect(PER_PHASE_RANGES.torso_lean!.followthrough).toBeUndefined()
  })

  it('has elbow_angle entries for backswing and followthrough (NOT contact)', () => {
    expect(PER_PHASE_RANGES.elbow_angle).toBeDefined()
    expect(PER_PHASE_RANGES.elbow_angle!.backswing).toBeDefined()
    expect(PER_PHASE_RANGES.elbow_angle!.followthrough).toBeDefined()
    expect(PER_PHASE_RANGES.elbow_angle!.contact).toBeUndefined()
  })

  it('every configured entry is well-formed (lo < hi, valid evidence, non-empty source containing PROVISIONAL)', () => {
    for (const [metric, phaseMap] of Object.entries(PER_PHASE_RANGES)) {
      if (phaseMap === undefined) continue
      for (const [phase, range] of Object.entries(phaseMap)) {
        if (range === undefined) continue
        expect(range.lo, `${metric}.${phase} lo < hi`).toBeLessThan(range.hi)
        expect(['measured', 'coach_opinion'], `${metric}.${phase} evidence`).toContain(range.evidence)
        expect(range.source.length, `${metric}.${phase} source non-empty`).toBeGreaterThan(0)
        expect(
          range.source.toLowerCase(),
          `${metric}.${phase} source must contain PROVISIONAL`
        ).toContain('provisional')
      }
    }
  })
})

describe('perPhaseRange', () => {
  it('returns the range for knee_bend at backswing', () => {
    const r = perPhaseRange('knee_bend', 'backswing')
    expect(r).not.toBeNull()
    expect(r!.lo).toBe(110)
    expect(r!.hi).toBe(130)
    expect(r!.evidence).toBe('measured')
  })

  it('returns null for elbow_angle at contact (no per-phase entry)', () => {
    expect(perPhaseRange('elbow_angle', 'contact')).toBeNull()
  })

  it('returns the backswing range for shoulder_angle', () => {
    const r = perPhaseRange('shoulder_angle', 'backswing')
    expect(r).not.toBeNull()
    expect(r!.lo).toBe(20)
    expect(r!.hi).toBe(60)
    expect(r!.evidence).toBe('coach_opinion')
  })

  it('returns null for shoulder_angle at contact (contact excluded for arm metrics)', () => {
    expect(perPhaseRange('shoulder_angle', 'contact')).toBeNull()
  })

  it('returns the backswing range for torso_lean', () => {
    const r = perPhaseRange('torso_lean', 'backswing')
    expect(r).not.toBeNull()
    expect(r!.lo).toBe(25)
    expect(r!.hi).toBe(45)
  })

  it('returns the contact range for torso_lean', () => {
    const r = perPhaseRange('torso_lean', 'contact')
    expect(r).not.toBeNull()
    expect(r!.lo).toBe(25)
    expect(r!.hi).toBe(45)
  })

  it('returns null for torso_lean at followthrough (excluded — rotation-corrupted)', () => {
    expect(perPhaseRange('torso_lean', 'followthrough')).toBeNull()
  })

  it('returns null for a known metric that has no per-phase ranges', () => {
    // shoulder_tilt is the only metric key with no PER_PHASE_RANGES entry
    expect(perPhaseRange('shoulder_tilt', 'contact')).toBeNull()
  })

  it('returns null for a wholly unknown key', () => {
    expect(perPhaseRange('nonsense', 'contact')).toBeNull()
  })

  it('returns the contact range for hip_flexion', () => {
    const r = perPhaseRange('hip_flexion', 'contact')
    expect(r).not.toBeNull()
    expect(r!.lo).toBe(120)
    expect(r!.hi).toBe(165)
  })

  it('returns the followthrough range for shoulder_angle', () => {
    const r = perPhaseRange('shoulder_angle', 'followthrough')
    expect(r).not.toBeNull()
    expect(r!.lo).toBe(80)
    expect(r!.hi).toBe(130)
  })
})

describe('FOREHAND_DRIVE_STANDARD unchanged by per-phase additions', () => {
  it('still has a knee_bend single-instant range', () => {
    expect(FOREHAND_DRIVE_STANDARD.ranges.knee_bend).toBeDefined()
    expect(FOREHAND_DRIVE_STANDARD.ranges.knee_bend.lo).toBe(110)
    expect(FOREHAND_DRIVE_STANDARD.ranges.knee_bend.hi).toBe(145)
  })

  it('still covers all metric keys', () => {
    expect(Object.keys(FOREHAND_DRIVE_STANDARD.ranges).sort()).toEqual([...METRIC_KEYS].sort())
  })
})
