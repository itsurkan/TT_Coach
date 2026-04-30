import type { Landmark } from '../types'
import { type V3, type BodyFrame, length, toUnit, dampZ, signedAngleAround, rotAroundAxis } from './vec3'

// Sentinel: world-down direction used when no torso tilt is provided.
const WORLD_DOWN: V3 = { x: 0, y: 1, z: 0 }

export const VIS_Z_FALLBACK = 0.85

/**
 * For each joint below VIS_Z_FALLBACK visibility, inherit z from the closest
 * upstream high-visibility joint. Collapses low-confidence depth toward the
 * image plane rather than projecting phantom depth.
 */
export function propagateZ(
  baseZ: number,
  joints: Array<[V3, number]>,
  lms: Landmark[],
): V3[] {
  let goodZ = baseZ
  return joints.map(([p, idx]) => {
    const v = lms[idx]?.visibility ?? 1
    if (v >= VIS_Z_FALLBACK) { goodZ = p.z; return p }
    return { x: p.x, y: p.y, z: goodZ }
  })
}

/**
 * Decompose an upper-arm (shoulder→elbow) vector into forward flexion and
 * sideways abduction, matching the FK's arm formula exactly.
 *
 * When `torsoDown` is provided (the tilted torso direction from the FK),
 * inverts  arm ∝ dDown·T + dForward·F + dAcross·A  exactly via a 3×3 linear
 * solve. Since F.y = A.y = 0 (horizontal axes), row-2 gives dDown = Vy/T.y
 * immediately; the remaining 2×2 system has det = Fx·Az − Ax·Fz = 1 always
 * (sin²θ + cos²θ), so the inversion is closed-form and numerically stable.
 *
 * Without torsoDown (or at zero tilt) falls back to the original world-down
 * approximation, giving identical results to the previous implementation.
 */
export function decomposeArm(
  arm: V3,
  abSignForAbduction: number,
  frame: BodyFrame,
  torsoDown: V3 = WORLD_DOWN,
): { flex: number; abduct: number } {
  const zd = dampZ(arm)
  const L = length(zd) || 1e-9
  const Vx = zd.x / L, Vy = zd.y / L, Vz = zd.z / L

  let dDown: number, dForward: number, dAcross: number

  if (torsoDown.y > 1e-4) {
    // Exact FK inversion via 3×3 linear solve (see JSDoc above).
    dDown    = Vy / torsoDown.y
    const r1 = Vx - torsoDown.x * dDown
    const r2 = Vz - torsoDown.z * dDown
    // det of 2×2 [F_xz | A_xz] = Fx·Az − Ax·Fz = 1 always
    dForward = r1 * frame.acrossZ  - r2 * frame.acrossX
    dAcross  = frame.forwardX * r2 - frame.forwardZ * r1
  } else {
    // World-down fallback (correct at tilt = 0°)
    dDown    = Vy
    dForward = Vx * frame.forwardX + Vz * frame.forwardZ
    dAcross  = Vx * frame.acrossX  + Vz * frame.acrossZ
  }

  const dLen = length({ x: dDown, y: dForward, z: dAcross }) || 1e-9
  const acrClamped = Math.max(-1, Math.min(1, abSignForAbduction * dAcross / dLen))
  return {
    abduct: (Math.asin(acrClamped) * 180) / Math.PI,
    flex:   (Math.atan2(dForward, dDown) * 180) / Math.PI,
  }
}

/**
 * Extract wrist yaw (ulnar/radial deviation) by inverting the FK hand-fan
 * rotation. The signed angle between the reference direction at yaw=0 and
 * the observed wrist→index direction IS the yaw.
 */
export function computeWristYaw(
  forearmUnit: V3,
  shoulderAcross: V3,
  wristAngleDeg: number,
  wristToIndex: V3,
  abSign: number,
): number {
  if (length(wristToIndex) < 1e-4) return 0
  const wristBend = 180 - wristAngleDeg
  const bentHandDir = rotAroundAxis(forearmUnit, shoulderAcross, -wristBend)
  const fanSideBase = rotAroundAxis(shoulderAcross, forearmUnit, 0)
  const handNormal: V3 = {
    x: forearmUnit.y * fanSideBase.z - forearmUnit.z * fanSideBase.y,
    y: forearmUnit.z * fanSideBase.x - forearmUnit.x * fanSideBase.z,
    z: forearmUnit.x * fanSideBase.y - forearmUnit.y * fanSideBase.x,
  }
  if (length(handNormal) < 1e-6) return 0
  const handNormalU = toUnit(handNormal)
  const ref: V3 = {
    x: bentHandDir.x + 0.2 * fanSideBase.x,
    y: bentHandDir.y + 0.2 * fanSideBase.y,
    z: bentHandDir.z + 0.2 * fanSideBase.z,
  }
  return abSign * signedAngleAround(toUnit(ref), toUnit(wristToIndex), handNormalU)
}

/**
 * Extract forearm twist (pronation/supination) by inverting the FK fan rotation.
 * handNormal is approximated as cross(forearmDir, shoulderAcross) — matches FK
 * exactly at twist=0, drifts slightly for larger twist, within slider tolerance.
 */
export function computeForearmTwist(
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
  if (length(fanSideObs) < 1e-4) return 0
  const fanSideObsU = toUnit(fanSideObs)
  const handNormal: V3 = {
    x: forearmUnit.y * shoulderAcross.z - forearmUnit.z * shoulderAcross.y,
    y: forearmUnit.z * shoulderAcross.x - forearmUnit.x * shoulderAcross.z,
    z: forearmUnit.x * shoulderAcross.y - forearmUnit.y * shoulderAcross.x,
  }
  const hnLen = length(handNormal) || 1e-9
  const handNormalU: V3 = { x: handNormal.x/hnLen, y: handNormal.y/hnLen, z: handNormal.z/hnLen }
  const fanSideBase = wristYawDeg !== 0
    ? rotAroundAxis(fanSideObsU, handNormalU, -abSign * wristYawDeg)
    : fanSideObsU
  return signedAngleAround(shoulderAcross, fanSideBase, forearmUnit)
}

/**
 * Elbow swivel extraction (inverse of FK's wrist-pinned swivel geometry):
 * parameterises the elbow on the circle around shoulder→wrist axis, measures
 * the signed angle via atan2. Pure 2D geometry — no Rodrigues algebra.
 */
export function computeElbowSwivel(
  shoulderPt: V3,
  elbowPt: V3,
  wristPt: V3,
  L_upper: number,
  L_forearm: number,
  abSign: number,
  frame: BodyFrame,
): number {
  const axisVec: V3 = {
    x: wristPt.x - shoulderPt.x,
    y: wristPt.y - shoulderPt.y,
    z: wristPt.z - shoulderPt.z,
  }
  const d = length(axisVec)
  if (d > L_upper + L_forearm - 0.01 || d < 1e-6) return 0
  const axis = toUnit(axisVec)
  const t = (d*d + L_upper*L_upper - L_forearm*L_forearm) / (2*d*d)
  const center: V3 = {
    x: shoulderPt.x + t * axisVec.x,
    y: shoulderPt.y + t * axisVec.y,
    z: shoulderPt.z + t * axisVec.z,
  }
  const torsoUp: V3 = { x: 0, y: -1, z: 0 }
  const tuDot = torsoUp.x*axis.x + torsoUp.y*axis.y + torsoUp.z*axis.z
  const upProj: V3 = {
    x: torsoUp.x - tuDot * axis.x,
    y: torsoUp.y - tuDot * axis.y,
    z: torsoUp.z - tuDot * axis.z,
  }
  let u: V3
  if (length(upProj) > 1e-4) {
    const ul = length(upProj)
    u = { x: -upProj.x / ul, y: -upProj.y / ul, z: -upProj.z / ul }
  } else {
    const sf: V3 = { x: frame.forwardX, y: 0, z: frame.forwardZ }
    const sfDot = sf.x*axis.x + sf.y*axis.y + sf.z*axis.z
    const sfProj: V3 = { x: sf.x - sfDot*axis.x, y: sf.y - sfDot*axis.y, z: sf.z - sfDot*axis.z }
    u = toUnit(sfProj)
  }
  const vRaw: V3 = {
    x: axis.y * u.z - axis.z * u.y,
    y: axis.z * u.x - axis.x * u.z,
    z: axis.x * u.y - axis.y * u.x,
  }
  const vl = length(vRaw) || 1e-9
  const v: V3 = { x: abSign * vRaw.x / vl, y: abSign * vRaw.y / vl, z: abSign * vRaw.z / vl }
  const ec: V3 = { x: elbowPt.x - center.x, y: elbowPt.y - center.y, z: elbowPt.z - center.z }
  return Math.atan2(ec.x*v.x + ec.y*v.y + ec.z*v.z, ec.x*u.x + ec.y*u.y + ec.z*u.z) * 180 / Math.PI
}

/**
 * Extract humeral twist via Rodrigues twistedHinge recovery. Currently unused
 * (replaced by computeElbowSwivel) — kept for audit history.
 * See anchorExtractor.ts commit history for the derivation notes.
 */
export function computeElbowYaw(
  upperArmDir: V3,
  forearmDir: V3,
  abSign: number,
  elbowDeg: number,
  frame: BodyFrame,
): number {
  if (elbowDeg >= 175) return 0
  const shoulderAcross: V3 = { x: frame.acrossX,  y: 0, z: frame.acrossZ  }
  const shoulderForward: V3 = { x: frame.forwardX, y: 0, z: frame.forwardZ }
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
  const elbowHinge = toUnit(hingeRaw)
  const elbowBRad = (180 - elbowDeg) * Math.PI / 180
  const c = Math.cos(elbowBRad)
  const beta = Math.sin(elbowBRad)
  if (Math.abs(beta) < 1e-6) return 0
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
  const thPerpLen = Math.sqrt(thPerpX*thPerpX + thPerpY*thPerpY + thPerpZ*thPerpZ) || 1e-9
  const twistedHingePerp: V3 = { x: thPerpX/thPerpLen, y: thPerpY/thPerpLen, z: thPerpZ/thPerpLen }
  return abSign * signedAngleAround(elbowHinge, twistedHingePerp, upperArmDir)
}
