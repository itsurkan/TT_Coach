import { describe, expect, it } from 'vitest'
import { filterReps } from '../repFilter'
import { Stroke2D } from '../types'

/** Mirrors RepFilterTest.kt. */
const stroke = (i: number, peakSpeed: number, durFrames: number): Stroke2D => ({
  strokeIndex: i,
  startFrame: i * 30,
  peakFrame: i * 30 + Math.floor(durFrames / 2),
  endFrame: i * 30 + durFrames,
  peakSpeed,
})

describe('filterReps (mirrors RepFilterTest)', () => {
  it('uniform strokes all kept', () => {
    const s = Array.from({ length: 6 }, (_, i) => stroke(i, 2.4, 6))
    expect(filterReps(s)).toEqual(s)
  })

  it('slow and overlong junk dropped', () => {
    const good = Array.from({ length: 6 }, (_, i) => stroke(i, 2.4, 6))
    const slow = stroke(6, 1.1, 6)   // ball pickup: above detector threshold, half the cluster speed
    const smear = stroke(7, 2.4, 20) // walking: long movement, plausible peak
    expect(filterReps([...good, slow, smear])).toEqual(good)
  })

  it('too few strokes are not filtered', () => {
    const s = [stroke(0, 2.4, 6), stroke(1, 0.5, 30), stroke(2, 5, 2)]
    expect(filterReps(s)).toEqual(s)
  })
})
