# Drills Swipe-to-Reveal Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fire-on-release `ItemTouchHelper` swipe on the Drills list with a persistent swipe-to-reveal row whose Clone (gold, right edge) / Delete (red, left edge) actions run only on a deliberate button tap.

**Architecture:** A new Android-only custom view `SwipeRevealLayout` (a `FrameLayout` driven by AndroidX `ViewDragHelper`) hosts three layers — a left Delete panel, a right Clone panel, and the existing opaque `MaterialCardView` foreground. The settle decision (which state a release snaps to) and the drag clamp are extracted into a pure, Android-free `SwipeSettle.kt` so they are JVM-unit-testable. `ExerciseAdapter` wires the panel buttons, resets recycled rows to closed, and enforces one-open-row-at-a-time; `DrillsFragment` drops the old `ItemTouchHelper` and closes any open row on scroll.

**Tech Stack:** Kotlin, Android View/RecyclerView stack (no Compose), AndroidX `androidx.customview.widget.ViewDragHelper` (ships transitively with `androidx.recyclerview:recyclerview:1.3.2`), Material `MaterialCardView`, JUnit 4 unit tests.

## Global Constraints

- **Presentation/interaction only.** Do NOT touch pose/ball/trajectory pipeline code. Only the Drills list row UI + adapter/fragment wiring.
- **No third-party swipe library.** Use AndroidX `ViewDragHelper` (repo lean-on-platform convention).
- **Preserve business logic exactly.** `cloneDrill()` and `deleteDrill()` bodies stay as-is; the Delete confirm `AlertDialog` and the Clone success toast are preserved.
- **Reuse existing resources** — strings `drill_action_clone` / `drill_action_delete` (EN + UA already exist), colors `@color/ttc_gold_bright` (`#E9C46A`) and `@color/ttc_error` (`#C4463C`). Legible text-on-gold = `@color/ttc_on_gold` (`#2A2008`); text-on-red = `@android:color/white`.
- **Directional gating:** built-in presets cannot be deleted — `DrillActions.canDelete(exercise)` is `true` only for custom drills (id starts with `custom_`). Delete side is enabled per-row from that.
- **Commit after each task.** `git add` explicit paths only — never `git add -A`.

---

## File Structure

- `app/src/main/java/com/ttcoachai/ui/SwipeSettle.kt` — **new.** Pure, Android-free: `SwipeState` enum, `decideSwipeSettle(...)`, `clampSwipeOffset(...)`, `DEFAULT_FLING_VELOCITY`. The unit-tested surface.
- `app/src/test/java/com/ttcoachai/ui/SwipeSettleTest.kt` — **new.** JUnit4 tests for the two pure functions.
- `app/src/main/java/com/ttcoachai/ui/SwipeRevealLayout.kt` — **new.** Custom `FrameLayout` + `ViewDragHelper`; consumes `SwipeSettle`.
- `app/src/main/res/drawable/ic_content_copy.xml`, `ic_trash.xml` — **new** vector glyphs (neither exists today).
- `app/src/main/res/layout/item_exercise.xml` — **restructured** into the 3-layer reveal (root becomes `SwipeRevealLayout`).
- `app/src/main/java/com/ttcoachai/ExerciseAdapter.kt` — **modified.** New Clone/Delete callbacks, reset-on-bind, single-open coordinator, button + foreground wiring.
- `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt` — **modified.** Remove `attachSwipeActions()`/`ItemTouchHelper`, pass new callbacks, close-open-row on scroll, drop `boundPrograms`.

---

## Task 1: Pure settle core + unit tests

**Files:**
- Create: `app/src/main/java/com/ttcoachai/ui/SwipeSettle.kt`
- Test: `app/src/test/java/com/ttcoachai/ui/SwipeSettleTest.kt`

**Interfaces:**
- Produces (consumed by Tasks 2 & 3):
  - `enum class SwipeState { CLOSED, OPEN_CLONE, OPEN_DELETE }`
  - `const val DEFAULT_FLING_VELOCITY: Float` (= `1000f`, px/s)
  - `fun clampSwipeOffset(offsetX: Float, buttonWidth: Float, deleteEnabled: Boolean): Float`
  - `fun decideSwipeSettle(offsetX: Float, velocityX: Float, buttonWidth: Float, deleteEnabled: Boolean, flingVelocityThreshold: Float = DEFAULT_FLING_VELOCITY): SwipeState`
- Sign convention: `offsetX`/`velocityX` are the foreground's left position / release velocity in px, px/s. **Negative = dragged left toward the Clone panel** (right edge); **positive = dragged right toward the Delete panel** (left edge).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/ttcoachai/ui/SwipeSettleTest.kt`:

```kotlin
package com.ttcoachai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeSettleTest {

    private val w = 88f          // button/panel width (px in tests)
    private val fling = DEFAULT_FLING_VELOCITY

    // --- decideSwipeSettle: threshold (no fling) ---

    @Test fun `small left drag below half closes`() {
        assertEquals(SwipeState.CLOSED, decideSwipeSettle(-20f, 0f, w, deleteEnabled = true))
    }

    @Test fun `left drag past half opens clone`() {
        assertEquals(SwipeState.OPEN_CLONE, decideSwipeSettle(-50f, 0f, w, deleteEnabled = true))
    }

    @Test fun `small right drag below half closes`() {
        assertEquals(SwipeState.CLOSED, decideSwipeSettle(20f, 0f, w, deleteEnabled = true))
    }

    @Test fun `right drag past half opens delete when enabled`() {
        assertEquals(SwipeState.OPEN_DELETE, decideSwipeSettle(50f, 0f, w, deleteEnabled = true))
    }

    @Test fun `exactly half opens that side`() {
        assertEquals(SwipeState.OPEN_CLONE, decideSwipeSettle(-44f, 0f, w, deleteEnabled = true))
        assertEquals(SwipeState.OPEN_DELETE, decideSwipeSettle(44f, 0f, w, deleteEnabled = true))
    }

    // --- decideSwipeSettle: delete-disabled lock ---

    @Test fun `right drag past half stays closed when delete disabled`() {
        assertEquals(SwipeState.CLOSED, decideSwipeSettle(80f, 0f, w, deleteEnabled = false))
    }

    @Test fun `left drag still opens clone when delete disabled`() {
        assertEquals(SwipeState.OPEN_CLONE, decideSwipeSettle(-80f, 0f, w, deleteEnabled = false))
    }

    // --- decideSwipeSettle: fling overrides a below-half offset ---

    @Test fun `fast fling left opens clone despite small offset`() {
        assertEquals(SwipeState.OPEN_CLONE, decideSwipeSettle(-5f, -fling, w, deleteEnabled = true))
    }

    @Test fun `fast fling right opens delete despite small offset when enabled`() {
        assertEquals(SwipeState.OPEN_DELETE, decideSwipeSettle(5f, fling, w, deleteEnabled = true))
    }

    @Test fun `fast fling right stays closed when delete disabled`() {
        assertEquals(SwipeState.CLOSED, decideSwipeSettle(5f, fling, w, deleteEnabled = false))
    }

    @Test fun `zero offset zero velocity closes`() {
        assertEquals(SwipeState.CLOSED, decideSwipeSettle(0f, 0f, w, deleteEnabled = true))
    }

    // --- clampSwipeOffset ---

    @Test fun `clamp allows both sides when delete enabled`() {
        assertEquals(-88f, clampSwipeOffset(-200f, w, deleteEnabled = true), 0f)
        assertEquals(88f, clampSwipeOffset(200f, w, deleteEnabled = true), 0f)
    }

    @Test fun `clamp blocks right side when delete disabled`() {
        assertEquals(-88f, clampSwipeOffset(-200f, w, deleteEnabled = false), 0f)
        assertEquals(0f, clampSwipeOffset(200f, w, deleteEnabled = false), 0f)
    }

    @Test fun `clamp passes through in-range value`() {
        assertEquals(-30f, clampSwipeOffset(-30f, w, deleteEnabled = true), 0f)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.ui.SwipeSettleTest"`
Expected: FAIL — `Unresolved reference: SwipeState` / `decideSwipeSettle` / `clampSwipeOffset` (compilation error, source not yet created).

- [ ] **Step 3: Write the pure implementation**

Create `app/src/main/java/com/ttcoachai/ui/SwipeSettle.kt`:

```kotlin
package com.ttcoachai.ui

/**
 * Pure, Android-free settle logic for [SwipeRevealLayout]. Kept in its own file with no
 * Android imports so it is unit-testable on the JVM without Robolectric.
 *
 * Sign convention: a foreground offset / velocity that is NEGATIVE means the foreground was
 * dragged LEFT, exposing the Clone panel on the right edge; POSITIVE means dragged RIGHT,
 * exposing the Delete panel on the left edge.
 */

enum class SwipeState { CLOSED, OPEN_CLONE, OPEN_DELETE }

/** Default release fling speed (px/s) above which a swipe opens regardless of distance. */
const val DEFAULT_FLING_VELOCITY: Float = 1000f

/**
 * Clamp the foreground's left offset to the range permitted by which sides are enabled.
 * Clone (left drag) is always allowed; Delete (right drag) only when [deleteEnabled].
 */
fun clampSwipeOffset(offsetX: Float, buttonWidth: Float, deleteEnabled: Boolean): Float {
    val min = -buttonWidth
    val max = if (deleteEnabled) buttonWidth else 0f
    return offsetX.coerceIn(min, max)
}

/**
 * Decide which state a released drag settles to. Crossing ~half the button width, or a fling
 * faster than [flingVelocityThreshold] in that direction, opens the side; otherwise it closes.
 * A blocked side (Delete when [deleteEnabled] is false) can only resolve to [SwipeState.CLOSED].
 */
fun decideSwipeSettle(
    offsetX: Float,
    velocityX: Float,
    buttonWidth: Float,
    deleteEnabled: Boolean,
    flingVelocityThreshold: Float = DEFAULT_FLING_VELOCITY,
): SwipeState {
    val half = buttonWidth / 2f
    if (offsetX < 0f) {
        val open = -offsetX >= half || velocityX <= -flingVelocityThreshold
        return if (open) SwipeState.OPEN_CLONE else SwipeState.CLOSED
    }
    if (offsetX > 0f && deleteEnabled) {
        val open = offsetX >= half || velocityX >= flingVelocityThreshold
        return if (open) SwipeState.OPEN_DELETE else SwipeState.CLOSED
    }
    return SwipeState.CLOSED
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.ui.SwipeSettleTest"`
Expected: PASS (all 14 tests green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ttcoachai/ui/SwipeSettle.kt \
        app/src/test/java/com/ttcoachai/ui/SwipeSettleTest.kt
git commit -m "feat(drills): pure swipe settle/clamp logic + unit tests"
```

---

## Task 2: Reveal drawables, row layout, and SwipeRevealLayout custom view

This task is a single unit because ViewBinding compiles the layout against the custom-view class and the ids, so the drawables, the layout, and the class must all land together for the build to compile. No unit test (gesture feel is device-verified per the spec); the deliverable is a green `:app:assembleDebug` and the custom view ready for wiring.

**Files:**
- Create: `app/src/main/res/drawable/ic_content_copy.xml`
- Create: `app/src/main/res/drawable/ic_trash.xml`
- Create: `app/src/main/java/com/ttcoachai/ui/SwipeRevealLayout.kt`
- Modify (full rewrite): `app/src/main/res/layout/item_exercise.xml`

**Interfaces:**
- Consumes (from Task 1): `SwipeState`, `decideSwipeSettle`, `clampSwipeOffset`, `DEFAULT_FLING_VELOCITY`.
- Produces (consumed by Task 3 via ViewBinding `ItemExerciseBinding`):
  - Layout ids: `@id/swipe_root` (the `SwipeRevealLayout`), `@id/swipe_foreground` (the `MaterialCardView`), `@id/swipe_clone_panel`, `@id/swipe_delete_panel`. Existing content ids (`tv_exercise_name`, `tv_exercise_description`, `tv_difficulty`, `tv_duration`, `tv_category`, `iv_exercise_icon`, `fl_icon_container`, `iv_chevron`) are preserved.
  - `SwipeRevealLayout` public API: `val isOpen: Boolean`, `val state: SwipeState`, `fun setDeleteEnabled(enabled: Boolean)`, `fun close(animate: Boolean)`, `fun open(side: SwipeState, animate: Boolean)`, `var onStateChanged: ((SwipeState) -> Unit)?`.

- [ ] **Step 1: Create the Clone glyph**

Create `app/src/main/res/drawable/ic_content_copy.xml` (Material `content_copy`, white baseline; tint is overridden per-panel in the layout):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z"/>
</vector>
```

- [ ] **Step 2: Create the Delete glyph**

Create `app/src/main/res/drawable/ic_trash.xml` (Material `delete`, white baseline):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6V19zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"/>
</vector>
```

- [ ] **Step 3: Rewrite the row layout into 3 layers**

Overwrite `app/src/main/res/layout/item_exercise.xml`. The root is now `SwipeRevealLayout` carrying the 12dp bottom gap the card used to carry; the Delete panel (`gravity=start`), the Clone panel (`gravity=end`), then the opaque foreground card last (drawn on top, hides both panels when closed):

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.ttcoachai.ui.SwipeRevealLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/swipe_root"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="12dp">

    <!-- Delete panel (left edge) — revealed by swiping RIGHT -->
    <LinearLayout
        android:id="@+id/swipe_delete_panel"
        android:layout_width="88dp"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        android:orientation="vertical"
        android:gravity="center"
        android:clickable="true"
        android:focusable="true"
        android:background="@color/ttc_error"
        android:contentDescription="@string/drill_action_delete">

        <ImageView
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@drawable/ic_trash"
            app:tint="@android:color/white"
            android:contentDescription="@null"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="@string/drill_action_delete"
            android:textColor="@android:color/white"
            android:textAppearance="@style/TextAppearance.TTC.Body.Secondary"/>
    </LinearLayout>

    <!-- Clone panel (right edge) — revealed by swiping LEFT -->
    <LinearLayout
        android:id="@+id/swipe_clone_panel"
        android:layout_width="88dp"
        android:layout_height="match_parent"
        android:layout_gravity="end"
        android:orientation="vertical"
        android:gravity="center"
        android:clickable="true"
        android:focusable="true"
        android:background="@color/ttc_gold_bright"
        android:contentDescription="@string/drill_action_clone">

        <ImageView
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@drawable/ic_content_copy"
            app:tint="@color/ttc_on_gold"
            android:contentDescription="@null"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="@string/drill_action_clone"
            android:textColor="@color/ttc_on_gold"
            android:textAppearance="@style/TextAppearance.TTC.Body.Secondary"/>
    </LinearLayout>

    <!-- Foreground: the existing card (opaque so panels are hidden when closed) -->
    <com.google.android.material.card.MaterialCardView
        android:id="@+id/swipe_foreground"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        style="@style/TTC.Card">

        <androidx.constraintlayout.widget.ConstraintLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="16dp">

            <FrameLayout
                android:id="@+id/fl_icon_container"
                android:layout_width="56dp"
                android:layout_height="56dp"
                android:background="@drawable/bg_icon_tile_gold"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintTop_toTopOf="parent">

                <ImageView
                    android:id="@+id/iv_exercise_icon"
                    android:layout_width="28dp"
                    android:layout_height="28dp"
                    android:contentDescription="@null"
                    android:src="@drawable/ic_target"
                    app:tint="@color/ttc_gold_accent"
                    android:layout_gravity="center" />
            </FrameLayout>

            <LinearLayout
                android:id="@+id/ll_content"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:orientation="vertical"
                app:layout_constraintEnd_toStartOf="@id/iv_chevron"
                app:layout_constraintStart_toEndOf="@id/fl_icon_container"
                app:layout_constraintTop_toTopOf="parent">

                <TextView
                    android:id="@+id/tv_exercise_name"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/exercise_forehand_name"
                    android:textAppearance="@style/TextAppearance.TTC.Title.Card"/>

                <TextView
                    android:id="@+id/tv_exercise_description"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/exercise_forehand_desc"
                    android:textAppearance="@style/TextAppearance.TTC.Body.Secondary"
                    android:maxLines="2"
                    android:ellipsize="end"
                    android:layout_marginTop="2dp"/>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:layout_marginTop="8dp">

                    <TextView
                        android:id="@+id/tv_difficulty"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/difficulty_beginner"
                        android:textAppearance="@style/TextAppearance.TTC.Body.Secondary"
                        android:textColor="@color/ttc_text_2"
                        android:layout_marginEnd="8dp"/>

                    <TextView
                        android:id="@+id/tv_duration"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/duration_10_15"
                        android:textAppearance="@style/TextAppearance.TTC.Mono.Meta"/>

                    <TextView
                        android:id="@+id/tv_category"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/label_technique"
                        android:textAppearance="@style/TextAppearance.TTC.Body.Secondary"
                        android:textColor="@color/ttc_text_2"
                        android:layout_marginStart="8dp"/>
                </LinearLayout>
            </LinearLayout>

            <ImageView
                android:id="@+id/iv_chevron"
                android:layout_width="20dp"
                android:layout_height="20dp"
                android:src="@drawable/ic_chevron_right"
                app:tint="@color/ttc_text_3"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintBottom_toBottomOf="parent"/>

        </androidx.constraintlayout.widget.ConstraintLayout>

    </com.google.android.material.card.MaterialCardView>

</com.ttcoachai.ui.SwipeRevealLayout>
```

- [ ] **Step 4: Write the SwipeRevealLayout custom view**

Create `app/src/main/java/com/ttcoachai/ui/SwipeRevealLayout.kt`:

```kotlin
package com.ttcoachai.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.customview.widget.ViewDragHelper
import com.ttcoachai.R
import kotlin.math.abs

/**
 * A swipe-to-reveal row: the [foreground] card slides horizontally over two fixed-width action
 * panels (Clone on the right edge, Delete on the left) and STAYS open until the revealed button
 * is tapped or the row is closed. Only the foreground is draggable; the settle decision and drag
 * clamp live in the pure [SwipeSettle] helpers so they can be unit-tested.
 */
class SwipeRevealLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private lateinit var foreground: View
    private lateinit var clonePanel: View
    private lateinit var deletePanel: View

    private var buttonWidth = 0
    private var deleteEnabled = true

    var state: SwipeState = SwipeState.CLOSED
        private set

    val isOpen: Boolean get() = state != SwipeState.CLOSED

    /** Fires whenever the settled state changes (open or close). Used by the single-open coordinator. */
    var onStateChanged: ((SwipeState) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f

    private val dragHelper = ViewDragHelper.create(this, 1f, DragCallback())

    override fun onFinishInflate() {
        super.onFinishInflate()
        deletePanel = findViewById(R.id.swipe_delete_panel)
        clonePanel = findViewById(R.id.swipe_clone_panel)
        foreground = findViewById(R.id.swipe_foreground)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        buttonWidth = clonePanel.width
        // super.onLayout puts the match_parent foreground at left=0; restore the open offset
        // (matters when a recycled/laid-out row should stay open).
        val target = leftFor(state)
        if (foreground.left != target) foreground.offsetLeftAndRight(target - foreground.left)
    }

    /** Locks/unlocks the right (Delete) side. Built-in presets pass false. */
    fun setDeleteEnabled(enabled: Boolean) {
        deleteEnabled = enabled
    }

    fun close(animate: Boolean) = moveTo(SwipeState.CLOSED, animate)

    fun open(side: SwipeState, animate: Boolean) {
        if (side == SwipeState.OPEN_DELETE && !deleteEnabled) return
        moveTo(side, animate)
    }

    private fun leftFor(s: SwipeState): Int = when (s) {
        SwipeState.CLOSED -> 0
        SwipeState.OPEN_CLONE -> -buttonWidth
        SwipeState.OPEN_DELETE -> buttonWidth
    }

    private fun moveTo(target: SwipeState, animate: Boolean) {
        val finalLeft = leftFor(target)
        if (animate) {
            if (dragHelper.smoothSlideViewTo(foreground, finalLeft, foreground.top)) {
                postInvalidateOnAnimation()
            }
        } else {
            dragHelper.abort()
            foreground.offsetLeftAndRight(finalLeft - foreground.left)
        }
        setStateInternal(target)
    }

    private fun setStateInternal(newState: SwipeState) {
        if (newState != state) {
            state = newState
            onStateChanged?.invoke(newState)
        }
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) postInvalidateOnAnimation()
    }

    private fun isTouchInForeground(ev: MotionEvent): Boolean {
        val x = ev.x.toInt()
        return x >= foreground.left && x <= foreground.right
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (dragHelper.shouldInterceptTouchEvent(ev)) return true
        // When open, a tap on the slid-away foreground closes the row instead of passing through
        // as a select. Taps on the exposed panel (outside the foreground bounds) are NOT
        // intercepted, so the Clone/Delete buttons still receive their clicks.
        return isOpen && ev.actionMasked == MotionEvent.ACTION_DOWN && isTouchInForeground(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> downX = event.x
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val moved = abs(event.x - downX) > touchSlop
                if (!moved && isOpen && isTouchInForeground(event)) {
                    dragHelper.cancel()
                    close(animate = true)
                    return true
                }
            }
        }
        dragHelper.processTouchEvent(event)
        return true
    }

    private inner class DragCallback : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean = child === foreground

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int =
            clampSwipeOffset(left.toFloat(), buttonWidth.toFloat(), deleteEnabled).toInt()

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int = child.top

        override fun getViewHorizontalDragRange(child: View): Int = buttonWidth

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val target = decideSwipeSettle(
                offsetX = foreground.left.toFloat(),
                velocityX = xvel,
                buttonWidth = buttonWidth.toFloat(),
                deleteEnabled = deleteEnabled,
            )
            moveTo(target, animate = true)
        }
    }
}
```

- [ ] **Step 5: Build to verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. If the `androidx.customview.widget.ViewDragHelper` import is unresolved (it ships transitively with recyclerview 1.3.2, so it should resolve), add `implementation 'androidx.customview:customview:1.1.0'` to the `dependencies { }` block of `app/build.gradle` and rebuild.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/drawable/ic_content_copy.xml \
        app/src/main/res/drawable/ic_trash.xml \
        app/src/main/res/layout/item_exercise.xml \
        app/src/main/java/com/ttcoachai/ui/SwipeRevealLayout.kt
git commit -m "feat(drills): swipe-reveal row layout + SwipeRevealLayout custom view"
```

---

## Task 3: Wire Clone/Delete/select through ExerciseAdapter

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/ExerciseAdapter.kt`

**Interfaces:**
- Consumes (from Task 2): `ItemExerciseBinding` root is now `SwipeRevealLayout` (`binding.root`); binding exposes `swipeClonePanel`, `swipeDeletePanel`, `swipeForeground`. `SwipeRevealLayout.{isOpen, close, setDeleteEnabled, onStateChanged}`; `SwipeState`.
- Consumes: `com.ttcoachai.fragment.DrillActions.canDelete(exercise)`.
- Produces (consumed by Task 4): constructor gains `onCloneClick: (Exercise) -> Unit` and `onDeleteClick: (Exercise) -> Unit`; public `fun closeOpenRow()`. `updateList` clears the tracked open row.

- [ ] **Step 1: Add the new constructor callbacks and open-row tracking**

In `app/src/main/java/com/ttcoachai/ExerciseAdapter.kt`, replace the constructor and `updateList` (lines 13–22) with:

```kotlin
class ExerciseAdapter(
    private var exercises: List<Exercise>,
    private val onExerciseClick: (Exercise) -> Unit,
    private val onExerciseLongClick: ((Exercise) -> Unit)? = null,
    private val onCloneClick: (Exercise) -> Unit = {},
    private val onDeleteClick: (Exercise) -> Unit = {}
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    /** The single row currently open, if any — enforces one-open-at-a-time. */
    private var openRow: SwipeRevealLayout? = null

    fun updateList(newExercises: List<Exercise>) {
        exercises = newExercises
        openRow = null
        notifyDataSetChanged()
    }

    /** Closes whichever row is open (used by the fragment's scroll listener). */
    fun closeOpenRow() {
        openRow?.close(animate = true)
    }
```

- [ ] **Step 2: Add the required imports**

At the top of `ExerciseAdapter.kt`, add (alongside the existing imports):

```kotlin
import com.ttcoachai.fragment.DrillActions
import com.ttcoachai.ui.SwipeRevealLayout
import com.ttcoachai.ui.SwipeState
```

- [ ] **Step 3: Rewrite `bind()` to reset, gate, and wire the row**

Replace the body of `fun bind(exercise: Exercise)` (lines 28–72) with:

```kotlin
        fun bind(exercise: Exercise) {
            val layout = binding.root // SwipeRevealLayout

            // CRITICAL: reset the recycled row to CLOSED before rebinding, or a recycled view
            // shows a stale open state. Null the listener first so the reset's state callback
            // does not disturb the coordinator, then wire the fresh listener.
            layout.onStateChanged = null
            layout.close(animate = false)
            layout.setDeleteEnabled(DrillActions.canDelete(exercise))
            layout.onStateChanged = { newState ->
                if (newState != SwipeState.CLOSED) {
                    if (openRow != null && openRow !== layout) openRow?.close(animate = true)
                    openRow = layout
                } else if (openRow === layout) {
                    openRow = null
                }
            }

            binding.apply {
                tvExerciseName.text = exercise.name
                tvExerciseDescription.text = exercise.description
                tvDifficulty.text = exercise.difficulty
                tvDuration.text = exercise.duration
                tvCategory.text = exercise.category

                // Single consistent gold icon treatment for every drill (design system).
                // Icon glyph still varies per drill; ring/tile + tint do not.
                val iconRes = when (exercise.id) {
                    "forehand_drive", "forehand_andrii" -> R.drawable.ic_skill_forehand
                    "backhand_loop" -> R.drawable.ic_skill_backhand
                    "serve_practice" -> R.drawable.ic_skill_topspin
                    "footwork_drill" -> R.drawable.ic_skill_footwork
                    "multiball_rally" -> R.drawable.ic_alert_circle
                    "consistency_challenge" -> R.drawable.ic_check_circle_2
                    else -> R.drawable.ic_target
                }

                ivExerciseIcon.setImageResource(iconRes)
                ivExerciseIcon.setColorFilter(root.context.getColor(R.color.ttc_gold_accent))
                flIconContainer.setBackgroundResource(R.drawable.bg_icon_tile_gold)
                flIconContainer.backgroundTintList = null

                // Difficulty is muted meta text (no colored pill).
                tvDifficulty.background = null
                tvDifficulty.setTextColor(root.context.getColor(R.color.ttc_text_2))

                // Lock status display (using alpha) — applied to the foreground card only.
                swipeForeground.alpha = if (exercise.isLocked) 0.5f else 1.0f

                // Action buttons: run the action, then close the row.
                swipeClonePanel.setOnClickListener {
                    onCloneClick(exercise)
                    layout.close(animate = true)
                }
                swipeDeletePanel.setOnClickListener {
                    onDeleteClick(exercise)
                    layout.close(animate = true)
                }

                // Foreground tap: select only when closed (the open case is consumed inside
                // SwipeRevealLayout, which closes the row). Long-press → options menu when closed.
                swipeForeground.setOnClickListener {
                    if (!layout.isOpen) onExerciseClick(exercise)
                }
                swipeForeground.setOnLongClickListener {
                    if (!layout.isOpen) {
                        onExerciseLongClick?.invoke(exercise)
                        onExerciseLongClick != null
                    } else false
                }
            }
        }
```

Note: `root.alpha` moved to `swipeForeground.alpha` so a dimmed lock state does not also dim the reveal panels; the icon/text wiring is otherwise unchanged from the original.

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (`DrillsFragment` still passes the old 3-arg constructor — the two new params default, so it compiles; it is updated in Task 4.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ttcoachai/ExerciseAdapter.kt
git commit -m "feat(drills): wire clone/delete/select + single-open coordinator in adapter"
```

---

## Task 4: Swap DrillsFragment to reveal actions + close-on-scroll

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`

**Interfaces:**
- Consumes (from Task 3): `ExerciseAdapter(..., onCloneClick, onDeleteClick)`, `adapter.closeOpenRow()`.
- Produces: no downstream consumers — this is the final wiring task.

- [ ] **Step 1: Remove the old swipe machinery imports**

In `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`, delete these now-unused imports (lines 5, 6, 18, 21):

```kotlin
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
```

(Keep `androidx.recyclerview.widget.LinearLayoutManager` and `androidx.recyclerview.widget.RecyclerView` — `RecyclerView` is still used for the scroll listener.)

- [ ] **Step 2: Pass the Clone/Delete callbacks and add the scroll listener**

Replace `setupUI()` (lines 160–172) with:

```kotlin
    private fun setupUI() {
        binding.rvDrills.layoutManager = LinearLayoutManager(context)
        adapter = ExerciseAdapter(
            exercises = builtInExercises,
            onExerciseClick = { onExerciseSelected(it) },
            onExerciseLongClick = { showDrillOptions(it) },
            onCloneClick = { cloneDrill(it) },
            onDeleteClick = { deleteDrill(it) }
        )
        binding.rvDrills.adapter = adapter

        // Close any open swipe row when the list scrolls.
        binding.rvDrills.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0 || dx != 0) adapter.closeOpenRow()
            }
        })

        binding.fabAddDrill.setOnClickListener { showCalibrationIntroDialog() }
    }
```

- [ ] **Step 3: Delete `attachSwipeActions()`**

Remove the entire `attachSwipeActions()` function and its KDoc (lines 174–222 in the original — the block from `/**` above `private fun attachSwipeActions()` through the closing `}` of `ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvDrills)`).

- [ ] **Step 4: Drop the swipe-only `boundPrograms` field**

Remove the `boundPrograms` field declaration (lines 59–60):

```kotlin
    /** Last list bound to the adapter (ALL PROGRAMS only), used to map a swipe position to an Exercise. */
    private var boundPrograms: List<Exercise> = emptyList()
```

and its assignment inside `bindRecentAndPrograms` (line 258):

```kotlin
            boundPrograms = programs
```

(Leave `currentDrills` untouched — it is a separate `var` and not swipe-specific.)

- [ ] **Step 5: Build to verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL with no unused-import or unresolved-reference errors.

- [ ] **Step 6: Run the full app unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (SwipeSettleTest + existing DrillActionsTest and others remain green).

- [ ] **Step 7: Manual verification on device**

Install and exercise the Drills screen: `./gradlew :app:installDebug` then open the app. Confirm each row of the spec's truth table:
  - Swipe left on any drill → row stays open, gold **Clone** button revealed at the right edge.
  - Swipe right on a **custom** drill → row stays open, red **Delete** button revealed at the left edge.
  - Swipe right on a **built-in** preset → does not open (Delete disabled); Clone (left) still works.
  - Tap **Clone** → clone toast appears, list reloads, row closes.
  - Tap **Delete** → confirm dialog appears; confirming deletes + toast; row closes.
  - Open row A, then open row B → A closes automatically (one open at a time).
  - Tap an open row's foreground → it closes and does **not** open Training.
  - Scroll the list with a row open → it closes.
  - Closed-row tap → selects (opens Training); long-press → options menu.
  - Fast-scroll to recycle rows, then look for any row stuck open → none (reset-on-bind).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt
git commit -m "feat(drills): replace ItemTouchHelper with swipe-reveal actions + close-on-scroll"
```

---

## Self-Review

**Spec coverage** (each spec section → task):
- Swipe reveals persistent tappable button, action only on tap → Tasks 2 (layout/view) + 3 (button wiring).
- Swipe-left Clone (gold, right edge) / swipe-right Delete (red, left edge) → Task 2 layout panels + Task 1 sign convention.
- Directional gating per row (built-ins Clone-only) → `setDeleteEnabled(DrillActions.canDelete(...))` in Task 3; clamp/settle honor it (Task 1).
- Clone runs immediately + toast + reload; Delete shows confirm dialog → preserved `cloneDrill`/`deleteDrill` bodies, wired in Task 4.
- One row open at a time; close on second-open / foreground-tap / scroll / reload → coordinator + `onStateChanged` (Task 3), tap-close in `SwipeRevealLayout` (Task 2), scroll listener + `updateList` reset (Tasks 3–4).
- Closed-row interactions unchanged (select / long-press) → Task 3 foreground wiring.
- Interaction truth table → Task 4 Step 7 manual checklist.
- `SwipeRevealLayout` architecture + public API → Task 2.
- Pure testable `decideSwipeSettle` + clamp → Task 1.
- Row layout 3-layer, 12dp margin moved to root, opaque foreground → Task 2 Step 3.
- Adapter callbacks / reset-on-bind / coordinator → Task 3.
- Fragment: remove ItemTouchHelper, pass callbacks, close-on-scroll, drop boundPrograms → Task 4.
- Resources: `ic_content_copy` / `ic_trash` new, reuse strings/colors → Task 2 Steps 1–2, layout.
- Unit + manual testing → Tasks 1 + 4 Step 7.

**Placeholder scan:** none — every code step ships complete content.

**Type consistency:** `SwipeState`, `decideSwipeSettle`, `clampSwipeOffset`, `DEFAULT_FLING_VELOCITY` defined in Task 1 and used verbatim in Tasks 2–3. `setDeleteEnabled`/`close`/`open`/`isOpen`/`state`/`onStateChanged` defined in Task 2 and called verbatim in Task 3. `onCloneClick`/`onDeleteClick`/`closeOpenRow` defined in Task 3 and called verbatim in Task 4. Layout ids (`swipe_root`/`swipe_foreground`/`swipe_clone_panel`/`swipe_delete_panel`) defined in Task 2 layout and referenced by `findViewById` in `SwipeRevealLayout` and by ViewBinding (`swipeForeground`/`swipeClonePanel`/`swipeDeletePanel`) in Task 3.
