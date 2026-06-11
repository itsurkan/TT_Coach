import { Keypoint2D, PoseFrame2D } from './types'

/**
 * Parses pose JSON schema v2 (docs/pose_json_schema_v2.md) — the *_poses_rtm.json
 * RTMPose exports. Mirrors PoseJsonV2Parser's strictness: schemaVersion must be 2
 * (legacy MediaPipe-33 v1 files have no schemaVersion and are a different format).
 */
export interface PoseSequence2D {
  topology: 'coco17' | 'halpe26'
  intervalMs: number
  videoWidth: number
  videoHeight: number
  videoDurationMs: number
  aspectRatio: number
  frames: PoseFrame2D[]
}

export function parsePoseV2(raw: unknown): PoseSequence2D {
  const root = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
  if (root.schemaVersion !== 2) {
    throw new Error(`expected schemaVersion 2, got ${JSON.stringify(root.schemaVersion)} — legacy v1 files are not supported here`)
  }
  const topology = root.topology
  if (topology !== 'coco17' && topology !== 'halpe26') {
    throw new Error(`unknown topology: ${JSON.stringify(topology)}`)
  }
  const kpCount = topology === 'halpe26' ? 26 : 17
  const intervalMs = requireNumber(root, 'intervalMs')
  const videoWidth = requireNumber(root, 'videoWidth')
  const videoHeight = requireNumber(root, 'videoHeight')
  const videoDurationMs = requireNumber(root, 'videoDurationMs')
  const rawFrames = Array.isArray(root.frames) ? root.frames : []

  const frames: PoseFrame2D[] = rawFrames.map((rf, idx) => {
    const f = (rf && typeof rf === 'object' ? rf : {}) as Record<string, unknown>
    const rawLms = Array.isArray(f.landmarks) ? f.landmarks : []
    let keypoints: Keypoint2D[] = []
    if (rawLms.length > 0) {
      keypoints = Array.from({ length: kpCount }, () => ({ x: 0, y: 0, score: 0 }))
      rawLms.forEach((lm, i) => {
        const o = (lm && typeof lm === 'object' ? lm : {}) as Record<string, unknown>
        const index = typeof o.index === 'number' ? o.index : i
        if (index >= 0 && index < kpCount) {
          keypoints[index] = { x: Number(o.x), y: Number(o.y), score: Number(o.score) }
        }
      })
    }
    return {
      frameIndex: typeof f.frameIndex === 'number' ? f.frameIndex : idx,
      timestampMs: typeof f.timestampMs === 'number' ? f.timestampMs : idx * intervalMs,
      keypoints,
    }
  })

  return {
    topology,
    intervalMs,
    videoWidth,
    videoHeight,
    videoDurationMs,
    aspectRatio: videoWidth / videoHeight,
    frames,
  }
}

function requireNumber(root: Record<string, unknown>, field: string): number {
  const v = root[field]
  if (typeof v !== 'number' || !Number.isFinite(v)) throw new Error(`missing/invalid ${field}`)
  return v
}
