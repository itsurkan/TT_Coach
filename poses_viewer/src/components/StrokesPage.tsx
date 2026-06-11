import { useEffect, useMemo, useRef, useState } from 'react'
import { Handedness } from '../drill2d/types'
import { parsePoseV2, PoseSequence2D } from '../drill2d/parsePoseV2'
import { countStrokes } from '../drill2d/countStrokes'
import { DETECTOR_DEFAULTS } from '../drill2d/strokeDetector2d'
import { StrokeTimeline, TimelineEntry } from './StrokeTimeline'

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
  const videoRef = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    fetch('/api/videos').then(r => (r.ok ? r.json() : [])).then(setVideos).catch(() => setVideos([]))
  }, [])

  useEffect(() => {
    if (!base) { setSeq(null); return }
    setError('')
    setSeq(null)
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

  const videoEntry = videos.find(v => v.name === base)
  const videoUrl = videoEntry?.ext ? `/videos/${base}/${base}${videoEntry.ext}` : null

  const seek = (ms: number) => {
    const v = videoRef.current
    if (v) v.currentTime = ms / 1000
  }

  return (
    <div className="min-h-screen bg-neutral-900 text-neutral-100 p-4 space-y-4">
      <header className="flex items-center gap-4 flex-wrap">
        <a href="#/main" className="text-sky-400 hover:underline">← Viewer</a>
        <h1 className="text-lg font-semibold">Підрахунок ударів (M0)</h1>
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
            onTimeUpdate={e => setCurrentMs(e.currentTarget.currentTime * 1000)}
          />
          {seq && result && (
            <StrokeTimeline
              entries={entries}
              durationMs={seq.videoDurationMs}
              currentMs={currentMs}
              onSeek={seek}
            />
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
        <p className="text-neutral-400">
          Зелені смуги — повтори; жовті — форвардні, відкинуті RepFilter; сірі — піки,
          відкинуті ForwardStrokeFilter (замахи назад/шум). Клік по смузі — перехід до піка.
        </p>
      </fieldset>
    </div>
  )
}
