/**
 * TrajectoryV3Overlay — SVG-based trajectory rendering for V3.
 * Vector rendering for crisp display at any zoom level.
 *
 * Renders: past arc (solid), predicted arc (dashed), table line,
 * bounce markers, spin classification label.
 * Color: cyan (#22d3ee) to distinguish from V1 magenta and V2 emerald.
 */

import { type PredictiveTrajectoryV3 } from '../utils/trajectoryPipelineV3'

interface Props {
  trajectory: PredictiveTrajectoryV3 | null
  frameIndex: number
}

/** Generate smooth SVG path using Catmull-Rom → cubic bezier conversion */
function positionsToSmoothPath(positions: { x: number; y: number }[]): string {
  if (positions.length === 0) return ''
  if (positions.length === 1) return `M${positions[0].x.toFixed(5)},${positions[0].y.toFixed(5)}`
  if (positions.length === 2) {
    return `M${positions[0].x.toFixed(5)},${positions[0].y.toFixed(5)} L${positions[1].x.toFixed(5)},${positions[1].y.toFixed(5)}`
  }

  const pts = positions
  const parts: string[] = [`M${pts[0].x.toFixed(5)},${pts[0].y.toFixed(5)}`]

  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[Math.max(0, i - 1)]
    const p1 = pts[i]
    const p2 = pts[i + 1]
    const p3 = pts[Math.min(pts.length - 1, i + 2)]

    // Catmull-Rom to cubic bezier control points
    const cp1x = p1.x + (p2.x - p0.x) / 6
    const cp1y = p1.y + (p2.y - p0.y) / 6
    const cp2x = p2.x - (p3.x - p1.x) / 6
    const cp2y = p2.y - (p3.y - p1.y) / 6

    parts.push(`C${cp1x.toFixed(5)},${cp1y.toFixed(5)} ${cp2x.toFixed(5)},${cp2y.toFixed(5)} ${p2.x.toFixed(5)},${p2.y.toFixed(5)}`)
  }

  return parts.join(' ')
}

/** Spin label with color coding */
const SPIN_COLORS: Record<string, string> = {
  topspin: '#f87171',   // red-ish
  backspin: '#60a5fa',  // blue-ish
  flat: '#a1a1aa',      // gray
}

export default function TrajectoryV3Overlay({ trajectory, frameIndex }: Props) {
  if (!trajectory) return null

  const traj = trajectory
  const fit = traj.fit
  const t0 = traj.segmentStartFrame

  // Past arc paths — draw each segment separately for sharp V at table bounces.
  // Each segment gets its own smooth spline; junctions between segments are sharp.
  const pastPaths: string[] = []
  if (traj.pastSegments && traj.pastSegments.length > 0) {
    for (const seg of traj.pastSegments) {
      if (seg.length >= 2) {
        pastPaths.push(positionsToSmoothPath(seg))
      }
    }
  } else if (traj.pastPositions.length >= 2) {
    // Fallback: single path through all positions
    pastPaths.push(positionsToSmoothPath(traj.pastPositions))
  }

  // Predicted path — connect from last past position to predicted positions
  let predictedPath = ''
  if (traj.predictedPositions.length >= 1 && traj.pastPositions.length > 0) {
    const lastPast = traj.pastPositions[traj.pastPositions.length - 1]
    const allPredicted = [lastPast, ...traj.predictedPositions]
    predictedPath = positionsToSmoothPath(allPredicted)
  }

  // Table surface line — clipped to estimated table X boundaries
  const table = traj.tableSurface
  let tableLine: { x1: number; y1: number; x2: number; y2: number } | null = null
  if (table.isValid) {
    const y1 = table.slope * table.xMin + table.intercept
    const y2 = table.slope * table.xMax + table.intercept
    tableLine = { x1: table.xMin, y1, x2: table.xMax, y2 }
  }

  // Detected dots from past positions
  const detectedDots = traj.pastPositions.filter(p => p.source === 'DETECTED')

  // Sizes in normalized units for circles/shapes (relative to viewBox 0-1)
  const dotR = 0.006
  const bounceR = 0.01

  const spinColor = SPIN_COLORS[traj.spinClass] ?? '#a1a1aa'

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
      {/* Table surface line — pixel-based stroke */}
      {tableLine && (
        <line
          x1={tableLine.x1}
          y1={tableLine.y1}
          x2={tableLine.x2}
          y2={tableLine.y2}
          stroke="#22d3ee"
          strokeWidth={1.5}
          strokeDasharray="6 4"
          opacity={0.5}
          vectorEffect="non-scaling-stroke"
        />
      )}

      {/* Past arc segments: each drawn separately for sharp V at bounces */}
      {pastPaths.map((pp, idx) => (
        <g key={`past-seg-${idx}`}>
          {/* Dark outline for contrast */}
          <path
            d={pp}
            fill="none"
            stroke="rgba(0,0,0,0.5)"
            strokeWidth={5}
            strokeLinecap="round"
            strokeLinejoin="round"
            vectorEffect="non-scaling-stroke"
          />
          {/* Solid cyan line */}
          <path
            d={pp}
            fill="none"
            stroke="#22d3ee"
            strokeWidth={3}
            strokeLinecap="round"
            strokeLinejoin="round"
            opacity={0.95}
            vectorEffect="non-scaling-stroke"
          />
        </g>
      ))}

      {/* Predicted arc: dark outline for contrast */}
      {predictedPath && (
        <path
          d={predictedPath}
          fill="none"
          stroke="rgba(0,0,0,0.4)"
          strokeWidth={4}
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
      )}

      {/* Predicted arc: dashed cyan line */}
      {predictedPath && (
        <path
          d={predictedPath}
          fill="none"
          stroke="#22d3ee"
          strokeWidth={2.5}
          strokeDasharray="8 5"
          opacity={0.7}
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
      )}

      {/* Detected dots (white with outline) */}
      {detectedDots.map((p, i) => (
        <circle
          key={`det-${i}`}
          cx={p.x}
          cy={p.y}
          r={dotR}
          fill="white"
          stroke="rgba(0,0,0,0.5)"
          strokeWidth={1}
          vectorEffect="non-scaling-stroke"
        />
      ))}

      {/* Predicted position dots (fading cyan) */}
      {traj.predictedPositions.map((p, i) => (
        <circle
          key={`pred-${i}`}
          cx={p.x}
          cy={p.y}
          r={dotR * 0.8}
          fill="#22d3ee"
          stroke="rgba(0,0,0,0.3)"
          strokeWidth={1}
          vectorEffect="non-scaling-stroke"
          opacity={0.8 * (1 - i / traj.predictedPositions.length)}
        />
      ))}

      {/* Predicted bounce markers (upward triangles) */}
      {traj.predictedBounces.map((b, i) => {
        const s = bounceR
        const tri = `M${b.x - s},${b.y} L${b.x},${b.y - s * 1.5} L${b.x + s},${b.y} Z`
        return (
          <path
            key={`bounce-${i}`}
            d={tri}
            fill="#22d3ee"
            stroke="white"
            strokeWidth={1.5}
            vectorEffect="non-scaling-stroke"
            opacity={0.9}
          />
        )
      })}

      {/* Observed bounce points from table model (diamonds) */}
      {table.bouncePoints.map((b, i) => {
        const s = dotR * 1.5
        const diamond = `M${b.x},${b.y - s} L${b.x + s},${b.y} L${b.x},${b.y + s} L${b.x - s},${b.y} Z`
        return (
          <path
            key={`obs-bounce-${i}`}
            d={diamond}
            fill="#fb923c"
            stroke="rgba(0,0,0,0.4)"
            strokeWidth={1}
            vectorEffect="non-scaling-stroke"
            opacity={0.85}
          />
        )
      })}

      {/* Spin classification label above arc midpoint */}
      {traj.spinClass !== 'flat' && traj.pastPositions.length > 0 && (
        <text
          x={traj.pastPositions[Math.floor(traj.pastPositions.length / 2)]?.x ?? 0.5}
          y={(traj.pastPositions[Math.floor(traj.pastPositions.length / 2)]?.y ?? 0.5) - 0.025}
          fontSize={0.02}
          fontFamily="monospace"
          fontWeight="bold"
          fill={spinColor}
          stroke="rgba(0,0,0,0.6)"
          strokeWidth={0.002}
          paintOrder="stroke"
          textAnchor="middle"
        >
          {traj.spinClass}
        </text>
      )}

      {/* Info text */}
      <text
        x={0.015}
        y={0.945}
        fontSize={0.025}
        fontFamily="monospace"
        fontWeight="bold"
        fill="#22d3ee"
        stroke="rgba(0,0,0,0.6)"
        strokeWidth={0.003}
        paintOrder="stroke"
      >
        {`v3 · ${traj.detectionCount} pts · ${traj.predictedBounces.length} bounce${traj.predictedBounces.length !== 1 ? 's' : ''}`}
        {traj.spinClass !== 'flat' ? ` · ${traj.spinClass}` : ''}
        {table.isValid ? ` · table: ${table.bouncePoints.length} obs` : ''}
      </text>
    </svg>
  )
}
