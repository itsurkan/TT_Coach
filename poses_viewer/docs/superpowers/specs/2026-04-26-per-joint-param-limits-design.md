# Per-Joint Param Limits — Design Spec

**Date:** 2026-04-26
**Branch:** 003-stage1-calibration

## Problem

The MannequinEditor preset system lets you lock a param to a fixed value (e.g. hip height), but there is no way to *constrain* a param to a narrower range while still letting the user slide within it. Example: limit `figureYawDeg` for `rightHip` to −30°..+30° while training a specific stroke pattern.

## Goal

Each highlighted param row in AnchorSliders gets inline editable min/max fields when a joint is selected. Limits are per-joint × per-param, persisted to localStorage, and reset to spec defaults on demand.

---

## Data Model

```ts
// Key: "${jointId}.${paramKey}"  e.g. "rightHip.figureYawDeg"
type ParamLimitsMap = Record<string, { min: number; max: number }>
```

- Stored in localStorage under key `poses_viewer_param_limits`
- Only keys that differ from spec defaults are stored (keeps storage clean)
- On reset: delete the key from the map → falls back to spec defaults

---

## Storage / Hook

New file `src/hooks/useParamLimits.ts`:

```ts
function useParamLimits(): {
  getLimits(jointId: string, paramKey: string, specMin: number, specMax: number): { min: number; max: number }
  setLimits(jointId: string, paramKey: string, min: number, max: number): void
  resetLimits(jointId: string, paramKey: string): void
}
```

- Reads/writes `poses_viewer_param_limits` in localStorage
- Returns spec defaults when no override exists
- The hook lives at the `MannequinEditor` level and is passed down to AnchorSliders

---

## AnchorSliders changes

`AnchorSliders` already receives `highlightedParams` and `selectedJointId`. New props added:

```ts
getLimits?: (jointId: string, paramKey: string, specMin: number, specMax: number) => { min: number; max: number }
setLimits?: (jointId: string, paramKey: string, min: number, max: number) => void
resetLimits?: (jointId: string, paramKey: string) => void
```

When `selectedJointId` is set and a row is highlighted:
- Render two small `<input type="number">` fields after the slider: **min** and **max**
- Pre-filled with current effective limits
- On blur/Enter: call `setLimits`, then clamp the live anchor value to `[newMin, newMax]` and call `onChange`
- Small reset icon button (`↺`) next to the inputs: calls `resetLimits`, restores spec defaults, clamps anchor value

The range `<input type="range">` uses `effectiveMin`/`effectiveMax` from `getLimits` instead of `spec.min`/`spec.max`. The number display and copy behavior are unchanged.

---

## Clamping on limit change

When the user tightens a limit and the current anchor value is outside the new range:
- Snap to `Math.max(newMin, Math.min(newMax, currentValue))`
- Call `onChange` with the updated anchor immediately

---

## Wiring in MannequinEditor

```tsx
const { getLimits, setLimits, resetLimits } = useParamLimits()

<AnchorSliders
  ...
  getLimits={getLimits}
  setLimits={setLimits}
  resetLimits={resetLimits}
/>
```

No changes to `DrillEditor` — the new props are optional.

---

## Reset behaviour

- Per-row `↺` button resets that single `jointId.paramKey` pair to spec defaults
- No global reset (out of scope)

---

## Out of scope

- Limits shown on non-highlighted rows
- Per-pose-file limits
- Import/export of limit configs
- DrillEditor integration
