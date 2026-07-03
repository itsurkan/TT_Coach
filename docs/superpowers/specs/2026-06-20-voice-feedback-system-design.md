# Configurable Voice-Feedback System — Design Spec

**Date:** 2026-06-20
**Status:** Approved design (pre-plan)
**Target:** the **poses_viewer** prototype (React + Vite + TypeScript). TS-only; not the Kotlin `shared/` module (see Non-goals).
**Evidence base:**
- [docs/research/2026-06-18-voice-feedback-best-practices.md](../../research/2026-06-18-voice-feedback-best-practices.md) — academic report (bandwidth gate, one cue, prescriptive, event-praise, skip-stale).
- [docs/research/2026-06-18-coaching-practitioner-feedback.md](../../research/2026-06-18-coaching-practitioner-feedback.md) — practitioner report (warm-demander modes, WPM budget, TTS UX, verbatim cue vocabulary).

---

## 1. Goal & summary

Turn the poses_viewer's current hard-coded, EN-only spoken feedback into a **config-driven voice engine** whose every parameter is editable through a **full visual editor**, so the user can tune and **create their own named voice styles**.

A *voice style* is a plain data object holding every knob the research identified: bandwidth (when to speak), cadence (how often), praise (event-driven), word choice (the phrase catalog), and voice (language + TTS voice/rate/pitch). The user clones one of three immutable built-in presets (Playful / Strict / Efficient) into a named, editable style.

Playback prefers **deterministic pre-rendered audio clips** (generated once by an offline cloud-TTS script, committed as static assets) and falls back to live Web Speech for any phrase without a clip. This solves the weak browser `uk-UA` voice problem while keeping editing live.

### Design spine (backed by *both* research reports)
1. **Bandwidth gate** — silence is the default; speak only when a metric is clearly outside its band. Silence *means* "you're fine."
2. **One short prescriptive cue per rep** — never stack corrections; protect working memory.
3. **Event-driven praise** — fire when a previously-flagged metric returns to band (and, optionally, on a clean streak). **No fixed praise:correction ratio** (Losada debunk).
4. **Speak after the stroke, in the inter-rep gap** — never during backswing or at contact; leave a short post-stroke gap so the player feels their own stroke first.
5. **Skip stale cues** — if an utterance can't finish before the next stroke begins, drop it (drop = "within tolerance this rep").
6. **Let the user choose/tune the style** — autonomy lever; the three presets are starting points, not laws.

---

## 2. Non-goals (v1)

- **No Kotlin `shared/` changes.** The viewer is the prototype and already diverges from `shared/` deliberately (it grades against *external ideal ranges*, not the personal baseline — prior spec decision). Porting voice styles to Kotlin/Android is a later phase.
- **No personal-baseline coaching in the viewer.** Band basis stays the viewer's existing *ideal-range* model; the style's band-width knob scales it.
- **No runtime cloud-TTS dependency.** Cloud TTS is invoked only at authoring time by an offline script; the app never calls it at runtime.
- **No anticipatory ("…3-2-1") cueing**, no auto-shortening of cues to fit a gap (skip = drop, not rephrase), no per-phrase short variants. Noted as future work.
- **No phase-level voice cues.** The on-screen table keeps per-phase columns; the *voice* layer speaks one per-rep cue. (Per-phase data may inform severity later — out of scope here.)

---

## 3. Background — what already exists

| Concern | Existing (poses_viewer) | This spec |
|---|---|---|
| Deviation math | `drill2d/feedbackEngine.ts` `evaluateRep(metrics, standard)` vs ideal ranges, `MIN_MEANINGFUL_DELTA_DEG=5` | keep the math; band-width multiplier applied in the voice core |
| Cadence | `drill2d/cadencePolicy.ts` (3–5 s) | absorbed/parameterized by the voice core |
| Messages | `drill2d/messageCatalog.ts` (EN only, flat) | replaced by per-style `phrases` (EN+UA); catalog becomes default seed |
| Cue variety / reminder | `drill2d/analyzeDrill.ts` EXP-1 (vary cue), `REMINDER_INTERVAL_MS=8000`, EXP-2 (unreliable metric) | moved into the voice core as style params |
| Playback | `components/useSpokenFeedback.ts` (`speechSynthesis`, hardcoded `en-US`, `cancel()`) | extended: clip-or-live, per-style voice profile, barge-in |
| Drill orchestration | `analyzeDrill.ts` → `DrillAnalysisReport` | split: analysis (style-independent) vs voice (style-dependent) |
| Metrics | `drill2d/drillMetrics.ts` — 5 in-plane + `hip_flexion`; per-phase | unchanged (analysis layer) |
| Persistence | localStorage (`strokes_*`) | add `poses_viewer_voice_styles` |
| UI | `components/StrokesPage.tsx`, `DrillResultsTable.tsx` | add `VoiceStyleEditor`; drop binary `feedbackMode` toggle |

The five in-plane metrics (the only ones spoken with precise degrees, per the trust rule) are
`elbow_angle`, `shoulder_angle`, `knee_bend`, `torso_lean`, `shoulder_tilt`.

---

## 4. Architecture — four layers

```
┌─ Config layer ───────────────────────────────────────────────┐
│ voiceStyle.ts       VoiceStyle type + 3 immutable presets     │
│ voiceStyleStore.ts  CRUD, active-id, localStorage, export/imp │
│ voiceClips.ts       clip manifest loader + clipKey(lang,text) │
│ VoiceStyleEditor    full visual editor + "Test voice"         │
└──────────────────────────────────────────────────────────────┘
            │ active VoiceStyle (+ clip manifest)
            ▼
┌─ Voice layer (PURE — vitest) ────────────────────────────────┐
│ buildSpokenSchedule(reps, strokeStartTimes, style, clips?)    │
│   gate → praise → select → cadence → skip-stale → format      │
│   → SpokenFeedback[] (atMs, text, lang, kind, metricKey,      │
│      clipKey?, estDurationMs)                                  │
└──────────────────────────────────────────────────────────────┘
      ▲ per-rep deviations + stroke times      │ SpokenSchedule
      │ (style-INDEPENDENT)                     ▼
┌─ Analysis layer (mostly unchanged) ─┐  ┌─ Playback layer ─────┐
│ analyzeDrill → reps[]               │  │ useSpokenFeedback(    │
│  metrics + raw deviations +         │  │   schedule, profile,  │
│  strokeStartMs/contactMs/strokeEndMs│  │   clipManifest)       │
│  (runs once per video)              │  │  clip-or-live, barge-in│
└─────────────────────────────────────┘  └──────────────────────┘
```

**Boundaries:**
- **Analysis** is style-independent and cached — changing a style never re-runs pose/stroke math.
- **Voice core** is one pure function plus helpers — the thoroughly tested heart.
- **Playback** is the only non-unit-tested piece (thin Web Audio / Web Speech adapter), verified via viewer-QA.
- **Config** owns the styles and the clip manifest.

---

## 5. Data model — `VoiceStyle`

Every field is editor-exposed. Grouped by editor section.

```ts
type MetricKey =
  | 'elbow_angle' | 'shoulder_angle' | 'knee_bend' | 'torso_lean' | 'shoulder_tilt';

type Lang = 'en' | 'uk';

type VoiceStyle = {
  id: string;                 // 'preset-playful' | 'preset-strict' | 'preset-efficient' | uuid
  name: string;               // editable label shown in the selector
  builtin: boolean;           // presets: true (immutable; editing auto-clones)

  // ── Cadence / timing ─────────────────────────────────────
  correctiveMinGapMs: number;   // min silence between corrective cues
  praiseMinSilenceMs: number;   // min silence before a praise may fire
  postStrokeGapMs: number;      // gap after follow-through before speaking (feel-your-own-stroke)

  // ── Bands / gating ───────────────────────────────────────
  bandWidthMult: number;        // ×half-width of each ideal range (>1 wider/quieter, <1 tighter/chattier)
  minMeaningfulDeltaDeg: number;// noise floor; below this, never speak
  reminderIntervalMs: number;   // don't re-flag the same metric within this window
  varyCues: boolean;            // prefer a different metric than the previous cue

  // ── Praise ───────────────────────────────────────────────
  praiseEnabled: boolean;
  praiseOnCorrection: boolean;  // a previously-flagged metric returns to band
  praiseOnStreak: boolean;      // after praiseStreakLen consecutive clean reps
  praiseStreakLen: number;

  // ── Skip-stale ───────────────────────────────────────────
  skipStaleEnabled: boolean;    // drop a cue that can't finish before the next stroke starts
  skipStaleMarginMs: number;    // safety margin before next stroke start

  // ── Voice (TTS) ──────────────────────────────────────────
  lang: Lang;
  voiceURI: string | null;      // from speechSynthesis.getVoices(); null = browser default for lang
  rate: number;                 // 0.5–2.0  (live TTS; also scales the word-count duration estimate)
  pitch: number;                // 0–2      (live TTS only; baked into clips)
  volume: number;               // 0–1

  // ── Phrases (word choice) ────────────────────────────────
  phrases: Record<Lang, PhraseSet>;
};

type PhraseSet = {
  cues: Record<MetricKey, { up: string; down: string }>; // up = value above band, down = below band
  praise: string[];                                       // rotated; specific, not "good job"
};
```

### Field semantics & invariants
- **`up` / `down`** are relative to the *band*, not raw magnitude: `up` = measured value is above the (widened) ideal range; `down` = below it.
- **`bandWidthMult`** scales each metric's ideal range about its center: for ideal `[lo, hi]`, `center=(lo+hi)/2`, `half=(hi-lo)/2`, effective band `= [center − half·mult, center + half·mult]`. A metric is *out-of-band* only if it exits the effective band **and** `|signedDeviation| ≥ minMeaningfulDeltaDeg`.
- **Trust rule:** rotational/qualitative metrics (e.g. `shoulder_tilt`) carry qualitative phrasing only; **no degree numbers are ever spoken** (numbers stay in `DrillResultsTable`). This is enforced by the phrases being plain imperatives — the voice layer never injects a number.
- **`BASE_WPM`** is a module constant (≈150 WPM, ≈2.5 words/s — practitioner budget). Live-TTS utterance duration estimate `= words ÷ (BASE_WPM·rate ÷ 60) · 1000`. For clips, the **real duration** from the manifest overrides this estimate.
- **`pitch`** applies to live TTS only; pre-rendered clips bake it in. **`rate`** affects live TTS and the estimate; clips may be sped via `playbackRate` (pitch artifacts accepted).

---

## 6. Built-in presets (immutable seeds)

Values are starting points; all editable after cloning. Phrases seeded from the practitioner report's verbatim coach vocabulary.

| Knob | Playful | Strict | Efficient |
|---|---|---|---|
| `correctiveMinGapMs` | 2500 | 3000 | 5000 |
| `praiseMinSilenceMs` | 3000 | 6000 | 10000 |
| `postStrokeGapMs` | 300 | 300 | 300 |
| `bandWidthMult` | 1.0 | 0.9 | 1.4 |
| `minMeaningfulDeltaDeg` | 5 | 5 | 7 |
| `reminderIntervalMs` | 8000 | 8000 | 10000 |
| `varyCues` | true | true | true |
| `praiseEnabled` | true | true | true |
| `praiseOnCorrection` | true | true | true |
| `praiseOnStreak` | true | false | false |
| `praiseStreakLen` | 3 | 3 | 3 |
| `skipStaleEnabled` | true | true | true |
| `skipStaleMarginMs` | 150 | 150 | 150 |
| `lang` | en | en | en |
| `rate` | 0.95 | 1.0 | 1.15 |
| `pitch` | 1.05 | 0.95 | 1.0 |
| `volume` | 1.0 | 1.0 | 1.0 |

Default `lang` is `en` (good browser voices out of the box); both EN and UA phrase sets ship for every preset, and the user can switch `lang` to `uk` and generate clips.

### Seed phrases (cue catalog)

Direction → coaching imperative (drawn from the cue vocabulary; **Efficient** is the terse single-keyword variant, **Playful** the warm variant). EN shown; UA shipped alongside (translations authored during implementation).

| Metric | `up` (above band) | `down` (below band) |
|---|---|---|
| `elbow_angle` (high = straighter) | "bend the elbow" · *Eff:* "bend elbow" · *Play:* "give the elbow a little bend" | "extend more" · *Eff:* "extend arm" · *Play:* "reach through it" |
| `shoulder_angle` | "drop the shoulder a touch" | "open the shoulder more" |
| `knee_bend` (high = straighter legs) | "bend the knees" / "use your legs" · *Eff:* "bend knees" · *Play:* "sit into it, legs on" | "ease the knees, stand taller" · *Eff:* "stand taller" |
| `torso_lean` (high = more lean) | "stand a bit taller" | "lean into the ball" |
| `shoulder_tilt` (qualitative) | "level the shoulders" | "level the shoulders" |

Praise pool (specific, not generic): EN `["that's the shape", "clean — repeat that", "yes — that follow-through", "nice, solid one", "that's it"]`; UA equivalents authored during implementation.

> These strings are seeds; the whole point is they're editable in the visual editor. Exact wording (and UA phrasing) is refined during implementation and by the user.

---

## 7. Runtime flow

### Pass 1 — Analysis (style-independent, once per video)
`analyzeDrill` produces `reps[]`, each carrying:
- `strokeStartMs` — start of backswing (valley-clamped stroke start),
- `contactMs` — wrist-speed peak (contact),
- `strokeEndMs` — end of follow-through (stroke end),
- per-metric measured value and **raw signed deviation** from the ideal range (existing `evaluateRep` math, minus cue selection),
- plus the sequence-wide `strokeStartTimes: number[]` (for inter-rep gap lookup).

No style is involved; result is cached and reused across style edits.

### Pass 2 — `buildSpokenSchedule(reps, strokeStartTimes, style, clipManifest?)` (pure)
Walk reps in time order, threading state:
- `lastSpokenMs` — `atMs` of the last emitted item (cue or praise),
- `prevOutOfBand: Set<MetricKey>` — metrics that were out-of-band in the previous rep (drives praise-on-correction),
- `lastCuedMs: Map<MetricKey, number>` — last `atMs` we spoke a correction for each metric (drives the reminder window),
- `lastCueMetric: MetricKey | null`, `cleanStreak: number`.

Each item's spoken time is fixed by the rep: `atMs = strokeEndMs + postStrokeGapMs`. Per rep, **in this exact order**:

1. **Bandwidth gate.** For each metric, widen its ideal range by `bandWidthMult`. A metric is *out-of-band* this rep iff its deviation exits the widened range **and** `|deviation| ≥ minMeaningfulDeltaDeg`. Compute `curOutOfBand` (the set for this rep). Update `cleanStreak` (++ if `curOutOfBand` empty, else 0).
2. **Praise check (before correction, so a corrected rep gets credited).** If `praiseEnabled`: a metric in `prevOutOfBand` but not in `curOutOfBand` = corrected → praise candidate; else if `praiseOnStreak` and `cleanStreak ≥ praiseStreakLen` → praise candidate. A praise may emit only if `atMs − lastSpokenMs ≥ praiseMinSilenceMs`. Corrections outrank praise within the same rep (if both qualify, the correction wins).
3. **Cue selection (one per rep).** Among `curOutOfBand` pick max severity (`|deviation| ÷ half-width`). If `varyCues` and it equals `lastCueMetric`, prefer next-highest. Suppress a metric whose `atMs − lastCuedMs[metric] < reminderIntervalMs`.
4. **Cadence gate.** A correction may emit only if `atMs − lastSpokenMs ≥ correctiveMinGapMs`; else drop it this rep (a later eligible rep carries the message).
5. **Skip-stale.** Compute the surviving item's duration: clip duration from `clipManifest` if a matching fresh clip exists, else the word-count estimate. Let `nextStartMs` = next rep's `strokeStartMs` (or +∞ for the last rep). Keep iff `atMs + durationMs ≤ nextStartMs − skipStaleMarginMs`; otherwise **drop**. (Disabled when `!skipStaleEnabled`.)
6. **Format & emit.** Resolve the phrase from `style.phrases[lang]` (`cues[metric][direction]` or the rotated `praise` pool). Emit
   `SpokenFeedback { atMs, text, lang, kind: 'cue' | 'praise', metricKey, clipKey?, estDurationMs }`.
   Update state: set `lastSpokenMs = atMs`; on a cue set `lastCueMetric` and `lastCuedMs[metric] = atMs`. Finally set `prevOutOfBand = curOutOfBand` (always, regardless of what was emitted).

**Timing anchor:** every spoken item lands in the **inter-rep gap** — at `strokeEndMs + postStrokeGapMs`, never during backswing/contact, and must finish before the next stroke starts. Output is deterministic, audio-free, fully vitest-coverable.

### Pass 3 — Playback (`useSpokenFeedback(schedule, voiceProfile, clipManifest)`)
On each `onTime(nowMs)` from the video, fire schedule items whose `atMs` just crossed the playhead:
- **Resolve source:** if `clipKey` is present and the manifest has a **fresh** clip → play the audio file (`HTMLAudioElement`/Web Audio); else live `speechSynthesis` with `voiceProfile` (`voiceURI`/`rate`/`pitch`/`volume`/`lang`).
- **Barge-in:** before starting a new item, stop any in-flight one (`speechSynthesis.cancel()` and/or `audio.pause()`); a newer item always wins.
- Maintain the on-screen feedback log (unchanged behavior).
- **Seek/replay:** `reset()` stops playback and replays from the new playhead.

A viewer-level **mute** toggle (not a style field) suppresses audio while keeping the on-screen log — this replaces the old binary `feedbackMode: 'audio' | 'text'`.

---

## 8. Deterministic audio clips

### Why
Browser `uk-UA` voices are thin/inconsistent (some Chrome voices are even network-backed). The phrase catalog is finite and number-free (trust rule), so every phrase is fully pre-renderable to a warm cloud-TTS clip, generated **once** at authoring time.

### Clip key & manifest
- `clipKey(lang, text)` = `${lang}__${hash(normalize(text))}` where `normalize` lowercases and collapses whitespace; `hash` is a short stable string hash. **Shared by app and generation script** so keys match exactly.
- Keying by *text* gives free staleness detection: edit a phrase → no matching key → live fallback until regenerated.
- Manifest per style at `poses_viewer/public/voice/<styleId>/manifest.json`:
  ```json
  { "styleId": "...", "clips": {
      "uk__1a2b3c": { "file": "uk__1a2b3c.mp3", "durationMs": 940, "text": "зігни лікоть", "lang": "uk" }
  } }
  ```
- The app fetches the active style's manifest (best-effort; absent manifest → all-live).

### Generation script (offline, committed assets)
- `poses_viewer/scripts/generateVoiceClips.ts` (Node/tsx — same ecosystem, **reuses** the app's `clipKey` helper).
- Input: a style JSON (a preset exported, or a user style exported via the editor's Export).
- For each unique `(lang, phrase)` in the style's `phrases` (cues + praise), call a **pluggable** cloud-TTS provider, write the audio file + measured `durationMs` into `public/voice/<styleId>/`, and write `manifest.json`.
- Provider chosen at generation time via env/flag; recommended for warmth: **ElevenLabs** or **Azure Neural** (`uk-UA-OstapNeural` / `uk-UA-PolinaNeural`). Credentials via env var; never committed, never shipped to the browser.
- Re-running regenerates only changed phrases (keys that already exist with matching text are skipped).

---

## 9. Editor UI — `VoiceStyleEditor`

A panel reachable from `StrokesPage`. Layout mirrors the schema groups:

- **Header:** style selector (dropdown) · **Clone** · **Rename** · **Delete** · **Export** / **Import** (JSON). Editing any field of a `builtin` style auto-clones it first (presets stay immutable).
- **Cadence:** sliders for `correctiveMinGapMs`, `praiseMinSilenceMs`, `postStrokeGapMs`.
- **Bands:** sliders for `bandWidthMult`, `minMeaningfulDeltaDeg`, `reminderIntervalMs`; toggle `varyCues`.
- **Praise:** toggles `praiseEnabled` / `praiseOnCorrection` / `praiseOnStreak`; slider `praiseStreakLen`.
- **Skip-stale:** toggle `skipStaleEnabled`; slider `skipStaleMarginMs`.
- **Voice:** `lang` segmented control; `voiceURI` dropdown (populated from `speechSynthesis.getVoices()` filtered by lang); sliders `rate` / `pitch` / `volume`.
- **Phrases:** a table — rows = the 5 metrics × {up, down} + a praise list; columns switch by the `lang` toggle. Each row shows a **clip badge** (fresh / stale / none) derived from the manifest + `clipKey`.
- **Test voice:** speaks a sample cue with the current settings (clip-or-live), so edits are audible immediately.

Re-running `buildSpokenSchedule` on every edit is cheap (pure, no pose math), enabling live preview.

---

## 10. Persistence

- New localStorage key `poses_viewer_voice_styles`: `{ activeStyleId: string, styles: VoiceStyle[] }` (user styles only).
- Built-in presets are code constants, never persisted; merged with stored user styles at load. If `activeStyleId` is missing/invalid → default to `preset-strict`.
- Export = download a `VoiceStyle` JSON; Import = validate + add as a new user style (regenerate `id`).
- Clip audio is **not** in localStorage — it lives as static assets under `public/voice/<styleId>/` with the manifest.

---

## 11. Testing strategy

All style-dependent logic is pure → vitest, no audio.

- **`buildSpokenSchedule.test.ts`** (the core):
  - bandwidth gate: in-band → silent; out-of-band → candidate; `bandWidthMult` widens/narrows; `minMeaningfulDeltaDeg` floor.
  - one cue/rep; `varyCues` avoids repeating the last metric; `reminderIntervalMs` suppresses re-flag.
  - cadence: corrections ≥ `correctiveMinGapMs`; praise ≥ `praiseMinSilenceMs`; correction outranks praise same rep.
  - event-praise: flagged metric returns to band → praise; streak praise after `praiseStreakLen`.
  - **timing anchors:** items land at `strokeEndMs + postStrokeGapMs`.
  - **skip-stale:** fits before `nextStartMs − margin` → kept; doesn't fit → dropped; last rep (no next stroke) → always kept; uses clip duration when manifest provides one, else word-count estimate.
  - formatting: correct phrase per `lang` + metric + direction; praise rotation; trust rule (no numbers in output).
- **`voiceStyleStore.test.ts`:** clone preset; rename/delete; active-id; serialize↔deserialize; preset immutability (edit → auto-clone); export/import round-trip; fallback when `activeStyleId` invalid.
- **`voiceClips.test.ts`:** `clipKey` stability/normalization; manifest lookup; freshness (text match) → fresh vs stale.
- **Determinism:** identical inputs → identical schedule (required for live preview).

Not unit-tested: `useSpokenFeedback` (Web Speech/Web Audio adapter) and `VoiceStyleEditor` wiring → verified via viewer-QA (`npx vitest run` for logic; manual audio check in the running viewer). `npx tsc -b --noEmit` must stay clean.

---

## 12. File-by-file change list

**New:**
- `poses_viewer/src/drill2d/voiceStyle.ts` — `VoiceStyle`, `MetricKey`, `Lang`, `PhraseSet`; 3 immutable presets; seed phrases (EN+UA).
- `poses_viewer/src/drill2d/voiceStyleStore.ts` — CRUD, active-id, localStorage, export/import.
- `poses_viewer/src/drill2d/voiceClips.ts` — `clipKey(lang, text)`, manifest type + loader, freshness check.
- `poses_viewer/src/drill2d/buildSpokenSchedule.ts` — the pure core (gate → praise → select → cadence → skip-stale → format) + duration helper.
- `poses_viewer/src/components/VoiceStyleEditor.tsx` (+ small subcomponents) — the visual editor + Test voice.
- `poses_viewer/scripts/generateVoiceClips.ts` — offline clip generation (pluggable cloud-TTS) → `public/voice/<styleId>/`.
- Tests: `buildSpokenSchedule.test.ts`, `voiceStyleStore.test.ts`, `voiceClips.test.ts`.

**Changed:**
- `poses_viewer/src/drill2d/analyzeDrill.ts` — emit style-independent `reps[]` with `strokeStartMs`/`contactMs`/`strokeEndMs` + raw deviations + `strokeStartTimes`; move cue selection / cadence / formatting (incl. EXP-1 vary, reminder, EXP-2 unreliable) out to `buildSpokenSchedule` as style params.
- `poses_viewer/src/components/useSpokenFeedback.ts` — take a `SpokenSchedule` + `voiceProfile` + `clipManifest`; clip-or-live resolution; barge-in via `cancel()`/`pause()`; EN/UA voice selection.
- `poses_viewer/src/components/StrokesPage.tsx` — load active style from the store; mount `VoiceStyleEditor`; pass schedule + profile to the player; replace binary `feedbackMode` with a mute toggle + the style's voice profile.
- `poses_viewer/src/drill2d/cadencePolicy.ts`, `messageCatalog.ts`, `feedbackEngine.ts` — absorbed/parameterized: cadence becomes a function of the style inside the core; the message catalog shrinks to the preset phrase seed; `evaluateRep` keeps only the deviation computation (band-width applied in the core).

---

## 13. Out of scope / future

- Cloud-TTS as a runtime fallback (in-app render button + IndexedDB blobs) — chose offline-script generation for v1.
- Per-phrase **short variants** + auto-shorten-to-fit (v1 skip = drop).
- **Anticipatory cueing** (pre-position before the next stroke).
- Phase-level voice cues / phase-aware severity.
- Porting voice styles to Kotlin `shared/` for Android/iOS.
- A/B testing whether a specific persona improves adherence (open research question).

## 14. Open questions (resolve in-app, not in spec)

- Real-footage tuning of `bandWidthMult` / gaps for a fast forehand drive (lit numbers come from simple/continuous tasks).
- Max tolerable post-stroke latency before a terminal cue stops helping.
- Final cloud-TTS provider + the warm `uk-UA` voice (ElevenLabs vs Azure) — settled when running the generation script.
