# Phase 0 Kickoff — `BaselineDeriver` (pure KMP logic)

**Read this first in a new chat.** This file is a self-contained brief so a fresh assistant instance can pick up Phase 0 work without re-discovering context.

---

## 1. Orientation (read these, in this order)

1. [CLAUDE.md](../../CLAUDE.md) — project commands, structure, conventions, gotchas
2. [docs/STAGED_ROADMAP.md](../../docs/STAGED_ROADMAP.md) — overall 3-stage roadmap (Stage 1 pose-only 🟡 IN PROGRESS)
3. [specs/003-stage1-calibration/spec.md](spec.md) — feature spec for calibration (Phase 1 of Stage 1)
4. [specs/003-stage1-calibration/plan.md](plan.md) — implementation plan; Phase 0 is the first section

The assistant's persistent memory already contains two project memory entries — `product_positioning.md` and `staged_roadmap.md` — those load automatically.

---

## 2. Product principle (the one thing not to forget)

**Don't re-teach the player a universal "correct" technique — build on their own.**

TT has no universal correct form (Chinese vs European schools, shakehand vs penhold, classical vs modern topspin all legitimately differ). A rigid "elbow must be at 120°" rule ships wrong advice to half the users and breaks credibility. Instead: the player calibrates to their own technique once, and the app checks **consistency with their personal baseline**, not a universal ideal.

This is why calibration is Phase 1 of Stage 1 — every downstream rule needs a personal baseline to compare against.

---

## 3. Phase 0 scope — what you are building

**Phase 0 is pure KMP logic in `shared/commonMain`. No Android. No UI. No Room.** Just data classes + derivation function + unit tests on existing JSON fixtures.

### Deliverables

Create these files in `shared/src/commonMain/kotlin/com/ttcoachai/shared/`:

1. **`models/MetricStats.kt`** — data class
   ```kotlin
   data class MetricStats(
       val mean: Double,
       val std: Double,
       val min: Double,
       val max: Double,
       val sampleCount: Int
   )
   ```

2. **`models/PersonalBaseline.kt`** — data class representing a player's personal baseline for a drill
   - `drillType: String` (e.g., `"forehand_shadow"`, `"backhand_shadow"`, `"footwork_shuffle"`)
   - `metricStats: Map<String, MetricStats>` — stats per named metric (e.g., `"peak_elbow_angle"`, `"body_rotation_at_contact"`)
   - `phaseDurationsMs: Map<String, MetricStats>` — stats on each StrokePhase's duration in ms
   - `repCount: Int` — valid reps used to derive (after outlier exclusion)
   - `excludedRepIndices: List<Int>` — which source reps were excluded as outliers
   - `qualityScore: Double` — 0.0 to 1.0, higher = more consistent calibration
   - `createdAtMs: Long` — epoch millis (use `kotlin.time.Clock.System.now().toEpochMilliseconds()` if Clock available, or accept as constructor param for testability)
   - `drillerHandedness: String?` — optional, `"right"` or `"left"` — punt on derivation for now; accept as parameter

3. **`analysis/BaselineDeriver.kt`** — function that consumes existing pipeline output and produces a `PersonalBaseline`
   - Input: `List<DetectedStroke>` (from [JsonStrokeDetector](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/JsonStrokeDetector.kt)) + `List<AnalysisResult>` (from [StrokeAnalyzer](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/StrokeAnalyzer.kt))
   - Output: `PersonalBaseline`
   - Algorithm:
     1. Extract per-rep metric values (walk the parallel lists — one `AnalysisResult` per stroke). **Read `AnalysisResult` first** to see what metrics are already computed (`wristAngle`, `bodyRotation`, `followThroughAngle`, `contactHeight`, `elbowBodyDistance`). These are your starting metric set.
     2. For each metric, compute mean + std iteratively. Exclude reps more than 2σ from the running mean (one pass is fine for v1 — iterative outlier rejection can come later).
     3. Compute per-phase durations from `DetectedStroke` phase boundary frames × frame interval.
     4. `qualityScore = 1.0 - mean(normalizedStd)` where `normalizedStd = std / mean` across all metrics (coefficient of variation). Clamp to [0, 1].
     5. Require `repCount >= 10` post-exclusion; throw `IllegalArgumentException` if fewer valid reps remain.
   - Edge cases to handle: all metrics identical (std = 0 → no outliers to exclude, quality = 1.0), empty input (throw), negative stats (shouldn't happen but defend).

4. **`analysis/BaselineRule.kt`** — **data-only sealed type** for downstream phases. NOT evaluated in Phase 0; just the shape so Phase 2 can depend on it.
   ```kotlin
   sealed class BaselineRule {
       abstract val id: String
       abstract val metricKey: String

       data class ConsistencyRule(
           override val id: String,
           override val metricKey: String,
           val kSigma: Double  // "stay within mean ± k·σ"
       ) : BaselineRule()

       data class RegressionRule(
           override val id: String,
           override val metricKey: String,
           val maxDropFromMean: Double  // "don't drop more than this below mean"
       ) : BaselineRule()

       data class RhythmRule(
           override val id: String,
           override val metricKey: String,  // here metricKey = phase name
           val maxDurationDeviationPct: Double  // "phase duration within ±k%"
       ) : BaselineRule()
   }
   ```

### Tests

Create `shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/BaselineDeriverTest.kt`:

1. **Happy path** — feed the existing fixture [shared/src/commonTest/resources/fixtures/forehand_drive.json](../../shared/src/commonTest/resources/fixtures/forehand_drive.json) through `JsonStrokeDetector` + `StrokeAnalyzer`, then pass into `BaselineDeriver`. Assert:
   - `repCount` matches expected stroke count (check the fixture first)
   - Each expected metric key is present in `metricStats`
   - `mean` is finite and reasonable (not NaN, not 0 for angle metrics)
   - `qualityScore` between 0 and 1
2. **Insufficient reps** — feed only 3 strokes, assert `IllegalArgumentException`.
3. **Outlier exclusion** — manually construct a `List<AnalysisResult>` where one rep has a metric wildly outside the others. Assert that rep's index appears in `excludedRepIndices` and it doesn't skew the mean.
4. **Zero variance** — feed identical `AnalysisResult` values, assert `std = 0` for all metrics and `qualityScore = 1.0`.
5. **Determinism** — run derivation twice with same input, assert byte-identical outputs.

Use the existing `JsonTestUtils` pattern if it helps, or load JSON directly with `kotlin.io.path` / `java.io.File`. JVM-only test code is fine (put in `jvmTest` if using `java.io.File`; in `commonTest` if using kotlin.io only).

---

## 4. Reuse — do NOT rewrite these

- [shared/.../detection/JsonStrokeDetector.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/JsonStrokeDetector.kt) — rep detection state machine. Use as-is.
- [shared/.../analysis/StrokeAnalyzer.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/StrokeAnalyzer.kt) — metric extraction. Use as-is.
- [shared/.../models/AnalysisResult.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/models/) — existing data class; read it to see which metrics are available.
- [shared/.../models/DetectedStroke.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/models/) — existing; contains phase boundary frames.
- [shared/.../models/ExerciseParameters.kt](../../shared/src/commonMain/kotlin/com/ttcoachai/shared/models/ExerciseParameters.kt) — existing universal thresholds; **do not modify**. Will be deprecated once calibration replaces them, but not in Phase 0.

---

## 5. Do NOT build in Phase 0

- No Room entity (that's Phase 1)
- No UI / Compose (that's Phase 2)
- No Android code (Phase 0 is purely in `shared/commonMain`)
- No `BaselineRule` evaluator (that's Stage 1 Phase 2). Only the data shape.
- No outlier iteration passes beyond a single 2σ sweep.
- No serialization (JSON/protobuf) of `PersonalBaseline` — Phase 1 handles persistence format.
- No camera, no MediaPipe, no CameraX.

---

## 6. Definition of done for Phase 0

- [ ] `PersonalBaseline`, `MetricStats`, `BaselineRule` compile in `shared/commonMain`
- [ ] `BaselineDeriver` compiles and is callable from `shared/commonMain`
- [ ] `./gradlew :shared:jvmTest` passes with new tests
- [ ] `./gradlew test` still green (no regressions in `:app` tests)
- [ ] `BaselineDeriverTest` covers all 5 scenarios from section 3 above
- [ ] Spec updated: tick the Phase 0 deliverables in [plan.md](plan.md) §Phases

---

## 7. How to run

```bash
./gradlew :shared:jvmTest              # fast, target this during development
./gradlew test                         # full suite before marking done
./gradlew :shared:jvmTest --tests BaselineDeriverTest  # single class
```

JDK setup is already pinned in `gradle.properties` (`org.gradle.java.home` points to Android Studio's JDK 21). No manual JDK selection needed.

---

## 8. Open questions — decide as you go, document in spec

- Exact minimum rep count (currently 10 in plan; may need 15 for noisy swing metrics)
- Whether outlier rejection should iterate (recompute mean after exclusion, loop until stable)
- How to handle left-handed vs right-handed (for now: accept handedness as parameter, don't auto-detect)
- Which specific metrics go into the baseline vs which are merely observed (start with all 5 from `AnalysisResult`; trim later)

Surface these to the user before making big assumptions; small defaults are fine.

---

## 9. Git / branching

- Current branch: `003-stage1-calibration` (already created, already has build fix + planning docs committed)
- Commit Phase 0 as a single logical commit or 2-3 if it grows. Prefer descriptive messages — "update" is not enough (see CLAUDE.md feedback guidance).

---

## 10. When to pause and ask the user

- If `AnalysisResult` doesn't expose a metric you need and you'd need to modify `StrokeAnalyzer` — stop and confirm scope. Phase 0 is supposed to be additive.
- If existing tests start failing because of your changes — stop and investigate; don't patch tests to make them green.
- If you realize calibration needs a concept that doesn't exist yet (e.g., "warmup reps vs calibration reps") — bring it up; don't invent silently.

Good luck. Keep it tight; Phase 0 is ~1-2 days of focused work, not a week.
