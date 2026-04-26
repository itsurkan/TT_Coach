# TT_Coach — Staged Roadmap

**Last updated:** 2026-04-18
**Current branch:** `002-ball-tracking` (repurposed — Stage 1 content first, ball work parked)

## Product positioning

**Practice coach that works with any counterpart — robot OR partner.** The app is agnostic to who feeds the ball; it observes the player.

**Core principle: don't re-teach, build on existing technique.**
Table tennis has no universal "correct" form. Chinese vs European schools, shakehand vs penhold, classical vs modern topspin — angles, positions, and rhythms legitimately differ. A rigid universal rule like "elbow should be 120°" ships wrong advice to half the users and kills credibility on first session.

Instead: the app **calibrates to the individual player's technique** (their coach taught them that stroke — respect it) and then helps them stay consistent with their own baseline, improve stability, and catch regressions.

---

## Stages

### Stage 1 — Pose-only prototype · 🟡 IN PROGRESS

**Target:** Play Store release, ~2 weeks focused work.

**Scope:**
- Drills: shadow footwork, stance/ready-position checks, swing form without ball
- **Calibration-based personal baseline** (not universal rules, not user-authored DSL)
- Live on-screen feedback (red/green per rule) + post-session summary
- Session model: one drill = one session
- 5 preset drills shipped as hardcoded `Drill(...)` configs using the existing `shared/` engine
- Storage: local only (Room) — no Firebase/cloud for Stage 1
- Camera: fixed-setup assumption (tripod), onboarding teaches correct angle/distance
- Monetization: free
- Dev-only debug UI for tuning rules; NO end-user authoring UI

**Phases:**

| Phase | Title | Status | Spec |
|-------|-------|--------|------|
| **Phase 1** | Calibration — personal baseline capture | 🟡 IN PROGRESS | [specs/003-stage1-calibration/](../specs/003-stage1-calibration/) |
| Phase 2 | Rule evaluator — `RuleEvaluator` (shared), `RuleEvaluation` model, baseline wired into training pipeline, audio ack/nack per rep | ⚪ Planned | TBD |
| Phase 3 | Audio feedback per rep — rule-based ack/nack tone, no UI | ⚪ Planned | TBD |
| Phase 4 | Preset drill library (5 drills using engine) | ⚪ Planned | TBD |
| Phase 5 | Camera onboarding + post-session summary UI (per-rule ✓/✗ across session, joint tinting on saved poses) | ⚪ Planned | TBD |
| Phase 6 | Play Store internal testing track — signing, AAB upload, store listing, privacy policy | 🟡 IN PROGRESS | — |

**Why calibration is Phase 1:** every subsequent phase depends on having a personal baseline to compare against. Rule engine needs something to aggregate against; feedback UI needs thresholds; drills need per-user parameters.

---

### Stage 2 — Ball tracking + paid AI analysis · ⚪ PLANNED

**Adds:**
- Continuation of `002-ball-tracking` work — OpenCV + YOLO ball detection (much is already done)
- Contact timing, contact point relative to body, bounce-based rep segmentation
- Second person in frame (partner) — identify the trainee among multiple poses
- Paid AI post-session analysis tier (LLM on serialized pose+ball metrics)
- Play Billing integration

---

### Stage 3 — Table detection · ⚪ PLANNED

**Adds:**
- Table geometry detection
- Outcome rules: "cross-court / down-the-line", "landed in / out", "depth"
- Drill outcomes beyond form feedback

---

## What's reusable from existing code

~70% of the engine bones already exist in `shared/`:
- Rep detection state machine: [shared/.../detection/JsonStrokeDetector.kt](../shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/JsonStrokeDetector.kt)
- Metric extraction: [shared/.../analysis/StrokeAnalyzer.kt](../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/StrokeAnalyzer.kt) + `AngleCalculations` + `MetricCalculations`
- Parameterized thresholds: [shared/.../models/ExerciseParameters.kt](../shared/src/commonMain/kotlin/com/ttcoachai/shared/models/ExerciseParameters.kt)
- Pipeline wiring: [app/.../processors/PoseAnalysisProcessor.kt](../app/src/main/java/com/ttcoachai/processors/PoseAnalysisProcessor.kt) + [TrainingStateManager](../app/src/main/java/com/ttcoachai/managers/TrainingStateManager.kt)
- JSON pose fixtures for tests: [shared/src/commonTest/resources/fixtures/](../shared/src/commonTest/resources/fixtures/)

**Gaps to build:**
- Personal baseline data model (this phase)
- Temporal rule aggregation (rule passed *throughout* phase, not just at peak frame)
- `RepResult` persistence layer (per-rep rule pass/fail, not just session aggregate)
- Rule schema with sealed types (`AngleThreshold`, `AngleDuringPhase`, `DistanceThreshold`, composite AND/OR)

---

## Key risks

1. **Pose quality on fast swing** — MediaPipe may miss wrist/arm precision during rapid motion. If calibration variance is too high on swing drills, narrow Stage 1 to footwork + stance.
2. **Calibration with bad habits** — player may calibrate while reinforcing flaws. Acceptable for v1 since goal is consistency/improvement, not overhaul. Mitigate with a "calibration quality" score (low variance reps only).
3. **Timeline** — 2 weeks is aggressive for Play Store release. Realistic fallback: closed testing track at 2 weeks, Play Store at 3-4 weeks.
