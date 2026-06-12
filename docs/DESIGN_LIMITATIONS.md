# Design Limitations Register

Project-wide register of known design limitations, accepted trade-offs, and open
design issues. One entry per limitation; update **Status** in place, don't delete
entries (move resolved ones to the bottom section with the resolving commit/doc).

**Status values:**
- `OPEN` — needs a design decision or fix; no mitigation in any plan yet
- `PLANNED` — mitigation exists in a written plan/spec, not yet implemented
- `ACCEPTED` — known and consciously deferred (record why and the revisit trigger)
- `RESOLVED` — fixed; entry moved to the Resolved section

Last updated: 2026-06-12

---

## 1. 2D pose pipeline (Phase 1–2, branch `2d`)

### L-04 · Torso-lean sign normalization relies on per-frame head facing, which is noise on real footage — `OPEN`
The sign normalization itself **is** implemented: `AngleCalculations2D.torsoLean`
normalizes by facing direction via nose x relative to shoulder-mid (shoulder-mid
chosen over hip-mid because hip-mid is confounded by the lean being measured) —
commit `c6dc78e`. **But** Task 14 E2E diagnostics measured the per-frame
head-facing signal as ~45/55 noise on real footage (475 frames +1 vs 628 −1 on
andrii_1_rtm, and anti-correlated with swing phase at stroke starts) — so the
torso-lean **sign** is unreliable on real footage despite correct math.
**Fix direction:** derive a session-level facing (e.g. the speed-dominance vote
already used by `ForwardStrokeFilter`, commit `85b0ef2`) and feed it into
`torsoLean` instead of per-frame head reads.
**Refs:** `AngleCalculations2D.kt`; `ForwardStrokeFilter.kt`; Task 14 E2E diagnostics.

### L-06 · Table occludes knees/ankles in side view → knee-bend may silently vanish — `OPEN`
Score gating returns `null` for occluded joints, so on protocol footage the
knee-bend metric may be gated out on most frames and calibration silently produces
no knee baseline. No "metric unavailable" behavior or per-metric coverage report is
defined. (Context doc flags table occlusion only for footwork, but forehand-drive
side view has the same problem.)
**Fix direction:** report per-metric coverage after calibration; define drill
behavior when a metric has no baseline.
**Refs:** context doc §3 (open design issue); plan Task 7 (`DrillMetrics`).

### L-07 · Multi-person: best-score person picked per frame, no identity continuity — `OPEN`
`export_poses_rtmpose.py` keeps the highest-mean-score person independently each
frame. With an opponent/coach in frame, identity can flip between frames → phantom
wrist-speed spikes the stroke detector reads as strokes. Acceptable for fixtures
(visual check in poses_viewer) but must be solved for live (Phase 3 bbox tracking).
**Refs:** `export_poses_rtmpose.py` (`best_person()`); `pose_json_schema_v2.md`.

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

### L-25 · Camera-yaw estimator saturates on non-protocol footage — `OPEN`
`CameraAngleEstimator.estimateSideViewYawDeg` returns 90.0° (the ceiling) on
andrii_1_rtm — the shoulder-foreshortening model needs validation on
protocol-compliant side-view footage before auto-estimation can gate real sessions.
Until then, orchestrator callers pin `cameraYawDeg` overrides in tests.
**Refs:** `CameraAngleEstimator.kt`; `ForehandDriveEndToEndTest.kt`; commit `ed2c739`.

### L-26 · Integer intervalMs truncation inflates speeds at high fps — `ACCEPTED` (revisit in Phase 3)
`StrokeDetector2D.detect` takes integer ms; at 120 fps (true 8.33 ms) truncation to
8 ms inflates torso/s speeds ~4%, shifting the tuned `minPeakSpeed` meaning. The
Phase 3 live loop should derive dt from per-frame timestamps
(`PoseFrame2D.timestampMs` already exists).
**Refs:** `StrokeDetector2D.kt` kdoc; Task 5 review.

### L-27 · Forward-stroke detection assumes drives are faster than recoveries — `ACCEPTED` (revisit per drill)
`ForwardStrokeFilter`'s session-level speed-dominance vote (median peak speed by
wrist-dx group, ratio ≥ 1.2, minority group ≥ 2) was validated on ONE fixture
(ratio 1.33). Holds for drive/topspin-class drills; weakens for touch/block/push.
Below the ratio it falls back to head facing, which is measured noise on real
footage (L-04) → conservative mass-drop → loud calibration failure.
Shadow play is a confirmed below-ratio case: on video_4 the unsigned backswing
peak speeds match the drives (4.58 vs 4.50 torso/s) — without a ball there is no
acceleration-into-contact asymmetry, so classification rides on the fallback.
**Refs:** `ForwardStrokeFilter.kt`; commits `85b0ef2`, `f3be865`.

### L-29 · Drill-simulator ideal ranges are provisional — `OPEN`
`poses_viewer/src/drill2d/referenceStandard.ts` ranges are provisional. The 2026-06-12
deep-research pass hit a session limit before adversarial verification ran (votes were 0-0,
i.e. unverified, NOT refuted). Measured biomechanics exist only for elbow/shoulder/knee, in
clinical flexion convention at slightly different stroke instants (converted to interior angles
here); torso lean and shoulder tilt have NO measured source and are coach-opinion. Re-run the
deep-research skill after the limit resets, verify the numbers, and tighten the bands +
evidence tags. Until then the UI surfaces the `evidence` flag so users see these are an
external provisional standard, not a calibrated target.
**Refs:** `poses_viewer/src/drill2d/referenceStandard.ts`; deep-research pass 2026-06-12.

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

### L-01 · Wrist speed is not body-size normalized — `RESOLVED`
`StrokeDetector2D.minPeakSpeed` (0.03f) was in xScale-corrected normalized image
coords, so it depended on camera distance/zoom; a threshold tuned on one video did
not transfer to another, nor from calibration footage to a live session.
**Resolved by:** `8dbd635` — wrist speed expressed in torso-lengths/sec in
`StrokeDetector2D` (torso length = shoulder-mid → hip-mid, scale-invariant).

### L-02 · 10 fps fixtures are too coarse for stroke-peak detection — `RESOLVED`
Phase 1 exports used `intervalMs: 100` (10 fps) — 2–3 samples per forward swing,
systematically underestimated peaks; detector params tuned in frame counts silently
changed meaning with every fps setting.
**Resolved by:** `e00d038` — fixtures re-exported at full video fps (andrii_1 @17ms,
video_2 @20ms); `8dbd635` — all `StrokeDetector2D` tuning windows expressed in
milliseconds, frame counts derived from `intervalMs`.

### L-03 · Every wrist-speed peak is treated as a drill rep — `RESOLVED`
`StrokeDetector2D` had no stroke/non-stroke discrimination; junk peaks (ball pickup,
hand wipe, walking) shifted the baseline mean before 2σ exclusion.
**Resolved by:** `5754a3a` — `RepFilter` bands peaks against the session's median
peak speed and duration; `23890ba`/`85b0ef2` — `ForwardStrokeFilter` direction
filter drops backward/recovery swings via session-level speed-dominance vote.

### L-05 · Rep metrics come from a single peak frame, keypoints unsmoothed — `RESOLVED`
Only the wrist-speed signal was smoothed; RTMPose per-frame jitter fed straight
into the baseline through the single peak frame.
**Resolved by:** `8c7b6a7` — `DrillMetrics.extractAtPeak` takes the median over a
±70 ms window around the speed peak (degrades gracefully to the single peak frame
on coarse fixtures).

### L-08 · Exporter ignores video rotation metadata — `RESOLVED`
`export_poses_rtmpose.py` read width/height via OpenCV header props without
checking the rotation flag — portrait phone videos could invert the aspect-ratio
correction all angle math depends on.
**Resolved by:** `e00d038` — exporter takes width/height from the *decoded* frame,
so rotation is baked in before export; follow-up `629c46a` clamps x/y to `[0,1]`.

### L-28 · Stroke direction measured start→peak misreads continuous play — `RESOLVED`
`ForwardStrokeFilter.wristDx` took wrist x-displacement startFrame→peakFrame; on
continuous shadow play the start boundary bleeds into the previous follow-through
and true drives read backward (video_4: 7 of 12 visually-verified drives dropped,
4 reps from 12). Detection itself was sound — every forward-motion run contained
a raw detector peak.
**Resolved by:** `73b9d00` — direction read over the ~100 ms approach INTO the peak
(`PEAK_APPROACH_WINDOW_MS`, timestamp-walked, clamped to startFrame) in
`ForwardStrokeFilter.kt`, mirrored in the TS harness; stage-level goldens
andrii_1 23/15/15 (unchanged) and video_4 18/12/9 pinned in BOTH suites
(`ForwardStrokeFilterRealFootageTest.kt`, `golden.test.ts`).
