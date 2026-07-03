import { describe, expect, it } from 'vitest'
import { angleDeg, elbowAngle, hipFlexion, shoulderTilt, torsoLean } from '../angles2d'
import { Coco17, Keypoint2D } from '../types'

const K = (x: number, y: number, score = 1): Keypoint2D => ({ x, y, score })

/** Build a 17-length keypoint list, all (0,0,0) unless overridden. */
function pose(overrides: Record<number, Keypoint2D>): Keypoint2D[] {
  const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0, y: 0, score: 0 }))
  for (const [i, k] of Object.entries(overrides)) kp[Number(i)] = k
  return kp
}

describe('angleDeg', () => {
  it('is 90° for a right angle (xScale = 1)', () => {
    expect(angleDeg(K(0, -1), K(0, 0), K(1, 0), 1)).toBeCloseTo(90, 4)
  })
  it('is 180° for a straight line', () => {
    expect(angleDeg(K(-1, 0), K(0, 0), K(1, 0), 1)).toBeCloseTo(180, 4)
  })
  it('applies xScale to x-deltas before trig', () => {
    expect(angleDeg(K(0, -1), K(0, 0), K(1, 0), 2)).toBeCloseTo(90, 4)
    // A 45° vector (dx=1,dy=-1): xScale=2 doubles the x-leg → atan2(1, 2) = 26.57° from +x
    expect(angleDeg(K(1, -1), K(0, 0), K(1, 0), 2)).toBeCloseTo(26.57, 1)
  })
})

describe('elbowAngle', () => {
  it('returns 180° for a straight right arm', () => {
    const kp = pose({
      [Coco17.RIGHT_SHOULDER]: K(0, 0),
      [Coco17.RIGHT_ELBOW]: K(1, 0),
      [Coco17.RIGHT_WRIST]: K(2, 0),
    })
    expect(elbowAngle(kp, 'right', 1)).toBeCloseTo(180, 4)
  })
  it('returns null when a required keypoint is below the score gate', () => {
    const kp = pose({
      [Coco17.RIGHT_SHOULDER]: K(0, 0),
      [Coco17.RIGHT_ELBOW]: K(1, 0, 0.1),
      [Coco17.RIGHT_WRIST]: K(2, 0),
    })
    expect(elbowAngle(kp, 'right', 1)).toBeNull()
  })
})

describe('hipFlexion', () => {
  it('returns ~180° for a straight standing pose (shoulder–hip–knee collinear)', () => {
    // Shoulder above hip, knee below hip — all on the same vertical line
    const kp = pose({
      [Coco17.RIGHT_SHOULDER]: K(0.5, 0.1),
      [Coco17.RIGHT_HIP]:      K(0.5, 0.5),
      [Coco17.RIGHT_KNEE]:     K(0.5, 0.9),
    })
    expect(hipFlexion(kp, 'right', 1)).toBeCloseTo(180, 4)
  })

  it('returns a clearly hinged angle (<180°) when the torso leans forward over the knee', () => {
    // Shoulder shifted forward of hip; knee directly below hip → hip angle < 180°
    const kp = pose({
      [Coco17.RIGHT_SHOULDER]: K(0.7, 0.1),
      [Coco17.RIGHT_HIP]:      K(0.5, 0.5),
      [Coco17.RIGHT_KNEE]:     K(0.5, 0.9),
    })
    const angle = hipFlexion(kp, 'right', 1)
    expect(angle).not.toBeNull()
    expect(angle as number).toBeCloseTo(153.4, 0)
  })

  it('returns null when a required keypoint is below the score gate', () => {
    const kp = pose({
      [Coco17.RIGHT_SHOULDER]: K(0.5, 0.1),
      [Coco17.RIGHT_HIP]:      K(0.5, 0.5, 0.1), // low score → gated
      [Coco17.RIGHT_KNEE]:     K(0.5, 0.9),
    })
    expect(hipFlexion(kp, 'right', 1)).toBeNull()
  })
})

describe('shoulderTilt', () => {
  it('is 0 for level shoulders', () => {
    const kp = pose({ [Coco17.LEFT_SHOULDER]: K(0, 0), [Coco17.RIGHT_SHOULDER]: K(1, 0) })
    expect(shoulderTilt(kp, 1)).toBeCloseTo(0, 4)
  })
})

describe('torsoLean', () => {
  it('returns null when facing is indeterminate (no head keypoints)', () => {
    const kp = pose({
      [Coco17.LEFT_SHOULDER]: K(0, 0), [Coco17.RIGHT_SHOULDER]: K(1, 0),
      [Coco17.LEFT_HIP]: K(0, 1), [Coco17.RIGHT_HIP]: K(1, 1),
    })
    expect(torsoLean(kp, 1)).toBeNull()
  })
  it('is signed positive when leaning toward the facing direction', () => {
    const kp = pose({
      [Coco17.NOSE]: K(1.0, -1),
      [Coco17.LEFT_SHOULDER]: K(0.3, 0), [Coco17.RIGHT_SHOULDER]: K(0.7, 0),
      [Coco17.LEFT_HIP]: K(0, 1), [Coco17.RIGHT_HIP]: K(0.4, 1),
    })
    const lean = torsoLean(kp, 1)
    expect(lean).not.toBeNull()
    expect(lean as number).toBeGreaterThan(0)
  })
})
