# Configurable Voice-Feedback System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the poses_viewer's hard-coded EN-only spoken feedback into a config-driven voice engine whose every parameter is editable through a visual editor, with named custom voice styles and deterministic pre-rendered audio clips.

**Architecture:** Four layers — (1) **config** (`voiceStyle.ts` types + 3 immutable presets, `voiceStyleStore.ts` CRUD/persistence, `voiceClips.ts` clip key+manifest); (2) **pure voice core** (`buildSpokenSchedule.ts`: gate → praise → select → cadence → skip-stale → format); (3) **analysis** (`analyzeDrill.ts` emits style-independent `voiceReps[]` + `strokeStartTimes`); (4) **playback** (`useSpokenFeedback.ts` clip-or-live + barge-in). Analysis is cached and style-independent, so editing a style re-runs only the cheap pure scheduler.

**Tech Stack:** React 18 + TypeScript + Vite 6, vitest 4 (`environment: 'node'`, `globals: true`, jsdom available per-file), Web Speech API + HTMLAudioElement, optional `tsx` for the offline generation script. Zero new runtime deps for the app itself.

**Spec:** [docs/superpowers/specs/2026-06-20-voice-feedback-system-design.md](../specs/2026-06-20-voice-feedback-system-design.md)

## Global Constraints

- **TS-only; no Kotlin `shared/` changes.** Everything lands under `poses_viewer/`. (spec §2)
- **No runtime cloud-TTS.** Cloud TTS is invoked only by the offline generation script; the app never calls it at runtime. (spec §2)
- **Trust rule — no numbers in voice.** The voice layer never injects a degree number; phrases are plain imperatives. Degree numbers stay only in `DrillResultsTable`. (spec §5)
- **Band basis stays the viewer's external IDEAL range** (not the personal baseline); `bandWidthMult` scales it. (spec §2)
- **Viewer UI strings are Ukrainian** (poses_viewer convention); code/identifiers English.
- **`clipKey` must be byte-identical between `voiceClips.ts` and `generateVoiceClips.ts`** (djb2 over normalized text). (spec §8)
- **Determinism:** identical `(reps, strokeStartTimes, style, manifest)` → identical schedule (required for live preview). (spec §11)
- **Verification each task:** `cd poses_viewer && npm test` (vitest run) green, and `npx tsc -b --noEmit` clean. UI/script tasks add viewer-QA where noted.
- **Commit hygiene:** `git add` explicit paths only (never `-A`); commit after each task. End commit messages with the `Co-Authored-By` trailer.
- **The 5 voiced metrics** (`MetricKey`) are exactly `elbow_angle`, `shoulder_angle`, `knee_bend`, `torso_lean`, `shoulder_tilt`. `hip_flexion` is analysis-only and is NOT voiced.

---

## File structure

**New files**

| File | Responsibility |
|---|---|
| `poses_viewer/src/drill2d/voiceClips.ts` | `clipKey`/`normalizeText`/`hashText`, `ClipEntry`/`ClipManifest`, `lookupClip` (freshness), `loadManifest` (fetch) |
| `poses_viewer/src/drill2d/voiceStyle.ts` | `MetricKey`, `Lang`, `PhraseSet`, `VoiceStyle`, `VoiceProfile`, `VOICE_METRIC_KEYS`, `voiceProfileOf`, `PRESETS` (3 immutable, EN+UA seed phrases) |
| `poses_viewer/src/drill2d/buildSpokenSchedule.ts` | pure core: `VoiceRep`/`MetricObservation`/`SpokenFeedbackItem`/`SpokenSchedule`, `BASE_WPM`, `estimateDurationMs`, `nextStrokeStartAfter`, `buildSpokenSchedule` |
| `poses_viewer/src/drill2d/voiceStyleStore.ts` | pure ops (`resolveActiveId`, `cloneStyle`, `renameStyle`, `removeStyle`, `upsertStyle`, `serializeStyle`, `importStyle`) + localStorage (`loadUserStyles`, `saveUserStyles`, `getActiveStyle`, `newStyleId`) |
| `poses_viewer/src/components/VoiceStyleEditor.tsx` | visual editor + "Test voice" (not unit-tested) |
| `poses_viewer/scripts/generateVoiceClips.ts` | offline clip generation (pluggable cloud-TTS) → `public/voice/<styleId>/` |
| `poses_viewer/src/drill2d/__tests__/voiceClips.test.ts` | clipKey stability/normalization, manifest lookup + freshness |
| `poses_viewer/src/drill2d/__tests__/voiceStyle.test.ts` | preset integrity (ids, builtin, phrase coverage) |
| `poses_viewer/src/drill2d/__tests__/buildSpokenSchedule.test.ts` | the core: gate/praise/select/cadence/skip-stale/format/determinism |
| `poses_viewer/src/drill2d/__tests__/voiceStyleStore.test.ts` | CRUD, persistence, export/import (jsdom env) |

**Changed files**

| File | Change |
|---|---|
| `poses_viewer/src/drill2d/analyzeDrill.ts` | add `voiceReps: VoiceRep[]` + `strokeStartTimes: number[]` to report (Task 5); remove the old `feedback` path + `SpokenFeedback`/`pickVariedCue`/`REMINDER_INTERVAL_MS` + `FeedbackCadencePolicy` use (Task 8) |
| `poses_viewer/src/components/useSpokenFeedback.ts` | rewrite to consume `SpokenSchedule` + `VoiceProfile` + `ClipManifest`; clip-or-live; barge-in (Task 6) |
| `poses_viewer/src/components/StrokesPage.tsx` | load active style, build schedule, new playback hook, mute toggle (Task 6); mount `VoiceStyleEditor` (Task 7) |
| `poses_viewer/src/drill2d/__tests__/analyzeDrill.test.ts` | add voiceReps assertions (Task 5); drop feedback assertions (Task 8) |
| `poses_viewer/src/drill2d/__tests__/analyzeExperiments.test.ts` | drop EXP-1 `pickVariedCue` block (Task 8) |
| `poses_viewer/src/components/__tests__/useSpokenFeedback.test.ts` | retype `newlyCrossed` to `SpokenFeedbackItem`/`atMs` (Task 6) |
| `poses_viewer/package.json` | add `tsx` devDep + `gen:voice` script (Task 9) |
| `poses_viewer/CLAUDE.md` | document the voice layer (Task 8) |

**Deleted files**

| File | Reason |
|---|---|
| `poses_viewer/src/drill2d/cadencePolicy.ts` | cadence absorbed into the voice core (Task 8) |
| `poses_viewer/src/drill2d/__tests__/cadencePolicy.test.ts` | tests the deleted module (Task 8) |

**Kept unchanged (intentionally):** `feedbackEngine.ts` (`evaluateRep` still feeds `rep.cues` for the table + `sessionFocus`/`sessionStrengths`), `messageCatalog.ts` (`formatCue`/`positiveMessage` still feed the on-screen session summary), `DrillResultsTable.tsx` (renders `rep.cues[0].metricKey`, unaffected), `metricPrecision.ts`, `referenceStandard.ts`.

> **Note on spec §12 vs this plan:** the spec says `messageCatalog`/`feedbackEngine` are "absorbed/parameterized". In practice they still power the *on-screen session summary* (`sessionFocus`/`sessionStrengths`), which is a separate, style-independent concern. The plan therefore leaves them intact and only re-routes the *voice* path through styles. This is the minimal-churn decomposition that keeps the analysis layer green.

---

## Task 1: `voiceClips.ts` — clip key, manifest, freshness

**Files:**
- Create: `poses_viewer/src/drill2d/voiceClips.ts`
- Test: `poses_viewer/src/drill2d/__tests__/voiceClips.test.ts`

**Interfaces:**
- Consumes: nothing. This module is intentionally **dependency-free** so it can be built and tested standalone (and reused verbatim by the Node generation script in Task 9). It uses a *local* `type Lang = 'en' | 'uk'` rather than importing from `voiceStyle.ts`; that union is structurally identical to the canonical `Lang` (`voiceStyle.ts`, Task 2), so values flow between the modules without friction.
- Produces: `clipKey(lang, text): string`, `normalizeText(text): string`, `hashText(text): string`, `lookupClip(manifest, lang, text): ClipEntry | null`, `loadManifest(styleId): Promise<ClipManifest | null>`, types `ClipEntry`, `ClipManifest`.

- [ ] **Step 1: Write the failing test**

`poses_viewer/src/drill2d/__tests__/voiceClips.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { clipKey, normalizeText, hashText, lookupClip, type ClipManifest } from '../voiceClips'

describe('normalizeText', () => {
  it('lowercases, trims, and collapses internal whitespace', () => {
    expect(normalizeText('  Зігни   Лікоть ')).toBe('зігни лікоть')
    expect(normalizeText('Bend   Elbow')).toBe('bend elbow')
  })
})

describe('hashText', () => {
  it('is deterministic and differs across inputs', () => {
    expect(hashText('bend elbow')).toBe(hashText('bend elbow'))
    expect(hashText('bend elbow')).not.toBe(hashText('extend arm'))
  })
})

describe('clipKey', () => {
  it('is stable under normalization (whitespace/case)', () => {
    expect(clipKey('uk', 'Зігни  лікоть ')).toBe(clipKey('uk', 'зігни лікоть'))
  })
  it('is language-scoped', () => {
    expect(clipKey('en', 'level shoulders')).not.toBe(clipKey('uk', 'level shoulders'))
  })
})

describe('lookupClip', () => {
  const manifest: ClipManifest = {
    styleId: 'preset-strict',
    clips: {
      [clipKey('en', 'bend elbow')]: { file: 'a.mp3', durationMs: 600, text: 'bend elbow', lang: 'en' },
    },
  }
  it('returns the entry for a fresh (matching-text) clip', () => {
    expect(lookupClip(manifest, 'en', 'bend elbow')?.durationMs).toBe(600)
  })
  it('returns null for a missing key', () => {
    expect(lookupClip(manifest, 'en', 'extend arm')).toBeNull()
  })
  it('returns null when manifest is null', () => {
    expect(lookupClip(null, 'en', 'bend elbow')).toBeNull()
  })
  it('returns null for a stale entry whose stored text no longer matches the key collision', () => {
    const stale: ClipManifest = {
      styleId: 'x',
      clips: { [clipKey('en', 'bend elbow')]: { file: 'a.mp3', durationMs: 600, text: 'different text', lang: 'en' } },
    }
    expect(lookupClip(stale, 'en', 'bend elbow')).toBeNull()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/voiceClips.test.ts`
Expected: FAIL — `Cannot find module '../voiceClips'`.

- [ ] **Step 3: Write the implementation**

`poses_viewer/src/drill2d/voiceClips.ts`:
```ts
/**
 * Deterministic clip lookup for the voice layer. The clip key is derived purely
 * from (lang, normalized text), so an edited phrase yields no matching key and the
 * playback layer falls back to live TTS until the clip is regenerated. The djb2
 * hash here MUST stay byte-identical to scripts/generateVoiceClips.ts.
 */

// Local structural alias (kept dependency-free on purpose; canonical Lang lives in voiceStyle.ts).
type Lang = 'en' | 'uk'

export interface ClipEntry {
  file: string
  durationMs: number
  text: string
  lang: Lang
}

export interface ClipManifest {
  styleId: string
  clips: Record<string, ClipEntry>
}

/** Lowercase, trim, and collapse internal whitespace — the canonical form keys hash over. */
export function normalizeText(text: string): string {
  return text.trim().toLowerCase().replace(/\s+/g, ' ')
}

/** djb2 (xor variant), kept unsigned per step, base36. Mirrored in generateVoiceClips.ts. */
export function hashText(text: string): string {
  let h = 5381
  for (let i = 0; i < text.length; i++) {
    h = ((h * 33) ^ text.charCodeAt(i)) >>> 0
  }
  return h.toString(36)
}

export function clipKey(lang: Lang, text: string): string {
  return `${lang}__${hashText(normalizeText(text))}`
}

/**
 * The clip for (lang, text) if present AND fresh. Freshness = the stored text still
 * normalizes to the same string we are looking up (guards against hash collisions
 * and any future key-scheme drift).
 */
export function lookupClip(manifest: ClipManifest | null, lang: Lang, text: string): ClipEntry | null {
  if (!manifest) return null
  const entry = manifest.clips[clipKey(lang, text)]
  if (!entry) return null
  if (normalizeText(entry.text) !== normalizeText(text)) return null
  return entry
}

/** Best-effort fetch of a style's manifest; missing/erroring → null (all-live playback). */
export async function loadManifest(styleId: string): Promise<ClipManifest | null> {
  try {
    const res = await fetch(`/voice/${encodeURIComponent(styleId)}/manifest.json`)
    if (!res.ok) return null
    return (await res.json()) as ClipManifest
  } catch {
    return null
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/voiceClips.test.ts`
Expected: PASS (all assertions). This module has no imports, so it builds standalone.

- [ ] **Step 5: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/drill2d/voiceClips.ts poses_viewer/src/drill2d/__tests__/voiceClips.test.ts
git commit -m "feat(viewer): voiceClips — deterministic clip key, manifest lookup, freshness

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `voiceStyle.ts` — types, presets, seed phrases

**Files:**
- Create: `poses_viewer/src/drill2d/voiceStyle.ts`
- Test: `poses_viewer/src/drill2d/__tests__/voiceStyle.test.ts`

**Interfaces:**
- Consumes: nothing (leaf module).
- Produces: types `MetricKey`, `Lang`, `PhraseSet`, `VoiceStyle`, `VoiceProfile`; const `VOICE_METRIC_KEYS: MetricKey[]`; fn `voiceProfileOf(style): VoiceProfile`; const `PRESETS: VoiceStyle[]` (ids `preset-playful`, `preset-strict`, `preset-efficient`).

- [ ] **Step 1: Write the failing test**

`poses_viewer/src/drill2d/__tests__/voiceStyle.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { PRESETS, VOICE_METRIC_KEYS, voiceProfileOf, type Lang } from '../voiceStyle'

const LANGS: Lang[] = ['en', 'uk']

describe('PRESETS', () => {
  it('ships exactly the three built-in presets with stable ids', () => {
    expect(PRESETS.map(p => p.id)).toEqual(['preset-playful', 'preset-strict', 'preset-efficient'])
  })
  it('marks every preset builtin', () => {
    expect(PRESETS.every(p => p.builtin)).toBe(true)
  })
  it('covers every voiced metric (up+down) and a non-empty praise pool in EN and UK', () => {
    for (const p of PRESETS) {
      for (const lang of LANGS) {
        const set = p.phrases[lang]
        expect(set, `${p.id}/${lang}`).toBeDefined()
        expect(set.praise.length).toBeGreaterThan(0)
        for (const key of VOICE_METRIC_KEYS) {
          expect(set.cues[key].up.length, `${p.id}/${lang}/${key}.up`).toBeGreaterThan(0)
          expect(set.cues[key].down.length, `${p.id}/${lang}/${key}.down`).toBeGreaterThan(0)
        }
      }
    }
  })
  it('never embeds a digit in any phrase (trust rule: no spoken numbers)', () => {
    for (const p of PRESETS) {
      for (const lang of LANGS) {
        const all = [
          ...VOICE_METRIC_KEYS.flatMap(k => [p.phrases[lang].cues[k].up, p.phrases[lang].cues[k].down]),
          ...p.phrases[lang].praise,
        ]
        for (const s of all) expect(/\d/.test(s), `${p.id}/${lang}: "${s}"`).toBe(false)
      }
    }
  })
})

describe('voiceProfileOf', () => {
  it('projects the TTS subset of a style', () => {
    const strict = PRESETS.find(p => p.id === 'preset-strict')!
    expect(voiceProfileOf(strict)).toEqual({
      lang: strict.lang, voiceURI: strict.voiceURI, rate: strict.rate, pitch: strict.pitch, volume: strict.volume,
    })
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/voiceStyle.test.ts`
Expected: FAIL — `Cannot find module '../voiceStyle'`.

- [ ] **Step 3: Write the implementation**

`poses_viewer/src/drill2d/voiceStyle.ts`:
```ts
/**
 * Voice-style data model + the three immutable built-in presets. A VoiceStyle is a
 * plain editable object holding every knob the feedback research identified:
 * bandwidth, cadence, praise, skip-stale, TTS voice, and a per-language phrase
 * catalog. The user clones a preset into a named, editable style. Trust rule: every
 * phrase is a plain imperative — the voice layer never injects a degree number.
 */

export type MetricKey = 'elbow_angle' | 'shoulder_angle' | 'knee_bend' | 'torso_lean' | 'shoulder_tilt'

/** The 5 voiced in-plane metrics, in a fixed order (severity-tie stability). */
export const VOICE_METRIC_KEYS: MetricKey[] = [
  'elbow_angle', 'shoulder_angle', 'knee_bend', 'torso_lean', 'shoulder_tilt',
]

export type Lang = 'en' | 'uk'

export interface PhraseSet {
  /** up = measured value ABOVE the (widened) band; down = BELOW it. */
  cues: Record<MetricKey, { up: string; down: string }>
  /** Rotated praise pool; specific, never bare "good job". */
  praise: string[]
}

export interface VoiceStyle {
  id: string
  name: string
  builtin: boolean

  // Cadence / timing
  correctiveMinGapMs: number
  praiseMinSilenceMs: number
  postStrokeGapMs: number

  // Bands / gating
  bandWidthMult: number
  minMeaningfulDeltaDeg: number
  reminderIntervalMs: number
  varyCues: boolean

  // Praise
  praiseEnabled: boolean
  praiseOnCorrection: boolean
  praiseOnStreak: boolean
  praiseStreakLen: number

  // Skip-stale
  skipStaleEnabled: boolean
  skipStaleMarginMs: number

  // Voice (TTS)
  lang: Lang
  voiceURI: string | null
  rate: number
  pitch: number
  volume: number

  // Phrases (word choice)
  phrases: Record<Lang, PhraseSet>
}

/** The TTS-only subset the playback layer needs. */
export interface VoiceProfile {
  lang: Lang
  voiceURI: string | null
  rate: number
  pitch: number
  volume: number
}

export function voiceProfileOf(s: VoiceStyle): VoiceProfile {
  return { lang: s.lang, voiceURI: s.voiceURI, rate: s.rate, pitch: s.pitch, volume: s.volume }
}

// ── Seed phrases (editable after cloning) ───────────────────────────────────
// EN seeded from the practitioner cue vocabulary; UA equivalents authored here and
// refined by the user in the editor. Efficient = terse keyword; Playful = warm.

const PLAYFUL_EN: PhraseSet = {
  cues: {
    elbow_angle: { up: 'give the elbow a little bend', down: 'reach through it' },
    shoulder_angle: { up: 'drop the shoulder a touch', down: 'open the shoulder a bit more' },
    knee_bend: { up: 'sit into it, legs on', down: 'ease up, stand a little taller' },
    torso_lean: { up: 'stand a bit taller', down: 'lean into the ball' },
    shoulder_tilt: { up: 'level the shoulders', down: 'level the shoulders' },
  },
  praise: ["that's the shape!", 'yes — that follow-through', 'clean — do that again', 'nice, really solid', 'love it, keep going'],
}
const PLAYFUL_UK: PhraseSet = {
  cues: {
    elbow_angle: { up: 'трохи зігни лікоть', down: 'тягнися крізь мʼяч' },
    shoulder_angle: { up: 'ледь опусти плече', down: 'трохи відкрий плече' },
    knee_bend: { up: 'присядь, працюй ногами', down: 'трохи випрямись' },
    torso_lean: { up: 'тримайся трохи рівніше', down: 'нахились до мʼяча' },
    shoulder_tilt: { up: 'вирівняй плечі', down: 'вирівняй плечі' },
  },
  praise: ['оце форма!', 'так — оце завершення', 'чисто — ще раз так', 'гарно, дуже впевнено', 'клас, продовжуй'],
}

const STRICT_EN: PhraseSet = {
  cues: {
    elbow_angle: { up: 'bend the elbow', down: 'extend more' },
    shoulder_angle: { up: 'drop the shoulder', down: 'open the shoulder' },
    knee_bend: { up: 'bend the knees', down: 'stand taller' },
    torso_lean: { up: 'stand taller', down: 'lean in' },
    shoulder_tilt: { up: 'level the shoulders', down: 'level the shoulders' },
  },
  praise: ["that's the shape", 'clean — repeat that', 'correct'],
}
const STRICT_UK: PhraseSet = {
  cues: {
    elbow_angle: { up: 'зігни лікоть', down: 'більше випрями руку' },
    shoulder_angle: { up: 'опусти плече', down: 'відкрий плече' },
    knee_bend: { up: 'зігни коліна', down: 'стань вище' },
    torso_lean: { up: 'тримайся рівніше', down: 'нахились уперед' },
    shoulder_tilt: { up: 'вирівняй плечі', down: 'вирівняй плечі' },
  },
  praise: ['оце форма', 'чисто — повтори', 'правильно'],
}

const EFFICIENT_EN: PhraseSet = {
  cues: {
    elbow_angle: { up: 'bend elbow', down: 'extend arm' },
    shoulder_angle: { up: 'drop shoulder', down: 'open shoulder' },
    knee_bend: { up: 'bend knees', down: 'stand taller' },
    torso_lean: { up: 'taller', down: 'lean in' },
    shoulder_tilt: { up: 'level shoulders', down: 'level shoulders' },
  },
  praise: ['clean', 'yes', 'good'],
}
const EFFICIENT_UK: PhraseSet = {
  cues: {
    elbow_angle: { up: 'зігни лікоть', down: 'випрями руку' },
    shoulder_angle: { up: 'опусти плече', down: 'відкрий плече' },
    knee_bend: { up: 'зігни коліна', down: 'вище' },
    torso_lean: { up: 'вище', down: 'нахились' },
    shoulder_tilt: { up: 'рівніше плечі', down: 'рівніше плечі' },
  },
  praise: ['чисто', 'так', 'добре'],
}

export const PRESETS: VoiceStyle[] = [
  {
    id: 'preset-playful', name: 'Playful', builtin: true,
    correctiveMinGapMs: 2500, praiseMinSilenceMs: 3000, postStrokeGapMs: 300,
    bandWidthMult: 1.0, minMeaningfulDeltaDeg: 5, reminderIntervalMs: 8000, varyCues: true,
    praiseEnabled: true, praiseOnCorrection: true, praiseOnStreak: true, praiseStreakLen: 3,
    skipStaleEnabled: true, skipStaleMarginMs: 150,
    lang: 'en', voiceURI: null, rate: 0.95, pitch: 1.05, volume: 1.0,
    phrases: { en: PLAYFUL_EN, uk: PLAYFUL_UK },
  },
  {
    id: 'preset-strict', name: 'Strict', builtin: true,
    correctiveMinGapMs: 3000, praiseMinSilenceMs: 6000, postStrokeGapMs: 300,
    bandWidthMult: 0.9, minMeaningfulDeltaDeg: 5, reminderIntervalMs: 8000, varyCues: true,
    praiseEnabled: true, praiseOnCorrection: true, praiseOnStreak: false, praiseStreakLen: 3,
    skipStaleEnabled: true, skipStaleMarginMs: 150,
    lang: 'en', voiceURI: null, rate: 1.0, pitch: 0.95, volume: 1.0,
    phrases: { en: STRICT_EN, uk: STRICT_UK },
  },
  {
    id: 'preset-efficient', name: 'Efficient', builtin: true,
    correctiveMinGapMs: 5000, praiseMinSilenceMs: 10000, postStrokeGapMs: 300,
    bandWidthMult: 1.4, minMeaningfulDeltaDeg: 7, reminderIntervalMs: 10000, varyCues: true,
    praiseEnabled: true, praiseOnCorrection: true, praiseOnStreak: false, praiseStreakLen: 3,
    skipStaleEnabled: true, skipStaleMarginMs: 150,
    lang: 'en', voiceURI: null, rate: 1.15, pitch: 1.0, volume: 1.0,
    phrases: { en: EFFICIENT_EN, uk: EFFICIENT_UK },
  },
]
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/voiceStyle.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/drill2d/voiceStyle.ts poses_viewer/src/drill2d/__tests__/voiceStyle.test.ts
git commit -m "feat(viewer): VoiceStyle model + 3 immutable presets (EN+UA seed phrases)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: `buildSpokenSchedule.ts` — the pure voice core

**Files:**
- Create: `poses_viewer/src/drill2d/buildSpokenSchedule.ts`
- Test: `poses_viewer/src/drill2d/__tests__/buildSpokenSchedule.test.ts`

**Interfaces:**
- Consumes: `Lang`, `MetricKey`, `VoiceStyle` from `voiceStyle.ts`; `ClipManifest`, `clipKey`, `lookupClip` from `voiceClips.ts`.
- Produces: types `MetricObservation`, `VoiceRep`, `SpokenFeedbackItem`, `SpokenSchedule`; const `BASE_WPM`; fns `estimateDurationMs(text, rate): number`, `nextStrokeStartAfter(starts, atMs): number`, `buildSpokenSchedule(reps, strokeStartTimes, style, clipManifest?): SpokenSchedule`.
- `VoiceRep = { strokeStartMs, contactMs, strokeEndMs, coachable, observations: Partial<Record<MetricKey, MetricObservation>> }`; `MetricObservation = { value, lo, hi }`.
- `SpokenFeedbackItem = { atMs, text, lang, kind: 'cue'|'praise', metricKey: MetricKey|null, clipKey?, estDurationMs }`.

- [ ] **Step 1: Write the failing test**

`poses_viewer/src/drill2d/__tests__/buildSpokenSchedule.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { buildSpokenSchedule, estimateDurationMs, nextStrokeStartAfter, BASE_WPM, type VoiceRep } from '../buildSpokenSchedule'
import { PRESETS, type VoiceStyle, type MetricKey } from '../voiceStyle'
import { clipKey, type ClipManifest } from '../voiceClips'

const STRICT = PRESETS.find(p => p.id === 'preset-strict')!

/** A style with everything relaxed so timing/gates don't interfere unless a test sets them. */
function style(overrides: Partial<VoiceStyle> = {}): VoiceStyle {
  return {
    ...STRICT,
    correctiveMinGapMs: 0, praiseMinSilenceMs: 0, postStrokeGapMs: 0,
    bandWidthMult: 1.0, minMeaningfulDeltaDeg: 5, reminderIntervalMs: 0, varyCues: false,
    praiseEnabled: false, praiseOnCorrection: false, praiseOnStreak: false,
    skipStaleEnabled: false, skipStaleMarginMs: 0,
    ...overrides,
  }
}

/** Build a rep at t with given observations; spaced 2000ms apart by index helper below. */
function rep(startMs: number, obs: VoiceRep['observations'], coachable = true): VoiceRep {
  return { strokeStartMs: startMs, contactMs: startMs + 200, strokeEndMs: startMs + 400, coachable, observations: obs }
}
const band = (value: number) => ({ value, lo: 100, hi: 140 }) // half=20, center=120

describe('estimateDurationMs', () => {
  it('uses the WPM budget and scales with rate', () => {
    // 2 words at 150 wpm (2.5 w/s) → 800ms
    expect(estimateDurationMs('bend elbow', 1)).toBeCloseTo((2 / (BASE_WPM / 60)) * 1000, 5)
    expect(estimateDurationMs('bend elbow', 2)).toBeCloseTo(estimateDurationMs('bend elbow', 1) / 2, 5)
  })
})

describe('nextStrokeStartAfter', () => {
  it('returns the first start strictly greater than atMs, else +Infinity', () => {
    expect(nextStrokeStartAfter([0, 1000, 2000], 1000)).toBe(2000)
    expect(nextStrokeStartAfter([0, 1000], 1500)).toBe(Number.POSITIVE_INFINITY)
  })
})

describe('buildSpokenSchedule — bandwidth gate', () => {
  it('stays silent when every metric is in band', () => {
    const reps = [rep(0, { elbow_angle: band(120) })]
    expect(buildSpokenSchedule(reps, [0], style())).toEqual([])
  })
  it('emits a cue when a metric exits the band by >= minMeaningfulDeltaDeg', () => {
    const reps = [rep(0, { elbow_angle: band(160) })] // 160 > 140, dev=20 >= 5
    const out = buildSpokenSchedule(reps, [0], style())
    expect(out).toHaveLength(1)
    expect(out[0].metricKey).toBe('elbow_angle')
    expect(out[0].kind).toBe('cue')
    expect(out[0].text).toBe(STRICT.phrases.en.cues.elbow_angle.up)
  })
  it('respects minMeaningfulDeltaDeg as a floor', () => {
    const reps = [rep(0, { elbow_angle: band(143) })] // dev=3 < 5 → silent
    expect(buildSpokenSchedule(reps, [0], style())).toEqual([])
  })
  it('bandWidthMult widens the band (quieter)', () => {
    const reps = [rep(0, { elbow_angle: band(150) })] // dev vs raw hi=10; widened hi=120+20*1.4=148 → dev=2 <5
    expect(buildSpokenSchedule(reps, [0], style({ bandWidthMult: 1.4 }))).toEqual([])
  })
  it('selects the down phrase when below band', () => {
    const reps = [rep(0, { elbow_angle: band(80) })] // 80 < 100, dev=-20
    expect(buildSpokenSchedule(reps, [0], style())[0].text).toBe(STRICT.phrases.en.cues.elbow_angle.down)
  })
})

describe('buildSpokenSchedule — one cue per rep + selection', () => {
  it('emits at most one cue per rep, the highest severity', () => {
    // elbow dev = 40 (sev 2), knee dev = 10 (sev 0.5) → elbow wins
    const reps = [rep(0, { elbow_angle: { value: 180, lo: 100, hi: 140 }, knee_bend: { value: 150, lo: 100, hi: 140 } })]
    const out = buildSpokenSchedule(reps, [0], style())
    expect(out).toHaveLength(1)
    expect(out[0].metricKey).toBe('elbow_angle')
  })
  it('varyCues prefers a different metric than the previous cue', () => {
    const reps = [
      rep(0, { elbow_angle: band(160) }),                                  // elbow
      rep(2000, { elbow_angle: band(160), knee_bend: band(155) }),         // elbow sev=1, knee sev=0.75 → vary picks knee
    ]
    const out = buildSpokenSchedule(reps, [0, 2000], style({ varyCues: true }))
    expect(out.map(o => o.metricKey)).toEqual(['elbow_angle', 'knee_bend'])
  })
  it('reminderIntervalMs suppresses re-flagging the same metric too soon', () => {
    const reps = [
      rep(0, { elbow_angle: band(160) }),
      rep(2000, { elbow_angle: band(160) }), // only fault, within reminder window → suppressed
    ]
    const out = buildSpokenSchedule(reps, [0, 2000], style({ reminderIntervalMs: 5000 }))
    expect(out).toHaveLength(1)
    expect(out[0].atMs).toBe(400)
  })
})

describe('buildSpokenSchedule — cadence', () => {
  it('drops a correction that arrives before correctiveMinGapMs', () => {
    const reps = [
      rep(0, { elbow_angle: band(160) }),
      rep(1000, { knee_bend: band(160) }), // atMs 1400 - 400 = 1000 < 3000 → dropped
    ]
    const out = buildSpokenSchedule(reps, [0, 1000], style({ correctiveMinGapMs: 3000 }))
    expect(out).toHaveLength(1)
    expect(out[0].metricKey).toBe('elbow_angle')
  })
})

describe('buildSpokenSchedule — praise', () => {
  it('fires when a previously-flagged metric returns to band', () => {
    const reps = [
      rep(0, { elbow_angle: band(160) }),  // flagged
      rep(5000, { elbow_angle: band(120) }), // corrected → praise
    ]
    const out = buildSpokenSchedule(reps, [0, 5000], style({ praiseEnabled: true, praiseOnCorrection: true, praiseMinSilenceMs: 1000 }))
    expect(out.map(o => o.kind)).toEqual(['cue', 'praise'])
    expect(out[1].metricKey).toBeNull()
    expect(STRICT.phrases.en.praise).toContain(out[1].text)
  })
  it('fires streak praise after praiseStreakLen clean reps', () => {
    const clean = (t: number) => rep(t, { elbow_angle: band(120) })
    const reps = [clean(0), clean(3000), clean(6000)]
    const out = buildSpokenSchedule(reps, [0, 3000, 6000], style({
      praiseEnabled: true, praiseOnStreak: true, praiseStreakLen: 3, praiseMinSilenceMs: 1000,
    }))
    expect(out).toHaveLength(1)
    expect(out[0].kind).toBe('praise')
    expect(out[0].atMs).toBe(6400)
  })
  it('a correction outranks praise in the same rep', () => {
    const reps = [
      rep(0, { elbow_angle: band(160) }),                       // flag elbow
      rep(5000, { elbow_angle: band(120), knee_bend: band(160) }), // elbow corrected (praise candidate) BUT knee now bad
    ]
    const out = buildSpokenSchedule(reps, [0, 5000], style({ praiseEnabled: true, praiseOnCorrection: true, praiseMinSilenceMs: 1000 }))
    expect(out[1].kind).toBe('cue')
    expect(out[1].metricKey).toBe('knee_bend')
  })
})

describe('buildSpokenSchedule — timing anchor', () => {
  it('places every item at strokeEndMs + postStrokeGapMs', () => {
    const reps = [rep(1000, { elbow_angle: band(160) })] // strokeEnd = 1400
    const out = buildSpokenSchedule(reps, [1000], style({ postStrokeGapMs: 300 }))
    expect(out[0].atMs).toBe(1700)
  })
})

describe('buildSpokenSchedule — skip-stale', () => {
  it('drops a cue that cannot finish before the next stroke starts', () => {
    // atMs = 400; next stroke at 600; margin 150 → must finish by 450. A multi-word cue won't.
    const reps = [rep(0, { elbow_angle: band(160) }), rep(600, {})]
    const out = buildSpokenSchedule(reps, [0, 600], style({ skipStaleEnabled: true, skipStaleMarginMs: 150 }))
    expect(out).toHaveLength(0)
  })
  it('keeps the last rep cue (no next stroke)', () => {
    const reps = [rep(0, { elbow_angle: band(160) })]
    const out = buildSpokenSchedule(reps, [0], style({ skipStaleEnabled: true, skipStaleMarginMs: 150 }))
    expect(out).toHaveLength(1)
  })
  it('uses the manifest clip duration when a fresh clip exists', () => {
    const text = STRICT.phrases.en.cues.elbow_angle.up
    const manifest: ClipManifest = {
      styleId: 'preset-strict',
      clips: { [clipKey('en', text)]: { file: 'e.mp3', durationMs: 100, text, lang: 'en' } },
    }
    // atMs=400, next stroke=600, margin=50 → must finish by 550. The 100ms clip fits;
    // the word-count estimate (~1200ms) would not → proves clip duration is used.
    const reps = [rep(0, { elbow_angle: band(160) }), rep(600, {})]
    const out = buildSpokenSchedule(reps, [0, 600], style({ skipStaleEnabled: true, skipStaleMarginMs: 50 }), manifest)
    expect(out).toHaveLength(1)
    expect(out[0].clipKey).toBe(clipKey('en', text))
    expect(out[0].estDurationMs).toBe(100)
  })
})

describe('buildSpokenSchedule — coachable + determinism', () => {
  it('skips non-coachable reps entirely', () => {
    const reps = [rep(0, {}, false)]
    expect(buildSpokenSchedule(reps, [0], style())).toEqual([])
  })
  it('is deterministic for identical inputs', () => {
    const mk = () => [rep(0, { elbow_angle: band(160) }), rep(5000, { knee_bend: band(160) })]
    const s = style({ correctiveMinGapMs: 3000 })
    expect(buildSpokenSchedule(mk(), [0, 5000], s)).toEqual(buildSpokenSchedule(mk(), [0, 5000], s))
  })
  it('uses the uk phrase set when style.lang = uk', () => {
    const reps = [rep(0, { elbow_angle: band(160) })]
    const out = buildSpokenSchedule(reps, [0], style({ lang: 'uk' }))
    expect(out[0].text).toBe(STRICT.phrases.uk.cues.elbow_angle.up)
    expect(out[0].lang).toBe('uk')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/buildSpokenSchedule.test.ts`
Expected: FAIL — `Cannot find module '../buildSpokenSchedule'`.

- [ ] **Step 3: Write the implementation**

`poses_viewer/src/drill2d/buildSpokenSchedule.ts`:
```ts
/**
 * The pure voice core. Walks style-independent per-rep observations in time order
 * and produces a deterministic, audio-free spoken schedule:
 *   gate → praise → select → cadence → skip-stale → format.
 * Every item lands in the inter-rep gap at strokeEndMs + postStrokeGapMs, and must
 * finish before the next stroke starts. No pose math here — re-running on a style
 * edit is cheap (enables live preview).
 */
import type { Lang, MetricKey, VoiceStyle } from './voiceStyle'
import { clipKey, lookupClip, type ClipManifest } from './voiceClips'

/** Practitioner WPM budget: ~150 wpm ≈ 2.5 words/s. */
export const BASE_WPM = 150

export interface MetricObservation {
  value: number
  lo: number
  hi: number
}

export interface VoiceRep {
  strokeStartMs: number
  contactMs: number
  strokeEndMs: number
  /** false → bad camera / no measurable coachable metrics; the core emits nothing and leaves state untouched. */
  coachable: boolean
  /** Only voiced (5 in-plane), reliable, measured metrics with their (un-widened) ideal band. */
  observations: Partial<Record<MetricKey, MetricObservation>>
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

/** Live-TTS utterance duration estimate from word count, scaled by rate. */
export function estimateDurationMs(text: string, rate: number): number {
  const words = text.trim().split(/\s+/).filter(Boolean).length
  const safeRate = rate > 0 ? rate : 1
  const wordsPerSec = (BASE_WPM * safeRate) / 60
  return wordsPerSec > 0 ? (words / wordsPerSec) * 1000 : 0
}

/** First stroke start strictly after atMs, or +Infinity if none (last rep). */
export function nextStrokeStartAfter(strokeStartTimes: number[], atMs: number): number {
  for (const t of strokeStartTimes) if (t > atMs) return t
  return Number.POSITIVE_INFINITY
}

interface Deviation {
  direction: 'up' | 'down'
  deviation: number
  severity: number
}

/** Per-metric out-of-band deviations for one rep, given the style's widened bands. */
function gateRep(
  observations: Partial<Record<MetricKey, MetricObservation>>,
  style: VoiceStyle,
): Map<MetricKey, Deviation> {
  const out = new Map<MetricKey, Deviation>()
  for (const key of Object.keys(observations) as MetricKey[]) {
    const o = observations[key]!
    const center = (o.lo + o.hi) / 2
    const half = (o.hi - o.lo) / 2
    const wLo = center - half * style.bandWidthMult
    const wHi = center + half * style.bandWidthMult
    let direction: 'up' | 'down'
    let deviation: number
    if (o.value > wHi) {
      direction = 'up'
      deviation = o.value - wHi
    } else if (o.value < wLo) {
      direction = 'down'
      deviation = o.value - wLo
    } else {
      continue
    }
    if (Math.abs(deviation) < style.minMeaningfulDeltaDeg) continue
    const severity = half > 0 ? Math.abs(deviation) / half : 0
    out.set(key, { direction, deviation, severity })
  }
  return out
}

export function buildSpokenSchedule(
  reps: VoiceRep[],
  strokeStartTimes: number[],
  style: VoiceStyle,
  clipManifest?: ClipManifest | null,
): SpokenSchedule {
  const schedule: SpokenSchedule = []
  const lang = style.lang
  const phrases = style.phrases[lang]

  let lastSpokenMs = Number.NEGATIVE_INFINITY
  let prevOutOfBand = new Set<MetricKey>()
  const lastCuedMs = new Map<MetricKey, number>()
  let lastCueMetric: MetricKey | null = null
  let cleanStreak = 0
  let praiseIndex = 0

  const durationOf = (text: string): { ms: number; key?: string } => {
    const clip = lookupClip(clipManifest ?? null, lang, text)
    if (clip) return { ms: clip.durationMs, key: clipKey(lang, text) }
    return { ms: estimateDurationMs(text, style.rate) }
  }
  const fits = (atMs: number, ms: number): boolean => {
    if (!style.skipStaleEnabled) return true
    const next = nextStrokeStartAfter(strokeStartTimes, atMs)
    return atMs + ms <= next - style.skipStaleMarginMs
  }

  for (const rep of reps) {
    if (!rep.coachable) continue
    const atMs = rep.strokeEndMs + style.postStrokeGapMs

    // 1. Bandwidth gate.
    const deviations = gateRep(rep.observations, style)
    const curOutOfBand = new Set<MetricKey>(deviations.keys())
    cleanStreak = curOutOfBand.size === 0 ? cleanStreak + 1 : 0

    // 2. Praise candidate (computed before correction; correction wins if both qualify).
    let praiseCandidate = false
    if (style.praiseEnabled) {
      const corrected = [...prevOutOfBand].some(k => !curOutOfBand.has(k))
      if (style.praiseOnCorrection && corrected) praiseCandidate = true
      else if (style.praiseOnStreak && cleanStreak >= style.praiseStreakLen) praiseCandidate = true
    }

    // 3. Cue selection (one per rep): max severity, vary-aware, reminder-suppressed.
    let chosen: MetricKey | null = null
    if (curOutOfBand.size > 0) {
      const ranked = [...curOutOfBand].sort(
        (a, b) => deviations.get(b)!.severity - deviations.get(a)!.severity,
      )
      const eligible = ranked.filter(k => {
        const last = lastCuedMs.get(k)
        return last === undefined || atMs - last >= style.reminderIntervalMs
      })
      if (eligible.length > 0) {
        chosen = eligible[0]
        if (style.varyCues && chosen === lastCueMetric && eligible.length > 1) chosen = eligible[1]
      }
    }

    // 4–6. Emit. Correction outranks praise; if the correction is dropped (cadence/stale) the rep stays silent.
    if (chosen !== null) {
      if (atMs - lastSpokenMs >= style.correctiveMinGapMs) {
        const dir = deviations.get(chosen)!.direction
        const text = phrases.cues[chosen][dir]
        const { ms, key } = durationOf(text)
        if (fits(atMs, ms)) {
          schedule.push({ atMs, text, lang, kind: 'cue', metricKey: chosen, clipKey: key, estDurationMs: ms })
          lastSpokenMs = atMs
          lastCueMetric = chosen
          lastCuedMs.set(chosen, atMs)
        }
      }
    } else if (praiseCandidate) {
      if (atMs - lastSpokenMs >= style.praiseMinSilenceMs && phrases.praise.length > 0) {
        const text = phrases.praise[praiseIndex % phrases.praise.length]
        const { ms, key } = durationOf(text)
        if (fits(atMs, ms)) {
          schedule.push({ atMs, text, lang, kind: 'praise', metricKey: null, clipKey: key, estDurationMs: ms })
          lastSpokenMs = atMs
          praiseIndex++
        }
      }
    }

    // Always advance the out-of-band memory, regardless of what was emitted.
    prevOutOfBand = curOutOfBand
  }

  return schedule
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/buildSpokenSchedule.test.ts`
Expected: PASS (all describe blocks).

- [ ] **Step 5: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/drill2d/buildSpokenSchedule.ts poses_viewer/src/drill2d/__tests__/buildSpokenSchedule.test.ts
git commit -m "feat(viewer): buildSpokenSchedule — pure voice core (gate/praise/select/cadence/skip-stale)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: `voiceStyleStore.ts` — CRUD, persistence, export/import

**Files:**
- Create: `poses_viewer/src/drill2d/voiceStyleStore.ts`
- Test: `poses_viewer/src/drill2d/__tests__/voiceStyleStore.test.ts`

**Interfaces:**
- Consumes: `PRESETS`, `VoiceStyle` from `voiceStyle.ts`.
- Produces: const `STORAGE_KEY`, `DEFAULT_ACTIVE_ID`; type `StoredStyles = { activeStyleId: string; styles: VoiceStyle[] }` (user styles only); pure fns `allStyles(userStyles)`, `resolveActiveId(activeStyleId, userStyles)`, `cloneStyle(source, newName, id)`, `renameStyle(userStyles, id, name)`, `removeStyle(userStyles, id)`, `upsertStyle(userStyles, style)`, `serializeStyle(style)`, `importStyle(json, id)`, `getActiveStyle(state)`; storage fns `loadUserStyles()`, `saveUserStyles(state)`, `newStyleId()`.

- [ ] **Step 1: Write the failing test**

`poses_viewer/src/drill2d/__tests__/voiceStyleStore.test.ts`:
```ts
// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import {
  STORAGE_KEY, DEFAULT_ACTIVE_ID,
  allStyles, resolveActiveId, cloneStyle, renameStyle, removeStyle, upsertStyle,
  serializeStyle, importStyle, getActiveStyle, loadUserStyles, saveUserStyles, newStyleId,
  type StoredStyles,
} from '../voiceStyleStore'
import { PRESETS } from '../voiceStyle'

beforeEach(() => localStorage.clear())

describe('resolveActiveId', () => {
  it('keeps a valid id (preset or user)', () => {
    expect(resolveActiveId('preset-playful', [])).toBe('preset-playful')
  })
  it('falls back to the default when the id is unknown', () => {
    expect(resolveActiveId('nope', [])).toBe(DEFAULT_ACTIVE_ID)
    expect(DEFAULT_ACTIVE_ID).toBe('preset-strict')
  })
})

describe('cloneStyle', () => {
  it('produces a non-builtin copy with the given id/name and deep-independent phrases', () => {
    const src = PRESETS.find(p => p.id === 'preset-playful')!
    const clone = cloneStyle(src, 'My Style', 'uid-1')
    expect(clone.id).toBe('uid-1')
    expect(clone.name).toBe('My Style')
    expect(clone.builtin).toBe(false)
    clone.phrases.en.praise.push('mutated')
    expect(src.phrases.en.praise).not.toContain('mutated') // deep copy
  })
})

describe('rename/remove/upsert', () => {
  it('renames only the matching user style', () => {
    const a = cloneStyle(PRESETS[0], 'A', 'a'); const b = cloneStyle(PRESETS[0], 'B', 'b')
    expect(renameStyle([a, b], 'a', 'A2').find(s => s.id === 'a')!.name).toBe('A2')
  })
  it('removes by id', () => {
    const a = cloneStyle(PRESETS[0], 'A', 'a')
    expect(removeStyle([a], 'a')).toEqual([])
  })
  it('upsert replaces in place or appends', () => {
    const a = cloneStyle(PRESETS[0], 'A', 'a')
    const updated = { ...a, name: 'A2' }
    expect(upsertStyle([a], updated).find(s => s.id === 'a')!.name).toBe('A2')
    const b = cloneStyle(PRESETS[0], 'B', 'b')
    expect(upsertStyle([a], b)).toHaveLength(2)
  })
})

describe('serialize/import round-trip', () => {
  it('imports a serialized style as a new non-builtin style with a fresh id', () => {
    const src = cloneStyle(PRESETS[1], 'Exported', 'orig')
    const json = serializeStyle(src)
    const imported = importStyle(json, 'fresh')
    expect(imported.id).toBe('fresh')
    expect(imported.builtin).toBe(false)
    expect(imported.name).toBe('Exported')
    expect(imported.phrases).toEqual(src.phrases)
  })
  it('rejects invalid JSON shapes', () => {
    expect(() => importStyle('{"foo":1}', 'x')).toThrow()
  })
})

describe('persistence', () => {
  it('saves user styles (dropping builtin) and restores active id', () => {
    const user = cloneStyle(PRESETS[0], 'Mine', 'mine')
    const state: StoredStyles = { activeStyleId: 'mine', styles: [user] }
    saveUserStyles(state)
    const loaded = loadUserStyles()
    expect(loaded.activeStyleId).toBe('mine')
    expect(loaded.styles.map(s => s.id)).toEqual(['mine'])
    // raw store never contains presets
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    expect(raw.styles.every((s: { builtin: boolean }) => !s.builtin)).toBe(true)
  })
  it('defaults cleanly when nothing is stored', () => {
    const loaded = loadUserStyles()
    expect(loaded.activeStyleId).toBe(DEFAULT_ACTIVE_ID)
    expect(loaded.styles).toEqual([])
  })
  it('falls back to default active id when the stored active id is invalid', () => {
    saveUserStyles({ activeStyleId: 'ghost', styles: [] })
    expect(loadUserStyles().activeStyleId).toBe(DEFAULT_ACTIVE_ID)
  })
})

describe('getActiveStyle + allStyles', () => {
  it('lists presets first, then user styles', () => {
    const u = cloneStyle(PRESETS[0], 'U', 'u')
    expect(allStyles([u]).map(s => s.id)).toEqual([...PRESETS.map(p => p.id), 'u'])
  })
  it('resolves the active style object', () => {
    const u = cloneStyle(PRESETS[0], 'U', 'u')
    expect(getActiveStyle({ activeStyleId: 'u', styles: [u] }).id).toBe('u')
    expect(getActiveStyle({ activeStyleId: 'bad', styles: [] }).id).toBe(DEFAULT_ACTIVE_ID)
  })
})

describe('newStyleId', () => {
  it('returns distinct non-empty ids', () => {
    expect(newStyleId()).not.toBe(newStyleId())
    expect(newStyleId().length).toBeGreaterThan(0)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/voiceStyleStore.test.ts`
Expected: FAIL — `Cannot find module '../voiceStyleStore'`.

- [ ] **Step 3: Write the implementation**

`poses_viewer/src/drill2d/voiceStyleStore.ts`:
```ts
/**
 * Voice-style persistence. Built-in presets are code constants (never stored);
 * only user styles live in localStorage. Pure operations (clone/rename/remove/
 * import/resolve) are split from the thin localStorage read/write so the logic is
 * node-testable. Editing a preset is prevented at the UI layer by cloning first.
 */
import { PRESETS, type VoiceStyle } from './voiceStyle'

export const STORAGE_KEY = 'poses_viewer_voice_styles'
export const DEFAULT_ACTIVE_ID = 'preset-strict'

/** Persisted shape: active id + user styles only (presets are merged in at load). */
export interface StoredStyles {
  activeStyleId: string
  styles: VoiceStyle[]
}

function deepCopy(style: VoiceStyle): VoiceStyle {
  return JSON.parse(JSON.stringify(style)) as VoiceStyle
}

/** Presets first, then user styles — the selector order. */
export function allStyles(userStyles: VoiceStyle[]): VoiceStyle[] {
  return [...PRESETS, ...userStyles]
}

export function resolveActiveId(activeStyleId: string, userStyles: VoiceStyle[]): string {
  return allStyles(userStyles).some(s => s.id === activeStyleId) ? activeStyleId : DEFAULT_ACTIVE_ID
}

export function cloneStyle(source: VoiceStyle, newName: string, id: string): VoiceStyle {
  return { ...deepCopy(source), id, name: newName, builtin: false }
}

export function renameStyle(userStyles: VoiceStyle[], id: string, name: string): VoiceStyle[] {
  return userStyles.map(s => (s.id === id ? { ...s, name } : s))
}

export function removeStyle(userStyles: VoiceStyle[], id: string): VoiceStyle[] {
  return userStyles.filter(s => s.id !== id)
}

export function upsertStyle(userStyles: VoiceStyle[], style: VoiceStyle): VoiceStyle[] {
  const i = userStyles.findIndex(s => s.id === style.id)
  if (i === -1) return [...userStyles, style]
  const copy = [...userStyles]
  copy[i] = style
  return copy
}

export function serializeStyle(style: VoiceStyle): string {
  return JSON.stringify(style, null, 2)
}

/** Parse + validate an exported style; always returns a fresh non-builtin style. */
export function importStyle(json: string, id: string): VoiceStyle {
  const parsed = JSON.parse(json) as Partial<VoiceStyle>
  if (
    parsed === null || typeof parsed !== 'object' ||
    typeof parsed.name !== 'string' ||
    typeof parsed.phrases !== 'object' || parsed.phrases === null ||
    typeof parsed.bandWidthMult !== 'number'
  ) {
    throw new Error('invalid voice style JSON')
  }
  return { ...(deepCopy(parsed as VoiceStyle)), id, builtin: false }
}

export function getActiveStyle(state: StoredStyles): VoiceStyle {
  const id = resolveActiveId(state.activeStyleId, state.styles)
  return allStyles(state.styles).find(s => s.id === id) ?? PRESETS[0]
}

export function newStyleId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  // Fallback: timestamp+counter (app-only; tests run where crypto exists).
  newStyleIdCounter += 1
  return `style-${newStyleIdCounter}-${typeof performance !== 'undefined' ? Math.floor(performance.now()) : newStyleIdCounter}`
}
let newStyleIdCounter = 0

export function loadUserStyles(): StoredStyles {
  if (typeof localStorage === 'undefined') return { activeStyleId: DEFAULT_ACTIVE_ID, styles: [] }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { activeStyleId: DEFAULT_ACTIVE_ID, styles: [] }
    const parsed = JSON.parse(raw) as StoredStyles
    const styles = Array.isArray(parsed.styles) ? parsed.styles.filter(s => !s.builtin) : []
    return { activeStyleId: resolveActiveId(parsed.activeStyleId ?? DEFAULT_ACTIVE_ID, styles), styles }
  } catch {
    return { activeStyleId: DEFAULT_ACTIVE_ID, styles: [] }
  }
}

export function saveUserStyles(state: StoredStyles): void {
  if (typeof localStorage === 'undefined') return
  const toStore: StoredStyles = { activeStyleId: state.activeStyleId, styles: state.styles.filter(s => !s.builtin) }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(toStore))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/voiceStyleStore.test.ts`
Expected: PASS.

- [ ] **Step 5: Run the full suite + typecheck (no regressions)**

Run: `cd poses_viewer && npm test && npx tsc -b --noEmit`
Expected: all green, no type errors.

- [ ] **Step 6: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/drill2d/voiceStyleStore.ts poses_viewer/src/drill2d/__tests__/voiceStyleStore.test.ts
git commit -m "feat(viewer): voiceStyleStore — CRUD, localStorage persistence, export/import

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: `analyzeDrill` emits `voiceReps` + `strokeStartTimes` (additive)

This task ADDS the style-independent voice inputs to the analysis report without removing the old `feedback` path (kept until Task 8 so every consumer stays green).

**Files:**
- Modify: `poses_viewer/src/drill2d/analyzeDrill.ts`
- Modify (test): `poses_viewer/src/drill2d/__tests__/analyzeDrill.test.ts`

**Interfaces:**
- Consumes: `VoiceRep`, `MetricObservation` from `buildSpokenSchedule.ts`; `MetricKey`, `VOICE_METRIC_KEYS` from `voiceStyle.ts`.
- Produces: `DrillAnalysisReport.voiceReps: VoiceRep[]` and `DrillAnalysisReport.strokeStartTimes: number[]` (one entry per kept rep, same order as `reps`).

- [ ] **Step 1: Write the failing test** (append to `analyzeDrill.test.ts`)

Add this `describe` block at the end of `poses_viewer/src/drill2d/__tests__/analyzeDrill.test.ts`:
```ts
describe('analyzeDrill — voice inputs (style-independent)', () => {
  it('emits one voiceRep and one strokeStartTime per kept rep, with ascending starts', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    expect(report.voiceReps).toHaveLength(report.reps.length)
    expect(report.strokeStartTimes).toHaveLength(report.reps.length)
    for (let i = 1; i < report.strokeStartTimes.length; i++) {
      expect(report.strokeStartTimes[i]).toBeGreaterThanOrEqual(report.strokeStartTimes[i - 1])
    }
  })
  it('places contact between start and end for every voiceRep', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    for (const v of report.voiceReps) {
      expect(v.strokeStartMs).toBeLessThanOrEqual(v.contactMs)
      expect(v.contactMs).toBeLessThanOrEqual(v.strokeEndMs)
    }
  })
  it('with yaw override 0, at least one coachable voiceRep carries observations bounded by the standard', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    const withObs = report.voiceReps.filter(v => v.coachable && Object.keys(v.observations).length > 0)
    expect(withObs.length).toBeGreaterThan(0)
    // never voices hip_flexion; every observation carries the ideal band
    for (const v of report.voiceReps) {
      expect(Object.keys(v.observations)).not.toContain('hip_flexion')
      for (const obs of Object.values(v.observations)) {
        expect(typeof obs!.lo).toBe('number')
        expect(typeof obs!.hi).toBe('number')
      }
    }
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/analyzeDrill.test.ts`
Expected: FAIL — `report.voiceReps`/`report.strokeStartTimes` are `undefined`.

- [ ] **Step 3: Add imports to `analyzeDrill.ts`**

After the existing imports (near line 25), add:
```ts
import type { VoiceRep, MetricObservation } from './buildSpokenSchedule'
import { VOICE_METRIC_KEYS, type MetricKey } from './voiceStyle'
```

- [ ] **Step 4: Add the two fields to `DrillAnalysisReport`**

In the `DrillAnalysisReport` interface, add after `cleanReps: number`:
```ts
  /** Style-independent per-rep inputs for the voice core (buildSpokenSchedule). One per kept rep. */
  voiceReps: VoiceRep[]
  /** Stroke onset (ms) per kept rep, ascending — used by skip-stale collision checks. */
  strokeStartTimes: number[]
```

- [ ] **Step 5: Build `voiceReps` in `analyzeDrill` and add to the returned report**

In `analyzeDrill`, AFTER the EXP-2 unreliable block (the `if (unreliable.size > 0) { ... }` loop, ~line 223) and BEFORE the existing `const feedback: SpokenFeedback[] = []` line, insert:
```ts
  // Style-independent voice inputs: per-rep observations the voice core (buildSpokenSchedule)
  // gates against the active style. Only the 5 VOICED in-plane metrics, only on placementOk reps,
  // and only metrics not flagged unreliable. hip_flexion is analysis-only (never voiced).
  const intervalMs = seq.intervalMs
  const voiceReps: VoiceRep[] = repAnalyses.map(rep => {
    const observations: Partial<Record<MetricKey, MetricObservation>> = {}
    if (rep.placementOk) {
      for (const key of VOICE_METRIC_KEYS) {
        if (unreliable.has(key)) continue
        if (config.enabledMetrics && !config.enabledMetrics.has(key)) continue
        const value = rep.metrics[key]
        const range = config.standard.ranges[key]
        if (value === undefined || range === undefined) continue
        observations[key] = { value, lo: range.lo, hi: range.hi }
      }
    }
    return {
      strokeStartMs: rep.stroke.startFrame * intervalMs,
      contactMs: rep.stroke.peakFrame * intervalMs,
      strokeEndMs: rep.stroke.endFrame * intervalMs,
      coachable: rep.placementOk && Object.keys(observations).length > 0,
      observations,
    }
  })
  const strokeStartTimes = voiceReps.map(r => r.strokeStartMs)
```

Then in the `return { ... }` object add the two fields (after `cleanReps: ...`):
```ts
    voiceReps,
    strokeStartTimes,
```

- [ ] **Step 6: Run tests + typecheck**

Run: `cd poses_viewer && npm test && npx tsc -b --noEmit`
Expected: all green (old `feedback` tests still pass; new voiceReps tests pass).

- [ ] **Step 7: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/drill2d/analyzeDrill.ts poses_viewer/src/drill2d/__tests__/analyzeDrill.test.ts
git commit -m "feat(viewer): analyzeDrill emits style-independent voiceReps + strokeStartTimes

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Rewrite playback (`useSpokenFeedback`) + wire `StrokesPage`

Couples the playback hook rewrite with its only caller so the build stays green. After this task the page no longer reads `report.feedback`. Not unit-tested except `newlyCrossed`; verified via tsc + viewer-QA.

**Files:**
- Modify: `poses_viewer/src/components/useSpokenFeedback.ts`
- Modify (test): `poses_viewer/src/components/__tests__/useSpokenFeedback.test.ts`
- Modify: `poses_viewer/src/components/StrokesPage.tsx`

**Interfaces:**
- Consumes: `SpokenSchedule`, `SpokenFeedbackItem` from `buildSpokenSchedule.ts`; `ClipManifest`, `lookupClip` from `voiceClips.ts`; `VoiceProfile` from `voiceStyle.ts`; `buildSpokenSchedule`; `loadUserStyles`/`getActiveStyle` from `voiceStyleStore.ts`; `voiceProfileOf` from `voiceStyle.ts`; `loadManifest` from `voiceClips.ts`.
- Produces: `useSpokenFeedback(schedule, profile, manifest, muted): SpokenFeedbackState`, `newlyCrossed(schedule, prevMs, nowMs): SpokenFeedbackItem[]`, `speakNow(item, profile, manifest): void`, `cancelPlayback(): void`. `SpokenFeedbackState = { log: SpokenFeedbackItem[]; latest: SpokenFeedbackItem | null; onTime(nowMs); reset(toMs?) }`.

- [ ] **Step 1: Update the `newlyCrossed` test first**

Replace the body of `poses_viewer/src/components/__tests__/useSpokenFeedback.test.ts` with:
```ts
import { describe, expect, it } from 'vitest'
import { newlyCrossed } from '../useSpokenFeedback'
import type { SpokenFeedbackItem } from '../../drill2d/buildSpokenSchedule'

const fb = (atMs: number): SpokenFeedbackItem => ({ atMs, text: `m${atMs}`, lang: 'en', kind: 'cue', metricKey: null, estDurationMs: 500 })

describe('newlyCrossed', () => {
  it('returns entries whose atMs is in (prevMs, nowMs]', () => {
    const feed = [fb(1000), fb(2000), fb(3000)]
    expect(newlyCrossed(feed, 900, 2000).map(f => f.atMs)).toEqual([1000, 2000])
  })
  it('returns nothing when time has not advanced past any entry', () => {
    expect(newlyCrossed([fb(1000), fb(2000)], 2000, 2500)).toEqual([])
  })
  it('handles a backward seek (prev > now) by firing nothing', () => {
    expect(newlyCrossed([fb(1000), fb(2000)], 2500, 1500)).toEqual([])
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd poses_viewer && npx vitest run src/components/__tests__/useSpokenFeedback.test.ts`
Expected: FAIL — `newlyCrossed` still types against the old `SpokenFeedback`/`timestampMs` (compile/type error or assertion mismatch).

- [ ] **Step 3: Rewrite `useSpokenFeedback.ts`**

Replace the entire file `poses_viewer/src/components/useSpokenFeedback.ts` with:
```ts
import { useCallback, useEffect, useRef, useState } from 'react'
import type { SpokenFeedbackItem, SpokenSchedule } from '../drill2d/buildSpokenSchedule'
import { lookupClip, type ClipManifest } from '../drill2d/voiceClips'
import type { VoiceProfile } from '../drill2d/voiceStyle'

/** Schedule items whose atMs lies in (prevMs, nowMs] — the ones just crossed. */
export function newlyCrossed(schedule: SpokenSchedule, prevMs: number, nowMs: number): SpokenFeedbackItem[] {
  if (nowMs <= prevMs) return [] // paused or seeked backward — fire nothing
  return schedule.filter(f => f.atMs > prevMs && f.atMs <= nowMs)
}

export interface SpokenFeedbackState {
  log: SpokenFeedbackItem[]
  latest: SpokenFeedbackItem | null
  onTime: (nowMs: number) => void
  reset: (toMs?: number) => void
}

// Module-level so barge-in can stop whatever is in flight (clip or live utterance).
let currentAudio: HTMLAudioElement | null = null

export function cancelPlayback(): void {
  if (typeof window !== 'undefined' && 'speechSynthesis' in window) window.speechSynthesis.cancel()
  if (currentAudio) {
    currentAudio.pause()
    currentAudio = null
  }
}

/** Play one item: a fresh pre-rendered clip if available, else live Web Speech. Barges in. */
export function speakNow(
  item: { text: string; clipKey?: string },
  profile: VoiceProfile,
  manifest: ClipManifest | null,
): void {
  cancelPlayback()
  const clip = item.clipKey ? lookupClip(manifest, profile.lang, item.text) : null
  if (clip && manifest) {
    const audio = new Audio(`/voice/${encodeURIComponent(manifest.styleId)}/${clip.file}`)
    audio.volume = profile.volume
    currentAudio = audio
    void audio.play().catch(() => {})
    return
  }
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return
  const u = new SpeechSynthesisUtterance(item.text)
  u.lang = profile.lang === 'uk' ? 'uk-UA' : 'en-US'
  if (profile.voiceURI) {
    const v = window.speechSynthesis.getVoices().find(voice => voice.voiceURI === profile.voiceURI)
    if (v) u.voice = v
  }
  u.rate = profile.rate
  u.pitch = profile.pitch
  u.volume = profile.volume
  window.speechSynthesis.speak(u)
}

/**
 * Fires each schedule item once, when playback first crosses its atMs. Prefers a
 * deterministic pre-rendered clip, falls back to live Web Speech. `muted` keeps the
 * on-screen log but suppresses audio (replaces the old audio/text mode).
 */
export function useSpokenFeedback(
  schedule: SpokenSchedule,
  profile: VoiceProfile,
  manifest: ClipManifest | null,
  muted: boolean,
): SpokenFeedbackState {
  const lastMsRef = useRef(0)
  const [log, setLog] = useState<SpokenFeedbackItem[]>([])
  const [latest, setLatest] = useState<SpokenFeedbackItem | null>(null)
  const profileRef = useRef(profile)
  const manifestRef = useRef(manifest)
  const mutedRef = useRef(muted)
  profileRef.current = profile
  manifestRef.current = manifest
  mutedRef.current = muted

  const onTime = useCallback((nowMs: number) => {
    const fired = newlyCrossed(schedule, lastMsRef.current, nowMs)
    lastMsRef.current = nowMs
    if (fired.length === 0) return
    setLog(prev => [...prev, ...fired])
    setLatest(fired[fired.length - 1])
    if (mutedRef.current) return
    // Speak only the newest crossed item (barge-in); older ones are already stale.
    speakNow(fired[fired.length - 1], profileRef.current, manifestRef.current)
  }, [schedule])

  const reset = useCallback((toMs = 0) => {
    lastMsRef.current = toMs
    setLog([])
    setLatest(null)
    cancelPlayback()
  }, [])

  // New schedule → start fresh.
  useEffect(() => { reset(0) }, [schedule, reset])

  return { log, latest, onTime, reset }
}
```

- [ ] **Step 4: Run the `newlyCrossed` test to verify it passes**

Run: `cd poses_viewer && npx vitest run src/components/__tests__/useSpokenFeedback.test.ts`
Expected: PASS.

- [ ] **Step 5: Wire `StrokesPage.tsx` to the new path**

5a. **Imports** — replace the line `import { useSpokenFeedback, FeedbackMode } from './useSpokenFeedback'` with:
```ts
import { useSpokenFeedback } from './useSpokenFeedback'
import { buildSpokenSchedule } from '../drill2d/buildSpokenSchedule'
import { loadUserStyles, getActiveStyle } from '../drill2d/voiceStyleStore'
import { voiceProfileOf } from '../drill2d/voiceStyle'
import { loadManifest, type ClipManifest } from '../drill2d/voiceClips'
```

5b. **State** — replace `const [feedbackMode, setFeedbackMode] = useState<FeedbackMode>('audio')` with:
```ts
const [muted, setMuted] = useState(false)
const [styleState] = useState(() => loadUserStyles())
const [manifest, setManifest] = useState<ClipManifest | null>(null)
const activeStyle = useMemo(() => getActiveStyle(styleState), [styleState])
```

5c. **Load the active style's manifest** — add near the other effects:
```ts
useEffect(() => {
  let alive = true
  loadManifest(activeStyle.id).then(m => { if (alive) setManifest(m) })
  return () => { alive = false }
}, [activeStyle.id])
```

5d. **Schedule + playback** — replace
```ts
const feed = useMemo(() => report?.feedback ?? [], [report])
const spoken = useSpokenFeedback(feed, feedbackMode)
```
with:
```ts
const schedule = useMemo(
  () => (report ? buildSpokenSchedule(report.voiceReps, report.strokeStartTimes, activeStyle, manifest) : []),
  [report, activeStyle, manifest],
)
const spoken = useSpokenFeedback(schedule, voiceProfileOf(activeStyle), manifest, muted)
```

5e. **Banner + log rendering** — replace `🔊 {spoken.latest.message}` with `🔊 {spoken.latest.text}`, and in the log map replace `{(f.timestampMs / 1000).toFixed(1)} с — {f.message}` with `{(f.atMs / 1000).toFixed(1)} с — {f.text}`.

5f. **Replace the mode `<select>`** (the `Озвучення підказок:` label block) with a mute toggle:
```tsx
<label className="flex items-center gap-2">
  <span className="w-56">Озвучення підказок:</span>
  <input type="checkbox" checked={!muted} onChange={e => setMuted(!e.target.checked)} />
  <span className="text-neutral-400">{muted ? 'вимкнено (лише текст)' : 'голос увімкнено'}</span>
</label>
```

- [ ] **Step 6: Typecheck + full suite**

Run: `cd poses_viewer && npx tsc -b --noEmit && npm test`
Expected: no type errors; all tests green. (`report.feedback` is still produced by analyzeDrill but no longer consumed — removed in Task 8.)

- [ ] **Step 7: Viewer-QA**

Run `cd poses_viewer && npm run dev`, open `http://localhost:5780/#/strokes`, load a clip, play it. Confirm: spoken cues fire after strokes (not mid-swing), the on-screen banner/log show style phrases, the mute checkbox silences audio while the log still updates, and seeking/looping re-arms cues. (No clips exist yet → all live TTS — expected.)

- [ ] **Step 8: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/components/useSpokenFeedback.ts poses_viewer/src/components/__tests__/useSpokenFeedback.test.ts poses_viewer/src/components/StrokesPage.tsx
git commit -m "feat(viewer): style-driven playback — clip-or-live, barge-in, mute toggle

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: `VoiceStyleEditor` — the visual editor

A panel mounted in `StrokesPage` that selects/clones/renames/deletes/export/imports styles and edits every field, with a "Test voice" button. Not unit-tested (UI wiring) — verified via tsc + viewer-QA. Live preview is automatic: editing the active style re-runs the cheap `buildSpokenSchedule` memo already wired in Task 6.

**Files:**
- Create: `poses_viewer/src/components/VoiceStyleEditor.tsx`
- Modify: `poses_viewer/src/components/StrokesPage.tsx`

**Interfaces:**
- Consumes: `VoiceStyle`, `MetricKey`, `Lang`, `VOICE_METRIC_KEYS`, `voiceProfileOf` from `voiceStyle.ts`; `StoredStyles`, `allStyles`, `cloneStyle`, `renameStyle`, `removeStyle`, `upsertStyle`, `serializeStyle`, `importStyle`, `newStyleId`, `saveUserStyles`, `PRESETS` (via store/voiceStyle) from `voiceStyleStore.ts`; `clipKey`, `lookupClip`, `ClipManifest` from `voiceClips.ts`; `speakNow` from `useSpokenFeedback.ts`.
- Props: `VoiceStyleEditorProps = { state: StoredStyles; onChange: (next: StoredStyles) => void; manifest: ClipManifest | null }`.

- [ ] **Step 1: Promote `styleState` to mutable in `StrokesPage.tsx`**

Change `const [styleState] = useState(() => loadUserStyles())` to:
```ts
const [styleState, setStyleState] = useState(() => loadUserStyles())
```
Add an import for the editor:
```ts
import { VoiceStyleEditor } from './VoiceStyleEditor'
```

- [ ] **Step 2: Mount the editor**

In the JSX, inside/after the `Налаштування` fieldset region, add:
```tsx
<VoiceStyleEditor
  state={styleState}
  manifest={manifest}
  onChange={next => { setStyleState(next); saveUserStyles(next) }}
/>
```
Add `saveUserStyles` to the `voiceStyleStore` import.

- [ ] **Step 3: Implement `VoiceStyleEditor.tsx`**

`poses_viewer/src/components/VoiceStyleEditor.tsx`:
```tsx
/**
 * Visual editor for voice styles. Presets are immutable: editing any field of a
 * builtin style auto-clones it into a new user style first. Live preview is free —
 * the active style feeds the buildSpokenSchedule memo in StrokesPage, so a slider
 * change re-runs only the pure scheduler. "Test voice" plays a sample (clip-or-live).
 */
import { useMemo, useState, type ReactNode } from 'react'
import {
  type VoiceStyle, type Lang, type MetricKey, VOICE_METRIC_KEYS, voiceProfileOf,
} from '../drill2d/voiceStyle'
import {
  type StoredStyles, allStyles, cloneStyle, renameStyle, removeStyle, upsertStyle,
  serializeStyle, importStyle, newStyleId, getActiveStyle,
} from '../drill2d/voiceStyleStore'
import { clipKey, lookupClip, type ClipManifest } from '../drill2d/voiceClips'
import { speakNow } from './useSpokenFeedback'

export interface VoiceStyleEditorProps {
  state: StoredStyles
  onChange: (next: StoredStyles) => void
  manifest: ClipManifest | null
}

const LANGS: Lang[] = ['en', 'uk']

export function VoiceStyleEditor({ state, onChange, manifest }: VoiceStyleEditorProps) {
  const active = getActiveStyle(state)
  const [editLang, setEditLang] = useState<Lang>(active.lang)
  const styles = allStyles(state.styles)

  // Edit a field; if the active style is builtin, clone it first and switch to the clone.
  function edit(mutate: (s: VoiceStyle) => VoiceStyle) {
    if (active.builtin) {
      const clone = mutate(cloneStyle(active, `${active.name} (copy)`, newStyleId()))
      onChange({ activeStyleId: clone.id, styles: [...state.styles, clone] })
    } else {
      const updated = mutate({ ...active })
      onChange({ activeStyleId: active.id, styles: upsertStyle(state.styles, updated) })
    }
  }

  function setActive(id: string) { onChange({ ...state, activeStyleId: id }) }
  function clone() {
    const c = cloneStyle(active, `${active.name} (copy)`, newStyleId())
    onChange({ activeStyleId: c.id, styles: [...state.styles, c] })
  }
  function rename(name: string) {
    if (active.builtin) return
    onChange({ ...state, styles: renameStyle(state.styles, active.id, name) })
  }
  function remove() {
    if (active.builtin) return
    onChange({ activeStyleId: 'preset-strict', styles: removeStyle(state.styles, active.id) })
  }
  function exportJson() {
    const blob = new Blob([serializeStyle(active)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `${active.name}.voicestyle.json`; a.click()
    URL.revokeObjectURL(url)
  }
  async function importJson(file: File) {
    try {
      const imported = importStyle(await file.text(), newStyleId())
      onChange({ activeStyleId: imported.id, styles: [...state.styles, imported] })
    } catch { alert('Невалідний файл стилю голосу') }
  }

  return (
    <fieldset className="border border-neutral-700 rounded p-3 max-w-xl space-y-3 text-sm">
      <legend className="px-1">Стиль голосу</legend>

      {/* Header: select / clone / rename / delete / export / import */}
      <div className="flex flex-wrap items-center gap-2">
        <select className="bg-neutral-800 rounded px-2 py-1" value={active.id} onChange={e => setActive(e.target.value)}>
          {styles.map(s => <option key={s.id} value={s.id}>{s.name}{s.builtin ? ' (вбудований)' : ''}</option>)}
        </select>
        <button className="px-2 py-1 bg-neutral-800 rounded" onClick={clone}>Клонувати</button>
        <button className="px-2 py-1 bg-neutral-800 rounded disabled:opacity-40" disabled={active.builtin} onClick={() => {
          const n = prompt('Нова назва', active.name); if (n) rename(n)
        }}>Перейменувати</button>
        <button className="px-2 py-1 bg-neutral-800 rounded disabled:opacity-40" disabled={active.builtin} onClick={remove}>Видалити</button>
        <button className="px-2 py-1 bg-neutral-800 rounded" onClick={exportJson}>Експорт</button>
        <label className="px-2 py-1 bg-neutral-800 rounded cursor-pointer">Імпорт
          <input type="file" accept="application/json" className="hidden" onChange={e => { const f = e.target.files?.[0]; if (f) void importJson(f) }} />
        </label>
        <button className="px-2 py-1 bg-sky-800 rounded" onClick={() => speakNow(
          { text: active.phrases[active.lang].cues.elbow_angle.up, clipKey: clipKey(active.lang, active.phrases[active.lang].cues.elbow_angle.up) },
          voiceProfileOf(active), manifest,
        )}>▶ Тест голосу</button>
      </div>
      {active.builtin && <p className="text-amber-400 text-xs">Вбудований пресет незмінний — редагування створить копію.</p>}

      {/* Cadence */}
      <Section title="Каденс (мс)">
        <Slider label="Пауза між підказками" min={0} max={10000} step={250} value={active.correctiveMinGapMs} onChange={v => edit(s => ({ ...s, correctiveMinGapMs: v }))} />
        <Slider label="Тиша перед похвалою" min={0} max={15000} step={250} value={active.praiseMinSilenceMs} onChange={v => edit(s => ({ ...s, praiseMinSilenceMs: v }))} />
        <Slider label="Пауза після удару" min={0} max={1500} step={50} value={active.postStrokeGapMs} onChange={v => edit(s => ({ ...s, postStrokeGapMs: v }))} />
      </Section>

      {/* Bands */}
      <Section title="Зони">
        <Slider label="Ширина зони ×" min={0.5} max={2} step={0.05} value={active.bandWidthMult} onChange={v => edit(s => ({ ...s, bandWidthMult: v }))} />
        <Slider label="Поріг значущості (°)" min={0} max={20} step={1} value={active.minMeaningfulDeltaDeg} onChange={v => edit(s => ({ ...s, minMeaningfulDeltaDeg: v }))} />
        <Slider label="Інтервал нагадування" min={0} max={20000} step={500} value={active.reminderIntervalMs} onChange={v => edit(s => ({ ...s, reminderIntervalMs: v }))} />
        <Toggle label="Чергувати підказки" value={active.varyCues} onChange={v => edit(s => ({ ...s, varyCues: v }))} />
      </Section>

      {/* Praise */}
      <Section title="Похвала">
        <Toggle label="Увімкнено" value={active.praiseEnabled} onChange={v => edit(s => ({ ...s, praiseEnabled: v }))} />
        <Toggle label="За виправлення" value={active.praiseOnCorrection} onChange={v => edit(s => ({ ...s, praiseOnCorrection: v }))} />
        <Toggle label="За серію" value={active.praiseOnStreak} onChange={v => edit(s => ({ ...s, praiseOnStreak: v }))} />
        <Slider label="Довжина серії" min={1} max={10} step={1} value={active.praiseStreakLen} onChange={v => edit(s => ({ ...s, praiseStreakLen: v }))} />
      </Section>

      {/* Skip-stale */}
      <Section title="Пропуск застарілих">
        <Toggle label="Увімкнено" value={active.skipStaleEnabled} onChange={v => edit(s => ({ ...s, skipStaleEnabled: v }))} />
        <Slider label="Запас перед ударом (мс)" min={0} max={1000} step={50} value={active.skipStaleMarginMs} onChange={v => edit(s => ({ ...s, skipStaleMarginMs: v }))} />
      </Section>

      {/* Voice */}
      <Section title="Голос">
        <div className="flex items-center gap-2">
          <span className="w-40">Мова</span>
          {LANGS.map(l => (
            <button key={l} className={`px-2 py-1 rounded ${active.lang === l ? 'bg-sky-700' : 'bg-neutral-800'}`}
              onClick={() => edit(s => ({ ...s, lang: l }))}>{l.toUpperCase()}</button>
          ))}
        </div>
        <VoicePicker style={active} onPick={uri => edit(s => ({ ...s, voiceURI: uri }))} />
        <Slider label="Темп" min={0.5} max={2} step={0.05} value={active.rate} onChange={v => edit(s => ({ ...s, rate: v }))} />
        <Slider label="Тон" min={0} max={2} step={0.05} value={active.pitch} onChange={v => edit(s => ({ ...s, pitch: v }))} />
        <Slider label="Гучність" min={0} max={1} step={0.05} value={active.volume} onChange={v => edit(s => ({ ...s, volume: v }))} />
      </Section>

      {/* Phrases */}
      <Section title="Фрази">
        <div className="flex items-center gap-2 mb-1">
          <span className="w-40">Редагувати мову</span>
          {LANGS.map(l => (
            <button key={l} className={`px-2 py-1 rounded ${editLang === l ? 'bg-sky-700' : 'bg-neutral-800'}`} onClick={() => setEditLang(l)}>{l.toUpperCase()}</button>
          ))}
        </div>
        <PhraseTable style={active} lang={editLang} manifest={manifest} onEdit={edit} />
      </Section>
    </fieldset>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="border-t border-neutral-800 pt-2">
      <div className="text-neutral-400 mb-1">{title}</div>
      <div className="space-y-1">{children}</div>
    </div>
  )
}

function Slider(props: { label: string; min: number; max: number; step: number; value: number; onChange: (v: number) => void }) {
  return (
    <label className="flex items-center gap-2">
      <span className="w-48">{props.label}</span>
      <input type="range" min={props.min} max={props.max} step={props.step} value={props.value}
        onChange={e => props.onChange(Number(e.target.value))} className="flex-1" />
      <span className="w-16 text-right tabular-nums">{props.value}</span>
    </label>
  )
}

function Toggle({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex items-center gap-2">
      <input type="checkbox" checked={value} onChange={e => onChange(e.target.checked)} />
      <span>{label}</span>
    </label>
  )
}

function VoicePicker({ style, onPick }: { style: VoiceStyle; onPick: (uri: string | null) => void }) {
  const voices = useMemo(() => {
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) return []
    const want = style.lang === 'uk' ? 'uk' : 'en'
    return window.speechSynthesis.getVoices().filter(v => v.lang.toLowerCase().startsWith(want))
  }, [style.lang])
  return (
    <label className="flex items-center gap-2">
      <span className="w-40">Голос TTS</span>
      <select className="bg-neutral-800 rounded px-2 py-1 flex-1" value={style.voiceURI ?? ''} onChange={e => onPick(e.target.value || null)}>
        <option value="">(браузер за замовч.)</option>
        {voices.map(v => <option key={v.voiceURI} value={v.voiceURI}>{v.name} ({v.lang})</option>)}
      </select>
    </label>
  )
}

function PhraseTable({ style, lang, manifest, onEdit }: {
  style: VoiceStyle; lang: Lang; manifest: ClipManifest | null; onEdit: (m: (s: VoiceStyle) => VoiceStyle) => void
}) {
  const set = style.phrases[lang]
  function badge(text: string) {
    const fresh = !!lookupClip(manifest, lang, text)
    return <span className={`ml-1 text-xs ${fresh ? 'text-emerald-400' : 'text-neutral-500'}`}>{fresh ? '● кліп' : '○ live'}</span>
  }
  function setCue(metric: MetricKey, dir: 'up' | 'down', value: string) {
    onEdit(s => {
      const phrases = JSON.parse(JSON.stringify(s.phrases)) as VoiceStyle['phrases']
      phrases[lang].cues[metric][dir] = value
      return { ...s, phrases }
    })
  }
  function setPraise(i: number, value: string) {
    onEdit(s => {
      const phrases = JSON.parse(JSON.stringify(s.phrases)) as VoiceStyle['phrases']
      phrases[lang].praise[i] = value
      return { ...s, phrases }
    })
  }
  return (
    <table className="w-full text-xs">
      <thead><tr className="text-neutral-500"><th className="text-left">Метрика</th><th className="text-left">вище зони</th><th className="text-left">нижче зони</th></tr></thead>
      <tbody>
        {VOICE_METRIC_KEYS.map(metric => (
          <tr key={metric}>
            <td className="pr-2 text-neutral-400">{metric}</td>
            {(['up', 'down'] as const).map(dir => (
              <td key={dir} className="pr-2">
                <input className="bg-neutral-800 rounded px-1 py-0.5 w-full" value={set.cues[metric][dir]} onChange={e => setCue(metric, dir, e.target.value)} />
                {badge(set.cues[metric][dir])}
              </td>
            ))}
          </tr>
        ))}
        <tr><td colSpan={3} className="pt-2 text-neutral-400">Похвала</td></tr>
        {set.praise.map((p, i) => (
          <tr key={i}><td colSpan={3}>
            <input className="bg-neutral-800 rounded px-1 py-0.5 w-full" value={p} onChange={e => setPraise(i, e.target.value)} />
            {badge(p)}
          </td></tr>
        ))}
      </tbody>
    </table>
  )
}
```

- [ ] **Step 4: Typecheck + full suite**

Run: `cd poses_viewer && npx tsc -b --noEmit && npm test`
Expected: no type errors; all tests green.

- [ ] **Step 5: Viewer-QA**

In the running viewer (`#/strokes`): the editor appears; switching the active style changes the spoken cues live; editing a preset field creates a `(copy)` and switches to it; clone/rename/delete/export/import work; the phrase table edits update cues and praise; "Test voice" speaks; clip badges read "○ live" (no clips yet). Confirm `Стиль голосу` controls feel responsive (no pose recompute lag).

- [ ] **Step 6: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/components/VoiceStyleEditor.tsx poses_viewer/src/components/StrokesPage.tsx
git commit -m "feat(viewer): VoiceStyleEditor — full visual editor + Test voice, live preview

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Remove the dead `feedback` path; delete `cadencePolicy`; docs

Now that `StrokesPage` no longer reads `report.feedback`, remove the legacy voice path and the now-unused cadence module. Keep `feedbackEngine`/`messageCatalog` (still power `sessionFocus`/`sessionStrengths` + the table).

**Files:**
- Modify: `poses_viewer/src/drill2d/analyzeDrill.ts`
- Modify (test): `poses_viewer/src/drill2d/__tests__/analyzeDrill.test.ts`
- Modify (test): `poses_viewer/src/drill2d/__tests__/analyzeExperiments.test.ts`
- Delete: `poses_viewer/src/drill2d/cadencePolicy.ts`
- Delete: `poses_viewer/src/drill2d/__tests__/cadencePolicy.test.ts`
- Modify: `poses_viewer/CLAUDE.md`

**Interfaces:**
- Removes from `analyzeDrill.ts`: exported `SpokenFeedback` interface, `pickVariedCue`, `REMINDER_INTERVAL_MS`, the `feedback` field of `DrillAnalysisReport`, the `FeedbackCadencePolicy`/`formatCue`/`positiveMessage` imports and the feedback-generation loop.
- Keeps: `unreliableMetricKeys`, `sessionFocus`, `sessionStrengths`, `RepAnalysis`, `voiceReps`/`strokeStartTimes`, and `evaluateRep` usage (rep.cues).

- [ ] **Step 1: Edit the tests first (red)**

In `poses_viewer/src/drill2d/__tests__/analyzeDrill.test.ts`, delete the test `it('produces a metric map per rep and (with yaw override) at least one spoken line', ...)` (the block asserting `report.feedback`). The metric-map coverage is already in the Task-5 voice-inputs block, so no replacement is needed.

In `poses_viewer/src/drill2d/__tests__/analyzeExperiments.test.ts`:
- Remove `pickVariedCue` and `REMINDER_INTERVAL_MS` from the import list (lines 3 and 10).
- Delete the entire `describe('pickVariedCue (EXP-1)', () => { ... })` block (lines 127–149). (The vary/reminder behavior is now covered by `buildSpokenSchedule.test.ts`.)

- [ ] **Step 2: Run tests to verify the expected failures**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/analyzeExperiments.test.ts src/drill2d/__tests__/analyzeDrill.test.ts`
Expected: at this point these test files should PASS (we removed assertions/imports for things still present). If `pickVariedCue` is still exported, that's fine — the test no longer references it. Proceed to remove the production code next.

- [ ] **Step 3: Remove the legacy feedback code from `analyzeDrill.ts`**

3a. Fix imports. `sessionFocus` still calls `formatCue`, but `positiveMessage` and `FeedbackCadencePolicy` were used only by the deleted loop:
- Change `import { formatCue, positiveMessage } from './messageCatalog'` → `import { formatCue } from './messageCatalog'`.
- Delete `import { FeedbackCadencePolicy } from './cadencePolicy'`.
- Keep `import { evaluateRep } from './feedbackEngine'` unchanged (powers `rep.cues` + `sessionFocus`/`sessionStrengths`).

3b. Delete the constant `REMINDER_INTERVAL_MS` (EXP-1) and the function `pickVariedCue` entirely.

3c. Delete the `SpokenFeedback` interface.

3d. Delete the `feedback` field from `DrillAnalysisReport`.

3e. Delete the cadence construction and the feedback-generation loop:
```ts
const cadence = new FeedbackCadencePolicy( ... )   // DELETE
...
const feedback: SpokenFeedback[] = []              // DELETE the whole loop through its end
let lastMetric ...                                  // DELETE
let positiveCount ...                               // DELETE
const lastSpokenMsByMetric ...                      // DELETE
for (const rep of repAnalyses) { ... }              // DELETE (the feedback loop)
```

3f. Remove `feedback,` from the returned report object.

3g. Confirm `positiveMessage` is now unused (it was only used in the deleted loop). Grep:
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer && grep -rn "positiveMessage\|pickVariedCue\|REMINDER_INTERVAL_MS\|\.feedback\b\|FeedbackCadencePolicy" src
```
Expected remaining hits: only `positiveMessage` defined+tested in `messageCatalog.ts`/`messageCatalog.test.ts` (those stay), and `formatCue` usage in `sessionFocus`. No references to `pickVariedCue`, `REMINDER_INTERVAL_MS`, `.feedback`, or `FeedbackCadencePolicy` anywhere.

- [ ] **Step 4: Delete the cadence module + its test**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git rm poses_viewer/src/drill2d/cadencePolicy.ts poses_viewer/src/drill2d/__tests__/cadencePolicy.test.ts
```

- [ ] **Step 5: Typecheck + full suite**

Run: `cd poses_viewer && npx tsc -b --noEmit && npm test`
Expected: clean typecheck; all tests green. If `tsc` flags an unused `formatCue`/`evaluateRep`, reconcile by keeping exactly the imports `sessionFocus`/`evaluateRep` need.

- [ ] **Step 6: Update `poses_viewer/CLAUDE.md`**

In the `src/drill2d/` section, replace the sentence describing `messageCatalog`/`cadencePolicy`/`useSpokenFeedback` feedback with a short note:
> The voice layer is config-driven: `voiceStyle.ts` (model + 3 immutable presets, EN+UA phrases), `voiceStyleStore.ts` (localStorage CRUD), `voiceClips.ts` (deterministic clip key + manifest), `buildSpokenSchedule.ts` (pure core: gate → praise → select → cadence → skip-stale; vitest-covered), `useSpokenFeedback.ts` (clip-or-live playback + barge-in), `VoiceStyleEditor.tsx` (visual editor). `analyzeDrill` emits style-independent `voiceReps`/`strokeStartTimes`; the spoken schedule is built per active style. `cadencePolicy.ts` removed (cadence is now a style param). `messageCatalog`/`feedbackEngine` remain only for the on-screen session summary (`sessionFocus`/`sessionStrengths`) + the per-rep table. Clips live in `public/voice/<styleId>/`, generated offline by `scripts/generateVoiceClips.ts`.

- [ ] **Step 7: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/src/drill2d/analyzeDrill.ts poses_viewer/src/drill2d/__tests__/analyzeDrill.test.ts poses_viewer/src/drill2d/__tests__/analyzeExperiments.test.ts poses_viewer/CLAUDE.md
git commit -m "refactor(viewer): remove legacy feedback path + cadencePolicy (voice now style-driven)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Offline clip generation script

A Node/tsx script that renders every unique phrase of a style to an audio clip via a pluggable cloud-TTS provider and writes `public/voice/<styleId>/{*.mp3,manifest.json}`. Reuses the app's `clipKey`/`normalizeText` so keys match exactly. Not unit-tested; verified by a dry run.

**Files:**
- Create: `poses_viewer/scripts/generateVoiceClips.ts`
- Modify: `poses_viewer/package.json` (add `tsx` devDep + `gen:voice` script)

**Interfaces:**
- Consumes: `clipKey`, `normalizeText`, `ClipEntry`, `ClipManifest` from `../src/drill2d/voiceClips`; `VoiceStyle`, `Lang`, `VOICE_METRIC_KEYS` from `../src/drill2d/voiceStyle`.
- Produces: a CLI `tsx scripts/generateVoiceClips.ts <style.json>` writing `public/voice/<styleId>/`.

- [ ] **Step 1: Add `tsx` + the script entry to `package.json`**

In `devDependencies` add `"tsx": "^4.19.2"`. In `scripts` add:
```json
"gen:voice": "tsx scripts/generateVoiceClips.ts"
```
Install: `cd poses_viewer && npm install`.

- [ ] **Step 2: Implement the generator**

`poses_viewer/scripts/generateVoiceClips.ts`:
```ts
/**
 * Offline voice-clip generator. Renders each unique (lang, phrase) of a voice style
 * to a committed audio clip via a pluggable cloud-TTS provider, and writes a manifest
 * keyed by the SAME clipKey the app uses (so playback finds them). Never called at
 * runtime; credentials come from env vars and are never shipped to the browser.
 *
 * Usage:  TTS_PROVIDER=azure AZURE_TTS_KEY=... AZURE_TTS_REGION=... \
 *           npm run gen:voice -- ./preset-strict.voicestyle.json
 *         (export a style from the editor, or hand-write the JSON.)
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { clipKey, normalizeText, type ClipEntry, type ClipManifest } from '../src/drill2d/voiceClips'
import { VOICE_METRIC_KEYS, type Lang, type VoiceStyle } from '../src/drill2d/voiceStyle'

const __dirname = dirname(fileURLToPath(import.meta.url))
const PUBLIC_DIR = resolve(__dirname, '../public/voice')

interface TtsResult { audio: Uint8Array; ext: 'mp3' | 'wav'; durationMs: number }
interface TtsProvider { synthesize(text: string, lang: Lang): Promise<TtsResult> }

/** Stub provider: no real audio. Lets you dry-run the manifest/key wiring offline. */
const stubProvider: TtsProvider = {
  async synthesize(text) {
    // ~150 wpm estimate so the manifest carries a plausible duration in a dry run.
    const words = normalizeText(text).split(' ').filter(Boolean).length
    return { audio: new Uint8Array(0), ext: 'mp3', durationMs: Math.round((words / (150 / 60)) * 1000) }
  },
}

// Real providers (fill in when credentials are chosen — see spec §8):
//   ElevenLabs: POST https://api.elevenlabs.io/v1/text-to-speech/<voiceId>  (header xi-api-key)
//   Azure Neural: POST https://<region>.tts.speech.microsoft.com/cognitiveservices/v1
//     SSML <voice name="uk-UA-OstapNeural">…</voice>; WAV → durationMs from sample count.
function pickProvider(): TtsProvider {
  const name = process.env.TTS_PROVIDER ?? 'stub'
  switch (name) {
    // case 'azure': return azureProvider()
    // case 'elevenlabs': return elevenLabsProvider()
    case 'stub': return stubProvider
    default: throw new Error(`unknown TTS_PROVIDER: ${name} (set it, or use 'stub' for a dry run)`)
  }
}

/** Every unique (lang, phrase) in the style: all cue up/down + praise, both langs. */
function uniquePhrases(style: VoiceStyle): Array<{ lang: Lang; text: string }> {
  const seen = new Set<string>()
  const out: Array<{ lang: Lang; text: string }> = []
  for (const lang of ['en', 'uk'] as Lang[]) {
    const set = style.phrases[lang]
    const all = [
      ...VOICE_METRIC_KEYS.flatMap(k => [set.cues[k].up, set.cues[k].down]),
      ...set.praise,
    ]
    for (const text of all) {
      const key = clipKey(lang, text)
      if (seen.has(key)) continue
      seen.add(key)
      out.push({ lang, text })
    }
  }
  return out
}

async function main() {
  const arg = process.argv[2]
  if (!arg) { console.error('usage: npm run gen:voice -- <style.json>'); process.exit(1) }
  const style = JSON.parse(readFileSync(resolve(process.cwd(), arg), 'utf-8')) as VoiceStyle
  const provider = pickProvider()
  const outDir = resolve(PUBLIC_DIR, style.id)
  mkdirSync(outDir, { recursive: true })

  // Reuse existing manifest so unchanged phrases (matching key) are skipped.
  const manifestPath = resolve(outDir, 'manifest.json')
  let manifest: ClipManifest = { styleId: style.id, clips: {} }
  try { manifest = JSON.parse(readFileSync(manifestPath, 'utf-8')) as ClipManifest } catch { /* fresh */ }

  for (const { lang, text } of uniquePhrases(style)) {
    const key = clipKey(lang, text)
    const existing = manifest.clips[key]
    if (existing && normalizeText(existing.text) === normalizeText(text)) {
      console.log(`skip  ${key}  "${text}"`)
      continue
    }
    const res = await provider.synthesize(text, lang)
    const file = `${key}.${res.ext}`
    if (res.audio.length > 0) writeFileSync(resolve(outDir, file), res.audio)
    const entry: ClipEntry = { file, durationMs: res.durationMs, text, lang }
    manifest.clips[key] = entry
    console.log(`write ${key}  (${res.durationMs}ms)  "${text}"`)
  }

  writeFileSync(manifestPath, JSON.stringify(manifest, null, 2))
  console.log(`\nmanifest → ${manifestPath}  (${Object.keys(manifest.clips).length} clips)`)
}

void main()
```

- [ ] **Step 3: Dry run (stub provider)**

Export the Strict preset from the editor (or write a minimal style JSON) and run:
```bash
cd poses_viewer && TTS_PROVIDER=stub npm run gen:voice -- ./preset-strict.voicestyle.json
```
Expected: console lists `write <key> "<phrase>"` for each unique phrase, then writes `public/voice/preset-strict/manifest.json`. Re-running prints `skip` for all (idempotent on matching text). Confirm `manifest.json` exists and the keys equal `clipKey(lang, text)`.

- [ ] **Step 4: Typecheck + suite (script must not break the app build)**

Run: `cd poses_viewer && npx tsc -b --noEmit && npm test`
Expected: clean. (If `tsc -b` does not include `scripts/`, also run `npx tsc --noEmit scripts/generateVoiceClips.ts` to typecheck the script.)

- [ ] **Step 5: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add poses_viewer/scripts/generateVoiceClips.ts poses_viewer/package.json poses_viewer/package-lock.json
git commit -m "feat(viewer): offline voice-clip generator (pluggable cloud-TTS) + gen:voice script

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

> Do NOT commit `public/voice/**` audio in this task — the dry run produces only a stub manifest. Real clips are committed once a provider + warm `uk-UA` voice are chosen (spec §14 open question). Add `public/voice/*.voicestyle.json` exports and any throwaway dry-run output to `.gitignore` if needed.

---

## Final verification (after all tasks)

- [ ] `cd poses_viewer && npm test` — all green (new: voiceClips, voiceStyle, buildSpokenSchedule, voiceStyleStore; updated: analyzeDrill, analyzeExperiments, useSpokenFeedback; deleted: cadencePolicy).
- [ ] `cd poses_viewer && npx tsc -b --noEmit` — clean.
- [ ] Viewer-QA on `#/strokes`: style selector + editor work; cues fire post-stroke; mute works; "Test voice" works; clip badges reflect a generated manifest if present.

---

## Self-review notes (coverage map)

| Spec section | Covered by |
|---|---|
| §4 four layers | Tasks 1–4 (config), 3 (core), 5 (analysis), 6 (playback) |
| §5 `VoiceStyle` model + band math + WPM | Task 2 (model), Task 3 (`gateRep` band math, `estimateDurationMs`) |
| §6 presets + seed phrases | Task 2 |
| §7 Pass 1 | Task 5 (voiceReps/strokeStartTimes) |
| §7 Pass 2 (gate→praise→select→cadence→skip-stale→format) | Task 3 |
| §7 Pass 3 (clip-or-live, barge-in, mute, seek/replay) | Task 6 |
| §8 clips (clipKey, manifest, freshness, generator) | Task 1 + Task 9 |
| §9 editor | Task 7 |
| §10 persistence | Task 4 |
| §11 testing | Tasks 1–6 test steps |
| §12 file changes | all tasks; legacy removal in Task 8 |
