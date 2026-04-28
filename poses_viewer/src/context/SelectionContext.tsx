// Selection state for the interactive mannequin editor.
//
// A single React context holds the currently selected JointId (or null) so
// the 3D canvas, the slider panel, the coordinates HUD, and the colour legend
// can all react to the same selection without threading prop callbacks
// through three layers of components.

import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import type { JointId } from '../drill/jointMap'

interface SelectionValue {
  selectedJoint: JointId | null
  setSelectedJoint: (id: JointId | null) => void
}

const SelectionContext = createContext<SelectionValue | null>(null)

export function SelectionProvider({ children }: { children: ReactNode }) {
  const [selectedJoint, setSelectedJoint] = useState<JointId | null>(null)
  const value = useMemo(() => ({ selectedJoint, setSelectedJoint }), [selectedJoint])
  return <SelectionContext.Provider value={value}>{children}</SelectionContext.Provider>
}

export function useSelection(): SelectionValue {
  const ctx = useContext(SelectionContext)
  if (!ctx) throw new Error('useSelection must be used inside <SelectionProvider>')
  return ctx
}
