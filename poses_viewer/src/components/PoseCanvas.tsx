import { useRef, useEffect, useCallback } from 'react'
import { Frame, FrameLabel, TableFrameLabel, PoseTopology, Landmark } from '../types'
import type { TrajectorySegment, PredictiveTrajectory } from '../utils/trajectoryPipeline'
import { evaluateFit } from '../utils/trajectoryPipeline'
import { getConnections, SIDE_COLORS, RTM_SIDE_COLORS, type Connection, type Side } from '../utils/poseConnections'
import { LABEL_COLORS } from './LabelPanel'

interface Props {
  frame: Frame | null
  frames: Frame[]
  frameIndex: number
  videoWidth: number
  videoHeight: number
  /** Which skeleton edge list to draw. Defaults to legacy MediaPipe-33. */
  topology?: PoseTopology
  /** RTMPose overlay (separate _poses_rtm.json data) */
  showRtmPoses?: boolean
  rtmFrame?: Frame | null
  rtmTopology?: PoseTopology
  transparent?: boolean
  showPoses?: boolean
  showBall?: boolean
  /** Ball V5 overlay (separate _ball_v5.json data) */
  showBallV5?: boolean
  ballV5Frame?: Frame | null
  ballV5Frames?: Frame[]
  ballV5FrameIdx?: number
  /** Ball YOLO overlay (separate _ball_yolo.json data) */
  showBallYolo?: boolean
  ballYoloFrame?: Frame | null
  ballYoloFrames?: Frame[]
  ballYoloFrameIdx?: number
  isContactFrame?: boolean
  contactType?: string
  /** Current frame's label (if any) */
  frameLabel?: FrameLabel | null
  /** When true, next canvas click sets the corrected ball position */
  placingBall?: boolean
  /** Called with normalized (0-1) coords when user clicks to place ball */
  onPlaceBall?: (x: number, y: number) => void
  /** Table keypoint labels */
  showTableLabels?: boolean
  tableFrameLabel?: TableFrameLabel | null
  placingKeypoint?: number  // 0-5 or -1
  onPlaceTableKeypoint?: (x: number, y: number) => void
  onResetTableLabel?: () => void
  /** Trajectory overlay */
  showTrajectory?: boolean
  trajectoryMode?: 'fitted' | 'predict'
  trajectorySegments?: TrajectorySegment[]
  predictiveTrajectory?: PredictiveTrajectory | null
}

const CANVAS_W = 360
const CANVAS_H = 640
const MIN_VISIBILITY = 0.3
const TRAIL_FRAMES = 5
const TRAIL_MAX_GAP = 2

export default function PoseCanvas({
  frame, frames, frameIndex, videoWidth, videoHeight,
  topology = 'mediapipe33',
  showRtmPoses = false, rtmFrame, rtmTopology = 'coco17',
  transparent, showPoses = true, showBall = true,
  showBallV5 = false, ballV5Frame, ballV5Frames, ballV5FrameIdx = -1,
  showBallYolo = false, ballYoloFrame, ballYoloFrames, ballYoloFrameIdx = -1,
  isContactFrame = false, contactType,
  frameLabel, placingBall, onPlaceBall,
  showTableLabels = false, tableFrameLabel, placingKeypoint = -1, onPlaceTableKeypoint, onResetTableLabel,
  showTrajectory = false, trajectoryMode = 'fitted', trajectorySegments = [],
  predictiveTrajectory,
}: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  // Handle click-to-place ball position or table keypoint
  const handleClick = useCallback((e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    const x = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
    const y = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height))

    // Table keypoint placement takes priority when active
    if (placingKeypoint >= 0 && onPlaceTableKeypoint) {
      onPlaceTableKeypoint(x, y)
      return
    }
    if (placingBall && onPlaceBall) {
      onPlaceBall(x, y)
    }
  }, [placingBall, onPlaceBall, placingKeypoint, onPlaceTableKeypoint])

  // Right-click to reset table label for current frame
  const handleContextMenu = useCallback((e: React.MouseEvent<HTMLCanvasElement>) => {
    if (showTableLabels && onResetTableLabel) {
      e.preventDefault()
      onResetTableLabel()
    }
  }, [showTableLabels, onResetTableLabel])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')!

    if (transparent) {
      ctx.clearRect(0, 0, CANVAS_W, CANVAS_H)
    } else {
      ctx.fillStyle = '#0f172a'
      ctx.fillRect(0, 0, CANVAS_W, CANVAS_H)
    }

    if (!frame) return

    // ── Pose skeleton ────────────────────────────────────────────────────────
    const drawSkeleton = (lms: Landmark[], connections: Connection[], jointColor: string, sideColors: Record<Side, string> = SIDE_COLORS) => {
      for (const [a, b, side] of connections) {
        const la = lms[a]
        const lb = lms[b]
        if (!la || !lb) continue
        if (la.visibility < MIN_VISIBILITY && lb.visibility < MIN_VISIBILITY) continue
        ctx.beginPath()
        ctx.moveTo(la.x * CANVAS_W, la.y * CANVAS_H)
        ctx.lineTo(lb.x * CANVAS_W, lb.y * CANVAS_H)
        if (transparent) {
          ctx.strokeStyle = 'rgba(0,0,0,0.5)'
          ctx.lineWidth = 4
          ctx.stroke()
        }
        ctx.beginPath()
        ctx.moveTo(la.x * CANVAS_W, la.y * CANVAS_H)
        ctx.lineTo(lb.x * CANVAS_W, lb.y * CANVAS_H)
        ctx.strokeStyle = sideColors[side]
        ctx.lineWidth = 2
        ctx.stroke()
      }

      // Only draw joints that participate in an edge — e.g. Halpe26's
      // head/neck/hip-mid points are exported but intentionally not rendered.
      const usedIndices = new Set<number>()
      for (const [a, b] of connections) { usedIndices.add(a); usedIndices.add(b) }

      lms.forEach((lm, i) => {
        if (!lm || !usedIndices.has(i)) return
        if (lm.visibility < MIN_VISIBILITY) return
        ctx.beginPath()
        ctx.arc(lm.x * CANVAS_W, lm.y * CANVAS_H, 3, 0, Math.PI * 2)
        ctx.fillStyle = jointColor
        ctx.fill()
      })
    }

    if (showPoses) {
      drawSkeleton(Array.isArray(frame.landmarks) ? frame.landmarks : [], getConnections(topology), '#ffffff')
    }

    // RTMPose overlay — fuchsia/amber/lime palette + yellow joints,
    // visually distinct from the MediaPipe skeleton's blue/red/green
    if (showRtmPoses && rtmFrame) {
      drawSkeleton(Array.isArray(rtmFrame.landmarks) ? rtmFrame.landmarks : [], getConnections(rtmTopology), '#facc15', RTM_SIDE_COLORS)
    }

    // ── Ball trail + ball ────────────────────────────────────────────────────
    if (showBall) {
      const trailPoints: Array<{ cx: number; cy: number; fi: number }> = []
      for (let i = Math.max(0, frameIndex - TRAIL_FRAMES); i <= frameIndex; i++) {
        const b = frames[i]?.ball
        if (b) trailPoints.push({ cx: b.x * CANVAS_W, cy: b.y * CANVAS_H, fi: i })
      }

      if (trailPoints.length >= 2) {
        for (let i = 1; i < trailPoints.length; i++) {
          const gap = trailPoints[i].fi - trailPoints[i - 1].fi
          if (gap > TRAIL_MAX_GAP) continue
          const alpha = (i / trailPoints.length) * 0.85
          ctx.beginPath()
          ctx.moveTo(trailPoints[i - 1].cx, trailPoints[i - 1].cy)
          ctx.lineTo(trailPoints[i].cx, trailPoints[i].cy)
          ctx.strokeStyle = `rgba(239, 68, 68, ${alpha})`
          ctx.lineWidth = 3
          ctx.stroke()
        }
      }

      if (frame.ball) {
        const bx = frame.ball.x * CANVAS_W
        const by = frame.ball.y * CANVAS_H
        const br = Math.max(4, frame.ball.radiusPx * (CANVAS_W / videoWidth))

        ctx.beginPath()
        ctx.arc(bx, by, br, 0, Math.PI * 2)
        ctx.fillStyle = 'rgba(250, 204, 21, 0.15)'
        ctx.fill()

        ctx.beginPath()
        ctx.arc(bx, by, br, 0, Math.PI * 2)
        ctx.strokeStyle = '#facc15'
        ctx.lineWidth = 2
        ctx.stroke()

        ctx.beginPath()
        ctx.arc(bx, by, 3, 0, Math.PI * 2)
        ctx.fillStyle = '#facc15'
        ctx.fill()
      }
    }

    // ── Ball V5 trail + ball (cyan) ──────────────────────────────────────────
    if (showBallV5 && ballV5Frames && ballV5FrameIdx >= 0) {
      const v5Trail: Array<{ cx: number; cy: number; fi: number }> = []
      for (let i = Math.max(0, ballV5FrameIdx - TRAIL_FRAMES); i <= ballV5FrameIdx; i++) {
        const b = ballV5Frames[i]?.ball
        if (b) v5Trail.push({ cx: b.x * CANVAS_W, cy: b.y * CANVAS_H, fi: i })
      }

      if (v5Trail.length >= 2) {
        for (let i = 1; i < v5Trail.length; i++) {
          const gap = v5Trail[i].fi - v5Trail[i - 1].fi
          if (gap > TRAIL_MAX_GAP) continue
          const alpha = (i / v5Trail.length) * 0.85
          ctx.beginPath()
          ctx.moveTo(v5Trail[i - 1].cx, v5Trail[i - 1].cy)
          ctx.lineTo(v5Trail[i].cx, v5Trail[i].cy)
          ctx.strokeStyle = `rgba(6, 182, 212, ${alpha})`  // cyan trail
          ctx.lineWidth = 3
          ctx.stroke()
        }
      }

      const v5Ball = ballV5Frame?.ball
      if (v5Ball) {
        const bx = v5Ball.x * CANVAS_W
        const by = v5Ball.y * CANVAS_H
        const br = Math.max(4, v5Ball.radiusPx * (CANVAS_W / videoWidth))

        ctx.beginPath()
        ctx.arc(bx, by, br, 0, Math.PI * 2)
        ctx.fillStyle = 'rgba(6, 182, 212, 0.15)'
        ctx.fill()

        ctx.beginPath()
        ctx.arc(bx, by, br, 0, Math.PI * 2)
        ctx.strokeStyle = '#06b6d4'  // cyan
        ctx.lineWidth = 2
        ctx.stroke()

        ctx.beginPath()
        ctx.arc(bx, by, 3, 0, Math.PI * 2)
        ctx.fillStyle = '#06b6d4'
        ctx.fill()
      }
    }

    // ── Ball YOLO trail + ball (lime) ────────────────────────────────────────
    if (showBallYolo && ballYoloFrames && ballYoloFrameIdx >= 0) {
      const yoloTrail: Array<{ cx: number; cy: number; fi: number }> = []
      for (let i = Math.max(0, ballYoloFrameIdx - TRAIL_FRAMES); i <= ballYoloFrameIdx; i++) {
        const b = ballYoloFrames[i]?.ball
        if (b) yoloTrail.push({ cx: b.x * CANVAS_W, cy: b.y * CANVAS_H, fi: i })
      }

      if (yoloTrail.length >= 2) {
        for (let i = 1; i < yoloTrail.length; i++) {
          const gap = yoloTrail[i].fi - yoloTrail[i - 1].fi
          if (gap > TRAIL_MAX_GAP) continue
          const alpha = (i / yoloTrail.length) * 0.85
          ctx.beginPath()
          ctx.moveTo(yoloTrail[i - 1].cx, yoloTrail[i - 1].cy)
          ctx.lineTo(yoloTrail[i].cx, yoloTrail[i].cy)
          ctx.strokeStyle = `rgba(163, 230, 53, ${alpha})`  // lime trail
          ctx.lineWidth = 3
          ctx.stroke()
        }
      }

      const yoloBall = ballYoloFrame?.ball
      if (yoloBall) {
        const bx = yoloBall.x * CANVAS_W
        const by = yoloBall.y * CANVAS_H
        const br = Math.max(4, yoloBall.radiusPx * (CANVAS_W / videoWidth))

        ctx.beginPath()
        ctx.arc(bx, by, br, 0, Math.PI * 2)
        ctx.fillStyle = 'rgba(163, 230, 53, 0.15)'
        ctx.fill()

        ctx.beginPath()
        ctx.arc(bx, by, br, 0, Math.PI * 2)
        ctx.strokeStyle = '#a3e635'  // lime
        ctx.lineWidth = 2
        ctx.stroke()

        ctx.beginPath()
        ctx.arc(bx, by, 3, 0, Math.PI * 2)
        ctx.fillStyle = '#a3e635'
        ctx.fill()
      }
    }

    // ── Trajectory overlay ───────────────────────────────────────────────────
    if (showTrajectory && trajectoryMode === 'predict' && predictiveTrajectory) {
      // ── PREDICTIVE MODE ──────────────────────────────────────────────────
      const traj = predictiveTrajectory
      const fit = traj.fit
      const t0 = traj.segmentStartFrame

      // Past: solid magenta line (fitted to actual detections)
      if (traj.pastPositions.length >= 2) {
        const tStart = traj.pastPositions[0].frameIndex - t0
        const tEnd = traj.pastPositions[traj.pastPositions.length - 1].frameIndex - t0
        const steps = Math.max(20, (tEnd - tStart) * 4)
        ctx.beginPath()
        for (let step = 0; step <= steps; step++) {
          const t = tStart + (step / steps) * (tEnd - tStart)
          const pos = evaluateFit(fit, t)
          const sx = pos.x * CANVAS_W
          const sy = pos.y * CANVAS_H
          if (step === 0) ctx.moveTo(sx, sy)
          else ctx.lineTo(sx, sy)
        }
        ctx.strokeStyle = 'rgba(232, 121, 249, 0.9)'
        ctx.lineWidth = 2
        ctx.stroke()
      }

      // Past detected dots
      for (const p of traj.pastPositions) {
        if (p.source !== 'DETECTED') continue
        ctx.beginPath()
        ctx.arc(p.x * CANVAS_W, p.y * CANVAS_H, 3, 0, Math.PI * 2)
        ctx.fillStyle = '#ffffff'
        ctx.fill()
      }

      // Predicted future: dashed magenta line through predicted positions
      if (traj.predictedPositions.length >= 1) {
        // Connect from last past position through predicted positions
        const lastPast = traj.pastPositions[traj.pastPositions.length - 1]
        ctx.beginPath()
        ctx.moveTo(lastPast.x * CANVAS_W, lastPast.y * CANVAS_H)
        for (const p of traj.predictedPositions) {
          ctx.lineTo(p.x * CANVAS_W, p.y * CANVAS_H)
        }
        ctx.strokeStyle = 'rgba(232, 121, 249, 0.5)'
        ctx.lineWidth = 2
        ctx.setLineDash([4, 4])
        ctx.stroke()
        ctx.setLineDash([])

        // Predicted position dots (fading)
        for (let i = 0; i < traj.predictedPositions.length; i++) {
          const p = traj.predictedPositions[i]
          const fade = 1 - i / traj.predictedPositions.length
          ctx.beginPath()
          ctx.arc(p.x * CANVAS_W, p.y * CANVAS_H, 2, 0, Math.PI * 2)
          ctx.fillStyle = `rgba(232, 121, 249, ${fade * 0.6})`
          ctx.fill()
        }

        // Prediction horizon marker
        const lastPred = traj.predictedPositions[traj.predictedPositions.length - 1]
        const lx = lastPred.x * CANVAS_W
        const ly = lastPred.y * CANVAS_H
        ctx.beginPath()
        ctx.moveTo(lx - 4, ly - 4)
        ctx.lineTo(lx + 4, ly + 4)
        ctx.moveTo(lx + 4, ly - 4)
        ctx.lineTo(lx - 4, ly + 4)
        ctx.strokeStyle = 'rgba(232, 121, 249, 0.4)'
        ctx.lineWidth = 1.5
        ctx.stroke()
      }

      // Info overlay
      ctx.font = '10px monospace'
      ctx.fillStyle = 'rgba(232, 121, 249, 0.8)'
      ctx.fillText(`predict · ${traj.detectionCount} pts · ${traj.predictedPositions.length}f ahead`, 6, CANVAS_H - 8)

    } else if (showTrajectory && trajectoryMode === 'fitted' && trajectorySegments.length > 0) {
      // ── FITTED MODE (retroactive) ────────────────────────────────────────
      const currentSegIdx = trajectorySegments.findIndex(
        s => frameIndex >= s.startFrameIndex && frameIndex <= s.endFrameIndex
      )

      for (let si = 0; si < trajectorySegments.length; si++) {
        const seg = trajectorySegments[si]
        const isCurrent = si === currentSegIdx
        const isAdjacent = currentSegIdx >= 0 && Math.abs(si - currentSegIdx) === 1
        if (!isCurrent && !isAdjacent) continue

        const alpha = isCurrent ? 0.9 : 0.3

        // Smooth parabolic curve
        if (seg.fittedPositions.length >= 2) {
          const fit = seg.fitCoefficients
          const tStart = seg.startFrameIndex
          const tEnd = seg.endFrameIndex
          const steps = Math.max(20, (tEnd - tStart) * 4)
          ctx.beginPath()
          for (let step = 0; step <= steps; step++) {
            const t = (step / steps) * (tEnd - tStart)
            const pos = evaluateFit(fit, t)
            if (step === 0) ctx.moveTo(pos.x * CANVAS_W, pos.y * CANVAS_H)
            else ctx.lineTo(pos.x * CANVAS_W, pos.y * CANVAS_H)
          }
          ctx.strokeStyle = `rgba(232, 121, 249, ${alpha})`
          ctx.lineWidth = 2
          ctx.setLineDash([6, 3])
          ctx.stroke()
          ctx.setLineDash([])
        }

        // Fitted position dots
        for (const p of seg.fittedPositions) {
          const r = p.source === 'DETECTED' ? 3 : 2
          const color = p.source === 'DETECTED'
            ? `rgba(255, 255, 255, ${alpha})`
            : `rgba(232, 121, 249, ${alpha * 0.7})`
          ctx.beginPath()
          ctx.arc(p.x * CANVAS_W, p.y * CANVAS_H, r, 0, Math.PI * 2)
          ctx.fillStyle = color
          ctx.fill()
        }
      }

      // Contact diamonds (current + adjacent only)
      const contactColors: Record<string, string> = {
        BOUNCE: '#fb923c', PADDLE_CONTACT: '#a78bfa', NET_CLIP: '#ffffff', UNKNOWN_CONTACT: '#9ca3af',
      }
      const drawnContacts = new Set<number>()
      for (let si = 0; si < trajectorySegments.length; si++) {
        const isCurrent = si === currentSegIdx
        const isAdjacent = currentSegIdx >= 0 && Math.abs(si - currentSegIdx) === 1
        if (!isCurrent && !isAdjacent) continue
        const seg = trajectorySegments[si]
        const alpha = isCurrent ? 1.0 : 0.4
        for (const contact of [seg.contactBefore, seg.contactAfter]) {
          if (!contact || drawnContacts.has(contact.frameIndex)) continue
          drawnContacts.add(contact.frameIndex)
          const cx = contact.position.x * CANVAS_W
          const cy = contact.position.y * CANVAS_H
          ctx.beginPath()
          ctx.moveTo(cx, cy - 4); ctx.lineTo(cx + 4, cy); ctx.lineTo(cx, cy + 4); ctx.lineTo(cx - 4, cy)
          ctx.closePath()
          ctx.globalAlpha = alpha
          ctx.fillStyle = contactColors[contact.type] ?? '#9ca3af'
          ctx.fill()
          ctx.globalAlpha = 1.0
        }
      }

      if (currentSegIdx >= 0) {
        const seg = trajectorySegments[currentSegIdx]
        ctx.font = '10px monospace'
        ctx.fillStyle = 'rgba(232, 121, 249, 0.8)'
        ctx.fillText(`Seg ${currentSegIdx + 1}/${trajectorySegments.length} · RMS ${seg.fitRmsError.toFixed(4)}`, 6, CANVAS_H - 8)
      }
    }

    // ── Contact indicator ────────────────────────────────────────────────────
    if (isContactFrame) {
      const isRacket = contactType === 'racket'
      const color = isRacket ? '167, 139, 250' : '249, 115, 22'  // violet vs orange
      const label = isRacket ? 'RACKET' : 'TABLE'

      // Burst ring at ball position (if ball visible)
      if (frame.ball) {
        const bx = frame.ball.x * CANVAS_W
        const by = frame.ball.y * CANVAS_H
        ctx.beginPath()
        ctx.arc(bx, by, 22, 0, Math.PI * 2)
        ctx.strokeStyle = `rgba(${color}, 0.8)`
        ctx.lineWidth = 3
        ctx.stroke()
        ctx.beginPath()
        ctx.arc(bx, by, 30, 0, Math.PI * 2)
        ctx.strokeStyle = `rgba(${color}, 0.4)`
        ctx.lineWidth = 2
        ctx.stroke()
      }
      // Type label top-right
      ctx.font = 'bold 14px monospace'
      ctx.fillStyle = `rgba(${color}, 0.9)`
      ctx.fillText(label, CANVAS_W - 75, 20)
    }

    // ── Label indicator ───────────────────────────────────────────────────────
    if (frameLabel) {
      const lColor = LABEL_COLORS[frameLabel.label]
      // Badge top-left
      ctx.font = 'bold 11px monospace'
      const text = frameLabel.label.toUpperCase()
      const tw = ctx.measureText(text).width
      ctx.fillStyle = lColor + '40'
      ctx.fillRect(4, 4, tw + 12, 18)
      ctx.fillStyle = lColor
      ctx.fillText(text, 10, 17)

      // Corrected position crosshair (for wrong/missed)
      if (frameLabel.correctedX != null && frameLabel.correctedY != null) {
        const cx = frameLabel.correctedX * CANVAS_W
        const cy = frameLabel.correctedY * CANVAS_H
        ctx.strokeStyle = '#22c55e'
        ctx.lineWidth = 2
        // Crosshair
        ctx.beginPath(); ctx.moveTo(cx - 10, cy); ctx.lineTo(cx + 10, cy); ctx.stroke()
        ctx.beginPath(); ctx.moveTo(cx, cy - 10); ctx.lineTo(cx, cy + 10); ctx.stroke()
        // Circle
        ctx.beginPath(); ctx.arc(cx, cy, 8, 0, Math.PI * 2); ctx.stroke()
      }
    }

    // Table keypoints now rendered via SVG TableLabelsOverlay

    // ── Placing mode cursor hint ──────────────────────────────────────────────
    if (placingBall || placingKeypoint >= 0) {
      ctx.strokeStyle = 'rgba(34, 197, 94, 0.6)'
      ctx.lineWidth = 1
      ctx.setLineDash([4, 4])
      ctx.strokeRect(2, 2, CANVAS_W - 4, CANVAS_H - 4)
      ctx.setLineDash([])
    }
  }, [frame, frames, frameIndex, videoWidth, videoHeight, topology, showRtmPoses, rtmFrame, rtmTopology, showPoses, showBall, showBallV5, ballV5Frame, ballV5Frames, ballV5FrameIdx, showBallYolo, ballYoloFrame, ballYoloFrames, ballYoloFrameIdx, isContactFrame, contactType, frameLabel, placingBall, placingKeypoint, showTableLabels, tableFrameLabel,  showTrajectory, trajectoryMode, trajectorySegments, predictiveTrajectory])

  return (
    <canvas
      ref={canvasRef}
      width={CANVAS_W}
      height={CANVAS_H}
      className={transparent ? 'rounded-lg' : 'rounded-lg border border-gray-800'}
      style={{
        ...(transparent
          ? { width: '100%', height: '100%' }
          : { maxHeight: 'calc(100vh - 120px)', width: 'auto' }),
        cursor: (placingBall || placingKeypoint >= 0) ? 'crosshair' : undefined,
      }}
      onClick={handleClick}
      onContextMenu={handleContextMenu}
    />
  )
}
