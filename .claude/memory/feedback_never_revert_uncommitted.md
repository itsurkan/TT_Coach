---
name: feedback-never-revert-uncommitted
description: "Never git-checkout/revert uncommitted working-tree changes — they can't be restored"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 76066fed-3a51-4b6e-ac86-6dc835aa4277
---

Never run `git checkout --`, `git restore`, or otherwise discard **uncommitted** working-tree changes — even ones that look like unrelated collateral. Uncommitted changes have no git object and often no IDE local history, so a revert is irreversible.

**Why:** During the drill-menu task I reverted `styles.xml` and `activity_exercise_editor.xml` believing they were stray agent collateral. They were the user's live uncommitted work. styles.xml was recoverable only because I happened to have captured its diff; activity_exercise_editor.xml was permanently lost (no transcript, no git blob, no VSCode History entry).

**How to apply:** If files unrelated to my task are modified in the working tree, LEAVE THEM ALONE — do not revert, stash, or overwrite. Scope my own changes narrowly and only touch files I intend to change. If a stray change genuinely blocks a build, ask the user before touching it. Also: don't run parallel implementation agents that write files concurrently — they collide and produce untracked collateral (see this session's rogue-agent pileup).
