# Table Tennis Coach AI — Project Context & Decisions Document

> Consolidated context from research sessions (June 2026). Purpose: hand-off document for an AI agent (e.g. Claude Code) to act on. Contains all technical decisions, rationale, architecture, market findings, and roadmap.

---

## 1. Project Overview

**Product:** Android-first mobile app for table tennis technique coaching using computer vision pose estimation.

**MVP (buildable in ~2 weeks):** Fixed/structured drills with 2D in-plane joint-angle analysis and voice/text feedback every 3–5 seconds ("real-time" for this product = 3–5 sec cadence, since voice feedback itself takes seconds).

**MVP exercises (3 fixed drills):**
1. **Footwork / legs** (ноги) — front-facing camera
2. **Forehand drive** (накат справа) — side camera
3. **Forehand topspin** (топспін) — side camera

**Target user:** intermediate players (USATT 1000–1800 equivalent), training 3–5×/week.

**Constraints:** Android/Kotlin, offline-capable, Ukrainian + English localization, closed-source commercial product (licensing matters!), founder dev machine = Mac Mini M4.

**History:** Founder previously spent 3 months on a full 3D version using MediaPipe; it failed to reach acceptable quality **because of the unreliable monocular z-coordinate**. This was not an execution failure — per-frame monocular depth is fundamentally inadequate (see §4).

**Future roadmap (post-MVP):** free-play analysis (no fixed drills), real-time technique AND strategy feedback, ball trajectory tracking, ball–racket interaction. Hardcoded angle thresholds will NOT transfer (learned models needed); the 2D pipeline, drill taxonomy, UX, and collected user data WILL transfer.

---

## 2. Pose Model Decision (FINAL)

### Chosen: RTMPose (MMPose, Apache-2.0)
- **RTMPose-m** on desktop/Mac for prototyping (75.8% AP COCO, 90+ FPS on i7 CPU, 430+ FPS GTX 1660 Ti)
- **RTMPose-s** on Android (72.2% AP COCO, 70+ FPS on Snapdragon 865, ~13.9 ms inference)
- Try -m on modern flagships first (high-accuracy mode); -s as default/fallback
- Top-down: requires person detector → **RTMDet-nano**, run every ~10–15 frames with bbox tracking between detections (single player, bbox barely moves)

### Rejected: MediaPipe Pose
- Its only unique advantage (built-in 3D world landmarks via GHUM) is exactly what the founder doesn't trust — monocular z is noisy (>10 cm knee/ankle world-coordinate errors reported; MediaPipe issue #4917)
- Loses to RTMPose on 2D accuracy
- Single-person design was fine, Apache-2.0 license was fine — but 2D accuracy is the priority and RTMPose wins

### Rejected: YOLO26-Pose
- Technically strong: up to 71.6 mAP50-95 COCO pose, NMS-free, RLE keypoints (+7.2 AP over YOLO11), clean CoreML/TFLite/NCNN export
- **AGPL-3.0 license is the dealbreaker**: closed-source commercial use requires either open-sourcing the entire app or buying an Ultralytics Enterprise license. Per Ultralytics: "An Enterprise License is required if you want to use Ultralytics YOLO without open-sourcing your entire project." This applies even to on-device and internal R&D use.
- Only revisit if: (a) app goes open-source, (b) Enterprise license is purchased (which would then also unlock YOLO for ball detection), or (c) multi-person scenes become required (doubles, coach+player)
- Also 2D-only (17 COCO keypoints), no 3D — so no advantage there either

### Alternatives noted
- **MoveNet** (Apache-2.0, TFLite): lightest fallback for low-end Android devices
- **ViTPose** (Apache-2.0, 80%+ AP): server-side / offline deep analysis only, too heavy for mobile real-time

### Keypoint topology
- COCO 17 keypoints (RTMPose) is sufficient: wrist, elbow, shoulder, hips, knees all present. MediaPipe's 33 points (extra hand/foot) were a mild bonus, not essential.

---

## 3. Why 2D In-Plane Angles Are Enough for the MVP

### What 2D measures accurately (in-plane, with correct camera placement)
- Elbow angle (shoulder–elbow–wrist)
- Shoulder angle (torso–shoulder–elbow)
- Knee bend
- Swing amplitude and wrist trajectory (incl. the critical low-to-high topspin trajectory)
- Stance width (ankle distance, front camera)
- Torso lean / shoulder tilt vs horizon
- Step timing/amplitude (ankle trajectories over time)

### What 2D CANNOT measure from one camera (yaw rotations around vertical axis)
- Torso rotation (поворот корпусу) — the #1 intermediate mistake in forehand topspin
- Foot direction (напрям стоп)
- Pelvis rotation / forward-back pelvis tilt
- Racket angle (open/closed) — not pose at all; separate object, not tracked in MVP
- Weight transfer (partially visible in profile, fully = 3D)

### Product rule (critical for trust)
- **Precise metrics (degrees):** only in-plane angles
- **Qualitative feedback only (no degrees):** rotational cues ("корпус недокручений") inferred from relative shoulder/hip positions — or stay silent
- Do NOT overclaim precision. 2D joint-angle accuracy: 1.4°–6.5° MAE in clinical validation (Lindera-v2, JMIR mHealth 2020, ICC 0.95–0.997), ~9° mean error for athletic movements. Good enough for coaching cues, not fine biomechanics. Intermediates will detect false precision and lose trust.
- Coach analogy: a human coach watching from one side also "sees in 2D" and delivers ~80% of value. The real comparison for users is **2D vs nothing**, not 2D vs 3D.

### Per-exercise camera placement (part of the methodology, instructed at drill start)
- **Footwork:** front camera. ⚠ OPEN DESIGN ISSUE: the table occludes the lower body if camera shoots across the table. Drill must be designed off-table / beside the table, or camera placed on the player's side of the table. Resolve before coding drill #1.
- **Forehand drive / topspin:** side camera, on the playing-hand side, perpendicular to the stroke plane, ~2–3 m, table height. This puts elbow/shoulder/swing/knee into the image plane.
- App should verify camera placement from the first frames' pose (e.g., profile check: shoulders nearly overlapping horizontally → side view OK).

### Scientific backing
- 2D pose + temporal CNN classified 11 table tennis strokes at **99.37% validation accuracy** (Kulkarni & Shenoy, arXiv:2104.09907) — 2D is demonstrably sufficient for stroke analysis
- Monocular 3D depth error: 146–249 mm (vs 72–122 mm in-plane) — Physio2.2M, Nature Sci Reports 2025. This quantifies exactly the MediaPipe z failure the founder experienced.
- Constraining to fixed drills + prescribed camera angle is the standard mitigation in the literature.

---

## 4. Real-Time Android Architecture (MVP)

```
CameraX (ImageAnalysis, max device FPS; prefer high-speed/120fps session if available)
   → YUV→RGB conversion
   → RTMDet-nano (ncnn) — every ~10–15 frames; bbox tracking between
   → RTMPose-s (ncnn) — every frame on ROI
   → 17 keypoints 2D
   → sliding pose buffer (~30–60 poses)
   → stroke-phase detector: wrist-speed peak (speed change between frames)
   → at peak: compute in-plane angles (Kotlin)
   → feedback (voice/TTS + on-screen), cadence 3–5 sec
```

### Runtime
- **ncnn via MMDeploy** (official RTMPose deployment path; fastest mobile CPU/GPU). Pipeline: `RTMPose (PyTorch) → MMDeploy → ncnn (.param + .bin)` → assets/ → JNI wrapper from Kotlin.
- Fallback: ONNX Runtime Mobile (easier Kotlin integration, usually slower than ncnn).
- TFLite: not first choice for RTMPose conversion.

### Camera reality on Android
- 120fps capture is NOT guaranteed: query `CameraCharacteristics.getHighSpeedVideoFpsRanges()`; use `CONSTRAINED_HIGH_SPEED` session if available (fixed FPS, limited resolutions); fallback to 60fps with honest UX warning that contact-moment precision is reduced.
- The stroke contact moment is ~1–2 frames at 30fps and motion-blurred — high FPS + good lighting matter more than model choice for wrist accuracy at contact.
- Build camera-capability detection into onboarding.

### Performance notes
- Thermal throttling under capture+inference is real; profile FPS stability on target devices
- Keep inference on background executor; skeleton/angle overlay rendering must not block camera
- The detector (RTMDet) is the expensive stage — the every-10-15-frames + tracking optimization is what makes real-time feasible

### Angle computation (Kotlin, COCO indices: 5/6 shoulders, 7/8 elbows, 9/10 wrists, 11/12 hips)
```kotlin
data class Kp(val x: Float, val y: Float, val score: Float)

fun angle(a: Kp, b: Kp, c: Kp): Float {
    val baX = a.x - b.x; val baY = a.y - b.y
    val bcX = c.x - b.x; val bcY = c.y - b.y
    val dot = baX * bcX + baY * bcY
    val mag = hypot(baX, baY) * hypot(bcX, bcY) + 1e-6f
    return Math.toDegrees(acos((dot / mag).coerceIn(-1f, 1f)).toDouble()).toFloat()
}

fun elbowAngle(kp: List<Kp>, right: Boolean = true): Float {
    val (s, e, w) = if (right) Triple(6, 8, 10) else Triple(5, 7, 9)
    return angle(kp[s], kp[e], kp[w])
}

fun shoulderTilt(kp: List<Kp>): Float =
    Math.toDegrees(atan2((kp[6].y - kp[5].y).toDouble(), (kp[6].x - kp[5].x).toDouble())).toFloat()

fun torsoLean(kp: List<Kp>): Float {
    val shX = (kp[5].x + kp[6].x) / 2; val shY = (kp[5].y + kp[6].y) / 2
    val hpX = (kp[11].x + kp[12].x) / 2; val hpY = (kp[11].y + kp[12].y) / 2
    return Math.toDegrees(atan2((shX - hpX).toDouble(), -(shY - hpY).toDouble())).toFloat() // 0 = vertical
}

fun wristSpeed(prev: Kp, curr: Kp): Float = hypot(curr.x - prev.x, curr.y - prev.y)
// stroke detection: local maximum of wrist speed in the buffer → freeze that frame → compute angles
```

### Reference angle models (content work, not CV work)
- Each drill needs a per-phase reference model of correct angles (from a coach recording or hand-coded ranges). Without it feedback is not actionable. This is a separate content/methodology task — plan for it.
- v1 hardcoded thresholds are a **disposable heuristic** — fine for validation, replaced by learned models later.

### Open UX question (not researched)
- Voice feedback in a noisy gym with phone 2–3 m away: earbuds? volume? large-print visual fallback? Core UX decision, decide during beta.

---

## 5. 3D Strategy — MotionAGFormer (Future Layer, NOT MVP)

### What 2D→3D lifting is
- Input: sequence of 2D keypoints (from RTMPose), NOT images. Output: 3D joints. Reconstructs depth from **temporal context** of a window of motion — far more stable than MediaPipe's per-frame z.
- This is the correct path to rotational metrics (torso/pelvis rotation, foot direction) that 2D can't measure. The founder's 3-month MediaPipe failure was "per-frame z" — the fix is "temporal lifting on top of quality 2D," not trying 3D-from-image again.

### MotionAGFormer vs MotionBERT
- **MotionAGFormer (WACV 2024) — preferred**: dual-stream Transformer + GCNFormer (graph) branches; better accuracy with fewer params than MotionBERT; has -XS/-S variants
- MotionBERT (ICCV 2023): DSTformer, pretrained universal motion backbone; heavier; best with 243-frame windows
- Both are temporal models requiring a frame window (27/81/243); both fail on too-short inputs

### Critical windowing rules (from discussion)
- **10 frames of a stroke is NOT enough input** — models are trained on 27/81/243-frame windows; insufficient temporal context
- **Do NOT stitch strokes together** (10+10+10) — discontinuities at seams produce garbage ("teleporting" joints break the model's continuous-motion assumption)
- **Correct approach:** for each detected stroke, extract a CONTINUOUS window of **±40 frames (~81) centered on the stroke** from the full video; feed the whole window; use only the central ~10 frames of the 3D output (depth reconstructs best at window center)
- Do NOT keep state between strokes — keep the full video and cut continuous context windows from it per stroke

### Why not real-time 3D (the honest analysis)
1. **Latency from future frames:** centered windows need 40 frames AFTER the moment → 0.33 s @ 120fps, 1.3 s @ 30fps delay even with instant compute
2. Causal variants (past-only) lose accuracy exactly on fast motion = the stroke = the critical moment
3. NPU contention with RTMPose under capture + thermal throttling on phone
4. **Product reason:** rotational 3D metrics are best consumed in replay/review, not shouted mid-motion
- HOWEVER: founder's clarification that "real-time" = 3–5 sec feedback cadence makes lifting **technically compatible with the product's real-time** later (the future-frame wait fits inside the cadence). The two-mode split is a product choice, not a hard constraint.

### Post-session cost (if/when implemented)
- Lifting itself is CHEAP (tiny tensors: 17 points × 81 frames; sub-second)
- **RTMPose over frames is the dominant cost.** Optimize: run RTMPose only on ±40-frame zones around detected strokes (not whole video); 60fps suffices for post-session; downscale before inference; batch on GPU
- Realistic target: few seconds per drill clip on a GPU server
- **Recommended: run lifting server-side** — input is keypoints only (a few KB, no video upload → privacy win), phone power becomes irrelevant. On-device only viable on recent flagships with -XS/-S, 27–81 windows, INT8 ONNX/ncnn.

### Two-mode architecture (target state)
```
RTMPose 2D ──┬──→ real-time: in-plane angles → instant feedback during drill
             └──→ saved video → MotionAGFormer (server) → 3D rotation angles → deep post-session review
```
One 2D model feeds both modes. The MVP pipeline is the INPUT to the 3D layer — building MVP ≠ throwaway work.

---

## 6. Market Research Findings (May–June 2026)

### Bottom line
**Ship the 2-week MVP. It is a credible beachhead, not a dead end.** No shipping consumer app delivers fixed-drill 2D joint-angle feedback for table tennis — the niche exists only in academic prototypes (ITTF 2025 Doha; MDPI 2023/2025). Pose tech is NOT the moat; speed, TT-native drill design, collected labeled data, and price are.

### Market size
- ~300M players worldwide (ITTF/WTT estimate); 40M competitive (IOC)
- Registered/paying core is small: Germany DTTB 540k+ members / ~9,300 clubs (growing 3 yrs straight); USA: only ~14k USATT members vs ~19M recreational; Ukraine: ~2,800 ranked players / 215 clubs; China: ~30M regular (Waldner interview estimate)
- "Sports coaching app" market estimates unreliable: $306M–$4.2B for 2025 depending on definition (14× spread!). Don't build the financial model on these; build on own funnel conversions.

### Competitors
| Product | Type | Notes |
|---|---|---|
| SwingVision | adjacent | Tennis/pickleball match analytics (ball/lines/score, NOT form). ~$9–10M raised, $150–180/yr. Self-reported "20k subs / $4M ARR" but audited FY2024 = $2.75M, only ~12% YoY growth → slow for venture pace, TT expansion less likely |
| Spherely 小球圈 | closest | 6 racket sports incl. TT; stroke analysis + NEW 3D motion replay. **NOT real-time — post-session highlight/replay/community app** (verified June 2026: "record and analyze," "slow playback, zoom, replay"). Bootstrapped, China-centric, built "in 4 months with AI" → proves low barrier to entry AND validates 2-week MVP pace. Their vector is toward motion analysis — monitor actively |
| Spinsight | closest | Real-time spin/speed BUT via special balls + sensors (€5–50/mo + €150 kit), key feature iOS-only → Android niche is empty |
| Pongio | adjacent | Stroke speed only, free |
| SpinCoach | dead | Domain now serves a Russian tournament app |
| PingSkills / Tom Lodziak / PingSunday | content | Coaching content channels, not pose apps → these are ACQUISITION CHANNELS (Lodziak ~149–275k subs, intermediate-focused; PingSunday ~191k; TableTennisDaily ~443k; Pongfinity ~4.7M) |

**Founder's unique position: real-time technique coaching for fixed TT drills — nobody does this, even the closest competitor (Spherely) is post-session.**

### Per-market verdicts
- **Ukraine 🇺🇦:** validation ground + free beta cohort + labeled data. ~2,800 ranked players — do NOT model revenue here. GTM: clubs, coaches, federation, Telegram.
- **Europe 🇪🇺 (Germany!):** PRIMARY revenue beachhead. Largest registered base globally; top willingness-to-pay. Localize DE + EN. Pricing: freemium + ~€4.99/mo or €39–49/yr, per-country localized prices (4×+ annual price variation across countries; high-priced fitness apps earn ~3× LTV — do NOT underprice).
- **China 🇨🇳:** NOT for MVP. Google Play inaccessible; only domestic legal entities can publish Android apps (ICP filing); fragmented stores (Huawei ~27.6%, Tencent, Xiaomi, OPPO, Vivo); needs CN hosting, WeChat Pay/Alipay, zh-CN. Spherely already there. Revisit only after EU traction via a local publishing partner (e.g. AppInChina).
- **USA 🇺🇸:** opportunistic upside on the same EN build. $4.99–9.99/mo tolerance. Pickleball boom dilutes racket-sport attention/capital. Watch SwingVision for TT-expansion signals.
- **Global:** EN-first + DE second; YouTube TT creators = most capital-efficient acquisition channel.

### Launch plan with gates
1. **Stage 1 (now, 2–6 wks):** Ship fixed-drill 2D MVP, UA+EN. Ukrainian clubs + r/tabletennis + one TT YouTuber shout-out. **Gate: ≥40% of beta completes ≥3 drill sessions in week 1** + qualitative "angles feel accurate and useful." (Note: this gate is a heuristic, not a validated benchmark — first real benchmark comes from own beta.)
2. **Stage 2 (mo 2–4):** DE localization; freemium €4.99/mo / €39/yr; 2–3 club partnerships + one DE YouTuber. **Gate: trial-to-paid ≥20%** (RevenueCat fitness benchmark) + 30-day retention justifying paid UA.
3. **Stage 3 (mo 4–12):** Replace hardcoded thresholds with learned models on collected data; add backhand/serve; explore free-play + ball tracking. **Gate for China: sustained EU MRR + willing local partner; else defer indefinitely.**

### Pivot triggers
- Trial-to-paid <10% in EU after pricing experiments → one-time purchase OR coach/club B2B model (aligns with founder's earlier plan: B2B coach tier ~month 9)
- SwingVision or Spherely ships a TT drill-coaching mode → accelerate differentiation on drill depth + offline capability

### Differentiators
- **Offline-first is a UNIVERSAL feature, not a Ukrainian one** — club basements have poor connectivity; SwingVision and Spherely are cloud-dependent. Market it globally.
- Ukrainian localization: cheap home-market differentiator; near non-factor abroad. EN + DE matter for revenue.
- On-device processing = strong GDPR/privacy claim ("video never leaves your phone") — pairs with offline-first; formalize in privacy policy. ⚠ Open legal item: filming in clubs (bystanders in frame, EU GDPR) was not researched.

### Marketing-ready value message
- ITTF-framed coaching literature: structured drills + video review narrow the coached-vs-self-directed gap from ~20% to 5–10% over months. Fixed-drill app slots directly into how serious amateurs already train (3–5×/wk, 20–30 min drill blocks).

### Research gaps (not covered)
- Retention benchmarks for niche sports apps (no analogs found)
- Voice-feedback UX in gym conditions
- Android vs iOS split among German club players (if iOS-heavy → earlier iOS port)
- YouTube integration costs/conversion (Stage 1 shout-out = cheap channel test)

---

## 7. Decision Log (chronological)

1. **Pose stack:** RTMPose-m (desktop) / RTMPose-s (Android via ncnn) — chosen over MediaPipe (z unreliable, lower 2D accuracy) and YOLO26-Pose (AGPL-3.0 blocker for closed source)
2. **MediaPipe rejected** specifically because its only edge (built-in 3D) is the failed part; founder builds skeleton ignoring z anyway
3. **2D in-plane angles are sufficient for fixed drills** with per-exercise camera placement; rotational metrics qualitative-only or silent
4. **"Real-time" defined as 3–5 sec feedback cadence** (voice takes seconds anyway)
5. **MotionAGFormer chosen over MotionBERT** for future 3D lifting (newer, more accurate, fewer params)
6. **Lifting windowing:** continuous ±40 frames around stroke-speed peak; never stitch strokes; no cross-stroke state; use central frames of output
7. **Lifting deployment:** server-side post-session preferred (keypoints-only upload, privacy+, phone-agnostic); on-device feasible on flagships only
8. **Real-time 3D rejected for now** (future-frame latency, causal accuracy loss on fast motion, NPU contention) — though 3–5 sec cadence keeps the door open
9. **Fixed drills with hardcoded angles = valid market wedge**, disposable technical heuristic; pipeline/data/UX transfer to free-play vision
10. **Market sequence:** UA validate → EU monetize (DE first) → US same build → CN partner-only later
11. **2D vs 3D final position:** 2D delivers real value now (vs nothing, like a one-angle human coach); 3D is the second step of the same staircase (lifting over the same 2D pipeline), not an alternative first product

---

## 8. Immediate Open Items (resolve before/while coding)

1. **Footwork drill camera/table occlusion** — design drill off-table or camera on player's side; decide before coding drill #1
2. **Feedback modality in noisy gym** — voice (earbuds?) vs large-print visual; test in beta
3. **Reference angle models per drill** — record a coach or hand-code ranges; content task, schedule it
4. **Camera-placement verification** — pose-based check on first frames (profile detection for side drills)
5. **High-FPS capability detection** — onboarding flow + honest degradation message at 60fps
6. **Privacy policy** — on-device processing claim, bystanders/GDPR for club filming
7. **Honest precision UX** — feedback as cues ("розігни лікоть пізніше"), not degrees

---

## 9. Key Sources

- RTMPose: arXiv:2303.07399 (Jiang et al.)
- YOLO26: arXiv:2606.03748 (Ultralytics); license: ultralytics.com/license, GitHub Discussion #1260
- MotionAGFormer: WACV 2024; MotionBERT: ICCV 2023
- 2D stroke classification 99.37%: arXiv:2104.09907 (Kulkarni & Shenoy)
- 2D angle accuracy 1.4–6.5° MAE: Lindera-v2, JMIR mHealth 2020
- Monocular 3D error 146–249mm: Physio2.2M, Nature Sci Reports 2025
- BlurBall (TT ball detection SOTA, CVPR-W 2026): arXiv:2509.18387 — relevant for future ball-tracking phase
- RacketVision (ball+racket benchmark, AAAI 2026 Oral): arXiv:2511.17045 — use cross-attention, not concatenation, when fusing racket pose
- VLM action-quality warning: arXiv:2604.08294 — VLMs (incl. Gemini 3.1 Pro) judge action quality barely above chance; use VLM/LLM only as explanation layer over computed metrics, never as the judge
- SwingVision financials: Wefunder 2025 campaign vs audited FY2024
- Spherely: App Store listings + spherely.app (verified post-session, not real-time, June 2026)
- EU AI Act note: Article 50 transparency obligations live from Aug 2, 2026 — app needs "you're interacting with AI" disclosure for EU users
