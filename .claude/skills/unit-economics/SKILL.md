---
name: unit-economics
description: Use when calculating, checking, or changing TT Coach AI unit economics — "порахуй юніт-економіку", LTV/CAC/payback/churn/margin questions, "what if ARPU is X", or ANY edit to the «Юніт-економіка» slide in pitch/TT_Coach_AI_pitch.html. The script pitch/unit_economics.py is the single source of truth; the slide only displays its output.
---

# Unit economics (TT Coach AI)

Single source of truth: **`pitch/unit_economics.py`**. The «Юніт-економіка» slide in
`pitch/TT_Coach_AI_pitch.html` must always display numbers produced by this script —
never hand-compute slide numbers.

## Calculate

```bash
python3 pitch/unit_economics.py            # conservative base (deck defaults)
python3 pitch/unit_economics.py --json     # machine-readable, includes slide strings
```

What-if — override any input:

```bash
python3 pitch/unit_economics.py --arpu 10 --churn 0.08 --cac-blended 35
python3 pitch/unit_economics.py --lifetime-months 14        # bypass churn-derived lifetime
python3 pitch/unit_economics.py --ai-cost-usd 0.5           # AI reports as absolute $/sub/міс
```

Inputs: `--arpu` (USD/міс), `--churn` (monthly share) or `--lifetime-months`,
`--store-fee`, `--ai-cost-share` or `--ai-cost-usd`, `--cac-paid`, `--cac-blended`.
Outputs: gross margin, margin $/міс, LTV, LTV:CAC (paid + blended), payback months
(paid + blended), plus ready-to-paste `slide` strings.

## Sync rule (MANDATORY, both directions)

The model logic and the deck must never diverge:

1. **Logic/assumption change** (new cost line, different churn model, annual-plan mix,
   new default): edit `pitch/unit_economics.py` FIRST → run it → copy the numbers /
   `slide` strings into the «Юніт-економіка» slide (wedge + three stat cards) →
   commit script + deck together in one commit.
2. **Deck-driven change** (user tweaks a number on the slide): update the script's
   defaults to match, rerun, and verify the slide's derived numbers (LTV, LTV:CAC,
   payback) — fix the slide if they don't reproduce.
3. The script prints a `note:` when blended LTV:CAC < 3× — the slide must then keep
   the "шлях до 3×" sub-label (річні плани ↑ лайфтайм, органіка ↓ CAC).

Current conservative base (defaults): ARPU $8/міс · churn 11%/міс (≈9 міс lifetime) ·
store fee 15% · AI post-game reports 5% of revenue → 80% margin · CAC $70 paid / $40 blended
→ LTV ~$58 · LTV:CAC ~1.5× blended · payback ~6 міс blended.

## Verify after any change

```bash
python3 pitch/unit_economics.py            # numbers reproduce the slide
python3 pitch/unit_economics.py --json     # slide strings match deck text
```

Then open the deck (`pitch/TT_Coach_AI_pitch.html`, slide «Юніт-економіка») and compare
the wedge and three cards against the script output before committing.
