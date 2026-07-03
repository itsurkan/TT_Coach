# How Real Coaches & Voice-Coaching Products Give Feedback — Practitioner Report

**Date:** 2026-06-18
**Companion to:** [2026-06-18-voice-feedback-best-practices.md](2026-06-18-voice-feedback-best-practices.md) (the academic report).
**Why this exists:** the academic literature is thin and cautious, and flagged three of our six angles as weakly-evidenced (coaching modes, skip-vs-late timing, TTS voice UX). Elite coaches and shipping voice-coaching products have *practical* systems for exactly those gaps. This report mines table-tennis coaches, coach-education frameworks, and fitness/sports voice-tech for concrete, reusable patterns.
**Method:** three parallel web-research agents (TT coaches; coaching-communication frameworks; real-time voice cueing in fitness/sports tech). Findings flagged sourced vs inferred; access caveats noted per source. This is *descriptive practitioner knowledge*, not adversarially-verified fact — weigh accordingly.

---

## The big result: practitioners and the science AGREE on the core

Independently of the motor-learning papers, working coaches and shipping products converge on the same spine:

- **Don't cue every rep.** Coach-ed guidance: *"if you cue every time you see an error, often the lifter would have fixed it the next rep regardless"*; *"intermittent (~half the reps) beats every-rep."* Fitness apps are **event-triggered, not periodic** — they speak on a *threshold breach*, duck the music, then go quiet. Even Nike Run Club (the chatty outlier) draws "talks too much" complaints; users say *"silence is golden."* → This is exactly the **bandwidth gate** from the science report, arrived at independently.
- **One thing at a time.** Near-universal across TT coaches and coach-ed ("fix ONE thing / KISS", "one-to-two cues max", priority hierarchy). → Matches the one-cue working-memory rule.
- **Short, imperative, before/after the rep — never mid-rep.** ACE group-fitness: *"use the least amount of words possible"* ("Knee up", 1–2 keywords for skilled clients); *"if they can't correct it in ~10s, don't say it."* → Matches prescriptive + brevity.
- **Praise genuine success, specifically, immediately.** *"Catch them doing it right"* — reward correct execution within ~3 s; *"generalized praise such as 'good job' is feedback from low-skilled coaches."* → Matches event-driven praise; sharpens it: **name the thing** ("yes — that follow-through"), don't just say "good".

That convergence is the strongest signal in either report: the design spine (gate by tolerance, one short cue, praise real success) is backed by *both* theory and practice.

---

## Gap-filler #1 — The three modes now have practitioner grounding

The science had *no* direct evidence on coaching personas. Practitioners do — via the **"warm demander"** model and observed tone archetypes. Crucially: **demanding ≠ mean.** Respected tough coaches (Belichick, Saban, Popovich) work through *clarity of expectations* + *demonstrated belief in the player*, not cruelty — *"high standards are a sign of respect,"* *"hold to a high standard after teaching what the standard is."*

Mapping our three modes onto observed coach styles (the app reuses 3 existing personas — Motivational/Technical/Gentle — naming to be settled in design):

| | **Playful / encouraging** | **Strict / blunt-technical** | **Efficient / minimal** |
|---|---|---|---|
| TT exemplar | Brett Clarke / TTEdge (animal analogies "topspin like a bear", "Like a Boss!", humor) | Werner Schlager Academy + Chinese/PingSunday system (blunt, fundamentals-first, *"repeat a thousand times"*) | Tom Lodziak / PingSkills (terse do/don't mechanics, *"see how little effort you can take"*) |
| Coach-ed archetype | Nurturing — open questions, process-praise, replays the player's own "why" | Warm demander — name standard → state error → state fix; no sandwich, no filler | Minimalist (Ancelotti "quiet leadership") — silence as default, *"don't speak unless you can improve on silence"* |
| Praise | frequent, warm, **specific** | rare, earned, carried by *specific instruction* not labels | almost never (terse "ok") |
| Words | warm phrasing, encouragement after corrections | terse, technical, prescriptive | ≤4 words, single keyword |
| Cadence | more talk, shorter gaps | normal, bandwidth-gated | longest silences, ~1-in-2-to-3 reps |

A caution from the Wooden data: his actual coded sessions were **50% instruction, only 7% labeled praise** — *"it was the information that promoted change, not an evaluation."* → A low "good rep" rate is **not** a cold environment if the corrective cues themselves are helpful and specific. Don't over-rotate the Playful mode into empty cheerleading.

On praise ratios: practitioners cite a **5:1** heuristic (Positive Coaching Alliance) — but explicitly *"spread across the week,"* not a moment-by-moment threshold. This survives the Losada debunk precisely because it's a loose heuristic, not a formula. → Keep praise **event-driven**; treat any ratio as a soft per-mode lean, never a hard rule.

## Gap-filler #2 — Skip-vs-late now has a concrete budget

The science could only *infer* "skip stale cues." The voice-tech research gives the missing numbers and the convergent product pattern:

- **Words-per-second budget** (from speech-rate sources: clear speech ~130 WPM, conversational ~150–160 WPM → ~2.2–2.5 words/sec):
  - **~1.0 s gap → 2–3 words**  ("bend more")
  - **~1.5 s gap → 3–4 words**  ("bend the knees more")
  - **~2.0 s gap → 4–5 words**
  → This makes the skip rule *computable*: estimate utterance duration from word count; if it can't finish inside the gap to the next stroke's contact, **skip or shorten it**.
- **Convergent product pattern** (no product documents an explicit "next-rep skip", but all point the same way): place the cue in the **inter-rep gap**, leave a short post-movement gap so the player feels their own stroke first (concurrent-feedback dependency warning), use **anticipatory cueing** to forecast the *next* phase, and **self-interrupt** a stale utterance when a higher-priority one arrives — `speechSynthesis.cancel()` before `.speak()` (barge-in).
- **Anticipatory cueing** (ACE): name the change *before* it happens ("…in 3-2-1"), with fewer words for advanced players ("4-beat" → "2-beat"). → For us: a brief cue can be *pre-positioned* before the next stroke rather than reacting late to the last one.

→ **Design rule, now grounded:** compute each candidate cue's spoken duration from its word count; if it won't complete before the next detected stroke's contact, drop it (a skipped cue = "within tolerance this rep") or fall back to a shorter variant; always `cancel()` a still-playing utterance when a newer cue wins.

## Gap-filler #3 — TTS / voice UX now has concrete guidance

The science had zero coverage. Practitioner/HCI sources give usable defaults:

- **Brevity is the binding constraint.** Voice is *"one-dimensional with zero persistence"* — no skimming; **one idea per utterance.** Combined with the WPM math above, this is *why* the catalog must be redesigned around utterance length, not just meaning.
- **Speech rate:** conversational 150–160 WPM, clearer ~130 WPM. → A slightly *slower* rate aids intelligibility during exertion; expose `rate` per mode (Efficient can run faster/terser, Playful slightly warmer/slower).
- **Warmth helps.** Expressive, prosodically rich TTS raises trust and engagement; monotone hinders. For voice, *more human-like = more liked* (no uncanny valley). → Pick the most natural available voice per mode; warmth is a real compliance lever.
- **Ukrainian is the weak spot.** `uk-UA` is supported as a language, but **browser/OS voice availability and quality are thin** vs. English. Neural uk-UA exists via cloud (e.g. Azure `uk-UA-OstapNeural`). → For the poses_viewer prototype, Web Speech API `uk-UA` is fine to start, but **plan a cloud-TTS fallback** if a warm Ukrainian voice matters; don't assume a good local uk voice exists.
- **Rhythm cueing aside:** TT shadow-play uses ~72 BPM, natural stroke rhythm ~600 ms ≈ 100 BPM — but real **multiball is feeder-driven, not metronome-driven.** → Model cadence against *detected stroke intervals*, not a fixed beat.

## On feedback structure: drop the "sandwich"

Coach-education has largely **abandoned the praise-criticism-praise sandwich**: it *dilutes the message* ("can't tell what to maintain vs change"), reads as *manipulative*, and becomes *predictable* ("listen past the praise, wait for the criticism"). PCA: *"before athletes can hear critical feedback, they need to know you care."* → For the app: don't wrap corrections in filler praise. Use **clean prescriptive cues**, and **separate, event-triggered, specific praise** when something genuinely improves.

The teaching-style spectrum (Mosston) also maps to our **trust rule**: use **Command/prescriptive** phrasing for the 5 precise in-plane metrics (the closed, measurable parts), and lean toward **guided-discovery / qualitative** prompts where degrees aren't trustworthy (rotation/coil).

---

## Reusable cue vocabulary (verbatim from coaches — for the message catalog)

Short, single-aspect imperatives, grouped by what we measure. These are real coaching phrases (mostly verbatim **Q**; some paraphrased **P**) — ideal raw material for short TTS cues:

- **Legs / stance / knee bend:** "Use your legs" · "Push up with your legs" · "Power from the ground" · "Bend the knees" · "Fast legs"
- **Hip / waist / torso rotation:** "Rotate the waist" · "Use the body, not just the arm" · "Rotate from the hips" · (don't be "like a robot")
- **Elbow / arm:** "Elbow close" · "Don't be a T-Rex" (keep a gap) · "Lazy arm" / "loose arm" · finish "like a salute"
- **Wrist:** "Relax the wrist" · "Whip the ball" (advanced) · "Straighten the wrist"
- **Contact point / timing:** "Wait for the ball" · "Hit at the top" (peak of bounce) · "Contact in front" · "Back of the ball"
- **Brush / spin:** "Brush the ball" · "Brush up" · "Brush fine and fast"
- **Follow-through:** "Finish high" · "Touch your eyebrow" · "Hip to lip" · "Don't cross your body"
- **Relax / accelerate / rhythm:** "Relax, then accelerate" · "Don't tense up" · "Let it flow" · "It will come"
- **Praise (specific > generic):** "Yes — that follow-through!" · "That's the shape" · "Clean — repeat that" · (avoid bare "good job")
- **Self-correction (Hodges):** after a miss, shadow the correct stroke once — terse, immediate.

## What to carry into the design

1. **Bandwidth gate** stays the cadence spine (confirmed by both reports).
2. **Compute spoken duration from word count** → makes the skip-vs-late rule concrete; barge-in via `cancel()`.
3. **Modes = same levers at different settings** (band width, gap, praise frequency, word choice, voice rate/warmth) — now grounded in the warm-demander + archetype evidence; "which is best" is still an A/B question.
4. **Specific event-praise**, no sandwich, no fixed ratio; a quiet mode isn't a cold mode if cues are useful.
5. **Shorten the catalog around utterance length**, seed it from the real cue vocabulary above; Command phrasing for precise metrics, qualitative for rotation (trust rule).
6. **uk-UA**: start on Web Speech API, plan a cloud-TTS fallback for a warm Ukrainian voice.

## Sources (selected; full lists in the agent runs)

**TT coaches:** PingSkills (pingskills.com), Tom Lodziak (tabletenniscoach.me.uk), EmRatThich/PingSunday (pingsunday.com), Larry Hodges (butterflyonline.com, tabletenniscoaching.com), Brett Clarke (@BrettClarkeTT), Werner Schlager Academy via experttabletennis.com, racketinsight.com.
**Coaching frameworks:** Positive Coaching Alliance (criticism-sandwich, 5:1), athleteassessments.com, athleticperformanceacademy.co.uk, spectrumofteachingstyles.org, Human Kinetics (guided discovery), ACE (cueing), bulletproofmusician.com (Wooden/Tharp-Gallimore data), edutopia.org / barkleypd.com (warm demander), Critical positivity ratio debunk (Wikipedia, Retraction Watch).
**Voice/fitness tech:** NPR on NRC coach, Runna/Garmin audio-cue docs, NHS C25K, ACE/IDEA cueing articles, Peloton mantras, SwingVision, ACE WPM/voice-UX (nngroup.com, Google VUI, milvus.io TTS rate), Nuance barge-in docs, MDN SpeechSynthesis, Azure/aivoov uk-UA TTS, experttabletennis.com rhythm, PMC RAS/entrainment studies.

**Caveats:** Several primary coach pages (PingSunday, Brett Clarke) blocked automated fetch — those quotes came via search extracts/secondary write-ups; **verify verbatim before putting a coach's exact words in the product.** The "praise on correction" and "4:1/5:1 ratio" guidance is folk/heuristic consensus, not a measured law. No product documents explicit next-rep skip logic — that rule remains a (now better-grounded) inference.
