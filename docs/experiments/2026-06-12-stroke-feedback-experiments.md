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

## Resume state (for fresh-context continuation)
- Branch `2d-experiments`. Done & committed: **EXP-1..5** (all feedback-layer, viewer-only TS in
  `poses_viewer/src/drill2d/` + `components/`). Detection verified solid (counts match goldens,
  robust to ±yaw) — value is in feedback, so the series leans feedback.
- Env: Vite dev on **5782**, headed Chrome CDP on **9222**. Tools: `tmp/analyze.mjs <port> <video> <yaw> <hand> <shot>` (counts + per-rep table + spoken log, deterministic), `tmp/triage.mjs` (yaw sweep + screenshot). Both have select-verify; Videos/ is gitignored.
- Usable videos: andrii_1, video_3, video_4, ivan_1 (all ~side-on). Marginal: video_2, IMG_6330, table_12, table_v7.
- **Next-up candidates:** (a) research-ground the coach_opinion torso_lean(5–25)/shoulder_tilt(0–20)
  ranges in `referenceStandard.ts` — both flag heavily; (b) validate improved feedback on marginal
  footage + non-zero yaw; (c) positive-message variety; (d) detection spot-check on ivan_1/video_4 bands.
- Each experiment = own commit + a log entry here. Keep going; user is away for the 12h run.

## Experiments

(chronological; each entry: hypothesis, change, commit, visual result, verdict)

### E0 — Baseline observation (no code change)
- All 14 triaged. Baseline spoken-feedback logs captured for the 4 usable videos.
- **Core finding:** the engine emits the single top-severity cue per rep through a blind
  rate-limiter (`cadence.offer`) with no memory → the SAME line repeats every 3–5s:
  andrii "Elbow… ×5", video_3 "Leaning… ×5". And andrii's elbow delta swings 39–79° off
  rep-to-rep — a motion-blur artifact (RTMPose is confident-but-wrong on the fast-swinging
  forearm at the wrist-speed peak; scores stay 0.6–0.9 even as the elbow collapses to 0°),
  yet it's coached as precise degrees. Two defects: (1) repetitive single-cue nagging,
  (2) precise coaching on unstable/artifact metrics. → EXP-1 fixes (1); EXP-2 targets (2).
- Measurement tool: `tmp/analyze.mjs` (CDP, deterministic currentTime-stepping to cross every
  feedback timestamp), `tmp/triage.mjs` (yaw sweep + screenshot). Both scratch (Videos/ gitignored).

### EXP-1 — Variety-aware feedback (anti-repetition) ✅ KEEP
- **File:** `poses_viewer/src/drill2d/analyzeDrill.ts` (feedback-assembly loop + `pickVariedCue`).
- **Change:** prefer the most-severe cue for an issue OTHER than the one just spoken; for a
  persistent single fault, space reminders to ≥8s instead of every rep. Cadence rate-limit
  still applies. (Feedback layer is viewer-only — no Kotlin mirror needed.)
- **Visual result (spoken logs, before → after):**
  - andrii: `Elbow ×5` → `Elbow / Legs / Elbow / Legs / Elbow`
  - video_3: `Leaning ×5` → `Leaning / Shoulder-tilt / Leaning / Shoulder-tilt / Leaning`
  - video_4: `Leaning ×3, Arm ×1` → 3 distinct faults
  - ivan_1: 2 messages → 4 distinct faults (knees, shoulder-line, elbow, shoulder-tilt)
- **Verdict:** clear improvement — coach surfaces the player's real spread of faults instead of
  one repeated line. Detection/counts untouched. Aligns with "don't re-teach" positioning.
- Commit: `feat(viewer): EXP-1 variety-aware feedback`.

### EXP-2 — Reliability / trust gating (suppress unstable-metric cues) ✅ KEEP
- **File:** `poses_viewer/src/drill2d/analyzeDrill.ts` (`unreliableMetricKeys` + cue filter).
- **Rationale:** a metric whose value swings across the player's reps is measurement noise,
  not a coachable fault. Root-caused on andrii: RTMPose is confident-but-wrong on the
  fast-swinging forearm at the wrist-speed peak (elbow collapses 116°→37°→0° in 1–2 frames,
  scores stay 0.6–0.9), so the elbow reads 35–124° across reps yet is coached as "79° off".
- **Cross-rep IQR (measured):** andrii elbow **28**, ivan shoulder_tilt **25** (artifacts) vs
  video_3 lean **1**, video_4 ≤9, everything-real ≤14. Threshold `UNRELIABLE_IQR_DEG = 20`
  cleanly separates them. Drop cues for metrics with IQR > 20 (≥4 reps required).
- **Visual result:**
  - andrii: false `elbow ×13` cue **gone** → coaches reliable `knee_bend` ("bend your knees", legs at 175°). ✅
  - ivan: noisy `shoulder_tilt` (IQR 25) suppressed → elbow corrections **+ 7 "Good rep" positives** (reps whose only fault was the noisy metric are now correctly clean). ✅
  - video_3 / video_4: real consistent signals untouched (IQR low). ✅
- **Verdict:** kills false coaching on tracking artifacts while preserving every real fault.
  Directly implements the CLAUDE.md trust rule. Detection/counts untouched.
- **Follow-up idea (EXP-2b):** visually gray out suppressed/unreliable metric values in
  `DrillResultsTable` so the displayed 43° elbow doesn't read as a trustworthy measurement.
- Commit: `feat(viewer): EXP-2 reliability/trust gating`.

### EXP-3 — Session "main focus" summary ✅ KEEP
- **Files:** `analyzeDrill.ts` (`sessionFocus` + `report.focus`), `StrokesPage.tsx` (🎯 banner).
- **Change:** compute the dominant reliable fault across the set (metric flagged on the most
  reps, ties → higher severity) and present ONE actionable takeaway, or praise when clean.
- **Visual result:**
  - andrii: "🎯 Main focus: bend the knees more (about 30° off) — 15/15 reps" (legit: player stands tall). ✅
  - ivan: "🎯 Main focus: open the elbow (about 9° off) — 14/23 reps". ✅
  - clean set → green "Great set — close to the standard."
- **Verdict:** turns 15 scattered nags into one focus the player can actually act on — the
  "don't re-teach, give a clear focus" positioning. Additive; per-rep table/feedback unchanged.
- Commit: `feat(viewer): EXP-3 session focus summary`.

### EXP-4 — Mark unreliable metrics in the results table (EXP-2b) ✅ KEEP
- **Files:** `analyzeDrill.ts` (`report.unreliableMetrics`), `DrillResultsTable.tsx`, `StrokesPage.tsx`.
- **Change:** the metrics EXP-2 suppressed are now shown muted + struck-through with a "~шум"
  header tag, so a displayed 43° elbow no longer reads as a trustworthy measurement.
- **Visual result:** andrii elbow column → grey/strikethrough + "~шум"; reliable columns
  keep their over/under colour. Honest UI: value present, but visibly not trusted. ✅
- Commit: `feat(viewer): EXP-4 mark unreliable metrics in results table`.

### ⚠ Data-integrity finding — corrupt IMG_6330 export
- During triage I found IMG_6330's `_poses_rtm.json` (from the batch `export_new.py` run)
  contained **andrii_1's exact frames** (header `videoName` correct, but `frames` byte-identical
  to andrii) — the video plays the rally, the poses were andrii's. Verified all other 12 exports
  are distinct/clean (frame-100/500/900 R-shoulder signature check). Re-exported IMG_6330 via
  `export_poses_rtmpose.py` → now correct (188 frames @100ms). **Experiments EXP-1..4 are
  unaffected** (they used andrii/video_3/video_4/ivan_1, all verified distinct).
- Hardened `tmp/analyze.mjs` with select-verify+retry after this surfaced a stale-selection risk.
- Root cause in `export_new.py` batch mode not chased (out of scope); flag for follow-up.
- EXP-5 (session-quality gate) abandoned: it didn't cleanly separate marginal from clean footage,
  and its initial signal was contaminated by the corrupt IMG_6330 measurement.

### EXP-5 — Suppress trivial (sub-5°) deviations ✅ KEEP
- **File:** `poses_viewer/src/drill2d/feedbackEngine.ts` (`MIN_MEANINGFUL_DELTA_DEG = 5`).
- **Change:** a metric < 5° outside its ideal band is within keypoint-jitter noise — drop the cue
  (a coach doesn't nitpick "3° off"). Keeps meaningful faults (≥5°).
- **Visual result:** ivan's "3°/4° off" nags → gone; those reps now read as **"Good rep"
  positives** (4 of them) + 2 real corrections. video_3/video_4 meaningful cues unchanged. ✅
- **Verdict:** less false-precision nagging, more deserved encouragement. Detection untouched.
- Commit: `feat(viewer): EXP-5 suppress trivial sub-5deg deviations`.

### EXP-6 — Positive-message variety ✅ KEEP
- **Files:** `messageCatalog.ts` (`POSITIVE_MESSAGES` pool + indexed `positiveMessage`), `analyzeDrill.ts` (rotating `positiveCount`).
- **Why:** EXP-5 made clean reps earn positives, but `positiveMessage()` returned one fixed string
  → ivan heard "Good rep" ×4 (the repetition EXP-1 fixed for corrections).
- **Visual result (ivan):** positives now rotate — "Good rep…", "Nice — that one looked solid",
  "Clean technique, keep it going", "That's the shape — repeat it". ✅
- Commit: `feat(viewer): EXP-6 positive-message variety`.

### EXP-7 — (deferred) per-video camera-angle persistence
- Idea: remember yaw+handedness per video in localStorage so calibration sticks. Deferred —
  all usable footage is ~0° so low visible payoff; revisit if angled footage matters.

### EXP-8 — Research-grounded torso_lean / shoulder_tilt ranges ✅ KEEP
- **File:** `referenceStandard.ts`. Background research (cited, see commit) found NO TT study
  measures trunk-from-vertical at contact; the "35°" web claim is **unverified**. Nearest
  same-convention anchor = tennis-serve trophy **25±7°** (TT contact is more flexed); our own
  footage shows skilled players at 33–39°. Old `torso_lean 5–25°` falsely flagged normal
  attacking lean on EVERY rep.
- **Change:** `torso_lean` 5–25 → **15–40°**; `shoulder_tilt` upper 20 → **25°**. Kept
  `coach_opinion` (no direct measure) with research-informed provenance + provisional caveat
  (2D lean is inflated by axial rotation/yaw; re-tune on protocol footage).
- **Visual result:**
  - video_3: torso_lean flagged 16/16 → **0**; 12 reps now clean, focus shifts to elbow (4/16). ✅
  - video_4: lean cues 7 → 1. ✅
  - andrii: gains a defensible "lean in more" cue (genuinely upright at 3°, below the 25–40° norm).
- **Verdict:** stops false-coaching of normal forward lean — the single biggest false-positive in
  the corpus — with honest, cited grounding. Detection untouched.
- Commit: `feat(viewer): EXP-8 research-grounded torso_lean/shoulder_tilt ranges`.

## Experiment backlog (prioritized; refined after full triage)
Validation = visible before/after in #/strokes. Each = own commit (TS `drill2d/` layer, where the viewer runs).
1. **E1 — Per-video camera-angle calibration (L-25).** Define correct yaw per usable video; verify metrics stabilize + placementOk. Core deliverable ("define camera angle, adapt analysis").
2. **E2 — torso_lean accuracy under camera angle.** video_3 shows systematic 33–37° "over" lean on every rep. Determine real-vs-artifact; fix torsoLean calc or reference range. Highly visible.
3. **E3 — Stroke detection robustness across corpus.** Find videos where ForwardStrokeFilter/RepFilter visibly miscount (recovery counted as rep, or real rep dropped); tune speed-dominance / banding.
4. **E4 — Locomotion gate (L-30) tuning.** Default off; enable+tune on footage with footwork/walking; visible rose bands.
5. **E5 — Feedback prioritization & cadence.** Surface the single most important cue per rep instead of repeating one metric; verify spoken log.
(Backlog grows as triage surfaces concrete defects.)
