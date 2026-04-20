import type { PoseAnchor } from './PoseAnchor'

/** Linear interpolation between two anchors. t ∈ [0, 1]. */
export function lerpAnchor(a: PoseAnchor, b: PoseAnchor, t: number): PoseAnchor {
  const s = 1 - t
  return {
    bodyRotationDeg:        a.bodyRotationDeg        * s + b.bodyRotationDeg        * t,
    torsoTiltDeg:           a.torsoTiltDeg           * s + b.torsoTiltDeg           * t,
    rightShoulderAngleDeg:     a.rightShoulderAngleDeg     * s + b.rightShoulderAngleDeg     * t,
    rightShoulderAbductionDeg: a.rightShoulderAbductionDeg * s + b.rightShoulderAbductionDeg * t,
    rightElbowAngleDeg:        a.rightElbowAngleDeg        * s + b.rightElbowAngleDeg        * t,
    rightWristAngleDeg:        a.rightWristAngleDeg        * s + b.rightWristAngleDeg        * t,
    rightForearmTwistDeg:      a.rightForearmTwistDeg      * s + b.rightForearmTwistDeg      * t,
    leftShoulderAngleDeg:      a.leftShoulderAngleDeg      * s + b.leftShoulderAngleDeg      * t,
    leftShoulderAbductionDeg:  a.leftShoulderAbductionDeg  * s + b.leftShoulderAbductionDeg  * t,
    leftElbowAngleDeg:         a.leftElbowAngleDeg         * s + b.leftElbowAngleDeg         * t,
    leftWristAngleDeg:         a.leftWristAngleDeg         * s + b.leftWristAngleDeg         * t,
    leftForearmTwistDeg:       a.leftForearmTwistDeg       * s + b.leftForearmTwistDeg       * t,
    leftThighForwardDeg:    a.leftThighForwardDeg    * s + b.leftThighForwardDeg    * t,
    rightThighForwardDeg:   a.rightThighForwardDeg   * s + b.rightThighForwardDeg   * t,
    leftThighAbductionDeg:  a.leftThighAbductionDeg  * s + b.leftThighAbductionDeg  * t,
    rightThighAbductionDeg: a.rightThighAbductionDeg * s + b.rightThighAbductionDeg * t,
    leftKneeAngleDeg:       a.leftKneeAngleDeg       * s + b.leftKneeAngleDeg       * t,
    rightKneeAngleDeg:      a.rightKneeAngleDeg      * s + b.rightKneeAngleDeg      * t,
    leftFootYawDeg:         a.leftFootYawDeg         * s + b.leftFootYawDeg         * t,
    rightFootYawDeg:        a.rightFootYawDeg        * s + b.rightFootYawDeg        * t,
    stanceWidthNorm:        a.stanceWidthNorm        * s + b.stanceWidthNorm        * t,
    hipMidX:                a.hipMidX                * s + b.hipMidX                * t,
    hipMidY:                a.hipMidY                * s + b.hipMidY                * t,
  }
}

/**
 * Build a sequence of `count` anchors linearly interpolated from start to end,
 * endpoints inclusive.
 */
export function interpolateAnchors(
  start: PoseAnchor,
  end: PoseAnchor,
  count: number
): PoseAnchor[] {
  if (count <= 1) return [start]
  const out: PoseAnchor[] = []
  for (let i = 0; i < count; i++) {
    const t = i / (count - 1)
    out.push(lerpAnchor(start, end, t))
  }
  return out
}
