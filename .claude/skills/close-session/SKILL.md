---
name: close-session
description: Use when the user says "close session", "wrap up session", "session retro", or otherwise wants an end-of-session retrospective that improves the custom skills exercised this session so they run faster and with fewer errors next time.
---

# Close Session

## Overview

A session-end retrospective for **this repo's own custom skills**. Reviews the custom
skills actually used this session, finds where each caused friction, and proposes
surgical edits so next time is faster and less error-prone. Propose first, apply only
after the user confirms.

**Core principle:** every proposed edit must trace to a concrete friction event that
happened *this session*. No observed friction → no edit. Never invent improvements.

## Scope — which skills are eligible

Edit only **our** skills:
- Repo skills in `.claude/skills/` (fixture-pipeline, phone-screenshot, rtmpose-export,
  run-on-phone, video-added, viewer-qa, viewer-ui-test, visualize-pose)
- Personal plugin skills we own (mongo-query, tm-build-sdk-package, tm-migration)

Never edit superpowers / built-in skills (brainstorming, writing-plans, TDD, etc.) — not ours.

## Workflow

1. **List custom skills used this session.** From the live conversation only — no log parsing.
   If none of our skills were used, say so and stop.
2. **Recall friction per skill.** Ground each item in a real event this session:
   - a command that errored or needed a retry
   - a wrong/guessed path or missing precondition
   - an ambiguous instruction that led to a wrong turn
   - a manual step that was slow or could be scripted
   If a used skill ran clean, record "no friction" and propose nothing for it.
3. **Draft minimal edits.** One targeted change per friction point. Each edit cites the
   event that motivates it (e.g. "`export_new.py` errored on loose mp4 → add tidy-first note").
   Keep edits surgical — a note, a corrected flag, a precondition line. No rewrites.
4. **Show the proposal, then confirm.** Present as a short list: `skill → change → because`.
   Apply with Edit only after the user approves. Never auto-apply.
5. **Offer to commit.** On approval, commit with explicit paths (never `git add -A`).

## Quick reference

| Step | Output |
|------|--------|
| Used-skills scan | list of our skills touched this session |
| Friction pass | per-skill: concrete events, or "clean" |
| Proposal | `skill → surgical edit → motivating event` |
| Apply | Edit approved items only, then optional explicit-path commit |

## Common mistakes

- **Speculative edits** — improving a skill that ran fine, or fixing a "future" problem
  not seen this session. Don't. Scope is observed friction only.
- **Editing built-ins** — superpowers/plugin skills are out of scope.
- **Rewrites instead of notes** — prefer the smallest change that prevents the error.
- **Touching frozen pipeline or unrelated repo files** — this skill edits SKILL.md files only.
- **Applying before confirming** — always propose first.
