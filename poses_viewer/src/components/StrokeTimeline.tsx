export interface TimelineEntry {
  kind: 'rep' | 'forward-dropped' | 'raw-dropped'
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
}

export function StrokeTimeline({ entries, durationMs }: Props) {
  return (
    <div className="h-10 bg-neutral-800 rounded text-xs text-neutral-400 flex items-center px-2">
      {entries.length} ударів · {Math.round(durationMs / 1000)} с (таймлайн — Task 7)
    </div>
  )
}
