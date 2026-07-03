# Drills swipe-to-reveal actions — design

Date: 2026-07-02
Track: Android UI redesign (gold-dark) — presentation/interaction only. No pose/ball/trajectory pipeline code touched.
Status: approved (brainstorming), pending implementation plan.

## Problem

On the Drills screen, swiping a drill row currently fires Clone/Delete **immediately** when
the finger is lifted past the halfway threshold. The row never stays open, and the gold/red
strip drawn during the drag is a non-interactive canvas background, not a button. This is
error-prone (an over-swipe clones/deletes with no deliberate confirming tap) and does not
match the expected mobile pattern.

Current implementation: `ItemTouchHelper.SimpleCallback` in
`DrillsFragment.attachSwipeActions()` (`app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt:178-222`),
with `onSwiped()` calling `cloneDrill()` / `deleteDrill()` and `onChildDraw()` painting the
gold/red rectangle.

## Goal

Swipe reveals a **persistent, tappable** action button. The action runs **only** on a
deliberate tap of that button, never on finger-release.

## Behavior (agreed)

- **Swipe left** → foreground slides left and stays open, revealing a gold **Clone** button
  (icon + label) pinned to the **right** edge.
- **Swipe right** → foreground slides right and stays open, revealing a red **Delete** button
  (icon + label) pinned to the **left** edge.
- **Directional gating per row** (via `DrillActions`): built-in presets cannot be deleted, so
  right-swipe (Delete) is disabled on them — only Clone (left) works. Custom drills allow both.
- Action fires **only on button tap**:
  - **Clone** → runs immediately (non-destructive), shows the existing toast, reloads list.
  - **Delete** → shows the existing confirm `AlertDialog` (destructive) before deleting.
    Confirmation is preserved (not removed).
- **Only one row open at a time.** Opening a second row closes the first. The open row also
  closes on: tapping the open row's foreground, scrolling the list, or `onResume`/list reload.
- **Closed-row interactions unchanged:** tap foreground → select drill (`onExerciseClick`);
  long-press → options menu (`onExerciseLongClick`). A tap on an **open** row's foreground
  closes it and is consumed (does not select).

### Interaction truth table (foreground tap)

| Row state | Tap target        | Result                          |
|-----------|-------------------|---------------------------------|
| Closed    | foreground        | select drill (unchanged)        |
| Closed    | (long-press)      | options menu (unchanged)        |
| Open      | foreground        | close row, consume tap          |
| Open      | Clone button      | clone + close                   |
| Open      | Delete button     | confirm dialog → delete + close |

## Architecture

Custom view `SwipeRevealLayout` (a `FrameLayout` driven by AndroidX `ViewDragHelper`).
No third-party swipe library — `ViewDragHelper` ships with AndroidX (`androidx.customview`),
so this honors the repo's lean-on-platform / minimal-dependency convention. Lives in
`app/src/main/java/com/ttcoachai/ui/SwipeRevealLayout.kt` (Android-only; not `shared/`).

### Row layout (`item_exercise.xml`) — 3 layers

```
SwipeRevealLayout (root; carries the 12dp bottom gap that the card used to carry)
├── Delete panel   (layout_gravity=start, width≈88dp, bg=@color/ttc_error)
│     trash icon + "Delete"  (centered, on-error text color)
├── Clone panel    (layout_gravity=end,   width≈88dp, bg=@color/ttc_gold_bright)
│     copy icon + "Clone"    (centered, on-gold/dark text color)
└── Foreground     (the existing MaterialCardView + its ConstraintLayout content;
                    match_parent, OPAQUE so both panels are hidden when closed)
```

Layout notes:
- The **foreground must be fully opaque** (the `TTC.Card` surface already is) so panels are
  invisible when the row is closed.
- The card's current `layout_marginBottom="12dp"` moves from the card to the
  `SwipeRevealLayout` root, so the reveal panels align to the card's exact bounds (no 12dp
  gap below the panels). Panels are `match_parent` height within the layout.
- Panel widths are equal and fixed (~88dp) — this is the single "open" offset per side.

### `SwipeRevealLayout` responsibilities

- Capture **only the foreground** in `ViewDragHelper`; horizontal drag only.
- Clamp foreground left position to `[-cloneWidth, +deleteWidth]`, further restricted by
  enabled sides: if delete disabled → clamp to `[-cloneWidth, 0]`.
- On release, settle to one of three states via the pure snap decision (below): `CLOSED`,
  `OPEN_CLONE` (foreground at `-cloneWidth`), `OPEN_DELETE` (foreground at `+deleteWidth`).
- Public API:
  - `fun setDeleteEnabled(enabled: Boolean)` — locks/unlocks the right (Delete) side.
  - `fun close(animate: Boolean)` / `fun open(side, animate)`.
  - `val isOpen: Boolean`, current state accessor.
  - `var onStateChanged: ((SwipeState) -> Unit)?` — fires on open/close (used by the
    single-open coordinator).
  - foreground/clone/delete child views exposed (by id) for the adapter to wire clicks.
- Intercept a tap on the foreground **when open** → `close(animate=true)` and consume it, so
  it doesn't propagate as a select.

### Pure, testable core

Extract the settle logic into a pure function (no Android types) so it is unit-testable:

```
enum class SwipeState { CLOSED, OPEN_CLONE, OPEN_DELETE }

// Given the current foreground offset, release velocity, the button width, and which
// sides are enabled, decide the settle target.
fun decideSwipeSettle(
    offsetX: Float,          // foreground left; negative = dragged left (toward clone)
    velocityX: Float,        // release fling velocity (px/s)
    buttonWidth: Float,
    deleteEnabled: Boolean,
): SwipeState
```

Rules: cross ~half the button width, or a fling above a velocity threshold, opens that side;
otherwise closes. A blocked side (e.g. delete disabled) can only resolve to `CLOSED` on that
side. This function + the clamp are the unit-tested surface.

## Wiring

### `ExerciseAdapter`
- Constructor gains `onCloneClick: (Exercise) -> Unit` and `onDeleteClick: (Exercise) -> Unit`
  (alongside existing `onExerciseClick`, `onExerciseLongClick`).
- `onBindViewHolder`:
  - **Reset the recycled row to `CLOSED`** (no `animate`) — critical, or recycled views show a
    stale open state.
  - `setDeleteEnabled(DrillActions.canDelete(exercise))`.
  - Wire Clone button → `onCloneClick(exercise)` then close; Delete button →
    `onDeleteClick(exercise)` then close.
  - Foreground tap → select when closed / close when open (handled inside `SwipeRevealLayout`
    for the open case; adapter keeps the existing `onExerciseClick` for the closed case).
- **Single-open coordinator:** adapter holds `var openRow: SwipeRevealLayout?`. On a row's
  `onStateChanged(open)`, close the previously tracked `openRow` and store the new one; clear
  on close.

### `DrillsFragment`
- **Remove** `attachSwipeActions()` and its `ItemTouchHelper` (the whole block at lines
  178-222) and the import.
- Pass `onCloneClick = { cloneDrill(it) }`, `onDeleteClick = { deleteDrill(it) }` to the
  adapter. `cloneDrill` / `deleteDrill` bodies stay as-is.
- Add a RecyclerView `OnScrollListener` (or reuse the coordinator) that closes any open row on
  scroll.
- `boundPrograms` position→Exercise mapping is no longer needed for swipe (the adapter binds
  the Exercise directly to each row's buttons); keep it only if still used elsewhere, else
  remove the now-dead swipe-only usage.

## Resources

- **Icons:** add vector drawables `ic_content_copy` and `ic_trash` if not already present
  (check `app/src/main/res/drawable/` first; reuse an existing delete/copy glyph if one fits).
- **Strings:** reuse `drill_action_clone` / `drill_action_delete` for labels (UA + EN already
  exist). Add `contentDescription` on the panels for accessibility (reuse the same strings).
- **Colors:** `@color/ttc_gold_bright` (Clone), `@color/ttc_error` (Delete) — already used by
  the old canvas draw.

## Testing

- **Unit (`app/src/test`):** `decideSwipeSettle` + clamp — table of (offset, velocity,
  deleteEnabled) → expected `SwipeState`, including the delete-disabled lock and the
  half-width / fling thresholds.
- **Manual (device):** swipe feel, snap open/closed, one-open-at-a-time, close-on-scroll,
  tap-open-row-to-close, Clone toast, Delete confirm dialog, built-in shows Clone only,
  recycled rows reset to closed after fast scrolling.
- Gesture "feel" (drag friction, settle animation) is not unit-tested — verified on device.

## Accessibility / edge cases

- The **options menu** (long-press → Continue/Edit/Clone/Rename/Delete) remains a non-swipe
  path to the same actions, so swipe is an enhancement, not the only route.
- Locked/`isLocked` drills already dim to alpha 0.5; swipe behavior is unaffected (they are
  built-ins → Clone-only). No new locking logic.

## Non-goals

- No change to Clone/Delete business logic, repositories, Room, or list data flow.
- No change to the Recent card, FAB, options menu, or calibration-intro flow.
- No Compose migration; stays in the existing View/RecyclerView stack.

## Files touched

- `app/src/main/java/com/ttcoachai/ui/SwipeRevealLayout.kt` — **new** custom view + pure
  settle helper (helper may live in same file or a sibling `SwipeSettle.kt`).
- `app/src/main/res/layout/item_exercise.xml` — restructured into the 3-layer reveal.
- `app/src/main/java/com/ttcoachai/ExerciseAdapter.kt` — new callbacks, reset-on-bind,
  single-open coordinator, button wiring.
- `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt` — remove `ItemTouchHelper`,
  pass new callbacks, add close-on-scroll.
- `app/src/main/res/drawable/ic_content_copy.xml`, `ic_trash.xml` — **new** if missing.
- `app/src/test/.../SwipeSettleTest.kt` — **new** unit tests.
```
