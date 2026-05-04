# Visual-judge anchor regression test — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pin the `landmarks → extractAnchorFromLandmarks → reconstructFromAnchor → rendered figure` pipeline against original video frames using Claude Code as a visual judge running **in-session** (no API key), with a cached fixture file driving a hermetic vitest assertion.

**Architecture:** Two deterministic CLIs do all heavy work — `prepare-anchor-composites` (ffmpeg + Playwright + sharp → composite PNG per frame) and `write-anchor-fixture-entry` (atomic upsert into the cache file). Claude Code judges each composite in-session via Read tool against `RUBRIC.md`, then invokes the write CLI. The vitest test reads the cache and asserts `torsoScore >= 7 && rightArmScore >= 7`, skipping frames with `imageQualityScore < 5`. A new `#/render` route in poses_viewer mounts `Drill2Mannequin` standalone for Playwright to screenshot. The `MannequinEditor` gets a button to load a cached frame's `extractedAnchor` for visual re-inspection.

**Tech Stack:** TypeScript + tsx (CLI runner), Playwright (headless Chromium), sharp (image compositing), ffmpeg (frame extraction), vitest 4 (test runner), React 18 + @react-three/fiber 0.184 (existing renderer).

**Spec:** [docs/superpowers/specs/2026-04-30-claude-oracle-anchor-test-design.md](../specs/2026-04-30-claude-oracle-anchor-test-design.md)

---

## File structure

**New files (under `poses_viewer/`):**

```
scripts/anchorFixtures/
  schema.ts                  # types, thresholds, loadAnchorFixture(), version constants
  RUBRIC.md                  # in-session scoring instructions for Claude
  extractFrames.ts           # ffmpeg wrapper (frame N → JPG buffer)
  renderMannequin.ts         # Playwright wrapper: navigate to #/render, screenshot
  composite.ts               # 2-point similarity + sharp affine + alpha-blend
  prepareComposites.ts       # CLI 1 entry point
  writeFixtureEntry.ts       # CLI 2 entry point
  args.ts                    # tiny CLI arg parser shared by both CLIs (no dep)
src/components/
  MannequinRenderRoute.tsx   # NEW: standalone render route for Playwright
src/drill/__tests__/
  anchorExtractor.cached.test.ts  # the assertion test
```

**Modified files:**

```
poses_viewer/package.json                 # +tsx, +playwright, +sharp; +2 scripts
poses_viewer/src/App.tsx                  # +import + route guard for #/render
poses_viewer/src/hooks/useHashRoute.ts    # add 'render' to Route union
poses_viewer/src/components/MannequinEditor.tsx  # +cached-frame button
.gitignore                                # exception for fixture file (Videos/ is gitignored at root)
```

**New gitignored directory** (created at runtime by CLI 1):

```
Videos/<base>/_oracle_inputs/
  frame_<N>.png         # composite (video frame + mannequin overlay)
  frame_<N>.meta.json   # extractedAnchor + alignment points (consumed by CLI 2)
```

**Cache file (committed to git):**

```
Videos/<base>/<base>_anchor_fixtures.json
```

**Responsibility split:**

- `schema.ts` is the single source of truth for types + version constants. Both CLIs and the test import from it.
- `extractFrames.ts`, `renderMannequin.ts`, `composite.ts` are independently testable units (each one function in / one buffer out).
- `prepareComposites.ts` is pure plumbing — orchestrates the three units, writes outputs.
- `writeFixtureEntry.ts` is pure JSON manipulation — does not touch ffmpeg/Playwright/sharp.
- `MannequinRenderRoute.tsx` mounts `Drill2Mannequin` with a transparent canvas and a fixed orthographic camera. It does not import any prep-script code — the only contract is the URL query string.

---

## Pre-flight notes for the engineer

1. **Working dir for all commands** in this plan is `poses_viewer/` unless noted otherwise.
2. **`Videos/` is already gitignored at the repo root** ([.gitignore line ~17](../../.gitignore)). Without an exception, the fixture file won't be committed. Task 1 adds `!Videos/*/*_anchor_fixtures.json` to the root `.gitignore` so only the cache file commits, not the videos or the `_oracle_inputs/` working dir.
3. **MediaPipe coords:** landmarks normalized `[0,1]`, `x=right, y=down, z=away`. The three.js mannequin coordinate system already matches this (per `poses_viewer/CLAUDE.md`).
4. **Frame numbering:** poses JSON uses `frameIndex` (0-based) — same indexing ffmpeg uses with `select=eq(n\,N)`. Confirmed against existing fixtures.
5. **No SDK / API key.** All "judging" steps are documented as **manual workflow** — the engineer (or Claude Code in the same session) reads composites and runs the write CLI. The plan does not call `@anthropic-ai/sdk`.
6. **Dev server port** is hardcoded to 5780 (vite.config.ts). Prep CLI assumes that port.
7. **TypeScript strict mode** is on (existing `tsconfig.json`). No `any` in new code — define types properly.

---

## Task 1: Cache schema + thresholds + loader (no rendering yet)

**Files:**
- Create: `poses_viewer/scripts/anchorFixtures/schema.ts`
- Create: `poses_viewer/scripts/anchorFixtures/__tests__/schema.test.ts`
- Modify: `.gitignore` (root of repo)

**Why first:** every other file imports from `schema.ts`. Versioning + threshold constants are settled before any I/O code runs. The .gitignore exception is set early so subsequent commits don't accidentally drop the fixture.

- [ ] **Step 1: Write the failing test**

Create `poses_viewer/scripts/anchorFixtures/__tests__/schema.test.ts`:

```ts
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd poses_viewer
npx vitest run scripts/anchorFixtures/__tests__/schema.test.ts
```

Expected: FAIL with module-not-found error for `../schema`.

- [ ] **Step 3: Write minimal implementation**

Create `poses_viewer/scripts/anchorFixtures/schema.ts`:

```ts
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
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npx vitest run scripts/anchorFixtures/__tests__/schema.test.ts
```

Expected: PASS, 4 tests.

- [ ] **Step 5: Add gitignore exception**

Edit the **repo root** `.gitignore`. Find the line `/Videos/` and add an exception below it so the fixture file is tracked:

Before:
```
/Videos/
```

After:
```
/Videos/
!/Videos/*/
!/Videos/*/*_anchor_fixtures.json
```

The two `!` lines are needed because git's gitignore semantics require re-including parent directories before re-including a file inside them. Composite working files (`_oracle_inputs/`) are still ignored because they're not whitelisted.

Verify with a sanity check (this should print nothing — meaning no untracked anchor-fixture files exist yet, which is correct):

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git check-ignore -v "Videos/ivan_1/ivan_1_anchor_fixtures.json"
```

Expected output: empty (file would be tracked if it existed). If you see `.gitignore:N:/Videos/`, the rule above didn't take effect.

- [ ] **Step 6: Commit**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
git add .gitignore poses_viewer/scripts/anchorFixtures/schema.ts \
  poses_viewer/scripts/anchorFixtures/__tests__/schema.test.ts
git commit -m "feat(poses_viewer): add anchor fixture schema + loader"
```

---

## Task 2: RUBRIC.md (in-session judging instructions)

**Files:**
- Create: `poses_viewer/scripts/anchorFixtures/RUBRIC.md`

**Why now:** `RUBRIC.md` is referenced by `rubricVersion` (Task 1) and by the workflow docs. It's a tracked text file, no test needed.

- [ ] **Step 1: Write the rubric file**

Create `poses_viewer/scripts/anchorFixtures/RUBRIC.md` with this exact content (this is the rubric Claude follows when judging composites in-session):

```markdown
# Anchor fixture judging rubric (rubricVersion = 1)

You are judging how well a reconstructed mannequin matches a player's
pose in a video frame.

The image you read is a composite: the video frame, with our reconstructed
mannequin drawn on top in semi-transparent green. Your job is to score the
match.

## What to score

Score these three things, all integers 0–10:

### imageQualityScore — How usable is this frame for comparison?

- 10 = player fully visible, no occlusion, mannequin anchored sensibly
       (overlapping the player's body, not floating off in space).
- 5  = significant occlusion or mannequin clearly anchored to the wrong place.
- 0  = unusable: player off-frame, or mannequin drawn somewhere unrelated
       to the player.

A LOW SCORE HERE IS NOT THE MANNEQUIN'S FAULT. It means the input data was
bad. Frames with imageQualityScore < 5 are skipped, not blamed on the
extractor.

### torsoScore — How well does the mannequin's torso match the player's?

Considers spine, shoulders, hips.

- 10 = visually indistinguishable.
- 7  = matches in all major axes; minor lean or yaw difference visible only
       on close inspection.
- 5  = clear mismatch in one axis (e.g. tilt off by ~15°, yaw clearly wrong
       direction).
- 0  = bears no resemblance.

### rightArmScore — Same scale, applied to the right arm

Shoulder → elbow → wrist → hand.

## Reasons

For each of the three: write a one-sentence reason. Be specific about
WHAT differs, not whether it's good or bad.

- Good reason: "Elbow position drifts ~15cm forward of player's elbow."
- Bad reason:  "Right arm doesn't match well."

## What NOT to score

DO NOT score left arm or legs. The mannequin's left-arm/leg positions in
this rendering may not reflect the actual extraction output (single-camera
ambiguity zeroes them) and should be ignored.

## After scoring

Invoke `write-anchor-fixture-entry` with the scores. The CLI requires
`--judged-by <model identity>`; fill in the model snapshot you are running
as. The CLI looks up `extractedAnchor` from the meta file the prep CLI
wrote — you don't pass it.
```

- [ ] **Step 2: Commit**

```bash
git add poses_viewer/scripts/anchorFixtures/RUBRIC.md
git commit -m "docs(poses_viewer): add visual-judge rubric for anchor fixtures"
```

---

## Task 3: CLI arg parser helper

**Files:**
- Create: `poses_viewer/scripts/anchorFixtures/args.ts`
- Create: `poses_viewer/scripts/anchorFixtures/__tests__/args.test.ts`

**Why a tiny helper:** the two CLIs need consistent flag parsing without pulling in commander/yargs. ~40 lines, fully tested.

- [ ] **Step 1: Write the failing test**

```ts
// poses_viewer/scripts/anchorFixtures/__tests__/args.test.ts
import { describe, it, expect } from 'vitest'
import { parseArgs, parseFrameRange } from '../args'

describe('parseArgs', () => {
  it('parses --key value pairs', () => {
    expect(parseArgs(['--video', 'ivan_1', '--frames', '315-320']))
      .toEqual({ video: 'ivan_1', frames: '315-320' })
  })
  it('parses --flag (boolean true)', () => {
    expect(parseArgs(['--force', '--video', 'x']))
      .toEqual({ force: true, video: 'x' })
  })
  it('throws on bare positional', () => {
    expect(() => parseArgs(['ivan_1'])).toThrow(/expected --flag/)
  })
})

describe('parseFrameRange', () => {
  it('single frame', () => {
    expect(parseFrameRange('315')).toEqual([315])
  })
  it('range', () => {
    expect(parseFrameRange('315-318')).toEqual([315, 316, 317, 318])
  })
  it('list', () => {
    expect(parseFrameRange('1,3,5')).toEqual([1, 3, 5])
  })
  it('mixed list+range', () => {
    expect(parseFrameRange('1-3,7,10-11')).toEqual([1, 2, 3, 7, 10, 11])
  })
  it('rejects empty', () => {
    expect(() => parseFrameRange('')).toThrow()
  })
  it('rejects reversed range', () => {
    expect(() => parseFrameRange('10-5')).toThrow(/reversed/)
  })
  it('rejects negative', () => {
    expect(() => parseFrameRange('-1')).toThrow()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npx vitest run scripts/anchorFixtures/__tests__/args.test.ts
```

Expected: FAIL — module not found.

- [ ] **Step 3: Write minimal implementation**

```ts
// poses_viewer/scripts/anchorFixtures/args.ts

export type ArgValue = string | boolean
export type ParsedArgs = Record<string, ArgValue>

export function parseArgs(argv: string[]): ParsedArgs {
  const out: ParsedArgs = {}
  let i = 0
  while (i < argv.length) {
    const tok = argv[i]
    if (!tok.startsWith('--')) {
      throw new Error(`Unexpected positional "${tok}" — expected --flag or --key value`)
    }
    const key = tok.slice(2)
    const next = argv[i + 1]
    if (next === undefined || next.startsWith('--')) {
      out[key] = true
      i += 1
    } else {
      out[key] = next
      i += 2
    }
  }
  return out
}

export function requireString(args: ParsedArgs, key: string): string {
  const v = args[key]
  if (typeof v !== 'string' || v.length === 0) {
    throw new Error(`Missing required arg --${key}`)
  }
  return v
}

export function requireScore(args: ParsedArgs, key: string): number {
  const v = args[key]
  if (typeof v !== 'string') throw new Error(`Missing required arg --${key}`)
  const n = Number(v)
  if (!Number.isInteger(n) || n < 0 || n > 10) {
    throw new Error(`--${key} must be an integer 0..10 (got ${v})`)
  }
  return n
}

export function parseFrameRange(spec: string): number[] {
  if (!spec) throw new Error('Empty frame range')
  const out: number[] = []
  for (const part of spec.split(',')) {
    if (!part) throw new Error(`Empty segment in "${spec}"`)
    if (part.includes('-')) {
      const [aStr, bStr] = part.split('-')
      const a = Number(aStr), b = Number(bStr)
      if (!Number.isInteger(a) || !Number.isInteger(b) || a < 0 || b < 0) {
        throw new Error(`Bad range "${part}"`)
      }
      if (b < a) throw new Error(`Reversed range "${part}"`)
      for (let n = a; n <= b; n++) out.push(n)
    } else {
      const n = Number(part)
      if (!Number.isInteger(n) || n < 0) throw new Error(`Bad frame "${part}"`)
      out.push(n)
    }
  }
  return out
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npx vitest run scripts/anchorFixtures/__tests__/args.test.ts
```

Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/scripts/anchorFixtures/args.ts \
  poses_viewer/scripts/anchorFixtures/__tests__/args.test.ts
git commit -m "feat(poses_viewer): add CLI arg parser helper for anchor scripts"
```

---

## Task 4: ffmpeg frame extractor

**Files:**
- Create: `poses_viewer/scripts/anchorFixtures/extractFrames.ts`

**Note:** Smoke-tested manually rather than unit-tested — ffmpeg is a system binary and mocking it adds no value. Failure modes (missing binary, frame OOB) bail with clear errors.

- [ ] **Step 1: Write the implementation**

```ts
// poses_viewer/scripts/anchorFixtures/extractFrames.ts
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'

const VIDEO_EXTS = ['.mp4', '.mov', '.webm']

export function findVideoFile(videoBase: string, videosDir: string): string {
  const dir = path.join(videosDir, videoBase)
  if (!fs.existsSync(dir)) {
    throw new Error(`Video folder not found: ${dir}`)
  }
  for (const f of fs.readdirSync(dir)) {
    if (f.startsWith(videoBase + '.') && VIDEO_EXTS.includes(path.extname(f).toLowerCase())) {
      return path.join(dir, f)
    }
  }
  throw new Error(`No video file in ${dir} matching ${videoBase}.{${VIDEO_EXTS.join('|')}}`)
}

/**
 * Extract a single frame from a video to a JPG file. Frame index is 0-based,
 * matching ffmpeg's `select=eq(n,N)` and the poses JSON's `frameIndex`.
 *
 * Returns the path to the JPG (under the OS temp dir, caller's responsibility
 * to delete unless KEEP_FRAMES=1).
 */
export function extractFrameJpg(videoPath: string, frameIndex: number): string {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'anchor-frame-'))
  const outPath = path.join(tmpDir, `frame_${frameIndex}.jpg`)
  // -nostdin: don't read from stdin (CI safety)
  // -loglevel error: suppress chatter
  // select=eq(n\,N): pick exactly frame N (0-based)
  // -vframes 1: stop after one frame
  // -q:v 2: high JPG quality (lossless enough for visual judge)
  try {
    execFileSync('ffmpeg', [
      '-nostdin', '-loglevel', 'error',
      '-i', videoPath,
      '-vf', `select=eq(n\\,${frameIndex})`,
      '-vframes', '1',
      '-q:v', '2',
      outPath,
    ])
  } catch (err) {
    fs.rmSync(tmpDir, { recursive: true, force: true })
    const e = err as NodeJS.ErrnoException
    if (e.code === 'ENOENT') {
      throw new Error('ffmpeg not on PATH. Install with: brew install ffmpeg')
    }
    throw new Error(`ffmpeg failed for frame ${frameIndex}: ${e.message}`)
  }
  if (!fs.existsSync(outPath) || fs.statSync(outPath).size === 0) {
    fs.rmSync(tmpDir, { recursive: true, force: true })
    throw new Error(`ffmpeg produced empty output for frame ${frameIndex} — frame OOB?`)
  }
  return outPath
}
```

- [ ] **Step 2: Smoke-test manually**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach/poses_viewer
npx tsx -e "
  import { findVideoFile, extractFrameJpg } from './scripts/anchorFixtures/extractFrames'
  const vp = findVideoFile('ivan_1', '../Videos')
  console.log('video:', vp)
  const out = extractFrameJpg(vp, 315)
  console.log('jpg at:', out, 'size:', require('node:fs').statSync(out).size)
"
```

Expected: prints the video path and a non-zero JPG size. Open the JPG manually to confirm it's frame 315 of `ivan_1`.

If `npx tsx` is not available yet (Task 9 adds it as a devDep), install first:

```bash
npm install -D tsx
```

- [ ] **Step 3: Commit**

```bash
git add poses_viewer/scripts/anchorFixtures/extractFrames.ts \
  poses_viewer/package.json poses_viewer/package-lock.json
git commit -m "feat(poses_viewer): add ffmpeg frame extractor for anchor prep"
```

---

## Task 5: Standalone render route (#/render)

**Files:**
- Create: `poses_viewer/src/components/MannequinRenderRoute.tsx`
- Modify: `poses_viewer/src/hooks/useHashRoute.ts`
- Modify: `poses_viewer/src/App.tsx`

**Goal:** A minimal route that mounts `Drill2Mannequin` with a transparent canvas, fixed orthographic camera, and a `window.__mannequinReady` flag for Playwright. Everything is driven via URL query params so Playwright doesn't need to inject scripts.

- [ ] **Step 1: Add 'render' to the Route union**

Edit [poses_viewer/src/hooks/useHashRoute.ts](poses_viewer/src/hooks/useHashRoute.ts):

Change:
```ts
export type Route = 'main' | 'mannequin' | 'drill2' | 'dataset'
export const ROUTES: readonly Route[] = ['main', 'mannequin', 'drill2', 'dataset']
```

To:
```ts
export type Route = 'main' | 'mannequin' | 'drill2' | 'dataset' | 'render'
export const ROUTES: readonly Route[] = ['main', 'mannequin', 'drill2', 'dataset', 'render']
```

Add to `ROUTE_TITLES`:
```ts
'render': 'Mannequin Render — Poses Viewer',
```

- [ ] **Step 2: Create `MannequinRenderRoute.tsx`**

```tsx
// poses_viewer/src/components/MannequinRenderRoute.tsx
//
// Standalone mount of Drill2Mannequin used by the prep CLI's Playwright
// step. Reads the anchor + canvas size from the URL query string, renders
// once on a transparent background with an orthographic camera covering
// the [0,1] x-y range (matching MediaPipe coordinate space), then sets
// window.__mannequinReady so Playwright knows it can screenshot.

import { useEffect, useMemo, useState } from 'react'
import type { PoseAnchor } from '../drill/PoseAnchor'
import { reconstructFromAnchor } from '../drill/skeletonReconstructor'
import { BONES } from '../drill/SkeletonModel'
import Drill2Mannequin from './Drill2Mannequin'

declare global {
  interface Window {
    __mannequinReady?: boolean
  }
}

interface ParsedQuery {
  anchor: PoseAnchor
  width: number
  height: number
}

function parseQuery(): ParsedQuery {
  const search = new URLSearchParams(window.location.hash.split('?')[1] ?? '')
  const anchorB64 = search.get('anchor')
  const width = Number(search.get('width') ?? '0')
  const height = Number(search.get('height') ?? '0')
  if (!anchorB64) throw new Error('Missing ?anchor=<base64 json>')
  if (!Number.isFinite(width) || width <= 0) throw new Error('Bad ?width')
  if (!Number.isFinite(height) || height <= 0) throw new Error('Bad ?height')
  const anchorJson = atob(anchorB64)
  const anchor = JSON.parse(anchorJson) as PoseAnchor
  return { anchor, width, height }
}

export default function MannequinRenderRoute() {
  const [error, setError] = useState<string | null>(null)
  const parsed = useMemo<ParsedQuery | null>(() => {
    try { return parseQuery() } catch (e) { setError((e as Error).message); return null }
  }, [])

  // Reconstruction happens inside Drill2Mannequin via its anchor prop, but we
  // also pre-validate it here so a bad anchor surfaces an explicit error rather
  // than rendering an empty canvas.
  useEffect(() => {
    if (!parsed) return
    try {
      reconstructFromAnchor(parsed.anchor)
    } catch (e) {
      setError(`reconstructFromAnchor failed: ${(e as Error).message}`)
    }
  }, [parsed])

  // After first paint, signal readiness on the *next* RAF. Two RAFs are used
  // because react-three-fiber mounts on the first RAF and the canvas is only
  // populated on the second.
  useEffect(() => {
    if (!parsed || error) return
    let raf2 = 0
    const raf1 = requestAnimationFrame(() => {
      raf2 = requestAnimationFrame(() => {
        window.__mannequinReady = true
      })
    })
    return () => {
      cancelAnimationFrame(raf1)
      cancelAnimationFrame(raf2)
    }
  }, [parsed, error])

  if (error) {
    return (
      <div style={{ color: 'red', fontFamily: 'monospace', padding: 16 }}>
        Render route error: {error}
      </div>
    )
  }
  if (!parsed) return null

  return (
    <div
      style={{
        width: parsed.width,
        height: parsed.height,
        background: 'transparent',
      }}
    >
      <Drill2Mannequin
        anchor={parsed.anchor}
        bones={BONES}
        // Transparent + orthographic + fixed camera; see Drill2Mannequin
        // props for the existing render-only knobs we reuse.
        backgroundTransparent
        orthographic
        viewBox={{ minX: 0, maxX: 1, minY: 0, maxY: 1 }}
      />
    </div>
  )
}
```

**Note for the engineer:** `Drill2Mannequin` may not currently expose `backgroundTransparent`, `orthographic`, or `viewBox` props. Read [poses_viewer/src/components/Drill2Mannequin.tsx](poses_viewer/src/components/Drill2Mannequin.tsx) and either (a) wire those three optional props through if the existing code already supports them via different names, or (b) add them as new props that default to today's behaviour. Two RAFs is a deliberate workaround for r3f's mount timing — don't simplify it to one.

- [ ] **Step 3: Add route guard in App.tsx**

Edit [poses_viewer/src/App.tsx](poses_viewer/src/App.tsx):

Add the import near the top, alongside the other route component imports:
```ts
import MannequinRenderRoute from './components/MannequinRenderRoute'
```

In the route guard block (currently around line 1030), add:
```tsx
if (route === 'render') {
  return <MannequinRenderRoute />
}
```

Place it **before** the `dataset` / `mannequin` / `drill2` checks so it always wins. The `#/render` route doesn't take an `onClose` and is never meant to be navigated to manually.

- [ ] **Step 4: Smoke-test in browser**

```bash
cd poses_viewer
npm run dev
```

In another terminal, build a tiny sample anchor and open the URL:

```bash
node -e "
  const a = { torsoTiltDeg: 24.5, /* fill the rest from any default */ }
  console.log('http://localhost:5780/#/render?anchor=' + Buffer.from(JSON.stringify(a)).toString('base64') + '&width=720&height=1280')
"
```

Open the printed URL. Expected: a green wireframe mannequin on a transparent (page-default) background. Open devtools and run `window.__mannequinReady` — should be `true`.

If `Drill2Mannequin` doesn't accept the orthographic/transparent props yet, the render will fall back to its perspective default — that's acceptable for this smoke test, but Task 6 (composite) needs orthographic to work. Fix Drill2Mannequin's prop wiring before moving on.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/hooks/useHashRoute.ts \
  poses_viewer/src/components/MannequinRenderRoute.tsx \
  poses_viewer/src/App.tsx
# also commit any Drill2Mannequin prop-plumbing changes if you needed them:
# git add poses_viewer/src/components/Drill2Mannequin.tsx
git commit -m "feat(poses_viewer): add #/render route for headless mannequin screenshots"
```

---

## Task 6: Playwright render wrapper

**Files:**
- Create: `poses_viewer/scripts/anchorFixtures/renderMannequin.ts`
- Modify: `poses_viewer/package.json` (add `playwright` to devDependencies)

**Goal:** Headless Chromium that navigates to `#/render?...`, awaits the ready flag, and screenshots the mannequin canvas as a transparent PNG.

- [ ] **Step 1: Install Playwright**

```bash
cd poses_viewer
npm install -D playwright
npx playwright install chromium
```

- [ ] **Step 2: Write the wrapper**

```ts
// poses_viewer/scripts/anchorFixtures/renderMannequin.ts
import { chromium, type Browser, type Page } from 'playwright'
import type { PoseAnchor } from '../../src/drill/PoseAnchor'

const READY_TIMEOUT_MS = 5000

export interface RenderInput {
  anchor: PoseAnchor
  width: number
  height: number
}

export class MannequinRenderer {
  private browser: Browser | null = null
  private page: Page | null = null
  private devUrl: string

  constructor(devUrl = 'http://localhost:5780') {
    this.devUrl = devUrl
  }

  async start(): Promise<void> {
    this.browser = await chromium.launch({ headless: true })
    this.page = await this.browser.newPage()
  }

  async render(input: RenderInput): Promise<Buffer> {
    if (!this.page) throw new Error('Renderer not started')
    const anchorB64 = Buffer.from(JSON.stringify(input.anchor)).toString('base64')
    const url = `${this.devUrl}/#/render?anchor=${anchorB64}&width=${input.width}&height=${input.height}`
    await this.page.setViewportSize({ width: input.width, height: input.height })
    await this.page.goto(url, { waitUntil: 'load' })
    // Wait for the readiness flag (set after 2 RAFs by MannequinRenderRoute)
    await this.page.waitForFunction(
      () => (window as Window & { __mannequinReady?: boolean }).__mannequinReady === true,
      undefined,
      { timeout: READY_TIMEOUT_MS },
    )
    // Screenshot the document with a transparent background. omitBackground:true
    // gives us alpha=0 outside the mannequin's own pixels, which is exactly
    // what composite.ts expects.
    return await this.page.screenshot({ type: 'png', omitBackground: true })
  }

  async stop(): Promise<void> {
    await this.page?.close()
    await this.browser?.close()
    this.page = null
    this.browser = null
  }
}
```

- [ ] **Step 3: Smoke-test**

The dev server must be running on port 5780.

```bash
cd poses_viewer
# In one terminal: npm run dev
# In another:
npx tsx -e "
  import { MannequinRenderer } from './scripts/anchorFixtures/renderMannequin'
  import fs from 'node:fs'
  import { extractAnchorFromLandmarks } from './src/drill/anchorExtractor'
  import { parsePoseFixture } from './src/drill/parsePoseFixture'
  const poses = JSON.parse(fs.readFileSync('../Videos/ivan_1/ivan_1_poses.json', 'utf-8'))
  const fix = parsePoseFixture(poses)
  const lms = fix.frames.find(f => f.frameIndex === 315).landmarks
  const anchor = extractAnchorFromLandmarks(lms)
  const r = new MannequinRenderer()
  await r.start()
  const png = await r.render({ anchor, width: 720, height: 1280 })
  fs.writeFileSync('/tmp/mannequin-test.png', png)
  await r.stop()
  console.log('wrote', png.length, 'bytes')
"
```

Expected: writes `/tmp/mannequin-test.png` ~tens of KB. Open it — should be a green mannequin on transparent background.

- [ ] **Step 4: Commit**

```bash
git add poses_viewer/scripts/anchorFixtures/renderMannequin.ts \
  poses_viewer/package.json poses_viewer/package-lock.json
git commit -m "feat(poses_viewer): add Playwright wrapper for headless mannequin render"
```

---

## Task 7: Composite step (2-point similarity + sharp)

**Files:**
- Create: `poses_viewer/scripts/anchorFixtures/composite.ts`
- Create: `poses_viewer/scripts/anchorFixtures/__tests__/composite.test.ts`
- Modify: `poses_viewer/package.json` (add `sharp` to devDependencies)

**Goal:** Compute a translate+uniform-scale 2D transform from two MediaPipe landmark pairs, apply it to the mannequin PNG, alpha-blend over the video frame JPG. Test the math; rely on visual smoke-test for the imaging.

- [ ] **Step 1: Install sharp**

```bash
cd poses_viewer
npm install -D sharp
```

- [ ] **Step 2: Write the failing test (math only)**

```ts
// poses_viewer/scripts/anchorFixtures/__tests__/composite.test.ts
import { describe, it, expect } from 'vitest'
import { computeSimilarity, type Point2 } from '../composite'

describe('computeSimilarity (translate + uniform scale, no rotation)', () => {
  it('identity when src==dst', () => {
    const src: [Point2, Point2] = [{ x: 0.4, y: 0.3 }, { x: 0.4, y: 0.6 }]
    const dst: [Point2, Point2] = [{ x: 0.4, y: 0.3 }, { x: 0.4, y: 0.6 }]
    const t = computeSimilarity(src, dst)
    expect(t.scale).toBeCloseTo(1, 6)
    expect(t.tx).toBeCloseTo(0, 6)
    expect(t.ty).toBeCloseTo(0, 6)
  })

  it('pure translation', () => {
    const src: [Point2, Point2] = [{ x: 0.5, y: 0.5 }, { x: 0.5, y: 0.7 }]
    const dst: [Point2, Point2] = [{ x: 0.6, y: 0.6 }, { x: 0.6, y: 0.8 }]
    const t = computeSimilarity(src, dst)
    expect(t.scale).toBeCloseTo(1, 6)
    expect(t.tx).toBeCloseTo(0.1, 6)
    expect(t.ty).toBeCloseTo(0.1, 6)
  })

  it('pure scale around src midpoint', () => {
    // src segment length 0.2 → dst segment length 0.4 → scale 2x
    const src: [Point2, Point2] = [{ x: 0.5, y: 0.4 }, { x: 0.5, y: 0.6 }]
    const dst: [Point2, Point2] = [{ x: 0.5, y: 0.3 }, { x: 0.5, y: 0.7 }]
    const t = computeSimilarity(src, dst)
    expect(t.scale).toBeCloseTo(2, 6)
    // After scale=2 around midpoint (0.5, 0.5), translation must be 0
    expect(t.tx).toBeCloseTo(0, 6)
    expect(t.ty).toBeCloseTo(0, 6)
  })

  it('throws on degenerate src (zero length)', () => {
    const p: [Point2, Point2] = [{ x: 0.5, y: 0.5 }, { x: 0.5, y: 0.5 }]
    expect(() => computeSimilarity(p, p)).toThrow(/degenerate/i)
  })
})
```

- [ ] **Step 3: Run test to verify it fails**

```bash
npx vitest run scripts/anchorFixtures/__tests__/composite.test.ts
```

Expected: FAIL — module not found.

- [ ] **Step 4: Write the implementation**

```ts
// poses_viewer/scripts/anchorFixtures/composite.ts
import sharp from 'sharp'

export interface Point2 { x: number; y: number }

export interface SimilarityTransform {
  /** Uniform scale factor. */
  scale: number
  /** Translation in image-pixel space (applied AFTER scale). */
  tx: number
  ty: number
}

/**
 * Solve for translate + uniform scale (3 DoF) that maps two source points
 * to two destination points. Rotation is fixed at 0 — the mannequin is
 * upright in landmark space and rotating it would mask torso-tilt bugs
 * (see spec § The 2-point similarity transform).
 *
 * Algorithm:
 *   scale = |dst[1] - dst[0]| / |src[1] - src[0]|
 *   t     = mid(dst) - scale * mid(src)
 */
export function computeSimilarity(
  src: [Point2, Point2],
  dst: [Point2, Point2],
): SimilarityTransform {
  const srcDx = src[1].x - src[0].x
  const srcDy = src[1].y - src[0].y
  const srcLen = Math.hypot(srcDx, srcDy)
  if (srcLen < 1e-9) {
    throw new Error('Degenerate similarity input: src points coincide')
  }
  const dstDx = dst[1].x - dst[0].x
  const dstDy = dst[1].y - dst[0].y
  const dstLen = Math.hypot(dstDx, dstDy)
  const scale = dstLen / srcLen
  const srcMid = { x: (src[0].x + src[1].x) / 2, y: (src[0].y + src[1].y) / 2 }
  const dstMid = { x: (dst[0].x + dst[1].x) / 2, y: (dst[0].y + dst[1].y) / 2 }
  return {
    scale,
    tx: dstMid.x - scale * srcMid.x,
    ty: dstMid.y - scale * srcMid.y,
  }
}

export interface CompositeInput {
  /** JPG bytes of the video frame (any size; output matches this size). */
  frameJpg: Buffer
  /** PNG bytes of the rendered mannequin (transparent bg, same logical
   *  width/height as frameJpg by convention — the renderer was launched
   *  at the frame's resolution). */
  mannequinPng: Buffer
  /** Landmark pairs in [0,1] coords. src = mannequin's hipMid+shoulderMid,
   *  dst = video's hipMid+shoulderMid. */
  src: [Point2, Point2]
  dst: [Point2, Point2]
}

/**
 * Composite the mannequin PNG over the video frame JPG using a 2-point
 * similarity transform. Returns PNG bytes of the same pixel dimensions as
 * the input frame.
 */
export async function compositeMannequinOverFrame(input: CompositeInput): Promise<Buffer> {
  const frameMeta = await sharp(input.frameJpg).metadata()
  const W = frameMeta.width
  const H = frameMeta.height
  if (!W || !H) throw new Error('Could not read frame dimensions')

  // Convert normalized similarity to pixel-space affine.
  // Landmarks live in [0,1] but the mannequin PNG is rendered at WxH,
  // so the same scale factor applies in pixel space.
  const t = computeSimilarity(input.src, input.dst)

  // sharp.affine uses a 2x2 matrix [[a,b],[c,d]] applied around the image's
  // top-left corner, plus an idx/idy translation. For uniform scale + no
  // rotation: matrix = [[s,0],[0,s]], translation = (tx*W, ty*H).
  const transformed = await sharp(input.mannequinPng)
    .affine(
      [[t.scale, 0], [0, t.scale]],
      { background: { r: 0, g: 0, b: 0, alpha: 0 }, idx: t.tx * W, idy: t.ty * H },
    )
    .resize(W, H, { fit: 'fill', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer()

  return await sharp(input.frameJpg)
    .composite([{ input: transformed, top: 0, left: 0 }])
    .png()
    .toBuffer()
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
npx vitest run scripts/anchorFixtures/__tests__/composite.test.ts
```

Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add poses_viewer/scripts/anchorFixtures/composite.ts \
  poses_viewer/scripts/anchorFixtures/__tests__/composite.test.ts \
  poses_viewer/package.json poses_viewer/package-lock.json
git commit -m "feat(poses_viewer): add 2-point similarity + sharp compositor"
```

---

## Task 8: CLI 1 — `prepare-anchor-composites`

**Files:**
- Create: `poses_viewer/scripts/anchorFixtures/prepareComposites.ts`
- Modify: `poses_viewer/package.json` (add `tsx` devDep + `prepare-anchor-composites` script)

**Goal:** Wire the four units (extract / render / composite / write meta) into a CLI that produces composites + meta files for a frame range.

- [ ] **Step 1: Install tsx and add the script**

```bash
cd poses_viewer
npm install -D tsx
```

Edit `poses_viewer/package.json`. Add to the `scripts` block (after `"test"`):

```json
"prepare-anchor-composites": "tsx scripts/anchorFixtures/prepareComposites.ts",
"write-anchor-fixture-entry": "tsx scripts/anchorFixtures/writeFixtureEntry.ts"
```

(Both go in now even though Task 9 implements the second one — keeps `package.json` changes in one commit per file.)

- [ ] **Step 2: Write the CLI**

```ts
// poses_viewer/scripts/anchorFixtures/prepareComposites.ts
//
// CLI entry point. Deterministic prep: ffmpeg → frame JPG, run extractor on
// the matching landmarks, headless render the mannequin, composite, write
// the composite + meta to Videos/<base>/_oracle_inputs/. No AI calls. The
// resulting PNGs are what Claude reads in-session (RUBRIC.md) before
// invoking write-anchor-fixture-entry.

import fs from 'node:fs'
import http from 'node:http'
import path from 'node:path'
import { spawn, type ChildProcess } from 'node:child_process'
import { extractAnchorFromLandmarks } from '../../src/drill/anchorExtractor'
import { parsePoseFixture } from '../../src/drill/parsePoseFixture'
import { LM } from '../../src/drill/SkeletonModel'
import { extractFrameJpg, findVideoFile } from './extractFrames'
import { MannequinRenderer } from './renderMannequin'
import { compositeMannequinOverFrame, type Point2 } from './composite'
import { parseArgs, parseFrameRange, requireString } from './args'
import type { PoseAnchor } from '../../src/drill/PoseAnchor'

const VIDEOS_DIR = path.resolve(__dirname, '../../../Videos')
const DEV_PORT = 5780
const DEV_URL = `http://localhost:${DEV_PORT}`

interface MetaFile {
  videoBase: string
  frameIndex: number
  /** [0,1] image coords of (hipMid, shoulderMid) used as similarity source. */
  alignmentSrc: [Point2, Point2]
  alignmentDst: [Point2, Point2]
  compositeWidth: number
  compositeHeight: number
  extractedAnchor: PoseAnchor
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2))
  const videoBase = requireString(args, 'video')
  const framesSpec = requireString(args, 'frames')
  const force = args.force === true
  const dryRun = args['dry-run'] === true

  const frames = parseFrameRange(framesSpec)
  console.log(`Prep: video=${videoBase} frames=[${frames[0]}..${frames[frames.length - 1]}] (${frames.length} total)`)
  if (dryRun) { console.log('--dry-run: stopping here.'); return }

  const videoPath = findVideoFile(videoBase, VIDEOS_DIR)
  const posesPath = path.join(VIDEOS_DIR, videoBase, `${videoBase}_poses.json`)
  if (!fs.existsSync(posesPath)) throw new Error(`Missing poses file: ${posesPath}`)
  const fixture = parsePoseFixture(JSON.parse(fs.readFileSync(posesPath, 'utf-8')))

  const oracleDir = path.join(VIDEOS_DIR, videoBase, '_oracle_inputs')
  fs.mkdirSync(oracleDir, { recursive: true })

  const devProc = await ensureDevServer()
  const renderer = new MannequinRenderer(DEV_URL)
  await renderer.start()
  try {
    for (const frameIndex of frames) {
      const compositePath = path.join(oracleDir, `frame_${frameIndex}.png`)
      const metaPath = path.join(oracleDir, `frame_${frameIndex}.meta.json`)
      if (!force && fs.existsSync(compositePath) && fs.existsSync(metaPath)) {
        console.log(`  frame ${frameIndex}: skip (exists)`)
        continue
      }

      const frameMatch = fixture.frames.find(f => f.frameIndex === frameIndex)
      if (!frameMatch) { console.log(`  frame ${frameIndex}: skip (no landmarks)`); continue }

      const lms = frameMatch.landmarks
      const anchor = extractAnchorFromLandmarks(lms)

      // Composite resolution = video frame's natural size. Read it lazily by
      // extracting the JPG first, then asking sharp for its dims.
      const jpgPath = extractFrameJpg(videoPath, frameIndex)
      const frameJpg = fs.readFileSync(jpgPath)
      // Cleanup the temp JPG dir
      fs.rmSync(path.dirname(jpgPath), { recursive: true, force: true })

      // Get JPG dimensions via sharp (already a transitive dep of composite.ts)
      const sharp = (await import('sharp')).default
      const meta = await sharp(frameJpg).metadata()
      const W = meta.width, H = meta.height
      if (!W || !H) throw new Error(`Could not read frame ${frameIndex} dims`)

      const mannequinPng = await renderer.render({ anchor, width: W, height: H })

      // Compute alignment from MediaPipe landmark pairs. Both endpoints are
      // landmark midpoints, NOT a single landmark, because hipMid/shoulderMid
      // are robust to a single landmark being noisy.
      const lm = (i: number) => ({ x: lms[i].x, y: lms[i].y })
      const mid = (a: Point2, b: Point2): Point2 => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 })
      const dstHip = mid(lm(LM.L_HIP), lm(LM.R_HIP))
      const dstShoulder = mid(lm(LM.L_SHOULDER), lm(LM.R_SHOULDER))
      // The mannequin's #/render output is fixed-scale around its own center,
      // and its hipMid/shoulderMid in the rendered PNG land at predictable
      // [0,1] positions because the orthographic camera covers [0,1]x[0,1].
      // We re-extract from the same anchor's reconstructed landmarks to get
      // the source pair without coupling to render-route internals.
      const { reconstructFromAnchor } = await import('../../src/drill/skeletonReconstructor')
      const reconstructed = reconstructFromAnchor(anchor)
      const srcHip = mid(
        { x: reconstructed[LM.L_HIP].x, y: reconstructed[LM.L_HIP].y },
        { x: reconstructed[LM.R_HIP].x, y: reconstructed[LM.R_HIP].y },
      )
      const srcShoulder = mid(
        { x: reconstructed[LM.L_SHOULDER].x, y: reconstructed[LM.L_SHOULDER].y },
        { x: reconstructed[LM.R_SHOULDER].x, y: reconstructed[LM.R_SHOULDER].y },
      )

      const composite = await compositeMannequinOverFrame({
        frameJpg,
        mannequinPng,
        src: [srcHip, srcShoulder],
        dst: [dstHip, dstShoulder],
      })

      fs.writeFileSync(compositePath, composite)
      const metaData: MetaFile = {
        videoBase,
        frameIndex,
        alignmentSrc: [srcHip, srcShoulder],
        alignmentDst: [dstHip, dstShoulder],
        compositeWidth: W,
        compositeHeight: H,
        extractedAnchor: anchor,
      }
      fs.writeFileSync(metaPath, JSON.stringify(metaData, null, 2))
      console.log(`  frame ${frameIndex}: wrote ${path.relative(VIDEOS_DIR, compositePath)}`)
    }
  } finally {
    await renderer.stop()
    if (devProc) devProc.kill('SIGTERM')
  }

  console.log(`\nDone. Path: Videos/${videoBase}/_oracle_inputs/`)
  console.log('Ask Claude to judge the composites against scripts/anchorFixtures/RUBRIC.md.')
}

async function ensureDevServer(): Promise<ChildProcess | null> {
  if (await isPortListening(DEV_PORT)) {
    console.log(`Using existing dev server on :${DEV_PORT}`)
    return null
  }
  console.log(`Starting dev server on :${DEV_PORT}...`)
  const proc = spawn('npm', ['run', 'dev'], {
    cwd: path.resolve(__dirname, '../..'),
    stdio: ['ignore', 'pipe', 'pipe'],
    detached: false,
  })
  // Wait up to 10s for port to come up
  const deadline = Date.now() + 10_000
  while (Date.now() < deadline) {
    if (await isPortListening(DEV_PORT)) {
      console.log('Dev server up.')
      return proc
    }
    await new Promise(r => setTimeout(r, 250))
  }
  proc.kill('SIGTERM')
  throw new Error(`Dev server failed to come up on :${DEV_PORT} within 10s`)
}

function isPortListening(port: number): Promise<boolean> {
  return new Promise(resolve => {
    const req = http.get({ host: '127.0.0.1', port, path: '/', timeout: 500 }, res => {
      res.resume(); resolve(true)
    })
    req.on('error', () => resolve(false))
    req.on('timeout', () => { req.destroy(); resolve(false) })
  })
}

main().catch(err => {
  console.error(err)
  process.exit(1)
})
```

- [ ] **Step 3: End-to-end smoke test**

```bash
cd poses_viewer
npm run prepare-anchor-composites -- --video ivan_1 --frames 315
```

Expected: prints `wrote Videos/ivan_1/_oracle_inputs/frame_315.png`. Open that file — should show frame 315 with a green mannequin overlaid roughly on the player. Open `frame_315.meta.json` — should contain `extractedAnchor` with all PoseAnchor fields populated.

If alignment is way off, there's a coordinate-system mismatch between landmark space and the rendered mannequin's pixels. Read [poses_viewer/CLAUDE.md](poses_viewer/CLAUDE.md) "three.js mannequin coordinate system matches landmark axes" — the renderer's orthographic frustum must cover `[0,1] × [0,1]` for src points to be in landmark space.

- [ ] **Step 4: Commit**

```bash
git add poses_viewer/scripts/anchorFixtures/prepareComposites.ts \
  poses_viewer/package.json poses_viewer/package-lock.json
git commit -m "feat(poses_viewer): add prepare-anchor-composites CLI"
```

---

## Task 9: CLI 2 — `write-anchor-fixture-entry`

**Files:**
- Create: `poses_viewer/scripts/anchorFixtures/writeFixtureEntry.ts`
- Create: `poses_viewer/scripts/anchorFixtures/__tests__/writeFixtureEntry.test.ts`

**Goal:** Atomic upsert of one frame's judgment into the cache file. No AI, no rendering — pure JSON manipulation. Reads `_oracle_inputs/frame_<N>.meta.json` for `extractedAnchor`.

- [ ] **Step 1: Write the failing test**

```ts
// poses_viewer/scripts/anchorFixtures/__tests__/writeFixtureEntry.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { writeFixtureEntry } from '../writeFixtureEntry'
import { CURRENT_RUBRIC_VERSION, CURRENT_RENDERER_VERSION, CURRENT_SCHEMA_VERSION } from '../schema'

describe('writeFixtureEntry', () => {
  let videosDir: string

  beforeEach(() => {
    videosDir = fs.mkdtempSync(path.join(os.tmpdir(), 'wfe-'))
    const baseDir = path.join(videosDir, 'demo')
    fs.mkdirSync(path.join(baseDir, '_oracle_inputs'), { recursive: true })
    fs.writeFileSync(
      path.join(baseDir, '_oracle_inputs', 'frame_42.meta.json'),
      JSON.stringify({
        videoBase: 'demo',
        frameIndex: 42,
        alignmentSrc: [{ x: 0.5, y: 0.5 }, { x: 0.5, y: 0.7 }],
        alignmentDst: [{ x: 0.5, y: 0.5 }, { x: 0.5, y: 0.7 }],
        compositeWidth: 720, compositeHeight: 1280,
        extractedAnchor: { stub: true },
      }),
    )
  })

  it('creates a fixture file with one entry', () => {
    writeFixtureEntry({
      videoBase: 'demo',
      videosDir,
      frame: 42,
      imageQuality: { score: 8, reason: 'clear' },
      torso: { score: 9, reason: 'tight' },
      rightArm: { score: 7, reason: 'minor drift' },
      judgedBy: 'claude-opus-4-7',
    })
    const f = path.join(videosDir, 'demo', 'demo_anchor_fixtures.json')
    const fix = JSON.parse(fs.readFileSync(f, 'utf-8'))
    expect(fix.schemaVersion).toBe(CURRENT_SCHEMA_VERSION)
    expect(fix.rubricVersion).toBe(CURRENT_RUBRIC_VERSION)
    expect(fix.rendererVersion).toBe(CURRENT_RENDERER_VERSION)
    expect(fix.frames['42'].torsoScore).toBe(9)
    expect(fix.frames['42'].judgedBy).toBe('claude-opus-4-7')
    expect(fix.frames['42'].extractedAnchor).toEqual({ stub: true })
  })

  it('upserts (replaces) an existing entry', () => {
    const args = {
      videoBase: 'demo', videosDir, frame: 42,
      imageQuality: { score: 8, reason: 'clear' },
      torso: { score: 5, reason: 'first' },
      rightArm: { score: 5, reason: 'first' },
      judgedBy: 'claude-opus-4-7',
    } as const
    writeFixtureEntry(args)
    writeFixtureEntry({ ...args, torso: { score: 9, reason: 'second' } })
    const fix = JSON.parse(fs.readFileSync(
      path.join(videosDir, 'demo', 'demo_anchor_fixtures.json'), 'utf-8'))
    expect(fix.frames['42'].torsoScore).toBe(9)
    expect(fix.frames['42'].torsoReason).toBe('second')
  })

  it('bails when meta file is missing', () => {
    expect(() => writeFixtureEntry({
      videoBase: 'demo', videosDir, frame: 99,
      imageQuality: { score: 8, reason: 'clear' },
      torso: { score: 9, reason: 'tight' },
      rightArm: { score: 7, reason: 'minor drift' },
      judgedBy: 'claude-opus-4-7',
    })).toThrow(/meta file/i)
  })

  it('bails when an existing fixture has a stale rubricVersion', () => {
    const baseDir = path.join(videosDir, 'demo')
    fs.writeFileSync(
      path.join(baseDir, 'demo_anchor_fixtures.json'),
      JSON.stringify({
        videoBase: 'demo',
        schemaVersion: CURRENT_SCHEMA_VERSION,
        rubricVersion: 999,
        rendererVersion: CURRENT_RENDERER_VERSION,
        frames: {},
      }),
    )
    expect(() => writeFixtureEntry({
      videoBase: 'demo', videosDir, frame: 42,
      imageQuality: { score: 8, reason: 'clear' },
      torso: { score: 9, reason: 'tight' },
      rightArm: { score: 7, reason: 'minor drift' },
      judgedBy: 'claude-opus-4-7',
    })).toThrow(/rubricVersion/)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npx vitest run scripts/anchorFixtures/__tests__/writeFixtureEntry.test.ts
```

Expected: FAIL — module not found.

- [ ] **Step 3: Write the implementation**

```ts
// poses_viewer/scripts/anchorFixtures/writeFixtureEntry.ts
import fs from 'node:fs'
import path from 'node:path'
import {
  CURRENT_RENDERER_VERSION,
  CURRENT_RUBRIC_VERSION,
  CURRENT_SCHEMA_VERSION,
  fixturePath,
  loadAnchorFixture,
  type AnchorFixture,
  type AnchorFixtureFrame,
} from './schema'
import { parseArgs, requireScore, requireString } from './args'

export interface ScoreEntry { score: number; reason: string }

export interface WriteEntryInput {
  videoBase: string
  videosDir: string
  frame: number
  imageQuality: ScoreEntry
  torso: ScoreEntry
  rightArm: ScoreEntry
  judgedBy: string
}

interface MetaFile {
  videoBase: string
  frameIndex: number
  extractedAnchor: unknown
  // ...other meta fields exist but are unused here
}

export function writeFixtureEntry(input: WriteEntryInput): void {
  const baseDir = path.join(input.videosDir, input.videoBase)
  const metaPath = path.join(baseDir, '_oracle_inputs', `frame_${input.frame}.meta.json`)
  if (!fs.existsSync(metaPath)) {
    throw new Error(
      `Missing meta file ${metaPath}. ` +
      `Run prepare-anchor-composites for this frame first.`,
    )
  }
  const meta = JSON.parse(fs.readFileSync(metaPath, 'utf-8')) as MetaFile

  const fixture = loadAnchorFixture(input.videoBase, input.videosDir)
  if (fixture.rubricVersion !== CURRENT_RUBRIC_VERSION) {
    throw new Error(
      `Fixture file rubricVersion=${fixture.rubricVersion} but current is ` +
      `${CURRENT_RUBRIC_VERSION}. Refresh required (re-judge entries) ` +
      `or delete the fixture file.`,
    )
  }
  if (fixture.rendererVersion !== CURRENT_RENDERER_VERSION) {
    throw new Error(
      `Fixture file rendererVersion=${fixture.rendererVersion} but current ` +
      `is ${CURRENT_RENDERER_VERSION}. Refresh required.`,
    )
  }

  const entry: AnchorFixtureFrame = {
    generatedAt: new Date().toISOString(),
    judgedBy: input.judgedBy,
    imageQualityScore: input.imageQuality.score,
    imageQualityReason: input.imageQuality.reason,
    torsoScore: input.torso.score,
    torsoReason: input.torso.reason,
    rightArmScore: input.rightArm.score,
    rightArmReason: input.rightArm.reason,
    // Trust the meta file; it was written by prepareComposites with the same
    // anchor that produced the rendered figure being judged.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    extractedAnchor: meta.extractedAnchor as any,
  }

  const next: AnchorFixture = {
    ...fixture,
    schemaVersion: CURRENT_SCHEMA_VERSION,
    rubricVersion: CURRENT_RUBRIC_VERSION,
    rendererVersion: CURRENT_RENDERER_VERSION,
    frames: { ...fixture.frames, [String(input.frame)]: entry },
  }

  // Atomic write: temp file + rename. Survives Ctrl-C mid-write.
  const target = fixturePath(input.videoBase, input.videosDir)
  const tmp = target + '.tmp'
  fs.mkdirSync(path.dirname(target), { recursive: true })
  fs.writeFileSync(tmp, JSON.stringify(next, null, 2))
  fs.renameSync(tmp, target)
}

// CLI entry point — runs only when invoked directly via tsx.
if (import.meta.url === `file://${process.argv[1]}`) {
  const args = parseArgs(process.argv.slice(2))
  const videoBase = requireString(args, 'video')
  const frame = Number(requireString(args, 'frame'))
  if (!Number.isInteger(frame) || frame < 0) {
    throw new Error('--frame must be a non-negative integer')
  }
  writeFixtureEntry({
    videoBase,
    videosDir: path.resolve(__dirname, '../../../Videos'),
    frame,
    imageQuality: {
      score: requireScore(args, 'image-quality-score'),
      reason: requireString(args, 'image-quality-reason'),
    },
    torso: {
      score: requireScore(args, 'torso-score'),
      reason: requireString(args, 'torso-reason'),
    },
    rightArm: {
      score: requireScore(args, 'right-arm-score'),
      reason: requireString(args, 'right-arm-reason'),
    },
    judgedBy: requireString(args, 'judged-by'),
  })
  console.log(`Wrote entry for ${videoBase} frame ${frame}.`)
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npx vitest run scripts/anchorFixtures/__tests__/writeFixtureEntry.test.ts
```

Expected: PASS, 4 tests.

- [ ] **Step 5: End-to-end smoke test**

Assuming Task 8's smoke test wrote `frame_315.png` and `frame_315.meta.json`:

```bash
cd poses_viewer
npm run write-anchor-fixture-entry -- \
  --video ivan_1 --frame 315 \
  --image-quality-score 8 --image-quality-reason "clear" \
  --torso-score 8 --torso-reason "matches" \
  --right-arm-score 7 --right-arm-reason "slight drift" \
  --judged-by claude-opus-4-7-test
cat ../Videos/ivan_1/ivan_1_anchor_fixtures.json | head -30
```

Expected: a fixture file with frame 315's entry, schemaVersion=1, rubricVersion=1, rendererVersion=1.

- [ ] **Step 6: Commit**

```bash
git add poses_viewer/scripts/anchorFixtures/writeFixtureEntry.ts \
  poses_viewer/scripts/anchorFixtures/__tests__/writeFixtureEntry.test.ts
git commit -m "feat(poses_viewer): add write-anchor-fixture-entry CLI"
```

---

## Task 10: Calibration round (manual, then commit fixtures)

**Files:**
- Modify: `Videos/ivan_1/ivan_1_anchor_fixtures.json` (created via CLI)
- Modify: `Videos/andrii_1/andrii_1_anchor_fixtures.json` (created via CLI)
- Possibly modify: `poses_viewer/scripts/anchorFixtures/RUBRIC.md` (if rubric clarification needed)
- Possibly modify: `poses_viewer/scripts/anchorFixtures/schema.ts` (if thresholds adjusted)

**Why before the test:** the spec § Calibration round says thresholds (7, skip-at-5) are guesses until verified against a real distribution. If we wire the test first and the thresholds are wrong, every test run fails noisily for the wrong reason.

This task is **interactive** — Claude in this same session reads the composites and judges them per RUBRIC.md.

- [ ] **Step 1: Prep both seed videos**

```bash
cd poses_viewer
npm run prepare-anchor-composites -- --video ivan_1 --frames 315-320
npm run prepare-anchor-composites -- --video andrii_1 --frames 57-63
```

Expected: 6 + 7 = 13 composites under `_oracle_inputs/` for each.

- [ ] **Step 2: Judge each composite in-session**

For each composite, the assistant follows RUBRIC.md:
1. `Read` `Videos/<base>/_oracle_inputs/frame_<N>.png`
2. `Read` `Videos/<base>/_oracle_inputs/frame_<N>.meta.json` (only `frameIndex` + `extractedAnchor` are needed for context — alignment fields are diagnostic)
3. Decide imageQuality, torso, rightArm scores + reasons.
4. `Bash` `npm run write-anchor-fixture-entry -- --video <base> --frame <N> ... --judged-by <model>`

Do all 13 frames.

- [ ] **Step 3: Inspect the score distribution**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
jq '.frames | to_entries | map({frame: .key, q: .value.imageQualityScore, t: .value.torsoScore, a: .value.rightArmScore})' \
  Videos/ivan_1/ivan_1_anchor_fixtures.json
jq '.frames | to_entries | map({frame: .key, q: .value.imageQualityScore, t: .value.torsoScore, a: .value.rightArmScore})' \
  Videos/andrii_1/andrii_1_anchor_fixtures.json
```

Look at the distribution:
- If most torso scores cluster at 6 on figures that look fine: either bump threshold to 6, or refine RUBRIC.md's 6-vs-7 distinction (and bump `CURRENT_RUBRIC_VERSION`).
- If most are 8+: the threshold of 7 is appropriately lenient — keep it.
- If imageQuality skips fire on > 30% of frames: skip threshold may be too high — consider 4. If 0% fire when some frames are clearly bad: skip threshold may be too low — consider 6.

- [ ] **Step 4: Commit fixtures + any rubric/threshold updates**

If RUBRIC.md or schema.ts changed, bump `CURRENT_RUBRIC_VERSION` and re-run the prep+judge cycle so fixtures match.

```bash
git add Videos/ivan_1/ivan_1_anchor_fixtures.json \
        Videos/andrii_1/andrii_1_anchor_fixtures.json
# optional, if rubric or thresholds changed:
# git add poses_viewer/scripts/anchorFixtures/RUBRIC.md poses_viewer/scripts/anchorFixtures/schema.ts
git commit -m "chore(poses_viewer): seed anchor fixture cache for ivan_1 and andrii_1"
```

---

## Task 11: The assertion test

**Files:**
- Create: `poses_viewer/src/drill/__tests__/anchorExtractor.cached.test.ts`

**Goal:** Hermetic vitest assertion that the cached scores meet the threshold. Reads only the fixture file — no network, no AI, no rendering.

- [ ] **Step 1: Write the test**

```ts
// poses_viewer/src/drill/__tests__/anchorExtractor.cached.test.ts
//
// End-to-end visual-judge regression test. The fixture file holds Claude's
// scores from a prior in-session judging round; this test asserts they meet
// the pass threshold. Mismatches mean the extractor (or FK, or rendering)
// has regressed visibly — fix the code or refresh the cache via
// prepare-anchor-composites + in-session judging.

import { describe, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import {
  loadAnchorFixture,
  PASS_THRESHOLD,
  QUALITY_SKIP_THRESHOLD,
} from '../../../scripts/anchorFixtures/schema'

interface TestCase { videoBase: string; frameRange: [number, number] }

const CASES: TestCase[] = [
  { videoBase: 'ivan_1',   frameRange: [315, 320] },
  { videoBase: 'andrii_1', frameRange: [57, 63] },
]

const VIDEOS_DIR = path.resolve(__dirname, '../../../../Videos')

for (const { videoBase, frameRange } of CASES) {
  describe(`mannequin pose vs video — ${videoBase} ${frameRange[0]}-${frameRange[1]}`, () => {
    const fixture = loadAnchorFixture(videoBase, VIDEOS_DIR)

    for (let i = frameRange[0]; i <= frameRange[1]; i++) {
      it(`frame ${i}`, ctx => {
        const cached = fixture.frames[String(i)]
        if (!cached) {
          throw new Error(
            `No cached oracle for ${videoBase} frame ${i}. Run:\n` +
            `  npm run prepare-anchor-composites -- --video ${videoBase} --frames ${i}\n` +
            `then ask Claude in this session to judge ` +
            `Videos/${videoBase}/_oracle_inputs/frame_${i}.png against ` +
            `scripts/anchorFixtures/RUBRIC.md and run write-anchor-fixture-entry.`,
          )
        }

        if (cached.imageQualityScore < QUALITY_SKIP_THRESHOLD) {
          ctx.skip(
            `Image quality ${cached.imageQualityScore}/10: ${cached.imageQualityReason}`,
          )
          return
        }

        const failures: string[] = []
        if (cached.torsoScore < PASS_THRESHOLD) {
          failures.push(`  torso: ${cached.torsoScore}/10 — ${cached.torsoReason}`)
        }
        if (cached.rightArmScore < PASS_THRESHOLD) {
          failures.push(`  right arm: ${cached.rightArmScore}/10 — ${cached.rightArmReason}`)
        }

        if (failures.length > 0) {
          throw new Error(
            `Frame ${i} below threshold ${PASS_THRESHOLD}/10:\n` +
            `${failures.join('\n')}\n` +
            `Inspect: open http://localhost:5780/#/mannequin, load ` +
            `video=${videoBase}, frame=${i}, click "Apply cached extracted anchor".`,
          )
        }
      })
    }
  })
}

// Sanity: surface a clear error if the fixture files themselves are
// missing — this happens on a fresh checkout if Task 10's fixtures
// weren't committed.
describe('fixture sanity', () => {
  for (const { videoBase } of CASES) {
    it(`fixture file exists for ${videoBase}`, () => {
      const f = path.join(VIDEOS_DIR, videoBase, `${videoBase}_anchor_fixtures.json`)
      if (!fs.existsSync(f)) {
        throw new Error(
          `Missing ${path.relative(process.cwd(), f)}. ` +
          `It should have been committed via Task 10 of the implementation plan.`,
        )
      }
    })
  }
})
```

- [ ] **Step 2: Run the test against committed fixtures**

```bash
cd poses_viewer
npx vitest run src/drill/__tests__/anchorExtractor.cached.test.ts
```

Expected: all frames PASS (or SKIP with imageQuality reason). No FAIL.

If frames fail at this stage, it means the calibration round (Task 10) accepted scores below threshold for the seed frames. That's a calibration error, not a code bug — go back to Task 10 step 3 and reconsider thresholds vs. rubric wording.

- [ ] **Step 3: Verify the test catches regressions**

Temporarily mutate one frame's torsoScore in the fixture file from 8 → 6 and re-run the test:

```bash
# manually edit Videos/ivan_1/ivan_1_anchor_fixtures.json: drop torsoScore for frame 315 to 6
npx vitest run src/drill/__tests__/anchorExtractor.cached.test.ts
```

Expected: that one test FAILS with the threshold message including the reason string. Revert the edit.

This is the only "smoke test" available for the assertion logic itself, since real regressions require the upstream code to actually break.

- [ ] **Step 4: Commit**

```bash
git add poses_viewer/src/drill/__tests__/anchorExtractor.cached.test.ts
git commit -m "test(poses_viewer): pin mannequin pose against video via cached visual judge"
```

---

## Task 12: MannequinEditor cached-frame button

**Files:**
- Modify: `poses_viewer/src/components/MannequinEditor.tsx`

**Goal:** When a video has a cached fixture for the current frame, surface a button labeled "Apply cached extracted anchor (Claude scored: torso N/10, arm M/10)" that loads `cached.extractedAnchor` into the editor for visual re-inspection.

Read the file first to map the integration point — the button slots into `FrameSourcePanel` alongside the existing "Apply Start" / "Apply End" buttons.

- [ ] **Step 1: Read the editor scaffolding**

```bash
cd poses_viewer
grep -n "applyAnchor\|onApplyStart\|FrameSourcePanel" src/components/MannequinEditor.tsx
```

Expected: locate the `applyAnchor` helper, the `onApplyStart` / `onApplyEnd` callbacks, and the `FrameSourcePanel` props (around line 475 of MannequinEditor.tsx as of this writing).

- [ ] **Step 2: Add fixture loading state**

Inside `EditorShell` (the inner component), alongside the existing `frames` / `loadStatus` state, add:

```tsx
import type { AnchorFixture } from '../../scripts/anchorFixtures/schema'

const [fixture, setFixture] = useState<AnchorFixture | null>(null)

// Reload fixture when the selected video changes. 404 → no fixture (silent).
useEffect(() => {
  let cancelled = false
  setFixture(null)
  if (!selectedBase) return
  fetch(`/videos/${selectedBase}/${selectedBase}_anchor_fixtures.json`)
    .then(r => (r.ok ? r.json() : null))
    .then(j => { if (!cancelled && j) setFixture(j as AnchorFixture) })
    .catch(() => {})
  return () => { cancelled = true }
}, [selectedBase])
```

Note: vite already serves `Videos/<base>/<file>` at `/videos/<base>/<file>` (see [vite.config.ts:178](poses_viewer/vite.config.ts#L178)).

- [ ] **Step 3: Compute the current cached entry**

Add a memo that picks the cached entry for whichever frame is "current" (the editor uses `startIdx` for the editor's primary frame):

```tsx
const cachedForCurrent = useMemo(() => {
  if (!fixture) return null
  return fixture.frames[String(startIdx)] ?? null
}, [fixture, startIdx])
```

- [ ] **Step 4: Wire the button through `FrameSourcePanel`**

Add to the `FrameSourcePanel` call site:

```tsx
cachedAnchorEntry={cachedForCurrent}
onApplyCachedAnchor={
  cachedForCurrent
    ? () => applyAnchor(cachedForCurrent.extractedAnchor)
    : undefined
}
```

Then in the `FrameSourcePanel` component (same file, lower down — `FrameSourcePanel` interface near line 590), add the optional props:

```tsx
import type { AnchorFixtureFrame } from '../../scripts/anchorFixtures/schema'

interface FrameSourcePanelProps {
  // ... existing props
  cachedAnchorEntry?: AnchorFixtureFrame | null
  onApplyCachedAnchor?: () => void
}
```

And in the JSX of `FrameSourcePanel`, near the existing Apply Start / Apply End buttons, add:

```tsx
{cachedAnchorEntry && onApplyCachedAnchor && (
  <button
    type="button"
    className="bg-purple-700 hover:bg-purple-600 px-3 py-1.5 rounded text-sm"
    onClick={onApplyCachedAnchor}
    title={
      `Image quality: ${cachedAnchorEntry.imageQualityScore}/10 — ${cachedAnchorEntry.imageQualityReason}\n` +
      `Torso: ${cachedAnchorEntry.torsoScore}/10 — ${cachedAnchorEntry.torsoReason}\n` +
      `Right arm: ${cachedAnchorEntry.rightArmScore}/10 — ${cachedAnchorEntry.rightArmReason}\n` +
      `Judged by: ${cachedAnchorEntry.judgedBy} (${cachedAnchorEntry.generatedAt})`
    }
  >
    Apply cached extracted anchor
    {' '}
    (torso {cachedAnchorEntry.torsoScore}/10, arm {cachedAnchorEntry.rightArmScore}/10)
  </button>
)}
```

- [ ] **Step 5: Manual smoke test**

```bash
cd poses_viewer
npm run dev
```

Open `http://localhost:5780/#/mannequin`. Pick `ivan_1` from the video dropdown. Set startIdx to 315 (one of the cached frames). Expected: a purple "Apply cached extracted anchor (torso N/10, arm M/10)" button appears. Hovering shows the tooltip with all three reasons. Clicking loads the cached anchor — the mannequin should match exactly what Claude saw at prep time.

Switch to a frame index that isn't cached (e.g. 100). Expected: button disappears.

- [ ] **Step 6: Commit**

```bash
git add poses_viewer/src/components/MannequinEditor.tsx
git commit -m "feat(poses_viewer): show cached extracted anchor in MannequinEditor"
```

---

## Task 13: Top-up the test cases (optional, post-merge)

**Files:**
- Modify: `poses_viewer/src/drill/__tests__/anchorExtractor.cached.test.ts` (extend `CASES`)

This is the steady-state workflow once the system is built. Adding a video to the test suite is:
1. `npm run prepare-anchor-composites -- --video <new> --frames <range>`
2. In-session: judge each composite per RUBRIC.md, run `write-anchor-fixture-entry`.
3. Add `{ videoBase: '<new>', frameRange: [...] }` to `CASES`.
4. Commit fixture + test change together.

No code change needed beyond the `CASES` array. Document this in the test file's header comment if it isn't already obvious.

(This task isn't required for the initial implementation — it's here so future contributors find the recipe.)

---

## Self-review

**Spec coverage:**

| Spec section | Plan task |
|---|---|
| § Goal: pin landmarks→FK→render against video | Tasks 5 (render), 7 (composite), 11 (assert) |
| § Architecture (PREP TIME / TEST TIME / EDITOR TIME) | 8, 11, 12 |
| § File layout: `scripts/anchorFixtures/*` + `MannequinRenderRoute` + test | 1, 2, 3, 4, 5, 6, 7, 8, 9, 11 |
| § File layout: `Videos/<base>/_oracle_inputs/` (gitignored) + fixture file (committed) | 1 (gitignore exception), 8 (writes), 10 (commits fixture) |
| § Cache file schema + version constants | 1 |
| § Render step (#/render route, orthographic, transparent) | 5 |
| § 2-point similarity transform | 7 |
| § Judging rubric (RUBRIC.md) | 2 |
| § Threshold (7) + skip threshold (5) | 1 (constants), 10 (calibration), 11 (asserts) |
| § Test (anchorExtractor.cached.test.ts) | 11 |
| § CLI 1: prepare composites | 4, 5, 6, 7, 8 |
| § CLI 2: write fixture entry | 9 |
| § Mannequin editor change | 12 |
| § Workflow (concrete user interaction) | 10 (calibration is the first run of this workflow), 13 (steady state) |
| § Calibration round (pre-commit step) | 10 |
| § Out of scope | not implemented (correct) |
| § Risks & mitigations | atomic write Task 9; gitignore Task 1; readiness flag Task 5; readiness timeout Task 6; bulk chunking Task 10 (manual) |

No spec section is unaddressed.

**Placeholder scan:** searched the plan for `TBD`, `TODO`, `implement later`, `similar to Task`, `add appropriate`, `handle edge cases`, `write tests for the above`. None found in step content. The only "TODO"-ish moment is Task 5 step 2's note that `Drill2Mannequin`'s `backgroundTransparent` / `orthographic` / `viewBox` props may need to be added — which is explicit instruction to read the existing file and either reuse existing prop names or add new ones, with concrete acceptance criteria (smoke test in step 4).

**Type consistency check:**
- `AnchorFixture`, `AnchorFixtureFrame`, `PoseAnchor`, `Point2`, `SimilarityTransform`, `WriteEntryInput`, `MetaFile` — same names used across all tasks. ✅
- `loadAnchorFixture(videoBase, videosDir)` signature — matches in Task 1, 9, 11. ✅
- `writeFixtureEntry({...})` arg shape — matches in Task 9 implementation, Task 9 test, and the CLI dispatch block. ✅
- `extractAnchorFromLandmarks` and `parsePoseFixture` — imported from real source paths verified during pre-flight (`src/drill/anchorExtractor.ts` re-exports `parsePoseFixture`). ✅
- `LM.L_HIP` / `LM.R_HIP` / `LM.L_SHOULDER` / `LM.R_SHOULDER` — from `src/drill/SkeletonModel`, verified during pre-flight (used by `extractBoneLengths`). ✅
- Threshold constants `PASS_THRESHOLD` / `QUALITY_SKIP_THRESHOLD` — defined in Task 1, used in Task 11. ✅
- Score-mode skip in vitest 4: uses `ctx.skip(reason)` (the `it` callback's TestContext), which matches the spec's "skip uses vitest's `skip()` from the test context". ✅

No drift detected. Plan is internally consistent.
