import { describe, expect, it } from 'vitest'
import { formatCue, positiveMessage } from '../messageCatalog'
import { FeedbackCue } from '../feedbackCue'

const cue = (over: Partial<FeedbackCue>): FeedbackCue => ({
  metricKey: 'elbow_angle', direction: 'too_high', deltaFromRange: 18,
  severity: 1, precision: 'precise_degrees', ...over,
})

describe('messageCatalog', () => {
  it('inserts the degree number for precise-degrees cues', () => {
    const msg = formatCue(cue({ deltaFromRange: 18 }))
    expect(msg).toMatch(/18°/)
    expect(msg.toLowerCase()).toContain('ideal')
  })
  it('omits the degree number for qualitative cues', () => {
    const msg = formatCue(cue({ metricKey: 'wrist_snap', precision: 'qualitative' }))
    expect(msg).not.toMatch(/\d+°/)
  })
  it('distinguishes too-high from too-low wording', () => {
    const hi = formatCue(cue({ direction: 'too_high' }))
    const lo = formatCue(cue({ direction: 'too_low', deltaFromRange: -18 }))
    expect(hi).not.toEqual(lo)
  })
  it('has a positive message', () => {
    expect(positiveMessage().length).toBeGreaterThan(0)
  })
  it('formats a hip_flexion cue', () => {
    const up = formatCue({ metricKey: 'hip_flexion', direction: 'too_high', deltaFromRange: 8, severity: 1, precision: 'precise_degrees' })
    expect(up.toLowerCase()).toContain('hip')
  })
})
