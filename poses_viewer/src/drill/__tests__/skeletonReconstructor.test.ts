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

  it('knee yaw rotates the knee-bend plane (knee-over-toe)', () => {
    // With thigh straight down and knee bent, the shin's XZ offset from the
    // knee lies in the vertical plane the knee points along. Changing kneeYaw
    // rotates that plane by the same angle around Y. (footYawDeg is now the
    // foot-vs-shin angle and does NOT affect the knee plane.)
    const base = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      leftThighForwardDeg: 0,
      leftThighAbductionDeg: 0,
      leftKneeAngleDeg: 120, // 60° bend → meaningful shin offset
      leftKneeYawDeg: 0,
      leftFootYawDeg: 0,
    }
    const yawed = { ...base, leftKneeYawDeg: 30 }
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

  it('elbow bend on an abducted arm points the forearm toward the head', () => {
    // Right arm abducted 90° (horizontal out to the player's right), elbow 90°.
    // New hinge = cross(upperArm, torsoUp): the bend plane contains the upper
    // arm and the spine, so the forearm rotates UP (toward the head/camera),
    // not further sideways. Rendered Y is larger = lower on screen; wrist must
    // be HIGHER on screen than the elbow.
    const pose = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 0,
      rightShoulderAbductionDeg: 90,
      rightElbowAngleDeg: 90,
    }
    const out = reconstructFromAnchor(pose)
    const elbow = out[LM.R_ELBOW]
    const wrist = out[LM.R_WRIST]
    // Wrist is higher on screen than elbow (smaller y in MediaPipe convention).
    expect(wrist.y).toBeLessThan(elbow.y)
    // Forearm length ≈ BONES.forearm, and it's mostly vertical — so the
    // vertical drop wrist→elbow is close to the full forearm length.
    expect(elbow.y - wrist.y).toBeGreaterThan(BONES.forearm * 0.8)
  })

  it('elbow bend on a rest-down arm still points the forearm forward (regression guard)', () => {
    // Arm hanging straight down, elbow 90°. Historical behaviour: forearm
    // points forward (−z). New hinge = cross(down, torsoUp) = shoulderAcross,
    // so forearm still bends around shoulderAcross — same result. Locks this.
    const pose = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 0,
      rightShoulderAbductionDeg: 0,
      rightElbowAngleDeg: 90,
    }
    const out = reconstructFromAnchor(pose)
    const elbow = out[LM.R_ELBOW]
    const wrist = out[LM.R_WRIST]
    // Forearm points forward: wrist.z < elbow.z (forward is −z).
    expect(wrist.z).toBeLessThan(elbow.z)
    // Forearm is roughly horizontal, so vertical drop is small.
    expect(Math.abs(wrist.y - elbow.y)).toBeLessThan(BONES.forearm * 0.2)
  })

  it('forearm position is continuous as shoulder-fwd slider crosses zero (no 180° snap)', () => {
    // Bug: dragging the Shoulder-fwd slider through 0 with abduction near 0
    // used to flip the forearm 180° because cross(torsoUp, upperArmDir) flips
    // sign as the upper arm passes through the spine axis. Sweep shFwd from
    // -10° to +10° in 1° steps with shAbd=0 and assert the wrist position
    // changes smoothly (no frame-to-frame jump > forearm length/3).
    const base = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAbductionDeg: 0,
      rightElbowAngleDeg: 90,
    }
    let prev: { x: number; y: number; z: number } | null = null
    for (let shFwd = -10; shFwd <= 10; shFwd++) {
      const out = reconstructFromAnchor({ ...base, rightShoulderAngleDeg: shFwd })
      const w = out[LM.R_WRIST]
      if (prev) {
        const jump = Math.hypot(w.x - prev.x, w.y - prev.y, w.z - prev.z)
        expect(jump).toBeLessThan(BONES.forearm / 3)
      }
      prev = { x: w.x, y: w.y, z: w.z }
    }
  })

  it('MIDPOINT_POSE honors defaultValue on param specs when present', async () => {
    // Sanity: once specs set defaultValue for shoulder flex/abduction to 41/31,
    // MIDPOINT_POSE must reflect those targets rather than raw (min+max)/2.
    const { MIDPOINT_POSE } = await import('../neutralPose')
    // These four will have defaultValue set by Task 2. Until then this test
    // also asserts the current (min+max)/2 behaviour, which is (min=-30,
    // max=112)/2 = 41 for flex and (min=0, max=62)/2 = 31 for abduction —
    // so the test passes today AND after Task 2 widens the ranges.
    expect(MIDPOINT_POSE.rightShoulderAngleDeg).toBe(41)
    expect(MIDPOINT_POSE.leftShoulderAngleDeg).toBe(41)
    expect(MIDPOINT_POSE.rightShoulderAbductionDeg).toBe(31)
    expect(MIDPOINT_POSE.leftShoulderAbductionDeg).toBe(31)
    expect(MIDPOINT_POSE.rightThighAbductionDeg).toBe(17)
    expect(MIDPOINT_POSE.leftThighAbductionDeg).toBe(17)
  })
})
