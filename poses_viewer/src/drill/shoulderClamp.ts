/**
 * Right-shoulder anatomical reachability envelope.
 *
 * The shoulder joint cannot physically reach every (flex, abd) combination
 * within the slider ranges. This module encodes a linear bound
 *   abd_min(flex) = piecewise-linear interp of two user-sampled points:
 *     flex=100  → abd_min=-40   (at extreme cross-body reach, flex is capped)
 *     flex=180  → abd_min= 70   (arm straight up requires strong abduction)
 *
 * Additionally, shoulder elevation (= acos(cos(flex)·cos(abd))) is capped at
 * MAX_ELEVATION_DEG (120°). When both flex and abd are large their combined
 * elevation can exceed 120° even though each individual slider is in range.
 * clampRightShoulderElevation enforces this ceiling using the polar round-trip
 * (Policy P1 — last-touched slider wins, the other yields).
 *
 * Used by `AnchorSliders` to clamp the two right-shoulder sliders at
 * commit time. Not used by the reconstructor or the extractor: anchors may be
 * rendered or extracted unclamped; only editor authoring is constrained.
 */

export const RIGHT_SHOULDER_FLEX_KNEE = 100
export const RIGHT_SHOULDER_FLEX_CEIL = 180
export const RIGHT_SHOULDER_ABD_AT_KNEE = -40
export const RIGHT_SHOULDER_ABD_AT_CEIL = 70
export const RIGHT_SHOULDER_MAX_ELEVATION_DEG = 120

export type ShoulderActiveKey =
  | 'rightShoulderAngleDeg'
  | 'rightShoulderAbductionDeg'

export function rightShoulderAbdMin(flex: number): number {
  if (flex <= RIGHT_SHOULDER_FLEX_KNEE) return RIGHT_SHOULDER_ABD_AT_KNEE
  if (flex >= RIGHT_SHOULDER_FLEX_CEIL) return RIGHT_SHOULDER_ABD_AT_CEIL
  const t = (flex - RIGHT_SHOULDER_FLEX_KNEE) /
            (RIGHT_SHOULDER_FLEX_CEIL - RIGHT_SHOULDER_FLEX_KNEE)
  return RIGHT_SHOULDER_ABD_AT_KNEE +
         t * (RIGHT_SHOULDER_ABD_AT_CEIL - RIGHT_SHOULDER_ABD_AT_KNEE)
}

export function rightShoulderFlexMax(abd: number): number {
  if (abd >= RIGHT_SHOULDER_ABD_AT_CEIL) return RIGHT_SHOULDER_FLEX_CEIL
  if (abd <= RIGHT_SHOULDER_ABD_AT_KNEE) return RIGHT_SHOULDER_FLEX_KNEE
  const t = (abd - RIGHT_SHOULDER_ABD_AT_KNEE) /
            (RIGHT_SHOULDER_ABD_AT_CEIL - RIGHT_SHOULDER_ABD_AT_KNEE)
  return RIGHT_SHOULDER_FLEX_KNEE +
         t * (RIGHT_SHOULDER_FLEX_CEIL - RIGHT_SHOULDER_FLEX_KNEE)
}

export function clampRightShoulder(
  flex: number,
  abd: number,
  activeKey: ShoulderActiveKey,
): { flex: number; abd: number } {
  const minAbd = rightShoulderAbdMin(flex)
  if (abd >= minAbd) return { flex, abd }
  if (activeKey === 'rightShoulderAngleDeg') {
    return { flex, abd: minAbd }
  }
  return { flex: rightShoulderFlexMax(abd), abd }
}

const DEG = Math.PI / 180
const RAD = 180 / Math.PI

/**
 * Enforce elevation = acos(cos(flex)·cos(abd)) ≤ MAX_ELEVATION_DEG.
 * Uses the polar round-trip: compute current plane, cap elevation at 120°,
 * then reconstruct the (flex, abd) pair that sits on the ceiling.
 * Policy P1: the active param is kept, the other yields.
 */
export function clampRightShoulderElevation(
  flex: number,
  abd: number,
  activeKey: ShoulderActiveKey,
): { flex: number; abd: number } {
  const dDown = Math.cos(flex * DEG) * Math.cos(abd * DEG)
  const elevation = Math.acos(Math.max(-1, Math.min(1, dDown))) * RAD
  if (elevation <= RIGHT_SHOULDER_MAX_ELEVATION_DEG) return { flex, abd }

  // Compute current plane angle (atan2 of across vs forward components).
  const dForward = Math.sin(flex * DEG) * Math.cos(abd * DEG)
  const dAcross = Math.sin(abd * DEG)
  const plane = Math.atan2(dAcross, dForward) * RAD

  // Project back onto the elevation ceiling at the same plane.
  const eRad = RIGHT_SHOULDER_MAX_ELEVATION_DEG * DEG
  const pRad = plane * DEG
  const sinE = Math.sin(eRad)
  const cosE = Math.cos(eRad)
  const fwd = Math.cos(pRad) * sinE
  const EPS = 1e-12
  const flexCapped =
    Math.abs(fwd) < EPS && Math.abs(cosE) < EPS
      ? 0
      : Math.atan2(fwd, cosE) * RAD
  const abdCapped = Math.asin(Math.max(-1, Math.min(1, Math.sin(pRad) * sinE))) * RAD

  if (activeKey === 'rightShoulderAngleDeg') {
    // Active = flex → keep flex, yield abd back to the capped value.
    return { flex, abd: abdCapped }
  }
  // Active = abd → keep abd, yield flex back to the capped value.
  return { flex: flexCapped, abd }
}
