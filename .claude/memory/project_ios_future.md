---
name: iOS app planned for future
description: TT_Coach will add an iPhone app after Android; plan KMP-portable code now to avoid Android-only lock-in
type: project
originSessionId: ea365c7b-777c-4066-b325-f3357f85a922
---
An iOS (iPhone) app is planned as a future target after the Android app stabilizes. No timeline yet (as of 2026-04-18), but it is a firm direction, not a maybe.

**Why:**
- Broader user reach — many table-tennis players are on iOS
- Ivan wants a single codebase strategy (KMP) so pose/ball/rule logic doesn't fork

**How to apply:**
- When designing new modules, default to `shared/commonMain` (pure Kotlin) — only drop into `app/` (Android) when the code genuinely needs Android APIs (CameraX, MediaPipe Android binding, Room, Firebase Android, TFLite Android)
- Avoid `kotlin.time.Duration` or other JDK-leaking types on serialization/persistence boundaries (see existing `PersonalBaseline.phaseDurationsMs` decision using millis + string keys as the right pattern)
- Persistence: Room is Android-only; when designing data models, keep the pure data class in `commonMain` and let Room entities wrap them in `app/` — so iOS can swap in SQLDelight/CoreData later without touching domain types
- MediaPipe: Android SDK ≠ iOS SDK — any pose-processing contract that touches MediaPipe types must go through a `commonMain` interface with an Android `actual` implementation, so iOS can bind its own
- When reviewing plans (e.g. `speckit.plan`), flag any design that buries non-UI logic in `app/` — ask whether it can live in `shared/` for iOS reuse
- UI itself (Compose vs SwiftUI) will fork — don't try to share UI yet; share everything below it
