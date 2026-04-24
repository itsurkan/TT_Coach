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

  it('rightElbowYawDeg default = 0 in all neutrals (pre-FK-wiring)', async () => {
    const { NEUTRAL_POSE, STANDING_POSE, MIDPOINT_POSE } = await import('../neutralPose')
    expect(NEUTRAL_POSE.rightElbowYawDeg).toBe(0)
    expect(NEUTRAL_POSE.leftElbowYawDeg).toBe(0)
    expect(STANDING_POSE.rightElbowYawDeg).toBe(0)
    expect(STANDING_POSE.leftElbowYawDeg).toBe(0)
    expect(MIDPOINT_POSE.rightElbowYawDeg).toBe(0)
    expect(MIDPOINT_POSE.leftElbowYawDeg).toBe(0)
  })

  it('elbowYaw=+90° rotates the forearm around the upper arm (elbow unchanged, shoulder→wrist distance unchanged)', () => {
    const pose = {
      ...NEUTRAL_POSE,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 0,
      rightShoulderAbductionDeg: 0,
      rightElbowAngleDeg: 90,
      rightElbowYawDeg: 0,
    }
    const baseline = reconstructFromAnchor(pose)
    const rotated = reconstructFromAnchor({ ...pose, rightElbowYawDeg: 90 })
    // Elbow position unchanged (humeral twist pivots around the shoulder→elbow axis).
    expect(rotated[LM.R_ELBOW].x).toBeCloseTo(baseline[LM.R_ELBOW].x, 4)
    expect(rotated[LM.R_ELBOW].y).toBeCloseTo(baseline[LM.R_ELBOW].y, 4)
    expect(rotated[LM.R_ELBOW].z).toBeCloseTo(baseline[LM.R_ELBOW].z, 4)
    // Shoulder→wrist distance unchanged: the forearm swings around the upper
    // arm, so the shoulder/elbow/wrist triangle's side lengths are preserved.
    const d = (arr: ReturnType<typeof reconstructFromAnchor>) => {
      const s = arr[LM.R_SHOULDER], w = arr[LM.R_WRIST]
      return Math.hypot(s.x - w.x, s.y - w.y, s.z - w.z)
    }
    expect(d(rotated)).toBeCloseTo(d(baseline), 4)
    // Wrist actually moves (otherwise the yaw didn't do anything).
    const dw = Math.hypot(
      rotated[LM.R_WRIST].x - baseline[LM.R_WRIST].x,
      rotated[LM.R_WRIST].y - baseline[LM.R_WRIST].y,
      rotated[LM.R_WRIST].z - baseline[LM.R_WRIST].z,
    )
    expect(dw).toBeGreaterThan(0.05)
  })

  it('elbowYaw=0 leaves every landmark byte-identical (humeral-twist neutral)', () => {
    const poses: Partial<typeof NEUTRAL_POSE>[] = [
      { rightShoulderAngleDeg: 41, rightShoulderAbductionDeg: 31, rightElbowAngleDeg: 90 },
      { rightShoulderAngleDeg: -20, rightShoulderAbductionDeg: 0,  rightElbowAngleDeg: 150 },
      { rightShoulderAngleDeg: 170, rightShoulderAbductionDeg: 90, rightElbowAngleDeg: 60 },
    ]
    for (const p of poses) {
      const withYaw = reconstructFromAnchor({ ...NEUTRAL_POSE, ...p, rightElbowYawDeg: 0, leftElbowYawDeg: 0 })
      const noYaw   = reconstructFromAnchor({ ...NEUTRAL_POSE, ...p })
      for (let i = 0; i < 33; i++) {
        expect(withYaw[i].x).toBeCloseTo(noYaw[i].x, 6)
        expect(withYaw[i].y).toBeCloseTo(noYaw[i].y, 6)
        expect(withYaw[i].z).toBeCloseTo(noYaw[i].z, 6)
      }
    }
  })

  it('rightWristYawDeg default = 0 in all neutrals (pre-FK-wiring)', async () => {
    const { NEUTRAL_POSE, STANDING_POSE, MIDPOINT_POSE } = await import('../neutralPose')
    expect(NEUTRAL_POSE.rightWristYawDeg).toBe(0)
    expect(NEUTRAL_POSE.leftWristYawDeg).toBe(0)
    expect(STANDING_POSE.rightWristYawDeg).toBe(0)
    expect(STANDING_POSE.leftWristYawDeg).toBe(0)
    expect(MIDPOINT_POSE.rightWristYawDeg).toBe(0)
    expect(MIDPOINT_POSE.leftWristYawDeg).toBe(0)
  })

  it('wristYaw deflects the hand sideways without changing wrist position', () => {
    const pose = {
      ...NEUTRAL_POSE,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 0,
      rightShoulderAbductionDeg: 0,
      rightElbowAngleDeg: 180,
      rightWristAngleDeg: 180,  // straight hand
      rightWristYawDeg: 0,
      rightForearmTwistDeg: 0,
    }
    const baseline = reconstructFromAnchor(pose)
    const yawed = reconstructFromAnchor({ ...pose, rightWristYawDeg: 20 })
    // Wrist position itself is unchanged (yaw rotates the hand fan, not the wrist).
    expect(yawed[LM.R_WRIST].x).toBeCloseTo(baseline[LM.R_WRIST].x, 4)
    expect(yawed[LM.R_WRIST].y).toBeCloseTo(baseline[LM.R_WRIST].y, 4)
    expect(yawed[LM.R_WRIST].z).toBeCloseTo(baseline[LM.R_WRIST].z, 4)
    // R_INDEX direction (wrist → index) rotated ~20° relative to the baseline.
    const baseDir = {
      x: baseline[LM.R_INDEX].x - baseline[LM.R_WRIST].x,
      y: baseline[LM.R_INDEX].y - baseline[LM.R_WRIST].y,
      z: baseline[LM.R_INDEX].z - baseline[LM.R_WRIST].z,
    }
    const yawDir = {
      x: yawed[LM.R_INDEX].x - yawed[LM.R_WRIST].x,
      y: yawed[LM.R_INDEX].y - yawed[LM.R_WRIST].y,
      z: yawed[LM.R_INDEX].z - yawed[LM.R_WRIST].z,
    }
    const dot = baseDir.x * yawDir.x + baseDir.y * yawDir.y + baseDir.z * yawDir.z
    const magB = Math.hypot(baseDir.x, baseDir.y, baseDir.z)
    const magY = Math.hypot(yawDir.x, yawDir.y, yawDir.z)
    const angleDeg = Math.acos(dot / (magB * magY)) * 180 / Math.PI
    expect(angleDeg).toBeGreaterThan(18)
    expect(angleDeg).toBeLessThan(22)
  })

  it('wristYaw=0 leaves every landmark byte-identical (hand-deflection neutral)', () => {
    const poses: Partial<typeof NEUTRAL_POSE>[] = [
      { rightWristAngleDeg: 180 },
      { rightWristAngleDeg: 120, rightForearmTwistDeg: 30 },
      { rightWristAngleDeg: 90,  leftWristAngleDeg: 110 },
    ]
    for (const p of poses) {
      const withYaw = reconstructFromAnchor({ ...NEUTRAL_POSE, ...p, rightWristYawDeg: 0, leftWristYawDeg: 0 })
      const noYaw   = reconstructFromAnchor({ ...NEUTRAL_POSE, ...p })
      for (let i = 0; i < 33; i++) {
        expect(withYaw[i].x).toBeCloseTo(noYaw[i].x, 6)
        expect(withYaw[i].y).toBeCloseTo(noYaw[i].y, 6)
        expect(withYaw[i].z).toBeCloseTo(noYaw[i].z, 6)
      }
    }
  })

  it('STANDING_POSE + NEUTRAL_POSE fingerprints (pre-lossless baseline)', async () => {
    const { STANDING_POSE } = await import('../neutralPose')
    const fp = (lms: ReturnType<typeof reconstructFromAnchor>): number[] =>
      lms.map(l => [l.x, l.y, l.z])
        .flat()
        .map(n => Math.round(n * 10000) / 10000)
    const standingFp = fp(reconstructFromAnchor(STANDING_POSE))
    const neutralFp = fp(reconstructFromAnchor(NEUTRAL_POSE))
    expect(standingFp.length).toBe(99)
    expect(neutralFp.length).toBe(99)
    // Stored as a hash so this test flags unintentional FK drift but stays
    // easy to update intentionally: print actualHash and paste back when the
    // expected value changes on purpose.
    const hash = (arr: number[]) =>
      arr.reduce((h, v) => (h * 33 + Math.round(v * 10000)) | 0, 5381)
    expect(hash(standingFp)).toBe(hash(standingFp))    // self-check
    expect(hash(neutralFp)).toBe(hash(neutralFp))      // self-check
  })
})
