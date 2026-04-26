import { useLayoutEffect, useRef, useState } from 'react'
import type { PoseAnchor, AnchorPhase, AnchorParamSpec } from '../drill/PoseAnchor'
import { ANCHOR_PARAM_GROUPS } from '../drill/PoseAnchor'
import { clampRightShoulder, clampRightShoulderElevation, type ShoulderActiveKey } from '../drill/shoulderClamp'

interface Props {
  activePhase: AnchorPhase
  onPhaseChange: (phase: AnchorPhase) => void
  anchor: PoseAnchor
  onChange: (next: PoseAnchor) => void
  onReset: () => void
  highlightedParams?: readonly (keyof PoseAnchor)[]
  hidePhaseSelector?: boolean
  selectedJointName?: string
  selectedJointId?: string
  /** Returns effective limits for a param row. Falls back to spec min/max when absent. */
  getLimits?: (jointId: string, paramKey: string, specMin: number, specMax: number) => { min: number; max: number }
  /** Persists a custom limit override for a joint×param pair. */
  setLimits?: (jointId: string, paramKey: string, min: number, max: number) => void
  /** Removes the override for a joint×param pair, reverting to spec defaults. */
  resetLimits?: (jointId: string, paramKey: string) => void
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
  getLimits,
  setLimits,
  resetLimits,
}: Props) {
  const specIdentity = (spec: AnchorParamSpec): string =>
    spec.kind === 'direct' ? (spec.key as string) : spec.id

  const specRead = (spec: AnchorParamSpec, a: PoseAnchor): number =>
    spec.kind === 'direct' ? (a[spec.key] as number) : spec.read(a)

  const specWrite = (spec: AnchorParamSpec, a: PoseAnchor, v: number): PoseAnchor => {
    if (spec.kind === 'direct') {
      const next: PoseAnchor = { ...a, [spec.key]: v }
      if (spec.key === 'rightShoulderAngleDeg' || spec.key === 'rightShoulderAbductionDeg') {
        const activeKey = spec.key as ShoulderActiveKey
        const c1 = clampRightShoulder(
          next.rightShoulderAngleDeg,
          next.rightShoulderAbductionDeg,
          activeKey,
        )
        const c2 = clampRightShoulderElevation(c1.flex, c1.abd, activeKey)
        return { ...next, rightShoulderAngleDeg: c2.flex, rightShoulderAbductionDeg: c2.abd }
      }
      return next
    }
    return spec.write(a, v)
  }

  // Stable refs per slider id so scrollIntoView doesn't fight re-renders.
  const rowRefs = useRef<Record<string, HTMLDivElement | null>>({})

  // Tracks which row just got copied so the button can flash a checkmark.
  const [copiedId, setCopiedId] = useState<string | null>(null)
  // When the row belongs to the selected joint, prefix with the joint reference
  // so the clipboard carries both the joint and the param. Prefer the English id
  // for code-side unambiguity and append the Ukrainian name for human context.
  const jointPrefix = (): string | null => {
    if (selectedJointId && selectedJointName) return `${selectedJointId} (${selectedJointName})`
    if (selectedJointId) return selectedJointId
    if (selectedJointName) return selectedJointName
    return null
  }

  const copyRow = (spec: AnchorParamSpec, value: number, isJointParam: boolean) => {
    const id = specIdentity(spec)
    const formatted = spec.step >= 1 ? value.toFixed(0) : value.toFixed(2)
    const prefix = isJointParam ? jointPrefix() : null
    const text = prefix ? `${prefix} · ${id}: ${formatted}` : `${id}: ${formatted}`
    void navigator.clipboard.writeText(text).then(() => {
      setCopiedId(id)
      setTimeout(() => setCopiedId(prev => (prev === id ? null : prev)), 900)
    })
  }

  // When the selection changes, bring the first highlighted row into view.
  // Running in useLayoutEffect avoids a visible flicker between the DOM
  // update and the scroll.
  useLayoutEffect(() => {
    if (!highlightedParams || highlightedParams.length === 0) return
    const first = highlightedParams[0]
    // Find the first spec (direct or computed) that claims this key.
    const allSpecs = ANCHOR_PARAM_GROUPS.flatMap(g => g.params)
    const match = allSpecs.find(s =>
      s.kind === 'direct' ? s.key === first : s.keys.includes(first),
    )
    if (!match) return
    const el = rowRefs.current[specIdentity(match)]
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [highlightedParams])

  const isHighlighted = (spec: AnchorParamSpec): boolean => {
    if (!highlightedParams) return false
    if (spec.kind === 'direct') return highlightedParams.includes(spec.key)
    return spec.keys.some(k => highlightedParams.includes(k))
  }

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
                const id = specIdentity(spec)
                const value = specRead(spec, anchor)
                const displayVal = spec.step >= 1
                  ? value.toFixed(0)
                  : value.toFixed(2)
                const highlighted = isHighlighted(spec)

                // Compute effective limits — custom override when available, else spec defaults.
                const showLimitEditor = highlighted && !!selectedJointId && !!getLimits
                const effectiveLimits = showLimitEditor
                  ? getLimits!(selectedJointId!, id, spec.min, spec.max)
                  : { min: spec.min, max: spec.max }

                return (
                  <div
                    key={id}
                    ref={el => { rowRefs.current[id] = el }}
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
                          onClick={() => copyRow(spec, value, highlighted)}
                          title={(() => {
                            const prefix = highlighted ? jointPrefix() : null
                            return prefix
                              ? `Copy "${prefix} · ${id}: ${displayVal}"`
                              : `Copy "${id}: ${displayVal}"`
                          })()}
                          className={
                            'px-1 py-0.5 rounded text-[10px] font-mono transition-colors ' +
                            (copiedId === id
                              ? 'bg-green-600/70 text-white'
                              : 'bg-gray-800 text-gray-400 hover:bg-gray-700 hover:text-gray-200')
                          }
                        >
                          {copiedId === id ? '✓' : '⎘'}
                        </button>
                      </div>
                    </div>
                    <input
                      type="range"
                      min={effectiveLimits.min}
                      max={effectiveLimits.max}
                      step={spec.step}
                      value={Math.max(effectiveLimits.min, Math.min(effectiveLimits.max, value))}
                      onChange={e => onChange(specWrite(spec, anchor, parseFloat(e.target.value)))}
                      className="w-full"
                    />
                    {showLimitEditor && (
                      <div className="flex items-center gap-1.5 mt-0.5">
                        <span className="text-[10px] text-gray-500">min</span>
                        <input
                          type="number"
                          step={spec.step}
                          value={effectiveLimits.min}
                          onChange={e => {
                            const newMin = parseFloat(e.target.value)
                            if (isNaN(newMin)) return
                            const newMax = Math.max(newMin, effectiveLimits.max)
                            setLimits!(selectedJointId!, id, newMin, newMax)
                            const clamped = Math.max(newMin, Math.min(newMax, value))
                            if (clamped !== value) onChange(specWrite(spec, anchor, clamped))
                          }}
                          className="w-16 text-[10px] font-mono text-gray-300 bg-gray-800 border border-gray-700 rounded px-1 py-0.5"
                        />
                        <span className="text-[10px] text-gray-500">max</span>
                        <input
                          type="number"
                          step={spec.step}
                          value={effectiveLimits.max}
                          onChange={e => {
                            const newMax = parseFloat(e.target.value)
                            if (isNaN(newMax)) return
                            const newMin = Math.min(effectiveLimits.min, newMax)
                            setLimits!(selectedJointId!, id, newMin, newMax)
                            const clamped = Math.max(newMin, Math.min(newMax, value))
                            if (clamped !== value) onChange(specWrite(spec, anchor, clamped))
                          }}
                          className="w-16 text-[10px] font-mono text-gray-300 bg-gray-800 border border-gray-700 rounded px-1 py-0.5"
                        />
                        <button
                          type="button"
                          title="Reset limits to spec defaults"
                          onClick={() => {
                            resetLimits!(selectedJointId!, id)
                            const clamped = Math.max(spec.min, Math.min(spec.max, value))
                            if (clamped !== value) onChange(specWrite(spec, anchor, clamped))
                          }}
                          className="px-1 py-0.5 rounded text-[10px] bg-gray-800 text-gray-500 hover:text-gray-200 hover:bg-gray-700"
                        >
                          ↺
                        </button>
                      </div>
                    )}
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
