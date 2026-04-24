/**
 * Extract a [PoseAnchor] from a raw MediaPipe 33-landmark pose.
 *
 * Used for bootstrapping a drill anchor from a recorded frame (e.g. pick
 * frame 42 of andrii_1_poses.json as the START anchor). All measurements
 * are done in the landmark's native normalized coordinate system, so the
 * output plugs straight into [reconstructFromAnchor] without rescaling.
 *
 * Note: `forearmTwistDeg` is now extracted from the pinky↔index fan
 * direction. Accuracy depends on hand-landmark visibility; clamped to
 * slider range [-90°, +90°] on return.
 */

import type { PoseAnchor } from './PoseAnchor'
import type { Landmark } from '../types'
import { LM } from './SkeletonModel'
import type { BoneLengthsOverride } from './skeletonReconstructor'

/** Compute a unit direction vector from the landmark at `fromIdx` to `toIdx`. */
function unitDir(lms: Landmark[], fromIdx: number, toIdx: number): [number, number, number] {
  const a = lms[fromIdx], b = lms[toIdx]
  const dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z
  const len = Math.sqrt(dx*dx + dy*dy + dz*dz) || 1e-9
  return [dx / len, dy / len, dz / len]
}

const clamp = (v: number, min: number, max: number) =>
  Math.max(min, Math.min(max, v))

type V3 = { x: number; y: number; z: number }

const toV3 = (l: Landmark): V3 => ({ x: l.x, y: l.y, z: l.z })
const sub = (a: V3, b: V3): V3 => ({ x: a.x - b.x, y: a.y - b.y, z: a.z - b.z })
const mid = (a: V3, b: V3): V3 => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, z: (a.z + b.z) / 2 })

function length(v: V3): number {
  return Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
}

/** Angle between two vectors in degrees (unsigned, 0..180). */
function angleBetween(a: V3, b: V3): number {
  const dot = a.x * b.x + a.y * b.y + a.z * b.z
  const m = length(a) * length(b)
  if (m < 1e-9) return 0
  const c = Math.max(-1, Math.min(1, dot / m))
  return (Math.acos(c) * 180) / Math.PI
}

/**
 * Signed rotation angle from vector `from` to vector `to` around `axis` —
 * all inputs treated as directions (magnitude ignored). Result in degrees,
 * positive = right-hand-rule around axis. Returns 0 if either vector
 * projects to near-zero length in the plane perpendicular to axis
 * (degenerate — no bend-plane to measure against).
 */
function signedAngleAround(from: V3, to: V3, axis: V3): number {
  const projectPerp = (v: V3): V3 => {
    const d = v.x * axis.x + v.y * axis.y + v.z * axis.z
    return { x: v.x - d * axis.x, y: v.y - d * axis.y, z: v.z - d * axis.z }
  }
  const a = projectPerp(from)
  const b = projectPerp(to)
  const aLen = length(a)
  const bLen = length(b)
  if (aLen < 1e-4 || bLen < 1e-4) return 0
  const ax = { x: a.x / aLen, y: a.y / aLen, z: a.z / aLen }
  const bx = { x: b.x / bLen, y: b.y / bLen, z: b.z / bLen }
  const dot = Math.max(-1, Math.min(1, ax.x * bx.x + ax.y * bx.y + ax.z * bx.z))
  // Signed via axis dotted with (ax × bx).
  const cx = ax.y * bx.z - ax.z * bx.y
  const cy = ax.z * bx.x - ax.x * bx.z
  const cz = ax.x * bx.y - ax.y * bx.x
  const sign = Math.sign(cx * axis.x + cy * axis.y + cz * axis.z) || 1
  return sign * Math.acos(dot) * 180 / Math.PI
}

/**
 * Compute bone-length overrides from raw landmarks so reconstructing with
 * these lengths will MATCH the source pose's scale exactly. Used for tests
 * and any "faithful replay" mode that wants the canonical FK math but with
 * the player's actual bone proportions.
 */
export function extractBoneLengths(lms: Landmark[]): BoneLengthsOverride {
  const lHip = lms[LM.L_HIP], rHip = lms[LM.R_HIP]
  const lSh = lms[LM.L_SHOULDER], rSh = lms[LM.R_SHOULDER]
  const hipMid = { x: (lHip.x + rHip.x) / 2, y: (lHip.y + rHip.y) / 2, z: (lHip.z + rHip.z) / 2 }
  const shMid  = { x: (lSh.x + rSh.x) / 2,  y: (lSh.y + rSh.y) / 2,  z: (lSh.z + rSh.z) / 2 }
  const dist = (a: Landmark, b: Landmark) => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z)
  const leftThigh  = dist(lHip, lms[LM.L_KNEE])
  const rightThigh = dist(rHip, lms[LM.R_KNEE])
  const leftShin   = dist(lms[LM.L_KNEE], lms[LM.L_ANKLE])
  const rightShin  = dist(lms[LM.R_KNEE], lms[LM.R_ANKLE])
  const leftUpperArm  = dist(lSh, lms[LM.L_ELBOW])
  const rightUpperArm = dist(rSh, lms[LM.R_ELBOW])
  const leftForearm   = dist(lms[LM.L_ELBOW], lms[LM.L_WRIST])
  const rightForearm  = dist(lms[LM.R_ELBOW], lms[LM.R_WRIST])
  const leftFootForward  = dist(lms[LM.L_ANKLE], lms[LM.L_FOOT])
  const rightFootForward = dist(lms[LM.R_ANKLE], lms[LM.R_FOOT])
  return {
    torso: Math.hypot(shMid.x - hipMid.x, shMid.y - hipMid.y, shMid.z - hipMid.z),
    shoulderWidth: dist(lSh, rSh),
    hipWidth: dist(lHip, rHip),
    upperArm: (leftUpperArm + rightUpperArm) / 2,
    forearm:  (leftForearm  + rightForearm)  / 2,
    thigh:    (leftThigh    + rightThigh)    / 2,
    shin:     (leftShin     + rightShin)     / 2,
    footForward: (leftFootForward + rightFootForward) / 2,
    headToShoulder: dist(lms[LM.NOSE], { ...lSh, x: (lSh.x + rSh.x)/2, y: (lSh.y + rSh.y)/2, z: (lSh.z + rSh.z)/2 }),
    leftThigh, rightThigh, leftShin, rightShin,
    leftUpperArm, rightUpperArm, leftForearm, rightForearm,
    leftFootForward, rightFootForward,
  }
}

/** Rodrigues rotation around `axis` by `degAngle` degrees (V3 object form). */
function rotAroundAxisObj(v: V3, axis: V3, degAngle: number): V3 {
  const aLen = Math.sqrt(axis.x*axis.x + axis.y*axis.y + axis.z*axis.z) || 1e-9
  const k = { x: axis.x/aLen, y: axis.y/aLen, z: axis.z/aLen }
  const rad = degAngle * Math.PI / 180
  const c = Math.cos(rad), s = Math.sin(rad)
  const d = k.x*v.x + k.y*v.y + k.z*v.z
  const cx = k.y*v.z - k.z*v.y
  const cy = k.z*v.x - k.x*v.z
  const cz = k.x*v.y - k.y*v.x
  return {
    x: v.x*c + cx*s + k.x*d*(1-c),
    y: v.y*c + cy*s + k.y*d*(1-c),
    z: v.z*c + cz*s + k.z*d*(1-c),
  }
}

/**
 * Extract wrist yaw (ulnar/radial deviation) by inverting the FK hand-fan
 * rotation. FK rotates the hand unit (bentHandDir + 0.2·fanSide) around
 * handNormal = cross(forearmDir, fanSideBase) by abSign·yaw. The reference
 * direction at yaw=0 and the observed wrist→index direction are both rotated
 * by the same angle around handNormal, so the signed angle between them IS
 * the yaw.
 *
 * Assumes forearm twist = 0 when constructing fanSideBase — fanSideBase at
 * twist=0 is rot(shoulderAcross, forearmDir, 0) = shoulderAcross (unnormalized),
 * which matches the FK identity case. Task 7 lifts this assumption.
 */
function computeWristYaw(
  forearmUnit: V3,
  shoulderAcross: V3,
  wristAngleDeg: number,
  wristToIndex: V3,
  abSign: number,
): number {
  if (length(wristToIndex) < 1e-4) return 0
  // bentHandDir at wristYaw=0 — mirrors FK's normalize(rot(forearmDir, shoulderAcross, -wristBend))
  const wristBend = 180 - wristAngleDeg
  const bentHandDir = rotAroundAxisObj(forearmUnit, shoulderAcross, -wristBend)
  // fanSideBase at twist=0 — FK's normalize(rot(shoulderAcross, forearmDir, 0)) = shoulderAcross
  const fanSideBase = rotAroundAxisObj(shoulderAcross, forearmUnit, 0)
  // handNormal = cross(forearmDir, fanSideBase) — the axis that wristYaw rotates around
  const handNormal: V3 = {
    x: forearmUnit.y * fanSideBase.z - forearmUnit.z * fanSideBase.y,
    y: forearmUnit.z * fanSideBase.x - forearmUnit.x * fanSideBase.z,
    z: forearmUnit.x * fanSideBase.y - forearmUnit.y * fanSideBase.x,
  }
  const hnLen = length(handNormal)
  if (hnLen < 1e-6) return 0
  const handNormalU: V3 = { x: handNormal.x/hnLen, y: handNormal.y/hnLen, z: handNormal.z/hnLen }
  // Reference direction at yaw=0: normalize(bentHandDir + 0.2 · fanSideBase)
  const ref: V3 = {
    x: bentHandDir.x + 0.2 * fanSideBase.x,
    y: bentHandDir.y + 0.2 * fanSideBase.y,
    z: bentHandDir.z + 0.2 * fanSideBase.z,
  }
  const rLen = length(ref) || 1e-9
  const refU: V3 = { x: ref.x/rLen, y: ref.y/rLen, z: ref.z/rLen }
  // Observed direction: wrist → index (from landmarks)
  const oLen = length(wristToIndex) || 1e-9
  const obsU: V3 = { x: wristToIndex.x/oLen, y: wristToIndex.y/oLen, z: wristToIndex.z/oLen }
  return abSign * signedAngleAround(refU, obsU, handNormalU)
}

/**
 * Extract forearm twist (pronation/supination) by inverting the FK's
 * fan-rotation. FK rotates shoulderAcross around forearmDir by twistDeg to
 * get fanSideBase, then optionally rotates by wristYaw around handNormal to
 * get fanSide. Reversing:
 *   fanSide_obs   = normalize(index - pinky)  // from landmarks
 *   fanSideBase   = rot(fanSide_obs, handNormal, -abSign·wristYaw)
 *   twistDeg      = signedAngleAround(shoulderAcross, fanSideBase, forearmDir)
 *
 * handNormal is approximated as cross(forearmDir, shoulderAcross) — matches
 * FK exactly at twist=0 and drifts slightly for larger twist. Acceptable
 * within the [-90°, +90°] slider range because slider clamp + round-trip
 * tolerance absorb the residual error.
 */
function computeForearmTwist(
  forearmUnit: V3,
  shoulderAcross: V3,
  wristYawDeg: number,
  indexLm: V3,
  pinkyLm: V3,
  abSign: number,
): number {
  const fanSideObs: V3 = {
    x: indexLm.x - pinkyLm.x,
    y: indexLm.y - pinkyLm.y,
    z: indexLm.z - pinkyLm.z,
  }
  const fsLen = length(fanSideObs)
  if (fsLen < 1e-4) return 0
  const fanSideObsU: V3 = { x: fanSideObs.x/fsLen, y: fanSideObs.y/fsLen, z: fanSideObs.z/fsLen }
  // Approximated handNormal at twist=0: cross(forearmDir, shoulderAcross).
  // Degenerate only if forearmDir ∥ shoulderAcross (anatomically rare).
  const handNormal: V3 = {
    x: forearmUnit.y * shoulderAcross.z - forearmUnit.z * shoulderAcross.y,
    y: forearmUnit.z * shoulderAcross.x - forearmUnit.x * shoulderAcross.z,
    z: forearmUnit.x * shoulderAcross.y - forearmUnit.y * shoulderAcross.x,
  }
  const hnLen = length(handNormal) || 1e-9
  const handNormalU: V3 = { x: handNormal.x/hnLen, y: handNormal.y/hnLen, z: handNormal.z/hnLen }
  // Undo wristYaw to recover fanSideBase.
  const fanSideBase = wristYawDeg !== 0
    ? rotAroundAxisObj(fanSideObsU, handNormalU, -abSign * wristYawDeg)
    : fanSideObsU
  return signedAngleAround(shoulderAcross, fanSideBase, forearmUnit)
}

export function extractAnchorFromLandmarks(lms: Landmark[]): PoseAnchor {
  const get = (i: number): V3 => toV3(lms[i])

  const lHip = get(LM.L_HIP); const rHip = get(LM.R_HIP)
  const lSh  = get(LM.L_SHOULDER); const rSh = get(LM.R_SHOULDER)
  const rElbow = get(LM.R_ELBOW); const rWrist = get(LM.R_WRIST); const rIndex = get(LM.R_INDEX)
  const lElbow = get(LM.L_ELBOW); const lWrist = get(LM.L_WRIST); const lIndex = get(LM.L_INDEX)
  const lKnee = get(LM.L_KNEE);  const rKnee = get(LM.R_KNEE)
  const lAnkle = get(LM.L_ANKLE); const rAnkle = get(LM.R_ANKLE)
  const lFootTip = get(LM.L_FOOT); const rFootTip = get(LM.R_FOOT)

  const hipMid = mid(lHip, rHip)
  const shMid  = mid(lSh, rSh)

  // Body rotation: yaw in the XZ plane, averaged across hip and shoulder
  // axes for robustness — MediaPipe z is noisy, so using only one axis
  // amplifies that noise. The sign is also flipped vs the naive
  // `atan2(hipAxis.z, hipAxis.x)`: FK's bodyRotation convention has positive
  // values rotating the body so its L side goes TOWARD the camera (−z), which
  // makes lHip.z < rHip.z, so hipAxis.z = lHip.z−rHip.z < 0 — we negate to
  // match. Weighting by 2D magnitude down-weights axes that collapse (e.g.,
  // torso bent far forward makes shoulder axis less reliable).
  const hipAxis = sub(lHip, rHip)
  const shAxis  = sub(lSh, rSh)
  const hipMag2D = Math.hypot(hipAxis.x, hipAxis.z)
  const shMag2D  = Math.hypot(shAxis.x, shAxis.z)
  const totalMag = hipMag2D + shMag2D || 1
  const hipYaw = Math.atan2(-hipAxis.z, hipAxis.x)
  const shYaw  = Math.atan2(-shAxis.z,  shAxis.x)
  const bodyRotationDeg = ((hipYaw * hipMag2D + shYaw * shMag2D) / totalMag) * 180 / Math.PI

  // Torso tilt: angle between torso vector and body-up direction (world up
  // is OK here since body up tracks world up up to small pitch/roll, and we
  // separately measure yaw as bodyRotation). The z-component dominates for
  // a heavy forward bend which over-estimates tilt due to MediaPipe z noise,
  // so we dampen z to half weight — brings extracted tilt closer to what FK
  // can reproduce in 2D projection.
  const torso = sub(shMid, hipMid) // points upward from hip
  const torsoDamped: V3 = { x: torso.x, y: torso.y, z: torso.z * 0.5 }
  const upRef: V3 = { x: 0, y: -1, z: 0 }
  let torsoTiltDeg = angleBetween(torsoDamped, upRef)
  if (torso.z > 0) torsoTiltDeg = -torsoTiltDeg

  // Corpus rotation: shoulder-line yaw minus hip-line yaw (the "X-factor").
  // Using the 2D-weighted per-axis yaws already computed above.
  const shoulderRotationDeg = ((shYaw - hipYaw) * 180) / Math.PI

  // Arm chain (right side).
  const rUpperArm = sub(rElbow, rSh)
  const rForearm = sub(rWrist, rElbow)
  const rWristToIndex = sub(rIndex, rWrist)

  // Body-frame axes used for shoulder decomposition below.
  // Duplicated on purpose — this block runs before `bodyRad`/`cosB`/`sinB`
  // are defined for thighs. Keeping consts local so TS doesn't complain.
  const _bodyRad = (bodyRotationDeg * Math.PI) / 180
  const _cosB = Math.cos(_bodyRad), _sinB = Math.sin(_bodyRad)
  const _forwardX = -_sinB, _forwardZ = -_cosB
  const _acrossX = _cosB, _acrossZ = -_sinB

  // MediaPipe z is noisy — dampen it by 50% in all limb decompositions so
  // noisy depth doesn't inflate extracted flexion/abduction angles.
  const Z_DAMP = 0.5

  /**
   * Decompose an upper-arm vector (shoulder → elbow) into forward flexion and
   * sideways abduction angles. Mirrors the thigh decomposition below so the
   * FK chain reproduces the correct 3D direction (not just a projected sum).
   */
  const decomposeArm = (arm: V3, abSignForAbduction: number) => {
    const zDamped: V3 = { x: arm.x, y: arm.y, z: arm.z * Z_DAMP }
    const L = length(zDamped) || 1e-9
    const vert = zDamped.y / L
    const fwd = (zDamped.x * _forwardX + zDamped.z * _forwardZ) / L
    const acr = (zDamped.x * _acrossX + zDamped.z * _acrossZ) / L
    // Inverse of FK rotation chain: v = (sin(ab), cos(ab)*cos(flex), −cos(ab)*sin(flex)).
    // So ab = asin(acr) (abduction fully determines the across component),
    // and flex = atan2(fwd, vert) (flex is independent of ab in the y/forward ratio).
    const acrClamped = Math.max(-1, Math.min(1, abSignForAbduction * acr))
    const abduct = (Math.asin(acrClamped) * 180) / Math.PI
    const flex = (Math.atan2(fwd, vert) * 180) / Math.PI
    return { flex, abduct }
  }

  const rArmDecomp = decomposeArm(rUpperArm, -1) // right: flip sign for -across
  const lArmDecomp = decomposeArm(sub(lElbow, lSh), +1)
  const rightShoulderAngleDeg     = rArmDecomp.flex
  const rightShoulderAbductionDeg = rArmDecomp.abduct
  const leftShoulderAngleDeg      = lArmDecomp.flex
  const leftShoulderAbductionDeg  = lArmDecomp.abduct

  // Elbow + wrist — interior angles.
  const rightElbowAngleDeg = angleBetween(sub(rSh, rElbow), rForearm)
  const rightWristAngleDeg = angleBetween(sub(rElbow, rWrist), rWristToIndex)
  const leftElbowAngleDeg  = angleBetween(sub(lSh, lElbow), sub(lWrist, lElbow))
  const leftWristAngleDeg  = angleBetween(sub(lElbow, lWrist), sub(lIndex, lWrist))

  // Humeral twist (elbowYaw) extraction.
  //
  // FK places the forearm at:
  //   forearmDir = rot(upperArmDir, twistedHinge, -(180 − elbowDeg))
  //   twistedHinge = rot(elbowHinge, upperArmDir, abSign * elbowYaw)
  //   elbowHinge = normalize(3·cross(torsoUp, upperArmDir)
  //                        + shoulderAcross + 2·shoulderForward)
  //
  // To invert: recover the component of twistedHinge ⊥ to upperArmDir from
  // the observed forearmDir and upperArmDir, then measure the signed angle
  // from elbowHinge to that component around upperArmDir. The elbowHinge is
  // NOT generally ⊥ to upperArmDir, so a simple cross-product proxy is
  // inaccurate — see the inline derivation inside computeElbowYaw for the
  // exact Rodrigues-based formula.
  //
  // Skips when the elbow is nearly straight (elbowDeg ≥ 175°) — the bend
  // plane degenerates and the extracted yaw becomes meaningless noise.
  const computeElbowYaw = (
    upperArmDir: V3,
    forearmDir: V3,
    abSign: number,
    elbowDeg: number,
  ): number => {
    if (elbowDeg >= 175) return 0
    // Body-frame axes — the extractor puts all the observed yaw into
    // figureYawDeg with bodyRotationDeg = 0 (see the return below), so
    // _forwardX/_forwardZ/_acrossX/_acrossZ are the body frame here.
    const shoulderAcross: V3 = { x: _acrossX,  y: 0, z: _acrossZ  }
    const shoulderForward: V3 = { x: _forwardX, y: 0, z: _forwardZ }
    const torsoUp: V3 = { x: 0, y: -1, z: 0 }
    const crossTU_UA: V3 = {
      x: torsoUp.y * upperArmDir.z - torsoUp.z * upperArmDir.y,
      y: torsoUp.z * upperArmDir.x - torsoUp.x * upperArmDir.z,
      z: torsoUp.x * upperArmDir.y - torsoUp.y * upperArmDir.x,
    }
    const hingeRaw: V3 = {
      x: 3 * crossTU_UA.x + shoulderAcross.x + 2 * shoulderForward.x,
      y: 3 * crossTU_UA.y + shoulderAcross.y + 2 * shoulderForward.y,
      z: 3 * crossTU_UA.z + shoulderAcross.z + 2 * shoulderForward.z,
    }
    const hLen = length(hingeRaw) || 1e-9
    const elbowHinge: V3 = {
      x: hingeRaw.x / hLen,
      y: hingeRaw.y / hLen,
      z: hingeRaw.z / hLen,
    }
    // Recover the direction of twistedHinge in the plane ⊥ to upperArmDir.
    //
    // FK: forearmDir = rot(upperArmDir, twistedHinge, -elbowBend)  [Rodrigues]
    // Let p = elbowHinge·upperArmDir (preserved by twist rotation around upperArmDir).
    // Let elbowB = 180 − elbowDeg, c = cos(elbowB), β = sin(elbowB).
    // Decomposing the Rodrigues formula in the ⊥-to-upperArmDir plane gives:
    //   forearmDir_perp = (c(1−p²)+p²)·upperArmDir subtracted from forearmDir
    //   twistedHinge_perp ∝ α·forearmDir_perp − β·(upperArmDir × forearmDir_perp)
    // where α = p·(1−c).
    // signedAngleAround projects both elbowHinge and the recovered direction
    // onto the ⊥-plane, so the parallel component of twistedHinge cancels out.
    const elbowBRad = (180 - elbowDeg) * Math.PI / 180
    const c = Math.cos(elbowBRad)
    const beta = Math.sin(elbowBRad)
    if (Math.abs(beta) < 1e-6) return 0  // degenerate (elbow nearly fully extended)

    const p = elbowHinge.x * upperArmDir.x + elbowHinge.y * upperArmDir.y + elbowHinge.z * upperArmDir.z
    const alpha = p * (1 - c)
    const faParallelComp = c * (1 - p * p) + p * p
    const faPerpX = forearmDir.x - faParallelComp * upperArmDir.x
    const faPerpY = forearmDir.y - faParallelComp * upperArmDir.y
    const faPerpZ = forearmDir.z - faParallelComp * upperArmDir.z
    const uaXfpX = upperArmDir.y * faPerpZ - upperArmDir.z * faPerpY
    const uaXfpY = upperArmDir.z * faPerpX - upperArmDir.x * faPerpZ
    const uaXfpZ = upperArmDir.x * faPerpY - upperArmDir.y * faPerpX
    const thPerpX = alpha * faPerpX - beta * uaXfpX
    const thPerpY = alpha * faPerpY - beta * uaXfpY
    const thPerpZ = alpha * faPerpZ - beta * uaXfpZ
    const thPerpLen = Math.sqrt(thPerpX * thPerpX + thPerpY * thPerpY + thPerpZ * thPerpZ) || 1e-9
    const twistedHingePerp: V3 = { x: thPerpX / thPerpLen, y: thPerpY / thPerpLen, z: thPerpZ / thPerpLen }
    return abSign * signedAngleAround(elbowHinge, twistedHingePerp, upperArmDir)
  }

  const toUnit = (v: V3): V3 => {
    const l = length(v) || 1e-9
    return { x: v.x / l, y: v.y / l, z: v.z / l }
  }
  const rUpperArmUnit = toUnit(rUpperArm)
  const rForearmUnit  = toUnit(rForearm)
  const lUpperArmVec  = sub(lElbow, lSh)
  const lForearmVec   = sub(lWrist, lElbow)
  const lUpperArmUnit = toUnit(lUpperArmVec)
  const lForearmUnit  = toUnit(lForearmVec)

  const rightElbowYawRaw = computeElbowYaw(rUpperArmUnit, rForearmUnit, -1, rightElbowAngleDeg)
  const leftElbowYawRaw  = computeElbowYaw(lUpperArmUnit, lForearmUnit, +1, leftElbowAngleDeg)

  const bodyShoulderAcross: V3 = { x: _acrossX, y: 0, z: _acrossZ }
  const rightWristYawRaw = computeWristYaw(
    rForearmUnit, bodyShoulderAcross, rightWristAngleDeg, rWristToIndex, -1,
  )
  const lWristToIndexVec = sub(lIndex, lWrist)
  const leftWristYawRaw = computeWristYaw(
    lForearmUnit, bodyShoulderAcross, leftWristAngleDeg, lWristToIndexVec, +1,
  )

  const rPinkyVec: V3 = toV3(lms[LM.R_PINKY])
  const lPinkyVec: V3 = toV3(lms[LM.L_PINKY])
  const rightForearmTwistRaw = computeForearmTwist(
    rForearmUnit, bodyShoulderAcross, rightWristYawRaw,
    rIndex, rPinkyVec, -1,
  )
  const leftForearmTwistRaw = computeForearmTwist(
    lForearmUnit, bodyShoulderAcross, leftWristYawRaw,
    lIndex, lPinkyVec, +1,
  )

  // Knee angles.
  const leftKneeAngleDeg  = angleBetween(sub(lHip, lKnee), sub(lAnkle, lKnee))
  const rightKneeAngleDeg = angleBetween(sub(rHip, rKnee), sub(rAnkle, rKnee))

  // Thigh flexion + abduction — decomposed in the BODY FRAME. `forward` and
  // `across` here mirror the FK's body-frame construction from bodyRotationDeg.
  const bodyRad = (bodyRotationDeg * Math.PI) / 180
  const cosB = Math.cos(bodyRad); const sinB = Math.sin(bodyRad)
  // forward = rotY([0,0,-1], bodyRot)
  const forwardX = -sinB, forwardZ = -cosB
  // across = rotY([1,0,0], bodyRot)
  const acrossX = cosB, acrossZ = -sinB

  const decomposeThigh = (thigh: V3) => {
    // Same z-dampening rationale as the arm decomposition above.
    const z = thigh.z * Z_DAMP
    const len = Math.sqrt(thigh.x * thigh.x + thigh.y * thigh.y + z * z) || 1e-9
    const vert = thigh.y / len
    const fwd = (thigh.x * forwardX + z * forwardZ) / len
    const acr = (thigh.x * acrossX + z * acrossZ) / len
    return { vert, fwd, acr }
  }

  const rD = decomposeThigh(sub(rKnee, rHip))
  const lD = decomposeThigh(sub(lKnee, lHip))
  // Forward flexion: how far the thigh tilts toward body-forward vs straight down.
  const rightThighForwardDeg = (Math.atan2(rD.fwd, rD.vert) * 180) / Math.PI
  const leftThighForwardDeg  = (Math.atan2(lD.fwd, lD.vert) * 180) / Math.PI
  // Abduction: must use asin (not atan2), matching FK's rotation-chain math.
  // atan2(acr, vert) over-estimates because vert shrinks as flex grows, but
  // abduction is a pure "across" rotation — determined entirely by the
  // across component of the unit direction. See decomposeArm above.
  const rightThighAbductionDeg = (Math.asin(Math.max(-1, Math.min(1, -rD.acr))) * 180) / Math.PI
  const leftThighAbductionDeg  = (Math.asin(Math.max(-1, Math.min(1,  lD.acr))) * 180) / Math.PI

  // Foot yaws: direction of ankle→foot_tip in the horizontal (XZ) plane,
  // relative to "body forward" (−z when bodyRotation = 0).
  const leftFootDir  = sub(lFootTip, lAnkle)
  const rightFootDir = sub(rFootTip, rAnkle)
  const leftFootYawDeg  = (Math.atan2(leftFootDir.x, -leftFootDir.z)  * 180) / Math.PI
  const rightFootYawDeg = (Math.atan2(rightFootDir.x, -rightFootDir.z) * 180) / Math.PI

  const stanceWidthNorm = length(sub(lAnkle, rAnkle))

  return {
    // The old single-yaw model put the whole-figure yaw into bodyRotationDeg.
    // New model splits this: figureYawDeg yaws everything, bodyRotationDeg is
    // pelvis-vs-leg torsion only. Extractor puts the observed yaw into
    // figureYawDeg so imported poses replay identically; bodyRotationDeg
    // starts at 0 (no observable trunk/leg torsion from a single MediaPipe view).
    figureYawDeg: clamp(bodyRotationDeg, -180, 180),
    bodyRotationDeg: 0,
    // Pelvic roll / torso side-bend / shoulder shrug aren't reliably decomposable
    // from a single-view MediaPipe pose — left at 0. FK uses the angle path
    // unconditionally.
    pelvicRollDeg: 0,
    shoulderRotationDeg: clamp(shoulderRotationDeg, -90, 90),
    torsoTiltDeg: clamp(torsoTiltDeg, 0, 75),
    torsoSideBendDeg: 0,
    shoulderShrugNorm: 0,
    rightShoulderAngleDeg: clamp(rightShoulderAngleDeg, -30, 180),
    rightShoulderAbductionDeg: clamp(rightShoulderAbductionDeg, -30, 180),
    rightElbowAngleDeg: clamp(rightElbowAngleDeg, 30, 180),
    rightWristAngleDeg: clamp(rightWristAngleDeg, 90, 180),
    rightWristYawDeg: clamp(rightWristYawRaw, -30, 20),
    rightForearmTwistDeg: clamp(rightForearmTwistRaw, -90, 90),
    rightElbowYawDeg: clamp(rightElbowYawRaw, -70, 90),
    leftShoulderAngleDeg: clamp(leftShoulderAngleDeg, -30, 180),
    leftShoulderAbductionDeg: clamp(leftShoulderAbductionDeg, -30, 180),
    leftElbowAngleDeg: clamp(leftElbowAngleDeg, 30, 180),
    leftWristAngleDeg: clamp(leftWristAngleDeg, 90, 180),
    leftWristYawDeg: clamp(leftWristYawRaw, -30, 20),
    leftForearmTwistDeg: clamp(leftForearmTwistRaw, -90, 90),
    leftElbowYawDeg:  clamp(leftElbowYawRaw,  -70, 90),
    leftThighForwardDeg: clamp(leftThighForwardDeg, -30, 120),
    rightThighForwardDeg: clamp(rightThighForwardDeg, -30, 120),
    leftThighAbductionDeg: clamp(leftThighAbductionDeg, -30, 80),
    rightThighAbductionDeg: clamp(rightThighAbductionDeg, -30, 80),
    leftKneeAngleDeg: clamp(leftKneeAngleDeg, 30, 180),
    rightKneeAngleDeg: clamp(rightKneeAngleDeg, 30, 180),
    // Old extractor produced the entire ankle-direction yaw in footYawDeg.
    // New model splits this: kneeYawDeg = knee bend plane yaw, footYawDeg =
    // foot vs shin. From a single MediaPipe view we can't see the foot's
    // axial twist, so put all the observed yaw into kneeYawDeg and zero
    // footYawDeg — the rendered foot direction (kneeYaw + footYaw) matches
    // the old behaviour exactly.
    leftKneeYawDeg: clamp(leftFootYawDeg, -90, 90),
    rightKneeYawDeg: clamp(rightFootYawDeg, -90, 90),
    leftFootYawDeg: 0,
    rightFootYawDeg: 0,
    stanceWidthNorm: clamp(stanceWidthNorm, 0.05, 0.7),
    hipMidX: clamp(hipMid.x, 0.2, 0.8),
    hipMidY: clamp(hipMid.y, 0.2, 0.7),
  }
}

export interface PoseFixtureFrame {
  frameIndex: number
  timestampMs: number
  landmarks: Landmark[]
}

export interface PoseFixture {
  intervalMs: number
  totalFrames: number
  frames: PoseFixtureFrame[]
}

/**
 * Normalize a fixture-file JSON object into the shape we consume.
 * Tolerates `landmarks` being either an array of {x,y,z,visibility,presence}
 * objects or an array of [x,y,z,v,p] tuples — same rules as App.tsx.
 */
export function parsePoseFixture(raw: unknown): PoseFixture {
  const r = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
  const intervalMs = Number(r.intervalMs ?? r.interval_ms ?? 100)
  const totalFrames = Number(r.totalFrames ?? r.total_frames ?? 0)
  const rawFrames = Array.isArray(r.frames) ? r.frames : []
  const frames: PoseFixtureFrame[] = rawFrames.map((rf, idx) => {
    const f = (rf && typeof rf === 'object' ? rf : {}) as Record<string, unknown>
    const rawLms = (f.landmarks ?? f.poseLandmarks ?? []) as unknown
    const landmarks = Array.isArray(rawLms) ? rawLms.map((item, i) => parseLm(item, i)) : []
    return {
      frameIndex: Number(f.frameIndex ?? f.frame_index ?? idx),
      timestampMs: Number(f.timestampMs ?? f.timestamp_ms ?? idx * intervalMs),
      landmarks,
    }
  })
  return { intervalMs, totalFrames: totalFrames || frames.length, frames }
}

function parseLm(item: unknown, index: number): Landmark {
  if (Array.isArray(item)) {
    const [x, y, z, v, p] = item
    return {
      index,
      x: Number(x), y: Number(y), z: Number(z),
      visibility: Number(v ?? 1), presence: Number(p ?? 1),
    }
  }
  const o = (item && typeof item === 'object' ? item : {}) as Record<string, unknown>
  return {
    index: Number(o.index ?? index),
    x: Number(o.x ?? 0), y: Number(o.y ?? 0), z: Number(o.z ?? 0),
    visibility: Number(o.visibility ?? 1),
    presence: Number(o.presence ?? 1),
  }
}
