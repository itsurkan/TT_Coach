# Shipped baseline derivation — ShippedBaselines.FOREHAND_ANDRII

Date: 2026-07-28

## Command

```
.venv/bin/python scripts/poses/export_poses_mediapipe.py Videos/andrii_1/andrii_1.mp4 --model lite --interval 17
```

`--interval 17` mirrors the committed full-fps `andrii_1_poses_rtm.json` fixture (source video
is 59.3 fps ≈ 16.86 ms/frame, rounded to 17 ms) — see "Derivation history" below for why this
replaced the script's `--interval` default of 100 ms. The flag accepts any integer (no
enforced minimum in `export_poses_mediapipe.py`'s argparse), so 17 was used directly.

Output: `Videos/andrii_1/andrii_1_poses_mediapipe_lite.json` (schema v2, COCO-17,
`intervalMs: 17`, 1106 frames, 1106 with pose detected — 720x1280, `videoDurationMs=18795`),
force-added to git per the `andrii_1_poses_rtm.json` precedent — `.gitignore` blocks
`/Videos/**` by default. Same path as the first pass — the export script overwrites in place,
so git tracks one file across both derivation passes.

## Derivation call

`DrillCalibrator.calibrate(sequence, drillType = "forehand_drive", createdAtMs = 1L,
handedness = Handedness.RIGHT, minRepCount = 3, cameraYawDeg = 0f)` — see
`ShippedBaselineDerivationHarness.kt` (unchanged between both derivation passes; only the
input JSON's `intervalMs` changed).

**Yaw gate deliberately relaxed**: real per-rep |yaw| on this footage runs **30.0°–90.0°**
across the 15 detected reps (median ≈43.5°), past the normal ~30° placement gate
(`CameraAngleEstimator` saturates at its 90° ceiling on this non-protocol footage, see
`docs/DESIGN_LIMITATIONS.md` L-25). `cameraYawDeg = 0f` treats fixture geometry as reference
for this one-time editorial derivation only — not a precedent for live sessions. (This
rationale and the measured yaw range are essentially unchanged between the 100ms and 17ms
derivation passes — yaw is a pre-stroke ready-stance read, insensitive to peak-frame phase.)

## Derivation history

**First pass (100ms interval, the script's default)** produced a strongly multi-modal
`elbow_angle` distribution (clusters at ~27–38°, ~70–100°, ~116–155° across the 15
`stationary` reps) that survived `BaselineDeriver`'s 2σ outlier exclusion almost untouched —
only 1 of the 4 high-cluster reps was excluded (via `coil_ratio`, not `elbow_angle` itself).
Initial hypothesis was recovery-swing contamination. Orchestrator PNG review of all 15 peak
frames overturned that: all 15 are the same genuine forehand family (consistent stance,
knee_bend, torso_lean, stroke_speed — `ForwardStrokeFilter` had already dropped the 6 real
recovery swings before this point), and the elbow spread co-varied with `shoulder_angle`
across reps rather than forming two cleanly-separated rep populations. That co-variation
pattern is the signature of **peak-frame phase jitter**, not two different movements:
`DrillMetrics.extractAtPeak`'s ±70ms median window is narrow relative to a 100ms sampling
interval, so which exact instant of the ~180ms forward swing gets sampled as "the peak" varies
rep to rep, and a forehand's elbow angle changes continuously through that window (well before
full extension near contact vs. already near contact). Manually excluding the high-elbow reps
would have hand-picked a phase mix rather than fixed the underlying sampling problem, and the
project's `shared/` convention already treats sampling-interval effects as a pipeline concern,
not an editorial one (see `StrokeDetector2D`'s own ms-based tuning windows, chosen specifically
to be fps-independent).

**Fix: re-export at full temporal resolution** (`--interval 17`, matching the committed
full-fps `*_rtm.json` fixture convention) so `extractAtPeak`'s ±70ms window covers ~8 real
frames around the true peak instead of ~1, sampling the actual swing apex rather than
whichever 100ms bucket happened to land near it. Re-running the *unchanged* harness against
this new export tightened `elbow_angle`'s std from 39.1° to **29.9°** (mean 71.6°→64.5°) and
changed which/how-many reps the 2σ exclusion drops (`[0,1,6,8]`→`[0,11,12]`, `repCount`
11→12, `qualityScore` 0.670→0.741). The spread did not fully collapse to a single tight mode —
two reps (rep 7 at 120.0° and excluded rep 11 at 127.8°) still sit above 110° — but this is now
plausibly genuine per-rep contact-angle variation on non-protocol footage rather than a
sampling artifact, and one rep of the two (11) already gets excluded automatically. This is the
final, shipped derivation; the 100ms pass is kept here only as reproducibility history, not as
an alternate candidate.

## Rep selection (final — 17ms/full-fps pass)

- Raw detected: 22 · after ForwardStrokeFilter: 15 · after RepFilter: 15 (no further banding
  removal) · after LocomotionFilter (stationary): 15 · final kept (post 2σ exclusion): **12**
  · excluded as outliers: **[0, 11, 12]**.
- Visual verification (`visualize-pose` skill, peak frames): all 15 peak frames rendered to
  `tmp/shipped_baseline_review/fullfps/rep_<i>_frame_<peakFrame>.png` and read. Confirms the
  same close/near-frontal camera framing as the first pass (consistent with 30–90° measured
  yaw). Reps 7 (elbow=120.0°, kept) and 11 (elbow=127.8°, excluded) are the only two reps with
  `elbow_angle` still above 110° after the full-fps re-derivation: rep 11's peak frame visibly
  shows the racket swung out to the side at hip height with motion blur — a different swing
  phase than the "racket near face" pose common to most other reps — while rep 7's peak frame
  shows the racket up near the face, similar to the low/mid-elbow reps, despite its high
  numeric elbow_angle. Rep 11 is already excluded by the automatic 2σ pipeline; rep 7 remains
  in `metricStats` as a plausible real high-angle contact variant, not hand-patched out (per
  the brief's "one reproducible run" instruction).
- Deviation from pure automatic exclusion: **none** — the full-fps re-derivation replaced the
  need for it; no manual rep exclusion applied on top of `DrillCalibrator`'s own output.

## Numbers (final — 17ms/full-fps pass)

```
=== ShippedBaseline derivation: andrii_1 (MediaPipe-lite) ===
createdAtMs candidate (paste literal): 1785249184303
raw detected=22 forward=15 banded=15 stationary=15
rep[0] peakFrame=67 startFrame=29 endFrame=109 yaw=90.0 shoulder_angle=26.0, knee_bend=177.6, torso_lean=4.9, elbow_angle=32.9, follow_through_angle_2d=167.0, stroke_speed=11.2, coil_ratio=1.1
rep[1] peakFrame=145 startFrame=123 endFrame=183 yaw=41.3 elbow_angle=33.7, shoulder_angle=23.3, knee_bend=174.6, torso_lean=3.4, follow_through_angle_2d=166.3, stroke_speed=10.3, coil_ratio=2.3
rep[2] peakFrame=221 startFrame=204 endFrame=251 yaw=50.8 shoulder_angle=49.3, knee_bend=176.1, torso_lean=5.8, elbow_angle=101.9, follow_through_angle_2d=169.4, stroke_speed=10.5, coil_ratio=1.5
rep[3] peakFrame=289 startFrame=273 endFrame=326 yaw=49.2 shoulder_angle=29.0, knee_bend=176.7, torso_lean=3.7, elbow_angle=40.4, follow_through_angle_2d=167.7, stroke_speed=9.8, coil_ratio=1.0
rep[4] peakFrame=356 startFrame=342 endFrame=394 yaw=46.5 shoulder_angle=24.6, knee_bend=176.3, torso_lean=2.9, elbow_angle=37.5, follow_through_angle_2d=166.6, stroke_speed=9.7, coil_ratio=1.0
rep[5] peakFrame=424 startFrame=410 endFrame=438 yaw=43.7 shoulder_angle=34.2, knee_bend=172.4, torso_lean=2.8, elbow_angle=52.7, follow_through_angle_2d=67.5, stroke_speed=9.3, coil_ratio=0.9
rep[6] peakFrame=496 startFrame=470 endFrame=535 yaw=43.5 elbow_angle=52.2, shoulder_angle=39.1, knee_bend=173.3, torso_lean=3.4, follow_through_angle_2d=147.4, stroke_speed=10.0, coil_ratio=1.1
rep[7] peakFrame=572 startFrame=552 endFrame=606 yaw=45.7 shoulder_angle=42.3, knee_bend=176.9, torso_lean=2.4, elbow_angle=120.0, follow_through_angle_2d=157.2, stroke_speed=9.9, coil_ratio=1.6
rep[8] peakFrame=639 startFrame=616 endFrame=654 yaw=40.4 elbow_angle=74.1, shoulder_angle=40.2, knee_bend=169.6, torso_lean=6.0, follow_through_angle_2d=79.5, stroke_speed=9.8, coil_ratio=2.0
rep[9] peakFrame=710 startFrame=688 endFrame=728 yaw=42.5 elbow_angle=106.4, shoulder_angle=52.2, knee_bend=171.6, torso_lean=6.4, follow_through_angle_2d=59.2, stroke_speed=9.7, coil_ratio=1.5
rep[10] peakFrame=786 startFrame=761 endFrame=830 yaw=42.0 elbow_angle=69.5, shoulder_angle=44.4, knee_bend=176.6, torso_lean=2.9, follow_through_angle_2d=151.5, stroke_speed=10.1, coil_ratio=1.2
rep[11] peakFrame=856 startFrame=840 endFrame=873 yaw=40.5 elbow_angle=127.8, shoulder_angle=48.3, knee_bend=167.8, torso_lean=6.2, follow_through_angle_2d=54.8, stroke_speed=10.3, coil_ratio=2.1
rep[12] peakFrame=929 startFrame=913 endFrame=939 yaw=45.6 elbow_angle=91.8, shoulder_angle=51.9, knee_bend=170.3, torso_lean=10.4, follow_through_angle_2d=63.7, stroke_speed=11.7, coil_ratio=0.6
rep[13] peakFrame=1002 startFrame=988 endFrame=1041 yaw=30.0 elbow_angle=39.4, shoulder_angle=40.6, knee_bend=176.3, torso_lean=3.4, follow_through_angle_2d=166.7, stroke_speed=10.3, coil_ratio=1.0
rep[14] peakFrame=1071 startFrame=1058 endFrame=1084 yaw=36.2 shoulder_angle=36.1, knee_bend=176.3, torso_lean=2.7, elbow_angle=46.8, follow_through_angle_2d=88.6, stroke_speed=10.2, coil_ratio=0.8
=== Derived PersonalBaseline ===
repCount=12 excludedRepIndices=[0, 11, 12] qualityScore=0.7412837894387136
--- metricStats (paste into ShippedBaselines.FOREHAND_ANDRII.metricStats) ---
"elbow_angle" to MetricStats(mean=64.53417587280273, std=29.928821717007263, min=33.66743850708008, max=119.95394134521484, sampleCount=12),
"shoulder_angle" to MetricStats(mean=37.94060389200846, std=9.023364910517047, min=23.295503616333008, max=52.16059112548828, sampleCount=12),
"knee_bend" to MetricStats(mean=174.72368621826172, std=2.431677913758513, min=169.59434509277344, max=176.87957763671875, sampleCount=12),
"torso_lean" to MetricStats(mean=3.8143043319384256, std=1.3922928139415498, min=2.423185110092163, max=6.360828638076782, sampleCount=12),
"follow_through_angle_2d" to MetricStats(mean=132.2988166809082, std=44.34245701413729, min=59.16209411621094, max=169.4313201904297, sampleCount=12),
"stroke_speed" to MetricStats(mean=9.967092275619507, std=0.33454233928057225, min=9.307697296142578, max=10.531898498535156, sampleCount=12),
"coil_ratio" to MetricStats(mean=1.322065035502116, std=0.4782544112683285, min=0.7653630375862122, max=2.3219175338745117, sampleCount=12),
--- phaseDurationsMs (paste into ShippedBaselines.FOREHAND_ANDRII.phaseDurationsMs) ---
"forward_swing_ms" to MetricStats(mean=320.1666666666667, std=80.01117346213513, min=221.0, max=442.0, sampleCount=12),
"stroke_total_ms" to MetricStats(mean=828.75, std=230.27143074680762, min=442.0, max=1173.0, sampleCount=12),
```

`createdAtMs` candidate for Task B: `1785249184303` (this is the run whose numbers are pasted
into `ShippedBaselines.FOREHAND_ANDRII` — supersedes the 100ms pass's candidate).

All 7 `DrillMetrics.ALL_KEYS` have `sampleCount=12` in this pass (unlike the 100ms pass, where
`follow_through_angle_2d` had `sampleCount=9` due to 2 reps missing the value) — every kept rep
has a measurable value for every metric at full fps.

## Superseded — first-pass numbers (100ms interval, kept for reproducibility record only)

Command: `.venv/bin/python scripts/poses/export_poses_mediapipe.py Videos/andrii_1/andrii_1.mp4
--model lite` (no `--interval`, script default 100ms). Raw detected: 21 · forward: 15 ·
banded: 15 · stationary: 15 · kept: 11 · excluded: `[0, 1, 6, 8]` · `qualityScore=0.6695`.
Full stdout block, per-rep table, and PNG list for this superseded pass are preserved in the
Task A report (`.superpowers/sdd/2026-07-28-no-calibration-shipped-baseline/task-A-report.md`)
rather than duplicated here — **do not paste these numbers into `ShippedBaselines.kt`**; use
the final (17ms) numbers above.
