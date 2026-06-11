import { describe, expect, it } from 'vitest'
import { filterForwardStrokes } from '../forwardStrokeFilter'
import { Coco17, Keypoint2D, PoseFrame2D, Stroke2D } from '../types'

/** Mirrors ForwardStrokeFilterTest.kt. Player faces +x: nose ahead of shoulder-mid. */
function frames(wristXs: number[], noseX = 0.55): PoseFrame2D[] {
  return wristXs.map((wx, i) => {
    const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1 }))
    kp[Coco17.NOSE] = { x: noseX, y: 0.15, score: 1 }
    kp[Coco17.LEFT_SHOULDER] = { x: 0.49, y: 0.30, score: 1 }
    kp[Coco17.RIGHT_SHOULDER] = { x: 0.51, y: 0.30, score: 1 }
    kp[Coco17.LEFT_HIP] = { x: 0.49, y: 0.55, score: 1 }
    kp[Coco17.RIGHT_HIP] = { x: 0.51, y: 0.55, score: 1 }
    kp[Coco17.RIGHT_WRIST] = { x: wx, y: 0.5, score: 1 }
    return { frameIndex: i, timestampMs: i * 100, keypoints: kp }
  })
}

const stroke = (start: number, peak: number, end: number): Stroke2D =>
  ({ strokeIndex: 0, startFrame: start, peakFrame: peak, endFrame: end, peakSpeed: 2.4 })

/**
 * Session of strokes with controlled dx sign + peakSpeed: stroke i owns frames
 * [3i, 3i+2], wrist sweeping 0.5 → 0.5 + sign·0.1 → 0.5.
 */
function session(specs: Array<[number, number]>, noseX: number): [PoseFrame2D[], Stroke2D[]] {
  const xs: number[] = []
  const strokes: Stroke2D[] = []
  specs.forEach(([sign, speed], i) => {
    const base = 3 * i
    xs.push(0.5, 0.5 + sign * 0.1, 0.5)
    strokes.push({ strokeIndex: i, startFrame: base, peakFrame: base + 1, endFrame: base + 2, peakSpeed: speed })
  })
  return [frames(xs, noseX), strokes]
}

describe('filterForwardStrokes (mirrors ForwardStrokeFilterTest)', () => {
  it('keeps forward stroke, drops recovery swing', () => {
    const xs = [0.50, 0.55, 0.62, 0.70, 0.72, 0.68, 0.60, 0.53, 0.50]
    const f = frames(xs)
    const forward = stroke(0, 3, 4)
    const recovery = stroke(4, 7, 8)
    expect(filterForwardStrokes([forward, recovery], f, 'right')).toEqual([forward])
  })

  it('mirrored player keeps mirrored forward stroke', () => {
    const xs = [0.50, 0.45, 0.38, 0.30, 0.28, 0.32, 0.40, 0.47, 0.50]
    const f = frames(xs, 0.45)
    const forward = stroke(0, 3, 4)
    const recovery = stroke(4, 7, 8)
    expect(filterForwardStrokes([forward, recovery], f, 'right')).toEqual([forward])
  })

  it('indeterminate direction is dropped', () => {
    const xs = [0.50, 0.55, 0.62, 0.70, 0.72]
    const f = frames(xs, 0.50) // nose dead-centered → facing unknown
    expect(filterForwardStrokes([stroke(0, 3, 4)], f, 'right')).toEqual([])
  })

  it('speed dominance keeps the fast group (+x), overriding contradicting head facing', () => {
    const [f, strokes] = session([[1, 8.0], [1, 8.2], [1, 7.9], [-1, 6.0], [-1, 6.1]], 0.45)
    expect(filterForwardStrokes(strokes, f, 'right')).toEqual(strokes.slice(0, 3))
  })

  it('speed dominance keeps the fast group (−x), overriding contradicting head facing', () => {
    const [f, strokes] = session([[-1, 8.0], [-1, 8.2], [-1, 7.9], [1, 6.0], [1, 6.1]], 0.55)
    expect(filterForwardStrokes(strokes, f, 'right')).toEqual(strokes.slice(0, 3))
  })

  it('speed tie falls back to head facing', () => {
    const [f, strokes] = session([[1, 6.0], [1, 6.1], [-1, 6.5], [-1, 6.6]], 0.55)
    expect(filterForwardStrokes(strokes, f, 'right')).toEqual(strokes.slice(0, 2))
  })

  it('a single junk spike cannot flip the vote', () => {
    const [f, strokes] = session([[1, 6.0], [1, 6.0], [1, 6.0], [1, 6.0], [-1, 9.0]], 0.55)
    expect(filterForwardStrokes(strokes, f, 'right')).toEqual(strokes.slice(0, 4))
  })

  it('gated wrist is dropped', () => {
    const xs = [0.50, 0.55, 0.62, 0.70, 0.72]
    const f = frames(xs).map(fr => ({
      ...fr,
      keypoints: fr.keypoints.map((k, j) => (j === Coco17.RIGHT_WRIST ? { ...k, score: 0.1 } : k)),
    }))
    expect(filterForwardStrokes([stroke(0, 3, 4)], f, 'right')).toEqual([])
  })
})
