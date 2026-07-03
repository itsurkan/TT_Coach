---
name: Commit after each change
description: User wants a git commit after each logical change/step, not batched end-of-task commits
type: feedback
originSessionId: d37858d3-f4c0-446c-aceb-aea577626172
---
Commit after each logical change rather than waiting for the end of a task.

**Why:** User said "commit after each change" at the end of Phase 0 of 003-stage1-calibration — they want the git history to reflect incremental progress, not one bulk commit per feature. This also aligns with the kickoff brief guidance "2-3 commits if it grows" and makes rollback/review easier.

**How to apply:**
- After each self-contained step (new data class, new function + its tests, a bug fix, a docs update), make a commit before moving on. Don't batch.
- Still observe the usual guardrails: don't commit unless asked for the *first* commit in a session; after this feedback is saved, subsequent logical chunks can be committed without asking each time, but a new session starts fresh (re-confirm).
- Prefer descriptive messages per CLAUDE.md ("update" is not enough).
- If a step is trivially small (one-line change), it's fine to bundle with the next related step — the rule is "per logical change," not "per file."
