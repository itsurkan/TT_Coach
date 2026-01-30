/*
 * AI Coach for Table Tennis
 * Training Screen with Camera and Analysis
 */

package com.ttcoachai

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.Spinner
import com.ttcoachai.databinding.ActivityTrainingBinding
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.managers.TrainingStateManager
import com.ttcoachai.managers.VideoPlayerManager
import com.ttcoachai.models.AnalysisResult
import com.ttcoachai.models.CorrectionType
import com.ttcoachai.models.ExerciseParameters
import com.ttcoachai.models.StrokePhase
import com.ttcoachai.services.FeedbackGenerator
import com.ttcoachai.services.MotionAnalyzer
import com.ttcoachai.processors.PoseAnalysisProcessor
import android.util.Log
import androidx.activity.OnBackPressedCallback

class TrainingActivity : BaseActivity(), PoseLandmarkerHelper.LandmarkerListener {
    private lateinit var binding: ActivityTrainingBinding
    private var exerciseId: String? = null
    private var exerciseName: String? = null
    private var useVideo: Boolean = false
    
    // Managers
    private lateinit var stateManager: TrainingStateManager
    private var videoPlayerManager: VideoPlayerManager? = null
    private lateinit var feedbackAdapter: com.ttcoachai.adapters.FeedbackListAdapter
    
    // Analysis processing
    private lateinit var poseAnalysisProcessor: PoseAnalysisProcessor
    
    // Аналітика та параметри
    private lateinit var exerciseParameters: ExerciseParameters

    companion object {
        private const val TAG = "TrainingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Отримання даних вправи
        exerciseId = intent.getStringExtra("EXERCISE_ID")
        exerciseName = intent.getStringExtra("EXERCISE_NAME")
        useVideo = intent.getBooleanExtra("USE_VIDEO", false)

        // Ініціалізація менеджерів
        initializeManagers()
        
        // Ініціалізація аналітики
        initializeAnalysis()
        
        setupUI()
        setupBackNavigation()
    }
    
    private fun initializeManagers() {
        stateManager = com.ttcoachai.managers.TrainingStateManager.getInstance(this)
    }
    
    private fun initializeAnalysis() {
        // Завантажити параметри з SharedPreferences
        val prefs = getSharedPreferences("ai_coach_prefs", MODE_PRIVATE)
        val idealWristAngle = prefs.getInt("ideal_wrist_angle", 180)
        val minBodyRotation = prefs.getInt("min_body_rotation", 45)
        val followThroughAngle = prefs.getInt("follow_through_angle", 120)
        
        exerciseParameters = when (exerciseId) {
            "forehand_drive" -> ExerciseParameters.fromSharedPreferences(
                exerciseId = "forehand_drive",
                idealWristAngle = idealWristAngle,
                minBodyRotation = minBodyRotation,
                followThroughAngle = followThroughAngle
            )
            "backhand_drive" -> ExerciseParameters.backhandDrive()
            else -> ExerciseParameters.forehandDrive()
        }
        
        val motionAnalyzer = MotionAnalyzer(exerciseParameters)
        val feedbackGenerator = FeedbackGenerator(this)
        
        // Initialize pose analysis processor
        poseAnalysisProcessor = PoseAnalysisProcessor(
            application as TTCoachApplication,
            motionAnalyzer,
            feedbackGenerator,
            stateManager,
            { updateStats() }
        )
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = exerciseName ?: getString(R.string.training_title)
            setDisplayHomeAsUpEnabled(true)
        }

        // Auto start camera or video
        startCameraPreview()

        // Setup bottom sheet behavior
        val bottomSheet = binding.bottomSheet
        val bottomSheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
        
        // Use the peek height defined in XML (120dp)
        // bottomSheetBehavior.peekHeight is automatically read from XML
        
        bottomSheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.isHideable = false

        // Drawer buttons
        binding.drillMenu.btnPauseResume.setOnClickListener {
            toggleTraining()
        }

        binding.drillMenu.btnEndSession.setOnClickListener {
            stopTraining()
        }

        // Overlay buttons
        binding.root.findViewById<View>(R.id.fab_pause_play)?.setOnClickListener {
            toggleTraining()
        }

        // Setup Feedback RecyclerView
        feedbackAdapter = com.ttcoachai.adapters.FeedbackListAdapter()
        binding.drillMenu.rvFeedbackList.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@TrainingActivity)
            adapter = feedbackAdapter
        }

        // Add Mock Items for UI testing
        val mockItems = listOf(
            com.ttcoachai.models.FeedbackItem(
                message = "Good Wrist Position!",
                type = com.ttcoachai.models.CorrectionType.WRIST,
                isPositive = true
            ),
            com.ttcoachai.models.FeedbackItem(
                message = "Rotate your Body more",
                type = com.ttcoachai.models.CorrectionType.BODY_ROTATION,
                isPositive = false
            ),
            com.ttcoachai.models.FeedbackItem(
                message = "Excellent Follow Through",
                type = com.ttcoachai.models.CorrectionType.FOLLOW_THROUGH,
                isPositive = true
            )
        )
        feedbackAdapter.updateFeedback(mockItems)
        stateManager.addFeedbackItems(mockItems)

        // Start timer update loop
        startTimerLoop()

        // Initial state - start training automatically
        binding.root.postDelayed({
            startTraining()
        }, 500)

        // Apply distance mode if enabled
        applyDistanceMode()

        // Setup feedback settings
        setupFeedbackSettings()
    }
    
    private fun setupFeedbackSettings() {
        val settingsManager = SettingsManager(this)
        val frequencies = listOf(3, 5, 10)
        
        // Setup frequency spinner
        val currentFreq = settingsManager.getFeedbackFrequency()
        val freqIndex = frequencies.indexOf(currentFreq).coerceAtLeast(0)
        binding.drillMenu.spinnerFrequency.setSelection(freqIndex)
        
        binding.drillMenu.spinnerFrequency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.setFeedbackFrequency(frequencies[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Setup correction checkboxes
        setupCorrectionCheckbox(binding.drillMenu.cbWrist, CorrectionType.WRIST, settingsManager)
        setupCorrectionCheckbox(binding.drillMenu.cbBodyRotation, CorrectionType.BODY_ROTATION, settingsManager)
        setupCorrectionCheckbox(binding.drillMenu.cbFollowThrough, CorrectionType.FOLLOW_THROUGH, settingsManager)
        setupCorrectionCheckbox(binding.drillMenu.cbContactHeight, CorrectionType.CONTACT_HEIGHT, settingsManager)
        setupCorrectionCheckbox(binding.drillMenu.cbElbowPosition, CorrectionType.ELBOW_POSITION, settingsManager)
        setupCorrectionCheckbox(binding.drillMenu.cbStrokeSpeed, CorrectionType.STROKE_SPEED, settingsManager)
    }
    
    private fun setupCorrectionCheckbox(checkbox: CheckBox, type: CorrectionType, settingsManager: SettingsManager) {
        checkbox.isChecked = settingsManager.isCorrectionTypeEnabled(type)
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setCorrectionTypeEnabled(type, isChecked)
        }
    }

    private fun applyDistanceMode() {
        val settingsManager = com.ttcoachai.managers.SettingsManager(this)
        if (settingsManager.isDistanceModeEnabled()) {
            // Scale up overlays for better visibility from a distance
            val timerView = binding.root.findViewById<android.widget.TextView>(R.id.tv_timer)
            val hitsView = binding.root.findViewById<android.widget.TextView>(R.id.tv_hits_count)
            val accuracyView = binding.root.findViewById<android.widget.TextView>(R.id.tv_accuracy_percent)
            
            // Labels
            val hitsLabel = (binding.root.findViewById<android.view.View>(R.id.layout_hits_stat) as? android.view.ViewGroup)?.getChildAt(0) as? android.widget.TextView
            val accuracyLabel = (binding.root.findViewById<android.view.View>(R.id.layout_accuracy_stat) as? android.view.ViewGroup)?.getChildAt(0) as? android.widget.TextView

            // Scale factor 1.5x
            timerView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28f)
            hitsView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 36f)
            accuracyView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 36f)
            
            hitsLabel?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            accuracyLabel?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            
            // Also scale the pause button
            val fab = binding.root.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_pause_play)
            fab?.customSize = (fab?.customSize ?: 56).let { (it * 1.5).toInt() }
        }
    }

    private fun toggleTraining() {
        if (stateManager.isTrainingActive) {
            pauseTraining()
        } else {
            resumeTraining()
        }
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

    private fun startCameraPreview() {
        if (useVideo) {
            // Debug mode: Load video from resources with pose detection
            binding.cameraPreviewContainer.visibility = View.GONE
            binding.videoContainer.visibility = View.VISIBLE
            binding.videoView.visibility = View.VISIBLE
            binding.overlay.visibility = View.VISIBLE
            
            videoPlayerManager = VideoPlayerManager(
                context = this,
                videoView = binding.videoView,
                overlayView = binding.overlay,
                onStatusChange = { status ->
                    // Status updates handled by UI
                }
            )
            
            videoPlayerManager?.playVideoWithPoseDetection("Videos/forehand_drive.mp4")
        } else {
            // Normal mode: Launch camera
            binding.videoContainer.visibility = View.GONE
            binding.videoView.visibility = View.GONE
            binding.overlay.visibility = View.GONE  // Fix: Hide overlay in camera mode as fragment handles it
            binding.cameraPreviewContainer.visibility = View.VISIBLE
            
            val cameraFragment = com.ttcoachai.fragment.CameraFragment()
            supportFragmentManager.beginTransaction()
                .replace(binding.cameraPreviewContainer.id, cameraFragment)
                .commit()
        }
    }

    private fun startTraining() {
        stateManager.startTraining()
        updateUIForTrainingState(true)
        
        // Start analysis session
        poseAnalysisProcessor.startSession(
            exerciseId ?: "forehand_drive",
            exerciseName ?: getString(R.string.exercise_forehand_name)
        )
    }

    private fun pauseTraining() {
        stateManager.pauseTraining()
        updateUIForTrainingState(false)
    }

    private fun resumeTraining() {
        stateManager.resumeTraining()
        updateUIForTrainingState(true)
    }

    private fun stopTraining() {
        stateManager.stopTraining()
        updateUIForTrainingState(false)
        
        // End analysis session
        poseAnalysisProcessor.endSession()
        
        showSummary()
    }

    private fun updateUIForTrainingState(isActive: Boolean) {
        val icon = if (isActive) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val textResId = if (isActive) R.string.btn_pause else R.string.btn_resume
        val text = getString(textResId)
        
        binding.root.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_pause_play)?.setImageResource(icon)
        binding.drillMenu.btnPauseResume.text = text
        binding.drillMenu.btnPauseResume.setIconResource(icon)
    }

    private fun updateStats() {
        runOnUiThread {
            // Update stats on screen
            val totalHits = stateManager.getTotalHits()
            val accuracy = if (totalHits > 0) {
                (stateManager.getSuccessfulHits().toFloat() / totalHits * 100).toInt()
            } else 0
    
            // Overlay stats
            binding.root.findViewById<android.widget.TextView>(R.id.tv_hits_count)?.text = totalHits.toString()
            binding.root.findViewById<android.widget.TextView>(R.id.tv_accuracy_percent)?.text = getString(R.string.format_percent_simple, accuracy)
            
            // Update drawer stats
            binding.drillMenu.tvTotalHits.text = totalHits.toString()
            binding.drillMenu.tvAccuracy.text = getString(R.string.format_percent_simple, accuracy)
            
            // Update drill progress
            val successfulHits = stateManager.getSuccessfulHits()
            val targetHits = 20 // Dummy target for now
            binding.drillMenu.progressDrill.progress = (successfulHits.toFloat() / targetHits * 100).toInt()
            binding.drillMenu.tvDrillProgress.text = getString(R.string.hits_progress_format, successfulHits, targetHits)
    
            // Update feedback adapter
            val feedbackItems = stateManager.getLatestFeedbackItems()
            feedbackAdapter.updateFeedback(feedbackItems)
        }
    }

    private fun showSummary() {
        val summary = stateManager.getSummaryText()
        val tip = stateManager.getImprovementTip()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.training_summary_title))
            .setMessage("$summary\n\n$tip")
            .setPositiveButton(getString(R.string.btn_complete)) { _, _ ->
                finish()
            }
            .setNegativeButton(getString(R.string.btn_continue)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitRequest()
            }
        })
    }

    private fun handleExitRequest() {
        showExitConfirmationDialog()
    }

    private fun showExitConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.finish_training_title))
            .setMessage(getString(R.string.finish_training_message))
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                finish()
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleExitRequest()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoPlayerManager?.release()
        if (::poseAnalysisProcessor.isInitialized) {
            poseAnalysisProcessor.release()
        }
    }
    
    // PoseLandmarkerHelper.LandmarkerListener implementation
    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Log.e(TAG, "Pose detection error: $error (code: $errorCode)")
        }
    }
    
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        poseAnalysisProcessor.processResults(resultBundle)
    }
}
