# Design Limitations Register

Project-wide register of known design limitations, accepted trade-offs, and open
design issues. One entry per limitation; update **Status** in place, don't delete
entries (move resolved ones to the bottom section with the resolving commit/doc).

**Status values:**
- `OPEN` — needs a design decision or fix; no mitigation in any plan yet
- `PLANNED` — mitigation exists in a written plan/spec, not yet implemented
- `ACCEPTED` — known and consciously deferred (record why and the revisit trigger)
- `RESOLVED` — fixed; entry moved to the Resolved section

Last updated: 2026-06-10

---

## 1. 2D pose pipeline (Phase 1–2, branch `2d`)

### L-01 · Wrist speed is not body-size normalized — `OPEN`
`StrokeDetector2D.minPeakSpeed` (0.03f) is in xScale-corrected normalized image
coords, so it depends on camera distance/zoom. A threshold tuned on one video does
not transfer to another, nor from calibration footage to a live session.
**Fix direction:** divide speed by torso length (shoulder-mid → hip-mid) — already
computed for yaw estimation, scale-invariant.
**Refs:** plan `2026-06-10-phase2-drill-logic-shared-kmp.md` Task 5; gap analysis 2026-06-10.

### L-02 · 10 fps fixtures are too coarse for stroke-peak detection — `OPEN`
Phase 1 exports use `intervalMs: 100` (10 fps). A forehand-drive forward swing is
~150–250 ms → 2–3 samples per swing: peak wrist speed is systematically
underestimated and the "angle at peak frame" is effectively a random nearby frame.
Detector params tuned on 10 fps will not transfer to live capture (Phase 3), which
is planned as **configurable 30/60/120 fps** — so detector params (peak gap,
smoothing window, boundary walk) must be parameterized in **milliseconds, not frame
counts**, or they silently change meaning with every fps setting.
**Fix direction:** re-export fixtures at full video fps before TDD cements thresholds
(Phase 2 Task 5); express all `StrokeDetector2D` tuning in ms and derive frame
counts from `intervalMs`.
**Refs:** `pose_json_schema_v2.md`; plan design note "revisit when interval changes".

### L-03 · Every wrist-speed peak is treated as a drill rep — `OPEN`
`StrokeDetector2D` has no stroke/non-stroke discrimination; `DrillCalibrator`
assumes every detected peak is a rep. Picking up a ball, wiping a hand, walking all
produce local maxima. `BaselineDeriver`'s single-pass 2σ exclusion partially helps,
but several junk "reps" shift the mean before exclusion.
**Fix direction:** minimal rep filter (peak-speed/duration clustering) and/or
calibration UX rule "only strokes during the capture window".
**Refs:** plan Tasks 5, 11.

### L-04 · Torso-lean sign is image-relative, not player-relative — `OPEN`
`AngleCalculations2D.torsoLean` sign convention is "positive = shoulders toward +x".
A player facing the other way inverts the sign, so a baseline rule would give the
opposite cue ("lean forward" → "lean back"). `shoulderTilt` is folded to a
half-plane for exactly this reason; `torsoLean` is not.
**Fix direction:** normalize sign by facing direction (e.g. nose/ear x relative to
hip-mid).
**Refs:** plan Task 4.

### L-05 · Rep metrics come from a single peak frame, keypoints unsmoothed — `OPEN`
Only the wrist-speed signal is smoothed (window 3); keypoint coordinates and angles
are not. RTMPose per-frame jitter feeds straight into the baseline.
**Fix direction:** average angles over ±1–2 frames around the peak (near-free).
**Refs:** plan Task 11 (`DrillMetrics.extractAtFrame`).

### L-06 · Table occludes knees/ankles in side view → knee-bend may silently vanish — `OPEN`
Score gating returns `null` for occluded joints, so on protocol footage the
knee-bend metric may be gated out on most frames and calibration silently produces
no knee baseline. No "metric unavailable" behavior or per-metric coverage report is
defined. (Context doc flags table occlusion only for footwork, but forehand-drive
side view has the same problem.)
**Fix direction:** report per-metric coverage after calibration; define drill
behavior when a metric has no baseline.
**Refs:** context doc §3 (open design issue); plan Task 11.

### L-07 · Multi-person: best-score person picked per frame, no identity continuity — `OPEN`
`export_poses_rtmpose.py` keeps the highest-mean-score person independently each
frame. With an opponent/coach in frame, identity can flip between frames → phantom
wrist-speed spikes the stroke detector reads as strokes. Acceptable for fixtures
(visual check in poses_viewer) but must be solved for live (Phase 3 bbox tracking).
**Refs:** `export_poses_rtmpose.py` (`best_person()`); `pose_json_schema_v2.md`.

### L-08 · Exporter ignores video rotation metadata — `OPEN`
`export_poses_rtmpose.py` reads width/height via OpenCV and does not check the
rotation flag. Portrait phone videos may come in sideways, inverting the
aspect-ratio correction that all angle math depends on (correctness-critical).
**Fix direction:** one-line rotation check in the exporter; schema v2 has no
rotation field by design — bake rotation in before export.
**Refs:** `export_poses_rtmpose.py`.

### L-09 · Camera yaw correction is first-order, |yaw| only, ≤~30° — `ACCEPTED`
Shoulder-foreshortening estimation can't recover yaw sign (cos is even — correction
doesn't need it). Beyond ~30° the model is unreliable → side-view drills skip
feedback entirely (`placementOk = false`, `CameraPlacementException` during
calibration). Founder decision 2026-06-10. Revisit if real footage shows frequent
>30° placements.
**Refs:** plan design note 9; `ViewGeometry`, `CameraAngleEstimator`.

### L-10 · Camera pitch/height not corrected (yaw only) — `ACCEPTED`
Vertical tilt compresses vertical measurements (knee bend, torso lean). Mitigated by
the recording protocol (camera at table height). No correction in 2D MVP; revisit if
beta footage shows protocol violations.
**Refs:** spec "Content dependency"; gap analysis 2026-06-10.

### L-11 · Left/right limb mislabeling in profile view — `ACCEPTED`
In true profile the far arm is occluded; RTMPose can place a far-side keypoint on
the near limb with decent score. Only mitigation is score gating + recording
protocol. Revisit if fixture QA in poses_viewer shows frequent swaps.
**Refs:** gap analysis 2026-06-10.

### L-12 · Backswing phase not segmented in v1 — `ACCEPTED`
Needs wrist-direction-reversal analysis; YAGNI until protocol footage exists. Rhythm
rules get `forward_swing_ms` (start→peak) and `stroke_total_ms` only.
**Refs:** plan design note 7.

### L-13 · Existing `Videos/` footage was not shot to the placement protocol — `ACCEPTED`
Good for pipeline bring-up, **not** for tuning reference ranges. Phase 2 exit gate
labels are constructed by perturbation (baseline from a fixture's own reps must stay
quiet; a shifted baseline must produce the matching directional cue), not human
labels. Real tuning waits for protocol footage (founder task).
**Refs:** spec "Content dependency"; plan design note 8 + exit-gate section.

### L-14 · Handedness is explicit config, no auto-detection — `ACCEPTED`
`Handedness` is always passed in (default RIGHT); left-handed players require
explicit selection at calibration/analysis entry. Fine for MVP onboarding; revisit
if onboarding friction shows up in beta.
**Refs:** plan Tasks 1, 11.

## 2. Live capture & Android runtime (Phase 3 relevant)

### L-15 · Capture rate ≠ inference rate; fixed frame-skip breaks stroke detection — `PLANNED` (design guidance)
Capture fps is planned as **configurable 30/60/120** (higher fps → shorter exposure
→ less motion blur on fast strokes; needs good lighting — context doc §3). RTMPose-s
(~13.9 ms on SD865) keeps up with every frame only at 30 fps (33 ms budget); at
60 fps it's marginal and at 120 fps (8.3 ms budget) per-frame inference is
impossible — capture and inference rates **must** be decoupled. Fixed skips are the
wrong tool: at 30 fps, every-3rd-frame = 10 fps effective (same hole as L-02),
every-5th = 6 fps (peak can be missed entirely).
**Design rule:** capture at the configured fps for blur/contact precision; run
inference adaptively by target *interval in ms*, not frame count — idle (slow wrist)
→ sparse sampling, motion onset → densest rate inference sustains. Buffered frames
around a detected peak can be inferred retroactively if contact-precision needs it.
**Refs:** context doc §2–3; analysis 2026-06-10.

### L-16 · 120 fps capture not guaranteed on-device — `ACCEPTED`
Query `getHighSpeedVideoFpsRanges()`; `CONSTRAINED_HIGH_SPEED` sessions have fixed
fps and limited resolutions; fallback to 60 fps with honest UX warning (contact
precision reduced). Contact moment is ~1–2 frames at 30 fps and motion-blurred.
**Refs:** context doc §3.

### L-17 · Thermal throttling under capture+inference — `ACCEPTED`
Real on mid-range devices; profile fps stability on targets before beta. Interacts
with L-15 (adaptive rate is also the thermal mitigation).
**Refs:** context doc §3.

### L-18 · TFLite GPU delegate silently falls back to CPU — `ACCEPTED`
No exception on GPU-unavailable devices; check logcat for `GPU delegate
unavailable`. (Applies to frozen ball detector; same risk class for any future
on-device accelerated inference.)
**Refs:** CLAUDE.md gotchas; `BallDetectorV6.kt:60-69`.

## 3. Data & persistence

### L-19 · Room uses `fallbackToDestructiveMigration()` — `ACCEPTED`
Any schema bump wipes local data (DB is v3). OK for dev; **must** switch to explicit
migrations before any external release. Revisit trigger: first beta build.
**Refs:** CLAUDE.md gotchas; `AppDatabase.kt:26`.

### L-20 · Calibration does not persist raw pose frames — `ACCEPTED`
`CalibrationStateManager` stores derived strokes + analyses only. Any
captured-rep replay feature needs a separate raw-frame persistence path; the Phase 7
editor replays bundled fixtures instead.
**Refs:** CLAUDE.md gotchas; plan for Phase 7 editor.

## 4. Modeling & scope (canonical, from context doc)

### L-21 · MediaPipe z is unreliable → 2D-only metrics — `ACCEPTED` (pivot driver)
Monocular depth errors 146–249 mm make 3D angles untrustworthy; this is the reason
for the 2D pivot. Rotational cues are qualitative-only or silent (trust rule).
**Refs:** context doc §3, §7 decision 1.

### L-22 · Real-time 3D lifting rejected for MVP — `ACCEPTED`
Centered windows need future frames (0.33 s @ 120 fps, 1.3 s @ 30 fps delay); causal
accuracy loss on fast motion; NPU contention. 3–5 s feedback cadence keeps the door
open. Don't stitch strokes for lifting (seam discontinuities produce garbage).
**Refs:** context doc §5, §7 decision 8.

### L-23 · VLM/LLM must never be the judge of action quality — `ACCEPTED`
VLMs judge action quality barely above chance (arXiv:2604.08294). Use only as an
explanation layer over computed metrics.
**Refs:** context doc §8.

### L-24 · Ball tracking, audio contacts, video analysis frozen — `ACCEPTED`
`BallDetectorV1–V6`, `ROIManager`, trajectory code, audio-contact and
frame-extraction scripts, YOLO training: frozen in place, not deleted. Return in
Stage 2 of the staged roadmap.
**Refs:** spec Phase 3; `STAGED_ROADMAP.md`.

---

## Resolved

*(none yet — move entries here with the resolving commit hash)*
