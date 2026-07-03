import { describe, expect, it } from 'vitest'
import {
  FOCUS_TO_METRICS, FOCUS_AREAS,
  defaultExercise, normalizeExercise,
  effectiveEnabledMetrics, effectiveBandWidthMult,
  deriveBaselineStandard, deriveBaselinePerPhase, mergePerPhaseOverrides,
  MIN_BASELINE_SAMPLES,
  type Exercise, type BaselineRep, type PerPhaseRanges,
} from '../exercise'
import { VOICE_METRIC_KEYS } from '../voiceStyle'
import { DEFAULT_FEEDBACK_SETTINGS } from '../feedbackSettings'
import { FOREHAND_DRIVE_STANDARD, PER_PHASE_RANGES, type ReferenceRange } from '../referenceStandard'

describe('focus → active metrics', () => {
  it('maps each focus area to its metrics', () => {
    expect(FOCUS_TO_METRICS.arm).toEqual(['elbow_angle', 'shoulder_angle'])
    expect(FOCUS_TO_METRICS.legs).toEqual(['knee_bend', 'hip_flexion'])
    expect(FOCUS_TO_METRICS.hip).toEqual(['hip_flexion'])
  })

  it('returns metrics in canonical VOICE_METRIC_KEYS order regardless of focus order', () => {
    const ex = { ...defaultExercise(), focusAreas: ['hip', 'arm'] as Exercise['focusAreas'] }
    // arm → elbow,shoulder; hip → hip_flexion. Canonical order keeps elbow<shoulder<hip.
    expect(effectiveEnabledMetrics(ex)).toEqual(['elbow_angle', 'shoulder_angle', 'hip_flexion'])
  })

  it('dedupes hip_flexion shared by legs + hip', () => {
    const ex = { ...defaultExercise(), focusAreas: ['legs', 'hip'] as Exercise['focusAreas'] }
    expect(effectiveEnabledMetrics(ex)).toEqual(['knee_bend', 'hip_flexion'])
  })

  it('empty focus ⇒ all metrics (no narrowing)', () => {
    const ex = { ...defaultExercise(), focusAreas: [] }
    expect(effectiveEnabledMetrics(ex)).toEqual([...VOICE_METRIC_KEYS])
  })

  it('exposes one chip per focus area', () => {
    expect(FOCUS_AREAS.map(f => f.id)).toEqual(['arm', 'shoulder', 'legs', 'torso', 'hip'])
  })
})

describe('golden-parity guard', () => {
  it('the default exercise reproduces the default enabled metrics exactly', () => {
    // If this breaks, the built-in preset no longer matches pre-feature behaviour and
    // the golden suites (decideRepCues/golden) would diverge.
    expect(effectiveEnabledMetrics(defaultExercise())).toEqual([...DEFAULT_FEEDBACK_SETTINGS.enabledMetrics])
  })

  it('default strictness is a no-op on the band-width multiplier', () => {
    expect(effectiveBandWidthMult(defaultExercise(), 1.4)).toBe(1.4)
  })
})

describe('strictness composition', () => {
  it('higher strictness narrows the band (smaller multiplier)', () => {
    expect(effectiveBandWidthMult({ ...defaultExercise(), strictness: 2 }, 1.4)).toBeCloseTo(0.7)
  })
  it('lower strictness widens the band', () => {
    expect(effectiveBandWidthMult({ ...defaultExercise(), strictness: 0.5 }, 1.4)).toBeCloseTo(2.8)
  })
  it('guards against non-positive strictness', () => {
    expect(effectiveBandWidthMult({ ...defaultExercise(), strictness: 0 }, 1.4)).toBe(1.4)
  })
})

describe('normalizeExercise', () => {
  it('backfills missing fields over the default and keeps id/name', () => {
    const n = normalizeExercise({ id: 'x', name: 'Mine' })
    expect(n).toMatchObject({
      id: 'x', name: 'Mine', drillType: 'forehand_drive',
      referenceSource: 'standard', strictness: 1, builtin: false,
    })
    expect(n.focusAreas.length).toBe(5)
  })
  it('drops invalid focus areas and non-positive strictness', () => {
    const n = normalizeExercise({ id: 'x', name: 'Y', focusAreas: ['arm', 'bogus'] as never, strictness: -3 })
    expect(n.focusAreas).toEqual(['arm'])
    expect(n.strictness).toBe(1)
  })
})

// --- personal-baseline derivation ---

function repWith(metrics: Record<string, number>, perPhase: BaselineRep['perPhase'] = {}, placementOk = true): BaselineRep {
  return { metrics, perPhase, placementOk }
}

describe('deriveBaselineStandard', () => {
  it('recenters a band on the median, keeping its width', () => {
    const reps = [repWith({ shoulder_tilt: 30 }), repWith({ shoulder_tilt: 34 }), repWith({ shoulder_tilt: 32 })]
    const out = deriveBaselineStandard(reps, FOREHAND_DRIVE_STANDARD)
    const base = FOREHAND_DRIVE_STANDARD.ranges.shoulder_tilt
    const width = base.hi - base.lo
    expect(out.ranges.shoulder_tilt.hi - out.ranges.shoulder_tilt.lo).toBeCloseTo(width)
    // median of [30,32,34] = 32 → center 32
    expect((out.ranges.shoulder_tilt.lo + out.ranges.shoulder_tilt.hi) / 2).toBeCloseTo(32)
  })

  it('keeps the textbook band when there are too few samples', () => {
    const reps = [repWith({ shoulder_tilt: 30 }), repWith({ shoulder_tilt: 34 })] // < MIN_BASELINE_SAMPLES
    expect(MIN_BASELINE_SAMPLES).toBe(3)
    const out = deriveBaselineStandard(reps, FOREHAND_DRIVE_STANDARD)
    expect(out.ranges.shoulder_tilt).toEqual(FOREHAND_DRIVE_STANDARD.ranges.shoulder_tilt)
  })

  it('ignores reps with bad camera placement', () => {
    const reps = [
      repWith({ shoulder_tilt: 30 }), repWith({ shoulder_tilt: 32 }), repWith({ shoulder_tilt: 34 }),
      repWith({ shoulder_tilt: 999 }, {}, false), // excluded
    ]
    const out = deriveBaselineStandard(reps, FOREHAND_DRIVE_STANDARD)
    expect((out.ranges.shoulder_tilt.lo + out.ranges.shoulder_tilt.hi) / 2).toBeCloseTo(32)
  })
})

describe('deriveBaselinePerPhase', () => {
  it('recenters per-phase pattern bands on the per-phase median', () => {
    const reps: BaselineRep[] = [
      repWith({}, { knee_bend: { contact: 120 } }),
      repWith({}, { knee_bend: { contact: 124 } }),
      repWith({}, { knee_bend: { contact: 122 } }),
    ]
    const out = deriveBaselinePerPhase(reps, PER_PHASE_RANGES)
    const base = PER_PHASE_RANGES.knee_bend!.contact!
    const width = base.hi - base.lo
    const got = out.knee_bend!.contact!
    expect(got.hi - got.lo).toBeCloseTo(width)
    expect((got.lo + got.hi) / 2).toBeCloseTo(122)
  })

  it('keeps the base band for a phase with too few measured values (nulls ignored)', () => {
    const reps: BaselineRep[] = [
      repWith({}, { knee_bend: { contact: null } }),
      repWith({}, { knee_bend: { contact: 124 } }),
    ]
    const out = deriveBaselinePerPhase(reps, PER_PHASE_RANGES)
    expect(out.knee_bend!.contact).toEqual(PER_PHASE_RANGES.knee_bend!.contact)
  })
})

describe('mergePerPhaseOverrides', () => {
  it('returns the base map unchanged when no overrides', () => {
    expect(mergePerPhaseOverrides(PER_PHASE_RANGES)).toBe(PER_PHASE_RANGES)
  })
  it('lets an override win per metric+phase while keeping the rest', () => {
    const override: PerPhaseRanges = {
      knee_bend: { contact: { lo: 100, hi: 140, evidence: 'coach_opinion', source: 't' } as ReferenceRange },
    }
    const out = mergePerPhaseOverrides(PER_PHASE_RANGES, override)
    expect(out.knee_bend!.contact).toEqual(override.knee_bend!.contact)
    // backswing (not overridden) preserved from base
    expect(out.knee_bend!.backswing).toEqual(PER_PHASE_RANGES.knee_bend!.backswing)
    // other metrics preserved
    expect(out.elbow_angle).toEqual(PER_PHASE_RANGES.elbow_angle)
  })
})
