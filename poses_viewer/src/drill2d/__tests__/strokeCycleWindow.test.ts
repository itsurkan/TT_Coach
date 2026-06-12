import { describe, it, expect } from 'vitest'
import { Coco17, Keypoint2D, PoseFrame2D, Stroke2D } from '../types'
import { cycleWindow } from '../strokeCycleWindow'

/** Frame with the right wrist at a given image-y (y grows DOWNWARD: larger = lower). */
function frame(idx: number, wristY: number, score = 1, intervalMs = 100): PoseFrame2D {
  const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1 }))
  kp[Coco17.RIGHT_WRIST] = { x: 0.5, y: wristY, score }
  return { frameIndex: idx, timestampMs: idx * intervalMs, keypoints: kp }
}

function framesFromYs(ys: number[], intervalMs = 100): PoseFrame2D[] {
  return ys.map((y, i) => frame(i, y, 1, intervalMs))
}

const stroke = (startFrame: number, peakFrame: number, endFrame: number): Stroke2D => ({
  strokeIndex: 0, startFrame, peakFrame, endFrame, peakSpeed: 3,
})

describe('cycleWindow', () => {
  // Low→high drive: y high (low body) at the load, y low (high body) at the finish.
  const profile = [
    0.5, 0.5, 0.5, 0.5, 0.5, // 0-4 ready
    0.70,                    // 5  LOAD (lowest physical point)
    0.62, 0.55, 0.50, 0.47,  // 6-9 forward swing
    0.45,                    // 10 PEAK (contact)
    0.40, 0.35, 0.28, 0.24,  // 11-14 follow-through up
    0.20,                    // 15 FINISH (highest physical point)
    0.30, 0.38, 0.42, 0.45, 0.48, // 16-20 recovery
  ]

  it('starts at the lowest wrist point before the peak and ends at the highest after it', () => {
    const w = cycleWindow(framesFromYs(profile), stroke(8, 10, 13), 'right', 0, 20, 100)
    expect(w).toEqual({ startFrame: 5, endFrame: 15 })
  })

  it('never searches past the neighbour-peak bounds', () => {
    const w = cycleWindow(framesFromYs(profile), stroke(8, 10, 13), 'right', 8, 12, 100)
    // max-y in [8,10] is frame 8 (0.50); min-y in [10,12] is frame 12 (0.35)
    expect(w).toEqual({ startFrame: 8, endFrame: 12 })
  })

  it('falls back to the stroke boundary when the wrist is gated across a sub-range', () => {
    // Wrist gated (score 0) for frames 0..10 → no low point measurable before the peak.
    const ys = [0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.4, 0.3, 0.25, 0.2, 0.18, 0.3]
    const frames = ys.map((y, i) => frame(i, y, i <= 10 ? 0 : 1))
    const w = cycleWindow(frames, stroke(8, 10, 13), 'right', 0, 16, 100)
    expect(w.startFrame).toBe(8) // unchanged from stroke.startFrame
    expect(w.endFrame).toBe(15)  // lowest y on the scored tail
  })

  it('rejects a one-frame spike via smoothing', () => {
    // Broad genuine load at 4-6; a single-frame spike at 8 would win without smoothing.
    const ys = [
      0.5, 0.5, 0.5, 0.5,
      0.68, 0.72, 0.68, // 4-6 broad load
      0.45, 0.72, 0.45, // 7-9: frame 8 is a 1-frame spike
      0.45,             // 10 peak
      0.4, 0.35, 0.3, 0.25, 0.2, 0.3, 0.4, 0.4, 0.4, 0.4,
    ]
    const w = cycleWindow(framesFromYs(ys, 50), stroke(9, 10, 11), 'right', 0, 20, 50)
    expect(w.startFrame).toBe(5) // the smoothed broad load, not the spike at 8
  })

  it('caps the search span around the peak', () => {
    const ys = new Array(41).fill(0.4)
    ys[10] = 0.45 // peak
    ys[12] = 0.2  // near high point (within cap)
    ys[30] = 0.05 // far high point (beyond cap) must be ignored
    const w = cycleWindow(framesFromYs(ys), stroke(9, 10, 11), 'right', 0, 50, 100, undefined, 300)
    expect(w.endFrame).toBe(12)
  })
})
