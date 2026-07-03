/**
 * drillMetrics.ts — mirrors Kotlin DrillMetricsTest.
 *
 * Key parity notes vs the original task spec:
 *  - A fully straight arm (180°) is OUTSIDE the [20, 170] sanity band and correctly
 *    dropped. Tests use a ~90° elbow (shoulder above elbow, wrist to the right) so
 *    the angle lands inside the sane range.
 *  - The "drops when out of sanity bounds" case uses a wrist position that yields
 *    an insane angle (≈180° straight arm), not a low-score gate — matching Kotlin's
 *    `insaneValueIsDropped` test.
 */
import { describe, expect, it } from 'vitest'
import { extractAtFrame, extractAtPeak, METRIC, ALL_KEYS } from '../drillMetrics'
import { Coco17, Keypoint2D, PoseFrame2D } from '../types'
import { METRIC_KEYS } from '../referenceStandard'

const K = (x: number, y: number, score = 1): Keypoint2D => ({ x, y, score })

/**
 * Frame with a ~90° right elbow: shoulder directly above elbow, wrist to the right.
 * All other keypoints default to (0.5, 0.5, score=1) so they don't accidentally
 * gate torso / knee metrics.
 */
function goodArmFrame(idx: number, wrist: Keypoint2D = K(0.70, 0.42)): PoseFrame2D {
  const kp: Keypoint2D[] = Array.from({ length: 17 }, () => K(0.5, 0.5))
  kp[Coco17.RIGHT_SHOULDER] = K(0.50, 0.22)
  kp[Coco17.RIGHT_ELBOW]    = K(0.50, 0.42)
  kp[Coco17.RIGHT_WRIST]    = wrist
  return { frameIndex: idx, timestampMs: idx * 33, keypoints: kp }
}

/** Fully straight arm (180°) — outside the [20,170] sanity band → dropped. */
function straightArmFrame(idx: number): PoseFrame2D {
  const kp: Keypoint2D[] = Array.from({ length: 17 }, () => K(0.5, 0.5))
  kp[Coco17.RIGHT_SHOULDER] = K(0.50, 0.20)
  kp[Coco17.RIGHT_ELBOW]    = K(0.50, 0.40)
  kp[Coco17.RIGHT_WRIST]    = K(0.50, 0.60)
  return { frameIndex: idx, timestampMs: idx * 33, keypoints: kp }
}

describe('extractAtFrame', () => {
  it('extracts elbow angle when the arm is bent within sanity bounds (~90°)', () => {
    const m = extractAtFrame(goodArmFrame(0), 'right', 1)
    expect(m[METRIC.ELBOW_ANGLE]).toBeDefined()
    expect(m[METRIC.ELBOW_ANGLE]).toBeGreaterThan(20)
    expect(m[METRIC.ELBOW_ANGLE]).toBeLessThan(170)
  })

  it('drops elbow angle when the arm is fully straight (180° > sanity upper bound 170°)', () => {
    const m = extractAtFrame(straightArmFrame(0), 'right', 1)
    expect(m[METRIC.ELBOW_ANGLE]).toBeUndefined()
  })

  it('drops elbow angle when the wrist keypoint is below minScore', () => {
    const kp: Keypoint2D[] = Array.from({ length: 17 }, () => K(0.5, 0.5))
    kp[Coco17.RIGHT_SHOULDER] = K(0.50, 0.22)
    kp[Coco17.RIGHT_ELBOW]    = K(0.50, 0.42)
    kp[Coco17.RIGHT_WRIST]    = K(0.70, 0.42, 0.1) // score below DEFAULT_MIN_SCORE
    const frame: PoseFrame2D = { frameIndex: 0, timestampMs: 0, keypoints: kp }
    expect(extractAtFrame(frame, 'right', 1)[METRIC.ELBOW_ANGLE]).toBeUndefined()
  })

  it('returns empty map for a frame with no keypoints', () => {
    const frame: PoseFrame2D = { frameIndex: 0, timestampMs: 0, keypoints: [] }
    expect(Object.keys(extractAtFrame(frame, 'right', 1))).toHaveLength(0)
  })
})

describe('extractAtPeak', () => {
  it('returns median ~90° over a ±70ms window of identical good frames', () => {
    const frames = [0, 1, 2, 3, 4].map(i => goodArmFrame(i))
    const m = extractAtPeak(frames, 2, 'right', 1, 33, undefined, 70)
    expect(m[METRIC.ELBOW_ANGLE]).toBeDefined()
    expect(m[METRIC.ELBOW_ANGLE]).toBeGreaterThan(20)
    expect(m[METRIC.ELBOW_ANGLE]).toBeLessThan(170)
  })

  it('ignores a jitter frame at the peak when neighbours are consistent', () => {
    // frame 2 (peak) has a wrist that reads ~135°; the other 4 frames are ~90°
    // median of [90, 90, 135, 90, 90] = 90 → jitter suppressed
    const jitteredWrist = K(0.64, 0.56)
    const frames = [
      goodArmFrame(0), goodArmFrame(1),
      goodArmFrame(2, jitteredWrist),
      goodArmFrame(3), goodArmFrame(4),
    ]
    const m = extractAtPeak(frames, 2, 'right', 1, 33, undefined, 70)
    expect(m[METRIC.ELBOW_ANGLE]).toBeCloseTo(
      extractAtFrame(goodArmFrame(0), 'right', 1)[METRIC.ELBOW_ANGLE]!,
      0,
    )
  })

  it('degrades to a single frame when intervalMs > radiusMs (radius = 0)', () => {
    const frames = [goodArmFrame(0), goodArmFrame(1), goodArmFrame(2)]
    const atPeak  = extractAtPeak(frames, 1, 'right', 1, 100, undefined, 70)
    const atFrame = extractAtFrame(frames[1], 'right', 1)
    for (const key of Object.keys(atFrame)) {
      expect(atPeak[key]).toBeCloseTo(atFrame[key], 9)
    }
  })

  it('throws on a non-positive intervalMs', () => {
    const frames = [goodArmFrame(0)]
    expect(() => extractAtPeak(frames, 0, 'right', 1, 0)).toThrow()
  })

  it('throws when peakFrame is out of bounds', () => {
    const frames = [goodArmFrame(0), goodArmFrame(1)]
    expect(() => extractAtPeak(frames, 2, 'right', 1, 33)).toThrow()
    expect(() => extractAtPeak([], 0, 'right', 1, 33)).toThrow()
  })

  it('ALL_KEYS lists the six metric keys', () => {
    expect(ALL_KEYS).toHaveLength(6)
    expect(ALL_KEYS).toContain(METRIC.ELBOW_ANGLE)
    expect(ALL_KEYS).toContain(METRIC.SHOULDER_ANGLE)
    expect(ALL_KEYS).toContain(METRIC.KNEE_BEND)
    expect(ALL_KEYS).toContain(METRIC.TORSO_LEAN)
    expect(ALL_KEYS).toContain(METRIC.SHOULDER_TILT)
    expect(ALL_KEYS).toContain(METRIC.HIP_FLEXION)
  })
})

describe('metric key consistency', () => {
  it('ALL_KEYS matches referenceStandard.METRIC_KEYS (no drift between the two definitions)', () => {
    expect([...ALL_KEYS].sort()).toEqual([...METRIC_KEYS].sort())
  })
})
