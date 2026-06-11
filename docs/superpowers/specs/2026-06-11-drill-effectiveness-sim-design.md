# Drill Effectiveness Simulator — Design

Date: 2026-06-11
Branch: `2d`
Status: Approved (brainstorming) → rescoped 2026-06-11: M0 (stroke counting) first, analysis milestones deferred — see Milestones

## Purpose

This tool is a **debug & review harness** for the drill pipeline, run in the browser inside `poses_viewer` — not a product feature. The pipeline of record is the Kotlin `shared/` module: every behavioral fix discovered while reviewing in the browser **must be implemented in `shared/` Kotlin** (and covered by tests there); the TS side is a mirror for visual debugging. Kotlin is the source of truth on any disagreement.

## Goal

A desktop tool that mirrors how the mobile app analyzes a table-tennis drill. Playing a clip, the user sees (by milestone):

- **M0 — stroke counting (current scope):** stroke splits (start / peak / end) drawn on the video timeline, forward vs recovery color-coded, raw-peak and forward-rep counts, detector knobs.
- **M1+ — deferred:** per-rep analysis results (the 5 in-plane metrics + feedback cues), spoken feedback at the live cadence (3–5 s), metric/drill-type configuration.

## Milestones

### M0 — stroke counting debug harness (current)

Port **only the detection chain**, not the analysis layer. Verified port surface (~440 lines of Kotlin total):

| TS module | Mirrors (Kotlin) | Lines |
|---|---|---|
| `strokeDetector2d.ts` | `StrokeDetector2D` | 199 |
| `forwardStrokeFilter.ts` | `ForwardStrokeFilter` | 116 |
| `repFilter.ts` | `RepFilter` | 39 |
| `geometry.ts` | `ViewGeometry` (xScale) | 34 |
| `types.ts` | `Stroke2D`, `Coco17` indices | ~43 |
| `facing.ts` | `AngleCalculations2D.facingSign` + `DEFAULT_MIN_SCORE` only | ~12 |

Not ported in M0: the rest of `AngleCalculations2D`, `CameraAngleEstimator`, `DrillMetrics`, feedback engine / message catalog / cadence policy, `referenceStandard`. No pose-JSON parser needed — the viewer already loads `*_poses_rtm.json`.

M0 config: handedness (default right), camera yaw as a manual number (default 0 → `xScale = aspectRatio`; the estimator is not ported — it saturates on non-protocol footage, L-25), detector knobs (min peak speed, NMS window).

Pipeline order is mandatory even in M0 — raw wrist-speed peaks ≠ reps (23 raw vs 15 forward on andrii_1): `detect → ForwardStrokeFilter → RepFilter`.

### M1+ — analysis & feedback (deferred, design below retained)

The sections below (metrics, reference ideal, spoken feedback, full config panel) describe the deferred milestones. They are kept as approved design context, to be re-planned when M0 review of detection quality is done.

## Fix flow (binding rule)

1. Observe a detection problem in the browser (M0 UI).
2. Reproduce / fix it in **Kotlin `shared/`** with a test (fixture-driven where possible).
3. Mirror the fix in the TS port.
4. Update the golden parity numbers in both test suites in the same change.

A TS-only fix is never done — it would silently diverge the harness from the app.

## Explicit decisions (and a deliberate departure)

These were chosen during brainstorming and override defaults that the rest of the project assumes:

1. **Reimplement the drill pipeline in TypeScript** inside `poses_viewer`, rather than calling the real Kotlin `shared/` code (which is JVM/Android-only, no JS target). Accepted risk: divergence from the pipeline the Android app runs. Mitigated by parity tests against the same fixtures + Kotlin golden numbers (see Testing).
2. **Reference = external ideal, not personal baseline.** This tool compares the player against a researched, fixed "standard" forehand-drive technique ("how close to ideal"), **not** the player's personal baseline. This is a deliberate departure from the project's "don't re-teach — calibrate to the player's own technique" positioning. It is correct *for this effectiveness-analysis tool* and does not change the product direction elsewhere. Future readers: this divergence is intentional.
3. **v1 includes spoken-feedback playback** (the "closest to the live app" scope), audio on by default.

## Non-goals (v1)

- No personal-baseline calibration path in this tool (the existing `DrillCalibrator` flow is unaffected).
- No language toggle — English only (`FeedbackMessageCatalog` UA support exists but is out of scope here).
- Only the forehand-drive drill is implemented; the drill-type selector is structural future-proofing.
- No changes to `shared/` Kotlin or the Android app.

## Architecture

### TS pipeline — `poses_viewer/src/drill2d/`

Pure functions mirroring the `shared/` packages, so the port maps 1:1 to its Kotlin counterpart for review and parity:

| TS module | Mirrors (Kotlin) | Responsibility |
|---|---|---|
| `geometry.ts` | `ViewGeometry` | `xScale = aspectRatio / cos(cameraYaw)`; apply to x-deltas before any trig |
| `angles2d.ts` | `AngleCalculations2D` | the 5 in-plane metrics (elbow, shoulder, knee bend, torso lean, shoulder tilt), xScale-corrected, score-gated (null if any required kp `score < 0.3`) |
| `cameraYaw.ts` | `CameraAngleEstimator` | per-rep `|yaw|` from pre-stroke shoulder foreshortening; `placementOk = |yaw| ≤ ~30°` |
| `strokeDetector2d.ts` | `StrokeDetector2D` | wrist-speed peaks in torso-lengths/sec, ms windows, keep-max NMS, valley-clamped boundaries → `Stroke2D[]` |
| `forwardStrokeFilter.ts` | `ForwardStrokeFilter` | speed-dominance direction vote — drop recovery swings |
| `repFilter.ts` | `RepFilter` | median banding to keep real reps |
| `drillMetrics.ts` | `DrillMetrics` | `extractAtPeak`: ±70 ms median window, score-gate, sanity bounds → metric map per rep |
| `referenceStandard.ts` | (new) | external **ideal** ranges per metric per drill — pluggable constant, sourced via research |
| `feedbackEngine.ts` | `DrillFeedbackEngine` | metric vs ideal range → `FeedbackCue` (TOO_HIGH / TOO_LOW, severity, precision) |
| `messageCatalog.ts` | `FeedbackMessageCatalog` | cue → EN message string, respecting metric precision (degree numbers only for the 5 in-plane metrics) |
| `cadencePolicy.ts` | `FeedbackCadencePolicy` | 3–5 s gating of spoken feedback |
| `analyzeDrill.ts` | `ForehandDriveDrillAnalyzer` | orchestrator: `analyzeDrill(seq, config) → DrillAnalysisReport` |

Pipeline order (do not reorder — silently corrupts results, per CLAUDE.md gotcha):
`detect → ForwardStrokeFilter → RepFilter → extractAtPeak → feedbackEngine → cadencePolicy`.

### Data types (TS)

A small TS mirror of the schema-v2 pose model: `Keypoint2D {x, y, score}`, `PoseFrame2D {timestampMs, keypoints[]}`, `PoseSequence2D {topology, frames[], viewGeometry}`. Loaded by parsing the existing `*_poses_rtm.json` already served to the viewer.

`DrillAnalysisReport`:
- `reps: RepAnalysis[]` — each `{ startFrame, peakFrame, endFrame, isForward, yawDeg, placementOk, metrics: Record<metric, number|null>, cues: FeedbackCue[] }`
- `spoken: SpokenFeedback[]` — `{ timestampMs, message, cue? }`, cadence-gated
- `rawPeakCount`, `forwardRepCount` — for the parity assertions

### UI — new route `#/drill-analysis`

Reuses the viewer's video element + `PoseCanvas` skeleton overlay and pose-JSON loading.

- **Timeline** — extend `FrameControls`: draw each rep as a band (start→end), forward vs recovery color-coded, peak tick. Click a band → seek to its peak frame.
- **Results panel** — per-rep table of the 5 metrics with pass / over / under vs the ideal range, plus the cue text. Selecting a rep highlights its band and seeks.
- **Spoken-feedback playback** — during play, `SpokenFeedback` entries fire as `currentTime` crosses their `timestampMs`: spoken via `window.speechSynthesis` (EN voice) **by default**, plus a running on-screen feedback log + latest-message banner.
- **Config panel** —
  - metric on/off toggles (the 5 in-plane metrics);
  - drill-type selector (forehand drive only for now);
  - detector/threshold knobs (min rep count, outlier sigma, yaw gate);
  - spoken-feedback mode: **audio (default)** / text-only.
  - Any change re-runs `analyzeDrill` in-browser (synchronous, instant) and re-renders.

### Data flow

`*_poses_rtm.json` (existing loader) → parse → `PoseSequence2D` → `analyzeDrill(seq, config)` → render timeline + panel; `speechSynthesis` driven by the video's `timeupdate`. No backend, no JVM — fully interactive.

## Testing (vitest, in `poses_viewer`)

1. **Parity / golden (M0)** — run `andrii_1_rtm.json` and `video_2_rtm.json` through the TS pipeline and assert against the Kotlin E2E golden: **15 forward reps from 23 raw peaks on andrii_1**. This is the primary anti-drift guardrail and the M0 exit gate.
2. **Unit (M0)** — xScale applied to x-deltas before speed computation (synthetic `xScale = 1`), score-gating below 0.3, stroke boundary valley-clamping, keep-max NMS.
3. **Unit (M1+)** — `angles2d` metric correctness, `cadencePolicy` 3–5 s gating.
4. **Reference ranges (M1+)** — `referenceStandard.ts` validated for shape/units; numbers are pluggable and may start as placeholders.

## Reference-ideal sourcing

Sourcing the external ideal ranges (standard forehand-drive joint angles from coaching/biomechanics literature) is a **dedicated plan task** using the deep-research skill. The pipeline runs on reasonable placeholder ranges until the researched numbers land, so implementation is not blocked on the research.

## Risks / open items

- **Divergence from Kotlin** — addressed by parity tests; if the TS port and Kotlin disagree on a fixture, the Kotlin is the source of truth and the TS is the bug.
- **`Videos/` footage was not shot to the camera-placement protocol** — fine for mechanics/pipeline bring-up, not for tuning the ideal ranges. The tool proves mechanics, not tuned thresholds.
- **Reference ranges are provisional** until research lands; the tool must make clear these are an external standard, not the player's baseline.
