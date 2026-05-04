
# Visual-judge regression test for the mannequin pose pipeline

**Status:** Design, awaiting user review
**Date:** 2026-04-30
**Scope:** poses_viewer (the React + Vite debug tool)
**Author:** Ivan + Claude (brainstorming session)

## Goal

Pin the **end-to-end mannequin pose pipeline** — `landmarks → extractAnchorFromLandmarks → reconstructFromAnchor → rendered figure` — against the original video frame so future code changes that make the rendered mannequin diverge from the player's actual pose fail loudly.

The oracle is Claude (multimodal), running **in-session via Claude Code**, not as a programmatic API call. The user asks Claude to prep a video + frame range; Claude runs a deterministic CLI that produces composite images (video frame + mannequin overlay), reads each composite via the Read tool, judges the match, and writes the cached JSON via the Write tool. The vitest test then reads from cache; it never invokes Claude or any AI.

**What this design replaces.** An earlier draft of this spec asked Claude to estimate 10 anchor angles per frame and asserted numerical agreement against the extractor. That approach has structural issues: it can't catch FK bugs, coordinate-frame bugs, or convention drifts (where extractor and oracle silently shift together); the extractor source ended up in the prompt as "reference," compromising oracle independence; angle tolerances don't map cleanly to "does the figure look right." The visual-judge approach addresses all three.

**Why in-session and not API.** No `ANTHROPIC_API_KEY` is required anywhere. No per-frame API cost. The "calibration round" becomes interactive — the user can correct or re-judge frames in the same conversation. The price: no batch automation; prep is a workflow ("ask Claude to prep these frames"), not a single shell command. Acceptable since this is a personal project.

**Scope discipline:**

- **In scope:** torso + right arm visual fidelity. Two scores per frame (torso, right arm).
- **Out of scope:** left arm, legs, ankles. Claude scores them implicitly when judging "torso" or "right arm" (e.g. shoulder anchor depends on torso) but not as separate failure signals.
- **In scope:** prep CLI to populate cache; vitest assertion test; editor button to inspect cached frames.
- **Out of scope:** Claude in CI; auto-update on mismatch; per-joint flags from Claude.

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│   PREP TIME (interactive — user asks Claude in this console)         │
│                                                                      │
│   USER:    "prep frames 315-320 for ivan_1"                          │
│                                                                      │
│   CLAUDE:                                                            │
│     1. Run CLI: npm run prepare-anchor-composites --                 │
│                  --video ivan_1 --frames 315-320                     │
│        The CLI does the deterministic prep:                          │
│          a. ffmpeg → frame_N.jpg                                     │
│          b. read landmarks_N from <base>_poses.json                  │
│          c. extractAnchorFromLandmarks(landmarks_N) → anchor         │
│          d. Playwright drives #/render route:                        │
│               mount Drill2Mannequin → screenshot transparent PNG     │
│          e. composite mannequin PNG over frame_N.jpg using           │
│             2-point similarity (hipMid + shoulderMid)                │
│          f. write Videos/<base>/_oracle_inputs/frame_N.png           │
│             + Videos/<base>/_oracle_inputs/frame_N.meta.json         │
│             (the meta holds: extractedAnchor, hipMid, shoulderMid,   │
│              composite dims — everything needed to write the         │
│              fixture entry without re-running the pipeline)          │
│                                                                      │
│     2. For each composite:                                           │
│          a. Read tool → load frame_N.png                             │
│          b. judge (visually, in-context) torso, rightArm,            │
│             imageQuality + reasons                                   │
│          c. Write tool → append entry to                             │
│             Videos/<base>/<base>_anchor_fixtures.json                │
│                                                                      │
│     3. Report summary back to user.                                  │
└──────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
   Videos/<base>/<base>_anchor_fixtures.json   (committed to git)
                                │
                ┌───────────────┴────────────────┐
                ▼                                ▼
┌───────────────────────────┐   ┌─────────────────────────────────┐
│   TEST TIME (npm test)    │   │   EDITOR TIME (npm run dev)     │
│                           │   │                                 │
│   anchorExtractor         │   │   MannequinEditor.tsx           │
│     .cached.test.ts       │   │     "Apply extracted anchor for │
│       loadAnchorFixture() │   │      cached frame N" button     │
│       assert torso ≥ 7    │   │     (lets user visually re-     │
│       assert rightArm ≥ 7 │   │      inspect what Claude saw)   │
│       skip if quality < 5 │   │                                 │
│                           │   │                                 │
│   No network. No AI.      │   │   Visual debug only.            │
└───────────────────────────┘   └─────────────────────────────────┘
```

## File layout

```
poses_viewer/
  scripts/
    anchorFixtures/
      prepareComposites.ts  # CLI entry point — deterministic prep ONLY,
                            # produces composites + meta. No AI calls.
      extractFrames.ts      # ffmpeg wrapper (frame N → JPG)
      renderMannequin.ts    # Playwright wrapper: drives #/render, returns PNG
      composite.ts          # 2-point similarity + alpha-blend (sharp)
      writeFixtureEntry.ts  # CLI entry point — given a frame's meta + scores,
                            # appends/updates one entry in the fixture file
                            # atomically. Invoked by Claude after judging.
      schema.ts             # AnchorFixture types, thresholds, loadAnchorFixture()
  src/
    components/
      MannequinEditor.tsx              # patched: + cached-frame button
      MannequinRenderRoute.tsx         # NEW: standalone render route for Playwright
    App.tsx                            # patched: route #/render
  src/drill/__tests__/
    anchorExtractor.cached.test.ts     # the assertion test

Videos/<base>/
  <base>_anchor_fixtures.json          # the cache, committed to git
  _oracle_inputs/                      # gitignored — composites + meta for
                                       # in-session judging
    frame_315.png
    frame_315.meta.json
    ...
```

**Two CLI entry points, no AI in either:**

- `prepare-anchor-composites` — produces images Claude reads.
- `write-anchor-fixture-entry` — applies Claude's judgments to the cache.

This split is deliberate. Claude's "judging" lives in the conversation transcript, not in a script. The CLIs do only deterministic work; the assistant does the visual judgment via Read tool and applies it via the write CLI (or Write tool directly — see "Workflow" below).

The `#/render` route is a minimal mount of `Drill2Mannequin` with a transparent canvas, a fixed orthographic camera, and a `window.__mannequinReady` signal Playwright awaits before screenshotting.

## New dependencies

- `tsx` — devDependency. Runs the TS prep CLIs.
- `playwright` — devDependency. Drives a headless Chromium for the render step.
- `sharp` — devDependency. Image compositing (paste mannequin PNG over frame JPG with 2-point similarity transform).
- `ffmpeg` — system binary.

**No `@anthropic-ai/sdk`.** Judging happens in-session, not via SDK.

`package.json` scripts:

```json
"prepare-anchor-composites":   "tsx scripts/anchorFixtures/prepareComposites.ts",
"write-anchor-fixture-entry":  "tsx scripts/anchorFixtures/writeFixtureEntry.ts"
```

A `postinstall` for `playwright install chromium` is **not** added — running it lazily on first prep invocation is enough, and it would slow `npm install` for everyone (test runners, the editor itself) for a tool only the prep flow needs.

## Cache file schema

Path: `Videos/<base>/<base>_anchor_fixtures.json`

```jsonc
{
  "videoBase": "ivan_1",
  "schemaVersion": 1,
  "rubricVersion": 1,
  "rendererVersion": 1,
  "frames": {
    "315": {
      "generatedAt": "2026-04-30T14:22:11Z",
      "judgedBy": "claude-opus-4-7",
      "imageQualityScore": 8,
      "imageQualityReason": "Player fully visible, paddle clear of body.",
      "torsoScore": 9,
      "torsoReason": "Tilt and yaw match closely; shoulders aligned.",
      "rightArmScore": 6,
      "rightArmReason": "Elbow position drifts ~10cm forward of player's elbow.",
      "extractedAnchor": { "torsoTiltDeg": 24.5, "...": "all 50 fields" }
    }
  }
}
```

**Field rules:**

- `schemaVersion` — bumps on file-format changes. Loader errors on unknown.
- `rubricVersion` — bumps when the scoring rubric changes (the instructions Claude is given for what 7 vs 8 means, what counts as "image quality < 5"). Recorded per file; prep CLI refuses to mix entries from different rubric versions in the same file unless `--refresh`.
- `rendererVersion` — bumps on `Drill2Mannequin` visual changes (color, line thickness, scale, render route mount logic). Same per-file mixing rule.
- `judgedBy` — recorded **per frame**, not per file. The model/session that judged this specific entry. Set by `write-anchor-fixture-entry` from a `--judged-by` CLI flag (Claude fills this in based on its own model identity at judging time). This is diagnostic, not a gate — frames judged by different model snapshots can coexist in one file. We accept that mixing is possible; the alternative ("refuse to mix") would mean re-judging an entire file every time the model rolls forward, which is heavy for marginal gain.
- `frames` — string-keyed by frame index (JSON limitation); loader parses to numbers.
- `imageQualityScore`, `imageQualityReason` — Claude's read on whether the input frame was usable. Score < 5 → test skips (not fails) the frame.
- `torsoScore`, `rightArmScore` — integers 0–10. Threshold `>= 7` to pass.
- `*Reason` — Claude's one-sentence justification. Always surfaced in failure messages.
- `extractedAnchor` — the full anchor that produced the rendered mannequin Claude scored. **Stored verbatim**, not for assertion. It enables the editor button: "show me exactly the figure Claude saw." Without it, the editor would re-extract the anchor at view time, and any later extractor change would silently make the editor disagree with the cached score.

```ts
// scripts/anchorFixtures/schema.ts

export const PASS_THRESHOLD = 7
export const QUALITY_SKIP_THRESHOLD = 5  // strict less-than

export interface AnchorFixture {
  videoBase: string
  schemaVersion: 1
  rubricVersion: number
  rendererVersion: number
  frames: Record<string, AnchorFixtureFrame>
}

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
```

## The render step

Headless render of the mannequin alone, transparent background, orthographic projection in MediaPipe coordinate space.

**Why this works without per-video calibration.** The viewer's `CLAUDE.md` documents that the three.js mannequin coordinate system matches MediaPipe landmark axes (x = right, y = down, z = away). Both spaces are normalized to `[0,1]` in the X-Y plane. So an orthographic camera that maps three.js `(x, y) ∈ [0,1]` to image pixels `(x * W, y * H)` projects the mannequin into the same coordinate system as the video frame's pose landmarks — without knowing anything about the real camera that shot the video.

**The 2-point similarity transform.** Drill2Mannequin renders the figure at a fixed scale (Drillis & Contini, ~1.7m) at a fixed pelvis position. The player in the video may be at a different image-space position and scale. We apply a 2D similarity transform (translate + uniform scale + rotation = 4 DoF, but we constrain to translate + uniform scale = 3 DoF) that maps the mannequin's `hipMid → player's hipMid` landmark and `shoulderMid → player's shoulderMid` landmark.

This is a 2-point fit — geometrically it's underdetermined for a full similarity (which needs scale + rotation + translation = 4 DoF and 2 corresponding points pin all 4). We deliberately fix rotation = 0 because the mannequin is already upright in landmark space; rotating it to match the video would mask exactly the kind of "torso tilt" bug we're trying to catch.

**The `#/render` route** (new file `MannequinRenderRoute.tsx`):

```tsx
// query params: ?anchor=<base64 json>&width=<W>&height=<H>
// On mount:
//   1. parse anchor from URL
//   2. render Drill2Mannequin with:
//        - the anchor
//        - transparent canvas background
//        - orthographic camera covering [0,1] x-y range
//        - lighting/colors tuned for overlay legibility (semi-
//          transparent green skeleton over arbitrary backgrounds)
//   3. on first RAF after layout: window.__mannequinReady = true
```

Playwright launches headless Chromium, navigates to `http://localhost:5780/#/render?anchor=...`, awaits `window.__mannequinReady`, screenshots the canvas, returns the PNG bytes. Dev server is started by the prep script (or assumed running — script auto-detects port 5780; spawns its own if not listening).

**Composite step (`composite.ts`)** uses `sharp`:

```ts
// Inputs: frameJpgBytes, mannequinPngBytes, transformParams
// 1. compute similarity matrix from hipMid + shoulderMid pairs
// 2. apply matrix to mannequin PNG via sharp.affine()
// 3. composite over frame
// 4. return composite PNG bytes
```

**Renderer version bumps when:** mannequin colors change, line thickness changes, the scale changes, the orthographic frustum changes, or the `#/render` route's mount logic changes. NOT when `Drill2Mannequin`'s pose math changes — that's what we're testing, so a rendering-pipeline change reflects new pose math and the cache should be regenerated by `--refresh`, not silently invalidated.

## The judging rubric

Since judging happens in-session (no API call), there's no "prompt" in the SDK sense. Instead there's a **rubric**: a set of instructions the assistant follows when the user asks it to judge a frame. The rubric lives in the repository as `scripts/anchorFixtures/RUBRIC.md` so its evolution is reviewable and `rubricVersion` can bump on real changes.

**Workflow when judging a frame:**

```
1. Read tool: load Videos/<base>/_oracle_inputs/frame_N.png
2. Read tool: load Videos/<base>/_oracle_inputs/frame_N.meta.json
3. Apply the rubric (below) to the composite. Decide:
     imageQualityScore + reason
     torsoScore + reason
     rightArmScore + reason
4. Run write-anchor-fixture-entry CLI with the scores +
   --judged-by <my model identity>.
   (Or write to the fixture JSON directly via Write tool — the CLI
   exists so the file write is atomic and schema-validated.)
```

**Rubric content** (`scripts/anchorFixtures/RUBRIC.md`):

```
You are judging how well a reconstructed mannequin matches a player's
pose in a video frame.

The image you read is a composite: the video frame, with our
reconstructed mannequin drawn on top in semi-transparent green. Your job
is to score the match.

WHAT TO SCORE

Score these three things, all integers 0–10:

  imageQualityScore   How usable is this frame for comparison?
                      10 = player fully visible, no occlusion, mannequin
                           anchored sensibly (overlapping the player's
                           body, not floating off in space).
                      5  = significant occlusion or mannequin clearly
                           anchored to the wrong place.
                      0  = unusable: player off-frame, or mannequin
                           drawn somewhere unrelated to the player.

                      A LOW SCORE HERE IS NOT THE MANNEQUIN'S FAULT.
                      It means the input data was bad. We will skip
                      this frame, not blame the pose extractor.

  torsoScore          How well does the mannequin's torso (spine,
                      shoulders, hips) match the player's?
                      10 = visually indistinguishable.
                      7  = matches in all major axes; minor lean or yaw
                           difference visible only on close inspection.
                      5  = clear mismatch in one axis (e.g. tilt off
                           by ~15°, yaw clearly wrong direction).
                      0  = bears no resemblance.

  rightArmScore       Same scale, applied to the right arm (shoulder
                      → elbow → wrist → hand).

For each of the three: write a one-sentence reason. Be specific about
WHAT differs, not whether it's good or bad.

  Good reason:  "Elbow position drifts ~15cm forward of player's elbow."
  Bad reason:   "Right arm doesn't match well."

DO NOT score left arm or legs. The mannequin's left-arm/leg positions
in this rendering may not reflect the actual extraction output and
should be ignored.

When done, invoke write-anchor-fixture-entry with the scores.
```

**Things to call out:**

1. **The rubric is a tracked file, not implicit knowledge.** Future sessions read the same rubric. If you tweak the wording, bump `rubricVersion` so old entries can be flagged for re-judging.

2. **No reference appendix.** The judge needs zero context about how the mannequin was computed — independence from the implementation by construction.

3. **The "ignore left arm and legs" instruction is load-bearing.** Without it the assistant will dock points for "the left arm doesn't match" — true, but those fields are zeroed in extraction (single-camera ambiguity) and the mannequin will render them at midpoint. We don't care.

4. **Three-region scoring with skip-on-quality.** A frame where the assistant scores `imageQualityScore: 3` skips the test, doesn't fail. Otherwise low-vis frames become permanent extractor-regression alarms.

5. **No few-shot examples.** Could be added later via rubric bump if scoring is inconsistent across sessions.

## The threshold

```
torsoScore    >= 7  AND  rightArmScore >= 7    → pass
either        <  7                              → fail
imageQualityScore < 5                           → skip (don't blame extractor)
```

**Why 7:** 8+ is rare for hard inverse problems even when the figure looks right (Claude tends to be cautious); 6 gives the extractor too much rope (visible misalignment). 7 is "I can see the match; small deviations tolerated."

**Why same threshold for torso and right arm:** the right arm is the technique-critical region (forehand stroke). Lower threshold there is exactly the wrong instinct — that's where we most want the alarm to fire.

These are subject to a **calibration round** (see below) before being committed.

## Test

`src/drill/__tests__/anchorExtractor.cached.test.ts`

```ts
import { describe, it } from 'vitest'
import { parsePoseFixture } from '../anchorExtractor'
import {
  loadAnchorFixture, PASS_THRESHOLD, QUALITY_SKIP_THRESHOLD,
} from '../../../scripts/anchorFixtures/schema'
import fs from 'node:fs'
import path from 'node:path'

interface TestCase { videoBase: string; frameRange: [number, number] }

const CASES: TestCase[] = [
  { videoBase: 'ivan_1',   frameRange: [315, 320] },
  { videoBase: 'andrii_1', frameRange: [57, 63]   },
]

const VIDEOS_DIR = path.resolve(__dirname, '../../../../Videos')

for (const { videoBase, frameRange } of CASES) {
  describe(`mannequin pose vs video — ${videoBase} ${frameRange[0]}-${frameRange[1]}`, () => {
    const fixture = loadAnchorFixture(videoBase, VIDEOS_DIR)

    for (let i = frameRange[0]; i <= frameRange[1]; i++) {
      it(`frame ${i}`, ({ skip }) => {
        const cached = fixture.frames[String(i)]
        if (!cached) {
          throw new Error(
            `No cached oracle for ${videoBase} frame ${i}. ` +
            `Run: npm run prepare-anchor-fixtures -- --video ${videoBase} --frames ${i}`
          )
        }

        if (cached.imageQualityScore < QUALITY_SKIP_THRESHOLD) {
          skip(`Image quality ${cached.imageQualityScore}/10: ${cached.imageQualityReason}`)
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
            `Inspect: open http://localhost:5780/#/mannequin, ` +
            `load video=${videoBase}, frame=${i}, ` +
            `click "Apply cached extracted anchor"`
          )
        }
      })
    }
  })
}
```

**Design choices:**

- **Both region failures reported in one error.** Same as before — fix-rerun cycles are slow.
- **Skip uses vitest's `skip()` from the test context** so the suite output shows skipped tests with their reason.
- **Inspection hint links to the editor.** The editor button (next section) loads the same anchor Claude saw, so the user can re-render the comparison and look at it.
- **No baseline-update mode.** Mismatches are bugs, not drift. Updating cache is explicit `prepare --refresh`.
- **Hardcoded CASES.** Adding videos is an explicit code change with PR review.

## CLI 1: prepare composites

```
npm run prepare-anchor-composites -- --video <base> --frames <range> [--force] [--dry-run]
```

Deterministic image prep. Produces composites for the assistant to read. Never calls AI.

**Args:**

- `--video <base>` *(required)* — folder under `Videos/`. Auto-detects `.<ext>` by globbing.
- `--frames <range>` *(required)* — formats: `315`, `315-320`, `315,318,320`, `315-320,400-405`.
- `--force` — re-render composites even if `_oracle_inputs/frame_N.png` already exists.
- `--dry-run` — print plan, do nothing.

**Behavior:**

1. **Validate** video file + poses file. Bail on missing.
2. **Determine frames to process.** Skip frames whose composite already exists in `_oracle_inputs/` unless `--force`. (Independent of fixture-file state — composites are reproducible artifacts.)
3. **Start dev server** if port 5780 isn't responding. Tear down on exit.
4. **Launch Playwright** (headless Chromium). One browser, one page reused across frames.
5. **Per frame:**
   - Extract image: `ffmpeg -i Videos/<base>/<base>.<ext> -vf "select=eq(n\,${i})" -vframes 1 -q:v 2 <tmp>/frame_${i}.jpg`. Validate non-empty output.
   - Slice landmarks from `<base>_poses.json`. Skip frame on missing.
   - Run `extractAnchorFromLandmarks(landmarks)` directly (KMP-portable JS).
   - Navigate Playwright to `#/render?anchor=<base64>&width=<videoW>&height=<videoH>`, await `window.__mannequinReady`, screenshot canvas as PNG.
   - Compute 2-point similarity from MediaPipe `hipMid` + `shoulderMid` (mean of L/R landmarks). Composite mannequin PNG over frame JPG via sharp.
   - Write `Videos/<base>/_oracle_inputs/frame_${i}.png` (composite).
   - Write `Videos/<base>/_oracle_inputs/frame_${i}.meta.json` containing: `extractedAnchor`, `hipMid`, `shoulderMid`, composite dimensions, source video path, source frame index. **The assistant reads this file when writing the fixture entry** — it carries everything needed to populate the cache without re-running the pipeline.
6. **Cleanup** tmp JPGs unless `KEEP_FRAMES=1`. Composites + meta stay (gitignored).
7. **Summary:** *"Wrote N composites. Path: Videos/<base>/_oracle_inputs/. Ask Claude to judge them."*

## CLI 2: write fixture entry

```
npm run write-anchor-fixture-entry -- \
  --video <base> --frame <N> \
  --image-quality-score <0-10> --image-quality-reason "..." \
  --torso-score <0-10> --torso-reason "..." \
  --right-arm-score <0-10> --right-arm-reason "..." \
  --judged-by <model-name>
```

Atomic upsert of one frame's entry into the fixture file. Never calls AI.

**Behavior:**

1. **Validate args.** All scores are integers 0–10. Reasons are non-empty strings.
2. **Load** `Videos/<base>/_oracle_inputs/frame_<N>.meta.json`. Bail if missing — means `prepare-anchor-composites` wasn't run for this frame.
3. **Load existing fixture** if present. Verify `schemaVersion`, `rubricVersion`, `rendererVersion` match the current values declared in `schema.ts`. On mismatch: bail with instructions to refresh.
4. **Build entry** from the meta + scores: `extractedAnchor` from meta, scores + reasons + `judgedBy` + `generatedAt` from args + now.
5. **Upsert** entry into `frames[String(N)]`. Replaces any existing entry for that frame.
6. **Write atomically:** `<base>_anchor_fixtures.json.tmp` → fsync → rename. Ctrl-C-safe.
7. **Print** the diff (was vs. now) for transparency.

**Why a CLI and not just Write tool:** the assistant *can* update the fixture JSON directly via Write tool, but doing so requires re-reading and re-serializing the whole file. The CLI exists to (a) guarantee atomicity, (b) validate scores are in range, (c) keep the schema version checks in one place. The assistant should prefer the CLI; falling back to Write tool is acceptable when the CLI fails for some reason.

**Error modes:**

| Condition | Action |
|---|---|
| `ffmpeg` not on PATH | `prepare-anchor-composites` bails. |
| Playwright Chromium not installed | Auto-install on first run; bail if install fails. |
| Frame index ≥ video length | Skip with warning. |
| Landmarks missing for frame | Skip with warning. |
| `window.__mannequinReady` never fires | Skip frame after 5s timeout, log. |
| `write-anchor-fixture-entry` invoked without prior composite prep | Bail with instruction to run `prepare-anchor-composites` first. |
| `rubricVersion`/`rendererVersion` mismatch on existing fixture file | `write-anchor-fixture-entry` bails; user must refresh or accept lossy migration. |

## Mannequin editor change

`MannequinEditor.tsx`: when a video has a cached fixture file, replace the existing "Apply extracted anchor" button's behavior for cached frames so it loads the **stored** `extractedAnchor` from the fixture (not the just-computed one).

**Why this and not the previous spec's "Apply Claude anchor" button:** there is no Claude anchor anymore. The cache stores Claude's *score* of a *rendered figure*. The figure was rendered from the extractor's output at prep time. To inspect what Claude saw, the editor needs to render the same anchor that was rendered then — which is `cached.extractedAnchor`.

**Behavior:**

- On `selectedBase` change: fetch `Videos/<base>/<base>_anchor_fixtures.json`. 404 → no fixtures.
- When a cached fixture exists for the current frame: button label becomes "Apply cached extracted anchor (Claude scored: torso N/10, arm M/10)". Tooltip shows the reason strings.
- On click: `setAnchor(cached.extractedAnchor)`. Full anchor (all 50 fields), not 10.
- Without cached fixture: button behavior unchanged from today.

**No new files** — patch `MannequinEditor.tsx` (~40 lines), reuse `loadAnchorFixture`.

## Workflow (concrete user interaction)

Once the infrastructure is built, prepping a video looks like:

```
USER:    Prep frames 315-320 for ivan_1.

CLAUDE:  [Bash] npm run prepare-anchor-composites --
         --video ivan_1 --frames 315-320
         → Wrote 6 composites to Videos/ivan_1/_oracle_inputs/.

         [Read] Videos/ivan_1/_oracle_inputs/frame_315.png
         [Read] Videos/ivan_1/_oracle_inputs/frame_315.meta.json
         <judges according to RUBRIC.md>
         [Bash] npm run write-anchor-fixture-entry --
         --video ivan_1 --frame 315
         --image-quality-score 9 --image-quality-reason "..."
         --torso-score 8 --torso-reason "..."
         --right-arm-score 7 --right-arm-reason "..."
         --judged-by claude-opus-4-7

         <repeats for 316, 317, ...>

         Done. 6 frames judged: 5 pass (≥7/7), 1 borderline
         (right-arm 6/10, see notes). Fixture file:
         Videos/ivan_1/ivan_1_anchor_fixtures.json.

USER:    Frame 318's right-arm score looks too low. Look again.

CLAUDE:  [Read] Videos/ivan_1/_oracle_inputs/frame_318.png
         <re-judges>
         [Bash] write-anchor-fixture-entry ... # overwrites
```

The interactive disagreement loop (the user pushing back on a score) is exactly why we picked in-session over API.

## Calibration round (pre-commit step)

The threshold of 7 and skip-at-5 are guesses. Before committing them:

1. Cache ~20 frames across 2 videos (one prep session, in-conversation).
2. Look at the score distribution for torso, rightArm, imageQuality. What's typical? What's the spread?
3. Spot-check 3–4 frames manually: open the composite image, eyeball it, agree/disagree with the judgment.
4. If scores are consistently 6 on figures that look fine → either bump threshold to 6 or refine `RUBRIC.md`'s 6-vs-7 distinction (and bump `rubricVersion`).
5. If imageQuality skips are wildly over- or under-firing, adjust the skip threshold.
6. Commit thresholds + any rubric revisions.

**Why this matters more than for the numeric design:** the visual judge's noise floor is harder to reason about a priori. We're trusting that scoring is roughly self-consistent across sessions; the calibration round verifies it.

## Out of scope (explicit non-goals)

- **Claude in CI.** Tests are hermetic; CI runs cache-only.
- **Auto-update on mismatch.** No `UPDATE_FIXTURES=1` env. Mismatches are bugs.
- **Per-joint failure flags from Claude.** Reason strings are the localization story.
- **Comparing mannequins across frames.** Each frame stands alone.
- **Left arm / legs scoring.** Out of scope.
- **Animated/temporal scoring** ("does the mannequin track a motion smoothly"). Frame-by-frame only.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Different Claude sessions score the same composite differently. | Cache freezes the score on first judgment. `judgedBy` per-frame surfaces drift if one frame shows up with a different model than its neighbors. Calibration round verifies session-to-session noise is small enough not to swamp the threshold. |
| The user (or assistant) is tempted to revise scores cosmetically when a test fails. | Test failures should fix the extractor or refresh the fixture, not edit scores. The fixture file is committed — diffs to scores are visible in PR review. |
| The 2-point hipMid/shoulderMid alignment fails when MediaPipe gets those landmarks wrong. | Such frames score low on `imageQualityScore` and skip. We accept correlated failure here — if MediaPipe is broken on a frame, the whole pipeline is broken, not just our part. |
| Renderer version drift is hard to detect. | `rendererVersion` field. Code reviewer bumps it on `Drill2Mannequin` visual changes. The editor button gives a quick visual sanity check when a regression seems suspicious. |
| ffmpeg version differences cause off-by-one frame extraction. | Use `select=eq(n,N)` (frame index, not time). Robust across versions. Verify on first prep. |
| Playwright Chromium adds ~200MB of devDeps. | Accepted. The alternative (headless three.js in Node via `gl` binding) is fragile across platforms. |
| Dev server startup races the first frame's Playwright navigation. | Prep CLI polls port 5780 until HTTP 200 before launching Playwright. |
| Composite directory `_oracle_inputs/` accidentally gets committed. | Add to `.gitignore` as part of the implementation. Composites are reproducible from the video + fixture entry; no need to ship them. |
| Bulk prep (e.g. 100 frames) hits assistant context limits during judging. | The two-CLI design naturally chunks: composites can all be prepared in one shot, then judging happens in batches sized to fit context. The assistant is responsible for chunking and writing entries incrementally so each batch survives context compression. |

## Implementation order

1. `schema.ts` — types, thresholds, `loadAnchorFixture`, version constants.
2. `RUBRIC.md` — the in-session scoring instructions.
3. `MannequinRenderRoute.tsx` + route wiring in `App.tsx` — manually verify in browser at `#/render?anchor=...`.
4. `extractFrames.ts` — ffmpeg wrapper + smoke test.
5. `renderMannequin.ts` — Playwright wrapper, returns PNG.
6. `composite.ts` — 2-point similarity + sharp compositing. Smoke-test by writing a few composites to disk and eyeballing.
7. `prepareComposites.ts` — CLI plumbing.
8. `writeFixtureEntry.ts` — CLI plumbing.
9. **Calibration round** — cache ~20 frames, eyeball composites + scores, adjust threshold and/or rubric, commit.
10. `anchorExtractor.cached.test.ts` — the test, with the two seed cases.
11. `MannequinEditor.tsx` patch — cached-frame button.
