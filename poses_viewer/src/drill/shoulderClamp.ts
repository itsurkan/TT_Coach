/**
 * Right-shoulder anatomical reachability envelope.
 *
 * The shoulder joint cannot physically reach every (flex, abd) combination
 * within the slider ranges. This module encodes a linear bound
 *   abd_min(flex) = piecewise-linear interp of two user-sampled points:
 *     flex=100  → abd_min=-40   (at extreme cross-body reach, flex is capped)
 *     flex=180  → abd_min= 70   (arm straight up requires strong abduction)
 *
 * Used by `AnchorSliders` to clamp the two right-shoulder sliders at
 * commit time (Policy P1 — last-touched slider wins, the other yields).
 * Not used by the reconstructor or the extractor: anchors may be rendered
 * or extracted unclamped; only editor authoring is constrained.
 */

export const RIGHT_SHOULDER_FLEX_KNEE = 100
export const RIGHT_SHOULDER_FLEX_CEIL = 180
export const RIGHT_SHOULDER_ABD_AT_KNEE = -40
export const RIGHT_SHOULDER_ABD_AT_CEIL = 70

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
