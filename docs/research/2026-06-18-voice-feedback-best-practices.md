# Voice Feedback for Real-Time Stroke Coaching — Research Report

**Date:** 2026-06-18
**Purpose:** Evidence base for designing the TT_Coach voice-feedback system — how often to speak, what to say, in what tone, and how to handle timing — so players keep playing and improve without feeling nagged. Implementation target: the **poses_viewer** prototype first.
**Method:** Deep-research harness — 25 sources fetched, 116 falsifiable claims extracted, 25 adversarially verified (3-vote, 2/3 to kill). 19 confirmed, 6 refuted. Findings below carry the verification verdict and confidence so you can see what's solid vs. inferred.

---

## TL;DR — the design rules the evidence actually supports

1. **Don't speak every rep.** Constant correction is the main source of "naggy" and the main cognitive-overload risk. *But* the popular "less feedback = better learning" law (guidance hypothesis) is **contested, not settled** — so don't hard-code a magic percentage either.
2. **Gate cues by a tolerance band** (bandwidth feedback): stay silent while a metric is within an acceptable band around the player's target; speak only when it's clearly outside. This is the single most defensible cadence mechanism, and it makes "silence" *mean* "you're fine."
3. **Make corrective cues prescriptive** — say *what to do* ("bend more", "through the ball"), not *what was wrong* ("your elbow was bent"). Prescriptive beats descriptive in the evidence.
4. **Prefer external phrasing** (point at the ball / contact / effect) over body-part phrasing — as a sensible default, not a law (several sweeping external-focus claims were refuted in verification).
5. **One short imperative cue at a time.** Protect working memory; don't stack two corrections in one rep.
6. **Praise on events, not on a ratio.** Say "good" right after the player *corrects* an error, or after a within-band streak. The "3:1 positivity ratio" (Losada) is mathematically **discredited** — do not encode any fixed praise:correction ratio.
7. **Let the user choose the mode/tone.** The act of choosing is an autonomy-support lever (theory-supported, low-risk). The claim that a *particular* persona improves outcomes is a **hypothesis to A/B test**, not an established finding.
8. **Skip a stale cue rather than collide it with the next stroke** — an engineering inference (the weakest-evidenced rule here), consistent with bandwidth gating and working-memory limits. A skipped cue just means "within tolerance this rep."

---

## ⚠️ Honesty note: half of this topic is weakly evidenced

The adversarial verification was deliberately harsh, and it killed several appealing claims. Three of the six angles you asked about have **little or no direct experimental support**:

- **Configurable coaching "personalities"** (playful/strict/efficient) — *no source directly tested this.* The mode design below is a synthesis of supported mechanisms (bandwidth, autonomy, event-praise), not a proven recipe.
- **Skip-vs-deliver-late timing** — *no source directly tested post-stroke cue latency or stale-cue collision.* The skip rule is a reasonable inference.
- **TTS / spoken-voice UX** (rate, pitch, warmth, barge-in, EN/UK voice quality) — *zero coverage in the verified motor-learning sources.* These must be decided by HCI practice + your own platform testing. The only transferable principle is **brevity**.

And even on the well-studied angles, six claims were **refuted** (listed at the end). Treat this report as "here's the defensible default and here's exactly how confident we are," not "here's the answer."

---

## Angle 1 — Frequency / cadence: how often to speak

**The headline law is contested.** The classic *guidance hypothesis* says every-rep feedback helps you during practice but creates dependency and hurts long-term retention vs. reduced/faded feedback. A 2022 meta-analysis (McKay et al., *Psych. of Sport & Exercise*; 61 papers, k=75, N=2228) found **no significant effect of reduced feedback frequency at any time point** (delayed-retention g=.19, 95% CI [−.05, .43] — crosses zero) and **no acquisition→retention reversal**. It frames the literature as underpowered — it *fails to support or disconfirm* the law. **Confidence: high (3-0). So: do not treat "less feedback = better learning" as fact.**

**Task complexity matters.** A 2024 balance study (Marco-Ahullo et al., *Sensors*, N=60) found **67% feedback beat 100%, 33%, and 0%**, with the 100% group *worsening* into retention — i.e., the guidance effect held *for a simple continuous task*. But "More Feedback Is Better than Less" (Frontiers, 2016) found **100% beat 50% for a complex coordination skill**. Reviews conclude the guidance effect holds for *simple* tasks but often fails for *complex* sport skills. **A forehand drive is complex — do NOT hard-code 67% or any single percentage.** Confidence: medium (2-1; transfer from visual/continuous tasks is inferential).

**The defensible mechanism: bandwidth (tolerance-band) feedback.** Withhold the cue while error stays inside a goal-centered band; speak only when it exceeds the band (Lee & Carnahan 1990, *QJEP*, "more than just a relative frequency effect"). **Confidence: high (3-0).** Two caveats from verification: (a) bandwidth confounds with frequency (a wider band just = less feedback), and (b) the specific claim that **band *width* affects retention was refuted (0-3)** — so don't obsess over tuning the exact band; and the claim that bandwidth *beats* matched reduced-frequency was downgraded (1-2). Part of bandwidth's value is that a silent (within-band) rep is itself an implicit "you're fine" signal.

→ **Design:** Silence is the default. Speak only when a metric is clearly outside the player's personal band. Make the band and overall cadence **user-configurable per mode** rather than betting on one number.

## Angle 2 — Content & format: what to say

- **Prescriptive > descriptive.** A 2021 systematic review (Oppici et al., 19 studies, novices): **prescriptive KP** ("what to do to fix it") alone beat knowledge-of-results; **KR beat descriptive KP** (merely describing the movement is worse than just giving the outcome); KR + prescriptive KP was best. **Confidence: high (3-0).** Caveat: all 19 studies used **novices** — skilled players may benefit from more descriptive/exploratory feedback. → *"bend more" / "extend through contact" beats "your elbow was bent."*
- **External focus (point at the effect) as a default, not a law.** External-focus *feedback* produced more accurate volleyball serves & soccer kicks (Wulf et al. 2002) — claim confirmed 3-0. **But verification refuted the sweeping generalizations:** the "~80 experiments, external always wins" tally (**0-3**), the "distance effect / more distal is better" (**0-3**), and the broad "feedback-to-movement-effect enhances learning" (**1-2**). → Prefer external phrasing ("through the ball", "lower stance") but don't assume a large universal advantage; body-part cues ("extend elbow") are acceptable when clearer.
- **One cue at a time.** Working memory becomes load-bearing for motor performance once a learner relies on explicit verbal cues (Maxwell, Masters & Eves 2003, *Consciousness & Cognition*). **Confidence: high (3-0).** → Never stack two corrections in one rep; pick the single highest-priority cue.
- **Brevity is forced by the medium.** A 1–3 word imperative ("bend more", "through the ball") fits inside a fast rep; a full sentence does not (see Angle 6).

## Angle 3 — Tone & motivation: don't make them quit

- **Autonomy support + praise-after-good** are the OPTIMAL-theory levers (Wulf & Lewthwaite 2016): positive feedback after *successful* attempts and giving the learner choices enhance expectancies and motivation. **But the direct test is weak** — a 2×2 golf-putting study (McKay & Ste-Marie 2020) crossing autonomy support × feedback frequency found effects that were **"trivial" and non-significant** (the word "trivial" is in the title). Confidence: medium (study facts 3-0, but it's a null result for the theory). A separate cited source ("Feedback After Good Trials Enhances Learning," Chiviacowsky & Wulf 2007) supports praising good reps, though it wasn't independently re-verified in this run. → Treat praise-after-success and autonomy as **low-risk, theory-motivated UX choices**, not proven effects.
- **Say "good" when they correct an error.** This is the motivational payoff your own brief describes, and it's consistent with both praise-after-good and the implicit "you're now within tolerance" signal of bandwidth gating. Trigger it on the *event* (a previously-flagged metric returns to band), not on a timer.
- **Do NOT use a praise:correction ratio.** The "Losada / 3:1 positivity ratio" was **mathematically discredited** (Brown, Sokal & Friedman 2013; the original Fredrickson & Losada modeling was formally retracted). No verified claim supports any fixed ratio. → Praise is **event-driven**, and its *frequency* is a per-mode tuning knob, not a science-backed constant.

## Angle 4 — Configurable modes / personality

**No source directly tested coaching "personalities."** This is the least-evidenced angle (confidence: low). What *is* supported is that letting the user **choose** is an autonomy lever. So the right framing: modes are a product/autonomy feature operationalized through the mechanisms that *are* supported (bandwidth width, cadence, event-praise frequency, word choice) — and whether any specific persona "works better" is an **A/B-test hypothesis**.

Operationalizing your three intents through supported knobs (these map onto the app's existing personas — see Design phase):

| Knob | **Playful** (encouraging) | **Strict** (blunt-technical) | **Efficient** (minimal) |
|---|---|---|---|
| Tolerance band | normal | normal–tight | **widest** (speak least) |
| Cadence / min gap between cues | shorter (more talk) | normal, bandwidth-gated | **longest** feedback-free intervals |
| Praise frequency | high (warm, frequent "nice!") | low (rare, earned) | **rare** (almost never) |
| Word choice | warm, casual, encouraging | terse, prescriptive, technical | **single-word** cues |
| Praise trigger | corrected error + within-band streaks | corrected error only | corrected error only (terse "ok") |

→ All three pull the same levers at different settings — which is *implementable and testable*, even though the "which persona is best" question is unanswered.

## Angle 5 — Timing & latency: skip vs. deliver late

**No verified source directly addressed** post-stroke cue latency, instantaneous-vs-delayed terminal feedback, or stale/overlapping cues during fast continuous strokes. This rule is an **engineering inference (confidence: low)**:

→ **If a corrective cue cannot finish before the next stroke begins, SKIP it** rather than play it stale over the next rep. Rationale: (a) under bandwidth gating, a withheld cue simply means "within tolerance this rep" — skipping is already an accepted outcome; (b) a cue arriving mid-next-rep adds working-memory load exactly when the player is executing (Angle 2). The open question — *what's the maximum tolerable post-stroke latency before a terminal cue stops helping* — has no verified answer and is a candidate for in-app testing.

## Angle 6 — Spoken-voice / TTS UX

**Not covered by any verified motor-learning source.** Decide these by HCI practice + platform testing. The one transferable principle is **brevity** (Angles 2 & 5): spoken-word duration is the binding constraint, so a 1–3 word cue is mandatory for fast strokes, and the message catalog must be designed around utterance length, not just meaning. For the poses_viewer this maps to the **Web Speech API** (`speechSynthesis`): tunable `rate`/`pitch`, per-mode voice selection, and `cancel()` for barge-in (skip-stale). EN voice quality is good across browsers; **Ukrainian (`uk-UA`) voice availability is limited and platform-dependent** — verify which voices actually exist in the target browser. (Sources here were HCI blogs + MDN, not peer-reviewed — treat as practitioner guidance.)

---

## What this means for the poses_viewer prototype

Concrete, in priority order:

1. **Bandwidth gate before anything is spoken.** Reuse the existing per-metric reference-range / `evaluateRep` pass-fail; only a cue *outside band* is eligible to speak. (This already partly exists — extend, don't rebuild.)
2. **One cue per rep, prescriptive, short, externally-phrased where natural.** Shorten the catalog: drop the "than your usual" clause and the "(about X° off)" number for *voice* (keep numbers in the on-screen table). Target ≤3 words for Efficient, a short phrase for the others.
3. **Event-driven praise.** Track which metric was last flagged; when it returns to band, fire "good"/"nice" (mode-dependent). No ratio.
4. **Skip-stale rule** in the playback trigger: if a cue's utterance can't complete before the next stroke's peak, `cancel()` and drop it.
5. **Mode selector** (the three existing personas) wired to: band width, min-gap cadence, praise frequency, and word-choice variant set. Plus a voice profile (rate/pitch/voice) per mode.
6. **All cadence/skip logic unit-tested** (vitest) — it's deterministic and testable without audio.

## Open questions (need in-app A/B testing, not literature)

- Optimal feedback frequency / band width for a *complex, fast, externally-paced* forehand drive — existing numbers come from simple/continuous tasks.
- Whether a specific persona improves adherence/enjoyment, and how much benefit is *choosing* vs. the style itself.
- Maximum tolerable post-stroke latency before a terminal cue stops helping (or harms the next rep).
- Evidence-based TTS rate/pitch/warmth and EN/UK voice choices during exertion; barge-in behavior when a stroke arrives mid-utterance.

## Refuted claims (killed in verification — do NOT design around these)

- "External focus wins across ~80 experiments / internal never wins" — **0-3**.
- "Distance effect: more distal external focus is better" — **0-3**.
- "Bandwidth *width* affects retained learning, not just acquisition" — **0-3**.
- "Bandwidth beats matched reduced-frequency feedback (the gating itself is the benefit)" — **1-2**.
- "Feedback to the movement effect enhances *learning*" (broad form) — **1-2**.
- "Withholding outcome feedback blocks declarative knowledge without hurting procedural" — **1-2**.

## Key sources

- McKay, Yantha, Hussien, Carter & Ste-Marie (2022). Reduced feedback frequency meta-analysis. *Psychology of Sport and Exercise.* [DOI 10.1016/j.psychsport.2022.102165](https://www.sciencedirect.com/science/article/abs/pii/S1469029222000334)
- Marco-Ahullo et al. (2024). Feedback frequency & postural control. *Sensors* 24(5):1404. [PMC10933749](https://pmc.ncbi.nlm.nih.gov/articles/PMC10933749/)
- Lee & Carnahan (1990). Bandwidth KR — more than a relative-frequency effect. *QJEP* 42A(4). [DOI 10.1080/14640749008401249](https://journals.sagepub.com/doi/10.1080/14640749008401249)
- Oppici et al. (2021). Prescriptive vs descriptive feedback review. *Int. Review of Sport & Exercise Psychology.* [DOI 10.1080/1750984X.2021.1986849](https://www.tandfonline.com/doi/full/10.1080/1750984X.2021.1986849)
- Wulf (2013) attentional-focus review + Wulf et al. (2002). [Wulf 2013 PDF](https://gwulf.faculty.unlv.edu/wp-content/uploads/2018/11/Wulf_AF_review_2013.pdf) · [PMC3153799](https://pmc.ncbi.nlm.nih.gov/articles/PMC3153799/)
- Maxwell, Masters & Eves (2003). Working memory & motor performance. *Consciousness & Cognition* 12(3). [PubMed 12941284](https://www.ncbi.nlm.nih.gov/pubmed/12941284)
- Wulf & Lewthwaite (2016). OPTIMAL theory. [PDF](https://gwulf.faculty.unlv.edu/wp-content/uploads/2014/05/Wulf-Lewthwaite-2016-OPTIMAL-Theory.pdf) · McKay & Ste-Marie (2020), "trivial" autonomy×frequency. [DOI S0167945719305743](https://www.sciencedirect.com/science/article/abs/pii/S0167945719305743)
- Critical positivity ratio (Losada) debunk. [Wikipedia](https://en.wikipedia.org/wiki/Critical_positivity_ratio) (Brown, Sokal & Friedman 2013)
- Web Speech API / TTS practitioner refs. [MDN](https://developer.mozilla.org/en-US/docs/Web/API/Web_Speech_API/Using_the_Web_Speech_API) · [web-speech-recommended-voices](https://github.com/HadrienGardeur/web-speech-recommended-voices)
