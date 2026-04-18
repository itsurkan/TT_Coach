# Feature Specification: Personal Baseline Calibration (Stage 1 · Phase 1)

**Feature Branch**: `002-ball-tracking` (Stage 1 content on existing branch)
**Created**: 2026-04-18
**Status**: Draft → In Progress
**Parent:** [docs/STAGED_ROADMAP.md](../../docs/STAGED_ROADMAP.md)

## Context

TT_Coach's core product principle is to coach **on top of a player's existing technique**, not re-teach a universal "correct" form. Every player's stroke was shaped by their coach; the app must respect that. Hardcoded universal thresholds ("elbow at 120°") would send wrong advice to half of players and break credibility on first session.

This phase introduces **personal baseline calibration**: the player records a short set of reference strokes performed in their own technique; the app derives personal thresholds; subsequent drills evaluate consistency against that baseline, not a universal ideal.

Calibration is **Phase 1 of Stage 1** because every subsequent phase (rule engine, live feedback, preset drills) depends on having a personal baseline to compare against.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — First-time calibration (Priority: P1)

A new user installs the app and runs calibration to establish their personal baseline for one drill type (e.g., forehand shadow swing).

**Why this priority:** Without calibration data, no downstream drill feedback has a reference point. This is the foundational user journey.

**Independent Test:** User opens the app, selects "Calibrate: Forehand Shadow," follows the on-screen prompt to perform 15 reference reps, and sees a confirmation screen with their baseline metrics (e.g., "Peak elbow angle: 108° ±6°, Body rotation: 38° ±4°"). No drill execution, no feedback evaluation — just baseline capture.

**Acceptance Scenarios:**
1. **Given** the user has no prior baseline, **When** they complete 15 valid reference reps, **Then** a `PersonalBaseline` is persisted locally with per-metric mean and standard deviation.
2. **Given** the user performs reps with high inter-rep variance (σ above threshold), **When** calibration finishes, **Then** the app flags "low calibration quality" and offers to redo with guidance.
3. **Given** the player partially completes calibration (<10 valid reps), **When** they exit, **Then** no baseline is saved and partial progress is discarded.

---

### User Story 2 — Recalibration (Priority: P2)

An existing user wants to update their baseline after a coaching change or technique adjustment.

**Why this priority:** Baselines go stale when technique evolves. Users need an explicit way to refresh.

**Independent Test:** User with an existing baseline selects "Recalibrate," runs through the same 15-rep flow, and the old baseline is replaced (with the prior version archived, not deleted, so we can track drift over time).

**Acceptance Scenarios:**
1. **Given** a baseline exists, **When** user completes recalibration, **Then** the previous baseline is archived with timestamp and the new one becomes active.
2. **Given** recalibration is interrupted, **When** user exits, **Then** the original baseline remains active.

---

### User Story 3 — Baseline quality feedback during calibration (Priority: P3)

While performing reference reps, the user sees a live indicator of calibration quality (e.g., "Rep 7/15 — consistency: good").

**Why this priority:** Without live feedback, users may inadvertently calibrate with sloppy reps. This improves baseline quality but isn't required for the MVP flow to work.

**Independent Test:** During calibration, user performs one deliberately inconsistent rep; the UI flags it as an outlier and it's excluded from the baseline calculation. Remaining 14 reps produce the baseline.

---

## Functional Requirements

### FR-1 — Calibration session lifecycle
The app MUST provide a dedicated calibration mode separate from drill mode. Calibration captures raw pose frames for N valid reps (default N=15), extracts per-rep metrics, and derives a `PersonalBaseline`.

### FR-2 — PersonalBaseline data model
A `PersonalBaseline` MUST capture at minimum, per drill type:
- Per-metric mean and standard deviation (e.g., peak elbow angle, peak body rotation, contact height, follow-through angle, phase durations)
- Rep-count used for derivation
- Timestamp of derivation
- Drill type identifier (`forehand_shadow`, `backhand_shadow`, `footwork_shuffle`, etc.)
- Outlier-exclusion metadata (which reps were excluded and why)

### FR-3 — Outlier exclusion
Reps with metrics more than 2σ from the running mean (computed incrementally) MUST be excluded from the final baseline and flagged to the user during the session.

### FR-4 — Calibration quality score
After calibration, the app MUST compute and display a quality score based on the σ of key metrics (lower σ = higher quality). Low-quality baselines MUST prompt the user to redo before proceeding to drill mode.

### FR-5 — Local persistence
`PersonalBaseline` MUST persist locally in Room. No cloud sync in Stage 1. One baseline per `(user, drill_type)` combination at a time; previous versions archived for drift analysis.

### FR-6 — Consumability by downstream phases
The `PersonalBaseline` MUST be queryable by drill_type and readable from the rule evaluator in `shared/`. The API MUST be platform-agnostic (living in `shared/commonMain`) so iOS/web could later reuse it.

### FR-7 — Baseline-aware rules (consumer contract)
Downstream rule evaluators MUST be able to express rules like:
- "Metric X stays within baseline.mean ± k·baseline.std" (consistency check)
- "Metric X does not drop more than Δ below baseline.mean" (regression check)
- "Phase Y duration within baseline.duration ± k%" (rhythm check)

### FR-8 — Reusing existing detection
Calibration MUST reuse the existing rep detection state machine in [shared/.../detection/StrokePhaseDetector.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/StrokePhaseDetector.kt) and metric extraction in [shared/.../analysis/StrokeAnalyzer.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/StrokeAnalyzer.kt). No new rep detection logic in this phase.

### FR-9 — Onboarding for camera setup
The calibration flow MUST include a one-time camera-setup onboarding step (correct angle, distance, player-in-frame indicator) before reps begin. Bad camera setup invalidates baselines.

---

## Non-functional Requirements

### NFR-1 — Calibration duration
Full calibration (15 reps + setup + review) SHOULD take under 3 minutes for a cooperative user.

### NFR-2 — No-network operation
Calibration MUST work entirely offline. No network calls required.

### NFR-3 — Determinism
Given the same input pose frames, the derived `PersonalBaseline` MUST be identical. Required for unit testability against the existing JSON fixtures in [shared/src/commonTest/resources/fixtures/](../../shared/src/commonTest/resources/fixtures/).

---

## Explicitly Out of Scope (Phase 1)

- Ball detection or ball-related metrics — Stage 2
- End-user drill authoring UI — Stage 1.5 or later
- Universal rule thresholds — replaced by baseline-relative rules
- Cloud sync of baselines — Stage 2+
- Per-coach baselines (coach calibrates for student) — later
- Multi-player identification (partner in frame) — Stage 2
- Live on-screen feedback during drills — Phase 3 of Stage 1
- Preset drill library — Phase 4 of Stage 1

---

## Key Decisions Captured

1. **Calibration over universal thresholds** — core product differentiator; see [docs/STAGED_ROADMAP.md](../../docs/STAGED_ROADMAP.md#product-positioning).
2. **Calibration replaces user-authored DSL** — most amateurs can't articulate their own joint angles; let the app derive them. End-user rule authoring deferred indefinitely; coach-authored drills is a later persona.
3. **Local-only persistence** — no accounts, no Firebase in Stage 1. Reduces scope significantly.
4. **15 reps, 2σ exclusion, quality score** — concrete starting points for FR-1/FR-3/FR-4; subject to tuning after first real sessions.

---

## Open Questions (to resolve before or during implementation)

- [ ] Exact rep count for calibration (15 is a guess — may need 20 for noisier drills like swing, 10 for stance)
- [ ] Minimum calibration quality threshold for "proceed allowed" vs "redo required"
- [ ] How do we handle left-handed vs right-handed players — separate baselines or mirrored?
- [ ] Does camera setup onboarding live in this phase or Phase 5?
- [ ] Recalibration cadence — prompt user every N sessions, or never until they ask?
