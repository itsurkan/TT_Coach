# Calibration Intro Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show an explanatory `AlertDialog` (camera placement, rep count, personal-baseline framing) when the user taps the "add drill" FAB, before `CalibrationActivity` (the camera) opens. The "Edit" action on existing custom drills is unaffected.

**Architecture:** One new private method `showCalibrationIntroDialog()` in `DrillsFragment.kt` that builds an `AlertDialog` and calls the existing `launchCustomDrillCalibration()` from its positive button. The FAB's click listener is rewired to call the new method instead of `launchCustomDrillCalibration()` directly. Four new string resources (title, body, proceed, cancel) added to both `values/strings.xml` (English) and `values-uk/strings.xml` (Ukrainian).

**Tech Stack:** Kotlin, AndroidX `AlertDialog.Builder` (already used elsewhere in this file), Android string resources.

## Global Constraints

- Ukrainian strings go in `app/src/main/res/values-uk/strings.xml`, English in `app/src/main/res/values/strings.xml` — both must be added together, same string names.
- Only the FAB ("add") flow gets the dialog. `drill_action_edit` in `showDrillOptions()` must keep calling `launchCustomDrillCalibration()` directly — do not touch that call site.
- No persistence/"don't show again" — the dialog must show every time the FAB is tapped.
- New method uses `AlertDialog.Builder(requireContext())`, matching the style of `showDrillOptions()` and `promptForCustomDrillName()` already in `DrillsFragment.kt`.
- No test harness exists for `DrillsFragment` (no Robolectric fragment tests in this codebase) — verification is manual, via building and running the app, not a new unit test.

---

### Task 1: Add calibration-intro string resources

**Files:**
- Modify: `app/src/main/res/values-uk/strings.xml` (near line 609, after `drills_add_own_exercise`)
- Modify: `app/src/main/res/values/strings.xml` (find the matching `drills_add_own_exercise` entry there, add alongside it)

**Interfaces:**
- Produces: four new string resource names consumed by Task 2 —
  `R.string.calibration_intro_title`, `R.string.calibration_intro_body`,
  `R.string.calibration_intro_proceed`, `R.string.calibration_intro_cancel`.

- [ ] **Step 1: Add Ukrainian strings**

In `app/src/main/res/values-uk/strings.xml`, insert immediately after the line:
```xml
<string name="drills_add_own_exercise">+ Додати свою вправу · калібрування</string>
```
add:
```xml
<string name="calibration_intro_title">Калібрування вправи</string>
<string name="calibration_intro_body">1. Розташуйте камеру збоку, на рівні пояса, на відстані 2–3 м.\n2. Виконайте 8–10 ударів у своєму звичному темпі.\n3. Система запам\'ятає ваші кути як еталон — це ваша особиста техніка, а не «правильна» форма.</string>
<string name="calibration_intro_proceed">Розпочати</string>
<string name="calibration_intro_cancel">Скасувати</string>
```

- [ ] **Step 2: Find the English counterpart location**

Run: `grep -n "drills_add_own_exercise" app/src/main/res/values/strings.xml`
Expected: one line number output, e.g. `780:    <string name="drills_add_own_exercise">+ Add your own exercise · calibration</string>`

- [ ] **Step 3: Add English strings**

In `app/src/main/res/values/strings.xml`, immediately after the `drills_add_own_exercise` line found in Step 2, add:
```xml
<string name="calibration_intro_title">Exercise calibration</string>
<string name="calibration_intro_body">1. Place the camera to the side, at waist height, 2–3 m away.\n2. Perform 8–10 strokes at your natural pace.\n3. The app will remember your angles as the baseline — this is your own technique, not a "correct" form.</string>
<string name="calibration_intro_proceed">Start</string>
<string name="calibration_intro_cancel">Cancel</string>
```

- [ ] **Step 4: Verify both files are well-formed XML**

Run: `xmllint --noout app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml`
Expected: no output (no errors). If `xmllint` isn't available, instead run `./gradlew :app:processDebugResources -q` and expect it to succeed with no resource-parsing errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml
git commit -m "feat(drills): add calibration intro dialog strings (UK+EN)"
```

---

### Task 2: Wire the calibration intro dialog into the add-drill FAB

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt:169` (FAB click listener in `setupUI()`)
- Modify: `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt` (add new method near `launchCustomDrillCalibration()`, currently around line 356)

**Interfaces:**
- Consumes: `R.string.calibration_intro_title`, `R.string.calibration_intro_body`, `R.string.calibration_intro_proceed`, `R.string.calibration_intro_cancel` (from Task 1); existing `private fun launchCustomDrillCalibration()` (unchanged, no parameters, no return value).
- Produces: `private fun showCalibrationIntroDialog()` — no parameters, no return value, callable from `setupUI()`.

- [ ] **Step 1: Rewire the FAB click listener**

In `setupUI()`, change:
```kotlin
        binding.fabAddDrill.setOnClickListener { launchCustomDrillCalibration() }
```
to:
```kotlin
        binding.fabAddDrill.setOnClickListener { showCalibrationIntroDialog() }
```

Leave the `showDrillOptions()` call site (`add(Action(getString(R.string.drill_action_edit)) { launchCustomDrillCalibration() })`) untouched — edit must keep launching calibration directly.

- [ ] **Step 2: Add the new method**

Immediately above `private fun launchCustomDrillCalibration() {` add:
```kotlin
    private fun showCalibrationIntroDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.calibration_intro_title)
            .setMessage(R.string.calibration_intro_body)
            .setPositiveButton(R.string.calibration_intro_proceed) { _, _ -> launchCustomDrillCalibration() }
            .setNegativeButton(R.string.calibration_intro_cancel, null)
            .show()
    }

```

- [ ] **Step 3: Compile the app module**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: build succeeds with no errors (only the pre-existing Gradle native-access warnings, if any).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt
git commit -m "feat(drills): show calibration intro dialog before opening camera on add"
```

---

### Task 3: Manual verification on device

**Files:** none (verification only, no code changes)

**Interfaces:**
- Consumes: the built debug APK from Task 2.

- [ ] **Step 1: Install the debug build**

Run: `./gradlew :app:installDebug -q`
Expected: `Installed on 1 device.` (or equivalent success). If `INSTALL_PARSE_FAILED_NO_CERTIFICATES` or a similar stale-install error occurs, run `adb uninstall com.ttcoachai` first, then retry install.

- [ ] **Step 2: Launch the app and open the Drills tab**

Run: `adb shell am start -n com.ttcoachai/.MainActivity`
Then navigate to the Drills tab in the running app (manual tap, or via `adb shell input tap <x> <y>` once the tab's on-screen position is confirmed via a screenshot).

- [ ] **Step 3: Tap the gold FAB and screenshot the dialog**

Tap the FAB (bottom-end gold circular button). Then run:
```bash
adb shell screencap -p /sdcard/calib_intro.png
adb pull /sdcard/calib_intro.png /Users/itsurkan/Dev/personal/TT_Coach/tmp/screenshots/calib_intro.png
```
Read the screenshot and confirm: title "Калібрування вправи", the three numbered steps, "Розпочати" and "Скасувати" buttons — and confirm the camera has NOT opened yet.

- [ ] **Step 4: Confirm "Скасувати" dismisses without opening the camera**

Tap "Скасувати". Screenshot again the same way as Step 3, saved as `calib_cancel.png`. Confirm you're back on the Drills screen, camera never opened.

- [ ] **Step 5: Confirm "Розпочати" proceeds to the camera**

Tap the FAB again, then tap "Розпочати". Screenshot again as `calib_proceed.png`. Confirm `CalibrationActivity`'s camera preview is now visible.

- [ ] **Step 6: Confirm Edit flow is unaffected**

Long-press an existing custom drill (if any exist) to open the options menu, tap "Редагувати", and confirm `CalibrationActivity` opens directly with no intro dialog shown first.

No commit for this task — verification only.
