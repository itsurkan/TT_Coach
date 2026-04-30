import type { PoseAnchor } from './PoseAnchor'
import type { Landmark } from '../types'
import { LM } from './SkeletonModel'
import type { BoneLengthsOverride } from './skeletonReconstructor'
import { type V3, toV3, sub, length, toUnit, dampZ, angleBetween, clamp, bodyFrameFromRotDeg, torsoDownFromYawTilt } from './vec3'
import {
  VIS_Z_FALLBACK, propagateZ, decomposeArm,
  computeWristYaw, computeForearmTwist, computeElbowSwivel,
} from './extractArm'
import { computeBodyFrame, computeLegMetrics } from './extractTorsoLegs'

export type { PoseFixture, PoseFixtureFrame } from './parsePoseFixture'
export { parsePoseFixture } from './parsePoseFixture'

export interface ExtractAnchorOptions {
  /** When true, compute bodyRotationDeg as hip-vs-leg torsion instead of leaving it 0. */
  computeBodyRotation?: boolean
  /** When true, stanceWidthNorm uses XY (2D) distance instead of full 3D. */
  stanceWidth2D?: boolean
  /**
   * Override the yaw used for arm/thigh body-frame decompositions (degrees).
   * Pass the post-camera-offset target yaw so extracted angles are consistent
   * with the FK body frame that will be used at display time. When omitted,
   * the auto-detected figureYawDeg is used (original behaviour).
   */
  overrideBodyFrameYaw?: number
}

/**
 * Compute bone-length overrides from raw landmarks so reconstructing with
 * these lengths will match the source pose's scale exactly.
 */
export function extractBoneLengths(lms: Landmark[]): BoneLengthsOverride {
  const lHip = lms[LM.L_HIP], rHip = lms[LM.R_HIP]
  const lSh  = lms[LM.L_SHOULDER], rSh = lms[LM.R_SHOULDER]
  const hipMid = { x: (lHip.x+rHip.x)/2, y: (lHip.y+rHip.y)/2, z: (lHip.z+rHip.z)/2 }
  const shMid  = { x: (lSh.x+rSh.x)/2,  y: (lSh.y+rSh.y)/2,  z: (lSh.z+rSh.z)/2 }
  const dist = (a: Landmark, b: Landmark) => Math.hypot(a.x-b.x, a.y-b.y, a.z-b.z)
  const leftThigh      = dist(lHip, lms[LM.L_KNEE])
  const rightThigh     = dist(rHip, lms[LM.R_KNEE])
  const leftShin       = dist(lms[LM.L_KNEE], lms[LM.L_ANKLE])
  const rightShin      = dist(lms[LM.R_KNEE], lms[LM.R_ANKLE])
  const leftUpperArm   = dist(lSh, lms[LM.L_ELBOW])
  const rightUpperArm  = dist(rSh, lms[LM.R_ELBOW])
  const leftForearm    = dist(lms[LM.L_ELBOW], lms[LM.L_WRIST])
  const rightForearm   = dist(lms[LM.R_ELBOW], lms[LM.R_WRIST])
  const leftFootForward  = dist(lms[LM.L_ANKLE], lms[LM.L_FOOT])
  const rightFootForward = dist(lms[LM.R_ANKLE], lms[LM.R_FOOT])
  return {
    torso: Math.hypot(shMid.x-hipMid.x, shMid.y-hipMid.y, shMid.z-hipMid.z),
    shoulderWidth: dist(lSh, rSh),
    hipWidth: dist(lHip, rHip),
    upperArm: (leftUpperArm  + rightUpperArm)  / 2,
    forearm:  (leftForearm   + rightForearm)   / 2,
    thigh:    (leftThigh     + rightThigh)     / 2,
    shin:     (leftShin      + rightShin)      / 2,
    footForward: (leftFootForward + rightFootForward) / 2,
    headToShoulder: dist(lms[LM.NOSE], {
      ...lSh, x: (lSh.x+rSh.x)/2, y: (lSh.y+rSh.y)/2, z: (lSh.z+rSh.z)/2,
    }),
    leftThigh, rightThigh, leftShin, rightShin,
    leftUpperArm, rightUpperArm, leftForearm, rightForearm,
    leftFootForward, rightFootForward,
  }
}

export function extractAnchorFromLandmarks(lms: Landmark[], opts: ExtractAnchorOptions = {}): PoseAnchor {
  const get = (i: number): V3 => toV3(lms[i])

  const lHip = get(LM.L_HIP);  const rHip = get(LM.R_HIP)
  const lSh  = get(LM.L_SHOULDER); const rSh = get(LM.R_SHOULDER)
  let rElbow = get(LM.R_ELBOW); let rWrist = get(LM.R_WRIST); let rIndex = get(LM.R_INDEX)
  let lElbow = get(LM.L_ELBOW); let lWrist = get(LM.L_WRIST); let lIndex = get(LM.L_INDEX)

  // Visibility-z fallback: low-confidence arm joints inherit z from upstream.
  ;[rElbow, rWrist, rIndex] = propagateZ(rSh.z,
    [[rElbow, LM.R_ELBOW], [rWrist, LM.R_WRIST], [rIndex, LM.R_INDEX]], lms) as [V3, V3, V3]
  ;[lElbow, lWrist, lIndex] = propagateZ(lSh.z,
    [[lElbow, LM.L_ELBOW], [lWrist, LM.L_WRIST], [lIndex, LM.L_INDEX]], lms) as [V3, V3, V3]

  const { bodyRotationDeg, torsoTiltDeg, shoulderRotationDeg, frame, hipMid } =
    computeBodyFrame(lHip, rHip, lSh, rSh, get(LM.NOSE))

  // When a camera yaw offset is active the FK will use a body frame rotated by
  // that offset. Re-derive the decomposition frame at the target yaw so arm/thigh
  // angles are extracted relative to the same frame the FK will use at display
  // time. Without this, a 119° camera offset would produce completely wrong arm
  // positions because the extraction and FK body frames would differ by 119°.
  const decompFrame = opts.overrideBodyFrameYaw !== undefined
    ? bodyFrameFromRotDeg(opts.overrideBodyFrameYaw)
    : frame

  const bodyShoulderAcross: V3 = { x: decompFrame.acrossX, y: 0, z: decompFrame.acrossZ }

  // Torso-down direction for the decomposition frame (accounts for forward tilt).
  // decomposeArm uses this to exactly invert the FK's tilted arm formula instead
  // of approximating with world-down, which causes large flex errors at 45°+ tilt.
  const armDecompYawDeg = opts.overrideBodyFrameYaw !== undefined ? opts.overrideBodyFrameYaw : bodyRotationDeg
  const torsoTiltClamped = clamp(torsoTiltDeg, 0, 75)
  const torsoDown = torsoDownFromYawTilt(armDecompYawDeg, torsoTiltClamped)

  // Arm vectors.
  const rUpperArm    = sub(rElbow, rSh)
  const rForearm     = sub(rWrist, rElbow)
  const rWristToIdx  = sub(rIndex, rWrist)
  const lUpperArmVec = sub(lElbow, lSh)
  const lForearmVec  = sub(lWrist, lElbow)

  const rArmDecomp = decomposeArm(rUpperArm, -1, decompFrame, torsoDown)
  const lArmDecomp = decomposeArm(lUpperArmVec, +1, decompFrame, torsoDown)

  // Elbow interior angles — z dampened to match FK decomposition.
  const rightElbowAngleDeg = angleBetween(dampZ(sub(rSh, rElbow)), dampZ(rForearm))
  const rightWristAngleDeg = angleBetween(sub(rElbow, rWrist), rWristToIdx)
  const leftElbowAngleDeg  = angleBetween(dampZ(sub(lSh, lElbow)), dampZ(sub(lWrist, lElbow)))
  const leftWristAngleDeg  = angleBetween(sub(lElbow, lWrist), sub(lIndex, lWrist))

  // Elbow swivel.
  const rightElbowYawRaw = computeElbowSwivel(
    rSh, rElbow, rWrist, length(rUpperArm), length(rForearm), -1, decompFrame)
  const leftElbowYawRaw = computeElbowSwivel(
    lSh, lElbow, lWrist, length(lUpperArmVec), length(lForearmVec), +1, decompFrame)

  // Wrist yaw + forearm twist — skip when hand chain has low-vis z fallback.
  const rWristVis = lms[LM.R_WRIST]?.visibility ?? 1
  const rIndexVis = lms[LM.R_INDEX]?.visibility ?? 1
  const rPinkyVis = lms[LM.R_PINKY]?.visibility ?? 1
  const lWristVis = lms[LM.L_WRIST]?.visibility ?? 1
  const lIndexVis = lms[LM.L_INDEX]?.visibility ?? 1
  const lPinkyVis = lms[LM.L_PINKY]?.visibility ?? 1
  const rWristYawOk = rWristVis >= VIS_Z_FALLBACK && rIndexVis >= VIS_Z_FALLBACK
  const lWristYawOk = lWristVis >= VIS_Z_FALLBACK && lIndexVis >= VIS_Z_FALLBACK

  const rForearmUnit = toUnit(rForearm)
  const lForearmUnit = toUnit(lForearmVec)

  const rightWristYawRaw = rWristYawOk
    ? computeWristYaw(rForearmUnit, bodyShoulderAcross, rightWristAngleDeg, rWristToIdx, -1) : 0
  const lWristToIndexVec = sub(lIndex, lWrist)
  const leftWristYawRaw = lWristYawOk
    ? computeWristYaw(lForearmUnit, bodyShoulderAcross, leftWristAngleDeg, lWristToIndexVec, +1) : 0

  const rightForearmTwistRaw = (rWristYawOk && rPinkyVis >= VIS_Z_FALLBACK)
    ? computeForearmTwist(rForearmUnit, bodyShoulderAcross, rightWristYawRaw, rIndex, toV3(lms[LM.R_PINKY]), -1) : 0
  const leftForearmTwistRaw = (lWristYawOk && lPinkyVis >= VIS_Z_FALLBACK)
    ? computeForearmTwist(lForearmUnit, bodyShoulderAcross, leftWristYawRaw, lIndex, toV3(lms[LM.L_PINKY]), +1) : 0

  const decompYawDeg = opts.overrideBodyFrameYaw !== undefined ? opts.overrideBodyFrameYaw : bodyRotationDeg
  const legs = computeLegMetrics(
    lHip, rHip,
    get(LM.L_KNEE), get(LM.R_KNEE),
    get(LM.L_ANKLE), get(LM.R_ANKLE),
    get(LM.L_FOOT), get(LM.R_FOOT),
    decompYawDeg, opts.stanceWidth2D, opts.computeBodyRotation,
  )

  return {
    figureYawDeg:          clamp(bodyRotationDeg, -180, 180),
    bodyRotationDeg:       clamp(legs.pelvisTorsionDeg, -90, 90),
    pelvicRollDeg:         0,
    shoulderRotationDeg:   clamp(shoulderRotationDeg, -90, 90),
    torsoTiltDeg:          clamp(torsoTiltDeg, 0, 75),
    torsoSideBendDeg:      0,
    shoulderShrugNorm:     0,
    rightShoulderAngleDeg:     clamp(rArmDecomp.flex,   -30, 180),
    rightShoulderAbductionDeg: clamp(rArmDecomp.abduct, -30, 180),
    rightElbowAngleDeg:   clamp(rightElbowAngleDeg,   30, 180),
    rightWristAngleDeg:   clamp(rightWristAngleDeg,   90, 180),
    rightWristYawDeg:     clamp(rightWristYawRaw,    -30, 20),
    rightForearmTwistDeg: clamp(rightForearmTwistRaw, -90, 90),
    rightElbowYawDeg:     clamp(rightElbowYawRaw,    -90, 90),
    leftShoulderAngleDeg:     clamp(lArmDecomp.flex,   -30, 180),
    leftShoulderAbductionDeg: clamp(lArmDecomp.abduct, -30, 180),
    leftElbowAngleDeg:   clamp(leftElbowAngleDeg,   30, 180),
    leftWristAngleDeg:   clamp(leftWristAngleDeg,   90, 180),
    leftWristYawDeg:     clamp(leftWristYawRaw,    -30, 20),
    leftForearmTwistDeg: clamp(leftForearmTwistRaw, -90, 90),
    leftElbowYawDeg:     clamp(leftElbowYawRaw,    -90, 90),
    leftThighForwardDeg:      clamp(legs.leftThighForwardDeg,      -30, 120),
    rightThighForwardDeg:     clamp(legs.rightThighForwardDeg,     -30, 120),
    leftThighAbductionDeg:    clamp(legs.leftThighAbductionDeg,    -30, 80),
    rightThighAbductionDeg:   clamp(legs.rightThighAbductionDeg,   -30, 80),
    leftKneeAngleDeg:  clamp(legs.leftKneeAngleDeg,  30, 180),
    rightKneeAngleDeg: clamp(legs.rightKneeAngleDeg, 30, 180),
    // Full ankle-direction yaw → kneeYawDeg; footYawDeg = 0 (split model).
    leftKneeYawDeg:    clamp(legs.leftFootYawDeg,   -90, 90),
    rightKneeYawDeg:   clamp(legs.rightFootYawDeg,  -90, 90),
    leftKneeSwivelDeg:  0,
    rightKneeSwivelDeg: 0,
    leftFootYawDeg:  0,
    rightFootYawDeg: 0,
    stanceWidthNorm: clamp(legs.stanceWidthNorm, 0.05, 0.7),
    hipMidX: clamp(hipMid.x, 0.2, 0.8),
    hipMidY: clamp(hipMid.y, 0.2, 0.7),
  }
}
