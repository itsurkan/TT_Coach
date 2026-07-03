import type { Landmark } from '../types'

export type V3 = [number, number, number]

export const deg = (d: number) => (d * Math.PI) / 180

export function add(a: V3, b: V3): V3 {
  return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]
}

export function scale(v: V3, s: number): V3 {
  return [v[0] * s, v[1] * s, v[2] * s]
}

export function normalize(v: V3): V3 {
  const m = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
  if (m < 1e-9) return [0, 0, 0]
  return [v[0] / m, v[1] / m, v[2] / m]
}

/** Rotate v around the y-axis (yaw). +deg rotates toward +z. */
export function rotY(v: V3, d: number): V3 {
  const c = Math.cos(deg(d))
  const s = Math.sin(deg(d))
  return [c * v[0] + s * v[2], v[1], -s * v[0] + c * v[2]]
}

/** Rotate vector v around a (unit-normalizable) axis by d degrees — Rodrigues. */
export function rotAroundAxis(v: V3, axis: V3, d: number): V3 {
  const k = normalize(axis)
  const c = Math.cos(deg(d))
  const s = Math.sin(deg(d))
  const dot = k[0] * v[0] + k[1] * v[1] + k[2] * v[2]
  const cross: V3 = [
    k[1] * v[2] - k[2] * v[1],
    k[2] * v[0] - k[0] * v[2],
    k[0] * v[1] - k[1] * v[0],
  ]
  return [
    v[0] * c + cross[0] * s + k[0] * dot * (1 - c),
    v[1] * c + cross[1] * s + k[1] * dot * (1 - c),
    v[2] * c + cross[2] * s + k[2] * dot * (1 - c),
  ]
}

export function mkLm(i: number, p: V3, vis = 1): Landmark {
  return { index: i, x: p[0], y: p[1], z: p[2], visibility: vis, presence: vis }
}
