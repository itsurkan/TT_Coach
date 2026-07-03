---
name: project_live_session_panel_over_camera
description: "Live Session (1a/1e) redesign must overlay the live camera as a slide-up panel, not replace it"
metadata: 
  node_type: memory
  type: project
  originSessionId: 8b102705-a407-4f33-815f-5379c4c1cc6d
---

Slice 4 "Live Session" (design frames 1a gold-dark / 1e light) must be integrated by
**restyling the existing `TrainingActivity` slide-up bottom-sheet panel** (the pause/finish
panel over the CameraX live view) to the gold-dark design — session focus card, AI coach card,
real-time feedback list, pause + end-session. The **camera stays live behind it**.

**Why:** On 2026-07-03 I first built 1a/1e as a standalone static full-screen `LiveSessionActivity`
and pointed the Drills "play" button at it — which replaced the live camera. The user reacted
strongly ("Where did the camera go? Return it back") and clarified the design intent: the focus/
coach/feedback content is the slide-up panel *over* the camera, never a camera replacement.

**How to apply:** Keep `TrainingActivity` + CameraX + `OverlayView` intact (frozen pipeline).
Restyle only the presentation of the bottom sheet / training overlays. The reusable pieces from
the first attempt live on branch `live-session-1a1e`: style `TTC.Button.Danger`, drawables
`bg_dot`/`dot_rec`, `live_*` strings (EN+UK), and the focus/AI-coach/feedback-row layout blocks
in `activity_live_session.xml`. The full-screen `LiveSessionActivity` itself is unreachable
(entry-point redirect was reverted, commit 1bd6953) — reuse its inner blocks, don't wire it as a
screen. See [[staged_roadmap]].
