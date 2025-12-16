# Building and Running on Windows

## Prerequisites

1. **Android Studio** - Download from [developer.android.com/studio](https://developer.android.com/studio)
2. **Java JDK 17** - Usually comes with Android Studio
3. **Android SDK** - Installed via Android Studio

## Quick Start (Using Android Studio - Recommended)

1. **Open Project:**
   - Launch Android Studio
   - Click `File > Open`
   - Navigate to `D:\Desktop\Test\TT_Coach`
   - Click OK

2. **Sync Gradle:**
   - Android Studio will automatically sync Gradle files
   - Wait for "Gradle sync finished" message

3. **Build Project:**
   - Press `Ctrl+F9` or click `Build > Make Project`
   - Check for errors in the Build window

4. **Run on Device/Emulator:**
   - Connect an Android device via USB (enable USB debugging)
   - OR create an Android Virtual Device (AVD):
     - `Tools > Device Manager > Create Device`
   - Click the green ▶️ Run button or press `Shift+F10`
   - Select your device/emulator

## Command Line Build (PowerShell)

### 1. Set Android SDK Path

Find your Android SDK location:
- Open Android Studio
- `File > Settings > Appearance & Behavior > System Settings > Android SDK`
- Copy the "Android SDK Location" path

Update `local.properties`:
```powershell
# Example (update with your actual path):
sdk.dir=C:/Users/YourUsername/AppData/Local/Android/Sdk
```

### 2. Build Commands

```powershell
# Navigate to project
cd D:\Desktop\Test\TT_Coach

# Build debug APK
.\gradlew.bat assembleDebug

# Install on connected device
.\gradlew.bat installDebug

# Run tests
.\gradlew.bat test
```

### 3. Common Issues

**Issue: "SDK location not found"**
- Solution: Create/update `local.properties` with correct SDK path
- Use forward slashes `/` or escaped backslashes `\\` in the path

**Issue: "Gradle wrapper not found"**
- Solution: The `gradle-wrapper.jar` should be in `gradle\wrapper\` folder
- If missing, Android Studio will download it automatically

**Issue: "Java not found"**
- Solution: Install JDK 17 and set JAVA_HOME environment variable
- Or use Android Studio's bundled JDK

## Project Structure (Windows Paths)

```
D:\Desktop\Test\TT_Coach\
├── app\
│   ├── build.gradle.kts          # App dependencies
│   └── src\main\
│       ├── java\com\ttcoach\     # Kotlin source code
│       ├── res\                   # Resources (strings, layouts)
│       └── AndroidManifest.xml   # App configuration
├── build.gradle.kts               # Root build file
├── settings.gradle.kts            # Project settings
├── gradlew.bat                    # Gradle wrapper (Windows)
└── local.properties               # SDK location (create this)
```

## Main Entry Point

- **Main Class:** `app\src\main\java\com\ttcoach\MainActivity.kt`
- **Manifest:** `app\src\main\AndroidManifest.xml` (declares MainActivity as launcher)

## Next Steps

After building successfully:
1. Implement camera integration (Week 1)
2. Add MediaPipe Pose tracking
3. Build the UI with Jetpack Compose

