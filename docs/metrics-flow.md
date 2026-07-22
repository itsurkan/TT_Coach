# TT Coach — how metrics flow (RTMPose live drill path)

The five in-plane metrics, where they're extracted, how the personal baseline turns them into rules, and where feedback is gated.

## The 5 metrics (`CoreMetricSpecs.ALL`)

| metricKey | Measures | Sanity bounds | Maps to CorrectionType |
|---|---|---|---|
| `elbow_angle` | elbow angle (shoulder–elbow–wrist) | 20–170° | `ELBOW_POSITION` |
| `shoulder_angle` | shoulder angle | 5–175° | `BODY_ROTATION` |
| `knee_bend` | hip–knee–ankle, 180° = straight leg | 60–180° | `KNEE_BEND` |
| `torso_lean` | facing-normalized torso lean | −60…+60° | `BODY_ROTATION` |
| `shoulder_tilt` | shoulder line tilt | −60…+60° | `BODY_ROTATION` |

Sanity bounds **drop** glitchy values from a rep — they never generate coaching cues. All angles are xScale-corrected (`ViewGeometry`), and every extractor returns `null` if a needed keypoint has `score < 0.3`.

## End-to-end flow

```mermaid
flowchart TD
    subgraph FRAME["Per frame"]
        KP["RTMPose keypoints<br/>(COCO-17, live camera)"]
        AC["AngleCalculations2D<br/>xScale-corrected, null if score &lt; 0.3"]
        M["5 metric values<br/>elbow_angle · shoulder_angle · knee_bend · torso_lean · shoulder_tilt"]
        KP --> AC --> M
    end

    subgraph REP["Per rep (stroke)"]
        SD["StrokeDetector2D<br/>wrist-speed peak"]
        EX["DrillMetrics.extractAtPeak<br/>median over ±70 ms around peak"]
        SB["SanityBounds<br/>drops out-of-band values (never coaches)"]
        SD --> EX --> SB
    end
    M --> EX

    subgraph CAL["Calibration (first reps → baseline)"]
        FF["ForwardStrokeFilter<br/>drops recovery swings"]
        RF["RepFilter<br/>median banding"]
        YG["Per-rep yaw gate<br/>|yaw| &gt; ~30° → rep excluded"]
        BD["BaselineDeriver.deriveFromMetrics<br/>2σ outlier exclusion → mean, σ per metric"]
        PB[("PersonalBaseline<br/>metricStats[key] = mean, σ<br/>phaseDurationsMs<br/>(Room)")]
        FF --> RF --> YG --> BD --> PB
    end
    SB -- "calibration mode" --> FF

    subgraph LIVE["Live training (per rep)"]
        BRF["BaselineRuleFactory.defaultRules<br/>per metric with σ &gt; 0:<br/>ConsistencyRule kSigma = 2.0<br/>per phase duration: RhythmRule ±25%"]
        FRE["FrameRuleEvaluator<br/>pass ⇔ |value − mean| ≤ 2σ"]
        DFE["DrillFeedbackEngine.evaluateRep<br/>fail → cue, severity = |Δ| / σ"]
        BRF --> FRE --> DFE
    end
    PB --> BRF
    SB -- "training mode" --> FRE

    subgraph OUT["Feedback surfaces"]
        MAP["mapMetricToCorrectionType<br/>elbow_angle → ELBOW_POSITION<br/>knee_bend → KNEE_BEND<br/>torso/shoulder → BODY_ROTATION<br/>unknown → GENERAL"]
        GATE{"Per-type toggle<br/>SettingsManager<br/>.isCorrectionTypeEnabled<br/>(positive + GENERAL always pass)"}
        V["🔊 Voice cue<br/>PresetVoiceController / TTS"]
        UI["📱 On-screen feedback list<br/>counts · flagged · explain sheet"]
        SA["📈 Session analytics<br/>Top Focus Areas"]
        MAP --> GATE
        GATE -- enabled --> V
        GATE -- enabled --> UI
        GATE -- enabled --> SA
        GATE -. disabled → silent .-> X["(no output)"]
    end
    DFE -- "cue(metricKey)" --> MAP

    subgraph EDITOR["Drill editor (custom drill)"]
        ED["knees · замах / удар bands<br/>e.g. 110–130°<br/>CustomDrillEntity.perPhaseTargetsJson"]
    end
    ED == "NEW: explicit band overrides the<br/>2σ baseline rule for knee_bend" ==> FRE

    style PB fill:#2b3a55,stroke:#7ea6e0,color:#fff
    style GATE fill:#4a3b1f,stroke:#d4a017,color:#fff
    style ED fill:#1f4a2e,stroke:#4caf50,color:#fff
    style X fill:#333,stroke:#666,color:#aaa
```

## Notes

- **Baseline is global on the RTM path, not per-drill.** Every RTM-eligible exercise (built-in forehand drive and all `custom_*` drills, including clones) loads the single `getActiveBaseline("forehand_drive_rtm")` ([TrainingActivity.kt:147-150](../app/src/main/java/com/ttcoachai/TrainingActivity.kt#L147-L150)). Cloned drills have no calibration of their own — they inherit this baseline; the editor's per-phase bands are their only personalization.
- **The default rule is relative, not absolute:** a rep passes when `|value − baseline mean| ≤ 2σ` (`FrameRuleEvaluator`). A value far from an editor target can still read green if it sits near the player's own calibration mean — hence the explicit editor-band override for `knee_bend` (green edge in the diagram).
- **Rhythm** (`RhythmRule ±25%` on phase durations) exists alongside the five angle metrics but produces rhythm cues, not angle cues.

## 5 metrics vs 7 correction chips

The 7 settings chips (`WRIST`, `BODY_ROTATION`, `FOLLOW_THROUGH`, `CONTACT_HEIGHT`, `ELBOW_POSITION`, `STROKE_SPEED`, `KNEE_BEND`) come from the legacy MediaPipe `StrokeAnalyzer` era. On the current RTM path the five metrics collapse onto only **3 active types**:

| Chip | RTM live path |
|---|---|
| `ELBOW_POSITION` | ✅ `elbow_angle` |
| `BODY_ROTATION` | ✅ `shoulder_angle` + `torso_lean` + `shoulder_tilt` (all three share one chip) |
| `KNEE_BEND` | ✅ `knee_bend` |
| `WRIST`, `FOLLOW_THROUGH`, `CONTACT_HEIGHT` | ❌ never emitted by the RTM path (legacy-only; wrist/contact/follow-through are 3D/ball-dependent checks the 2D pivot dropped per the trust rule) |
| `STROKE_SPEED` | ❌ dead chip — never emitted even by legacy `StrokeAnalyzer` |

`GENERAL` is the 8th enum value: it has no chip, always passes the gate, and is the fallback for unknown metric keys.
