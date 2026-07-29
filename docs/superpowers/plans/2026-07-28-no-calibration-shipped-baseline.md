# Training without calibration — editable drill bands seeded from Andrii — Implementation Plan

## For agentic workers

**REQUIRED SUB-SKILL:** Execute this plan task-by-task via `superpowers:subagent-driven-development`
(fresh subagent per task, review between tasks). Do not execute inline in the orchestrating session,
and do not ask which execution approach to use — subagent-driven is the standing default.

**Goal:** Every drill (built-in-turned-seeded or custom) opens and trains immediately on the live
RTM path — no calibration gate — with per-metric reference bands seeded from a curated Andrii
baseline and editable per drill. Calibration ("Reference: Baseline") stays available as an
explicit opt-in that still gates on a personal baseline.

**Architecture:** `ShippedBaselines.FOREHAND_ANDRII` (new shared-KMP constant, curated one-time
from a MediaPipe-lite re-export of `Videos/andrii_1/andrii_1.mp4`) plays two roles: (a) source of
`defaultBands()` — `mean ± 2σ` per metric, baked into two seeded `CustomDrillEntity` rows'
`perPhaseTargetsJson` at seed time; (b) the σ-carrier baseline `LiveDrillSession` uses in
"standard" mode so qualitative cues (`coil_ratio`, `stroke_speed`) get a real severity scale
instead of the crude degrees-shaped fallback constant. `CustomDrillEntity.referenceType`
(already persisted, currently dead) gets read for the first time: `"standard"` → shipped
baseline + drill bands, no gate; `"baseline"` → personal calibration, gate restored (unchanged
today's behavior). A new nullable `CustomDrillEntity.movementProfile` column (not an id-string
match) carries the General drill's widened locomotion tolerance through to
`LiveDrillSession.hipTravelMaxTorso` — a gap that exists today because `RtmposeTrainingController`
never plumbed it through. The exercise editor collapses from 10 per-phase rows (8 dead, wired to
nothing) to exactly the 7 rows matching `DrillMetrics.ALL_KEYS`.

**Tech Stack:** Kotlin KMP `shared/` (zero external deps, hand-rolled JSON, `kotlin.math` only),
Android `app/` (Room, org.json, Material3 XML layouts), Python 3.13 desktop MediaPipe export
script (`scripts/poses/export_poses_mediapipe.py`), JUnit/`kotlin.test` for jvmTest.

**Source spec:** `docs/superpowers/specs/2026-07-27-no-calibration-shipped-baseline-design.md`
(read it first — this plan implements it exactly, no scope additions).

## Global Constraints

- `shared/` has **zero external dependencies** — no kotlinx-serialization, no org.json, no
  `java.lang.Math`. Use `kotlin.math` only. JSON parsing (where `shared/` ever needs it) is
  hand-rolled; `app/` uses `org.json`.
- Resource-loading tests (`ClassLoader.getResourceAsStream`) go in `shared/src/jvmTest`, never
  `shared/src/commonTest` (commonTest has no ClassLoader). Ad-hoc file reads (this plan's Task A
  harness, which reads a file that is NOT a packaged test resource) also belong in `jvmTest`.
- `git add` **explicit paths only** — never `git add -A`. The working tree carries concurrent-session
  edits (`MediaPipeMapper.kt`, `MotionAnalyzer.kt`, `StrokePhaseDetector.kt`, `SettingsFragment.kt`,
  `BaseActivity.kt`, `LocaleHelper.kt`, `scripts/run_on_phone.sh`, `.claude/memory/*`,
  `app/src/main/res/xml/`, `docs/superpowers/plans/2026-07-24-remove-mediapipe-legacy-pipeline.md`,
  and possibly more by the time this executes) — **do not touch, revert, or re-stage any file this
  plan doesn't explicitly list.** Never `git checkout --`/`git restore`/`git reset --hard`/`git stash
  drop`/force-anything.
- Commit after each logical change (each task, or each numbered step within a task where noted).
- Test commands: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.<FQCN>"` and
  `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.<FQCN>"`. **Never** read a red full
  `./gradlew test` / `:app:testDebugUnitTest` as your own regression — `MotionAnalyzerJsonTest` has
  a pre-existing unrelated failure in frozen legacy code; always scope with `--tests`.
- Build gate: `./gradlew :app:assembleDebug` after any task touching `app/src/main` or a Gradle file.
- Before citing any file:line or calling any API, **re-read the actual current file** — the spec's
  line numbers may have drifted (concurrent sessions) and this plan's own line numbers are a
  snapshot from when it was written; re-locate by content/grep if a line number looks off.
- No placeholders, no "TODO", no "add appropriate error handling" — every step has complete code,
  **except** the literal derived numbers inside `ShippedBaselines.kt` (Task B), which Task A
  produces at execution time and Task B's own step tells you exactly what to paste and where.
- First task creates branch `feat/no-calibration-shipped-baseline` from current HEAD via plain
  `git checkout -b feat/no-calibration-shipped-baseline` — the dirty tree carries over onto the new
  branch; this is expected and fine, do not clean it.

## File Structure

```
Videos/andrii_1/
  andrii_1_poses_mediapipe_lite.json          NEW (Task A, git add -f — see Task A step 3)
docs/
  shipped-baseline-derivation.md              NEW (Task A)
  DESIGN_LIMITATIONS.md                       MODIFY (Task I — 4 new L-entries)
shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/
  ShippedBaselines.kt                         NEW (Task B)
shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/
  ShippedBaselineDerivationHarness.kt         NEW (Task A)
  ShippedBaselinesTest.kt                     NEW (Task B)
shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/
  BaselineRuleFactoryApplyRangeOverridesTest.kt  MODIFY (Task H — add empty-seed multi-band test)
app/src/main/java/com/ttcoachai/util/
  PerPhaseTargetsCodec.kt                     MODIFY (Task C — legacy-key map, decimal, encode())
  DrillReferenceResolver.kt                   NEW (Task H)
  SeededDrillsPolicy.kt                       NEW (Task F)
app/src/test/java/com/ttcoachai/util/
  PerPhaseTargetsCodecTest.kt                 MODIFY (Task C)
  SeededDrillsPolicyTest.kt                   NEW (Task F)
  DrillReferenceResolverTest.kt               NEW (Task H)
app/src/main/java/com/ttcoachai/ui/
  ExerciseEditorActivity.kt                   MODIFY (Task D)
app/src/main/res/layout/
  activity_exercise_editor.xml                MODIFY (Task D)
app/src/main/res/values/strings.xml           MODIFY (Task D)
app/src/main/res/values-uk/strings.xml        MODIFY (Task D)
app/src/main/java/com/ttcoachai/models/
  CustomDrillEntity.kt                        MODIFY (Task E — movementProfile column)
app/src/test/java/com/ttcoachai/util/
  CommunityDrillCopierTest.kt                 MODIFY (Task E — assert movementProfile stays null)
app/src/main/java/com/ttcoachai/db/
  AppDatabase.kt                              MODIFY (Task E — version 9 -> 10)
app/src/main/java/com/ttcoachai/
  TTCoachApplication.kt                        MODIFY (Task F — seeding trigger)
app/src/main/java/com/ttcoachai/fragment/
  DrillsFragment.kt                           MODIFY (Task F — remove 3 hardcoded rows; Task H — new intent extras)
app/src/main/java/com/ttcoachai/pose/
  RtmposeTrainingController.kt                MODIFY (Task G — rules + hipTravelMaxTorso params)
app/src/main/java/com/ttcoachai/
  TrainingActivity.kt                         MODIFY (Task H — referenceType consulted, generic bands)
```

## Investigation notes (facts gathered while writing this plan — do not re-derive)

- **`AppDatabase.version` is currently `9`** (confirmed by reading the file), so Task E's bump is
  `9 -> 10` exactly as the spec says (spec's own "9→10" claim checks out).
- **`TrainingActivity.kt`'s current structure** (re-read in full, not from the spec's possibly-stale
  line numbers): `kneeBendStrikeBand: ClosedRange<Double>?` field, populated only from
  `PerPhaseTargetsCodec.KEY_KNEES_STRIKE` inside `initializeAnalysis()`, is the sole entry ever
  passed into `RtmposeTrainingController`'s `metricBands` parameter (as
  `kneeBendStrikeBand?.let { mapOf(DrillMetrics.METRIC_KNEE_BEND to it) } ?: emptyMap()`). This
  matches the spec's description; Task H replaces this whole mechanism.
- **`RtmposeTrainingController`'s constructor has exactly one caller** (`TrainingActivity.startRtmController`)
  — confirmed via repo-wide grep — so Task G/H can add new constructor parameters with defaults
  without needing to touch any other call site.
- **`exerciseParameters` (the legacy `ExerciseParameters` built in `TrainingActivity.initializeAnalysis()`)
  is dead** — confirmed via grep: nothing outside its own `.copy()` chain in that function ever
  reads the field. This plan does **not** touch that block (leave it exactly as-is; it still
  compiles fine after Task C/H's changes since it only reads `PerPhaseTargetsCodec.KEY_KNEES_BACKSWING`,
  which stays an unmapped passthrough key).
- **`PersonalBaselineRepository.baselineDrillType()`'s `"forehand_drive_general" -> "forehand_drive"`
  special case is already dead today**, independent of this spec: `TrainingActivity.loadRtmBaseline()`
  always queries the literal `"forehand_drive_rtm"`, never `exerciseId`, so that mapping is never
  actually exercised by the live path. Not touched by this plan (out of scope; noted for the record).
- **`SessionReviewFragment.onTrainAgain()`** launches `TrainingActivity` with only `EXERCISE_ID`/
  `EXERCISE_NAME` — it does not pass `PER_PHASE_TARGETS_JSON` today, for ANY drill, custom or not
  (pre-existing gap, unrelated to this spec). After this plan, "Train Again" (no `REFERENCE_TYPE`
  extra) falls back to standard mode + `ShippedBaselines.defaultBands()` — trains immediately
  instead of being gated (a strict improvement over today, where every drill is gated regardless of
  `referenceType` since it's never read). It does **not** restore a custom drill's own tuned bands
  or `"baseline"` mode on relaunch — this plan does not fix that (out of scope; the spec never
  mentions `SessionReviewFragment`). Not a regression: today it's also gated and also loses bands.
- **`ExerciseSelectionActivity`** is manifest-declared but has **zero** `Intent(..., ExerciseSelectionActivity::class.java)`
  call sites anywhere in `app/src/main` — dead entry point, not touched.
- **`DrillsFragment.iconForDrill()`'s `"forehand_drive", "forehand_andrii", "forehand_drive_general" -> R.drawable.ic_skill_forehand`
  branch stays** — it's still useful for the Recent-session card icon on old sessions recorded
  against those (now-removed-from-the-catalog) ids. Not removed by Task F.
- **`.gitignore`** ignores `/Videos/**` except directory placeholders and `*_anchor_fixtures.json`
  files — yet `Videos/andrii_1/andrii_1_poses_rtm.json` **is** tracked (predates the ignore rule, or
  was force-added). Task A's new `andrii_1_poses_mediapipe_lite.json` must be added with `git add -f`
  to follow that precedent, or it will silently not be staged.
- **`export_poses_mediapipe.py`** confirmed flags: `--model {lite,full,heavy}` (default `lite`),
  `--interval` ms (default `100`), `--out-dir` (default: same folder as the video). Output filename:
  `<video_stem>_poses_mediapipe_<model>.json`. For `andrii_1.mp4` with `--model lite` and no
  `--out-dir`, output is `Videos/andrii_1/andrii_1_poses_mediapipe_lite.json`.
- **`PersonalBaseline`** (`shared/.../models/PersonalBaseline.kt`) constructor: `drillType: String,
  metricStats: Map<String, MetricStats>, phaseDurationsMs: Map<String, MetricStats>, repCount: Int,
  excludedRepIndices: List<Int>, qualityScore: Double, createdAtMs: Long, drillerHandedness: String?
  = null`. `MetricStats(mean: Double, std: Double, min: Double, max: Double, sampleCount: Int)`.
- **`DrillMetrics.ALL_KEYS`** = `elbow_angle, shoulder_angle, knee_bend, torso_lean,
  follow_through_angle_2d, stroke_speed, coil_ratio` (7 keys, `PEAK_KEYS` (4) + `DERIVED_KEYS` (3)).

---

## Task A — Derive the Andrii baseline data

This is a **data-production task**, not a code-design task: run the export, run a diagnostic
harness against the result, visually sanity-check the selected reps, and record the numbers +
rationale. The numbers this task produces are consumed verbatim by Task B.

**Files:**
- Create: `Videos/andrii_1/andrii_1_poses_mediapipe_lite.json` (export output, git-add -f)
- Create: `shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/ShippedBaselineDerivationHarness.kt`
- Create: `docs/shipped-baseline-derivation.md`

**Interfaces:**
- Consumes: `DrillCalibrator.calibrate(sequence, drillType, createdAtMs, handedness, minRepCount,
  outlierSigmaThreshold, detector, cameraYawDeg, maxCameraYawDeg, hipTravelMaxTorso)` (existing);
  `com.ttcoachai.shared.io.PoseJsonV2Parser.parse(json: String): PoseSequence2D` (existing);
  `StrokeDetector2D().detect(frames, handedness, xScale, intervalMs)`, `ForwardStrokeFilter.filter`,
  `RepFilter.filter`, `LocomotionFilter.filterStationary`, `DrillMetrics.extractAtPeak`,
  `DerivedMetrics.merge`, `CameraAngleEstimator.estimateYawForStroke` (all existing, public).
- Produces: printed stdout (captured by the executor into `docs/shipped-baseline-derivation.md`)
  giving, per metric key in `DrillMetrics.ALL_KEYS`: `mean`, `std`, `min`, `max`, `sampleCount`
  (i.e. a full `PersonalBaseline.metricStats` dump) plus `phaseDurationsMs`, `repCount`,
  `excludedRepIndices`, `qualityScore`, and a `createdAtMs` candidate — everything Task B needs to
  paste into `ShippedBaselines.FOREHAND_ANDRII`.

- [ ] **Step 1: Run the MediaPipe-lite export**

  ```bash
  .venv/bin/python scripts/poses/export_poses_mediapipe.py Videos/andrii_1/andrii_1.mp4 --model lite
  ```

  This writes `Videos/andrii_1/andrii_1_poses_mediapipe_lite.json` (schema v2, COCO-17,
  `intervalMs: 100` — the script's default — matching what this session's own research appendix
  already used for its 13-clip survey, so rep counts stay comparable to the spec's quoted numbers).
  Confirm the script's own printed summary line (`N frames, M with pose detected`) shows a
  non-trivial detection rate before proceeding.

- [ ] **Step 2: Verify the file exists and is schema v2**

  ```bash
  python3 -c "import json; d=json.load(open('Videos/andrii_1/andrii_1_poses_mediapipe_lite.json')); print(d['schemaVersion'], d['topology'], d['model'], d['totalFrames'], d['videoWidth'], d['videoHeight'])"
  ```

  Expected: `2 coco17 mediapipe-lite <N> <W> <H>`.

- [ ] **Step 3: Force-add the export JSON (gitignore blocks `/Videos/**` by default)**

  ```bash
  git add -f Videos/andrii_1/andrii_1_poses_mediapipe_lite.json
  ```

  Do not commit yet — bundle this with the harness file at the end of Step 6.

- [ ] **Step 4: Write the derivation harness**

  Create `shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/ShippedBaselineDerivationHarness.kt`:

  ```kotlin
  package com.ttcoachai.shared.drill

  import com.ttcoachai.shared.analysis.CameraAngleEstimator
  import com.ttcoachai.shared.detection.StrokeDetector2D
  import com.ttcoachai.shared.io.PoseJsonV2Parser
  import com.ttcoachai.shared.models.Handedness
  import com.ttcoachai.shared.models.PoseSequence2D
  import java.io.File
  import kotlin.test.Test
  import kotlin.test.assertTrue

  /**
   * ONE-TIME editorial derivation tool for `ShippedBaselines.FOREHAND_ANDRII` (docs/superpowers/
   * specs/2026-07-27-no-calibration-shipped-baseline-design.md §1). Not a regression gate on its
   * own numbers — prints per-rep diagnostics (metrics + |yaw|) plus the final derived
   * PersonalBaseline so a human can visually sanity-check rep selection (poses_viewer /
   * visualize-pose skill) before pasting numbers into ShippedBaselines.kt.
   *
   * `cameraYawDeg` is pinned to 0f — the SAME established convention
   * `ForehandDriveEndToEndTest.calibrated()` already uses for this exact fixture family — a
   * DELIBERATE, one-time relaxation of the placement gate for this editorial derivation only
   * (real per-rep |yaw| on this footage runs ~41-90°, past the normal ~30° gate; spec §1
   * explicitly sanctions relaxing it here, "an intentional exception, not a precedent").
   *
   * Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ShippedBaselineDerivationHarness"`
   * and read the captured stdout — that IS this task's deliverable, copied into
   * docs/shipped-baseline-derivation.md and pasted into ShippedBaselines.kt (Task B).
   */
  class ShippedBaselineDerivationHarness {

      private fun loadSequence(): PoseSequence2D {
          // Gradle's jvmTest working directory is the `shared/` module dir, so `../` reaches
          // the repo root. If this file isn't found, print `File(".").absolutePath` and adjust
          // the relative prefix — do not silently swap in a different (e.g. classpath) fixture.
          val file = File("../Videos/andrii_1/andrii_1_poses_mediapipe_lite.json")
          require(file.exists()) {
              "Missing ${file.path} (absolute: ${file.absolutePath}, cwd: ${File(".").absolutePath}) " +
                  "— run `.venv/bin/python scripts/poses/export_poses_mediapipe.py " +
                  "Videos/andrii_1/andrii_1.mp4 --model lite` from the repo root first (Task A step 1)."
          }
          return PoseJsonV2Parser.parse(file.readText())
      }

      @Test
      fun printPerRepDiagnosticsAndDerivedBaseline() {
          val seq = loadSequence()
          val handedness = Handedness.RIGHT
          val xScale = seq.aspectRatio // cameraYawDeg=0 -> ViewGeometry(aspectRatio, 0f).xScale == aspectRatio

          // Same detect -> ForwardStrokeFilter -> RepFilter -> LocomotionFilter chain
          // DrillCalibrator.calibrate() runs internally (mirrors LiveDrillSession.onFrame /
          // ForehandDriveDrillAnalyzer.analyze) — replicated here with PUBLIC apis only, purely
          // to print per-rep diagnostics BEFORE derivation's own 2-sigma outlier exclusion runs.
          val detected = StrokeDetector2D().detect(seq.frames, handedness, xScale, seq.intervalMs)
          val forward = ForwardStrokeFilter.filter(detected, seq.frames, handedness)
          val banded = RepFilter.filter(forward)
          val stationary = LocomotionFilter.filterStationary(banded, seq.frames, xScale)

          println("=== ShippedBaseline derivation: andrii_1 (MediaPipe-lite) ===")
          println("createdAtMs candidate (paste literal): ${System.currentTimeMillis()}")
          println(
              "raw detected=${detected.size} forward=${forward.size} banded=${banded.size} " +
                  "stationary=${stationary.size}"
          )

          stationary.forEachIndexed { index, stroke ->
              val yaw = CameraAngleEstimator.estimateYawForStroke(seq.frames, stroke, xScale, seq.intervalMs)
              val metrics = DrillMetrics.extractAtPeak(seq.frames, stroke.peakFrame, handedness, xScale, seq.intervalMs) +
                  DerivedMetrics.merge(seq.frames, stroke, handedness, xScale, seq.intervalMs)
              val metricsStr = metrics.entries.joinToString(", ") { (k, v) -> "$k=${"%.1f".format(v)}" }
              val yawStr = yaw?.let { "%.1f".format(it) } ?: "null"
              println("rep[$index] peakFrame=${stroke.peakFrame} startFrame=${stroke.startFrame} " +
                  "endFrame=${stroke.endFrame} yaw=$yawStr $metricsStr")
          }

          // Actual derivation, cameraYawDeg pinned per the class doc above.
          val baseline = DrillCalibrator.calibrate(
              sequence = seq,
              drillType = "forehand_drive",
              createdAtMs = 1L,
              handedness = handedness,
              minRepCount = 3,
              cameraYawDeg = 0f
          )

          println("=== Derived PersonalBaseline ===")
          println(
              "repCount=${baseline.repCount} excludedRepIndices=${baseline.excludedRepIndices} " +
                  "qualityScore=${baseline.qualityScore}"
          )
          println("--- metricStats (paste into ShippedBaselines.FOREHAND_ANDRII.metricStats) ---")
          for (key in DrillMetrics.ALL_KEYS) {
              val stats = baseline.metricStats[key]
              println("\"$key\" to MetricStats(mean=${stats?.mean}, std=${stats?.std}, " +
                  "min=${stats?.min}, max=${stats?.max}, sampleCount=${stats?.sampleCount}),")
          }
          println("--- phaseDurationsMs (paste into ShippedBaselines.FOREHAND_ANDRII.phaseDurationsMs) ---")
          for ((key, stats) in baseline.phaseDurationsMs) {
              println("\"$key\" to MetricStats(mean=${stats.mean}, std=${stats.std}, " +
                  "min=${stats.min}, max=${stats.max}, sampleCount=${stats.sampleCount}),")
          }

          // Sanity only — this harness's value is the printed diagnostics above, not an assertion.
          assertTrue(baseline.repCount > 0, "derivation must yield at least one kept rep")
          assertTrue(baseline.metricStats.isNotEmpty(), "at least some in-plane metrics must derive")
      }
  }
  ```

- [ ] **Step 5: Run the harness and capture stdout**

  ```bash
  ./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ShippedBaselineDerivationHarness" 2>&1 | tee /tmp/shipped_baseline_derivation_output.txt
  ```

  (Redirecting to a scratch file is fine here — this raw capture is a working artifact, not the
  deliverable; the deliverable is the curated summary you write into
  `docs/shipped-baseline-derivation.md` in Step 7. `--info` may be needed if Gradle swallows
  `println` output: `./gradlew :shared:jvmTest --tests "..." --info | tee ...`.)

- [ ] **Step 6: Visually verify the selected reps**

  For each `rep[i]` printed in Step 5, especially any with an elbow_angle in the ~111-135° range
  (the spec's noted bimodal recovery-swing candidates) or a surprising yaw, render its peak frame:

  ```bash
  .venv/bin/python .claude/skills/visualize-pose/render_pose.py Videos/andrii_1/andrii_1_poses_mediapipe_lite.json --frame <peakFrame> --out tmp/shipped_baseline_review/rep_<i>_frame_<peakFrame>.png
  ```

  Read each PNG. A genuine forward forehand strike shows the racket arm extended forward/across
  the body at contact; a recovery/backward swing shows the arm still coiled or moving away. Note
  per-rep verdicts (keep / exclude / uncertain) — this is what `docs/shipped-baseline-derivation.md`
  records in Step 7. If the automatic pipeline (Step 5's `stationary` list feeding
  `DrillCalibrator.calibrate`) already excluded the wrong-direction reps via its own 2σ outlier
  exclusion (check `excludedRepIndices` against which rep indices looked wrong visually), no
  further action is needed — just document the match. If a rep that visually looks like a genuine
  recovery swing was NOT auto-excluded (i.e. it's polluting `metricStats`), note this explicitly in
  the derivation doc as a known deviation and proceed with the harness's own numbers anyway (per
  spec: rep-selection nuance is documented, not hand-patched into a second, undocumented derivation
  path — the shipped constant must trace to one reproducible run).

- [ ] **Step 7: Write the derivation doc**

  Create `docs/shipped-baseline-derivation.md`:

  ```markdown
  # Shipped baseline derivation — ShippedBaselines.FOREHAND_ANDRII

  Date: <today's date>

  ## Command

  ```
  .venv/bin/python scripts/poses/export_poses_mediapipe.py Videos/andrii_1/andrii_1.mp4 --model lite
  ```

  Output: `Videos/andrii_1/andrii_1_poses_mediapipe_lite.json` (schema v2, COCO-17, force-added
  to git per the `andrii_1_poses_rtm.json` precedent — `.gitignore` blocks `/Videos/**` by default).

  ## Derivation call

  `DrillCalibrator.calibrate(sequence, drillType = "forehand_drive", createdAtMs = 1L,
  handedness = Handedness.RIGHT, minRepCount = 3, cameraYawDeg = 0f)` — see
  `ShippedBaselineDerivationHarness.kt`.

  **Yaw gate deliberately relaxed**: real per-rep |yaw| on this footage ran <fill in observed
  range from Step 5 stdout>, past the normal ~30° placement gate (`CameraAngleEstimator` saturates
  on this non-protocol footage, see L-25). `cameraYawDeg = 0f` treats fixture geometry as reference
  for this one-time editorial derivation only — not a precedent for live sessions.

  ## Rep selection

  - Raw detected: <N> · after ForwardStrokeFilter: <N> · after RepFilter: <N> · after
    LocomotionFilter: <N> · final kept (post 2σ exclusion): <baseline.repCount> ·
    excluded as outliers: <baseline.excludedRepIndices>.
  - Visual verification (`visualize-pose` skill, peak frames): <list rep indices reviewed and
    verdict — e.g. "reps 0-9 confirmed genuine forward strikes (arm extended at contact); reps
    10-14 confirmed recovery swings, auto-excluded by 2σ outlier exclusion — matches visual
    review, no manual override needed.">
  - Any deviations from pure automatic exclusion: <none, or describe + rationale>.

  ## Numbers

  <paste the full stdout block from Step 5 here verbatim for the record>
  ```

- [ ] **Step 8: Build/test gate**

  ```bash
  ./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ShippedBaselineDerivationHarness"
  ```

  Expected: PASS (green), with the diagnostic stdout visible.

- [ ] **Step 9: Commit**

  ```bash
  git add -f Videos/andrii_1/andrii_1_poses_mediapipe_lite.json
  git add shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/ShippedBaselineDerivationHarness.kt docs/shipped-baseline-derivation.md
  git commit -m "$(cat <<'EOF'
  data(baseline): derive Andrii's shipped forehand baseline from MediaPipe-lite

  One-time editorial derivation for ShippedBaselines.FOREHAND_ANDRII (Task B) — re-exports
  andrii_1 through the same MediaPipe-lite backend the live app uses, runs it through the
  existing DrillCalibrator path, and records rep selection + rationale for reproducibility.

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Task B — `ShippedBaselines.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ShippedBaselines.kt`
- Create: `shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/ShippedBaselinesTest.kt`

**Interfaces:**
- Consumes: Task A's printed `metricStats`/`phaseDurationsMs`/`repCount`/`excludedRepIndices`/
  `qualityScore`/`createdAtMs` values; `PersonalBaseline`, `MetricStats` (existing);
  `BaselineRuleFactory.DEFAULT_CONSISTENCY_K_SIGMA` (existing, = `2.0`).
- Produces: `ShippedBaselines.FOREHAND_ANDRII: PersonalBaseline` and
  `ShippedBaselines.defaultBands(): Map<String, ClosedRange<Double>>` — consumed by Task F
  (seeding) and Task H (`LiveDrillSession` baseline in standard mode + fallback bands).

- [ ] **Step 1: Write the failing test first (computes expectations FROM the object under test,
  so it can't go stale when numbers are pasted)**

  Create `shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/ShippedBaselinesTest.kt`:

  ```kotlin
  package com.ttcoachai.shared.drill

  import com.ttcoachai.shared.analysis.BaselineRuleFactory
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  class ShippedBaselinesTest {

      @Test
      fun baselineHasAllSevenMetricKeys() {
          for (key in DrillMetrics.ALL_KEYS) {
              assertTrue(
                  key in ShippedBaselines.FOREHAND_ANDRII.metricStats,
                  "FOREHAND_ANDRII.metricStats must contain $key"
              )
          }
      }

      @Test
      fun baselineHasPlausibleHandednessAndRepCount() {
          assertEquals("right", ShippedBaselines.FOREHAND_ANDRII.drillerHandedness)
          assertTrue(ShippedBaselines.FOREHAND_ANDRII.repCount > 0)
          assertTrue(ShippedBaselines.FOREHAND_ANDRII.qualityScore in 0.0..1.0)
      }

      @Test
      fun defaultBandsCoversAllSevenKeysWithMinLessThanMax() {
          val bands = ShippedBaselines.defaultBands()
          assertEquals(DrillMetrics.ALL_KEYS.toSet(), bands.keys)
          for ((key, band) in bands) {
              assertTrue(band.start < band.endInclusive, "$key band must have min < max")
          }
      }

      @Test
      fun defaultBandsMatchMeanPlusMinusTwoSigmaIndependently() {
          val bands = ShippedBaselines.defaultBands()
          for (key in DrillMetrics.ALL_KEYS) {
              val stats = ShippedBaselines.FOREHAND_ANDRII.metricStats.getValue(key)
              val spread = BaselineRuleFactory.DEFAULT_CONSISTENCY_K_SIGMA * stats.std
              val expectedLow = stats.mean - spread
              val expectedHigh = stats.mean + spread
              val band = bands.getValue(key)
              assertTrue(
                  kotlin.math.abs(band.start - expectedLow) < 0.02,
                  "$key band.start=${band.start} must match mean-2sigma=$expectedLow (within rounding)"
              )
              assertTrue(
                  kotlin.math.abs(band.endInclusive - expectedHigh) < 0.02,
                  "$key band.endInclusive=${band.endInclusive} must match mean+2sigma=$expectedHigh (within rounding)"
              )
          }
      }
  }
  ```

- [ ] **Step 2: Run it to verify it fails (ShippedBaselines doesn't exist yet)**

  Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ShippedBaselinesTest"`
  Expected: FAIL (compile error — unresolved reference `ShippedBaselines`).

- [ ] **Step 3: Write `ShippedBaselines.kt`, pasting Task A's numbers**

  Create `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ShippedBaselines.kt`. The
  `MetricStats(...)` values below are **placeholders to replace verbatim with Task A's printed
  output** (`docs/shipped-baseline-derivation.md`'s "Numbers" section) — every other line is final:

  ```kotlin
  package com.ttcoachai.shared.drill

  import com.ttcoachai.shared.analysis.BaselineRuleFactory
  import com.ttcoachai.shared.models.MetricStats
  import com.ttcoachai.shared.models.PersonalBaseline

  /**
   * Curated, one-time-derived shipped baseline (docs/superpowers/specs/
   * 2026-07-27-no-calibration-shipped-baseline-design.md §1; derivation record:
   * docs/shipped-baseline-derivation.md). Two roles:
   *
   * (a) [defaultBands] seeds a new drill's editable per-metric reference bands
   *     (`CustomDrillEntity.perPhaseTargetsJson`) so a player never sees an empty editor or a
   *     hardcoded textbook figure.
   * (b) [FOREHAND_ANDRII] itself is the σ-carrier baseline `LiveDrillSession` uses in "standard"
   *     reference mode — so a qualitative-metric cue (`coil_ratio`, `stroke_speed`) ranks by
   *     Andrii's own variability instead of the crude
   *     [com.ttcoachai.shared.drill.DrillFeedbackEngine.DEFAULT_RANGE_SEVERITY_SCALE_DEGREES] fallback.
   *
   * LIMITATION (see docs/DESIGN_LIMITATIONS.md L-37, L-38): derived with the camera-yaw gate
   * consciously relaxed (`cameraYawDeg = 0f`) for this one-time editorial step — these bands
   * encode "match this recorded stroke as filmed," not a camera-agnostic universal norm; standard-
   * mode severity ranking likewise reflects Andrii's own consistency, not a player-agnostic scale.
   */
  object ShippedBaselines {

      val FOREHAND_ANDRII: PersonalBaseline = PersonalBaseline(
          drillType = "forehand_drive",
          metricStats = mapOf(
              DrillMetrics.METRIC_ELBOW_ANGLE to MetricStats(mean = 0.0, std = 0.0, min = 0.0, max = 0.0, sampleCount = 0), // REPLACE
              DrillMetrics.METRIC_SHOULDER_ANGLE to MetricStats(mean = 0.0, std = 0.0, min = 0.0, max = 0.0, sampleCount = 0), // REPLACE
              DrillMetrics.METRIC_KNEE_BEND to MetricStats(mean = 0.0, std = 0.0, min = 0.0, max = 0.0, sampleCount = 0), // REPLACE
              DrillMetrics.METRIC_TORSO_LEAN to MetricStats(mean = 0.0, std = 0.0, min = 0.0, max = 0.0, sampleCount = 0), // REPLACE
              DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D to MetricStats(mean = 0.0, std = 0.0, min = 0.0, max = 0.0, sampleCount = 0), // REPLACE
              DrillMetrics.METRIC_STROKE_SPEED to MetricStats(mean = 0.0, std = 0.0, min = 0.0, max = 0.0, sampleCount = 0), // REPLACE
              DrillMetrics.METRIC_COIL_RATIO to MetricStats(mean = 0.0, std = 0.0, min = 0.0, max = 0.0, sampleCount = 0), // REPLACE
          ),
          phaseDurationsMs = mapOf(
              // REPLACE with Task A's printed phaseDurationsMs entries (key -> MetricStats), e.g.:
              // "forward_swing_ms" to MetricStats(mean = 0.0, std = 0.0, min = 0.0, max = 0.0, sampleCount = 0),
              // "stroke_total_ms" to MetricStats(mean = 0.0, std = 0.0, min = 0.0, max = 0.0, sampleCount = 0),
          ),
          repCount = 0, // REPLACE with Task A's printed baseline.repCount
          excludedRepIndices = emptyList(), // REPLACE with Task A's printed baseline.excludedRepIndices
          qualityScore = 0.0, // REPLACE with Task A's printed baseline.qualityScore
          createdAtMs = 0L, // REPLACE with the createdAtMs candidate Task A printed — fixed, not System.currentTimeMillis()
          drillerHandedness = "right"
      )

      /**
       * `mean ± 2σ` per metric (rounded to 2 decimals — enough resolution for `coil_ratio`/
       * `stroke_speed`, whose values are typically < 10, while degree metrics still read cleanly).
       * Seeded verbatim into a new drill's `perPhaseTargetsJson` at creation time (Task F) — this
       * is NOT a runtime fallback re-applied to every empty editor field; a band a player
       * deliberately leaves blank stays silent (see `BaselineRuleFactory.applyRangeOverrides`).
       */
      fun defaultBands(): Map<String, ClosedRange<Double>> =
          DrillMetrics.ALL_KEYS.mapNotNull { key ->
              val stats = FOREHAND_ANDRII.metricStats[key] ?: return@mapNotNull null
              val spread = BaselineRuleFactory.DEFAULT_CONSISTENCY_K_SIGMA * stats.std
              key to round2(stats.mean - spread)..round2(stats.mean + spread)
          }.toMap()

      private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
  }
  ```

- [ ] **Step 4: Run the test to verify it passes**

  Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ShippedBaselinesTest"`
  Expected: PASS once real numbers are pasted in (with placeholder zeros it will fail
  `baselineHasPlausibleHandednessAndRepCount`'s `repCount > 0` and the all-keys/band tests — that's
  the point: it forces the real paste).

- [ ] **Step 5: Commit**

  ```bash
  git add shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ShippedBaselines.kt shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/ShippedBaselinesTest.kt
  git commit -m "$(cat <<'EOF'
  feat(baseline): add ShippedBaselines.FOREHAND_ANDRII + defaultBands()

  Curated, reproducible shipped baseline (see docs/shipped-baseline-derivation.md) that will
  back the no-calibration "standard" reference mode: defaultBands() seeds a new drill's
  editable bands, FOREHAND_ANDRII itself is the sigma-carrier for qualitative-cue severity.

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Task C — `PerPhaseTargetsCodec` key migration + decimal support + `encode()`

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/util/PerPhaseTargetsCodec.kt`
- Modify: `app/src/test/java/com/ttcoachai/util/PerPhaseTargetsCodecTest.kt`

**Interfaces:**
- Consumes: `com.ttcoachai.shared.drill.DrillMetrics.{METRIC_KNEE_BEND, METRIC_TORSO_LEAN}` (existing).
- Produces: `PerPhaseTargetsCodec.parse(json: String): Map<String, Pair<Float, Float>>` (same
  signature, new behavior: legacy phase-string keys remap to `DrillMetrics` keys; values decode
  as doubles-then-`Float`, not truncated ints) and a new
  `PerPhaseTargetsCodec.encode(bands: Map<String, Pair<Float, Float>>): String` (inverse of
  `parse`, empty map → `""`) — consumed by Task D (editor) and Task F (seeding).

Current behavior (re-read, confirmed): `parse()` truncates via `arr.optInt(0)`/`optInt(1)` and
returns values keyed by whatever literal string was in the JSON (e.g. `"knees · strike"`). This
task changes BOTH: values become decimal-capable, and the two keys the spec calls out remap to
their `DrillMetrics` key on the way out.

- [ ] **Step 1: Rewrite the test file first (TDD — these are the NEW expected semantics)**

  Replace `app/src/test/java/com/ttcoachai/util/PerPhaseTargetsCodecTest.kt` in full:

  ```kotlin
  package com.ttcoachai.util

  import com.ttcoachai.shared.drill.DrillMetrics
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertTrue
  import org.junit.Test

  /**
   * Pure JSON parsing tests for [PerPhaseTargetsCodec] — no Android dependency, plain JUnit.
   */
  class PerPhaseTargetsCodecTest {

      @Test
      fun blankJsonYieldsEmptyMap() {
          assertTrue(PerPhaseTargetsCodec.parse("").isEmpty())
          assertTrue(PerPhaseTargetsCodec.parse("   ").isEmpty())
      }

      @Test
      fun malformedJsonYieldsEmptyMap() {
          assertTrue(PerPhaseTargetsCodec.parse("{not json").isEmpty())
      }

      @Test
      fun legacyKneesStrikeKeyMapsToKneeBendMetricKey() {
          val parsed = PerPhaseTargetsCodec.parse("""{"knees · strike":[110,130]}""")
          assertEquals(110f to 130f, parsed[DrillMetrics.METRIC_KNEE_BEND])
          assertTrue(PerPhaseTargetsCodec.KEY_KNEES_STRIKE !in parsed)
      }

      @Test
      fun legacyTorsoStrikeKeyMapsToTorsoLeanMetricKey() {
          val parsed = PerPhaseTargetsCodec.parse("""{"torso tilt · strike":[25,45]}""")
          assertEquals(25f to 45f, parsed[DrillMetrics.METRIC_TORSO_LEAN])
      }

      @Test
      fun newDirectMetricKeysDecodeUnchanged() {
          val parsed = PerPhaseTargetsCodec.parse(
              """{"elbow_angle":[35,70],"coil_ratio":[0.9,1.3]}"""
          )
          assertEquals(35f to 70f, parsed[DrillMetrics.METRIC_ELBOW_ANGLE])
          assertEquals(0.9f to 1.3f, parsed[DrillMetrics.METRIC_COIL_RATIO])
      }

      @Test
      fun decimalValuesSurviveRoundTrip() {
          val encoded = PerPhaseTargetsCodec.encode(mapOf(DrillMetrics.METRIC_STROKE_SPEED to (3.25f to 6.8f)))
          val parsed = PerPhaseTargetsCodec.parse(encoded)
          assertEquals(3.25f to 6.8f, parsed[DrillMetrics.METRIC_STROKE_SPEED])
      }

      @Test
      fun ignoresArraysShorterThanTwoElements() {
          val parsed = PerPhaseTargetsCodec.parse("""{"knees · strike":[110]}""")
          assertTrue(parsed.isEmpty())
      }

      @Test
      fun unrelatedKeysPassThroughGenerically() {
          val parsed = PerPhaseTargetsCodec.parse("""{"elbow · backswing":[80,100]}""")
          assertEquals(80f to 100f, parsed["elbow · backswing"])
      }

      @Test
      fun encodeEmptyMapYieldsEmptyString() {
          assertEquals("", PerPhaseTargetsCodec.encode(emptyMap()))
      }

      @Test
      fun encodeThenParseRoundTripsMultipleKeys() {
          val bands = mapOf(
              DrillMetrics.METRIC_KNEE_BEND to (106f to 134f),
              DrillMetrics.METRIC_COIL_RATIO to (0.95f to 1.28f)
          )
          val parsed = PerPhaseTargetsCodec.parse(PerPhaseTargetsCodec.encode(bands))
          assertEquals(bands, parsed)
      }
  }
  ```

- [ ] **Step 2: Run to verify the new/changed tests fail against the current implementation**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.util.PerPhaseTargetsCodecTest"`
  Expected: FAIL (`legacyKneesStrikeKeyMapsToKneeBendMetricKey` etc. — no remapping yet; `encode`
  doesn't exist — compile error).

- [ ] **Step 3: Rewrite `PerPhaseTargetsCodec.kt`**

  ```kotlin
  package com.ttcoachai.util

  import com.ttcoachai.shared.drill.DrillMetrics
  import org.json.JSONArray
  import org.json.JSONObject

  /**
   * Pure JSON<->model codec for the custom-drill editor's per-metric target bands
   * (`CustomDrillEntity.perPhaseTargetsJson`). Keys are `DrillMetrics` metric keys
   * (`"elbow_angle"`, `"knee_bend"`, ...) as of the 7-row editor rework (docs/superpowers/specs/
   * 2026-07-27-no-calibration-shipped-baseline-design.md §4). [LEGACY_KEY_MAP] remaps the two
   * pre-rework phase-string keys that used to have a live binding so old community-shared JSON
   * blobs (`CommunityDrillRepository`/`CommunityDrillMapper` sync `perPhaseTargetsJson` verbatim)
   * keep decoding — see docs/DESIGN_LIMITATIONS.md L-40 for what an old blob does NOT recover.
   *
   * Values decode/encode as doubles-then-Float (not truncated ints) — `stroke_speed`
   * (torso-lengths/s) and `coil_ratio` need fractional resolution; the 5 degree metrics still
   * round-trip whole numbers fine since `round(intValue) == intValue`.
   */
  object PerPhaseTargetsCodec {

      /** Legacy-only: no longer produced by the editor, kept so old JSON keeps decoding. */
      const val KEY_KNEES_BACKSWING = "knees · backswing"
      const val KEY_KNEES_STRIKE = "knees · strike"
      const val KEY_TORSO_STRIKE = "torso tilt · strike"

      private val LEGACY_KEY_MAP: Map<String, String> = mapOf(
          KEY_KNEES_STRIKE to DrillMetrics.METRIC_KNEE_BEND,
          KEY_TORSO_STRIKE to DrillMetrics.METRIC_TORSO_LEAN,
      )

      /**
       * Parses [json] into a generic key -> (min, max) map. Unknown/malformed keys, blank
       * input, or unparseable JSON all yield an empty map (silent — same tolerance the
       * original inline decode had). A key present in [LEGACY_KEY_MAP] is remapped to its
       * DrillMetrics key on the way out; every other key (new-format DrillMetrics keys, or a
       * genuinely unrecognized key) passes through unchanged.
       */
      fun parse(json: String): Map<String, Pair<Float, Float>> {
          if (json.isBlank()) return emptyMap()
          val obj = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
          val result = mutableMapOf<String, Pair<Float, Float>>()
          val keys = obj.keys()
          while (keys.hasNext()) {
              val rawKey = keys.next()
              val arr = obj.optJSONArray(rawKey)?.takeIf { it.length() >= 2 } ?: continue
              val key = LEGACY_KEY_MAP[rawKey] ?: rawKey
              result[key] = arr.optDouble(0).toFloat() to arr.optDouble(1).toFloat()
          }
          return result
      }

      /** Inverse of [parse]'s value encoding (keys are written as-is — callers pass DrillMetrics
       *  keys directly, never a legacy phase string). Empty map encodes to `""`, matching how
       *  [parse] treats blank input. */
      fun encode(bands: Map<String, Pair<Float, Float>>): String {
          if (bands.isEmpty()) return ""
          val json = JSONObject()
          for ((key, pair) in bands) {
              json.put(key, JSONArray().put(pair.first.toDouble()).put(pair.second.toDouble()))
          }
          return json.toString()
      }
  }
  ```

- [ ] **Step 4: Run to verify the tests pass**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.util.PerPhaseTargetsCodecTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/ttcoachai/util/PerPhaseTargetsCodec.kt app/src/test/java/com/ttcoachai/util/PerPhaseTargetsCodecTest.kt
  git commit -m "$(cat <<'EOF'
  refactor(drills): PerPhaseTargetsCodec keys on DrillMetrics + decimal precision

  Per-phase-target JSON keys move from phase-string labels to DrillMetrics metric keys so the
  live path can do one generic all-keys band pass (Task H) instead of a single hardcoded
  knee-bend extraction. A legacy-key map keeps old community-shared blobs decoding. Values
  gain decimal precision for the two ratio/speed metrics that need it.

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Task D — Exercise editor: 7 rows bound to `DrillMetrics.ALL_KEYS`

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/ui/ExerciseEditorActivity.kt`
- Modify: `app/src/main/res/layout/activity_exercise_editor.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`

**Interfaces:**
- Consumes: `PerPhaseTargetsCodec.parse`/`encode` (Task C), `DrillMetrics.{METRIC_ELBOW_ANGLE,
  METRIC_SHOULDER_ANGLE, METRIC_KNEE_BEND, METRIC_TORSO_LEAN, METRIC_FOLLOW_THROUGH_ANGLE_2D,
  METRIC_STROKE_SPEED, METRIC_COIL_RATIO}` (existing), `BaselineHintBand.compute` (existing,
  unchanged — still returns a rounded `Pair<Int,Int>?`; the 2 qualitative rows' baseline-derived
  hint is therefore coarser than their persisted band precision — an accepted rough edge, not a
  functional defect, since the actual persisted/default bands keep full decimal precision).
- Produces: `ExerciseEditorActivity`'s `advancedRows` now has exactly 7 entries whose `jsonKey`
  IS the `DrillMetrics` key (no separate `personalizedHintMetrics` indirection — removed entirely).

Current XML has 10 rows; of those, only `knees · strike` (`et_knees_strike_from/to`),
`torso tilt · strike` (`et_torso_strike_from/to`), and `elbow · finish`
(`et_elbow_finish_from/to`) are **kept and rebound** (same view ids, same visible label, new
`jsonKey`). The other 7 (`elbow · backswing`, `shoulder · backswing`, `shoulder · finish`,
`knees · backswing`, `hips · backswing`, `hips · strike`, `torso · backswing`) are **deleted**.
4 brand-new rows are **added**: elbow · strike, shoulder · strike, stroke speed, body rotation.

- [ ] **Step 1: Update `values/strings.xml`** (English) — locate the `exercise_editor_row_*`
  block (currently lines ~1017-1035; re-`grep -n "exercise_editor_row_\|exercise_editor_col_"` to
  confirm current line numbers before editing) and replace it with:

  ```xml
  <string name="exercise_editor_col_metric">METRIC · PHASE</string>
  <string name="exercise_editor_col_from">FROM</string>
  <string name="exercise_editor_col_to">TO</string>
  <string name="exercise_editor_row_elbow_strike">Elbow · strike (°)</string>
  <string name="exercise_editor_row_shoulder_strike">Shoulder · strike (°)</string>
  <string name="exercise_editor_row_knees_strike">Knees · strike (°)</string>
  <string name="exercise_editor_row_torso_strike">Torso tilt · strike (°)</string>
  <string name="exercise_editor_row_elbow_finish">Elbow · finish (°)</string>
  <string name="exercise_editor_row_stroke_speed">Stroke speed (torso-lengths/s)</string>
  <string name="exercise_editor_row_body_rotation">Body rotation (ratio)</string>
  ```

  (This replaces the OLD `exercise_editor_col_from`/`_col_to` — dropping the literal `°` since 2
  of 7 rows aren't degrees — and the OLD 10 `exercise_editor_row_*` strings, of which
  `exercise_editor_row_elbow_finish`/`_knees_strike`/`_torso_strike` are kept-but-reworded with a
  `(°)` suffix for consistency with the new unit-agnostic column headers. `exercise_editor_row_elbow_backswing`,
  `_shoulder_backswing`, `_shoulder_finish`, `_knees_backswing`, `_hips_backswing`, `_hips_strike`,
  `_torso_backswing` are removed — grep-confirm zero remaining `@string/exercise_editor_row_elbow_backswing`
  etc. references in `res/layout/` after Step 2 below.)

- [ ] **Step 2: Update `values-uk/strings.xml`** (Ukrainian) — same block (currently lines
  ~819-835; re-grep to confirm), replace with:

  ```xml
  <string name="exercise_editor_col_metric">МЕТРИКА · ФАЗА</string>
  <string name="exercise_editor_col_from">ВІД</string>
  <string name="exercise_editor_col_to">ДО</string>
  <string name="exercise_editor_row_elbow_strike">Лікоть · удар (°)</string>
  <string name="exercise_editor_row_shoulder_strike">Плече · удар (°)</string>
  <string name="exercise_editor_row_knees_strike">Коліна · удар (°)</string>
  <string name="exercise_editor_row_torso_strike">Нахил корпусу · удар (°)</string>
  <string name="exercise_editor_row_elbow_finish">Лікоть · завершення (°)</string>
  <string name="exercise_editor_row_stroke_speed">Швидкість удару (довжин корпусу/с)</string>
  <string name="exercise_editor_row_body_rotation">Обертання корпусу (коефіцієнт)</string>
  ```

- [ ] **Step 3: Rework `activity_exercise_editor.xml`'s advanced-rows section**

  In the `container_advanced` `LinearLayout` (re-grep `container_advanced` to confirm current
  location), **delete** the 7 dead row blocks by their `<!-- Row: ... -->` comments: `elbow ·
  backswing`, `shoulder · backswing`, `shoulder · finish`, `knees · backswing`, `hips ·
  backswing`, `hips · strike`, `torso tilt · backswing` (each is one `<LinearLayout>` containing
  a label `TextView` + 2 `TextInputEditText`s — delete the whole block for each).

  **Keep unchanged** (view ids stay, only the string RESOURCE VALUES changed above, no XML edit
  needed): the `knees · strike` row (`et_knees_strike_from/to`), the `torso tilt · strike` row
  (`et_torso_strike_from/to`), and the `elbow · finish` row (`et_elbow_finish_from/to`).

  **Add** 4 new rows, following the exact template of the rows you kept (same styles/weights),
  inserted in this order — elbow · strike and shoulder · strike near the top (before knees ·
  strike), stroke speed and body rotation at the end (after torso tilt · strike):

  ```xml
  <!-- Row: elbow · strike -->
  <LinearLayout
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:layout_marginBottom="10dp"
      android:gravity="center_vertical"
      android:orientation="horizontal">

      <TextView
          android:layout_width="0dp"
          android:layout_height="wrap_content"
          android:layout_weight="2"
          android:text="@string/exercise_editor_row_elbow_strike"
          android:textAppearance="@style/TextAppearance.TTC.Body" />

      <com.google.android.material.textfield.TextInputEditText
          android:id="@+id/et_elbow_strike_from"
          android:layout_width="0dp"
          android:layout_height="40dp"
          android:layout_marginEnd="6dp"
          android:layout_weight="1"
          android:background="@drawable/bg_pill_track"
          android:gravity="center"
          android:hint="35"
          android:inputType="number"
          android:paddingHorizontal="4dp"
          android:textAppearance="@style/TextAppearance.TTC.Mono.Meta"
          android:textColor="@color/ttc_text_1" />

      <com.google.android.material.textfield.TextInputEditText
          android:id="@+id/et_elbow_strike_to"
          android:layout_width="0dp"
          android:layout_height="40dp"
          android:layout_weight="1"
          android:background="@drawable/bg_pill_track"
          android:gravity="center"
          android:hint="70"
          android:inputType="number"
          android:paddingHorizontal="4dp"
          android:textAppearance="@style/TextAppearance.TTC.Mono.Meta"
          android:textColor="@color/ttc_text_1" />
  </LinearLayout>

  <!-- Row: shoulder · strike -->
  <LinearLayout
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:layout_marginBottom="10dp"
      android:gravity="center_vertical"
      android:orientation="horizontal">

      <TextView
          android:layout_width="0dp"
          android:layout_height="wrap_content"
          android:layout_weight="2"
          android:text="@string/exercise_editor_row_shoulder_strike"
          android:textAppearance="@style/TextAppearance.TTC.Body" />

      <com.google.android.material.textfield.TextInputEditText
          android:id="@+id/et_shoulder_strike_from"
          android:layout_width="0dp"
          android:layout_height="40dp"
          android:layout_marginEnd="6dp"
          android:layout_weight="1"
          android:background="@drawable/bg_pill_track"
          android:gravity="center"
          android:hint="20"
          android:inputType="number"
          android:paddingHorizontal="4dp"
          android:textAppearance="@style/TextAppearance.TTC.Mono.Meta"
          android:textColor="@color/ttc_text_1" />

      <com.google.android.material.textfield.TextInputEditText
          android:id="@+id/et_shoulder_strike_to"
          android:layout_width="0dp"
          android:layout_height="40dp"
          android:layout_weight="1"
          android:background="@drawable/bg_pill_track"
          android:gravity="center"
          android:hint="45"
          android:inputType="number"
          android:paddingHorizontal="4dp"
          android:textAppearance="@style/TextAppearance.TTC.Mono.Meta"
          android:textColor="@color/ttc_text_1" />
  </LinearLayout>
  ```

  ...then, AFTER the (kept, unedited) `torso tilt · strike` row's closing `</LinearLayout>`:

  ```xml
  <!-- Row: stroke speed -->
  <LinearLayout
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:layout_marginBottom="10dp"
      android:gravity="center_vertical"
      android:orientation="horizontal">

      <TextView
          android:layout_width="0dp"
          android:layout_height="wrap_content"
          android:layout_weight="2"
          android:text="@string/exercise_editor_row_stroke_speed"
          android:textAppearance="@style/TextAppearance.TTC.Body" />

      <com.google.android.material.textfield.TextInputEditText
          android:id="@+id/et_stroke_speed_from"
          android:layout_width="0dp"
          android:layout_height="40dp"
          android:layout_marginEnd="6dp"
          android:layout_weight="1"
          android:background="@drawable/bg_pill_track"
          android:gravity="center"
          android:hint="3"
          android:inputType="numberDecimal"
          android:paddingHorizontal="4dp"
          android:textAppearance="@style/TextAppearance.TTC.Mono.Meta"
          android:textColor="@color/ttc_text_1" />

      <com.google.android.material.textfield.TextInputEditText
          android:id="@+id/et_stroke_speed_to"
          android:layout_width="0dp"
          android:layout_height="40dp"
          android:layout_weight="1"
          android:background="@drawable/bg_pill_track"
          android:gravity="center"
          android:hint="7"
          android:inputType="numberDecimal"
          android:paddingHorizontal="4dp"
          android:textAppearance="@style/TextAppearance.TTC.Mono.Meta"
          android:textColor="@color/ttc_text_1" />
  </LinearLayout>

  <!-- Row: body rotation -->
  <LinearLayout
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:layout_marginBottom="4dp"
      android:gravity="center_vertical"
      android:orientation="horizontal">

      <TextView
          android:layout_width="0dp"
          android:layout_height="wrap_content"
          android:layout_weight="2"
          android:text="@string/exercise_editor_row_body_rotation"
          android:textAppearance="@style/TextAppearance.TTC.Body" />

      <com.google.android.material.textfield.TextInputEditText
          android:id="@+id/et_body_rotation_from"
          android:layout_width="0dp"
          android:layout_height="40dp"
          android:layout_marginEnd="6dp"
          android:layout_weight="1"
          android:background="@drawable/bg_pill_track"
          android:gravity="center"
          android:hint="0.9"
          android:inputType="numberDecimal"
          android:paddingHorizontal="4dp"
          android:textAppearance="@style/TextAppearance.TTC.Mono.Meta"
          android:textColor="@color/ttc_text_1" />

      <com.google.android.material.textfield.TextInputEditText
          android:id="@+id/et_body_rotation_to"
          android:layout_width="0dp"
          android:layout_height="40dp"
          android:layout_weight="1"
          android:background="@drawable/bg_pill_track"
          android:gravity="center"
          android:hint="1.4"
          android:inputType="numberDecimal"
          android:paddingHorizontal="4dp"
          android:textAppearance="@style/TextAppearance.TTC.Mono.Meta"
          android:textColor="@color/ttc_text_1" />
  </LinearLayout>
  ```

  Make sure the `torso tilt · strike` row's `layout_marginBottom` reverts to `10dp` (it was `4dp`
  as the previously-last row) now that `body rotation` is the new last row with `4dp`.

- [ ] **Step 4: Rewrite `ExerciseEditorActivity.kt`'s advanced-rows machinery**

  Remove the `personalizedHintMetrics` map entirely, and replace `advancedRows`:

  ```kotlin
  private val advancedRows: List<AdvancedRow> by lazy {
      listOf(
          AdvancedRow(R.id.et_elbow_strike_from, R.id.et_elbow_strike_to, DrillMetrics.METRIC_ELBOW_ANGLE),
          AdvancedRow(R.id.et_shoulder_strike_from, R.id.et_shoulder_strike_to, DrillMetrics.METRIC_SHOULDER_ANGLE),
          AdvancedRow(R.id.et_knees_strike_from, R.id.et_knees_strike_to, DrillMetrics.METRIC_KNEE_BEND),
          AdvancedRow(R.id.et_torso_strike_from, R.id.et_torso_strike_to, DrillMetrics.METRIC_TORSO_LEAN),
          AdvancedRow(R.id.et_elbow_finish_from, R.id.et_elbow_finish_to, DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D),
          AdvancedRow(R.id.et_stroke_speed_from, R.id.et_stroke_speed_to, DrillMetrics.METRIC_STROKE_SPEED),
          AdvancedRow(R.id.et_body_rotation_from, R.id.et_body_rotation_to, DrillMetrics.METRIC_COIL_RATIO),
      )
  }
  ```

  (`AdvancedRow`'s own declaration — `data class AdvancedRow(val fromId: Int, val toId: Int, val
  jsonKey: String)` — is unchanged; `jsonKey` now always holds a `DrillMetrics.*` constant.)

  Replace `loadBaselineHints()`'s row loop (drop the `personalizedHintMetrics` lookup — every row
  now has a directly-bindable metric key):

  ```kotlin
  for (row in advancedRows) {
      val band = BaselineHintBand.compute(baseline.metricStats[row.jsonKey]) ?: continue
      findViewById<android.widget.EditText>(row.fromId).hint = band.first.toString()
      findViewById<android.widget.EditText>(row.toId).hint = band.second.toString()
  }
  ```

  Replace `encodeAdvancedTargets()` (decimal-capable, delegates to the codec):

  ```kotlin
  private fun encodeAdvancedTargets(): String {
      val bands = mutableMapOf<String, Pair<Float, Float>>()
      for (row in advancedRows) {
          val fromText = findViewById<android.widget.EditText>(row.fromId).text?.toString()?.trim().orEmpty()
          val toText = findViewById<android.widget.EditText>(row.toId).text?.toString()?.trim().orEmpty()
          if (fromText.isEmpty() || toText.isEmpty()) continue
          val from = fromText.toFloatOrNull() ?: continue
          val to = toText.toFloatOrNull() ?: continue
          bands[row.jsonKey] = from to to
      }
      return com.ttcoachai.util.PerPhaseTargetsCodec.encode(bands)
  }
  ```

  Replace `decodeAdvancedTargets()` (formats whole numbers without a trailing `.0`, decimals with
  their fraction kept):

  ```kotlin
  private fun decodeAdvancedTargets(perPhaseTargetsJson: String) {
      for (row in advancedRows) {
          findViewById<android.widget.EditText>(row.fromId).setText("")
          findViewById<android.widget.EditText>(row.toId).setText("")
      }
      val parsed = com.ttcoachai.util.PerPhaseTargetsCodec.parse(perPhaseTargetsJson)
      for (row in advancedRows) {
          val (from, to) = parsed[row.jsonKey] ?: continue
          findViewById<android.widget.EditText>(row.fromId).setText(formatBandValue(from))
          findViewById<android.widget.EditText>(row.toId).setText(formatBandValue(to))
      }
  }

  private fun formatBandValue(value: Float): String =
      if (value == kotlin.math.floor(value)) value.toInt().toString() else value.toString()
  ```

- [ ] **Step 5: Build gate**

  Run: `./gradlew :app:assembleDebug`
  Expected: SUCCESS. If it fails on a missing `R.id.et_*` or `R.string.exercise_editor_row_*`
  reference, grep-confirm you deleted/added the matching XML/strings entries in Steps 1-3.

- [ ] **Step 6: Manual verification (no Robolectric harness in this repo for Activity UI wiring
  — see the 2026-07-24 plan's precedent for why this stays manual)**

  On device (`run-on-phone` skill): open Drills → FAB "Add Drill" → expand "Advanced — per-phase
  targets". Confirm exactly 7 rows show, in order: Elbow · strike, Shoulder · strike, Knees ·
  strike, Torso tilt · strike, Elbow · finish, Stroke speed, Body rotation — each with a unit
  label matching its metric. Type a decimal into Stroke speed's FROM field (e.g. `3.5`), save,
  re-open the same drill for edit, confirm `3.5` round-trips (not `3` or `3.0`).

- [ ] **Step 7: Commit**

  ```bash
  git add app/src/main/java/com/ttcoachai/ui/ExerciseEditorActivity.kt app/src/main/res/layout/activity_exercise_editor.xml app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml
  git commit -m "$(cat <<'EOF'
  feat(editor): rework advanced targets to the 7 live DrillMetrics rows

  The old 10-row advanced-targets section only ever wired 2 rows (knees/torso · strike) to a
  live metric; the other 8 were silent placeholders with no phase-segmentation to back them.
  Replace with exactly the 7 rows matching DrillMetrics.ALL_KEYS, each editable with a
  unit-appropriate label (5 precise degrees, stroke speed in torso-lengths/s, body rotation
  as a ratio) in EN + UA.

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Task E — `CustomDrillEntity.movementProfile` column + schema bump

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/models/CustomDrillEntity.kt`
- Modify: `app/src/main/java/com/ttcoachai/db/AppDatabase.kt`
- Modify: `app/src/test/java/com/ttcoachai/util/CommunityDrillCopierTest.kt`

**Interfaces:**
- Produces: `CustomDrillEntity.movementProfile: String?` (default `null`, trailing param — every
  existing call site keeps compiling unchanged). `AppDatabase.version == 10`. Consumed by Task F
  (seeded General row sets it to `"general"`) and Task H (`TrainingActivity` reads it via the new
  `MOVEMENT_PROFILE` intent extra).
- **Not** produced: no change to `CommunityDrillMapper`/`CommunityDrill` — `movementProfile` is
  local-only, following the exact precedent already set by `drillType`/`baselineId` (both already
  excluded from `CommunityDrillMapper.fromCustomDrill`/`toMap`). A community-drill copy always
  gets `movementProfile = null` because `CommunityDrillMapper.toCustomDrillEntity` doesn't set it
  and the new field defaults to `null`.

- [ ] **Step 1: Add the failing assertion to the existing copier test first**

  In `app/src/test/java/com/ttcoachai/util/CommunityDrillCopierTest.kt`, add one line inside
  `copyToLocal saves a fresh unlinked entity and returns it` right after the existing
  `assertNull(saved?.sharedCommunityId)`:

  ```kotlin
  assertNull(saved?.movementProfile)
  ```

  Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.util.CommunityDrillCopierTest"`
  Expected: FAIL (compile error — `CustomDrillEntity` has no `movementProfile` member yet).

- [ ] **Step 2: Add the column**

  In `app/src/main/java/com/ttcoachai/models/CustomDrillEntity.kt`, add a trailing field:

  ```kotlin
  @Entity(tableName = "custom_drills")
  data class CustomDrillEntity(
      @PrimaryKey val drillType: String,
      val name: String,
      val baseTemplate: String,
      val createdAtMs: Long,
      val focusCsv: String = "",
      val referenceType: String = "standard",
      val baselineId: Long? = null,
      val strictnessX: Float = 1.0f,
      val perPhaseTargetsJson: String = "",
      val sharedCommunityId: String? = null,
      /** "general" for the widened-locomotion-tolerance profile (see
       *  ForehandDriveGeneral.MOVEMENT_TOLERANT_HIP_TRAVEL), null for the default structured
       *  tolerance. Editor does not expose this in v1 — seed-only (Task F). Deliberately excluded
       *  from CommunityDrillMapper (never syncs to Firestore), same precedent as drillType/baselineId. */
      val movementProfile: String? = null
  )
  ```

- [ ] **Step 3: Bump the database version**

  In `app/src/main/java/com/ttcoachai/db/AppDatabase.kt`, change `version = 9` to `version = 10`
  (the `@Database(...)` annotation's `entities` list is unchanged — `CustomDrillEntity` is already
  listed; only its own schema changed). `fallbackToDestructiveMigration()` stays — repo norm,
  wipes local data on this bump (same as every prior schema change here).

- [ ] **Step 4: Run to verify the test passes**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.util.CommunityDrillCopierTest"`
  Expected: PASS.

- [ ] **Step 5: Build gate**

  Run: `./gradlew :app:assembleDebug`
  Expected: SUCCESS.

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/ttcoachai/models/CustomDrillEntity.kt app/src/main/java/com/ttcoachai/db/AppDatabase.kt app/src/test/java/com/ttcoachai/util/CommunityDrillCopierTest.kt
  git commit -m "$(cat <<'EOF'
  feat(drills): add CustomDrillEntity.movementProfile column (schema v10)

  Carries the General drill's widened locomotion tolerance by column, not an id-string match,
  so it survives renaming/cloning. Local-only like drillType/baselineId — never synced to the
  community Firestore collection. fallbackToDestructiveMigration() wipes local data on this
  bump per repo norm.

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Task F — Seed two `CustomDrillEntity` rows, replace the 3 hardcoded forehand drills

**Files:**
- Create: `app/src/main/java/com/ttcoachai/util/SeededDrillsPolicy.kt`
- Create: `app/src/test/java/com/ttcoachai/util/SeededDrillsPolicyTest.kt`
- Modify: `app/src/main/java/com/ttcoachai/TTCoachApplication.kt`
- Modify: `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`

**Interfaces:**
- Consumes: `ShippedBaselines.defaultBands()` (Task B), `PerPhaseTargetsCodec.encode` (Task C),
  `CustomDrillRepository.{get, save, count}` (existing), `CustomDrillEntity` (Task E's new field).
- Produces: `SeededDrillsPolicy.SEED_ANDRII_ID = "custom_seed_forehand_andrii"`,
  `SeededDrillsPolicy.SEED_GENERAL_ID = "custom_seed_forehand_general"`,
  `SeededDrillsPolicy.shouldSeed(flagAlreadySet: Boolean, existingDrillCount: Int): Boolean`,
  `SeededDrillsPolicy.SeedNames(andriiName: String, generalName: String)`,
  `SeededDrillsPolicy.buildSeedEntities(names: SeedNames, nowMs: Long, perPhaseTargetsJson:
  String): List<CustomDrillEntity>`, `SeededDrillsPolicy.seedMissing(repo:
  CustomDrillRepository, entities: List<CustomDrillEntity>)` (suspend) — consumed by
  `TTCoachApplication.onCreate()`. `DrillsFragment.setupData()` drops `forehand_drive`,
  `forehand_andrii`, `forehand_drive_general` from `builtInExercises`.

- [ ] **Step 1: Write the failing test first**

  Create `app/src/test/java/com/ttcoachai/util/SeededDrillsPolicyTest.kt`:

  ```kotlin
  package com.ttcoachai.util

  import com.ttcoachai.db.CustomDrillDao
  import com.ttcoachai.models.CustomDrillEntity
  import com.ttcoachai.repository.CustomDrillRepository
  import kotlinx.coroutines.runBlocking
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertNull
  import org.junit.Assert.assertTrue
  import org.junit.Test

  class SeededDrillsPolicyTest {

      private class FakeCustomDrillDao : CustomDrillDao {
          val rows = mutableMapOf<String, CustomDrillEntity>()

          override suspend fun upsert(entity: CustomDrillEntity) {
              rows[entity.drillType] = entity
          }

          override suspend fun getAll(): List<CustomDrillEntity> = rows.values.toList()

          override suspend fun getByDrillType(drillType: String): CustomDrillEntity? = rows[drillType]

          override suspend fun count(): Int = rows.size

          override suspend fun deleteByDrillType(drillType: String) {
              rows.remove(drillType)
          }

          override suspend fun getBySharedCommunityId(communityId: String): CustomDrillEntity? =
              rows.values.firstOrNull { it.sharedCommunityId == communityId }
      }

      private val names = SeededDrillsPolicy.SeedNames(
          andriiName = "Forehand Andrii",
          generalName = "Forehand Drive General"
      )

      @Test
      fun shouldSeedWhenFlagUnsetAndTableEmpty() {
          assertTrue(SeededDrillsPolicy.shouldSeed(flagAlreadySet = false, existingDrillCount = 0))
      }

      @Test
      fun shouldNotSeedWhenFlagSetAndDrillsPresent() {
          assertFalse(SeededDrillsPolicy.shouldSeed(flagAlreadySet = true, existingDrillCount = 2))
      }

      @Test
      fun shouldReSeedWhenFlagSetButTableWiped() {
          assertTrue(SeededDrillsPolicy.shouldSeed(flagAlreadySet = true, existingDrillCount = 0))
      }

      @Test
      fun buildSeedEntitiesReturnsBothRowsWithExpectedIdsAndMovementProfile() {
          val entities = SeededDrillsPolicy.buildSeedEntities(names, nowMs = 1000L, perPhaseTargetsJson = "{}")
          assertEquals(2, entities.size)
          val andrii = entities.first { it.drillType == SeededDrillsPolicy.SEED_ANDRII_ID }
          val general = entities.first { it.drillType == SeededDrillsPolicy.SEED_GENERAL_ID }
          assertEquals("Forehand Andrii", andrii.name)
          assertEquals("standard", andrii.referenceType)
          assertNull(andrii.movementProfile)
          assertEquals("Forehand Drive General", general.name)
          assertEquals("standard", general.referenceType)
          assertEquals("general", general.movementProfile)
          assertEquals("{}", andrii.perPhaseTargetsJson)
          assertEquals("{}", general.perPhaseTargetsJson)
      }

      @Test
      fun seedMissingInsertsOnlyAbsentRows() = runBlocking {
          val dao = FakeCustomDrillDao()
          val repo = CustomDrillRepository(dao)
          val editedAndrii = CustomDrillEntity(
              drillType = SeededDrillsPolicy.SEED_ANDRII_ID,
              name = "My renamed drill",
              baseTemplate = SeededDrillsPolicy.SEED_ANDRII_ID,
              createdAtMs = 1L,
              referenceType = "standard",
              perPhaseTargetsJson = "custom edits"
          )
          dao.upsert(editedAndrii)

          val entities = SeededDrillsPolicy.buildSeedEntities(names, nowMs = 2000L, perPhaseTargetsJson = "{}")
          SeededDrillsPolicy.seedMissing(repo, entities)

          // Edited row must survive untouched.
          assertEquals("My renamed drill", dao.rows.getValue(SeededDrillsPolicy.SEED_ANDRII_ID).name)
          // Missing row must have been inserted.
          assertTrue(SeededDrillsPolicy.SEED_GENERAL_ID in dao.rows)
      }
  }
  ```

- [ ] **Step 2: Run to verify it fails**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.util.SeededDrillsPolicyTest"`
  Expected: FAIL (compile error — `SeededDrillsPolicy` doesn't exist).

- [ ] **Step 3: Write `SeededDrillsPolicy.kt`**

  ```kotlin
  package com.ttcoachai.util

  import com.ttcoachai.models.CustomDrillEntity
  import com.ttcoachai.repository.CustomDrillRepository

  /**
   * Seeds two editable CustomDrillEntity rows on first run (and after any destructive-migration
   * DB wipe) so the app ships with trainable forehand drills instead of the old hardcoded
   * unlocked Exercise entries (docs/superpowers/specs/2026-07-27-no-calibration-shipped-baseline-design.md
   * §2). Pure decision + entity-construction logic; the Android-specific bits (flag storage,
   * localized name resolution) live at the call site (TTCoachApplication.onCreate()).
   */
  object SeededDrillsPolicy {

      const val SEED_ANDRII_ID = "custom_seed_forehand_andrii"
      const val SEED_GENERAL_ID = "custom_seed_forehand_general"

      data class SeedNames(val andriiName: String, val generalName: String)

      /**
       * Seeding runs when EITHER the "has ever seeded" flag is unset (first-ever launch) OR
       * [existingDrillCount] is 0 (a destructive-migration DB wipe clears Room's custom_drills
       * table but not SharedPreferences, so the flag alone would never re-seed after a wipe).
       * KNOWN LIMITATION (docs/DESIGN_LIMITATIONS.md L-39): this can't distinguish "wiped by
       * migration" from "player deliberately deleted every custom drill" — the latter also
       * re-triggers seeding on next launch.
       */
      fun shouldSeed(flagAlreadySet: Boolean, existingDrillCount: Int): Boolean =
          !flagAlreadySet || existingDrillCount == 0

      /**
       * Both seed rows: `referenceType = "standard"` (no calibration gate — the whole point of
       * this spec) and the SAME [perPhaseTargetsJson] (encoded `ShippedBaselines.defaultBands()`,
       * per Task B/C) — General differs ONLY by [SEED_GENERAL_ID]'s `movementProfile = "general"`
       * (Task G/H reads this to widen the locomotion gate). `baseTemplate` is self-referential
       * (own drillType), matching how every other NEW-mode custom drill is created
       * (ExerciseEditorActivity.onPrimaryClicked, EditorMode.NEW branch).
       */
      fun buildSeedEntities(
          names: SeedNames,
          nowMs: Long,
          perPhaseTargetsJson: String
      ): List<CustomDrillEntity> = listOf(
          CustomDrillEntity(
              drillType = SEED_ANDRII_ID,
              name = names.andriiName,
              baseTemplate = SEED_ANDRII_ID,
              createdAtMs = nowMs,
              referenceType = "standard",
              perPhaseTargetsJson = perPhaseTargetsJson,
          ),
          CustomDrillEntity(
              drillType = SEED_GENERAL_ID,
              name = names.generalName,
              baseTemplate = SEED_GENERAL_ID,
              createdAtMs = nowMs,
              referenceType = "standard",
              perPhaseTargetsJson = perPhaseTargetsJson,
              movementProfile = "general",
          ),
      )

      /**
       * Idempotent insert: writes only the [entities] whose `drillType` has no existing row —
       * `CustomDrillDao.upsert` is `OnConflictStrategy.REPLACE`, so calling it unconditionally
       * would silently clobber a player's edits (rename, re-banded targets) on every app start.
       */
      suspend fun seedMissing(repo: CustomDrillRepository, entities: List<CustomDrillEntity>) {
          for (entity in entities) {
              if (repo.get(entity.drillType) == null) {
                  repo.save(entity)
              }
          }
      }
  }
  ```

- [ ] **Step 4: Run to verify it passes**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.util.SeededDrillsPolicyTest"`
  Expected: PASS.

- [ ] **Step 5: Add the seeded-flag getter/setter to `SettingsManager`**

  In `app/src/main/java/com/ttcoachai/managers/SettingsManager.kt`, add (near the other boolean
  flags, e.g. after `isPoseUploadEnabled`/`setPoseUploadEnabled`):

  ```kotlin
  // Seeded built-in drills (docs/superpowers/specs/2026-07-27-no-calibration-shipped-baseline-design.md §2)
  fun isDrillsSeeded(): Boolean = prefs.getBoolean("seeded_drills_v1", false)
  fun setDrillsSeeded(seeded: Boolean) = prefs.edit().putBoolean("seeded_drills_v1", seeded).apply()
  ```

- [ ] **Step 6: Wire seeding into `TTCoachApplication.onCreate()`**

  In `app/src/main/java/com/ttcoachai/TTCoachApplication.kt`, add inside `onCreate()`, after the
  existing `AppCompatDelegate.setDefaultNightMode(...)` line and before the `cloudSyncManager.initialize(...)`
  block (seeding needs no auth state, same rationale as the pose-upload cache eviction call
  already there — runs unconditionally, off the main thread):

  ```kotlin
  // Seed built-in forehand drills as editable custom drills (docs/superpowers/specs/
  // 2026-07-27-no-calibration-shipped-baseline-design.md §2) — first run, or after any
  // destructive-migration DB wipe. Runs on applicationScope (IO) since it touches Room.
  applicationScope.launch(Dispatchers.IO) {
      val repo = com.ttcoachai.repository.CustomDrillRepository(database.customDrillDao())
      val flagAlreadySet = settingsManager.isDrillsSeeded()
      val existingCount = repo.count()
      if (com.ttcoachai.util.SeededDrillsPolicy.shouldSeed(flagAlreadySet, existingCount)) {
          val names = com.ttcoachai.util.SeededDrillsPolicy.SeedNames(
              andriiName = getString(R.string.exercise_forehand_andrii_name),
              generalName = getString(R.string.exercise_forehand_general_name)
          )
          val bands = com.ttcoachai.shared.drill.ShippedBaselines.defaultBands()
              .mapValues { (_, range) -> range.start.toFloat() to range.endInclusive.toFloat() }
          val targetsJson = com.ttcoachai.util.PerPhaseTargetsCodec.encode(bands)
          com.ttcoachai.util.SeededDrillsPolicy.seedMissing(
              repo,
              com.ttcoachai.util.SeededDrillsPolicy.buildSeedEntities(names, System.currentTimeMillis(), targetsJson)
          )
          settingsManager.setDrillsSeeded(true)
      }
  }
  ```

  (Reuses the existing `exercise_forehand_andrii_name`/`exercise_forehand_general_name` string
  resources verbatim — "Forehand Andrii" / "Накат справа (Андрій)" and "Forehand Drive General" /
  "Накат справа General" — already present in both `values/strings.xml` and `values-uk/strings.xml`,
  no new strings needed. Note: since `CustomDrillEntity.name` is a plain persisted string, not a
  string-resource reference, the seeded name is fixed at whatever interface language was active at
  the moment of first-ever seeding — it will not re-localize later if the player switches
  languages, same as any user-typed custom drill name.)

- [ ] **Step 7: Remove the 3 hardcoded unlocked forehand rows from `DrillsFragment.setupData()`**

  In `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`, delete the three `Exercise(id =
  "forehand_drive", ...)`, `Exercise(id = "forehand_andrii", ...)`, `Exercise(id =
  "forehand_drive_general", ...)` entries from the `builtInExercises = listOf(...)` block — keep
  every locked entry (`backhand_loop`, `serve_practice`, `footwork_drill`, `multiball_rally`,
  `consistency_challenge`) exactly as-is.

- [ ] **Step 8: Build gate**

  Run: `./gradlew :app:assembleDebug`
  Expected: SUCCESS.

- [ ] **Step 9: Device smoke (fresh install or clear app data)**

  Install fresh (or `adb shell pm clear com.ttcoachai` to simulate first run — this also exercises
  the destructive-migration re-seed path since it wipes Room too). Open Drills tab: confirm
  "Форхенд (Андрій)"/"Forehand Andrii" and "Forehand Drive General" appear as ordinary custom
  drills (editable via long-press → Edit, deletable, clonable) alongside the still-locked programs.
  No hardcoded hardcoded forehand entries remain.

- [ ] **Step 10: Commit**

  ```bash
  git add app/src/main/java/com/ttcoachai/util/SeededDrillsPolicy.kt app/src/test/java/com/ttcoachai/util/SeededDrillsPolicyTest.kt app/src/main/java/com/ttcoachai/managers/SettingsManager.kt app/src/main/java/com/ttcoachai/TTCoachApplication.kt app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt
  git commit -m "$(cat <<'EOF'
  feat(drills): seed forehand drills as editable custom drills, drop hardcoded rows

  Replaces the 3 hardcoded unlocked forehand Exercise entries with 2 idempotently-seeded
  CustomDrillEntity rows (custom_seed_forehand_andrii, custom_seed_forehand_general) whose
  bands come from ShippedBaselines.defaultBands() — one code path (editor, DrillActions,
  Community Drills) instead of a parallel hardcoded-vs-custom split, and bands become
  editable for free. Seeding is idempotent (check-before-write) so an edited seed row is
  never clobbered, and re-runs after a destructive-migration DB wipe.

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Task G — Plumb `hipTravelMaxTorso` through `RtmposeTrainingController`

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt`

**Interfaces:**
- Consumes: `com.ttcoachai.shared.drill.LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO` (existing),
  `com.ttcoachai.shared.analysis.BaselineRule`, `com.ttcoachai.shared.analysis.BaselineRuleFactory`
  (existing).
- Produces: `RtmposeTrainingController`'s constructor gains 2 new parameters (both defaulted, so
  the sole caller — `TrainingActivity` — is NOT required to change until Task H):
  `rules: List<BaselineRule> = BaselineRuleFactory.defaultRules(baseline)` and
  `hipTravelMaxTorso: Float = LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO`, both threaded into
  `ensureSession()`'s `LiveDrillSession(...)` construction.

This task is plumbing-only — it does not change behavior yet (defaults reproduce today's exact
behavior bit-for-bit). Task H is what actually passes non-default values.

- [ ] **Step 1: Add the 2 imports**

  In `app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt`, add alongside the
  existing `com.ttcoachai.shared.drill.*` imports:

  ```kotlin
  import com.ttcoachai.shared.analysis.BaselineRule
  import com.ttcoachai.shared.analysis.BaselineRuleFactory
  import com.ttcoachai.shared.drill.LocomotionFilter
  ```

- [ ] **Step 2: Add the constructor parameters**

  Insert `rules` and `hipTravelMaxTorso` right after `baseline` and before `onUiUpdate` (order
  doesn't strictly matter since all call sites use named arguments, but keeping it near `baseline`
  reads naturally — `rules`'s default expression references `baseline`, which Kotlin allows since
  it's declared earlier in the same primary constructor):

  ```kotlin
  class RtmposeTrainingController(
      private val activity: FragmentActivity,
      private val container: ViewGroup,
      private val stateManager: TrainingStateManager,
      private val settingsManager: SettingsManager,
      private val baseline: PersonalBaseline,
      /** Starting rule set BEFORE metricBands overlay (see LiveDrillSession.metricBands kdoc) —
       *  defaults to today's exact behavior (baseline-derived consistency rules). Task H passes
       *  emptyList() explicitly for "standard" reference mode so ONLY the drill's configured
       *  bands produce cues. */
      private val rules: List<BaselineRule> = BaselineRuleFactory.defaultRules(baseline),
      /** Locomotion gate tolerance in torso-lengths (LiveDrillSession/LocomotionFilter). Task H
       *  passes ForehandDriveGeneral.MOVEMENT_TOLERANT_HIP_TRAVEL for the General movement
       *  profile, the default otherwise. */
      private val hipTravelMaxTorso: Float = LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO,
      private val onUiUpdate: () -> Unit,
      private val metricBands: Map<String, ClosedRange<Double>> = emptyMap(),
  ) {
  ```

  **Note:** `onUiUpdate: () -> Unit` has no default value, so it must stay BEFORE any parameter
  that also lacks one, OR — simpler and matching Kotlin's actual rule (a parameter without a
  default can follow parameters that DO have defaults, as long as callers use named arguments,
  which `TrainingActivity`'s only call site already does) — leave `onUiUpdate` exactly where it
  already is (before `metricBands`) and insert `rules`/`hipTravelMaxTorso` between `baseline` and
  `onUiUpdate` as shown above. Verify with the build gate in Step 4.

- [ ] **Step 3: Thread both into `ensureSession()`**

  In `ensureSession()`, change the `LiveDrillSession(...)` construction to:

  ```kotlin
  current = LiveDrillSession(
      baseline = baseline,
      aspectRatio = aspectRatio,
      rules = rules,
      handedness = handedness(),
      lang = coachLang(),
      cameraYawDeg = 0f,
      hipTravelMaxTorso = hipTravelMaxTorso,
      metricBands = metricBands
  )
  ```

- [ ] **Step 4: Build gate**

  Run: `./gradlew :app:assembleDebug`
  Expected: SUCCESS — `TrainingActivity.startRtmController`'s existing call (all named arguments,
  no `rules`/`hipTravelMaxTorso` passed yet) must still compile unchanged, picking up the defaults.

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt
  git commit -m "$(cat <<'EOF'
  refactor(rtm): plumb rules + hipTravelMaxTorso through RtmposeTrainingController

  Both new constructor params default to today's exact behavior (defaultRules(baseline),
  LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO) so this is a no-op until Task H starts passing
  non-default values for the no-calibration standard mode and the General movement profile.

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Task H — `referenceType` consulted; generic all-keys metric bands

The core of the spec: `TrainingActivity` reads `REFERENCE_TYPE`/`MOVEMENT_PROFILE` intent extras,
never gates in "standard" mode, and passes ALL configured bands (not just knee-bend) through.

**Files:**
- Create: `app/src/main/java/com/ttcoachai/util/DrillReferenceResolver.kt`
- Create: `app/src/test/java/com/ttcoachai/util/DrillReferenceResolverTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/BaselineRuleFactoryApplyRangeOverridesTest.kt`
- Modify: `app/src/main/java/com/ttcoachai/TrainingActivity.kt`
- Modify: `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`

**Interfaces:**
- Consumes: `ShippedBaselines.FOREHAND_ANDRII`/`defaultBands()` (Task B),
  `PerPhaseTargetsCodec.parse` (Task C), `com.ttcoachai.ui.{REFERENCE_STANDARD, REFERENCE_BASELINE,
  isCalibrationRequired}` (existing, first production caller), `ForehandDriveGeneral.
  MOVEMENT_TOLERANT_HIP_TRAVEL` (existing), `RtmposeTrainingController`'s new `rules`/
  `hipTravelMaxTorso` params (Task G), `BaselineRuleFactory.{defaultRules, applyRangeOverrides}`
  (existing).
- Produces: intent extra keys `"REFERENCE_TYPE"` and `"MOVEMENT_PROFILE"` (string literals,
  matching the existing all-caps convention of `"EXERCISE_ID"`/`"EXERCISE_NAME"`/
  `"PER_PHASE_TARGETS_JSON"` — set by `DrillsFragment.onExerciseSelected`, read by
  `TrainingActivity`). `DrillReferenceResolver.resolveMetricBands(referenceTypeExtraPresent:
  Boolean, parsedBands: Map<String, ClosedRange<Double>>, shippedDefaultBands: Map<String,
  ClosedRange<Double>>): Map<String, ClosedRange<Double>>`.

**Design note (read before editing):** the ONLY entry point that will ever set `REFERENCE_TYPE`
is `DrillsFragment.onExerciseSelected`'s `custom_`-prefixed branch. Any other launch of
`TrainingActivity` (`SessionReviewFragment.onTrainAgain`, or a locked/legacy id) carries no
`REFERENCE_TYPE` extra at all — `intent.getStringExtra("REFERENCE_TYPE")` returns `null`, which is
the signal `DrillReferenceResolver` uses to fall back to `ShippedBaselines.defaultBands()` instead
of an empty band map (spec: "an exercise id with no backing CustomDrillEntity row ... defaults to
standard mode with ShippedBaselines.defaultBands() — trains immediately, never gates"). When the
extra IS present (even carrying `"standard"` with a genuinely-blank `perPhaseTargetsJson`), the
parsed bands are used as-is — a metric a player deliberately left blank in the editor stays silent.

**Second design note:** in "standard" mode, `decideCameraModeAndStart()` does **not** call
`loadRtmBaseline()` at all (it uses `ShippedBaselines.FOREHAND_ANDRII` directly) — so
`isForehandRtmEligible(exerciseId)`'s forehand/`custom_`-prefix gate is bypassed entirely for
standard mode. This is deliberate, not an oversight: the spec's Goal is literally "Any drill opens
and trains immediately, no calibration gate," and every real launchable id is either locked
(blocked before reaching `TrainingActivity`) or `custom_`-prefixed (already forehand-RTM-eligible
today). `isForehandRtmEligible` keeps gating only the "baseline" mode's personal-baseline lookup,
exactly as it does today — unchanged.

- [ ] **Step 1: Write the failing test for `DrillReferenceResolver` first**

  Create `app/src/test/java/com/ttcoachai/util/DrillReferenceResolverTest.kt`:

  ```kotlin
  package com.ttcoachai.util

  import org.junit.Assert.assertEquals
  import org.junit.Test

  class DrillReferenceResolverTest {

      private val parsed = mapOf("knee_bend" to 100.0..140.0)
      private val shippedDefaults = mapOf(
          "elbow_angle" to 30.0..70.0,
          "knee_bend" to 120.0..160.0
      )

      @Test
      fun fallsBackToShippedDefaultsWhenReferenceTypeExtraAbsent() {
          val result = DrillReferenceResolver.resolveMetricBands(
              referenceTypeExtraPresent = false,
              parsedBands = parsed,
              shippedDefaultBands = shippedDefaults
          )
          assertEquals(shippedDefaults, result)
      }

      @Test
      fun usesParsedBandsAsIsWhenReferenceTypeExtraPresent() {
          val result = DrillReferenceResolver.resolveMetricBands(
              referenceTypeExtraPresent = true,
              parsedBands = parsed,
              shippedDefaultBands = shippedDefaults
          )
          assertEquals(parsed, result)
      }

      @Test
      fun emptyParsedBandsStaySilentWhenExtraPresent() {
          val result = DrillReferenceResolver.resolveMetricBands(
              referenceTypeExtraPresent = true,
              parsedBands = emptyMap(),
              shippedDefaultBands = shippedDefaults
          )
          assertEquals(emptyMap<String, ClosedRange<Double>>(), result)
      }
  }
  ```

  Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.util.DrillReferenceResolverTest"`
  Expected: FAIL (compile error — `DrillReferenceResolver` doesn't exist).

- [ ] **Step 2: Write `DrillReferenceResolver.kt`**

  ```kotlin
  package com.ttcoachai.util

  /**
   * Resolves the per-metric reference bands the RTM live path should use, given what the
   * launching intent carried (docs/superpowers/specs/2026-07-27-no-calibration-shipped-baseline-design.md
   * §5). [referenceTypeExtraPresent] is false only when the launching screen predates/doesn't
   * know about this feature (e.g. SessionReviewFragment's "Train Again", or an exercise id with
   * no backing CustomDrillEntity) — in that case [shippedDefaultBands] is used so the drill
   * trains immediately instead of silently producing zero cues. When the extra IS present,
   * [parsedBands] is used as-is: a band a player left blank in the editor legitimately means
   * "silent for this metric," not "fall back to the shipped default."
   */
  object DrillReferenceResolver {
      fun resolveMetricBands(
          referenceTypeExtraPresent: Boolean,
          parsedBands: Map<String, ClosedRange<Double>>,
          shippedDefaultBands: Map<String, ClosedRange<Double>>
      ): Map<String, ClosedRange<Double>> =
          if (referenceTypeExtraPresent) parsedBands else shippedDefaultBands
  }
  ```

- [ ] **Step 3: Run to verify it passes**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.util.DrillReferenceResolverTest"`
  Expected: PASS.

- [ ] **Step 4: Add the "standard mode yields exactly the band-covered metrics" shared-level test**

  In `shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/BaselineRuleFactoryApplyRangeOverridesTest.kt`,
  add a new test (this is the exact scenario Task H's `startRtmController` exercises: seeding
  `applyRangeOverrides` from an EMPTY starting list with multiple bands):

  ```kotlin
  @Test
  fun emptySeedWithMultipleBandsYieldsExactlyThoseRangeRulesNothingElse() {
      val bands = mapOf(
          "elbow_angle" to 30.0..70.0,
          "knee_bend" to 120.0..160.0,
          "coil_ratio" to 0.9..1.4
      )
      val result = BaselineRuleFactory.applyRangeOverrides(emptyList(), bands)
      assertEquals(3, result.size)
      assertTrue(result.all { it is BaselineRule.RangeRule })
      assertEquals(bands.keys, result.filterIsInstance<BaselineRule.RangeRule>().map { it.metricKey }.toSet())
  }
  ```

  Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.analysis.BaselineRuleFactoryApplyRangeOverridesTest"`
  Expected: PASS (this exercises existing, unmodified `applyRangeOverrides` — a true regression
  pin, not a behavior change).

- [ ] **Step 5: `TrainingActivity` — read the 2 new intent extras, replace `kneeBendStrikeBand`
  with a generic all-keys field**

  Re-grep `kneeBendStrikeBand\|EXERCISE_ID\|EXERCISE_NAME` in `TrainingActivity.kt` to confirm
  current line numbers before editing (the file has concurrent-session edits elsewhere in the
  repo, though not necessarily in this file — verify before assuming the content you read earlier
  in this session still matches).

  Replace the `kneeBendStrikeBand` field declaration with:

  ```kotlin
  /**
   * All configured per-metric reference bands for this drill, decoded once in
   * [initializeAnalysis] and resolved (Task H — [com.ttcoachai.util.DrillReferenceResolver])
   * in [decideCameraModeAndStart]/[retryAfterCalibration]. Replaces the old single-metric
   * kneeBendStrikeBand extraction — every DrillMetrics key configured in the editor now flows
   * through, not just knee_bend.
   */
  private var drillMetricBands: Map<String, ClosedRange<Double>> = emptyMap()

  private var referenceTypeExtra: String? = null
  private var movementProfile: String? = null
  ```

  In `onCreate()`, right after `exerciseName = intent.getStringExtra("EXERCISE_NAME")`, add:

  ```kotlin
  referenceTypeExtra = intent.getStringExtra("REFERENCE_TYPE")
  movementProfile = intent.getStringExtra("MOVEMENT_PROFILE")
  ```

  In `initializeAnalysis()`, replace the `perPhaseTargets[PerPhaseTargetsCodec.KEY_KNEES_STRIKE]?.let
  { ... }` block (the one that also set `kneeBendStrikeBand`) with a generic pass over the WHOLE
  parsed map — leave the preceding `KEY_KNEES_BACKSWING` block (legacy, feeds only the dead
  `exerciseParameters`) untouched:

  ```kotlin
  drillMetricBands = perPhaseTargets.mapValues { (_, pair) -> pair.first.toDouble()..pair.second.toDouble() }
  ```

- [ ] **Step 6: `TrainingActivity` — rewrite `decideCameraModeAndStart()`/`retryAfterCalibration()`/`startRtmController()`**

  Add imports:

  ```kotlin
  import com.ttcoachai.shared.analysis.BaselineRuleFactory
  import com.ttcoachai.shared.drill.LocomotionFilter
  import com.ttcoachai.shared.drill.ShippedBaselines
  import com.ttcoachai.shared.drill.movements.ForehandDriveGeneral
  import com.ttcoachai.ui.REFERENCE_STANDARD
  import com.ttcoachai.ui.isCalibrationRequired
  import com.ttcoachai.util.DrillReferenceResolver
  ```

  Replace `decideCameraModeAndStart()`:

  ```kotlin
  private fun decideCameraModeAndStart() {
      lifecycleScope.launch {
          val referenceType = referenceTypeExtra ?: REFERENCE_STANDARD
          val drillBands = DrillReferenceResolver.resolveMetricBands(
              referenceTypeExtraPresent = referenceTypeExtra != null,
              parsedBands = drillMetricBands,
              shippedDefaultBands = ShippedBaselines.defaultBands()
          )
          val baseline: PersonalBaseline? = if (isCalibrationRequired(referenceType)) {
              loadRtmBaseline()
          } else {
              ShippedBaselines.FOREHAND_ANDRII
          }

          // Coroutine resumed after the suspend point above — bail out before touching the
          // fragment manager or views if the activity dropped below STARTED meanwhile.
          if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch

          val started = baseline != null && startRtmController(baseline, referenceType, movementProfile, drillBands)
          if (started) {
              binding.root.postDelayed({ startTraining() }, 500)
          } else {
              showCalibrationRequiredDialog()
          }
      }
  }
  ```

  Replace `startRtmController(baseline: PersonalBaseline): Boolean` with a 4-parameter version:

  ```kotlin
  private fun startRtmController(
      baseline: PersonalBaseline,
      referenceType: String,
      movementProfile: String?,
      drillBands: Map<String, ClosedRange<Double>>
  ): Boolean {
      mediaManager.setup()
      val rules = if (isCalibrationRequired(referenceType)) {
          BaselineRuleFactory.defaultRules(baseline)
      } else {
          emptyList()
      }
      val hipTravelMaxTorso = if (movementProfile == "general") {
          ForehandDriveGeneral.MOVEMENT_TOLERANT_HIP_TRAVEL
      } else {
          LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO
      }
      val controller = RtmposeTrainingController(
          activity = this@TrainingActivity,
          container = binding.cameraPreviewContainer,
          stateManager = stateManager,
          settingsManager = SettingsManager(this@TrainingActivity),
          baseline = baseline,
          rules = rules,
          hipTravelMaxTorso = hipTravelMaxTorso,
          onUiUpdate = { uiController.updateStats() },
          metricBands = drillBands
      )
      if (!controller.start()) return false
      rtmController = controller
      uiController.setCorrectionChipsForPath(true)
      return true
  }
  ```

  Replace `retryAfterCalibration()`:

  ```kotlin
  private suspend fun retryAfterCalibration() {
      val referenceType = referenceTypeExtra ?: REFERENCE_STANDARD
      val drillBands = DrillReferenceResolver.resolveMetricBands(
          referenceTypeExtraPresent = referenceTypeExtra != null,
          parsedBands = drillMetricBands,
          shippedDefaultBands = ShippedBaselines.defaultBands()
      )
      val baseline: PersonalBaseline? = if (isCalibrationRequired(referenceType)) {
          loadRtmBaseline()
      } else {
          ShippedBaselines.FOREHAND_ANDRII
      }
      if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
      if (baseline != null && startRtmController(baseline, referenceType, movementProfile, drillBands)) {
          binding.root.postDelayed({ startTraining() }, 500)
      } else {
          finish()
      }
  }
  ```

  Leave `loadRtmBaseline()` (personal-baseline lookup, `isForehandRtmEligible` gate) and
  `showCalibrationRequiredDialog()`/`calibrationLauncher` completely unchanged — "baseline" mode's
  gate behavior is explicitly unchanged by this spec.

- [ ] **Step 7: `DrillsFragment` — pass the 2 new extras**

  In `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`'s `onExerciseSelected()`,
  inside the `exercise.id.startsWith(CUSTOM_DRILL_PREFIX)` branch, add the 2 new `putExtra` calls
  right after the existing `PER_PHASE_TARGETS_JSON` one:

  ```kotlin
  val intent = Intent(requireContext(), TrainingActivity::class.java).apply {
      putExtra("EXERCISE_ID", exercise.id)
      putExtra("EXERCISE_NAME", exercise.name)
      putExtra("PER_PHASE_TARGETS_JSON", entity?.perPhaseTargetsJson ?: "")
      putExtra("REFERENCE_TYPE", entity?.referenceType ?: "standard")
      putExtra("MOVEMENT_PROFILE", entity?.movementProfile)
  }
  ```

  (The bottom non-custom branch, reachable only for a locked drill hypothetically un-locked in the
  future, is left untouched — it carries none of these 3 extras, matching the "no extras → standard
  + shipped default bands, never gate" fallback design.)

- [ ] **Step 8: Build gate**

  Run: `./gradlew :app:assembleDebug`
  Expected: SUCCESS.

- [ ] **Step 9: Run the full set of app-module unit tests touched by this task**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.util.DrillReferenceResolverTest" --tests "com.ttcoachai.util.PerPhaseTargetsCodecTest" --tests "com.ttcoachai.util.SeededDrillsPolicyTest" --tests "com.ttcoachai.util.CommunityDrillCopierTest"
  ./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.analysis.BaselineRuleFactoryApplyRangeOverridesTest" --tests "com.ttcoachai.shared.drill.ShippedBaselinesTest"
  ```

  Expected: all PASS.

- [ ] **Step 10: Device smoke**

  Open a seeded drill (uncalibrated device/fresh install): confirm it trains immediately, voice
  fires on a stroke with a cue. Edit one of its bands in the editor, save, start a new session —
  confirm the edited band takes effect (e.g. narrow the knee-bend band to something you'll clearly
  violate, confirm the cue fires sooner/differently). Long-press a seeded drill → Clone → in the
  new drill's editor, select "Reference: My baseline (calibrate to me)" → save without
  calibrating first → start it → confirm the "calibration required" dialog now DOES appear
  (baseline mode still gates).

- [ ] **Step 11: Commit**

  ```bash
  git add app/src/main/java/com/ttcoachai/util/DrillReferenceResolver.kt app/src/test/java/com/ttcoachai/util/DrillReferenceResolverTest.kt shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/BaselineRuleFactoryApplyRangeOverridesTest.kt app/src/main/java/com/ttcoachai/TrainingActivity.kt app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt
  git commit -m "$(cat <<'EOF'
  feat(training): consult referenceType — standard mode trains without calibration

  isCalibrationRequired(referenceType) gets its first production caller: "standard" (seeded
  drills' default) builds LiveDrillSession against ShippedBaselines.FOREHAND_ANDRII with an
  empty starting rule list overlaid with the drill's own bands, no personal-baseline gate;
  "baseline" keeps today's exact gated behavior. The single hardcoded knee-bend band
  extraction is replaced by a generic all-DrillMetrics-keys pass, and the General movement
  profile's widened locomotion tolerance now actually reaches LiveDrillSession (previously a
  no-op — RtmposeTrainingController never plumbed it through).

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Task I — Design Limitations register update

**Files:**
- Modify: `docs/DESIGN_LIMITATIONS.md`

**Interfaces:** none (documentation only).

- [ ] **Step 1: Re-check the highest existing `L-` number before adding**

  ```bash
  grep -oE "L-[0-9]+" docs/DESIGN_LIMITATIONS.md | sort -t- -k2 -n | tail -5
  ```

  As of this plan's writing, `L-36` is the highest defined entry. Concurrent sessions may have
  added more since — if so, start numbering from one past whatever the grep shows, not from L-37
  blindly.

- [ ] **Step 2: Add 4 new entries** to the "## 1. 2D pose pipeline" section (after `L-30`, before
  the `## 2. Live capture & Android runtime` header — or wherever the highest-numbered active entry
  in that section actually sits once you've re-checked in Step 1):

  ```markdown
  ### L-37 · Shipped `ShippedBaselines.FOREHAND_ANDRII` bands carry Andrii's own camera-yaw error — `ACCEPTED`
  Derived with `cameraYawDeg` pinned to 0f (yaw gate consciously relaxed for this one-time
  editorial derivation — per-rep |yaw| on the source footage ran well past the normal ~30°
  placement gate; `CameraAngleEstimator` saturates on this non-protocol footage, see L-25). The
  shipped bands therefore encode "match this recorded stroke as filmed," not a camera-agnostic
  universal norm — consistent with the accompanying research appendix's conclusion that no
  external numeric target survives scrutiny for this project's 2D included-angle convention.
  **Refs:** docs/superpowers/specs/2026-07-27-no-calibration-shipped-baseline-design.md §1;
  docs/shipped-baseline-derivation.md; `ShippedBaselines.kt`; L-25.

  ### L-38 · Standard-mode severity ranking reflects Andrii's own variability, not a player-agnostic scale — `ACCEPTED`
  `ShippedBaselines.FOREHAND_ANDRII` is also the σ-carrier baseline `DrillFeedbackEngine.evaluateRep`
  normalizes severity against in "standard" reference mode — a metric where Andrii was very
  consistent (small σ) ranks small deviations from it more severely than one where he naturally
  varied more. Useful as a relative priority order (which cue to say first when several fire at
  once); not a validated absolute severity scale.
  **Refs:** `DrillFeedbackEngine.kt`; `ShippedBaselines.kt`.

  ### L-39 · Deleting both seeded drills with no other custom drills resurrects them — `ACCEPTED`
  `SeededDrillsPolicy.shouldSeed`'s trigger ("flag unset OR custom_drills table empty") is needed
  so a destructive-migration DB wipe re-seeds (the wipe clears Room but not SharedPreferences) —
  but it can't distinguish "wiped by migration" from "player deleted every custom drill on
  purpose." A player who deletes both seeded rows and has no other custom drill gets them back on
  the next app start.
  **Refs:** `SeededDrillsPolicy.kt`; docs/superpowers/specs/2026-07-27-no-calibration-shipped-baseline-design.md §2.

  ### L-40 · Community drills authored before the 7-row editor rework surface only 2 of 7 bands — `ACCEPTED`
  `PerPhaseTargetsCodec`'s legacy-key mapping only covers `"knees · strike"` → `knee_bend` and
  `"torso tilt · strike"` → `torso_lean` — the other 5 metrics (`elbow_angle`, `shoulder_angle`,
  `follow_through_angle_2d`, `stroke_speed`, `coil_ratio`) had no editor row before this rework, so
  old shared blobs never carried them. A pre-rework community drill shows those 5 rows unset until
  the author re-edits and re-shares.
  **Refs:** `PerPhaseTargetsCodec.kt`; `ExerciseEditorActivity.kt`.
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add docs/DESIGN_LIMITATIONS.md
  git commit -m "$(cat <<'EOF'
  docs: register 4 limitations from the no-calibration shipped-baseline work

  Andrii yaw-carried bands, sigma-carrier severity reflecting his own variability, seed
  resurrection on full-delete, and old community blobs surfacing only 2 of 7 bands.

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Task J — Build gate + device smoke + merge

**Files:** none (verification only).

- [ ] **Step 1: Full scoped test sweep**

  ```bash
  ./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ShippedBaselineDerivationHarness" --tests "com.ttcoachai.shared.drill.ShippedBaselinesTest" --tests "com.ttcoachai.shared.analysis.BaselineRuleFactoryApplyRangeOverridesTest"
  ./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.util.PerPhaseTargetsCodecTest" --tests "com.ttcoachai.util.SeededDrillsPolicyTest" --tests "com.ttcoachai.util.DrillReferenceResolverTest" --tests "com.ttcoachai.util.CommunityDrillCopierTest"
  ```

  Expected: all PASS. (Do not run the bare `./gradlew test` / `:app:testDebugUnitTest` without
  `--tests` — `MotionAnalyzerJsonTest`'s pre-existing unrelated failure will show red and is not
  this work's regression.)

- [ ] **Step 2: Full build gate**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: SUCCESS.

- [ ] **Step 3: End-to-end device smoke (fresh install / `adb shell pm clear com.ttcoachai`)**

  1. Fresh install (or clear app data). Open Drills tab. Confirm the seeded "Forehand Andrii" and
     "Forehand Drive General" custom drills exist and no hardcoded unlocked forehand rows remain.
  2. Tap "Forehand Andrii" with **no personal calibration**. Confirm training starts immediately
     (no calibration-required dialog) and voice feedback fires on a stroke with a violated band.
  3. Long-press → Edit → widen or narrow a band in the 7-row advanced section (try the new Stroke
     speed / Body rotation decimal fields) → Save. Start a new session; confirm the edited band is
     the one now in effect (compare cue timing/threshold against before the edit).
  4. Long-press "Forehand Drive General" → confirm it still opens and trains (movement-tolerant
     profile) — take a small step between reps and confirm it is NOT flagged as locomotion (wider
     gate than the structured drill).
  5. Clone any custom drill → in the editor select "Reference: My baseline (calibrate to me)" →
     save WITHOUT calibrating → start it → confirm the calibration-required dialog now appears
     (this is the one case that should still gate).
  6. Complete a calibration from that dialog → confirm training starts afterward.

- [ ] **Step 4: Merge to main** (per this repo's standing preference — merge automatically, no PR)

  ```bash
  git checkout main
  git pull --ff-only
  git merge --no-ff feat/no-calibration-shipped-baseline
  git push
  ```

  If `main` has moved and the merge conflicts, resolve conflicts favoring an explicit re-read of
  both sides (never blindly take "ours"/"theirs" on files with concurrent-session activity —
  `TrainingActivity.kt`, `DrillsFragment.kt`, `AppDatabase.kt`, and `strings.xml`/`values-uk/strings.xml`
  are the files most likely to have moved).

---

## Self-Review

- **Spec coverage:** Decision 1 (dual-role Andrii baseline) → Tasks A/B. Decision 2 (seeded
  CustomDrillEntity rows replace hardcoded) → Task F. Decision 3 (movementProfile column, not id
  match) → Tasks E/G/H. Decision 4 (10→7 editor rows) → Task D. Decision 5 (referenceType
  consulted) → Task H. §6 "what does not change" (voice, cadence, chips, session save, MediaPipe
  backend) → untouched by any task, confirmed no task edits those files. Testing section's 6
  bullets → `ShippedBaselinesTest` (defaultBands), `PerPhaseTargetsCodecTest` (legacy-key
  mapping), `BaselineRuleFactoryApplyRangeOverridesTest`+`DrillReferenceResolverTest` (rule
  construction), `SeededDrillsPolicyTest` (seeding idempotence, all 3 named scenarios), existing
  `LiveDrillSession`/`RepValidationConfig` tests untouched (confirmed — no task modifies them),
  Task J (build gate + device smoke, verbatim from spec). Limitations section's 4 items → Task I.
- **Placeholder scan:** the only intentional placeholders are the zero-valued `MetricStats(...)`
  entries in Task B's `ShippedBaselines.kt` code block — explicitly sanctioned by the Global
  Constraints section and flagged inline with `// REPLACE` comments; every other code block in
  every task is complete, real, and matches the actual current signatures read from the repo.
- **Type consistency:** `DrillReferenceResolver.resolveMetricBands` signature (Task H Step 2)
  matches its test (Step 1) and its call sites (Step 6) exactly. `SeededDrillsPolicy`'s
  `SeedNames`/`buildSeedEntities`/`seedMissing`/`shouldSeed` signatures match between the test
  (Step 1) and implementation (Step 3) and the `TTCoachApplication` wiring (Task F Step 6).
  `RtmposeTrainingController`'s new `rules`/`hipTravelMaxTorso` params (Task G) match exactly how
  `TrainingActivity.startRtmController` calls them (Task H Step 6). `PerPhaseTargetsCodec.encode`'s
  `Map<String, Pair<Float, Float>>` shape matches both `ExerciseEditorActivity.encodeAdvancedTargets`
  (Task D) and the `TTCoachApplication` seeding call (Task F Step 6, via `.mapValues` from
  `ShippedBaselines.defaultBands()`'s `Map<String, ClosedRange<Double>>`).
