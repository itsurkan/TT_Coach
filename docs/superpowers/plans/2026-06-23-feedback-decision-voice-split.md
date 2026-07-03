# Feedback Decision / Voice Reproduction Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make one feedback decision drive both the on-screen table and the voice; reduce `Стиль голосу` to pure reproduction (voice + wording), move all policy knobs to `Налаштування`, and voice `hip_flexion`.

**Architecture:** A single pure function `decideRepCues(metrics, standard, settings)` becomes the only place that decides what's wrong with a rep (widened bands + significance threshold + enabled metrics + full coachable metric set incl. `hip_flexion`). The table renders all of its cues plus a voiced-only column; `buildSpokenSchedule` consumes the same cues and only layers cadence/praise/skip-stale + phrase rendering, returning which cue (if any) it actually spoke per rep. Policy lives in a new `FeedbackSettings` object (Налаштування); `VoiceStyle` keeps only reproduction fields.

**Tech Stack:** React + TypeScript + Vite + vitest (poses_viewer). No new dependencies.

## Global Constraints

- **poses_viewer-only.** This is the M1 feedback half, which "deliberately diverges from Kotlin and has NO shared/ counterpart" — do NOT touch `shared/` Kotlin or golden-parity tests.
- **UI strings stay Ukrainian** (`jointMap.ts`/viewer convention). Spoken phrases stay EN+UK per style.
- **Trust rule exception accepted:** `hip_flexion` becomes a voiced metric (deliberate product call for this simulator).
- **Commit hygiene:** `git add` explicit paths only — never `git add -A`.
- **Green gate each task:** `cd poses_viewer && npx tsc -b --noEmit` and `npx vitest run` must both pass before commit.
- **Detection path is frozen here:** the M0 stroke-count goldens must not change — only the feedback/voice layer changes.

---

### Task 1: Add `hip_flexion` to the metric model + phrases

**Files:**
- Modify: `poses_viewer/src/drill2d/voiceStyle.ts`
- Test: `poses_viewer/src/drill2d/__tests__/voiceStyle.test.ts`

**Interfaces:**
- Consumes: nothing new.
- Produces: `MetricKey` now includes `'hip_flexion'`; `VOICE_METRIC_KEYS` (the coachable set) includes `'hip_flexion'`; every `PhraseSet.cues` has a `hip_flexion: { up, down }`.

- [ ] **Step 1: Write the failing test**

Add to `voiceStyle.test.ts`:

```ts
import { PRESETS, VOICE_METRIC_KEYS } from '../voiceStyle'

describe('hip_flexion is a coachable, voiced metric', () => {
  it('is in VOICE_METRIC_KEYS', () => {
    expect(VOICE_METRIC_KEYS).toContain('hip_flexion')
  })
  it('every preset has EN + UK hip_flexion phrases', () => {
    for (const p of PRESETS) {
      expect(p.phrases.en.cues.hip_flexion.up.length).toBeGreaterThan(0)
      expect(p.phrases.en.cues.hip_flexion.down.length).toBeGreaterThan(0)
      expect(p.phrases.uk.cues.hip_flexion.up.length).toBeGreaterThan(0)
      expect(p.phrases.uk.cues.hip_flexion.down.length).toBeGreaterThan(0)
    }
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/voiceStyle.test.ts`
Expected: FAIL (`hip_flexion` not in `VOICE_METRIC_KEYS`; and `npx tsc` would error that phrase consts miss `hip_flexion`).

- [ ] **Step 3: Edit the model**

In `voiceStyle.ts`:
- Extend the type:
```ts
export type MetricKey = 'elbow_angle' | 'shoulder_angle' | 'knee_bend' | 'torso_lean' | 'shoulder_tilt' | 'hip_flexion'
```
- Update the constant + its doc:
```ts
/** The coachable metrics (drive both the table and the voice), fixed order for tie stability. */
export const VOICE_METRIC_KEYS: MetricKey[] = [
  'elbow_angle', 'shoulder_angle', 'knee_bend', 'torso_lean', 'shoulder_tilt', 'hip_flexion',
]
```
- Add a `hip_flexion` entry to the `cues` of ALL SIX phrase consts (TS will error until present):
  - `PLAYFUL_EN`: `hip_flexion: { up: 'ease the hips up a touch', down: 'sink into the hips' }`
  - `PLAYFUL_UK`: `hip_flexion: { up: 'трохи вище стегнами', down: 'присядь у стегнах' }`
  - `STRICT_EN`: `hip_flexion: { up: 'stand tall', down: 'hinge forward' }`
  - `STRICT_UK`: `hip_flexion: { up: 'вище', down: 'нахились у стегнах' }`
  - `EFFICIENT_EN`: `hip_flexion: { up: 'hips up', down: 'hinge' }`
  - `EFFICIENT_UK`: `hip_flexion: { up: 'вище', down: 'нахились' }`

- [ ] **Step 4: Run tests + typecheck to verify they pass**

Run: `cd poses_viewer && npx tsc -b --noEmit && npx vitest run src/drill2d/__tests__/voiceStyle.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/voiceStyle.ts poses_viewer/src/drill2d/__tests__/voiceStyle.test.ts
git commit -m "feat(viewer): hip_flexion becomes a coachable/voiced metric + phrases"
```

---

### Task 2: `FeedbackSettings` model + persistence

**Files:**
- Create: `poses_viewer/src/drill2d/feedbackSettings.ts`
- Test: `poses_viewer/src/drill2d/__tests__/feedbackSettings.test.ts`

**Interfaces:**
- Consumes: `MetricKey` from `voiceStyle.ts`.
- Produces: `interface FeedbackSettings`, `DEFAULT_FEEDBACK_SETTINGS`, `loadFeedbackSettings(): FeedbackSettings`, `saveFeedbackSettings(s: FeedbackSettings): void`, `FEEDBACK_SETTINGS_KEY`.

- [ ] **Step 1: Write the failing test**

Create `feedbackSettings.test.ts`:

```ts
import { describe, expect, it, beforeEach } from 'vitest'
import {
  DEFAULT_FEEDBACK_SETTINGS, loadFeedbackSettings, saveFeedbackSettings, FEEDBACK_SETTINGS_KEY,
} from '../feedbackSettings'

describe('feedbackSettings persistence', () => {
  beforeEach(() => localStorage.clear())

  it('returns defaults when nothing stored', () => {
    expect(loadFeedbackSettings()).toEqual(DEFAULT_FEEDBACK_SETTINGS)
  })
  it('round-trips a saved object', () => {
    const custom = { ...DEFAULT_FEEDBACK_SETTINGS, bandWidthMult: 0.8, enabledMetrics: ['knee_bend'] as const }
    saveFeedbackSettings(custom)
    expect(loadFeedbackSettings()).toEqual(custom)
  })
  it('falls back to defaults on corrupt JSON', () => {
    localStorage.setItem(FEEDBACK_SETTINGS_KEY, '{not json')
    expect(loadFeedbackSettings()).toEqual(DEFAULT_FEEDBACK_SETTINGS)
  })
  it('defaults include all six metrics enabled', () => {
    expect([...DEFAULT_FEEDBACK_SETTINGS.enabledMetrics].sort()).toEqual(
      ['elbow_angle', 'hip_flexion', 'knee_bend', 'shoulder_angle', 'shoulder_tilt', 'torso_lean'],
    )
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/feedbackSettings.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 3: Create the module**

`feedbackSettings.ts`:

```ts
/**
 * Feedback decision/policy — what & when to coach. Drives BOTH the on-screen table
 * and the voice (single source of truth). Reproduction (TTS voice + wording) lives
 * separately in VoiceStyle. Edited in the Налаштування panel; persisted on its own
 * localStorage key. Defaults seeded from the old "Efficient" preset numbers.
 */
import { VOICE_METRIC_KEYS, type MetricKey } from './voiceStyle'

export interface FeedbackSettings {
  enabledMetrics: MetricKey[]
  bandWidthMult: number
  minMeaningfulDeltaDeg: number
  reminderIntervalMs: number
  varyCues: boolean
  correctiveMinGapMs: number
  praiseMinSilenceMs: number
  postStrokeGapMs: number
  praiseEnabled: boolean
  praiseOnCorrection: boolean
  praiseOnStreak: boolean
  praiseStreakLen: number
  skipStaleEnabled: boolean
  skipStaleMarginMs: number
}

export const DEFAULT_FEEDBACK_SETTINGS: FeedbackSettings = {
  enabledMetrics: [...VOICE_METRIC_KEYS],
  bandWidthMult: 1.4,
  minMeaningfulDeltaDeg: 7,
  reminderIntervalMs: 10000,
  varyCues: true,
  correctiveMinGapMs: 5000,
  praiseMinSilenceMs: 10000,
  postStrokeGapMs: 300,
  praiseEnabled: true,
  praiseOnCorrection: true,
  praiseOnStreak: false,
  praiseStreakLen: 3,
  skipStaleEnabled: true,
  skipStaleMarginMs: 150,
}

export const FEEDBACK_SETTINGS_KEY = 'strokes_feedback_settings'

export function loadFeedbackSettings(): FeedbackSettings {
  if (typeof localStorage === 'undefined') return { ...DEFAULT_FEEDBACK_SETTINGS }
  try {
    const raw = localStorage.getItem(FEEDBACK_SETTINGS_KEY)
    if (!raw) return { ...DEFAULT_FEEDBACK_SETTINGS }
    const parsed = JSON.parse(raw) as Partial<FeedbackSettings>
    // Merge over defaults so a newly-added field is never undefined.
    return { ...DEFAULT_FEEDBACK_SETTINGS, ...parsed }
  } catch {
    return { ...DEFAULT_FEEDBACK_SETTINGS }
  }
}

export function saveFeedbackSettings(s: FeedbackSettings): void {
  if (typeof localStorage === 'undefined') return
  localStorage.setItem(FEEDBACK_SETTINGS_KEY, JSON.stringify(s))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx tsc -b --noEmit && npx vitest run src/drill2d/__tests__/feedbackSettings.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/feedbackSettings.ts poses_viewer/src/drill2d/__tests__/feedbackSettings.test.ts
git commit -m "feat(viewer): FeedbackSettings policy model + persistence"
```

---

### Task 3: `decideRepCues` — the single decision engine

**Files:**
- Create: `poses_viewer/src/drill2d/decideRepCues.ts`
- Test: `poses_viewer/src/drill2d/__tests__/decideRepCues.test.ts`

**Interfaces:**
- Consumes: `FeedbackCue` (`feedbackCue.ts`), `ReferenceStandard` (`referenceStandard.ts`), `FeedbackSettings` (`feedbackSettings.ts`), `precisionFor` (`metricPrecision.ts`), `MetricKey` (`voiceStyle.ts`).
- Produces: `decideRepCues(metrics: Record<string, number>, standard: ReferenceStandard, settings: FeedbackSettings): FeedbackCue[]` — all out-of-(widened-)band cues, severity-desc. With `bandWidthMult: 1, minMeaningfulDeltaDeg: 5, enabledMetrics: all` it is identical to the retired `evaluateRep`.

- [ ] **Step 1: Write the failing test**

Create `decideRepCues.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { decideRepCues } from '../decideRepCues'
import { FOREHAND_DRIVE_STANDARD } from '../referenceStandard'
import { DEFAULT_FEEDBACK_SETTINGS } from '../feedbackSettings'

const raw = { ...DEFAULT_FEEDBACK_SETTINGS, bandWidthMult: 1, minMeaningfulDeltaDeg: 5 }

describe('decideRepCues', () => {
  it('is silent inside the band', () => {
    expect(decideRepCues({ elbow_angle: 130 }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([]) // band 115–150
  })
  it('flags above the band as too_high', () => {
    const cues = decideRepCues({ elbow_angle: 168 }, FOREHAND_DRIVE_STANDARD, raw) // hi 150
    expect(cues[0].metricKey).toBe('elbow_angle')
    expect(cues[0].direction).toBe('too_high')
  })
  it('flags below the band as too_low', () => {
    const cues = decideRepCues({ knee_bend: 90 }, FOREHAND_DRIVE_STANDARD, raw) // lo 110
    expect(cues[0].direction).toBe('too_low')
  })
  it('respects minMeaningfulDeltaDeg', () => {
    expect(decideRepCues({ elbow_angle: 153 }, FOREHAND_DRIVE_STANDARD, raw)).toEqual([]) // dev 3 < 5
  })
  it('widens the band with bandWidthMult', () => {
    const wide = { ...raw, bandWidthMult: 1.4 } // elbow widened lo ≈ 108
    expect(decideRepCues({ elbow_angle: 110 }, FOREHAND_DRIVE_STANDARD, wide)).toEqual([]) // inside widened
    const cues = decideRepCues({ elbow_angle: 97 }, FOREHAND_DRIVE_STANDARD, wide) // ~11 under 108
    expect(cues[0].metricKey).toBe('elbow_angle')
    expect(cues[0].direction).toBe('too_low')
  })
  it('honours enabledMetrics', () => {
    const only = { ...raw, enabledMetrics: ['knee_bend'] as const }
    expect(decideRepCues({ elbow_angle: 168 }, FOREHAND_DRIVE_STANDARD, only)).toEqual([])
  })
  it('includes hip_flexion when out of band', () => {
    const cues = decideRepCues({ hip_flexion: 100 }, FOREHAND_DRIVE_STANDARD, raw) // band 130–165, lo 130
    expect(cues[0].metricKey).toBe('hip_flexion')
    expect(cues[0].direction).toBe('too_low')
  })
  it('sorts by severity descending', () => {
    const cues = decideRepCues({ elbow_angle: 170, knee_bend: 108 }, FOREHAND_DRIVE_STANDARD, raw)
    expect(cues[0].severity).toBeGreaterThanOrEqual(cues[1].severity)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/decideRepCues.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 3: Create the module**

`decideRepCues.ts`:

```ts
/**
 * The single feedback-decision engine. Compares each rep's metrics to the external
 * ideal bands, WIDENED by settings.bandWidthMult, and returns every out-of-band cue
 * (severity-desc). Drives BOTH the table ("Всі зауваження") and the voice
 * (buildSpokenSchedule). With bandWidthMult=1, minMeaningfulDeltaDeg=5 and all
 * metrics enabled it reproduces the retired feedbackEngine.evaluateRep exactly.
 */
import { FeedbackCue } from './feedbackCue'
import { precisionFor } from './metricPrecision'
import { ReferenceStandard } from './referenceStandard'
import { FeedbackSettings } from './feedbackSettings'
import { MetricKey } from './voiceStyle'

export function decideRepCues(
  metrics: Record<string, number>,
  standard: ReferenceStandard,
  settings: FeedbackSettings,
): FeedbackCue[] {
  const enabled = new Set<string>(settings.enabledMetrics as MetricKey[])
  const cues: FeedbackCue[] = []
  for (const [key, range] of Object.entries(standard.ranges)) {
    if (!enabled.has(key)) continue
    const value = metrics[key]
    if (value === undefined) continue
    const half = (range.hi - range.lo) / 2
    const center = (range.lo + range.hi) / 2
    const wLo = center - half * settings.bandWidthMult
    const wHi = center + half * settings.bandWidthMult
    let delta: number
    let direction: FeedbackCue['direction']
    if (value > wHi) { delta = value - wHi; direction = 'too_high' }
    else if (value < wLo) { delta = value - wLo; direction = 'too_low' }
    else continue
    if (Math.abs(delta) < settings.minMeaningfulDeltaDeg) continue
    const severity = half > 0 ? Math.abs(delta) / half : 0
    cues.push({ metricKey: key, direction, deltaFromRange: delta, severity, precision: precisionFor(key) })
  }
  return cues.sort((a, b) => b.severity - a.severity)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx tsc -b --noEmit && npx vitest run src/drill2d/__tests__/decideRepCues.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/drill2d/decideRepCues.ts poses_viewer/src/drill2d/__tests__/decideRepCues.test.ts
git commit -m "feat(viewer): decideRepCues — single feedback-decision engine"
```

---

### Task 4: Rewire voice core + analyzeDrill to the unified engine

**Files:**
- Modify: `poses_viewer/src/drill2d/buildSpokenSchedule.ts`
- Modify: `poses_viewer/src/drill2d/analyzeDrill.ts`
- Modify: `poses_viewer/src/drill2d/messageCatalog.ts` (add `hip_flexion` phrase)
- Modify: `poses_viewer/src/components/StrokesPage.tsx` (call new signature; temp settings)
- Delete: `poses_viewer/src/drill2d/feedbackEngine.ts`, `poses_viewer/src/drill2d/__tests__/feedbackEngine.test.ts`
- Test: rewrite `poses_viewer/src/drill2d/__tests__/buildSpokenSchedule.test.ts`; update `analyzeDrill.test.ts`, `analyzeExperiments.test.ts`, `messageCatalog.test.ts`

**Interfaces:**
- Consumes: `decideRepCues` (Task 3), `FeedbackSettings` (Task 2), `FeedbackCue`, `PhraseSet`/`Lang`/`MetricKey` (`voiceStyle.ts`).
- Produces:
  - `interface RepTiming { strokeStartMs: number; contactMs: number; strokeEndMs: number }`
  - `interface RepInput { cues: FeedbackCue[]; timing: RepTiming; coachable: boolean }`
  - `interface ScheduleResult { schedule: SpokenSchedule; voicedByRep: (FeedbackCue | null)[] }`
  - `buildSpokenSchedule(reps: RepInput[], strokeStartTimes: number[], settings: FeedbackSettings, phrases: PhraseSet, lang: Lang, rate: number, clipManifest?: ClipManifest | null): ScheduleResult`
  - `DrillAnalysisConfig` loses `enabledMetrics`, gains `feedbackSettings: FeedbackSettings`.
  - `DrillAnalysisReport.voiceReps: RepInput[]` (was `VoiceRep[]`); `RepAnalysis.cues` stays `FeedbackCue[]` (now from `decideRepCues`).

- [ ] **Step 1: Rewrite the voice-core test (failing)**

Replace the body of `buildSpokenSchedule.test.ts` (keep `estimateDurationMs`/`nextStrokeStartAfter` cases) with cue-fed cases:

```ts
import { describe, expect, it } from 'vitest'
import { buildSpokenSchedule, estimateDurationMs, nextStrokeStartAfter, BASE_WPM, type RepInput } from '../buildSpokenSchedule'
import { PRESETS } from '../voiceStyle'
import { DEFAULT_FEEDBACK_SETTINGS } from '../feedbackSettings'
import type { FeedbackCue } from '../feedbackCue'

const STRICT = PRESETS.find(p => p.id === 'preset-strict')!

function settings(o: Partial<typeof DEFAULT_FEEDBACK_SETTINGS> = {}) {
  return {
    ...DEFAULT_FEEDBACK_SETTINGS,
    correctiveMinGapMs: 0, praiseMinSilenceMs: 0, postStrokeGapMs: 0,
    reminderIntervalMs: 0, varyCues: false,
    praiseEnabled: false, praiseOnCorrection: false, praiseOnStreak: false,
    skipStaleEnabled: false, skipStaleMarginMs: 0,
    ...o,
  }
}
const cue = (metricKey: string, direction: FeedbackCue['direction'], severity = 1): FeedbackCue =>
  ({ metricKey, direction, deltaFromRange: direction === 'too_high' ? 10 : -10, severity, precision: 'precise_degrees' })
const rep = (startMs: number, cues: FeedbackCue[], coachable = true): RepInput =>
  ({ cues, timing: { strokeStartMs: startMs, contactMs: startMs + 200, strokeEndMs: startMs + 400 }, coachable })

describe('buildSpokenSchedule (unified cues)', () => {
  it('says nothing when a rep has no cues', () => {
    const { schedule, voicedByRep } = buildSpokenSchedule([rep(0, [])], [0], settings(), STRICT.phrases.en, 'en', STRICT.rate)
    expect(schedule).toEqual([])
    expect(voicedByRep).toEqual([null])
  })
  it('voices the top cue and records it in voicedByRep', () => {
    const { schedule, voicedByRep } = buildSpokenSchedule(
      [rep(0, [cue('elbow_angle', 'too_low')])], [0], settings(), STRICT.phrases.en, 'en', STRICT.rate)
    expect(schedule).toHaveLength(1)
    expect(schedule[0].metricKey).toBe('elbow_angle')
    expect(schedule[0].text).toBe(STRICT.phrases.en.cues.elbow_angle.down) // too_low → 'down' phrase
    expect(voicedByRep[0]?.metricKey).toBe('elbow_angle')
  })
  it('suppresses a cue inside correctiveMinGapMs and marks the rep null', () => {
    const reps = [rep(0, [cue('elbow_angle', 'too_low')]), rep(500, [cue('knee_bend', 'too_high')])]
    const { schedule, voicedByRep } = buildSpokenSchedule(
      reps, [0, 500], settings({ correctiveMinGapMs: 2000 }), STRICT.phrases.en, 'en', STRICT.rate)
    expect(schedule).toHaveLength(1)         // only the first fires
    expect(voicedByRep[0]?.metricKey).toBe('elbow_angle')
    expect(voicedByRep[1]).toBeNull()
  })
  it('never voices a cue the rep did not contain', () => {
    const { voicedByRep } = buildSpokenSchedule(
      [rep(0, [cue('knee_bend', 'too_high')])], [0], settings(), STRICT.phrases.en, 'en', STRICT.rate)
    expect(voicedByRep[0]?.metricKey).toBe('knee_bend')
  })
  it('leaves a non-coachable rep silent and null', () => {
    const { schedule, voicedByRep } = buildSpokenSchedule(
      [rep(0, [cue('elbow_angle', 'too_low')], false)], [0], settings(), STRICT.phrases.en, 'en', STRICT.rate)
    expect(schedule).toEqual([])
    expect(voicedByRep).toEqual([null])
  })
})

// keep the existing estimateDurationMs + nextStrokeStartAfter describe blocks unchanged
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/buildSpokenSchedule.test.ts`
Expected: FAIL (old signature / `RepInput` not exported).

- [ ] **Step 3: Rewrite `buildSpokenSchedule.ts`**

Replace `MetricObservation`/`VoiceRep`/`gateRep` with cue-fed inputs. Full new file:

```ts
/**
 * The pure voice core. Walks per-rep DECISION cues (from decideRepCues — already
 * band-gated) in time order and produces a deterministic spoken schedule:
 *   select → cadence → praise → skip-stale → format.
 * It never re-decides what's wrong (no band math here) — it only chooses which of a
 * rep's cues to voice and when. Returns voicedByRep so the table can show exactly
 * what was spoken per rep.
 */
import type { FeedbackCue } from './feedbackCue'
import type { FeedbackSettings } from './feedbackSettings'
import type { Lang, MetricKey, PhraseSet } from './voiceStyle'
import { clipKey, lookupClip, type ClipManifest } from './voiceClips'

/** Practitioner WPM budget: ~150 wpm ≈ 2.5 words/s. */
export const BASE_WPM = 150

export interface RepTiming { strokeStartMs: number; contactMs: number; strokeEndMs: number }
export interface RepInput {
  cues: FeedbackCue[]   // severity-desc, already gated by decideRepCues
  timing: RepTiming
  coachable: boolean    // false → bad camera / unmeasurable: emit nothing, state untouched
}

export interface SpokenFeedbackItem {
  atMs: number
  text: string
  lang: Lang
  kind: 'cue' | 'praise'
  metricKey: MetricKey | null
  clipKey?: string
  estDurationMs: number
}
export type SpokenSchedule = SpokenFeedbackItem[]
export interface ScheduleResult { schedule: SpokenSchedule; voicedByRep: (FeedbackCue | null)[] }

export function estimateDurationMs(text: string, rate: number): number {
  const words = text.trim().split(/\s+/).filter(Boolean).length
  const safeRate = rate > 0 ? rate : 1
  const wordsPerSec = (BASE_WPM * safeRate) / 60
  return wordsPerSec > 0 ? (words / wordsPerSec) * 1000 : 0
}

export function nextStrokeStartAfter(strokeStartTimes: number[], atMs: number): number {
  for (const t of strokeStartTimes) if (t > atMs) return t
  return Number.POSITIVE_INFINITY
}

const dirToPhrase = (d: FeedbackCue['direction']): 'up' | 'down' => (d === 'too_high' ? 'up' : 'down')

export function buildSpokenSchedule(
  reps: RepInput[],
  strokeStartTimes: number[],
  settings: FeedbackSettings,
  phrases: PhraseSet,
  lang: Lang,
  rate: number,
  clipManifest?: ClipManifest | null,
): ScheduleResult {
  const schedule: SpokenSchedule = []
  const voicedByRep: (FeedbackCue | null)[] = []

  let lastSpokenMs = Number.NEGATIVE_INFINITY
  let prevOutOfBand = new Set<string>()
  const lastCuedMs = new Map<string, number>()
  let lastCueMetric: string | null = null
  let cleanStreak = 0
  let praiseIndex = 0

  const durationOf = (text: string): { ms: number; key?: string } => {
    const clip = lookupClip(clipManifest ?? null, lang, text)
    if (clip) return { ms: clip.durationMs, key: clipKey(lang, text) }
    return { ms: estimateDurationMs(text, rate) }
  }
  const fits = (atMs: number, ms: number): boolean => {
    if (!settings.skipStaleEnabled) return true
    const next = nextStrokeStartAfter(strokeStartTimes, atMs)
    return atMs + ms <= next - settings.skipStaleMarginMs
  }

  for (const rep of reps) {
    if (!rep.coachable) { voicedByRep.push(null); continue }
    const atMs = rep.timing.strokeEndMs + settings.postStrokeGapMs
    const curOutOfBand = new Set<string>(rep.cues.map(c => c.metricKey))
    cleanStreak = curOutOfBand.size === 0 ? cleanStreak + 1 : 0

    // Praise candidate (correction wins if both qualify).
    let praiseCandidate = false
    if (settings.praiseEnabled) {
      const corrected = [...prevOutOfBand].some(k => !curOutOfBand.has(k))
      if (settings.praiseOnCorrection && corrected) praiseCandidate = true
      else if (settings.praiseOnStreak && cleanStreak > 0 && cleanStreak >= settings.praiseStreakLen) praiseCandidate = true
    }

    // Cue selection: cues are already severity-desc; drop reminder-suppressed, vary-aware.
    let chosen: FeedbackCue | null = null
    const eligible = rep.cues.filter(c => {
      const last = lastCuedMs.get(c.metricKey)
      return last === undefined || atMs - last >= settings.reminderIntervalMs
    })
    if (eligible.length > 0) {
      chosen = eligible[0]
      if (settings.varyCues && chosen.metricKey === lastCueMetric && eligible.length > 1) chosen = eligible[1]
    }

    let voiced: FeedbackCue | null = null
    if (chosen !== null) {
      if (atMs - lastSpokenMs >= settings.correctiveMinGapMs) {
        const text = phrases.cues[chosen.metricKey as MetricKey][dirToPhrase(chosen.direction)]
        const { ms, key } = durationOf(text)
        if (fits(atMs, ms)) {
          schedule.push({ atMs, text, lang, kind: 'cue', metricKey: chosen.metricKey as MetricKey, clipKey: key, estDurationMs: ms })
          lastSpokenMs = atMs
          lastCueMetric = chosen.metricKey
          lastCuedMs.set(chosen.metricKey, atMs)
          voiced = chosen
        }
      }
    } else if (praiseCandidate) {
      if (atMs - lastSpokenMs >= settings.praiseMinSilenceMs && phrases.praise.length > 0) {
        const text = phrases.praise[praiseIndex % phrases.praise.length]
        const { ms, key } = durationOf(text)
        if (fits(atMs, ms)) {
          schedule.push({ atMs, text, lang, kind: 'praise', metricKey: null, clipKey: key, estDurationMs: ms })
          lastSpokenMs = atMs
          praiseIndex++
        }
      }
    }

    voicedByRep.push(voiced)
    prevOutOfBand = curOutOfBand
  }

  return { schedule, voicedByRep }
}
```

- [ ] **Step 4: Run the voice-core test**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/buildSpokenSchedule.test.ts`
Expected: PASS.

- [ ] **Step 5: Update `analyzeDrill.ts`**

- Replace `import { evaluateRep } from './feedbackEngine'` with `import { decideRepCues } from './decideRepCues'` and `import type { FeedbackSettings } from './feedbackSettings'`.
- Replace `import type { VoiceRep, MetricObservation } from './buildSpokenSchedule'` with `import type { RepInput } from './buildSpokenSchedule'`.
- In `DrillAnalysisConfig`: delete `enabledMetrics?: Set<string>`; add `feedbackSettings: FeedbackSettings`.
- In `DrillAnalysisReport`: change `voiceReps: VoiceRep[]` → `voiceReps: RepInput[]`.
- The per-rep cue line:
```ts
const cues = placementOk ? decideRepCues(metrics, config.standard, config.feedbackSettings) : []
```
- The `unreliableMetricKeys` cue-filter block stays as-is (still filters `rep.cues`).
- Replace the `voiceReps` builder with:
```ts
const intervalMs = seq.intervalMs
const voiceReps: RepInput[] = repAnalyses.map(rep => ({
  cues: rep.cues, // already reliability-filtered above
  timing: {
    strokeStartMs: rep.stroke.startFrame * intervalMs,
    contactMs: rep.stroke.peakFrame * intervalMs,
    strokeEndMs: rep.stroke.endFrame * intervalMs,
  },
  coachable: rep.placementOk,
}))
const strokeStartTimes = voiceReps.map(r => r.timing.strokeStartMs)
```
- `sessionStrengths(...)` is called with `config.enabledMetrics` — change that call to `new Set(config.feedbackSettings.enabledMetrics)`. (`evaluateRep`'s old `config.enabledMetrics` arg in the cue line is gone — `decideRepCues` reads `settings.enabledMetrics` itself.)
- Delete the now-unused `VOICE_METRIC_KEYS`/`MetricKey`/`MetricObservation` imports if they become unused.

- [ ] **Step 6: Add the `hip_flexion` summary phrase to `messageCatalog.ts`**

In `phrase(metricKey, high)` add a case before `default`:
```ts
case 'hip_flexion':
  return high ? 'Hips higher than ideal — sit into them a touch'
              : 'Standing too tall — hinge forward into the hips'
```
Add to `messageCatalog.test.ts`:
```ts
it('formats a hip_flexion cue', () => {
  const up = formatCue({ metricKey: 'hip_flexion', direction: 'too_high', deltaFromRange: 8, severity: 1, precision: 'precise_degrees' })
  expect(up.toLowerCase()).toContain('hip')
})
```

- [ ] **Step 7: Fix the StrokesPage call site (temporary settings)**

In `StrokesPage.tsx`:
- Import: `import { DEFAULT_FEEDBACK_SETTINGS } from '../drill2d/feedbackSettings'` and `import type { MetricKey } from '../drill2d/voiceStyle'`.
- Add a memo deriving settings from the existing `enabledMetrics` state (Task 5 replaces this with real state):
```ts
const feedbackSettings = useMemo(
  () => ({ ...DEFAULT_FEEDBACK_SETTINGS, enabledMetrics: [...enabledMetrics] as MetricKey[] }),
  [enabledMetrics],
)
```
- In the `report` useMemo config, replace `enabledMetrics,` with `feedbackSettings,` and add `feedbackSettings` to its dep array.
- Replace the `schedule` memo + its `spoken` line:
```ts
const { schedule, voicedByRep } = useMemo(
  () => report
    ? buildSpokenSchedule(report.voiceReps, report.strokeStartTimes, feedbackSettings, activeStyle.phrases, activeStyle.lang, activeStyle.rate, manifest)
    : { schedule: [], voicedByRep: [] },
  [report, feedbackSettings, activeStyle, manifest],
)
const spoken = useSpokenFeedback(schedule, voiceProfileOf(activeStyle), manifest, muted)
```
- Update `exportDebugJson` `spokenSchedule` mapping reference is unchanged (`schedule` still in scope). `voicedByRep` is now in scope for Task 6.

- [ ] **Step 8: Delete the retired engine**

```bash
git rm poses_viewer/src/drill2d/feedbackEngine.ts poses_viewer/src/drill2d/__tests__/feedbackEngine.test.ts
```
Then update `analyzeExperiments.test.ts`: every `analyzeDrill(seq, { ... })` config that passed `enabledMetrics: <Set>` must pass `feedbackSettings: { ...DEFAULT_FEEDBACK_SETTINGS, bandWidthMult: 1, minMeaningfulDeltaDeg: 5, enabledMetrics: [...] }` instead (this reproduces the old raw-band behavior so expectations hold). Import `DEFAULT_FEEDBACK_SETTINGS`. Configs that passed no `enabledMetrics` must still add `feedbackSettings: { ...DEFAULT_FEEDBACK_SETTINGS, bandWidthMult: 1, minMeaningfulDeltaDeg: 5 }`.

- [ ] **Step 9: Update `analyzeDrill.test.ts` configs**

Add `import { DEFAULT_FEEDBACK_SETTINGS } from '../feedbackSettings'`. To every `analyzeDrill(seq, { ... })` config object add:
```ts
feedbackSettings: { ...DEFAULT_FEEDBACK_SETTINGS, bandWidthMult: 1, minMeaningfulDeltaDeg: 5 },
```
(raw-band equivalence keeps existing cue/voice expectations). Any test asserting on `report.voiceReps[i].observations` must switch to `report.voiceReps[i].cues` / `.timing` / `.coachable`. (The "voice inputs" describe block at line ~195: assert `voiceReps[i].coachable` and that `cues` is an array; drop `observations`-shape assertions.)

- [ ] **Step 10: Full green gate**

Run: `cd poses_viewer && npx tsc -b --noEmit && npx vitest run`
Expected: PASS (all suites). Fix any remaining `enabledMetrics`/`observations` references the compiler flags.

- [ ] **Step 11: Commit**

```bash
git add poses_viewer/src/drill2d/buildSpokenSchedule.ts poses_viewer/src/drill2d/analyzeDrill.ts \
  poses_viewer/src/drill2d/messageCatalog.ts poses_viewer/src/components/StrokesPage.tsx \
  poses_viewer/src/drill2d/__tests__/buildSpokenSchedule.test.ts \
  poses_viewer/src/drill2d/__tests__/analyzeDrill.test.ts \
  poses_viewer/src/drill2d/__tests__/analyzeExperiments.test.ts \
  poses_viewer/src/drill2d/__tests__/messageCatalog.test.ts
git rm poses_viewer/src/drill2d/feedbackEngine.ts poses_viewer/src/drill2d/__tests__/feedbackEngine.test.ts
git commit -m "refactor(viewer): unify table+voice on decideRepCues; voice core returns voicedByRep"
```

---

### Task 5: Move policy knobs into the Налаштування panel

**Files:**
- Create: `poses_viewer/src/components/controls.tsx` (shared `Slider`, `Toggle`, `secFmt`)
- Modify: `poses_viewer/src/components/StrokesPage.tsx` (real `feedbackSettings` state + Налаштування controls)
- Modify: `poses_viewer/src/components/VoiceStyleEditor.tsx` (remove Cadence/Bands/Praise/Skip-stale sections; import shared controls)

**Interfaces:**
- Consumes: `FeedbackSettings`, `loadFeedbackSettings`, `saveFeedbackSettings` (Task 2).
- Produces: shared `Slider`/`Toggle`/`secFmt` from `controls.tsx`; `feedbackSettings` is real `useState` in StrokesPage, persisted; `enabledMetrics` now lives in `feedbackSettings.enabledMetrics`.

- [ ] **Step 1: Extract shared controls**

Create `controls.tsx` by moving the `Slider`, `Toggle`, and `secFmt` definitions verbatim out of `VoiceStyleEditor.tsx` and exporting them:
```tsx
import { type ReactNode } from 'react'
export const secFmt = (v: number) => `${Math.round(v * 100) / 100} с`
export function Slider(props: { label: string; min: number; max: number; step: number; value: number; onChange: (v: number) => void; hint?: string; fmt?: (v: number) => string }) { /* moved body */ }
export function Toggle({ label, value, onChange, hint }: { label: string; value: boolean; onChange: (v: boolean) => void; hint?: string }) { /* moved body */ }
```
In `VoiceStyleEditor.tsx` delete those three local definitions and add `import { Slider, Toggle, secFmt } from './controls'` (the `Section` helper stays local or also moves — move `Section` too if Налаштування reuses it; otherwise keep local). Keep the editor compiling.

- [ ] **Step 2: Replace temp settings with real state in StrokesPage**

- Import `loadFeedbackSettings, saveFeedbackSettings` and the shared controls + `secFmt`.
- Remove the Task-4 temp `feedbackSettings` memo and the `enabledMetrics` `useState`.
- Add:
```ts
const [feedbackSettings, setFeedbackSettings] = useState(loadFeedbackSettings())
useEffect(() => { saveFeedbackSettings(feedbackSettings) }, [feedbackSettings])
function patchSettings(p: Partial<FeedbackSettings>) { setFeedbackSettings(s => ({ ...s, ...p })) }
```
- `enabledMetrics` references become `feedbackSettings.enabledMetrics` (an array). The Метрики checkbox block toggles via:
```ts
onChange={e => patchSettings({
  enabledMetrics: e.target.checked
    ? [...feedbackSettings.enabledMetrics, k as MetricKey]
    : feedbackSettings.enabledMetrics.filter(m => m !== k),
})}
checked={feedbackSettings.enabledMetrics.includes(k as MetricKey)}
```
- The `report`/`schedule` memos already read `feedbackSettings`; keep them. Update the reset `useEffect` dep that listed `enabledMetrics` → `feedbackSettings`.

- [ ] **Step 3: Add the moved control sections to the Налаштування fieldset**

Inside the Налаштування `<fieldset>` (after the existing detector controls, before/after Метрики as preferred) add, using the shared `Slider`/`Toggle`:
```tsx
<div className="border-t border-neutral-800 pt-2 text-neutral-400">Зони</div>
<Slider label="Ширина зони ×" min={0.5} max={2} step={0.05} value={feedbackSettings.bandWidthMult} onChange={v => patchSettings({ bandWidthMult: v })} hint="Множник допустимої зони навколо ідеалу. >1 поблажливіше, <1 суворіше." />
<Slider label="Поріг значущості (°)" min={0} max={20} step={1} value={feedbackSettings.minMeaningfulDeltaDeg} onChange={v => patchSettings({ minMeaningfulDeltaDeg: v })} hint="Мінімальне відхилення в градусах, яке варто озвучувати." />
<Slider label="Інтервал нагадування" min={0} max={20000} step={500} value={feedbackSettings.reminderIntervalMs} onChange={v => patchSettings({ reminderIntervalMs: v })} hint="Як часто повторювати підказку про ту саму проблему." />
<Toggle label="Чергувати підказки" value={feedbackSettings.varyCues} onChange={v => patchSettings({ varyCues: v })} hint="Чергувати метрику підказки, коли їх кілька." />

<div className="border-t border-neutral-800 pt-2 text-neutral-400">Каденс (с)</div>
<Slider label="Пауза між підказками" min={0} max={10} step={0.1} value={feedbackSettings.correctiveMinGapMs / 1000} onChange={v => patchSettings({ correctiveMinGapMs: Math.round(v * 1000) })} fmt={secFmt} hint="Мінімальний час між виправними підказками." />
<Slider label="Тиша перед похвалою" min={0} max={15} step={0.1} value={feedbackSettings.praiseMinSilenceMs / 1000} onChange={v => patchSettings({ praiseMinSilenceMs: Math.round(v * 1000) })} fmt={secFmt} hint="Скільки тиші перед похвалою." />
<Slider label="Пауза після удару" min={0} max={1.5} step={0.05} value={feedbackSettings.postStrokeGapMs / 1000} onChange={v => patchSettings({ postStrokeGapMs: Math.round(v * 1000) })} fmt={secFmt} hint="Затримка після удару перед озвученням." />

<div className="border-t border-neutral-800 pt-2 text-neutral-400">Похвала</div>
<Toggle label="Увімкнено" value={feedbackSettings.praiseEnabled} onChange={v => patchSettings({ praiseEnabled: v })} />
<Toggle label="За виправлення" value={feedbackSettings.praiseOnCorrection} onChange={v => patchSettings({ praiseOnCorrection: v })} />
<Toggle label="За серію" value={feedbackSettings.praiseOnStreak} onChange={v => patchSettings({ praiseOnStreak: v })} />
<Slider label="Довжина серії" min={1} max={10} step={1} value={feedbackSettings.praiseStreakLen} onChange={v => patchSettings({ praiseStreakLen: v })} />

<div className="border-t border-neutral-800 pt-2 text-neutral-400">Пропуск застарілих</div>
<Toggle label="Увімкнено" value={feedbackSettings.skipStaleEnabled} onChange={v => patchSettings({ skipStaleEnabled: v })} hint="Не озвучувати, якщо вже починається наступний удар." />
<Slider label="Запас перед ударом (мс)" min={0} max={1000} step={50} value={feedbackSettings.skipStaleMarginMs} onChange={v => patchSettings({ skipStaleMarginMs: v })} />
```

- [ ] **Step 4: Strip the moved sections from VoiceStyleEditor**

Delete the `{/* Cadence */}`, `{/* Bands */}`, `{/* Praise */}`, and `{/* Skip-stale */}` `<Section>` blocks from `VoiceStyleEditor.tsx`. Keep only the header (select/clone/rename/delete/export/import + Test voice), the `{/* Voice */}` section (lang/voice/rate/pitch/volume), and the `{/* Phrases */}` section. The editor no longer reads cadence/band/praise fields.

- [ ] **Step 5: Green gate + manual check**

Run: `cd poses_viewer && npx tsc -b --noEmit && npx vitest run`
Expected: PASS. (No new unit test — this is UI wiring; behavior is covered by Tasks 3–4.)
Manual: `npm run dev`, open `#/strokes`, confirm the policy controls now live under Налаштування and Стиль голосу shows only Voice + Phrases.

- [ ] **Step 6: Commit**

```bash
git add poses_viewer/src/components/controls.tsx poses_viewer/src/components/StrokesPage.tsx poses_viewer/src/components/VoiceStyleEditor.tsx
git commit -m "feat(viewer): move feedback policy knobs to Налаштування; voice editor = reproduction only"
```

---

### Task 6: Two table columns — all cues vs voiced-only

**Files:**
- Modify: `poses_viewer/src/components/DrillResultsTable.tsx`
- Modify: `poses_viewer/src/components/StrokesPage.tsx` (pass `voicedByRep`)

**Interfaces:**
- Consumes: `voicedByRep: (FeedbackCue | null)[]` from the schedule (Task 4), `RepAnalysis.cues` (FeedbackCue[]).
- Produces: table renders **Всі зауваження** (all cue metric keys) + **Підказка** (voiced cue metric key or "—").

- [ ] **Step 1: Add the prop + columns**

In `DrillResultsTable.tsx`:
- Extend `Props` with `voicedByRep?: (FeedbackCue | null)[]` (import `FeedbackCue` from `../drill2d/feedbackCue`).
- In the header row, where the single `<th>Підказка</th>` is (line ~158), make it two headers:
```tsx
<th className="py-1 px-2">Всі зауваження</th>
<th className="py-1 px-2">Підказка</th>
```
Add a matching empty `<th className="px-2 pb-1" />` in the sub-header row so column counts line up.
- In the body row, replace the single Підказка `<td>` (lines ~229–235) with:
```tsx
<td className="py-1 px-2 text-neutral-300">
  {!rep.placementOk ? '⚠ перевір кут камери (placement)'
    : rep.cues.length > 0 ? rep.cues.map(c => c.metricKey).join(', ')
    : '✓'}
</td>
<td className="py-1 px-2 text-sky-300">
  {voicedByRep?.[i]?.metricKey ?? '—'}
</td>
```
- Remove the now-unused `const top = rep.cues[0]`.

- [ ] **Step 2: Pass voicedByRep from StrokesPage**

In the `<DrillResultsTable ... />` JSX, add `voicedByRep={voicedByRep}`.

- [ ] **Step 3: Green gate + manual check**

Run: `cd poses_viewer && npx tsc -b --noEmit && npx vitest run`
Expected: PASS.
Manual: `#/strokes` — "Всі зауваження" lists every detected metric per rep; "Підказка" shows only what was voiced (or "—" when cadence suppressed / clean).

- [ ] **Step 4: Commit**

```bash
git add poses_viewer/src/components/DrillResultsTable.tsx poses_viewer/src/components/StrokesPage.tsx
git commit -m "feat(viewer): table shows all detected cues + voiced-only Підказка"
```

---

### Task 7: Slim the `VoiceStyle` model + store migration + debug export

**Files:**
- Modify: `poses_viewer/src/drill2d/voiceStyle.ts` (remove policy fields from interface + PRESETS)
- Modify: `poses_viewer/src/drill2d/voiceStyleStore.ts` (normalize on load: strip legacy policy, backfill hip phrases; fix `importStyle`)
- Modify: `poses_viewer/src/components/StrokesPage.tsx` (`exportDebugJson` shape)
- Test: `poses_viewer/src/drill2d/__tests__/voiceStyle.test.ts`, `voiceStyleStore.test.ts`

**Interfaces:**
- Consumes: `DEFAULT_FEEDBACK_SETTINGS` (export shape).
- Produces: `VoiceStyle` = `{ id, name, builtin, lang, voiceURI, rate, pitch, volume, phrases }`; `normalizeStyle(raw): VoiceStyle` backfilling hip phrases + stripping unknown fields.

- [ ] **Step 1: Write the failing store test**

Add to `voiceStyleStore.test.ts`:
```ts
import { normalizeStyle } from '../voiceStyleStore'
import { PRESETS } from '../voiceStyle'

describe('normalizeStyle migration', () => {
  it('strips legacy policy fields', () => {
    const legacy = { ...PRESETS[0], bandWidthMult: 1.4, correctiveMinGapMs: 5000 } as Record<string, unknown>
    const n = normalizeStyle(legacy) as Record<string, unknown>
    expect(n.bandWidthMult).toBeUndefined()
    expect(n.correctiveMinGapMs).toBeUndefined()
    expect(n.rate).toBeDefined()
  })
  it('backfills missing hip_flexion phrases from the default preset', () => {
    const noHip = JSON.parse(JSON.stringify(PRESETS[0]))
    delete noHip.phrases.en.cues.hip_flexion
    delete noHip.phrases.uk.cues.hip_flexion
    const n = normalizeStyle(noHip)
    expect(n.phrases.en.cues.hip_flexion.up.length).toBeGreaterThan(0)
    expect(n.phrases.uk.cues.hip_flexion.down.length).toBeGreaterThan(0)
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/voiceStyleStore.test.ts`
Expected: FAIL (`normalizeStyle` not exported).

- [ ] **Step 3: Slim the model**

In `voiceStyle.ts`, reduce `VoiceStyle` to:
```ts
export interface VoiceStyle {
  id: string; name: string; builtin: boolean
  lang: Lang; voiceURI: string | null; rate: number; pitch: number; volume: number
  phrases: Record<Lang, PhraseSet>
}
```
Delete the cadence/band/praise/skip-stale fields from each of the three `PRESETS` entries (keep `id, name, builtin, lang, voiceURI, rate, pitch, volume, phrases`). `VoiceProfile`/`voiceProfileOf` stay unchanged.

- [ ] **Step 4: Add `normalizeStyle` + fix `importStyle` + load**

In `voiceStyleStore.ts`:
```ts
import { PRESETS, type VoiceStyle, type Lang, VOICE_METRIC_KEYS } from './voiceStyle'

const REPRO_DEFAULT = PRESETS.find(p => p.id === DEFAULT_ACTIVE_ID) ?? PRESETS[0]

/** Keep only reproduction fields; backfill any missing cue phrases (e.g. hip_flexion). */
export function normalizeStyle(raw: VoiceStyle): VoiceStyle {
  const phrases = JSON.parse(JSON.stringify(raw.phrases ?? REPRO_DEFAULT.phrases)) as VoiceStyle['phrases']
  for (const lang of ['en', 'uk'] as Lang[]) {
    phrases[lang] ??= JSON.parse(JSON.stringify(REPRO_DEFAULT.phrases[lang]))
    phrases[lang].cues ??= JSON.parse(JSON.stringify(REPRO_DEFAULT.phrases[lang].cues))
    for (const k of VOICE_METRIC_KEYS) {
      phrases[lang].cues[k] ??= { ...REPRO_DEFAULT.phrases[lang].cues[k] }
    }
    phrases[lang].praise ??= [...REPRO_DEFAULT.phrases[lang].praise]
  }
  return {
    id: raw.id, name: raw.name, builtin: !!raw.builtin,
    lang: raw.lang ?? REPRO_DEFAULT.lang,
    voiceURI: raw.voiceURI ?? null,
    rate: raw.rate ?? REPRO_DEFAULT.rate,
    pitch: raw.pitch ?? REPRO_DEFAULT.pitch,
    volume: raw.volume ?? REPRO_DEFAULT.volume,
    phrases,
  }
}
```
- In `loadUserStyles`, map stored styles through `normalizeStyle`:
```ts
const styles = Array.isArray(parsed.styles) ? parsed.styles.filter(s => !s.builtin).map(normalizeStyle) : []
```
- In `importStyle`, drop the `typeof parsed.bandWidthMult !== 'number'` validation clause, and return `normalizeStyle({ ...(parsed as VoiceStyle), id, builtin: false })`.
- In `cloneStyle`, the existing deep-copy is fine (source is already normalized).

- [ ] **Step 5: Update the debug export shape**

In `StrokesPage.tsx` `exportDebugJson`, replace the detector-only/voiceStyle dump with the split shape:
```ts
feedbackSettings,
voiceStyle: { id: activeStyle.id, name: activeStyle.name, lang: activeStyle.lang, rate: activeStyle.rate, pitch: activeStyle.pitch, volume: activeStyle.volume, phrases: activeStyle.phrases },
```
and in the per-rep `report.reps.map`, replace `cues: r.cues` with both:
```ts
allCues: r.cues.map(c => ({ metricKey: c.metricKey, direction: c.direction, deltaFromRange: c.deltaFromRange, severity: c.severity })),
voicedCue: voicedByRep[i] ? { metricKey: voicedByRep[i]!.metricKey, direction: voicedByRep[i]!.direction } : null,
```
(Remove the old top-level `enabledMetrics`/`muted` only if redundant — `enabledMetrics` now lives in `feedbackSettings`; keep `muted`.)

- [ ] **Step 6: Update model tests**

In `voiceStyle.test.ts` / `voiceStyleStore.test.ts`, remove any assertions on removed fields (`bandWidthMult`, `correctiveMinGapMs`, etc.). The `importStyle` test fixture must no longer require `bandWidthMult`; ensure it still validates `name` + `phrases`.

- [ ] **Step 7: Full green gate**

Run: `cd poses_viewer && npx tsc -b --noEmit && npx vitest run`
Expected: PASS (whole suite).

- [ ] **Step 8: Commit**

```bash
git add poses_viewer/src/drill2d/voiceStyle.ts poses_viewer/src/drill2d/voiceStyleStore.ts \
  poses_viewer/src/components/StrokesPage.tsx \
  poses_viewer/src/drill2d/__tests__/voiceStyle.test.ts poses_viewer/src/drill2d/__tests__/voiceStyleStore.test.ts
git commit -m "refactor(viewer): VoiceStyle = reproduction only; migrate stored styles + debug export"
```

---

### Task 8: Documentation

**Files:**
- Modify: `poses_viewer/CLAUDE.md`

- [ ] **Step 1: Update the M1 section**

In the `drill2d/` + `StrokesPage` description, document the split:
- One decision engine `decideRepCues.ts` (widened bands via `FeedbackSettings`, all coachable metrics incl. `hip_flexion`) drives BOTH the table and the voice.
- `feedbackSettings.ts` (new) holds policy; persisted at `strokes_feedback_settings`; edited in Налаштування.
- `VoiceStyle` is reproduction-only (voice + phrases); `buildSpokenSchedule(reps: RepInput[], …, settings, phrases, lang, rate)` returns `{ schedule, voicedByRep }`.
- `feedbackEngine.ts` removed (replaced by `decideRepCues`).
- Table columns: «Всі зауваження» (all detected cues) vs «Підказка» (voiced-only).
- `hip_flexion` is now voiced — note the deliberate trust-rule exception.

- [ ] **Step 2: Commit**

```bash
git add poses_viewer/CLAUDE.md
git commit -m "docs(viewer): document feedback-decision / voice-reproduction split"
```

---

## Self-Review notes

- **Spec coverage:** §1 model split → Tasks 1,2,7; §2 single engine → Task 3 + wiring in 4; §3 analyzeDrill output → Task 4; §4 buildSpokenSchedule signature → Task 4; §5 UI (Налаштування + editor strip) → Task 5; two-column table → Task 6; hip phrases → Tasks 1,4; migration → Task 7; debug export → Task 7; docs → Task 8. All covered.
- **Behavior-preservation trick:** `decideRepCues` with `bandWidthMult:1, minMeaningfulDeltaDeg:5` equals the retired `evaluateRep`, so existing `analyzeDrill`/`analyzeExperiments` expectations hold by passing those explicit settings; the app default (1.4 / 7) intentionally differs.
- **Type consistency:** `RepInput`/`RepTiming`/`ScheduleResult`/`voicedByRep` defined in Task 4 are consumed verbatim in Tasks 5–7; `FeedbackSettings` field names match across Tasks 2/3/4/5; `voicedByRep` element type `FeedbackCue | null` is consistent table↔schedule.
- **Detection untouched:** no task edits the detect→forward→rep chain; M0 goldens unaffected.
