import { describe, expect, it } from 'vitest'
import { median } from '../median'
import { xScaleFor, MAX_YAW_DEG } from '../geometry'
import { facingSign, DEFAULT_MIN_SCORE } from '../facing'
import { Coco17, Keypoint2D } from '../types'

describe('median', () => {
  it('odd count returns middle', () => expect(median([3, 1, 2])).toBe(2))
  it('even count returns mean of middles', () => expect(median([4, 1, 3, 2])).toBe(2.5))
})

describe('xScaleFor (mirrors ViewGeometry)', () => {
  it('yaw 0 returns aspect ratio unchanged', () => {
    expect(xScaleFor(1, 0)).toBe(1)
    expect(xScaleFor(720 / 1280, 0)).toBeCloseTo(0.5625, 6)
  })
  it('yaw 60 doubles the scale (1/cos60 = 2)', () => {
    expect(xScaleFor(1, 60)).toBeCloseTo(2, 4)
  })
  it('sign-irrelevant (cos is even)', () => {
    expect(xScaleFor(1, -30)).toBeCloseTo(xScaleFor(1, 30), 8)
  })
  it('rejects yaw beyond the hard math limit', () => {
    expect(() => xScaleFor(1, MAX_YAW_DEG + 1)).toThrow()
  })
})

describe('facingSign (mirrors AngleCalculations2D.facingSign)', () => {
  const kp = (noseX: number | null, noseScore = 1): Keypoint2D[] => {
    const out: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1 }))
    if (noseX !== null) out[Coco17.NOSE] = { x: noseX, y: 0.15, score: noseScore }
    return out
  }
  it('nose right of shoulder-mid → +1', () => expect(facingSign(kp(0.55), 0.5, DEFAULT_MIN_SCORE)).toBe(1))
  it('nose left of shoulder-mid → -1', () => expect(facingSign(kp(0.45), 0.5, DEFAULT_MIN_SCORE)).toBe(-1))
  it('dead-centered within epsilon → null', () => expect(facingSign(kp(0.5), 0.5, DEFAULT_MIN_SCORE)).toBeNull())
  it('gated nose falls back to ear midpoint', () => {
    const k = kp(0.55, 0.1) // nose below minScore
    k[Coco17.LEFT_EAR] = { x: 0.58, y: 0.15, score: 1 }
    k[Coco17.RIGHT_EAR] = { x: 0.56, y: 0.15, score: 1 }
    expect(facingSign(k, 0.5, DEFAULT_MIN_SCORE)).toBe(1)
  })
  it('all head keypoints gated → null', () => {
    const k = kp(0.55, 0.1)
    k[Coco17.LEFT_EAR] = { x: 0.58, y: 0.15, score: 0.1 }
    k[Coco17.RIGHT_EAR] = { x: 0.56, y: 0.15, score: 0.1 }
    expect(facingSign(k, 0.5, DEFAULT_MIN_SCORE)).toBeNull()
  })
})
