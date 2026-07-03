# Settings + Feedback + Detection (gold-dark) — design spec

Date: 2026-07-02
Track: Android UI redesign (gold-dark), **Slice 3 — new screens** (partial). Presentation-only track:
touches UI + persistence, does **not** modify the frozen pose/ball/trajectory pipeline.
Source design: claude.ai/design project "Table Tennis Coach AI Redesign"
(`feb1eaea-d763-41c9-86fe-1262790d7291`), canvas `Live Session.dc.html`, screens `8a` / `11a` / `11b`.

## 1. Goal & scope

Rebuild the **Settings** tab to the new `8a` design and add two **new** screens reached from it:
**Feedback** (`11a`) and **Detection** (`11b`). The new Settings design is a *restructure*: it removes
most inline tuning and replaces it with two link-rows plus a new Language section. The removed controls
are **folded into** the two new pages so no existing UI/functionality is lost.

**In scope:** three screens (one rebuilt, two new), reusable gold-dark components, prefs-backed
persistence, real-default seeding, navigation wiring.

**Out of scope (explicitly):**
- Driving the frozen pipeline from these settings (persist-only; no pipeline reads yet — that is Phase 3).
- Actually switching the app's locale from the Language section (selection is stored only).
- Any change to shared/ analysis/detection code or the frozen MediaPipe/ball/trajectory pipeline.

## 2. Decisions (locked with user, 2026-07-02)

1. **Wiring depth:** persist to prefs (SharedPreferences; reuse existing Room/keys where already wired).
   Controls save/restore across restart but do **not** yet drive the pipeline.
2. **Dropped Settings controls:** keep them — fold into the new Feedback/Detection pages.
3. **Custom controls:** build a small set of reusable `TTC.*` components, reused across screens.
4. **Defaults:** map to the **real** code defaults, not the mockup's illustrative slider positions.
5. **Praise-enabled default:** ON (real default), even though the mockup shows it OFF to demo the
   disabled state.
6. **Folded-control placement:** Ball-detection FPS + Distance mode → Detection page; correction chips +
   cues-per-session → Feedback page.

## 3. Navigation

- `Settings` remains a root bottom-nav destination (`navigation_settings`, `SettingsFragment`).
- Add two destinations to `app/src/main/res/navigation/nav_graph.xml`:
  `navigation_feedback` → `FeedbackFragment`, `navigation_detection` → `DetectionFragment`.
- Add actions `navigation_settings → navigation_feedback` and `→ navigation_detection`.
- Both new screens are **pushed** (not tabs): they show a **back arrow** in the header returning to
  Settings. Bottom nav stays visible with **Settings** highlighted (design shows Settings active on
  `11a`/`11b`). Keep the Settings tab selected on these child destinations (do not let NavigationUI
  clear the selection).

## 4. Design system additions

New reusable pieces (in `app/`, gold-dark house system; not shared/ — these are Android View APIs).

**Custom compound Views** (carry behavior):
- `TtcStepperView` — `−  value  +` control. XML attrs: `min`, `max`, `step`, `value`, `unitSuffix`
  (e.g. `°`, `ms`, none), `valueFormat` (int vs. 1-decimal). Emits change callback. Visuals per §7.
- `TtcSegmentedToggle` — 2–3 equal pill segments, single-select. Programmatic `entries` + `selectedIndex`
  + change callback. Reused for playing-hand (2), frame-rate (3), cues-per-session (3),
  ball-FPS (4). Visuals per §7.

**Styles / reusable layout snippets** (styling only):
- `TTC.LinkRow` — full-width card row: leading icon tile (38dp, `ttc` alt-surface, gold glyph) +
  title + subtitle + trailing chevron. Used as `<include>` with bindable id.
- `TTC.SectionHeader.Gold` — gold uppercase 10–11sp, ~0.10em letter-spacing (for in-card mini-headers
  and above-card headers with a leading gold icon).
- `TTC.Card.GoldTint` — `#221C0F` bg + `#5C4A22` stroke variant (selected-coach card, tip card).
- Full-bleed in-card divider — 1dp, `ttc_outline`, negative 16dp horizontal margin.
- Reuse existing `TTC.Slider`, `TTC.Toggle`, `TTC.Chip.Filter`, `TTC.Card`.

**New color tokens** (add to `colors.xml` dark + a sensible light mapping):
map the design's dark hexes to named tokens where not already present — `ttc_gold_tint_bg` (#221C0F),
`ttc_gold_tint_stroke` (#5C4A22), disabled-state `ttc_text_disabled` (#8A909E),
`ttc_control_knob_off` (#565C68), `ttc_slider_ring_disabled` (#2F3542), inset well `ttc_sink_deep`
(#0B0E12). Reuse existing tokens (`ttc_surface`, `ttc_outline`, `ttc_gold_bright`, `ttc_text_1/2/3`)
wherever they already match.

## 5. Screen content

### 5.1 Settings (`8a`) — rebuild `fragment_settings.xml` + `SettingsFragment.kt`
Header: title "Training settings", subtitle "Tune how your coach watches and talks to you". No back arrow.
Vertical sections (each: gold above-card header with leading icon, then a `TTC.Card`):

- **AI Coach** — "Choose your coach" + sub; `TtcSegmentedToggle` Vadym / **Ivan** / Andriy; selected-coach
  `TTC.Card.GoldTint` (gold avatar w/ initial, name, gold tagline, description). Reuses existing coach
  selection wiring/persistence.
- **Language** *(new)* — two rows split by divider: Interface language (EN/UK segmented) and Coach
  language (EN/UK segmented). Persisted to prefs only; no runtime locale switch.
- **Feedback & Detection** — two `TTC.LinkRow`s:
  - Feedback → `navigation_feedback`, sub "Cues, cadence & praise · applies to all sessions".
  - Detection → `navigation_detection`, sub "Stroke picking · thresholds, smoothing & timing".
- **Camera** — three blocks split by dividers: Video quality (dropdown, e.g. "Medium (balanced)"),
  Frame rate (`TtcSegmentedToggle` 24/30/60), Show pose skeleton (toggle).
- **Tip** card (`TTC.Card.GoldTint`): lightbulb + "TIP" + battery/quality advice copy.

### 5.2 Feedback (`11a`) — new `FeedbackFragment` + `fragment_feedback.xml`
Header: back arrow → Settings; title "Feedback"; subtitle "Applies to every session".
One `TTC.Card` with gold mini-header subsections split by full-bleed dividers:

- **Coaching** — Playing hand (`TtcSegmentedToggle` Right/Left); Voice cues (toggle — *absorbs the old
  audio-feedback on/off*); **Voice volume** slider *(folded from old volume slider; disabled when Voice
  cues off)*.
- **Corrections** *(folded)* — `TTC.Chip.Filter` group: Wrist Angle, Body Rotation, Follow Through,
  Contact Height, Elbow Position, Stroke Speed (multi-select; default set read from existing persistence).
- **Cue zones** — sub "Global — layered on top of exercise strictness". Sliders: Zone width, Significance
  threshold; toggle: Alternate cues.
- **Cadence** — sliders: Reminder interval, Pause between cues, Silence before praise, Pause after stroke;
  `TtcSegmentedToggle` **Cues per session** 3/5/10 *(folded from old feedback frequency)*.
- **Praise** — Praise enabled (master toggle); On corrections, On streak (toggles); Streak length
  (slider). Child rows + slider go to the disabled-gray state when the master toggle is off.

### 5.3 Detection (`11b`) — new `DetectionFragment` + `fragment_detection.xml`
Header: back arrow → Settings; title "Detection"; subtitle "Advanced — how strokes are picked out".
One `TTC.Card`, plain rows (title + sub on the left, control on the right):

- Camera angle — `TtcStepperView` (°)
- Peak speed threshold — stepper (torso-widths/sec, 1-decimal)
- Min peak interval — stepper (ms)
- Speed smoothing — stepper (ms)
- Walk gate — stepper (torso-widths, 1-decimal, 0 = off)
- Skip stale reps — toggle
- Pre-stroke buffer — stepper (ms)
- *(folded)* Ball detection FPS — `TtcSegmentedToggle` 10/30/60/120
- *(folded)* Distance mode — toggle

Ball-FPS + Distance mode are labelled as ball-pipeline settings (that pipeline is frozen; persist-only).

## 6. Persistence & defaults

A typed `TrainingSettingsStore` backed by `SharedPreferences` exposing get/set per field, plus a
`TrainingSettingsDefaults` object holding the seed values below. Existing wired settings (coach, audio,
volume, corrections, camera, ball-FPS, distance mode) **reuse their current preference keys** — the
implementation plan's first task is to inventory those exact keys from `SettingsFragment.kt`,
`AppSettingsActivity.kt`, `ActivitySettingsActivity.kt` so none are orphaned. New params (language,
detection/feedback tuning) get new keys.

**Seed defaults (real code values, per decision #4):**

Detection (`strokeDetector2d.ts` / `StrokeDetector2D.kt`, `CameraAngleEstimator.kt`, `LocomotionFilter.kt`):
- Camera angle override: `0°` (range ~ −45…45, step 5)
- Peak speed threshold: `1.0` (`minPeakSpeed`, step 0.1)
- Min peak interval: `500 ms` (`minPeakGapMs`, step 50)
- Speed smoothing: `300 ms` (`smoothingWindowMs`, step 50)
- Walk gate: `0.4` (`DEFAULT_MAX_TRAVEL_TORSO`, step 0.1, 0 = off)
- Skip stale reps: `ON` (`skipStaleEnabled = true`)
- Pre-stroke buffer: `1000 ms` (`DEFAULT_LOOKBACK_MS`)

Feedback (`feedbackSettings.ts` `DEFAULT_FEEDBACK_SETTINGS`):
- Playing hand: `Right`
- Voice cues: `ON` (UI-only; merges old audio-feedback toggle)
- Zone width: `×1.4` (`bandWidthMult`)
- Significance threshold: `7°` (`minMeaningfulDeltaDeg`)
- Reminder interval: `10 s` (`reminderIntervalMs = 10000`)
- Alternate cues: `ON` (`varyCues`)
- Pause between cues: `5 s` (`correctiveMinGapMs = 5000`)
- Silence before praise: `10 s` (`praiseMinSilenceMs = 10000`)
- Pause after stroke: `0.3 s` (`postStrokeGapMs = 300`)
- Praise enabled: `ON`; on-corrections `ON`; on-streak `OFF`; streak length `3`

Folded-control defaults (voice volume, ball-FPS, cues-per-session, enabled-corrections set) are read from
the existing `SettingsFragment` persistence during implementation; if a control has no existing default,
use the mockup value.

Where the design mockup's slider position differs from the real default (Significance 6→7, Zone width
0.5→1.4, Reminder 3.5→10s, Pause-between 2.4→5s, Silence 0.4→10s, Pre-stroke 0→1000, Skip-stale
OFF→ON, Praise OFF→ON), the **real default wins**.

## 7. Component visual reference (from design tokens)

Card: `ttc_surface` (#141820) bg, 1dp `ttc_outline` (#232833) stroke, 14dp radius, 16dp padding.
Section gap (Settings): 20dp. Inset/well bg: `#0B0E12`.

- **Toggle (switch):** 44×26dp, ON = gold `#E9C46A` track + dark `#16130B` knob; OFF = `#232833` track
  + `#565C68` knob. (Use existing `TTC.Toggle`.)
- **Slider:** 5dp track (`#0B0E12`), gold fill, 15dp gold thumb w/ double ring
  (`0 0 0 3px #0E1115, 0 0 0 4px #5C4A22`). Disabled: fill/thumb `#565C68`, ring `#2F3542`.
- **Segmented pill:** container `#0B0E12` bg + `#232833` stroke, 999dp radius, 3–4dp pad; active segment
  `#221C0F` bg + `#5C4A22` stroke + gold `#E9C46A` bold text; inactive text `#B4BAC5`.
- **Stepper:** container `#0B0E12` + `#232833` stroke, 9dp radius; `−`/`+` 33×36dp `#B4BAC5`; value cell
  min-width 50dp centered, JetBrains Mono 600 13.5sp `#E7EAF0`, flanked by 1dp `#232833` borders.
- **Link-row:** `TTC.Card` row, 15×16dp pad, leading 38dp icon tile (`#1A1F28` bg, gold glyph),
  title `#E7EAF0` + sub `#7F8694`, trailing chevron `#565C68`.
- **Disabled group** (Praise-off): text `#8A909E`, knobs `#565C68`, slider ring `#2F3542`.

## 8. Testing / verification

- Build: `./gradlew :app:assembleDebug` green.
- Manual: launch app, verify Settings renders per `8a`; link-rows push Feedback/Detection with back arrow
  and Settings tab highlighted; all controls reflect real defaults; change a value, navigate away/back and
  restart — value persists.
- Screenshots to `tmp/screenshots/` for visual comparison against the mockups.
- No changes to shared/ tests expected (persist-only); `./gradlew test` stays green.

## 9. Risks / notes

- Existing `SettingsFragment.kt` is large and multi-purpose; rebuild will restructure it. First plan task
  must inventory current persistence keys before removing UI, to avoid orphaning saved data.
- Bottom-nav "keep Settings selected on child destinations" needs manual handling (NavigationUI clears
  selection on non-tab destinations) — spike/verify early.
- Room uses `fallbackToDestructiveMigration()`; keep new persistence in SharedPreferences (not Room) to
  avoid schema bumps that wipe local data — unless a control already lives in Room, in which case reuse it.
