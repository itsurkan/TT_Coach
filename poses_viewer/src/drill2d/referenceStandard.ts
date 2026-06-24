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

/**
 * Phase type is imported TYPE-ONLY to avoid a runtime import cycle between
 * referenceStandard.ts and drillMetrics.ts (drillMetrics imports angle
 * functions; referenceStandard is imported by decideRepCues / metricPrecision
 * which are downstream consumers — a value import would form a cycle). The
 * `import type` erases fully at compile time.
 */
import type { Phase } from './drillMetrics'

/** The metric keys, duplicated from drillMetrics to avoid an import cycle. */
export const METRIC_KEYS = [
  'elbow_angle',
  'shoulder_angle',
  'knee_bend',
  'torso_lean',
  'shoulder_tilt',
  'hip_flexion',
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
    // EXP-8 (2026-06-12 research pass): NO TT study measures trunk-from-vertical at
    // contact. Nearest same-convention anchor = tennis-serve trophy 25±7° from
    // vertical (PMC11260724/PMC3445225); TT contact is MORE flexed. Skilled players
    // in our own footage sit at 33–39°. Old 5–25° falsely flagged normal attacking
    // lean on every rep, so widened to 15–40° (centred ~28). Still provisional: 2D
    // lean is inflated by axial rotation/camera-yaw, so re-tune on protocol footage.
    torso_lean: {
      lo: 15, hi: 40, evidence: 'coach_opinion',
      source: 'research-informed (tennis-serve 25±7° anchor + own footage 33–39°); no direct TT measure; provisional',
    },
    // EXP-8: nearest proxy = lumbar lateral-bending 20–28° in 3D mocap (FBioE 2023,
    // 1185177); maps indirectly to shoulder-line tilt. Nudged upper 20→25 so the
    // playing-shoulder drop of an aggressive FH topspin isn't over-flagged.
    shoulder_tilt: {
      lo: 0, hi: 25, evidence: 'coach_opinion',
      source: 'research-informed (lumbar lateral-bending 20–28°, FBioE 2023 1185177, indirect); provisional',
    },
    // Hip flexion (shoulder→hip→knee, vertex at hip): 180° = upright stance. At the
    // load/contact phase of a forehand drive, the player sits into a forward hip hinge
    // — approximately 130–160° based on own footage and coach observation. No direct
    // TT biomechanics study measures this interior angle; PROVISIONAL.
    hip_flexion: {
      lo: 130, hi: 165, evidence: 'coach_opinion',
      source: 'coach observation + own footage; no measured TT source; PROVISIONAL',
    },
  },
}

/** Registry for the (currently single) drill-type selector. */
export const REFERENCE_STANDARDS: Record<string, ReferenceStandard> = {
  forehand_drive: FOREHAND_DRIVE_STANDARD,
}

// ---------------------------------------------------------------------------
// Per-phase reference ranges
// ---------------------------------------------------------------------------

/**
 * Per-phase ideal ranges for the five pattern metrics (all graded per-phase,
 * never at the single contact instant by decideRepCues).
 *
 * Movement-bracketing rule (mirrors METRIC_PHASES in drillMetrics.ts):
 *   - Arm metrics (elbow, shoulder): graded at backswing + followthrough.
 *     Contact is the noisiest instant for both — RTMPose mislocates the fast
 *     forearm and the shoulder sweeps through its full range there.
 *   - Legs/trunk (knee, hip, torso): graded at backswing + contact (load → drive).
 *     Followthrough is excluded — recovery phase, rotation-corrupted in 2D, no ideal.
 *
 * Unmeasured / provisional bands:
 *   - shoulder_angle.backswing: coach-opinion only, no TT study measures take-back
 *     shoulder elevation at this instant. Primary tuning dial.
 *   - torso_lean.backswing + torso_lean.contact: coach-opinion. 2D lean is inflated
 *     by axial rotation/camera-yaw; re-tune on protocol footage.
 *
 * Interior-angle convention throughout: 180° = fully straight.
 */
export const PER_PHASE_RANGES: Partial<Record<MetricKey, Partial<Record<Phase, ReferenceRange>>>> = {
  // ---- ARM metrics: backswing + followthrough ----
  elbow_angle: {
    backswing: {
      lo: 145, hi: 175,
      evidence: 'coach_opinion',
      source: 'PROVISIONAL coach-opinion: near-straight extended take-back (andrii ~152–167 sits in band). <145 too bent → too_low; >175 locked → too_high. Primary tuning dial.',
    },
    followthrough: {
      lo: 60, hi: 85,
      evidence: 'coach_opinion',
      source: 'PROVISIONAL coach-opinion: elbow FOLDS as the arm wraps up/across at the finish. >85 too straight / did not wrap → too_high («доводи лікоть»); <60 over-folded → too_low. Unmeasured on our footage; primary tuning dial.',
    },
  },
  shoulder_angle: {
    backswing: {
      lo: 20, hi: 60,
      evidence: 'coach_opinion',
      source: 'PROVISIONAL coach-opinion: arm hangs low/back at the take-back (small hip–shoulder–elbow elevation); <20 collapsed → too_low; >60 already lifting early → too_high. Unmeasured; tuning dial.',
    },
    followthrough: {
      lo: 80, hi: 130,
      evidence: 'coach_opinion',
      source: 'arm sweeps high at finish (Bańkosz shoulder flex peaks ~97°); interior-angle mapping UNVERIFIED; PROVISIONAL',
    },
  },
  // ---- LEGS/TRUNK metrics: backswing + contact; followthrough excluded ----
  knee_bend: {
    backswing: {
      lo: 110, hi: 130,
      evidence: 'measured',
      source: 'Bańkosz & Winiarski JSSM 2020, knee flex ~63° at backswing → interior ~117°; loaded deep bend is the ideal, straightening above hi flags «stay down»; PROVISIONAL, re-tune on protocol footage',
    },
    contact: {
      lo: 110, hi: 130,
      evidence: 'measured',
      source: 'Bańkosz 2020, knee flex ~66° at contact (deepest) → interior ~114°; deep bend is the ideal, getting up above hi flags «stay down / don\'t get up»; PROVISIONAL',
    },
  },
  hip_flexion: {
    backswing: {
      lo: 115, hi: 160,
      evidence: 'coach_opinion',
      source: 'Bańkosz ~22° vs PeerJ 2021 ~63° flex at backswing → interior ~158°/~117°; sources DISAGREE, seeded wide; robust signal is the hinge DIRECTION not degrees; PROVISIONAL',
    },
    contact: {
      lo: 120, hi: 165,
      evidence: 'coach_opinion',
      source: 'hip extends slightly toward contact; PROVISIONAL, sources disagree',
    },
  },
  torso_lean: {
    backswing: {
      lo: 25, hi: 45,
      evidence: 'coach_opinion',
      source: 'PROVISIONAL coach-opinion: forward lean already established at take-back; cleanest 2D instant (least axial rotation). Unmeasured; tuning dial.',
    },
    contact: {
      lo: 25, hi: 45,
      evidence: 'coach_opinion',
      source: 'PROVISIONAL coach-opinion: forward attacking lean at contact (own footage 33–39°); 2D lean inflated by axial rotation, re-tune on protocol footage; tuning dial.',
    },
  },
}

/**
 * Look up the per-phase ideal range for a given metric and phase.
 *
 * Returns `null` when:
 *   - the metric has no per-phase entry (e.g. `shoulder_tilt`);
 *   - the metric has entries but not for the requested phase (e.g. `elbow_angle`
 *     at `contact` — arm metrics are graded at backswing/followthrough only;
 *     legs/trunk metrics at backswing/contact only, never followthrough).
 *
 * Table coloring uses this to decide whether to render a phase cell
 * with a good/warn/bad background or leave it uncolored.
 */
export function perPhaseRange(metricKey: string, phase: Phase): ReferenceRange | null {
  const metricEntry = PER_PHASE_RANGES[metricKey as MetricKey]
  if (metricEntry === undefined) return null
  return metricEntry[phase] ?? null
}
