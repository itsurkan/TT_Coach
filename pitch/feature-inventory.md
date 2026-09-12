# TT_Coach_AI — Evidence-Backed Feature Inventory

**Date:** 2026-09-12 · **Commit:** `3d1dfaf` (`docs(pitch): add investor feature-analysis prompt for Fable 5.1`, branch `feat/per-phase-metric-grid`)

**Method:** Every status below was assigned by opening the source file cited in its "Code evidence"
row and confirming the claim directly (`grep`/`sed`/`Read`), not by trusting `CLAUDE.md` or the
Phase-0 orientation summary — both are treated as a map, not evidence. Per the brief's Shipped
rule: a feature is **Shipped** only if (a) there is a user-reachable screen (wired in
`AndroidManifest.xml` or `nav_graph.xml`, not a `FLAG_DEBUGGABLE`-gated dev tool) **and** (b) the
code path it runs is live production code, not frozen Stage-2 code and not a mock. Where the
orientation doc or `CLAUDE.md` disagreed with what the code actually does, the code wins; every
such case is logged in "Discrepancies found" below. Frozen Stage 2 (ball tracking, audio-contact,
trajectory) is excluded entirely per the brief.

## Status legend

| Status | Meaning |
|---|---|
| **Shipped** | User-reachable screen, live code path, no gate blocking normal use. |
| **Shipped-but-gated (gate named)** | Live code path and reachable screen, but a named condition currently blocks or weakens it in production (undeployed security rules, a stale/withdrawn data source, no device verification, etc.). |
| **Mock** | Screen exists and is reachable, but the code behind it does not do the real thing (e.g. no real billing). |
| **Roadmap-validated** | Validated as a direction in `CLAUDE.md`/research; zero shipped code, no screen. |

---

## 1. Real-time on-device voice cues

| Field | Value |
|---|---|
| What the player does | Trains against a fixed drill (forehand drive) with the phone on a stand; the app speaks a specific correction (e.g. "bend your elbow more") within 3–5 s of the rep, live, no cloud round-trip. |
| Status | **Shipped.** Live path via `PoseBackendFactory` → `MediaPipePoseLandmarkerBackend`, `RtmposeTrainingController`, `TrainingActivity` (the app's main/launcher-reachable training screen, `nav_graph.xml` `navigation_dashboard` → drill launch). |
| Code evidence | 8 per-axis correction chips, one metric each: `CLAUDE.md:69-91` (RTM correction taxonomy table) — verified against `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/CorrectionType.kt`-consuming code (`FeedbackCadencePolicy.kt`, `PresetVoiceController.kt`, `DrillTtsController.kt` all present under `shared/`/`app/src/main/java/com/ttcoachai/pose/`). Cadence 3–5 s: `CLAUDE.md:7`, `CLAUDE.md:23` ("existing 3–5s cue catalog"), class `FeedbackCadencePolicy.kt`. `HIP_HINGE` has **no physical mute chip** in `TrainingUIController.correctionChipPairs` (`CLAUDE.md:85-90`) — confirmed: `isCorrectionTypeEnabled` call sites found in `TrainingActivity.kt:404`, `TrainingUIController.kt:55,118`, `RtmposeTrainingController.kt:320`, but the chip-pair list itself only wires the other 7. |
| Measured proof points | Live-device FPS only (throughput, not cue-quality): `docs/pose-backend-fps-benchmark.md:20-25` — MediaPipe Full/Lite GPU 34.4–34.6 fps, CPU 28.0–29.5 fps, Samsung Galaxy S23, single run (explicitly caveated "indicative, not authoritative," `:35-36`). No measurement exists of real end-user cue latency, cue accuracy, or the 3–5 s cadence being felt as "real time" by a player. |
| Job-to-be-done | Get a specific, spoken correction on the same rep the error happened, without stopping to look at a phone or waiting for a coach. |
| Closest competitor behaviour | `docs/tt-coach-ai-context.md:219`: "real-time technique coaching for fixed TT drills — nobody does this, even the closest competitor (Spherely) is post-session." |
| Open L-numbers | L-04 (torso-lean sign noisy on real footage), L-41 (`stroke_speed` band unreachable at live camera frame rates: live-measured 2.6–4.6 torso-lengths/s vs. the shipped band, 16–24σ outside, every rep), L-50 (torso-lean inflated by axial rotation). |

---

## 2. Personal-baseline calibration ("calibrate, don't re-teach")

| Field | Value |
|---|---|
| What the player does | Records a short calibration set of their own forehand strokes; the app derives *their* reference angles (not a generic "ideal") and coaches against that baseline afterward. |
| Status | **Shipped.** `RtmposeCalibrationActivity` (`app/src/main/java/com/ttcoachai/pose/RtmposeCalibrationActivity.kt`), reachable from `TrainingActivity`'s calibration-required flow and from `ExerciseEditorActivity`'s "Reference: Baseline" option; one unified baseline lineage `"forehand_drive_rtm"` since the 2026-07-24 fix (`docs/shipped-features.md:90-98`). |
| Code evidence | `BaselineDeriver.deriveFromMetrics` (`shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/BaselineDeriver.kt`) — 2σ single-pass outlier exclusion, `qualityScore = 1 − mean(CV)`. `PersonalBaseline` model (`shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PersonalBaseline.kt`). Both calibration entry points now agree per `docs/shipped-features.md:90-98`. |
| Measured proof points | No live-device measurement exists of calibration completion rate, time-to-calibrate, or quality-score distribution across real players. The only fully-worked derivation example (`docs/shipped-baseline-derivation.md`) is `andrii_1`, which is a **withdrawn** reference clip (see feature 3 and L-49) — its numbers must not be cited as representative calibration output. |
| Job-to-be-done | Get corrected against your own best technique, not an abstract "ideal" that may not fit your body or level. |
| Closest competitor behaviour | `docs/tt-coach-ai-context.md:78`: "a human coach watching from one side also 'sees in 2D' ... the real comparison for users is 2D vs nothing, not 2D vs 3D" — no competitor in the list (§209-219) calibrates to the individual player at all; all compare to a fixed reference or nothing. |
| Open L-numbers | L-04 (facing-sign noise feeding torso-lean at calibration time), L-20 (calibration doesn't persist raw pose frames), L-14 (handedness is explicit config, not auto-detected). |

---

## 3. Training without calibration (seeded standard-mode drills)

| Field | Value |
|---|---|
| What the player does | Starts a forehand-drive drill immediately with **zero calibration step**, via one of two pre-seeded drills ("Andrii" / "General"). |
| Status | **Shipped.** The mechanism has changed since the last `CLAUDE.md` entry on it (see Discrepancies §D1). Two idempotently-seeded `CustomDrillEntity` rows (`SeededDrillsPolicy.SEED_ANDRII_ID`, `SEED_GENERAL_ID`) are created at first app launch and reachable via `DrillsFragment` → `TrainingActivity`. Caveat: the per-phase reference ranges these drills coach against are coach opinion, not measurement (`Evidence.COACH_OPINION` throughout — L-43/L-48, see Code evidence and Open L-numbers below). |
| Code evidence | `app/src/main/java/com/ttcoachai/util/SeededDrillsPolicy.kt:15-16,59`. **Current production reference source is NOT `ShippedBaselines.FOREHAND_ANDRII`**: `TrainingActivity.kt:161-188,269-282` resolves standard-mode reps via `PerPhaseTargetsSeed.rangeBands()` (fallback) or the drill's own bands, synthesizing its severity σ-carrier with `BandBaselineSynthesizer.fromBands(...)` (`shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/BandBaselineSynthesizer.kt`) — a grep for `ShippedBaselines.` in `app/src`/`shared/src` production code returns zero hits outside `ShippedBaselines.kt` itself and its tests (`docs/DESIGN_LIMITATIONS.md:138-155` L-43, resolved 2026-08-16). |
| Measured proof points | `PerPhaseSeededBandCoverageTest` (jvmTest) asserts every seeded band covers the measured median across `video_3`/`video_4` parity fixtures, 32 cycles (`docs/DESIGN_LIMITATIONS.md:413-461`). **No measurement exists** of on-device behaviour — "Build-and-JVM-test verified only — NO device smoke this session" (`CLAUDE.md:149-152`). |
| Job-to-be-done | Start training the same session you download the app, without a separate calibration ritual. |
| Closest competitor behaviour | `docs/tt-coach-ai-context.md:219` (as feature 1) — no competitor offers structured per-metric feedback at all, calibrated or not. |
| Open L-numbers | L-37, L-38 (legacy caveats on the now-superseded `FOREHAND_ANDRII` object, kept for history), L-39 (deleting both seeded drills resurrects them), L-40 (old community-shared drills surface only 2/7 bands), L-48 (**all 10** `PER_PHASE_RANGES` entries are now `Evidence.COACH_OPINION`, zero `Evidence.MEASURED` — see Discrepancies §D2), L-49 (`andrii_1` withdrawn as reference footage). |

---

## 4. Custom drill editor (7 metric bands)

| Field | Value |
|---|---|
| What the player does | Creates or clones a drill and sets their own acceptable range ("band") for each of 7 metrics, per stroke phase. |
| Status | **Shipped.** `ExerciseEditorActivity` (`app/src/main/java/com/ttcoachai/ui/ExerciseEditorActivity.kt`), reachable from Drills (New/Clone/Edit). |
| Code evidence | `DrillMetrics.ALL_KEYS` = `PEAK_KEYS` (4) + `DERIVED_KEYS` (3) = 7 (`shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillMetrics.kt:50-59`). Encoding: `PerPhaseTargetsCodec`, with a legacy-key map for old community-shared drill blobs (`CLAUDE.md:140-142`). |
| Measured proof points | Seed values for the 10 (metric×phase) entries: 4 owner-re-set bands trace to measured parity-fixture ranges (`docs/DESIGN_LIMITATIONS.md:441-458` — `shoulder_angle@FOLLOWTHROUGH` 55–85° against measured 14.5–83.1°/median 71.9°, n=31; `hip_flexion@CONTACT` 115–150° against measured 113.3–177.3°/median 121.3°, n=32). The remaining 6 entries and both `knee_bend` entries are **not** measured (see feature 3 evidence and Discrepancies §D2) — no `Evidence.MEASURED` tag exists anywhere in the current table. |
| Job-to-be-done | Tune the app's coaching to a specific drill variant or coaching philosophy the built-in bands don't cover. |
| Closest competitor behaviour | No entry in `docs/tt-coach-ai-context.md:209-219`'s competitor table offers per-metric configurable coaching bands; closest analog is a human coach's own judgment, which this feature is explicitly modeled to substitute for (`docs/tt-coach-ai-context.md:78`). |
| Open L-numbers | L-35 (exercise editor fields not consumed by the live feedback analyzer for non-per-phase fields), L-40, L-48. |

---

## 5. Community drills (publish / rate / copy)

| Field | Value |
|---|---|
| What the player does | Publishes a custom drill to a public collection, browses/sorts others' drills, rates 1–5 stars, previews, and copies one into their own local drill list. |
| Status | **Shipped-but-gated** (gate: `firestore.rules` deploy status not confirmed; rating aggregates are client-trusted). |
| Code evidence | `CommunityDrillRepository.kt` (`app/src/main/java/com/ttcoachai/repository/CommunityDrillRepository.kt`) does the Firestore read/write directly from the client, no backend (`ratingSum`/`ratingCount` fields at lines 147-152,190-191). `CommunityDrillsActivity` (browse), `CommunityDrillDetailSheet` (preview/rate/copy), `DrillsFragment.kt:174` (publish/unshare, long-press menu). |
| Measured proof points | No measurement exists of publish counts, rating distribution, or copy rate — feature has no analytics instrumentation found. `firestore.rules` (repo root) is present and constrains field-set + star range (lines 1-40 read directly), but the file's own header states "MANUAL DEPLOYMENT REQUIRED... NOT auto-deployed by the build or CI/CD" (`firestore.rules:4-6`) — this analysis found no evidence in-repo of whether it is currently live in the Firebase console. |
| Job-to-be-done | Discover and reuse drill configurations other players have already tuned, instead of building bands from scratch. |
| Closest competitor behaviour | Spherely is described as having a "community app" component (`docs/tt-coach-ai-context.md:213`: "post-session highlight/replay/community app") — the closest direct analog found in the competitor list, though Spherely's community layer is around replay content, not tunable coaching drills. |
| Open L-numbers | L-40 (pre-editor-rework community drills surface only 2/7 bands). |

---

## 6. Session review / history / progress

| Field | Value |
|---|---|
| What the player does | Reviews a completed session's rep-by-rep breakdown, browses past sessions, and tracks progress over time. |
| Status | **Shipped.** `SessionReviewFragment`, `SessionHistoryFragment`, `ProgressFragment` all wired as reachable nav destinations: `navigation_session_review` (from Dashboard and from History, `nav_graph.xml:15,61`), `navigation_session_history` (`nav_graph.xml:55`), `navigation_progress` (bottom-nav tab, `nav_graph.xml:19`). |
| Code evidence | `app/src/main/java/com/ttcoachai/fragment/{SessionReviewFragment,SessionHistoryFragment,ProgressFragment}.kt` all present and reference in `nav_graph.xml`. `SessionAnalyticsEntity` (Room, `AppDatabase` entity list, `app/src/main/java/com/ttcoachai/db/AppDatabase.kt:12-22`, current schema **version 11** — see Discrepancies §D3). |
| Measured proof points | No measurement exists of session-review engagement (open rate, time spent, repeat visits). |
| Job-to-be-done | See how a session or a stretch of training actually went, beyond the live in-the-moment cues. |
| Closest competitor behaviour | `docs/tt-coach-ai-context.md:213`: Spherely's whole product is built around post-session review ("slow playback, zoom, replay") — this is the segment where TT_Coach_AI's session-review screens compete most directly, even though live coaching is the differentiator. |
| Open L-numbers | L-45 (2 of 4 declared phase-duration keys never produced), L-46 (per-phase ConsistencyRules dilute the session summary's flagged-rep count). |

---

## 7. Pose-data upload to Firebase (data flywheel)

| Field | Value |
|---|---|
| What the player does | Nothing explicit — every RTM training session's full per-frame pose stream is captured locally and uploaded to Firebase Storage in the background, consent-gated, default ON. |
| Status | **Shipped-but-gated** (gate: `storage.rules` **not yet deployed** — explicit release gate per the docs themselves). |
| Code evidence | `PoseJsonV2Writer` (`shared/src/commonMain/kotlin/com/ttcoachai/shared/io/`), `PoseSessionRecorder` (`app/src/main/java/com/ttcoachai/pose/PoseSessionRecorder.kt` — its own kdoc states the start/finish/abort lifecycle is a caller-serialized "non-atomic check-then-set," `PoseSessionRecorder.kt:31-33`; the actual `AtomicBoolean` CAS finalization guard `CLAUDE.md:236` describes lives in the caller, `RtmposeTrainingController.kt:173,440,448` — see Discrepancies §D4), `PoseUploadTask`/`PoseUploadWorker`/`PoseUploadQueue` (WorkManager). |
| Measured proof points | **Measured**, `docs/shipped-features.md:38-39`: 886 B/frame compact, 158 B/frame gzipped → 15 min @ 15 fps ≈ 12 MB raw / 2.1 MB gzipped. |
| Job-to-be-done | (Company-facing, not player-facing) accumulate a labeled pose dataset from real usage to improve future models/ranges — the player's job-to-be-done is implicit consent to a stated privacy tradeoff. |
| Closest competitor behaviour | Not covered in `docs/tt-coach-ai-context.md`'s competitor table — no competitor's data-collection practice is documented there; this is a company-strategy feature (labeled-data moat, `docs/tt-coach-ai-context.md:201-202` "Pose tech is NOT the moat... collected labeled data... are"), not a feature any competitor is compared against directly. |
| Open L-numbers | None directly registered in `docs/DESIGN_LIMITATIONS.md` against this feature; the outstanding blocker is the undeployed `storage.rules` file itself (`storage.rules:4-6`, `CLAUDE.md:231`). |

---

## 8. Backend picker (Lite/Full × GPU/CPU)

| Field | Value |
|---|---|
| What the player does | Chooses a pose-inference backend variant (MediaPipe Lite/Full model × GPU/CPU delegate) in Settings, trading off speed vs. thoroughness. |
| Status | **Shipped, user-facing.** `DetectionFragment` (`app/src/main/java/com/ttcoachai/fragment/DetectionFragment.kt`), reachable at Settings → Detection, **no debug gate on this fragment** — confirmed by reading the file: no `FLAG_DEBUGGABLE` check anywhere in `DetectionFragment.kt` (unlike `RtmposeDrillActivity`/`PoseBenchmarkActivity`, which explicitly self-guard). |
| Code evidence | `PoseBackendFactory.kt` (`app/src/main/java/com/ttcoachai/pose/PoseBackendFactory.kt`) — `enum PoseBackendVariant { MEDIAPIPE_LITE_GPU (default), MEDIAPIPE_LITE_CPU, MEDIAPIPE_FULL_GPU, MEDIAPIPE_FULL_CPU }`. `DetectionFragment.kt:40-59` wires all 4 buttons through `SettingsManager.setPoseBackendVariant`. All 3 live pose sites (`RtmposeTrainingController`, `RtmposeCalibrationActivity`, `RtmposeDrillActivity`) build through this one factory (`CLAUDE.md:107-108`). |
| Measured proof points | `docs/pose-backend-fps-benchmark.md:20-25` (same table as feature 1) — but that benchmark ran on the separate dev-only `PoseBenchmarkActivity`, not on this picker itself; no measurement exists of how players actually use the picker or whether switching improves their experience. |
| Job-to-be-done | Get usable frame rate on a slower/hotter phone, or the most accurate model on a capable one, without the app choosing silently. |
| Closest competitor behaviour | Not addressed in `docs/tt-coach-ai-context.md` — no competitor is documented as exposing an inference-backend picker to end users; this is an internal-engineering-surfaced-to-user feature, not a market differentiator. |
| Open L-numbers | L-17 (thermal throttling under capture+inference), L-18 (TFLite GPU delegate silently falls back to CPU). |

---

## Excluded candidates

None of the brief's 8 named candidates were dropped — all 8 above were verifiable against code and
are included. Frozen Stage 2 (ball tracking, ROI, trajectory, audio-contact) was excluded per the
brief's explicit instruction, not because it failed verification.

---

## Roadmap — post-session AI Coach report + chat, and subscription gating

### (a) Post-session AI Coach report + "Ask the coach" chat

| Field | Value |
|---|---|
| What the player does | After a session, reads an LLM-written coach report grounded in their own `PersonalBaseline`, and can ask it follow-up questions in a chat. Not built — this is the intended flow, not observed behaviour. |
| Status | **Roadmap-validated.** |
| Code evidence | None exists. `CLAUDE.md:15-31` states "VALIDATED 2026-07-22, NOT STARTED"; a repo-wide grep for `AICoach`/`AiCoach`/`Anthropic`/`OpenAI`/`chatbot` across `app/src` and `shared/src` found zero matches; no screen for it in `AndroidManifest.xml` or any `nav_graph.xml`. |
| Measured proof points | No measurement exists. |
| Job-to-be-done | Get expert-level, personalized coaching commentary on a whole session — not just the live per-rep cues — without paying for a human coach's time. |
| Closest competitor behaviour | `docs/tt-coach-ai-context.md:213`: Spherely (closest competitor) is "**NOT real-time — post-session highlight/replay/community app**." `docs/tt-coach-ai-context.md:212`: SwingVision is adjacent (tennis/pickleball match analytics, not form coaching). Neither doc entry describes an LLM chat feature specifically — this is the closest category match, not an exact one. (`CLAUDE.md:20-21` additionally names Sportsbox/Mustard/SpinCoach among rejected-direction competitors, but those three do not appear anywhere in `docs/tt-coach-ai-context.md`, so they are not cited here per the brief's "trace every number to a file" rule.) |
| Open L-numbers | None — no L-number in `docs/DESIGN_LIMITATIONS.md` references AI Coach (grepped, zero hits); the feature has no shipped code for a limitation to attach to. |

**Notes (carried over):**
- **Rejected direction — do not re-propose:** `CLAUDE.md:20-26` — real-time cloud-LLM feedback was explicitly rejected. No shipped competitor does it; a 1.5–5 s cloud round-trip vs. ~200 ms motor reaction lands cues 1–2 strokes late (negative transfer); per-user cost kills margin. Real-time stays on-device (feature 1 above). Payload for the post-session report, when built, is derived per-rep metrics + baseline (~KB/session), never raw poses (2–3M tokens/session — "economically impossible," `CLAUDE.md:23-24`).
- **Cost estimate (assumption, not measured):** `CLAUDE.md:19` — "$0.4–0.5/user/mo on Sonnet 5 with prompt caching." This figure does **not** appear in `pitch/unit_economics.py` itself; treat as a separate, unverified estimate, not a measured or modeled cost.
- **Prerequisites still absent (per `CLAUDE.md:15-31`):** a thin backend proxy holding the Anthropic API key (repo has **zero backend code** — confirmed, no `server/` directory or API-proxy code found anywhere in the tree); server-side entitlement (Firestore `isPremium` field exists on `UserProfile` but, per table (b) below, nothing reads it to gate); real Google Play Billing (currently mock, see table (b)).

### (b) Subscription gating

| Field | Value |
|---|---|
| What the player does | Taps "Start" on the Subscribe screen expecting to purchase premium access; nothing is actually charged. |
| Status | **Mock.** |
| Code evidence | `SubscribeActivity.kt` — `btnStart` click handler at `:118`, with the comment `// Mock purchase — no real billing integration.` at `:119` immediately inside it; the handler calls `settingsManager.setSubscriptionActive(true)` (a local SharedPreferences flag) and `finish()`. `btnRestore` click handler at `:127` shows an unconditional "nothing to restore" toast at `:128`. No Google Play Billing import anywhere in the file. |
| Measured proof points | No measurement exists. |
| Job-to-be-done | Pay to unlock premium features once they exist; today, tapping "Start" only flips a local flag with no real purchase. |
| Closest competitor behaviour | `docs/tt-coach-ai-context.md:212`: SwingVision is a real paid subscription, "~$150–180/yr." `docs/tt-coach-ai-context.md:214`: Spinsight is "€5–50/mo + €150 kit." Both give a pricing anchor for the category; neither doc entry describes those competitors' billing *implementation*, only their price. |
| Open L-numbers | None found in `docs/DESIGN_LIMITATIONS.md` referencing `SubscribeActivity` or subscription gating (grepped, zero hits). |

**Notes (carried over):**
- **Gating chain confirmed unused:** `UserProfile.isPremium()` (`app/src/main/java/com/ttcoachai/models/UserProfile.kt:50-54`) → `UserRepository.isPremium(uid)` (`UserRepository.kt:118-120`) → `CloudSyncManager.isPremium()` (`CloudSyncManager.kt:269-272`) — a 3-link call chain that calls only itself; no fragment, activity, or `shared/` code calls `cloudSyncManager.isPremium()` to gate any feature. Matches `CLAUDE.md:27` verbatim.
- **Prerequisites still absent (per `CLAUDE.md:15-31`):** real Google Play Billing (currently mock); server-side entitlement (Firestore `isPremium` field exists on `UserProfile` but, per above, nothing reads it to gate); a thin backend proxy holding the Anthropic API key (repo has **zero backend code**).

---

## Numbers usable in the deck (measured only)

| Value | Meaning | File:line |
|---|---|---|
| 34.6 fps (MediaPipe Full, GPU) | Live on-device pose-inference throughput, Samsung Galaxy S23, single run | `docs/pose-backend-fps-benchmark.md:20` |
| 34.4 fps (MediaPipe Lite, GPU) | Same benchmark, Lite model | `docs/pose-backend-fps-benchmark.md:21` |
| 29.5 fps (MediaPipe Lite, CPU) | Same benchmark, CPU delegate | `docs/pose-backend-fps-benchmark.md:22` |
| 28.0 fps (MediaPipe Full, CPU) | Same benchmark, CPU delegate | `docs/pose-backend-fps-benchmark.md:23` |
| 8.4 fps (RTMPose-lite, CPU) | Same benchmark, prior on-device pose backend (now replaced live, still used desktop-side) | `docs/pose-backend-fps-benchmark.md:24` |
| ~18–20 fps (MoveNet Thunder, CPU) | Prior measurement, NOT from the same benchmark run — explicitly caveated | `docs/pose-backend-fps-benchmark.md:25,31` |
| 886 B/frame compact, 158 B/frame gzipped | Pose-upload payload size per frame | `docs/shipped-features.md:38-39` |
| ≈12 MB raw / 2.1 MB gzipped per 15-min @15fps session | Derived from the per-frame size above | `docs/shipped-features.md:38-39` |
| 7 metric bands, 4 owner-corrected via measured coverage | `DrillMetrics.ALL_KEYS` size; band re-set history | `shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/DrillMetrics.kt:50-59`; `docs/DESIGN_LIMITATIONS.md:441-458` |
| `shoulder_angle@FOLLOWTHROUGH` measured 14.5–83.1°, median 71.9°, n=31 | Parity-fixture measurement backing the 55–85° seed band | `docs/DESIGN_LIMITATIONS.md:441-442` |
| `hip_flexion@CONTACT` measured 113.3–177.3°, median 121.3°, n=32 | Parity-fixture measurement backing the 115–150° seed band | `docs/DESIGN_LIMITATIONS.md:444-445` |
| `stroke_speed` live-measured 2.6–4.6 torso-lengths/s vs. shipped band mean 10.0 ± 0.3σ (16–24σ outside) | Evidence the withdrawn-clip-derived `stroke_speed` band is unreachable in real play (L-41) | `docs/DESIGN_LIMITATIONS.md:189-199` (heading `:189`) |
| `andrii_1`: 59.3 fps, 720×1280, 18,795 ms, 1106 frames, repCount=12, qualityScore=0.741 | One-clip baseline-derivation record — **do not use for technique claims**, clip withdrawn (L-49) | `docs/shipped-baseline-derivation.md:16-17,109-142` |
| Room `AppDatabase` schema version 11 | Current local-DB schema version (see Discrepancies §D3 — `CLAUDE.md` cites stale v3/v10) | `app/src/main/java/com/ttcoachai/db/AppDatabase.kt:22` |

### Assumptions, not measurements (never present as data)

| Value | Source | Line |
|---|---|---|
| `--arpu 8.0` USD/mo (default) | `pitch/unit_economics.py` | `:91` |
| `--churn 0.11` monthly (default) → lifetime ≈ 9.1 months | `pitch/unit_economics.py` | `:92` (derived `:34`) |
| `--store-fee 0.15`, `--ai-cost-share 0.05` → gross margin 80% | `pitch/unit_economics.py` | `:95-96` (derived `:38-40`) |
| `--cac-paid 70.0`, `--cac-blended 40.0` USD | `pitch/unit_economics.py` | `:100-101` |
| Derived: LTV ≈ $58.18, LTV:CAC paid ≈ 0.83×, blended ≈ 1.45× (below the 3× SaaS bar the script itself flags, `:134-136`) | `pitch/unit_economics.py` compute() | `:28-72` (hand-verified by re-running the formula, not the script) |
| "$12 ARPU + annual-plan churn ~8% → LTV:CAC 3.0×" — a **different, more optimistic scenario**, not the script's own default output | `CLAUDE.md` | `:17-19` |
| "$0.4–0.5/user/mo" Sonnet 5 AI-Coach COGS estimate — does not appear in `unit_economics.py` | `CLAUDE.md` | `:19` |
| Stage-1 beta gate "≥40% of beta completes ≥3 sessions" — explicitly called "a heuristic, not a validated benchmark" by the doc itself | `docs/tt-coach-ai-context.md` | `:229`(launch-plan gates section) |
| 2D joint-angle accuracy 1.4°–6.5° MAE / ~9° athletic-movement error — cited from external clinical literature (Lindera-v2, JMIR mHealth 2020), not measured on this app's own pipeline | `docs/tt-coach-ai-context.md` | `:77` |

---

## Discrepancies found

**D1 — "Training without calibration" mechanism is stale in `CLAUDE.md`.** `CLAUDE.md:113-152`
(dated 2026-07-29) and the Phase-0 orientation summary both describe standard-mode training as
using `ShippedBaselines.FOREHAND_ANDRII` as its σ-carrier. The code has since moved past this: L-43
(`docs/DESIGN_LIMITATIONS.md:138-163`, resolved 2026-08-16) records that both of `FOREHAND_ANDRII`'s
production roles were removed — `TrainingActivity.kt:161-188,269-282` now uses
`BandBaselineSynthesizer.fromBands` (severity) and `PerPhaseTargetsSeed.rangeBands()` (missing-extra
fallback) instead. A grep across `app/src` and `shared/src` for `ShippedBaselines.` confirms zero
production call sites remain (only `ShippedBaselines.kt` itself and test files). `CLAUDE.md`'s
committed Phase entry has not been updated to reflect this later, more-tested state.

**D2 — `PER_PHASE_RANGES` evidence grading is worse than `CLAUDE.md` implies.** Neither `CLAUDE.md`
nor the orientation doc mention that, as of the 2026-08-16 owner ruling recorded in
`docs/DESIGN_LIMITATIONS.md:189-236` (L-48), **all 10** (metric × phase) seed-band entries are now
`Evidence.COACH_OPINION` — the two previously `Evidence.MEASURED` `knee_bend` entries lost that tag
because their literature source (3D sagittal knee flexion) doesn't transfer to this pipeline's 2D
interior-angle computation. The editor's own "✓ = measured" legend string
(`R.string.exercise_editor_evidence_hint`) is consequently stale — nothing in the current table can
earn that checkmark. This is a materially weaker evidence story than "researched per-phase ranges"
(`CLAUDE.md`/orientation phrasing) suggests, and is a legitimate risk-report item.

**D3 — `AppDatabase` schema version cited inconsistently.** `CLAUDE.md:194,240` still say "v3"
(from the original `drill_configs` addition) and `CLAUDE.md:134` says "v10" (from the seeded-drills
work); the actual current schema in `app/src/main/java/com/ttcoachai/db/AppDatabase.kt:22` is
**version 11**. `fallbackToDestructiveMigration()` is still in effect either way (unverified in this
pass beyond the version number itself), so the substantive risk (schema bumps wipe local data) is
unaffected, but the specific version number in committed docs is stale.

**D4 — `PoseSessionRecorder.kt` does not itself contain the `AtomicBoolean` CAS `CLAUDE.md:236`
attributes to it.** Reading the file shows its own kdoc (`PoseSessionRecorder.kt:31-33`) describing
a **non-atomic** caller-serialized check-then-set contract for `start`/`finish`/`abort`. The actual
`AtomicBoolean` finalization-claim guard lives one layer up, in
`RtmposeTrainingController.kt:173,440,448` (`private val finalizationClaimed = AtomicBoolean(false)`).
The underlying claim in `CLAUDE.md` (finalization is race-guarded so `onDestroy`'s safety net can't
pre-empt a real save) is correct; the file it points to is not where the guard lives.

**D5 — `DesignSystemPreviewActivity` does not exist in the tree.** `CLAUDE.md:50` documents it as a
runnable debug-preview harness (`adb shell am start -n com.ttcoachai/.debug.DesignSystemPreviewActivity`).
A repo-wide search found zero files under any `debug/` package and only one other reference — a
comment in `RtmposeDrillActivity.kt:12` citing it as a prior-art "Slice pattern" — confirming it was
either never committed or was removed without the `CLAUDE.md` entry being updated. The Slice-1
design-system foundation work it was meant to preview (colors, type, `TTC.*` styles) is otherwise
present in resources; only this specific preview Activity is missing.

**D6 — Orientation doc's "MetricPrecisionPolicy" file name is slightly off.** The orientation
summary and `CLAUDE.md:223-224` both refer to "`MetricPrecisionPolicy`" as if it were a file; the
object of that name (`object MetricPrecisionPolicy`) is actually defined inside
`shared/src/commonMain/kotlin/com/ttcoachai/shared/drill/MetricPrecision.kt:9`. Cosmetic only — the
policy itself, and its allowlist semantics, were confirmed present and match the description.

**D7 — `L-36` is genuinely duplicated in `docs/DESIGN_LIMITATIONS.md`**, confirming the orientation
doc's flag: line 101 is "ms→frame window quantization sensitivity in `StrokeDetector2D`" (ACCEPTED,
mitigated by design) and line 721 is an unrelated "Baselines derived from full-fps export are
tighter than live pose noise" (ACCEPTED, mitigated not fixed). Referenced here as **L-36a**
(quantization, `:101`) and **L-36b** (full-fps-vs-live-noise, `:721`).

---

*Three-file close-out (per brief): `pitch/feature-inventory.md` (this file, commit pending at
write time — see task report); `.superpowers/sdd/2026-09-12-investor-feature-analysis/phase0-orientation.md`
(commit `3d1dfaf`, pre-existing); `.superpowers/sdd/2026-09-12-investor-feature-analysis/phase1-brief.md`
(commit `3d1dfaf`, pre-existing). No PDF was generated in this phase — this is a Markdown
deliverable, not a rendered document; page count: n/a.*
