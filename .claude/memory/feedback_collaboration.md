---
name: Collaboration preferences — honest estimates over cheerleading
description: How Ivan wants Claude Code to respond — favour honest pushback and concrete breakdowns over validation, use structured questions for alignment moments.
type: feedback
originSessionId: 51fe4f63-cc7e-4a09-84eb-927adff4f560
---
**Give honest, concrete estimates. Push back on overly optimistic scope/timelines — don't cheerlead.**

**Why:** Ivan repeatedly framed requests as "analyze this approach", "give fair estimate", "наскільки це потрібно". When told a 2-week Play Store release for full Stage 1 was unrealistic with his chosen scope, he absorbed the feedback and narrowed scope rather than objecting. When his "Claude Code 10×" optimism was countered with a concrete list of what Claude Code does and doesn't accelerate (empirical calibration, UX iteration, Play Store review are not 10× wins), he accepted the more realistic framing without pushback. Cheerleading wastes his time; honest concrete analysis earns trust.

**How to apply:**
- When the user proposes a timeline or scope, run it through a reality check before agreeing. If it's aspirational, say so explicitly and give a numbered breakdown of what's actually feasible in the time.
- Frame pushback as "honest флаг" / "чесно" / explicit caveat — he treats this as a feature, not friction.
- Prefer concrete day-by-day or component-by-component breakdowns over vague "it'll take longer".
- Do NOT soften estimates to be nice. Don't hedge with "it depends" when you have a real opinion.

---

**Use AskUserQuestion (structured mode) for multi-decision alignment moments.**

**Why:** Ivan explicitly asked for "режим питань" (question mode) when aligning on the staged plan. He engaged well with 4-question batches that had 2-4 options each, especially when each option had "(Recommended)" marking the low-risk default. This format helps him make 4 decisions in 1 turn instead of 4 text exchanges.

**How to apply:**
- When there are 2+ independent decisions needed before proceeding, batch them into a single AskUserQuestion call with 2-4 questions.
- Always include a "(Recommended)" option as the first choice and state why it's the safer default in the description.
- Keep options mutually exclusive (single-select) unless genuinely orthogonal (then multiSelect).

---

**Ukrainian for conceptual/strategic discussion; English OK for technical detail.**

**Why:** He switched to Ukrainian when the conversation moved from "how to build X" to "what product are we even making." Conceptual alignment apparently feels more natural in Ukrainian. Technical details (file paths, commit messages, code) he keeps in English.

**How to apply:**
- If he writes in Ukrainian, respond in Ukrainian — don't translate back.
- Code, file paths, CLI commands, commit messages: English.
- Documents checked into the repo (`docs/`, `specs/`, `CLAUDE.md`): English by default unless he explicitly asks for Ukrainian.

---

**Commit hygiene needs encouragement (do not save as "grief" — just watch).**

**Why:** Recent commit history before my arrival had terse messages like "update", "big move 2", "BIG MOVE", "clean". I pointed this out as a workflow issue; he acknowledged by creating `init 003` (better, but still terse). The subsequent commits I drafted (`build:`, `test:`, `docs:` with full bodies) he accepted without edits. TBD whether he adopts the pattern independently.

**How to apply:**
- When drafting commit messages for him, always explain the why in the body, not just the what. Use type prefixes (`build:`, `test:`, `docs:`, `feat:`, `fix:`) so messages are skimmable later.
- If he writes a terse commit himself, don't lecture — but gently model better messages in subsequent ones.
