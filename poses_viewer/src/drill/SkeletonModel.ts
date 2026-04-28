/**
 * Canonical skeleton model for the drill editor.
 *
 * Fixed bone lengths in normalized screen-height coordinates (y ∈ [0, 1]).
 * Bone lengths are independent of any player — every drill renders against
 * the same skeleton. Drill authoring = pose anchors (angles only), not
 * per-player body scans.
 */

export const BONES = {
  headToShoulder: 0.18,
  shoulderWidth: 0.30,
  torso: 0.32,
  upperArm: 0.22,
  forearm: 0.20,
  hand: 0.08,
  hipWidth: 0.20,
  thigh: 0.28,
  shin: 0.26,
  footForward: 0.10,
} as const

/** MediaPipe BlazePose 33-landmark indices. */
export const LM = {
  NOSE: 0,
  L_EYE_INNER: 1, L_EYE: 2, L_EYE_OUTER: 3,
  R_EYE_INNER: 4, R_EYE: 5, R_EYE_OUTER: 6,
  L_EAR: 7, R_EAR: 8,
  MOUTH_L: 9, MOUTH_R: 10,
  L_SHOULDER: 11, R_SHOULDER: 12,
  L_ELBOW: 13, R_ELBOW: 14,
  L_WRIST: 15, R_WRIST: 16,
  L_PINKY: 17, R_PINKY: 18,
  L_INDEX: 19, R_INDEX: 20,
  L_THUMB: 21, R_THUMB: 22,
  L_HIP: 23, R_HIP: 24,
  L_KNEE: 25, R_KNEE: 26,
  L_ANKLE: 27, R_ANKLE: 28,
  L_HEEL: 29, R_HEEL: 30,
  L_FOOT: 31, R_FOOT: 32,
} as const

export const LANDMARK_COUNT = 33
