# Implementation Plan: Personal Baseline Calibration

**Parent Spec:** [spec.md](spec.md)
**Branch**: `002-ball-tracking` (Stage 1 content before resuming ball work)
**Date**: 2026-04-18
**Status:** 🟡 In Progress

## Summary

Implement the calibration flow that captures a player's reference strokes and derives a `PersonalBaseline` used by all downstream Stage 1 rules. Reuses the existing stroke detection + metric extraction in `shared/commonMain` (~70% of the engine already exists). Adds: baseline data model, outlier exclusion, quality scoring, local persistence, and an onboarding + reference-capture UI.

## Technical Context

**Language/Version:** Kotlin 2.1.0 (KMP shared module + Android app module)
**Primary Dependencies:**
- MediaPipe tasks-vision 0.10.14 (reuse existing pose detection)
- Room 2.6.1 (baseline persistence)
- kotlinx-coroutines 1.10.2
- Existing: [StrokePhaseDetector](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/StrokePhaseDetector.kt), [StrokeAnalyzer](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/StrokeAnalyzer.kt)

**Storage:** Room (local-only Stage 1; no Firebase)
**Testing:** JUnit 4.13.2 + kotlin.test against existing JSON pose fixtures in [shared/src/commonTest/resources/fixtures/](../../shared/src/commonTest/resources/fixtures/)
**Target Platform:** Android (minSdk 24, compileSdk 36); baseline math in `shared/commonMain` for future iOS/web reuse
**Performance Goals:** Baseline derivation on 15 reps completes in <500ms post-capture; no live-stream constraints beyond existing pose pipeline
**Constraints:** Must not modify existing `StrokePhaseDetector` / `StrokeAnalyzer` contracts — additive only; no breaking changes to in-flight `002-ball-tracking` work

## Phases

### Phase 0 — Data model + math (shared/, pure logic) ✅ DONE

**Deliverables:**
- [x] `PersonalBaseline` data class in [shared/.../models/PersonalBaseline.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PersonalBaseline.kt)
  - fields: `drillType`, `metricStats: Map<String, MetricStats>`, `phaseDurationsMs: Map<String, MetricStats>`, `repCount`, `excludedRepIndices`, `qualityScore`, `createdAtMs`, `drillerHandedness`
  - Note: `phaseDurationsMs` uses string keys and millisecond stats (mean/std/min/max) rather than `Map<StrokePhase, Duration>`, so phase timing has full distribution info and stays portable without `kotlin.time` on the serialization boundary.
- [x] `MetricStats` data class in [shared/.../models/MetricStats.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/models/MetricStats.kt): `mean`, `std`, `min`, `max`, `sampleCount`
- [x] `BaselineDeriver` in [shared/.../analysis/BaselineDeriver.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineDeriver.kt)
  - input: `List<DetectedStroke>` + `List<AnalysisResult>` (already emitted by existing pipeline) + `frameIntervalMs`
  - output: `PersonalBaseline`
  - logic: per-metric sample mean/std, single-pass 2σ rep-level outlier exclusion, qualityScore = 1 − mean(coefficient of variation) clamped to [0, 1]. Iterative outlier rejection deferred (see §Open questions in `phase-0-kickoff.md`).
- [x] `BaselineRule` sealed type in [shared/.../analysis/BaselineRule.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineRule.kt) — data-only, not yet evaluated:
  - `ConsistencyRule(id, metricKey, kSigma)` — "stay within mean ± k·σ"
  - `RegressionRule(id, metricKey, maxDropFromMean)` — "don't drop below mean − Δ"
  - `RhythmRule(id, metricKey, maxDurationDeviationPct)` — "phase duration within ±k%"

**Tests:** [shared/.../jvmTest/.../BaselineDeriverTest.kt](../../shared/src/jvmTest/kotlin/com/ttcoachai/shared/analysis/BaselineDeriverTest.kt)
- [x] Happy path — real `forehand_drive.json` fixture through `JsonStrokeDetector` + `StrokeAnalyzer` → `BaselineDeriver` (5 strokes detected; uses `minRepCount=1` since fixture is below the production default of 10).
- [x] Insufficient reps — 3 synthetic strokes → `IllegalArgumentException`.
- [x] Outlier exclusion — 14 identical reps with one wrist-angle outlier → outlier index appears in `excludedRepIndices` and mean stays near non-outlier cluster.
- [x] Zero variance — identical reps → `std = 0` on every metric and `qualityScore = 1.0`.
- [x] Determinism — twice-derived baselines with same input compare equal.

### Phase 1 — Persistence (app/, Room)

**Deliverables:**
- `PersonalBaselineEntity` Room entity in [app/.../models/](../../app/src/main/java/com/ttcoachai/models/)
- `PersonalBaselineDao` in [app/.../db/](../../app/src/main/java/com/ttcoachai/db/)
- `PersonalBaselineRepository` following existing [TrainingRepository](../../app/src/main/java/com/ttcoachai/repository/TrainingRepository.kt) pattern (local-only — no Firestore branch)
- Room migration from current schema (still `fallbackToDestructiveMigration()` acceptable pre-Play-Store; revisit before Phase 6)
- Serialize `MetricStats` map as JSON column (simplest; `@TypeConverter` for `Map<String, MetricStats>` → String)

**Tests:**
- DAO round-trip: insert → query by drill_type → equal
- Multiple versions for same (user, drill_type): active vs archived

### Phase 2 — Capture UI (app/, new activity/fragment)

**Deliverables:**
- `CalibrationActivity` — entry point, drill-type selection
- Calibration flow fragments:
  - `CalibrationOnboardingFragment` — camera setup guidance (angle, distance, full-body visibility check using existing pose landmarker)
  - `CalibrationCaptureFragment` — live camera + rep counter ("Rep 7/15"), real-time outlier warnings
  - `CalibrationReviewFragment` — quality score, baseline summary, "save" / "redo" CTAs
- `CalibrationStateManager` (new, follow existing `*Manager` pattern) — session state singleton
- Reuse [PoseAnalysisProcessor](../../app/src/main/java/com/ttcoachai/processors/PoseAnalysisProcessor.kt) for frame pipeline; add a "calibration" mode that routes to `BaselineDeriver` instead of `FeedbackGenerator`

**UI spec — deferred** to UX iteration; use plain Compose screens initially. Polish in Phase 5 of Stage 1.

### Phase 3 — Integration + dev debug screen

**Deliverables:**
- Debug screen (dev build only): load a baseline, show metric distributions as histograms, slider-preview "what rules would this baseline trigger for synthetic input"
- CLI/ADB command to dump current baseline as JSON for manual inspection
- Hook into [AppDatabase](../../app/src/main/java/com/ttcoachai/db/) to include new DAO

**Tests:**
- End-to-end instrumented test: mocked pose stream → calibration flow → persisted baseline → query via repository

## Project Structure

```text
shared/src/
├── commonMain/kotlin/com/ttcoachai/shared/
│   ├── models/
│   │   ├── PersonalBaseline.kt        # NEW
│   │   └── MetricStats.kt             # NEW
│   ├── analysis/
│   │   ├── BaselineDeriver.kt         # NEW
│   │   └── BaselineRule.kt            # NEW (sealed type, data only)
│   │   └── (existing StrokeAnalyzer.kt, AngleCalculations.kt, MetricCalculations.kt)
│   └── detection/
│       └── (existing JsonStrokeDetector.kt, StrokePhaseDetector.kt — no changes)
└── commonTest/kotlin/com/ttcoachai/shared/
    └── analysis/
        └── BaselineDeriverTest.kt     # NEW

app/src/main/java/com/ttcoachai/
├── models/
│   └── PersonalBaselineEntity.kt      # NEW (Room entity)
├── db/
│   ├── PersonalBaselineDao.kt         # NEW
│   └── AppDatabase.kt                 # MODIFY (register new DAO)
├── repository/
│   └── PersonalBaselineRepository.kt  # NEW
├── managers/
│   └── CalibrationStateManager.kt     # NEW
├── calibration/                        # NEW package
│   ├── CalibrationActivity.kt
│   ├── CalibrationOnboardingFragment.kt
│   ├── CalibrationCaptureFragment.kt
│   └── CalibrationReviewFragment.kt
└── processors/
    └── PoseAnalysisProcessor.kt       # MODIFY (add calibration-mode routing)
```

## Reuse vs Build

| Area | Approach | Source |
|------|----------|--------|
| Rep detection state machine | **Reuse as-is** | [StrokePhaseDetector](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/StrokePhaseDetector.kt) |
| Per-frame metric extraction | **Reuse as-is** | [StrokeAnalyzer](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/StrokeAnalyzer.kt) + `AngleCalculations`, `MetricCalculations` |
| Pipeline wiring | **Reuse + extend** | [PoseAnalysisProcessor](../../app/src/main/java/com/ttcoachai/processors/PoseAnalysisProcessor.kt) + [TrainingStateManager](../../app/src/main/java/com/ttcoachai/managers/TrainingStateManager.kt) pattern |
| JSON pose fixtures | **Reuse** | [shared/src/commonTest/resources/fixtures/](../../shared/src/commonTest/resources/fixtures/) |
| Repository pattern | **Reuse pattern, simplify** (local-only, no Firestore branch) | [TrainingRepository](../../app/src/main/java/com/ttcoachai/repository/TrainingRepository.kt) as template |
| Baseline data model | **Build new** | — |
| Baseline derivation logic | **Build new** | — |
| Baseline-aware rule types | **Build new** (data only in this phase) | — |
| Calibration UI | **Build new** | — |

## Timeline (aggressive target)

- **Day 1-2:** Phase 0 — `PersonalBaseline`, `BaselineDeriver`, unit tests on existing fixtures
- **Day 3:** Phase 1 — Room entity, DAO, repository, migration
- **Day 4-5:** Phase 2 — Calibration UI (onboarding + capture + review)
- **Day 6:** Phase 3 — dev debug screen + instrumented end-to-end test
- **Day 7:** Buffer / QA on real training

Total: ~1 week for Phase 1 of Stage 1. Remaining Stage 1 phases (rule engine, live feedback UI, preset drills, onboarding polish, Play Store) continue after.

## Risks

1. **σ-based outlier exclusion may be too aggressive for swing drills** — high natural variance. Mitigation: per-drill-type tuning of `kSigma`; default 2.0, may go to 2.5 for swing.
2. **Calibration with bad habits** — player may lock in flaws. Acceptable for v1; quality score surfaces inconsistency but not "incorrectness" (since there is no universal correct). Long-term: coach-sign-off flow in later stage.
3. **Baseline stability across sessions** — same player may produce different baselines on different days (fatigue, warmup). Mitigation: average across N calibrations, or explicit "warmup" instruction before calibration starts. Deferred decision.

## Out of Scope (explicitly deferred)

- Rule evaluator that *uses* `BaselineRule` — Phase 2 of Stage 1
- UI polish beyond functional — Phase 5 of Stage 1
- Cloud sync — Stage 2+
- End-user drill authoring UI — deferred indefinitely; calibration replaces most of its need
- Coach-authored baselines / coach mode — later persona
