# Feedback decision / voice reproduction split + unified cue engine

Date: 2026-06-23
Scope: `poses_viewer` only (the M1 feedback half — no Kotlin `shared/` counterpart per CLAUDE.md).

## Problem

The viewer has **two parallel feedback-decision engines that disagree**:

- **On-screen table "Підказка"** → `feedbackEngine.evaluateRep(metrics, standard, enabledKeys)`:
  raw reference bands (no widening), its own `MIN_MEANINGFUL_DELTA_DEG = 5`, **all 6 metrics
  including `hip_flexion`**, dominant-cue selection via `sessionFocus`.
- **Voice** → `buildSpokenSchedule.gateRep`:
  *widened* bands (`bandWidthMult`), `style.minMeaningfulDeltaDeg`, **only the 5
  `VOICE_METRIC_KEYS` (no `hip_flexion`)**, plus cadence / praise / skip-stale gating.

Consequence: what the table reports as the fault and what the voice actually says are computed
from different numbers and different metric sets. Observed symptom (video_4, "Efficient (copy)"
style): the table headlines `hip_flexion` on 7/10 reps, but the voice says "extend arm"
(`elbow_angle`) on the 3 elbow reps and is silent on the hip reps — because `hip_flexion` is
never voiced and on those reps the elbow sits inside the *widened* band.

Compounding this, `VoiceStyle` currently owns **decision knobs** (bands, thresholds, cadence,
praise rules) that conceptually belong to drill feedback policy, not to voice rendering. Editing
the "voice style" silently changes *what / when* feedback fires, not just *how it sounds*.

## Goal

One feedback decision, two renderings:

- **Налаштування** owns the decision/policy. It drives BOTH the table and the voice.
- **Стиль голосу** owns reproduction only (TTS voice + phrase wording). Editing it changes only
  how feedback sounds, never what or when.
- The table makes the pipeline transparent with two columns: **all detected cues** vs **what was
  actually voiced**.

## Decisions (locked with user)

1. **`hip_flexion` becomes a voiced metric** (new phrases). Deliberate departure from the "5
   trusted in-plane metrics" trust rule, accepted for this effectiveness simulator so that the
   table and voice can fully agree.
2. **All policy knobs move to Налаштування.** `Стиль голосу` keeps only `lang, voiceURI, rate,
   pitch, volume, phrases`.
3. **Two table columns:** `Всі зауваження` (all detected cues) and `Підказка` (voiced-only).
4. **Migration:** clean defaults for the new `FeedbackSettings` (seeded from the old "Efficient"
   numbers); legacy policy fields on stored styles are dropped on load.
5. **Out of scope:** per-phase cell coloring `(over)/(under)` from `PER_PHASE_RANGES` stays a raw
   literature diagnostic — unchanged.

## Design

### 1. Data model split

**`VoiceStyle` (reproduction only)** — `src/drill2d/voiceStyle.ts`:

```ts
interface VoiceStyle {
  id: string; name: string; builtin: boolean
  lang: Lang; voiceURI: string | null; rate: number; pitch: number; volume: number
  phrases: Record<Lang, PhraseSet>
}
```

Removed fields: `correctiveMinGapMs, praiseMinSilenceMs, postStrokeGapMs, bandWidthMult,
minMeaningfulDeltaDeg, reminderIntervalMs, varyCues, praiseEnabled, praiseOnCorrection,
praiseOnStreak, praiseStreakLen, skipStaleEnabled, skipStaleMarginMs`.

**`FeedbackSettings` (decision/policy)** — new `src/drill2d/feedbackSettings.ts`:

```ts
interface FeedbackSettings {
  enabledMetrics: MetricKey[]            // which metrics to coach (incl. hip_flexion)
  bandWidthMult: number                  // widen/narrow the ideal band
  minMeaningfulDeltaDeg: number          // ignore deviations smaller than this
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
export const DEFAULT_FEEDBACK_SETTINGS: FeedbackSettings // seeded from old "Efficient" preset
```

Persistence: own `localStorage` key (`strokes_feedback_settings`), load/save helpers mirroring
`voiceStyleStore`. `enabledMetrics` migrates from the existing `enabledMetrics` UI state in
`StrokesPage` (already a `Set<string>`).

`MetricKey` gains `hip_flexion`; `VOICE_METRIC_KEYS` becomes the coachable-metric set including
`hip_flexion`. (Rename consideration: the constant now drives both table and voice — keep the
name for minimal churn but update its doc comment to "coachable metrics".)

### 2. Single decision engine

New pure function — `src/drill2d/decideRepCues.ts`:

```ts
interface RepCue {
  metricKey: MetricKey
  direction: 'up' | 'down'    // up = above widened band, down = below
  deviation: number            // signed degrees outside the widened band
  severity: number             // |deviation| / half-width
}

function decideRepCues(
  metrics: Record<string, number>,
  standard: ReferenceStandard,
  settings: FeedbackSettings,
): RepCue[]   // all out-of-band cues for the rep, severity-desc
```

Logic = the current `gateRep` math (widened band via `bandWidthMult`, `minMeaningfulDeltaDeg`
threshold, `enabledMetrics` filter) generalized to the full coachable metric set. This is the
**single source of truth**.

- **Table** uses `decideRepCues` per rep → `Всі зауваження` = the full list; the existing
  reliability/IQR drop (`unreliableMetricKeys`) still filters here.
- **Voice** (`buildSpokenSchedule`) consumes the SAME `RepCue[]` and only layers cadence / praise /
  skip-stale / vary on top, then renders phrases.

`feedbackEngine.evaluateRep` is retired for the Підказка path (it may stay only if still needed
for the session-summary `sessionFocus`/`sessionStrengths` wording; those will be re-pointed at
`decideRepCues` output to stay consistent). `FeedbackCue` vs `RepCue`: consolidate to one shape;
`RepCue` carries `direction: 'up'|'down'` (band-relative) — map to message/precision where the
summary needs it.

### 3. analyzeDrill output

`analyzeDrill` produces per-rep `RepCue[]` (from `decideRepCues`) instead of the current split of
`cues` (FeedbackCue) + `voiceReps` (observations). It still emits `strokeStartTimes` and per-rep
timing (`strokeStartMs/contactMs/strokeEndMs`, `coachable`). The voice core takes
`{ repCues, timing }[]` + `FeedbackSettings` + `VoiceStyle.phrases`.

### 4. buildSpokenSchedule signature

```ts
function buildSpokenSchedule(
  reps: { cues: RepCue[]; timing: RepTiming; coachable: boolean }[],
  strokeStartTimes: number[],
  settings: FeedbackSettings,    // was: style — cadence/praise/bands now here
  phrases: PhraseSet,            // from active VoiceStyle, for style.lang
  rate: number,                  // from active VoiceStyle; for live-TTS duration estimate
  clipManifest?: ClipManifest | null,
): { schedule: SpokenSchedule; voicedByRep: (RepCue | null)[] }
```

Key change: returns **`voicedByRep`** — for each rep, the cue actually spoken (or `null` if
suppressed by cadence/stale/reminder). The table's `Підказка` column reads this. `rate`/`lang`
come from the active `VoiceStyle` (reproduction), everything else from `FeedbackSettings`.

The internal band gate (`gateRep`) is deleted — `reps[i].cues` already are the gated deviations.
The selection step (max severity, vary-aware, reminder-suppressed) and the cadence/praise/
skip-stale steps are unchanged in behavior, just sourced from `FeedbackSettings`.

### 5. UI

**Налаштування panel** (`StrokesPage`) gains the moved sections, edited against `FeedbackSettings`:
- Зони: `bandWidthMult`, `minMeaningfulDeltaDeg`, `reminderIntervalMs`, `varyCues`
- Каденс (seconds): `correctiveMinGapMs`, `praiseMinSilenceMs`, `postStrokeGapMs`
- Похвала: `praiseEnabled`, `praiseOnCorrection`, `praiseOnStreak`, `praiseStreakLen`
- Пропуск застарілих: `skipStaleEnabled`, `skipStaleMarginMs`
- (existing) Метрики toggles drive `settings.enabledMetrics`

**VoiceStyleEditor** loses Cadence/Bands/Praise/Skip-stale sections; keeps **Голос** (lang, voice,
rate, pitch, volume) + **Фрази** (now with a `hip_flexion` row). The seconds-formatting Slider
work moves to the Налаштування cadence controls.

**DrillResultsTable** gains a `Всі зауваження` column (joined metric keys from `decideRepCues`)
and repurposes `Підказка` to show `voicedByRep[i]` (the spoken cue, or "—").

**Debug export JSON** (`exportDebugJson`) updated: emit `feedbackSettings`, slimmed `voiceStyle`,
per-rep `allCues` + `voicedCue`, and the schedule.

### 6. Phrases for hip_flexion

`PhraseSet.cues` gains `hip_flexion: { up, down }`. Seeds (refinable in editor):
- Efficient EN up "stand tall" / down "hinge forward"; UK "вище" / "нахились у стегнах"
- Strict / Playful: analogous tone.

## Testing

- `decideRepCues` unit tests: widened-band math, threshold, enabled filter, direction, severity
  ordering, hip_flexion inclusion. (Pure — easy.)
- `buildSpokenSchedule` tests updated to new signature; add an assertion that `voicedByRep`
  corresponds to emitted schedule items, and that suppressed reps yield `null`.
- A consistency test: for a given rep set, every `voicedByRep[i]` is a member of that rep's
  `decideRepCues` output (voice never invents a cue the decision didn't produce).
- Existing M0 count goldens untouched (detection path unchanged).
- `npx tsc -b --noEmit` + `npx vitest run` green.

## Files touched

- `src/drill2d/voiceStyle.ts` (slim model, hip phrases, MetricKey += hip_flexion)
- `src/drill2d/feedbackSettings.ts` (new), persistence helpers (new or in voiceStyleStore sibling)
- `src/drill2d/decideRepCues.ts` (new)
- `src/drill2d/buildSpokenSchedule.ts` (signature + return `voicedByRep`, drop gateRep)
- `src/drill2d/analyzeDrill.ts` (emit RepCue[] per rep; thread FeedbackSettings)
- `src/drill2d/feedbackEngine.ts` (retire from Підказка path / re-point summary)
- `src/components/StrokesPage.tsx` (FeedbackSettings state + Налаштування controls + wiring)
- `src/components/VoiceStyleEditor.tsx` (drop policy sections; hip phrase row)
- `src/components/DrillResultsTable.tsx` (Всі зауваження + voiced-only Підказка)
- tests under `src/drill2d/__tests__/`
- `poses_viewer/CLAUDE.md` (document the split + new files)

## Open / deferred

- `sessionFocus`/`sessionStrengths` wording currently leans on `FeedbackCue`/messageCatalog —
  re-point at `decideRepCues`/`RepCue` so the summary stays consistent with the per-rep columns.
  If that proves large, it can be a follow-up; the per-rep table + voice unification is the core.
