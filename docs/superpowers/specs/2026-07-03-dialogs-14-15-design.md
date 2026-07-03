# Dialogs 14/15 — Design Spec

Status: draft. Slice 3 of the Android gold-dark redesign (design project "Table Tennis Coach
AI Redesign", turns 14 and 15 of the "Live Session" doc). Presentation-only — no pose/ball/
pipeline code touched.

## Overview

Three modal dialogs, currently stock `MaterialAlertDialog` popups, get rebuilt as custom-styled
surfaces matching the house system:

- **14a** delete-exercise confirm — centered alert.
- **14b** end-session — bottom sheet.
- **14c** session summary — bottom sheet.

The AI-coach note shown in the design's 14c is **removed** per product decision — the summary
shows stats only.

Turn 15 (light theme) is the same three dialogs re-tuned for white surfaces, achieved purely
through existing `ttc_*` theme tokens (`values/colors.xml` light, `values-night/colors.xml`
dark) — **no separate layouts, no theme-branching logic**.

## Dialog 14a — ConfirmDialog (centered alert)

A reusable custom confirm dialog (not stock Material buttons/theming). Structure, top to
bottom, centered card:

- **Card**: surface `ttc_surface_elevated`, 1px `ttc_outline_strong` border, 20dp corner
  radius, max-width ~296dp, elevation shadow, padding 22/20/18dp (top/sides/bottom), contents
  centered.
- **Icon badge**: 54×54dp, 15dp corners, background = `ttc_error_container`, 1px
  `ttc_error_container_outline` border, containing a coral (`ttc_error`) trash/stroke icon,
  24dp.
- **Title**: 18sp bold, `ttc_text_1`, margin-top 15dp. Default: "Delete exercise?"
- **Body**: 13.5sp, `ttc_text_2`, line-height 1.55, margin-top 8dp.
- **Button row** (margin-top 20dp, 10dp gap):
  - **Cancel**: transparent background, 1px `ttc_outline_strong` border, 11dp corners,
    `ttc_text_2` text, 600 weight.
  - **Delete**: filled `ttc_error`, no border, 11dp corners, `ttc_on_error` text, 700 weight.

Parameterize: icon drawable, title, body, confirm-label, confirm-is-destructive (drives
whether the confirm button uses the error/coral treatment or a neutral one) — so this same
view is reusable for future confirms beyond delete.

**Reference hexes** (already covered by existing tokens except where noted):

| Role | Dark | Light |
|---|---|---|
| Card bg | `ttc_surface_elevated` #171C24* | `ttc_surface_elevated` #EFF1F4 |
| Card border | `ttc_outline_strong` #2F3542 | `ttc_outline_strong` #D8DBE2 |
| Badge bg | `ttc_error_container` #2A1818* | `ttc_error_container` #FDECEA |
| Badge border | `ttc_error_container_outline` #4A2626* | `ttc_error_container_outline` #F5C9C4 |
| Coral icon/fill | `ttc_error` #E8817A (icon) | `ttc_error` #C4463C, fill variant #E06A62 per design comp |
| Delete-button text | `ttc_on_error` (new, dark ink #3A1212) | `ttc_on_error` (new, #FFFFFF) |

\* The design comp's dark hexes (#171C24 card, #2A1818/#4A2626 badge) are close to but not
identical to the current tokens (`ttc_surface_elevated` #1A1F28, `ttc_error_container`
#3A1A18/#5A2B27). **Use the existing tokens, not the raw design hexes** — the house system
already defines these roles; do not introduce parallel one-off colors. This spec's authority is
"use the token", the raw hex column above is comp reference only.

## Dialog 14b — EndSessionSheet (`BottomSheetDialogFragment`)

The app has **no existing bottom-sheet pattern** — this introduces one:
`ThemeOverlay.TTC.BottomSheet` + a modal style (rounded top 22dp corners,
`ttc_surface_elevated` background, top border `ttc_outline_strong`).

Structure:

- **Drag handle**: 38×4dp, 2dp corner radius, `ttc_outline_strong`, horizontally centered.
- **Title**: "End this session?", 18sp bold, `ttc_text_1`.
- **Body**: "You've been training for {mm:ss}. Save your progress before you leave.", 13sp,
  `ttc_text_2`.
- **Stats tile row** (shared component, see below) — Duration / Strokes / Accuracy tiles.
- **Buttons**:
  1. "Finish & save" — full-width filled gold (`ttc_gold_bright` / primary), dark text
     (`ttc_on_gold`), 12dp corners, leading check icon.
  2. Row of two: "Keep training" — filled `ttc_surface` (#1A1F28 dark token value) with
     `ttc_outline` border, `ttc_text_1` text; "Discard" — transparent, 1px
     `ttc_error_container_outline` border, `ttc_error` text.

**Callbacks**: `onFinishSave`, `onKeepTraining`, `onDiscard`.

**Dismiss behavior**: tapping outside / back-press is non-destructive — treat as
`onKeepTraining` (does not discard or save). `setCanceledOnTouchOutside` stays enabled; wire
the dismiss listener to invoke `onKeepTraining` rather than nothing.

## Dialog 14c — SessionSummarySheet (`BottomSheetDialogFragment`)

Same bottom-sheet container/theme as 14b.

Structure:

- **Drag handle** — as above.
- **Header row**: 40×40dp gold-tint badge, 11dp corners, background `ttc_gold_container`
  (#221C0F dark), border `ttc_gold_container_outline` (#5C4A22 dark), containing a gold target
  icon. Title "Session summary", 17sp bold, `ttc_text_1`. Subtitle "{DrillName} · {mm:ss}",
  11.5sp, JetBrains Mono, `ttc_text_3`.
- **Stats tile row** (shared component, icon variant) — Strokes / Clean·% / Accuracy tiles.
- **Buttons row**: "Continue" — filled `ttc_surface` with `ttc_outline` border, gold play icon,
  `ttc_text_1` text; "Finish" — filled gold, dark (`ttc_on_gold`) text.
- **No AI-coach block** (removed vs. the design comp).

**Callbacks**: `onContinue` (dismiss sheet, stay on the live session), `onFinish` (finish the
hosting activity).

## Shared component — icon stat tiles

One reusable tile layout plus a 3-up row container, used by **both** 14b and 14c.

Per-tile structure: `flex: 1`, background `ttc_sink` (#0E1115 dark), 1px `ttc_outline` (#232833
dark) border, 12dp corners, padding ~12/6dp (vertical/horizontal), column, centered:

1. 17dp stroke icon.
2. Value, JetBrains Mono — 20sp for the summary sheet, 16sp for the end-session sheet.
3. Uppercase 9sp label, `ttc_text_3`, with letter-spacing.

Tile set per screen (parameterize icon/value-color/label per tile — do not fork the layout):

| Sheet | Tile 1 | Tile 2 | Tile 3 |
|---|---|---|---|
| 14b (end session) | Duration (value `ttc_text_1`) | Strokes (value `ttc_text_1`) | Accuracy (value + icon `ttc_gold_bright`, target icon) |
| 14c (summary) | Strokes (lightning icon, value `ttc_text_1`) | Clean·{pct}% (check-circle icon, value + icon `ttc_success`) | Accuracy (target icon, value `ttc_gold_bright`) |

## Data

Sourced from `TrainingStateManager` (singleton):

- `getStrokeCount()`
- `getGoodStrokesCount()` — score ≥ 80 counts as "clean"
- `getAverageScore()` — accuracy %
- `getSessionDurationSeconds()`

Derived values:

- **Clean-percent** = `getGoodStrokesCount() / getStrokeCount() * 100`, guarded against
  divide-by-zero (0 strokes → 0%, not NaN/crash).
- **Duration** formatted `mm:ss`.

## Wire-in points (replace existing stock dialogs)

- **14a**: `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`, `deleteDrill()`
  (~lines 335–355) — replace the `MaterialAlertDialogBuilder` call with `ConfirmDialog`. This
  single call site covers both the swipe-to-delete and long-press-menu delete flows (both
  route through `deleteDrill()`).
- **14b**: `app/src/main/java/com/ttcoachai/TrainingActivity.kt`, back-navigation handler
  (~lines 191–215, inside the `OnBackPressedCallback` registered at line 192) — replace the
  3-button `MaterialAlertDialog` (line ~197) with `EndSessionSheet`.
- **14c**: `app/src/main/java/com/ttcoachai/managers/TrainingUIController.kt`, `showSummary()`
  (~lines 109–116) — replace the `MaterialAlertDialogBuilder` (line 110) with
  `SessionSummarySheet`. Drop the `tip: String` parameter's use in the summary path — remove
  reliance on `getImprovementTip()` / `start_training_advice` for this dialog. (If
  `showSummary`'s `tip` parameter becomes unused, decide during implementation whether to strip
  it from the signature or leave it as dead-but-harmless — prefer stripping if there are no
  other callers.)

## Design tokens to add

Audited both `values/colors.xml` and `values-night/colors.xml`. All of the following already
exist in both variants: `ttc_surface_elevated`, `ttc_outline`, `ttc_outline_strong`, `ttc_sink`,
`ttc_error`, `ttc_error_container`, `ttc_error_container_outline`, `ttc_gold_bright`,
`ttc_gold_container`, `ttc_gold_container_outline`, `ttc_success`, `ttc_text_1`, `ttc_text_2`,
`ttc_text_3`, `ttc_on_gold`, `ttc_surface`.

**Missing — must add to both files:**

| Token | Light value | Dark value |
|---|---|---|
| `ttc_on_error` | `#FFFFFF` | `#3A1212` |

That is the only net-new token identified. Re-audit at implementation time in case other work
lands first and changes the token set.

## Styles to add (`values/styles.xml` + `values/shapes.xml`)

- `values/shapes.xml`: a rounded-top shape appearance for sheets — 22dp top-left/top-right
  corners, 0dp bottom corners (e.g. `ShapeAppearance.TTC.BottomSheet`).
- `values/styles.xml`:
  - `ThemeOverlay.TTC.BottomSheet` — points `bottomSheetStyle` at the modal style below.
  - `TTC.BottomSheet.Modal` (parent `Widget.Material3.BottomSheet`) — background
    `ttc_surface_elevated`, `shapeAppearance` = the new rounded-top shape, top border drawn via
    background/stroke using `ttc_outline_strong` (implementation detail: either a
    `ShapeAppearanceModel`-backed `MaterialShapeDrawable` or a 9-patch/inset-drawable top
    stroke — pick whichever the existing codebase's shape-drawable conventions favor).
- Reuse existing `ShapeAppearance.TTC.*` (Small/Medium/Large/Pill/Inset/Tile) wherever a match
  exists instead of adding new shape styles for the dialog card / tiles / buttons.

## Strings (add to `values/strings.xml` and `values-uk/strings.xml`)

English (add Ukrainian translations alongside, do not leave `values-uk` stale):

| Key | English |
|---|---|
| `end_session_title` | "End this session?" |
| `end_session_body` | "You've been training for %1$s. Save your progress before you leave." |
| `end_session_finish_save` | "Finish & save" |
| `end_session_keep` | "Keep training" |
| `end_session_discard` | "Discard" |
| `session_summary_title` | "Session summary" |
| `session_summary_meta` | "%1$s · %2$s" (drill name · duration) |
| `stat_strokes` | "Strokes" |
| `stat_clean` | "Clean · %1$d%%" |
| `stat_accuracy` | "Accuracy" |
| `stat_duration` | "Duration" |
| `summary_continue` | "Continue" |
| `summary_finish` | "Finish" |

Reuse existing keys for 14a: `drill_delete_title`, `drill_delete_message`,
`drill_delete_confirm`, `drill_cancel` — do not duplicate these.

## Testing

- **Pure logic**: extract clean-percent calculation and mm:ss duration formatting into a small
  testable helper (e.g. a plain function/object, not tied to Android views); unit test in
  `app/src/test`. Note: the underlying stroke-count/score math already lives in
  `TrainingStateManager` — the new helper only needs to own the percentage/formatting
  transform, not re-derive the raw counts.
- **Manual/visual QA**: on-device screenshots in both light and dark theme for all three
  dialogs, since these are custom views without Espresso/instrumented coverage in this pass.

## Non-goals / out of scope

- Real AI-generated coach note in the summary sheet (removed, not deferred to a helper — no
  placeholder UI slot either).
- Redesigning the live-session screen itself (1a/2a) — that's Slice 4, blocked on the parent
  design doc.
- Merging 14b and 14c into a single step — keep them as two separate dialogs/steps.
- Undo-toast delete variant.
- Empty-state summary (0-stroke session summary polish) — logged as a future "Try next" item,
  not built here (the divide-by-zero guard above only prevents a crash, it does not add
  dedicated empty-state copy/layout).

## Execution note

Presentation-only change, confined to `app/`. Execute via
`superpowers:subagent-driven-development` in a git worktree off the current working branch;
cherry-pick the resulting commits back when green, per repo convention (see project
`CLAUDE.md` — plan isolation / integrate-via-cherry-pick rules).
