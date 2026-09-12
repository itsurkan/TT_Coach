---
name: feedback-verify-deployed-not-checkout
description: "When work depends on what is actually published, verify the live artifact rather than trusting the local checkout"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 60f5aad5-5920-4b35-8d6a-b4d644e2cd69
  modified: 2026-07-23T14:45:31.091Z
---

If a task's correctness depends on what is *deployed* — a published page, a live rules
file, a released package — verify the deployed artifact directly (curl the URL, list the
remote branch, check the console) instead of inferring it from the working copy.

**Why:** the local branch can be stale, and the deploy source can be a branch you are not
on. Assuming the checkout was authoritative produced a whole duplicate task and an app
pointing at 404s.

**How to apply:** put the verification in the subagent's dispatch prompt as an explicit
step ("curl the live URL and report the status code before writing anything"), not as an
assumption. Same reflex for `storage.rules`/`firestore.rules`: being in the repo does not
mean being live — both are manual-deploy. See [[project-site-repo-pages-branch]].
