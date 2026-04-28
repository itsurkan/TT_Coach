# 🔍 Quick Reference: Where are the logs?

## TL;DR

**Логи на телефоні:**  
❌ **НЕ МОЖНА** знайти через файловий менеджер  
✅ **МОЖНА** експортувати через USB

**Швидкий експорт:**
```powershell
.\scripts\quick_export.ps1
```
Результат: `d:\Desktop\TT_Coach_AI\logs_export\`

---

## Why can't I see logs on my phone?

📱 **Short answer:** `/data/data/` is a **protected system folder** in Android.

🔒 **It's not a bug, it's a security feature:**
- File managers can't access it (without root)
- Only the app itself can read/write there
- This protects your data from other apps

---

## How to get logs?

### ✅ Method 1: USB Export (Works Now)

**Requirements:** USB cable + ADB installed

```powershell
cd d:\Desktop\TT_Coach_AI
.\scripts\quick_export.ps1
```

**Result:** Logs copied to your computer in `logs_export/` folder

---

### 📲 Method 2: Share from Phone (Coming Soon)

**Will be added in Settings:**
- Settings → Debug → Export Logs
- Creates ZIP in accessible folder
- Share via Email/Drive/Telegram

---

## File Structure

```
logs_export/
├── sessions.jsonl   ← Training sessions (start/end times)
├── events.jsonl     ← App events (launched, errors)
└── strokes.jsonl    ← Pose analysis per frame
```

**Format:** JSONL (JSON Lines) - one JSON object per line

---

## Read More

📚 **Detailed guides:**
- [How to Access Logs](HOW_TO_ACCESS_LOGS.md) - Full guide with troubleshooting
- [Log Locations](LOG_LOCATIONS.md) - Technical details about log storage
- [Logging System README](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/core/logging/README.md) - Architecture docs

---

**Updated:** January 3, 2026  
**Status:** ✅ Working (USB export only)  
**Next:** 📲 Add in-app export to accessible folder
