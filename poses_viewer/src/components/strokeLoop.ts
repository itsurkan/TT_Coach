/**
 * Loop playback helper for the stroke timeline. When a stroke is selected and loop
 * is on, playback that reaches (or passes) the stroke's endMs jumps back to startMs.
 * Pure so it can be unit-tested without a <video> element.
 */

/**
 * Returns the ms to seek back to when the playhead has run past the segment end,
 * or null when playback is still inside the segment (let it continue).
 * Guards against an inverted/empty segment (endMs <= startMs) by never looping.
 */
export function loopBackTarget(currentMs: number, startMs: number, endMs: number): number | null {
  if (endMs <= startMs) return null
  return currentMs >= endMs ? startMs : null
}
