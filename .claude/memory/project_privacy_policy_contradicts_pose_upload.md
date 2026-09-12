---
name: project-privacy-policy-contradicts-pose-upload
description: "RESOLVED — TT Coach's privacy policy was updated to match the shipped pose-upload feature"
metadata: 
  node_type: memory
  type: project
  originSessionId: 60f5aad5-5920-4b35-8d6a-b4d644e2cd69
  modified: 2026-07-23T14:53:50.674Z
---

RESOLVED 2026-07-23 (`TT_Coach_AA_site` commit `e814f28` on `main`, live and verified via
`gh api .../pages/builds/latest` + a direct fetch of `src/pagesContent.jsx`).

The published policy previously said pose estimation was "discarded frame-by-frame" and
video/clips "stored locally unless you explicitly choose to back them up" — false as of the
pose-upload feature merged to TT_Coach `main` the same day, which uploads 2D joint
coordinates per frame by default.

Rewrote "What stays on your device" and "What we collect" in all four languages (en/uk/es/zh)
to describe what's actually collected (2D joint coordinates only, no video/images), added an
explicit opt-out note under "Your controls" pointing at Settings → Data & Privacy, and used
the site's existing `ttcoachai@gmail.com` contact for deletion requests — no need to publish
a personal address, contradicting the earlier assumption that none existed.

Kept only as a pointer to [[project-site-repo-pages-branch]] for how the deploy/verify
mechanics work, and as a reminder to re-check this page whenever pose-collection behaviour
changes again (e.g. sample rate, retention).
