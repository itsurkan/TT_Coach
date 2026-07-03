---
name: feedback_plan_execution_subagents
description: Always execute implementation plans via subagent-driven-development; never ask which execution approach
metadata: 
  node_type: memory
  type: feedback
  originSessionId: cc279f2d-2eed-4fe2-b71d-96b40567c084
---

Always execute written implementation plans via `superpowers:subagent-driven-development` — fresh subagent per task, review between tasks. Never run plan tasks inline in the main session, and never ask the user which execution approach to use.

**Why:** Ivan considers subagent-driven the standing default (it's also in CLAUDE.md). Asking "which approach?" wastes a turn and ignores an established rule — he called it out after I offered the choice.

**How to apply:** When a plan is ready, go straight into `subagent-driven-development`. This OVERRIDES the `writing-plans` / `executing-plans` skills' "offer execution choice" final step — skip that prompt entirely. Related: [[feedback_subagents]] (prefer subagents for exploration/research to keep main context clean).
