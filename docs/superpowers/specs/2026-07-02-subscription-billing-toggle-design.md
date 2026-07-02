# Subscription paywall — billing-toggle redesign (12b / 13a)

Date: 2026-07-02
Track: Android gold-dark redesign (Slice 3 — new/restyled screens)
Source design: claude.ai/design project `feb1eaea-d763-41c9-86fe-1262790d7291`, `Live Session.dc.html`, screens **12b** (Plans · billing toggle, gold-dark) and **13a** (Plans · billing toggle, white/light theme).

## Summary

Restyle the existing subscription paywall from the current **3-card plan picker** (which corresponds to design 12a) to the **billing-toggle** layout (12b / 13a): one focused price with a Monthly / Quarterly / Yearly segmented switch, a price hero, a "What's included" checklist, and a single gold CTA.

12b and 13a are the **same layout in two themes** (dark vs. light). The app is already a DayNight app (`values-night/` exists, user-facing theme toggle in Profile). The Slice 1 design tokens already encode both palettes exactly, so this is implemented as **one `activity_subscribe.xml` referencing semantic `ttc_*` color tokens** — it renders as 12b in dark mode and 13a in light mode automatically. No second layout.

Scope is **presentation + light view logic only**. The billing backend stays fully mocked (`SettingsManager.setSubscriptionActive(true)`), consistent with the current implementation.

## Token mapping (design → existing resources)

The Slice 1 tokens match the design pixel-for-pixel; no new palette work is needed.

| Design role | 12b (dark) | 13a (light) | Token |
|---|---|---|---|
| Page background | `#0E1115` | `#F5F6F8` | `ttc_canvas` / `ttc_sink` (see note) |
| Card surface | `#141820` | `#FFFFFF` | `ttc_surface` |
| Elevated / back-btn | `#1A1F28` | `#FFFFFF`/`#F5F6F8` | `ttc_surface_elevated` |
| Gold accent | `#E9C46A` | `#9A6F0F` | `ttc_gold_accent` |
| Gold container (price hero / check bg) | `#221C0F` | `#FBF3DE` | `ttc_gold_container` |
| Gold container outline | `#5C4A22` | `#ECDBAC` | `ttc_gold_container_outline` |
| Primary text | `#E7EAF0` | `#1A1D23` | `ttc_text_1` |
| Secondary text | `#B4BAC5` | `#4E545F` | `ttc_text_2` |
| Muted text | `#7F8694` | `#868C98` | `ttc_text_3` |
| Savings (green) | `#9BE3A6` on `#0E241B`/`#235C3F` | `#1B9257` on `#E9F7EF`/`#BEE6D0` | `ttc_success` / `ttc_success_soft` |
| CTA text on gold | `#16130B` | `#2A2008` | `ttc_on_gold` |

Note: the design's page background (`#0E1115` dark / `#F5F6F8` light) is one step darker/lighter than `ttc_surface`. Map it to the existing `ttc_canvas`/`ttc_sink` token if it matches; if neither is close, add a `ttc_paywall_bg` token in both `values/colors.xml` and `values-night/colors.xml` rather than hardcoding hex in the layout.

## Layout — `activity_subscribe.xml` (top → bottom)

Root: vertical scroll container on the page-background token, one 360dp-equivalent column (use `match_parent` with horizontal padding for real devices).

1. **Top bar** — back arrow (left, `ic_chevron_left`, in a rounded `ttc_surface_elevated` tile) that `finish()`es back to Profile; "Restore" text link (right).
2. **Hero** — gold-filled circle (56–60dp) with the crown/award mark, "Unlock Premium" title, one-line subtitle.
3. **Billing toggle** — segmented control, pill-shaped track (`MaterialButtonToggleGroup` or a custom 3-segment row), options **Monthly · Quarterly · Yearly**, single-select, **Yearly selected by default**. Selected segment = gold fill + on-gold text; unselected = muted text on track.
4. **Price hero** — `ttc_gold_container` card with `ttc_gold_container_outline` stroke: a badge pill (varies by period), large mono price, `/period` suffix, "Just $X / month" line, and a green savings pill.
5. **What's included** — section label + a `ttc_surface` card listing 5 features, each a gold check chip + label:
   - Unlimited AI coaching sessions
   - Advanced pose & stroke analysis
   - Personalized training plans
   - Advanced analytics & error breakdown
   - Detailed post-session reports
6. **CTA** — full-width gold "Start Premium" button with a trailing chevron; billing caption below ("$79.99 billed yearly · cancel anytime", varies by period).
7. **Footer** — "Terms" · dot · "Privacy" text links.

Remove from the old layout: the three plan cards, the "Maybe Later" button, and the old auto-renew/guarantee footer paragraph.

## Behavior — `SubscribeActivity.kt`

Keep the existing `Plan { MONTHLY, QUARTERLY, YEARLY }` enum and `selectedPlan` (default `YEARLY`, matching the design). Replace the 3-card click logic with toggle-selection logic. On period change, update the price hero + CTA caption:

| Period | Badge | Price | Per-month | Savings | CTA caption |
|---|---|---|---|---|---|
| Monthly | *(none)* | $9.99 /mo | — | *(none)* | $9.99 billed monthly · cancel anytime |
| Quarterly | Popular | $24.99 /3 mo | Just $8.33 / month | Save 17% vs monthly | $24.99 billed every 3 months · cancel anytime |
| Yearly *(default)* | Best value | $79.99 /yr | Just $6.67 / month | Save 33% vs monthly | $79.99 billed yearly · cancel anytime |

Badge/savings pills are hidden (GONE) for Monthly. All price/label text comes from string resources — no hardcoded prices in code beyond what already exists in `strings.xml`.

- **Start Premium** → unchanged mock: `settingsManager.setSubscriptionActive(true)`, toast, `finish()`.
- **Back arrow** → `finish()` (returns to Profile; the only dismiss path — no "Maybe Later").
- **Restore** → placeholder: toast ("No purchases to restore" / localized). No-op otherwise.
- **Terms / Privacy** → placeholder no-ops for now (stubbed click handlers, ready to wire to URLs later).

## Strings

Reuse existing subscription strings where present (`subscribe_title`, `subscribe_subtitle`, `plan_*`, `feature_*`, `badge_popular`, `badge_best_value`). Add new strings (EN in `values/strings.xml`, UK in `values-uk/strings.xml`) for anything the toggle layout introduces that doesn't already exist, e.g.:
- `subscribe_restore` — "Restore"
- `subscribe_start_premium` — "Start Premium"
- `subscribe_per_month_format` — "Just %1$s / month"
- `subscribe_savings_format` — "Save %1$d%% vs monthly"
- `subscribe_billed_monthly` / `_quarterly` / `_yearly` captions
- `subscribe_whats_included` — "What's included"
- `subscribe_terms`, `subscribe_privacy`
- `subscribe_restore_none` — restore toast text

The exact reuse-vs-add split is a plan-time detail; the rule is **no hardcoded user-facing text in the layout or activity**, and EN + UK must stay in parity.

## Out of scope

- Real billing / Google Play Billing integration (stays mocked).
- Profile's upgrade/premium cards (screen 9a) and AppSettings' developer toggle — unchanged; they still launch `SubscribeActivity`.
- Screen 12a (plan picker) — not implemented; the toggle layout replaces it.
- Any pose/ball/trajectory pipeline code (frozen).

## Testing

This is presentation + view logic in `app/` (Android-only, no shared KMP). Verify by:
- Build: `./gradlew :app:assembleDebug`.
- Visual check on device in **both** themes via the Profile theme toggle (dark → matches 12b, light → matches 13a). Capture screenshots to `tmp/screenshots/`.
- Confirm toggle updates price hero + caption for all three periods, badges hide correctly for Monthly, and back arrow returns to Profile.
