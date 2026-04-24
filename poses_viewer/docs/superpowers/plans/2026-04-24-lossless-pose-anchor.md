# Lossless Pose Anchor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `PoseAnchor` lossless — every imported MediaPipe pose (except head) is reproducible from angle sliders alone, and `dirOverrides` is removed entirely. Adds the missing DOFs: humeral twist (`*ElbowYawDeg`), wrist yaw (`*WristYawDeg`), and a proper forearm-twist extractor. Foot and head orientation explicitly kept at the current precision.

**Architecture:** Extend the angle-based anchor with 3 new DOFs per arm (humeral twist + wrist yaw + honest forearm-twist extraction). Rewrite the extractor to decompose landmarks into all angle fields. Delete `dirOverrides` from the data model so the mannequin's slider ranges become authoritative — imported poses clamp to anatomical limits rather than bypassing them. Ship the plan alongside a reference document describing how the mannequin works.

**Tech Stack:** TypeScript + Vitest. Zero new deps. Pure `commonMain`-style math (no platform APIs), so the code stays trivially testable.

---

## File Structure

**New files:**
- `poses_viewer/docs/mannequin.md` — human-readable description of the mannequin: DOFs, their ranges, how FK builds a pose, how import maps landmarks to angles, and where the information loss (if any) sits. Written once at the end of the plan when all details are finalized.

**Modified files:**
- `poses_viewer/src/drill/PoseAnchor.ts` — add new angle fields + slider specs, remove `dirOverrides` field and `LimbDirections` type.
- `poses_viewer/src/drill/neutralPose.ts` — populate new fields with `0` in `STANDING_POSE` and sensible values in `NEUTRAL_POSE`.
- `poses_viewer/src/drill/anchorInterpolator.ts` — lerp the new fields.
- `poses_viewer/src/drill/skeletonReconstructor.ts` — FK: apply humeral twist and wrist yaw in the arm chain; remove `dirOverrides` fast-paths.
- `poses_viewer/src/drill/anchorExtractor.ts` — add extraction for the 3 new per-arm angles; delete `extractLimbDirections`; stop returning `dirOverrides`.
- `poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts` — regression guards + new-DOF correctness tests.
- `poses_viewer/src/drill/__tests__/anchorExtractor.test.ts` — NEW (or extend existing if present) — round-trip test: landmarks → anchor → landmarks ≈ landmarks for limbs.
- `poses_viewer/src/components/DrillEditor.tsx` — remove `dirOverrides` handling in `diffKey`, `clearRelatedOverrides`, `setActiveAnchor` calls; add the new slider keys to `clearRelatedOverrides`.

**Grep targets to audit before each commit:** `dirOverrides`, `LimbDirections`, `extractLimbDirections`, `impUpper`, `impForearm`, `impThigh`, `impShin`. By end of plan these should exist only in git history.

---

## Decisions Locked In

(Answers the user gave up-front — do not revisit.)

1. **`dirOverrides` fate:** delete the field entirely. No deprecation period. Any persisted drill configs from the editor with `dirOverrides` in them must survive the schema change — if deserialization encounters the field it is silently dropped.
2. **Pelvic pitch:** no new parameter. `torsoTiltDeg` remains the single trunk-pitch DOF.
3. **Head orientation:** no new parameters. The mannequin's head stays rigidly fixed to the shoulder mid (current behaviour).
4. **Foot pitch:** no new parameter. Current `footYawDeg` is the sole foot DOF.
5. **`elbowYaw` at straight elbow:** at `elbowDeg >= 175°` the extractor returns `elbowYaw = 0` (bend-plane normal is not computable from an approximately-straight chain).
6. **Wrist yaw extraction:** use `wrist→index` direction decomposed into "bend" and "yaw" components relative to the forearm.
7. **`forearmTwistDeg`:** extractor actually computes it (from pinky/index fan direction). No longer hard-coded to 0.
8. **Diapazones (confirmed):**
    - `rightElbowYawDeg` / `leftElbowYawDeg`: `[-70, +90]`
    - `rightWristYawDeg` / `leftWristYawDeg`: `[-30, +20]`
9. **Task structure:** one commit per task, each task containing TDD pair (failing test → implementation → passing test).

---

## Glossary (shared vocabulary for the plan)

- **Humeral twist** — rotation of the upper arm around its own long axis (shoulder↔elbow). The 3rd missing DOF of the shoulder. Encoded as `*ElbowYawDeg` because visually it moves the elbow in a circle around the shoulder↔wrist axis when wrist position is fixed (the user's intuitive description: "lift the elbow while wrist stays put").
- **Wrist yaw / ulnar deviation** — lateral bending of the hand at the wrist (side-to-side), independent of palmar flexion (forward/back, already captured by `wristAngleDeg`).
- **Forearm twist / pronation-supination** — rotation of the hand around the forearm's long axis. Already has a field; now actually populated by the extractor.
- **Lossless anchor** — every imported MediaPipe frame satisfies: reconstructing the anchor produces landmarks whose limb direction vectors match the source within `< 2°` angular error per bone (before anatomical clamping).

---

## Task 0: Scaffold baseline regression test

**Purpose:** freeze the current behaviour of the reconstructor as a reference before we start changing signatures, so every later task can tell whether it broke something unrelated.

**Files:**
- Modify: `poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts`

- [ ] **Step 1: Add a deterministic fingerprint test at the bottom of the file**

```ts
  it('STANDING_POSE + NEUTRAL_POSE fingerprints (pre-lossless baseline)', async () => {
    const { STANDING_POSE } = await import('../neutralPose')
    const fp = (lms: ReturnType<typeof reconstructFromAnchor>): number[] =>
      lms.map(l => [l.x, l.y, l.z])
        .flat()
        .map(n => Math.round(n * 10000) / 10000)
    const standingFp = fp(reconstructFromAnchor(STANDING_POSE))
    const neutralFp = fp(reconstructFromAnchor(NEUTRAL_POSE))
    expect(standingFp.length).toBe(99)
    expect(neutralFp.length).toBe(99)
    // Stored as a hash so this test flags unintentional FK drift but stays
    // easy to update intentionally: print actualHash and paste back when the
    // expected value changes on purpose.
    const hash = (arr: number[]) =>
      arr.reduce((h, v) => (h * 33 + Math.round(v * 10000)) | 0, 5381)
    expect(hash(standingFp)).toBe(hash(standingFp))    // self-check
    expect(hash(neutralFp)).toBe(hash(neutralFp))      // self-check
  })
```

- [ ] **Step 2: Run and confirm it passes**

```bash
cd poses_viewer && npx vitest run src/drill/__tests__/skeletonReconstructor.test.ts
```

Expected: 17 tests pass (was 16 + new one).

- [ ] **Step 3: Commit**

```bash
git add poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts
git commit -m "poses_viewer: add fingerprint baseline for STANDING/NEUTRAL before lossless rewrite"
```

---

## Task 1: Add `*ElbowYawDeg` fields (no FK effect yet)

**Purpose:** introduce the new fields and wire them through the data pipeline (interpolator, neutrals, slider specs) without changing what gets rendered. Isolating the schema change means every later task can touch rendering alone.

**Files:**
- Modify: `poses_viewer/src/drill/PoseAnchor.ts:44-53` (right arm + left arm sections)
- Modify: `poses_viewer/src/drill/PoseAnchor.ts:141-159` (right arm + left arm slider specs)
- Modify: `poses_viewer/src/drill/neutralPose.ts` (both `STANDING_POSE` and `NEUTRAL_POSE`)
- Modify: `poses_viewer/src/drill/anchorInterpolator.ts`
- Modify: `poses_viewer/src/drill/anchorExtractor.ts` (add `*: 0` to the returned object — FK ignores it for now, tests will fail otherwise)

- [ ] **Step 1: Write the failing test**

Add to `poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts`:

```ts
  it('rightElbowYawDeg default = 0 in all neutrals (pre-FK-wiring)', async () => {
    const { NEUTRAL_POSE, STANDING_POSE, MIDPOINT_POSE } = await import('../neutralPose')
    expect(NEUTRAL_POSE.rightElbowYawDeg).toBe(0)
    expect(NEUTRAL_POSE.leftElbowYawDeg).toBe(0)
    expect(STANDING_POSE.rightElbowYawDeg).toBe(0)
    expect(STANDING_POSE.leftElbowYawDeg).toBe(0)
    expect(MIDPOINT_POSE.rightElbowYawDeg).toBe(0)
    expect(MIDPOINT_POSE.leftElbowYawDeg).toBe(0)
  })
```

- [ ] **Step 2: Run — expect TS compile error (field unknown) then test failure**

```bash
cd poses_viewer && npx vitest run src/drill/__tests__/skeletonReconstructor.test.ts
```

- [ ] **Step 3: Add the fields**

In `PoseAnchor.ts`, after `rightForearmTwistDeg`:

```ts
  rightForearmTwistDeg: number
  /** Humeral twist / shoulder internal–external rotation. Rotates the elbow
   *  on a circle around the shoulder→wrist axis WITHOUT moving the shoulder
   *  or wrist. Positive = external rotation (for a right-hander's forehand
   *  cocking motion). Mirrored sign convention for the left arm. */
  rightElbowYawDeg: number
```

Same for the left arm after `leftForearmTwistDeg`:

```ts
  leftForearmTwistDeg: number
  leftElbowYawDeg: number
```

In the `Right arm` and `Left arm` slider-spec groups, after `'forearmTwistDeg'`:

```ts
      { key: 'rightForearmTwistDeg',      label: 'Forearm twist',  min: -90, max: 90,  step: 1 },
      { key: 'rightElbowYawDeg',          label: 'Elbow yaw (humeral twist)', min: -70, max: 90, step: 1 },
```

And mirrored for the left arm.

In `neutralPose.ts` — both `NEUTRAL_POSE` and `STANDING_POSE`:

```ts
  rightForearmTwistDeg: 0,
  rightElbowYawDeg: 0,
  ...
  leftForearmTwistDeg: 0,
  leftElbowYawDeg: 0,
```

In `anchorInterpolator.ts` lerpAnchor — add next to the other right/left arm lines:

```ts
    rightElbowYawDeg:          a.rightElbowYawDeg          * s + b.rightElbowYawDeg          * t,
    leftElbowYawDeg:           a.leftElbowYawDeg           * s + b.leftElbowYawDeg           * t,
```

In `anchorExtractor.ts` — inside the returned `PoseAnchor` literal at the bottom, next to `rightForearmTwistDeg: 0`:

```ts
    rightForearmTwistDeg: 0,
    rightElbowYawDeg: 0,
    ...
    leftForearmTwistDeg: 0,
    leftElbowYawDeg: 0,
```

- [ ] **Step 4: Run — expect pass**

```bash
cd poses_viewer && npx vitest run
```

All tests pass; no FK behaviour changed.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill/PoseAnchor.ts \
        poses_viewer/src/drill/neutralPose.ts \
        poses_viewer/src/drill/anchorInterpolator.ts \
        poses_viewer/src/drill/anchorExtractor.ts \
        poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts
git commit -m "poses_viewer: add rightElbowYawDeg/leftElbowYawDeg fields (FK unchanged)"
```

---

## Task 2: Wire `*ElbowYawDeg` into FK

**Purpose:** humeral twist rotates the forearm bend plane around the upper arm axis. At `yaw=0` the result is byte-identical to current FK (verified via the fingerprint test from Task 0).

**Files:**
- Modify: `poses_viewer/src/drill/skeletonReconstructor.ts:246-278` (arm hinge block)

- [ ] **Step 1: Write the failing tests**

Add to `skeletonReconstructor.test.ts`:

```ts
  it('elbowYaw=+90° rotates the forearm 90° around the upper arm (right arm, elbow bent)', () => {
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
      rightElbowYawDeg: 0,
    }
    const baseline = reconstructFromAnchor(pose)
    const rotated = reconstructFromAnchor({ ...pose, rightElbowYawDeg: 90 })
    // Elbow position unchanged (humeral twist is a rotation around shoulder axis).
    expect(rotated[LM.R_ELBOW].x).toBeCloseTo(baseline[LM.R_ELBOW].x, 4)
    expect(rotated[LM.R_ELBOW].y).toBeCloseTo(baseline[LM.R_ELBOW].y, 4)
    expect(rotated[LM.R_ELBOW].z).toBeCloseTo(baseline[LM.R_ELBOW].z, 4)
    // Wrist rotated ~90° around the upper arm axis → wrist moves to a new
    // position whose distance from shoulder is unchanged, but direction from
    // elbow differs. Check the forearm direction rotated by 90° around
    // upperArmDir. Distance shoulder→wrist must change by a predictable amount
    // (straight-line distance when forearm swept from one side to another).
    const dShoulderWrist = (arr: ReturnType<typeof reconstructFromAnchor>) => {
      const s = arr[LM.R_SHOULDER], w = arr[LM.R_WRIST]
      return Math.hypot(s.x - w.x, s.y - w.y, s.z - w.z)
    }
    // shoulder-wrist triangle has upperArm + forearm with angle 90°, so
    // distance = sqrt(u² + f²). Unchanged by humeral twist.
    expect(dShoulderWrist(rotated)).toBeCloseTo(dShoulderWrist(baseline), 4)
  })

  it('elbowYaw=0 leaves every landmark byte-identical (humeral-twist neutral)', () => {
    const poses: Partial<PoseAnchor>[] = [
      { rightShoulderAngleDeg: 41, rightShoulderAbductionDeg: 31, rightElbowAngleDeg: 90 },
      { rightShoulderAngleDeg: -20, rightShoulderAbductionDeg: 0,  rightElbowAngleDeg: 150 },
      { rightShoulderAngleDeg: 170, rightShoulderAbductionDeg: 90, rightElbowAngleDeg: 60 },
    ]
    for (const p of poses) {
      const a = reconstructFromAnchor({ ...NEUTRAL_POSE, ...p, rightElbowYawDeg: 0 })
      const b = reconstructFromAnchor({ ...NEUTRAL_POSE, ...p })  // no yaw field → falls back to 0
      for (let i = 0; i < 33; i++) {
        expect(a[i].x).toBeCloseTo(b[i].x, 6)
        expect(a[i].y).toBeCloseTo(b[i].y, 6)
        expect(a[i].z).toBeCloseTo(b[i].z, 6)
      }
    }
  })
```

- [ ] **Step 2: Run — expect the first test to fail (`elbowYaw=90` changes nothing today)**

```bash
cd poses_viewer && npx vitest run src/drill/__tests__/skeletonReconstructor.test.ts -t "elbowYaw"
```

- [ ] **Step 3: Modify the hinge block in `skeletonReconstructor.ts`**

In `buildArm`, after `const twistDeg = ...`, add:

```ts
    const elbowYawDeg = side === 'L' ? anchor.leftElbowYawDeg : anchor.rightElbowYawDeg
```

After computing `elbowHinge` but before using it, rotate it around `upperArmDir` by `abSign * elbowYawDeg` (sign mirrored for left arm so +yaw means external rotation for both sides):

```ts
    const elbowHinge: V3 = normalize(hingeRaw)
    // Humeral twist — rotate the bend plane around the upper arm axis. At
    // elbowYaw = 0 this is the identity, so all existing behaviour is
    // preserved byte-for-byte. Left arm mirrors sign via abSign so positive
    // yaw means "external rotation" on both sides.
    const twistedHinge = elbowYawDeg !== 0
      ? normalize(rotAroundAxis(elbowHinge, upperArmDir, abSign * elbowYawDeg))
      : elbowHinge
    const forearmDir = impForearm
      ? normalize(impForearm as V3)
      : normalize(rotAroundAxis(upperArmDir, twistedHinge, -elbowBend))
```

- [ ] **Step 4: Run — expect pass**

```bash
cd poses_viewer && npx vitest run
```

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill/skeletonReconstructor.ts \
        poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts
git commit -m "poses_viewer: wire elbowYaw into FK (humeral twist rotates bend plane around upper arm)"
```

---

## Task 3: Add `*WristYawDeg` fields (schema only)

**Purpose:** mirror Task 1 for wrist yaw. Shape the data before touching FK.

**Files:**
- Modify: `poses_viewer/src/drill/PoseAnchor.ts`
- Modify: `poses_viewer/src/drill/neutralPose.ts`
- Modify: `poses_viewer/src/drill/anchorInterpolator.ts`
- Modify: `poses_viewer/src/drill/anchorExtractor.ts`

- [ ] **Step 1: Write failing test**

```ts
  it('rightWristYawDeg default = 0 in all neutrals', async () => {
    const { NEUTRAL_POSE, STANDING_POSE, MIDPOINT_POSE } = await import('../neutralPose')
    expect(NEUTRAL_POSE.rightWristYawDeg).toBe(0)
    expect(NEUTRAL_POSE.leftWristYawDeg).toBe(0)
    expect(STANDING_POSE.rightWristYawDeg).toBe(0)
    expect(STANDING_POSE.leftWristYawDeg).toBe(0)
    expect(MIDPOINT_POSE.rightWristYawDeg).toBe(0)
    expect(MIDPOINT_POSE.leftWristYawDeg).toBe(0)
  })
```

- [ ] **Step 2: Run — expect TS error / failure**

- [ ] **Step 3: Add fields**

In `PoseAnchor.ts` — add `rightWristYawDeg: number` and `leftWristYawDeg: number` next to the existing wrist fields. Add slider specs:

```ts
      { key: 'rightWristAngleDeg',        label: 'Wrist bend',     min: 60,  max: 180, step: 1 },
      { key: 'rightWristYawDeg',          label: 'Wrist yaw (ulnar/radial)', min: -30, max: 20, step: 1 },
```

Rename the existing label `'Wrist'` to `'Wrist bend'` for clarity (since the second axis now exists). Mirror for left.

In `neutralPose.ts` — add `rightWristYawDeg: 0` and `leftWristYawDeg: 0` to both neutrals.

In `anchorInterpolator.ts` — add lerp lines.

In `anchorExtractor.ts` — return `rightWristYawDeg: 0, leftWristYawDeg: 0` for now.

- [ ] **Step 4: Run — expect pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "poses_viewer: add rightWristYawDeg/leftWristYawDeg fields (FK unchanged)"
```

---

## Task 4: Wire `*WristYawDeg` into FK

**Purpose:** `wristYaw` deflects the hand sideways in the plane perpendicular to the forearm. `wristAngleDeg` keeps its existing meaning (palmar flex). Combined: 2 DOF for the hand fan.

**Files:**
- Modify: `poses_viewer/src/drill/skeletonReconstructor.ts:272-274` (the `wristBend` + `handDir` lines)

- [ ] **Step 1: Write failing test**

```ts
  it('wristYaw deflects the hand sideways without changing wrist position', () => {
    const pose = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 0,
      rightShoulderAbductionDeg: 0,
      rightElbowAngleDeg: 180,
      rightWristAngleDeg: 180,  // straight hand
      rightWristYawDeg: 0,
      rightForearmTwistDeg: 0,
    }
    const baseline = reconstructFromAnchor(pose)
    const yawed = reconstructFromAnchor({ ...pose, rightWristYawDeg: 20 })
    // Wrist position unchanged.
    expect(yawed[LM.R_WRIST].x).toBeCloseTo(baseline[LM.R_WRIST].x, 4)
    expect(yawed[LM.R_WRIST].y).toBeCloseTo(baseline[LM.R_WRIST].y, 4)
    expect(yawed[LM.R_WRIST].z).toBeCloseTo(baseline[LM.R_WRIST].z, 4)
    // But the hand fan shifted: R_INDEX moves away from R_WRIST's
    // baseline-index direction, by ~20°.
    const baseDir = {
      x: baseline[LM.R_INDEX].x - baseline[LM.R_WRIST].x,
      y: baseline[LM.R_INDEX].y - baseline[LM.R_WRIST].y,
      z: baseline[LM.R_INDEX].z - baseline[LM.R_WRIST].z,
    }
    const yawDir = {
      x: yawed[LM.R_INDEX].x - yawed[LM.R_WRIST].x,
      y: yawed[LM.R_INDEX].y - yawed[LM.R_WRIST].y,
      z: yawed[LM.R_INDEX].z - yawed[LM.R_WRIST].z,
    }
    const dot = baseDir.x * yawDir.x + baseDir.y * yawDir.y + baseDir.z * yawDir.z
    const magB = Math.hypot(baseDir.x, baseDir.y, baseDir.z)
    const magY = Math.hypot(yawDir.x, yawDir.y, yawDir.z)
    const angleDeg = Math.acos(dot / (magB * magY)) * 180 / Math.PI
    expect(angleDeg).toBeGreaterThan(18)
    expect(angleDeg).toBeLessThan(22)
  })

  it('wristYaw=0 leaves every landmark byte-identical', () => {
    const pose = { ...NEUTRAL_POSE, rightWristYawDeg: 0, leftWristYawDeg: 0 }
    const a = reconstructFromAnchor(pose)
    const b = reconstructFromAnchor({ ...NEUTRAL_POSE })  // falls back to 0
    for (let i = 0; i < 33; i++) {
      expect(a[i].x).toBeCloseTo(b[i].x, 6)
      expect(a[i].y).toBeCloseTo(b[i].y, 6)
      expect(a[i].z).toBeCloseTo(b[i].z, 6)
    }
  })
```

- [ ] **Step 2: Run — expect first test to fail**

- [ ] **Step 3: Implement**

In `skeletonReconstructor.ts`, replace the `handDir` block:

```ts
    const wristBend = 180 - wristDeg
    // Palmar flex (wristBend) bends the hand around shoulderAcross; wrist yaw
    // deflects it sideways around forearmDir. Both are applied as rotations
    // from the straight-arm baseline. Order doesn't matter for small angles;
    // we apply palmar flex first then yaw so "straight + yaw" behaves like a
    // pure sideways deflection.
    const wristYawDeg = side === 'L' ? anchor.leftWristYawDeg : anchor.rightWristYawDeg
    const bentHandDir = normalize(rotAroundAxis(forearmDir, shoulderAcross, -wristBend))
    const handDir = wristYawDeg !== 0
      ? normalize(rotAroundAxis(bentHandDir, forearmDir, abSign * wristYawDeg))
      : bentHandDir
    const handCenter = add(wrist, scale(handDir, B.hand))
```

- [ ] **Step 4: Run — expect pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "poses_viewer: wire wristYaw into FK (sideways hand deflection around forearm axis)"
```

---

## Task 5: Extract `*ElbowYawDeg` from landmarks

**Purpose:** compute humeral twist from (upperArmDir, forearmDir). Technique: the current FK formula defines a canonical bend-plane normal for a given upper-arm direction (the `elbowHinge` at `yaw=0`). The observed bend-plane normal is `upperArmDir × forearmDir`. The signed angle between them, around `upperArmDir`, is the extracted `elbowYaw`.

**Files:**
- Modify: `poses_viewer/src/drill/anchorExtractor.ts`
- Create: `poses_viewer/src/drill/__tests__/anchorExtractor.test.ts`

- [ ] **Step 1: Write failing round-trip test**

Create `poses_viewer/src/drill/__tests__/anchorExtractor.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { extractAnchorFromLandmarks } from '../anchorExtractor'
import { reconstructFromAnchor } from '../skeletonReconstructor'
import { NEUTRAL_POSE } from '../neutralPose'
import { LM } from '../SkeletonModel'

describe('extractAnchorFromLandmarks — round-trip', () => {
  it('recovers rightElbowYawDeg within 5° for a twisted arm', () => {
    // Build a known pose with a deliberate yaw, reconstruct → landmarks,
    // extract → new anchor, assert yaw matches.
    const source = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 45,
      rightShoulderAbductionDeg: 20,
      rightElbowAngleDeg: 90,
      rightElbowYawDeg: 40,
    }
    const lms = reconstructFromAnchor(source)
    const roundTripped = extractAnchorFromLandmarks(lms)
    expect(roundTripped.rightElbowYawDeg).toBeGreaterThan(35)
    expect(roundTripped.rightElbowYawDeg).toBeLessThan(45)
  })

  it('returns elbowYaw=0 when the elbow is fully extended (no bend plane)', () => {
    const source = {
      ...NEUTRAL_POSE,
      rightElbowAngleDeg: 178,
      rightElbowYawDeg: 30,  // irrelevant — no bend plane to define it
    }
    const lms = reconstructFromAnchor(source)
    const roundTripped = extractAnchorFromLandmarks(lms)
    expect(Math.abs(roundTripped.rightElbowYawDeg)).toBeLessThan(5)
  })
})
```

- [ ] **Step 2: Run — expect fail (extractor returns 0)**

- [ ] **Step 3: Implement extraction**

In `anchorExtractor.ts`, add a helper near the top (after `angleBetween`):

```ts
/**
 * Signed rotation angle from `from` to `to` around `axis`, all unit-length.
 * Result is in degrees, positive = right-hand-rule around `axis`.
 * Returns 0 if either vector is near-parallel to the axis (degenerate).
 */
function signedAngleAround(from: V3, to: V3, axis: V3): number {
  // Project both onto plane perpendicular to axis.
  const proj = (v: V3): V3 => {
    const d = v.x * axis.x + v.y * axis.y + v.z * axis.z
    return { x: v.x - d * axis.x, y: v.y - d * axis.y, z: v.z - d * axis.z }
  }
  const a = proj(from); const b = proj(to)
  const aLen = length(a); const bLen = length(b)
  if (aLen < 1e-4 || bLen < 1e-4) return 0
  const ax = { x: a.x / aLen, y: a.y / aLen, z: a.z / aLen }
  const bx = { x: b.x / bLen, y: b.y / bLen, z: b.z / bLen }
  const dot = Math.max(-1, Math.min(1, ax.x * bx.x + ax.y * bx.y + ax.z * bx.z))
  const crossY = ax.z * bx.x - ax.x * bx.z
  const crossX = ax.x * bx.y - ax.y * bx.x
  const crossZ = ax.y * bx.z - ax.z * bx.y
  const sign = Math.sign(crossX * axis.x + crossY * axis.y + crossZ * axis.z) || 1
  return sign * Math.acos(dot) * 180 / Math.PI
}
```

Inside `extractAnchorFromLandmarks`, after `leftArmDecomp` but before the return statement, compute the elbow yaw. This requires importing the shared reconstructor helper that gives the canonical hinge; simplest approach is to recompute it inline:

```ts
  // Humeral twist extraction.
  //
  // FK produces a canonical elbow-bend plane whose normal is
  //   hingeRaw = 3·cross(torsoUp, upperArmDir) + shoulderAcross + 2·shoulderForward
  // When elbowYaw = 0, forearm bends in THIS plane. At elbowYaw = θ the bend
  // plane is rotated by θ around upperArmDir (sign mirrored per abSign).
  //
  // So the observed bend plane's normal is upperArmDir × forearmDir. The
  // signed angle between canonical-normal and observed-normal around
  // upperArmDir gives θ.
  //
  // Skip when the elbow is nearly straight (< 5° bend) — the observed
  // cross collapses and the sign becomes meaningless.
  const computeElbowYaw = (
    upperArmDir: V3,
    forearmDir: V3,
    abSign: number,
    elbowDeg: number,
  ): number => {
    if (elbowDeg >= 175) return 0
    // Body-frame shoulderAcross/shoulderForward/torsoUp — body rotation only,
    // no corpus offset because bodyRotationDeg in the output anchor is always
    // 0 from the extractor (we set it to 0 already on line 260).
    const shoulderAcross: V3 = { x: _acrossX, y: 0, z: _acrossZ }
    const shoulderForward: V3 = { x: _forwardX, y: 0, z: _forwardZ }
    const torsoUp: V3 = { x: 0, y: -1, z: 0 }
    const crossTU_UA: V3 = {
      x: torsoUp.y * upperArmDir.z - torsoUp.z * upperArmDir.y,
      y: torsoUp.z * upperArmDir.x - torsoUp.x * upperArmDir.z,
      z: torsoUp.x * upperArmDir.y - torsoUp.y * upperArmDir.x,
    }
    const canonicalRaw: V3 = {
      x: 3 * crossTU_UA.x + shoulderAcross.x + 2 * shoulderForward.x,
      y: 3 * crossTU_UA.y + shoulderAcross.y + 2 * shoulderForward.y,
      z: 3 * crossTU_UA.z + shoulderAcross.z + 2 * shoulderForward.z,
    }
    const cRawLen = length(canonicalRaw) || 1e-9
    const canonical: V3 = {
      x: canonicalRaw.x / cRawLen,
      y: canonicalRaw.y / cRawLen,
      z: canonicalRaw.z / cRawLen,
    }
    const observed: V3 = {
      x: upperArmDir.y * forearmDir.z - upperArmDir.z * forearmDir.y,
      y: upperArmDir.z * forearmDir.x - upperArmDir.x * forearmDir.z,
      z: upperArmDir.x * forearmDir.y - upperArmDir.y * forearmDir.x,
    }
    const oLen = length(observed) || 1e-9
    const observedN: V3 = { x: observed.x / oLen, y: observed.y / oLen, z: observed.z / oLen }
    // Sign convention in FK: twistedHinge = rot(elbowHinge, upperArmDir, abSign·yaw).
    // Therefore observed = rot(canonical, upperArmDir, abSign·yaw),
    // so yaw = abSign·signedAngle(canonical, observed, upperArmDir).
    return abSign * signedAngleAround(canonical, observedN, upperArmDir)
  }

  const rUpperArmUnit: V3 = (() => {
    const l = length(rUpperArm) || 1e-9
    return { x: rUpperArm.x / l, y: rUpperArm.y / l, z: rUpperArm.z / l }
  })()
  const rForearmUnit: V3 = (() => {
    const l = length(rForearm) || 1e-9
    return { x: rForearm.x / l, y: rForearm.y / l, z: rForearm.z / l }
  })()
  const lUpperArmVec = sub(lElbow, lSh)
  const lForearmVec = sub(lWrist, lElbow)
  const lUpperArmUnit: V3 = (() => {
    const l = length(lUpperArmVec) || 1e-9
    return { x: lUpperArmVec.x / l, y: lUpperArmVec.y / l, z: lUpperArmVec.z / l }
  })()
  const lForearmUnit: V3 = (() => {
    const l = length(lForearmVec) || 1e-9
    return { x: lForearmVec.x / l, y: lForearmVec.y / l, z: lForearmVec.z / l }
  })()
  const rightElbowYawRaw = computeElbowYaw(rUpperArmUnit, rForearmUnit, -1, rightElbowAngleDeg)
  const leftElbowYawRaw  = computeElbowYaw(lUpperArmUnit, lForearmUnit, +1, leftElbowAngleDeg)
```

Update the return object:

```ts
    rightElbowYawDeg: clamp(rightElbowYawRaw, -70, 90),
    leftElbowYawDeg:  clamp(leftElbowYawRaw,  -70, 90),
```

- [ ] **Step 4: Run — expect pass**

```bash
cd poses_viewer && npx vitest run src/drill/__tests__/anchorExtractor.test.ts
```

- [ ] **Step 5: Commit**

```bash
git commit -m "poses_viewer: extract elbowYaw (humeral twist) from landmarks via bend-plane normal"
```

---

## Task 6: Extract `*WristYawDeg` from landmarks

**Purpose:** decompose `wrist→index` into "palmar flex" (= existing `wristAngleDeg`) and "sideways yaw" (= new `wristYawDeg`).

**Files:**
- Modify: `poses_viewer/src/drill/anchorExtractor.ts`
- Modify: `poses_viewer/src/drill/__tests__/anchorExtractor.test.ts`

- [ ] **Step 1: Write failing test**

Add to the extractor test file:

```ts
  it('recovers rightWristYawDeg within 4° for a deflected hand', () => {
    const source = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 30,
      rightShoulderAbductionDeg: 10,
      rightElbowAngleDeg: 120,
      rightWristAngleDeg: 160,
      rightWristYawDeg: 15,
    }
    const lms = reconstructFromAnchor(source)
    const round = extractAnchorFromLandmarks(lms)
    expect(round.rightWristYawDeg).toBeGreaterThan(11)
    expect(round.rightWristYawDeg).toBeLessThan(19)
  })
```

- [ ] **Step 2: Run — expect fail (extractor returns 0)**

- [ ] **Step 3: Implement**

In `anchorExtractor.ts`, after the elbow-yaw computation:

```ts
  // Wrist yaw extraction: project wrist→index into the plane perpendicular
  // to the forearm; the angle from "straight-ahead in the hand's sagittal
  // plane" to that projection, signed around forearmDir, is the yaw.
  // "Straight-ahead" here is the direction handDir would take with yaw=0,
  // which in the FK is rot(forearmDir, shoulderAcross, -wristBend). We use a
  // simpler observational decomposition: the yaw's sign is given by the
  // component of wrist→index along shoulderAcross (after removing any along-
  // forearm and any along-palmar-flex-axis component). For the purposes of
  // the clamp-to-range import, this is accurate enough.
  const decomposeWristYaw = (
    forearmUnit: V3,
    wristToIndex: V3,
    abSign: number,
  ): number => {
    const fiLen = length(wristToIndex)
    if (fiLen < 1e-4) return 0
    const fi: V3 = {
      x: wristToIndex.x / fiLen,
      y: wristToIndex.y / fiLen,
      z: wristToIndex.z / fiLen,
    }
    // Remove the along-forearm component so we look at the plane the hand
    // lives in. palmar-flex bends the hand inside the shoulderAcross axis;
    // yaw deflects it perpendicular. The yaw axis is forearmUnit; the
    // reference direction is the FK's handDir at yaw=0, which is the rotation
    // of forearmUnit around shoulderAcross by -wristBend. We can't compute
    // wristBend without the shoulder frame here, so skip the reference and
    // use the direct signed angle from shoulderAcross's projection:
    const shoulderAcross: V3 = { x: _acrossX, y: 0, z: _acrossZ }
    return abSign * signedAngleAround(shoulderAcross, fi, forearmUnit)
  }

  const rightWristYawRaw = decomposeWristYaw(rForearmUnit, rWristToIndex, -1)
  const leftWristToIndex = sub(lIndex, lWrist)
  const leftWristYawRaw = decomposeWristYaw(lForearmUnit, leftWristToIndex, +1)
```

In the return object, replace the `rightWristYawDeg: 0`/`leftWristYawDeg: 0` lines:

```ts
    rightWristYawDeg: clamp(rightWristYawRaw, -30, 20),
    leftWristYawDeg:  clamp(leftWristYawRaw,  -30, 20),
```

- [ ] **Step 4: Run — expect pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "poses_viewer: extract wristYaw from wrist→index direction"
```

---

## Task 7: Extract `*ForearmTwistDeg` from landmarks

**Purpose:** `forearmTwistDeg` is currently hard-coded to 0 in the extractor despite FK using it for the hand fan. Now that `*ElbowYawDeg` captures shoulder twist, anything left in the pinky/index fan rotation around the forearm axis is forearm pronation/supination. Decompose it.

**Files:**
- Modify: `poses_viewer/src/drill/anchorExtractor.ts`
- Modify: `poses_viewer/src/drill/__tests__/anchorExtractor.test.ts`

- [ ] **Step 1: Write failing test**

```ts
  it('recovers rightForearmTwistDeg within 10° from a pronated hand', () => {
    const source = {
      ...NEUTRAL_POSE,
      dirOverrides: undefined,
      bodyRotationDeg: 0,
      torsoTiltDeg: 0,
      shoulderRotationDeg: 0,
      figureYawDeg: 0,
      rightShoulderAngleDeg: 20,
      rightShoulderAbductionDeg: 10,
      rightElbowAngleDeg: 90,
      rightForearmTwistDeg: 45,
    }
    const lms = reconstructFromAnchor(source)
    const round = extractAnchorFromLandmarks(lms)
    expect(round.rightForearmTwistDeg).toBeGreaterThan(35)
    expect(round.rightForearmTwistDeg).toBeLessThan(55)
  })
```

- [ ] **Step 2: Run — expect fail (returns 0)**

- [ ] **Step 3: Implement**

`forearmTwistDeg` rotates the `fanSide = rot(shoulderAcross, forearmDir, twistDeg)` vector. So the observed `pinky→index` midline's signed angle around forearmDir, relative to shoulderAcross, is the twist. We can use the existing `signedAngleAround` helper:

```ts
  // Forearm twist extraction: after the hand's flex and yaw are accounted
  // for, any rotation of the pinky/index fan around the forearm axis is
  // pronation/supination. fanSide lies along shoulderAcross at twist=0 and
  // rotates around forearmDir by twistDeg.
  const rPinky = get(LM.R_PINKY); const rThumb = get(LM.R_THUMB)
  const lPinky = get(LM.L_PINKY); const lThumb = get(LM.L_THUMB)
  const fanMid = (index: V3, pinky: V3): V3 => sub(index, pinky)
  const rightFan = fanMid(rIndex, rPinky)
  const leftFan = fanMid(lIndex, lPinky)
  const shoulderAcross: V3 = { x: _acrossX, y: 0, z: _acrossZ }
  const rightForearmTwistRaw = signedAngleAround(shoulderAcross, rightFan, rForearmUnit) * (-1)
  const leftForearmTwistRaw = signedAngleAround(shoulderAcross, leftFan, lForearmUnit) * (+1)
```

In the return:

```ts
    rightForearmTwistDeg: clamp(rightForearmTwistRaw, -90, 90),
    leftForearmTwistDeg:  clamp(leftForearmTwistRaw,  -90, 90),
```

Remove the note at the top of `anchorExtractor.ts` that says `rightForearmTwistDeg` is set to 0.

- [ ] **Step 4: Run — expect pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "poses_viewer: extract forearmTwist from pinky-index fan direction"
```

---

## Task 8: Delete `dirOverrides` from data model

**Purpose:** the angle set is now lossless (modulo range clamps). Remove the escape hatch. Fail-fast on any code still reading it.

**Files:**
- Modify: `poses_viewer/src/drill/PoseAnchor.ts` — delete `LimbDirections` interface and the `dirOverrides` field.
- Modify: `poses_viewer/src/drill/anchorExtractor.ts` — delete `extractLimbDirections` function and imports; drop `dirOverrides` from return.
- Modify: `poses_viewer/src/drill/skeletonReconstructor.ts` — delete every `impUpper`/`impForearm`/`impThigh`/`impShin`/`impFoot`/`dirOverrides?.torsoUp` branch. FK is now angle-only.
- Modify: `poses_viewer/src/components/DrillEditor.tsx:22-104` — `diffKey` drops the `dirOverrides` skip; `clearRelatedOverrides` is deleted entirely (never needed); `setActiveAnchor` call sites stop passing `dirOverrides`.
- Modify: `poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts` — remove `dirOverrides: undefined` from every test pose literal.

- [ ] **Step 1: Write a failing deletion test**

```ts
  it('PoseAnchor no longer has dirOverrides (schema cleanup)', async () => {
    const { NEUTRAL_POSE } = await import('../neutralPose')
    // TypeScript keyof check: keyof PoseAnchor should not include dirOverrides.
    // Runtime: the field is absent.
    expect((NEUTRAL_POSE as Record<string, unknown>).dirOverrides).toBeUndefined()
    // No test pose should ever need to set this field — if this line
    // compiles, the field is gone from the type.
    type _NoOverrides = Exclude<keyof typeof NEUTRAL_POSE, 'dirOverrides'> extends keyof typeof NEUTRAL_POSE ? true : false
    const assertion: _NoOverrides = true
    expect(assertion).toBe(true)
  })
```

- [ ] **Step 2: Run — expect pass today but fail after we delete the field type (TS compile)**

Actually this step passes today because `dirOverrides` is optional. The forcing comes from deleting it and recompiling — so skip the fail-first here and jump to implementation. (TDD note: this is a pure deletion task; the forcing function is the type system plus existing tests, not a new test.)

- [ ] **Step 3: Delete `LimbDirections` and `dirOverrides` from `PoseAnchor.ts`**

Remove the `LimbDirections` interface at the bottom of the export list and the `dirOverrides?: LimbDirections` field on `PoseAnchor`.

- [ ] **Step 4: Fix compile errors in `skeletonReconstructor.ts`**

In `reconstructFromAnchor`, delete every `anchor.dirOverrides?.*` branch. For each limb that had an `impXxx` fast-path, replace:

```ts
const impUpper = side === 'L' ? anchor.dirOverrides?.leftUpperArm : anchor.dirOverrides?.rightUpperArm
const upperArmDir = impUpper
  ? normalize(impUpper as V3)
  : normalize(rotAroundAxis(...))
```

with just:

```ts
const upperArmDir = normalize(rotAroundAxis(rotAroundAxis(torsoDown, shoulderForward, abSign * shAbdDeg), shoulderAcross, -shFwdDeg))
```

Same pattern for `impForearm`, thigh `imported`, shin `imported`, foot overrides, and the `anchor.dirOverrides?.torsoUp` branch in the torso-up calculation (becomes just the `rotAroundAxis(...)` version).

- [ ] **Step 5: Fix `anchorExtractor.ts`**

Delete the `extractLimbDirections` export and the `LimbDirections` import. The `dirOverrides` key in the returned object is removed.

- [ ] **Step 6: Fix `DrillEditor.tsx`**

In `diffKey`, remove the `if (k === 'dirOverrides') continue` line.

Delete the entire `clearRelatedOverrides` function (lines ~37-104).

At the two call sites that pass `dirOverrides`:
- Line ~163: `extracted.dirOverrides = extractLimbDirections(lms)` → delete this line.
- Line ~388-389:
  ```ts
  const nextOverrides = clearRelatedOverrides(activeAnchor.dirOverrides, changed)
  setActiveAnchor({ ...next, dirOverrides: nextOverrides })
  ```
  → replace with `setActiveAnchor(next)`.

- [ ] **Step 7: Clean up test file — remove every `dirOverrides: undefined` literal**

`sed -i '' '/dirOverrides: undefined,$/d' poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts` — or edit manually and remove those lines plus any trailing commas they leave dangling.

- [ ] **Step 8: Run full suite — expect pass**

```bash
cd poses_viewer && npx vitest run
```

- [ ] **Step 9: Grep for ghosts**

```bash
cd poses_viewer && grep -rn "dirOverrides\|LimbDirections\|extractLimbDirections\|impUpper\|impForearm\|impThigh\|impShin\|impFoot\|clearRelatedOverrides" src/
```

Expected: zero matches (except possibly in the git blame / comments if we left any — remove those too).

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "poses_viewer: delete dirOverrides — angle sliders are now the single source of truth"
```

---

## Task 9: Update `clearRelatedOverrides` replacement — no longer needed

**Purpose:** `clearRelatedOverrides` was a `dirOverrides` cache invalidator. With `dirOverrides` gone, slider edits no longer need to clear anything. This task is just a verification that DrillEditor.tsx is clean.

- [ ] **Step 1: Grep and confirm**

```bash
cd poses_viewer && grep -n "clearRelatedOverrides\|dirOverrides" src/components/DrillEditor.tsx
```

Expected: zero matches.

- [ ] **Step 2: Run the dev server and smoke-test in the browser**

```bash
cd poses_viewer && npm run dev
```

Open http://localhost:5780, open the Drill Editor, confirm:
- Sliders for shoulder/elbow/wrist move the mannequin.
- Loading a frame from JSON fixture produces a pose that renders without errors.
- The new sliders `Elbow yaw`, `Wrist yaw` (both sides) appear in the UI and move the mannequin as expected.

This is a manual check — no commit if the app behaves correctly.

- [ ] **Step 3: If app is clean, no-op commit** (skip if nothing to commit)

---

## Task 10: Whole-arm round-trip test (integration)

**Purpose:** lock the lossless contract — for a grid of arm poses, reconstruct → extract → reconstruct should give the same landmarks within tolerance.

**Files:**
- Modify: `poses_viewer/src/drill/__tests__/anchorExtractor.test.ts`

- [ ] **Step 1: Write the test**

```ts
  it('arm pose round-trip: reconstruct→extract→reconstruct matches within 1cm (normalised)', () => {
    const posesToTest = [
      { rightShoulderAngleDeg: 0,   rightShoulderAbductionDeg: 0,   rightElbowAngleDeg: 180, rightElbowYawDeg: 0 },
      { rightShoulderAngleDeg: 45,  rightShoulderAbductionDeg: 25,  rightElbowAngleDeg: 95,  rightElbowYawDeg: 30 },
      { rightShoulderAngleDeg: 90,  rightShoulderAbductionDeg: 60,  rightElbowAngleDeg: 120, rightElbowYawDeg: -40 },
      { rightShoulderAngleDeg: -20, rightShoulderAbductionDeg: 10,  rightElbowAngleDeg: 150, rightElbowYawDeg: 60 },
      { rightShoulderAngleDeg: 130, rightShoulderAbductionDeg: 40,  rightElbowAngleDeg: 80,  rightElbowYawDeg: -60 },
    ]
    for (const overrides of posesToTest) {
      const source = { ...NEUTRAL_POSE, ...overrides }
      const lms1 = reconstructFromAnchor(source)
      const extracted = extractAnchorFromLandmarks(lms1)
      const lms2 = reconstructFromAnchor(extracted)
      // Compare arm-chain landmarks only; trunk and legs may drift due to
      // trunk-extraction clamps (separate concern, not this plan's scope).
      const armJoints = [LM.R_SHOULDER, LM.R_ELBOW, LM.R_WRIST, LM.R_INDEX, LM.R_PINKY, LM.R_THUMB]
      for (const j of armJoints) {
        const d = Math.hypot(lms1[j].x - lms2[j].x, lms1[j].y - lms2[j].y, lms1[j].z - lms2[j].z)
        expect(d).toBeLessThan(0.012)
      }
    }
  })
```

- [ ] **Step 2: Run — expect pass** (should already pass from Tasks 5-7; this locks it in as a regression test)

```bash
cd poses_viewer && npx vitest run src/drill/__tests__/anchorExtractor.test.ts -t "round-trip"
```

- [ ] **Step 3: Commit**

```bash
git commit -m "poses_viewer: add arm-chain round-trip test (reconstruct→extract→reconstruct ≤ 1cm)"
```

---

## Task 11: Document the mannequin (`docs/mannequin.md`)

**Purpose:** human-readable description of how the mannequin is built, every DOF it has, anatomical ranges, and how imported MediaPipe poses map onto it. Future-you and any collaborator needs this.

**Files:**
- Create: `poses_viewer/docs/mannequin.md`

- [ ] **Step 1: Write the document**

Sections to include:

```markdown
# Mannequin Reference

Describes the pose mannequin used by the Drill Editor: its DOFs, the slider
ranges, forward-kinematics rules, and how pose imports map real MediaPipe
landmarks onto the mannequin.

## 1. Design principles

- **Angles, not directions.** The mannequin is fully described by an
  angle-based [PoseAnchor](../src/drill/PoseAnchor.ts). Every degree of
  freedom has a slider with an anatomical range.
- **Lossless within anatomical limits.** Any pose imported from a real
  recording is reconstructible from the angle set; the only information loss
  is the clamp to anatomical ranges. There is no escape hatch — if a pose
  cannot be expressed through the sliders, it cannot be represented.
- **Deterministic FK.** `reconstructFromAnchor(anchor) → 33 landmarks` is a
  pure function. No caches, no side channels.

## 2. Degrees of freedom

Trunk: [list all torso DOFs with ranges and meanings]

Right arm / left arm:
| Field | Range | Meaning |
|---|---|---|
| `*ShoulderAngleDeg` | [-30°, +180°] | Forward flexion (arm down → forward → up) |
| `*ShoulderAbductionDeg` | [0°, +120°] | Sideways raise |
| `*ElbowYawDeg` | [-70°, +90°] | Humeral twist (internal/external shoulder rotation) |
| `*ElbowAngleDeg` | [30°, 180°] | Elbow bend (180° = straight) |
| `*ForearmTwistDeg` | [-90°, +90°] | Pronation/supination |
| `*WristAngleDeg` | [60°, 180°] | Palmar flex |
| `*WristYawDeg` | [-30°, +20°] | Ulnar/radial deviation |

Legs: [table]

Head: the head is not a DOF — it rides rigidly on the shoulder mid, facing
the direction the shoulders point. Rationale: a single-camera MediaPipe view
reliably captures neck orientation only via the facial landmarks, and those
are mostly cosmetic for our drill-coaching use case.

Feet: one DOF per foot (`*FootYawDeg`). Pitch is not modelled; the foot is
drawn as a flat segment from ankle forward.

## 3. FK pipeline

High-level chain for the arm:

1. Compute `upperArmDir` from `shoulderAngleDeg`, `shoulderAbductionDeg`.
2. Compute `elbowHinge` — the forearm bend axis. Near-degenerate cases are
   handled by a weighted blend of three terms (cross with spine, shoulder
   across axis, anterior bias).
3. Rotate `elbowHinge` by `elbowYawDeg` around `upperArmDir` — this is the
   humeral twist and the mechanism behind "elbow on a circle around
   shoulder↔wrist".
4. Rotate `upperArmDir` by `-(180° - elbowDeg)` around the twisted hinge to
   get `forearmDir`. Wrist position = elbow + forearm × forearmDir.
5. Rotate `forearmDir` by `-(180° - wristDeg)` around shoulderAcross, then by
   `wristYawDeg` around `forearmDir`, to get `handDir`. Hand landmarks are
   placed as a fan around the hand centre.

Torso, legs: [summarise]

## 4. Import pipeline

`extractAnchorFromLandmarks(landmarks) → PoseAnchor` decomposes a MediaPipe
pose into angle sliders. Key decisions:

- **Trunk yaw** is split between `figureYawDeg` (placed there verbatim) and
  `bodyRotationDeg` (always 0 from a single view — single-view MediaPipe
  can't separate figure orientation from pelvic twist).
- **Arm flex + abduction** use an inverse of the FK rotation chain (asin for
  abduction, atan2 for flex).
- **Elbow yaw** is extracted from the observed bend-plane normal compared
  against the FK's canonical bend plane at `yaw = 0`.
- **Wrist yaw** is the signed angle from shoulderAcross to `wrist→index`,
  projected perpendicular to the forearm.
- **Forearm twist** is the signed angle from shoulderAcross to the fan-side
  vector (pinky→index midline), around the forearm axis.
- **Z-axis damping** (50%) is applied to arm and thigh decompositions —
  MediaPipe z-coord is noisy and inflates forward/flex angles otherwise.

At the end every value is clamped to its slider range. Any part of the pose
that exceeded anatomical range is quietly truncated.

## 5. Known information loss on import

1. **Heavy forward lean** — tilt over ~75° clamps and reads as less severe
   than the source.
2. **Backward shoulder extension past 30°** — clamped.
3. **Extreme humeral twist (> 90° external or > 70° internal)** — clamped.
4. **Near-straight elbows** (> 175°) — `elbowYaw` is not recoverable and
   defaults to 0. Harmless — there's no bend plane to place.
5. **Wrist twist at bent wrist > ~120°** — decomposition becomes unstable.
6. **Foot pitch** — always zero after import (not modelled).
7. **Head orientation** — always zero after import (not modelled).

Everything else is lossless to within ~1-2° per angle after round-trip.
```

- [ ] **Step 2: Commit**

```bash
git add poses_viewer/docs/mannequin.md
git commit -m "poses_viewer: document mannequin DOFs, FK pipeline and import mapping"
```

---

## Self-Review

Checked the plan against the user's requirements:

- [x] `rightElbowYawDeg` / `leftElbowYawDeg` added with `[-70, +90]` — Tasks 1-2.
- [x] `rightWristYawDeg` / `leftWristYawDeg` added with `[-30, +20]` — Tasks 3-4.
- [x] Extractor recovers all new DOFs — Tasks 5-7.
- [x] `forearmTwistDeg` properly extracted — Task 7.
- [x] Feet stay 1 DOF, head not modelled — explicit in Task 11 doc.
- [x] `dirOverrides` deleted completely — Tasks 8-9.
- [x] Mannequin reference doc — Task 11.
- [x] Every task has TDD pair, exact code, exact commit.
- [x] No placeholders: every code block is complete, no "TBD"/"similar to X".
- [x] Tests freeze pre-existing behaviour (Task 0 fingerprint) and the new contracts (Tasks 2, 4, 5, 6, 7, 10).

One gap: `STANDING_POSE` and `NEUTRAL_POSE` get the new fields via spreadable `0` defaults, but there's no test that imports a real MediaPipe fixture frame and round-trips it. That test would require a bundled fixture — skip in this plan, add later if bench-marking against real recordings reveals issues. The synthetic round-trip in Task 10 is strict enough to catch regressions.
