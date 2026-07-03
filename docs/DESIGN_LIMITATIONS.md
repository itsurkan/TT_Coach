# Design Limitations Register

Project-wide register of known design limitations, accepted trade-offs, and open
design issues. One entry per limitation; update **Status** in place, don't delete
entries (move resolved ones to the bottom section with the resolving commit/doc).

**Status values:**
- `OPEN` — needs a design decision or fix; no mitigation in any plan yet
- `PLANNED` — mitigation exists in a written plan/spec, not yet implemented
- `ACCEPTED` — known and consciously deferred (record why and the revisit trigger)
- `RESOLVED` — fixed; entry moved to the Resolved section

Last updated: 2026-06-17

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

### L-36 · ms→frame window quantization sensitivity in `StrokeDetector2D` — `ACCEPTED` (mitigated; residual by design)
`StrokeDetector2D.framesFor()` converts ms-based tuning windows (smoothing,
peak-radius, `minPeakGapMs`) into integer frame counts via `intervalMs`; the result
depends on the estimated frame interval. A **sustained** interval change (e.g. a
17ms→18ms mean-fps shift) can move a window by ±1 frame at a floor boundary and
change which peaks survive NMS — on `andrii_1_rtm` a deliberately-constructed
sustained +1ms shift split 1 stroke into 2 in several places (23→27 raw strokes).
**Mitigation applied (this task):** `framesFor()` was changed from truncation
(`(ms / intervalMs).toInt()`) to integer rounding
(`((ms + intervalMs / 2) / intervalMs).toInt()`) so a small interval-estimate change
doesn't cross a floor boundary as sharply. **Kept experimental, not shipped**: the
rounding change regressed `LiveDrillSessionParityTest` (A3) — because
`LiveDrillSession` re-runs `StrokeDetector2D.detect` on the growing/trimmed live
buffer every frame (not once over the full sequence like the batch analyzer), the
rounding shifted a frame-count boundary on an early, small buffer and flipped one
rep's emitted direction (`TOO_LOW`→`TOO_HIGH` at cue index 3) relative to batch. Per
the proven-gate guardrail this was reverted; `framesFor()` still truncates. The
live-path re-detection-on-growing-buffer interaction is a separate, deeper
finding — not yet root-caused — and should be investigated before rounding is
retried.
**What DOES hold (proven by the reworked A4 `LiveDrillSessionStabilityTest`):** the
live path estimates `intervalMs` as the MEDIAN of consecutive frame deltas over the
buffer (L-26), and realistic **symmetric** sub-frame jitter (a deterministic
cancelling-pulse model, most deltas untouched, a few ±2ms nudges that cancel) leaves
that median exactly equal to the true capture interval — verified empirically
(logged `jitteredMedian == seq.intervalMs`) — and the emitted cue sequence is then
provably identical to the unjittered reference. Small per-frame timing noise is
genuinely absorbed; this task's quantization concern is about a *sustained* interval
change, not ordinary jitter.
**Residual (by design, not a bug):** a real sustained fps change still moves the
median and can legitimately shift detected/emitted reps — the on-device
calibrate+feedback loop stays mutually self-consistent (both read the same live
median), but device-vs-desktop rep-count divergence from a true fps difference is a
known, accepted possibility, gated separately by T7 (on-device parity fixture).
**Refs:** `StrokeDetector2D.kt` (`framesFor`, truncating); `LiveDrillSessionStabilityTest.kt`
(A4, reworked); `LiveDrillSessionParityTest.kt` (A3, the regressed gate); L-26.

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

### L-32 · Per-phase ideal ranges are provisional (extends L-29) — `OPEN`

`PER_PHASE_RANGES` in `poses_viewer/src/drill2d/referenceStandard.ts` seedes per-phase bands (knee, hip, shoulder × backswing / contact / follow-through) primarily from Bańkosz & Winiarski JSSM 2020.
Several conversion issues are partly unverified:

- The Bańkosz source uses a **flexion** convention (0° = straight joint); this codebase uses **interior** angles (0° = fully folded, 180° = straight). The conversion is `interior = 180 − flexion`, but the mapping of which Bańkosz stroke instant corresponds to which `Phase` enum value has not been independently verified against the original video-protocol description.
- **Hip flexion sources disagree:** Bańkosz JSSM 2020 reports ~22° at backswing; the companion PeerJ 2021 paper reports ~63°. Different phase-boundary conventions are the likely cause. The hip range is therefore seeded wide and tagged `coach_opinion`.
- **Shoulder follow-through** mapping (Bańkosz ~97° flexion ≈ 83° interior elevation) is plausible but unverified against video.

**Backswing-end availability and data quality:**

The backswing phase is produced only for ~75% of cycles (unpaired cycles — forward drives whose paired backswing peak was not detected or fell outside `MAX_PAIR_GAP_MS` — omit it). The backswing-end instant is also the worst-tracked moment in the stroke: the racket arm passes behind the torso, reducing keypoint scores and triggering the `score < 0.3` null gate more often. Expect more blank cells in the backswing column than other phases; this is correct score-gate behavior, not a bug.

**Shoulder-coil indicator (`shoulderCoil.ts`):**

«Скрутка» is a LOW-CONFIDENCE qualitative proxy: it measures the projected shoulder-width foreshortening ratio (backswing vs follow-through). This signal is noisy, confounded by player translation, and sensitive to camera yaw and body sway. `COIL_OPENED_RATIO = 1.25` is a provisional heuristic. The indicator intentionally produces no degree value (trust rule; see L-21); it emits only soft qualitative labels.

**Fix direction:** re-tune all per-phase bands + the coil ratio on protocol footage (following the L-30 precedent). Verify the interior-angle convention mapping against the Bańkosz phase-boundary protocol. Cross-link: L-29 (single-instant ranges provisional), L-04 (sign noise affects display-only torso_lean), L-30 (protocol footage re-tune precedent).

**Refs:** `poses_viewer/src/drill2d/referenceStandard.ts`; `poses_viewer/src/drill2d/shoulderCoil.ts`; `poses_viewer/src/drill2d/drillMetrics.ts`; experiments log 2026-06-17.

### L-31 · Cycle RepFilter dropped real drives that lack a paired backswing — `RESOLVED`
After direction-aware NMS + the full-cycle model (2026-06-15), a forward drive whose backswing
peak isn't detected (or is >`MAX_PAIR_GAP_MS` away) becomes an UNPAIRED cycle whose span is just the
~0.5s drive-half. RepFilter's old duration LOWER bound (`medDur/2`) dropped it as "too short",
losing two real `andrii_1` topspin drives (@1.14s, @4.88s, normal speed ~8 torso/s) → 13 instead of
15. The same bound usefully dropped `video_4`'s trailing junk (@15.74s, 0.42s, **0.68× median speed**).
**Resolved by** relaxing the lower bound in `filterCycleReps`: a short cycle is dropped only when it
is ALSO slow (`< SHORT_STRONG_SPEED_FRACTION = 0.85 × median speed`). A short-but-STRONG cycle is a
real fast/unpaired drive (kept); a short-AND-slow one is junk (dropped). The fraction sits on a wide
stable plateau (0.75–0.95 all give the same counts), not a knife-edge — andrii's drives (0.96×, 1.09×)
are kept, video_4's junk (0.68×) dropped. Final: video_3=20, video_4=10, andrii_1=15, all pinned by
`strokeCountContract.integration.test.ts`. The detector is stroke-type- and camera-agnostic
(andrii is topspin from a different camera; it still counts 15).
**Refs:** `repFilter.ts` `filterCycleReps` / `SHORT_STRONG_SPEED_FRACTION`; `cyclePairing.ts`;
`strokeCountContract.integration.test.ts`.

### L-30 · Locomotion (walking) counted as reps — `RESOLVED` (gate default-on + Kotlin-mirrored; threshold provisional)
The detect → ForwardStrokeFilter → RepFilter chain keys on wrist speed + forward direction +
speed/duration banding — none of which distinguish a forehand drive from a player walking while
swinging the arm. On `video_4_rtm` a walking step at 15.18 s (peak 5.8 torso/s) is counted as a
rep. Diagnosed via hip-mid horizontal travel (torso-length-normalized): genuine drives keep the
hips planted (0.09–0.25 torso on andrii_1 + video_4), the walking rep travels 0.68 torso — a clean
3–4× separation.
**Fixed (2026-06-15, user-directed full fix):** `LocomotionFilter` (`hipMidTravelTorso`,
`filterStationary`, `DEFAULT_MAX_TRAVEL_TORSO = 0.4`) added to Kotlin `shared/drill/` (source of
truth) and mirrored 1:1 in `poses_viewer/src/drill2d/locomotionFilter.ts`. Wired into the default
pipeline (`DrillCalibrator`, `ForehandDriveDrillAnalyzer`, `countStrokes`, `analyzeDrill`) **on by
default**; the «Гейт ходьби» knob can set 0 to disable. Both golden suites updated in the same
change: video_4 final count **9 → 8** (`ForwardStrokeFilterRealFootageTest`, `golden.test.ts`);
andrii unchanged at 15. `IosRtmposeParityTest` holds the gate off (it compares backends, not gate
behavior, on marginal 3–4-rep `video_2`).
**Residual caveat:** the 0.4-torso threshold was picked on non-protocol `Videos/` footage and is a
single global value; on distant footage (`video_2_ios_rtm`) a large-movement swing reads 0.868 torso
and is also dropped — fine here, but re-tune + freeze it on protocol footage before treating it as
final. Strokes whose hip travel can't be measured are KEPT (never reject on absence of evidence).
**Refs:** `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/LocomotionFilter.kt`,
`poses_viewer/src/drill2d/locomotionFilter.ts`, `countStrokes.ts`, `analyzeDrill.ts`,
`StrokesPage.tsx`; relates to L-03 (resolved precursor), L-27.

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

### L-33 · Out-of-plane arm movement (elbow flyout) not tracked — `ACCEPTED` (revisit post-MVP)
`shoulder_angle` (hip–shoulder–elbow) collapses the entire in-plane arm sweep
(forward/back + up/down) into one interior angle and is blind to the depth axis —
abduction toward/away from a side camera, i.e. the "chicken wing" / elbow-flyout
fault. This is a real, commonly-coached forehand fault and the one dimension a
pure side-camera 2D angle set genuinely misses.
**Why deferred (not adding now):** (1) worst noise/value ratio of any candidate
metric — foreshortening recovery `θ = acos(L_proj / L_true)` has a dead zone below
~30° tilt and no toward/away sign, so it only fires on extreme flyout, which
`shoulder_angle` already moves on; mostly a noisy projection of signal already
captured. (2) A new low-confidence metric erodes trust in the validated ones, and
false positives are toxic to the calibrate-don't-re-teach positioning. (3) The
existing 5 in-plane metrics + ideal ranges are still provisional (L-29, L-32) —
don't stack a 6th axis on an unvalidated base.
**Revisit trigger / fix direction:** after the 5 metrics are validated on protocol
footage, check on real reps whether a flyout fault survives uncaught; only then add
it as a QUALITATIVE label (`'tucked' | 'flying_out'`), styled like `shoulderCoil`
(kept out of the °-formatter / severity coloring), NEVER a numeric angle in
`PER_PHASE_RANGES`. Precise depth would instead argue for a frontal second camera.
**Refs:** `AngleCalculations2D.shoulderAngle`; `shoulderCoil.ts`;
`CameraAngleEstimator` (same foreshortening math, shipped for yaw); trust rule (L-21).

### L-34 · Non-forehand drills reuse forehand-tuned detection — `ACCEPTED` (revisit post-MVP)
`StrokeDetector2D` / `ForwardStrokeFilter` are tuned for the forehand drive. The
Exercises tab (2026-07-02 redesign) launches every program — backhand, footwork,
multiball, custom clones — through the existing training flow, but only forehand
drills produce calibrated feedback. Editing params for a non-forehand drill (this
slice / the deferred editor slice) does not yet yield accurate coaching.
Generalizing stroke detection per drill type is deferred; until then, non-forehand
feedback accuracy is not claimed.
**Refs:** `docs/superpowers/specs/2026-07-02-android-exercises-tab-gold-dark-design.md`.

### L-35 · Exercise editor fields not consumed by the live feedback analyzer — `ACCEPTED` (revisit post-MVP)
The exercise editor (screens 10c New / 10d Clone/Edit) persists focus areas,
reference type, strictness, and per-phase targets onto `CustomDrillEntity`
(`focusCsv`, `referenceType`, `strictnessX`, `perPhaseTargetsJson`, `baselineId`).
The editor round-trips these fields faithfully (create/clone/edit all read and
write them correctly), but the live drill run does not yet read them back — the
Phase 2 rule evaluator (`FrameRuleEvaluator`/`DrillFeedbackEngine`) is not wired to
apply per-drill overrides at runtime. This is the same deferral already true of
`DrillConfigEntity` (coach-tuned drill-shape overrides, also unconsumed at
runtime). `baseTemplate` on a NEW drill is currently self-referential (its own
`custom_...` drillType) — verified harmless today since nothing resolves icons or
analyzer selection via `baseTemplate` (icon lookup keys off `drillType`/`id` with a
safe default; drill-type resolution keys off `EXERCISE_ID`), but any future code
that starts consuming `baseTemplate` for analyzer/icon selection must special-case
or default the NEW-mode self-reference.
**Refs:** `ExerciseEditorActivity.kt` (`onPrimaryClicked`); `CustomDrillEntity.kt`;
`DrillsFragment.kt` (`iconForDrill`); L-20 (`DrillConfigEntity` same deferral).

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
