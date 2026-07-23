# Pose data upload to Firebase — design

Date: 2026-07-23
Status: approved (design decisions confirmed by user; write-up only, no implementation yet)

## Goal

Every RTM training session uploads its full per-frame pose stream (schema-v2 JSON,
COCO-17 keypoints, coordinates already rounded to 4 decimals per the schema-v2
contract) to Firebase Storage in the background, without the player waiting for it.
Ship alongside it: a user-facing consent toggle (default ON) and real published
privacy/terms pages the app can link to.

This data is the raw material for later offline algorithm tuning (protocol-footage
gaps noted in CLAUDE.md — `Videos/` wasn't shot to spec, and camera-yaw estimation
saturates on real footage per `docs/DESIGN_LIMITATIONS.md` L-25) and, longer term,
the Phase 4 AI Coach payload. This spec only lands the upload path — no server-side
processing.

## Context & constraint

Live pose frames flow through exactly one choke point today:
`RtmposeTrainingController.onPoseResult(keypoints: List<Keypoint2D>, timestampMs: Long)`
(`app/src/main/java/com/ttcoachai/pose/RtmposeTrainingController.kt:252`), called on
the UI thread once per analyzed camera frame via a `runOnUiThread` hop from
`RtmposeFrameProcessor`'s callback (set up in `start()`, line 158).

**No component retains full-session poses today**, and neither may be modified to do so:

- `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/LiveDrillSession.kt` keeps
  only a ~4s rolling buffer; its `trim(minNeededIndex)` drops and re-indexes older
  frames as it goes (comment at line 70: "indices shift when the buffer trims").
- `TrainingStateManager.repPoseHistory` (`app/src/main/java/com/ttcoachai/managers/TrainingStateManager.kt:42`)
  keeps only the last 10 `RepPoseCapture` entries (line 158-160: `if (repPoseHistory.size > 10) repPoseHistory.removeAt(0)`).

Both exist for live-feedback/session-review purposes and dropping/trimming is
correct for those jobs. A full-session recording needs a separate, dedicated
listener at the same tap point — not a repurposing of either buffer.

**Session-id ordering problem:** the session id used everywhere downstream
(Firestore doc id, `poseDataPath`) is generated *after* the session ends, inside
`CloudSyncManager.saveTrainingFromState` (`app/src/main/java/com/ttcoachai/managers/CloudSyncManager.kt:286`,
`TrainingSession.generateId()`), which is called from
`TrainingActivity.saveSessionToCloud()` (`app/src/main/java/com/ttcoachai/TrainingActivity.kt:315-359`),
itself called from `stopTraining()` (line 294-313) — well after the last pose frame
was recorded. The recorder cannot know the session id while it is writing.

**Parser finding:** `PoseJsonV2Parser` (`shared/src/commonMain/kotlin/com/ttcoachai/shared/io/PoseJsonV2Parser.kt`)
locates every header field (`schemaVersion`, `topology`, `intervalMs`,
`videoWidth`/`videoHeight`, `totalFrames`, `videoDurationMs`, ...) with
document-global anchored regexes (lines 20-30) — header field order is **not**
significant to the parser. The documented "field-order tripwire" (`LANDMARK_RE`,
lines 31-33, cross-checked against `INDEX_KEY_RE` at lines 36 and 98-104) applies
**only within a landmark entry**, which must stay `index, x, y, score` in that
exact order. We nonetheless finalize the writer with a normally-ordered header
(matching `docs/pose_json_schema_v2.md`'s example) so on-device output is
byte-comparable with `scripts/poses/export_poses_rtmpose.py`'s desktop output —
not because the parser requires it, but so diffing/tooling built for the desktop
format works unmodified on device output.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | Capture the **full session, every frame** — no rep-window cropping, no downsampling | Downsampling would break wrist-speed peak detection on re-analysis; the whole detection pipeline (`StrokeDetector2D` etc.) assumes full fps. Cropping to rep windows would also discard the recovery-swing / non-rep frames needed to validate `ForwardStrokeFilter` and `RepFilter` against real footage. |
| 2 | Consent is a **Settings toggle, default ON**, no first-run blocking screen | User's explicit call, made with the Play Store data-safety declaration tradeoff in view — see Risks. |
| 3 | Background upload via **WorkManager**, ANY network constraint, exponential backoff | First `androidx.work` dependency in the app. ANY network (not Wi-Fi-only) because sessions are short-lived local artifacts a player may not revisit the app soon enough for Wi-Fi to show up; exponential backoff is WorkManager's default retry policy for transient failures. |
| 4 | Format: **schema-v2 JSON, compact (no pretty-print), gzipped** (`.json.gz`) | See size evidence below — gzip alone gets a 5.6x reduction on top of compacting; without it a 15-min session is a multi-MB per-session cellular upload. |
| 5 | Legal: in-app explanatory text + **real published** privacy/terms pages on the existing marketing site | `LegalLinks.kt` currently points at placeholder `ttcoach.ai` URLs the app doesn't own; a live toggle collecting pose data needs a live policy to point at. |

## Size evidence

Measured on `Videos/andrii_1/andrii_1_poses_rtm.json` (1106 frames, 17ms interval,
COCO-17):

| Format | Bytes/frame | Ratio vs pretty |
|---|---|---|
| Pretty-printed JSON (current on-disk format) | 2049 | 1.0x |
| Compact JSON | 886 | 2.3x |
| Compact + gzip(9) | 158 | **13.0x** (5.6x vs compact) |

Projected 15-minute session:

| FPS | Frames | Compact | Gzipped |
|---|---|---|---|
| 15 | 13,500 | 12.0 MB | 2.1 MB |
| 30 | 27,000 | 23.9 MB | 4.3 MB |

Compact-plus-gzip is the only combination that keeps a 15-minute session in the
low single-digit MB over a cellular ANY-network WorkManager constraint.

## Architecture

### A) `shared/src/commonMain/kotlin/com/ttcoachai/shared/io/PoseJsonV2Writer.kt`

Pure Kotlin, zero external dependencies (KMP split rule — iOS is a firm future
target, per CLAUDE.md). Mirror of `PoseJsonV2Parser`, not a shared abstraction with
it — writer and parser stay independent so a bug in one can't silently corrupt the
other's contract.

- `header(meta): String` / `frameLine(frame: PoseFrame2D): String` / `footer(): String`
  — streaming API so the caller (component B) never needs the whole document in
  memory at once.
- `PoseFrame2D` (`shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PoseFrame2D.kt`)
  and `PoseSequence2D` (`.../models/PoseSequence2D.kt`) are the existing schema-v2
  in-memory types; the writer's `meta` parameter carries the same header fields
  `PoseSequence2D` holds (`topology`, `model`, `videoName`, `intervalMs`,
  `totalFrames`, `videoDurationMs`, `videoWidth`, `videoHeight`).
- Own `round4(value: Float): String` — `commonMain` has no `String.format`. Must
  reproduce exactly what the Python exporter's `round()` + `json.dumps()` produces:
  `"0.5999"`, `"0.896"` (trailing zeros trimmed, not "0.8960"), correct handling of
  negatives, `0.0` → `"0.0"`, `1.0` → `"1.0"`, and values that round up
  (`0.99995` → `"1.0"`).
- Landmark entry field order `index, x, y, score` is load-bearing — it's the exact
  literal `PoseJsonV2Parser.LANDMARK_RE` matches. The writer hardcodes this order;
  it is not configurable.

### B) `app/src/main/java/com/ttcoachai/pose/PoseSessionRecorder.kt`

- Active only when `(SettingsManager.isPoseUploadEnabled()) AND (RTM path)`. The
  legacy MediaPipe pipeline (`PoseLandmarkerProcessor`) produces normalized
  MediaPipe-33 landmarks, not COCO-17 `Keypoint2D` — out of scope, no adapter
  planned.
- Tap point: `RtmposeTrainingController.onPoseResult(keypoints, timestampMs)`
  (line 252) — the controller adds a call into the recorder alongside its existing
  `overlayView?.setKeypoints(keypoints)` line, **after** the
  `stateManager.isTrainingActive` early-return. The recording covers exactly the
  training window, so `frameIndex`/`timestampMs` stay aligned with the session's
  own clock and with the reps `LiveDrillSession` detected; pre-start framing and
  post-stop idle frames are not recorded (they inflate the file and would offset
  every downstream index).
- Writes one compact frame line at a time to a **plain temp file** (not gzip yet)
  on a dedicated single-thread IO dispatcher. Two reasons this shape, both
  necessary: memory must stay flat (a 15-30fps 15-minute session is ~12-24 MB of
  string data — holding all of it in memory while the camera pipeline and ONNX
  Runtime inference are also running is not acceptable), and the camera/UI thread
  (`onPoseResult` runs via `runOnUiThread`) must never block on file IO.
- `finish(): File` is called once the session ends. Only at this point are
  `totalFrames` and `videoDurationMs` known (they're end-of-session facts, not
  per-frame ones) — so `finish()` writes the real header via
  `PoseJsonV2Writer.header(...)`, then streams the plain temp file through
  `GZIPOutputStream` into the final `.json.gz`, then deletes the temp. This is why
  the format can't be gzipped incrementally frame-by-frame: the header (which must
  come first in the JSON) needs facts only available at the end.
- Hard cap: **60 minutes / ~60,000 frames**. On hitting the cap, recording stops
  but does not fail the session — whatever was captured so far is finalized and
  still uploaded. This is a backstop, not a target (see Risks: on-device fps is
  unmeasured).
- `abort()` deletes the temp file outright — called when the player discards the
  session (`TrainingActivity.stopTraining(discard = true)`) or when consent is
  turned off mid-session.
- **Provisional filename, renamed on save:** because the recorder starts writing
  before a session id exists (see Context & constraint), the temp/final file uses
  a provisional name (e.g. a recorder-local UUID or start-timestamp) for its
  entire life. Only inside `CloudSyncManager.saveTrainingFromState`'s `onSaved`
  callback — which is the first point downstream code learns the real
  `sessionId` — does the file get renamed to `<sessionId>.json.gz` and only then
  is the upload enqueued (component C). If the session is discarded before
  `onSaved` fires, `abort()` has already deleted the file and nothing is
  ever enqueued.

### C) `app/src/main/java/com/ttcoachai/work/PoseUploadWorker.kt` + `PoseUploadQueue.kt`

- New `androidx.work:work-runtime-ktx` dependency in `app/build.gradle` (first use
  of WorkManager in this app; existing dependency block already has
  `kotlinx-coroutines-android` for the `CoroutineWorker` base).
- `PoseUploadQueue` is the enqueue-side API `TrainingActivity`/`CloudSyncManager`
  call after the rename in B's `onSaved` callback. Unique work name
  `"pose-upload-<sessionId>"` (`enqueueUniqueWork`, `ExistingWorkPolicy.KEEP`) so a
  retry or app restart can't double-enqueue the same file.
- Constraints: `NetworkType.CONNECTED` (any network, not `UNMETERED`), exponential
  backoff (`BackoffPolicy.EXPONENTIAL`, WorkManager's default initial delay is
  acceptable — no need to hand-tune it for v1).
- Upload target: `poses/{uid}/{sessionId}.json.gz`, `contentType = "application/json"`,
  `contentEncoding = "gzip"` (set via `StorageMetadata` so Cloud Storage — and any
  client downloading it later — can decompress transparently over HTTP).
- On success: PATCH the Firestore session doc's `poseDataPath` field (same field
  `PoseDataRepository.uploadPoseData` already sets via `TrainingSession.copy(poseDataPath = ...)`
  in `CloudSyncManager.saveTrainingSession`, lines 216-224 — this worker follows
  the same convention but runs later, asynchronously, against the already-saved
  doc) and delete the local `.json.gz`.
- **App-start sweep:** on app launch, scan the local pose-upload cache directory
  for orphan files (left behind by a process death mid-session, after the rename
  but before the worker completed) and enqueue workers for each. Also evict
  cached files older than 7 days or once the directory exceeds 200 MB (oldest
  first) — a local disk cap, independent of the Storage-side retention gap noted
  in Risks.
- `PoseDataRepository` (`app/src/main/java/com/ttcoachai/repository/PoseDataRepository.kt`)
  gains `uploadPoseFile(userId: String, sessionId: String, file: File): Result<String>`
  alongside the existing `uploadPoseData`/`uploadPoseDataJson` (which take
  in-memory `ByteArray`/`String` — unsuitable here since the whole point of B is to
  never hold the full payload in memory). Same `poses/$userId/$sessionId.json.gz`
  path convention as `uploadPoseData`'s `POSES_FOLDER` constant, `.json.gz`
  extension instead of `.json`, uses `putFile` (streaming) with `StorageMetadata`
  instead of `putBytes`.

### D) Consent

- `SettingsManager` (`app/src/main/java/com/ttcoachai/managers/SettingsManager.kt`)
  gains `isPoseUploadEnabled(): Boolean = prefs.getBoolean("pose_upload_enabled", true)`
  and `setPoseUploadEnabled(Boolean)`, following the file's existing boolean-pref
  pattern (e.g. `isAudioFeedbackEnabled`/`setAudioFeedbackEnabled` at lines 28-29).
- Toggle row in `AppSettingsActivity` (`app/src/main/java/com/ttcoachai/AppSettingsActivity.kt`)
  + `app/src/main/res/layout/activity_app_settings.xml`: a new card section
  following the file's established section pattern (icon+title header
  `LinearLayout` immediately above a `MaterialCardView`, e.g. the "Subscription
  Management" section at lines 288-402) — a switch row with explanatory body text
  plus a tappable privacy-policy link (`android:autoLink="web"` or a click
  listener opening `LegalLinks.PRIVACY_URL` via `Intent(ACTION_VIEW)`). Wired in
  `AppSettingsActivity` with a new `setupPoseUploadConsent()` alongside
  `setupSubscription()`/`setupDebugMode()`, same `OnCheckedChangeListener` →
  `cloudSyncManager.uploadSettings()` shape those two already use.
- New strings in `values/strings.xml` (EN) and mirrored in `values-uk/strings.xml`
  (per repo convention — see CLAUDE.md hot-file note on `values-uk` lagging
  newer EN keys, so both must land together here).
- Turning the toggle **OFF** cancels pending WorkManager work
  (`WorkManager.cancelAllWorkByTag`/unique-name cancellation for any
  `pose-upload-*` still queued) and deletes any local pending `.json.gz`/temp
  files. The gate is checked at session start (component B's activation check),
  so a player who has opted out never writes pose data to disk at all — there is
  no "written then discarded" step to leak through.

### E) Security rules

Cloud Firestore and Cloud Storage for Firebase are **different products with
different rules files** — `firestore.rules` (already in this repo, governing
`community_drills`/`sessions`/`users`) has no bearing on Cloud Storage access.
The repo currently has **no** Storage rules file at all, meaning Cloud Storage is
governed by whatever is live in the Firebase console — the Firebase default for
new projects (`allow read, write: if request.auth != null`) would let *any*
signed-in user read or overwrite *another* user's pose files at
`poses/{other-uid}/...`, since nothing ties the path to the requester's uid.

New `storage.rules` at repo root:

```
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

Plus `"storage": { "rules": "storage.rules" }` added to `firebase.json` (currently
only `{"firestore": {"rules": "firestore.rules"}}`).

Same convention already established for Community Drills:
**documentation-as-code, MANUAL deploy** — `firebase deploy --only storage` (or
paste into the Firebase console under Storage → Rules), same as the header
comment already in `firestore.rules` ("MANUAL DEPLOYMENT REQUIRED... NOT
auto-deployed by the build or CI/CD"). `storage.rules` should carry the same
warning header. Target bucket, confirmed from `app/google-services.json`
(`project_id: ttcoachai`): `ttcoachai.firebasestorage.app`.

### F) Legal pages

The marketing site is a **separate git repo**:
`/Users/itsurkan/Dev/personal/TT_Coach_AA_site`
(`git@github.com:itsurkan/TT_Coach_AA_site.git`), published at
`https://itsurkan.github.io/TT_Coach_AA_site/`. It is a static `index.html` plus
`src/*.jsx` (per the top-level `personal/CLAUDE.md` catalog entry). This work
**spans two repos and is committed separately** — the site change is not part of
the TT_Coach app PR.

- Add `privacy.html` and `terms.html` to that repo, linked from the `index.html`
  footer. Privacy page covers, at minimum, a section on pose-data collection:
  - **What is collected:** 2D joint (skeleton) coordinates per video frame during
    training sessions — no video, no camera imagery is stored or uploaded.
  - **Why:** session analysis/feedback and improving the coaching algorithm.
  - **Where stored:** Firebase Cloud Storage (Google Cloud), scoped per-user.
  - **Opt-out:** an in-app Settings toggle, on by default, which the player can
    switch off at any time to stop all future collection.
  - **Deletion:** how to request deletion of previously uploaded pose data.
- Repoint `app/src/main/java/com/ttcoachai/core/LegalLinks.kt` — currently
  `TERMS_URL`/`PRIVACY_URL` are placeholder `https://ttcoach.ai/...` URLs behind a
  `TODO(product): replace with final URLs before release` comment — to the
  GitHub Pages URLs (`https://itsurkan.github.io/TT_Coach_AA_site/privacy.html`,
  `.../terms.html`). This is the only TT_Coach-repo change component F makes; the
  page content itself lives in the site repo.

## Data flow

```
session start
  │  RtmposeTrainingController.start() — SettingsManager.isPoseUploadEnabled()
  │  AND RTM path → PoseSessionRecorder created, provisional filename chosen
  ▼
per frame
  │  RtmposeFrameProcessor callback → onPoseResult(keypoints, timestampMs)
  │  → recorder.onFrame(keypoints, timestampMs) [fire-and-forget, IO dispatcher]
  │  → PoseJsonV2Writer.frameLine(...) appended to plain temp file
  │  (cap: recording stops at ~60k frames / 60 min; already-captured data kept)
  ▼
session stop
  │  TrainingActivity.stopTraining(discard)
  ├─ discard = true  → recorder.abort() → temp file deleted → nothing enqueued
  └─ discard = false → recorder.finish()
       │  totalFrames/videoDurationMs now known
       │  PoseJsonV2Writer.header/footer + GZIPOutputStream(temp → final .json.gz)
       │  temp file deleted
       ▼
     CloudSyncManager.saveTrainingFromState(...) generates sessionId
       │  onSaved(sessionId) callback
       ▼
     rename provisional file → <sessionId>.json.gz
       │
       ▼
     PoseUploadQueue.enqueue(userId, sessionId, file)
       │  WorkManager unique work "pose-upload-<sessionId>", ANY network,
       │  exponential backoff
       ▼
     PoseUploadWorker.doWork()
       │  PoseDataRepository.uploadPoseFile(userId, sessionId, file)
       │  → poses/{uid}/{sessionId}.json.gz (contentType application/json,
       │    contentEncoding gzip)
       ├─ success → Firestore session doc poseDataPath set, local file deleted
       └─ failure → WorkManager retries with backoff (constraints unmet / IO error)

app start
  │  sweep local pose-upload cache dir → enqueue orphaned files
  │  evict cached files > 7 days old or dir > 200 MB
```

## Testing

- `shared` jvmTest — `PoseJsonV2WriterTest`:
  - Round-trip: write a real fixture (e.g. `andrii_1_poses_rtm.json`'s parsed
    `PoseSequence2D`) through the writer, re-parse with `PoseJsonV2Parser`,
    assert equality with the original.
  - `round4` edge cases: negative values, `0.0`, `1.0`, trailing-zero trimming
    (`0.8960` → `"0.896"`), values that round up across a boundary.
  - Landmark field-order tripwire: assert the emitted landmark literal matches
    `index, x, y, score` verbatim (regex or substring check against
    `PoseJsonV2Parser.LANDMARK_RE`'s pattern).
- `app` unit tests:
  - Consent gating: recorder does not activate when `isPoseUploadEnabled()` is
    false, or on the legacy (non-RTM) path.
  - Frame cap: recorder stops accepting frames at the cap and `finish()` still
    produces a valid file from what was captured.
  - Temp → final finalization: `finish()` produces a gzip-decodable file with a
    correct header (`totalFrames`, `videoDurationMs`) and deletes the temp;
    `abort()` deletes without producing a final file.
  - `PoseUploadWorker` success/retry behavior against a fake `PoseDataRepository`
    (success path sets `poseDataPath` + deletes local file; failure path returns
    `Result.retry()`).

## Non-goals

- Legacy MediaPipe capture path (no COCO-17 keypoints available there).
- Video/image upload of any kind — pose coordinates only.
- Any change to `LiveDrillSession`'s 4s rolling buffer or
  `TrainingStateManager`'s 10-rep ring — both are frozen for this work.
- A first-run consent screen (decision #2 — Settings toggle only).
- Real legal review of the drafted privacy/terms text (drafted content, not
  reviewed copy).
- Server-side processing of the uploaded files — this spec only lands the data
  in Storage; analysis/tuning against it is future work.

## Risks & follow-ups

- **Storage cost growth is unbounded.** No lifecycle/retention policy exists on
  the `ttcoachai.firebasestorage.app` bucket yet — every uploaded session stays
  forever. A Cloud Storage lifecycle rule (age-based deletion or move to a
  cheaper storage class) is a follow-up, not part of this spec.
- **On-device RTM frame rate is unmeasured.** The size projections (15/30 fps)
  are assumptions, not measurements from the live `RtmposeFrameProcessor` path;
  the 60-minute/60k-frame cap in component B is the backstop against an
  unexpectedly high sustained fps blowing past the projected sizes.
- **Play Store data-safety declaration** must be updated to disclose this new
  data collection (pose/skeleton coordinates, stored, user-controllable) before
  any release that ships this feature — required regardless of the in-app
  consent toggle.
