---
name: feedback_viewer_fast_iteration
description: "For viewer-facing fixes the user wants direct fast iteration, not the full Kotlin-first subagent plan"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 8159b994-6be6-4d25-ac9c-bc1e0b0bd013
---

When a fix is about what the user *sees in poses_viewer* (#/strokes counts, bands, UI behavior), the user prefers fast direct iteration in the TS viewer over the heavyweight Kotlin-first subagent-driven plan. In the 2026-06-15 full-cycle work they twice interrupted a subagent dispatch and said "skip kotlin, focus on UI fix" — they wanted to *see* video_4 hit 10 strokes, not wait through an 8-task Kotlin-source-of-truth process.

**Why:** the viewer is the feedback loop they actually watch; a long correct-but-slow process feels like nothing is changing (they explicitly noted "nothing changed, still 8").

**How to apply:** for viewer-visible behavior, propose wiring the TS change directly and verifying with a quick count probe + vitest, then offer to back-port to Kotlin separately. Still update TS goldens in the same change. Flag any resulting TS↔Kotlin divergence (the [[staged_roadmap]] binding rule says Kotlin is source of truth) as a known follow-up, don't silently leave it. Reserve the full subagent-driven plan for deep shared/ logic work. Relates to [[feedback_subagents]].
