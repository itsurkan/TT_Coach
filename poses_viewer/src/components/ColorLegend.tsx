// Colour legend for the mannequin editor.
//
// A toggle button in the canvas corner expands into a panel listing all 14
// body-part chips with their Ukrainian names. Clicking a chip selects the
// joint that best represents that body part (shoulder for upper arm, elbow
// for forearm, etc.), giving the user a non-geometric way to navigate —
// useful when the coach tells them "move the orange segment" without
// pointing at the 3D view.

import { useState } from 'react'
import { COLOR_SCHEME, BODY_PART_ORDER, type BodyPartId } from '../drill/jointColorScheme'
import type { JointId } from '../drill/jointMap'

// Maps a body part to the joint that primarily rotates with it. Picked so
// each legend row selects a joint whose sliders are the most useful starting
// point when the user wants to edit that segment.
const PRIMARY_JOINT: Record<BodyPartId, JointId> = {
  head: 'head',
  torso: 'hipMid',
  rightUpperArm: 'rightShoulder',
  rightForearm: 'rightElbow',
  rightHand: 'rightWrist',
  leftUpperArm: 'leftShoulder',
  leftForearm: 'leftElbow',
  leftHand: 'leftWrist',
  rightThigh: 'rightHip',
  rightShin: 'rightKnee',
  rightFoot: 'rightAnkle',
  leftThigh: 'leftHip',
  leftShin: 'leftKnee',
  leftFoot: 'leftAnkle',
}

interface Props {
  onSelectJoint: (id: JointId) => void
  selectedJoint: JointId | null
}

export default function ColorLegend({ onSelectJoint, selectedJoint }: Props) {
  const [open, setOpen] = useState(false)

  return (
    <div className="absolute top-3 left-3 flex flex-col items-start gap-2 pointer-events-auto">
      <button
        onClick={() => setOpen(v => !v)}
        className="bg-gray-950/90 border border-gray-700 hover:bg-gray-900 text-gray-200 text-xs px-2 py-1 rounded-md shadow"
        title={open ? 'Hide legend' : 'Show colour legend'}
      >
        {open ? '× Hide legend' : '≡ Colours'}
      </button>

      {open && (
        <div className="bg-gray-950/95 border border-gray-700 rounded-md p-2 shadow-lg">
          <ul className="flex flex-col gap-1 text-xs">
            {BODY_PART_ORDER.map(bp => {
              const style = COLOR_SCHEME[bp]
              const primary = PRIMARY_JOINT[bp]
              const isSelected = selectedJoint === primary
              return (
                <li key={bp}>
                  <button
                    onClick={() => onSelectJoint(primary)}
                    className={
                      'flex items-center gap-2 w-full px-1.5 py-0.5 rounded hover:bg-gray-800 ' +
                      (isSelected ? 'bg-gray-800' : '')
                    }
                  >
                    <span
                      className="w-3 h-3 rounded-sm border border-gray-700 shrink-0"
                      style={{ backgroundColor: style.color }}
                      aria-hidden
                    />
                    <span className="text-gray-200">{style.name}</span>
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </div>
  )
}
