/**
 * TS mirror of shared/ KMP 2D models (Keypoint2D, PoseFrame2D, Stroke2D,
 * Handedness, Coco17). Kotlin is the source of truth — see the M0 spec's
 * binding fix-flow rule.
 */
export interface Keypoint2D {
  x: number
  y: number
  score: number
}

/** One video frame of 2D keypoints. Empty keypoints = no person detected. */
export interface PoseFrame2D {
  frameIndex: number
  timestampMs: number
  keypoints: Keypoint2D[]
}

/** One detected stroke; frame fields index into the source frame list. */
export interface Stroke2D {
  strokeIndex: number
  startFrame: number
  peakFrame: number
  endFrame: number
  /** Smoothed wrist speed at the peak, in torso-lengths per second. */
  peakSpeed: number
}

export type Handedness = 'right' | 'left'

/** COCO-17 keypoint indices (docs/pose_json_schema_v2.md). Valid for Halpe26 indices 0–16 too. */
export const Coco17 = {
  NOSE: 0,
  LEFT_EYE: 1,
  RIGHT_EYE: 2,
  LEFT_EAR: 3,
  RIGHT_EAR: 4,
  LEFT_SHOULDER: 5,
  RIGHT_SHOULDER: 6,
  LEFT_ELBOW: 7,
  RIGHT_ELBOW: 8,
  LEFT_WRIST: 9,
  RIGHT_WRIST: 10,
  LEFT_HIP: 11,
  RIGHT_HIP: 12,
  LEFT_KNEE: 13,
  RIGHT_KNEE: 14,
  LEFT_ANKLE: 15,
  RIGHT_ANKLE: 16,
  shoulder(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_SHOULDER : Coco17.LEFT_SHOULDER
  },
  elbow(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_ELBOW : Coco17.LEFT_ELBOW
  },
  wrist(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_WRIST : Coco17.LEFT_WRIST
  },
  hip(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_HIP : Coco17.LEFT_HIP
  },
  knee(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_KNEE : Coco17.LEFT_KNEE
  },
  ankle(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_ANKLE : Coco17.LEFT_ANKLE
  },
} as const
