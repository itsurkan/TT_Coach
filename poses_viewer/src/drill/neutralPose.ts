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
  shoulderRotationDeg: 0,       // shoulders aligned with hips (no coil)
  torsoTiltDeg: 25,             // clear forward lean — athletic crouch

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

export function cloneAnchor(a: PoseAnchor): PoseAnchor {
  return { ...a }
}
