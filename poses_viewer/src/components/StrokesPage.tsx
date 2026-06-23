import { useEffect, useMemo, useRef, useState } from 'react'
import { useSpokenFeedback } from './useSpokenFeedback'
import { buildSpokenSchedule } from '../drill2d/buildSpokenSchedule'
import { loadUserStyles, getActiveStyle, saveUserStyles } from '../drill2d/voiceStyleStore'
import { voiceProfileOf, type MetricKey } from '../drill2d/voiceStyle'
import { type FeedbackSettings, loadFeedbackSettings, saveFeedbackSettings } from '../drill2d/feedbackSettings'
import { loadManifest, type ClipManifest } from '../drill2d/voiceClips'
import { Handedness } from '../drill2d/types'
import { parsePoseV2, PoseSequence2D } from '../drill2d/parsePoseV2'
import { countStrokes } from '../drill2d/countStrokes'
import { DETECTOR_DEFAULTS } from '../drill2d/strokeDetector2d'
import { DEFAULT_MAX_TRAVEL_TORSO } from '../drill2d/locomotionFilter'
import { StrokeTimeline, TimelineEntry } from './StrokeTimeline'
import { analyzeDrill, DrillAnalysisReport } from '../drill2d/analyzeDrill'
import { REFERENCE_STANDARDS } from '../drill2d/referenceStandard'
import { ALL_KEYS } from '../drill2d/drillMetrics'
import { DrillResultsTable } from './DrillResultsTable'
import { loopBackTarget } from './strokeLoop'
import { VoiceStyleEditor } from './VoiceStyleEditor'
import { Slider, Toggle, secFmt } from './controls'

interface VideoItem { name: string; ext: string }

/** EXP-9: UA metric labels for the session summary (mirrors DrillResultsTable chrome). */
const METRIC_LABEL_UA: Record<string, string> = {
  elbow_angle: 'лікоть',
  shoulder_angle: 'плече',
  knee_bend: 'коліна',
  torso_lean: 'нахил корпусу',
  shoulder_tilt: 'нахил плечей',
}

export default function StrokesPage() {
  const [videos, setVideos] = useState<VideoItem[]>([])
  const [base, setBase] = useState('')
  const [seq, setSeq] = useState<PoseSequence2D | null>(null)
  const [error, setError] = useState('')
  const [handedness, setHandedness] = useState<Handedness>('right')
  const [yawDeg, setYawDeg] = useState(0)
  const [minPeakSpeed, setMinPeakSpeed] = useState(DETECTOR_DEFAULTS.minPeakSpeed)
  const [minPeakGapMs, setMinPeakGapMs] = useState(DETECTOR_DEFAULTS.minPeakGapMs)
  // Per-clip detector sensitivity. Default 300ms keeps the global goldens (video_4=10);
  // lower it (~200ms) for slow/warm-up footage like video_3 to surface faint strokes + splits.
  const [smoothingMs, setSmoothingMs] = useState(DETECTOR_DEFAULTS.smoothingWindowMs)
  const [hipTravelMaxTorso, setHipTravelMaxTorso] = useState(DEFAULT_MAX_TRAVEL_TORSO) // L-30 gate on by default; set 0 to disable
  const [currentMs, setCurrentMs] = useState(0)
  const [selectedIdx, setSelectedIdx] = useState<number | null>(null)
  const [loop, setLoop] = useState(false)
  const [drillType, setDrillType] = useState('forehand_drive')
  const [feedbackSettings, setFeedbackSettings] = useState<FeedbackSettings>(loadFeedbackSettings)
  const [muted, setMuted] = useState(false)
  const [styleState, setStyleState] = useState(() => loadUserStyles())
  const [manifest, setManifest] = useState<ClipManifest | null>(null)
  const activeStyle = useMemo(() => getActiveStyle(styleState), [styleState])
  useEffect(() => { saveFeedbackSettings(feedbackSettings) }, [feedbackSettings])
  function patchSettings(p: Partial<FeedbackSettings>) { setFeedbackSettings(s => ({ ...s, ...p })) }
  const videoRef = useRef<HTMLVideoElement>(null)
  // EXP-7: gate the calibration save until the load for the current base has applied,
  // so switching videos doesn't write the old yaw to the new video's key.
  const calibAppliedRef = useRef('')

  useEffect(() => {
    fetch('/api/videos').then(r => (r.ok ? r.json() : [])).then((vs: VideoItem[]) => {
      setVideos(vs)
      // Restore the last-selected video across reloads (only if it still exists).
      try {
        const saved = localStorage.getItem('strokes_selected_video')
        if (saved && vs.some(v => v.name === saved)) setBase(saved)
      } catch { /* ignore storage errors */ }
    }).catch(() => setVideos([]))
  }, [])

  // Persist the selected video so a reload reopens it.
  useEffect(() => {
    if (!base) return
    try { localStorage.setItem('strokes_selected_video', base) } catch { /* ignore */ }
  }, [base])

  // EXP-7: per-video camera-angle + handedness persistence. Selecting a video restores
  // its saved calibration (the manual L-25 angle you defined once); changing the knobs
  // saves it back. Keyed by video base in localStorage.
  useEffect(() => {
    if (!base) return
    let yaw = 0
    let hand: Handedness = 'right'
    try {
      const raw = localStorage.getItem(`strokes_calib_${base}`)
      if (raw) {
        const c = JSON.parse(raw)
        if (typeof c.yaw === 'number') yaw = c.yaw
        if (c.hand === 'left' || c.hand === 'right') hand = c.hand
      }
    } catch { /* ignore malformed storage */ }
    setYawDeg(yaw)
    setHandedness(hand)
    calibAppliedRef.current = base
  }, [base])

  useEffect(() => {
    if (!base || calibAppliedRef.current !== base) return
    try {
      localStorage.setItem(`strokes_calib_${base}`, JSON.stringify({ yaw: yawDeg, hand: handedness }))
    } catch { /* ignore quota/availability errors */ }
  }, [base, yawDeg, handedness])

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

  useEffect(() => {
    let alive = true
    loadManifest(activeStyle.id).then(m => { if (alive) setManifest(m) })
    return () => { alive = false }
  }, [activeStyle.id])

  const result = useMemo(() => {
    if (!seq) return null
    try {
      return countStrokes(seq, {
        handedness,
        cameraYawDeg: yawDeg,
        detector: { minPeakSpeed, minPeakGapMs, smoothingWindowMs: smoothingMs },
        hipTravelMaxTorso,
      })
    } catch {
      return null // xScaleFor throws beyond ±60°; inputs are clamped, but stay safe
    }
  }, [seq, handedness, yawDeg, minPeakSpeed, minPeakGapMs, smoothingMs, hipTravelMaxTorso])

  const report = useMemo<DrillAnalysisReport | null>(() => {
    if (!seq) return null
    const standard = REFERENCE_STANDARDS[drillType]
    if (!standard) return null
    try {
      return analyzeDrill(seq, {
        handedness,
        drillType,
        standard,
        feedbackSettings,
        cameraYawDeg: yawDeg,
        detector: { minPeakSpeed, minPeakGapMs, smoothingWindowMs: smoothingMs },
        hipTravelMaxTorso,
      })
    } catch {
      return null
    }
  }, [seq, handedness, yawDeg, minPeakSpeed, minPeakGapMs, smoothingMs, drillType, feedbackSettings, hipTravelMaxTorso])

  const { schedule, voicedByRep } = useMemo(
    () => report
      ? buildSpokenSchedule(report.voiceReps, report.strokeStartTimes, feedbackSettings, activeStyle.phrases[activeStyle.lang], activeStyle.lang, activeStyle.rate, manifest)
      : { schedule: [], voicedByRep: [] },
    [report, feedbackSettings, activeStyle, manifest],
  )
  const spoken = useSpokenFeedback(schedule, voiceProfileOf(activeStyle), manifest, muted)

  /** Dump every input + output that determines feedback into one JSON for offline analysis. */
  function exportDebugJson() {
    const dump = {
      exportedAt: new Date().toISOString(),
      video: base,
      drillType,
      detector: { handedness, cameraYawDeg: yawDeg, minPeakSpeed, minPeakGapMs, smoothingWindowMs: smoothingMs, hipTravelMaxTorso },
      enabledMetrics: [...feedbackSettings.enabledMetrics],
      muted,
      referenceStandard: REFERENCE_STANDARDS[drillType],
      voiceStyle: activeStyle,
      report: report && {
        counts: { rawPeakCount: report.rawPeakCount, forwardRepCount: report.forwardRepCount, reps: report.reps.length, cleanReps: report.cleanReps },
        placementOk: report.placementOk,
        focus: report.focus,
        unreliableMetrics: report.unreliableMetrics,
        strengths: report.strengths,
        reps: report.reps.map((r, i) => ({
          index: i + 1,
          placementOk: r.placementOk,
          cameraYawDeg: r.cameraYawDeg,
          metrics: r.metrics,
          perPhase: r.perPhase,
          coil: r.coil,
          cues: r.cues,
        })),
        // The exact per-rep inputs the voice core gates against (value + un-widened ideal band).
        voiceReps: report.voiceReps,
      },
      // What the active voice style would actually say, in time order.
      spokenSchedule: schedule.map(s => ({ atMs: s.atMs, kind: s.kind, metricKey: s.metricKey, text: s.text })),
    }
    const blob = new Blob([JSON.stringify(dump, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${base || 'drill'}-debug.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  const entries = useMemo<TimelineEntry[]>(() => {
    if (!seq || !result) return []
    const ms = (frame: number) => seq.frames[frame]?.timestampMs ?? frame * seq.intervalMs
    const repPeaks = new Set(result.reps.map(s => s.peakFrame))
    const locoPeaks = new Set(result.locomotionStrokes.map(s => s.peakFrame))
    // A "stroke" is one full cycle (backswing + forward drive). Each cycle renders a
    // single band spanning [backswing.start → drive.end]; the consumed backswing peak
    // is NOT drawn separately as gray recovery.
    const consumed = new Set<number>()
    for (const c of result.cycles) if (c.backswing) consumed.add(c.backswing.peakFrame)

    const out: TimelineEntry[] = []
    let n = 0
    for (const c of result.cycles) {
      const drivePeak = c.drive.peakFrame
      const kind: TimelineEntry['kind'] = repPeaks.has(drivePeak) ? 'rep'
        : locoPeaks.has(drivePeak) ? 'locomotion-dropped'
        : 'forward-dropped'
      const tag = c.backswing ? 'бек+драйв' : 'драйв'
      out.push({
        kind,
        startMs: ms(c.startFrame),
        peakMs: ms(drivePeak),
        endMs: ms(c.endFrame),
        label: `#${++n} · ${tag} · ${c.drive.peakSpeed.toFixed(1)} торс/с`,
      })
    }
    // Leftover raw peaks that are neither a drive nor a consumed backswing
    // (e.g. getting-into-position swings) stay as gray recovery bands.
    const drivePeaks = new Set(result.forwardStrokes.map(s => s.peakFrame))
    for (const s of result.rawStrokes) {
      if (drivePeaks.has(s.peakFrame) || consumed.has(s.peakFrame)) continue
      out.push({
        kind: 'raw-dropped',
        startMs: ms(s.startFrame),
        peakMs: ms(s.peakFrame),
        endMs: ms(s.endFrame),
        label: `замах · ${s.peakSpeed.toFixed(1)} торс/с`,
      })
    }
    out.sort((a, b) => a.startMs - b.startMs)
    return out
  }, [seq, result])

  // Selection indexes into entries; drop it (and stop looping) when the knobs rebuild the stroke list.
  useEffect(() => { setSelectedIdx(null); setLoop(false) }, [handedness, yawDeg, minPeakSpeed, minPeakGapMs, smoothingMs, drillType, feedbackSettings, hipTravelMaxTorso])

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
      <div className="w-full flex flex-col lg:flex-row gap-4 items-start">
      <div className="flex-1 min-w-0 space-y-4">
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
                  🔊 {spoken.latest.text}
                </div>
              )}
              {spoken.log.length > 0 && (
                <details className="text-xs text-neutral-300">
                  <summary>Журнал підказок ({spoken.log.length})</summary>
                  <ul className="mt-1 space-y-0.5">
                    {spoken.log.map((f, i) => (
                      <li key={i}>{(f.atMs / 1000).toFixed(1)} с — {f.text}</li>
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
          {result.locomotionStrokes.length > 0 && (
            <span className="text-rose-400">Хода (відкинуто): <b>{result.locomotionStrokes.length}</b></span>
          )}
        </div>
      )}

      {report && report.reps.length > 0 && (
        <div className="space-y-2">
          <h2 className="text-sm font-semibold">Аналіз повторів</h2>
          {report.placementOk && (
            <div className={`rounded p-2 text-sm border ${report.focus.metricKey
              ? 'bg-amber-900/40 border-amber-700 text-amber-100'
              : 'bg-emerald-900/40 border-emerald-700 text-emerald-100'}`}>
              🎯 {report.focus.message}
              {report.focus.metricKey && (
                <span className="text-amber-300/80"> — {report.focus.count}/{report.focus.total} повторів</span>
              )}
            </div>
          )}
          {report.placementOk && (
            <div className="rounded p-2 text-xs bg-neutral-800/70 border border-neutral-700 text-neutral-300 space-y-0.5">
              <div>📋 <b>Підсумок:</b> {report.reps.length} повторів · {report.cleanReps} без зауважень</div>
              {report.strengths.length > 0 && (
                <div className="text-emerald-300">
                  ✅ Сильні сторони: {report.strengths.map(k => METRIC_LABEL_UA[k] ?? k).join(', ')}
                </div>
              )}
            </div>
          )}
          {!report.placementOk && (
            <div className="text-amber-400 text-xs">
              ⚠ Більшість повторів зняті не збоку — підказки приглушені. Виправ кут камери.
            </div>
          )}
          <DrillResultsTable
            reps={report.reps}
            standard={REFERENCE_STANDARDS[drillType]}
            enabledMetrics={new Set(feedbackSettings.enabledMetrics)}
            unreliableMetrics={report.unreliableMetrics}
            selectedIndex={selectedIdx}
            voicedByRep={voicedByRep}
            onSelect={i => {
              setSelectedIdx(i)
              const peakMs = seq ? (seq.frames[report.reps[i].stroke.peakFrame]?.timestampMs ?? 0) : 0
              seek(peakMs)
            }}
          />
        </div>
      )}
      </div>

      <div className="flex flex-col xl:flex-row gap-4 items-start shrink-0">
      <fieldset className="border border-neutral-700 rounded p-3 max-w-xl space-y-2 text-sm">
        <legend className="px-1">Налаштування</legend>
        <button
          type="button"
          className="px-2 py-1 bg-sky-800 hover:bg-sky-700 disabled:opacity-40 rounded text-xs"
          onClick={exportDebugJson}
          disabled={!report}
          title="Зберегти всі налаштування + результати аналізу в JSON для надсилання на діагностику"
        >
          ⬇ Експорт налаштувань (JSON)
        </button>
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
        <label className="flex items-center gap-2">
          <span className="w-56">Згладжування швидкості (мс):</span>
          <input
            type="number" min={50} max={500} step={50}
            className="bg-neutral-800 rounded px-2 py-1 w-20"
            value={smoothingMs}
            onChange={e => setSmoothingMs(Number(e.target.value))}
          />
        </label>
        <p className="text-neutral-400">
          За замовч. 300 мс (тримає глобальні голдени, video_4=10). Нижче (~200) — для повільних/розминкових
          кліпів (video_3): ловить слабкі удари та розділяє злиті піки. Менше згладжування = чутливіший детектор.
        </p>
        <label className="flex items-center gap-2">
          <span className="w-56">Гейт ходьби (зсув таза, торс; 0 = вимк):</span>
          <input
            type="number" min={0} max={2} step={0.1}
            className="bg-neutral-800 rounded px-2 py-1 w-20"
            value={hipTravelMaxTorso}
            onChange={e => setHipTravelMaxTorso(Number(e.target.value))}
          />
        </label>
        <p className="text-neutral-400">
          Експериментально (L-30): відкидає «повтори», де таз їде вбік (хода). ~0.4 ловить ходьбу,
          лишаючи справжні удари (зсув 0.1–0.25 торса). 0 = вимкнено.
        </p>
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
          <input type="checkbox" checked={!muted} onChange={e => setMuted(!e.target.checked)} />
          <span className="text-neutral-400">{muted ? 'вимкнено (лише текст)' : 'голос увімкнено'}</span>
        </label>
        <div className="flex items-start gap-2">
          <span className="w-56">Метрики:</span>
          <div className="flex flex-wrap gap-x-3 gap-y-1">
            {ALL_KEYS.map(k => (
              <label key={k} className="flex items-center gap-1">
                <input
                  type="checkbox"
                  checked={feedbackSettings.enabledMetrics.includes(k as MetricKey)}
                  onChange={e => patchSettings({
                    enabledMetrics: e.target.checked
                      ? [...feedbackSettings.enabledMetrics, k as MetricKey]
                      : feedbackSettings.enabledMetrics.filter(m => m !== k),
                  })}
                />
                {k}
              </label>
            ))}
          </div>
        </div>
        <div className="border-t border-neutral-800 pt-2 text-neutral-400">Зони</div>
        <Slider label="Ширина зони ×" min={0.5} max={2} step={0.05} value={feedbackSettings.bandWidthMult} onChange={v => patchSettings({ bandWidthMult: v })} hint="Множник допустимої зони навколо ідеалу. >1 поблажливіше, <1 суворіше." />
        <Slider label="Поріг значущості (°)" min={0} max={20} step={1} value={feedbackSettings.minMeaningfulDeltaDeg} onChange={v => patchSettings({ minMeaningfulDeltaDeg: v })} hint="Мінімальне відхилення в градусах, яке варто озвучувати." />
        <Slider label="Інтервал нагадування" min={0} max={20000} step={500} value={feedbackSettings.reminderIntervalMs} onChange={v => patchSettings({ reminderIntervalMs: v })} hint="Як часто повторювати підказку про ту саму проблему." />
        <Toggle label="Чергувати підказки" value={feedbackSettings.varyCues} onChange={v => patchSettings({ varyCues: v })} hint="Чергувати метрику підказки, коли їх кілька." />

        <div className="border-t border-neutral-800 pt-2 text-neutral-400">Каденс (с)</div>
        <Slider label="Пауза між підказками" min={0} max={10} step={0.1} value={feedbackSettings.correctiveMinGapMs / 1000} onChange={v => patchSettings({ correctiveMinGapMs: Math.round(v * 1000) })} fmt={secFmt} hint="Мінімальний час між виправними підказками." />
        <Slider label="Тиша перед похвалою" min={0} max={15} step={0.1} value={feedbackSettings.praiseMinSilenceMs / 1000} onChange={v => patchSettings({ praiseMinSilenceMs: Math.round(v * 1000) })} fmt={secFmt} hint="Скільки тиші перед похвалою." />
        <Slider label="Пауза після удару" min={0} max={1.5} step={0.05} value={feedbackSettings.postStrokeGapMs / 1000} onChange={v => patchSettings({ postStrokeGapMs: Math.round(v * 1000) })} fmt={secFmt} hint="Затримка після удару перед озвученням." />

        <div className="border-t border-neutral-800 pt-2 text-neutral-400">Похвала</div>
        <Toggle label="Увімкнено" value={feedbackSettings.praiseEnabled} onChange={v => patchSettings({ praiseEnabled: v })} />
        <Toggle label="За виправлення" value={feedbackSettings.praiseOnCorrection} onChange={v => patchSettings({ praiseOnCorrection: v })} />
        <Toggle label="За серію" value={feedbackSettings.praiseOnStreak} onChange={v => patchSettings({ praiseOnStreak: v })} />
        <Slider label="Довжина серії" min={1} max={10} step={1} value={feedbackSettings.praiseStreakLen} onChange={v => patchSettings({ praiseStreakLen: v })} />

        <div className="border-t border-neutral-800 pt-2 text-neutral-400">Пропуск застарілих</div>
        <Toggle label="Увімкнено" value={feedbackSettings.skipStaleEnabled} onChange={v => patchSettings({ skipStaleEnabled: v })} hint="Не озвучувати, якщо вже починається наступний удар." />
        <Slider label="Запас перед ударом (мс)" min={0} max={1000} step={50} value={feedbackSettings.skipStaleMarginMs} onChange={v => patchSettings({ skipStaleMarginMs: v })} />
      </fieldset>
      <VoiceStyleEditor
        state={styleState}
        manifest={manifest}
        onChange={next => { setStyleState(next); saveUserStyles(next) }}
      />
      </div>
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
        <span className="inline-block w-3.5 h-3.5 rounded-sm bg-rose-500/80" />
        Хода (гейт зсуву таза, L-30)
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
