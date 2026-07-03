import { useEffect, useMemo, useRef, useState } from 'react'
import { Canvas } from '@react-three/fiber'
import { OrbitControls, Grid, Line } from '@react-three/drei'
import * as THREE from 'three'

/**
 * #/pose3d — visualize a temporally-lifted 3D skeleton (MotionAGFormer over RTMPose 2D).
 *
 * Loads the self-describing `<base>_pose3d_lift.json` produced by
 * scripts/poses/lift_pose3d.py (topology "h36m17"; embeds `joints` + `bones`, so this
 * page is topology-agnostic). Throwaway visual experiment — eyeball whether temporal
 * lifting reads as 3D on table-tennis footage. The lift's model-world axes are
 * (+x right, +y depth, +z up); three.js is y-up, so we map (x, z, -y).
 */

interface Joint3D { index: number; x: number; y: number; z: number }
interface Pose3DFrame { frameIndex: number; timestampMs: number; detected: boolean; joints3d: Joint3D[] }
interface Pose3DDoc {
  schemaVersion: string
  topology: string
  model: string
  source?: string
  videoName: string
  intervalMs: number
  totalFrames: number
  joints: string[]
  bones: number[][]
  frames: Pose3DFrame[]
}

const SCALE = 1
// H36M joint sides for colouring (standard H36M order: 1-3 right leg, 4-6 left leg,
// 11-13 left arm, 14-16 right arm — matches scripts/poses/lift_pose3d.py H36M_JOINTS).
const LEFT = new Set([4, 5, 6, 11, 12, 13])
const RIGHT = new Set([1, 2, 3, 14, 15, 16])

type Source = 'rtm' | 'vision' | 'mediapipe'
const SOURCES: { id: Source; label: string }[] = [
  { id: 'rtm', label: 'RTM' },
  { id: 'vision', label: 'Vision' },
  { id: 'mediapipe', label: 'MediaPipe' },
]
const C_LEFT = '#38bdf8'   // cyan
const C_RIGHT = '#f87171'  // red
const C_CENTER = '#9ca3af' // gray

function sideColor(jointIdx: number): string {
  if (LEFT.has(jointIdx)) return C_LEFT
  if (RIGHT.has(jointIdx)) return C_RIGHT
  return C_CENTER
}

/** Map a lifted joint (model-world: +z up) into a three.js y-up Vector3. */
function toVec(j: Joint3D): THREE.Vector3 {
  return new THREE.Vector3(j.x * SCALE, j.z * SCALE, -j.y * SCALE)
}

// Global fit over the whole sequence so the skeleton is always fully framed (independent of
// zoom): bounding box of every joint across all detected frames -> centre, height, radius.
interface Fit { cx: number; cz: number; minY: number; height: number; radius: number }
function computeFit(frames: Pose3DFrame[]): Fit | null {
  let mnx = Infinity, mny = Infinity, mnz = Infinity, mxx = -Infinity, mxy = -Infinity, mxz = -Infinity
  for (const fr of frames) {
    if (!fr.detected) continue
    for (const j of fr.joints3d) {
      const x = j.x * SCALE, y = j.z * SCALE, z = -j.y * SCALE // toVec mapping
      if (x < mnx) mnx = x; if (x > mxx) mxx = x
      if (y < mny) mny = y; if (y > mxy) mxy = y
      if (z < mnz) mnz = z; if (z > mxz) mxz = z
    }
  }
  if (!isFinite(mnx)) return null
  return {
    cx: (mnx + mxx) / 2,
    cz: (mnz + mxz) / 2,
    minY: mny,
    height: mxy - mny,
    radius: 0.5 * Math.hypot(mxx - mnx, mxy - mny, mxz - mnz),
  }
}

function Skeleton({ frame, bones }: { frame: Pose3DFrame; bones: number[][] }) {
  const pts = useMemo(() => frame.joints3d.map(toVec), [frame])

  if (!frame.detected) return null

  return (
    <group>
      {bones.map(([a, b], i) => (
        <Line
          key={i}
          points={[pts[a], pts[b]]}
          color={sideColor(b)}
          lineWidth={3}
        />
      ))}
      {pts.map((p, i) => (
        <mesh key={i} position={p}>
          <sphereGeometry args={[0.025, 12, 12]} />
          <meshStandardMaterial color={sideColor(i)} emissive={sideColor(i)} emissiveIntensity={0.35} />
        </mesh>
      ))}
    </group>
  )
}

export default function Pose3DPage({ onClose }: { onClose: () => void }) {
  const [videoList, setVideoList] = useState<{ name: string; ext: string }[]>([])
  const [base, setBase] = useState<string>('')
  const [source, setSource] = useState<Source>('rtm')
  const [doc, setDoc] = useState<Pose3DDoc | null>(null)
  const [status, setStatus] = useState<string>('')
  const [frameIdx, setFrameIdx] = useState(0)
  const [playing, setPlaying] = useState(true)
  const timer = useRef<number | null>(null)

  // Video list for the picker.
  useEffect(() => {
    fetch('/api/videos').then(r => (r.ok ? r.json() : [])).then(setVideoList).catch(() => {})
  }, [])

  // Load the lift JSON when the base or source changes.
  useEffect(() => {
    if (!base) return
    const inputName = source === 'mediapipe' ? `${base}_poses.json` : `${base}_poses_${source}.json`
    setDoc(null)
    setStatus('Loading…')
    setFrameIdx(0)
    fetch(`/videos/${base}/${base}_pose3d_lift_${source}.json`)
      .then(r => {
        if (!r.ok) throw new Error(`${r.status}`)
        return r.json()
      })
      .then((d: Pose3DDoc) => { setDoc(d); setStatus('') })
      .catch(() => {
        setStatus(`No ${source} lift for "${base}". Run:  .venv-lift/bin/python scripts/poses/lift_pose3d.py Videos/${base}/${inputName}`)
      })
  }, [base, source])

  // Playback loop.
  useEffect(() => {
    if (timer.current) { clearInterval(timer.current); timer.current = null }
    if (!doc || !playing) return
    const interval = Math.max(16, doc.intervalMs || 33)
    timer.current = window.setInterval(() => {
      setFrameIdx(i => (i + 1) % doc.frames.length)
    }, interval)
    return () => { if (timer.current) clearInterval(timer.current) }
  }, [doc, playing])

  // Keyboard control: Space = play/pause, ←/→ = step one frame (and pause).
  useEffect(() => {
    if (!doc) return
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName
      if (tag === 'SELECT' || tag === 'INPUT' || tag === 'TEXTAREA') return
      if (e.code === 'Space') {
        e.preventDefault()
        setPlaying(p => !p)
      } else if (e.code === 'ArrowRight') {
        e.preventDefault()
        setPlaying(false)
        setFrameIdx(i => Math.min(i + 1, doc.frames.length - 1))
      } else if (e.code === 'ArrowLeft') {
        e.preventDefault()
        setPlaying(false)
        setFrameIdx(i => Math.max(i - 1, 0))
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [doc])

  // Wheel/trackpad scrubbing over the timeline: scroll to jump through frames (shift = ×10).
  const onWheelScrub = (e: React.WheelEvent) => {
    if (!doc) return
    const raw = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY
    if (raw === 0) return
    const step = (raw > 0 ? 1 : -1) * (e.shiftKey ? 10 : 1)
    setPlaying(false)
    setFrameIdx(i => Math.max(0, Math.min(doc.frames.length - 1, i + step)))
  }

  const frame = doc?.frames[Math.min(frameIdx, (doc.frames.length - 1))] ?? null

  // Auto-fit camera to the whole sequence so the skeleton is always fully visible (no zoom needed).
  const fit = useMemo(() => (doc ? computeFit(doc.frames) : null), [doc])
  const FOV = 45
  const dist = fit ? (fit.radius / Math.tan((FOV / 2) * Math.PI / 180)) * 1.12 : 4
  const targetY = fit ? fit.height / 2 : 0.9
  const camPos = useMemo<[number, number, number]>(() => {
    const d = [0.55, 0.32, 0.77]
    const dl = Math.hypot(d[0], d[1], d[2])
    return fit ? [(d[0] / dl) * dist, targetY + (d[1] / dl) * dist, (d[2] / dl) * dist] : [2.4, 1.6, 2.8]
  }, [fit, dist, targetY])
  const fitOffset: [number, number, number] = fit ? [-fit.cx, -fit.minY, -fit.cz] : [0, 0, 0]
  // Remount the canvas when the loaded clip changes so the camera re-fits to it.
  const canvasKey = doc ? `${doc.source ?? ''}:${doc.totalFrames}` : 'empty'

  return (
    <div className="h-screen bg-gray-950 text-gray-100 flex flex-col select-none overflow-hidden">
      <header className="border-b border-gray-800 px-4 py-2.5 flex items-center gap-4 shrink-0">
        <button className="bg-gray-800 hover:bg-gray-700 px-3 py-1.5 rounded text-sm" onClick={onClose}>← Back</button>
        <h1 className="font-semibold text-white">3D Lift <span className="text-gray-500 text-sm font-normal">— MotionAGFormer temporal lifting</span></h1>
        <select
          className="bg-gray-800 text-sm text-gray-300 rounded px-2 py-1.5 border border-gray-700 max-w-48"
          value={base}
          onChange={e => setBase(e.target.value)}
        >
          <option value="">Select video…</option>
          {videoList.map(v => (
            <option key={v.name} value={v.name}>{v.name}</option>
          ))}
        </select>

        <div className="flex rounded overflow-hidden border border-gray-700" title="2D pose source fed to the lift">
          {SOURCES.map(s => (
            <button
              key={s.id}
              className={`px-2.5 py-1.5 text-sm ${source === s.id ? 'bg-sky-700 text-white' : 'bg-gray-800 text-gray-400 hover:bg-gray-700'}`}
              onClick={() => setSource(s.id)}
            >
              {s.label}
            </button>
          ))}
        </div>

        {doc && (
          <span className="text-xs text-gray-500">
            {doc.source ?? doc.model} · {doc.topology} · {doc.totalFrames} frames
          </span>
        )}
      </header>

      <div className="flex-1 relative" onWheel={onWheelScrub}>
        <Canvas key={canvasKey} camera={{ position: camPos, fov: FOV }} dpr={[1, 2]}>
          <color attach="background" args={['#0a0a0f']} />
          <ambientLight intensity={0.8} />
          <directionalLight position={[3, 5, 2]} intensity={0.6} />
          <Grid
            args={[10, 10]}
            cellSize={0.25}
            cellColor="#1f2937"
            sectionSize={1}
            sectionColor="#374151"
            fadeDistance={14}
            infiniteGrid
          />
          <group position={fitOffset}>
            {frame && <Skeleton frame={frame} bones={doc!.bones} />}
          </group>
          {/* wheel is reserved for frame-scrubbing (onWheelScrub on the wrapper); zoom via pinch */}
          <OrbitControls target={[0, targetY, 0]} enableDamping enableZoom={false} />
        </Canvas>

        {status && (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <p className="bg-gray-900/90 text-gray-300 text-sm px-4 py-3 rounded max-w-2xl text-center">{status}</p>
          </div>
        )}
        {!base && !status && (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <p className="text-gray-500 text-sm">Pick a video to view its lifted 3D skeleton. Drag to rotate · scroll anywhere to scrub frames.</p>
          </div>
        )}
      </div>

      {doc && (
        <div className="border-t border-gray-800 px-4 py-3 flex items-center gap-3 shrink-0" onWheel={onWheelScrub}>
          <button
            className="bg-emerald-700 hover:bg-emerald-600 px-3 py-1.5 rounded text-sm w-20"
            onClick={() => setPlaying(p => !p)}
          >
            {playing ? '❚❚ Pause' : '▶ Play'}
          </button>
          <input
            type="range"
            min={0}
            max={doc.frames.length - 1}
            value={frameIdx}
            onChange={e => { setPlaying(false); setFrameIdx(Number(e.target.value)) }}
            className="flex-1 h-2 cursor-pointer"
          />
          <span className="text-xs text-gray-600 hidden sm:inline">Space play/pause · ←/→ step · scroll to scrub</span>
          <span className="text-xs text-gray-400 tabular-nums w-28 text-right">
            {frameIdx + 1} / {doc.frames.length}{frame && !frame.detected ? ' (interp)' : ''}
          </span>
        </div>
      )}
    </div>
  )
}
