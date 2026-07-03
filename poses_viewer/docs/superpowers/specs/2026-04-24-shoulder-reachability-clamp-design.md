# Right-shoulder reachability clamp (poses_viewer)

**Status:** Design — awaiting user review
**Date:** 2026-04-24
**Scope:** `poses_viewer/src/drill/` + `poses_viewer/src/components/AnchorSliders.tsx`
**Branch:** 003-stage1-calibration

## Problem

In the mannequin editor, the right-shoulder sliders `rightShoulderAngleDeg` ("Shoulder fwd") and `rightShoulderAbductionDeg` ("Shoulder side") can be dialed into (flex, abd) combinations the shoulder joint cannot reach anatomically. The user identified two sample points on the reachability envelope:

- At `abd = -40` (extreme cross-body reach), `flex` must be `≤ 100°`.
- At `flex = 180` (arm straight up), `abd` must be `≥ 70°`.

Between these, further unnatural combinations are reachable. The constraint is a property of the shoulder joint itself (anatomy), not of the FK implementation, so the two samples bound a larger forbidden region.

## Design: linear envelope + slider-commit clamp

### Envelope

A single linear bound `abd_min(flex)` interpolating the two samples, flat-capped outside:

```
abd_min(flex) =
  -40                          if flex ≤ 100
  -40 + (flex - 100) * 110/80  if 100 < flex < 180    # slope = 1.375 abd°/flex°
  70                           if flex ≥ 180
```

Properties:

- Below `flex = 100`, `abd_min = -40` equals the slider minimum, so the constraint is silently inactive — you can move abd freely in the low-flex range.
- At `flex = 100`, `abd_min = -40` (sample A on the line).
- At `flex = 180`, `abd_min = 70` (sample B on the line).
- At `flex = 129`, `abd_min ≈ 0` — a useful reference point.

Hyperextension (`flex < 0`) is left uncapped. The two user-provided samples don't speak to that end, and we don't want to guess anatomy without data. Revisit when a real observation shows up.

### Clamp helper

New pure module `poses_viewer/src/drill/shoulderClamp.ts`:

```ts
export const RIGHT_SHOULDER_FLEX_KNEE = 100    // flex-deg where constraint starts biting
export const RIGHT_SHOULDER_FLEX_CEIL = 180    // slider max
export const RIGHT_SHOULDER_ABD_AT_CEIL = 70   // abd-min at flex=180
export const RIGHT_SHOULDER_ABD_AT_KNEE = -40  // abd-min at flex=100 (= slider min)

export function rightShoulderAbdMin(flex: number): number {
  if (flex <= RIGHT_SHOULDER_FLEX_KNEE) return RIGHT_SHOULDER_ABD_AT_KNEE
  if (flex >= RIGHT_SHOULDER_FLEX_CEIL) return RIGHT_SHOULDER_ABD_AT_CEIL
  const t = (flex - RIGHT_SHOULDER_FLEX_KNEE) /
            (RIGHT_SHOULDER_FLEX_CEIL - RIGHT_SHOULDER_FLEX_KNEE)
  return RIGHT_SHOULDER_ABD_AT_KNEE +
         t * (RIGHT_SHOULDER_ABD_AT_CEIL - RIGHT_SHOULDER_ABD_AT_KNEE)
}

export function rightShoulderFlexMax(abd: number): number {
  // Inverse of abd_min for the sloped segment. Above abd=70 the constraint
  // doesn't cap flex; below abd=-40 it's impossible (slider min).
  if (abd >= RIGHT_SHOULDER_ABD_AT_CEIL) return RIGHT_SHOULDER_FLEX_CEIL
  if (abd <= RIGHT_SHOULDER_ABD_AT_KNEE) return RIGHT_SHOULDER_FLEX_KNEE
  const t = (abd - RIGHT_SHOULDER_ABD_AT_KNEE) /
            (RIGHT_SHOULDER_ABD_AT_CEIL - RIGHT_SHOULDER_ABD_AT_KNEE)
  return RIGHT_SHOULDER_FLEX_KNEE +
         t * (RIGHT_SHOULDER_FLEX_CEIL - RIGHT_SHOULDER_FLEX_KNEE)
}

export type ShoulderActiveKey =
  | 'rightShoulderAngleDeg'
  | 'rightShoulderAbductionDeg'

/**
 * Clamp a candidate (flex, abd) pair to the anatomical envelope. The slider
 * the user just moved (`activeKey`) keeps its value; the other yields.
 * Values not on the envelope boundary are returned unchanged.
 */
export function clampRightShoulder(
  flex: number,
  abd: number,
  activeKey: ShoulderActiveKey,
): { flex: number; abd: number } {
  const minAbd = rightShoulderAbdMin(flex)
  if (abd >= minAbd) return { flex, abd }
  if (activeKey === 'rightShoulderAngleDeg') {
    // User moved flex; abd yields upward to satisfy bound.
    return { flex, abd: minAbd }
  }
  // User moved abd; flex yields downward.
  return { flex: rightShoulderFlexMax(abd), abd }
}
```

### Wiring into the slider

`AnchorSliders.tsx:40` centralizes all slider commits in one `setKey` helper:

```ts
const setKey = (k: keyof PoseAnchor, v: number) => {
  onChange({ ...anchor, [k]: v })
}
```

Replace with a shoulder-aware version:

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

The UI re-renders with the clamped values; from the user's POV one slider moved and the other silently follows when the bound is crossed. This is Policy P1 — "last-touched slider wins" — chosen over projecting both values onto the boundary line because it matches the user's direct intent with the slider they're dragging.

### Why not in the reconstructor or extractor?

- **Reconstructor (`skeletonReconstructor.ts`)** is a pure function: `PoseAnchor → landmarks`. It must render whatever anchor it's given, including anchors stored from before the clamp existed. No change here.
- **Extractor (`anchorExtractor.ts`)** converts real captured landmarks (or MediaPipe output) into anchor values. These may legitimately fall outside the editor's anatomical envelope — the person in the video may be at a pose the envelope disallows, or MediaPipe may produce noisy values. Clamping extracted values would corrupt observations. No change here.

The clamp is an **editor authoring guardrail**, not an invariant of `PoseAnchor`.

### Left shoulder

Out of scope. The two user samples are right-side only. The left shoulder stores the same fields with `abSign` flipped in the reconstructor, so mirroring would be a one-liner, but we defer until there are real samples showing the left-side envelope needs the same treatment.

## Testing

New file `poses_viewer/src/drill/__tests__/shoulderClamp.test.ts`:

1. `clampRightShoulder(100, -40, 'rightShoulderAngleDeg')` → `{flex: 100, abd: -40}`. Sample A on the bound, pass-through.
2. `clampRightShoulder(180, 70, 'rightShoulderAbductionDeg')` → `{flex: 180, abd: 70}`. Sample B on the bound.
3. `clampRightShoulder(180, 0, 'rightShoulderAngleDeg')` → `{flex: 180, abd: 70}`. User raised flex to 180, abd yields.
4. `clampRightShoulder(180, 0, 'rightShoulderAbductionDeg')` → `{flex: 129, abd: 0}` (tolerance ±1°). User lowered abd to 0, flex yields.
5. `clampRightShoulder(50, -40, 'rightShoulderAngleDeg')` → `{flex: 50, abd: -40}`. Below the knee, constraint inactive.
6. `clampRightShoulder(140, 0, 'rightShoulderAngleDeg')` → `{flex: 140, abd: 15}` (tolerance ±1°). Ramp region, abd yields.
7. `rightShoulderAbdMin(100)` === -40; `rightShoulderAbdMin(180)` === 70; `rightShoulderAbdMin(140)` === 15 (midpoint of the two samples).

Existing reconstructor / extractor tests must continue to pass unchanged — the clamp doesn't touch either module.

### Manual verification

The clamp runs at commit time: the slider writes its new value, `setKey` clamps, and the next render reflects the clamped state. So in the UI the *other* (non-dragged) slider visibly jumps; the dragged slider stays wherever the user let go.

1. Open `DrillEditor`. Set `rightShoulderAbductionDeg = -40`. Drag `rightShoulderAngleDeg` up to 180 → abd slider visibly jumps from -40 up to 70 as flex crosses the envelope; flex stays at 180.
2. Reset. Set `rightShoulderAngleDeg = 180`. Drag `rightShoulderAbductionDeg` down to -40 → flex slider visibly jumps from 180 down to 100; abd stays at -40.
3. Reset to `NEUTRAL_POSE` (defaults flex=41, abd=31). Both sliders move freely until the high-flex / low-abd corner is approached.

## Files touched

- New: `poses_viewer/src/drill/shoulderClamp.ts` — pure helper + constants. ~40 lines.
- New: `poses_viewer/src/drill/__tests__/shoulderClamp.test.ts` — seven cases above. ~50 lines.
- `poses_viewer/src/components/AnchorSliders.tsx` — wrap `setKey` to call the clamp when a right-shoulder key is written. ~10 lines.

No changes to `PoseAnchor.ts` (slider ranges / defaults unchanged), `skeletonReconstructor.ts`, `anchorExtractor.ts`, `neutralPose.ts`, or `DrillEditor.tsx`.

## Out of scope

- Left-shoulder mirror envelope.
- Hyperextension (flex < 0) envelope.
- Envelopes for other coupled DOF pairs (elbow swivel × forearm twist, knee yaw × knee swivel, etc.).
- Visual indication of the clamp (no greyed-out slider track, no warning badge). The clamp is silent — if a future UX need arises, surface it separately.
- Migrating anchors authored before this change that already sit in the forbidden region. Existing anchors are not rewritten; they just render. If the user re-touches a shoulder slider on such an anchor, the clamp snaps the *other* slider into range on that commit.
