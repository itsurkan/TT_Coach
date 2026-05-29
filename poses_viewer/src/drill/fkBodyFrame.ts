/**
 * Body-frame construction for the FK skeleton reconstructor.
 *
 * Exports: BoneLengthsOverride, ResolvedBones, BodyFrame, GROUND_ANCHOR_Y,
 *          resolveBones, buildBodyFrame, buildHeadFace.
 */

import type { V3 } from './fkMath'
import { deg, add, scale, normalize, rotY, rotAroundAxis, mkLm } from './fkMath'
import { BONES, LM } from './SkeletonModel'
import type { PoseAnchor } from './PoseAnchor'
import type { Landmark } from '../types'

// ── Module-scoped unexported constants ────────────────────────────────────────
const TILT_TO_KNEE_BEND = 0
const EFFECTIVE_KNEE_MIN_DEG = 30
const TILT_TO_HIP_BACK = 0

// ── Interfaces ────────────────────────────────────────────────────────────────

/** Per-bone length overrides. Missing fields fall back to canonical [BONES]. */
export interface BoneLengthsOverride {
  torso?: number
  shoulderWidth?: number
  upperArm?: number
  forearm?: number
  hand?: number
  hipWidth?: number
  thigh?: number
  shin?: number
  footForward?: number
  headToShoulder?: number
  /** Per-side overrides (take precedence over the symmetric fallback above). */
  leftThigh?: number
  rightThigh?: number
  leftShin?: number
  rightShin?: number
  leftUpperArm?: number
  rightUpperArm?: number
  leftForearm?: number
  rightForearm?: number
  leftFootForward?: number
  rightFootForward?: number
}

/** Resolved symmetric bone lengths (per-side overrides handled by orchestrator). */
export interface ResolvedBones {
  torso: number
  shoulderWidth: number
  upperArm: number
  forearm: number
  hand: number
  hipWidth: number
  thigh: number
  shin: number
  footForward: number
  headToShoulder: number
}

/** All body-frame axes and anchor positions computed from a PoseAnchor. */
export interface BodyFrame {
  legForward: V3; legAcross: V3
  forward: V3; acrossLevel: V3; across: V3
  shoulderForward: V3; shoulderAcross: V3
  torsoUp: V3; torsoDown: V3
  hipMid: V3
  effLeftKnee: number; effRightKnee: number
  lHip: V3; rHip: V3
  lShoulder: V3; rShoulder: V3
  shoulderMid: V3
}

/**
 * Expected ankle midpoint Y in anchor coordinates. After FK, the whole body
 * is translated vertically so the ankle midpoint lands on this line — feet
 * stay on the ground regardless of torso tilt or knee bend.
 */
export const GROUND_ANCHOR_Y = 0.92

// ── Functions ─────────────────────────────────────────────────────────────────

/** Resolve bone lengths: override → canonical BONES. */
export function resolveBones(override?: BoneLengthsOverride): ResolvedBones {
  return {
    torso:          override?.torso          ?? BONES.torso,
    shoulderWidth:  override?.shoulderWidth  ?? BONES.shoulderWidth,
    upperArm:       override?.upperArm       ?? BONES.upperArm,
    forearm:        override?.forearm        ?? BONES.forearm,
    hand:           override?.hand           ?? BONES.hand,
    hipWidth:       override?.hipWidth       ?? BONES.hipWidth,
    thigh:          override?.thigh          ?? BONES.thigh,
    shin:           override?.shin           ?? BONES.shin,
    footForward:    override?.footForward    ?? BONES.footForward,
    headToShoulder: override?.headToShoulder ?? BONES.headToShoulder,
  }
}

/**
 * Build all body-frame axis vectors, positions and effective knee angles from
 * a PoseAnchor and resolved bone lengths.
 * Logic extracted verbatim from skeletonReconstructor.ts lines 133–203.
 */
export function buildBodyFrame(anchor: PoseAnchor, B: ResolvedBones): BodyFrame {
  // Body axes ──────────────────────────────────────────────────────────────
  // Two yaw layers stacked around the vertical axis through hipMid:
  //   figureYawDeg     — orients the entire figure (legs + hips + upper body)
  //   bodyRotationDeg  — pelvic twist relative to the planted legs (upper body
  //                      swings; legs stay where the figure yaw put them)
  // Legs use the leg frame; hips/torso/arms/head use the hip frame.
  const legForward: V3 = rotY([0, 0, -1], anchor.figureYawDeg)
  const legAcross:  V3 = rotY([1, 0, 0], anchor.figureYawDeg)
  const hipYawDeg = anchor.figureYawDeg + anchor.bodyRotationDeg
  const forward: V3 = rotY([0, 0, -1], hipYawDeg)
  const acrossLevel: V3 = rotY([1, 0, 0], hipYawDeg)
  // Pelvic roll: tilt the hip-across vector around forward. Positive lifts
  // the player's right hip (weight onto left leg). Legs still attach to the
  // rolled across so pelvic tilt propagates to leg positions, but the torso
  // stays vertical — bending is done independently via torsoSideBendDeg.
  const across: V3 = anchor.pelvicRollDeg !== 0
    ? normalize(rotAroundAxis(acrossLevel, forward, anchor.pelvicRollDeg))
    : acrossLevel
  // Shoulder frame: hip yaw + an independent corpus rotation. Shoulders and
  // arms live here. When shoulderRotationDeg = 0 the two frames coincide.
  const shoulderForward: V3 = rotY([0, 0, -1], hipYawDeg + anchor.shoulderRotationDeg)
  const shoulderAcross:  V3 = rotY([1, 0, 0], hipYawDeg + anchor.shoulderRotationDeg)

  // Torso tilt (single segment): rotate the torso-up vector forward around
  // the hip line. The whole spine rotates rigidly — hips are the pivot,
  // shoulders translate forward by sin(tilt)*torso. Then apply side-bend
  // around the body-forward axis so a relaxed/imbalanced posture can lean
  // sideways without tipping the whole body.
  const torsoUpFromTilt: V3 = normalize(rotAroundAxis([0, -1, 0], acrossLevel, anchor.torsoTiltDeg))
  const torsoUp: V3 = anchor.torsoSideBendDeg !== 0
    ? normalize(rotAroundAxis(torsoUpFromTilt, forward, anchor.torsoSideBendDeg))
    : torsoUpFromTilt
  const torsoDown: V3 = scale(torsoUp, -1)

  // Automatic tilt compensation ────────────────────────────────────────────
  // Forward tilt lets the knees bend slightly so the legs don't lock
  // straight when the torso leans. TILT_TO_HIP_BACK is 0 — balance comes
  // from the anchor values (thighForward, stanceWidth), not a synthetic shift.
  const tiltRad = deg(anchor.torsoTiltDeg)
  const hipBackShift = Math.sin(tiltRad) * B.torso * TILT_TO_HIP_BACK
  const backward: V3 = scale(forward, -1)
  const hipMid: V3 = add(
    [anchor.hipMidX, anchor.hipMidY, 0],
    scale(backward, hipBackShift)
  )
  const clampKnee = (k: number) => Math.max(EFFECTIVE_KNEE_MIN_DEG, Math.min(180, k))
  const kneeRoom = (kneeDeg: number) => Math.max(0, (kneeDeg - 90) / 90)
  const kneeCompFor = (kneeDeg: number) =>
    Math.abs(anchor.torsoTiltDeg) * TILT_TO_KNEE_BEND * kneeRoom(kneeDeg)
  const effLeftKnee  = clampKnee(anchor.leftKneeAngleDeg  - kneeCompFor(anchor.leftKneeAngleDeg))
  const effRightKnee = clampKnee(anchor.rightKneeAngleDeg - kneeCompFor(anchor.rightKneeAngleDeg))

  // Hips (23 L, 24 R) ───────────────────────────────────────────────────────
  const lHip: V3 = add(hipMid, scale(across,  B.hipWidth / 2))
  const rHip: V3 = add(hipMid, scale(across, -B.hipWidth / 2))

  // Shoulders (11 L, 12 R) ──────────────────────────────────────────────────
  // Single-segment torso: shoulderMid sits one torso length above hipMid
  // along torsoUp. The shoulder LINE is oriented by `shoulderAcross`, which
  // carries the corpus rotation — so twisting the trunk pivots L/R shoulder
  // around shoulderMid without moving the spine base.
  // Shoulder shrug raises/lowers the shoulder line along torsoUp without
  // stretching the spine in the FK chain (head still rides on spineUp, so
  // the head lifts with the shoulders — matches physical shrug motion).
  const shoulderMid: V3 = add(hipMid, scale(torsoUp, B.torso + anchor.shoulderShrugNorm))
  const lShoulder: V3 = add(shoulderMid, scale(shoulderAcross,  B.shoulderWidth / 2))
  const rShoulder: V3 = add(shoulderMid, scale(shoulderAcross, -B.shoulderWidth / 2))

  return {
    legForward, legAcross,
    forward, acrossLevel, across,
    shoulderForward, shoulderAcross,
    torsoUp, torsoDown,
    hipMid,
    effLeftKnee, effRightKnee,
    lHip, rHip,
    lShoulder, rShoulder,
    shoulderMid,
  }
}

/**
 * Write head/face landmarks (indices 0–10: NOSE, eyes, ears, mouth) into
 * `out`. Logic extracted verbatim from skeletonReconstructor.ts lines 205–220.
 */
export function buildHeadFace(
  shoulderMid: V3,
  torsoUp: V3,
  torsoDown: V3,
  shoulderAcross: V3,
  headToShoulder: number,
  out: Landmark[],
): void {
  // Head / face (0-10) ──────────────────────────────────────────────────────
  // Head is part of the trunk — rides on torsoUp and follows shoulderAcross
  // for the L/R face landmarks so a twisted trunk turns the head with it.
  const nose: V3 = add(shoulderMid, scale(torsoUp, headToShoulder * 0.75))
  const mouthOffset: V3 = scale(torsoDown, 0.02)
  out[LM.NOSE]        = mkLm(LM.NOSE,        nose)
  out[LM.L_EYE_INNER] = mkLm(LM.L_EYE_INNER, add(nose, scale(shoulderAcross,  0.015)))
  out[LM.L_EYE]       = mkLm(LM.L_EYE,       add(nose, scale(shoulderAcross,  0.030)))
  out[LM.L_EYE_OUTER] = mkLm(LM.L_EYE_OUTER, add(nose, scale(shoulderAcross,  0.045)))
  out[LM.R_EYE_INNER] = mkLm(LM.R_EYE_INNER, add(nose, scale(shoulderAcross, -0.015)))
  out[LM.R_EYE]       = mkLm(LM.R_EYE,       add(nose, scale(shoulderAcross, -0.030)))
  out[LM.R_EYE_OUTER] = mkLm(LM.R_EYE_OUTER, add(nose, scale(shoulderAcross, -0.045)))
  out[LM.L_EAR]       = mkLm(LM.L_EAR,       add(nose, scale(shoulderAcross,  0.06)))
  out[LM.R_EAR]       = mkLm(LM.R_EAR,       add(nose, scale(shoulderAcross, -0.06)))
  out[LM.MOUTH_L]     = mkLm(LM.MOUTH_L,     add(add(nose, mouthOffset), scale(shoulderAcross,  0.015)))
  out[LM.MOUTH_R]     = mkLm(LM.MOUTH_R,     add(add(nose, mouthOffset), scale(shoulderAcross, -0.015)))
}
