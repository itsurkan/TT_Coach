import { Download, Copy } from 'lucide-react'
import { TableFrameLabel, TABLE_KEYPOINT_COUNT } from '../types'

interface Props {
  frameIndex: number
  totalFrames: number
  tableLabels: Record<number, TableFrameLabel>
  currentTableLabel: TableFrameLabel | null
  placingKeypoint: number
  onSelectKeypoint: (idx: number) => void
  onClearTableLabel: () => void
  onCopyFromNearest: () => void
  onExportTableLabels: () => void
  /** Ball marking */
  placingBall: boolean
  onStartBallPlacing: () => void
  hasBallLabel: boolean
}

export default function TableLabelPanel({
  frameIndex,
  totalFrames,
  tableLabels,
  currentTableLabel,
  placingKeypoint,
  onSelectKeypoint,
  onClearTableLabel,
  onCopyFromNearest,
  onExportTableLabels,
  placingBall,
  onStartBallPlacing,
  hasBallLabel,
}: Props) {
  const labelCount = Object.keys(tableLabels).length
  const pts = currentTableLabel?.points ?? []
  const pointCount = pts.filter(p => p !== null).length

  return (
    <div className="space-y-3">
      <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Table Keypoints</div>

      {/* Progress dots */}
      <div className="flex gap-1.5 items-center">
        {Array.from({ length: TABLE_KEYPOINT_COUNT }, (_, idx) => (
          <button
            key={idx}
            onClick={() => onSelectKeypoint(idx)}
            title={`Select point ${idx + 1}`}
            className={`w-5 h-5 rounded-full border-2 transition-colors text-[9px] font-bold ${
              pts[idx] ? 'bg-purple-500 border-purple-500 text-white' :
              idx === placingKeypoint ? 'border-purple-500 animate-pulse text-purple-400' :
              'border-gray-600 text-gray-500 hover:border-gray-400'
            }`}
          >{idx + 1}</button>
        ))}
        <span className="text-xs text-gray-500 ml-1">{pointCount}/6</span>
      </div>

      {/* Current action */}
      {placingKeypoint >= 0 && placingKeypoint < TABLE_KEYPOINT_COUNT && (
        <div className="text-xs text-purple-400 bg-purple-400/10 px-2 py-1.5 rounded animate-pulse">
          Click point {placingKeypoint + 1} of 6...
        </div>
      )}
      {placingKeypoint < 0 && pointCount === 6 && (
        <div className="text-xs text-green-400">✓ All 6 points placed</div>
      )}

      {/* Ball marking */}
      <button
        className={`w-full px-2 py-1.5 rounded text-xs font-medium transition-colors ${
          placingBall
            ? 'bg-yellow-500/20 text-yellow-400 ring-2 ring-yellow-500 ring-offset-1 ring-offset-gray-900 animate-pulse'
            : hasBallLabel
              ? 'bg-yellow-500/10 text-yellow-400'
              : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
        }`}
        onClick={onStartBallPlacing}
      >
        {placingBall ? 'Click ball position...' : hasBallLabel ? '● Ball marked' : '○ Mark ball'}
      </button>

      {/* Actions */}
      <div className="flex gap-1">
        <button
          className="flex items-center gap-1 px-2 py-1 rounded text-xs text-gray-500 hover:text-gray-300 hover:bg-gray-800 transition-colors"
          onClick={onCopyFromNearest}
          title="Copy points from nearest labeled frame"
        >
          <Copy size={10} />
          Copy nearest
        </button>
      </div>

      {/* Progress */}
      <div className="space-y-1">
        <div className="flex justify-between text-xs text-gray-500">
          <span>Labeled frames</span>
          <span>{labelCount}</span>
        </div>
        <div className="w-full h-1.5 bg-gray-800 rounded-full overflow-hidden">
          <div
            className="h-full bg-purple-500 rounded-full transition-all"
            style={{ width: `${(labelCount / Math.max(totalFrames, 1)) * 100}%` }}
          />
        </div>
      </div>

      {/* Diagram + shortcuts */}
      <div className="text-xs text-gray-600 font-mono leading-tight">
        <div>1 ——— 2 &nbsp;(far)</div>
        <div>|&nbsp; 5—6 &nbsp;|&nbsp; (net)</div>
        <div>4 ——— 3 &nbsp;(near)</div>
      </div>
      <div className="text-xs text-gray-600 space-y-0.5">
        <div>Click — place next point</div>
        <div>Right-click — reset frame</div>
        <div>Shift+→/← — skip 100 frames</div>
      </div>

      {/* Export */}
      {labelCount > 0 && (
        <button
          className="flex items-center gap-1.5 w-full px-2.5 py-1.5 rounded text-xs bg-purple-600 hover:bg-purple-500 transition-colors text-white justify-center"
          onClick={onExportTableLabels}
        >
          <Download size={12} />
          Export ({labelCount})
        </button>
      )}
    </div>
  )
}
