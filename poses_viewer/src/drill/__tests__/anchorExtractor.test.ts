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

  it('elbowYaw round-trip: extractor recovers yaw within 3° across diverse arm poses', () => {
    // Pure DOF test for the new 3rd-shoulder-DOF contract: for each of 5
    // diverse arm configurations, reconstruct → extract and check that the
    // extracted `*ElbowYawDeg` matches the source within 3° (signed).
    //
    // Other angle fields are NOT asserted here — the decomposition helpers
    // for shFwd/shAbd apply a 50% Z-dampening for MediaPipe noise tolerance,
    // which visibly distorts those two angles on clean FK outputs. Elbow
    // yaw's extraction is Z-dampening-independent (it's a pure signed angle
    // around upperArmDir), so it round-trips cleanly and IS the load-bearing
    // contract this plan's Tasks 5-7 promised.
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
    const posesToTest = [
      { rightShoulderAngleDeg: 45,  rightShoulderAbductionDeg: 25,  rightElbowAngleDeg: 95,  rightElbowYawDeg: 30 },
      { rightShoulderAngleDeg: 90,  rightShoulderAbductionDeg: 60,  rightElbowAngleDeg: 120, rightElbowYawDeg: -40 },
      { rightShoulderAngleDeg: -20, rightShoulderAbductionDeg: 10,  rightElbowAngleDeg: 150, rightElbowYawDeg: 60 },
      { rightShoulderAngleDeg: 130, rightShoulderAbductionDeg: 40,  rightElbowAngleDeg: 80,  rightElbowYawDeg: -60 },
      { rightShoulderAngleDeg: 0,   rightShoulderAbductionDeg: 80,  rightElbowAngleDeg: 90,  rightElbowYawDeg: 20 },
    ]
    for (const overrides of posesToTest) {
      const source = { ...trunkNeutral, ...overrides }
      const lms = reconstructFromAnchor(source)
      const round = extractAnchorFromLandmarks(lms)
      const delta = Math.abs(round.rightElbowYawDeg - overrides.rightElbowYawDeg)
      expect(delta, `elbowYaw delta for pose ${JSON.stringify(overrides)}`).toBeLessThan(3)
    }
  })
})
