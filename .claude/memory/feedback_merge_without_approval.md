---
name: feedback_merge_without_approval
description: User wants merges performed automatically without asking for approval first
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 60d101d4-b69f-43df-b08e-4bc5e71db80a
---

When work on a feature/worktree branch is complete and verified, merge it back into the target branch (e.g. `2d-experiments`) **without pausing to ask for approval**. The user said "always merge without approve."

**Why:** the user runs multiple concurrent sessions on a shared branch and doesn't want the final integration to block on a confirmation round-trip.

**How to apply:** After the slice is green (build + tests + final review fixes), just do the merge. If the target working tree has uncommitted concurrent WIP that blocks it, `git stash -u` the WIP, merge (resolving additive `strings.xml`/resource conflicts by unioning both sides), then `git stash pop` and report any pop conflicts for the other session to resolve — don't stop to ask first. Pushing is still separate and needs the gh account switch ([[project_git_push_auth]]). Related: [[feedback_commit_cadence]], [[feedback_scope_discipline]].
