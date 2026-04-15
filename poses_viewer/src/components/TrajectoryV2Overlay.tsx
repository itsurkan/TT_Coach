/**
 * TrajectoryV2Overlay — SVG-based trajectory rendering for V2.
 * Uses SVG instead of canvas so the trajectory stays crisp at any zoom level.
 * Renders: past arc (solid), predicted arc (dashed), table line, bounce markers.
 * Color: emerald (#34d399) to distinguish from V1 magenta.
 */

import type { PredictiveTrajectoryV2 } from '../utils/trajectoryPipelineV2'
import { evaluateFit } from '../utils/trajectoryPipeline'

interface Props {
  trajectory: PredictiveTrajectoryV2 | null
  /** Current frame index for info display */
  frameIndex: number
}

/** Generate smooth SVG path data from the parabolic fit */
function fitToPathData(
  fit: { ax: number; bx: number; cx: number; ay: number; by: number; cy: number },
  tStart: number,
  tEnd: number,
  steps: number,
): string {
  const parts: string[] = []
  for (let i = 0; i <= steps; i++) {
    const t = tStart + (i / steps) * (tEnd - tStart)
    const pos = evaluateFit(fit, t)
    // SVG viewBox is 0-1 normalized
    const x = pos.x
    const y = pos.y
    parts.push(`${i === 0 ? 'M' : 'L'}${x.toFixed(5)},${y.toFixed(5)}`)
  }
  return parts.join(' ')
}

/** Generate path data from position array */
function positionsToPathData(positions: { x: number; y: number }[]): string {
  if (positions.length === 0) return ''
  return positions
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(5)},${p.y.toFixed(5)}`)
    .join(' ')
}

export default function TrajectoryV2Overlay({ trajectory, frameIndex }: Props) {
  if (!trajectory) return null

  const traj = trajectory
  const fit = traj.fit
  const t0 = traj.segmentStartFrame

  // Past arc path (smooth fitted curve)
  let pastPath = ''
  if (traj.pastPositions.length >= 2) {
    const tStart = traj.pastPositions[0].frameIndex - t0
    const tEnd = traj.pastPositions[traj.pastPositions.length - 1].frameIndex - t0
    const steps = Math.max(30, (tEnd - tStart) * 6)
    pastPath = fitToPathData(fit, tStart, tEnd, steps)
  }

  // Predicted path (through predicted positions — includes bounces)
  let predictedPath = ''
  if (traj.predictedPositions.length >= 1) {
    const lastPast = traj.pastPositions[traj.pastPositions.length - 1]
    const allPredicted = [lastPast, ...traj.predictedPositions]
    predictedPath = positionsToPathData(allPredicted)
  }

  // Table surface line (if valid, clipped to estimated table X bounds)
  const table = traj.tableSurface
  let tableLine: { x1: number; y1: number; x2: number; y2: number } | null = null
  if (table.isValid) {
    const x1 = table.tableXMin
    const x2 = table.tableXMax
    const y1 = table.slope * x1 + table.intercept
    const y2 = table.slope * x2 + table.intercept
    tableLine = { x1, y1, x2, y2 }
  }

  // Detected dots from past positions
  const detectedDots = traj.pastPositions.filter(p => p.source === 'DETECTED')

  // Sizes in normalized units for circles/shapes (relative to viewBox 0-1)
  const dotR = 0.006
  const bounceR = 0.01

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
          stroke="#34d399"
          strokeWidth={1.5}
          strokeDasharray="6 4"
          opacity={0.5}
          vectorEffect="non-scaling-stroke"
        />
      )}

      {/* Past arc: dark outline for contrast */}
      {pastPath && (
        <path
          d={pastPath}
          fill="none"
          stroke="rgba(0,0,0,0.5)"
          strokeWidth={5}
          strokeLinecap="round"
          strokeLinejoin="round"
          vectorEffect="non-scaling-stroke"
        />
      )}

      {/* Past arc: solid emerald line */}
      {pastPath && (
        <path
          d={pastPath}
          fill="none"
          stroke="#34d399"
          strokeWidth={3}
          strokeLinecap="round"
          strokeLinejoin="round"
          opacity={0.95}
          vectorEffect="non-scaling-stroke"
        />
      )}

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

      {/* Predicted arc: dashed emerald line */}
      {predictedPath && (
        <path
          d={predictedPath}
          fill="none"
          stroke="#34d399"
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

      {/* Predicted position dots (fading emerald) */}
      {traj.predictedPositions.map((p, i) => (
        <circle
          key={`pred-${i}`}
          cx={p.x}
          cy={p.y}
          r={dotR * 0.8}
          fill="#34d399"
          stroke="rgba(0,0,0,0.3)"
          strokeWidth={1}
          vectorEffect="non-scaling-stroke"
          opacity={0.8 * (1 - i / traj.predictedPositions.length)}
        />
      ))}

      {/* Bounce markers (triangles) */}
      {traj.predictedBounces.map((b, i) => {
        const s = bounceR
        const tri = `M${b.x - s},${b.y} L${b.x},${b.y - s * 1.5} L${b.x + s},${b.y} Z`
        return (
          <path
            key={`bounce-${i}`}
            d={tri}
            fill="#34d399"
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

      {/* Info text */}
      <text
        x={0.015}
        y={0.975}
        fontSize={0.025}
        fontFamily="monospace"
        fontWeight="bold"
        fill="#34d399"
        stroke="rgba(0,0,0,0.6)"
        strokeWidth={0.003}
        paintOrder="stroke"
      >
        {`v2 · ${traj.detectionCount} pts · ${traj.predictedBounces.length} bounce${traj.predictedBounces.length !== 1 ? 's' : ''}`}
        {table.isValid ? ` · table: ${table.bouncePoints.length} obs` : ''}
      </text>
    </svg>
  )
}
