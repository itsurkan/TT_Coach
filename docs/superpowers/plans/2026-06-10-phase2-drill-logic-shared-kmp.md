# Phase 2 — Drill Logic in Shared KMP (Fixture-Driven, TDD) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Platform-independent drill-analysis logic in `shared/commonMain` — schema-v2 pose loading, COCO-17 in-plane angles, wrist-speed stroke detection, baseline-derived reference ranges, and UA+EN cadenced feedback — all proven on real RTMPose fixtures.

**Architecture:** New 2D types (`Keypoint2D`/`PoseFrame2D`/`PoseSequence2D`/`ViewGeometry`) and a dependency-free schema-v2 JSON parser feed `AngleCalculations2D` (score-gated in-plane angles) and `StrokeDetector2D` (wrist-speed local-maximum over the frame sequence); all geometry is corrected by one `xScale` factor combining aspect ratio and camera-yaw foreshortening (`CameraAngleEstimator` re-detects the yaw **per rep** from the pre-stroke ready-stance window — the player moves their feet during the drill — with an explicit override available; reps beyond ~30° get no feedback instead of a bad correction). Per-rep metrics at the speed peak flow into the existing 003 baseline path via a new generic `BaselineDeriver.deriveFromMetrics` entry point; `BaselineRuleFactory` + `FrameRuleEvaluator` stay the single source of rule logic. A new `drill/` package turns rule failures into `FeedbackCue`s, formats them UA/EN (degrees only for in-plane metrics), and throttles to the 3–5 s cadence.

**Tech Stack:** Kotlin 2.1.0 KMP (`shared/commonMain`, zero external deps — repo convention), `kotlin-test` in `commonTest`, ClassLoader fixture loading in `jvmTest` (existing `TestFixtures` pattern), real RTMPose exports from Phase 1 as fixtures.

**Spec:** [docs/superpowers/specs/2026-06-10-2d-pivot-design.md](../specs/2026-06-10-2d-pivot-design.md), context doc §3–§4, [docs/pose_json_schema_v2.md](../../pose_json_schema_v2.md).

---

## Design notes (read before Task 1)

1. **Horizontal-scale correction (correctness-critical).** Schema-v2 `x` is normalized by `videoWidth`, `y` by `videoHeight`. Angles computed on raw normalized coords are distorted (e.g. 712×1280 squashes x by 0.556). Every geometric function takes a single `xScale` factor (`ViewGeometry.xScale = aspectRatio / cos(cameraYawDeg)`, note 9) and multiplies x-deltas by it before any trig. Synthetic tests use `xScale = 1f`.
2. **Score gating.** Every angle function returns `null` if any required keypoint's `score < minScore` (default `0.3f`). No feedback on low-confidence frames (spec error-handling section).
3. **Why a new `AngleCalculations2D` instead of editing `AngleCalculations`:** the existing object is MediaPipe-33/`Landmark3D`-typed and feeds the frozen live pipeline. We adapt its dot-product technique to `Keypoint2D` + COCO indices; the old object stays untouched (freeze, don't delete).
4. **Stroke detection** is batch over a frame list (fixture-driven phase). The algorithm is streaming-compatible (sliding window of smoothed speeds); a live wrapper is Phase 3 work.
5. **Baseline reuse.** `BaselineDeriver` currently extracts metrics from `AnalysisResult` fields. We refactor its core into a public `deriveFromMetrics(repMetrics, repPhaseDurations, …)` and have the existing `derive(...)` delegate to it. Existing tests must stay green — the 003 calibration path is unchanged.
6. **Trust rule encoding.** `MetricPrecisionPolicy` maps metric keys to `PRECISE_DEGREES` (the 5 in-plane metrics) or `QUALITATIVE` (everything else, default). The message catalog inserts degree numbers **only** for `PRECISE_DEGREES`.
7. **Backswing phase is not segmented in v1** (needs wrist-direction-reversal analysis — YAGNI until real protocol footage exists). Phase durations fed to rhythm rules: `forward_swing_ms` (start→peak) and `stroke_total_ms` (start→end).
8. **Exit gate honesty.** `Videos/` footage was not shot to the camera-placement protocol (spec "Content dependency"). The end-to-end test proves the pipeline mechanics (calibrate → analyze → correct cues on perturbation), not tuned reference ranges.
9. **Camera-yaw correction (founder decision 2026-06-10).** poses_viewer already has camera-angle compensation (`MannequinEditor.cameraYawOffsetDeg`, yaw detection in `extractTorsoLegs.ts`), but that math uses the MediaPipe z coordinate — unusable with COCO-17. The 2D equivalent: estimate the camera's deviation from a perfect side view via shoulder foreshortening (in true profile the shoulders overlap horizontally; apparent shoulder separation / torso length ≈ sin(yaw) — same anthropometric source, Drillis & Contini, as poses_viewer bone lengths). The correction is first-order: un-squash x-deltas by `1/cos(yaw)`, which folds into the same x-scale factor as the aspect ratio — so every geometric function takes one `xScale` parameter (`ViewGeometry.xScale = aspectRatio / cos(cameraYawDeg)`). Estimation returns `|yaw|` only (foreshortening can't recover the sign; cos is even, so the correction doesn't need it). Policy per founder: auto-estimate with explicit override; for drills that **require** the camera angle (forehand drive = side view), beyond ~30° the model is unreliable → **skip feedback** (`placementOk = false`, `CameraPlacementException` during calibration); within the threshold, correct.
10. **Yaw is dynamic — re-estimated per rep (founder requirement 2026-06-10).** The player moves their feet during a drill, so their orientation relative to the camera changes mid-session. Yaw is therefore estimated **per stroke**, from the ~1 s ready-stance window immediately *before* each stroke (`CameraAngleEstimator.estimateYawForStroke`, lookback 1000 ms) — never from the swing itself, where the player's own body rotation would be misread as camera placement. With reps every 1–3 s this satisfies the founder's "recalculate every second-or-few"; the Phase 3 live loop wraps the same estimator in a rolling window. Placement gating is per rep: bad-placement reps are excluded from calibration (throws `CameraPlacementException` only when that exclusion drops the count below `minRepCount`) and get no cues during analysis. Stroke *detection* runs on plain aspect ratio — peak finding is threshold-based and tolerant to the ≤15% speed-magnitude error of uncorrected ≤30° yaw; only metric extraction uses the per-rep corrected xScale.
11. **Registry follow-ups folded in pre-execution (L-03, L-05, L-08; founder 2026-06-10).** **L-08:** the exporter takes width/height from the *decoded* frame, not header props, so rotation metadata (portrait phone video) can't invert the aspect correction — fixed *before* the Task 3 re-export. **L-05:** rep metrics are the **median over a ±70 ms window** around the speed peak (`DrillMetrics.extractAtPeak`) instead of one raw frame — RTMPose per-frame jitter doesn't feed the baseline, and a single junk frame inside the window can't shift a rep (median, not mean). At 100 ms intervals the window degrades to the single peak frame, so coarse-fixture tests are unaffected. **L-03:** `RepFilter` drops non-stroke peaks (ball pickup, hand wipe, walking) by banding against the session's median peak speed and duration, applied to detector output in BOTH calibration and analysis; with fewer than 4 strokes nothing is filtered (no cluster to trust). **L-04:** `torsoLean`'s sign is normalized by facing direction (nose, fallback ear-midpoint, relative to hip-mid) so POSITIVE always means forward lean regardless of which way the player faces; indeterminate facing → null (no measurement beats a possibly-flipped one).
12. **Detector units are fps- and scale-invariant (DESIGN_LIMITATIONS L-01/L-02, founder 2026-06-10).** Phase 3 capture fps is configurable 30/60/120, so `StrokeDetector2D` tuning is expressed in **milliseconds** (smoothing window, peak radius, min peak gap) and converted to frame counts via `intervalMs` at detect time — frame-count tuning would silently change meaning with every fps setting. Wrist speed is normalized to **torso-lengths per second** (torso = median shoulder-mid→hip-mid distance over the sequence), making `minPeakSpeed` invariant to camera distance/zoom AND fps. Same rule for the estimator's pre-stroke lookback (`lookbackMs`, not frames). Fixtures are re-exported at the videos' native fps **before** Task 5 cements thresholds — 10 fps gives only 2–3 samples per ~200 ms forward swing, so peaks are systematically underestimated (L-02). The limitations register is the source of truth: [docs/DESIGN_LIMITATIONS.md](../../DESIGN_LIMITATIONS.md); Task 15 moves L-01, L-02, L-03, L-04, L-05 and L-08 to its Resolved section.

## File structure

```
shared/src/commonMain/kotlin/com/ttcoachai/shared/
  models/Keypoint2D.kt          # Keypoint2D data class
  models/PoseFrame2D.kt         # PoseFrame2D data class
  models/PoseSequence2D.kt      # PoseSequence2D + aspectRatio
  models/Topology.kt            # Topology enum (COCO17, HALPE26)
  models/Coco17.kt              # keypoint index constants + handedness accessors
  models/Handedness.kt          # Handedness enum
  models/Stroke2D.kt            # detected stroke (frame indices + peak speed)
  models/ViewGeometry.kt        # aspectRatio + cameraYawDeg → xScale
  io/PoseJsonV2Parser.kt        # schema-v2 parser + PoseSchemaException
  analysis/AngleCalculations2D.kt   # in-plane angles, score-gated, xScale-corrected
  analysis/CameraAngleEstimator.kt  # side-view yaw from shoulder foreshortening
  analysis/BaselineDeriver.kt   # MODIFIED: extract public deriveFromMetrics(...)
  detection/StrokeDetector2D.kt # wrist-speed local-maximum detector
  drill/DrillMetrics.kt         # metric keys + per-frame extraction + extractAtPeak median smoothing (L-05)
  drill/RepFilter.kt            # drops non-stroke peaks by speed/duration banding (L-03)
  drill/SanityBounds.kt         # hand-coded sanity ranges (elbow 20–170° etc.)
  drill/MetricPrecision.kt      # MetricPrecision enum + MetricPrecisionPolicy
  drill/FeedbackCue.kt          # FeedbackCue + CueDirection
  drill/DrillFeedbackEngine.kt  # rules + metrics → cues
  drill/FeedbackMessageCatalog.kt   # UA + EN templates
  drill/FeedbackCadencePolicy.kt    # 3–5 s throttle
  drill/DrillCalibrator.kt      # frames → PersonalBaseline (003 path)
  drill/ForehandDriveDrillAnalyzer.kt  # orchestrator: frames → report

shared/src/commonTest/kotlin/com/ttcoachai/shared/
  io/PoseJsonV2ParserTest.kt
  analysis/AngleCalculations2DTest.kt
  analysis/CameraAngleEstimatorTest.kt
  analysis/BaselineDeriverFromMetricsTest.kt
  detection/StrokeDetector2DTest.kt
  drill/DrillMetricsTest.kt
  drill/RepFilterTest.kt
  drill/DrillFeedbackEngineTest.kt
  drill/FeedbackMessageCatalogTest.kt
  drill/FeedbackCadencePolicyTest.kt
  drill/DrillCalibratorTest.kt

shared/src/commonTest/resources/fixtures/
  andrii_1_rtm.json             # copied from Videos/andrii_1/andrii_1_poses_rtm.json
  video_2_rtm.json              # copied from Videos/video_2/video_2_poses_rtm.json

shared/src/jvmTest/kotlin/com/ttcoachai/shared/
  TestFixturesV2.kt             # ClassLoader loader → PoseJsonV2Parser
  io/PoseJsonV2FixtureTest.kt
  detection/StrokeDetector2DFixtureTest.kt
  drill/ForehandDriveEndToEndTest.kt
```

Run commands throughout: `./gradlew :shared:jvmTest --tests "<class>"` (commonTest classes execute on the JVM target via this task). Full suite: `./gradlew :shared:jvmTest`.

**Commit hygiene:** the working tree has unrelated dirt (`node_modules/.vite/...`, `poses_viewer/tsconfig.tsbuildinfo`). Always `git add` explicit paths, never `git add -A`.

---

### Task 1: 2D pose models (Keypoint2D, PoseFrame2D, PoseSequence2D, Topology, Coco17, Handedness, Stroke2D, ViewGeometry)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/ViewGeometry.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Keypoint2D.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PoseFrame2D.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PoseSequence2D.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Topology.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Coco17.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Handedness.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Stroke2D.kt`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/models/Pose2DModelsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttcoachai.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Pose2DModelsTest {

    @Test
    fun aspectRatioIsWidthOverHeight() {
        val seq = PoseSequence2D(
            topology = Topology.COCO17,
            model = "rtmpose-m",
            videoName = "clip.mp4",
            intervalMs = 100L,
            totalFrames = 0,
            videoDurationMs = 0L,
            videoWidth = 712,
            videoHeight = 1280,
            frames = emptyList()
        )
        assertEquals(712f / 1280f, seq.aspectRatio, 1e-6f)
    }

    @Test
    fun coco17HandednessAccessors() {
        assertEquals(Coco17.RIGHT_WRIST, Coco17.wrist(Handedness.RIGHT))
        assertEquals(Coco17.LEFT_WRIST, Coco17.wrist(Handedness.LEFT))
        assertEquals(Coco17.RIGHT_ELBOW, Coco17.elbow(Handedness.RIGHT))
        assertEquals(Coco17.LEFT_SHOULDER, Coco17.shoulder(Handedness.LEFT))
        assertEquals(Coco17.RIGHT_HIP, Coco17.hip(Handedness.RIGHT))
        assertEquals(Coco17.LEFT_KNEE, Coco17.knee(Handedness.LEFT))
        assertEquals(Coco17.RIGHT_ANKLE, Coco17.ankle(Handedness.RIGHT))
    }

    @Test
    fun topologyFromJsonName() {
        assertEquals(Topology.COCO17, Topology.fromJsonName("coco17"))
        assertEquals(Topology.HALPE26, Topology.fromJsonName("halpe26"))
        assertEquals(null, Topology.fromJsonName("mediapipe33"))
        assertEquals(17, Topology.COCO17.keypointCount)
        assertEquals(26, Topology.HALPE26.keypointCount)
    }

    @Test
    fun viewGeometryXScaleCombinesAspectAndCameraYaw() {
        assertEquals(0.5f, ViewGeometry(aspectRatio = 0.5f).xScale, 1e-6f)
        // 1/cos(60°) = 2 → xScale doubles
        assertEquals(1.0f, ViewGeometry(aspectRatio = 0.5f, cameraYawDeg = 60f).xScale, 1e-4f)
        // sign-independent (cos is even)
        assertEquals(
            ViewGeometry(1f, cameraYawDeg = 30f).xScale,
            ViewGeometry(1f, cameraYawDeg = -30f).xScale,
            1e-6f
        )
        assertFailsWith<IllegalArgumentException> { ViewGeometry(1f, cameraYawDeg = 75f) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.models.Pose2DModelsTest"`
Expected: FAIL — compilation error, unresolved references (`PoseSequence2D`, `Topology`, …)

- [ ] **Step 3: Write the models**

`Keypoint2D.kt`:
```kotlin
package com.ttcoachai.shared.models

/** Single COCO/Halpe keypoint in normalized image coords (x / videoWidth, y / videoHeight). */
data class Keypoint2D(
    val x: Float,
    val y: Float,
    val score: Float
)
```

`PoseFrame2D.kt`:
```kotlin
package com.ttcoachai.shared.models

/** One video frame of 2D keypoints. Empty [keypoints] = no person detected. */
data class PoseFrame2D(
    val frameIndex: Int,
    val timestampMs: Long,
    val keypoints: List<Keypoint2D>
)
```

`Topology.kt`:
```kotlin
package com.ttcoachai.shared.models

/** Keypoint topology of a schema-v2 pose export. Indices 0–16 are identical in both. */
enum class Topology(val jsonName: String, val keypointCount: Int) {
    COCO17("coco17", 17),
    HALPE26("halpe26", 26);

    companion object {
        fun fromJsonName(name: String): Topology? = entries.firstOrNull { it.jsonName == name }
    }
}
```

`PoseSequence2D.kt`:
```kotlin
package com.ttcoachai.shared.models

/** Full schema-v2 pose export: header metadata + frames. */
data class PoseSequence2D(
    val topology: Topology,
    val model: String,
    val videoName: String,
    val intervalMs: Long,
    val totalFrames: Int,
    val videoDurationMs: Long,
    val videoWidth: Int,
    val videoHeight: Int,
    val frames: List<PoseFrame2D>
) {
    /** x-deltas must be multiplied by this before any angle/distance math. */
    val aspectRatio: Float get() = videoWidth.toFloat() / videoHeight.toFloat()
}
```

`Handedness.kt`:
```kotlin
package com.ttcoachai.shared.models

/** Playing hand. [baselineString] matches PersonalBaseline.drillerHandedness values. */
enum class Handedness(val baselineString: String) {
    RIGHT("right"),
    LEFT("left")
}
```

`Coco17.kt`:
```kotlin
package com.ttcoachai.shared.models

/** COCO-17 keypoint indices (docs/pose_json_schema_v2.md). Valid for Halpe26 indices 0–16 too. */
object Coco17 {
    const val NOSE = 0
    const val LEFT_EYE = 1
    const val RIGHT_EYE = 2
    const val LEFT_EAR = 3
    const val RIGHT_EAR = 4
    const val LEFT_SHOULDER = 5
    const val RIGHT_SHOULDER = 6
    const val LEFT_ELBOW = 7
    const val RIGHT_ELBOW = 8
    const val LEFT_WRIST = 9
    const val RIGHT_WRIST = 10
    const val LEFT_HIP = 11
    const val RIGHT_HIP = 12
    const val LEFT_KNEE = 13
    const val RIGHT_KNEE = 14
    const val LEFT_ANKLE = 15
    const val RIGHT_ANKLE = 16

    fun shoulder(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_SHOULDER else LEFT_SHOULDER
    fun elbow(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_ELBOW else LEFT_ELBOW
    fun wrist(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_WRIST else LEFT_WRIST
    fun hip(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_HIP else LEFT_HIP
    fun knee(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_KNEE else LEFT_KNEE
    fun ankle(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_ANKLE else LEFT_ANKLE
}
```

`Stroke2D.kt`:
```kotlin
package com.ttcoachai.shared.models

/**
 * One detected stroke from the wrist-speed signal. All values are frame indices
 * into the source frame list; durations are derived by the caller via intervalMs.
 */
data class Stroke2D(
    val strokeIndex: Int,
    val startFrame: Int,
    val peakFrame: Int,
    val endFrame: Int,
    /** Smoothed wrist speed at the peak, in torso-lengths per second (L-01). */
    val peakSpeed: Float
)
```

`ViewGeometry.kt`:
```kotlin
package com.ttcoachai.shared.models

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Geometry of the recording viewpoint. [xScale] is THE single factor every
 * geometric function applies to x-deltas: it combines per-axis normalization
 * (schema-v2 normalizes x by width, y by height → aspect ratio) with first-order
 * camera-yaw foreshortening correction (1/cos) for side-view drills.
 *
 * [cameraYawDeg] is the camera's deviation from a perfect side view (sign
 * irrelevant — cos is even). Correction quality degrades with yaw: drills that
 * require the side view gate feedback above ~30° (drill policy, see
 * DrillCalibrator.DEFAULT_MAX_CAMERA_YAW_DEG); MAX_YAW_DEG is the hard math limit.
 */
data class ViewGeometry(
    val aspectRatio: Float,
    val cameraYawDeg: Float = 0f
) {
    init {
        require(abs(cameraYawDeg) <= MAX_YAW_DEG) {
            "cameraYawDeg must be within ±$MAX_YAW_DEG°, got $cameraYawDeg"
        }
    }

    val xScale: Float = aspectRatio / cos(cameraYawDeg * DEG_TO_RAD)

    companion object {
        const val MAX_YAW_DEG = 60f
        private const val DEG_TO_RAD = (PI / 180.0).toFloat()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.models.Pose2DModelsTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Keypoint2D.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PoseFrame2D.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PoseSequence2D.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Topology.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Coco17.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Handedness.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Stroke2D.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/models/ViewGeometry.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/models/Pose2DModelsTest.kt
git commit -m "feat(shared): 2D pose models for COCO-17 schema v2"
```

---

### Task 2: Schema-v2 parser (`PoseJsonV2Parser`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/io/PoseJsonV2Parser.kt`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/io/PoseJsonV2ParserTest.kt`

Dependency-free parser (repo convention: shared module has no external deps). Same regex-anchoring technique as the proven `TestFixtures` jvmTest parser, but productionized: strict validation, explicit `PoseSchemaException` on legacy v1 input, unknown topology, or wrong landmark counts.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttcoachai.shared.io

import com.ttcoachai.shared.models.Topology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PoseJsonV2ParserTest {

    private fun landmarksJson(n: Int): String =
        (0 until n).joinToString(",") { """{ "index": $it, "x": 0.5, "y": 0.25, "score": 0.9 }""" }

    private fun v2Json(
        topology: String = "coco17",
        landmarkCount: Int = 17,
        secondFrameLandmarks: String = ""
    ): String = """
        {
          "schemaVersion": 2,
          "topology": "$topology",
          "model": "rtmpose-m",
          "videoName": "clip.mp4",
          "intervalMs": 100,
          "totalFrames": 2,
          "videoDurationMs": 200,
          "videoWidth": 712,
          "videoHeight": 1280,
          "exportTimestamp": 1781085099336,
          "frames": [
            { "frameIndex": 0, "timestampMs": 0, "landmarks": [ ${landmarksJson(landmarkCount)} ] },
            { "frameIndex": 1, "timestampMs": 100, "landmarks": [ $secondFrameLandmarks ] }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesCoco17HappyPath() {
        val seq = PoseJsonV2Parser.parse(v2Json())
        assertEquals(Topology.COCO17, seq.topology)
        assertEquals("rtmpose-m", seq.model)
        assertEquals("clip.mp4", seq.videoName)
        assertEquals(100L, seq.intervalMs)
        assertEquals(2, seq.totalFrames)
        assertEquals(712, seq.videoWidth)
        assertEquals(1280, seq.videoHeight)
        assertEquals(2, seq.frames.size)
        assertEquals(17, seq.frames[0].keypoints.size)
        assertEquals(0.5f, seq.frames[0].keypoints[3].x, 1e-6f)
        assertEquals(0.25f, seq.frames[0].keypoints[3].y, 1e-6f)
        assertEquals(0.9f, seq.frames[0].keypoints[3].score, 1e-6f)
        assertEquals(100L, seq.frames[1].timestampMs)
    }

    @Test
    fun emptyLandmarksFrameIsValid() {
        val seq = PoseJsonV2Parser.parse(v2Json())
        assertTrue(seq.frames[1].keypoints.isEmpty(), "no-person frame must parse to empty keypoints")
    }

    @Test
    fun parsesHalpe26() {
        val seq = PoseJsonV2Parser.parse(v2Json(topology = "halpe26", landmarkCount = 26))
        assertEquals(Topology.HALPE26, seq.topology)
        assertEquals(26, seq.frames[0].keypoints.size)
    }

    @Test
    fun rejectsLegacyV1WithoutSchemaVersion() {
        val legacy = """{ "frames": [ { "frameIndex": 0, "timestampMs": 0, "landmarks": [] } ] }"""
        val ex = assertFailsWith<PoseSchemaException> { PoseJsonV2Parser.parse(legacy) }
        assertTrue(ex.message!!.contains("legacy"), "error must name the legacy v1 case: ${ex.message}")
    }

    @Test
    fun rejectsUnknownTopology() {
        assertFailsWith<PoseSchemaException> {
            PoseJsonV2Parser.parse(v2Json(topology = "mediapipe33"))
        }
    }

    @Test
    fun rejectsWrongLandmarkCount() {
        val ex = assertFailsWith<PoseSchemaException> {
            PoseJsonV2Parser.parse(v2Json(landmarkCount = 12))
        }
        assertTrue(ex.message!!.contains("12"), "error must report the bad count: ${ex.message}")
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        assertFailsWith<PoseSchemaException> {
            PoseJsonV2Parser.parse(v2Json().replace("\"schemaVersion\": 2", "\"schemaVersion\": 3"))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2ParserTest"`
Expected: FAIL — unresolved reference `PoseJsonV2Parser`

- [ ] **Step 3: Write the parser**

`PoseJsonV2Parser.kt`:
```kotlin
package com.ttcoachai.shared.io

import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Topology

/** Thrown when input JSON is not a valid schema-v2 pose export. */
class PoseSchemaException(message: String) : IllegalArgumentException(message)

/**
 * Parser for pose JSON schema v2 (docs/pose_json_schema_v2.md), the format written by
 * scripts/poses/export_poses_rtmpose.py. Pure Kotlin, no dependencies (shared-module
 * convention). Regex-anchored extraction is safe here because the exporter controls
 * the format; all structural assumptions are validated explicitly.
 */
object PoseJsonV2Parser {

    private val SCHEMA_VERSION_RE = Regex(""""schemaVersion"\s*:\s*(\d+)""")
    private val TOPOLOGY_RE = Regex(""""topology"\s*:\s*"([^"]+)"""")
    private val MODEL_RE = Regex(""""model"\s*:\s*"([^"]+)"""")
    private val VIDEO_NAME_RE = Regex(""""videoName"\s*:\s*"([^"]+)"""")
    private val INTERVAL_RE = Regex(""""intervalMs"\s*:\s*(\d+)""")
    private val TOTAL_FRAMES_RE = Regex(""""totalFrames"\s*:\s*(\d+)""")
    private val DURATION_RE = Regex(""""videoDurationMs"\s*:\s*(\d+)""")
    private val WIDTH_RE = Regex(""""videoWidth"\s*:\s*(\d+)""")
    private val HEIGHT_RE = Regex(""""videoHeight"\s*:\s*(\d+)""")
    private val FRAME_INDEX_RE = Regex(""""frameIndex"\s*:\s*(\d+)""")
    private val TIMESTAMP_RE = Regex(""""timestampMs"\s*:\s*(\d+)""")
    private val LANDMARK_RE = Regex(
        """"index"\s*:\s*(\d+)\s*,\s*"x"\s*:\s*([-\d.Ee]+)\s*,\s*"y"\s*:\s*([-\d.Ee]+)\s*,\s*"score"\s*:\s*([-\d.Ee]+)"""
    )

    fun parse(json: String): PoseSequence2D {
        val version = SCHEMA_VERSION_RE.find(json)?.groupValues?.get(1)?.toInt()
            ?: throw PoseSchemaException(
                "Missing schemaVersion — this looks like a legacy v1 (MediaPipe-33) export, " +
                    "which PoseJsonV2Parser does not read"
            )
        if (version != 2) throw PoseSchemaException("Unsupported schemaVersion $version, expected 2")

        val topologyName = TOPOLOGY_RE.find(json)?.groupValues?.get(1)
            ?: throw PoseSchemaException("Missing topology field")
        val topology = Topology.fromJsonName(topologyName)
            ?: throw PoseSchemaException(
                "Unknown topology \"$topologyName\", expected one of: " +
                    Topology.entries.joinToString { it.jsonName }
            )

        val intervalMs = requireLong(json, INTERVAL_RE, "intervalMs")
        if (intervalMs <= 0) throw PoseSchemaException("intervalMs must be > 0, got $intervalMs")
        val videoWidth = requireLong(json, WIDTH_RE, "videoWidth").toInt()
        val videoHeight = requireLong(json, HEIGHT_RE, "videoHeight").toInt()
        if (videoWidth <= 0 || videoHeight <= 0) {
            throw PoseSchemaException("videoWidth/videoHeight must be > 0, got ${videoWidth}x$videoHeight")
        }

        val frames = parseFrames(json, topology)

        return PoseSequence2D(
            topology = topology,
            model = MODEL_RE.find(json)?.groupValues?.get(1) ?: "",
            videoName = VIDEO_NAME_RE.find(json)?.groupValues?.get(1) ?: "",
            intervalMs = intervalMs,
            totalFrames = TOTAL_FRAMES_RE.find(json)?.groupValues?.get(1)?.toInt() ?: frames.size,
            videoDurationMs = DURATION_RE.find(json)?.groupValues?.get(1)?.toLong() ?: 0L,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            frames = frames
        )
    }

    private fun requireLong(json: String, re: Regex, field: String): Long =
        re.find(json)?.groupValues?.get(1)?.toLong()
            ?: throw PoseSchemaException("Missing required field $field")

    private fun parseFrames(json: String, topology: Topology): List<PoseFrame2D> {
        val frames = mutableListOf<PoseFrame2D>()
        val anchors = FRAME_INDEX_RE.findAll(json).toList()
        for ((i, anchor) in anchors.withIndex()) {
            val sectionEnd = if (i + 1 < anchors.size) anchors[i + 1].range.first else json.length
            val section = json.substring(anchor.range.first, sectionEnd)
            val frameIndex = anchor.groupValues[1].toInt()
            val timestampMs = TIMESTAMP_RE.find(section)?.groupValues?.get(1)?.toLong() ?: 0L

            val keypoints = LANDMARK_RE.findAll(section).map { m ->
                Keypoint2D(
                    x = m.groupValues[2].toFloat(),
                    y = m.groupValues[3].toFloat(),
                    score = m.groupValues[4].toFloat()
                )
            }.toList()

            if (keypoints.isNotEmpty() && keypoints.size != topology.keypointCount) {
                throw PoseSchemaException(
                    "Frame $frameIndex has ${keypoints.size} landmarks, " +
                        "expected ${topology.keypointCount} for ${topology.jsonName} (or 0 for no person)"
                )
            }

            frames.add(PoseFrame2D(frameIndex, timestampMs, keypoints))
        }
        return frames
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2ParserTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/io/PoseJsonV2Parser.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/io/PoseJsonV2ParserTest.kt
git commit -m "feat(shared): schema-v2 pose JSON parser with strict topology validation"
```

---

### Task 3: RTMPose fixtures + jvmTest loader

**Files:**
- Create: `shared/src/commonTest/resources/fixtures/andrii_1_rtm.json` (copy)
- Create: `shared/src/commonTest/resources/fixtures/video_2_rtm.json` (copy)
- Create: `shared/src/jvmTest/kotlin/com/ttcoachai/shared/TestFixturesV2.kt`
- Test: `shared/src/jvmTest/kotlin/com/ttcoachai/shared/io/PoseJsonV2FixtureTest.kt`

- [ ] **Step 0: Fix exporter rotation handling (DESIGN_LIMITATIONS L-08)**

`export_poses_rtmpose.py` reads `width`/`height` from `CAP_PROP_FRAME_WIDTH/HEIGHT` header props. Rotation metadata (portrait phone video) can make those disagree with what `cap.read()` actually decodes — which would silently invert the aspect-ratio correction every downstream angle depends on. Fix before re-exporting: take dimensions from the decoded frame itself (the same frames the model sees).

In `scripts/poses/export_poses_rtmpose.py`, replace:

```python
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
```
with:
```python
    # L-08: take dimensions from the DECODED frame, not header props — rotation
    # metadata (portrait phone video) can swap them, which would invert the
    # aspect-ratio correction all downstream angle math depends on.
    ok, probe = cap.read()
    if not ok:
        print(f"ERROR: cannot decode first frame: {video_path}", file=sys.stderr)
        sys.exit(1)
    height, width = probe.shape[:2]
    header_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    header_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    if (width, height) != (header_w, header_h):
        print(f"NOTE: rotation metadata applied: header {header_w}x{header_h} -> decoded {width}x{height}")
    cap.set(cv2.CAP_PROP_POS_MSEC, 0)  # rewind after the probe read
```

(The export loop seeks by `CAP_PROP_POS_MSEC` per frame, so the probe read does not disturb it.) Verify: run the exporter on both videos and check the printed dimensions against `ffprobe -v 0 -select_streams v:0 -show_entries stream=width,height,side_data_list <video>`; the poses_viewer QA in Step 1 is the end-to-end confirmation (a swapped aspect shows up as a visibly squashed skeleton).

- [ ] **Step 1: Re-export both videos at native fps, QA, then copy into test resources**

The existing `*_poses_rtm.json` exports use `--interval 100` (10 fps) — too coarse for stroke-peak detection (DESIGN_LIMITATIONS **L-02**: a ~200 ms forward swing gives 2–3 samples; peak speed is systematically underestimated). Re-export at the videos' native fps **before** any detector threshold is tuned. The `rtmpose-export` skill automates the export; `viewer-qa` covers the visual check; `fixture-pipeline` describes this whole flow — prefer the skills, manual form below:

```bash
# interval = round(1000 / native fps); check fps first:
ffprobe -v 0 -of csv=p=0 -select_streams v:0 -show_entries stream=r_frame_rate Videos/andrii_1/andrii_1.mp4
# e.g. 30/1 → interval 33
python scripts/poses/export_poses_rtmpose.py Videos/andrii_1/andrii_1.mp4 --interval 33
python scripts/poses/export_poses_rtmpose.py Videos/video_2/video_2.mp4 --interval 33
```

Visually QA both re-exports in poses_viewer (`viewer-qa` skill) — same bar as the Phase 1 exit gate — then copy:

```bash
cp Videos/andrii_1/andrii_1_poses_rtm.json shared/src/commonTest/resources/fixtures/andrii_1_rtm.json
cp Videos/video_2/video_2_poses_rtm.json shared/src/commonTest/resources/fixtures/video_2_rtm.json
```

- [ ] **Step 2: Write the failing test**

`TestFixturesV2.kt` (loader, jvmTest — ClassLoader resource loading is JVM-only, same pattern as the existing `TestFixtures`):
```kotlin
package com.ttcoachai.shared

import com.ttcoachai.shared.io.PoseJsonV2Parser
import com.ttcoachai.shared.models.PoseSequence2D

/** Loads schema-v2 RTMPose fixtures from commonTest resources (JVM classpath). */
object TestFixturesV2 {

    fun loadAndriiRtm(): PoseSequence2D = parse("fixtures/andrii_1_rtm.json")

    fun loadVideo2Rtm(): PoseSequence2D = parse("fixtures/video_2_rtm.json")

    private fun parse(path: String): PoseSequence2D = PoseJsonV2Parser.parse(loadResource(path))

    private fun loadResource(path: String): String {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: ClassLoader.getSystemResourceAsStream(path)
            ?: throw IllegalStateException("Test resource not found on classpath: $path")
        return stream.bufferedReader(Charsets.UTF_8).readText()
    }
}
```

`PoseJsonV2FixtureTest.kt`:
```kotlin
package com.ttcoachai.shared.io

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.models.Topology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoseJsonV2FixtureTest {

    @Test
    fun andriiFixtureParsesCleanly() {
        val seq = TestFixturesV2.loadAndriiRtm()
        assertEquals(Topology.COCO17, seq.topology)
        // L-02 guard: fixtures must be full-fps re-exports (≥20 fps), not the old 10 fps ones
        assertTrue(seq.intervalMs in 1..50, "fixture is ${seq.intervalMs}ms/frame — re-export at native fps (L-02)")
        assertEquals(seq.totalFrames, seq.frames.size)
        assertTrue(seq.frames.isNotEmpty())
        seq.frames.forEach { f ->
            assertTrue(f.keypoints.size == 17 || f.keypoints.isEmpty(),
                "frame ${f.frameIndex}: ${f.keypoints.size} keypoints")
            f.keypoints.forEach { kp ->
                assertTrue(kp.x in 0f..1f && kp.y in 0f..1f, "frame ${f.frameIndex}: coords out of [0,1]")
                assertTrue(kp.score in 0f..1f, "frame ${f.frameIndex}: score out of [0,1]")
            }
        }
    }

    @Test
    fun video2FixtureParsesCleanly() {
        val seq = TestFixturesV2.loadVideo2Rtm()
        assertEquals(Topology.COCO17, seq.topology)
        assertTrue(seq.intervalMs in 1..50, "fixture is ${seq.intervalMs}ms/frame — re-export at native fps (L-02)")
        assertEquals(712, seq.videoWidth)
        assertEquals(1280, seq.videoHeight)
        assertEquals(seq.totalFrames, seq.frames.size)
    }
}
```

- [ ] **Step 3: Run test to verify it fails, then passes**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2FixtureTest"`
Expected: PASS on first run if Tasks 1–2 are correct (the new code here is test-only). If it fails, the parser has a real bug against real data — fix the parser, not the test. Note: RTMPose normalized coords can rarely exceed [0,1] slightly when the model places a joint off-frame; if the coord assertion fails for that reason, relax that one assertion to `-0.5f..1.5f` and keep the score assertion strict.

- [ ] **Step 4: Commit**

```bash
git add scripts/poses/export_poses_rtmpose.py \
        shared/src/commonTest/resources/fixtures/andrii_1_rtm.json \
        shared/src/commonTest/resources/fixtures/video_2_rtm.json \
        Videos/andrii_1/andrii_1_poses_rtm.json \
        Videos/video_2/video_2_poses_rtm.json \
        shared/src/jvmTest/kotlin/com/ttcoachai/shared/TestFixturesV2.kt \
        shared/src/jvmTest/kotlin/com/ttcoachai/shared/io/PoseJsonV2FixtureTest.kt
git commit -m "test(shared): full-fps RTMPose fixtures + jvmTest loader (L-02 fixtures, L-08 rotation)"
```

---

### Task 4: In-plane angles (`AngleCalculations2D`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/AngleCalculations2D.kt`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/AngleCalculations2DTest.kt`

The five in-plane metrics from context doc §3/§4: elbow (shoulder–elbow–wrist), shoulder (hip–shoulder–elbow), knee bend (hip–knee–ankle), torso lean (shoulder-mid vs hip-mid, signed degrees from vertical), shoulder tilt (shoulder line vs horizon, folded to (−90°, 90°]). All score-gated; all take a single `xScale` factor (`ViewGeometry.xScale` = aspect ratio × camera-yaw correction, design note 9) — synthetic tests pass `1f`. `commonMain` has no `java.lang.Math` — use `kotlin.math` and `* 180 / PI`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AngleCalculations2DTest {

    /** All 17 keypoints at (0.5, 0.5) score 1.0, with positional overrides. */
    private fun kps(vararg overrides: Pair<Int, Keypoint2D>): List<Keypoint2D> {
        val base = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1.0f) }
        overrides.forEach { (i, kp) -> base[i] = kp }
        return base
    }

    @Test
    fun elbowAngleRightAngle() {
        // shoulder above elbow, wrist to the right of elbow → 90°
        val kp = kps(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.5f, 0.2f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.7f, 0.4f, 1f)
        )
        assertEquals(90f, AngleCalculations2D.elbowAngle(kp, Handedness.RIGHT, 1f)!!, 0.1f)
    }

    @Test
    fun elbowAngleStraightArm() {
        val kp = kps(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.5f, 0.2f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.5f, 0.6f, 1f)
        )
        assertEquals(180f, AngleCalculations2D.elbowAngle(kp, Handedness.RIGHT, 1f)!!, 0.1f)
    }

    @Test
    fun elbowAngleLeftHand() {
        val kp = kps(
            Coco17.LEFT_SHOULDER to Keypoint2D(0.5f, 0.2f, 1f),
            Coco17.LEFT_ELBOW to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.LEFT_WRIST to Keypoint2D(0.3f, 0.4f, 1f)
        )
        assertEquals(90f, AngleCalculations2D.elbowAngle(kp, Handedness.LEFT, 1f)!!, 0.1f)
    }

    @Test
    fun xScaleChangesTheAngle() {
        // 135° at xScale 1: elbow→shoulder (0,-0.2), elbow→wrist (0.2, 0.2)
        val kp = kps(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.5f, 0.2f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.7f, 0.6f, 1f)
        )
        assertEquals(135f, AngleCalculations2D.elbowAngle(kp, Handedness.RIGHT, 1f)!!, 0.1f)
        // xScale 0.5 → elbow→wrist becomes (0.1, 0.2) → cos = -0.04/(0.2·0.2236) → 153.43°
        assertEquals(153.43f, AngleCalculations2D.elbowAngle(kp, Handedness.RIGHT, 0.5f)!!, 0.5f)
    }

    @Test
    fun lowScoreGatesToNull() {
        val kp = kps(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.5f, 0.2f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.7f, 0.4f, 0.1f) // below default 0.3
        )
        assertNull(AngleCalculations2D.elbowAngle(kp, Handedness.RIGHT, 1f))
    }

    @Test
    fun missingKeypointsGateToNull() {
        assertNull(AngleCalculations2D.elbowAngle(emptyList(), Handedness.RIGHT, 1f))
    }

    @Test
    fun shoulderAngle() {
        // at shoulder: →hip (0, 0.3), →elbow (0.15, 0.05) → 71.57°
        val kp = kps(
            Coco17.RIGHT_HIP to Keypoint2D(0.5f, 0.6f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.5f, 0.3f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.65f, 0.35f, 1f)
        )
        assertEquals(71.57f, AngleCalculations2D.shoulderAngle(kp, Handedness.RIGHT, 1f)!!, 0.5f)
    }

    @Test
    fun kneeBendStraightAndBent() {
        val straight = kps(
            Coco17.RIGHT_HIP to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_KNEE to Keypoint2D(0.5f, 0.6f, 1f),
            Coco17.RIGHT_ANKLE to Keypoint2D(0.5f, 0.8f, 1f)
        )
        assertEquals(180f, AngleCalculations2D.kneeBend(straight, Handedness.RIGHT, 1f)!!, 0.1f)

        // knee→hip (0,-0.2), knee→ankle (0.1, 0.15) → 146.31°
        val bent = kps(
            Coco17.RIGHT_HIP to Keypoint2D(0.5f, 0.4f, 1f),
            Coco17.RIGHT_KNEE to Keypoint2D(0.5f, 0.6f, 1f),
            Coco17.RIGHT_ANKLE to Keypoint2D(0.6f, 0.75f, 1f)
        )
        assertEquals(146.31f, AngleCalculations2D.kneeBend(bent, Handedness.RIGHT, 1f)!!, 0.5f)
    }

    @Test
    fun torsoLeanVerticalIsZero() {
        val kp = kps(
            Coco17.NOSE to Keypoint2D(0.6f, 0.15f, 1f), // facing +x
            Coco17.LEFT_SHOULDER to Keypoint2D(0.45f, 0.2f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.55f, 0.2f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.45f, 0.6f, 1f),
            Coco17.RIGHT_HIP to Keypoint2D(0.55f, 0.6f, 1f)
        )
        assertEquals(0f, AngleCalculations2D.torsoLean(kp, 1f)!!, 0.1f)
    }

    @Test
    fun torsoLeanForwardIsPositive() {
        // shoulder-mid (0.6, 0.2), hip-mid (0.5, 0.6), facing +x → atan2(0.1, 0.4) = +14.04°
        val kp = kps(
            Coco17.NOSE to Keypoint2D(0.65f, 0.15f, 1f),
            Coco17.LEFT_SHOULDER to Keypoint2D(0.55f, 0.2f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.65f, 0.2f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.45f, 0.6f, 1f),
            Coco17.RIGHT_HIP to Keypoint2D(0.55f, 0.6f, 1f)
        )
        assertEquals(14.04f, AngleCalculations2D.torsoLean(kp, 1f)!!, 0.5f)
    }

    @Test
    fun torsoLeanSignIsFacingIndependent() {
        // L-04: the SAME forward lean, mirrored (player faces -x): shoulder-mid
        // (0.4, 0.2), hip-mid (0.5, 0.6), nose toward -x → still +14.04°, not -14.04°.
        val kp = kps(
            Coco17.NOSE to Keypoint2D(0.35f, 0.15f, 1f),
            Coco17.LEFT_SHOULDER to Keypoint2D(0.35f, 0.2f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.45f, 0.2f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.45f, 0.6f, 1f),
            Coco17.RIGHT_HIP to Keypoint2D(0.55f, 0.6f, 1f)
        )
        assertEquals(14.04f, AngleCalculations2D.torsoLean(kp, 1f)!!, 0.5f)
    }

    @Test
    fun torsoLeanNullWhenFacingIndeterminate() {
        // Nose dead-centered over the hips and ears at the same x → cannot orient;
        // a possibly-flipped sign must not be reported (trust rule).
        val kp = kps(
            Coco17.LEFT_SHOULDER to Keypoint2D(0.45f, 0.2f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.55f, 0.2f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.45f, 0.6f, 1f),
            Coco17.RIGHT_HIP to Keypoint2D(0.55f, 0.6f, 1f)
            // nose/ears stay at the kps() default x = 0.5 = hip-mid x
        )
        assertNull(AngleCalculations2D.torsoLean(kp, 1f))
    }

    @Test
    fun shoulderTiltLevelIsZeroAndFoldsToHalfPlane() {
        val level = kps(
            Coco17.LEFT_SHOULDER to Keypoint2D(0.4f, 0.5f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.6f, 0.5f, 1f)
        )
        assertEquals(0f, AngleCalculations2D.shoulderTilt(level, 1f)!!, 0.1f)

        // left→right (0.2, 0.1) → 26.57°
        val tilted = kps(
            Coco17.LEFT_SHOULDER to Keypoint2D(0.4f, 0.5f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.6f, 0.6f, 1f)
        )
        assertEquals(26.57f, AngleCalculations2D.shoulderTilt(tilted, 1f)!!, 0.5f)

        // reversed direction (player facing other way): left→right (-0.2, -0.1) → raw -153.43° → folds to 26.57°
        val reversed = kps(
            Coco17.LEFT_SHOULDER to Keypoint2D(0.6f, 0.5f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.4f, 0.4f, 1f)
        )
        assertEquals(26.57f, AngleCalculations2D.shoulderTilt(reversed, 1f)!!, 0.5f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.analysis.AngleCalculations2DTest"`
Expected: FAIL — unresolved reference `AngleCalculations2D`

- [ ] **Step 3: Write the implementation**

`AngleCalculations2D.kt`:
```kotlin
package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * In-plane (2D) joint angles over COCO-17 keypoints. Adaptation of the dot-product
 * approach in [AngleCalculations] to the RTMPose 2D topology.
 *
 * All functions:
 *  - return null when any required keypoint is missing or below [minScore]
 *    (no feedback on low-confidence frames — spec quality gate);
 *  - take xScale = ViewGeometry.xScale, the combined horizontal correction
 *    (aspect ratio, because schema-v2 x and y are normalized by different axes,
 *    × 1/cos(cameraYaw) foreshortening compensation); x-deltas are multiplied
 *    by it before any trig.
 */
object AngleCalculations2D {

    const val DEFAULT_MIN_SCORE = 0.3f
    private const val RAD_TO_DEG = (180.0 / PI).toFloat()
    private const val EPSILON = 1e-9f
    private const val FACING_EPSILON = 1e-3f

    /** Inner angle at [b] formed by segments b→a and b→c, in degrees [0, 180]. */
    fun angleDeg(a: Keypoint2D, b: Keypoint2D, c: Keypoint2D, xScale: Float): Float {
        val baX = (a.x - b.x) * xScale
        val baY = a.y - b.y
        val bcX = (c.x - b.x) * xScale
        val bcY = c.y - b.y
        val mag = hypot(baX, baY) * hypot(bcX, bcY)
        if (mag < EPSILON) return 0f
        val cos = ((baX * bcX + baY * bcY) / mag).coerceIn(-1f, 1f)
        return acos(cos) * RAD_TO_DEG
    }

    /** Elbow angle: shoulder–elbow–wrist. 180° = straight arm. */
    fun elbowAngle(
        kp: List<Keypoint2D>,
        handedness: Handedness,
        xScale: Float,
        minScore: Float = DEFAULT_MIN_SCORE
    ): Float? = jointAngle(
        kp, Coco17.shoulder(handedness), Coco17.elbow(handedness), Coco17.wrist(handedness),
        xScale, minScore
    )

    /** Shoulder angle: hip–shoulder–elbow (upper arm vs torso). */
    fun shoulderAngle(
        kp: List<Keypoint2D>,
        handedness: Handedness,
        xScale: Float,
        minScore: Float = DEFAULT_MIN_SCORE
    ): Float? = jointAngle(
        kp, Coco17.hip(handedness), Coco17.shoulder(handedness), Coco17.elbow(handedness),
        xScale, minScore
    )

    /** Knee bend: hip–knee–ankle. 180° = straight leg. */
    fun kneeBend(
        kp: List<Keypoint2D>,
        handedness: Handedness,
        xScale: Float,
        minScore: Float = DEFAULT_MIN_SCORE
    ): Float? = jointAngle(
        kp, Coco17.hip(handedness), Coco17.knee(handedness), Coco17.ankle(handedness),
        xScale, minScore
    )

    /**
     * Torso lean: signed angle of the hip-mid → shoulder-mid line from vertical.
     * 0 = upright; POSITIVE = leaning toward the player's facing direction (forward
     * lean), independent of which way they face on screen (DESIGN_LIMITATIONS L-04:
     * an image-relative sign would give the opposite cue to a player standing the
     * other way). Facing comes from the nose (fallback: ear midpoint) relative to
     * hip-mid; returns null when facing is indeterminate (head keypoints gated or
     * dead-centered over the hips) — no measurement beats a possibly-flipped one.
     */
    fun torsoLean(
        kp: List<Keypoint2D>,
        xScale: Float,
        minScore: Float = DEFAULT_MIN_SCORE
    ): Float? {
        val ls = scored(kp, Coco17.LEFT_SHOULDER, minScore) ?: return null
        val rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore) ?: return null
        val lh = scored(kp, Coco17.LEFT_HIP, minScore) ?: return null
        val rh = scored(kp, Coco17.RIGHT_HIP, minScore) ?: return null
        val hipMidX = (lh.x + rh.x) / 2f
        val facing = facingSign(kp, hipMidX, minScore) ?: return null
        val dx = ((ls.x + rs.x) / 2f - hipMidX) * xScale
        // image y grows downward; -(shY - hpY) makes "up" positive
        val dy = -((ls.y + rs.y) / 2f - (lh.y + rh.y) / 2f)
        if (hypot(dx, dy) < EPSILON) return null
        return atan2(dx * facing, dy) * RAD_TO_DEG
    }

    /** +1 = facing +x, -1 = facing -x, null = indeterminate (L-04 sign normalizer). */
    private fun facingSign(kp: List<Keypoint2D>, hipMidX: Float, minScore: Float): Float? {
        val headX = scored(kp, Coco17.NOSE, minScore)?.x
            ?: run {
                val le = scored(kp, Coco17.LEFT_EAR, minScore)
                val re = scored(kp, Coco17.RIGHT_EAR, minScore)
                if (le != null && re != null) (le.x + re.x) / 2f else null
            }
            ?: return null
        val offset = headX - hipMidX
        if (abs(offset) < FACING_EPSILON) return null
        return if (offset > 0f) 1f else -1f
    }

    /**
     * Shoulder tilt vs horizon, folded to (-90°, 90°] so the result is independent
     * of which way the player faces. 0 = level shoulders.
     */
    fun shoulderTilt(
        kp: List<Keypoint2D>,
        xScale: Float,
        minScore: Float = DEFAULT_MIN_SCORE
    ): Float? {
        val ls = scored(kp, Coco17.LEFT_SHOULDER, minScore) ?: return null
        val rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore) ?: return null
        val dx = (rs.x - ls.x) * xScale
        val dy = rs.y - ls.y
        if (hypot(dx, dy) < EPSILON) return null
        var deg = atan2(dy, dx) * RAD_TO_DEG
        if (deg > 90f) deg -= 180f
        if (deg <= -90f) deg += 180f
        return deg
    }

    private fun jointAngle(
        kp: List<Keypoint2D>,
        aIdx: Int,
        bIdx: Int,
        cIdx: Int,
        xScale: Float,
        minScore: Float
    ): Float? {
        val a = scored(kp, aIdx, minScore) ?: return null
        val b = scored(kp, bIdx, minScore) ?: return null
        val c = scored(kp, cIdx, minScore) ?: return null
        return angleDeg(a, b, c, xScale)
    }

    private fun scored(kp: List<Keypoint2D>, idx: Int, minScore: Float): Keypoint2D? {
        val p = kp.getOrNull(idx) ?: return null
        return if (p.score >= minScore) p else null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.analysis.AngleCalculations2DTest"`
Expected: PASS (13 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/AngleCalculations2D.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/AngleCalculations2DTest.kt
git commit -m "feat(shared): score-gated xScale-corrected in-plane angles for COCO-17"
```

---

### Task 5: Wrist-speed stroke detector (`StrokeDetector2D`) — synthetic tests

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/StrokeDetector2D.kt`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/detection/StrokeDetector2DTest.kt`

Algorithm (context doc §4: "stroke detection: local maximum of wrist speed in the buffer"):
1. per-frame wrist displacement (xScale-corrected), 0 when either endpoint is score-gated; normalized to **torso-lengths per second** — torso = median shoulder-mid→hip-mid distance over the sequence (DESIGN_LIMITATIONS **L-01**: image-coord thresholds don't transfer across camera distance/zoom; per-second units make them fps-independent);
2. centered moving-average smoothing, window in **ms** (`smoothingWindowMs`, default 300);
3. local maxima above `minPeakSpeed` (torso-lengths/sec), strictly greater than everything earlier in a `±peakWindowRadiusMs` window (first-of-plateau wins), with `minPeakGapMs` refractory;
4. boundaries: walk out from the peak while smoothed speed stays above `boundaryFraction × peakSpeed`.

All windows are ms and converted to frame counts via the `intervalMs` parameter at detect time (DESIGN_LIMITATIONS **L-02**: Phase 3 capture fps is configurable 30/60/120 — frame-count tuning would silently change meaning with every fps setting).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttcoachai.shared.detection

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeDetector2DTest {

    /**
     * Frames where only the right wrist x moves; shoulders/hips fixed with torso
     * length 0.25, so speeds are well-defined in torso-lengths/sec. Peak raw speed
     * of [singleStrokeXs] at 100 ms interval: 0.06 / 0.25 / 0.1 s = 2.4 torso/s.
     */
    private fun framesFromWristXs(xs: List<Float>, intervalMs: Long = 100L): List<PoseFrame2D> =
        xs.mapIndexed { i, wx ->
            val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
            kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.49f, 0.30f, 1f)
            kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.51f, 0.30f, 1f)
            kp[Coco17.LEFT_HIP] = Keypoint2D(0.49f, 0.55f, 1f)
            kp[Coco17.RIGHT_HIP] = Keypoint2D(0.51f, 0.55f, 1f)
            kp[Coco17.RIGHT_WRIST] = Keypoint2D(wx, 0.5f, 1f)
            PoseFrame2D(frameIndex = i, timestampMs = i * intervalMs, keypoints = kp)
        }

    // still — accelerate to peak — decelerate — still
    private val singleStrokeXs = listOf(
        0.50f, 0.50f, 0.50f, 0.50f,
        0.51f, 0.53f, 0.57f, 0.63f, 0.68f, 0.71f, 0.72f,
        0.72f, 0.72f, 0.72f, 0.72f
    )

    @Test
    fun detectsSingleStroke() {
        val strokes = StrokeDetector2D().detect(framesFromWristXs(singleStrokeXs), Handedness.RIGHT, 1f, 100L)
        assertEquals(1, strokes.size)
        val s = strokes[0]
        // raw speed peaks at frame 7 (0.057→0.063 = 0.06 → 2.4 torso/s); smoothing may shift ±1
        assertTrue(s.peakFrame in 6..8, "peak at ${s.peakFrame}")
        assertTrue(s.startFrame < s.peakFrame, "start ${s.startFrame} before peak")
        assertTrue(s.endFrame > s.peakFrame, "end ${s.endFrame} after peak")
        assertEquals(0, s.strokeIndex)
    }

    @Test
    fun detectsTwoStrokesWithGap() {
        val back = singleStrokeXs.reversed() // return swing of equal magnitude
        val xs = singleStrokeXs + back
        val strokes = StrokeDetector2D().detect(framesFromWristXs(xs), Handedness.RIGHT, 1f, 100L)
        assertEquals(2, strokes.size)
        assertTrue(strokes[1].peakFrame - strokes[0].peakFrame >= 5)
        assertEquals(listOf(0, 1), strokes.map { it.strokeIndex })
    }

    @Test
    fun subThresholdJitterYieldsNoStrokes() {
        // 0.004/frame jitter = 0.16 torso/s — far below the 1.0 torso/s default
        val xs = List(30) { 0.5f + (if (it % 2 == 0) 0.002f else -0.002f) }
        val strokes = StrokeDetector2D().detect(framesFromWristXs(xs), Handedness.RIGHT, 1f, 100L)
        assertEquals(0, strokes.size)
    }

    @Test
    fun msBasedTuningSurvivesFpsChange() {
        // The same motion sampled at 50 ms (linear 2× resample): displacement per
        // frame halves but torso-lengths/SEC are unchanged, and ms windows convert
        // to 2× the frame counts — still exactly one stroke. Frame-based tuning
        // would halve every window's time span and break this (L-02).
        val xs50 = singleStrokeXs.flatMapIndexed { i, x ->
            if (i == singleStrokeXs.lastIndex) listOf(x)
            else listOf(x, (x + singleStrokeXs[i + 1]) / 2f)
        }
        val strokes = StrokeDetector2D()
            .detect(framesFromWristXs(xs50, intervalMs = 50L), Handedness.RIGHT, 1f, 50L)
        assertEquals(1, strokes.size)
    }

    @Test
    fun lowScoreWristFramesContributeZeroSpeed() {
        val frames = framesFromWristXs(singleStrokeXs).map { f ->
            val kp = f.keypoints.toMutableList()
            kp[Coco17.RIGHT_WRIST] = kp[Coco17.RIGHT_WRIST].copy(score = 0.1f)
            f.copy(keypoints = kp)
        }
        assertEquals(0, StrokeDetector2D().detect(frames, Handedness.RIGHT, 1f, 100L).size)
    }

    @Test
    fun emptyAndTinyInputsAreSafe() {
        assertEquals(0, StrokeDetector2D().detect(emptyList(), Handedness.RIGHT, 1f, 100L).size)
        assertEquals(0, StrokeDetector2D().detect(framesFromWristXs(listOf(0.5f)), Handedness.RIGHT, 1f, 100L).size)
    }

    @Test
    fun detectionIsDeterministic() {
        val frames = framesFromWristXs(singleStrokeXs)
        val a = StrokeDetector2D().detect(frames, Handedness.RIGHT, 1f, 100L)
        val b = StrokeDetector2D().detect(frames, Handedness.RIGHT, 1f, 100L)
        assertEquals(a, b)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.detection.StrokeDetector2DTest"`
Expected: FAIL — unresolved reference `StrokeDetector2D`

- [ ] **Step 3: Write the implementation**

`StrokeDetector2D.kt`:
```kotlin
package com.ttcoachai.shared.detection

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import kotlin.math.hypot

/**
 * Stroke detection via wrist-speed local maximum (context doc §4), adapted from the
 * phase-FSM approach of [StrokePhaseDetector] to the 2D COCO topology. Batch API for
 * the fixture-driven phase; the smoothed-speed core is streaming-compatible (Phase 3).
 *
 * Units (DESIGN_LIMITATIONS L-01/L-02):
 *  - speeds are in TORSO-LENGTHS PER SECOND — invariant to camera distance/zoom
 *    (L-01) and to capture fps (L-02); torso = median xScale-corrected
 *    shoulder-mid→hip-mid distance over the sequence;
 *  - all tuning windows are in MILLISECONDS, converted to frame counts via
 *    [detect]'s intervalMs — Phase 3 capture fps is configurable 30/60/120, so
 *    frame-count tuning would silently change meaning with every fps setting.
 */
class StrokeDetector2D(
    private val minScore: Float = 0.3f,
    private val smoothingWindowMs: Long = 300,
    private val peakWindowRadiusMs: Long = 300,
    /** Torso-lengths per second. */
    private val minPeakSpeed: Float = 1.0f,
    private val boundaryFraction: Float = 0.3f,
    private val minPeakGapMs: Long = 500
) {

    fun detect(
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        xScale: Float,
        intervalMs: Long
    ): List<Stroke2D> {
        require(intervalMs > 0) { "intervalMs must be > 0, got $intervalMs" }
        if (frames.size < 2) return emptyList()
        val torsoLen = medianTorsoLength(frames, xScale) ?: return emptyList()

        val speed = smooth(
            rawWristSpeeds(frames, handedness, xScale, torsoLen, intervalMs),
            window = framesFor(smoothingWindowMs, intervalMs)
        )
        val peaks = findPeaks(
            speed,
            radius = framesFor(peakWindowRadiusMs, intervalMs),
            minGap = framesFor(minPeakGapMs, intervalMs)
        )
        return peaks.mapIndexed { idx, p ->
            val floor = speed[p] * boundaryFraction
            var start = p
            while (start > 0 && speed[start - 1] > floor) start--
            var end = p
            while (end < speed.lastIndex && speed[end + 1] > floor) end++
            Stroke2D(
                strokeIndex = idx,
                startFrame = start,
                peakFrame = p,
                endFrame = end,
                peakSpeed = speed[p]
            )
        }
    }

    /** ms → frame count at the given interval, never below 1. */
    private fun framesFor(ms: Long, intervalMs: Long): Int =
        (ms / intervalMs).toInt().coerceAtLeast(1)

    private fun rawWristSpeeds(
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        xScale: Float,
        torsoLen: Float,
        intervalMs: Long
    ): FloatArray {
        val wristIdx = Coco17.wrist(handedness)
        val dtSec = intervalMs / 1000f
        val raw = FloatArray(frames.size)
        for (i in 1 until frames.size) {
            val prev = frames[i - 1].keypoints.getOrNull(wristIdx)
            val curr = frames[i].keypoints.getOrNull(wristIdx)
            raw[i] = if (prev == null || curr == null || prev.score < minScore || curr.score < minScore) {
                0f
            } else {
                hypot((curr.x - prev.x) * xScale, curr.y - prev.y) / torsoLen / dtSec
            }
        }
        return raw
    }

    /**
     * Median xScale-corrected shoulder-mid→hip-mid distance over the sequence;
     * null if never measurable (then no strokes can be detected — L-01 normalizer).
     * CameraAngleEstimator has a sibling computation parameterized by aspectRatio.
     */
    private fun medianTorsoLength(frames: List<PoseFrame2D>, xScale: Float): Float? {
        val lens = frames.mapNotNull { f ->
            val kp = f.keypoints
            val ls = kp.getOrNull(Coco17.LEFT_SHOULDER)?.takeIf { it.score >= minScore } ?: return@mapNotNull null
            val rs = kp.getOrNull(Coco17.RIGHT_SHOULDER)?.takeIf { it.score >= minScore } ?: return@mapNotNull null
            val lh = kp.getOrNull(Coco17.LEFT_HIP)?.takeIf { it.score >= minScore } ?: return@mapNotNull null
            val rh = kp.getOrNull(Coco17.RIGHT_HIP)?.takeIf { it.score >= minScore } ?: return@mapNotNull null
            val len = hypot(
                ((ls.x + rs.x) - (lh.x + rh.x)) / 2f * xScale,
                ((ls.y + rs.y) - (lh.y + rh.y)) / 2f
            )
            if (len < MIN_TORSO_LEN) null else len
        }
        if (lens.isEmpty()) return null
        val sorted = lens.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    private fun smooth(raw: FloatArray, window: Int): FloatArray {
        if (window <= 1) return raw
        val half = window / 2
        val out = FloatArray(raw.size)
        for (i in raw.indices) {
            val lo = (i - half).coerceAtLeast(0)
            val hi = (i + half).coerceAtMost(raw.lastIndex)
            var sum = 0f
            for (j in lo..hi) sum += raw[j]
            out[i] = sum / (hi - lo + 1)
        }
        return out
    }

    private fun findPeaks(speed: FloatArray, radius: Int, minGap: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        for (i in speed.indices) {
            if (speed[i] < minPeakSpeed) continue
            val lo = (i - radius).coerceAtLeast(0)
            val hi = (i + radius).coerceAtMost(speed.lastIndex)
            var isPeak = true
            for (j in lo..hi) {
                // strictly greater than earlier frames → first index of a plateau wins
                if (j < i && speed[j] >= speed[i]) { isPeak = false; break }
                if (j > i && speed[j] > speed[i]) { isPeak = false; break }
            }
            if (isPeak && (peaks.isEmpty() || i - peaks.last() >= minGap)) {
                peaks.add(i)
            }
        }
        return peaks
    }

    private companion object {
        const val MIN_TORSO_LEN = 1e-4f
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.detection.StrokeDetector2DTest"`
Expected: PASS (7 tests). If `detectsSingleStroke` fails on the peak index, print the smoothed speeds and adjust the asserted range by at most ±1 — the smoothing math, not the test intent, decides the exact frame.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/StrokeDetector2D.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/detection/StrokeDetector2DTest.kt
git commit -m "feat(shared): wrist-speed local-maximum stroke detector for 2D poses"
```

---

### Task 6: Stroke detector on real RTMPose fixture

**Files:**
- Test: `shared/src/jvmTest/kotlin/com/ttcoachai/shared/detection/StrokeDetector2DFixtureTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.ttcoachai.shared.detection

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.models.Handedness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeDetector2DFixtureTest {

    @Test
    fun detectsStrokesOnAndriiRtmFixture() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val strokes = StrokeDetector2D().detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)

        // Diagnostic for founder QA against poses_viewer (footage predates the
        // camera-placement protocol; counts are pipeline checks, not tuning).
        println("andrii_1_rtm: ${strokes.size} strokes at peaks ${strokes.map { it.peakFrame }}")

        assertTrue(strokes.isNotEmpty(), "expected at least one stroke in andrii_1_rtm")
        strokes.forEach { s ->
            assertTrue(s.startFrame <= s.peakFrame && s.peakFrame <= s.endFrame, "ordered boundaries: $s")
            assertTrue(s.endFrame < seq.frames.size, "endFrame in range: $s")
            assertTrue(s.peakSpeed > 0f)
        }
        strokes.zipWithNext().forEach { (a, b) ->
            assertTrue(a.peakFrame < b.peakFrame, "strokes ordered by time")
        }
    }

    @Test
    fun fixtureDetectionIsDeterministic() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val a = StrokeDetector2D().detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)
        val b = StrokeDetector2D().detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)
        assertEquals(a, b)
    }
}
```

- [ ] **Step 2: Run it**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.detection.StrokeDetector2DFixtureTest"`
Expected: PASS, with the stroke count printed. **If zero strokes are found**, the default `minPeakSpeed = 1.0` torso-lengths/sec is too high for this footage: print the max smoothed speed (temporarily expose it or compute inline in the test), set `minPeakSpeed` to roughly half the observed typical peak, and re-run. This threshold tuning loop is the point of having a real fixture — record the final value in the `StrokeDetector2D` kdoc. Because the units are torso-normalized and per-second (L-01/L-02), the tuned value is expected to transfer across videos and frame rates — if it doesn't, that's a finding worth a registry entry, not a silent re-tune.

- [ ] **Step 3: Note the printed stroke count** — it is the RAW detector count used as `KNOWN_REP_COUNT` in Task 14 (an upper bound there: `RepFilter` from Task 13 may drop junk peaks downstream). Eyeball-check the peak frames against the same video in poses_viewer (`cd poses_viewer && npm run dev`, load `andrii_1`) — the `viewer-qa` skill covers this.

- [ ] **Step 4: Commit**

```bash
git add shared/src/jvmTest/kotlin/com/ttcoachai/shared/detection/StrokeDetector2DFixtureTest.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/StrokeDetector2D.kt
git commit -m "test(shared): stroke detection on real RTMPose fixture (threshold tuned)"
```

---

### Task 7: Metric extraction + sanity bounds (`DrillMetrics`, `SanityBounds`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillMetrics.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/SanityBounds.kt`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/DrillMetricsTest.kt`

Sanity bounds are the spec's "hand-coded values survive only as sanity bounds (e.g. elbow 20–170°)": values outside them are tracking glitches and are **dropped** (no metric, no feedback), never coached on.

Two extraction entry points: `extractAtFrame` (single-frame primitive) and `extractAtPeak` — the per-rep API that medians each metric over a ±70 ms window around the wrist-speed peak (DESIGN_LIMITATIONS **L-05**: keypoints are unsmoothed; one raw frame feeds RTMPose jitter straight into the baseline). Orchestrators use `extractAtPeak`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrillMetricsTest {

    private fun frame(vararg overrides: Pair<Int, Keypoint2D>): PoseFrame2D {
        val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
        overrides.forEach { (i, p) -> kp[i] = p }
        return PoseFrame2D(0, 0L, kp)
    }

    @Test
    fun extractsAllFiveMetricsFromGoodFrame() {
        val f = frame(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.50f, 0.30f, 1f),
            Coco17.LEFT_SHOULDER to Keypoint2D(0.48f, 0.30f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.55f, 0.42f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.65f, 0.40f, 1f),
            Coco17.RIGHT_HIP to Keypoint2D(0.50f, 0.55f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.48f, 0.55f, 1f),
            Coco17.RIGHT_KNEE to Keypoint2D(0.50f, 0.72f, 1f),
            Coco17.RIGHT_ANKLE to Keypoint2D(0.52f, 0.90f, 1f)
        )
        val m = DrillMetrics.extractAtFrame(f, Handedness.RIGHT, 1f)
        assertEquals(
            setOf(
                DrillMetrics.METRIC_ELBOW_ANGLE,
                DrillMetrics.METRIC_SHOULDER_ANGLE,
                DrillMetrics.METRIC_KNEE_BEND,
                DrillMetrics.METRIC_TORSO_LEAN,
                DrillMetrics.METRIC_SHOULDER_TILT
            ),
            m.keys
        )
    }

    @Test
    fun lowScoreJointDropsOnlyAffectedMetrics() {
        val f = frame(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.50f, 0.30f, 1f),
            Coco17.LEFT_SHOULDER to Keypoint2D(0.48f, 0.30f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.55f, 0.42f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.65f, 0.40f, 0.1f), // gated
            Coco17.RIGHT_HIP to Keypoint2D(0.50f, 0.55f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.48f, 0.55f, 1f),
            Coco17.RIGHT_KNEE to Keypoint2D(0.50f, 0.72f, 1f),
            Coco17.RIGHT_ANKLE to Keypoint2D(0.52f, 0.90f, 1f)
        )
        val m = DrillMetrics.extractAtFrame(f, Handedness.RIGHT, 1f)
        assertFalse(DrillMetrics.METRIC_ELBOW_ANGLE in m, "elbow needs the wrist")
        assertTrue(DrillMetrics.METRIC_KNEE_BEND in m, "knee unaffected by wrist score")
    }

    @Test
    fun insaneValueIsDropped() {
        // Fully straight arm = 180° elbow — outside the 20–170° sanity band → dropped
        val f = frame(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.50f, 0.20f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.50f, 0.40f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.50f, 0.60f, 1f)
        )
        val m = DrillMetrics.extractAtFrame(f, Handedness.RIGHT, 1f)
        assertFalse(DrillMetrics.METRIC_ELBOW_ANGLE in m)
    }

    @Test
    fun emptyFrameYieldsNoMetrics() {
        assertTrue(DrillMetrics.extractAtFrame(PoseFrame2D(0, 0L, emptyList()), Handedness.RIGHT, 1f).isEmpty())
    }

    @Test
    fun sanityBoundsSpotChecks() {
        assertTrue(SanityBounds.isSane(DrillMetrics.METRIC_ELBOW_ANGLE, 90.0))
        assertFalse(SanityBounds.isSane(DrillMetrics.METRIC_ELBOW_ANGLE, 19.0))
        assertFalse(SanityBounds.isSane(DrillMetrics.METRIC_ELBOW_ANGLE, 171.0))
        assertTrue(SanityBounds.isSane("unknown_metric", 12345.0), "unknown metrics pass through")
    }

    /** 90°-elbow frame used by the peak-window tests. */
    private fun goodArmFrame(idx: Int, wrist: Keypoint2D = Keypoint2D(0.70f, 0.42f, 1f)): PoseFrame2D {
        val f = frame(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.50f, 0.22f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.50f, 0.42f, 1f),
            Coco17.RIGHT_WRIST to wrist
        )
        return f.copy(frameIndex = idx, timestampMs = idx * 33L)
    }

    @Test
    fun peakMetricsAreMedianSmoothedOverTheWindow() {
        // L-05: middle frame (the peak) has a jittered wrist that alone reads ~135°;
        // the ±70 ms window at 33 ms holds 5 frames and the median must ignore it.
        val frames = listOf(
            goodArmFrame(0), goodArmFrame(1),
            goodArmFrame(2, wrist = Keypoint2D(0.64f, 0.56f, 1f)), // jitter → ≈135°
            goodArmFrame(3), goodArmFrame(4)
        )
        val m = DrillMetrics.extractAtPeak(frames, peakFrame = 2, Handedness.RIGHT, 1f, intervalMs = 33L)
        assertEquals(90.0, m[DrillMetrics.METRIC_ELBOW_ANGLE]!!, 1.0)
    }

    @Test
    fun peakWindowDegradesToSingleFrameAtCoarseIntervals() {
        // radius = 70 ms / 100 ms = 0 frames → identical to extractAtFrame
        val frames = listOf(goodArmFrame(0), goodArmFrame(1), goodArmFrame(2))
        val atPeak = DrillMetrics.extractAtPeak(frames, peakFrame = 1, Handedness.RIGHT, 1f, intervalMs = 100L)
        val atFrame = DrillMetrics.extractAtFrame(frames[1], Handedness.RIGHT, 1f)
        assertEquals(atFrame.keys, atPeak.keys)
        atFrame.forEach { (k, v) -> assertEquals(v, atPeak[k]!!, 1e-9) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.DrillMetricsTest"`
Expected: FAIL — unresolved references

- [ ] **Step 3: Write the implementation**

`SanityBounds.kt`:
```kotlin
package com.ttcoachai.shared.drill

/**
 * Hand-coded anatomical sanity bounds (design decision 2: hand-coded values survive
 * only as sanity bounds). A value outside its band is a tracking glitch — the metric
 * is dropped for that frame, never coached on. Personal baselines, not these bounds,
 * define "correct".
 */
object SanityBounds {

    private val bounds: Map<String, ClosedFloatingPointRange<Double>> = mapOf(
        DrillMetrics.METRIC_ELBOW_ANGLE to 20.0..170.0,    // spec example
        DrillMetrics.METRIC_SHOULDER_ANGLE to 5.0..175.0,
        DrillMetrics.METRIC_KNEE_BEND to 60.0..180.0,
        DrillMetrics.METRIC_TORSO_LEAN to -60.0..60.0,
        DrillMetrics.METRIC_SHOULDER_TILT to -60.0..60.0
    )

    /** Metrics without a registered band pass through (bounds are opt-in). */
    fun isSane(metricKey: String, value: Double): Boolean =
        bounds[metricKey]?.contains(value) ?: true
}
```

`DrillMetrics.kt`:
```kotlin
package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.AngleCalculations2D
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseFrame2D

/**
 * Per-frame extraction of the five Phase 2 in-plane metrics (context doc §3) at the
 * stroke's wrist-speed peak. Score-gated per joint; sanity-bounded per value.
 */
object DrillMetrics {

    const val METRIC_ELBOW_ANGLE = "elbow_angle"
    const val METRIC_SHOULDER_ANGLE = "shoulder_angle"
    const val METRIC_KNEE_BEND = "knee_bend"
    const val METRIC_TORSO_LEAN = "torso_lean"
    const val METRIC_SHOULDER_TILT = "shoulder_tilt"

    val ALL_KEYS = listOf(
        METRIC_ELBOW_ANGLE, METRIC_SHOULDER_ANGLE, METRIC_KNEE_BEND,
        METRIC_TORSO_LEAN, METRIC_SHOULDER_TILT
    )

    fun extractAtFrame(
        frame: PoseFrame2D,
        handedness: Handedness,
        xScale: Float,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE
    ): Map<String, Double> {
        val kp = frame.keypoints
        if (kp.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, Double>()
        AngleCalculations2D.elbowAngle(kp, handedness, xScale, minScore)
            ?.let { out[METRIC_ELBOW_ANGLE] = it.toDouble() }
        AngleCalculations2D.shoulderAngle(kp, handedness, xScale, minScore)
            ?.let { out[METRIC_SHOULDER_ANGLE] = it.toDouble() }
        AngleCalculations2D.kneeBend(kp, handedness, xScale, minScore)
            ?.let { out[METRIC_KNEE_BEND] = it.toDouble() }
        AngleCalculations2D.torsoLean(kp, xScale, minScore)
            ?.let { out[METRIC_TORSO_LEAN] = it.toDouble() }
        AngleCalculations2D.shoulderTilt(kp, xScale, minScore)
            ?.let { out[METRIC_SHOULDER_TILT] = it.toDouble() }
        return out.filter { (k, v) -> SanityBounds.isSane(k, v) }
    }

    /** Half-width of the peak-metric smoothing window, in ms (±2 frames at 30 fps). */
    const val DEFAULT_PEAK_RADIUS_MS = 70L

    /**
     * Per-rep metrics: MEDIAN of each metric over the frames within ±[radiusMs] of
     * [peakFrame] (DESIGN_LIMITATIONS L-05 — keypoints are unsmoothed, so a single
     * raw frame feeds RTMPose jitter straight into the baseline). Median, not mean:
     * one junk frame inside the window must not shift the rep's value. Each frame
     * is score-gated and sanity-bounded independently via [extractAtFrame]; at
     * coarse intervals (radius < interval) this degrades to the single peak frame.
     */
    fun extractAtPeak(
        frames: List<PoseFrame2D>,
        peakFrame: Int,
        handedness: Handedness,
        xScale: Float,
        intervalMs: Long,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
        radiusMs: Long = DEFAULT_PEAK_RADIUS_MS
    ): Map<String, Double> {
        require(intervalMs > 0) { "intervalMs must be > 0, got $intervalMs" }
        val radius = (radiusMs / intervalMs).toInt()
        val lo = (peakFrame - radius).coerceAtLeast(0)
        val hi = (peakFrame + radius).coerceAtMost(frames.lastIndex)
        val byKey = mutableMapOf<String, MutableList<Double>>()
        for (i in lo..hi) {
            for ((key, value) in extractAtFrame(frames[i], handedness, xScale, minScore)) {
                byKey.getOrPut(key) { mutableListOf() }.add(value)
            }
        }
        return byKey.mapValues { (_, values) ->
            val sorted = values.sorted()
            val mid = sorted.size / 2
            if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.DrillMetricsTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillMetrics.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/SanityBounds.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/DrillMetricsTest.kt
git commit -m "feat(shared): drill metric extraction with hand-coded sanity bounds"
```

---

### Task 8: `BaselineDeriver.deriveFromMetrics` refactor

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineDeriver.kt:57-108`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/BaselineDeriverFromMetricsTest.kt`

Pure extraction: the body of `derive(...)` from `val initialMetricStats = ...` down becomes the new public `deriveFromMetrics(...)`; `derive(...)` keeps its requires + `AnalysisResult`/`DetectedStroke` mapping and delegates. **Existing `BaselineDeriverTest` (jvmTest) must stay green — the 003 calibration path must not change behavior.**

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttcoachai.shared.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BaselineDeriverFromMetricsTest {

    private val elbow = "elbow_angle"
    private val total = "stroke_total_ms"

    private fun rep(elbowDeg: Double) = mapOf(elbow to elbowDeg)
    private fun phases(ms: Double) = mapOf(total to ms)

    @Test
    fun derivesStatsFromGenericMetricMaps() {
        val baseline = BaselineDeriver.deriveFromMetrics(
            repMetrics = listOf(rep(100.0), rep(110.0), rep(120.0)),
            repPhaseDurations = listOf(phases(800.0), phases(820.0), phases(840.0)),
            drillType = "forehand_drive",
            createdAtMs = 1_000L,
            drillerHandedness = "right",
            minRepCount = 3
        )
        assertEquals(3, baseline.repCount)
        assertEquals(110.0, baseline.metricStats[elbow]!!.mean, 1e-9)
        assertEquals(10.0, baseline.metricStats[elbow]!!.std, 1e-9)
        assertEquals(820.0, baseline.phaseDurationsMs[total]!!.mean, 1e-9)
        assertEquals("forehand_drive", baseline.drillType)
        assertEquals("right", baseline.drillerHandedness)
    }

    @Test
    fun excludesOutlierRep() {
        // 5 tight reps + 1 far outlier; 2σ one-pass exclusion must drop the outlier
        val metrics = listOf(rep(100.0), rep(101.0), rep(99.0), rep(100.5), rep(99.5), rep(160.0))
        val durations = List(6) { phases(800.0) }
        val baseline = BaselineDeriver.deriveFromMetrics(
            metrics, durations, "forehand_drive", 0L, null, minRepCount = 3
        )
        assertEquals(listOf(5), baseline.excludedRepIndices)
        assertEquals(5, baseline.repCount)
        assertTrue(baseline.metricStats[elbow]!!.mean < 102.0)
    }

    @Test
    fun throwsBelowMinRepCount() {
        assertFailsWith<IllegalArgumentException> {
            BaselineDeriver.deriveFromMetrics(
                listOf(rep(100.0), rep(110.0)),
                listOf(phases(800.0), phases(810.0)),
                "forehand_drive", 0L, null, minRepCount = 3
            )
        }
    }

    @Test
    fun throwsOnEmptyOrMismatchedInput() {
        assertFailsWith<IllegalArgumentException> {
            BaselineDeriver.deriveFromMetrics(emptyList(), emptyList(), "x", 0L, null)
        }
        assertFailsWith<IllegalArgumentException> {
            BaselineDeriver.deriveFromMetrics(listOf(rep(1.0)), emptyList(), "x", 0L, null)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.analysis.BaselineDeriverFromMetricsTest"`
Expected: FAIL — unresolved reference `deriveFromMetrics`

- [ ] **Step 3: Refactor**

In `BaselineDeriver.kt`, replace the body of `derive(...)` (keep its signature and kdoc) and add the new function directly below it:

```kotlin
    fun derive(
        strokes: List<DetectedStroke>,
        analyses: List<AnalysisResult>,
        frameIntervalMs: Long,
        drillType: String,
        createdAtMs: Long,
        drillerHandedness: String? = null,
        minRepCount: Int = DEFAULT_MIN_REPS,
        outlierSigmaThreshold: Double = DEFAULT_OUTLIER_SIGMA
    ): PersonalBaseline {
        require(strokes.isNotEmpty()) { "Cannot derive baseline from empty stroke list" }
        require(strokes.size == analyses.size) {
            "strokes (${strokes.size}) and analyses (${analyses.size}) must be parallel lists"
        }
        require(frameIntervalMs > 0) { "frameIntervalMs must be > 0, got $frameIntervalMs" }

        return deriveFromMetrics(
            repMetrics = analyses.map { extractMetricValues(it) },
            repPhaseDurations = strokes.map { extractPhaseDurations(it, frameIntervalMs) },
            drillType = drillType,
            createdAtMs = createdAtMs,
            drillerHandedness = drillerHandedness,
            minRepCount = minRepCount,
            outlierSigmaThreshold = outlierSigmaThreshold
        )
    }

    /**
     * Source-agnostic core of baseline derivation: one metric map + one phase-duration
     * map per rep. The 2D drill pipeline (Stage: 2D pivot Phase 2) feeds this directly;
     * the legacy 003 path feeds it via [derive].
     */
    fun deriveFromMetrics(
        repMetrics: List<Map<String, Double>>,
        repPhaseDurations: List<Map<String, Double>>,
        drillType: String,
        createdAtMs: Long,
        drillerHandedness: String? = null,
        minRepCount: Int = DEFAULT_MIN_REPS,
        outlierSigmaThreshold: Double = DEFAULT_OUTLIER_SIGMA
    ): PersonalBaseline {
        require(repMetrics.isNotEmpty()) { "Cannot derive baseline from zero reps" }
        require(repMetrics.size == repPhaseDurations.size) {
            "repMetrics (${repMetrics.size}) and repPhaseDurations (${repPhaseDurations.size}) " +
                "must be parallel lists"
        }

        val initialMetricStats = computeStatsPerKey(repMetrics)
        val initialPhaseStats = computeStatsPerKey(repPhaseDurations)

        val outlierIndices = findOutlierRepIndices(
            repMetrics, repPhaseDurations,
            initialMetricStats, initialPhaseStats,
            outlierSigmaThreshold
        )

        val keptIndices = repMetrics.indices.filter { it !in outlierIndices }
        if (keptIndices.size < minRepCount) {
            throw IllegalArgumentException(
                "Insufficient valid reps after outlier exclusion: " +
                    "${keptIndices.size} < $minRepCount (input=${repMetrics.size}, excluded=${outlierIndices.size})"
            )
        }

        val finalMetricStats = computeStatsPerKey(keptIndices.map { repMetrics[it] })
        val finalPhaseStats = computeStatsPerKey(keptIndices.map { repPhaseDurations[it] })

        return PersonalBaseline(
            drillType = drillType,
            metricStats = finalMetricStats,
            phaseDurationsMs = finalPhaseStats,
            repCount = keptIndices.size,
            excludedRepIndices = outlierIndices.toList().sorted(),
            qualityScore = computeQualityScore(finalMetricStats),
            createdAtMs = createdAtMs,
            drillerHandedness = drillerHandedness
        )
    }
```

All private helpers (`extractMetricValues`, `extractPhaseDurations`, `computeStatsPerKey`, `statsOf`, `findOutlierRepIndices`, `isOutlier`, `computeQualityScore`) stay exactly as they are.

- [ ] **Step 4: Run new test AND the pre-existing deriver tests**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.analysis.BaselineDeriverFromMetricsTest" --tests "com.ttcoachai.shared.analysis.BaselineDeriverTest"`
Expected: PASS — both classes. A failure in `BaselineDeriverTest` means the refactor changed 003 behavior: stop and fix before proceeding.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineDeriver.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/BaselineDeriverFromMetricsTest.kt
git commit -m "refactor(shared): extract BaselineDeriver.deriveFromMetrics for 2D drill path"
```

---

### Task 9: Cues from rules (`MetricPrecision`, `FeedbackCue`, `DrillFeedbackEngine`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/MetricPrecision.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/FeedbackCue.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillFeedbackEngine.kt`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/DrillFeedbackEngineTest.kt`

Rule evaluation stays in `FrameRuleEvaluator` and rule derivation in `BaselineRuleFactory` (single-source-of-truth constraint). The engine only translates failures into prioritized cues.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineRule
import com.ttcoachai.shared.analysis.BaselineRuleFactory
import com.ttcoachai.shared.models.MetricStats
import com.ttcoachai.shared.models.PersonalBaseline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrillFeedbackEngineTest {

    private fun baseline(vararg stats: Pair<String, MetricStats>) = PersonalBaseline(
        drillType = "forehand_drive",
        metricStats = stats.toMap(),
        phaseDurationsMs = mapOf("stroke_total_ms" to MetricStats(800.0, 40.0, 750.0, 850.0, 10)),
        repCount = 10,
        excludedRepIndices = emptyList(),
        qualityScore = 0.9,
        createdAtMs = 0L,
        drillerHandedness = "right"
    )

    private val elbowStats = MetricStats(mean = 100.0, std = 5.0, min = 92.0, max = 108.0, sampleCount = 10)
    private val kneeStats = MetricStats(mean = 150.0, std = 4.0, min = 144.0, max = 156.0, sampleCount = 10)

    @Test
    fun withinTwoSigmaYieldsNoCues() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val rules = BaselineRuleFactory.defaultRules(b)
        val cues = DrillFeedbackEngine.evaluateRep(
            mapOf(DrillMetrics.METRIC_ELBOW_ANGLE to 108.0), b, rules
        )
        assertTrue(cues.isEmpty(), "108 is within 100±10, got $cues")
    }

    @Test
    fun beyondTwoSigmaYieldsDirectionalCue() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val rules = BaselineRuleFactory.defaultRules(b)
        val cues = DrillFeedbackEngine.evaluateRep(
            mapOf(DrillMetrics.METRIC_ELBOW_ANGLE to 115.0), b, rules
        )
        assertEquals(1, cues.size)
        val cue = cues[0]
        assertEquals(DrillMetrics.METRIC_ELBOW_ANGLE, cue.metricKey)
        assertEquals(CueDirection.TOO_HIGH, cue.direction)
        assertEquals(15.0, cue.deltaFromMean, 1e-9)
        assertEquals(3.0, cue.severity, 1e-9)
        assertEquals(MetricPrecision.PRECISE_DEGREES, cue.precision)
    }

    @Test
    fun belowMeanIsTooLow() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val cues = DrillFeedbackEngine.evaluateRep(
            mapOf(DrillMetrics.METRIC_ELBOW_ANGLE to 85.0), b, BaselineRuleFactory.defaultRules(b)
        )
        assertEquals(CueDirection.TOO_LOW, cues[0].direction)
    }

    @Test
    fun cuesSortedBySeverityDescending() {
        val b = baseline(
            DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats,   // 115 → 3σ
            DrillMetrics.METRIC_KNEE_BEND to kneeStats        // 160 → 2.5σ
        )
        val cues = DrillFeedbackEngine.evaluateRep(
            mapOf(
                DrillMetrics.METRIC_ELBOW_ANGLE to 115.0,
                DrillMetrics.METRIC_KNEE_BEND to 160.0
            ),
            b, BaselineRuleFactory.defaultRules(b)
        )
        assertEquals(2, cues.size)
        assertEquals(DrillMetrics.METRIC_ELBOW_ANGLE, cues[0].metricKey)
        assertTrue(cues[0].severity > cues[1].severity)
    }

    @Test
    fun missingMetricIsSilent() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val cues = DrillFeedbackEngine.evaluateRep(emptyMap(), b, BaselineRuleFactory.defaultRules(b))
        assertTrue(cues.isEmpty(), "no measurement → no feedback (trust rule)")
    }

    @Test
    fun rhythmRulesAreIgnoredPerRep() {
        val b = baseline(DrillMetrics.METRIC_ELBOW_ANGLE to elbowStats)
        val onlyRhythm = listOf(
            BaselineRule.RhythmRule(id = "rhythm:stroke_total_ms", metricKey = "stroke_total_ms", maxDurationDeviationPct = 0.25)
        )
        val cues = DrillFeedbackEngine.evaluateRep(
            mapOf(DrillMetrics.METRIC_ELBOW_ANGLE to 115.0), b, onlyRhythm
        )
        assertTrue(cues.isEmpty())
    }

    @Test
    fun unknownMetricGetsQualitativePrecision() {
        assertEquals(MetricPrecision.QUALITATIVE, MetricPrecisionPolicy.precisionFor("body_rotation"))
        assertEquals(MetricPrecision.PRECISE_DEGREES, MetricPrecisionPolicy.precisionFor(DrillMetrics.METRIC_ELBOW_ANGLE))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.DrillFeedbackEngineTest"`
Expected: FAIL — unresolved references

- [ ] **Step 3: Write the implementation**

`MetricPrecision.kt`:
```kotlin
package com.ttcoachai.shared.drill

/**
 * Trust rule (context doc §3): precise degrees ONLY for in-plane metrics;
 * everything else is qualitative-only or silent.
 */
enum class MetricPrecision { PRECISE_DEGREES, QUALITATIVE }

object MetricPrecisionPolicy {

    private val preciseKeys = DrillMetrics.ALL_KEYS.toSet()

    /** Unknown metrics default to QUALITATIVE — never overclaim precision. */
    fun precisionFor(metricKey: String): MetricPrecision =
        if (metricKey in preciseKeys) MetricPrecision.PRECISE_DEGREES else MetricPrecision.QUALITATIVE
}
```

`FeedbackCue.kt`:
```kotlin
package com.ttcoachai.shared.drill

enum class CueDirection { TOO_HIGH, TOO_LOW }

/** One actionable deviation of a rep metric from the personal baseline. */
data class FeedbackCue(
    val metricKey: String,
    val direction: CueDirection,
    /** Signed degrees (or metric units) vs the baseline mean. */
    val deltaFromMean: Double,
    /** |delta| / σ — used to pick the single most important cue per cadence window. */
    val severity: Double,
    val precision: MetricPrecision
)
```

`DrillFeedbackEngine.kt`:
```kotlin
package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineRule
import com.ttcoachai.shared.analysis.FrameRuleEvaluator
import com.ttcoachai.shared.models.PersonalBaseline
import kotlin.math.abs

/**
 * Turns per-rep metric values + baseline rules into prioritized feedback cues.
 * Rule semantics live in [FrameRuleEvaluator]; rule derivation in BaselineRuleFactory
 * (single source of truth). Rhythm rules are session-level and skipped here.
 */
object DrillFeedbackEngine {

    fun evaluateRep(
        metrics: Map<String, Double>,
        baseline: PersonalBaseline,
        rules: List<BaselineRule>
    ): List<FeedbackCue> {
        val cues = mutableListOf<FeedbackCue>()
        for (rule in rules) {
            if (rule is BaselineRule.RhythmRule) continue
            val value = metrics[rule.metricKey] ?: continue // no measurement → silent
            val passed = FrameRuleEvaluator.evaluate(rule, baseline, value) ?: continue
            if (passed) continue
            val stats = baseline.metricStats[rule.metricKey] ?: continue
            val delta = value - stats.mean
            cues += FeedbackCue(
                metricKey = rule.metricKey,
                direction = if (delta > 0) CueDirection.TOO_HIGH else CueDirection.TOO_LOW,
                deltaFromMean = delta,
                severity = if (stats.std > 0.0) abs(delta) / stats.std else 0.0,
                precision = MetricPrecisionPolicy.precisionFor(rule.metricKey)
            )
        }
        return cues.sortedByDescending { it.severity }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.DrillFeedbackEngineTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/MetricPrecision.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/FeedbackCue.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillFeedbackEngine.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/DrillFeedbackEngineTest.kt
git commit -m "feat(shared): baseline-rule feedback cues with precision trust policy"
```

---

### Task 10: UA + EN message catalog (`FeedbackMessageCatalog`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/FeedbackMessageCatalog.kt`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/FeedbackMessageCatalogTest.kt`

Strings live in shared code (no Android resources — KMP + iOS future). Degrees appear **only** for `PRECISE_DEGREES` cues.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackMessageCatalogTest {

    private fun cue(
        key: String,
        direction: CueDirection = CueDirection.TOO_HIGH,
        delta: Double = 14.7,
        precision: MetricPrecision = MetricPrecisionPolicy.precisionFor(key)
    ) = FeedbackCue(key, direction, if (direction == CueDirection.TOO_HIGH) delta else -delta, 2.5, precision)

    @Test
    fun everyKnownMetricDirectionAndLangHasAMessage() {
        for (key in DrillMetrics.ALL_KEYS) {
            for (dir in CueDirection.entries) {
                for (lang in FeedbackLang.entries) {
                    val msg = FeedbackMessageCatalog.format(cue(key, dir), lang)
                    assertTrue(msg.isNotBlank(), "$key/$dir/$lang must have a message")
                }
            }
        }
    }

    @Test
    fun preciseCuesContainRoundedDegrees() {
        val msg = FeedbackMessageCatalog.format(cue(DrillMetrics.METRIC_ELBOW_ANGLE, delta = 14.7), FeedbackLang.EN)
        assertTrue("15°" in msg, "rounded degrees expected in: $msg")
    }

    @Test
    fun qualitativeCuesNeverContainDegrees() {
        val q = cue("body_rotation", precision = MetricPrecision.QUALITATIVE)
        for (lang in FeedbackLang.entries) {
            val msg = FeedbackMessageCatalog.format(q, lang)
            assertFalse("°" in msg, "qualitative cue must not show degrees: $msg")
            assertTrue(msg.isNotBlank())
        }
    }

    @Test
    fun ukrainianMessagesAreCyrillic() {
        val msg = FeedbackMessageCatalog.format(cue(DrillMetrics.METRIC_ELBOW_ANGLE), FeedbackLang.UA)
        assertTrue(msg.any { it in 'А'..'я' || it == 'і' || it == 'ї' || it == 'є' }, "expected Cyrillic: $msg")
    }

    @Test
    fun positiveMessagesExistInBothLanguages() {
        assertTrue(FeedbackMessageCatalog.positive(FeedbackLang.EN).isNotBlank())
        assertTrue(FeedbackMessageCatalog.positive(FeedbackLang.UA).isNotBlank())
    }

    @Test
    fun directionsProduceDifferentMessages() {
        assertFalse(
            FeedbackMessageCatalog.format(cue(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_HIGH), FeedbackLang.EN) ==
                FeedbackMessageCatalog.format(cue(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_LOW), FeedbackLang.EN)
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.FeedbackMessageCatalogTest"`
Expected: FAIL — unresolved references (`FeedbackLang`, `FeedbackMessageCatalog`)

- [ ] **Step 3: Write the implementation**

`FeedbackMessageCatalog.kt`:
```kotlin
package com.ttcoachai.shared.drill

import kotlin.math.abs
import kotlin.math.roundToInt

enum class FeedbackLang { EN, UA }

/**
 * UA + EN feedback strings. Lives in shared code (no Android resources) so the same
 * catalog serves Android, desktop fixture runs, and the future iOS app.
 *
 * Trust rule: the degree number is inserted ONLY for PRECISE_DEGREES cues;
 * qualitative cues get direction-only phrasing.
 */
object FeedbackMessageCatalog {

    fun format(cue: FeedbackCue, lang: FeedbackLang): String {
        val d = abs(cue.deltaFromMean).roundToInt()
        val precise = cue.precision == MetricPrecision.PRECISE_DEGREES
        val high = cue.direction == CueDirection.TOO_HIGH

        return when (cue.metricKey) {
            DrillMetrics.METRIC_ELBOW_ANGLE -> when {
                high && lang == FeedbackLang.EN -> withDeg("Elbow straighter than your usual — bend it a bit more", d, precise, lang)
                high -> withDeg("Лікоть пряміший, ніж зазвичай — зігни трохи більше", d, precise, lang)
                lang == FeedbackLang.EN -> withDeg("Elbow more bent than your usual — open it up a bit", d, precise, lang)
                else -> withDeg("Лікоть зігнутий більше, ніж зазвичай — розігни трохи", d, precise, lang)
            }
            DrillMetrics.METRIC_SHOULDER_ANGLE -> when {
                high && lang == FeedbackLang.EN -> withDeg("Upper arm higher than your usual — drop the elbow a bit", d, precise, lang)
                high -> withDeg("Плече вище, ніж зазвичай — опусти лікоть трохи", d, precise, lang)
                lang == FeedbackLang.EN -> withDeg("Upper arm lower than your usual — lift the elbow a bit", d, precise, lang)
                else -> withDeg("Плече нижче, ніж зазвичай — підніми лікоть трохи", d, precise, lang)
            }
            DrillMetrics.METRIC_KNEE_BEND -> when {
                high && lang == FeedbackLang.EN -> withDeg("Legs straighter than your usual — bend the knees more", d, precise, lang)
                high -> withDeg("Ноги пряміші, ніж зазвичай — зігни коліна більше", d, precise, lang)
                lang == FeedbackLang.EN -> withDeg("Knees more bent than your usual stance", d, precise, lang)
                else -> withDeg("Коліна зігнуті більше, ніж у твоїй звичній стійці", d, precise, lang)
            }
            DrillMetrics.METRIC_TORSO_LEAN -> when {
                high && lang == FeedbackLang.EN -> withDeg("Leaning further than your usual — straighten up a bit", d, precise, lang)
                high -> withDeg("Нахил більший, ніж зазвичай — випрямся трохи", d, precise, lang)
                lang == FeedbackLang.EN -> withDeg("More upright than your usual — keep your normal lean", d, precise, lang)
                else -> withDeg("Корпус пряміший, ніж зазвичай — тримай свій звичний нахил", d, precise, lang)
            }
            DrillMetrics.METRIC_SHOULDER_TILT -> when {
                high && lang == FeedbackLang.EN -> withDeg("Shoulders more tilted than your usual — level them", d, precise, lang)
                high -> withDeg("Плечі нахилені більше, ніж зазвичай — вирівняй їх", d, precise, lang)
                lang == FeedbackLang.EN -> withDeg("Shoulder line flatter than your usual", d, precise, lang)
                else -> withDeg("Лінія плечей рівніша, ніж зазвичай", d, precise, lang)
            }
            // Unknown metric (e.g. future rotational cues): qualitative-only, never degrees.
            else -> when (lang) {
                FeedbackLang.EN -> if (high) "A bit more than your usual on that move — ease off" else "A bit less than your usual on that move"
                FeedbackLang.UA -> if (high) "Трохи більше, ніж зазвичай у цьому русі — послаб" else "Трохи менше, ніж зазвичай у цьому русі"
            }
        }
    }

    fun positive(lang: FeedbackLang): String = when (lang) {
        FeedbackLang.EN -> "Good rep — keep that rhythm"
        FeedbackLang.UA -> "Гарний повтор — тримай цей ритм"
    }

    private fun withDeg(base: String, deg: Int, precise: Boolean, lang: FeedbackLang): String =
        if (precise) {
            when (lang) {
                FeedbackLang.EN -> "$base (about $deg° off your baseline)"
                FeedbackLang.UA -> "$base (близько $deg° від твого еталону)"
            }
        } else {
            base
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.FeedbackMessageCatalogTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/FeedbackMessageCatalog.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/FeedbackMessageCatalogTest.kt
git commit -m "feat(shared): UA+EN feedback message catalog with degree trust rule"
```

---

### Task 11: 3–5 s cadence (`FeedbackCadencePolicy`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/FeedbackCadencePolicy.kt`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/FeedbackCadencePolicyTest.kt`

Semantics: at most one spoken cue per `minIntervalMs` (3 s); when a rep is clean, positive reinforcement is allowed only after `maxIntervalMs` (5 s) of silence, so corrections always win the channel.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedbackCadencePolicyTest {

    private val elbowHigh = FeedbackCue(
        DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_HIGH, 15.0, 3.0, MetricPrecision.PRECISE_DEGREES
    )
    private val kneeLow = FeedbackCue(
        DrillMetrics.METRIC_KNEE_BEND, CueDirection.TOO_LOW, -10.0, 2.0, MetricPrecision.PRECISE_DEGREES
    )

    @Test
    fun firstCueIsEmittedImmediately() {
        val policy = FeedbackCadencePolicy()
        assertEquals(elbowHigh, policy.offer(nowMs = 0L, cues = listOf(kneeLow, elbowHigh)))
    }

    @Test
    fun secondCueWithinMinIntervalIsSuppressed() {
        val policy = FeedbackCadencePolicy(minIntervalMs = 3000, maxIntervalMs = 5000)
        policy.offer(0L, listOf(elbowHigh))
        assertNull(policy.offer(2999L, listOf(kneeLow)))
        assertEquals(kneeLow, policy.offer(3000L, listOf(kneeLow)))
    }

    @Test
    fun emptyCuesEmitNothingAndDoNotResetTheClock() {
        val policy = FeedbackCadencePolicy()
        policy.offer(0L, listOf(elbowHigh))
        assertNull(policy.offer(4000L, emptyList()))
        assertEquals(kneeLow, policy.offer(4001L, listOf(kneeLow)), "empty offer must not consume the window")
    }

    @Test
    fun highestSeverityWins() {
        val policy = FeedbackCadencePolicy()
        assertEquals(elbowHigh, policy.offer(0L, listOf(kneeLow, elbowHigh)))
    }

    @Test
    fun positiveOnlyAfterMaxInterval() {
        val policy = FeedbackCadencePolicy(minIntervalMs = 3000, maxIntervalMs = 5000)
        policy.offer(0L, listOf(elbowHigh))
        assertFalse(policy.offerPositive(4000L), "positive must wait the full maxInterval")
        assertTrue(policy.offerPositive(5000L))
        assertFalse(policy.offerPositive(5001L), "positive consumes the window too")
    }

    @Test
    fun positiveAllowedImmediatelyWhenNothingSpokenYet() {
        assertTrue(FeedbackCadencePolicy().offerPositive(0L))
    }

    @Test
    fun resetClearsTheClock() {
        val policy = FeedbackCadencePolicy()
        policy.offer(0L, listOf(elbowHigh))
        policy.reset()
        assertEquals(kneeLow, policy.offer(1L, listOf(kneeLow)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.FeedbackCadencePolicyTest"`
Expected: FAIL — unresolved reference `FeedbackCadencePolicy`

- [ ] **Step 3: Write the implementation**

`FeedbackCadencePolicy.kt`:
```kotlin
package com.ttcoachai.shared.drill

/**
 * 3–5 s feedback cadence (the product's definition of "real-time" — context doc §1).
 * At most one corrective cue per [minIntervalMs]; positive reinforcement only after
 * [maxIntervalMs] of silence so corrections always have priority for the voice channel.
 *
 * Stateful and single-session: create one per drill run, [reset] between runs.
 */
class FeedbackCadencePolicy(
    private val minIntervalMs: Long = 3000,
    private val maxIntervalMs: Long = 5000
) {

    private var lastEmittedMs: Long? = null

    /** Returns the cue to speak now (highest severity), or null if the window is closed. */
    fun offer(nowMs: Long, cues: List<FeedbackCue>): FeedbackCue? {
        val last = lastEmittedMs
        if (last != null && nowMs - last < minIntervalMs) return null
        val top = cues.maxByOrNull { it.severity } ?: return null
        lastEmittedMs = nowMs
        return top
    }

    /** True if a positive message may be spoken now; consumes the window if so. */
    fun offerPositive(nowMs: Long): Boolean {
        val last = lastEmittedMs
        if (last != null && nowMs - last < maxIntervalMs) return false
        lastEmittedMs = nowMs
        return true
    }

    fun reset() {
        lastEmittedMs = null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.FeedbackCadencePolicyTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/FeedbackCadencePolicy.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/FeedbackCadencePolicyTest.kt
git commit -m "feat(shared): 3-5s feedback cadence policy"
```

---

### Task 12: Camera-yaw estimation (`CameraAngleEstimator`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/CameraAngleEstimator.kt`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/CameraAngleEstimatorTest.kt`

2D port of the camera-angle idea already in poses_viewer (`MannequinEditor.cameraYawOffsetDeg`) — the z-based detection there (`extractTorsoLegs.ts`) can't be used with COCO-17. Estimation: in a perfect side view the shoulders overlap horizontally; `sin(yaw) ≈ shoulderSeparationX / (0.9 × torsoLength)` (Drillis & Contini ratio, same anthropometric source as poses_viewer bone lengths). Median over sampled frames for robustness. Returns `|yaw|` — the sign is unrecoverable from foreshortening and irrelevant for the 1/cos correction.

Two entry points (design note 10): `estimateSideViewYawDeg` over an arbitrary frame window, and `estimateYawForStroke` — the per-rep variant that samples the ~1 s ready-stance window **before** the stroke, because the player moves their feet between reps and because estimating during the swing would confound the player's own body rotation with camera placement.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CameraAngleEstimatorTest {

    /** Torso: shoulder-mid (0.5, 0.3), hip-mid (0.5, 0.6) → torsoLen 0.3. */
    private fun frameWithShoulderSep(sepX: Float, score: Float = 1f): PoseFrame2D {
        val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
        kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.5f - sepX / 2, 0.3f, score)
        kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.5f + sepX / 2, 0.3f, score)
        kp[Coco17.LEFT_HIP] = Keypoint2D(0.5f, 0.6f, 1f)
        kp[Coco17.RIGHT_HIP] = Keypoint2D(0.5f, 0.6f, 1f)
        return PoseFrame2D(0, 0L, kp)
    }

    @Test
    fun perfectProfileIsZeroYaw() {
        val frames = List(10) { frameWithShoulderSep(0f) }
        assertEquals(0f, CameraAngleEstimator.estimateSideViewYawDeg(frames, 1f)!!, 0.5f)
    }

    @Test
    fun thirtyDegreeYawFromForeshortening() {
        // sin(30°) = sep / (0.9 × 0.3) → sep = 0.5 × 0.27 = 0.135
        val sep = (sin(30.0 * PI / 180.0) * 0.9 * 0.3).toFloat()
        val frames = List(10) { frameWithShoulderSep(sep) }
        assertEquals(30f, CameraAngleEstimator.estimateSideViewYawDeg(frames, 1f)!!, 1.5f)
    }

    @Test
    fun medianIgnoresOutlierFrames() {
        val frames = List(9) { frameWithShoulderSep(0f) } + frameWithShoulderSep(0.25f)
        assertEquals(0f, CameraAngleEstimator.estimateSideViewYawDeg(frames, 1f)!!, 0.5f)
    }

    @Test
    fun lowConfidenceShouldersYieldNull() {
        val frames = List(10) { frameWithShoulderSep(0.1f, score = 0.1f) }
        assertNull(CameraAngleEstimator.estimateSideViewYawDeg(frames, 1f))
    }

    @Test
    fun emptyInputYieldsNull() {
        assertNull(CameraAngleEstimator.estimateSideViewYawDeg(emptyList(), 1f))
    }

    @Test
    fun perStrokeYawUsesPreStrokeWindowNotTheSwing() {
        // Ready stance in profile (sep 0) frames 0..9; swing frames 10..15 with the
        // torso visibly rotated (sep 0.2 — the player's OWN rotation, not the camera).
        // At intervalMs=100 the 1000 ms lookback covers exactly frames 0..9.
        val frames = List(10) { frameWithShoulderSep(0f) } + List(6) { frameWithShoulderSep(0.2f) }
        val stroke = Stroke2D(strokeIndex = 0, startFrame = 10, peakFrame = 13, endFrame = 15, peakSpeed = 2f)
        assertEquals(0f, CameraAngleEstimator.estimateYawForStroke(frames, stroke, 1f, 100L)!!, 0.5f)
    }

    @Test
    fun perStrokeYawFallsBackToStrokeWindowAtRecordingStart() {
        // Stroke begins at frame 0 → no lookback window → falls back to the stroke itself.
        val sep = (sin(30.0 * PI / 180.0) * 0.9 * 0.3).toFloat()
        val frames = List(6) { frameWithShoulderSep(sep) }
        val stroke = Stroke2D(strokeIndex = 0, startFrame = 0, peakFrame = 3, endFrame = 5, peakSpeed = 2f)
        assertEquals(30f, CameraAngleEstimator.estimateYawForStroke(frames, stroke, 1f, 100L)!!, 1.5f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.analysis.CameraAngleEstimatorTest"`
Expected: FAIL — unresolved reference `CameraAngleEstimator`

- [ ] **Step 3: Write the implementation**

`CameraAngleEstimator.kt`:
```kotlin
package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.hypot

/**
 * Estimates how far the camera is from a perfect side (profile) view, in degrees,
 * from 2D foreshortening: in a true profile the shoulders overlap horizontally;
 * the wider they appear relative to torso length, the further the camera is from
 * perpendicular. KMP port of the camera-yaw compensation idea in poses_viewer's
 * MannequinEditor — the z-based math there (extractTorsoLegs.ts) needs MediaPipe z
 * and cannot work on COCO-17.
 *
 * The player moves their feet during a drill, so yaw is NOT a per-session constant:
 * orchestrators call [estimateYawForStroke] per rep (design note 10). The Phase 3
 * live loop wraps [estimateSideViewYawDeg] in a rolling ~1 s window the same way.
 *
 * Returns |yaw| only — foreshortening cannot recover the sign, and the 1/cos
 * correction (ViewGeometry.xScale) is sign-independent anyway.
 *
 * NOTE: takes the raw aspectRatio (NOT xScale) — this runs BEFORE any yaw is known.
 */
object CameraAngleEstimator {

    /**
     * Biacromial width ≈ 0.259·H, shoulder–hip torso length ≈ 0.288·H
     * (Drillis & Contini 1966 — same source as poses_viewer bone lengths)
     * → shoulder width ≈ 0.9 × torso length.
     */
    const val SHOULDER_TO_TORSO_RATIO = 0.9f
    const val DEFAULT_SAMPLE_FRAMES = 30

    /** Pre-stroke ready-stance window in ms — fps-independent (DESIGN_LIMITATIONS L-02). */
    const val DEFAULT_LOOKBACK_MS = 1000L

    private const val RAD_TO_DEG = (180.0 / PI).toFloat()
    private const val MIN_TORSO_LEN = 1e-4f

    /**
     * Per-rep yaw: estimated from the [lookbackMs] ready-stance window immediately
     * BEFORE the stroke — estimating during the swing would confound the player's
     * own body rotation with camera placement. Falls back to the stroke's own
     * window when there is no lookback (stroke at recording start).
     */
    fun estimateYawForStroke(
        frames: List<PoseFrame2D>,
        stroke: Stroke2D,
        aspectRatio: Float,
        intervalMs: Long,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
        lookbackMs: Long = DEFAULT_LOOKBACK_MS
    ): Float? {
        require(intervalMs > 0) { "intervalMs must be > 0, got $intervalMs" }
        val lookbackFrames = (lookbackMs / intervalMs).toInt().coerceAtLeast(1)
        val until = stroke.startFrame.coerceIn(0, frames.size)
        val from = (until - lookbackFrames).coerceAtLeast(0)
        val preStroke = frames.subList(from, until)
        estimateSideViewYawDeg(preStroke, aspectRatio, minScore)?.let { return it }
        val strokeEnd = (stroke.endFrame + 1).coerceIn(0, frames.size)
        if (until >= strokeEnd) return null
        return estimateSideViewYawDeg(frames.subList(until, strokeEnd), aspectRatio, minScore)
    }

    /** Median per-frame yaw over the first [sampleFrames] frames with a person. Null if none qualify. */
    fun estimateSideViewYawDeg(
        frames: List<PoseFrame2D>,
        aspectRatio: Float,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
        sampleFrames: Int = DEFAULT_SAMPLE_FRAMES
    ): Float? {
        val perFrame = frames.asSequence()
            .filter { it.keypoints.isNotEmpty() }
            .take(sampleFrames)
            .mapNotNull { frameYawDeg(it.keypoints, aspectRatio, minScore) }
            .toList()
        if (perFrame.isEmpty()) return null
        return median(perFrame)
    }

    private fun frameYawDeg(kp: List<Keypoint2D>, aspectRatio: Float, minScore: Float): Float? {
        val ls = kp.getOrNull(Coco17.LEFT_SHOULDER)?.takeIf { it.score >= minScore } ?: return null
        val rs = kp.getOrNull(Coco17.RIGHT_SHOULDER)?.takeIf { it.score >= minScore } ?: return null
        val lh = kp.getOrNull(Coco17.LEFT_HIP)?.takeIf { it.score >= minScore } ?: return null
        val rh = kp.getOrNull(Coco17.RIGHT_HIP)?.takeIf { it.score >= minScore } ?: return null

        val torsoLen = hypot(
            ((ls.x + rs.x) / 2f - (lh.x + rh.x) / 2f) * aspectRatio,
            (ls.y + rs.y) / 2f - (lh.y + rh.y) / 2f
        )
        if (torsoLen < MIN_TORSO_LEN) return null

        val shoulderSepX = abs(rs.x - ls.x) * aspectRatio
        val sinYaw = (shoulderSepX / (SHOULDER_TO_TORSO_RATIO * torsoLen)).coerceIn(0f, 1f)
        return asin(sinYaw) * RAD_TO_DEG
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.analysis.CameraAngleEstimatorTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/CameraAngleEstimator.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/CameraAngleEstimatorTest.kt
git commit -m "feat(shared): camera-yaw estimation from shoulder foreshortening (2D)"
```

---

### Task 13: Orchestration (`DrillCalibrator`, `ForehandDriveDrillAnalyzer`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/RepFilter.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillCalibrator.kt`
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ForehandDriveDrillAnalyzer.kt`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/RepFilterTest.kt`
- Test: `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/DrillCalibratorTest.kt`

Camera-yaw policy (design notes 9–10, founder decisions): yaw is resolved **per rep** via `CameraAngleEstimator.estimateYawForStroke` (pre-stroke ready-stance window — the player moves their feet between reps), unless an explicit `cameraYawDeg` override is passed (applies to all reps; used by fixture tests). Forehand drive **requires** the side view, so per rep beyond `maxCameraYawDeg` (default 30°): during **calibration** the rep is excluded — `CameraPlacementException` is thrown only when placement exclusions drop the count below `minRepCount` (a baseline from bad placement would poison all later feedback); during **analysis** the rep gets no cues and no spoken message (`RepAnalysis.placementOk = false`; metrics kept as diagnostics). Within the threshold, the per-rep yaw folds into that rep's `ViewGeometry.xScale`. Stroke detection itself runs on plain aspect ratio (design note 10).

Both orchestrators also run detector output through `RepFilter` (DESIGN_LIMITATIONS **L-03**): every wrist-speed peak is otherwise treated as a drill rep, so picking up a ball, wiping a hand, or walking would feed junk into the baseline (calibration) or trigger phantom cues (analysis). The filter bands strokes against the session's **median** peak speed and duration (keep within [median/2, median×2]); with fewer than 4 strokes there is no cluster to trust, so nothing is filtered.

- [ ] **Step 1: Write the failing RepFilter test**

```kotlin
package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Stroke2D
import kotlin.test.Test
import kotlin.test.assertEquals

class RepFilterTest {

    private fun stroke(i: Int, peakSpeed: Float, durFrames: Int) = Stroke2D(
        strokeIndex = i,
        startFrame = i * 30,
        peakFrame = i * 30 + durFrames / 2,
        endFrame = i * 30 + durFrames,
        peakSpeed = peakSpeed
    )

    @Test
    fun uniformStrokesAllKept() {
        val s = List(6) { stroke(it, 2.4f, 6) }
        assertEquals(s, RepFilter.filter(s))
    }

    @Test
    fun slowAndOverlongJunkDropped() {
        val good = List(6) { stroke(it, 2.4f, 6) }
        val slow = stroke(6, 1.1f, 6)    // ball pickup: above detector threshold, half the cluster speed
        val smear = stroke(7, 2.4f, 20)  // walking: long movement, plausible peak
        assertEquals(good, RepFilter.filter(good + slow + smear))
    }

    @Test
    fun tooFewStrokesAreNotFiltered() {
        val s = listOf(stroke(0, 2.4f, 6), stroke(1, 0.5f, 30), stroke(2, 5f, 2))
        assertEquals(s, RepFilter.filter(s), "below 4 strokes there is no cluster to trust")
    }
}
```

- [ ] **Step 2: Run it (expect FAIL), implement RepFilter, re-run (expect PASS — 3 tests)**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.RepFilterTest"`

`RepFilter.kt`:
```kotlin
package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Stroke2D

/**
 * Minimal stroke/non-stroke discrimination (DESIGN_LIMITATIONS L-03): keeps only
 * strokes whose peak speed AND duration lie within [median/BAND, median×BAND] of
 * the session's medians. Ball pickups and hand wipes are slower than drill strokes;
 * walking is longer — both fall outside the dominant cluster. Below
 * [MIN_STROKES_TO_FILTER] there is no cluster to trust, so input passes through.
 *
 * Note: private median is the module's fourth copy (estimator, detector,
 * DrillMetrics) — consolidation is a post-merge /simplify candidate, not worth
 * re-threading earlier tasks now.
 */
object RepFilter {

    const val MIN_STROKES_TO_FILTER = 4
    const val SPEED_BAND = 2.0f
    const val DURATION_BAND = 2.0f

    fun filter(strokes: List<Stroke2D>): List<Stroke2D> {
        if (strokes.size < MIN_STROKES_TO_FILTER) return strokes
        val medSpeed = median(strokes.map { it.peakSpeed })
        val medDur = median(strokes.map { (it.endFrame - it.startFrame).toFloat() })
        return strokes.filter { s ->
            val dur = (s.endFrame - s.startFrame).toFloat()
            s.peakSpeed >= medSpeed / SPEED_BAND && s.peakSpeed <= medSpeed * SPEED_BAND &&
                dur >= medDur / DURATION_BAND && dur <= medDur * DURATION_BAND
        }
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
```

- [ ] **Step 3: Commit RepFilter**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/RepFilter.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/RepFilterTest.kt
git commit -m "feat(shared): rep filter — drop non-stroke peaks by speed/duration banding (L-03)"
```

- [ ] **Step 4: Write the failing orchestrator test**

The test builds a synthetic multi-rep sequence by repeating a parameterized stroke (wrist sweep with a controllable elbow geometry) so calibration and analysis are fully deterministic.

```kotlin
package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Topology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrillCalibratorTest {

    /**
     * One rep = 4 still frames + 7 swing frames. wristYAtPeak controls the elbow
     * angle at the speed peak, so reps can be made identical or deviant.
     * shoulderSepX controls the apparent stance vs the camera: 0.02 ≈ 5° yaw
     * (passes the gate), 0.22 ≈ 78° yaw (gated — player turned toward the camera).
     */
    private fun repFrames(startIndex: Int, wristYAtPeak: Float, shoulderSepX: Float = 0.02f): List<PoseFrame2D> {
        val wristXs = listOf(0.50f, 0.50f, 0.50f, 0.50f, 0.51f, 0.53f, 0.57f, 0.63f, 0.68f, 0.71f, 0.72f)
        return wristXs.mapIndexed { i, wx ->
            val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
            kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.44f + shoulderSepX / 2, 0.30f, 1f)
            kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.44f - shoulderSepX / 2, 0.30f, 1f)
            kp[Coco17.RIGHT_ELBOW] = Keypoint2D(0.50f, 0.42f, 1f)
            kp[Coco17.RIGHT_WRIST] = Keypoint2D(wx, wristYAtPeak, 1f)
            kp[Coco17.RIGHT_HIP] = Keypoint2D(0.45f, 0.55f, 1f)
            kp[Coco17.LEFT_HIP] = Keypoint2D(0.43f, 0.55f, 1f)
            kp[Coco17.RIGHT_KNEE] = Keypoint2D(0.46f, 0.72f, 1f)
            kp[Coco17.RIGHT_ANKLE] = Keypoint2D(0.48f, 0.90f, 1f)
            PoseFrame2D(startIndex + i, (startIndex + i) * 100L, kp)
        }
    }

    private fun sequenceOf(
        wristYs: List<Float>,
        shoulderSeps: List<Float> = List(wristYs.size) { 0.02f }
    ): PoseSequence2D {
        val frames = mutableListOf<PoseFrame2D>()
        wristYs.forEachIndexed { i, y -> frames += repFrames(frames.size, y, shoulderSeps[i]) }
        return PoseSequence2D(
            topology = Topology.COCO17, model = "synthetic", videoName = "synthetic.mp4",
            intervalMs = 100L, totalFrames = frames.size, videoDurationMs = frames.size * 100L,
            videoWidth = 1000, videoHeight = 1000, frames = frames
        )
    }

    @Test
    fun calibratesBaselineFromRepeatedReps() {
        // 5 near-identical reps (tiny y jitter so std > 0 and consistency rules derive)
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(
            sequence = seq, drillType = "forehand_drive", createdAtMs = 1L,
            handedness = Handedness.RIGHT, minRepCount = 3
        )
        assertEquals("forehand_drive", baseline.drillType)
        assertEquals("right", baseline.drillerHandedness)
        assertTrue(baseline.repCount >= 3)
        assertTrue(DrillMetrics.METRIC_ELBOW_ANGLE in baseline.metricStats)
        assertTrue(com.ttcoachai.shared.analysis.BaselineDeriver.PHASE_STROKE_TOTAL_MS in baseline.phaseDurationsMs)
    }

    @Test
    fun analyzerIsQuietOnRepsMatchingTheBaseline() {
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(seq, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3)
        val report = ForehandDriveDrillAnalyzer(baseline = baseline).analyze(seq)
        assertTrue(report.reps.isNotEmpty())
        val corrective = report.feedback.filter { it.cue != null }
        assertTrue(corrective.isEmpty(), "reps that built the baseline must not trigger cues: $corrective")
    }

    @Test
    fun analyzerFlagsDeviantRep() {
        val calib = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(calib, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3)
        // Same drill, wrist much higher at peak → elbow angle far from baseline
        val deviant = sequenceOf(listOf(0.25f))
        val report = ForehandDriveDrillAnalyzer(baseline = baseline).analyze(deviant)
        assertEquals(1, report.reps.size)
        assertTrue(report.reps[0].cues.isNotEmpty(), "deviant rep must produce cues")
        assertTrue(report.feedback.any { it.cue != null }, "cue must be spoken")
    }

    @Test
    fun feedbackRespectsCadence() {
        val calib = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(calib, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3)
        // 6 deviant reps, 1.1 s apart (11 frames × 100 ms) → 3 s cadence must suppress some
        val deviant = sequenceOf(List(6) { 0.25f })
        val report = ForehandDriveDrillAnalyzer(baseline = baseline).analyze(deviant)
        val spoken = report.feedback
        assertTrue(spoken.size < 6, "cadence must throttle: ${spoken.size}")
        spoken.zipWithNext().forEach { (a, b) ->
            assertTrue(b.timestampMs - a.timestampMs >= 3000, "gap ${b.timestampMs - a.timestampMs} < 3000ms")
        }
    }

    @Test
    fun calibrationThrowsOnBadCameraPlacement() {
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        assertFailsWith<DrillCalibrator.CameraPlacementException> {
            DrillCalibrator.calibrate(
                seq, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3,
                cameraYawDeg = 45f // explicit override beyond the 30° gate
            )
        }
    }

    @Test
    fun analyzerSkipsFeedbackOnBadCameraPlacement() {
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(
            seq, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3, cameraYawDeg = 0f
        )
        val report = ForehandDriveDrillAnalyzer(baseline = baseline, cameraYawDeg = 45f).analyze(seq)
        assertFalse(report.placementOk)
        assertTrue(report.reps.isNotEmpty(), "reps still reported for diagnostics")
        report.reps.forEach { rep ->
            assertEquals(45f, rep.cameraYawDeg)
            assertFalse(rep.placementOk)
            assertTrue(rep.cues.isEmpty(), "no cues on a bad-placement rep")
        }
        assertTrue(report.feedback.isEmpty(), "feedback must be skipped on bad placement")
    }

    @Test
    fun yawIsResolvedPerRepNotPerSession() {
        // Without an override, every rep must carry its own estimated yaw — the player
        // may move their feet between reps. Synthetic reps share identical geometry,
        // so the estimates agree, but each rep is annotated independently.
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(seq, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3)
        val report = ForehandDriveDrillAnalyzer(baseline = baseline).analyze(seq)
        assertTrue(report.reps.isNotEmpty())
        report.reps.forEach { rep ->
            assertTrue(rep.placementOk, "synthetic ~5° yaw must pass the 30° gate")
            assertTrue(rep.cameraYawDeg in 0f..30f, "per-rep yaw out of range: ${rep.cameraYawDeg}")
        }
    }

    @Test
    fun playerTurningMidSessionGatesOnlyTheTurnedReps() {
        // Calibrate in profile, then mid-drill the player turns toward the camera
        // (feet moved → wide apparent shoulder separation) for the last two reps.
        val calib = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(calib, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3)

        val drill = sequenceOf(
            wristYs = listOf(0.40f, 0.401f, 0.399f, 0.40f, 0.401f),
            shoulderSeps = listOf(0.02f, 0.02f, 0.02f, 0.22f, 0.22f)
        )
        val report = ForehandDriveDrillAnalyzer(baseline = baseline).analyze(drill)
        assertEquals(5, report.reps.size)
        assertTrue(report.reps[0].placementOk && report.reps[1].placementOk && report.reps[2].placementOk,
            "profile reps must keep getting feedback")
        // reps[3] is the transition rep: its pre-stroke lookback window mixes both
        // stances, so its verdict depends on the median split — deliberately unasserted.
        assertFalse(report.reps[4].placementOk, "turned rep must be gated (yaw ~78°)")
        assertTrue(report.reps[4].cues.isEmpty(), "gated rep must produce no cues")
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.DrillCalibratorTest"`
Expected: FAIL — unresolved references

- [ ] **Step 6: Write the implementation**

`DrillCalibrator.kt`:
```kotlin
package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineDeriver
import com.ttcoachai.shared.analysis.CameraAngleEstimator
import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.ViewGeometry
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 2D pivot calibration: detect reps in a calibration recording, extract per-rep
 * metrics at each wrist-speed peak, and derive a PersonalBaseline via the 003 path
 * (BaselineDeriver — design decision 2: calibrate, don't re-teach).
 *
 * Camera yaw is resolved PER REP (design note 10 — the player moves their feet):
 * [cameraYawDeg] override if given, else CameraAngleEstimator.estimateYawForStroke.
 * Reps beyond [maxCameraYawDeg] are excluded; [CameraPlacementException] fires only
 * when those exclusions leave fewer than [minRepCount] reps — a baseline built from
 * a badly placed camera would poison every later feedback session.
 */
object DrillCalibrator {

    /** Camera too far off the required side view; reposition and re-record. */
    class CameraPlacementException(message: String) : IllegalStateException(message)

    /** First-order 1/cos correction is trustworthy up to roughly here. */
    const val DEFAULT_MAX_CAMERA_YAW_DEG = 30f

    fun calibrate(
        sequence: PoseSequence2D,
        drillType: String,
        createdAtMs: Long,
        handedness: Handedness = Handedness.RIGHT,
        minRepCount: Int = 10,
        outlierSigmaThreshold: Double = 2.0,
        detector: StrokeDetector2D = StrokeDetector2D(),
        /** Explicit camera-yaw override applied to all reps; null → per-rep auto-estimate. */
        cameraYawDeg: Float? = null,
        maxCameraYawDeg: Float = DEFAULT_MAX_CAMERA_YAW_DEG
    ): PersonalBaseline {
        // Detection on plain aspect: peak finding tolerates uncorrected ≤30° yaw
        // (≤15% speed-magnitude error); metrics below use per-rep corrected xScale.
        // RepFilter drops non-stroke peaks before they can shift the baseline (L-03).
        val strokes = RepFilter.filter(
            detector.detect(sequence.frames, handedness, sequence.aspectRatio, sequence.intervalMs)
        )

        val strokesWithYaw = strokes.map { stroke ->
            val yaw = cameraYawDeg
                ?: CameraAngleEstimator.estimateYawForStroke(
                    sequence.frames, stroke, sequence.aspectRatio, sequence.intervalMs
                )
                ?: 0f
            stroke to yaw
        }
        val placed = strokesWithYaw.filter { (_, yaw) -> abs(yaw) <= maxCameraYawDeg }
        if (placed.size < minRepCount && placed.size < strokes.size) {
            throw CameraPlacementException(
                "Only ${placed.size} of ${strokes.size} reps had the camera within " +
                    "${maxCameraYawDeg.roundToInt()}° of the side view (need $minRepCount) — " +
                    "reposition the camera and re-record calibration"
            )
        }

        val repMetrics = placed.map { (stroke, yaw) ->
            val view = ViewGeometry(sequence.aspectRatio, yaw)
            DrillMetrics.extractAtPeak(
                sequence.frames, stroke.peakFrame, handedness, view.xScale, sequence.intervalMs
            )
        }
        val repPhases = placed.map { (stroke, _) ->
            mapOf(
                // Backswing segmentation is deferred (needs direction-reversal analysis);
                // forward-swing and total cover the rhythm rules for v1.
                BaselineDeriver.PHASE_FORWARD_SWING_MS to
                    (stroke.peakFrame - stroke.startFrame) * sequence.intervalMs.toDouble(),
                BaselineDeriver.PHASE_STROKE_TOTAL_MS to
                    (stroke.endFrame - stroke.startFrame) * sequence.intervalMs.toDouble()
            )
        }
        return BaselineDeriver.deriveFromMetrics(
            repMetrics = repMetrics,
            repPhaseDurations = repPhases,
            drillType = drillType,
            createdAtMs = createdAtMs,
            drillerHandedness = handedness.baselineString,
            minRepCount = minRepCount,
            outlierSigmaThreshold = outlierSigmaThreshold
        )
    }
}
```

`ForehandDriveDrillAnalyzer.kt`:
```kotlin
package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineRule
import com.ttcoachai.shared.analysis.BaselineRuleFactory
import com.ttcoachai.shared.analysis.CameraAngleEstimator
import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Stroke2D
import com.ttcoachai.shared.models.ViewGeometry
import kotlin.math.abs

data class RepAnalysis(
    val stroke: Stroke2D,
    val metrics: Map<String, Double>,
    val cues: List<FeedbackCue>,
    /** Camera yaw used for THIS rep (pre-stroke estimate or the override), degrees. */
    val cameraYawDeg: Float,
    /**
     * false → camera was too far off the required side view at this rep: cues and
     * spoken feedback were skipped (trust rule); metrics are diagnostics only.
     */
    val placementOk: Boolean
)

data class SpokenFeedback(
    val timestampMs: Long,
    val message: String,
    /** null = positive reinforcement, not a correction. */
    val cue: FeedbackCue?
)

data class DrillAnalysisReport(
    val reps: List<RepAnalysis>,
    val feedback: List<SpokenFeedback>,
    /**
     * Session summary: false → at least half the reps had bad camera placement;
     * the UI should surface a "reposition camera" prompt. Per-rep detail is on
     * [RepAnalysis.placementOk].
     */
    val placementOk: Boolean
)

/**
 * Phase 2 exit-gate orchestrator: pose sequence → strokes → per-rep metrics →
 * baseline-rule cues → cadenced UA/EN feedback. Batch over fixtures now; the same
 * per-rep flow drives the live Android loop in Phase 3.
 *
 * Forehand drive requires the side camera. Yaw is resolved PER REP (the player
 * moves their feet between reps — design note 10): within [maxCameraYawDeg] it is
 * corrected via that rep's ViewGeometry.xScale; beyond it the rep gets no feedback.
 */
class ForehandDriveDrillAnalyzer(
    private val baseline: PersonalBaseline,
    private val rules: List<BaselineRule> = BaselineRuleFactory.defaultRules(baseline),
    private val handedness: Handedness = Handedness.RIGHT,
    private val lang: FeedbackLang = FeedbackLang.EN,
    private val cadence: FeedbackCadencePolicy = FeedbackCadencePolicy(),
    private val detector: StrokeDetector2D = StrokeDetector2D(),
    /** Explicit camera-yaw override applied to all reps; null → per-rep auto-estimate. */
    private val cameraYawDeg: Float? = null,
    private val maxCameraYawDeg: Float = DrillCalibrator.DEFAULT_MAX_CAMERA_YAW_DEG
) {

    fun analyze(sequence: PoseSequence2D): DrillAnalysisReport {
        // Detection on plain aspect (design note 10); per-rep corrected xScale below.
        // RepFilter keeps phantom peaks (ball pickup, hand wipe) out of feedback (L-03).
        val strokes = RepFilter.filter(
            detector.detect(sequence.frames, handedness, sequence.aspectRatio, sequence.intervalMs)
        )

        val reps = strokes.map { stroke ->
            val yaw = cameraYawDeg
                ?: CameraAngleEstimator.estimateYawForStroke(
                    sequence.frames, stroke, sequence.aspectRatio, sequence.intervalMs
                )
                ?: 0f
            val placementOk = abs(yaw) <= maxCameraYawDeg
            // Beyond the gate the 1/cos model is unreliable: fall back to plain aspect
            // (this rep's metrics become diagnostics only; no cues evaluated from them).
            val view = if (placementOk) ViewGeometry(sequence.aspectRatio, yaw)
                       else ViewGeometry(sequence.aspectRatio)
            val metrics = DrillMetrics.extractAtPeak(
                sequence.frames, stroke.peakFrame, handedness, view.xScale, sequence.intervalMs
            )
            val cues = if (placementOk) DrillFeedbackEngine.evaluateRep(metrics, baseline, rules)
                       else emptyList()
            RepAnalysis(stroke, metrics, cues, cameraYawDeg = yaw, placementOk = placementOk)
        }

        val feedback = mutableListOf<SpokenFeedback>()
        for (rep in reps) {
            if (!rep.placementOk) continue // silent rep; UI surfaces the placement flag
            val atMs = rep.stroke.endFrame * sequence.intervalMs
            val cue = cadence.offer(atMs, rep.cues)
            when {
                cue != null ->
                    feedback += SpokenFeedback(atMs, FeedbackMessageCatalog.format(cue, lang), cue)
                rep.cues.isEmpty() && rep.metrics.isNotEmpty() && cadence.offerPositive(atMs) ->
                    feedback += SpokenFeedback(atMs, FeedbackMessageCatalog.positive(lang), null)
            }
        }

        val okCount = reps.count { it.placementOk }
        return DrillAnalysisReport(
            reps = reps,
            feedback = feedback,
            placementOk = reps.isEmpty() || okCount * 2 >= reps.size
        )
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.DrillCalibratorTest"`
Expected: PASS (8 tests). Two known sensitivities if it fails (note: the default synthetic frames put shoulders 0.02 apart with torso ~0.25, so the per-rep auto-estimated yaw is ~5° — under the gate, and identical between calibration and analysis since the reps share geometry, so corrections cancel). A third sensitivity in `playerTurningMidSessionGatesOnlyTheTurnedReps`: the gate verdict for the FIRST turned rep depends on where the detector places its `startFrame` (the lookback window mixes both stances) — that rep is deliberately unasserted; only the second turned rep, whose lookback is fully in the turned stance, is asserted. If reps[4] is unexpectedly placement-OK, print `report.reps.map { it.cameraYawDeg }` and check the detected stroke boundaries first:
- `calibratesBaselineFromRepeatedReps` fails with "Insufficient valid reps": the synthetic swing's peak speed (~2.4 torso-lengths/sec at torso 0.25, interval 100 ms) is below the (possibly retuned in Task 6) `minPeakSpeed`, or rep boundaries merge — print `detector.detect(...)` output and widen the still gap between reps (add more 0.50f frames).
- `analyzerIsQuietOnReps...` fails: tiny stds make 2σ bands razor-thin and float noise crosses them — increase the jitter spread (e.g. 0.40f/0.402f/0.398f/0.401f/0.399f) so the band is wider than numeric noise.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillCalibrator.kt \
        shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/ForehandDriveDrillAnalyzer.kt \
        shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/DrillCalibratorTest.kt
git commit -m "feat(shared): drill calibrator + forehand-drive analyzer orchestration"
```

---

### Task 14: Exit gate — end-to-end on real RTMPose fixture

**Files:**
- Test: `shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/ForehandDriveEndToEndTest.kt`

Spec exit gate: "drill analysis produces correct cues on labeled fixture reps (unit-tested)". The fixture footage predates the camera-placement protocol, so "labels" here are constructed by perturbation: a baseline derived from the fixture's own reps must stay quiet on those reps, and a baseline shifted by a known amount must produce the matching directional cue. That validates cue correctness end-to-end without trusting untuned absolute ranges.

- [ ] **Step 1: Write the test**

Replace `KNOWN_REP_COUNT` below with the stroke count printed in Task 6, Step 3.

```kotlin
package com.ttcoachai.shared.drill

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.analysis.CameraAngleEstimator
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.MetricStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForehandDriveEndToEndTest {

    // RAW detector count from the Task 6 diagnostic run against andrii_1_rtm.json —
    // an upper bound: RepFilter (L-03) may drop junk peaks before calibration/analysis.
    // Update if detector tuning changes.
    private val KNOWN_REP_COUNT = -1 // TODO(Task 6): set the observed count before merging

    // The fixture predates the camera-placement protocol, so its true yaw is unknown —
    // tests pin cameraYawDeg = 0f (treat fixture geometry as reference) instead of letting
    // the estimator gate feedback on footage we can't re-shoot. The estimator itself is
    // exercised separately below.
    private fun calibrated() = DrillCalibrator.calibrate(
        sequence = TestFixturesV2.loadAndriiRtm(),
        drillType = "forehand_drive",
        createdAtMs = 1L,
        handedness = Handedness.RIGHT,
        minRepCount = 3, // fixture has fewer than the production 10 reps
        cameraYawDeg = 0f
    )

    @Test
    fun calibrationProducesUsableBaseline() {
        val baseline = calibrated()
        // RepFilter may drop junk peaks, so the raw detector count is an upper bound
        assertTrue(
            baseline.repCount + baseline.excludedRepIndices.size <= KNOWN_REP_COUNT,
            "reps seen by calibration (${baseline.repCount} + ${baseline.excludedRepIndices.size} excluded) " +
                "must not exceed raw detector count $KNOWN_REP_COUNT"
        )
        assertTrue(baseline.repCount >= 3)
        assertTrue(baseline.metricStats.isNotEmpty(), "at least some in-plane metrics must derive")
        assertTrue(baseline.qualityScore in 0.0..1.0)
        println("baseline metrics: ${baseline.metricStats.mapValues { it.value.mean }}")
    }

    @Test
    fun ownBaselineStaysMostlyQuietOnOwnReps() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val baseline = calibrated()
        val report = ForehandDriveDrillAnalyzer(baseline = baseline, cameraYawDeg = 0f).analyze(seq)
        // Same detector, same RepFilter, same yaw override → the analyzer must see
        // exactly the rep set calibration derived from (kept + outlier-excluded).
        assertEquals(baseline.repCount + baseline.excludedRepIndices.size, report.reps.size)
        // Only reps excluded as outliers during derivation may trigger cues.
        val flagged = report.reps.withIndex().filter { it.value.cues.isNotEmpty() }.map { it.index }
        assertTrue(
            flagged.all { it in baseline.excludedRepIndices },
            "non-outlier reps must not be flagged by their own baseline; flagged=$flagged, " +
                "excluded=${baseline.excludedRepIndices}"
        )
    }

    @Test
    fun shiftedBaselineProducesCorrectDirectionalCueInBothLanguages() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val real = calibrated()
        val elbow = real.metricStats[DrillMetrics.METRIC_ELBOW_ANGLE]
            ?: error("fixture must yield elbow angles — check score gating thresholds")

        // Pretend the player's usual elbow is 30° straighter → every rep reads TOO_LOW.
        val shifted = real.copy(
            metricStats = real.metricStats + (DrillMetrics.METRIC_ELBOW_ANGLE to
                MetricStats(elbow.mean + 30.0, elbow.std, elbow.min + 30.0, elbow.max + 30.0, elbow.sampleCount))
        )

        for (lang in FeedbackLang.entries) {
            val report = ForehandDriveDrillAnalyzer(baseline = shifted, lang = lang, cameraYawDeg = 0f).analyze(seq)
            val elbowCues = report.reps.flatMap { it.cues }.filter { it.metricKey == DrillMetrics.METRIC_ELBOW_ANGLE }
            assertTrue(elbowCues.isNotEmpty(), "shifted baseline must flag elbow on real reps")
            assertTrue(elbowCues.all { it.direction == CueDirection.TOO_LOW }, "direction must match the shift")
            val spokenElbow = report.feedback.filter { it.cue?.metricKey == DrillMetrics.METRIC_ELBOW_ANGLE }
            assertTrue(spokenElbow.isNotEmpty(), "elbow cue must reach the voice channel")
            assertTrue(spokenElbow.all { "°" in it.message }, "in-plane cue must carry degrees ($lang)")
        }
    }

    @Test
    fun spokenFeedbackRespectsCadenceOnRealTimeline() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val real = calibrated()
        val elbow = real.metricStats[DrillMetrics.METRIC_ELBOW_ANGLE]!!
        val shifted = real.copy(
            metricStats = real.metricStats + (DrillMetrics.METRIC_ELBOW_ANGLE to
                MetricStats(elbow.mean + 30.0, elbow.std, elbow.min + 30.0, elbow.max + 30.0, elbow.sampleCount))
        )
        val report = ForehandDriveDrillAnalyzer(baseline = shifted, cameraYawDeg = 0f).analyze(seq)
        report.feedback.zipWithNext().forEach { (a, b) ->
            assertTrue(b.timestampMs - a.timestampMs >= 3000, "cadence violated: ${a.timestampMs}→${b.timestampMs}")
        }
    }

    @Test
    fun cameraYawEstimatesOnRealFootageAndGateSkipsFeedback() {
        val seq = TestFixturesV2.loadAndriiRtm()

        // Diagnostic: what does the estimator say about this (non-protocol) footage?
        val estimated = CameraAngleEstimator.estimateSideViewYawDeg(seq.frames, seq.aspectRatio)
        println("andrii_1_rtm estimated camera yaw: $estimated°")
        assertTrue(estimated != null && estimated in 0f..90f, "estimator must produce a value on real footage")

        // Forcing a beyond-gate yaw must skip all feedback regardless of baseline.
        val gated = ForehandDriveDrillAnalyzer(baseline = calibrated(), cameraYawDeg = 45f).analyze(seq)
        assertFalse(gated.placementOk)
        assertTrue(gated.feedback.isEmpty(), "feedback must be skipped beyond the placement gate")
        assertTrue(gated.reps.isNotEmpty(), "reps still reported for diagnostics")
    }
}
```

- [ ] **Step 2: Set `KNOWN_REP_COUNT`** from the Task 6 output, run, and iterate

Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ForehandDriveEndToEndTest"`
Expected: PASS. Failure modes and fixes:
- Fewer than 3 strokes detected → calibration throws: revisit detector thresholds (Task 6) — this fixture must yield ≥3 reps or it cannot serve as the Phase 2 fixture; if it truly has fewer reps, swap in another `Videos/` clip exported via `scripts/poses/export_poses_rtmpose.py` and update Task 3.
- `ownBaselineStaysMostlyQuietOnOwnReps` flags non-outlier reps: a metric's per-rep value crosses 2σ even though derivation kept the rep (possible because exclusion uses initial stats, evaluation uses final stats). If observed, relax the assertion to allow at most 1 such rep and leave a comment explaining the initial-vs-final-stats asymmetry — this mirrors how the 003 path already behaves.

- [ ] **Step 3: Commit**

```bash
git add shared/src/jvmTest/kotlin/com/ttcoachai/shared/drill/ForehandDriveEndToEndTest.kt
git commit -m "test(shared): Phase 2 exit gate — calibrate/analyze E2E on RTMPose fixture"
```

---

### Task 15: Full verification + docs

**Files:**
- Modify: `docs/pose_json_schema_v2.md` (consumer note)
- Modify: `docs/DESIGN_LIMITATIONS.md` (L-01/L-02 → Resolved)
- Modify: `CLAUDE.md` (Recent Changes + file map)

- [ ] **Step 1: Run the full shared suite and the app unit tests**

```bash
./gradlew :shared:jvmTest && ./gradlew :app:test
```
Expected: BUILD SUCCESSFUL, zero failures. `:app:test` guards the `BaselineDeriver` refactor (app code constructs baselines via the unchanged `derive` signature).

- [ ] **Step 2: Update `docs/pose_json_schema_v2.md`**

In the **Compatibility** section, change:

```markdown
- **Consumer dispatch:** Consumers (poses_viewer now, the KMP loader in Phase 2) must dispatch on `topology`, defaulting to `mediapipe33` when the field is absent.
```
to:
```markdown
- **Consumer dispatch:** Consumers must dispatch on `topology`, defaulting to `mediapipe33` when the field is absent. Implemented consumers: poses_viewer (renders coco17/halpe26) and the shared KMP loader `com.ttcoachai.shared.io.PoseJsonV2Parser` (accepts coco17/halpe26, rejects legacy v1 explicitly with `PoseSchemaException`).
```

- [ ] **Step 3: Update `docs/DESIGN_LIMITATIONS.md`**

Move the resolved entries to the Resolved section, each with its resolving commit hash (the register's convention — entries move, they are not deleted):
- **L-01** (wrist speed not body-size normalized) — Task 5 commit (torso-lengths/sec)
- **L-02** (10 fps fixtures / frame-count tuning) — Task 3 commit (full-fps re-export) + Task 5 commit (ms-based tuning)
- **L-03** (every peak treated as a rep) — Task 13's RepFilter commit
- **L-04** (torso-lean sign image-relative) — Task 4 commit (facing-normalized sign)
- **L-05** (unsmoothed single-frame peak metrics) — Task 7 commit (`extractAtPeak` median window)
- **L-08** (exporter ignores rotation metadata) — Task 3 commit (decoded-frame dimensions)

While there, re-check that **L-06** (knee occlusion) and **L-07** (multi-person identity) still describe the shipped code accurately; update their plan refs if task numbers shifted.

- [ ] **Step 4: Update `CLAUDE.md`**

Add to **Recent Changes** (top of the list):

```markdown
- 2d-phase2: Drill logic in shared KMP (2D pivot Phase 2). `models/` 2D types (`Keypoint2D`, `PoseFrame2D`, `PoseSequence2D`, `Topology`, `Coco17`, `Handedness`, `Stroke2D`, `ViewGeometry`); `io/PoseJsonV2Parser` (schema-v2, strict topology validation); `analysis/AngleCalculations2D` (score-gated in-plane angles, xScale-corrected, facing-normalized torso lean — L-04); `analysis/CameraAngleEstimator` (side-view yaw from shoulder foreshortening, |yaw| only); `detection/StrokeDetector2D` (wrist-speed local maximum; speeds in torso-lengths/sec, tuning in ms — L-01/L-02); `BaselineDeriver.deriveFromMetrics` (generic entry, 003 path unchanged); `drill/` package (DrillMetrics w/ `extractAtPeak` ±70ms median — L-05, RepFilter — L-03, SanityBounds, MetricPrecisionPolicy, DrillFeedbackEngine, FeedbackMessageCatalog UA+EN, FeedbackCadencePolicy 3–5s, DrillCalibrator w/ CameraPlacementException, ForehandDriveDrillAnalyzer w/ placement gate). RTMPose fixtures in `shared/src/commonTest/resources/fixtures/*_rtm.json`, loaded via `TestFixturesV2` (jvmTest).
```

Add three gotchas to **Gotchas**:

```markdown
- **Schema-v2 x/y are normalized by different axes** — multiply x-deltas by `ViewGeometry.xScale` (aspect ratio × camera-yaw correction) before any angle/distance math, or angles are distorted (712×1280 squashes x by 0.556). All `AngleCalculations2D`/`StrokeDetector2D` functions take xScale; never compute geometry on raw normalized coords. [ViewGeometry.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/models/ViewGeometry.kt)
- **Camera yaw is per-rep, |yaw| only, and estimated from the PRE-stroke window** — the player moves their feet between reps, so `CameraAngleEstimator.estimateYawForStroke` re-estimates before every stroke (estimating during the swing would misread the player's own body rotation as camera placement); poses_viewer's z-based yaw math can't be ported (COCO-17 has no z), and foreshortening can't recover the sign (1/cos doesn't need it). Beyond ~30° (`DrillCalibrator.DEFAULT_MAX_CAMERA_YAW_DEG`) a rep is excluded from calibration (`CameraPlacementException` when too few remain) and gets no feedback during analysis (`RepAnalysis.placementOk = false`). [CameraAngleEstimator.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/CameraAngleEstimator.kt)
- **`SanityBounds` drops, never coaches** — out-of-band values (e.g. elbow outside 20–170°) are treated as tracking glitches and removed from the rep's metrics; feedback always compares against the personal baseline, not hand-coded ranges. [SanityBounds.kt](shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/SanityBounds.kt)
```

- [ ] **Step 5: Commit**

```bash
git add docs/pose_json_schema_v2.md docs/DESIGN_LIMITATIONS.md CLAUDE.md
git commit -m "docs: record Phase 2 shared drill logic; resolve L-01/L-02 in limitations register"
```

---

## Out of scope (explicitly)

- Backswing-phase segmentation (needs wrist-direction-reversal analysis; deferred until protocol footage exists)
- Streaming/live detector wrapper, `PoseBackend` interface, TTS — all Phase 3. Camera-placement **UX** (onboarding prompt, live "reposition camera" warning) is Phase 3 too, but its estimation core (`CameraAngleEstimator`) and the gating policy ship here
- Signed camera yaw / full perspective correction — needs 3D or multi-frame structure; the 1/cos foreshortening model is the deliberate v1
- Tuning reference ranges on real protocol footage — blocked on founder recording (spec "Content dependency")
- Room persistence of 2D baselines, drill-config overrides integration — Stage 1 Phase 2 rule-evaluator work
- Any changes to frozen ball-tracking / MediaPipe live pipeline code
