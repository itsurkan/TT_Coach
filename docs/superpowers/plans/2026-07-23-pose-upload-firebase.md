# Pose data upload to Firebase — implementation plan

**For agentic workers:** execute this plan task-by-task via `superpowers:subagent-driven-development` — one fresh subagent per task, review between tasks, never inline in the main session (per project CLAUDE.md). Each task ends in a commit. Do not skip steps, do not "batch" multiple tasks' commits together, do not add anything not in this plan (no gold-plating, no adjacent fixes).

## Goal

Every RTM training session captures its full per-frame pose stream (schema-v2 compact JSON,
COCO-17, `.json.gz`) locally during the session and uploads it to Firebase Storage in the
background via WorkManager, without the player waiting for it. Ship alongside it: a Settings
consent toggle (default ON), Cloud Storage security rules scoped per-user, and real published
privacy/terms pages the app links to instead of the current placeholder `ttcoach.ai` URLs.

Source of truth: `docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md` (read first —
this plan implements it verbatim, no redesign).

## Architecture

```
session start (RtmposeTrainingController.start(), only if SettingsManager.isPoseUploadEnabled())
  -> PoseSessionRecorder created (provisional file, not yet started)
per frame (RtmposeTrainingController.onPoseResult, after the isTrainingActive early-return)
  -> recorder.onFrame(keypoints, timestampMs)      [fire-and-forget, single-thread IO dispatcher]
  -> PoseJsonV2Writer.frameLine(...) appended to a plain temp file (cap: 60,000 frames)
session stop (TrainingActivity.stopTraining)
  discard=true  -> rtmController.abortRecording() -> temp file deleted, nothing enqueued
  discard=false -> rtmController.finishRecording() -> gzip .json.gz written, temp deleted
                 -> CloudSyncManager.saveTrainingFromState(...).onSaved(sessionId)
                 -> rename file to <sessionId>.json.gz
                 -> PoseUploadQueue.enqueue(context, userId, sessionId, file)
                      -> WorkManager unique work "pose-upload-<sessionId>", ANY network,
                         default exponential backoff
                 -> PoseUploadWorker.doWork() -> PoseDataRepository.uploadPoseFile(...)
                      -> success: Firestore sessions/{id}.poseDataPath set, local file deleted
                      -> failure: Result.retry()
app start (TTCoachApplication.onCreate)
  -> PoseUploadQueue.sweepOrphans(context)   (re-enqueue any leftover <sessionId>.json.gz)
  -> PoseUploadQueue.evictOldCache(context)  (delete files >7 days old / dir >200MB, oldest first)
```

## Tech Stack

- Kotlin 2.2.21, `shared/` KMP module (zero external deps — pure Kotlin, no `kotlinx-serialization`,
  no `org.json`, no `java.*`, no `String.format`; `commonMain` has no ClassLoader).
- `app/`: Android, Room 2.6.1, Firebase BOM 34.8.0 (`firebase-storage`, `firebase-firestore`,
  `firebase-auth`), `kotlinx-coroutines-android` 1.10.2, `org.json` (test-only), JUnit 4.13.2.
- New: `androidx.work:work-runtime-ktx:2.9.1` — first WorkManager use in this app.
- No version catalog exists (`app/build.gradle` uses inline `implementation '...'` version
  strings, some via a local `def xxx_version = '...'` — see Task 3, Step 1).

## Global Constraints

- `shared/` has **zero external dependencies** — no kotlinx-serialization, no org.json, no
  `java.*`. `commonMain` has no `String.format` and no ClassLoader. Use `kotlin.math`.
- Resource-loading tests go in `shared/src/jvmTest`, **never** `commonTest`.
- `git add` **explicit paths only** — NEVER `git add -A` (working tree carries unrelated
  artifacts: `node_modules/.vite/`, `tsconfig.tsbuildinfo`, other uncommitted work).
- Commit after each task (not each step — one commit per Task N, per project convention).
- Test commands:
  - `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2WriterTest"`
  - `./gradlew :app:testDebugUnitTest --tests "<FQCN>"` — ALWAYS scope with `--tests`; the full
    app suite has a KNOWN pre-existing failure in frozen legacy code (`MotionAnalyzerJsonTest`)
    that is NOT a regression from this work.
  - `./gradlew :app:assembleDebug` for compile checks.
- New user-facing strings land in BOTH `app/src/main/res/values/strings.xml` (EN) and
  `app/src/main/res/values-uk/strings.xml` (UA) in the same task.
- Frozen code (ball tracking, MediaPipe live pipeline, trajectory, `LiveDrillSession`'s trim,
  `TrainingStateManager`'s rep ring) must not be modified. This plan does not touch any of it.
- The marketing-site work (Task 7a) is in a **separate repo**
  (`/Users/itsurkan/Dev/personal/TT_Coach_AA_site`) and gets its own commit there, independent of
  the TT_Coach repo commits.

### Deviations from the prompt's task numbering (deliberate, to keep every task independently compiling)

1. **`SettingsManager.isPoseUploadEnabled()`/`setPoseUploadEnabled()`** is added in **Task 2**
   (end of the `PoseSessionRecorder` task), not in the consent-UI task (Task 5), because Task 4
   (wiring) needs it to compile.
2. **The original "Task 3" (wire the recorder into the live path) and "Task 4" (WorkManager +
   Repository + Worker) are swapped** from the prompt's order: WorkManager plumbing is now
   **Task 3**, wiring is now **Task 4**. Reason: Task 4's wiring calls `PoseUploadQueue.enqueue(...)`,
   which does not exist until the WorkManager task runs — so the WorkManager task must come first.
   `PoseUploadQueue` (with `enqueue`/`cancelAll`) is created in Task 3, not deferred to Task 8;
   Task 8 only **extends** it with `sweepOrphans`/`evictOldCache`.

Every task below is self-contained and compiles/tests green on its own, in the order given.

## File Structure

```
shared/src/commonMain/kotlin/com/ttcoachai/shared/io/
  PoseJsonV2Writer.kt                         [CREATE — Task 1]
shared/src/jvmTest/kotlin/com/ttcoachai/shared/io/
  PoseJsonV2WriterTest.kt                     [CREATE — Task 1]

app/src/main/java/com/ttcoachai/pose/
  PoseSessionRecorder.kt                      [CREATE — Task 2]
  RtmposeTrainingController.kt                [MODIFY — Task 4]
app/src/test/java/com/ttcoachai/pose/
  PoseSessionRecorderTest.kt                  [CREATE — Task 2]

app/src/main/java/com/ttcoachai/managers/
  SettingsManager.kt                          [MODIFY — Task 2]
app/src/test/java/com/ttcoachai/settings/
  SettingsManagerTuningTest.kt                [MODIFY — Task 2]

app/build.gradle                              [MODIFY — Task 3]
app/src/main/java/com/ttcoachai/repository/
  PoseDataRepository.kt                       [MODIFY — Task 3]
app/src/main/java/com/ttcoachai/work/
  PoseUploadTask.kt                           [CREATE — Task 3]
  PoseUploadWorker.kt                         [CREATE — Task 3]
  PoseUploadQueue.kt                          [CREATE — Task 3, MODIFY — Task 8]
  PoseCacheEviction.kt                        [CREATE — Task 8]
app/src/test/java/com/ttcoachai/work/
  PoseUploadTaskTest.kt                       [CREATE — Task 3]
  PoseCacheEvictionTest.kt                    [CREATE — Task 8]

app/src/main/java/com/ttcoachai/
  TrainingActivity.kt                         [MODIFY — Task 4]
  AppSettingsActivity.kt                      [MODIFY — Task 5]
  TTCoachApplication.kt                       [MODIFY — Task 8]
  core/LegalLinks.kt                          [MODIFY — Task 7b]
app/src/main/res/layout/
  activity_app_settings.xml                   [MODIFY — Task 5]
app/src/main/res/values/strings.xml           [MODIFY — Task 5]
app/src/main/res/values-uk/strings.xml        [MODIFY — Task 5]

storage.rules                                 [CREATE — Task 6, repo root]
firebase.json                                 [MODIFY — Task 6]

/Users/itsurkan/Dev/personal/TT_Coach_AA_site/privacy.html   [CREATE — Task 7a, separate repo]
/Users/itsurkan/Dev/personal/TT_Coach_AA_site/terms.html     [CREATE — Task 7a, separate repo]
/Users/itsurkan/Dev/personal/TT_Coach_AA_site/src/cta.jsx    [MODIFY — Task 7a, separate repo]
```

---

### Task 1: `PoseJsonV2Writer` in shared + round-trip tests

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ttcoachai/shared/io/PoseJsonV2Writer.kt`
- Test: `shared/src/jvmTest/kotlin/com/ttcoachai/shared/io/PoseJsonV2WriterTest.kt`

**Interfaces:**
- Produces:
  - `object PoseJsonV2Writer { fun header(topology: Topology, model: String, videoName: String, intervalMs: Long, totalFrames: Int, videoDurationMs: Long, videoWidth: Int, videoHeight: Int): String }`
  - `fun frameLine(frame: PoseFrame2D, isFirst: Boolean): String`
  - `fun footer(): String`
  - `internal fun round4(value: Float): String`
- Consumes: `com.ttcoachai.shared.models.{PoseFrame2D, Keypoint2D, Topology}`,
  `com.ttcoachai.shared.io.PoseJsonV2Parser.parse(json: String): PoseSequence2D`,
  `com.ttcoachai.shared.TestFixturesV2.loadVideo2Rtm(): PoseSequence2D` (jvmTest-only).

#### Step 1: Create a compiling skeleton so the first test can target real symbols

- [ ] **Step 1: Create `PoseJsonV2Writer.kt` with a stub `round4`**

  ```kotlin
  package com.ttcoachai.shared.io

  import com.ttcoachai.shared.models.PoseFrame2D
  import com.ttcoachai.shared.models.Topology

  /**
   * Writer for pose JSON schema v2 (docs/pose_json_schema_v2.md). Pure Kotlin, no dependencies
   * (shared-module convention) — a streaming mirror of [PoseJsonV2Parser], intentionally NOT a
   * shared abstraction with it: a bug in one must not be able to silently corrupt the other's
   * contract. Landmark field order (index, x, y, score) is load-bearing — see
   * [PoseJsonV2Parser.LANDMARK_RE] — and is hardcoded here, not configurable.
   *
   * Streaming usage: emit [header] once, then [frameLine] for every frame (isFirst=true only for
   * the very first one, to control the leading comma), then [footer] once. The caller never
   * needs the whole document in memory — see PoseSessionRecorder (app/.../pose/PoseSessionRecorder.kt).
   */
  object PoseJsonV2Writer {

      internal fun round4(value: Float): String {
          TODO("Task 1 Step 2")
      }
  }
  ```

- [ ] **Step 2: Write the failing `round4` edge-case test**

  Create `shared/src/jvmTest/kotlin/com/ttcoachai/shared/io/PoseJsonV2WriterTest.kt`:

  ```kotlin
  package com.ttcoachai.shared.io

  import kotlin.test.Test
  import kotlin.test.assertEquals

  class PoseJsonV2WriterTest {

      @Test
      fun round4MatchesPythonRoundPlusJsonDumps() {
          val cases = listOf(
              0f to "0.0",
              1f to "1.0",
              -1f to "-1.0",
              0.5999f to "0.5999",
              0.896f to "0.896",       // trailing zero trimmed — not "0.8960"
              -0.0125f to "-0.0125",
              0.99995f to "1.0",       // rounds up across the integer boundary
              -0.99995f to "-1.0",
              0.1f to "0.1",
          )
          for ((input, expected) in cases) {
              assertEquals(expected, PoseJsonV2Writer.round4(input), "round4($input)")
          }
      }
  }
  ```

  Run it:

  ```
  ./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2WriterTest"
  ```

  Expected: **FAILS** — `kotlin.NotImplementedError: An operation is not implemented: Task 1 Step 2`
  thrown from `round4`, surfaced as a test failure for `round4MatchesPythonRoundPlusJsonDumps`.

- [ ] **Step 3: Implement `round4`**

  Replace the `round4` stub in `PoseJsonV2Writer.kt`:

  ```kotlin
      /**
       * Reproduces Python's `round(v, 4)` + `json.dumps()` on a 4-decimal value: trailing zeros
       * trimmed ("0.896" not "0.8960"), "0.0"/"1.0" kept as a single trailing zero, negatives
       * handled, half-up rounding at the 4th decimal (0.99995 -> "1.0"). No `String.format`
       * (commonMain has none) — built from integer arithmetic only, via [kotlin.math.floor].
       *
       * Multiplying a Float by 10000 and adding 0.5 before flooring can, in principle, land on
       * the wrong side of a boundary if the Float's binary-representation error happens to be
       * comparable to the 0.5 slack — but every value this function sees is either already a
       * decimal rounded to <=4 places (schema-v2 coordinates round-tripping through this writer)
       * or a live keypoint coordinate in roughly [-1, 2], both far below the precision floor
       * needed to trip that edge case.
       */
      internal fun round4(value: Float): String {
          val negative = value < 0f
          val absValue = if (negative) -value.toDouble() else value.toDouble()
          val scaled = kotlin.math.floor(absValue * 10000.0 + 0.5).toLong()
          val intPart = scaled / 10000L
          val fracPart = scaled % 10000L
          var fracStr = fracPart.toString().padStart(4, '0').trimEnd('0')
          if (fracStr.isEmpty()) fracStr = "0"
          val sign = if (negative && scaled != 0L) "-" else ""
          return "$sign$intPart.$fracStr"
      }
  ```

  Run it:

  ```
  ./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2WriterTest"
  ```

  Expected: **PASSES**

  ```
  BUILD SUCCESSFUL in 6s
  3 actionable tasks: 3 executed
  ```

- [ ] **Step 4: Add stub `header`/`frameLine`/`footer`**

  Append to `PoseJsonV2Writer.kt` (inside the `object`, after `round4`):

  ```kotlin
      fun header(
          topology: Topology,
          model: String,
          videoName: String,
          intervalMs: Long,
          totalFrames: Int,
          videoDurationMs: Long,
          videoWidth: Int,
          videoHeight: Int
      ): String {
          TODO("Task 1 Step 6")
      }

      fun frameLine(frame: PoseFrame2D, isFirst: Boolean): String {
          TODO("Task 1 Step 6")
      }

      fun footer(): String {
          TODO("Task 1 Step 6")
      }
  ```

- [ ] **Step 5: Write the failing tripwire + round-trip tests**

  Append to `PoseJsonV2WriterTest.kt`:

  ```kotlin
  package com.ttcoachai.shared.io

  import com.ttcoachai.shared.TestFixturesV2
  import com.ttcoachai.shared.models.Keypoint2D
  import com.ttcoachai.shared.models.PoseFrame2D
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  class PoseJsonV2WriterTest {

      @Test
      fun round4MatchesPythonRoundPlusJsonDumps() {
          // ... (unchanged, see Step 2)
      }

      @Test
      fun landmarkFieldOrderIsIndexXYScore() {
          // Field-order tripwire: PoseJsonV2Parser.LANDMARK_RE matches this exact literal order.
          val frame = PoseFrame2D(frameIndex = 0, timestampMs = 0L, keypoints = listOf(Keypoint2D(0.5f, 0.25f, 0.9f)))
          val line = PoseJsonV2Writer.frameLine(frame, isFirst = true)
          assertTrue(
              line.contains("\"index\":0,\"x\":0.5,\"y\":0.25,\"score\":0.9"),
              "expected literal index,x,y,score order, got: $line"
          )
      }

      @Test
      fun roundTripsVideo2FixtureThroughParser() {
          val original = TestFixturesV2.loadVideo2Rtm()
          val sb = StringBuilder()
          sb.append(
              PoseJsonV2Writer.header(
                  topology = original.topology,
                  model = original.model,
                  videoName = original.videoName,
                  intervalMs = original.intervalMs,
                  totalFrames = original.totalFrames,
                  videoDurationMs = original.videoDurationMs,
                  videoWidth = original.videoWidth,
                  videoHeight = original.videoHeight
              )
          )
          original.frames.forEachIndexed { i, frame ->
              sb.append(PoseJsonV2Writer.frameLine(frame, isFirst = i == 0))
          }
          sb.append(PoseJsonV2Writer.footer())

          val reparsed = PoseJsonV2Parser.parse(sb.toString())
          assertEquals(original, reparsed)
      }
  }
  ```

  (Keep the existing `round4MatchesPythonRoundPlusJsonDumps` body from Step 2 — only the two new
  `@Test` methods and the two new imports are additions.)

  Run it:

  ```
  ./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2WriterTest"
  ```

  Expected: **FAILS** — `landmarkFieldOrderIsIndexXYScore` and `roundTripsVideo2FixtureThroughParser`
  both throw `kotlin.NotImplementedError: An operation is not implemented: Task 1 Step 6`;
  `round4MatchesPythonRoundPlusJsonDumps` still passes.

- [ ] **Step 6: Implement `header`/`frameLine`/`footer`**

  Replace the three stubs from Step 4:

  ```kotlin
      fun header(
          topology: Topology,
          model: String,
          videoName: String,
          intervalMs: Long,
          totalFrames: Int,
          videoDurationMs: Long,
          videoWidth: Int,
          videoHeight: Int
      ): String {
          return "{" +
              "\"schemaVersion\":2," +
              "\"topology\":\"${topology.jsonName}\"," +
              "\"model\":\"$model\"," +
              "\"videoName\":\"$videoName\"," +
              "\"intervalMs\":$intervalMs," +
              "\"totalFrames\":$totalFrames," +
              "\"videoDurationMs\":$videoDurationMs," +
              "\"videoWidth\":$videoWidth," +
              "\"videoHeight\":$videoHeight," +
              "\"frames\":["
      }

      /** One frame object, compact JSON. [isFirst] must be true only for the very first frame in
       *  the document — every other frame gets a leading comma so a stream of these concatenates
       *  into a valid JSON array body. */
      fun frameLine(frame: PoseFrame2D, isFirst: Boolean): String {
          val landmarks = StringBuilder()
          frame.keypoints.forEachIndexed { index, kp ->
              if (index > 0) landmarks.append(',')
              landmarks.append("{\"index\":").append(index)
                  .append(",\"x\":").append(round4(kp.x))
                  .append(",\"y\":").append(round4(kp.y))
                  .append(",\"score\":").append(round4(kp.score))
                  .append('}')
          }
          val prefix = if (isFirst) "" else ","
          return "$prefix{\"frameIndex\":${frame.frameIndex},\"timestampMs\":${frame.timestampMs},\"landmarks\":[$landmarks]}"
      }

      fun footer(): String = "]}"
  ```

  Run it:

  ```
  ./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2WriterTest"
  ```

  Expected: **PASSES**

  ```
  BUILD SUCCESSFUL in 7s
  3 actionable tasks: 3 executed
  ```

- [ ] **Step 7: Commit**

  ```
  git add shared/src/commonMain/kotlin/com/ttcoachai/shared/io/PoseJsonV2Writer.kt \
          shared/src/jvmTest/kotlin/com/ttcoachai/shared/io/PoseJsonV2WriterTest.kt
  git commit -m "feat(shared): add PoseJsonV2Writer, the streaming mirror of PoseJsonV2Parser"
  ```

---

### Task 2: `PoseSessionRecorder` (app) + `SettingsManager.isPoseUploadEnabled`

**Files:**
- Create: `app/src/main/java/com/ttcoachai/pose/PoseSessionRecorder.kt`
- Test: `app/src/test/java/com/ttcoachai/pose/PoseSessionRecorderTest.kt`
- Modify: `app/src/main/java/com/ttcoachai/managers/SettingsManager.kt`
- Modify: `app/src/test/java/com/ttcoachai/settings/SettingsManagerTuningTest.kt`

**Interfaces:**
- Produces:
  - `class PoseSessionRecorder(outputDir: File) { fun start(videoWidth: Int, videoHeight: Int); fun onFrame(keypoints: List<Keypoint2D>, timestampMs: Long); suspend fun finish(): File?; fun abort() }`
  - `companion object { const val MAX_FRAMES: Int; fun cacheDir(context: Context): File }`
  - `SettingsManager.isPoseUploadEnabled(): Boolean`, `SettingsManager.setPoseUploadEnabled(enabled: Boolean)`
- Consumes: `com.ttcoachai.shared.io.PoseJsonV2Writer.{header, frameLine, footer}`,
  `com.ttcoachai.shared.models.{Keypoint2D, PoseFrame2D, Topology}`

#### Step 1: SettingsManager flag first (small, independent, unblocks Task 4 later)

- [ ] **Step 1: Add `isPoseUploadEnabled`/`setPoseUploadEnabled` to `SettingsManager`**

  In `app/src/main/java/com/ttcoachai/managers/SettingsManager.kt`, add after the existing
  `isAudioFeedbackEnabled`/`setAudioFeedbackEnabled` pair (currently lines 28-29):

  ```kotlin
      // Pose data upload consent (docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md)
      fun isPoseUploadEnabled(): Boolean = prefs.getBoolean("pose_upload_enabled", true)
      fun setPoseUploadEnabled(enabled: Boolean) = prefs.edit().putBoolean("pose_upload_enabled", enabled).apply()
  ```

- [ ] **Step 2: Add the failing test**

  In `app/src/test/java/com/ttcoachai/settings/SettingsManagerTuningTest.kt`, add a new test
  method (this is a new assertion on an already-implemented method from Step 1, so per strict TDD
  ordering do this step *before* Step 1 — reorder: write the test first, watch it fail to compile
  because `isPoseUploadEnabled` doesn't exist yet, then add the method). Concretely:

  1. Add the test method below to the class, run it, see it fail to **compile** (unresolved
     reference `isPoseUploadEnabled`).
  2. Then apply Step 1's `SettingsManager.kt` change.
  3. Re-run — now it passes.

  ```kotlin
      @Test fun pose_upload_defaults_true_and_persists() {
          val sm = SettingsManager(ctx)
          assertEquals(true, sm.isPoseUploadEnabled())
          sm.setPoseUploadEnabled(false)
          val sm2 = SettingsManager(ctx)
          assertEquals(false, sm2.isPoseUploadEnabled())
      }
  ```

  Run it (before Step 1's implementation exists):

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.settings.SettingsManagerTuningTest"
  ```

  Expected: **FAILS to compile** —
  `e: SettingsManagerTuningTest.kt: unresolved reference: isPoseUploadEnabled`

- [ ] **Step 3: Apply Step 1's `SettingsManager.kt` change, then re-run**

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.settings.SettingsManagerTuningTest"
  ```

  Expected: **PASSES**

  ```
  BUILD SUCCESSFUL in 14s
  4 actionable tasks: 4 executed
  ```

- [ ] **Step 4: Commit**

  ```
  git add app/src/main/java/com/ttcoachai/managers/SettingsManager.kt \
          app/src/test/java/com/ttcoachai/settings/SettingsManagerTuningTest.kt
  git commit -m "feat(settings): add pose-upload consent flag, default ON"
  ```

#### Step 5-9: `PoseSessionRecorder`

- [ ] **Step 5: Create a compiling skeleton**

  ```kotlin
  package com.ttcoachai.pose

  import android.content.Context
  import com.ttcoachai.shared.io.PoseJsonV2Writer
  import com.ttcoachai.shared.models.Keypoint2D
  import com.ttcoachai.shared.models.PoseFrame2D
  import com.ttcoachai.shared.models.Topology
  import kotlinx.coroutines.CoroutineScope
  import kotlinx.coroutines.SupervisorJob
  import kotlinx.coroutines.asCoroutineDispatcher
  import kotlinx.coroutines.launch
  import kotlinx.coroutines.runBlocking
  import kotlinx.coroutines.withContext
  import java.io.BufferedWriter
  import java.io.File
  import java.util.UUID
  import java.util.concurrent.Executors
  import java.util.zip.GZIPOutputStream

  /**
   * Records every live RTM pose frame of one training session to a schema-v2 compact-JSON gzip
   * file, off the caller's thread. See
   * docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md, component B. Takes a plain
   * output [File] directory (not a Context) so tests can point it at a temp dir; real call sites
   * use [cacheDir].
   *
   * Writes go through a dedicated single-thread dispatcher so [onFrame] (called from the UI
   * thread via RtmposeTrainingController.onPoseResult) never blocks on file IO. [onFrame] calls
   * are fire-and-forget but always land on that same single thread in submission order — so
   * [finish], which is `suspend` and dispatches onto the same thread, is guaranteed to run after
   * every prior [onFrame] write has completed, with no extra synchronization needed.
   */
  class PoseSessionRecorder(private val outputDir: File) {

      companion object {
          private const val MODEL_NAME = "rtmpose-m"
          private const val CACHE_DIR_NAME = "pose_uploads"

          /** Hard cap: recording stops accepting frames past this many, but whatever was
           *  captured so far still finalizes normally. Backstop against unmeasured on-device
           *  fps (see spec Risks), not a target. */
          const val MAX_FRAMES = 60_000

          /** Real on-device output directory. Tests use their own temp [File] via the
           *  constructor instead. */
          fun cacheDir(context: Context): File = File(context.filesDir, CACHE_DIR_NAME)
      }

      private val provisionalId = "pose_${UUID.randomUUID()}"
      private val tempFile = File(outputDir, "$provisionalId.tmp")

      private val executor = Executors.newSingleThreadExecutor()
      private val dispatcher = executor.asCoroutineDispatcher()
      private val scope = CoroutineScope(SupervisorJob() + dispatcher)

      private var writer: BufferedWriter? = null
      private var videoWidth = 0
      private var videoHeight = 0
      private var frameCount = 0
      private var firstTimestampMs = 0L
      private var lastTimestampMs = 0L
      @Volatile private var started = false

      fun start(videoWidth: Int, videoHeight: Int) {
          TODO("Task 2 Step 7")
      }

      fun onFrame(keypoints: List<Keypoint2D>, timestampMs: Long) {
          TODO("Task 2 Step 7")
      }

      suspend fun finish(): File? {
          TODO("Task 2 Step 7")
      }

      fun abort() {
          TODO("Task 2 Step 7")
      }
  }
  ```

- [ ] **Step 6: Write the failing tests**

  Create `app/src/test/java/com/ttcoachai/pose/PoseSessionRecorderTest.kt`:

  ```kotlin
  package com.ttcoachai.pose

  import com.ttcoachai.shared.io.PoseJsonV2Parser
  import com.ttcoachai.shared.models.Keypoint2D
  import kotlinx.coroutines.runBlocking
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertTrue
  import org.junit.Rule
  import org.junit.Test
  import org.junit.rules.TemporaryFolder
  import java.io.File
  import java.util.zip.GZIPInputStream

  class PoseSessionRecorderTest {

      @get:Rule
      val tempFolder = TemporaryFolder()

      private fun coco17Frame(seed: Float = 0.5f): List<Keypoint2D> =
          (0 until 17).map { Keypoint2D(x = seed, y = seed, score = 0.9f) }

      private fun gunzip(file: File): String =
          GZIPInputStream(file.inputStream()).use { it.bufferedReader(Charsets.UTF_8).readText() }

      @Test
      fun framesWrittenThenFinishProducesValidFile() = runBlocking {
          val recorder = PoseSessionRecorder(tempFolder.newFolder())
          recorder.start(videoWidth = 640, videoHeight = 480)
          for (i in 0 until 5) {
              recorder.onFrame(coco17Frame(), timestampMs = i * 20L)
          }
          val finalFile = recorder.finish()
          requireNotNull(finalFile)
          assertTrue(finalFile.name.endsWith(".json.gz"))
          val seq = PoseJsonV2Parser.parse(gunzip(finalFile))
          assertEquals(5, seq.totalFrames)
          assertEquals(5, seq.frames.size)
          assertEquals(640, seq.videoWidth)
          assertEquals(480, seq.videoHeight)
      }

      @Test
      fun capStopsAcceptingFramesButFinishStillWorks() = runBlocking {
          val recorder = PoseSessionRecorder(tempFolder.newFolder())
          recorder.start(videoWidth = 640, videoHeight = 480)
          for (i in 0 until PoseSessionRecorder.MAX_FRAMES + 5) {
              recorder.onFrame(coco17Frame(), timestampMs = i * 20L)
          }
          val finalFile = recorder.finish()
          requireNotNull(finalFile)
          val seq = PoseJsonV2Parser.parse(gunzip(finalFile))
          assertEquals(PoseSessionRecorder.MAX_FRAMES, seq.totalFrames)
      }

      @Test
      fun abortDeletesTempFileAndLeavesNothingBehind() = runBlocking {
          val dir = tempFolder.newFolder()
          val recorder = PoseSessionRecorder(dir)
          recorder.start(videoWidth = 640, videoHeight = 480)
          recorder.onFrame(coco17Frame(), timestampMs = 0L)
          recorder.abort()
          assertTrue(dir.listFiles()?.isEmpty() ?: true)
      }
  }
  ```

  Run it:

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.pose.PoseSessionRecorderTest"
  ```

  Expected: **FAILS** — all three tests throw `kotlin.NotImplementedError: An operation is not
  implemented: Task 2 Step 7` (from `start`/`onFrame`/`finish`/`abort`).

- [ ] **Step 7: Implement `start`/`onFrame`/`finish`/`abort`**

  Replace the four stubs in `PoseSessionRecorder.kt`:

  ```kotlin
      fun start(videoWidth: Int, videoHeight: Int) {
          this.videoWidth = videoWidth
          this.videoHeight = videoHeight
          started = true
          outputDir.mkdirs()
          scope.launch {
              writer = tempFile.bufferedWriter()
          }
      }

      fun onFrame(keypoints: List<Keypoint2D>, timestampMs: Long) {
          if (!started) return
          scope.launch {
              val w = writer ?: return@launch
              if (frameCount >= MAX_FRAMES) return@launch
              val isFirst = frameCount == 0
              if (isFirst) firstTimestampMs = timestampMs
              lastTimestampMs = timestampMs
              w.write(PoseJsonV2Writer.frameLine(PoseFrame2D(frameCount, timestampMs, keypoints), isFirst))
              frameCount++
          }
      }

      /** Finalizes the recording: streams the temp file into a compact-JSON gzip with a real
       *  header (only known now — totalFrames/videoDurationMs are end-of-session facts), deletes
       *  the temp file, and returns the final file. Returns null if [start] was never called or
       *  zero frames were captured. */
      suspend fun finish(): File? {
          if (!started) return null
          val result = withContext(dispatcher) {
              writer?.flush()
              writer?.close()
              writer = null
              if (frameCount == 0) {
                  tempFile.delete()
                  return@withContext null
              }
              val totalFrames = frameCount
              val durationMs = (lastTimestampMs - firstTimestampMs).coerceAtLeast(0L)
              val intervalMs = if (totalFrames > 1) (durationMs / (totalFrames - 1)).coerceAtLeast(1L) else 1L
              val finalFile = File(outputDir, "$provisionalId.json.gz")
              GZIPOutputStream(finalFile.outputStream()).use { gz ->
                  gz.write(
                      PoseJsonV2Writer.header(
                          topology = Topology.COCO17,
                          model = MODEL_NAME,
                          videoName = "",
                          intervalMs = intervalMs,
                          totalFrames = totalFrames,
                          videoDurationMs = durationMs,
                          videoWidth = videoWidth,
                          videoHeight = videoHeight
                      ).toByteArray(Charsets.UTF_8)
                  )
                  tempFile.inputStream().use { it.copyTo(gz) }
                  gz.write(PoseJsonV2Writer.footer().toByteArray(Charsets.UTF_8))
              }
              tempFile.delete()
              finalFile
          }
          executor.shutdown()
          return result
      }

      /** Cancels any pending writes and deletes the temp file. Safe to call before [start] or
       *  instead of [finish] (session discarded). */
      fun abort() {
          runBlocking(dispatcher) {
              writer?.close()
              writer = null
          }
          tempFile.delete()
          executor.shutdown()
      }
  ```

  Run it:

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.pose.PoseSessionRecorderTest"
  ```

  Expected: **PASSES**

  ```
  BUILD SUCCESSFUL in 22s
  6 actionable tasks: 6 executed
  ```

- [ ] **Step 8: Compile check**

  ```
  ./gradlew :app:assembleDebug
  ```

  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 9: Commit**

  ```
  git add app/src/main/java/com/ttcoachai/pose/PoseSessionRecorder.kt \
          app/src/test/java/com/ttcoachai/pose/PoseSessionRecorderTest.kt
  git commit -m "feat(app): add PoseSessionRecorder — full-session pose capture to gzip JSON"
  ```

---

### Task 3: WorkManager dependency + `PoseDataRepository.uploadPoseFile` + `PoseUploadWorker` + `PoseUploadQueue`

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/java/com/ttcoachai/repository/PoseDataRepository.kt`
- Create: `app/src/main/java/com/ttcoachai/work/PoseUploadTask.kt`
- Create: `app/src/main/java/com/ttcoachai/work/PoseUploadWorker.kt`
- Create: `app/src/main/java/com/ttcoachai/work/PoseUploadQueue.kt`
- Test: `app/src/test/java/com/ttcoachai/work/PoseUploadTaskTest.kt`

**Interfaces:**
- Produces:
  - `PoseDataRepository.uploadPoseFile(userId: String, sessionId: String, file: File): Result<String>`
  - `class PoseUploadTask(uploadPoseFile: suspend (String, String, File) -> Result<String>, setPoseDataPath: suspend (String, String) -> Unit) { suspend fun run(userId: String, sessionId: String, file: File): Outcome }` with `sealed class Outcome { data class Success(val path: String); object Retry; object MissingFile }`
  - `class PoseUploadWorker(context: Context, params: WorkerParameters, ...) : CoroutineWorker`
  - `object PoseUploadQueue { const val TAG_POSE_UPLOAD: String; fun enqueue(context: Context, userId: String, sessionId: String, file: File); fun cancelAll(context: Context) }`
- Consumes: `com.google.firebase.storage.{FirebaseStorage, StorageMetadata}`,
  `com.google.firebase.firestore.FirebaseFirestore`, `com.ttcoachai.models.TrainingSession.COLLECTION`,
  `androidx.work.*`

#### Step 1: Add the WorkManager dependency

- [ ] **Step 1: Add `work-runtime-ktx` to `app/build.gradle`**

  In `app/build.gradle`, add to the `dependencies` block, after the Firebase block (currently
  ending at line 149, `implementation 'com.google.android.gms:play-services-auth:21.5.0'`):

  ```groovy
      // WorkManager (background pose-file upload; first use in this app)
      implementation 'androidx.work:work-runtime-ktx:2.9.1'
  ```

  (No version catalog exists in this repo — matches the file's existing inline-version-string
  style used for every other dependency.)

  Verify it resolves:

  ```
  ./gradlew :app:assembleDebug
  ```

  Expected: **BUILD SUCCESSFUL** (dependency downloads, nothing references it yet so nothing else
  changes).

#### Step 2-4: `PoseDataRepository.uploadPoseFile`

This method has no separate unit test file in this repo's convention (`PoseDataRepository`'s
existing methods are all thin Firebase SDK wrappers with no prior test coverage — see the class
as read). It is exercised indirectly through `PoseUploadTaskTest` via a fake lambda, and directly
by manual QA (spec is non-goals: no server-side test harness). Implement directly, verify by
compile + the `PoseUploadTaskTest` below.

- [ ] **Step 2: Add `uploadPoseFile` to `PoseDataRepository.kt`**

  In `app/src/main/java/com/ttcoachai/repository/PoseDataRepository.kt`, add imports at the top
  (after the existing `import java.io.ByteArrayOutputStream` — note that import is unused by the
  existing file and is left untouched, out of scope for this change):

  ```kotlin
  import android.net.Uri
  import com.google.firebase.storage.StorageMetadata
  import java.io.File
  ```

  Add the method after `uploadPoseDataJson` (after line 62, before `downloadPoseData` at line 64):

  ```kotlin
      /**
       * Upload a pose file (already-gzipped schema-v2 JSON) by streaming it from disk — unlike
       * [uploadPoseData]/[uploadPoseDataJson] (in-memory ByteArray/String), this never holds the
       * whole payload in memory, which is the entire point of a full-session on-disk recording
       * (see PoseSessionRecorder). Same path convention as [uploadPoseData]'s [POSES_FOLDER],
       * `.json.gz` extension instead of `.json`.
       */
      suspend fun uploadPoseFile(
          userId: String,
          sessionId: String,
          file: File
      ): Result<String> {
          return try {
              val path = "$POSES_FOLDER/$userId/$sessionId.json.gz"
              val ref = storage.reference.child(path)
              val metadata = StorageMetadata.Builder()
                  .setContentType("application/json")
                  .setContentEncoding("gzip")
                  .build()

              ref.putFile(Uri.fromFile(file), metadata).await()

              Log.d(TAG, "Pose file uploaded: $path (${file.length()} bytes)")
              Result.success(path)
          } catch (e: Exception) {
              Log.e(TAG, "Failed to upload pose file", e)
              Result.failure(e)
          }
      }
  ```

- [ ] **Step 3: Compile check**

  ```
  ./gradlew :app:assembleDebug
  ```

  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 4: Commit**

  ```
  git add app/build.gradle app/src/main/java/com/ttcoachai/repository/PoseDataRepository.kt
  git commit -m "feat(app): add WorkManager dep + PoseDataRepository.uploadPoseFile streaming upload"
  ```

#### Step 5-9: `PoseUploadTask` (TDD) + `PoseUploadWorker` + `PoseUploadQueue`

- [ ] **Step 5: Create a compiling `PoseUploadTask` skeleton**

  ```kotlin
  package com.ttcoachai.work

  import java.io.File

  /**
   * Pure upload-decision logic for [PoseUploadWorker], extracted so it's testable without
   * Robolectric/WorkManager test infra — see
   * docs/superpowers/plans/2026-07-23-pose-upload-firebase.md Task 3.
   */
  class PoseUploadTask(
      private val uploadPoseFile: suspend (userId: String, sessionId: String, file: File) -> Result<String>,
      private val setPoseDataPath: suspend (sessionId: String, path: String) -> Unit,
  ) {
      sealed class Outcome {
          data class Success(val path: String) : Outcome()
          object Retry : Outcome()
          object MissingFile : Outcome()
      }

      suspend fun run(userId: String, sessionId: String, file: File): Outcome {
          TODO("Task 3 Step 7")
      }
  }
  ```

- [ ] **Step 6: Write the failing tests**

  Create `app/src/test/java/com/ttcoachai/work/PoseUploadTaskTest.kt`:

  ```kotlin
  package com.ttcoachai.work

  import kotlinx.coroutines.runBlocking
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertTrue
  import org.junit.Rule
  import org.junit.Test
  import org.junit.rules.TemporaryFolder
  import java.io.File

  class PoseUploadTaskTest {

      @get:Rule
      val tempFolder = TemporaryFolder()

      @Test
      fun success_sets_pose_data_path_and_deletes_file() = runBlocking {
          val file = tempFolder.newFile("s1.json.gz").apply { writeText("data") }
          var savedSessionId: String? = null
          var savedPath: String? = null
          val task = PoseUploadTask(
              uploadPoseFile = { _, _, _ -> Result.success("poses/u1/s1.json.gz") },
              setPoseDataPath = { sid, path -> savedSessionId = sid; savedPath = path }
          )

          val outcome = task.run("u1", "s1", file)

          assertTrue(outcome is PoseUploadTask.Outcome.Success)
          assertEquals("poses/u1/s1.json.gz", (outcome as PoseUploadTask.Outcome.Success).path)
          assertEquals("s1", savedSessionId)
          assertEquals("poses/u1/s1.json.gz", savedPath)
          assertTrue("uploaded file should be deleted locally", !file.exists())
      }

      @Test
      fun failure_returns_retry_and_keeps_file() = runBlocking {
          val file = tempFolder.newFile("s2.json.gz").apply { writeText("data") }
          val task = PoseUploadTask(
              uploadPoseFile = { _, _, _ -> Result.failure(RuntimeException("network")) },
              setPoseDataPath = { _, _ -> }
          )

          val outcome = task.run("u1", "s2", file)

          assertEquals(PoseUploadTask.Outcome.Retry, outcome)
          assertTrue("file must survive a retry so WorkManager can try again", file.exists())
      }

      @Test
      fun missing_file_returns_missing_file_outcome() = runBlocking {
          val file = File(tempFolder.root, "does-not-exist.json.gz")
          val task = PoseUploadTask(
              uploadPoseFile = { _, _, _ -> Result.success("unused") },
              setPoseDataPath = { _, _ -> }
          )

          assertEquals(PoseUploadTask.Outcome.MissingFile, task.run("u1", "s3", file))
      }
  }
  ```

  Run it:

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.work.PoseUploadTaskTest"
  ```

  Expected: **FAILS** — all three tests throw `kotlin.NotImplementedError: An operation is not
  implemented: Task 3 Step 7`.

- [ ] **Step 7: Implement `PoseUploadTask.run`**

  Replace the stub in `PoseUploadTask.kt`:

  ```kotlin
      suspend fun run(userId: String, sessionId: String, file: File): Outcome {
          if (!file.exists()) return Outcome.MissingFile
          val result = uploadPoseFile(userId, sessionId, file)
          return result.fold(
              onSuccess = { path ->
                  setPoseDataPath(sessionId, path)
                  file.delete()
                  Outcome.Success(path)
              },
              onFailure = { Outcome.Retry }
          )
      }
  ```

  Run it:

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.work.PoseUploadTaskTest"
  ```

  Expected: **PASSES**

  ```
  BUILD SUCCESSFUL in 15s
  4 actionable tasks: 4 executed
  ```

- [ ] **Step 8: Create `PoseUploadWorker.kt`** (delegates to `PoseUploadTask`, no dedicated test —
  `CoroutineWorker` construction needs Robolectric/work-testing, out of scope per the extraction
  decision above; its only logic is the delegation, already covered by Step 7's tests)

  ```kotlin
  package com.ttcoachai.work

  import android.content.Context
  import androidx.work.CoroutineWorker
  import androidx.work.WorkerParameters
  import com.google.firebase.firestore.FirebaseFirestore
  import com.ttcoachai.models.TrainingSession
  import com.ttcoachai.repository.PoseDataRepository
  import kotlinx.coroutines.tasks.await
  import java.io.File

  /**
   * Background upload of one session's pose gzip file. See
   * docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md component C. Decision logic
   * lives in [PoseUploadTask] (tested directly, without WorkManager infra) — this class is only
   * the WorkManager glue: read [inputData], build the real collaborators, delegate, map the
   * [PoseUploadTask.Outcome] onto a WorkManager [Result].
   */
  class PoseUploadWorker(
      context: Context,
      params: WorkerParameters,
      private val repository: PoseDataRepository = PoseDataRepository(),
      private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
  ) : CoroutineWorker(context, params) {

      companion object {
          const val KEY_USER_ID = "userId"
          const val KEY_SESSION_ID = "sessionId"
          const val KEY_FILE_PATH = "filePath"
      }

      override suspend fun doWork(): Result {
          val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
          val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
          val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()

          val task = PoseUploadTask(
              uploadPoseFile = { uid, sid, f -> repository.uploadPoseFile(uid, sid, f) },
              setPoseDataPath = { sid, path ->
                  firestore.collection(TrainingSession.COLLECTION)
                      .document(sid)
                      .update("poseDataPath", path)
                      .await()
              }
          )

          return when (task.run(userId, sessionId, File(filePath))) {
              is PoseUploadTask.Outcome.Success -> Result.success()
              PoseUploadTask.Outcome.Retry -> Result.retry()
              PoseUploadTask.Outcome.MissingFile -> Result.failure()
          }
      }
  }
  ```

- [ ] **Step 9: Create `PoseUploadQueue.kt`** (enqueue-side API; no dedicated test — thin
  WorkManager builder-pattern glue, covered by the Task 4 compile check that exercises it end to
  end)

  ```kotlin
  package com.ttcoachai.work

  import android.content.Context
  import androidx.work.Constraints
  import androidx.work.Data
  import androidx.work.ExistingWorkPolicy
  import androidx.work.NetworkType
  import androidx.work.OneTimeWorkRequestBuilder
  import androidx.work.WorkManager
  import java.io.File

  /**
   * Enqueue-side API for background pose-file uploads (component C of
   * docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md). ANY network (not
   * Wi-Fi-only, per spec decision #3) and WorkManager's default exponential backoff (no
   * hand-tuned initial delay needed for v1). Unique work name per session so a retry or app
   * restart can't double-enqueue the same file.
   */
  object PoseUploadQueue {

      const val TAG_POSE_UPLOAD = "pose-upload"

      fun enqueue(context: Context, userId: String, sessionId: String, file: File) {
          val constraints = Constraints.Builder()
              .setRequiredNetworkType(NetworkType.CONNECTED)
              .build()
          val data = Data.Builder()
              .putString(PoseUploadWorker.KEY_USER_ID, userId)
              .putString(PoseUploadWorker.KEY_SESSION_ID, sessionId)
              .putString(PoseUploadWorker.KEY_FILE_PATH, file.absolutePath)
              .build()
          val request = OneTimeWorkRequestBuilder<PoseUploadWorker>()
              .setConstraints(constraints)
              .addTag(TAG_POSE_UPLOAD)
              .setInputData(data)
              .build()

          WorkManager.getInstance(context)
              .enqueueUniqueWork("pose-upload-$sessionId", ExistingWorkPolicy.KEEP, request)
      }

      /** Cancels all queued/running pose-upload work — called when the consent toggle is turned
       *  off (component D, Task 5). */
      fun cancelAll(context: Context) {
          WorkManager.getInstance(context).cancelAllWorkByTag(TAG_POSE_UPLOAD)
      }
  }
  ```

- [ ] **Step 10: Compile check**

  ```
  ./gradlew :app:assembleDebug
  ```

  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 11: Commit**

  ```
  git add app/src/main/java/com/ttcoachai/work/PoseUploadTask.kt \
          app/src/main/java/com/ttcoachai/work/PoseUploadWorker.kt \
          app/src/main/java/com/ttcoachai/work/PoseUploadQueue.kt \
          app/src/test/java/com/ttcoachai/work/PoseUploadTaskTest.kt
  git commit -m "feat(app): add PoseUploadTask/PoseUploadWorker/PoseUploadQueue (WorkManager upload path)"
  ```

---

### Task 4: Wire the recorder + upload queue into the live RTM path

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt`
- Modify: `app/src/main/java/com/ttcoachai/TrainingActivity.kt`

**Interfaces:**
- Produces: `RtmposeTrainingController.finishRecording(): File?` (suspend), `RtmposeTrainingController.abortRecording()`
- Consumes: `PoseSessionRecorder(outputDir: File)`, `.start(videoWidth: Int, videoHeight: Int)`,
  `.onFrame(keypoints: List<Keypoint2D>, timestampMs: Long)`, `.finish(): File?` (suspend),
  `.abort()`, `PoseSessionRecorder.cacheDir(context: Context): File`,
  `SettingsManager.isPoseUploadEnabled(): Boolean`, `PoseUploadQueue.enqueue(context, userId, sessionId, file)`,
  `CloudSyncManager.currentUserId: String?`

No dedicated unit test for this task — `RtmposeTrainingController`/`TrainingActivity` are
Android-framework-heavy (`FragmentActivity`, CameraX, ViewGroup) with no existing unit-test
coverage precedent in this repo (see `app/src/test/`: none of the wiring-level classes are
tested directly, only pure logic extracted from them). Verification is the compile check plus
the fact that every piece it calls (`PoseSessionRecorder`, `PoseUploadQueue`) is already unit
tested in Tasks 2-3. This matches the plan-prompt's own Task 3 verification step (compile only).

- [ ] **Step 1: Add recorder fields and frame-size capture to `RtmposeTrainingController`**

  In `app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt`, add an import at the
  top (after the existing `import java.util.concurrent.Executors` at line 45):

  ```kotlin
  import java.io.File
  ```

  Add new private fields, right after the existing `private var sessionCreated = false` (line 134):

  ```kotlin
      private var poseRecorder: PoseSessionRecorder? = null
      private var poseRecordingStarted = false
      @Volatile private var frameWidth: Int = 0
      @Volatile private var frameHeight: Int = 0
  ```

  In `bindCameraUseCases()`, extend the analyzer block that currently sets `aspectRatio` (lines
  227-234):

  ```kotlin
              .also {
                  it.setAnalyzer(executor) { imageProxy ->
                      val rot = imageProxy.imageInfo.rotationDegrees
                      val rotatedWidth = if (rot % 180 != 0) imageProxy.height else imageProxy.width
                      val rotatedHeight = if (rot % 180 != 0) imageProxy.width else imageProxy.height
                      if (rotatedHeight > 0) {
                          aspectRatio = rotatedWidth.toFloat() / rotatedHeight.toFloat()
                          frameWidth = rotatedWidth
                          frameHeight = rotatedHeight
                      }
                      processor?.analyze(imageProxy) ?: imageProxy.close()
                  }
              }
  ```

  (Only the two new assignment lines `frameWidth = rotatedWidth` / `frameHeight = rotatedHeight`
  are additions inside the existing `if` block.)

- [ ] **Step 2: Create the recorder in `start()` when consent is granted**

  In `start()`, after the `voiceController = voice` line (line 173), before
  `analysisExecutor = Executors.newSingleThreadExecutor()` (line 175):

  ```kotlin
          if (settingsManager.isPoseUploadEnabled()) {
              poseRecorder = PoseSessionRecorder(PoseSessionRecorder.cacheDir(activity))
          }

  ```

- [ ] **Step 3: Feed frames into the recorder from `onPoseResult`**

  In `onPoseResult` (line 252), right after the existing early-return line
  `if (!stateManager.isTrainingActive) return` (line 255), add:

  ```kotlin
          poseRecorder?.let { recorder ->
              if (!poseRecordingStarted) {
                  recorder.start(frameWidth, frameHeight)
                  poseRecordingStarted = true
              }
              recorder.onFrame(keypoints, timestampMs)
          }

  ```

  (This is the exact spec placement: "after the `stateManager.isTrainingActive` early-return".)

- [ ] **Step 4: Expose `finishRecording`/`abortRecording`**

  Add these two public methods to `RtmposeTrainingController`, near `stop()`/`release()` (after
  line 368, before `fun release()`):

  ```kotlin
      /** Finalizes the pose recording (if pose upload is enabled and recording started) and
       *  returns the resulting gzip file, or null if nothing was recorded. */
      suspend fun finishRecording(): File? = poseRecorder?.finish()

      /** Aborts and deletes any in-progress pose recording (session discarded). No-op if pose
       *  upload is disabled or recording never started. */
      fun abortRecording() {
          poseRecorder?.abort()
      }

  ```

- [ ] **Step 5: Wire `stopTraining(discard=true)` to abort the recording**

  In `app/src/main/java/com/ttcoachai/TrainingActivity.kt`, in `stopTraining` (lines 294-313),
  change the `discard` branch:

  ```kotlin
          if (discard) {
              rtmController?.abortRecording()
              android.widget.Toast.makeText(this, R.string.session_discarded, android.widget.Toast.LENGTH_SHORT).show()
              finish()
              return
          }
  ```

  (Only the new `rtmController?.abortRecording()` line is an addition, right before the existing
  `Toast.makeText` line.)

- [ ] **Step 6: Wire the non-discard path to finish + rename + enqueue**

  Add the import at the top of `TrainingActivity.kt` (after `import com.ttcoachai.repository.PersonalBaselineRepository` at line 18):

  ```kotlin
  import com.ttcoachai.work.PoseUploadQueue
  import java.io.File
  ```

  In `saveSessionToCloud()`'s `onSaved` lambda (lines 347-357), extend the body:

  ```kotlin
              onSaved = { sessionId ->
                  val settingsManager = SettingsManager(this@TrainingActivity)
                  app.sessionAnalyticsRecorder.record(
                      sessionId = sessionId,
                      results = stateManager.getAnalysisResults(),
                      feedbackCounts = stateManager.getFeedbackCounts().toMap(),
                      isTypeEnabled = { type -> settingsManager.isCorrectionTypeEnabled(type) },
                      repPoses = stateManager.getRepPoses()
                  )
                  app.pendingReviewSessionId.value = sessionId

                  val poseFile = rtmController?.finishRecording()
                  val userId = app.cloudSyncManager.currentUserId
                  if (poseFile != null && userId != null) {
                      val renamed = File(poseFile.parentFile, "$sessionId.json.gz")
                      if (poseFile.renameTo(renamed)) {
                          PoseUploadQueue.enqueue(this@TrainingActivity, userId, sessionId, renamed)
                      }
                  }
              }
  ```

- [ ] **Step 7: Compile check**

  ```
  ./gradlew :app:assembleDebug
  ```

  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 8: Commit**

  ```
  git add app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt \
          app/src/main/java/com/ttcoachai/TrainingActivity.kt
  git commit -m "feat(app): wire PoseSessionRecorder + PoseUploadQueue into the live RTM training path"
  ```

---

### Task 5: Consent UI — `AppSettingsActivity` toggle + strings

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/AppSettingsActivity.kt`
- Modify: `app/src/main/res/layout/activity_app_settings.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`

**Interfaces:**
- Consumes: `SettingsManager.{isPoseUploadEnabled, setPoseUploadEnabled}` (Task 2),
  `PoseUploadQueue.cancelAll(context)` (Task 3), `PoseSessionRecorder.cacheDir(context)` (Task 2),
  `LegalLinks.PRIVACY_URL`, `CloudSyncManager.uploadSettings()`

No unit test — this class has no existing test coverage precedent (`setupSubscription`/
`setupDebugMode` aren't tested either); verification is the compile check plus a manual toggle
check via `phone-screenshot`/`run-on-phone` skills, out of scope for an automated step here.

- [ ] **Step 1: Add EN strings**

  In `app/src/main/res/values/strings.xml`, add after `subscription_how_works_profile` (line 364,
  before the `<!-- Profile Updates -->` comment at line 366):

  ```xml
      <string name="data_privacy_section_title">Data &amp; Privacy</string>
      <string name="pose_upload_toggle_title">Upload training pose data</string>
      <string name="pose_upload_toggle_desc">Pose data (2D joint coordinates — no video or images) is uploaded so your sessions can be analysed and the coaching algorithm improved. You can turn this off at any time.</string>
      <string name="pose_upload_privacy_link">Privacy Policy</string>
  ```

- [ ] **Step 2: Add UA strings**

  In `app/src/main/res/values-uk/strings.xml`, add after `subscription_how_works_profile` (line
  484, before `activity_settings_title` at line 485):

  ```xml
      <string name="data_privacy_section_title">Дані та приватність</string>
      <string name="pose_upload_toggle_title">Завантажувати дані пози тренувань</string>
      <string name="pose_upload_toggle_desc">Дані пози (2D координати суглобів — без відео чи зображень) завантажуються, щоб аналізувати ваші тренування та вдосконалювати алгоритм коучингу. Ви можете вимкнути це в будь-який момент.</string>
      <string name="pose_upload_privacy_link">Політика конфіденційності</string>
  ```

- [ ] **Step 3: Add the "Data &amp; Privacy" section to `activity_app_settings.xml`**

  Insert after the "How it works Info Card" closes (currently `</com.google.android.material.card.MaterialCardView>`
  at line 451) and before the "Activity Settings Section" comment (line 453):

  ```xml
          <!-- Data & Privacy Section -->
          <LinearLayout
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:orientation="horizontal"
              android:gravity="center_vertical"
              android:layout_marginTop="24dp"
              android:layout_marginBottom="12dp">

              <ImageView
                  android:layout_width="20dp"
                  android:layout_height="20dp"
                  android:src="@drawable/ic_settings"
                  app:tint="#6366F1"
                  android:layout_marginEnd="8dp"/>

              <TextView
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:text="@string/data_privacy_section_title"
                  android:textStyle="bold"
                  android:textSize="16sp"/>
          </LinearLayout>

          <com.google.android.material.card.MaterialCardView
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              app:cardCornerRadius="16dp"
              app:cardElevation="0dp"
              app:strokeWidth="1dp"
              app:strokeColor="?attr/colorControlHighlight"
              android:layout_marginBottom="24dp">

              <LinearLayout
                  android:layout_width="match_parent"
                  android:layout_height="wrap_content"
                  android:orientation="vertical"
                  android:divider="?android:attr/listDivider"
                  android:showDividers="middle">

                  <!-- Pose Upload Toggle -->
                  <LinearLayout
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:padding="16dp"
                      android:gravity="center_vertical">

                      <LinearLayout
                          android:layout_width="0dp"
                          android:layout_height="wrap_content"
                          android:layout_weight="1"
                          android:orientation="vertical">

                          <TextView
                              android:layout_width="wrap_content"
                              android:layout_height="wrap_content"
                              android:text="@string/pose_upload_toggle_title"
                              android:textSize="16sp"/>

                          <TextView
                              android:layout_width="wrap_content"
                              android:layout_height="wrap_content"
                              android:text="@string/pose_upload_toggle_desc"
                              android:textColor="@color/text_muted"
                              android:textSize="12sp"
                              android:layout_marginTop="4dp"/>
                      </LinearLayout>

                      <com.google.android.material.switchmaterial.SwitchMaterial
                          android:id="@+id/switch_pose_upload"
                          style="@style/TTC.Toggle"
                          android:layout_width="wrap_content"
                          android:layout_height="wrap_content"/>
                  </LinearLayout>

                  <!-- Privacy Policy Link -->
                  <TextView
                      android:id="@+id/tv_pose_upload_privacy_link"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:background="?attr/selectableItemBackgroundBorderless"
                      android:clickable="true"
                      android:focusable="true"
                      android:padding="16dp"
                      android:text="@string/pose_upload_privacy_link"
                      android:textAppearance="@style/TextAppearance.TTC.Body.Secondary"/>
              </LinearLayout>
          </com.google.android.material.card.MaterialCardView>

  ```

- [ ] **Step 4: Wire it up in `AppSettingsActivity.kt`**

  Add a call to `setupPoseUploadConsent()` in `onCreate()`, after `setupSubscription()` (line 28):

  ```kotlin
          setupSubscription()
          setupPoseUploadConsent()
  ```

  Add the method (after `updateSubscriptionStatus`, before the closing `}` of the class — i.e.
  after line 130):

  ```kotlin
      private fun setupPoseUploadConsent() {
          binding.switchPoseUpload.isChecked = settingsManager.isPoseUploadEnabled()

          binding.tvPoseUploadPrivacyLink.setOnClickListener {
              try {
                  startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.ttcoachai.core.LegalLinks.PRIVACY_URL)))
              } catch (e: android.content.ActivityNotFoundException) {
                  android.widget.Toast.makeText(this, R.string.subscribe_no_browser_app, android.widget.Toast.LENGTH_SHORT).show()
              }
          }

          binding.switchPoseUpload.setOnCheckedChangeListener { _, isChecked ->
              settingsManager.setPoseUploadEnabled(isChecked)
              if (!isChecked) {
                  com.ttcoachai.work.PoseUploadQueue.cancelAll(this)
                  com.ttcoachai.pose.PoseSessionRecorder.cacheDir(this).listFiles()?.forEach { it.delete() }
              }
              cloudSyncManager.uploadSettings()
          }
      }
  ```

- [ ] **Step 5: Compile check**

  ```
  ./gradlew :app:assembleDebug
  ```

  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 6: Commit**

  ```
  git add app/src/main/java/com/ttcoachai/AppSettingsActivity.kt \
          app/src/main/res/layout/activity_app_settings.xml \
          app/src/main/res/values/strings.xml \
          app/src/main/res/values-uk/strings.xml
  git commit -m "feat(app): add pose-upload consent toggle to App Settings (default ON)"
  ```

---

### Task 6: `storage.rules` + `firebase.json`

**Files:**
- Create: `storage.rules` (repo root)
- Modify: `firebase.json`

**Interfaces:** none (declarative rules file, manually deployed — no code consumes it at build
time).

- [ ] **Step 1: Create `storage.rules`**

  ```
  /*
   * Cloud Storage Security Rules for TT_Coach pose-data upload
   *
   * MANUAL DEPLOYMENT REQUIRED
   * These rules are NOT auto-deployed by the build or CI/CD. To activate them:
   * 1. Via Firebase CLI: firebase deploy --only storage
   * 2. Via Firebase Console: go to Storage → Rules → paste this file
   *
   * Target bucket (confirmed from app/google-services.json, project_id "ttcoachai"):
   * ttcoachai.firebasestorage.app
   *
   * CAUTION: Deploying these rules switches Cloud Storage from whatever is currently live
   * (the Firebase default for new projects — any signed-in user may read/write ANY path) to
   * THIS file wholesale, scoping poses/{uid}/... to its owner only. Verify no other feature
   * relies on broader Storage access before deploying.
   */

  service firebase.storage {
    match /b/{bucket}/o {
      match /poses/{uid}/{file} {
        allow read, write: if request.auth != null && request.auth.uid == uid
                           && request.resource.size < 32 * 1024 * 1024
                           && request.resource.contentType == 'application/json';
      }
      match /{allPaths=**} { allow read, write: if false; }
    }
  }
  ```

- [ ] **Step 2: Add the storage rules reference to `firebase.json`**

  Current content:

  ```json
  {
    "firestore": {
      "rules": "firestore.rules"
    }
  }
  ```

  New content:

  ```json
  {
    "firestore": {
      "rules": "firestore.rules"
    },
    "storage": {
      "rules": "storage.rules"
    }
  }
  ```

- [ ] **Step 3: Verify (no automated test — declarative rules file)**

  If the Firebase CLI is installed and authenticated:

  ```
  firebase deploy --only storage --dry-run
  ```

  Expected: rules file parses with no syntax errors (dry-run reports what *would* deploy,
  without deploying). If the CLI isn't available in this environment, skip this step — deployment
  is a manual step for the user regardless (see Verification section at the end of this plan).

- [ ] **Step 4: Commit**

  ```
  git add storage.rules firebase.json
  git commit -m "feat(infra): add Cloud Storage rules scoping poses/{uid}/... to its owner"
  ```

---

### Task 7: Legal pages (site repo) + `LegalLinks` repoint (app repo)

Two repos, two commits.

#### Task 7a — site repo (`/Users/itsurkan/Dev/personal/TT_Coach_AA_site`)

**Files:**
- Create: `privacy.html`
- Create: `terms.html`
- Modify: `src/cta.jsx`

**Interfaces:** none (static HTML pages; no build step — this site is served as-is, per its
existing `index.html` which loads `src/*.jsx` directly via Babel-in-browser, no bundler).

- [ ] **Step 1: Create `privacy.html`**

  Reuses `index.html`'s color palette, fonts (Inter + JetBrains Mono, same Google Fonts URLs),
  and container/typography conventions (`Container` = max-width 960px narrow / 1240px wide,
  `--bg`/`--ink`/`--ink-2`/`--ink-3`/`--line`/`--amber` CSS variables) as a self-contained static
  page — no React/Babel runtime needed for a legal document.

  ```html
  <!DOCTYPE html>
  <html lang="en">
  <head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Privacy Policy — TT Coach AI</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #FAF7F2;
      --ink: #1A1A1F;
      --ink-2: #3A3A42;
      --ink-3: #6B6B75;
      --line: #E6E1D6;
      --amber: #F5B547;
    }
    @media (prefers-color-scheme: dark) {
      :root { --bg: #0E0E12; --ink: #F2EEE6; --ink-2: #C9C7BF; --ink-3: #8A8A92; --line: #2A2A32; }
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
      background: var(--bg);
      color: var(--ink);
      -webkit-font-smoothing: antialiased;
    }
    .container { max-width: 720px; margin: 0 auto; padding: 64px 32px 96px; }
    a { color: var(--ink); }
    .back { display: inline-flex; align-items: center; gap: 8px; font-size: 14px; color: var(--ink-3); text-decoration: none; margin-bottom: 40px; }
    h1 { font-size: clamp(32px, 5vw, 44px); letter-spacing: -0.02em; margin-bottom: 8px; }
    .updated { font-family: 'JetBrains Mono', monospace; font-size: 13px; color: var(--ink-3); margin-bottom: 48px; }
    h2 { font-size: 20px; margin-top: 40px; margin-bottom: 12px; letter-spacing: -0.01em; }
    p, li { font-size: 15px; line-height: 1.7; color: var(--ink-2); margin-bottom: 12px; }
    ul { padding-left: 20px; margin-bottom: 16px; }
    .highlight { background: color-mix(in oklab, var(--amber) 12%, transparent); border-left: 3px solid var(--amber); padding: 16px 20px; border-radius: 8px; margin: 20px 0; }
  </style>
  </head>
  <body>
  <div class="container">
    <a class="back" href="index.html">&larr; TT Coach AI</a>
    <h1>Privacy Policy</h1>
    <div class="updated">Last updated: 2026-07-23</div>

    <p>TT Coach AI ("we", "the app") helps you improve your table tennis technique using your
    phone's camera. This page explains what data we collect, why, and how you control it.</p>

    <h2>Account data</h2>
    <p>If you sign in, we store your name, email, and profile photo (as provided by your sign-in
    provider) to identify your account and sync your training history across devices.</p>

    <h2>Training session data</h2>
    <p>We store summary statistics for each training session — exercise type, duration, stroke
    count, and accuracy score — so you can track your progress over time.</p>

    <h2>Pose data</h2>
    <div class="highlight">
      <p><strong>What is collected:</strong> During a training session, the app tracks the 2D
      position of your joints (shoulders, elbows, wrists, hips, knees, ankles — 17 points per
      video frame) using on-device pose estimation. <strong>No video or camera images are ever
      stored or uploaded</strong> — only these numeric joint coordinates.</p>
      <p><strong>Why:</strong> this data lets us analyse your sessions in more depth than the
      on-device coaching can, and improve the coaching algorithm's accuracy over time.</p>
      <p><strong>Where it's stored:</strong> Firebase Cloud Storage (operated by Google Cloud),
      in a folder scoped to your account only — no other user can access your pose data.</p>
      <p><strong>Opt-out:</strong> pose data upload is <strong>on by default</strong> and can be
      switched off at any time from Settings → Data &amp; Privacy in the app. Turning it off
      stops all future uploads immediately; pose data is never written to disk at all while the
      setting is off.</p>
      <p><strong>Deletion:</strong> to request deletion of previously uploaded pose data, contact
      us using the details in "Contact" below with your account email — we will delete it within
      30 days.</p>
    </div>

    <h2>How we use your data</h2>
    <ul>
      <li>To provide and improve the coaching feedback shown during and after training</li>
      <li>To sync your history and progress across your devices</li>
      <li>To analyse aggregated, de-identified usage patterns to improve the app</li>
    </ul>
    <p>We do not sell your data to third parties.</p>

    <h2>Data retention</h2>
    <p>Account and session-summary data is kept until you delete your account. Pose data is kept
    for as long as needed for analysis; a bucket-level retention policy that automatically ages
    out old pose files is planned but not yet in place.</p>

    <h2>Your rights</h2>
    <p>You can request access to, export of, or deletion of your data at any time by contacting
    us. You can delete your account from Settings, which removes your account and session-summary
    data.</p>

    <h2>Contact</h2>
    <p>Questions about this policy or data requests: <a href="mailto:itsurkan.1@gmail.com">itsurkan.1@gmail.com</a></p>
  </div>
  </body>
  </html>
  ```

- [ ] **Step 2: Create `terms.html`**

  ```html
  <!DOCTYPE html>
  <html lang="en">
  <head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Terms of Service — TT Coach AI</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #FAF7F2;
      --ink: #1A1A1F;
      --ink-2: #3A3A42;
      --ink-3: #6B6B75;
      --line: #E6E1D6;
    }
    @media (prefers-color-scheme: dark) {
      :root { --bg: #0E0E12; --ink: #F2EEE6; --ink-2: #C9C7BF; --ink-3: #8A8A92; --line: #2A2A32; }
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
      background: var(--bg);
      color: var(--ink);
      -webkit-font-smoothing: antialiased;
    }
    .container { max-width: 720px; margin: 0 auto; padding: 64px 32px 96px; }
    a { color: var(--ink); }
    .back { display: inline-flex; align-items: center; gap: 8px; font-size: 14px; color: var(--ink-3); text-decoration: none; margin-bottom: 40px; }
    h1 { font-size: clamp(32px, 5vw, 44px); letter-spacing: -0.02em; margin-bottom: 8px; }
    .updated { font-family: 'JetBrains Mono', monospace; font-size: 13px; color: var(--ink-3); margin-bottom: 48px; }
    h2 { font-size: 20px; margin-top: 40px; margin-bottom: 12px; letter-spacing: -0.01em; }
    p, li { font-size: 15px; line-height: 1.7; color: var(--ink-2); margin-bottom: 12px; }
    ul { padding-left: 20px; margin-bottom: 16px; }
  </style>
  </head>
  <body>
  <div class="container">
    <a class="back" href="index.html">&larr; TT Coach AI</a>
    <h1>Terms of Service</h1>
    <div class="updated">Last updated: 2026-07-23</div>

    <p>By using TT Coach AI ("the app"), you agree to these terms.</p>

    <h2>The service</h2>
    <p>TT Coach AI analyses your table tennis technique using your phone's camera and gives you
    real-time and post-session coaching feedback. It is a training aid, not a substitute for
    professional coaching.</p>

    <h2>Your account</h2>
    <p>You're responsible for keeping your account credentials secure and for all activity under
    your account.</p>

    <h2>Acceptable use</h2>
    <ul>
      <li>Use the app for personal, non-commercial training purposes</li>
      <li>Don't attempt to reverse-engineer, decompile, or extract the app's models or code</li>
      <li>Don't use the app in a way that could harm, disable, or overburden our services</li>
    </ul>

    <h2>Your content and data</h2>
    <p>You retain ownership of any data you provide. See our
    <a href="privacy.html">Privacy Policy</a> for what we collect (including pose data during
    training) and how it's used.</p>

    <h2>Subscriptions</h2>
    <p>Some features require a paid subscription. Subscription pricing, billing, and cancellation
    terms are shown in-app at the point of purchase, via the app store you subscribed through.</p>

    <h2>Disclaimer</h2>
    <p>The app is provided "as is". Coaching feedback is generated by automated analysis and may
    not always be accurate — use your own judgment and consult a qualified coach for serious
    technique or injury concerns.</p>

    <h2>Changes</h2>
    <p>We may update these terms from time to time. Continued use of the app after a change means
    you accept the updated terms.</p>

    <h2>Contact</h2>
    <p>Questions about these terms: <a href="mailto:itsurkan.1@gmail.com">itsurkan.1@gmail.com</a></p>
  </div>
  </body>
  </html>
  ```

- [ ] **Step 3: Point the site footer at the new pages**

  In `src/cta.jsx`, in the `Footer` component (lines 74-79), change the placeholder `href="#"`
  links:

  ```jsx
      <div style={{ display: 'flex', gap: 24 }}>
        <a href="privacy.html" style={{ color: 'inherit', textDecoration: 'none' }}>Privacy</a>
        <a href="terms.html" style={{ color: 'inherit', textDecoration: 'none' }}>Terms</a>
        <a href="#" style={{ color: 'inherit', textDecoration: 'none' }}>Support</a>
        <a href="#" style={{ color: 'inherit', textDecoration: 'none' }}>For coaches</a>
      </div>
  ```

  (Only the `Privacy`/`Terms` `href` values change, from `"#"` to `"privacy.html"`/`"terms.html"`
  — `Support`/`For coaches` stay as-is, out of scope for this plan.)

- [ ] **Step 4: Verify (visual check, no build step)**

  ```
  cd /Users/itsurkan/Dev/personal/TT_Coach_AA_site && python3 -m http.server 8743
  ```

  Open `http://localhost:8743/privacy.html` and `http://localhost:8743/terms.html` in a browser;
  confirm both render, and `http://localhost:8743/index.html`'s footer Privacy/Terms links
  navigate to them.

- [ ] **Step 5: Commit (in the site repo)**

  ```
  cd /Users/itsurkan/Dev/personal/TT_Coach_AA_site
  git add privacy.html terms.html src/cta.jsx
  git commit -m "feat: add published Privacy Policy and Terms of Service pages"
  ```

#### Task 7b — TT_Coach repo: repoint `LegalLinks`

**Files:**
- Modify: `app/src/main/java/com/ttcoachai/core/LegalLinks.kt`

**Interfaces:** none (constants only; already consumed by `SubscribeActivity` and, as of Task 5,
`AppSettingsActivity`).

- [ ] **Step 1: Repoint the URLs and drop the TODO**

  Current content:

  ```kotlin
  package com.ttcoachai.core

  /**
   * Legal document URLs used by SubscribeActivity's Terms/Privacy links.
   *
   * TODO(product): replace with final URLs before release.
   */
  object LegalLinks {
      const val TERMS_URL = "https://ttcoach.ai/terms"
      const val PRIVACY_URL = "https://ttcoach.ai/privacy"
  }
  ```

  New content:

  ```kotlin
  package com.ttcoachai.core

  /**
   * Legal document URLs used by SubscribeActivity's and AppSettingsActivity's Terms/Privacy
   * links. Published from the marketing site repo
   * (/Users/itsurkan/Dev/personal/TT_Coach_AA_site, privacy.html / terms.html) via GitHub Pages.
   */
  object LegalLinks {
      const val TERMS_URL = "https://itsurkan.github.io/TT_Coach_AA_site/terms.html"
      const val PRIVACY_URL = "https://itsurkan.github.io/TT_Coach_AA_site/privacy.html"
  }
  ```

- [ ] **Step 2: Compile check**

  ```
  ./gradlew :app:assembleDebug
  ```

  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 3: Commit (in the TT_Coach repo)**

  ```
  git add app/src/main/java/com/ttcoachai/core/LegalLinks.kt
  git commit -m "fix(app): repoint LegalLinks to the published GitHub Pages privacy/terms pages"
  ```

---

### Task 8: Orphan sweep + local cache eviction

**Files:**
- Create: `app/src/main/java/com/ttcoachai/work/PoseCacheEviction.kt`
- Test: `app/src/test/java/com/ttcoachai/work/PoseCacheEvictionTest.kt`
- Modify: `app/src/main/java/com/ttcoachai/work/PoseUploadQueue.kt`
- Modify: `app/src/main/java/com/ttcoachai/TTCoachApplication.kt`

**Interfaces:**
- Produces: `object PoseCacheEviction { data class Entry(path: String, lastModifiedMs: Long, sizeBytes: Long); fun entriesToEvict(entries: List<Entry>, nowMs: Long, maxAgeDays: Int, maxBytes: Long): List<String> }`
  `PoseUploadQueue.sweepOrphans(context: Context)`, `PoseUploadQueue.evictOldCache(context: Context, maxAgeDays: Int = 7, maxBytes: Long = 200L * 1024 * 1024)`
- Consumes: `PoseSessionRecorder.cacheDir(context)` (Task 2), `PoseUploadQueue.enqueue` (Task 3),
  `com.google.firebase.auth.FirebaseAuth`

#### Step 1-4: `PoseCacheEviction` (pure logic, TDD)

- [ ] **Step 1: Create a compiling skeleton**

  ```kotlin
  package com.ttcoachai.work

  /**
   * Pure local-disk cache eviction logic for the pose-upload cache directory
   * (PoseSessionRecorder.cacheDir), extracted from [PoseUploadQueue.evictOldCache] so it's
   * testable without touching the filesystem. Two policies, applied in order: age (anything
   * older than [maxAgeDays] goes regardless of total size), then size (if what's left still
   * exceeds [maxBytes], delete the oldest remaining entries first until under budget).
   */
  object PoseCacheEviction {

      data class Entry(val path: String, val lastModifiedMs: Long, val sizeBytes: Long)

      fun entriesToEvict(
          entries: List<Entry>,
          nowMs: Long,
          maxAgeDays: Int = 7,
          maxBytes: Long = 200L * 1024 * 1024,
      ): List<String> {
          TODO("Task 8 Step 3")
      }
  }
  ```

- [ ] **Step 2: Write the failing tests**

  Create `app/src/test/java/com/ttcoachai/work/PoseCacheEvictionTest.kt`:

  ```kotlin
  package com.ttcoachai.work

  import org.junit.Assert.assertEquals
  import org.junit.Test

  class PoseCacheEvictionTest {

      private val oneDayMs = 24L * 60 * 60 * 1000
      private val now = 1_000_000_000_000L

      @Test
      fun staleFileIsEvictedEvenIfSmall() {
          val entries = listOf(
              PoseCacheEviction.Entry(path = "old.json.gz", lastModifiedMs = now - 8 * oneDayMs, sizeBytes = 1_000),
              PoseCacheEviction.Entry(path = "fresh.json.gz", lastModifiedMs = now - 1 * oneDayMs, sizeBytes = 1_000),
          )
          val toDelete = PoseCacheEviction.entriesToEvict(entries, now, maxAgeDays = 7, maxBytes = Long.MAX_VALUE)
          assertEquals(listOf("old.json.gz"), toDelete)
      }

      @Test
      fun freshFilesUnderCapAreKept() {
          val entries = listOf(
              PoseCacheEviction.Entry(path = "a.json.gz", lastModifiedMs = now, sizeBytes = 50L * 1024 * 1024),
              PoseCacheEviction.Entry(path = "b.json.gz", lastModifiedMs = now, sizeBytes = 50L * 1024 * 1024),
          )
          val toDelete = PoseCacheEviction.entriesToEvict(entries, now, maxAgeDays = 7, maxBytes = 200L * 1024 * 1024)
          assertEquals(emptyList<String>(), toDelete)
      }

      @Test
      fun overCapEvictsOldestFirstUntilUnderBudget() {
          val entries = listOf(
              PoseCacheEviction.Entry(path = "oldest.json.gz", lastModifiedMs = now - 3 * oneDayMs, sizeBytes = 80L * 1024 * 1024),
              PoseCacheEviction.Entry(path = "middle.json.gz", lastModifiedMs = now - 2 * oneDayMs, sizeBytes = 80L * 1024 * 1024),
              PoseCacheEviction.Entry(path = "newest.json.gz", lastModifiedMs = now - 1 * oneDayMs, sizeBytes = 80L * 1024 * 1024),
          )
          // total 240MB > 200MB cap; deleting "oldest" alone brings it to 160MB, under budget.
          val toDelete = PoseCacheEviction.entriesToEvict(entries, now, maxAgeDays = 7, maxBytes = 200L * 1024 * 1024)
          assertEquals(listOf("oldest.json.gz"), toDelete)
      }
  }
  ```

  Run it:

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.work.PoseCacheEvictionTest"
  ```

  Expected: **FAILS** — all three throw `kotlin.NotImplementedError: An operation is not
  implemented: Task 8 Step 3`.

- [ ] **Step 3: Implement `entriesToEvict`**

  ```kotlin
      fun entriesToEvict(
          entries: List<Entry>,
          nowMs: Long,
          maxAgeDays: Int = 7,
          maxBytes: Long = 200L * 1024 * 1024,
      ): List<String> {
          val maxAgeMs = maxAgeDays * 24L * 60 * 60 * 1000
          val (stale, fresh) = entries.partition { nowMs - it.lastModifiedMs > maxAgeMs }
          val toDelete = stale.map { it.path }.toMutableList()

          var remainingBytes = fresh.sumOf { it.sizeBytes }
          if (remainingBytes > maxBytes) {
              val oldestFirst = fresh.sortedBy { it.lastModifiedMs }
              for (entry in oldestFirst) {
                  if (remainingBytes <= maxBytes) break
                  toDelete.add(entry.path)
                  remainingBytes -= entry.sizeBytes
              }
          }
          return toDelete
      }
  ```

  Run it:

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.work.PoseCacheEvictionTest"
  ```

  Expected: **PASSES**

  ```
  BUILD SUCCESSFUL in 13s
  4 actionable tasks: 4 executed
  ```

- [ ] **Step 4: Compile check**

  ```
  ./gradlew :app:assembleDebug
  ```

  Expected: **BUILD SUCCESSFUL**

#### Step 5-8: wire sweep + eviction into `PoseUploadQueue` and `TTCoachApplication`

- [ ] **Step 5: Add `sweepOrphans`/`evictOldCache` to `PoseUploadQueue.kt`**

  Add imports at the top of `PoseUploadQueue.kt`:

  ```kotlin
  import com.google.firebase.auth.FirebaseAuth
  import com.ttcoachai.pose.PoseSessionRecorder
  ```

  Add these two methods to the `object PoseUploadQueue` block, after `cancelAll`:

  ```kotlin
      /** Called on app start: re-enqueues any `<sessionId>.json.gz` left behind in the cache dir
       *  by a process death after the Task 4 rename but before the worker finished (component
       *  C's "App-start sweep"). Files still in provisional `.tmp` form (never reached the
       *  rename) are pre-session-id and are not recoverable — they're covered by
       *  [evictOldCache]'s age cap instead. */
      fun sweepOrphans(context: Context) {
          val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
          val dir = PoseSessionRecorder.cacheDir(context)
          dir.listFiles { f -> f.name.endsWith(".json.gz") }?.forEach { file ->
              val sessionId = file.name.removeSuffix(".json.gz")
              enqueue(context, userId, sessionId, file)
          }
      }

      /** Called on app start: deletes cached pose files older than [maxAgeDays] or, if the
       *  directory still exceeds [maxBytes] after that, the oldest remaining files until under
       *  budget. A local-disk cap independent of the Storage-side retention gap (see spec
       *  Risks). */
      fun evictOldCache(context: Context, maxAgeDays: Int = 7, maxBytes: Long = 200L * 1024 * 1024) {
          val dir = PoseSessionRecorder.cacheDir(context)
          val files = dir.listFiles()?.toList() ?: return
          val entries = files.map { PoseCacheEviction.Entry(it.absolutePath, it.lastModified(), it.length()) }
          val toDelete = PoseCacheEviction.entriesToEvict(entries, System.currentTimeMillis(), maxAgeDays, maxBytes)
          toDelete.forEach { java.io.File(it).delete() }
      }
  ```

- [ ] **Step 6: Call the sweep from `TTCoachApplication.onCreate`**

  In `app/src/main/java/com/ttcoachai/TTCoachApplication.kt`, add the import:

  ```kotlin
  import com.ttcoachai.work.PoseUploadQueue
  ```

  In `onCreate()`, after `cloudSyncManager.initialize()` (line 65), before the `Log.i(TAG, ...)`
  line (line 67):

  ```kotlin
          // Pose-upload cache maintenance (component C's app-start sweep, see
          // docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md)
          PoseUploadQueue.sweepOrphans(this)
          PoseUploadQueue.evictOldCache(this)

  ```

- [ ] **Step 7: Compile check**

  ```
  ./gradlew :app:assembleDebug
  ```

  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 8: Commit**

  ```
  git add app/src/main/java/com/ttcoachai/work/PoseCacheEviction.kt \
          app/src/test/java/com/ttcoachai/work/PoseCacheEvictionTest.kt \
          app/src/main/java/com/ttcoachai/work/PoseUploadQueue.kt \
          app/src/main/java/com/ttcoachai/TTCoachApplication.kt
  git commit -m "feat(app): sweep orphaned pose uploads and evict stale local cache on app start"
  ```

---

## Verification

Run the full set at the end, in this order:

```
./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.io.PoseJsonV2WriterTest"
./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.settings.SettingsManagerTuningTest"
./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.pose.PoseSessionRecorderTest"
./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.work.PoseUploadTaskTest"
./gradlew :app:testDebugUnitTest --tests "com.ttcoachai.work.PoseCacheEvictionTest"
./gradlew :app:assembleDebug
```

All six expected **BUILD SUCCESSFUL** / all tests green. Do **not** run the bare `./gradlew test`
or `:app:testDebugUnitTest` (unscoped) as a final check — `MotionAnalyzerJsonTest` fails there
for unrelated, pre-existing reasons (frozen legacy code) and will read as a false regression.

**Manual steps the user performs (not automatable from this plan):**
- `firebase deploy --only storage` (or paste `storage.rules` into the Firebase Console) — Task 6.
- Publish the site repo (`git push` in `TT_Coach_AA_site`, or whatever its GitHub Pages deploy
  trigger is) so `privacy.html`/`terms.html` actually go live at the URLs `LegalLinks` now points
  to — Task 7a.
- Update the Play Store data-safety declaration to disclose pose/skeleton-coordinate collection
  before shipping a release with this feature (spec Risks — out of scope for this plan, flagged
  here as a release blocker).

## Spec → task mapping

| Spec section | Task(s) |
|---|---|
| Decision 1 (full session, no cropping) | Task 2 (`PoseSessionRecorder` records every `onFrame`, no filtering) |
| Decision 2 (consent toggle, default ON, no first-run screen) | Task 2 (flag default `true`), Task 5 (UI) |
| Decision 3 (WorkManager, ANY network, exponential backoff) | Task 3 |
| Decision 4 (compact + gzip `.json.gz`) | Task 1 (compact writer), Task 2 (gzip in `finish()`) |
| Decision 5 (real published legal pages) | Task 7 |
| Architecture A — `PoseJsonV2Writer` | Task 1 |
| Architecture B — `PoseSessionRecorder` | Task 2, wired in Task 4 |
| Architecture C — `PoseUploadWorker`/`PoseUploadQueue`/app-start sweep | Task 3 (worker/queue/enqueue), Task 8 (sweep/eviction) |
| Architecture D — Consent | Task 2 (flag), Task 5 (UI + cancel-on-off) |
| Architecture E — Security rules | Task 6 |
| Architecture F — Legal pages | Task 7 |
| Testing section (writer round-trip/round4/tripwire; recorder consent/cap/finalization; worker success/retry) | Task 1, Task 2, Task 3 (consent-gating itself is a 1-line `if` at the call site in Task 4, not separately unit-testable without Robolectric — covered by the compile check per the prompt's own Task 3 verification step) |
| Non-goals (legacy MediaPipe path, video/image upload, `LiveDrillSession`/`TrainingStateManager` changes, first-run screen, legal review, server-side processing) | Not implemented — confirmed out of scope throughout |
| Risks (storage cost growth, unmeasured fps, Play Store data-safety declaration) | Not implemented — flagged as follow-ups in the Verification section above |

## Self-review

1. **Every spec section maps to a task** — see the table above; no spec requirement was dropped.
2. **No placeholders** — every step above contains complete, real Kotlin/XML/JSON/HTML, not
   "TBD" or "add error handling" stubs (TDD `TODO("Task N Step M")` markers are intentional
   red-test scaffolding, replaced within the same task, not left in the final state).
3. **Type/name consistency across tasks**, verified:
   - `isPoseUploadEnabled()` — declared Task 2 Step 1, consumed Task 4 Step 2, Task 5 Step 4.
   - `PoseSessionRecorder.finish(): File?` (suspend) — declared Task 2 Step 7, consumed as
     `RtmposeTrainingController.finishRecording()` Task 4 Step 4, consumed Task 4 Step 6.
   - `PoseUploadQueue.enqueue(context, userId, sessionId, file)` — declared Task 3 Step 9,
     consumed Task 4 Step 6.
   - `uploadPoseFile(userId: String, sessionId: String, file: File): Result<String>` — declared
     Task 3 Step 2, consumed identically (lambda-wrapped) in Task 3 Step 8's `PoseUploadWorker`.
   - `PoseSessionRecorder.cacheDir(context: Context): File` — declared Task 2 Step 5, consumed
     Task 4 Step 2, Task 5 Step 4, Task 8 Step 5.
4. **Task ordering compiles** — verified by construction; see "Deviations from the prompt's task
   numbering" above for the two reorderings made specifically to satisfy this (SettingsManager
   flag pulled into Task 2; WorkManager/Queue task moved before the wiring task). No task
   references a symbol first created in a later task.
