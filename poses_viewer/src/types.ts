export interface Landmark {
  index: number
  x: number
  y: number
  z: number
  visibility: number
  presence: number
}

export interface BallDetection {
  x: number
  y: number
  radiusPx: number
  confidence: number
  status: 'DETECTED'
}

export interface Frame {
  frameIndex: number
  timestampMs: number
  landmarks: Landmark[]
  ball: BallDetection | null
}

export interface PosesBallData {
  videoUri?: string
  videoName?: string
  intervalMs: number
  totalFrames: number
  videoDurationMs: number
  videoWidth: number
  videoHeight: number
  exportTimestamp: number
  frames: Frame[]
}

export interface Contact {
  frameIndex: number
  timestampMs: number
  confidence: number
  type: string
}

export interface ContactsData {
  videoName?: string
  intervalMs: number
  totalFrames: number
  videoDurationMs: number
  contacts: Contact[]
}

// ── Trajectory pipeline (computed client-side from ball detections) ────────────

export type { TrajectorySegment, FittedPosition, ContactEvent, ContactType, ParabolicFit } from './utils/trajectoryPipeline'

// ── Ball labeling (for training data) ─────────────────────────────────────────

export type LabelStatus = 'correct' | 'wrong' | 'no_ball'

export interface FrameLabel {
  frameIndex: number
  label: LabelStatus
  /** Corrected ball position (normalized 0-1). Set when label is 'wrong' or 'missed'. */
  correctedX?: number
  correctedY?: number
}

export interface CropConfig {
  /** Y offset in pixels from top of frame */
  y: number
  /** Crop height in pixels */
  h: number
  /** X offset in pixels from left of frame (for landscape) */
  x?: number
  /** Crop width in pixels (for landscape) */
  w?: number
}

export interface LabelsData {
  videoName: string
  totalFrames: number
  labels: Record<number, FrameLabel>
  crop?: CropConfig
}

// ── Table keypoint labeling (4 corners + 2 net points) ──────────────────────

/** 6 table keypoints, always labeled clockwise: 1=far-left, 2=far-right, 3=near-right, 4=near-left, 5=net-left, 6=net-right */
export const TABLE_KEYPOINT_COUNT = 6

export interface TableFrameLabel {
  frameIndex: number
  /** points[0]..points[5], null if not yet placed */
  points: Array<{ x: number; y: number } | null>
}

export interface TableLabelsData {
  videoName: string
  totalFrames: number
  labels: Record<number, TableFrameLabel>
}

// ── 3D Trajectory (ball projected to table surface via homography) ──────────

export interface BallPosition3D {
  frameIndex: number
  x_cm: number        // table width axis (0=left edge, 152.5=right edge)
  y_cm: number        // table length axis (0=far end, 274=near end)
  z_cm: number        // height above table (0=on surface, parabolic between bounces)
  screenX: number     // original normalized screen x
  screenY: number     // original normalized screen y
  confidence: number
}

export interface Bounce3D {
  frameIndex: number
  x_cm: number
  y_cm: number
  z_cm: number        // 0 for table surface bounces
}

export interface Trajectory3DResult {
  positions: BallPosition3D[]
  bounces: Bounce3D[]
}
