# Gold-Dark Design System — Slice 2 (Existing Screens) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle Dashboard, Progress, Drills, Settings, Profile, and History screens to the Slice 1 gold-dark design system (TTC.* styles + ttc_* tokens), so users see a cohesive Material 3 dark-theme design across the app.

**Architecture:** Layouts migrate from legacy color/style bindings to Slice 1 resources. Fragment logic and data flow are frozen (presentation-only changes). Light and dark themes auto-switch via Material3 DayNight + the `?attr/isLightTheme` binding from Slice 1.

**Tech Stack:** Android XML resources, Material Components 1.12.0, Kotlin (no changes to Fragments), Gradle.

## Global Constraints

- Spec: [docs/superpowers/specs/2026-07-02-android-gold-dark-slice2-screens.md](../specs/2026-07-02-android-gold-dark-slice2-screens.md)
- Freeze discipline: layouts + colors + type only. No Fragment refactors, no data-model changes.
- Do NOT delete legacy color names (they may be used elsewhere; Slice 3 cleans them up).
- Do NOT touch the Debug Preview Activity or any of Slice 1's resources (frozen).
- Commit after each screen with explicit paths (`git add app/src/main/res/layout/fragment_*.xml` + optional color/style adjustments for that screen).
- Build check after each commit: `./gradlew :app:assembleDebug`.
- Co-Author trailer: `Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>`

---

### Task 1: Restyle Dashboard screen

**Files:**
- Read: `app/src/main/java/com/ttcoachai/fragment/DashboardFragment.kt` (understand data + component types)
- Modify: `app/src/main/res/layout/fragment_dashboard.xml` (migrate colors/styles to TTC.*)

**Approach:**
1. Read the current layout to identify all visual elements (cards, buttons, text, images)
2. For each component:
   - Card elements → add or update `style="@style/TTC.Card"`
   - Text (headers) → add `android:textAppearance="@style/TextAppearance.TTC.Title.Card"`
   - Text (body) → add `android:textAppearance="@style/TextAppearance.TTC.Body"`
   - Stat numbers → use `TextAppearance.TTC.Stat.*` (mono font, appropriate weight/size)
   - Buttons → use `style="@style/TTC.Button.Primary"` or `TTC.Button.Ghost`
   - Icons (if colorized) → add `android:tint="@color/ttc_gold_accent"` or `ttc_text_2` as appropriate
   - Legacy `android:textColor="@color/blue_*"` or similar → replace with `@color/ttc_text_1` (primary), `ttc_text_2` (secondary), or `ttc_gold_accent` (accent)
   - Legacy `android:background="@color/..."` → replace with appropriate `ttc_*` token
3. Build and verify.
4. Commit.

- [ ] **Step 1: Read current Dashboard layout**

```kotlin
// Pseudocode to understand structure
// Typically: ScrollView > VerticalLayout [
//   - Header (SectionHeader style)
//   - Quick-stat cards (TTC.Card style, StatNumber children)
//   - Session summary (TTC.Card, TTC.Body text)
//   - Call-to-action button (TTC.Button.Primary)
// ]
```

- [ ] **Step 2: Migrate all color/style bindings in `fragment_dashboard.xml`**

See the approach above. Replace legacy refs with Slice 1 tokens/styles.

- [ ] **Step 3: Build and verify**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. No layout validation errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_dashboard.xml
git commit -m "feat(screens): restyle Dashboard to gold-dark design system

Migrate legacy colors/styles to TTC.* tokens and component styles.
Cards, buttons, and text now use Slice 1 design system.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
```

---

### Task 2: Restyle Progress screen

**Files:**
- Read: `app/src/main/java/com/ttcoachai/fragment/ProgressFragment.kt` + helper loader
- Modify: `app/src/main/res/layout/fragment_progress.xml`

**Approach:** Same as Task 1. Progress likely contains:
- Section headers (eyebrow + title)
- Chart/graph containers (background color → `ttc_surface_elevated`)
- Metric cards with trend chips (TrendChip style, TTC.Card wrapper)
- Text: headers → TTC.Title.*, body → TTC.Body, secondary → ttc_text_2
- Buttons (filter, export, etc.) → TTC.Button.*

- [ ] **Step 1: Read current Progress layout**
- [ ] **Step 2: Migrate all color/style bindings in `fragment_progress.xml`**
- [ ] **Step 3: Build and verify**
- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_progress.xml
git commit -m "feat(screens): restyle Progress to gold-dark design system

Migrate legacy colors/styles to TTC.* tokens and component styles.
Charts, metric cards, and trend indicators now use Slice 1 design system.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
```

---

### Task 3: Restyle Drills screen

**Files:**
- Read: `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`
- Modify: `app/src/main/res/layout/fragment_drills.xml`

**Approach:** Same as Task 1. Drills likely contains:
- Section headers (TTC.SectionHeader style)
- Drill list items (cards with drill name/status; TTC.Card wrapper)
- Selection UI (segmented control / chip group; SegmentedTrack style)
- Text: drill titles → TTC.Title.*, status → TTC.Body.Secondary or TTC.Text.Faint
- Buttons (start drill, etc.) → TTC.Button.Primary or Ghost

- [ ] **Step 1: Read current Drills layout**
- [ ] **Step 2: Migrate all color/style bindings in `fragment_drills.xml`**
- [ ] **Step 3: Build and verify**
- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_drills.xml
git commit -m "feat(screens): restyle Drills to gold-dark design system

Migrate legacy colors/styles to TTC.* tokens and component styles.
Drill list, selection UI, and action buttons now use Slice 1 design system.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
```

---

### Task 4: Restyle Settings screen

**Files:**
- Read: `app/src/main/java/com/ttcoachai/fragment/SettingsFragment.kt`
- Modify: `app/src/main/res/layout/fragment_settings.xml`

**Approach:** Same as Task 1. Settings likely contains:
- Section headers (TTC.SectionHeader)
- Preference rows (cards with toggle/text; TTC.Card wrapper, TTC.Toggle style for switches)
- Text: preference title → TTC.Body, description → TTC.Body.Secondary
- Toggles (theme, notifications, etc.) → MaterialSwitch with `style="@style/TTC.Toggle"` (inherits colors from Slice 1 binding)

- [ ] **Step 1: Read current Settings layout**
- [ ] **Step 2: Migrate all color/style bindings in `fragment_settings.xml`**
- [ ] **Step 3: Build and verify**
- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_settings.xml
git commit -m "feat(screens): restyle Settings to gold-dark design system

Migrate legacy colors/styles to TTC.* tokens and component styles.
Preference rows, toggles, and text now use Slice 1 design system.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
```

---

### Task 5: Restyle Profile screen

**Files:**
- Read: `app/src/main/java/com/ttcoachai/fragment/ProfileFragment.kt`
- Modify: `app/src/main/res/layout/fragment_profile.xml`

**Approach:** Same as Task 1. Profile likely contains:
- Header section (profile pic + name/email; background → ttc_surface_elevated or ttc_canvas)
- Info cards (baseline stats, personal bests; TTC.Card wrapper)
- Text: name → TTC.Title.Card, stats → TTC.Stat.*, secondary info → TTC.Body.Secondary
- Buttons (edit, share, etc.) → TTC.Button.Primary or Ghost

- [ ] **Step 1: Read current Profile layout**
- [ ] **Step 2: Migrate all color/style bindings in `fragment_profile.xml`**
- [ ] **Step 3: Build and verify**
- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_profile.xml
git commit -m "feat(screens): restyle Profile to gold-dark design system

Migrate legacy colors/styles to TTC.* tokens and component styles.
Profile info, baseline stats, and action buttons now use Slice 1 design system.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
```

---

### Task 6: Restyle History (or integrate into Progress)

**Files:**
- Read: check if History is a separate screen (`fragment_history.xml`) or integrated into Progress
- Modify: `app/src/main/res/layout/fragment_*.xml` (as applicable)

**Approach:** Same as Task 1. If History is a modal or nested view within Progress:
- Session items (cards; TTC.Card wrapper)
- Session metadata (date, duration, drill name; TTC.Body.Secondary or ttc_text_2)
- Rep/metric timeline (scrollable list of items; TTC.Card per item)
- Action buttons (replay, delete, etc.) → TTC.Button.* styles

- [ ] **Step 1: Locate History UI (separate screen or nested)**
- [ ] **Step 2: Migrate all color/style bindings**
- [ ] **Step 3: Build and verify**
- [ ] **Step 4: Commit**

```bash
# If separate layout:
git add app/src/main/res/layout/fragment_history.xml

# If nested within Progress layout (already committed in Task 2):
# No additional commit needed; include in Task 2's Progress commit.

git commit -m "feat(screens): restyle History to gold-dark design system

Migrate legacy colors/styles to TTC.* tokens and component styles.
Session items and timeline now use Slice 1 design system.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
```

---

## Exit Gate

After all 6 tasks:

- [ ] **Full build check:** `./gradlew :app:assembleDebug && ./gradlew test`
- [ ] **On-device visual verification (light + dark themes):**
  - Install on device: `./gradlew :app:installDebug`
  - Navigate each screen (Dashboard → Progress → Drills → Settings → Profile → History)
  - Take screenshots in both light (via Settings theme toggle) and dark modes
  - Verify colors match tokens (gold-bright buttons, surface elevated cards, text hierarchy)
  - Verify fonts (Inter Tight headings, JetBrains Mono for numbers)
  - No layout breaks, text truncation, or rendering glitches
- [ ] **Final commit (if any last-minute fixes needed):** `git commit -m "..."`
- [ ] **Report:** Slice 2 complete, 6 screens restyled, on-device verified in both themes

---

## Notes

- **Legacy color refs outside Slice 2:** If a screen uses colors not covered by Slice 2 (e.g., a specific drawable or hard-coded color in Fragment code), note it but do NOT fix it in Slice 2 — that's Slice 3 cleanup.
- **Light theme deepening:** In light theme, `ttc_gold_accent` is `#9A6F0F` (darker than bright `#E9C46A`). Verify buttons/accents in light theme look correct (readability on white background).
- **Emulator vs. phone:** If phone is available, use it for verification (more accurate rendering). Otherwise, use Medium_Phone or Pixel_6 AVD emulator.
