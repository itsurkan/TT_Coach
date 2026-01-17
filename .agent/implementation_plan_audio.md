# Audio Feedback Implementation Plan

## Goal
Enable "tic" (start of stroke) and "tac" (contact point) audio feedback in the TT Coach Android app, ensuring it works on the user's Windows development environment and Android device.

## Current State
- Audio files (`tic.m4a`, `tac.m4a`) are in `app/src/main/res/raw/`.
- `SoundPool` implementation is in `FeedbackGenerator.kt` with async loading.
- `ToneGenerator` fallback added for robustness.
- Permissions `MODIFY_AUDIO_SETTINGS` added.
- Build is passing.

## Remaining Steps
1.  **Verification of "Tic/Tac" Logic**: Ensure the `PoseAnalysisProcessor` calls `playTic` and `playTac` at the correct `StrokePhase`.
2.  **Windows-Compatible Commands**: Use `gradlew.bat` instead of `./gradlew` for all future terminal operations to ensure compatibility with Windows Command Prompt / PowerShell.
3.  **Clean Install**: Perform a clean uninstall/reinstall cycle to ensure all permissions and resources are correctly propagated to the device.
4.  **User Verification Guide**: Provide a checklist for the user to verify the sound on their device.

## Detailed Tasks
- [x] Fix missing variables in `FeedbackGenerator.kt` (Done)
- [x] Add Manifest permissions (Done)
- [ ] Verify `PoseAnalysisProcessor.kt` logic for sound triggers.
- [ ] Run `gradlew.bat installDebug` (using Windows syntax).
- [ ] Verify Logcat for "Sound loaded" messages.

## User Action Required
- Connect Android device.
- Approve the installation command.
- Test a forehand drive stroke and listen for sounds.
