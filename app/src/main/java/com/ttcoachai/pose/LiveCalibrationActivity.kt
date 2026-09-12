package com.ttcoachai.pose

// LiveCalibrationActivity.kt
//
// Real, shippable calibration screen — produces the "forehand_drive_rtm" baseline that
// TrainingActivity.decideCameraModeAndStart requires before a live RTMPose-coached drive can
// start. Unlike LiveDrillActivity (the dev-only debug tool this flow is lifted from), this
// Activity is NOT FLAG_DEBUGGABLE-gated — it is the only in-app path a real player has to
// calibrate the RTM lineage (CalibrationActivity writes the older/legacy baseline lineage
// instead, see project CLAUDE.md "RTM correction taxonomy").
//
// Reuses PoseBackendFactory (MediaPipe) / LivePoseFrameProcessor / Coco17OverlayView and the
// CameraX binding technique from LiveDrillActivity.bindCameraUseCases() /
// LiveTrainingController — no inference logic is duplicated here.
//
// Flow (mirrors calibration/CalibrationActivity's onboarding -> capture -> review shape, as
// one Activity with visibility-toggled panels instead of a fragment host):
//   INSTRUCTIONS — camera + live Coco17 skeleton so the player can confirm their whole body,
//                  including knees/ankles, is tracked before recording.
//   RECORDING    — buffers raw PoseFrame2D while the player performs their forehand drives.
//   PROCESSING   — DrillCalibrator.calibrateChecked(...) off the buffered sequence.
//   RESULT       — success (baseline saved) or failure (camera-placement / other), with retry.

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ttcoachai.R
import com.ttcoachai.databinding.ActivityLiveCalibrationBinding
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.repository.PersonalBaselineRepository
import com.ttcoachai.shared.drill.CalibrationOutcome
import com.ttcoachai.shared.drill.DrillCalibrator
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Topology
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LiveCalibrationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LiveCalibrationAct"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 43

        /** Matches the legacy CalibrationStateManager.MIN_REPS_TO_PERSIST floor — this is a
         *  real player-facing flow, not the dev tool's minRepCount=3 convenience. */
        private const val MIN_REP_COUNT = 10
    }

    private enum class Screen { INSTRUCTIONS, RECORDING, PROCESSING, RESULT }

    private lateinit var binding: ActivityLiveCalibrationBinding

    private val repository by lazy {
        PersonalBaselineRepository(AppDatabase.getDatabase(this).personalBaselineDao())
    }

    // Same hardcoded default as LiveDrillActivity/LiveTrainingController — no shared-
    // Handedness picker exists yet in Settings (see those classes' TODOs on this exact gap).
    private val handedness = Handedness.RIGHT

    private var screen = Screen.INSTRUCTIONS

    private var backend: PoseBackend? = null
    private var backendErrorMessage: String? = null
    private var processor: LivePoseFrameProcessor? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analysisExecutor: ExecutorService? = null

    /** Analysis-frame aspect ratio (rotated width/height), captured from the first analyzed
     *  frame — same technique as LiveDrillActivity/LiveTrainingController. */
    @Volatile private var aspectRatio: Float = 3f / 4f

    private val calibrationFrames = mutableListOf<PoseFrame2D>()
    private var recording = false

    /** True while the RESULT screen's primary button should retry (go back to INSTRUCTIONS)
     *  rather than close the screen. */
    private var resultCanRetry = false
    private var savedBaseline = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.live_calibration_title)
            setDisplayHomeAsUpEnabled(true)
        }

        analysisExecutor = Executors.newSingleThreadExecutor()

        backend = try {
            PoseBackendFactory.create(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to construct pose backend", e)
            backendErrorMessage = e.message ?: "unknown error"
            null
        }

        val activeBackend = backend
        if (activeBackend != null) {
            processor = LivePoseFrameProcessor(activeBackend, mirror = false) { keypoints, timestampMs ->
                onPoseResult(keypoints, timestampMs)
            }
        }

        binding.btnStartRecording.setOnClickListener { startRecording() }
        binding.btnCancelRecording.setOnClickListener { cancelRecording() }
        binding.btnFinishRecording.setOnClickListener { finishRecording() }
        binding.btnResultPrimary.setOnClickListener { onResultPrimaryClicked() }

        setupBackNavigation()

        if (activeBackend == null) {
            showBackendUnavailable()
        } else {
            renderScreen()
            if (hasCameraPermission()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE
                )
            }
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
                Toast.makeText(this, R.string.live_calibration_camera_permission_denied, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // MARK: - CameraX (copied technique from LiveDrillActivity.bindCameraUseCases)

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
        val executor = analysisExecutor ?: return
        val rotation = binding.previewView.display?.rotation ?: android.view.Surface.ROTATION_0

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(executor) { imageProxy ->
                    val rot = imageProxy.imageInfo.rotationDegrees
                    val rotatedWidth = if (rot % 180 != 0) imageProxy.height else imageProxy.width
                    val rotatedHeight = if (rot % 180 != 0) imageProxy.width else imageProxy.height
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
            binding.overlayView.setKeypoints(keypoints)
            if (recording) {
                calibrationFrames.add(
                    PoseFrame2D(frameIndex = calibrationFrames.size, timestampMs = timestampMs, keypoints = keypoints)
                )
            }
        }
    }

    // MARK: - Recording flow

    private fun startRecording() {
        calibrationFrames.clear()
        recording = true
        screen = Screen.RECORDING
        renderScreen()
    }

    private fun cancelRecording() {
        recording = false
        calibrationFrames.clear()
        screen = Screen.INSTRUCTIONS
        renderScreen()
    }

    private fun finishRecording() {
        recording = false
        val frames = calibrationFrames.toList()
        if (frames.size < 2) {
            showFailureResult(getString(R.string.live_calibration_not_enough_frames))
            return
        }

        screen = Screen.PROCESSING
        renderScreen()

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
                drillType = LiveDrillActivity.DRILL_TYPE,
                createdAtMs = System.currentTimeMillis(),
                handedness = handedness,
                minRepCount = MIN_REP_COUNT,
                // No override: let CameraAngleEstimator resolve yaw per rep from the footage
                // itself, so a genuinely misplaced camera actually trips CalibrationOutcome.
                // PlacementError instead of being silently accepted (unlike LiveDrillActivity's
                // dev-tool hardcode of 0f).
                cameraYawDeg = null
            )

            when (outcome) {
                is CalibrationOutcome.Success -> {
                    repository.saveBaseline(outcome.baseline)
                    showSuccessResult(outcome.baseline.repCount)
                }
                is CalibrationOutcome.PlacementError -> {
                    showFailureResult(getString(R.string.live_calibration_failed_placement, outcome.message))
                }
                is CalibrationOutcome.Failed -> {
                    showFailureResult(getString(R.string.live_calibration_failed_generic, outcome.message))
                }
            }
        }
    }

    /** Median of consecutive timestamp deltas — same technique LiveDrillActivity uses
     *  locally (PoseSequence2D needs one interval up front; not imported from that frozen-
     *  adjacent dev tool to avoid coupling this shippable screen to it beyond DRILL_TYPE). */
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

    // MARK: - Result screen

    private fun showSuccessResult(repCount: Int) {
        savedBaseline = true
        resultCanRetry = false
        binding.tvResultTitle.text = getString(R.string.live_calibration_success_title)
        binding.tvResultBody.text = getString(R.string.live_calibration_success_body, repCount)
        binding.btnResultPrimary.text = getString(R.string.live_calibration_done_button)
        screen = Screen.RESULT
        renderScreen()
    }

    private fun showFailureResult(message: String) {
        resultCanRetry = true
        binding.tvResultTitle.text = getString(R.string.live_calibration_failed_title)
        binding.tvResultBody.text = message
        binding.btnResultPrimary.text = getString(R.string.live_calibration_retry_button)
        screen = Screen.RESULT
        renderScreen()
    }

    private fun showBackendUnavailable() {
        resultCanRetry = false
        binding.tvResultTitle.text = getString(R.string.live_calibration_failed_title)
        binding.tvResultBody.text = getString(
            R.string.live_drill_backend_unavailable, backendErrorMessage ?: "unknown error"
        )
        binding.btnResultPrimary.text = getString(R.string.live_calibration_done_button)
        screen = Screen.RESULT
        renderScreen()
    }

    private fun onResultPrimaryClicked() {
        if (resultCanRetry) {
            calibrationFrames.clear()
            screen = Screen.INSTRUCTIONS
            renderScreen()
        } else {
            setResult(if (savedBaseline) RESULT_OK else RESULT_CANCELED)
            finish()
        }
    }

    // MARK: - Screen state

    private fun renderScreen() {
        binding.groupInstructions.visibility = if (screen == Screen.INSTRUCTIONS) View.VISIBLE else View.GONE
        binding.groupRecording.visibility = if (screen == Screen.RECORDING) View.VISIBLE else View.GONE
        binding.groupProcessing.visibility = if (screen == Screen.PROCESSING) View.VISIBLE else View.GONE
        binding.groupResult.visibility = if (screen == Screen.RESULT) View.VISIBLE else View.GONE
    }

    // MARK: - Back navigation

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (screen == Screen.RECORDING) {
                    cancelRecording()
                } else {
                    setResult(if (savedBaseline) RESULT_OK else RESULT_CANCELED)
                    finish()
                }
            }
        })
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // MARK: - Lifecycle cleanup

    /** Closes [processor] and [backend]. Must only run as a task on [analysisExecutor] (or, when
     *  that executor was never created, directly) — see [onDestroy]. */
    private fun closeBackendNow() {
        processor?.close()
        (backend as? AutoCloseable)?.close()
        backend = null
        processor = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop new frames first, then tear down the backend as a task ON analysisExecutor — the
        // SAME single-thread executor that runs analyze() for every frame (see
        // bindCameraUseCases()) — so close can never run concurrently with an in-flight
        // analyze(). Without this, a frame could still be inside backend.estimatePose() (native
        // MediaPipe detect()) while close() destroys it: a fatal SIGSEGV, not a catchable
        // exception (same hazard/fix as PoseBenchmarkActivity.onDestroy()/switchBackend()).
        // shutdown() (not shutdownNow()) lets this queued task run to completion before the
        // executor terminates, without blocking this (main) thread on it.
        cameraProvider?.unbindAll()
        val executor = analysisExecutor
        if (executor != null) {
            executor.execute { closeBackendNow() }
            executor.shutdown()
        } else {
            closeBackendNow()
        }
    }
}
