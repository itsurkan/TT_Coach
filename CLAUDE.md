# TT_Coach_AI Development Guidelines

Last updated: 2026-06-10 (2D pivot). Read the "Current direction" section first — it overrides older context below.

## Current direction — 2D PIVOT (2026-06-10, branch `2d`)

The project pivoted from MediaPipe-3D + ball-tracking to a **2D in-plane joint-angle coaching MVP on RTMPose, desktop-first**. Fixed/structured drills (first: forehand drive, side camera), voice/text feedback at 3–5 s cadence, reference angles derived from the player's **personal baseline** (003 calibration path) — calibrate to the player's technique, don't re-teach.

**Frozen, not deleted** (returns post-MVP as Stage 2 — don't modify, don't delete, don't call from new code): `BallDetectorV1–V6`, `ROIManager`, trajectory code (`TimelineSynchronizer`/`TrajectoryFilter`/`TrajectorySegmenter`), audio-contact + frame-extraction Python scripts, YOLO training, the live MediaPipe pipeline in `app/` (ports to RTMPose in Phase 3).

**Phase status:**
- **Phase 1 — desktop pose pipeline: DONE.** `scripts/poses/export_poses_rtmpose.py` (RTMPose-m + RTMDet-nano via MMPose, Mac M4) exports pose JSON **schema v2** (COCO-17; `--feet` flag → Halpe26 with foot keypoints). poses_viewer renders COCO-17/Halpe26 skeletons with an RTM header toggle.
- **Phase 2 — drill logic in shared KMP: DONE (executed).** `models/` 2D types (Keypoint2D, PoseFrame2D, PoseSequence2D, Topology, Coco17, Handedness, Stroke2D, ViewGeometry w/ xScale); `io/PoseJsonV2Parser` (strict, field-order tripwire); `analysis/AngleCalculations2D` (xScale-corrected in-plane angles, facing-normalized torso lean), `analysis/CameraAngleEstimator` (per-stroke |yaw| from shoulder foreshortening); `detection/StrokeDetector2D` (torso-lengths/sec, ms windows, keep-max NMS, valley-clamped boundaries); `BaselineDeriver.deriveFromMetrics`; `drill/` (DrillMetrics extractAtPeak ±70ms median, SanityBounds, ForwardStrokeFilter speed-dominance, RepFilter banding, DrillFeedbackEngine, FeedbackMessageCatalog UA+EN, FeedbackCadencePolicy 3–5s, DrillCalibrator w/ per-rep yaw gate + CameraPlacementException, ForehandDriveDrillAnalyzer). Fixtures: full-fps `*_rtm.json` (andrii_1 @17ms, video_2 @20ms) + TestFixturesV2. E2E exit gate green (15 forward reps from 23 raw peaks on andrii_1).
- **Phase 3 — Android port: NOT STARTED.** `PoseBackend` interface, RTMPose-s via ncnn/ONNX Runtime Mobile, TTS feedback.

**Canonical docs (read in this order when orienting):**
1. [docs/superpowers/specs/2026-06-10-2d-pivot-design.md](docs/superpowers/specs/2026-06-10-2d-pivot-design.md) — pivot decisions + phase plan
2. [docs/tt-coach-ai-context.md](docs/tt-coach-ai-context.md) — consolidated research & rationale (RTMPose vs MediaPipe, 2D sufficiency, trust rule, camera placement)
3. [docs/pose_json_schema_v2.md](docs/pose_json_schema_v2.md) — pose JSON schema v2 contract
4. [docs/superpowers/plans/2026-06-10-phase2-drill-logic-shared-kmp.md](docs/superpowers/plans/2026-06-10-phase2-drill-logic-shared-kmp.md) — current implementation plan (task-by-task, TDD)

Docs dated April 2026 and earlier (ball tracking, MediaPipe calibration UI, MVP trackers) describe the **pre-pivot** state — still accurate as history and for frozen code, but not current direction.

## Android UI redesign (gold-dark) — Slice 1 DONE (2026-07-01)

Parallel track: restyling the existing Material 3 Android `app/` to a gold-on-dark "house system"
from the claude.ai/design project **"Table Tennis Coach AI Redesign"** (project UUID
`feb1eaea-d763-41c9-86fe-1262790d7291`, read via the DesignSync tool). **`app/` UI resources are
therefore no longer strictly frozen** — but the pose/ball/trajectory pipeline code stays frozen
(this track touches presentation only: colors/type/shape/styles/layouts + debug UI). Built in slices:
- **Slice 1 — design-system foundation: DONE.** Colors (dark+light), bundled Inter Tight +
  JetBrains Mono, shape appearances, `TTC.*` component styles, and a debug preview harness
  `DesignSystemPreviewActivity` (`adb shell am start -n com.ttcoachai/.debug.DesignSystemPreviewActivity`,
  FLAG_DEBUGGABLE-gated). Spec: [docs/superpowers/specs/2026-07-01-android-gold-dark-foundation-design.md](docs/superpowers/specs/2026-07-01-android-gold-dark-foundation-design.md);
  plan: [docs/superpowers/plans/2026-07-01-android-gold-dark-foundation.md](docs/superpowers/plans/2026-07-01-android-gold-dark-foundation.md);
  verbatim tokens: [docs/design/design-tokens-source.md](docs/design/design-tokens-source.md).
- **Slices 2–4 — PENDING.** Slice 2: restyle existing screens (Dashboard/Progress/Drills/Settings/Profile/History).
  Slice 3: new screens (Session Review `6b`, Feedback `11a`, Detection `11b`, New/Clone exercise forms `10c`/`10d`).
  Slice 4: Live Session `1a` (needs the **parent** design doc — the `Live Session.dc.html` canvas only
  contains the surrounding screens, not the `1a`/`2a` capture screen itself).

## Active Technologies

**Current (2D pivot):**
- Python 3.13 (`.venv`): MMPose, RTMPose-m + RTMDet-nano — desktop pose extraction (`scripts/poses/export_poses_rtmpose.py`)
- Kotlin 2.1.0 KMP `shared/` module, **zero external deps** (repo convention) — all drill logic lives here; iOS is a firm future target
- poses_viewer: React + Vite + vitest — visual QA for RTMPose output, COCO-17/Halpe26 skeleton rendering

**Carried over / frozen in `app/`:**
- CameraX 1.5.3, MediaPipe tasks-vision 0.10.14 (frozen live pipeline), OpenCV 4.9.0 + TFLite YOLO (frozen ball tracking)
- Room 2.6.1 (sessions, baselines, `drill_configs`), `org.json` for `@TypeConverter`s, Firebase BOM 34.8.0

## Commands

**Tests (primary agent feedback loop)**
- `./gradlew :shared:jvmTest` — shared KMP tests (commonTest classes run on JVM via this task; no device)
- `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2ParserTest"` — single class
- `./gradlew test` — all JVM unit tests (shared + app)
- `cd poses_viewer && npx vitest run` — viewer/FK math tests; `npx tsc -b --noEmit` — typecheck
- `./gradlew connectedAndroidTest` — instrumented (device required; frozen-pipeline coverage)

**Desktop pose pipeline (Phase 1)**
- `.venv/bin/python scripts/poses/export_new.py` — new-clip flow: tidies loose `Videos/*.mp4` into `Videos/<base>/`, exports full-fps `_poses_rtm.json` (see `video-added` skill)
- `.venv/bin/python scripts/poses/export_poses_rtmpose.py <video>` — RTMPose video→JSON schema v2 (COCO-17); `--feet` → Halpe26
- `cd poses_viewer && npm run dev` — http://localhost:5780, overlays pose JSON on video frames; RTM header toggle for schema-v2 exports

**Build (Android, frozen pipeline)**
- `./gradlew :app:assembleDebug` / `:app:assembleRelease`

**Python scripts (`scripts/`) — frozen except poses/**
- `poses/export_poses_rtmpose.py` — CURRENT exporter (schema v2)
- `poses/export_poses.py` — legacy MediaPipe-33 exporter (schema v1, kept for old fixtures)
- `training/`, `contacts/`, `frames/`, `video/` — frozen (ball/audio/frame tooling, Stage 2)

## Project structure

```
shared/                      # KMP module — ALL NEW LOGIC GOES HERE (Phase 2)
  src/commonMain/kotlin/com/ttcoachai/shared/
    models/                  # PoseFrame, Landmark3D (legacy) + Keypoint2D, PoseFrame2D, Topology, Coco17 (Phase 2)
    io/                      # PoseJsonV2Parser (Phase 2)
    analysis/                # BaselineDeriver, BaselineRuleFactory, FrameRuleEvaluator, AngleCalculations (legacy 3D) + AngleCalculations2D, CameraAngleEstimator (Phase 2)
    detection/               # JsonStrokeDetector, StrokePhaseDetector (legacy) + StrokeDetector2D (Phase 2)
    drill/                   # DrillFeedbackEngine, FeedbackMessageCatalog UA+EN, cadence policy (Phase 2)
    tracking/                # FROZEN: TimelineSynchronizer, TrajectoryFilter, TrajectorySegmenter
  src/commonTest/            # pure-Kotlin tests + resources/fixtures/ (JSON pose fixtures, v1 + v2)
  src/jvmTest/               # fixture loaders (TestFixtures v1, TestFixturesV2) + fixture-driven tests

app/                         # Android app — frozen live pipeline; Phase 3 will add PoseBackend here
  src/main/java/com/ttcoachai/
    processors/              # PoseAnalysisProcessor (LIVE + CALIBRATION modes) — frozen
    managers/                # TrainingStateManager, CalibrationStateManager — frozen
    tracking/                # FROZEN: BallDetectorV1..V6, ROIManager
    services/                # MotionAnalyzer, FeedbackGenerator — frozen
    helpers/                 # PoseLandmarkerProcessor (MediaPipe) — frozen, replaced by RTMPose in Phase 3
    calibration/             # CalibrationActivity flow (Stage 1 Phase 1) — UX reused for drills later
    debug/                   # BaselineDebugActivity, BaselinePreviewActivity (FLAG_DEBUGGABLE-gated)
    repository/ db/ models/  # Room + Firestore; PersonalBaselineEntity, DrillConfigEntity
  src/test/                  # JVM unit tests; src/androidTest/ — instrumented

poses_viewer/                # React + Vite QA/labeling UI — has its own CLAUDE.md, read it before editing
scripts/                     # Python: poses/ current, rest frozen
docs/                        # canonical context + superpowers/{specs,plans}
Videos/                      # test footage + per-video JSON (*_poses, *_poses_rtm, *_ball_yolo, *_contacts, *_labels)
models/trained/              # frozen YOLO weights
```

## Conventions

**KMP split rule (now stricter):** all drill/analysis/detection logic → `shared/commonMain`, developed TDD against JSON fixtures, proven on JVM before any Android work. `app/` only for Android APIs (camera, inference runtime, TTS, Room, UI). iOS is a firm future target.

**`shared/` has zero external dependencies** — no kotlinx-serialization, no org.json. JSON parsing is hand-rolled (regex-anchored, see `PoseJsonV2Parser`); Room converters in `app/` use `org.json`.

**Naming suffixes:** `*Manager` stateful singleton · `*Processor` frame-by-frame pipeline · `*Analyzer` pure logic · `*Detector` inference/signal detection (versioned when iterating) · `*Repository` dual-source data. 2D-pivot classes take a `2D` suffix when a legacy 3D counterpart exists (`AngleCalculations2D`, `StrokeDetector2D`).

**Freeze discipline:** frozen code (ball tracking, live MediaPipe pipeline, trajectory) is modified only to keep the build green. New code must not call into it; adapt techniques by copying into new `2D` classes instead (e.g. `AngleCalculations2D` adapts `AngleCalculations` rather than editing it).

**Commit hygiene:** `git add` explicit paths, never `git add -A` (working tree carries unrelated artifacts: `node_modules/.vite/`, `tsconfig.tsbuildinfo`). Commit after each logical change.

**Plan execution:** always execute written implementation plans via subagent-driven development (`superpowers:subagent-driven-development`) — fresh subagent per task, review between tasks. Never execute plan tasks inline in the main session. **Never ask the user which execution approach to use — subagent-driven is the standing default; proceed without asking.** This OVERRIDES the `writing-plans` / `executing-plans` skills' "offer execution choice" step: skip that prompt and go straight to `subagent-driven-development`. **Run implementation/exploration subagents on `sonnet` (or `haiku` for cheap mechanical tasks), not opus** — pass `model: "sonnet"` / `model: "haiku"` to the Agent tool; reserve the main opus session for orchestration and review.

**Never work in a worktree — preserve all changes:** do NOT create git worktrees (no `superpowers:using-git-worktrees`, no `.claude/worktrees/`). Work directly on a branch of the current working branch. Still assume other Claude sessions may touch the same repo concurrently, so never do anything that could discard in-flight edits (no `git checkout -- .`, no `git reset --hard`, no `git stash drop`, no force-anything, no `git add -A`) — `git add` explicit paths only, keep the tree clean, and commit each logical change so nothing is lost.

**Minimize opus output:** opus (main session) orchestrates, reviews, and decides — it generates nothing long itself. All code, docs, and file writes go to sonnet/haiku subagents; opus never writes them inline. Keep main-session prose terse: no preamble, no recap of what was just done, no surveying options you won't take — decide and act. Review subagent work as short verdicts ("line X: do Y"), not rewritten code blocks.

**Room:** entities in `app/.../models/`, DAOs in `app/.../db/`, `AppDatabase` v3 (added `drill_configs`), still `fallbackToDestructiveMigration()` — schema bump wipes local data.

**Tests:** `shared/src/commonTest` for pure-Kotlin unit tests, `shared/src/jvmTest` for fixture-driven tests (ClassLoader resource loading is JVM-only). Fixtures in `shared/src/commonTest/resources/fixtures/` — legacy v1 (MediaPipe-33) and v2 (`*_rtm.json`) coexist; load via `JsonTestUtils`/`TestFixtures` (v1) or `TestFixturesV2` (v2).

## File map (top repeat-reads for current work)

Phase 2 files land per the plan; legacy entries below are what Phase 2 reuses or adapts.

- **[Phase 2 plan](docs/superpowers/plans/2026-06-10-phase2-drill-logic-shared-kmp.md)** — task-by-task TDD plan with full code listings; the source of truth for what exists vs is pending in `shared/`.
- **[BaselineDeriver](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineDeriver.kt)** — pure KMP: strokes + analyses → `PersonalBaseline`. 2σ single-pass outlier exclusion, qualityScore = `1 − mean(CV)`, min-rep check **after** exclusion. Phase 2 extracts a public `deriveFromMetrics(...)`; the existing `derive(...)` must keep delegating to it (003 path stays green).
- **[BaselineRuleFactory](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineRuleFactory.kt)** — single source of rule derivation (`PersonalBaseline → List<BaselineRule>`: 2σ consistency, 25% rhythm). Drill feedback evaluates via [FrameRuleEvaluator](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/FrameRuleEvaluator.kt).
- **[AngleCalculations](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/AngleCalculations.kt)** — legacy MediaPipe-33/`Landmark3D` dot-product angles; frozen (feeds live pipeline). Phase 2's `AngleCalculations2D` copies the technique for `Keypoint2D` + COCO indices.
- **[StrokePhaseDetector](shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/StrokePhaseDetector.kt)** / **[JsonStrokeDetector](shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/JsonStrokeDetector.kt)** — legacy phase/stroke detection over 33-landmark frames; `StrokeDetector2D` (wrist-speed local maximum) is the 2D adaptation.
- **[TestFixtures](shared/src/jvmTest/kotlin/com/ttcoachai/shared/TestFixtures.kt)** — jvmTest fixture loader pattern (ClassLoader + regex parsing) that `TestFixturesV2`/`PoseJsonV2Parser` productionize.
- **[export_poses_rtmpose.py](scripts/poses/export_poses_rtmpose.py)** — schema-v2 producer. Any schema change must update [docs/pose_json_schema_v2.md](docs/pose_json_schema_v2.md), the KMP parser, and poses_viewer together.
- **[poses_viewer/CLAUDE.md](poses_viewer/CLAUDE.md)** — own detailed guide (file map, gotchas, conventions). Read before touching the viewer.
- **[PersonalBaseline](shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PersonalBaseline.kt)** + **[DrillConfigEntity](app/src/main/java/com/ttcoachai/models/DrillConfigEntity.kt)** — baseline model + coach-tuned drill-shape overrides (Room `drill_configs`, authored in the Phase 7 editor) that Phase 2's evaluator applies on top of derived baselines.

### Hot UI files (ui-wiring reference)
Summarized below to avoid re-reads during UI-wiring work — check here before reopening.

- **[fragment_profile.xml](app/src/main/res/layout/fragment_profile.xml)** — root `NestedScrollView#profile_scroll_view` → `LinearLayout`; key IDs: `iv_profile_image`/`tv_profile_initials` (avatar), `tv_profile_name`, `tv_profile_email`, `tv_profile_hours`, `tv_profile_streak`, `card_subscription_active`/`card_subscription_upgrade`, `tv_renewal_date`, `toggle_group_theme` + `btn_theme_light`/`btn_theme_dark`/`btn_theme_system`, `layout_edit_profile` (gone), `layout_app_settings`, `layout_help_support`, `btn_log_out`. No include/merge.
- **[DrillsFragment.kt](app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt)** — binds `FragmentDrillsBinding` (`rvDrills`, `fabAddDrill`, `sectionRecent`, `tvRecentName`/`ivRecentIcon`/`tvRecentDate`/`tvRecentAccuracy`/`btnRecentContinue`); builds built-in `Exercise` list + custom drills via `CustomDrillRepository`/`AppDatabase`; `ExerciseAdapter` (click/long-click/clone/delete/toggle-locked callbacks); long-press → `dialog_drill_menu` rows gated by `DrillActions`; launches `ExerciseEditorActivity` (new/edit/clone) via `exerciseEditorLauncher`; navigates to `TrainingActivity`. Session-level `cachedCustomExercises` companion cache avoids pop-in on refragment.
- **[styles.xml](app/src/main/res/values/styles.xml)** — base `AppTheme` (Material3 DayNight). `TTC.*` families: `TextAppearance.TTC.{Stat.Hero/Large/Medium/Small, Mono.Meta, Title.Screen/Card, Body/Body.Secondary, Eyebrow(.Gold), Nav.Label}`; `TTC.Card(.Highlighted/.GoldTint)`; `TTC.Segment{Track,.Inactive,.Active,.Button(.Paywall)}`; `TTC.Button.{Primary,Ghost,Danger}`; `TTC.Fab.Extended`; `TTC.SectionHeader(.Gold)`; `TTC.StatNumber(.Gold/.Positive)`; `TTC.TrendChip.{Positive,Negative}`; `TTC.Toggle`; `TTC.Slider`; `TTC.Chip.{Filter,Focus}`; `TtcStepper.{Button,Value}`; `TTC.Dialog.{Title.Panel,Body.Panel,Button.Positive/Neutral}` + `ThemeOverlay.TTC.Dialog`; `TTC.BottomSheet.Modal` + `ThemeOverlay.TTC.BottomSheet`; `TTC.Button.Confirm.{Cancel,Destructive,Neutral}`, `TTC.Button.Discard`.
- **[strings.xml](app/src/main/res/values/strings.xml)** — ~935 strings (EN). Groups by prefix: `exercise_*`/`cat_*`/`difficulty_*` (drill catalog), `training_*`/`btn_*` (Training), `settings_*`/`feedback_*`/`detection_*` (8a/11a/11b), `profile_*`/`subscription_*`/`premium_*` (Profile), `drills_*`/`drill_action_*` (Drills tab + long-press menu), `calibration_*`, `review_*`/`history_*`, `live_*` (1a/1e), `exercise_editor_*` (10c/10d), `dow_*`/`day_*`/`greeting_*` (Dashboard), `format_*` (shared), `placeholder_*` (debug).
- **[TrainingUIController.kt](app/src/main/java/com/ttcoachai/managers/TrainingUIController.kt)** — wraps `ActivityTrainingBinding`; wires bottom sheet (`binding.bottomSheet`, collapsed/not-hideable), `drillMenu.btnPauseResume`/`btnEndSession`/`cardFullReport`, `fab_pause_play`; `rvFeedbackList` + `FeedbackListAdapter`; cues-per-session segment (`btnCues3/5/10`) persisted via `SettingsManager`; correction chips (`chipWrist/Rotation/FollowThrough/ContactHeight/Elbow/Speed`) → `CorrectionType`; collapsible `headerFeedbackSettings`/`groupFeedbackSettingsContent`/`ivFeedbackSettingsChevron`; `updateStats()` writes `tv_hits_count`/`tv_accuracy_percent` + `drillMenu.tvTotalHits/tvAccuracy/progressDrill/tvDrillProgress/tvFlagged`; `showFeedbackExplanation` → `FeedbackExplanationSheet`. Collaborators: `TrainingActivity`, `SettingsManager`, `TrainingStateManager`.
- **[ProfileFragment.kt](app/src/main/java/com/ttcoachai/fragment/ProfileFragment.kt)** — binds `FragmentProfileBinding`; IDs: `tvProfileName`, `tvProfileEmail`, `ivProfileImage`/`tvProfileInitials` (Coil + initials fallback), `cardSubscriptionActive`/`cardSubscriptionUpgrade`, `tvRenewalDate`, `toggleGroupTheme`, `layoutAppSettings`, `layoutHelpSupport`, `btnLogOut`, `tvProfileStreak`/`tvProfileHours`, `profileScrollView` (scroll restore). Collaborators: `SettingsManager`, `AuthViewModel`(+`AuthRepository`), `CloudSyncManager`, `ProgressDataLoader`; navigates to `AppSettingsActivity`, `HelpSupportActivity`, `SubscribeActivity`, `LoginActivity` (logout).
- **[values-uk/strings.xml](app/src/main/res/values-uk/strings.xml)** — ~795 strings, Ukrainian mirror of `values/strings.xml` (same keys/prefixes; fewer entries — some newer EN strings not yet translated). Format placeholders (`%1$d`/`%s`) preserved.
- **[item_exercise.xml](app/src/main/res/layout/item_exercise.xml)** — root `com.ttcoachai.ui.SwipeRevealLayout#swipe_root` with `swipe_delete_panel` (start) + `swipe_clone_panel` (end) reveal panels, foreground `MaterialCardView#swipe_foreground` (`TTC.Card`); adapter-bound IDs: `fl_icon_container`/`iv_exercise_icon`, `tv_exercise_name`, `tv_exercise_description`, `tv_duration`, `tv_category`, `iv_chevron`.

## Gotchas — current (2D pivot)

- **All 2D geometry takes ONE xScale factor** (`ViewGeometry.xScale` = aspectRatio / cos(cameraYaw)) — never compute angles/speeds on raw normalized coords: schema v2 normalizes x and y by different axes (`x / videoWidth`, `y / videoHeight`). Multiply x-deltas by `xScale` before any trig. Synthetic tests use `xScale = 1f`. [ViewGeometry.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/models/ViewGeometry.kt)
- **Two topologies, one parser:** `"topology": "coco17"` (17 kp) vs `"halpe26"` (26 kp, `--feet` export); indices 0–16 identical. Legacy v1 (MediaPipe-33, `x,y,z,visibility`, no `schemaVersion`) is a *different format* — `PoseJsonV2Parser` rejects it explicitly; old fixtures still load via `JsonTestUtils`/`TestFixtures`.
- **Score gating:** angle functions return `null` when any required keypoint `score < 0.3` — no feedback on low-confidence frames. Don't "fix" nulls by lowering the threshold.
- **Camera yaw is per-rep, |yaw| only, from the PRE-stroke window** — player moves their feet between reps; estimating during the swing reads the player's own rotation as camera placement. `|yaw| > ~30°` → rep excluded from calibration / no feedback in analysis (`placementOk = false`), don't correct. Estimator currently saturates (90°) on non-protocol footage — see [docs/DESIGN_LIMITATIONS.md](docs/DESIGN_LIMITATIONS.md) L-25. [CameraAngleEstimator.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/CameraAngleEstimator.kt)
- **Wrist-speed peaks ≠ reps** — ~half are recovery swings on real footage. Pipeline order is detect → `ForwardStrokeFilter` (speed-dominance direction vote) → `RepFilter` (median banding); reordering or skipping silently corrupts baselines. [ForwardStrokeFilter.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilter.kt), [DrillCalibrator.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillCalibrator.kt)
- **SanityBounds drops, never coaches** — out-of-band values are tracking glitches removed from the rep; feedback always compares against the personal baseline. [SanityBounds.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/SanityBounds.kt)
- **Head-facing (facingSign) is noisy on real footage** — fine for synthetic tests; torso-lean sign and the `ForwardStrokeFilter` fallback both depend on it (L-04 still OPEN). [AngleCalculations2D.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/AngleCalculations2D.kt)
- **Trust rule:** precise degree numbers only for the 5 in-plane metrics (elbow, shoulder, knee bend, torso lean, shoulder tilt); rotational cues qualitative-only or silent. Encoded in `MetricPrecisionPolicy`.
- **`Videos/` footage was not shot to the camera-placement protocol** — fine for pipeline bring-up and mechanics tests, not for tuning reference ranges. End-to-end tests prove mechanics, not tuned thresholds.
- **commonMain has no `java.lang.Math` / no ClassLoader** — use `kotlin.math`; resource-loading fixture tests go in `jvmTest`, not `commonTest`.

## Gotchas — frozen pipeline (relevant when build breaks or for Stage 2 / Phase 3 port)

- **Live `DetectedStroke` is reconstructed, not detected** — `PoseAnalysisProcessor` tracks phase transitions and assembles the stroke at finalization; only boundary frames + `strokeDurationMs` populated, velocity/peak fields stay 0f (unused by `BaselineDeriver`). [PoseAnalysisProcessor.kt](app/src/main/java/com/ttcoachai/processors/PoseAnalysisProcessor.kt)
- **`CameraFragment` skips its own processor when hosted by a `LandmarkerListener` activity** — new such activities must join the carve-out check at [CameraFragment.kt:164](app/src/main/java/com/ttcoachai/fragment/CameraFragment.kt#L164) or you get double-processing. Current: `TrainingActivity`, `CalibrationActivity`.
- **MediaPipe landmarks are normalized [0,1] image coords**, rotation/centering in [PoseLandmarkerProcessor](app/src/main/java/com/ttcoachai/helpers/PoseLandmarkerProcessor.kt).
- **TrainingStateManager is a volatile singleton** — not safe for concurrent mutation; synchronized/coroutine-scoped updates only. [TrainingStateManager.kt:35-39](app/src/main/java/com/ttcoachai/managers/TrainingStateManager.kt#L35-L39)
- **CalibrationStateManager persists derived strokes + analyses only — no raw pose frames.** Captured-rep replay needs a separate raw-frame persistence path; `BaselinePreviewActivity` replays bundled fixtures via [AssetPoseFrameLoader](app/src/main/java/com/ttcoachai/debug/AssetPoseFrameLoader.kt).
- **Editor renders the canonical (mean) stroke, not raw frames** — [CanonicalStrokeLoader](app/src/main/java/com/ttcoachai/debug/CanonicalStrokeLoader.kt) → [MeanStrokeBuilder](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/MeanStrokeBuilder.kt); fixed 45° yaw via `PoseTransformer.DEFAULT_VIEW_CAMERA_YAW_DEG`.
- **`BaselineConverters` uses `org.json`, not kotlinx-serialization** — add explicit converters there for new Room columns. [BaselineConverters.kt](app/src/main/java/com/ttcoachai/db/BaselineConverters.kt)
- **Room uses `fallbackToDestructiveMigration()`** — schema changes wipe local DB (AppDatabase v3). Switch to explicit migrations before release. [AppDatabase.kt:26](app/src/main/java/com/ttcoachai/db/AppDatabase.kt#L26)
- **GoogleSignIn relies on `default_web_client_id`** — auto-generated by google-services plugin; init fails silently if missing. [AuthRepository.kt:32-36](app/src/main/java/com/ttcoachai/repository/AuthRepository.kt#L32-L36)
- **Ball tracking (all frozen):** BallDetectorV6 needs top-half ROI crop (full-frame drops to 26.8%); conf threshold 0.25, not 0.5; dual coord transform ROI→full-frame; V5 deprecated (6.6% vs 86.3%); TFLite GPU delegate silently falls back to CPU. [BallDetectorV6.kt](app/src/main/java/com/ttcoachai/tracking/BallDetectorV6.kt)
