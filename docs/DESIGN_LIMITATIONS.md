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

### L-37 · Shipped `ShippedBaselines.FOREHAND_ANDRII` bands carry Andrii's own camera-yaw error — `ACCEPTED`
Derived with `cameraYawDeg` pinned to 0f (yaw gate consciously relaxed for this one-time
editorial derivation — per-rep |yaw| on the source footage ran well past the normal ~30°
placement gate; `CameraAngleEstimator` saturates on this non-protocol footage, see L-25). The
shipped bands therefore encode "match this recorded stroke as filmed," not a camera-agnostic
universal norm — consistent with the accompanying research appendix's conclusion that no
external numeric target survives scrutiny for this project's 2D included-angle convention.
**Refs:** docs/superpowers/specs/2026-07-27-no-calibration-shipped-baseline-design.md §1;
docs/shipped-baseline-derivation.md; `ShippedBaselines.kt`; L-25.

### L-38 · Standard-mode severity ranking reflects Andrii's own variability, not a player-agnostic scale — `ACCEPTED`
`ShippedBaselines.FOREHAND_ANDRII` is also the σ-carrier baseline `DrillFeedbackEngine.evaluateRep`
normalizes severity against in "standard" reference mode — a metric where Andrii was very
consistent (small σ) ranks small deviations from it more severely than one where he naturally
varied more. Useful as a relative priority order (which cue to say first when several fire at
once); not a validated absolute severity scale.
**Refs:** `DrillFeedbackEngine.kt`; `ShippedBaselines.kt`.

### L-39 · Deleting both seeded drills with no other custom drills resurrects them — `ACCEPTED`
`SeededDrillsPolicy.shouldSeed`'s trigger ("flag unset OR custom_drills table empty") is needed
so a destructive-migration DB wipe re-seeds (the wipe clears Room but not SharedPreferences) —
but it can't distinguish "wiped by migration" from "player deleted every custom drill on
purpose." A player who deletes both seeded rows and has no other custom drill gets them back on
the next app start.
**Refs:** `SeededDrillsPolicy.kt`; docs/superpowers/specs/2026-07-27-no-calibration-shipped-baseline-design.md §2.

### L-40 · Community drills authored before the 7-row editor rework surface only 2 of 7 bands — `ACCEPTED`
`PerPhaseTargetsCodec`'s legacy-key mapping only covers `"knees · strike"` → `knee_bend` and
`"torso tilt · strike"` → `torso_lean` — the other 5 metrics (`elbow_angle`, `shoulder_angle`,
`follow_through_angle_2d`, `stroke_speed`, `coil_ratio`) had no editor row before this rework, so
old shared blobs never carried them. A pre-rework community drill shows those 5 rows unset until
the author re-edits and re-shares.
**Refs:** `PerPhaseTargetsCodec.kt`; `ExerciseEditorActivity.kt`.

### L-41 · `stroke_speed` band read 2.6–4.6 live vs a 9.3–10.6 shipped band — player intensity, not frame rate — `RESOLVED` (2026-09-12)
**Observation:** `ShippedBaselines.FOREHAND_ANDRII`'s `stroke_speed` band (mean 10.0 ±0.3
torso-lengths/s, i.e. 9.3–10.6) was derived from a full-fps (17ms interval) desktop video
export. A real device logcat capture (`adb logcat -s LiveTrainingCtrl`, Samsung S23,
~40 reps) showed live-measured `stroke_speed` of 2.6–4.6 torso-lengths/s on EVERY rep —
16–24σ outside the band, every single time.

**Original hypothesis (2026-07-25), now refuted:** the live MediaPipe camera path samples
the swing far more coarsely than the 17ms export it was calibrated against, so it
structurally under-reads peak wrist speed — a sampling-rate mismatch between how the
baseline was derived and how the metric is measured live. This was never verified before
being recorded, and a 2026-09-12 downgrade to "root cause UNVERIFIED" listed two candidate
causes instead: (a) the S23 test player genuinely swung slower that session, (b) a live
`xScale`/`aspectRatio` mismatch.

**Measurement (2026-09-12):** a frame-rate sensitivity sweep re-ran the identical pipeline
(`StrokeDetector2D` → `ForwardStrokeFilter` → `RepFilter` → `LocomotionFilter` →
`DerivedMetrics`) over the same full-fps `andrii_1` export
(`Videos/andrii_1/andrii_1_poses_mediapipe_lite.json`, intervalMs=17, 1106 frames),
decimating frames to simulate coarser capture:

| decimation | effective intervalMs | reps surviving | mean `stroke_speed` (min–max) |
|---|---|---|---|
| 1x | 17 | 15 | 10.18 (9.31–11.67) |
| 2x | 34 | 15 | 9.22 (8.44–10.27) |
| 3x | 51 | 15 | 9.84 (8.63–11.15) |
| 4x | 68 | 15 | 8.16 (7.39–9.21) |
| 6x | 102 | 0 | — |
| 8x | 136 | 0 | — |
| 12x | 204 | 0 | — |

Across 17ms→68ms (≈60fps down to ≈15fps) the metric drifts only ~1.25×, and
non-monotonically (51ms reads higher than 34ms). At 102ms and coarser, rep detection
collapses to ZERO reps.

**Why this is decisive:** the S23 field log detected reps on EVERY one of ~40 strokes with
stable 2.6–4.6 values. Coarse sampling cannot produce that pattern — it destroys rep
detection well before it halves the metric, and the live session was clearly still
detecting reps normally.

**Supporting checks:** the live path constructs `LiveDrillSession` with a default
`StrokeDetector2D()`, so `smoothingWindowMs=300`/`peakWindowRadiusMs=300`/
`minPeakSpeed=1.0` are IDENTICAL live and in export. Live frame timestamps come from
`imageProxy.imageInfo.timestamp / 1_000_000` (sensor nanoseconds → ms, correct units);
`STRATEGY_KEEP_ONLY_LATEST` drops frames but the median interval then honestly reflects
the real time between *analyzed* frames, so dt is not inflated. Live `aspectRatio`
(CameraX `RATIO_4_3` → xScale ≈ 0.75) is LARGER than the export's (720×1280 → 0.5625);
since the swing is mostly horizontal and the torso mostly vertical, `stroke_speed` scales
with xScale, so the live path should read ~33% HIGHER — pushing the unexplained gap the
wrong direction, not toward it.

**Confirmed cause:** owner (Ivan) confirmed directly that on that S23 session the player
was swinging slowly. Candidate (a) from the 2026-09-12 downgrade is the answer — the
2.6–4.6 readings were a CORRECT measurement of low-intensity shadow reps, not an
under-read. This is a metric-semantics issue, not a pipeline defect.

**Rule that survives:** a `stroke_speed` band is an INTENSITY band, not just a technique
band. A band derived from one clip of full-intensity drives will false-positive on every
rep of a lower-intensity session by the same or another player. Any manually authored
`stroke_speed` value (Exercise Settings editor rows, a seeded drill's bands, or any future
shipped baseline) must be authored for the intensity regime the player will actually train
at — it does NOT transfer across intensity the way the in-plane joint-angle metrics do.
Consistent with the project's "calibrate, don't re-teach" positioning.

**Why this isn't live today:** `PerPhaseReferenceRanges` (the current seeded-drill σ-carrier)
has no `stroke_speed` entry, and `ShippedBaselines.FOREHAND_ANDRII` has no production call
sites (L-43) — so this band cannot fire in today's coaching path. Recorded here as a
resolved diagnosis, and as the rule to apply if `stroke_speed` bands are ever authored or
shipped again.
**Refs:** `docs/shipped-baseline-derivation.md`; `ShippedBaselines.kt`;
`shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/FrameRateSensitivityHarness.kt`
(reproducible measurement); `LiveTrainingController.kt` (`logRep`); L-37, L-38 (same
shipped baseline's other camera/σ caveats); L-43 (no production call sites).

### L-42 · Kotlin and viewer stroke detectors disagree on which strokes are reps — `RESOLVED` (2026-08-16, task 4b)
**Was:** Kotlin `StrokeDetector2D`'s wrist-speed peak NMS was gap-based (suppressed
any peak within `minPeakGapMs` regardless of swing direction); `poses_viewer`'s
`strokeDetector2d.ts` NMS was direction-aware (a backswing and its forward drive can
both survive — `poses_viewer/CLAUDE.md`, 2026-06-15, "user-directed 'viewer-first'").
The two sides disagreed on which raw peaks were kept and, downstream, on which
forward strokes/cycles existed — not a rounding difference, a different rep set.
Visually confirmed against `video_3_rtm.json`: the viewer's extra peak at frame 317
is a genuine forward contact (racket driven toward the wall, elbow open, knees
loaded — same silhouette as the shared frame 706 peak); Kotlin instead picked frame
291 in that region (a recovery/take-back, correctly dropped by `ForwardStrokeFilter`)
and so lost the real stroke entirely.
**Resolution:** ported the viewer's direction-aware refractory into
`MovementDetector.findPeaks` — the min-peak-gap de-dup now only merges two peaks of
the SAME movement direction (a signed horizontal displacement of the tracked
[SignalKeypoint], smoothed the same as the speed signal); an opposite-direction
peak within `minPeakGapMs` is always admitted, so a backswing and its forward drive
~300ms later both survive instead of the gap arbitrarily picking one (this was also
the drive/recovery-symmetry failure mode described by L-27 on shadow play). Kept
generic: direction is computed from `config.signalKeypoint`, not hard-coded to the
wrist/forehand, so the fix serves any future `MovementDetector`-based movement type.
**Measured post-fix** (task-4 parity gate, `PerPhaseParityTest.kt`,
`detect -> ForwardStrokeFilter -> RepFilter -> CyclePairing` chain, cameraYawDeg
pinned 0): `video_3_rtm.json` — viewer 20 cycles, Kotlin 20 (full convergence, all
peakFrames match exactly); `video_4_rtm.json` — viewer 12 cycles, Kotlin 12 (same).
Phase-key-set agreement across all five per-phase metrics is now complete: 20/20
(video_3), 12/12 (video_4) — every matched cycle agrees on which phases are present.
Backswing-pairing coverage, previously 0/16 (0%) and 2/9 (22%) on the Kotlin side
alone, now matches the viewer's own fraction: 18/20 (90%) on video_3, 11/12 (92%) on
video_4. Stage-level goldens moved accordingly
(`ForwardStrokeFilterRealFootageTest.kt`): video_3 raw/forward/reps 42/20/20 (was
tested via the now-withdrawn `andrii_1` fixture pre-4b); video_4 25/12/12, matching
the previously-documented visual ground truth of exactly 12 real forward drives
(pre-fix this read 18/12/9 — 3 of the 12 real drives only survived as reps by luck
of which raw peak the old gap-based NMS happened to keep).
**Product-level consequence, resolved:** the poses_viewer `#/strokes` stroke
table/rep count is now a faithful preview of what the shipped Android app coaches on
the same footage for the two reference fixtures.
**Refs:** `MovementDetector.kt` (`findPeaks`, `rawDx`, `signedDirection`);
`strokeDetector2d.ts` (`findPeaks`, `rawWristDx`); `PerPhaseParityTest.kt`;
`ForwardStrokeFilterRealFootageTest.kt`. `andrii_1` is withdrawn as reference
footage (non-protocol camera angle) — `ForehandDriveEndToEndTest.
ownBaselineStaysMostlyQuietOnOwnReps`, which pins outlier/cue rep indices, is
re-pointed to `video_3_rtm` (commit `f9b995e`, fix round 1 ruling: the property —
a player's own derived baseline stays mostly quiet on the reps it was derived
from — is a real E2E behavioural guarantee, not a footage-specific number, so it
moves rather than staying `@Ignore`d) and runs, unignored, with numbers re-measured
on `video_3_rtm` in the test's own KDoc.

### L-43 · `ShippedBaselines.FOREHAND_ANDRII` was derived under the pre-4b detector, now stale — `RESOLVED` (production references removed, object kept)
L-42's resolution changed which reps the detector finds and keeps (task 4b, 2026-08-16), but
`ShippedBaselines.FOREHAND_ANDRII` — the hard-coded `PersonalBaseline` pasted from a one-time
`ShippedBaselineDerivationHarness` run — was derived under the OLD gap-based `MovementDetector`
and has not been re-derived. It used to ship live in both of its roles: every
`referenceType="standard"` custom drill used it as the σ-carrier `DrillFeedbackEngine.evaluateRep`
normalized severity against (`applyRangeOverrides`/"standard" mode, see L-38), and
`ShippedBaselines.defaultBands()` (mean ± 2σ) derived from its `metricStats`/`repCount` as
`TrainingActivity`'s fallback when a drill launched without the `REFERENCE_TYPE` extra. The
repCount/metricStats/phaseDurationsMs baked into this constant reflect a rep SET the current
detector would no longer reproduce on the same source footage (`andrii_1`) — not just a "different
camera framing" caveat like L-37, but a genuine detector-version mismatch between the numbers
that were shipping and what re-running the (unchanged) derivation harness would now produce.

**Resolved (task 6, 2026-08-16):** both roles are now gone from production code.
`DrillFeedbackEngine`'s "standard"-mode severity fallback derives σ directly from the violated
band's own width (`BaselineRuleFactory.sigmaFromBand`, `mean ± 2σ` convention) instead of a
baseline's `metricStats`, and `TrainingActivity` synthesizes its σ-carrier `PersonalBaseline` from
the drill's own bands (`BandBaselineSynthesizer.fromBands`) rather than passing
`ShippedBaselines.FOREHAND_ANDRII`. `TrainingActivity`'s `REFERENCE_TYPE`-missing fallback now
reads `PerPhaseTargetsSeed.rangeBands()` (the same researched per-phase ranges, e118c2b, that seed
a freshly-created drill) instead of `ShippedBaselines.defaultBands()`. `ShippedBaselines.kt` and
`ShippedBaselinesTest.kt` are deliberately left in place — no remaining production caller reaches
either `FOREHAND_ANDRII` or `defaultBands()` (verified by grep, not deleted); whether to delete the
object and its derivation-record doc is a call for the plan owner, not this task.
**Refs:** `ShippedBaselines.kt`; `ShippedBaselineDerivationHarness.kt`; `DrillFeedbackEngine.kt`;
`BandBaselineSynthesizer.kt`; `PerPhaseTargetsSeed.kt`; L-37, L-38 (this baseline's other
pre-existing caveats); L-42 (the detector change that made this one stale).

### L-44 · Cold-start false-positive reps before `ForwardStrokeFilter`'s facing vote has enough context — `ACCEPTED`
Task 4c fixed `LiveDrillSession`'s stroke-identity dedup (peak-timestamp jitter under
re-detection caused up to 4x same-stroke re-emission — see this entry's sibling fix, and
`LiveDrillSessionParityTest`/`LiveDrillSessionNmsRegressionDiagnosticTest`) but surfaced a
smaller, SEPARATE, pre-existing residual it does not (and structurally cannot) close: on both
`video_3_rtm` and `video_4_rtm`, live emits a small number of extra reps BEFORE the session's
first real rep — video_4: 3 extras (endMs 4097, 3043, 2227, all < first real rep 4607); video_3:
1 extra (endMs 2975, < first real rep 4692). Confirmed NOT a detection difference: running
`MovementDetector.detect()` once over the FULL video_4 sequence (batch mode) produces the exact
same early candidate windows live sees — `(1173,1394,2227)`, `(2227,2516,3043)`,
`(3553,3910,4097)` — so both paths detect the identical raw peaks. The difference is entirely in
`ForwardStrokeFilter`: its session-facing speed-dominance vote only overrides the noisy
per-frame head-facing fallback (L-04) once it has seen `MIN_GROUP_SIZE` (2) verified strokes in
EACH wrist-dx direction (L-27 covers the same vote's other failure mode). Batch supplies that
from the WHOLE session; live, at the moment these early candidates first stabilize (within the
first ~2 real strokes' worth of buffer), has not yet accumulated its OWN later strokes to supply
that evidence — a causal limit: the disambiguating context does not exist yet at the time these
early strokes must be committed to keep feedback live. No live-path change (identity scheme,
buffering, dedup) can close this without either changing `ForwardStrokeFilter` (task 4c's brief
explicitly disallows it) or redesigning the live path to delay-commit early strokes until
`MIN_GROUP_SIZE`-per-direction is satisfied (a materially bigger change than "stable identity
under re-detection" — a candidate follow-up, not attempted here).
**Why accepted, not blocking:** each cold-start artifact fires at most once (task 4c's dedup
correctly recognizes its own re-detection and does not re-emit it), always lands before the
player's first real stroke of the session (i.e. before there is anything to compare it against
in the stroke-snapshot carousel/session stats), and is small in count (1–3 measured). The
`liveReplayMatchesBatchRepSetOnVideo3/4ShadowPlay` tests assert this precisely: zero repeated
timestamps anywhere, and an EXACT (TRAILING-tolerant) match against batch for every rep from the
first real one onward — the cold-start prefix is reported, not silently size-bounded away.
**Refs:** `LiveDrillSession.kt` (`emittedWindows`); `ForwardStrokeFilter.kt`
(`speedDominantFacing`, `MIN_GROUP_SIZE`); `LiveDrillSessionParityTest.kt`
(`assertLiveMatchesBatchModuloColdStart`); L-04 (noisy head-facing fallback), L-27 (the vote's
other below-ratio failure mode).

### L-45 · Two of the four declared phase-duration keys are never produced — `OPEN`
`BaselineDeriver` declares all four phase-duration keys (`PHASE_BACKSWING_MS`,
`PHASE_FORWARD_SWING_MS`, `PHASE_FOLLOW_THROUGH_MS`, `PHASE_STROKE_TOTAL_MS`), and the frozen
legacy 3D path (`BaselineDeriver.derive` → `extractPhaseDurations`, fed from `DetectedStroke`
boundary frames) does compute all four. But the 2D pivot's live/calibration path —
`MovementCalibrator.calibrate` (and, via task 7's `TempoMetrics.forStroke`, `DrillRepProcessor`/
`MovementAnalyzer`) — only ever produces `PHASE_FORWARD_SWING_MS` (`peakFrame - startFrame`) and
`PHASE_STROKE_TOTAL_MS` (`endFrame - startFrame`). `PHASE_BACKSWING_MS` and
`PHASE_FOLLOW_THROUGH_MS` have no 2D-pivot source: `Stroke2D` only carries `startFrame`/
`peakFrame`/`endFrame` — there is no direction-reversal analysis to locate a backswing-start or
follow-through-end boundary within that window. Consequence: `PersonalBaseline.phaseDurationsMs`
never contains these two keys on the 2D pivot, so a `RhythmRule` for them is never derived, and —
now that task 7 makes tempo authorable — an editor row bound to `backswing_ms`/`follow_through_ms`
would produce a `RangeRule` whose `metrics` lookup always misses (silent, per the documented safe
behavior for an absent measurement, but permanently so: no code path will ever populate a value).
**Do not wire an editor row to these two keys** until a backswing/follow-through boundary
detector exists.
**Refs:** `TempoMetrics.kt`; `MovementCalibrator.kt`; `BaselineDeriver.kt`
(`PHASE_BACKSWING_MS`/`PHASE_FOLLOW_THROUGH_MS` declarations, `extractPhaseDurations` — the
legacy path that DOES compute them); `Stroke2D.kt`.

### L-46 · Per-phase ConsistencyRules dilute the session summary's flagged-rep count — `OPEN`
Task 9a wires the five per-phase metrics' composite `metric@phase` values into all three
per-rep metrics maps, and (for `MovementCalibrator.calibrate`, via the isolated
`repExtraMetrics` channel — see the task-9a report) into `PersonalBaseline.metricStats` too.
`BaselineRuleFactory.defaultRules` auto-derives one `ConsistencyRule` (two-sided 2σ) per
metric key present in `metricStats` with `std > 0`, with no distinction between a
single-instant metric and a per-phase composite one. A calibrated baseline that used to carry
~7 ConsistencyRules (the bare `DrillMetrics.ALL_KEYS`) now carries up to 17 (7 bare + 10
composite) — more than double the number of independent-ish 2σ checks run against every rep.

Consequence: the count of reps `DrillFeedbackEngine.evaluateRep` flags (`RepAnalysis.cues.
isNotEmpty()`, what session-summary "flagged rep" counters read) rises measurably even when
the player's technique hasn't changed — re-measured in `ForehandDriveEndToEndTest.
ownBaselineStaysMostlyQuietOnOwnReps` on video_3_rtm: borderline non-outlier flags went from
1/20 (7 rules) to 3/20 (17 rules) with the SAME reps and the SAME baseline-derivation outlier
decision. This does NOT change how often the app speaks — `FeedbackCadencePolicy` still emits
at most one cue per 3–5s window, chosen by max severity, so a larger rule set changes WHICH
cue wins that window, not how many windows fire. But it does mean **the session summary's
flagged-rep count is now a weaker signal of technique quality than it was**, because it scales
with how many rules happen to exist, not purely with how consistent the player's strokes were.
Any future change to the rule count (more phased metrics, more movements) will shift this
further in the same direction unless addressed.
**Not fixed here** — candidate directions (not evaluated): a per-metric-count-aware severity
threshold, excluding composite keys from `defaultRules`' auto-generation (leaving per-phase
coaching purely to explicit editor/seeded bands), or reporting flagged-METRIC-count rather
than flagged-REP-count in session analytics.

**Wider than the session summary alone (app/, checked task 9a):** `LiveTrainingController.
synthesizeAnalysisResult` scores a rep 95 (clean) vs. 65 (flagged) from this SAME raw
`rep.cueCount == 0` signal — NOT cadence-gated — feeding `TrainingStateManager.
getTotalHits`/`getSuccessfulHits`/`getAverageScore`, which drive the LIVE in-session progress
bar/accuracy (`TrainingUIController.updateStats`) in real time, and are persisted on save
(`TrainingActivity.saveSessionToCloud`: `correctStrokes`, `averageScore`) into
`ProgressDataLoader`'s weekly accuracy chart. So accuracy dilutes live, during the session, not
only in a post-session summary — the app's day-to-day skill-progress signal, not just one
screen. By contrast, `RepCarouselView`'s snapshot highlight and the on-screen feedback-type
list/count (`tvFlagged`) are driven by the CADENCE-GATED `SpokenFeedback` stream, not raw
`cueCount` — those are NOT diluted in rate (only in which `CorrectionType` wins), consistent
with the cadence-rate reasoning above. Streak count itself appears to be session-occurrence-
based rather than accuracy-based (not independently verified in depth here).
**Refs:** `BaselineRuleFactory.defaultRules`; `DrillFeedbackEngine.evaluateRep`;
`ForehandDriveEndToEndTest.ownBaselineStaysMostlyQuietOnOwnReps` (re-derived bound + full
reasoning); `LiveTrainingController.synthesizeAnalysisResult`; `TrainingStateManager`;
`TrainingActivity.saveSessionToCloud`; `ProgressDataLoader`; task-9a report
(`.superpowers/sdd/task-notification-task-id-a59ecfedad4dc-cuddly-seal/task-9a-report.md`).

### L-47 · Five metrics have no pre-recorded voice clips — `OPEN`
`follow_through_angle_2d`, `stroke_speed`, and `coil_ratio` are part of `DrillMetrics.ALL_KEYS`
(the 8-cue RTM correction taxonomy) and have phrase text in `VoicePresetCatalog` for all 3 built-in
styles × both languages × both directions, but NONE of those phrases resolve to a recorded clip in
any of `app/src/main/assets/voice/{preset-playful,preset-strict,preset-efficient}/manifest.json` —
verified by hashing every catalog phrase via `VoiceClipKeys.clipKey` and checking manifest
membership (`VoicePresetManifestCoverageTest.unresolvedCatalogPhrasesMatchKnownGapAllowlistExactly`).
Not a port defect (task 10's phased-phrase port is separately verified clean, zero misses) — the
audio for these 3 metrics was simply never recorded when the manifests were generated. Consequence:
`PresetVoiceController.speak` always falls through to live TTS for these 3 metrics' cues, on all
3 styles, in both languages — never plays a pre-recorded clip.

Fix round (final review, fix 4) added two more: `forward_swing_ms`/`stroke_total_ms` (tempo cues)
got brand-new `VoicePresetCatalog` phrases with no recorded clip at all — there was never an
opportunity to record them, since these keys never cued before this fix round. Same consequence:
always falls through to TTS.

**Not fixed here.** The exact 60 missing (style, lang, phrase) triples (36 for the original 3
metrics + 24 for the 2 tempo keys: 3 styles × 2 langs × 2 metrics × 2 directions) are enumerated in
`VoicePresetManifestCoverageTest`'s `KNOWN_MISSING_CLIP_PHRASES` allowlist — an exact-match set,
not an upper bound, so recording a metric's clips must shrink the allowlist (the test fails loudly
either way: a new miss, or a listed phrase that now resolves).
**Refs:** `VoicePresetCatalog.kt`; `VoiceClipKeys.kt`; `VoicePresetManifestCoverageTest.kt`
(`app/src/test/java/com/ttcoachai/services/`); `app/src/main/assets/voice/*/manifest.json`.

### L-48 · `PER_PHASE_RANGES` is entirely coach opinion, not measurement — `OPEN`
`PER_PHASE_RANGES` (`PerPhaseReferenceRanges.kt`, mostly ported from
`poses_viewer/src/drill2d/referenceStandard.ts`, with four deliberate exceptions below) is now
the seed source for every new drill's bands (`PerPhaseTargetsSeed`, replacing
`ShippedBaselines.defaultBands()` — see L-43) and the `REFERENCE_TYPE`-missing fallback
`TrainingActivity` reads.

**Update 2026-08-16 (owner ruling, `feat/per-phase-metric-grid`):** a whole-branch review
measured the seeded bands against `video_3`/`video_4` parity fixtures (32 cycles) and found
`knee_bend@BACKSWING` at 0/29 in band, `knee_bend@CONTACT` at 0/32, and
`shoulder_angle@FOLLOWTHROUGH` at 2/31 — the `knee_bend` entries' `Evidence.MEASURED` tag was a
Bańkosz & Winiarski JSSM 2020 literature conversion (3D sagittal knee flexion) that does not
transfer to the 2D hip–knee–ankle interior angle this pipeline actually computes. The project
owner re-set all three bands to cover the measured range as default seed values (not re-derived
research): `knee_bend@BACKSWING`/`@CONTACT` → 120–150, `shoulder_angle@FOLLOWTHROUGH` → 60–110.
Post-change coverage on the same fixtures: `knee_bend@BACKSWING` 15/29 (median 149.8, in band),
`knee_bend@CONTACT` 31/32, `shoulder_angle@FOLLOWTHROUGH` 29/31. Both `knee_bend` entries lost
their `Evidence.MEASURED` tag (now `Evidence.COACH_OPINION`, with the superseded literature
citation kept in the `source` string as history) — **all 10 entries are now
`Evidence.COACH_OPINION`; zero carry `Evidence.MEASURED`.** A regression guard
(`PerPhaseSeededBandCoverageTest`, jvmTest) now asserts every seeded band contains the measured
median across the parity fixtures, plus a stricter ≥50%-of-reps-in-band check — this is the
check whose absence let the old bands ship.

**Update 2026-08-16, second pass (owner ruling + attribution correction, same branch):** the
`shoulder_angle@FOLLOWTHROUGH` 60–110 band recorded above was misattributed in
`PerPhaseReferenceRanges.kt`'s `source` string as a "project owner" choice — it was in fact an AI
assistant's provisional pick, never actually ruled on by the owner. The owner has now made the
real ruling: `shoulder_angle@FOLLOWTHROUGH` → 55–85 (was 60–110; measured 14.5–83.1°, median
71.9°, n=31 — new band covers 30/31 reps). The `source` string has been corrected in place to say
so plainly (misattribution stated, not silently overwritten). Separately, the owner also ruled on
the `hip_flexion@CONTACT` residual noted below: band re-set from 120–165 → **115–150** (measured
113.3–177.3°, median 121.3°, n=32 — new band covers 28/32 reps, up from 16/32). Both changes are
recorded in the class kdoc and the entries' `source` strings.

**Residual — RESOLVED 2026-08-16 (second pass, see above):** `hip_flexion@CONTACT`'s coverage gap
(median in band, but only 16/32 reps) has been fixed by the owner's 115–150 ruling (28/32 now in
band, above the ≥50% guard). The `PerPhaseSeededBandCoverageTest.KNOWN_LOW_COVERAGE_BANDS`
allowlist entry for this pair has been removed accordingly (kept as an empty set for future use,
not deleted as a mechanism).

Of the 10 (metric, phase) entries, 6 (`elbow_angle`×2, `shoulder_angle@BACKSWING`,
`hip_flexion@BACKSWING`, `torso_lean`×2) remain untouched `Evidence.COACH_OPINION` — heuristic, no
measured source — and several of their `source` strings still carry explicit "PROVISIONAL" /
"UNVERIFIED" / "sources DISAGREE" warnings verbatim in the code. Consequence: a freshly-seeded
drill's editor grid shows precise-looking degree numbers for all 10 rows, and with zero entries
now `Evidence.MEASURED`, the editor's ✓ "measured" marker (see mitigation below) currently has
nothing left to mark — worth a follow-up UI pass, not addressed on this branch.
**Mitigation shipped (now stale re: the ✓ marker):** the editor's per-phase grid legend
(`R.string.exercise_editor_evidence_hint`, "✓ = measured (published research). Unmarked ranges
are coach-estimated, not measured.") and the ✓ suffix on the two former measured row labels
(`exercise_editor_row_knees_backswing`, `exercise_editor_row_knees_strike`) surfaced the evidence
grade at a glance; since 2026-08-16 those two rows are no longer `Evidence.MEASURED`, so the ✓
labels are now inaccurate and should be revisited. Nothing else in the app (baseline hints,
community-drill sharing, the seeded-drill bands themselves once encoded to
`perPhaseTargetsJson`) retains the `Evidence` tag or `source` string — it exists only in
`PerPhaseReferenceRanges.kt` and the editor's static legend/row labels.
**Would actually resolve it:** re-deriving the 6 remaining `COACH_OPINION` entries (plus
re-validating the 4 owner-ruled ones) from protocol-shot footage (camera-placement protocol, side
view, `|yaw| < 30°`), or at minimum a written source study per entry, and removing/relabeling the
now-stale ✓ "measured" UI marker.
**Refs:** `PerPhaseReferenceRanges.kt` (`PER_PHASE_RANGES`, `Evidence` enum, kdoc);
`PerPhaseReferenceRangesTest.kt` (table-driven, documents the 3 intentional TS divergences);
`PerPhaseSeededBandCoverageTest.kt` (new regression guard); `poses_viewer/src/drill2d/referenceStandard.ts`
(NOT edited — the 3 divergences are Kotlin-only, deliberate); `PerPhaseTargetsSeed.kt`;
`activity_exercise_editor.xml` (`exercise_editor_evidence_hint` legend, now stale re: ✓); L-43
(this is the provenance `ShippedBaselines.defaultBands()` was replaced BY, not the same
limitation).

### L-49 · `andrii_1` is a withdrawn reference clip — do not derive or tune values from it — `OPEN`
`andrii_1` was withdrawn by the project owner as reference footage: it was not shot to the
camera-placement protocol, the camera sits roughly 45° in front of the player rather than to
the side, and the player is shadow-swinging away from the table rather than actually striking.
This is a standing constraint on **any** future work, not specific to one derived artifact:
**`andrii_1` must not be used to derive or re-tune reference ranges, baselines, or thresholds,
and a test result or measurement obtained only over `andrii_1` must not be relied on as
evidence of correctness.** It remains fine to use as a pipeline-mechanics fixture (detector
bring-up, regression coverage of code paths) precisely because those uses don't claim the
numbers it produces are representative technique. `video_3` and `video_4` are the current
reference clips (protocol-compliant side view; see L-30/L-42/L-46's re-measurements, both
already run on `video_3_rtm`/`video_4_rtm`).
L-43 already covers the specific fallout — `ShippedBaselines.FOREHAND_ANDRII`, a baseline
object derived from `andrii_1`, going stale and being removed from production callers. This
entry is the broader rule that L-43 is one instance of: it applies to any future
derivation/tuning work over this clip, not only to that one object.
**Refs:** `PerPhaseReferenceRanges.kt` kdoc (states the withdrawal); L-43 (the specific stale
baseline this rule already forced out of production); L-25 (the yaw-estimator saturation that
was one symptom of `andrii_1`'s bad camera angle); `Videos/video_3`, `Videos/video_4`.

### L-50 · 2D torso-lean is inflated by axial rotation, same root cause as L-25 — `OPEN`
`PER_PHASE_RANGES`'s `torso_lean` entries (both `BACKSWING` and `CONTACT`, both
`Evidence.COACH_OPINION`) carry `source` strings stating the 2D-projected lean angle is
inflated by the player's axial (shoulder/hip) rotation during the stroke and "needs re-tuning
on protocol footage" — the `CONTACT` entry additionally notes it was set from "own footage
33–39°", i.e. read off `andrii_1`/similar non-protocol clips rather than measured (see L-49:
those readings must not be treated as reference values). Root cause is the same 2D-projection
problem as L-25 (camera-yaw estimation saturating on non-protocol footage): a side-on camera
model cannot separate genuine forward lean from rotation-induced foreshortening when the
player's shoulders/hips are turning through the stroke, so both the yaw estimate and any
angle measured off that projection (torso lean here) degrade together on the same footage.
Consequence: the `torso_lean` bands seeded into every new drill inherit this uncorrected
inflation, on top of already being `COACH_OPINION` (L-48).
**Refs:** `PerPhaseReferenceRanges.kt` (`torso_lean` `source` strings); `CameraAngleEstimator.kt`;
L-25 (shared root cause); L-48 (this metric's ranges are also unmeasured); L-04 (the related,
separately-tracked head-facing sign-normalization noise).

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

### L-36 · Baselines derived from full-fps export are tighter than live pose noise — `ACCEPTED` (mitigated, not fixed)
A device log caught a player with essentially straight legs (`knee_bend=169.1`)
told to straighten up: the session band was `[169.9, 179.6]` (mean 174.7, σ 2.4),
so the value was 0.8° outside the edge and produced a spoken cue. The SAME rep,
read from adjacent peak frames, measured `knee_bend=169.1` and `knee_bend=172.5` —
3.4° of spread on one stroke — and `elbow_angle=76.5` vs `84.5`, ~8° of spread.
That is live MediaPipe measurement noise, not player movement. `BaselineRuleFactory`
bands are derived as mean±2σ from `BaselineDeriver`, whose σ is measured against a
full-fps desktop pose export (`ShippedBaselines.FOREHAND_ANDRII` at `--interval 17`)
— a much cleaner signal than the live on-device path. A tight-σ metric like
knee_bend (σ=2.4°) therefore yields a band narrower than live pose noise, so
sub-noise excursions routinely fall outside it and get coached as real faults.
**Mitigated by:** a per-metric cue deadband (`CueDeadbands.forMetric`, 3.0° for the
5 precise in-plane angle metrics, modeled directly on the same log's jitter
evidence) applied in `DrillFeedbackEngine.evaluateRep` — a value must clear both
the band AND the noise floor before it cues. This is a floor, not a fix: it
suppresses the symptom without correcting the underlying band-too-tight cause.
**Revisit trigger / fix direction:** band derivation should eventually account for
live measurement error directly — e.g. widening `BaselineRuleFactory`'s ±2σ band by
a live-noise term, or having `BaselineDeriver` incorporate a measured live-noise
component instead of relying solely on the deadband floor.
**Refs:** `CueDeadbands.kt`; `DrillFeedbackEngine.evaluateRep`; `BaselineDeriver.kt`;
`BaselineRuleFactory.kt`; `ShippedBaselines.kt`.

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
