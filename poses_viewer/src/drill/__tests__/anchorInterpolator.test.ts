import { describe, it, expect } from 'vitest'
import { lerpAnchor, interpolateAnchors } from '../anchorInterpolator'
import { NEUTRAL_POSE } from '../neutralPose'

describe('lerpAnchor', () => {
  it('t=0 returns start', () => {
    const a = { ...NEUTRAL_POSE, rightElbowAngleDeg: 170 }
    const b = { ...NEUTRAL_POSE, rightElbowAngleDeg: 90 }
    const out = lerpAnchor(a, b, 0)
    expect(out.rightElbowAngleDeg).toBe(170)
  })
  it('t=1 returns end', () => {
    const a = { ...NEUTRAL_POSE, rightElbowAngleDeg: 170 }
    const b = { ...NEUTRAL_POSE, rightElbowAngleDeg: 90 }
    const out = lerpAnchor(a, b, 1)
    expect(out.rightElbowAngleDeg).toBe(90)
  })
  it('t=0.5 returns midpoint', () => {
    const a = { ...NEUTRAL_POSE, rightElbowAngleDeg: 180, bodyRotationDeg: 0 }
    const b = { ...NEUTRAL_POSE, rightElbowAngleDeg: 90,  bodyRotationDeg: 60 }
    const out = lerpAnchor(a, b, 0.5)
    expect(out.rightElbowAngleDeg).toBeCloseTo(135, 5)
    expect(out.bodyRotationDeg).toBeCloseTo(30, 5)
  })
})

describe('interpolateAnchors', () => {
  it('endpoints are start and end exactly', () => {
    const a = { ...NEUTRAL_POSE, bodyRotationDeg: -45 }
    const b = { ...NEUTRAL_POSE, bodyRotationDeg:  45 }
    const frames = interpolateAnchors(a, b, 10)
    expect(frames).toHaveLength(10)
    expect(frames[0].bodyRotationDeg).toBe(-45)
    expect(frames[9].bodyRotationDeg).toBe(45)
  })
  it('produces evenly spaced values', () => {
    const a = { ...NEUTRAL_POSE, bodyRotationDeg: 0 }
    const b = { ...NEUTRAL_POSE, bodyRotationDeg: 90 }
    const frames = interpolateAnchors(a, b, 10)
    for (let i = 1; i < frames.length; i++) {
      const step = frames[i].bodyRotationDeg - frames[i - 1].bodyRotationDeg
      expect(step).toBeCloseTo(10, 5)
    }
  })
  it('handles count=1 as start-only', () => {
    const a = { ...NEUTRAL_POSE, bodyRotationDeg: 42 }
    const b = { ...NEUTRAL_POSE, bodyRotationDeg: 88 }
    const frames = interpolateAnchors(a, b, 1)
    expect(frames).toHaveLength(1)
    expect(frames[0].bodyRotationDeg).toBe(42)
  })
})
