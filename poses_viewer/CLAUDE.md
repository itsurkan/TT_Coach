# poses_viewer — Claude orientation

React + Vite debug/labeling tool for TT_Coach. Overlays pose, ball, contact, and table JSON on video frames and renders a 3D mannequin preview for drill calibration.

- Dev server: `npm run dev` → http://localhost:5780
- Stack: React 18, TypeScript, Vite 6, Tailwind v4, three / @react-three/fiber / drei 0.184, vitest 4
- Vite config proxies JSON + MP4 from repo-top `Videos/<base>/` (see [vite.config.ts](vite.config.ts)). No hash routing; `App.tsx` is a single monolithic screen that conditionally mounts full-screen subviews (`DrillEditor`, `Drill2Preview`, `DatasetBrowser`).

## Read this file before re-reading hot source files

This orientation is here so you don't re-read the same large files every session. Consult the summaries below first; only open the actual source when the summary doesn't answer your specific question (e.g. exact constant values, untouched helper internals). Don't re-read a file just to "refresh" — trust the summary + your search results.

If you change a file listed here, also update its summary so the doc stays truthful.

## Conventions

- Angles everywhere in the drill pipeline are **degrees**, not radians.
- MediaPipe landmarks are normalized `[0, 1]` image coords: `x=right`, `y=down`, `z=away` (z is noisy on side-on cameras).
- Coordinate system for the mannequin (three.js) matches landmark axes.
- UI strings in the drill editor are **Ukrainian** (joint labels come from `jointMap.ts`).
- Tests pin FK outputs via djb2 fingerprints — see `drill/__tests__/skeletonReconstructor.test.ts:500`. Any intentional drift in `reconstructFromAnchor` needs a fingerprint bump with a commit message explaining why.

## File map

### `src/App.tsx` (~1776 lines)

Single top-level component. Loads `data` (pose+ball JSON) keyed by `videoBase`, plus optional V5/YOLO ball data, contacts, labels, crop config, and a stack of trajectory result types (V1/V2/V3, 3D, 3Dv2). Persists UI toggles through `loadSettings` / `saveSettings` against `localStorage` key `poses_viewer_settings`.

Render structure: top-level conditionally mounts `DatasetBrowser`, `DrillEditor`, or `Drill2Preview` as full-screen overlays; otherwise shows the main pose/ball viewer with `PoseCanvas`, trajectory overlays (`TrajectoryV2/V3/V4Overlay`, `Trajectory3D/3Dv2Overlay`), table overlays (`TableLabelsOverlay`, `TableDetectOverlay`, `TableGridOverlay`), `FrameControls`, `LabelPanel`, and a `MultiSelect` for toggles. Helper utils at top: `toNumber`, `normalizeLandmarks`, `normalizeData`, `jsonSuffixes`. This file is mostly wiring — logic lives in the components and `utils/trajectoryPipeline*`.

### `src/components/Drill2Mannequin.tsx` (~805 lines)

3D articulated mannequin rendered with react-three-fiber. Props: `startLms`, `endLms`, `phase` (0–1 lerp), canvas `width`/`height`, optional `cameraPitch`/`cameraYaw`, `useBodyColors`, `selectedJoint`, `flaggedJoints`, `onJointClick`, `onDeselect`, `cameraResetSignal`, `skipCoMBalancer`, `trustZ`.

Builds the skeleton via `buildFixedSkeleton` (exported). Anchors at mid-hip origin; all joints placed by SLERPing bone **directions** between start/end, with **fixed anthropometric bone lengths** (Drillis & Contini 1966, scaled to 1.7 m) — directions interpolate, lengths never do, so MediaPipe noise can't distort proportions. Feet are frozen to the start pose (ping-pong drills involve footwork). Floor sits at the lower ankle.

Guards against MediaPipe noise: `kneePerpAt` forces knees into the forward hemisphere (fixes "flamingo leg"), spine is guarded against severe backward bend. `applyCoMBalancer` shears the upper body ~10% forward via a y-dependent z-offset for athletic posture. Camera pitch is mathematically reversed to un-squash depth. If `trustZ=false`, shoulder midpoints are projected to XY before computing spine direction.

Subcomponents: `Capsule`, `Joint`, `Mannequin`, `Floor`. Helpers: `slerpDir`, `toWorldLandmarks`, `buildFixedSkeleton`. Joint clicks dispatch `onJointClick(JointId)`; flagged joints get a yellow emissive highlight regardless of selection. Depends on `drill/jointColorScheme` and `drill/jointMap`.

### `src/components/DrillEditor.tsx` (~311 lines)

Interactive anchor-based pose editor: load a frame from `andrii_1`, tweak start/end pose via sliders, preview interpolated playback as 2D stick-figure or 3D mannequin. Single prop: `onClose: () => void`.

State: two `PoseAnchor` values (`startAnchor`, `endAnchor`), `activePhase: 'START' | 'END'`, `isPlaying`, `playFrame`, `showGhost`, `humanize`, `frameInput`, `loadStatus`, `bonesOverride`. Playback loops 0 → 9 → 0 at ~8 fps via `requestAnimationFrame` + 120 ms tick. Frame loader auto-fires on input change (120 ms debounce) — no manual button.

Flow: `AnchorSliders` (right panel) mutates the active anchor → `reconstructFromAnchor(anchor, bonesOverride)` → `displayedLandmarks` and `ghostLandmarks` (the other phase, for reference). Ghost auto-hides during playback. `bonesOverride` persists across START/END so both share the player's anatomy. Canvas shows `Drill2Mannequin` (humanize on, camera pitch/yaw default 15°) or `DrillSkeletonCanvas` (2D).

Depends on: `drill/PoseAnchor`, `drill/neutralPose` (`STANDING_POSE`, `cloneAnchor`), `drill/skeletonReconstructor` (`reconstructFromAnchor`, `BoneLengthsOverride`), `drill/anchorInterpolator`, `drill/anchorExtractor` (`extractAnchorFromLandmarks`, `extractBoneLengths`, `parsePoseFixture`), `DrillSkeletonCanvas`, `AnchorSliders`, `Drill2Mannequin`. Gotcha: line 311 has a dangling `void lerpAnchor;` (currently unused).

### `src/components/Drill2Preview.tsx` (~456 lines)

Standalone demo viewer — loads two pose frames from `andrii_1`, ping-pong animates between them, renders 2D stick OR 3D mannequin, drag-to-rotate yaw. Prop: `onClose: () => void`. State: start/end frame indices, loaded landmark arrays, animation tick, pause flag, rep counter, yaw angle, humanizer toggle.

Key transforms: `ankleAnchors` are **visibility-filtered medians across the whole pose file** (computed once, cached in `framesRef`), giving noise-immune foot anchors. End pose's feet are explicitly re-aligned to the start-pose footprint (`alignedEnd`) — rest of body translated by centroid delta — then `blended = lerp(start, alignedEnd, phase)` and `rotate` applies yaw around hip midpoint.

Playback: LOOP_FRAMES advance per tick with a 450 ms pause between reps; FPS = 24 / 1.3 (1.3× slower than captured). 2D canvas draws rotated start/end as cyan/magenta ghosts plus blended in yellow, using `POSE_EDGES` topology and auto-fit bounding box. Humanizer on → `Drill2Mannequin` with `zScale=0.3` (heavy depth suppression) and `ankleAnchors` for stable feet. One horizontal pointer drag = 2π rotation. HUD shows phase t, frame range, yaw°, rep count.

Deliberately does **not** share fixture-loading or skeleton-rendering code with `DrillEditor` — duplication is intentional. Only internal import: `./Drill2Mannequin`.

### `src/drill/skeletonReconstructor.ts` (~505 lines)

Forward kinematics: `PoseAnchor` → 33 MediaPipe landmarks via Rodrigues rotations, with foot-IK pinning the lowest foot to `GROUND_ANCHOR_Y`. Main export:

```
reconstructFromAnchor(anchor: PoseAnchor, bonesOverride?: BoneLengthsOverride, options?: { skipFootIK?: boolean }): Landmark[]
```

Also exports `GROUND_ANCHOR_Y` and `BoneLengthsOverride` interface.

Layered frames centered on `hipMid`: leg frame (figureYaw only), hip frame (figureYaw + bodyRotation + pelvic roll), shoulder frame (hip frame + shoulderRotation). Torso is one rigid segment (tilts via `torsoTiltDeg` around hip across-axis, plus `torsoSideBendDeg`). Arms: 2-DOF shoulder (flexion around across, abduction around forward), elbow hinge as **weighted Rodrigues** (cross-product + across + forward-bias terms) to keep forearm anteriorly oriented — naked cross-product alone snaps 180° in overhead poses; the weighting is empirically tuned. Elbow swivel (7th DOF) orbits around shoulder→wrist axis. Legs split thigh into forward/abduction, knee bends in a plane perpendicular to `kneeYaw`.

Invariants worth defending:
- Default elbow swivel / wrist yaw (0°) produce byte-identical output with old frames via fast paths — critical for test fingerprints.
- Knee clamped to ≥30° (`EFFECTIVE_KNEE_MIN_DEG`). `TILT_TO_KNEE_BEND` and `TILT_TO_HIP_BACK` both 0 (no auto-compensation).
- Shoulder abduction `abSign` flips per side so UI-positive yaw means lateral away from midline on both L/R.
- Foot-IK translates the whole body (not per-leg cosine-law) so user knee-bend input isn't overwritten when hip-ground ≈ thigh+shin.

Depends on: `./SkeletonModel` (BONES, LM, LANDMARK_COUNT), `./PoseAnchor`, `../types` (Landmark).

### `src/drill/anchorExtractor.ts` (~655 lines)

Inverse of the FK: landmarks → anchor. Exports:

```
extractAnchorFromLandmarks(lms: Landmark[]): PoseAnchor
extractBoneLengths(lms: Landmark[]): BoneLengthsOverride
parsePoseFixture(raw: unknown): PoseFixture     // tolerates tuple or object landmark formats
```

Plus types `PoseFixture`, `PoseFixtureFrame`.

Decomposes observed geometry into DOF via vector projection + signed-angle measurement. Body yaw averaged from hip and shoulder axes (2D-magnitude weighted to resist z-noise). Torso tilt: angle between dampened torso-up and world-up, z dampened 0.5× across the whole module to down-weight MediaPipe z-noise. Shoulder flexion uses `atan2(fwd, vert)`, abduction uses **`asin` of the across-component** (not atan2 — atan2 over-estimates when vert shrinks under flexion).

Elbow yaw extraction skipped when elbow is nearly straight (≥175°) — bend plane degenerates. Complex Rodrigues recovery of `twistedHinge` in the plane perpendicular to upper-arm is explained inline at [anchorExtractor.ts:374](src/drill/anchorExtractor.ts#L374). Wrist yaw inverted from `bentHandDir + 0.2·fanSide` rotated by `handNormal`; forearm twist extracted by undoing wrist yaw from pinky↔index fan direction.

Ambiguous from a single MediaPipe view → left at 0: knee swivel, pelvicRoll, torsoSideBend, shoulderShrug. Extracted body yaw goes into `figureYawDeg` (not `bodyRotationDeg`) to keep single-view round-trip clean. All outputs clamped to slider ranges (shoulder ±30…180°, wrist ±90…180°, knee ±30…180°, thigh forward ±30…120°, thigh abduction ±30…80°).

Depends on: `./PoseAnchor`, `../types`, `./SkeletonModel` (LM), `./skeletonReconstructor` (BoneLengthsOverride).

### `src/drill/anchorInterpolator.ts` (~62 lines)

Component-wise linear blend between anchors. Exports:

```
lerpAnchor(a: PoseAnchor, b: PoseAnchor, t: number): PoseAnchor
interpolateAnchors(start: PoseAnchor, end: PoseAnchor, count: number): PoseAnchor[]
```

`result = a·(1−t) + b·t` for all 34 numeric fields. `interpolateAnchors` generates evenly spaced samples via `t = i / (count − 1)`; `count ≤ 1` → `[start]`, `count = 2` → `[start, end]`.

**Gotcha:** no circular unwrapping. Lerping from −170° to +170° goes through −180°, not the short 20° path. Caller's responsibility to pre-normalize if shortest-path matters.

### `src/drill/PoseAnchor.ts` (~200 lines)

Type + slider specs. `PoseAnchor` is a ~50-field struct: torso (yaw, rotation, tilt, side-bend, pelvic roll, shrug), both arms (shoulder angle/abduction, elbow, wrist angle/yaw, forearm twist, elbow swivel), both legs (thigh forward/abduction, knee angle/yaw/swivel, foot yaw), stance width, hip position. All rotations in **degrees**; hip positions in normalized [0, 1] screen coords.

Also exports `AnchorParamSpec` (min/max/step/label, optional `defaultValue` to decouple reset target from slider range) and `ANCHOR_PARAM_GROUPS` — five UI groups: Torso, Right arm, Left arm, Legs, Position.

Semantics: `figureYawDeg` rotates the whole body as one unit; `bodyRotationDeg` twists hips relative to legs; `shoulderRotationDeg` adds X-factor coil on top of body rotation. Knee and elbow yaw rotate joints in-place around their long axes (hip→ankle or shoulder→wrist, pinned). Knee swivel is anatomically tiny (±5°).

### `src/drill/neutralPose.ts` (~145 lines)

Three preset anchors and helpers:
- `NEUTRAL_POSE` — athletic table-tennis ready crouch; editor startup default. Right-handed, figure facing three-quarters (50° yaw), 25° forward torso tilt, right shoulder pre-loaded for a forehand.
- `STANDING_POSE` — anatomical reference: all zeros except fully-extended 180° joints.
- `MIDPOINT_POSE` — built dynamically from slider specs: each param at its midpoint or custom `defaultValue`, snapped to step grid, decimal-trimmed to avoid float noise.

Helpers: `cloneAnchor` (shallow), `buildMidpointPose`. Foot positioning accounts for anatomical heel-bone offset so heels sit ~4 cm behind the head line.

### `src/drill/jointMap.ts` (~162 lines)

Wires MediaPipe landmarks + PoseAnchor sliders to 16 skeleton joints for click-to-highlight and HUD. `JOINT_MAP: Record<JointId, JointDefinition>`; each entry names the landmark source (single index or averaged pair for composites like `midShoulder`/`hipMid`), `controlParams` (slider keys that move the joint), body-part color ID, and a **Ukrainian** display label. `JOINT_ORDER` iterates deterministically (head→feet, right before left).

Composite joints (`hipMid`, `shoulderMid`) aren't separately rendered — averaged only for HUD. Some joints (e.g. `head`) lack dedicated FK params and use `torsoTiltDeg` as a proxy.

## Where things live (quick index)

- Trajectory math: `src/utils/trajectoryPipeline*.ts` (V1–V3, 3D, 3Dv2) — each module exports a `predictTrajectory*` function and a result type.
- Overlays: `src/components/*Overlay.tsx` (table, trajectory variants).
- Table labeling: `TableLabelPanel`, `TableLabelsOverlay`, `TableDetectOverlay`, `TableGridOverlay`.
- Persistence: `/api/labels/:base`, `/api/table-labels/:base`, `/api/videos`, `/api/dataset/*` — implemented as Vite middleware in `vite.config.ts`.
- Settings persistence: `localStorage` key `poses_viewer_settings` via `loadSettings`/`saveSettings` in `App.tsx`.
