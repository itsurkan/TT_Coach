// 3D articulated mannequin view for Drill 2 — matte capsule-and-sphere doll
// rendered with react-three-fiber. Inspired by the qualitative-results figures
// in Dabral et al. "Learning 3D Human Pose from Structure and Motion"
// (ECCV 2018): wooden posing-doll aesthetic, one directional light.
//
// Bone lengths are taken from a fixed anthropometric table (Drillis & Contini
// 1966, adult mean) scaled by the reference pose's figure height, and applied
// as a `fit_standard_skeleton` rebuild each frame. The lerped MediaPipe pose
// only contributes joint *directions* — bone *lengths* never change, so the
// knee always sits at the midpoint of the leg, shoulder width is constant, etc.

import { Canvas } from '@react-three/fiber'
import { OrbitControls } from '@react-three/drei'
import { useMemo } from 'react'
import * as THREE from 'three'

type Landmark = { x: number; y: number; z?: number; visibility?: number }

const COLOR = '#ece8df'         // warm cream, matches the paper's mannequins
const ROUGHNESS = 0.7
const WORLD_HEIGHT = 1.7        // world-space multiplier for normalized landmarks

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

function toWorldLandmarks(lms: Landmark[]): THREE.Vector3[] {
  // Center on hip midpoint, flip Y (MediaPipe y grows down), flip Z (camera
  // convention: +Z toward viewer in three.js, while MediaPipe z is negative
  // toward camera).
  const hipMidX = (lms[L_HIP].x + lms[R_HIP].x) / 2
  const hipMidY = (lms[L_HIP].y + lms[R_HIP].y) / 2
  const hipMidZ = ((lms[L_HIP].z ?? 0) + (lms[R_HIP].z ?? 0)) / 2
  return lms.map(l =>
    new THREE.Vector3(
      (l.x - hipMidX) * WORLD_HEIGHT,
      -(l.y - hipMidY) * WORLD_HEIGHT,
      -((l.z ?? 0) - hipMidZ) * WORLD_HEIGHT,
    ),
  )
}

// Total figure height derived from a reference pose. Nose sits at ≈ 82% of
// body height in adult anthropometry, so H = (nose-to-ankle Y) / 0.82.
// Uses Y only — avoids the z-dominance issue we saw in this dataset.
function deriveFigureHeight(ref: THREE.Vector3[]): number {
  const topY = ref[NOSE].y
  const bottomY = Math.min(ref[L_ANKLE].y, ref[R_ANKLE].y)
  const span = Math.max(topY - bottomY, 1e-3)
  return span / 0.82
}

// Rebuild the skeleton with fixed bone lengths, preserving only the directions
// from the lerped pose. Ankle-rooted: both ankles stay pinned at the reference
// positions, legs build UPWARD (ankle → knee → hip), hips derive mid-hip, and
// the upper body builds from there. Feet therefore stay planted through the
// whole animation; hip height follows naturally from leg flex (crouch = hip
// drops, because the leg angles shorten the vertical reach of the chain).
//
// This is the `fit_standard_skeleton` idea from Dabral et al. ECCV 2018,
// re-rooted at the feet to satisfy a "feet don't slide" constraint.
function buildFixedSkeleton(
  lerped: THREE.Vector3[],
  H: number,
  pinnedAnkles: { L: THREE.Vector3; R: THREE.Vector3 },
): THREE.Vector3[] {
  const out: THREE.Vector3[] = lerped.map(v => v.clone())

  const dirOf = (from: THREE.Vector3, to: THREE.Vector3): THREE.Vector3 => {
    const d = new THREE.Vector3().subVectors(to, from)
    if (d.lengthSq() < 1e-10) return UP.clone()
    return d.normalize()
  }
  const placeAt = (parentNew: THREE.Vector3, dir: THREE.Vector3, len: number): THREE.Vector3 =>
    parentNew.clone().add(dir.clone().multiplyScalar(len))

  // Pinned ankles — anchor of the whole chain.
  out[L_ANKLE] = pinnedAnkles.L.clone()
  out[R_ANKLE] = pinnedAnkles.R.clone()

  // Legs, going UP from the pinned ankles.
  out[L_KNEE] = placeAt(out[L_ANKLE], dirOf(lerped[L_ANKLE], lerped[L_KNEE]), ANTHRO.shin * H)
  out[R_KNEE] = placeAt(out[R_ANKLE], dirOf(lerped[R_ANKLE], lerped[R_KNEE]), ANTHRO.shin * H)
  out[L_HIP]  = placeAt(out[L_KNEE],  dirOf(lerped[L_KNEE],  lerped[L_HIP]),  ANTHRO.thigh * H)
  out[R_HIP]  = placeAt(out[R_KNEE],  dirOf(lerped[R_KNEE],  lerped[R_HIP]),  ANTHRO.thigh * H)

  // Feet (toes): extend forward from the pinned ankles along the lerped
  // foot direction. Ankles never move; only toe direction changes.
  out[L_FOOT] = placeAt(out[L_ANKLE], dirOf(lerped[L_ANKLE], lerped[L_FOOT]), ANTHRO.foot * H)
  out[R_FOOT] = placeAt(out[R_ANKLE], dirOf(lerped[R_ANKLE], lerped[R_FOOT]), ANTHRO.foot * H)

  // Mid-hip from the two leg-chain hips, then re-place both hips symmetrically
  // around it so the pelvis bar stays at the anthropometric hip width (0.191·H)
  // no matter what the leg angles do. Thighs will render from these corrected
  // hips to the leg-chain knees, so thigh length may drift by a tiny amount
  // under very asymmetric stances — acceptable for the constant-torso goal.
  const midHipR = out[L_HIP].clone().add(out[R_HIP]).multiplyScalar(0.5)
  const hipLineDir = dirOf(lerped[R_HIP], lerped[L_HIP])
  out[L_HIP] = placeAt(midHipR, hipLineDir, ANTHRO.hipHalf * H)
  out[R_HIP] = placeAt(midHipR, hipLineDir.clone().negate(), ANTHRO.hipHalf * H)

  // Spine direction from the lerped pose (shoulders relative to hips).
  const midHipL = lerped[L_HIP].clone().add(lerped[R_HIP]).multiplyScalar(0.5)
  const midShL  = lerped[L_SHOULDER].clone().add(lerped[R_SHOULDER]).multiplyScalar(0.5)
  const spineDir = dirOf(midHipL, midShL)
  const midShR   = placeAt(midHipR, spineDir, ANTHRO.spine * H)

  // Shoulders: mirror pair around the rebuilt mid-shoulder, along the lerped
  // shoulder-line direction so chest width stays exactly 2 × shoulderHalf × H.
  const shLineDir = dirOf(lerped[R_SHOULDER], lerped[L_SHOULDER])
  out[L_SHOULDER] = placeAt(midShR, shLineDir, ANTHRO.shoulderHalf * H)
  out[R_SHOULDER] = placeAt(midShR, shLineDir.clone().negate(), ANTHRO.shoulderHalf * H)

  // Arms: shoulder → elbow → wrist.
  out[L_ELBOW] = placeAt(out[L_SHOULDER], dirOf(lerped[L_SHOULDER], lerped[L_ELBOW]), ANTHRO.upperArm * H)
  out[R_ELBOW] = placeAt(out[R_SHOULDER], dirOf(lerped[R_SHOULDER], lerped[R_ELBOW]), ANTHRO.upperArm * H)
  out[L_WRIST] = placeAt(out[L_ELBOW],    dirOf(lerped[L_ELBOW],    lerped[L_WRIST]), ANTHRO.forearm * H)
  out[R_WRIST] = placeAt(out[R_ELBOW],    dirOf(lerped[R_ELBOW],    lerped[R_WRIST]), ANTHRO.forearm * H)

  // Head nose landmark (kept in sync for any downstream consumer).
  out[NOSE] = placeAt(midShR, spineDir, (ANTHRO.neck + ANTHRO.headR) * H)

  return out
}

function Capsule({
  from, to, radius,
}: { from: THREE.Vector3; to: THREE.Vector3; radius: number }) {
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
      <meshStandardMaterial color={COLOR} roughness={ROUGHNESS} metalness={0} />
    </mesh>
  )
}

function Joint({ at, radius }: { at: THREE.Vector3; radius: number }) {
  return (
    <mesh position={at} castShadow receiveShadow>
      <sphereGeometry args={[radius, 20, 16]} />
      <meshStandardMaterial color={COLOR} roughness={ROUGHNESS} metalness={0} />
    </mesh>
  )
}

function Mannequin({
  lms, H, pinnedAnkles,
}: {
  lms: Landmark[]
  H: number
  pinnedAnkles: { L: THREE.Vector3; R: THREE.Vector3 }
}) {
  // Convert to world, then rebuild with fixed anthropometric lengths. Feet
  // stay pinned at reference positions so the mannequin doesn't slide.
  const v = useMemo(
    () => buildFixedSkeleton(toWorldLandmarks(lms), H, pinnedAnkles),
    [lms, H, pinnedAnkles],
  )

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
      <Joint at={headCenter} radius={headR} />

      {/* Spine. */}
      <Capsule from={midSh} to={midHip} radius={RADIUS.spine * H} />

      {/* Chest bar — shoulder line. */}
      <Capsule from={v[L_SHOULDER]} to={v[R_SHOULDER]} radius={RADIUS.chestBar * H} />

      {/* Pelvis bar — hip line. */}
      <Capsule from={v[L_HIP]} to={v[R_HIP]} radius={RADIUS.pelvisBar * H} />

      {/* Shoulder joints */}
      <Joint at={v[L_SHOULDER]} radius={RADIUS.shoulder * H} />
      <Joint at={v[R_SHOULDER]} radius={RADIUS.shoulder * H} />

      {/* Upper arms */}
      <Capsule from={v[L_SHOULDER]} to={v[L_ELBOW]} radius={RADIUS.upperArm * H} />
      <Capsule from={v[R_SHOULDER]} to={v[R_ELBOW]} radius={RADIUS.upperArm * H} />

      {/* Elbow joints */}
      <Joint at={v[L_ELBOW]} radius={RADIUS.elbow * H} />
      <Joint at={v[R_ELBOW]} radius={RADIUS.elbow * H} />

      {/* Forearms */}
      <Capsule from={v[L_ELBOW]} to={v[L_WRIST]} radius={RADIUS.forearm * H} />
      <Capsule from={v[R_ELBOW]} to={v[R_WRIST]} radius={RADIUS.forearm * H} />

      {/* Hands */}
      <Joint at={v[L_WRIST]} radius={RADIUS.wrist * H} />
      <Joint at={v[R_WRIST]} radius={RADIUS.wrist * H} />

      {/* Hip joints */}
      <Joint at={v[L_HIP]} radius={RADIUS.hip * H} />
      <Joint at={v[R_HIP]} radius={RADIUS.hip * H} />

      {/* Thighs */}
      <Capsule from={v[L_HIP]} to={v[L_KNEE]} radius={RADIUS.thigh * H} />
      <Capsule from={v[R_HIP]} to={v[R_KNEE]} radius={RADIUS.thigh * H} />

      {/* Knee joints */}
      <Joint at={v[L_KNEE]} radius={RADIUS.knee * H} />
      <Joint at={v[R_KNEE]} radius={RADIUS.knee * H} />

      {/* Shins */}
      <Capsule from={v[L_KNEE]} to={v[L_ANKLE]} radius={RADIUS.shin * H} />
      <Capsule from={v[R_KNEE]} to={v[R_ANKLE]} radius={RADIUS.shin * H} />

      {/* Ankle joints */}
      <Joint at={v[L_ANKLE]} radius={RADIUS.ankle * H} />
      <Joint at={v[R_ANKLE]} radius={RADIUS.ankle * H} />

      {/* Feet: ankle → foot-index. */}
      <Capsule from={v[L_ANKLE]} to={v[L_FOOT]} radius={RADIUS.foot * H} />
      <Capsule from={v[R_ANKLE]} to={v[R_FOOT]} radius={RADIUS.foot * H} />
    </group>
  )
}

function Ground({ y }: { y: number }) {
  return (
    <mesh rotation-x={-Math.PI / 2} position-y={y} receiveShadow>
      <planeGeometry args={[8, 8]} />
      <meshStandardMaterial color="#1f2937" roughness={1} metalness={0} />
    </mesh>
  )
}

interface Props {
  lms: Landmark[]
  /** Stable reference pose used for figure-height calibration. */
  referenceLms: Landmark[]
  width: number
  height: number
}

export default function Drill2Mannequin({ lms, referenceLms, width, height }: Props) {
  // Figure height from the reference pose, used as the H multiplier for every
  // anthropometric bone length. Computed once per reference identity.
  const H = useMemo(() => {
    return deriveFigureHeight(toWorldLandmarks(referenceLms))
  }, [referenceLms])

  // Ankle positions from the reference pose, pinned for the whole animation
  // so the feet never slide. All other joints build upward from here.
  const pinnedAnkles = useMemo(() => {
    const r = toWorldLandmarks(referenceLms)
    return { L: r[L_ANKLE].clone(), R: r[R_ANKLE].clone() }
  }, [referenceLms])

  // Ground Y: slightly below the pinned ankles so the floor sits right under
  // the feet and stays put through the animation.
  const groundY = useMemo(() => {
    return Math.min(pinnedAnkles.L.y, pinnedAnkles.R.y) - ANTHRO.foot * H * 0.5
  }, [pinnedAnkles, H])

  // Orbit target = body mid-height (mid-way between ankles and head), so
  // horizontal drag spins the figure around its own center instead of around
  // the feet.
  const targetY = useMemo(() => {
    return Math.min(pinnedAnkles.L.y, pinnedAnkles.R.y) + H * 0.5
  }, [pinnedAnkles, H])

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
        <Mannequin lms={lms} H={H} pinnedAnkles={pinnedAnkles} />
        <Ground y={groundY} />
        {/* Horizontal drag orbits the camera around the body center.
            Polar angle is locked so vertical drag can't tilt the view;
            pan/zoom stay off so slider edits don't shift the framing. */}
        <OrbitControls
          enablePan={false}
          enableRotate={true}
          enableZoom={false}
          target={[0, targetY, 0]}
          minPolarAngle={Math.PI / 2}
          maxPolarAngle={Math.PI / 2}
          makeDefault
        />
      </Canvas>
    </div>
  )
}
