/**
 * TableGridOverlay — SVG-based perspective grid on the table surface.
 * Uses homography to project real-world grid lines onto screen.
 */

import { useMemo } from 'react'
import { computeHomography, tableToScreen, TABLE_WIDTH, TABLE_LENGTH, NET_Y } from '../utils/tableHomography'

interface Props {
  keypoints: Array<{ x: number; y: number; confidence: number }> | null
  videoWidth: number
  videoHeight: number
  /** Ball position for cm label */
  ballX?: number  // normalized 0-1
  ballY?: number
  ballDetected?: boolean
  /** Grid color (CSS color, default emerald) */
  color?: string
}

export default function TableGridOverlay({
  keypoints, videoWidth, videoHeight,
  ballX, ballY, ballDetected, color,
}: Props) {
  const gridColor = color ?? '#10b981'
  const homo = useMemo(() => {
    if (!keypoints || keypoints.length < 6) return null
    const corners: [{ x: number; y: number }, { x: number; y: number }, { x: number; y: number }, { x: number; y: number }] = [
      keypoints[0], keypoints[1], keypoints[2], keypoints[3],
    ]
    return computeHomography(corners, videoWidth, videoHeight)
  }, [keypoints, videoWidth, videoHeight])

  if (!homo || !homo.valid) return null

  const GRID_STEP_Y = TABLE_LENGTH / 9   // ~30.4cm, 9 divisions along length
  const GRID_STEP_X = TABLE_WIDTH / 5    // ~30.5cm, 5 divisions along width

  // Generate grid lines
  const widthLines: Array<{ x1: number; y1: number; x2: number; y2: number; isNet: boolean }> = []
  for (let y_cm = 0; y_cm <= TABLE_LENGTH + 0.1; y_cm += GRID_STEP_Y) {
    const p1 = tableToScreen(homo, 0, y_cm, videoWidth, videoHeight)
    const p2 = tableToScreen(homo, TABLE_WIDTH, y_cm, videoWidth, videoHeight)
    if (p1 && p2) {
      widthLines.push({ x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y, isNet: Math.abs(y_cm - NET_Y) < 1 })
    }
  }
  // Ensure net line is included
  const hasNet = widthLines.some(l => l.isNet)
  if (!hasNet) {
    const p1 = tableToScreen(homo, 0, NET_Y, videoWidth, videoHeight)
    const p2 = tableToScreen(homo, TABLE_WIDTH, NET_Y, videoWidth, videoHeight)
    if (p1 && p2) {
      widthLines.push({ x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y, isNet: true })
    }
  }

  const lengthLines: Array<{ x1: number; y1: number; x2: number; y2: number }> = []
  for (let x_cm = 0; x_cm <= TABLE_WIDTH + 0.1; x_cm += GRID_STEP_X) {
    const p1 = tableToScreen(homo, x_cm, 0, videoWidth, videoHeight)
    const p2 = tableToScreen(homo, x_cm, TABLE_LENGTH, videoWidth, videoHeight)
    if (p1 && p2) {
      lengthLines.push({ x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y })
    }
  }

  // Corner labels
  const cornerLabels = [
    { x_cm: 0, y_cm: 0, label: '0,0' },
    { x_cm: TABLE_WIDTH, y_cm: 0, label: `${TABLE_WIDTH.toFixed(0)},0` },
    { x_cm: TABLE_WIDTH, y_cm: TABLE_LENGTH, label: `${TABLE_WIDTH.toFixed(0)},${TABLE_LENGTH.toFixed(0)}` },
    { x_cm: 0, y_cm: TABLE_LENGTH, label: `0,${TABLE_LENGTH.toFixed(0)}` },
  ]

  // Ball cm position
  let ballLabel: { screenX: number; screenY: number; text: string } | null = null
  if (ballDetected && ballX != null && ballY != null) {
    const px = ballX * videoWidth
    const py = ballY * videoHeight
    const w = homo.H[6] * px + homo.H[7] * py + homo.H[8]
    if (Math.abs(w) > 1e-10) {
      const x_cm = (homo.H[0] * px + homo.H[1] * py + homo.H[2]) / w
      const y_cm = (homo.H[3] * px + homo.H[4] * py + homo.H[5]) / w
      ballLabel = {
        screenX: ballX,
        screenY: ballY,
        text: `${x_cm.toFixed(0)},${y_cm.toFixed(0)}cm`,
      }
    }
  }

  return (
    <svg
      viewBox="0 0 1 1"
      preserveAspectRatio="none"
      style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', pointerEvents: 'none' }}
    >
      {/* Width lines (cross-table) */}
      {widthLines.map((l, i) => (
        <line
          key={`w${i}`}
          x1={l.x1} y1={l.y1} x2={l.x2} y2={l.y2}
          stroke={l.isNet ? '#eab308' : gridColor}
          strokeWidth={l.isNet ? 0.003 : 0.0015}
          opacity={l.isNet ? 0.8 : 0.4}
        />
      ))}

      {/* Length lines (along-table) */}
      {lengthLines.map((l, i) => (
        <line
          key={`l${i}`}
          x1={l.x1} y1={l.y1} x2={l.x2} y2={l.y2}
          stroke={gridColor}
          strokeWidth={0.0015}
          opacity={0.4}
        />
      ))}

      {/* Corner labels */}
      {cornerLabels.map((cl, i) => {
        const p = tableToScreen(homo, cl.x_cm, cl.y_cm, videoWidth, videoHeight)
        if (!p) return null
        return (
          <text
            key={`cl${i}`}
            x={p.x + 0.01}
            y={p.y - 0.005}
            fontSize={0.018}
            fill={gridColor}
            opacity={0.7}
            fontFamily="monospace"
          >
            {cl.label}
          </text>
        )
      })}

      {/* Ball position in cm */}
      {ballLabel && (
        <text
          x={ballLabel.screenX + 0.02}
          y={ballLabel.screenY - 0.015}
          fontSize={0.02}
          fill={gridColor}
          fontWeight="bold"
          fontFamily="monospace"
        >
          {ballLabel.text}
        </text>
      )}
    </svg>
  )
}
