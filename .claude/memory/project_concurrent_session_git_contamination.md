---
name: concurrent-session-git-contamination
description: "In TT_Coach, concurrent Claude sessions commit onto the shared checkout/branch — verify the commit range and cherry-pick clean before merging"
metadata: 
  node_type: memory
  type: project
  originSessionId: eb189e5e-748e-46cb-91b5-a4fe52841f76
  modified: 2026-07-24T11:42:49.442Z
---

Multiple Claude sessions operate in the TT_Coach repo at once and share one working
checkout. A `git commit` from another session lands on **whatever branch is currently
checked out** — including your feature branch. On 2026-07-23, while executing the
Community Drills plan on `feat/community-drills`, two unrelated foreign commits (a
feedback-scoring fix and a devcontainer pilot) got interleaved into my branch's history.

**Why it matters:** `git log main..HEAD` then contains commits that aren't yours;
review packages and a plain merge to `main` would drag foreign (possibly-unfinished)
work into main. Per-commit review base SHAs also shift under you between tasks.

**How to apply:**
- Before generating a review package or merging, run `git log main..HEAD` (or
  `git log <base>..<head>`) and confirm every commit is yours. Scope each task's
  review-package base to the actual parent of that task's commit, not a stale base.
- To merge only your work: `git checkout -b <clean> main` and cherry-pick just your
  commits, then fast-forward `main` to the clean branch. Leave the foreign commits on
  their existing branch — **never** `reset --hard`/discard them (see [[never-revert-uncommitted]]).
- **When you cannot switch the checkout at all (2026-07-23):** if the other session has
  UNCOMMITTED edits to a file that also differs between `main` and the current branch,
  `git checkout main` is refused (or would clobber their work). Check first with
  `git diff main HEAD --name-only` vs `git status --short`. If your own change touches only
  files that are IDENTICAL in both branches, land it on `main` without any checkout, using a
  temp index — never touching HEAD or the working tree:
  ```bash
  export GIT_INDEX_FILE=$(mktemp -u); git read-tree main
  git update-index --add --cacheinfo <mode>,<blob-sha>,<path>   # per file, blobs from your commit
  NEW=$(git commit-tree $(git write-tree) -p main -m "msg"); git update-ref refs/heads/main $NEW
  ```
  Commit the same change on the current branch too, so the working tree goes clean; the duplicate
  merges without conflict later (identical content on both sides).
- After moving `main` this way, VERIFY nothing was lost: `git merge-base --is-ancestor <your-earlier-merge> main`
  and `git log --oneline main`. Concurrent sessions land commits on `main` between your merges, so
  `main`'s tip is often not where you left it — confirm rather than assume.
- Related but distinct: [[stale-context-snapshot]] (session-start CLAUDE.md text lags
  the working tree for the same concurrent-session reason).

**Two more techniques (2026-07-24, MediaPipe-removal plan):**
- **Editing one file a concurrent session also has dirty, without a branch switch:** don't
  `git add` the whole file (stages their hunk too) and don't touch their lines. Make your edit
  in the working tree, hand-build a patch containing only your hunk(s), `git apply --cached`
  it, then `git diff --cached -- <file>` to confirm only your change is staged before
  committing. Verify afterward that `git status --short -- <file>` still shows their original
  uncommitted hunk untouched. Used successfully across ~6 tasks in one plan (AndroidManifest.xml,
  DrillsFragment.kt, CameraFragment.kt) — reliable when their edit and yours are in different
  regions of the same file.
- **Switching the checkout when a concurrent session's dirty files block `git checkout`:**
  simpler than the temp-index trick above when you don't need `main` to receive your specific
  in-progress diff (you're just checking out to merge an already-committed branch). Run
  `git stash push -u -m "<label>"` to shelve their WIP, do the checkout/merge/verify, then
  `git checkout` back and `git stash pop` to restore their WIP exactly as it was. Confirmed the
  stash round-trip leaves their working tree byte-identical (`git status --short` before/after
  matched). Use the temp-index route instead only when you need to land a change on `main`
  without ever moving off the current branch at all.
