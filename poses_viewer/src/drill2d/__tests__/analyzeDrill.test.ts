import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { analyzeDrill } from '../analyzeDrill'
import { parsePoseV2 } from '../parsePoseV2'
import { FOREHAND_DRIVE_STANDARD } from '../referenceStandard'
import { METRIC_PHASES } from '../drillMetrics'
import { DEFAULT_FEEDBACK_SETTINGS } from '../feedbackSettings'

/** Raw-band equivalence: bandWidthMult=1, minMeaningfulDeltaDeg=5 reproduces
 *  the old feedbackEngine.evaluateRep behaviour so existing cue/voice expectations hold. */
const RAW_BAND_SETTINGS = { ...DEFAULT_FEEDBACK_SETTINGS, bandWidthMult: 1, minMeaningfulDeltaDeg: 5 }

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
      feedbackSettings: RAW_BAND_SETTINGS,
      cameraYawDeg: 0, // manual override → placementOk, feedback flows
    })
    expect(report.rawPeakCount).toBe(29)
    expect(report.reps).toHaveLength(15)
  })

  it('auto yaw estimation flags Videos/ footage as bad placement (L-25 saturation)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: null,
    })
    expect(report.placementOk).toBe(false)
  })
})

describe('analyzeDrill — perPhase field', () => {
  it('every rep has a perPhase field', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
    })
    for (const rep of report.reps) {
      expect(rep.perPhase).toBeDefined()
      expect(typeof rep.perPhase).toBe('object')
    }
  })

  it('perPhase contains all METRIC_PHASES keys for each rep', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
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
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
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
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
    })
    const paired = report.reps.filter(r => 'backswing' in r.perPhase.hip_flexion)
    expect(paired.length).toBeGreaterThan(0)
    for (const rep of paired) {
      const hf = rep.perPhase.hip_flexion
      expect('backswing' in hf).toBe(true)
      expect('contact' in hf).toBe(true)
    }
  })

  it('elbow_angle has backswing and followthrough phases (paired cycle), not contact', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
    })
    const paired = report.reps.filter(r => 'backswing' in r.perPhase.elbow_angle)
    expect(paired.length).toBeGreaterThan(0)
    for (const rep of paired) {
      const ea = rep.perPhase.elbow_angle
      expect('backswing' in ea).toBe(true)
      expect('followthrough' in ea).toBe(true)
      expect('contact' in ea).toBe(false)
    }
  })

  it('shoulder_angle has backswing and followthrough phases (NOT contact)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
    })
    // shoulder_angle is now an arm-pattern metric (backswing + followthrough only).
    // Paired reps have backswing; unpaired reps skip it but always have followthrough.
    for (const rep of report.reps) {
      const sa = rep.perPhase.shoulder_angle
      expect('contact' in sa).toBe(false)
      expect('followthrough' in sa).toBe(true)
    }
  })

  it('contact-phase value equals single-instant metrics for metrics measured at contact', () => {
    // The contact anchor is drive.peakFrame, same as extractAtPeak — values must match exactly.
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
    })
    // Metrics that include the 'contact' phase (from METRIC_PHASES subset):
    // knee_bend, hip_flexion, torso_lean. elbow_angle and shoulder_angle are arm-pattern
    // metrics graded at backswing/followthrough (not contact); shoulder_tilt is excluded
    // from METRIC_PHASES entirely.
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
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
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
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
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
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
    })
    for (const rep of report.reps) {
      expect('coil' in rep.perPhase).toBe(false)
    }
  })

  it('coil is NOT a numeric metric key in rep.metrics', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
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
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
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
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
    })
    for (const v of report.voiceReps) {
      expect(v.timing.strokeStartMs).toBeLessThanOrEqual(v.timing.contactMs)
      expect(v.timing.contactMs).toBeLessThanOrEqual(v.timing.strokeEndMs)
    }
  })
  it('with yaw override 0, voiceReps carry RepInput shape (.cues, .timing, .coachable)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD,
      feedbackSettings: RAW_BAND_SETTINGS, cameraYawDeg: 0,
    })
    for (const v of report.voiceReps) {
      expect(Array.isArray(v.cues)).toBe(true)
      expect(typeof v.coachable).toBe('boolean')
      expect(typeof v.timing.strokeStartMs).toBe('number')
      expect(typeof v.timing.contactMs).toBe('number')
      expect(typeof v.timing.strokeEndMs).toBe('number')
    }
    // At least one coachable rep
    expect(report.voiceReps.some(v => v.coachable)).toBe(true)
  })
})
