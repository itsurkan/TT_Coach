// Coordinates HUD for the mannequin editor.
//
// Shown whenever the user has a joint selected. Pulls the joint's display
// name and landmark source from JOINT_MAP, looks up the xyz position in the
// current FK output, and shows a colour chip matching the joint's body part.
//
// Coordinates are rendered in normalized anchor space (x/y/z in roughly
// [0, 1]), which matches how PoseAnchor positions hips and how MediaPipe
// reports landmarks — so numbers displayed here can be cross-referenced
// with fixture JSON and slider values without unit conversion.

import { useEffect, useState } from 'react'
import type { Landmark } from '../types'
import { JOINT_MAP, type JointId } from '../drill/jointMap'
import { COLOR_SCHEME } from '../drill/jointColorScheme'

interface Props {
  selectedJoint: JointId | null
  landmarks: Landmark[]
}

/** Resolve a landmark source (single index or average-pair) to a position.
 *  Returns null if the required landmarks are missing from the frame. */
function positionFor(
  source: number | readonly [number, number],
  lms: Landmark[],
): { x: number; y: number; z: number } | null {
  if (typeof source === 'number') {
    const lm = lms[source]
    return lm ? { x: lm.x, y: lm.y, z: lm.z } : null
  }
  const [aIdx, bIdx] = source
  const a = lms[aIdx]
  const b = lms[bIdx]
  if (!a || !b) return null
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, z: (a.z + b.z) / 2 }
}

export default function CoordinatesHUD({ selectedJoint, landmarks }: Props) {
  const [copied, setCopied] = useState(false)

  // Reset the "Copied!" indicator whenever the selection changes, so a fresh
  // click on the copy button always produces feedback even if the same label
  // was copied moments earlier.
  useEffect(() => {
    setCopied(false)
  }, [selectedJoint])

  if (!selectedJoint) return null
  const def = JOINT_MAP[selectedJoint]
  const style = COLOR_SCHEME[def.bodyPart]
  const pos = positionFor(def.landmarkIdx, landmarks)

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(selectedJoint)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1200)
    } catch {
      setCopied(false)
    }
  }

  return (
    <div className="absolute top-3 right-3 bg-gray-950/90 border border-gray-700 rounded-md px-3 py-2 shadow-lg text-sm min-w-48">
      <div className="flex items-center gap-2 mb-1.5">
        <span
          className="w-3 h-3 rounded-sm border border-gray-700"
          style={{ backgroundColor: style.color }}
          aria-hidden
        />
        <span className="text-gray-100 font-medium flex-1">{def.displayName}</span>
        <button
          onClick={handleCopy}
          className={
            'text-[10px] px-1.5 py-0.5 rounded border transition-colors ' +
            (copied
              ? 'bg-emerald-600/30 border-emerald-500 text-emerald-200'
              : 'bg-gray-800 border-gray-700 text-gray-300 hover:bg-gray-700')
          }
          title="Скопіювати назву для посилання у чаті"
        >
          {copied ? 'Copied!' : 'Copy'}
        </button>
      </div>
      <div className="text-[10px] uppercase tracking-wider text-gray-500 mb-0.5">
        {style.name}
      </div>
      {pos ? (
        <div className="font-mono text-xs text-gray-300 grid grid-cols-[auto_1fr] gap-x-2 gap-y-0.5">
          <span className="text-gray-500">x</span>
          <span className="text-right">{pos.x.toFixed(3)}</span>
          <span className="text-gray-500">y</span>
          <span className="text-right">{pos.y.toFixed(3)}</span>
          <span className="text-gray-500">z</span>
          <span className="text-right">{pos.z.toFixed(3)}</span>
        </div>
      ) : (
        <div className="text-xs text-red-400">landmark missing</div>
      )}
    </div>
  )
}
