---
name: Ivan — solo developer on TT_Coach
description: Profile of the user — Kotlin/Android engineer building TT_Coach as a personal product. Native Ukrainian speaker, works on macOS.
type: user
originSessionId: 51fe4f63-cc7e-4a09-84eb-927adff4f560
---
**Name:** Ivan Tsurkan (itsurkan.1@gmail.com)

**Role:** Solo developer on TT_Coach — a personal product (table tennis coaching app), not employer code. Decides scope, timeline, and tradeoffs himself.

**Technical background:**
- Primary stack on this project: Kotlin 2.1.0, KMP (shared module + Android app), MediaPipe, CameraX, OpenCV, Room, Firebase
- Uses Android Studio (bundled JDK 21) + VSCode with Claude Code extension
- Already fluent with speckit workflow (`specs/###-feature/` structure with spec.md + plan.md + tasks.md)

**Environment:**
- macOS (Darwin arm64, 24.5.0)
- System Java: Temurin 25 (too new for Gradle 8.14.3). Temurin 21 installed at `~/jdks/jdk-21.0.10+7/Contents/Home` (tarball, not brew cask — sudo install failed due to non-interactive shell). Export `JAVA_HOME` there before running `./gradlew`
- Homebrew installed; empty macOS login password (but `brew install --cask` sudo prompt still needs an interactive terminal — run cask installs in Terminal.app, not via Claude's shell)

**Language:** Native Ukrainian. Comfortable in English for technical terms. Typically starts in English then switches to Ukrainian for conceptual/strategic discussion. Casual tone, tolerant of typos in both directions — don't correct grammar.

**Goals:**
- Ship TT_Coach as a product with real users
- Views Claude Code as a significant productivity multiplier (expects 2-10× speedup on scaffolding work)
- Aggressive timelines (2-week Play Store goal for Stage 1) — willing to cut scope to hit them

**Collaboration style:**
- Responds well to structured multi-question alignment (AskUserQuestion tool for decision points)
- Accepts "Recommended" defaults most of the time; overrides when he has stronger vision
- Open to pushback when it's reasoned and honest
