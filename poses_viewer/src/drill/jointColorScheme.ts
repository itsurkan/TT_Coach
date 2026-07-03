// Body-part colour palette for the interactive mannequin editor.
//
// 14 body segments (head, torso, 6 arm segments, 6 leg segments) each get a
// single hex colour so the user can refer to them unambiguously while
// collaborating with the coach ("move the orange shoulder up"). The palette
// splits warm tones (red → yellow) on the player's right side — the stroking
// arm for a right-handed player — and cool tones (blue → purple) on the left,
// keeping head + torso in neutral greys so the extremities read first.
//
// This scheme is the single source of truth consumed by mesh materials, the
// color-legend panel, and the chip shown next to the selected-joint name in
// the coordinates HUD. When adding a segment, add its entry here first.

export type BodyPartId =
  | 'head' | 'torso'
  | 'rightUpperArm' | 'rightForearm' | 'rightHand'
  | 'leftUpperArm'  | 'leftForearm'  | 'leftHand'
  | 'rightThigh' | 'rightShin' | 'rightFoot'
  | 'leftThigh'  | 'leftShin'  | 'leftFoot'

export interface BodyPartStyle {
  /** Hex colour applied to the mesh's base material. */
  color: string
  /** Ukrainian display name shown in the legend and HUD chip. */
  name: string
  /** Brighter variant used as emissive when the part is selected or flagged. */
  emissiveHighlight: string
}

export const COLOR_SCHEME: Record<BodyPartId, BodyPartStyle> = {
  head:          { color: '#ECE8DF', name: 'голова',            emissiveHighlight: '#FFFDF3' },
  torso:         { color: '#BDC3C7', name: 'тулуб',             emissiveHighlight: '#D8DDDE' },

  // Right side — warm family (stroking arm for a right-handed player).
  rightUpperArm: { color: '#E74C3C', name: 'праве плече',        emissiveHighlight: '#FF7565' },
  rightForearm:  { color: '#E67E22', name: 'праве передпліччя',  emissiveHighlight: '#FFA24C' },
  rightHand:     { color: '#F39C12', name: 'права кисть',        emissiveHighlight: '#FFC047' },
  rightThigh:    { color: '#C0392B', name: 'праве стегно',       emissiveHighlight: '#E15F51' },
  rightShin:     { color: '#D35400', name: 'права гомілка',      emissiveHighlight: '#F17A2A' },
  rightFoot:     { color: '#F1C40F', name: 'права стопа',        emissiveHighlight: '#FFE04D' },

  // Left side — cool family.
  leftUpperArm:  { color: '#3498DB', name: 'ліве плече',         emissiveHighlight: '#66BBF1' },
  leftForearm:   { color: '#2980B9', name: 'ліве передпліччя',   emissiveHighlight: '#54A5DB' },
  leftHand:      { color: '#5DADE2', name: 'ліва кисть',         emissiveHighlight: '#8CCDFA' },
  leftThigh:     { color: '#1ABC9C', name: 'ліве стегно',        emissiveHighlight: '#4FD9BA' },
  leftShin:      { color: '#16A085', name: 'ліва гомілка',       emissiveHighlight: '#45BE9F' },
  leftFoot:      { color: '#9B59B6', name: 'ліва стопа',         emissiveHighlight: '#BB7FD4' },
}

/** Render order for the legend panel: head → feet, right side before left. */
export const BODY_PART_ORDER: readonly BodyPartId[] = [
  'head', 'torso',
  'rightUpperArm', 'rightForearm', 'rightHand',
  'leftUpperArm', 'leftForearm', 'leftHand',
  'rightThigh', 'rightShin', 'rightFoot',
  'leftThigh', 'leftShin', 'leftFoot',
] as const
