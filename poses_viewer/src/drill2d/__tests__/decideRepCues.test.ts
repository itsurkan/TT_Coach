import { describe, expect, it } from 'vitest'
import { decideRepCues, decidePatternCues } from '../decideRepCues'
import { FOREHAND_DRIVE_STANDARD } from '../referenceStandard'
import type { ReferenceStandard } from '../referenceStandard'
import { DEFAULT_FEEDBACK_SETTINGS } from '../feedbackSettings'
import type { MetricKey } from '../voiceStyle'
import type { Phase } from '../drillMetrics'

const raw = { ...DEFAULT_FEEDBACK_SETTINGS, bandWidthMult: 1, minMeaningfulDeltaDeg: 5 }

/**
 * Synthetic reference standard for single-instant (non-pattern) metric tests.
 * Uses keys that are not in PATTERN_METRICS so decideRepCues will grade them.
 * Band 110–145 (center 127.5, half 17.5) matches the old knee_bend band so the
 * numeric assertions below are identical to what the knee_bend tests had.
 */
const TEST_STANDARD: ReferenceStandard = {
  drillType: 'test',
  ranges: {
    test_metric:  { lo: 110, hi: 145, evidence: 'coach_opinion', source: 'test' },
    test_metric2: { lo: 30,  hi: 75,  evidence: 'coach_opinion', source: 'test' },
  },
}

/**
 * Settings for the TEST_STANDARD suite — includes the synthetic keys in enabledMetrics
 * since DEFAULT_FEEDBACK_SETTINGS only lists the real metric keys.
 */
const rawTest = { ...raw, enabledMetrics: ['test_metric', 'test_metric2'] as unknown as MetricKey[] }

describe('decideRepCues', () => {
  it('is silent inside the band', () => {
    // test_metric band 110–145; 127 is comfortably inside
    expect(decideRepCues({ test_metric: 127 }, TEST_STANDARD, rawTest)).toEqual([])
  })
  it('flags above the band as too_high', () => {
    const cues = decideRepCues({ test_metric: 160 }, TEST_STANDARD, rawTest) // hi 145
    expect(cues[0].metricKey).toBe('test_metric')
    expect(cues[0].direction).toBe('too_high')
  })
  it('flags below the band as too_low', () => {
    const cues = decideRepCues({ test_metric: 90 }, TEST_STANDARD, rawTest) // lo 110
    expect(cues[0].direction).toBe('too_low')
  })
  it('respects minMeaningfulDeltaDeg', () => {
    // test_metric hi=145; value 148 → delta=3 < 5 → suppressed
    expect(decideRepCues({ test_metric: 148 }, TEST_STANDARD, rawTest)).toEqual([])
  })
  it('widens the band with bandWidthMult', () => {
    // test_metric center=127.5, half=17.5; mult=1.4 → wLo ≈ 127.5 - 24.5 = 103
    const wide = { ...rawTest, bandWidthMult: 1.4 }
    expect(decideRepCues({ test_metric: 105 }, TEST_STANDARD, wide)).toEqual([]) // inside widened
    const cues = decideRepCues({ test_metric: 88 }, TEST_STANDARD, wide) // ~15 under 103
    expect(cues[0].metricKey).toBe('test_metric')
    expect(cues[0].direction).toBe('too_low')
  })
  it('honours enabledMetrics', () => {
    // test_metric excluded → out-of-band produces no cue
    const only = { ...rawTest, enabledMetrics: ['test_metric2'] as unknown as MetricKey[] }
    expect(decideRepCues({ test_metric: 90 }, TEST_STANDARD, only)).toEqual([])
  })
  it('sorts by severity descending', () => {
    // test_metric2 band 30–75; values 110 (way above, delta=35) vs test_metric 90 (below 110, delta=-20)
    const cues = decideRepCues({ test_metric: 90, test_metric2: 110 }, TEST_STANDARD, rawTest)
    expect(cues.length).toBe(2)
    expect(cues[0].severity).toBeGreaterThanOrEqual(cues[1].severity)
  })

  // ---------------------------------------------------------------------------
  // Pattern metrics — never graded at the single contact instant
  // ---------------------------------------------------------------------------
  it('never grades any of the five pattern metrics (elbow, shoulder, knee, hip, torso)', () => {
    // Far out of band for all five: decideRepCues must return [] for each.
    expect(decideRepCues({ elbow_angle:    97  }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
    expect(decideRepCues({ elbow_angle:    168 }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
    expect(decideRepCues({ shoulder_angle: 200 }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
    expect(decideRepCues({ shoulder_angle: 0   }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
    expect(decideRepCues({ knee_bend:      200 }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
    expect(decideRepCues({ knee_bend:      50  }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
    expect(decideRepCues({ hip_flexion:    200 }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
    expect(decideRepCues({ hip_flexion:    50  }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
    expect(decideRepCues({ torso_lean:     200 }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
    expect(decideRepCues({ torso_lean:     0   }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
  })
})

// ---------------------------------------------------------------------------
// Per-phase grading
// ---------------------------------------------------------------------------

// elbow per-phase bands: backswing 145–175, followthrough 60–85
// shoulder_angle: backswing 20–60, followthrough 80–130
// knee_bend: backswing 110–130, contact 110–130
// hip_flexion: backswing 115–160, contact 120–165
// torso_lean: backswing 25–45, contact 25–45
type PerPhase = Record<string, Partial<Record<Phase, number | null>>>

describe('decidePatternCues (elbow per-phase)', () => {
  it('flags a too-bent backswing as too_low with phase=backswing', () => {
    const perPhase: PerPhase = { elbow_angle: { backswing: 120 } } // < 145
    const cues = decidePatternCues(perPhase, raw)
    expect(cues).toHaveLength(1)
    expect(cues[0].metricKey).toBe('elbow_angle')
    expect(cues[0].direction).toBe('too_low')
    expect(cues[0].phase).toBe('backswing')
  })

  it('flags a too-straight followthrough as too_high with phase=followthrough', () => {
    const perPhase: PerPhase = { elbow_angle: { followthrough: 110 } } // > 85, did not wrap
    const cues = decidePatternCues(perPhase, raw)
    expect(cues).toHaveLength(1)
    expect(cues[0].direction).toBe('too_high')
    expect(cues[0].phase).toBe('followthrough')
  })

  it('is silent when the followthrough is in band', () => {
    expect(decidePatternCues({ elbow_angle: { followthrough: 80 } }, raw)).toEqual([])
  })

  it('widens the band with bandWidthMult', () => {
    const wide = { ...raw, bandWidthMult: 1.4 } // backswing center 160, half 15 → widened lo ≈ 139
    expect(decidePatternCues({ elbow_angle: { backswing: 142 } }, wide)).toEqual([]) // inside widened
  })

  it('respects minMeaningfulDeltaDeg', () => {
    // backswing lo 145; value 143 → delta -2 < 5 → suppressed
    expect(decidePatternCues({ elbow_angle: { backswing: 143 } }, raw)).toEqual([])
  })

  it('honours enabledMetrics (elbow excluded → no pattern cues)', () => {
    const only = { ...raw, enabledMetrics: ['knee_bend'] as MetricKey[] }
    expect(decidePatternCues({ elbow_angle: { backswing: 120 } }, only)).toEqual([])
  })

  it('skips a null phase value', () => {
    expect(decidePatternCues({ elbow_angle: { backswing: null, followthrough: 80 } }, raw)).toEqual([])
  })

  it('does not grade elbow at contact (no per-phase range there)', () => {
    expect(decidePatternCues({ elbow_angle: { contact: 97 } }, raw)).toEqual([])
  })
})

describe('decidePatternCues (shoulder_angle per-phase)', () => {
  // backswing band 20–60 (center 40, half 20); followthrough 80–130 (center 105, half 25)

  it('flags too-low backswing (arm collapsed, below 20)', () => {
    // value 10 < 20 → delta = 10 - 20 = -10; |delta|=10 ≥ 5
    const cues = decidePatternCues({ shoulder_angle: { backswing: 10 } }, raw)
    expect(cues).toHaveLength(1)
    expect(cues[0].metricKey).toBe('shoulder_angle')
    expect(cues[0].direction).toBe('too_low')
    expect(cues[0].phase).toBe('backswing')
  })

  it('flags too-high followthrough (arm lifting too early)', () => {
    // value 140 > 130 → delta = 140 - 130 = 10; |delta|=10 ≥ 5
    const cues = decidePatternCues({ shoulder_angle: { followthrough: 140 } }, raw)
    expect(cues).toHaveLength(1)
    expect(cues[0].metricKey).toBe('shoulder_angle')
    expect(cues[0].direction).toBe('too_high')
    expect(cues[0].phase).toBe('followthrough')
  })

  it('is silent when both phases are in band', () => {
    expect(decidePatternCues({ shoulder_angle: { backswing: 40, followthrough: 100 } }, raw)).toEqual([])
  })

  it('does NOT grade shoulder_angle at contact (no per-phase range there)', () => {
    // contact=50 is inside the old orphan contact range but that entry is now removed
    expect(decidePatternCues({ shoulder_angle: { contact: 50 } }, raw)).toEqual([])
  })
})

describe('decidePatternCues (knee_bend per-phase)', () => {
  // backswing band 110–130 (center 120, half 10); contact 110–130 (center 120, half 10)

  it('flags too-low at backswing (knee not loaded)', () => {
    // value 90 < 110 → delta = 90 - 110 = -20; |delta|=20 ≥ 5
    const cues = decidePatternCues({ knee_bend: { backswing: 90 } }, raw)
    expect(cues).toHaveLength(1)
    expect(cues[0].metricKey).toBe('knee_bend')
    expect(cues[0].direction).toBe('too_low')
    expect(cues[0].phase).toBe('backswing')
  })

  it('flags too-high at contact (knee too straight)', () => {
    // value 150 > 130 → delta = 150 - 130 = 20; |delta|=20 ≥ 5
    const cues = decidePatternCues({ knee_bend: { contact: 150 } }, raw)
    expect(cues).toHaveLength(1)
    expect(cues[0].metricKey).toBe('knee_bend')
    expect(cues[0].direction).toBe('too_high')
    expect(cues[0].phase).toBe('contact')
  })

  it('does NOT grade knee_bend at followthrough (no per-phase range there)', () => {
    expect(decidePatternCues({ knee_bend: { followthrough: 90 } }, raw)).toEqual([])
  })
})

describe('decidePatternCues (hip_flexion per-phase)', () => {
  // backswing band 115–160 (center 137.5, half 22.5); contact 120–165 (center 142.5, half 22.5)

  it('flags too-low at backswing (hip not loading)', () => {
    // value 100 < 115 → delta = 100 - 115 = -15; |delta|=15 ≥ 5
    const cues = decidePatternCues({ hip_flexion: { backswing: 100 } }, raw)
    expect(cues).toHaveLength(1)
    expect(cues[0].metricKey).toBe('hip_flexion')
    expect(cues[0].direction).toBe('too_low')
    expect(cues[0].phase).toBe('backswing')
  })

  it('is silent when backswing hip is in band', () => {
    expect(decidePatternCues({ hip_flexion: { backswing: 140 } }, raw)).toEqual([])
  })

  it('does NOT grade hip_flexion at followthrough (no per-phase range there)', () => {
    expect(decidePatternCues({ hip_flexion: { followthrough: 100 } }, raw)).toEqual([])
  })

  it('flags too-high at contact (hip too extended)', () => {
    // contact band 120–165; value 175 > 165 → delta = 175 - 165 = 10; |delta|=10 ≥ 5
    const cues = decidePatternCues({ hip_flexion: { contact: 175 } }, raw)
    expect(cues).toHaveLength(1)
    expect(cues[0].metricKey).toBe('hip_flexion')
    expect(cues[0].direction).toBe('too_high')
    expect(cues[0].phase).toBe('contact')
  })

  it('honours enabledMetrics for a new pattern metric', () => {
    const only = { ...raw, enabledMetrics: ['elbow_angle'] as MetricKey[] }
    // hip_flexion far out of band but excluded → no cue
    expect(decidePatternCues({ hip_flexion: { backswing: 50 } }, only)).toEqual([])
  })
})

describe('decidePatternCues (torso_lean per-phase)', () => {
  // backswing band 25–45 (center 35, half 10); contact 25–45 (center 35, half 10)

  it('flags too-high at backswing (leaning forward too early)', () => {
    // value 55 > 45 → delta = 55 - 45 = 10; |delta|=10 ≥ 5
    const cues = decidePatternCues({ torso_lean: { backswing: 55 } }, raw)
    expect(cues).toHaveLength(1)
    expect(cues[0].metricKey).toBe('torso_lean')
    expect(cues[0].direction).toBe('too_high')
    expect(cues[0].phase).toBe('backswing')
  })

  it('flags too-low at contact (not leaning into the shot)', () => {
    // value 8 < 25 → delta = 8 - 25 = -17; |delta|=17 ≥ 5
    const cues = decidePatternCues({ torso_lean: { contact: 8 } }, raw)
    expect(cues).toHaveLength(1)
    expect(cues[0].metricKey).toBe('torso_lean')
    expect(cues[0].direction).toBe('too_low')
    expect(cues[0].phase).toBe('contact')
  })

  it('does NOT grade torso_lean at followthrough (excluded — rotation-corrupted, no coaching ideal)', () => {
    expect(decidePatternCues({ torso_lean: { followthrough: 0 } }, raw)).toEqual([])
  })

  it('skips a null backswing but still grades a valid contact (null-skip is unambiguous)', () => {
    // contact band 15–40; value 8 < 15 → delta = 8 - 15 = -7; |delta|=7 ≥ 5 → would produce a cue if graded.
    // backswing is null → must be skipped. Result must be exactly ONE contact cue (not zero),
    // proving the null skip did NOT suppress the valid contact value alongside it.
    const cues = decidePatternCues({ torso_lean: { backswing: null, contact: 8 } }, raw)
    expect(cues).toHaveLength(1)
    expect(cues[0].metricKey).toBe('torso_lean')
    expect(cues[0].direction).toBe('too_low')
    expect(cues[0].phase).toBe('contact')
  })
})
