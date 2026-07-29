# Training without calibration — editable drill reference bands seeded from Andrii

Date: 2026-07-27 · Updated: 2026-07-28 (core design replaced after feedback — Problem,
Research appendix, General-profile wiring gap unchanged)
Status: approved (design decisions confirmed by user; write-up only, no implementation yet)

## Problem

Since the RTM path became the only live path, `TrainingActivity` blocks EVERY drill behind
a "calibration required" dialog. `decideCameraModeAndStart()` calls `loadRtmBaseline()`
(`app/src/main/java/com/ttcoachai/TrainingActivity.kt:170`); if it returns null,
`showCalibrationRequiredDialog()` (line 207, called from line 165) shows a non-cancelable
dialog whose only actions are "Calibrate Now" (launches `RtmposeCalibrationActivity` via
`calibrationLauncher`, line 56) or "Not Now" (`finish()`). The app ships **zero** reference
angles for the live path — `loadRtmBaseline()` only reads
`PersonalBaselineRepository.getActiveBaseline("forehand_drive_rtm")`
(`app/src/main/java/com/ttcoachai/repository/PersonalBaselineRepository.kt:35`), null until
the player calibrates once.

`CustomDrillEntity.referenceType` (`app/src/main/java/com/ttcoachai/models/CustomDrillEntity.kt:13`,
default `"standard"`) is persisted end-to-end (editor UI, Room, Community Drills Firestore
mapping) but `isCalibrationRequired(referenceType)`
(`app/src/main/java/com/ttcoachai/ui/ExerciseEditorLogic.kt:110`) has no production caller —
grep shows only test references. The field means nothing at runtime today.

The legacy MediaPipe pipeline (deleted 2026-07-24, see CLAUDE.md "MediaPipe legacy
calibration/inference path removed") papered over this with hard-coded `ExerciseParameters`
textbook angles. That fallback is gone — there is nothing behind the RTM path except the
personal baseline. **Net effect:** a fresh install cannot try a single drill without
calibrating first — a hard onboarding wall that contradicts the product's own "calibrate,
don't re-teach" positioning by making calibration a gate instead of an upgrade.

## Goal

Any drill opens and trains immediately, no calibration gate, with editable per-metric
reference bands a player (or a shared community drill) can tune — seeded from a shipped
Andrii baseline, never from a hardcoded textbook. Calibration stays available as an upgrade
path: opting a drill into "baseline" mode still requires it, and still gates.

## Decisions (revised)

| # | Decision | Rationale |
|---|---|---|
| 1 | `ShippedBaselines.FOREHAND_ANDRII` keeps existing, gains two new roles: default-band source + σ-carrier | One curated derivation now feeds both the editable bands and the severity math that ranks qualitative cues — no second data source to keep in sync. |
| 2 | The 3 hardcoded forehand drills become 2 **seeded `CustomDrillEntity` rows** | One code path (editor, `DrillActions`, Community Drills) instead of a parallel hardcoded-vs-custom split; bands become editable for free. |
| 3 | `CustomDrillEntity.movementProfile` column carries the General tolerance, not an id string match | Survives drill renaming/cloning; matches how every other per-drill knob (bands, strictness) is already column-driven. |
| 4 | Editor's 10 dead-backswing-inclusive rows collapse to the 7 rows that actually bind to a live metric | The old 10 rows only ever wired 2 of themselves to feedback; the other 8 were silent placeholders. Backswing rows return with the rhythm spec, not before. |
| 5 | `referenceType` is finally read: `"standard"` → shipped baseline + drill bands, no gate; `"baseline"` → personal calibration, gate restored | This is the original bug (`isCalibrationRequired` had no caller) — fixing it is what makes per-drill "does this need calibration" meaningful instead of a dead field. |

## 1. Shipped Andrii baseline — dual role

`ShippedBaselines.FOREHAND_ANDRII: PersonalBaseline` (new file,
`shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ShippedBaselines.kt`) is still
derived exactly as before — offline, one-time, curated: `Videos/andrii_1/andrii_1.mp4` →
`.venv/bin/python scripts/poses/export_poses_mediapipe.py Videos/andrii_1/andrii_1.mp4
--model lite` (`--model` is `lite|full|heavy`, default `lite` — same keypoint space as
`MediaPipePoseLandmarkerBackend`'s default live variant `MEDIAPIPE_LITE_GPU`, per
`PoseBackendFactory.kt`) → fed through the same `DrillCalibrator`
(`shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillCalibrator.kt`) path the
live "Reference: Baseline" flow uses → numbers pasted into the constant.

This session's MediaPipe-lite re-export of `andrii_1` surfaced two issues that make the
derivation curated, not blind: **camera-yaw saturation** (`CameraAngleEstimator` already has
an open limitation, L-25 in `docs/DESIGN_LIMITATIONS.md`: *"returns 90.0° (the ceiling) on
`andrii_1_rtm`"* — this re-export confirms it, per-rep `|yaw|` roughly 41–90° across 15
reps, median ≈43.5°, past the ~30° placement gate), and a **bimodal `elbow_angle`**
distribution (~10 reps at 35–69°, ~5 at 111–135°), consistent with forward strokes mixed
with recovery/non-forward swings surviving detection. Given both, rep selection is
**verified visually in poses_viewer** (not accepted purely on filter/gate output), and the
yaw gate may be consciously relaxed for this one-time editorial step — an intentional
exception, not a precedent. Derivation command, chosen rep indices, and exclusion rationale
are committed alongside the constant so it stays reproducible. `createdAtMs` fixed at
authoring time; `drillerHandedness = "right"`.

**What's new is what the constant is used for.** **(a) Default band source:** a small pure
function, `ShippedBaselines.defaultBands(): Map<String, ClosedRange<Double>>`, computes
`mean ± 2σ` (rounded) for each of the 7 `DrillMetrics.ALL_KEYS` metrics from
`FOREHAND_ANDRII.metricStats` — the seed value written into a new drill's
`perPhaseTargetsJson` (§2), so a player never sees an empty editor or a hardcoded textbook
figure, only a real, tunable starting band.

**(b) σ-carrier for severity.** `DrillFeedbackEngine.evaluateRep` (`DrillFeedbackEngine.kt:38-76`)
normalizes a `RangeRule` cue's severity by `stats.std` when the passed-in baseline has stats
for that metric (line 63: `stats != null && stats.std > 0.0 -> abs(delta) / stats.std`), and
only falls back to the crude `DEFAULT_RANGE_SEVERITY_SCALE_DEGREES = 10.0` constant (line 64,
kdoc: *"chosen to sit in the same ballpark as typical in-plane-metric baseline σ"*) when the
baseline has no stats for that metric. In standard mode, `LiveDrillSession` is now
constructed with `baseline = ShippedBaselines.FOREHAND_ANDRII` (stats for all 7 keys)
instead of nothing — so a `coil_ratio`/`stroke_speed` band violation ranks by Andrii's own
variability instead of a degrees-shaped constant meaningless for a ratio or a torso-lengths/s
speed, letting qualitative-metric cues compete for priority at all. The 10.0 fallback still
applies to any metric genuinely missing stats.

## 2. Seeded drills replace built-in forehands

`DrillsFragment.setupData()` (`app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt:91-117`)
drops all three unlocked hardcoded forehand rows (`forehand_drive`, `forehand_andrii`,
`forehand_drive_general`); locked drills (`backhand_loop` etc.) stay hardcoded, unchanged.

On first run — and after any DB wipe, since `fallbackToDestructiveMigration()`
(`app/src/main/java/com/ttcoachai/db/AppDatabase.kt:44`) is the repo's stated norm for
schema bumps — the app idempotently seeds two `CustomDrillEntity` rows:
`"custom_seed_forehand_andrii"` ("Накат справа (Андрій)") and
`"custom_seed_forehand_general"` ("Накат справа General"), `perPhaseTargetsJson` pre-filled
with `ShippedBaselines.defaultBands()` (§1a) encoded via `PerPhaseTargetsCodec`, and
`referenceType = "standard"`. The `custom_` prefix is load-bearing, not cosmetic: it's what
`DrillActions.isCustom`/`canEdit`/`canRename`/`canDelete` (`DrillActions.kt:15-21`,
`startsWith("custom_")`) and `TrainingActivity.isForehandRtmEligible`'s `startsWith("custom_")`
branch both key off — so seeded drills are ordinary editable/deletable/shareable custom
drills from the moment they exist, same editor and repository code path as anything a
player creates by hand. No new drill-type concept.

**Idempotence.** `CustomDrillDao.upsert` (`CustomDrillDao.kt:11-12`) is
`@Insert(onConflict = OnConflictStrategy.REPLACE)` — calling it unconditionally on a seed id
would silently clobber a player's edits on every app start. Seeding must therefore
check-before-write: call `getByDrillType(id)` (`CustomDrillDao.kt:17`) per seed id and only
insert if absent, so an edited/renamed/re-banded seeded row (same `drillType` id) is never
overwritten.

**When seeding runs, stated plainly:** a `SharedPreferences` boolean flag
`"seeded_drills_v1"` (same pattern as `isPoseUploadEnabled`/`isAudioFeedbackEnabled`,
`SettingsManager.kt:28-32`, `"ai_coach_prefs"` prefs file) marks "seeding has run once."
Seeding executes when **either** the flag is unset **or** `CustomDrillRepository.count()`
(`CustomDrillRepository.kt:16`) is `0` — the latter because a destructive-migration DB wipe
clears Room's `custom_drills` table but not `SharedPreferences`, so the flag alone would
never re-seed after a wipe. The flag is set `true` after seeding runs either way.

**Accepted resurrection risk:** if a player deletes both seeded rows and has no other
custom drills, `count()` returns to `0` and the next app start re-seeds them — not solved
here (see Limitations). **Icon fallback:** `DrillsFragment.iconForDrill(id)`
(`DrillsFragment.kt:260-267`) matches old hardcoded ids by literal string; seeded ids fall
through to `else -> R.drawable.ic_target`, the same generic icon every hand-created custom
drill already gets — acceptable, noted rather than given a new branch.

## 3. General movement profile

`ForehandDriveGeneral.DEFINITION`
(`shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/movements/ForehandDriveGeneral.kt:37-42`)
widens `RepValidationConfig.hipTravelMaxTorso` from the default
(`LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO`) to `MOVEMENT_TOLERANT_HIP_TRAVEL = 0.8f` so
small between-shot steps don't trip `LocomotionFilter`. But the live path never reads
`DEFINITION`: `RtmposeTrainingController.ensureSession()` constructs `LiveDrillSession`
directly (`app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt:329-336`) with
no `hipTravelMaxTorso` argument, so it silently takes `LiveDrillSession`'s own default
(`LiveDrillSession.kt:34`). Live, the General drill currently behaves identically to the
structured one — its one differentiator is a no-op. This gap and its fix are unchanged from
the original design; only the *source* of the tolerance value changes:

`CustomDrillEntity` gains a nullable column `movementProfile: String?` (`"general"` or
`null`), same schema bump as §2 (`AppDatabase` version 9 → 10, still
`fallbackToDestructiveMigration()`, per repo norm). The seeded General row
(`"custom_seed_forehand_general"`) carries `movementProfile = "general"`. `TrainingActivity`
reads this column (not an id/string match) and passes
`ForehandDriveGeneral.MOVEMENT_TOLERANT_HIP_TRAVEL` (`0.8f`) — or
`LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO` otherwise — into a new
`RtmposeTrainingController` constructor parameter, flowing into
`LiveDrillSession(hipTravelMaxTorso = ...)`. Plain `Float`, not a `MovementDefinition`
plumb-through (YAGNI). Editor does **not** expose `movementProfile` in v1 — seed-only.

## 4. Editor rework: 7 live rows

`ExerciseEditorActivity.advancedRows` (`ExerciseEditorActivity.kt:90-101`) currently has 10
rows (5 zones × backswing/strike-or-finish); only 2 — `"knees · strike"` and `"torso tilt ·
strike"` — ever bound to a live metric, via
`personalizedHintMetrics` (lines 85-88, whose own comment says every other row *"has no
baseline stat that matches its phase semantics"*). The 8 dead rows (all backswing rows,
plus elbow/shoulder finish) are **removed** — they bind to nothing and won't until phase
segmentation (rhythm spec, out of scope here) exists.

Replaced by exactly the 7 rows matching `DrillMetrics.ALL_KEYS` (`DrillMetrics.kt:40-49`):
elbow · strike (`elbow_angle`), shoulder · strike (`shoulder_angle`), knees · strike
(`knee_bend`), torso · strike (`torso_lean`), elbow · finish (`follow_through_angle_2d`) —
all precise ° — plus stroke speed (`stroke_speed`, torso-lengths/s) and body rotation
(`coil_ratio`, ratio) — qualitative per the trust rule, but still numerically editable with
unit-appropriate labels (not degrees).

`PerPhaseTargetsCodec` (`app/src/main/java/com/ttcoachai/util/PerPhaseTargetsCodec.kt`)
switches its JSON keys from the old zone/phase strings (`"knees · strike"` etc.) to the
`DrillMetrics` metric keys themselves (`"elbow_angle"`, `"knee_bend"`, ...). `parse()`
(`PerPhaseTargetsCodec.kt:24-34`) is already a generic `Map<String, Pair<Float, Float>>`
walk — no shape change needed — but gains a legacy-key mapping applied on parse
(`"knees · strike"` → `knee_bend`, `"torso tilt · strike"` → `torso_lean`) because
`CommunityDrillRepository` syncs `perPhaseTargetsJson` verbatim
(`CommunityDrillRepository.kt:175,185`, `CommunityDrillMapper.kt:23,45,55`) — old
community-shared blobs must keep decoding.

`TrainingActivity`'s current hardcoded single-metric extraction —
`kneeBendStrikeBand: ClosedRange<Double>?` (line 49), filled only from
`PerPhaseTargetsCodec.KEY_KNEES_STRIKE` (lines 114-123), sole entry of `metricBands`
(line 196) — is replaced by a generic all-keys pass into `metricBands`.

New strings land in `values/strings.xml` (EN) and `values-uk/strings.xml` for the 7 row
labels. `CorrectionType` (`CorrectionType.kt:3-15`) does **not** gain new values — bands
feed the existing per-metric cues (`ELBOW_BEND`, `ELBOW_POSITION`, `KNEE_BEND`, `POSTURE`,
`FOLLOW_THROUGH`, `STROKE_SPEED`, `BODY_ROTATION`), unchanged from the shipped RTM
correction taxonomy.

## 5. `referenceType` finally consulted

This is the original bug this spec exists to fix: `isCalibrationRequired(referenceType)`
(`ExerciseEditorLogic.kt:110`) gets its first production caller.

- **`"standard"`** (seeded drills' default): `LiveDrillSession` is built with `baseline =
  ShippedBaselines.FOREHAND_ANDRII` and `rules = BaselineRuleFactory.applyRangeOverrides(
  emptyList(), drillBands)` (`BaselineRuleFactory.kt:47-59`) — starting from an empty rule
  list means the result is *only* the drill's configured `RangeRule`s, one per non-empty
  band; a metric left blank in the editor gets no rule and stays silent
  (`DrillFeedbackEngine.evaluateRep` skips it). No calibration required; the dialog never
  fires for this mode.
- **`"baseline"`**: today's behavior, unchanged — personal calibration
  (`BaselineRuleFactory.defaultRules(personal)`, `ConsistencyRule`s at 2σ), non-empty drill
  bands still override per-metric via the same `applyRangeOverrides` (existing semantics).
  `showCalibrationRequiredDialog()` / `retryAfterCalibration()` / `calibrationLauncher`
  (`TrainingActivity.kt:56,165,207,224`) are **kept**, not deleted (correcting this spec's
  earlier draft) — firing only when `referenceType == "baseline"` and no personal baseline
  exists yet.

**How the drill row reaches `TrainingActivity`:** the same way `perPhaseTargetsJson`
already does — intent extras from the launching screen (`DrillsFragment.kt:476` passes
`PER_PHASE_TARGETS_JSON`; it gains `REFERENCE_TYPE` and `MOVEMENT_PROFILE` siblings). An
exercise id with **no** backing `CustomDrillEntity` row (e.g. "Continue" on an old session
whose id was a removed hardcoded drill) defaults to standard mode with
`ShippedBaselines.defaultBands()` — trains immediately, never gates.

## 6. What does not change / out of scope

Voice, cadence, correction chips, session save/upload, `PoseSessionRecorder`, overlay,
MediaPipe backend/factory/picker — untouched. Trust rule unchanged: `stroke_speed`/
`coil_ratio` keep qualitative voice phrasing, never spoken as degrees, regardless of source.

**Deferred, separate specs:**
- **Rhythm/tempo coaching (backswing segmentation).** `BaselineRule.RhythmRule`
  (`BaselineRule.kt:27`, 25% tolerance) is evaluated nowhere — `FrameRuleEvaluator.evaluate`
  returns null for it (line 33, *"Phase duration rules ... aren't evaluable per frame"*);
  segmentation is deferred in `MovementCalibrator.kt:83`. The 8 removed rows return only
  once this exists.
- **`referenceType` per-drill baseline keys.** Still ONE global personal-baseline lineage
  (`"forehand_drive_rtm"`) — `"baseline"` mode means "use your one calibrated baseline,"
  not one scoped to that specific drill.

## Research appendix

Two literature rounds found **no usable numeric joint-angle targets** for the forehand
drive in this project's included-angle 2D side-view convention — all quantitative published
work is 3D mocap on the topspin loop in anatomical-plane conventions that don't map onto 2D
included angles. The four previously-inaccessible sources were retrieved in round two; none
contained usable numbers. One direct contradiction was found on the direction of
knee-flexion-vs-skill correlation — another reason not to trust a single textbook figure
even where one exists.

Given no external target survives scrutiny, the project empirically surveyed its own
footage: 13 repo clips exported through MediaPipe-lite, 4 yielded ≥5 forward reps
(`andrii_1`: 15, `ivan_1`: 7, `video_3`: 11, `video_4`: 9 — 42 reps total). Aggregate
percentiles across all 42 reps (yaw gating **not** applied, non-protocol footage — a rough
sanity range, not a target):

| Metric | p5 | median | p95 |
|---|---|---|---|
| `elbow_angle` | 37.7° | 103.8° | 143.8° |
| `shoulder_angle` | 22.6° | 40.7° | 65.6° |
| `knee_bend` | 122.7° | 150.3° | 175.9° |
| `torso_lean` | 3.9° | 31.1° | 43.6° |
| `follow_through_angle_2d` | 65.3° | 87.0° | 166.8° |

A separate external reference (`TT_TOLMACHEV`, YouTube `kHlEc93Xl04`) was the first footage
ever to **pass** the yaw gate: 6 of 7 reps had `|yaw|` 8.3–22.8° (one outlier at 70.1°
failed). It still fell short of `minRepCount = 10` (*"Insufficient valid reps after outlier
exclusion: 6 < 10"*), so it could not become the shipped baseline. Artifacts kept in this
session's scratchpad. Path forward if revisited: a longer continuous stationary side-view
take of the same subject.

## Testing

- **jvmTest, `defaultBands()`:** all 7 `DrillMetrics.ALL_KEYS` present, `min < max` each,
  values match `mean ± 2σ` computed independently from `FOREHAND_ANDRII.metricStats` — a
  bad future re-derivation fails CI instead of shipping silently.
- **jvmTest, `PerPhaseTargetsCodec`:** legacy-key mapping (`"knees · strike"` → `knee_bend`,
  `"torso tilt · strike"` → `torso_lean`) decodes correctly alongside the new direct-key
  format.
- **jvmTest, rule construction:** standard mode (`applyRangeOverrides(emptyList(), bands)`)
  yields exactly the band-covered metrics, nothing else; baseline mode still overrides
  matching `ConsistencyRule`s and passes through the rest, per existing semantics.
- **Seeding idempotence:** check-before-write / flag-or-empty-table trigger logic extracted
  pure and tested against (flag unset, table empty), (flag set, ids present), (flag set,
  table wiped) — asserting when seeding fires and that an edited seed-id row is never
  overwritten.
- **Existing `LiveDrillSession`/`RepValidationConfig` tests** stay as the hip-travel
  tolerance coverage, unchanged by this work.
- **Build gate:** `./gradlew :app:assembleDebug`.
- **Device smoke:** open a seeded drill uncalibrated → trains immediately, voice fires.
  Edit a band → next session start uses it. Flip a drill to `"baseline"` without a personal
  calibration → the dialog fires.

## Limitations (honest)

- **Andrii's bands carry his yaw/foreshortening error** — "match this recorded stroke as
  filmed," not a universal norm, consistent with the research appendix's conclusion that no
  external numeric target exists instead.
- **σ-carrier means standard-mode severity ranking reflects Andrii's own variability**, not
  a player-agnostic "how bad is this deviation" — a metric where he was very consistent
  ranks small deviations more severely than one where he naturally varied more.
- **Deleting both seeded drills with no other custom drills present resurrects them** on
  the next app start (§2) — the flag-or-empty-table rule can't distinguish "wiped by
  migration" from "player deleted everything." Accepted for v1.
- **Community drills authored before this rework carry old JSON keys** and surface only
  their 2 previously-mappable bands (`knee_bend`, `torso_lean`) after the legacy-key
  mapping — the other 5 show unset until the author re-edits and re-shares.
