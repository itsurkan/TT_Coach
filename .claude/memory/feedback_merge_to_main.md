---
name: feedback_merge_to_main
description: "After finishing branch work, always fast-forward/merge to main without asking; never open a PR or pause for a merge decision"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 4dfd7580-f07b-48d6-9a9c-dbe916042ea5
---

When a feature branch's work is complete and green, merge it into `main` automatically —
do not ask, do not open a PR, do not present merge/PR/cleanup options.

**Why:** Ivan is a solo dev on TT_Coach; the SDD "finishing-a-development-branch" prompt
and my habit of asking "merge, PR, or leave it?" is wasted friction for him.

**How to apply:** After the final verification passes, `git checkout main` and
`git merge --ff-only <branch>` (or a merge commit if not fast-forwardable) as the last step,
then report it as done. Still respect [[feedback_never_revert_uncommitted]] — carry
uncommitted concurrent edits across the checkout untouched. Pushing still needs the gh
account switch ([[project_git_push_auth]]); merge locally, don't auto-push unless asked.
