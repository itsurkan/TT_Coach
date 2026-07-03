// Small header-bar button that returns the editor's anchor to a caller-
// supplied default pose. The button stays clickable even when the anchor is
// already at the default — it's a no-op then, but showing it active makes
// the control predictable (the user clicks it regardless of dirty state).
// A confirm dialog only appears when the anchor differs from the default.

import { cloneAnchor } from '../drill/neutralPose'
import type { PoseAnchor } from '../drill/PoseAnchor'

interface Props {
  anchor: PoseAnchor
  defaultPose: PoseAnchor
  onReset: (next: PoseAnchor) => void
}

/** Deep-compares every scalar field in PoseAnchor against the default. */
function isAtDefault(anchor: PoseAnchor, defaultPose: PoseAnchor): boolean {
  const a = anchor as unknown as Record<string, unknown>
  const n = defaultPose as unknown as Record<string, unknown>
  const keys = Object.keys(defaultPose) as (keyof PoseAnchor)[]
  for (const k of keys) {
    if (a[k] !== n[k]) return false
  }
  return true
}

export default function ResetPoseButton({ anchor, defaultPose, onReset }: Props) {
  const dirty = !isAtDefault(anchor, defaultPose)
  const handleClick = () => {
    if (!dirty) return
    const ok = window.confirm('Відкинути незбережені зміни і повернутися до базової пози?')
    if (!ok) return
    onReset(cloneAnchor(defaultPose))
  }
  return (
    <button
      onClick={handleClick}
      className={
        'px-3 py-1.5 rounded text-sm ' +
        (dirty
          ? 'bg-gray-700 hover:bg-gray-600 text-gray-100'
          : 'bg-gray-800 text-gray-500 hover:bg-gray-700')
      }
      title={dirty ? 'Reset to default pose' : 'Already at default pose'}
    >
      ↺ Reset
    </button>
  )
}
