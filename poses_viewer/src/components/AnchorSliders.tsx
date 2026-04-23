import { useLayoutEffect, useRef, useState } from 'react'
import type { PoseAnchor, AnchorPhase } from '../drill/PoseAnchor'
import { ANCHOR_PARAM_GROUPS } from '../drill/PoseAnchor'

interface Props {
  activePhase: AnchorPhase
  onPhaseChange: (phase: AnchorPhase) => void
  anchor: PoseAnchor
  onChange: (next: PoseAnchor) => void
  onReset: () => void
  /** When provided, wraps matching rows in a yellow ring and scrolls the
   *  first match into view. Used by MannequinEditor to surface the sliders
   *  that rotate the currently selected joint. */
  highlightedParams?: readonly (keyof PoseAnchor)[]
  /** When true, hides the START/END phase buttons — used by single-anchor
   *  editors where the phase concept doesn't apply. */
  hidePhaseSelector?: boolean
  /** Display name of the currently-selected joint (e.g. "правий лікоть").
   *  When present, the per-row copy button for any highlighted row prefixes
   *  the joint name so the clipboard carries both the joint and the param. */
  selectedJointName?: string
  /** English joint id of the currently-selected joint (e.g. "rightElbow").
   *  When present alongside selectedJointName, the highlighted row copy
   *  emits `${id} (${name}) · ${param}: ${value}` so chat references carry
   *  a canonical code identifier plus the Ukrainian label for humans. */
  selectedJointId?: string
}

export default function AnchorSliders({
  activePhase,
  onPhaseChange,
  anchor,
  onChange,
  onReset,
  highlightedParams,
  hidePhaseSelector = false,
  selectedJointName,
  selectedJointId,
}: Props) {
  const setKey = (k: keyof PoseAnchor, v: number) => {
    onChange({ ...anchor, [k]: v })
  }

  // Stable refs per slider key so scrollIntoView doesn't fight re-renders.
  const rowRefs = useRef<Record<string, HTMLDivElement | null>>({})

  // Tracks which row just got copied so the button can flash a checkmark.
  const [copiedKey, setCopiedKey] = useState<keyof PoseAnchor | null>(null)
  // When the row belongs to the selected joint, prefix with the joint reference
  // so the clipboard carries both the joint and the param. Prefer the English id
  // for code-side unambiguity and append the Ukrainian name for human context.
  const jointPrefix = (): string | null => {
    if (selectedJointId && selectedJointName) return `${selectedJointId} (${selectedJointName})`
    if (selectedJointId) return selectedJointId
    if (selectedJointName) return selectedJointName
    return null
  }

  const copyRow = (k: keyof PoseAnchor, value: number, step: number, isJointParam: boolean) => {
    const formatted = step >= 1 ? value.toFixed(0) : value.toFixed(2)
    const prefix = isJointParam ? jointPrefix() : null
    const text = prefix ? `${prefix} · ${k}: ${formatted}` : `${k}: ${formatted}`
    void navigator.clipboard.writeText(text).then(() => {
      setCopiedKey(k)
      setTimeout(() => setCopiedKey(prev => (prev === k ? null : prev)), 900)
    })
  }

  // When the selection changes, bring the first highlighted row into view.
  // Running in useLayoutEffect avoids a visible flicker between the DOM
  // update and the scroll.
  useLayoutEffect(() => {
    if (!highlightedParams || highlightedParams.length === 0) return
    const first = highlightedParams[0]
    const el = rowRefs.current[first]
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [highlightedParams])

  const isHighlighted = (k: keyof PoseAnchor): boolean =>
    highlightedParams?.includes(k) ?? false

  return (
    <div className="flex flex-col gap-3 min-w-72 max-h-[85vh] overflow-y-auto pr-1">
      {!hidePhaseSelector && (
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
      )}

      <div className="flex flex-col gap-3">
        {ANCHOR_PARAM_GROUPS.map(group => (
          <div key={group.name} className="flex flex-col gap-1.5">
            <div className="text-[11px] uppercase tracking-wider text-gray-500 border-b border-gray-800 pb-0.5">
              {group.name}
            </div>
            <div className="flex flex-col gap-2 pl-1">
              {group.params.map(spec => {
                // ANCHOR_PARAM_SPECS only contains number-valued keys by
                // construction; cast keeps the branch-free arithmetic below
                // without threading a runtime guard through every row.
                const value = anchor[spec.key] as number
                const displayVal = spec.step >= 1
                  ? value.toFixed(0)
                  : value.toFixed(2)
                const highlighted = isHighlighted(spec.key)
                return (
                  <div
                    key={spec.key}
                    ref={el => { rowRefs.current[spec.key] = el }}
                    className={
                      'flex flex-col gap-0.5 rounded transition-colors ' +
                      (highlighted
                        ? 'ring-2 ring-yellow-400/60 bg-yellow-400/10 px-1.5 py-1'
                        : '')
                    }
                  >
                    <div className="flex justify-between items-center text-xs text-gray-400">
                      <span className={highlighted ? 'text-yellow-200 font-medium' : ''}>
                        {spec.label}
                      </span>
                      <div className="flex items-center gap-1.5">
                        <span className="font-mono text-gray-200">{displayVal}</span>
                        <button
                          type="button"
                          onClick={() => copyRow(spec.key, value, spec.step, highlighted)}
                          title={(() => {
                            const prefix = highlighted ? jointPrefix() : null
                            return prefix
                              ? `Copy "${prefix} · ${spec.key}: ${displayVal}"`
                              : `Copy "${spec.key}: ${displayVal}"`
                          })()}
                          className={
                            'px-1 py-0.5 rounded text-[10px] font-mono transition-colors ' +
                            (copiedKey === spec.key
                              ? 'bg-green-600/70 text-white'
                              : 'bg-gray-800 text-gray-400 hover:bg-gray-700 hover:text-gray-200')
                          }
                        >
                          {copiedKey === spec.key ? '✓' : '⎘'}
                        </button>
                      </div>
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
