# Anthropomorphic joint fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple the Mannequin Editor's Reset-pose target from slider ranges, widen shoulder/knee-yaw ranges to anatomical limits, and fix the elbow hinge so a raised arm bends toward the face instead of sideways.

**Architecture:** Three self-contained changes in `poses_viewer/src/drill/`. (1) Add optional `defaultValue` to `AnchorParamSpec`; have `buildMidpointPose` prefer it. (2) Widen six slider ranges and set `defaultValue` where the new midpoint would drift from the user's Reset target. (3) Replace the forearm hinge axis in `reconstructFromAnchor` with `cross(upperArmDir, torsoUp)`, falling back to `shoulderAcross` when the cross degenerates. Tests live in [src/drill/__tests__/skeletonReconstructor.test.ts](../../../src/drill/__tests__/skeletonReconstructor.test.ts).

**Tech Stack:** TypeScript, React/Vite (debug UI), Vitest (unit tests).

**Spec:** [docs/superpowers/specs/2026-04-23-anthropomorphic-joint-fixes-design.md](../specs/2026-04-23-anthropomorphic-joint-fixes-design.md)

---

## File Structure

- **Modify** `src/drill/PoseAnchor.ts` — add `defaultValue?: number` to `AnchorParamSpec`; update 6 entries in `ANCHOR_PARAM_GROUPS`.
- **Modify** `src/drill/neutralPose.ts` — teach `buildMidpointPose` to honor `spec.defaultValue`.
- **Modify** `src/drill/skeletonReconstructor.ts` — replace the forearm hinge axis inside `buildArm`.
- **Modify** `src/drill/__tests__/skeletonReconstructor.test.ts` — add 3 tests (two behavioral, one for `MIDPOINT_POSE` defaults).

All four files already exist. No new files, no new dependencies.

Run tests from `/Users/itsurkan/Dev/personal/TT_Coach/poses_viewer` with `npm run test`. Type-check with `npx tsc --noEmit` (pre-existing `fs`/`path` errors in `src/utils/__tests__/*.test.ts` are unrelated and can be filtered with `| grep -v "src/utils/__tests__"`).

---

## Task 1: Add `defaultValue` field to `AnchorParamSpec` and honor it in `buildMidpointPose`

**Files:**
- Modify: `src/drill/PoseAnchor.ts` (interface `AnchorParamSpec`)
- Modify: `src/drill/neutralPose.ts` (function `buildMidpointPose`)
- Test: `src/drill/__tests__/skeletonReconstructor.test.ts`

This task establishes the new plumbing without changing any ranges or any pose output yet. Once this task is committed, `MIDPOINT_POSE` still computes exactly the same values because no spec uses `defaultValue` yet.

- [ ] **Step 1: Write a failing test that `MIDPOINT_POSE` uses `defaultValue` when present**

Append this test to [src/drill/__tests__/skeletonReconstructor.test.ts](../../../src/drill/__tests__/skeletonReconstructor.test.ts), inside the existing `describe('reconstructFromAnchor', ...)` block (before its closing `})`):

```ts
  it('MIDPOINT_POSE honors defaultValue on param specs when present', async () => {
    // Sanity: once specs set defaultValue for shoulder flex/abduction to 41/31,
    // MIDPOINT_POSE must reflect those targets rather than raw (min+max)/2.
    const { MIDPOINT_POSE } = await import('../neutralPose')
    // These four will have defaultValue set by Task 2. Until then this test
    // also asserts the current (min+max)/2 behaviour, which is (min=-30,
    // max=112)/2 = 41 for flex and (min=0, max=62)/2 = 31 for abduction —
    // so the test passes today AND after Task 2 widens the ranges.
    expect(MIDPOINT_POSE.rightShoulderAngleDeg).toBe(41)
    expect(MIDPOINT_POSE.leftShoulderAngleDeg).toBe(41)
    expect(MIDPOINT_POSE.rightShoulderAbductionDeg).toBe(31)
    expect(MIDPOINT_POSE.leftShoulderAbductionDeg).toBe(31)
    expect(MIDPOINT_POSE.rightThighAbductionDeg).toBe(17)
    expect(MIDPOINT_POSE.leftThighAbductionDeg).toBe(17)
  })
```

- [ ] **Step 2: Run test to verify it passes today**

Run: `cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npm run test -- --reporter=verbose skeletonReconstructor`
Expected: PASS (current midpoints already equal these values). The test's purpose is to lock the target values; Task 2 will widen ranges and rely on `defaultValue` to keep it green.

- [ ] **Step 3: Add `defaultValue` to the `AnchorParamSpec` interface**

In [src/drill/PoseAnchor.ts](../../../src/drill/PoseAnchor.ts), replace the `AnchorParamSpec` interface (currently around lines 108-115) with:

```ts
/** Slider spec for the editor UI. */
export interface AnchorParamSpec {
  key: keyof PoseAnchor
  label: string
  min: number
  max: number
  step: number
  /** Reset/MIDPOINT_POSE target. When omitted, MIDPOINT_POSE uses (min+max)/2.
   *  Use this to decouple "slider reach" from "default pose" — e.g. shoulder
   *  abduction goes up to 120° but the ready-position default sits at 31°. */
  defaultValue?: number
}
```

- [ ] **Step 4: Teach `buildMidpointPose` to prefer `defaultValue`**

In [src/drill/neutralPose.ts](../../../src/drill/neutralPose.ts), replace the `buildMidpointPose` function (currently at lines 122-133) with:

```ts
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
```

- [ ] **Step 5: Run all skeletonReconstructor tests**

Run: `cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npm run test -- skeletonReconstructor`
Expected: all tests pass, including the new `MIDPOINT_POSE honors defaultValue` test. No existing test should change (ranges haven't moved yet).

- [ ] **Step 6: Type-check**

Run: `cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npx tsc --noEmit 2>&1 | grep -v "src/utils/__tests__" | grep -v "^$" || echo "typecheck clean"`
Expected: `typecheck clean` (pre-existing `fs`/`path` errors in `src/utils/__tests__/` are filtered; no other errors).

- [ ] **Step 7: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/drill/PoseAnchor.ts poses_viewer/src/drill/neutralPose.ts poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts
git commit -m "poses_viewer: add defaultValue to AnchorParamSpec

Optional per-spec default lets Reset/MIDPOINT_POSE land on a chosen
value independent of slider range. When absent, falls back to the
current (min+max)/2 midpoint so existing specs are unaffected."
```

---

## Task 2: Widen shoulder flex/abduction and knee-yaw ranges; set explicit defaults

**Files:**
- Modify: `src/drill/PoseAnchor.ts` (6 entries in `ANCHOR_PARAM_GROUPS`)

The 6 ranges per the spec:

| key | old | new | defaultValue |
|---|---|---|---|
| `rightShoulderAngleDeg` | `min: -30, max: 112` | `min: -30, max: 180` | `41` |
| `leftShoulderAngleDeg`  | `min: -30, max: 112` | `min: -30, max: 180` | `41` |
| `rightShoulderAbductionDeg` | `min: 0, max: 62` | `min: 0, max: 120` | `31` |
| `leftShoulderAbductionDeg`  | `min: 0, max: 62` | `min: 0, max: 120` | `31` |
| `rightKneeYawDeg` | `min: -90, max: 90` | `min: -85, max: 85` | *(none — natural midpoint 0)* |
| `leftKneeYawDeg`  | `min: -90, max: 90` | `min: -85, max: 85` | *(none)* |

Thigh-abduction entries already have `min: -30, max: 64` (midpoint 17) and stay unchanged — they need a `defaultValue: 17` added so the test from Task 1 continues to pass *by design* rather than by coincidence.

- [ ] **Step 1: Run the Task 1 test first — still passing**

Run: `cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npm run test -- skeletonReconstructor`
Expected: PASS. Baseline before any spec edits.

- [ ] **Step 2: Update the right-arm specs**

In [src/drill/PoseAnchor.ts](../../../src/drill/PoseAnchor.ts), inside the `'Right arm (stroking)'` group (currently around lines 137-144), replace the `rightShoulderAngleDeg` and `rightShoulderAbductionDeg` entries:

```ts
      { key: 'rightShoulderAngleDeg',     label: 'Shoulder fwd',   min: -30, max: 180, step: 1, defaultValue: 41 },
      { key: 'rightShoulderAbductionDeg', label: 'Shoulder side',  min: 0,   max: 120, step: 1, defaultValue: 31 },
```

- [ ] **Step 3: Update the left-arm specs (mirror of right)**

Inside the `'Left arm'` group (around lines 147-154), replace the `leftShoulderAngleDeg` and `leftShoulderAbductionDeg` entries:

```ts
      { key: 'leftShoulderAngleDeg',     label: 'Shoulder fwd',   min: -30, max: 180, step: 1, defaultValue: 41 },
      { key: 'leftShoulderAbductionDeg', label: 'Shoulder side',  min: 0,   max: 120, step: 1, defaultValue: 31 },
```

- [ ] **Step 4: Update the leg specs (knee yaw + add explicit thigh-abduction defaults)**

Inside the `'Legs'` group (around lines 156-170), replace the four relevant entries. The thigh-abduction lines keep the same range but gain `defaultValue: 17`; the knee-yaw lines narrow to `[-85, 85]`:

```ts
      { key: 'leftThighAbductionDeg',  label: 'L thigh abduction',  min: -30, max: 64,  step: 1, defaultValue: 17 },
      { key: 'rightThighAbductionDeg', label: 'R thigh abduction',  min: -30, max: 64,  step: 1, defaultValue: 17 },
```

and:

```ts
      { key: 'leftKneeYawDeg',         label: 'L knee yaw',         min: -85, max: 85,  step: 1 },
      { key: 'rightKneeYawDeg',        label: 'R knee yaw',         min: -85, max: 85,  step: 1 },
```

- [ ] **Step 5: Run the full test file — Task 1 test must still pass**

Run: `cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npm run test -- skeletonReconstructor`
Expected: all existing tests pass, including the `MIDPOINT_POSE honors defaultValue` test. With the widened shoulder ranges, the test now passes because of `defaultValue` rather than by coincidence of midpoint math. Knee yaw has no `defaultValue`, so its midpoint falls through to `(−85+85)/2 = 0` — still matching any implicit assumption of 0.

- [ ] **Step 6: Type-check**

Run: `cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npx tsc --noEmit 2>&1 | grep -v "src/utils/__tests__" | grep -v "^$" || echo "typecheck clean"`
Expected: `typecheck clean`.

- [ ] **Step 7: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/drill/PoseAnchor.ts
git commit -m "poses_viewer: widen shoulder+knee-yaw ranges, pin defaults

Shoulder flex extended to [-30, 180] (full overhead reach) with
defaultValue=41. Shoulder abduction extended to [0, 120] (above-
horizontal) with defaultValue=31. Knee yaw tightened to [-85, 85]
(88° was anatomically implausible). Thigh abduction range unchanged
but now carries an explicit defaultValue=17."
```

---

## Task 3: Fix the elbow hinge axis in `skeletonReconstructor`

**Files:**
- Modify: `src/drill/skeletonReconstructor.ts` (forearm computation inside `buildArm`)
- Test: `src/drill/__tests__/skeletonReconstructor.test.ts`

The issue: today the forearm bends around `shoulderAcross` (world shoulder-line). When the upper arm abducts upward, `shoulderAcross` is no longer perpendicular to the upper arm, so the bend plane is wrong — reads as a rotation. Fix: hinge axis = `cross(upperArmDir, torsoUp)`, with a `shoulderAcross` fallback when the cross degenerates (arm nearly parallel to spine).

- [ ] **Step 1: Write a failing test — abducted arm should bend toward the head**

Append this test to [src/drill/__tests__/skeletonReconstructor.test.ts](../../../src/drill/__tests__/skeletonReconstructor.test.ts) inside the existing `describe` block:

```ts
  it('elbow bend on an abducted arm points the forearm toward the head', () => {
    // Right arm abducted 90° (horizontal out to the player's right), elbow 90°.
    // New hinge = cross(upperArm, torsoUp): the bend plane contains the upper
    // arm and the spine, so the forearm rotates UP (toward the head/camera),
    // not further sideways. Rendered Y is larger = lower on screen; wrist must
    // be HIGHER on screen than the elbow.
    const pose = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 0,
      rightShoulderAbductionDeg: 90,
      rightElbowAngleDeg: 90,
    }
    const out = reconstructFromAnchor(pose)
    const elbow = out[LM.R_ELBOW]
    const wrist = out[LM.R_WRIST]
    // Wrist is higher on screen than elbow (smaller y in MediaPipe convention).
    expect(wrist.y).toBeLessThan(elbow.y)
    // Forearm length ≈ BONES.forearm, and it's mostly vertical — so the
    // vertical drop wrist→elbow is close to the full forearm length.
    expect(elbow.y - wrist.y).toBeGreaterThan(BONES.forearm * 0.8)
  })
```

- [ ] **Step 2: Run the new test to verify it fails**

Run: `cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npm run test -- --reporter=verbose skeletonReconstructor -t "abducted arm"`
Expected: FAIL. With the current `shoulderAcross` hinge, an abducted arm bent at 90° sends the forearm further out to the side (same y as the elbow), not up — so `wrist.y < elbow.y` is false and `elbow.y - wrist.y > BONES.forearm * 0.8` is false.

- [ ] **Step 3: Also add a regression test for the rest-down arm**

Append this test to the same file (still inside the `describe` block):

```ts
  it('elbow bend on a rest-down arm still points the forearm forward (regression guard)', () => {
    // Arm hanging straight down, elbow 90°. Historical behaviour: forearm
    // points forward (−z). New hinge = cross(down, torsoUp) = shoulderAcross,
    // so forearm still bends around shoulderAcross — same result. Locks this.
    const pose = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 0,
      rightShoulderAbductionDeg: 0,
      rightElbowAngleDeg: 90,
    }
    const out = reconstructFromAnchor(pose)
    const elbow = out[LM.R_ELBOW]
    const wrist = out[LM.R_WRIST]
    // Forearm points forward: wrist.z < elbow.z (forward is −z).
    expect(wrist.z).toBeLessThan(elbow.z)
    // Forearm is roughly horizontal, so vertical drop is small.
    expect(Math.abs(wrist.y - elbow.y)).toBeLessThan(BONES.forearm * 0.2)
  })
```

- [ ] **Step 4: Run the regression test — it should PASS today**

Run: `cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npm run test -- --reporter=verbose skeletonReconstructor -t "rest-down arm"`
Expected: PASS. The rest-down case already behaves correctly; this locks it so the Step 5 change doesn't regress.

- [ ] **Step 5: Implement the new forearm hinge axis**

In [src/drill/skeletonReconstructor.ts](../../../src/drill/skeletonReconstructor.ts), inside the `buildArm` function, find the current forearm block (around lines 246-252):

```ts
    const elbowBend = 180 - elbowDeg
    const impForearm = side === 'L' ? anchor.dirOverrides?.leftForearm : anchor.dirOverrides?.rightForearm
    const forearmDir = impForearm
      ? normalize(impForearm as V3)
      : normalize(rotAroundAxis(upperArmDir, shoulderAcross, -elbowBend))
    const wrist = add(elbow, scale(forearmDir, B.forearm))
    out[idxWrist] = mkLm(idxWrist, wrist)
```

Replace with:

```ts
    const elbowBend = 180 - elbowDeg
    const impForearm = side === 'L' ? anchor.dirOverrides?.leftForearm : anchor.dirOverrides?.rightForearm
    // Hinge axis = cross(upperArmDir, torsoUp). This keeps the forearm bend
    // plane in the plane containing the upper arm and the spine, so an
    // abducted/raised arm bends toward the face rather than sideways. When
    // the upper arm is nearly parallel to the spine the cross degenerates,
    // so fall back to shoulderAcross (the legacy hinge). forearmTwistDeg
    // still independently rotates the hand fan around the forearm axis.
    const hingeRaw: V3 = [
      upperArmDir[1]*torsoUp[2] - upperArmDir[2]*torsoUp[1],
      upperArmDir[2]*torsoUp[0] - upperArmDir[0]*torsoUp[2],
      upperArmDir[0]*torsoUp[1] - upperArmDir[1]*torsoUp[0],
    ]
    const hingeMag = Math.sqrt(hingeRaw[0]*hingeRaw[0] + hingeRaw[1]*hingeRaw[1] + hingeRaw[2]*hingeRaw[2])
    const elbowHinge: V3 = hingeMag < 1e-6 ? shoulderAcross : normalize(hingeRaw)
    const forearmDir = impForearm
      ? normalize(impForearm as V3)
      : normalize(rotAroundAxis(upperArmDir, elbowHinge, -elbowBend))
    const wrist = add(elbow, scale(forearmDir, B.forearm))
    out[idxWrist] = mkLm(idxWrist, wrist)
```

Note: the existing hand computation on the next lines continues to use `shoulderAcross` for wrist bend and fan. That's intentional — this plan only changes the forearm hinge, not the wrist/hand.

- [ ] **Step 6: Run the full test file**

Run: `cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npm run test -- skeletonReconstructor`
Expected: all tests pass, including both new tests (`abducted arm` and `rest-down arm`) and all existing tests (`bent elbow (90°) shortens the vertical drop…`, `straight elbow (180°) places wrist…`, etc.).

If the existing `bent elbow (90°) shortens the vertical drop` test fails: check the test's pose. It sets `rightShoulderAbductionDeg: 0, rightShoulderAngleDeg: 0` (arm down), so `upperArmDir ≈ torsoDown` which is antiparallel to `torsoUp` → cross magnitude ≈ 0 → fallback to `shoulderAcross` → same behaviour as today. The test should pass. If it doesn't, the cross-product signs may be off — double-check the `[a×b]_i = a_{i+1}·b_{i+2} − a_{i+2}·b_{i+1}` pattern in the code above.

- [ ] **Step 7: Type-check**

Run: `cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npx tsc --noEmit 2>&1 | grep -v "src/utils/__tests__" | grep -v "^$" || echo "typecheck clean"`
Expected: `typecheck clean`.

- [ ] **Step 8: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/drill/skeletonReconstructor.ts poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts
git commit -m "poses_viewer: derive elbow hinge from upper arm × spine

The forearm used to bend around shoulderAcross (world shoulder line),
which is only correct for an arm hanging down. Raising/abducting the
arm put the bend plane out of sync with anatomy — reading as a twist.
New hinge = cross(upperArm, torsoUp) keeps the bend plane in the
upper-arm + spine plane, so the forearm rotates toward the face when
the arm is overhead. Falls back to shoulderAcross when the cross
degenerates (arm parallel to spine). forearmTwistDeg still rotates the
hand fan around the forearm independently."
```

---

## Task 4: Manual smoke check in the Mannequin Editor

**Files:** none modified

Code-side work is complete. This task is a quick visual sanity pass in the browser before marking the feature done. No commit.

- [ ] **Step 1: Start the dev server**

Run: `cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npm run dev`
Expected: Vite prints a local URL (e.g. `http://localhost:5173/` or similar).

- [ ] **Step 2: Open the Mannequin Editor and verify Reset**

Open the URL, enter the Mannequin Editor, click **Reset**. Confirm the figure sits in the expected athletic-ready pose: shoulders flexed ~41°, abducted ~31°; thighs abducted ~17°; knees yawed to 0°.

(The HUD/copy buttons next to each slider show the live value — cross-check `rightShoulderAngleDeg: 41`, `rightShoulderAbductionDeg: 31`, `leftShoulderAngleDeg: 41`, `leftShoulderAbductionDeg: 31`, `leftThighAbductionDeg: 17`, `rightThighAbductionDeg: 17`, `leftKneeYawDeg: 0`, `rightKneeYawDeg: 0`.)

- [ ] **Step 3: Verify extended ranges**

Drag `rightShoulderAbductionDeg` to 120. The arm should lift to well above horizontal. Drag `rightShoulderAngleDeg` to 180. The arm should reach straight overhead. Drag `rightKneeYawDeg` — the slider should cap at ±85°, and the leg should never swing to the implausible ±90° position it used to reach.

- [ ] **Step 4: Verify elbow hinge**

Set `rightShoulderAbductionDeg: 90`, `rightShoulderAngleDeg: 0`, `rightElbowAngleDeg: 30` (deep bend). The forearm should rotate **upward toward the head/camera**, not sideways. Then set `rightShoulderAbductionDeg: 0, rightShoulderAngleDeg: 0, rightElbowAngleDeg: 30` — forearm should point forward (toward the camera), same as before.

- [ ] **Step 5: Stop the dev server**

Ctrl-C in the terminal running `npm run dev`.

- [ ] **Step 6: Mark task complete**

If all four checks in Steps 2-4 looked right, the feature is done. If any were wrong, capture what you saw and revisit the relevant task (Task 2 for ranges/defaults, Task 3 for hinge direction).

---

## Self-Review

Spec coverage: §1 (decouple Reset from range) → Task 1. §2 (6 range changes) → Task 2. §3 (elbow hinge) → Task 3. §4 (tests) → Tasks 1 & 3. Out-of-scope items (anchorExtractor clamp, thigh-abduction widen, UI) explicitly skipped per spec.

Placeholder scan: no TODOs, no "appropriate error handling", every step contains either exact code or an exact command with expected output.

Type consistency: `defaultValue` is `number | undefined`, matching the `?: number` declaration; the cross-product hinge code uses the same `V3` tuple type and `normalize` helper already defined in the file.

Scope: 4 files, 3 logical changes, ~4 commits. Single-session plan, no decomposition needed.
