# Finish-to-Session-Summary Flow — Implementation Plan

Spec: [docs/superpowers/specs/2026-07-03-finish-to-session-summary-flow-design.md](../specs/2026-07-03-finish-to-session-summary-flow-design.md)

## Goal

Replace the old `TrainingUIController.showSummary(...)` Material popup at the end of a
training session with navigation to the existing `SessionReviewFragment` screen. `Finish &
Save` closes `TrainingActivity` immediately (does not block on the async save); once the
session + analytics are persisted, `MainActivity` navigates to
`SessionReviewFragment(sessionId)` exactly once. Discard and save-skipped paths (session
`< 5s`, not authenticated) toast and return to whatever `MainActivity` was showing, with no
summary.

## Architecture

- **Signal:** `TTCoachApplication.pendingReviewSessionId: MutableStateFlow<String?>` — an
  application-scoped one-shot observable. `TrainingActivity`'s `onSaved` callback (already
  running on an app-scoped `CloudSyncManager` coroutine that outlives the activity) sets it;
  `MainActivity` collects it for its started lifetime and consumes it (resets to `null`) after
  navigating.
- **Why a StateFlow and not a plain field / intent flag:** see spec "Mechanism" section —
  `finish()` happens before the async `onSaved` fires, so a synchronous read in `onResume`
  could miss the value; a collected `StateFlow` catches it whenever it lands. Intent-based
  deep-linking was rejected as strictly more complex (see spec).
- **No new persistence, no new pure-logic module.** This is presentation/navigation wiring
  confined to `app/`; `shared/` is untouched.

## Tech Stack

- Kotlin, AndroidX Navigation Component (`androidx.navigation:navigation-fragment-ktx`), AndroidX
  Lifecycle (`lifecycleScope`, `repeatOnLifecycle` — already used elsewhere, e.g.
  `app/src/main/java/com/ttcoachai/calibration/CalibrationCaptureFragment.kt:51-52`; no new
  Gradle dependency needed, see Task 0).
- kotlinx-coroutines-core / -android 1.10.2 (already in `app/build.gradle:136-137`) for
  `MutableStateFlow`; the `MutableStateFlow`/`StateFlow`/`asStateFlow()` pattern is already
  established in `app/src/main/java/com/ttcoachai/managers/CloudSyncManager.kt:18-19,41-45` and
  `app/src/main/java/com/ttcoachai/managers/CalibrationStateManager.kt:7-11,50-54`.

## Global Constraints

- No pose/ball/pipeline code touched. Changes confined to `app/src/main/java/com/ttcoachai/`
  (`TTCoachApplication.kt`, `MainActivity.kt`, `TrainingActivity.kt`,
  `managers/TrainingUIController.kt`) plus a possible strings-only cleanup in Task 4.
- No unit tests to write — this is Android UI/lifecycle wiring with no pure-logic surface (per
  spec "Testing" section). Every task verifies via `./gradlew :app:compileDebugKotlin`; the
  final task additionally verifies on-device per the spec's manual checklist.
- Follow repo convention: work in a git worktree off the current working branch, commit each
  logical change, cherry-pick specific commits back when green (see project `CLAUDE.md` —
  plan isolation / integrate-via-cherry-pick). Execute via
  `superpowers:subagent-driven-development`, fresh subagent per task, review between tasks,
  on `sonnet`.
- `git add` explicit paths only — never `-A` or `.` (working tree carries unrelated build
  artifacts).
- Commit after each task (repo convention: commit per logical change, don't batch).

---

## Task 0 — Confirm lifecycle APIs are available (no-op verification task)

No code change. `repeatOnLifecycle`, `Lifecycle.State`, and `lifecycleScope` are already used
in this module (e.g. `app/src/main/java/com/ttcoachai/calibration/CalibrationCaptureFragment.kt:9-10,51-52`
and `app/src/main/java/com/ttcoachai/fragment/DashboardFragment.kt:8,99`), transitively pulled
in via `androidx.fragment:fragment-ktx:1.8.5` and `androidx.camera:camera-lifecycle` in
`app/build.gradle`. There is no explicit `androidx.lifecycle:lifecycle-runtime-ktx` line in
`app/build.gradle`, but since the symbols already resolve and compile elsewhere in this exact
module, no new dependency is required for `MainActivity` to use them too.

**Verify:**
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL (confirms current baseline compiles before changes start).

No commit for this task (no file changes).

---

## Task 1 — Add `pendingReviewSessionId` to `TTCoachApplication`

**File:** `app/src/main/java/com/ttcoachai/TTCoachApplication.kt`

Add the import and the property. Current imports end at line 14
(`kotlinx.coroutines.runBlocking`); the class body's manager properties end at line 40
(`sessionAnalyticsRecorder`), right before `attachBaseContext` at line 42.

Add import after line 14:
```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
```

Add property after the `sessionAnalyticsRecorder` block (after line 40, before line 42's
blank line / `override fun attachBaseContext`):
```kotlin
    val sessionAnalyticsRecorder: com.ttcoachai.managers.SessionAnalyticsRecorder by lazy {
        com.ttcoachai.managers.SessionAnalyticsRecorder(database.sessionAnalyticsDao())
    }

    // One-shot signal: set by TrainingActivity's async save-completion callback, consumed by
    // MainActivity to navigate to SessionReviewFragment exactly once. See
    // docs/superpowers/specs/2026-07-03-finish-to-session-summary-flow-design.md.
    val pendingReviewSessionId = MutableStateFlow<String?>(null)

    override fun attachBaseContext(base: Context) {
```

**Verify:**
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

**Commit:**
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && git add app/src/main/java/com/ttcoachai/TTCoachApplication.kt && git commit -m "$(cat <<'EOF'
feat(app): add pendingReviewSessionId signal to TTCoachApplication

One-shot StateFlow so TrainingActivity's async save-completion callback
can hand off the saved sessionId to MainActivity for post-finish
navigation to SessionReviewFragment.
EOF
)"
```

---

## Task 2 — Collect the signal in `MainActivity` and navigate

**File:** `app/src/main/java/com/ttcoachai/MainActivity.kt`

Current imports (lines 19-22):
```kotlin
import android.os.Bundle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.ttcoachai.databinding.ActivityMainBinding
```

Replace with:
```kotlin
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.ttcoachai.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
```

Current end of `onCreate` (lines 47-56):
```kotlin
        // Keep the Settings/Progress tabs highlighted on their pushed child screens.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_feedback, R.id.navigation_detection ->
                    binding.navView.menu.findItem(R.id.navigation_settings)?.isChecked = true
                R.id.navigation_session_history, R.id.navigation_session_review ->
                    binding.navView.menu.findItem(R.id.navigation_progress)?.isChecked = true
            }
        }
    }
```

Append the collector after the `addOnDestinationChangedListener` block, still inside
`onCreate`:
```kotlin
        // Keep the Settings/Progress tabs highlighted on their pushed child screens.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_feedback, R.id.navigation_detection ->
                    binding.navView.menu.findItem(R.id.navigation_settings)?.isChecked = true
                R.id.navigation_session_history, R.id.navigation_session_review ->
                    binding.navView.menu.findItem(R.id.navigation_progress)?.isChecked = true
            }
        }

        // Finish-to-summary handoff: TrainingActivity's async save-completion callback sets
        // this on the Application; navigate to the session review screen exactly once, then
        // consume (reset to null) so backing out of review doesn't re-trigger it. See
        // docs/superpowers/specs/2026-07-03-finish-to-session-summary-flow-design.md.
        val app = application as TTCoachApplication
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.pendingReviewSessionId.collectLatest { sessionId ->
                    if (sessionId != null) {
                        navController.navigate(
                            R.id.navigation_session_review,
                            Bundle().apply { putString("sessionId", sessionId) }
                        )
                        app.pendingReviewSessionId.value = null
                    }
                }
            }
        }
    }
```

Note: `collectLatest` is used (matches the coroutines flow APIs already imported project-wide)
over plain `collect` — behavior is equivalent here since the flow only ever carries a single
value per emission and the body is synchronous/non-suspending before the reset.

**Verify:**
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

**Commit:**
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && git add app/src/main/java/com/ttcoachai/MainActivity.kt && git commit -m "$(cat <<'EOF'
feat(app): navigate to SessionReviewFragment on pendingReviewSessionId

MainActivity collects TTCoachApplication.pendingReviewSessionId for its
started lifetime and navigates to navigation_session_review exactly once
per signal, consuming it immediately after.
EOF
)"
```

---

## Task 3 — Rewrite `TrainingActivity.stopTraining` to finish immediately and drop the popup

**File:** `app/src/main/java/com/ttcoachai/TrainingActivity.kt`

### 3a. `stopTraining` (lines 132-147)

Before:
```kotlin
    private fun stopTraining(discard: Boolean = false) {
        stateManager.stopTraining()
        uiController.updateUIForTrainingState(false)
        poseAnalysisProcessor.endSession()
        
        if (discard) {
            android.widget.Toast.makeText(this, R.string.session_discarded, android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Save training session to cloud
        saveSessionToCloud()
        
        uiController.showSummary(stateManager.getSummaryText(), stateManager.getImprovementTip())
    }
```

After:
```kotlin
    private fun stopTraining(discard: Boolean = false) {
        stateManager.stopTraining()
        uiController.updateUIForTrainingState(false)
        poseAnalysisProcessor.endSession()
        
        if (discard) {
            android.widget.Toast.makeText(this, R.string.session_discarded, android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Save training session to cloud (async, app-scoped — survives this finish()).
        // On success, onSaved (inside saveSessionToCloud) sets
        // TTCoachApplication.pendingReviewSessionId, which MainActivity picks up and
        // navigates to SessionReviewFragment. Close this screen immediately rather than
        // waiting on the save.
        saveSessionToCloud()
        finish()
    }
```

Note: the too-short-session toast-and-skip stays inside `saveSessionToCloud` (see 3b) — it
still fires (Toast survives briefly after `finish()` on the same UI thread/Looper since the
activity is only finishing, not destroyed synchronously), and since `onSaved` is never
invoked on that path, `pendingReviewSessionId` is never set, matching the spec's "no summary"
requirement for both the too-short and not-authenticated cases.

### 3b. `saveSessionToCloud` / `onSaved` (lines 149-189) — only the `onSaved` lambda changes

Before (lines 181-187):
```kotlin
            onSaved = { sessionId ->
                app.sessionAnalyticsRecorder.record(
                    sessionId = sessionId,
                    results = stateManager.getAnalysisResults(),
                    feedback = stateManager.getLatestFeedbackItems()
                )
            }
```

After:
```kotlin
            onSaved = { sessionId ->
                app.sessionAnalyticsRecorder.record(
                    sessionId = sessionId,
                    results = stateManager.getAnalysisResults(),
                    feedback = stateManager.getLatestFeedbackItems()
                )
                app.pendingReviewSessionId.value = sessionId
            }
```

The rest of `saveSessionToCloud` (lines 149-180, 188-189) is unchanged — the `durationSeconds
< 5` guard at lines 161-165 still returns early with its existing toast, before
`cloudSyncManager.saveTrainingFromState(...)` is even called, so `onSaved` never fires and
`pendingReviewSessionId` is never touched on that path. Likewise `CloudSyncManager
.saveTrainingFromState` already skips firing `onSaved` when not authenticated (per spec,
existing behavior, no change needed here).

**Verify:**
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

**Commit:**
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && git add app/src/main/java/com/ttcoachai/TrainingActivity.kt && git commit -m "$(cat <<'EOF'
feat(training): finish immediately on save, hand off sessionId for review

stopTraining no longer waits on the cloud save or shows the popup
summary; it calls finish() right away. The existing onSaved callback
(app-scoped, survives finish()) now also sets
TTCoachApplication.pendingReviewSessionId so MainActivity can navigate
to SessionReviewFragment once the session and analytics are persisted.
EOF
)"
```

---

## Task 4 — Remove `TrainingUIController.showSummary` and check for now-dead summary code

**File:** `app/src/main/java/com/ttcoachai/managers/TrainingUIController.kt`

Remove the `showSummary` function (lines 108-115):
```kotlin
    fun showSummary(summary: String, tip: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.training_summary_title))
            .setMessage("$summary\n\n$tip")
            .setPositiveButton(activity.getString(R.string.btn_complete)) { _, _ -> activity.finish() }
            .setNegativeButton(activity.getString(R.string.btn_continue)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

```
(Delete the whole block including its trailing blank line, leaving `updateStats()` followed
directly by `private fun showEndSessionDialog()`.)

### Check for now-unused callers

`TrainingActivity.stopTraining` was the only call site (removed in Task 3). Grep for any other
callers of `showSummary`, `getSummaryText()`, `getImprovementTip()` before removing anything
else:

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && grep -rn "showSummary\|getSummaryText\|getImprovementTip" app/src/main app/src/test app/src/androidTest 2>/dev/null
```

- If `showSummary` has zero remaining references after this edit — done, nothing else to do
  for that symbol.
- If `TrainingStateManager.getSummaryText()` (defined
  `app/src/main/java/com/ttcoachai/managers/TrainingStateManager.kt:150-161`) and
  `getImprovementTip()` (lines 163-175) have **zero** remaining callers anywhere in the grep
  output, remove both methods from `TrainingStateManager.kt`, and then also grep for the
  string resources they reference (`R.string.summary_total_strokes`,
  `summary_successful_strokes`, `summary_average_accuracy`, `start_training_advice`,
  `tip_excellent`, `tip_good`, `tip_not_bad`, `tip_needs_practice`,
  `R.string.training_summary_title`, `R.string.btn_complete`, `R.string.btn_continue`) across
  `app/src/main/res/values*/strings.xml` and `app/src/main` — remove only the ones that show
  zero other references. `R.string.btn_continue`/`R.string.btn_complete` in particular may be
  reused by other dialogs; **do not remove a string resource without confirming via grep that
  its only reference was the deleted code.**
- If either method (or any of those strings) still has other callers, **leave them as-is** —
  per spec, this is scoped to `showSummary`'s call site only; do not go hunting for broader
  cleanup.

Expected outcome based on current reading of the code: `getSummaryText()` and
`getImprovementTip()` are defined in `TrainingStateManager.kt` and, as far as this plan's
authors could see, `stopTraining` was their only caller — but the subagent executing this task
must re-run the grep above against the live tree (post Task 3 commit) rather than trust this
note, since other in-flight work in the repo could have added callers.

**Verify:**
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

**Commit:**
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && git add app/src/main/java/com/ttcoachai/managers/TrainingUIController.kt
# If TrainingStateManager.kt and/or strings.xml were also touched, add them explicitly too, e.g.:
# git add app/src/main/java/com/ttcoachai/managers/TrainingStateManager.kt
# git add app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml
git commit -m "$(cat <<'EOF'
refactor(training): remove TrainingUIController.showSummary popup

Superseded by navigation to SessionReviewFragment (see
TrainingActivity.stopTraining). Removes now-dead summary-only helpers/
strings only where grep confirmed zero remaining callers.
EOF
)"
```

---

## Task 5 — Build, install, manual on-device verification

No further code changes expected; this task is verification, plus any small touch-ups that
surface during manual testing (e.g. a missed import, a stale reference).

**Build and install:**
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && ./gradlew :app:installDebug
```

**Manual checklist (device, per spec "Testing" section) — run in both dark and light theme:**

1. Start a training session, run it past 5 seconds, press back or **End Session** → dialog →
   **Finish & Save**.
   - Expect: `TrainingActivity` closes immediately (no popup, no visible wait).
   - Expect: shortly after, `SessionReviewFragment` appears automatically, showing the just-
     completed session (analytics may render progressively as they load — this is expected
     per the existing null-analytics rendering path).
   - Expect: this happens exactly once — no duplicate navigation, no popup.
2. Start a session, back/End Session → dialog → **Discard**.
   - Expect: toast ("session discarded" string), straight to Dashboard (or whatever screen
     was underneath), no summary, no navigation to review.
3. Start a session, stop it in under 5 seconds → **Finish & Save**.
   - Expect: "Training is too short" toast, straight to Dashboard, no summary/review
     navigation.
4. If feasible, sign out (not authenticated) → start and finish a session ≥ 5s → **Finish &
   Save**.
   - Expect: toast/no crash from `CloudSyncManager`, straight to Dashboard, no summary/review
     navigation (existing `CloudSyncManager` behavior — `onSaved` never fires when
     unauthenticated).
5. From step 1's review screen, press back.
   - Expect: lands on the previous screen (Dashboard) normally.
   - Then navigate to a *different* session's review from History or Dashboard, and back out
     again, to confirm the earlier signal wasn't accidentally left set and doesn't re-fire
     unexpectedly (it shouldn't — `pendingReviewSessionId` was reset to `null` right after the
     first navigation in Task 2's collector).

If any step fails, fix the specific issue (do not expand scope — see project CLAUDE.md "stay
in scope" convention), re-run `./gradlew :app:compileDebugKotlin`, and re-verify on device.

**Commit** (only if touch-ups were needed; skip if the checklist passed with zero code
changes):
```bash
cd /Users/itsurkan/Dev/personal/TT_Coach && git add <touched files>
git commit -m "$(cat <<'EOF'
fix(training): <describe the on-device fix>

Found during manual verification of the finish-to-session-summary flow.
EOF
)"
```

---

## Self-review notes

- **Spec coverage:** all five "Wiring" / "Removal" / "Testing" items from the spec map to
  Tasks 1–5. Edge cases (process death, not-authenticated, too-short) are explicitly called
  out in Tasks 3 and 5 rather than left implicit.
- **No placeholders:** every code block above is the literal current file content plus the
  literal diff, taken directly from the files read for this plan (line numbers cited match the
  as-read versions of `TTCoachApplication.kt`, `MainActivity.kt`, `TrainingActivity.kt`,
  `TrainingUIController.kt`).
- **Type/name consistency:** `pendingReviewSessionId` type (`MutableStateFlow<String?>`),
  argument key `"sessionId"` (matches `nav_graph.xml`'s `<argument android:name="sessionId"
  app:argType="string">` on `navigation_session_review`), and destination id
  `R.id.navigation_session_review` are used identically across Tasks 1–3.
- **No fabricated unit tests:** verification is `./gradlew :app:compileDebugKotlin` per task
  and a manual on-device checklist for the final task, per the spec's explicit statement that
  this change has no meaningful pure-logic surface to unit test.
- **Dependency check resolved inline (Task 0):** confirmed `repeatOnLifecycle`/`lifecycleScope`
  are already used and compiling in this module (`CalibrationCaptureFragment.kt`,
  `DashboardFragment.kt`), so no `app/build.gradle` change is needed — avoided speculatively
  adding a redundant dependency line.
