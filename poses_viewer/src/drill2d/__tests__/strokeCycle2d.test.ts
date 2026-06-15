import { describe, expect, it } from 'vitest'
import { makeCycle, Stroke2D } from '../types'

/** Mirrors StrokeCycle2DTest.kt. */
const stroke = (
  i: number,
  start: number,
  peak: number,
  end: number,
  speed = 2.4,
): Stroke2D => ({
  strokeIndex: i,
  startFrame: start,
  peakFrame: peak,
  endFrame: end,
  peakSpeed: speed,
})

describe('StrokeCycle2D (mirrors StrokeCycle2DTest)', () => {
  it('paired cycle forwards fields from drive', () => {
    const backswing = stroke(0, 10, 20, 30)
    const drive = stroke(1, 30, 45, 60, 3.1)
    const cycle = makeCycle(backswing, drive)

    expect(cycle.peakFrame).toBe(drive.peakFrame)
    expect(cycle.peakSpeed).toBe(drive.peakSpeed)
    expect(cycle.startFrame).toBe(backswing.startFrame)
    expect(cycle.endFrame).toBe(drive.endFrame)
  })

  it('unpaired cycle uses drive startFrame', () => {
    const drive = stroke(0, 30, 45, 60, 2.8)
    const cycle = makeCycle(null, drive)

    expect(cycle.backswing).toBeNull()
    expect(cycle.peakFrame).toBe(drive.peakFrame)
    expect(cycle.peakSpeed).toBe(drive.peakSpeed)
    expect(cycle.startFrame).toBe(drive.startFrame)
    expect(cycle.endFrame).toBe(drive.endFrame)
  })
})
