/**
 * Shared primitive UI controls used by both VoiceStyleEditor and the Налаштування panel.
 * Extracted from VoiceStyleEditor so the StrokesPage Налаштування fieldset can reuse them.
 */

/** Render a seconds value with up to 2 decimals + unit, trailing zeros trimmed (2.5 → "2.5 с"). */
export const secFmt = (v: number) => `${Math.round(v * 100) / 100} с`

export function Slider(props: {
  label: string
  min: number
  max: number
  step: number
  value: number
  onChange: (v: number) => void
  hint?: string
  fmt?: (v: number) => string
}) {
  return (
    <label className="flex items-center gap-2" title={props.hint}>
      <span className={`w-48 ${props.hint ? 'cursor-help decoration-dotted underline underline-offset-2 decoration-neutral-600' : ''}`}>{props.label}</span>
      <input type="range" min={props.min} max={props.max} step={props.step} value={props.value}
        onChange={e => props.onChange(Number(e.target.value))} className="flex-1" />
      <span className="w-16 text-right tabular-nums">{props.fmt ? props.fmt(props.value) : props.value}</span>
    </label>
  )
}

export function Toggle({ label, value, onChange, hint }: {
  label: string
  value: boolean
  onChange: (v: boolean) => void
  hint?: string
}) {
  return (
    <label className="flex items-center gap-2" title={hint}>
      <input type="checkbox" checked={value} onChange={e => onChange(e.target.checked)} />
      <span className={hint ? 'cursor-help decoration-dotted underline underline-offset-2 decoration-neutral-600' : ''}>{label}</span>
    </label>
  )
}
