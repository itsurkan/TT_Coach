---
name: Staged roadmap — pose → ball → table
description: TT_Coach roadmap after 2026-06-10 pivot — 2D fixed-drill MVP on RTMPose (desktop-first), ball/audio/video analysis frozen
type: project
originSessionId: 51fe4f63-cc7e-4a09-84eb-927adff4f560
---
**PIVOT (2026-06-10), branch `2d`:** TT_Coach pivoted to a fixed-drill 2D in-plane joint-angle coaching MVP based on a consolidated research doc (`docs/tt-coach-ai-context.md`). Design spec: `docs/superpowers/specs/2026-06-10-2d-pivot-design.md` (commit d747d30). Supersedes the 2026-04-18 three-stage plan below in specifics, keeps its spirit (pose first, ball later, calibration over universal rules).

**Confirmed pivot decisions:**
- **Pose model:** RTMPose (Apache-2.0) replaces MediaPipe — founder's 3-month MediaPipe-3D attempt failed on unreliable monocular z; 2D in-plane angles only, rotational cues qualitative-only or silent (never fake degrees). YOLO-Pose rejected (AGPL).
- **Approach: desktop-first (option C).** RTMPose-m via MMPose on Mac M4 → export COCO-17 pose JSON (schema v2 with `topology` field; legacy MediaPipe-33 fixtures stay valid) → drill logic in `shared/` KMP on fixtures (TDD) → Android port last (RTMPose-s, MMDeploy→ncnn→JNI, ONNX Runtime Mobile fallback).
- **Reference angles from personal baseline** (003 path: reps → BaselineDeriver → per-player ranges); hand-coded values only as sanity bounds. Keeps "don't re-teach" positioning.
- **3 MVP drills:** forehand drive first (side camera, confirmed), footwork second (blocked on table-occlusion design), forehand topspin third.
- **"Real-time" = 3–5 s feedback cadence** (voice takes seconds anyway).
- **Frozen, not deleted:** BallDetectorV1–V6, ROIManager, trajectory code, audio-contact + frame-extraction scripts, YOLO training. Excluded from pivot scope per founder; returns post-MVP.
- **Future 3D:** MotionAGFormer 2D→3D lifting (server-side, post-session, ±40-frame continuous windows around stroke peak) — same 2D pipeline feeds it, so MVP work transfers.
- **Market sequence:** UA validate (free beta) → EU monetize (DE first, ~€4.99/mo) → US same EN build → CN partner-only later. Stage 1 gate: ≥40% of beta does ≥3 drill sessions in week 1.

**How to apply:**
- New analysis/drill logic goes in `shared/commonMain` against COCO-17 fixtures; adapt existing `AngleCalculations`/`StrokePhaseDetector` rather than rewriting.
- Don't touch ball-tracking/audio code; don't propose MediaPipe for new work (legacy pipeline still exists until Android port).
- Founder content task: record forehand drive footage per placement spec (side camera, playing-hand side, perpendicular, ~2–3 m, table height).

**Authoritative docs in repo:** `docs/tt-coach-ai-context.md` (research + decision log), `docs/superpowers/specs/2026-06-10-2d-pivot-design.md` (design), `docs/STAGED_ROADMAP.md` (pre-pivot, historical).
