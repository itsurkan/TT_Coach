import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'
import { segmentTrajectory, type TrajectorySegment } from '../trajectoryPipeline'
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

/** Map V1 contact type to test label. */
function contactLabel(seg: TrajectorySegment): string | null {
  if (!seg.contactAfter) return null
  switch (seg.contactAfter.type) {
    case 'BOUNCE': return 'table_contact'
    case 'PADDLE_CONTACT': return 'racket_contact'
    case 'NET_CLIP': return 'net_clip'
    case 'UNKNOWN_CONTACT': return 'unknown'
  }
}

/** Pretty-print segments for console inspection. */
function printSegments(segments: TrajectorySegment[]) {
  for (const seg of segments) {
    const detCount = seg.fittedPositions.filter(p => p.source === 'DETECTED').length
    const after = contactLabel(seg) ?? '(current)'
    console.log(
      `arc${seg.segmentIndex + 1}: frames ${seg.startFrameIndex}-${seg.endFrameIndex}` +
      ` (${detCount} dets), then ${after}`
    )
  }
}

describe('V1 trajectory segmentation — IMG_6330', () => {
  const { frames, intervalMs } = loadBallData('IMG_6330')

  it('frames 98-105: arc1 98-102 table_contact, arc2 103-105', () => {
    const subset = sliceFrames(frames, 98, 105)
    const segments = segmentTrajectory(subset, intervalMs)
    printSegments(segments)

    expect(segments[0].startFrameIndex).toBe(98)
    expect(segments[0].endFrameIndex).toBe(102)
    expect(contactLabel(segments[0])).toBe('table_contact')

    expect(segments[1].startFrameIndex).toBe(103)
    expect(segments[1].endFrameIndex).toBe(105)
  })

  it('frames 106-113: arc1 106-110 table_contact, arc2 111-113 racket_contact', () => {
    const subset = sliceFrames(frames, 106, 113)
    const segments = segmentTrajectory(subset, intervalMs)
    printSegments(segments)

    expect(segments[0].startFrameIndex).toBe(106)
    expect(segments[0].endFrameIndex).toBe(110)
    expect(contactLabel(segments[0])).toBe('table_contact')

    expect(segments[1].startFrameIndex).toBe(111)
    expect(segments[1].endFrameIndex).toBe(113)
    expect(contactLabel(segments[1])).toBe('racket_contact')
  })

  it('frames 114-124: arc1 114-116 table_contact, arc2 117-119 racket_contact, arc3 121-124 table_contact', () => {
    const subset = sliceFrames(frames, 114, 124)
    const segments = segmentTrajectory(subset, intervalMs)
    printSegments(segments)

    expect(segments[0].startFrameIndex).toBe(114)
    expect(segments[0].endFrameIndex).toBe(116)
    expect(contactLabel(segments[0])).toBe('table_contact')

    expect(segments[1].startFrameIndex).toBe(117)
    expect(segments[1].endFrameIndex).toBe(119)
    expect(contactLabel(segments[1])).toBe('racket_contact')

    expect(segments[2].startFrameIndex).toBe(121)
    expect(segments[2].endFrameIndex).toBe(124)
    expect(contactLabel(segments[2])).toBe('table_contact')
  })
})
