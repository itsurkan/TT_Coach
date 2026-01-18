/*
 * AI Coach for Table Tennis
 * Settings UI Controller - Manages settings screen UI components
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import com.google.mediapipe.examples.poselandmarker.LocaleHelper
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.ActivitySettingsBinding

class SettingsUIController(
    private val binding: ActivitySettingsBinding,
    private val settingsManager: SettingsManager
) {
    
    fun setupExerciseParameters() {
        // Wrist angle
        binding.seekBarWristAngle.apply {
            max = 200
            progress = settingsManager.getIdealWristAngle()
            setOnSeekBarChangeListener(createSeekBarListener { progress ->
                binding.tvWristAngleValue.text = "$progress°"
            })
            binding.tvWristAngleValue.text = "$progress°"
        }

        // Body rotation
        binding.seekBarBodyRotation.apply {
            max = 90
            progress = settingsManager.getMinBodyRotation()
            setOnSeekBarChangeListener(createSeekBarListener { progress ->
                binding.tvBodyRotationValue.text = "$progress°"
            })
            binding.tvBodyRotationValue.text = "$progress°"
        }

        // Follow through
        binding.seekBarFollowThrough.apply {
            max = 180
            progress = settingsManager.getFollowThroughAngle()
            setOnSeekBarChangeListener(createSeekBarListener { progress ->
                binding.tvFollowThroughValue.text = "$progress°"
            })
            binding.tvFollowThroughValue.text = "$progress°"
        }
    }

    fun setupAudioSettings() {
        binding.switchAudioFeedback.isChecked = settingsManager.isAudioFeedbackEnabled()

        binding.seekBarVolume.apply {
            max = 100
            progress = settingsManager.getFeedbackVolume()
            setOnSeekBarChangeListener(createSeekBarListener { progress ->
                binding.tvVolumeValue.text = "$progress%"
            })
            binding.tvVolumeValue.text = "$progress%"
        }

        binding.seekBarSpeechRate.apply {
            max = 100
            progress = settingsManager.getSpeechRate()
            setOnSeekBarChangeListener(createSeekBarListener { progress ->
                val rate = progress / 50f
                binding.tvSpeechRateValue.text = String.format("%.1fx", rate)
            })
            val rate = progress / 50f
            binding.tvSpeechRateValue.text = String.format("%.1fx", rate)
        }
    }

    fun setupCameraSettings() {
        binding.spinnerCameraResolution.setSelection(settingsManager.getCameraResolution())
        
        binding.seekBarFps.apply {
            max = 60
            progress = settingsManager.getTargetFps()
            setOnSeekBarChangeListener(createSeekBarListener { progress ->
                val validProgress = progress.coerceAtLeast(15)
                if (progress < 15) {
                    this.progress = 15
                }
                binding.tvFpsValue.text = "$validProgress FPS"
            })
            binding.tvFpsValue.text = "$progress FPS"
        }

        binding.switchShowSkeleton.isChecked = settingsManager.isShowSkeleton()
        
        setupFeedbackTypeSpinner()
    }

    private fun setupFeedbackTypeSpinner() {
        val context = binding.root.context
        val options = arrayOf(
            context.getString(R.string.feedback_type_short),
            context.getString(R.string.feedback_type_standard)
        )
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFeedbackType.adapter = adapter
        binding.spinnerFeedbackType.setSelection(settingsManager.getFeedbackType())
    }
    
    fun setupLanguageSpinner(languageSpinner: Spinner, context: android.content.Context) {
        val languages = context.resources.getStringArray(R.array.language_options)
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
        
        val currentLang = LocaleHelper.getSavedLanguage(context)
        val languageCodes = context.resources.getStringArray(R.array.language_codes)
        val currentIndex = languageCodes.indexOf(currentLang)
        if (currentIndex >= 0) {
            languageSpinner.setSelection(currentIndex)
        }
    }

    fun loadAllSettings() {
        binding.seekBarWristAngle.progress = settingsManager.getIdealWristAngle()
        binding.seekBarBodyRotation.progress = settingsManager.getMinBodyRotation()
        binding.seekBarFollowThrough.progress = settingsManager.getFollowThroughAngle()
        binding.switchAudioFeedback.isChecked = settingsManager.isAudioFeedbackEnabled()
        binding.seekBarVolume.progress = settingsManager.getFeedbackVolume()
        binding.seekBarSpeechRate.progress = settingsManager.getSpeechRate()
        binding.spinnerCameraResolution.setSelection(settingsManager.getCameraResolution())
        binding.seekBarFps.progress = settingsManager.getTargetFps()
        binding.switchShowSkeleton.isChecked = settingsManager.isShowSkeleton()
        binding.spinnerFeedbackType.setSelection(settingsManager.getFeedbackType())
    }
    
    fun saveAllSettings() {
        settingsManager.saveAll(
            wristAngle = binding.seekBarWristAngle.progress,
            bodyRotation = binding.seekBarBodyRotation.progress,
            followThrough = binding.seekBarFollowThrough.progress,
            audioEnabled = binding.switchAudioFeedback.isChecked,
            volume = binding.seekBarVolume.progress,
            speechRate = binding.seekBarSpeechRate.progress,
            cameraResolution = binding.spinnerCameraResolution.selectedItemPosition,
            fps = binding.seekBarFps.progress,
            showSkeleton = binding.switchShowSkeleton.isChecked,
            feedbackType = binding.spinnerFeedbackType.selectedItemPosition
        )
    }
    
    private fun createSeekBarListener(onProgressChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onProgressChanged(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }
}
