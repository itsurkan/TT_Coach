# Exercises Tab — RECENT/ALL PROGRAMS + Interactions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle and restructure the Android Exercises/Drills tab to the gold-dark house system — a RECENT card (live last-session data) + an ALL PROGRAMS list — and add swipe-to-Clone/Delete, a long-press action menu, and a gold "+" FAB, all gated by the Clone-all / edit-custom-only action rule.

**Architecture:** Presentation + interaction only. All logic lives in `app/` (Android). Pose/ball/trajectory pipeline stays frozen; no shared-KMP changes. The one testable pure unit — the action-availability rule and recent/programs partition — is extracted into a `DrillActions` Kotlin object with JVM unit tests. Everything else is XML layout, styles, drawables, strings, and `DrillsFragment` wiring, verified by build + manual screenshot QA. Session data comes from the existing `TrainingSession` Room store via a new no-arg DAO query.

**Tech Stack:** Kotlin, Android Material 3 (`MaterialCardView`, `FloatingActionButton`, `ItemTouchHelper`, `RecyclerView`), Room 2.6.1, existing TTC gold-dark design tokens.

## Global Constraints

- **Commit hygiene:** `git add` explicit paths only, never `git add -A`. Commit after each task.
- **Freeze discipline:** do not modify pose/ball/trajectory pipeline code; this slice touches only `app/` presentation + `DrillsFragment` + one DAO query.
- **Bilingual strings:** every new user-facing string added to BOTH `app/src/main/res/values/strings.xml` (EN) and `app/src/main/res/values-uk/strings.xml` (UK). No hardcoded strings in layouts/Kotlin.
- **Action rule (verbatim):** Continue ✓both · Clone ✓both · Edit/Rename/Delete custom-only. Custom drills are identified by `id` starting with `"custom_"` (`DrillsFragment.CUSTOM_DRILL_PREFIX`). Built-in presets are immutable.
- **Design tokens exist already:** `ttc_canvas`, `ttc_surface`, `ttc_gold_bright` (#E9C46A), `ttc_gold_accent`, `ttc_on_gold`, `ttc_text_1/2/3`, `ttc_success` (#12A05F), `ttc_error` (#C4463C), `ttc_outline`, `ttc_gold_container_outline`; styles `TTC.Card`, `TTC.Button.Primary`, `TTC.Button.Ghost`, `TextAppearance.TTC.{Title.Card, Body, Body.Secondary, Mono.Meta, Eyebrow}`, shapes `ShapeAppearance.TTC.{Small,Medium,Large}`, drawable `bg_icon_tile_gold`. Reuse them; do not redefine.
- **Test command:** `./gradlew :app:testDebugUnitTest` for JVM unit tests; `./gradlew :app:assembleDebug` for build.

---

## File Structure

- `app/src/main/java/com/ttcoachai/fragment/DrillActions.kt` — NEW. Pure object: action-availability predicates + recent/programs partition. Testable, no Android deps beyond the `Exercise` model.
- `app/src/test/java/com/ttcoachai/fragment/DrillActionsTest.kt` — NEW. JVM unit tests for `DrillActions`.
- `app/src/main/java/com/ttcoachai/db/TrainingDao.kt` — MODIFY. Add `getMostRecentSession()` no-arg query.
- `app/src/main/res/values/strings.xml` + `values-uk/strings.xml` — MODIFY. New bilingual strings.
- `app/src/main/res/values/styles.xml` — MODIFY. Add `TTC.Card.Highlighted` (gold-outlined) card style.
- `app/src/main/res/drawable/bg_dashed_outline.xml` — NEW. Dashed ghost-button background.
- `app/src/main/res/layout/fragment_drills.xml` — MODIFY. Restructure to RECENT + ALL PROGRAMS + FAB, remove featured card + filter chips.
- `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt` — MODIFY. Recent/programs split, recent-card binding from `TrainingSession`, swipe + long-press wiring, action-rule enforcement, empty state.
- `docs/DESIGN_LIMITATIONS.md` — MODIFY. New L-entry for non-forehand analyzer generalization.

---

### Task 1: `DrillActions` pure helper + tests

The only unit-testable logic in this slice: who can do what, and how the list splits into recent + programs.

**Files:**
- Create: `app/src/main/java/com/ttcoachai/fragment/DrillActions.kt`
- Test: `app/src/test/java/com/ttcoachai/fragment/DrillActionsTest.kt`

**Interfaces:**
- Consumes: `com.ttcoachai.Exercise` (data class with `id: String`).
- Produces:
  - `DrillActions.isCustom(exercise: Exercise): Boolean`
  - `DrillActions.canEdit(exercise): Boolean` / `canRename` / `canDelete` (all == `isCustom`)
  - `DrillActions.canClone(exercise): Boolean` / `canContinue(exercise): Boolean` (both == `true`)
  - `DrillActions.partition(all: List<Exercise>, recentId: String?): Pair<Exercise?, List<Exercise>>` — returns (recent-or-null, programs = all minus recent, order preserved). If `recentId` is null or not found in `all`, recent is null and programs == all.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttcoachai.fragment

import com.ttcoachai.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillActionsTest {

    private fun ex(id: String) = Exercise(id = id, name = id, description = "", difficulty = "", duration = "")

    @Test
    fun builtIn_isNotCustom_cloneAndContinueOnly() {
        val e = ex("forehand_drive")
        assertFalse(DrillActions.isCustom(e))
        assertTrue(DrillActions.canClone(e))
        assertTrue(DrillActions.canContinue(e))
        assertFalse(DrillActions.canEdit(e))
        assertFalse(DrillActions.canRename(e))
        assertFalse(DrillActions.canDelete(e))
    }

    @Test
    fun custom_isCustom_allActionsAllowed() {
        val e = ex("custom_1720000000000")
        assertTrue(DrillActions.isCustom(e))
        assertTrue(DrillActions.canClone(e))
        assertTrue(DrillActions.canContinue(e))
        assertTrue(DrillActions.canEdit(e))
        assertTrue(DrillActions.canRename(e))
        assertTrue(DrillActions.canDelete(e))
    }

    @Test
    fun partition_pullsRecentOutAndKeepsOrder() {
        val all = listOf(ex("a"), ex("b"), ex("c"))
        val (recent, programs) = DrillActions.partition(all, "b")
        assertEquals("b", recent?.id)
        assertEquals(listOf("a", "c"), programs.map { it.id })
    }

    @Test
    fun partition_nullRecentId_returnsNullRecentAndFullList() {
        val all = listOf(ex("a"), ex("b"))
        val (recent, programs) = DrillActions.partition(all, null)
        assertNull(recent)
        assertEquals(listOf("a", "b"), programs.map { it.id })
    }

    @Test
    fun partition_unknownRecentId_returnsNullRecentAndFullList() {
        val all = listOf(ex("a"), ex("b"))
        val (recent, programs) = DrillActions.partition(all, "zzz")
        assertNull(recent)
        assertEquals(listOf("a", "b"), programs.map { it.id })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.fragment.DrillActionsTest"`
Expected: FAIL — `DrillActions` unresolved reference.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.ttcoachai.fragment

import com.ttcoachai.Exercise

/**
 * Action-availability rule for the Exercises tab (this slice).
 * Clone/Continue apply to every drill; Edit/Rename/Delete only to user-created
 * custom drills. Built-in presets are immutable. Custom drills are identified by
 * the `custom_` id prefix (see DrillsFragment.CUSTOM_DRILL_PREFIX).
 */
object DrillActions {

    private const val CUSTOM_PREFIX = "custom_"

    fun isCustom(exercise: Exercise): Boolean = exercise.id.startsWith(CUSTOM_PREFIX)

    fun canClone(exercise: Exercise): Boolean = true
    fun canContinue(exercise: Exercise): Boolean = true
    fun canEdit(exercise: Exercise): Boolean = isCustom(exercise)
    fun canRename(exercise: Exercise): Boolean = isCustom(exercise)
    fun canDelete(exercise: Exercise): Boolean = isCustom(exercise)

    /**
     * Splits [all] into (mostRecent, programs). The recent drill is removed from
     * the programs list; original order is otherwise preserved. Returns null recent
     * and the full list when [recentId] is null or not present in [all].
     */
    fun partition(all: List<Exercise>, recentId: String?): Pair<Exercise?, List<Exercise>> {
        val recent = recentId?.let { id -> all.firstOrNull { it.id == id } }
        val programs = if (recent == null) all else all.filter { it.id != recent.id }
        return recent to programs
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.fragment.DrillActionsTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ttcoachai/fragment/DrillActions.kt app/src/test/java/com/ttcoachai/fragment/DrillActionsTest.kt
git commit -m "feat(drills): DrillActions action-rule + recent/programs partition"
```

---

### Task 2: Most-recent-session DAO query

The RECENT card needs the single most-recent training session regardless of user. Add a no-arg query (single-user local DB; avoids coupling the fragment to auth).

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/db/TrainingDao.kt`

**Interfaces:**
- Produces: `TrainingDao.getMostRecentSession(): TrainingSession?` — newest row by `startTime`, or null when the table is empty.

- [ ] **Step 1: Add the query**

Add this method to the `TrainingDao` interface (next to the existing `getRecentSessions`):

```kotlin
@Query("SELECT * FROM training_sessions ORDER BY startTime DESC LIMIT 1")
suspend fun getMostRecentSession(): TrainingSession?
```

- [ ] **Step 2: Verify it compiles (Room annotation processing runs)**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (Room validates the SQL at compile time).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ttcoachai/db/TrainingDao.kt
git commit -m "feat(drills): getMostRecentSession DAO query for RECENT card"
```

---

### Task 3: New string resources (bilingual)

All labels the new UI needs, in EN + UK.

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`

- [ ] **Step 1: Add EN strings**

Add to `app/src/main/res/values/strings.xml` (inside `<resources>`):

```xml
<!-- Exercises tab redesign (2a / 10a / 10b) -->
<string name="drills_section_recent">RECENT</string>
<string name="drills_section_all_programs">ALL PROGRAMS</string>
<string name="drills_recent_last_session">Last session</string>
<string name="drills_recent_accuracy">%1$d%% accuracy</string>
<string name="drills_continue_training">Continue training</string>
<string name="drills_add_own_exercise">+ Add your own exercise · calibration</string>
<string name="drill_action_continue">Continue training</string>
<string name="drill_action_edit">Edit</string>
<string name="drill_action_clone">Clone</string>
<string name="drill_action_rename">Rename</string>
<string name="drill_action_delete">Delete</string>
<string name="drill_clone_copy_suffix">(copy)</string>
<string name="drill_clone_toast">Cloned as \"%1$s\"</string>
<string name="drill_rename_title">Rename exercise</string>
<string name="drill_rename_save">Save</string>
<string name="drill_delete_title">Delete exercise?</string>
<string name="drill_delete_message">\"%1$s\" will be removed. This cannot be undone.</string>
<string name="drill_delete_confirm">Delete</string>
<string name="drill_delete_toast">Deleted \"%1$s\"</string>
<string name="drill_cancel">Cancel</string>
```

- [ ] **Step 2: Add UK strings**

Add to `app/src/main/res/values-uk/strings.xml` (inside `<resources>`):

```xml
<!-- Exercises tab redesign (2a / 10a / 10b) -->
<string name="drills_section_recent">НЕЩОДАВНІ</string>
<string name="drills_section_all_programs">ВСІ ПРОГРАМИ</string>
<string name="drills_recent_last_session">Останнє тренування</string>
<string name="drills_recent_accuracy">%1$d%% точності</string>
<string name="drills_continue_training">Продовжити тренування</string>
<string name="drills_add_own_exercise">+ Додати свою вправу · калібрування</string>
<string name="drill_action_continue">Продовжити тренування</string>
<string name="drill_action_edit">Редагувати</string>
<string name="drill_action_clone">Клонувати</string>
<string name="drill_action_rename">Перейменувати</string>
<string name="drill_action_delete">Видалити</string>
<string name="drill_clone_copy_suffix">(копія)</string>
<string name="drill_clone_toast">Клоновано як «%1$s»</string>
<string name="drill_rename_title">Перейменувати вправу</string>
<string name="drill_rename_save">Зберегти</string>
<string name="drill_delete_title">Видалити вправу?</string>
<string name="drill_delete_message">«%1$s» буде видалено. Цю дію не можна скасувати.</string>
<string name="drill_delete_confirm">Видалити</string>
<string name="drill_delete_toast">Видалено «%1$s»</string>
<string name="drill_cancel">Скасувати</string>
```

- [ ] **Step 2b: Verify resources compile**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml
git commit -m "feat(drills): bilingual strings for RECENT/ALL PROGRAMS + row actions"
```

---

### Task 4: New drawables + highlighted card style

Gold-outlined RECENT card variant and the dashed add-button background.

**Files:**
- Create: `app/src/main/res/drawable/bg_dashed_outline.xml`
- Modify: `app/src/main/res/values/styles.xml`

**Interfaces:**
- Produces: style `TTC.Card.Highlighted`; drawable `@drawable/bg_dashed_outline`.

- [ ] **Step 1: Create the dashed-outline drawable**

`app/src/main/res/drawable/bg_dashed_outline.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@android:color/transparent" />
    <stroke
        android:width="1dp"
        android:color="@color/ttc_outline"
        android:dashWidth="6dp"
        android:dashGap="4dp" />
    <corners android:radius="11dp" />
</shape>
```

- [ ] **Step 2: Add the highlighted card style**

Add to `app/src/main/res/values/styles.xml` (immediately after the existing `TTC.Card` style):

```xml
<!-- Gold-outlined card variant for the RECENT drill card -->
<style name="TTC.Card.Highlighted" parent="TTC.Card">
    <item name="strokeColor">@color/ttc_gold_bright</item>
    <item name="strokeWidth">1.5dp</item>
</style>
```

- [ ] **Step 3: Verify resources compile**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/drawable/bg_dashed_outline.xml app/src/main/res/values/styles.xml
git commit -m "feat(drills): highlighted card style + dashed add-button drawable"
```

---

### Task 5: Restructure `fragment_drills.xml`

Replace featured card + filter chips with RECENT section + ALL PROGRAMS header + FAB. Keep the RecyclerView and dashed add button.

**Files:**
- Modify: `app/src/main/res/layout/fragment_drills.xml`

**Interfaces:**
- Produces (view IDs consumed by Task 6):
  - `section_recent` (container, `View`) — whole RECENT block, toggled visible/gone.
  - `tv_recent_date` (`TextView`) — right-aligned eyebrow date.
  - `fl_recent_icon` (`FrameLayout`), `iv_recent_icon` (`ImageView`), `tv_recent_name` (`TextView`).
  - `tv_recent_meta` (`TextView`) — "Last session".
  - `tv_recent_accuracy` (`TextView`) — "68% accuracy", green.
  - `btn_recent_continue` (`MaterialButton`).
  - `rv_drills` (`RecyclerView`) — ALL PROGRAMS list (unchanged id).
  - `btn_add_custom_drill` (`MaterialButton`) — dashed add button (unchanged id).
  - `fab_add_drill` (`FloatingActionButton`) — gold "+" FAB.

- [ ] **Step 1: Replace the layout**

Overwrite `app/src/main/res/layout/fragment_drills.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/ttc_canvas">

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appbar_drills"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/ttc_canvas"
        app:elevation="0dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingHorizontal="20dp"
            android:paddingTop="20dp"
            app:layout_scrollFlags="scroll|enterAlways">

            <!-- Screen header -->
            <TextView
                android:id="@+id/tv_drills_title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/nav_drills"
                android:textAppearance="@style/TextAppearance.TTC.Title.Screen" />

            <TextView
                android:id="@+id/tv_drills_subtitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:text="@string/drills_subtitle"
                android:textAppearance="@style/TextAppearance.TTC.Body.Secondary" />

            <!-- RECENT section -->
            <LinearLayout
                android:id="@+id/section_recent"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:layout_marginTop="20dp"
                android:visibility="gone">

                <RelativeLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="8dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignParentStart="true"
                        android:text="@string/drills_section_recent"
                        android:textAppearance="@style/TextAppearance.TTC.Eyebrow" />

                    <TextView
                        android:id="@+id/tv_recent_date"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignParentEnd="true"
                        android:textAppearance="@style/TextAppearance.TTC.Eyebrow" />
                </RelativeLayout>

                <com.google.android.material.card.MaterialCardView
                    style="@style/TTC.Card.Highlighted"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="16dp">

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="horizontal"
                            android:gravity="center_vertical">

                            <FrameLayout
                                android:id="@+id/fl_recent_icon"
                                android:layout_width="56dp"
                                android:layout_height="56dp"
                                android:background="@drawable/bg_icon_tile_gold">

                                <ImageView
                                    android:id="@+id/iv_recent_icon"
                                    android:layout_width="28dp"
                                    android:layout_height="28dp"
                                    android:layout_gravity="center"
                                    android:src="@drawable/ic_target"
                                    app:tint="@color/ttc_gold_accent" />
                            </FrameLayout>

                            <LinearLayout
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_weight="1"
                                android:layout_marginStart="12dp"
                                android:orientation="vertical">

                                <TextView
                                    android:id="@+id/tv_recent_name"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:textAppearance="@style/TextAppearance.TTC.Title.Card" />

                                <LinearLayout
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:layout_marginTop="2dp"
                                    android:orientation="horizontal"
                                    android:gravity="center_vertical">

                                    <TextView
                                        android:id="@+id/tv_recent_meta"
                                        android:layout_width="wrap_content"
                                        android:layout_height="wrap_content"
                                        android:text="@string/drills_recent_last_session"
                                        android:textAppearance="@style/TextAppearance.TTC.Body.Secondary" />

                                    <TextView
                                        android:layout_width="wrap_content"
                                        android:layout_height="wrap_content"
                                        android:layout_marginStart="6dp"
                                        android:layout_marginEnd="6dp"
                                        android:text="·"
                                        android:textAppearance="@style/TextAppearance.TTC.Body.Secondary" />

                                    <TextView
                                        android:id="@+id/tv_recent_accuracy"
                                        android:layout_width="wrap_content"
                                        android:layout_height="wrap_content"
                                        android:textColor="@color/ttc_success"
                                        android:textAppearance="@style/TextAppearance.TTC.Mono.Meta" />
                                </LinearLayout>
                            </LinearLayout>
                        </LinearLayout>

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btn_recent_continue"
                            style="@style/TTC.Button.Primary"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="14dp"
                            android:text="@string/drills_continue_training"
                            app:icon="@drawable/ic_play_arrow"
                            app:iconGravity="textStart" />
                    </LinearLayout>
                </com.google.android.material.card.MaterialCardView>
            </LinearLayout>

            <!-- ALL PROGRAMS header -->
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                android:layout_marginBottom="4dp"
                android:text="@string/drills_section_all_programs"
                android:textAppearance="@style/TextAppearance.TTC.Eyebrow" />
        </LinearLayout>
    </com.google.android.material.appbar.AppBarLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_drills"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:paddingHorizontal="20dp"
        android:paddingBottom="96dp"
        app:layout_behavior="@string/appbar_scrolling_view_behavior" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_add_custom_drill"
        style="@style/TTC.Button.Ghost"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:layout_marginHorizontal="20dp"
        android:layout_marginBottom="20dp"
        android:background="@drawable/bg_dashed_outline"
        android:text="@string/drills_add_own_exercise" />

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_add_drill"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="20dp"
        android:src="@drawable/ic_plus"
        android:contentDescription="@string/drills_add_own_exercise"
        app:backgroundTint="@color/ttc_gold_bright"
        app:tint="@color/ttc_on_gold" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

NOTE ON IDS/STRINGS: this layout references existing resources `@string/nav_drills`, `@string/drills_subtitle`, `@drawable/ic_target`, `@drawable/ic_play_arrow`, `@drawable/ic_plus`, and `@style/TextAppearance.TTC.Title.Screen`. If the build reports any of these unresolved, grep the current `fragment_drills.xml` (git history) and `strings.xml` for the actual names used in the old header/featured card and substitute — do not invent new ones. `ic_play_arrow` specifically: if absent, use `ic_target` as the button icon or drop `app:icon`.

- [ ] **Step 2: Verify the layout compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. If it fails on an unresolved resource, fix per the note above, then re-run. (DrillsFragment still compiles because all removed views — featured card, chips — are wired in Task 6; if the current fragment references `card_featured_drill`/`chip_*`/`scroll_chips` view IDs, this build will fail — proceed to Task 6 in the same working session and treat Task 5+6 as one build-green unit, committing Task 5 only after Task 6 compiles. If the current fragment does NOT reference those IDs, commit now.)

- [ ] **Step 3: Commit (if build green; otherwise commit with Task 6)**

```bash
git add app/src/main/res/layout/fragment_drills.xml
git commit -m "feat(drills): restructure layout to RECENT + ALL PROGRAMS + FAB"
```

---

### Task 6: Wire `DrillsFragment` — recent card, split, FAB, add flow

Bind the RECENT card from the latest `TrainingSession`, split the list via `DrillActions`, point the FAB + dashed button at the existing add-custom flow, and remove references to the deleted featured-card/chip views.

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`

**Interfaces:**
- Consumes: `DrillActions.partition(...)` (Task 1); `TrainingDao.getMostRecentSession()` (Task 2); view IDs from Task 5.
- Produces: `DrillsFragment.currentDrills: List<Exercise>` (the merged built-in + custom list already built today, reused by Tasks 7–8 for menu/swipe).

- [ ] **Step 1: Remove featured-card + filter-chip wiring**

In `DrillsFragment`, delete every reference to the removed views: `card_featured_drill` / featured start button, `chip_all` / `chip_beginner` / `chip_intermediate` / `chip_advanced`, `scroll_chips`, and any difficulty-filter state/handlers. Grep the file for `chip_`, `featured`, `filter` and remove those blocks. The merged-list builder (`builtInExercises + customExercises`) and `ExerciseAdapter` setup stay.

- [ ] **Step 2: Add TrainingDao access + recent binding**

Where `customDrillRepo` is initialized, add a training DAO handle:

```kotlin
private val trainingDao by lazy {
    AppDatabase.getDatabase(requireContext()).trainingDao()
}
```

(If the DAO accessor on `AppDatabase` is named differently, grep `AppDatabase.kt` for the `TrainingDao` accessor and use that name.)

Add a bind function that runs after the merged list is built and the adapter updated:

```kotlin
private fun bindRecentAndPrograms(allDrills: List<Exercise>) {
    viewLifecycleOwner.lifecycleScope.launch {
        val session = withContext(Dispatchers.IO) { trainingDao.getMostRecentSession() }
        val (recent, programs) = DrillActions.partition(allDrills, session?.exerciseId)

        val sectionRecent = requireView().findViewById<View>(R.id.section_recent)
        if (recent == null || session == null) {
            sectionRecent.visibility = View.GONE
        } else {
            sectionRecent.visibility = View.VISIBLE
            requireView().findViewById<TextView>(R.id.tv_recent_name).text = recent.name
            requireView().findViewById<ImageView>(R.id.iv_recent_icon)
                .setImageResource(iconForDrill(recent.id))
            requireView().findViewById<TextView>(R.id.tv_recent_date).text =
                DateUtils.getRelativeTimeSpanString(
                    session.startTime, System.currentTimeMillis(),
                    DateUtils.DAY_IN_MILLIS
                ).toString().uppercase()

            val accuracyView = requireView().findViewById<TextView>(R.id.tv_recent_accuracy)
            val pct = if (session.accuracy <= 1f) (session.accuracy * 100).toInt()
                      else session.accuracy.toInt()
            accuracyView.text = getString(R.string.drills_recent_accuracy, pct)

            requireView().findViewById<MaterialButton>(R.id.btn_recent_continue)
                .setOnClickListener { onExerciseSelected(recent) }
        }

        exerciseAdapter.updateList(programs)
    }
}

private fun iconForDrill(id: String): Int = when (id) {
    "backhand_loop" -> R.drawable.ic_trending_up
    "footwork_drill" -> R.drawable.ic_person
    "multiball_rally" -> R.drawable.ic_alert_circle
    "consistency_challenge" -> R.drawable.ic_check_circle_2
    else -> R.drawable.ic_target
}
```

Add the imports this needs: `android.text.format.DateUtils`, `android.view.View`, `android.widget.ImageView`, `android.widget.TextView`, `com.google.android.material.button.MaterialButton`, `androidx.lifecycle.lifecycleScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.withContext`. (`iconForDrill` duplicates the mapping in `ExerciseAdapter`; acceptable — the adapter's copy binds rows, this one binds the standalone recent card. Do not refactor the adapter in this slice.)

- [ ] **Step 3: Route the merged-list flow through `bindRecentAndPrograms`**

Find where the fragment currently calls `exerciseAdapter.updateList(builtInExercises + customExercises)` (in `reloadCustomDrills()` / initial load). Replace the direct `updateList(...)` call with `bindRecentAndPrograms(builtInExercises + customExercises)`, and store the merged list in a field for Tasks 7–8:

```kotlin
private var currentDrills: List<Exercise> = emptyList()
```

Set `currentDrills = allDrills` at the top of `bindRecentAndPrograms`.

- [ ] **Step 4: Wire the FAB + dashed add button to the existing add flow**

In `onViewCreated` (or wherever `btn_add_custom_drill` is currently wired), point both entry points at the existing `launchCustomDrillCalibration()`:

```kotlin
view.findViewById<MaterialButton>(R.id.btn_add_custom_drill)
    .setOnClickListener { launchCustomDrillCalibration() }
view.findViewById<FloatingActionButton>(R.id.fab_add_drill)
    .setOnClickListener { launchCustomDrillCalibration() }
```

Add import `com.google.android.material.floatingactionbutton.FloatingActionButton`.

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Fix any remaining references to deleted views.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/fragment_drills.xml app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt
git commit -m "feat(drills): RECENT card from session history + list split + FAB add flow"
```

---

### Task 7: Long-press action menu (rule-gated)

Replace the existing two-item long-click dialog with the full Continue/Edit/Clone/Rename/Delete menu, filtered by `DrillActions`.

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`

**Interfaces:**
- Consumes: `DrillActions` predicates; `currentDrills`; `customDrillRepo`; existing `onExerciseSelected`, `reloadCustomDrills`, `CUSTOM_DRILL_PREFIX`.
- Produces: `cloneDrill(source: Exercise)`, `renameDrill(exercise: Exercise)`, `deleteDrill(exercise: Exercise)` — reused by Task 8 swipe actions.

- [ ] **Step 1: Replace `showDrillOptions` with a rule-gated menu**

Replace the body of the existing long-click handler (`showDrillOptions`) with:

```kotlin
private fun showDrillOptions(exercise: Exercise) {
    data class Action(val label: String, val run: () -> Unit)
    val actions = buildList {
        if (DrillActions.canContinue(exercise))
            add(Action(getString(R.string.drill_action_continue)) { onExerciseSelected(exercise) })
        if (DrillActions.canEdit(exercise))
            add(Action(getString(R.string.drill_action_edit)) { launchCustomDrillCalibration() })
        if (DrillActions.canClone(exercise))
            add(Action(getString(R.string.drill_action_clone)) { cloneDrill(exercise) })
        if (DrillActions.canRename(exercise))
            add(Action(getString(R.string.drill_action_rename)) { renameDrill(exercise) })
        if (DrillActions.canDelete(exercise))
            add(Action(getString(R.string.drill_action_delete)) { deleteDrill(exercise) })
    }
    val labels = actions.map { it.label }.toTypedArray()
    AlertDialog.Builder(requireContext())
        .setTitle(exercise.name)
        .setItems(labels) { _, which -> actions[which].run() }
        .show()
}
```

- [ ] **Step 2: Add clone / rename / delete helpers**

```kotlin
private fun cloneDrill(source: Exercise) {
    viewLifecycleOwner.lifecycleScope.launch {
        val newType = "${CUSTOM_DRILL_PREFIX}${System.currentTimeMillis()}"
        val copyName = "${source.name} ${getString(R.string.drill_clone_copy_suffix)}"
        val baseTemplate = if (DrillActions.isCustom(source)) {
            withContext(Dispatchers.IO) { customDrillRepo.get(source.id) }?.baseTemplate ?: source.id
        } else source.id
        val entity = CustomDrillEntity(
            drillType = newType,
            name = copyName,
            baseTemplate = baseTemplate,
            createdAtMs = System.currentTimeMillis()
        )
        withContext(Dispatchers.IO) { customDrillRepo.save(entity) }
        Toast.makeText(requireContext(),
            getString(R.string.drill_clone_toast, copyName), Toast.LENGTH_SHORT).show()
        reloadCustomDrills()
    }
}

private fun renameDrill(exercise: Exercise) {
    val input = EditText(requireContext()).apply { setText(exercise.name) }
    AlertDialog.Builder(requireContext())
        .setTitle(R.string.drill_rename_title)
        .setView(input)
        .setPositiveButton(R.string.drill_rename_save) { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val existing = withContext(Dispatchers.IO) { customDrillRepo.get(exercise.id) }
                    if (existing != null) {
                        withContext(Dispatchers.IO) {
                            customDrillRepo.save(existing.copy(name = newName))
                        }
                        reloadCustomDrills()
                    }
                }
            }
        }
        .setNegativeButton(R.string.drill_cancel, null)
        .show()
}

private fun deleteDrill(exercise: Exercise) {
    AlertDialog.Builder(requireContext())
        .setTitle(R.string.drill_delete_title)
        .setMessage(getString(R.string.drill_delete_message, exercise.name))
        .setPositiveButton(R.string.drill_delete_confirm) { _, _ ->
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) { customDrillRepo.delete(exercise.id) }
                Toast.makeText(requireContext(),
                    getString(R.string.drill_delete_toast, exercise.name), Toast.LENGTH_SHORT).show()
                reloadCustomDrills()
            }
        }
        .setNegativeButton(R.string.drill_cancel, null)
        .show()
}
```

Add imports: `android.widget.EditText`, `android.widget.Toast`, `com.ttcoachai.models.CustomDrillEntity` (confirm the package via grep — the entity lives in `com.ttcoachai.models`).

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt
git commit -m "feat(drills): rule-gated long-press menu with clone/rename/delete"
```

---

### Task 8: Swipe-to-Clone / swipe-to-Delete (rule-gated)

Attach an `ItemTouchHelper` to the ALL PROGRAMS RecyclerView: swipe-left = Clone (gold), swipe-right = Delete (red). Per-row gating: built-ins allow Clone only.

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`

**Interfaces:**
- Consumes: `currentDrills`, `programs` order via the adapter, `DrillActions`, `cloneDrill`, `deleteDrill` (Task 7), `exerciseAdapter`.

- [ ] **Step 1: Add a helper to resolve the swiped row's Exercise**

The adapter is bound to the `programs` list (recent excluded). Add a field capturing the last-bound programs list so the swipe callback can map an adapter position to an `Exercise`:

```kotlin
private var boundPrograms: List<Exercise> = emptyList()
```

In `bindRecentAndPrograms`, right before `exerciseAdapter.updateList(programs)`, set `boundPrograms = programs`.

- [ ] **Step 2: Attach the ItemTouchHelper after adapter setup**

In `onViewCreated`, after the RecyclerView + adapter are set up, add:

```kotlin
val swipeCallback = object : ItemTouchHelper.SimpleCallback(
    0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
) {
    private val goldPaint = Paint().apply { color = ContextCompat.getColor(requireContext(), R.color.ttc_gold_bright) }
    private val redPaint = Paint().apply { color = ContextCompat.getColor(requireContext(), R.color.ttc_error) }

    override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
        val pos = vh.bindingAdapterPosition
        val ex = boundPrograms.getOrNull(pos) ?: return 0
        // Built-ins: Clone (left) only. Custom: Clone (left) + Delete (right).
        return if (DrillActions.canDelete(ex)) ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
               else ItemTouchHelper.LEFT
    }

    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
        val ex = boundPrograms.getOrNull(vh.bindingAdapterPosition) ?: run {
            exerciseAdapter.notifyDataSetChanged(); return
        }
        when (direction) {
            ItemTouchHelper.LEFT -> cloneDrill(ex)                       // swipe left → Clone
            ItemTouchHelper.RIGHT -> if (DrillActions.canDelete(ex)) deleteDrill(ex)
                                     else exerciseAdapter.notifyDataSetChanged()
        }
        // reloadCustomDrills() inside clone/delete rebinds the list; snap the row back meanwhile
        exerciseAdapter.notifyItemChanged(vh.bindingAdapterPosition)
    }

    override fun onChildDraw(
        c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        val item = vh.itemView
        if (dX < 0) {
            c.drawRect(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat(), goldPaint)
        } else if (dX > 0) {
            c.drawRect(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat(), redPaint)
        }
        super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
    }
}
ItemTouchHelper(swipeCallback).attachToRecyclerView(view.findViewById(R.id.rv_drills))
```

Add imports: `android.graphics.Canvas`, `android.graphics.Paint`, `androidx.core.content.ContextCompat`, `androidx.recyclerview.widget.ItemTouchHelper`, `androidx.recyclerview.widget.RecyclerView`.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt
git commit -m "feat(drills): swipe-to-clone/delete with per-row action gating"
```

---

### Task 9: Document deferred analyzer work

Log the non-forehand analyzer generalization so the deferred scope is tracked.

**Files:**
- Modify: `docs/DESIGN_LIMITATIONS.md`

- [ ] **Step 1: Add the L-entry**

Append a new L-numbered entry to `docs/DESIGN_LIMITATIONS.md` (use the next free L-number; grep the file for the highest existing `L-` id first):

```markdown
## L-NN — Non-forehand drills reuse forehand-tuned detection

`StrokeDetector2D` / `ForwardStrokeFilter` are tuned for the forehand drive. The
Exercises tab (2026-07-02 redesign) launches every program — backhand, footwork,
multiball, custom clones — through the existing training flow, but only forehand
drills produce calibrated feedback. Editing params for a non-forehand drill (this
slice / the next editor slice) does not yet yield accurate coaching. Generalizing
stroke detection per drill type is deferred; until then, non-forehand feedback
accuracy is not claimed. Spec: docs/superpowers/specs/2026-07-02-android-exercises-tab-gold-dark-design.md.
```

- [ ] **Step 2: Commit**

```bash
git add docs/DESIGN_LIMITATIONS.md
git commit -m "docs: L-entry for deferred non-forehand analyzer generalization"
```

---

### Task 10: Manual QA + screenshots

No shared-KMP logic changed, so device verification closes the loop.

**Files:** none (verification only).

- [ ] **Step 1: Install and launch**

Run: `./gradlew :app:installDebug` then open the Exercises tab.

- [ ] **Step 2: Verify against spec 2a / 10a / 10b**

Confirm and screencap (adb screencaps → `tmp/screenshots/`):
- RECENT card renders with real "Last session · N% accuracy" (green) when a session exists; whole RECENT section hidden on first run (empty `training_sessions`).
- ALL PROGRAMS lists every drill except the recent one; no difficulty filter chips present.
- Long-press a built-in → menu shows Continue + Clone only; long-press a custom → Continue/Edit/Clone/Rename/Delete.
- Swipe a custom row left → gold Clone panel → creates "(copy)"; swipe right → red Delete panel → confirm → removed. Swipe a built-in right → no delete (springs back); swipe left → Clone.
- FAB and dashed button both launch the calibration add-custom flow.
- Recheck in light theme.

- [ ] **Step 3: Save screenshots**

```bash
adb exec-out screencap -p > tmp/screenshots/drills-2a-recent.png
adb exec-out screencap -p > tmp/screenshots/drills-longpress-menu.png
```

(No commit — screenshots are scratch artifacts under `tmp/`, gitignored.)

---

## Self-Review Notes

- **Spec coverage:** 2a list (RECENT + ALL PROGRAMS + empty state) → Tasks 5–6; chips removed → Task 6 Step 1; 10a swipe + FAB → Tasks 5, 8; 10b menu → Task 7; action rule → Tasks 1, 7, 8; bilingual strings → Task 3; deferred L-entry → Task 9; testing → Tasks 1, 10. Editor (10c/10d) is spec-only per the design doc — not in this plan; add-entry points route to the existing calibration flow (Task 6 Step 4, Task 7 Edit action) as the placeholder.
- **Open dependency resolved:** session-history source = `TrainingSession` (table `training_sessions`, `accuracy: Float`) via new `TrainingDao.getMostRecentSession()` (Task 2). Accuracy shown as percent with a fraction-vs-percent guard (Task 6 Step 2). No session → RECENT hidden.
- **Type consistency:** `partition(all, recentId): Pair<Exercise?, List<Exercise>>` defined in Task 1, consumed in Task 6. `cloneDrill`/`renameDrill`/`deleteDrill` defined in Task 7, reused in Task 8. `boundPrograms`/`currentDrills` fields introduced where first needed.
- **Grep-guarded assumptions:** resource names (`nav_drills`, `drills_subtitle`, `ic_play_arrow`), the `AppDatabase.trainingDao()` accessor, and `CustomDrillEntity`'s package are flagged for verification during implementation rather than assumed, since the Explore pass did not confirm every one verbatim.
