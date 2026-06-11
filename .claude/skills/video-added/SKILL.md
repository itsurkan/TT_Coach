---
name: video-added
description: Use when the user says a new video/clip was added to Videos/ — "video-added", "clip added", "new footage", "added a video" — and wants pose detection run and the clip viewable in poses_viewer.
---

# video-added — new clip → RTMPose JSON → poses_viewer

One command handles the whole flow: `scripts/poses/export_new.py` tidies loose clips into the `Videos/<base>/<base>.mp4` folder convention and exports full-fps schema-v2 pose JSON next to the video.

## Flow

1. **Find the clip(s):** loose video files (`.mp4`/`.mov`/`.webm`) directly in `Videos/` root.
2. **Rename if ugly:** camera/messenger names (`video5285221050324396305.mp4`, `IMG_1234.mp4`) get a short base first — `video_N` with N = highest existing + 1 (don't reuse gaps), or `<person>_N` if the user named the subject. `mv Videos/<long>.mp4 Videos/<short>.mp4`. Tell the user the name you picked.
3. **Export** (always the repo venv, never system python, never `pip install`):

   ```bash
   .venv/bin/python scripts/poses/export_new.py
   ```

   Run in background — full-fps export takes minutes per clip. It moves the file into its folder, auto-detects fps for `--interval`, skips anything that already has `_poses_rtm.json`, and prints a summary. Pass base names to restrict (`export_new.py video_3 video_4` — folder stem, accepts several), `--feet` for Halpe26 foot keypoints.
4. **Verify:** `Videos/<base>/<base>_poses_rtm.json` exists and the summary line says exported. Then tell the user: refresh http://localhost:5780, pick the video, enable the **RTM** header toggle.

## Notes

- Existing `Videos/<base>/` folders are never scanned implicitly — name one explicitly to backfill it.
- Don't run the raw `export_poses_rtmpose.py` for this flow — its 100 ms default interval gives choppy playback; `export_new.py` sets full-fps automatically.
- Follow up with the `viewer-qa` skill to judge overlay quality, and `fixture-pipeline` if the clip should become a shared-KMP test fixture.
