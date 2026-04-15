import { SkipBack, ChevronLeft, Play, Pause, ChevronRight, SkipForward } from 'lucide-react'
import { ContactsData, FrameLabel, TableFrameLabel } from '../types'
import { LABEL_COLORS } from './LabelPanel'

interface Props {
  frameIndex: number
  totalFrames: number
  playing: boolean
  onFrameChange: (i: number) => void
  onPlayPause: () => void
  onFirst: () => void
  onLast: () => void
  contacts?: ContactsData | null
  labels?: Record<number, FrameLabel>
  tableLabels?: Record<number, TableFrameLabel>
}

export default function FrameControls({
  frameIndex,
  totalFrames,
  playing,
  onFrameChange,
  onPlayPause,
  onFirst,
  onLast,
  contacts,
  labels,
  tableLabels,
}: Props) {
  const btn  = 'p-1.5 rounded hover:bg-gray-800 transition-colors disabled:opacity-30 text-gray-300'
  const jump = (delta: number) => onFrameChange(Math.max(0, Math.min(totalFrames - 1, frameIndex + delta)))

  return (
    <div className="border-t border-gray-800 bg-gray-950 px-4 py-3 flex flex-col gap-2 shrink-0">
      {/* Scrubber with contact markers */}
      <div className="relative w-full">
        {/* Label markers — bottom half of scrubber */}
        {labels && Object.keys(labels).length > 0 && totalFrames > 1 && (
          <div className="absolute inset-x-0 bottom-0 h-1/2 pointer-events-none z-0">
            {Object.values(labels).map((l) => {
              const pct = (l.frameIndex / (totalFrames - 1)) * 100
              return (
                <div
                  key={l.frameIndex}
                  className="absolute w-1 rounded-full"
                  style={{
                    left: `${pct}%`,
                    height: '100%',
                    background: LABEL_COLORS[l.label],
                    opacity: 0.7,
                  }}
                  title={`${l.label} @ frame ${l.frameIndex}`}
                />
              )
            })}
          </div>
        )}
        {/* Table label markers — purple ticks */}
        {tableLabels && Object.keys(tableLabels).length > 0 && totalFrames > 1 && (
          <div className="absolute inset-x-0 top-0 h-full pointer-events-none z-0">
            {Object.values(tableLabels).map((tl) => {
              const pct = (tl.frameIndex / (totalFrames - 1)) * 100
              return (
                <div
                  key={`tbl-${tl.frameIndex}`}
                  className="absolute w-1.5 rounded-full"
                  style={{
                    left: `${pct}%`,
                    height: '100%',
                    background: '#a855f7',
                    opacity: 0.6,
                  }}
                  title={`Table label @ frame ${tl.frameIndex} (${Object.keys(tl.points).length}/6 pts)`}
                />
              )
            })}
          </div>
        )}
        {/* Contact markers — top half of scrubber */}
        {contacts && contacts.contacts.length > 0 && totalFrames > 1 && (
          <div className="absolute inset-x-0 top-0 h-1/2 pointer-events-none z-0 flex items-center">
            {contacts.contacts.map((c, i) => {
              const pct = (c.frameIndex / (totalFrames - 1)) * 100
              const color = c.type === 'racket' ? '#a78bfa' : '#fb923c'
              return (
                <div
                  key={i}
                  className="absolute w-0.5 opacity-70 rounded-full"
                  style={{ left: `${pct}%`, height: '100%', background: color }}
                  title={`${c.type} #${i + 1} @ ${c.timestampMs}ms (${(c.confidence * 100).toFixed(0)}%)`}
                />
              )
            })}
          </div>
        )}
        <input
          type="range"
          min={0}
          max={totalFrames - 1}
          value={frameIndex}
          onChange={e => onFrameChange(Number(e.target.value))}
          className="relative w-full h-1 accent-blue-500 cursor-pointer z-10"
        />
      </div>

      {/* Jump buttons row */}
      <div className="flex items-center justify-center gap-0.5 text-xs font-mono">
        {([-20, -10, -5, -1] as const).map(d => (
          <button
            key={d}
            className={btn + ' px-2 py-1 text-xs'}
            onClick={() => jump(d)}
            disabled={frameIndex === 0}
            title={`${d} frames`}
          >
            {d}
          </button>
        ))}

        {/* Core controls */}
        <button className={btn + ' ml-1'} onClick={onFirst} disabled={frameIndex === 0} title="First frame">
          <SkipBack size={14} />
        </button>
        <button className={btn} onClick={() => jump(-1)} disabled={frameIndex === 0} title="Previous (←)">
          <ChevronLeft size={14} />
        </button>
        <button
          className="p-2 rounded bg-blue-600 hover:bg-blue-500 transition-colors mx-1"
          onClick={onPlayPause}
          title="Play / Pause (Space)"
        >
          {playing ? <Pause size={14} /> : <Play size={14} />}
        </button>
        <button className={btn} onClick={() => jump(1)} disabled={frameIndex === totalFrames - 1} title="Next (→)">
          <ChevronRight size={14} />
        </button>
        <button className={btn + ' mr-1'} onClick={onLast} disabled={frameIndex === totalFrames - 1} title="Last frame">
          <SkipForward size={14} />
        </button>

        {([1, 5, 10, 20] as const).map(d => (
          <button
            key={d}
            className={btn + ' px-2 py-1 text-xs'}
            onClick={() => jump(d)}
            disabled={frameIndex === totalFrames - 1}
            title={`+${d} frames`}
          >
            +{d}
          </button>
        ))}
      </div>
    </div>
  )
}
