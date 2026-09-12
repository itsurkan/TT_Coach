---
name: stale-context-snapshot
description: Session-start CLAUDE.md/git snapshots can be stale in TT_Coach — grep the real file before editing or reasoning from snapshot text
metadata: 
  node_type: memory
  type: feedback
  originSessionId: f9d47e0f-bbac-4d69-a8fd-641f7ac11ad4
---

The CLAUDE.md content injected at session start can lag the working tree (concurrent Claude
sessions update TT_Coach; the snapshot is taken once). On 2026-07-22 the snapshot said
"Phase 3 — NOT STARTED" while the real file said "Phase 3 — DONE (2026-07-03)".

**Why it matters:** it caused (a) a failed Edit — the anchor text quoted from the snapshot
didn't exist in the file, and (b) a mis-framed user question (asked about sequencing "after
Phase 3" as if it weren't done, and initially misread the answer).

**How to apply:** before editing CLAUDE.md (or basing decisions/questions on its phase-status
claims), `grep` the actual file for the relevant lines. Treat the snapshot as orientation,
not ground truth — especially for anything time-sensitive like phase/slice status.
