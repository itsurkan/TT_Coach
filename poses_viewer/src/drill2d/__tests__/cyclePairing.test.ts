import { describe, expect, it } from 'vitest'
import { MAX_PAIR_GAP_MS, pairCycles } from '../cyclePairing'
import { Keypoint2D, PoseFrame2D, Stroke2D } from '../types'

/** Builds minimal frames with explicit timestamps. Only timestampMs matters for pairing. */
function frames(timestampsMs: number[]): PoseFrame2D[] {
  const dummyKp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1 }))
  return timestampsMs.map((ts, i) => ({ frameIndex: i, timestampMs: ts, keypoints: dummyKp }))
}

const stroke = (index: number, start: number, peak: number, end: number, speed = 2.0): Stroke2D =>
  ({ strokeIndex: index, startFrame: start, peakFrame: peak, endFrame: end, peakSpeed: speed })

// -------------------------------------------------------------------------
// Case (a): drive WITH an adjacent dropped backswing within gap → paired
// -------------------------------------------------------------------------
describe('pairCycles (mirrors CyclePairingTest)', () => {
  it('(a) drive with adjacent dropped backswing within gap is paired', () => {
    // Frame layout (each frame = 100 ms):
    //   frame 0  ts=0    backswing starts
    //   frame 2  ts=200  backswing peak (raw, dropped by FSF)
    //   frame 4  ts=400  drive starts
    //   frame 6  ts=600  drive peak (forward)
    //   frame 8  ts=800  drive ends
    // Gap = 600 - 200 = 400 ms < 800 ms → paired.
    const ts = Array.from({ length: 9 }, (_, i) => i * 100)
    const f = frames(ts)

    const backswingRaw = stroke(0, 0, 2, 4)
    const driveRaw     = stroke(1, 4, 6, 8)

    const cycles = pairCycles([backswingRaw, driveRaw], [driveRaw], f, 100)

    expect(cycles).toHaveLength(1)
    expect(cycles[0].backswing).toEqual(backswingRaw)
    expect(cycles[0].drive).toEqual(driveRaw)
    expect(cycles[0].startFrame).toBe(0)   // backswing.startFrame
    expect(cycles[0].endFrame).toBe(8)
    expect(cycles[0].peakFrame).toBe(6)
  })

  // -------------------------------------------------------------------------
  // Case (b): drive with NO preceding dropped peak → backswing = null
  // -------------------------------------------------------------------------
  it('(b) drive with no dropped peak has null backswing', () => {
    const ts = Array.from({ length: 5 }, (_, i) => i * 100)
    const f = frames(ts)

    const driveRaw = stroke(0, 0, 2, 4)

    const cycles = pairCycles([driveRaw], [driveRaw], f, 100)

    expect(cycles).toHaveLength(1)
    expect(cycles[0].backswing).toBeNull()
    expect(cycles[0].drive).toEqual(driveRaw)
    expect(cycles[0].startFrame).toBe(0)  // drive.startFrame when no backswing
    expect(cycles[0].endFrame).toBe(4)
  })

  // -------------------------------------------------------------------------
  // Case (c): dropped peak OUTSIDE maxPairGapMs → not paired
  // -------------------------------------------------------------------------
  it('(c) dropped peak outside maxPairGapMs is not paired', () => {
    // backswing peak at ts=0, drive peak at ts=900 → gap = 900 ms > 800 ms.
    const ts = [0, 100, 200, 400, 600, 700, 800, 900, 1000]
    const f = frames(ts)

    const backswingRaw = stroke(0, 0, 0, 3)
    const driveRaw     = stroke(1, 3, 7, 8)

    const cycles = pairCycles([backswingRaw, driveRaw], [driveRaw], f, 100)

    expect(cycles).toHaveLength(1)
    expect(cycles[0].backswing).toBeNull()
    expect(cycles[0].startFrame).toBe(driveRaw.startFrame)
  })

  // -------------------------------------------------------------------------
  // Case (d): one dropped peak between two drives → attaches to LATER drive only
  // -------------------------------------------------------------------------
  it('(d) dropped peak between two drives attaches to later drive only', () => {
    // Timeline:
    //   frame 0–2  drive1 (forward, peakFrame=1)
    //   frame 3–5  dropped backswing (raw only, peakFrame=4)
    //   frame 6–8  drive2 (forward, peakFrame=7)
    //
    // Drive1 prevPeak=-1; candidate window: (-1, 1) → no dropped peak (dropped.peakFrame=4 > 1).
    // Drive2 prevPeak=1; candidate window: (1, 7) → dropped at frame 4 qualifies.
    const ts = Array.from({ length: 9 }, (_, i) => i * 100)
    const f = frames(ts)

    const drive1   = stroke(0, 0, 1, 2)
    const dropped  = stroke(1, 3, 4, 5)
    const drive2   = stroke(2, 6, 7, 8)

    const cycles = pairCycles([drive1, dropped, drive2], [drive1, drive2], f, 100)

    expect(cycles).toHaveLength(2)
    expect(cycles[0].backswing).toBeNull()          // drive1 has no dropped peak before it
    expect(cycles[1].drive).toEqual(drive2)
    expect(cycles[1].backswing).toEqual(dropped)    // dropped pairs to the LATER drive2
    expect(cycles[1].startFrame).toBe(3)             // dropped.startFrame
  })

  // -------------------------------------------------------------------------
  // Case (e): first drive never pairs to a peak before frame 0
  // (lower bound is -1, so frame 0 IS a valid candidate for the first drive)
  // -------------------------------------------------------------------------
  it('(e) dropped peak at frame 0 is valid for the first drive (prevPeak = -1)', () => {
    const ts = Array.from({ length: 6 }, (_, i) => i * 100)
    const f = frames(ts)

    const backswingRaw = stroke(0, 0, 0, 1)
    const driveRaw     = stroke(1, 1, 3, 5)
    // gap = frames[3].ts - frames[0].ts = 300 - 0 = 300 ms < 800 ms → pair

    const cycles = pairCycles([backswingRaw, driveRaw], [driveRaw], f, 100)

    expect(cycles).toHaveLength(1)
    expect(cycles[0].backswing).toEqual(backswingRaw)
    expect(cycles[0].startFrame).toBe(0)
  })

  // -------------------------------------------------------------------------
  // Boundary: gap exactly at MAX_PAIR_GAP_MS → paired (≤, not <)
  // -------------------------------------------------------------------------
  it('dropped peak exactly at MAX_PAIR_GAP_MS is paired (≤)', () => {
    // backswing peak at frame 1 (ts=100), drive peak at frame 3 (ts=900).
    // gap = 900 - 100 = 800 = MAX_PAIR_GAP_MS → paired.
    const ts = [0, 100, 500, 900, 1000]
    const f = frames(ts)

    const backswingRaw = stroke(0, 0, 1, 2)
    const driveRaw     = stroke(1, 2, 3, 4)

    const cycles = pairCycles([backswingRaw, driveRaw], [driveRaw], f, 100)

    expect(cycles).toHaveLength(1)
    expect(cycles[0].backswing).toEqual(backswingRaw)
  })

  it('MAX_PAIR_GAP_MS constant equals 800', () => {
    expect(MAX_PAIR_GAP_MS).toBe(800)
  })
})
