import { describe, expect, it } from 'vitest'
import { newlyCrossed } from '../useSpokenFeedback'
import type { SpokenFeedbackItem } from '../../drill2d/buildSpokenSchedule'

const fb = (atMs: number): SpokenFeedbackItem => ({ atMs, text: `m${atMs}`, lang: 'en', kind: 'cue', metricKey: null, estDurationMs: 500 })

describe('newlyCrossed', () => {
  it('returns entries whose atMs is in (prevMs, nowMs]', () => {
    const feed = [fb(1000), fb(2000), fb(3000)]
    expect(newlyCrossed(feed, 900, 2000).map(f => f.atMs)).toEqual([1000, 2000])
  })
  it('returns nothing when time has not advanced past any entry', () => {
    expect(newlyCrossed([fb(1000), fb(2000)], 2000, 2500)).toEqual([])
  })
  it('handles a backward seek (prev > now) by firing nothing', () => {
    expect(newlyCrossed([fb(1000), fb(2000)], 2500, 1500)).toEqual([])
  })
})
