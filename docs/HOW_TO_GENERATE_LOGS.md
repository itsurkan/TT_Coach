# 🎯 How to Generate Stroke Analysis Logs

## Current Situation

**Files found on device:**
- ✅ `sessions.jsonl` (209 bytes) - training session start/end
- ✅ `events.jsonl` (88 bytes) - app launched event  
- ❌ `strokes.jsonl` - **NOT CREATED** (file doesn't exist)

**Why `strokes.jsonl` doesn't exist?**
- The app was launched ✅
- TrainingActivity was opened ✅
- **BUT training was NOT started** ❌
- Without active training, pose detection doesn't run
- No pose results → no stroke analysis → no `strokes.jsonl`

---

## ✅ Step-by-Step: How to Generate Logs

### 1. Launch the App on Android Device
```
📱 Open "AI Coach - Настільний теніс"
```

### 2. Start a Training Session
```
Main Screen → "Почати тренування"
   ↓
Exercise Selection → Select "Накат справа"
   ↓
Training Screen (TrainingActivity)
```

### 3. **IMPORTANT: Start Training!** ⚠️
```
1. Click "Калібрувати позицію" (optional, 3 sec)
2. Click "Почати тренування" ✅ ← THIS IS CRITICAL!
3. Click "Відкрити камеру" to enable pose detection
```

**Without clicking "Start Training", pose detection won't run!**

### 4. Perform Some Movements
```
🏓 Make 10-20 forehand stroke movements in front of camera
⏱️ Train for at least 30 seconds
📹 Ensure you're visible in camera frame
```

### 5. Stop Training
```
Click "Зупинити тренування"
```

### 6. Export Logs
```powershell
cd d:\Desktop\TT_Coach_AI
.\scripts\quick_export.ps1
```

**Expected result:**
```
✅ sessions.jsonl (>200 bytes)
✅ events.jsonl (~100 bytes)  
✅ strokes.jsonl (should be LARGE! 1KB+ with real data) ← THIS!
✅ metrics.jsonl (optional - performance metrics)
```

---

## 🔍 Verify Logs Are Being Created

### Real-time Check (while training):
```powershell
# Watch logcat for pose analysis
adb logcat -s PoseAnalysisProcessor:* AsyncFileLogger:*

# You should see:
# I PoseAnalysisProcessor: Training session started: [session-id]
# D PoseAnalysisProcessor: Frame 30: score=85%, inference=15ms
# D PoseAnalysisProcessor: Frame 60: score=90%, inference=14ms
```

### After Training:
```powershell
# Check file sizes
adb shell "run-as com.ttcoachai ls -lh /data/data/com.ttcoachai/files/logs/training_sessions/"

# strokes.jsonl should be MUCH LARGER than sessions.jsonl
# Example:
# -rw------- 209   2026-01-03_sessions.jsonl  ✅
# -rw------- 45678 2026-01-03_strokes.jsonl   ✅ BIG FILE!
```

---

## 📊 What's in Each File?

### sessions.jsonl
```json
{"type":"training_session","session_id":"uuid","exercise_id":"forehand_drive","start_time":1704268800000,"end_time":1704269200000,"total_strokes":42,"good_strokes":35,"average_score":83.5}
```
**Created:** When you start/stop training  
**Size:** ~200-400 bytes  
**Frequency:** 1-2 lines per session

### strokes.jsonl (⚠️ This is what you want!)
```json
{"type":"stroke_analysis","session_id":"uuid","frame_number":30,"inference_time_ms":15,"result":{"phase":"CONTACT","overall_score":85,"detected_wrist_angle":180,"detected_body_rotation":50,...}}
{"type":"stroke_analysis","session_id":"uuid","frame_number":31,"inference_time_ms":14,"result":{"phase":"CONTACT","overall_score":87,...}}
```
**Created:** Every frame during active training (30 FPS)  
**Size:** **LARGE!** ~1KB per second (30 lines/sec)  
**Frequency:** 30 lines per second during training

### events.jsonl
```json
{"event":"app_launched","timestamp":1704268800000,"params":{...}}
```
**Created:** On app events  
**Size:** ~100 bytes  
**Frequency:** Few lines per app session

---

## ❌ Common Mistakes

| Mistake | Result | Fix |
|---------|--------|-----|
| Opened TrainingActivity but didn't click "Start" | ❌ No strokes.jsonl | Click "Почати тренування" |
| Started training but didn't open camera | ❌ No strokes.jsonl | Click "Відкрити камеру" |
| Trained for <5 seconds | ✅ Small strokes.jsonl | Train for 30+ seconds |
| Not in camera frame | ✅ Empty pose results | Stand in front of camera |
| Exported logs immediately after closing app | ⚠️ Buffer not flushed | Wait 5-10 seconds before export |

---

## 🆘 Troubleshooting

### Q: "I clicked Start Training but still no strokes.jsonl"
**Check:**
1. Camera opened? (`onResults()` only called with camera)
2. Pose detected? (green skeleton visible on screen?)
3. Training state active? (button says "Зупинити" not "Почати")

```powershell
# Check if training session is active
adb logcat -d | Select-String "Training session started"
# Should show: "Training session started: [session-id]"

# Check if pose results are coming
adb logcat -d | Select-String "Frame.*score"
# Should show: "Frame 30: score=85%, inference=15ms"
```

### Q: "strokes.jsonl exists but is empty/small"
**Possible reasons:**
- Training time too short (<5 sec)
- Pose not detected (not visible in camera)
- Buffer not flushed (wait 5-10 sec after stopping)

```powershell
# Check if pose was detected
adb logcat -d | Select-String "landmarks|keypoints"
```

### Q: "File says 'No such file or directory'"
**This means:** File was never created = No training session ran with camera

**Solution:** Follow steps 1-6 above carefully!

---

## ✅ Success Criteria

You know logs are working correctly when:
- ✅ `strokes.jsonl` **exists** on device
- ✅ `strokes.jsonl` is **LARGE** (>1KB for 30 sec training)
- ✅ Logcat shows "Frame X: score=Y%" messages
- ✅ Export shows multiple JSONL lines with stroke analysis
- ✅ Each line has `detected_wrist_angle`, `detected_body_rotation`, etc.

---

## 📋 Quick Checklist

- [ ] App launched on Android device
- [ ] Exercise "Накат справа" selected
- [ ] Training screen opened
- [ ] **"Почати тренування" clicked** ⚠️ CRITICAL
- [ ] **"Відкрити камеру" clicked** ⚠️ CRITICAL
- [ ] Performed 10-20 strokes in front of camera
- [ ] Trained for at least 30 seconds
- [ ] Stopped training ("Зупинити тренування")
- [ ] Waited 5-10 seconds (buffer flush)
- [ ] Ran export script
- [ ] Verified `strokes.jsonl` is LARGE (>1KB)

---

**Last Updated:** January 3, 2026  
**Your Current Status:** ❌ Training not started (only app launched)  
**Next Step:** Follow steps 1-6 to generate real stroke analysis logs
