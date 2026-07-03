# Generic Movement Pipeline — Universal Stroke Detection + Single Feedback Engine (Design)

**Date:** 2026-07-02
**Branch/worktree:** `.claude/worktrees/generic-movement-pipeline`
**Status:** decided, not yet implemented (this doc records the decision; an execution plan follows separately)

## Summary

`StrokeDetector2D`, `DrillMetrics`, `FeedbackMessageCatalog`, `DrillCalibrator`, and
`ForehandDriveDrillAnalyzer` were all written for one movement (forehand drive) with
its constants and metric set baked in as defaults. Adding a second movement (backhand,
footwork, serve) currently means forking these classes. This doc records the decision
to generalize the pipeline into one detector + one feedback engine driven by
per-movement data (`MovementDefinition`), while every existing public API keeps its
signature and delegates — no behavior change for forehand drive, no new test failures.

## Problem — weaknesses in current code

1. **Hard-coded signal source.** `StrokeDetector2D` always tracks
   `Coco17.wrist(handedness)` — no other keypoint can drive detection.
   [detection/StrokeDetector2D.kt](../../../shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/StrokeDetector2D.kt)
2. **Forehand-tuned constants as constructor defaults, not a profile.** `smoothingWindowMs=300`,
   `peakWindowRadiusMs=300`, `minPeakSpeed=1.0f` (torso/s), `boundaryFraction=0.3f`,
   `minPeakGapMs=500` are hard-wired defaults with no per-movement variant.
3. **Pipeline order duplicated, unenforced.** `detect → ForwardStrokeFilter → RepFilter →
   LocomotionFilter` is copy-pasted in `DrillCalibrator.calibrate` and
   `ForehandDriveDrillAnalyzer.analyze`. CLAUDE.md already flags that reordering
   silently corrupts baselines — nothing in the code actually enforces the order.
   Highest bug risk in the current design.
4. **`ForwardStrokeFilter` semantics are movement-specific but applied unconditionally.**
   "Forward" (facing direction, session speed-dominance vote) is a forehand-drive
   assumption, not a general rep-validation rule.
5. **`LocomotionFilter` always on.** Wrong for future drills involving footwork —
   this needs to be a per-movement toggle, not a hard-wired stage.
6. **Duplicated math.** ~5 private copies of `median()` and 3 near-copies of
   torso-length computation spread across `detection/` and `drill/` (`RepFilter`'s own
   comment already flags this).
7. **Fixed `intervalMs` instead of per-frame `timestampMs`** — ~4% speed error at
   120fps (noted in `StrokeDetector2D` docs). Deferred, not fixed in this slice.
8. **`FeedbackMessageCatalog` is a hard-coded `when(metricKey)` block.** Phrasing is
   data, not behavior; adding a movement means editing the when-block.
9. **Metric set is global, defined three times.** `DrillMetrics` extracts a fixed 5
   metrics; `SanityBounds` and `MetricPrecisionPolicy` are separate global maps keyed
   by the same metric-key strings. A new metric requires touching three places.
10. **`DrillFeedbackEngine` gaps (deferred).** Rhythm rules are silently skipped
    (session-level eval unbuilt); no cue-repetition hysteresis, so the same cue can
    repeat every 3s.

## Decision: ONE generic feedback engine, not per-movement classes

Engine logic — baseline z-score comparison, severity ranking, the trust rule
(`MetricPrecisionPolicy`), cadence policy — is identical across TT strokes; only
**data** differs: metric list, phrasing, sanity bounds, precision, detector tuning.
Separate per-movement classes would fork logic that must never drift, especially the
trust rule. Genuinely movement-specific behavior enters as **configuration** (flags
for direction gate / banding / locomotion), not as forked code. Strategy hooks
(pluggable rep-validation) can be added later if a movement — e.g. serve — needs
custom rep validation the config flags can't express.

## New classes

All in `shared/commonMain`, zero external deps, `kotlin.math` only.

- **`detection/DetectionConfig.kt`** — `data class DetectionConfig(signalKeypoint:
  SignalKeypoint = DOMINANT_WRIST, smoothingWindowMs=300, peakWindowRadiusMs=300,
  minPeakSpeed=1.0f, boundaryFraction=0.3f, minPeakGapMs=500, minScore=
  AngleCalculations2D.DEFAULT_MIN_SCORE)` + `enum SignalKeypoint { DOMINANT_WRIST,
  NON_DOMINANT_WRIST, DOMINANT_ELBOW, NON_DOMINANT_ELBOW, DOMINANT_ANKLE,
  NON_DOMINANT_ANKLE }` with `fun index(handedness: Handedness): Int` resolving to
  Coco17 indices.
- **`detection/MovementDetector.kt`** — the generalized `StrokeDetector2D`: same
  algorithm (torso-normalized smoothed speed, keep-max NMS peaks, boundary walk,
  valley clamp), reading tracked keypoint + tuning from `DetectionConfig`.
  `StrokeDetector2D` becomes a thin delegating wrapper (kept so existing tests/API
  stay green), marked `@Deprecated("use MovementDetector")`.
- **`analysis/SignalMath.kt`** — consolidation point: `median(List<Float>)`,
  `median(List<Double>)`, `medianTorsoLength(frames, xScale, minScore)`. All
  duplicated private copies in `StrokeDetector2D`/`MovementDetector`, `RepFilter`,
  `ForwardStrokeFilter`, `LocomotionFilter`, `DrillMetrics` refactor onto it.
  `CameraAngleEstimator`'s aspectRatio-parameterized sibling stays as-is (different
  signature, not a duplicate).
- **`drill/MetricSpec.kt`** — `data class MetricSpec(key, precision: MetricPrecision,
  sanityBounds: ClosedFloatingPointRange<Double>?, extractor: (keypoints, handedness,
  xScale, minScore) -> Float?)`.
- **`drill/MovementMetrics.kt`** — generic `extractAtFrame`/`extractAtPeak` over
  `List<MetricSpec>` (median over ±70ms window, per-frame score gating + per-spec
  sanity bounds). `DrillMetrics` keeps its API and delegates using the core-5 specs.
- **`drill/MessageTemplates.kt`** + generic formatter in `FeedbackMessageCatalog` —
  templates keyed by `(metricKey, direction, lang)`; degree suffix appended only when
  `precision == PRECISE_DEGREES` (trust rule enforced in **one** place); qualitative
  fallback phrase for unknown metrics. `FeedbackMessageCatalog.format(cue, lang)`
  keeps its API, delegating with the forehand/core template set.
- **`drill/RepValidationConfig.kt`** — `data class(directionGate: Boolean = true,
  banding: Boolean = true, hipTravelMaxTorso: Float =
  LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO /* <=0 disables */)`.
- **`drill/MovementRepPipeline.kt`** — THE single owner of pipeline order: `detect →
  (directionGate? ForwardStrokeFilter) → (banding? RepFilter) → LocomotionFilter
  gate`. Both calibrator and analyzer call it; order is no longer copy-pasted.
- **`drill/MovementDefinition.kt`** — `data class MovementDefinition(id, detection:
  DetectionConfig, repValidation: RepValidationConfig, metrics: List<MetricSpec>,
  messages: MessageTemplates)`.
- **`drill/MovementAnalyzer.kt`** — the generic `ForehandDriveDrillAnalyzer` (per-rep
  yaw gate, `ViewGeometry` xScale, cues, cadence, `placementOk` aggregation — behavior
  identical); `ForehandDriveDrillAnalyzer` becomes a thin wrapper delegating with
  `ForehandDrive.definition`.
- **`drill/MovementCalibrator.kt`** — the generic `DrillCalibrator.calibrate` (per-rep
  yaw gate, `CameraPlacementException` semantics, `BaselineDeriver.deriveFromMetrics`);
  `DrillCalibrator` delegates.
- **`drill/movements/ForehandDrive.kt`** — the `MovementDefinition` instance
  reproducing today's behavior bit-for-bit (all current defaults, core-5 metrics,
  current UA+EN strings).
- **`drill/movements/BackhandDrive.kt`** — second definition (id `"backhand_drive"`,
  same config/metrics for now) proving a new movement is pure data + a smoke test.

## Compatibility guarantees

Existing public APIs — `StrokeDetector2D`, `DrillMetrics`, `FeedbackMessageCatalog`,
`DrillCalibrator`, `ForehandDriveDrillAnalyzer`, `SanityBounds`,
`MetricPrecisionPolicy` — keep their signatures and delegate to the generic classes.
Every existing test must pass unchanged. The andrii_1 E2E exit gate (15 forward reps
from 23 raw peaks) must stay green.

## Deferred (documented, out of scope)

- Timestamp-derived `dt` instead of fixed `intervalMs` (L-registry follow-up).
- Multi-peak movement topologies (e.g. serve toss+strike).
- Per-metric sample phase (peak vs. other phase points) — currently all metrics
  sample at the detected peak.
- Cue-repetition hysteresis in `DrillFeedbackEngine`.
- Session-level rhythm rule evaluation (currently silently skipped).
- Pluggable `RepCandidateFilter` strategy interface — add when a movement actually
  needs rep validation the `RepValidationConfig` flags can't express.
