---
name: fixture-pipeline
description: Use when adding new training footage to the test suite — turning a video into a shared-KMP test fixture, adding *_rtm.json to shared/src/commonTest/resources/fixtures/, or wiring fixture-driven jvmTest coverage for drill logic.
---

# Fixture pipeline: footage → RTMPose export → KMP fixture → tests → visual QA

The core Phase 2 loop. Order matters: visual QA before tests trust the data.

## Steps

1. **Export** — see the `rtmpose-export` skill:
   `.venv/bin/python scripts/poses/export_poses_rtmpose.py Videos/<base>/<base>.mp4`

2. **Visual QA first** — see the `viewer-qa` skill. A broken export (wrong person tracked, garbage frames) silently poisons every downstream test.

3. **Copy into fixtures** (naming convention: `<base>_rtm.json`):
   ```bash
   cp Videos/<base>/<base>_poses_rtm.json shared/src/commonTest/resources/fixtures/<base>_rtm.json
   ```

4. **Load via the v2 loader** — add a `load<Base>Rtm()` method to
   `shared/src/jvmTest/kotlin/com/ttcoachai/shared/TestFixturesV2.kt`, which goes through `PoseJsonV2Parser`
   (`shared/src/commonMain/kotlin/com/ttcoachai/shared/io/PoseJsonV2Parser.kt`).
   - **NEVER load v2 fixtures through legacy `TestFixtures`/`JsonTestUtils`** — those parse the v1 MediaPipe-33 `x,y,z,visibility` schema and will fail or silently mis-read.
   - If `TestFixturesV2` doesn't exist yet, create it per Task 3 of [the Phase 2 plan](../../../docs/superpowers/plans/2026-06-10-phase2-drill-logic-shared-kmp.md).
   - Fixture loaders live in `jvmTest`, not `commonTest` (ClassLoader is JVM-only).

5. **Run tests**:
   ```bash
   ./gradlew :shared:jvmTest                                    # full shared suite
   ./gradlew :shared:jvmTest --tests "<fully.qualified.Class>"  # one class
   ```
   commonTest classes execute on JVM via this same task.

## Common mistakes

| Mistake | Reality |
|---------|---------|
| Registering v2 JSON in legacy `TestFixtures.kt` | v1 parser — wrong schema; use `TestFixturesV2` + `PoseJsonV2Parser` |
| Skipping viewer QA before writing tests | Tests then pin garbage data as "expected" |
| Fixture loader in commonTest | No ClassLoader there — jvmTest only |
| Asserting coords strictly in `[0,1]` | RTMPose can place off-frame joints slightly outside; allow margin, keep `score` strict |
