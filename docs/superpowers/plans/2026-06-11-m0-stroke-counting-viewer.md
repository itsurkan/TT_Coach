# M0 Stroke-Counting Debug Harness (poses_viewer) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A `#/strokes` route in poses_viewer that runs a TypeScript mirror of the Kotlin stroke-detection chain (`StrokeDetector2D → ForwardStrokeFilter → RepFilter`) over `*_poses_rtm.json` and draws stroke splits on a clickable video timeline, gated by the Kotlin golden (23 raw peaks / 15 reps on andrii_1).

**Architecture:** Pure-function TS port in a new `poses_viewer/src/drill2d/` directory (the existing `src/drill/` is the unrelated 3D-mannequin FK code — do not touch it). Each TS module mirrors one Kotlin file 1:1 for reviewability. UI is a standalone route component that loads data itself (does not thread state through the 1776-line `App.tsx`). Spec: `docs/superpowers/specs/2026-06-11-drill-effectiveness-sim-design.md` (M0 section + binding fix-flow rule: Kotlin is source of truth; never "fix" a parity mismatch by changing TS behavior away from Kotlin or loosening goldens).

**Tech Stack:** TypeScript, React 18, Vite 6, vitest 4 (config lives in `poses_viewer/vite.config.ts` → `test:` block, node env, globals on). Tests read fixtures from `shared/src/commonTest/resources/fixtures/` via `fs` + repo-relative paths.

**Kotlin sources being ported (read each before porting its task):**
- `shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/StrokeDetector2D.kt`
- `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilter.kt`
- `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/RepFilter.kt`
- `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/ViewGeometry.kt`, `Stroke2D.kt`, `Coco17.kt`, `Keypoint2D.kt`, `PoseFrame2D.kt`, `Handedness.kt`
- `AngleCalculations2D.facingSign` only (lines ~113–124) + `DEFAULT_MIN_SCORE = 0.3f`

**Numeric parity note:** Kotlin uses `Float` (32-bit); JS numbers are 64-bit doubles. Do NOT simulate float32 (`Math.fround`) — port the math plainly. The goldens are **counts**, which are robust to low-order-bit drift on this signal (smoothed peaks are well separated from thresholds). If a count golden fails, the cause is a porting bug (wrong window conversion, off-by-one in a loop bound, missed score gate), not float width — debug by diffing per-stroke `{startFrame, peakFrame, endFrame, peakSpeed}` against a Kotlin diagnostic print, never by loosening the golden.

---

## File structure

```
poses_viewer/src/drill2d/            # NEW — TS mirror of the Kotlin detection chain
  types.ts                           # Keypoint2D, PoseFrame2D, Stroke2D, Handedness, Coco17
  median.ts                          # one median() (Kotlin has 5 private copies; TS consolidates)
  geometry.ts                        # xScaleFor() — mirrors ViewGeometry
  facing.ts                          # DEFAULT_MIN_SCORE, scored(), facingSign()
  strokeDetector2d.ts                # detectStrokes() — mirrors StrokeDetector2D
  forwardStrokeFilter.ts             # filterForwardStrokes() — mirrors ForwardStrokeFilter
  repFilter.ts                       # filterReps() — mirrors RepFilter
  parsePoseV2.ts                     # *_poses_rtm.json → PoseSequence2D (schema v2 only)
  countStrokes.ts                    # orchestrator: detect → forward → rep
  __tests__/
    core.test.ts                     # geometry + facing + median
    strokeDetector2d.test.ts         # mirrors StrokeDetector2DTest.kt (all 11 tests)
    forwardStrokeFilter.test.ts      # mirrors ForwardStrokeFilterTest.kt (all 8 tests)
    repFilter.test.ts                # mirrors RepFilterTest.kt (all 3 tests)
    golden.test.ts                   # andrii_1: 23 raw / 15 reps; video_2 invariants
poses_viewer/src/components/
  StrokesPage.tsx                    # NEW — #/strokes route component
  StrokeTimeline.tsx                 # NEW — bands + cursor + click-to-seek
poses_viewer/src/hooks/useHashRoute.ts   # MODIFY — add 'strokes' route
poses_viewer/src/App.tsx                 # MODIFY — early-return StrokesPage (~line 1025)
poses_viewer/CLAUDE.md                   # MODIFY — routes line + file map entries
```

All test/typecheck commands run from `poses_viewer/`.

---

### Task 1: Core types, median, geometry, facing

**Files:**
- Create: `poses_viewer/src/drill2d/types.ts`
- Create: `poses_viewer/src/drill2d/median.ts`
- Create: `poses_viewer/src/drill2d/geometry.ts`
- Create: `poses_viewer/src/drill2d/facing.ts`
- Test: `poses_viewer/src/drill2d/__tests__/core.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// poses_viewer/src/drill2d/__tests__/core.test.ts
import { describe, expect, it } from 'vitest'
import { median } from '../median'
import { xScaleFor, MAX_YAW_DEG } from '../geometry'
import { facingSign, DEFAULT_MIN_SCORE } from '../facing'
import { Coco17, Keypoint2D } from '../types'

describe('median', () => {
  it('odd count returns middle', () => expect(median([3, 1, 2])).toBe(2))
  it('even count returns mean of middles', () => expect(median([4, 1, 3, 2])).toBe(2.5))
})

describe('xScaleFor (mirrors ViewGeometry)', () => {
  it('yaw 0 returns aspect ratio unchanged', () => {
    expect(xScaleFor(1, 0)).toBe(1)
    expect(xScaleFor(720 / 1280, 0)).toBeCloseTo(0.5625, 6)
  })
  it('yaw 60 doubles the scale (1/cos60 = 2)', () => {
    expect(xScaleFor(1, 60)).toBeCloseTo(2, 4)
  })
  it('sign-irrelevant (cos is even)', () => {
    expect(xScaleFor(1, -30)).toBeCloseTo(xScaleFor(1, 30), 8)
  })
  it('rejects yaw beyond the hard math limit', () => {
    expect(() => xScaleFor(1, MAX_YAW_DEG + 1)).toThrow()
  })
})

describe('facingSign (mirrors AngleCalculations2D.facingSign)', () => {
  const kp = (noseX: number | null, noseScore = 1): Keypoint2D[] => {
    const out: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1 }))
    if (noseX !== null) out[Coco17.NOSE] = { x: noseX, y: 0.15, score: noseScore }
    return out
  }
  it('nose right of shoulder-mid → +1', () => expect(facingSign(kp(0.55), 0.5, DEFAULT_MIN_SCORE)).toBe(1))
  it('nose left of shoulder-mid → -1', () => expect(facingSign(kp(0.45), 0.5, DEFAULT_MIN_SCORE)).toBe(-1))
  it('dead-centered within epsilon → null', () => expect(facingSign(kp(0.5), 0.5, DEFAULT_MIN_SCORE)).toBeNull())
  it('gated nose falls back to ear midpoint', () => {
    const k = kp(0.55, 0.1) // nose below minScore
    k[Coco17.LEFT_EAR] = { x: 0.58, y: 0.15, score: 1 }
    k[Coco17.RIGHT_EAR] = { x: 0.56, y: 0.15, score: 1 }
    expect(facingSign(k, 0.5, DEFAULT_MIN_SCORE)).toBe(1)
  })
  it('all head keypoints gated → null', () => {
    const k = kp(0.55, 0.1)
    k[Coco17.LEFT_EAR] = { x: 0.58, y: 0.15, score: 0.1 }
    k[Coco17.RIGHT_EAR] = { x: 0.56, y: 0.15, score: 0.1 }
    expect(facingSign(k, 0.5, DEFAULT_MIN_SCORE)).toBeNull()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/core.test.ts`
Expected: FAIL — cannot resolve `../median`, `../geometry`, `../facing`, `../types`.

- [ ] **Step 3: Write the implementation**

```ts
// poses_viewer/src/drill2d/types.ts
/**
 * TS mirror of shared/ KMP 2D models (Keypoint2D, PoseFrame2D, Stroke2D,
 * Handedness, Coco17). Kotlin is the source of truth — see the M0 spec's
 * binding fix-flow rule.
 */
export interface Keypoint2D {
  x: number
  y: number
  score: number
}

/** One video frame of 2D keypoints. Empty keypoints = no person detected. */
export interface PoseFrame2D {
  frameIndex: number
  timestampMs: number
  keypoints: Keypoint2D[]
}

/** One detected stroke; frame fields index into the source frame list. */
export interface Stroke2D {
  strokeIndex: number
  startFrame: number
  peakFrame: number
  endFrame: number
  /** Smoothed wrist speed at the peak, in torso-lengths per second. */
  peakSpeed: number
}

export type Handedness = 'right' | 'left'

/** COCO-17 keypoint indices (docs/pose_json_schema_v2.md). Valid for Halpe26 indices 0–16 too. */
export const Coco17 = {
  NOSE: 0,
  LEFT_EYE: 1,
  RIGHT_EYE: 2,
  LEFT_EAR: 3,
  RIGHT_EAR: 4,
  LEFT_SHOULDER: 5,
  RIGHT_SHOULDER: 6,
  LEFT_ELBOW: 7,
  RIGHT_ELBOW: 8,
  LEFT_WRIST: 9,
  RIGHT_WRIST: 10,
  LEFT_HIP: 11,
  RIGHT_HIP: 12,
  LEFT_KNEE: 13,
  RIGHT_KNEE: 14,
  LEFT_ANKLE: 15,
  RIGHT_ANKLE: 16,
  wrist(h: Handedness): number {
    return h === 'right' ? Coco17.RIGHT_WRIST : Coco17.LEFT_WRIST
  },
} as const
```

```ts
// poses_viewer/src/drill2d/median.ts
/** Median of a non-empty list. Kotlin keeps 5 private copies; the TS port consolidates. */
export function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}
```

```ts
// poses_viewer/src/drill2d/geometry.ts
/**
 * Mirrors ViewGeometry: xScale = aspectRatio / cos(cameraYawDeg) — THE single
 * factor applied to x-deltas before any geometry (schema v2 normalizes x by
 * width, y by height). Sign of yaw is irrelevant (cos is even).
 */
export const MAX_YAW_DEG = 60

export function xScaleFor(aspectRatio: number, cameraYawDeg = 0): number {
  if (Math.abs(cameraYawDeg) > MAX_YAW_DEG) {
    throw new Error(`cameraYawDeg must be within ±${MAX_YAW_DEG}°, got ${cameraYawDeg}`)
  }
  return aspectRatio / Math.cos((cameraYawDeg * Math.PI) / 180)
}
```

```ts
// poses_viewer/src/drill2d/facing.ts
import { Coco17, Keypoint2D } from './types'

/** Score gate threshold — angle/speed functions ignore keypoints below this. */
export const DEFAULT_MIN_SCORE = 0.3
const FACING_EPSILON = 1e-3

/** The keypoint at idx if present and score ≥ minScore, else null. */
export function scored(kp: Keypoint2D[], idx: number, minScore: number): Keypoint2D | null {
  const k = kp[idx]
  return k !== undefined && k.score >= minScore ? k : null
}

/**
 * Mirrors AngleCalculations2D.facingSign: +1 when the head (nose, or ear
 * midpoint fallback) is right of shoulderMidX, −1 when left, null when gated
 * or dead-centered. KNOWN NOISY on real footage (L-04) — used only as the
 * ForwardStrokeFilter fallback when speed dominance is inconclusive.
 */
export function facingSign(kp: Keypoint2D[], shoulderMidX: number, minScore: number): number | null {
  const nose = scored(kp, Coco17.NOSE, minScore)
  let headX: number | null = nose !== null ? nose.x : null
  if (headX === null) {
    const le = scored(kp, Coco17.LEFT_EAR, minScore)
    const re = scored(kp, Coco17.RIGHT_EAR, minScore)
    headX = le !== null && re !== null ? (le.x + re.x) / 2 : null
  }
  if (headX === null) return null
  const offset = headX - shoulderMidX
  if (Math.abs(offset) < FACING_EPSILON) return null
  return offset > 0 ? 1 : -1
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/core.test.ts`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/types.ts poses_viewer/src/drill2d/median.ts poses_viewer/src/drill2d/geometry.ts poses_viewer/src/drill2d/facing.ts poses_viewer/src/drill2d/__tests__/core.test.ts
git commit -m "feat(poses_viewer): drill2d core — types, median, xScale, facingSign (TS mirror of shared KMP)"
```

---

### Task 2: Stroke detector port

**Files:**
- Create: `poses_viewer/src/drill2d/strokeDetector2d.ts`
- Test: `poses_viewer/src/drill2d/__tests__/strokeDetector2d.test.ts`

The test file is a 1:1 translation of `StrokeDetector2DTest.kt` — every scenario, including the keep-max-NMS, valley-clamp, and fps-invariance regression tests. Do not skip any: they encode tuning bugs already found and fixed in Kotlin.

- [ ] **Step 1: Write the failing test**

```ts
// poses_viewer/src/drill2d/__tests__/strokeDetector2d.test.ts
import { describe, expect, it } from 'vitest'
import { detectStrokes } from '../strokeDetector2d'
import { Coco17, Keypoint2D, PoseFrame2D } from '../types'

/**
 * Mirrors StrokeDetector2DTest.kt. Frames where only the right wrist x moves;
 * shoulders/hips fixed with torso length 0.25, so speeds are well-defined in
 * torso-lengths/sec. Peak raw speed of singleStrokeXs at 100 ms: 2.4 torso/s.
 */
function framesFromWristXs(xs: number[], intervalMs = 100): PoseFrame2D[] {
  return xs.map((wx, i) => {
    const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1 }))
    kp[Coco17.LEFT_SHOULDER] = { x: 0.49, y: 0.30, score: 1 }
    kp[Coco17.RIGHT_SHOULDER] = { x: 0.51, y: 0.30, score: 1 }
    kp[Coco17.LEFT_HIP] = { x: 0.49, y: 0.55, score: 1 }
    kp[Coco17.RIGHT_HIP] = { x: 0.51, y: 0.55, score: 1 }
    kp[Coco17.RIGHT_WRIST] = { x: wx, y: 0.5, score: 1 }
    return { frameIndex: i, timestampMs: i * intervalMs, keypoints: kp }
  })
}

function withWristScore(frames: PoseFrame2D[], indices: 'all' | number[], score: number): PoseFrame2D[] {
  return frames.map((f, i) => {
    if (indices !== 'all' && !indices.includes(i)) return f
    const kp = f.keypoints.map((k, j) => (j === Coco17.RIGHT_WRIST ? { ...k, score } : k))
    return { ...f, keypoints: kp }
  })
}

/** Linear 2× resample: same motion at half the frame interval. */
function resample2x(xs: number[]): number[] {
  return xs.flatMap((x, i) => (i === xs.length - 1 ? [x] : [x, (x + xs[i + 1]) / 2]))
}

// still — accelerate to peak — decelerate — still
const singleStrokeXs = [
  0.50, 0.50, 0.50, 0.50,
  0.51, 0.53, 0.57, 0.63, 0.68, 0.71, 0.72,
  0.72, 0.72, 0.72, 0.72,
]

describe('detectStrokes (mirrors StrokeDetector2DTest)', () => {
  it('detects a single stroke', () => {
    const strokes = detectStrokes(framesFromWristXs(singleStrokeXs), 'right', 1, 100)
    expect(strokes).toHaveLength(1)
    const s = strokes[0]
    expect(s.peakFrame).toBeGreaterThanOrEqual(6)
    expect(s.peakFrame).toBeLessThanOrEqual(8)
    expect(s.startFrame).toBeLessThan(s.peakFrame)
    expect(s.endFrame).toBeGreaterThan(s.peakFrame)
    expect(s.strokeIndex).toBe(0)
  })

  it('detects two strokes with a gap', () => {
    const xs = [...singleStrokeXs, ...[...singleStrokeXs].reverse()]
    const strokes = detectStrokes(framesFromWristXs(xs), 'right', 1, 100)
    expect(strokes).toHaveLength(2)
    expect(strokes[1].peakFrame - strokes[0].peakFrame).toBeGreaterThanOrEqual(5)
    expect(strokes.map(s => s.strokeIndex)).toEqual([0, 1])
  })

  it('sub-threshold jitter yields no strokes', () => {
    const xs = Array.from({ length: 30 }, (_, i) => 0.5 + (i % 2 === 0 ? 0.002 : -0.002))
    expect(detectStrokes(framesFromWristXs(xs), 'right', 1, 100)).toHaveLength(0)
  })

  it('ms-based tuning survives an fps change (L-02)', () => {
    const xs50 = resample2x(singleStrokeXs)
    const strokes = detectStrokes(framesFromWristXs(xs50, 50), 'right', 1, 50)
    expect(strokes).toHaveLength(1)
  })

  it('low-score wrist frames contribute zero speed', () => {
    const frames = withWristScore(framesFromWristXs(singleStrokeXs), 'all', 0.1)
    expect(detectStrokes(frames, 'right', 1, 100)).toHaveLength(0)
  })

  it('empty and tiny inputs are safe', () => {
    expect(detectStrokes([], 'right', 1, 100)).toHaveLength(0)
    expect(detectStrokes(framesFromWristXs([0.5]), 'right', 1, 100)).toHaveLength(0)
  })

  it('detection is deterministic', () => {
    const frames = framesFromWristXs(singleStrokeXs)
    expect(detectStrokes(frames, 'right', 1, 100)).toEqual(detectStrokes(frames, 'right', 1, 100))
  })

  it('refractory keeps the taller of two nearby peaks (keep-max NMS)', () => {
    const xs = [
      0.50, 0.50, 0.50,
      0.54, 0.58, 0.605, 0.610,
      0.63, 0.70, 0.74,
      0.75, 0.75, 0.75,
    ]
    const strokes = detectStrokes(framesFromWristXs(xs), 'right', 1, 100)
    expect(strokes).toHaveLength(1)
    expect(strokes[0].peakFrame).toBeGreaterThanOrEqual(8)
  })

  it('adjacent strokes never overlap (valley clamp)', () => {
    const xs = [0.50, 0.52, 0.56, 0.62, 0.68, 0.72, 0.72, 0.68, 0.62, 0.56, 0.52, 0.50]
    const strokes = detectStrokes(framesFromWristXs(xs), 'right', 1, 100)
    expect(strokes).toHaveLength(2)
    expect(strokes[0].endFrame).toBeLessThanOrEqual(strokes[1].startFrame)
  })

  it('ms-based smoothing gives the same stroke count at any fps', () => {
    const xs100 = [
      0.50, 0.52, 0.56, 0.62,
      0.62, 0.645, 0.675, 0.660, 0.640,
      0.650, 0.670, 0.720, 0.790, 0.830,
      0.830, 0.830,
    ]
    const at100 = detectStrokes(framesFromWristXs(xs100), 'right', 1, 100)
    const at50 = detectStrokes(framesFromWristXs(resample2x(xs100), 50), 'right', 1, 50)
    expect(at100).toHaveLength(2)
    expect(at50).toHaveLength(at100.length)
  })

  it('ms-based minGap suppresses sub-gap peaks at any fps', () => {
    const xs100 = [
      0.50, 0.50, 0.53, 0.61, 0.64, 0.655, 0.685, 0.735,
      0.765, 0.765, 0.765, 0.765,
    ]
    const opts = { peakWindowRadiusMs: 100 }
    const at100 = detectStrokes(framesFromWristXs(xs100), 'right', 1, 100, opts)
    const at50 = detectStrokes(framesFromWristXs(resample2x(xs100), 50), 'right', 1, 50, opts)
    expect(at100).toHaveLength(1)
    expect(at50).toHaveLength(1)
  })

  it('brief mid-stroke occlusion does not split the stroke', () => {
    const frames = withWristScore(framesFromWristXs(singleStrokeXs), [6, 7], 0.1)
    const strokes = detectStrokes(frames, 'right', 1, 100)
    expect(strokes.length).toBeLessThanOrEqual(1)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/strokeDetector2d.test.ts`
Expected: FAIL — cannot resolve `../strokeDetector2d`.

- [ ] **Step 3: Write the implementation**

Faithful port of `StrokeDetector2D.kt` — same algorithm, same defaults, same order of operations. The Kotlin class constructor becomes an options argument; `DETECTOR_DEFAULTS` is exported so the UI knobs can show the real defaults.

```ts
// poses_viewer/src/drill2d/strokeDetector2d.ts
import { Coco17, Handedness, PoseFrame2D, Stroke2D } from './types'
import { DEFAULT_MIN_SCORE, scored } from './facing'

/**
 * Mirrors StrokeDetector2D.kt: wrist-speed local maxima with keep-max NMS and
 * valley-clamped boundaries. Speeds are TORSO-LENGTHS PER SECOND (invariant to
 * camera distance and fps); all tuning windows are MILLISECONDS converted to
 * frame counts via intervalMs.
 */
export interface StrokeDetectorOptions {
  minScore?: number
  smoothingWindowMs?: number
  peakWindowRadiusMs?: number
  /** Torso-lengths/sec, applied to the SMOOTHED signal. */
  minPeakSpeed?: number
  boundaryFraction?: number
  minPeakGapMs?: number
}

export const DETECTOR_DEFAULTS: Required<StrokeDetectorOptions> = {
  minScore: DEFAULT_MIN_SCORE,
  smoothingWindowMs: 300,
  peakWindowRadiusMs: 300,
  minPeakSpeed: 1.0,
  boundaryFraction: 0.3,
  minPeakGapMs: 500,
}

const MIN_TORSO_LEN = 1e-4

export function detectStrokes(
  frames: PoseFrame2D[],
  handedness: Handedness,
  xScale: number,
  intervalMs: number,
  options: StrokeDetectorOptions = {},
): Stroke2D[] {
  const opts = { ...DETECTOR_DEFAULTS, ...options }
  if (intervalMs <= 0) throw new Error(`intervalMs must be > 0, got ${intervalMs}`)
  if (frames.length < 2) return []
  const torsoLen = medianTorsoLength(frames, xScale, opts.minScore)
  if (torsoLen === null) return []

  const speed = smooth(
    rawWristSpeeds(frames, handedness, xScale, torsoLen, intervalMs, opts.minScore),
    framesFor(opts.smoothingWindowMs, intervalMs),
  )
  const peaks = findPeaks(
    speed,
    framesFor(opts.peakWindowRadiusMs, intervalMs),
    framesFor(opts.minPeakGapMs, intervalMs),
    opts.minPeakSpeed,
  )
  const strokes: Stroke2D[] = peaks.map((p, idx) => {
    const floor = speed[p] * opts.boundaryFraction
    let start = p
    while (start > 0 && speed[start - 1] > floor) start--
    let end = p
    while (end < speed.length - 1 && speed[end + 1] > floor) end++
    return { strokeIndex: idx, startFrame: start, peakFrame: p, endFrame: end, peakSpeed: speed[p] }
  })
  // Valley-clamp: ensure adjacent strokes never overlap.
  for (let i = 0; i < strokes.length - 1; i++) {
    const a = strokes[i]
    const b = strokes[i + 1]
    if (a.endFrame >= b.startFrame) {
      let valley = a.peakFrame + 1
      for (let j = a.peakFrame + 1; j <= b.peakFrame; j++) {
        if (speed[j] < speed[valley]) valley = j
      }
      strokes[i] = { ...a, endFrame: valley }
      strokes[i + 1] = { ...b, startFrame: valley }
    }
  }
  return strokes
}

/** ms → frame count at the given interval, never below 1. */
function framesFor(ms: number, intervalMs: number): number {
  return Math.max(1, Math.floor(ms / intervalMs))
}

function rawWristSpeeds(
  frames: PoseFrame2D[],
  handedness: Handedness,
  xScale: number,
  torsoLen: number,
  intervalMs: number,
  minScore: number,
): number[] {
  const wristIdx = Coco17.wrist(handedness)
  const dtSec = intervalMs / 1000
  const raw = new Array<number>(frames.length).fill(0)
  for (let i = 1; i < frames.length; i++) {
    const prev = scored(frames[i - 1].keypoints, wristIdx, minScore)
    const curr = scored(frames[i].keypoints, wristIdx, minScore)
    raw[i] =
      prev === null || curr === null
        ? 0
        : Math.hypot((curr.x - prev.x) * xScale, curr.y - prev.y) / torsoLen / dtSec
  }
  return raw
}

/** Median xScale-corrected shoulder-mid→hip-mid distance; null if never measurable. */
function medianTorsoLength(frames: PoseFrame2D[], xScale: number, minScore: number): number | null {
  const lens: number[] = []
  for (const f of frames) {
    const kp = f.keypoints
    const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
    const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
    const lh = scored(kp, Coco17.LEFT_HIP, minScore)
    const rh = scored(kp, Coco17.RIGHT_HIP, minScore)
    if (ls === null || rs === null || lh === null || rh === null) continue
    const len = Math.hypot(
      ((ls.x + rs.x - (lh.x + rh.x)) / 2) * xScale,
      (ls.y + rs.y - (lh.y + rh.y)) / 2,
    )
    if (len >= MIN_TORSO_LEN) lens.push(len)
  }
  if (lens.length === 0) return null
  const sorted = lens.sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

/** Centered box-average; an even window widens by one (window=2 behaves as 3). */
function smooth(raw: number[], window: number): number[] {
  if (window <= 1) return raw
  const half = Math.floor(window / 2)
  const out = new Array<number>(raw.length)
  for (let i = 0; i < raw.length; i++) {
    const lo = Math.max(0, i - half)
    const hi = Math.min(raw.length - 1, i + half)
    let sum = 0
    for (let j = lo; j <= hi; j++) sum += raw[j]
    out[i] = sum / (hi - lo + 1)
  }
  return out
}

/**
 * Local-maximum peak finding with keep-max NMS refractory: a candidate within
 * minGap of the previously admitted peak REPLACES it when taller, so a small
 * early bump cannot block a taller stroke peak.
 */
function findPeaks(speed: number[], radius: number, minGap: number, minPeakSpeed: number): number[] {
  const peaks: number[] = []
  for (let i = 0; i < speed.length; i++) {
    if (speed[i] < minPeakSpeed) continue
    const lo = Math.max(0, i - radius)
    const hi = Math.min(speed.length - 1, i + radius)
    let isPeak = true
    for (let j = lo; j <= hi; j++) {
      // strictly greater than earlier frames → first index of a plateau wins
      if (j < i && speed[j] >= speed[i]) { isPeak = false; break }
      if (j > i && speed[j] > speed[i]) { isPeak = false; break }
    }
    if (!isPeak) continue
    if (peaks.length === 0 || i - peaks[peaks.length - 1] >= minGap) {
      peaks.push(i)
    } else if (speed[i] > speed[peaks[peaks.length - 1]]) {
      peaks[peaks.length - 1] = i
    }
  }
  return peaks
}
```

Note `medianTorsoLength` does not reuse `median.ts` directly because it filters as it collects — but the final median math must be identical. Keep the loop structure shown above (it mirrors the Kotlin `mapNotNull` + sorted-median).

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/strokeDetector2d.test.ts`
Expected: PASS (12 tests). If `keep-max NMS` or `valley clamp` tests fail, re-diff your port against `StrokeDetector2D.kt` lines 80–94 and 173–194 — those encode fixed bugs.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/strokeDetector2d.ts poses_viewer/src/drill2d/__tests__/strokeDetector2d.test.ts
git commit -m "feat(poses_viewer): drill2d stroke detector — TS port of StrokeDetector2D with full test mirror"
```

---

### Task 3: Forward-stroke filter port

**Files:**
- Create: `poses_viewer/src/drill2d/forwardStrokeFilter.ts`
- Test: `poses_viewer/src/drill2d/__tests__/forwardStrokeFilter.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// poses_viewer/src/drill2d/__tests__/forwardStrokeFilter.test.ts
import { describe, expect, it } from 'vitest'
import { filterForwardStrokes } from '../forwardStrokeFilter'
import { Coco17, Keypoint2D, PoseFrame2D, Stroke2D } from '../types'

/** Mirrors ForwardStrokeFilterTest.kt. Player faces +x: nose ahead of shoulder-mid. */
function frames(wristXs: number[], noseX = 0.55): PoseFrame2D[] {
  return wristXs.map((wx, i) => {
    const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0.5, y: 0.5, score: 1 }))
    kp[Coco17.NOSE] = { x: noseX, y: 0.15, score: 1 }
    kp[Coco17.LEFT_SHOULDER] = { x: 0.49, y: 0.30, score: 1 }
    kp[Coco17.RIGHT_SHOULDER] = { x: 0.51, y: 0.30, score: 1 }
    kp[Coco17.LEFT_HIP] = { x: 0.49, y: 0.55, score: 1 }
    kp[Coco17.RIGHT_HIP] = { x: 0.51, y: 0.55, score: 1 }
    kp[Coco17.RIGHT_WRIST] = { x: wx, y: 0.5, score: 1 }
    return { frameIndex: i, timestampMs: i * 100, keypoints: kp }
  })
}

const stroke = (start: number, peak: number, end: number): Stroke2D =>
  ({ strokeIndex: 0, startFrame: start, peakFrame: peak, endFrame: end, peakSpeed: 2.4 })

/**
 * Session of strokes with controlled dx sign + peakSpeed: stroke i owns frames
 * [3i, 3i+2], wrist sweeping 0.5 → 0.5 + sign·0.1 → 0.5.
 */
function session(specs: Array<[number, number]>, noseX: number): [PoseFrame2D[], Stroke2D[]] {
  const xs: number[] = []
  const strokes: Stroke2D[] = []
  specs.forEach(([sign, speed], i) => {
    const base = 3 * i
    xs.push(0.5, 0.5 + sign * 0.1, 0.5)
    strokes.push({ strokeIndex: i, startFrame: base, peakFrame: base + 1, endFrame: base + 2, peakSpeed: speed })
  })
  return [frames(xs, noseX), strokes]
}

describe('filterForwardStrokes (mirrors ForwardStrokeFilterTest)', () => {
  it('keeps forward stroke, drops recovery swing', () => {
    const xs = [0.50, 0.55, 0.62, 0.70, 0.72, 0.68, 0.60, 0.53, 0.50]
    const f = frames(xs)
    const forward = stroke(0, 3, 4)
    const recovery = stroke(4, 7, 8)
    expect(filterForwardStrokes([forward, recovery], f, 'right')).toEqual([forward])
  })

  it('mirrored player keeps mirrored forward stroke', () => {
    const xs = [0.50, 0.45, 0.38, 0.30, 0.28, 0.32, 0.40, 0.47, 0.50]
    const f = frames(xs, 0.45)
    const forward = stroke(0, 3, 4)
    const recovery = stroke(4, 7, 8)
    expect(filterForwardStrokes([forward, recovery], f, 'right')).toEqual([forward])
  })

  it('indeterminate direction is dropped', () => {
    const xs = [0.50, 0.55, 0.62, 0.70, 0.72]
    const f = frames(xs, 0.50) // nose dead-centered → facing unknown
    expect(filterForwardStrokes([stroke(0, 3, 4)], f, 'right')).toEqual([])
  })

  it('speed dominance keeps the fast group (+x), overriding contradicting head facing', () => {
    const [f, strokes] = session([[1, 8.0], [1, 8.2], [1, 7.9], [-1, 6.0], [-1, 6.1]], 0.45)
    expect(filterForwardStrokes(strokes, f, 'right')).toEqual(strokes.slice(0, 3))
  })

  it('speed dominance keeps the fast group (−x), overriding contradicting head facing', () => {
    const [f, strokes] = session([[-1, 8.0], [-1, 8.2], [-1, 7.9], [1, 6.0], [1, 6.1]], 0.55)
    expect(filterForwardStrokes(strokes, f, 'right')).toEqual(strokes.slice(0, 3))
  })

  it('speed tie falls back to head facing', () => {
    const [f, strokes] = session([[1, 6.0], [1, 6.1], [-1, 6.5], [-1, 6.6]], 0.55)
    expect(filterForwardStrokes(strokes, f, 'right')).toEqual(strokes.slice(0, 2))
  })

  it('a single junk spike cannot flip the vote', () => {
    const [f, strokes] = session([[1, 6.0], [1, 6.0], [1, 6.0], [1, 6.0], [-1, 9.0]], 0.55)
    expect(filterForwardStrokes(strokes, f, 'right')).toEqual(strokes.slice(0, 4))
  })

  it('gated wrist is dropped', () => {
    const xs = [0.50, 0.55, 0.62, 0.70, 0.72]
    const f = frames(xs).map(fr => ({
      ...fr,
      keypoints: fr.keypoints.map((k, j) => (j === Coco17.RIGHT_WRIST ? { ...k, score: 0.1 } : k)),
    }))
    expect(filterForwardStrokes([stroke(0, 3, 4)], f, 'right')).toEqual([])
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/forwardStrokeFilter.test.ts`
Expected: FAIL — cannot resolve `../forwardStrokeFilter`.

- [ ] **Step 3: Write the implementation**

```ts
// poses_viewer/src/drill2d/forwardStrokeFilter.ts
import { Coco17, Handedness, PoseFrame2D, Stroke2D } from './types'
import { DEFAULT_MIN_SCORE, facingSign, scored } from './facing'
import { median } from './median'

/**
 * Mirrors ForwardStrokeFilter.kt: drops wrist-speed peaks that are NOT forward
 * strokes (~half of all peaks on real footage are recovery swings). Session
 * facing comes from the speed-dominance vote over wrist-dx groups; the noisy
 * per-frame head read is only the fallback. Unverifiable strokes are dropped.
 */
export const SPEED_DOMINANCE_RATIO = 1.2
export const MIN_GROUP_SIZE = 2

export function filterForwardStrokes(
  strokes: Stroke2D[],
  frames: PoseFrame2D[],
  handedness: Handedness,
  minScore: number = DEFAULT_MIN_SCORE,
): Stroke2D[] {
  const verified: Array<{ stroke: Stroke2D; dx: number }> = []
  for (const stroke of strokes) {
    const dx = wristDx(stroke, frames, handedness, minScore)
    if (dx !== null) verified.push({ stroke, dx })
  }
  const facing = speedDominantFacing(verified)
  if (facing !== null) {
    return verified.filter(v => v.dx * facing > 0).map(v => v.stroke)
  }
  return verified
    .filter(v => {
      const head = headFacingAtStart(v.stroke, frames, minScore)
      return head !== null && v.dx * head > 0
    })
    .map(v => v.stroke)
}

/** Wrist x-displacement start→peak; null when the wrist is gated at either end. */
function wristDx(
  stroke: Stroke2D,
  frames: PoseFrame2D[],
  handedness: Handedness,
  minScore: number,
): number | null {
  const wristIdx = Coco17.wrist(handedness)
  const startKp = frames[stroke.startFrame]?.keypoints
  const peakKp = frames[stroke.peakFrame]?.keypoints
  if (startKp === undefined || peakKp === undefined) return null
  const start = scored(startKp, wristIdx, minScore)
  const peak = scored(peakKp, wristIdx, minScore)
  if (start === null || peak === null) return null
  return peak.x - start.x
}

/**
 * Session facing from speed asymmetry: ±1 when one dx-sign group's median peak
 * speed dominates the other by SPEED_DOMINANCE_RATIO; null when either group
 * has fewer than MIN_GROUP_SIZE strokes or neither dominates.
 */
function speedDominantFacing(verified: Array<{ stroke: Stroke2D; dx: number }>): number | null {
  const posSpeeds = verified.filter(v => v.dx > 0).map(v => v.stroke.peakSpeed)
  const negSpeeds = verified.filter(v => v.dx < 0).map(v => v.stroke.peakSpeed)
  if (posSpeeds.length < MIN_GROUP_SIZE || negSpeeds.length < MIN_GROUP_SIZE) return null
  const posMed = median(posSpeeds)
  const negMed = median(negSpeeds)
  if (posMed >= negMed * SPEED_DOMINANCE_RATIO) return 1
  if (negMed >= posMed * SPEED_DOMINANCE_RATIO) return -1
  return null
}

function headFacingAtStart(stroke: Stroke2D, frames: PoseFrame2D[], minScore: number): number | null {
  const kp = frames[stroke.startFrame]?.keypoints
  if (kp === undefined) return null
  const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
  const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
  if (ls === null || rs === null) return null
  return facingSign(kp, (ls.x + rs.x) / 2, minScore)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/forwardStrokeFilter.test.ts`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/forwardStrokeFilter.ts poses_viewer/src/drill2d/__tests__/forwardStrokeFilter.test.ts
git commit -m "feat(poses_viewer): drill2d forward-stroke filter — TS port of ForwardStrokeFilter"
```

---

### Task 4: Rep filter port

**Files:**
- Create: `poses_viewer/src/drill2d/repFilter.ts`
- Test: `poses_viewer/src/drill2d/__tests__/repFilter.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// poses_viewer/src/drill2d/__tests__/repFilter.test.ts
import { describe, expect, it } from 'vitest'
import { filterReps } from '../repFilter'
import { Stroke2D } from '../types'

/** Mirrors RepFilterTest.kt. */
const stroke = (i: number, peakSpeed: number, durFrames: number): Stroke2D => ({
  strokeIndex: i,
  startFrame: i * 30,
  peakFrame: i * 30 + Math.floor(durFrames / 2),
  endFrame: i * 30 + durFrames,
  peakSpeed,
})

describe('filterReps (mirrors RepFilterTest)', () => {
  it('uniform strokes all kept', () => {
    const s = Array.from({ length: 6 }, (_, i) => stroke(i, 2.4, 6))
    expect(filterReps(s)).toEqual(s)
  })

  it('slow and overlong junk dropped', () => {
    const good = Array.from({ length: 6 }, (_, i) => stroke(i, 2.4, 6))
    const slow = stroke(6, 1.1, 6)   // ball pickup: above detector threshold, half the cluster speed
    const smear = stroke(7, 2.4, 20) // walking: long movement, plausible peak
    expect(filterReps([...good, slow, smear])).toEqual(good)
  })

  it('too few strokes are not filtered', () => {
    const s = [stroke(0, 2.4, 6), stroke(1, 0.5, 30), stroke(2, 5, 2)]
    expect(filterReps(s)).toEqual(s)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/repFilter.test.ts`
Expected: FAIL — cannot resolve `../repFilter`.

- [ ] **Step 3: Write the implementation**

```ts
// poses_viewer/src/drill2d/repFilter.ts
import { Stroke2D } from './types'
import { median } from './median'

/**
 * Mirrors RepFilter.kt: keeps strokes whose peak speed AND duration lie within
 * [median/BAND, median×BAND] of the session medians. Runs AFTER
 * filterForwardStrokes, so the medians describe forward strokes only — do not
 * reorder (CLAUDE.md gotcha: reordering silently corrupts results).
 */
export const MIN_STROKES_TO_FILTER = 4
export const SPEED_BAND = 2.0
export const DURATION_BAND = 2.0

export function filterReps(strokes: Stroke2D[]): Stroke2D[] {
  if (strokes.length < MIN_STROKES_TO_FILTER) return strokes
  const medSpeed = median(strokes.map(s => s.peakSpeed))
  const medDur = median(strokes.map(s => s.endFrame - s.startFrame))
  return strokes.filter(s => {
    const dur = s.endFrame - s.startFrame
    return (
      s.peakSpeed >= medSpeed / SPEED_BAND &&
      s.peakSpeed <= medSpeed * SPEED_BAND &&
      dur >= medDur / DURATION_BAND &&
      dur <= medDur * DURATION_BAND
    )
  })
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/repFilter.test.ts`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/repFilter.ts poses_viewer/src/drill2d/__tests__/repFilter.test.ts
git commit -m "feat(poses_viewer): drill2d rep filter — TS port of RepFilter"
```

---

### Task 5: Schema-v2 parser, orchestrator, GOLDEN parity test

**Files:**
- Create: `poses_viewer/src/drill2d/parsePoseV2.ts`
- Create: `poses_viewer/src/drill2d/countStrokes.ts`
- Test: `poses_viewer/src/drill2d/__tests__/golden.test.ts`

Fixture facts (verified): `shared/src/commonTest/resources/fixtures/andrii_1_rtm.json` has `schemaVersion: 2`, `topology: "coco17"`, `intervalMs: 17`, `videoWidth: 720`, `videoHeight: 1280`, frames with a `landmarks` array of `{index, x, y, score}` objects. The Kotlin golden (ForehandDriveEndToEndTest + CLAUDE.md exit gate): **23 raw detector peaks, 15 reps after both filters, with `handedness = RIGHT`, `cameraYawDeg = 0`** (yaw pinned because the fixture predates the camera-placement protocol).

- [ ] **Step 1: Write the failing test**

```ts
// poses_viewer/src/drill2d/__tests__/golden.test.ts
import fs from 'fs'
import path from 'path'
import { describe, expect, it } from 'vitest'
import { parsePoseV2 } from '../parsePoseV2'
import { countStrokes } from '../countStrokes'

// __dirname = poses_viewer/src/drill2d/__tests__ → repo root is 4 levels up
const FIXTURES = path.resolve(__dirname, '../../../../shared/src/commonTest/resources/fixtures')

const load = (name: string) =>
  parsePoseV2(JSON.parse(fs.readFileSync(path.join(FIXTURES, name), 'utf-8')))

describe('parsePoseV2', () => {
  it('parses andrii_1_rtm metadata', () => {
    const seq = load('andrii_1_rtm.json')
    expect(seq.topology).toBe('coco17')
    expect(seq.intervalMs).toBe(17)
    expect(seq.aspectRatio).toBeCloseTo(720 / 1280, 6)
    expect(seq.frames.length).toBeGreaterThan(1000)
    expect(seq.frames[0].keypoints).toHaveLength(17)
    expect(seq.frames[0].keypoints[0].score).toBeGreaterThan(0)
  })

  it('rejects legacy schema-v1 (MediaPipe-33) files', () => {
    expect(() => parsePoseV2({ frames: [] })).toThrow(/schemaVersion/)
    expect(() => parsePoseV2({ schemaVersion: 1, frames: [] })).toThrow(/schemaVersion/)
  })
})

describe('GOLDEN parity vs Kotlin E2E (ForehandDriveEndToEndTest)', () => {
  // Kotlin source of truth: 23 raw peaks, 15 reps, handedness RIGHT, yaw 0.
  // If this fails, the TS port is the bug — diff per-stroke output against a
  // Kotlin diagnostic; NEVER loosen these numbers to make TS pass.
  it('andrii_1: 23 raw peaks → 15 reps', () => {
    const seq = load('andrii_1_rtm.json')
    const r = countStrokes(seq, { handedness: 'right', cameraYawDeg: 0 })
    expect(r.rawStrokes).toHaveLength(23)
    expect(r.forwardStrokes.length).toBeLessThan(r.rawStrokes.length) // filter must be alive
    expect(r.reps).toHaveLength(15)
    // eslint-disable-next-line no-console
    console.log(`andrii_1: raw=${r.rawStrokes.length} forward=${r.forwardStrokes.length} reps=${r.reps.length}`)
  })

  it('video_2: pipeline invariants hold', () => {
    const seq = load('video_2_rtm.json')
    expect(seq.intervalMs).toBe(20)
    const r = countStrokes(seq, { handedness: 'right', cameraYawDeg: 0 })
    expect(r.rawStrokes.length).toBeGreaterThan(0)
    expect(r.forwardStrokes.length).toBeLessThanOrEqual(r.rawStrokes.length)
    expect(r.reps.length).toBeLessThanOrEqual(r.forwardStrokes.length)
    // eslint-disable-next-line no-console
    console.log(`video_2: raw=${r.rawStrokes.length} forward=${r.forwardStrokes.length} reps=${r.reps.length}`)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/golden.test.ts`
Expected: FAIL — cannot resolve `../parsePoseV2` / `../countStrokes`.

- [ ] **Step 3: Write the implementation**

```ts
// poses_viewer/src/drill2d/parsePoseV2.ts
import { Keypoint2D, PoseFrame2D } from './types'

/**
 * Parses pose JSON schema v2 (docs/pose_json_schema_v2.md) — the *_poses_rtm.json
 * RTMPose exports. Mirrors PoseJsonV2Parser's strictness: schemaVersion must be 2
 * (legacy MediaPipe-33 v1 files have no schemaVersion and are a different format).
 */
export interface PoseSequence2D {
  topology: 'coco17' | 'halpe26'
  intervalMs: number
  videoWidth: number
  videoHeight: number
  videoDurationMs: number
  aspectRatio: number
  frames: PoseFrame2D[]
}

export function parsePoseV2(raw: unknown): PoseSequence2D {
  const root = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
  if (root.schemaVersion !== 2) {
    throw new Error(`expected schemaVersion 2, got ${JSON.stringify(root.schemaVersion)} — legacy v1 files are not supported here`)
  }
  const topology = root.topology
  if (topology !== 'coco17' && topology !== 'halpe26') {
    throw new Error(`unknown topology: ${JSON.stringify(topology)}`)
  }
  const kpCount = topology === 'halpe26' ? 26 : 17
  const intervalMs = requireNumber(root, 'intervalMs')
  const videoWidth = requireNumber(root, 'videoWidth')
  const videoHeight = requireNumber(root, 'videoHeight')
  const videoDurationMs = requireNumber(root, 'videoDurationMs')
  const rawFrames = Array.isArray(root.frames) ? root.frames : []

  const frames: PoseFrame2D[] = rawFrames.map((rf, idx) => {
    const f = (rf && typeof rf === 'object' ? rf : {}) as Record<string, unknown>
    const rawLms = Array.isArray(f.landmarks) ? f.landmarks : []
    let keypoints: Keypoint2D[] = []
    if (rawLms.length > 0) {
      keypoints = Array.from({ length: kpCount }, () => ({ x: 0, y: 0, score: 0 }))
      rawLms.forEach((lm, i) => {
        const o = (lm && typeof lm === 'object' ? lm : {}) as Record<string, unknown>
        const index = typeof o.index === 'number' ? o.index : i
        if (index >= 0 && index < kpCount) {
          keypoints[index] = { x: Number(o.x), y: Number(o.y), score: Number(o.score) }
        }
      })
    }
    return {
      frameIndex: typeof f.frameIndex === 'number' ? f.frameIndex : idx,
      timestampMs: typeof f.timestampMs === 'number' ? f.timestampMs : idx * intervalMs,
      keypoints,
    }
  })

  return {
    topology,
    intervalMs,
    videoWidth,
    videoHeight,
    videoDurationMs,
    aspectRatio: videoWidth / videoHeight,
    frames,
  }
}

function requireNumber(root: Record<string, unknown>, field: string): number {
  const v = root[field]
  if (typeof v !== 'number' || !Number.isFinite(v)) throw new Error(`missing/invalid ${field}`)
  return v
}
```

```ts
// poses_viewer/src/drill2d/countStrokes.ts
import { Handedness, Stroke2D } from './types'
import { xScaleFor } from './geometry'
import { detectStrokes, StrokeDetectorOptions } from './strokeDetector2d'
import { filterForwardStrokes } from './forwardStrokeFilter'
import { filterReps } from './repFilter'
import { DEFAULT_MIN_SCORE } from './facing'
import { PoseSequence2D } from './parsePoseV2'

export interface StrokeCountConfig {
  handedness: Handedness
  /** Manual camera-yaw override. The estimator is NOT ported (saturates on
   *  non-protocol footage, L-25); Videos/ clips analyze with yaw 0. */
  cameraYawDeg: number
  detector?: StrokeDetectorOptions
}

export interface StrokeCountResult {
  /** Every wrist-speed peak (includes recovery swings + junk). */
  rawStrokes: Stroke2D[]
  /** After ForwardStrokeFilter (recovery swings dropped). */
  forwardStrokes: Stroke2D[]
  /** After RepFilter (junk movement dropped) — the countable reps. */
  reps: Stroke2D[]
  xScale: number
}

/**
 * The M0 pipeline. Order is mandatory (CLAUDE.md gotcha):
 * detect → ForwardStrokeFilter → RepFilter.
 */
export function countStrokes(seq: PoseSequence2D, config: StrokeCountConfig): StrokeCountResult {
  const xScale = xScaleFor(seq.aspectRatio, config.cameraYawDeg)
  const minScore = config.detector?.minScore ?? DEFAULT_MIN_SCORE
  const rawStrokes = detectStrokes(seq.frames, config.handedness, xScale, seq.intervalMs, config.detector)
  const forwardStrokes = filterForwardStrokes(rawStrokes, seq.frames, config.handedness, minScore)
  const reps = filterReps(forwardStrokes)
  return { rawStrokes, forwardStrokes, reps, xScale }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/golden.test.ts`
Expected: PASS, with console lines like `andrii_1: raw=23 forward=15 reps=15`.

**If the golden fails:** the TS port has a bug. Get the Kotlin per-stroke truth by running `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ForehandDriveEndToEndTest"` from the repo root (its println shows rep counts) and, if needed, add a temporary println of per-stroke frames in a scratch Kotlin test. Diff stage by stage: raw count first (detector bug), then forward (filter bug). Common porting bugs: `framesFor` using round instead of floor; `>=`/`>` asymmetry in `findPeaks` plateau handling; boundary walk using `>=` instead of `>`; forgetting the score gate zeroes speed rather than skipping the frame. Never change thresholds or loosen the assertion.

- [ ] **Step 5: Tighten the forward-stroke golden**

The console line from Step 4 shows the observed `forward` count (expected 15 — Kotlin keeps all 15 forward strokes through RepFilter on this fixture). Replace the `toBeLessThan` line in the andrii_1 test with the exact observed value:

```ts
    expect(r.forwardStrokes).toHaveLength(15) // observed; matches Kotlin (RepFilter drops none on this fixture)
```

(If the observed value is not 15, STOP — reps=15 with forward≠15 means RepFilter dropped strokes where Kotlin didn't; debug per Step 4 before pinning.)

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/golden.test.ts`
Expected: PASS.

- [ ] **Step 6: Run the full drill2d suite**

Run: `cd poses_viewer && npx vitest run src/drill2d`
Expected: all 5 test files PASS.

- [ ] **Step 7: Commit**

```bash
git add poses_viewer/src/drill2d/parsePoseV2.ts poses_viewer/src/drill2d/countStrokes.ts poses_viewer/src/drill2d/__tests__/golden.test.ts
git commit -m "feat(poses_viewer): drill2d pipeline orchestrator + golden parity test (23 raw / 15 reps on andrii_1)"
```

---

### Task 6: Route plumbing + StrokesPage

**Files:**
- Modify: `poses_viewer/src/hooks/useHashRoute.ts`
- Modify: `poses_viewer/src/App.tsx` (~line 1025, route early-returns)
- Create: `poses_viewer/src/components/StrokesPage.tsx`

UI strings are **Ukrainian** (viewer convention, see poses_viewer/CLAUDE.md). No automated UI tests in this repo's viewer — verification is typecheck + manual smoke (Step 4).

- [ ] **Step 1: Add the route**

In `poses_viewer/src/hooks/useHashRoute.ts`, change:

```ts
export type Route = 'main' | 'mannequin' | 'drill2' | 'dataset' | 'strokes'

export const ROUTES: readonly Route[] = ['main', 'mannequin', 'drill2', 'dataset', 'strokes']
```

and add to `ROUTE_TITLES`:

```ts
  'strokes': 'Підрахунок ударів — Poses Viewer',
```

- [ ] **Step 2: Create StrokesPage**

```tsx
// poses_viewer/src/components/StrokesPage.tsx
import { useEffect, useMemo, useRef, useState } from 'react'
import { Handedness } from '../drill2d/types'
import { parsePoseV2, PoseSequence2D } from '../drill2d/parsePoseV2'
import { countStrokes } from '../drill2d/countStrokes'
import { DETECTOR_DEFAULTS } from '../drill2d/strokeDetector2d'
import { StrokeTimeline, TimelineEntry } from './StrokeTimeline'

interface VideoItem { name: string; ext: string }

export default function StrokesPage() {
  const [videos, setVideos] = useState<VideoItem[]>([])
  const [base, setBase] = useState('')
  const [seq, setSeq] = useState<PoseSequence2D | null>(null)
  const [error, setError] = useState('')
  const [handedness, setHandedness] = useState<Handedness>('right')
  const [yawDeg, setYawDeg] = useState(0)
  const [minPeakSpeed, setMinPeakSpeed] = useState(DETECTOR_DEFAULTS.minPeakSpeed)
  const [minPeakGapMs, setMinPeakGapMs] = useState(DETECTOR_DEFAULTS.minPeakGapMs)
  const [currentMs, setCurrentMs] = useState(0)
  const videoRef = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    fetch('/api/videos').then(r => (r.ok ? r.json() : [])).then(setVideos).catch(() => setVideos([]))
  }, [])

  useEffect(() => {
    if (!base) { setSeq(null); return }
    setError('')
    setSeq(null)
    fetch(`/videos/${base}/${base}_poses_rtm.json`)
      .then(r => {
        if (!r.ok) throw new Error(`немає ${base}_poses_rtm.json — спершу запусти export_poses_rtmpose.py`)
        return r.json()
      })
      .then(json => setSeq(parsePoseV2(json)))
      .catch(e => setError(String(e.message ?? e)))
  }, [base])

  const result = useMemo(() => {
    if (!seq) return null
    try {
      return countStrokes(seq, {
        handedness,
        cameraYawDeg: yawDeg,
        detector: { minPeakSpeed, minPeakGapMs },
      })
    } catch {
      return null // xScaleFor throws beyond ±60°; inputs are clamped, but stay safe
    }
  }, [seq, handedness, yawDeg, minPeakSpeed, minPeakGapMs])

  const entries = useMemo<TimelineEntry[]>(() => {
    if (!seq || !result) return []
    const repPeaks = new Set(result.reps.map(s => s.peakFrame))
    const fwdPeaks = new Set(result.forwardStrokes.map(s => s.peakFrame))
    const ms = (frame: number) => seq.frames[frame]?.timestampMs ?? frame * seq.intervalMs
    return result.rawStrokes.map((s, i) => ({
      kind: repPeaks.has(s.peakFrame) ? 'rep' : fwdPeaks.has(s.peakFrame) ? 'forward-dropped' : 'raw-dropped',
      startMs: ms(s.startFrame),
      peakMs: ms(s.peakFrame),
      endMs: ms(s.endFrame),
      label: `#${i + 1} · ${s.peakSpeed.toFixed(1)} торс/с`,
    }))
  }, [seq, result])

  const videoEntry = videos.find(v => v.name === base)
  const videoUrl = videoEntry?.ext ? `/videos/${base}/${base}${videoEntry.ext}` : null

  const seek = (ms: number) => {
    const v = videoRef.current
    if (v) v.currentTime = ms / 1000
  }

  return (
    <div className="min-h-screen bg-neutral-900 text-neutral-100 p-4 space-y-4">
      <header className="flex items-center gap-4 flex-wrap">
        <a href="#/main" className="text-sky-400 hover:underline">← Viewer</a>
        <h1 className="text-lg font-semibold">Підрахунок ударів (M0)</h1>
        <label className="flex items-center gap-2">
          <span>Відео:</span>
          <select
            className="bg-neutral-800 rounded px-2 py-1"
            value={base}
            onChange={e => setBase(e.target.value)}
          >
            <option value="">— вибери —</option>
            {videos.map(v => <option key={v.name} value={v.name}>{v.name}</option>)}
          </select>
        </label>
      </header>

      {error && <div className="text-red-400">{error}</div>}

      {videoUrl && (
        <div className="space-y-2 max-w-4xl">
          <video
            ref={videoRef}
            src={videoUrl}
            controls
            className="max-h-[55vh] bg-black"
            onTimeUpdate={e => setCurrentMs(e.currentTarget.currentTime * 1000)}
          />
          {seq && result && (
            <StrokeTimeline
              entries={entries}
              durationMs={seq.videoDurationMs}
              currentMs={currentMs}
              onSeek={seek}
            />
          )}
        </div>
      )}

      {result && (
        <div className="flex gap-6 text-sm">
          <span>Сирі піки: <b>{result.rawStrokes.length}</b></span>
          <span className="text-amber-400">Форвардні: <b>{result.forwardStrokes.length}</b></span>
          <span className="text-emerald-400">Повтори: <b>{result.reps.length}</b></span>
        </div>
      )}

      <fieldset className="border border-neutral-700 rounded p-3 max-w-xl space-y-2 text-sm">
        <legend className="px-1">Налаштування</legend>
        <label className="flex items-center gap-2">
          <span className="w-56">Рука:</span>
          <select
            className="bg-neutral-800 rounded px-2 py-1"
            value={handedness}
            onChange={e => setHandedness(e.target.value as Handedness)}
          >
            <option value="right">Права</option>
            <option value="left">Ліва</option>
          </select>
        </label>
        <label className="flex items-center gap-2">
          <span className="w-56">Кут камери (°, вручну — L-25):</span>
          <input
            type="number" min={-60} max={60} step={1}
            className="bg-neutral-800 rounded px-2 py-1 w-20"
            value={yawDeg}
            onChange={e => setYawDeg(Number(e.target.value))}
          />
        </label>
        <label className="flex items-center gap-2">
          <span className="w-56">Поріг швидкості піка (торс/с):</span>
          <input
            type="number" min={0.2} max={5} step={0.1}
            className="bg-neutral-800 rounded px-2 py-1 w-20"
            value={minPeakSpeed}
            onChange={e => setMinPeakSpeed(Number(e.target.value))}
          />
        </label>
        <label className="flex items-center gap-2">
          <span className="w-56">Мін. інтервал між піками (мс):</span>
          <input
            type="number" min={100} max={2000} step={50}
            className="bg-neutral-800 rounded px-2 py-1 w-20"
            value={minPeakGapMs}
            onChange={e => setMinPeakGapMs(Number(e.target.value))}
          />
        </label>
        <p className="text-neutral-400">
          Зелені смуги — повтори; жовті — форвардні, відкинуті RepFilter; сірі — піки,
          відкинуті ForwardStrokeFilter (замахи назад/шум). Клік по смузі — перехід до піка.
        </p>
      </fieldset>
    </div>
  )
}
```

- [ ] **Step 3: Wire the early return in App.tsx**

Add the import near the other component imports at the top of `poses_viewer/src/App.tsx`:

```tsx
import StrokesPage from './components/StrokesPage'
```

Add the early return next to the existing ones (around line 1025, before `if (route === 'dataset')`):

```tsx
  if (route === 'strokes') {
    return <StrokesPage />
  }
```

Note: `StrokeTimeline` doesn't exist yet — to keep this task compiling, create the placeholder in Step 3b, fully implemented in Task 7:

```tsx
// poses_viewer/src/components/StrokeTimeline.tsx
export interface TimelineEntry {
  kind: 'rep' | 'forward-dropped' | 'raw-dropped'
  startMs: number
  peakMs: number
  endMs: number
  label: string
}

interface Props {
  entries: TimelineEntry[]
  durationMs: number
  currentMs: number
  onSeek: (ms: number) => void
}

export function StrokeTimeline({ entries, durationMs }: Props) {
  return (
    <div className="h-10 bg-neutral-800 rounded text-xs text-neutral-400 flex items-center px-2">
      {entries.length} ударів · {Math.round(durationMs / 1000)} с (таймлайн — Task 7)
    </div>
  )
}
```

- [ ] **Step 4: Typecheck and smoke-test**

Run: `cd poses_viewer && npx tsc -b --noEmit`
Expected: clean.

Run: `cd poses_viewer && npm run dev` then open `http://localhost:5780/#/strokes`.
Expected: video dropdown populated; picking `andrii_1` shows the video, counts `Сирі піки: 23 · Форвардні: 15 · Повтори: 15`, and the placeholder timeline bar. Changing «Поріг швидкості» instantly changes the counts. Stop the dev server after checking.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/hooks/useHashRoute.ts poses_viewer/src/App.tsx poses_viewer/src/components/StrokesPage.tsx poses_viewer/src/components/StrokeTimeline.tsx
git commit -m "feat(poses_viewer): #/strokes route — stroke-counting debug page with live config"
```

---

### Task 7: StrokeTimeline bands + click-to-seek

**Files:**
- Modify: `poses_viewer/src/components/StrokeTimeline.tsx` (replace placeholder body)

- [ ] **Step 1: Implement the timeline**

Replace the entire placeholder `StrokeTimeline.tsx` with:

```tsx
// poses_viewer/src/components/StrokeTimeline.tsx
export interface TimelineEntry {
  kind: 'rep' | 'forward-dropped' | 'raw-dropped'
  startMs: number
  peakMs: number
  endMs: number
  label: string
}

interface Props {
  entries: TimelineEntry[]
  durationMs: number
  currentMs: number
  onSeek: (ms: number) => void
}

const BAND_CLASS: Record<TimelineEntry['kind'], string> = {
  'rep': 'bg-emerald-500/80 hover:bg-emerald-400',
  'forward-dropped': 'bg-amber-500/70 hover:bg-amber-400',
  'raw-dropped': 'bg-neutral-500/50 hover:bg-neutral-400',
}

export function StrokeTimeline({ entries, durationMs, currentMs, onSeek }: Props) {
  if (durationMs <= 0) return null
  const pct = (ms: number) => `${(Math.min(ms, durationMs) / durationMs) * 100}%`

  return (
    <div
      className="relative h-12 bg-neutral-800 rounded overflow-hidden cursor-pointer select-none"
      onClick={e => {
        const rect = e.currentTarget.getBoundingClientRect()
        onSeek(((e.clientX - rect.left) / rect.width) * durationMs)
      }}
    >
      {entries.map((en, i) => (
        <div
          key={i}
          title={en.label}
          className={`absolute top-1 bottom-1 rounded-sm ${BAND_CLASS[en.kind]}`}
          style={{ left: pct(en.startMs), width: `max(calc(${pct(en.endMs)} - ${pct(en.startMs)}), 3px)` }}
          onClick={ev => { ev.stopPropagation(); onSeek(en.peakMs) }}
        >
          {/* peak tick */}
          <div
            className="absolute top-0 bottom-0 w-px bg-white/90"
            style={{ left: `${((en.peakMs - en.startMs) / Math.max(en.endMs - en.startMs, 1)) * 100}%` }}
          />
        </div>
      ))}
      {/* playhead */}
      <div className="absolute top-0 bottom-0 w-0.5 bg-red-500 pointer-events-none" style={{ left: pct(currentMs) }} />
    </div>
  )
}
```

- [ ] **Step 2: Typecheck**

Run: `cd poses_viewer && npx tsc -b --noEmit`
Expected: clean.

- [ ] **Step 3: Manual verification**

Run: `cd poses_viewer && npm run dev`, open `http://localhost:5780/#/strokes`, pick `andrii_1`:
- 23 bands visible; 15 emerald, the rest gray/amber.
- Clicking an emerald band seeks the video to that stroke's peak; the red playhead tracks playback.
- Hover shows `#N · X.X торс/с`.
Stop the dev server.

- [ ] **Step 4: Commit**

```bash
git add poses_viewer/src/components/StrokeTimeline.tsx
git commit -m "feat(poses_viewer): stroke timeline — color-coded bands, peak ticks, click-to-seek"
```

---

### Task 8: Docs + final verification

**Files:**
- Modify: `poses_viewer/CLAUDE.md`
- Modify: `docs/superpowers/specs/2026-06-11-drill-effectiveness-sim-design.md` (status line only)

- [ ] **Step 1: Update poses_viewer/CLAUDE.md**

In the intro bullet list, extend the routes sentence to include `#/strokes`:

```
Hash routing via `src/hooks/useHashRoute.ts`: `#/main` (default), `#/mannequin`, `#/drill2`, `#/dataset`, `#/strokes`.
```

Add a file-map section after the `src/App.tsx` section:

```markdown
### `src/drill2d/` + `src/components/StrokesPage.tsx` / `StrokeTimeline.tsx`

M0 stroke-counting debug harness (spec: docs/superpowers/specs/2026-06-11-drill-effectiveness-sim-design.md).
`drill2d/` is a 1:1 TS mirror of the Kotlin shared/ detection chain — `strokeDetector2d.ts`,
`forwardStrokeFilter.ts`, `repFilter.ts`, plus `geometry.ts` (xScale), `facing.ts`, `parsePoseV2.ts`,
`countStrokes.ts` (pipeline order detect → forward → rep is MANDATORY). NOT related to `src/drill/`
(3D mannequin FK). **Binding fix-flow rule: Kotlin is source of truth — any behavioral fix lands in
shared/ Kotlin first, then is mirrored here; goldens updated in both suites in the same change.**
Golden parity test (`drill2d/__tests__/golden.test.ts`): 23 raw / 15 forward / 15 reps on
andrii_1_rtm — reads fixtures from `shared/src/commonTest/resources/fixtures/` via repo-relative path.
`#/strokes` UI: video + color-coded stroke bands (emerald reps / amber RepFilter-dropped / gray
recovery), click-to-seek, knobs for handedness / manual camera yaw (estimator not ported, L-25) /
minPeakSpeed / minPeakGapMs.
```

- [ ] **Step 2: Mark M0 implemented in the spec**

In `docs/superpowers/specs/2026-06-11-drill-effectiveness-sim-design.md`, change the Status line to:

```
Status: Approved (brainstorming) → rescoped 2026-06-11: M0 (stroke counting) IMPLEMENTED (plan: docs/superpowers/plans/2026-06-11-m0-stroke-counting-viewer.md); M1+ analysis milestones deferred — see Milestones
```

- [ ] **Step 3: Full verification**

```bash
cd poses_viewer && npx vitest run && npx tsc -b --noEmit
```
Expected: all viewer suites PASS (drill2d new tests + pre-existing drill/utils tests), typecheck clean.

```bash
cd .. && ./gradlew :shared:jvmTest
```
Expected: PASS — proves the Kotlin side was untouched (M0 non-goal: no changes to shared/).

- [ ] **Step 4: Commit**

```bash
git add poses_viewer/CLAUDE.md docs/superpowers/specs/2026-06-11-drill-effectiveness-sim-design.md
git commit -m "docs(poses_viewer): document drill2d mirror, #/strokes route, golden fix-flow rule"
```
