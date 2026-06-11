# L-28 Fix: Forward-Stroke Direction via Peak-Approach Displacement — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix `ForwardStrokeFilter` so true forward drives are not dropped on continuous play: measure stroke direction as the wrist x-displacement over the ~100 ms **approach into the peak** instead of startFrame→peakFrame.

**Architecture:** Kotlin-first per the binding fix-flow rule (spec `docs/superpowers/specs/2026-06-11-drill-effectiveness-sim-design.md`): the fix lands in `shared/` Kotlin with tests, then is mirrored 1:1 in the TS harness (`poses_viewer/src/drill2d/`), and the goldens are updated in **both** suites in the same plan. A new real-footage fixture (`video_4_rtm.json`, shadow play, 12 visually-verified right-hand drives) becomes a stage-level golden alongside andrii_1.

**Tech Stack:** Kotlin 2.1 KMP (`shared/`, zero external deps), kotlin.test; TypeScript + vitest 4 (`poses_viewer/`).

---

## Background (read before Task 1)

**The bug (L-28, `docs/DESIGN_LIMITATIONS.md`):** `ForwardStrokeFilter.wristDx` measures wrist x-displacement startFrame→peakFrame. On continuous shadow play the smoothed wrist speed never falls below the detector's 0.3 boundary floor between swings, so a stroke's `startFrame` bleeds back into the *previous* follow-through, where the wrist x is already forward. Result on `video_4` (12 drives, right hand verified visually frame-by-frame): 7 of 12 true drives measure dx ≤ 0 and are dropped → 4 reps instead of 12 forward.

**Diagnosis facts (TS prototype, 2026-06-11):** detection itself is sound — all 12 forward-motion runs contain a raw detector peak; only the direction read is wrong. The fix below restores 12/12 forward classification.

**The fix:** direction = `x[peak] − x[approach]`, where `approach` is the latest frame at least 100 ms before the peak (walk back by timestamps), clamped to `startFrame`. The drive accelerates *into* the peak, so the approach direction is the stroke direction regardless of where the start boundary landed. Window walk uses `PoseFrame2D.timestampMs` (no `intervalMs` parameter needed, no signature change).

**Validated prototype numbers (TS float64, yaw 0, handedness right):**

| Fixture | Before (raw/fwd/reps) | After | Ground truth |
|---|---|---|---|
| andrii_1_rtm | 23 / 15 / 15 (golden) | **23 / 15 / 15 — unchanged** | 15 |
| video_4_rtm | 18 / 4 / 4 | **18 / 12 / 9** | 12 drives |
| video_2_rtm | 8 / 3 / 3 | 8 / 5 / 4 | not counted |

Kotlin uses Float32; counts are expected to match exactly. **If a Kotlin count differs from this table, STOP — do not pin different numbers and do not tune thresholds.** Print per-stroke `{startFrame, peakFrame, endFrame, peakSpeed, dx}` from Kotlin, compare against ground truth (video_4 = 12 forward drives), and report. Kotlin output is the source of truth for the TS goldens in Task 4.

**Existing tests are safe by design:** all 8 `ForwardStrokeFilterTest` scenarios use 100 ms frame spacing, where the approach window covers exactly 1 frame; for the `session()` tent shapes (`0.5 → 0.5±0.1 → 0.5` with peak at the middle frame) `x[peak] − x[peak−1]` equals the old start→peak dx, and for the two boundary-clean stroke tests the approach sign matches the swing direction. No existing test changes.

---

## File structure

```
shared/src/commonTest/resources/fixtures/video_4_rtm.json     # NEW — copy of Videos/video_4/video_4_poses_rtm.json
shared/src/jvmTest/kotlin/com/ttcoachai/shared/TestFixturesV2.kt   # MODIFY — add loadVideo4Rtm()
shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilter.kt  # MODIFY — wristDx + kdoc
shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilterTest.kt  # MODIFY — add 1 test
shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilterRealFootageTest.kt  # NEW — stage-level goldens
poses_viewer/src/drill2d/forwardStrokeFilter.ts                # MODIFY — mirror wristDx
poses_viewer/src/drill2d/__tests__/forwardStrokeFilter.test.ts # MODIFY — mirror new test
poses_viewer/src/drill2d/__tests__/golden.test.ts              # MODIFY — add video_4 golden
docs/DESIGN_LIMITATIONS.md                                     # MODIFY — L-28 → RESOLVED
poses_viewer/CLAUDE.md                                         # MODIFY — goldens line
```

All Kotlin commands run from the repo root; all viewer commands from `poses_viewer/`.

---

### Task 1: video_4 fixture + loader

**Files:**
- Create: `shared/src/commonTest/resources/fixtures/video_4_rtm.json`
- Modify: `shared/src/jvmTest/kotlin/com/ttcoachai/shared/TestFixturesV2.kt`

- [ ] **Step 1: Copy the fixture**

```bash
cp Videos/video_4/video_4_poses_rtm.json shared/src/commonTest/resources/fixtures/video_4_rtm.json
```

(~2.0 MB, same class as the committed `andrii_1_rtm.json` at 2.3 MB. Verified facts: `schemaVersion: 2`, `topology: "coco17"`, `intervalMs: 17`, 1003 frames, 720×1280.)

- [ ] **Step 2: Add the loader**

In `shared/src/jvmTest/kotlin/com/ttcoachai/shared/TestFixturesV2.kt`, after the `loadVideo2Rtm()` line, add:

```kotlin
    fun loadVideo4Rtm(): PoseSequence2D = parse("fixtures/video_4_rtm.json")
```

- [ ] **Step 3: Verify it loads**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2ParserTest"`
Expected: PASS (compilation proves the loader resolves; the parser suite stays green).

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonTest/resources/fixtures/video_4_rtm.json shared/src/jvmTest/kotlin/com/ttcoachai/shared/TestFixturesV2.kt
git commit -m "test(shared): add video_4 shadow-play fixture (12 visually-verified drives)"
```

---

### Task 2: Kotlin fix — wristDx approach window (TDD)

**Files:**
- Modify: `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilterTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilter.kt`

- [ ] **Step 1: Write the failing test**

Add to `ForwardStrokeFilterTest.kt` (inside the class, after `gatedWristIsDropped`). It uses the existing `frames(...)` and `stroke(...)` helpers in that file:

```kotlin
    @Test
    fun continuousPlayBledStartBoundaryStillReadsForward() {
        // L-28: on continuous play the boundary walk bleeds startFrame back into
        // the previous follow-through (x already forward of the backswing trough),
        // so start→peak displacement reads BACKWARD on a true forward drive.
        // The ~100 ms approach INTO the peak must read forward instead.
        // x: follow-through 0.72 → backswing trough 0.50 → drive up to 0.71
        val xs = listOf(0.72f, 0.60f, 0.50f, 0.55f, 0.65f, 0.70f, 0.71f)
        val f = frames(xs) // nose +x (facing forward = +x)
        val drive = stroke(start = 0, peak = 4, end = 6) // x[4]−x[0] = −0.07 < 0
        val kept = ForwardStrokeFilter.filter(listOf(drive), f, Handedness.RIGHT)
        assertEquals(listOf(drive), kept)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ForwardStrokeFilterTest"`
Expected: FAIL on `continuousPlayBledStartBoundaryStillReadsForward` — old start→peak dx is −0.07, head facing +1, `dx * head > 0` false → stroke dropped, `kept` is empty. The other 8 tests pass.

- [ ] **Step 3: Implement the fix**

In `ForwardStrokeFilter.kt`:

(a) Add the window constant after `MIN_GROUP_SIZE`:

```kotlin
    /**
     * Direction is read over this window of APPROACH into the peak. ~100 ms is
     * long enough to span RTMPose jitter at any supported fps (≥1 frame even at
     * 10 fps fixtures) and short enough to stay inside the drive's final
     * acceleration regardless of where the start boundary landed (L-28).
     */
    const val PEAK_APPROACH_WINDOW_MS = 100L
```

(b) Replace the entire `wristDx` function (currently the start→peak version) with:

```kotlin
    /**
     * Wrist x-displacement over the ~[PEAK_APPROACH_WINDOW_MS] approach INTO the
     * peak: x[peak] − x[approach], where approach is the latest frame ≥100 ms
     * before the peak, clamped to startFrame; null when the wrist is gated at
     * either end. Start→peak displacement is deliberately NOT used: on continuous
     * play the smoothed speed never drops below the boundary floor between swings,
     * startFrame bleeds into the previous follow-through, and true drives read
     * backward (L-28 — video_4 dropped 7 of 12 drives). The drive accelerates
     * into the peak, so the approach direction IS the stroke direction.
     */
    private fun wristDx(
        stroke: Stroke2D,
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        minScore: Float
    ): Float? {
        val wristIdx = Coco17.wrist(handedness)
        val peakFrame = frames.getOrNull(stroke.peakFrame) ?: return null
        var a = stroke.peakFrame
        while (a > stroke.startFrame &&
            peakFrame.timestampMs - frames[a - 1].timestampMs <= PEAK_APPROACH_WINDOW_MS
        ) a--
        val approach = frames.getOrNull(a)?.keypoints?.getOrNull(wristIdx)
            ?.takeIf { it.score >= minScore } ?: return null
        val peak = peakFrame.keypoints.getOrNull(wristIdx)
            ?.takeIf { it.score >= minScore } ?: return null
        return peak.x - approach.x
    }
```

(c) Update the class kdoc: in the first paragraph, replace the sentence

```
A forehand-drive forward
 * stroke moves the wrist in the player's FACING direction between startFrame and
 * peakFrame; recovery moves opposite.
```

with

```
A forehand-drive forward
 * stroke moves the wrist in the player's FACING direction during the final
 * ~100 ms approach into the speed peak; recovery moves opposite. (Direction is
 * NOT read start→peak — see [wristDx] and DESIGN_LIMITATIONS L-28.)
```

- [ ] **Step 4: Run the filter suite to verify all pass**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ForwardStrokeFilterTest"`
Expected: PASS — all 9 tests (8 existing + the new one).

- [ ] **Step 5: Run the full shared suite**

Run: `./gradlew :shared:jvmTest`
Expected: PASS, including `ForehandDriveEndToEndTest` (the andrii_1 golden path is unchanged by design — speed-dominance still resolves facing=+1 there). If any E2E assertion fails, STOP and report per the Background section; do not adjust thresholds.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilter.kt shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilterTest.kt
git commit -m "fix(shared): L-28 — stroke direction from peak-approach window, not start→peak"
```

---

### Task 3: Kotlin stage-level goldens on real footage

**Files:**
- Create: `shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilterRealFootageTest.kt`

- [ ] **Step 1: Write the golden test**

```kotlin
package com.ttcoachai.shared.drill

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseSequence2D
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stage-level goldens for the detection chain (detect → ForwardStrokeFilter →
 * RepFilter) on real footage. Mirrored 1:1 by the TS harness
 * (poses_viewer/src/drill2d/__tests__/golden.test.ts) — per the binding fix-flow
 * rule these Kotlin numbers are the source of truth for the TS goldens; update
 * both suites in the same change or not at all.
 */
class ForwardStrokeFilterRealFootageTest {

    private fun counts(seq: PoseSequence2D): Triple<Int, Int, Int> {
        // Pre-protocol footage → cameraYawDeg pinned to 0 (xScale = aspectRatio).
        val raw = StrokeDetector2D().detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)
        val forward = ForwardStrokeFilter.filter(raw, seq.frames, Handedness.RIGHT)
        val reps = RepFilter.filter(forward)
        println("raw=${raw.size} forward=${forward.size} reps=${reps.size}")
        return Triple(raw.size, forward.size, reps.size)
    }

    @Test
    fun andrii1GoldenUnchangedByL28Fix() {
        assertEquals(Triple(23, 15, 15), counts(TestFixturesV2.loadAndriiRtm()))
    }

    @Test
    fun video4ShadowPlayDrivesAreKept() {
        // Ground truth: 12 forward drives, right hand verified visually
        // frame-by-frame (racket + watch overlay, 2026-06-11). Before the L-28
        // fix this read 18/4/4 — startFrame bled into the previous follow-through
        // and 7 true drives measured dx ≤ 0.
        assertEquals(Triple(18, 12, 9), counts(TestFixturesV2.loadVideo4Rtm()))
    }
}
```

- [ ] **Step 2: Run it**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ForwardStrokeFilterRealFootageTest"`
Expected: PASS with printed lines `raw=23 forward=15 reps=15` and `raw=18 forward=12 reps=9`.

**If video_4 prints different numbers:** STOP. Do not change the assertion to match. The prototype numbers are float64; a Kotlin float32 divergence here means a borderline stroke flipped — print per-stroke `{startFrame, peakFrame, endFrame, peakSpeed}` plus the computed approach-dx for each, verify against the 12-drive ground truth, and report which stroke differs and why before any pinning decision.

- [ ] **Step 3: Commit**

```bash
git add shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/ForwardStrokeFilterRealFootageTest.kt
git commit -m "test(shared): stage-level goldens — andrii_1 23/15/15, video_4 18/12/9"
```

---

### Task 4: TS mirror (filter + tests + goldens)

**Files:**
- Modify: `poses_viewer/src/drill2d/forwardStrokeFilter.ts`
- Modify: `poses_viewer/src/drill2d/__tests__/forwardStrokeFilter.test.ts`
- Modify: `poses_viewer/src/drill2d/__tests__/golden.test.ts`

All commands from `poses_viewer/`.

- [ ] **Step 1: Write the failing unit test**

Add to `poses_viewer/src/drill2d/__tests__/forwardStrokeFilter.test.ts`, inside the `describe` block after the `gated wrist is dropped` test (uses the existing `frames`/`stroke` helpers; frames are at 100 ms spacing):

```ts
  it('continuous play: bled start boundary still reads forward (L-28)', () => {
    // x: follow-through 0.72 → backswing trough 0.50 → drive up to 0.71
    const xs = [0.72, 0.60, 0.50, 0.55, 0.65, 0.70, 0.71]
    const f = frames(xs) // nose +x
    const drive = stroke(0, 4, 6) // x[4]−x[0] < 0 — old start→peak read backward
    expect(filterForwardStrokes([drive], f, 'right')).toEqual([drive])
  })
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/forwardStrokeFilter.test.ts`
Expected: FAIL on the new test only (8 existing pass).

- [ ] **Step 3: Mirror the fix**

In `poses_viewer/src/drill2d/forwardStrokeFilter.ts`:

(a) After the `MIN_GROUP_SIZE` export, add:

```ts
/** Direction is read over this window of APPROACH into the peak (L-28). */
export const PEAK_APPROACH_WINDOW_MS = 100
```

(b) Replace the entire `wristDx` function with:

```ts
/**
 * Wrist x-displacement over the ~100 ms approach INTO the peak: x[peak] −
 * x[approach], where approach is the latest frame ≥100 ms before the peak,
 * clamped to startFrame; null when the wrist is gated at either end. Mirrors
 * ForwardStrokeFilter.wristDx (L-28): start→peak is NOT used — on continuous
 * play startFrame bleeds into the previous follow-through and true drives
 * read backward.
 */
function wristDx(
  stroke: Stroke2D,
  frames: PoseFrame2D[],
  handedness: Handedness,
  minScore: number,
): number | null {
  const wristIdx = Coco17.wrist(handedness)
  const peakFrame = frames[stroke.peakFrame]
  if (peakFrame === undefined) return null
  let a = stroke.peakFrame
  while (a > stroke.startFrame && peakFrame.timestampMs - frames[a - 1].timestampMs <= PEAK_APPROACH_WINDOW_MS) a--
  const approachKp = frames[a] !== undefined ? scored(frames[a].keypoints, wristIdx, minScore) : null
  const peakKp = scored(peakFrame.keypoints, wristIdx, minScore)
  if (approachKp === null || peakKp === null) return null
  return peakKp.x - approachKp.x
}
```

(c) Update the module kdoc's direction sentence: in the block comment at the top of the file, the description stays valid except direction provenance — append to the end of that comment block (before the closing `*/`):

```
 * Direction is read over the ~100 ms approach into the peak, not start→peak
 * (L-28 — bled boundaries on continuous play).
```

- [ ] **Step 4: Run unit suite to verify all pass**

Run: `cd poses_viewer && npx vitest run src/drill2d/__tests__/forwardStrokeFilter.test.ts`
Expected: PASS (9 tests).

- [ ] **Step 5: Add the video_4 golden + update goldens**

In `poses_viewer/src/drill2d/__tests__/golden.test.ts`, inside the `GOLDEN parity vs Kotlin E2E` describe block, add after the `video_2` test:

```ts
  it('video_4: shadow play — 18 raw → 12 forward → 9 reps (mirrors Kotlin)', () => {
    const seq = load('video_4_rtm.json')
    const r = countStrokes(seq, { handedness: 'right', cameraYawDeg: 0 })
    expect(r.rawStrokes).toHaveLength(18)
    expect(r.forwardStrokes).toHaveLength(12) // 12 drives, visually verified
    expect(r.reps).toHaveLength(9)
    // eslint-disable-next-line no-console
    console.log(`video_4: raw=${r.rawStrokes.length} forward=${r.forwardStrokes.length} reps=${r.reps.length}`)
  })
```

**These numbers must equal what Kotlin printed in Task 3 Step 2.** If Task 3 pinned different (investigated and approved) numbers, use those here instead — TS mirrors Kotlin, never the other way.

The andrii_1 test (23/15/15) and the video_2 invariants test stay byte-identical — the L-28 fix must not change them. (video_2's *printed* counts will change from 8/3/3 to 8/5/4; the test asserts invariants, not exact counts, so no edit.)

- [ ] **Step 6: Run the full viewer verification**

Run: `cd poses_viewer && npx vitest run && npx tsc -b --noEmit`
Expected: all suites PASS (the golden file now prints `andrii_1: raw=23 forward=15 reps=15`, `video_4: raw=18 forward=12 reps=9`), typecheck clean.

- [ ] **Step 7: Commit**

```bash
git add poses_viewer/src/drill2d/forwardStrokeFilter.ts poses_viewer/src/drill2d/__tests__/forwardStrokeFilter.test.ts poses_viewer/src/drill2d/__tests__/golden.test.ts
git commit -m "fix(poses_viewer): mirror L-28 peak-approach direction + video_4 golden (18/12/9)"
```

---

### Task 5: Docs + final verification

**Files:**
- Modify: `docs/DESIGN_LIMITATIONS.md`
- Modify: `poses_viewer/CLAUDE.md`

- [ ] **Step 1: Resolve L-28 in the registry**

In `docs/DESIGN_LIMITATIONS.md`:

(a) Delete the whole `### L-28 · Stroke direction measured start→peak misreads continuous play — `OPEN`` entry from section 1 (the heading and its paragraph through the `**Refs:**` line).

(b) Append to the end of the `## Resolved` section:

```markdown
### L-28 · Stroke direction measured start→peak misreads continuous play — `RESOLVED`
`ForwardStrokeFilter.wristDx` took wrist x-displacement startFrame→peakFrame; on
continuous shadow play the start boundary bleeds into the previous follow-through
and true drives read backward (video_4: 7 of 12 visually-verified drives dropped,
4 reps from 12). Detection itself was sound — every forward-motion run contained
a raw detector peak.
**Resolved by:** direction read over the ~100 ms approach INTO the peak
(`PEAK_APPROACH_WINDOW_MS`, timestamp-walked, clamped to startFrame) in
`ForwardStrokeFilter.kt`, mirrored in the TS harness; stage-level goldens
andrii_1 23/15/15 (unchanged) and video_4 18/12/9 pinned in BOTH suites
(`ForwardStrokeFilterRealFootageTest.kt`, `golden.test.ts`).
```

(Use the actual fix commit SHA from Task 2 in place of a bare description if your tooling records it — format as the other Resolved entries do, e.g. `**Resolved by:** \`<sha>\` — …`.)

- [ ] **Step 2: Update the viewer goldens line**

In `poses_viewer/CLAUDE.md`, in the `### \`src/drill2d/\` + …` section, replace the sentence

```
Golden parity test (`drill2d/__tests__/golden.test.ts`): 23 raw / 15 forward / 15 reps on
andrii_1_rtm — reads fixtures from `shared/src/commonTest/resources/fixtures/` via repo-relative path.
```

with

```
Golden parity tests (`drill2d/__tests__/golden.test.ts`, mirroring Kotlin
`ForwardStrokeFilterRealFootageTest`): andrii_1_rtm 23 raw / 15 forward / 15 reps;
video_4_rtm (shadow play) 18 / 12 / 9 — fixtures read from
`shared/src/commonTest/resources/fixtures/` via repo-relative path.
```

- [ ] **Step 3: Full verification, both suites**

```bash
./gradlew :shared:jvmTest
```
Expected: PASS (all shared tests, including the new real-footage goldens and the untouched E2E).

```bash
cd poses_viewer && npx vitest run && npx tsc -b --noEmit
```
Expected: PASS, typecheck clean.

- [ ] **Step 4: Commit**

```bash
git add docs/DESIGN_LIMITATIONS.md poses_viewer/CLAUDE.md
git commit -m "docs: L-28 resolved — peak-approach direction read, goldens documented in both suites"
```
