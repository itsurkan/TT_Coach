# Calibration intro dialog on "Add drill" — design

## Problem

Tapping the gold FAB on the Drills screen jumps straight into `CalibrationActivity`
(camera) with no explanation of what calibration is or how to perform it. First-time
users aren't told to face the camera from the side, how many reps to do, or that the
system is capturing *their* technique as a personal baseline rather than judging them
against a "correct" form.

## Scope

Only the **add new custom drill** flow (`fabAddDrill` in `fragment_drills.xml`) gets the
new confirmation step. The **Edit** action on existing custom drills (`drill_action_edit`
in `showDrillOptions()`) continues to call `launchCustomDrillCalibration()` directly,
unchanged — a user editing an existing drill has already been through calibration once.

## Design

### Trigger point

`DrillsFragment.setupUI()`:

```kotlin
binding.fabAddDrill.setOnClickListener { showCalibrationIntroDialog() }
```

(Previously called `launchCustomDrillCalibration()` directly.)

### New method

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

`launchCustomDrillCalibration()` itself is unchanged — it still opens `CalibrationActivity`
via `calibrationLauncher`.

### UI mechanism

`AlertDialog.Builder`, matching existing patterns already used in this fragment
(`showDrillOptions()`, `promptForCustomDrillName()`). No new UI pattern introduced.

### Copy (new string resources)

Added to both `values/strings.xml` (English) and `values-uk/strings.xml` (Ukrainian),
placed near `drills_add_own_exercise`.

Ukrainian:
```xml
<string name="calibration_intro_title">Калібрування вправи</string>
<string name="calibration_intro_body">1. Розташуйте камеру збоку, на рівні пояса, на відстані 2–3 м.\n2. Виконайте 8–10 ударів у своєму звичному темпі.\n3. Система запам\'ятає ваші кути як еталон — це ваша особиста техніка, а не «правильна» форма.</string>
<string name="calibration_intro_proceed">Розпочати</string>
<string name="calibration_intro_cancel">Скасувати</string>
```

English:
```xml
<string name="calibration_intro_title">Exercise calibration</string>
<string name="calibration_intro_body">1. Place the camera to the side, at waist height, 2–3 m away.\n2. Perform 8–10 strokes at your natural pace.\n3. The app will remember your angles as the baseline — this is your own technique, not a "correct" form.</string>
<string name="calibration_intro_proceed">Start</string>
<string name="calibration_intro_cancel">Cancel</string>
```

### Persistence

None. The dialog shows every time the FAB is tapped — no "don't show again" flag, no
SharedPreferences. Creating a new custom drill is a rare action, so repetition isn't a
real burden, and it avoids adding state to manage.

## Out of scope

- No changes to `CalibrationActivity` itself or the calibration capture flow.
- No changes to the Edit-drill re-calibration path.
- No illustrations/step icons — text-only dialog, consistent with the rest of the fragment's dialogs.
