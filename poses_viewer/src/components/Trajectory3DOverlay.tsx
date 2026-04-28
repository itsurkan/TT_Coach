/**
 * Trajectory3DOverlay — SVG overlay showing 3D ball trajectory projected via homography.
 *
 * Renders on the video viewport:
 * - Ball trail in screen space (past positions), colored by height (z_cm)
 * - Bounce markers (diamonds) at detected table bounces with cm coordinates
 * - Height indicator line from ball shadow to ball position
 *
 * Color: #f472b6 (pink) to distinguish from other trajectory overlays.
 */

import type { Trajectory3DResult } from '../types'

interface Props {
  trajectory: Trajectory3DResult | null
  frameIndex: number
}

/** Map z_cm (height) to a color: green at table → yellow mid-air → red high */
function heightColor(z_cm: number): string {
  const t = Math.min(z_cm / 40, 1)  // normalize: 0cm → 0, 40cm+ → 1
  if (t < 0.5) {
    // green → yellow
    const r = Math.round(34 + (250 - 34) * (t * 2))
    const g = Math.round(197 + (204 - 197) * (t * 2))
    const b = Math.round(94 - 94 * (t * 2))
    return `rgb(${r},${g},${b})`
  }
  // yellow → red
  const r = Math.round(250 + (239 - 250) * ((t - 0.5) * 2))
  const g = Math.round(204 - 136 * ((t - 0.5) * 2))
  const b = Math.round(0 + 68 * ((t - 0.5) * 2))
  return `rgb(${r},${g},${b})`
}

export default function Trajectory3DOverlay({ trajectory, frameIndex }: Props) {
  if (!trajectory || trajectory.positions.length === 0) return null

  const positions = trajectory.positions.slice(-5)
  const minFrame = positions.length > 0 ? positions[0].frameIndex : Infinity
  const bounces = trajectory.bounces.filter(b => b.frameIndex >= minFrame)

  const dotR = 0.005
  const bounceR = 0.008

  return (
    <svg
      viewBox="0 0 1 1"
      preserveAspectRatio="none"
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%',
        pointerEvents: 'none',
        overflow: 'visible',
      }}
    >
      {/* Past position dots — colored by height */}
      {positions.map((p, i) => (
        <circle
          key={`p3d-${i}`}
          cx={p.screenX}
          cy={p.screenY}
          r={dotR}
          fill={heightColor(p.z_cm)}
          stroke="rgba(0,0,0,0.4)"
          strokeWidth={1}
          vectorEffect="non-scaling-stroke"
          opacity={0.85}
        />
      ))}

      {/* Bounce markers — diamonds with cm label */}
      {bounces.map((b, i) => {
        // Find screen position from the matching position entry
        const pos = positions.find(p => p.frameIndex === b.frameIndex)
        if (!pos) return null
        const s = bounceR
        const diamond = `M${pos.screenX},${pos.screenY - s} L${pos.screenX + s},${pos.screenY} L${pos.screenX},${pos.screenY + s} L${pos.screenX - s},${pos.screenY} Z`
        return (
          <g key={`b3d-${i}`}>
            <path
              d={diamond}
              fill="#f472b6"
              stroke="white"
              strokeWidth={1.5}
              vectorEffect="non-scaling-stroke"
              opacity={0.9}
            />
            {/* cm coordinates label */}
            <text
              x={pos.screenX + 0.015}
              y={pos.screenY - 0.008}
              fontSize={0.016}
              fontFamily="monospace"
              fontWeight="bold"
              fill="#f472b6"
              stroke="rgba(0,0,0,0.7)"
              strokeWidth={0.002}
              paintOrder="stroke"
            >
              {`${b.x_cm.toFixed(0)},${b.y_cm.toFixed(0)}cm`}
            </text>
          </g>
        )
      })}

      {/* Current frame height indicator */}
      {positions.length > 0 && (() => {
        const curr = positions[positions.length - 1]
        if (curr.z_cm < 1) return null
        return (
          <text
            x={curr.screenX + 0.015}
            y={curr.screenY + 0.005}
            fontSize={0.014}
            fontFamily="monospace"
            fill={heightColor(curr.z_cm)}
            stroke="rgba(0,0,0,0.6)"
            strokeWidth={0.002}
            paintOrder="stroke"
          >
            {`z=${curr.z_cm.toFixed(0)}cm`}
          </text>
        )
      })()}

      {/* Info text */}
      <text
        x={0.015}
        y={0.915}
        fontSize={0.025}
        fontFamily="monospace"
        fontWeight="bold"
        fill="#f472b6"
        stroke="rgba(0,0,0,0.6)"
        strokeWidth={0.003}
        paintOrder="stroke"
      >
        {`3D · ${positions.length} pts · ${bounces.length} bounce${bounces.length !== 1 ? 's' : ''}`}
      </text>
    </svg>
  )
}
