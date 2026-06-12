import { describe, expect, it } from 'vitest'
import { evaluateRep } from '../feedbackEngine'
import { FOREHAND_DRIVE_STANDARD } from '../referenceStandard'

describe('feedbackEngine.evaluateRep', () => {
  it('emits no cue for a metric inside its ideal band', () => {
    const cues = evaluateRep({ elbow_angle: 130 }, FOREHAND_DRIVE_STANDARD) // band 115–150
    expect(cues).toHaveLength(0)
  })
  it('emits TOO_HIGH above the band with signed delta and severity', () => {
    const cues = evaluateRep({ elbow_angle: 168 }, FOREHAND_DRIVE_STANDARD) // hi 150, half-width 17.5
    expect(cues).toHaveLength(1)
    expect(cues[0].direction).toBe('too_high')
    expect(cues[0].deltaFromRange).toBeCloseTo(18, 4) // 168 - 150
    expect(cues[0].severity).toBeCloseTo(18 / 17.5, 4)
    expect(cues[0].precision).toBe('precise_degrees')
  })
  it('emits TOO_LOW below the band', () => {
    const cues = evaluateRep({ knee_bend: 90 }, FOREHAND_DRIVE_STANDARD) // lo 110
    expect(cues[0].direction).toBe('too_low')
    expect(cues[0].deltaFromRange).toBeCloseTo(-20, 4)
  })
  it('skips metrics that are disabled', () => {
    const cues = evaluateRep({ elbow_angle: 168 }, FOREHAND_DRIVE_STANDARD, new Set(['knee_bend']))
    expect(cues).toHaveLength(0)
  })
  it('sorts cues by descending severity', () => {
    const cues = evaluateRep(
      { elbow_angle: 160, knee_bend: 50 }, // elbow +10/17.5 ; knee -60/17.5
      FOREHAND_DRIVE_STANDARD,
    )
    expect(cues[0].metricKey).toBe('knee_bend')
  })
})
