/**
 * Forward-kinematics reconstructor: PoseAnchor + canonical skeleton → 33 Landmark.
 *
 * Coordinate convention (matches PoseCanvas):
 *   x — right (normalized [0, 1])
 *   y — down  (normalized [0, 1])
 *   z — away from camera (normalized, roughly [-0.5, 0.5])
 *
 * Body "up" direction is −y. Body rotation is yaw around the y-axis: positive
 * = trunk turns toward +z (player's right, camera's left).
 *
 * Output is compatible with MediaPipe BlazePose's 33-landmark layout so the
 * existing POSE_CONNECTIONS can draw it unchanged.
 */

import { BONES, LM, LANDMARK_COUNT } from './SkeletonModel'
import type { PoseAnchor } from './PoseAnchor'
import type { Landmark } from '../types'

/** Per-bone length overrides. Missing fields fall back to canonical [BONES]. */
export interface BoneLengthsOverride {
  torso?: number
  shoulderWidth?: number
  upperArm?: number
  forearm?: number
  hand?: number
  hipWidth?: number
  thigh?: number
  shin?: number
  footForward?: number
  headToShoulder?: number
}

type V3 = [number, number, number]

const deg = (d: number) => (d * Math.PI) / 180

function add(a: V3, b: V3): V3 { return [a[0]+b[0], a[1]+b[1], a[2]+b[2]] }
function scale(v: V3, s: number): V3 { return [v[0]*s, v[1]*s, v[2]*s] }

function normalize(v: V3): V3 {
  const m = Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2])
  if (m < 1e-9) return [0, 0, 0]
  return [v[0]/m, v[1]/m, v[2]/m]
}

/** Rotate v around the y-axis (yaw). +deg rotates toward +z. */
function rotY(v: V3, d: number): V3 {
  const c = Math.cos(deg(d)), s = Math.sin(deg(d))
  return [c*v[0] + s*v[2], v[1], -s*v[0] + c*v[2]]
}

/** Rotate vector v around a (unit-normalizable) axis by d degrees — Rodrigues. */
function rotAroundAxis(v: V3, axis: V3, d: number): V3 {
  const k = normalize(axis)
  const c = Math.cos(deg(d))
  const s = Math.sin(deg(d))
  const dot = k[0]*v[0] + k[1]*v[1] + k[2]*v[2]
  const cross: V3 = [
    k[1]*v[2] - k[2]*v[1],
    k[2]*v[0] - k[0]*v[2],
    k[0]*v[1] - k[1]*v[0],
  ]
  return [
    v[0]*c + cross[0]*s + k[0]*dot*(1-c),
    v[1]*c + cross[1]*s + k[1]*dot*(1-c),
    v[2]*c + cross[2]*s + k[2]*dot*(1-c),
  ]
}

function mkLm(i: number, p: V3, vis = 1): Landmark {
  return { index: i, x: p[0], y: p[1], z: p[2], visibility: vis, presence: vis }
}

/**
 * Expected ankle midpoint Y in anchor coordinates. After FK, the whole body
 * is translated vertically so the ankle midpoint lands on this line — feet
 * stay on the ground regardless of torso tilt or knee bend.
 */
export const GROUND_ANCHOR_Y = 0.92

/**
 * Auto-compensation constants. Kept mild so extracted anchors (which already
 * contain correct knee/hip values from the source pose) render faithfully.
 * Previous over-aggressive values (1.2 / 1.5) were double-counting posture
 * that's already baked into the anchor.
 *
 * For pure manual editing the slight nudge from torso tilt still produces a
 * visible "squat" feel, but it no longer dominates the extracted pose.
 */
const TILT_TO_KNEE_BEND = 0.3
const EFFECTIVE_KNEE_MIN_DEG = 60
const TILT_TO_HIP_BACK = 0.3

export function reconstructFromAnchor(
  anchor: PoseAnchor,
  bonesOverride?: BoneLengthsOverride,
  options?: { skipFootIK?: boolean },
): Landmark[] {
  const out: Landmark[] = new Array(LANDMARK_COUNT)
  // Resolve bone lengths: override → canonical.
  const B = {
    torso:           bonesOverride?.torso           ?? BONES.torso,
    shoulderWidth:   bonesOverride?.shoulderWidth   ?? BONES.shoulderWidth,
    upperArm:        bonesOverride?.upperArm        ?? BONES.upperArm,
    forearm:         bonesOverride?.forearm         ?? BONES.forearm,
    hand:            bonesOverride?.hand            ?? BONES.hand,
    hipWidth:        bonesOverride?.hipWidth        ?? BONES.hipWidth,
    thigh:           bonesOverride?.thigh           ?? BONES.thigh,
    shin:            bonesOverride?.shin            ?? BONES.shin,
    footForward:     bonesOverride?.footForward     ?? BONES.footForward,
    headToShoulder:  bonesOverride?.headToShoulder  ?? BONES.headToShoulder,
  }

  // Body axes ────────────────────────────────────────────────────────────────
  // Forward = where the trunk faces; positive bodyRotation yaws toward +z.
  const forward: V3 = rotY([0, 0, -1], anchor.bodyRotationDeg)
  // Across = perpendicular, horizontal. Points from right hip to left hip.
  const across: V3 = rotY([1, 0, 0], anchor.bodyRotationDeg)
  // Torso up — use imported direction if available, else compute from tilt.
  const torsoUp: V3 = anchor.dirOverrides?.torsoUp
    ? normalize(anchor.dirOverrides.torsoUp as V3)
    : normalize(rotAroundAxis([0, -1, 0], across, anchor.torsoTiltDeg))
  const torsoDown: V3 = scale(torsoUp, -1)

  // Automatic tilt compensation ────────────────────────────────────────────
  // Forward tilt (|torsoTilt| > 0) should: (a) shift hips backward,
  // (b) bend knees more, (c) keep feet on the ground. Below handles (a) and
  // (b); (c) is enforced by a post-pass that snaps ankles to GROUND_ANCHOR_Y.
  const tiltRad = deg(anchor.torsoTiltDeg)
  const hipBackShift = Math.sin(tiltRad) * B.torso * TILT_TO_HIP_BACK
  const backward: V3 = scale(forward, -1)
  const hipMid: V3 = add(
    [anchor.hipMidX, anchor.hipMidY, 0],
    scale(backward, hipBackShift)
  )
  // Auto-bend knees on torso tilt — but only if the knee still has "room" to
  // bend (i.e., anchor knee angle is near straight). This avoids double-
  // counting: imported anchors already carry their real knee bend, and
  // blindly subtracting tilt*K would drive the knee below anatomical range
  // and flip the shin upward.
  const clampKnee = (k: number) => Math.max(EFFECTIVE_KNEE_MIN_DEG, Math.min(180, k))
  const kneeRoom = (kneeDeg: number) => Math.max(0, (kneeDeg - 90) / 90)
  const kneeCompFor = (kneeDeg: number) =>
    Math.abs(anchor.torsoTiltDeg) * TILT_TO_KNEE_BEND * kneeRoom(kneeDeg)
  const effLeftKnee  = clampKnee(anchor.leftKneeAngleDeg  - kneeCompFor(anchor.leftKneeAngleDeg))
  const effRightKnee = clampKnee(anchor.rightKneeAngleDeg - kneeCompFor(anchor.rightKneeAngleDeg))

  // Hips (23 L, 24 R) ───────────────────────────────────────────────────────
  const lHip: V3 = add(hipMid, scale(across,  B.hipWidth / 2))
  const rHip: V3 = add(hipMid, scale(across, -B.hipWidth / 2))
  out[LM.L_HIP] = mkLm(LM.L_HIP, lHip)
  out[LM.R_HIP] = mkLm(LM.R_HIP, rHip)

  // Shoulders (11 L, 12 R) ──────────────────────────────────────────────────
  const shoulderMid: V3 = add(hipMid, scale(torsoUp, B.torso))
  const lShoulder: V3 = add(shoulderMid, scale(across,  B.shoulderWidth / 2))
  const rShoulder: V3 = add(shoulderMid, scale(across, -B.shoulderWidth / 2))
  out[LM.L_SHOULDER] = mkLm(LM.L_SHOULDER, lShoulder)
  out[LM.R_SHOULDER] = mkLm(LM.R_SHOULDER, rShoulder)

  // Head / face (0-10) ──────────────────────────────────────────────────────
  const nose: V3 = add(shoulderMid, scale(torsoUp, B.headToShoulder * 0.75))
  const mouthOffset: V3 = scale(torsoDown, 0.02)
  out[LM.NOSE] = mkLm(LM.NOSE, nose)
  out[LM.L_EYE_INNER] = mkLm(LM.L_EYE_INNER, add(nose, scale(across,  0.015)))
  out[LM.L_EYE]       = mkLm(LM.L_EYE,       add(nose, scale(across,  0.030)))
  out[LM.L_EYE_OUTER] = mkLm(LM.L_EYE_OUTER, add(nose, scale(across,  0.045)))
  out[LM.R_EYE_INNER] = mkLm(LM.R_EYE_INNER, add(nose, scale(across, -0.015)))
  out[LM.R_EYE]       = mkLm(LM.R_EYE,       add(nose, scale(across, -0.030)))
  out[LM.R_EYE_OUTER] = mkLm(LM.R_EYE_OUTER, add(nose, scale(across, -0.045)))
  out[LM.L_EAR] = mkLm(LM.L_EAR, add(nose, scale(across,  0.06)))
  out[LM.R_EAR] = mkLm(LM.R_EAR, add(nose, scale(across, -0.06)))
  out[LM.MOUTH_L] = mkLm(LM.MOUTH_L, add(add(nose, mouthOffset), scale(across,  0.015)))
  out[LM.MOUTH_R] = mkLm(LM.MOUTH_R, add(add(nose, mouthOffset), scale(across, -0.015)))

  // Arm chains — same FK for both sides, parameterized per side ────────────
  // Shoulder has two DOF:
  //   forward flexion (in sagittal plane, around across-axis)
  //   abduction / sideways (in coronal plane, around forward-axis)
  // Sign convention for abduction flips per side: positive abduction swings
  // the arm AWAY from the body midline.
  function buildArm(side: 'L' | 'R') {
    const shFwdDeg  = side === 'L' ? anchor.leftShoulderAngleDeg     : anchor.rightShoulderAngleDeg
    const shAbdDeg  = side === 'L' ? anchor.leftShoulderAbductionDeg : anchor.rightShoulderAbductionDeg
    const elbowDeg  = side === 'L' ? anchor.leftElbowAngleDeg        : anchor.rightElbowAngleDeg
    const wristDeg  = side === 'L' ? anchor.leftWristAngleDeg        : anchor.rightWristAngleDeg
    const twistDeg  = side === 'L' ? anchor.leftForearmTwistDeg      : anchor.rightForearmTwistDeg
    const abSign = side === 'L' ? +1 : -1
    const shoulder  = side === 'L' ? lShoulder : rShoulder
    const idxElbow  = side === 'L' ? LM.L_ELBOW : LM.R_ELBOW
    const idxWrist  = side === 'L' ? LM.L_WRIST : LM.R_WRIST
    const idxPinky  = side === 'L' ? LM.L_PINKY : LM.R_PINKY
    const idxIndex  = side === 'L' ? LM.L_INDEX : LM.R_INDEX
    const idxThumb  = side === 'L' ? LM.L_THUMB : LM.R_THUMB

    // Upper arm — override direct direction if available.
    const o = anchor.dirOverrides
    const upperArmDir = o
      ? normalize((side === 'L' ? o.leftUpperArm : o.rightUpperArm) as V3)
      : normalize(rotAroundAxis(rotAroundAxis(torsoDown, forward, abSign * shAbdDeg), across, -shFwdDeg))
    const elbow = add(shoulder, scale(upperArmDir, B.upperArm))
    out[idxElbow] = mkLm(idxElbow, elbow)

    const elbowBend = 180 - elbowDeg
    const forearmDir = o
      ? normalize((side === 'L' ? o.leftForearm : o.rightForearm) as V3)
      : normalize(rotAroundAxis(upperArmDir, across, -elbowBend))
    const wrist = add(elbow, scale(forearmDir, B.forearm))
    out[idxWrist] = mkLm(idxWrist, wrist)

    const wristBend = 180 - wristDeg
    const handDir = normalize(rotAroundAxis(forearmDir, across, -wristBend))
    const handCenter = add(wrist, scale(handDir, B.hand))
    // Twist rotates the finger fan around the forearm axis.
    const fanSide = normalize(rotAroundAxis(across, forearmDir, twistDeg))
    out[idxPinky] = mkLm(idxPinky, add(handCenter, scale(fanSide, -B.hand * 0.3)))
    out[idxIndex] = mkLm(idxIndex, add(handCenter, scale(fanSide,  B.hand * 0.2)))
    out[idxThumb] = mkLm(idxThumb, add(handCenter, scale(fanSide,  B.hand * 0.4)))
  }

  buildArm('R')
  buildArm('L')

  // Legs ────────────────────────────────────────────────────────────────────
  // Each thigh has independent hip flexion (forward) and abduction (sideways).
  // This is what enables deep squats — the thigh tilts forward so the knee
  // can bend heavily without the shin folding back unnaturally.
  //
  // Order of rotation (both around the body frame):
  //   1. Start from world-down.
  //   2. Apply abduction: rotate around the body's forward axis.
  //   3. Apply forward flexion: rotate around the body's across axis.
  const worldDown: V3 = [0, 1, 0]

  function thighDirFor(side: 'L' | 'R'): V3 {
    // Fast path: if an exact direction was imported, use it verbatim — no
    // angle-decomposition loss. This is how frame-imported anchors stay
    // faithful to the source pose.
    const o = anchor.dirOverrides
    if (o) {
      // Imported direction already encodes real orientation; don't double-yaw.
      const v = side === 'L' ? o.leftThigh : o.rightThigh
      return normalize(v as V3)
    }
    const flexDeg = side === 'L' ? anchor.leftThighForwardDeg  : anchor.rightThighForwardDeg
    const absDeg  = side === 'L' ? anchor.leftThighAbductionDeg : anchor.rightThighAbductionDeg
    const footYaw = side === 'L' ? anchor.leftFootYawDeg : anchor.rightFootYawDeg
    // Right-leg abduction positive means swing to player's right (−across).
    const abSign = side === 'L' ? +1 : -1
    const abducted = rotAroundAxis(worldDown, forward, abSign * absDeg)
    const flexed = normalize(rotAroundAxis(abducted, across, -flexDeg))
    // Foot yaw couples to HIP EXTERNAL ROTATION — rotating the leg around the
    // vertical axis turns the whole chain (thigh → shin → ankle → foot). Without
    // this, feet would appear to twist independent of the leg.
    return normalize(rotY(flexed, footYaw))
  }

  const shinDirFor = (side: 'L' | 'R', thighDir: V3, effKnee: number): V3 => {
    const o = anchor.dirOverrides
    if (o) return normalize((side === 'L' ? o.leftShin : o.rightShin) as V3)
    const kneeBend = 180 - effKnee
    return normalize(rotAroundAxis(thighDir, across, kneeBend))
  }

  // Right leg ───────────────────────────────────────────────────────────────
  const rThighDir: V3 = thighDirFor('R')
  const rKnee: V3 = add(rHip, scale(rThighDir, B.thigh))
  const rShinDir: V3 = shinDirFor('R', rThighDir, effRightKnee)
  const rAnkle: V3 = add(rKnee, scale(rShinDir, B.shin))
  out[LM.R_KNEE]  = mkLm(LM.R_KNEE,  rKnee)
  out[LM.R_ANKLE] = mkLm(LM.R_ANKLE, rAnkle)
  const rFootDir: V3 = anchor.dirOverrides
    ? normalize(anchor.dirOverrides.rightFoot as V3)
    : normalize(rotY(forward, anchor.rightFootYawDeg))
  const rFootTip: V3 = add(rAnkle, scale(rFootDir, B.footForward))
  const rHeel:    V3 = add(rAnkle, scale(rFootDir, -B.footForward * 0.4))
  out[LM.R_HEEL] = mkLm(LM.R_HEEL, rHeel)
  out[LM.R_FOOT] = mkLm(LM.R_FOOT, rFootTip)

  // Left leg ────────────────────────────────────────────────────────────────
  const lThighDir: V3 = thighDirFor('L')
  const lKnee: V3 = add(lHip, scale(lThighDir, B.thigh))
  const lShinDir: V3 = shinDirFor('L', lThighDir, effLeftKnee)
  const lAnkle: V3 = add(lKnee, scale(lShinDir, B.shin))
  out[LM.L_KNEE]  = mkLm(LM.L_KNEE,  lKnee)
  out[LM.L_ANKLE] = mkLm(LM.L_ANKLE, lAnkle)
  const lFootDir: V3 = anchor.dirOverrides
    ? normalize(anchor.dirOverrides.leftFoot as V3)
    : normalize(rotY(forward, anchor.leftFootYawDeg))
  const lFootTip: V3 = add(lAnkle, scale(lFootDir, B.footForward))
  const lHeel:    V3 = add(lAnkle, scale(lFootDir, -B.footForward * 0.4))
  out[LM.L_HEEL] = mkLm(LM.L_HEEL, lHeel)
  out[LM.L_FOOT] = mkLm(LM.L_FOOT, lFootTip)

  // Post-pass: per-leg IK so BOTH feet land on the ground, regardless of how
  // differently each leg is flexed. The old "translate whole body by max
  // ankle dy" put one foot on the ground and floated the other. This solves
  // a 2-joint IK per leg: given hip + target ankle (x,z kept from FK, y
  // clamped to GROUND), compute a new knee position via cosine law such that
  // thigh and shin keep their canonical lengths.
  if (!options?.skipFootIK) {
    applyLegIK(out, LM.L_HIP, LM.L_KNEE, LM.L_ANKLE, LM.L_HEEL, LM.L_FOOT, forward, across, B.thigh, B.shin)
    applyLegIK(out, LM.R_HIP, LM.R_KNEE, LM.R_ANKLE, LM.R_HEEL, LM.R_FOOT, forward, across, B.thigh, B.shin)
  }

  return out
}

function lmV3(l: Landmark): V3 { return [l.x, l.y, l.z] }
function sub(a: V3, b: V3): V3 { return [a[0]-b[0], a[1]-b[1], a[2]-b[2]] }
function dot(a: V3, b: V3): number { return a[0]*b[0] + a[1]*b[1] + a[2]*b[2] }
function len(v: V3): number { return Math.sqrt(dot(v, v)) }

/** Apply 2-joint IK to one leg so the ankle lands on GROUND_ANCHOR_Y. */
function applyLegIK(
  out: Landmark[],
  hipIdx: number, kneeIdx: number, ankleIdx: number,
  heelIdx: number, footIdx: number,
  forward: V3, across: V3,
  thighLen: number, shinLen: number,
) {
  const T = thighLen
  const S = shinLen
  const hip = lmV3(out[hipIdx])
  const fkAnkle = lmV3(out[ankleIdx])
  // Target: keep the FK-computed horizontal (x, z) direction, clamp Y to floor.
  const target: V3 = [fkAnkle[0], GROUND_ANCHOR_Y, fkAnkle[2]]
  const toTarget = sub(target, hip)
  const Draw = len(toTarget)
  if (Draw < 1e-5) return
  const u: V3 = [toTarget[0] / Draw, toTarget[1] / Draw, toTarget[2] / Draw]

  // Truly unreachable (>15% beyond full leg length) — clamp to max reach so
  // the foot just floats above ground rather than producing a broken chain.
  const HARD_MAX = (T + S) * 1.15
  if (Draw > HARD_MAX) {
    const knee: V3 = [hip[0] + u[0]*T, hip[1] + u[1]*T, hip[2] + u[2]*T]
    const ankle: V3 = [hip[0] + u[0]*(T+S), hip[1] + u[1]*(T+S), hip[2] + u[2]*(T+S)]
    applyKneeAnkle(out, kneeIdx, ankleIdx, heelIdx, footIdx, knee, ankle, sub(ankle, fkAnkle))
    return
  }

  // Reachable (or minor stretch ≤15%): ankle lands exactly on target, bend at
  // knee via cosine law. When Draw exceeds T+S the clamp below produces a
  // straight leg (α=0) — imperceptible visual stretch, feet stay planted.
  const D = Draw
  const ankle: V3 = target
  const cosA = Math.max(-1, Math.min(1, (T * T + D * D - S * S) / (2 * T * D)))
  const alpha = Math.acos(cosA)
  // Perpendicular direction in the sagittal plane so the knee points forward.
  const fu = dot(forward, u)
  let vPerp: V3 = [forward[0] - fu * u[0], forward[1] - fu * u[1], forward[2] - fu * u[2]]
  let vLen = len(vPerp)
  if (vLen < 1e-3) {
    // Target colinear with body forward — fall back to across axis.
    const au = dot(across, u)
    vPerp = [across[0] - au * u[0], across[1] - au * u[1], across[2] - au * u[2]]
    vLen = len(vPerp)
    if (vLen < 1e-3) return
  }
  const vN: V3 = [vPerp[0] / vLen, vPerp[1] / vLen, vPerp[2] / vLen]
  const kC = T * Math.cos(alpha)
  const kS = T * Math.sin(alpha)
  const knee: V3 = [
    hip[0] + u[0] * kC + vN[0] * kS,
    hip[1] + u[1] * kC + vN[1] * kS,
    hip[2] + u[2] * kC + vN[2] * kS,
  ]

  applyKneeAnkle(out, kneeIdx, ankleIdx, heelIdx, footIdx, knee, ankle, sub(ankle, fkAnkle))
}

function applyKneeAnkle(
  out: Landmark[],
  kneeIdx: number, ankleIdx: number, heelIdx: number, footIdx: number,
  knee: V3, ankle: V3, ankleDelta: V3
) {
  out[kneeIdx]  = { ...out[kneeIdx],  x: knee[0],  y: knee[1],  z: knee[2] }
  out[ankleIdx] = { ...out[ankleIdx], x: ankle[0], y: ankle[1], z: ankle[2] }
  out[heelIdx]  = {
    ...out[heelIdx],
    x: out[heelIdx].x + ankleDelta[0],
    y: out[heelIdx].y + ankleDelta[1],
    z: out[heelIdx].z + ankleDelta[2],
  }
  out[footIdx]  = {
    ...out[footIdx],
    x: out[footIdx].x + ankleDelta[0],
    y: out[footIdx].y + ankleDelta[1],
    z: out[footIdx].z + ankleDelta[2],
  }
}
