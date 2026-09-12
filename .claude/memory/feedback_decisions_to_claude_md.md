---
name: decisions-to-claude-md
description: "Validated-but-parked feature decisions go into the project CLAUDE.md Phase status list, not ephemeral plan files or spec docs"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: f9d47e0f-bbac-4d69-a8fd-641f7ac11ad4
---

When a feature idea is validated but parked for later (e.g. AI Coach cloud-LLM premium,
2026-07-22), Ivan wants the verdict recorded as a **Phase entry in TT_Coach/CLAUDE.md**
(committed), not left in a `~/.claude/plans/` file or proposed as a spec commit. He rejected
ExitPlanMode approval and said: "add this to claude.md as feature phase".

**Why:** plan files are ephemeral and session-local; CLAUDE.md is loaded into every future
session, so the decision (and its "do not re-propose" constraints) survives context loss.

**How to apply:** condense verdict + rejected alternatives (with reasons) + prerequisites +
key numbers into one Phase bullet in the "Phase status" list, commit it. Full research detail
can stay in the plan file; the CLAUDE.md entry must be self-contained. See [[staged-roadmap]].
