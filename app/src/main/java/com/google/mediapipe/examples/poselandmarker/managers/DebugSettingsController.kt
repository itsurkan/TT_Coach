/*
 * AI Coach for Table Tennis
 * Debug Settings Controller - Manages feedback settings UI in DebugActivity
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.view.View
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.Toast
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityDebugBinding
import com.google.mediapipe.examples.poselandmarker.models.CorrectionType

class DebugSettingsController(
    private val binding: ActivityDebugBinding,
    private val settingsManager: SettingsManager
) {
    private val frequencies = listOf(3, 5, 10)

    fun setup() {
        setupFrequencySpinner()
        setupCorrectionCheckboxes()
    }

    private fun setupFrequencySpinner() {
        val currentFreq = settingsManager.getFeedbackFrequency()
        val freqIndex = frequencies.indexOf(currentFreq).coerceAtLeast(0)
        binding.spinnerFrequency.setSelection(freqIndex)
        
        binding.spinnerFrequency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.setFeedbackFrequency(frequencies[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupCorrectionCheckboxes() {
        setupCheckbox(binding.cbWrist, CorrectionType.WRIST)
        setupCheckbox(binding.cbBodyRotation, CorrectionType.BODY_ROTATION)
        setupCheckbox(binding.cbFollowThrough, CorrectionType.FOLLOW_THROUGH)
        setupCheckbox(binding.cbContactHeight, CorrectionType.CONTACT_HEIGHT)
        setupCheckbox(binding.cbElbowPosition, CorrectionType.ELBOW_POSITION)
        setupCheckbox(binding.cbStrokeSpeed, CorrectionType.STROKE_SPEED)
    }

    private fun setupCheckbox(checkbox: CheckBox, type: CorrectionType) {
        checkbox.isChecked = settingsManager.isCorrectionTypeEnabled(type)
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setCorrectionTypeEnabled(type, isChecked)
        }
    }
}
