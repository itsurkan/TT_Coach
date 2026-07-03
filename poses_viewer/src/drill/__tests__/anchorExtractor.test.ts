import { describe, it, expect } from 'vitest'
import { extractAnchorFromLandmarks } from '../anchorExtractor'
import { reconstructFromAnchor } from '../skeletonReconstructor'
import { NEUTRAL_POSE } from '../neutralPose'
import { LM } from '../SkeletonModel'

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

  it('recovers rightForearmTwistDeg within 10° from a pronated hand (twist=0 wristYaw)', () => {
    const source = {
      ...NEUTRAL_POSE,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 20,
      rightShoulderAbductionDeg: 10,
      rightElbowAngleDeg: 90,
      rightWristAngleDeg: 170,
      rightWristYawDeg: 0,
      rightForearmTwistDeg: 45,
    }
    const lms = reconstructFromAnchor(source)
    const round = extractAnchorFromLandmarks(lms)
    expect(round.rightForearmTwistDeg).toBeGreaterThan(35)
    expect(round.rightForearmTwistDeg).toBeLessThan(55)
  })

  it('elbowSwivel round-trip: extractor recovers swivel within 1° across diverse arm poses', () => {
    // Pure DOF test for the swivel contract: reconstruct → extract round-trip.
    // The FK and extractor both solve on the same shoulder→wrist circle, so
    // the math is exact — 1° tolerance accommodates float noise only.
    const trunkNeutral = {
      ...NEUTRAL_POSE,
      figureYawDeg: 0,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      torsoSideBendDeg: 0,
      pelvicRollDeg: 0,
      shoulderShrugNorm: 0,
    }
    const rightPoses = [
      { rightShoulderAngleDeg: 45,  rightShoulderAbductionDeg: 25,  rightElbowAngleDeg: 95,  rightElbowYawDeg: 30 },
      { rightShoulderAngleDeg: 90,  rightShoulderAbductionDeg: 60,  rightElbowAngleDeg: 120, rightElbowYawDeg: -40 },
      { rightShoulderAngleDeg: -20, rightShoulderAbductionDeg: 10,  rightElbowAngleDeg: 150, rightElbowYawDeg: 60 },
      { rightShoulderAngleDeg: 130, rightShoulderAbductionDeg: 40,  rightElbowAngleDeg: 80,  rightElbowYawDeg: -60 },
      { rightShoulderAngleDeg: 0,   rightShoulderAbductionDeg: 80,  rightElbowAngleDeg: 90,  rightElbowYawDeg: 20 },
    ]
    for (const overrides of rightPoses) {
      const source = { ...trunkNeutral, ...overrides }
      const lms = reconstructFromAnchor(source)
      const round = extractAnchorFromLandmarks(lms)
      const delta = Math.abs(round.rightElbowYawDeg - overrides.rightElbowYawDeg)
      expect(delta, `R swivel delta for ${JSON.stringify(overrides)}`).toBeLessThan(1)
    }
    // Symmetric left-arm coverage — same poses mirrored.
    const leftPoses = [
      { leftShoulderAngleDeg: 45,  leftShoulderAbductionDeg: 25,  leftElbowAngleDeg: 95,  leftElbowYawDeg: 30 },
      { leftShoulderAngleDeg: 90,  leftShoulderAbductionDeg: 60,  leftElbowAngleDeg: 120, leftElbowYawDeg: -40 },
      { leftShoulderAngleDeg: -20, leftShoulderAbductionDeg: 10,  leftElbowAngleDeg: 150, leftElbowYawDeg: 60 },
      { leftShoulderAngleDeg: 130, leftShoulderAbductionDeg: 40,  leftElbowAngleDeg: 80,  leftElbowYawDeg: -60 },
      { leftShoulderAngleDeg: 0,   leftShoulderAbductionDeg: 80,  leftElbowAngleDeg: 90,  leftElbowYawDeg: 20 },
    ]
    for (const overrides of leftPoses) {
      const source = { ...trunkNeutral, ...overrides }
      const lms = reconstructFromAnchor(source)
      const round = extractAnchorFromLandmarks(lms)
      const delta = Math.abs(round.leftElbowYawDeg - overrides.leftElbowYawDeg)
      expect(delta, `L swivel delta for ${JSON.stringify(overrides)}`).toBeLessThan(1)
    }
  })
})
