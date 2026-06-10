---
name: viewer-qa
description: Use when visually checking pose-export quality — verifying an RTMPose/MediaPipe skeleton overlay in poses_viewer, judging whether a new export is usable, or before promoting an export to a test fixture.
---

# Visual QA of pose exports in poses_viewer

Phase 1 exit gate: an export is usable only after the skeleton looks right on real footage.

## Run

```bash
cd poses_viewer && npm run dev    # http://localhost:5780
```

- Select the clip in the video dropdown (`#/main` route, the default for QA; viewer serves `Videos/<base>/` via Vite middleware — the export must sit next to its video).
- Enable the **RTM** header toggle — independently fetches `<base>_poses_rtm.json` and draws the RTM skeleton in fuchsia/amber/lime (`RTM_SIDE_COLORS`) with yellow joints, on top of the legacy blue/red/green MediaPipe overlay. The "Poses" layer stays MediaPipe-only.
- Halpe26 exports: foot keypoints render; head/neck/hip-mid never drawn.

## Checklist (scrub the whole clip, not just the first frames)

- **Right person tracked** — exporter picks highest-mean-score detection per frame; with bystanders/coach in frame it can jump between people. Watch for skeleton teleporting.
- **Left/right not swapped** — fuchsia vs amber sides must match the player's actual sides; swaps happen on turns/occlusion.
- **Limb stability through the stroke** — playing-arm wrist/elbow must track smoothly through the swing (this drives `StrokeDetector2D` wrist-speed peaks).
- **Dropped frames** — empty-landmark frames show no skeleton; occasional is fine, runs of them near strokes are a blocker.
- **Legs/feet on side view** — table occludes legs; check knee/ankle plausibility, and foot keypoints if `--feet`.
- **Sanity vs MediaPipe overlay** — both skeletons should roughly agree; large systematic disagreement means one export is wrong.

**Do not judge z/depth — RTMPose is 2D, there is no z.** Jitter in apparent depth is not a defect.

## Verdict

- Usable → promote to fixture (see `fixture-pipeline` skill).
- Wrong-person / side-swap / stroke-window dropouts → blocker: re-shoot or re-export; don't "fix" it downstream with thresholds.

Viewer internals (rendering, palettes, routes): [poses_viewer/CLAUDE.md](../../../poses_viewer/CLAUDE.md).
