import { describe, expect, it } from 'vitest'
import { decideRepCues } from '../decideRepCues'
import { FOREHAND_DRIVE_STANDARD } from '../referenceStandard'
import { DEFAULT_FEEDBACK_SETTINGS } from '../feedbackSettings'
import type { MetricKey } from '../voiceStyle'

const raw = { ...DEFAULT_FEEDBACK_SETTINGS, bandWidthMult: 1, minMeaningfulDeltaDeg: 5 }

describe('decideRepCues', () => {
  it('is silent inside the band', () => {
    expect(decideRepCues({ knee_bend: 127 }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([]) // band 110–145
  })
  it('flags above the band as too_high', () => {
    const cues = decideRepCues({ knee_bend: 160 }, FOREHAND_DRIVE_STANDARD, raw) // hi 145
    expect(cues[0].metricKey).toBe('knee_bend')
    expect(cues[0].direction).toBe('too_high')
  })
  it('flags below the band as too_low', () => {
    const cues = decideRepCues({ knee_bend: 90 }, FOREHAND_DRIVE_STANDARD, raw) // lo 110
    expect(cues[0].direction).toBe('too_low')
  })
  it('respects minMeaningfulDeltaDeg', () => {
    expect(decideRepCues({ knee_bend: 148 }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([]) // dev 3 < 5
  })
  it('widens the band with bandWidthMult', () => {
    const wide = { ...raw, bandWidthMult: 1.4 } // knee widened lo ≈ 103
    expect(decideRepCues({ knee_bend: 105 }, FOREHAND_DRIVE_STANDARD, wide)).toEqual([]) // inside widened
    const cues = decideRepCues({ knee_bend: 88 }, FOREHAND_DRIVE_STANDARD, wide) // ~15 under 103
    expect(cues[0].metricKey).toBe('knee_bend')
    expect(cues[0].direction).toBe('too_low')
  })
  it('honours enabledMetrics', () => {
    const only = { ...raw, enabledMetrics: ['shoulder_angle'] as MetricKey[] }
    expect(decideRepCues({ knee_bend: 90 }, FOREHAND_DRIVE_STANDARD, only)).toEqual([])
  })
  it('never grades elbow_angle — it is a pattern metric (extends on backswing, flexes at contact)', () => {
    // Far below AND far above the band: a single contact instant has no static ideal for the elbow.
    expect(decideRepCues({ elbow_angle: 97 }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
    expect(decideRepCues({ elbow_angle: 168 }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([])
  })
  it('includes hip_flexion when out of band', () => {
    const cues = decideRepCues({ hip_flexion: 100 }, FOREHAND_DRIVE_STANDARD, raw) // band 130–165, lo 130
    expect(cues[0].metricKey).toBe('hip_flexion')
    expect(cues[0].direction).toBe('too_low')
  })
  it('sorts by severity descending', () => {
    const cues = decideRepCues({ shoulder_angle: 110, knee_bend: 100 }, FOREHAND_DRIVE_STANDARD, raw)
    expect(cues[0].severity).toBeGreaterThanOrEqual(cues[1].severity)
  })
})
