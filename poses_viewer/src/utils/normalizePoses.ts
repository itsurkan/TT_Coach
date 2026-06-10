import { Landmark, PosesBallData } from '../types'

export function toNumber(value: unknown, fallback = 0): number {
  const num = Number(value)
  return Number.isFinite(num) ? num : fallback
}

export function normalizeLandmarks(input: unknown): Landmark[] {
  if (!Array.isArray(input)) return []

  return input
    .map((item, index): Landmark | null => {
      if (Array.isArray(item)) {
        const [x, y, z, visibility, presence] = item
        return {
          index,
          x: toNumber(x),
          y: toNumber(y),
          z: toNumber(z),
          visibility: toNumber(visibility, 1),
          presence: toNumber(presence, 1),
        }
      }

      if (item && typeof item === 'object') {
        const lm = item as Partial<Landmark> & { score?: number }
        const score = toNumber(lm.score, 1)
        return {
          index: typeof lm.index === 'number' ? lm.index : index,
          x: toNumber(lm.x),
          y: toNumber(lm.y),
          z: toNumber(lm.z),
          visibility: lm.visibility !== undefined ? toNumber(lm.visibility, 1) : score,
          presence: lm.presence !== undefined ? toNumber(lm.presence, 1) : score,
        }
      }

      return null
    })
    .filter((lm): lm is Landmark => lm !== null)
}

export function normalizeData(input: unknown): PosesBallData {
  const root = (input && typeof input === 'object' ? input : {}) as Record<string, unknown>
  const intervalMs = toNumber(root.intervalMs ?? root.interval_ms, 33)
  const rawFrames = Array.isArray(input)
    ? input
    : (Array.isArray(root.frames)
        ? root.frames
        : (Array.isArray(root.poseFrames) ? root.poseFrames : []))

  const frames = rawFrames.map((rawFrame, idx) => {
    const f = (rawFrame && typeof rawFrame === 'object' ? rawFrame : {}) as Record<string, unknown>
    const landmarks = normalizeLandmarks(
      f.landmarks ?? f.poseLandmarks ?? f.pose_landmarks ?? f.keypoints ?? f.pose
    )
    const rawBall = (
      (f.ball && typeof f.ball === 'object')
      ? (f.ball as Record<string, unknown>)
      : ((f.ball_detection && typeof f.ball_detection === 'object')
          ? (f.ball_detection as Record<string, unknown>)
          : null)
    )
    const ball = rawBall
      ? {
          x: toNumber(rawBall.x),
          y: toNumber(rawBall.y),
          radiusPx: toNumber(rawBall.radiusPx ?? rawBall.radius_px),
          confidence: toNumber(rawBall.confidence, 1),
          status: 'DETECTED' as const,
        }
      : null

    return {
      frameIndex: toNumber(f.frameIndex ?? f.frame_index, idx),
      timestampMs: toNumber(f.timestampMs ?? f.timestamp_ms, idx * intervalMs),
      landmarks,
      ball,
    }
  })

  return {
    videoUri: typeof root.videoUri === 'string' ? root.videoUri : undefined,
    videoName: typeof root.videoName === 'string' ? root.videoName : undefined,
    topology: root.topology === 'coco17' ? 'coco17' : 'mediapipe33',
    intervalMs,
    totalFrames: toNumber(root.totalFrames ?? root.total_frames, frames.length),
    videoDurationMs: toNumber(root.videoDurationMs ?? root.video_duration_ms, frames.length * intervalMs),
    videoWidth: toNumber(root.videoWidth ?? root.video_width, 1),
    videoHeight: toNumber(root.videoHeight ?? root.video_height, 1),
    exportTimestamp: toNumber(root.exportTimestamp ?? root.export_timestamp, Date.now()),
    frames,
  }
}
