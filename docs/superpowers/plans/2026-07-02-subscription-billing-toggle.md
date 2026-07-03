# Subscription paywall — billing-toggle redesign (12b / 13a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the Android subscription paywall (`SubscribeActivity`) from a 3-card plan picker to the gold-dark billing-toggle layout (Monthly / Quarterly / Yearly segmented switch + price hero + checklist + single gold CTA), rendering as design 12b in dark mode and 13a in light mode from one layout.

**Architecture:** One `activity_subscribe.xml` referencing semantic `ttc_*` tokens (DayNight handles both themes — no second layout). Plan→copy mapping is extracted into a pure, unit-tested `SubscribePlanCopy` object; `SubscribeActivity` becomes thin view-wiring over `MaterialButtonToggleGroup`. Billing stays fully mocked.

**Tech Stack:** Kotlin, Android (`app/` module only — no `shared/` KMP), Material 3, ViewBinding (`ActivitySubscribeBinding`), JUnit4 for the pure-logic test.

## Global Constraints

- **App-only change.** All work is in `app/`. Do **not** touch `shared/`, pose/ball/trajectory pipeline code (frozen), or any other screen.
- **No hardcoded user-facing text.** Every string comes from a resource. **EN (`values/strings.xml`) + UK (`values-uk/strings.xml`) parity is required for every string this screen references** (reused strings that were previously EN-only must get their UK translation added here).
- **Reuse Slice-1 design system.** Use existing `ttc_*` color tokens, `TTC.*` styles, `ShapeAppearance.TTC.*`, and existing drawables wherever possible. Only add resources this screen genuinely needs.
- **Both themes from one layout.** `AppTheme` = `Theme.Material3.DayNight.NoActionBar`; `values-night/colors.xml` already exists. Reference semantic tokens, never raw hex, in the layout.
- **Billing stays mocked:** `settingsManager.setSubscriptionActive(true)` — no Google Play Billing.
- **Commit hygiene:** `git add` explicit paths only, never `git add -A`. Commit after each task.
- **Verification commands:**
  - Build: `./gradlew :app:assembleDebug`
  - Logic test: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.SubscribePlanCopyTest"`
- **Do NOT delete the old subscription strings** (`start_monthly_plan`, `plan_*_duration`, `plan_*_save`, `subscribe_auto_renew`, etc.). They were already EN-only; leaving them is out of scope for this restyle. Only add/translate the strings this screen uses.
- **No manifest change.** `SubscribeActivity` keeps `android:parentActivityName=".MainActivity"`; launched from `ProfileFragment`, `finish()` returns to the Profile tab.

---

## File Structure

**Create:**
- `app/src/main/res/color/ttc_paywall_segment_bg_tint.xml` — segment fill state list (gold-bright when checked).
- `app/src/main/res/color/ttc_paywall_segment_text.xml` — segment text state list (on-gold when checked).
- `app/src/main/res/drawable/bg_circle_gold.xml` — solid gold circle (hero mark).
- `app/src/main/res/drawable/bg_pill_success.xml` — savings pill background.
- `app/src/main/java/com/ttcoachai/SubscribePlanCopy.kt` — `Plan` enum + `SubscribePlanUiState` + pure `SubscribePlanCopy.stateFor(plan)`.
- `app/src/test/java/com/ttcoachai/SubscribePlanCopyTest.kt` — unit test for the mapping.

**Modify:**
- `app/src/main/res/values/colors.xml` — add `ttc_paywall_bg`, `ttc_success_container` (light).
- `app/src/main/res/values-night/colors.xml` — add `ttc_paywall_bg`, `ttc_success_container` (dark).
- `app/src/main/res/values/styles.xml` — add `TTC.Segment.Button.Paywall`.
- `app/src/main/res/values/strings.xml` — add new EN subscription strings.
- `app/src/main/res/values-uk/strings.xml` — add UK translations (new + reused strings this screen uses).
- `app/src/main/res/layout/activity_subscribe.xml` — full rewrite to the billing-toggle layout.
- `app/src/main/java/com/ttcoachai/SubscribeActivity.kt` — rewrite to toggle + `renderPlan()` wiring.

**Task order (dependencies):** Task 1 (resources) → Task 2 (strings) → Task 3 (logic, TDD) → Task 4 (layout + activity, together) → Task 5 (device verification).

> **Why layout + activity land in ONE task (Task 4):** ViewBinding couples them. The new layout renames every id (`card_monthly`→`toggle_billing`/`btn_monthly`, etc.), so the old `SubscribeActivity` referencing `binding.cardMonthly` would fail to compile against the new binding, and vice-versa. Neither can be independently build-green, so they must be rewritten together.

---

## Task 1: Design-system additions (tokens, state lists, drawables, segment style)

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-night/colors.xml`
- Create: `app/src/main/res/color/ttc_paywall_segment_bg_tint.xml`
- Create: `app/src/main/res/color/ttc_paywall_segment_text.xml`
- Create: `app/src/main/res/drawable/bg_circle_gold.xml`
- Create: `app/src/main/res/drawable/bg_pill_success.xml`
- Modify: `app/src/main/res/values/styles.xml`

**Interfaces:**
- Produces (consumed by Task 4 layout/style):
  - Colors: `@color/ttc_paywall_bg`, `@color/ttc_success_container`
  - Color state lists: `@color/ttc_paywall_segment_bg_tint`, `@color/ttc_paywall_segment_text`
  - Drawables: `@drawable/bg_circle_gold`, `@drawable/bg_pill_success`
  - Style: `@style/TTC.Segment.Button.Paywall`
- Consumes existing tokens (already defined, both themes): `ttc_gold_bright` (`#E9C46A`/`#E9C46A`), `ttc_on_gold` (`#2A2008`), `ttc_text_2`, `ttc_success`.

**Rationale for the two new colors:** the design's page background (`#F5F6F8` light / `#0E1115` dark) is intentionally one step darker/lighter than `ttc_surface`; no existing light token is exactly `#F5F6F8` (`ttc_canvas`=`#FFFFFF`, `ttc_sink`=`#EFF1F4`), so a dedicated `ttc_paywall_bg` preserves the card-separation the design relies on. `ttc_success_container` gives the savings pill its exact green fill (design `#E9F7EF` light / `#0E241B` dark); no existing token matches.

**Rationale for the paywall segment variant:** the reusable `TTC.Segment.Button` (used in Settings) resolves its checked state to `ttc_gold_container` fill + `ttc_gold_accent` text — a *subtle* treatment. 12b/13a want a **solid gold fill + on-gold text**. A `.Paywall` sub-style with paywall-specific state lists gives that without altering the Settings segment.

- [ ] **Step 1: Add light colors**

In `app/src/main/res/values/colors.xml`, add these two entries inside the `<resources>` element (place them next to the other `ttc_*` colors):

```xml
<color name="ttc_paywall_bg">#F5F6F8</color>
<color name="ttc_success_container">#E9F7EF</color>
```

- [ ] **Step 2: Add dark colors**

In `app/src/main/res/values-night/colors.xml`, add the matching dark values inside `<resources>`:

```xml
<color name="ttc_paywall_bg">#0E1115</color>
<color name="ttc_success_container">#0E241B</color>
```

- [ ] **Step 3: Create the segment fill state list**

Create `app/src/main/res/color/ttc_paywall_segment_bg_tint.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/ttc_gold_bright" android:state_checked="true" />
    <item android:color="@android:color/transparent" />
</selector>
```

- [ ] **Step 4: Create the segment text state list**

Create `app/src/main/res/color/ttc_paywall_segment_text.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/ttc_on_gold" android:state_checked="true" />
    <item android:color="@color/ttc_text_2" />
</selector>
```

- [ ] **Step 5: Create the gold hero circle**

Create `app/src/main/res/drawable/bg_circle_gold.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/ttc_gold_bright" />
</shape>
```

- [ ] **Step 6: Create the savings pill background**

Create `app/src/main/res/drawable/bg_pill_success.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/ttc_success_container" />
    <corners android:radius="999dp" />
</shape>
```

- [ ] **Step 7: Add the paywall segment style**

In `app/src/main/res/values/styles.xml`, add immediately after the existing `TTC.Segment.Button` style:

```xml
<!-- Bold gold-fill segment for the subscription paywall toggle (12b/13a). -->
<style name="TTC.Segment.Button.Paywall" parent="TTC.Segment.Button">
    <item name="backgroundTint">@color/ttc_paywall_segment_bg_tint</item>
    <item name="android:textColor">@color/ttc_paywall_segment_text</item>
</style>
```

- [ ] **Step 8: Verify the build compiles the new resources**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Resources are not yet referenced by any layout, so this only proves they are valid.)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml \
  app/src/main/res/color/ttc_paywall_segment_bg_tint.xml \
  app/src/main/res/color/ttc_paywall_segment_text.xml \
  app/src/main/res/drawable/bg_circle_gold.xml \
  app/src/main/res/drawable/bg_pill_success.xml \
  app/src/main/res/values/styles.xml
git commit -m "feat(subscribe): add gold-dark tokens/styles for billing-toggle paywall"
```

---

## Task 2: Strings (EN + UK)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`

**Interfaces:**
- Produces string keys consumed by Task 3 (`SubscribePlanCopy`) and Task 4 (layout + activity):
  - Reused (already exist in EN, **UK to be added in this task**): `subscribe_title`, `subscribe_subtitle`, `plan_monthly`, `plan_quarterly`, `plan_yearly`, `plan_monthly_price`, `plan_quarterly_price`, `plan_yearly_price`, `badge_popular`, `badge_best_value`, `whats_included`, `feature_unlimited_coaching`, `feature_training_plans`.
  - New: `subscribe_restore`, `subscribe_start_premium`, `subscribe_per_month_format`, `subscribe_savings_format`, `subscribe_billed_monthly`, `subscribe_billed_quarterly`, `subscribe_billed_yearly`, `subscribe_period_month`, `subscribe_period_quarter`, `subscribe_period_year`, `subscribe_per_month_quarterly_value`, `subscribe_per_month_yearly_value`, `subscribe_feature_analysis`, `subscribe_feature_analytics`, `subscribe_feature_reports`, `subscribe_terms`, `subscribe_privacy`, `subscribe_restore_none`, `subscribe_activated`.

- [ ] **Step 1: Add the new EN strings**

In `app/src/main/res/values/strings.xml`, add inside `<resources>` (group them under a comment for clarity):

```xml
<!-- Subscription paywall — billing-toggle redesign (12b/13a) -->
<string name="subscribe_restore">Restore</string>
<string name="subscribe_start_premium">Start Premium</string>
<string name="subscribe_per_month_format">Just %1$s / month</string>
<string name="subscribe_savings_format">Save %1$d%% vs monthly</string>
<string name="subscribe_billed_monthly">$9.99 billed monthly · cancel anytime</string>
<string name="subscribe_billed_quarterly">$24.99 billed every 3 months · cancel anytime</string>
<string name="subscribe_billed_yearly">$79.99 billed yearly · cancel anytime</string>
<string name="subscribe_period_month">/mo</string>
<string name="subscribe_period_quarter">/3 mo</string>
<string name="subscribe_period_year">/yr</string>
<string name="subscribe_per_month_quarterly_value">$8.33</string>
<string name="subscribe_per_month_yearly_value">$6.67</string>
<string name="subscribe_feature_analysis">Advanced pose &amp; stroke analysis</string>
<string name="subscribe_feature_analytics">Advanced analytics &amp; error breakdown</string>
<string name="subscribe_feature_reports">Detailed post-session reports</string>
<string name="subscribe_terms">Terms</string>
<string name="subscribe_privacy">Privacy</string>
<string name="subscribe_restore_none">No purchases to restore</string>
<string name="subscribe_activated">Subscription activated!</string>
```

- [ ] **Step 2: Add the UK translations (new + reused)**

In `app/src/main/res/values-uk/strings.xml`, add inside `<resources>`:

```xml
<!-- Subscription paywall — billing-toggle redesign (12b/13a) -->
<!-- Reused strings (previously EN-only) — added here for EN/UK parity -->
<string name="subscribe_title">Відкрийте Premium</string>
<string name="subscribe_subtitle">Необмежений доступ до ШІ-тренера та розширених інструментів тренування</string>
<string name="plan_monthly">Щомісячно</string>
<string name="plan_quarterly">Щокварталу</string>
<string name="plan_yearly">Щороку</string>
<string name="plan_monthly_price">$9.99</string>
<string name="plan_quarterly_price">$24.99</string>
<string name="plan_yearly_price">$79.99</string>
<string name="badge_popular">Популярний</string>
<string name="badge_best_value">Найвигідніше</string>
<string name="whats_included">Що входить</string>
<string name="feature_unlimited_coaching">Необмежені сесії з ШІ-тренером</string>
<string name="feature_training_plans">Персоналізовані плани тренувань</string>
<!-- New strings -->
<string name="subscribe_restore">Відновити</string>
<string name="subscribe_start_premium">Почати Premium</string>
<string name="subscribe_per_month_format">Лише %1$s / місяць</string>
<string name="subscribe_savings_format">Заощаджуйте %1$d%% порівняно з місячним</string>
<string name="subscribe_billed_monthly">$9.99 щомісяця · скасування будь-коли</string>
<string name="subscribe_billed_quarterly">$24.99 кожні 3 місяці · скасування будь-коли</string>
<string name="subscribe_billed_yearly">$79.99 щороку · скасування будь-коли</string>
<string name="subscribe_period_month">/міс</string>
<string name="subscribe_period_quarter">/3 міс</string>
<string name="subscribe_period_year">/рік</string>
<string name="subscribe_per_month_quarterly_value">$8.33</string>
<string name="subscribe_per_month_yearly_value">$6.67</string>
<string name="subscribe_feature_analysis">Розширений аналіз пози та удару</string>
<string name="subscribe_feature_analytics">Розширена аналітика та розбір помилок</string>
<string name="subscribe_feature_reports">Детальні звіти після сесії</string>
<string name="subscribe_terms">Умови</string>
<string name="subscribe_privacy">Конфіденційність</string>
<string name="subscribe_restore_none">Немає покупок для відновлення</string>
<string name="subscribe_activated">Підписку активовано!</string>
```

> Note: if any of the "reused" keys already have a `values-uk` entry, do **not** duplicate it — skip that line (a duplicate key fails the build). Per the current audit all 13 are EN-only, so all are expected to be new in `values-uk`.

- [ ] **Step 3: Verify the build compiles the strings**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (If it fails with "duplicate resource", remove the offending already-present UK key per the note above.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml
git commit -m "feat(subscribe): add billing-toggle strings (EN + UK parity)"
```

---

## Task 3: Plan→copy mapping (pure logic, TDD)

**Files:**
- Create: `app/src/main/java/com/ttcoachai/SubscribePlanCopy.kt`
- Test: `app/src/test/java/com/ttcoachai/SubscribePlanCopyTest.kt`

**Interfaces:**
- Consumes string ids from Task 2.
- Produces (consumed by Task 4 `SubscribeActivity`):
  - `enum class Plan { MONTHLY, QUARTERLY, YEARLY }` (top-level, public — must be visible from tests and the activity)
  - `data class SubscribePlanUiState(val priceRes: Int, val periodSuffixRes: Int, val badgeRes: Int?, val perMonthPriceRes: Int?, val savingsPercent: Int?, val billedCaptionRes: Int)`
  - `object SubscribePlanCopy { fun stateFor(plan: Plan): SubscribePlanUiState }`

The mapping encodes the spec's behavior table. `null` for `badgeRes` / `perMonthPriceRes` / `savingsPercent` means "hide that view" (Monthly).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ttcoachai/SubscribePlanCopyTest.kt`:

```kotlin
package com.ttcoachai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscribePlanCopyTest {

    @Test
    fun monthly_hasNoBadgePerMonthOrSavings() {
        val s = SubscribePlanCopy.stateFor(Plan.MONTHLY)
        assertNull("Monthly shows no badge", s.badgeRes)
        assertNull("Monthly shows no per-month line", s.perMonthPriceRes)
        assertNull("Monthly shows no savings pill", s.savingsPercent)
        assertEquals(R.string.plan_monthly_price, s.priceRes)
        assertEquals(R.string.subscribe_period_month, s.periodSuffixRes)
        assertEquals(R.string.subscribe_billed_monthly, s.billedCaptionRes)
    }

    @Test
    fun quarterly_isPopularAndSaves17() {
        val s = SubscribePlanCopy.stateFor(Plan.QUARTERLY)
        assertEquals(R.string.badge_popular, s.badgeRes)
        assertEquals(Integer.valueOf(17), s.savingsPercent)
        assertEquals(R.string.plan_quarterly_price, s.priceRes)
        assertEquals(R.string.subscribe_period_quarter, s.periodSuffixRes)
        assertEquals(R.string.subscribe_per_month_quarterly_value, s.perMonthPriceRes)
        assertEquals(R.string.subscribe_billed_quarterly, s.billedCaptionRes)
    }

    @Test
    fun yearly_isBestValueAndSaves33() {
        val s = SubscribePlanCopy.stateFor(Plan.YEARLY)
        assertEquals(R.string.badge_best_value, s.badgeRes)
        assertEquals(Integer.valueOf(33), s.savingsPercent)
        assertEquals(R.string.plan_yearly_price, s.priceRes)
        assertEquals(R.string.subscribe_period_year, s.periodSuffixRes)
        assertEquals(R.string.subscribe_per_month_yearly_value, s.perMonthPriceRes)
        assertEquals(R.string.subscribe_billed_yearly, s.billedCaptionRes)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.SubscribePlanCopyTest"`
Expected: **compile failure** — `Unresolved reference: SubscribePlanCopy` / `Plan`. (This is the red state.)

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/ttcoachai/SubscribePlanCopy.kt`:

```kotlin
package com.ttcoachai

/** Billing periods offered by the subscription paywall. */
enum class Plan { MONTHLY, QUARTERLY, YEARLY }

/**
 * View state for one billing period. A `null` in [badgeRes], [perMonthPriceRes], or
 * [savingsPercent] means the corresponding view is hidden (GONE) for that period.
 */
data class SubscribePlanUiState(
    val priceRes: Int,
    val periodSuffixRes: Int,
    val badgeRes: Int?,
    val perMonthPriceRes: Int?,
    val savingsPercent: Int?,
    val billedCaptionRes: Int,
)

/** Pure mapping from a [Plan] to its price-hero + CTA copy (the spec's behavior table). */
object SubscribePlanCopy {
    fun stateFor(plan: Plan): SubscribePlanUiState = when (plan) {
        Plan.MONTHLY -> SubscribePlanUiState(
            priceRes = R.string.plan_monthly_price,
            periodSuffixRes = R.string.subscribe_period_month,
            badgeRes = null,
            perMonthPriceRes = null,
            savingsPercent = null,
            billedCaptionRes = R.string.subscribe_billed_monthly,
        )
        Plan.QUARTERLY -> SubscribePlanUiState(
            priceRes = R.string.plan_quarterly_price,
            periodSuffixRes = R.string.subscribe_period_quarter,
            badgeRes = R.string.badge_popular,
            perMonthPriceRes = R.string.subscribe_per_month_quarterly_value,
            savingsPercent = 17,
            billedCaptionRes = R.string.subscribe_billed_quarterly,
        )
        Plan.YEARLY -> SubscribePlanUiState(
            priceRes = R.string.plan_yearly_price,
            periodSuffixRes = R.string.subscribe_period_year,
            badgeRes = R.string.badge_best_value,
            perMonthPriceRes = R.string.subscribe_per_month_yearly_value,
            savingsPercent = 33,
            billedCaptionRes = R.string.subscribe_billed_yearly,
        )
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.SubscribePlanCopyTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ttcoachai/SubscribePlanCopy.kt \
  app/src/test/java/com/ttcoachai/SubscribePlanCopyTest.kt
git commit -m "feat(subscribe): add pure Plan->copy mapping with unit tests"
```

---

## Task 4: Layout + activity rewrite (billing-toggle screen)

**Files:**
- Modify (full rewrite): `app/src/main/res/layout/activity_subscribe.xml`
- Modify (full rewrite): `app/src/main/java/com/ttcoachai/SubscribeActivity.kt`

**Interfaces:**
- Consumes: Task 1 resources, Task 2 strings, Task 3 `Plan` / `SubscribePlanUiState` / `SubscribePlanCopy`.
- Consumes existing (verified present): styles `TTC.Card`, `TTC.Card.GoldTint`, `TTC.Button.Primary`, text appearances `TextAppearance.TTC.Title.Screen` / `.Title.Card` / `.Body` / `.Body.Secondary` / `.Stat.Hero` / `.Eyebrow.Gold`; drawables `ic_chevron_left`, `ic_chevron_right`, `ic_crown`, `ic_check_circle_2`, `bg_pill_track`, `bg_pill_gold_container`; shape `ShapeAppearance.TTC.Tile`; tokens `ttc_surface_elevated`, `ttc_text_1`, `ttc_gold_accent`, `ttc_success`, `ttc_on_gold`.
- View ids the activity binds: `btn_back`, `btn_restore`, `toggle_billing`, `btn_monthly`, `btn_quarterly`, `btn_yearly`, `text_badge`, `text_price`, `text_period`, `text_per_month`, `text_savings`, `btn_start`, `text_billed_caption`, `btn_terms`, `btn_privacy`. (ViewBinding camel-cases these: `binding.btnBack`, `binding.toggleBilling`, `binding.textBadge`, etc.)

- [ ] **Step 1: Rewrite the layout**

Replace the entire contents of `app/src/main/res/layout/activity_subscribe.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/ttc_paywall_bg"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingHorizontal="20dp"
        android:paddingTop="16dp"
        android:paddingBottom="24dp">

        <!-- 1. Top bar -->
        <RelativeLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp">

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/btn_back"
                style="@style/TTC.Card"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:layout_alignParentStart="true"
                android:clickable="true"
                android:focusable="true"
                app:cardBackgroundColor="@color/ttc_surface_elevated"
                app:contentPadding="0dp"
                app:shapeAppearance="@style/ShapeAppearance.TTC.Tile">

                <ImageView
                    android:layout_width="20dp"
                    android:layout_height="20dp"
                    android:layout_gravity="center"
                    android:importantForAccessibility="no"
                    android:src="@drawable/ic_chevron_left"
                    app:tint="@color/ttc_text_1" />
            </com.google.android.material.card.MaterialCardView>

            <TextView
                android:id="@+id/btn_restore"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentEnd="true"
                android:layout_centerVertical="true"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:clickable="true"
                android:focusable="true"
                android:padding="8dp"
                android:text="@string/subscribe_restore"
                android:textAppearance="@style/TextAppearance.TTC.Body"
                android:textColor="@color/ttc_gold_accent" />
        </RelativeLayout>

        <!-- 2. Hero -->
        <FrameLayout
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:layout_gravity="center_horizontal"
            android:layout_marginBottom="16dp"
            android:background="@drawable/bg_circle_gold">

            <ImageView
                android:layout_width="28dp"
                android:layout_height="28dp"
                android:layout_gravity="center"
                android:importantForAccessibility="no"
                android:src="@drawable/ic_crown"
                app:tint="@color/ttc_on_gold" />
        </FrameLayout>

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="6dp"
            android:gravity="center"
            android:text="@string/subscribe_title"
            android:textAppearance="@style/TextAppearance.TTC.Title.Screen" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp"
            android:gravity="center"
            android:text="@string/subscribe_subtitle"
            android:textAppearance="@style/TextAppearance.TTC.Body.Secondary" />

        <!-- 3. Billing toggle -->
        <com.google.android.material.button.MaterialButtonToggleGroup
            android:id="@+id/toggle_billing"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="20dp"
            android:background="@drawable/bg_pill_track"
            android:padding="4dp"
            app:checkedButton="@id/btn_yearly"
            app:selectionRequired="true"
            app:singleSelection="true">

            <Button
                android:id="@+id/btn_monthly"
                style="@style/TTC.Segment.Button.Paywall"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/plan_monthly" />

            <Button
                android:id="@+id/btn_quarterly"
                style="@style/TTC.Segment.Button.Paywall"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/plan_quarterly" />

            <Button
                android:id="@+id/btn_yearly"
                style="@style/TTC.Segment.Button.Paywall"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/plan_yearly" />
        </com.google.android.material.button.MaterialButtonToggleGroup>

        <!-- 4. Price hero -->
        <com.google.android.material.card.MaterialCardView
            style="@style/TTC.Card.GoldTint"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_horizontal"
                android:orientation="vertical"
                android:padding="20dp">

                <TextView
                    android:id="@+id/text_badge"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="12dp"
                    android:background="@drawable/bg_pill_gold_container"
                    android:paddingHorizontal="12dp"
                    android:paddingVertical="4dp"
                    android:text="@string/badge_best_value"
                    android:textAppearance="@style/TextAppearance.TTC.Eyebrow.Gold" />

                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:gravity="bottom"
                    android:orientation="horizontal">

                    <TextView
                        android:id="@+id/text_price"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/plan_yearly_price"
                        android:textAppearance="@style/TextAppearance.TTC.Stat.Hero"
                        android:textColor="@color/ttc_text_1" />

                    <TextView
                        android:id="@+id/text_period"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="4dp"
                        android:layout_marginBottom="4dp"
                        android:text="@string/subscribe_period_year"
                        android:textAppearance="@style/TextAppearance.TTC.Body.Secondary" />
                </LinearLayout>

                <TextView
                    android:id="@+id/text_per_month"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textAppearance="@style/TextAppearance.TTC.Body.Secondary"
                    tools:text="Just $6.67 / month" />

                <TextView
                    android:id="@+id/text_savings"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:background="@drawable/bg_pill_success"
                    android:paddingHorizontal="12dp"
                    android:paddingVertical="4dp"
                    android:textAppearance="@style/TextAppearance.TTC.Body"
                    android:textColor="@color/ttc_success"
                    tools:text="Save 33% vs monthly" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- 5. What's included -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            android:text="@string/whats_included"
            android:textAppearance="@style/TextAppearance.TTC.Title.Card" />

        <com.google.android.material.card.MaterialCardView
            style="@style/TTC.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="20dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="14dp"
                    android:gravity="center_vertical"
                    android:orientation="horizontal">
                    <ImageView
                        android:layout_width="22dp"
                        android:layout_height="22dp"
                        android:layout_marginEnd="12dp"
                        android:importantForAccessibility="no"
                        android:src="@drawable/ic_check_circle_2"
                        app:tint="@color/ttc_gold_accent" />
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/feature_unlimited_coaching"
                        android:textAppearance="@style/TextAppearance.TTC.Body" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="14dp"
                    android:gravity="center_vertical"
                    android:orientation="horizontal">
                    <ImageView
                        android:layout_width="22dp"
                        android:layout_height="22dp"
                        android:layout_marginEnd="12dp"
                        android:importantForAccessibility="no"
                        android:src="@drawable/ic_check_circle_2"
                        app:tint="@color/ttc_gold_accent" />
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/subscribe_feature_analysis"
                        android:textAppearance="@style/TextAppearance.TTC.Body" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="14dp"
                    android:gravity="center_vertical"
                    android:orientation="horizontal">
                    <ImageView
                        android:layout_width="22dp"
                        android:layout_height="22dp"
                        android:layout_marginEnd="12dp"
                        android:importantForAccessibility="no"
                        android:src="@drawable/ic_check_circle_2"
                        app:tint="@color/ttc_gold_accent" />
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/feature_training_plans"
                        android:textAppearance="@style/TextAppearance.TTC.Body" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="14dp"
                    android:gravity="center_vertical"
                    android:orientation="horizontal">
                    <ImageView
                        android:layout_width="22dp"
                        android:layout_height="22dp"
                        android:layout_marginEnd="12dp"
                        android:importantForAccessibility="no"
                        android:src="@drawable/ic_check_circle_2"
                        app:tint="@color/ttc_gold_accent" />
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/subscribe_feature_analytics"
                        android:textAppearance="@style/TextAppearance.TTC.Body" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:gravity="center_vertical"
                    android:orientation="horizontal">
                    <ImageView
                        android:layout_width="22dp"
                        android:layout_height="22dp"
                        android:layout_marginEnd="12dp"
                        android:importantForAccessibility="no"
                        android:src="@drawable/ic_check_circle_2"
                        app:tint="@color/ttc_gold_accent" />
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/subscribe_feature_reports"
                        android:textAppearance="@style/TextAppearance.TTC.Body" />
                </LinearLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- 6. CTA -->
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_start"
            style="@style/TTC.Button.Primary"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:layout_marginBottom="10dp"
            android:text="@string/subscribe_start_premium"
            app:icon="@drawable/ic_chevron_right"
            app:iconGravity="textEnd" />

        <TextView
            android:id="@+id/text_billed_caption"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="20dp"
            android:gravity="center"
            android:textAppearance="@style/TextAppearance.TTC.Body.Secondary"
            tools:text="$79.99 billed yearly · cancel anytime" />

        <!-- 7. Footer -->
        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center_horizontal"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/btn_terms"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:clickable="true"
                android:focusable="true"
                android:padding="6dp"
                android:text="@string/subscribe_terms"
                android:textAppearance="@style/TextAppearance.TTC.Body.Secondary" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="·"
                android:textAppearance="@style/TextAppearance.TTC.Body.Secondary" />

            <TextView
                android:id="@+id/btn_privacy"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:clickable="true"
                android:focusable="true"
                android:padding="6dp"
                android:text="@string/subscribe_privacy"
                android:textAppearance="@style/TextAppearance.TTC.Body.Secondary" />
        </LinearLayout>

    </LinearLayout>
</ScrollView>
```

- [ ] **Step 2: Rewrite the activity**

Replace the entire contents of `app/src/main/java/com/ttcoachai/SubscribeActivity.kt` with:

```kotlin
package com.ttcoachai

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ttcoachai.databinding.ActivitySubscribeBinding
import com.ttcoachai.managers.SettingsManager

class SubscribeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscribeBinding
    private lateinit var settingsManager: SettingsManager

    private var selectedPlan = Plan.YEARLY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscribeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupToggle()
        setupButtons()
        renderPlan()
    }

    private fun setupToggle() {
        binding.toggleBilling.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedPlan = when (checkedId) {
                R.id.btn_monthly -> Plan.MONTHLY
                R.id.btn_quarterly -> Plan.QUARTERLY
                else -> Plan.YEARLY
            }
            renderPlan()
        }
    }

    private fun renderPlan() {
        val state = SubscribePlanCopy.stateFor(selectedPlan)

        binding.textPrice.text = getString(state.priceRes)
        binding.textPeriod.text = getString(state.periodSuffixRes)

        binding.textBadge.apply {
            if (state.badgeRes == null) {
                visibility = View.GONE
            } else {
                text = getString(state.badgeRes)
                visibility = View.VISIBLE
            }
        }

        binding.textPerMonth.apply {
            if (state.perMonthPriceRes == null) {
                visibility = View.GONE
            } else {
                text = getString(R.string.subscribe_per_month_format, getString(state.perMonthPriceRes))
                visibility = View.VISIBLE
            }
        }

        binding.textSavings.apply {
            val percent = state.savingsPercent
            if (percent == null) {
                visibility = View.GONE
            } else {
                text = getString(R.string.subscribe_savings_format, percent)
                visibility = View.VISIBLE
            }
        }

        binding.textBilledCaption.text = getString(state.billedCaptionRes)
    }

    private fun setupButtons() {
        binding.btnStart.setOnClickListener {
            // Mock purchase — no real billing integration.
            settingsManager.setSubscriptionActive(true)
            Toast.makeText(this, getString(R.string.subscribe_activated), Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnRestore.setOnClickListener {
            Toast.makeText(this, getString(R.string.subscribe_restore_none), Toast.LENGTH_SHORT).show()
        }

        // Terms / Privacy — stubbed no-ops, ready to wire to URLs later.
        binding.btnTerms.setOnClickListener { /* TODO: open Terms URL */ }
        binding.btnPrivacy.setOnClickListener { /* TODO: open Privacy URL */ }
    }
}
```

> Note: the `/* TODO: open ... URL */` comments are intentional stubs per the spec ("placeholder no-ops for now"), not plan placeholders — the click handlers are fully wired and compile.

- [ ] **Step 3: Verify the build compiles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If it fails with an unresolved `binding.*` reference, confirm the layout id matches the camelCased binding name (e.g. `text_per_month` → `binding.textPerMonth`).

- [ ] **Step 4: Run the full app unit-test suite (regression guard)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` (includes `SubscribePlanCopyTest`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_subscribe.xml \
  app/src/main/java/com/ttcoachai/SubscribeActivity.kt
git commit -m "feat(subscribe): billing-toggle paywall layout + toggle wiring (12b/13a)"
```

---

## Task 5: Device verification — both themes (12b dark / 13a light)

**Files:** none (manual verification per the spec's Testing section).

This screen is presentation + view logic; correctness beyond the unit-tested mapping is verified on-device. Use the `run-on-phone` and `phone-screenshot` skills. Save screenshots to `tmp/screenshots/` (project root), not `/tmp/`.

- [ ] **Step 1: Build, install, and launch on the connected device**

Use the `run-on-phone` skill (builds `:app:assembleDebug`, reinstalls, launches). Then navigate: Profile tab → the subscription/upgrade card → Subscribe screen.

- [ ] **Step 2: Verify dark theme (12b)**

Ensure the app is in dark mode (Profile theme toggle → Dark). On the Subscribe screen confirm:
- Page background is near-black (`#0E1115`), cards are the gold-tinted / surface tokens, gold accents render.
- Toggle shows **Yearly** selected by default with a **solid gold fill + dark on-gold text**; Monthly/Quarterly are muted text on the pill track.
- Price hero shows `$79.99`, `/yr`, "Best Value" badge, "Just $6.67 / month", green "Save 33% vs monthly" pill.
- CTA reads "Start Premium" with a trailing chevron; caption reads "$79.99 billed yearly · cancel anytime".

Capture: `tmp/screenshots/subscribe-12b-dark.png` (via `phone-screenshot` skill).

- [ ] **Step 3: Verify light theme (13a)**

Switch to Light (Profile theme toggle). Re-open the Subscribe screen and confirm the same structure renders with the light palette (page `#F5F6F8`, white surfaces, darker gold accent), cards still visually separated from the background.

Capture: `tmp/screenshots/subscribe-13a-light.png`.

- [ ] **Step 4: Verify toggle behavior across all three periods**

Tap **Monthly** → price `$9.99`, `/mo`, **no** badge, **no** per-month line, **no** savings pill, caption "$9.99 billed monthly · cancel anytime".
Tap **Quarterly** → price `$24.99`, `/3 mo`, "Popular" badge, "Just $8.33 / month", green "Save 17% vs monthly", caption "$24.99 billed every 3 months · cancel anytime".
Tap **Yearly** → back to the yearly values above.

Capture at least one non-default period: `tmp/screenshots/subscribe-monthly.png`.

- [ ] **Step 5: Verify navigation + actions**

- Back arrow (top-left tile) → returns to the Profile tab.
- "Restore" → toast "No purchases to restore".
- "Start Premium" → toast "Subscription activated!" and the screen closes (returns to Profile). Re-open to confirm subscription state persisted via `SettingsManager`.

- [ ] **Step 6: Report results**

Summarize pass/fail per step with the two theme screenshots. If anything mismatches the spec table or design, note it before considering the task done. (No commit — verification only.)

---

## Self-Review (completed while writing this plan)

**Spec coverage:**
- Token mapping / new `ttc_paywall_bg` + `ttc_success_container` → Task 1 (with rationale for why new tokens over `ttc_canvas`/`ttc_sink`). ✓
- Layout sections 1–7 (top bar, hero, toggle, price hero, checklist, CTA, footer) → Task 4 layout. ✓
- "Remove old 3 cards / Maybe Later / auto-renew footer" → new layout omits them entirely. ✓
- Behavior table (badge/price/per-month/savings/caption per period; badges GONE for Monthly; default Yearly) → Task 3 mapping (unit-tested) + Task 4 `renderPlan()`. ✓
- Start Premium mock / Back / Restore toast / Terms+Privacy stubs → Task 4 `setupButtons()`. ✓
- Strings reuse-vs-add + EN/UK parity → Task 2. ✓
- Testing (build both, visual both themes, toggle behavior, back nav) → Task 5. ✓
- Out-of-scope items (real billing, Profile cards, screen 12a, frozen pipeline) → untouched; Global Constraints. ✓

**Placeholder scan:** No "TBD/handle edge cases/similar to Task N". The only `TODO` comments are the spec-mandated Terms/Privacy no-op stubs (handlers are wired and compile).

**Type/name consistency:** `Plan` enum, `SubscribePlanUiState` fields (`priceRes`, `periodSuffixRes`, `badgeRes`, `perMonthPriceRes`, `savingsPercent`, `billedCaptionRes`), and `SubscribePlanCopy.stateFor` are used identically in Task 3 (definition + test) and Task 4 (`renderPlan`). Layout ids match the camelCased `binding.*` accessors used in the activity (`text_per_month`→`textPerMonth`, `toggle_billing`→`toggleBilling`, `btn_start`→`btnStart`, etc.).

**Known fidelity note (not a blocker):** feature checks use `ic_check_circle_2` tinted `ttc_gold_accent` (a gold check) rather than a separate filled "chip" background — a minor, low-risk simplification of "gold check chip"; flag during Task 5 review if the design demands the filled chip and add a small circle background then.
