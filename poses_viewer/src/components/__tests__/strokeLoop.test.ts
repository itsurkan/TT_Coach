import { describe, it, expect } from 'vitest'
import { loopBackTarget } from '../strokeLoop'

describe('loopBackTarget', () => {
  it('returns null while inside the segment', () => {
    expect(loopBackTarget(500, 200, 1000)).toBeNull()
    expect(loopBackTarget(200, 200, 1000)).toBeNull() // at start
  })

  it('returns startMs when playhead reaches or passes the end', () => {
    expect(loopBackTarget(1000, 200, 1000)).toBe(200) // exactly at end
    expect(loopBackTarget(1200, 200, 1000)).toBe(200) // overshot
  })

  it('never loops a zero-length or inverted segment', () => {
    expect(loopBackTarget(500, 1000, 1000)).toBeNull()
    expect(loopBackTarget(500, 1000, 200)).toBeNull()
  })
})
