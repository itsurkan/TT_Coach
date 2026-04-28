import { Check, X, EyeOff, Download } from 'lucide-react'
import { FrameLabel, LabelStatus, LabelsData } from '../types'

interface Props {
  frameIndex: number
  totalFrames: number
  currentLabel: FrameLabel | null
  hasBallDetection: boolean
  labels: Record<number, FrameLabel>
  /** True when waiting for the user to click the canvas to place the ball */
  placingBall: boolean
  onLabel: (label: LabelStatus) => void
  onClearLabel: () => void
  onExport: () => void
}

const LABEL_COLORS: Record<LabelStatus, string> = {
  correct: '#22c55e',  // green-500
  wrong: '#ef4444',    // red-500
  no_ball: '#6b7280',  // gray-500
}

const LABEL_NAMES: Record<LabelStatus, string> = {
  correct: 'Correct',
  wrong: 'Wrong',
  no_ball: 'No Ball',
}

export default function LabelPanel({
  frameIndex,
  totalFrames,
  currentLabel,
  hasBallDetection,
  labels,
  placingBall,
  onLabel,
  onClearLabel,
  onExport,
}: Props) {
  const labelCount = Object.keys(labels).length
  const counts = { correct: 0, wrong: 0, no_ball: 0 }
  for (const l of Object.values(labels)) counts[l.label]++

  const btn = (status: LabelStatus, icon: React.ReactNode, shortcut: string) => {
    const active = currentLabel?.label === status
    return (
      <button
        key={status}
        className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded text-xs font-medium transition-colors
          ${active
            ? 'ring-2 ring-offset-1 ring-offset-gray-900'
            : 'hover:bg-gray-800'}`}
        style={{
          background: active ? LABEL_COLORS[status] + '30' : undefined,
          color: active ? LABEL_COLORS[status] : '#9ca3af',
          outlineColor: active ? LABEL_COLORS[status] : undefined,
        }}
        onClick={() => onLabel(status)}
        title={`${LABEL_NAMES[status]} (${shortcut})`}
      >
        {icon}
        {LABEL_NAMES[status]}
      </button>
    )
  }

  return (
    <div className="space-y-3">
      <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Label Ball</div>

      {/* Current frame status */}
      {currentLabel ? (
        <div className="flex items-center gap-1.5 text-xs font-mono">
          <span className="w-2 h-2 rounded-full shrink-0"
            style={{ background: LABEL_COLORS[currentLabel.label] }} />
          <span style={{ color: LABEL_COLORS[currentLabel.label] }}>
            {LABEL_NAMES[currentLabel.label].toUpperCase()}
          </span>
          <button
            className="ml-auto text-gray-600 hover:text-gray-400 text-xs"
            onClick={onClearLabel}
            title="Clear label"
          >
            clear
          </button>
        </div>
      ) : (
        <div className="text-xs text-gray-600 font-mono">unlabeled</div>
      )}

      {placingBall && (
        <div className="text-xs text-amber-400 bg-amber-400/10 px-2 py-1.5 rounded animate-pulse">
          Click on the ball position...
        </div>
      )}

      {/* Label buttons */}
      <div className="grid grid-cols-3 gap-1.5">
        {btn('correct', <Check size={12} />, 'C')}
        {btn('wrong', <X size={12} />, 'W')}
        {btn('no_ball', <EyeOff size={12} />, 'N')}
      </div>

      {/* Corrected position */}
      {currentLabel?.correctedX != null && (
        <div className="text-xs font-mono text-gray-400">
          pos: ({currentLabel.correctedX.toFixed(3)}, {currentLabel.correctedY?.toFixed(3)})
        </div>
      )}

      {/* Progress */}
      <div className="space-y-1">
        <div className="flex justify-between text-xs text-gray-500">
          <span>Progress</span>
          <span>{labelCount} / {totalFrames}</span>
        </div>
        <div className="w-full h-1.5 bg-gray-800 rounded-full overflow-hidden">
          <div
            className="h-full bg-blue-500 rounded-full transition-all"
            style={{ width: `${(labelCount / Math.max(totalFrames, 1)) * 100}%` }}
          />
        </div>
        <div className="flex gap-3 text-xs font-mono">
          <span style={{ color: LABEL_COLORS.correct }}>{counts.correct}</span>
          <span style={{ color: LABEL_COLORS.wrong }}>{counts.wrong}</span>
          <span style={{ color: LABEL_COLORS.no_ball }}>{counts.no_ball}</span>
        </div>
      </div>

      {/* Shortcuts */}
      <div className="text-xs text-gray-600 space-y-0.5">
        <div>C — correct</div>
        <div>W — wrong (then click ball)</div>
        <div>N — no ball</div>
        <div>Esc — cancel click</div>
      </div>

      {/* Export */}
      {labelCount > 0 && (
        <button
          className="flex items-center gap-1.5 w-full px-2.5 py-1.5 rounded text-xs bg-blue-600 hover:bg-blue-500 transition-colors text-white justify-center"
          onClick={onExport}
        >
          <Download size={12} />
          Export Labels ({labelCount})
        </button>
      )}
    </div>
  )
}

export { LABEL_COLORS }
