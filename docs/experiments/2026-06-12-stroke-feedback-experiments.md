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
| andrii_1 | ✅ | ~0° (side-on) | ✅ usable | clean side-on FH drive, full body, 23/15/15 (golden). E2E gate. |
| video_2 | ✅ | ~0–10° (distant/frontal) | ⚠ marginal | big hall, player small+distant, 8/5/4; tracking weak on small figure. Use cautiously. |
| video_3 | ✅ | ~0° (slight front-angle) | ✅ usable | shadow play, hallway, well-tracked, 23/16/16. NOTE: torso_lean systematically 33–37° "over" all reps. |
| video_4 | ✅ | ~0° | ✅ usable | shadow play, 18/12/9 (golden). |
| IMG_6330 | ✅ | side-elevated rally | ⚠ marginal | club RALLY (2 players, mixed strokes), not a drill; near player tracks OK, 23/15/15 but reps are rally strokes. Renamed files folder-base. Use for detection-robustness only. |
| IMG_6332 | ✅ | distant frontal | ⚠ marginal | club, distant small figure, 10s, 10/4/4; low reliability. Skip for feedback. |
| ivan_1 | ✅ | ~5–10° (side-on) | ✅ usable | excellent tracking, single player, 38/27/23, strong continuous drill. Cues: elbow_angle + shoulder_tilt. 4th good drill video. |
| table_11 | ✅ | ~0–10° | ❌ skip | ~3s, only 1 rep — too short to calibrate. |
| table_12 | ✅ | ~10–15° | ⚠ marginal | ~4s, 3 reps, player drifts to frame edge. Thin supplement only. |
| table_v1 | ✅ | frontal wide | ❌ skip | club wide ceiling cam, many tables/players, 3s, 2 reps. |
| table_v2 | ✅ | frontal wide | ❌ skip | same club cam, 2s, 1 rep, crowded. |
| table_v3 | ✅ | angled wide | ❌ skip | two-player rally, 3s. |
| table_v7 | ✅ | ~45–60° off side | ⚠ marginal | 7s, 5 reps, elevated frontal/back, table-occluded. Best of table_v*. |
| video_1 | ✅ | n/a | ❌ skip | NOT table tennis — room with a piano, no player. |

### Triage summary
- **USABLE (experiment workhorses):** andrii_1, video_3, video_4, ivan_1 — clean single-player side-on (~0–10°) forehand drives, good tracking, ≥9 reps each.
- **MARGINAL (robustness/edge only):** video_2 (distant), IMG_6330 (rally/2-player), table_12 (short), table_v7 (angled).
- **SKIP:** IMG_6332, table_11, table_v1, table_v2, table_v3, video_1.
- **Camera angle (L-25):** all usable footage is ~side-on; best-yaw==0 on every clip. Rep counts are FLAT across ±30° yaw → **camera angle's effect is on the angle metrics (torso_lean/shoulder_tilt), not detection.** So L-25 calibration is a feedback-accuracy lever, not a detection lever.
| IMG_6370 | ❌ no mp4 | — | no | empty dir |
| IMG_6414 | ❌ no mp4 | — | no | empty dir |

## Experiments

(chronological; each entry: hypothesis, change, commit, visual result, verdict)

### E0 — Baseline observation (no code change)
- Status: triaging. 4 of 4 existing exports triaged (see table). 10 new exports in progress.

## Experiment backlog (prioritized; refined after full triage)
Validation = visible before/after in #/strokes. Each = own commit (TS `drill2d/` layer, where the viewer runs).
1. **E1 — Per-video camera-angle calibration (L-25).** Define correct yaw per usable video; verify metrics stabilize + placementOk. Core deliverable ("define camera angle, adapt analysis").
2. **E2 — torso_lean accuracy under camera angle.** video_3 shows systematic 33–37° "over" lean on every rep. Determine real-vs-artifact; fix torsoLean calc or reference range. Highly visible.
3. **E3 — Stroke detection robustness across corpus.** Find videos where ForwardStrokeFilter/RepFilter visibly miscount (recovery counted as rep, or real rep dropped); tune speed-dominance / banding.
4. **E4 — Locomotion gate (L-30) tuning.** Default off; enable+tune on footage with footwork/walking; visible rose bands.
5. **E5 — Feedback prioritization & cadence.** Surface the single most important cue per rep instead of repeating one metric; verify spoken log.
(Backlog grows as triage surfaces concrete defects.)
