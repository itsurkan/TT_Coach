# Shipped baseline derivation — ShippedBaselines.FOREHAND_ANDRII

Date: 2026-07-28

## Command

```
.venv/bin/python scripts/poses/export_poses_mediapipe.py Videos/andrii_1/andrii_1.mp4 --model lite
```

Output: `Videos/andrii_1/andrii_1_poses_mediapipe_lite.json` (schema v2, COCO-17, `intervalMs: 100`,
188 frames, 188 with pose detected — 720x1280), force-added to git per the
`andrii_1_poses_rtm.json` precedent — `.gitignore` blocks `/Videos/**` by default.

## Derivation call

`DrillCalibrator.calibrate(sequence, drillType = "forehand_drive", createdAtMs = 1L,
handedness = Handedness.RIGHT, minRepCount = 3, cameraYawDeg = 0f)` — see
`ShippedBaselineDerivationHarness.kt`.

**Yaw gate deliberately relaxed**: real per-rep |yaw| on this footage ran **36.4°–90.0°**
across the 15 detected reps (median ≈46.1°), past the normal ~30° placement gate
(`CameraAngleEstimator` saturates at its 90° ceiling on this non-protocol footage, see
`docs/DESIGN_LIMITATIONS.md` L-25). `cameraYawDeg = 0f` treats fixture geometry as reference
for this one-time editorial derivation only — not a precedent for live sessions.

## Rep selection

- Raw detected: 21 · after ForwardStrokeFilter: 15 · after RepFilter: 15 (no further banding
  removal — all 15 already inside 2× median speed/duration) · after LocomotionFilter
  (stationary): 15 · final kept (post 2σ exclusion): **11** · excluded as outliers:
  **[0, 1, 6, 8]**.
- Visual verification (`visualize-pose` skill, peak frames): all 15 peak frames rendered to
  `tmp/shipped_baseline_review/rep_<i>_frame_<peakFrame>.png` and read. This footage is a
  close, near-frontal camera angle (consistent with the 36–90° measured yaw, not a clean side
  profile) — at every rep's peak frame the paddle is raised near head/face height with only
  moderate differences in elbow bend and hand position between reps. Given the framing, the
  brief's mechanical check ("racket arm extended forward/across the body at contact" vs "arm
  still coiled or moving away") is **not visually decisive from a single peak frame** for most
  reps — the strongest visual signal available is the `elbow_angle` clustering itself, not a
  clear-cut forward/recovery silhouette difference. Rep 11 (frame 147) is a partial exception:
  its racket-holding arm sits low near the torso rather than raised toward the head, visually
  distinct from the other 14 kept/excluded reps — flagged below as worth extra scrutiny.
- Any deviations from pure automatic exclusion: **yes, one identified** — `elbow_angle` is
  visibly multi-modal across the 15 reps (low cluster ~27–38°: reps 0,1,3,5,7,12,14; mid
  cluster ~70–100°: reps 4,8,10,11; high cluster ~116–155°: reps 2,6,9,13), matching the
  design spec's noted "bimodal `elbow_angle`, ~10 reps 35–69°, ~5 reps 111–135°" observation
  (spec: `docs/superpowers/specs/2026-07-27-no-calibration-shipped-baseline-design.md` §1) in
  kind, though not in exact split. Of the high cluster, **only rep 6 (elbow=155.4°) was
  auto-excluded**, and it was excluded via `coil_ratio` (1.9, blowing the 2σ band on that
  metric), not via `elbow_angle` itself. **Reps 2 (elbow=116.5°), 9 (elbow=119.0°), and 13
  (elbow=118.7°) — squarely inside the same "111–135° recovery-swing candidate" band flagged
  by the spec — were NOT auto-excluded** and remain inside `metricStats`. This is because the
  wide spread of the elbow_angle values (mean=71.6°, std=39.1° over the kept 11) inflates the
  2σ exclusion window (≈[−6.6°, 149.8°]) enough to swallow the entire high cluster rather than
  separating it. Rep 8 (elbow=96.6°, mid cluster) was excluded, but via `knee_bend` (166.1°,
  below its 2σ band), and rep 0/1 were excluded via `torso_lean`/`coil_ratio` respectively —
  i.e. **no rep in this dataset was excluded because of its `elbow_angle` value alone.**
  Per the brief's instruction ("rep-selection nuance is documented, not hand-patched into a
  second, undocumented derivation path"), this derivation proceeds with the harness's own
  numbers unchanged — the shipped constant traces to this one reproducible run. Downstream
  consumers of `FOREHAND_ANDRII.metricStats["elbow_angle"]` should be aware the mean/std pair
  describes a distribution with a real (not outlier-excluded) high-angle tail.

## Numbers

```
=== ShippedBaseline derivation: andrii_1 (MediaPipe-lite) ===
createdAtMs candidate (paste literal): 1785248516745
raw detected=21 forward=15 banded=15 stationary=15
rep[0] peakFrame=12 startFrame=9 endFrame=14 yaw=90.0 elbow_angle=37.9, shoulder_angle=9.6, knee_bend=178.0, torso_lean=10.1, follow_through_angle_2d=73.9, stroke_speed=8.7, coil_ratio=0.9
rep[1] peakFrame=25 startFrame=22 endFrame=31 yaw=46.1 elbow_angle=31.4, shoulder_angle=16.0, knee_bend=177.5, torso_lean=3.4, follow_through_angle_2d=166.7, stroke_speed=8.3, coil_ratio=1.9
rep[2] peakFrame=38 startFrame=36 endFrame=43 yaw=46.1 elbow_angle=116.5, shoulder_angle=65.1, knee_bend=177.2, torso_lean=7.7, stroke_speed=9.1, coil_ratio=1.1
rep[3] peakFrame=49 startFrame=47 endFrame=51 yaw=49.0 elbow_angle=32.3, shoulder_angle=24.7, knee_bend=175.4, torso_lean=1.8, follow_through_angle_2d=99.9, stroke_speed=7.7, coil_ratio=0.8
rep[4] peakFrame=61 startFrame=59 endFrame=67 yaw=53.0 elbow_angle=70.4, shoulder_angle=17.1, knee_bend=178.5, torso_lean=7.4, stroke_speed=8.3, coil_ratio=0.8
rep[5] peakFrame=72 startFrame=71 endFrame=75 yaw=56.0 elbow_angle=38.0, shoulder_angle=23.5, knee_bend=172.0, torso_lean=2.7, follow_through_angle_2d=69.3, stroke_speed=8.5, coil_ratio=0.7
rep[6] peakFrame=85 startFrame=80 endFrame=90 yaw=58.4 elbow_angle=155.4, shoulder_angle=94.2, knee_bend=172.2, torso_lean=4.6, follow_through_angle_2d=158.1, stroke_speed=8.5, coil_ratio=1.9
rep[7] peakFrame=97 startFrame=95 endFrame=103 yaw=36.4 elbow_angle=31.8, shoulder_angle=10.9, knee_bend=176.9, torso_lean=1.8, follow_through_angle_2d=157.2, stroke_speed=8.2, coil_ratio=0.9
rep[8] peakFrame=110 startFrame=107 endFrame=116 yaw=39.6 elbow_angle=96.6, shoulder_angle=49.6, knee_bend=166.1, torso_lean=6.0, follow_through_angle_2d=164.6, stroke_speed=7.7, coil_ratio=0.9
rep[9] peakFrame=122 startFrame=119 endFrame=128 yaw=37.7 elbow_angle=119.0, shoulder_angle=64.3, knee_bend=173.6, torso_lean=4.2, follow_through_angle_2d=158.1, stroke_speed=8.7, coil_ratio=1.0
rep[10] peakFrame=134 startFrame=132 endFrame=140 yaw=37.1 elbow_angle=99.9, shoulder_angle=49.1, knee_bend=176.6, torso_lean=3.9, follow_through_angle_2d=159.6, stroke_speed=8.8, coil_ratio=0.8
rep[11] peakFrame=147 startFrame=145 endFrame=153 yaw=41.2 elbow_angle=96.7, shoulder_angle=48.9, knee_bend=170.3, torso_lean=5.5, follow_through_angle_2d=164.8, stroke_speed=8.7, coil_ratio=0.7
rep[12] peakFrame=157 startFrame=156 endFrame=160 yaw=53.8 elbow_angle=37.4, shoulder_angle=31.3, knee_bend=179.3, torso_lean=4.0, follow_through_angle_2d=62.7, stroke_speed=9.3, coil_ratio=0.4
rep[13] peakFrame=171 startFrame=169 endFrame=177 yaw=46.8 elbow_angle=118.7, shoulder_angle=70.3, knee_bend=171.8, torso_lean=7.4, follow_through_angle_2d=169.3, stroke_speed=9.0, coil_ratio=0.8
rep[14] peakFrame=182 startFrame=181 endFrame=185 yaw=49.6 elbow_angle=27.3, shoulder_angle=25.2, knee_bend=179.8, torso_lean=1.5, follow_through_angle_2d=63.2, stroke_speed=8.7, coil_ratio=0.7
=== Derived PersonalBaseline ===
repCount=11 excludedRepIndices=[0, 1, 6, 8] qualityScore=0.6695165073025089
--- metricStats (paste into ShippedBaselines.FOREHAND_ANDRII.metricStats) ---
"elbow_angle" to MetricStats(mean=71.64542215520686, std=39.114602922054395, min=27.338851928710938, max=119.0262680053711, sampleCount=11),
"shoulder_angle" to MetricStats(mean=39.125756523825906, std=21.13768114821029, min=10.877771377563477, max=70.30072021484375, sampleCount=11),
"knee_bend" to MetricStats(mean=175.5822615189986, std=3.2270200698360165, min=170.2755126953125, max=179.75291442871094, sampleCount=11),
"torso_lean" to MetricStats(mean=4.349920131943443, std=2.3482826393594873, min=1.4852596521377563, max=7.665465831756592, sampleCount=11),
"follow_through_angle_2d" to MetricStats(mean=122.67502000596788, std=47.786254229559475, min=62.71063995361328, max=169.2542266845703, sampleCount=9),
"stroke_speed" to MetricStats(mean=8.614285165613348, std=0.46126920336117117, min=7.65094518661499, max=9.282928466796875, sampleCount=11),
"coil_ratio" to MetricStats(mean=0.7931778187101538, std=0.17916409753423934, min=0.43392249941825867, max=1.1075656414031982, sampleCount=11),
--- phaseDurationsMs (paste into ShippedBaselines.FOREHAND_ANDRII.phaseDurationsMs) ---
"forward_swing_ms" to MetricStats(mean=181.8181818181818, std=60.30226891555273, min=100.0, max=300.0, sampleCount=11),
"stroke_total_ms" to MetricStats(mean=654.5454545454545, std=206.70576365276494, min=400.0, max=900.0, sampleCount=11),
```

Note: `follow_through_angle_2d` has `sampleCount=9` (of 11 kept reps) — reps 2 and 4 have no
`follow_through_angle_2d` printed above (omitted, not zero — the metric is unmeasurable for
those reps' endFrame window per `DerivedMetrics.merge`'s score-gate/sanity-bound contract),
so the derived stats for that key are computed over the 9 reps where it was present.
