# Shoulder DOF decoupling (poses_viewer)

**Status:** Design — awaiting user review
**Date:** 2026-04-24
**Scope:** `poses_viewer/src/drill/skeletonReconstructor.ts` + `anchorExtractor.ts`
**Branch:** 003-stage1-calibration

## Problem

In the mannequin editor, combining `rightShoulderAngleDeg` (slider labeled "Shoulder fwd") and `rightShoulderAbductionDeg` (slider labeled "Shoulder side") produces unpredictable arm motion. Moving either slider while the other is nonzero sweeps the arm along a curve instead of in a single anatomical plane.

The user's expectation (which matches textbook shoulder anatomy):

- **`rightShoulderAngleDeg` alone** should raise the arm purely in the **sagittal plane**: 0° = arm down along torso → 90° = arm horizontal forward → 180° = arm straight up.
- **`rightShoulderAbductionDeg` alone** should raise the arm purely in the **frontal plane**: 0° = arm down → 90° = arm horizontal out to the side → 180° = arm straight up.
- **Both simultaneously** should place the arm in an intermediate plane (diagonal between forward and sideways), predictably.

## Root cause

`skeletonReconstructor.ts:238` composes the two DOFs sequentially:

```ts
const upperArmDir = normalize(
  rotAroundAxis(
    rotAroundAxis(torsoDown, shoulderForward, abSign * shAbdDeg), // abduction first
    shoulderAcross, -shFwdDeg                                       // then flexion
  )
)
```

Sequential rotations around fixed axes are order-dependent and do **not** produce "pure sagittal + pure frontal" behavior. As soon as abduction is nonzero, the subsequent flex rotation pivots around an axis that no longer lies in the arm's current plane, so the arm traces a curve. Geometrically this is a form of gimbal coupling.

## Design: decomposition-based reconstruction (variant B)

Keep the parameter names, ranges, and defaults (`rightShoulderAngleDeg: -30..180`, `rightShoulderAbductionDeg: -40..120`, etc.). Replace the sequential FK with a **direct construction** of the upper-arm direction from the two angles, treated as independent plane projections.

### Forward kinematics (reconstructor)

Given body-frame axes (already computed upstream):
- `torsoDown` — unit vector pointing from shoulder to feet (with tilt/side-bend applied)
- `shoulderForward` — unit vector pointing forward out of the chest
- `shoulderAcross` — unit vector pointing to the player's left along the shoulder line

Treat the two sliders as **anatomical plane angles**:
- `flex = shFwdDeg` → angle in the sagittal plane (torsoDown ↔ shoulderForward)
- `abd = abSign * shAbdDeg` → angle in the frontal plane (torsoDown ↔ shoulderAcross, signed by side)

Construct `upperArmDir` directly in body frame:

```ts
const fRad = deg(shFwdDeg)
const aRad = deg(abSign * shAbdDeg)

// Plane-projection components:
//   forward projection = sin(flex) * cos(abd)
//   across  projection = sin(abd)
//   down    projection = cos(flex) * cos(abd)
const dDown    = Math.cos(fRad) * Math.cos(aRad)
const dForward = Math.sin(fRad) * Math.cos(aRad)
const dAcross  = Math.sin(aRad)

const upperArmDir = normalize([
  dDown * torsoDown[0] + dForward * shoulderForward[0] + dAcross * shoulderAcross[0],
  dDown * torsoDown[1] + dForward * shoulderForward[1] + dAcross * shoulderAcross[1],
  dDown * torsoDown[2] + dForward * shoulderForward[2] + dAcross * shoulderAcross[2],
])
```

**Properties this delivers:**
- `(flex=0, abd=0)` → `upperArmDir = torsoDown` (arm straight down). ✓
- `(flex=90, abd=0)` → `upperArmDir = shoulderForward` (pure sagittal forward). ✓
- `(flex=180, abd=0)` → `upperArmDir = -torsoDown` (straight up). ✓
- `(flex=0, abd=90)` → `upperArmDir = shoulderAcross` (pure lateral). ✓
- `(flex=0, abd=180)` → `upperArmDir = -torsoDown` (straight up via the side path). ✓
- `(flex=0, abd=-40)` (cross-body reach) → arm sweeps across the chest. ✓
- Independent movement: changing `flex` with `abd=30` fixed moves the arm in a *meridian* of a sphere (stable, predictable), not a curve that drifts laterally.

**Gimbal caveat:** At `abd = ±90°` (arm purely to the side), `flex` becomes a rotation around the arm's own axis (no-op on the arm direction). This is an anatomical truth — at pure lateral, the arm-direction has no sagittal component. We accept it; the user is expected to use elbow swivel / forearm twist for fine orientation in those poses.

### Inverse kinematics (extractor)

`anchorExtractor.ts:295-308` already uses a compatible decomposition:

```ts
const abduct = asin(across_component)           // across component fully determines abd
const flex   = atan2(forward_component, vertical_component)
```

This **almost** matches the new FK but isn't quite the inverse, because today's FK's sequential rotation doesn't match `asin/atan2`. Once the FK is switched to plane-projection, the inverse needs to match exactly. For the new FK:

```
dDown    = cos(flex) * cos(abd)
dForward = sin(flex) * cos(abd)
dAcross  = sin(abd)
```

Invert:
```ts
const abd  = asin(clamp(acr, -1, 1))
const flex = atan2(fwd, vert)   // cos(abd) cancels — sign and ratio preserved
```

The existing extractor already does `atan2(fwd, vert)` for flex (not `atan2(fwd, vert*cos_abd)`), which is the correct inverse for the new FK. So **the extractor likely needs no change**, but the implementation plan will verify this with a round-trip test: `extract(reconstruct(anchor)) ≈ anchor` for a grid of (flex, abd) values.

### Downstream impact

`skeletonReconstructor.ts:238` feeds `upperArmDir` into:
1. Elbow position: `elbow = shoulder + upperArm * upperArmDir`
2. Elbow hinge computation (uses `cross(torsoUp, upperArmDir)` + `shoulderAcross` + `shoulderForward`)
3. Elbow swivel circle (uses the shoulder→wrist axis derived from `upperArmDir`)

All three consume `upperArmDir` as a unit vector — they don't care *how* it was constructed. So the downstream arm chain (elbow bend, elbow swivel, forearm twist, wrist) continues to work unchanged, and `rightElbowYawDeg` remains the third DOF (internal/external humeral rotation) with no change to its semantics.

## Impact on existing anchors

Existing saved anchors will render **slightly differently** after this change — the arm direction for `(flex=45, abd=30)` is not the same vector as before. The change is small for small-to-moderate angles and grows at extremes. This is acceptable because:
- The existing behavior was the bug, not a desired reference.
- Anchors can be re-authored via the editor where needed.
- No production flow outside poses_viewer consumes `rightShoulderAngleDeg` / `rightShoulderAbductionDeg` values (they live in the editor's local state).

If the user wants to preserve visual parity for specific saved anchors, they can re-capture or re-tune those anchors after the change lands.

## Testing

**Unit tests** — new file or additions to [`poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts`](poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts):

1. **Pure sagittal sweep.** For a figure at `figureYawDeg=0`, `abd=0`, iterate `flex ∈ {0, 45, 90, 135, 180}`. Assert the elbow lies in the plane `x = hipMidX` (sagittal plane has zero lateral component). Assert that the elbow's (y, z) traces a semicircle of radius `upperArm` around the shoulder.
2. **Pure frontal sweep.** `flex=0`, iterate `abd ∈ {0, 45, 90, 135, 180}`. Assert the elbow's z-component equals the shoulder's z (no forward movement — frontal plane).
3. **Independence check.** Changing `flex` while `abd` is fixed should move the elbow only in a meridian — verify that the across-component (projection of elbow-shoulder onto `shoulderAcross`) stays constant (`sin(abd)`).
4. **Round-trip with extractor.** `extract(reconstruct({flex, abd, ...})).{flex,abd} ≈ {flex,abd}` for a 5×5 grid over `(flex, abd) ∈ [-30..180] × [-40..120]`. Tolerance: 1°.

**Visual regression** — after the math change:
- Reset to MIDPOINT_POSE, wiggle each shoulder slider alone → confirm single-plane motion.
- Combine sliders, wiggle one → confirm the other stays visually pinned (arm doesn't drift sideways when moving flex).
- Existing backswing / ready-position anchors still look anatomically plausible (they will differ, but shouldn't be broken).

## Files touched

- [`poses_viewer/src/drill/skeletonReconstructor.ts`](poses_viewer/src/drill/skeletonReconstructor.ts) — replace L238 FK block with plane-projection construction. ~15 lines changed.
- [`poses_viewer/src/drill/anchorExtractor.ts`](poses_viewer/src/drill/anchorExtractor.ts) — verify inverse; may need a tiny sign-convention tweak if the round-trip test fails. ~0-5 lines.
- [`poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts`](poses_viewer/src/drill/__tests__/skeletonReconstructor.test.ts) — add 4 test cases listed above.
- No changes to `PoseAnchor.ts`, `neutralPose.ts`, UI — parameter names/ranges/defaults stay identical.

## Out of scope

- Shape vs motion slider UX (link shape sliders across START/END) — separate task.
- Stroke keyframing (backswing → contact → follow-through) — separate task.
- `leftShoulderAngleDeg` / `leftShoulderAbductionDeg` math is symmetric: the same change applies via the existing `abSign` flip, no extra work.
- Elbow swivel (`rightElbowYawDeg`) semantics — unchanged.
