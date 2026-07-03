import { describe, expect, it } from 'vitest'
import { shoulderWidth, estimateCoil, COIL_OPENED_RATIO } from '../shoulderCoil'
import type { Keypoint2D, PoseFrame2D, StrokeCycle2D, Stroke2D } from '../types'
import { makeCycle } from '../types'
import { Coco17 } from '../types'

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

/** Build a 17-keypoint frame (all COCO-17 slots) with default score 1.0. */
function makeFrame(frameIndex: number, overrides: Partial<Record<number, Keypoint2D>>): PoseFrame2D {
  const keypoints: Keypoint2D[] = Array.from({ length: 17 }, (_, i) => ({
    x: 0.5,
    y: 0.5,
    score: 1.0,
    ...overrides[i],
  }))
  return { frameIndex, timestampMs: frameIndex * 33, keypoints }
}

/** Minimal Stroke2D stub. */
function makeStroke(startFrame: number, peakFrame: number, endFrame: number): Stroke2D {
  return { strokeIndex: 0, startFrame, peakFrame, endFrame, peakSpeed: 5 }
}

// Convenience: build a PoseFrame2D with a specific shoulder width (|rs.x - ls.x| * xScale).
// We control ls.x=0.4, rs.x = 0.4 + widthNorm (before xScale), xScale=1 so width = widthNorm.
function frameWithShoulderWidth(frameIndex: number, widthNorm: number): PoseFrame2D {
  return makeFrame(frameIndex, {
    [Coco17.LEFT_SHOULDER]:  { x: 0.4,              y: 0.5, score: 1.0 },
    [Coco17.RIGHT_SHOULDER]: { x: 0.4 + widthNorm,  y: 0.5, score: 1.0 },
  })
}

// ---------------------------------------------------------------------------
// shoulderWidth
// ---------------------------------------------------------------------------

describe('shoulderWidth', () => {
  it('returns |rs.x - ls.x| * xScale when both shoulders are above minScore', () => {
    const kp: Keypoint2D[] = Array.from({ length: 17 }, (_, i) => ({ x: 0.5, y: 0.5, score: 1.0 }))
    kp[Coco17.LEFT_SHOULDER]  = { x: 0.3, y: 0.5, score: 0.9 }
    kp[Coco17.RIGHT_SHOULDER] = { x: 0.7, y: 0.5, score: 0.9 }
    expect(shoulderWidth(kp, 1, 0.3)).toBeCloseTo(0.4)
  })

  it('applies xScale to the x delta', () => {
    const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1.0 }))
    kp[Coco17.LEFT_SHOULDER]  = { x: 0.2, y: 0.5, score: 1.0 }
    kp[Coco17.RIGHT_SHOULDER] = { x: 0.6, y: 0.5, score: 1.0 }
    // xScale = 2: width = 0.4 * 2 = 0.8
    expect(shoulderWidth(kp, 2, 0.3)).toBeCloseTo(0.8)
  })

  it('returns null when left shoulder score is below minScore', () => {
    const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1.0 }))
    kp[Coco17.LEFT_SHOULDER]  = { x: 0.3, y: 0.5, score: 0.1 } // gated
    kp[Coco17.RIGHT_SHOULDER] = { x: 0.7, y: 0.5, score: 0.9 }
    expect(shoulderWidth(kp, 1, 0.3)).toBeNull()
  })

  it('returns null when right shoulder score is below minScore', () => {
    const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1.0 }))
    kp[Coco17.LEFT_SHOULDER]  = { x: 0.3, y: 0.5, score: 0.9 }
    kp[Coco17.RIGHT_SHOULDER] = { x: 0.7, y: 0.5, score: 0.1 } // gated
    expect(shoulderWidth(kp, 1, 0.3)).toBeNull()
  })
})

// ---------------------------------------------------------------------------
// estimateCoil
// ---------------------------------------------------------------------------

describe('estimateCoil', () => {
  const INTERVAL_MS = 33
  const XSCALE = 1

  it('returns null when cycle.backswing is null (unpaired drive)', () => {
    const frames: PoseFrame2D[] = [
      frameWithShoulderWidth(0, 0.1),
      frameWithShoulderWidth(1, 0.2),
      frameWithShoulderWidth(2, 0.3),
    ]
    const drive = makeStroke(0, 1, 2)
    const cycle = makeCycle(null, drive)  // unpaired — no backswing
    expect(estimateCoil(cycle, frames, XSCALE, INTERVAL_MS)).toBeNull()
  })

  it('returns "opened" when follow-through width is clearly wider than backswing width', () => {
    // backswingAnchor = drive.startFrame = 5 → narrow width 0.1
    // followthroughAnchor = drive.endFrame = 15 → wide width 0.2 (ratio 2.0 >= 1.25)
    const frames: PoseFrame2D[] = Array.from({ length: 20 }, (_, i) => {
      // Frames around index 5: narrow (0.1); frames around index 15: wide (0.2)
      const width = i <= 7 ? 0.1 : 0.2
      return frameWithShoulderWidth(i, width)
    })
    const backswing = makeStroke(0, 2, 4)
    const drive = makeStroke(5, 10, 15)
    const cycle = makeCycle(backswing, drive)
    const result = estimateCoil(cycle, frames, XSCALE, INTERVAL_MS)
    expect(result).not.toBeNull()
    expect(result!.label).toBe('opened')
    expect(result!.ratio).toBeGreaterThanOrEqual(COIL_OPENED_RATIO)
  })

  it('returns "limited" when widths are roughly equal (ratio < COIL_OPENED_RATIO)', () => {
    // backswingAnchor frame 5: width 0.15; followthrough frame 15: width 0.16
    // ratio ~1.07 < 1.25 → 'limited'
    const frames: PoseFrame2D[] = Array.from({ length: 20 }, (_, i) => {
      const width = i <= 7 ? 0.15 : 0.16
      return frameWithShoulderWidth(i, width)
    })
    const backswing = makeStroke(0, 2, 4)
    const drive = makeStroke(5, 10, 15)
    const cycle = makeCycle(backswing, drive)
    const result = estimateCoil(cycle, frames, XSCALE, INTERVAL_MS)
    expect(result).not.toBeNull()
    expect(result!.label).toBe('limited')
    expect(result!.ratio).toBeLessThan(COIL_OPENED_RATIO)
  })

  it('returns null when the backswing anchor window has no valid shoulder widths (score gated)', () => {
    // Frames around backswingAnchor (frame 5) have low shoulder scores → gated
    const frames: PoseFrame2D[] = Array.from({ length: 20 }, (_, i) => {
      const gated = i >= 4 && i <= 6
      const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1.0 }))
      kp[Coco17.LEFT_SHOULDER]  = { x: 0.4, y: 0.5, score: gated ? 0.05 : 1.0 }
      kp[Coco17.RIGHT_SHOULDER] = { x: 0.6, y: 0.5, score: gated ? 0.05 : 1.0 }
      return { frameIndex: i, timestampMs: i * 33, keypoints: kp }
    })
    const backswing = makeStroke(0, 2, 4)
    const drive = makeStroke(5, 10, 15) // backswingAnchor = startFrame = 5 → gated window
    const cycle = makeCycle(backswing, drive)
    // radiusMs=70, intervalMs=33 → radius=2, window=[3,7]; frames 4,5,6 are gated.
    // Frames 3 and 7 are NOT gated → median still resolves. Let's gate more frames to make the window empty:
    // Use radiusMs=0 (single frame at anchor 5 only) to ensure only gated frames in window.
    expect(estimateCoil(cycle, frames, XSCALE, INTERVAL_MS, 0.3, 0)).toBeNull()
  })

  it('returns null when the follow-through anchor window has no valid shoulder widths (score gated)', () => {
    const frames: PoseFrame2D[] = Array.from({ length: 20 }, (_, i) => {
      const gated = i === 15  // only the exact followthrough anchor is gated
      const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1.0 }))
      kp[Coco17.LEFT_SHOULDER]  = { x: 0.4, y: 0.5, score: gated ? 0.05 : 1.0 }
      kp[Coco17.RIGHT_SHOULDER] = { x: 0.6, y: 0.5, score: gated ? 0.05 : 1.0 }
      return { frameIndex: i, timestampMs: i * 33, keypoints: kp }
    })
    const backswing = makeStroke(0, 2, 4)
    const drive = makeStroke(5, 10, 15)
    const cycle = makeCycle(backswing, drive)
    // radiusMs=0 → only the exact frame at endFrame=15 in the window, which is gated.
    expect(estimateCoil(cycle, frames, XSCALE, INTERVAL_MS, 0.3, 0)).toBeNull()
  })

  it('returns null when the follow-through projected width is zero (degenerate/glitch frame)', () => {
    // backswingAnchor frame 5: valid width 0.1; followthroughAnchor frame 15: width 0 → degenerate.
    // Zero projected width is unmeasurable (shoulders perfectly overlapping in projection) — not a
    // real "limited coil" — so the guard must be symmetric: both anchors reject width === 0.
    const frames: PoseFrame2D[] = Array.from({ length: 20 }, (_, i) => {
      const width = i === 15 ? 0 : 0.1   // exact followthrough anchor: zero width
      return frameWithShoulderWidth(i, width)
    })
    const backswing = makeStroke(0, 2, 4)
    const drive = makeStroke(5, 10, 15)
    const cycle = makeCycle(backswing, drive)
    // radiusMs=0 → single-frame window at each anchor; followthrough width = 0 → null.
    expect(estimateCoil(cycle, frames, XSCALE, INTERVAL_MS, 0.3, 0)).toBeNull()
  })
})
