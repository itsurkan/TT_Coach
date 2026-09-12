# Plan: "Annoying" feedback cadence — a voice cue after every stroke

> For agentic workers: execute tasks top to bottom via `superpowers:subagent-driven-development`
> (fresh subagent per task, review between tasks — never inline in the main session). Each task
> is TDD: write a failing test, run it (confirm the failure), implement, run again (confirm
> green), then commit with explicit `git add <paths>` (never `git add -A`). Model hints are
> given per task. Do not skip a step, do not batch tasks into one commit, do not leave a
> placeholder — every code block below is real, compiling code to type as-is (adjust only for
> what you observe differs in the working tree at execution time).

## Spec (inline)

"Annoying" is a new feedback cadence mode: a voice cue after **every** stroke, no 3–5 s
throttling. If the voice channel is still busy speaking the previous cue when a stroke
completes, that stroke's cue is **not spoken** — no queue, no barge-in — but its text still
goes on screen and into the session feedback list (identical to how every other cue's text
does today). Praise ("good") fires on every clean stroke too, when the voice is free; there is
no text-only praise. The existing 3–5 s ("standard") mode's behavior — every code path, every
existing test — stays byte-for-byte unchanged; the new mode is additive and opt-in via a
Settings segment control, defaulting to the current standard behavior for all existing users.

Decisions locked by the owner (do not re-litigate):
- No queueing of skipped cues; a skipped cue is gone from voice, not delayed.
- Text/on-screen feedback is unconditional — cadence/busy state only gates the voice channel.
- Praise obeys the same busy gate as corrections; no separate rule for it.
- Standard mode must not change in any observable way.

## Goal

Ship an opt-in "Annoying" cadence mode end-to-end: shared cadence logic → live Android wiring →
Settings UI (two screens) → docs of the known limitation this surfaces (unwired legacy cadence
sliders).

## Architecture

`FeedbackCadencePolicy` (shared/commonMain) gains a `CadenceMode` and two new methods used only
in `EVERY_STROKE` mode; `DrillRepProcessor.emitRepFeedback` branches on `cadence.mode`;
`MovementAnalyzer`/`LiveDrillSession` need no structural change beyond `SpokenFeedback` carrying
a new `voice: Boolean` flag. On the Android side, `PresetVoiceController` and `DrillTtsController`
each expose `isBusy(): Boolean` backed by MediaPlayer/TTS utterance state; `RtmposeTrainingController`
(the live production path, formerly named `RtmposeTrainingController` — file renamed since the
CLAUDE.md snapshot this plan cites was written) and `RtmposeDrillActivity` (debug screen, formerly
`RtmposeDrillActivity`) read `settingsManager.isEveryStrokeCadence()` to pick the cadence mode and
gate `voiceController.speak()`/`tts.speak()` on `item.voice`, while always showing the on-screen
text. `SettingsManager.feedback_frequency` gains a fourth value (`1`) meaning "every stroke".

## Tech Stack

Kotlin Multiplatform (`shared/commonMain`, zero external deps, hand-rolled everything, tested via
`kotlin.test` on JVM through `:shared:jvmTest`) + Android (`app/`, View Binding, Material3,
`android.speech.tts.TextToSpeech`, `android.media.MediaPlayer`).

## Global Constraints

- `shared/` has **zero external dependencies** — no kotlinx-serialization, no org.json, no
  Android APIs. Everything here is plain Kotlin + `kotlin.test`.
- Tests: `shared/src/commonTest/kotlin/...` (pure logic, `kotlin.test`). Run with
  `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.<ClassName>Test"` per task;
  full sweep only in the final task.
- `git add` **explicit paths only** — never `git add -A` (the tree carries unrelated artifacts:
  `node_modules/.vite/`, `tsconfig.tsbuildinfo`, uncommitted files from other work in progress on
  this branch/session). Commit after each task (logical unit), never batch.
- Do **not** modify frozen legacy code (`MotionAnalyzer`, `MediaPipeMapper`,
  `StrokePhaseDetector` under `app/src/main/java/com/ttcoachai/{mappers,processors,services}/`) —
  irrelevant to this feature, not touched by any task below.
- Naming must be **byte-identical** everywhere it recurs: `CadenceMode`, `EVERY_STROKE`,
  `INTERVAL`, `offerEveryStroke`, `offerPositiveEveryStroke`, `SpokenFeedback.voice`, `isBusy()`,
  `isEveryStrokeCadence()`, `FEEDBACK_FREQUENCY_EVERY_STROKE`.
- Existing constructor defaults (`FeedbackCadencePolicy()`, `SpokenFeedback(...)`) must keep
  every current call site compiling with zero changes — new params/fields are additive with
  defaults.
- No worktrees. Branch directly off the current branch tip; commit there.
- Backend renames since the file map in the project CLAUDE.md was last refreshed:
  `RtmposeTrainingController` → **`RtmposeTrainingController`**
  (`app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt`),
  `RtmposeDrillActivity` → **`RtmposeDrillActivity`**
  (`app/src/main/java/com/ttcoachai/pose/RtmposeDrillActivity.kt`),
  `RtmposeCalibrationActivity` → **`RtmposeCalibrationActivity`**
  (`app/src/main/java/com/ttcoachai/pose/RtmposeCalibrationActivity.kt`, all three construct their
  backend via `PoseBackendFactory.create(...)`, confirmed by grep). Use the real names throughout
  this plan and in the code you write.

## Task 1 — Branch setup + `CadenceMode` in `FeedbackCadencePolicy`

Model: sonnet.

### Files
- `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/FeedbackCadencePolicy.kt` (edit)
- `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/FeedbackCadencePolicyTest.kt` (edit)

### Interfaces

```kotlin
enum class CadenceMode { INTERVAL, EVERY_STROKE }

class FeedbackCadencePolicy(
    private val minIntervalMs: Long = 3000,
    private val maxIntervalMs: Long = 5000,
    val mode: CadenceMode = CadenceMode.INTERVAL,
    private val isVoiceBusy: () -> Boolean = { false }
) {
    // existing offer()/offerPositive()/reset() UNCHANGED — INTERVAL-mode callers keep working
    // exactly as today; do NOT call offer()/offerPositive() from EVERY_STROKE mode callers.

    /** EVERY_STROKE only: highest-severity cue, paired with whether the voice channel is
     *  currently free. Never touches [minIntervalMs]/[maxIntervalMs] or any clock state —
     *  there is no window to consume in this mode. Returns null iff [cues] is empty. */
    fun offerEveryStroke(cues: List<FeedbackCue>): Pair<FeedbackCue, Boolean>? {
        val top = cues.maxByOrNull { it.severity } ?: return null
        return top to !isVoiceBusy()
    }

    /** EVERY_STROKE only: true iff the voice channel is free right now. No text-only praise —
     *  callers must skip praise entirely when this returns false (per spec: no "silent praise"). */
    fun offerPositiveEveryStroke(): Boolean = !isVoiceBusy()
}
```

### Steps

- [ ] 1. Create branch `feat/annoying-cadence` from the current branch tip (`main`):
      `git checkout -b feat/annoying-cadence`.
- [ ] 2. Write failing tests in `FeedbackCadencePolicyTest.kt` — append to the existing class
      (leave every existing `@Test` untouched, they must still pass):

  ```kotlin
  // ---- EVERY_STROKE mode: offerEveryStroke ----

  @Test
  fun everyStrokeCueReturnedWithVoiceFalseWhenBusy() {
      val policy = FeedbackCadencePolicy(mode = CadenceMode.EVERY_STROKE, isVoiceBusy = { true })
      val result = policy.offerEveryStroke(listOf(kneeLow, elbowHigh))
      assertEquals(elbowHigh to false, result)
  }

  @Test
  fun everyStrokeCueReturnedWithVoiceTrueWhenFree() {
      val policy = FeedbackCadencePolicy(mode = CadenceMode.EVERY_STROKE, isVoiceBusy = { false })
      val result = policy.offerEveryStroke(listOf(kneeLow, elbowHigh))
      assertEquals(elbowHigh to true, result)
  }

  @Test
  fun everyStrokeTwoConsecutiveFreeCallsBothSpeakNoIntervalSuppression() {
      val policy = FeedbackCadencePolicy(mode = CadenceMode.EVERY_STROKE, isVoiceBusy = { false })
      val first = policy.offerEveryStroke(listOf(elbowHigh))
      val second = policy.offerEveryStroke(listOf(kneeLow))
      assertEquals(elbowHigh to true, first)
      assertEquals(kneeLow to true, second, "EVERY_STROKE must never throttle back-to-back calls")
  }

  @Test
  fun everyStrokeEmptyCuesReturnsNull() {
      val policy = FeedbackCadencePolicy(mode = CadenceMode.EVERY_STROKE, isVoiceBusy = { false })
      assertNull(policy.offerEveryStroke(emptyList()))
  }

  // ---- EVERY_STROKE mode: offerPositiveEveryStroke ----

  @Test
  fun everyStrokePositiveFalseWhenBusy() {
      val policy = FeedbackCadencePolicy(mode = CadenceMode.EVERY_STROKE, isVoiceBusy = { true })
      assertFalse(policy.offerPositiveEveryStroke())
  }

  @Test
  fun everyStrokePositiveTrueWhenFree() {
      val policy = FeedbackCadencePolicy(mode = CadenceMode.EVERY_STROKE, isVoiceBusy = { false })
      assertTrue(policy.offerPositiveEveryStroke())
  }

  // ---- INTERVAL mode is unaffected by the new constructor param ----

  @Test
  fun intervalModeIsTheDefaultAndOfferBehaviorIsUnchanged() {
      val policy = FeedbackCadencePolicy()
      assertEquals(CadenceMode.INTERVAL, policy.mode)
      assertEquals(elbowHigh, policy.offer(nowMs = 0L, cues = listOf(kneeLow, elbowHigh)))
  }
  ```

- [ ] 3. Run and confirm failure (methods don't exist yet):
      `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.FeedbackCadencePolicyTest"`
      → expect a compile error (`offerEveryStroke`/`offerPositiveEveryStroke`/`mode`/`CadenceMode`
      unresolved).
- [ ] 4. Implement `CadenceMode` and the two new methods/param in `FeedbackCadencePolicy.kt`
      exactly as in Interfaces above (keep `offer`/`offerPositive`/`reset` untouched; add the
      KDoc note "EVERY_STROKE mode callers must not call `offer`/`offerPositive` — those consume
      the INTERVAL clock" above the class).
- [ ] 5. Run again, confirm all tests (old + new) pass:
      `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.FeedbackCadencePolicyTest"`.
- [ ] 6. Commit:
      `git add shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/FeedbackCadencePolicy.kt shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/FeedbackCadencePolicyTest.kt`
      then commit with message `feat(cadence): add CadenceMode.EVERY_STROKE to FeedbackCadencePolicy`.

## Task 2 — `SpokenFeedback.voice` flag

Model: sonnet.

### Files
- `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/MovementAnalyzer.kt` (edit, line 30-35)
- `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/DrillRepProcessorTest.kt` (edit — add
  one assertion to an existing test to lock the default)

### Interfaces

```kotlin
data class SpokenFeedback(
    val timestampMs: Long,
    val message: String,
    /** null = positive reinforcement, not a correction. */
    val cue: FeedbackCue?,
    /** Whether this feedback was (or should be) spoken aloud. Defaults to true so every
     *  existing constructor call and every INTERVAL-mode caller sees zero behavior change —
     *  only EVERY_STROKE-mode callers (Task 3) ever construct one with voice=false. */
    val voice: Boolean = true
)
```

### Steps

- [ ] 1. Add a failing assertion to `DrillRepProcessorTest.defaultCuesForCadenceFallsBackToRepCuesAndPicksHighestSeverity`
      (append a line after the existing `assertEquals(topCue, spoken.cue, ...)`):

  ```kotlin
      assertTrue(spoken.voice, "INTERVAL-mode feedback must default to spoken=true")
  ```

  (add `import kotlin.test.assertTrue` if not already present in the file — check first, the
  file currently imports `assertEquals`/`assertNotNull`/`assertNull` only).
- [ ] 2. Run and confirm failure (compile error: `voice` unresolved on `SpokenFeedback`):
      `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.DrillRepProcessorTest"`.
- [ ] 3. Add the `voice: Boolean = true` field to `SpokenFeedback` in `MovementAnalyzer.kt` exactly
      as shown in Interfaces above (a trailing-comma-free data class edit — every existing
      `SpokenFeedback(atMs, message, cue)` positional-arg call site keeps compiling since `voice`
      is the fourth param with a default).
- [ ] 4. Run again, confirm green:
      `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.DrillRepProcessorTest"` and
      `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.MovementAnalyzerTest"` (or
      whatever the batch-analyzer test class is actually named — check
      `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/` / `shared/src/jvmTest/...` for
      the real name before running; it must stay green untouched).
- [ ] 5. Commit:
      `git add shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/MovementAnalyzer.kt shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/DrillRepProcessorTest.kt`
      then commit with message `feat(cadence): add SpokenFeedback.voice flag (default true)`.

## Task 3 — `DrillRepProcessor.emitRepFeedback` branches on `cadence.mode`

Model: sonnet.

### Files
- `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillRepProcessor.kt` (edit)
- `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/DrillRepProcessorTest.kt` (edit)

### Interfaces

```kotlin
internal fun emitRepFeedback(
    rep: RepAnalysis,
    atMs: Long,
    cadence: FeedbackCadencePolicy,
    lang: FeedbackLang,
    cuesForCadence: List<FeedbackCue> = rep.cues
): SpokenFeedback? {
    if (!rep.placementOk) return null
    return when (cadence.mode) {
        CadenceMode.EVERY_STROKE -> {
            val offered = cadence.offerEveryStroke(cuesForCadence)
            when {
                offered != null -> {
                    val (cue, voice) = offered
                    SpokenFeedback(atMs, FeedbackMessageCatalog.format(cue, lang), cue, voice = voice)
                }
                rep.cues.isEmpty() && rep.metrics.isNotEmpty() && cadence.offerPositiveEveryStroke() ->
                    SpokenFeedback(atMs, FeedbackMessageCatalog.positive(lang), null, voice = true)
                else -> null
            }
        }
        CadenceMode.INTERVAL -> {
            val cue = cadence.offer(atMs, cuesForCadence)
            when {
                cue != null ->
                    SpokenFeedback(atMs, FeedbackMessageCatalog.format(cue, lang), cue)
                rep.cues.isEmpty() && rep.metrics.isNotEmpty() && cadence.offerPositive(atMs) ->
                    SpokenFeedback(atMs, FeedbackMessageCatalog.positive(lang), null)
                else -> null
            }
        }
    }
}
```

Note: the `CadenceMode.INTERVAL` branch is a verbatim copy of the pre-existing body — this
preserves byte-for-byte behavior for every current caller (`MovementAnalyzer` and every existing
`LiveDrillSession` construction, which all default to `CadenceMode.INTERVAL`).

### Steps

- [ ] 1. Add failing tests to `DrillRepProcessorTest.kt`:

  ```kotlin
  // ---- EVERY_STROKE mode ----

  @Test
  fun everyStrokeModeSpeaksTopCueWhenVoiceFree() {
      val cadence = FeedbackCadencePolicy(mode = CadenceMode.EVERY_STROKE, isVoiceBusy = { false })
      val spoken = DrillRepProcessor.emitRepFeedback(
          rep(listOf(runnerUpCue, topCue)), atMs = 0L, cadence = cadence, lang = FeedbackLang.EN
      )
      assertNotNull(spoken)
      assertEquals(topCue, spoken.cue)
      assertTrue(spoken.voice)
  }

  @Test
  fun everyStrokeModeStillReturnsTextWhenVoiceBusy() {
      val cadence = FeedbackCadencePolicy(mode = CadenceMode.EVERY_STROKE, isVoiceBusy = { true })
      val spoken = DrillRepProcessor.emitRepFeedback(
          rep(listOf(runnerUpCue, topCue)), atMs = 0L, cadence = cadence, lang = FeedbackLang.EN
      )
      assertNotNull(spoken)
      assertEquals(topCue, spoken.cue)
      assertFalse(spoken.voice, "busy voice must still return the cue for on-screen text, just voice=false")
  }

  @Test
  fun everyStrokeModeFiresPositiveOnEveryCleanRepWhenFree() {
      val cadence = FeedbackCadencePolicy(mode = CadenceMode.EVERY_STROKE, isVoiceBusy = { false })
      val cleanRep = rep(cues = emptyList())
      val first = DrillRepProcessor.emitRepFeedback(cleanRep, atMs = 0L, cadence = cadence, lang = FeedbackLang.EN)
      val second = DrillRepProcessor.emitRepFeedback(cleanRep, atMs = 1L, cadence = cadence, lang = FeedbackLang.EN)
      assertNotNull(first)
      assertNotNull(second)
      assertNull(first.cue)
      assertNull(second.cue)
      assertTrue(first.voice)
      assertTrue(second.voice, "EVERY_STROKE praise must not be throttled between consecutive clean reps")
  }

  @Test
  fun everyStrokeModeNoTextOnlyPraiseWhenVoiceBusy() {
      val cadence = FeedbackCadencePolicy(mode = CadenceMode.EVERY_STROKE, isVoiceBusy = { true })
      val cleanRep = rep(cues = emptyList())
      val spoken = DrillRepProcessor.emitRepFeedback(cleanRep, atMs = 0L, cadence = cadence, lang = FeedbackLang.EN)
      assertNull(spoken, "no text-only praise per spec — busy voice means no positive feedback at all this rep")
  }
  ```

  Add missing imports (`import kotlin.test.assertFalse`, `import kotlin.test.assertTrue`) if not
  already present.
- [ ] 2. Run and confirm failure:
      `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.DrillRepProcessorTest"`
      (compile error: `CadenceMode` unresolved in this file's import scope, or logic mismatch —
      `emitRepFeedback` doesn't branch yet).
- [ ] 3. Implement the branch in `DrillRepProcessor.emitRepFeedback` exactly as in Interfaces
      above (the `when (cadence.mode)` construct; keep the existing KDoc, append a paragraph
      describing the EVERY_STROKE branch: "In `CadenceMode.EVERY_STROKE`, cues never compete for
      a time window — every rep's top-severity cue is offered immediately, gated only on whether
      the voice channel is currently free (`cadence.offerEveryStroke`); the returned
      `SpokenFeedback.voice` flag tells the caller whether to actually play audio. A skipped cue
      is never queued or replayed.").
- [ ] 4. Run again, confirm full class green:
      `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.DrillRepProcessorTest"`.
- [ ] 5. Commit:
      `git add shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillRepProcessor.kt shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/DrillRepProcessorTest.kt`
      then commit with message `feat(cadence): DrillRepProcessor.emitRepFeedback branches on CadenceMode`.

## Task 4 — `LiveDrillSession` end-to-end test with `EVERY_STROKE`

Model: sonnet.

### Files
- `shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/LiveDrillSessionTest.kt` (edit only —
  no production code change; `LiveDrillSession`'s existing `cadence: FeedbackCadencePolicy`
  constructor param already accepts any policy, including an `EVERY_STROKE` one, unchanged)

### Interfaces

No new production interface — this task proves `LiveDrillSession` correctly threads an
`EVERY_STROKE` policy's `SpokenFeedback.voice` flags through `onFrame()`'s returned list, using
the file's existing `singleStrokeXs`/`keypointsAt`/`timestampsFor`/`feedAll` fixtures.

### Steps

- [ ] 1. Add a failing test to `LiveDrillSessionTest.kt` (place it near `usesMedianIntervalNotMeanOrLast`,
      same file, same class):

  ```kotlin
  @Test
  fun everyStrokeModeVoiceFlagReflectsBusyToggle() {
      var busy = false
      val session = LiveDrillSession(
          baseline = baseline(),
          aspectRatio = 1f,
          rules = emptyList(),
          handedness = Handedness.RIGHT,
          cameraYawDeg = 0f,
          cadence = FeedbackCadencePolicy(mode = CadenceMode.EVERY_STROKE, isVoiceBusy = { busy })
      )
      val timestamps = timestampsFor(singleStrokeXs, intervalMs = 100L)
      val feedback = feedAll(session, singleStrokeXs, timestamps)
      // With an empty rules list every rep is "clean" -> only positive reinforcement is possible;
      // this asserts the wiring (voice flag reaches the caller), not cue selection itself.
      assertTrue(feedback.isNotEmpty(), "expected at least one rep's worth of feedback from the single stroke shape")
      assertTrue(feedback.all { it.voice }, "isVoiceBusy() returned false throughout -> every emitted item must be voice=true")
  }
  ```

  (`busy` is deliberately never flipped to `true` in this pass — flipping it mid-stream would
  require asserting against a specific rep index tied to the exact frame the stroke stabilizes
  on, which is incidental detail of `StrokeDetector2D`'s smoothing window, not something this
  test should pin down. The false→true wiring itself is already fully covered by Task 3's
  `DrillRepProcessorTest`; this test's job is only to prove `LiveDrillSession` does not drop or
  overwrite the `voice` flag on the way out of `onFrame`.)
- [ ] 2. Run and confirm failure (compile error: `CadenceMode` unresolved import, or the test
      fails because `LiveDrillSession` isn't yet passing the flag through — expected to already
      pass once Tasks 1–3 land, since `LiveDrillSession.onFrame` calls
      `DrillRepProcessor.emitRepFeedback` unchanged; this run is a regression/wiring check, not
      new production logic):
      `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.LiveDrillSessionTest"`.
- [ ] 3. If it fails, the likely cause is a missing `import com.ttcoachai.shared.drill.CadenceMode`
      — but `CadenceMode` lives in the same package (`com.ttcoachai.shared.drill`) as this test
      file, so no import should be needed; if it still fails, inspect the failure message and fix
      only what's broken (do not weaken the assertion). No production code changes are expected
      in this task.
- [ ] 4. Run again, confirm green:
      `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.LiveDrillSessionTest"`.
- [ ] 5. Commit:
      `git add shared/src/commonTest/kotlin/com/ttcoachai/shared/drill/LiveDrillSessionTest.kt`
      then commit with message `test(cadence): prove LiveDrillSession threads EVERY_STROKE voice flag`.

## Task 5 — Voice-busy signal: `PresetVoiceController.isBusy()` / `DrillTtsController.isBusy()`

Model: sonnet.

### Files
- `app/src/main/java/com/ttcoachai/pose/PresetVoiceController.kt` (edit)
- `app/src/main/java/com/ttcoachai/pose/DrillTtsController.kt` (edit)

### Interfaces

```kotlin
// PresetVoiceController
fun isBusy(): Boolean = clipPlayer != null || ttsSpeaking

// DrillTtsController
fun isBusy(): Boolean = ttsSpeaking
```

### Steps

- [ ] 1. Confirm there is no existing Android unit-test harness for these classes (Robolectric or
      similar) before starting: `grep -n "robolectric\|Robolectric" app/build.gradle*` — expected
      to find nothing (this repo's `app/` module has no Android-instrumented unit-test setup for
      these Context-dependent classes per project conventions). Since no automated test can drive
      real `MediaPlayer`/`TextToSpeech` callbacks in this module, this task's "failing test → run"
      step is the compile check: attempt to reference `isBusy()` from a throwaway call site first.
- [ ] 2. In `PresetVoiceController.kt`, add near the other `@Volatile` fields (after
      `private var clipPlayer: MediaPlayer? = null`, line 55):

  ```kotlin
  @Volatile private var ttsSpeaking: Boolean = false
  ```

  In `init()`, after `engine.language = locale` succeeds and `speechAvailable = true` is set,
  register a progress listener on the same `engine`:

  ```kotlin
  engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) { ttsSpeaking = true }
      override fun onDone(utteranceId: String?) { ttsSpeaking = false }
      override fun onError(utteranceId: String?) { ttsSpeaking = false }
  })
  ```

  Add the public accessor after `speak()`:

  ```kotlin
  /** True while a recorded clip is playing OR TTS is mid-utterance. Read from the UI thread by
   *  callers building an EVERY_STROKE [com.ttcoachai.shared.drill.FeedbackCadencePolicy]'s
   *  `isVoiceBusy` lambda; the two backing fields are updated off-thread (MediaPlayer completion
   *  callbacks, TTS utterance-progress callbacks) hence @Volatile on both. */
  fun isBusy(): Boolean = clipPlayer != null || ttsSpeaking
  ```

- [ ] 3. In `DrillTtsController.kt`, add the same `@Volatile private var ttsSpeaking: Boolean = false`
      field, the same `UtteranceProgressListener` registration inside `init()` (this class has no
      clip player, so `isBusy()` only reads `ttsSpeaking`):

  ```kotlin
  fun isBusy(): Boolean = ttsSpeaking
  ```

- [ ] 4. Build check (this IS the "run" step for this task, in place of a unit test — Android
      Context-bound classes have no test harness here): `./gradlew :app:assembleDebug`. Confirm
      it compiles clean (no new warnings about the listener signature — `UtteranceProgressListener`
      is an abstract class with exactly these 3 abstract methods; verify against the SDK if the
      compiler complains about a missing override, but `onStart`/`onDone`/`onError` are the
      complete abstract set).
- [ ] 5. Commit:
      `git add app/src/main/java/com/ttcoachai/pose/PresetVoiceController.kt app/src/main/java/com/ttcoachai/pose/DrillTtsController.kt`
      then commit with message `feat(cadence): add isBusy() voice-channel signal to both TTS controllers`.

## Task 6 — `SettingsManager.isEveryStrokeCadence()`

Model: haiku.

### Files
- `app/src/main/java/com/ttcoachai/managers/SettingsManager.kt` (edit)

### Interfaces

```kotlin
companion object {
    /** [feedback_frequency] value meaning "cue after every stroke" (the Annoying cadence mode),
     *  distinct from the existing 3/5/10 "cues per session" integers. */
    const val FEEDBACK_FREQUENCY_EVERY_STROKE = 1
}

fun isEveryStrokeCadence(): Boolean = getFeedbackFrequency() == FEEDBACK_FREQUENCY_EVERY_STROKE
```

### Steps

- [ ] 1. This is a plain-Kotlin, Context-independent addition to a class that is otherwise
      Android-Context-bound (constructed with a `Context`), so it has no existing unit-test
      coverage either — same situation as Task 5. Pre-check by grepping for any test file
      exercising `SettingsManager`: `grep -rl "SettingsManager(" app/src/test/ 2>/dev/null` (if
      one turns up, add a test there instead of skipping to the build-check step below).
- [ ] 2. Add the `companion object { const val FEEDBACK_FREQUENCY_EVERY_STROKE = 1 }` block and the
      `isEveryStrokeCadence()` function to `SettingsManager.kt`, placed directly after the existing
      `getFeedbackFrequency()`/`setFeedbackFrequency()` pair (lines 48-50).
- [ ] 3. Build check: `./gradlew :app:compileDebugKotlin` (cheaper than a full assemble for a
      pure-addition change with no XML/resource dependency).
- [ ] 4. Commit:
      `git add app/src/main/java/com/ttcoachai/managers/SettingsManager.kt`
      then commit with message `feat(cadence): add SettingsManager.isEveryStrokeCadence()`.

## Task 7 — Fourth "Annoying" segment in both cues-per-session UIs

Model: haiku.

### Files
- `app/src/main/res/values/strings.xml` (edit — add 2 strings near line 935)
- `app/src/main/res/values-uk/strings.xml` (edit — add 2 strings near line 721)
- `app/src/main/res/layout/layout_drill_menu_content.xml` (edit — 4th button after line 381,
  inside `#toggle_cues_per_session`)
- `app/src/main/res/layout/fragment_feedback.xml` (edit — 4th button after line ~709, inside the
  second `#toggle_cues_per_session` block at line 656)
- `app/src/main/java/com/ttcoachai/managers/TrainingUIController.kt` (edit — `setupFeedbackSettings`,
  lines 95-126)
- `app/src/main/java/com/ttcoachai/fragment/FeedbackFragment.kt` (edit — the `selectCues`
  block at lines 148-162)

### Interfaces

New string keys (both `values/strings.xml` and `values-uk/strings.xml`):

```xml
<!-- values/strings.xml -->
<string name="feedback_cues_annoying">Annoying</string>
<string name="feedback_cues_annoying_sub">Cue after every stroke; skipped if the voice is still speaking</string>
```

```xml
<!-- values-uk/strings.xml -->
<string name="feedback_cues_annoying">Настирливий</string>
<string name="feedback_cues_annoying_sub">Підказка після кожного удару; пропускається, якщо голос ще говорить</string>
```

New button in both layouts, styled identically to the existing three (place immediately after
the `btn_cues_10` `MaterialButton` closing tag, still inside `#toggle_cues_per_session`):

```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/btn_cues_annoying"
    style="@style/Widget.Material3.Button.TextButton"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:minHeight="0dp"
    android:insetTop="0dp"
    android:insetBottom="0dp"
    android:paddingVertical="10dp"
    android:textAllCaps="false"
    android:textSize="13sp"
    android:fontFamily="@font/inter_tight_semibold"
    app:strokeWidth="0dp"
    app:elevation="0dp"
    app:cornerRadius="999dp"
    android:text="@string/feedback_cues_annoying" />
```

`TrainingUIController.setupFeedbackSettings` `selectCues` gains the 4th branch:

```kotlin
val cuesButtons = listOf(
    binding.drillMenu.btnCues3,
    binding.drillMenu.btnCues5,
    binding.drillMenu.btnCues10,
    binding.drillMenu.btnCuesAnnoying
)
fun selectCues(count: Int, persist: Boolean) {
    val selected = when (count) {
        5 -> binding.drillMenu.btnCues5
        10 -> binding.drillMenu.btnCues10
        SettingsManager.FEEDBACK_FREQUENCY_EVERY_STROKE -> binding.drillMenu.btnCuesAnnoying
        else -> binding.drillMenu.btnCues3
    }
    cuesButtons.forEach { styleSegment(it, it === selected) }
    if (persist) settingsManager.setFeedbackFrequency(count)
}
binding.drillMenu.btnCues3.setOnClickListener { selectCues(3, persist = true) }
binding.drillMenu.btnCues5.setOnClickListener { selectCues(5, persist = true) }
binding.drillMenu.btnCues10.setOnClickListener { selectCues(10, persist = true) }
binding.drillMenu.btnCuesAnnoying.setOnClickListener {
    selectCues(SettingsManager.FEEDBACK_FREQUENCY_EVERY_STROKE, persist = true)
}
selectCues(settingsManager.getFeedbackFrequency(), persist = false)
```

Same shape applies to `FeedbackFragment.kt`'s `cuesButtons`/`selectCues` block (add
`binding.btnCuesAnnoying`, the same 4th `when` branch and click listener, using `sm` instead of
`settingsManager` — match the existing local variable name in that file).

### Steps

- [ ] 1. No automated test exists for XML layout or click-wiring in this codebase (View-Binding
      classes are exercised manually on-device, per the "Build-and-JVM-test verified only" note
      in the project CLAUDE.md's shipped-feature history) — this task's verification is a build +
      manual smoke, not a unit test. Before editing, confirm the button IDs you're about to add
      don't already exist: `grep -rn "btn_cues_annoying" app/src/main/res/` (expect no matches).
- [ ] 2. Add the two string pairs to `values/strings.xml` (near line 935, after
      `feedback_cues_per_session`) and `values-uk/strings.xml` (near line 721) exactly as in
      Interfaces above.
- [ ] 3. Add the 4th `MaterialButton` to `layout_drill_menu_content.xml` (after the `btn_cues_10`
      button, still inside `#toggle_cues_per_session`, around line 381) and to
      `fragment_feedback.xml` (the second `#toggle_cues_per_session` block starting at line 656 —
      NOT the first "read-only" one at line 323, which mirrors the drill-menu panel for a
      different screen state; check both blocks in the file and add the button to whichever ones
      the earlier grep at lines 330/348/366/662/680/698 actually found — there were TWO
      `toggle_cues_per_session` occurrences in this file per the earlier read, both need the 4th
      button so the segment stays 1:1 with the drill-menu copy).
- [ ] 4. Edit `TrainingUIController.setupFeedbackSettings` per Interfaces above.
- [ ] 5. Edit `FeedbackFragment.kt`'s cues-per-session block (lines 148-162) per Interfaces above
      — read the surrounding function first to match the local `sm`/`binding` naming exactly as
      it already appears in that file.
- [ ] 6. Build: `./gradlew :app:assembleDebug`. Confirm it compiles (View Binding will fail loudly
      at compile time if `btnCuesAnnoying` doesn't resolve in either binding class — that's the
      closest thing to a "failing test" for this task; if it fails, the IDs in one of the two XML
      edits don't match, fix and rebuild).
- [ ] 7. Commit:
      `git add app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml app/src/main/res/layout/layout_drill_menu_content.xml app/src/main/res/layout/fragment_feedback.xml app/src/main/java/com/ttcoachai/managers/TrainingUIController.kt app/src/main/java/com/ttcoachai/fragment/FeedbackFragment.kt`
      then commit with message `feat(cadence): add Annoying 4th segment to both cues-per-session UIs`.

## Task 8 — Live wiring: `RtmposeTrainingController` + `RtmposeDrillActivity`

Model: sonnet.

### Files
- `app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt` (edit — `ensureSession()`
  around line 350-386, `onPoseResult` around line 299-346)
- `app/src/main/java/com/ttcoachai/pose/RtmposeDrillActivity.kt` (edit — around line 339-343)

### Interfaces

`RtmposeTrainingController.ensureSession()` — construct the cadence policy conditionally:

```kotlin
private fun ensureSession(): LiveDrillSession {
    var current = session
    if (current == null || !sessionCreated) {
        current = LiveDrillSession(
            baseline = baseline,
            aspectRatio = aspectRatio,
            rules = rules,
            handedness = handedness(),
            lang = coachLang(),
            cadence = if (settingsManager.isEveryStrokeCadence()) {
                FeedbackCadencePolicy(
                    mode = CadenceMode.EVERY_STROKE,
                    isVoiceBusy = { voiceController?.isBusy() == true }
                )
            } else {
                FeedbackCadencePolicy()
            },
            cameraYawDeg = 0f,
            hipTravelMaxTorso = hipTravelMaxTorso,
            metricBands = metricBands,
            cueFilter = { cue ->
                val type = mapMetricToCorrectionType(cue.metricKey)
                type == CorrectionType.GENERAL || settingsManager.isCorrectionTypeEnabled(type)
            }
        )
        // ... rest unchanged (logBaselineOnce, onRep wiring)
    }
    return current
}
```

Needs new imports: `com.ttcoachai.shared.drill.CadenceMode`, `com.ttcoachai.shared.drill.FeedbackCadencePolicy`.

`onPoseResult` — gate the voice call on `item.voice`, always show text:

```kotlin
Log.i(TAG, "SPOKEN metricKey=${item.cue?.metricKey ?: "positive"} type=$type voice=${item.voice}")

if (item.voice) {
    voiceController?.speak(item)
} else {
    voiceController?.showOnly(item)
    Log.d(TAG, "SKIPPED-VOICE metricKey=${item.cue?.metricKey ?: "positive"} reason=voice-busy")
}
stateManager.addFeedback(item.message)
stateManager.addFeedbackItems(...)  // unchanged
```

`PresetVoiceController` needs a `showOnly` method (this task adds it, since Task 5 didn't) —
`speak()`'s current body already does `onScreen(feedback.message)` first, unconditionally, then
gates the audio; the cleanest non-duplicative fix is to extract that first line:

```kotlin
// PresetVoiceController.kt — add alongside speak()
/** Shows [feedback]'s on-screen text WITHOUT playing any audio — used when the caller has
 *  already decided (e.g. [com.ttcoachai.shared.drill.SpokenFeedback.voice] is false) that this
 *  item must not be spoken, but the text must still appear (spec: text/on-screen feedback is
 *  never gated by cadence or voice-busy state). */
fun showOnly(feedback: SpokenFeedback) {
    onScreen(feedback.message)
}
```

`DrillTtsController` gets the same `showOnly` for `RtmposeDrillActivity`'s use:

```kotlin
fun showOnly(feedback: SpokenFeedback) {
    onScreen(feedback.message)
}
```

`RtmposeDrillActivity` around line 339-343 — apply the same gate:

```kotlin
val feedback = session.onFrame(keypoints, timestampMs)
for (item in feedback) {
    if (item.voice) {
        tts.speak(item)
    } else {
        tts.showOnly(item)
    }
    // ... whatever else this loop already does with `item` stays unchanged — read the
    // surrounding ~15 lines before editing to preserve them exactly.
}
```

### Steps

- [ ] 1. Re-read `RtmposeTrainingController.kt`'s `ensureSession()` (lines 350-386) and `onPoseResult`
      (lines 299-346) in full immediately before editing — this plan's snippets above show the
      target shape but the surrounding comments/log lines must be preserved, only the specific
      lines shown are new/changed.
- [ ] 2. Add `showOnly()` to `PresetVoiceController.kt` (after `speak()`, before `playClip`) and to
      `DrillTtsController.kt` (after `speak()`), exactly as in Interfaces above.
- [ ] 3. Edit `RtmposeTrainingController.ensureSession()`: add the `isEveryStrokeCadence()` branch for
      `cadence =`, add the two imports (`CadenceMode`, `FeedbackCadencePolicy`).
- [ ] 4. Edit `RtmposeTrainingController.onPoseResult`: replace the unconditional
      `voiceController?.speak(item)` line with the `if (item.voice) ... else ...` gate + the
      `SKIPPED-VOICE` debug log line, and extend the existing `Log.i(TAG, "SPOKEN ...")` line to
      include `voice=${item.voice}`.
- [ ] 5. Re-read `RtmposeDrillActivity.kt` lines ~330-350 in full, then apply the same `item.voice`
      gate around the existing `tts.speak(item)` call at line 342, preserving every other line in
      that loop body unchanged.
- [ ] 6. Build: `./gradlew :app:assembleDebug`. This is the closest available check — there is no
      unit-test harness for these Context/CameraX-bound classes (confirmed in Tasks 5/6); a clean
      compile plus the shared-module tests from Tasks 1-4 (which already prove the `voice` flag's
      correctness at the source) are the verification for this task.
- [ ] 7. Commit:
      `git add app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt app/src/main/java/com/ttcoachai/pose/RtmposeDrillActivity.kt app/src/main/java/com/ttcoachai/pose/PresetVoiceController.kt app/src/main/java/com/ttcoachai/pose/DrillTtsController.kt`
      then commit with message `feat(cadence): wire EVERY_STROKE cadence + voice-busy gate into both live paths`.

## Task 9 — Docs: `DESIGN_LIMITATIONS.md` L-51 + CLAUDE.md gotcha, final full verification, merge

Model: haiku for the doc edits, sonnet for the final build/test/merge sequencing.

### Files
- `docs/DESIGN_LIMITATIONS.md` (edit — new entry, `## Open` section, before `## Resolved` at
  line 760; last used number is **L-50**, confirmed at line 515, so this is **L-51**)
- `/Users/itsurkan/Dev/personal/TT_Coach/CLAUDE.md` (edit — one line under "Gotchas — current
  (2D pivot)")

### Interfaces

New `DESIGN_LIMITATIONS.md` entry (insert immediately before the `## Resolved` heading at line
760, matching the file's existing entry format — heading level, `**Refs:**` line):

```markdown
### L-51 · Feedback-settings cadence sliders are unwired from the live training path — `OPEN`
`fb_pause_between_ms`, `fb_reminder_ms`, `fb_silence_before_praise_ms`, `fb_pause_after_stroke_ms`
(all persisted via `SettingsManager`, editable in `FeedbackFragment`) and the 3/5/10 "cues per
session" segment (`feedback_frequency`, when NOT set to the new Annoying value `1`) are read
nowhere in the live path: `RtmposeTrainingController.ensureSession()` builds a plain
`FeedbackCadencePolicy()` with the hard-coded 3000ms/5000ms defaults whenever
`settingsManager.isEveryStrokeCadence()` is false, ignoring every slider value the user set.
Only the new Annoying (`feedback_frequency == 1`) value is actually wired to a different cadence
mode. Pre-existing gap (not introduced by the Annoying-cadence feature) — surfaced while wiring
`RtmposeTrainingController.ensureSession()`'s cadence construction for this feature.
**Refs:** `RtmposeTrainingController.kt` (`ensureSession`); `SettingsManager.kt`
(`getFbPauseBetweenMs`/`getFbReminderIntervalMs`/`getFbSilenceBeforePraiseMs`/
`getFbPauseAfterStrokeMs`/`getFeedbackFrequency`); `FeedbackCadencePolicy.kt`.
```

CLAUDE.md gotcha (add as a new bullet under "## Gotchas — current (2D pivot)", after the
"Camera yaw is per-rep..." bullet or wherever fits alphabetically/thematically — insert near the
other cadence/feedback-related bullets):

```markdown
- **`feedback_frequency == 1` means "every-stroke" (Annoying) cadence, not a 4th cues-per-session
  count.** `SettingsManager.FEEDBACK_FREQUENCY_EVERY_STROKE` — `RtmposeTrainingController`/
  `RtmposeDrillActivity` read `isEveryStrokeCadence()` to pick `CadenceMode.EVERY_STROKE` over the
  standard 3–5s policy; a stroke's cue is dropped from voice (never queued) if the voice channel
  is still busy, but its text always appears. The pre-existing `fb_*` cadence sliders and the
  3/5/10 segment remain unwired from the live path in every other mode — see L-51.
```

### Steps

- [ ] 1. Insert the L-51 entry into `docs/DESIGN_LIMITATIONS.md` immediately before `## Resolved`
      (line 760 as read at plan-writing time — re-locate the exact line before inserting, since
      concurrent sessions may have appended further entries; append after the LAST `### L-`
      heading under `## Open`, never inside `## Resolved`).
- [ ] 2. Insert the CLAUDE.md bullet under "## Gotchas — current (2D pivot)" in
      `/Users/itsurkan/Dev/personal/TT_Coach/CLAUDE.md`.
- [ ] 3. Full verification sweep:
      - `./gradlew :shared:jvmTest > /tmp/annoying-cadence-shared-jvmtest.log 2>&1` (write to a
        scratch log file, then grep it — do not pipe a backgrounded/long-running gradle command
        directly to `tail`/`grep`, per the global working-rules note on lost exit codes) —
        confirm zero failures across the whole shared module, not just the classes touched above.
      - `./gradlew :app:assembleDebug > /tmp/annoying-cadence-app-assemble.log 2>&1` — confirm
        BUILD SUCCESSFUL.
      - Do NOT run bare `./gradlew test` / `:app:testDebugUnitTest` as a pass/fail gate — the
        project CLAUDE.md documents a pre-existing unrelated failure there
        (`MotionAnalyzerJsonTest`, frozen legacy code); scope to the classes this plan touched if
        you want extra confidence: `./gradlew test --tests "com.ttcoachai.shared.drill.*"`.
- [ ] 4. Commit the docs:
      `git add docs/DESIGN_LIMITATIONS.md CLAUDE.md`
      then commit with message `docs(cadence): record L-51 unwired-sliders limitation + CLAUDE.md gotcha`.
- [ ] 5. Merge to main (owner's standing rule: merge automatically, no PR, no asking):
      `git checkout main && git merge --no-ff feat/annoying-cadence -m "Merge feat/annoying-cadence: Annoying every-stroke feedback cadence mode"`
      then `git checkout feat/annoying-cadence` is NOT needed afterward — confirm `git log --oneline -1 main`
      shows the merge commit and `git status` is clean before considering this task done.
