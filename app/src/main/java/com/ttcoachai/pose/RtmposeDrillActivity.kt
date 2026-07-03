package com.ttcoachai.pose

// RtmposeDrillActivity.kt
//
// Phase 3 (P4c) integration Activity: the live forehand-drive drill on RTMPose. Ties together
// the already-committed pieces — RtmposeBackend, RtmposeFrameProcessor (P4b), Coco17OverlayView
// (P4a), DrillTtsController (P4a), and the shared LiveDrillSession / DrillCalibrator /
// PersonalBaselineRepository — none of whose logic is reimplemented here. This class only binds
// CameraX (copied technique from CameraManager.bindCameraUseCases) and wires the two live modes.
//
// Dev-only debug entry point (Slice pattern from DesignSystemPreviewActivity /
// BaselineDebugActivity): registered `exported="true"` in the manifest, self-guards on
// ApplicationInfo.FLAG_DEBUGGABLE so a release APK can't launch it.
//
// drillType is ALWAYS "forehand_drive_rtm" (Part 3 decision — distinct baseline lineage from the
// legacy MediaPipe "forehand_drive"/"forehand_shadow" drills).

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ttcoachai.R
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.repository.PersonalBaselineRepository
import com.ttcoachai.shared.drill.CalibrationOutcome
import com.ttcoachai.shared.drill.DrillCalibrator
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.drill.LiveDrillSession
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Topology
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RtmposeDrillActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RtmposeDrillActivity"

        /** Distinct from the legacy MediaPipe "forehand_drive"/"forehand_shadow" lineage. */
        const val DRILL_TYPE = "forehand_drive_rtm"

        private const val CAMERA_PERMISSION_REQUEST_CODE = 42
    }

    /** Two-mode state machine. CALIBRATE buffers frames for a baseline; FEEDBACK runs the
     *  live coaching loop against an already-saved baseline. Starts in CALIBRATE — there is
     *  nothing to give feedback against until a baseline exists. */
    private enum class Mode { CALIBRATE, FEEDBACK }

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: Coco17OverlayView
    private lateinit var statusText: TextView
    private lateinit var btnPrimary: Button
    private lateinit var btnSwitchMode: Button

    private val repository by lazy {
        PersonalBaselineRepository(AppDatabase.getDatabase(this).personalBaselineDao())
    }

    // Defaults per brief: no trivial SettingsManager mapping to shared Handedness/FeedbackLang
    // exists (SettingsManager.isPlayingHandRight()/getCoachLanguage() use different value
    // spaces), so this compile-pass wiring hardcodes them.
    // TODO pre-drill picker: surface handedness + language selection before starting a session.
    private val handedness = Handedness.RIGHT
    private val lang = FeedbackLang.EN

    private var mode = Mode.CALIBRATE

    private var backend: PoseBackend? = null
    private var processor: RtmposeFrameProcessor? = null
    private var ttsController: DrillTtsController? = null
    private var liveSession: LiveDrillSession? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private lateinit var analysisExecutor: ExecutorService

    /** Analysis-frame aspect ratio (rotated width/height), captured from the first analyzed
     *  frame — feeds both LiveDrillSession and the calibration PoseSequence2D. */
    @Volatile private var aspectRatio: Float = 3f / 4f

    /** In-memory calibration buffer: raw frames + their real timestamps. */
    private val calibrationFrames = mutableListOf<PoseFrame2D>()

    private var calibrating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }

        setContentView(R.layout.activity_rtmpose_drill)
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        statusText = findViewById(R.id.statusText)
        btnPrimary = findViewById(R.id.btnPrimary)
        btnSwitchMode = findViewById(R.id.btnSwitchMode)

        analysisExecutor = Executors.newSingleThreadExecutor()

        backend = try {
            RtmposeBackend(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to construct RtmposeBackend", e)
            statusText.text = getString(R.string.rtmpose_drill_backend_unavailable, e.message ?: "unknown error")
            null
        }

        val activeBackend = backend
        if (activeBackend != null) {
            processor = RtmposeFrameProcessor(activeBackend, mirror = false) { keypoints, timestampMs ->
                onPoseResult(keypoints, timestampMs)
            }
        }

        btnPrimary.setOnClickListener { onPrimaryButtonClicked() }
        btnSwitchMode.setOnClickListener { onSwitchModeClicked() }

        renderModeUi()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    // MARK: - Permission

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // MARK: - CameraX (copied technique from CameraManager.bindCameraUseCases; this Activity
    // binds CameraX itself rather than reusing the frozen manager/fragment)

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(analysisExecutor) { imageProxy ->
                    // Capture the rotated frame's aspect ratio once (LiveDrillSession/
                    // PoseSequence2D need one aspectRatio; it does not change frame to frame).
                    // RtmposeFrameProcessor computes this same rotated width/height internally
                    // but does not expose it via onPose, so it is derived here from the same
                    // ImageProxy fields without duplicating its bitmap/matrix logic.
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val rotatedWidth = if (rotation % 180 != 0) imageProxy.height else imageProxy.width
                    val rotatedHeight = if (rotation % 180 != 0) imageProxy.width else imageProxy.height
                    if (rotatedHeight > 0) {
                        aspectRatio = rotatedWidth.toFloat() / rotatedHeight.toFloat()
                    }
                    processor?.analyze(imageProxy) ?: imageProxy.close()
                }
            }

        provider.unbindAll()
        try {
            camera = provider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
        } catch (e: Exception) {
            Log.e(TAG, "CameraX bind failed", e)
        }
    }

    // MARK: - Pose result dispatch (analyzer thread -> main thread)

    private fun onPoseResult(keypoints: List<Keypoint2D>, timestampMs: Long) {
        runOnUiThread {
            overlayView.setKeypoints(keypoints)
            when (mode) {
                Mode.CALIBRATE -> onCalibrationFrame(keypoints, timestampMs)
                Mode.FEEDBACK -> onFeedbackFrame(keypoints, timestampMs)
            }
        }
    }

    // MARK: - CALIBRATE mode

    private fun onCalibrationFrame(keypoints: List<Keypoint2D>, timestampMs: Long) {
        if (!calibrating) return
        calibrationFrames.add(
            PoseFrame2D(frameIndex = calibrationFrames.size, timestampMs = timestampMs, keypoints = keypoints)
        )
    }

    private fun onPrimaryButtonClicked() {
        when (mode) {
            Mode.CALIBRATE -> {
                if (!calibrating) {
                    // Start capturing.
                    calibrationFrames.clear()
                    calibrating = true
                    statusText.text = getString(R.string.rtmpose_drill_status_calibrate)
                } else {
                    finishCalibration()
                }
            }
            Mode.FEEDBACK -> { /* no primary action in FEEDBACK mode */ }
        }
    }

    private fun finishCalibration() {
        calibrating = false
        val frames = calibrationFrames.toList()
        if (frames.size < 2) {
            statusText.text = getString(R.string.rtmpose_drill_calibration_failed, "not enough frames captured")
            return
        }

        statusText.text = getString(R.string.rtmpose_drill_calibrating_busy)
        val ratio = aspectRatio
        val interval = medianIntervalMs(frames)

        lifecycleScope.launch {
            val sequence = PoseSequence2D(
                topology = Topology.COCO17,
                model = "rtmpose",
                videoName = "live-calibration",
                intervalMs = interval,
                totalFrames = frames.size,
                videoDurationMs = frames.last().timestampMs - frames.first().timestampMs,
                videoWidth = (ratio * 1000).toInt().coerceAtLeast(1),
                videoHeight = 1000,
                frames = frames
            )

            val outcome = DrillCalibrator.calibrateChecked(
                sequence = sequence,
                drillType = DRILL_TYPE,
                createdAtMs = System.currentTimeMillis(),
                handedness = handedness,
                minRepCount = 3,
                cameraYawDeg = 0f
            )

            when (outcome) {
                is CalibrationOutcome.Success -> {
                    repository.saveBaseline(outcome.baseline)
                    statusText.text = getString(
                        R.string.rtmpose_drill_calibration_saved, outcome.baseline.repCount
                    )
                }
                is CalibrationOutcome.PlacementError -> {
                    statusText.text = getString(R.string.rtmpose_drill_calibration_failed, outcome.message)
                }
                is CalibrationOutcome.Failed -> {
                    statusText.text = getString(R.string.rtmpose_drill_calibration_failed, outcome.message)
                }
            }
        }
    }

    /** Median of consecutive timestamp deltas — same idea LiveDrillSession uses internally,
     *  reimplemented locally since PoseSequence2D needs one interval up front. */
    private fun medianIntervalMs(frames: List<PoseFrame2D>): Long {
        val deltas = ArrayList<Long>(frames.size - 1)
        for (i in 1 until frames.size) {
            deltas.add(frames[i].timestampMs - frames[i - 1].timestampMs)
        }
        if (deltas.isEmpty()) return 33L
        val sorted = deltas.sorted()
        val mid = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        return median.coerceAtLeast(1L)
    }

    // MARK: - FEEDBACK mode

    private fun onFeedbackFrame(keypoints: List<Keypoint2D>, timestampMs: Long) {
        val session = liveSession ?: return
        val feedback = session.onFrame(keypoints, timestampMs)
        val tts = ttsController ?: return
        for (item in feedback) {
            tts.speak(item)
        }
    }

    private fun onSwitchModeClicked() {
        when (mode) {
            Mode.CALIBRATE -> enterFeedbackMode()
            Mode.FEEDBACK -> enterCalibrateMode()
        }
    }

    private fun enterFeedbackMode() {
        lifecycleScope.launch {
            val baseline = repository.getActiveBaseline(DRILL_TYPE).first()
            if (baseline == null) {
                statusText.text = getString(R.string.rtmpose_drill_need_calibration)
                return@launch
            }

            liveSession = LiveDrillSession(
                baseline = baseline,
                aspectRatio = aspectRatio,
                handedness = handedness,
                lang = lang,
                cameraYawDeg = 0f
            )

            ttsController?.shutdown()
            ttsController = DrillTtsController(this@RtmposeDrillActivity, lang) { text ->
                runOnUiThread { statusText.text = text }
            }.also { it.init() }

            mode = Mode.FEEDBACK
            calibrating = false
            renderModeUi()
            statusText.text = getString(R.string.rtmpose_drill_status_feedback)
        }
    }

    private fun enterCalibrateMode() {
        mode = Mode.CALIBRATE
        calibrating = false
        calibrationFrames.clear()
        liveSession = null
        ttsController?.shutdown()
        ttsController = null
        renderModeUi()
        statusText.text = getString(R.string.rtmpose_drill_status_calibrate)
    }

    private fun renderModeUi() {
        when (mode) {
            Mode.CALIBRATE -> {
                btnPrimary.isEnabled = true
                btnPrimary.text = getString(R.string.rtmpose_drill_finish_calibration)
                btnSwitchMode.text = getString(R.string.rtmpose_drill_switch_to_feedback)
            }
            Mode.FEEDBACK -> {
                btnPrimary.isEnabled = false
                btnSwitchMode.text = getString(R.string.rtmpose_drill_status_calibrate)
            }
        }
    }

    // MARK: - Lifecycle cleanup

    override fun onDestroy() {
        super.onDestroy()
        processor?.close()
        ttsController?.shutdown()
        analysisExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}
