// Interactive 3D mannequin editor — Phase 1 target from the spec in
// linear-frolicking-crayon.md.
//
// Wraps Drill2Mannequin in a single-anchor editing context: no START/END
// phases, no fixture playback. The anchor starts at MIDPOINT_POSE (every
// slider at the middle of its range — the "zero-input" default, distinct
// from the pre-loaded athletic crouch NEUTRAL_POSE used by DrillEditor)
// and the user shapes it via sliders or direct joint selection. Built on
// top of SelectionProvider so the canvas, sliders, HUD, and legend can all
// observe the same selection without prop drilling.
//
// Frame-source panel: lets the user pick a `<base>_poses.json` file, choose
// a start and end frame index, apply either as the editor's anchor, or play
// a ping-pong animation between them. Frame anchors are extracted once per
// index change (not per RAF tick) and reused inside lerpAnchor.

import { useEffect, useMemo, useRef, useState } from 'react'
import type { PoseAnchor } from '../drill/PoseAnchor'
import { cloneAnchor, MIDPOINT_POSE } from '../drill/neutralPose'
import { reconstructFromAnchor } from '../drill/skeletonReconstructor'
import { JOINT_MAP } from '../drill/jointMap'
import {
  extractAnchorFromLandmarks,
  parsePoseFixture,
  type PoseFixtureFrame,
} from '../drill/anchorExtractor'
import { BONES } from '../drill/SkeletonModel'
import { GROUND_ANCHOR_Y } from '../drill/skeletonReconstructor'
import { clampAnchor } from '../drill/shoulderClamp'
import { lerpAnchor } from '../drill/anchorInterpolator'
import { SelectionProvider, useSelection } from '../context/SelectionContext'
import { useParamLimits } from '../hooks/useParamLimits'
import Drill2Mannequin from './Drill2Mannequin'
import AnchorSliders from './AnchorSliders'
import ResetPoseButton from './ResetPoseButton'
import CoordinatesHUD from './CoordinatesHUD'
import ColorLegend from './ColorLegend'
import SavedPosesList from './SavedPosesList'

interface Props {
  onClose: () => void
}

interface VideoEntry { name: string; ext: string }

const DEFAULT_BASE = 'andrii_1'
const DEFAULT_INDICES: Record<string, [number, number]> = {
  andrii_1: [57, 63],
  ivan_1:   [315, 320],
}
// Animation timing — one-way duration and end-of-pong pause, both at 1×
// speed. The user's speed multiplier scales elapsed time, so changing
// speed feels instant without restarting the loop.
const HALF_MS = 700
const PAUSE_MS = 350

const clamp01 = (v: number) => Math.max(0, Math.min(1, v))

export default function MannequinEditor({ onClose }: Props) {
  return (
    <SelectionProvider>
      <EditorShell onClose={onClose} />
    </SelectionProvider>
  )
}

function EditorShell({ onClose }: Props) {
  const { selectedJoint, setSelectedJoint } = useSelection()
  const [anchor, setAnchor] = useState<PoseAnchor>(() => cloneAnchor(MIDPOINT_POSE))
  // Incremented by every Reset path — Drill2Mannequin watches this to also
  // reset the OrbitControls camera, so a Reset visibly returns to front-on.
  const [cameraResetSignal, setCameraResetSignal] = useState(0)

  const { getLimits, setLimits, resetLimits } = useParamLimits()

  // ───── frame-source state ───────────────────────────────────────────
  const [bases, setBases] = useState<VideoEntry[]>([])
  const [selectedBase, setSelectedBase] = useState<string>(DEFAULT_BASE)
  const [frames, setFrames] = useState<PoseFixtureFrame[] | null>(null)
  const [loadStatus, setLoadStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [startIdx, setStartIdx] = useState(0)
  const [endIdx, setEndIdx] = useState(0)
  const [isAnimating, setIsAnimating] = useState(false)
  const [speedMult, setSpeedMult] = useState(1)
  const speedRef = useRef(speedMult)
  speedRef.current = speedMult

  // ───── constraint toggles ─────────────────────────────────────────────
  const [freezeFeet, setFreezeFeet] = useState(false)
  const [applySliderClamps, setApplySliderClamps] = useState(false)
  const [computeBodyRotation, setComputeBodyRotation] = useState(false)
  const [stanceWidth2D, setStanceWidth2D] = useState(false)

  // ───── hardcoded biomechanical presets ─────────────────────────────────
  // Hip height as a fraction of leg length (0.5–1.0, default 0.8).
  // hipMidY alone is cancelled by foot-IK; the real lever is knee bend:
  // deeper bend shortens vertical leg span → hips drop after IK.
  // Solve: cos(180-k) = (ratio*LEG_LEN - thigh) / shin
  const LEG_LEN = BONES.thigh + BONES.shin
  const [hipHeightRatio, setHipHeightRatio] = useState(0.8)
  const hipHeightRatioRef = useRef(hipHeightRatio)
  hipHeightRatioRef.current = hipHeightRatio

  const kneeFromHipRatio = (ratio: number) => {
    const cosArg = Math.max(-1, Math.min(1, (ratio * LEG_LEN - BONES.thigh) / BONES.shin))
    return Math.round(180 - (Math.acos(cosArg) * 180) / Math.PI)
  }

  // Left hand preset: TT neutral receive position (arm forward, elbow lateral).
  const LEFT_HAND_PRESET = {
    leftShoulderAngleDeg: 41,
    leftShoulderAbductionDeg: 5,
    leftElbowYawDeg: 37,
  } as const

  // Stance multiplier: controls ankle lateral separation as N × hipWidth.
  // Also drives staggered fore-aft placement (left forward, right back).
  // The abduction angle is solved so that the total ankle separation
  //   hipWidth + 2·sin(abd)·LEG_LEN  =  stanceMult × hipWidth
  // → abd = asin((stanceMult-1)·hipWidth / (2·LEG_LEN))
  const [stanceMult, setStanceMult] = useState(1.7)
  const stanceMultRef = useRef(stanceMult)
  stanceMultRef.current = stanceMult

  const feetFromMult = (mult: number) => {
    const sinAbd = Math.max(0, (mult - 1) * BONES.hipWidth / (2 * LEG_LEN))
    const abd = Math.round((Math.asin(Math.min(sinAbd, 1)) * 180) / Math.PI)
    // Fore-aft stagger scales with stance width: at 1.7× → ±12°.
    const stagger = Math.round(mult * 7)
    return {
      leftThighAbductionDeg:  abd,
      rightThighAbductionDeg: abd,
      leftThighForwardDeg:    stagger,
      rightThighForwardDeg:   -Math.round(stagger * 0.6),
      leftKneeYawDeg:         Math.round(abd * 1.2),
      rightKneeYawDeg:        -Math.round(abd * 1.2),
    }
  }

  const [lockHipHeight, setLockHipHeight] = useState(false)
  const [lockFeet, setLockFeet] = useState(false)
  const [lockLeftHand, setLockLeftHand] = useState(false)
  const lockHipHeightRef = useRef(lockHipHeight)
  lockHipHeightRef.current = lockHipHeight
  const lockFeetRef = useRef(lockFeet)
  lockFeetRef.current = lockFeet
  const lockLeftHandRef = useRef(lockLeftHand)
  lockLeftHandRef.current = lockLeftHand

  type LockedFeet = Pick<PoseAnchor,
    'leftThighForwardDeg'   | 'rightThighForwardDeg'   |
    'leftThighAbductionDeg' | 'rightThighAbductionDeg' |
    'leftKneeAngleDeg'      | 'rightKneeAngleDeg'      |
    'leftKneeYawDeg'        | 'rightKneeYawDeg'        |
    'leftKneeSwivelDeg'     | 'rightKneeSwivelDeg'     |
    'leftFootYawDeg'        | 'rightFootYawDeg'        |
    'stanceWidthNorm'>
  const lockedFeetRef = useRef<LockedFeet | null>(null)

  const applyPresetsRef = useRef<(next: PoseAnchor) => PoseAnchor>(a => a)

  const applyPresets = (next: PoseAnchor): PoseAnchor => {
    const a = { ...next }
    if (lockFeetRef.current) {
      Object.assign(a, lockedFeetRef.current ?? feetFromMult(stanceMultRef.current))
    }
    // Hip height runs after feet so it can override knee angles even when feet are locked.
    if (lockHipHeightRef.current) {
      const k = kneeFromHipRatio(hipHeightRatioRef.current)
      a.leftKneeAngleDeg  = k
      a.rightKneeAngleDeg = k
    }
    if (lockLeftHandRef.current) {
      a.leftShoulderAngleDeg     = LEFT_HAND_PRESET.leftShoulderAngleDeg
      a.leftShoulderAbductionDeg = LEFT_HAND_PRESET.leftShoulderAbductionDeg
      a.leftElbowYawDeg          = LEFT_HAND_PRESET.leftElbowYawDeg
    }
    return a
  }
  applyPresetsRef.current = applyPresets

  const toggleLockHipHeight = (v: boolean) => {
    lockHipHeightRef.current = v
    setLockHipHeight(v)
    if (v) {
      setAnchor(prev => applyPresetsRef.current(prev))
    }
  }

  const onHipHeightRatioChange = (v: number) => {
    hipHeightRatioRef.current = v
    setHipHeightRatio(v)
    if (lockHipHeightRef.current) {
      setAnchor(prev => applyPresetsRef.current(prev))
    }
  }

  const toggleLockFeet = (v: boolean) => {
    setLockFeet(v)
    if (v) {
      lockedFeetRef.current = {
        leftThighForwardDeg:    MIDPOINT_POSE.leftThighForwardDeg,
        rightThighForwardDeg:   MIDPOINT_POSE.rightThighForwardDeg,
        leftThighAbductionDeg:  MIDPOINT_POSE.leftThighAbductionDeg,
        rightThighAbductionDeg: MIDPOINT_POSE.rightThighAbductionDeg,
        leftKneeAngleDeg:       MIDPOINT_POSE.leftKneeAngleDeg,
        rightKneeAngleDeg:      MIDPOINT_POSE.rightKneeAngleDeg,
        leftKneeYawDeg:         MIDPOINT_POSE.leftKneeYawDeg,
        rightKneeYawDeg:        MIDPOINT_POSE.rightKneeYawDeg,
        leftKneeSwivelDeg:      MIDPOINT_POSE.leftKneeSwivelDeg,
        rightKneeSwivelDeg:     MIDPOINT_POSE.rightKneeSwivelDeg,
        leftFootYawDeg:         MIDPOINT_POSE.leftFootYawDeg,
        rightFootYawDeg:        MIDPOINT_POSE.rightFootYawDeg,
        stanceWidthNorm:        MIDPOINT_POSE.stanceWidthNorm,
      }
      setAnchor(prev => ({ ...prev, ...lockedFeetRef.current! }))
    } else {
      lockedFeetRef.current = null
    }
  }

  const onStanceMultChange = (v: number) => {
    setStanceMult(v)
    if (lockFeetRef.current) setAnchor(prev => ({ ...prev, ...feetFromMult(v) }))
  }

  const toggleLockLeftHand = (v: boolean) => {
    setLockLeftHand(v)
    if (v) setAnchor(prev => ({ ...prev, ...LEFT_HAND_PRESET }))
  }

  const stopAnim = () => setIsAnimating(false)

  const resetAnchor = () => {
    stopAnim()
    setAnchor(cloneAnchor(MIDPOINT_POSE))
    setCameraResetSignal(n => n + 1)
  }

  // One-time: list available video bases.
  useEffect(() => {
    let cancelled = false
    fetch('/api/videos')
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((list: VideoEntry[]) => {
        if (cancelled) return
        setBases(Array.isArray(list) ? list : [])
      })
      .catch(() => { if (!cancelled) setBases([]) })
    return () => { cancelled = true }
  }, [])

  // Whenever the selected base changes, fetch and parse its poses fixture.
  useEffect(() => {
    let cancelled = false
    setIsAnimating(false)
    setLoadStatus('loading')
    setLoadError(null)
    setFrames(null)
    fetch(`/videos/${selectedBase}/${selectedBase}_poses.json`)
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then(raw => {
        if (cancelled) return
        const fixture = parsePoseFixture(raw)
        if (fixture.frames.length === 0) {
          setLoadStatus('error')
          setLoadError('no frames in file')
          return
        }
        setFrames(fixture.frames)
        const [defStart, defEnd] = DEFAULT_INDICES[selectedBase] ?? [0, fixture.frames.length - 1]
        setStartIdx(Math.min(defStart, fixture.frames.length - 1))
        setEndIdx(Math.min(defEnd, fixture.frames.length - 1))
        setLoadStatus('ready')
      })
      .catch(err => {
        if (cancelled) return
        setLoadStatus('error')
        setLoadError(err instanceof Error ? err.message : String(err))
      })
    return () => { cancelled = true }
  }, [selectedBase])

  // Extract once per index change. Skip frames whose landmarks array is
  // incomplete — the FK extractor assumes 33 MediaPipe points and will
  // produce garbage otherwise.
  const startAnchor = useMemo<PoseAnchor | null>(() => {
    if (!frames || startIdx < 0 || startIdx >= frames.length) return null
    const lms = frames[startIdx].landmarks
    if (lms.length < 33) return null
    const raw = extractAnchorFromLandmarks(lms, { computeBodyRotation, stanceWidth2D })
    return applySliderClamps ? clampAnchor(raw) : raw
  }, [frames, startIdx, computeBodyRotation, stanceWidth2D, applySliderClamps])
  const endAnchor = useMemo<PoseAnchor | null>(() => {
    if (!frames || endIdx < 0 || endIdx >= frames.length) return null
    const lms = frames[endIdx].landmarks
    if (lms.length < 33) return null
    const raw = extractAnchorFromLandmarks(lms, { computeBodyRotation, stanceWidth2D })
    return applySliderClamps ? clampAnchor(raw) : raw
  }, [frames, endIdx, computeBodyRotation, stanceWidth2D, applySliderClamps])

  // Raw start landmarks for frozen-feet: pin feet to start-pose ankles.
  const startLmsRaw = useMemo(() => {
    if (!frames || startIdx < 0 || startIdx >= frames.length) return null
    const lms = frames[startIdx].landmarks
    return lms.length >= 33 ? lms : null
  }, [frames, startIdx])

  // When not animating, immediately reflect index changes in the viewport.
  useEffect(() => {
    if (isAnimating || !startAnchor) return
    setAnchor(applyPresetsRef.current(cloneAnchor(startAnchor)))
  }, [startAnchor, isAnimating])

  // RAF-driven ping-pong with pause-at-both-ends. Speed scales elapsed
  // time via speedRef so the slider doesn't restart the effect.
  useEffect(() => {
    if (!isAnimating || !startAnchor || !endAnchor) return
    let raf = 0
    type Phase = 'fwd' | 'pauseEnd' | 'back' | 'pauseStart'
    let phase: Phase = 'fwd'
    let phaseStart = performance.now()
    const step = (now: number) => {
      const elapsed = (now - phaseStart) * speedRef.current
      let t = 0
      let next: Phase = phase
      if (phase === 'fwd') {
        t = clamp01(elapsed / HALF_MS)
        if (elapsed >= HALF_MS) next = 'pauseEnd'
      } else if (phase === 'pauseEnd') {
        t = 1
        if (elapsed >= PAUSE_MS) next = 'back'
      } else if (phase === 'back') {
        t = 1 - clamp01(elapsed / HALF_MS)
        if (elapsed >= HALF_MS) next = 'pauseStart'
      } else {
        t = 0
        if (elapsed >= PAUSE_MS) next = 'fwd'
      }
      setAnchor(applyPresetsRef.current(lerpAnchor(startAnchor, endAnchor, t)))
      if (next !== phase) { phase = next; phaseStart = now }
      raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf)
  }, [isAnimating, startAnchor, endAnchor])

  // Single FK pass per anchor change. Re-runs only on anchor edits — selection
  // doesn't invalidate geometry.
  const landmarks = useMemo(() => {
    const lms = reconstructFromAnchor(anchor)
    if (!freezeFeet || !startLmsRaw) return lms
    // Pin feet to start-pose landmarks (indices 27-32: ankles, heels, foot tips).
    const FOOT_IDX = [27, 28, 29, 30, 31, 32]
    return lms.map((pt, i) => FOOT_IDX.includes(i) ? startLmsRaw[i] : pt)
  }, [anchor, freezeFeet, startLmsRaw])

  // Slider keys that rotate the currently selected joint — used by
  // AnchorSliders to highlight and scroll to the relevant rows.
  const highlightedParams = useMemo(
    () => (selectedJoint ? JOINT_MAP[selectedJoint].controlParams : undefined),
    [selectedJoint],
  )

  const framesCount = frames?.length ?? 0
  const idxMax = Math.max(0, framesCount - 1)
  const clampIdx = (n: number) => Math.max(0, Math.min(idxMax, Math.round(n) || 0))
  const isReady = loadStatus === 'ready' && framesCount > 0

  const applyAnchor = (a: PoseAnchor | null) => {
    if (!a) return
    stopAnim()
    setAnchor(cloneAnchor(a))
    setCameraResetSignal(n => n + 1)
  }

  const onSliderChange = (next: PoseAnchor) => {
    stopAnim()
    setAnchor(applyPresets(next))
  }

  return (
    <div className="flex-1 min-h-0 bg-gray-900 text-gray-100 overflow-auto">
      <div className="p-4 flex flex-col gap-4">
        <div className="flex justify-between items-center">
          <h2 className="text-lg font-semibold">Mannequin Editor</h2>
          <div className="flex gap-2">
            <ResetPoseButton
              anchor={anchor}
              defaultPose={MIDPOINT_POSE}
              onReset={next => {
                stopAnim()
                setAnchor(next)
                setCameraResetSignal(n => n + 1)
              }}
            />
            <button
              className="px-3 py-1.5 rounded bg-gray-700 text-sm hover:bg-gray-600"
              onClick={onClose}
            >
              ✕ Close
            </button>
          </div>
        </div>

        <FrameSourcePanel
          bases={bases}
          selectedBase={selectedBase}
          onSelectBase={setSelectedBase}
          loadStatus={loadStatus}
          loadError={loadError}
          framesCount={framesCount}
          startIdx={startIdx}
          endIdx={endIdx}
          idxMax={idxMax}
          onStartIdx={n => setStartIdx(clampIdx(n))}
          onEndIdx={n => setEndIdx(clampIdx(n))}
          onApplyStart={() => applyAnchor(startAnchor)}
          onApplyEnd={() => applyAnchor(endAnchor)}
          isAnimating={isAnimating}
          canAnimate={isReady && !!startAnchor && !!endAnchor}
          onToggleAnimate={() => setIsAnimating(v => !v)}
          speedMult={speedMult}
          onSpeedChange={setSpeedMult}
          haveStart={!!startAnchor}
          haveEnd={!!endAnchor}
          freezeFeet={freezeFeet}
          onFreezeFeet={setFreezeFeet}
          applySliderClamps={applySliderClamps}
          onApplySliderClamps={setApplySliderClamps}
          computeBodyRotation={computeBodyRotation}
          onComputeBodyRotation={setComputeBodyRotation}
          stanceWidth2D={stanceWidth2D}
          onStanceWidth2D={setStanceWidth2D}
          lockHipHeight={lockHipHeight}
          onLockHipHeight={toggleLockHipHeight}
          hipHeightRatio={hipHeightRatio}
          onHipHeightRatio={onHipHeightRatioChange}
          lockFeet={lockFeet}
          onLockFeet={toggleLockFeet}
          stanceMult={stanceMult}
          onStanceMult={onStanceMultChange}
          lockLeftHand={lockLeftHand}
          onLockLeftHand={toggleLockLeftHand}
        />

        <div className="flex gap-6 justify-center items-start">
          <div className="flex flex-col gap-2 items-center">
            {/* Canvas + overlays. position:relative is required so the
                absolutely-positioned HUD and legend anchor to the canvas. */}
            <div className="relative">
              <Drill2Mannequin
                startLms={landmarks}
                endLms={landmarks}
                phase={0}
                width={620}
                height={780}
                useBodyColors
                selectedJoint={selectedJoint}
                onJointClick={setSelectedJoint}
                onDeselect={() => setSelectedJoint(null)}
                cameraResetSignal={cameraResetSignal}
                skipCoMBalancer
                trustZ
              />
              <ColorLegend
                selectedJoint={selectedJoint}
                onSelectJoint={setSelectedJoint}
              />
              <CoordinatesHUD
                selectedJoint={selectedJoint}
                landmarks={landmarks}
              />
            </div>
            <div className="text-xs text-gray-500">
              Click a joint to select · click empty space to deselect · drag to orbit
            </div>
          </div>

          <AnchorSliders
            activePhase="START"
            onPhaseChange={() => { /* editor is single-anchor; phase selector is inert */ }}
            anchor={anchor}
            onChange={onSliderChange}
            onReset={resetAnchor}
            highlightedParams={highlightedParams}
            hidePhaseSelector
            selectedJointName={selectedJoint ? JOINT_MAP[selectedJoint].displayName : undefined}
            selectedJointId={selectedJoint ?? undefined}
            getLimits={getLimits}
            setLimits={setLimits}
            resetLimits={resetLimits}
          />

          <SavedPosesList
            currentAnchor={anchor}
            onLoad={next => {
              stopAnim()
              setAnchor(cloneAnchor(next))
              setCameraResetSignal(n => n + 1)
            }}
          />
        </div>
      </div>
    </div>
  )
}

interface FrameSourcePanelProps {
  bases: VideoEntry[]
  selectedBase: string
  onSelectBase: (base: string) => void
  loadStatus: 'idle' | 'loading' | 'ready' | 'error'
  loadError: string | null
  framesCount: number
  startIdx: number
  endIdx: number
  idxMax: number
  onStartIdx: (n: number) => void
  onEndIdx: (n: number) => void
  onApplyStart: () => void
  onApplyEnd: () => void
  isAnimating: boolean
  canAnimate: boolean
  onToggleAnimate: () => void
  speedMult: number
  onSpeedChange: (n: number) => void
  haveStart: boolean
  haveEnd: boolean
  freezeFeet: boolean
  onFreezeFeet: (v: boolean) => void
  applySliderClamps: boolean
  onApplySliderClamps: (v: boolean) => void
  computeBodyRotation: boolean
  onComputeBodyRotation: (v: boolean) => void
  stanceWidth2D: boolean
  onStanceWidth2D: (v: boolean) => void
  lockHipHeight: boolean
  onLockHipHeight: (v: boolean) => void
  hipHeightRatio: number
  onHipHeightRatio: (v: number) => void
  lockFeet: boolean
  onLockFeet: (v: boolean) => void
  stanceMult: number
  onStanceMult: (v: number) => void
  lockLeftHand: boolean
  onLockLeftHand: (v: boolean) => void
}

function FrameSourcePanel({
  bases,
  selectedBase,
  onSelectBase,
  loadStatus,
  loadError,
  framesCount,
  startIdx,
  endIdx,
  idxMax,
  onStartIdx,
  onEndIdx,
  onApplyStart,
  onApplyEnd,
  isAnimating,
  canAnimate,
  onToggleAnimate,
  speedMult,
  onSpeedChange,
  haveStart,
  haveEnd,
  freezeFeet,
  onFreezeFeet,
  applySliderClamps,
  onApplySliderClamps,
  computeBodyRotation,
  onComputeBodyRotation,
  stanceWidth2D,
  onStanceWidth2D,
  lockHipHeight,
  onLockHipHeight,
  hipHeightRatio,
  onHipHeightRatio,
  lockFeet,
  onLockFeet,
  stanceMult,
  onStanceMult,
  lockLeftHand,
  onLockLeftHand,
}: FrameSourcePanelProps) {
  const status =
    loadStatus === 'loading' ? 'loading…' :
    loadStatus === 'error' ? `error: ${loadError ?? 'unknown'}` :
    loadStatus === 'ready' ? `frames: ${framesCount} · base: ${selectedBase}` :
    'idle'

  // The base list comes from /api/videos which only checks for a video
  // file in each folder — a base may not have a poses JSON. We still let
  // the user pick (the fetch will surface a 404).
  const baseOptions = bases.length > 0
    ? bases
    : [{ name: selectedBase, ext: '' }]

  return (
    <section className="flex flex-wrap gap-3 items-end p-3 rounded bg-gray-800/60 text-sm">
      <label className="flex flex-col gap-1">
        <span className="text-xs text-gray-400">Pose file</span>
        <select
          className="bg-gray-800 text-gray-200 rounded px-2 py-1 border border-gray-700"
          value={selectedBase}
          onChange={e => onSelectBase(e.target.value)}
        >
          {baseOptions.map(b => (
            <option key={b.name} value={b.name}>{b.name}</option>
          ))}
        </select>
      </label>

      <FrameIndexInput
        label="Start"
        color="text-cyan-400"
        value={startIdx}
        max={idxMax}
        onChange={onStartIdx}
        onApply={onApplyStart}
        applyDisabled={!haveStart}
      />

      <FrameIndexInput
        label="End"
        color="text-fuchsia-400"
        value={endIdx}
        max={idxMax}
        onChange={onEndIdx}
        onApply={onApplyEnd}
        applyDisabled={!haveEnd}
      />

      <button
        className={`px-3 py-1.5 rounded text-sm font-medium ${
          isAnimating
            ? 'bg-amber-600 hover:bg-amber-500'
            : 'bg-emerald-700 hover:bg-emerald-600 disabled:bg-gray-700 disabled:text-gray-500'
        }`}
        onClick={onToggleAnimate}
        disabled={!canAnimate && !isAnimating}
      >
        {isAnimating ? '❚❚ Pause' : '▶ Animate'}
      </button>

      <label className="flex flex-col gap-1">
        <span className="text-xs text-gray-400">Speed: {speedMult.toFixed(2)}×</span>
        <input
          type="range"
          min={0.25}
          max={4}
          step={0.05}
          value={speedMult}
          onChange={e => onSpeedChange(Number(e.target.value))}
          className="w-40"
        />
      </label>

      <div className="text-xs text-gray-400 font-mono ml-auto">{status}</div>

      <div className="w-full border-t border-gray-700/50 pt-2 flex flex-wrap gap-x-4 gap-y-1">
        <span className="text-[11px] uppercase tracking-wider text-gray-500 self-center">Обмеження</span>
        {([
          ['Фіксація стоп', freezeFeet, onFreezeFeet],
          ['Slider clamps', applySliderClamps, onApplySliderClamps],
          ['Ротація корпусу', computeBodyRotation, onComputeBodyRotation],
          ['2D stance width', stanceWidth2D, onStanceWidth2D],
        ] as [string, boolean, (v: boolean) => void][]).map(([label, val, set]) => (
          <label key={label} className="flex items-center gap-1.5 text-xs text-gray-300 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={val}
              onChange={e => set(e.target.checked)}
              className="accent-yellow-400"
            />
            {label}
          </label>
        ))}
        <span className="text-[11px] uppercase tracking-wider text-gray-500 self-center ml-4 pl-4 border-l border-gray-700">Пресети</span>

        <label className="flex items-center gap-1.5 text-xs text-emerald-300 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={lockHipHeight}
            onChange={e => onLockHipHeight(e.target.checked)}
            className="accent-emerald-400"
          />
          Висота тазу
        </label>
        <label className="flex flex-col gap-0.5">
          <span className="text-[10px] text-gray-400">{(hipHeightRatio * 100).toFixed(0)}% ноги</span>
          <input
            type="range"
            min={0.5} max={1.0} step={0.01}
            value={hipHeightRatio}
            onChange={e => onHipHeightRatio(Number(e.target.value))}
            className="w-24"
          />
        </label>

        <label className="flex items-center gap-1.5 text-xs text-emerald-300 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={lockFeet}
            onChange={e => onLockFeet(e.target.checked)}
            className="accent-emerald-400"
          />
          Стопи
        </label>
        <label className="flex flex-col gap-0.5">
          <span className="text-[10px] text-gray-400">{stanceMult.toFixed(2)}× таз</span>
          <input
            type="range"
            min={0.8} max={3.0} step={0.05}
            value={stanceMult}
            onChange={e => onStanceMult(Number(e.target.value))}
            className="w-24"
          />
        </label>

        <label className="flex items-center gap-1.5 text-xs text-emerald-300 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={lockLeftHand}
            onChange={e => onLockLeftHand(e.target.checked)}
            className="accent-emerald-400"
          />
          Ліва рука
        </label>
      </div>
    </section>
  )
}

interface FrameIndexInputProps {
  label: string
  color: string
  value: number
  max: number
  onChange: (n: number) => void
  onApply: () => void
  applyDisabled: boolean
}

function FrameIndexInput({ label, color, value, max, onChange, onApply, applyDisabled }: FrameIndexInputProps) {
  return (
    <div className="flex items-center gap-2">
      <span className={`${color} w-12 text-right text-xs`}>{label}</span>
      <button
        className="px-2 py-1 rounded bg-gray-700 hover:bg-gray-600 font-mono"
        onClick={() => onChange(value - 1)}
      >◀</button>
      <input
        type="number"
        min={0}
        max={max}
        step={1}
        value={value}
        onChange={e => onChange(Number(e.target.value))}
        className="font-mono text-gray-200 w-20 text-center bg-gray-800 border border-gray-700 rounded px-1 py-0.5"
      />
      <button
        className="px-2 py-1 rounded bg-gray-700 hover:bg-gray-600 font-mono"
        onClick={() => onChange(value + 1)}
      >▶</button>
      <button
        className="px-2 py-1 rounded bg-indigo-700 hover:bg-indigo-600 disabled:bg-gray-700 disabled:text-gray-500 text-xs"
        onClick={onApply}
        disabled={applyDisabled}
      >
        Apply
      </button>
    </div>
  )
}
