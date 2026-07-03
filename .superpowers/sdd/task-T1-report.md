# Task T1 report — RtmposeMath.kt Kotlin port

## Status: DONE

## Files added (new files only, no existing files modified)
- `app/src/test/java/com/ttcoachai/pose/RtmposeMathTest.kt` — 13 tests, ported 1:1 from
  `iosApp/TTCoach/Pose/RTMPoseMathTests.swift`.
- `app/src/main/java/com/ttcoachai/pose/RtmposeMath.kt` — `object RtmposeMath`, ported 1:1 from
  `iosApp/TTCoach/Pose/RTMPoseMath.swift`.

## TDD sequence
1. Read `RTMPoseMath.swift`, `RTMPoseMathTests.swift`, `RTMPOSE_PARITY.md`.
2. Wrote the test file first (mirroring every `func test...`).
3. Ran `./gradlew :app:testDebugUnitTest --tests '*RtmposeMathTest*'` — failed to compile
   (Unresolved reference `RtmposeMath`/`Vec2`), confirming red state (no implementation existed).
4. Implemented `RtmposeMath.kt`.
5. Reran — BUILD SUCCESSFUL, 13/13 tests green, 0 failures/errors.

## Test list (13, all green)
1. `testLetterboxRatioAndSize`
2. `testBboxXyxyToCenterScale`
3. `testTopDownAffineScaleFix`
4. `testAffineFromPointPairs`
5. `testGetWarpMatrix`
6. `testGetWarpMatrixRotated`
7. `testGetWarpMatrixInverseRoundTrips`
8. `testSimccMaximumSmall`
9. `testSimccMaximumK17OneHot`
10. `testSimccMaximumTieTakesFirstIndex`
11. `testSimccMaximumAllZeroIsExcluded`
12. `testDecodeKeypoints`
13. `testConstants`

## Commands run
- `./gradlew :app:testDebugUnitTest --tests '*RtmposeMathTest*'` (red, then green)
- Verified via `app/build/test-results/.../TEST-com.ttcoachai.pose.RtmposeMathTest.xml`:
  `tests="13" skipped="0" failures="0" errors="0"`

## Swift → Kotlin translation decisions
- `SIMD2<Float>` → local `data class Vec2(val x: Float, val y: Float)`, per the brief.
- `simd_float2x3` (the parity-math's custom column encoding: columns 0/1 hold row-x/row-y
  `[a,b,tx]`/`[c,d,ty]`) → local `data class Affine2x3(a, b, tx, c, d, ty)` storing the same six
  scalars directly by name — simpler than replicating SIMD's column-major layout in Kotlin, and
  `affine2x3Flat`/`affine2x3Apply` produce numerically identical results.
- `affineFromPointPairs`: Swift solves via `simd_float3x3.inverse` (matrix inverse) then
  matrix-vector multiply. Kotlin has no SIMD/matrix library available (zero-deps app module
  scope for this task), so I hand-expanded the 3x3 inverse via the adjugate/determinant formula
  and applied it to `dstX`/`dstY` columns exactly as the Swift does (`mx = inv * dstX`,
  `my = inv * dstY`). Verified numerically against the oracle values in
  `testAffineFromPointPairs` and `testGetWarpMatrix*`.
- `Int(x)` on a positive Float (truncation) → Kotlin `.toInt()`, which also truncates toward
  zero — used in `letterboxResizedSize`.
- `simd`'s `SIMD2<Float> / Float` scalar division and `+`/`-` operators → written out as explicit
  per-component arithmetic in Kotlin (no operator overloads added on `Vec2`, kept minimal per
  brief's "new files only" scope).
- BGR mean/std constants, `simccSplitRatio = 2.0f`, bbox padding `1.25f`, top-down affine scale
  `0.75` (derived from 192/256), detector score threshold `0.3f`, pose input W=192 H=256,
  detector input 640 — all preserved verbatim as named constants.
- `kotlin.math.sin`/`cos`/`PI` used (not `java.lang.Math`), per brief.

## Concerns
- None. All 13 ported cases pass within the same `1e-3` tolerance used in the Swift original.
  No existing files were touched. No new dependencies added — the 3x3 inverse was hand-rolled
  rather than pulling in a matrix library, keeping this a pure-Kotlin, zero-dependency addition
  consistent with the module's existing constraints.
