import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'
import { analyzeArcsV2, type ArcInfo } from '../trajectoryPipelineV2'
import type { Frame } from '../../types'

const VIDEOS_DIR = 'D:/Desktop/TT_Coach_AI/app/src/main/assets/Videos'

/** Load ball detections from a video's _ball_yolo.json file. */
function loadBallData(videoName: string): { frames: Frame[]; intervalMs: number } {
  const jsonPath = path.join(VIDEOS_DIR, videoName, `${videoName}_ball_yolo.json`)
  const raw = JSON.parse(fs.readFileSync(jsonPath, 'utf-8'))
  const frames: Frame[] = raw.frames.map((f: any) => ({
    frameIndex: f.frameIndex,
    timestampMs: f.timestampMs,
    landmarks: [],
    ball: f.ball?.confidence > 0
      ? { x: f.ball.x, y: f.ball.y, radiusPx: f.ball.radiusPx,
          confidence: f.ball.confidence, status: 'DETECTED' as const }
      : null,
  }))
  return { frames, intervalMs: raw.intervalMs }
}

/** Filter frames to [from, to] inclusive. */
function sliceFrames(frames: Frame[], from: number, to: number): Frame[] {
  return frames.filter(f => f.frameIndex >= from && f.frameIndex <= to)
}

/** Pretty-print arcs for console inspection. */
function printArcs(arcs: ArcInfo[]) {
  for (let i = 0; i < arcs.length; i++) {
    const arc = arcs[i]
    const dets = arc.detections.map(d =>
      `  f${d.frameIndex} (${d.x.toFixed(4)}, ${d.y.toFixed(4)})`
    ).join('\n')
    console.log(
      `arc${i + 1}: frames ${arc.startFrame}-${arc.endFrame}` +
      ` (${arc.detectionCount} dets)` +
      (arc.splitReasonAfter ? `, then ${arc.splitReasonAfter}` : ' (current)'),
    )
    console.log(dets)
  }
}

describe('V2 arc analysis — IMG_6330', () => {
  const { frames } = loadBallData('IMG_6330')

  it('frames 98-105: arc1 98-102 table_contact, arc2 103-105', () => {
    const subset = sliceFrames(frames, 98, 105)
    const arcs = analyzeArcsV2(subset)
    printArcs(arcs)

    expect(arcs[0].startFrame).toBe(98)
    expect(arcs[0].endFrame).toBe(102)
    expect(arcs[0].splitReasonAfter).toBe('table_contact')

    expect(arcs[1].startFrame).toBe(103)
    expect(arcs[1].endFrame).toBe(105)
  })

  it('frames 106-113: arc1 106-110 table_contact, arc2 111-113 racket_contact', () => {
    const subset = sliceFrames(frames, 106, 113)
    const arcs = analyzeArcsV2(subset)
    printArcs(arcs)

    expect(arcs[0].startFrame).toBe(106)
    expect(arcs[0].endFrame).toBe(110)
    expect(arcs[0].splitReasonAfter).toBe('table_contact')

    expect(arcs[1].startFrame).toBe(111)
    expect(arcs[1].endFrame).toBe(113)
    expect(arcs[1].splitReasonAfter).toBe('racket_contact')
  })

  it('frames 114-124: arc1 114-116 table_contact (weak bounce contact zone)', () => {
    const subset = sliceFrames(frames, 114, 124)
    const arcs = analyzeArcsV2(subset)
    printArcs(arcs)

    // Arc 1: ball approaches and bounces on table (weak bounce at f115, contact zone through f116)
    expect(arcs[0].startFrame).toBe(114)
    expect(arcs[0].endFrame).toBe(116)
    expect(arcs[0].splitReasonAfter).toBe('table_contact')

    // Arc 2: single detection after bounce, then 3-frame gap (f118-f119 no detections)
    expect(arcs[1].startFrame).toBe(117)
    expect(arcs[1].endFrame).toBe(117)
    expect(arcs[1].splitReasonAfter).toBe('time_gap')

    // Arc 3: isolated detection f120 (position jump to f121)
    expect(arcs[2].startFrame).toBe(120)
    expect(arcs[2].endFrame).toBe(120)
    expect(arcs[2].splitReasonAfter).toBe('position_jump')

    // Arc 4: ball moving away after racket contact
    expect(arcs[3].startFrame).toBe(121)
    expect(arcs[3].endFrame).toBe(122)
    expect(arcs[3].splitReasonAfter).toBeNull()
  })
})
