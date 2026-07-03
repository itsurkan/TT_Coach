# Anthropomorphic joint-range and elbow-hinge fixes

**Date:** 2026-04-23
**Scope:** `poses_viewer/` (React/Vite mannequin editor)
**Status:** Design — awaiting user review

## Problem

Three interacting issues surfaced while shaping poses in the Mannequin Editor:

1. **Knee yaw range is too permissive.** `leftKneeYawDeg` / `rightKneeYawDeg` accept `[-90, 90]`, letting the bent leg swing a full quarter-turn outward — anatomically impossible.
2. **Shoulder abduction can't reach overhead.** `*ShoulderAbductionDeg` is capped at 62° so the arm never rises above shoulder height. Earlier the cap was chosen so the midpoint (used as Reset target) landed on 31°, conflating slider reach with Reset pose.
3. **Elbow bend rotates the forearm unnaturally.** `*ElbowAngleDeg` hinges the forearm around `shoulderAcross` (a world shoulder-line axis). When the upper arm is raised/abducted, the forearm bends sideways instead of toward the face — reads as a rotation, not a bend.

The shoulder-abduction issue tangles with a design decision from the previous session: we narrowed `max` values so `(min+max)/2` would land on desired Reset defaults. That removed reachable pose space. We need to separate **slider range** from **Reset target**.

## Design

### 1. Decouple Reset target from slider range

Add an optional `defaultValue?: number` to `AnchorParamSpec` in [src/drill/PoseAnchor.ts](../../../src/drill/PoseAnchor.ts).

```ts
export interface AnchorParamSpec {
  key: keyof PoseAnchor
  label: string
  min: number
  max: number
  step: number
  /** Reset/MIDPOINT_POSE target. When omitted, uses (min+max)/2. */
  defaultValue?: number
}
```

`buildMidpointPose` in [src/drill/neutralPose.ts](../../../src/drill/neutralPose.ts) uses `spec.defaultValue` when present, else falls back to `(min+max)/2`. Existing step-snap and float-trim logic applies to both paths. Slider UI (`AnchorSliders.tsx`) is unchanged — the slider track still shows `[min, max]`.

### 2. Widen joint ranges (and set explicit defaults where the midpoint would drift)

| Key | Old `[min, max]` | New `[min, max]` | `defaultValue` | Midpoint today | Midpoint after |
|---|---|---|---|---|---|
| `rightShoulderAngleDeg` | `[-30, 112]` | `[-30, 180]` | `41` | 41 | 41 |
| `leftShoulderAngleDeg` | `[-30, 112]` | `[-30, 180]` | `41` | 41 | 41 |
| `rightShoulderAbductionDeg` | `[0, 62]` | `[0, 120]` | `31` | 31 | 31 |
| `leftShoulderAbductionDeg` | `[0, 62]` | `[0, 120]` | `31` | 31 | 31 |
| `rightKneeYawDeg` | `[-90, 90]` | `[-85, 85]` | — | 0 | 0 |
| `leftKneeYawDeg` | `[-90, 90]` | `[-85, 85]` | — | 0 | 0 |

Thigh abduction (`[-30, 64]` with midpoint 17) is intentionally left alone — user confirmed that range in the previous session.

### 3. Fix the elbow hinge axis

In [src/drill/skeletonReconstructor.ts](../../../src/drill/skeletonReconstructor.ts), the forearm currently rotates around `shoulderAcross`:

```ts
// current (line ~250)
const forearmDir = impForearm
  ? normalize(impForearm as V3)
  : normalize(rotAroundAxis(upperArmDir, shoulderAcross, -elbowBend))
```

Replace with a hinge axis derived per-frame from the upper arm and the spine:

```ts
// new
const hingeRaw: V3 = [
  upperArmDir[1]*torsoUp[2] - upperArmDir[2]*torsoUp[1],
  upperArmDir[2]*torsoUp[0] - upperArmDir[0]*torsoUp[2],
  upperArmDir[0]*torsoUp[1] - upperArmDir[1]*torsoUp[0],
]
const hingeMag = Math.sqrt(hingeRaw[0]**2 + hingeRaw[1]**2 + hingeRaw[2]**2)
const elbowHinge: V3 = hingeMag < 1e-6 ? shoulderAcross : normalize(hingeRaw)
const forearmDir = impForearm
  ? normalize(impForearm as V3)
  : normalize(rotAroundAxis(upperArmDir, elbowHinge, -elbowBend))
```

- For a rest-down arm: `cross(down, torsoUp)` points along `shoulderAcross` — so the forearm bends forward (current behavior, regression-safe).
- For an arm raised overhead: `cross(up, torsoUp)` is tiny → fallback to `shoulderAcross` keeps behavior defined and smooth.
- For an abducted arm (out to the side): hinge is roughly `forward`, so elbow bend pulls the forearm up toward the face — the correct biological direction.

`forearmTwistDeg` still independently twists the hand fan around the forearm axis, so users who want to rotate the bend plane still can.

Sign convention: `-elbowBend` is kept (same as today) because `elbowAngleDeg=180` means straight, `30` means bent — `elbowBend = 180 - elbowAngleDeg` is the bend amount.

### 4. Tests

File: [src/drill/__tests__/skeletonReconstructor.test.ts](../../../src/drill/__tests__/skeletonReconstructor.test.ts).

Add:

- **Arm-overhead elbow bends toward face.** Set `rightShoulderAbductionDeg: 90`, `rightShoulderAngleDeg: 0`, `rightElbowAngleDeg: 90`. Assert the wrist y-coordinate is less (higher on screen) than the elbow y — forearm points up toward the head, not sideways.
- **Arm-rest elbow bends forward.** Set defaults with `rightElbowAngleDeg: 90`. Assert wrist z-coordinate is less than elbow z (forearm points toward camera, toward −z) — regression guard for the rest-down case.
- **MIDPOINT_POSE honors `defaultValue`.** Assert `MIDPOINT_POSE.rightShoulderAbductionDeg === 31`, `MIDPOINT_POSE.rightShoulderAngleDeg === 41`, and that params without a `defaultValue` still equal `(min+max)/2` snapped to step.

Existing tests must continue to pass.

## Out of scope

- `anchorExtractor.ts` still clamps imported landmark poses to the old wider bounds ([anchorExtractor.ts:269-282](../../../src/drill/anchorExtractor.ts#L269-L282)). With the new ranges, shoulder abduction up to 180° won't clamp (it's widened), but shoulder flex >180° still will. Not touching this pass — flag only.
- No per-side thigh-abduction range changes.
- No UI changes (slider labels, highlighting, copy).

## Files touched

- [src/drill/PoseAnchor.ts](../../../src/drill/PoseAnchor.ts) — add `defaultValue?`, update 6 spec entries
- [src/drill/neutralPose.ts](../../../src/drill/neutralPose.ts) — use `defaultValue` in `buildMidpointPose`
- [src/drill/skeletonReconstructor.ts](../../../src/drill/skeletonReconstructor.ts) — new elbow hinge axis in `buildArm`
- [src/drill/__tests__/skeletonReconstructor.test.ts](../../../src/drill/__tests__/skeletonReconstructor.test.ts) — 3 new tests

## Verification

1. `cd poses_viewer && npx tsc --noEmit` passes (modulo pre-existing `fs`/`path` test-file errors).
2. Existing skeleton tests pass.
3. Manual check in editor: Reset lands on the documented defaults (41/41/31/31/17/17/0/0); arm-overhead poses look natural; knee-yaw slider no longer allows the leg to swing past 85°.
