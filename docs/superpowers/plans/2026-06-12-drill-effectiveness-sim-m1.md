# Drill Effectiveness Simulator — M1 (Metrics + Feedback) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the `poses_viewer` `#/strokes` page from stroke-counting (M0, done) into the full Drill Effectiveness Simulator: per-rep in-plane metrics, comparison against an external **ideal** standard, and cadenced spoken feedback during playback.

**Architecture:** Add the measurement half of the pipeline as 1:1 TS ports of the Kotlin `shared/` classes (`angles2d`, `cameraYaw`, `drillMetrics` — golden-parity to Kotlin), plus a *deliberately divergent* feedback half (`referenceStandard`, `feedbackEngine`, `messageCatalog`, `cadencePolicy`, `analyzeDrill`) that compares metrics to a fixed external ideal range instead of the personal baseline (spec decision #2). The `#/strokes` page is extended in place — no new route — with a results table, metric toggles, a drill-type selector, and `speechSynthesis` feedback playback.

**Tech Stack:** TypeScript, React 18, Vite 6, Tailwind v4, vitest 4 (all in `poses_viewer/`). Zero new dependencies — `window.speechSynthesis` is a browser built-in.

---

## Context every task assumes

**Spec:** [docs/superpowers/specs/2026-06-11-drill-effectiveness-sim-design.md](../specs/2026-06-11-drill-effectiveness-sim-design.md). Read it once before starting.

**What M0 already shipped** (do not re-implement — import it):
- `poses_viewer/src/drill2d/` mirror: `types.ts` (Keypoint2D, PoseFrame2D, Stroke2D, Handedness, Coco17), `facing.ts` (`scored`, `facingSign`, `DEFAULT_MIN_SCORE = 0.3`), `geometry.ts` (`xScaleFor`, `MAX_YAW_DEG = 60`), `median.ts` (`median`), `parsePoseV2.ts` (`PoseSequence2D`), `strokeDetector2d.ts` (`detectStrokes`, `DETECTOR_DEFAULTS`, `StrokeDetectorOptions`), `forwardStrokeFilter.ts` (`filterForwardStrokes`), `repFilter.ts` (`filterReps`), `countStrokes.ts` (`countStrokes` — pipeline detect → forward → rep).
- `poses_viewer/src/components/StrokesPage.tsx` + `StrokeTimeline.tsx` — the `#/strokes` route.
- Golden parity: `drill2d/__tests__/golden.test.ts` — **andrii_1_rtm 23 raw / 15 forward / 15 reps**, video_4_rtm 18/12/9. Fixtures read from `shared/src/commonTest/resources/fixtures/` via repo-relative path (`golden.test.ts` shows the path pattern — copy it).

**Two hard rules carried from M0:**
1. **Binding fix-flow rule (Kotlin is source of truth).** The measurement modules (`angles2d`, `cameraYaw`, `drillMetrics`) are 1:1 ports — they must produce the same numbers as Kotlin. If a port and Kotlin disagree on a fixture, **the Kotlin is correct and the TS is the bug**; never "fix" by editing Kotlin to match TS without a separate shared-module change.
2. **The feedback half deliberately diverges.** `feedbackEngine`/`referenceStandard`/`messageCatalog` compare against an *external ideal range*, NOT the personal baseline the Kotlin `DrillFeedbackEngine` uses. **Do not** try to golden-test the feedback engine against Kotlin output — there is no Kotlin counterpart. This divergence is intentional (spec decision #2).

**The five in-plane metrics and their conventions** (from `AngleCalculations2D`):
| key | meaning | convention |
|---|---|---|
| `elbow_angle` | interior shoulder–elbow–wrist | 180° = straight arm |
| `shoulder_angle` | interior hip–shoulder–elbow (upper arm vs torso) | larger = arm further from torso |
| `knee_bend` | interior hip–knee–ankle | 180° = straight leg |
| `torso_lean` | hip-mid→shoulder-mid line vs vertical, facing-normalized | 0 = upright, + = forward lean |
| `shoulder_tilt` | shoulder line vs horizon, folded to (−90°, 90°] | 0 = level |

**xScale gotcha (CLAUDE.md):** schema v2 normalizes x by width, y by height. Every x-delta is multiplied by `xScale = aspectRatio / cos(yaw)` before trig. Detection runs on plain `aspectRatio` (yaw 0); per-rep metrics run on the yaw-corrected `xScale`. Synthetic tests use `xScale = 1`.

**Reference-data reality (read before Task 0):** the deep-research pass (2026-06-12) came back thin. Measured biomechanics values exist for elbow/shoulder/knee but in *clinical flexion convention* (0° = straight) at slightly different stroke instants, and **torso lean and shoulder tilt have no measured source at all** (multiple studies explicitly did not measure them). The ranges in Task 0 are therefore **provisional**, converted to our interior-angle convention, and each is tagged `measured` (literature-derived) or `coach_opinion` (heuristic). A follow-up to re-verify after the research rate-limit resets is recorded in Task 12.

---

## File structure

New files, all under `poses_viewer/src/drill2d/` unless noted:

| File | Responsibility | Kotlin mirror? |
|---|---|---|
| `referenceStandard.ts` | external ideal ranges per metric per drill, with evidence tags | NEW (no mirror) |
| `angles2d.ts` | the 5 in-plane angle functions + `angleDeg` | `AngleCalculations2D` (1:1) |
| `cameraYaw.ts` | per-rep `|yaw|` from shoulder foreshortening | `CameraAngleEstimator` (1:1) |
| `sanityBounds.ts` | anatomical drop-bounds (drop glitches, never coach) | `SanityBounds` (1:1) |
| `metricPrecision.ts` | precise-degrees vs qualitative policy | `MetricPrecision` (1:1) |
| `drillMetrics.ts` | metric keys + `extractAtPeak` (±70 ms median window) | `DrillMetrics` (1:1) |
| `feedbackCue.ts` | `FeedbackCue` + `CueDirection` types (range-based) | adapted shape |
| `feedbackEngine.ts` | metric vs ideal range → `FeedbackCue[]` | NEW semantics |
| `messageCatalog.ts` | cue → EN message (vs-ideal wording) | adapted from `FeedbackMessageCatalog` |
| `cadencePolicy.ts` | 3–5 s gating of spoken feedback | `FeedbackCadencePolicy` (1:1) |
| `analyzeDrill.ts` | orchestrator → `DrillAnalysisReport` | adapted from `ForehandDriveDrillAnalyzer` |
| `components/StrokesPage.tsx` | extend: results table, toggles, drill selector, spoken playback | — |

Test files live in `poses_viewer/src/drill2d/__tests__/`.

---

### Task 0: Reference standard (provisional ideal ranges)

**Files:**
- Create: `poses_viewer/src/drill2d/referenceStandard.ts`
- Test: `poses_viewer/src/drill2d/__tests__/referenceStandard.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// poses_viewer/src/drill2d/__tests__/referenceStandard.test.ts
import { describe, expect, it } from 'vitest'
import { FOREHAND_DRIVE_STANDARD, METRIC_KEYS } from '../referenceStandard'

describe('referenceStandard', () => {
  it('covers all five in-plane metrics', () => {
    expect(Object.keys(FOREHAND_DRIVE_STANDARD.ranges).sort()).toEqual([...METRIC_KEYS].sort())
  })

  it('every range is well-formed (lo < hi, tagged with evidence + source)', () => {
    for (const [key, r] of Object.entries(FOREHAND_DRIVE_STANDARD.ranges)) {
      expect(r.lo, key).toBeLessThan(r.hi)
      expect(['measured', 'coach_opinion']).toContain(r.evidence)
      expect(r.source.length, key).toBeGreaterThan(0)
    }
  })

  it('flags torso lean and shoulder tilt as coach-opinion (no measured source)', () => {
    expect(FOREHAND_DRIVE_STANDARD.ranges.torso_lean.evidence).toBe('coach_opinion')
    expect(FOREHAND_DRIVE_STANDARD.ranges.shoulder_tilt.evidence).toBe('coach_opinion')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/referenceStandard.test.ts`
Expected: FAIL — cannot resolve `../referenceStandard`.

- [ ] **Step 3: Write the implementation**

```typescript
// poses_viewer/src/drill2d/referenceStandard.ts
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/referenceStandard.test.ts`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/referenceStandard.ts poses_viewer/src/drill2d/__tests__/referenceStandard.test.ts
git commit -m "feat(viewer): provisional ideal ranges for forehand-drive metrics (M1)"
```

---

### Task 1: angles2d — the five in-plane angle functions

**Files:**
- Modify: `poses_viewer/src/drill2d/types.ts` (add Coco17 joint helpers)
- Create: `poses_viewer/src/drill2d/angles2d.ts`
- Test: `poses_viewer/src/drill2d/__tests__/angles2d.test.ts`

- [ ] **Step 1: Extend Coco17 with the per-joint helpers angles2d needs**

In `poses_viewer/src/drill2d/types.ts`, the `Coco17` object currently exposes only `wrist(h)`. Add the other handedness helpers. Replace the `wrist(h)` block with:

```typescript
  shoulder(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_SHOULDER : Coco17.LEFT_SHOULDER
  },
  elbow(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_ELBOW : Coco17.LEFT_ELBOW
  },
  wrist(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_WRIST : Coco17.LEFT_WRIST
  },
  hip(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_HIP : Coco17.LEFT_HIP
  },
  knee(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_KNEE : Coco17.LEFT_KNEE
  },
  ankle(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_ANKLE : Coco17.LEFT_ANKLE
  },
```

- [ ] **Step 2: Write the failing test**

```typescript
// poses_viewer/src/drill2d/__tests__/angles2d.test.ts
import { describe, expect, it } from 'vitest'
import { angleDeg, elbowAngle, shoulderTilt, torsoLean } from '../angles2d'
import { Coco17, Keypoint2D } from '../types'

const K = (x: number, y: number, score = 1): Keypoint2D => ({ x, y, score })

/** Build a 17-length keypoint list, all (0,0,0) unless overridden. */
function pose(overrides: Record<number, Keypoint2D>): Keypoint2D[] {
  const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0, y: 0, score: 0 }))
  for (const [i, k] of Object.entries(overrides)) kp[Number(i)] = k
  return kp
}

describe('angleDeg', () => {
  it('is 90° for a right angle (xScale = 1)', () => {
    // b at origin, a straight up, c straight right
    expect(angleDeg(K(0, -1), K(0, 0), K(1, 0), 1)).toBeCloseTo(90, 4)
  })
  it('is 180° for a straight line', () => {
    expect(angleDeg(K(-1, 0), K(0, 0), K(1, 0), 1)).toBeCloseTo(180, 4)
  })
  it('applies xScale to x-deltas before trig', () => {
    // With xScale 2 the horizontal leg doubles, so the right angle is preserved
    expect(angleDeg(K(0, -1), K(0, 0), K(1, 0), 2)).toBeCloseTo(90, 4)
    // A 45° vector (dx=1,dy=-1): xScale=2 doubles the x-leg → atan2(1, 2) = 26.57° from +x
    expect(angleDeg(K(1, -1), K(0, 0), K(1, 0), 2)).toBeCloseTo(26.57, 1)
  })
})

describe('elbowAngle', () => {
  it('returns 180° for a straight right arm', () => {
    const kp = pose({
      [Coco17.RIGHT_SHOULDER]: K(0, 0),
      [Coco17.RIGHT_ELBOW]: K(1, 0),
      [Coco17.RIGHT_WRIST]: K(2, 0),
    })
    expect(elbowAngle(kp, 'right', 1)).toBeCloseTo(180, 4)
  })
  it('returns null when a required keypoint is below the score gate', () => {
    const kp = pose({
      [Coco17.RIGHT_SHOULDER]: K(0, 0),
      [Coco17.RIGHT_ELBOW]: K(1, 0, 0.1), // gated out
      [Coco17.RIGHT_WRIST]: K(2, 0),
    })
    expect(elbowAngle(kp, 'right', 1)).toBeNull()
  })
})

describe('shoulderTilt', () => {
  it('is 0 for level shoulders', () => {
    const kp = pose({ [Coco17.LEFT_SHOULDER]: K(0, 0), [Coco17.RIGHT_SHOULDER]: K(1, 0) })
    expect(shoulderTilt(kp, 1)).toBeCloseTo(0, 4)
  })
})

describe('torsoLean', () => {
  it('returns null when facing is indeterminate (no head keypoints)', () => {
    const kp = pose({
      [Coco17.LEFT_SHOULDER]: K(0, 0), [Coco17.RIGHT_SHOULDER]: K(1, 0),
      [Coco17.LEFT_HIP]: K(0, 1), [Coco17.RIGHT_HIP]: K(1, 1),
    })
    expect(torsoLean(kp, 1)).toBeNull()
  })
  it('is signed positive when leaning toward the facing direction', () => {
    // facing +x (nose right of shoulder-mid); shoulders shifted +x above hips → forward lean
    const kp = pose({
      [Coco17.NOSE]: K(1.0, -1),
      [Coco17.LEFT_SHOULDER]: K(0.3, 0), [Coco17.RIGHT_SHOULDER]: K(0.7, 0),
      [Coco17.LEFT_HIP]: K(0, 1), [Coco17.RIGHT_HIP]: K(0.4, 1),
    })
    const lean = torsoLean(kp, 1)
    expect(lean).not.toBeNull()
    expect(lean as number).toBeGreaterThan(0)
  })
})
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/angles2d.test.ts`
Expected: FAIL — cannot resolve `../angles2d`.

- [ ] **Step 4: Write the implementation**

```typescript
// poses_viewer/src/drill2d/angles2d.ts
/**
 * In-plane (2D) joint angles over COCO-17 — 1:1 TS mirror of Kotlin
 * AngleCalculations2D. Kotlin is the source of truth (binding fix-flow rule).
 *
 * All functions: return null when any required keypoint is missing/below minScore
 * (no feedback on low-confidence frames); take xScale = ViewGeometry.xScale and
 * multiply x-deltas by it before any trig (schema v2 normalizes x and y by
 * different axes).
 */
import { Coco17, Handedness, Keypoint2D } from './types'
import { DEFAULT_MIN_SCORE, facingSign, scored } from './facing'

const RAD_TO_DEG = 180 / Math.PI
const EPSILON = 1e-9

/** Inner angle at b formed by b→a and b→c, in degrees [0, 180]. */
export function angleDeg(a: Keypoint2D, b: Keypoint2D, c: Keypoint2D, xScale: number): number {
  const baX = (a.x - b.x) * xScale
  const baY = a.y - b.y
  const bcX = (c.x - b.x) * xScale
  const bcY = c.y - b.y
  const mag = Math.hypot(baX, baY) * Math.hypot(bcX, bcY)
  if (mag < EPSILON) return 0
  const cos = Math.min(1, Math.max(-1, (baX * bcX + baY * bcY) / mag))
  return Math.acos(cos) * RAD_TO_DEG
}

function jointAngle(
  kp: Keypoint2D[], aIdx: number, bIdx: number, cIdx: number, xScale: number, minScore: number,
): number | null {
  const a = scored(kp, aIdx, minScore)
  const b = scored(kp, bIdx, minScore)
  const c = scored(kp, cIdx, minScore)
  if (a === null || b === null || c === null) return null
  // Degenerate geometry (coincident keypoints) is unmeasurable — null, not 0
  // (which downstream would read as "joint folded shut").
  if (Math.hypot((a.x - b.x) * xScale, a.y - b.y) < EPSILON) return null
  if (Math.hypot((c.x - b.x) * xScale, c.y - b.y) < EPSILON) return null
  return angleDeg(a, b, c, xScale)
}

/** Elbow: shoulder–elbow–wrist. 180° = straight arm. */
export function elbowAngle(
  kp: Keypoint2D[], h: Handedness, xScale: number, minScore = DEFAULT_MIN_SCORE,
): number | null {
  return jointAngle(kp, Coco17.shoulder(h), Coco17.elbow(h), Coco17.wrist(h), xScale, minScore)
}

/** Shoulder: hip–shoulder–elbow (upper arm vs torso). */
export function shoulderAngle(
  kp: Keypoint2D[], h: Handedness, xScale: number, minScore = DEFAULT_MIN_SCORE,
): number | null {
  return jointAngle(kp, Coco17.hip(h), Coco17.shoulder(h), Coco17.elbow(h), xScale, minScore)
}

/** Knee bend: hip–knee–ankle. 180° = straight leg. */
export function kneeBend(
  kp: Keypoint2D[], h: Handedness, xScale: number, minScore = DEFAULT_MIN_SCORE,
): number | null {
  return jointAngle(kp, Coco17.hip(h), Coco17.knee(h), Coco17.ankle(h), xScale, minScore)
}

/**
 * Torso lean: signed angle of hip-mid→shoulder-mid from vertical, facing-normalized
 * (0 = upright, + = forward lean toward facing direction). Null when facing is
 * indeterminate (head keypoints gated or dead-centered over the shoulders).
 */
export function torsoLean(
  kp: Keypoint2D[], xScale: number, minScore = DEFAULT_MIN_SCORE,
): number | null {
  const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
  const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
  const lh = scored(kp, Coco17.LEFT_HIP, minScore)
  const rh = scored(kp, Coco17.RIGHT_HIP, minScore)
  if (ls === null || rs === null || lh === null || rh === null) return null
  const hipMidX = (lh.x + rh.x) / 2
  const shoulderMidX = (ls.x + rs.x) / 2
  const facing = facingSign(kp, shoulderMidX, minScore)
  if (facing === null) return null
  const dx = (shoulderMidX - hipMidX) * xScale
  // image y grows downward; negate so "up" is positive
  const dy = -(((ls.y + rs.y) / 2) - ((lh.y + rh.y) / 2))
  if (Math.hypot(dx, dy) < EPSILON) return null
  return Math.atan2(dx * facing, dy) * RAD_TO_DEG
}

/**
 * Shoulder tilt vs horizon, folded to (−90°, 90°]. 0 = level. Magnitude is robust
 * to left/right label swaps; sign follows image x (compare only within a session
 * where the player faces one way — fixed-drill assumption).
 */
export function shoulderTilt(
  kp: Keypoint2D[], xScale: number, minScore = DEFAULT_MIN_SCORE,
): number | null {
  const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
  const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
  if (ls === null || rs === null) return null
  const dx = (rs.x - ls.x) * xScale
  const dy = rs.y - ls.y
  if (Math.hypot(dx, dy) < EPSILON) return null
  let deg = Math.atan2(dy, dx) * RAD_TO_DEG
  if (deg > 90) deg -= 180
  if (deg <= -90) deg += 180
  return deg
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/angles2d.test.ts`
Expected: PASS.

- [ ] **Step 6: Typecheck (Coco17 change touches existing imports)**

Run: `cd poses_viewer && npx tsc -b --noEmit`
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add poses_viewer/src/drill2d/types.ts poses_viewer/src/drill2d/angles2d.ts poses_viewer/src/drill2d/__tests__/angles2d.test.ts
git commit -m "feat(viewer): port AngleCalculations2D → angles2d.ts (M1)"
```

---

### Task 2: cameraYaw — per-rep side-view yaw estimator

**Files:**
- Create: `poses_viewer/src/drill2d/cameraYaw.ts`
- Test: `poses_viewer/src/drill2d/__tests__/cameraYaw.test.ts`

Note: takes raw `aspectRatio` (NOT xScale) — it runs *before* any yaw is known. Returns `|yaw|` only.

- [ ] **Step 1: Write the failing test**

```typescript
// poses_viewer/src/drill2d/__tests__/cameraYaw.test.ts
import { describe, expect, it } from 'vitest'
import { estimateSideViewYawDeg, estimateYawForStroke } from '../cameraYaw'
import { Coco17, Keypoint2D, PoseFrame2D, Stroke2D } from '../types'

const K = (x: number, y: number, score = 1): Keypoint2D => ({ x, y, score })

function frame(idx: number, shoulderSep: number): PoseFrame2D {
  const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0, y: 0, score: 0 }))
  // torso length 1.0 (shoulders at y=0, hips at y=1), shoulders centered on x=0.5
  kp[Coco17.LEFT_SHOULDER] = K(0.5 - shoulderSep / 2, 0)
  kp[Coco17.RIGHT_SHOULDER] = K(0.5 + shoulderSep / 2, 0)
  kp[Coco17.LEFT_HIP] = K(0.5, 1)
  kp[Coco17.RIGHT_HIP] = K(0.5, 1)
  return { frameIndex: idx, timestampMs: idx * 33, keypoints: kp }
}

describe('estimateSideViewYawDeg', () => {
  it('is ~0° in a true profile (shoulders overlap)', () => {
    const frames = [frame(0, 0), frame(1, 0)]
    expect(estimateSideViewYawDeg(frames, 1)).toBeCloseTo(0, 1)
  })
  it('grows as shoulders separate', () => {
    // sep = 0.9 * torso * sin(yaw); with ratio 0.9 and torso 1, sep 0.45 → sin=0.5 → 30°
    const frames = [frame(0, 0.45), frame(1, 0.45)]
    expect(estimateSideViewYawDeg(frames, 1)).toBeCloseTo(30, 0)
  })
  it('returns null when no frame has scored shoulders+hips', () => {
    const empty: PoseFrame2D = { frameIndex: 0, timestampMs: 0, keypoints: [] }
    expect(estimateSideViewYawDeg([empty], 1)).toBeNull()
  })
})

describe('estimateYawForStroke', () => {
  it('uses the pre-stroke lookback window', () => {
    const frames = [frame(0, 0), frame(1, 0), frame(2, 0.45), frame(3, 0.45)]
    const stroke: Stroke2D = { strokeIndex: 0, startFrame: 2, peakFrame: 3, endFrame: 3, peakSpeed: 1 }
    // lookback covers frames 0–1 (profile) → ~0°, not the wide-shouldered swing frames
    expect(estimateYawForStroke(frames, stroke, 1, 33, undefined, 66)).toBeCloseTo(0, 1)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/cameraYaw.test.ts`
Expected: FAIL — cannot resolve `../cameraYaw`.

- [ ] **Step 3: Write the implementation**

```typescript
// poses_viewer/src/drill2d/cameraYaw.ts
/**
 * Side-view yaw from 2D foreshortening — 1:1 TS mirror of Kotlin
 * CameraAngleEstimator. In a true profile the shoulders overlap; the wider they
 * appear relative to torso length, the further the camera is from perpendicular.
 * Returns |yaw| only (foreshortening can't recover the sign; the 1/cos correction
 * is sign-independent). Takes raw aspectRatio — runs BEFORE any yaw is known.
 *
 * Kotlin is the source of truth (binding fix-flow rule). NOTE: on Videos/ footage
 * (not shot to the camera-placement protocol) this saturates toward 90° (L-25) —
 * that is expected, not a port bug; the UI offers a manual override.
 */
import { Coco17, Keypoint2D, PoseFrame2D, Stroke2D } from './types'
import { DEFAULT_MIN_SCORE, scored } from './facing'
import { median } from './median'

/** Biacromial width ≈ 0.9 × shoulder–hip torso length (Drillis & Contini 1966). */
export const SHOULDER_TO_TORSO_RATIO = 0.9
export const DEFAULT_SAMPLE_FRAMES = 30
/** Pre-stroke ready-stance window in ms — fps-independent (L-02). */
export const DEFAULT_LOOKBACK_MS = 1000

const RAD_TO_DEG = 180 / Math.PI
const MIN_TORSO_LEN = 1e-4

function frameYawDeg(kp: Keypoint2D[], aspectRatio: number, minScore: number): number | null {
  const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
  const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
  const lh = scored(kp, Coco17.LEFT_HIP, minScore)
  const rh = scored(kp, Coco17.RIGHT_HIP, minScore)
  if (ls === null || rs === null || lh === null || rh === null) return null

  const torsoLen = Math.hypot(
    ((ls.x + rs.x) / 2 - (lh.x + rh.x) / 2) * aspectRatio,
    (ls.y + rs.y) / 2 - (lh.y + rh.y) / 2,
  )
  if (torsoLen < MIN_TORSO_LEN) return null

  const shoulderSepX = Math.abs(rs.x - ls.x) * aspectRatio
  const sinYaw = Math.min(1, Math.max(0, shoulderSepX / (SHOULDER_TO_TORSO_RATIO * torsoLen)))
  return Math.asin(sinYaw) * RAD_TO_DEG
}

/** Median per-frame yaw over the first sampleFrames frames with a person. Null if none qualify. */
export function estimateSideViewYawDeg(
  frames: PoseFrame2D[],
  aspectRatio: number,
  minScore = DEFAULT_MIN_SCORE,
  sampleFrames = DEFAULT_SAMPLE_FRAMES,
): number | null {
  const perFrame: number[] = []
  for (const f of frames) {
    if (f.keypoints.length === 0) continue
    if (perFrame.length >= sampleFrames) break
    const y = frameYawDeg(f.keypoints, aspectRatio, minScore)
    if (y !== null) perFrame.push(y)
  }
  if (perFrame.length === 0) return null
  return median(perFrame)
}

/**
 * Per-rep yaw from the lookback window immediately BEFORE the stroke (estimating
 * during the swing confounds the player's own rotation with camera placement).
 * Falls back to the stroke's own window when there is no lookback.
 */
export function estimateYawForStroke(
  frames: PoseFrame2D[],
  stroke: Stroke2D,
  aspectRatio: number,
  intervalMs: number,
  minScore = DEFAULT_MIN_SCORE,
  lookbackMs = DEFAULT_LOOKBACK_MS,
): number | null {
  if (intervalMs <= 0) throw new Error(`intervalMs must be > 0, got ${intervalMs}`)
  const lookbackFrames = Math.max(1, Math.trunc(lookbackMs / intervalMs))
  const until = Math.min(Math.max(stroke.startFrame, 0), frames.length)
  const from = Math.max(until - lookbackFrames, 0)
  const pre = estimateSideViewYawDeg(frames.slice(from, until), aspectRatio, minScore)
  if (pre !== null) return pre
  const strokeEnd = Math.min(Math.max(stroke.endFrame + 1, 0), frames.length)
  if (until >= strokeEnd) return null
  return estimateSideViewYawDeg(frames.slice(until, strokeEnd), aspectRatio, minScore)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/cameraYaw.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/cameraYaw.ts poses_viewer/src/drill2d/__tests__/cameraYaw.test.ts
git commit -m "feat(viewer): port CameraAngleEstimator → cameraYaw.ts (M1)"
```

---

### Task 3: sanityBounds + metricPrecision

**Files:**
- Create: `poses_viewer/src/drill2d/sanityBounds.ts`
- Create: `poses_viewer/src/drill2d/metricPrecision.ts`
- Test: `poses_viewer/src/drill2d/__tests__/sanityPrecision.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// poses_viewer/src/drill2d/__tests__/sanityPrecision.test.ts
import { describe, expect, it } from 'vitest'
import { isSane } from '../sanityBounds'
import { precisionFor } from '../metricPrecision'

describe('sanityBounds', () => {
  it('passes an in-band elbow angle', () => {
    expect(isSane('elbow_angle', 130)).toBe(true)
  })
  it('drops an out-of-band elbow angle (tracking glitch)', () => {
    expect(isSane('elbow_angle', 5)).toBe(false)
  })
  it('passes metrics without a registered band', () => {
    expect(isSane('unknown_metric', 9999)).toBe(true)
  })
})

describe('metricPrecision', () => {
  it('the five in-plane metrics are precise-degrees', () => {
    expect(precisionFor('elbow_angle')).toBe('precise_degrees')
    expect(precisionFor('shoulder_tilt')).toBe('precise_degrees')
  })
  it('unknown metrics default to qualitative', () => {
    expect(precisionFor('wrist_snap')).toBe('qualitative')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/sanityPrecision.test.ts`
Expected: FAIL — cannot resolve `../sanityBounds`.

- [ ] **Step 3: Write both implementations**

```typescript
// poses_viewer/src/drill2d/sanityBounds.ts
/**
 * Anatomical sanity bounds — 1:1 mirror of Kotlin SanityBounds. A value outside
 * its band is a tracking glitch: the metric is DROPPED for that frame, never
 * coached on. Bounds are opt-in (unregistered metrics pass through).
 */
import { METRIC_KEYS } from './referenceStandard'

const BOUNDS: Record<string, readonly [number, number]> = {
  elbow_angle: [20, 170],
  shoulder_angle: [5, 175],
  knee_bend: [60, 180],
  torso_lean: [-60, 60],
  shoulder_tilt: [-60, 60],
}

export function isSane(metricKey: string, value: number): boolean {
  const b = BOUNDS[metricKey]
  if (b === undefined) return true
  return value >= b[0] && value <= b[1]
}

// Compile-time reminder that bounds cover the metric set (no runtime cost).
void METRIC_KEYS
```

```typescript
// poses_viewer/src/drill2d/metricPrecision.ts
/**
 * Trust rule — precise degrees ONLY for the five in-plane metrics; everything else
 * is qualitative-only. 1:1 mirror of Kotlin MetricPrecisionPolicy.
 */
import { METRIC_KEYS } from './referenceStandard'

export type MetricPrecision = 'precise_degrees' | 'qualitative'

const PRECISE = new Set<string>(METRIC_KEYS)

/** Unknown metrics default to qualitative — never overclaim precision. */
export function precisionFor(metricKey: string): MetricPrecision {
  return PRECISE.has(metricKey) ? 'precise_degrees' : 'qualitative'
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/sanityPrecision.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/sanityBounds.ts poses_viewer/src/drill2d/metricPrecision.ts poses_viewer/src/drill2d/__tests__/sanityPrecision.test.ts
git commit -m "feat(viewer): port SanityBounds + MetricPrecisionPolicy (M1)"
```

---

### Task 4: drillMetrics — extractAtPeak (±70 ms median window)

**Files:**
- Create: `poses_viewer/src/drill2d/drillMetrics.ts`
- Test: `poses_viewer/src/drill2d/__tests__/drillMetrics.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// poses_viewer/src/drill2d/__tests__/drillMetrics.test.ts
import { describe, expect, it } from 'vitest'
import { extractAtFrame, extractAtPeak, METRIC, ALL_KEYS } from '../drillMetrics'
import { Coco17, Keypoint2D, PoseFrame2D } from '../types'

const K = (x: number, y: number, score = 1): Keypoint2D => ({ x, y, score })

/** Right arm straight (elbow 180°), level shoulders, both legs straight & vertical. */
function uprightFrame(idx: number, elbowY = 0): PoseFrame2D {
  const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0, y: 0, score: 0 }))
  kp[Coco17.RIGHT_SHOULDER] = K(0, 0)
  kp[Coco17.RIGHT_ELBOW] = K(1, elbowY)
  kp[Coco17.RIGHT_WRIST] = K(2, 2 * elbowY)
  return { frameIndex: idx, timestampMs: idx * 33, keypoints: kp }
}

describe('extractAtFrame', () => {
  it('extracts elbow angle and drops it when out of sanity bounds', () => {
    const sane = extractAtFrame(uprightFrame(0, 0), 'right', 1)
    expect(sane[METRIC.ELBOW_ANGLE]).toBeCloseTo(180, 4)
    // collapse the elbow far past the 170° sanity cap is impossible (max 180);
    // instead bend it hard so the angle drops below 20° → dropped
    const bent = uprightFrame(0)
    bent.keypoints[Coco17.RIGHT_WRIST] = K(0.05, 0.001) // wrist back near shoulder → tiny angle
    expect(extractAtFrame(bent, 'right', 1)[METRIC.ELBOW_ANGLE]).toBeUndefined()
  })
})

describe('extractAtPeak', () => {
  it('takes the median over the ±radius window', () => {
    // 5 frames, peak at index 2; elbow angle identical across all → median = that value
    const frames = [0, 1, 2, 3, 4].map(i => uprightFrame(i, 0))
    const m = extractAtPeak(frames, 2, 'right', 1, 33, undefined, 70)
    expect(m[METRIC.ELBOW_ANGLE]).toBeCloseTo(180, 4)
  })
  it('throws on a non-positive interval', () => {
    const frames = [uprightFrame(0)]
    expect(() => extractAtPeak(frames, 0, 'right', 1, 0)).toThrow()
  })
  it('ALL_KEYS lists the five metric keys', () => {
    expect(ALL_KEYS).toHaveLength(5)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/drillMetrics.test.ts`
Expected: FAIL — cannot resolve `../drillMetrics`.

- [ ] **Step 3: Write the implementation**

```typescript
// poses_viewer/src/drill2d/drillMetrics.ts
/**
 * Per-rep extraction of the five in-plane metrics at the stroke's wrist-speed peak
 * — 1:1 mirror of Kotlin DrillMetrics. Score-gated per joint, sanity-bounded per
 * value, then MEDIAN over a ±70 ms window (keypoints are unsmoothed — one junk
 * frame must not shift the rep value).
 */
import { Handedness, PoseFrame2D } from './types'
import { DEFAULT_MIN_SCORE } from './facing'
import { elbowAngle, kneeBend, shoulderAngle, shoulderTilt, torsoLean } from './angles2d'
import { isSane } from './sanityBounds'

export const METRIC = {
  ELBOW_ANGLE: 'elbow_angle',
  SHOULDER_ANGLE: 'shoulder_angle',
  KNEE_BEND: 'knee_bend',
  TORSO_LEAN: 'torso_lean',
  SHOULDER_TILT: 'shoulder_tilt',
} as const

export const ALL_KEYS: string[] = [
  METRIC.ELBOW_ANGLE, METRIC.SHOULDER_ANGLE, METRIC.KNEE_BEND,
  METRIC.TORSO_LEAN, METRIC.SHOULDER_TILT,
]

/** Half-width of the peak-metric smoothing window, in ms (±2 frames at 30 fps). */
export const DEFAULT_PEAK_RADIUS_MS = 70

export function extractAtFrame(
  frame: PoseFrame2D, handedness: Handedness, xScale: number, minScore = DEFAULT_MIN_SCORE,
): Record<string, number> {
  const kp = frame.keypoints
  if (kp.length === 0) return {}
  const out: Record<string, number> = {}
  const put = (key: string, v: number | null) => {
    if (v !== null && isSane(key, v)) out[key] = v
  }
  put(METRIC.ELBOW_ANGLE, elbowAngle(kp, handedness, xScale, minScore))
  put(METRIC.SHOULDER_ANGLE, shoulderAngle(kp, handedness, xScale, minScore))
  put(METRIC.KNEE_BEND, kneeBend(kp, handedness, xScale, minScore))
  put(METRIC.TORSO_LEAN, torsoLean(kp, xScale, minScore))
  put(METRIC.SHOULDER_TILT, shoulderTilt(kp, xScale, minScore))
  return out
}

function medianOf(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

/** Per-rep metrics: median of each metric over frames within ±radiusMs of peakFrame. */
export function extractAtPeak(
  frames: PoseFrame2D[],
  peakFrame: number,
  handedness: Handedness,
  xScale: number,
  intervalMs: number,
  minScore = DEFAULT_MIN_SCORE,
  radiusMs = DEFAULT_PEAK_RADIUS_MS,
): Record<string, number> {
  if (intervalMs <= 0) throw new Error(`intervalMs must be > 0, got ${intervalMs}`)
  if (peakFrame < 0 || peakFrame >= frames.length) {
    throw new Error(`peakFrame ${peakFrame} out of bounds for ${frames.length} frames`)
  }
  const radius = Math.trunc(radiusMs / intervalMs)
  const lo = Math.max(peakFrame - radius, 0)
  const hi = Math.min(peakFrame + radius, frames.length - 1)
  const byKey: Record<string, number[]> = {}
  for (let i = lo; i <= hi; i++) {
    for (const [key, value] of Object.entries(extractAtFrame(frames[i], handedness, xScale, minScore))) {
      ;(byKey[key] ??= []).push(value)
    }
  }
  const out: Record<string, number> = {}
  for (const [key, values] of Object.entries(byKey)) out[key] = medianOf(values)
  return out
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/drillMetrics.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/drillMetrics.ts poses_viewer/src/drill2d/__tests__/drillMetrics.test.ts
git commit -m "feat(viewer): port DrillMetrics.extractAtPeak → drillMetrics.ts (M1)"
```

---

### Task 5: feedbackCue + feedbackEngine (range-based, divergent)

**Files:**
- Create: `poses_viewer/src/drill2d/feedbackCue.ts`
- Create: `poses_viewer/src/drill2d/feedbackEngine.ts`
- Test: `poses_viewer/src/drill2d/__tests__/feedbackEngine.test.ts`

**Divergence note:** Kotlin `DrillFeedbackEngine` compares to `baseline.mean ± k·σ` and computes `severity = |delta| / σ`. This tool compares to an ideal **range** and computes `severity = distanceOutsideBand / halfWidth`, `deltaFromRange = signed distance to the nearest edge` (0 inside). No personal baseline, no Kotlin golden.

- [ ] **Step 1: Write the failing test**

```typescript
// poses_viewer/src/drill2d/__tests__/feedbackEngine.test.ts
import { describe, expect, it } from 'vitest'
import { evaluateRep } from '../feedbackEngine'
import { FOREHAND_DRIVE_STANDARD } from '../referenceStandard'

describe('feedbackEngine.evaluateRep', () => {
  it('emits no cue for a metric inside its ideal band', () => {
    const cues = evaluateRep({ elbow_angle: 130 }, FOREHAND_DRIVE_STANDARD) // band 115–150
    expect(cues).toHaveLength(0)
  })
  it('emits TOO_HIGH above the band with signed delta and severity', () => {
    const cues = evaluateRep({ elbow_angle: 168 }, FOREHAND_DRIVE_STANDARD) // hi 150, half-width 17.5
    expect(cues).toHaveLength(1)
    expect(cues[0].direction).toBe('too_high')
    expect(cues[0].deltaFromRange).toBeCloseTo(18, 4) // 168 - 150
    expect(cues[0].severity).toBeCloseTo(18 / 17.5, 4)
    expect(cues[0].precision).toBe('precise_degrees')
  })
  it('emits TOO_LOW below the band', () => {
    const cues = evaluateRep({ knee_bend: 90 }, FOREHAND_DRIVE_STANDARD) // lo 110
    expect(cues[0].direction).toBe('too_low')
    expect(cues[0].deltaFromRange).toBeCloseTo(-20, 4)
  })
  it('skips metrics that are disabled', () => {
    const cues = evaluateRep({ elbow_angle: 168 }, FOREHAND_DRIVE_STANDARD, new Set(['knee_bend']))
    expect(cues).toHaveLength(0)
  })
  it('sorts cues by descending severity', () => {
    const cues = evaluateRep(
      { elbow_angle: 160, knee_bend: 50 }, // elbow +10/17.5 ; knee -60/17.5
      FOREHAND_DRIVE_STANDARD,
    )
    expect(cues[0].metricKey).toBe('knee_bend')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/feedbackEngine.test.ts`
Expected: FAIL — cannot resolve `../feedbackEngine`.

- [ ] **Step 3: Write both implementations**

```typescript
// poses_viewer/src/drill2d/feedbackCue.ts
import { MetricPrecision } from './metricPrecision'

export type CueDirection = 'too_high' | 'too_low'

/** One actionable deviation of a rep metric from the external ideal band. */
export interface FeedbackCue {
  metricKey: string
  direction: CueDirection
  /** Signed degrees outside the band: + above hi, − below lo. */
  deltaFromRange: number
  /** |deltaFromRange| / band half-width — ranks cues for the cadence window. */
  severity: number
  precision: MetricPrecision
}
```

```typescript
// poses_viewer/src/drill2d/feedbackEngine.ts
/**
 * Metric vs external IDEAL range → prioritized feedback cues. DELIBERATELY diverges
 * from Kotlin DrillFeedbackEngine (which compares to the personal baseline mean±σ):
 * this tool grades "how close to a standard technique" (spec decision #2). No
 * personal baseline, no Kotlin golden — do not parity-test against shared/.
 */
import { FeedbackCue } from './feedbackCue'
import { precisionFor } from './metricPrecision'
import { ReferenceStandard } from './referenceStandard'

export function evaluateRep(
  metrics: Record<string, number>,
  standard: ReferenceStandard,
  enabledKeys?: Set<string>,
): FeedbackCue[] {
  const cues: FeedbackCue[] = []
  for (const [key, range] of Object.entries(standard.ranges)) {
    if (enabledKeys && !enabledKeys.has(key)) continue
    const value = metrics[key]
    if (value === undefined) continue // no measurement → silent
    const halfWidth = (range.hi - range.lo) / 2
    let delta: number
    let direction: FeedbackCue['direction']
    if (value > range.hi) {
      delta = value - range.hi
      direction = 'too_high'
    } else if (value < range.lo) {
      delta = value - range.lo
      direction = 'too_low'
    } else {
      continue // inside the band — no cue
    }
    const severity = halfWidth > 0 ? Math.abs(delta) / halfWidth : 0
    cues.push({ metricKey: key, direction, deltaFromRange: delta, severity, precision: precisionFor(key) })
  }
  return cues.sort((a, b) => b.severity - a.severity)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/feedbackEngine.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/feedbackCue.ts poses_viewer/src/drill2d/feedbackEngine.ts poses_viewer/src/drill2d/__tests__/feedbackEngine.test.ts
git commit -m "feat(viewer): range-based feedbackEngine vs ideal standard (M1)"
```

---

### Task 6: messageCatalog (EN, vs-ideal wording)

**Files:**
- Create: `poses_viewer/src/drill2d/messageCatalog.ts`
- Test: `poses_viewer/src/drill2d/__tests__/messageCatalog.test.ts`

**Divergence note:** Kotlin's catalog says "than your usual / off your baseline." This tool compares to an external ideal, so wording is "than ideal / off the standard." EN only (spec non-goal: no UA here). Degree number inserted ONLY for `precise_degrees` cues (trust rule).

- [ ] **Step 1: Write the failing test**

```typescript
// poses_viewer/src/drill2d/__tests__/messageCatalog.test.ts
import { describe, expect, it } from 'vitest'
import { formatCue, positiveMessage } from '../messageCatalog'
import { FeedbackCue } from '../feedbackCue'

const cue = (over: Partial<FeedbackCue>): FeedbackCue => ({
  metricKey: 'elbow_angle', direction: 'too_high', deltaFromRange: 18,
  severity: 1, precision: 'precise_degrees', ...over,
})

describe('messageCatalog', () => {
  it('inserts the degree number for precise-degrees cues', () => {
    const msg = formatCue(cue({ deltaFromRange: 18 }))
    expect(msg).toMatch(/18°/)
    expect(msg.toLowerCase()).toContain('ideal')
  })
  it('omits the degree number for qualitative cues', () => {
    const msg = formatCue(cue({ metricKey: 'wrist_snap', precision: 'qualitative' }))
    expect(msg).not.toMatch(/\d+°/)
  })
  it('distinguishes too-high from too-low wording', () => {
    const hi = formatCue(cue({ direction: 'too_high' }))
    const lo = formatCue(cue({ direction: 'too_low', deltaFromRange: -18 }))
    expect(hi).not.toEqual(lo)
  })
  it('has a positive message', () => {
    expect(positiveMessage().length).toBeGreaterThan(0)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/messageCatalog.test.ts`
Expected: FAIL — cannot resolve `../messageCatalog`.

- [ ] **Step 3: Write the implementation**

```typescript
// poses_viewer/src/drill2d/messageCatalog.ts
/**
 * Cue → English message string. Adapted from Kotlin FeedbackMessageCatalog but
 * worded against the external IDEAL ("than ideal / off the standard"), not the
 * personal baseline. EN only (spec non-goal: no UA toggle here). Trust rule: the
 * degree number is inserted ONLY for precise-degrees cues.
 */
import { FeedbackCue } from './feedbackCue'
import { METRIC } from './drillMetrics'

export function formatCue(cue: FeedbackCue): string {
  const d = Math.round(Math.abs(cue.deltaFromRange))
  const high = cue.direction === 'too_high'
  const base = phrase(cue.metricKey, high)
  return cue.precision === 'precise_degrees' ? `${base} (about ${d}° off the standard)` : base
}

function phrase(metricKey: string, high: boolean): string {
  switch (metricKey) {
    case METRIC.ELBOW_ANGLE:
      return high ? 'Elbow straighter than ideal — bend it a bit more'
                  : 'Elbow more bent than ideal — open it up a bit'
    case METRIC.SHOULDER_ANGLE:
      return high ? 'Upper arm higher than ideal — drop the elbow a bit'
                  : 'Upper arm lower than ideal — lift the elbow a bit'
    case METRIC.KNEE_BEND:
      return high ? 'Legs straighter than ideal — bend the knees more'
                  : 'Knees more bent than ideal — rise a little'
    case METRIC.TORSO_LEAN:
      return high ? 'Leaning further than ideal — straighten up a bit'
                  : 'More upright than ideal — lean in a little'
    case METRIC.SHOULDER_TILT:
      return high ? 'Shoulders more tilted than ideal — level them'
                  : 'Shoulder line flatter than ideal — let the playing shoulder drop a touch'
    default:
      // Unknown metric (future rotational cues): qualitative-only, never degrees.
      return high ? 'A bit more than ideal on that move — ease off'
                  : 'A bit less than ideal on that move'
  }
}

export function positiveMessage(): string {
  return 'Good rep — close to the standard'
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/messageCatalog.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/messageCatalog.ts poses_viewer/src/drill2d/__tests__/messageCatalog.test.ts
git commit -m "feat(viewer): EN message catalog (vs-ideal wording) (M1)"
```

---

### Task 7: cadencePolicy — 3–5 s gating

**Files:**
- Create: `poses_viewer/src/drill2d/cadencePolicy.ts`
- Test: `poses_viewer/src/drill2d/__tests__/cadencePolicy.test.ts`

1:1 mirror of Kotlin `FeedbackCadencePolicy` (stateful class).

- [ ] **Step 1: Write the failing test**

```typescript
// poses_viewer/src/drill2d/__tests__/cadencePolicy.test.ts
import { describe, expect, it } from 'vitest'
import { FeedbackCadencePolicy } from '../cadencePolicy'
import { FeedbackCue } from '../feedbackCue'

const cue = (severity: number): FeedbackCue => ({
  metricKey: 'elbow_angle', direction: 'too_high', deltaFromRange: 10, severity, precision: 'precise_degrees',
})

describe('FeedbackCadencePolicy', () => {
  it('emits the first cue and suppresses cues within minInterval', () => {
    const p = new FeedbackCadencePolicy(3000, 5000)
    expect(p.offer(0, [cue(1)])).not.toBeNull()
    expect(p.offer(2000, [cue(1)])).toBeNull() // 2s < 3s
    expect(p.offer(3000, [cue(1)])).not.toBeNull() // exactly 3s
  })
  it('picks the highest-severity cue', () => {
    const p = new FeedbackCadencePolicy()
    const out = p.offer(0, [cue(0.5), cue(2.0), cue(1.0)])
    expect(out?.severity).toBe(2.0)
  })
  it('offerPositive only after maxInterval of silence', () => {
    const p = new FeedbackCadencePolicy(3000, 5000)
    expect(p.offer(0, [cue(1)])).not.toBeNull()
    expect(p.offerPositive(4000)).toBe(false) // 4s < 5s
    expect(p.offerPositive(5000)).toBe(true)
  })
  it('reset clears the window', () => {
    const p = new FeedbackCadencePolicy()
    p.offer(0, [cue(1)])
    p.reset()
    expect(p.offer(10, [cue(1)])).not.toBeNull()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/cadencePolicy.test.ts`
Expected: FAIL — cannot resolve `../cadencePolicy`.

- [ ] **Step 3: Write the implementation**

```typescript
// poses_viewer/src/drill2d/cadencePolicy.ts
/**
 * 3–5 s feedback cadence — 1:1 mirror of Kotlin FeedbackCadencePolicy. At most one
 * corrective cue per minIntervalMs; positive reinforcement only after maxIntervalMs
 * of silence (corrections keep priority for the voice channel). Stateful and
 * single-session: one per drill run, reset() between runs. Timestamps must be
 * monotonically increasing.
 */
import { FeedbackCue } from './feedbackCue'

export class FeedbackCadencePolicy {
  private lastEmittedMs: number | null = null

  constructor(
    private readonly minIntervalMs = 3000,
    private readonly maxIntervalMs = 5000,
  ) {
    if (minIntervalMs < 0 || maxIntervalMs < minIntervalMs) {
      throw new Error(`invalid cadence intervals: min=${minIntervalMs} max=${maxIntervalMs}`)
    }
  }

  /** The cue to speak now (highest severity), or null if the window is closed. */
  offer(nowMs: number, cues: FeedbackCue[]): FeedbackCue | null {
    const last = this.lastEmittedMs
    if (last !== null && nowMs - last < this.minIntervalMs) return null
    let top: FeedbackCue | null = null
    for (const c of cues) if (top === null || c.severity > top.severity) top = c
    if (top === null) return null
    this.lastEmittedMs = nowMs
    return top
  }

  /** True if a positive message may be spoken now; consumes the window if so. */
  offerPositive(nowMs: number): boolean {
    const last = this.lastEmittedMs
    if (last !== null && nowMs - last < this.maxIntervalMs) return false
    this.lastEmittedMs = nowMs
    return true
  }

  reset(): void {
    this.lastEmittedMs = null
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/cadencePolicy.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/cadencePolicy.ts poses_viewer/src/drill2d/__tests__/cadencePolicy.test.ts
git commit -m "feat(viewer): port FeedbackCadencePolicy → cadencePolicy.ts (M1)"
```

---

### Task 8: analyzeDrill — orchestrator + count-golden

**Files:**
- Create: `poses_viewer/src/drill2d/analyzeDrill.ts`
- Test: `poses_viewer/src/drill2d/__tests__/analyzeDrill.test.ts`

**Adapted from Kotlin `ForehandDriveDrillAnalyzer`**: same detect → forward → rep → per-rep yaw → metrics → cues → cadence flow, but cues come from the ideal-range `feedbackEngine`, not a baseline. Detection runs on plain `aspectRatio` (yaw 0) to preserve the M0 count-golden; metrics run on the yaw-corrected xScale.

**Yaw default:** because `cameraYaw` saturates on Videos/ footage (L-25), the default config uses a **manual yaw override of 0** so `placementOk` is true and feedback is demoable (matches the existing `#/strokes` default). Passing `cameraYawDeg: null` switches to honest per-rep auto-estimation (most Videos/ reps then read `placementOk: false`, no cues).

- [ ] **Step 1: Write the failing test**

```typescript
// poses_viewer/src/drill2d/__tests__/analyzeDrill.test.ts
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { analyzeDrill } from '../analyzeDrill'
import { parsePoseV2 } from '../parsePoseV2'
import { FOREHAND_DRIVE_STANDARD } from '../referenceStandard'

// Mirror golden.test.ts's fixture path pattern (repo-relative).
function loadSeq(name: string) {
  const p = resolve(__dirname, '../../../../shared/src/commonTest/resources/fixtures', name)
  return parsePoseV2(JSON.parse(readFileSync(p, 'utf-8')))
}

describe('analyzeDrill — count parity (anti-drift guardrail)', () => {
  it('andrii_1 produces 15 reps from 23 raw peaks (matches Kotlin E2E golden)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right',
      drillType: 'forehand_drive',
      standard: FOREHAND_DRIVE_STANDARD,
      cameraYawDeg: 0, // manual override → placementOk, feedback flows
    })
    expect(report.rawPeakCount).toBe(23)
    expect(report.reps).toHaveLength(15)
  })

  it('produces a metric map per rep and (with yaw override) at least one spoken line', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    expect(report.reps.every(r => typeof r.metrics === 'object')).toBe(true)
    expect(report.reps.every(r => r.placementOk)).toBe(true) // yaw override 0
    expect(report.feedback.length).toBeGreaterThan(0)
    // cadence: consecutive spoken lines are ≥ 3 s apart
    for (let i = 1; i < report.feedback.length; i++) {
      expect(report.feedback[i].timestampMs - report.feedback[i - 1].timestampMs).toBeGreaterThanOrEqual(3000)
    }
  })

  it('auto yaw estimation flags Videos/ footage as bad placement (L-25 saturation)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: null,
    })
    // Non-protocol footage: most reps fail the ≤30° gate, so the session flag is false.
    expect(report.placementOk).toBe(false)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/analyzeDrill.test.ts`
Expected: FAIL — cannot resolve `../analyzeDrill`.

- [ ] **Step 3: Write the implementation**

```typescript
// poses_viewer/src/drill2d/analyzeDrill.ts
/**
 * Drill analysis orchestrator — adapted from Kotlin ForehandDriveDrillAnalyzer.
 * Same pipeline (detect → ForwardStrokeFilter → RepFilter → per-rep yaw → metrics →
 * cues → cadenced feedback), but cues compare to the external IDEAL range
 * (feedbackEngine), not a personal baseline. Detection runs on plain aspectRatio so
 * the M0 count-golden (andrii_1: 23 raw / 15 reps) is preserved; per-rep metrics use
 * the yaw-corrected xScale.
 */
import { Handedness, Stroke2D } from './types'
import { PoseSequence2D } from './parsePoseV2'
import { xScaleFor } from './geometry'
import { detectStrokes, StrokeDetectorOptions } from './strokeDetector2d'
import { filterForwardStrokes } from './forwardStrokeFilter'
import { filterReps } from './repFilter'
import { DEFAULT_MIN_SCORE } from './facing'
import { estimateYawForStroke } from './cameraYaw'
import { extractAtPeak } from './drillMetrics'
import { evaluateRep } from './feedbackEngine'
import { FeedbackCue } from './feedbackCue'
import { formatCue, positiveMessage } from './messageCatalog'
import { FeedbackCadencePolicy } from './cadencePolicy'
import { ReferenceStandard } from './referenceStandard'

/** |yaw| beyond this → rep excluded from feedback (CLAUDE.md: ~30° gate). */
export const DEFAULT_MAX_CAMERA_YAW_DEG = 30

export interface RepAnalysis {
  stroke: Stroke2D
  metrics: Record<string, number>
  cues: FeedbackCue[]
  /** Yaw used for this rep (override or pre-stroke estimate); null = unmeasurable. */
  cameraYawDeg: number | null
  /** false → camera too far off side view (or unmeasurable): no cues, metrics diagnostic only. */
  placementOk: boolean
}

export interface SpokenFeedback {
  timestampMs: number
  message: string
  /** null = positive reinforcement, not a correction. */
  cue: FeedbackCue | null
}

export interface DrillAnalysisReport {
  reps: RepAnalysis[]
  feedback: SpokenFeedback[]
  /** Session summary: false → over half the reps had bad camera placement. */
  placementOk: boolean
  rawPeakCount: number
  forwardRepCount: number
}

export interface DrillAnalysisConfig {
  handedness: Handedness
  drillType: string
  standard: ReferenceStandard
  /** Metric keys to evaluate; omitted → all metrics in the standard. */
  enabledMetrics?: Set<string>
  /** Manual yaw applied to ALL reps; null/undefined → per-rep auto-estimate. Default 0. */
  cameraYawDeg?: number | null
  maxCameraYawDeg?: number
  detector?: StrokeDetectorOptions
  cadence?: { minIntervalMs: number; maxIntervalMs: number }
}

export function analyzeDrill(seq: PoseSequence2D, config: DrillAnalysisConfig): DrillAnalysisReport {
  const minScore = config.detector?.minScore ?? DEFAULT_MIN_SCORE
  const maxYaw = config.maxCameraYawDeg ?? DEFAULT_MAX_CAMERA_YAW_DEG
  // undefined → default manual 0 (demoable on Videos/); explicit null → auto-estimate.
  const yawOverride = config.cameraYawDeg === undefined ? 0 : config.cameraYawDeg

  // Detection on plain aspect (yaw 0) — preserves the M0 count-golden.
  const detectXScale = xScaleFor(seq.aspectRatio, 0)
  const rawStrokes = detectStrokes(seq.frames, config.handedness, detectXScale, seq.intervalMs, config.detector)
  const forwardStrokes = filterForwardStrokes(rawStrokes, seq.frames, config.handedness, minScore)
  const reps = filterReps(forwardStrokes)

  const cadence = new FeedbackCadencePolicy(
    config.cadence?.minIntervalMs ?? 3000,
    config.cadence?.maxIntervalMs ?? 5000,
  )

  const repAnalyses: RepAnalysis[] = reps.map(stroke => {
    const yaw = yawOverride !== null
      ? yawOverride
      : estimateYawForStroke(seq.frames, stroke, seq.aspectRatio, seq.intervalMs, minScore)
    const placementOk = yaw !== null && Math.abs(yaw) <= maxYaw
    // Beyond the gate (or unmeasurable) the 1/cos model is unreliable: fall back to
    // plain aspect; this rep's metrics become diagnostics only (no cues).
    const xScale = placementOk && yaw !== null && Math.abs(yaw) <= 60
      ? xScaleFor(seq.aspectRatio, yaw)
      : xScaleFor(seq.aspectRatio, 0)
    const metrics = extractAtPeak(seq.frames, stroke.peakFrame, config.handedness, xScale, seq.intervalMs, minScore)
    const cues = placementOk ? evaluateRep(metrics, config.standard, config.enabledMetrics) : []
    return { stroke, metrics, cues, cameraYawDeg: yaw, placementOk }
  })

  const feedback: SpokenFeedback[] = []
  for (const rep of repAnalyses) {
    if (!rep.placementOk) continue // silent rep; UI surfaces the placement flag
    const atMs = rep.stroke.endFrame * seq.intervalMs
    const cue = cadence.offer(atMs, rep.cues)
    if (cue !== null) {
      feedback.push({ timestampMs: atMs, message: formatCue(cue), cue })
    } else if (rep.cues.length === 0 && Object.keys(rep.metrics).length > 0 && cadence.offerPositive(atMs)) {
      feedback.push({ timestampMs: atMs, message: positiveMessage(), cue: null })
    }
  }

  const okCount = repAnalyses.filter(r => r.placementOk).length
  return {
    reps: repAnalyses,
    feedback,
    placementOk: repAnalyses.length === 0 || okCount * 2 >= repAnalyses.length,
    rawPeakCount: rawStrokes.length,
    forwardRepCount: reps.length,
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/analyzeDrill.test.ts`
Expected: PASS. If `rawPeakCount`/`reps` differ from 23/15, **stop** — the detection wiring diverged from M0; diff against `countStrokes.ts` (it must call `detectStrokes` → `filterForwardStrokes` → `filterReps` with the same args). Do not adjust the golden.

- [ ] **Step 5: Run the whole drill2d suite (no regressions)**

Run: `cd poses_viewer && npx vitest run src/drill2d`
Expected: all M0 + M1 tests PASS (golden.test.ts still 23/15/15 and 18/12/9).

- [ ] **Step 6: Commit**

```bash
git add poses_viewer/src/drill2d/analyzeDrill.ts poses_viewer/src/drill2d/__tests__/analyzeDrill.test.ts
git commit -m "feat(viewer): analyzeDrill orchestrator + count-golden parity (M1)"
```

---

### Task 9: UI — results table, metric toggles, drill-type selector

Extend `StrokesPage.tsx` in place. The page keeps its M0 timeline/counts; we add an analysis report computed from `analyzeDrill`, a per-rep results table, metric on/off toggles, and a drill-type selector. **No new route.**

**Files:**
- Modify: `poses_viewer/src/components/StrokesPage.tsx`
- Create: `poses_viewer/src/components/DrillResultsTable.tsx`
- Test: `poses_viewer/src/components/__tests__/DrillResultsTable.test.tsx`

- [ ] **Step 1: Write the failing test for the presentational table**

```tsx
// poses_viewer/src/components/__tests__/DrillResultsTable.test.tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DrillResultsTable } from '../DrillResultsTable'
import { FOREHAND_DRIVE_STANDARD } from '../../drill2d/referenceStandard'
import type { RepAnalysis } from '../../drill2d/analyzeDrill'

const rep = (over: Partial<RepAnalysis>): RepAnalysis => ({
  stroke: { strokeIndex: 0, startFrame: 0, peakFrame: 5, endFrame: 10, peakSpeed: 3 },
  metrics: { elbow_angle: 168, knee_bend: 130 },
  cues: [{ metricKey: 'elbow_angle', direction: 'too_high', deltaFromRange: 18, severity: 1, precision: 'precise_degrees' }],
  cameraYawDeg: 0, placementOk: true, ...over,
})

describe('DrillResultsTable', () => {
  it('renders one row per rep with metric values and over/under status', () => {
    render(
      <DrillResultsTable
        reps={[rep({}), rep({ stroke: { strokeIndex: 1, startFrame: 11, peakFrame: 15, endFrame: 20, peakSpeed: 3 } })]}
        standard={FOREHAND_DRIVE_STANDARD}
        enabledMetrics={new Set(['elbow_angle', 'knee_bend'])}
        selectedIndex={null}
        onSelect={() => {}}
      />,
    )
    // 2 rep rows
    expect(screen.getAllByRole('row').length).toBeGreaterThanOrEqual(3) // header + 2
    // elbow 168 over band (115–150) → flagged "over"
    expect(screen.getAllByText(/over/i).length).toBeGreaterThan(0)
  })

  it('shows a placement warning for reps with bad camera placement', () => {
    render(
      <DrillResultsTable
        reps={[rep({ placementOk: false, cues: [], cameraYawDeg: 47 })]}
        standard={FOREHAND_DRIVE_STANDARD}
        enabledMetrics={new Set(['elbow_angle'])}
        selectedIndex={null}
        onSelect={() => {}}
      />,
    )
    expect(screen.getByText(/камер|placement/i)).toBeTruthy()
  })
})
```

Note: if `@testing-library/react` is not already a dev dependency, install it first: `cd poses_viewer && npm i -D @testing-library/react @testing-library/jest-dom` and ensure vitest uses jsdom (`environment: 'jsdom'` — check `vite.config.ts`/`vitest.config`; add if missing). If the project has no DOM test setup and adding one is heavy, fall back to testing `DrillResultsTable`'s pure status helper (`metricStatus(value, range)`) extracted into the module and unit-tested instead of rendering. Prefer the render test if jsdom is already configured.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/components/__tests__/DrillResultsTable.test.tsx`
Expected: FAIL — cannot resolve `../DrillResultsTable`.

- [ ] **Step 3: Write the table component**

```tsx
// poses_viewer/src/components/DrillResultsTable.tsx
import { RepAnalysis } from '../drill2d/analyzeDrill'
import { ReferenceStandard, ReferenceRange } from '../drill2d/referenceStandard'
import { ALL_KEYS } from '../drill2d/drillMetrics'

export type MetricStatus = 'ok' | 'over' | 'under' | 'n/a'

/** Where a measured value sits relative to its ideal band. */
export function metricStatus(value: number | undefined, range: ReferenceRange | undefined): MetricStatus {
  if (value === undefined || range === undefined) return 'n/a'
  if (value > range.hi) return 'over'
  if (value < range.lo) return 'under'
  return 'ok'
}

const STATUS_CLASS: Record<MetricStatus, string> = {
  ok: 'text-emerald-400',
  over: 'text-amber-400',
  under: 'text-sky-400',
  'n/a': 'text-neutral-500',
}

/** Ukrainian metric labels (UI chrome stays UA; spoken feedback is EN per spec). */
const METRIC_LABEL: Record<string, string> = {
  elbow_angle: 'Лікоть',
  shoulder_angle: 'Плече',
  knee_bend: 'Коліна',
  torso_lean: 'Нахил корпусу',
  shoulder_tilt: 'Нахил плечей',
}

interface Props {
  reps: RepAnalysis[]
  standard: ReferenceStandard
  enabledMetrics: Set<string>
  selectedIndex: number | null
  onSelect: (index: number) => void
}

export function DrillResultsTable({ reps, standard, enabledMetrics, selectedIndex, onSelect }: Props) {
  const cols = ALL_KEYS.filter(k => enabledMetrics.has(k))
  return (
    <table className="w-full text-xs border-collapse">
      <thead>
        <tr className="text-neutral-400 text-left">
          <th className="py-1 pr-2">#</th>
          {cols.map(k => (
            <th key={k} className="py-1 px-2">{METRIC_LABEL[k] ?? k}</th>
          ))}
          <th className="py-1 px-2">Підказка</th>
        </tr>
      </thead>
      <tbody>
        {reps.map((rep, i) => {
          const top = rep.cues[0]
          return (
            <tr
              key={i}
              className={`cursor-pointer hover:bg-neutral-800 ${selectedIndex === i ? 'bg-neutral-800' : ''}`}
              onClick={() => onSelect(i)}
            >
              <td className="py-1 pr-2 text-neutral-300">{i + 1}</td>
              {cols.map(k => {
                const v = rep.metrics[k]
                const status = metricStatus(v, standard.ranges[k])
                return (
                  <td key={k} className={`py-1 px-2 ${STATUS_CLASS[status]}`}>
                    {v === undefined ? '—' : `${Math.round(v)}° ${status === 'ok' ? '' : `(${status})`}`}
                  </td>
                )
              })}
              <td className="py-1 px-2 text-neutral-300">
                {!rep.placementOk
                  ? '⚠ перевір кут камери (placement)'
                  : top
                    ? top.metricKey
                    : '✓'}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/components/__tests__/DrillResultsTable.test.tsx`
Expected: PASS.

- [ ] **Step 5: Wire analyzeDrill + the new controls into StrokesPage**

In `poses_viewer/src/components/StrokesPage.tsx`:

(a) Add imports near the top:
```tsx
import { analyzeDrill, DrillAnalysisReport } from '../drill2d/analyzeDrill'
import { REFERENCE_STANDARDS } from '../drill2d/referenceStandard'
import { ALL_KEYS } from '../drill2d/drillMetrics'
import { DrillResultsTable } from './DrillResultsTable'
```

(b) Add state next to the existing `useState` hooks (after `selectedIdx`):
```tsx
  const [drillType, setDrillType] = useState('forehand_drive')
  const [enabledMetrics, setEnabledMetrics] = useState<Set<string>>(new Set(ALL_KEYS))
```

(c) Add the analysis memo just after the existing `result` memo (reuses the same knobs; `cameraYawDeg: yawDeg` keeps the manual override the page already exposes):
```tsx
  const report = useMemo<DrillAnalysisReport | null>(() => {
    if (!seq) return null
    const standard = REFERENCE_STANDARDS[drillType]
    if (!standard) return null
    try {
      return analyzeDrill(seq, {
        handedness,
        drillType,
        standard,
        enabledMetrics,
        cameraYawDeg: yawDeg,
        detector: { minPeakSpeed, minPeakGapMs },
      })
    } catch {
      return null
    }
  }, [seq, handedness, yawDeg, minPeakSpeed, minPeakGapMs, drillType, enabledMetrics])
```

(d) Render the results table below the existing counts block (after the `{result && (...)}` summary `</div>`), and seek the selected rep's peak:
```tsx
      {report && report.reps.length > 0 && (
        <div className="space-y-2">
          <h2 className="text-sm font-semibold">Аналіз повторів</h2>
          {!report.placementOk && (
            <div className="text-amber-400 text-xs">
              ⚠ Більшість повторів зняті не збоку — підказки приглушені. Виправ кут камери.
            </div>
          )}
          <DrillResultsTable
            reps={report.reps}
            standard={REFERENCE_STANDARDS[drillType]}
            enabledMetrics={enabledMetrics}
            selectedIndex={selectedIdx}
            onSelect={i => {
              setSelectedIdx(i)
              const peakMs = seq ? (seq.frames[report.reps[i].stroke.peakFrame]?.timestampMs ?? 0) : 0
              seek(peakMs)
            }}
          />
        </div>
      )}
```

(e) Add the drill-type selector and metric toggles inside the existing `<fieldset>` (before the closing `</fieldset>`):
```tsx
        <label className="flex items-center gap-2">
          <span className="w-56">Вправа:</span>
          <select
            className="bg-neutral-800 rounded px-2 py-1"
            value={drillType}
            onChange={e => setDrillType(e.target.value)}
          >
            <option value="forehand_drive">Накат справа (forehand drive)</option>
          </select>
        </label>
        <div className="flex items-start gap-2">
          <span className="w-56">Метрики:</span>
          <div className="flex flex-wrap gap-x-3 gap-y-1">
            {ALL_KEYS.map(k => (
              <label key={k} className="flex items-center gap-1">
                <input
                  type="checkbox"
                  checked={enabledMetrics.has(k)}
                  onChange={e => setEnabledMetrics(prev => {
                    const next = new Set(prev)
                    if (e.target.checked) next.add(k); else next.delete(k)
                    return next
                  })}
                />
                {k}
              </label>
            ))}
          </div>
        </div>
```

(f) The existing selection-reset effect must also reset when the new knobs change. Update its dependency array:
```tsx
  useEffect(() => { setSelectedIdx(null) }, [handedness, yawDeg, minPeakSpeed, minPeakGapMs, drillType, enabledMetrics])
```

- [ ] **Step 6: Typecheck + full viewer test run**

Run: `cd poses_viewer && npx tsc -b --noEmit && npx vitest run`
Expected: no type errors; all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add poses_viewer/src/components/StrokesPage.tsx poses_viewer/src/components/DrillResultsTable.tsx poses_viewer/src/components/__tests__/DrillResultsTable.test.tsx
git commit -m "feat(viewer): per-rep results table + metric toggles + drill selector (M1)"
```

---

### Task 10: UI — spoken-feedback playback

Add `speechSynthesis`-driven feedback that fires as the video's `currentTime` crosses each `SpokenFeedback.timestampMs`, plus an on-screen running log + latest-message banner, and an audio/text-only toggle (audio default).

**Files:**
- Create: `poses_viewer/src/components/useSpokenFeedback.ts`
- Modify: `poses_viewer/src/components/StrokesPage.tsx`
- Test: `poses_viewer/src/components/__tests__/useSpokenFeedback.test.ts`

- [ ] **Step 1: Write the failing test for the fire-tracking helper**

The "which feedback entries should have fired by time T, and which are newly crossed since lastT" logic is pure — extract and test it.

```typescript
// poses_viewer/src/components/__tests__/useSpokenFeedback.test.ts
import { describe, expect, it } from 'vitest'
import { newlyCrossed } from '../useSpokenFeedback'
import type { SpokenFeedback } from '../../drill2d/analyzeDrill'

const fb = (timestampMs: number): SpokenFeedback => ({ timestampMs, message: `m${timestampMs}`, cue: null })

describe('newlyCrossed', () => {
  it('returns entries whose timestamp is in (prevMs, nowMs]', () => {
    const feed = [fb(1000), fb(2000), fb(3000)]
    expect(newlyCrossed(feed, 900, 2000).map(f => f.timestampMs)).toEqual([1000, 2000])
  })
  it('returns nothing when time has not advanced past any entry', () => {
    const feed = [fb(1000), fb(2000)]
    expect(newlyCrossed(feed, 2000, 2500)).toEqual([])
  })
  it('handles a backward seek (prev > now) by firing nothing', () => {
    const feed = [fb(1000), fb(2000)]
    expect(newlyCrossed(feed, 2500, 1500)).toEqual([])
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/components/__tests__/useSpokenFeedback.test.ts`
Expected: FAIL — cannot resolve `../useSpokenFeedback`.

- [ ] **Step 3: Write the hook + pure helper**

```typescript
// poses_viewer/src/components/useSpokenFeedback.ts
import { useCallback, useEffect, useRef, useState } from 'react'
import { SpokenFeedback } from '../drill2d/analyzeDrill'

/** Feedback entries whose timestamp lies in (prevMs, nowMs] — the ones just crossed. */
export function newlyCrossed(feed: SpokenFeedback[], prevMs: number, nowMs: number): SpokenFeedback[] {
  if (nowMs <= prevMs) return [] // paused or seeked backward — fire nothing
  return feed.filter(f => f.timestampMs > prevMs && f.timestampMs <= nowMs)
}

export type FeedbackMode = 'audio' | 'text'

export interface SpokenFeedbackState {
  log: SpokenFeedback[]
  latest: SpokenFeedback | null
  /** Call on every video timeupdate with currentTime in ms. */
  onTime: (nowMs: number) => void
  /** Reset when the clip or report changes, or on a manual seek. */
  reset: (toMs?: number) => void
}

/**
 * Speaks each feedback line once, when playback first crosses its timestamp.
 * Audio via window.speechSynthesis (EN voice); text mode still logs/banners.
 */
export function useSpokenFeedback(feed: SpokenFeedback[], mode: FeedbackMode): SpokenFeedbackState {
  const lastMsRef = useRef(0)
  const [log, setLog] = useState<SpokenFeedback[]>([])
  const [latest, setLatest] = useState<SpokenFeedback | null>(null)

  const speak = useCallback((msg: string) => {
    if (mode !== 'audio') return
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) return
    const u = new SpeechSynthesisUtterance(msg)
    u.lang = 'en-US'
    window.speechSynthesis.speak(u)
  }, [mode])

  const onTime = useCallback((nowMs: number) => {
    const fired = newlyCrossed(feed, lastMsRef.current, nowMs)
    lastMsRef.current = nowMs
    if (fired.length === 0) return
    setLog(prev => [...prev, ...fired])
    setLatest(fired[fired.length - 1])
    for (const f of fired) speak(f.message)
  }, [feed, speak])

  const reset = useCallback((toMs = 0) => {
    lastMsRef.current = toMs
    setLog([])
    setLatest(null)
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) window.speechSynthesis.cancel()
  }, [])

  // New report → start fresh.
  useEffect(() => { reset(0) }, [feed, reset])

  return { log, latest, onTime, reset }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/components/__tests__/useSpokenFeedback.test.ts`
Expected: PASS.

- [ ] **Step 5: Wire the hook into StrokesPage**

In `poses_viewer/src/components/StrokesPage.tsx`:

(a) Add imports:
```tsx
import { useSpokenFeedback, FeedbackMode } from './useSpokenFeedback'
```

(b) Add state + hook (after the `report` memo). Guard against a null report with an empty feed:
```tsx
  const [feedbackMode, setFeedbackMode] = useState<FeedbackMode>('audio')
  const spoken = useSpokenFeedback(report?.feedback ?? [], feedbackMode)
```

(c) Drive it from the video's time. The page already calls `setCurrentMs(...)` in the `<video onTimeUpdate>` handler — extend that handler to also call `spoken.onTime`:
```tsx
            onTimeUpdate={e => {
              const ms = e.currentTarget.currentTime * 1000
              setCurrentMs(ms)
              spoken.onTime(ms)
            }}
```

(d) When the user seeks via a band/rep click or the keyboard stepper, reset the fire-tracker so lines don't double-fire or get skipped. In the `seek` function, after setting `currentTime`:
```tsx
  const seek = (ms: number) => {
    const v = videoRef.current
    if (v) v.currentTime = ms / 1000
    spoken.reset(ms) // re-arm feedback from the new position
  }
```

(e) Render the banner + log + mode toggle. Add the mode toggle inside the `<fieldset>`:
```tsx
        <label className="flex items-center gap-2">
          <span className="w-56">Озвучення підказок:</span>
          <select
            className="bg-neutral-800 rounded px-2 py-1"
            value={feedbackMode}
            onChange={e => setFeedbackMode(e.target.value as FeedbackMode)}
          >
            <option value="audio">Голос (за замовч.)</option>
            <option value="text">Лише текст</option>
          </select>
        </label>
```

And add the banner + log near the video (e.g. just under `<TimelineLegend />`), only when a report exists:
```tsx
              {spoken.latest && (
                <div className="bg-sky-900/60 border border-sky-700 rounded p-2 text-sm">
                  🔊 {spoken.latest.message}
                </div>
              )}
              {spoken.log.length > 0 && (
                <details className="text-xs text-neutral-300">
                  <summary>Журнал підказок ({spoken.log.length})</summary>
                  <ul className="mt-1 space-y-0.5">
                    {spoken.log.map((f, i) => (
                      <li key={i}>{(f.timestampMs / 1000).toFixed(1)} с — {f.message}</li>
                    ))}
                  </ul>
                </details>
              )}
```

- [ ] **Step 6: Typecheck + full viewer test run**

Run: `cd poses_viewer && npx tsc -b --noEmit && npx vitest run`
Expected: no type errors; all tests PASS.

- [ ] **Step 7: Manual smoke test (browser)**

Run: `cd poses_viewer && npm run dev`, open http://localhost:5780/#/strokes, pick `andrii_1`, set camera yaw 0, press play. Confirm: rep rows populate, the banner updates, the log grows, and (audio mode) feedback is spoken roughly every 3–5 s. Switch to "Лише текст" and confirm audio stops but the log still updates. This is a manual check — note the result in the commit body; do not block the plan on a headless audio assertion.

- [ ] **Step 8: Commit**

```bash
git add poses_viewer/src/components/StrokesPage.tsx poses_viewer/src/components/useSpokenFeedback.ts poses_viewer/src/components/__tests__/useSpokenFeedback.test.ts
git commit -m "feat(viewer): spoken-feedback playback + log/banner + audio toggle (M1)"
```

---

### Task 11: Header relabel + docs

The page header still reads "Підрахунок ударів (M0)". Update it, and document M1 in the viewer guide.

**Files:**
- Modify: `poses_viewer/src/components/StrokesPage.tsx` (header text)
- Modify: `poses_viewer/CLAUDE.md`
- Modify: `poses_viewer/src/hooks/useHashRoute.ts` (route title, if it carries one for `strokes`)

- [ ] **Step 1: Relabel the header**

In `StrokesPage.tsx`, change:
```tsx
        <h1 className="text-lg font-semibold">Підрахунок ударів (M0)</h1>
```
to:
```tsx
        <h1 className="text-lg font-semibold">Симулятор ефективності вправи</h1>
```
If `useHashRoute.ts` has a default title string for the `strokes` route, update it to match.

- [ ] **Step 2: Document M1 in poses_viewer/CLAUDE.md**

In the `### src/drill2d/ + src/components/StrokesPage.tsx / StrokeTimeline.tsx` section, append after the M0 description:

```markdown
**M1 (metrics + feedback) extends the same page (no new route):** measurement modules
`angles2d.ts`, `cameraYaw.ts`, `drillMetrics.ts`, `sanityBounds.ts`, `metricPrecision.ts` are
1:1 Kotlin mirrors (golden-parity); the feedback half — `referenceStandard.ts` (external IDEAL
ranges, NOT personal baseline — spec decision #2), `feedbackEngine.ts` (range-based severity),
`messageCatalog.ts` (EN, "vs ideal" wording), `cadencePolicy.ts`, `analyzeDrill.ts` — deliberately
diverges from Kotlin and has NO shared/ counterpart to golden against. `analyzeDrill` preserves the
M0 count-golden (detection on plain aspect). `#/strokes` now also shows a per-rep results table
(`DrillResultsTable.tsx`), metric on/off toggles, a drill-type selector, and `speechSynthesis`
spoken-feedback playback (`useSpokenFeedback.ts`, EN voice, audio default + text-only mode).
Reference ranges are PROVISIONAL (see referenceStandard.ts header).
```

- [ ] **Step 3: Typecheck + final full viewer run**

Run: `cd poses_viewer && npx tsc -b --noEmit && npx vitest run`
Expected: no type errors; all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add poses_viewer/src/components/StrokesPage.tsx poses_viewer/CLAUDE.md poses_viewer/src/hooks/useHashRoute.ts
git commit -m "docs(viewer): relabel page + document M1 simulator (M1)"
```

---

### Task 12: Reference-range re-verification follow-up

The provisional ranges in Task 0 must be re-grounded once the deep-research rate-limit resets. This task records the obligation; do it when unblocked.

**Files:**
- Modify: `docs/DESIGN_LIMITATIONS.md`

- [ ] **Step 1: Add a tracked limitation**

Append a new entry to `docs/DESIGN_LIMITATIONS.md` (use the file's existing L-NN numbering — pick the next free number):

```markdown
### L-NN: Drill-simulator ideal ranges are provisional (OPEN)

`poses_viewer/src/drill2d/referenceStandard.ts` ranges are provisional. The 2026-06-12
deep-research pass hit a session limit before adversarial verification ran (votes were 0-0,
i.e. unverified, NOT refuted). Measured biomechanics exist only for elbow/shoulder/knee, in
clinical flexion convention at slightly different stroke instants (converted to interior angles
here); torso lean and shoulder tilt have NO measured source and are coach-opinion. Re-run the
deep-research skill after the limit resets, verify the numbers, and tighten the bands +
evidence tags. Until then the UI surfaces the `evidence` flag so users see these are an
external provisional standard, not a calibrated target.
```

- [ ] **Step 2: Commit**

```bash
git add docs/DESIGN_LIMITATIONS.md
git commit -m "docs: track provisional drill-simulator ideal ranges (L-NN)"
```

- [ ] **Step 3: (When unblocked) re-run research and update Task 0 numbers**

Re-invoke the `deep-research` skill with the Task-0 question. If verified numbers land, update `referenceStandard.ts` ranges + `evidence`/`source` tags, adjust `referenceStandard.test.ts` if the coach-opinion flags change, run `cd poses_viewer && npx vitest run`, and move the L-NN limitation to RESOLVED in `docs/DESIGN_LIMITATIONS.md`.

---

## Self-review notes

- **Spec coverage:** stroke splits + timeline (M0, reused) ✓; per-rep 5-metric results (Task 4 + 9) ✓; feedback cues (Task 5/6) ✓; spoken playback at 3–5 s cadence, audio default (Task 7 + 10) ✓; configurable metrics/drill/detector knobs (Task 9, reusing M0 knobs) ✓; reimplement-in-TS with count-golden parity (Task 8) ✓; external-ideal reference, not baseline (Task 0/5, flagged) ✓; reference sourcing as a dedicated task (Task 0 + 12) ✓.
- **Divergence guardrails:** every feedback-half task states "no Kotlin golden"; every measurement-half task states "Kotlin is source of truth" and Task 8 pins the 23/15 count-golden.
- **Type consistency:** metric keys are the same five strings across `referenceStandard.METRIC_KEYS`, `drillMetrics.METRIC`/`ALL_KEYS`, `sanityBounds`, `metricPrecision`; `FeedbackCue` shape is shared by `feedbackEngine`/`messageCatalog`/`cadencePolicy`/`analyzeDrill`; `RepAnalysis`/`SpokenFeedback`/`DrillAnalysisReport` defined once in `analyzeDrill.ts` and imported by the UI.
- **Known soft spots:** Task 9's render test depends on jsdom + testing-library being configured — a pure-helper fallback (`metricStatus`) is specified if not. Task 10 step 7 is a manual browser smoke test (audio can't be asserted headlessly).
