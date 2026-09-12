---
name: feedback_docs_plans_lean_mode
description: "Docs/pitch/report plans must NOT run full SDD (per-task review+fix+re-review); use lean mode — one writer per file in parallel, one final review, one fix — and state expected token cost up front"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 6ddaca51-c78d-43f8-b805-b03dc1a14364
  modified: 2026-09-12T11:29:47.315Z
---

On 2026-09-12 the investor feature-analysis plan (3 docs: inventory, UA deck, UA risk report)
ran under full subagent-driven development: 16 sequential subagent calls, ~2.3M tokens, >1 hour.
Ivan's verdict: "взагалі не виправдані" витрати. Only ~4 calls wrote output; 12 were review
ceremony that re-read the same repo files 4–5 times.

**Why:** the CLAUDE.md "always SDD, never ask" rule was written for code, where per-task
regressions matter. For documents a single end review catches the same errors (wrong
citations, cross-doc contradictions) at ~1/6 of the cost.

**How to apply:** for any plan whose outputs are docs/decks/reports:
- One writer subagent per output file, dispatched IN PARALLEL when inputs allow (deck and
  risk report both depend only on the inventory — run them together; append evaluator
  points as a short follow-up step). Consider a Workflow script for the fan-out.
- Verify code evidence ONCE (the inventory); downstream writers and reviewers get the
  inventory + diff only, no source access, no re-verification.
- One whole-branch review at the end (sonnet), one fix dispatch, one haiku scoped re-check.
- Say the expected cost in the first message ("~600–800k tokens, ~20 min") and offer lean vs
  full before starting. This overrides the "never ask which execution approach" rule for
  docs plans only. See [[feedback_subagents]], [[feedback_collaboration]].
