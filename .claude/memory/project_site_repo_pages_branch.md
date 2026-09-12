---
name: project-site-repo-pages-branch
description: TT_Coach_AA_site is published from a non-main branch with capitalised filenames; local main is stale
metadata: 
  node_type: memory
  type: project
  originSessionId: 60f5aad5-5920-4b35-8d6a-b4d644e2cd69
  modified: 2026-07-23T14:53:37.182Z
---

GitHub Pages actually serves `itsurkan.github.io/TT_Coach_AA_site/` from **`origin/main`**
(confirmed via `gh api repos/itsurkan/TT_Coach_AA_site/pages` → `source.branch: "main"`).
`origin/main` already merged the `claude/site-design-compliance-0e4f4d` work via PR #1
(2026-07-13, merge commit `f96aded`), so it has the full i18n rebuild, `Support.html`, and
capitalised `Privacy.html`/`Terms.html` (Pages is case-sensitive; lowercase 404s). Page
content is not in the HTML — it's client-rendered from `src/pagesContent.jsx` (en/uk/es/zh
in one object), loaded via in-browser Babel, no bundler.

**The trap:** the LOCAL `main` checkout can be stale relative to `origin/main` (a `git fetch`
had not been run), which looks identical to "Pages deploys from a different branch" if you
only check `git ls-tree HEAD` without also checking `git log origin/main` / `gh api .../pages`.
That mistake happened twice in one session: once building a duplicate `privacy.html` against
a stale local `main`, and again almost force-pushing a stale local `main` over the real one
before diffing `main` vs `origin/main` caught it.

**How to apply:** before editing this site, run `git fetch origin` and `gh api
repos/itsurkan/TT_Coach_AA_site/pages` to confirm the deploy source branch, then edit
`src/pagesContent.jsx` on a branch based on current `origin/main` (or PR into it) — never
assume the local branch matches origin. To verify a change went live: poll
`gh api repos/.../pages/builds/latest` for the pushed commit SHA + `status: built`, then
curl the raw source URL (Pages doesn't reflect client-rendered content via a text fetch of
the HTML — fetch `src/pagesContent.jsx` directly and grep for the new string).
See [[project-privacy-policy-contradicts-pose-upload]], [[feedback-verify-deployed-not-checkout]].
