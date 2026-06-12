/**
 * External IDEAL ranges for the five in-plane metrics — the "how close to a
 * standard technique" reference this tool grades against. This is a DELIBERATE
 * departure from the project's personal-baseline positioning (spec decision #2):
 * correct for this effectiveness simulator, not a change of product direction.
 *
 * PROVISIONAL. The deep-research pass (2026-06-12) found measured biomechanics
 * values only for elbow/shoulder/knee, in clinical flexion convention (0° =
 * straight) and at slightly different stroke instants; they are converted here to
 * our interior-angle convention (180° = straight) and widened to bands. Torso lean
 * and shoulder tilt have NO measured source (studies explicitly did not measure
 * them) and are coach-opinion. Re-verify after the research limit resets (plan
 * Task 12). The UI must surface the `evidence` tag so these never read as gospel.
 */

/** The five metric keys, duplicated from drillMetrics to avoid an import cycle. */
export const METRIC_KEYS = [
  'elbow_angle',
  'shoulder_angle',
  'knee_bend',
  'torso_lean',
  'shoulder_tilt',
] as const

export type MetricKey = (typeof METRIC_KEYS)[number]

export interface ReferenceRange {
  /** Inclusive lower edge of the ideal band, in degrees. */
  lo: number
  /** Inclusive upper edge of the ideal band, in degrees. */
  hi: number
  /** 'measured' = literature-derived (converted); 'coach_opinion' = heuristic, no measured source. */
  evidence: 'measured' | 'coach_opinion'
  /** Short provenance note for UI tooltips. */
  source: string
}

export interface ReferenceStandard {
  drillType: string
  ranges: Record<string, ReferenceRange>
}

export const FOREHAND_DRIVE_STANDARD: ReferenceStandard = {
  drillType: 'forehand_drive',
  ranges: {
    // Clinical contact flexion ~44° (PMC7238326) → interior ~136°; forward-phase
    // flexion ~60° (JSSM 24-2-311) → interior ~120°. Band spans both instants.
    elbow_angle: {
      lo: 115, hi: 150, evidence: 'measured',
      source: 'PMC7238326 (IMU, 7 elite) + JSSM 24-2-311; clinical flexion→interior, unverified',
    },
    // Shoulder elevation 26° at contact → 72° at forward-phase end (PMC7238326,
    // JSSM). Our interior hip–shoulder–elbow is not identical to clinical flexion.
    shoulder_angle: {
      lo: 30, hi: 75, evidence: 'measured',
      source: 'PMC7238326 + JSSM 24-2-311; clinical shoulder flexion, weak mapping',
    },
    // Knee flexion 47–53° at contact → up to 65–76° elite (PMC7238326, PMC10177840)
    // → interior ~104–133°.
    knee_bend: {
      lo: 110, hi: 145, evidence: 'measured',
      source: 'PMC7238326 + PMC10177840; clinical flexion→interior, unverified',
    },
    // No measured source — coaching consensus is a slight forward lean.
    torso_lean: {
      lo: 5, hi: 25, evidence: 'coach_opinion',
      source: 'coach heuristic; no biomechanics source measured trunk lean',
    },
    // No measured source — playing shoulder drops slightly for FH topspin.
    shoulder_tilt: {
      lo: 0, hi: 20, evidence: 'coach_opinion',
      source: 'coach heuristic; no biomechanics source measured shoulder-line tilt',
    },
  },
}

/** Registry for the (currently single) drill-type selector. */
export const REFERENCE_STANDARDS: Record<string, ReferenceStandard> = {
  forehand_drive: FOREHAND_DRIVE_STANDARD,
}
