import type { Landmark } from '../types'

export interface PoseFixtureFrame {
  frameIndex: number
  timestampMs: number
  landmarks: Landmark[]
}

export interface PoseFixture {
  intervalMs: number
  totalFrames: number
  frames: PoseFixtureFrame[]
}

/**
 * Normalize a fixture-file JSON object into the shape we consume.
 * Tolerates `landmarks` being either an array of {x,y,z,visibility,presence}
 * objects or an array of [x,y,z,v,p] tuples — same rules as App.tsx.
 */
export function parsePoseFixture(raw: unknown): PoseFixture {
  const r = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
  const intervalMs = Number(r.intervalMs ?? r.interval_ms ?? 100)
  const totalFrames = Number(r.totalFrames ?? r.total_frames ?? 0)
  const rawFrames = Array.isArray(r.frames) ? r.frames : []
  const frames: PoseFixtureFrame[] = rawFrames.map((rf, idx) => {
    const f = (rf && typeof rf === 'object' ? rf : {}) as Record<string, unknown>
    const rawLms = (f.landmarks ?? f.poseLandmarks ?? []) as unknown
    const landmarks = Array.isArray(rawLms) ? rawLms.map((item, i) => parseLm(item, i)) : []
    return {
      frameIndex: Number(f.frameIndex ?? f.frame_index ?? idx),
      timestampMs: Number(f.timestampMs ?? f.timestamp_ms ?? idx * intervalMs),
      landmarks,
    }
  })
  return { intervalMs, totalFrames: totalFrames || frames.length, frames }
}

function parseLm(item: unknown, index: number): Landmark {
  if (Array.isArray(item)) {
    const [x, y, z, v, p] = item
    return {
      index,
      x: Number(x), y: Number(y), z: Number(z),
      visibility: Number(v ?? 1), presence: Number(p ?? 1),
    }
  }
  const o = (item && typeof item === 'object' ? item : {}) as Record<string, unknown>
  return {
    index: Number(o.index ?? index),
    x: Number(o.x ?? 0), y: Number(o.y ?? 0), z: Number(o.z ?? 0),
    visibility: Number(o.visibility ?? 1),
    presence: Number(o.presence ?? 1),
  }
}
