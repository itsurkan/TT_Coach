package com.ttcoachai.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.ttcoachai.BaseActivity
import com.ttcoachai.R
import com.ttcoachai.databinding.ActivityBaselinePreviewBinding
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.repository.DrillConfigRepository
import com.ttcoachai.shared.models.PoseFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Dev-only drill-shape editor (Phase 7).
 *
 * Loads the canonical stroke for forehand_drive (fixture → JsonStrokeDetector
 * → MeanStrokeBuilder) and loops it. Sliders apply [PoseTransformer] deltas
 * to reshape the replayed pose in real time. On top of every frame we bake
 * a fixed 45° camera yaw so the figure is rendered from the 7:30 position
 * (three-quarter view) rather than a flat frontal 6 o'clock.
 *
 * Edits persist to [com.ttcoachai.models.DrillConfigEntity] so the next open
 * restores the last-tuned shape. Export dumps the same params as JSON to
 * logcat + clipboard.
 */
class BaselinePreviewActivity : BaseActivity() {

    private lateinit var binding: ActivityBaselinePreviewBinding
    private val drillConfigRepo by lazy {
        DrillConfigRepository(AppDatabase.getDatabase(this).drillConfigDao())
    }
    private val handler = Handler(Looper.getMainLooper())

    private var meanStrokeFrames: List<PoseFrame> = emptyList()
    private var currentFrame: Int = 0
    private var isPlaying: Boolean = false
    private var frameIntervalMs: Long = 33L
    private var params: PoseTransformer.EditableParams = PoseTransformer.EditableParams()
    private val viewCameraYawDeg: Float = PoseTransformer.DEFAULT_VIEW_CAMERA_YAW_DEG

    private data class SliderSpec(
        val minValue: Float,
        val maxValue: Float,
        val step: Float,
        val initial: Float,
        val labelFormatRes: Int,
        val writeToParams: (PoseTransformer.EditableParams, Float) -> PoseTransformer.EditableParams,
        val readFromParams: (PoseTransformer.EditableParams) -> Float
    ) {
        val steps: Int get() = ((maxValue - minValue) / step).toInt().coerceAtLeast(1)
        fun progressFor(value: Float): Int =
            (((value - minValue) / step).toInt()).coerceIn(0, steps)
        fun valueFor(progress: Int): Float = minValue + progress * step
    }

    private val sliderSpecs: List<SliderSpec> by lazy {
        listOf(
            SliderSpec(-30f, 30f, 1f, 0f, R.string.preview_slider_body_rotation,
                writeToParams = { p, v -> p.copy(bodyRotationDeltaDeg = v) },
                readFromParams = { it.bodyRotationDeltaDeg }),
            SliderSpec(-20f, 20f, 1f, 0f, R.string.preview_slider_torso_tilt,
                writeToParams = { p, v -> p.copy(torsoTiltDeltaDeg = v) },
                readFromParams = { it.torsoTiltDeltaDeg }),
            SliderSpec(-30f, 30f, 1f, 0f, R.string.preview_slider_shoulder,
                writeToParams = { p, v -> p.copy(rightShoulderAngleDeltaDeg = v) },
                readFromParams = { it.rightShoulderAngleDeltaDeg }),
            SliderSpec(-45f, 45f, 1f, 0f, R.string.preview_slider_elbow_angle,
                writeToParams = { p, v -> p.copy(rightElbowAngleDeltaDeg = v) },
                readFromParams = { it.rightElbowAngleDeltaDeg }),
            SliderSpec(-0.1f, 0.1f, 0.005f, 0f, R.string.preview_slider_elbow_x,
                writeToParams = { p, v -> p.copy(rightElbowXOffset = v) },
                readFromParams = { it.rightElbowXOffset }),
            SliderSpec(-40f, 40f, 1f, 0f, R.string.preview_slider_wrist,
                writeToParams = { p, v -> p.copy(rightWristAngleDeltaDeg = v) },
                readFromParams = { it.rightWristAngleDeltaDeg }),
            SliderSpec(-30f, 30f, 1f, 0f, R.string.preview_slider_knee_bend,
                writeToParams = { p, v -> p.copy(kneeBendDeltaDeg = v) },
                readFromParams = { it.kneeBendDeltaDeg })
        )
    }

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
            binding.overlayView.setResults(
                emptyList(),
                FIXTURE_IMAGE_HEIGHT,
                FIXTURE_IMAGE_WIDTH,
                RunningMode.VIDEO
            )
        }
        binding.tvCameraHint.text = getString(R.string.preview_camera_hint, viewCameraYawDeg)

        buildSliderViews()
        wireTransport()

        lifecycleScope.launch { loadAll() }
    }

    private suspend fun loadAll() {
        val loaded = runCatching {
            CanonicalStrokeLoader.loadForehandDrive(this@BaselinePreviewActivity)
        }.getOrElse {
            Log.e(TAG, "Failed to load canonical stroke", it)
            return
        }
        meanStrokeFrames = loaded.frames
        frameIntervalMs = loaded.intervalMs
        binding.tvStatus.text = if (loaded.strokeCount > 0) {
            getString(R.string.preview_status_mean, loaded.strokeCount, loaded.meanStrokeLength)
        } else {
            getString(R.string.preview_status_raw_fallback)
        }
        binding.seekTimeline.max = (meanStrokeFrames.size - 1).coerceAtLeast(0)
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

        params = drillConfigRepo.load(DRILL_TYPE)
        syncSlidersToParams()
        renderFrame(0)
        play()
    }

    private fun wireTransport() {
        binding.btnPlayPause.setOnClickListener { if (isPlaying) pause() else play() }
        binding.btnReset.setOnClickListener {
            params = PoseTransformer.EditableParams()
            syncSlidersToParams()
            renderFrame(currentFrame)
        }
        binding.btnSave.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { drillConfigRepo.save(DRILL_TYPE, params) }
                Toast.makeText(this@BaselinePreviewActivity, R.string.preview_saved_toast, Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnExport.setOnClickListener { exportParams() }
    }

    private fun play() {
        if (meanStrokeFrames.isEmpty()) return
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
            if (!isPlaying || meanStrokeFrames.isEmpty()) return
            val next = (currentFrame + 1) % meanStrokeFrames.size
            renderFrame(next)
            binding.seekTimeline.progress = next
            handler.postDelayed(this, frameIntervalMs)
        }
    }

    private fun renderFrame(index: Int) {
        if (meanStrokeFrames.isEmpty()) return
        currentFrame = index.coerceIn(0, meanStrokeFrames.size - 1)
        val source = meanStrokeFrames[currentFrame]
        val transformed = PoseTransformer.apply(source, params, viewCameraYawDeg)
        binding.overlayView.setPoseFrame(transformed)
        binding.tvFrameLabel.text = getString(
            R.string.preview_frame_label,
            currentFrame + 1,
            meanStrokeFrames.size
        )
    }

    private fun buildSliderViews() {
        val parent = binding.llParamSliders
        parent.removeAllViews()
        for ((i, spec) in sliderSpecs.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this).apply {
                id = View.generateViewId()
                textSize = 13f
            }
            val bar = SeekBar(this).apply {
                id = View.generateViewId()
                max = spec.steps
                progress = spec.progressFor(spec.readFromParams(params))
                tag = i
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val sliderIdx = seekBar?.tag as? Int ?: return
                        val value = sliderSpecs[sliderIdx].valueFor(progress)
                        params = sliderSpecs[sliderIdx].writeToParams(params, value)
                        refreshSliderLabel(sliderIdx)
                        renderFrame(currentFrame)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }
            row.addView(label)
            row.addView(bar)
            parent.addView(row)
        }
        for (i in sliderSpecs.indices) refreshSliderLabel(i)
    }

    private fun syncSlidersToParams() {
        val parent = binding.llParamSliders
        for (i in sliderSpecs.indices) {
            val row = parent.getChildAt(i) as? LinearLayout ?: continue
            val bar = row.getChildAt(1) as? SeekBar ?: continue
            bar.progress = sliderSpecs[i].progressFor(sliderSpecs[i].readFromParams(params))
            refreshSliderLabel(i)
        }
    }

    private fun refreshSliderLabel(index: Int) {
        val parent = binding.llParamSliders
        val row = parent.getChildAt(index) as? LinearLayout ?: return
        val label = row.getChildAt(0) as? TextView ?: return
        val spec = sliderSpecs[index]
        label.text = getString(spec.labelFormatRes, spec.readFromParams(params))
    }

    private fun exportParams() {
        val json = JSONObject().apply {
            put("drillType", DRILL_TYPE)
            put("viewCameraYawDeg", viewCameraYawDeg.toDouble())
            put("bodyRotationDeltaDeg", params.bodyRotationDeltaDeg.toDouble())
            put("torsoTiltDeltaDeg", params.torsoTiltDeltaDeg.toDouble())
            put("rightShoulderAngleDeltaDeg", params.rightShoulderAngleDeltaDeg.toDouble())
            put("rightElbowAngleDeltaDeg", params.rightElbowAngleDeltaDeg.toDouble())
            put("rightElbowXOffset", params.rightElbowXOffset.toDouble())
            put("rightWristAngleDeltaDeg", params.rightWristAngleDeltaDeg.toDouble())
            put("kneeBendDeltaDeg", params.kneeBendDeltaDeg.toDouble())
        }
        val payload = json.toString(2)
        Log.i("BaselineDump", payload)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("drill_overrides", payload))
        Toast.makeText(this, R.string.preview_exported_toast, Toast.LENGTH_SHORT).show()
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
        private const val DRILL_TYPE = "forehand_drive"
        // Fixture video was 720×1280 (portrait). These must match the fixture
        // header so OverlayView scales poses into the same coordinate system.
        private const val FIXTURE_IMAGE_WIDTH = 720
        private const val FIXTURE_IMAGE_HEIGHT = 1280
    }
}
