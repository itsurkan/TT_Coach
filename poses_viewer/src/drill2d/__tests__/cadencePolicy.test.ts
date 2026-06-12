import { describe, expect, it } from 'vitest'
import { FeedbackCadencePolicy } from '../cadencePolicy'
import { FeedbackCue } from '../feedbackCue'

const cue = (severity: number): FeedbackCue => ({
  metricKey: 'elbow_angle', direction: 'too_high', deltaFromRange: 10, severity, precision: 'precise_degrees',
})

describe('FeedbackCadencePolicy', () => {
  it('emits the first cue and suppresses cues within minInterval', () => {
    const p = new FeedbackCadencePolicy(3000, 5000)
    expect(p.offer(0, [cue(1)])).not.toBeNull()
    expect(p.offer(2000, [cue(1)])).toBeNull() // 2s < 3s
    expect(p.offer(3000, [cue(1)])).not.toBeNull() // exactly 3s
  })
  it('picks the highest-severity cue', () => {
    const p = new FeedbackCadencePolicy()
    const out = p.offer(0, [cue(0.5), cue(2.0), cue(1.0)])
    expect(out?.severity).toBe(2.0)
  })
  it('offerPositive only after maxInterval of silence', () => {
    const p = new FeedbackCadencePolicy(3000, 5000)
    expect(p.offer(0, [cue(1)])).not.toBeNull()
    expect(p.offerPositive(4000)).toBe(false) // 4s < 5s
    expect(p.offerPositive(5000)).toBe(true)
  })
  it('reset clears the window', () => {
    const p = new FeedbackCadencePolicy()
    p.offer(0, [cue(1)])
    p.reset()
    expect(p.offer(10, [cue(1)])).not.toBeNull()
  })
})
