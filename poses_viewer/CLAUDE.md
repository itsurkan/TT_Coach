# poses_viewer — Claude orientation

React + Vite debug/labeling tool for TT_Coach. Overlays pose, ball, contact, and table JSON on video frames; renders 3D mannequin for drill calibration.

- Dev server: `npm run dev` → http://localhost:5780
- Stack: React 18, TypeScript, Vite 6, Tailwind v4, three/@react-three/fiber/drei 0.184, vitest 4
- Vite proxies JSON + MP4 from repo-top `Videos/<base>/`. Hash routing via `src/hooks/useHashRoute.ts`: `#/main` (default), `#/mannequin`, `#/drill2`, `#/dataset`, `#/strokes`. `App.tsx` early-returns the matching component for non-`main` routes.

Update this file when you change a listed file.

## Conventions

- Drill pipeline angles: **degrees** everywhere.
- MediaPipe landmarks: normalized `[0,1]` coords, `x=right y=down z=away` (z noisy on side-on cameras).
- three.js mannequin coordinate system matches landmark axes.
- UI strings: **Ukrainian** (labels from `jointMap.ts`).
- Tests pin FK via djb2 fingerprints (`drill/__tests__/skeletonReconstructor.test.ts:500`). Intentional drift → bump fingerprint + explain in commit.

## File map

### `src/App.tsx` (~1776 lines)

Mostly wiring. Loads pose+ball JSON keyed by `videoBase`; optional V5/YOLO ball, contacts, labels, crop config, trajectory results (V1–V3, 3D, 3Dv2). Overlay-toggle settings in `localStorage` key `poses_viewer_settings`; last-opened video + frame in `poses_viewer_session` (auto-resumes on reload once `/api/videos` resolves). Header `?` button / `?` key opens a keyboard-shortcuts modal; `R` resets zoom/pan. Save failures surface a transient toast; JSON fetch shows a `Loading…` state. Logic lives in components and `utils/trajectoryPipeline*`. Pose JSON: schema v2 (`topology: 'coco17'`, see `docs/pose_json_schema_v2.md`) supported alongside legacy MediaPipe-33; normalization lives in `src/utils/normalizePoses.ts`. RTMPose overlay is a separate "RTM" header toggle (`showRtmPoses`) that fetches `{base}_poses_rtm.json` independently and draws the RTM skeleton (COCO-17, or Halpe26 with feet when exported via `--feet`; head/neck/hip-mid never drawn) in a distinct fuchsia/amber/lime palette (`RTM_SIDE_COLORS`) with yellow joints on top of the legacy blue/red/green one — the "Poses" layer stays MediaPipe-only.

### `src/drill2d/` + `src/components/StrokesPage.tsx` / `StrokeTimeline.tsx`

M0 stroke-counting debug harness (spec: docs/superpowers/specs/2026-06-11-drill-effectiveness-sim-design.md).
`drill2d/` is a 1:1 TS mirror of the Kotlin shared/ detection chain — `strokeDetector2d.ts`,
`forwardStrokeFilter.ts`, `repFilter.ts`, plus `geometry.ts` (xScale), `facing.ts`, `parsePoseV2.ts`,
`countStrokes.ts` (pipeline order detect → forward → rep is MANDATORY). NOT related to `src/drill/`
(3D mannequin FK). **Binding fix-flow rule: Kotlin is source of truth — any behavioral fix lands in
shared/ Kotlin first, then is mirrored here; goldens updated in both suites in the same change.**
Golden parity tests (`drill2d/__tests__/golden.test.ts`, mirroring Kotlin
`ForwardStrokeFilterRealFootageTest`): andrii_1_rtm 23 raw / 15 forward / 15 reps;
video_4_rtm (shadow play) 18 / 12 / 9 — fixtures read from
`shared/src/commonTest/resources/fixtures/` via repo-relative path.
`#/strokes` UI: video + color-coded stroke bands (emerald reps / amber RepFilter-dropped / gray
recovery), click-to-seek, knobs for handedness / manual camera yaw (estimator not ported, L-25) /
minPeakSpeed / minPeakGapMs.

**M1 (metrics + feedback) extends the same page (no new route):** measurement modules
`angles2d.ts`, `cameraYaw.ts`, `drillMetrics.ts`, `sanityBounds.ts`, `metricPrecision.ts` are
1:1 Kotlin mirrors (golden-parity); the feedback half — `referenceStandard.ts` (external IDEAL
ranges, NOT personal baseline — spec decision #2), `feedbackEngine.ts` (range-based severity),
`messageCatalog.ts` (EN, "vs ideal" wording), `cadencePolicy.ts`, `analyzeDrill.ts` — deliberately
diverges from Kotlin and has NO shared/ counterpart to golden against. `analyzeDrill` preserves the
M0 count-golden (detection on plain aspect). `#/strokes` now also shows a per-rep results table
(`DrillResultsTable.tsx`), metric on/off toggles, a drill-type selector, and `speechSynthesis`
spoken-feedback playback (`useSpokenFeedback.ts`, EN voice, audio default + text-only mode).
Clicking a stroke band loops its `start→end` segment (`strokeLoop.ts` `loopBackTarget`, wired in
the video `onTimeUpdate`); a `🔁 Цикл` toggle in the selected-stroke row turns it off without
deselecting. Reference ranges are PROVISIONAL (see referenceStandard.ts header).

### `src/components/Drill2Mannequin.tsx` (~805 lines)

3D mannequin via react-three-fiber. SLERPs bone **directions** between start/end poses; bone **lengths** are fixed (Drillis & Contini 1966, 1.7 m scale) — noise can't distort proportions. Feet frozen to start pose.

Key noise guards: `kneePerpAt` (flamingo-leg fix), `applyCoMBalancer` (~10% forward shear), camera-pitch depth un-squash, `trustZ=false` projects shoulders to XY. Flagged joints: yellow emissive.

### `src/components/DrillEditor.tsx` (~311 lines)

Anchor-based pose editor. Two `PoseAnchor` states (start/end); `AnchorSliders` → `reconstructFromAnchor` → landmarks. Playback loops 0→9→0 at ~8 fps. Frame loader debounced 120 ms. `bonesOverride` shared across both phases.

- Gotcha: line 311 has dangling `void lerpAnchor` (unused).

### `src/components/Drill2Preview.tsx` (~456 lines)

Ping-pong demo viewer. Feet re-aligned to start footprint (`alignedEnd`); `ankleAnchors` are visibility-filtered medians across whole pose file. Playback 24/1.3 fps, 450 ms pause between reps. Does **not** share code with DrillEditor — intentional.

### `src/drill/skeletonReconstructor.ts` (~529 lines)

FK: `PoseAnchor` → 33 landmarks via Rodrigues rotations. Layered frames on `hipMid`: leg (figureYaw) → hip (+bodyRotation+pelvicRoll) → shoulder (+shoulderRotation). Elbow hinge uses weighted Rodrigues (avoids 180° snap in overhead poses). Foot-IK translates whole body.

Invariants:
- Default swivel/wrist yaw (0°) → byte-identical output (fingerprint-safe fast paths).
- Knee ≥30° (`EFFECTIVE_KNEE_MIN_DEG`); `TILT_TO_KNEE_BEND` and `TILT_TO_HIP_BACK` = 0.
- `abSign` flips per side → UI-positive = lateral away from midline on both arms.
- Foot-IK is whole-body translation, not per-leg cosine-law.

### `src/drill/anchorExtractor.ts` (~655 lines)

IK: landmarks → `PoseAnchor`. z dampened 0.5× throughout. Shoulder abduction uses `asin` of across-component (not atan2 — atan2 over-estimates under flexion). Elbow yaw skipped when arm nearly straight (≥175°). Rodrigues twistedHinge recovery explained at [anchorExtractor.ts:374](src/drill/anchorExtractor.ts#L374).

Left at 0 (ambiguous from single view): knee swivel, pelvicRoll, torsoSideBend, shoulderShrug. Body yaw → `figureYawDeg` (not `bodyRotationDeg`) for round-trip clean.

### `src/drill/anchorInterpolator.ts` (~62 lines)

`lerpAnchor(a, b, t)` and `interpolateAnchors(start, end, count)`. **No circular unwrapping** — lerping −170°→+170° goes through −180°.

### `src/drill/PoseAnchor.ts` (~316 lines)

`PoseAnchor`: ~50-field struct (torso, both arms, both legs, stance, hip position). All rotations in degrees. `ANCHOR_PARAM_GROUPS`: 5 UI groups (Torso, Right arm, Left arm, Legs, Position). `figureYawDeg` = whole-body; `bodyRotationDeg` = hips vs legs; `shoulderRotationDeg` = X-factor coil.

### `src/drill/neutralPose.ts` (~148 lines)

- `NEUTRAL_POSE` — TT ready crouch, 50° yaw, 25° tilt, right shoulder forehand-loaded.
- `STANDING_POSE` — all zeros, 180° joints.
- `MIDPOINT_POSE` — dynamically built from slider midpoints/defaultValues.

### `src/drill/jointMap.ts` (~162 lines)

16 joints: landmark index (or averaged pair), `controlParams`, color ID, Ukrainian label. `JOINT_ORDER`: head→feet, right before left. Composite joints (hipMid, shoulderMid) HUD-only.

## Where things live

- Trajectory math: `src/utils/trajectoryPipeline*.ts` (V1–V3, 3D, 3Dv2)
- Overlays: `src/components/*Overlay.tsx`
- Table labeling: `TableLabelPanel`, `*TableLabels/Detect/GridOverlay`
- API persistence: `/api/labels/:base`, `/api/table-labels/:base`, `/api/videos`, `/api/dataset/*` — Vite middleware in `vite.config.ts`
- Settings: `localStorage` key `poses_viewer_settings`
