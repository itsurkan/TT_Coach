---
name: run-on-phone
description: Use when you need to build, reinstall, and launch the TT_Coach Android app on the connected phone/device — "run the app on my phone", "reinstall and run", "install the debug build", "deploy to device", "launch the app on device". Not for taking screenshots (see phone-screenshot).
---

# Reinstall & run the app on the phone

Builds the debug APK, installs it on the connected Android device, and launches it.

## Commands

Run the one-step script from the project root:

```bash
# Build + install + launch (whole "reinstall and run" flow)
scripts/run_on_phone.sh

# Multiple devices attached → target one explicitly
scripts/run_on_phone.sh -s <serial>   # serial from `adb devices -l`
```

The script resolves the project root and `adb` location itself, checks that a device is
in the `device` state, builds with `./gradlew :app:assembleDebug`, then installs the
resulting APK via `adb install -r` and launches `com.ttcoachai/.MainActivity`. It fails
fast with a clear message if no device is attached or multiple devices need `-s`.

It is resilient to flaky wireless adb-tls: it waits for the device to settle
(`wait_for_device`, polls `adb get-state` up to 30 s) and retries the install up to 3×,
restarting the adb server between attempts. This is why it uses `adb install` rather than
`:app:installDebug` — gradle pushes via ddmlib, which dies with **"Broken pipe"** when the
wireless link hiccups mid-transfer.

Underlying steps (if you need to run them by hand):

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb shell am start -n com.ttcoachai/.MainActivity
```

## Facts (this project)

- **applicationId / namespace:** `com.ttcoachai`
- **Launcher activity:** `com.ttcoachai/.MainActivity`
- **Device:** Samsung Galaxy S23 (`SM-S911B`), connected wireless over adb-tls.
- **Build takes ~2 min** cold; usually faster incrementally. Set a generous timeout.

## Rules

- **Prefer the script** (`assembleDebug` + `adb install -r` with device-wait + retry)
  over a bare `./gradlew :app:installDebug` — installDebug pushes via ddmlib and dies
  with "Broken pipe" on flaky wireless. The script auto-picks the right ABI-split APK
  (`app-arm64-v8a-debug.apk`) via `ls -t`.
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
  `adb uninstall com.ttcoachai` then re-run the script. Note this wipes local Room
  data (the DB uses `fallbackToDestructiveMigration`).
- **"Broken pipe" / device drops mid-install** → transient wireless adb-tls hiccup. The
  script already waits + retries 3×; if it still fails, re-pair (`adb connect <ip>:<port>`)
  or switch to USB.
