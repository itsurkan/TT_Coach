# Per-Joint Param Limits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add inline editable min/max limit fields to highlighted AnchorSliders rows, persisted per joint×param pair in localStorage, with a reset-to-spec-defaults button per row.

**Architecture:** A new `useParamLimits` hook owns localStorage read/write. `AnchorSliders` receives three optional callbacks from it and, when a row is both highlighted and `selectedJointId` is set, renders two number inputs and a reset button after the range slider. The slider's `min`/`max` attributes and value clamping use the effective limits.

**Tech Stack:** React 18, TypeScript, localStorage, Tailwind v4, vitest 4

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `src/hooks/useParamLimits.ts` | **Create** | localStorage r/w for `poses_viewer_param_limits`; `getLimits`, `setLimits`, `resetLimits` |
| `src/hooks/__tests__/useParamLimits.test.ts` | **Create** | Unit tests for the hook |
| `src/components/AnchorSliders.tsx` | **Modify** | Accept limit props; render inline min/max inputs + reset on highlighted rows |
| `src/components/MannequinEditor.tsx` | **Modify** | Wire `useParamLimits` into `AnchorSliders` |

---

## Task 1: `useParamLimits` hook (with tests)

**Files:**
- Create: `src/hooks/useParamLimits.ts`
- Create: `src/hooks/__tests__/useParamLimits.test.ts`

- [ ] **Step 1.1: Write failing tests**

Create `src/hooks/__tests__/useParamLimits.test.ts`:

```typescript
import { renderHook, act } from '@testing-library/react'
import { useParamLimits } from '../useParamLimits'

const LS_KEY = 'poses_viewer_param_limits'

beforeEach(() => localStorage.clear())

describe('useParamLimits', () => {
  it('returns spec defaults when no override exists', () => {
    const { result } = renderHook(() => useParamLimits())
    expect(result.current.getLimits('rightHip', 'figureYawDeg', -180, 180)).toEqual({ min: -180, max: 180 })
  })

  it('setLimits persists override and returns it', () => {
    const { result } = renderHook(() => useParamLimits())
    act(() => { result.current.setLimits('rightHip', 'figureYawDeg', -30, 30) })
    expect(result.current.getLimits('rightHip', 'figureYawDeg', -180, 180)).toEqual({ min: -30, max: 30 })
    const stored = JSON.parse(localStorage.getItem(LS_KEY) ?? '{}')
    expect(stored['rightHip.figureYawDeg']).toEqual({ min: -30, max: 30 })
  })

  it('resetLimits removes override and returns spec defaults', () => {
    const { result } = renderHook(() => useParamLimits())
    act(() => { result.current.setLimits('rightHip', 'figureYawDeg', -30, 30) })
    act(() => { result.current.resetLimits('rightHip', 'figureYawDeg') })
    expect(result.current.getLimits('rightHip', 'figureYawDeg', -180, 180)).toEqual({ min: -180, max: 180 })
    const stored = JSON.parse(localStorage.getItem(LS_KEY) ?? '{}')
    expect('rightHip.figureYawDeg' in stored).toBe(false)
  })

  it('different joint×param pairs are independent', () => {
    const { result } = renderHook(() => useParamLimits())
    act(() => { result.current.setLimits('rightHip', 'figureYawDeg', -30, 30) })
    expect(result.current.getLimits('leftHip', 'figureYawDeg', -180, 180)).toEqual({ min: -180, max: 180 })
    expect(result.current.getLimits('rightHip', 'bodyRotationDeg', -90, 90)).toEqual({ min: -90, max: 90 })
  })

  it('survives page reload — reads initial state from localStorage', () => {
    localStorage.setItem(LS_KEY, JSON.stringify({ 'rightHip.figureYawDeg': { min: -45, max: 45 } }))
    const { result } = renderHook(() => useParamLimits())
    expect(result.current.getLimits('rightHip', 'figureYawDeg', -180, 180)).toEqual({ min: -45, max: 45 })
  })
})
```

- [ ] **Step 1.2: Run tests — expect FAIL (module not found)**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npx vitest run src/hooks/__tests__/useParamLimits.test.ts
```

Expected: error `Cannot find module '../useParamLimits'`

- [ ] **Step 1.3: Implement the hook**

Create `src/hooks/useParamLimits.ts`:

```typescript
import { useState } from 'react'

const LS_KEY = 'poses_viewer_param_limits'

type LimitsMap = Record<string, { min: number; max: number }>

function readLS(): LimitsMap {
  try {
    return JSON.parse(localStorage.getItem(LS_KEY) ?? '{}') as LimitsMap
  } catch {
    return {}
  }
}

function writeLS(map: LimitsMap): void {
  localStorage.setItem(LS_KEY, JSON.stringify(map))
}

export function useParamLimits() {
  const [map, setMap] = useState<LimitsMap>(() => readLS())

  const getLimits = (
    jointId: string,
    paramKey: string,
    specMin: number,
    specMax: number,
  ): { min: number; max: number } => {
    const override = map[`${jointId}.${paramKey}`]
    return override ?? { min: specMin, max: specMax }
  }

  const setLimits = (jointId: string, paramKey: string, min: number, max: number): void => {
    const next = { ...map, [`${jointId}.${paramKey}`]: { min, max } }
    writeLS(next)
    setMap(next)
  }

  const resetLimits = (jointId: string, paramKey: string): void => {
    const next = { ...map }
    delete next[`${jointId}.${paramKey}`]
    writeLS(next)
    setMap(next)
  }

  return { getLimits, setLimits, resetLimits }
}
```

- [ ] **Step 1.4: Run tests — expect PASS**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npx vitest run src/hooks/__tests__/useParamLimits.test.ts
```

Expected: 5 tests pass

- [ ] **Step 1.5: Commit**

```bash
git add src/hooks/useParamLimits.ts src/hooks/__tests__/useParamLimits.test.ts
git commit -m "feat: useParamLimits hook — per-joint param limit storage"
```

---

## Task 2: AnchorSliders — inline limit inputs on highlighted rows

**Files:**
- Modify: `src/components/AnchorSliders.tsx`

- [ ] **Step 2.1: Add the three optional props to the Props interface**

In `src/components/AnchorSliders.tsx`, replace the Props interface (lines 6–28) with:

```typescript
interface Props {
  activePhase: AnchorPhase
  onPhaseChange: (phase: AnchorPhase) => void
  anchor: PoseAnchor
  onChange: (next: PoseAnchor) => void
  onReset: () => void
  highlightedParams?: readonly (keyof PoseAnchor)[]
  hidePhaseSelector?: boolean
  selectedJointName?: string
  selectedJointId?: string
  /** Returns effective limits for a param row. Falls back to spec min/max when absent. */
  getLimits?: (jointId: string, paramKey: string, specMin: number, specMax: number) => { min: number; max: number }
  /** Persists a custom limit override for a joint×param pair. */
  setLimits?: (jointId: string, paramKey: string, min: number, max: number) => void
  /** Removes the override for a joint×param pair, reverting to spec defaults. */
  resetLimits?: (jointId: string, paramKey: string) => void
}
```

- [ ] **Step 2.2: Destructure the new props**

Replace the destructuring block (lines 30–40) with:

```typescript
export default function AnchorSliders({
  activePhase,
  onPhaseChange,
  anchor,
  onChange,
  onReset,
  highlightedParams,
  hidePhaseSelector = false,
  selectedJointName,
  selectedJointId,
  getLimits,
  setLimits,
  resetLimits,
}: Props) {
```

- [ ] **Step 2.3: Replace the param row render block**

Replace lines 156–211 (the `{group.params.map(spec => { ... })}` block) with:

```typescript
{group.params.map(spec => {
  const id = specIdentity(spec)
  const value = specRead(spec, anchor)
  const displayVal = spec.step >= 1
    ? value.toFixed(0)
    : value.toFixed(2)
  const highlighted = isHighlighted(spec)

  // Compute effective limits — custom override when available, else spec defaults.
  const showLimitEditor = highlighted && !!selectedJointId && !!getLimits
  const effectiveLimits = showLimitEditor
    ? getLimits!(selectedJointId!, id, spec.min, spec.max)
    : { min: spec.min, max: spec.max }

  return (
    <div
      key={id}
      ref={el => { rowRefs.current[id] = el }}
      className={
        'flex flex-col gap-0.5 rounded transition-colors ' +
        (highlighted
          ? 'ring-2 ring-yellow-400/60 bg-yellow-400/10 px-1.5 py-1'
          : '')
      }
    >
      <div className="flex justify-between items-center text-xs text-gray-400">
        <span className={highlighted ? 'text-yellow-200 font-medium' : ''}>
          {spec.label}
        </span>
        <div className="flex items-center gap-1.5">
          <span className="font-mono text-gray-200">{displayVal}</span>
          <button
            type="button"
            onClick={() => copyRow(spec, value, highlighted)}
            title={(() => {
              const prefix = highlighted ? jointPrefix() : null
              return prefix
                ? `Copy "${prefix} · ${id}: ${displayVal}"`
                : `Copy "${id}: ${displayVal}"`
            })()}
            className={
              'px-1 py-0.5 rounded text-[10px] font-mono transition-colors ' +
              (copiedId === id
                ? 'bg-green-600/70 text-white'
                : 'bg-gray-800 text-gray-400 hover:bg-gray-700 hover:text-gray-200')
            }
          >
            {copiedId === id ? '✓' : '⎘'}
          </button>
        </div>
      </div>
      <input
        type="range"
        min={effectiveLimits.min}
        max={effectiveLimits.max}
        step={spec.step}
        value={Math.max(effectiveLimits.min, Math.min(effectiveLimits.max, value))}
        onChange={e => onChange(specWrite(spec, anchor, parseFloat(e.target.value)))}
        className="w-full"
      />
      {showLimitEditor && (
        <div className="flex items-center gap-1.5 mt-0.5">
          <span className="text-[10px] text-gray-500">min</span>
          <input
            type="number"
            step={spec.step}
            value={effectiveLimits.min}
            onChange={e => {
              const newMin = parseFloat(e.target.value)
              if (isNaN(newMin)) return
              const newMax = Math.max(newMin, effectiveLimits.max)
              setLimits!(selectedJointId!, id, newMin, newMax)
              const clamped = Math.max(newMin, Math.min(newMax, value))
              if (clamped !== value) onChange(specWrite(spec, anchor, clamped))
            }}
            className="w-16 text-[10px] font-mono text-gray-300 bg-gray-800 border border-gray-700 rounded px-1 py-0.5"
          />
          <span className="text-[10px] text-gray-500">max</span>
          <input
            type="number"
            step={spec.step}
            value={effectiveLimits.max}
            onChange={e => {
              const newMax = parseFloat(e.target.value)
              if (isNaN(newMax)) return
              const newMin = Math.min(effectiveLimits.min, newMax)
              setLimits!(selectedJointId!, id, newMin, newMax)
              const clamped = Math.max(newMin, Math.min(newMax, value))
              if (clamped !== value) onChange(specWrite(spec, anchor, clamped))
            }}
            className="w-16 text-[10px] font-mono text-gray-300 bg-gray-800 border border-gray-700 rounded px-1 py-0.5"
          />
          <button
            type="button"
            title="Reset limits to spec defaults"
            onClick={() => {
              resetLimits!(selectedJointId!, id)
              const clamped = Math.max(spec.min, Math.min(spec.max, value))
              if (clamped !== value) onChange(specWrite(spec, anchor, clamped))
            }}
            className="px-1 py-0.5 rounded text-[10px] bg-gray-800 text-gray-500 hover:text-gray-200 hover:bg-gray-700"
          >
            ↺
          </button>
        </div>
      )}
    </div>
  )
})}
```

- [ ] **Step 2.4: Run existing tests to make sure nothing broke**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npx vitest run
```

Expected: all tests pass (AnchorSliders has no dedicated test file, so the FK fingerprint tests in `drill/__tests__/` should still pass)

- [ ] **Step 2.5: Commit**

```bash
git add src/components/AnchorSliders.tsx
git commit -m "feat: inline per-joint param limit editor in AnchorSliders"
```

---

## Task 3: Wire `useParamLimits` into MannequinEditor

**Files:**
- Modify: `src/components/MannequinEditor.tsx`

- [ ] **Step 3.1: Import the hook**

At the top of `src/components/MannequinEditor.tsx`, add after the existing imports:

```typescript
import { useParamLimits } from '../hooks/useParamLimits'
```

- [ ] **Step 3.2: Call the hook inside EditorShell**

Inside `EditorShell`, after the existing state declarations (around line 85), add:

```typescript
const { getLimits, setLimits, resetLimits } = useParamLimits()
```

- [ ] **Step 3.3: Pass the props to AnchorSliders**

Find the `<AnchorSliders` usage (around line 434) and add the three new props:

```typescript
<AnchorSliders
  activePhase="START"
  onPhaseChange={() => { /* editor is single-anchor; phase selector is inert */ }}
  anchor={anchor}
  onChange={onSliderChange}
  onReset={resetAnchor}
  highlightedParams={highlightedParams}
  hidePhaseSelector
  selectedJointName={selectedJoint ? JOINT_MAP[selectedJoint].displayName : undefined}
  selectedJointId={selectedJoint ?? undefined}
  getLimits={getLimits}
  setLimits={setLimits}
  resetLimits={resetLimits}
/>
```

- [ ] **Step 3.4: Run full test suite**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npx vitest run
```

Expected: all tests pass

- [ ] **Step 3.5: Start dev server and manually verify**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && npm run dev
```

1. Open http://localhost:5780, navigate to Mannequin Editor
2. Click the rightHip joint on the canvas — its row group gets yellow highlight
3. Confirm `figureYawDeg` row shows `min` / `max` number inputs and `↺` button
4. Change max to 30 — slider range shrinks immediately
5. Drag slider past old 30° point — confirms it's clamped
6. Click `↺` — limits restore to −180/180
7. Reload page — custom limits reloaded from localStorage (set them again, reload, confirm)
8. Click a different joint (e.g. leftHip) — its `figureYawDeg` row has its own independent limits

- [ ] **Step 3.6: Commit**

```bash
git add src/components/MannequinEditor.tsx
git commit -m "feat: wire per-joint param limits into MannequinEditor"
```
