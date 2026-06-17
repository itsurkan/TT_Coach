import { describe, expect, it } from 'vitest'
import {
  pickVariedCue,
  unreliableMetricKeys,
  sessionFocus,
  sessionStrengths,
  UNRELIABLE_IQR_DEG,
  MIN_REPS_FOR_RELIABILITY,
  STRENGTH_FRACTION,
  REMINDER_INTERVAL_MS,
  RepAnalysis,
} from '../analyzeDrill'
import { positiveMessage } from '../messageCatalog'
import { FeedbackCue } from '../feedbackCue'
import { ReferenceStandard } from '../referenceStandard'

// --- builders -------------------------------------------------------------
function cue(metricKey: string, severity = 1): FeedbackCue {
  return { metricKey, direction: 'too_high', deltaFromRange: severity * 10, severity, precision: 'precise_degrees' }
}
function rep(metrics: Record<string, number>, cues: FeedbackCue[] = [], placementOk = true): RepAnalysis {
  return { stroke: { startFrame: 0, peakFrame: 1, endFrame: 2 } as never, metrics, perPhase: {}, cues, cameraYawDeg: 0, placementOk }
}
const STD: ReferenceStandard = {
  drillType: 'forehand_drive',
  ranges: {
    elbow_angle: { lo: 100, hi: 140, evidence: 'measured', source: '' },
    knee_bend: { lo: 110, hi: 145, evidence: 'measured', source: '' },
  },
}

// --- EXP-2: unreliableMetricKeys -----------------------------------------
describe('unreliableMetricKeys (EXP-2)', () => {
  it('does NOT flag a metric whose IQR is exactly at the threshold (strict >)', () => {
    // sorted [10,20,30,40] → nearest-rank IQR = q75(idx3)=40 − q25(idx1)=20 = 20 == UNRELIABLE_IQR_DEG
    const reps = [10, 20, 30, 40].map(v => rep({ elbow_angle: v }))
    expect(UNRELIABLE_IQR_DEG).toBe(20)
    expect(unreliableMetricKeys(reps).has('elbow_angle')).toBe(false)
  })
  it('flags a metric whose IQR exceeds the threshold', () => {
    const reps = [10, 20, 30, 45].map(v => rep({ elbow_angle: v })) // IQR = 45−20 = 25 > 20
    expect(unreliableMetricKeys(reps).has('elbow_angle')).toBe(true)
  })
  it('does not flag with fewer than MIN_REPS_FOR_RELIABILITY measured reps', () => {
    expect(MIN_REPS_FOR_RELIABILITY).toBe(4)
    const reps = [10, 40, 90].map(v => rep({ elbow_angle: v })) // huge spread but only 3 reps
    expect(unreliableMetricKeys(reps).has('elbow_angle')).toBe(false)
  })
  it('leaves a consistent metric reliable', () => {
    const reps = [120, 121, 122, 123].map(v => rep({ elbow_angle: v }))
    expect(unreliableMetricKeys(reps).has('elbow_angle')).toBe(false)
  })
  it('ignores reps with placementOk=false', () => {
    const reps = [
      rep({ elbow_angle: 10 }, [], false),
      rep({ elbow_angle: 90 }, [], false),
      rep({ elbow_angle: 11 }, [], false),
      rep({ elbow_angle: 89 }, [], false),
    ]
    expect(unreliableMetricKeys(reps).size).toBe(0)
  })
})

// --- EXP-3: sessionFocus --------------------------------------------------
describe('sessionFocus (EXP-3)', () => {
  it('returns a null focus and a camera message when there are no coachable reps', () => {
    const f = sessionFocus([rep({ elbow_angle: 120 }, [], false)])
    expect(f.metricKey).toBeNull()
    expect(f.total).toBe(0)
    expect(f.message).toMatch(/camera/i)
  })
  it('praises a fully clean set', () => {
    const f = sessionFocus([0, 1, 2].map(() => rep({ elbow_angle: 120 }, [])))
    expect(f.metricKey).toBeNull()
    expect(f.message).toMatch(/Great set/i)
    expect(f.total).toBe(3)
  })
  it('picks the metric flagged on the most reps', () => {
    const reps = [
      rep({}, [cue('elbow_angle')]),
      rep({}, [cue('elbow_angle')]),
      rep({}, [cue('elbow_angle')]),
      rep({}, [cue('knee_bend')]),
    ]
    const f = sessionFocus(reps)
    expect(f.metricKey).toBe('elbow_angle')
    expect(f.count).toBe(3)
    expect(f.total).toBe(4)
  })
  it('breaks a count tie toward the higher-severity metric', () => {
    const reps = [
      rep({}, [cue('elbow_angle', 0.5)]),
      rep({}, [cue('elbow_angle', 0.5)]),
      rep({}, [cue('knee_bend', 3)]),
      rep({}, [cue('knee_bend', 3)]),
    ]
    expect(sessionFocus(reps).metricKey).toBe('knee_bend')
  })
})

// --- EXP-9: sessionStrengths ---------------------------------------------
describe('sessionStrengths (EXP-9)', () => {
  it('counts a reliable, mostly-in-band metric as a strength', () => {
    const reps = [120, 121, 122, 123].map(v => rep({ elbow_angle: v })) // all in [100,140]
    expect(sessionStrengths(reps, new Set(), STD)).toContain('elbow_angle')
  })
  it('excludes a metric flagged unreliable even if it is in-band', () => {
    const reps = [120, 121, 122, 123].map(v => rep({ elbow_angle: v }))
    expect(sessionStrengths(reps, new Set(['elbow_angle']), STD)).not.toContain('elbow_angle')
  })
  it('requires >= STRENGTH_FRACTION of reps in-band', () => {
    expect(STRENGTH_FRACTION).toBe(0.8)
    // 3 of 5 in-band = 0.6 < 0.8 → not a strength
    const reps = [120, 121, 122, 200, 200].map(v => rep({ knee_bend: v }))
    expect(sessionStrengths(reps, new Set(), STD)).not.toContain('knee_bend')
  })
  it('accepts exactly the fraction boundary (4/5 = 0.8)', () => {
    const reps = [115, 116, 117, 118, 999].map(v => rep({ knee_bend: v })) // 4 in [110,145]
    expect(sessionStrengths(reps, new Set(), STD)).toContain('knee_bend')
  })
  it('needs at least MIN_REPS_FOR_RELIABILITY measurements', () => {
    const reps = [120, 121, 122].map(v => rep({ elbow_angle: v }))
    expect(sessionStrengths(reps, new Set(), STD)).not.toContain('elbow_angle')
  })
})

// --- EXP-1: pickVariedCue -------------------------------------------------
describe('pickVariedCue (EXP-1)', () => {
  it('returns null for an empty cue list', () => {
    expect(pickVariedCue([], null, {}, 0)).toBeNull()
  })
  it('prefers a cue for a different metric than the one just spoken', () => {
    const cues = [cue('elbow_angle', 2), cue('knee_bend', 1)]
    expect(pickVariedCue(cues, 'elbow_angle', {}, 0)?.metricKey).toBe('knee_bend')
  })
  it('takes the top cue when no previous metric is set', () => {
    const cues = [cue('elbow_angle', 2), cue('knee_bend', 1)]
    expect(pickVariedCue(cues, null, {}, 0)?.metricKey).toBe('elbow_angle')
  })
  it('suppresses a repeat of the only fault until the reminder interval elapses', () => {
    const cues = [cue('elbow_angle')]
    const spoken = { elbow_angle: 1000 }
    expect(pickVariedCue(cues, 'elbow_angle', spoken, 1000 + REMINDER_INTERVAL_MS - 1)).toBeNull()
    expect(pickVariedCue(cues, 'elbow_angle', spoken, 1000 + REMINDER_INTERVAL_MS)?.metricKey).toBe('elbow_angle')
  })
  it('surfaces a persistent fault the first time even if it equals lastMetric', () => {
    expect(pickVariedCue([cue('elbow_angle')], 'elbow_angle', {}, 5000)?.metricKey).toBe('elbow_angle')
  })
})

// --- EXP-6: positiveMessage rotation -------------------------------------
describe('positiveMessage (EXP-6)', () => {
  it('rotates between consecutive indices', () => {
    expect(positiveMessage(0)).not.toBe(positiveMessage(1))
  })
  it('is stable and non-empty for large and negative indices', () => {
    expect(typeof positiveMessage(-1)).toBe('string')
    expect(positiveMessage(-1).length).toBeGreaterThan(0)
    expect(positiveMessage(1000).length).toBeGreaterThan(0)
  })
  it('wraps around (default arg is index 0)', () => {
    expect(positiveMessage()).toBe(positiveMessage(0))
  })
})
