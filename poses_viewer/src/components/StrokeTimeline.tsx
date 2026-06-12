export interface TimelineEntry {
  kind: 'rep' | 'forward-dropped' | 'raw-dropped' | 'locomotion-dropped'
  startMs: number
  peakMs: number
  endMs: number
  label: string
}

interface Props {
  entries: TimelineEntry[]
  durationMs: number
  currentMs: number
  onSeek: (ms: number) => void
  selectedIndex?: number | null
  onSelect?: (index: number) => void
}

const BAND_CLASS: Record<TimelineEntry['kind'], string> = {
  'rep': 'bg-emerald-500/80 hover:bg-emerald-400',
  'forward-dropped': 'bg-amber-500/70 hover:bg-amber-400',
  'raw-dropped': 'bg-neutral-500/50 hover:bg-neutral-400',
  'locomotion-dropped': 'bg-rose-500/80 hover:bg-rose-400',
}

export function StrokeTimeline({ entries, durationMs, currentMs, onSeek, selectedIndex, onSelect }: Props) {
  if (durationMs <= 0) return null
  const pct = (ms: number) => `${(Math.min(ms, durationMs) / durationMs) * 100}%`

  return (
    <div
      className="relative h-12 bg-neutral-800 rounded overflow-hidden cursor-pointer select-none"
      onClick={e => {
        const rect = e.currentTarget.getBoundingClientRect()
        onSeek(((e.clientX - rect.left) / rect.width) * durationMs)
      }}
    >
      {entries.map((en, i) => (
        <div
          key={i}
          title={en.label}
          className={`absolute top-1 bottom-1 rounded-sm ${BAND_CLASS[en.kind]}${selectedIndex === i ? ' ring-2 ring-white z-10' : ''}`}
          style={{ left: pct(en.startMs), width: `max(calc(${pct(en.endMs)} - ${pct(en.startMs)}), 3px)` }}
          onClick={ev => {
            ev.stopPropagation()
            if (onSelect) onSelect(i)
            else onSeek(en.peakMs)
          }}
        >
          {/* peak tick */}
          <div
            className="absolute top-0 bottom-0 w-px bg-white/90"
            style={{ left: `${((en.peakMs - en.startMs) / Math.max(en.endMs - en.startMs, 1)) * 100}%` }}
          />
        </div>
      ))}
      {/* playhead */}
      <div className="absolute top-0 bottom-0 w-0.5 bg-red-500 pointer-events-none" style={{ left: pct(currentMs) }} />
    </div>
  )
}
