/**
 * Pose anchor — a single keyframe in a drill. A drill is defined by two of
 * these (START + END); everything in between is linearly interpolated and
 * reconstructed by the FK builder.
 *
 * All angles in degrees. Positions in normalized [0, 1] screen coordinates.
 */
export interface PoseAnchor {
  /** Yaw of the entire figure around the vertical axis through hipMid.
   *  Rotates legs, hips, torso, arms and head together — use this when you
   *  want to face the figure differently relative to the camera. Positive
   *  rotates toward +z (player's right swings back). Default 0 = facing
   *  the camera straight on. */
  figureYawDeg: number
  /** Hip (pelvis) twist RELATIVE to the planted legs. Rotates the hip line,
   *  torso, arms and head — but legs (thighs / shins / feet) stay where
   *  figureYawDeg put them. Use this for the trunk-vs-legs torsion that
   *  loads a stroke. Positive = right shoulder back. */
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
  /** Lateral pelvic tilt (roll around the body-forward axis). Positive =
   *  player's right hip rises (weight shift onto left leg). Rotates the
   *  hip-across vector but does NOT propagate into torsoUp — the torso is
   *  side-bent independently via torsoSideBendDeg. */
  pelvicRollDeg: number
  /** Lateral torso bend (roll of torsoUp around the body-forward axis).
   *  Positive = upper body leans to the player's right, so left shoulder
   *  rises and right shoulder drops. Applied AFTER torsoTiltDeg so the
   *  forward lean and side-bend compose cleanly. */
  torsoSideBendDeg: number
  /** Shoulder shrug: extra offset applied to the shoulder midpoint along
   *  torsoUp, in H-normalized units. Positive raises both shoulders toward
   *  the ears; negative depresses them. Separate from torsoTilt so you can
   *  hunch without tilting the spine. */
  shoulderShrugNorm: number
  rightShoulderAngleDeg: number     // forward flexion (0 = arm down, 90 = arm forward, 180 = arm up)
  rightShoulderAbductionDeg: number // sideways (0 = arm along torso, 90 = horizontal out to side)
  rightElbowAngleDeg: number
  rightWristAngleDeg: number
  /** Wrist ulnar/radial deviation — lateral deflection of the hand at the
   *  wrist, independent of palmar flex (wristAngleDeg). Positive = radial
   *  deviation (hand toward thumb side); negative = ulnar (toward pinky).
   *  Sign mirrored for the left arm. */
  rightWristYawDeg: number
  rightForearmTwistDeg: number
  /** Humeral twist / shoulder internal–external rotation. Rotates the elbow
   *  on a circle around the shoulder→wrist axis WITHOUT moving the shoulder
   *  or wrist. Positive = external rotation (for a right-hander's forehand
   *  cocking motion). Mirrored sign convention for the left arm. */
  rightElbowYawDeg: number
  leftShoulderAngleDeg: number
  leftShoulderAbductionDeg: number
  leftElbowAngleDeg: number
  leftWristAngleDeg: number
  leftWristYawDeg: number
  leftForearmTwistDeg: number
  leftElbowYawDeg: number
  // Hip joint angles (each thigh independently) — enables proper squat / lunge poses.
  // Without these, heavy knee flexion folds the shin backward unnaturally.
  leftThighForwardDeg: number   // hip flexion, positive = knee forward
  rightThighForwardDeg: number
  leftThighAbductionDeg: number // hip abduction, positive = leg out to player's left
  rightThighAbductionDeg: number// for right leg: positive = out to player's right
  leftKneeAngleDeg: number
  rightKneeAngleDeg: number
  /**
   * Yaw of the knee bend plane around the vertical axis through the hip
   * socket — i.e. the direction the knee points when bent. Also yaws the
   * thigh's projection on the ground. Independent of `footYawDeg`, which
   * rotates the foot relative to the shin. The world-yaw of the foot is
   * the SUM of `kneeYawDeg + footYawDeg`. Positive = knee swings to the
   * player's right (right leg) / left (left leg, sign flips internally).
   */
  leftKneeYawDeg: number
  rightKneeYawDeg: number
  /**
   * Knee swivel — the leg analog of arm `*ElbowYawDeg` (elbow swivel).
   * Hip and ankle are pinned; the knee orbits on a circle around the
   * hip→ankle axis. Swivel=0 places the knee in the plane containing the
   * swivel=0 FK result (i.e. today's kneeYaw-free leg), so the DOF is
   * additive: byte-identical at 0, orbits the knee at ≠0. Positive =
   * lateral (knee swings outward, away from the midline); negative =
   * medial (knock-kneed). Sign is mirrored internally between sides so
   * the same sign convention feels symmetric.
   */
  leftKneeSwivelDeg: number
  rightKneeSwivelDeg: number
  leftFootYawDeg: number
  rightFootYawDeg: number
  stanceWidthNorm: number
  hipMidX: number
  hipMidY: number
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
  /** Reset/MIDPOINT_POSE target. When omitted, MIDPOINT_POSE uses (min+max)/2.
   *  Use this to decouple "slider reach" from "default pose" — e.g. shoulder
   *  abduction goes up to 120° but the ready-position default sits at 31°.
   *  Value is snapped to `step` before use. */
  defaultValue?: number
}

export interface AnchorParamGroup {
  name: string
  params: AnchorParamSpec[]
}

export const ANCHOR_PARAM_GROUPS: AnchorParamGroup[] = [
  {
    name: 'Torso',
    params: [
      { key: 'figureYawDeg',        label: 'Figure yaw (whole body)', min: -180, max: 180, step: 1 },
      { key: 'bodyRotationDeg',     label: 'Hip rotation (pelvis twist)', min: -90, max: 90, step: 1 },
      { key: 'pelvicRollDeg',       label: 'Pelvic roll',        min: -30, max: 30, step: 1 },
      { key: 'shoulderRotationDeg', label: 'Corpus rotation',    min: -90, max: 90, step: 1 },
      { key: 'torsoTiltDeg',        label: 'Torso tilt forward', min: 0,   max: 75, step: 1 },
      { key: 'torsoSideBendDeg',    label: 'Torso side-bend',    min: -30, max: 30, step: 1 },
      { key: 'shoulderShrugNorm',   label: 'Shoulder shrug',     min: -0.03, max: 0.06, step: 0.005 },
    ],
  },
  {
    name: 'Right arm (stroking)',
    params: [
      { key: 'rightShoulderAngleDeg',     label: 'Shoulder fwd',   min: -30, max: 180, step: 1, defaultValue: 41 },
      { key: 'rightShoulderAbductionDeg', label: 'Shoulder side',  min: -20, max: 120, step: 1, defaultValue: 31 },
      { key: 'rightElbowAngleDeg',        label: 'Elbow',          min: 30,  max: 180, step: 1 },
      { key: 'rightWristAngleDeg',        label: 'Wrist bend',     min: 90,  max: 180, step: 1 },
      { key: 'rightWristYawDeg',          label: 'Wrist yaw (ulnar/radial)', min: -30, max: 20, step: 1, defaultValue: 0 },
      { key: 'rightForearmTwistDeg',      label: 'Forearm twist',  min: -90, max: 90,  step: 1 },
      { key: 'rightElbowYawDeg',          label: 'Elbow swivel (shoulder+wrist pinned)', min: -90, max: 90, step: 1, defaultValue: 40 },
    ],
  },
  {
    name: 'Left arm',
    params: [
      { key: 'leftShoulderAngleDeg',     label: 'Shoulder fwd',   min: -30, max: 180, step: 1, defaultValue: 41 },
      { key: 'leftShoulderAbductionDeg', label: 'Shoulder side',  min: 0,   max: 120, step: 1, defaultValue: 31 },
      { key: 'leftElbowAngleDeg',        label: 'Elbow',          min: 30,  max: 180, step: 1 },
      { key: 'leftWristAngleDeg',         label: 'Wrist bend',     min: 90,  max: 180, step: 1 },
      { key: 'leftWristYawDeg',           label: 'Wrist yaw (ulnar/radial)', min: -30, max: 20, step: 1, defaultValue: 0 },
      { key: 'leftForearmTwistDeg',      label: 'Forearm twist',  min: -90, max: 90,  step: 1 },
      { key: 'leftElbowYawDeg',           label: 'Elbow swivel (shoulder+wrist pinned)', min: -90, max: 90, step: 1, defaultValue: 0 },
    ],
  },
  {
    name: 'Legs',
    params: [
      { key: 'leftThighForwardDeg',    label: 'L thigh forward',    min: -30, max: 120, step: 1 },
      { key: 'rightThighForwardDeg',   label: 'R thigh forward',    min: -30, max: 120, step: 1 },
      { key: 'leftThighAbductionDeg',  label: 'L thigh abduction',  min: -30, max: 64,  step: 1, defaultValue: 17 },
      { key: 'rightThighAbductionDeg', label: 'R thigh abduction',  min: -30, max: 64,  step: 1, defaultValue: 17 },
      { key: 'leftKneeAngleDeg',       label: 'L knee bend (180=straight, 30=deep)',   min: 30,  max: 180, step: 1 },
      { key: 'rightKneeAngleDeg',      label: 'R knee bend (180=straight, 30=deep)',   min: 30,  max: 180, step: 1 },
      // Past ~82° the kneeYaw interacts with kneeSwivel in a visually off
      // way (swivel offsets the knee past the hip-ankle projection). Keep
      // slider shy of that zone so (yaw, swivel) combinations stay clean.
      { key: 'leftKneeYawDeg',         label: 'L knee yaw',         min: -82, max: 82,  step: 1 },
      { key: 'rightKneeYawDeg',        label: 'R knee yaw',         min: -82, max: 82,  step: 1 },
      // Knee swivel is anatomically tiny — only a few very flexible people
      // exceed ±5°, so keep the slider tight to the realistic range.
      { key: 'leftKneeSwivelDeg',      label: 'L knee swivel (hip+ankle pinned)',  min: -5, max: 5, step: 1, defaultValue: 0 },
      { key: 'rightKneeSwivelDeg',     label: 'R knee swivel (hip+ankle pinned)',  min: -5, max: 5, step: 1, defaultValue: 0 },
      { key: 'leftFootYawDeg',         label: 'L foot yaw (vs shin)',  min: -60, max: 60,  step: 1 },
      { key: 'rightFootYawDeg',        label: 'R foot yaw (vs shin)',  min: -60, max: 60,  step: 1 },
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
