import { useCallback, useEffect, useState } from 'react'
import type { PoseAnchor } from '../drill/PoseAnchor'

export interface SavedPose {
  id: string
  name: string
  anchor: PoseAnchor
  createdAt: number
}

const STORAGE_KEY = 'poses_viewer.savedPoses.v1'

/**
 * Backfill PoseAnchor fields added after a save was made:
 *
 *   - figureYawDeg (added when the single bodyRotation slider was split into
 *     a whole-figure yaw + a pelvis-vs-leg twist). Old saves had all the yaw
 *     in bodyRotationDeg with both halves moving together, so we move that
 *     value into figureYawDeg and zero bodyRotationDeg to preserve the visual.
 *
 *   - leftKneeYawDeg / rightKneeYawDeg (added when footYaw was split into
 *     knee-plane yaw + foot-vs-shin yaw). Old saves had everything in
 *     footYawDeg, which drove both the bend plane and the foot direction —
 *     migrate by moving that value into kneeYaw and zeroing footYaw.
 */
function migrateAnchor(raw: unknown): PoseAnchor {
  const a = (raw ?? {}) as Partial<PoseAnchor> & Record<string, unknown>
  const out: PoseAnchor = { ...(a as PoseAnchor) }
  if (typeof out.figureYawDeg !== 'number') {
    out.figureYawDeg = typeof out.bodyRotationDeg === 'number' ? out.bodyRotationDeg : 0
    out.bodyRotationDeg = 0
  }
  if (typeof out.leftKneeYawDeg !== 'number') {
    out.leftKneeYawDeg = typeof out.leftFootYawDeg === 'number' ? out.leftFootYawDeg : 0
    out.leftFootYawDeg = 0
  }
  if (typeof out.rightKneeYawDeg !== 'number') {
    out.rightKneeYawDeg = typeof out.rightFootYawDeg === 'number' ? out.rightFootYawDeg : 0
    out.rightFootYawDeg = 0
  }
  return out
}

function loadFromStorage(): SavedPose[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.map((p: SavedPose) => ({ ...p, anchor: migrateAnchor(p.anchor) }))
  } catch {
    return []
  }
}

function saveToStorage(list: SavedPose[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  } catch {
    // Quota exceeded or storage unavailable — swallow; list is still in memory.
  }
}

export function useSavedPoses() {
  const [list, setList] = useState<SavedPose[]>(() => loadFromStorage())

  // Re-sync if another tab mutates the list.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === STORAGE_KEY) setList(loadFromStorage())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const save = useCallback((name: string, anchor: PoseAnchor) => {
    const item: SavedPose = {
      id: crypto.randomUUID(),
      name,
      anchor: { ...anchor },
      createdAt: Date.now(),
    }
    setList(prev => {
      const next = [item, ...prev]
      saveToStorage(next)
      return next
    })
  }, [])

  const remove = useCallback((id: string) => {
    setList(prev => {
      const next = prev.filter(p => p.id !== id)
      saveToStorage(next)
      return next
    })
  }, [])

  return { list, save, remove }
}
