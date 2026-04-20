import type { PoseAnchor } from './PoseAnchor'

/**
 * Default "standing neutral" pose. Used as the initial value when starting
 * a new drill — user slides from this baseline to author START and END.
 */
export const NEUTRAL_POSE: PoseAnchor = {
  bodyRotationDeg: 0,
  torsoTiltDeg: 5,
  rightShoulderAngleDeg: 10,
  rightShoulderAbductionDeg: 10,
  rightElbowAngleDeg: 170,
  rightWristAngleDeg: 180,
  rightForearmTwistDeg: 0,
  leftShoulderAngleDeg: 10,
  leftShoulderAbductionDeg: 10,
  leftElbowAngleDeg: 170,
  leftWristAngleDeg: 180,
  leftForearmTwistDeg: 0,
  leftThighForwardDeg: 0,
  rightThighForwardDeg: 0,
  leftThighAbductionDeg: 0,
  rightThighAbductionDeg: 0,
  leftKneeAngleDeg: 170,
  rightKneeAngleDeg: 170,
  leftFootYawDeg: 0,
  rightFootYawDeg: 0,
  stanceWidthNorm: 0.18,
  hipMidX: 0.5,
  hipMidY: 0.38,
}

export function cloneAnchor(a: PoseAnchor): PoseAnchor {
  return { ...a }
}
