# Session History & Session Review screens — design spec

Date: 2026-07-02
Track: Android UI redesign (gold-dark), **Slice 3** (new screens)
Design source: claude.ai/design project "Table Tennis Coach AI Redesign"
(`feb1eaea-d763-41c9-86fe-1262790d7291`), canvas `Live Session.dc.html`.
Screens: **5a / 5c** (Session history, dark / light) and **6b / 7a** (Session review, dark / light).

## 1. Summary

Implement two new pushed screens in the existing Material 3 `app/` shell, styled in the
gold-dark house system (Slice 1 foundation), plus the persistence and capture needed to feed
the Session Review analytics with **real** per-session data.

The four requested "pages" are **two theme-aware screens**, each rendered in both themes:

| Page | Screen | Theme |
|------|--------|-------|
| 5a | Session History (log grouped by week) | dark |
| 5c | Session History — same layout | light |
| 6b | Session Review (analytics-forward) | dark |
| 7a | Session Review — same layout | light |

Because the app ships a `Theme.Material3.DayNight` theme with both `values/colors.xml` and
`values-night/colors.xml`, building each screen against `ttc_*` tokens yields the light
variants (5c / 7a) for free — this is **one implementation per screen**, not two.

## 2. Scope

**In scope**
- `SessionHistoryFragment` + `fragment_session_history.xml` (5a / 5c).
- `SessionReviewFragment` + `fragment_session_review.xml` (6b / 7a).
- `AccuracyLineChartView` and `ShotQualityBarView` — small custom Views (no charting dependency).
- Shared KMP `SessionAnalytics` model + `SessionAnalyticsBuilder` (pure, TDD'd on JVM).
- Room `SessionAnalyticsEntity` + `SessionAnalyticsDao` + converters; `AppDatabase` v4 → v5.
- `SessionAnalyticsRecorder` — computes + persists analytics at session-save boundary.
- Navigation: Progress → Session History → Session Review (`sessionId` arg); keep Progress tab
  highlighted.
- ~4 new color tokens (light + dark). UK + EN strings. Empty state for History.
- Tests: `SessionAnalyticsBuilderTest` (jvmTest) + week-grouping / trend mapper tests.
- Debug-only seeder (FLAG_DEBUGGABLE) for visual QA only.

**Out of scope / stubs (rendered but not wired)**
- Header **share** icon (Review) and **funnel** icon (History): decorative; stroke-type **chips**
  are the actual History filter.
- **"Train this again"** → routes to the existing training-start for that drill; **"Overview"** →
  `navigateUp()` for now.
- "View all 5 focus areas" / "tap a focus area → flagged clips": the row list renders top areas;
  the "view all" affordance is a stub (`navigateUp`/no-op) this slice.
- No Compare-to-last-session overlay, no swipe-between-sessions (canvas "try next" ideas).

## 3. Design fidelity

Extracted verbatim from the canvas mockups. Dark hex values map to existing `ttc_*` tokens; the
same layout resolves to the light palette automatically.

### 3.1 Palette mapping (dark mockup hex → token)
- `#0E1115` canvas → `ttc_canvas`
- `#141820` card fill → `ttc_surface`
- `#232833` card border → `ttc_outline`
- `#E7EAF0` primary text → `ttc_text_1`
- `#B4BAC5` / `#7F8694` secondary/tertiary → `ttc_text_2` / `ttc_text_3`
- `#E9C46A` gold (accent, curve, clean segment, active chip) → `ttc_gold_bright`
- `#2FD08A` positive trend (▲) → **new** `ttc_trend_up`
- `#E8817A` coral (error segment, top-focus bar/chip) → **new** `ttc_shot_error`
- `#1E2530` chart gridline → **new** `ttc_chart_grid`
- `#0B0E12` chart/bar track → `ttc_sink` (or a dedicated track color if contrast needs it)

New tokens to add in both `values/colors.xml` and `values-night/colors.xml`:
`ttc_trend_up`, `ttc_trend_down`, `ttc_shot_error`, `ttc_chart_grid`. (`clean` reuses
`ttc_gold_bright`; the exact **light** coral is taken from screen 7a during implementation.)

### 3.2 Session History (5a) anatomy
- **Header**: back chevron (`bg_link_icon_tile` + `ic_arrow_back`) · title "Session history"
  (`TextAppearance.TTC.Title.Screen`) · subtitle "N sessions · last 30 days"
  (`Body.Secondary`) · trailing funnel icon (decorative).
- **KPI strip** (3 cells, mono numerals): `N Sessions` · `NN% Avg accuracy` (gold) · `N.Nh Total time`.
- **Filter chips**: All (active gold pill) / Forehand / Backhand / Topspin — `TTC.Chip.Filter`.
- **Grouped list**: section headers `THIS WEEK` (count) / `LAST WEEK` / `EARLIER · <MONTH>`
  (`TTC.SectionHeader`). Each **row**: stroke-icon tile · stroke name (`Title.Card`) ·
  "`<when>` · `NN min`" (`Body.Secondary`) · accuracy `NN%` (right, mono) · trend `▲n / ▼n / —`
  (`ttc_trend_up` / `ttc_trend_down` / muted) vs the previous same-stroke session.
- **Expander**: "Show N earlier sessions ⌄" (ghost, reveals the collapsed `EARLIER` group).
- Bottom nav supplied by the shell; Progress tab stays highlighted.

### 3.3 Session Review (6b) anatomy
- **Header**: back chevron · eyebrow "SESSION REVIEW" (`Eyebrow.Gold`) · title "<drill name>" ·
  subtitle "`<when>` · `HH:MM` · `NN min`" · trailing share icon (decorative).
- **KPI strip**: `NN%` + trend `▲n` (gold, Accuracy) · `NNN` Strokes · `NNm` Duration.
- **Card "ACCURACY THROUGH SESSION"** + "Peak NN%" gold pill: `AccuracyLineChartView`
  (grid 40/60/80, gold curve, 10% gold fill, filled peak dot, hollow end dot, x-labels
  `0 min / mid / total`) + one-line caption.
- **Card "SHOT QUALITY · NNN strokes"**: `ShotQualityBarView` (rounded pill, **2 segments**:
  clean gold + error coral) + legend `Clean N` / `Error N`.
- **Card "TOP FOCUS AREAS"**: ranked rows (name · `N×` · rounded bar · chevron), first row
  tagged "TOP FOCUS" (coral chip). "View all 5 focus areas ›" (stub).
- **Card "AI COACH SUMMARY"** (`TTC.Card.GoldTint`, lightbulb icon): templated summary text.
- **Actions**: "Train this again" (`TTC.Button.Primary`) · "Overview" (`TTC.Button.Ghost`).

### 3.4 Chart geometry (from the SVG)
`AccuracyLineChartView` (viewBox 0 0 300 130): value→Y maps the 40–80 domain to Y 120→10
(`y = 10 + (80 − v)/40 × 110`); auto-expand domain if data falls outside 40–80. N points spaced
evenly across width; gold polyline (2.5dp), fill `ttc_gold_bright @ 10%`, filled dot at the peak
bucket, hollow circle at the last bucket, three gridlines (`ttc_chart_grid`).
`ShotQualityBarView`: height ~12dp, fully rounded, track `ttc_sink`; clean width =
`clean/total`, error width = `error/total`.

## 4. Architecture

### 4.1 Shared (KMP, `shared/commonMain`) — the testable core
`analysis/SessionAnalytics.kt`:
```
data class SessionAnalytics(
  val sessionId: String,
  val accuracyTimeline: List<Float>,   // bucketed accuracy %, up to ~12 points
  val peakAccuracy: Float,
  val peakBucketIndex: Int,
  val cleanCount: Int,                 // strokes with score >= 80
  val errorCount: Int,                 // strokes with score < 80
  val focusAreas: List<FocusArea>,     // ranked desc by count
  val summaryText: String,             // templated
)
data class FocusArea(val type: CorrectionType, val count: Int)
```
`analysis/SessionAnalyticsBuilder.kt` — pure: `build(sessionId, results: List<AnalysisResult>,
feedback: List<FeedbackItem>): SessionAnalytics`.
- **Timeline**: bucket `results` (sorted by `timestamp`) into ≤12 windows; each bucket % =
  `count(score ≥ 80) / bucketSize × 100`. `peak` = max bucket.
- **Shot quality**: `clean = count(score ≥ 80)`, `error = size − clean`. (Two-way split per the
  chosen decision; no late bucket, no CorrectionType heuristic.)
- **Focus areas**: aggregate feedback by `CorrectionType`, drop `GENERAL`, rank desc, take top N.
  Design names map 1:1 (`WRIST` → "Wrist angle", `BODY_ROTATION` → "Body rotation",
  `ELBOW_POSITION` → "Elbow position", plus `FOLLOW_THROUGH`, `CONTACT_HEIGHT`, `STROKE_SPEED`).
- **Summary**: deterministic template over the real stats (peak, start-vs-end delta, dominant
  focus area). No LLM.

Placement follows the KMP split rule (analysis logic in `shared`, proven on JVM before Android).

### 4.2 Persistence (`app/`)
`models/SessionAnalyticsEntity.kt` — 1:1 with `training_sessions` (PK `sessionId`), JSON columns
via `org.json` converters (mirrors `PersonalBaselineEntity`): `accuracyTimelineJson`,
`focusAreasJson`, plus `peakAccuracy`, `peakBucketIndex`, `cleanCount`, `errorCount`,
`summaryText`, `generatedAtMs`. `db/SessionAnalyticsDao.kt`: `upsert`, `getForSession(id)`.
`AppDatabase` v4 → v5 (adds the entity). Still `fallbackToDestructiveMigration()` — **the bump
wipes local sessions** (project convention; acceptable pre-release).

### 4.3 Capture (`app/`) — minimal frozen touch
`SessionAnalyticsRecorder` (new): at the session-save boundary
(`TrainingActivity.saveSessionToCloud()` / `CloudSyncManager.saveTrainingFromState()`,
where `stateManager` is already read for aggregates) it reads
`stateManager.getAnalysisResults()` + feedback, calls `SessionAnalyticsBuilder.build(...)`,
and writes `SessionAnalyticsEntity` for the new `sessionId`.
- **One new call** at an existing boundary; **no change to per-frame / per-stroke frozen logic**
  (`PoseAnalysisProcessor`, `MotionAnalyzer`, `FeedbackGenerator`, `TrainingStateManager` stay
  intact — the recorder only *reads* already-retained state).
- Pipeline-agnostic seam: the Phase-3 RTMPose port later feeds the same builder instead of
  re-implementing capture.

### 4.4 Read path (`app/`)
- **History**: `TrainingDao.getSessionsForUser(userId)` (existing `Flow`). A mapper groups by
  week (This week / Last week / Earlier · <month>) using `java.time` in the app layer
  (presentation grouping, not drill logic), computes KPI strip from the last-30-days slice, and
  computes each row's trend vs the previous same-`exerciseName` session.
- **Review**: `TrainingDao.getSessionById(sessionId)` (existing) + `SessionAnalyticsDao
  .getForSession(sessionId)`. If a session predates this feature and has no analytics row (e.g.
  a Firestore-synced one), Review shows the KPI strip from `TrainingSession` aggregates and
  degrades the chart / shot-quality / focus cards to a quiet "no detailed analytics" state.

### 4.5 Navigation
`nav_graph.xml`: add `navigation_session_history` (Progress → it) and `navigation_session_review`
(`<action>` from history, `<argument name="sessionId" type="string">`, Bundle). Progress gets a
"Session history" tile below the Skills card (`ProgressFragment`, existing click pattern).
Extend the `MainActivity` `addOnDestinationChangedListener` to keep `navigation_progress` checked
for both new destinations. Back button mirrors the Feedback/Detection header pattern
(`btn_back` → `navigateUp()`). Bottom nav is provided by the shell.

## 5. Empty state
When `getSessionsForUser` is empty, History shows a real empty state (icon + "No sessions yet —
start a drill" + a primary CTA to Drills). No sample data. Review is reachable only from a real
row. A FLAG_DEBUGGABLE-gated seeder inserts representative sessions + analytics for local visual
QA only; it is never shown in release and does not alter the production empty-state behavior.

## 6. Theming
All new layouts use `ttc_*` tokens and existing `TTC.*` styles, so 5c / 7a resolve via
`values-night`. Add `ttc_trend_up`, `ttc_trend_down`, `ttc_shot_error`, `ttc_chart_grid` to both
color files. Custom Views read colors from theme attrs / `ContextCompat` (never hardcoded hex).

## 7. Strings
All new copy added to `values/strings.xml` (EN) and `values-uk/strings.xml` (UK): screen titles,
subtitles, KPI labels, section headers, chip labels, empty-state copy, card titles, focus-area
names, button labels, content descriptions.

## 8. Testing
- `SessionAnalyticsBuilderTest` (jvmTest) — synthetic `List<AnalysisResult>` → assert timeline
  buckets, peak, clean/error counts, focus-area ranking, summary basics. **Core TDD target.**
- Week-grouping + trend mapper unit tests (app `src/test`).
- Custom-View coordinate math extracted as pure functions with unit tests (value→Y, segment
  widths).
- Green gates: `./gradlew :shared:jvmTest` and `./gradlew :app:assembleDebug`.
- Manual: seed via debug seeder, verify 5a/6b (dark) and 5c/7a (light via system theme) on device.

## 9. Risks & decisions
- **Destructive DB migration** (v4 → v5) wipes local sessions on update — accepted per project
  convention (still pre-release).
- **Two-way shot quality** (clean/error) diverges from the mockup's three-way bar by explicit
  decision (honesty over invented "late").
- **Summary is templated**, not model-generated — deterministic, driven by real stats.
- **Frozen-code touch** is limited to one read-and-record call at the save boundary; no per-stroke
  logic changes. Sanctioned for the capture requirement.
- Old/synced sessions without an analytics row degrade gracefully in Review.

## 10. Deliverables checklist
- [ ] `SessionAnalytics` + `SessionAnalyticsBuilder` (shared) + tests
- [ ] `SessionAnalyticsEntity` + `SessionAnalyticsDao` + converters + `AppDatabase` v5
- [ ] `SessionAnalyticsRecorder` + save-boundary wiring
- [ ] `AccuracyLineChartView` + `ShotQualityBarView`
- [ ] `SessionHistoryFragment` + `fragment_session_history.xml` (+ empty state, filter, grouping, trend)
- [ ] `SessionReviewFragment` + `fragment_session_review.xml`
- [ ] Progress "Session history" tile + nav graph + args + MainActivity tab-highlight
- [ ] New color tokens (light + dark), UK + EN strings
- [ ] Debug-only seeder (FLAG_DEBUGGABLE)
- [ ] Tests green: `:shared:jvmTest`, `:app:assembleDebug`
