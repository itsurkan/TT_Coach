---
name: Stay in scope — don't fix adjacent problems unasked
description: When user asks to fix X, fix X only; do not expand to Y even if Y looks broken in the screenshot
type: feedback
originSessionId: 001ad2a2-dff3-43f4-bd97-ff44be75ba01
---
When the user asks to fix a specific thing (e.g., "fix legs"), fix that thing only. Do NOT also fix adjacent issues you notice (e.g., torso orientation, camera view, etc.), even if they seem obviously wrong in the screenshot.

**Why:** in the TT_Coach drill-shape-editor sessions (2026-04-19), user asked to fix legs. I saw the figure looked horizontal at 9:00 view and added a `TorsoUprighter` pass on my own initiative. User pushed back: "why did you touch torso? i asked to change legs position". Out-of-scope work wastes iteration cycles and trust.

**How to apply:**
- Fix exactly what was asked. If something else looks broken, point it out in text and ask "should I also address X?" — don't just do it.
- Screenshots are for observation, not automatic fix expansion.
- Before adding a new post-processing stage to a pipeline, confirm the stage's purpose matches the user's stated problem.
