# Stroke & Feedback Analysis — Experiment Series (2026-06-12)

Branch: `2d-experiments` (off `2d`). Autonomous 12h run.

## Ground rules (from user, 2026-06-12)
- **Validation:** visual UI judgment in poses_viewer `#/strokes` (no test gate; tests may be wrong and can be ignored).
- **Integration:** every experiment = its own cleanly-scoped commit so individual changes can be cherry-picked to `main` later. This log tracks hypothesis → change → result → keep/revert.
- **Risk:** full freedom to modify core analysis code (StrokeDetector2D, DrillCalibrator, ForwardStrokeFilter, feedback engine).
- **Focus:** camera-angle estimation + stroke-detection robustness on varied footage first, then feedback quality.
- **Skip** videos with low visibility or wrong angle. Camera angle (Кут камери °, L-25) defined manually by me per video.

## Video inventory & triage

| Video | RTM | Camera angle (L-25) | Usable? | Notes |
|-------|-----|--------------------|---------|-------|
| andrii_1 | ✅ | TBD | TBD | existing fixture, E2E exit gate |
| video_2 | ✅ | TBD | TBD | existing fixture |
| video_3 | ✅ | TBD | TBD | |
| video_4 | ✅ | TBD | TBD | |
| IMG_6330 | exporting | TBD | TBD | 59fps, 18.8s |
| IMG_6332 | exporting | TBD | TBD | |
| ivan_1 | exporting | TBD | TBD | |
| table_11 | exporting | TBD | TBD | |
| table_12 | exporting | TBD | TBD | |
| table_v1 | exporting | TBD | TBD | |
| table_v2 | exporting | TBD | TBD | |
| table_v3 | exporting | TBD | TBD | |
| table_v7 | exporting | TBD | TBD | |
| video_1 | exporting | TBD | TBD | |
| IMG_6370 | ❌ no mp4 | — | no | empty dir |
| IMG_6414 | ❌ no mp4 | — | no | empty dir |

## Experiments

(chronological; each entry: hypothesis, change, commit, visual result, verdict)

### E0 — Baseline observation (no code change)
- Status: pending triage.
