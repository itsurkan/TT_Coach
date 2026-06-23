# Full-Cycle Stroke Segmentation (backswing + forward drive = one stroke) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Kotlin `shared/` is the SOURCE OF TRUTH; the TS `poses_viewer/src/drill2d/` mirror and goldens in BOTH suites are updated in the SAME change per task. Never execute inline in the main session.

**Goal:** Redefine a counted "stroke" as a FULL CYCLE = backswing + forward drive, paired into one rep that spans `[backswing.start → drive.end]`. This (a) lets the viewer show one bi-colored band per stroke (backswing half vs drive half), and (b) fixes the video_4 "8 vs 10" undercount: two real forward drives that are slightly short in the forward-half (#6 @7.53 s dur 0.46 s, #14 @13.80 s dur 0.45 s) are currently dropped by `RepFilter`'s duration LOWER bound; banding on near-uniform *cycle* durations (~1.0 s) instead of forward-half durations keeps them.

**Measured impact (Part A prototype, vite-node over the TS mirror, gap-bounded pairing + cycle-duration banding + cycle-window locomotion):**

| fixture | current reps | cycle-model reps | delta | notes |
|---|---|---|---|---|
| andrii_1_rtm | 15 | **15** | 0 | E2E exit gate UNCHANGED — golden safe |
| video_2_rtm | 3 | 3 | 0 | invariants only (no hard golden) |
| video_4_rtm | 8 | **10** | +2 | TARGET HIT — #6 and #14 rescued; walking #16 still dropped |
| IMG_6332 (Videos/) | 0 | 0 | 0 | all walking; not a fixture |

The **single load-bearing tuning constant** that makes this work is the pairing gap cap `MAX_PAIR_GAP_MS ≈ 800`: with an UNBOUNDED gap andrii_1 regresses 15 → 11 (far-away dropped peaks get mis-attached as backswings, inflating some cycle durations into a bimodal distribution that `RepFilter` then trims). Bounded at ≤800 ms, andrii pairs only the 5 genuinely-adjacent backswings; all 15 cycle durations land inside `[medDur/2, medDur×2]` and nothing drops.

**Architecture:** Insert a NEW `CyclePairing` step into the mandatory pipeline AFTER `ForwardStrokeFilter` and BEFORE `RepFilter`:

```
detectStrokes2d
  → ForwardStrokeFilter        (keeps forward drives, drops backswings — UNCHANGED)
  → CyclePairing               (NEW: pair each drive with its nearest preceding dropped raw peak)
  → RepFilter                  (CHANGED: bands on CYCLE duration, not forward-half duration)
  → LocomotionFilter           (CHANGED: hip-travel measured over the CYCLE window)
```

The cycle is represented by a `StrokeCycle2D` value carrying both the backswing (nullable) and the drive, plus `[startFrame, endFrame]` for the full span. **The drive's `peakFrame` is preserved as the cycle's metric anchor** — `DrillMetrics.extractAtPeak` is called at the drive peak exactly as today, so all metric/feedback logic is UNCHANGED. Cycles whose drive has no preceding backswing peak (first stroke, or detector missed it) still count — pairing is best-effort, never a reject gate (never reject on absence of evidence).

**Tech Stack:** Kotlin 2.1.0 KMP (`shared/commonMain`, zero external deps), `kotlin-test` in `commonTest`, ClassLoader fixture loading in `jvmTest`; TS mirror in `poses_viewer/src/drill2d/` with vitest. Source-of-truth = Kotlin.

**Spec:** [docs/superpowers/specs/2026-06-10-2d-pivot-design.md](../specs/2026-06-10-2d-pivot-design.md); M0/M1 viewer specs referenced in poses_viewer/CLAUDE.md.

---

## Design notes (read before Task 1)

1. **Pairing rule (exact).** Process drives in order. For drive `D` with previous drive peak `prevPeak` (`-1` for the first), the backswing candidate is the dropped raw peak `R` (a `detectStrokes2d` peak NOT in the `ForwardStrokeFilter` output) with the LARGEST `R.peakFrame` satisfying `prevPeak < R.peakFrame < D.peakFrame`. Accept it only if `frames[D.peakFrame].timestampMs − frames[R.peakFrame].timestampMs ≤ MAX_PAIR_GAP_MS`. If none qualifies, the cycle has `backswing = null` and `startFrame = D.startFrame`. When accepted, `startFrame = R.startFrame`. `endFrame = D.endFrame`, `peakFrame = D.peakFrame`, `peakSpeed = D.peakSpeed` in all cases. A dropped raw peak is consumed by at most one drive (continuous play where one apparent backswing sits between two drives attaches to the LATER drive via the `prevPeak` lower bound; the earlier drive then has `backswing = null` — verified harmless on fixtures).

2. **`MAX_PAIR_GAP_MS = 800` is PROVISIONAL** and the highest-risk constant in this change (see Risks). It is tuned on non-protocol footage (andrii_1, video_4). Expose it as a named constant in both `CyclePairing.kt` and `cyclePairing.ts`, and as a viewer knob default so it can be re-tuned without a code change. Probe showed 600 and 800 both give the target counts (andrii 15 / video_4 10); 800 is chosen for margin on andrii's slowest real cycle (the bs=16.32→drive=17.03 pair is a ~710 ms gap).

3. **RepFilter change is the count-mover.** `RepFilter` keeps its `[median/BAND, median×BAND]` banding on BOTH peak speed and duration (`SPEED_BAND = DURATION_BAND = 2.0`, `MIN_STROKES_TO_FILTER = 4`), but **duration is now the cycle span `endFrame − startFrame`** (which equals the drive-half span for unpaired cycles, and the full `backswing.start → drive.end` span for paired ones). Speed banding is unchanged (drive `peakSpeed`). On video_4 the cycle-duration median is ~0.97 s and the two rescued drives' cycles are ~0.99 s — comfortably in-band. **Peak speed must still operate on the drive peak**, not a backswing peak.

4. **LocomotionFilter window change.** `hipMidTravelTorso` is computed over the CYCLE window `[cycle.startFrame, cycle.endFrame]` instead of the forward-half. Probe confirms the genuine video_4 walking step (#16 @15.18, hip-travel 1.14 torso over its cycle) is still dropped, and no real rep is newly dropped (andrii cycles all ≤0.30 over their full spans at gap=800). The "keep when unmeasurable" rule (never reject on absence of evidence) is preserved. NOTE: measuring loco over the wider cycle window is what made the UNBOUNDED-gap andrii_1 regress to 14 in the probe — another reason the gap cap matters; at gap=800 it is safe.

5. **Metrics anchor is UNCHANGED.** `DrillMetrics.extractAtPeak(frames, cycle.drivePeakFrame, …)` — the ±70 ms median window around the DRIVE peak. `DrillMetrics`, `SanityBounds`, `MetricPrecision`, `DrillFeedbackEngine`, `FeedbackMessageCatalog`, `FeedbackCadencePolicy`, `BaselineDeriver` are all untouched. The cycle is a wrapper; metric extraction sees the same peak frame it sees today.

6. **Pipeline order is still mandatory** (CLAUDE.md gotcha). The new step slots between forward filtering and rep banding. Detection and `ForwardStrokeFilter` are byte-for-byte unchanged — `ForwardStrokeFilter` still needs the full raw list to vote session facing, and `CyclePairing` needs BOTH the raw list (for backswing candidates) and the forward list (the drives). So `CyclePairing.pair(rawStrokes, forwardStrokes, frames, intervalMs)` takes both.

7. **`StrokeCycle2D` vs `Stroke2D`.** Introduce `StrokeCycle2D(backswing: Stroke2D?, drive: Stroke2D, startFrame, endFrame)`. `RepFilter` and `LocomotionFilter` gain cycle-aware entry points; keep the old `Stroke2D`-list signatures (delegating or overloaded) so nothing else in the tree breaks during the transition. The drive peak is reachable as `cycle.drive.peakFrame`; convenience accessor `cycle.peakFrame`/`cycle.peakSpeed` forward to the drive.

8. **Viewer band = one cycle, bi-colored.** Each kept cycle renders ONE timeline band split into two sub-segments at the backswing→drive boundary (the drive's `startFrame`, i.e. the speed valley between backswing peak and drive peak), colored distinctly (e.g. backswing half = cooler/indigo, drive half = emerald). Reuse `strokeCycleWindow.cycleWindow` extrema logic for the display extents where possible: the band START extends to the lowest physical wrist point (max y) before the backswing peak, the SPLIT is at the backswing→drive valley, the END is the follow-through finish (min y) after the drive peak. For `backswing = null` cycles the band has only the drive half (no split). This is DISPLAY-ONLY and COUNT-SAFE — it runs on already-kept cycles, never feeds detection/metrics.

9. **Goldens that MUST change (call out explicitly, with Part A numbers):**
   - Kotlin `ForwardStrokeFilterRealFootageTest.video4ShadowPlayDrivesAreKept`: `gatedReps(video_4)` **8 → 10**. The `counts()` triple `(18, 12, 9)` is computed by the OLD inline `detect→forward→rep` path; after the refactor this test should assert against the cycle pipeline. Replace with cycle-pipeline counts: raw 18, forward(drives) 12, cycle-reps-before-loco 11, gated cycle reps **10**.
   - Kotlin `ForwardStrokeFilterRealFootageTest.andrii1GoldenUnchangedByL28Fix`: `gatedReps(andrii_1)` stays **15** (assert UNCHANGED — this is the proof the change is safe).
   - Kotlin `ForehandDriveEndToEndTest` (E2E exit gate, andrii_1): rep count stays 15. If it asserts the intermediate `forwardStrokes`/`reps` lists, update to the cycle pipeline while keeping the final count 15.
   - TS `drill2d/__tests__/golden.test.ts`: `video_4` block **8 → 10** reps (and the intermediate comment 18/12/9 → 18/12/11→10); `andrii_1` block stays 15; `video_2` invariants hold.
   - `RepFilterTest` (commonTest) + `repFilter.test.ts`: add cases for cycle-duration banding (the rescued-short-drive case) while keeping existing forward-half cases green if the old signature is retained.
   - Any `countStrokes` / `analyzeDrill` snapshot or count test in TS that asserts video_4 = 8.

10. **003 baseline path & E2E exit gate.** `BaselineDeriver` / `DrillCalibrator` consume per-rep metrics extracted at the drive peak. Because the metric anchor is unchanged AND andrii_1's rep set is unchanged (still the same 15 drive peaks), the baseline-derivation path produces identical metrics and the E2E exit gate stays green. This is asserted, not assumed (Task 8 re-runs the E2E test).

## File structure

```
shared/src/commonMain/kotlin/com/ttcoachai/shared/
  models/StrokeCycle2D.kt              # NEW: backswing? + drive + [start,end] span; peak accessors → drive
  drill/CyclePairing.kt                # NEW: pair(raw, forward, frames, intervalMs, maxPairGapMs) → List<StrokeCycle2D>
  drill/RepFilter.kt                   # CHANGED: add filterCycles(...) banding on cycle duration
  drill/LocomotionFilter.kt            # CHANGED: cycle-window hip travel + filterStationaryCycles(...)
  drill/ForehandDriveDrillAnalyzer.kt  # CHANGED: wire CyclePairing into the pipeline; metrics at drive peak
shared/src/commonTest/kotlin/com/ttcoachai/shared/
  drill/CyclePairingTest.kt            # NEW
  drill/RepFilterTest.kt               # CHANGED: cycle-duration cases
shared/src/jvmTest/kotlin/com/ttcoachai/shared/
  drill/ForwardStrokeFilterRealFootageTest.kt  # CHANGED: video_4 → 10, andrii_1 stays 15
  drill/ForehandDriveEndToEndTest.kt           # VERIFY: andrii_1 stays 15

poses_viewer/src/drill2d/
  cyclePairing.ts                      # NEW: 1:1 mirror of CyclePairing.kt
  repFilter.ts                         # CHANGED: filterCycleReps(...)
  locomotionFilter.ts                  # CHANGED: cycle-window travel
  countStrokes.ts                      # CHANGED: insert pairing; expose cycles + reps
  analyzeDrill.ts                      # CHANGED: same pipeline; metrics at drive peak
  strokeCycleWindow.ts                 # REUSE for bi-color band extents
poses_viewer/src/drill2d/__tests__/
  cyclePairing.test.ts                 # NEW
  repFilter.test.ts                    # CHANGED
  golden.test.ts                       # CHANGED: video_4 → 10
poses_viewer/src/components/
  StrokesPage.tsx / StrokeTimeline.tsx # CHANGED: one bi-colored band per cycle
```

---

## Tasks

### Task 1 — `StrokeCycle2D` model (Kotlin, then TS)
- [ ] **Failing test first:** `commonTest` `StrokeCycle2DTest` — constructs a paired cycle and an unpaired (`backswing = null`) cycle; asserts `peakFrame == drive.peakFrame`, `peakSpeed == drive.peakSpeed`, `startFrame == backswing.startFrame` when paired / `drive.startFrame` when null, `endFrame == drive.endFrame`.
- [ ] **Implement** `shared/.../models/StrokeCycle2D.kt` (data class + accessors).
- [ ] **TS mirror:** add `StrokeCycle2D` interface + helper to `drill2d/types.ts`; mirror the test in `drill2d/__tests__/core.test.ts` (or a new file).
- [ ] **Verify:** `./gradlew :shared:jvmTest --tests "*StrokeCycle2DTest"` and `cd poses_viewer && npx vitest run src/drill2d/__tests__`.

### Task 2 — `CyclePairing` step (Kotlin, then TS)
- [ ] **Failing test first:** `commonTest` `CyclePairingTest` with synthetic raw+forward lists covering: (a) drive WITH an adjacent dropped backswing within gap → paired, span = bs.start→drive.end; (b) drive with NO preceding dropped peak → `backswing = null`, span = drive-half; (c) dropped peak just OUTSIDE `MAX_PAIR_GAP_MS` → not paired; (d) continuous play: one dropped peak between two drives attaches to the LATER drive only; (e) first drive never pairs to a peak before frame 0.
- [ ] **Implement** `shared/.../drill/CyclePairing.kt`: `pair(rawStrokes, forwardStrokes, frames, intervalMs, maxPairGapMs = MAX_PAIR_GAP_MS): List<StrokeCycle2D>` per Design note 1; `const val MAX_PAIR_GAP_MS = 800L`.
- [ ] **TS mirror:** `drill2d/cyclePairing.ts` + `cyclePairing.test.ts` (1:1).
- [ ] **Verify:** `./gradlew :shared:jvmTest --tests "*CyclePairingTest"`; `npx vitest run src/drill2d/__tests__/cyclePairing.test.ts`.

### Task 3 — `RepFilter` banding on cycle duration (Kotlin, then TS)
- [ ] **Failing test first:** extend `RepFilterTest` (commonTest) + `repFilter.test.ts`: a set of cycles where one paired cycle's DRIVE-half is short (below forward-half lower band) but its CYCLE duration is near the median → KEPT under `filterCycles`, DROPPED under the old forward-half banding. Assert the new behavior.
- [ ] **Implement** `RepFilter.filterCycles(cycles): List<StrokeCycle2D>` — same band constants, `dur = cycle.endFrame − cycle.startFrame`, speed = `cycle.peakSpeed` (drive). Keep `filter(strokes)` for back-compat.
- [ ] **TS mirror:** `repFilter.ts` `filterCycleReps(cycles)`.
- [ ] **Verify:** `./gradlew :shared:jvmTest --tests "*RepFilterTest"`; `npx vitest run src/drill2d/__tests__/repFilter.test.ts`.

### Task 4 — `LocomotionFilter` over the cycle window (Kotlin, then TS)
- [ ] **Failing test first:** `LocomotionFilter` test with a cycle whose hip-mid travels >0.4 torso across `[start,end]` → dropped; one whose travel is unmeasurable → kept.
- [ ] **Implement** `filterStationaryCycles(cycles, frames, xScale, max, minScore)` reusing `hipMidTravelTorso` over `[cycle.startFrame, cycle.endFrame]` (build a `Stroke2D`-shaped window or overload `hipMidTravelTorso` to take start/end ints).
- [ ] **TS mirror:** `locomotionFilter.ts`.
- [ ] **Verify:** `./gradlew :shared:jvmTest --tests "*Locomotion*"`; `npx vitest run src/drill2d/__tests__/locomotion*`.

### Task 5 — Wire the cycle pipeline into the analyzer + countStrokes (Kotlin, then TS)
- [ ] **Failing test first (Kotlin):** update `ForwardStrokeFilterRealFootageTest`:
  - `video4ShadowPlayDrivesAreKept`: assert cycle pipeline → gated cycle reps == **10** (and document raw 18 / drives 12 / cycle-reps-pre-loco 11).
  - `andrii1GoldenUnchangedByL28Fix`: assert gated cycle reps == **15** (UNCHANGED).
- [ ] **Implement (Kotlin):** `ForehandDriveDrillAnalyzer` pipeline becomes detect → forward → `CyclePairing.pair` → `RepFilter.filterCycles` → `LocomotionFilter.filterStationaryCycles`; per-rep metrics via `extractAtPeak(frames, cycle.drive.peakFrame, …)`.
- [ ] **Failing test first (TS):** update `golden.test.ts` video_4 block to **10** reps; andrii_1 stays 15; video_2 invariants.
- [ ] **Implement (TS):** `countStrokes.ts` + `analyzeDrill.ts` insert pairing; `StrokeCountResult` gains `cycles` (and `reps` become cycle-derived); metrics at `cycle.drive.peakFrame`.
- [ ] **Verify:** `./gradlew :shared:jvmTest`; `cd poses_viewer && npx vitest run`.

### Task 6 — Viewer: one bi-colored band per cycle (TS only)
- [ ] **Failing test first:** `StrokesPage`/`StrokeTimeline` test (or a `strokeCycleWindow` test) asserting a kept paired cycle yields ONE band with a split at the backswing→drive valley, and an unpaired cycle yields a single-segment band.
- [ ] **Implement:** in the `StrokesPage` `entries` memo, build per-cycle band extents reusing `cycleWindow` extrema (start = pre-backswing wrist low, split = drive.startFrame valley, end = follow-through high); render two sub-segments with distinct colors; add a `«Цикл (бек+драйв)»` legend entry. Keep count display (`Повтори`) = cycle count.
- [ ] **Verify:** `npx vitest run`; `npx tsc -b --noEmit`.

### Task 7 — Viewer knob for `MAX_PAIR_GAP_MS` (TS only)
- [ ] **Failing test first:** none needed (UI wiring); add a `countStrokes`/`analyzeDrill` test asserting the gap override changes pairing on a synthetic case.
- [ ] **Implement:** thread `maxPairGapMs` through `StrokeCountConfig` / `DrillAnalysisConfig` (default 800); add a knob to `#/strokes` alongside the existing locomotion/minPeakSpeed knobs.
- [ ] **Verify:** `npx vitest run`; `npx tsc -b --noEmit`.

### Task 8 — Full-suite verification + golden parity audit + docs
- [ ] **Verify both suites green:** `./gradlew :shared:jvmTest` AND `cd poses_viewer && npx vitest run` AND `npx tsc -b --noEmit`.
- [ ] **Re-run E2E exit gate explicitly:** `./gradlew :shared:jvmTest --tests "*ForehandDriveEndToEndTest"` — andrii_1 stays 15.
- [ ] **Parity audit:** confirm Kotlin and TS report identical counts for andrii_1 (15) and video_4 (10); diff per-cycle output if they disagree (Kotlin is truth, TS is the bug — never loosen TS numbers).
- [ ] **Update docs in the SAME change:** poses_viewer/CLAUDE.md (drill2d section: pipeline order now includes CyclePairing; video_4 golden 8 → 10; bi-color band note); root CLAUDE.md gotcha "Wrist-speed peaks ≠ reps" → note the cycle step and that metrics stay at the drive peak; add/append a DESIGN_LIMITATIONS entry for `MAX_PAIR_GAP_MS` provisionality (extends L-30 lineage).
- [ ] **Commit** explicit paths (never `git add -A`).

---

## Risks (honest)

1. **`MAX_PAIR_GAP_MS = 800` provisionality — HIGHEST RISK.** It is tuned on TWO non-protocol clips (andrii_1, video_4). The mechanism is sharp: too LARGE re-introduces the andrii_1 15→11 regression (far peaks mis-paired → bimodal cycle durations → RepFilter trims) AND lets the cycle-window locomotion gate over-trigger; too SMALL fails to rescue video_4's short drives (back to 8). 600 and 800 both pass today, but protocol footage with different rest cadence could shift the safe band. Mitigation: named constant + viewer knob + DESIGN_LIMITATIONS entry; re-tune on protocol footage before any threshold is called final.

2. **Baseline-derivation impact.** The 003 path is argued safe because (a) the metric anchor is the unchanged drive peak and (b) andrii_1's rep SET is unchanged (same 15 drive peaks). This holds ONLY while andrii_1 stays at 15. If a future fixture's count changes under the cycle model, its derived baseline (means, 2σ bands, qualityScore) shifts — `BaselineDeriver`/`BaselineRuleFactory` tests must be re-audited per fixture. Task 8 re-runs the E2E gate to catch this.

3. **`RepFilter` semantics broadened.** Banding now spans paired (long) and unpaired (short) cycles under ONE duration median. On fixtures the distribution is unimodal enough (andrii med 0.97, all within [0.485, 1.94]); but a clip mixing many paired and many unpaired cycles could widen the spread and let a genuine non-stroke slip through the upper band, or trim a legitimate cycle at the lower band. Watch for fixtures where `paired/total` is near 0.5.

4. **Continuous-play pairing ambiguity.** The "attach the shared backswing to the LATER drive" rule is a heuristic; on dense rallies a backswing could legitimately belong to the earlier drive's follow-through. Harmless on current fixtures (video_4 paired only 4/12, all unambiguous), but unverified on true continuous-play footage — which the project does not yet have (DESIGN_LIMITATIONS L-28 lineage).

5. **TS/Kotlin drift.** Two banding functions, two pairing functions, two loco windows now mirror across suites. The binding fix-flow rule (Kotlin first, mirror, goldens in both same change) must be followed strictly or the golden-parity tests will catch a divergence as a TS "bug" that is actually a missed mirror.

6. **What could regress that we already checked and cleared:** video_4 walking step #16 is still dropped under cycle-window loco (hip 1.14); the trailing junk #17 still band-drops; andrii_1's three short unpaired drives (7.17, 15.71, 18.21 — all ~0.48–0.53 s) stay in-band because the cycle median (0.97) keeps the lower edge at ~0.485. These are tight margins: a fixture re-export that shifts the median could clip them. Re-verify counts after any fixture re-export.
