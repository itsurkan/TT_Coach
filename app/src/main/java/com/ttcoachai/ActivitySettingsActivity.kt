package com.ttcoachai

import android.os.Bundle
import android.view.MenuItem
import android.widget.AdapterView
import com.ttcoachai.databinding.ActivityActivitySettingsBinding
import com.ttcoachai.managers.SettingsManager

class ActivitySettingsActivity : BaseActivity() {
    
    private lateinit var binding: ActivityActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    
    private var currentMinPoseDetectionConfidence = PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE
    private var currentMinPoseTrackingConfidence = PoseLandmarkerHelper.DEFAULT_POSE_TRACKING_CONFIDENCE
    private var currentMinPosePresenceConfidence = PoseLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE
    private var currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL
    private var currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = (application as TTCoachApplication).settingsManager

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.activity_settings_title)
            setDisplayHomeAsUpEnabled(true)
        }

        // Load current settings
        loadSettings()
        
        // Setup UI
        setupThresholdControls()
        setupSpinners()
    }

    private fun loadSettings() {
        currentMinPoseDetectionConfidence = settingsManager.getDetectionThreshold()
        currentMinPoseTrackingConfidence = settingsManager.getTrackingThreshold()
        currentMinPosePresenceConfidence = settingsManager.getPresenceThreshold()
        currentModel = settingsManager.getPoseModel()
        currentDelegate = settingsManager.getPoseDelegate()
        
        updateThresholdUI()
    }

    private fun setupThresholdControls() {
        // Detection threshold
        binding.btnDetectionMinus.setOnClickListener {
            adjustThreshold(ThresholdType.DETECTION, -0.1f)
        }
        binding.btnDetectionPlus.setOnClickListener {
            adjustThreshold(ThresholdType.DETECTION, 0.1f)
        }
        binding.seekbarDetectionThreshold.max = 100
        binding.seekbarDetectionThreshold.setOnSeekBarChangeListener(
            createSeekBarListener(ThresholdType.DETECTION)
        )

        // Tracking threshold
        binding.btnTrackingMinus.setOnClickListener {
            adjustThreshold(ThresholdType.TRACKING, -0.1f)
        }
        binding.btnTrackingPlus.setOnClickListener {
            adjustThreshold(ThresholdType.TRACKING, 0.1f)
        }
        binding.seekbarTrackingThreshold.max = 100
        binding.seekbarTrackingThreshold.setOnSeekBarChangeListener(
            createSeekBarListener(ThresholdType.TRACKING)
        )

        // Presence threshold
        binding.btnPresenceMinus.setOnClickListener {
            adjustThreshold(ThresholdType.PRESENCE, -0.1f)
        }
        binding.btnPresencePlus.setOnClickListener {
            adjustThreshold(ThresholdType.PRESENCE, 0.1f)
        }
        binding.seekbarPresenceThreshold.max = 100
        binding.seekbarPresenceThreshold.setOnSeekBarChangeListener(
            createSeekBarListener(ThresholdType.PRESENCE)
        )
    }

    private fun setupSpinners() {
        // Model spinner
        binding.spinnerModel.setSelection(currentModel, false)
        binding.spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentModel = position
                saveSettings()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Delegate spinner
        binding.spinnerDelegate.setSelection(currentDelegate, false)
        binding.spinnerDelegate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentDelegate = position
                saveSettings()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun adjustThreshold(type: ThresholdType, delta: Float) {
        when (type) {
            ThresholdType.DETECTION -> {
                currentMinPoseDetectionConfidence = (currentMinPoseDetectionConfidence + delta).coerceIn(0f, 1f)
            }
            ThresholdType.TRACKING -> {
                currentMinPoseTrackingConfidence = (currentMinPoseTrackingConfidence + delta).coerceIn(0f, 1f)
            }
            ThresholdType.PRESENCE -> {
                currentMinPosePresenceConfidence = (currentMinPosePresenceConfidence + delta).coerceIn(0f, 1f)
            }
        }
        updateThresholdUI()
        saveSettings()
    }

    private fun createSeekBarListener(type: ThresholdType) = object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                val value = progress / 100f
                when (type) {
                    ThresholdType.DETECTION -> currentMinPoseDetectionConfidence = value
                    ThresholdType.TRACKING -> currentMinPoseTrackingConfidence = value
                    ThresholdType.PRESENCE -> currentMinPosePresenceConfidence = value
                }
                updateThresholdUI()
            }
        }
        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
            saveSettings()
        }
    }

    private fun updateThresholdUI() {
        // Detection
        binding.tvDetectionThresholdValue.text = String.format("%.2f", currentMinPoseDetectionConfidence)
        binding.seekbarDetectionThreshold.progress = (currentMinPoseDetectionConfidence * 100).toInt()

        // Tracking
        binding.tvTrackingThresholdValue.text = String.format("%.2f", currentMinPoseTrackingConfidence)
        binding.seekbarTrackingThreshold.progress = (currentMinPoseTrackingConfidence * 100).toInt()

        // Presence
        binding.tvPresenceThresholdValue.text = String.format("%.2f", currentMinPosePresenceConfidence)
        binding.seekbarPresenceThreshold.progress = (currentMinPosePresenceConfidence * 100).toInt()
    }

    private fun saveSettings() {
        settingsManager.setDetectionThreshold(currentMinPoseDetectionConfidence)
        settingsManager.setTrackingThreshold(currentMinPoseTrackingConfidence)
        settingsManager.setPresenceThreshold(currentMinPosePresenceConfidence)
        settingsManager.setPoseModel(currentModel)
        settingsManager.setPoseDelegate(currentDelegate)
        
        // Trigger cloud sync
        (application as TTCoachApplication).cloudSyncManager.uploadSettings()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private enum class ThresholdType {
        DETECTION, TRACKING, PRESENCE
    }
}
