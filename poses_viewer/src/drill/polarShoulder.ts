// poses_viewer/src/drill/polarShoulder.ts
//
// Bidirectional conversion between the rectangular shoulder DOF pair
// (flex = *ShoulderAngleDeg, abd = *ShoulderAbductionDeg) and the polar
// pair (elevation + plane). Derived directly from the plane-projection
// FK in skeletonReconstructor.ts. Side-agnostic: no abSign — world-space
// mirroring is the FK's job.

export interface FlexAbd {
  flex: number // degrees
  abd: number  // degrees
}

export interface Polar {
  elevation: number // degrees, 0..180
  plane: number     // degrees, -180..180
}

export function polarToFlexAbd(p: Polar): FlexAbd {
  throw new Error('not implemented')
}

export function flexAbdToPolar(r: FlexAbd): Polar {
  throw new Error('not implemented')
}
