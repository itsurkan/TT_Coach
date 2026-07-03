# Exercises tab — gold-dark redesign (RECENT / ALL PROGRAMS + interactions)

**Date:** 2026-07-02
**Track:** Android UI redesign (gold-dark), Slice 2/3 continuation
**Screens covered:** `2a` (list), `10a` (swipe), `10b` (long-press menu), `10c`/`10d` (editor — spec only)
**Design source:** claude.ai/design project "Table Tennis Coach AI Redesign" (UUID `feb1eaea-d763-41c9-86fe-1262790d7291`)

## Summary

Restyle and restructure the Exercises tab (`DrillsFragment`, the "Вправи" tab) to the
gold-dark house system, and add the row-level interactions from the mockups. The current
featured-card + difficulty-filter layout is replaced by two sections — **RECENT** (last
session + resume) and **ALL PROGRAMS** — plus swipe actions, a long-press menu, and a "+"
FAB. The New/Clone param **editor** (`10c`/`10d`) is specified here but **implemented in a
following slice**; this slice wires the "add" entry points to the existing calibration→name
flow as a placeholder.

This is presentation + interaction work only. The pose/ball/trajectory pipeline stays
frozen. No new drill analyzers are written (see Deferred).

## Scope

**In scope (this slice):**
- `2a` — RECENT + ALL PROGRAMS list layout, gold-dark styling, empty/first-run state.
- `10a` — swipe-to-Clone (left) / swipe-to-Delete (right); gold "+" FAB.
- `10b` — long-press contextual menu (Continue / Edit / Clone / Rename / Delete).
- Action-availability rule (Clone-all, edit custom-only).
- Bilingual string resources (UA + EN) for all new labels/menu items.

**Spec-only (next slice):**
- `10c`/`10d` — New/Clone exercise editor persisting to `DrillConfigEntity`.

**Out of scope / deferred:**
- Generalizing stroke detection so non-forehand drills produce accurate feedback
  (new `DESIGN_LIMITATIONS.md` L-entry). Tapping a non-forehand program still launches the
  existing training flow; feedback accuracy for those drills is not claimed.

## Decisions (from brainstorming)

1. **Scope split:** list + interactions now; editor spec now, editor build next slice.
2. **Programs are launchable, not gated:** every program (built-in or custom) launches the
   existing `TrainingActivity` flow. No "coming soon" placeholders.
3. **Editor persists via `DrillConfigEntity`** (`drill_configs` Room table) — reuses the
   existing per-phase-override model; no new data model.
4. **Action rule — Clone-all, edit custom-only:** built-ins can only be **Cloned** (into an
   editable custom copy) and **Continued**; **Edit / Rename / Delete** apply to
   user-created custom drills only. Built-in presets are immutable.
5. **Bilingual labels:** all new strings added to both `values/strings.xml` (EN) and
   `values-uk/strings.xml` (UK); default follows device locale.

## Screen 2a — list structure

Root stays a scrollable list on `ttc_canvas`. Sections top-to-bottom:

### RECENT
- **Header row:** eyebrow label "RECENT" / "НЕЩОДАВНІ" (`TextAppearance.TTC.Eyebrow`,
  `ttc_text_3`) on the left; relative date ("Yesterday") on the right, same style.
- **Recent card:** a **gold-outlined** `TTC.Card` variant — `strokeColor = ttc_gold_bright`
  (or `ttc_gold_container_outline` for a softer edge), optional faint gold-tint surface,
  14dp radius. Contents:
  - Icon tile (`bg_icon_tile_gold`, `ttc_gold_accent` tint) — drill glyph.
  - Title (`TextAppearance.TTC.Title.Card`).
  - Sub-line: "Last session · **68% accuracy**" — the accuracy value in `ttc_success`
    green (`TextAppearance.TTC.Mono.Meta` for the number, body for the rest).
  - Full-width `TTC.Button.Primary` "▶ Continue training" (gold fill, `ttc_on_gold` text).

### ALL PROGRAMS
- **Header:** eyebrow "ALL PROGRAMS" / "ВСІ ПРОГРАМИ".
- **Rows:** existing `item_exercise` row (icon tile + name + description + "10–15 min ·
  Technique" meta via `Mono.Meta`/`ttc_text_3` + chevron), one per drill **except** the
  recent one. The **difficulty filter chips (All/Beginner/Intermediate/Advanced) are
  removed** — the new IA has no filter dimension.

### Add button
- Dashed-outline ghost button "**+ Add your own exercise · calibration**" below the list.
  New `bg_dashed_outline` drawable (dashed stroke `ttc_outline`, 11dp radius) applied to a
  `TTC.Button.Ghost` variant.

### Empty / first-run state
- If session history is empty, the **entire RECENT section (header + card) is hidden** and
  ALL PROGRAMS renders the full list. No recent card placeholder.

### Data flow
- `DrillsFragment` builds `allDrills = builtIns + customDrills` (as today), then splits into
  `recent` (most-recently-trained drill from session history) and `programs = allDrills −
  recent`.
- **Session-history source is an open dependency** to resolve in planning: locate the
  existing sessions store (Room `sessions`/baseline records) and whether a per-session
  accuracy value exists. If accuracy is unavailable for the recent session, omit the
  accuracy chip (show "Last session · <date>" only). If no session store exists yet, treat
  as first-run (RECENT hidden) until session persistence lands.

## Screen 10a — swipe actions + FAB

- **`ItemTouchHelper`** attached to the ALL PROGRAMS RecyclerView:
  - **Swipe left → Clone**: gold background panel (`ttc_gold_bright`/container), copy icon
    + "Clone" label revealed on the trailing edge.
  - **Swipe right → Delete**: red background panel (`ttc_error`), trash icon + "Delete".
  - Swipe is **gated by the action rule** (below): a built-in row allows Clone-swipe only;
    Delete-swipe on a built-in is disabled (row springs back, or Delete direction is not
    enabled for built-ins).
- **"+" FAB:** gold circular `FloatingActionButton` (`ttc_gold_bright` background,
  `ttc_on_gold` icon), anchored bottom-right above the bottom nav.
- The RECENT card is not swipeable (its actions live in its own long-press/menu if needed;
  primary affordance is Continue training).

## Screen 10b — long-press menu

- Long-press a program row → contextual menu (reuse the existing long-click hook in
  `DrillsFragment`). Items, **filtered by the action rule**:
  - **Continue training** (always) — launches `TrainingActivity`.
  - **Edit** (custom only) — opens editor (placeholder flow this slice).
  - **Clone** (always) — creates an editable custom copy (labelled "swipe →" hint).
  - **Rename** (custom only) — inline rename prompt.
  - **Delete** (custom only) — confirm + remove from `custom_drills` (labelled "swipe ←").

## Action-availability rule

| Action     | Built-in preset | Custom drill |
|------------|-----------------|--------------|
| Continue   | ✓               | ✓            |
| Clone      | ✓               | ✓            |
| Edit       | —               | ✓            |
| Rename     | —               | ✓            |
| Delete     | —               | ✓            |

- **Clone** of a built-in creates a new `custom_drills` entry (`baseTemplate` = source drill
  type) with a "(copy)" name; that copy is fully editable.
- Swipe directions respect this rule per-row (built-in: Clone-swipe enabled, Delete-swipe
  disabled).

## Add / editor entry points (this slice)

- Both the **dashed "Add your own exercise"** button and the **"+" FAB** launch the
  **existing calibration → name-prompt add-custom flow** (as today) as the placeholder for
  the editor. **Edit** on a custom drill likewise routes to the current simple flow.
- Next slice replaces these entry points with the `10c`/`10d` editor.

## Screen 10c / 10d — editor (SPEC ONLY, next slice)

Single form reused for New (empty) and Clone (pre-filled from source config). Persists to
`DrillConfigEntity` / `drill_configs`.

- **NAME** — text field (placeholder "e.g. Forehand counter-drive"; Clone pre-fills
  "<source> (copy)").
- **FOCUS** — multi-select pill chips: Arm, Shoulders, Legs, Core, Hips. Selection drives an
  "Active metrics: elbow, shoulder, …" summary line.
- **REFERENCE** — radio: "My baseline (calibrate to me)" vs "Standard technique". Baseline
  option triggers the calibration flow.
- **STRICTNESS** — slider, ×multiplier (e.g. ×1.00), gold `TTC.Slider`.
- **Advanced — per-phase targets** — collapsible. Rows of `metric · phase` with FROM° / TO°
  numeric inputs (e.g. `elbow · backswing` 145 / 175). Empty = standard values.
- **Actions** — Cancel + Create exercise (New) / Save copy (Clone), `TTC.Button.Ghost` +
  `TTC.Button.Primary`.
- **Persistence** — map form → `DrillConfigEntity` per-phase overrides + focus + strictness
  + reference mode. Detailed field mapping is defined when this slice is planned.

## New resources

- `bg_dashed_outline.xml` — dashed stroke ghost-button/background.
- Swipe backgrounds — gold Clone panel + red Delete panel (drawables or drawn in the
  `ItemTouchHelper` callback).
- Gold FAB style (reuse `ttc_gold_bright`/`ttc_on_gold`; `ShapeAppearance.TTC.Large` or
  circular).
- Gold-outlined card variant (either a new `TTC.Card.Highlighted` style or per-view stroke
  override).
- String resources (EN + UK): section labels, menu items, swipe labels, add-button text,
  "Last session", "accuracy".

## Files touched (this slice)

- `app/src/main/res/layout/fragment_drills.xml` — restructure to RECENT + ALL PROGRAMS + FAB.
- `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt` — recent/programs split,
  swipe + long-press wiring, action rule, empty state.
- `app/src/main/java/com/ttcoachai/ExerciseAdapter.kt` / `item_exercise.xml` — minor
  (stays mostly as-is; ensure gold-outline recent card handled separately or via a header
  view type).
- `app/src/main/res/values/styles.xml`, `.../drawable/` — new card/button/FAB/swipe
  resources.
- `app/src/main/res/values/strings.xml` + `values-uk/strings.xml` — new bilingual strings.
- `docs/DESIGN_LIMITATIONS.md` — new L-entry for non-forehand analyzer generalization.

## Testing

- Presentation is Android-only (no shared-KMP logic changes), so coverage is manual +
  existing build green:
  - `./gradlew :app:assembleDebug` compiles.
  - Manual: recent card renders with/without accuracy; first-run hides RECENT; swipe
    reveals Clone/Delete per action rule; long-press menu items filtered per rule; FAB and
    dashed button launch add flow; Clone of a built-in creates an editable custom copy.
  - Screenshot verification against `2a` in both dark and light themes (adb screencaps to
    `tmp/screenshots/`).
- Any pure logic extracted (e.g. recent/programs split, action-rule predicate) should be a
  small testable function; add a JVM unit test if it lives somewhere testable.

## Deferred (logged, not blocking)

- **Non-forehand analyzer generalization** — `StrokeDetector2D` / `ForwardStrokeFilter` are
  forehand-tuned; editing params for a backhand/topspin drill does not yet yield accurate
  feedback. New `DESIGN_LIMITATIONS.md` L-entry.
- **Editor implementation** (`10c`/`10d`) — next slice.
