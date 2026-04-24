import { describe, it, expect } from 'vitest'
import { extractAnchorFromLandmarks } from '../anchorExtractor'
import { reconstructFromAnchor } from '../skeletonReconstructor'
import { NEUTRAL_POSE } from '../neutralPose'

describe('extractAnchorFromLandmarks — round-trip', () => {
  it('recovers rightElbowYawDeg within 5° for a twisted arm', () => {
    const source = {
      ...NEUTRAL_POSE,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 45,
      rightShoulderAbductionDeg: 20,
      rightElbowAngleDeg: 90,
      rightElbowYawDeg: 40,
    }
    const lms = reconstructFromAnchor(source)
    const round = extractAnchorFromLandmarks(lms)
    expect(round.rightElbowYawDeg).toBeGreaterThan(35)
    expect(round.rightElbowYawDeg).toBeLessThan(45)
  })

  it('returns elbowYaw=0 when the elbow is fully extended (no bend plane)', () => {
    const source = {
      ...NEUTRAL_POSE,
      rightElbowAngleDeg: 178,
      rightElbowYawDeg: 30,
    }
    const lms = reconstructFromAnchor(source)
    const round = extractAnchorFromLandmarks(lms)
    expect(Math.abs(round.rightElbowYawDeg)).toBeLessThan(5)
  })

  it('recovers rightWristYawDeg within 4° for a deflected hand', () => {
    const source = {
      ...NEUTRAL_POSE,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 30,
      rightShoulderAbductionDeg: 10,
      rightElbowAngleDeg: 120,
      rightWristAngleDeg: 160,
      rightWristYawDeg: 15,
    }
    const lms = reconstructFromAnchor(source)
    const round = extractAnchorFromLandmarks(lms)
    expect(round.rightWristYawDeg).toBeGreaterThan(11)
    expect(round.rightWristYawDeg).toBeLessThan(19)
  })

  it('recovers leftWristYawDeg with the mirrored sign', () => {
    const source = {
      ...NEUTRAL_POSE,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      leftShoulderAngleDeg: 30,
      leftShoulderAbductionDeg: 10,
      leftElbowAngleDeg: 120,
      leftWristAngleDeg: 160,
      leftWristYawDeg: 15,
    }
    const lms = reconstructFromAnchor(source)
    const round = extractAnchorFromLandmarks(lms)
    expect(round.leftWristYawDeg).toBeGreaterThan(11)
    expect(round.leftWristYawDeg).toBeLessThan(19)
  })
})
