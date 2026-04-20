import type { PoseAnchor, AnchorPhase } from '../drill/PoseAnchor'
import { ANCHOR_PARAM_GROUPS } from '../drill/PoseAnchor'

interface Props {
  activePhase: AnchorPhase
  onPhaseChange: (phase: AnchorPhase) => void
  anchor: PoseAnchor
  onChange: (next: PoseAnchor) => void
  onReset: () => void
}

export default function AnchorSliders({
  activePhase,
  onPhaseChange,
  anchor,
  onChange,
  onReset,
}: Props) {
  const setKey = (k: keyof PoseAnchor, v: number) => {
    onChange({ ...anchor, [k]: v })
  }

  return (
    <div className="flex flex-col gap-3 min-w-72">
      <div className="flex gap-2">
        <button
          className={
            'flex-1 px-3 py-1.5 rounded text-sm font-medium ' +
            (activePhase === 'START'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-700 text-gray-300 hover:bg-gray-600')
          }
          onClick={() => onPhaseChange('START')}
        >
          START
        </button>
        <button
          className={
            'flex-1 px-3 py-1.5 rounded text-sm font-medium ' +
            (activePhase === 'END'
              ? 'bg-red-600 text-white'
              : 'bg-gray-700 text-gray-300 hover:bg-gray-600')
          }
          onClick={() => onPhaseChange('END')}
        >
          END
        </button>
        <button
          className="px-2 py-1.5 rounded text-xs bg-gray-800 text-gray-400 hover:bg-gray-700"
          onClick={onReset}
          title="Reset to neutral pose"
        >
          Reset
        </button>
      </div>

      <div className="flex flex-col gap-3">
        {ANCHOR_PARAM_GROUPS.map(group => (
          <div key={group.name} className="flex flex-col gap-1.5">
            <div className="text-[11px] uppercase tracking-wider text-gray-500 border-b border-gray-800 pb-0.5">
              {group.name}
            </div>
            <div className="flex flex-col gap-2 pl-1">
              {group.params.map(spec => {
                const value = anchor[spec.key]
                const displayVal = spec.step >= 1
                  ? value.toFixed(0)
                  : value.toFixed(2)
                return (
                  <div key={spec.key} className="flex flex-col gap-0.5">
                    <div className="flex justify-between text-xs text-gray-400">
                      <span>{spec.label}</span>
                      <span className="font-mono text-gray-200">{displayVal}</span>
                    </div>
                    <input
                      type="range"
                      min={spec.min}
                      max={spec.max}
                      step={spec.step}
                      value={value}
                      onChange={e => setKey(spec.key, parseFloat(e.target.value))}
                      className="w-full"
                    />
                  </div>
                )
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
