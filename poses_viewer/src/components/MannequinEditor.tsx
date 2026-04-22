// Interactive 3D mannequin editor — Phase 1 target from the spec in
// linear-frolicking-crayon.md.
//
// Wraps Drill2Mannequin in a single-anchor editing context: no START/END
// phases, no fixture playback. The anchor starts at STANDING_POSE (relaxed
// upright pose, distinct from the pre-loaded athletic crouch NEUTRAL_POSE
// used by DrillEditor for drill playback) and the user shapes it via
// sliders or direct joint selection. Built on top of SelectionProvider so
// the canvas, sliders, HUD, and legend can all observe the same selection
// without prop drilling.

import { useMemo, useState } from 'react'
import type { PoseAnchor } from '../drill/PoseAnchor'
import { cloneAnchor, STANDING_POSE } from '../drill/neutralPose'
import { reconstructFromAnchor } from '../drill/skeletonReconstructor'
import { JOINT_MAP } from '../drill/jointMap'
import { SelectionProvider, useSelection } from '../context/SelectionContext'
import Drill2Mannequin from './Drill2Mannequin'
import AnchorSliders from './AnchorSliders'
import ResetPoseButton from './ResetPoseButton'
import CoordinatesHUD from './CoordinatesHUD'
import ColorLegend from './ColorLegend'

interface Props {
  onClose: () => void
}

export default function MannequinEditor({ onClose }: Props) {
  return (
    <SelectionProvider>
      <EditorShell onClose={onClose} />
    </SelectionProvider>
  )
}

function EditorShell({ onClose }: Props) {
  const { selectedJoint, setSelectedJoint } = useSelection()
  const [anchor, setAnchor] = useState<PoseAnchor>(() => cloneAnchor(STANDING_POSE))

  // Single FK pass per anchor change. Re-runs only on anchor edits — selection
  // doesn't invalidate geometry.
  const landmarks = useMemo(() => reconstructFromAnchor(anchor), [anchor])

  // Slider keys that rotate the currently selected joint — used by
  // AnchorSliders to highlight and scroll to the relevant rows.
  const highlightedParams = useMemo(
    () => (selectedJoint ? JOINT_MAP[selectedJoint].controlParams : undefined),
    [selectedJoint],
  )

  return (
    <div className="flex-1 min-h-0 bg-gray-900 text-gray-100 overflow-auto">
      <div className="p-4 flex flex-col gap-4">
        <div className="flex justify-between items-center">
          <h2 className="text-lg font-semibold">Mannequin Editor</h2>
          <div className="flex gap-2">
            <ResetPoseButton anchor={anchor} defaultPose={STANDING_POSE} onReset={setAnchor} />
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
            onChange={setAnchor}
            onReset={() => setAnchor(cloneAnchor(STANDING_POSE))}
            highlightedParams={highlightedParams}
            hidePhaseSelector
          />
        </div>
      </div>
    </div>
  )
}
