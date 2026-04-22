// Joint → (anchor-param, landmark, body-part) lookup for the mannequin editor.
//
// When the user clicks a joint in the 3D scene, the editor needs to know:
//   1. which PoseAnchor sliders rotate that joint — to highlight + scroll,
//   2. which MediaPipe landmark position to show in the coordinates HUD,
//   3. which body-part colour to use for the emissive selection glow.
//
// A joint and a body part are not the same thing: the right-shoulder joint
// belongs to the right-upper-arm body segment (the part that moves when the
// joint rotates), and the mid-hip joint is a synthetic point averaged from
// both hip landmarks for HUD readout only — it is not rendered separately.
//
// The landmark source is a single MediaPipe index for most joints, or a pair
// that the HUD averages for composite joints (mid-hip, mid-shoulder).

import type { PoseAnchor } from './PoseAnchor'
import { LM } from './SkeletonModel'
import type { BodyPartId } from './jointColorScheme'

export type AnchorParamKey = keyof PoseAnchor

export type JointId =
  | 'head' | 'neck' | 'shoulderMid' | 'hipMid'
  | 'rightShoulder' | 'rightElbow' | 'rightWrist'
  | 'leftShoulder'  | 'leftElbow'  | 'leftWrist'
  | 'rightHip' | 'rightKnee' | 'rightAnkle'
  | 'leftHip'  | 'leftKnee'  | 'leftAnkle'

/** Landmark source — either a single MediaPipe index or a pair averaged for composite joints. */
export type LandmarkSource = number | readonly [number, number]

export interface JointDefinition {
  /** MediaPipe landmark (or pair) whose world position is shown in the HUD. */
  landmarkIdx: LandmarkSource
  /** PoseAnchor slider keys that move this joint; used to drive slider highlight + auto-scroll. */
  controlParams: readonly AnchorParamKey[]
  /** Body part this joint belongs to, for colour + emissive highlight. */
  bodyPart: BodyPartId
  /** Ukrainian display name shown in the HUD header. */
  displayName: string
}

export const JOINT_MAP: Record<JointId, JointDefinition> = {
  head: {
    landmarkIdx: LM.NOSE,
    // No dedicated head-rotation field on PoseAnchor; torso tilt is the closest
    // proxy for moving the head up/down in the current FK chain.
    controlParams: ['torsoTiltDeg'],
    bodyPart: 'head',
    displayName: 'голова',
  },
  neck: {
    landmarkIdx: [LM.L_SHOULDER, LM.R_SHOULDER],
    controlParams: ['torsoTiltDeg', 'shoulderRotationDeg'],
    bodyPart: 'torso',
    displayName: 'шия',
  },
  shoulderMid: {
    landmarkIdx: [LM.L_SHOULDER, LM.R_SHOULDER],
    controlParams: ['torsoTiltDeg', 'bodyRotationDeg', 'shoulderRotationDeg', 'torsoSideBendDeg', 'shoulderShrugNorm'],
    bodyPart: 'torso',
    displayName: 'середина плечей',
  },
  hipMid: {
    landmarkIdx: [LM.L_HIP, LM.R_HIP],
    controlParams: ['bodyRotationDeg', 'pelvicRollDeg', 'torsoTiltDeg', 'hipMidX', 'hipMidY'],
    bodyPart: 'torso',
    displayName: 'таз',
  },

  // Right arm — joints belong to the body segment they move (shoulder → upper arm, etc.).
  rightShoulder: {
    landmarkIdx: LM.R_SHOULDER,
    controlParams: ['rightShoulderAngleDeg', 'rightShoulderAbductionDeg', 'shoulderRotationDeg'],
    bodyPart: 'rightUpperArm',
    displayName: 'правий плечовий суглоб',
  },
  rightElbow: {
    landmarkIdx: LM.R_ELBOW,
    controlParams: ['rightElbowAngleDeg', 'rightForearmTwistDeg'],
    bodyPart: 'rightForearm',
    displayName: 'правий лікоть',
  },
  rightWrist: {
    landmarkIdx: LM.R_WRIST,
    controlParams: ['rightWristAngleDeg', 'rightForearmTwistDeg'],
    bodyPart: 'rightHand',
    displayName: "правий зап'ясток",
  },

  // Left arm.
  leftShoulder: {
    landmarkIdx: LM.L_SHOULDER,
    controlParams: ['leftShoulderAngleDeg', 'leftShoulderAbductionDeg', 'shoulderRotationDeg'],
    bodyPart: 'leftUpperArm',
    displayName: 'лівий плечовий суглоб',
  },
  leftElbow: {
    landmarkIdx: LM.L_ELBOW,
    controlParams: ['leftElbowAngleDeg', 'leftForearmTwistDeg'],
    bodyPart: 'leftForearm',
    displayName: 'лівий лікоть',
  },
  leftWrist: {
    landmarkIdx: LM.L_WRIST,
    controlParams: ['leftWristAngleDeg', 'leftForearmTwistDeg'],
    bodyPart: 'leftHand',
    displayName: "лівий зап'ясток",
  },

  // Right leg.
  rightHip: {
    landmarkIdx: LM.R_HIP,
    controlParams: ['rightThighForwardDeg', 'rightThighAbductionDeg', 'stanceWidthNorm', 'bodyRotationDeg'],
    bodyPart: 'rightThigh',
    displayName: 'правий кульшовий суглоб',
  },
  rightKnee: {
    landmarkIdx: LM.R_KNEE,
    controlParams: ['rightKneeAngleDeg', 'rightThighForwardDeg'],
    bodyPart: 'rightShin',
    displayName: 'праве коліно',
  },
  rightAnkle: {
    landmarkIdx: LM.R_ANKLE,
    controlParams: ['rightFootYawDeg', 'rightKneeAngleDeg'],
    bodyPart: 'rightFoot',
    displayName: 'права щиколотка',
  },

  // Left leg.
  leftHip: {
    landmarkIdx: LM.L_HIP,
    controlParams: ['leftThighForwardDeg', 'leftThighAbductionDeg', 'stanceWidthNorm', 'bodyRotationDeg'],
    bodyPart: 'leftThigh',
    displayName: 'лівий кульшовий суглоб',
  },
  leftKnee: {
    landmarkIdx: LM.L_KNEE,
    controlParams: ['leftKneeAngleDeg', 'leftThighForwardDeg'],
    bodyPart: 'leftShin',
    displayName: 'ліве коліно',
  },
  leftAnkle: {
    landmarkIdx: LM.L_ANKLE,
    controlParams: ['leftFootYawDeg', 'leftKneeAngleDeg'],
    bodyPart: 'leftFoot',
    displayName: 'ліва щиколотка',
  },
}

/** Deterministic iteration order — head → feet, right side before left. */
export const JOINT_ORDER: readonly JointId[] = [
  'head', 'neck', 'shoulderMid',
  'rightShoulder', 'rightElbow', 'rightWrist',
  'leftShoulder', 'leftElbow', 'leftWrist',
  'hipMid',
  'rightHip', 'rightKnee', 'rightAnkle',
  'leftHip', 'leftKnee', 'leftAnkle',
] as const
