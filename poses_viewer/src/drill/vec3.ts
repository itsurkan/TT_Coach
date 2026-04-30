export type V3 = { x: number; y: number; z: number }

export const clamp = (v: number, min: number, max: number): number =>
  Math.max(min, Math.min(max, v))

export const toV3 = (l: { x: number; y: number; z: number }): V3 =>
  ({ x: l.x, y: l.y, z: l.z })

export const sub = (a: V3, b: V3): V3 =>
  ({ x: a.x - b.x, y: a.y - b.y, z: a.z - b.z })

export const mid = (a: V3, b: V3): V3 =>
  ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, z: (a.z + b.z) / 2 })

export function length(v: V3): number {
  return Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
}

export function toUnit(v: V3): V3 {
  const l = length(v) || 1e-9
  return { x: v.x / l, y: v.y / l, z: v.z / l }
}

/** Dampen z component by `factor` (default 0.5) — matches Z_DAMP convention. */
export function dampZ(v: V3, factor = 0.5): V3 {
  return { x: v.x, y: v.y, z: v.z * factor }
}

/** Angle between two vectors in degrees (unsigned, 0..180). */
export function angleBetween(a: V3, b: V3): number {
  const dot = a.x * b.x + a.y * b.y + a.z * b.z
  const m = length(a) * length(b)
  if (m < 1e-9) return 0
  const c = Math.max(-1, Math.min(1, dot / m))
  return (Math.acos(c) * 180) / Math.PI
}

/**
 * Signed rotation angle from vector `from` to vector `to` around `axis`.
 * Result in degrees, positive = right-hand-rule around axis. Returns 0 if
 * either vector projects to near-zero length in the plane perpendicular to
 * axis (degenerate — no bend-plane to measure against).
 */
export function signedAngleAround(from: V3, to: V3, axis: V3): number {
  const projectPerp = (v: V3): V3 => {
    const d = v.x * axis.x + v.y * axis.y + v.z * axis.z
    return { x: v.x - d * axis.x, y: v.y - d * axis.y, z: v.z - d * axis.z }
  }
  const a = projectPerp(from)
  const b = projectPerp(to)
  const aLen = length(a)
  const bLen = length(b)
  if (aLen < 1e-4 || bLen < 1e-4) return 0
  const ax = { x: a.x / aLen, y: a.y / aLen, z: a.z / aLen }
  const bx = { x: b.x / bLen, y: b.y / bLen, z: b.z / bLen }
  const dot = Math.max(-1, Math.min(1, ax.x * bx.x + ax.y * bx.y + ax.z * bx.z))
  const cx = ax.y * bx.z - ax.z * bx.y
  const cy = ax.z * bx.x - ax.x * bx.z
  const cz = ax.x * bx.y - ax.y * bx.x
  const sign = Math.sign(cx * axis.x + cy * axis.y + cz * axis.z) || 1
  return sign * Math.acos(dot) * 180 / Math.PI
}

/** Rodrigues rotation of `v` around `axis` by `degAngle` degrees. */
export function rotAroundAxis(v: V3, axis: V3, degAngle: number): V3 {
  const aLen = Math.sqrt(axis.x*axis.x + axis.y*axis.y + axis.z*axis.z) || 1e-9
  const k = { x: axis.x/aLen, y: axis.y/aLen, z: axis.z/aLen }
  const rad = degAngle * Math.PI / 180
  const c = Math.cos(rad), s = Math.sin(rad)
  const d = k.x*v.x + k.y*v.y + k.z*v.z
  const crx = k.y*v.z - k.z*v.y
  const cry = k.z*v.x - k.x*v.z
  const crz = k.x*v.y - k.y*v.x
  return {
    x: v.x*c + crx*s + k.x*d*(1-c),
    y: v.y*c + cry*s + k.y*d*(1-c),
    z: v.z*c + crz*s + k.z*d*(1-c),
  }
}

/** Body-frame horizontal axes derived from whole-body yaw. */
export interface BodyFrame {
  forwardX: number
  forwardZ: number
  acrossX: number
  acrossZ: number
}

export function bodyFrameFromRotDeg(bodyRotationDeg: number): BodyFrame {
  const rad = (bodyRotationDeg * Math.PI) / 180
  const cosB = Math.cos(rad), sinB = Math.sin(rad)
  return { forwardX: -sinB, forwardZ: -cosB, acrossX: cosB, acrossZ: -sinB }
}

/**
 * Compute the torso-down direction from yaw + forward tilt, matching the FK's
 * `torsoDown = -rotAroundAxis([0,-1,0], acrossLevel, tiltDeg)` convention.
 * Used by decomposeArm to invert the FK exactly instead of approximating with
 * world-down at non-zero tilt.
 */
export function torsoDownFromYawTilt(yawDeg: number, tiltDeg: number): V3 {
  if (Math.abs(tiltDeg) < 1e-4) return { x: 0, y: 1, z: 0 }
  const rad = (yawDeg * Math.PI) / 180
  const across: V3 = { x: Math.cos(rad), y: 0, z: -Math.sin(rad) }
  const torsoUp = rotAroundAxis({ x: 0, y: -1, z: 0 }, across, tiltDeg)
  return { x: -torsoUp.x, y: -torsoUp.y, z: -torsoUp.z }
}
