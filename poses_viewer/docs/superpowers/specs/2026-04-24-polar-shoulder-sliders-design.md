# Polar shoulder sliders (computed view over flex/abd)

**Status:** Design — awaiting user review
**Date:** 2026-04-24
**Scope:** `poses_viewer/src/drill/PoseAnchor.ts`, `src/components/AnchorSliders.tsx`, small new conversion module + unit test
**Branch:** 003-stage1-calibration
**Depends on:** shipped commit `762d163` (plane-projection shoulder FK)

## Problem

After the plane-projection FK landed, moving `rightShoulderAngleDeg` ("Shoulder fwd") or `rightShoulderAbductionDeg` ("Shoulder side") in isolation now traces a clean anatomical plane. But for table-tennis coaching the natural way to describe an arm position is not "30° flex + 45° abd" — it's "raise the arm to 100°, in the plane between forward and sideways." The anatomy terms are correct; they're just not the vocabulary the user thinks in.

## Goal

Add a second pair of sliders — **Elevation** and **Plane** — that expose the same two DOFs under a polar parameterization. Both pairs stay visible and stay in sync. Dragging one pair updates the other.

`PoseAnchor` is not touched. The polar sliders are a **computed view** over the existing `shoulderAngleDeg`/`shoulderAbductionDeg` fields.

## Why computed-view (and not new PoseAnchor fields)

Rejected: "add `rightShoulderElevationDeg` and `rightShoulderPlaneDeg` to the anchor."
- Two sources of truth → sync bugs and a "who wins" rule.
- Extractor (`anchorExtractor.ts`) returns flex/abd from MediaPipe frames; would need to also emit polar, doubling the output contract.
- Fixture files and Android's `drill_configs` Room table would need schema migration for a UI-only concern.
- Anchor interpolation (`anchorInterpolator.ts`) lerps every field — two redundant pairs would lerp differently and fight each other.

Computed-view keeps a single source of truth in the anchor, avoids migrations, and interpolation stays unambiguous.

## Parameterization

Polar (elevation + plane-of-elevation), all in degrees:

- `elevation` — how high the arm is lifted. `0° = arm straight down` (along torsoDown), `180° = arm straight up` (along −torsoDown). Range independent of direction.
- `plane` — compass bearing within the horizontal ring, in the anatomical frame (not the world frame): `0° = pure forward (sagittal)`, `90° = pure sideways / lateral (frontal, away from the midline)`, `−90° = cross-body / medial (toward the midline)`, `180° = purely backward`.

The polar pair uses the **same anatomical convention on both sides**: `plane=90°` means "lateral, away from the player's midline" for both the right and the left arm. The world-space left/right mirroring is handled downstream by the FK's existing `abSign` flip — polar→rect does **not** know or care which side it's for. This matches the user-facing semantics of the existing `shoulderAbductionDeg` slider ("positive = away from midline on both sides").

### Conversion formulas

Derived directly from the new plane-projection FK:

```
upperArmDir = cos(flex)·cos(abd)·torsoDown
            + sin(flex)·cos(abd)·shoulderForward
            + sin(abd)·shoulderAcross
```

**Polar → rectangular** (used when user drags a polar slider):

```
flex = atan2( cos(plane) · sin(elevation),  cos(elevation) )
abd  = asin( sin(plane) · sin(elevation) )
```

**Rectangular → polar** (used to display polar values given the anchor):

```
dDown   = cos(flex) · cos(abd)
dForward = sin(flex) · cos(abd)
dAcross = sin(abd)
elevation = acos( clamp(dDown, -1, 1) )
plane     = atan2( dAcross, dForward )
```

When `elevation == 0` or `180`, `plane` is mathematically indeterminate (`atan2(0, 0)`). The formula returns `0` in that case, which is a stable, harmless value (plane has no effect on the arm there). No special-casing.

**Invariants:**
- `polar → rect → polar` is identity everywhere except the two degenerate elevations, where `plane` snaps to 0.
- `rect → polar → rect` is identity everywhere except when the resulting `abd` exceeds ±90°: the UI slider (`rightShoulderAbductionDeg ∈ [-40, 120]`) reaches 120°, but `asin(sin(plane)·sin(elevation))` can't exceed 90°. This ambiguity is why the rect-form UI slider still has its own range — see §Degeneracies.

## Degeneracies

1. **Elevation ∈ {0°, 180°}:** `plane` is indeterminate. Slider displays 0°; dragging it at this elevation does nothing until elevation moves off the pole. Documented via a faded/disabled cue would be nice but is out of scope — user can discover.

2. **`abd` in `(90°, 120°]`:** the existing rectangular slider allows `abd` up to 120° (arm rotated past pure lateral). The polar pair **cannot reach that region at all**: `abd = asin(sin(plane)·sin(elevation))` is bounded to `[-90°, 90°]`. A pose set via `Shoulder side = 120°` round-trips to `elevation ≈ 90°, plane ≈ 90°` → `abd = 90°`, losing 30°. The two parameterizations are **not** fully interchangeable here — the "arm past pure lateral" region is reachable only through the rect slider. Round-tripping *through* polar will clip such poses to `abd = 90°`.

This is an accepted trade-off: the extreme abduction region is rarely coached (it's anatomically past the shoulder's natural range), and preserving it would require a 3rd DOF that the polar pair doesn't expose. User expectation: "don't drag the polar sliders if you need abd > 90°; use the rect slider." We will not visually warn; the slider value simply clamps if the user drags polar after setting rect to the extreme.

3. **`plane ∈ (90°, 180°]`:** arm behind the body. The formulas are defined and the FK renders correctly; the pose just looks unusual. Kept in range because cross-table reach poses need it.

4. **Out-of-range flex from polar.** `polarToFlexAbd(elevation=90°, plane=180°)` yields `flex = −90°`, below the rect slider's min of `−30°`. Any polar input where `cos(plane)·sin(elevation) < 0` combined with a small `cos(elevation)` can produce flex outside `[-30°, 180°]`.

   **Resolution:** `polarToFlexAbd` **does not clamp**. The write path in `AnchorSliders.tsx` clamps both outputs to the rect slider's declared `min`/`max` before writing to the anchor. If clamping kicks in, the rect sliders show the clamped values, the polar sliders re-derive from the clamped anchor, and both pairs end up consistent (but at a nearby pose, not the exact one the user dragged to). This is the expected UX for "asking for a pose the rect DOF can't represent."

## UI layout

Each shoulder gets its polar pair **inserted next to the existing pair**, inside the same arm group:

```
▸ Right arm (stroking)
    Shoulder fwd            (rectangular, existing)
    Shoulder side           (rectangular, existing)
    Shoulder elevation      (polar, new)
    Shoulder plane          (polar, new)
    Elbow
    Wrist bend
    …
```

Both pairs edit the same underlying `(rightShoulderAngleDeg, rightShoulderAbductionDeg)` state, so dragging any one of the four slides all four labels' displayed values in sync on the next render.

Label wording (English, to match the existing shoulder labels):
- `Shoulder elevation` (range 0..180, step 1, default derived from `NEUTRAL_POSE`)
- `Shoulder plane` (range −90..180, step 1, default derived from `NEUTRAL_POSE`)

## Extending `AnchorParamSpec` for computed params

Today `AnchorParamSpec` is `{ key: keyof PoseAnchor, label, min, max, step, defaultValue? }`. `AnchorSliders.tsx` uses `spec.key` for:

1. Reading: `anchor[spec.key] as number`.
2. Writing: `setKey(spec.key, v)` → `{ ...anchor, [k]: v }`.
3. Row refs (`rowRefs.current[spec.key]`) for scroll-into-view.
4. Clipboard copy text.
5. Joint-highlight matching (`highlightedParams` is `keyof PoseAnchor[]`).

For (1) and (2) we need getter/setter semantics. For (3)–(5) we need a stable string id.

**Proposed shape** — discriminated union:

```ts
export type AnchorParamSpec =
  | (AnchorParamSpecBase & { kind: 'direct'; key: keyof PoseAnchor })
  | (AnchorParamSpecBase & {
      kind: 'computed'
      id: string                                   // stable id for refs/clipboard/highlight
      keys: readonly (keyof PoseAnchor)[]          // underlying keys this view touches (for highlighting)
      read: (anchor: PoseAnchor) => number
      write: (anchor: PoseAnchor, value: number) => PoseAnchor
    })

interface AnchorParamSpecBase {
  label: string
  min: number
  max: number
  step: number
  defaultValue?: number
}
```

Migration: every existing spec in `ANCHOR_PARAM_GROUPS` gets `kind: 'direct'` added (mechanical, ~30 entries). `ANCHOR_PARAM_GROUPS` is the only author site; nothing outside that file constructs specs, so the blast radius is contained.

`AnchorSliders.tsx` handles both shapes via a `specRead` / `specWrite` / `specIdentity` helper. The joint-highlight system matches a computed spec when **any** of its `keys` is in `highlightedParams`.

## Files touched

- **[`poses_viewer/src/drill/PoseAnchor.ts`](src/drill/PoseAnchor.ts)** — extend `AnchorParamSpec` to the discriminated-union shape; add `kind: 'direct'` to every existing entry; add four new `'computed'` entries (2 per side) referencing `keys: ['rightShoulderAngleDeg', 'rightShoulderAbductionDeg']` (or left equivalents).
- **[`poses_viewer/src/drill/polarShoulder.ts`](src/drill/polarShoulder.ts)** — new module. Two functions: `polarToFlexAbd({ elevation, plane }) → { flex, abd }` and `flexAbdToPolar({ flex, abd }) → { elevation, plane }`. Pure math; side-agnostic (no `abSign`); no React or anchor dependencies.
- **[`poses_viewer/src/components/AnchorSliders.tsx`](src/components/AnchorSliders.tsx)** — handle the `'computed'` spec kind: read via `spec.read(anchor)`, write via `spec.write(anchor, v)`, key everything off `spec.id` instead of `spec.key` when computed (helper that returns the string id for either kind). Joint-highlight: for computed, test if any of `spec.keys` intersects `highlightedParams`.
- **[`poses_viewer/src/drill/__tests__/polarShoulder.test.ts`](src/drill/__tests__/polarShoulder.test.ts)** — new. Round-trip `polar → rect → polar` on a grid, degeneracy behaviour at elevation `0`/`180`, left/right sign symmetry.

No changes to `skeletonReconstructor.ts`, `anchorExtractor.ts`, `neutralPose.ts`, `anchorInterpolator.ts`, Room schema, fixtures, or any other consumer of `PoseAnchor`. The underlying DOF space is identical.

## Testing

Unit tests for `polarShoulder.ts`:

1. **Round-trip in the regular region.** For a 7×7 grid of `(elevation ∈ {10°, 30°, ..., 170°}, plane ∈ {-60°, -30°, 0°, 45°, 90°, 135°, 170°})` on both sides, assert `flexAbdToPolar(polarToFlexAbd(p)) ≈ p` to 1e-6 radians (after converting to degrees). Excludes the `elevation ∈ {0°, 180°}` poles.
2. **Pole degeneracy.** `polarToFlexAbd({ elevation: 0, plane: ANYTHING })` → `flex = 0, abd = 0`. `flexAbdToPolar({ flex: 0, abd: 0 })` → `elevation = 0, plane = 0`.
3. **Pure-axis cases.** `polar(elevation=90, plane=0) → (flex=90, abd=0)` — arm horizontal forward. `polar(elevation=90, plane=90)` → `(flex=0, abd=90)` on right (abSign=-1 flips if needed — documented in test). Straight-up: `polar(elevation=180, plane=anything)` → `flex=180, abd=0`.
4. **Side-agnosticism.** `polarToFlexAbd` is called identically for left and right (no side parameter). Visual mirroring is FK's job via `abSign`. Sanity check: with the same polar value fed into left-arm and right-arm shoulder fields, `reconstructFromAnchor` produces elbows mirrored across `x = hipMidX` (bodies are bilaterally symmetric in the base pose). This confirms our polar→rect and the existing FK compose correctly without any side hackery inside the conversion.

Integration: no visual regression test framework here, so manual in the browser:
- Reset to MIDPOINT_POSE. Drag `Shoulder fwd` → watch `Shoulder elevation` and `Shoulder plane` update in the sidebar.
- Drag `Shoulder elevation` → watch `Shoulder fwd` and `Shoulder side` update.
- Confirm the 3D mannequin looks identical whether you arrive at a given pose via the rect or the polar pair.

## Out of scope

- Ukrainian translation of shoulder labels (all shoulder labels stay English to match the existing four).
- Same treatment for hips/knees (could be useful someday, but not part of this task).
- Making the polar sliders the *canonical* parameterization (would require migrating fixtures, Android Room table, extractor output — a separate, bigger change).
- Hiding/disabling the `Shoulder plane` slider when `elevation ∈ {0°, 180°}` — the value is harmless at poles, and visually indicating "disabled" adds UI complexity for minimal gain.
