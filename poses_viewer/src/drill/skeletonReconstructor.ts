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
  /** Per-side overrides (take precedence over the symmetric fallback above). */
  leftThigh?: number
  rightThigh?: number
  leftShin?: number
  rightShin?: number
  leftUpperArm?: number
  rightUpperArm?: number
  leftForearm?: number
  rightForearm?: number
  leftFootForward?: number
  rightFootForward?: number
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
 * Auto-compensation constants. Both are zero: the reconstructor honours slider
 * values literally. Earlier revisions added a touch of knee bend on forward
 * tilt (athletic-crouch look) and a hip-back shift for CoM balance, but the
 * interactive mannequin editor exposes every DOF explicitly — users would
 * rather set knee/hip values directly than fight against an auto-shift.
 */
const TILT_TO_KNEE_BEND = 0
const EFFECTIVE_KNEE_MIN_DEG = 30
const TILT_TO_HIP_BACK = 0

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
  // Two yaw layers stacked around the vertical axis through hipMid:
  //   figureYawDeg     — orients the entire figure (legs + hips + upper body)
  //   bodyRotationDeg  — pelvic twist relative to the planted legs (upper body
  //                      swings; legs stay where the figure yaw put them)
  // Legs use the leg frame; hips/torso/arms/head use the hip frame.
  const legForward: V3 = rotY([0, 0, -1], anchor.figureYawDeg)
  const legAcross:  V3 = rotY([1, 0, 0], anchor.figureYawDeg)
  const hipYawDeg = anchor.figureYawDeg + anchor.bodyRotationDeg
  const forward: V3 = rotY([0, 0, -1], hipYawDeg)
  const acrossLevel: V3 = rotY([1, 0, 0], hipYawDeg)
  // Pelvic roll: tilt the hip-across vector around forward. Positive lifts
  // the player's right hip (weight onto left leg). Legs still attach to the
  // rolled across so pelvic tilt propagates to leg positions, but the torso
  // stays vertical — bending is done independently via torsoSideBendDeg.
  const across: V3 = anchor.pelvicRollDeg !== 0
    ? normalize(rotAroundAxis(acrossLevel, forward, anchor.pelvicRollDeg))
    : acrossLevel
  // Shoulder frame: hip yaw + an independent corpus rotation. Shoulders and
  // arms live here. When shoulderRotationDeg = 0 the two frames coincide.
  const shoulderForward: V3 = rotY([0, 0, -1], hipYawDeg + anchor.shoulderRotationDeg)
  const shoulderAcross:  V3 = rotY([1, 0, 0], hipYawDeg + anchor.shoulderRotationDeg)

  // Torso tilt (single segment): rotate the torso-up vector forward around
  // the hip line. The whole spine rotates rigidly — hips are the pivot,
  // shoulders translate forward by sin(tilt)*torso. Then apply side-bend
  // around the body-forward axis so a relaxed/imbalanced posture can lean
  // sideways without tipping the whole body.
  const torsoUpFromTilt: V3 = normalize(rotAroundAxis([0, -1, 0], acrossLevel, anchor.torsoTiltDeg))
  const torsoUp: V3 = anchor.torsoSideBendDeg !== 0
    ? normalize(rotAroundAxis(torsoUpFromTilt, forward, anchor.torsoSideBendDeg))
    : torsoUpFromTilt
  const torsoDown: V3 = scale(torsoUp, -1)

  // Automatic tilt compensation ────────────────────────────────────────────
  // Forward tilt lets the knees bend slightly so the legs don't lock
  // straight when the torso leans. TILT_TO_HIP_BACK is 0 — balance comes
  // from the anchor values (thighForward, stanceWidth), not a synthetic shift.
  const tiltRad = deg(anchor.torsoTiltDeg)
  const hipBackShift = Math.sin(tiltRad) * B.torso * TILT_TO_HIP_BACK
  const backward: V3 = scale(forward, -1)
  const hipMid: V3 = add(
    [anchor.hipMidX, anchor.hipMidY, 0],
    scale(backward, hipBackShift)
  )
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
  // Single-segment torso: shoulderMid sits one torso length above hipMid
  // along torsoUp. The shoulder LINE is oriented by `shoulderAcross`, which
  // carries the corpus rotation — so twisting the trunk pivots L/R shoulder
  // around shoulderMid without moving the spine base.
  // Shoulder shrug raises/lowers the shoulder line along torsoUp without
  // stretching the spine in the FK chain (head still rides on spineUp, so
  // the head lifts with the shoulders — matches physical shrug motion).
  const shoulderMid: V3 = add(hipMid, scale(torsoUp, B.torso + anchor.shoulderShrugNorm))
  const lShoulder: V3 = add(shoulderMid, scale(shoulderAcross,  B.shoulderWidth / 2))
  const rShoulder: V3 = add(shoulderMid, scale(shoulderAcross, -B.shoulderWidth / 2))
  out[LM.L_SHOULDER] = mkLm(LM.L_SHOULDER, lShoulder)
  out[LM.R_SHOULDER] = mkLm(LM.R_SHOULDER, rShoulder)

  // Head / face (0-10) ──────────────────────────────────────────────────────
  // Head is part of the trunk — rides on torsoUp and follows shoulderAcross
  // for the L/R face landmarks so a twisted trunk turns the head with it.
  const nose: V3 = add(shoulderMid, scale(torsoUp, B.headToShoulder * 0.75))
  const mouthOffset: V3 = scale(torsoDown, 0.02)
  out[LM.NOSE] = mkLm(LM.NOSE, nose)
  out[LM.L_EYE_INNER] = mkLm(LM.L_EYE_INNER, add(nose, scale(shoulderAcross,  0.015)))
  out[LM.L_EYE]       = mkLm(LM.L_EYE,       add(nose, scale(shoulderAcross,  0.030)))
  out[LM.L_EYE_OUTER] = mkLm(LM.L_EYE_OUTER, add(nose, scale(shoulderAcross,  0.045)))
  out[LM.R_EYE_INNER] = mkLm(LM.R_EYE_INNER, add(nose, scale(shoulderAcross, -0.015)))
  out[LM.R_EYE]       = mkLm(LM.R_EYE,       add(nose, scale(shoulderAcross, -0.030)))
  out[LM.R_EYE_OUTER] = mkLm(LM.R_EYE_OUTER, add(nose, scale(shoulderAcross, -0.045)))
  out[LM.L_EAR] = mkLm(LM.L_EAR, add(nose, scale(shoulderAcross,  0.06)))
  out[LM.R_EAR] = mkLm(LM.R_EAR, add(nose, scale(shoulderAcross, -0.06)))
  out[LM.MOUTH_L] = mkLm(LM.MOUTH_L, add(add(nose, mouthOffset), scale(shoulderAcross,  0.015)))
  out[LM.MOUTH_R] = mkLm(LM.MOUTH_R, add(add(nose, mouthOffset), scale(shoulderAcross, -0.015)))

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
    const elbowYawDeg = side === 'L' ? anchor.leftElbowYawDeg : anchor.rightElbowYawDeg
    const wristYawDeg = side === 'L' ? anchor.leftWristYawDeg : anchor.rightWristYawDeg
    const abSign = side === 'L' ? +1 : -1
    const shoulder  = side === 'L' ? lShoulder : rShoulder
    const idxElbow  = side === 'L' ? LM.L_ELBOW : LM.R_ELBOW
    const idxWrist  = side === 'L' ? LM.L_WRIST : LM.R_WRIST
    const idxPinky  = side === 'L' ? LM.L_PINKY : LM.R_PINKY
    const idxIndex  = side === 'L' ? LM.L_INDEX : LM.R_INDEX
    const idxThumb  = side === 'L' ? LM.L_THUMB : LM.R_THUMB

    // Upper arm — two anatomical plane angles, composed as direct projections
    // onto the shoulder-frame axes (not sequential rotations). Sequential
    // rotations are order-dependent and gimbal-couple, so moving `flex` with
    // `abd` nonzero would sweep the arm along a curve instead of a meridian.
    //
    //   flex (shFwdDeg)  — angle in the sagittal plane (torsoDown↔shoulderForward)
    //   abd  (shAbdDeg)  — angle in the frontal plane (torsoDown↔shoulderAcross),
    //                      signed by side via abSign so +abd is lateral on both sides
    //
    // Properties:
    //   (0, 0)       → torsoDown (arm straight down)
    //   (90, 0)      → shoulderForward (pure sagittal forward)
    //   (180, 0)     → -torsoDown (overhead via front)
    //   (0, ±90)     → ±shoulderAcross (pure lateral)
    //   (0, 180)     → -torsoDown (overhead via side)
    // At abd = ±90° flex becomes a rotation around the arm's own axis — that's
    // an anatomical truth; fine orientation falls to elbow swivel / forearm twist.
    const fRad = deg(shFwdDeg)
    const aRad = deg(abSign * shAbdDeg)
    const cosA = Math.cos(aRad)
    const dDown    = Math.cos(fRad) * cosA
    const dForward = Math.sin(fRad) * cosA
    const dAcross  = Math.sin(aRad)
    const upperArmDir = normalize([
      dDown * torsoDown[0] + dForward * shoulderForward[0] + dAcross * shoulderAcross[0],
      dDown * torsoDown[1] + dForward * shoulderForward[1] + dAcross * shoulderAcross[1],
      dDown * torsoDown[2] + dForward * shoulderForward[2] + dAcross * shoulderAcross[2],
    ])
    const elbow = add(shoulder, scale(upperArmDir, B.upperArm))
    // NB: out[idxElbow] is written below after the swivel resolves elbowFinal.

    const elbowBend = 180 - elbowDeg
    // Elbow hinge — weighted combination that is continuous across the whole
    // (shAbd, shFwd) slider domain AND keeps the forearm bending into the
    // body's anterior half-space (toward the face/chest) for any arm pose.
    //
    // Three terms, all normalized together:
    //   1. 3·cross(torsoUp, upperArmDir) — the anatomical "bend in the spine+
    //      arm plane" axis. Strong when the arm is clearly abducted; vanishes
    //      when the arm is parallel to the spine (both arm-down and overhead).
    //   2. shoulderAcross — stable fallback for the arm-parallel-to-spine
    //      cases. Without this, the cross's sign flips as the arm crosses the
    //      spine pole, snapping the forearm 180°.
    //   3. 2·shoulderForward — anterior bias. Pushes the hinge toward the
    //      "forearm folds toward face/chest" half-space even for extreme poses
    //      (shFwd>90° with modest shAbd — arm raised behind-and-above the
    //      shoulder). Without this term, such poses let the hinge point
    //      backward, and the forearm folds AWAY from the body, which reads as
    //      anatomically impossible ("elbow bends the wrong way").
    // forearmTwistDeg still rotates the hand fan around the forearm axis.
    const HINGE_CROSS_WEIGHT = 3.0
    const HINGE_ANTERIOR_BIAS = 2.0
    const hingeRaw: V3 = [
      HINGE_CROSS_WEIGHT*(torsoUp[1]*upperArmDir[2] - torsoUp[2]*upperArmDir[1])
        + shoulderAcross[0] + HINGE_ANTERIOR_BIAS*shoulderForward[0],
      HINGE_CROSS_WEIGHT*(torsoUp[2]*upperArmDir[0] - torsoUp[0]*upperArmDir[2])
        + shoulderAcross[1] + HINGE_ANTERIOR_BIAS*shoulderForward[1],
      HINGE_CROSS_WEIGHT*(torsoUp[0]*upperArmDir[1] - torsoUp[1]*upperArmDir[0])
        + shoulderAcross[2] + HINGE_ANTERIOR_BIAS*shoulderForward[2],
    ]
    const elbowHinge: V3 = normalize(hingeRaw)
    // Elbow swivel (7-DOF arm redundancy parameter): shoulder and wrist are
    // pinned, elbow orbits the shoulder→wrist axis. Pass 1 computes the
    // reference wrist using the swivel=0 bend plane. Pass 2 re-solves the
    // elbow on the swivel circle around S→W at angle elbowYawDeg.
    //
    // At elbowYaw=0 the swivel circle places the elbow exactly at elbow0, so
    // the fast path produces byte-identical landmarks — existing neutrals
    // reconstruct unchanged. abSign mirrors the sign so +yaw means the elbow
    // swings laterally (away from the body midline) on both sides.
    const forearmDir0 = normalize(rotAroundAxis(upperArmDir, elbowHinge, -elbowBend))
    const wristLocked: V3 = add(elbow, scale(forearmDir0, B.forearm))
    let elbowFinal: V3 = elbow
    let forearmDir: V3 = forearmDir0
    if (elbowYawDeg !== 0) {
      const axisVec: V3 = [wristLocked[0] - shoulder[0], wristLocked[1] - shoulder[1], wristLocked[2] - shoulder[2]]
      const d = Math.sqrt(axisVec[0]*axisVec[0] + axisVec[1]*axisVec[1] + axisVec[2]*axisVec[2])
      if (d > 1e-6) {
        const axis: V3 = [axisVec[0]/d, axisVec[1]/d, axisVec[2]/d]
        const t = (d*d + B.upperArm*B.upperArm - B.forearm*B.forearm) / (2*d*d)
        const center: V3 = add(shoulder, scale(axisVec, t))
        const radiusSq = B.upperArm*B.upperArm - (t*d)*(t*d)
        const radius = Math.sqrt(Math.max(0, radiusSq))
        // Reference u = -(torsoUp projected ⊥ axis). At swivel=0 this points
        // from circle center toward elbow0 — so +θ/−θ rotate off that direction
        // via standard Rodrigues. Degenerate guard (arm aligned with torsoUp):
        // fall back to shoulderForward projection.
        const tuDotAxis = torsoUp[0]*axis[0] + torsoUp[1]*axis[1] + torsoUp[2]*axis[2]
        const upProj: V3 = [torsoUp[0] - tuDotAxis*axis[0], torsoUp[1] - tuDotAxis*axis[1], torsoUp[2] - tuDotAxis*axis[2]]
        const upProjLen = Math.sqrt(upProj[0]*upProj[0] + upProj[1]*upProj[1] + upProj[2]*upProj[2])
        let u: V3
        if (upProjLen > 1e-4) {
          u = [-upProj[0]/upProjLen, -upProj[1]/upProjLen, -upProj[2]/upProjLen]
        } else {
          const fDotAxis = shoulderForward[0]*axis[0] + shoulderForward[1]*axis[1] + shoulderForward[2]*axis[2]
          const fProj: V3 = [shoulderForward[0] - fDotAxis*axis[0], shoulderForward[1] - fDotAxis*axis[1], shoulderForward[2] - fDotAxis*axis[2]]
          u = normalize(fProj)
        }
        // v = axis × u; abSign flips handedness so +yaw is lateral on both sides.
        const vRaw: V3 = [
          axis[1]*u[2] - axis[2]*u[1],
          axis[2]*u[0] - axis[0]*u[2],
          axis[0]*u[1] - axis[1]*u[0],
        ]
        const v: V3 = scale(normalize(vRaw), abSign)
        const theta = deg(elbowYawDeg)
        const cosT = Math.cos(theta), sinT = Math.sin(theta)
        elbowFinal = [
          center[0] + radius*(cosT*u[0] + sinT*v[0]),
          center[1] + radius*(cosT*u[1] + sinT*v[1]),
          center[2] + radius*(cosT*u[2] + sinT*v[2]),
        ]
        forearmDir = normalize([
          wristLocked[0] - elbowFinal[0],
          wristLocked[1] - elbowFinal[1],
          wristLocked[2] - elbowFinal[2],
        ])
      }
    }
    out[idxElbow] = mkLm(idxElbow, elbowFinal)
    const wrist = add(elbowFinal, scale(forearmDir, B.forearm))
    out[idxWrist] = mkLm(idxWrist, wrist)

    const wristBend = 180 - wristDeg
    const bentHandDir = normalize(rotAroundAxis(forearmDir, shoulderAcross, -wristBend))
    // Twist rotates the finger fan around the forearm axis.
    const fanSideBase = normalize(rotAroundAxis(shoulderAcross, forearmDir, twistDeg))
    // Wrist yaw (ulnar/radial deviation) rotates the entire hand unit around
    // the hand-normal axis = cross(forearmDir, fanSide). This axis is
    // perpendicular to both the forearm and the finger-spread direction, so
    // the rotation deflects the hand sideways even when the wrist is straight
    // (bentHandDir == forearmDir). At wristYaw=0 the fast path is identity,
    // so existing neutrals reconstruct byte-for-byte.
    // abSign mirrors the sign so +yaw means radial deviation on both sides.
    const handNormal: V3 = normalize([
      forearmDir[1]*fanSideBase[2] - forearmDir[2]*fanSideBase[1],
      forearmDir[2]*fanSideBase[0] - forearmDir[0]*fanSideBase[2],
      forearmDir[0]*fanSideBase[1] - forearmDir[1]*fanSideBase[0],
    ])
    const handDir = wristYawDeg !== 0
      ? normalize(rotAroundAxis(bentHandDir, handNormal, abSign * wristYawDeg))
      : bentHandDir
    const fanSide = wristYawDeg !== 0
      ? normalize(rotAroundAxis(fanSideBase, handNormal, abSign * wristYawDeg))
      : fanSideBase
    const handCenter = add(wrist, scale(handDir, B.hand))
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
    const flexDeg = side === 'L' ? anchor.leftThighForwardDeg  : anchor.rightThighForwardDeg
    const absDeg  = side === 'L' ? anchor.leftThighAbductionDeg : anchor.rightThighAbductionDeg
    const kneeYaw = side === 'L' ? anchor.leftKneeYawDeg : anchor.rightKneeYawDeg
    const abSign = side === 'L' ? +1 : -1
    // Legs live in the LEG frame (figureYawDeg only) so pelvic twist
    // (bodyRotationDeg) doesn't sweep the planted feet. The thigh's yaw is
    // controlled by kneeYawDeg (where the knee points) — footYawDeg only
    // rotates the foot relative to the shin.
    const abducted = rotAroundAxis(worldDown, legForward, abSign * absDeg)
    const flexed = normalize(rotAroundAxis(abducted, legAcross, -flexDeg))
    return normalize(rotY(flexed, kneeYaw))
  }

  const shinDirFor = (side: 'L' | 'R', thighDir: V3, effKnee: number): V3 => {
    const kneeBend = 180 - effKnee
    // The knee bends in the vertical plane that the knee points along —
    // controlled by kneeYawDeg (independent of footYawDeg). Hinge axis is
    // horizontal and perpendicular to that direction.
    const kneeYawDeg = side === 'L' ? anchor.leftKneeYawDeg : anchor.rightKneeYawDeg
    const kneeForward: V3 = rotY(legForward, kneeYawDeg)
    const hinge: V3 = normalize([-kneeForward[2], 0, kneeForward[0]])
    return normalize(rotAroundAxis(thighDir, hinge, kneeBend))
  }

  // Per-side bone lengths — fall back to symmetric `B.thigh`/`B.shin` when not set.
  const Brt = bonesOverride?.rightThigh ?? B.thigh
  const Blt = bonesOverride?.leftThigh ?? B.thigh
  const Brs = bonesOverride?.rightShin ?? B.shin
  const Bls = bonesOverride?.leftShin ?? B.shin
  const Brf = bonesOverride?.rightFootForward ?? B.footForward
  const Blf = bonesOverride?.leftFootForward ?? B.footForward

  /**
   * Knee swivel — leg analog of arm elbow swivel.
   * Pass 1 builds today's knee/ankle from thighDir+shinDir. Pass 2 (only when
   * swivel ≠ 0) orbits the knee around the hip→ankle axis, keeping hip and
   * ankle pinned and bone lengths exact. At swivel=0 the fast path returns the
   * reference knee unchanged → byte-identical to today's output.
   * Reference basis: u = knee0 projected ⊥ axis (guarantees identity at 0);
   * v = abSign · normalize(axis × u) — +yaw = lateral (same sign convention
   * as the arm swivel).
   */
  function orbitKnee(hip: V3, knee0: V3, ankle: V3, thighLen: number, shinLen: number, swivelDeg: number, abSign: number): V3 {
    if (swivelDeg === 0) return knee0
    const axisVec: V3 = [ankle[0] - hip[0], ankle[1] - hip[1], ankle[2] - hip[2]]
    const d = Math.sqrt(axisVec[0]*axisVec[0] + axisVec[1]*axisVec[1] + axisVec[2]*axisVec[2])
    if (d < 1e-6) return knee0
    // Straight leg (ankle co-linear with hip+thigh+shin): no circle to orbit on.
    if (d > thighLen + shinLen - 1e-4) return knee0
    const axis: V3 = [axisVec[0]/d, axisVec[1]/d, axisVec[2]/d]
    const t = (d*d + thighLen*thighLen - shinLen*shinLen) / (2*d*d)
    const center: V3 = add(hip, scale(axisVec, t))
    const radius = Math.sqrt(Math.max(0, thighLen*thighLen - (t*d)*(t*d)))
    // u = (knee0 − center) projected ⊥ axis — points from circle center to today's knee.
    const kRaw: V3 = [knee0[0] - center[0], knee0[1] - center[1], knee0[2] - center[2]]
    const kDotAxis = kRaw[0]*axis[0] + kRaw[1]*axis[1] + kRaw[2]*axis[2]
    const uRaw: V3 = [kRaw[0] - kDotAxis*axis[0], kRaw[1] - kDotAxis*axis[1], kRaw[2] - kDotAxis*axis[2]]
    const uLen = Math.sqrt(uRaw[0]*uRaw[0] + uRaw[1]*uRaw[1] + uRaw[2]*uRaw[2])
    if (uLen < 1e-6) return knee0
    const u: V3 = [uRaw[0]/uLen, uRaw[1]/uLen, uRaw[2]/uLen]
    const vRaw: V3 = [
      axis[1]*u[2] - axis[2]*u[1],
      axis[2]*u[0] - axis[0]*u[2],
      axis[0]*u[1] - axis[1]*u[0],
    ]
    const v: V3 = scale(normalize(vRaw), abSign)
    const theta = deg(swivelDeg)
    const cosT = Math.cos(theta), sinT = Math.sin(theta)
    return [
      center[0] + radius*(cosT*u[0] + sinT*v[0]),
      center[1] + radius*(cosT*u[1] + sinT*v[1]),
      center[2] + radius*(cosT*u[2] + sinT*v[2]),
    ]
  }

  // Right leg ───────────────────────────────────────────────────────────────
  const rThighDir: V3 = thighDirFor('R')
  const rKnee0: V3 = add(rHip, scale(rThighDir, Brt))
  const rShinDir0: V3 = shinDirFor('R', rThighDir, effRightKnee)
  const rAnkle: V3 = add(rKnee0, scale(rShinDir0, Brs))
  const rKnee: V3 = orbitKnee(rHip, rKnee0, rAnkle, Brt, Brs, anchor.rightKneeSwivelDeg, -1)
  out[LM.R_KNEE]  = mkLm(LM.R_KNEE,  rKnee)
  out[LM.R_ANKLE] = mkLm(LM.R_ANKLE, rAnkle)
  const rFootDir: V3 = normalize(rotY(legForward, anchor.rightKneeYawDeg + anchor.rightFootYawDeg))
  const rFootTip: V3 = add(rAnkle, scale(rFootDir, Brf))
  const rHeel:    V3 = add(rAnkle, scale(rFootDir, -Brf * 0.4))
  out[LM.R_HEEL] = mkLm(LM.R_HEEL, rHeel)
  out[LM.R_FOOT] = mkLm(LM.R_FOOT, rFootTip)

  // Left leg ────────────────────────────────────────────────────────────────
  const lThighDir: V3 = thighDirFor('L')
  const lKnee0: V3 = add(lHip, scale(lThighDir, Blt))
  const lShinDir0: V3 = shinDirFor('L', lThighDir, effLeftKnee)
  const lAnkle: V3 = add(lKnee0, scale(lShinDir0, Bls))
  const lKnee: V3 = orbitKnee(lHip, lKnee0, lAnkle, Blt, Bls, anchor.leftKneeSwivelDeg, +1)
  out[LM.L_KNEE]  = mkLm(LM.L_KNEE,  lKnee)
  out[LM.L_ANKLE] = mkLm(LM.L_ANKLE, lAnkle)
  const lFootDir: V3 = normalize(rotY(legForward, anchor.leftKneeYawDeg + anchor.leftFootYawDeg))
  const lFootTip: V3 = add(lAnkle, scale(lFootDir, Blf))
  const lHeel:    V3 = add(lAnkle, scale(lFootDir, -Blf * 0.4))
  out[LM.L_HEEL] = mkLm(LM.L_HEEL, lHeel)
  out[LM.L_FOOT] = mkLm(LM.L_FOOT, lFootTip)

  // Post-pass: ground the lowest foot by translating the whole body vertically.
  //
  // This REPLACES the old cosine-law per-leg IK. That IK snapped each ankle
  // to GROUND and recomputed the knee from (hip, ankle, thighLen, shinLen).
  // Problem: with hipMidY=0.42 and GROUND=0.92, hip-to-ground ≈ thigh+shin,
  // so the cosine law always returned α≈0 — knees stayed straight no matter
  // what the user's kneeAngleDeg slider said. Bending the knee had zero
  // visible effect because IK immediately re-straightened the leg.
  //
  // New behaviour: honour the anchor's knee/thigh angles exactly, then shift
  // the whole body so the lowest ankle (max y in MediaPipe convention) sits
  // on GROUND_ANCHOR_Y. Bending knees → shorter vertical leg span → hips and
  // torso drop naturally. In asymmetric stances (lunges) the higher foot
  // floats; tune thigh-forward on the trail leg to plant it.
  if (options?.skipFootIK !== true) {
    const lowestAnkleY = Math.max(out[LM.L_ANKLE].y, out[LM.R_ANKLE].y)
    const dy = GROUND_ANCHOR_Y - lowestAnkleY
    if (Math.abs(dy) > 1e-9) {
      for (let i = 0; i < out.length; i++) {
        out[i].y += dy
      }
    }
  }

  return out
}
