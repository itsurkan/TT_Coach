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

**Exception — main diverged / concurrent session active (2026-07-03):** the "never ask, just
merge" rule assumes a clean fast-forward. When `main` has moved under a long-running branch
(another session landed commits) AND/OR the main working tree is dirty/checked-out by an active
concurrent session (e.g. a `cowork_sandbox_git_locks.md` file present), do NOT blind-merge into
main — that can clobber concurrent work or mis-resolve real conflicts. Instead: do the merge on a
SEPARATE integration branch off current `main` (Ivan explicitly asked "роби мерж в окремій гілці"),
resolve conflicts preserving BOTH sides, and prove BOTH test suites green (the concurrent work's
tests = "переконайся що попередній функціонал працює" AND your own). Adapt only YOUR files to their
new APIs; never edit their files to fit yours. Then land on main as a trivial fast-forward once the
main tree is free — or hand the ready green branch to Ivan to land. Surfacing a genuine
architectural divergence for a decision is warranted here; it is not the "wasted friction" the
base rule forbids.
