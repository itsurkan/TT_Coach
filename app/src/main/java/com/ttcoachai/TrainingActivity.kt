package com.ttcoachai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ttcoachai.databinding.ActivityTrainingBinding
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.managers.*
import com.ttcoachai.pose.RtmposeCalibrationActivity
import com.ttcoachai.pose.RtmposeTrainingController
import com.ttcoachai.repository.PersonalBaselineRepository
import com.ttcoachai.work.PoseUploadQueue
import java.io.File
import com.ttcoachai.shared.models.ExerciseParameters
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.analysis.BaselineRuleFactory
import com.ttcoachai.shared.drill.LocomotionFilter
import com.ttcoachai.shared.drill.ShippedBaselines
import com.ttcoachai.shared.drill.movements.ForehandDriveGeneral
import com.ttcoachai.ui.REFERENCE_STANDARD
import com.ttcoachai.ui.isCalibrationRequired
import com.ttcoachai.util.DrillReferenceResolver
import com.ttcoachai.util.PerPhaseTargetsCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TrainingActivity : BaseActivity() {
    private lateinit var binding: ActivityTrainingBinding
    private var exerciseId: String? = null
    private var exerciseName: String? = null

    private lateinit var stateManager: TrainingStateManager
    private lateinit var uiController: TrainingUIController
    private lateinit var mediaManager: TrainingMediaManager
    private lateinit var exerciseParameters: ExerciseParameters

    /** Non-null only when the RTMPose live path took over (see [decideCameraModeAndStart]).
     *  Null if the RTM controller failed to start (see [showCalibrationRequiredDialog]). */
    private var rtmController: RtmposeTrainingController? = null

    /**
     * All configured per-metric reference bands for this drill, decoded once in
     * [initializeAnalysis] and resolved (Task H — [com.ttcoachai.util.DrillReferenceResolver])
     * in [decideCameraModeAndStart]/[retryAfterCalibration]. Replaces the old single-metric
     * kneeBendStrikeBand extraction — every DrillMetrics key configured in the editor now flows
     * through, not just knee_bend.
     */
    private var drillMetricBands: Map<String, ClosedRange<Double>> = emptyMap()

    private var referenceTypeExtra: String? = null
    private var movementProfile: String? = null

    /** Launches [RtmposeCalibrationActivity] from the "calibration required" dialog (see
     *  [decideCameraModeAndStart]). Must be registered unconditionally before STARTED, so it
     *  lives as a property rather than being created inside the dialog callback. Result code
     *  is not load-bearing — [retryAfterCalibration] always re-checks the baseline directly,
     *  whether the player finished calibration, backed out, or hit an unrecoverable error. */
    private val calibrationLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            lifecycleScope.launch { retryAfterCalibration() }
        }

    companion object {
        private const val TAG = "TrainingActivity"

        /** Same forehand-family gate RtmposeDrillActivity's baseline lineage implies:
         *  the RTMPose path only has reference angles for the forehand drive. */
        private fun isForehandRtmEligible(exerciseId: String?): Boolean =
            exerciseId == null || exerciseId.startsWith("forehand") || exerciseId.startsWith("custom_")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        exerciseId = intent.getStringExtra("EXERCISE_ID")
        exerciseName = intent.getStringExtra("EXERCISE_NAME")
        referenceTypeExtra = intent.getStringExtra("REFERENCE_TYPE")
        movementProfile = intent.getStringExtra("MOVEMENT_PROFILE")

        initializeManagers()
        initializeAnalysis()
        setupUI()
        setupBackNavigation()
    }
    
    private fun initializeManagers() {
        stateManager = TrainingStateManager.getInstance(this)
        uiController = TrainingUIController(
            this, binding, SettingsManager(this), stateManager,
            ::toggleTraining,
            { stopTraining(discard = false) }
        )
        mediaManager = TrainingMediaManager(this, binding)
    }
    
    private fun initializeAnalysis() {
        val prefs = getSharedPreferences("ai_coach_prefs", MODE_PRIVATE)
        exerciseParameters = when (exerciseId) {
            "forehand_drive" -> ExerciseParameters(
                exerciseId = "forehand_drive",
                idealWristAngle = prefs.getInt("ideal_wrist_angle", 180).toFloat(),
                minBodyRotation = prefs.getInt("min_body_rotation", 45).toFloat(),
                followThroughAngle = prefs.getInt("follow_through_angle", 120).toFloat()
            )
            "forehand_andrii" -> ExerciseParameters.forehandDrive().copy(exerciseId = "forehand_andrii")
            "forehand_drive_general" -> ExerciseParameters.forehandDrive().copy(exerciseId = "forehand_drive_general")
            "backhand_drive" -> ExerciseParameters.backhandDrive()
            else -> if (exerciseId?.startsWith("custom_") == true) {
                ExerciseParameters.forehandDrive().copy(exerciseId = exerciseId!!)
            } else {
                ExerciseParameters.forehandDrive()
            }
        }
        
        val targetsJson = intent.getStringExtra("PER_PHASE_TARGETS_JSON").orEmpty()
        val perPhaseTargets = PerPhaseTargetsCodec.parse(targetsJson)
        perPhaseTargets[PerPhaseTargetsCodec.KEY_KNEES_BACKSWING]?.let { (min, max) ->
            exerciseParameters = exerciseParameters.copy(kneeBendBackswingMin = min, kneeBendBackswingMax = max)
        }
        drillMetricBands = perPhaseTargets.mapValues { (_, pair) -> pair.first.toDouble()..pair.second.toDouble() }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = exerciseName ?: getString(R.string.training_title)
            setDisplayHomeAsUpEnabled(true)
        }

        uiController.setup()
        startTimerLoop()
        decideCameraModeAndStart()
    }

    /**
     * Camera-mode decision is async (baseline lookup is a suspend Flow read). Video-debug
     * mode is unaffected (still legacy, still synchronous). For live camera: a forehand
     * RTMPose baseline is now REQUIRED — the RTM path owns the whole camera+drill path via
     * [RtmposeTrainingController] (PoseAnalysisProcessor is never started, and
     * [TrainingMediaManager] only prepares `cameraPreviewContainer` for the RTM controller
     * to attach itself into). There is no legacy fallback anymore (see project CLAUDE.md "why this
     * task exists" — the legacy pipeline has no voice output at all, so falling back to it
     * silently produced mute sessions). If no baseline exists, or the RTM controller fails to
     * start, [showCalibrationRequiredDialog] blocks the screen until the player calibrates or
     * leaves.
     */
    private fun decideCameraModeAndStart() {
        lifecycleScope.launch {
            val referenceType = referenceTypeExtra ?: REFERENCE_STANDARD
            val drillBands = DrillReferenceResolver.resolveMetricBands(
                referenceTypeExtraPresent = referenceTypeExtra != null,
                parsedBands = drillMetricBands,
                shippedDefaultBands = ShippedBaselines.defaultBands()
            )
            val baseline: PersonalBaseline? = if (isCalibrationRequired(referenceType)) {
                val loaded = loadRtmBaseline()
                // We actually suspended (baseline read) — if the activity has since dropped
                // below STARTED (e.g. backgrounded), any fragment transaction below would throw
                // "Can not perform this action after onSaveInstanceState". Bail out before
                // touching the fragment manager or views. NB: this check MUST stay inside the
                // suspending branch. The "standard" branch below never suspends, so the body
                // runs synchronously inside onCreate where the state is still CREATED — an
                // unconditional STARTED guard here silently aborts camera startup entirely.
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
                loaded
            } else {
                ShippedBaselines.FOREHAND_ANDRII
            }

            if (isFinishing || isDestroyed) return@launch

            val started = baseline != null && startRtmController(baseline, referenceType, movementProfile, drillBands)
            if (started) {
                binding.root.postDelayed({ startTraining() }, 500)
            } else {
                showCalibrationRequiredDialog()
            }
        }
    }

    private suspend fun loadRtmBaseline(): PersonalBaseline? {
        if (!isForehandRtmEligible(exerciseId)) return null
        return try {
            PersonalBaselineRepository(AppDatabase.getDatabase(this@TrainingActivity).personalBaselineDao())
                .getActiveBaseline("forehand_drive_rtm")
                .first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load RTMPose baseline", e)
            null
        }
    }

    /** Attempts to start the RTM live path against [baseline]. Returns false (nothing left
     *  attached beyond what [TrainingMediaManager.setup] already did) if the
     *  RTMPose backend fails to construct inside [RtmposeTrainingController.start]. */
    private fun startRtmController(
        baseline: PersonalBaseline,
        referenceType: String,
        movementProfile: String?,
        drillBands: Map<String, ClosedRange<Double>>
    ): Boolean {
        mediaManager.setup()
        val rules = if (isCalibrationRequired(referenceType)) {
            BaselineRuleFactory.defaultRules(baseline)
        } else {
            emptyList()
        }
        val hipTravelMaxTorso = if (movementProfile == "general") {
            ForehandDriveGeneral.MOVEMENT_TOLERANT_HIP_TRAVEL
        } else {
            LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO
        }
        val controller = RtmposeTrainingController(
            activity = this@TrainingActivity,
            container = binding.cameraPreviewContainer,
            stateManager = stateManager,
            settingsManager = SettingsManager(this@TrainingActivity),
            baseline = baseline,
            rules = rules,
            hipTravelMaxTorso = hipTravelMaxTorso,
            onUiUpdate = { uiController.updateStats() },
            metricBands = drillBands
        )
        if (!controller.start()) return false
        rtmController = controller
        uiController.setCorrectionChipsForPath(true)
        return true
    }

    /** Blocking, dismissible-only-via-its-own-actions dialog: calibration is a hard
     *  prerequisite now, so there is nothing useful to show behind it. "Calibrate Now" hands
     *  off to [RtmposeCalibrationActivity]; "Not Now" leaves the training screen entirely. */
    private fun showCalibrationRequiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.training_calibration_required_title)
            .setMessage(R.string.training_calibration_required_message)
            .setCancelable(false)
            .setPositiveButton(R.string.training_calibration_required_calibrate) { _, _ ->
                calibrationLauncher.launch(Intent(this, RtmposeCalibrationActivity::class.java))
            }
            .setNegativeButton(R.string.training_calibration_required_leave) { _, _ ->
                finish()
            }
            .show()
    }

    /** Re-checks the baseline after [RtmposeCalibrationActivity] returns (whether it saved a
     *  baseline, was backed out of, or failed) and either starts the drill or leaves the
     *  screen — no second dialog loop, per the calibration-flow contract. */
    private suspend fun retryAfterCalibration() {
        val referenceType = referenceTypeExtra ?: REFERENCE_STANDARD
        val drillBands = DrillReferenceResolver.resolveMetricBands(
            referenceTypeExtraPresent = referenceTypeExtra != null,
            parsedBands = drillMetricBands,
            shippedDefaultBands = ShippedBaselines.defaultBands()
        )
        val baseline: PersonalBaseline? = if (isCalibrationRequired(referenceType)) {
            loadRtmBaseline()
        } else {
            ShippedBaselines.FOREHAND_ANDRII
        }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (baseline != null && startRtmController(baseline, referenceType, movementProfile, drillBands)) {
            binding.root.postDelayed({ startTraining() }, 500)
        } else {
            finish()
        }
    }

    private fun toggleTraining() {
        if (stateManager.isTrainingActive) pauseTraining() else resumeTraining()
    }

    private fun startTimerLoop() {
        val timerView = binding.root.findViewById<android.widget.TextView>(R.id.tv_timer)
        binding.root.post(object : Runnable {
            override fun run() {
                if (!isFinishing) {
                    timerView?.text = stateManager.getSessionTimeFormatted()
                    binding.root.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun startTraining() {
        stateManager.startTraining()
        uiController.updateUIForTrainingState(true)
    }

    private fun pauseTraining() {
        stateManager.pauseTraining()
        uiController.updateUIForTrainingState(false)
    }

    private fun resumeTraining() {
        stateManager.resumeTraining()
        uiController.updateUIForTrainingState(true)
    }

    private fun stopTraining(discard: Boolean = false) {
        stateManager.stopTraining()
        uiController.updateUIForTrainingState(false)

        if (discard) {
            rtmController?.abortRecording()
            android.widget.Toast.makeText(this, R.string.session_discarded, android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val durationSeconds = stateManager.getSessionDurationSeconds()
        if (durationSeconds < 5) {
            rtmController?.abortRecording()
            android.widget.Toast.makeText(this, "Training is too short", android.widget.Toast.LENGTH_SHORT).show()
            android.util.Log.d("TrainingActivity", "Training too short ($durationSeconds s), skipping save")
            finish()
            return
        }

        val app = application as TTCoachApplication
        val controller = rtmController
        if (controller != null) {
            // Finalize the pose recording SYNCHRONOUSLY, before saveSessionToCloud/finish()
            // below, and on the app-scoped scope (not lifecycleScope) so the finalize-then-save
            // sequence survives finish() tearing this activity down. launch(Main.immediate) runs
            // the coroutine body — including finishRecording()'s CAS claim on
            // RtmposeTrainingController.finalizationClaimed — synchronously up to its first
            // suspension point, i.e. before this function returns and calls finish() below. That
            // closes the race the previous async-save design had: onDestroy's abortRecording()
            // safety net can no longer win against an intended finish, because finalization is
            // already claimed by the time onDestroy could possibly run.
            app.applicationScope.launch(Dispatchers.Main.immediate) {
                val poseFile = controller.finishRecording()
                saveSessionToCloud(poseFile)
            }
        } else {
            saveSessionToCloud(poseFile = null)
        }

        // Close this screen immediately rather than waiting on the save — see
        // docs/superpowers/specs/2026-07-03-finish-to-session-summary-flow-design.md. onSaved
        // (inside saveSessionToCloud, running on the app-scoped launch above) sets
        // TTCoachApplication.pendingReviewSessionId, which MainActivity picks up and navigates
        // to SessionReviewFragment.
        finish()
    }

    /** [poseFile] is the already-finalized recording (or null — pose upload disabled, no
     *  RTM controller, or nothing was recorded), handed in by [stopTraining] which finalizes it
     *  synchronously before this runs. This function itself does not call finishRecording or
     *  abortRecording — by the time it runs, finalization has already happened. */
    private fun saveSessionToCloud(poseFile: File?) {
        val app = application as TTCoachApplication

        val exerciseIdToSave = exerciseId ?: "forehand_drive"
        val exerciseNameToSave = exerciseName ?: getString(R.string.exercise_forehand_name)
        val startTimeValue = stateManager.getStartTime()
        val endTimeValue = stateManager.getEndTime()
        val durationSeconds = stateManager.getSessionDurationSeconds()
        val strokeCount = stateManager.getStrokeCount()
        val correctStrokes = stateManager.getGoodStrokesCount()
        val averageScore = stateManager.getAverageScore()

        // LOGGING FOR DEBUGGING
        android.util.Log.d("TrainingActivity", "Saving to cloud: exercise=$exerciseIdToSave, duration=$durationSeconds sec, strokes=$strokeCount, score=$averageScore")
        android.util.Log.d("TrainingActivity", "CloudSync authenticated: ${app.cloudSyncManager.isAuthenticated}")

        app.cloudSyncManager.saveTrainingFromState(
            exerciseId = exerciseIdToSave,
            exerciseName = exerciseNameToSave,
            startTime = startTimeValue,
            endTime = endTimeValue,
            durationSeconds = durationSeconds,
            strokeCount = strokeCount,
            correctStrokes = correctStrokes,
            averageScore = averageScore,
            appVersion = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" },
            onSaved = { sessionId ->
                val settingsManager = SettingsManager(this@TrainingActivity)
                app.sessionAnalyticsRecorder.record(
                    sessionId = sessionId,
                    results = stateManager.getAnalysisResults(),
                    feedbackCounts = stateManager.getFeedbackCounts().toMap(),
                    isTypeEnabled = { type -> settingsManager.isCorrectionTypeEnabled(type) },
                    repPoses = stateManager.getRepPoses()
                )
                app.pendingReviewSessionId.value = sessionId

                val userId = app.cloudSyncManager.currentUserId
                if (poseFile != null && userId != null) {
                    // Re-check consent right before enqueueing: it may have been revoked after
                    // the recording started (or even after finishRecording() returned a
                    // finalized file). The toggle's promise is "no pose data leaves the device"
                    // — honor that at the last possible moment, not just at session start.
                    if (!settingsManager.isPoseUploadEnabled()) {
                        android.util.Log.d(
                            "TrainingActivity",
                            "Pose upload consent revoked mid-session; discarding local recording ${poseFile.absolutePath} instead of enqueueing"
                        )
                        poseFile.delete()
                    } else {
                        val renamed = File(poseFile.parentFile, "$sessionId.json.gz")
                        if (poseFile.renameTo(renamed)) {
                            PoseUploadQueue.enqueue(this@TrainingActivity, userId, sessionId, renamed)
                        } else {
                            // Rename failed (stale file at target, directory removed by a cache
                            // clear, etc.) — poseFile is already a fully finalized .json.gz from
                            // finishRecording(). Losing the upload is worse than losing the
                            // sessionId-based local filename convention, so enqueue it as-is rather
                            // than silently orphaning a real recording.
                            android.util.Log.e(
                                "TrainingActivity",
                                "renameTo failed for pose file ${poseFile.absolutePath} -> ${renamed.absolutePath}; enqueuing under provisional name"
                            )
                            PoseUploadQueue.enqueue(this@TrainingActivity, userId, sessionId, poseFile)
                        }
                    }
                }
            },
            onFailed = {
                // The recording was already finalized (by stopTraining, before this save even
                // started) — RtmposeTrainingController.abortRecording() is now a no-op (its
                // finalizationClaimed CAS is already claimed). Delete the finalized file
                // ourselves instead, so an unauthenticated or failed save still leaves nothing
                // behind, matching the pre-existing "save fails -> aborted" contract.
                poseFile?.delete()
            }
        )
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (stateManager.isTrainingActive) {
                    pauseTraining()
                }
                val sheet = com.ttcoachai.ui.dialogs.EndSessionSheet.newInstance(
                    durationSeconds = stateManager.getSessionDurationSeconds(),
                    strokeCount = stateManager.getStrokeCount(),
                    accuracyPercent = stateManager.getAverageScore().toInt()
                )
                sheet.onKeepTraining = { resumeTraining() }
                sheet.onDiscard = { stopTraining(discard = true) }
                sheet.onFinishSave = { stopTraining(discard = false) }
                sheet.show(supportFragmentManager, com.ttcoachai.ui.dialogs.EndSessionSheet.TAG)
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
        mediaManager.release()
        // Safety net for exits that never reached stopTraining (task-switch kill, unhandled
        // exception, back out before the end-session sheet): abort any still-unclaimed
        // recording so it doesn't rot on disk forever. Race-safe against stopTraining, which
        // now claims finalization (RtmposeTrainingController.finishRecording()'s CAS)
        // SYNCHRONOUSLY before calling finish() — so by the time onDestroy can possibly run,
        // an intended finalize has already won the latch and this call is a no-op.
        rtmController?.abortRecording()
        rtmController?.release()
    }
}
