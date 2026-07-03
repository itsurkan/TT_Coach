import fs from 'node:fs'
import path from 'node:path'
import type { PoseAnchor } from '../../src/drill/PoseAnchor'

export const PASS_THRESHOLD = 7
/** Strict less-than: imageQualityScore < this skips the test (does not fail). */
export const QUALITY_SKIP_THRESHOLD = 5

export const CURRENT_SCHEMA_VERSION = 1 as const
export const CURRENT_RUBRIC_VERSION = 1
export const CURRENT_RENDERER_VERSION = 1

export interface AnchorFixtureFrame {
  generatedAt: string
  judgedBy: string
  imageQualityScore: number
  imageQualityReason: string
  torsoScore: number
  torsoReason: string
  rightArmScore: number
  rightArmReason: string
  extractedAnchor: PoseAnchor
}

export interface AnchorFixture {
  videoBase: string
  schemaVersion: typeof CURRENT_SCHEMA_VERSION
  rubricVersion: number
  rendererVersion: number
  frames: Record<string, AnchorFixtureFrame>
}

export function fixturePath(videoBase: string, videosDir: string): string {
  return path.join(videosDir, videoBase, `${videoBase}_anchor_fixtures.json`)
}

export function loadAnchorFixture(videoBase: string, videosDir: string): AnchorFixture {
  const filePath = fixturePath(videoBase, videosDir)
  if (!fs.existsSync(filePath)) {
    return {
      videoBase,
      schemaVersion: CURRENT_SCHEMA_VERSION,
      rubricVersion: CURRENT_RUBRIC_VERSION,
      rendererVersion: CURRENT_RENDERER_VERSION,
      frames: {},
    }
  }
  const raw = JSON.parse(fs.readFileSync(filePath, 'utf-8')) as unknown
  return validateFixture(raw, videoBase, filePath)
}

function validateFixture(raw: unknown, videoBase: string, filePath: string): AnchorFixture {
  if (!raw || typeof raw !== 'object') {
    throw new Error(`Fixture ${filePath}: not a JSON object`)
  }
  const o = raw as Partial<AnchorFixture>
  if (o.schemaVersion !== CURRENT_SCHEMA_VERSION) {
    throw new Error(
      `Fixture ${filePath}: unknown schemaVersion ${o.schemaVersion} ` +
      `(expected ${CURRENT_SCHEMA_VERSION}). Refresh required.`,
    )
  }
  if (o.videoBase !== videoBase) {
    throw new Error(`Fixture ${filePath}: videoBase mismatch (${o.videoBase} vs ${videoBase})`)
  }
  return {
    videoBase,
    schemaVersion: CURRENT_SCHEMA_VERSION,
    rubricVersion: typeof o.rubricVersion === 'number' ? o.rubricVersion : CURRENT_RUBRIC_VERSION,
    rendererVersion: typeof o.rendererVersion === 'number' ? o.rendererVersion : CURRENT_RENDERER_VERSION,
    frames: (o.frames ?? {}) as Record<string, AnchorFixtureFrame>,
  }
}
