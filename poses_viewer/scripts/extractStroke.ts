import { readFileSync, writeFileSync } from 'node:fs'
import { parsePoseV2 } from '../src/drill2d/parsePoseV2'
import { detectStrokes } from '../src/drill2d/strokeDetector2d'
import { filterForwardStrokes } from '../src/drill2d/forwardStrokeFilter'
import { pairCycles } from '../src/drill2d/cyclePairing'
import { filterCycleReps } from '../src/drill2d/repFilter'
import { filterStationaryCycles, DEFAULT_MAX_TRAVEL_TORSO } from '../src/drill2d/locomotionFilter'
import { xScaleFor } from '../src/drill2d/geometry'
import { DEFAULT_MIN_SCORE } from '../src/drill2d/facing'
import type { Handedness } from '../src/drill2d/types'

const SRC = '/Users/itsurkan/Dev/personal/TT_Coach/Videos/video_3/video_3_poses_rtm.json'
const handedness: Handedness = 'right'
const repNumber = Number(process.argv[2] ?? '1') // 1-based; first good stroke by default

const rawJson = JSON.parse(readFileSync(SRC, 'utf8'))
const seq = parsePoseV2(rawJson)

const detectXScale = xScaleFor(seq.aspectRatio, 0)
const raw = detectStrokes(seq.frames, handedness, detectXScale, seq.intervalMs)
const forward = filterForwardStrokes(raw, seq.frames, handedness, DEFAULT_MIN_SCORE)
const cycles = pairCycles(raw, forward, seq.frames, seq.intervalMs)
const banded = filterCycleReps(cycles)
const kept = filterStationaryCycles(banded, seq.frames, detectXScale, DEFAULT_MAX_TRAVEL_TORSO, DEFAULT_MIN_SCORE)

console.log(`raw=${raw.length} forward=${forward.length} cycles=${cycles.length} banded=${banded.length} keptReps=${kept.length}`)
kept.slice(0, 5).forEach((c, i) =>
  console.log(`  rep#${i + 1} frames [${c.startFrame}..${c.endFrame}] peak=${c.peakFrame} (drive ${c.drive.startFrame}..${c.drive.endFrame})`),
)

const cycle = kept[repNumber - 1]
if (!cycle) throw new Error(`rep ${repNumber} not found (only ${kept.length} good reps)`)

// Slice ORIGINAL JSON frames by frameIndex, preserving original frameIndex/timestampMs.
const start = cycle.startFrame
const end = cycle.endFrame
const slicedFrames = rawJson.frames.filter(
  (f: { frameIndex: number }) => f.frameIndex >= start && f.frameIndex <= end,
)

const out = {
  ...rawJson,
  totalFrames: slicedFrames.length,
  frames: slicedFrames,
}

const OUT = `/Users/itsurkan/Dev/personal/TT_Coach/Videos/video_3/video_3_stroke${repNumber}_poses_rtm.json`
writeFileSync(OUT, JSON.stringify(out))
console.log(`\nWrote rep#${repNumber}: frames [${start}..${end}] (${slicedFrames.length} frames) -> ${OUT}`)
