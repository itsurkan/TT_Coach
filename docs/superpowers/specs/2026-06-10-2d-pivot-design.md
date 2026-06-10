# 2D Pivot — Desktop-First Fixed-Drill MVP (Design)

**Date:** 2026-06-10
**Branch:** `2d`
**Context doc:** [docs/tt-coach-ai-context.md](../../tt-coach-ai-context.md) (consolidated research & decisions, June 2026)

## Summary

Pivot from the MediaPipe-3D + ball-tracking direction to a 2D in-plane joint-angle
coaching MVP built on RTMPose, following the consolidated context document. The MVP
delivers fixed/structured drills with voice/text feedback at a 3–5 second cadence.
Ball tracking, audio contact detection, and all video/audio/image analysis beyond
pose extraction are **out of scope** — frozen, not deleted.

## Decisions (confirmed 2026-06-10)

1. **Approach: desktop-first.** Prototype RTMPose-m on the Mac M4; develop all drill
   logic in `shared/` KMP against JSON fixtures; port to Android afterwards.
   (Alternatives considered: ship MVP on the existing MediaPipe pipeline behind a
   `PoseBackend` interface with a parallel RTMPose spike; or RTMPose-on-Android
   first. Desktop-first chosen by the founder.)
2. **Reference angles: personal baseline.** Reuse the 003 calibration path —
   calibration reps → `BaselineDeriver` → per-player reference ranges per drill.
   Hand-coded values survive only as sanity bounds (e.g. elbow 20–170°). This
   preserves the product positioning: calibrate to the player's technique, don't
   re-teach. `BaselineRuleFactory` remains the single source of rule derivation.
3. **Repo strategy:** work on the existing `2d` branch in this repository. Keep the
   shared module, tests, and CameraX infrastructure; freeze ball-tracking code in
   place.
4. **First drill: forehand drive** (side camera). Footwork second, after the
   table-occlusion design question is resolved. Forehand topspin third.

## Phase 1 — Desktop pose pipeline (Mac M4, Python)

- Set up MMPose; run **RTMPose-m + RTMDet-nano** on recorded videos.
- New script `scripts/poses/export_poses_rtmpose.py`: video → JSON with 17 COCO
  keypoints (`x, y, score` per keypoint, normalized image coords).
- **Pose JSON schema v2** with a `"topology"` field (`"coco17"` vs the legacy
  implicit MediaPipe-33). `JsonTestUtils` and poses_viewer must distinguish the two
  formats; the legacy 33-landmark `x,y,z,visibility` schema stays valid for old
  fixtures.
- poses_viewer: render the COCO-17 skeleton. This is the visual QA tool for RTMPose
  output quality before any product logic is written.

## Phase 2 — Drill logic in shared KMP (fixture-driven, TDD)

All platform-independent, in `shared/commonMain` (iOS is a firm future target):

- `PoseFrame2D` model (COCO-17 topology) + in-plane angle functions from the context
  doc §4: elbow, shoulder, knee bend, torso lean, shoulder tilt. Adapt the existing
  `AngleCalculations` rather than rewriting.
- Stroke detection via wrist-speed local maximum over a sliding pose buffer —
  adaptation of the existing `StrokePhaseDetector`.
- Reference ranges via the 003 baseline path (decision 2 above).
- Feedback rules: precise degrees **only** for in-plane metrics; rotational cues
  qualitative-only or silent (trust rule, context doc §3); 3–5 s cadence policy;
  UA + EN strings.
- Fixtures: real RTMPose-exported JSON from Phase 1, tests in
  `shared/src/commonTest`.

## Phase 3 — Android port (after logic is proven on fixtures)

- `PoseBackend` interface in `app/`; RTMPose-s via MMDeploy → ncnn → JNI; fallback
  ONNX Runtime Mobile.
- Camera-placement verification from first frames (profile check for side drills).
- TTS voice feedback + on-screen fallback.
- Frozen, untouched: `BallDetectorV1–V6`, `ROIManager`, trajectory code
  (`TimelineSynchronizer`/`TrajectoryFilter`/`TrajectorySegmenter`), audio-contact
  and frame-extraction Python scripts, YOLO training. They return post-MVP (Stage 2
  of the staged roadmap).

## Content dependency (founder task)

Record forehand drive footage per the context doc's placement spec: side camera on
the playing-hand side, perpendicular to the stroke plane, ~2–3 m, table height.
Until then, Phase 2 runs on existing `Videos/` footage that was not shot to this
protocol — acceptable for pipeline bring-up, not for tuning reference ranges.

## Error handling & quality gates

- RTMPose keypoint `score` thresholds gate angle computation (no feedback on
  low-confidence frames).
- Schema-v2 loader rejects topology mismatches explicitly (same spirit as the
  existing `JsonTestUtils` strictness).
- Phase 1 exit gate: poses_viewer visual check confirms RTMPose-m skeleton quality
  on real footage.
- Phase 2 exit gate: drill analysis produces correct cues on labeled fixture reps
  (unit-tested).

## First concrete steps

1. Commit the pivot context document into `docs/` as canonical context. ✅ (this
   commit)
2. Install MMPose on the Mac; run RTMPose-m on an existing `Videos/` clip; verify
   JSON export end-to-end.
3. Define pose JSON schema v2 (COCO-17) + KMP models + loader (TDD).
4. Adapt angle math and the stroke-peak detector to the COCO topology.
