package com.ttcoachai.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.ttcoachai.BaseActivity
import com.ttcoachai.R
import com.ttcoachai.calibration.CalibrationActivity
import com.ttcoachai.databinding.ActivityBaselinePreviewBinding
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.repository.PersonalBaselineRepository
import com.ttcoachai.shared.analysis.AngleCalculations
import com.ttcoachai.shared.analysis.BaselineDeriver
import com.ttcoachai.shared.analysis.BaselineRule
import com.ttcoachai.shared.analysis.BaselineRuleFactory
import com.ttcoachai.shared.analysis.FrameRuleEvaluator
import com.ttcoachai.shared.analysis.MetricCalculations
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.PoseFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Dev-only parameter editor (Phase 7).
 *
 * Loads the active `forehand_shadow` baseline, loads a bundled reference rep
 * from `assets/fixtures/forehand_drive.json`, and lets you scrub through
 * frames while tuning each rule's threshold with a slider. For every frame,
 * the per-metric value is recomputed via [AngleCalculations] / [MetricCalculations]
 * and evaluated against the current rule config via [FrameRuleEvaluator];
 * landmarks tied to a failing metric are tinted red on the overlay.
 *
 * Constraints (intentional):
 * - Replays a recorded pose sequence — does NOT synthesize poses from sliders
 *   (IK from scalar params to pose is ambiguous, see tasks.md T037 note).
 * - Phase-duration (Rhythm) rules don't evaluate at the frame level — their
 *   slider only affects the exported JSON.
 * - Gated by `ApplicationInfo.FLAG_DEBUGGABLE`; refuses to open on release
 *   APKs even though the manifest entry is always present.
 */
class BaselinePreviewActivity : BaseActivity() {

    private lateinit var binding: ActivityBaselinePreviewBinding
    private val repository by lazy {
        PersonalBaselineRepository(AppDatabase.getDatabase(this).personalBaselineDao())
    }
    private val handler = Handler(Looper.getMainLooper())

    private var baseline: PersonalBaseline? = null
    private var frames: List<PoseFrame> = emptyList()
    private var currentFrame: Int = 0
    private var isPlaying: Boolean = false
    private var frameIntervalMs: Long = 33L

    private val ruleOverrides: MutableMap<String, BaselineRule> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBaselinePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (!isDebuggable()) {
            Toast.makeText(this, R.string.debug_baseline_not_debuggable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.overlayView.setHumanizationEnabled(true)
        binding.overlayView.setPhaseColoringEnabled(false)
        binding.overlayView.post {
            // Fixture is 720×1280; overlay scales poses into that coordinate system.
            binding.overlayView.setResults(
                emptyList(),
                FIXTURE_IMAGE_HEIGHT,
                FIXTURE_IMAGE_WIDTH,
                RunningMode.VIDEO
            )
        }

        wireTransport()
        lifecycleScope.launch { loadData() }
    }

    private suspend fun loadData() {
        val loaded = runCatching { AssetPoseFrameLoader.load(this, FIXTURE_ASSET_PATH) }
            .getOrElse {
                Log.e(TAG, "Failed to load fixture", it)
                return
            }
        frames = loaded.frames
        frameIntervalMs = loaded.intervalMs
        val active = repository.getActiveBaseline(CalibrationActivity.DRILL_FOREHAND_SHADOW).first()
        baseline = active

        if (active == null) {
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            for (rule in BaselineRuleFactory.defaultRules(active)) {
                ruleOverrides[rule.id] = rule
            }
            buildRuleSliders(active)
        }

        binding.seekTimeline.max = (frames.size - 1).coerceAtLeast(0)
        binding.seekTimeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) renderFrame(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { pause() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        binding.swHumanize.setOnCheckedChangeListener { _, checked ->
            binding.overlayView.setHumanizationEnabled(checked)
        }
        renderFrame(0)
    }

    private fun wireTransport() {
        binding.btnPlayPause.setOnClickListener { if (isPlaying) pause() else play() }
        binding.btnExport.setOnClickListener { exportRules() }
    }

    private fun play() {
        if (frames.isEmpty()) return
        isPlaying = true
        binding.btnPlayPause.text = getString(R.string.preview_pause)
        handler.post(playbackLoop)
    }

    private fun pause() {
        isPlaying = false
        binding.btnPlayPause.text = getString(R.string.preview_play)
        handler.removeCallbacks(playbackLoop)
    }

    private val playbackLoop = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            val next = (currentFrame + 1) % frames.size
            renderFrame(next)
            binding.seekTimeline.progress = next
            handler.postDelayed(this, frameIntervalMs)
        }
    }

    private fun renderFrame(index: Int) {
        if (frames.isEmpty()) return
        currentFrame = index.coerceIn(0, frames.size - 1)
        val frame = frames[currentFrame]
        binding.overlayView.setPoseFrame(frame)
        binding.tvFrameLabel.text = getString(
            R.string.preview_frame_label,
            currentFrame + 1,
            frames.size
        )
        val metrics = computeFrameMetrics(frame.landmarks)
        val failingMetrics = collectFailingMetrics(metrics)
        binding.overlayView.setJointTint { landmarkIdx ->
            if (landmarkIdxNeedsTint(landmarkIdx, failingMetrics)) android.graphics.Color.RED else null
        }
        renderMetricReadout(metrics, failingMetrics)
        refreshRuleSliderLabels(metrics)
    }

    private fun computeFrameMetrics(landmarks: List<Landmark3D>): Map<String, Double?> = mapOf(
        BaselineDeriver.METRIC_WRIST_ANGLE to AngleCalculations.calculateWristAngle(landmarks)?.toDouble(),
        BaselineDeriver.METRIC_BODY_ROTATION to AngleCalculations.calculateBodyRotation(landmarks)?.toDouble(),
        BaselineDeriver.METRIC_FOLLOW_THROUGH_ANGLE to AngleCalculations.calculateFollowThroughAngle(landmarks)?.toDouble(),
        BaselineDeriver.METRIC_CONTACT_HEIGHT to MetricCalculations.calculateContactHeight(landmarks)?.toDouble(),
        BaselineDeriver.METRIC_ELBOW_BODY_DISTANCE to MetricCalculations.calculateElbowBodyDistance(landmarks)?.toDouble()
    )

    private fun collectFailingMetrics(metrics: Map<String, Double?>): Set<String> {
        val b = baseline ?: return emptySet()
        val failing = mutableSetOf<String>()
        for (rule in ruleOverrides.values) {
            val pass = FrameRuleEvaluator.evaluate(rule, b, metrics[rule.metricKey])
            if (pass == false) failing += rule.metricKey
        }
        return failing
    }

    private fun landmarkIdxNeedsTint(idx: Int, failing: Set<String>): Boolean {
        for (metric in failing) {
            if (idx in METRIC_TO_LANDMARKS[metric].orEmpty()) return true
        }
        return false
    }

    private fun renderMetricReadout(
        metrics: Map<String, Double?>,
        failing: Set<String>
    ) {
        val parent = binding.llMetricReadout
        parent.removeAllViews()
        val b = baseline ?: return
        for ((key, value) in metrics) {
            val stats = b.metricStats[key] ?: continue
            val tv = TextView(this).apply {
                text = getString(
                    R.string.preview_metric_line,
                    key,
                    value?.let { "%.2f".format(it) } ?: getString(R.string.preview_metric_na),
                    stats.mean,
                    stats.std,
                    if (key in failing) getString(R.string.preview_fail)
                    else getString(R.string.preview_pass)
                )
                setTextColor(
                    if (key in failing) android.graphics.Color.parseColor("#B71C1C")
                    else android.graphics.Color.parseColor("#1B5E20")
                )
                setPadding(0, 2, 0, 2)
            }
            parent.addView(tv)
        }
    }

    private fun buildRuleSliders(b: PersonalBaseline) {
        val parent = binding.llRuleSliders
        parent.removeAllViews()
        for (rule in ruleOverrides.values.toList()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this)
            val bar = SeekBar(this)
            val cfg = sliderConfigFor(rule)
            bar.max = cfg.steps
            bar.progress = cfg.initialProgress
            label.text = describeRule(rule, frameFailureCount = 0)
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val updated = cfg.buildRule(progress)
                    ruleOverrides[updated.id] = updated
                    renderFrame(currentFrame)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
            row.addView(label)
            row.addView(bar)
            parent.addView(row)
        }
    }

    private fun refreshRuleSliderLabels(metrics: Map<String, Double?>) {
        val parent = binding.llRuleSliders
        val b = baseline ?: return
        var row = 0
        for (rule in ruleOverrides.values) {
            val container = parent.getChildAt(row) as? LinearLayout ?: break
            val label = container.getChildAt(0) as? TextView ?: break
            val failures = countFramesFailing(rule, b)
            label.text = describeRule(rule, failures)
            row++
        }
    }

    private fun countFramesFailing(rule: BaselineRule, b: PersonalBaseline): Int {
        if (rule is BaselineRule.RhythmRule) return 0
        var failing = 0
        for (frame in frames) {
            val value = when (rule.metricKey) {
                BaselineDeriver.METRIC_WRIST_ANGLE ->
                    AngleCalculations.calculateWristAngle(frame.landmarks)?.toDouble()
                BaselineDeriver.METRIC_BODY_ROTATION ->
                    AngleCalculations.calculateBodyRotation(frame.landmarks)?.toDouble()
                BaselineDeriver.METRIC_FOLLOW_THROUGH_ANGLE ->
                    AngleCalculations.calculateFollowThroughAngle(frame.landmarks)?.toDouble()
                BaselineDeriver.METRIC_CONTACT_HEIGHT ->
                    MetricCalculations.calculateContactHeight(frame.landmarks)?.toDouble()
                BaselineDeriver.METRIC_ELBOW_BODY_DISTANCE ->
                    MetricCalculations.calculateElbowBodyDistance(frame.landmarks)?.toDouble()
                else -> null
            }
            if (FrameRuleEvaluator.evaluate(rule, b, value) == false) failing++
        }
        return failing
    }

    private fun describeRule(rule: BaselineRule, frameFailureCount: Int): String = when (rule) {
        is BaselineRule.ConsistencyRule ->
            "${rule.id} | " + getString(
                R.string.preview_rule_slider_consistency, rule.kSigma, frameFailureCount
            )
        is BaselineRule.RegressionRule ->
            "${rule.id} | " + getString(
                R.string.preview_rule_slider_regression, rule.maxDropFromMean, frameFailureCount
            )
        is BaselineRule.RhythmRule ->
            "${rule.id} | " + getString(
                R.string.preview_rule_slider_rhythm, rule.maxDurationDeviationPct * 100.0
            )
    }

    private data class SliderConfig(
        val steps: Int,
        val initialProgress: Int,
        val buildRule: (Int) -> BaselineRule
    )

    private fun sliderConfigFor(rule: BaselineRule): SliderConfig = when (rule) {
        is BaselineRule.ConsistencyRule -> {
            // kσ ∈ [0.5, 4.0] in 0.1 steps
            val steps = 35
            val progress = (((rule.kSigma - 0.5) / 0.1).toInt()).coerceIn(0, steps)
            SliderConfig(steps, progress) { p ->
                rule.copy(kSigma = (0.5 + p * 0.1))
            }
        }
        is BaselineRule.RegressionRule -> {
            // maxDrop ∈ [0, 2 × std] in 40 steps, fallback to linear 0..10
            val steps = 40
            val progress = (rule.maxDropFromMean / 0.25).toInt().coerceIn(0, steps)
            SliderConfig(steps, progress) { p ->
                rule.copy(maxDropFromMean = p * 0.25)
            }
        }
        is BaselineRule.RhythmRule -> {
            // pct ∈ [5%, 50%] in 1% steps
            val steps = 45
            val progress = (((rule.maxDurationDeviationPct * 100 - 5)).toInt()).coerceIn(0, steps)
            SliderConfig(steps, progress) { p ->
                rule.copy(maxDurationDeviationPct = (5 + p) / 100.0)
            }
        }
    }

    private fun exportRules() {
        val arr = JSONArray()
        for (rule in ruleOverrides.values) {
            arr.put(ruleToJson(rule))
        }
        val root = JSONObject().apply {
            put("drillType", CalibrationActivity.DRILL_FOREHAND_SHADOW)
            put("baselineCreatedAtMs", baseline?.createdAtMs ?: 0L)
            put("rules", arr)
        }
        val payload = root.toString(2)
        Log.i("BaselineDump", payload)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("tuned_rules", payload))
        Toast.makeText(this, R.string.preview_exported_toast, Toast.LENGTH_SHORT).show()
    }

    private fun ruleToJson(rule: BaselineRule): JSONObject = JSONObject().apply {
        put("id", rule.id)
        put("metricKey", rule.metricKey)
        when (rule) {
            is BaselineRule.ConsistencyRule -> {
                put("type", "ConsistencyRule"); put("kSigma", rule.kSigma)
            }
            is BaselineRule.RegressionRule -> {
                put("type", "RegressionRule"); put("maxDropFromMean", rule.maxDropFromMean)
            }
            is BaselineRule.RhythmRule -> {
                put("type", "RhythmRule"); put("maxDurationDeviationPct", rule.maxDurationDeviationPct)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(playbackLoop)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    private fun isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    companion object {
        private const val TAG = "BaselinePreview"
        private const val FIXTURE_ASSET_PATH = "fixtures/forehand_drive.json"
        // Must match the videoWidth/videoHeight declared in the fixture JSON header.
        private const val FIXTURE_IMAGE_WIDTH = 720
        private const val FIXTURE_IMAGE_HEIGHT = 1280

        /**
         * MediaPipe pose landmark indices tied to each metric — used to pick which
         * joints glow red when the rule fails at a frame. Indices mirror the
         * MediaPipe pose-landmarker model (0=nose, 11/12=shoulders, 13/14=elbows,
         * 15/16=wrists, 23/24=hips, etc.).
         */
        private val METRIC_TO_LANDMARKS: Map<String, Set<Int>> = mapOf(
            BaselineDeriver.METRIC_WRIST_ANGLE to setOf(14, 16, 20),
            BaselineDeriver.METRIC_BODY_ROTATION to setOf(11, 12, 23, 24),
            BaselineDeriver.METRIC_FOLLOW_THROUGH_ANGLE to setOf(12, 14, 16),
            BaselineDeriver.METRIC_CONTACT_HEIGHT to setOf(16),
            BaselineDeriver.METRIC_ELBOW_BODY_DISTANCE to setOf(12, 14, 24)
        )

        @Suppress("unused")
        private fun forceKeep(a: Any?): Any? = abs(0.0).let { a }
    }
}
