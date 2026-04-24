# Mannequin reference

This document describes the poses_viewer mannequin: the degree-of-freedom
set, the FK pipeline that maps angles to landmarks, the import pipeline that
maps raw MediaPipe landmarks back to angles, and the known approximations
that degrade round-trip fidelity.

Sources: all claims about field names, ranges, and weights are read directly
from the TypeScript source. Where source file names appear they link to the
relevant file under `../src/drill/`.

---

## 1. Design principles

**Angles, not directions.** The full pose of the mannequin is captured by a
single `PoseAnchor` record — a flat bag of named angle (degrees) and position
(normalised screen coordinates) fields. There are no raw landmark directions
stored. This means a drill keyframe is human-readable, diff-friendly, and
transport-trivial.

**Lossless-within-limits.** An imported MediaPipe pose round-trips through
`extractAnchorFromLandmarks → reconstructFromAnchor` with accuracy bounded
only by the clamping ranges of the sliders and the known approximations listed
in Section 5. Outside those edges the set of PoseAnchor fields is complete
enough to reproduce the 3-D arm configuration exactly.

**Deterministic FK.** `reconstructFromAnchor` in
[skeletonReconstructor.ts](../src/drill/skeletonReconstructor.ts) is a pure
function: same `PoseAnchor` in, same 33 `Landmark[]` out, every time. There
is no random state, no frame history, no adaptive compensation. The two
auto-compensation constants (`TILT_TO_KNEE_BEND = 0`,
`TILT_TO_HIP_BACK = 0`) are explicitly zero; every DOF the user sets is
honoured literally.

---

## 2. Degrees of freedom

All angle fields are in degrees. Position fields are normalised [0, 1] in the
MediaPipe screen-coordinate convention (x right, y down, z away from camera).
Ranges are taken from `ANCHOR_PARAM_GROUPS` in
[PoseAnchor.ts](../src/drill/PoseAnchor.ts).

### 2.1 Trunk

| Field | Range | Meaning |
|---|---|---|
| `figureYawDeg` | −180 … 180 | Yaw of the entire figure around the vertical axis through `hipMid`. Rotates legs, hips, torso, arms, and head together. 0 = facing the camera straight on; positive rotates toward +z (player's right swings back). |
| `bodyRotationDeg` | −90 … 90 | Pelvic twist relative to the planted legs. Rotates hips, torso, arms, and head; legs stay where `figureYawDeg` put them. Positive = right shoulder back. Used to represent trunk-vs-legs torsion (stroke loading). |
| `pelvicRollDeg` | −30 … 30 | Lateral pelvic tilt (roll around the body-forward axis). Positive = player's right hip rises. Does not propagate into `torsoUp`; torso side-bend is controlled independently. |
| `shoulderRotationDeg` | −90 … 90 | Extra yaw applied to the shoulder line on top of `bodyRotationDeg` (hips stay). Models the shoulder-hip X-factor. Positive = shoulders rotate further back to the player's right. |
| `torsoTiltDeg` | 0 … 75 | Forward bend at the hips. The whole spine rotates rigidly around the hip line. |
| `torsoSideBendDeg` | −30 … 30 | Lateral torso lean (roll of `torsoUp` around body-forward). Positive = upper body leans to the player's right. Applied after `torsoTiltDeg`. |
| `shoulderShrugNorm` | −0.03 … 0.06 | Offset of the shoulder midpoint along `torsoUp`, in normalised height units. Positive raises both shoulders. |

### 2.2 Arms

Both sides share the same seven DOFs. The left arm mirrors the sign convention
so "positive = external rotation / radial deviation" means the same anatomical
direction on both sides.

| Field | Range | Meaning |
|---|---|---|
| `*ShoulderAngleDeg` | −30 … 180 | Forward flexion (sagittal plane). 0 = arm hanging down, 90 = arm pointing forward, 180 = arm overhead. Default 41°. |
| `*ShoulderAbductionDeg` | 0 … 120 | Sideways abduction (coronal plane). 0 = arm along torso, 90 = arm horizontal out to the side. Default 31°. |
| `*ElbowAngleDeg` | 30 … 180 | Interior elbow angle. 180 = straight, 30 = maximum bend. |
| `*ElbowYawDeg` | −70 … 90 | Humeral twist — rotates the elbow on a circle around the shoulder→elbow axis without moving shoulder or elbow. Positive = external rotation. Default 0. Implemented as Tasks 5-6 of the lossless-pose-anchor plan. |
| `*WristAngleDeg` | 90 … 180 | Interior wrist (palmar flexion) angle. 180 = straight. |
| `*WristYawDeg` | −30 … 20 | Ulnar/radial deviation — lateral deflection of the hand at the wrist independent of palmar flex. Positive = radial deviation (thumb side). Default 0. |
| `*ForearmTwistDeg` | −90 … 90 | Forearm pronation/supination. Rotates the finger-fan around the forearm axis. |

`*` expands to `right` or `left`.

### 2.3 Legs

| Field | Range | Meaning |
|---|---|---|
| `*ThighForwardDeg` | −30 … 120 | Hip flexion. Positive = knee forward. Controls the thigh tilt in the sagittal plane. |
| `*ThighAbductionDeg` | −30 … 64 | Hip abduction. Positive = leg out away from midline. Default 17°. |
| `*KneeAngleDeg` | 30 … 180 | Interior knee angle. 180 = straight, 30 = deep bend. |
| `*KneeYawDeg` | −85 … 85 | Yaw of the knee-bend plane around the vertical axis through the hip socket. Controls the "knee-over-toe" direction. The world yaw of the foot tip is `kneeYawDeg + footYawDeg`. |
| `*FootYawDeg` | −60 … 60 | Foot rotation relative to the shin. Does not affect the knee plane. |
| `stanceWidthNorm` | 0.10 … 0.70 | Distance between the two ankles in normalised screen units. |
| `hipMidX` | 0.30 … 0.70 | Horizontal position of the hip midpoint in normalised screen coordinates. |
| `hipMidY` | 0.25 … 0.55 | Vertical position of the hip midpoint (y-down convention). |

`*` expands to `left` or `right`.

### 2.4 Head

Not a DOF. The head rides rigidly on the shoulder midpoint and follows
`torsoUp` and `shoulderAcross`. There is no independent head yaw, pitch, or
roll. Eleven face/ear landmarks (0-10) are placed at fixed offsets from the
nose, which is 75% of `headToShoulder` above the shoulder midpoint.

### 2.5 Feet

One DOF per foot (`*FootYawDeg`). Dorsiflexion/plantarflexion (foot pitch) is
not modelled. The foot is rendered as a forward-pointing segment from the
ankle; its world direction is `rotY(legForward, kneeYawDeg + footYawDeg)`.

---

## 3. FK pipeline

Source: [skeletonReconstructor.ts](../src/drill/skeletonReconstructor.ts).

All coordinates use the MediaPipe convention: x right, y down, z away from
camera. "Up" is −y. Yaw around the y-axis: positive rotates toward +z.

### 3.1 Coordinate frames

Two yaw layers stack on top of each other:

1. **Leg frame** — `rotY(forward, figureYawDeg)`. Legs (thighs, shins, feet)
   live here, so pelvic twist does not sweep the planted feet.
2. **Hip frame** — `rotY(forward, figureYawDeg + bodyRotationDeg)`. Hips,
   torso, and head live here.
3. **Shoulder frame** — `rotY(forward, figureYawDeg + bodyRotationDeg + shoulderRotationDeg)`.
   Arms and the shoulder line live here; rotating shoulders sweeps both arms.

Pelvic roll (`pelvicRollDeg`) tilts the hip-across vector around the
body-forward axis. Torso tilt rotates `torsoUp` forward around `acrossLevel`.
Torso side-bend (`torsoSideBendDeg`) then rolls `torsoUp` around `forward`,
composing cleanly with the tilt.

### 3.2 Arm chain

For each side (right then left):

1. **Upper arm direction** — start from `torsoDown`. Rotate by abduction
   around `shoulderForward` (abSign flips per side so positive always means
   "away from midline"). Then rotate by forward flexion around
   `shoulderAcross`. Normalise. Elbow = shoulder + upperArmDir × upperArm.

2. **Canonical hinge** — a weighted blend of three terms, all evaluated and
   normalised together:
   - `3 × cross(torsoUp, upperArmDir)` — anatomical bend-plane axis;
     strong when the arm is abducted, vanishes near the spine pole.
   - `shoulderAcross` — stable fallback for the arm-along-spine cases,
     preventing a 180° snap as the arm crosses the pole.
   - `2 × shoulderForward` — anterior bias so the forearm always folds
     toward the face/chest half-space even for extreme overhead poses.

3. **Humeral twist (elbowYawDeg)** — the 3rd shoulder DOF. Rodrigues-rotates
   the canonical hinge around `upperArmDir` by `abSign × elbowYawDeg`. At
   `elbowYawDeg = 0` this is the identity; the fast path is skipped entirely
   so pre-existing neutrals reconstruct byte-for-byte.

4. **Forearm direction** — Rodrigues-rotate `upperArmDir` around
   `twistedHinge` by `−(180 − elbowAngleDeg)`. Wrist = elbow + forearmDir × forearm.

5. **Wrist bend** — Rodrigues-rotate `forearmDir` around `shoulderAcross`
   by `−(180 − wristAngleDeg)`.

6. **Forearm twist** — `fanSideBase = rot(shoulderAcross, forearmDir, twistDeg)`.
   Rotates the finger-fan around the forearm axis.

7. **Wrist yaw** — `handNormal = cross(forearmDir, fanSideBase)`. Both
   `bentHandDir` and `fanSide` are Rodrigues-rotated around `handNormal` by
   `abSign × wristYawDeg`. At `wristYawDeg = 0` the fast path leaves all
   landmarks byte-identical.

8. **Finger landmarks** — pinky, index, and thumb placed at fixed offsets
   along `handDir` and `fanSide` from `handCenter`.

### 3.3 Leg chain

Each thigh is placed in the leg frame:
1. Apply abduction: rotate `worldDown` around `legForward` by `abSign × abductionDeg`.
2. Apply forward flexion: rotate around `legAcross`.
3. Apply knee yaw: `rotY(flexed, kneeYawDeg)`.

The shin bends in the vertical plane that the knee points along (hinge =
`normalize([-kneeForward.z, 0, kneeForward.x])`).

### 3.4 Ground anchor

After placing all joints, a post-pass shifts the entire figure vertically so
the lowest ankle (max y) lands at `GROUND_ANCHOR_Y = 0.92`. Bending knees
shortens the vertical leg span, causing the hips and torso to drop naturally.
In asymmetric stances the higher foot floats; use `*ThighForwardDeg` on the
trail leg to bring it down.

### 3.5 Bone lengths

Canonical lengths (in normalised screen-height units) are defined in
[SkeletonModel.ts](../src/drill/SkeletonModel.ts):

| Segment | Length |
|---|---|
| `headToShoulder` | 0.18 |
| `shoulderWidth` | 0.30 |
| `torso` | 0.32 |
| `upperArm` | 0.22 |
| `forearm` | 0.20 |
| `hand` | 0.08 |
| `hipWidth` | 0.20 |
| `thigh` | 0.28 |
| `shin` | 0.26 |
| `footForward` | 0.10 |

Per-side overrides (`BoneLengthsOverride`) can be passed to
`reconstructFromAnchor` for faithful replay of a specific player's
proportions; drill authoring always uses the canonical lengths.

---

## 4. Import pipeline

Source: [anchorExtractor.ts](../src/drill/anchorExtractor.ts).

`extractAnchorFromLandmarks(landmarks) → PoseAnchor` decomposes raw
MediaPipe 33-landmark output into the angle set. The pipeline runs once per
bootstrap (user picks a reference frame); it is not called on every video
frame.

### 4.1 Trunk extraction

**Figure yaw.** The observed body yaw is computed from both the hip axis
(`lHip − rHip`) and shoulder axis (`lShoulder − rShoulder`), weighted by each
axis's 2D (xz-plane) magnitude. This down-weights axes that collapse when the
torso bends far forward. The resulting average yaw is placed verbatim into
`figureYawDeg`. `bodyRotationDeg` is set to 0 — a single-camera view cannot
distinguish pelvic twist from a yawed figure.

**Shoulder rotation.** The X-factor is the shoulder-axis yaw minus the
hip-axis yaw (both from the weighted computation above).

**Torso tilt.** The spine vector `shoulderMid − hipMid` is measured against
world-up (`[0, −1, 0]`). The z-component of the spine vector is halved before
this measurement (see Z_DAMP below) to reduce the contribution of noisy depth.

**Not extracted.** `pelvicRollDeg`, `torsoSideBendDeg`, and
`shoulderShrugNorm` are not reliably decomposable from a single-view pose;
they are set to 0 on every import.

### 4.2 Arm extraction

**Shoulder flexion and abduction.** The upper-arm vector
`elbow − shoulder` is projected into the body frame and decomposed using the
exact inverse of the FK rotation chain:
- abduction = `asin(acrossComponent)` — abduction is a pure rotation
  around the forward axis, so the across component of the unit direction
  determines it uniquely.
- flexion = `atan2(forwardComponent, verticalComponent)` — flex is
  independent of abduction in this decomposition.

**Z_DAMP = 0.5.** Before projection, the z-component of the upper-arm vector
is multiplied by 0.5. MediaPipe's depth estimate is noisy enough that the full
z value inflates the extracted flexion and abduction angles; halving it brings
the result closer to what the FK can reproduce in 2-D projection. The
trade-off is explicit: on clean synthetic inputs (z is correct), z-dampening
under-estimates flex/abduction by up to ~20° for arms with deep z-motion; on
real MediaPipe inputs the noise it suppresses is of the same scale. The same
0.5 factor is applied to thigh decomposition.

**Elbow angle.** Interior angle `angle(shoulder→elbow, elbow→wrist)`.

**Elbow yaw.** Inverts the FK humeral-twist layer using Rodrigues algebra:
given `upperArmDir` and the observed `forearmDir`, recovers the direction of
`twistedHinge` in the plane perpendicular to `upperArmDir`, then measures the
signed angle from the canonical `elbowHinge` to that direction around
`upperArmDir`. Returns 0 if `elbowAngleDeg ≥ 175°` (no bend plane).

**Wrist angle.** Interior angle `angle(elbow→wrist, wrist→index)`.

**Wrist yaw.** Inverts the FK hand-fan rotation: reconstructs the reference
direction of `wrist→index` at `wristYaw = 0` (using `bentHandDir` and
`fanSideBase` at `twist = 0`), then measures the signed angle from that
reference to the observed `wrist→index` direction around `handNormal =
cross(forearmDir, fanSideBase)`.

**Forearm twist.** Inverts the FK fan rotation:
1. Observe `fanSideObs = normalize(index − pinky)`.
2. Undo wristYaw: rotate `fanSideObs` around `handNormal` by
   `−abSign × wristYawDeg` to recover `fanSideBase`.
3. Measure `signedAngleAround(shoulderAcross, fanSideBase, forearmDir)`.

`handNormal` is approximated as `cross(forearmDir, shoulderAcross)` (exact at
`twist = 0`; drifts slightly for larger twist values, but the slider clamp to
±90° and round-trip tolerance absorb the residual error).

### 4.3 Leg extraction

Knee angles are interior angles. Thigh flexion and abduction use the same
z-dampened body-frame decomposition as the arm, with identical `Z_DAMP = 0.5`.

Foot yaw from `atan2(footDir.x, −footDir.z)` is placed into `kneeYawDeg`
and `footYawDeg` is set to 0. A single camera cannot separate the knee bend
plane from the foot's axial twist; all observed yaw goes to `kneeYawDeg` so
the rendered foot direction (`kneeYawDeg + footYawDeg`) matches the original.

---

## 5. Known information loss on import

The following situations degrade round-trip fidelity.

1. **Trunk tilt over 75°.** `torsoTiltDeg` is clamped to [0, 75] on return.
   Any forward bend beyond this is lost.

2. **Backward shoulder extension past 30°.** `*ShoulderAngleDeg` clamps to
   [−30, 180]. Negative values (arm behind the torso) beyond −30° are clamped.

3. **Extreme humeral twist beyond slider range.** `*ElbowYawDeg` clamps to
   [−70, 90]. External rotation beyond 90° or internal rotation beyond 70° is
   clamped.

4. **Near-straight elbows (elbowAngleDeg ≥ 175°).** The bend plane degenerates
   and `elbowYawDeg` defaults to 0. No attempt is made to infer humeral
   rotation from a nearly-straight arm.

5. **Z-dampening (0.5×) on arm/thigh decomposition.** Real poses with deep
   z-motion have their flex/abduction under-estimated by up to ~20° on clean
   synthetic data. On real MediaPipe inputs the noise the dampening suppresses
   is of the same scale, so the net effect is smaller but not zero. This is a
   deliberate design choice: noise tolerance is worth more than synthetic
   precision for the live-pose import use case.

6. **Foot pitch (dorsi/plantarflexion) not modelled.** The foot is always
   rendered flat (pointing along the ankle-to-tip direction in the horizontal
   plane). Any up/down foot angle from the source pose is discarded.

7. **Head orientation not modelled.** Head yaw, pitch, and roll from the
   source are not extracted; the head rides rigidly on the shoulder midpoint.

8. **Wrist/twist coupling approximation.** Wrist yaw is extracted assuming
   forearm twist = 0 when building `fanSideBase` (Task 6). Forearm twist is
   then extracted using that wrist yaw to undo the hand-fan rotation (Task 7).
   Both steps ignore the coupled-approximation drift from using `handNormal ≈
   cross(forearmDir, shoulderAcross)` instead of the FK-exact normal. Combined
   error is typically < 5° across the slider ranges.

9. **Pelvic roll, torso side-bend, shoulder shrug not extracted.** These three
   fields are always set to 0 on import.

Outside these edges, round-trip accuracy is typically within ±3° per angle for
arm DOFs (locked by the `elbowYaw round-trip` test in `anchorExtractor.test.ts`).

---

## 6. Maintenance

The following tests lock the mannequin contract. A failing test means either
the FK changed unintentionally (fingerprint tests) or a specific DOF no longer
round-trips (round-trip tests). Update the expected values explicitly and
document the reason when intentional FK changes are made.

All tests are in
[src/drill/\_\_tests\_\_/skeletonReconstructor.test.ts](../src/drill/__tests__/skeletonReconstructor.test.ts)
and
[src/drill/\_\_tests\_\_/anchorExtractor.test.ts](../src/drill/__tests__/anchorExtractor.test.ts).

| Test | File | What it locks |
|---|---|---|
| `STANDING_POSE + NEUTRAL_POSE fingerprints (pre-lossless baseline)` | `skeletonReconstructor.test.ts` | Hash of all 99 xyz values for both reference poses. Flags any unintentional FK drift. |
| `elbowYaw=0 leaves every landmark byte-identical (humeral-twist neutral)` | `skeletonReconstructor.test.ts` | `rightElbowYawDeg = 0` is a strict identity across 3 arm configurations — all 33 landmarks match to 6 decimal places. |
| `wristYaw=0 leaves every landmark byte-identical (hand-deflection neutral)` | `skeletonReconstructor.test.ts` | `rightWristYawDeg = 0` is a strict identity across 3 wrist configurations. |
| `elbowYaw round-trip: extractor recovers yaw within 3° across diverse arm poses` | `anchorExtractor.test.ts` | The primary contract for the humeral-twist DOF: 5 arm configurations, signed error < 3°. |
| `elbow bend on an abducted arm points the forearm toward the head` | `skeletonReconstructor.test.ts` | FK anatomical guard: abducted arm, elbow 90° → forearm goes upward (wrist.y < elbow.y). |
| `elbow bend on a rest-down arm still points the forearm forward (regression guard)` | `skeletonReconstructor.test.ts` | FK anatomical guard: arm hanging down, elbow 90° → forearm points forward (wrist.z < elbow.z). |
