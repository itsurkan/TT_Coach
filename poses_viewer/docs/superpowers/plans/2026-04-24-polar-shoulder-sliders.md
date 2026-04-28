# Polar Shoulder Sliders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a second pair of sliders per shoulder — **Elevation** and **Plane** — that expose the same two DOFs (`*ShoulderAngleDeg` + `*ShoulderAbductionDeg`) under a polar parameterization. Both pairs stay visible in the editor and stay in sync; dragging one pair updates the other. `PoseAnchor` fields are not touched — polar sliders are a *computed view* over existing anchor state.

**Architecture:** One new pure-math module (`polarShoulder.ts`) implements the bidirectional polar ↔ rectangular conversion derived from the plane-projection FK. `AnchorParamSpec` becomes a discriminated union with `kind: 'direct'` (existing keys) and `kind: 'computed'` (read/write closures over the anchor). `AnchorSliders.tsx` handles both shapes behind three small helpers (`specRead`, `specWrite`, `specIdentity`). Four new `'computed'` entries are added to `ANCHOR_PARAM_GROUPS` (Elevation + Plane for each arm) — no other consumer of `PoseAnchor` sees any change.

**Tech Stack:** TypeScript + Vitest + React 18. Zero new deps. All new math is pure (no React, no anchor dependencies), trivially unit-testable.

---

## File Structure

**New files:**
- `poses_viewer/src/drill/polarShoulder.ts` — pure bidirectional conversion between `{ flex, abd }` and `{ elevation, plane }`. Side-agnostic (no `abSign`).
- `poses_viewer/src/drill/__tests__/polarShoulder.test.ts` — round-trip grid, pole degeneracy, pure-axis cases, side-agnosticism sanity check via `reconstructFromAnchor`.

**Modified files:**
- `poses_viewer/src/drill/PoseAnchor.ts` — convert `AnchorParamSpec` to a discriminated union (`kind: 'direct' | 'computed'`); add `kind: 'direct'` to every existing entry; add 4 new `'computed'` entries (Elevation + Plane × 2 arms).
- `poses_viewer/src/components/AnchorSliders.tsx` — handle both spec kinds uniformly via `specRead` / `specWrite` / `specIdentity` helpers; joint-highlight matches on `keys[]` for computed specs.

**Not touched** (confirmed in spec §Why computed-view): `skeletonReconstructor.ts`, `anchorExtractor.ts`, `anchorInterpolator.ts`, `neutralPose.ts`, `jointMap.ts`, any fixture, any Android Room schema.

**Grep target to audit:** `spec.key` — after Task 5 this should only appear inside the `kind === 'direct'` branches of `AnchorSliders.tsx` and in the `PoseAnchor.ts` type definition itself.

---

## Decisions Locked In

(Answers the user gave up-front or fixed by the spec — do not revisit.)

1. **Polar↔rect is side-agnostic.** `polarToFlexAbd` / `flexAbdToPolar` take no side parameter. The FK's existing `abSign` flip in `skeletonReconstructor.ts` handles world-space left/right mirroring. Both arms use "positive `plane` = away from midline" user convention, which already matches the existing `shoulderAbductionDeg` semantics.
2. **Clamping happens on write, not in conversion.** `polarToFlexAbd` returns raw math results (can go outside rect slider ranges e.g. `flex = −90°` at `elevation=90, plane=180`). `AnchorSliders.tsx` clamps to each rect slider's declared `min/max` before writing to the anchor. After clamp, the polar sliders re-derive their displayed values from the (now-clamped) anchor.
3. **No UI disable at poles.** When `elevation ∈ {0°, 180°}`, `plane` is mathematically indeterminate; the formula returns 0 and the slider stays enabled. No fade/disable affordance.
4. **Labels are English.** "Shoulder elevation" (0..180, step 1) and "Shoulder plane" (−90..180, step 1), matching the existing English shoulder labels.
5. **Defaults derived from NEUTRAL_POSE.** The computed specs have no independent `defaultValue` — the polar slider's reset target is whatever `flexAbdToPolar(NEUTRAL_POSE's flex, abd)` yields. No standalone default.
6. **`'computed'` spec `keys` field is for highlight-matching only.** Not a write-restriction or read-derivation contract. The authoritative read/write logic lives in the closures.
7. **Range for `plane`: −90..180.** Matches spec §UI layout. Covers cross-body reach (−90°) through lateral (90°) through cross-table back-reach (180°).
8. **Shoulder Elevation clamp is 0..180, not wider.** Polar elevation `acos(dDown)` is mathematically bounded to `[0°, 180°]`, so the slider range can't exceed this. No need to widen beyond the formula's natural range.
9. **Task structure:** one commit per task, each task containing TDD pair (failing test → implementation → passing test) where applicable. Pure-math tasks use strict TDD; the UI plumbing task uses integration-style tests against a mocked `onChange`.

---

## Glossary

- **flex** — shorthand for `rightShoulderAngleDeg` / `leftShoulderAngleDeg` (sagittal-plane shoulder flexion, degrees).
- **abd** — shorthand for `rightShoulderAbductionDeg` / `leftShoulderAbductionDeg` (frontal-plane abduction, degrees). Anatomical — positive means "away from midline" regardless of side.
- **elevation** — how high the arm is lifted. 0° = arm straight down along `torsoDown`; 180° = arm straight up.
- **plane** — compass bearing within the anatomical frame at that elevation. 0° = pure forward (sagittal); 90° = pure lateral away from midline (frontal); −90° = cross-body medial; 180° = behind the body.
- **Pole** — `elevation ∈ {0°, 180°}`. At these values `plane` is indeterminate (arm is along the vertical axis of the polar coordinate system).
- **Computed spec** — an `AnchorParamSpec` with `kind: 'computed'` that reads/writes multiple underlying anchor keys via closures instead of a single `key`.

---

## Conversion formulas (reference — used verbatim in code)

From the shipped plane-projection FK (commit `762d163`):

```
upperArmDir = cos(flex)·cos(abd)·torsoDown
            + sin(flex)·cos(abd)·shoulderForward
            + sin(abd)·shoulderAcross
```

**Polar → rectangular** (user drags polar slider):

```
flex = atan2( cos(plane) · sin(elevation),  cos(elevation) )
abd  = asin(  sin(plane) · sin(elevation) )
```

**Rectangular → polar** (display polar given the anchor):

```
dDown    = cos(flex) · cos(abd)
dForward = sin(flex) · cos(abd)
dAcross  = sin(abd)
elevation = acos( clamp(dDown, -1, 1) )
plane     = atan2( dAcross, dForward )   // returns 0 at poles; stable
```

---

## Task 0: Scaffold the pure-math module (signatures + failing compile)

**Purpose:** lock in the module boundary and types before any logic lands, so later tasks write against a stable interface. No implementation yet — functions throw.

**Files:**
- Create: `poses_viewer/src/drill/polarShoulder.ts`

- [ ] **Step 1: Create the file with signatures only**

```ts
// poses_viewer/src/drill/polarShoulder.ts
//
// Bidirectional conversion between the rectangular shoulder DOF pair
// (flex = *ShoulderAngleDeg, abd = *ShoulderAbductionDeg) and the polar
// pair (elevation + plane). Derived directly from the plane-projection
// FK in skeletonReconstructor.ts. Side-agnostic: no abSign — world-space
// mirroring is the FK's job.

export interface FlexAbd {
  flex: number // degrees
  abd: number  // degrees
}

export interface Polar {
  elevation: number // degrees, 0..180
  plane: number     // degrees, -180..180
}

export function polarToFlexAbd(p: Polar): FlexAbd {
  throw new Error('not implemented')
}

export function flexAbdToPolar(r: FlexAbd): Polar {
  throw new Error('not implemented')
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd poses_viewer && npx tsc -b --noEmit`
Expected: PASS (type-checks clean; no other file imports these yet).

- [ ] **Step 3: Commit**

```bash
git add poses_viewer/src/drill/polarShoulder.ts
git commit -m "poses_viewer: scaffold polarShoulder module (signatures only)"
```

---

## Task 1: `polarToFlexAbd` — failing test + implementation

**Purpose:** implement the polar → rectangular conversion and pin its correctness on the four pure-axis cases plus one diagonal.

**Files:**
- Create: `poses_viewer/src/drill/__tests__/polarShoulder.test.ts`
- Modify: `poses_viewer/src/drill/polarShoulder.ts`

- [ ] **Step 1: Write failing tests**

```ts
// poses_viewer/src/drill/__tests__/polarShoulder.test.ts
import { describe, it, expect } from 'vitest'
import { polarToFlexAbd, flexAbdToPolar } from '../polarShoulder'

describe('polarToFlexAbd', () => {
  it('elevation=0 → arm straight down (flex=0, abd=0), plane irrelevant', () => {
    const out = polarToFlexAbd({ elevation: 0, plane: 0 })
    expect(out.flex).toBeCloseTo(0, 6)
    expect(out.abd).toBeCloseTo(0, 6)
    // plane is arbitrary at the pole — any value must still give flex=0, abd=0
    const out2 = polarToFlexAbd({ elevation: 0, plane: 137 })
    expect(out2.flex).toBeCloseTo(0, 6)
    expect(out2.abd).toBeCloseTo(0, 6)
  })
  it('elevation=180 → arm straight up (flex=180, abd=0)', () => {
    const out = polarToFlexAbd({ elevation: 180, plane: 0 })
    expect(out.flex).toBeCloseTo(180, 6)
    expect(out.abd).toBeCloseTo(0, 6)
  })
  it('elevation=90, plane=0 → pure sagittal forward (flex=90, abd=0)', () => {
    const out = polarToFlexAbd({ elevation: 90, plane: 0 })
    expect(out.flex).toBeCloseTo(90, 6)
    expect(out.abd).toBeCloseTo(0, 6)
  })
  it('elevation=90, plane=90 → pure lateral (flex=0, abd=90)', () => {
    const out = polarToFlexAbd({ elevation: 90, plane: 90 })
    expect(out.flex).toBeCloseTo(0, 6)
    expect(out.abd).toBeCloseTo(90, 6)
  })
  it('elevation=90, plane=-90 → cross-body (flex=0, abd=-90)', () => {
    const out = polarToFlexAbd({ elevation: 90, plane: -90 })
    expect(out.flex).toBeCloseTo(0, 6)
    expect(out.abd).toBeCloseTo(-90, 6)
  })
  it('elevation=90, plane=180 → backward reach (flex=-90, abd=0)', () => {
    // cos(plane)=cos(180)=-1, sin(elevation)=1, cos(elevation)=0
    // flex = atan2(-1, 0) = -90; sin(plane)=0 so abd=0
    const out = polarToFlexAbd({ elevation: 90, plane: 180 })
    expect(out.flex).toBeCloseTo(-90, 6)
    expect(out.abd).toBeCloseTo(0, 6)
  })
  it('elevation=45, plane=45 → symmetric diagonal', () => {
    // flex = atan2(cos45·sin45, cos45) = atan2(0.5, √2/2) ≈ 35.2644°
    // abd  = asin(sin45·sin45) = asin(0.5) = 30°
    const out = polarToFlexAbd({ elevation: 45, plane: 45 })
    expect(out.flex).toBeCloseTo(35.2644, 3)
    expect(out.abd).toBeCloseTo(30, 3)
  })
})
```

- [ ] **Step 2: Run — expect failure**

Run: `cd poses_viewer && npx vitest run src/drill/__tests__/polarShoulder.test.ts`
Expected: FAIL with `Error: not implemented` on every test.

- [ ] **Step 3: Implement `polarToFlexAbd`**

```ts
// poses_viewer/src/drill/polarShoulder.ts
const DEG = Math.PI / 180
const RAD = 180 / Math.PI

export interface FlexAbd {
  flex: number
  abd: number
}
export interface Polar {
  elevation: number
  plane: number
}

export function polarToFlexAbd(p: Polar): FlexAbd {
  const e = p.elevation * DEG
  const pl = p.plane * DEG
  const sinE = Math.sin(e)
  const cosE = Math.cos(e)
  const flexRad = Math.atan2(Math.cos(pl) * sinE, cosE)
  const abdRad = Math.asin(clamp(Math.sin(pl) * sinE, -1, 1))
  return { flex: flexRad * RAD, abd: abdRad * RAD }
}

export function flexAbdToPolar(r: FlexAbd): Polar {
  throw new Error('not implemented')
}

function clamp(v: number, lo: number, hi: number): number {
  return v < lo ? lo : v > hi ? hi : v
}
```

- [ ] **Step 4: Run — expect pass**

Run: `cd poses_viewer && npx vitest run src/drill/__tests__/polarShoulder.test.ts`
Expected: PASS (all 7 tests green).

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill/polarShoulder.ts poses_viewer/src/drill/__tests__/polarShoulder.test.ts
git commit -m "poses_viewer: implement polarToFlexAbd (polar→rect shoulder conversion)"
```

---

## Task 2: `flexAbdToPolar` — failing test + implementation

**Purpose:** implement the inverse (rect → polar) and pin its correctness on pure-axis cases and at poles.

**Files:**
- Modify: `poses_viewer/src/drill/__tests__/polarShoulder.test.ts`
- Modify: `poses_viewer/src/drill/polarShoulder.ts`

- [ ] **Step 1: Append failing tests**

```ts
// append to poses_viewer/src/drill/__tests__/polarShoulder.test.ts
describe('flexAbdToPolar', () => {
  it('flex=0, abd=0 → elevation=0 (pole), plane=0', () => {
    const out = flexAbdToPolar({ flex: 0, abd: 0 })
    expect(out.elevation).toBeCloseTo(0, 6)
    expect(out.plane).toBeCloseTo(0, 6)
  })
  it('flex=180, abd=0 → elevation=180 (pole), plane=0', () => {
    const out = flexAbdToPolar({ flex: 180, abd: 0 })
    expect(out.elevation).toBeCloseTo(180, 6)
    expect(out.plane).toBeCloseTo(0, 6)
  })
  it('flex=90, abd=0 → elevation=90, plane=0 (sagittal forward)', () => {
    const out = flexAbdToPolar({ flex: 90, abd: 0 })
    expect(out.elevation).toBeCloseTo(90, 6)
    expect(out.plane).toBeCloseTo(0, 6)
  })
  it('flex=0, abd=90 → elevation=90, plane=90 (pure lateral)', () => {
    const out = flexAbdToPolar({ flex: 0, abd: 90 })
    expect(out.elevation).toBeCloseTo(90, 6)
    expect(out.plane).toBeCloseTo(90, 6)
  })
  it('flex=0, abd=-90 → elevation=90, plane=-90 (cross-body)', () => {
    const out = flexAbdToPolar({ flex: 0, abd: -90 })
    expect(out.elevation).toBeCloseTo(90, 6)
    expect(out.plane).toBeCloseTo(-90, 6)
  })
})
```

- [ ] **Step 2: Run — expect failure**

Run: `cd poses_viewer && npx vitest run src/drill/__tests__/polarShoulder.test.ts`
Expected: FAIL with `Error: not implemented` on the new 5 tests (old 7 still green).

- [ ] **Step 3: Implement `flexAbdToPolar`**

Replace the stub body in `polarShoulder.ts`:

```ts
export function flexAbdToPolar(r: FlexAbd): Polar {
  const f = r.flex * DEG
  const a = r.abd * DEG
  const dDown = Math.cos(f) * Math.cos(a)
  const dForward = Math.sin(f) * Math.cos(a)
  const dAcross = Math.sin(a)
  const elevation = Math.acos(clamp(dDown, -1, 1)) * RAD
  const plane = Math.atan2(dAcross, dForward) * RAD
  return { elevation, plane }
}
```

- [ ] **Step 4: Run — expect pass**

Run: `cd poses_viewer && npx vitest run src/drill/__tests__/polarShoulder.test.ts`
Expected: PASS (all 12 tests green).

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill/polarShoulder.ts poses_viewer/src/drill/__tests__/polarShoulder.test.ts
git commit -m "poses_viewer: implement flexAbdToPolar (rect→polar shoulder conversion)"
```

---

## Task 3: Round-trip property test (`polar → rect → polar`) on a grid

**Purpose:** verify the two conversions are exact inverses everywhere except the two degenerate poles. This is the invariant that keeps the four sliders in sync in the UI.

**Files:**
- Modify: `poses_viewer/src/drill/__tests__/polarShoulder.test.ts`

- [ ] **Step 1: Append a grid round-trip test**

```ts
// append to poses_viewer/src/drill/__tests__/polarShoulder.test.ts
describe('round-trip: polar → rect → polar', () => {
  it('identity on 7x7 grid excluding the two poles', () => {
    const elevations = [10, 30, 60, 90, 120, 150, 170]
    const planes = [-60, -30, 0, 45, 90, 135, 170]
    for (const elevation of elevations) {
      for (const plane of planes) {
        const rect = polarToFlexAbd({ elevation, plane })
        const back = flexAbdToPolar(rect)
        expect(back.elevation).toBeCloseTo(elevation, 4)
        expect(back.plane).toBeCloseTo(plane, 4)
      }
    }
  })
  it('rect → polar → rect identity on 5x5 grid within the polar-reachable region', () => {
    // abd is bounded to [-90, 90] because asin can't exceed 90°, so restrict
    // the grid accordingly. flex can span the full rect-slider range.
    const flexes = [-30, 0, 45, 90, 135, 180]
    const abds = [-40, -20, 0, 30, 60, 90]
    for (const flex of flexes) {
      for (const abd of abds) {
        const polar = flexAbdToPolar({ flex, abd })
        const back = polarToFlexAbd(polar)
        expect(back.flex).toBeCloseTo(flex, 4)
        expect(back.abd).toBeCloseTo(abd, 4)
      }
    }
  })
})
```

- [ ] **Step 2: Run — expect pass**

Run: `cd poses_viewer && npx vitest run src/drill/__tests__/polarShoulder.test.ts`
Expected: PASS (14 tests; grid iterates 49 + 36 = 85 assertions each direction).

- [ ] **Step 3: Commit**

```bash
git add poses_viewer/src/drill/__tests__/polarShoulder.test.ts
git commit -m "poses_viewer: round-trip grid test for polar ↔ rect shoulder conversion"
```

---

## Task 4: Side-agnosticism sanity check via `reconstructFromAnchor`

**Purpose:** prove that feeding identical polar values into left and right shoulder fields produces visually mirrored arms — i.e. the polar math truly does not need to know which side it's for. This catches any future regression where someone tries to bake `abSign` into the conversion.

**Files:**
- Modify: `poses_viewer/src/drill/__tests__/polarShoulder.test.ts`

- [ ] **Step 1: Append the sanity test**

```ts
// append to poses_viewer/src/drill/__tests__/polarShoulder.test.ts
import { STANDING_POSE } from '../neutralPose'
import { reconstructFromAnchor } from '../skeletonReconstructor'
import { LM } from '../SkeletonModel'

describe('side-agnosticism: polar values mirror across midline when applied equally', () => {
  it('polar(elevation=60, plane=45) on both shoulders → elbows mirrored in x around hipMidX', () => {
    const rect = polarToFlexAbd({ elevation: 60, plane: 45 })
    // Apply the same polar (= same rect) to both sides. Figure faces camera
    // (figureYawDeg=0), torso upright. FK's internal abSign flip should do
    // the visual mirroring.
    const anchor = {
      ...STANDING_POSE,
      rightShoulderAngleDeg: rect.flex,
      rightShoulderAbductionDeg: rect.abd,
      leftShoulderAngleDeg: rect.flex,
      leftShoulderAbductionDeg: rect.abd,
    }
    const lms = reconstructFromAnchor(anchor)
    const rElbow = lms[LM.RIGHT_ELBOW]
    const lElbow = lms[LM.LEFT_ELBOW]
    const rShoulder = lms[LM.RIGHT_SHOULDER]
    const lShoulder = lms[LM.LEFT_SHOULDER]
    // Midline x is the midpoint of the two shoulders (figure faces camera,
    // hips are symmetric, so shoulder midline ≈ hip midline in x).
    const midX = (rShoulder.x + lShoulder.x) / 2
    // Distance from the midline should match on both sides (mirrored).
    expect(Math.abs(rElbow.x - midX)).toBeCloseTo(Math.abs(lElbow.x - midX), 4)
    // And the y/z components should be identical (symmetric plane).
    expect(rElbow.y).toBeCloseTo(lElbow.y, 4)
    expect(rElbow.z).toBeCloseTo(lElbow.z, 4)
  })
})
```

- [ ] **Step 2: Run — expect pass**

Run: `cd poses_viewer && npx vitest run src/drill/__tests__/polarShoulder.test.ts`
Expected: PASS.

If this test fails, the `abSign` flip inside the FK is not compensating the way the spec predicts. Do NOT add a side parameter to the polar conversion to fix it — stop and re-read the spec's §Parameterization paragraph; the bug is elsewhere.

- [ ] **Step 3: Commit**

```bash
git add poses_viewer/src/drill/__tests__/polarShoulder.test.ts
git commit -m "poses_viewer: sanity-test side-agnostic polar shoulders via FK round-trip"
```

---

## Task 5: Convert `AnchorParamSpec` to a discriminated union

**Purpose:** extend the spec type to support computed params while keeping all existing direct-key specs working unchanged. This task ships with zero new UI behaviour — just the type migration and every existing spec entry tagged `kind: 'direct'`.

**Files:**
- Modify: `poses_viewer/src/drill/PoseAnchor.ts`
- Modify: `poses_viewer/src/components/AnchorSliders.tsx`

- [ ] **Step 1: Rewrite the `AnchorParamSpec` type in `PoseAnchor.ts`**

Replace lines 110–122 (the current `AnchorParamSpec` interface):

```ts
/** Common fields shared by all slider spec kinds. */
interface AnchorParamSpecBase {
  label: string
  min: number
  max: number
  step: number
  /** Reset/MIDPOINT_POSE target. When omitted, MIDPOINT_POSE uses (min+max)/2.
   *  Use this to decouple "slider reach" from "default pose" — e.g. shoulder
   *  abduction goes up to 120° but the ready-position default sits at 31°.
   *  Value is snapped to `step` before use.
   *  Not applicable to computed specs (polar sliders re-derive from the
   *  underlying anchor on every render). */
  defaultValue?: number
}

/** A slider that reads and writes a single `PoseAnchor` field directly. */
export interface DirectAnchorParamSpec extends AnchorParamSpecBase {
  kind: 'direct'
  key: keyof PoseAnchor
}

/** A slider that exposes a *computed view* over one or more anchor fields.
 *  Used for the polar shoulder sliders (elevation + plane) which are a
 *  reparameterization of `*ShoulderAngleDeg` + `*ShoulderAbductionDeg`. */
export interface ComputedAnchorParamSpec extends AnchorParamSpecBase {
  kind: 'computed'
  /** Stable id used for row refs, clipboard copy, and highlight matching.
   *  Must be unique across all specs (direct or computed). */
  id: string
  /** Underlying anchor keys this view touches. Joint-highlighting marks
   *  this spec as highlighted when any of these keys is in
   *  `highlightedParams`. Does not restrict reads/writes — the closures
   *  below are authoritative for that. */
  keys: readonly (keyof PoseAnchor)[]
  read: (anchor: PoseAnchor) => number
  write: (anchor: PoseAnchor, value: number) => PoseAnchor
}

export type AnchorParamSpec = DirectAnchorParamSpec | ComputedAnchorParamSpec
```

- [ ] **Step 2: Add `kind: 'direct'` to every existing entry in `ANCHOR_PARAM_GROUPS`**

The existing block spans ~30 entries (lines 129–196). Mechanical edit: each `{ key: 'X', label: ... }` becomes `{ kind: 'direct', key: 'X', label: ... }`. No other field changes.

Example of the transformation for the first entry:

```ts
// before:
{ key: 'figureYawDeg',        label: 'Figure yaw (whole body)', min: -180, max: 180, step: 1 },
// after:
{ kind: 'direct', key: 'figureYawDeg',        label: 'Figure yaw (whole body)', min: -180, max: 180, step: 1 },
```

Apply the same transformation to **every** entry across all five groups (Torso, Right arm, Left arm, Legs, Position). Do not remove any entry.

- [ ] **Step 3: Update `AnchorSliders.tsx` to use the new type**

The file currently does `spec.key` in several places. Add three helper functions at the top of the component body (right after the `setKey` definition) and route every `spec.key` access through them:

```tsx
// Add inside AnchorSliders component, after setKey (which becomes unused and
// should be deleted once these helpers are wired in).
const specIdentity = (spec: AnchorParamSpec): string =>
  spec.kind === 'direct' ? (spec.key as string) : spec.id

const specRead = (spec: AnchorParamSpec, a: PoseAnchor): number =>
  spec.kind === 'direct' ? (a[spec.key] as number) : spec.read(a)

const specWrite = (spec: AnchorParamSpec, a: PoseAnchor, v: number): PoseAnchor =>
  spec.kind === 'direct' ? { ...a, [spec.key]: v } : spec.write(a, v)
```

Then replace every `spec.key` / `anchor[spec.key]` / `{ ...anchor, [spec.key]: v }` usage with the helper calls. Specifically:

1. Line 129 `const value = anchor[spec.key] as number` → `const value = specRead(spec, anchor)`.
2. Line 133 `const highlighted = isHighlighted(spec.key)` → `const highlighted = isHighlighted(spec)` (see below — `isHighlighted` needs a signature change).
3. Line 136 `key={spec.key}` → `key={specIdentity(spec)}`.
4. Line 137 `ref={el => { rowRefs.current[spec.key] = el }}` → `ref={el => { rowRefs.current[specIdentity(spec)] = el }}`.
5. Line 153 `onClick={() => copyRow(spec.key, value, spec.step, highlighted)}` → new signature that accepts the spec itself (see below).
6. Line 177 `onChange={e => setKey(spec.key, parseFloat(e.target.value))}` → `onChange={e => onChange(specWrite(spec, anchor, parseFloat(e.target.value)))}`.

`isHighlighted` needs to accept either kind:

```ts
const isHighlighted = (spec: AnchorParamSpec): boolean => {
  if (!highlightedParams) return false
  if (spec.kind === 'direct') return highlightedParams.includes(spec.key)
  return spec.keys.some(k => highlightedParams.includes(k))
}
```

`copyRow` needs to operate on the spec identity and label (not on the raw `key`):

```ts
const [copiedId, setCopiedId] = useState<string | null>(null)

const copyRow = (spec: AnchorParamSpec, value: number, isJointParam: boolean) => {
  const id = specIdentity(spec)
  const formatted = spec.step >= 1 ? value.toFixed(0) : value.toFixed(2)
  const prefix = isJointParam ? jointPrefix() : null
  const text = prefix ? `${prefix} · ${id}: ${formatted}` : `${id}: ${formatted}`
  void navigator.clipboard.writeText(text).then(() => {
    setCopiedId(id)
    setTimeout(() => setCopiedId(prev => (prev === id ? null : prev)), 900)
  })
}
```

Rename the existing `copiedKey` state variable to `copiedId` throughout the file; the title-string lambda at lines 154–159 uses `spec.key` / `copiedKey === spec.key` — update to use `specIdentity(spec)` / `copiedId === specIdentity(spec)`. The `setKey` helper (line 40) becomes unused and should be deleted.

Also update the `useLayoutEffect` scroll-into-view on lines 72–77:

```ts
useLayoutEffect(() => {
  if (!highlightedParams || highlightedParams.length === 0) return
  const first = highlightedParams[0]
  // Find the first spec (direct or computed) that claims this key.
  const allSpecs = ANCHOR_PARAM_GROUPS.flatMap(g => g.params)
  const match = allSpecs.find(s =>
    s.kind === 'direct' ? s.key === first : s.keys.includes(first),
  )
  if (!match) return
  const el = rowRefs.current[specIdentity(match)]
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}, [highlightedParams])
```

Also update the import so the `AnchorParamSpec` type can be used in helper signatures:

```tsx
import type { PoseAnchor, AnchorPhase, AnchorParamSpec } from '../drill/PoseAnchor'
```

- [ ] **Step 4: Verify compilation**

Run: `cd poses_viewer && npx tsc -b --noEmit`
Expected: PASS. TypeScript should complain if any `spec.key` call site was missed — fix any reported errors before moving on.

- [ ] **Step 5: Run the full test suite**

Run: `cd poses_viewer && npx vitest run`
Expected: PASS — no behavioural change, just type reshape.

- [ ] **Step 6: Manual smoke test**

Run `cd poses_viewer && npm run dev` and open http://localhost:5780 → click the Drill editor button → verify every existing slider still renders, drags, updates the mannequin, highlights on joint click, and that the clipboard copy button still produces `figureYawDeg: 0` style text. No visual or behavioural change expected.

- [ ] **Step 7: Commit**

```bash
git add poses_viewer/src/drill/PoseAnchor.ts poses_viewer/src/components/AnchorSliders.tsx
git commit -m "poses_viewer: discriminated-union AnchorParamSpec (direct | computed)"
```

---

## Task 6: Add the four polar shoulder specs to `ANCHOR_PARAM_GROUPS`

**Purpose:** wire the polar math into the editor. After this task, Elevation and Plane sliders appear under each arm group and stay in sync with the rectangular pair.

**Files:**
- Modify: `poses_viewer/src/drill/PoseAnchor.ts`
- Modify: `poses_viewer/src/components/AnchorSliders.tsx` (clamp on write)

- [ ] **Step 1: Add the polar import to `PoseAnchor.ts`**

At the top of the file:

```ts
import { polarToFlexAbd, flexAbdToPolar } from './polarShoulder'
```

- [ ] **Step 2: Insert the four computed specs into the arm groups**

Inside the `'Right arm (stroking)'` group, add two entries **immediately after** `rightShoulderAbductionDeg` (current line 146 / 147):

```ts
{
  kind: 'computed',
  id: 'rightShoulderElevationDeg',
  keys: ['rightShoulderAngleDeg', 'rightShoulderAbductionDeg'],
  label: 'Shoulder elevation',
  min: 0, max: 180, step: 1,
  read: a => flexAbdToPolar({
    flex: a.rightShoulderAngleDeg, abd: a.rightShoulderAbductionDeg,
  }).elevation,
  write: (a, v) => {
    const polar = flexAbdToPolar({
      flex: a.rightShoulderAngleDeg, abd: a.rightShoulderAbductionDeg,
    })
    const rect = polarToFlexAbd({ elevation: v, plane: polar.plane })
    return {
      ...a,
      rightShoulderAngleDeg: clamp(rect.flex, -30, 180),
      rightShoulderAbductionDeg: clamp(rect.abd, -40, 120),
    }
  },
},
{
  kind: 'computed',
  id: 'rightShoulderPlaneDeg',
  keys: ['rightShoulderAngleDeg', 'rightShoulderAbductionDeg'],
  label: 'Shoulder plane',
  min: -90, max: 180, step: 1,
  read: a => flexAbdToPolar({
    flex: a.rightShoulderAngleDeg, abd: a.rightShoulderAbductionDeg,
  }).plane,
  write: (a, v) => {
    const polar = flexAbdToPolar({
      flex: a.rightShoulderAngleDeg, abd: a.rightShoulderAbductionDeg,
    })
    const rect = polarToFlexAbd({ elevation: polar.elevation, plane: v })
    return {
      ...a,
      rightShoulderAngleDeg: clamp(rect.flex, -30, 180),
      rightShoulderAbductionDeg: clamp(rect.abd, -40, 120),
    }
  },
},
```

Do the same for `'Left arm'` with `leftShoulderAngleDeg` / `leftShoulderAbductionDeg`, using the left-arm rect clamp bounds (`leftShoulderAngleDeg` is `-30..180`, `leftShoulderAbductionDeg` is `0..120` — note the asymmetry: the left arm's rect `abd` does **not** extend below 0, so dragging the left-arm plane slider below 0° will clamp `abd` to 0 and the visible arm motion will differ from the right arm's cross-body reach):

```ts
{
  kind: 'computed',
  id: 'leftShoulderElevationDeg',
  keys: ['leftShoulderAngleDeg', 'leftShoulderAbductionDeg'],
  label: 'Shoulder elevation',
  min: 0, max: 180, step: 1,
  read: a => flexAbdToPolar({
    flex: a.leftShoulderAngleDeg, abd: a.leftShoulderAbductionDeg,
  }).elevation,
  write: (a, v) => {
    const polar = flexAbdToPolar({
      flex: a.leftShoulderAngleDeg, abd: a.leftShoulderAbductionDeg,
    })
    const rect = polarToFlexAbd({ elevation: v, plane: polar.plane })
    return {
      ...a,
      leftShoulderAngleDeg: clamp(rect.flex, -30, 180),
      leftShoulderAbductionDeg: clamp(rect.abd, 0, 120),
    }
  },
},
{
  kind: 'computed',
  id: 'leftShoulderPlaneDeg',
  keys: ['leftShoulderAngleDeg', 'leftShoulderAbductionDeg'],
  label: 'Shoulder plane',
  min: -90, max: 180, step: 1,
  read: a => flexAbdToPolar({
    flex: a.leftShoulderAngleDeg, abd: a.leftShoulderAbductionDeg,
  }).plane,
  write: (a, v) => {
    const polar = flexAbdToPolar({
      flex: a.leftShoulderAngleDeg, abd: a.leftShoulderAbductionDeg,
    })
    const rect = polarToFlexAbd({ elevation: polar.elevation, plane: v })
    return {
      ...a,
      leftShoulderAngleDeg: clamp(rect.flex, -30, 180),
      leftShoulderAbductionDeg: clamp(rect.abd, 0, 120),
    }
  },
},
```

Add the local `clamp` helper at the bottom of `PoseAnchor.ts` (above the flattened export):

```ts
function clamp(v: number, lo: number, hi: number): number {
  return v < lo ? lo : v > hi ? hi : v
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd poses_viewer && npx tsc -b --noEmit`
Expected: PASS.

- [ ] **Step 4: Run unit tests**

Run: `cd poses_viewer && npx vitest run`
Expected: PASS — 14 polar tests + every existing suite. If `skeletonReconstructor.test.ts` fingerprints break, stop — they shouldn't, since we haven't touched the FK or `NEUTRAL_POSE`/`STANDING_POSE`.

- [ ] **Step 5: Manual in-browser check**

Run `cd poses_viewer && npm run dev`, open the Drill editor, and verify:

- Each arm group now shows four shoulder rows: `Shoulder fwd`, `Shoulder side`, `Shoulder elevation`, `Shoulder plane` (in that order).
- Dragging `Shoulder fwd` → both `Shoulder elevation` and `Shoulder plane` numeric readouts update live.
- Dragging `Shoulder elevation` → both `Shoulder fwd` and `Shoulder side` readouts update live, and the mannequin arm lifts/lowers in the **current plane** (no lateral drift).
- Dragging `Shoulder plane` from 0° to 90° at a fixed non-zero elevation → arm sweeps from pure sagittal forward to pure lateral (frontal plane), staying at the same height.
- Setting `Shoulder side` to its max (120°) then dragging `Shoulder elevation` → `Shoulder side` **clamps to 90°** (the polar-reachable ceiling). This is the documented trade-off.
- Clicking the right shoulder joint on the 3D mannequin highlights all four rows (all share `keys: ['rightShoulderAngleDeg', 'rightShoulderAbductionDeg']`).

- [ ] **Step 6: Commit**

```bash
git add poses_viewer/src/drill/PoseAnchor.ts
git commit -m "poses_viewer: add polar Elevation/Plane shoulder sliders as computed view"
```

---

## Task 7: Verify joint-highlight covers the polar rows

**Purpose:** confirm the `isHighlighted` helper's `spec.keys.some(...)` branch (added in Task 5) wires through correctly for computed specs. This is a regression guard — if a future refactor accidentally drops the `kind === 'computed'` branch, this test will catch it.

**Files:**
- Modify: `poses_viewer/src/drill/__tests__/polarShoulder.test.ts`

- [ ] **Step 1: Append a test that verifies the spec array contents**

```ts
// append to poses_viewer/src/drill/__tests__/polarShoulder.test.ts
import { ANCHOR_PARAM_GROUPS } from '../PoseAnchor'

describe('ANCHOR_PARAM_GROUPS: polar shoulder entries', () => {
  const all = ANCHOR_PARAM_GROUPS.flatMap(g => g.params)
  const findById = (id: string) =>
    all.find(p => p.kind === 'computed' && p.id === id)

  it('adds four computed entries (2 per arm)', () => {
    const computed = all.filter(p => p.kind === 'computed')
    expect(computed).toHaveLength(4)
    expect(computed.map(p => (p as any).id).sort()).toEqual([
      'leftShoulderElevationDeg',
      'leftShoulderPlaneDeg',
      'rightShoulderElevationDeg',
      'rightShoulderPlaneDeg',
    ])
  })

  it('right-arm polar specs reference both right-shoulder anchor keys', () => {
    const e = findById('rightShoulderElevationDeg')
    const p = findById('rightShoulderPlaneDeg')
    expect(e).toBeDefined()
    expect(p).toBeDefined()
    expect((e as any).keys).toEqual(['rightShoulderAngleDeg', 'rightShoulderAbductionDeg'])
    expect((p as any).keys).toEqual(['rightShoulderAngleDeg', 'rightShoulderAbductionDeg'])
  })

  it('read/write round-trips through a sample anchor', () => {
    const e = findById('rightShoulderElevationDeg') as any
    const anchor = {
      rightShoulderAngleDeg: 90,
      rightShoulderAbductionDeg: 0,
    } as any
    // With flex=90, abd=0 the arm is horizontal forward → elevation=90.
    expect(e.read(anchor)).toBeCloseTo(90, 4)
    // Writing elevation=45 at plane=0 → flex=45, abd=0.
    const updated = e.write(anchor, 45)
    expect(updated.rightShoulderAngleDeg).toBeCloseTo(45, 4)
    expect(updated.rightShoulderAbductionDeg).toBeCloseTo(0, 4)
  })
})
```

- [ ] **Step 2: Run — expect pass**

Run: `cd poses_viewer && npx vitest run src/drill/__tests__/polarShoulder.test.ts`
Expected: PASS (17 tests total).

- [ ] **Step 3: Commit**

```bash
git add poses_viewer/src/drill/__tests__/polarShoulder.test.ts
git commit -m "poses_viewer: verify ANCHOR_PARAM_GROUPS contains polar shoulder specs"
```

---

## Task 8: Final end-to-end regression sweep

**Purpose:** catch anything missed by the unit/integration tests — visual drift in `DrillEditor`, clipboard text formatting, joint-row scroll-into-view, etc.

**Files:** none (manual + test suite).

- [ ] **Step 1: Run the full test suite**

Run: `cd poses_viewer && npx vitest run`
Expected: PASS — every existing suite plus the new `polarShoulder.test.ts`.

- [ ] **Step 2: Run the TypeScript build**

Run: `cd poses_viewer && npx tsc -b --noEmit`
Expected: PASS.

- [ ] **Step 3: Manual UI sweep in DrillEditor**

Run `cd poses_viewer && npm run dev`. In the editor, verify:

1. **Rect-only drag (regression).** Reset to neutral. Drag `Shoulder fwd` from 41° to 120°. The mannequin arm swings forward in one plane (no lateral drift). Polar readouts update live.
2. **Polar-only drag.** Reset. Drag `Shoulder elevation` from its NEUTRAL value to 150°. Arm lifts in the **current plane** (whatever `Shoulder plane` shows). Rect readouts update live.
3. **Plane sweep.** Set elevation to 90°. Drag plane from −90° (cross-body) → 0° (sagittal) → 90° (lateral) → 180° (behind). Arm sweeps a horizontal circle. No jumps.
4. **Pole behaviour.** Set elevation to 0° (arm straight down). Drag plane slider — mannequin does not move (expected). Raise elevation; plane starts affecting direction.
5. **Clamp behaviour — right arm.** Set `Shoulder side` (rect) to 120°. Touch `Shoulder elevation` — rect's `Shoulder side` clamps to 90°. This is expected; documented in the spec.
6. **Clamp behaviour — left arm asymmetry.** Left arm's rect `Shoulder side` is `0..120` (no cross-body negative region). Drag the **left** `Shoulder plane` slider to -45° — `Shoulder side` clamps to 0° and the arm stays in the sagittal plane. This is expected (matches the left-arm rect's documented range).
7. **Joint highlight.** Click the right shoulder joint on the mannequin. All four right-shoulder rows (`Shoulder fwd`, `Shoulder side`, `Shoulder elevation`, `Shoulder plane`) get the yellow ring, and the scroll brings the first highlighted row into view.
8. **Clipboard copy.** Click the copy button on a polar row while the right shoulder is selected. Expect clipboard text like `rightShoulder (правe плече) · rightShoulderElevationDeg: 135` — the `spec.id` appears instead of a raw `key`.
9. **START/END phase switch.** Switch to END phase, confirm polar sliders reflect END anchor's shoulder state, drag a polar slider, switch back to START, confirm START still has its prior values (no cross-phase bleed).
10. **Reset.** Click Reset. All four shoulder rows return to neutral pose's shoulder — rect to ~(41°, 31°), polar to whatever `flexAbdToPolar({flex: 41, abd: 31})` yields.

- [ ] **Step 4: (No commit) — this task is a sign-off gate**

If any item in Step 3 regresses, back up to the task that introduced the regression (likely Task 5 for plumbing issues or Task 6 for math issues) and fix it there with a new commit. Do NOT squash.

---

## Out of scope (per spec)

- Ukrainian translation of polar labels — stays English to match existing shoulder labels.
- Making polar the canonical parameterization (would require extractor, fixture, and Android Room migrations).
- Disabling the plane slider visually at poles.
- Same polar treatment for hips/knees.

---

## Self-review checklist (for the plan author)

- [x] Every section in the spec is covered by a task (the "Parameterization" conversion formulas → Tasks 1, 2; degeneracies → Tasks 1, 2, 3; UI layout → Task 6; `AnchorParamSpec` extension → Task 5; file list → matches spec §Files touched).
- [x] No placeholders (`TBD`, `TODO`, "handle appropriately") anywhere.
- [x] Every step with code shows the exact code, not a description.
- [x] Types are consistent across tasks — `FlexAbd`, `Polar`, `DirectAnchorParamSpec`, `ComputedAnchorParamSpec`, `AnchorParamSpec`.
- [x] Commits are one-per-task.
- [x] Test-first on all pure-math tasks (Tasks 1, 2, 3, 4, 7); type-migration task (5) verifies by compiler + existing suite; wiring task (6) has unit coverage (Task 7) plus a manual gate.
