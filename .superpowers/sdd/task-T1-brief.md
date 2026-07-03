# Task T1 — RtmposeMath.kt (Kotlin port of iOS RTMPoseMath.swift)

## Goal
Create a pure-Kotlin `RtmposeMath` object in the Android `app/` module that is a faithful 1:1 port
of the iOS `RTMPoseMath.swift`, proven by porting `RTMPoseMathTests.swift` verbatim (same numeric
expectations). No Android framework, no ONNX — pure math, plain JUnit.

## TDD (test first, then impl, then green)
1. Write `app/src/test/java/com/ttcoachai/pose/RtmposeMathTest.kt` porting EVERY case from
   `/Users/itsurkan/Dev/personal/TT_Coach/iosApp/TTCoach/Pose/RTMPoseMathTests.swift` — same inputs,
   same expected numbers, same tolerances. Read that Swift test file and mirror each `func test...`.
2. Implement `app/src/main/java/com/ttcoachai/pose/RtmposeMath.kt` as a 1:1 port of
   `/Users/itsurkan/Dev/personal/TT_Coach/iosApp/TTCoach/Pose/RTMPoseMath.swift`.

## Source of truth (READ THESE FIRST)
- `iosApp/TTCoach/Pose/RTMPoseMath.swift` — the implementation to port.
- `iosApp/TTCoach/Pose/RTMPoseMathTests.swift` — the tests to port.
- `iosApp/TTCoach/Pose/RTMPOSE_PARITY.md` — parity constants and rationale (read for the exact numbers).

## Porting rules (CRITICAL — these are parity-load-bearing)
- Replace `SIMD2<Float>` with a small local `data class Vec2(val x: Float, val y: Float)` (or Pair<Float,Float>);
  keep the same semantics. Prefer a `Vec2` for readability.
- Preserve EXACTLY: BGR channel order, `meanBGR`/`stdBGR` constants, `simccSplitRatio = 2.0f`,
  bbox padding `1.25`, top-down affine scale `0.75`, detector score threshold `0.3`,
  pose input W=192 H=256, detector input 640.
- Preserve truncation semantics: where Swift uses `Int(x)` on a positive float, use `x.toInt()`
  (truncates toward zero). Do NOT round unless Swift rounds.
- Functions to port (match Swift names, adapt to Kotlin camelCase if Swift already is): letterbox
  ratio/resized size, `bboxXyxyToCenterScale`, `fixAspectRatio`, `topDownAffineScale`,
  `affineFromPointPairs`, `affine2x3Apply`/`affine2x3ApplyFlat`, `rotatePoint`, `thirdPoint`,
  `getWarpMatrix(inverse: Bool)`, `simccMaximum` (val<=0 → (-1,-1)), `decodeKeypoints`.
- Use `kotlin.math` (NOT java.lang.Math). Constants as `const val` where possible.

## Constraints
- New files ONLY. Do not modify any existing file. Package `com.ttcoachai.pose`.
- Do not touch frozen files. Do not add dependencies (this task is pure Kotlin + JUnit, which app already has).

## Verify
`./gradlew :app:testDebugUnitTest --tests '*RtmposeMathTest*'` — all green. Commit with an explicit
path `git add` (never `git add -A`).

## Report
Write your full report to `.superpowers/sdd/task-T1-report.md` (test list, commands run + output,
any Swift↔Kotlin translation decisions, concerns). Return only: status, commit range, one-line test
summary, concerns.
