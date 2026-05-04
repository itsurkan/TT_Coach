import { describe, it, expect } from 'vitest'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {
  PASS_THRESHOLD,
  QUALITY_SKIP_THRESHOLD,
  CURRENT_SCHEMA_VERSION,
  CURRENT_RUBRIC_VERSION,
  CURRENT_RENDERER_VERSION,
  loadAnchorFixture,
  type AnchorFixture,
} from '../schema'

describe('schema constants', () => {
  it('PASS_THRESHOLD = 7', () => {
    expect(PASS_THRESHOLD).toBe(7)
  })
  it('QUALITY_SKIP_THRESHOLD = 5', () => {
    expect(QUALITY_SKIP_THRESHOLD).toBe(5)
  })
  it('versions start at 1', () => {
    expect(CURRENT_SCHEMA_VERSION).toBe(1)
    expect(CURRENT_RUBRIC_VERSION).toBe(1)
    expect(CURRENT_RENDERER_VERSION).toBe(1)
  })
})

describe('loadAnchorFixture', () => {
  function withTempVideosDir<T>(fn: (dir: string) => T): T {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'anchor-fix-'))
    try { return fn(dir) } finally { fs.rmSync(dir, { recursive: true, force: true }) }
  }

  it('returns an empty fixture when file is absent', () => {
    withTempVideosDir(videosDir => {
      const fix = loadAnchorFixture('absent_video', videosDir)
      expect(fix.videoBase).toBe('absent_video')
      expect(fix.frames).toEqual({})
      expect(fix.schemaVersion).toBe(CURRENT_SCHEMA_VERSION)
      expect(fix.rubricVersion).toBe(CURRENT_RUBRIC_VERSION)
      expect(fix.rendererVersion).toBe(CURRENT_RENDERER_VERSION)
    })
  })

  it('parses an existing file', () => {
    withTempVideosDir(videosDir => {
      const baseDir = path.join(videosDir, 'demo')
      fs.mkdirSync(baseDir, { recursive: true })
      const fixture: AnchorFixture = {
        videoBase: 'demo',
        schemaVersion: 1,
        rubricVersion: 1,
        rendererVersion: 1,
        frames: {
          '42': {
            generatedAt: '2026-04-30T00:00:00Z',
            judgedBy: 'claude-opus-4-7',
            imageQualityScore: 8, imageQualityReason: 'clear',
            torsoScore: 9, torsoReason: 'tight match',
            rightArmScore: 7, rightArmReason: 'minor elbow drift',
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            extractedAnchor: { stub: true } as any,
          },
        },
      }
      fs.writeFileSync(
        path.join(baseDir, 'demo_anchor_fixtures.json'),
        JSON.stringify(fixture),
      )
      const loaded = loadAnchorFixture('demo', videosDir)
      expect(loaded.frames['42']?.torsoScore).toBe(9)
      expect(loaded.frames['42']?.rightArmReason).toBe('minor elbow drift')
    })
  })

  it('throws on unknown schemaVersion', () => {
    withTempVideosDir(videosDir => {
      const baseDir = path.join(videosDir, 'demo')
      fs.mkdirSync(baseDir, { recursive: true })
      fs.writeFileSync(
        path.join(baseDir, 'demo_anchor_fixtures.json'),
        JSON.stringify({
          videoBase: 'demo', schemaVersion: 99,
          rubricVersion: 1, rendererVersion: 1, frames: {},
        }),
      )
      expect(() => loadAnchorFixture('demo', videosDir)).toThrow(/schemaVersion/)
    })
  })
})
