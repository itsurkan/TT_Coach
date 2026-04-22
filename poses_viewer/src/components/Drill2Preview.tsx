import { useEffect, useMemo, useRef, useState } from 'react'
import Drill2Mannequin from './Drill2Mannequin'

// Standalone "Drill 2" preview: loads a start and end frame from andrii_1 poses
// and animates a ping-pong linear transition between them on a fresh canvas.
// Deliberately does NOT share any code with the anchor-based drill editor.

const POSES_URL = '/videos/andrii_1/andrii_1_poses.json'
const DEFAULT_START_FRAME = 57
const DEFAULT_END_FRAME = 63
const LOOP_FRAMES = 24              // 12 forward + 12 back (ping-pong)
const FPS = 24 / 1.3                // playback rate (1.3× slower than captured)
const PAUSE_BETWEEN_REPS_MS = 450   // idle hold at the start pose after each loop
const CANVAS_W = 640
const CANVAS_H = 720

// MediaPipe pose landmark connections (33-keypoint model).
const POSE_EDGES: Array<[number, number]> = [
  [0, 1], [1, 2], [2, 3], [3, 7],
  [0, 4], [4, 5], [5, 6], [6, 8],
  [9, 10],
  [11, 13], [13, 15], [15, 17], [15, 19], [15, 21], [17, 19],
  [12, 14], [14, 16], [16, 18], [16, 20], [16, 22], [18, 20],
  [11, 12], [11, 23], [12, 24], [23, 24],
  [23, 25], [25, 27], [27, 29], [29, 31], [27, 31],
  [24, 26], [26, 28], [28, 30], [30, 32], [28, 32],
]

type Landmark = { x: number; y: number; z?: number; visibility?: number }
type Frame = { landmarks?: Landmark[]; frameIndex?: number; timestampMs?: number }
type PoseFile = { frames?: Frame[] } | Frame[]

interface Props {
  onClose: () => void
}

export default function Drill2Preview({ onClose }: Props) {
  const [status, setStatus] = useState<string>('Loading…')
  const [startFrame, setStartFrame] = useState(DEFAULT_START_FRAME)
  const [endFrame, setEndFrame] = useState(DEFAULT_END_FRAME)
  const [startLms, setStartLms] = useState<Landmark[] | null>(null)
  const [endLms, setEndLms] = useState<Landmark[] | null>(null)
  const [tick, setTick] = useState(0)
  const [paused, setPaused] = useState(false)
  const [reps, setReps] = useState(0)
  const [yaw, setYaw] = useState(0)       // rotation around vertical axis (radians)
  const [fixtureReady, setFixtureReady] = useState(0)
  const [humanizer, setHumanizer] = useState(true)

  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const rafRef = useRef<number | null>(null)
  const framesRef = useRef<Frame[] | null>(null)
  const totalFramesRef = useRef(0)
  const dragRef = useRef<{ px: number; yaw: number } | null>(null)

  // Median ankle anchor computed once across the whole pose file. Stable
  // horizontal position (X/Z median, visibility-filtered) + floor contact
  // Y (max, since MediaPipe y grows downward so higher y = lower on screen).
  // Both MediaPipe ankle indices: 27 = L_ANKLE, 28 = R_ANKLE.
  const ankleAnchors = useMemo(() => {
    const frames = framesRef.current
    if (!frames) return null
    const collect = (idx: number) => {
      const xs: number[] = [], zs: number[] = [], ys: number[] = []
      for (const f of frames) {
        const p = f.landmarks?.[idx]
        if (!p || (p.visibility ?? 1) < 0.5) continue
        xs.push(p.x); zs.push(p.z ?? 0); ys.push(p.y)
      }
      if (xs.length === 0) return null
      const med = (arr: number[]) => {
        const s = [...arr].sort((a, b) => a - b)
        return s[Math.floor(s.length / 2)]
      }
      return { x: med(xs), y: Math.max(...ys), z: med(zs) }
    }
    const L = collect(27), R = collect(28)
    if (!L || !R) return null
    return { L, R }
  }, [fixtureReady])

  // ---- Fetch fixture once, cache into ref. ----
  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const resp = await fetch(POSES_URL)
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
        const raw = (await resp.json()) as PoseFile
        const frames = Array.isArray(raw) ? raw : raw.frames ?? []
        if (cancelled) return
        framesRef.current = frames
        totalFramesRef.current = frames.length
        setFixtureReady(n => n + 1)
      } catch (err) {
        if (!cancelled) setStatus(`Load failed: ${String(err)}`)
      }
    })()
    return () => { cancelled = true }
  }, [])

  // ---- Resolve start / end landmarks whenever frame indices change. ----
  useEffect(() => {
    const frames = framesRef.current
    if (!frames) return
    const pick = (idx: number): Landmark[] | null => {
      if (idx < 0 || idx >= frames.length) return null
      const lms = frames[idx]?.landmarks
      return lms && lms.length >= 33 ? lms : null
    }
    const a = pick(startFrame)
    const b = pick(endFrame)
    setStartLms(a)
    setEndLms(b)
    if (a && b) {
      setStatus(`frame ${startFrame} → ${endFrame}  ·  ${frames.length} total`)
    } else {
      setStatus(`Missing landmarks at frame ${startFrame} or ${endFrame}`)
    }
  }, [startFrame, endFrame, fixtureReady])

  // ---- Animation loop with pause-after-rep. ----
  useEffect(() => {
    if (paused || !startLms || !endLms) return
    const frameMs = 1000 / FPS
    let last = performance.now()
    let holdUntil = 0
    const step = (now: number) => {
      if (now < holdUntil) {
        rafRef.current = requestAnimationFrame(step)
        return
      }
      if (now - last >= frameMs) {
        setTick(t => {
          const next = (t + 1) % LOOP_FRAMES
          if (next === 0) {
            holdUntil = now + PAUSE_BETWEEN_REPS_MS
            setReps(r => r + 1)
          }
          return next
        })
        last = now
      }
      rafRef.current = requestAnimationFrame(step)
    }
    rafRef.current = requestAnimationFrame(step)
    return () => { if (rafRef.current) cancelAnimationFrame(rafRef.current) }
  }, [paused, startLms, endLms])

  // ---- Drag-to-rotate (yaw only — horizontal drag). ----
  const onPointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    (e.target as HTMLCanvasElement).setPointerCapture(e.pointerId)
    dragRef.current = { px: e.clientX, yaw }
  }
  const onPointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const d = dragRef.current
    if (!d) return
    const dx = e.clientX - d.px
    // One full horizontal drag across canvas = ~2π yaw. Pitch is locked.
    setYaw(d.yaw + (dx / CANVAS_W) * Math.PI * 2)
  }
  const onPointerUp = (e: React.PointerEvent<HTMLCanvasElement>) => {
    dragRef.current = null
    try { (e.target as HTMLCanvasElement).releasePointerCapture(e.pointerId) } catch { /* ignore */ }
  }

  // ---- Foot-align the end pose to the start (pin feet, translate the rest by
  //      the foot-centroid delta). Captured feet drift between frames but the
  //      real player is stationary, so we correct it here once per pose pair.
  const alignedEnd = useMemo<Landmark[] | null>(() => {
    if (!startLms || !endLms) return null
    const FOOT_IDX = [27, 28, 29, 30, 31, 32]
    const footSet = new Set(FOOT_IDX)
    const centroid = (lms: Landmark[]) => {
      let sx = 0, sy = 0, sz = 0
      for (const i of FOOT_IDX) {
        sx += lms[i].x; sy += lms[i].y; sz += lms[i].z ?? 0
      }
      const n = FOOT_IDX.length
      return { x: sx / n, y: sy / n, z: sz / n }
    }
    const cS = centroid(startLms)
    const cE = centroid(endLms)
    const dx = cS.x - cE.x, dy = cS.y - cE.y, dz = cS.z - cE.z
    return endLms.map((b, i) => {
      if (footSet.has(i)) {
        const a = startLms[i]
        return { x: a.x, y: a.y, z: a.z ?? 0, visibility: b.visibility ?? 1 }
      }
      return {
        x: b.x + dx,
        y: b.y + dy,
        z: (b.z ?? 0) + dz,
        visibility: b.visibility ?? 1,
      }
    })
  }, [startLms, endLms])

  // ---- Current-frame blended landmarks (linear lerp).
  const half = LOOP_FRAMES / 2
  const phase = tick < half ? tick / half : (LOOP_FRAMES - tick) / half
  const blended = useMemo<Landmark[] | null>(() => {
    if (!startLms || !alignedEnd) return null
    return startLms.map((a, i) => {
      const b = alignedEnd[i]
      return {
        x: a.x + (b.x - a.x) * phase,
        y: a.y + (b.y - a.y) * phase,
        z: (a.z ?? 0) + ((b.z ?? 0) - (a.z ?? 0)) * phase,
        visibility: Math.min(a.visibility ?? 1, b.visibility ?? 1),
      }
    })
  }, [startLms, alignedEnd, phase])

  // ---- 2D stick-view draw. Skipped when humanizer is on (3D view takes over).
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !startLms || !alignedEnd || !blended) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    // Yaw-only rotation around hip midpoint (vertical axis) — pivots the
    // skeleton left/right without tilting it.
    const rotate = (lms: Landmark[], cx: number, cy: number, cz: number): Landmark[] => {
      const sy = Math.sin(yaw), cy_ = Math.cos(yaw)
      return lms.map(p => {
        const x0 = p.x - cx
        const z0 = (p.z ?? 0) - cz
        const x1 = x0 * cy_ + z0 * sy
        const z1 = -x0 * sy + z0 * cy_
        return { x: x1 + cx, y: p.y, z: z1 + cz, visibility: p.visibility }
      })
    }

    const hipCx = (blended[23].x + blended[24].x) / 2
    const hipCy = (blended[23].y + blended[24].y) / 2
    const hipCz = ((blended[23].z ?? 0) + (blended[24].z ?? 0)) / 2

    const rotBlended = rotate(blended, hipCx, hipCy, hipCz)
    const rotStart = rotate(startLms, hipCx, hipCy, hipCz)
    const rotEnd = rotate(alignedEnd, hipCx, hipCy, hipCz)

    // Fit to canvas using combined bbox of rotated start+end.
    const bbox = (() => {
      let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
      for (const set of [rotStart, rotEnd]) {
        for (const p of set) {
          if ((p.visibility ?? 1) < 0.2) continue
          if (p.x < minX) minX = p.x
          if (p.y < minY) minY = p.y
          if (p.x > maxX) maxX = p.x
          if (p.y > maxY) maxY = p.y
        }
      }
      const pad = 0.08
      return { x0: minX - pad, y0: minY - pad, x1: maxX + pad, y1: maxY + pad }
    })()

    const bw = Math.max(1e-6, bbox.x1 - bbox.x0)
    const bh = Math.max(1e-6, bbox.y1 - bbox.y0)
    const scale = Math.min(CANVAS_W / bw, CANVAS_H / bh)
    const offX = (CANVAS_W - bw * scale) / 2
    const offY = (CANVAS_H - bh * scale) / 2
    const project = (p: Landmark) => ({
      x: (p.x - bbox.x0) * scale + offX,
      y: (p.y - bbox.y0) * scale + offY,
    })

    // Clear.
    ctx.fillStyle = '#0b0f19'
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H)

    // Faint ghosts of START (cyan) and END (magenta).
    const drawGhost = (lms: Landmark[], color: string) => {
      ctx.strokeStyle = color
      ctx.lineWidth = 1.5
      ctx.globalAlpha = 0.25
      ctx.beginPath()
      for (const [i, j] of POSE_EDGES) {
        const a = project(lms[i])
        const b = project(lms[j])
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(b.x, b.y)
      }
      ctx.stroke()
      ctx.globalAlpha = 1
    }
    drawGhost(rotStart, '#22d3ee')
    drawGhost(rotEnd, '#e879f9')

    // Stick rendering (only path now; 3D mannequin has its own canvas).
    ctx.strokeStyle = '#fbbf24'
    ctx.lineWidth = 3
    ctx.beginPath()
    for (const [i, j] of POSE_EDGES) {
      const a = project(rotBlended[i])
      const b = project(rotBlended[j])
      ctx.moveTo(a.x, a.y)
      ctx.lineTo(b.x, b.y)
    }
    ctx.stroke()

    ctx.fillStyle = '#fef3c7'
    for (const p of rotBlended) {
      const q = project(p)
      ctx.beginPath()
      ctx.arc(q.x, q.y, 3.5, 0, Math.PI * 2)
      ctx.fill()
    }

    // HUD.
    ctx.fillStyle = '#94a3b8'
    ctx.font = '13px ui-monospace, monospace'
    ctx.fillText(`t = ${phase.toFixed(2)}`, 12, 20)
    ctx.fillText(`frame ${startFrame} ─── ${endFrame}`, 12, 38)
    ctx.fillText(`yaw ${((yaw * 180) / Math.PI).toFixed(0)}°`, 12, 56)
    ctx.fillText(`reps ${reps}`, 12, 74)
    ctx.fillText(`humanizer ${humanizer ? 'on' : 'off'}`, 12, 92)
  }, [blended, alignedEnd, startLms, yaw, startFrame, endFrame, reps, humanizer, phase])

  const clampFrame = (n: number) => {
    const total = totalFramesRef.current || Number.MAX_SAFE_INTEGER
    return Math.max(0, Math.min(total - 1, Math.trunc(n)))
  }
  const stepStart = (delta: number) => setStartFrame(f => clampFrame(f + delta))
  const stepEnd = (delta: number) => setEndFrame(f => clampFrame(f + delta))
  const onStartInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value
    if (v === '') return
    const n = Number(v)
    if (Number.isFinite(n)) setStartFrame(clampFrame(n))
  }
  const onEndInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value
    if (v === '') return
    const n = Number(v)
    if (Number.isFinite(n)) setEndFrame(clampFrame(n))
  }

  const resetView = () => setYaw(0)

  return (
    <div className="flex-1 min-h-0 bg-gray-950 text-gray-100 overflow-auto">
      <div className="p-4 flex flex-col gap-3 items-center">
        <div className="w-full flex items-center justify-between max-w-3xl">
          <h2 className="text-lg font-semibold">Drill 2 — frame {startFrame} → {endFrame}</h2>
          <div className="flex gap-2 items-center">
            <label
              className="px-3 py-1.5 rounded bg-gray-700 text-sm flex items-center gap-2 cursor-pointer select-none hover:bg-gray-600"
              title="Easing + dwell + anatomical fit + breathing jitter"
            >
              <input
                type="checkbox"
                checked={humanizer}
                onChange={e => setHumanizer(e.target.checked)}
              />
              Humanizer
            </label>
            <button
              className="px-3 py-1.5 rounded bg-gray-700 text-sm hover:bg-gray-600"
              onClick={resetView}
              title="Reset rotation"
            >
              Reset view
            </button>
            <button
              className="px-3 py-1.5 rounded bg-gray-700 text-sm hover:bg-gray-600"
              onClick={() => setPaused(p => !p)}
            >
              {paused ? '▶ Play' : '⏸ Pause'}
            </button>
            <button
              className="px-3 py-1.5 rounded bg-gray-700 text-sm hover:bg-gray-600"
              onClick={onClose}
            >
              ✕ Close
            </button>
          </div>
        </div>

        {humanizer && startLms && alignedEnd ? (
          <Drill2Mannequin
            startLms={startLms}
            endLms={alignedEnd}
            ankleAnchors={ankleAnchors}
            zScale={0.3}
            phase={phase}
            width={CANVAS_W}
            height={CANVAS_H}
          />
        ) : (
          <canvas
            ref={canvasRef}
            width={CANVAS_W}
            height={CANVAS_H}
            className="border border-gray-800 rounded bg-gray-900 cursor-grab active:cursor-grabbing"
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
            onPointerCancel={onPointerUp}
          />
        )}

        <div className="flex gap-6 text-sm">
          <div className="flex items-center gap-2">
            <span className="text-cyan-400 w-12 text-right">Start</span>
            <button
              className="px-2 py-1 rounded bg-gray-700 hover:bg-gray-600 font-mono"
              onClick={() => stepStart(-1)}
            >◀</button>
            <input
              type="number"
              min={0}
              max={(totalFramesRef.current || 1) - 1}
              step={1}
              value={startFrame}
              onChange={onStartInput}
              className="font-mono text-gray-200 w-16 text-center bg-gray-800 border border-gray-700 rounded px-1 py-0.5"
            />
            <button
              className="px-2 py-1 rounded bg-gray-700 hover:bg-gray-600 font-mono"
              onClick={() => stepStart(+1)}
            >▶</button>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-fuchsia-400 w-12 text-right">End</span>
            <button
              className="px-2 py-1 rounded bg-gray-700 hover:bg-gray-600 font-mono"
              onClick={() => stepEnd(-1)}
            >◀</button>
            <input
              type="number"
              min={0}
              max={(totalFramesRef.current || 1) - 1}
              step={1}
              value={endFrame}
              onChange={onEndInput}
              className="font-mono text-gray-200 w-16 text-center bg-gray-800 border border-gray-700 rounded px-1 py-0.5"
            />
            <button
              className="px-2 py-1 rounded bg-gray-700 hover:bg-gray-600 font-mono"
              onClick={() => stepEnd(+1)}
            >▶</button>
          </div>
        </div>

        <div className="text-xs text-gray-400 font-mono">{status}</div>
        <div className="text-xs text-gray-500 max-w-md text-center">
          Drag canvas left/right to rotate (yaw only).
          Cyan = start ghost, magenta = end ghost, yellow = live interpolation.
          {PAUSE_BETWEEN_REPS_MS}ms pause between reps.
        </div>
      </div>
    </div>
  )
}
