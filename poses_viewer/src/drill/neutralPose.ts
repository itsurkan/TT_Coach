import type { PoseAnchor } from './PoseAnchor'

/**
 * Base table-tennis ready position (right-handed player).
 *
 * Athletic "receive-ready" crouch: weight on balls of feet, knees over toes,
 * torso leaned forward at the hips. Hips face the three-quarter camera;
 * shoulders add a little extra coil on top via `shoulderRotationDeg` so the
 * right shoulder sits slightly more back than the hips alone would give
 * (pre-loaded for a forehand).
 *
 * Used as the initial pose when opening the Drill Editor — every new drill
 * starts from this baseline. "Reset" in the editor returns here.
 */
export const NEUTRAL_POSE: PoseAnchor = {
  // ── Torso ──────────────────────────────────────────────────────────────
  bodyRotationDeg: 50,          // hips rotated right: right hip back
  pelvicRollDeg: 0,
  shoulderRotationDeg: 0,       // shoulders aligned with hips (no coil)
  torsoTiltDeg: 25,             // clear forward lean — athletic crouch
  torsoSideBendDeg: 0,
  shoulderShrugNorm: 0,

  // ── Right arm (playing / racket hand) ──────────────────────────────────
  rightShoulderAngleDeg: 45,
  rightShoulderAbductionDeg: 25,
  rightElbowAngleDeg: 95,
  rightWristAngleDeg: 160,
  rightForearmTwistDeg: 0,

  // ── Left arm (free hand / balance) ─────────────────────────────────────
  leftShoulderAngleDeg: 30,
  leftShoulderAbductionDeg: 20,
  leftElbowAngleDeg: 120,
  leftWristAngleDeg: 165,
  leftForearmTwistDeg: 0,

  // ── Legs ───────────────────────────────────────────────────────────────
  leftThighForwardDeg: 28,
  rightThighForwardDeg: 28,
  leftThighAbductionDeg: 10,
  rightThighAbductionDeg: 10,
  leftKneeAngleDeg: 130,
  rightKneeAngleDeg: 130,
  leftFootYawDeg: 15,
  rightFootYawDeg: -15,

  // ── Stance / root ──────────────────────────────────────────────────────
  stanceWidthNorm: 0.32,
  hipMidX: 0.50,
  hipMidY: 0.42,
}

/**
 * Relaxed standing pose — default for the mannequin editor. Player faces
 * camera square-on so the figure reads as truly symmetric: arms hang
 * naturally at the sides with a small abduction so they don't intersect the
 * torso, knees almost locked (not hyper-extended), feet shoulder-width apart.
 * Viewers orbit via the camera (OrbitControls) to inspect from a 3/4 angle —
 * baking rotation into the pose creates perspective illusions that read as
 * pelvic roll / floating-foot even though the FK output is symmetric.
 * Used as the baseline new users shape FROM, rather than the pre-loaded
 * athletic crouch that NEUTRAL_POSE encodes for drill playback.
 */
export const STANDING_POSE: PoseAnchor = {
  // Torso — upright, facing camera, no coil.
  bodyRotationDeg: 0,
  pelvicRollDeg: 0,
  shoulderRotationDeg: 0,
  torsoTiltDeg: 0,
  torsoSideBendDeg: 0,
  shoulderShrugNorm: 0,

  // Arms — hanging down, tiny outward abduction so the capsules clear the torso.
  rightShoulderAngleDeg: 5,
  rightShoulderAbductionDeg: 8,
  rightElbowAngleDeg: 170,
  rightWristAngleDeg: 175,
  rightForearmTwistDeg: 0,
  leftShoulderAngleDeg: 5,
  leftShoulderAbductionDeg: 8,
  leftElbowAngleDeg: 170,
  leftWristAngleDeg: 175,
  leftForearmTwistDeg: 0,

  // Legs — standing tall, knees soft (175, not 180, to avoid lock-out).
  // Symmetric abduction + mirrored foot yaw: both legs tilt slightly outward
  // with feet pointed slightly away from each other, mimicking a relaxed
  // stance. Kept symmetric so there's no lateral CoM offset — asymmetric
  // values only looked right under a rotated camera and read as "falling"
  // when the body faces straight ahead.
  leftThighForwardDeg: 0,
  rightThighForwardDeg: 0,
  leftThighAbductionDeg: 3,
  rightThighAbductionDeg: 3,
  leftKneeAngleDeg: 175,
  rightKneeAngleDeg: 175,
  leftFootYawDeg: 8,
  rightFootYawDeg: -8,

  // Narrow but steady stance.
  stanceWidthNorm: 0.20,
  hipMidX: 0.50,
  hipMidY: 0.42,
}

export function cloneAnchor(a: PoseAnchor): PoseAnchor {
  return { ...a }
}
