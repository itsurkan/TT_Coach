package com.ttcoachai.calibration

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.ttcoachai.BaseActivity
import com.ttcoachai.PoseLandmarkerHelper
import com.ttcoachai.R
import com.ttcoachai.TTCoachApplication
import com.ttcoachai.databinding.ActivityCalibrationBinding
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.fragment.CameraFragment
import com.ttcoachai.managers.CalibrationStateManager
import com.ttcoachai.managers.TrainingStateManager
import com.ttcoachai.processors.PoseAnalysisProcessor
import com.ttcoachai.repository.PersonalBaselineRepository
import com.ttcoachai.services.FeedbackGenerator
import com.ttcoachai.services.MotionAnalyzer
import com.ttcoachai.shared.models.ExerciseParameters
import com.ttcoachai.shared.models.PersonalBaseline
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts the calibration flow: onboarding → capture → review.
 *
 * Mirrors the TrainingActivity shape (implements LandmarkerListener, owns a
 * PoseAnalysisProcessor) but operates in Mode.CALIBRATION and feeds a
 * CalibrationStateManager instead of the live feedback pipeline.
 *
 * CameraFragment is inserted into `camera_preview_container` once on create
 * and lives through all three phases; the `calibration_fragment_host` overlays
 * the current phase UI on top of the camera.
 */
class CalibrationActivity : BaseActivity(), PoseLandmarkerHelper.LandmarkerListener {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var poseAnalysisProcessor: PoseAnalysisProcessor

    val calibrationStateManager: CalibrationStateManager = CalibrationStateManager.getInstance()
    val baselineRepository: PersonalBaselineRepository by lazy {
        PersonalBaselineRepository(AppDatabase.getDatabase(this).personalBaselineDao())
    }

    lateinit var drillType: String
        private set

    companion object {
        private const val TAG = "CalibrationActivity"

        // MVP: hardcoded drill type per tasks.md T015 note — handedness selector deferred.
        const val DRILL_FOREHAND_SHADOW = "forehand_shadow"

        /** Optional intent extra to calibrate a specific drill type (e.g. `custom_<ts>`). */
        const val EXTRA_DRILL_TYPE = "drill_type"
        /** Result extra: drill type that was saved (only on RESULT_OK). */
        const val RESULT_EXTRA_DRILL_TYPE = "drill_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drillType = intent.getStringExtra(EXTRA_DRILL_TYPE) ?: DRILL_FOREHAND_SHADOW

        initializeAnalysis()
        setupToolbar()
        setupBackNavigation()

        if (savedInstanceState == null) {
            hostCameraFragment()
            lifecycleScope.launch { showOnboardingWithBaselineContext() }
        }
    }

    private suspend fun showOnboardingWithBaselineContext() {
        val existing: PersonalBaseline? = baselineRepository.getActiveBaseline(drillType).first()
        val args = Bundle().apply {
            if (existing != null) {
                putInt(CalibrationOnboardingFragment.ARG_EXISTING_REP_COUNT, existing.repCount)
                putLong(CalibrationOnboardingFragment.ARG_EXISTING_CREATED_AT_MS, existing.createdAtMs)
            }
        }
        val fragment = CalibrationOnboardingFragment().apply { arguments = args }
        showFragment(fragment)
    }

    private fun initializeAnalysis() {
        // Reuse forehand_drive parameters as a stand-in for forehand_shadow metric extraction.
        // Calibration doesn't score, it collects raw AnalysisResult streams, so the thresholds
        // here only affect the overallScore / feedbackItems fields which we ignore in this mode.
        val params = ExerciseParameters(exerciseId = "forehand_drive")

        poseAnalysisProcessor = PoseAnalysisProcessor(
            application = application as TTCoachApplication,
            motionAnalyzer = MotionAnalyzer(params),
            feedbackGenerator = FeedbackGenerator(this),
            stateManager = TrainingStateManager.getInstance(this),
            onUIUpdate = { /* no-op — capture fragment binds directly to state manager flow */ },
            calibrationStateManager = calibrationStateManager
        ).apply { mode = PoseAnalysisProcessor.Mode.CALIBRATION }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.calibration_title)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun hostCameraFragment() {
        supportFragmentManager.beginTransaction()
            .replace(binding.cameraPreviewContainer.id, CameraFragment())
            .commit()
    }

    private fun showFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(binding.calibrationFragmentHost.id, fragment)
            .commit()
    }

    fun beginCapture() {
        calibrationStateManager.startSession(
            drillType = drillType,
            targetRepCount = CalibrationStateManager.DEFAULT_TARGET_REPS
        )
        poseAnalysisProcessor.startSession(drillType, getString(R.string.calibration_title))
        showFragment(CalibrationCaptureFragment())
    }

    fun finishCapture() {
        calibrationStateManager.finishCapture()
        poseAnalysisProcessor.endSession()
        showFragment(CalibrationReviewFragment())
    }

    fun redoCalibration() {
        calibrationStateManager.discardSession()
        lifecycleScope.launch { showOnboardingWithBaselineContext() }
    }

    fun exitAfterSave() {
        Toast.makeText(this, R.string.calibration_saved_toast, Toast.LENGTH_SHORT).show()
        calibrationStateManager.discardSession()
        setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_EXTRA_DRILL_TYPE, drillType))
        finish()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val accepted = calibrationStateManager.acceptedRepCount.value
                if (accepted == 0) {
                    calibrationStateManager.discardSession()
                    finish()
                    return
                }
                AlertDialog.Builder(this@CalibrationActivity)
                    .setTitle(R.string.calibration_discard_title)
                    .setMessage(
                        getString(
                            R.string.calibration_discard_message,
                            accepted,
                            CalibrationStateManager.MIN_REPS_TO_PERSIST
                        )
                    )
                    .setNegativeButton(R.string.dialog_no, null)
                    .setPositiveButton(R.string.dialog_yes) { _, _ ->
                        calibrationStateManager.discardSession()
                        finish()
                    }
                    .show()
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::poseAnalysisProcessor.isInitialized) poseAnalysisProcessor.release()
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "Pose detection error: $error (code: $errorCode)")
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        poseAnalysisProcessor.processResults(resultBundle)
    }
}
