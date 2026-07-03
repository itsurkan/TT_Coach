# Android gold-dark design system — Slice 1 (foundation)

Date: 2026-07-01
Status: approved (target + theme scope + fonts confirmed by user via `/goal`)
Source design: claude.ai/design project "Table Tennis Coach AI Redesign" (`Live Session.dc.html`)
Verbatim token reference: [docs/design/design-tokens-source.md](../../design/design-tokens-source.md)

## Context

The user produced a full gold-on-dark "house system" redesign of the whole app (17 phone
screens across 8 areas). The existing Android app (`app/`) is already a complete Material 3
XML application (Activities/Fragments + View Binding, bottom-nav + nav graph, Room + Firestore,
MPAndroidChart) — so this is a **restyle + fill-gaps** program, not greenfield.

Because it is 17 screens, we build it in **slices**, each with its own spec → plan → build:

- **Slice 1 (this spec) — design-system foundation.** Colors, typography + bundled fonts,
  shape, a small set of universal component styles, day-night wiring, and a debug preview
  harness to verify it. Everything else consumes this.
- Slice 2 — restyle existing screens (Home, Progress, Drills, Settings, Profile, History).
- Slice 3 — new screens (Session Review `6b`, Feedback `11a`, Detection `11b`, New/Clone forms `10c`/`10d`).
- Slice 4 — Live Session `1a` (needs the parent design doc).

This departs from the documented "Android frozen / desktop-first" direction; the CLAUDE.md
roadmap should be updated once Slice 1 lands. **Freeze discipline still holds:** this slice
touches only presentation resources (colors/type/shape/styles) + one debug activity. No pose
pipeline, ball, or trajectory logic is modified.

## Goal / success criteria

1. A gold-dark **dark** theme and a light **light** theme, both defined as Material 3 palettes,
   so the app's day-night switch renders the design's dark and light variants.
2. Inter Tight + JetBrains Mono **bundled** as app fonts and wired into the type scale.
3. Shape appearances + a reusable Pill shape matching the design's radii.
4. A small set of named `TTC.*` component styles (card, segmented control, primary button,
   ghost button, section eyebrow, stat number, trend chip) with exact token values.
5. A `DesignSystemPreviewActivity` (debug-gated) that renders swatches, the type ramp, and each
   component in both themes — the verification artifact for this slice.
6. `:app:assembleDebug` builds; existing `:app` unit tests still pass; the app still launches.

## Non-goals (explicitly out of this slice)

- Restyling any real screen or migrating the ~130 legacy hardcoded color names (Slice 2+).
- New screens / Live Session (Slice 3–4).
- iOS. Per-screen light "warm-paper" flavor variants (this slice ships ONE canonical light palette).
- Explicit MPAndroidChart re-theming (Slice 2, where charts live).

## Approach — hybrid (M3 role remap + bespoke `TTC.*` layer)

Keep the single existing `AppTheme` (`Theme.Material3.DayNight.NoActionBar`). Two moves:

1. **Remap the M3 role colors** (which stock Material widgets consume) to the design palette, by
   redefining the existing `@color/color*` values in `values/colors.xml` (light) and
   `values-night/colors.xml` (dark). Day-night then follows automatically.
2. **Add a bespoke `ttc_*` token + `TTC.*` style layer** for where the design diverges from M3
   semantics (single-gold-accent, red-for-errors-only, two greens, amber third status, cream).

Legacy color names stay defined and untouched so all existing screens keep building; they get
migrated in Slice 2.

### The one real M3 tension: gold behaves differently by theme

The design keeps **filled** elements (primary button, active chip, chart bars, FAB) at bright
`#E9C46A` in *both* themes, but deepens accent **text/icons** to `#9A6F0F` in light. M3's single
`colorPrimary` cannot be both. Resolution — two brand tokens:

- `ttc_gold_bright` = `#E9C46A` in **both** themes → filled buttons/chips/FAB/chart bars/progress fill.
- `ttc_gold_accent` = `#E9C46A` (dark) / `#9A6F0F` (light) → accent text, icon strokes, active-nav text.
- `colorPrimary` = `ttc_gold_accent`; `TTC.Button.Primary`/`TTC.Chip.Active` hardcode `ttc_gold_bright`.
- `ttc_on_gold` = `#2A2008` (both) → text/icons on any bright-gold fill.

## Design detail

### 1. Color system

New brand tokens (added to both color files; values below as dark / light):

| Token | Dark | Light | Use |
|---|---|---|---|
| `ttc_canvas` | `#0E1115` | `#FFFFFF` | screen background |
| `ttc_sink` | `#0B0E12` | `#EFF1F4` | deepest: nav bg, slider/progress troughs, segmented track |
| `ttc_surface` | `#141820` | `#F7F8FA` | primary card |
| `ttc_surface_elevated` | `#1A1F28` | `#EFF1F4` | icon tiles, nested surfaces |
| `ttc_outline` | `#232833` | `#E4E6EC` | primary border/divider |
| `ttc_outline_strong` | `#2F3542` | `#D8DBE2` | stronger border (swipe rows, KPI tiles) |
| `ttc_gold_bright` | `#E9C46A` | `#E9C46A` | filled accent (buttons, chips, FAB, bars) |
| `ttc_gold_accent` | `#E9C46A` | `#9A6F0F` | accent text/icons/active-nav |
| `ttc_gold_deep` | `#A9862F` | `#997219` | lower-tier fills, secondary accent |
| `ttc_gold_container` | `#221C0F` | `#FBF3DE` | active-pill / chip / avatar / coach-card bg |
| `ttc_gold_container_outline` | `#5C4A22` | `#ECDBAC` | border paired with gold container |
| `ttc_on_gold` | `#2A2008` | `#2A2008` | text/icon on bright gold |
| `ttc_toggle_knob` | `#16130B` | `#2A2008` | switch knob on gold track |
| `ttc_text_1` | `#E7EAF0` | `#1A1D23` | primary text |
| `ttc_text_2` | `#B4BAC5` | `#545B68` | secondary text |
| `ttc_text_3` | `#7F8694` | `#868D99` | muted/section labels |
| `ttc_text_faint` | `#565C68` | `#A0A5AF` | disabled/faint |
| `ttc_text_on_gold_card` | `#D9CFB4` | `#6E5E38` | body text on gold container card |
| `ttc_success` | `#2FD08A` | `#12A05F` | positive trend (bright) |
| `ttc_success_soft` | `#9BE3A6` | `#3BAF6B` | positive stats / battery |
| `ttc_error` | `#E8817A` | `#C4463C` | negative trend / error |
| `ttc_error_container` | `#3A1A18` | `#FDECEA` | destructive-action background |
| `ttc_error_container_outline` | `#5A2B27` | `#F5C9C4` | coral chip border |
| `ttc_amber` | `#E9A25E` | `#E9A25E` | third status ("attention"/"Late") |
| `ttc_cream` | `#FBF3DE` | `#FBF4E1` | brand-warmth cream tile |

M3 role remap (both files), referencing the tokens above:

- `colorPrimary` = `ttc_gold_accent`; `colorOnPrimary` = `ttc_on_gold`;
  `colorPrimaryContainer` = `ttc_gold_container`; `colorOnPrimaryContainer` = `ttc_gold_accent`.
- `colorSecondary` = `ttc_text_2`; `colorOnSecondary` = `ttc_canvas`;
  `colorSecondaryContainer` = `ttc_surface_elevated`; `colorOnSecondaryContainer` = `ttc_text_1`.
- `colorTertiary` = `ttc_amber`; `colorOnTertiary` = `ttc_on_gold`.
- `colorError` = `ttc_error`; `colorOnError` = `#43110D` (dark)/`#FFFFFF` (light);
  `colorErrorContainer` = `ttc_error_container`; `colorOnErrorContainer` = `ttc_error` (dark)/`#7A1B14` (light).
- `android:colorBackground` = `ttc_canvas` (was `colorSurface`); `colorSurface` = `ttc_surface`;
  `colorOnSurface` = `ttc_text_1`; `colorSurfaceVariant` = `ttc_surface_elevated`;
  `colorOnSurfaceVariant` = `ttc_text_2`.
- `colorOutline` = `ttc_outline`; `colorOutlineVariant` = `ttc_outline_strong`.
- Surface containers: `Lowest`=`ttc_sink`, `Low`=`ttc_surface`, `Container`=`ttc_surface_elevated`,
  `High`=`ttc_outline`, `Highest`=`ttc_outline_strong`.
- `android:statusBarColor` = `?android:attr/colorBackground` (canvas, not card); `windowLightStatusBar`
  = false in `values-night` (light icons on dark canvas), true in `values` (dark icons on white).

Status/trend colors that are NOT M3 roles are consumed directly via `@color/ttc_success`,
`ttc_success_soft`, `ttc_amber`, and the error tokens.

### 2. Typography + fonts

Bundle 7 static TTFs (already instantiated/staged; OFL licenses included) into `app/src/main/res/font/`:
`inter_tight_regular/medium/semibold/bold.ttf` (400/500/600/700),
`jetbrains_mono_regular/medium/semibold.ttf` (400/500/600).

Font-family resources (weight-mapped, so `textStyle`/`fontWeight` pick the right file, works on minSdk 24):
- `res/font/inter_tight.xml` — 4 `<font>` entries by `app:fontWeight`/`android:fontWeight`.
- `res/font/jetbrains_mono.xml` — 3 `<font>` entries.

Theme default: set `android:fontFamily` and `?fontFamily` to `@font/inter_tight` on `AppTheme`, and
override the M3 `textAppearance*` slots to Inter-Tight-based appearances. Add mono numeral appearances:

Type ramp (from the design), as `TextAppearance.TTC.*` referenced by component styles / Slice 2 layouts:

| Appearance | Font | Spec |
|---|---|---|
| `TTC.Stat.Hero` | mono | `700 28sp` (ring center) |
| `TTC.Stat.Large` | mono | `700 22sp` (KPI) |
| `TTC.Stat.Medium` | mono | `700 20sp` (inset strips) |
| `TTC.Stat.Small` | mono | `600 15sp` (row accuracy) |
| `TTC.Mono.Meta` | mono | `500 11–12sp` (timestamps, ticks, readouts) |
| `TTC.Title.Screen` | Inter Tight | `700 22–24sp`, `-0.01em` |
| `TTC.Title.Card` | Inter Tight | `700 16–17sp` |
| `TTC.Body` | Inter Tight | `400 13sp`, line 1.5 |
| `TTC.Body.Secondary` | Inter Tight | `400 12sp` |
| `TTC.Eyebrow` | Inter Tight | `700 10sp`, `.11em`, UPPERCASE (`color=ttc_text_3`) |
| `TTC.Nav.Label` | Inter Tight | `500 10sp` inactive / `600 10sp` active |

(letterSpacing in em → Android `android:letterSpacing` unit is em, direct.)

### 3. Shape

`res/values/shapes.xml` shape appearances wired into the theme:
- `shapeAppearanceSmallComponent` → 11dp (buttons, avatar tiles).
- `shapeAppearanceMediumComponent` → 14dp (cards).
- `shapeAppearanceLargeComponent` → 16dp (extended FAB / large containers).
- `ShapeAppearance.TTC.Pill` → 999dp (chips, segmented controls, trend chips, progress bars).
- `ShapeAppearance.TTC.Inset` → 12dp (inset strips, list rows, swipe rows).
- `ShapeAppearance.TTC.Tile` → 10dp (icon tiles).

### 4. Universal component styles (`res/values/styles.xml`)

Exact values from the verbatim recipes (dark values via tokens so they theme automatically):

- **`TTC.Card`** (`Widget.Material3.CardView.Filled` parent): `cardBackgroundColor=@color/ttc_surface`,
  `strokeColor=@color/ttc_outline`, `strokeWidth=1dp`, `cardElevation=0dp`, `shapeAppearance=…Medium` (14dp),
  contentPadding 16dp.
- **`TTC.SegmentedTrack`** (LinearLayout style): `background` = pill drawable filled `ttc_sink`, stroke
  `ttc_outline`, radius 999dp, padding 4dp.
  - **`TTC.Segment.Inactive`**: transparent, `TTC.Body` weight-600 13sp, `ttc_text_2`, pill.
  - **`TTC.Segment.Active`**: bg `ttc_gold_container`, stroke `ttc_gold_container_outline`, 700 13sp,
    `ttc_gold_accent`, pill.
- **`TTC.Button.Primary`** (`Widget.Material3.Button` parent): `backgroundTint=@color/ttc_gold_bright`,
  `android:textColor=@color/ttc_on_gold`, `700 14sp`, icon tint `ttc_on_gold`, shape 11dp, padding 13dp.
- **`TTC.Button.Ghost`** (`Widget.Material3.Button.OutlinedButton`): transparent bg,
  `strokeColor=@color/ttc_outline`, `android:textColor=@color/ttc_text_2`, `600 14sp`, shape 10–11dp.
- **`TTC.Fab.Extended`** (`Widget.Material3.ExtendedFloatingActionButton`): `backgroundTint=ttc_gold_bright`,
  text/icon `ttc_on_gold`, `700 14sp`, shape 16dp.
- **`TTC.SectionHeader`** → `TextAppearance` `TTC.Eyebrow`.
- **`TTC.StatNumber`** (+ `.Gold` / `.Positive` variants) → `TTC.Stat.Medium`, colors
  `ttc_text_1` / `ttc_gold_accent` / `ttc_success`.
- **`TTC.TrendChip.Positive`** / **`.Negative`**: `TTC.Mono.Meta` 600 11sp, color `ttc_success` / `ttc_error`;
  the up/down triangle is a small drawable tinted to match (drawableStart).
- **`TTC.Toggle`** (`Widget.Material3.CompoundButton.MaterialSwitch`): `trackTint` selector
  (on=`ttc_gold_bright`, off=`ttc_outline_strong`), `thumbTint` selector (on=`ttc_toggle_knob`,
  off=`ttc_text_3`). (Off-state inferred; not in mockups.)
- **`TTC.Slider`** (`Widget.Material3.Slider`): `trackColorActive=ttc_gold_bright`,
  `trackColorInactive=ttc_sink`, `thumbColor=ttc_gold_bright`, `haloColor` translucent gold.

Drawables needed: `bg_pill_track` (sink fill + outline stroke), `bg_pill_gold_container`
(gold container fill + outline), `ic_trend_up` / `ic_trend_down` triangles.

### 5. Day-night wiring

- Dark tokens/roles → `values-night/colors.xml`; light → `values/colors.xml`.
- Preserve the existing in-app theme toggle (AppSettings) — no behavior change, it already flips
  `AppCompatDelegate` night mode; our palettes make both modes correct.

### 6. Preview harness (verification artifact)

`app/src/main/java/com/ttcoachai/debug/DesignSystemPreviewActivity.kt` + `res/layout/activity_design_system_preview.xml`.
- Same convention as existing debug activities: `android:exported="true"` in manifest, runtime
  `FLAG_DEBUGGABLE` gate (`finish()` on release), `parentActivityName=".MainActivity"`.
- Renders, in a scroll view: color swatch grid (each `ttc_*` token labeled), the type ramp
  (each `TextAppearance.TTC.*` with sample text incl. mono numerals), and one live instance of every
  `TTC.*` component (card, segmented control w/ active+inactive, primary button, ghost button,
  extended FAB, eyebrow, stat numbers ×3, trend chips ±, switch, slider).
- Launch for verification: `adb shell am start -n com.ttcoachai/.debug.DesignSystemPreviewActivity`.
- Light/dark checked by toggling `adb shell cmd uimode night yes|no` and screenshotting each.

## Files touched / added

- `app/src/main/res/values/colors.xml` — add `ttc_*` tokens (light), remap M3 roles.
- `app/src/main/res/values-night/colors.xml` — add `ttc_*` tokens (dark), remap M3 roles.
- `app/src/main/res/values/styles.xml` — theme font/shape wiring, `TextAppearance.TTC.*`, `TTC.*` styles.
- `app/src/main/res/values/shapes.xml` — NEW: shape appearances + Pill/Inset/Tile.
- `app/src/main/res/font/` — NEW: 7 TTFs + `inter_tight.xml`, `jetbrains_mono.xml`.
- `app/src/main/res/color/` — NEW: `ttc_switch_track.xml`, `ttc_switch_thumb.xml` selectors.
- `app/src/main/res/drawable/` — NEW: `bg_pill_track.xml`, `bg_pill_gold_container.xml`,
  `ic_trend_up.xml`, `ic_trend_down.xml`.
- `app/src/main/java/com/ttcoachai/debug/DesignSystemPreviewActivity.kt` — NEW.
- `app/src/main/res/layout/activity_design_system_preview.xml` — NEW.
- `app/src/main/AndroidManifest.xml` — register the preview activity (debug-gated, exported).
- `app/src/main/res/font/OFL-*.txt` (or `app/src/main/assets/`) — bundled font licenses.

## Risks & mitigations

- **Mid-migration mixed look:** remapping M3 roles recolors stock-widget parts of existing screens
  while legacy-hardcoded parts stay blue. Expected; Slice 2 resolves. The preview harness is the
  isolated, clean verification for this slice — do not judge Slice 1 by the un-migrated screens.
- **Blanket widget default overrides could ripple** unpredictably. Mitigation: set only safe
  theme-level defaults (fontFamily, shapeAppearance, role colors); ship component looks as *named*
  `TTC.*` styles applied explicitly, not by overriding every `materialButtonStyle` etc.
- **Light theme has 3 per-screen flavors** in the design. This slice ships ONE canonical light
  palette (cool-white); warm-paper per-screen variants are a Slice-2 concern (local overrides).
- **Fonts:** static weights already instantiated from the variable font (avoids API<26 variable-font
  degradation); minSdk 24 safe.

## Verification / exit gate

1. `./gradlew :app:assembleDebug` succeeds (JBR 21 auto-selected via `~/.gradle`).
2. `./gradlew :app:testDebugUnitTest` (existing app unit tests) still green.
3. Install on the connected Galaxy S23; launch `DesignSystemPreviewActivity`; screenshot in
   **dark** and **light** (uimode toggle) into `tmp/screenshots/`.
4. Visual check: swatches match the token table; Inter Tight + JetBrains Mono render (mono numerals
   visibly monospaced); components match the recipes (gold container active pill, bright-gold filled
   button with near-black text, ± trend chips in green/coral, gold switch/slider).
5. App still launches to its normal start screen without crash.
