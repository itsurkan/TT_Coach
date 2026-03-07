# 🐛 Video Debug Testing Guide

## Overview
The Video Debug system allows you to test PoseAnalysisProcessor without connecting to a phone's camera. Load test videos, see pose tracking in real-time, and validate stroke detection against expected counts.

## 🎯 Key Features

### 1. **Split-Screen Interface**
- **Left Panel**: Video player with pose overlay (skeleton visualization)
- **Right Panel**: Live analysis metrics showing:
  - Current frame number and phase
  - Technical parameters (wrist angle, body rotation, etc.)
  - Score (0-100%) with color coding
  - Real-time feedback
  - Session summary (total strokes, accuracy, phase distribution)

### 2. **Playback Controls**
- **Play/Pause**: Start/stop video playback
- **Frame Stepping**: Move forward/backward one frame at a time
- **Speed Control**: 0.25x, 0.5x, 1x, 2x playback speeds
- **Frame Scrubber**: Seek to any frame in the video
- **Reset**: Clear analysis and start over

### 3. **Stroke Detection**
- Automatic phase detection based on wrist velocity:
  - `READY` → `BACKSWING` → `FORWARD_SWING` → `CONTACT` → `FOLLOW_THROUGH` → `RECOVERY`
- Phase transitions logged in console
- Velocity thresholds:
  - Backswing: < -0.015 m/s
  - Forward swing: > 0.02 m/s
  - Contact: Peak velocity
  - Recovery: Near-zero velocity

### 4. **Real-Time Metrics**
Each frame displays:
- ✅ ✗ Validation indicators for each parameter
- Color-coded scores (green: 80%+, orange: 60-79%, red: <60%)
- Phase name and frame number
- Technical measurements with 2 decimal precision

## 📂 How to Use

### Step 1: Launch Debug Activity
1. Open the app
2. On Welcome screen, tap **"🐛 Debug Video"** button (orange)
3. Debug Activity opens in landscape mode

### Step 2: Load Test Video
- Default video (`forehand_drive.mp4`) loads automatically
- To change video: Tap **"📂 Load Video"** button
- Options:
  - `forehand_drive.mp4` (Default)
  - Choose from gallery (TODO)

### Step 3: Analyze Video
1. Video processes automatically (may take 10-30 seconds)
2. Progress bar shows during processing
3. When complete:
   - Analysis panel appears on right
   - Summary statistics update
   - Frame scrubber becomes active

### Step 4: Inspect Results
- **Play through video**: Watch real-time analysis
- **Step frame-by-frame**: Use ⏮/⏭ buttons to inspect specific frames
- **Check specific moments**: Use scrubber to jump to frame of interest
- **Verify stroke count**: Check "Strokes Detected" in summary panel
- **Review phase distribution**: See how many frames spent in each phase

### Step 5: Validate Detection
Compare detected strokes vs. expected:
```
Expected: 5 strokes in video
Detected: [Check "Strokes Detected" value]
```

If counts don't match:
1. Review phase transitions in logcat:
   ```
   Phase transition: READY -> BACKSWING (velocity: -0.018)
   ```
2. Adjust thresholds in `PoseAnalysisProcessor.kt`
3. Rebuild and retest

## 🔧 Debugging Workflow

### Finding Issues with Stroke Detection

**Problem: Strokes not detected**
1. Check logcat for phase transitions:
   ```powershell
   adb logcat | Select-String "PoseAnalysisProcessor"
   ```
2. Look for velocity values - are they reaching thresholds?
3. If velocities are too small:
   - Lower `VELOCITY_THRESHOLD` in `PoseAnalysisProcessor.kt`
   - Lower `BACKSWING_VELOCITY_THRESHOLD`

**Problem: Too many false positives**
1. Increase `MIN_PHASE_FRAMES` (currently 3)
2. Increase velocity thresholds
3. Add additional validation (e.g., elbow position)

**Problem: Phase detection wrong**
1. Use frame stepping to find exact problematic frame
2. Check wrist Z-coordinate in logs
3. Verify velocity calculation logic
4. Test with slower playback (0.25x) to see transitions

### Collecting Data for Analysis

All analysis is automatically logged to:
```
Download/TT_Coach_AI/logs/
├── sessions.jsonl
├── strokes.jsonl
└── raw_poses.jsonl
```

Export logs after test:
```powershell
cd d:\Desktop\TT_Coach_AI
.\scripts\quick_export.ps1
```

Analyze with Python:
```powershell
python scripts/analyze_poses.py logs_export_TIMESTAMP/
```

## 📊 Understanding Metrics

### Technical Parameters (Right Panel)

**Wrist Angle** (170-190° ideal for forehand)
- Measures elbow-wrist-index finger angle
- ✓ = within tolerance (±10°)
- ✗ = outside tolerance

**Body Rotation** (45°+ ideal)
- Difference between shoulder and hip angles
- Indicates proper torso engagement
- ✓ = sufficient rotation
- ✗ = insufficient rotation

**Follow-Through** (100-140° ideal)
- Shoulder-elbow-wrist angle during follow-through
- Indicates complete stroke motion
- ✓ = proper follow-through
- ✗ = abbreviated motion

**Contact Height** (0.7-1.1 relative)
- Wrist height relative to floor
- 1.0 = hip height (ideal for forehand)
- ✓ = within range
- ✗ = too high or too low

**Elbow Distance** (<0.3m ideal)
- Distance from elbow to body center
- Ensures compact technique
- ✓ = close to body
- ✗ = too far from body

### Session Summary

**Total Frames**: Number of frames processed
**Strokes Detected**: Count of complete stroke cycles (READY → ... → RECOVERY → READY)
**Avg Score**: Average overall score across all frames (0-100%)
**Good Strokes**: Number of strokes with score ≥80%
**Success Rate**: Percentage of good strokes

**Phase Distribution**: How many frames spent in each phase
```
READY: 45 frames
BACKSWING: 12 frames
FORWARD_SWING: 8 frames
CONTACT: 5 frames
FOLLOW_THROUGH: 10 frames
RECOVERY: 15 frames
```

## 🎬 Creating Test Videos

### Recording Guidelines
1. **Camera setup**: Side view, 3-4 meters from table
2. **Lighting**: Bright, even lighting (MediaPipe needs good visibility)
3. **Framing**: Full body visible (waist to head minimum)
4. **Duration**: 5-15 seconds per video
5. **Content**: Consistent stroke repetitions (5-10 strokes)

### Recommended Test Videos
1. **Perfect technique** (5 strokes) - Expected: 5 detected, 100% accuracy
2. **Good technique** (10 strokes) - Expected: 10 detected, 80%+ accuracy
3. **Poor technique** (5 strokes) - Expected: 5 detected, <60% accuracy
4. **Mixed technique** (10 strokes, varied) - Expected: 10 detected, 60-80% accuracy
5. **Edge cases** (fast, slow, incomplete strokes) - Stress test detection

### Adding Videos to App
1. Place `.mp4` file in `app/src/main/res/raw/`
2. Name: lowercase, no spaces (e.g., `test_forehand_5strokes.mp4`)
3. Update `DebugActivity.showVideoSelectionDialog()` to include new video
4. Rebuild app

## 🧪 Testing Checklist

Before considering stroke detection "working":

- [ ] Correct stroke count on perfect technique video
- [ ] No false positives during ready position
- [ ] Phases transition in correct order (READY → BACKSWING → ... → RECOVERY)
- [ ] Phase timing looks reasonable (not too fast/slow)
- [ ] Good technique scores 80%+ consistently
- [ ] Poor technique scores <60% consistently
- [ ] Edge cases don't crash the app
- [ ] Logcat shows smooth velocity transitions
- [ ] Exported logs contain complete data
- [ ] Frame stepping shows smooth overlay movement

## 📝 Known Limitations (Current Implementation)

1. **No automatic stroke counting yet** - Phase detection exists but stroke boundaries not tracked
2. **Fixed FPS assumption** - Assumes 30 FPS, may not match actual video framerate
3. **Z-coordinate only** - Only uses wrist Z for phase detection (could add X, Y)
4. **No stroke boundary events** - Doesn't emit "stroke started" / "stroke ended" events
5. **Gallery picker not implemented** - Only default video works currently
6. **No video export** - Can't save annotated video with analysis overlay

## 🔮 Future Enhancements

1. **Stroke counter** - Track complete stroke cycles (READY → RECOVERY → READY)
2. **Ground truth comparison** - Load JSON with expected strokes, show diff
3. **Multi-video batch test** - Process multiple videos, generate report
4. **Export annotated video** - Save video with pose overlay and metrics
5. **Advanced phase detection** - Use ML classifier instead of rule-based
6. **Side-by-side comparison** - Compare current vs. ideal technique
7. **Slow-motion analysis** - Automatic slow-motion during key phases (contact)
8. **Velocity graph** - Plot wrist velocity over time

## 💡 Tips & Tricks

- **Laptop testing**: Use Android emulator with uploaded video file
- **Quick iteration**: Keep DebugActivity open, just tap "🔄 Reset Analysis"
- **Fine-tuning thresholds**: Edit `PoseAnalysisProcessor.kt`, instant build with Gradle
- **Comparing videos**: Export logs from each test, use Python script to compare
- **Sharing results**: Screenshot analysis panel, paste into issue tracker

## 🆘 Troubleshooting

**Video won't load**
- Check file exists in `res/raw/`
- Verify filename matches (no uppercase, no spaces)
- Check logcat for MediaMetadataRetriever errors

**No pose overlay visible**
- Video may have poor lighting/visibility
- Check MediaPipe confidence thresholds in `VideoDebugProcessor`
- Try with better quality video

**Wrong stroke count**
- See "Debugging Workflow" section above
- Check phase transition logs
- Adjust velocity thresholds

**App crashes during processing**
- Large video file may cause OutOfMemoryError
- Use shorter videos (<30 seconds)
- Lower resolution if needed

**Laggy playback**
- Use slower playback speed (0.5x or 0.25x)
- Step frame-by-frame instead
- Device may be underpowered - use higher-end phone

## 📧 Support

If stroke detection still doesn't work after tuning:
1. Export logs: `.\scripts\quick_export.ps1`
2. Share log folder + test video
3. Include expected vs actual stroke counts
4. Describe what you observed vs. expected behavior

---

**Happy Debugging! 🏓**
