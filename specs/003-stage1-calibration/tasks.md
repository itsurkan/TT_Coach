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

- [ ] T001 Verify `./gradlew :shared:jvmTest` and `./gradlew test` are green on branch `003-stage1-calibration` before starting Phase 1 work (baseline CI state check)

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

- [ ] T007 [P] [US1] `PersonalBaselineEntity` Room entity in [app/src/main/java/com/ttcoachai/models/PersonalBaselineEntity.kt](../../app/src/main/java/com/ttcoachai/models/PersonalBaselineEntity.kt) — fields mirror `PersonalBaseline` (drill_type, created_at_ms, rep_count, quality_score, handedness, metric_stats_json, phase_durations_json, excluded_rep_indices_json, is_active)
- [ ] T008 [P] [US1] `BaselineConverters` `@TypeConverter` in [app/src/main/java/com/ttcoachai/db/BaselineConverters.kt](../../app/src/main/java/com/ttcoachai/db/BaselineConverters.kt) — serialize `Map<String, MetricStats>` and `List<Int>` to JSON (use kotlinx-serialization already in the module)
- [ ] T009 [US1] `PersonalBaselineDao` in [app/src/main/java/com/ttcoachai/db/PersonalBaselineDao.kt](../../app/src/main/java/com/ttcoachai/db/PersonalBaselineDao.kt) — `insert`, `getActiveByDrillType(drillType): Flow<PersonalBaselineEntity?>`, `getAllForDrillType(drillType): List<PersonalBaselineEntity>`, `archiveActive(drillType)` (depends on T007)
- [ ] T010 [US1] Register new entity + DAO + TypeConverters in [app/src/main/java/com/ttcoachai/db/AppDatabase.kt](../../app/src/main/java/com/ttcoachai/db/AppDatabase.kt) (bump `version`; `fallbackToDestructiveMigration()` acceptable per plan §Phase 1) (depends on T007, T008, T009)
- [ ] T011 [US1] `PersonalBaselineRepository` in [app/src/main/java/com/ttcoachai/repository/PersonalBaselineRepository.kt](../../app/src/main/java/com/ttcoachai/repository/PersonalBaselineRepository.kt) — local-only (no Firestore branch), mirrors shape of [TrainingRepository](../../app/src/main/java/com/ttcoachai/repository/TrainingRepository.kt); exposes `saveBaseline(PersonalBaseline)` and `getActiveBaseline(drillType): Flow<PersonalBaseline?>` with entity↔domain mapping (depends on T009)
- [ ] T012 [US1] DAO round-trip unit test in [app/src/test/java/com/ttcoachai/db/PersonalBaselineDaoTest.kt](../../app/src/test/java/com/ttcoachai/db/PersonalBaselineDaoTest.kt) — insert baseline → query by `drillType` → deserialized `metricStats`/`phaseDurations` equal originals (Robolectric + in-memory Room)

### Capture pipeline (US1)

- [ ] T013 [US1] `CalibrationStateManager` singleton in [app/src/main/java/com/ttcoachai/managers/CalibrationStateManager.kt](../../app/src/main/java/com/ttcoachai/managers/CalibrationStateManager.kt) — session state (drill type, target rep count, collected `List<DetectedStroke>` + `List<AnalysisResult>`, rep progress flow); follow volatile/DCL pattern from [TrainingStateManager](../../app/src/main/java/com/ttcoachai/managers/TrainingStateManager.kt)
- [ ] T014 [US1] Add calibration-mode routing to [app/src/main/java/com/ttcoachai/processors/PoseAnalysisProcessor.kt](../../app/src/main/java/com/ttcoachai/processors/PoseAnalysisProcessor.kt) — when mode = calibration, forward detected strokes + analysis results to `CalibrationStateManager` instead of `FeedbackGenerator`. Additive only; must not change existing drill-mode behavior per plan §Technical Context

### UI flow (US1)

- [ ] T015 [P] [US1] `CalibrationActivity` entry point in [app/src/main/java/com/ttcoachai/calibration/CalibrationActivity.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationActivity.kt) — drill-type selection (hardcode `forehand_shadow` for MVP), hosts fragment stack onboarding → capture → review
- [ ] T016 [P] [US1] `CalibrationOnboardingFragment` in [app/src/main/java/com/ttcoachai/calibration/CalibrationOnboardingFragment.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationOnboardingFragment.kt) — camera angle/distance guidance; use existing pose landmarker to confirm full-body visibility before "Start Capture" CTA enables (FR-9)
- [ ] T017 [US1] `CalibrationCaptureFragment` in [app/src/main/java/com/ttcoachai/calibration/CalibrationCaptureFragment.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationCaptureFragment.kt) — live camera preview + rep counter ("Rep N/15") bound to `CalibrationStateManager` flow; auto-advance to review on target count reached; exit mid-flow discards session per AC3 (depends on T013, T014)
- [ ] T018 [US1] `CalibrationReviewFragment` in [app/src/main/java/com/ttcoachai/calibration/CalibrationReviewFragment.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationReviewFragment.kt) — invoke `BaselineDeriver.derive(...)` on the captured lists, show per-metric mean ± σ + `qualityScore`; "Save" calls `PersonalBaselineRepository.saveBaseline`, "Redo" pops back to onboarding (depends on T011, T013)
- [ ] T019 [US1] Add calibration entry point from main app (menu item or home-screen card) linking into `CalibrationActivity` — [app/src/main/java/com/ttcoachai/](../../app/src/main/java/com/ttcoachai/) top-level activity host (pick whichever existing home/menu surface exists; small, single file edit)

**Checkpoint**: User Story 1 fully functional — user can calibrate forehand shadow end-to-end and the baseline persists across app restarts. MVP delivered.

---

## Phase 4: User Story 2 — Recalibration (Priority: P2)

**Goal**: An existing user with a saved baseline can rerun calibration; the prior baseline is archived (not deleted) and the new one becomes active. Interrupted recalibration leaves the original untouched.

**Independent Test**: Seed a baseline in Room (via US1 flow or test fixture) → enter `CalibrationActivity` → UI shows "Recalibrate" instead of "Calibrate" → complete 15 reps → confirm only the new baseline has `isActive = true` and the prior one is archived with its original `createdAtMs` preserved. Also: start recalibration, kill process mid-flow, reopen — original baseline still `isActive`. Meets FR-5 (versioning) and both AC of US2.

### Implementation for User Story 2

- [ ] T020 [US2] Extend `PersonalBaselineDao` in [app/src/main/java/com/ttcoachai/db/PersonalBaselineDao.kt](../../app/src/main/java/com/ttcoachai/db/PersonalBaselineDao.kt) with `@Transaction archiveAndInsert(drillType, newEntity)` — flips any existing active row for that `drillType` to `isActive = false`, inserts new active row in one transaction
- [ ] T021 [US2] Extend `PersonalBaselineRepository` in [app/src/main/java/com/ttcoachai/repository/PersonalBaselineRepository.kt](../../app/src/main/java/com/ttcoachai/repository/PersonalBaselineRepository.kt) — `saveBaseline` now routes through `archiveAndInsert`; add `getBaselineHistory(drillType): Flow<List<PersonalBaseline>>` for drift tracking
- [ ] T022 [US2] Recalibration UX in [app/src/main/java/com/ttcoachai/calibration/CalibrationActivity.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationActivity.kt) — on entry, check active baseline via repository; if present, show "Recalibrate (current: N reps, Δt days old)" variant of CTA; otherwise show first-time copy
- [ ] T023 [US2] Discard-on-exit guard in [app/src/main/java/com/ttcoachai/managers/CalibrationStateManager.kt](../../app/src/main/java/com/ttcoachai/managers/CalibrationStateManager.kt) — clear any in-memory session on `onDestroy`/process death; `saveBaseline` is the only path that touches Room
- [ ] T024 [US2] Versioning unit test in [app/src/test/java/com/ttcoachai/db/PersonalBaselineDaoTest.kt](../../app/src/test/java/com/ttcoachai/db/PersonalBaselineDaoTest.kt) — insert v1 → insert v2 via `archiveAndInsert` → assert exactly one active row, v1 archived with original timestamp (extends test from T012)

**Checkpoint**: Both US1 and US2 are independently functional — first-timers and recalibrators both complete successfully, with versioning preserved.

---

## Phase 5: User Story 3 — Live quality feedback during calibration (Priority: P3)

**Goal**: During capture, the user sees per-rep quality indicators and outlier warnings. Excluded reps are flagged in real time and not counted toward the 15-rep target.

**Independent Test**: During capture, perform 14 consistent reps plus one deliberately sloppy rep (exaggerated wrist angle). UI flashes "Rep excluded — large deviation" on the outlier; rep counter stays at 14; continuing with one more good rep reaches 15 and auto-advances to review, where `excludedRepIndices` contains the outlier index. Meets FR-3, FR-4 live-surface requirement, and the AC of US3.

### Implementation for User Story 3

- [ ] T025 [US3] Live outlier detection in [app/src/main/java/com/ttcoachai/managers/CalibrationStateManager.kt](../../app/src/main/java/com/ttcoachai/managers/CalibrationStateManager.kt) — after each new `AnalysisResult`, compute running mean/σ on accepted reps; if new rep > 2σ on any key metric, mark it excluded, emit an `OutlierDetected(repIndex, metricKey, deviationSigmas)` event on a SharedFlow. Do not advance rep counter for excluded reps
- [ ] T026 [US3] Outlier toast/banner in [app/src/main/java/com/ttcoachai/calibration/CalibrationCaptureFragment.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationCaptureFragment.kt) — collect `OutlierDetected` events, show transient "Rep excluded — {metric} off by {σ}σ" UI; update rep counter to show `{accepted}/15` with optional sub-line "+{excluded} excluded"
- [ ] T027 [US3] Low-quality gating in [app/src/main/java/com/ttcoachai/calibration/CalibrationReviewFragment.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationReviewFragment.kt) — if `qualityScore < 0.6` (plan-documented threshold to confirm on first real session), surface "Low calibration quality — redo recommended" banner and make "Redo" the primary CTA (FR-4)
- [ ] T028 [US3] Excluded-reps summary row in [app/src/main/java/com/ttcoachai/calibration/CalibrationReviewFragment.kt](../../app/src/main/java/com/ttcoachai/calibration/CalibrationReviewFragment.kt) — render `excludedRepIndices` count + reason (dominant deviating metric) so the user understands why the final sample size may be <attempted count

**Checkpoint**: All three user stories deliverable. Live quality is additive over US1's silent-capture flow.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Plan §Phase 3 deliverables — dev-only debug tooling + end-to-end instrumented coverage + doc updates. Not blocking for first real-world calibration test but required before moving to Stage 1 Phase 2 (rule engine).

- [ ] T029 [P] Dev-only baseline debug screen in [app/src/main/java/com/ttcoachai/debug/BaselineDebugActivity.kt](../../app/src/main/java/com/ttcoachai/debug/BaselineDebugActivity.kt) — load active baseline, render metric distributions as histograms, slider preview "what rules would this trigger for synthetic input" (gate with `BuildConfig.DEBUG`)
- [ ] T030 [P] ADB baseline dump in [app/src/main/java/com/ttcoachai/debug/BaselineDumpReceiver.kt](../../app/src/main/java/com/ttcoachai/debug/BaselineDumpReceiver.kt) — broadcast receiver that serializes current baseline to JSON on `adb shell am broadcast` invocation; debug builds only
- [ ] T031 End-to-end instrumented test in [app/src/androidTest/java/com/ttcoachai/calibration/CalibrationFlowTest.kt](../../app/src/androidTest/java/com/ttcoachai/calibration/CalibrationFlowTest.kt) — inject mocked pose stream from an existing fixture → run through calibration activity → assert `PersonalBaseline` persisted and queryable via repository (plan §Phase 3 test)
- [ ] T032 [P] Update [CLAUDE.md](../../CLAUDE.md) — add `CalibrationStateManager` and new `calibration/` package to project structure section; add any new gotchas encountered
- [ ] T033 [P] Resolve or explicitly defer the open questions in [spec.md](spec.md#open-questions) (rep-count final value, low-quality threshold, handedness policy, onboarding location, recalibration cadence) — update spec in place with decisions or explicit "defer to Stage 1 Phase 5" notes
- [ ] T034 Tick Phase 1/2/3 deliverables in [plan.md](plan.md) §Phases as they land (mirrors how Phase 0 was tracked)

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
