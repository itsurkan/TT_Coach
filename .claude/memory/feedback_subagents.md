---
name: Prefer subagents to protect context
description: Always delegate to subagents (Explore, general-purpose, Plan, etc.) when the work would otherwise pull lots of file content or search output into the main context
type: feedback
originSessionId: 1dc95051-972b-4656-a414-918fe7829b91
---
Always reach for subagents (Agent tool — Explore, general-purpose, Plan, code-reviewer, etc.) when delegation would avoid loading large amounts of content into the main context.

**Why:** Ivan wants the main conversation context preserved for decision-making and synthesis, not consumed by raw search results, file dumps, or multi-step exploration. Subagents return a compact summary instead of the full intermediate output, which keeps token usage efficient and prevents context blow-out on long sessions.

**Standing rule (2026-06-12):** Always use subagents for plan execution — never execute implementation-plan tasks inline in the main session. **Never ask the user which execution approach to use (subagent-driven vs inline); subagent-driven is the permanent default — just proceed.**

**How to apply:**
- Executing a written implementation plan → subagent-driven-development (fresh subagent per task, review between), no approach question.
- Codebase exploration spanning more than ~3 file reads or grep queries → spawn `Explore` subagent (specify thoroughness: quick / medium / very thorough).
- Open-ended research questions ("how does X work?", "where is Y handled?") → `general-purpose` subagent.
- Multi-step implementation planning before touching code → `Plan` subagent.
- Independent parallel tasks (e.g. researching two unrelated files) → dispatch multiple subagents in a single message.
- Direct, targeted lookups (one known file path, one specific grep) → still use Read/Bash directly; don't spawn an agent for trivial work.
- Always brief subagents with full self-contained context — they don't see the conversation.
