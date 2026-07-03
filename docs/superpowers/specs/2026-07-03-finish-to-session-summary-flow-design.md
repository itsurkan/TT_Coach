# Finish-to-Session-Summary Flow — Design Spec

Status: draft. Reworks the training-session end flow so that a saved session lands on the
existing `SessionReviewFragment` screen instead of the old Material popup summary.
Presentation/navigation-only — no pose/ball/pipeline code touched.

## Current state (grounding)

- `TrainingActivity` (separate activity, parent `MainActivity`) hosts the live session.
  `stopTraining(discard: Boolean)` — `app/src/main/java/com/ttcoachai/TrainingActivity.kt`
  (~line 132) — stops state and ends the pose session; if `discard`, toasts and calls
  `finish()`; otherwise calls `saveSessionToCloud()` then the OLD
  `uiController.showSummary(...)` Material popup.
- `TrainingUIController.showSummary(summary, tip)` —
  `app/src/main/java/com/ttcoachai/managers/TrainingUIController.kt` (~line 108) — a
  `MaterialAlertDialogBuilder` popup. This is the summary being **removed**.
- `saveSessionToCloud()` returns early (no save at all) when `durationSeconds < 5`.
  `CloudSyncManager.saveTrainingFromState(...)` also skips — never fires `onSaved` — when the
  user is not Firebase-authenticated. Otherwise it persists the `TrainingSession` to local Room
  first (offline-first), then cloud, then fires the suspend `onSaved(sessionId)` callback on a
  `Dispatchers.IO` coroutine owned by the **application-scoped** `CloudSyncManager` — this
  coroutine survives `TrainingActivity.finish()`. `sessionId` is a fresh UUID
  (`TrainingSession.id`).
- Inside `onSaved`, `TrainingActivity` already awaits
  `app.sessionAnalyticsRecorder.record(sessionId, ...)`, which upserts `SessionAnalyticsEntity`
  into Room. By the time `onSaved` completes, both the session and its analytics are in Room.
- `SessionReviewFragment` (nav destination `R.id.navigation_session_review` in
  `app/src/main/res/navigation/nav_graph.xml`) reads `arguments.getString("sessionId")`, loads
  `trainingDao().getSessionById(sessionId)` + `sessionAnalyticsDao().getForSession(sessionId)`,
  and renders gracefully when analytics are null. It is hosted only by `MainActivity`'s
  NavController. Existing callers navigate via
  `findNavController().navigate(R.id.action_..._to_review, Bundle().apply { putString("sessionId", id) })`.
- `MainActivity.onCreate` sets up the NavController from `nav_host_fragment`. No
  `onNewIntent`/deep-link exists.

## Desired flow

- **Pause** button / center FAB → pauses only (unchanged).
- **Back button** and **End Session** button → the finish dialog (already wired: the
  back-navigation `MaterialAlertDialog` in `TrainingActivity.setupBackNavigation`).
- **Discard** → toast + `finish()`. No summary.
- **Save skipped** (session `< 5s`, or user not authenticated) → toast + `finish()`. No summary.
- **Finish & Save** → close the training screen **immediately** (`finish()` now — do not block
  on the save). The save runs async. When `onSaved` completes (session + analytics persisted),
  the app navigates to `SessionReviewFragment` for that `sessionId`. The summary screen loads
  its data async — `SessionReviewFragment`'s existing null-analytics rendering path absorbs the
  brief window before analytics land.

## Mechanism

**Chosen: observable one-shot signal on the Application object.**

Add `val pendingReviewSessionId = MutableStateFlow<String?>(null)` to `TTCoachApplication` (the
app already depends on kotlinx-coroutines app-wide).

- **Why observable, not a plain field read in `onResume`**: `TrainingActivity.finish()` happens
  *before* the async `onSaved` sets the pending id. `MainActivity` may already be resumed by the
  time the value arrives, so a one-shot `onResume` read would miss it. A `StateFlow` collected
  for the activity's started lifetime catches the value whenever it lands, regardless of
  ordering.
- **Why not intent-flag deep-linking** (rejected): would require `TrainingActivity` to pass an
  extra back to `MainActivity` via `startActivity`/result contract, plus `MainActivity` handling
  `onNewIntent` (which doesn't currently exist) and re-deriving intent-vs-lifecycle timing races
  itself. The `finish()`-then-async-`onSaved` ordering makes a request/intent-based handoff
  strictly more complex than a shared observable value the two components already have a path to
  (`TTCoachApplication` is reachable from both). The StateFlow approach needs no new
  activity-result machinery and no intent contract.

### Wiring

- **`TrainingActivity`** (Finish & Save path): call `finish()` immediately, don't wait for save.
  The existing `onSaved` callback (already running post-`finish()`, app-scoped) additionally
  sets `app.pendingReviewSessionId.value = sessionId`. The relative order of `finish()` and the
  `onSaved` assignment doesn't matter — the signal is observed by `MainActivity`, not read
  synchronously by `TrainingActivity`.
- **`MainActivity`**: collect `pendingReviewSessionId` with `repeatOnLifecycle(STARTED)` inside
  `lifecycleScope`. On a non-null value: navigate
  `navController.navigate(R.id.navigation_session_review, Bundle().apply { putString("sessionId", it) })`,
  then set `pendingReviewSessionId.value = null` (consume-once).

### One-shot consume semantics

Setting the value back to `null` immediately after navigating is what makes this "show the
summary exactly once, only on the save path":

- Backing out of `SessionReviewFragment` afterward does not re-trigger navigation — the flow
  value is already `null`, so the collector has nothing to act on. Back-from-review just
  reconfirms whatever screen was underneath, as normal.
- Existing Dashboard/History → `SessionReviewFragment` navigation call sites are untouched —
  they never touch `pendingReviewSessionId`, so this mechanism only affects the finish-and-save
  path.

## Edge cases

- **Not authenticated / session too short**: `onSaved` never fires, so
  `pendingReviewSessionId` is never set. User simply lands back on the Dashboard (or wherever
  `MainActivity` was showing) after `TrainingActivity.finish()`. No summary — matches spec.
- **Process death between `finish()` and `onSaved`**: acceptable data loss is *only* the pending
  signal (an in-memory `StateFlow`, not persisted). Worst case: no summary is shown, but the
  session itself is still saved (Room write happens inside the app-scoped `onSaved` chain,
  independent of whether the process is later killed) and remains viewable from History. Not
  worth persisting the pending-id across process death for this UX-only feature.

## Removal

- Remove `TrainingUIController.showSummary(...)` and its call site in
  `TrainingActivity.stopTraining`.
- Check whether `TrainingStateManager.getSummaryText()` / `getImprovementTip()` and any
  summary-only strings become unused as a result. Strip them **only if** there are no other
  callers; otherwise leave as-is (do not go hunting for a broader cleanup — see scope note
  below).

## Testing

This is a UI/navigation change with no meaningful pure-logic surface to unit test — the only
"logic" is a StateFlow set/consume, which is trivial and Android-lifecycle-bound. Verify
manually on-device:

- Finish & Save → training screen closes immediately, session summary (`SessionReviewFragment`)
  appears once data is ready, in both dark and light theme.
- Discard → toast, straight to Dashboard, no summary.
- Short session (< 5s) and not-authenticated cases → toast, straight to Dashboard, no summary.
- Back out of the summary screen → lands on the previous screen, does not re-show the summary on
  subsequent navigation.

## Non-goals / out of scope

- Redesigning `SessionReviewFragment` itself — it already handles null analytics gracefully;
  this spec only changes how/when it's reached from the training-end flow.
- Any change to the finish dialog's own UI (covered separately, already wired).
- Broader cleanup of `TrainingUIController` / `TrainingStateManager` beyond the specific
  now-possibly-unused summary methods noted above.
- Persisting the pending-review signal across process death.

## Execution note

Presentation/navigation-only change, confined to `app/`. Execute via
`superpowers:subagent-driven-development` in a git worktree off the current working branch;
cherry-pick the resulting commits back when green, per repo convention (see project `CLAUDE.md`
— plan isolation / integrate-via-cherry-pick rules).
