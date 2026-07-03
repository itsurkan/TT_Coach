# Gold-Dark Design System — Slice 2 (Existing Screens) Spec

**Date:** 2026-07-02

**Goal:** Restyle the 6 main navigation screens (Dashboard, Progress, Drills, Settings, Profile, History) from legacy Material 2 color palette to the Slice 1 TTC.* gold-dark design system (tokens + component styles).

## Scope

**Screens to restyle:**
1. **Dashboard** (`fragment_dashboard.xml`, `DashboardFragment.kt`) — entry screen with quick-stat cards, session summary, call-to-action
2. **Progress** (`fragment_progress.xml`, `ProgressFragment.kt`) — metric charts/graphs, session history, trend indicators
3. **Drills** (`fragment_drills.xml`, `DrillsFragment.kt`) — drill list, exercise cards, drill selection UI
4. **Settings** (`fragment_settings.xml`, `SettingsFragment.kt`) — preference toggles, app settings, theme selection
5. **Profile** (`fragment_profile.xml`, `ProfileFragment.kt`) — user profile info, stats summary, personal baseline display
6. **History** (integrated into Progress or as modal) — session/rep replay, timeline, session details

**Out of scope for Slice 2:**
- New screens (Session Review, Feedback, Detection, forms) — those are Slice 3
- Live Session `1a` capture UI — blocked on parent design doc (Slice 4)
- Pipeline code (frozen)
- Debug activities (already styled in Slice 1)

## Design Direction

**Color scheme:**
- Canvas: `@color/ttc_canvas` (#FFFFFF light / #0E1115 dark)
- Surface/cards: `@color/ttc_surface` (#F7F8FA light / #141820 dark)
- Elevated surface: `@color/ttc_surface_elevated` (#EFF1F4 light / #1A1F28 dark)
- Text primary: `@color/ttc_text_1` (#1A1D23 light / #E7EAF0 dark)
- Text secondary: `@color/ttc_text_2` (#545B68 light / #B4BAC5 dark)
- Gold accents: `@color/ttc_gold_bright` (#E9C46A both themes) + `@color/ttc_gold_accent` (deepens to #9A6F0F light)
- Status/alert: `@color/ttc_success` (#12A05F light / #2FD08A dark), `@color/ttc_error` (#C4463C light / #E8817A dark)

**Component styles (all defined in Slice 1):**
- `style/TTC.Card` — card wrapper with correct surface + elevation
- `style/TTC.Button.Primary` — gold-bright button with correct text appearance
- `style/TTC.Button.Ghost` — text-only button for secondary actions
- `style/TTC.SegmentedTrack` / `TTC.Segment.Active/Inactive` — tab/chip selectors
- `style/TTC.Toggle` — switch states with gold/outline colors
- `style/TTC.Slider` — slider with gold track
- `style/TTC.SectionHeader` — eyebrow label for grouping
- `style/TTC.StatNumber.*` — numeric displays (mono type scale)
- `style/TTC.TrendChip` — badge showing metric direction (+/−)

**Typography scale:** All text appearances (`TextAppearance.TTC.*`) are defined in Slice 1. Screens migrate from legacy `android:textAppearance="@style/..."` to `style/TextAppearance.TTC.*` (e.g., screen titles → `TextAppearance.TTC.Title.Screen`, body → `TextAppearance.TTC.Body`).

## Reference

- **Slice 1 spec:** [2026-07-01-android-gold-dark-foundation-design.md](2026-07-01-android-gold-dark-foundation-design.md)
- **Slice 1 plan:** [../plans/2026-07-01-android-gold-dark-foundation.md](../plans/2026-07-01-android-gold-dark-foundation.md)
- **Design tokens (verbatim):** [../../design/design-tokens-source.md](../../design/design-tokens-source.md)
- **Claude Design project:** UUID `feb1eaea-d763-41c9-86fe-1262790d7291` (Live Session / surrounding screens; details TBD)

## Constraints

- No new dependencies — use existing Material Components + Slice 1 resources only
- Freeze discipline: touch presentation (layouts + colors + type) only. Do not refactor Fragment logic, do not change data models.
- Legacy color names (`blue_600`, `amber_500`, etc.) stay in `values/colors.xml` and `values-night/colors.xml` — Slice 3 migrates cards that use them; Slice 2 only adds new layout/style bindings.
- Commit per screen (6 commits total) with explicit paths.
- Build check after each commit: `./gradlew :app:assembleDebug` + optional on-device visual verification.
- Co-Author trailer: `Claude Haiku 4.5 <noreply@anthropic.com>`

## Restyling Approach

For each screen:
1. **Read** current `fragment_*.xml` layout and identify components (cards, buttons, text, etc.)
2. **Map** each component to a Slice 1 style or token (e.g., card → `TTC.Card`, button → `TTC.Button.Primary`, heading → `TextAppearance.TTC.Title.Card`)
3. **Migrate** legacy color refs (`@color/blue_600`, `android:textColor="..."`) to token refs (`@color/ttc_text_1`) and style refs (`style="@style/TTC.Card"`)
4. **Verify** the layout compiles and (on device) renders correctly in both light and dark themes
5. **Commit** with a clear message (e.g., `feat(screens): restyle Dashboard to gold-dark design system`)

## Success Criteria

- All 6 screens build without errors
- All 6 screens render in both light and dark themes with correct colors, fonts, and spacing
- Components use Slice 1 `TTC.*` styles / `ttc_*` tokens (no hardcoded colors or legacy Material 2 ref in new bindings)
- On-device screenshots show gold-dark aesthetic (dark canvas, light text, gold accents, elevated surfaces, shadows/elevations via Material Design Tokens)
- No functional changes (Fragment logic, data flow, navigation remain identical)
