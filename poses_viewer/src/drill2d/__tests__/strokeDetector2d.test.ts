import { describe, expect, it } from 'vitest'
import { detectStrokes } from '../strokeDetector2d'
import { Coco17, Keypoint2D, PoseFrame2D } from '../types'

/**
 * Mirrors StrokeDetector2DTest.kt. Frames where only the right wrist x moves;
 * shoulders/hips fixed with torso length 0.25, so speeds are well-defined in
 * torso-lengths/sec. Peak raw speed of singleStrokeXs at 100 ms: 2.4 torso/s.
 */
function framesFromWristXs(xs: number[], intervalMs = 100): PoseFrame2D[] {
  return xs.map((wx, i) => {
    const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1 }))
    kp[Coco17.LEFT_SHOULDER] = { x: 0.49, y: 0.30, score: 1 }
    kp[Coco17.RIGHT_SHOULDER] = { x: 0.51, y: 0.30, score: 1 }
    kp[Coco17.LEFT_HIP] = { x: 0.49, y: 0.55, score: 1 }
    kp[Coco17.RIGHT_HIP] = { x: 0.51, y: 0.55, score: 1 }
    kp[Coco17.RIGHT_WRIST] = { x: wx, y: 0.5, score: 1 }
    return { frameIndex: i, timestampMs: i * intervalMs, keypoints: kp }
  })
}

function withWristScore(frames: PoseFrame2D[], indices: 'all' | number[], score: number): PoseFrame2D[] {
  return frames.map((f, i) => {
    if (indices !== 'all' && !indices.includes(i)) return f
    const kp = f.keypoints.map((k, j) => (j === Coco17.RIGHT_WRIST ? { ...k, score } : k))
    return { ...f, keypoints: kp }
  })
}

/** Linear 2× resample: same motion at half the frame interval. */
function resample2x(xs: number[]): number[] {
  return xs.flatMap((x, i) => (i === xs.length - 1 ? [x] : [x, (x + xs[i + 1]) / 2]))
}

// still — accelerate to peak — decelerate — still
const singleStrokeXs = [
  0.50, 0.50, 0.50, 0.50,
  0.51, 0.53, 0.57, 0.63, 0.68, 0.71, 0.72,
  0.72, 0.72, 0.72, 0.72,
]

describe('detectStrokes (mirrors StrokeDetector2DTest)', () => {
  it('detects a single stroke', () => {
    const strokes = detectStrokes(framesFromWristXs(singleStrokeXs), 'right', 1, 100)
    expect(strokes).toHaveLength(1)
    const s = strokes[0]
    expect(s.peakFrame).toBeGreaterThanOrEqual(6)
    expect(s.peakFrame).toBeLessThanOrEqual(8)
    expect(s.startFrame).toBeLessThan(s.peakFrame)
    expect(s.endFrame).toBeGreaterThan(s.peakFrame)
    expect(s.strokeIndex).toBe(0)
  })

  it('detects two strokes with a gap', () => {
    const xs = [...singleStrokeXs, ...[...singleStrokeXs].reverse()]
    const strokes = detectStrokes(framesFromWristXs(xs), 'right', 1, 100)
    expect(strokes).toHaveLength(2)
    expect(strokes[1].peakFrame - strokes[0].peakFrame).toBeGreaterThanOrEqual(5)
    expect(strokes.map(s => s.strokeIndex)).toEqual([0, 1])
  })

  it('sub-threshold jitter yields no strokes', () => {
    const xs = Array.from({ length: 30 }, (_, i) => 0.5 + (i % 2 === 0 ? 0.002 : -0.002))
    expect(detectStrokes(framesFromWristXs(xs), 'right', 1, 100)).toHaveLength(0)
  })

  it('ms-based tuning survives an fps change (L-02)', () => {
    const xs50 = resample2x(singleStrokeXs)
    const strokes = detectStrokes(framesFromWristXs(xs50, 50), 'right', 1, 50)
    expect(strokes).toHaveLength(1)
  })

  it('low-score wrist frames contribute zero speed', () => {
    const frames = withWristScore(framesFromWristXs(singleStrokeXs), 'all', 0.1)
    expect(detectStrokes(frames, 'right', 1, 100)).toHaveLength(0)
  })

  it('empty and tiny inputs are safe', () => {
    expect(detectStrokes([], 'right', 1, 100)).toHaveLength(0)
    expect(detectStrokes(framesFromWristXs([0.5]), 'right', 1, 100)).toHaveLength(0)
  })

  it('detection is deterministic', () => {
    const frames = framesFromWristXs(singleStrokeXs)
    expect(detectStrokes(frames, 'right', 1, 100)).toEqual(detectStrokes(frames, 'right', 1, 100))
  })

  it('refractory keeps the taller of two nearby peaks (keep-max NMS)', () => {
    const xs = [
      0.50, 0.50, 0.50,
      0.54, 0.58, 0.605, 0.610,
      0.63, 0.70, 0.74,
      0.75, 0.75, 0.75,
    ]
    const strokes = detectStrokes(framesFromWristXs(xs), 'right', 1, 100)
    expect(strokes).toHaveLength(1)
    expect(strokes[0].peakFrame).toBeGreaterThanOrEqual(8)
  })

  it('adjacent strokes never overlap (valley clamp)', () => {
    const xs = [0.50, 0.52, 0.56, 0.62, 0.68, 0.72, 0.72, 0.68, 0.62, 0.56, 0.52, 0.50]
    const strokes = detectStrokes(framesFromWristXs(xs), 'right', 1, 100)
    expect(strokes).toHaveLength(2)
    expect(strokes[0].endFrame).toBeLessThanOrEqual(strokes[1].startFrame)
  })

  it('ms-based smoothing gives the same stroke count at any fps', () => {
    const xs100 = [
      0.50, 0.52, 0.56, 0.62,
      0.62, 0.645, 0.675, 0.660, 0.640,
      0.650, 0.670, 0.720, 0.790, 0.830,
      0.830, 0.830,
    ]
    const at100 = detectStrokes(framesFromWristXs(xs100), 'right', 1, 100)
    const at50 = detectStrokes(framesFromWristXs(resample2x(xs100), 50), 'right', 1, 50)
    expect(at100).toHaveLength(2)
    expect(at50).toHaveLength(at100.length)
  })

  it('ms-based minGap suppresses sub-gap peaks at any fps', () => {
    const xs100 = [
      0.50, 0.50, 0.53, 0.61, 0.64, 0.655, 0.685, 0.735,
      0.765, 0.765, 0.765, 0.765,
    ]
    const opts = { peakWindowRadiusMs: 100 }
    const at100 = detectStrokes(framesFromWristXs(xs100), 'right', 1, 100, opts)
    const at50 = detectStrokes(framesFromWristXs(resample2x(xs100), 50), 'right', 1, 50, opts)
    expect(at100).toHaveLength(1)
    expect(at50).toHaveLength(1)
  })

  it('brief mid-stroke occlusion does not split the stroke', () => {
    const frames = withWristScore(framesFromWristXs(singleStrokeXs), [6, 7], 0.1)
    const strokes = detectStrokes(frames, 'right', 1, 100)
    expect(strokes.length).toBeLessThanOrEqual(1)
  })
})
