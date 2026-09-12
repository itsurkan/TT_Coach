# Remove MediaPipe legacy pipeline

## For agentic workers

**REQUIRED SUB-SKILL:** Execute this plan task-by-task via `superpowers:subagent-driven-development`
(fresh subagent per task, review between tasks). Do not execute inline in the orchestrating session.

## Goal

Two competing calibration screens are both live: the legacy MediaPipe `calibration.CalibrationActivity`
(wired into drill creation via `ExerciseEditorActivity`) and the RTMPose-native
`pose.RtmposeCalibrationActivity` (wired into live training via `TrainingActivity`). Only one RTM
baseline lineage (`"forehand_drive_rtm"`) is ever read by the live drill (`TrainingActivity.loadRtmBaseline`),
so a baseline saved through the legacy path is invisible to the app that actually coaches the player.
Fix: repoint drill creation at `RtmposeCalibrationActivity`, then delete the now-fully-unreachable
MediaPipe inference pipeline (`CalibrationActivity` + a separate, previously-unnoticed dead
`CameraActivity`/`GalleryFragment`/`ActivitySettingsActivity` subtree that also depended on it),
the `com.google.mediapipe:tasks-vision` Gradle dependency, and the `.task` model asset download.

## Architecture

Before:
```
ExerciseEditorActivity --(REFERENCE_BASELINE radio)--> calibration.CalibrationActivity (MediaPipe)
                                                          -> saves baseline "forehand_shadow"
TrainingActivity --(calibration required dialog)------> pose.RtmposeCalibrationActivity (RTMPose)
                                                          -> saves baseline "forehand_drive_rtm"
TrainingActivity.loadRtmBaseline() reads ONLY "forehand_drive_rtm"
```
A baseline saved via drill creation is under a drillType the live trainer never reads. The
"Reference: Baseline" radio silently does nothing useful for RTM-eligible drills.

After:
```
ExerciseEditorActivity --(REFERENCE_BASELINE radio)--> pose.RtmposeCalibrationActivity (RTMPose)
                                                          -> saves baseline "forehand_drive_rtm"
TrainingActivity --(calibration required dialog)------> pose.RtmposeCalibrationActivity (RTMPose)
                                                          -> saves baseline "forehand_drive_rtm"
TrainingActivity.loadRtmBaseline() reads "forehand_drive_rtm"  <-- now actually populated by both entry points
```
One calibration screen, one baseline lineage, both entry points agree.

**Crux fix — the intent-contract mismatch (read this before touching code):**
`RtmposeCalibrationActivity` is *not* parameterized by drill type at all — it hardcodes
`DrillCalibrator.calibrateChecked(drillType = RtmposeDrillActivity.DRILL_TYPE, ...)`
(`RtmposeDrillActivity.DRILL_TYPE = "forehand_drive_rtm"`) and its result `Intent` carries no
extras, only `RESULT_OK`/`RESULT_CANCELED`. `TrainingActivity.loadRtmBaseline()` likewise
hardcodes the literal `"forehand_drive_rtm"` — it does **not** key off `exerciseId` or any
per-drill identifier; `isForehandRtmEligible(exerciseId)` only gates *whether* the RTM path
runs at all (forehand-family or custom drills), not *which* baseline it loads. In other words,
today's RTM system supports exactly **one global personal baseline**, not one per custom drill —
unlike the legacy `CalibrationActivity`, which is genuinely keyed by `EXTRA_DRILL_TYPE` /
`workingDrillType` (e.g. `custom_1690000000000`) and returns the saved drill type via
`RESULT_EXTRA_DRILL_TYPE`.
Therefore the correct fix is **not** "port `EXTRA_DRILL_TYPE` onto `RtmposeCalibrationActivity`" —
that would imply per-drill RTM baselines that nothing downstream (`loadRtmBaseline`) can honor,
and would silently misrepresent the feature. The correct, minimal, contract-accurate fix is:
`ExerciseEditorActivity.launchCalibration()` drops the `EXTRA_DRILL_TYPE` extra entirely and
launches `RtmposeCalibrationActivity` with a bare `Intent`, consuming the result exactly the way
`TrainingActivity` already does (result code only; on `RESULT_OK` the player now has *the* RTM
baseline, regardless of which drill they were editing). The "Reference: Baseline" radio option
becomes "use your calibrated RTM baseline" (global), not "use a baseline for this specific drill" —
which is what the live trainer already assumes. `currentBaselineId`/`baselineId` on the drill
config stays unused by this flow exactly as it is today (already dead for the legacy path too —
see Task 1 investigation note); out of scope to wire up per-drill baselines here.

## Tech Stack

Kotlin, Android (AppCompatActivity, ActivityResultContracts), Gradle. No new dependencies —
this plan only removes one (`com.google.mediapipe:tasks-vision`).

## Global Constraints

- The app must build and the drill-creation + live-training calibration flows must both work after
  **every single task** — never leave a broken build mid-plan.
- `git add` explicit paths only, never `-A`. Commit after each task.
- Before any deletion task, grep-confirm zero remaining references to the file(s) being deleted —
  do not trust the reference list in this plan as current; the working tree may have moved since
  this plan was written (see project's stale-context-snapshot lesson). If a grep turns up an
  unexpected reference, stop and re-scope that task rather than deleting anyway.
- Run `./gradlew :app:testDebugUnitTest --tests "<Class>"` after each task that touches
  `app/src/test`; run `./gradlew :app:assembleDebug` after each task that touches `app/src/main`
  or `app/build.gradle`. Do not read a red *full* `./gradlew test` as your own regression — the
  frozen legacy `MotionAnalyzerJsonTest` failure predates this work (see project CLAUDE.md);
  scope with `--tests` filters.
- No placeholders, no "TODO", no "add appropriate error handling" — every step below has the
  complete code to write.

## File Structure

```
app/src/main/java/com/ttcoachai/
  ui/ExerciseEditorActivity.kt              MODIFY (Task 1)
  calibration/                              DELETE (Task 3): CalibrationActivity.kt,
                                             CalibrationOnboardingFragment.kt,
                                             CalibrationCaptureFragment.kt,
                                             CalibrationReviewFragment.kt
  CameraActivity.kt                         DELETE (Task 4)
  ActivitySettingsActivity.kt               DELETE (Task 4)
  fragment/GalleryFragment.kt               DELETE (Task 4)
  fragment/CameraFragment.kt                DELETE (Task 5)
  managers/CameraManager.kt                 DELETE (Task 5)
  managers/CameraUIController.kt            DELETE (Task 5)
  managers/GalleryUIController.kt           DELETE (Task 4)
  managers/GalleryMediaProcessor.kt         DELETE (Task 4)
  managers/VideoPlayerManager.kt            DELETE (Task 6)
  PoseLandmarkerHelper.kt                   DELETE (Task 6)
  processors/PoseAnalysisProcessor.kt       DELETE (Task 6)
  processors/PoseAnalysisLogger.kt          DELETE (Task 6)
  helpers/PoseLandmarkerProcessor.kt        DELETE (Task 6)
  helpers/PoseLandmarkerConfig.kt           DELETE (Task 6)
  mappers/MediaPipeMapper.kt                DELETE (Task 6)
  TrainingActivity.kt                       MODIFY (Task 2 dead-code strip, Task 6 field removal)
  MainViewModel.kt                          MODIFY (Task 4 — drop MediaPipe-only members if any remain)
app/src/main/res/layout/
  activity_calibration.xml, fragment_calibration_*.xml   DELETE (Task 3)
  activity_camera.xml, activity_activity_settings.xml,
  fragment_gallery.xml (or equivalent)                   DELETE (Task 4)
app/src/main/AndroidManifest.xml            MODIFY (Task 3, Task 4 — remove <activity> entries)
app/src/androidTest/java/com/ttcoachai/calibration/CalibrationFlowTest.kt   DELETE (Task 7)
app/src/androidTest/java/com/google/mediapipe/examples/poselandmarker/
  CameraVideoModeIntegrationTest.kt         DELETE (Task 7)
app/src/test/java/com/ttcoachai/processors/PoseAnalysisProcessorVideoTest.kt DELETE (Task 7)
app/src/test/java/com/ttcoachai/TrainingActivityTest.kt                      DELETE (Task 7)
app/build.gradle                            MODIFY (Task 8 — remove tasks-vision dep)
app/download_tasks.gradle                   DELETE (Task 8)
docs/tt-coach-ai-context.md, CLAUDE.md      MODIFY (Task 9)
```

## Investigation notes (facts gathered while writing this plan — do not re-derive)

- `ExerciseEditorActivity.launchCalibration()` (`app/src/main/java/com/ttcoachai/ui/ExerciseEditorActivity.kt:343-347`):
  ```kotlin
  private fun launchCalibration() {
      val intent = Intent(this, CalibrationActivity::class.java)
          .putExtra(CalibrationActivity.EXTRA_DRILL_TYPE, workingDrillType)
      calibrationLauncher.launch(intent)
  }
  ```
  Its `calibrationLauncher` (`:62-71`) only branches on `result.resultCode`, never reads
  `RESULT_EXTRA_DRILL_TYPE` — so today's contract is already "result code only" on the consumer
  side; only the launch side needs to change.
- `TrainingActivity`'s `calibrationLauncher` (`app/src/main/java/com/ttcoachai/TrainingActivity.kt:62-65`)
  launches a bare `Intent(this, RtmposeCalibrationActivity::class.java)` with no extras and, in
  its callback, ignores the result code entirely (`retryAfterCalibration()` always re-queries the
  DB) — confirming `RtmposeCalibrationActivity` needs no input contract change.
- `RtmposeCalibrationActivity.finishRecording()` (`app/src/main/java/com/ttcoachai/pose/RtmposeCalibrationActivity.kt`,
  the `lifecycleScope.launch` block) hardcodes `drillType = RtmposeDrillActivity.DRILL_TYPE`; its
  `onResultPrimaryClicked()` sets only `RESULT_OK`/`RESULT_CANCELED`, no extras. No change needed.
- `TrainingActivity.loadRtmBaseline()` (`:193-204`) hardcodes the literal `"forehand_drive_rtm"`.
  No change needed — this is exactly the drillType `RtmposeCalibrationActivity` already saves under.
- `TrainingActivity`'s legacy video-mode fields (`useVideo: Boolean` at `:37`, read at `:83` from
  intent extra `"USE_VIDEO"`, passed into `TrainingMediaManager` at `:98`, branched on at `:168`)
  are unreachable: `grep -rn "USE_VIDEO"` across `app/src/main` other than `TrainingActivity.kt`
  itself returns nothing — no caller ever sets this extra (`DrillsFragment.kt`,
  `ExerciseSelectionActivity.kt` never set it). Confirmed dead, matches audit.
- **New finding beyond the audit:** `CameraActivity` (`app/src/main/java/com/ttcoachai/CameraActivity.kt`,
  manifest-declared at `AndroidManifest.xml:159`) has **zero** `Intent(..., CameraActivity::class.java)`
  call sites anywhere in `app/src/main` — it is a dead entry point, exactly like `RtmposeDrillActivity`.
  It hosts `GalleryFragment` (also never constructed anywhere — `grep -rn "GalleryFragment()"` is
  empty) and is the only live consumer of `ActivitySettingsActivity` (also manifest-only, zero
  `Intent` call sites), `managers/GalleryUIController.kt`, `managers/GalleryMediaProcessor.kt`,
  and (via `CameraFragment`) `managers/CameraManager.kt` / `managers/CameraUIController.kt`. This
  whole subtree is a second, previously-unnoticed dead MediaPipe screen and is safe to delete in
  this plan — it is **not** referenced by `CalibrationActivity` or `TrainingActivity`.
- `CameraFragment` (`app/src/main/java/com/ttcoachai/fragment/CameraFragment.kt`) is used by exactly
  three things: `CalibrationActivity` (Task 3 deletes this caller), `CameraActivity` (Task 4 deletes
  this caller), and `TrainingActivity`/`TrainingMediaManager` for the **legacy, dead** `useVideo`
  path only — `RtmposeTrainingController.kt:262` explicitly documents "CameraFragment is never
  attached while RTMPose mode is active". After Tasks 2–4, `CameraFragment` has zero live callers.
- `PoseLandmarkerHelper` is used by: `MainViewModel`, `ActivitySettingsActivity` (dead, Task 4),
  `TrainingActivity`/`PoseAnalysisProcessor` (dead `useVideo` path, Task 2/6), `VideoPlayerManager`
  (only called from `TrainingMediaManager`'s dead `useVideo` branch), `CalibrationActivity` (Task 3),
  `CameraManager`, `GalleryFragment`, `CameraUIController` (all dead subtree, Task 4/5). Verify
  `MainViewModel`'s usage at Task 6 time — if it is only consumed by the dead-subtree Activities,
  it becomes dead too and its MediaPipe-specific members are removed in that task, not the whole
  file (check for other non-MediaPipe members first).
- **Test-file finding beyond the audit:** `app/src/test/java/com/ttcoachai/TrainingActivityTest.kt`
  is not "mostly unrelated with one video-mode test case" — every one of its 5 `@Test` methods
  (`testCameraModeVisibilityLogic`, `testVideoModeVisibilityLogic`,
  `testSwitchingFromVideoToCameraModeLogic`, `testFragmentShouldBeReplacedOnlyInCameraMode`,
  `testBothModesCannotBeActiveSimultaneously`) tests only the dead `useVideo` boolean-visibility
  logic in isolation (no real `TrainingActivity` instance). The entire file is deletable, not just
  one case — see Task 7.

## Tasks

### Task 1 — Point drill-creation calibration at RtmposeCalibrationActivity

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/ui/ExerciseEditorActivity.kt`
- Test: manual/androidTest verification only (see Verification below) — this repo has no
  Robolectric harness (per CLAUDE.md's Commands section, JVM tests are pure-Kotlin/fixture-driven
  only), and `ExerciseEditorActivity`'s calibration launch is pure Android `Intent`/`ActivityResult`
  wiring with no extractable pure-Kotlin logic to unit-test. Do not fabricate a Robolectric test
  harness for this one wiring change — that is out of scope and would be a much bigger addition
  than this fix.

**Interfaces:**
- Consumes: `com.ttcoachai.pose.RtmposeCalibrationActivity` (no `EXTRA_*` input contract).
- Produces: `ExerciseEditorActivity.launchCalibration()` now launches `RtmposeCalibrationActivity`.

**Steps:**

1. Read the current import block and `launchCalibration()` to confirm line numbers still match
   (`grep -n "CalibrationActivity\|launchCalibration" app/src/main/java/com/ttcoachai/ui/ExerciseEditorActivity.kt`).
   If line numbers drifted, adjust the edit below to the actual location — the *content* change
   is unaffected.
2. Edit the import (near the top of the file, alongside the other `com.ttcoachai.*` imports):
   ```kotlin
   // Remove:
   import com.ttcoachai.calibration.CalibrationActivity
   // Add:
   import com.ttcoachai.pose.RtmposeCalibrationActivity
   ```
3. Replace `launchCalibration()`:
   ```kotlin
   private fun launchCalibration() {
       calibrationLauncher.launch(Intent(this, RtmposeCalibrationActivity::class.java))
   }
   ```
   (Drops the `EXTRA_DRILL_TYPE` extra per the crux-fix rationale above — `RtmposeCalibrationActivity`
   has no such input and always calibrates the single global `"forehand_drive_rtm"` baseline.)
4. Build: `./gradlew :app:assembleDebug`. Verify it succeeds (no remaining reference to the old
   import in this file).
5. Verification (manual, device required — see `run-on-phone` / `phone-screenshot` skills):
   - Launch the app, go to Drills → FAB "Add Drill" (or long-press → Edit on an existing custom
     drill), select "Reference: Baseline".
   - Confirm `RtmposeCalibrationActivity`'s camera/instructions screen opens (not the old MediaPipe
     `CalibrationActivity` onboarding screen — visually distinct, check the title string
     `R.string.rtmpose_calibration_title` vs `R.string.calibration_title`).
   - Complete or cancel calibration; confirm the "Reference: Baseline" radio reflects `RESULT_OK`/
     `RESULT_CANCELED` correctly (same logic as before, untouched).
   - Separately, start a live training session for a forehand/custom drill with no baseline yet;
     confirm `TrainingActivity`'s "calibration required" dialog still opens the same
     `RtmposeCalibrationActivity` screen (this path is unchanged by this task — verifies both
     entry points now genuinely converge on the same baseline).
6. Commit:
   ```
   git add app/src/main/java/com/ttcoachai/ui/ExerciseEditorActivity.kt
   git commit -m "fix(calibration): point drill-creation calibration at the RTM flow

   ExerciseEditorActivity's 'Reference: Baseline' option launched the legacy MediaPipe
   CalibrationActivity, which saves a baseline under a drillType TrainingActivity's RTM
   path never reads (loadRtmBaseline() only reads the fixed \"forehand_drive_rtm\" key).
   Point it at RtmposeCalibrationActivity instead so both calibration entry points agree
   on one baseline lineage."
   ```

### Task 2 — Remove TrainingActivity's dead video-mode path

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/TrainingActivity.kt`
- Modify: `app/src/main/java/com/ttcoachai/managers/TrainingMediaManager.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `TrainingActivity` no longer accepts/reads the `"USE_VIDEO"` intent extra; `PoseAnalysisProcessor`
  field and its lifecycle calls (`initializeAnalysis`, `poseAnalysisProcessor.release()` etc. —
  re-check exact call sites at execution time via
  `grep -n "poseAnalysisProcessor" app/src/main/java/com/ttcoachai/TrainingActivity.kt`) are removed;
  `TrainingMediaManager` drops its `useVideo` constructor parameter and the `playVideoWithPoseDetection`
  branch.

**Steps:**

1. Re-confirm dead-ness before editing:
   ```
   grep -rn "USE_VIDEO" app/src/main app/src/test app/src/androidTest
   ```
   Expect exactly one hit, in `TrainingActivity.kt` itself (the `getBooleanExtra` line). If any
   other caller now sets this extra, STOP — this task's premise no longer holds; re-scope with
   the user rather than deleting a now-reachable path.
2. In `TrainingActivity.kt`: remove `useVideo: Boolean` field, its assignment from
   `intent.getBooleanExtra("USE_VIDEO", false)`, the `useVideo` argument passed to
   `TrainingMediaManager(...)`, and the `if (useVideo) { ... }` branch (re-read the surrounding
   function at execution time — the audit-confirmed lines are `:37`, `:83`, `:98`, `:168`, but
   confirm current line numbers first since Task 1 may have shifted nothing here but never assume).
   Also remove the `poseAnalysisProcessor: PoseAnalysisProcessor` field and any initialization/
   release calls that exist *only* to serve the video-mode path — leave any RTM-path-relevant
   code untouched. Read the full file before editing to be certain which `poseAnalysisProcessor`
   references are video-mode-only vs. still load-bearing for something else; if any use is
   ambiguous, keep the field for now and only remove the `useVideo` branch itself in this task —
   defer `PoseAnalysisProcessor` field removal to Task 6 once its only remaining callers
   (`CalibrationActivity`) are also gone, to avoid a two-task ordering hazard.
3. In `TrainingMediaManager.kt`: remove the `useVideo: Boolean` constructor parameter, the
   `videoPlayerManager` field, and `playVideoWithPoseDetection(...)` call — re-read the file at
   execution time for exact signatures/lines (already located at
   `app/src/main/java/com/ttcoachai/managers/TrainingMediaManager.kt:17,43,50,65` in this
   investigation but confirm before editing).
4. Build: `./gradlew :app:assembleDebug`.
5. Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.TrainingActivityTest"` — expect
   this to now FAIL TO COMPILE if it references the removed `useVideo` field directly, or to pass
   trivially if (per the investigation note above) it never touched a real `TrainingActivity`
   instance. Either way, do not "fix" it here — Task 7 deletes this whole file; if it fails to
   compile, temporarily confirm the failure is exactly the expected removed-symbol error, then
   proceed (Task 7 lands immediately after this in execution order per this plan's task list, so
   the compile break is short-lived within the plan but must not be left broken across a commit —
   if the file fails to compile, pull Task 7's deletion of `TrainingActivityTest.kt` forward into
   this task's commit instead of leaving a broken build).
6. Commit (folding in `TrainingActivityTest.kt` deletion if step 5 required it):
   ```
   git add app/src/main/java/com/ttcoachai/TrainingActivity.kt app/src/main/java/com/ttcoachai/managers/TrainingMediaManager.kt
   git commit -m "refactor(training): remove dead USE_VIDEO/legacy-video-mode path

   Nothing in the UI ever sets the USE_VIDEO intent extra (confirmed via grep across
   app/src/main) — this branch of TrainingActivity has been unreachable dead code since
   the RTMPose live path shipped."
   ```

### Task 3 — Delete legacy CalibrationActivity and its fragments

**Files:**
- Delete: `app/src/main/java/com/ttcoachai/calibration/CalibrationActivity.kt`,
  `CalibrationOnboardingFragment.kt`, `CalibrationCaptureFragment.kt`, `CalibrationReviewFragment.kt`
  (list all files in `app/src/main/java/com/ttcoachai/calibration/` at execution time — the
  investigation above only opened `CalibrationActivity.kt`; confirm the fragment file names via
  `ls app/src/main/java/com/ttcoachai/calibration/`)
- Delete: matching layout XML (`grep -rln "activity_calibration\|calibration_onboarding\|calibration_capture\|calibration_review" app/src/main/res/layout`)
- Modify: `app/src/main/AndroidManifest.xml` (remove the `.calibration.CalibrationActivity`
  `<activity>` entry at the line found via `grep -n "CalibrationActivity" app/src/main/AndroidManifest.xml`)

**Interfaces:**
- Consumes: nothing (Task 1 already removed the only live caller).
- Produces: `com.ttcoachai.calibration` package no longer exists.

**Steps:**

1. Grep-confirm zero remaining references before deleting anything:
   ```
   grep -rn "calibration.CalibrationActivity\|CalibrationOnboardingFragment\|CalibrationCaptureFragment\|CalibrationReviewFragment" app/src/main app/src/test
   ```
   Expect zero hits in `app/src/main` (Task 1 removed the only one). `app/src/androidTest` will
   still hit `CalibrationFlowTest.kt` — that's expected, handled in Task 7, not blocking here since
   androidTest isn't compiled by `:app:assembleDebug`.
2. Delete the four Kotlin files and their layout XML counterparts (exact filenames from step-1
   `ls`, since this investigation didn't enumerate the fragment files individually).
3. Remove the `<activity android:name=".calibration.CalibrationActivity" ...>` block from
   `AndroidManifest.xml`.
4. Build: `./gradlew :app:assembleDebug`. This is expected to reveal any remaining reference this
   plan's grep missed (e.g. a string resource ID shared with another screen) — fix forward, don't
   guess ahead of the compiler.
5. Commit:
   ```
   git add app/src/main/java/com/ttcoachai/calibration app/src/main/res/layout app/src/main/AndroidManifest.xml
   git commit -m "chore(mediapipe): delete unreachable legacy CalibrationActivity

   RtmposeCalibrationActivity (Task 1) is now the only calibration entry point; the
   MediaPipe CalibrationActivity and its onboarding/capture/review fragments have zero
   remaining callers."
   ```

### Task 4 — Delete dead CameraActivity/GalleryFragment/ActivitySettingsActivity subtree

**Files:**
- Delete: `app/src/main/java/com/ttcoachai/CameraActivity.kt`,
  `app/src/main/java/com/ttcoachai/ActivitySettingsActivity.kt`,
  `app/src/main/java/com/ttcoachai/fragment/GalleryFragment.kt`,
  `app/src/main/java/com/ttcoachai/managers/GalleryUIController.kt`,
  `app/src/main/java/com/ttcoachai/managers/GalleryMediaProcessor.kt`
- Delete: matching layout XML (`grep -rln "activity_camera\|activity_activity_settings\|fragment_gallery" app/src/main/res/layout`)
- Modify: `app/src/main/AndroidManifest.xml` (remove `.CameraActivity` and
  `.ActivitySettingsActivity` entries, lines found at investigation time to be `:159` and `:96`
  respectively — re-grep, don't trust these numbers after Task 3's edits shift the file)
- Modify: `app/src/main/java/com/ttcoachai/MainViewModel.kt` — only if it has MediaPipe-only
  members with no other caller after this deletion; re-check with
  `grep -n "PoseLandmarkerHelper" app/src/main/java/com/ttcoachai/MainViewModel.kt` and read
  the surrounding function to decide whether to strip members or find `MainViewModel` itself is
  now entirely dead (delete the file) or still used for non-MediaPipe state (keep, strip only the
  MediaPipe-specific members).

**Interfaces:**
- Consumes: nothing (this subtree was already unreachable before this plan).
- Produces: `CameraActivity`, `GalleryFragment`, `ActivitySettingsActivity` and their two Gallery
  managers no longer exist.

**Steps:**

1. Grep-confirm dead-ness (re-run — do not trust this plan's snapshot):
   ```
   grep -rn "Intent(.*CameraActivity::class\|CameraActivity::class.java" app/src/main
   grep -rn "GalleryFragment()" app/src/main
   grep -rn "Intent(.*ActivitySettingsActivity::class\|ActivitySettingsActivity::class.java" app/src/main
   ```
   All three must return zero hits (other than the classes' own file/manifest declarations). If
   any hit turns up, STOP — that screen is reachable after all; drop it from this task's deletion
   list and only delete the parts confirmed dead.
2. Delete the five Kotlin files and their layout XML.
3. Remove the two `<activity>` entries from `AndroidManifest.xml`.
4. Handle `MainViewModel.kt` per the file-list note above (strip MediaPipe-only members, or delete
   the file entirely if now empty of purpose — confirm by reading it fully first).
5. Build: `./gradlew :app:assembleDebug`.
6. Commit:
   ```
   git add app/src/main/java/com/ttcoachai/CameraActivity.kt app/src/main/java/com/ttcoachai/ActivitySettingsActivity.kt app/src/main/java/com/ttcoachai/fragment/GalleryFragment.kt app/src/main/java/com/ttcoachai/managers/GalleryUIController.kt app/src/main/java/com/ttcoachai/managers/GalleryMediaProcessor.kt app/src/main/res/layout app/src/main/AndroidManifest.xml app/src/main/java/com/ttcoachai/MainViewModel.kt
   git commit -m "chore(mediapipe): delete dead CameraActivity/GalleryFragment subtree

   CameraActivity and ActivitySettingsActivity are manifest-declared but never launched
   by any Intent in the app; GalleryFragment is never constructed. This second MediaPipe
   screen was already unreachable independent of the CalibrationActivity removal."
   ```

### Task 5 — Delete CameraFragment and its CameraX managers

**Files:**
- Delete: `app/src/main/java/com/ttcoachai/fragment/CameraFragment.kt`,
  `app/src/main/java/com/ttcoachai/managers/CameraManager.kt`,
  `app/src/main/java/com/ttcoachai/managers/CameraUIController.kt`
- Delete: matching layout XML (`grep -rln "fragment_camera" app/src/main/res/layout`)

**Interfaces:**
- Consumes: nothing (Tasks 2–4 removed every caller: `CalibrationActivity`, `CameraActivity`, and
  `TrainingActivity`'s dead `useVideo` branch).

**Steps:**

1. Grep-confirm:
   ```
   grep -rln "CameraFragment" app/src/main
   ```
   Expect only `CameraFragment.kt`, `CameraManager.kt`, `CameraUIController.kt` themselves, and
   `RtmposeTrainingController.kt`'s comment reference (a comment, not code — confirm with
   `grep -n "CameraFragment" app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt`
   that it's inside a `//` comment, not an import/call). If any non-comment hit remains outside
   the three files being deleted, STOP and re-scope.
2. Delete the three files and layout XML.
3. Build: `./gradlew :app:assembleDebug`.
4. Commit:
   ```
   git add app/src/main/java/com/ttcoachai/fragment/CameraFragment.kt app/src/main/java/com/ttcoachai/managers/CameraManager.kt app/src/main/java/com/ttcoachai/managers/CameraUIController.kt app/src/main/res/layout
   git commit -m "chore(mediapipe): delete CameraFragment and its CameraX managers

   Zero remaining callers after CalibrationActivity/CameraActivity/TrainingActivity's
   dead video-mode path are gone; RTMPose's own camera binding (RtmposeFrameProcessor)
   never used this fragment."
   ```

### Task 6 — Delete MediaPipe inference core

**Files:**
- Delete: `app/src/main/java/com/ttcoachai/PoseLandmarkerHelper.kt`,
  `app/src/main/java/com/ttcoachai/processors/PoseAnalysisProcessor.kt`,
  `app/src/main/java/com/ttcoachai/processors/PoseAnalysisLogger.kt`,
  `app/src/main/java/com/ttcoachai/helpers/PoseLandmarkerProcessor.kt`,
  `app/src/main/java/com/ttcoachai/helpers/PoseLandmarkerConfig.kt`,
  `app/src/main/java/com/ttcoachai/mappers/MediaPipeMapper.kt`,
  `app/src/main/java/com/ttcoachai/managers/VideoPlayerManager.kt`
- Modify: `app/src/main/java/com/ttcoachai/TrainingActivity.kt` (remove the deferred
  `poseAnalysisProcessor` field from Task 2, if not already removed there)
- Modify: `app/src/main/java/com/ttcoachai/managers/CalibrationStateManager.kt` — check whether
  its `PoseAnalysisProcessor` reference (found in investigation) is a real dependency or just a
  KDoc mention; re-grep at execution time.
- Modify: `app/src/main/java/com/ttcoachai/services/MotionAnalyzer.kt`,
  `app/src/main/java/com/ttcoachai/processors/StrokePhaseDetector.kt` — both reference
  `MediaPipeMapper`; read them fully to determine whether MediaPipe types are load-bearing there
  (these are pre-2D-pivot legacy files feeding the frozen 3D pipeline, not the RTM path — deleting
  `MediaPipeMapper` may cascade further than this plan anticipated; if so, STOP this task, do not
  improvise a larger deletion, and report back for re-scoping rather than guessing).

**Interfaces:**
- Consumes: nothing after Tasks 2–5.

**Steps:**

1. Grep-confirm each file's remaining callers individually before deleting any of them (they don't
   all share one blast radius):
   ```
   grep -rln "PoseLandmarkerHelper" app/src/main
   grep -rln "PoseAnalysisProcessor\b" app/src/main
   grep -rln "PoseAnalysisLogger" app/src/main
   grep -rln "PoseLandmarkerProcessor\b" app/src/main
   grep -rln "PoseLandmarkerConfig" app/src/main
   grep -rln "MediaPipeMapper" app/src/main
   grep -rln "VideoPlayerManager" app/src/main
   ```
   `PoseLandmarkerProcessor` (`helpers/`) is used by `RtmposeFrameProcessor.kt` and
   `RtmposeTrainingController.kt` per this investigation — re-verify at execution time whether that
   is the *legacy MediaPipe* `PoseLandmarkerProcessor` class or an unrelated same-named RTM type;
   if the RTM path genuinely depends on it, **do not delete it** — drop it from this task's list
   and note the correction in the task's commit message.
2. Delete only the files confirmed dead by step 1's greps (each file's only remaining references
   being itself + files already deleted in Tasks 2–5).
3. Remove any now-dangling field/import left in `TrainingActivity.kt` / `CalibrationStateManager.kt`
   from the deletions.
4. Build: `./gradlew :app:assembleDebug`.
5. Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.processors.*"` and any other
   test classes touching deleted symbols, to catch compile breaks before the full-suite run in
   Task 7.
6. Commit (message reflects exactly which files were actually deletable per step 1's findings —
   do not claim files were deleted if step 1 pulled them out of scope):
   ```
   git add <files actually deleted/modified>
   git commit -m "chore(mediapipe): delete MediaPipe inference core

   PoseLandmarkerHelper, PoseAnalysisProcessor and friends have zero remaining callers
   now that CalibrationActivity, CameraActivity, CameraFragment and TrainingActivity's
   dead video-mode path are gone."
   ```

### Task 7 — Delete orphaned tests

**Files:**
- Delete: `app/src/androidTest/java/com/ttcoachai/calibration/CalibrationFlowTest.kt`
- Delete: `app/src/androidTest/java/com/google/mediapipe/examples/poselandmarker/CameraVideoModeIntegrationTest.kt`
- Delete: `app/src/test/java/com/ttcoachai/processors/PoseAnalysisProcessorVideoTest.kt`
- Delete: `app/src/test/java/com/ttcoachai/TrainingActivityTest.kt` (whole file — per the
  investigation note above, every `@Test` in it exercises only the dead `useVideo` boolean logic
  in isolation, not a real `TrainingActivity`; there is no non-video-mode test to preserve. If a
  re-read at execution time finds a test that is NOT about `useVideo`, keep that one test and only
  delete the video-mode ones.)

**Interfaces:** none — pure test cleanup.

**Steps:**

1. Re-read each file fully before deleting, to reconfirm the "whole file is MediaPipe/video-mode
   only" claim (already done for `TrainingActivityTest.kt` in this investigation; do the same gut
   check for the other three at execution time — they were not fully read during this plan's
   investigation, only located by grep).
2. Delete the four files (or the applicable subset per step 1).
3. Run: `./gradlew :app:testDebugUnitTest` (full app JVM suite this time, now that MediaPipe test
   files are gone) — expect this to be green modulo the pre-existing unrelated
   `MotionAnalyzerJsonTest` failure noted in project CLAUDE.md; confirm that failure is the *only*
   red test, don't rubber-stamp a red suite.
4. Commit:
   ```
   git add app/src/androidTest/java/com/ttcoachai/calibration/CalibrationFlowTest.kt app/src/androidTest/java/com/google/mediapipe app/src/test/java/com/ttcoachai/processors/PoseAnalysisProcessorVideoTest.kt app/src/test/java/com/ttcoachai/TrainingActivityTest.kt
   git commit -m "test(mediapipe): delete orphaned tests for deleted MediaPipe code"
   ```

### Task 8 — Remove Gradle dependency and asset download

**Files:**
- Modify: `app/build.gradle` (remove line ~140:
  `implementation 'com.google.mediapipe:tasks-vision:0.10.14'`; remove the
  `apply from: 'download_tasks.gradle'` line found at `:86`)
- Delete: `app/download_tasks.gradle`
- Delete downloaded assets if present under version control:
  `find app/src/main/assets -iname "pose_landmarker_*.task"` — delete any tracked files found
  (they may be gitignored/downloaded-at-build-time only; check `git status`/`.gitignore` before
  assuming they need a `git rm`).

**Interfaces:** none.

**Steps:**

1. Grep-confirm zero remaining `import com.google.mediapipe` anywhere in `app/src/main` and
   `app/src/test`/`app/src/androidTest`:
   ```
   grep -rln "com.google.mediapipe" app/src/main app/src/test app/src/androidTest
   ```
   Expect zero hits (Tasks 3–7 removed every consumer). If any remain, STOP — do not remove the
   Gradle dependency out from under still-compiling code.
2. Remove the `tasks-vision` dependency line and the `apply from: 'download_tasks.gradle'` line
   from `app/build.gradle`.
3. Delete `app/download_tasks.gradle`.
4. Check for tracked `.task` model assets and remove if git-tracked (skip if gitignored/build-time
   only — inspect `git status` output, don't blind-delete from disk if they'll just regenerate and
   pollute the working tree with an untracked-vs-deleted mismatch).
5. Build clean: `./gradlew clean :app:assembleDebug` (a clean build matters here specifically
   because `download_tasks.gradle` ran at configuration time — a stale build cache could mask a
   missing-dependency break).
6. Commit:
   ```
   git add app/build.gradle
   git commit -m "chore(mediapipe): remove tasks-vision dependency and model asset download

   Nothing in the app imports com.google.mediapipe anymore."
   ```
   (Add `app/download_tasks.gradle` deletion and any removed asset files to the same commit.)

### Task 9 — Update CLAUDE.md and docs

**Files:**
- Modify: `/Users/itsurkan/Dev/personal/TT_Coach/CLAUDE.md`
- Modify: `docs/tt-coach-ai-context.md` if it references the MediaPipe pipeline as frozen/present
  (check first: `grep -n "MediaPipe" docs/tt-coach-ai-context.md`)

**Steps:**

1. In CLAUDE.md's "Current direction — 2D PIVOT" section, edit the "Frozen, not deleted" sentence:
   remove "the live MediaPipe pipeline in `app/` (superseded by the Phase 3 RTMPose backend in
   `app/.../pose/`, kept frozen)" — it is now deleted, not frozen. Keep the rest of that sentence
   (`BallDetectorV1–V6`, `ROIManager`, trajectory code, audio-contact scripts, YOLO training) as-is
   — those are still genuinely frozen, this plan does not touch them.
2. Delete or rewrite the entire "## Gotchas — frozen legacy pipeline" section — every bullet in it
   (`CameraFragment`, `PoseLandmarkerHelper`, `TrainingStateManager` MediaPipe note if applicable,
   `AppDatabase`/Room fallback note if MediaPipe-specific, `MediaPipe landmarks are normalized`
   bullet, ball-tracking bullets) — re-read the section fully at execution time and keep only
   bullets that are NOT about the now-deleted files (e.g. the ball-tracking bullets and Room
   migration bullet are unrelated to MediaPipe pose inference and should stay; the
   `PoseLandmarkerProcessor`/`CameraFragment`/`PoseAnalysisProcessor`-specific bullets should be
   removed).
3. Search for any other CLAUDE.md mention of "MediaPipe" or "frozen" that references the deleted
   pipeline (`grep -n "MediaPipe\|frozen" CLAUDE.md`) and update each — do not do a blind
   find-replace, read each hit's context (some "frozen" mentions are about ball-tracking/YOLO,
   which stay frozen and unmodified).
4. Add a one-line dated entry near the top of CLAUDE.md's phase-status area (or as its own short
   subsection, following the file's existing convention for shipped work — see "Pose data upload
   (shipped 2026-07-23)" and "RTM correction taxonomy (shipped 2026-07-23)" as the pattern to
   match) documenting: the legacy MediaPipe calibration/inference pipeline was deleted (not
   frozen) on this date, `ExerciseEditorActivity` now calibrates through `RtmposeCalibrationActivity`,
   and the RTM baseline model is a single global baseline (not per-drill) — call out that this is
   a known limitation for future per-drill-baseline work, not an oversight.
5. Update `docs/tt-coach-ai-context.md` only if step-1's grep found live references to the deleted
   pipeline as current-state (not historical) — if all hits are clearly historical/rationale
   (RTMPose vs MediaPipe comparison), leave it untouched; that's accurate history, not a stale
   claim.
6. Commit:
   ```
   git add CLAUDE.md docs/tt-coach-ai-context.md
   git commit -m "docs(claude): record that the MediaPipe legacy pipeline was deleted, not frozen"
   ```

### Task 10 — Final verification

**Steps:**

1. `./gradlew clean :app:assembleDebug` — full clean build green.
2. `./gradlew :app:testDebugUnitTest` — green modulo the pre-existing unrelated
   `MotionAnalyzerJsonTest` failure (confirm it's the only red test).
3. `./gradlew :shared:jvmTest` — unaffected by this plan, confirm still green as a sanity check
   nothing in `shared/` accidentally referenced `app/`-side MediaPipe code (it shouldn't; `shared/`
   has zero external deps per repo convention).
4. Device verification (use the `run-on-phone` skill to build+install+launch, then
   `phone-screenshot` to capture): repeat Task 1's manual verification end-to-end now that the
   legacy screen no longer exists at all — confirm drill creation's "Reference: Baseline" opens
   `RtmposeCalibrationActivity`, completes a calibration, and that a subsequent live training
   session for that drill picks up the saved baseline without hitting the "calibration required"
   dialog again.
5. `grep -rn "com.google.mediapipe" app/src` — zero hits anywhere (main, test, androidTest).
6. Report completion; do not merge to main without the user's explicit "merge" trigger per this
   project's standing instruction (merge-to-main is automatic once work is done and verified —
   confirm tests/build are green first, per Global Constraints, before triggering that).

## Self-Review Checklist

- **Spec coverage vs. findings above:**
  - ExerciseEditorActivity → RtmposeCalibrationActivity: Task 1. ✓
  - Intent-contract mismatch resolved: crux-fix section + Task 1 (drop `EXTRA_DRILL_TYPE`, no
    `RtmposeCalibrationActivity` change needed — documented why). ✓
  - `TrainingActivity`'s dead `useVideo`/`PoseAnalysisProcessor` field: Task 2. ✓
  - `CalibrationActivity` + dependents deletion, one task at a time with grep-confirm: Tasks 3–6,
    each independently grep-gated. ✓
  - Orphaned tests: Task 7, corrected scope (whole `TrainingActivityTest.kt`, not one case) per
    fresh investigation. ✓
  - Gradle dependency + asset download: Task 8. ✓
  - CLAUDE.md update: Task 9, scoped to actual remaining mentions, not blind replace. ✓
  - Task ordering keeps build green at every step: Task 1 (pure repoint) → Task 2 (dead-code
    strip, self-contained) → Tasks 3–6 (deletions, each grep-gated immediately before its own
    edit) → Task 7 (tests) → Task 8 (Gradle) → Task 9 (docs) → Task 10 (verify). ✓
  - Device/manual verification called out explicitly (Task 1 step 5, Task 10 step 4) since this
    is Android UI flow that JVM tests can't cover. ✓
  - `RtmposeDrillActivity` dead-code note: explicitly marked out of scope per the original ask
    (mentioned only for context in the crux section, not a task). ✓
- **New findings beyond the original audit, incorporated:** the dead `CameraActivity`/
  `GalleryFragment`/`ActivitySettingsActivity` subtree (Task 4, new) and the whole-file (not
  partial) dead-ness of `TrainingActivityTest.kt` (Task 7) are both flagged as corrections to the
  audit's assumptions, with the evidence gathered inline rather than asserted.
- **Placeholder scan:** no "TODO", no "add appropriate error handling", no "similar to Task N"
  as astand-in for real code — every code block is complete Kotlin/Gradle/grep. Several steps
  explicitly say "re-grep at execution time, don't trust this plan's line numbers/file lists" —
  that is a deliberate staleness guard (per this project's stale-context-snapshot lesson), not a
  placeholder; the actual deletion/edit action is still fully specified conditionally on that
  grep's outcome.
- **Type/signature consistency across tasks:** `RtmposeCalibrationActivity`'s no-arg launch
  contract (Task 1) matches how `TrainingActivity` already launches it (confirmed identical in
  Investigation notes) — no divergent contract introduced. `PoseAnalysisProcessor` field removal
  is deliberately split across Task 2 (defer) and Task 6 (finish) to avoid a two-task ordering
  hazard where Task 2 alone would break the build if `CalibrationActivity` (deleted in Task 3)
  still held a reference — documented explicitly in Task 2 step 2.
