---
description: "Task list for Personal Baseline Calibration (Stage 1 · Phase 1)"
---

# Tasks: Personal Baseline Calibration

**Input**: Design documents from [specs/003-stage1-calibration/](./)
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [phase-0-kickoff.md](phase-0-kickoff.md)

**Tests**: Included where the plan explicitly calls them out (Phase 0 unit tests ✅ done, Phase 1 DAO round-trip, Phase 3 instrumented end-to-end). No broad TDD mandate beyond those.

**Organization**: Grouped by user story. US1 (first-time calibration) is the MVP — persistence + capture UI together. US2 (recalibration) adds versioning. US3 (live quality feedback) adds per-rep outlier surfacing during capture.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1, US2, US3 — maps to spec.md priorities

## Path Conventions

- Shared KMP logic → [shared/src/commonMain/kotlin/com/ttcoachai/shared/](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/)
- Shared tests → [shared/src/commonTest/kotlin/com/ttcoachai/shared/](../../shared/src/commonTest/kotlin/com/ttcoachai/shared/), JVM-only into `jvmTest`
- Android app → [app/src/main/java/com/ttcoachai/](../../app/src/main/java/com/ttcoachai/)
- Android unit tests → [app/src/test/java/com/ttcoachai/](../../app/src/test/java/com/ttcoachai/)
- Instrumented tests → [app/src/androidTest/java/com/ttcoachai/](../../app/src/androidTest/java/com/ttcoachai/)

---

## Phase 1: Setup

**Purpose**: No new project init — Stage 1 continues on `003-stage1-calibration` with existing KMP + Android modules wired up.

- [x] T001 Verify `./gradlew :shared:jvmTest` and `./gradlew test` are green on branch `003-stage1-calibration` before starting Phase 1 work (baseline CI state check)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Pure KMP data model + derivation logic shared by all three user stories. **Phase 0 of the plan — already complete.** Listed here for traceability; no further work unless a gap is discovered.

- [x] T002 `MetricStats` data class in [shared/src/commonMain/kotlin/com/ttcoachai/shared/models/MetricStats.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/models/MetricStats.kt)
- [x] T003 `PersonalBaseline` data class in [shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PersonalBaseline.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PersonalBaseline.kt)
- [x] T004 `BaselineDeriver` in [shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineDeriver.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineDeriver.kt) (2σ single-pass outlier exclusion, quality score = 1 − mean(CV))
- [x] T005 `BaselineRule` sealed type in [shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineRule.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineRule.kt) (data only — not evaluated in Stage 1 Phase 1)
- [x] T006 Unit tests in [shared/src/jvmTest/kotlin/com/ttcoachai/shared/analysis/BaselineDeriverTest.kt](../../shared/src/jvmTest/kotlin/com/ttcoachai/shared/analysis/BaselineDeriverTest.kt) — happy path, insufficient reps, outlier exclusion, zero variance, determinism

**Checkpoint**: Shared baseline math + data model exist and are unit-tested. All three user stories can now begin.

---

## Phase 3: User Story 1 — First-time calibration (Priority: P1) 🎯 MVP

**Goal**: A new user runs through an onboarding → 15-rep capture → review flow for one drill type (forehand shadow). The app derives a `PersonalBaseline` via `BaselineDeriver` and persists it locally.

**Independent Test**: Launch app on clean install → tap "Calibrate: Forehand Shadow" → pass camera onboarding → perform 15 reps → review screen shows per-metric mean ± σ and a quality score → tap "Save" → query Room and confirm one `PersonalBaseline` row exists for `drillType = "forehand_shadow"`. Meets FR-1, FR-2, FR-5, FR-8, FR-9 and all AC of User Story 1.

### Persistence layer (US1)

- [x] T007 [P] [US1] `PersonalBaselineEntity` Room entity in [app/src/main/java/com/ttcoachai/models/PersonalBaselineEntity.kt](../../app/src/main/java/com/ttcoachai/models/PersonalBaselineEntity.kt) — fields mirror `PersonalBaseline` (drill_type, created_at_ms, rep_count, quality_score, handedness, metric_stats_json, phase_durations_json, excluded_rep_indices_json, is_active)
- [x] T008 [P] [US1] `BaselineConverters` `@TypeConverter` in [app/src/main/java/com/ttcoachai/db/BaselineConverters.kt](../../app/src/main/java/com/ttcoachai/db/BaselineConverters.kt) — serialize `Map<String, MetricStats>` and `List<Int>` to JSON (used `org.json` rather than kotlinx-serialization — not already in the module and avoids pulling a serialization plugin into shared/)
- [x] T009 [US1] `PersonalBaselineDao` in [app/src/main/java/com/ttcoachai/db/PersonalBaselineDao.kt](../../app/src/main/java/com/ttcoachai/db/PersonalBaselineDao.kt) — `insert`, `getActiveByDrillType(drillType): Flow<PersonalBaselineEntity?>`, `getAllForDrillType(drillType): List<PersonalBaselineEntity>`, `archiveActive(drillType)`; `archiveAndInsert(drillType, entity)` also included (T020 trivia in same file) so recalibration is a pure UI change
- [x] T010 [US1] Register new entity + DAO + TypeConverters in [app/src/main/java/com/ttcoachai/db/AppDatabase.kt](../../app/src/main/java/com/ttcoachai/db/AppDatabase.kt) (bump `version`; `fallbackToDestructiveMigration()` acceptable per plan §Phase 1)
- [x] T011 [US1] `PersonalBaselineRepository` in [app/src/main/java/com/ttcoachai/repository/PersonalBaselineRepository.kt](../../app/src/main/java/com/ttcoachai/repository/PersonalBaselineRepository.kt) — local-only; `saveBaseline` routes through `archiveAndInsert` from day one to preserve the "≤ 1 active row per drill type" invariant
- [x] T012 [US1] DAO round-trip unit test in [app/src/test/java/com/ttcoachai/db/PersonalBaselineDaoTest.kt](../../app/src/test/java/com/ttcoachai/db/PersonalBaselineDaoTest.kt) — 4 tests: insert→query round-trip, empty-query null, `archiveAndInsert` single-active invariant, cross-drill isolation

### Capture pipeline (US1)

- [x] T013 [US1] `CalibrationStateManager` singleton in [app/src/main/java/com/ttcoachai/managers/CalibrationStateManager.kt](../../app/src/main/java/com/ttcoachai/managers/CalibrationStateManager.kt) — session state (drill type, target rep count, collected `List<DetectedStroke>` + `List<AnalysisResult>`, `acceptedRepCount: StateFlow<Int>`, observed-frame timing for `frameIntervalMs`); volatile/DCL pattern; no Context dependency since all state is in-memory
- [x] T014 [US1] Calibration-mode routing in [app/src/main/java/com/ttcoachai/processors/PoseAnalysisProcessor.kt](../../app/src/main/java/com/ttcoachai/processors/PoseAnalysisProcessor.kt) — added `Mode { TRAINING, CALIBRATION }`, optional `calibrationStateManager` ctor arg, phase-boundary frame tracking (live path previously emitted only phase enum, so we observe transitions and reconstruct `DetectedStroke` at stroke finalization), dual routing: training path unchanged, calibration path skips audio feedback and records into `CalibrationStateManager`

### UI flow (US1)

- [x] T015 [US1] `CalibrationActivity` in [app/src/main/java/com/ttcoachai/calibration/CalibrationActivity.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationActivity.kt) + [activity_calibration.xml](../../app/src/main/res/layout/activity_calibration.xml) — mirrors TrainingActivity shape (implements `LandmarkerListener`, owns `PoseAnalysisProcessor` in `Mode.CALIBRATION`), hosts `CameraFragment` in `camera_preview_container` for the full lifetime and overlays phase fragments on top; drill hardcoded to `forehand_shadow`; back-navigation shows a discard dialog once reps have been captured
- [x] T016 [US1] `CalibrationOnboardingFragment` in [app/src/main/java/com/ttcoachai/calibration/CalibrationOnboardingFragment.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationOnboardingFragment.kt) + [fragment_calibration_onboarding.xml](../../app/src/main/res/layout/fragment_calibration_onboarding.xml) — static camera-setup instructions card + "Start Capture" CTA satisfies FR-9 for MVP; live pose-landmarker-based full-body-visibility gating deferred to a follow-up
- [x] T017 [US1] `CalibrationCaptureFragment` in [app/src/main/java/com/ttcoachai/calibration/CalibrationCaptureFragment.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationCaptureFragment.kt) + [fragment_calibration_capture.xml](../../app/src/main/res/layout/fragment_calibration_capture.xml) — rep counter overlay bound to `CalibrationStateManager.acceptedRepCount` via `repeatOnLifecycle`; auto-advance at target, "Finish Early" surfaces at ≥ floor; discard dialog handled by Activity
- [x] T018 [US1] `CalibrationReviewFragment` in [app/src/main/java/com/ttcoachai/calibration/CalibrationReviewFragment.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationReviewFragment.kt) + [fragment_calibration_review.xml](../../app/src/main/res/layout/fragment_calibration_review.xml) — derives baseline from snapshot, renders per-metric mean ± σ and phase durations, low-quality (<0.6) banner non-blocking per Key Decision #5, Save persists via repository, Redo returns to onboarding
- [x] T019 [US1] Entry point: outlined "Calibrate: Forehand Shadow" button in [fragment_dashboard.xml](../../app/src/main/res/layout/fragment_dashboard.xml), wired in [DashboardFragment](../../app/src/main/java/com/ttcoachai/fragment/DashboardFragment.kt). Also patched [CameraFragment](../../app/src/main/java/com/ttcoachai/fragment/CameraFragment.kt) to skip standalone-processor setup when hosted by `CalibrationActivity`, and registered the activity in [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml). Handedness: right-handed assumed (selector deferred per spec §Open Questions)

**Checkpoint**: User Story 1 fully functional — user can calibrate forehand shadow end-to-end and the baseline persists across app restarts. MVP delivered.

---

## Phase 4: User Story 2 — Recalibration (Priority: P2)

**Goal**: An existing user with a saved baseline can rerun calibration; the prior baseline is archived (not deleted) and the new one becomes active. Interrupted recalibration leaves the original untouched.

**Independent Test**: Seed a baseline in Room (via US1 flow or test fixture) → enter `CalibrationActivity` → UI shows "Recalibrate" instead of "Calibrate" → complete 15 reps → confirm only the new baseline has `isActive = true` and the prior one is archived with its original `createdAtMs` preserved. Also: start recalibration, kill process mid-flow, reopen — original baseline still `isActive`. Meets FR-5 (versioning) and both AC of US2.

### Implementation for User Story 2

- [x] T020 [US2] `archiveAndInsert` landed as part of T009 — transactionally flips the active row and inserts the new one in a single DAO method
- [x] T021 [US2] Repository `saveBaseline` routes through `archiveAndInsert` (shipped in T011); `getBaselineHistory(drillType): List<PersonalBaseline>` exposed for drift analysis (suspend flavor rather than Flow — history is a snapshot, not a continuously-observed stream)
- [x] T022 [US2] Recalibration copy: `CalibrationActivity.showOnboardingWithBaselineContext` reads the active baseline via repository and passes `ARG_EXISTING_REP_COUNT` / `ARG_EXISTING_CREATED_AT_MS` into `CalibrationOnboardingFragment`, which swaps the Start-Capture button label to "Recalibrate (current: N reps, Δt days old)" when present
- [x] T023 [US2] Discard-on-exit is already correct by construction — singleton holds only in-memory state, process death wipes it; back-navigation explicitly calls `discardSession` after confirm dialog; `saveBaseline` is the only Room path. Nothing else required.
- [x] T024 [US2] Versioning tests live in [PersonalBaselineDaoTest](../../app/src/test/java/com/ttcoachai/db/PersonalBaselineDaoTest.kt) (landed with T012): `archiveAndInsert_keeps_exactly_one_active_row` verifies single-active invariant and that v1's original `createdAtMs` survives archival; `archiveAndInsert_does_not_affect_other_drill_types` covers cross-drill isolation

**Checkpoint**: Both US1 and US2 are independently functional — first-timers and recalibrators both complete successfully, with versioning preserved.

---

## Phase 5: User Story 3 — Live quality feedback during calibration (Priority: P3)

**Goal**: During capture, the user sees per-rep quality indicators and outlier warnings. Excluded reps are flagged in real time and not counted toward the 15-rep target.

**Independent Test**: During capture, perform 14 consistent reps plus one deliberately sloppy rep (exaggerated wrist angle). UI flashes "Rep excluded — large deviation" on the outlier; rep counter stays at 14; continuing with one more good rep reaches 15 and auto-advances to review, where `excludedRepIndices` contains the outlier index. Meets FR-3, FR-4 live-surface requirement, and the AC of US3.

### Implementation for User Story 3

- [x] T025 [US3] Live outlier detection in `CalibrationStateManager.recordStroke`: after a warmup of 3 accepted reps, computes running mean/σ across both technique metrics and phase durations; any rep > 2σ on any metric is routed to `_excludedAttempts` instead of `_strokes` (accepted counter doesn't advance), and an `OutlierDetected(attemptedRepIndex, metricKey, deviationSigmas)` event is emitted on the `outlierEvents` SharedFlow
- [x] T026 [US3] Capture fragment now collects `outlierEvents` via `repeatOnLifecycle` and shows a 2.5 s banner ("Rep excluded — {metric} off by {σ}σ"); rep-counter pill gained a yellow "+{N} excluded" subtext bound to `excludedCount` StateFlow
- [x] T027 [US3] Low-quality banner in `CalibrationReviewFragment` — triggered when `qualityScore < 0.6` (Key Decision #5), non-blocking (Save remains enabled per FR-4). Shipped as part of T018; marking here for traceability.
- [x] T028 [US3] Excluded-reps summary — review fragment renders `"%d rep(s) excluded as outliers"` from `baseline.excludedRepIndices.size`. Shipped as part of T018. The per-metric "dominant deviating metric" detail lives on `ExcludedAttempt` in the state manager — not surfaced in the review UI yet because `PersonalBaseline` only carries indices; promote to a spec FR if the per-rep reason needs to persist.

**Checkpoint**: All three user stories deliverable. Live quality is additive over US1's silent-capture flow.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Plan §Phase 3 deliverables — dev-only debug tooling + end-to-end instrumented coverage + doc updates. Not blocking for first real-world calibration test but required before moving to Stage 1 Phase 2 (rule engine).

- [x] T029 [P] `BaselineDebugActivity` renders a plain-text metric/phase dump of the active baseline (per-metric mean/std/min/max/n + excludedRepIndices), runtime-gated by `ApplicationInfo.FLAG_DEBUGGABLE` (the project doesn't enable BuildConfig). Histogram rendering + slider preview deferred to Phase 7 per original note.
- [x] T030 [P] `BaselineDumpReceiver` registered on action `com.ttcoachai.debug.DUMP_BASELINE`; dumps the active baseline as pretty-printed JSON under the `BaselineDump` logcat tag (`adb logcat -s BaselineDump:V`). Refuses on non-debuggable APKs even though the manifest receiver is always present.
- [x] T031 `CalibrationFlowTest` (instrumented) — Espresso smoke test (activity launches, onboarding card + Start-Capture CTA displayed) + real-SQLite round-trip that derives a baseline from synthetic pipeline output and verifies repository read-back. Full mocked-pose-stream-through-MediaPipe E2E deferred — plumbing synthetic frames into the live pipeline is out of scope for this phase.
- [x] T032 [P] [CLAUDE.md](../../CLAUDE.md) updated — added `calibration/` and `debug/` packages to project structure, added PersonalBaselineEntity note, recorded three new gotchas: live-DetectedStroke reconstruction, `CameraFragment` carve-out list (must be maintained when adding new `LandmarkerListener` hosts), and `BaselineConverters` uses `org.json` not kotlinx-serialization
- [x] T033 [P] [spec.md §Open Questions](spec.md#open-questions) — handedness policy explicitly deferred to Stage 1 Phase 5 (right-handed default, null column = unspecified, selector lands alongside handedness-aware metric mirroring); recalibration cadence deferred indefinitely (versioning infra gives us history when we need to answer the question)
- [x] T034 [plan.md](plan.md) Phases 1/2/3 ticked with actual deliverables and file links

---

## Phase 7: Drill Parameter Editor with Movement Preview (Dev-Only, Optional)

**Purpose**: Give a dev (Ivan) a visual tool for tuning `BaselineRule` parameters against a replayed reference rep, so threshold tuning is honest and iterative instead of guess-and-recapture. Expands T029's slider-preview idea into a full interactive screen.

**Scope guardrails** (per [docs/STAGED_ROADMAP.md](../../docs/STAGED_ROADMAP.md) "Dev-only debug UI for tuning rules; NO end-user authoring UI"): behind `BuildConfig.DEBUG`, no main-app entry point, no Play Store surface. Same code can be promoted to user-facing in Stage 1.5+ if/when end-user drill authoring is unblocked.

**Architecture principle (important):** the preview **replays a recorded pose sequence** (captured calibration rep or bundled fixture) and re-evaluates rules against those frames when sliders move. It does **NOT** synthesize poses from parameters — inverse-kinematics from scalar params to a pose is ambiguous and out of scope. Sliders affect the pass/fail overlay, not the geometry.

**Independent Test**: In a debug build, open "Drill Parameter Editor" → pick `forehand_shadow` + active baseline + one reference rep from the latest capture → see the rep animate with a humanized stick figure and racket → drag `kSigma` slider on the wrist-angle `ConsistencyRule` from 2.0 to 1.0 → affected frames tint red in real time → tap "Export" → JSON of edited rule overrides appears in logcat (or a saved `DrillConfig` row, depending on T039 choice).

### Implementation for Phase 7

- [x] T035 [Editor] [BaselinePreviewActivity](../../app/src/main/java/com/ttcoachai/debug/BaselinePreviewActivity.kt) + [activity_baseline_preview.xml](../../app/src/main/res/layout/activity_baseline_preview.xml) — debug-gated entry loads the active `forehand_shadow` baseline + a bundled reference rep (`assets/fixtures/forehand_drive.json`, loader in [AssetPoseFrameLoader.kt](../../app/src/main/java/com/ttcoachai/debug/AssetPoseFrameLoader.kt)); timeline SeekBar scrubber + play/pause driven by a `Handler` loop at fixture `intervalMs`. Reference-rep picker reduced to "bundled fixture only" for MVP — replaying a captured rep needs raw-frame persistence, which isn't in `CalibrationStateManager`'s contract today.
- [x] T036 [Editor] [OverlayView](../../app/src/main/java/com/ttcoachai/OverlayView.kt) — additive only, existing live-camera path untouched. Added `setPoseFrame(PoseFrame?)` (thin wrapper around the existing `setSynchronizedFrame` ingress) and `setJointTint((Int) -> Int?)`; per-landmark tint supersedes the default point paint on matching indices.
- [x] T037 [Editor] Rule-tuning panel wired in `BaselinePreviewActivity`. Rules for the loaded baseline come from the new shared [BaselineRuleFactory](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineRuleFactory.kt) (2σ ConsistencyRule per non-degenerate metric, 25% RhythmRule per phase). Sliders cover all three variants; per-frame pass/fail runs through the new shared [FrameRuleEvaluator](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/FrameRuleEvaluator.kt) (RhythmRule returns null at frame level — the slider only affects the exported JSON). On slider change the activity recomputes failing metrics for the current frame and tints the corresponding landmarks via `OverlayView.setJointTint`; slider labels show the running "fails at N frames" count.
- [x] T038 [P] [Editor] Humanization Tier 1 — `OverlayView.setHumanizationEnabled(true)` draws a filled torso polygon (shoulders↔hips), capsule-stroke bones for arms + legs, head circle sized to shoulder-nose distance, and a racket stick from right wrist along wrist→index with a forearm-direction fallback when index visibility < 0.5. Toggled via a `SwitchMaterial` in the activity.
- [x] T039 [Editor] Export goes to **logcat + clipboard** (option b, the recommended path in tasks.md). "Export tuned rules" dumps pretty-printed JSON under the `BaselineDump` tag and copies the same payload to the clipboard via `ClipboardManager`; no schema change. Upgrade to a `DrillConfigEntity` table when preset drills land in Stage 1 Phase 4.
- [x] T040 [P] [Editor] Docs cross-links: [CLAUDE.md](../../CLAUDE.md) debug/ entry extended with `BaselinePreviewActivity` and the "PersonalBaseline → BaselineRule mapping lives in shared/BaselineRuleFactory" gotcha; [plan.md](plan.md) §Phase 3 note added that T029 stays histogram-free and the interactive parameter editor is T035–T039.

**Checkpoint**: Ivan can open the debug editor, replay a real captured rep, tune rule tolerances with immediate visual feedback, and export the tuned config. No impact on user-facing app.

**Effort estimate**: ~2 dev days for T035–T037 (functional tool), +0.5–1 day for T038 (Tier 1 humanization — optional, lands separately), +0.5 day for T039+T040.

**Out of scope for Phase 7** (explicitly deferred):
- Tier 2/3 humanization (Rive/Lottie humanoid, 3D model) — too much for a dev screen
- Synthesizing poses from parameters (inverse kinematics) — architectural no-go, see §principle above
- Editing legacy [ExerciseParameters](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/models/ExerciseParameters.kt) — dead-end path, calibration-derived `BaselineRule` is the target model
- User-facing entry point — roadmap scope guardrail
- Live-camera tuning (tune against what's happening *now*) — interesting but out of scope; replay-against-recording is deterministic and enough

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — smoke-test current green build state
- **Foundational (Phase 2)**: Already complete (Phase 0) — unblocks all user stories
- **User Stories (Phase 3-5)**:
  - US1 is the MVP and should land first
  - US2 extends US1's persistence layer — hard dependency on T009/T011
  - US3 extends US1's capture UI — hard dependency on T013/T017
- **Polish (Phase 6)**: Depends on all user stories being complete
- **Drill Parameter Editor (Phase 7, optional)**: Depends on US1 persistence (T011) and at least one completed calibration to have an active baseline to load; narrows T029 scope. Can be skipped entirely for Stage 1 MVP

### User Story Dependencies

- **US1 (P1)**: Depends only on Foundational (Phase 2 ✅). Fully independent MVP.
- **US2 (P2)**: Depends on US1 persistence (T009, T011). Does not depend on US3.
- **US3 (P3)**: Depends on US1 capture (T013, T017). Does not depend on US2. US2 and US3 can land in either order after US1.

### Within Each User Story

- Models/entities before DAOs before repositories before UI
- T007, T008 can run in parallel; T009 depends on both
- T015, T016 can be scaffolded in parallel; T017/T018 depend on the state manager + repository
- UI fragments that share a file get serialized (T018/T027/T028 all touch `CalibrationReviewFragment.kt`)

### Parallel Opportunities

- T007 + T008 (entity + converters, different files)
- T015 + T016 (activity + onboarding fragment, different files)
- T029 + T030 + T032 + T033 (polish tasks in different files)

---

## Parallel Example: User Story 1 persistence layer

```text
# Kick off in parallel (different files, no cross-dependencies):
Task: "T007 PersonalBaselineEntity Room entity in app/.../models/PersonalBaselineEntity.kt"
Task: "T008 BaselineConverters @TypeConverter in app/.../db/BaselineConverters.kt"

# After both land, serialize:
Task: "T009 PersonalBaselineDao in app/.../db/PersonalBaselineDao.kt"
Task: "T010 Register in AppDatabase.kt"
Task: "T011 PersonalBaselineRepository in app/.../repository/PersonalBaselineRepository.kt"
Task: "T012 DAO round-trip unit test"
```

---

## Implementation Strategy

### MVP first (US1 only)

1. T001 smoke-test build
2. T007–T012 persistence layer
3. T013–T014 capture pipeline
4. T015–T019 UI flow
5. **Stop and validate** — run through a real calibration on device; confirm baseline round-trips
6. Commit + demo

### Incremental delivery

1. MVP (US1) → test → commit
2. Add US2 (recalibration) → test versioning → commit
3. Add US3 (live quality) → test outlier surfacing → commit
4. Polish (debug tools + instrumented test) → commit

### Solo developer reality (per user memory — Ivan, solo on TT_Coach)

Linear sequence US1 → US2 → US3 → Polish. Parallel-file opportunities still worth batching within a single work session to reduce context switches. Commit after each logical task per user's commit-cadence preference.

---

## Notes

- `[P]` = different file, no incomplete-task dependency
- `[Story]` traces each task back to the spec's acceptance criteria
- Phase 0 shared code is **already landed on `003-stage1-calibration`** — see commit `5f288fc feat(shared): add BaselineDeriver + PersonalBaseline model (Phase 0)`
- Room migration uses `fallbackToDestructiveMigration()` per plan — switch to explicit migrations before Play Store (Stage 1 Phase 6)
- Any modification to existing `StrokePhaseDetector` / `StrokeAnalyzer` contracts is out of scope for this feature (plan §Technical Context constraints)
