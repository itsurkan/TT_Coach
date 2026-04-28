/**
 * TableLabelsOverlay — SVG-based overlay for user-labeled table keypoints.
 * Renders table outline, net line, and numbered keypoint dots.
 */

import { TableFrameLabel } from '../types'

interface Props {
  tableFrameLabel: TableFrameLabel | null
  color?: string
}

export default function TableLabelsOverlay({ tableFrameLabel, color = '#a855f7' }: Props) {
  if (!tableFrameLabel) return null
  const pts = tableFrameLabel.points

  // Table outline: 0→1→2→3→0 (far-L, far-R, near-R, near-L)
  const corners = [pts[0], pts[1], pts[2], pts[3]]
  const allCorners = corners.every(p => p != null)
  const outlinePath = allCorners
    ? corners.map((p, i) => `${i === 0 ? 'M' : 'L'}${p!.x},${p!.y}`).join(' ') + ' Z'
    : ''

  // Net line: 4→5
  const hasNet = pts[4] != null && pts[5] != null

  return (
    <svg
      viewBox="0 0 1 1"
      preserveAspectRatio="none"
      style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', pointerEvents: 'none' }}
    >
      {/* Table outline */}
      {outlinePath && (
        <path
          d={outlinePath}
          fill={color}
          fillOpacity={0.06}
          stroke={color}
          strokeWidth={0.003}
          strokeOpacity={0.5}
        />
      )}

      {/* Net line (dashed) */}
      {hasNet && (
        <line
          x1={pts[4]!.x} y1={pts[4]!.y}
          x2={pts[5]!.x} y2={pts[5]!.y}
          stroke={color}
          strokeWidth={0.003}
          strokeOpacity={0.7}
          strokeDasharray="0.008 0.005"
        />
      )}

      {/* Numbered keypoint dots */}
      {pts.map((pt, i) => {
        if (!pt) return null
        return (
          <g key={i}>
            {/* Glow */}
            <circle cx={pt.x} cy={pt.y} r={0.012} fill={color} fillOpacity={0.2} />
            {/* Dot */}
            <circle cx={pt.x} cy={pt.y} r={0.007} fill={color} stroke="#000" strokeWidth={0.0015} />
            {/* Number */}
            <text
              x={pt.x}
              y={pt.y + 0.003}
              fontSize={0.014}
              fill="#fff"
              fontWeight="bold"
              fontFamily="monospace"
              textAnchor="middle"
              dominantBaseline="middle"
            >
              {i + 1}
            </text>
          </g>
        )
      })}
    </svg>
  )
}
