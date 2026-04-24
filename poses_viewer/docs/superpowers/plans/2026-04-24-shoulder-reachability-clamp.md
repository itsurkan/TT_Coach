# Right-shoulder reachability clamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent the `DrillEditor` right-shoulder sliders from committing anatomically unreachable (flex, abd) combinations by applying a linear-envelope hard clamp at slider-commit time.

**Architecture:** A new pure helper module `src/drill/shoulderClamp.ts` encodes the envelope and a `clampRightShoulder(flex, abd, activeKey)` function (Policy P1 — last-touched slider wins). `AnchorSliders.tsx` calls the helper inside its `setKey` write-through when the changed key is one of the two shoulder keys; all other keys pass through unchanged. No changes to the reconstructor, extractor, or anchor schema.

**Tech Stack:** TypeScript, React 18, vitest 4. Lives under `poses_viewer/`; run tests and build from that directory (`cd poses_viewer && npm run test`, `cd poses_viewer && npm run build`).

**Reference spec:** [poses_viewer/docs/superpowers/specs/2026-04-24-shoulder-reachability-clamp-design.md](poses_viewer/docs/superpowers/specs/2026-04-24-shoulder-reachability-clamp-design.md)

---

## File Structure

- **Create** `poses_viewer/src/drill/shoulderClamp.ts` — pure helper: envelope constants, `rightShoulderAbdMin(flex)`, `rightShoulderFlexMax(abd)`, `clampRightShoulder(flex, abd, activeKey)`.
- **Create** `poses_viewer/src/drill/__tests__/shoulderClamp.test.ts` — unit tests for the envelope helpers and the clamp.
- **Modify** `poses_viewer/src/components/AnchorSliders.tsx:40-42` — wrap `setKey` so right-shoulder writes route through `clampRightShoulder`.

No other files touched. The clamp is an editor-only guardrail; the reconstructor and extractor continue to operate on raw anchor values.

---

### Task 1: Pure envelope helper — write failing tests first

**Files:**
- Create: `poses_viewer/src/drill/__tests__/shoulderClamp.test.ts`

- [ ] **Step 1.1: Write the failing tests**

Create `poses_viewer/src/drill/__tests__/shoulderClamp.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import {
  rightShoulderAbdMin,
  rightShoulderFlexMax,
  clampRightShoulder,
  RIGHT_SHOULDER_FLEX_KNEE,
  RIGHT_SHOULDER_FLEX_CEIL,
  RIGHT_SHOULDER_ABD_AT_KNEE,
  RIGHT_SHOULDER_ABD_AT_CEIL,
} from '../shoulderClamp'

describe('rightShoulderAbdMin', () => {
  it('is -40 at flex=100 (sample A)', () => {
    expect(rightShoulderAbdMin(100)).toBe(-40)
  })
  it('is 70 at flex=180 (sample B)', () => {
    expect(rightShoulderAbdMin(180)).toBe(70)
  })
  it('is flat at -40 below the knee', () => {
    expect(rightShoulderAbdMin(50)).toBe(-40)
    expect(rightShoulderAbdMin(-30)).toBe(-40)
  })
  it('is flat at 70 at or above the ceiling', () => {
    expect(rightShoulderAbdMin(200)).toBe(70)
  })
  it('interpolates linearly at flex=140 (midpoint)', () => {
    expect(rightShoulderAbdMin(140)).toBeCloseTo(15, 5)
  })
})

describe('rightShoulderFlexMax', () => {
  it('returns the knee flex at abd=-40', () => {
    expect(rightShoulderFlexMax(-40)).toBe(RIGHT_SHOULDER_FLEX_KNEE)
  })
  it('returns the ceiling flex at abd=70', () => {
    expect(rightShoulderFlexMax(70)).toBe(RIGHT_SHOULDER_FLEX_CEIL)
  })
  it('clamps to ceiling flex when abd > 70', () => {
    expect(rightShoulderFlexMax(120)).toBe(RIGHT_SHOULDER_FLEX_CEIL)
  })
  it('clamps to knee flex when abd < -40', () => {
    expect(rightShoulderFlexMax(-80)).toBe(RIGHT_SHOULDER_FLEX_KNEE)
  })
  it('is the inverse of rightShoulderAbdMin on the sloped segment', () => {
    // abd=15 at flex=140 from the other test; round-trip
    expect(rightShoulderFlexMax(15)).toBeCloseTo(140, 5)
  })
})

describe('clampRightShoulder', () => {
  it('passes through valid combinations unchanged', () => {
    const out = clampRightShoulder(41, 31, 'rightShoulderAngleDeg')
    expect(out).toEqual({ flex: 41, abd: 31 })
  })
  it('leaves sample A untouched (on the bound)', () => {
    const out = clampRightShoulder(100, -40, 'rightShoulderAngleDeg')
    expect(out).toEqual({ flex: 100, abd: -40 })
  })
  it('leaves sample B untouched (on the bound)', () => {
    const out = clampRightShoulder(180, 70, 'rightShoulderAbductionDeg')
    expect(out).toEqual({ flex: 180, abd: 70 })
  })
  it('raises abd when flex is the active key at flex=180, abd=0', () => {
    const out = clampRightShoulder(180, 0, 'rightShoulderAngleDeg')
    expect(out.flex).toBe(180)
    expect(out.abd).toBe(70)
  })
  it('lowers flex when abd is the active key at flex=180, abd=0', () => {
    const out = clampRightShoulder(180, 0, 'rightShoulderAbductionDeg')
    expect(out.abd).toBe(0)
    // abd_min(flex)=0 → flex = 100 + (0-(-40))/(70-(-40)) * 80 ≈ 129.09
    expect(out.flex).toBeCloseTo(129.09, 1)
  })
  it('raises abd to 15 when flex=140 is the active key and abd=0', () => {
    const out = clampRightShoulder(140, 0, 'rightShoulderAngleDeg')
    expect(out.flex).toBe(140)
    expect(out.abd).toBeCloseTo(15, 5)
  })
  it('leaves sub-knee flex alone (constraint inactive)', () => {
    const out = clampRightShoulder(50, -40, 'rightShoulderAngleDeg')
    expect(out).toEqual({ flex: 50, abd: -40 })
  })
})
```

- [ ] **Step 1.2: Run the tests to verify they fail**

Run: `cd poses_viewer && npm run test -- shoulderClamp`

Expected: **FAIL** — all cases fail with module-not-found on `../shoulderClamp`. That's the correct red state before the helper exists.

- [ ] **Step 1.3: Commit the failing tests**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/drill/__tests__/shoulderClamp.test.ts
git commit -m "test(poses_viewer): failing tests for right-shoulder reachability clamp"
```

---

### Task 2: Implement the envelope helper

**Files:**
- Create: `poses_viewer/src/drill/shoulderClamp.ts`

- [ ] **Step 2.1: Write the helper module**

Create `poses_viewer/src/drill/shoulderClamp.ts`:

```ts
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
```

- [ ] **Step 2.2: Run the tests to verify they pass**

Run: `cd poses_viewer && npm run test -- shoulderClamp`

Expected: **PASS** — all describe blocks from Task 1 green.

- [ ] **Step 2.3: Typecheck**

Run: `cd poses_viewer && npx tsc --noEmit`

Expected: no errors.

- [ ] **Step 2.4: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/drill/shoulderClamp.ts
git commit -m "feat(poses_viewer): right-shoulder reachability clamp helper"
```

---

### Task 3: Wire the clamp into `AnchorSliders.setKey`

**Files:**
- Modify: `poses_viewer/src/components/AnchorSliders.tsx:40-42`

- [ ] **Step 3.1: Add the import**

At the top of `poses_viewer/src/components/AnchorSliders.tsx`, add the import next to the existing `PoseAnchor` imports. Current imports at lines 1-3:

```ts
import { useLayoutEffect, useRef, useState } from 'react'
import type { PoseAnchor, AnchorPhase } from '../drill/PoseAnchor'
import { ANCHOR_PARAM_GROUPS } from '../drill/PoseAnchor'
```

Add this line immediately after:

```ts
import { clampRightShoulder } from '../drill/shoulderClamp'
```

- [ ] **Step 3.2: Replace `setKey` with a shoulder-aware version**

Current `setKey` at `poses_viewer/src/components/AnchorSliders.tsx:40-42`:

```ts
  const setKey = (k: keyof PoseAnchor, v: number) => {
    onChange({ ...anchor, [k]: v })
  }
```

Replace with:

```ts
  const setKey = (k: keyof PoseAnchor, v: number) => {
    let next: PoseAnchor = { ...anchor, [k]: v }
    if (k === 'rightShoulderAngleDeg' || k === 'rightShoulderAbductionDeg') {
      const clamped = clampRightShoulder(
        next.rightShoulderAngleDeg,
        next.rightShoulderAbductionDeg,
        k,
      )
      next = {
        ...next,
        rightShoulderAngleDeg: clamped.flex,
        rightShoulderAbductionDeg: clamped.abd,
      }
    }
    onChange(next)
  }
```

- [ ] **Step 3.3: Typecheck**

Run: `cd poses_viewer && npx tsc --noEmit`

Expected: no errors. If the type-narrowed `k` triggers a complaint, the union-literal comparison above should satisfy TypeScript without a cast. If not, cast to `ShoulderActiveKey` explicitly: `k as ShoulderActiveKey`.

- [ ] **Step 3.4: Run the full vitest suite**

Run: `cd poses_viewer && npm run test`

Expected: all existing tests (`anchorExtractor.test.ts`, `anchorInterpolator.test.ts`, `skeletonReconstructor.test.ts`) plus the new `shoulderClamp.test.ts` all pass. The clamp lives in the editor only, so no reconstructor fingerprint should change.

- [ ] **Step 3.5: Full build**

Run: `cd poses_viewer && npm run build`

Expected: clean build (tsc + vite).

- [ ] **Step 3.6: Manual verification in the editor**

Run the dev server: `cd poses_viewer && npm run dev` (http://localhost:5780). Open `DrillEditor`.

1. Set `rightShoulderAbductionDeg` to `-40`. Drag `rightShoulderAngleDeg` up to `180`. Expected: `rightShoulderAbductionDeg` visibly jumps up (eventually reaching `70` at `flex=180`) as `flex` crosses the envelope; `rightShoulderAngleDeg` stays wherever dragged.
2. Reset. Set `rightShoulderAngleDeg` to `180`. Drag `rightShoulderAbductionDeg` down to `-40`. Expected: `rightShoulderAngleDeg` visibly jumps down (to ~`100` at `abd=-40`); `rightShoulderAbductionDeg` stays.
3. Reset to defaults (`flex=41`, `abd=31`). Both sliders move independently within their full ranges until the high-flex / low-abd corner.

If any of these fail, the wiring is wrong — recheck the import path and that `setKey` is the single write-through used by the UI.

- [ ] **Step 3.7: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/components/AnchorSliders.tsx
git commit -m "feat(poses_viewer): clamp right-shoulder sliders to anatomical envelope"
```

---

## Self-Review

**Spec coverage:**
- Envelope definition (linear, two samples, flat outside) → Task 2 helper + Task 1 tests for `rightShoulderAbdMin` at flex=100/180/50/140/200.
- `clampRightShoulder` Policy P1 — last-touched slider wins → Task 1 test cases for (180, 0, flex-active) vs (180, 0, abd-active); Task 2 implementation branches on `activeKey`.
- Hyperextension left uncapped → implicit (no sub-knee constraint added; Task 1 verifies `abdMin(-30)==-40`).
- Editor-only, not in reconstructor/extractor → Task 3 touches only `AnchorSliders`; Task 3 Step 3.4 confirms no reconstructor/extractor tests regress.
- Left shoulder out of scope → no tasks.
- Manual verification steps from the spec → Task 3 Step 3.6 mirrors them.

**Placeholder scan:** no TBDs, no "similar to", all code and commands shown inline.

**Type consistency:**
- `ShoulderActiveKey` — exported from `shoulderClamp.ts` (Task 2), not reused in `AnchorSliders` (Task 3 relies on TypeScript's union narrowing on the literal `||` check; a cast is mentioned as a fallback). Names align: `rightShoulderAngleDeg`, `rightShoulderAbductionDeg` match `PoseAnchor.ts:44-45`.
- Return type `{ flex: number; abd: number }` consistent across helper signature and test assertions.
- Constants `RIGHT_SHOULDER_FLEX_KNEE` / `FLEX_CEIL` / `ABD_AT_KNEE` / `ABD_AT_CEIL` named consistently and used in both the helper and the tests.
