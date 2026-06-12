import { useEffect, useMemo, useRef, useState } from 'react'
import { useSpokenFeedback, FeedbackMode } from './useSpokenFeedback'
import { Handedness } from '../drill2d/types'
import { parsePoseV2, PoseSequence2D } from '../drill2d/parsePoseV2'
import { countStrokes } from '../drill2d/countStrokes'
import { DETECTOR_DEFAULTS } from '../drill2d/strokeDetector2d'
import { StrokeTimeline, TimelineEntry } from './StrokeTimeline'
import { analyzeDrill, DrillAnalysisReport } from '../drill2d/analyzeDrill'
import { REFERENCE_STANDARDS } from '../drill2d/referenceStandard'
import { ALL_KEYS } from '../drill2d/drillMetrics'
import { DrillResultsTable } from './DrillResultsTable'
import { loopBackTarget } from './strokeLoop'

interface VideoItem { name: string; ext: string }

export default function StrokesPage() {
  const [videos, setVideos] = useState<VideoItem[]>([])
  const [base, setBase] = useState('')
  const [seq, setSeq] = useState<PoseSequence2D | null>(null)
  const [error, setError] = useState('')
  const [handedness, setHandedness] = useState<Handedness>('right')
  const [yawDeg, setYawDeg] = useState(0)
  const [minPeakSpeed, setMinPeakSpeed] = useState(DETECTOR_DEFAULTS.minPeakSpeed)
  const [minPeakGapMs, setMinPeakGapMs] = useState(DETECTOR_DEFAULTS.minPeakGapMs)
  const [currentMs, setCurrentMs] = useState(0)
  const [selectedIdx, setSelectedIdx] = useState<number | null>(null)
  const [loop, setLoop] = useState(false)
  const [drillType, setDrillType] = useState('forehand_drive')
  const [enabledMetrics, setEnabledMetrics] = useState<Set<string>>(new Set(ALL_KEYS))
  const [feedbackMode, setFeedbackMode] = useState<FeedbackMode>('audio')
  const videoRef = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    fetch('/api/videos').then(r => (r.ok ? r.json() : [])).then(setVideos).catch(() => setVideos([]))
  }, [])

  useEffect(() => {
    if (!base) { setSeq(null); return }
    setError('')
    setSeq(null)
    setSelectedIdx(null)
    fetch(`/videos/${base}/${base}_poses_rtm.json`)
      .then(r => {
        if (!r.ok) throw new Error(`немає ${base}_poses_rtm.json — спершу запусти export_poses_rtmpose.py`)
        return r.json()
      })
      .then(json => setSeq(parsePoseV2(json)))
      .catch(e => setError(String(e.message ?? e)))
  }, [base])

  const result = useMemo(() => {
    if (!seq) return null
    try {
      return countStrokes(seq, {
        handedness,
        cameraYawDeg: yawDeg,
        detector: { minPeakSpeed, minPeakGapMs },
      })
    } catch {
      return null // xScaleFor throws beyond ±60°; inputs are clamped, but stay safe
    }
  }, [seq, handedness, yawDeg, minPeakSpeed, minPeakGapMs])

  const report = useMemo<DrillAnalysisReport | null>(() => {
    if (!seq) return null
    const standard = REFERENCE_STANDARDS[drillType]
    if (!standard) return null
    try {
      return analyzeDrill(seq, {
        handedness,
        drillType,
        standard,
        enabledMetrics,
        cameraYawDeg: yawDeg,
        detector: { minPeakSpeed, minPeakGapMs },
      })
    } catch {
      return null
    }
  }, [seq, handedness, yawDeg, minPeakSpeed, minPeakGapMs, drillType, enabledMetrics])

  const feed = useMemo(() => report?.feedback ?? [], [report])
  const spoken = useSpokenFeedback(feed, feedbackMode)

  const entries = useMemo<TimelineEntry[]>(() => {
    if (!seq || !result) return []
    const repPeaks = new Set(result.reps.map(s => s.peakFrame))
    const fwdPeaks = new Set(result.forwardStrokes.map(s => s.peakFrame))
    const ms = (frame: number) => seq.frames[frame]?.timestampMs ?? frame * seq.intervalMs
    return result.rawStrokes.map((s, i) => ({
      kind: repPeaks.has(s.peakFrame) ? 'rep' : fwdPeaks.has(s.peakFrame) ? 'forward-dropped' : 'raw-dropped',
      startMs: ms(s.startFrame),
      peakMs: ms(s.peakFrame),
      endMs: ms(s.endFrame),
      label: `#${i + 1} · ${s.peakSpeed.toFixed(1)} торс/с`,
    }))
  }, [seq, result])

  // Selection indexes into entries; drop it (and stop looping) when the knobs rebuild the stroke list.
  useEffect(() => { setSelectedIdx(null); setLoop(false) }, [handedness, yawDeg, minPeakSpeed, minPeakGapMs, drillType, enabledMetrics])

  const videoEntry = videos.find(v => v.name === base)
  const videoUrl = videoEntry?.ext ? `/videos/${base}/${base}${videoEntry.ext}` : null

  const seek = (ms: number) => {
    const v = videoRef.current
    if (v) v.currentTime = ms / 1000
    spoken.reset(ms)
  }

  // ←/→ = ±1 кадр, Shift = ±10 кадрів (пауза перед кроком, щоб кадр не "втікав")
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
      const t = e.target as HTMLElement | null
      if (t && ['INPUT', 'SELECT', 'TEXTAREA'].includes(t.tagName)) return
      const v = videoRef.current
      if (!v || !seq) return
      e.preventDefault()
      v.pause()
      const stepSec = (seq.intervalMs * (e.shiftKey ? 10 : 1)) / 1000
      const next = e.key === 'ArrowRight' ? v.currentTime + stepSec : v.currentTime - stepSec
      v.currentTime = Math.min(Math.max(next, 0), seq.videoDurationMs / 1000)
      setCurrentMs(v.currentTime * 1000)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [seq])

  const selected = selectedIdx !== null ? entries[selectedIdx] : null
  const currentFrame = seq ? Math.min(Math.round(currentMs / seq.intervalMs), seq.frames.length - 1) : 0

  return (
    <div className="min-h-screen bg-neutral-900 text-neutral-100 p-4">
      <div className="mx-auto w-full max-w-4xl space-y-4">
      <header className="flex items-center gap-4 flex-wrap">
        <a
          href="#/main"
          className="px-3 py-1.5 rounded text-sm bg-sky-800 hover:bg-sky-700 transition-colors text-sky-100"
        >
          ← Poses Viewer
        </a>
        <h1 className="text-lg font-semibold">Симулятор ефективності вправи</h1>
        <label className="flex items-center gap-2">
          <span>Відео:</span>
          <select
            className="bg-neutral-800 rounded px-2 py-1"
            value={base}
            onChange={e => setBase(e.target.value)}
          >
            <option value="">— вибери —</option>
            {videos.map(v => <option key={v.name} value={v.name}>{v.name}</option>)}
          </select>
        </label>
      </header>

      {error && <div className="text-red-400">{error}</div>}

      {videoUrl && (
        <div className="space-y-2 max-w-4xl">
          <video
            ref={videoRef}
            src={videoUrl}
            controls
            className="max-h-[55vh] bg-black"
            onTimeUpdate={e => {
              const v = e.currentTarget
              const ms = v.currentTime * 1000
              if (loop && selected) {
                const back = loopBackTarget(ms, selected.startMs, selected.endMs)
                if (back !== null) {
                  v.currentTime = back / 1000
                  setCurrentMs(back)
                  spoken.reset(back) // re-arm cues for the next pass
                  return
                }
              }
              setCurrentMs(ms)
              spoken.onTime(ms)
            }}
          />
          {seq && result && (
            <>
              <StrokeTimeline
                entries={entries}
                durationMs={seq.videoDurationMs}
                currentMs={currentMs}
                onSeek={seek}
                selectedIndex={selectedIdx}
                onSelect={i => {
                  setSelectedIdx(i)
                  setLoop(true) // clicking a stroke loops it
                  seek(entries[i].startMs)
                  videoRef.current?.play()
                }}
              />
              <TimelineLegend />
              {spoken.latest && (
                <div className="bg-sky-900/60 border border-sky-700 rounded p-2 text-sm">
                  🔊 {spoken.latest.message}
                </div>
              )}
              {spoken.log.length > 0 && (
                <details className="text-xs text-neutral-300">
                  <summary>Журнал підказок ({spoken.log.length})</summary>
                  <ul className="mt-1 space-y-0.5">
                    {spoken.log.map((f, i) => (
                      <li key={i}>{(f.timestampMs / 1000).toFixed(1)} с — {f.message}</li>
                    ))}
                  </ul>
                </details>
              )}
              <div className="text-xs text-neutral-400">
                Кадр: <b className="text-neutral-200">{currentFrame}</b> / {seq.frames.length - 1}
                {' · '}{(currentMs / 1000).toFixed(2)} с
                {' · '}←/→ — ±1 кадр, Shift — ±10
              </div>
              {selected && (
                <div className="flex items-center gap-3 flex-wrap text-sm bg-neutral-800 rounded p-2">
                  <span className="font-semibold">{selected.label}</span>
                  <button
                    className="px-2 py-1 rounded bg-neutral-700 hover:bg-neutral-600"
                    onClick={() => seek(selected.startMs)}
                  >
                    Початок руху · {(selected.startMs / 1000).toFixed(2)} с
                  </button>
                  <button
                    className="px-2 py-1 rounded bg-neutral-700 hover:bg-neutral-600"
                    onClick={() => seek(selected.peakMs)}
                  >
                    Пік · {(selected.peakMs / 1000).toFixed(2)} с
                  </button>
                  <button
                    className="px-2 py-1 rounded bg-neutral-700 hover:bg-neutral-600"
                    onClick={() => seek(selected.endMs)}
                  >
                    Кінець · {(selected.endMs / 1000).toFixed(2)} с
                  </button>
                  <button
                    className={`px-2 py-1 rounded ${loop ? 'bg-emerald-700 hover:bg-emerald-600 text-emerald-50' : 'bg-neutral-700 hover:bg-neutral-600'}`}
                    onClick={() => {
                      const next = !loop
                      setLoop(next)
                      if (next) { seek(selected.startMs); videoRef.current?.play() }
                    }}
                    title="Зациклити цей удар (старт → кінець)"
                  >
                    🔁 Цикл: {loop ? 'увімк' : 'вимк'}
                  </button>
                  <button
                    className="px-2 py-1 rounded text-neutral-400 hover:text-neutral-200"
                    onClick={() => { setSelectedIdx(null); setLoop(false) }}
                    title="Зняти вибір"
                  >
                    ✕
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      )}

      {result && (
        <div className="flex gap-6 text-sm">
          <span>Сирі піки: <b>{result.rawStrokes.length}</b></span>
          <span className="text-amber-400">Форвардні: <b>{result.forwardStrokes.length}</b></span>
          <span className="text-emerald-400">Повтори: <b>{result.reps.length}</b></span>
        </div>
      )}

      {report && report.reps.length > 0 && (
        <div className="space-y-2">
          <h2 className="text-sm font-semibold">Аналіз повторів</h2>
          {!report.placementOk && (
            <div className="text-amber-400 text-xs">
              ⚠ Більшість повторів зняті не збоку — підказки приглушені. Виправ кут камери.
            </div>
          )}
          <DrillResultsTable
            reps={report.reps}
            standard={REFERENCE_STANDARDS[drillType]}
            enabledMetrics={enabledMetrics}
            selectedIndex={selectedIdx}
            onSelect={i => {
              setSelectedIdx(i)
              const peakMs = seq ? (seq.frames[report.reps[i].stroke.peakFrame]?.timestampMs ?? 0) : 0
              seek(peakMs)
            }}
          />
        </div>
      )}

      <fieldset className="border border-neutral-700 rounded p-3 max-w-xl space-y-2 text-sm">
        <legend className="px-1">Налаштування</legend>
        <label className="flex items-center gap-2">
          <span className="w-56">Рука:</span>
          <select
            className="bg-neutral-800 rounded px-2 py-1"
            value={handedness}
            onChange={e => setHandedness(e.target.value as Handedness)}
          >
            <option value="right">Права</option>
            <option value="left">Ліва</option>
          </select>
        </label>
        <label className="flex items-center gap-2">
          <span className="w-56">Кут камери (°, вручну — L-25):</span>
          <input
            type="number" min={-60} max={60} step={1}
            className="bg-neutral-800 rounded px-2 py-1 w-20"
            value={yawDeg}
            onChange={e => setYawDeg(Number(e.target.value))}
          />
        </label>
        <label className="flex items-center gap-2">
          <span className="w-56">Поріг швидкості піка (торс/с):</span>
          <input
            type="number" min={0.2} max={5} step={0.1}
            className="bg-neutral-800 rounded px-2 py-1 w-20"
            value={minPeakSpeed}
            onChange={e => setMinPeakSpeed(Number(e.target.value))}
          />
        </label>
        <label className="flex items-center gap-2">
          <span className="w-56">Мін. інтервал між піками (мс):</span>
          <input
            type="number" min={100} max={2000} step={50}
            className="bg-neutral-800 rounded px-2 py-1 w-20"
            value={minPeakGapMs}
            onChange={e => setMinPeakGapMs(Number(e.target.value))}
          />
        </label>
        <p className="text-neutral-400">Клік по смузі — перехід до піка удару.</p>
        <label className="flex items-center gap-2">
          <span className="w-56">Вправа:</span>
          <select
            className="bg-neutral-800 rounded px-2 py-1"
            value={drillType}
            onChange={e => setDrillType(e.target.value)}
          >
            <option value="forehand_drive">Накат справа (forehand drive)</option>
          </select>
        </label>
        <label className="flex items-center gap-2">
          <span className="w-56">Озвучення підказок:</span>
          <select
            className="bg-neutral-800 rounded px-2 py-1"
            value={feedbackMode}
            onChange={e => setFeedbackMode(e.target.value as FeedbackMode)}
          >
            <option value="audio">Голос (за замовч.)</option>
            <option value="text">Лише текст</option>
          </select>
        </label>
        <div className="flex items-start gap-2">
          <span className="w-56">Метрики:</span>
          <div className="flex flex-wrap gap-x-3 gap-y-1">
            {ALL_KEYS.map(k => (
              <label key={k} className="flex items-center gap-1">
                <input
                  type="checkbox"
                  checked={enabledMetrics.has(k)}
                  onChange={e => setEnabledMetrics(prev => {
                    const next = new Set(prev)
                    if (e.target.checked) next.add(k); else next.delete(k)
                    return next
                  })}
                />
                {k}
              </label>
            ))}
          </div>
        </div>
      </fieldset>
      </div>
    </div>
  )
}

/** Color key for the timeline bands (matches StrokeTimeline BAND_CLASS). */
function TimelineLegend() {
  return (
    <div className="flex items-center gap-x-5 gap-y-1 flex-wrap text-xs text-neutral-300">
      <span className="flex items-center gap-1.5">
        <span className="inline-block w-3.5 h-3.5 rounded-sm bg-emerald-500/80" />
        Повтори (зараховані удари)
      </span>
      <span className="flex items-center gap-1.5">
        <span className="inline-block w-3.5 h-3.5 rounded-sm bg-amber-500/70" />
        Форвардні, відкинуті RepFilter (швидкість/тривалість поза смугою)
      </span>
      <span className="flex items-center gap-1.5">
        <span className="inline-block w-3.5 h-3.5 rounded-sm bg-neutral-500/50" />
        Відкинуті ForwardStrokeFilter (замах назад / шум)
      </span>
      <span className="flex items-center gap-1.5">
        <span className="inline-block w-px h-3.5 bg-white/90" />
        Пік (макс. швидкість)
      </span>
      <span className="flex items-center gap-1.5">
        <span className="inline-block w-0.5 h-3.5 bg-red-500" />
        Поточна позиція відео
      </span>
    </div>
  )
}
