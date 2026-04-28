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

  it('elbowYaw default = 0 in NEUTRAL/STANDING neutrals (swivel=0 invariance)', async () => {
    // MIDPOINT_POSE intentionally uses the slider `defaultValue` (40° on the
    // right), so it's not constrained here. NEUTRAL/STANDING must stay at 0 so
    // fingerprint drift guards below keep their meaning.
    const { NEUTRAL_POSE, STANDING_POSE } = await import('../neutralPose')
    expect(NEUTRAL_POSE.rightElbowYawDeg).toBe(0)
    expect(NEUTRAL_POSE.leftElbowYawDeg).toBe(0)
    expect(STANDING_POSE.rightElbowYawDeg).toBe(0)
    expect(STANDING_POSE.leftElbowYawDeg).toBe(0)
  })

  it('elbowSwivel orbits elbow around shoulder→wrist axis with wrist pinned (right arm)', () => {
    const baseAnchor = {
      ...NEUTRAL_POSE,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 45,
      rightShoulderAbductionDeg: 20,
      rightElbowAngleDeg: 90,
    }
    const baseline = reconstructFromAnchor({ ...baseAnchor, rightElbowYawDeg: 0 })
    const S0 = baseline[LM.R_SHOULDER]
    const W0 = baseline[LM.R_WRIST]
    const E0 = baseline[LM.R_ELBOW]
    const L_upper = 0.22, L_forearm = 0.20

    for (const swivel of [-60, -30, 30, 60, 90]) {
      const out = reconstructFromAnchor({ ...baseAnchor, rightElbowYawDeg: swivel })
      const S = out[LM.R_SHOULDER], E = out[LM.R_ELBOW], W = out[LM.R_WRIST]
      // Shoulder pinned (FK doesn't touch it)
      expect(Math.hypot(S.x - S0.x, S.y - S0.y, S.z - S0.z)).toBeLessThan(1e-6)
      // Wrist pinned — THE new contract
      expect(Math.hypot(W.x - W0.x, W.y - W0.y, W.z - W0.z)).toBeLessThan(1e-4)
      // Bone lengths preserved (elbow stays on the swivel circle)
      expect(Math.abs(Math.hypot(S.x - E.x, S.y - E.y, S.z - E.z) - L_upper)).toBeLessThan(1e-4)
      expect(Math.abs(Math.hypot(E.x - W.x, E.y - W.y, E.z - W.z) - L_forearm)).toBeLessThan(1e-4)
      // Elbow actually moves (otherwise the swivel did nothing)
      expect(Math.hypot(E.x - E0.x, E.y - E0.y, E.z - E0.z)).toBeGreaterThan(0.01)
    }
  })

  it('elbowSwivel is symmetric for left arm (wrist pinned, elbow orbits)', () => {
    const baseAnchor = {
      ...NEUTRAL_POSE,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      leftShoulderAngleDeg: 45,
      leftShoulderAbductionDeg: 20,
      leftElbowAngleDeg: 90,
    }
    const baseline = reconstructFromAnchor({ ...baseAnchor, leftElbowYawDeg: 0 })
    const W0 = baseline[LM.L_WRIST]
    const E0 = baseline[LM.L_ELBOW]
    const L_upper = 0.22, L_forearm = 0.20

    for (const swivel of [-60, 60, 90]) {
      const out = reconstructFromAnchor({ ...baseAnchor, leftElbowYawDeg: swivel })
      const S = out[LM.L_SHOULDER], E = out[LM.L_ELBOW], W = out[LM.L_WRIST]
      expect(Math.hypot(W.x - W0.x, W.y - W0.y, W.z - W0.z)).toBeLessThan(1e-4)
      expect(Math.abs(Math.hypot(S.x - E.x, S.y - E.y, S.z - E.z) - L_upper)).toBeLessThan(1e-4)
      expect(Math.abs(Math.hypot(E.x - W.x, E.y - W.y, E.z - W.z) - L_forearm)).toBeLessThan(1e-4)
      expect(Math.hypot(E.x - E0.x, E.y - E0.y, E.z - E0.z)).toBeGreaterThan(0.01)
    }
  })

  it('kneeSwivel orbits knee around hip→ankle axis with ankle pinned (both legs)', () => {
    // Knee-bent stance so the hip→ankle triangle is non-degenerate (swivel
    // circle has non-zero radius). Symmetric test covers both legs at once.
    const baseAnchor = {
      ...NEUTRAL_POSE,
      figureYawDeg: 0,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      leftThighForwardDeg: 30,
      rightThighForwardDeg: 30,
      leftThighAbductionDeg: 10,
      rightThighAbductionDeg: 10,
      leftKneeAngleDeg: 120,
      rightKneeAngleDeg: 120,
      leftKneeYawDeg: 0,
      rightKneeYawDeg: 0,
    }
    const baseline = reconstructFromAnchor({ ...baseAnchor, leftKneeSwivelDeg: 0, rightKneeSwivelDeg: 0 })
    const rH0 = baseline[LM.R_HIP], rA0 = baseline[LM.R_ANKLE], rK0 = baseline[LM.R_KNEE]
    const lH0 = baseline[LM.L_HIP], lA0 = baseline[LM.L_ANKLE], lK0 = baseline[LM.L_KNEE]
    const thighLen = BONES.thigh, shinLen = BONES.shin

    for (const swivel of [-60, -20, 20, 60, 90]) {
      const out = reconstructFromAnchor({ ...baseAnchor, leftKneeSwivelDeg: swivel, rightKneeSwivelDeg: swivel })
      for (const [H0, A0, K0, HI, AI, KI] of [
        [rH0, rA0, rK0, LM.R_HIP, LM.R_ANKLE, LM.R_KNEE] as const,
        [lH0, lA0, lK0, LM.L_HIP, LM.L_ANKLE, LM.L_KNEE] as const,
      ]) {
        const H = out[HI], A = out[AI], K = out[KI]
        // Hip pinned (trivially — hips don't depend on knee FK).
        expect(Math.hypot(H.x - H0.x, H.y - H0.y, H.z - H0.z)).toBeLessThan(1e-6)
        // Ankle pinned (the new contract).
        expect(Math.hypot(A.x - A0.x, A.y - A0.y, A.z - A0.z)).toBeLessThan(1e-4)
        // Bone lengths preserved (knee stays on the swivel circle).
        expect(Math.abs(Math.hypot(H.x - K.x, H.y - K.y, H.z - K.z) - thighLen)).toBeLessThan(1e-4)
        expect(Math.abs(Math.hypot(K.x - A.x, K.y - A.y, K.z - A.z) - shinLen)).toBeLessThan(1e-4)
        // Knee actually moves (otherwise the swivel did nothing).
        expect(Math.hypot(K.x - K0.x, K.y - K0.y, K.z - K0.z)).toBeGreaterThan(0.01)
      }
    }
  })

  it('kneeSwivel=0 leaves every landmark byte-identical (leg-orbit neutral)', () => {
    const poses: Partial<typeof NEUTRAL_POSE>[] = [
      { leftKneeAngleDeg: 130, rightKneeAngleDeg: 130, leftThighForwardDeg: 28, rightThighForwardDeg: 28 },
      { leftKneeAngleDeg: 80,  rightKneeAngleDeg: 80,  leftThighForwardDeg: 60, rightThighForwardDeg: 60 },
      { leftKneeAngleDeg: 179, rightKneeAngleDeg: 179, leftThighForwardDeg: 5,  rightThighForwardDeg: 5  },
    ]
    for (const p of poses) {
      const withSwivel = reconstructFromAnchor({ ...NEUTRAL_POSE, ...p, leftKneeSwivelDeg: 0, rightKneeSwivelDeg: 0 })
      const noSwivel   = reconstructFromAnchor({ ...NEUTRAL_POSE, ...p })
      for (let i = 0; i < 33; i++) {
        expect(withSwivel[i].x).toBeCloseTo(noSwivel[i].x, 6)
        expect(withSwivel[i].y).toBeCloseTo(noSwivel[i].y, 6)
        expect(withSwivel[i].z).toBeCloseTo(noSwivel[i].z, 6)
      }
    }
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

  // ─ Shoulder decomposition (plane-projection FK) ─────────────────────────
  // The shoulder FK treats rightShoulderAngleDeg (flex) and
  // rightShoulderAbductionDeg (abd) as independent anatomical plane angles:
  //   upperArmDir = dDown·torsoDown + dForward·shoulderForward + dAcross·shoulderAcross
  //   where dDown=cos(flex)cos(abd), dForward=sin(flex)cos(abd), dAcross=sin(abd)
  // These tests pin that behaviour (single-plane sweeps, independence of the
  // two sliders, round-trip through the extractor).

  const flatPose = {
    ...NEUTRAL_POSE,
    torsoTiltDeg: 0,
    torsoSideBendDeg: 0,
    pelvicRollDeg: 0,
    figureYawDeg: 0,
    bodyRotationDeg: 0,
    shoulderRotationDeg: 0,
    rightElbowAngleDeg: 180,
    rightElbowYawDeg: 0,
    rightWristAngleDeg: 180,
    rightWristYawDeg: 0,
    rightForearmTwistDeg: 0,
  }

  it('shoulder: pure sagittal sweep — elbow stays on the shoulder plane (no lateral drift)', () => {
    // flex varies, abd=0. The elbow's x must match the shoulder's x at every step
    // (sagittal plane has zero lateral component). The (y, z) offset must trace
    // a circle of radius upperArm around the shoulder.
    for (const flex of [0, 45, 90, 135, 180]) {
      const out = reconstructFromAnchor({
        ...flatPose,
        rightShoulderAngleDeg: flex,
        rightShoulderAbductionDeg: 0,
      })
      const sh = out[LM.R_SHOULDER]
      const el = out[LM.R_ELBOW]
      expect(el.x).toBeCloseTo(sh.x, 4)
      const dy = el.y - sh.y
      const dz = el.z - sh.z
      expect(Math.hypot(dy, dz)).toBeCloseTo(BONES.upperArm, 4)
    }
  })

  it('shoulder: pure frontal sweep — elbow stays on the shoulder plane (no forward drift)', () => {
    // abd varies, flex=0. The elbow's z must match the shoulder's z (frontal
    // plane has zero forward component). The (x, y) offset must trace the
    // upperArm circle.
    for (const abd of [0, 45, 90, 135, 180]) {
      const out = reconstructFromAnchor({
        ...flatPose,
        rightShoulderAngleDeg: 0,
        rightShoulderAbductionDeg: abd,
      })
      const sh = out[LM.R_SHOULDER]
      const el = out[LM.R_ELBOW]
      expect(el.z).toBeCloseTo(sh.z, 4)
      const dx = el.x - sh.x
      const dy = el.y - sh.y
      expect(Math.hypot(dx, dy)).toBeCloseTo(BONES.upperArm, 4)
    }
  })

  it('shoulder: independence — changing flex with abd fixed keeps the across-component constant', () => {
    // shoulderAcross at figureYaw=0, shoulderRotation=0 is (+1, 0, 0). The
    // across projection of (elbow − shoulder) should stay at sin(abd)*upperArm
    // for every value of flex. This is what "meridian, not curve" means.
    for (const abd of [-20, 30, 60, 90]) {
      const expectedAcross = Math.sin(abd * Math.PI / 180) * BONES.upperArm
      // Right-side sign: FK uses abSign=-1, so across projection onto +x is -sin(abd)
      for (const flex of [0, 30, 60, 90, 120, 150, 180]) {
        const out = reconstructFromAnchor({
          ...flatPose,
          rightShoulderAngleDeg: flex,
          rightShoulderAbductionDeg: abd,
        })
        const acrossComp = out[LM.R_ELBOW].x - out[LM.R_SHOULDER].x
        expect(acrossComp).toBeCloseTo(-expectedAcross, 3)
      }
    }
  })

  it('shoulder: round-trip through extractor — pure-axis inputs round-trip cleanly', async () => {
    // The extractor dampens MediaPipe z by 50% (noise filter for real captures),
    // so a synthetic combined (flex, abd) pose does NOT round-trip exactly —
    // z-damping distorts whichever angle ends up along the camera's z axis.
    // We therefore test the clean cases: pure-flex at a yaw where flex lives
    // in x (no z damping), and pure-abd at the default yaw where abd lives in
    // x. Both should round-trip within 1°.
    const { extractAnchorFromLandmarks } = await import('../anchorExtractor')

    // Pure abduction at figureYaw=0: shoulderAcross=(+1,0,0) — x only.
    // Range restricted to (−90°, 90°) because dAcross=sin(abd) is ambiguous
    // past ±90° (the FK collapses abd=100° and abd=80° onto the same point).
    // The UI slider allows up to 120°, but that region is only uniquely
    // resolvable with a 3rd DOF (elbow swivel) — not in scope here.
    for (const abd of [-30, -10, 0, 15, 40, 70, 85]) {
      const pose = { ...flatPose, rightShoulderAngleDeg: 0, rightShoulderAbductionDeg: abd }
      const lms = reconstructFromAnchor(pose)
      const extracted = extractAnchorFromLandmarks(lms)
      expect(extracted.rightShoulderAbductionDeg).toBeCloseTo(abd, 0) // tolerance 1°
      expect(extracted.rightShoulderAngleDeg).toBeCloseTo(0, 0)
    }

    // Pure flexion at figureYaw=90°: shoulderForward becomes (-1,0,0) — x only,
    // so flex lives in x and isn't z-damped either.
    for (const flex of [-20, 0, 30, 75, 120, 170]) {
      const pose = {
        ...flatPose,
        figureYawDeg: 90,
        rightShoulderAngleDeg: flex,
        rightShoulderAbductionDeg: 0,
      }
      const lms = reconstructFromAnchor(pose)
      const extracted = extractAnchorFromLandmarks(lms)
      expect(extracted.rightShoulderAngleDeg).toBeCloseTo(flex, 0)
      expect(extracted.rightShoulderAbductionDeg).toBeCloseTo(0, 0)
    }
  })

  it('STANDING_POSE + NEUTRAL_POSE fingerprints (FK drift guard)', async () => {
    const { STANDING_POSE } = await import('../neutralPose')
    const fp = (lms: ReturnType<typeof reconstructFromAnchor>): number[] =>
      lms.map(l => [l.x, l.y, l.z])
        .flat()
        .map(n => Math.round(n * 10000) / 10000)
    const standingFp = fp(reconstructFromAnchor(STANDING_POSE))
    const neutralFp = fp(reconstructFromAnchor(NEUTRAL_POSE))
    expect(standingFp.length).toBe(99)  // 33 landmarks × (x, y, z)
    expect(neutralFp.length).toBe(99)
    // djb2 hash over all 99 coordinates rounded to 4 decimals. If FK output
    // drifts for either pose, one of these expectations fails — print the
    // actual hash from the test failure, verify the drift is intentional,
    // and paste the new constant back here.
    const hash = (arr: number[]) =>
      arr.reduce((h, v) => (h * 33 + Math.round(v * 10000)) | 0, 5381)
    expect(hash(standingFp)).toBe(1524744803)
    // Bumped 2026-04-24 (was 664668715): shoulder FK switched from
    // sequential rotation to plane-projection (sagittal/frontal decomposition),
    // which changes upperArmDir for any pose with simultaneously nonzero
    // shoulder flex + abduction. See
    // docs/superpowers/specs/2026-04-24-shoulder-dof-decoupling-design.md.
    expect(hash(neutralFp)).toBe(653999345)
  })
})
