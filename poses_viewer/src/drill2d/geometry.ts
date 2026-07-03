/**
 * Mirrors ViewGeometry: xScale = aspectRatio / cos(cameraYawDeg) — THE single
 * factor applied to x-deltas before any geometry (schema v2 normalizes x by
 * width, y by height). Sign of yaw is irrelevant (cos is even).
 */
export const MAX_YAW_DEG = 60

export function xScaleFor(aspectRatio: number, cameraYawDeg = 0): number {
  if (Math.abs(cameraYawDeg) > MAX_YAW_DEG) {
    throw new Error(`cameraYawDeg must be within ±${MAX_YAW_DEG}°, got ${cameraYawDeg}`)
  }
  return aspectRatio / Math.cos((cameraYawDeg * Math.PI) / 180)
}
