# Gold-Dark Design System — Slice 1 (Foundation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the gold-on-dark (+ light) Material 3 design-system foundation in the Android `app/` — colors, bundled fonts, typography, shape, universal component styles, and a debug preview harness — so later slices can restyle screens against a single source of truth.

**Architecture:** Hybrid theming — remap Material 3 role colors (so stock widgets theme themselves) via the existing single `AppTheme` (`Theme.Material3.DayNight`), plus a bespoke `ttc_*` token + `TTC.*` style layer for where the design diverges from M3 semantics. Dark tokens live in `values-night/colors.xml`, light in `values/colors.xml`; day-night follows automatically. Legacy color names are left intact so existing screens keep building (migrated in Slice 2).

**Tech Stack:** Android XML resources, Material Components 1.12.0, Kotlin (one debug Activity), Gradle (JBR 21 auto-selected via `~/.gradle/gradle.properties`).

## Global Constraints

- Spec: [docs/superpowers/specs/2026-07-01-android-gold-dark-foundation-design.md](../specs/2026-07-01-android-gold-dark-foundation-design.md). Token reference: [docs/design/design-tokens-source.md](../../design/design-tokens-source.md).
- Freeze discipline: touch ONLY presentation resources + the one new debug activity. Do not modify pose/ball/trajectory pipeline code.
- Do NOT delete or edit existing legacy `@color/*` names (blue/amber/badge/phase/etc.) — only add new tokens and redefine the M3 role colors' values.
- `minSdk 24` — reference specific weighted font files directly in TextAppearances (not `textFontWeight`, which is API 28+).
- Bundled fonts only (no downloadable-fonts provider). Files already staged at `/private/tmp/claude-501/-Users-itsurkan-Dev-personal-TT-Coach/7b56c737-8f57-451c-b4cd-518b70266a8f/scratchpad/fonts/`.
- `ttc_gold_bright` = `#E9C46A` in BOTH themes (filled elements). `ttc_gold_accent` = `#E9C46A` dark / `#9A6F0F` light (accent text/icons). `ttc_on_gold` = `#2A2008` both.
- Build check command: `./gradlew :app:assembleDebug` (from repo root). Applicationid/namespace: `com.ttcoachai`.
- Commit after each task with explicit paths (never `git add -A`). End messages with the Co-Authored-By trailer:
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: Bundle fonts + font-family resources

**Files:**
- Create: `app/src/main/res/font/inter_tight_regular.ttf`, `inter_tight_medium.ttf`, `inter_tight_semibold.ttf`, `inter_tight_bold.ttf`, `jetbrains_mono_regular.ttf`, `jetbrains_mono_medium.ttf`, `jetbrains_mono_semibold.ttf` (copied from the staged scratchpad dir)
- Create: `app/src/main/res/font/inter_tight.xml`, `app/src/main/res/font/jetbrains_mono.xml`
- Create: `app/src/main/assets/fonts/OFL-InterTight.txt`, `app/src/main/assets/fonts/OFL-JetBrainsMono.txt` (license attribution ships in APK)

**Interfaces:**
- Produces: `@font/inter_tight` (weighted family 400/500/700), `@font/jetbrains_mono` (weighted family 400/500/600), and per-weight files `@font/inter_tight_{regular,medium,semibold,bold}`, `@font/jetbrains_mono_{regular,medium,semibold}`.

- [ ] **Step 1: Copy the staged font + license files into the project**

```bash
SRC="/private/tmp/claude-501/-Users-itsurkan-Dev-personal-TT-Coach/7b56c737-8f57-451c-b4cd-518b70266a8f/scratchpad/fonts"
DST="app/src/main/res/font"
mkdir -p "$DST" app/src/main/assets/fonts
cp "$SRC"/inter_tight_regular.ttf "$SRC"/inter_tight_medium.ttf "$SRC"/inter_tight_semibold.ttf "$SRC"/inter_tight_bold.ttf "$DST"/
cp "$SRC"/jetbrains_mono_regular.ttf "$SRC"/jetbrains_mono_medium.ttf "$SRC"/jetbrains_mono_semibold.ttf "$DST"/
cp "$SRC"/InterTight-OFL.txt app/src/main/assets/fonts/OFL-InterTight.txt
cp "$SRC"/JetBrainsMono-OFL.txt app/src/main/assets/fonts/OFL-JetBrainsMono.txt
ls -1 "$DST"
```
Expected: the 7 `.ttf` files listed. (If the scratchpad dir is gone, re-stage per the spec: download JetBrains Mono v2.304 zip + google/fonts Inter Tight variable ttf, `fonttools` instancer for the 4 Inter Tight weights.)

- [ ] **Step 2: Create `app/src/main/res/font/inter_tight.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <font android:fontStyle="normal" android:fontWeight="400" android:font="@font/inter_tight_regular"
        app:fontStyle="normal" app:fontWeight="400" app:font="@font/inter_tight_regular" />
    <font android:fontStyle="normal" android:fontWeight="500" android:font="@font/inter_tight_medium"
        app:fontStyle="normal" app:fontWeight="500" app:font="@font/inter_tight_medium" />
    <font android:fontStyle="normal" android:fontWeight="600" android:font="@font/inter_tight_semibold"
        app:fontStyle="normal" app:fontWeight="600" app:font="@font/inter_tight_semibold" />
    <font android:fontStyle="normal" android:fontWeight="700" android:font="@font/inter_tight_bold"
        app:fontStyle="normal" app:fontWeight="700" app:font="@font/inter_tight_bold" />
</font-family>
```

- [ ] **Step 3: Create `app/src/main/res/font/jetbrains_mono.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <font android:fontStyle="normal" android:fontWeight="400" android:font="@font/jetbrains_mono_regular"
        app:fontStyle="normal" app:fontWeight="400" app:font="@font/jetbrains_mono_regular" />
    <font android:fontStyle="normal" android:fontWeight="500" android:font="@font/jetbrains_mono_medium"
        app:fontStyle="normal" app:fontWeight="500" app:font="@font/jetbrains_mono_medium" />
    <font android:fontStyle="normal" android:fontWeight="600" android:font="@font/jetbrains_mono_semibold"
        app:fontStyle="normal" app:fontWeight="600" app:font="@font/jetbrains_mono_semibold" />
</font-family>
```

- [ ] **Step 4: Build to verify font resources compile**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Font resource names validate at `mergeDebugResources`/`processDebugResources`.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/font app/src/main/assets/fonts
git commit -m "feat(app-theme): bundle Inter Tight + JetBrains Mono fonts

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Color tokens + M3 role remap (dark + light)

**Files:**
- Modify: `app/src/main/res/values/colors.xml` (light: add `ttc_*` tokens, redefine role colors to reference tokens)
- Modify: `app/src/main/res/values-night/colors.xml` (dark: same)
- Create: `app/src/main/res/color/ttc_switch_track.xml`, `app/src/main/res/color/ttc_switch_thumb.xml`

**Interfaces:**
- Produces: `@color/ttc_canvas`, `ttc_sink`, `ttc_surface`, `ttc_surface_elevated`, `ttc_outline`, `ttc_outline_strong`, `ttc_gold_bright`, `ttc_gold_accent`, `ttc_gold_deep`, `ttc_gold_container`, `ttc_gold_container_outline`, `ttc_on_gold`, `ttc_toggle_knob`, `ttc_text_1`, `ttc_text_2`, `ttc_text_3`, `ttc_text_faint`, `ttc_text_on_gold_card`, `ttc_success`, `ttc_success_soft`, `ttc_error`, `ttc_error_container`, `ttc_error_container_outline`, `ttc_amber`, `ttc_cream`; selectors `@color/ttc_switch_track`, `@color/ttc_switch_thumb`.
- Consumes: nothing.

- [ ] **Step 1: Add the `ttc_*` DARK tokens to `values-night/colors.xml`** (insert before the closing `</resources>`)

```xml
    <!-- ===== TTC gold-dark design system (Slice 1) — DARK ===== -->
    <color name="ttc_canvas">#0E1115</color>
    <color name="ttc_sink">#0B0E12</color>
    <color name="ttc_surface">#141820</color>
    <color name="ttc_surface_elevated">#1A1F28</color>
    <color name="ttc_outline">#232833</color>
    <color name="ttc_outline_strong">#2F3542</color>
    <color name="ttc_gold_bright">#E9C46A</color>
    <color name="ttc_gold_accent">#E9C46A</color>
    <color name="ttc_gold_deep">#A9862F</color>
    <color name="ttc_gold_container">#221C0F</color>
    <color name="ttc_gold_container_outline">#5C4A22</color>
    <color name="ttc_on_gold">#2A2008</color>
    <color name="ttc_toggle_knob">#16130B</color>
    <color name="ttc_text_1">#E7EAF0</color>
    <color name="ttc_text_2">#B4BAC5</color>
    <color name="ttc_text_3">#7F8694</color>
    <color name="ttc_text_faint">#565C68</color>
    <color name="ttc_text_on_gold_card">#D9CFB4</color>
    <color name="ttc_success">#2FD08A</color>
    <color name="ttc_success_soft">#9BE3A6</color>
    <color name="ttc_error">#E8817A</color>
    <color name="ttc_error_container">#3A1A18</color>
    <color name="ttc_error_container_outline">#5A2B27</color>
    <color name="ttc_amber">#E9A25E</color>
    <color name="ttc_cream">#FBF3DE</color>
```

- [ ] **Step 2: Add the `ttc_*` LIGHT tokens to `values/colors.xml`** (insert before the closing `</resources>`)

```xml
    <!-- ===== TTC gold-dark design system (Slice 1) — LIGHT ===== -->
    <color name="ttc_canvas">#FFFFFF</color>
    <color name="ttc_sink">#EFF1F4</color>
    <color name="ttc_surface">#F7F8FA</color>
    <color name="ttc_surface_elevated">#EFF1F4</color>
    <color name="ttc_outline">#E4E6EC</color>
    <color name="ttc_outline_strong">#D8DBE2</color>
    <color name="ttc_gold_bright">#E9C46A</color>
    <color name="ttc_gold_accent">#9A6F0F</color>
    <color name="ttc_gold_deep">#997219</color>
    <color name="ttc_gold_container">#FBF3DE</color>
    <color name="ttc_gold_container_outline">#ECDBAC</color>
    <color name="ttc_on_gold">#2A2008</color>
    <color name="ttc_toggle_knob">#2A2008</color>
    <color name="ttc_text_1">#1A1D23</color>
    <color name="ttc_text_2">#545B68</color>
    <color name="ttc_text_3">#868D99</color>
    <color name="ttc_text_faint">#A0A5AF</color>
    <color name="ttc_text_on_gold_card">#6E5E38</color>
    <color name="ttc_success">#12A05F</color>
    <color name="ttc_success_soft">#3BAF6B</color>
    <color name="ttc_error">#C4463C</color>
    <color name="ttc_error_container">#FDECEA</color>
    <color name="ttc_error_container_outline">#F5C9C4</color>
    <color name="ttc_amber">#E9A25E</color>
    <color name="ttc_cream">#FBF4E1</color>
```

- [ ] **Step 3: Redefine the M3 role colors to reference tokens — in BOTH files**

In `values-night/colors.xml`, replace the existing role color lines (the `colorPrimary`…`colorOnErrorContainer`, `colorSurface`…`colorOutlineVariant`, and the `surfaceContainer*` block at the top) with these (delete the old hex definitions of the SAME names; keep everything else):

```xml
    <color name="colorPrimary">@color/ttc_gold_accent</color>
    <color name="colorOnPrimary">@color/ttc_on_gold</color>
    <color name="colorPrimaryContainer">@color/ttc_gold_container</color>
    <color name="colorOnPrimaryContainer">@color/ttc_gold_accent</color>
    <color name="colorPrimaryInverse">@color/ttc_gold_deep</color>
    <color name="colorSecondary">@color/ttc_text_2</color>
    <color name="colorOnSecondary">@color/ttc_canvas</color>
    <color name="colorSecondaryContainer">@color/ttc_surface_elevated</color>
    <color name="colorOnSecondaryContainer">@color/ttc_text_1</color>
    <color name="colorTertiary">@color/ttc_amber</color>
    <color name="colorOnTertiary">@color/ttc_on_gold</color>
    <color name="colorTertiaryContainer">@color/ttc_gold_container</color>
    <color name="colorOnTertiaryContainer">@color/ttc_amber</color>
    <color name="colorError">@color/ttc_error</color>
    <color name="colorOnError">#43110D</color>
    <color name="colorErrorContainer">@color/ttc_error_container</color>
    <color name="colorOnErrorContainer">@color/ttc_error</color>
    <color name="colorSurface">@color/ttc_surface</color>
    <color name="colorOnSurface">@color/ttc_text_1</color>
    <color name="colorSurfaceVariant">@color/ttc_surface_elevated</color>
    <color name="colorOnSurfaceVariant">@color/ttc_text_2</color>
    <color name="colorSurfaceInverse">@color/ttc_text_1</color>
    <color name="colorOnSurfaceInverse">@color/ttc_canvas</color>
    <color name="colorOutline">@color/ttc_outline</color>
    <color name="colorOutlineVariant">@color/ttc_outline_strong</color>
    <color name="colorSurfaceTint">@color/ttc_gold_accent</color>
    <color name="surfaceBright">@color/ttc_surface_elevated</color>
    <color name="surfaceDim">@color/ttc_canvas</color>
    <color name="surfaceContainerLowest">@color/ttc_sink</color>
    <color name="surfaceContainerLow">@color/ttc_surface</color>
    <color name="surfaceContainer">@color/ttc_surface_elevated</color>
    <color name="surfaceContainerHigh">@color/ttc_outline</color>
    <color name="surfaceContainerHighest">@color/ttc_outline_strong</color>
```

In `values/colors.xml`, replace the SAME role color names with the identical token-referencing lines EXCEPT the two on-error literals which differ for light:

```xml
    <color name="colorOnError">#FFFFFF</color>
    <color name="colorOnErrorContainer">#7A1B14</color>
```
(all other role lines are identical to the night file above — they resolve per theme through the tokens).

- [ ] **Step 4: Create `app/src/main/res/color/ttc_switch_track.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/ttc_gold_bright" android:state_checked="true" />
    <item android:color="@color/ttc_outline_strong" />
</selector>
```

- [ ] **Step 5: Create `app/src/main/res/color/ttc_switch_thumb.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/ttc_toggle_knob" android:state_checked="true" />
    <item android:color="@color/ttc_text_3" />
</selector>
```

- [ ] **Step 6: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Then confirm tokens present: `grep -c "ttc_gold_bright" app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml` → `1` each.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml app/src/main/res/color
git commit -m "feat(app-theme): gold-dark color tokens + M3 role remap (dark+light)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Shape appearances + drawables

**Files:**
- Create: `app/src/main/res/values/shapes.xml`
- Create: `app/src/main/res/drawable/bg_pill_track.xml`, `bg_pill_gold_container.xml`, `ic_trend_up.xml`, `ic_trend_down.xml`

**Interfaces:**
- Produces: `@style/ShapeAppearance.TTC.Small` (11dp), `.Medium` (14dp), `.Large` (16dp), `.Pill` (999dp), `.Inset` (12dp), `.Tile` (10dp); drawables `@drawable/bg_pill_track`, `@drawable/bg_pill_gold_container`, `@drawable/ic_trend_up`, `@drawable/ic_trend_down`.
- Consumes: `@color/ttc_*` (Task 2).

- [ ] **Step 1: Create `app/src/main/res/values/shapes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="ShapeAppearance.TTC.Small" parent="ShapeAppearance.Material3.SmallComponent">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">11dp</item>
    </style>
    <style name="ShapeAppearance.TTC.Medium" parent="ShapeAppearance.Material3.MediumComponent">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">14dp</item>
    </style>
    <style name="ShapeAppearance.TTC.Large" parent="ShapeAppearance.Material3.LargeComponent">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">16dp</item>
    </style>
    <style name="ShapeAppearance.TTC.Pill" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">999dp</item>
    </style>
    <style name="ShapeAppearance.TTC.Inset" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">12dp</item>
    </style>
    <style name="ShapeAppearance.TTC.Tile" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">10dp</item>
    </style>
</resources>
```

- [ ] **Step 2: Create `app/src/main/res/drawable/bg_pill_track.xml`** (segmented-control / slider-like track background)

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/ttc_sink" />
    <stroke android:width="1dp" android:color="@color/ttc_outline" />
    <corners android:radius="999dp" />
</shape>
```

- [ ] **Step 3: Create `app/src/main/res/drawable/bg_pill_gold_container.xml`** (active pill / chip background)

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/ttc_gold_container" />
    <stroke android:width="1dp" android:color="@color/ttc_gold_container_outline" />
    <corners android:radius="999dp" />
</shape>
```

- [ ] **Step 4: Create `app/src/main/res/drawable/ic_trend_up.xml`** (white triangle; tinted at use site)

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="10dp" android:height="10dp" android:viewportWidth="10" android:viewportHeight="10">
    <path android:fillColor="#FFFFFF" android:pathData="M5,1.5 L9,8 L1,8 Z" />
</vector>
```

- [ ] **Step 5: Create `app/src/main/res/drawable/ic_trend_down.xml`**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="10dp" android:height="10dp" android:viewportWidth="10" android:viewportHeight="10">
    <path android:fillColor="#FFFFFF" android:pathData="M5,8.5 L1,2 L9,2 Z" />
</vector>
```

- [ ] **Step 6: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/shapes.xml app/src/main/res/drawable/bg_pill_track.xml app/src/main/res/drawable/bg_pill_gold_container.xml app/src/main/res/drawable/ic_trend_up.xml app/src/main/res/drawable/ic_trend_down.xml
git commit -m "feat(app-theme): shape appearances + pill/trend drawables

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Typography appearances + theme wiring

**Files:**
- Modify: `app/src/main/res/values/styles.xml` (add `TextAppearance.TTC.*`; wire `AppTheme` fontFamily + shape defaults)

**Interfaces:**
- Produces: `@style/TextAppearance.TTC.Stat.Hero/Large/Medium/Small`, `.Mono.Meta`, `.Title.Screen/Card`, `.Body`, `.Body.Secondary`, `.Eyebrow`, `.Nav.Label`. `AppTheme` gains default Inter Tight fontFamily + `shapeAppearance*Component` → `ShapeAppearance.TTC.*`.
- Consumes: `@font/*` (Task 1), `@color/ttc_*` (Task 2), `@style/ShapeAppearance.TTC.*` (Task 3).

- [ ] **Step 1: Add `TextAppearance.TTC.*` styles to `styles.xml`** (inside `<resources>`, after `AppTheme`)

```xml
    <!-- ===== TTC typography (Slice 1) ===== -->
    <style name="TextAppearance.TTC.Stat.Hero" parent="TextAppearance.Material3.HeadlineMedium">
        <item name="android:fontFamily">@font/jetbrains_mono_semibold</item>
        <item name="fontFamily">@font/jetbrains_mono_semibold</item>
        <item name="android:textSize">28sp</item>
        <item name="android:textColor">@color/ttc_text_1</item>
    </style>
    <style name="TextAppearance.TTC.Stat.Large" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:fontFamily">@font/jetbrains_mono_semibold</item>
        <item name="fontFamily">@font/jetbrains_mono_semibold</item>
        <item name="android:textSize">22sp</item>
        <item name="android:textColor">@color/ttc_text_1</item>
    </style>
    <style name="TextAppearance.TTC.Stat.Medium" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:fontFamily">@font/jetbrains_mono_semibold</item>
        <item name="fontFamily">@font/jetbrains_mono_semibold</item>
        <item name="android:textSize">20sp</item>
        <item name="android:textColor">@color/ttc_text_1</item>
    </style>
    <style name="TextAppearance.TTC.Stat.Small" parent="TextAppearance.Material3.TitleMedium">
        <item name="android:fontFamily">@font/jetbrains_mono_semibold</item>
        <item name="fontFamily">@font/jetbrains_mono_semibold</item>
        <item name="android:textSize">15sp</item>
        <item name="android:textColor">@color/ttc_text_1</item>
    </style>
    <style name="TextAppearance.TTC.Mono.Meta" parent="TextAppearance.Material3.LabelMedium">
        <item name="android:fontFamily">@font/jetbrains_mono_medium</item>
        <item name="fontFamily">@font/jetbrains_mono_medium</item>
        <item name="android:textSize">11sp</item>
        <item name="android:textColor">@color/ttc_text_3</item>
    </style>
    <style name="TextAppearance.TTC.Title.Screen" parent="TextAppearance.Material3.HeadlineSmall">
        <item name="android:fontFamily">@font/inter_tight_bold</item>
        <item name="fontFamily">@font/inter_tight_bold</item>
        <item name="android:textSize">22sp</item>
        <item name="android:letterSpacing">-0.01</item>
        <item name="android:textColor">@color/ttc_text_1</item>
    </style>
    <style name="TextAppearance.TTC.Title.Card" parent="TextAppearance.Material3.TitleMedium">
        <item name="android:fontFamily">@font/inter_tight_bold</item>
        <item name="fontFamily">@font/inter_tight_bold</item>
        <item name="android:textSize">16sp</item>
        <item name="android:textColor">@color/ttc_text_1</item>
    </style>
    <style name="TextAppearance.TTC.Body" parent="TextAppearance.Material3.BodyMedium">
        <item name="android:fontFamily">@font/inter_tight_regular</item>
        <item name="fontFamily">@font/inter_tight_regular</item>
        <item name="android:textSize">13sp</item>
        <item name="android:textColor">@color/ttc_text_2</item>
    </style>
    <style name="TextAppearance.TTC.Body.Secondary" parent="TextAppearance.TTC.Body">
        <item name="android:textSize">12sp</item>
        <item name="android:textColor">@color/ttc_text_3</item>
    </style>
    <style name="TextAppearance.TTC.Eyebrow" parent="TextAppearance.Material3.LabelSmall">
        <item name="android:fontFamily">@font/inter_tight_bold</item>
        <item name="fontFamily">@font/inter_tight_bold</item>
        <item name="android:textSize">10sp</item>
        <item name="android:letterSpacing">0.11</item>
        <item name="android:textAllCaps">true</item>
        <item name="android:textColor">@color/ttc_text_3</item>
    </style>
    <style name="TextAppearance.TTC.Nav.Label" parent="TextAppearance.Material3.LabelSmall">
        <item name="android:fontFamily">@font/inter_tight_medium</item>
        <item name="fontFamily">@font/inter_tight_medium</item>
        <item name="android:textSize">10sp</item>
        <item name="android:textColor">@color/ttc_text_3</item>
    </style>
```

- [ ] **Step 2: Wire default font + shape into `AppTheme`** — add these items inside the existing `<style name="AppTheme" …>` block (after the typography `textAppearance*` lines, before `</style>`)

```xml
        <!-- TTC foundation: default font + shape (Slice 1) -->
        <item name="android:fontFamily">@font/inter_tight</item>
        <item name="fontFamily">@font/inter_tight</item>
        <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.TTC.Small</item>
        <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.TTC.Medium</item>
        <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.TTC.Large</item>
```

Then CHANGE the value of the existing `android:statusBarColor` line already in `AppTheme` (do not add a second one — a duplicate item is invalid):

```xml
        <item name="android:statusBarColor">?android:attr/colorBackground</item>
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/styles.xml
git commit -m "feat(app-theme): TTC typography appearances + font/shape theme wiring

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Universal component styles

**Files:**
- Modify: `app/src/main/res/values/styles.xml` (add `TTC.*` component styles)

**Interfaces:**
- Produces: `@style/TTC.Card`, `TTC.SegmentedTrack`, `TTC.Segment.Inactive`, `TTC.Segment.Active`, `TTC.Button.Primary`, `TTC.Button.Ghost`, `TTC.Fab.Extended`, `TTC.SectionHeader`, `TTC.StatNumber`, `TTC.StatNumber.Gold`, `TTC.StatNumber.Positive`, `TTC.TrendChip.Positive`, `TTC.TrendChip.Negative`, `TTC.Toggle`, `TTC.Slider`.
- Consumes: colors (Task 2), shapes + drawables (Task 3), text appearances (Task 4).

- [ ] **Step 1: Add the `TTC.*` component styles to `styles.xml`** (inside `<resources>`, after the TextAppearance block)

```xml
    <!-- ===== TTC components (Slice 1) ===== -->
    <style name="TTC.Card" parent="Widget.Material3.CardView.Filled">
        <item name="cardBackgroundColor">@color/ttc_surface</item>
        <item name="strokeColor">@color/ttc_outline</item>
        <item name="strokeWidth">1dp</item>
        <item name="cardElevation">0dp</item>
        <item name="shapeAppearance">@style/ShapeAppearance.TTC.Medium</item>
        <item name="contentPadding">16dp</item>
    </style>

    <style name="TTC.SegmentedTrack">
        <item name="android:background">@drawable/bg_pill_track</item>
        <item name="android:padding">4dp</item>
        <item name="android:orientation">horizontal</item>
    </style>
    <style name="TTC.Segment.Inactive">
        <item name="android:textAppearance">@style/TextAppearance.TTC.Body</item>
        <item name="android:textColor">@color/ttc_text_2</item>
        <item name="android:fontFamily">@font/inter_tight_semibold</item>
        <item name="android:textSize">13sp</item>
        <item name="android:gravity">center</item>
        <item name="android:paddingTop">8dp</item>
        <item name="android:paddingBottom">8dp</item>
        <item name="android:background">@android:color/transparent</item>
    </style>
    <style name="TTC.Segment.Active" parent="TTC.Segment.Inactive">
        <item name="android:textColor">@color/ttc_gold_accent</item>
        <item name="android:fontFamily">@font/inter_tight_bold</item>
        <item name="android:background">@drawable/bg_pill_gold_container</item>
    </style>

    <style name="TTC.Button.Primary" parent="Widget.Material3.Button">
        <item name="backgroundTint">@color/ttc_gold_bright</item>
        <item name="android:textColor">@color/ttc_on_gold</item>
        <item name="iconTint">@color/ttc_on_gold</item>
        <item name="android:fontFamily">@font/inter_tight_bold</item>
        <item name="android:textSize">14sp</item>
        <item name="android:textAllCaps">false</item>
        <item name="shapeAppearance">@style/ShapeAppearance.TTC.Small</item>
    </style>
    <style name="TTC.Button.Ghost" parent="Widget.Material3.Button.OutlinedButton">
        <item name="android:textColor">@color/ttc_text_2</item>
        <item name="strokeColor">@color/ttc_outline</item>
        <item name="iconTint">@color/ttc_text_2</item>
        <item name="android:fontFamily">@font/inter_tight_semibold</item>
        <item name="android:textSize">14sp</item>
        <item name="android:textAllCaps">false</item>
        <item name="shapeAppearance">@style/ShapeAppearance.TTC.Small</item>
    </style>
    <style name="TTC.Fab.Extended" parent="Widget.Material3.ExtendedFloatingActionButton.Primary">
        <item name="backgroundTint">@color/ttc_gold_bright</item>
        <item name="android:textColor">@color/ttc_on_gold</item>
        <item name="iconTint">@color/ttc_on_gold</item>
        <item name="android:fontFamily">@font/inter_tight_bold</item>
        <item name="shapeAppearance">@style/ShapeAppearance.TTC.Large</item>
    </style>

    <style name="TTC.SectionHeader" parent="Widget.AppCompat.TextView">
        <item name="android:textAppearance">@style/TextAppearance.TTC.Eyebrow</item>
    </style>
    <style name="TTC.StatNumber" parent="Widget.AppCompat.TextView">
        <item name="android:textAppearance">@style/TextAppearance.TTC.Stat.Medium</item>
    </style>
    <style name="TTC.StatNumber.Gold">
        <item name="android:textColor">@color/ttc_gold_accent</item>
    </style>
    <style name="TTC.StatNumber.Positive">
        <item name="android:textColor">@color/ttc_success</item>
    </style>

    <style name="TTC.TrendChip.Positive" parent="Widget.AppCompat.TextView">
        <item name="android:textAppearance">@style/TextAppearance.TTC.Mono.Meta</item>
        <item name="android:textColor">@color/ttc_success</item>
        <item name="android:drawableStart">@drawable/ic_trend_up</item>
        <item name="android:drawableTint">@color/ttc_success</item>
        <item name="android:drawablePadding">3dp</item>
        <item name="android:gravity">center_vertical</item>
    </style>
    <style name="TTC.TrendChip.Negative" parent="TTC.TrendChip.Positive">
        <item name="android:textColor">@color/ttc_error</item>
        <item name="android:drawableStart">@drawable/ic_trend_down</item>
        <item name="android:drawableTint">@color/ttc_error</item>
    </style>

    <style name="TTC.Toggle" parent="Widget.Material3.CompoundButton.MaterialSwitch">
        <item name="trackTint">@color/ttc_switch_track</item>
        <item name="thumbTint">@color/ttc_switch_thumb</item>
    </style>
    <style name="TTC.Slider" parent="Widget.Material3.Slider">
        <item name="trackColorActive">@color/ttc_gold_bright</item>
        <item name="trackColorInactive">@color/ttc_sink</item>
        <item name="thumbColor">@color/ttc_gold_bright</item>
        <item name="haloColor">@color/ttc_gold_container_outline</item>
    </style>
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/styles.xml
git commit -m "feat(app-theme): TTC universal component styles

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Design-system preview harness (verification artifact)

**Files:**
- Create: `app/src/main/java/com/ttcoachai/debug/DesignSystemPreviewActivity.kt`
- Create: `app/src/main/res/layout/activity_design_system_preview.xml`
- Modify: `app/src/main/AndroidManifest.xml` (register activity, exported, debug-gated)

**Interfaces:**
- Consumes: all `ttc_*` colors, `TextAppearance.TTC.*`, `TTC.*` component styles, drawables.
- Produces: launchable `com.ttcoachai/.debug.DesignSystemPreviewActivity`.

- [ ] **Step 1: Create `app/src/main/res/layout/activity_design_system_preview.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="?android:attr/colorBackground"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:padding="16dp">

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.TTC.Title.Screen"
            android:text="Design System · Slice 1" />

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginTop="18dp"
            android:textAppearance="@style/TextAppearance.TTC.Eyebrow" android:text="Color tokens" />
        <LinearLayout android:id="@+id/swatchContainer"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="vertical" android:layout_marginTop="8dp" />

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginTop="22dp"
            android:textAppearance="@style/TextAppearance.TTC.Eyebrow" android:text="Typography" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="8dp"
            android:textAppearance="@style/TextAppearance.TTC.Stat.Hero" android:text="68" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.TTC.Stat.Large" android:text="12:04.7  ·  1234" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.TTC.Title.Screen" android:text="Screen title (Inter Tight)" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.TTC.Title.Card" android:text="Card title" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.TTC.Body" android:text="Body text — the quick brown fox." />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.TTC.Body.Secondary" android:text="Secondary caption text." />

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginTop="22dp"
            android:textAppearance="@style/TextAppearance.TTC.Eyebrow" android:text="Components" />

        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="10dp" style="@style/TTC.Card">
            <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                android:orientation="vertical">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:textAppearance="@style/TextAppearance.TTC.Title.Card" android:text="TTC.Card" />
                <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:orientation="horizontal" android:layout_marginTop="10dp" android:gravity="center_vertical">
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                        style="@style/TTC.StatNumber.Gold" android:text="68%" />
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                        android:layout_marginStart="12dp" style="@style/TTC.TrendChip.Positive" android:text="+2" />
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                        android:layout_marginStart="12dp" style="@style/TTC.TrendChip.Negative" android:text="-3" />
                </LinearLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="12dp" style="@style/TTC.SegmentedTrack">
            <TextView android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
                style="@style/TTC.Segment.Active" android:text="Training time" />
            <TextView android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
                style="@style/TTC.Segment.Inactive" android:text="Accuracy" />
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="12dp" style="@style/TTC.Button.Primary" android:text="New session" />
        <com.google.android.material.button.MaterialButton
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="8dp" style="@style/TTC.Button.Ghost" android:text="Secondary" />

        <com.google.android.material.materialswitch.MaterialSwitch
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginTop="14dp" style="@style/TTC.Toggle"
            android:checked="true" android:text="Show pose skeleton"
            android:textAppearance="@style/TextAppearance.TTC.Body" />

        <com.google.android.material.slider.Slider
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="10dp" style="@style/TTC.Slider"
            android:valueFrom="0" android:valueTo="100" android:value="50" />

        <com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginTop="14dp" android:layout_gravity="end"
            style="@style/TTC.Fab.Extended" android:text="New session" />

    </LinearLayout>
</ScrollView>
```

- [ ] **Step 2: Create `app/src/main/java/com/ttcoachai/debug/DesignSystemPreviewActivity.kt`**

```kotlin
package com.ttcoachai.debug

import android.content.pm.ApplicationInfo
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ttcoachai.R

/**
 * Dev-only visual gallery for the TTC gold-dark design system (Slice 1). Renders color
 * swatches, the type ramp, and each TTC.* component so the foundation can be eyeballed in
 * both light and dark. Exported so `adb shell am start` can launch it directly; runtime
 * FLAG_DEBUGGABLE gate keeps it inert on release builds.
 */
class DesignSystemPreviewActivity : AppCompatActivity() {

    private val tokens = listOf(
        "ttc_canvas" to R.color.ttc_canvas,
        "ttc_sink" to R.color.ttc_sink,
        "ttc_surface" to R.color.ttc_surface,
        "ttc_surface_elevated" to R.color.ttc_surface_elevated,
        "ttc_outline" to R.color.ttc_outline,
        "ttc_outline_strong" to R.color.ttc_outline_strong,
        "ttc_gold_bright" to R.color.ttc_gold_bright,
        "ttc_gold_accent" to R.color.ttc_gold_accent,
        "ttc_gold_deep" to R.color.ttc_gold_deep,
        "ttc_gold_container" to R.color.ttc_gold_container,
        "ttc_on_gold" to R.color.ttc_on_gold,
        "ttc_text_1" to R.color.ttc_text_1,
        "ttc_text_2" to R.color.ttc_text_2,
        "ttc_text_3" to R.color.ttc_text_3,
        "ttc_success" to R.color.ttc_success,
        "ttc_success_soft" to R.color.ttc_success_soft,
        "ttc_error" to R.color.ttc_error,
        "ttc_error_container" to R.color.ttc_error_container,
        "ttc_amber" to R.color.ttc_amber,
        "ttc_cream" to R.color.ttc_cream,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }
        setContentView(R.layout.activity_design_system_preview)
        val container = findViewById<LinearLayout>(R.id.swatchContainer)
        tokens.forEach { (name, colorRes) -> container.addView(swatchRow(name, colorRes)) }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun swatchRow(name: String, colorRes: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        val chipBg = GradientDrawable().apply {
            setColor(ContextCompat.getColor(this@DesignSystemPreviewActivity, colorRes))
            cornerRadius = dp(6).toFloat()
            setStroke(dp(1), ContextCompat.getColor(this@DesignSystemPreviewActivity, R.color.ttc_outline_strong))
        }
        val chip = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(24))
            background = chipBg
        }
        val label = TextView(this).apply {
            text = name
            setTextAppearance(R.style.TextAppearance_TTC_Mono_Meta)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(12) }
        }
        row.addView(chip); row.addView(label)
        return row
    }
}
```

- [ ] **Step 3: Register the activity in `app/src/main/AndroidManifest.xml`** — add next to the other debug activities (e.g. after `BaselinePreviewActivity`):

```xml
        <!-- Design System Preview (dev-only, runtime FLAG_DEBUGGABLE gate protects release).
             Exported so `adb shell am start` can launch it directly. -->
        <activity
            android:name=".debug.DesignSystemPreviewActivity"
            android:exported="true"
            android:parentActivityName=".MainActivity" />
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Install + launch + screenshot both themes**

```bash
./gradlew :app:installDebug
adb shell cmd uimode night yes
adb shell am start -n com.ttcoachai/.debug.DesignSystemPreviewActivity
mkdir -p tmp/screenshots
sleep 2 && adb exec-out screencap -p > tmp/screenshots/slice1-dark.png
adb shell cmd uimode night no
adb shell am start -n com.ttcoachai/.debug.DesignSystemPreviewActivity
sleep 2 && adb exec-out screencap -p > tmp/screenshots/slice1-light.png
```
Expected: two screenshots. Dark shows gold `#E9C46A` accents on `#0E1115` canvas; light shows deepened gold `#9A6F0F` text with bright-gold filled button on white. Mono numerals visibly monospaced.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ttcoachai/debug/DesignSystemPreviewActivity.kt app/src/main/res/layout/activity_design_system_preview.xml app/src/main/AndroidManifest.xml
git commit -m "feat(app-theme): design-system preview harness (debug-gated)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Exit gate (main-session verification after all tasks)

1. `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`.
2. `./gradlew :app:testDebugUnitTest` → existing app unit tests still green.
3. Preview screenshots captured in both themes (`tmp/screenshots/slice1-dark.png`, `slice1-light.png`); visually match the token table + component recipes.
4. App still launches to its normal start screen without crash (`adb shell am start -n com.ttcoachai/.MainActivity`).
5. Update CLAUDE.md/roadmap note that Android UI restyle (Slice 1) has begun (housekeeping, can follow).
