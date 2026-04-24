// poses_viewer/src/drill/polarShoulder.ts
//
// Bidirectional conversion between the rectangular shoulder DOF pair
// (flex = *ShoulderAngleDeg, abd = *ShoulderAbductionDeg) and the polar
// pair (elevation + plane). Derived directly from the plane-projection
// FK in skeletonReconstructor.ts. Side-agnostic: no abSign — world-space
// mirroring is the FK's job.

const DEG = Math.PI / 180
const RAD = 180 / Math.PI

export interface FlexAbd {
  flex: number // degrees
  abd: number  // degrees
}

export interface Polar {
  elevation: number // degrees, 0..180
  plane: number     // degrees, -180..180
}

export function polarToFlexAbd(p: Polar): FlexAbd {
  const e = p.elevation * DEG
  const pl = p.plane * DEG
  const sinE = Math.sin(e)
  const cosE = Math.cos(e)
  const fwd = Math.cos(pl) * sinE
  // At the "lateral pole" (elevation=90, plane=±90°) both atan2 args collapse
  // to floating-point noise; guard so flex reports 0 instead of ~45°.
  const EPS = 1e-12
  const flexRad =
    Math.abs(fwd) < EPS && Math.abs(cosE) < EPS ? 0 : Math.atan2(fwd, cosE)
  const abdRad = Math.asin(clamp(Math.sin(pl) * sinE, -1, 1))
  return { flex: flexRad * RAD, abd: abdRad * RAD }
}

export function flexAbdToPolar(r: FlexAbd): Polar {
  throw new Error('not implemented')
}

function clamp(v: number, lo: number, hi: number): number {
  return v < lo ? lo : v > hi ? hi : v
}
