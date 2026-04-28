/**
 * Trajectory3Dv2Overlay — SVG overlay for physics-aware 3D trajectory.
 *
 * Shows fitted positions (smooth arcs), bounce markers, outlier flags,
 * and arc boundaries. Violet color scheme (#8b5cf6).
 */

import type { Trajectory3Dv2Result } from '../utils/trajectoryPipeline3Dv2'

interface Props {
  trajectory: Trajectory3Dv2Result | null
  frameIndex: number
}

function heightColor(z_cm: number): string {
  const t = Math.min(z_cm / 40, 1)
  if (t < 0.5) {
    const r = Math.round(34 + 216 * (t * 2))
    const g = Math.round(197 + 7 * (t * 2))
    const b = Math.round(94 - 94 * (t * 2))
    return `rgb(${r},${g},${b})`
  }
  const r = Math.round(250 - 11 * ((t - 0.5) * 2))
  const g = Math.round(204 - 136 * ((t - 0.5) * 2))
  const b = Math.round(68 * ((t - 0.5) * 2))
  return `rgb(${r},${g},${b})`
}

export default function Trajectory3Dv2Overlay({ trajectory, frameIndex }: Props) {
  if (!trajectory || trajectory.positions.length === 0) return null

  const positions = trajectory.positions.slice(-5)
  const minFrame = positions.length > 0 ? positions[0].frameIndex : Infinity
  const bounces = trajectory.bounces.filter(b => b.frameIndex >= minFrame)
  const outlierSet = new Set(trajectory.outlierFrames)

  const dotR = 0.005
  const bounceR = 0.008

  return (
    <svg
      viewBox="0 0 1 1"
      preserveAspectRatio="none"
      style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', pointerEvents: 'none', overflow: 'visible' }}
    >
      {/* Fitted position dots — colored by height */}
      {positions.map((p, i) => {
        const isOutlier = outlierSet.has(p.frameIndex)
        if (isOutlier) {
          // Outlier: X marker
          const s = dotR * 1.2
          return (
            <g key={`o3d-${i}`}>
              <line x1={p.screenX - s} y1={p.screenY - s} x2={p.screenX + s} y2={p.screenY + s}
                stroke="#ef4444" strokeWidth={2} vectorEffect="non-scaling-stroke" opacity={0.9} />
              <line x1={p.screenX + s} y1={p.screenY - s} x2={p.screenX - s} y2={p.screenY + s}
                stroke="#ef4444" strokeWidth={2} vectorEffect="non-scaling-stroke" opacity={0.9} />
            </g>
          )
        }
        return (
          <circle
            key={`f3d-${i}`}
            cx={p.screenX} cy={p.screenY} r={dotR}
            fill={heightColor(p.z_cm)}
            stroke="rgba(0,0,0,0.4)" strokeWidth={1} vectorEffect="non-scaling-stroke"
            opacity={0.9}
          />
        )
      })}

      {/* Bounce diamonds with cm label */}
      {bounces.map((b, i) => {
        const pos = positions.find(p => p.frameIndex === b.frameIndex)
        if (!pos) return null
        const s = bounceR
        const diamond = `M${pos.screenX},${pos.screenY - s} L${pos.screenX + s},${pos.screenY} L${pos.screenX},${pos.screenY + s} L${pos.screenX - s},${pos.screenY} Z`
        return (
          <g key={`b3dv2-${i}`}>
            <path d={diamond} fill="#8b5cf6" stroke="white" strokeWidth={1.5} vectorEffect="non-scaling-stroke" opacity={0.9} />
            <text x={pos.screenX + 0.015} y={pos.screenY - 0.008}
              fontSize={0.016} fontFamily="monospace" fontWeight="bold"
              fill="#8b5cf6" stroke="rgba(0,0,0,0.7)" strokeWidth={0.002} paintOrder="stroke"
            >{`${b.x_cm.toFixed(0)},${b.y_cm.toFixed(0)}cm`}</text>
          </g>
        )
      })}

      {/* Current height label */}
      {positions.length > 0 && (() => {
        const curr = positions[positions.length - 1]
        if (curr.z_cm < 1) return null
        return (
          <text x={curr.screenX + 0.015} y={curr.screenY + 0.005}
            fontSize={0.014} fontFamily="monospace"
            fill={heightColor(curr.z_cm)} stroke="rgba(0,0,0,0.6)" strokeWidth={0.002} paintOrder="stroke"
          >{`z=${curr.z_cm.toFixed(0)}cm`}</text>
        )
      })()}

      {/* Info bar */}
      <text x={0.015} y={0.885}
        fontSize={0.025} fontFamily="monospace" fontWeight="bold"
        fill="#8b5cf6" stroke="rgba(0,0,0,0.6)" strokeWidth={0.003} paintOrder="stroke"
      >
        {`3Dv2 · ${trajectory.positions.length} pts · ${trajectory.arcs.length} arcs · ${trajectory.bounces.length} bounces`}
        {trajectory.outlierFrames.length > 0 ? ` · ${trajectory.outlierFrames.length} outliers` : ''}
      </text>
    </svg>
  )
}
