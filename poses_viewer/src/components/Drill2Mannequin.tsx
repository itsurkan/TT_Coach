// 3D articulated mannequin view for Drill 2 — matte capsule-and-sphere doll
// rendered with react-three-fiber. Intended to match the look of the
// qualitative-results figures in Dabral et al. "Learning 3D Human Pose from
// Structure and Motion" (ECCV 2018): wooden posing-doll aesthetic, matte
// plastic material, one directional light.
//
// Input: the current (already-blended) 33 MediaPipe landmarks. Rendering
// is stateless; every landmark change re-positions/rotates the meshes.
// Animation is driven from the parent (Drill2Preview updates lms each tick).

import { Canvas } from '@react-three/fiber'
import { OrbitControls } from '@react-three/drei'
import { useMemo } from 'react'
import * as THREE from 'three'

type Landmark = { x: number; y: number; z?: number; visibility?: number }

const COLOR = '#ece8df'         // warm cream, matches the paper's mannequins
const ROUGHNESS = 0.7
const WORLD_HEIGHT = 1.7        // figure height in world units

// MediaPipe indices we consume.
const L_SHOULDER = 11, R_SHOULDER = 12
const L_ELBOW = 13, R_ELBOW = 14
const L_WRIST = 15, R_WRIST = 16
const L_HIP = 23, R_HIP = 24
const L_KNEE = 25, R_KNEE = 26
const L_ANKLE = 27, R_ANKLE = 28
const L_FOOT = 31, R_FOOT = 32

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

function Mannequin({ lms, headR }: { lms: Landmark[]; headR: number }) {
  const v = useMemo(() => toWorldLandmarks(lms), [lms])

  // Torso axes derived from the current pose so the head/neck follow spine
  // orientation. Only the *positions* come from the live pose; the head
  // RADIUS is passed in fixed from the parent so it doesn't pulse with the
  // inter-frame shoulder-width variance.
  const midSh = v[L_SHOULDER].clone().add(v[R_SHOULDER]).multiplyScalar(0.5)
  const midHip = v[L_HIP].clone().add(v[R_HIP]).multiplyScalar(0.5)
  const spineUp = midSh.clone().sub(midHip).normalize()

  // Neck runs from the shoulder-midpoint up along the spine direction.
  // Length ≈ one head-radius so the head visibly sits above the shoulders
  // rather than appearing glued on.
  const neckLength = headR
  const neckTop = midSh.clone().add(spineUp.clone().multiplyScalar(neckLength))
  const headCenter = neckTop.clone().add(spineUp.clone().multiplyScalar(headR))

  return (
    <group>
      {/* Head & neck — neck is a visible capsule from shoulder-mid up to the
          base of the head; head is a sphere sitting atop. */}
      <Capsule from={midSh} to={neckTop} radius={0.045} />
      <Joint at={headCenter} radius={headR} />

      {/* Spine — single capsule from shoulder-mid to hip-mid. */}
      <Capsule from={midSh} to={midHip} radius={0.085} />

      {/* Chest bar — the shoulder line. Rotates with the upper torso, so its
          orientation changes as the shoulders twist through the stroke. */}
      <Capsule from={v[L_SHOULDER]} to={v[R_SHOULDER]} radius={0.058} />

      {/* Pelvis bar — the hip line. Rotates with the lower torso. When the
          chest bar and pelvis bar aren't parallel, you're seeing torso twist. */}
      <Capsule from={v[L_HIP]} to={v[R_HIP]} radius={0.065} />

      {/* Shoulder joints */}
      <Joint at={v[L_SHOULDER]} radius={0.058} />
      <Joint at={v[R_SHOULDER]} radius={0.058} />

      {/* Upper arms */}
      <Capsule from={v[L_SHOULDER]} to={v[L_ELBOW]} radius={0.048} />
      <Capsule from={v[R_SHOULDER]} to={v[R_ELBOW]} radius={0.048} />

      {/* Elbow joints */}
      <Joint at={v[L_ELBOW]} radius={0.042} />
      <Joint at={v[R_ELBOW]} radius={0.042} />

      {/* Forearms */}
      <Capsule from={v[L_ELBOW]} to={v[L_WRIST]} radius={0.040} />
      <Capsule from={v[R_ELBOW]} to={v[R_WRIST]} radius={0.040} />

      {/* Hands */}
      <Joint at={v[L_WRIST]} radius={0.045} />
      <Joint at={v[R_WRIST]} radius={0.045} />

      {/* Hip joints */}
      <Joint at={v[L_HIP]} radius={0.065} />
      <Joint at={v[R_HIP]} radius={0.065} />

      {/* Thighs */}
      <Capsule from={v[L_HIP]} to={v[L_KNEE]} radius={0.060} />
      <Capsule from={v[R_HIP]} to={v[R_KNEE]} radius={0.060} />

      {/* Knee joints */}
      <Joint at={v[L_KNEE]} radius={0.052} />
      <Joint at={v[R_KNEE]} radius={0.052} />

      {/* Shins */}
      <Capsule from={v[L_KNEE]} to={v[L_ANKLE]} radius={0.048} />
      <Capsule from={v[R_KNEE]} to={v[R_ANKLE]} radius={0.048} />

      {/* Ankle joints */}
      <Joint at={v[L_ANKLE]} radius={0.042} />
      <Joint at={v[R_ANKLE]} radius={0.042} />

      {/* Feet: short capsule from ankle to foot_index. */}
      <Capsule from={v[L_ANKLE]} to={v[L_FOOT]} radius={0.035} />
      <Capsule from={v[R_ANKLE]} to={v[R_FOOT]} radius={0.035} />
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
  /** Stable reference pose used for proportions that shouldn't pulse with
   *  per-frame variance (e.g. head radius). Typically the start keyframe. */
  referenceLms: Landmark[]
  width: number
  height: number
}

export default function Drill2Mannequin({ lms, referenceLms, width, height }: Props) {
  // Head radius derived from the reference pose's shoulder width. We measure
  // in the x-y plane only — MediaPipe's z range can be 2-3× larger than x/y
  // for poses with significant depth, and using full 3D distance here makes
  // the head disproportionately large.
  const headR = useMemo(() => {
    const r = toWorldLandmarks(referenceLms)
    const dx = r[L_SHOULDER].x - r[R_SHOULDER].x
    const dy = r[L_SHOULDER].y - r[R_SHOULDER].y
    const shoulderWidthXY = Math.hypot(dx, dy)
    return Math.max(0.08, shoulderWidthXY * 0.38)
  }, [referenceLms])

  // Floor height from the lowest ankle/foot of the reference pose (also stable
  // across the loop — we don't want the ground bobbing either).
  const groundY = useMemo(() => {
    const r = toWorldLandmarks(referenceLms)
    return Math.min(
      r[L_ANKLE].y, r[R_ANKLE].y, r[L_FOOT].y, r[R_FOOT].y,
    ) - 0.03
  }, [referenceLms])

  return (
    <div
      style={{ width, height }}
      className="border border-gray-800 rounded bg-gray-900 overflow-hidden"
    >
      <Canvas shadows camera={{ position: [0, 0.15, 4.5], fov: 32 }}>
        <color attach="background" args={['#0b0f19']} />
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
        <Mannequin lms={lms} headR={headR} />
        <Ground y={groundY} />
        <OrbitControls
          enablePan={false}
          minDistance={1.5}
          maxDistance={10}
          target={[0, 0, 0]}
        />
      </Canvas>
    </div>
  )
}
