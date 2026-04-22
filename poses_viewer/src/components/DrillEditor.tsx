import { useMemo, useState, useEffect, useRef } from 'react'
import type { PoseAnchor, AnchorPhase, LimbDirections } from '../drill/PoseAnchor'
import { NEUTRAL_POSE, cloneAnchor } from '../drill/neutralPose'
import { reconstructFromAnchor } from '../drill/skeletonReconstructor'
import type { BoneLengthsOverride } from '../drill/skeletonReconstructor'
import { interpolateAnchors, lerpAnchor } from '../drill/anchorInterpolator'
import {
  extractAnchorFromLandmarks,
  extractLimbDirections,
  extractBoneLengths,
  parsePoseFixture,
} from '../drill/anchorExtractor'
import DrillSkeletonCanvas from './DrillSkeletonCanvas'
import AnchorSliders from './AnchorSliders'
import Drill2Mannequin from './Drill2Mannequin'

const PLAYBACK_FRAMES = 10
const PLAYBACK_INTERVAL_MS = 120

/** First scalar anchor field whose value differs between `a` and `b`. */
function diffKey(a: PoseAnchor, b: PoseAnchor): keyof PoseAnchor | null {
  const keys = Object.keys(b) as (keyof PoseAnchor)[]
  for (const k of keys) {
    if (k === 'dirOverrides') continue
    if ((a as Record<string, unknown>)[k] !== (b as Record<string, unknown>)[k]) return k
  }
  return null
}

/**
 * Clear only the direction overrides whose implied bones are controlled by
 * the slider that changed. Mapping follows FK's chain: e.g. editing the
 * elbow angle only rotates the forearm; editing thigh forward/abduction
 * rotates thigh AND implicitly shin (which is derived relative to thigh in
 * the angle-path fallback).
 */
function clearRelatedOverrides(
  o: LimbDirections | undefined,
  changed: keyof PoseAnchor | null,
): LimbDirections | undefined {
  if (!o || !changed) return o
  const out: LimbDirections = { ...o }
  switch (changed) {
    case 'torsoTiltDeg':
    case 'shoulderRotationDeg':
    case 'bodyRotationDeg':
      // Body frame pivots → all derived bones.
      return undefined
    case 'rightShoulderAngleDeg':
    case 'rightShoulderAbductionDeg':
      out.rightUpperArm = undefined
      out.rightForearm = undefined
      break
    case 'rightElbowAngleDeg':
      out.rightForearm = undefined
      break
    case 'leftShoulderAngleDeg':
    case 'leftShoulderAbductionDeg':
      out.leftUpperArm = undefined
      out.leftForearm = undefined
      break
    case 'leftElbowAngleDeg':
      out.leftForearm = undefined
      break
    case 'leftThighForwardDeg':
    case 'leftThighAbductionDeg':
      out.leftThigh = undefined
      out.leftShin = undefined
      break
    case 'leftKneeAngleDeg':
      out.leftShin = undefined
      break
    case 'leftFootYawDeg':
      out.leftThigh = undefined
      out.leftShin = undefined
      out.leftFoot = undefined
      break
    case 'rightThighForwardDeg':
    case 'rightThighAbductionDeg':
      out.rightThigh = undefined
      out.rightShin = undefined
      break
    case 'rightKneeAngleDeg':
      out.rightShin = undefined
      break
    case 'rightFootYawDeg':
      out.rightThigh = undefined
      out.rightShin = undefined
      out.rightFoot = undefined
      break
    // hipMidX / hipMidY / stanceWidthNorm / wrist / twist don't map to a
    // direction vector; keep all overrides intact.
    default:
      return o
  }
  return out
}

interface Props {
  onClose: () => void
}

export default function DrillEditor({ onClose }: Props) {
  const [startAnchor, setStartAnchor] = useState<PoseAnchor>(() => cloneAnchor(NEUTRAL_POSE))
  const [endAnchor, setEndAnchor] = useState<PoseAnchor>(() => cloneAnchor(NEUTRAL_POSE))
  const [activePhase, setActivePhase] = useState<AnchorPhase>('START')
  const [isPlaying, setIsPlaying] = useState(false)
  const [playFrame, setPlayFrame] = useState(0)
  const [showGhost, setShowGhost] = useState(true)
  const [humanize, setHumanize] = useState(true)

  // Frame loader state (populated below after `setActiveAnchor` is defined).
  const [frameInput, setFrameInput] = useState('')
  const [loadStatus, setLoadStatus] = useState<{ kind: 'idle' | 'loading' | 'ok' | 'err'; msg?: string }>({ kind: 'idle' })
  const fixtureCacheRef = useRef<ReturnType<typeof parsePoseFixture> | null>(null)
  const FIXTURE_URL = '/videos/andrii_1/andrii_1_poses.json'

  // Per-session bone overrides — populated when the user imports a frame so
  // FK reconstructs the player's actual skeleton proportions rather than the
  // canonical defaults. Shared across START/END since both are the same
  // person doing a single stroke.
  const [bonesOverride, setBonesOverride] = useState<BoneLengthsOverride | null>(null)

  const activeAnchor = activePhase === 'START' ? startAnchor : endAnchor
  const setActiveAnchor = (a: PoseAnchor) => {
    if (activePhase === 'START') setStartAnchor(a)
    else setEndAnchor(a)
  }

  const loadFrameAtIndex = async (frameIdx: number): Promise<number | null> => {
    if (!Number.isFinite(frameIdx) || frameIdx < 0) {
      setLoadStatus({ kind: 'err', msg: 'Enter a non-negative integer' })
      return null
    }
    try {
      if (!fixtureCacheRef.current) {
        setLoadStatus({ kind: 'loading' })
        const resp = await fetch(FIXTURE_URL)
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
        fixtureCacheRef.current = parsePoseFixture(await resp.json())
      }
      const fixture = fixtureCacheRef.current
      const total = fixture.frames.length
      if (frameIdx >= total) {
        setLoadStatus({ kind: 'err', msg: `Out of range (0..${total - 1})` })
        return null
      }
      const lms = fixture.frames[frameIdx].landmarks
      if (!lms || lms.length < 33) {
        setLoadStatus({ kind: 'err', msg: 'Frame has no pose' })
        return null
      }
      const extracted = extractAnchorFromLandmarks(lms)
      // Attach unit-vector overrides from the source landmarks — lets FK
      // render the EXACT imported pose without decomposition loss.
      extracted.dirOverrides = extractLimbDirections(lms)
      // Lock skeleton centering so scrubbing frames doesn't "jump" around —
      // only the pose shape updates, not the hip's 2D position on canvas.
      extracted.hipMidX = NEUTRAL_POSE.hipMidX
      extracted.hipMidY = NEUTRAL_POSE.hipMidY
      setActiveAnchor(extracted)
      setBonesOverride(extractBoneLengths(lms))
      setIsPlaying(false)
      setLoadStatus({ kind: 'ok', msg: `frame ${frameIdx} / ${total - 1} → ${activePhase}` })
      return total
    } catch (e) {
      setLoadStatus({ kind: 'err', msg: `Load failed: ${String(e)}` })
      return null
    }
  }

  // Auto-load whenever the frame number changes (no button/Enter needed).
  useEffect(() => {
    if (frameInput === '') return
    const idx = parseInt(frameInput, 10)
    if (!Number.isFinite(idx) || idx < 0) return
    const t = setTimeout(() => { void loadFrameAtIndex(idx) }, 120)
    return () => clearTimeout(t)
    // Reload when the target phase changes so user can retarget existing frame.
  }, [frameInput, activePhase]) // eslint-disable-line react-hooks/exhaustive-deps

  const stepFrame = (delta: number) => {
    const cur = parseInt(frameInput || '0', 10)
    const next = Math.max(0, cur + delta)
    setFrameInput(String(next))
  }

  // Interpolated frames for playback.
  const playbackFrames = useMemo(
    () => interpolateAnchors(startAnchor, endAnchor, PLAYBACK_FRAMES),
    [startAnchor, endAnchor]
  )

  // Landmarks currently displayed (editing mode shows active anchor; playback
  // mode shows an interpolated frame).
  const displayedLandmarks = useMemo(() => {
    if (isPlaying) {
      // Ping-pong: 0 → PLAYBACK_FRAMES-1 → 0 → ...
      const cycleLen = (PLAYBACK_FRAMES - 1) * 2
      const pos = playFrame % cycleLen
      const idx = pos < PLAYBACK_FRAMES ? pos : cycleLen - pos
      return reconstructFromAnchor(playbackFrames[idx], bonesOverride ?? undefined)
    }
    return reconstructFromAnchor(activeAnchor, bonesOverride ?? undefined)
  }, [isPlaying, playFrame, playbackFrames, activeAnchor, bonesOverride])

  // Ghost shows the OTHER anchor when editing; helps see the contrast.
  const ghostLandmarks = useMemo(() => {
    if (!showGhost || isPlaying) return null
    const other = activePhase === 'START' ? endAnchor : startAnchor
    return reconstructFromAnchor(other, bonesOverride ?? undefined)
  }, [showGhost, isPlaying, activePhase, startAnchor, endAnchor, bonesOverride])

  // Playback timer.
  const rafRef = useRef<number | null>(null)
  useEffect(() => {
    if (!isPlaying) {
      if (rafRef.current) cancelAnimationFrame(rafRef.current)
      return
    }
    let last = performance.now()
    const tick = (now: number) => {
      if (now - last >= PLAYBACK_INTERVAL_MS) {
        setPlayFrame(f => f + 1)
        last = now
      }
      rafRef.current = requestAnimationFrame(tick)
    }
    rafRef.current = requestAnimationFrame(tick)
    return () => { if (rafRef.current) cancelAnimationFrame(rafRef.current) }
  }, [isPlaying])

  const canvasLabel = isPlaying
    ? `Playback  ${(playFrame % (PLAYBACK_FRAMES * 2 - 2)) + 1}/${PLAYBACK_FRAMES}`
    : activePhase

  const reset = () => setActiveAnchor(cloneAnchor(NEUTRAL_POSE))

  const exportJSON = () => {
    const payload = {
      version: 1,
      format: 'drill-anchors-v1',
      anchors: { start: startAnchor, end: endAnchor },
    }
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `drill-${Date.now()}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="fixed inset-0 bg-gray-900 text-gray-100 z-40 overflow-auto">
      <div className="p-4 flex flex-col gap-4">
        <div className="flex justify-between items-center">
          <h2 className="text-lg font-semibold">Drill Editor (anchor-based)</h2>
          <div className="flex gap-2">
            <button
              className="px-3 py-1.5 rounded bg-gray-700 text-sm hover:bg-gray-600"
              onClick={() => {
                setIsPlaying(p => !p)
                setPlayFrame(0)
              }}
            >
              {isPlaying ? '⏸ Pause' : '▶ Play'}
            </button>
            <button
              className="px-3 py-1.5 rounded bg-green-700 text-sm hover:bg-green-600"
              onClick={exportJSON}
            >
              Export JSON
            </button>
            <button
              className="px-3 py-1.5 rounded bg-gray-700 text-sm hover:bg-gray-600"
              onClick={onClose}
            >
              ✕ Close
            </button>
          </div>
        </div>

        <div className="flex gap-6 justify-center items-start">
          <div className="flex flex-col gap-2 items-center">
            <div
              className="flex items-center justify-center"
              style={{ maxHeight: '85vh' }}
            >
              {humanize && displayedLandmarks && displayedLandmarks.length >= 33 ? (
                <Drill2Mannequin
                  startLms={displayedLandmarks}
                  endLms={displayedLandmarks}
                  phase={0}
                  width={560}
                  height={760}
                />
              ) : (
                <DrillSkeletonCanvas
                  landmarks={displayedLandmarks}
                  label={canvasLabel}
                  ghost={ghostLandmarks}
                  humanize={false}
                />
              )}
            </div>
            <div className="flex gap-4 text-sm text-gray-400">
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={showGhost}
                  onChange={e => setShowGhost(e.target.checked)}
                />
                Ghost anchor
              </label>
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={humanize}
                  onChange={e => setHumanize(e.target.checked)}
                />
                Humanize (anthropomorphic 3D mannequin)
              </label>
            </div>
          </div>

          <div className="flex flex-col gap-3 min-w-72">
            <div className="bg-gray-800 rounded p-2 flex flex-col gap-1.5">
              <div className="text-xs text-gray-400">
                Import from andrii_1 poses · applies to <span className="text-gray-200 font-mono">{activePhase}</span>
              </div>
              <div className="flex gap-2 items-center">
                <button
                  className="px-2 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm font-mono"
                  onClick={() => stepFrame(-1)}
                  title="Previous frame"
                >
                  ◀
                </button>
                <input
                  type="number"
                  min={0}
                  placeholder="frame #"
                  value={frameInput}
                  onChange={e => setFrameInput(e.target.value)}
                  className="flex-1 bg-gray-900 border border-gray-700 rounded px-2 py-1 text-sm text-gray-100 text-center font-mono"
                />
                <button
                  className="px-2 py-1 rounded bg-gray-700 hover:bg-gray-600 text-sm font-mono"
                  onClick={() => stepFrame(+1)}
                  title="Next frame"
                >
                  ▶
                </button>
              </div>
              {loadStatus.msg && (
                <div className={
                  'text-xs ' +
                  (loadStatus.kind === 'err' ? 'text-red-400'
                    : loadStatus.kind === 'loading' ? 'text-gray-400'
                    : 'text-green-400')
                }>
                  {loadStatus.msg}
                </div>
              )}
            </div>
            <AnchorSliders
              activePhase={activePhase}
              onPhaseChange={phase => {
                setActivePhase(phase)
                setIsPlaying(false)
              }}
              anchor={activeAnchor}
              onChange={next => {
                // Selectively clear the direction override for the specific
                // bone the user just edited. Other bones keep their imported
                // direction so, e.g., moving a leg slider doesn't shift the
                // torso width (which would happen if we cleared all overrides
                // and FK had to recompute torsoUp from the extracted tilt angle).
                const changed = diffKey(activeAnchor, next)
                const nextOverrides = clearRelatedOverrides(activeAnchor.dirOverrides, changed)
                setActiveAnchor({ ...next, dirOverrides: nextOverrides })
              }}
              onReset={reset}
            />
          </div>
        </div>

        <div className="text-xs text-gray-500 max-w-md">
          Editing <span className="text-gray-300 font-mono">{activePhase}</span>.
          Ghost = the other anchor. Press Play to see the interpolated{' '}
          <span className="font-mono">{PLAYBACK_FRAMES}-frame</span> loop at{' '}
          {Math.round(1000 / PLAYBACK_INTERVAL_MS)} fps.
        </div>
      </div>
    </div>
  )
}

void lerpAnchor
