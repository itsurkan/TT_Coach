// Small header-bar button that returns the editor's anchor to NEUTRAL_POSE.
// If the current anchor differs from the baseline a confirm dialog guards
// against accidental loss of edits.

import { cloneAnchor, NEUTRAL_POSE } from '../drill/neutralPose'
import type { PoseAnchor } from '../drill/PoseAnchor'

interface Props {
  anchor: PoseAnchor
  onReset: (next: PoseAnchor) => void
}

/** Deep-compares every scalar field in PoseAnchor against NEUTRAL_POSE.
 *  dirOverrides is ignored — NEUTRAL_POSE has none, so any overrides on
 *  `anchor` already count as edits via the fallback branch. */
function isAtNeutral(anchor: PoseAnchor): boolean {
  if (anchor.dirOverrides) return false
  const a = anchor as unknown as Record<string, unknown>
  const n = NEUTRAL_POSE as unknown as Record<string, unknown>
  const keys = Object.keys(NEUTRAL_POSE) as (keyof PoseAnchor)[]
  for (const k of keys) {
    if (k === 'dirOverrides') continue
    if (a[k] !== n[k]) return false
  }
  return true
}

export default function ResetPoseButton({ anchor, onReset }: Props) {
  const dirty = !isAtNeutral(anchor)
  const handleClick = () => {
    if (dirty) {
      const ok = window.confirm('Відкинути незбережені зміни і повернутися до базової пози?')
      if (!ok) return
    }
    onReset(cloneAnchor(NEUTRAL_POSE))
  }
  return (
    <button
      onClick={handleClick}
      disabled={!dirty}
      className={
        'px-3 py-1.5 rounded text-sm ' +
        (dirty
          ? 'bg-gray-700 hover:bg-gray-600 text-gray-100'
          : 'bg-gray-800 text-gray-500 cursor-not-allowed')
      }
      title={dirty ? 'Reset to NEUTRAL_POSE' : 'Already at NEUTRAL_POSE'}
    >
      ↺ Reset
    </button>
  )
}
