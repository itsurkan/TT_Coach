import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { analyzeDrill } from '../analyzeDrill'
import { parsePoseV2 } from '../parsePoseV2'
import { FOREHAND_DRIVE_STANDARD } from '../referenceStandard'
import { METRIC_PHASES } from '../drillMetrics'

// Mirror golden.test.ts's fixture path pattern (repo-relative).
function loadSeq(name: string) {
  const p = resolve(__dirname, '../../../../shared/src/commonTest/resources/fixtures', name)
  return parsePoseV2(JSON.parse(readFileSync(p, 'utf-8')))
}

describe('analyzeDrill — count parity (anti-drift guardrail)', () => {
  it('andrii_1 produces 15 reps from 29 raw peaks (direction-aware NMS, TS-diverged)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right',
      drillType: 'forehand_drive',
      standard: FOREHAND_DRIVE_STANDARD,
      cameraYawDeg: 0, // manual override → placementOk, feedback flows
    })
    expect(report.rawPeakCount).toBe(29)
    expect(report.reps).toHaveLength(15)
  })

  it('produces a metric map per rep and (with yaw override) at least one spoken line', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    expect(report.reps.every(r => typeof r.metrics === 'object')).toBe(true)
    expect(report.reps.every(r => r.placementOk)).toBe(true) // yaw override 0
    expect(report.feedback.length).toBeGreaterThan(0)
    // cadence: consecutive spoken lines are ≥ 3 s apart
    for (let i = 1; i < report.feedback.length; i++) {
      expect(report.feedback[i].timestampMs - report.feedback[i - 1].timestampMs).toBeGreaterThanOrEqual(3000)
    }
  })

  it('auto yaw estimation flags Videos/ footage as bad placement (L-25 saturation)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: null,
    })
    expect(report.placementOk).toBe(false)
  })
})

describe('analyzeDrill — perPhase field', () => {
  it('every rep has a perPhase field', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    for (const rep of report.reps) {
      expect(rep.perPhase).toBeDefined()
      expect(typeof rep.perPhase).toBe('object')
    }
  })

  it('perPhase contains all METRIC_PHASES keys for each rep', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    const metricKeys = Object.keys(METRIC_PHASES)
    for (const rep of report.reps) {
      for (const key of metricKeys) {
        expect(rep.perPhase).toHaveProperty(key)
      }
    }
  })

  it('knee_bend has backswing and contact phases (paired cycle)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    // Find at least one rep with a backswing (paired cycle)
    const paired = report.reps.filter(r => 'backswing' in r.perPhase.knee_bend)
    expect(paired.length).toBeGreaterThan(0)
    for (const rep of paired) {
      const kb = rep.perPhase.knee_bend
      expect('backswing' in kb).toBe(true)
      expect('contact' in kb).toBe(true)
    }
  })

  it('hip_flexion has backswing and contact phases (paired cycle)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    const paired = report.reps.filter(r => 'backswing' in r.perPhase.hip_flexion)
    expect(paired.length).toBeGreaterThan(0)
    for (const rep of paired) {
      const hf = rep.perPhase.hip_flexion
      expect('backswing' in hf).toBe(true)
      expect('contact' in hf).toBe(true)
    }
  })

  it('elbow_angle has backswing and contact phases (paired cycle)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    const paired = report.reps.filter(r => 'backswing' in r.perPhase.elbow_angle)
    expect(paired.length).toBeGreaterThan(0)
    for (const rep of paired) {
      const ea = rep.perPhase.elbow_angle
      expect('backswing' in ea).toBe(true)
      expect('contact' in ea).toBe(true)
    }
  })

  it('shoulder_angle has contact and followthrough phases', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    // shoulder_angle phases are contact and followthrough — present on ALL reps (no backswing required)
    for (const rep of report.reps) {
      const sa = rep.perPhase.shoulder_angle
      expect('contact' in sa).toBe(true)
      expect('followthrough' in sa).toBe(true)
    }
  })

  it('contact-phase value equals single-instant metrics for metrics measured at contact', () => {
    // The contact anchor is drive.peakFrame, same as extractAtPeak — values must match exactly.
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    // Metrics that include the 'contact' phase (from METRIC_PHASES subset):
    // knee_bend, hip_flexion, elbow_angle, shoulder_angle, torso_lean.
    // shoulder_tilt is intentionally excluded from METRIC_PHASES — it is not in perPhase.
    const contactMetrics = Object.entries(METRIC_PHASES)
      .filter(([, phases]) => (phases as string[]).includes('contact'))
      .map(([key]) => key)

    for (const rep of report.reps) {
      for (const key of contactMetrics) {
        const contactVal = rep.perPhase[key]?.contact
        const instantVal = rep.metrics[key]
        if (contactVal === null || contactVal === undefined) {
          // metric was gated (score < threshold) — single-instant also missing
          expect(instantVal).toBeUndefined()
        } else {
          // Both must be present and equal (same window, same computation)
          expect(instantVal).toBeDefined()
          expect(contactVal).toBeCloseTo(instantVal!, 5)
        }
      }
    }
  })

  it('perPhase does NOT contain shoulder_tilt (it is a single-instant colored cell, not per-phase)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    for (const rep of report.reps) {
      expect('shoulder_tilt' in rep.perPhase).toBe(false)
    }
  })
})

describe('analyzeDrill — coil field', () => {
  it('every rep has a coil field (object or null)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    for (const rep of report.reps) {
      expect('coil' in rep).toBe(true)
      // coil is either null or { ratio: number, label: 'opened' | 'limited' }
      if (rep.coil !== null) {
        expect(typeof rep.coil.ratio).toBe('number')
        expect(['opened', 'limited']).toContain(rep.coil.label)
      }
    }
  })

  it('coil does NOT appear in perPhase (it is a separate qualitative field)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    for (const rep of report.reps) {
      expect('coil' in rep.perPhase).toBe(false)
    }
  })

  it('coil is NOT a numeric metric key in rep.metrics', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    for (const rep of report.reps) {
      expect('coil' in rep.metrics).toBe(false)
    }
  })
})

describe('analyzeDrill — voice inputs (style-independent)', () => {
  it('emits one voiceRep and one strokeStartTime per kept rep, with ascending starts', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    expect(report.voiceReps).toHaveLength(report.reps.length)
    expect(report.strokeStartTimes).toHaveLength(report.reps.length)
    for (let i = 1; i < report.strokeStartTimes.length; i++) {
      expect(report.strokeStartTimes[i]).toBeGreaterThanOrEqual(report.strokeStartTimes[i - 1])
    }
  })
  it('places contact between start and end for every voiceRep', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    for (const v of report.voiceReps) {
      expect(v.strokeStartMs).toBeLessThanOrEqual(v.contactMs)
      expect(v.contactMs).toBeLessThanOrEqual(v.strokeEndMs)
    }
  })
  it('with yaw override 0, at least one coachable voiceRep carries observations bounded by the standard', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    const withObs = report.voiceReps.filter(v => v.coachable && Object.keys(v.observations).length > 0)
    expect(withObs.length).toBeGreaterThan(0)
    // never voices hip_flexion; every observation carries the ideal band
    for (const v of report.voiceReps) {
      expect(Object.keys(v.observations)).not.toContain('hip_flexion')
      for (const obs of Object.values(v.observations)) {
        expect(typeof obs!.lo).toBe('number')
        expect(typeof obs!.hi).toBe('number')
      }
    }
  })
})
