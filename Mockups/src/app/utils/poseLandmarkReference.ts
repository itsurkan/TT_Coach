/**
 * MediaPipe Pose Landmark Reference
 * 
 * This file contains the landmark indices for body parts used in pose estimation.
 * Use these constants when creating highlighted points and connections for feedback.
 */

export const PoseLandmarks = {
  // Face (0-10)
  NOSE: 0,
  LEFT_EYE_INNER: 1,
  LEFT_EYE: 2,
  LEFT_EYE_OUTER: 3,
  RIGHT_EYE_INNER: 4,
  RIGHT_EYE: 5,
  RIGHT_EYE_OUTER: 6,
  LEFT_EAR: 7,
  RIGHT_EAR: 8,
  MOUTH_LEFT: 9,
  MOUTH_RIGHT: 10,

  // Upper Body (11-22)
  LEFT_SHOULDER: 11,
  RIGHT_SHOULDER: 12,
  LEFT_ELBOW: 13,
  RIGHT_ELBOW: 14,
  LEFT_WRIST: 15,
  RIGHT_WRIST: 16,
  LEFT_PINKY: 17,
  RIGHT_PINKY: 18,
  LEFT_INDEX: 19,
  RIGHT_INDEX: 20,
  LEFT_THUMB: 21,
  RIGHT_THUMB: 22,

  // Lower Body (23-32)
  LEFT_HIP: 23,
  RIGHT_HIP: 24,
  LEFT_KNEE: 25,
  RIGHT_KNEE: 26,
  LEFT_ANKLE: 27,
  RIGHT_ANKLE: 28,
  LEFT_HEEL: 29,
  RIGHT_HEEL: 30,
  LEFT_FOOT_INDEX: 31,
  RIGHT_FOOT_INDEX: 32,
} as const;

/**
 * Common body part groups for easy reference
 */
export const BodyPartGroups = {
  RIGHT_ARM: [
    PoseLandmarks.RIGHT_SHOULDER,
    PoseLandmarks.RIGHT_ELBOW,
    PoseLandmarks.RIGHT_WRIST,
  ],
  LEFT_ARM: [
    PoseLandmarks.LEFT_SHOULDER,
    PoseLandmarks.LEFT_ELBOW,
    PoseLandmarks.LEFT_WRIST,
  ],
  RIGHT_LEG: [
    PoseLandmarks.RIGHT_HIP,
    PoseLandmarks.RIGHT_KNEE,
    PoseLandmarks.RIGHT_ANKLE,
  ],
  LEFT_LEG: [
    PoseLandmarks.LEFT_HIP,
    PoseLandmarks.LEFT_KNEE,
    PoseLandmarks.LEFT_ANKLE,
  ],
  SHOULDERS: [PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER],
  HIPS: [PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP],
} as const;

/**
 * Common connection groups for highlighting specific movements
 */
export const ConnectionGroups = {
  RIGHT_ARM: [
    [PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_ELBOW],
    [PoseLandmarks.RIGHT_ELBOW, PoseLandmarks.RIGHT_WRIST],
  ] as [number, number][],
  LEFT_ARM: [
    [PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_ELBOW],
    [PoseLandmarks.LEFT_ELBOW, PoseLandmarks.LEFT_WRIST],
  ] as [number, number][],
  RIGHT_LEG: [
    [PoseLandmarks.RIGHT_HIP, PoseLandmarks.RIGHT_KNEE],
    [PoseLandmarks.RIGHT_KNEE, PoseLandmarks.RIGHT_ANKLE],
  ] as [number, number][],
  LEFT_LEG: [
    [PoseLandmarks.LEFT_HIP, PoseLandmarks.LEFT_KNEE],
    [PoseLandmarks.LEFT_KNEE, PoseLandmarks.LEFT_ANKLE],
  ] as [number, number][],
  TORSO: [
    [PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER],
    [PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_HIP],
    [PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_HIP],
    [PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP],
  ] as [number, number][],
} as const;

/**
 * Example usage:
 * 
 * const feedback: Feedback = {
 *   id: "1",
 *   type: "success",
 *   message: "Perfect forehand stroke!",
 *   poseData: {
 *     frames: yourPoseFrames,
 *     duration: 0.9,
 *     highlightedPoints: BodyPartGroups.RIGHT_ARM,
 *     highlightedConnections: ConnectionGroups.RIGHT_ARM,
 *   },
 * };
 */
