/**
 * Pose anchor — a single keyframe in a drill. A drill is defined by two of
 * these (START + END); everything in between is linearly interpolated and
 * reconstructed by the FK builder.
 *
 * All angles in degrees. Positions in normalized [0, 1] screen coordinates.
 */
export interface PoseAnchor {
  /** Hip-line yaw (around vertical axis). Positive = right shoulder back. */
  bodyRotationDeg: number
  /** Forward bend at the hips. Whole torso rotates rigidly around the hip line. */
  torsoTiltDeg: number
  /** Corpus rotation: extra yaw applied to the SHOULDER line on top of
   *  bodyRotationDeg (hips stay where bodyRotationDeg put them). Models the
   *  shoulder-hip separation ("X-factor") used when coiling the trunk for a
   *  stroke. 0 = shoulders aligned with hips; positive = shoulders rotate
   *  further back to the player's right; negative = further left. Arms
   *  follow the shoulder frame, so rotating shoulders sweeps both arms. */
  shoulderRotationDeg: number
  rightShoulderAngleDeg: number     // forward flexion (0 = arm down, 90 = arm forward, 180 = arm up)
  rightShoulderAbductionDeg: number // sideways (0 = arm along torso, 90 = horizontal out to side)
  rightElbowAngleDeg: number
  rightWristAngleDeg: number
  rightForearmTwistDeg: number
  leftShoulderAngleDeg: number
  leftShoulderAbductionDeg: number
  leftElbowAngleDeg: number
  leftWristAngleDeg: number
  leftForearmTwistDeg: number
  // Hip joint angles (each thigh independently) — enables proper squat / lunge poses.
  // Without these, heavy knee flexion folds the shin backward unnaturally.
  leftThighForwardDeg: number   // hip flexion, positive = knee forward
  rightThighForwardDeg: number
  leftThighAbductionDeg: number // hip abduction, positive = leg out to player's left
  rightThighAbductionDeg: number// for right leg: positive = out to player's right
  leftKneeAngleDeg: number
  rightKneeAngleDeg: number
  leftFootYawDeg: number
  rightFootYawDeg: number
  stanceWidthNorm: number
  hipMidX: number
  hipMidY: number
  /**
   * Optional unit-vector overrides for each limb bone — when present, FK
   * uses these directly instead of deriving from the angle fields above.
   * Set by the extractor during import from raw landmarks so faithful
   * replay doesn't depend on perfect angle-decomposition math.
   * Angle fields are still kept in sync for slider UI.
   */
  dirOverrides?: LimbDirections
}

export interface LimbDirections {
  leftThigh?: [number, number, number]
  rightThigh?: [number, number, number]
  leftShin?: [number, number, number]
  rightShin?: [number, number, number]
  leftUpperArm?: [number, number, number]
  rightUpperArm?: [number, number, number]
  leftForearm?: [number, number, number]
  rightForearm?: [number, number, number]
  torsoUp?: [number, number, number]
  leftFoot?: [number, number, number]
  rightFoot?: [number, number, number]
}

export type AnchorPhase = 'START' | 'END'

export interface StrokeAnchorSet {
  start: PoseAnchor
  end: PoseAnchor
}

/** Slider spec for the editor UI. */
export interface AnchorParamSpec {
  key: keyof PoseAnchor
  label: string
  min: number
  max: number
  step: number
}

export interface AnchorParamGroup {
  name: string
  params: AnchorParamSpec[]
}

export const ANCHOR_PARAM_GROUPS: AnchorParamGroup[] = [
  {
    name: 'Torso',
    params: [
      { key: 'bodyRotationDeg',     label: 'Hip rotation',       min: -90, max: 90, step: 1 },
      { key: 'shoulderRotationDeg', label: 'Corpus rotation',    min: -90, max: 90, step: 1 },
      { key: 'torsoTiltDeg',        label: 'Torso tilt forward', min: 0,   max: 75, step: 1 },
    ],
  },
  {
    name: 'Right arm (stroking)',
    params: [
      { key: 'rightShoulderAngleDeg',     label: 'Shoulder fwd',   min: -30, max: 180, step: 1 },
      { key: 'rightShoulderAbductionDeg', label: 'Shoulder side',  min: 0,   max: 180, step: 1 },
      { key: 'rightElbowAngleDeg',        label: 'Elbow',          min: 30,  max: 180, step: 1 },
      { key: 'rightWristAngleDeg',        label: 'Wrist',          min: 90,  max: 180, step: 1 },
      { key: 'rightForearmTwistDeg',      label: 'Forearm twist',  min: -90, max: 90,  step: 1 },
    ],
  },
  {
    name: 'Left arm',
    params: [
      { key: 'leftShoulderAngleDeg',     label: 'Shoulder fwd',   min: -30, max: 180, step: 1 },
      { key: 'leftShoulderAbductionDeg', label: 'Shoulder side',  min: 0,   max: 180, step: 1 },
      { key: 'leftElbowAngleDeg',        label: 'Elbow',          min: 30,  max: 180, step: 1 },
      { key: 'leftWristAngleDeg',        label: 'Wrist',          min: 90,  max: 180, step: 1 },
      { key: 'leftForearmTwistDeg',      label: 'Forearm twist',  min: -90, max: 90,  step: 1 },
    ],
  },
  {
    name: 'Legs',
    params: [
      { key: 'leftThighForwardDeg',    label: 'L thigh forward',    min: -30, max: 120, step: 1 },
      { key: 'rightThighForwardDeg',   label: 'R thigh forward',    min: -30, max: 120, step: 1 },
      { key: 'leftThighAbductionDeg',  label: 'L thigh abduction',  min: -30, max: 80,  step: 1 },
      { key: 'rightThighAbductionDeg', label: 'R thigh abduction',  min: -30, max: 80,  step: 1 },
      { key: 'leftKneeAngleDeg',       label: 'L knee bend (180=straight, 30=deep)',   min: 30,  max: 180, step: 1 },
      { key: 'rightKneeAngleDeg',      label: 'R knee bend (180=straight, 30=deep)',   min: 30,  max: 180, step: 1 },
      { key: 'leftFootYawDeg',         label: 'L foot yaw',         min: -60, max: 60,  step: 1 },
      { key: 'rightFootYawDeg',        label: 'R foot yaw',         min: -60, max: 60,  step: 1 },
      { key: 'stanceWidthNorm',        label: 'Stance width',       min: 0.10, max: 0.70, step: 0.01 },
    ],
  },
  {
    name: 'Position (root)',
    params: [
      { key: 'hipMidX', label: 'Hip X', min: 0.30, max: 0.70, step: 0.005 },
      { key: 'hipMidY', label: 'Hip Y', min: 0.25, max: 0.55, step: 0.005 },
    ],
  },
]

/** Flattened list of all param specs (preserves group ordering). */
export const ANCHOR_PARAM_SPECS: AnchorParamSpec[] =
  ANCHOR_PARAM_GROUPS.flatMap(g => g.params)
