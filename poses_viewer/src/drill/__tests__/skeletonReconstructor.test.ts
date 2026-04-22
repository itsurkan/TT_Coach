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
    // Force zero rotation AND zero tilt: body rotation rotates the hip line,
    // and tilt-compensation shifts hips along `backward` — both offset X.
    const neutralRot = { ...NEUTRAL_POSE, bodyRotationDeg: 0, torsoTiltDeg: 0, shoulderRotationDeg: 0 }
    const out = reconstructFromAnchor(neutralRot)
    const midX = (out[LM.L_HIP].x + out[LM.R_HIP].x) / 2
    expect(midX).toBeCloseTo(neutralRot.hipMidX, 4)
  })

  it('lowest ankle lands on the ground anchor line', () => {
    // New semantics: whichever foot is lower (max y) plants on GROUND; the
    // other may float for asymmetric/lunge stances. The old ankleMid-snap
    // behaviour was the cosine-law IK that silently overrode kneeAngleDeg.
    const out = reconstructFromAnchor(NEUTRAL_POSE)
    const lowest = Math.max(out[LM.L_ANKLE].y, out[LM.R_ANKLE].y)
    expect(lowest).toBeCloseTo(GROUND_ANCHOR_Y, 4)
  })

  it('lowest foot stays grounded across torso tilt (0 vs 30 degrees)', () => {
    const flat = reconstructFromAnchor({ ...NEUTRAL_POSE, torsoTiltDeg: 0 })
    const tilted = reconstructFromAnchor({ ...NEUTRAL_POSE, torsoTiltDeg: 30 })
    const flatLow = Math.max(flat[LM.L_ANKLE].y, flat[LM.R_ANKLE].y)
    const tiltedLow = Math.max(tilted[LM.L_ANKLE].y, tilted[LM.R_ANKLE].y)
    expect(flatLow).toBeCloseTo(GROUND_ANCHOR_Y, 4)
    expect(tiltedLow).toBeCloseTo(GROUND_ANCHOR_Y, 4)
  })

  it('bending knees drops the hips (shorter vertical leg span)', () => {
    // Core invariant of the new body-translate post-pass: with feet planted,
    // a deeper knee bend must lower the hip in world-Y.
    const straight = reconstructFromAnchor({ ...NEUTRAL_POSE, leftKneeAngleDeg: 175, rightKneeAngleDeg: 175 })
    const bent     = reconstructFromAnchor({ ...NEUTRAL_POSE, leftKneeAngleDeg: 110, rightKneeAngleDeg: 110 })
    const hipYstraight = (straight[LM.L_HIP].y + straight[LM.R_HIP].y) / 2
    const hipYbent     = (bent[LM.L_HIP].y     + bent[LM.R_HIP].y)     / 2
    // Bent pose: hip closer to ground (bigger y in MediaPipe convention).
    expect(hipYbent).toBeGreaterThan(hipYstraight)
  })

  it('places shoulder mid approximately one torso length above hip', () => {
    const out = reconstructFromAnchor(NEUTRAL_POSE)
    // Use RENDERED hip Y (not anchor.hipMidY) — the ground-translate offsets
    // the whole body vertically, so the anchor value is no longer the world Y.
    const hipY = (out[LM.L_HIP].y + out[LM.R_HIP].y) / 2
    const shMidY = (out[LM.L_SHOULDER].y + out[LM.R_SHOULDER].y) / 2
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
      shoulderRotationDeg: 0,
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
      shoulderRotationDeg: 0,
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

  it('torsoTiltDeg rotates the whole torso forward (shoulders lead, hip pivot fixed)', () => {
    // Single-segment torso tilt: shoulders move forward and down. HipMid XZ
    // stays put (hip line is the pivot); shoulderMid XZ translates along forward.
    const base = { ...NEUTRAL_POSE, bodyRotationDeg: 0, torsoTiltDeg: 0, shoulderRotationDeg: 0 }
    const tilted = { ...base, torsoTiltDeg: 30 }
    const A = reconstructFromAnchor(base)
    const B = reconstructFromAnchor(tilted)
    const hipMidA_x = (A[LM.L_HIP].x + A[LM.R_HIP].x) / 2
    const hipMidB_x = (B[LM.L_HIP].x + B[LM.R_HIP].x) / 2
    expect(hipMidB_x).toBeCloseTo(hipMidA_x, 5)
    const shMidZ_A = (A[LM.L_SHOULDER].z + A[LM.R_SHOULDER].z) / 2
    const shMidZ_B = (B[LM.L_SHOULDER].z + B[LM.R_SHOULDER].z) / 2
    expect(shMidZ_B).toBeLessThan(shMidZ_A)
  })

  it('shoulderRotationDeg yaws the shoulder line without moving hips', () => {
    // Corpus rotation: shoulder line rotates around vertical independent of
    // the hip line. Hip positions must stay identical; the shoulder-across
    // direction rotates by exactly shoulderRotationDeg around Y.
    const base = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
    }
    const yawed = { ...base, shoulderRotationDeg: 30 }
    const A = reconstructFromAnchor(base)
    const B = reconstructFromAnchor(yawed)
    // Hip line unchanged.
    expect(B[LM.L_HIP].x).toBeCloseTo(A[LM.L_HIP].x, 5)
    expect(B[LM.L_HIP].z).toBeCloseTo(A[LM.L_HIP].z, 5)
    expect(B[LM.R_HIP].x).toBeCloseTo(A[LM.R_HIP].x, 5)
    expect(B[LM.R_HIP].z).toBeCloseTo(A[LM.R_HIP].z, 5)
    // Shoulder across direction rotated by shoulderRotationDeg around Y.
    const shAcrossA = { x: A[LM.L_SHOULDER].x - A[LM.R_SHOULDER].x, z: A[LM.L_SHOULDER].z - A[LM.R_SHOULDER].z }
    const shAcrossB = { x: B[LM.L_SHOULDER].x - B[LM.R_SHOULDER].x, z: B[LM.L_SHOULDER].z - B[LM.R_SHOULDER].z }
    const angA = Math.atan2(shAcrossA.z, shAcrossA.x) * 180 / Math.PI
    const angB = Math.atan2(shAcrossB.z, shAcrossB.x) * 180 / Math.PI
    let delta = angB - angA
    while (delta > 180)  delta -= 360
    while (delta < -180) delta += 360
    // rotY(+d) takes (x,z) → (x·cos+z·sin, −x·sin+z·cos); atan2(z,x) therefore
    // decreases by d. Expect the observed delta ≈ −30°.
    expect(Math.abs(delta + 30)).toBeLessThan(1)
  })

  it('foot yaw rotates the knee-bend plane (knee-over-toe)', () => {
    // With thigh straight down and knee bent, the shin's XZ offset from the
    // knee lies in the vertical plane the foot points along. Changing footYaw
    // rotates that plane by the same angle around Y.
    const base = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      leftThighForwardDeg: 0,
      leftThighAbductionDeg: 0,
      leftKneeAngleDeg: 120, // 60° bend → meaningful shin offset
      leftFootYawDeg: 0,
    }
    const yawed = { ...base, leftFootYawDeg: 30 }
    const A = reconstructFromAnchor(base)
    const B = reconstructFromAnchor(yawed)
    const shinA = { x: A[LM.L_ANKLE].x - A[LM.L_KNEE].x, z: A[LM.L_ANKLE].z - A[LM.L_KNEE].z }
    const shinB = { x: B[LM.L_ANKLE].x - B[LM.L_KNEE].x, z: B[LM.L_ANKLE].z - B[LM.L_KNEE].z }
    // Angle (about Y) of each shin's XZ component.
    const angA = Math.atan2(shinA.x, shinA.z) * 180 / Math.PI
    const angB = Math.atan2(shinB.x, shinB.z) * 180 / Math.PI
    let delta = angB - angA
    while (delta > 180)  delta -= 360
    while (delta < -180) delta += 360
    expect(Math.abs(delta - 30)).toBeLessThan(1) // rotated by footYaw delta
  })
})
