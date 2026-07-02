---
name: run-on-phone
description: Use when you need to build, reinstall, and launch the TT_Coach Android app on the connected phone/device — "run the app on my phone", "reinstall and run", "install the debug build", "deploy to device", "launch the app on device". Not for taking screenshots (see phone-screenshot).
---

# Reinstall & run the app on the phone

Builds the debug APK, installs it on the connected Android device, and launches it.

## Commands

```bash
# 1. Build + install the debug APK (JDK 21 pin lives in ~/.gradle/gradle.properties)
./gradlew :app:installDebug

# 2. Launch the launcher activity
adb shell am start -n com.ttcoachai/.MainActivity
```

That's the whole "reinstall and run" flow. `installDebug` both compiles and pushes the
APK, so no separate `adb install` is needed. Run from the project root.

## Facts (this project)

- **applicationId / namespace:** `com.ttcoachai`
- **Launcher activity:** `com.ttcoachai/.MainActivity`
- **Device:** Samsung Galaxy S23 (`SM-S911B`), connected wireless over adb-tls.
- **Build takes ~2 min** cold; usually faster incrementally. Set a generous timeout.

## Rules

- **Always `./gradlew :app:installDebug`**, not `assembleDebug` + manual install — one
  step, and it targets the right ABI split (`app-arm64-v8a-debug.apk`) automatically.
- The `source/target version 8` deprecation warning during `compileDebugJavaWithJavac`
  is expected and harmless — do not "fix" it.
- After launching, confirm visually with the **phone-screenshot** skill if the user
  wants to see the result.

## If it fails

- **`adb devices` shows nothing / `offline` / `unauthorized`** → phone not connected or
  USB-debugging prompt not accepted. Reconnect (USB or `adb connect <ip>`), accept the
  prompt on the phone.
- **Multiple devices** → add `-s <serial>` to the `adb` command (serial from
  `adb devices -l`), e.g. `adb -s adb-RFCWB0AZP2D-1oe5GR shell am start -n com.ttcoachai/.MainActivity`.
- **Gradle JDK error** (Gradle 8.14 needs JDK 21, this Mac defaults to Java 25) → the pin
  `org.gradle.java.home` must be set in `~/.gradle/gradle.properties`. It's machine-local
  and gitignored; without it the build fails.
- **`adb` not on PATH** → it's at `~/Library/Android/sdk/platform-tools/adb`.
- **INSTALL_FAILED_UPDATE_INCOMPATIBLE** (signature mismatch after a release build) →
  `adb uninstall com.ttcoachai` then re-run `installDebug`. Note this wipes local Room
  data (the DB uses `fallbackToDestructiveMigration`).
