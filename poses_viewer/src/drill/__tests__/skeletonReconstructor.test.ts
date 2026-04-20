import { describe, it, expect } from 'vitest'
import { reconstructFromAnchor, GROUND_ANCHOR_Y } from '../skeletonReconstructor'
import { NEUTRAL_POSE } from '../neutralPose'
import { LM, BONES } from '../SkeletonModel'

describe('reconstructFromAnchor', () => {
  it('produces 33 landmarks from the neutral pose', () => {
    const out = reconstructFromAnchor(NEUTRAL_POSE)
    expect(out).toHaveLength(33)
    for (let i = 0; i < 33; i++) {
      expect(out[i]).toBeDefined()
      expect(Number.isFinite(out[i].x)).toBe(true)
      expect(Number.isFinite(out[i].y)).toBe(true)
    }
  })

  it('hip mid X matches anchor (no horizontal shift at neutral rotation)', () => {
    const out = reconstructFromAnchor(NEUTRAL_POSE)
    const midX = (out[LM.L_HIP].x + out[LM.R_HIP].x) / 2
    expect(midX).toBeCloseTo(NEUTRAL_POSE.hipMidX, 4)
  })

  it('ankle midpoint is snapped to the ground anchor line', () => {
    const out = reconstructFromAnchor(NEUTRAL_POSE)
    const ankleMidY = (out[LM.L_ANKLE].y + out[LM.R_ANKLE].y) / 2
    expect(ankleMidY).toBeCloseTo(GROUND_ANCHOR_Y, 4)
  })

  it('feet stay on the ground across torso tilt (0 vs 30 degrees)', () => {
    const flat = reconstructFromAnchor({ ...NEUTRAL_POSE, torsoTiltDeg: 0 })
    const tilted = reconstructFromAnchor({ ...NEUTRAL_POSE, torsoTiltDeg: 30 })
    const flatAnkleY = (flat[LM.L_ANKLE].y + flat[LM.R_ANKLE].y) / 2
    const tiltedAnkleY = (tilted[LM.L_ANKLE].y + tilted[LM.R_ANKLE].y) / 2
    expect(flatAnkleY).toBeCloseTo(tiltedAnkleY, 4)
  })

  it('places shoulder mid approximately one torso length above hip', () => {
    const out = reconstructFromAnchor(NEUTRAL_POSE)
    const hipY = NEUTRAL_POSE.hipMidY
    const lSh = out[LM.L_SHOULDER]
    const rSh = out[LM.R_SHOULDER]
    const shMidY = (lSh.y + rSh.y) / 2
    // torso goes "up" which is negative y direction, so shoulder_mid_y < hip_y
    expect(shMidY).toBeLessThan(hipY)
    // magnitude approx torso (allowing slight tilt effect)
    expect(Math.abs(hipY - shMidY)).toBeGreaterThan(BONES.torso * 0.9)
    expect(Math.abs(hipY - shMidY)).toBeLessThan(BONES.torso * 1.05)
  })

  it('straight elbow (180°) places wrist roughly upperArm+forearm below shoulder', () => {
    // Tilt-free baseline so the arm hangs purely vertical.
    const straightArm = {
      ...NEUTRAL_POSE,
      torsoTiltDeg: 0,
      rightShoulderAngleDeg: 0,
      rightShoulderAbductionDeg: 0,
      rightElbowAngleDeg: 180,
    }
    const out = reconstructFromAnchor(straightArm)
    const rSh = out[LM.R_SHOULDER]
    const rWr = out[LM.R_WRIST]
    const dropY = rWr.y - rSh.y
    expect(dropY).toBeCloseTo(BONES.upperArm + BONES.forearm, 3)
  })

  it('bent elbow (90°) shortens the vertical drop from shoulder to wrist', () => {
    const bent = {
      ...NEUTRAL_POSE,
      torsoTiltDeg: 0,
      rightShoulderAngleDeg: 0,
      rightShoulderAbductionDeg: 0,
      rightElbowAngleDeg: 90,
    }
    const out = reconstructFromAnchor(bent)
    const rSh = out[LM.R_SHOULDER]
    const rWr = out[LM.R_WRIST]
    const dropY = rWr.y - rSh.y
    // 90° bend: upper arm down, forearm horizontal → wrist is upperArm below + forearm sideways
    expect(dropY).toBeCloseTo(BONES.upperArm, 2)
  })

  it('feet stay near the same Y across two anchors when only arm angles differ', () => {
    const a = { ...NEUTRAL_POSE, rightElbowAngleDeg: 170 }
    const b = { ...NEUTRAL_POSE, rightElbowAngleDeg: 90 }
    const poseA = reconstructFromAnchor(a)
    const poseB = reconstructFromAnchor(b)
    const ankleYDiff = Math.abs(poseA[LM.R_ANKLE].y - poseB[LM.R_ANKLE].y)
    expect(ankleYDiff).toBeLessThan(1e-5)
  })
})
