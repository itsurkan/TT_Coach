import { polarToFlexAbd, flexAbdToPolar } from './polarShoulder'

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

/** Common fields shared by all slider spec kinds. */
interface AnchorParamSpecBase {
  label: string
  min: number
  max: number
  step: number
  /** Reset/MIDPOINT_POSE target. When omitted, MIDPOINT_POSE uses (min+max)/2.
   *  Use this to decouple "slider reach" from "default pose" — e.g. shoulder
   *  abduction goes up to 120° but the ready-position default sits at 31°.
   *  Value is snapped to `step` before use.
   *  Not applicable to computed specs (polar sliders re-derive from the
   *  underlying anchor on every render). */
  defaultValue?: number
}

/** A slider that reads and writes a single `PoseAnchor` field directly. */
export interface DirectAnchorParamSpec extends AnchorParamSpecBase {
  kind: 'direct'
  key: keyof PoseAnchor
}

/** A slider that exposes a *computed view* over one or more anchor fields.
 *  Used for the polar shoulder sliders (elevation + plane) which are a
 *  reparameterization of `*ShoulderAngleDeg` + `*ShoulderAbductionDeg`. */
export interface ComputedAnchorParamSpec extends AnchorParamSpecBase {
  kind: 'computed'
  /** Stable id used for row refs, clipboard copy, and highlight matching.
   *  Must be unique across all specs (direct or computed). */
  id: string
  /** Underlying anchor keys this view touches. Joint-highlighting marks
   *  this spec as highlighted when any of these keys is in
   *  `highlightedParams`. Does not restrict reads/writes — the closures
   *  below are authoritative for that. */
  keys: readonly (keyof PoseAnchor)[]
  read: (anchor: PoseAnchor) => number
  write: (anchor: PoseAnchor, value: number) => PoseAnchor
}

export type AnchorParamSpec = DirectAnchorParamSpec | ComputedAnchorParamSpec

export interface AnchorParamGroup {
  name: string
  params: AnchorParamSpec[]
}

export const ANCHOR_PARAM_GROUPS: AnchorParamGroup[] = [
  {
    name: 'Torso',
    params: [
      { kind: 'direct', key: 'figureYawDeg',        label: 'Figure yaw (whole body)', min: -180, max: 180, step: 1 },
      { kind: 'direct', key: 'bodyRotationDeg',     label: 'Hip rotation (pelvis twist)', min: -90, max: 90, step: 1 },
      { kind: 'direct', key: 'pelvicRollDeg',       label: 'Pelvic roll',        min: -30, max: 30, step: 1 },
      { kind: 'direct', key: 'shoulderRotationDeg', label: 'Corpus rotation',    min: -90, max: 90, step: 1 },
      { kind: 'direct', key: 'torsoTiltDeg',        label: 'Torso tilt forward', min: 0,   max: 75, step: 1 },
      { kind: 'direct', key: 'torsoSideBendDeg',    label: 'Torso side-bend',    min: -30, max: 30, step: 1 },
      { kind: 'direct', key: 'shoulderShrugNorm',   label: 'Shoulder shrug',     min: -0.03, max: 0.06, step: 0.005 },
    ],
  },
  {
    name: 'Right arm (stroking)',
    params: [
      { kind: 'direct', key: 'rightShoulderAngleDeg',     label: 'Shoulder fwd',   min: -30, max: 180, step: 1, defaultValue: 41 },
      { kind: 'direct', key: 'rightShoulderAbductionDeg', label: 'Shoulder side',  min: -40, max: 120, step: 1, defaultValue: 31 },
      {
        kind: 'computed',
        id: 'rightShoulderElevationDeg',
        keys: ['rightShoulderAngleDeg', 'rightShoulderAbductionDeg'],
        label: 'Shoulder elevation',
        min: 0, max: 180, step: 1,
        read: a => flexAbdToPolar({
          flex: a.rightShoulderAngleDeg, abd: a.rightShoulderAbductionDeg,
        }).elevation,
        write: (a, v) => {
          const polar = flexAbdToPolar({
            flex: a.rightShoulderAngleDeg, abd: a.rightShoulderAbductionDeg,
          })
          const rect = polarToFlexAbd({ elevation: v, plane: polar.plane })
          return {
            ...a,
            rightShoulderAngleDeg: clamp(rect.flex, -30, 180),
            rightShoulderAbductionDeg: clamp(rect.abd, -40, 120),
          }
        },
      },
      {
        kind: 'computed',
        id: 'rightShoulderPlaneDeg',
        keys: ['rightShoulderAngleDeg', 'rightShoulderAbductionDeg'],
        label: 'Shoulder plane',
        min: -90, max: 180, step: 1,
        read: a => flexAbdToPolar({
          flex: a.rightShoulderAngleDeg, abd: a.rightShoulderAbductionDeg,
        }).plane,
        write: (a, v) => {
          const polar = flexAbdToPolar({
            flex: a.rightShoulderAngleDeg, abd: a.rightShoulderAbductionDeg,
          })
          const rect = polarToFlexAbd({ elevation: polar.elevation, plane: v })
          return {
            ...a,
            rightShoulderAngleDeg: clamp(rect.flex, -30, 180),
            rightShoulderAbductionDeg: clamp(rect.abd, -40, 120),
          }
        },
      },
      { kind: 'direct', key: 'rightElbowAngleDeg',        label: 'Elbow',          min: 30,  max: 180, step: 1 },
      { kind: 'direct', key: 'rightWristAngleDeg',        label: 'Wrist bend',     min: 90,  max: 180, step: 1 },
      { kind: 'direct', key: 'rightWristYawDeg',          label: 'Wrist yaw (ulnar/radial)', min: -30, max: 20, step: 1, defaultValue: 0 },
      { kind: 'direct', key: 'rightForearmTwistDeg',      label: 'Forearm twist',  min: -90, max: 90,  step: 1 },
      { kind: 'direct', key: 'rightElbowYawDeg',          label: 'Elbow swivel (shoulder+wrist pinned)', min: -90, max: 90, step: 1, defaultValue: 40 },
    ],
  },
  {
    name: 'Left arm',
    params: [
      { kind: 'direct', key: 'leftShoulderAngleDeg',     label: 'Shoulder fwd',   min: -30, max: 180, step: 1, defaultValue: 41 },
      { kind: 'direct', key: 'leftShoulderAbductionDeg', label: 'Shoulder side',  min: 0,   max: 120, step: 1, defaultValue: 31 },
      {
        kind: 'computed',
        id: 'leftShoulderElevationDeg',
        keys: ['leftShoulderAngleDeg', 'leftShoulderAbductionDeg'],
        label: 'Shoulder elevation',
        min: 0, max: 180, step: 1,
        read: a => flexAbdToPolar({
          flex: a.leftShoulderAngleDeg, abd: a.leftShoulderAbductionDeg,
        }).elevation,
        write: (a, v) => {
          const polar = flexAbdToPolar({
            flex: a.leftShoulderAngleDeg, abd: a.leftShoulderAbductionDeg,
          })
          const rect = polarToFlexAbd({ elevation: v, plane: polar.plane })
          return {
            ...a,
            leftShoulderAngleDeg: clamp(rect.flex, -30, 180),
            leftShoulderAbductionDeg: clamp(rect.abd, 0, 120),
          }
        },
      },
      {
        kind: 'computed',
        id: 'leftShoulderPlaneDeg',
        keys: ['leftShoulderAngleDeg', 'leftShoulderAbductionDeg'],
        label: 'Shoulder plane',
        min: -90, max: 180, step: 1,
        read: a => flexAbdToPolar({
          flex: a.leftShoulderAngleDeg, abd: a.leftShoulderAbductionDeg,
        }).plane,
        write: (a, v) => {
          const polar = flexAbdToPolar({
            flex: a.leftShoulderAngleDeg, abd: a.leftShoulderAbductionDeg,
          })
          const rect = polarToFlexAbd({ elevation: polar.elevation, plane: v })
          return {
            ...a,
            leftShoulderAngleDeg: clamp(rect.flex, -30, 180),
            leftShoulderAbductionDeg: clamp(rect.abd, 0, 120),
          }
        },
      },
      { kind: 'direct', key: 'leftElbowAngleDeg',        label: 'Elbow',          min: 30,  max: 180, step: 1 },
      { kind: 'direct', key: 'leftWristAngleDeg',         label: 'Wrist bend',     min: 90,  max: 180, step: 1 },
      { kind: 'direct', key: 'leftWristYawDeg',           label: 'Wrist yaw (ulnar/radial)', min: -30, max: 20, step: 1, defaultValue: 0 },
      { kind: 'direct', key: 'leftForearmTwistDeg',      label: 'Forearm twist',  min: -90, max: 90,  step: 1 },
      { kind: 'direct', key: 'leftElbowYawDeg',           label: 'Elbow swivel (shoulder+wrist pinned)', min: -90, max: 90, step: 1, defaultValue: 0 },
    ],
  },
  {
    name: 'Legs',
    params: [
      { kind: 'direct', key: 'leftThighForwardDeg',    label: 'L thigh forward',    min: -30, max: 120, step: 1 },
      { kind: 'direct', key: 'rightThighForwardDeg',   label: 'R thigh forward',    min: -30, max: 120, step: 1 },
      { kind: 'direct', key: 'leftThighAbductionDeg',  label: 'L thigh abduction',  min: -30, max: 64,  step: 1, defaultValue: 17 },
      { kind: 'direct', key: 'rightThighAbductionDeg', label: 'R thigh abduction',  min: -30, max: 64,  step: 1, defaultValue: 17 },
      { kind: 'direct', key: 'leftKneeAngleDeg',       label: 'L knee bend (180=straight, 30=deep)',   min: 30,  max: 180, step: 1 },
      { kind: 'direct', key: 'rightKneeAngleDeg',      label: 'R knee bend (180=straight, 30=deep)',   min: 30,  max: 180, step: 1 },
      // Past ~82° the kneeYaw interacts with kneeSwivel in a visually off
      // way (swivel offsets the knee past the hip-ankle projection). Keep
      // slider shy of that zone so (yaw, swivel) combinations stay clean.
      { kind: 'direct', key: 'leftKneeYawDeg',         label: 'L knee yaw',         min: -82, max: 82,  step: 1 },
      { kind: 'direct', key: 'rightKneeYawDeg',        label: 'R knee yaw',         min: -82, max: 82,  step: 1 },
      // Knee swivel is anatomically tiny — only a few very flexible people
      // exceed ±5°, so keep the slider tight to the realistic range.
      { kind: 'direct', key: 'leftKneeSwivelDeg',      label: 'L knee swivel (hip+ankle pinned)',  min: -5, max: 5, step: 1, defaultValue: 0 },
      { kind: 'direct', key: 'rightKneeSwivelDeg',     label: 'R knee swivel (hip+ankle pinned)',  min: -5, max: 5, step: 1, defaultValue: 0 },
      { kind: 'direct', key: 'leftFootYawDeg',         label: 'L foot yaw (vs shin)',  min: -60, max: 60,  step: 1 },
      { kind: 'direct', key: 'rightFootYawDeg',        label: 'R foot yaw (vs shin)',  min: -60, max: 60,  step: 1 },
      { kind: 'direct', key: 'stanceWidthNorm',        label: 'Stance width',       min: 0.10, max: 0.70, step: 0.01 },
    ],
  },
  {
    name: 'Position (root)',
    params: [
      { kind: 'direct', key: 'hipMidX', label: 'Hip X', min: 0.30, max: 0.70, step: 0.005 },
      { kind: 'direct', key: 'hipMidY', label: 'Hip Y', min: 0.25, max: 0.55, step: 0.005 },
    ],
  },
]

function clamp(v: number, lo: number, hi: number): number {
  return v < lo ? lo : v > hi ? hi : v
}

/** Flattened list of all param specs (preserves group ordering). */
export const ANCHOR_PARAM_SPECS: AnchorParamSpec[] =
  ANCHOR_PARAM_GROUPS.flatMap(g => g.params)
