# Settings + Feedback + Detection (gold-dark) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Android Settings tab to the gold-dark `8a` design and add two new pushed screens — Feedback (`11a`) and Detection (`11b`) — reached from it, with dropped controls folded in and prefs-backed persistence seeded from real code defaults.

**Architecture:** Pure presentation + persistence track (frozen pipeline untouched). All new tuning persists via the existing central `SettingsManager` (SharedPreferences file `ai_coach_prefs`). Reuse existing gold-dark styles/widgets (`MaterialButtonToggleGroup`+`TTC.Segment.*` for pills, `SwitchMaterial`+`TTC.Toggle`, `Slider`+`TTC.Slider`, `Chip`+`TTC.Chip.Filter`, exposed dropdowns). The only new custom widget is a `TtcStepperView` (−/value/+). Feedback/Detection are non-tab nav destinations pushed from Settings with a back arrow; the Settings bottom-nav item stays highlighted on them.

**Tech Stack:** Kotlin, Android Views + view binding, Material 3, Navigation component, JUnit (JVM `src/test`).

## Global Constraints

- Do **not** modify the frozen pipeline (pose/ball/trajectory), shared/ code, or Room schema. New persistence is SharedPreferences-only, via `SettingsManager` (file `ai_coach_prefs`), reusing existing keys where they already exist. — verbatim from spec §1/§6/§9.
- **Persist-only:** controls save/restore across restart but do **not** drive the pipeline yet. — spec §2.1.
- **Real defaults win** over the mockup's illustrative slider positions. — spec §2.4/§7.
- Reuse existing `ttc_*` color tokens and `TTC.*` styles wherever they match; add new only when none exists. — spec §4.
- Language selection is stored only — **no runtime locale switch** in this work. — spec §1.
- Commit with explicit paths (never `git add -A`), commit after each logical change. — CLAUDE.md conventions.
- Coach selection, audio-feedback on/off, volume, correction chips, feedback frequency, camera resolution, frame rate, show-skeleton, ball-FPS, distance-mode already persist under existing `ai_coach_prefs` keys — reuse those exact keys, never orphan them. — spec §6 + code inventory.

**Existing keys to reuse (file `ai_coach_prefs`, via `SettingsManager`):**
`coaching_style`:Int, `audio_feedback_enabled`:Bool(true), `feedback_volume`:Int(80), `feedback_frequency`:Int(3), `correction_enabled_<TYPE>`:Bool(true) for TYPE in {WRIST, BODY_ROTATION, FOLLOW_THROUGH, CONTACT_HEIGHT, ELBOW_POSITION, STROKE_SPEED}, `camera_resolution`:Int(1), `target_fps`:Int(30), `show_skeleton`:Bool(true), `ball_detection_fps`:Int(30), `distance_mode_enabled`:Bool(false), `app_language`:String("").

**Existing tokens (values-night) reused for the design's dark hexes:**
`ttc_surface`=#141820 (card), `ttc_outline`=#232833 (stroke/divider), `ttc_sink`=#0B0E12 (inset well), `ttc_surface_elevated`=#1A1F28 (icon tile), `ttc_gold_bright`=#E9C46A, `ttc_gold_container`=#221C0F (gold-tint bg), `ttc_gold_container_outline`=#5C4A22 (gold-tint stroke), `ttc_toggle_knob`=#16130B, `ttc_text_1`=#E7EAF0, `ttc_text_2`=#B4BAC5, `ttc_text_3`=#7F8694, `ttc_text_faint`=#565C68 (knob off), `ttc_outline_strong`=#2F3542 (disabled ring).

---

## File Structure

**Create:**
- `app/src/main/java/com/ttcoachai/views/TtcStepperView.kt` — custom −/value/+ compound view.
- `app/src/main/java/com/ttcoachai/views/StepperRange.kt` — pure clamp/step logic (unit-tested).
- `app/src/main/res/layout/view_ttc_stepper.xml` — stepper internal layout.
- `app/src/main/res/values/attrs_ttc_stepper.xml` — stepper XML attrs.
- `app/src/main/res/layout/fragment_feedback.xml`, `.../fragment_detection.xml`
- `app/src/main/java/com/ttcoachai/fragment/FeedbackFragment.kt`, `.../DetectionFragment.kt`
- `app/src/main/res/drawable/bg_stepper_container.xml`, `bg_link_icon_tile.xml`
- `app/src/test/java/com/ttcoachai/settings/SettingsManagerTuningTest.kt`
- `app/src/test/java/com/ttcoachai/views/StepperRangeTest.kt`

**Modify:**
- `app/src/main/java/com/ttcoachai/managers/SettingsManager.kt` — add detection/feedback/language getters+setters+defaults.
- `app/src/main/res/values/colors.xml` + `values-night/colors.xml` — add `ttc_text_disabled`.
- `app/src/main/res/values/styles.xml` — add `TTC.Card.GoldTint`, `TextAppearance.TTC.Eyebrow.Gold`, `TTC.SectionHeader.Gold`, `TtcStepper.Button`, `TtcStepper.Value`.
- `app/src/main/res/values/strings.xml` — new labels.
- `app/src/main/res/layout/fragment_settings.xml` — rebuilt to `8a`.
- `app/src/main/java/com/ttcoachai/fragment/SettingsFragment.kt` — rebuilt for `8a`.
- `app/src/main/res/navigation/nav_graph.xml` — add feedback/detection destinations + actions.
- `app/src/main/java/com/ttcoachai/MainActivity.kt` — keep Settings item selected on child destinations.

---

## Task 1: Foundation — tokens, drawables, styles, strings

**Files:**
- Modify: `app/src/main/res/values/colors.xml`, `app/src/main/res/values-night/colors.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/bg_stepper_container.xml`, `app/src/main/res/drawable/bg_link_icon_tile.xml`

**Interfaces:**
- Produces: color `@color/ttc_text_disabled`; styles `TTC.Card.GoldTint`, `TextAppearance.TTC.Eyebrow.Gold`, `TTC.SectionHeader.Gold`, `TtcStepper.Button`, `TtcStepper.Value`; drawables `@drawable/bg_stepper_container`, `@drawable/bg_link_icon_tile`; the string names listed below.

- [ ] **Step 1: Add disabled-text token (light + dark)**

In `values/colors.xml` add:
```xml
<color name="ttc_text_disabled">#A0A5AF</color>
```
In `values-night/colors.xml` add:
```xml
<color name="ttc_text_disabled">#8A909E</color>
```

- [ ] **Step 2: Add gold-tint card + gold section-header styles**

Append to `values/styles.xml`:
```xml
<style name="TTC.Card.GoldTint" parent="TTC.Card">
    <item name="cardBackgroundColor">@color/ttc_gold_container</item>
    <item name="strokeColor">@color/ttc_gold_container_outline</item>
</style>

<style name="TextAppearance.TTC.Eyebrow.Gold" parent="TextAppearance.TTC.Eyebrow">
    <item name="android:textColor">@color/ttc_gold_accent</item>
</style>

<style name="TTC.SectionHeader.Gold" parent="Widget.AppCompat.TextView">
    <item name="android:textAppearance">@style/TextAppearance.TTC.Eyebrow.Gold</item>
</style>
```

- [ ] **Step 3: Add stepper button + value text styles**

Append to `values/styles.xml`:
```xml
<style name="TtcStepper.Button" parent="Widget.AppCompat.TextView">
    <item name="android:layout_width">33dp</item>
    <item name="android:layout_height">36dp</item>
    <item name="android:gravity">center</item>
    <item name="android:textSize">18sp</item>
    <item name="android:textColor">@color/ttc_text_2</item>
    <item name="android:background">?attr/selectableItemBackgroundBorderless</item>
</style>

<style name="TtcStepper.Value" parent="Widget.AppCompat.TextView">
    <item name="android:minWidth">50dp</item>
    <item name="android:gravity">center</item>
    <item name="android:paddingTop">9dp</item>
    <item name="android:paddingBottom">9dp</item>
    <item name="android:fontFamily">@font/jetbrains_mono_semibold</item>
    <item name="android:textSize">13.5sp</item>
    <item name="android:textColor">@color/ttc_text_1</item>
</style>
```

- [ ] **Step 4: Add stepper container + icon-tile drawables**

Create `app/src/main/res/drawable/bg_stepper_container.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/ttc_sink" />
    <stroke android:width="1dp" android:color="@color/ttc_outline" />
    <corners android:radius="9dp" />
</shape>
```
Create `app/src/main/res/drawable/bg_link_icon_tile.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/ttc_surface_elevated" />
    <corners android:radius="10dp" />
</shape>
```

- [ ] **Step 5: Add strings**

Append to `values/strings.xml` (exact names — used verbatim in later tasks):
```xml
<!-- Settings 8a -->
<string name="settings_title">Training settings</string>
<string name="settings_subtitle">Tune how your coach watches and talks to you</string>
<string name="settings_section_ai_coach">AI Coach</string>
<string name="settings_section_language">Language</string>
<string name="settings_section_feedback_detection">Feedback &amp; detection</string>
<string name="settings_section_camera">Camera</string>
<string name="settings_choose_coach">Choose your coach</string>
<string name="settings_choose_coach_sub">Pick a coaching style that fits how you like to train</string>
<string name="settings_interface_language">Interface language</string>
<string name="settings_interface_language_sub">Language of the app\'s screens and menus</string>
<string name="settings_coach_language">Coach language</string>
<string name="settings_coach_language_sub">Language your coach speaks and writes in</string>
<string name="settings_link_feedback">Feedback</string>
<string name="settings_link_feedback_sub">Cues, cadence &amp; praise · applies to all sessions</string>
<string name="settings_link_detection">Detection</string>
<string name="settings_link_detection_sub">Stroke picking · thresholds, smoothing &amp; timing</string>
<string name="settings_video_quality">Video quality</string>
<string name="settings_video_quality_sub">Higher quality improves pose detection</string>
<string name="settings_frame_rate">Frame rate</string>
<string name="settings_frame_rate_sub">Higher captures faster motion</string>
<string name="settings_show_skeleton">Show pose skeleton</string>
<string name="settings_show_skeleton_sub">Overlay skeleton visualization during training</string>
<string name="settings_tip">TIP</string>
<string name="settings_tip_body">Use 720p at 30 FPS with the pose skeleton on. This balance keeps feedback accurate without draining your battery.</string>
<string name="lang_en">EN</string>
<string name="lang_uk">UK</string>
<!-- Feedback 11a -->
<string name="feedback_title">Feedback</string>
<string name="feedback_subtitle">Applies to every session</string>
<string name="feedback_section_coaching">Coaching</string>
<string name="feedback_section_corrections">Corrections</string>
<string name="feedback_section_cue_zones">Cue zones</string>
<string name="feedback_section_cue_zones_sub">Global — layered on top of exercise strictness</string>
<string name="feedback_section_cadence">Cadence</string>
<string name="feedback_section_praise">Praise</string>
<string name="feedback_playing_hand">Playing hand</string>
<string name="feedback_hand_right">Right</string>
<string name="feedback_hand_left">Left</string>
<string name="feedback_voice_cues">Voice cues</string>
<string name="feedback_voice_cues_sub">Speak corrections aloud during play</string>
<string name="feedback_voice_volume">Voice volume</string>
<string name="feedback_zone_width">Zone width</string>
<string name="feedback_significance">Significance threshold</string>
<string name="feedback_alternate_cues">Alternate cues</string>
<string name="feedback_alternate_cues_sub">Rotate between flagged issues</string>
<string name="feedback_reminder_interval">Reminder interval</string>
<string name="feedback_pause_between">Pause between cues</string>
<string name="feedback_silence_before_praise">Silence before praise</string>
<string name="feedback_pause_after_stroke">Pause after stroke</string>
<string name="feedback_cues_per_session">Cues per session</string>
<string name="feedback_praise_enabled">Praise enabled</string>
<string name="feedback_praise_on_corrections">On corrections</string>
<string name="feedback_praise_on_streak">On streak</string>
<string name="feedback_streak_length">Streak length</string>
<string name="correction_wrist">Wrist Angle</string>
<string name="correction_rotation">Body Rotation</string>
<string name="correction_follow_through">Follow Through</string>
<string name="correction_contact_height">Contact Height</string>
<string name="correction_elbow">Elbow Position</string>
<string name="correction_speed">Stroke Speed</string>
<!-- Detection 11b -->
<string name="detection_title">Detection</string>
<string name="detection_subtitle">Advanced — how strokes are picked out</string>
<string name="detection_camera_angle">Camera angle</string>
<string name="detection_camera_angle_sub">Manual override · degrees</string>
<string name="detection_peak_speed">Peak speed threshold</string>
<string name="detection_peak_speed_sub">Torso-widths / second</string>
<string name="detection_min_interval">Min peak interval</string>
<string name="detection_min_interval_sub">Milliseconds</string>
<string name="detection_smoothing">Speed smoothing</string>
<string name="detection_smoothing_sub">Milliseconds · lower = more sensitive</string>
<string name="detection_walk_gate">Walk gate</string>
<string name="detection_walk_gate_sub">Pelvis shift · 0 = off</string>
<string name="detection_skip_stale">Skip stale reps</string>
<string name="detection_skip_stale_sub">Drop late-detected strokes</string>
<string name="detection_prestroke_buffer">Pre-stroke buffer</string>
<string name="detection_prestroke_buffer_sub">Milliseconds</string>
<string name="detection_ball_fps">Ball detection FPS</string>
<string name="detection_ball_fps_sub">Ball tracking capture rate</string>
<string name="detection_distance_mode">Distance mode</string>
<string name="detection_distance_mode_sub">Track ball distance from table</string>
<string name="cd_back">Back</string>
```

- [ ] **Step 6: Build to verify resources compile**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (no resource-linking errors).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml \
  app/src/main/res/values/styles.xml app/src/main/res/values/strings.xml \
  app/src/main/res/drawable/bg_stepper_container.xml app/src/main/res/drawable/bg_link_icon_tile.xml
git commit -m "feat(ui): gold-dark tokens/styles/strings for settings redesign

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: SettingsManager tuning persistence

Add typed getters/setters + real defaults for detection, feedback, and coach-language params. Reuse existing keys for voice-cues (`audio_feedback_enabled`), voice-volume (`feedback_volume`), cues-per-session (`feedback_frequency`), corrections (`correction_enabled_*`), interface language (`app_language`). All new keys live in the same `ai_coach_prefs` file.

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/managers/SettingsManager.kt`
- Test: `app/src/test/java/com/ttcoachai/settings/SettingsManagerTuningTest.kt`

**Interfaces:**
- Consumes: existing `SettingsManager(context)` with its `prefs` (SharedPreferences on `ai_coach_prefs`). Inspect the file first to match its existing accessor style (property vs get/set fn).
- Produces (new public API on `SettingsManager`):
  - Detection: `detCameraAngle:Int` (def 0), `detPeakSpeed:Float` (def 1.0f), `detMinPeakIntervalMs:Int` (def 500), `detSpeedSmoothingMs:Int` (def 300), `detWalkGate:Float` (def 0.4f), `detSkipStaleReps:Boolean` (def true), `detPreStrokeBufferMs:Int` (def 1000), `ballDetectionFps:Int` (existing key `ball_detection_fps`, def 30), `distanceModeEnabled:Boolean` (existing `distance_mode_enabled`, def false).
  - Feedback: `voiceCuesEnabled:Boolean` (existing `audio_feedback_enabled`, def true), `voiceVolume:Int` (existing `feedback_volume`, def 80), `fbZoneWidth:Float` (def 1.4f), `fbSignificanceDeg:Int` (def 7), `fbReminderIntervalMs:Int` (def 10000), `fbAlternateCues:Boolean` (def true), `fbPauseBetweenMs:Int` (def 5000), `fbSilenceBeforePraiseMs:Int` (def 10000), `fbPauseAfterStrokeMs:Int` (def 300), `cuesPerSession:Int` (existing `feedback_frequency`, def 3), `praiseEnabled:Boolean` (def true), `praiseOnCorrections:Boolean` (def true), `praiseOnStreak:Boolean` (def false), `praiseStreakLen:Int` (def 3), `playingHandRight:Boolean` (new key `fb_playing_hand_right`, def true).
  - Language: `coachLanguage:String` (new key `coach_language`, def "en").

New pref key strings: `det_camera_angle`, `det_peak_speed`, `det_min_peak_interval`, `det_speed_smoothing`, `det_walk_gate`, `det_skip_stale`, `det_prestroke_buffer`, `fb_zone_width`, `fb_significance`, `fb_reminder_ms`, `fb_alternate_cues`, `fb_pause_between_ms`, `fb_silence_before_praise_ms`, `fb_pause_after_stroke_ms`, `fb_praise_enabled`, `fb_praise_on_corrections`, `fb_praise_on_streak`, `fb_praise_streak_len`, `fb_playing_hand_right`, `coach_language`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ttcoachai/settings/SettingsManagerTuningTest.kt`. Use Robolectric if the project already has it; otherwise mock `SharedPreferences`. Check the project's existing `src/test` for the established pattern first and match it. Test body asserts real defaults and round-trip:
```kotlin
package com.ttcoachai.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ttcoachai.managers.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsManagerTuningTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun detection_defaults_match_real_code() {
        val sm = SettingsManager(ctx)
        assertEquals(0, sm.detCameraAngle)
        assertEquals(1.0f, sm.detPeakSpeed, 0.0001f)
        assertEquals(500, sm.detMinPeakIntervalMs)
        assertEquals(300, sm.detSpeedSmoothingMs)
        assertEquals(0.4f, sm.detWalkGate, 0.0001f)
        assertEquals(true, sm.detSkipStaleReps)
        assertEquals(1000, sm.detPreStrokeBufferMs)
    }

    @Test fun feedback_defaults_match_real_code() {
        val sm = SettingsManager(ctx)
        assertEquals(1.4f, sm.fbZoneWidth, 0.0001f)
        assertEquals(7, sm.fbSignificanceDeg)
        assertEquals(10000, sm.fbReminderIntervalMs)
        assertEquals(true, sm.fbAlternateCues)
        assertEquals(5000, sm.fbPauseBetweenMs)
        assertEquals(10000, sm.fbSilenceBeforePraiseMs)
        assertEquals(300, sm.fbPauseAfterStrokeMs)
        assertEquals(true, sm.praiseEnabled)
        assertEquals(false, sm.praiseOnStreak)
        assertEquals(3, sm.praiseStreakLen)
    }

    @Test fun round_trip_persists() {
        val sm = SettingsManager(ctx)
        sm.detPeakSpeed = 1.5f
        sm.fbSignificanceDeg = 9
        sm.coachLanguage = "uk"
        val sm2 = SettingsManager(ctx)
        assertEquals(1.5f, sm2.detPeakSpeed, 0.0001f)
        assertEquals(9, sm2.fbSignificanceDeg)
        assertEquals("uk", sm2.coachLanguage)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.settings.SettingsManagerTuningTest"`
Expected: FAIL — unresolved references (`detPeakSpeed`, etc.).

- [ ] **Step 3: Implement accessors on SettingsManager**

Add to `SettingsManager.kt`, matching its existing accessor idiom. Example (var-property style over `prefs`):
```kotlin
// --- Detection tuning (persist-only; real defaults from StrokeDetector2D / CameraAngleEstimator / LocomotionFilter) ---
var detCameraAngle: Int
    get() = prefs.getInt("det_camera_angle", 0)
    set(v) { prefs.edit().putInt("det_camera_angle", v).apply() }
var detPeakSpeed: Float
    get() = prefs.getFloat("det_peak_speed", 1.0f)
    set(v) { prefs.edit().putFloat("det_peak_speed", v).apply() }
var detMinPeakIntervalMs: Int
    get() = prefs.getInt("det_min_peak_interval", 500)
    set(v) { prefs.edit().putInt("det_min_peak_interval", v).apply() }
var detSpeedSmoothingMs: Int
    get() = prefs.getInt("det_speed_smoothing", 300)
    set(v) { prefs.edit().putInt("det_speed_smoothing", v).apply() }
var detWalkGate: Float
    get() = prefs.getFloat("det_walk_gate", 0.4f)
    set(v) { prefs.edit().putFloat("det_walk_gate", v).apply() }
var detSkipStaleReps: Boolean
    get() = prefs.getBoolean("det_skip_stale", true)
    set(v) { prefs.edit().putBoolean("det_skip_stale", v).apply() }
var detPreStrokeBufferMs: Int
    get() = prefs.getInt("det_prestroke_buffer", 1000)
    set(v) { prefs.edit().putInt("det_prestroke_buffer", v).apply() }

// --- Feedback tuning (real defaults from feedbackSettings.ts DEFAULT_FEEDBACK_SETTINGS) ---
var playingHandRight: Boolean
    get() = prefs.getBoolean("fb_playing_hand_right", true)
    set(v) { prefs.edit().putBoolean("fb_playing_hand_right", v).apply() }
var fbZoneWidth: Float
    get() = prefs.getFloat("fb_zone_width", 1.4f)
    set(v) { prefs.edit().putFloat("fb_zone_width", v).apply() }
var fbSignificanceDeg: Int
    get() = prefs.getInt("fb_significance", 7)
    set(v) { prefs.edit().putInt("fb_significance", v).apply() }
var fbReminderIntervalMs: Int
    get() = prefs.getInt("fb_reminder_ms", 10000)
    set(v) { prefs.edit().putInt("fb_reminder_ms", v).apply() }
var fbAlternateCues: Boolean
    get() = prefs.getBoolean("fb_alternate_cues", true)
    set(v) { prefs.edit().putBoolean("fb_alternate_cues", v).apply() }
var fbPauseBetweenMs: Int
    get() = prefs.getInt("fb_pause_between_ms", 5000)
    set(v) { prefs.edit().putInt("fb_pause_between_ms", v).apply() }
var fbSilenceBeforePraiseMs: Int
    get() = prefs.getInt("fb_silence_before_praise_ms", 10000)
    set(v) { prefs.edit().putInt("fb_silence_before_praise_ms", v).apply() }
var fbPauseAfterStrokeMs: Int
    get() = prefs.getInt("fb_pause_after_stroke_ms", 300)
    set(v) { prefs.edit().putInt("fb_pause_after_stroke_ms", v).apply() }
var praiseEnabled: Boolean
    get() = prefs.getBoolean("fb_praise_enabled", true)
    set(v) { prefs.edit().putBoolean("fb_praise_enabled", v).apply() }
var praiseOnCorrections: Boolean
    get() = prefs.getBoolean("fb_praise_on_corrections", true)
    set(v) { prefs.edit().putBoolean("fb_praise_on_corrections", v).apply() }
var praiseOnStreak: Boolean
    get() = prefs.getBoolean("fb_praise_on_streak", false)
    set(v) { prefs.edit().putBoolean("fb_praise_on_streak", v).apply() }
var praiseStreakLen: Int
    get() = prefs.getInt("fb_praise_streak_len", 3)
    set(v) { prefs.edit().putInt("fb_praise_streak_len", v).apply() }

// --- reuse existing keys ---
var voiceCuesEnabled: Boolean
    get() = prefs.getBoolean("audio_feedback_enabled", true)
    set(v) { prefs.edit().putBoolean("audio_feedback_enabled", v).apply() }
var voiceVolume: Int
    get() = prefs.getInt("feedback_volume", 80)
    set(v) { prefs.edit().putInt("feedback_volume", v).apply() }
var cuesPerSession: Int
    get() = prefs.getInt("feedback_frequency", 3)
    set(v) { prefs.edit().putInt("feedback_frequency", v).apply() }
var coachLanguage: String
    get() = prefs.getString("coach_language", "en") ?: "en"
    set(v) { prefs.edit().putString("coach_language", v).apply() }
```
If `SettingsManager` exposes prefs under a different name than `prefs`, adapt. If it already has `ballDetectionFps`/`distanceModeEnabled`/`interfaceLanguage`(`app_language`) accessors, reuse them; only add if missing.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.settings.SettingsManagerTuningTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ttcoachai/managers/SettingsManager.kt \
  app/src/test/java/com/ttcoachai/settings/SettingsManagerTuningTest.kt
git commit -m "feat(settings): persist detection/feedback/language tuning with real defaults

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: TtcStepperView custom widget

A −/value/+ control. Split pure clamp/step math into `StepperRange` (JVM-unit-tested); the View wires clicks + rendering.

**Files:**
- Create: `app/src/main/java/com/ttcoachai/views/StepperRange.kt`
- Create: `app/src/main/java/com/ttcoachai/views/TtcStepperView.kt`
- Create: `app/src/main/res/layout/view_ttc_stepper.xml`
- Create: `app/src/main/res/values/attrs_ttc_stepper.xml`
- Test: `app/src/test/java/com/ttcoachai/views/StepperRangeTest.kt`

**Interfaces:**
- Produces:
  - `data class StepperRange(min:Double, max:Double, step:Double, decimals:Int)` with `fun clamp(v:Double):Double`, `fun inc(v:Double):Double`, `fun dec(v:Double):Double`, `fun format(v:Double, suffix:String):String`.
  - `class TtcStepperView(context, attrs) : LinearLayout` with: `var value: Double`, `fun configure(range: StepperRange, unitSuffix: String, initial: Double)`, `var onValueChanged: ((Double) -> Unit)?`. XML attrs `ttcMin`, `ttcMax`, `ttcStep`, `ttcDecimals`, `ttcSuffix`.

- [ ] **Step 1: Write failing StepperRange test**

Create `app/src/test/java/com/ttcoachai/views/StepperRangeTest.kt`:
```kotlin
package com.ttcoachai.views

import org.junit.Assert.assertEquals
import org.junit.Test

class StepperRangeTest {
    @Test fun inc_and_clamp_at_max() {
        val r = StepperRange(min = 0.0, max = 2.0, step = 0.1, decimals = 1)
        assertEquals(1.1, r.inc(1.0), 1e-9)
        assertEquals(2.0, r.inc(2.0), 1e-9)   // clamps
    }
    @Test fun dec_and_clamp_at_min() {
        val r = StepperRange(0.0, 2.0, 0.1, 1)
        assertEquals(0.0, r.dec(0.0), 1e-9)
    }
    @Test fun format_int_vs_decimal_with_suffix() {
        assertEquals("500ms", StepperRange(0.0, 2000.0, 50.0, 0).format(500.0, "ms"))
        assertEquals("1.0", StepperRange(0.0, 2.0, 0.1, 1).format(1.0, ""))
        assertEquals("0°", StepperRange(-45.0, 45.0, 5.0, 0).format(0.0, "°"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.views.StepperRangeTest"`
Expected: FAIL — `StepperRange` unresolved.

- [ ] **Step 3: Implement StepperRange**

Create `app/src/main/java/com/ttcoachai/views/StepperRange.kt`:
```kotlin
package com.ttcoachai.views

import kotlin.math.round

data class StepperRange(
    val min: Double,
    val max: Double,
    val step: Double,
    val decimals: Int,
) {
    fun clamp(v: Double): Double = v.coerceIn(min, max)
    fun inc(v: Double): Double = clamp(snap(v + step))
    fun dec(v: Double): Double = clamp(snap(v - step))
    private fun snap(v: Double): Double = round(v / step) * step
    fun format(v: Double, suffix: String): String {
        val n = if (decimals == 0) round(v).toInt().toString()
                else "%.${decimals}f".format(clamp(v))
        return n + suffix
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.views.StepperRangeTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Add stepper attrs + internal layout**

Create `app/src/main/res/values/attrs_ttc_stepper.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <declare-styleable name="TtcStepperView">
        <attr name="ttcMin" format="float" />
        <attr name="ttcMax" format="float" />
        <attr name="ttcStep" format="float" />
        <attr name="ttcDecimals" format="integer" />
        <attr name="ttcSuffix" format="string" />
    </declare-styleable>
</resources>
```
Create `app/src/main/res/layout/view_ttc_stepper.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:android="http://schemas.android.com/apk/res/android">
    <TextView android:id="@+id/stepper_minus" style="@style/TtcStepper.Button" android:text="−" />
    <View android:layout_width="1dp" android:layout_height="match_parent"
        android:background="@color/ttc_outline" />
    <TextView android:id="@+id/stepper_value" style="@style/TtcStepper.Value" tools:text="500ms"
        xmlns:tools="http://schemas.android.com/tools" />
    <View android:layout_width="1dp" android:layout_height="match_parent"
        android:background="@color/ttc_outline" />
    <TextView android:id="@+id/stepper_plus" style="@style/TtcStepper.Button" android:text="+" />
</merge>
```

- [ ] **Step 6: Implement TtcStepperView**

Create `app/src/main/java/com/ttcoachai/views/TtcStepperView.kt`:
```kotlin
package com.ttcoachai.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.ttcoachai.R

class TtcStepperView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val minusBtn: TextView
    private val plusBtn: TextView
    private val valueLabel: TextView
    private var range = StepperRange(0.0, 100.0, 1.0, 0)
    private var suffix = ""

    var onValueChanged: ((Double) -> Unit)? = null
    var value: Double = 0.0
        set(v) { field = range.clamp(v); render() }

    init {
        orientation = HORIZONTAL
        setBackgroundResource(R.drawable.bg_stepper_container)
        LayoutInflater.from(context).inflate(R.layout.view_ttc_stepper, this, true)
        minusBtn = findViewById(R.id.stepper_minus)
        plusBtn = findViewById(R.id.stepper_plus)
        valueLabel = findViewById(R.id.stepper_value)

        var min = 0.0; var max = 100.0; var step = 1.0; var decimals = 0
        context.obtainStyledAttributes(attrs, R.styleable.TtcStepperView).use { a ->
            min = a.getFloat(R.styleable.TtcStepperView_ttcMin, 0f).toDouble()
            max = a.getFloat(R.styleable.TtcStepperView_ttcMax, 100f).toDouble()
            step = a.getFloat(R.styleable.TtcStepperView_ttcStep, 1f).toDouble()
            decimals = a.getInt(R.styleable.TtcStepperView_ttcDecimals, 0)
            suffix = a.getString(R.styleable.TtcStepperView_ttcSuffix) ?: ""
        }
        range = StepperRange(min, max, step, decimals)

        minusBtn.setOnClickListener { setAndEmit(range.dec(value)) }
        plusBtn.setOnClickListener { setAndEmit(range.inc(value)) }
        render()
    }

    fun configure(range: StepperRange, unitSuffix: String, initial: Double) {
        this.range = range; this.suffix = unitSuffix; value = initial
    }

    private fun setAndEmit(v: Double) { value = v; onValueChanged?.invoke(v) }
    private fun render() { valueLabel.text = range.format(value, suffix) }
}
```
(Requires `androidx.core` `use {}` extension for `TypedArray`; if unavailable, wrap in try/finally + `recycle()`.)

- [ ] **Step 7: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ttcoachai/views/StepperRange.kt \
  app/src/main/java/com/ttcoachai/views/TtcStepperView.kt \
  app/src/main/res/layout/view_ttc_stepper.xml \
  app/src/main/res/values/attrs_ttc_stepper.xml \
  app/src/test/java/com/ttcoachai/views/StepperRangeTest.kt
git commit -m "feat(ui): TtcStepperView (−/value/+) with tested StepperRange math

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Detection screen (`11b`)

**Files:**
- Create: `app/src/main/res/layout/fragment_detection.xml`
- Create: `app/src/main/java/com/ttcoachai/fragment/DetectionFragment.kt`
- Modify: `app/src/main/res/navigation/nav_graph.xml` (add destination + it will be linked from Settings in Task 6)

**Interfaces:**
- Consumes: `TtcStepperView`, `StepperRange`, `SettingsManager` accessors (Task 2/3), styles/strings (Task 1).
- Produces: nav destination `@+id/navigation_detection` → `com.ttcoachai.fragment.DetectionFragment`, layout `@layout/fragment_detection`.

- [ ] **Step 1: Add nav destination**

In `nav_graph.xml`, after `navigation_settings`, add:
```xml
<fragment
    android:id="@+id/navigation_detection"
    android:name="com.ttcoachai.fragment.DetectionFragment"
    android:label="@string/detection_title"
    tools:layout="@layout/fragment_detection" />
```

- [ ] **Step 2: Build the layout**

Create `app/src/main/res/layout/fragment_detection.xml`. Structure: root `CoordinatorLayout` → `NestedScrollView` → vertical `LinearLayout` (padding 20dp, bg `@color/ttc_canvas`). Header row: 34dp back button (`ImageButton` id `btn_back`, `bg_link_icon_tile`, `ic_arrow_back` tinted `ttc_text_1`, `contentDescription="@string/cd_back"`) + title block (`detection_title` in `TextAppearance.TTC.Title.Screen`, `detection_subtitle` in `TextAppearance.TTC.Body.Secondary`). Then one `TTC.Card` containing 9 rows in a vertical LinearLayout, dividers between rows (`<View height=1dp bg=@color/ttc_outline` with 12dp vertical margins).

Each stepper row = horizontal LinearLayout: left vertical block (title `TextAppearance.TTC.Title.Card` @ 13.5sp weight, sub `TextAppearance.TTC.Body.Secondary`) with `layout_weight=1`, right a `TtcStepperView`. Worked example (Camera angle row):
```xml
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="horizontal" android:gravity="center_vertical">
    <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content"
        android:layout_weight="1" android:orientation="vertical">
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.TTC.Title.Card"
            android:textSize="13.5sp" android:text="@string/detection_camera_angle" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.TTC.Body.Secondary"
            android:text="@string/detection_camera_angle_sub" />
    </LinearLayout>
    <com.ttcoachai.views.TtcStepperView android:id="@+id/stepper_camera_angle"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        app:ttcMin="-45" app:ttcMax="45" app:ttcStep="5" app:ttcDecimals="0" app:ttcSuffix="°" />
</LinearLayout>
```
Enumerate all rows with ids / attrs / strings:
| id | title / sub strings | control | attrs |
|---|---|---|---|
| `stepper_camera_angle` | detection_camera_angle / _sub | stepper | min −45 max 45 step 5 dec 0 suffix ° |
| `stepper_peak_speed` | detection_peak_speed / _sub | stepper | min 0.2 max 3.0 step 0.1 dec 1 suffix "" |
| `stepper_min_interval` | detection_min_interval / _sub | stepper | min 100 max 2000 step 50 dec 0 suffix ms |
| `stepper_smoothing` | detection_smoothing / _sub | stepper | min 50 max 1000 step 50 dec 0 suffix ms |
| `stepper_walk_gate` | detection_walk_gate / _sub | stepper | min 0 max 1.5 step 0.1 dec 1 suffix "" |
| `switch_skip_stale` | detection_skip_stale / _sub | `SwitchMaterial` style `@style/TTC.Toggle` | — |
| `stepper_prestroke` | detection_prestroke_buffer / _sub | stepper | min 0 max 2000 step 50 dec 0 suffix ms |
| `toggle_ball_fps` (`MaterialButtonToggleGroup`, singleSelection, children `btn_ball_fps_10/30/60/120` styled `TTC.Segment.Button`, text 10/30/60/120) | detection_ball_fps / _sub | segmented | — |
| `switch_distance_mode` | detection_distance_mode / _sub | `SwitchMaterial` `TTC.Toggle` | — |

For toggle/switch rows the right control replaces the `TtcStepperView`. Reuse the exact `MaterialButtonToggleGroup`/`SwitchMaterial` markup from the current `fragment_settings.xml` (frame-rate group / skeleton switch) so styling matches.

- [ ] **Step 3: Build the fragment**

Create `app/src/main/java/com/ttcoachai/fragment/DetectionFragment.kt` using view binding (`FragmentDetectionBinding`). In `onViewCreated`: `val sm = SettingsManager(requireContext())`; seed each control from `sm` and write back on change. Wire back button: `binding.btnBack.setOnClickListener { findNavController().navigateUp() }`. Example wiring:
```kotlin
binding.stepperCameraAngle.apply {
    value = sm.detCameraAngle.toDouble()
    onValueChanged = { sm.detCameraAngle = it.toInt() }
}
binding.stepperPeakSpeed.apply {
    value = sm.detPeakSpeed.toDouble()
    onValueChanged = { sm.detPeakSpeed = it.toFloat() }
}
// ...min interval, smoothing, walk gate, prestroke analogous (Int/Float per accessor)...
binding.switchSkipStale.isChecked = sm.detSkipStaleReps
binding.switchSkipStale.setOnCheckedChangeListener { _, c -> sm.detSkipStaleReps = c }
binding.switchDistanceMode.isChecked = sm.distanceModeEnabled
binding.switchDistanceMode.setOnCheckedChangeListener { _, c -> sm.distanceModeEnabled = c }
// ball fps: map checkedButtonId -> 10/30/60/120 and preselect from sm.ballDetectionFps
```
Follow the existing `MaterialButtonToggleGroup` selection idiom already in `SettingsFragment.kt` for the ball-FPS group (check `addOnButtonCheckedListener`, `isChecked` guard).

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/fragment_detection.xml \
  app/src/main/java/com/ttcoachai/fragment/DetectionFragment.kt \
  app/src/main/res/navigation/nav_graph.xml
git commit -m "feat(detection): 11b detection tuning screen (steppers + ball-fps/distance)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Feedback screen (`11a`)

**Files:**
- Create: `app/src/main/res/layout/fragment_feedback.xml`
- Create: `app/src/main/java/com/ttcoachai/fragment/FeedbackFragment.kt`
- Modify: `app/src/main/res/navigation/nav_graph.xml`

**Interfaces:**
- Consumes: `SettingsManager` accessors, styles/strings, existing segmented/switch/slider/chip styles.
- Produces: nav destination `@+id/navigation_feedback` → `com.ttcoachai.fragment.FeedbackFragment`, layout `@layout/fragment_feedback`.

- [ ] **Step 1: Add nav destination**

In `nav_graph.xml` add:
```xml
<fragment
    android:id="@+id/navigation_feedback"
    android:name="com.ttcoachai.fragment.FeedbackFragment"
    android:label="@string/feedback_title"
    tools:layout="@layout/fragment_feedback" />
```

- [ ] **Step 2: Build the layout**

Create `fragment_feedback.xml`: same header pattern as Detection (back button `btn_back`, title `feedback_title`, subtitle `feedback_subtitle`). One `TTC.Card` with 5 subsections, each led by a gold mini-header (`TextView` style `@style/TTC.SectionHeader.Gold`), separated by full-bleed dividers (`<View 1dp @color/ttc_outline`, `layout_marginHorizontal="-16dp"`, 14dp vertical margin).

Controls per subsection (ids exact):
- **Coaching:** header `feedback_section_coaching`. Row: `feedback_playing_hand` + `MaterialButtonToggleGroup` `toggle_playing_hand` (children `btn_hand_right`/`btn_hand_left`, `TTC.Segment.Button`, text `feedback_hand_right`/`feedback_hand_left`, singleSelection). Row: `feedback_voice_cues` + sub + `SwitchMaterial` `switch_voice_cues` (`TTC.Toggle`). Row: `feedback_voice_volume` + `Slider` `slider_voice_volume` (`TTC.Slider`, `valueFrom=0 valueTo=100 stepSize=1`).
- **Corrections:** header `feedback_section_corrections`. `ChipGroup` `chip_group_corrections` with 6 `Chip` (`TTC.Chip.Filter`, `checkable`), ids `chip_wrist`,`chip_rotation`,`chip_follow_through`,`chip_contact_height`,`chip_elbow`,`chip_speed`, texts from `correction_*` strings.
- **Cue zones:** header `feedback_section_cue_zones` + sub line `feedback_section_cue_zones_sub`. Slider rows with a right-aligned gold mono value label each (`TextAppearance.TTC.Mono.Meta` tinted `ttc_gold_accent`): `slider_zone_width` (`valueFrom=0.5 valueTo=3.0 stepSize=0.1`, label id `tv_zone_width`), `slider_significance` (`valueFrom=1 valueTo=20 stepSize=1`, `tv_significance`). Row `feedback_alternate_cues`+sub + `SwitchMaterial` `switch_alternate_cues`.
- **Cadence:** header `feedback_section_cadence`. Slider rows with value labels: `slider_reminder` (0–30s, step 0.5 → store ms; label `tv_reminder`), `slider_pause_between` (0–15s step 0.1, `tv_pause_between`), `slider_silence_praise` (0–20s step 0.1, `tv_silence_praise`), `slider_pause_after` (0–2s step 0.1, `tv_pause_after`). Row `feedback_cues_per_session` + `MaterialButtonToggleGroup` `toggle_cues_per_session` (children `btn_cues_3/5/10`, text 3/5/10).
- **Praise:** header `feedback_section_praise`. Master `SwitchMaterial` `switch_praise_enabled` (row `feedback_praise_enabled`). Child rows: `feedback_praise_on_corrections`+`switch_praise_corrections`, `feedback_praise_on_streak`+`switch_praise_streak`, `feedback_streak_length`+`slider_streak_len` (`valueFrom=1 valueTo=10 stepSize=1`, label `tv_streak_len`). Group child controls under a container id `group_praise_children` so it can be enabled/disabled together.

Slider value-label pattern (reused): put the slider under a header row that has the title on the left and a `tv_*` gold value on the right; update `tv_*` in the slider's change listener.

- [ ] **Step 3: Build the fragment**

Create `FeedbackFragment.kt` (view binding `FragmentFeedbackBinding`). Back button → `navigateUp()`. Seed + persist each control from `SettingsManager`. Store seconds-based sliders as ms (`(value*1000).toInt()`), display seconds with `"%.1fs"`. Example:
```kotlin
val sm = SettingsManager(requireContext())
// playing hand
if (sm.playingHandRight) binding.togglePlayingHand.check(R.id.btn_hand_right)
else binding.togglePlayingHand.check(R.id.btn_hand_left)
binding.togglePlayingHand.addOnButtonCheckedListener { _, id, checked ->
    if (checked) sm.playingHandRight = (id == R.id.btn_hand_right)
}
// voice cues + volume gating
binding.switchVoiceCues.isChecked = sm.voiceCuesEnabled
binding.sliderVoiceVolume.value = sm.voiceVolume.toFloat()
binding.sliderVoiceVolume.isEnabled = sm.voiceCuesEnabled
binding.switchVoiceCues.setOnCheckedChangeListener { _, c ->
    sm.voiceCuesEnabled = c; binding.sliderVoiceVolume.isEnabled = c
}
binding.sliderVoiceVolume.addOnChangeListener { _, v, _ -> sm.voiceVolume = v.toInt() }
// significance (int deg)
binding.sliderSignificance.value = sm.fbSignificanceDeg.toFloat()
binding.tvSignificance.text = "${sm.fbSignificanceDeg}°"
binding.sliderSignificance.addOnChangeListener { _, v, _ ->
    sm.fbSignificanceDeg = v.toInt(); binding.tvSignificance.text = "${v.toInt()}°"
}
// zone width (×, 1 decimal)
binding.sliderZoneWidth.value = sm.fbZoneWidth
binding.tvZoneWidth.text = "×%.1f".format(sm.fbZoneWidth)
binding.sliderZoneWidth.addOnChangeListener { _, v, _ ->
    sm.fbZoneWidth = v; binding.tvZoneWidth.text = "×%.1f".format(v)
}
// reminder (s <-> ms)
binding.sliderReminder.value = sm.fbReminderIntervalMs / 1000f
binding.tvReminder.text = "%.1fs".format(sm.fbReminderIntervalMs / 1000f)
binding.sliderReminder.addOnChangeListener { _, v, _ ->
    sm.fbReminderIntervalMs = (v * 1000).toInt(); binding.tvReminder.text = "%.1fs".format(v)
}
// pauseBetween / silencePraise / pauseAfter analogous to reminder
// alternate cues
binding.switchAlternateCues.isChecked = sm.fbAlternateCues
binding.switchAlternateCues.setOnCheckedChangeListener { _, c -> sm.fbAlternateCues = c }
// cues per session (3/5/10)
binding.toggleCuesPerSession.check(when (sm.cuesPerSession) {
    5 -> R.id.btn_cues_5; 10 -> R.id.btn_cues_10; else -> R.id.btn_cues_3 })
binding.toggleCuesPerSession.addOnButtonCheckedListener { _, id, checked ->
    if (checked) sm.cuesPerSession = when (id) {
        R.id.btn_cues_5 -> 5; R.id.btn_cues_10 -> 10; else -> 3 }
}
// corrections chips
data class CorrChip(val chipId: Int, val type: String)
listOf(
    CorrChip(R.id.chip_wrist, "WRIST"),
    CorrChip(R.id.chip_rotation, "BODY_ROTATION"),
    CorrChip(R.id.chip_follow_through, "FOLLOW_THROUGH"),
    CorrChip(R.id.chip_contact_height, "CONTACT_HEIGHT"),
    CorrChip(R.id.chip_elbow, "ELBOW_POSITION"),
    CorrChip(R.id.chip_speed, "STROKE_SPEED"),
).forEach { cc ->
    val chip = binding.root.findViewById<com.google.android.material.chip.Chip>(cc.chipId)
    chip.isChecked = sm.prefs.getBoolean("correction_enabled_${cc.type}", true)
    chip.setOnCheckedChangeListener { _, c ->
        sm.prefs.edit().putBoolean("correction_enabled_${cc.type}", c).apply()
    }
}
// praise group enable/disable
fun applyPraiseEnabled(on: Boolean) {
    binding.groupPraiseChildren.isEnabled = on
    setChildrenEnabled(binding.groupPraiseChildren, on) // helper: recurse, setEnabled + alpha
}
binding.switchPraiseEnabled.isChecked = sm.praiseEnabled
applyPraiseEnabled(sm.praiseEnabled)
binding.switchPraiseEnabled.setOnCheckedChangeListener { _, c ->
    sm.praiseEnabled = c; applyPraiseEnabled(c)
}
binding.switchPraiseCorrections.isChecked = sm.praiseOnCorrections
binding.switchPraiseCorrections.setOnCheckedChangeListener { _, c -> sm.praiseOnCorrections = c }
binding.switchPraiseStreak.isChecked = sm.praiseOnStreak
binding.switchPraiseStreak.setOnCheckedChangeListener { _, c -> sm.praiseOnStreak = c }
binding.sliderStreakLen.value = sm.praiseStreakLen.toFloat()
binding.tvStreakLen.text = sm.praiseStreakLen.toString()
binding.sliderStreakLen.addOnChangeListener { _, v, _ ->
    sm.praiseStreakLen = v.toInt(); binding.tvStreakLen.text = v.toInt().toString()
}
```
Add a private helper:
```kotlin
private fun setChildrenEnabled(v: android.view.View, enabled: Boolean) {
    v.isEnabled = enabled
    v.alpha = if (enabled) 1f else 0.45f
    if (v is android.view.ViewGroup) for (i in 0 until v.childCount) setChildrenEnabled(v.getChildAt(i), enabled)
}
```
If `SettingsManager.prefs` is private, add a small `fun isCorrectionEnabled(type:String):Boolean` / `fun setCorrectionEnabled(type:String,v:Boolean)` to `SettingsManager` (reusing the existing `correction_enabled_` key) and call those instead of touching `prefs` directly.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/fragment_feedback.xml \
  app/src/main/java/com/ttcoachai/fragment/FeedbackFragment.kt \
  app/src/main/res/navigation/nav_graph.xml
git commit -m "feat(feedback): 11a feedback screen (coaching/cue-zones/cadence/praise)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Rebuild Settings (`8a`) + link to Feedback/Detection

**Files:**
- Modify: `app/src/main/res/layout/fragment_settings.xml` (rebuild)
- Modify: `app/src/main/java/com/ttcoachai/fragment/SettingsFragment.kt` (rebuild)
- Modify: `app/src/main/java/com/ttcoachai/MainActivity.kt` (keep Settings selected on child destinations)

**Interfaces:**
- Consumes: nav destinations `navigation_feedback`/`navigation_detection` (Tasks 4/5), `SettingsManager`, `TTC.LinkRow`/`TTC.Card.GoldTint`/`TTC.SectionHeader.Gold` styles.
- Produces: rebuilt Settings screen matching `8a`.

- [ ] **Step 1: Rebuild fragment_settings.xml**

Replace with `8a`: `CoordinatorLayout`→`NestedScrollView`→vertical `LinearLayout` (padding 16dp, bg `@color/ttc_canvas`, section gap via 20dp `marginTop`). Header: `settings_title` (`TextAppearance.TTC.Title.Screen`), `settings_subtitle` (`TextAppearance.TTC.Body.Secondary`). No back arrow.

Sections (each = above-card gold header row [gold icon + `TTC.SectionHeader.Gold` label] then a `TTC.Card`):
- **AI Coach:** `settings_choose_coach` title + `settings_choose_coach_sub`; `MaterialButtonToggleGroup` `toggle_coach_style` (children `btn_coach_vadym`/`btn_coach_ivan`/`btn_coach_andriy`, `TTC.Segment.Button`, singleSelection); selected-coach `TTC.Card.GoldTint` `layout_coach_info` with `tv_coach_avatar`(gold circle initial), `tv_coach_name`, `tv_coach_style`(gold tagline), `tv_coach_desc`.
- **Language:** two rows split by divider: `settings_interface_language`+sub + `MaterialButtonToggleGroup` `toggle_interface_lang` (`btn_iface_en`/`btn_iface_uk`, text `lang_en`/`lang_uk`); `settings_coach_language`+sub + `toggle_coach_lang` (`btn_coach_lang_en`/`btn_coach_lang_uk`).
- **Feedback & detection:** two link-rows. Link-row = `MaterialCardView` `TTC.Card` (`android:clickable="true"`, `foreground=?attr/selectableItemBackground`) containing horizontal LinearLayout: 38dp icon tile (`bg_link_icon_tile`, gold `ImageView`), title+sub vertical block (weight 1), trailing chevron `ImageView` (`ic_chevron_right` tinted `ttc_text_faint`). ids: card `card_feedback` (icon `ic_sliders`, `settings_link_feedback`/`_sub`), card `card_detection` (icon `ic_detection` or `ic_target`, `settings_link_detection`/`_sub`).
- **Camera:** three blocks split by dividers: `settings_video_quality`+sub + exposed dropdown `auto_complete_video_quality` (reuse existing `TextInputLayout`+`AutoCompleteTextView` pattern from current layout); `settings_frame_rate`+sub + `MaterialButtonToggleGroup` `toggle_fps` (`btn_fps_24/30/60`); `settings_show_skeleton`+sub + `SwitchMaterial` `switch_pose_skeleton` (`TTC.Toggle`).
- **Tip:** `TTC.Card.GoldTint`: lightbulb icon + `settings_tip` (gold eyebrow) + `settings_tip_body` (`TextAppearance.TTC.Body` tinted `ttc_text_2`).

Drop from the old layout (folded elsewhere): audio switch, volume seekbar, feedback-frequency dropdown, corrections chip group, distance-mode switch, ball-fps toggle group.

- [ ] **Step 2: Rebuild SettingsFragment.kt**

Keep the existing coach-selection + camera persistence logic (reuse the code paths already in the file for `toggle_coach_style`, `auto_complete_video_quality`, `toggle_fps`, `switch_pose_skeleton`, and the coach-info card update). Remove handlers for the dropped controls. Add:
```kotlin
binding.cardFeedback.setOnClickListener {
    findNavController().navigate(R.id.navigation_feedback)
}
binding.cardDetection.setOnClickListener {
    findNavController().navigate(R.id.navigation_detection)
}
// interface language
val sm = SettingsManager(requireContext())
binding.toggleInterfaceLang.check(
    if (sm.interfaceLanguageIsUk()) R.id.btn_iface_uk else R.id.btn_iface_en)
binding.toggleInterfaceLang.addOnButtonCheckedListener { _, id, checked ->
    if (checked) sm.setInterfaceLanguage(if (id == R.id.btn_iface_uk) "uk" else "en")
}
// coach language
binding.toggleCoachLang.check(
    if (sm.coachLanguage == "uk") R.id.btn_coach_lang_uk else R.id.btn_coach_lang_en)
binding.toggleCoachLang.addOnButtonCheckedListener { _, id, checked ->
    if (checked) sm.coachLanguage = if (id == R.id.btn_coach_lang_uk) "uk" else "en"
}
```
Interface language reuses existing `app_language` key: if `SettingsManager` lacks helpers, add `fun interfaceLanguageIsUk() = (prefs.getString("app_language","") == "uk")` and `fun setInterfaceLanguage(v:String){ prefs.edit().putString("app_language",v).apply() }` (store only; no recreate).

- [ ] **Step 3: Keep Settings tab highlighted on child destinations**

In `MainActivity.onCreate`, after `binding.navView.setupWithNavController(navController)`, add a destination listener so pushed Feedback/Detection keep the Settings item selected:
```kotlin
navController.addOnDestinationChangedListener { _, destination, _ ->
    when (destination.id) {
        R.id.navigation_feedback, R.id.navigation_detection ->
            binding.navView.menu.findItem(R.id.navigation_settings)?.isChecked = true
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/fragment_settings.xml \
  app/src/main/java/com/ttcoachai/fragment/SettingsFragment.kt \
  app/src/main/java/com/ttcoachai/MainActivity.kt
git commit -m "feat(settings): rebuild 8a — AI coach, language, feedback/detection links, camera

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Manual verification pass

**Files:** none (verification only).

- [ ] **Step 1: Full build + unit tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 2: Install + screenshot the three screens**

Run: `./gradlew :app:installDebug` then launch the app. Navigate Settings → Feedback → back → Detection. Capture screenshots to `tmp/screenshots/` (project root, per convention):
```bash
adb exec-out screencap -p > tmp/screenshots/settings_8a.png
adb exec-out screencap -p > tmp/screenshots/feedback_11a.png
adb exec-out screencap -p > tmp/screenshots/detection_11b.png
```

- [ ] **Step 3: Verify against spec**

Confirm: Settings matches `8a` (AI Coach / Language / two link-rows / Camera / Tip); link-rows push with back arrow; Settings tab stays highlighted on both child screens; controls show real defaults (Peak speed 1.0, Min interval 500, Significance 7°, Zone width ×1.4, Reminder 10.0s, Praise ON); change a value, leave and return, kill+relaunch → value persists. Note any mismatch and fix before marking the plan complete.

---

## Self-Review

**Spec coverage:** §1 goal → Tasks 4/5/6. §2 decisions: persist-to-prefs → Task 2; dropped-controls folded → Detection(ball-fps,distance) Task 4 + Feedback(corrections,cues/session,volume,voice) Task 5; reusable components → Tasks 1/3 (+ documented reuse of existing segmented/switch/slider/chip); real defaults → Task 2. §3 nav → Tasks 4/5 destinations + Task 6 links + Task 6 tab-highlight. §4 design-system → Task 1. §5 screen content → Tasks 4/5/6 (all rows enumerated). §6 persistence/keys → Task 2 (reuses existing keys, new keys listed). §7 defaults table → Task 2 values. §8 testing → Task 7. §9 risks: key inventory done up front (Global Constraints), tab-highlight handled (Task 6 Step 3), SharedPreferences-not-Room (Task 2).

**Placeholder scan:** no TBD/TODO; all code shown; row enumerations complete with ids/strings/attrs.

**Type consistency:** `SettingsManager` accessor names identical across Tasks 2/4/5/6; nav ids `navigation_feedback`/`navigation_detection` consistent Tasks 4/5/6; `StepperRange`/`TtcStepperView` API consistent Tasks 3/4; button ids (`btn_hand_right`, `btn_cues_3/5/10`, `btn_ball_fps_*`, `btn_coach_*`, `btn_fps_*`) consistent between layout and fragment steps.

**Refinements vs spec (intentional, DRY):** no separate `TtcSegmentedToggle` custom view — reuse existing `MaterialButtonToggleGroup`+`TTC.Segment.Button`; only new token is `ttc_text_disabled` (rest already exist in `values-night`). Both covered by spec §4's "reuse where matches."
