import { type V3, type BodyFrame, sub, mid, length, angleBetween, bodyFrameFromRotDeg } from './vec3'

export interface BodyFrameResult {
  bodyRotationDeg: number
  torsoTiltDeg: number
  shoulderRotationDeg: number
  frame: BodyFrame
  hipMid: V3
  shMid: V3
}

/**
 * Compute whole-body yaw, torso tilt, and shoulder X-factor from the four
 * torso corners. Yaw is the weighted average of hip-axis and shoulder-axis
 * yaws (weighted by 2D magnitude to down-weight collapsed axes). Facing-away
 * detection flips the sign by 180° when the back is toward camera.
 */
export function computeBodyFrame(
  lHip: V3, rHip: V3,
  lSh: V3, rSh: V3,
  noseLm: V3,
): BodyFrameResult {
  const hipMid = mid(lHip, rHip)
  const shMid  = mid(lSh, rSh)

  const hipAxis = sub(lHip, rHip)
  const shAxis  = sub(lSh, rSh)
  const hipMag2D = Math.hypot(hipAxis.x, hipAxis.z)
  const shMag2D  = Math.hypot(shAxis.x, shAxis.z)
  const totalMag = hipMag2D + shMag2D || 1
  const hipYaw = Math.atan2(-hipAxis.z, hipAxis.x)
  const shYaw  = Math.atan2(-shAxis.z,  shAxis.x)
  let bodyRotationDeg = ((hipYaw * hipMag2D + shYaw * shMag2D) / totalMag) * 180 / Math.PI

  // Facing-away detection: cross(hipUp, hipAxis) points out the back.
  // cross.z > 0 → back at +z → facing camera (no flip).
  // cross.z < 0 → back at −z → facing away → flip 180°.
  const hipUp = sub(shMid, hipMid)
  const backwardNormal: V3 = {
    x: hipUp.y * hipAxis.z - hipUp.z * hipAxis.y,
    y: hipUp.z * hipAxis.x - hipUp.x * hipAxis.z,
    z: hipUp.x * hipAxis.y - hipUp.y * hipAxis.x,
  }
  const bnMag = Math.hypot(backwardNormal.x, backwardNormal.z)
  const facingAway = bnMag > 0.02 ? backwardNormal.z < 0 : noseLm.z > hipMid.z
  if (facingAway) {
    bodyRotationDeg = bodyRotationDeg > 0 ? bodyRotationDeg - 180 : bodyRotationDeg + 180
  }

  // Torso tilt: angle between torso vector and world up (-y). z dampened to
  // half weight — reduces over-estimation from MediaPipe z noise on forward bend.
  // Always non-negative: the slider range is [0,75°] (no backward lean), so the
  // previous z-sign flip just created a noise-driven step from 0° → ~50° when
  // shoulder/hip z crossed each other in MediaPipe noise on lateral-facing poses.
  const torso = sub(shMid, hipMid)
  const torsoDamped: V3 = { x: torso.x, y: torso.y, z: torso.z * 0.5 }
  const torsoTiltDeg = angleBetween(torsoDamped, { x: 0, y: -1, z: 0 })

  const shoulderRotationDeg = ((shYaw - hipYaw) * 180) / Math.PI

  return {
    bodyRotationDeg,
    torsoTiltDeg,
    shoulderRotationDeg,
    frame: bodyFrameFromRotDeg(bodyRotationDeg),
    hipMid,
    shMid,
  }
}

export interface LegMetrics {
  leftThighForwardDeg: number
  rightThighForwardDeg: number
  leftThighAbductionDeg: number
  rightThighAbductionDeg: number
  leftKneeAngleDeg: number
  rightKneeAngleDeg: number
  leftFootYawDeg: number
  rightFootYawDeg: number
  stanceWidthNorm: number
  pelvisTorsionDeg: number
}

/**
 * Decompose leg chain into FK-compatible angles. Thigh abduction uses asin
 * (not atan2) to match FK's rotation-chain math. z dampened 50% throughout.
 */
export function computeLegMetrics(
  lHip: V3, rHip: V3,
  lKnee: V3, rKnee: V3,
  lAnkle: V3, rAnkle: V3,
  lFootTip: V3, rFootTip: V3,
  bodyRotationDeg: number,
  stanceWidth2D = false,
  computePelvisTorsion = false,
): LegMetrics {
  const bodyRad = (bodyRotationDeg * Math.PI) / 180
  const cosB = Math.cos(bodyRad), sinB = Math.sin(bodyRad)
  const forwardX = -sinB, forwardZ = -cosB
  const acrossX  =  cosB, acrossZ  = -sinB

  const decomposeThigh = (thigh: V3) => {
    const z = thigh.z * 0.5
    const len = Math.sqrt(thigh.x*thigh.x + thigh.y*thigh.y + z*z) || 1e-9
    return {
      vert: thigh.y / len,
      fwd:  (thigh.x * forwardX + z * forwardZ) / len,
      acr:  (thigh.x * acrossX  + z * acrossZ)  / len,
    }
  }

  const rD = decomposeThigh(sub(rKnee, rHip))
  const lD = decomposeThigh(sub(lKnee, lHip))

  const leftFootDir  = sub(lFootTip, lAnkle)
  const rightFootDir = sub(rFootTip, rAnkle)
  const ankleVec     = sub(lAnkle, rAnkle)
  const hipVec       = sub(lHip,   rHip)

  return {
    leftThighForwardDeg:   (Math.atan2(lD.fwd, lD.vert) * 180) / Math.PI,
    rightThighForwardDeg:  (Math.atan2(rD.fwd, rD.vert) * 180) / Math.PI,
    leftThighAbductionDeg:  (Math.asin(Math.max(-1, Math.min(1,  lD.acr))) * 180) / Math.PI,
    rightThighAbductionDeg: (Math.asin(Math.max(-1, Math.min(1, -rD.acr))) * 180) / Math.PI,
    leftKneeAngleDeg:  angleBetween(sub(lHip, lKnee), sub(lAnkle, lKnee)),
    rightKneeAngleDeg: angleBetween(sub(rHip, rKnee), sub(rAnkle, rKnee)),
    leftFootYawDeg:  (Math.atan2(leftFootDir.x,  -leftFootDir.z)  * 180) / Math.PI,
    rightFootYawDeg: (Math.atan2(rightFootDir.x, -rightFootDir.z) * 180) / Math.PI,
    stanceWidthNorm: stanceWidth2D ? Math.hypot(ankleVec.x, ankleVec.y) : length(ankleVec),
    pelvisTorsionDeg: computePelvisTorsion
      ? (Math.atan2(-hipVec.z, hipVec.x) - Math.atan2(-ankleVec.z, ankleVec.x)) * 180 / Math.PI
      : 0,
  }
}
