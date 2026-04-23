import type { PoseAnchor } from './PoseAnchor'
import { ANCHOR_PARAM_SPECS } from './PoseAnchor'

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
  figureYawDeg: 50,             // entire figure faces three-quarter (right side toward camera)
  bodyRotationDeg: 0,           // no extra pelvic twist on top of figure yaw
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
  leftKneeYawDeg: 15,           // knees splay outward over the toes
  rightKneeYawDeg: -15,
  leftFootYawDeg: 0,            // feet aligned with the knees
  rightFootYawDeg: 0,

  // ── Stance / root ──────────────────────────────────────────────────────
  stanceWidthNorm: 0.32,
  hipMidX: 0.50,
  hipMidY: 0.42,
}

/**
 * Anatomical reference standing pose — default for the mannequin editor.
 * Every segment is strictly perpendicular to the floor: spine, thighs,
 * shins and arms are all vertical; knees and elbows fully extended; feet
 * point straight ahead. Heels sit ~4cm behind the head line because the
 * heel bone anatomically attaches behind the ankle joint — forcing heels
 * directly under the head would require a forward thigh tilt, which reads
 * as the figure leaning/falling.
 * Kept as the blank-canvas pose new users shape FROM — distinct from the
 * pre-loaded athletic crouch NEUTRAL_POSE used by DrillEditor.
 */
export const STANDING_POSE: PoseAnchor = {
  figureYawDeg: 0,
  bodyRotationDeg: 0,
  pelvicRollDeg: 0,
  shoulderRotationDeg: 0,
  torsoTiltDeg: 0,
  torsoSideBendDeg: 0,
  shoulderShrugNorm: 0,

  rightShoulderAngleDeg: 0,
  rightShoulderAbductionDeg: 0,
  rightElbowAngleDeg: 180,
  rightWristAngleDeg: 180,
  rightForearmTwistDeg: 0,
  leftShoulderAngleDeg: 0,
  leftShoulderAbductionDeg: 0,
  leftElbowAngleDeg: 180,
  leftWristAngleDeg: 180,
  leftForearmTwistDeg: 0,

  leftThighForwardDeg: 0,
  rightThighForwardDeg: 0,
  leftThighAbductionDeg: 0,
  rightThighAbductionDeg: 0,
  leftKneeAngleDeg: 180,
  rightKneeAngleDeg: 180,
  leftKneeYawDeg: 0,
  rightKneeYawDeg: 0,
  leftFootYawDeg: 0,
  rightFootYawDeg: 0,

  stanceWidthNorm: 0.20,
  hipMidX: 0.50,
  hipMidY: 0.42,
}

export function cloneAnchor(a: PoseAnchor): PoseAnchor {
  return { ...a }
}

/**
 * Slider-midpoint pose — every numeric param sits at the middle of its
 * [min, max] range, snapped to the slider's step grid. Derived from
 * ANCHOR_PARAM_SPECS so it stays in sync if a range changes. Used as the
 * Mannequin Editor's Reset target ("middle = default"). Not anatomical —
 * e.g. torso tilts ~38°, knees sit mid-bend.
 *
 * To shift the midpoint for a specific slider, narrow that slider's [min, max]
 * in ANCHOR_PARAM_GROUPS so (min+max)/2 lands where you want. This pose has
 * no hand-tuned overrides — every value is mechanically (min+max)/2.
 */
export const MIDPOINT_POSE: PoseAnchor = buildMidpointPose()

function buildMidpointPose(): PoseAnchor {
  const out = cloneAnchor(STANDING_POSE)
  for (const spec of ANCHOR_PARAM_SPECS) {
    const raw = spec.defaultValue ?? (spec.min + spec.max) / 2
    const snapped = Math.round(raw / spec.step) * spec.step
    // Trim float noise from step multiplication (e.g. 0.005 * 3 = 0.015000…2).
    const decimals = spec.step >= 1 ? 0 : Math.max(0, -Math.floor(Math.log10(spec.step)))
    ;(out as unknown as Record<string, number>)[spec.key as string] =
      parseFloat(snapped.toFixed(decimals))
  }
  return out
}
