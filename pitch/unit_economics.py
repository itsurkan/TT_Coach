#!/usr/bin/env python3
"""TT Coach AI — unit economics calculator.

Single source of truth for the numbers on the «Юніт-економіка» slide in
pitch/TT_Coach_AI_pitch.html. Defaults are the conservative base model;
override any input via CLI flags. If the model logic changes (new cost
lines, new assumptions), change it HERE first, rerun, then sync the slide.

Usage:
  python3 pitch/unit_economics.py                       # conservative base
  python3 pitch/unit_economics.py --arpu 10 --churn 0.08
  python3 pitch/unit_economics.py --lifetime-months 14  # override churn-derived lifetime
  python3 pitch/unit_economics.py --json
"""

import argparse
import json
import math
import sys
from decimal import Decimal, ROUND_HALF_UP


def r1(x):
    """Half-up rounding to 1 decimal (avoids float 1.45 -> 1.4)."""
    return Decimal(str(x)).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)


def compute(arpu, churn, lifetime_months, store_fee, ai_cost_share, ai_cost_usd,
            cac_paid, cac_blended):
    """Pure model. Returns a dict with every derived metric."""
    if lifetime_months is None:
        if not 0 < churn < 1:
            raise ValueError("churn must be in (0, 1) when lifetime is not given")
        lifetime_months = 1.0 / churn
    implied_churn = 1.0 / lifetime_months

    if ai_cost_usd is not None:
        ai_cost_share = ai_cost_usd / arpu

    gross_margin = 1.0 - store_fee - ai_cost_share
    if gross_margin <= 0:
        raise ValueError("costs exceed revenue: gross margin <= 0")

    margin_per_month = arpu * gross_margin
    ltv = margin_per_month * lifetime_months

    def ratio(cac):
        return ltv / cac if cac else math.inf

    def payback(cac):
        return cac / margin_per_month if margin_per_month else math.inf

    return {
        "inputs": {
            "arpu_usd_month": arpu,
            "churn_monthly": round(implied_churn, 4),
            "lifetime_months": round(lifetime_months, 2),
            "store_fee_share": store_fee,
            "ai_report_cost_share": round(ai_cost_share, 4),
            "cac_paid_usd": cac_paid,
            "cac_blended_usd": cac_blended,
        },
        "outputs": {
            "gross_margin_pct": round(gross_margin * 100, 1),
            "margin_usd_month": round(margin_per_month, 2),
            "ltv_usd": round(ltv, 2),
            "ltv_cac_paid": round(ratio(cac_paid), 2),
            "ltv_cac_blended": round(ratio(cac_blended), 2),
            "payback_months_paid": round(payback(cac_paid), 1),
            "payback_months_blended": round(payback(cac_blended), 1),
        },
    }


def slide_strings(r):
    """The rounded forms used verbatim on the pitch slide."""
    i, o = r["inputs"], r["outputs"]
    return {
        "wedge": (f"${i['arpu_usd_month']:g} ARPU/міс × {i['lifetime_months']:g} міс "
                  f"(~{i['churn_monthly'] * 100:.0f}% churn) × {o['gross_margin_pct']:.0f}% маржа "
                  f"= ~${o['ltv_usd']:.0f} LTV"),
        "cac": f"${i['cac_paid_usd']:g} paid → ~${i['cac_blended_usd']:g} blended",
        "ltv_cac": f"~{r1(o['ltv_cac_blended'])}× (blended) · {r1(o['ltv_cac_paid'])}× (paid)",
        "payback": (f"~{o['payback_months_blended']:.0f} міс (blended) · "
                    f"~{o['payback_months_paid']:.0f} міс (paid)"),
    }


def main():
    p = argparse.ArgumentParser(description="TT Coach AI unit economics")
    p.add_argument("--arpu", type=float, default=8.0, help="USD per subscriber per month")
    p.add_argument("--churn", type=float, default=0.11, help="monthly churn share, e.g. 0.11")
    p.add_argument("--lifetime-months", type=float, default=None,
                   help="override lifetime directly (ignores --churn)")
    p.add_argument("--store-fee", type=float, default=0.15, help="app store share of revenue")
    p.add_argument("--ai-cost-share", type=float, default=0.05,
                   help="cloud AI post-game reports, share of revenue")
    p.add_argument("--ai-cost-usd", type=float, default=None,
                   help="cloud AI cost in USD/subscriber/month (overrides --ai-cost-share)")
    p.add_argument("--cac-paid", type=float, default=70.0, help="pure paid CAC, USD")
    p.add_argument("--cac-blended", type=float, default=40.0, help="blended CAC, USD")
    p.add_argument("--json", action="store_true", help="machine-readable output")
    a = p.parse_args()

    try:
        r = compute(a.arpu, a.churn, a.lifetime_months, a.store_fee,
                    a.ai_cost_share, a.ai_cost_usd, a.cac_paid, a.cac_blended)
    except ValueError as e:
        sys.exit(f"error: {e}")

    if a.json:
        print(json.dumps({**r, "slide": slide_strings(r)}, ensure_ascii=False, indent=2))
        return

    i, o, s = r["inputs"], r["outputs"], slide_strings(r)
    w = 34
    print("TT Coach AI — unit economics (per subscriber)")
    print("-" * 56)
    print(f"{'ARPU':<{w}} ${i['arpu_usd_month']:g}/міс")
    print(f"{'Churn / lifetime':<{w}} {i['churn_monthly'] * 100:.1f}%/міс → {i['lifetime_months']:g} міс")
    print(f"{'Store fee':<{w}} {i['store_fee_share'] * 100:.0f}%")
    print(f"{'AI post-game reports':<{w}} {i['ai_report_cost_share'] * 100:.1f}% of revenue")
    print(f"{'Gross margin':<{w}} {o['gross_margin_pct']:.1f}%  (${o['margin_usd_month']:.2f}/міс)")
    print("-" * 56)
    print(f"{'LTV':<{w}} ${o['ltv_usd']:.2f}")
    print(f"{'LTV:CAC  paid (${:g})'.format(i['cac_paid_usd']):<{w}} {o['ltv_cac_paid']:.2f}×")
    print(f"{'LTV:CAC  blended (${:g})'.format(i['cac_blended_usd']):<{w}} {o['ltv_cac_blended']:.2f}×")
    print(f"{'Payback  paid':<{w}} {o['payback_months_paid']:.1f} міс")
    print(f"{'Payback  blended':<{w}} {o['payback_months_blended']:.1f} міс")
    print("-" * 56)
    print("Slide strings (pitch/TT_Coach_AI_pitch.html, слайд «Юніт-економіка»):")
    for k, v in s.items():
        print(f"  {k:<8} {v}")
    if o["ltv_cac_blended"] < 3:
        print("\nnote: blended LTV:CAC < 3× — слайд має показувати шлях до 3×"
              " (річні плани ↑ лайфтайм, органіка ↓ CAC).")


if __name__ == "__main__":
    main()
