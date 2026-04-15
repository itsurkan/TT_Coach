/**
 * TrajectoryV4Overlay — SVG-based trajectory rendering for V4.
 * Color: amber (#f59e0b) to distinguish from V1/V2/V3.
 */

import type { PredictiveTrajectory } from '../utils/trajectoryPipeline'
import { evaluateFit } from '../utils/trajectoryPipeline'

interface Props {
  trajectory: PredictiveTrajectory | null
  frameIndex: number
}

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
    parts.push(`${i === 0 ? 'M' : 'L'}${pos.x.toFixed(5)},${pos.y.toFixed(5)}`)
  }
  return parts.join(' ')
}

function positionsToPathData(positions: { x: number; y: number }[]): string {
  if (positions.length === 0) return ''
  return positions
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(5)},${p.y.toFixed(5)}`)
    .join(' ')
}

export default function TrajectoryV4Overlay({ trajectory, frameIndex }: Props) {
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

  // Predicted path
  let predictedPath = ''
  if (traj.predictedPositions.length >= 1) {
    const lastPast = traj.pastPositions[traj.pastPositions.length - 1]
    predictedPath = positionsToPathData([lastPast, ...traj.predictedPositions])
  }

  const detectedDots = traj.pastPositions.filter(p => p.source === 'DETECTED')
  const dotR = 0.006

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
      {/* Past arc: dark outline */}
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

      {/* Past arc: solid amber line */}
      {pastPath && (
        <path
          d={pastPath}
          fill="none"
          stroke="#f59e0b"
          strokeWidth={3}
          strokeLinecap="round"
          strokeLinejoin="round"
          opacity={0.95}
          vectorEffect="non-scaling-stroke"
        />
      )}

      {/* Predicted arc: dark outline */}
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

      {/* Predicted arc: dashed amber line */}
      {predictedPath && (
        <path
          d={predictedPath}
          fill="none"
          stroke="#f59e0b"
          strokeWidth={2.5}
          strokeDasharray="8 5"
          opacity={0.7}
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
      )}

      {/* Detected dots */}
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

      {/* Predicted position dots */}
      {traj.predictedPositions.map((p, i) => (
        <circle
          key={`pred-${i}`}
          cx={p.x}
          cy={p.y}
          r={dotR * 0.8}
          fill="#f59e0b"
          stroke="rgba(0,0,0,0.3)"
          strokeWidth={1}
          vectorEffect="non-scaling-stroke"
          opacity={0.8 * (1 - i / traj.predictedPositions.length)}
        />
      ))}

      {/* Info text */}
      <text
        x={0.015}
        y={0.975}
        fontSize={0.025}
        fontFamily="monospace"
        fontWeight="bold"
        fill="#f59e0b"
        stroke="rgba(0,0,0,0.6)"
        strokeWidth={0.003}
        paintOrder="stroke"
      >
        {`v4 · ${traj.detectionCount} pts`}
      </text>
    </svg>
  )
}
