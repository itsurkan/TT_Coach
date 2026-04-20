import { useRef, useEffect, useState, useCallback } from 'react'
import type { Landmark } from '../types'
import { POSE_CONNECTIONS, SIDE_COLORS } from '../utils/poseConnections'
import { LM } from '../drill/SkeletonModel'

// Intrinsic canvas resolution. Wider than portrait so rotated limbs don't clip.
const CANVAS_W = 900
const CANVAS_H = 960

// The canonical skeleton spans roughly y ∈ [-0.12, +0.92] in anchor coords
// (head peaks above the frame, ankles reach just above the floor). We scale
// it down so it fits inside the canvas and align the feet to the floor line.
const SKEL_SCALE = 0.70
const FLOOR_NORM_Y = 0.88

interface Props {
  landmarks: Landmark[] | null
  /** Label shown top-left (e.g., "START" / "END" / "frame 3/10"). */
  label?: string
  /** If true, render a faint ghost of another pose underneath for reference. */
  ghost?: Landmark[] | null
  /** Humanized rendering: torso fill, limb capsules, z-based depth shading. */
  humanize?: boolean
}

/** Rotate a landmark around the vertical axis through (0.5, y, 0) by cameraYaw. */
function applyCameraYaw(lm: Landmark, yawDeg: number): Landmark {
  if (yawDeg === 0) return lm
  const rad = (yawDeg * Math.PI) / 180
  const c = Math.cos(rad); const s = Math.sin(rad)
  const dx = lm.x - 0.5
  const dz = lm.z
  return {
    ...lm,
    x: 0.5 + c * dx + s * dz,
    z: -s * dx + c * dz,
  }
}

function projectLandmarks(lms: Landmark[] | null, yawDeg: number): Landmark[] | null {
  if (!lms) return null
  return lms.map(l => applyCameraYaw(l, yawDeg))
}

export default function DrillSkeletonCanvas({ landmarks, label, ghost, humanize = false }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [cameraYaw, setCameraYaw] = useState(0)
  const dragRef = useRef<{ startX: number; startYaw: number } | null>(null)

  const onMouseDown = useCallback((e: React.MouseEvent<HTMLCanvasElement>) => {
    dragRef.current = { startX: e.clientX, startYaw: cameraYaw }
  }, [cameraYaw])

  const onMouseMove = useCallback((e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!dragRef.current) return
    const dx = e.clientX - dragRef.current.startX
    // 1 canvas-pixel ≈ 0.6°. Full drag across ~400 px = ~240°.
    setCameraYaw(dragRef.current.startYaw + dx * 0.6)
  }, [])

  const onMouseUp = useCallback(() => {
    dragRef.current = null
  }, [])

  const onDoubleClick = useCallback(() => setCameraYaw(0), [])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    ctx.clearRect(0, 0, CANVAS_W, CANVAS_H)

    // Sky (top) → floor (bottom) backdrop.
    const sky = ctx.createLinearGradient(0, 0, 0, 0.88 * CANVAS_H)
    sky.addColorStop(0, '#0b1220')
    sky.addColorStop(1, '#1e293b')
    ctx.fillStyle = sky
    ctx.fillRect(0, 0, CANVAS_W, 0.88 * CANVAS_H)

    // Floor: perspective-style grid below the ground line.
    drawFloor(ctx)

    // Horizon / ground line.
    ctx.strokeStyle = '#475569'
    ctx.lineWidth = 2
    const groundY = FLOOR_NORM_Y * CANVAS_H
    ctx.beginPath(); ctx.moveTo(0, groundY); ctx.lineTo(CANVAS_W, groundY); ctx.stroke()

    // Render skeleton in a scaled coordinate space so the canonical body fits
    // vertically and the feet land on the floor line. The transform scales
    // about the horizontal center and translates so anchor-y=0.92 (ankle) maps
    // to FLOOR_NORM_Y on canvas.
    const tx = (CANVAS_W / 2) * (1 - SKEL_SCALE)
    const ankleAnchorY = 0.92
    const ty = (FLOOR_NORM_Y - ankleAnchorY * SKEL_SCALE) * CANVAS_H
    ctx.save()
    ctx.setTransform(SKEL_SCALE, 0, 0, SKEL_SCALE, tx, ty)

    const projectedGhost = projectLandmarks(ghost ?? null, cameraYaw)
    const projectedLms = projectLandmarks(landmarks, cameraYaw)
    if (projectedGhost && projectedGhost.length === 33) {
      drawSkeleton(ctx, projectedGhost, 0.25, false)
    }
    if (projectedLms && projectedLms.length === 33) {
      drawSkeleton(ctx, projectedLms, 1.0, humanize)
    }
    ctx.restore()

    if (label) {
      ctx.fillStyle = '#e2e8f0'
      ctx.font = '14px system-ui, -apple-system, sans-serif'
      ctx.fillText(label, 10, 22)
    }
    // Camera-yaw readout bottom-left (helps when exporting).
    ctx.fillStyle = '#64748b'
    ctx.font = '11px system-ui, -apple-system, sans-serif'
    ctx.fillText(`cam yaw ${cameraYaw.toFixed(0)}°  (drag to rotate · dbl-click = 0)`, 10, CANVAS_H - 10)
  }, [landmarks, label, ghost, humanize, cameraYaw])

  return (
    <canvas
      ref={canvasRef}
      width={CANVAS_W}
      height={CANVAS_H}
      onMouseDown={onMouseDown}
      onMouseMove={onMouseMove}
      onMouseUp={onMouseUp}
      onMouseLeave={onMouseUp}
      onDoubleClick={onDoubleClick}
      className="border border-gray-700 rounded cursor-grab active:cursor-grabbing select-none"
      style={{
        // Scale canvas to fit available vertical space while preserving aspect.
        maxHeight: '85vh',
        height: '85vh',
        width: 'auto',
      }}
    />
  )
}

function drawFloor(ctx: CanvasRenderingContext2D) {
  const groundY = 0.88 * CANVAS_H
  const floorBottomY = CANVAS_H
  const vanishX = CANVAS_W / 2
  // Solid floor fill.
  const floorGrad = ctx.createLinearGradient(0, groundY, 0, floorBottomY)
  floorGrad.addColorStop(0, '#1f2937') // close
  floorGrad.addColorStop(1, '#0f172a') // far (darker → sense of depth)
  ctx.fillStyle = floorGrad
  ctx.fillRect(0, groundY, CANVAS_W, floorBottomY - groundY)

  // Perspective grid: lines radiating from the vanishing point along the horizon.
  ctx.strokeStyle = 'rgba(100, 116, 139, 0.35)' // slate-500 @ 35%
  ctx.lineWidth = 1
  const rayCount = 9
  for (let i = 0; i <= rayCount; i++) {
    const xFar = (i / rayCount) * CANVAS_W
    ctx.beginPath()
    ctx.moveTo(vanishX, groundY)
    ctx.lineTo(xFar * 2 - vanishX, floorBottomY)
    ctx.stroke()
  }

  // Horizontal bands for depth rhythm.
  const bands = 5
  for (let i = 1; i <= bands; i++) {
    // Non-linear spacing (closer bands near the viewer).
    const t = i / bands
    const yBand = groundY + (floorBottomY - groundY) * Math.pow(t, 0.6)
    ctx.beginPath()
    ctx.moveTo(0, yBand)
    ctx.lineTo(CANVAS_W, yBand)
    ctx.stroke()
  }
}

function drawSkeleton(
  ctx: CanvasRenderingContext2D,
  lms: Landmark[],
  alpha: number,
  humanize: boolean
) {
  ctx.globalAlpha = alpha
  if (humanize) {
    drawTorsoFill(ctx, lms)
    drawLimbsAsCapsules(ctx, lms)
  } else {
    drawSticks(ctx, lms)
  }
  // Joints (both modes).
  ctx.fillStyle = '#fef3c7' // amber-100
  for (const l of lms) {
    if (!l) continue
    ctx.beginPath()
    ctx.arc(l.x * CANVAS_W, l.y * CANVAS_H, humanize ? 2 : 3, 0, 2 * Math.PI)
    ctx.fill()
  }
  drawHead(ctx, lms)
  drawFeet(ctx, lms, humanize)
  drawRacket(ctx, lms, alpha)
  ctx.globalAlpha = 1
}

function drawFeet(ctx: CanvasRenderingContext2D, lms: Landmark[], humanize: boolean) {
  // Brighter colors in stick mode so feet are visible against the dark floor.
  drawOneFoot(ctx, lms, LM.L_ANKLE, LM.L_HEEL, LM.L_FOOT, humanize ? '#1e3a8a' : '#3b82f6', humanize)
  drawOneFoot(ctx, lms, LM.R_ANKLE, LM.R_HEEL, LM.R_FOOT, humanize ? '#7f1d1d' : '#ef4444', humanize)
}

function drawOneFoot(
  ctx: CanvasRenderingContext2D,
  lms: Landmark[],
  ankleIdx: number, heelIdx: number, tipIdx: number,
  color: string,
  humanize: boolean
) {
  const a = lms[ankleIdx]; const h = lms[heelIdx]; const t = lms[tipIdx]
  if (!a || !h || !t) return
  const ax = a.x * CANVAS_W, ay = a.y * CANVAS_H
  const hx = h.x * CANVAS_W, hy = h.y * CANVAS_H
  const tx = t.x * CANVAS_W, ty = t.y * CANVAS_H
  // Shoe as a filled polygon ankle → heel → tip → ankle, with a slight outward
  // bulge for the sole in humanize mode.
  ctx.fillStyle = humanize ? depthShade(color, a.z) : color
  ctx.strokeStyle = '#0f172a'
  ctx.lineWidth = humanize ? 2 : 1.5
  ctx.beginPath()
  ctx.moveTo(ax, ay)
  if (humanize) {
    // Sole: quadratic curves for a rounded shoe silhouette.
    const midHeelX = (ax + hx) / 2
    const midHeelY = (ay + hy) / 2
    const midTipX = (ax + tx) / 2
    const midTipY = (ay + ty) / 2
    ctx.quadraticCurveTo(midHeelX, midHeelY + 6, hx, hy)
    ctx.quadraticCurveTo((hx + tx) / 2, (hy + ty) / 2 + 4, tx, ty)
    ctx.quadraticCurveTo(midTipX, midTipY + 2, ax, ay)
  } else {
    ctx.lineTo(hx, hy)
    ctx.lineTo(tx, ty)
    ctx.closePath()
  }
  ctx.fill()
  ctx.stroke()
}

function drawSticks(ctx: CanvasRenderingContext2D, lms: Landmark[]) {
  ctx.lineWidth = 3
  // Draw far bones first (large z = away from camera), near bones last on top.
  // Forward = −z, so "closer to camera" = smaller z → draw LAST.
  const sorted = [...POSE_CONNECTIONS].sort((A, B) => {
    const za = (lms[A[0]].z + lms[A[1]].z) / 2
    const zb = (lms[B[0]].z + lms[B[1]].z) / 2
    return zb - za // descending z: far first, near last
  })
  for (const [a, b, side] of sorted) {
    const la = lms[a]; const lb = lms[b]
    if (!la || !lb) continue
    ctx.strokeStyle = SIDE_COLORS[side]
    ctx.beginPath()
    ctx.moveTo(la.x * CANVAS_W, la.y * CANVAS_H)
    ctx.lineTo(lb.x * CANVAS_W, lb.y * CANVAS_H)
    ctx.stroke()
  }
}

/** Fill the trunk as a 4-point polygon so the humanized body reads as mass. */
function drawTorsoFill(ctx: CanvasRenderingContext2D, lms: Landmark[]) {
  const lSh = lms[LM.L_SHOULDER]; const rSh = lms[LM.R_SHOULDER]
  const lHip = lms[LM.L_HIP];      const rHip = lms[LM.R_HIP]
  if (!lSh || !rSh || !lHip || !rHip) return
  const zAvg = (lSh.z + rSh.z + lHip.z + rHip.z) / 4
  const fill = depthShade('#e0e7ff', zAvg) // indigo-100 with depth tint
  ctx.fillStyle = fill
  ctx.strokeStyle = '#4338ca' // indigo-700
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(lSh.x * CANVAS_W, lSh.y * CANVAS_H)
  ctx.lineTo(rSh.x * CANVAS_W, rSh.y * CANVAS_H)
  ctx.lineTo(rHip.x * CANVAS_W, rHip.y * CANVAS_H)
  ctx.lineTo(lHip.x * CANVAS_W, lHip.y * CANVAS_H)
  ctx.closePath()
  ctx.fill()
  ctx.stroke()
}

/** Draw each limb segment as a thick rounded line with depth-based thickness. */
function drawLimbsAsCapsules(ctx: CanvasRenderingContext2D, lms: Landmark[]) {
  ctx.lineCap = 'round'
  // Sort by z so limbs closer to the camera render on top of farther ones.
  const bones = POSE_CONNECTIONS
    .filter(([a, b]) => !isTorsoConn(a, b) && !isFaceConn(a, b) && !isHandSpread(a, b))
    .sort((A, B) => {
      const za = (lms[A[0]].z + lms[A[1]].z) / 2
      const zb = (lms[B[0]].z + lms[B[1]].z) / 2
      return zb - za // far first, near last
    })
  for (const [a, b, side] of bones) {
    const la = lms[a]; const lb = lms[b]
    if (!la || !lb) continue
    const zAvg = (la.z + lb.z) / 2
    const thickness = limbThicknessFor(a, b, zAvg)
    ctx.strokeStyle = depthShade(SIDE_COLORS[side], zAvg)
    ctx.lineWidth = thickness
    ctx.beginPath()
    ctx.moveTo(la.x * CANVAS_W, la.y * CANVAS_H)
    ctx.lineTo(lb.x * CANVAS_W, lb.y * CANVAS_H)
    ctx.stroke()
  }
}

function isTorsoConn(a: number, b: number): boolean {
  const torso: number[] = [LM.L_SHOULDER, LM.R_SHOULDER, LM.L_HIP, LM.R_HIP]
  return torso.includes(a) && torso.includes(b)
}

function isFaceConn(a: number, b: number): boolean {
  return a <= 10 && b <= 10
}

function isHandSpread(a: number, b: number): boolean {
  const handIdx: number[] = [LM.L_PINKY, LM.L_INDEX, LM.L_THUMB, LM.R_PINKY, LM.R_INDEX, LM.R_THUMB]
  return handIdx.includes(a) && handIdx.includes(b)
}

function limbThicknessFor(a: number, b: number, zAvg: number): number {
  const upperLimb = [
    [LM.L_SHOULDER, LM.L_ELBOW], [LM.R_SHOULDER, LM.R_ELBOW],
    [LM.L_HIP, LM.L_KNEE], [LM.R_HIP, LM.R_KNEE],
  ]
  const lowerLimb = [
    [LM.L_ELBOW, LM.L_WRIST], [LM.R_ELBOW, LM.R_WRIST],
    [LM.L_KNEE, LM.L_ANKLE], [LM.R_KNEE, LM.R_ANKLE],
  ]
  const match = (pair: number[][]) =>
    pair.some(([p, q]) => (a === p && b === q) || (a === q && b === p))
  const base = match(upperLimb) ? 18 : match(lowerLimb) ? 14 : 6
  // Depth factor: far away (+z) → thinner.
  const depthFactor = 1 - Math.max(-0.3, Math.min(0.3, zAvg)) * 0.6
  return base * depthFactor
}

/** Tint an RGB-ish hex color based on z (further away = darker). */
function depthShade(hex: string, z: number): string {
  // Clamp z to roughly the range skeleton generates.
  const zc = Math.max(-0.4, Math.min(0.4, z))
  // -0.4 (close) → 1.15 brightness ; +0.4 (far) → 0.6 brightness
  const brightness = 1 + (-zc) * 0.8
  const { r, g, b } = hexToRgb(hex)
  const scale = (v: number) =>
    Math.max(0, Math.min(255, Math.round(v * brightness)))
  return `rgb(${scale(r)}, ${scale(g)}, ${scale(b)})`
}

function hexToRgb(hex: string): { r: number; g: number; b: number } {
  const h = hex.replace('#', '')
  const n = parseInt(h.length === 3
    ? h.split('').map(c => c + c).join('')
    : h, 16)
  return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 }
}

function drawHead(ctx: CanvasRenderingContext2D, lms: Landmark[]) {
  const nose = lms[LM.NOSE]
  if (!nose) return
  // Fixed canvas-space radius — independent of body rotation (projecting the
  // shoulder width would otherwise shrink the head as the trunk turns).
  // Sized at ~half the previous area (r / √2).
  const headR = CANVAS_H * 0.04
  const cx = nose.x * CANVAS_W
  const cy = nose.y * CANVAS_H
  ctx.fillStyle = '#fde68a' // amber-200
  ctx.beginPath()
  ctx.arc(cx, cy, headR, 0, 2 * Math.PI)
  ctx.fill()
  ctx.strokeStyle = '#92400e' // amber-800
  ctx.lineWidth = 2
  ctx.stroke()
}

function drawRacket(ctx: CanvasRenderingContext2D, lms: Landmark[], alpha: number) {
  const wrist = lms[LM.R_WRIST]
  const index = lms[LM.R_INDEX]
  const thumb = lms[LM.R_THUMB]
  if (!wrist || !index) return
  // Racket head center: a bit beyond the index-finger landmark along forearm.
  const dx = (index.x - wrist.x)
  const dy = (index.y - wrist.y)
  const len = Math.hypot(dx, dy) || 1e-6
  const ux = dx / len; const uy = dy / len
  const headOffset = 0.04 // normalized, roughly paddle neck length
  const cx = (wrist.x + ux * (len + headOffset)) * CANVAS_W
  const cy = (wrist.y + uy * (len + headOffset)) * CANVAS_H

  // Paddle face: ellipse oriented perpendicular to forearm.
  const wristPx = wrist.x * CANVAS_W; const wristPy = wrist.y * CANVAS_H
  const headPx = cx; const headPy = cy
  const angle = Math.atan2(headPy - wristPy, headPx - wristPx)

  ctx.save()
  ctx.translate(headPx, headPy)
  ctx.rotate(angle + Math.PI / 2) // face perpendicular to handle
  ctx.globalAlpha = alpha
  // Rubber face (red — classic TT forehand side)
  ctx.fillStyle = '#dc2626' // red-600
  ctx.strokeStyle = '#7f1d1d' // red-900
  ctx.lineWidth = 1.5
  ctx.beginPath()
  ctx.ellipse(0, 0, 26, 34, 0, 0, 2 * Math.PI)
  ctx.fill()
  ctx.stroke()
  // Edge highlight
  ctx.strokeStyle = 'rgba(255,255,255,0.25)'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.ellipse(0, 0, 24, 32, 0, 0, 2 * Math.PI)
  ctx.stroke()
  ctx.restore()

  // Handle (short, between wrist and head).
  ctx.save()
  ctx.strokeStyle = '#78350f' // amber-900 — wood handle
  ctx.lineWidth = 5
  ctx.lineCap = 'round'
  ctx.beginPath()
  ctx.moveTo(wristPx, wristPy)
  ctx.lineTo(headPx - ux * 22, headPy - uy * 22)
  ctx.stroke()
  ctx.restore()

  // Thumb dot for visual grip reference.
  if (thumb) {
    ctx.fillStyle = '#f59e0b'
    ctx.beginPath()
    ctx.arc(thumb.x * CANVAS_W, thumb.y * CANVAS_H, 3, 0, 2 * Math.PI)
    ctx.fill()
  }
}
