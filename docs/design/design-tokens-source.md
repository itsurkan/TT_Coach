# TT Coach — Design Tokens (verbatim from Claude Design canvas)

Source: `Live Session.dc.html` (Claude Design canvas). Hand-built mockups, all values are **inline styles**.
The only CSS classes are design-review chrome (`dv-*`) and are ignored here.
Fonts loaded via Google Fonts: `Inter Tight` weights **400;500;600;700**, `JetBrains Mono` weights **400;500;600**.
Body font-family fallback stack (declared once on `<body>`): `'Inter Tight',system-ui,sans-serif`.

Screens (17), by theme:
- **DARK:** Feedback·Gold, Detection·Gold, Exercises·Swipe actions, Exercises·Long-press menu, Exercises·New, Exercises·Clone, Profile·Gold, Settings·Gold, Session review·Summary, Session review·Analytics, Session history·Log, Session history·Calendar, Progress·Gold, Home·Gold.
- **LIGHT:** Session review·Analytics (light), Progress·Light, Session history·Log·light.

All hex are as authored (uppercased). 3-char "#…" strings in the raw file were HTML entities (`&#160;` etc.), not colors — excluded.

---

## A. DARK THEME PALETTE (canonical)

### App canvas / background
| Hex | Role / where |
|---|---|
| `#0E1115` | **App canvas** — phone-frame `background`, status-bar bg, header bg. The base screen color. |
| `#0B0E12` | **Deepest sink** — bottom-nav bar bg, inset stat strips, slider tracks, progress-bar troughs, segmented-control track bg. One step darker than the canvas. |

### Card / surface
| Hex | Role / where |
|---|---|
| `#141820` | **Primary card / surface** — every content card (last-session, this-week, KPI, chart, settings group, list rows). The default raised surface. |
| `#1A1F28` | **Elevated / nested surface** — icon tiles inside cards (38–46px rounded squares), back-button tiles, the Progress chart's segmented-toggle bg, "Recent" featured card bg. |
| `#171B22` | **Calendar heatmap empty cell** / alt elevated surface (Session history·Calendar). Between `#141820` and `#1A1F28`. |
| `#1E2530` | **Chart gridlines on cards** — faint horizontal rules inside chart cards (Progress, Analytics). |
| `#181C23` | (appears in light-history screen — see Light section; NOT a dark token.) |

### Borders / dividers (dark)
| Hex | Role / where |
|---|---|
| `#232833` | **Primary border / divider** — border on all cards, inset strips, segmented tracks; 1px vertical/horizontal dividers between stats; also the phone-frame outer border. The workhorse border. |
| `#2F3542` | **Stronger border** — swipe-row cards (`border:1px solid #2F3542`), the "Total hours" KPI icon-tile border (neutral, non-gold). |
| `#1A1F28` | **Bottom-nav top border** — `border-top:1px solid #1A1F28` (doubles as elevated surface color). |
| `#3C424E` | **Muted dot / micro-divider** — 3px meta-separator dots between exercise metadata; icon strokes for low-emphasis chevrons in some rows. |
| `#2A3040` | Minor border variant (Exercises New/Clone chip outlines). |

### Gold family (the accent)
| Hex | Role / where |
|---|---|
| `#E9C46A` | **Primary gold accent** — active nav/text, FAB fill, primary-button fill, chart bars, progress-bar fill, active-pill text, icon strokes, slider fill+thumb, "Premium" tag text, accent numbers (accuracy %). THE brand gold. |
| `#221C0F` | **Gold-tinted surface (active pill bg / gold container)** — active segmented-pill bg, "Premium"/streak chip bg, avatar circle bg, gold icon-tile bg, active-nav pill bg, coach-summary card bg, swipe "Clone" action bg, Tip card bg. Very dark warm brown. |
| `#5C4A22` | **Gold-tinted border** — border paired with `#221C0F` on every gold container (active pills, chips, avatar, coach card, Tip card, slider-thumb outer ring). |
| `#2A2008` | **On-gold text/icon** — text and icon color on top of a solid `#E9C46A` fill (button labels, FAB label, avatar initial on gold, active toggle-related). Near-black warm brown. |
| `#16130B` | **On-gold toggle knob** — the switch knob color when the track is gold-on (`background:#16130B`). Darkest warm brown. |
| `#A9862F` | **Deep/muted gold** — lower-tier progress-bar fills (33%, 22%), medium-intensity calendar heatmap cells. A desaturated bronze-gold. |
| `#C99A2A` | (light-history icon gold — see Light section.) |
| `#9A6F0F` | **Deepened gold (LIGHT theme accent)** — see Light section; appears only in light screens. |
| `#8A6412` | **Warm gold icon stroke on cream** — icon stroke inside the dark Profile "Premium" upsell block (on `#FBF3DE` cream tile). |
| `#6B5214` | **Warm gold body text on cream** — body copy inside the dark Profile Premium cream block. |
| `#B49B63` | Muted gold text (one-off, long-press menu label). |

### Text colors (dark)
| Hex | Tier | Where |
|---|---|---|
| `#E7EAF0` | **Primary text** — headings, card titles, primary values, status-bar clock. Near-white cool. |
| `#B4BAC5` | **Secondary text** — body copy, inactive-pill text, secondary/ghost-button labels, sub-values ("Weekly goal"), battery %. |
| `#7F8694` | **Muted / tertiary** — section labels (UPPERCASE), captions, sub-descriptions, meta timestamps, inactive nav labels, most muted numerals. The dominant muted tone. |
| `#565C68` | **Faint / disabled** — weekend day letters, faint chevrons, low-emphasis icon strokes, "142 strokes" faint count, battery outline. |
| `#868D99` / `#868C98` / `#8A909E` | Muted-text near-duplicates that surface mostly on light screens; on dark, `#7F8694` is canonical. |
| `#D9CFB4` | **Warm text on gold card** — coach-summary body text on `#221C0F` bg (warm cream-grey). |

### Success / positive green (dark)
| Hex | Role / where |
|---|---|
| `#2FD08A` | **Positive/success accent** — up-trend chips (+n) with up-triangle icon, positive skill-trend numerals. Bright mint-green. |
| `#9BE3A6` | **Battery-full green** — status-bar battery fill; also a "72% consistency" positive stat number and "68% accuracy" positive meta. Soft light green. |

### Error / negative coral-red (dark)
| Hex | Role / where |
|---|---|
| `#E8817A` | **Negative/error coral** — down-trend chips (−n) with down-triangle, "Error" legend swatch + bar segment, "Top focus" tag text, "Delete" swipe-action text+icon, top-focus progress-bar fill. The brand coral. |
| `#3A1A18` | **Delete-row / destructive action background** — swipe "Delete" action panel bg (paired with `#E8817A` fg). Very dark maroon. |
| `#2A1512` | **Coral chip background** — "Top focus" tag bg (with `#E8817A` text). |
| `#5A2B27` | **Coral chip border** — border of the "Top focus" tag (with `#2A1512` bg). |

### Warm amber (mid-tier, not error, not gold)
| Hex | Role / where |
|---|---|
| `#E9A25E` | **Warm amber / "Late" / attention** — "Late" shot-quality swatch + bar segment, mid-tier focus-area bars (61%, 50%), the "Focus this week" dot. Between gold and coral. |

### Warm cream / brand-warmth (dark screens)
| Hex | Role / where |
|---|---|
| `#FBF3DE` | **Cream icon tile** — bright cream square behind the trophy/premium icon in the dark Profile upsell block. The one bright warm surface on dark. |
| `#221C0F` | (also the warm gold-container — see gold family.) |

### Overlays / scrims / glows (dark)
| Value | Role / where |
|---|---|
| `rgba(0,0,0,.5)` | Phone-frame drop shadow (`box-shadow:0 24px 60px rgba(0,0,0,.5)`); FAB secondary shadow; swipe-row soft shadows. |
| `rgba(0,0,0,.45)` | Extended-FAB drop shadow. |
| `rgba(0,0,0,.6)` / `rgba(0,0,0,.62)` | Deeper card/menu scrims (long-press menu). |
| `rgba(0,0,0,.65)` | Swipe-action edge shadow (`box-shadow:-14px 0 22px -10px rgba(0,0,0,.65)`). |
| `rgba(6,8,11,.72)` | Long-press full-screen scrim (dark blue-black). |
| `rgba(233,196,106,.5)` | **Gold glow** — round-FAB glow shadow (`0 10px 20px -6px rgba(233,196,106,.5)`). |
| `rgba(233,196,106,.13)` | Gold glow soft (menu highlight). |
| `rgba(233,196,106,.10)` | **Gold area-fill** — accuracy-curve gradient fill under the line (dark analytics). |
| `rgba(255,255,255,.06)` / `.08` / `.09` | Subtle white hairlines/inner strokes (menu/calendar). |

---

## B. LIGHT THEME PALETTE

Two light flavors exist:
- **Cool-white** (Session review·Analytics-light, Progress·Light): near-white canvas, cool greys.
- **Warm-paper** (Session history·Log·light): warmer surfaces + a brighter medium-gold.

### Canvas (light)
| Hex | Role | Screen |
|---|---|---|
| `#F5F6F8` | App canvas (cool off-white) | Session review·light |
| `#FFFFFF` | App canvas (pure white) | Progress·light, Session history·light |

### Surface / card (light)
| Hex | Role | Screen |
|---|---|---|
| `#FFFFFF` | Primary card/surface (on the off-white canvas) | Session review·light (cards on `#F5F6F8`) |
| `#F7F8FA` | Primary card/surface (on the white canvas) | Progress·light (cards on `#FFFFFF`) |
| `#F1F3F6` | Nested/inset surface — progress-bar troughs, shot-quality track, back-tile bg | Session review·light |
| `#EFF1F4` | Nested/inset surface — segmented-toggle track bg, neutral KPI icon tile | Progress·light |
| `#F1F3F6` | Elevated tile bg (upload/back icon tile) | Session history·light |

### Borders / dividers (light)
| Hex | Role | Screen |
|---|---|---|
| `#E4E7EC` | Primary card border + 1px dividers | Session review·light |
| `#E9ECF1` | Phone-frame outer border | Session review·light |
| `#EAECF0` | Bottom-nav top border | Session review·light |
| `#EBEDF1` | Chart gridlines | Session review·light |
| `#E4E6EC` | Primary card border + dividers + nav border | Progress·light |
| `#E0E2E8` | Phone-frame outer border | Progress·light |
| `#E7E9EE` | Progress-bar troughs + chart gridlines | Progress·light |
| `#E6E8ED` | Primary border/divider (warm-paper) | Session history·light |
| `#E2E5EA` | Inactive filter-chip border | Session history·light |

### Gold accent (light) — deepened for legibility on white
| Hex | Role | Screen |
|---|---|---|
| `#9A6F0F` | **Deepened gold accent** — accent text, accuracy %, section-eyebrow, icon strokes, accuracy-curve line, chart dots, active-nav text/icon | Session review·light (cool) |
| `#997219` | **Deepened gold accent** (near-identical) — icon strokes, active weekend letter, section-header icons | Progress·light |
| `#C99A2A` | **Medium gold** — list-row icon strokes/fills (warm-paper variant) | Session history·light |
| `#B58A1E` | **Gold stat number** — "63% avg accuracy" summary value | Session history·light |
| `#9A7616` | **Active-nav gold text** | Session history·light |
| `#8A6412` | **Premium-tag text on cream** | Progress·light |
| `#6E5E38` | **Warm text on cream coach card** — coach-summary body | Session review·light |
| `#E9C46A` | Still used at full brightness for **chart bars & shot-quality "Clean" segment** even on light (bars keep the bright gold) | Progress·light, Session review·light |
| `#2A2008` | On-gold text — still the label color on the `#E9C46A` primary button and active filter-pill/toggle in light | all light |

### Gold-tint containers (light warm surfaces)
| Hex | Role | Screen |
|---|---|---|
| `#FBF3DE` | Cream/gold chip bg — "Peak 74%" chip, coach-summary card bg, active-nav pill bg | Session review·light |
| `#ECDBAC` | Cream/gold chip border (with `#FBF3DE`) | Session review·light |
| `#FBF4DE` | Cream/gold tile+chip bg — gold KPI icon tiles, Premium tag bg, active-nav pill bg | Progress·light |
| `#EBDCAE` | Cream/gold border (with `#FBF4DE`) | Progress·light |
| `#FBF4E1` | Warm cream icon-tile bg (list rows) | Session history·light |
| `#F1E7C6` | Warm cream icon-tile border | Session history·light |
| `#FBF1D6` | Active-nav pill bg (warm) | Session history·light |

### Text tiers (light)
| Hex | Tier | Screen |
|---|---|---|
| `#1A1D24` | Primary text (near-black cool) | Session review·light |
| `#1A1D23` | Primary text (near-black) | Progress·light |
| `#181C23` | Primary text (warm near-black) | Session history·light |
| `#4E545F` | Secondary text | Session review·light |
| `#545B68` | Secondary text | Progress·light, Session history variants |
| `#5A616F` | Secondary text (chip labels) | Session history·light |
| `#868C98` | Muted/tertiary | Session review·light |
| `#868D99` | Muted/tertiary | Progress·light |
| `#71778A` | Muted/tertiary (warm) | Session history·light |
| `#9AA0AB` | Faint (inactive nav, unit suffixes) | Session review·light |
| `#A0A5AF` | Faint (chart axis labels, faint counts) | Session review·light |
| `#AEB4BF` | Faint (chart axis, weekend letters, chevrons) | Progress·light |
| `#A0A6B2` / `#C4C9D2` / `#C2C7D0` / `#B8BDC6` | Faintest (chevrons, meta dots, battery outline, disabled) | light screens |

### Success green (light)
| Hex | Role | Screen |
|---|---|---|
| `#1FA869` | Positive trend chip (+n) | Session review·light |
| `#2E9E63` | Positive skill-trend chip (+n) | Progress·light |
| `#12A05F` | Positive session-trend chip (+n) | Session history·light |
| `#3DBE7B` / `#3BAF6B` / `#37B37E` | Battery-full green (status bar) | light screens |

### Error red / coral (light)
| Hex | Role | Screen |
|---|---|---|
| `#C4463C` | "Top focus" tag text (deep red) | Session review·light |
| `#FDECEA` | "Top focus" tag bg (pale red) | Session review·light |
| `#F5C9C4` | "Top focus" tag border | Session review·light |
| `#CF5F57` | Negative skill-trend chip (−n) | Progress·light |
| `#D75148` | Negative session-trend chip (−n) | Session history·light |
| `#E8817A` | Error swatch + "Top focus" bar fill (kept from dark palette) | Session review·light |
| `#E9A25E` | "Late"/mid-tier amber (kept from dark palette) | Session review·light |

### Overlays (light)
| Value | Role | Screen |
|---|---|---|
| `rgba(0,0,0,.5)` | Phone-frame shadow (Session review·light reuses dark shadow) | Session review·light |
| `rgba(20,24,32,.16)` | Phone-frame shadow (softer) | Progress·light |
| `rgba(24,28,40,.16)` | Phone-frame shadow (softer, warm) | Session history·light |
| `rgba(154,111,15,.10)` | Deepened-gold area fill under accuracy curve | Session review·light |

---

## C. TYPOGRAPHY

Two families, declared bare in inline `font:` shorthand (no fallback in shorthand except the status bar):
- **`'Inter Tight'`** — primary UI/body font. Loaded weights **400, 500, 600, 700**.
- **`'JetBrains Mono'`** — monospace numerals font. Loaded weights **400, 500, 600** (some markup requests `700` mono → browser-synthesized bold; not in the Google Fonts import). Status-bar uses `'JetBrains Mono',monospace`.

**Where mono is used:** every numeral/quantity — stat numbers (128, 68%, 22), status-bar clock (11:58) & battery %, chart axis labels & day letters (M T W…), timestamps ("Yesterday · 18:30"), durations ("10–15 min"), trend deltas, slider readouts (×0.5, 6°, 3.5s), session counts, EN/UK & 24/30/60 toggle labels.
**Inter Tight** covers all prose: titles, labels, body, button text, pill text (except numeric pills).

### Distinct size / weight / line-height combos (by role)

**Large stat numbers (JetBrains Mono):**
| Font | Use |
|---|---|
| `700 28px 'JetBrains Mono';line-height:1` | Biggest hero stat (accuracy ring center "68") — unit suffix `700 15px` |
| `700 22px 'JetBrains Mono';line-height:1` | KPI card numbers (12, 26.5, 8) |
| `700 20px 'JetBrains Mono'` | Inset stat-strip numbers (128, 68%, 72%) |
| `700 18px 'JetBrains Mono'` | Summary-strip numbers (68%, 142, 22 + `700 12px` unit) |
| `600 15px 'JetBrains Mono'` | Session-row accuracy (68%) |
| `600 13px 'JetBrains Mono'` | Skill-level % (71%), slider readouts |

**Screen / section titles (Inter Tight, `letter-spacing:-.01em`):**
| Font | Use |
|---|---|
| `700 24px/1.1 'Inter Tight'` | Screen title (Progress) |
| `700 22px/1.15 'Inter Tight'` | Screen title (Settings) |
| `700 21px/1.15 'Inter Tight'` | Greeting name (Home "Yaroslav") |
| `700 20px/1.05 'Inter Tight'` | Review title (Forehand Drive) |
| `700 20px/1.1 'Inter Tight'` | Session history title |
| `700 19px 'Inter Tight'` | Sub-screen title (Feedback) |

**Card titles (Inter Tight):**
| Font | Use |
|---|---|
| `700 17px 'Inter Tight'` | Featured card title (Forehand Drive) |
| `700 16px 'Inter Tight'` | Coach-card name |
| `700 16px 'Inter Tight'` | Chart card title ("Weekly training") |
| `600 15px 'Inter Tight'` | List-row / settings-row title |
| `600 14.5px 'Inter Tight'` | Session-row title (history) |
| `600 14px 'Inter Tight'` | Skill-level name, generic row title |

**Body text (Inter Tight):**
| Font | Use |
|---|---|
| `400 13px/1.5 'Inter Tight'` | Tip / body copy |
| `400 13px/1.55 'Inter Tight'` | Coach-summary body |
| `500 14px 'Inter Tight'` | List-link label ("Session history") |
| `400 12px/1.4 'Inter Tight'` | Sub-descriptions under row titles |
| `400 12.5px/1.4 'Inter Tight'` | Exercise description |
| `500 12.5px 'Inter Tight'` | "Good evening" / meta |

**Secondary / caption (Inter Tight & Mono):**
| Font | Use |
|---|---|
| `400 12px 'Inter Tight'` | Captions, sub-labels |
| `400 11.5px/1.4 'Inter Tight'` | Settings sub-copy |
| `500 12px 'JetBrains Mono'` | Timestamps, counts |
| `500 11px 'JetBrains Mono'` | Meta numerals |

**Tiny labels / eyebrows / tags:**
| Font | Use |
|---|---|
| `700 10px 'Inter Tight';letter-spacing:.11em;text-transform:uppercase` | **Section eyebrow** — canonical section label ("LAST SESSION", "THIS WEEK") |
| `700 10.5px 'Inter Tight';letter-spacing:.12em;uppercase` | Settings section header ("AI COACH") |
| `700 11px 'Inter Tight';letter-spacing:.09em;uppercase;color:gold` | In-card gold subhead ("Coaching") |
| `500 10px 'Inter Tight'` | Bottom-nav labels (inactive) / `600 10px` active |
| `700 9px 'Inter Tight';letter-spacing:.08em;uppercase` | "Premium" tag |
| `700 8px 'Inter Tight';letter-spacing:.07em;uppercase` | "Top focus" micro-tag |
| `700 8.5px 'Inter Tight';letter-spacing:.15em;uppercase` | Review eyebrow ("SESSION REVIEW") |
| `500 10px 'JetBrains Mono'` / `500 9px` | Chart day-letters / axis ticks |

**Weights observed overall:** 400 (regular body), 500 (medium — labels/meta), 600 (semibold — row titles, active), 700 (bold — titles, stats, tags).
**Letter-spacing values:** `-.01em` (large titles), `.04em`, `.06em`, `.07em`, `.08em`, `.09em`, `.11em`, `.12em`, `.13em`, `.15em` (uppercase eyebrows, wider the smaller the label).

---

## D. SHAPE / RADII

| Radius | Applied to |
|---|---|
| `26px` | **Phone frame** (`border-radius:26px`). |
| `16px` | **Extended FAB** (pill-ish rounded rect, `padding:14px 18px`). |
| `14px` | **Cards** — the standard card radius (all content cards, coach card, KPI cards). |
| `12px` | **Inset strips / list rows / swipe rows / segmented track / Tip card / coach card (analytics)** — secondary container radius. |
| `11px` | **Primary buttons / avatar icon-tiles (46px)** (`border-radius:11px`). |
| `10px` | **Icon tiles (38px), inset stat strip, list-link rows, secondary/ghost buttons, swipe-action panels** (`border-radius:10px`). |
| `9px` | **Small icon tiles (34px), Progress segmented pill inner** (`border-radius:9px`). |
| `999px` | **Pills / chips / segmented controls / progress bars & troughs / trend chips / tags / status pills / active-nav pill** (fully rounded). |
| `50%` | **Avatars, circular icon tiles (40px), round FAB (46px), toggle knob, focus dots, slider thumb, chart end-dots, meta separator dots.** |
| `3px` | **Chart bars** (`border-radius:3px`) and small battery-glyph rects. |
| `2px` | **Legend swatches** (8×8 rounded squares) and heatmap cells (`border-radius:3px`). |
| Split corners | **Swipe rows:** clone panel `border-radius:0 12px 12px 0`; delete panel `border-radius:12px 0 0 12px` (the action reveals under the sliding `12px` row). |

---

## E. SPACING

| Value | Where |
|---|---|
| `16px` | **Screen horizontal padding** (`padding:… 16px`), card inner padding (`padding:16px`). The base unit. |
| `14px` | Card gap in column stacks (Home content `gap:14px`), card padding on small cards (`padding:14px`), extended-FAB vertical padding. |
| `12px` | Content-block gaps, coach-card/Tip padding (`padding:14px`→`15px` variants), row gaps. |
| `10px` | KPI-row gap, CTA-button gap, section-to-content gap. |
| `13px` | Card padding on swipe rows (`padding:13px`), row inner gaps. |
| `11px` | Inset-strip cell padding (`padding:11px 12px`). |
| `9px / 8px / 7px` | Icon↔label gaps, chart bar gaps, small element gaps. |
| Status bar | `padding:9px 16px 6px` (top 9, sides 16, bottom 6). |
| Header | `padding:12px 16px 4px` (Home) / `13px 16px 4px` (Settings) / `14px 16px 2px` (Progress). |
| Bottom nav | `padding:9px 6px 12px`. |
| Content block | e.g. Home `padding:12px 16px 88px` (extra bottom for FAB clearance). |
| Pills | active pill `padding:8px 0` (segmented) / `6px 15px` (labeled) / `3px 8px` (Premium tag) / `3px 9px` (chip) / `2px 7px` (micro-tag) / `7px 17px` (EN/UK). |

---

## F. COMPONENT INLINE-STYLE RECIPES (verbatim)

### 1. Card / surface container (DARK)
```
background:#141820;border:1px solid #232833;border-radius:14px;padding:16px;
display:flex;flex-direction:column;gap:14px
```
Light equivalent (Progress·light): `background:#F7F8FA;border:1px solid #E4E6EC;border-radius:14px;padding:16px`
Light equivalent (Review·light): `background:#FFFFFF;border:1px solid #E4E7EC;border-radius:14px;padding:16px`

### 2. Segmented pill / chip — INACTIVE vs ACTIVE (DARK)
Track: `display:flex;gap:4px;background:#0B0E12;border:1px solid #232833;border-radius:999px;padding:4px`
- **INACTIVE segment:**
```
flex:1;text-align:center;padding:8px 0;border-radius:999px;border:1px solid transparent;
font:600 13px 'Inter Tight';color:#B4BAC5
```
- **ACTIVE segment:**
```
flex:1;text-align:center;padding:8px 0;border-radius:999px;background:#221C0F;
border:1px solid #5C4A22;font:700 13px 'Inter Tight';color:#E9C46A
```
Labeled variant (Feedback "Right/Left"): active `padding:6px 15px;border-radius:999px;background:#221C0F;border:1px solid #5C4A22;font:700 12.5px 'Inter Tight';color:#E9C46A`; inactive `padding:6px 15px;border-radius:999px;border:1px solid transparent;font:600 12.5px 'Inter Tight';color:#B4BAC5`.
Chart-toggle (Progress) active: `font:700 12.5px 'Inter Tight';color:#2A2008;background:#E9C46A;padding:9px 6px;border-radius:9px` (this one uses a SOLID gold fill, not the tinted container).
LIGHT filter chip inactive (history): `color:#5A616F;background:#FFFFFF;border:1px solid #E2E5EA;border-radius:999px;padding:6px 13px`; active: `color:#2A2008;background:#E9C46A;border-radius:999px;padding:7px 14px`.

### 3. Primary button / FAB (gold fill)
- **Primary button (Analytics CTA):**
```
flex:1;padding:13px;background:#E9C46A;border:none;border-radius:11px;color:#2A2008;
font:700 14px 'Inter Tight';cursor:pointer;display:flex;align-items:center;
justify-content:center;gap:8px
```
(SVG icon inside uses `stroke="#2A2008"`.)
- **Full-width primary button (swipe "Continue training"):** `width:100%;padding:13px;background:#E9C46A;border:none;border-radius:10px;color:#2A2008;font:700 14px 'Inter Tight'`
- **Extended FAB (Home "New session"):**
```
position:absolute;right:16px;bottom:86px;display:flex;align-items:center;gap:9px;
background:#E9C46A;border-radius:16px;padding:14px 18px;
box-shadow:0 12px 26px rgba(0,0,0,.45);cursor:pointer
```
label: `font:700 14px 'Inter Tight';color:#2A2008`
- **Round FAB (Exercises, 46px, with gold glow):**
```
position:absolute;right:16px;bottom:78px;width:46px;height:46px;border-radius:50%;
background:#E9C46A;border:none;cursor:pointer;display:flex;align-items:center;
justify-content:center;box-shadow:0 10px 20px -6px rgba(233,196,106,.5),0 3px 8px rgba(0,0,0,.5)
```
icon: `stroke="#2A2008" stroke-width="2.3"`

### 4. Secondary / ghost button (DARK)
- **Outline ghost (filled dark bg) — "Overview":**
```
padding:13px 15px;background:#141820;border:1px solid #232833;border-radius:11px;
color:#B4BAC5;font:600 14px 'Inter Tight';text-decoration:none;display:flex;align-items:center;gap:7px
```
- **Transparent ghost — "View all 5 focus areas":**
```
display:flex;align-items:center;justify-content:center;gap:8px;background:transparent;
border:1px solid #232833;border-radius:10px;padding:11px;color:#B4BAC5;font:600 13px 'Inter Tight'
```
LIGHT ghost (review): `background:#FFFFFF;border:1px solid #E4E7EC;…;color:#4E545F` and transparent variant `background:transparent;border:1px solid #E4E7EC;…;color:#4E545F`.

### 5. Section header / label (eyebrow) (DARK)
```
font:700 10px 'Inter Tight';letter-spacing:.11em;text-transform:uppercase;color:#7F8694
```
Settings-style (with leading gold icon): `font:700 10.5px 'Inter Tight';letter-spacing:.12em;text-transform:uppercase;color:#7F8694` next to a `fill="#E9C46A"` / `stroke="#E9C46A"` 13px icon.
In-card gold subhead: `font:700 11px 'Inter Tight';letter-spacing:.09em;text-transform:uppercase;color:#E9C46A`.

### 6. Large stat number (DARK)
```
font:700 20px 'JetBrains Mono';color:#E7EAF0     (neutral stat)
font:700 20px 'JetBrains Mono';color:#E9C46A     (gold/accuracy stat)
font:700 20px 'JetBrains Mono';color:#9BE3A6     (positive/consistency stat)
```
paired label: `font:500 10px 'Inter Tight';letter-spacing:.04em;text-transform:uppercase;color:#7F8694`
Hero ring stat: `font:700 28px 'JetBrains Mono';color:#E7EAF0;line-height:1` with unit `font:700 15px 'JetBrains Mono'`.
KPI card: `font:700 22px 'JetBrains Mono';color:#E7EAF0;line-height:1`.

### 7. Trend chip — POSITIVE (green) vs NEGATIVE (coral)
- **POSITIVE (DARK):** container `display:flex;align-items:center;gap:3px;font:600 11px 'JetBrains Mono';color:#2FD08A` + up-triangle SVG `fill="#2FD08A"` path `d="M5 1.5 9 8 1 8Z"`.
- **NEGATIVE (DARK):** `…color:#E8817A` + down-triangle SVG `fill="#E8817A"` path `d="M5 8.5 1 2 9 2Z"`.
- **POSITIVE (LIGHT):** `#1FA869` (review) / `#2E9E63` (progress) / `#12A05F` (history), same triangle.
- **NEGATIVE (LIGHT):** `#CF5F57` (progress) / `#D75148` (history), same down-triangle.
Summary-strip variant is `font:600 10.5px 'JetBrains Mono'`.
Related: coral "Top focus" TAG (dark) `font:700 8px 'Inter Tight';letter-spacing:.07em;text-transform:uppercase;color:#E8817A;background:#2A1512;border:1px solid #5A2B27;border-radius:999px;padding:2px 7px`; LIGHT version `color:#C4463C;background:#FDECEA;border:1px solid #F5C9C4`.

### 8. Toggle / switch (DARK) — ON state
Track (ON):
```
width:44px;height:26px;border-radius:999px;background:#E9C46A;position:relative;flex:none
```
Knob (ON, sits right):
```
position:absolute;top:3px;right:3px;width:20px;height:20px;border-radius:50%;background:#16130B
```
(No explicit OFF state shown in the mockups — infer: track `#0B0E12`/`#232833` border, knob `#565C68`/`#7F8694`, knob at `left:3px`.)

### 9. Slider (DARK)
```
container:  position:relative;height:18px;display:flex;align-items:center;margin-top:3px
track:      height:5px;border-radius:999px;background:#0B0E12;width:100%
fill:       position:absolute;left:0;height:5px;width:50%;border-radius:999px;background:#E9C46A
thumb:      position:absolute;left:50%;transform:translateX(-50%);width:15px;height:15px;
            border-radius:50%;background:#E9C46A;
            box-shadow:0 0 0 3px #0E1115,0 0 0 4px #5C4A22
```
(Thumb has a double ring: 3px canvas-color halo `#0E1115` then a 1px gold-tint ring `#5C4A22`.)
Readout label above: `font:600 13px 'JetBrains Mono';color:#E9C46A` (e.g. `×0.5`, `6°`, `3.5s`).

---

## Quick Android mapping cheat-sheet (DARK canonical)

| Token name (suggested) | Hex |
|---|---|
| `colorBackground` | `#0E1115` |
| `colorBackgroundSink` | `#0B0E12` |
| `colorSurface` (card) | `#141820` |
| `colorSurfaceElevated` | `#1A1F28` |
| `colorSurfaceElevated2` | `#171B22` |
| `colorOutline` (border) | `#232833` |
| `colorOutlineStrong` | `#2F3542` |
| `colorGold` (accent) | `#E9C46A` |
| `colorGoldContainer` | `#221C0F` |
| `colorGoldOutline` | `#5C4A22` |
| `colorOnGold` | `#2A2008` |
| `colorOnGoldKnob` | `#16130B` |
| `colorGoldDeep` | `#A9862F` |
| `colorTextPrimary` | `#E7EAF0` |
| `colorTextSecondary` | `#B4BAC5` |
| `colorTextMuted` | `#7F8694` |
| `colorTextFaint` | `#565C68` |
| `colorSuccess` | `#2FD08A` |
| `colorSuccessSoft` (battery) | `#9BE3A6` |
| `colorError` (coral) | `#E8817A` |
| `colorErrorContainer` (delete row) | `#3A1A18` |
| `colorErrorChipBg` | `#2A1512` |
| `colorErrorChipBorder` | `#5A2B27` |
| `colorWarn` (amber/Late) | `#E9A25E` |
| `colorCream` (warm tile) | `#FBF3DE` |
| `goldGlow` | `rgba(233,196,106,.5)` |

| Token name (LIGHT) | Hex |
|---|---|
| `colorBackground` | `#FFFFFF` / `#F5F6F8` |
| `colorSurface` | `#F7F8FA` / `#FFFFFF` |
| `colorSurfaceInset` | `#F1F3F6` / `#EFF1F4` |
| `colorOutline` | `#E4E6EC` / `#E4E7EC` |
| `colorGold` (accent) | `#9A6F0F` / `#997219` |
| `colorGoldContainer` | `#FBF4DE` / `#FBF3DE` |
| `colorGoldOutline` | `#EBDCAE` / `#ECDBAC` |
| `colorTextPrimary` | `#1A1D23` / `#1A1D24` / `#181C23` |
| `colorTextSecondary` | `#545B68` / `#4E545F` |
| `colorTextMuted` | `#868D99` / `#868C98` / `#71778A` |
| `colorSuccess` | `#12A05F` / `#2E9E63` / `#1FA869` |
| `colorError` | `#D75148` / `#CF5F57` / `#C4463C` |
