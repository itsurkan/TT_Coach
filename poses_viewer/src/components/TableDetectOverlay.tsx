/**
 * TableDetectOverlay — SVG-based table detection overlay.
 * Renders table outline + net line + keypoint dots.
 */

interface Props {
  keypoints: Array<{ x: number; y: number; confidence: number }> | null
  color?: string  // CSS color
  minConf?: number
}

export default function TableDetectOverlay({ keypoints, color = '#8b5cf6', minConf = 0.3 }: Props) {
  if (!keypoints || keypoints.length < 6) return null

  const kps = keypoints
  // Corner order: 0=farL, 1=farR, 2=nearR, 3=nearL
  const cornerOrder = [0, 1, 2, 3]
  const visibleCorners = cornerOrder.filter(i => kps[i].confidence > minConf)

  // Build table outline path
  let outlinePath = ''
  if (visibleCorners.length >= 3) {
    outlinePath = visibleCorners
      .map((i, idx) => `${idx === 0 ? 'M' : 'L'}${kps[i].x},${kps[i].y}`)
      .join(' ') + ' Z'
  }

  // Net line: 4→5
  const hasNet = kps[4].confidence > minConf && kps[5].confidence > minConf

  return (
    <svg
      viewBox="0 0 1 1"
      preserveAspectRatio="none"
      style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', pointerEvents: 'none' }}
    >
      {/* Table outline */}
      {outlinePath && (
        <>
          <path
            d={outlinePath}
            fill={color}
            fillOpacity={0.06}
            stroke={color}
            strokeWidth={0.003}
            strokeOpacity={0.6}
          />
        </>
      )}

      {/* Net line (dashed) */}
      {hasNet && (
        <line
          x1={kps[4].x} y1={kps[4].y}
          x2={kps[5].x} y2={kps[5].y}
          stroke={color}
          strokeWidth={0.003}
          strokeOpacity={0.8}
          strokeDasharray="0.008 0.005"
        />
      )}

      {/* Keypoint dots */}
      {kps.map((kp, i) => kp.confidence > 0.2 ? (
        <circle
          key={i}
          cx={kp.x} cy={kp.y}
          r={0.008}
          fill={color}
          stroke="#000"
          strokeWidth={0.002}
        />
      ) : null)}
    </svg>
  )
}
