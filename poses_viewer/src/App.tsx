import { useState, useEffect, useCallback, useRef } from 'react'
import { Film, Volume2, VolumeX } from 'lucide-react'
import { Landmark, PosesBallData, Contact, ContactsData, FrameLabel, LabelStatus, LabelsData, CropConfig, TrajectorySegment, TableFrameLabel, TableLabelsData, TABLE_KEYPOINT_COUNT } from './types'
import { segmentTrajectory, predictTrajectory, type PredictiveTrajectory } from './utils/trajectoryPipeline'
import { predictTrajectoryV2, type PredictiveTrajectoryV2 } from './utils/trajectoryPipelineV2'
import { predictTrajectoryV3, type PredictiveTrajectoryV3 } from './utils/trajectoryPipelineV3'
import { predictTrajectoryV4 } from './utils/trajectoryPipelineV4'
import { computeTrajectory3D } from './utils/trajectoryPipeline3D'
import { computeTrajectory3Dv2, type Trajectory3Dv2Result } from './utils/trajectoryPipeline3Dv2'
import { computeHomographyFromPartial } from './utils/tableHomography'
import type { Trajectory3DResult } from './types'
import Trajectory3DOverlay from './components/Trajectory3DOverlay'
import Trajectory3Dv2Overlay from './components/Trajectory3Dv2Overlay'
import TrajectoryV2Overlay from './components/TrajectoryV2Overlay'
import TrajectoryV3Overlay from './components/TrajectoryV3Overlay'
import TrajectoryV4Overlay from './components/TrajectoryV4Overlay'
import PoseCanvas from './components/PoseCanvas'
import FrameControls from './components/FrameControls'
import LabelPanel from './components/LabelPanel'
import TableLabelPanel from './components/TableLabelPanel'
import TableGridOverlay from './components/TableGridOverlay'
import TableDetectOverlay from "./components/TableDetectOverlay"
import TableLabelsOverlay from './components/TableLabelsOverlay'
import DatasetBrowser from './components/DatasetBrowser'
import Drill2Preview from './components/Drill2Preview'
import MannequinEditor from './components/MannequinEditor'
import { useHashRoute } from './hooks/useHashRoute'
import { toNumber, normalizeData } from './utils/normalizePoses'

/** Ordered list of JSON suffixes to try, based on which layers are enabled. */
function jsonSuffixes(wantPoses: boolean, wantBall: boolean): string[] {
  if (wantPoses && wantBall)  return ['_poses_ball.json', '_poses.json', '_ball.json']
  if (wantPoses && !wantBall) return ['_poses.json', '_poses_ball.json']
  if (!wantPoses && wantBall) return ['_ball.json', '_poses_ball.json']
  return ['_poses_ball.json', '_poses.json', '_ball.json']
}

/* ── MultiSelect dropdown with checkboxes ──────────────────────────── */
interface MultiSelectItem {
  label: string
  checked: boolean
  onChange: (v: boolean) => void
  accent: string
}

function MultiSelect({ label, items, footer }: { label: string; items: MultiSelectItem[]; footer?: React.ReactNode }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  const activeCount = items.filter(i => i.checked).length

  return (
    <div ref={ref} className="relative">
      <button
        className="flex items-center gap-1.5 bg-gray-800 hover:bg-gray-700 px-3 py-1.5 rounded text-sm transition-colors cursor-pointer"
        onClick={() => setOpen(o => !o)}
      >
        {label}
        {activeCount > 0 && (
          <span className="bg-blue-600 text-white text-xs rounded-full px-1.5 min-w-[18px] text-center leading-[18px]">
            {activeCount}
          </span>
        )}
        <span className="text-gray-500 text-xs ml-0.5">{open ? '\u25B2' : '\u25BC'}</span>
      </button>
      {open && (
        <div className="absolute top-full left-0 mt-1 bg-gray-800 border border-gray-700 rounded shadow-lg z-50 min-w-[160px] py-1">
          {items.map(item => (
            <label
              key={item.label}
              className="flex items-center gap-2 px-3 py-1.5 hover:bg-gray-700 cursor-pointer text-sm whitespace-nowrap"
            >
              <input
                type="checkbox"
                checked={item.checked}
                onChange={e => item.onChange(e.target.checked)}
                className={item.accent}
              />
              {item.label}
            </label>
          ))}
          {footer}
        </div>
      )}
    </div>
  )
}

const SETTINGS_KEY = 'poses_viewer_settings'

interface PersistedSettings {
  showPoses: boolean; showRtmPoses: boolean; showBall: boolean; showBallV5: boolean; showBallYolo: boolean
  showContacts: boolean; muted: boolean; placingBall: boolean; showLabels: boolean
  showTrajectory: boolean; showTrajectoryV2: boolean; showTrajectoryV3: boolean; showTrajectoryV4: boolean; showTrajectory3D: boolean; showTrajectory3Dv2: boolean
  showTableLabels: boolean; showTableView: boolean; showTableYolo: boolean; showTablePredict: boolean; showTableGrid: boolean; showTableGridMarked: boolean
}

const DEFAULT_SETTINGS: PersistedSettings = {
  showPoses: false, showRtmPoses: false, showBall: false, showBallV5: false, showBallYolo: false,
  showContacts: false, muted: false, placingBall: false, showLabels: false,
  showTrajectory: false, showTrajectoryV2: false, showTrajectoryV3: false, showTrajectoryV4: false, showTrajectory3D: false, showTrajectory3Dv2: false,
  showTableLabels: false, showTableView: false, showTableYolo: false, showTablePredict: true, showTableGrid: true, showTableGridMarked: false,
}

function loadSettings(): PersistedSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY)
    if (raw) return { ...DEFAULT_SETTINGS, ...JSON.parse(raw) }
  } catch { /* ignore */ }
  return DEFAULT_SETTINGS
}

function saveSettings(s: PersistedSettings) {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(s))
}

export default function App() {
  const [data, setData] = useState<PosesBallData | null>(null)
  const [videoBase, setVideoBase] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [frameIndex, setFrameIndex] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [videoSrc, setVideoSrc] = useState<string | null>(null)
  const saved = useRef(loadSettings())
  const [showPoses, setShowPoses] = useState(saved.current.showPoses)
  const [showRtmPoses, setShowRtmPoses] = useState(saved.current.showRtmPoses)
  const [rtmData, setRtmData] = useState<PosesBallData | null>(null)
  const [showBall, setShowBall] = useState(saved.current.showBall)
  const [showBallV5, setShowBallV5] = useState(saved.current.showBallV5)
  const [ballV5Data, setBallV5Data] = useState<PosesBallData | null>(null)
  const [showBallYolo, setShowBallYolo] = useState(saved.current.showBallYolo)
  const [ballYoloData, setBallYoloData] = useState<PosesBallData | null>(null)
  const [contacts, setContacts] = useState<ContactsData | null>(null)
  const [showContacts, setShowContacts] = useState(saved.current.showContacts)
  const [muted, setMuted] = useState(saved.current.muted)
  const [labels, setLabels] = useState<Record<number, FrameLabel>>({})
  const [placingBall, setPlacingBall] = useState(saved.current.placingBall)
  const [showLabels, setShowLabels] = useState(saved.current.showLabels)
  const [cropConfig, setCropConfig] = useState<CropConfig | null>(null)
  const [showTrajectory, setShowTrajectory] = useState(saved.current.showTrajectory)
  const [trajectoryMode, setTrajectoryMode] = useState<'fitted' | 'predict'>('predict')
  const [trajectorySegments, setTrajectorySegments] = useState<TrajectorySegment[]>([])
  const [predictiveTraj, setPredictiveTraj] = useState<PredictiveTrajectory | null>(null)
  const [showTrajectoryV2, setShowTrajectoryV2] = useState(saved.current.showTrajectoryV2)
  const [predictiveTrajV2, setPredictiveTrajV2] = useState<PredictiveTrajectoryV2 | null>(null)
  const [showTrajectoryV3, setShowTrajectoryV3] = useState(saved.current.showTrajectoryV3)
  const [predictiveTrajV3, setPredictiveTrajV3] = useState<PredictiveTrajectoryV3 | null>(null)
  const [showTrajectoryV4, setShowTrajectoryV4] = useState(saved.current.showTrajectoryV4)
  const [showTrajectory3D, setShowTrajectory3D] = useState(saved.current.showTrajectory3D)
  const [showTrajectory3Dv2, setShowTrajectory3Dv2] = useState(saved.current.showTrajectory3Dv2)
  const [trajectory3Dv2, setTrajectory3Dv2] = useState<Trajectory3Dv2Result | null>(null)
  const [predictiveTrajV4, setPredictiveTrajV4] = useState<PredictiveTrajectory | null>(null)
  const [showTableLabels, setShowTableLabels] = useState(saved.current.showTableLabels)
  const [showTableView, setShowTableView] = useState(saved.current.showTableView)
  const [tableLabels, setTableLabels] = useState<Record<number, TableFrameLabel>>({})
  const [placingKeypoint, setPlacingKeypoint] = useState(0) // 0-5 auto-advances, -1 = done
  const [showTableYolo, setShowTableYolo] = useState(saved.current.showTableYolo)
  const [tableYoloData, setTableYoloData] = useState<{ keypoints: Array<{ x: number; y: number; confidence: number }> } | null>(null)
  const [showTablePredict, setShowTablePredict] = useState(saved.current.showTablePredict)
  const [showTableGrid, setShowTableGrid] = useState(saved.current.showTableGrid)
  const [showTableGridMarked, setShowTableGridMarked] = useState(saved.current.showTableGridMarked)
  const [tablePredictData, setTablePredictData] = useState<{ keypoints: Array<{ x: number; y: number; confidence: number }> } | null>(null)
  const [trajectory3D, setTrajectory3D] = useState<Trajectory3DResult | null>(null)
  const [videoList, setVideoList] = useState<{ name: string; ext: string }[]>([])
  const { route, navigate } = useHashRoute()
  const [zoom, setZoom] = useState(1)
  const [pan, setPan] = useState({ x: 0, y: 0 })
  const isPanning = useRef(false)
  const panStart = useRef({ x: 0, y: 0 })
  const viewportRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    saveSettings({
      showPoses, showRtmPoses, showBall, showBallV5, showBallYolo, showContacts, muted,
      placingBall, showLabels, showTrajectory, showTrajectoryV2, showTrajectoryV3,
      showTrajectoryV4, showTrajectory3D, showTrajectory3Dv2, showTableLabels, showTableView, showTableYolo, showTablePredict, showTableGrid, showTableGridMarked,
    })
  }, [showPoses, showRtmPoses, showBall, showBallV5, showBallYolo, showContacts, muted,
      placingBall, showLabels, showTrajectory, showTrajectoryV2, showTrajectoryV3,
      showTrajectoryV4, showTableLabels, showTableView, showTableYolo, showTablePredict, showTableGrid, showTableGridMarked])

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const videoObjectUrl = useRef<string | null>(null)
  const totalFramesRef = useRef(0)

  // Fetch video list on mount
  useEffect(() => {
    fetch('/api/videos').then(r => r.ok ? r.json() : []).then(setVideoList).catch(() => {})
  }, [])

  // Keep totalFramesRef in sync
  useEffect(() => {
    totalFramesRef.current = data?.totalFrames ?? 0
  }, [data])

  // Compute trajectory segments when YOLO ball data changes
  useEffect(() => {
    if (ballYoloData && ballYoloData.frames.length > 0) {
      const segments = segmentTrajectory(ballYoloData.frames, ballYoloData.intervalMs)
      setTrajectorySegments(segments)
    } else {
      setTrajectorySegments([])
    }
  }, [ballYoloData])

  // Compute predictive trajectory on frame change (only when in predict mode)
  useEffect(() => {
    if (!showTrajectory || trajectoryMode !== 'predict' || !ballYoloData) {
      setPredictiveTraj(null)
      return
    }
    const traj = predictTrajectory(ballYoloData.frames, frameIndex, ballYoloData.intervalMs)
    setPredictiveTraj(traj)
  }, [showTrajectory, trajectoryMode, ballYoloData, contacts, frameIndex])

  // Compute V2 predictive trajectory on frame change
  useEffect(() => {
    if (!showTrajectoryV2 || !ballYoloData) {
      setPredictiveTrajV2(null)
      return
    }
    const traj = predictTrajectoryV2(ballYoloData.frames, frameIndex, ballYoloData.intervalMs)
    setPredictiveTrajV2(traj)
  }, [showTrajectoryV2, ballYoloData, frameIndex])

  // Compute V3 predictive trajectory on frame change
  useEffect(() => {
    if (!showTrajectoryV3 || !ballYoloData) {
      setPredictiveTrajV3(null)
      return
    }
    const traj = predictTrajectoryV3(
      ballYoloData.frames, frameIndex, ballYoloData.intervalMs, 10, contacts?.contacts,
      { width: ballYoloData.videoWidth, height: ballYoloData.videoHeight },
    )
    setPredictiveTrajV3(traj)
  }, [showTrajectoryV3, ballYoloData, frameIndex, contacts])

  // Compute V4 predictive trajectory on frame change
  useEffect(() => {
    if (!showTrajectoryV4 || !ballYoloData) {
      setPredictiveTrajV4(null)
      return
    }
    const traj = predictTrajectoryV4(
      ballYoloData.frames, frameIndex, ballYoloData.intervalMs, 10,
      { width: ballYoloData.videoWidth, height: ballYoloData.videoHeight },
    )
    setPredictiveTrajV4(traj)
  }, [showTrajectoryV4, ballYoloData, frameIndex])

  // Compute 3D trajectory (ball projected to table via marked homography)
  useEffect(() => {
    if (!showTrajectory3D || !ballYoloData || Object.keys(tableLabels).length === 0) {
      setTrajectory3D(null)
      return
    }
    // Try each labeled frame until one produces a valid homography
    let result: ReturnType<typeof computeHomographyFromPartial> = null
    for (const label of Object.values(tableLabels)) {
      if (!label || label.points.filter(Boolean).length < 4) continue
      result = computeHomographyFromPartial(
        label.points,
        ballYoloData.videoWidth,
        ballYoloData.videoHeight,
      )
      if (result) break
    }
    if (!result) { setTrajectory3D(null); return }
    const homography = result.homography

    // Collect ball frames up to current frame
    const ballFrames = ballYoloData.frames
      .filter(f => f.ball && f.frameIndex <= frameIndex)
      .map(f => ({ frameIndex: f.frameIndex, ball: f.ball! }))

    const traj3d = computeTrajectory3D(
      ballFrames, homography,
      ballYoloData.videoWidth, ballYoloData.videoHeight,
      ballYoloData.intervalMs,
    )
    if (traj3d.bounces.length > 0) {
      console.log('[3D] bounces:', traj3d.bounces, 'positions:', traj3d.positions.length)
    }
    setTrajectory3D(traj3d)
  }, [showTrajectory3D, ballYoloData, tableLabels, frameIndex])

  // Compute 3D v2 trajectory (physics-aware arc fitting)
  useEffect(() => {
    if (!showTrajectory3Dv2 || !ballYoloData || Object.keys(tableLabels).length === 0) {
      setTrajectory3Dv2(null)
      return
    }
    let result: ReturnType<typeof computeHomographyFromPartial> = null
    for (const label of Object.values(tableLabels)) {
      if (!label || label.points.filter(Boolean).length < 4) continue
      result = computeHomographyFromPartial(label.points, ballYoloData.videoWidth, ballYoloData.videoHeight)
      if (result) break
    }
    if (!result) { setTrajectory3Dv2(null); return }
    const homography = result.homography

    const ballFrames = ballYoloData.frames
      .filter(f => f.ball && f.frameIndex <= frameIndex)
      .map(f => ({ frameIndex: f.frameIndex, ball: f.ball! }))

    const traj = computeTrajectory3Dv2(
      ballFrames, homography,
      ballYoloData.videoWidth, ballYoloData.videoHeight,
      ballYoloData.intervalMs,
    )
    setTrajectory3Dv2(traj)
  }, [showTrajectory3Dv2, ballYoloData, tableLabels, frameIndex])

  const handlePlayPause = useCallback(() => {
    if (!data) return
    if (playing) { setPlaying(false); return }
    if (frameIndex >= data.frames.length - 1) setFrameIndex(0)
    setPlaying(true)
  }, [data, frameIndex, playing])

  /** Fetch a JSON file from the Vite server and load it. Tries fallback suffixes if primary is missing. */
  const fetchJson = useCallback(async (base: string, wantPoses: boolean, wantBall: boolean) => {
    // Always clear stale data from the previous video immediately
    setData(null)
    setError(null)

    const suffixes = jsonSuffixes(wantPoses, wantBall)
    for (const suffix of suffixes) {
      const url = `/videos/${base}/${base}${suffix}`
      try {
        const res = await fetch(url)
        if (!res.ok) continue           // try next suffix
        const json: unknown = await res.json()
        setData(normalizeData(json))
        setFrameIndex(0)
        setPlaying(false)
        return
      } catch {
        continue
      }
    }

    // All suffixes failed
    setError(`No JSON found for "${base}" (tried: ${suffixes.join(', ')})`)
  }, [])

  /** Fetch and normalize contacts JSON (silently ignores if missing). */
  const fetchContacts = useCallback(async (base: string) => {
    setContacts(null)
    const url = `/videos/${base}/${base}_contacts.json`
    try {
      const res = await fetch(url)
      if (!res.ok) return
      const json = (await res.json()) as Record<string, unknown>
      const raw = Array.isArray(json.contacts) ? json.contacts : []
      const parsed: Contact[] = raw
        .map((c: Record<string, unknown>) => ({
          frameIndex: toNumber(c.frameIndex ?? c.frame_index, -1),
          timestampMs: toNumber(c.timestampMs ?? c.timestamp_ms, 0),
          confidence: toNumber(c.confidence, 0),
          type: typeof c.type === 'string' ? c.type : 'table',
        }))
        .filter((c: Contact) => c.frameIndex >= 0)
        .sort((a: Contact, b: Contact) => a.frameIndex - b.frameIndex)

      setContacts({
        videoName: typeof json.videoName === 'string' ? json.videoName : undefined,
        intervalMs: toNumber(json.intervalMs ?? json.interval_ms, 100),
        totalFrames: toNumber(json.totalFrames ?? json.total_frames, 0),
        videoDurationMs: toNumber(json.videoDurationMs ?? json.video_duration_ms, 0),
        contacts: parsed,
      })
    } catch {
      // contacts file is optional — silently ignore
    }
  }, [])

  /** Fetch existing labels JSON (silently ignores if missing). */
  const fetchLabels = useCallback(async (base: string) => {
    setLabels({})
    setCropConfig(null)
    const url = `/videos/${base}/${base}_labels.json`
    try {
      const res = await fetch(url)
      if (!res.ok) return
      const json = (await res.json()) as LabelsData
      if (json.labels && typeof json.labels === 'object') {
        const map: Record<number, FrameLabel> = {}
        for (const [k, v] of Object.entries(json.labels)) {
          map[Number(k)] = v as FrameLabel
        }
        setLabels(map)
      }
      if (json.crop) setCropConfig(json.crop)
    } catch {
      // labels file is optional
    }
  }, [])

  /** Fetch RTMPose schema-v2 poses JSON (silently ignores if missing). */
  const fetchRtmPoses = useCallback(async (base: string) => {
    setRtmData(null)
    const url = `/videos/${base}/${base}_poses_rtm.json`
    try {
      const res = await fetch(url)
      if (!res.ok) return
      const json: unknown = await res.json()
      setRtmData(normalizeData(json))
    } catch {
      // rtm poses file is optional
    }
  }, [])

  /** Fetch ball_v5 JSON (silently ignores if missing). */
  const fetchBallV5 = useCallback(async (base: string) => {
    setBallV5Data(null)
    const url = `/videos/${base}/${base}_ball_v5.json`
    try {
      const res = await fetch(url)
      if (!res.ok) return
      const json: unknown = await res.json()
      setBallV5Data(normalizeData(json))
    } catch {
      // ball_v5 file is optional
    }
  }, [])

  /** Fetch ball_yolo JSON (silently ignores if missing). */
  const fetchBallYolo = useCallback(async (base: string) => {
    setBallYoloData(null)
    const url = `/videos/${base}/${base}_ball_yolo.json`
    try {
      const res = await fetch(url)
      if (!res.ok) return
      const json: unknown = await res.json()
      setBallYoloData(normalizeData(json))
    } catch {
      // ball_yolo file is optional
    }
  }, [])

  /** Save labels to the server. */
  const saveLabels = useCallback(async (base: string, newLabels: Record<number, FrameLabel>) => {
    const payload: LabelsData = {
      videoName: base,
      totalFrames: data?.totalFrames ?? 0,
      labels: newLabels,
      ...(cropConfig ? { crop: cropConfig } : {}),
    }
    try {
      await fetch(`/api/labels/${encodeURIComponent(base)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
    } catch {
      // silent save failure
    }
  }, [data, cropConfig])

  /** Set label for current frame (C/N shortcuts). Click on canvas handles ball position directly. */
  const handleLabel = useCallback((label: LabelStatus) => {
    const maxFrame = data ? data.frames.length - 1 : totalFramesRef.current - 1
    const newLabel: FrameLabel = { frameIndex, label }
    const updated = { ...labels, [frameIndex]: newLabel }
    setLabels(updated)
    if (videoBase) saveLabels(videoBase, updated)
    // Auto-advance for non-click labels (C and N)
    if (label !== 'wrong') {
      if (frameIndex < maxFrame) {
        setFrameIndex(frameIndex + 1)
      }
    }
  }, [data, frameIndex, labels, videoBase, saveLabels])

  /** Handle ball placement click from canvas. */
  const handlePlaceBall = useCallback((x: number, y: number) => {
    if (!placingBall) return
    // Create or update label with the clicked position
    const existing = labels[frameIndex]
    const label: FrameLabel = {
      frameIndex,
      label: existing?.label ?? 'wrong',
      correctedX: x,
      correctedY: y,
    }
    const updated = { ...labels, [frameIndex]: label }
    setLabels(updated)
    if (videoBase) saveLabels(videoBase, updated)
    // Auto-advance to next frame (stay in placing mode)
    const maxFrame = data ? data.frames.length - 1 : totalFramesRef.current - 1
    if (frameIndex < maxFrame) {
      setFrameIndex(frameIndex + 1)
    }
  }, [placingBall, labels, frameIndex, videoBase, saveLabels, data])

  /** Clear label for current frame. */
  const handleClearLabel = useCallback(() => {
    setPlacingBall(false)
    const updated = { ...labels }
    delete updated[frameIndex]
    setLabels(updated)
    if (videoBase) saveLabels(videoBase, updated)
  }, [labels, frameIndex, videoBase, saveLabels])

  /** Export labels as a standalone JSON file download. */
  const handleExportLabels = useCallback(() => {
    if (!data || !videoBase) return
    const payload: LabelsData = {
      videoName: videoBase,
      totalFrames: data.totalFrames,
      labels,
    }
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${videoBase}_labels.json`
    a.click()
    URL.revokeObjectURL(a.href)
  }, [data, videoBase, labels])

  // ── Table keypoint label handlers ─────────────────────────────────────────

  /** Save table labels to the server. */
  const saveTableLabels = useCallback(async (base: string, newLabels: Record<number, TableFrameLabel>) => {
    const payload: TableLabelsData = {
      videoName: base,
      totalFrames: data?.totalFrames ?? 0,
      labels: newLabels,
    }
    try {
      await fetch(`/api/table-labels/${encodeURIComponent(base)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
    } catch {
      // silent save failure
    }
  }, [data])

  /** Fetch existing table labels JSON. */
  const fetchTableLabels = useCallback(async (base: string) => {
    setTableLabels({})
    const url = `/videos/${base}/${base}_table_labels.json`
    try {
      const res = await fetch(url)
      if (!res.ok) return
      const json = (await res.json()) as TableLabelsData
      if (json.labels && typeof json.labels === 'object') {
        const map: Record<number, TableFrameLabel> = {}
        for (const [k, v] of Object.entries(json.labels)) {
          map[Number(k)] = v as TableFrameLabel
        }
        setTableLabels(map)
      }
    } catch {
      // table labels file is optional
    }
  }, [])

  /** Handle placing a table keypoint on canvas click. */
  const handlePlaceTableKeypoint = useCallback((x: number, y: number) => {
    if (placingKeypoint < 0 || placingKeypoint >= TABLE_KEYPOINT_COUNT) return
    const existing = tableLabels[frameIndex]
    const points = [...(existing?.points ?? Array(TABLE_KEYPOINT_COUNT).fill(null))]
    // Ensure array is full length
    while (points.length < TABLE_KEYPOINT_COUNT) points.push(null)
    points[placingKeypoint] = { x, y }
    const updated: Record<number, TableFrameLabel> = {
      ...tableLabels,
      [frameIndex]: { frameIndex, points },
    }
    setTableLabels(updated)
    if (videoBase) saveTableLabels(videoBase, updated)
    // Auto-advance to next keypoint, or skip 100 frames and restart
    if (placingKeypoint < TABLE_KEYPOINT_COUNT - 1) {
      setPlacingKeypoint(placingKeypoint + 1)
    } else {
      // All 6 placed — jump 100 frames and restart at point 1
      const maxFrame = data ? data.frames.length - 1 : totalFramesRef.current - 1
      setFrameIndex(i => Math.min(i + 100, maxFrame))
      setPlacingKeypoint(0)
    }
  }, [placingKeypoint, tableLabels, frameIndex, videoBase, saveTableLabels])

  /** Clear table label for current frame and restart placing. */
  const handleClearTableLabel = useCallback(() => {
    setPlacingKeypoint(0)
    const updated = { ...tableLabels }
    delete updated[frameIndex]
    setTableLabels(updated)
    if (videoBase) saveTableLabels(videoBase, updated)
  }, [tableLabels, frameIndex, videoBase, saveTableLabels])

  /** Copy table points from the nearest labeled frame. */
  const handleCopyFromNearest = useCallback(() => {
    const frames = Object.keys(tableLabels).map(Number).sort((a, b) => a - b)
    if (frames.length === 0) return
    let nearest = frames[0]
    let bestDist = Math.abs(nearest - frameIndex)
    for (const f of frames) {
      const d = Math.abs(f - frameIndex)
      if (d < bestDist) { bestDist = d; nearest = f }
    }
    if (nearest === frameIndex) return // already labeled
    const source = tableLabels[nearest]
    if (!source) return
    const updated: Record<number, TableFrameLabel> = {
      ...tableLabels,
      [frameIndex]: { frameIndex, points: [...source.points] },
    }
    setTableLabels(updated)
    if (videoBase) saveTableLabels(videoBase, updated)
  }, [tableLabels, frameIndex, videoBase, saveTableLabels])

  /** Export table labels as a standalone JSON file download. */
  const handleExportTableLabels = useCallback(() => {
    if (!data || !videoBase) return
    const payload: TableLabelsData = {
      videoName: videoBase,
      totalFrames: data.totalFrames,
      labels: tableLabels,
    }
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${videoBase}_table_labels.json`
    a.click()
    URL.revokeObjectURL(a.href)
  }, [data, videoBase, tableLabels])

  /** Update crop config and save. */
  const handleCropChange = useCallback((preset: string) => {
    const h = data?.videoHeight ?? 1920
    const w = data?.videoWidth ?? 1072
    const side = Math.min(h, w)
    const isPortrait = h > w
    let cfg: CropConfig | null = null
    if (isPortrait) {
      // Portrait: crop vertically
      if (preset === 'top') cfg = { y: 0, h: side }
      else if (preset === 'center') cfg = { y: Math.round((h - side) / 2), h: side }
      else if (preset === 'bottom') cfg = { y: h - side, h: side }
    } else {
      // Landscape: crop horizontally (use x/w fields)
      if (preset === 'left') cfg = { y: 0, h: h, x: 0, w: side } as any
      else if (preset === 'center') cfg = { y: 0, h: h, x: Math.round((w - side) / 2), w: side } as any
      else if (preset === 'right') cfg = { y: 0, h: h, x: w - side, w: side } as any
    }
    // 'none' → null (no crop, full stretch)

    setCropConfig(cfg)
    // Save immediately
    if (videoBase) {
      const payload: LabelsData = {
        videoName: videoBase,
        totalFrames: data?.totalFrames ?? 0,
        labels,
        ...(cfg ? { crop: cfg } : {}),
      }
      fetch(`/api/labels/${encodeURIComponent(videoBase)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      }).catch(() => {})
    }
  }, [data, videoBase, labels])

  /** Called when the user picks a video file. */
  const handleVideoFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    // Derive base name (strip extension)
    const base = file.name.replace(/\.[^.]+$/, '')
    setVideoBase(base)

    // Set up video playback via object URL
    if (videoObjectUrl.current) URL.revokeObjectURL(videoObjectUrl.current)
    const url = URL.createObjectURL(file)
    videoObjectUrl.current = url
    setVideoSrc(url)

    // Fetch the appropriate JSON from the server
    fetchJson(base, showPoses, showBall)
    fetchContacts(base)
    fetchLabels(base)
    fetchTableLabels(base)
    // Fetch table YOLO detection
    fetch(`/videos/${base}/${base}_table_yolo.json`)
      .then(r => r.ok ? r.json() : null)
      .then(json => {
        if (json?.detection) {
          setTableYoloData({
            keypoints: json.detection.keypoints_refined || json.detection.keypoints_averaged || json.detection.keypoints,
          })
        } else {
          setTableYoloData(null)
        }
      })
      .catch(() => setTableYoloData(null))
    // Fetch table YOLO predict detection
    fetch(`/videos/${base}/${base}_table_yolo_predict.json`)
      .then(r => r.ok ? r.json() : null)
      .then(json => {
        if (json?.detection) {
          setTablePredictData({
            keypoints: json.detection.keypoints_averaged || json.detection.keypoints,
          })
        } else {
          setTablePredictData(null)
        }
      })
      .catch(() => setTablePredictData(null))
    fetchBallV5(base)
    fetchBallYolo(base)
    fetchRtmPoses(base)

    e.target.value = ''
  }

  /** Pick a video from the server dropdown. */
  const handleSelectVideo = async (base: string) => {
    if (!base) return
    setVideoBase(base)
    setFrameIndex(0)
    setPlaying(false)

    // Try to find video file first (before clearing data)
    if (videoObjectUrl.current) URL.revokeObjectURL(videoObjectUrl.current)
    videoObjectUrl.current = null

    // Find video extension from the videoList (already known from API)
    const entry = videoList.find(v => v.name === base)
    const foundVideoUrl = entry?.ext ? `/videos/${base}/${base}${entry.ext}` : null
    setVideoSrc(foundVideoUrl)

    // Fetch all associated data — await so data loads before video element switches
    await fetchJson(base, showPoses, showBall)
    fetchContacts(base)
    fetchLabels(base)
    fetchTableLabels(base)
    fetch(`/videos/${base}/${base}_table_yolo.json`)
      .then(r => r.ok ? r.json() : null)
      .then(json => {
        if (json?.detection) {
          setTableYoloData({
            keypoints: json.detection.keypoints_refined || json.detection.keypoints_averaged || json.detection.keypoints,
          })
        } else setTableYoloData(null)
      })
      .catch(() => setTableYoloData(null))
    fetch(`/videos/${base}/${base}_table_yolo_predict.json`)
      .then(r => r.ok ? r.json() : null)
      .then(json => {
        if (json?.detection) {
          setTablePredictData({
            keypoints: json.detection.keypoints_averaged || json.detection.keypoints,
          })
        } else setTablePredictData(null)
      })
      .catch(() => setTablePredictData(null))
    fetchBallV5(base)
    fetchBallYolo(base)
    fetchRtmPoses(base)
  }

  /** Re-fetch JSON when checkboxes change (if a video is already loaded). */
  const handleShowPoses = (next: boolean) => {
    setShowPoses(next)
    if (videoBase) fetchJson(videoBase, next, showBall)
  }
  const handleShowBall = (next: boolean) => {
    setShowBall(next)
    if (videoBase) fetchJson(videoBase, showPoses, next)
  }

  /** When video metadata loads and no JSON exists, create empty frame data for labeling. */
  const handleVideoMetadata = useCallback(() => {
    if (data) return  // JSON already loaded, no need
    const video = videoRef.current
    if (!video) return
    const durationMs = Math.round(video.duration * 1000)
    const intervalMs = 100
    const totalFrames = Math.floor(durationMs / intervalMs) + 1
    const frames = Array.from({ length: totalFrames }, (_, i) => ({
      frameIndex: i,
      timestampMs: i * intervalMs,
      landmarks: [] as Landmark[],
      ball: null,
    }))
    setData({
      topology: 'mediapipe33',
      intervalMs,
      totalFrames,
      videoDurationMs: durationMs,
      videoWidth: video.videoWidth || 720,
      videoHeight: video.videoHeight || 1280,
      exportTimestamp: Date.now(),
      frames,
    })
    setError(null)
  }, [data])

  // Seek video when frame changes while paused
  useEffect(() => {
    const video = videoRef.current
    if (!video || !data || playing) return
    const frame = data.frames[frameIndex]
    if (!frame) return
    video.pause()
    video.currentTime = frame.timestampMs / 1000
  }, [frameIndex, data, playing])

  // Playback: use native video playback for audio, sync frameIndex from video time
  useEffect(() => {
    const video = videoRef.current
    if (!data) return

    if (playing && video) {
      // Start native video playback (with audio)
      video.play().catch(() => {})

      // Sync frame index from video time using requestAnimationFrame
      let raf: number
      const syncFrame = () => {
        const t = video.currentTime * 1000
        // Find the closest frame
        let best = 0
        for (let i = 0; i < data.frames.length; i++) {
          if (data.frames[i].timestampMs <= t) best = i
          else break
        }
        setFrameIndex(prev => {
          if (prev !== best) return best
          return prev
        })
        if (best >= data.frames.length - 1) {
          setPlaying(false)
          return
        }
        raf = requestAnimationFrame(syncFrame)
      }
      raf = requestAnimationFrame(syncFrame)

      return () => cancelAnimationFrame(raf)
    } else if (!playing && video) {
      video.pause()
    }
  }, [playing, data])

  // Keep video muted state in sync
  useEffect(() => {
    const video = videoRef.current
    if (video) video.muted = muted
  }, [muted])

  // Keyboard shortcuts
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!data) return
      if (e.code === 'Space') { e.preventDefault(); handlePlayPause() }
      if (e.code === 'ArrowRight') { e.preventDefault(); setFrameIndex(i => Math.min(i + 1, data.frames.length - 1)) }
      if (e.code === 'ArrowLeft')  { e.preventDefault(); setFrameIndex(i => Math.max(i - 1, 0)) }
      if (e.code === 'BracketLeft' && contacts) {
        setFrameIndex(i => {
          const prev = contacts.contacts.filter(c => c.frameIndex < i)
          return prev.length ? prev[prev.length - 1].frameIndex : i
        })
      }
      if (e.code === 'BracketRight' && contacts) {
        setFrameIndex(i => {
          const next = contacts.contacts.find(c => c.frameIndex > i)
          return next ? next.frameIndex : i
        })
      }
      if (e.code === 'KeyM') setMuted(m => !m)
      // Label shortcuts
      if (e.code === 'KeyC') { e.preventDefault(); handleLabel('correct') }
      if (e.code === 'KeyW') { e.preventDefault(); handleLabel('wrong') }
      if (e.code === 'KeyN') { e.preventDefault(); handleLabel('no_ball') }
      if (e.code === 'Escape') { e.preventDefault(); setPlacingBall(false); setPlacingKeypoint(-1) }
      // Table keypoint shortcuts (1-6 select point, T toggle mode)
      if (showTableLabels && !e.shiftKey && !e.ctrlKey) {
        const digit = e.code.match(/^Digit([1-6])$/)?.[1]
        if (digit) { e.preventDefault(); setPlacingKeypoint(Number(digit) - 1); return }
      }
      if (e.code === 'KeyT' && !e.shiftKey && !e.ctrlKey) {
        e.preventDefault()
        setShowTableLabels(v => {
          if (!v) setPlacingKeypoint(0) // start placing when toggling on
          return !v
        })
      }
      if (e.code === 'KeyM' && !e.shiftKey && !e.ctrlKey) {
        e.preventDefault()
        setShowTableView(v => !v)
      }
      // Shift+Arrow: skip 100 frames (useful for static camera labeling)
      if (e.shiftKey && e.code === 'ArrowRight') {
        e.preventDefault()
        setFrameIndex(i => Math.min(i + 100, (data?.totalFrames ?? 1) - 1))
      }
      if (e.shiftKey && e.code === 'ArrowLeft') {
        e.preventDefault()
        setFrameIndex(i => Math.max(i - 100, 0))
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [data, contacts, handlePlayPause, handleLabel, showTableLabels])

  const frame = data?.frames[frameIndex] ?? null
  const contactSet = new Set(contacts?.contacts.map(c => c.frameIndex) ?? [])
  const isContactFrame = contactSet.has(frameIndex)
  const currentContact = contacts?.contacts.find(c => c.frameIndex === frameIndex) ?? null
  const ball  = frame?.ball ?? null

  // Find matching RTM pose frame index by timestampMs
  const rtmFrameIdx = (() => {
    if (!rtmData || !frame) return -1
    let best = 0
    let bestDiff = Infinity
    for (let i = 0; i < rtmData.frames.length; i++) {
      const diff = Math.abs(rtmData.frames[i].timestampMs - frame.timestampMs)
      if (diff < bestDiff) { bestDiff = diff; best = i }
    }
    return bestDiff < (rtmData.intervalMs ?? 200) ? best : -1
  })()
  const rtmFrame = rtmFrameIdx >= 0 ? rtmData!.frames[rtmFrameIdx] : null

  // Find matching Ball V5 frame index by timestampMs
  const ballV5FrameIdx = (() => {
    if (!ballV5Data || !frame) return -1
    let best = 0
    let bestDiff = Infinity
    for (let i = 0; i < ballV5Data.frames.length; i++) {
      const diff = Math.abs(ballV5Data.frames[i].timestampMs - frame.timestampMs)
      if (diff < bestDiff) { bestDiff = diff; best = i }
    }
    return bestDiff < (ballV5Data.intervalMs ?? 200) ? best : -1
  })()
  const ballV5Frame = ballV5FrameIdx >= 0 ? ballV5Data!.frames[ballV5FrameIdx] : null

  // Find matching Ball YOLO frame index by timestampMs
  const ballYoloFrameIdx = (() => {
    if (!ballYoloData || !frame) return -1
    let best = 0
    let bestDiff = Infinity
    for (let i = 0; i < ballYoloData.frames.length; i++) {
      const diff = Math.abs(ballYoloData.frames[i].timestampMs - frame.timestampMs)
      if (diff < bestDiff) { bestDiff = diff; best = i }
    }
    return bestDiff < (ballYoloData.intervalMs ?? 200) ? best : -1
  })()
  const ballYoloFrame = ballYoloFrameIdx >= 0 ? ballYoloData!.frames[ballYoloFrameIdx] : null

  const cbClass = 'flex items-center gap-1.5 cursor-pointer select-none text-sm text-gray-300'

  if (route === 'dataset') {
    return <DatasetBrowser onClose={() => navigate('main')} />
  }
  if (route === 'mannequin') {
    return <MannequinEditor onClose={() => navigate('main')} />
  }
  if (route === 'drill2') {
    return <Drill2Preview onClose={() => navigate('main')} />
  }

  return (
    <div className="h-screen bg-gray-950 text-gray-100 flex flex-col select-none overflow-hidden">

      {/* Header */}
      <header className="border-b border-gray-800 px-4 py-2.5 flex items-center gap-4 shrink-0 overflow-visible z-50 relative">
        <h1 className="font-semibold text-white">Poses Viewer</h1>

        <label className="flex items-center gap-1.5 cursor-pointer bg-gray-800 hover:bg-gray-700 px-3 py-1.5 rounded text-sm transition-colors">
          <Film size={14} />
          Open Video
          <input type="file" accept="video/*,.mp4,.MP4,.mov,.webm" className="hidden" onChange={handleVideoFile} />
        </label>

        <button
          className="bg-fuchsia-700 hover:bg-fuchsia-600 px-3 py-1.5 rounded text-sm"
          onClick={() => navigate('drill2')}
          title="Animate frame 57 → 63 from andrii_1 poses"
        >
          + Add Drill 2
        </button>

        <button
          className="bg-emerald-700 hover:bg-emerald-600 px-3 py-1.5 rounded text-sm"
          onClick={() => navigate('mannequin')}
          title="Interactive 3D mannequin editor — click joints, coloured body parts"
        >
          + Mannequin Editor
        </button>

        {videoList.length > 0 && (
          <select
            className="bg-gray-800 text-sm text-gray-300 rounded px-2 py-1.5 border border-gray-700 max-w-48"
            value={videoBase ?? ''}
            onChange={e => handleSelectVideo(e.target.value)}
          >
            <option value="">Select video...</option>
            {videoList.map(v => (
              <option key={v.name} value={v.name}>{v.name}{v.ext}</option>
            ))}
          </select>
        )}

        <label className={cbClass}>
          <input type="checkbox" checked={showPoses} onChange={e => handleShowPoses(e.target.checked)} className="accent-blue-500" />
          Poses
        </label>
        <label className={cbClass} title="RTMPose COCO-17 skeleton (_poses_rtm.json)">
          <input type="checkbox" checked={showRtmPoses} onChange={e => setShowRtmPoses(e.target.checked)} className="accent-amber-400" />
          RTM
        </label>
        <label className={cbClass}>
          <input type="checkbox" checked={showContacts} onChange={e => setShowContacts(e.target.checked)} className="accent-orange-500" />
          Contacts
        </label>
        <label className={cbClass}>
          <input type="checkbox" checked={showLabels} onChange={e => setShowLabels(e.target.checked)} className="accent-green-500" />
          Labels
        </label>

        <MultiSelect label="Ball" items={[
          { label: 'Ball', checked: showBall, onChange: handleShowBall, accent: 'accent-yellow-400' },
          { label: 'Ball V5', checked: showBallV5, onChange: setShowBallV5, accent: 'accent-cyan-400' },
          { label: 'Ball YOLO', checked: showBallYolo, onChange: setShowBallYolo, accent: 'accent-lime-400' },
        ]} />

        <MultiSelect label="Trajectory" items={[
          { label: 'Trajectory', checked: showTrajectory, onChange: setShowTrajectory, accent: 'accent-fuchsia-400' },
          { label: 'V2', checked: showTrajectoryV2, onChange: setShowTrajectoryV2, accent: 'accent-emerald-400' },
          { label: 'V3', checked: showTrajectoryV3, onChange: setShowTrajectoryV3, accent: 'accent-cyan-400' },
          { label: 'V4', checked: showTrajectoryV4, onChange: setShowTrajectoryV4, accent: 'accent-amber-400' },
          { label: '3D', checked: showTrajectory3D, onChange: setShowTrajectory3D, accent: 'accent-pink-400' },
          { label: '3Dv2', checked: showTrajectory3Dv2, onChange: setShowTrajectory3Dv2, accent: 'accent-violet-400' },
        ]} footer={showTrajectory ? (
          <div className="px-3 py-1.5 border-t border-gray-700">
            <select
              className="bg-gray-900 text-xs text-gray-300 rounded px-1.5 py-0.5 border border-gray-700 w-full"
              value={trajectoryMode}
              onChange={e => setTrajectoryMode(e.target.value as 'fitted' | 'predict')}
            >
              <option value="fitted">Fitted</option>
              <option value="predict">Predict</option>
            </select>
          </div>
        ) : undefined} />

        <MultiSelect label="Table" items={[
          { label: 'Table(Markup)', checked: showTableLabels, onChange: setShowTableLabels, accent: 'accent-purple-500' },
          { label: 'Table (Markup View)', checked: showTableView, onChange: setShowTableView, accent: 'accent-purple-400' },
          { label: 'Table YOLO', checked: showTableYolo, onChange: setShowTableYolo, accent: 'accent-violet-500' },
          { label: 'Table(predict)', checked: showTablePredict, onChange: setShowTablePredict, accent: 'accent-teal-500' },
          { label: 'Table Grid', checked: showTableGrid, onChange: setShowTableGrid, accent: 'accent-emerald-500' },
          { label: 'Grid(Marked)', checked: showTableGridMarked, onChange: setShowTableGridMarked, accent: 'accent-lime-500' },
        ]} />
        <button
          className="px-3 py-1.5 rounded text-sm bg-purple-800 hover:bg-purple-700 transition-colors text-purple-200"
          onClick={() => navigate('dataset')}
        >
          Dataset
        </button>

        <button
          className="p-1.5 rounded hover:bg-gray-800 transition-colors text-gray-400 hover:text-gray-200"
          onClick={() => setMuted(m => !m)}
          title={muted ? 'Unmute (M)' : 'Mute (M)'}
        >
          {muted ? <VolumeX size={16} /> : <Volume2 size={16} />}
        </button>

        {/* Zoom controls */}
        <div className="flex items-center gap-1 text-xs text-gray-400">
          <button className="px-1.5 py-0.5 rounded hover:bg-gray-800" onClick={() => setZoom(z => Math.max(0.5, z / 1.15))} title="Zoom out">−</button>
          <span className="w-10 text-center font-mono">{Math.round(zoom * 100)}%</span>
          <button className="px-1.5 py-0.5 rounded hover:bg-gray-800" onClick={() => setZoom(z => Math.min(5, z * 1.15))} title="Zoom in">+</button>
          {(zoom !== 1 || pan.x !== 0 || pan.y !== 0) && (
            <button className="px-1.5 py-0.5 rounded hover:bg-gray-800" onClick={() => { setZoom(1); setPan({ x: 0, y: 0 }) }} title="Reset zoom">Reset</button>
          )}
        </div>

        <span className="text-gray-400 text-sm truncate">{videoBase ?? ''}</span>
        {error && <span className="text-red-400 text-sm truncate max-w-xs">{error}</span>}
      </header>

      <>
      {/* Body */}
      {data ? (
        <div className="flex flex-1 min-h-0">

          {/* Video + canvas overlay */}
          <div
            ref={viewportRef}
            className="flex-1 flex items-center justify-center p-2 min-w-0 overflow-hidden"
            onWheel={e => {
              e.preventDefault()
              const rect = viewportRef.current!.getBoundingClientRect()
              // Cursor position relative to viewport center
              const cx = e.clientX - rect.left - rect.width / 2
              const cy = e.clientY - rect.top - rect.height / 2
              const factor = e.deltaY < 0 ? 1.15 : 1 / 1.15
              setZoom(z => {
                const next = Math.max(0.5, Math.min(5, z * factor))
                const actualFactor = next / z
                // Adjust pan so the point under cursor stays fixed
                setPan(p => ({
                  x: cx - actualFactor * (cx - p.x),
                  y: cy - actualFactor * (cy - p.y),
                }))
                return next
              })
            }}
            onMouseDown={e => {
              if (e.button === 1 || (e.button === 0 && e.altKey)) {
                e.preventDefault()
                isPanning.current = true
                panStart.current = { x: e.clientX - pan.x, y: e.clientY - pan.y }
              }
            }}
            onMouseMove={e => {
              if (isPanning.current) {
                setPan({ x: e.clientX - panStart.current.x, y: e.clientY - panStart.current.y })
              }
            }}
            onMouseUp={() => { isPanning.current = false }}
            onMouseLeave={() => { isPanning.current = false }}
          >
            <div style={{
              position: 'relative',
              display: 'inline-block',
              lineHeight: 0,
              transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
              transformOrigin: 'center center',
            }}>
              {videoSrc && (
                <video
                  ref={videoRef}
                  src={videoSrc}
                  style={{ height: 'calc(100vh - 120px)', width: 'auto', display: 'block' }}
                  playsInline
                  className="rounded-lg border border-gray-800"
                  onError={() => setVideoSrc(null)}
                  onLoadedMetadata={handleVideoMetadata}
                />
              )}
              <div style={videoSrc
                ? { position: 'absolute', top: 0, left: 0, width: '100%', height: '100%' }
                : {}
              }>
                <PoseCanvas
                  topology={data?.topology ?? 'mediapipe33'}
                  frame={frame}
                  frames={data.frames}
                  frameIndex={frameIndex}
                  videoWidth={data.videoWidth}
                  videoHeight={data.videoHeight}
                  transparent={!!videoSrc}
                  showPoses={showPoses}
                  showRtmPoses={showRtmPoses}
                  rtmFrame={rtmFrame}
                  rtmTopology={rtmData?.topology ?? 'coco17'}
                  showBall={showBall}
                  showBallV5={showBallV5}
                  ballV5Frame={ballV5Frame}
                  ballV5Frames={ballV5Data?.frames}
                  ballV5FrameIdx={ballV5FrameIdx}
                  showBallYolo={showBallYolo}
                  ballYoloFrame={ballYoloFrame}
                  ballYoloFrames={ballYoloData?.frames}
                  ballYoloFrameIdx={ballYoloFrameIdx}
                  isContactFrame={showContacts && isContactFrame}
                  contactType={currentContact?.type}
                  frameLabel={showLabels ? labels[frameIndex] ?? null : null}
                  placingBall={placingBall}
                  onPlaceBall={handlePlaceBall}
                  showTableLabels={showTableLabels}
                  tableFrameLabel={null}
                  placingKeypoint={showTableLabels ? placingKeypoint : -1}
                  onPlaceTableKeypoint={handlePlaceTableKeypoint}
                  onResetTableLabel={handleClearTableLabel}
                  showTrajectory={showTrajectory}
                  trajectoryMode={trajectoryMode}
                  trajectorySegments={trajectorySegments}
                  predictiveTrajectory={predictiveTraj}
                />
                {showTrajectoryV2 && (
                  <TrajectoryV2Overlay
                    trajectory={predictiveTrajV2}
                    frameIndex={frameIndex}
                  />
                )}
                {showTrajectoryV3 && (
                  <TrajectoryV3Overlay
                    trajectory={predictiveTrajV3}
                    frameIndex={frameIndex}
                  />
                )}
                {showTrajectoryV4 && (
                  <TrajectoryV4Overlay
                    trajectory={predictiveTrajV4}
                    frameIndex={frameIndex}
                  />
                )}
                {showTrajectory3D && (
                  <Trajectory3DOverlay
                    trajectory={trajectory3D}
                    frameIndex={frameIndex}
                  />
                )}
                {showTrajectory3Dv2 && (
                  <Trajectory3Dv2Overlay
                    trajectory={trajectory3Dv2}
                    frameIndex={frameIndex}
                  />
                )}
                {(showTableLabels || showTableView) && (
                  <TableLabelsOverlay tableFrameLabel={tableLabels[frameIndex] ?? null} />
                )}
                {showTableYolo && tableYoloData && (
                  <TableDetectOverlay keypoints={tableYoloData.keypoints} color="#8b5cf6" />
                )}
                {showTablePredict && tablePredictData && (
                  <TableDetectOverlay keypoints={tablePredictData.keypoints} color="#14b8a6" />
                )}
                {showTableGrid && (
                  <TableGridOverlay
                    keypoints={tablePredictData?.keypoints ?? tableYoloData?.keypoints ?? null}
                    videoWidth={data.videoWidth}
                    videoHeight={data.videoHeight}
                    ballX={ball?.x}
                    ballY={ball?.y}
                    ballDetected={ball?.status === 'DETECTED'}
                  />
                )}
                {showTableGridMarked && (() => {
                  // Try each labeled frame until one produces a valid homography
                  let result: ReturnType<typeof computeHomographyFromPartial> = null
                  for (const label of Object.values(tableLabels)) {
                    if (!label || label.points.filter(Boolean).length < 4) continue
                    result = computeHomographyFromPartial(
                      label.points, data.videoWidth, data.videoHeight,
                    )
                    if (result) break
                  }
                  if (!result) return null
                  const mkps = result.allKeypoints.map(p => ({ x: p.x, y: p.y, confidence: 1 }))
                  return (
                    <TableGridOverlay
                      keypoints={mkps}
                      videoWidth={data.videoWidth}
                      videoHeight={data.videoHeight}
                      ballX={ball?.x}
                      ballY={ball?.y}
                      ballDetected={ball?.status === 'DETECTED'}
                      color="#84cc16"
                    />
                  )
                })()}
              </div>
            </div>
          </div>

          {/* Sidebar */}
          <aside className="w-52 shrink-0 border-l border-gray-800 p-4 flex flex-col gap-5 text-sm overflow-y-auto">

            <div>
              <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Frame</div>
              <div className="font-mono">
                <span className="text-2xl font-bold">{frameIndex}</span>
                <span className="text-gray-500"> / {data.totalFrames - 1}</span>
              </div>
              <div className="text-gray-400 text-xs mt-1">{frame?.timestampMs} ms</div>
            </div>

            <div>
              <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Ball</div>
              {ball ? (
                <div className="space-y-1 font-mono text-xs">
                  <div className="flex items-center gap-1.5 text-yellow-400">
                    <span className="w-2 h-2 rounded-full bg-yellow-400 shrink-0" />
                    DETECTED
                  </div>
                  <div className="text-gray-300">x&nbsp;&nbsp;&nbsp;{ball.x.toFixed(3)}</div>
                  <div className="text-gray-300">y&nbsp;&nbsp;&nbsp;{ball.y.toFixed(3)}</div>
                  <div className="text-gray-300">r&nbsp;&nbsp;&nbsp;{ball.radiusPx.toFixed(1)} px</div>
                  <div className="text-gray-400">conf {(ball.confidence * 100).toFixed(0)}%</div>
                </div>
              ) : (
                <div className="flex items-center gap-1.5 text-gray-600 text-xs">
                  <span className="w-2 h-2 rounded-full bg-gray-700 shrink-0" />
                  NOT DETECTED
                </div>
              )}
            </div>

            {showBallV5 && (
              <div>
                <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Ball V5</div>
                {ballV5Frame?.ball ? (
                  <div className="space-y-1 font-mono text-xs">
                    <div className="flex items-center gap-1.5 text-cyan-400">
                      <span className="w-2 h-2 rounded-full bg-cyan-400 shrink-0" />
                      DETECTED
                    </div>
                    <div className="text-gray-300">x&nbsp;&nbsp;&nbsp;{ballV5Frame.ball.x.toFixed(3)}</div>
                    <div className="text-gray-300">y&nbsp;&nbsp;&nbsp;{ballV5Frame.ball.y.toFixed(3)}</div>
                    <div className="text-gray-300">r&nbsp;&nbsp;&nbsp;{ballV5Frame.ball.radiusPx.toFixed(1)} px</div>
                    <div className="text-gray-400">conf {(ballV5Frame.ball.confidence * 100).toFixed(0)}%</div>
                  </div>
                ) : (
                  <div className="flex items-center gap-1.5 text-gray-600 text-xs">
                    <span className="w-2 h-2 rounded-full bg-gray-700 shrink-0" />
                    {ballV5Data ? 'NOT DETECTED' : 'No V5 data'}
                  </div>
                )}
              </div>
            )}

            {showBallYolo && (
              <div>
                <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Ball YOLO</div>
                {ballYoloFrame?.ball ? (
                  <div className="space-y-1 font-mono text-xs">
                    <div className="flex items-center gap-1.5 text-lime-400">
                      <span className="w-2 h-2 rounded-full bg-lime-400 shrink-0" />
                      DETECTED
                    </div>
                    <div className="text-gray-300">x&nbsp;&nbsp;&nbsp;{ballYoloFrame.ball.x.toFixed(3)}</div>
                    <div className="text-gray-300">y&nbsp;&nbsp;&nbsp;{ballYoloFrame.ball.y.toFixed(3)}</div>
                    <div className="text-gray-300">r&nbsp;&nbsp;&nbsp;{ballYoloFrame.ball.radiusPx.toFixed(1)} px</div>
                    <div className="text-gray-400">conf {(ballYoloFrame.ball.confidence * 100).toFixed(0)}%</div>
                  </div>
                ) : (
                  <div className="flex items-center gap-1.5 text-gray-600 text-xs">
                    <span className="w-2 h-2 rounded-full bg-gray-700 shrink-0" />
                    {ballYoloData ? 'NOT DETECTED' : 'No YOLO data'}
                  </div>
                )}
              </div>
            )}

            {showTrajectory && (() => {
              if (trajectoryMode === 'predict') {
                return predictiveTraj ? (
                  <div>
                    <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory (predict)</div>
                    <div className="space-y-1 font-mono text-xs">
                      <div className="text-fuchsia-400">{predictiveTraj.detectionCount} detections</div>
                      <div className="text-gray-300">arc from frame {predictiveTraj.segmentStartFrame}</div>
                      <div className="text-gray-300">{predictiveTraj.pastPositions.length} past + {predictiveTraj.predictedPositions.length} predicted</div>
                      <div className="text-gray-400">cy {predictiveTraj.fit.cy.toFixed(5)}</div>
                    </div>
                  </div>
                ) : (
                  <div>
                    <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory (predict)</div>
                    <div className="text-gray-600 text-xs">Not enough data</div>
                  </div>
                )
              }
              // Fitted mode
              const currentSeg = trajectorySegments.find(s => frameIndex >= s.startFrameIndex && frameIndex <= s.endFrameIndex)
              return trajectorySegments.length > 0 ? (
                <div>
                  <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory (fitted)</div>
                  <div className="space-y-1 font-mono text-xs">
                    <div className="text-fuchsia-400">{trajectorySegments.length} segments</div>
                    {currentSeg ? (
                      <>
                        <div className="text-gray-300">Seg {currentSeg.segmentIndex + 1}/{trajectorySegments.length}</div>
                        <div className="text-gray-300">frames {currentSeg.startFrameIndex}–{currentSeg.endFrameIndex}</div>
                        <div className="text-gray-400">RMS {currentSeg.fitRmsError.toFixed(4)}</div>
                        <div className="text-gray-400">{currentSeg.fittedPositions.filter(p => p.source === 'INTERPOLATED').length} interpolated</div>
                        {currentSeg.contactBefore && (
                          <div className="text-orange-400">← {currentSeg.contactBefore.type}</div>
                        )}
                        {currentSeg.contactAfter && (
                          <div className="text-orange-400">→ {currentSeg.contactAfter.type}</div>
                        )}
                      </>
                    ) : (
                      <div className="text-gray-600">Between segments</div>
                    )}
                  </div>
                </div>
              ) : null
            })()}

            {showTrajectoryV2 && (() => {
              return predictiveTrajV2 ? (
                <div>
                  <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory V2</div>
                  <div className="space-y-1 font-mono text-xs">
                    <div className="text-emerald-400">{predictiveTrajV2.detectionCount} detections</div>
                    <div className="text-gray-300">arc from frame {predictiveTrajV2.segmentStartFrame}</div>
                    <div className="text-gray-300">{predictiveTrajV2.pastPositions.length} past + {predictiveTrajV2.predictedPositions.length} predicted</div>
                    <div className="text-gray-300">{predictiveTrajV2.predictedBounces.length} bounce{predictiveTrajV2.predictedBounces.length !== 1 ? 's' : ''} predicted</div>
                    {predictiveTrajV2.tableSurface.isValid && (
                      <div className="text-gray-400">table: {predictiveTrajV2.tableSurface.bouncePoints.length} obs · slope {predictiveTrajV2.tableSurface.slope.toFixed(4)}</div>
                    )}
                    <div className="text-gray-400">cy {predictiveTrajV2.fit.cy.toFixed(5)} · cx {predictiveTrajV2.fit.cx.toFixed(5)}</div>
                  </div>
                </div>
              ) : (
                <div>
                  <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory V2</div>
                  <div className="text-gray-600 text-xs">Not enough data</div>
                </div>
              )
            })()}

            {showTrajectoryV3 && (() => {
              return predictiveTrajV3 ? (
                <div>
                  <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory V3</div>
                  <div className="space-y-1 font-mono text-xs">
                    <div className="text-cyan-400">{predictiveTrajV3.detectionCount} detections</div>
                    <div className="text-gray-300">arc from frame {predictiveTrajV3.segmentStartFrame}</div>
                    <div className="text-gray-300">{predictiveTrajV3.pastPositions.length} past + {predictiveTrajV3.predictedPositions.length} predicted</div>
                    <div className="text-gray-300">{predictiveTrajV3.predictedBounces.length} bounce{predictiveTrajV3.predictedBounces.length !== 1 ? 's' : ''} predicted</div>
                    {predictiveTrajV3.spinClass !== 'flat' && (
                      <div className={predictiveTrajV3.spinClass === 'topspin' ? 'text-red-400' : 'text-blue-400'}>{predictiveTrajV3.spinClass}</div>
                    )}
                    {predictiveTrajV3.tableSurface.isValid && (
                      <div className="text-gray-400">table: {predictiveTrajV3.tableSurface.bouncePoints.length} obs · slope {predictiveTrajV3.tableSurface.slope.toFixed(4)}</div>
                    )}
                    <div className="text-gray-400">cy {predictiveTrajV3.fit.cy.toFixed(5)} · dy {predictiveTrajV3.fit.dy.toFixed(6)}</div>
                  </div>
                </div>
              ) : (
                <div>
                  <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory V3</div>
                  <div className="text-gray-600 text-xs">Not enough data</div>
                </div>
              )
            })()}

            {showTrajectoryV4 && (() => {
              return predictiveTrajV4 ? (
                <div>
                  <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory V4</div>
                  <div className="space-y-1 font-mono text-xs">
                    <div className="text-amber-400">{predictiveTrajV4.detectionCount} detections</div>
                    <div className="text-gray-300">arc from frame {predictiveTrajV4.segmentStartFrame}</div>
                    <div className="text-gray-300">{predictiveTrajV4.pastPositions.length} past + {predictiveTrajV4.predictedPositions.length} predicted</div>
                    <div className="text-gray-400">cy {predictiveTrajV4.fit.cy.toFixed(5)}</div>
                  </div>
                </div>
              ) : (
                <div>
                  <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory V4</div>
                  <div className="text-gray-600 text-xs">Not enough data</div>
                </div>
              )
            })()}

            {showTrajectory3D && (() => {
              return trajectory3D && trajectory3D.positions.length > 0 ? (
                <div>
                  <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory 3D</div>
                  <div className="space-y-1 font-mono text-xs">
                    <div className="text-pink-400">{trajectory3D.positions.length} positions</div>
                    <div className="text-gray-300">{trajectory3D.bounces.length} bounce{trajectory3D.bounces.length !== 1 ? 's' : ''}</div>
                    {trajectory3D.bounces.map((b, i) => (
                      <div key={i} className="text-gray-400">
                        #{b.frameIndex}: ({b.x_cm.toFixed(0)}, {b.y_cm.toFixed(0)}) cm
                      </div>
                    ))}
                    {trajectory3D.positions.length > 0 && (() => {
                      const last = trajectory3D.positions[trajectory3D.positions.length - 1]
                      return (
                        <div className="text-gray-400">
                          z={last.z_cm.toFixed(1)}cm · ({last.x_cm.toFixed(0)},{last.y_cm.toFixed(0)})cm
                        </div>
                      )
                    })()}
                  </div>
                </div>
              ) : (
                <div>
                  <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory 3D</div>
                  <div className="text-gray-600 text-xs">Need ball + table labels</div>
                </div>
              )
            })()}

            {showTrajectory3Dv2 && (() => {
              return trajectory3Dv2 && trajectory3Dv2.positions.length > 0 ? (
                <div>
                  <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory 3Dv2</div>
                  <div className="space-y-1 font-mono text-xs">
                    <div className="text-violet-400">{trajectory3Dv2.positions.length} pts · {trajectory3Dv2.arcs.length} arcs</div>
                    <div className="text-gray-300">{trajectory3Dv2.bounces.length} bounce{trajectory3Dv2.bounces.length !== 1 ? 's' : ''}</div>
                    {trajectory3Dv2.outlierFrames.length > 0 && (
                      <div className="text-red-400">{trajectory3Dv2.outlierFrames.length} outliers</div>
                    )}
                  </div>
                </div>
              ) : (
                <div>
                  <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Trajectory 3Dv2</div>
                  <div className="text-gray-600 text-xs">Need ball + table labels</div>
                </div>
              )
            })()}

            <div>
              <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Contacts</div>
              {contacts && contacts.contacts.length > 0 ? (
                <div className="space-y-1">
                  {isContactFrame && currentContact ? (
                    <div className="flex items-center gap-1.5 text-xs font-mono"
                      style={{ color: currentContact.type === 'table' ? '#fb923c' : '#a78bfa' }}>
                      <span className="w-2 h-2 rounded-full shrink-0"
                        style={{ background: currentContact.type === 'table' ? '#fb923c' : '#a78bfa' }} />
                      {currentContact.type === 'table' ? 'TABLE' : 'RACKET'} ({(currentContact.confidence * 100).toFixed(0)}%)
                    </div>
                  ) : (
                    <div className="flex items-center gap-1.5 text-gray-600 text-xs">
                      <span className="w-2 h-2 rounded-full bg-gray-700 shrink-0" />
                      —
                    </div>
                  )}
                  <div className="text-xs text-gray-500">
                    {contacts.contacts.length} contacts
                    ({contacts.contacts.filter(c => c.type === 'table').length} table, {contacts.contacts.filter(c => c.type === 'racket').length} racket)
                  </div>
                  <div className="max-h-32 overflow-y-auto space-y-0.5 mt-1">
                    {contacts.contacts.map((c, i) => (
                      <button key={i}
                        className={`block w-full text-left px-1.5 py-0.5 rounded text-xs font-mono
                          ${c.frameIndex === frameIndex ? 'bg-orange-500/20 text-orange-300' : 'text-gray-500 hover:text-gray-300 hover:bg-gray-800'}`}
                        onClick={() => { setFrameIndex(c.frameIndex); setPlaying(false) }}>
                        <span style={{ color: c.type === 'table' ? '#fb923c' : '#a78bfa' }}>
                          {c.type === 'table' ? 'T' : 'R'}
                        </span>
                        {' '}#{c.frameIndex} @ {c.timestampMs}ms
                      </button>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="text-xs text-gray-600">No contacts data</div>
              )}
            </div>

            {showTableLabels && (
              <TableLabelPanel
                frameIndex={frameIndex}
                totalFrames={data.totalFrames}
                tableLabels={tableLabels}
                currentTableLabel={tableLabels[frameIndex] ?? null}
                placingKeypoint={placingKeypoint}
                onSelectKeypoint={setPlacingKeypoint}
                onClearTableLabel={handleClearTableLabel}
                onCopyFromNearest={handleCopyFromNearest}
                onExportTableLabels={handleExportTableLabels}
                placingBall={placingBall}
                onStartBallPlacing={() => { setPlacingKeypoint(-1); setPlacingBall(true) }}
                hasBallLabel={!!(labels[frameIndex]?.correctedX != null)}
              />
            )}

            {showLabels && (
              <LabelPanel
                frameIndex={frameIndex}
                totalFrames={data.totalFrames}
                currentLabel={labels[frameIndex] ?? null}
                hasBallDetection={!!ball}
                labels={labels}
                placingBall={placingBall}
                onLabel={handleLabel}
                onClearLabel={handleClearLabel}
                onExport={handleExportLabels}
              />
            )}

            <div>
              <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Video</div>
              <div className="text-xs text-gray-400 space-y-1 font-mono">
                <div>{data.videoWidth} × {data.videoHeight}</div>
                <div>{data.intervalMs} ms / frame</div>
                <div>{(data.videoDurationMs / 1000).toFixed(2)} s</div>
              </div>
            </div>

            {showLabels && (
              <div>
                <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Training Crop</div>
                <div className="flex gap-1">
                  {(data.videoHeight > data.videoWidth
                    ? ['top', 'center', 'bottom'] as const
                    : ['left', 'center', 'right'] as const
                  ).map(preset => (
                    <button
                      key={preset}
                      className={`px-2 py-1 rounded text-xs transition-colors ${
                        cropConfig && (
                          (data.videoHeight > data.videoWidth
                            ? cropConfig.y === (preset === 'top' ? 0 : preset === 'center' ? Math.round((data.videoHeight - Math.min(data.videoHeight, data.videoWidth)) / 2) : data.videoHeight - Math.min(data.videoHeight, data.videoWidth))
                            : (cropConfig as any).x === (preset === 'left' ? 0 : preset === 'center' ? Math.round((data.videoWidth - Math.min(data.videoHeight, data.videoWidth)) / 2) : data.videoWidth - Math.min(data.videoHeight, data.videoWidth))
                          )
                        )
                          ? 'bg-blue-600 text-white'
                          : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
                      }`}
                      onClick={() => handleCropChange(preset)}
                    >
                      {preset}
                    </button>
                  ))}
                </div>
                {cropConfig && (
                  <div className="text-xs text-gray-500 font-mono mt-1">
                    {cropConfig.x != null ? `x=${cropConfig.x} w=${cropConfig.w} ` : ''}y={cropConfig.y} h={cropConfig.h}
                  </div>
                )}
              </div>
            )}

            <div>
              <div className="text-gray-500 text-xs uppercase tracking-wider mb-1.5">Legend</div>
              <div className="text-xs space-y-1">
                <div className="flex items-center gap-1.5">
                  <span className="w-4 h-0.5 bg-blue-500 shrink-0" />
                  <span className="text-gray-400">Left side</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-4 h-0.5 bg-red-500 shrink-0" />
                  <span className="text-gray-400">Right side</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-4 h-0.5 bg-green-500 shrink-0" />
                  <span className="text-gray-400">Center</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-4 h-0.5 bg-yellow-400 shrink-0" />
                  <span className="text-gray-400">Ball</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-4 h-0.5 bg-cyan-400 shrink-0" />
                  <span className="text-gray-400">Ball V5</span>
                </div>
              </div>
            </div>

            <div className="mt-auto text-xs text-gray-600 space-y-0.5">
              <div>Space — play/pause</div>
              <div>← → — prev/next</div>
              <div>[ ] — prev/next contact</div>
              <div>M — mute/unmute</div>
            </div>
          </aside>
        </div>
      ) : (
        <div className="flex-1 flex items-center justify-center flex-col gap-3 text-gray-500 text-sm">
          {videoSrc ? (
            /* Video loaded but no JSON yet — play with native controls, onLoadedMetadata will create empty data */
            <video
              ref={videoRef}
              src={videoSrc}
              controls
              style={{ maxHeight: 'calc(100vh - 160px)', maxWidth: '100%' }}
              className="rounded-lg border border-gray-800"
              onError={() => setVideoSrc(null)}
              onLoadedMetadata={handleVideoMetadata}
            />
          ) : (
            <>
              <Film size={32} className="text-gray-700" />
              <span>Open a video file to get started</span>
            </>
          )}
          {error && <span className="text-red-400 text-xs max-w-sm text-center">{error}</span>}
        </div>
      )}

      {data && (
        <FrameControls
          frameIndex={frameIndex}
          totalFrames={data.totalFrames}
          playing={playing}
          onFrameChange={setFrameIndex}
          onPlayPause={handlePlayPause}
          onFirst={() => { setFrameIndex(0); setPlaying(false) }}
          onLast={() => { setFrameIndex(data.totalFrames - 1); setPlaying(false) }}
          contacts={showContacts ? contacts : null}
          labels={showLabels ? labels : undefined}
          tableLabels={(showTableLabels || showTableView) ? tableLabels : undefined}
        />
      )}
      </>
    </div>
  )
}
