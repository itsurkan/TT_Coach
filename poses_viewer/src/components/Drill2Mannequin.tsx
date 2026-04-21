// 3D articulated mannequin view for Drill 2 — matte capsule-and-sphere doll
// rendered with react-three-fiber. Inspired by the qualitative-results figures
// in Dabral et al. "Learning 3D Human Pose from Structure and Motion"
// (ECCV 2018): wooden posing-doll aesthetic, one directional light.
//
// Bone lengths come from a fixed anthropometric table (Drillis & Contini 1966,
// adult mean) scaled by a constant 1.7 m figure height, and applied as a
// `fit_standard_skeleton` rebuild each frame. The lerped MediaPipe pose only
// contributes joint *directions* — bone *lengths* never change. The skeleton
// is hip-rooted (mid-hip at origin, every other joint grown outward), so
// feet land wherever the rebuild puts them; proportions always win.

import { Canvas } from '@react-three/fiber'
import { Grid, OrbitControls } from '@react-three/drei'
import { useMemo } from 'react'
import * as THREE from 'three'

type Landmark = { x: number; y: number; z?: number; visibility?: number }

// Doll stays uniformly matte-cream; joints are a touch darker in the same hue
// family so articulation reads without introducing a decorative palette.
const COLOR_BODY  = '#ece8df'   // warm cream — bones + head
const COLOR_JOINT = '#cec2a9'   // same hue, ~10% darker — all joint spheres
const ROUGHNESS = 0.7
const H = 1.7                   // figure height in world units — also the scale
                                // factor applied to normalized landmark coords.
                                // Constant so body proportions are invariant.

// MediaPipe indices we consume.
const NOSE = 0
const L_SHOULDER = 11, R_SHOULDER = 12
const L_ELBOW = 13, R_ELBOW = 14
const L_WRIST = 15, R_WRIST = 16
const L_HIP = 23, R_HIP = 24
const L_KNEE = 25, R_KNEE = 26
const L_ANKLE = 27, R_ANKLE = 28
const L_FOOT = 31, R_FOOT = 32

// Anthropometric segment lengths as fractions of total body height H.
// Source: Drillis & Contini 1966 adult-mean table, widely used in biomechanics.
const ANTHRO = {
  upperArm: 0.186,
  forearm:  0.146,
  thigh:    0.245,
  shin:     0.246,   // ≈ thigh — knee lands at leg midpoint
  foot:     0.152,
  shoulderHalf: 0.259 / 2,   // half of full shoulder width
  hipHalf:      0.191 / 2,
  spine:    0.288,    // midHip ↔ midShoulder
  neck:     0.052,
  headR:    0.07,     // head radius (diameter ≈ H/7)
} as const

// Segment thicknesses as fractions of H, derived from typical adult
// circumferences. Keeping these proportional to H means radii shrink with the
// figure so a close-up pose doesn't turn into a blob of overlapping capsules.
const RADIUS = {
  upperArm:  0.028,
  forearm:   0.024,
  thigh:     0.045,
  shin:      0.032,
  foot:      0.022,
  neck:      0.030,
  spine:     0.065,
  chestBar:  0.038,
  pelvisBar: 0.048,
  shoulder:  0.045,
  elbow:     0.032,
  wrist:     0.034,
  hip:       0.052,
  knee:      0.040,
  ankle:     0.030,
} as const

const UP = new THREE.Vector3(0, 1, 0)

// Spherical linear interpolation between two direction vectors on the unit
// sphere. Used to animate bone orientations along great-circle arcs so joints
// rotate through arcs (what real anatomy does) instead of chord-lerping, which
// would dip limbs through the body mid-sweep.
function slerpDir(a: THREE.Vector3, b: THREE.Vector3, t: number): THREE.Vector3 {
  const an = a.clone().normalize()
  const bn = b.clone().normalize()
  const dot = Math.max(-1, Math.min(1, an.dot(bn)))
  if (dot > 0.9995) return an.lerp(bn, t).normalize()
  if (dot < -0.9995) {
    const ortho = Math.abs(an.x) < 0.9 ? new THREE.Vector3(1, 0, 0) : new THREE.Vector3(0, 1, 0)
    const axis = new THREE.Vector3().crossVectors(an, ortho).normalize()
    const q = new THREE.Quaternion().setFromAxisAngle(axis, Math.PI * t)
    return an.clone().applyQuaternion(q)
  }
  const omega = Math.acos(dot)
  const sinOmega = Math.sin(omega)
  const a0 = Math.sin((1 - t) * omega) / sinOmega
  const b0 = Math.sin(t * omega) / sinOmega
  return new THREE.Vector3(
    an.x * a0 + bn.x * b0,
    an.y * a0 + bn.y * b0,
    an.z * a0 + bn.z * b0,
  ).normalize()
}

// Returns the component of `kneeHint − hip` perpendicular to the hip→ankle
// direction. This is the knee-bend perpendicular for one endpoint pose. Used
// in `buildFixedSkeleton` to SLERP the bend plane between start and end so
// knees rotate naturally without the thigh swinging through 180°.
function kneePerpAt(
  hip: THREE.Vector3,
  ankle: THREE.Vector3,
  kneeHint: THREE.Vector3,
): THREE.Vector3 {
  const along = ankle.clone().sub(hip)
  const d = along.length()
  if (d < 1e-6) return UP.clone().negate()
  along.divideScalar(d)
  const hint = kneeHint.clone().sub(hip)
  const perp = hint.clone().sub(along.clone().multiplyScalar(hint.dot(along)))
  if (perp.lengthSq() < 1e-8) {
    const forward = new THREE.Vector3(0, 0, -1)
    const tangent = forward.clone().sub(along.clone().multiplyScalar(forward.dot(along)))
    return tangent.lengthSq() > 1e-10 ? tangent.normalize() : UP.clone().negate()
  }
  return perp.normalize()
}

function toWorldLandmarks(lms: Landmark[], zScale = 1): THREE.Vector3[] {
  // Center on hip midpoint, flip Y (MediaPipe y grows down), flip Z (camera
  // convention: +Z toward viewer in three.js, while MediaPipe z is negative
  // toward camera). Scale by H so normalized coords become metres.
  // zScale < 1 suppresses noisy depth estimates (e.g. side-on camera views).
  const hipMidX = (lms[L_HIP].x + lms[R_HIP].x) / 2
  const hipMidY = (lms[L_HIP].y + lms[R_HIP].y) / 2
  const hipMidZ = ((lms[L_HIP].z ?? 0) + (lms[R_HIP].z ?? 0)) / 2
  return lms.map(l =>
    new THREE.Vector3(
      (l.x - hipMidX) * H,
      -(l.y - hipMidY) * H,
      -((l.z ?? 0) - hipMidZ) * H * zScale,
    ),
  )
}

// Rebuild the skeleton with fixed anthropometric bone lengths. Bone
// *orientations* are SLERPed between the start-pose and end-pose so joints
// rotate through great-circle arcs (not chords through the body). Legs use
// 2-link IK from each frame's moving hip to a **median-anchored ankle** —
// stable across the whole pose file, immune to per-frame MediaPipe noise.
// Knee direction and toe direction are derived from the body-forward vector
// (perpendicular to the SLERPed hip line) so no raw knee/foot landmarks are
// read at all.
//
// Hip-rooted: mid-hip stays at the origin and every other joint grows
// outward along its SLERPed direction by its fixed bone length.
type AnkleAnchors = { L: { x: number; y: number; z: number }; R: { x: number; y: number; z: number } } | null
function buildFixedSkeleton(
  startLms: Landmark[],
  startW: THREE.Vector3[],
  endW: THREE.Vector3[],
  phase: number,
  ankleAnchors: AnkleAnchors,
  zScale = 1,
): THREE.Vector3[] {
  // Default positions for landmarks the skeleton rebuild doesn't touch
  // (eyes, ears, mouth, fingers): a simple position-lerp between endpoints.
  // Every landmark used by `Mannequin` gets overwritten below.
  const out: THREE.Vector3[] = startW.map((s, i) => s.clone().lerp(endW[i], phase))

  const dirOf = (from: THREE.Vector3, to: THREE.Vector3): THREE.Vector3 => {
    const d = new THREE.Vector3().subVectors(to, from)
    if (d.lengthSq() < 1e-10) return UP.clone()
    return d.normalize()
  }
  const placeAt = (parentNew: THREE.Vector3, dir: THREE.Vector3, len: number): THREE.Vector3 =>
    parentNew.clone().add(dir.clone().multiplyScalar(len))
  // SLERPed bone orientation for a given (from, to) landmark pair.
  const boneDir = (a: number, b: number): THREE.Vector3 =>
    slerpDir(dirOf(startW[a], startW[b]), dirOf(endW[a], endW[b]), phase)

  // Root: mid-hip at the origin.
  const midHipR = new THREE.Vector3(0, 0, 0)

  // Hips: mirror pair around mid-hip along the SLERPed hip-line direction.
  const hipLineDir = boneDir(R_HIP, L_HIP)
  out[L_HIP] = placeAt(midHipR, hipLineDir, ANTHRO.hipHalf * H)
  out[R_HIP] = placeAt(midHipR, hipLineDir.clone().negate(), ANTHRO.hipHalf * H)

  // Floor plane: lower of the two START-pose ankle y's. Anchors are always
  // pinned to this Y so the floor doesn't slide as the animation progresses.
  const floorY = Math.min(startW[L_ANKLE].y, startW[R_ANKLE].y)

  // Median ankle anchors from the whole pose file, transformed to skeleton
  // world-space using the START pose's hip midpoint (the same centering
  // `toWorldLandmarks` applied). Fall back to start-pose ankle when no
  // anchor is available (low-visibility fixture).
  const startHipMidX = (startLms[L_HIP].x + startLms[R_HIP].x) / 2
  const startHipMidZ = ((startLms[L_HIP].z ?? 0) + (startLms[R_HIP].z ?? 0)) / 2
  const anchorToWorld = (a: { x: number; y: number; z: number }): THREE.Vector3 =>
    new THREE.Vector3(
      (a.x - startHipMidX) * H,
      floorY,
      -(a.z - startHipMidZ) * H * zScale,
    )
  // Start-phase hip positions needed for ankle clamping (must come before frozen ankles).
  const startHipLineDirRaw = dirOf(startW[R_HIP], startW[L_HIP])
  const startHipL = placeAt(midHipR, startHipLineDirRaw,                 ANTHRO.hipHalf * H)
  const startHipR = placeAt(midHipR, startHipLineDirRaw.clone().negate(), ANTHRO.hipHalf * H)

  // Clamp the ankle's horizontal distance from its hip joint to prevent
  // noisy MediaPipe depth values from placing the foot outside leg reach,
  // which would force the IK to produce a near-horizontal thigh.
  const MAX_HORIZ = 0.25 * H
  const clampToHip = (ankle: THREE.Vector3, hip: THREE.Vector3): THREE.Vector3 => {
    const dx = ankle.x - hip.x
    const dz = ankle.z - hip.z
    const horiz = Math.sqrt(dx * dx + dz * dz)
    if (horiz <= MAX_HORIZ) return ankle
    return new THREE.Vector3(hip.x + dx * MAX_HORIZ / horiz, ankle.y, hip.z + dz * MAX_HORIZ / horiz)
  }

  const ankleL_raw = ankleAnchors
    ? anchorToWorld(ankleAnchors.L)
    : new THREE.Vector3(startW[L_ANKLE].x, floorY, startW[L_ANKLE].z)
  const ankleR_raw = ankleAnchors
    ? anchorToWorld(ankleAnchors.R)
    : new THREE.Vector3(startW[R_ANKLE].x, floorY, startW[R_ANKLE].z)
  const ankleL_frozen = clampToHip(ankleL_raw, startHipL)
  const ankleR_frozen = clampToHip(ankleR_raw, startHipR)

  // Endpoint hip positions (phase=1) used to derive per-leg knee perpendiculars.
  const endHipLineDirRaw = dirOf(endW[R_HIP], endW[L_HIP])
  const endHipL   = placeAt(midHipR, endHipLineDirRaw,                         ANTHRO.hipHalf * H)
  const endHipR   = placeAt(midHipR, endHipLineDirRaw.clone().negate(),        ANTHRO.hipHalf * H)

  // Per-leg knee-bend perpendicular, SLERPed between endpoints.
  const perpL = slerpDir(
    kneePerpAt(startHipL, ankleL_frozen, startW[L_KNEE]),
    kneePerpAt(endHipL,   ankleL_frozen, endW[L_KNEE]),
    phase,
  )
  const perpR = slerpDir(
    kneePerpAt(startHipR, ankleR_frozen, startW[R_KNEE]),
    kneePerpAt(endHipR,   ankleR_frozen, endW[R_KNEE]),
    phase,
  )

  // Per-leg toe direction in the floor plane, SLERPed between endpoints.
  const toeDirOf = (lms: THREE.Vector3[], ankleI: number, footI: number): THREE.Vector3 => {
    const raw = new THREE.Vector3(lms[footI].x - lms[ankleI].x, 0, lms[footI].z - lms[ankleI].z)
    return raw.lengthSq() > 1e-10 ? raw.normalize() : new THREE.Vector3(0, 0, 1)
  }
  const toeDirL = slerpDir(toeDirOf(startW, L_ANKLE, L_FOOT), toeDirOf(endW, L_ANKLE, L_FOOT), phase)
  const toeDirR = slerpDir(toeDirOf(startW, R_ANKLE, R_FOOT), toeDirOf(endW, R_ANKLE, R_FOOT), phase)

  // 2-link IK from moving hip to frozen ankle. Ankle clamped to max reach
  // in the rare case hip drift would overshoot it.
  const placeLeg = (
    h: THREE.Vector3,
    ankle: THREE.Vector3,
    perp: THREE.Vector3,
    toeDir: THREE.Vector3,
  ): { knee: THREE.Vector3; ankle: THREE.Vector3; foot: THREE.Vector3 } => {
    const l1 = ANTHRO.thigh * H
    const l2 = ANTHRO.shin  * H
    let a = ankle.clone()
    let delta = a.clone().sub(h)
    let d = delta.length()
    const reach = (l1 + l2) * 0.999
    if (d > reach) {
      a = h.clone().add(delta.setLength(reach))
      delta = a.clone().sub(h)
      d = reach
    }
    const along = delta.clone().divideScalar(Math.max(d, 1e-6))
    const cosC = Math.max(-1, Math.min(1, (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)))
    const sinC = Math.sqrt(1 - cosC * cosC)
    const knee = h.clone()
      .add(along.clone().multiplyScalar(l1 * cosC))
      .add(perp.clone().multiplyScalar(l1 * sinC))
    const foot = a.clone().add(toeDir.clone().multiplyScalar(ANTHRO.foot * H))
    return { knee, ankle: a, foot }
  }

  const legL = placeLeg(out[L_HIP], ankleL_frozen, perpL, toeDirL)
  const legR = placeLeg(out[R_HIP], ankleR_frozen, perpR, toeDirR)
  out[L_KNEE]  = legL.knee;  out[L_ANKLE] = legL.ankle; out[L_FOOT] = legL.foot
  out[R_KNEE]  = legR.knee;  out[R_ANKLE] = legR.ankle; out[R_FOOT] = legR.foot

  // Spine: mid-hip → mid-shoulder along the SLERPed spine direction.
  const midShStart  = startW[L_SHOULDER].clone().add(startW[R_SHOULDER]).multiplyScalar(0.5)
  const midShEnd    = endW[L_SHOULDER].clone().add(endW[R_SHOULDER]).multiplyScalar(0.5)
  const midHipStart = startW[L_HIP].clone().add(startW[R_HIP]).multiplyScalar(0.5)
  const midHipEnd   = endW[L_HIP].clone().add(endW[R_HIP]).multiplyScalar(0.5)
  const spineDir = slerpDir(
    dirOf(midHipStart, midShStart),
    dirOf(midHipEnd,   midShEnd),
    phase,
  )
  const midShR = placeAt(midHipR, spineDir, ANTHRO.spine * H)

  // Shoulders: mirror pair around rebuilt mid-shoulder along SLERPed line dir.
  const shLineDir = boneDir(R_SHOULDER, L_SHOULDER)
  out[L_SHOULDER] = placeAt(midShR, shLineDir, ANTHRO.shoulderHalf * H)
  out[R_SHOULDER] = placeAt(midShR, shLineDir.clone().negate(), ANTHRO.shoulderHalf * H)

  // Arms: shoulder → elbow → wrist, SLERPed.
  out[L_ELBOW] = placeAt(out[L_SHOULDER], boneDir(L_SHOULDER, L_ELBOW), ANTHRO.upperArm * H)
  out[R_ELBOW] = placeAt(out[R_SHOULDER], boneDir(R_SHOULDER, R_ELBOW), ANTHRO.upperArm * H)
  out[L_WRIST] = placeAt(out[L_ELBOW],    boneDir(L_ELBOW,    L_WRIST), ANTHRO.forearm * H)
  out[R_WRIST] = placeAt(out[R_ELBOW],    boneDir(R_ELBOW,    R_WRIST), ANTHRO.forearm * H)

  // Head nose landmark.
  out[NOSE] = placeAt(midShR, spineDir, (ANTHRO.neck + ANTHRO.headR) * H)

  return out
}

function Capsule({
  from, to, radius, color = COLOR_BODY,
}: { from: THREE.Vector3; to: THREE.Vector3; radius: number; color?: string }) {
  const mid = new THREE.Vector3().addVectors(from, to).multiplyScalar(0.5)
  const delta = new THREE.Vector3().subVectors(to, from)
  // Capsule length is the cylinder segment only; hemispherical caps add 2*radius
  // to the total end-to-end distance, so subtract them from the measured bone.
  const length = Math.max(delta.length() - radius * 2, 0.01)
  const dir = delta.lengthSq() > 1e-10 ? delta.clone().normalize() : UP
  const quat = new THREE.Quaternion().setFromUnitVectors(UP, dir)
  return (
    <mesh position={mid} quaternion={quat} castShadow receiveShadow>
      <capsuleGeometry args={[radius, length, 4, 12]} />
      <meshStandardMaterial color={color} roughness={ROUGHNESS} metalness={0} />
    </mesh>
  )
}

function Joint({ at, radius, color = COLOR_JOINT }: { at: THREE.Vector3; radius: number; color?: string }) {
  return (
    <mesh position={at} castShadow receiveShadow>
      <sphereGeometry args={[radius, 20, 16]} />
      <meshStandardMaterial color={color} roughness={ROUGHNESS} metalness={0} />
    </mesh>
  )
}

function Mannequin({ v }: { v: THREE.Vector3[] }) {
  const headR = ANTHRO.headR * H
  const neckLen = ANTHRO.neck * H

  // Rebuilt mids come straight from the fixed skeleton.
  const midSh = v[L_SHOULDER].clone().add(v[R_SHOULDER]).multiplyScalar(0.5)
  const midHip = v[L_HIP].clone().add(v[R_HIP]).multiplyScalar(0.5)
  const spineUp = midSh.clone().sub(midHip).normalize()

  const neckTop = midSh.clone().add(spineUp.clone().multiplyScalar(neckLen))
  const headCenter = neckTop.clone().add(spineUp.clone().multiplyScalar(headR))

  return (
    <group>
      {/* Neck + head. */}
      <Capsule from={midSh} to={neckTop} radius={RADIUS.neck * H} />
      <Joint at={headCenter} radius={headR} color={COLOR_BODY} />

      {/* Spine + chest/pelvis bars. */}
      <Capsule from={midSh} to={midHip} radius={RADIUS.spine * H} />
      <Capsule from={v[L_SHOULDER]} to={v[R_SHOULDER]} radius={RADIUS.chestBar * H} />
      <Capsule from={v[L_HIP]} to={v[R_HIP]} radius={RADIUS.pelvisBar * H} />

      {/* Arm joints. */}
      <Joint at={v[L_SHOULDER]} radius={RADIUS.shoulder * H} />
      <Joint at={v[R_SHOULDER]} radius={RADIUS.shoulder * H} />
      <Joint at={v[L_ELBOW]}    radius={RADIUS.elbow * H}    />
      <Joint at={v[R_ELBOW]}    radius={RADIUS.elbow * H}    />
      <Joint at={v[L_WRIST]}    radius={RADIUS.wrist * H}    />
      <Joint at={v[R_WRIST]}    radius={RADIUS.wrist * H}    />

      {/* Arm bones. */}
      <Capsule from={v[L_SHOULDER]} to={v[L_ELBOW]} radius={RADIUS.upperArm * H} />
      <Capsule from={v[R_SHOULDER]} to={v[R_ELBOW]} radius={RADIUS.upperArm * H} />
      <Capsule from={v[L_ELBOW]}    to={v[L_WRIST]} radius={RADIUS.forearm * H}  />
      <Capsule from={v[R_ELBOW]}    to={v[R_WRIST]} radius={RADIUS.forearm * H}  />

      {/* Leg joints. */}
      <Joint at={v[L_HIP]}   radius={RADIUS.hip * H}   />
      <Joint at={v[R_HIP]}   radius={RADIUS.hip * H}   />
      <Joint at={v[L_KNEE]}  radius={RADIUS.knee * H}  />
      <Joint at={v[R_KNEE]}  radius={RADIUS.knee * H}  />
      <Joint at={v[L_ANKLE]} radius={RADIUS.ankle * H} />
      <Joint at={v[R_ANKLE]} radius={RADIUS.ankle * H} />

      {/* Leg bones + feet. */}
      <Capsule from={v[L_HIP]}   to={v[L_KNEE]}  radius={RADIUS.thigh * H} />
      <Capsule from={v[R_HIP]}   to={v[R_KNEE]}  radius={RADIUS.thigh * H} />
      <Capsule from={v[L_KNEE]}  to={v[L_ANKLE]} radius={RADIUS.shin * H}  />
      <Capsule from={v[R_KNEE]}  to={v[R_ANKLE]} radius={RADIUS.shin * H}  />
      <Capsule from={v[L_ANKLE]} to={v[L_FOOT]}  radius={RADIUS.foot * H}  />
      <Capsule from={v[R_ANKLE]} to={v[R_FOOT]}  radius={RADIUS.foot * H}  />
    </group>
  )
}

function Floor({ y }: { y: number }) {
  // Warm-matte base plus a faded grid overlay. The grid sits a hair above the
  // base so it wins the z-fight; fadeDistance hides the hard edge of the plane.
  return (
    <group>
      <mesh rotation-x={-Math.PI / 2} position-y={y} receiveShadow>
        <planeGeometry args={[12, 12]} />
        <meshStandardMaterial color="#2a2620" roughness={1} metalness={0} />
      </mesh>
      <Grid
        position={[0, y + 0.001, 0]}
        args={[12, 12]}
        cellSize={0.25}
        cellThickness={0.6}
        cellColor="#4a4238"
        sectionSize={1}
        sectionThickness={1.1}
        sectionColor="#6b5f4c"
        fadeDistance={6}
        fadeStrength={1}
        followCamera={false}
        infiniteGrid={false}
      />
    </group>
  )
}

interface Props {
  startLms: Landmark[]
  endLms: Landmark[]
  ankleAnchors?: AnkleAnchors
  phase: number
  width: number
  height: number
  zScale?: number
}

export default function Drill2Mannequin({ startLms, endLms, ankleAnchors = null, phase, width, height, zScale = 1 }: Props) {
  // Rebuild the skeleton once per frame with fixed proportions. Bone
  // orientations are SLERPed between the two endpoint poses, so the animation
  // traces arcs instead of chords through the body. Camera and ground derive
  // from the rebuilt ankle positions.
  const v = useMemo(() => {
    const startW = toWorldLandmarks(startLms, zScale)
    const endW   = toWorldLandmarks(endLms, zScale)
    return buildFixedSkeleton(startLms, startW, endW, phase, ankleAnchors, zScale)
  }, [startLms, endLms, ankleAnchors, phase, zScale])

  // Ground sits just under the lower rebuilt ankle.
  const groundY = useMemo(
    () => Math.min(v[L_ANKLE].y, v[R_ANKLE].y) - ANTHRO.foot * H * 0.5,
    [v],
  )

  // Orbit target = mid-body so horizontal drag pivots around the figure's
  // center rather than its feet.
  const targetY = useMemo(
    () => Math.min(v[L_ANKLE].y, v[R_ANKLE].y) + H * 0.5,
    [v],
  )

  return (
    <div
      style={{ width, height }}
      className="border border-gray-800 rounded bg-gray-900 overflow-hidden"
    >
      <Canvas shadows camera={{ position: [0, targetY, 4.5], fov: 32 }}>
        <color attach="background" args={['#0A0F1A']} />
        <ambientLight intensity={0.55} />
        <directionalLight
          position={[3, 4, 5]}
          intensity={0.9}
          castShadow
          shadow-mapSize-width={1024}
          shadow-mapSize-height={1024}
          shadow-camera-left={-2}
          shadow-camera-right={2}
          shadow-camera-top={2}
          shadow-camera-bottom={-2}
        />
        <Mannequin v={v} />
        <Floor y={groundY} />
        {/* Full 3D orbit around the body center. Pan/zoom stay off so slider
            edits don't shift the framing. */}
        <OrbitControls
          enablePan={false}
          enableRotate={true}
          enableZoom={false}
          target={[0, targetY, 0]}
          makeDefault
        />
      </Canvas>
    </div>
  )
}
