package com.ttcoachai.pose

// PoseBenchmarkActivity.kt
//
// THROWAWAY PROTOTYPE, dev-only: live A/B FPS bench between RtmposeBackend and MoveNetBackend.
// Same FLAG_DEBUGGABLE self-guard + exported="true" pattern as RtmposeDrillActivity so it can be
// launched directly via adb. Built programmatically (no new layout XML) — this is a throwaway
// measurement tool, not shippable UI.
//
// Reuses RtmposeFrameProcessor and Coco17OverlayView UNMODIFIED per freeze discipline; only new
// code is the toggle + FPS readout wiring.

import android.content.pm.ApplicationInfo
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import com.google.mediapipe.tasks.core.Delegate
import com.ttcoachai.shared.models.Keypoint2D
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PoseBenchmarkActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PoseBenchmarkActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 43
    }

    private enum class BackendKind {
        RTMPOSE_LITE, MOVENET,
        MEDIAPIPE_LITE_CPU, MEDIAPIPE_LITE_GPU,
        MEDIAPIPE_FULL_CPU, MEDIAPIPE_FULL_GPU
    }

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: Coco17OverlayView
    private lateinit var fpsText: TextView
    private lateinit var toggleButton: Button

    private var activeKind = BackendKind.RTMPOSE_LITE
    private var backend: PoseBackend? = null
    private var processor: RtmposeFrameProcessor? = null

    private val fpsTracker = FpsTracker()
    @Volatile private var frameStartNanos: Long = 0L

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private lateinit var analysisExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }

        setContentView(buildLayout())

        analysisExecutor = Executors.newSingleThreadExecutor()

        switchBackend(BackendKind.RTMPOSE_LITE)

        toggleButton.setOnClickListener {
            val next = when (activeKind) {
                BackendKind.RTMPOSE_LITE -> BackendKind.MOVENET
                BackendKind.MOVENET -> BackendKind.MEDIAPIPE_LITE_CPU
                BackendKind.MEDIAPIPE_LITE_CPU -> BackendKind.MEDIAPIPE_LITE_GPU
                BackendKind.MEDIAPIPE_LITE_GPU -> BackendKind.MEDIAPIPE_FULL_CPU
                BackendKind.MEDIAPIPE_FULL_CPU -> BackendKind.MEDIAPIPE_FULL_GPU
                BackendKind.MEDIAPIPE_FULL_GPU -> BackendKind.RTMPOSE_LITE
            }
            switchBackend(next)
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    // MARK: - Programmatic layout (throwaway UI, no XML)

    private fun buildLayout(): ViewGroup {
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(previewView)

        overlayView = Coco17OverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(overlayView)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
        }

        fpsText = TextView(this).apply {
            setTextColor(Color.GREEN)
            textSize = 20f
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            setPadding(16, 8, 16, 8)
            text = "backend: RTMPose-lite\nfps: --"
        }
        controls.addView(fpsText)

        toggleButton = Button(this).apply {
            text = "Switch backend"
        }
        controls.addView(toggleButton)

        root.addView(controls)
        return root
    }

    // MARK: - Backend switching

    private fun switchBackend(kind: BackendKind) {
        processor?.close()
        (backend as? AutoCloseable)?.close()
        backend = null
        processor = null
        fpsTracker.reset()

        val newBackend: PoseBackend? = try {
            when (kind) {
                BackendKind.RTMPOSE_LITE -> RtmposeBackend(
                    context = this,
                    yoloxAssetName = "yolox_tiny_8xb8-300e_humanart-6f3252f9.onnx",
                    rtmposeAssetName = "rtmpose-s_simcc-body7_pt-body7_420e-256x192-acd4a1ef_20230504.onnx",
                    detInputSize = 416
                )
                BackendKind.MOVENET -> MoveNetBackend(this)
                BackendKind.MEDIAPIPE_LITE_CPU -> MediaPipePoseLandmarkerBackend(
                    context = this, modelAssetName = "pose_landmarker_lite.task", delegate = Delegate.CPU)
                BackendKind.MEDIAPIPE_LITE_GPU -> MediaPipePoseLandmarkerBackend(
                    context = this, modelAssetName = "pose_landmarker_lite.task", delegate = Delegate.GPU)
                BackendKind.MEDIAPIPE_FULL_CPU -> MediaPipePoseLandmarkerBackend(
                    context = this, modelAssetName = "pose_landmarker_full.task", delegate = Delegate.CPU)
                BackendKind.MEDIAPIPE_FULL_GPU -> MediaPipePoseLandmarkerBackend(
                    context = this, modelAssetName = "pose_landmarker_full.task", delegate = Delegate.GPU)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to construct backend $kind", e)
            Toast.makeText(this, "Backend init failed: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }

        activeKind = kind
        backend = newBackend
        if (newBackend != null) {
            processor = RtmposeFrameProcessor(newBackend, mirror = false) { keypoints, timestampMs ->
                onPoseResult(keypoints, timestampMs)
            }
        }
        runOnUiThread {
            fpsText.text = "backend: ${displayName(kind)}\nfps: --"
        }
    }

    private fun displayName(kind: BackendKind): String = when (kind) {
        BackendKind.RTMPOSE_LITE -> "RTMPose-lite"
        BackendKind.MOVENET -> "movenet"
        BackendKind.MEDIAPIPE_LITE_CPU -> "MediaPipe Lite (CPU)"
        BackendKind.MEDIAPIPE_LITE_GPU -> "MediaPipe Lite (GPU)"
        BackendKind.MEDIAPIPE_FULL_CPU -> "MediaPipe Full (CPU)"
        BackendKind.MEDIAPIPE_FULL_GPU -> "MediaPipe Full (GPU)"
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

    // MARK: - CameraX (copied technique from RtmposeDrillActivity)

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
                    frameStartNanos = System.nanoTime()
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

    // MARK: - Pose result -> FPS

    private fun onPoseResult(keypoints: List<Keypoint2D>, timestampMs: Long) {
        val elapsedMs = (System.nanoTime() - frameStartNanos) / 1_000_000L
        fpsTracker.tick(elapsedMs)
        runOnUiThread {
            overlayView.setKeypoints(keypoints)
            fpsText.text = "backend: ${displayName(activeKind)}\nfps: %.1f".format(fpsTracker.averageFps)
        }
    }

    // MARK: - Lifecycle cleanup

    override fun onDestroy() {
        super.onDestroy()
        processor?.close()
        (backend as? AutoCloseable)?.close()
        analysisExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}
