package com.ttcoachai.managers

import android.view.View
import android.widget.AdapterView
import android.widget.CheckBox
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.ttcoachai.R
import com.ttcoachai.TrainingActivity
import com.ttcoachai.adapters.FeedbackListAdapter
import com.ttcoachai.databinding.ActivityTrainingBinding
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.FeedbackItem

/**
 * Manages UI interactions and updates for TrainingActivity
 */
class TrainingUIController(
    private val activity: TrainingActivity,
    private val binding: ActivityTrainingBinding,
    private val settingsManager: SettingsManager,
    private val stateManager: TrainingStateManager,
    private val onToggleTraining: () -> Unit,
    private val onStopTraining: () -> Unit
) {
    private val feedbackAdapter = FeedbackListAdapter()

    fun setup() {
        setupBottomSheet()
        setupButtons()
        setupRecyclerView()
        setupFeedbackSettings()
        updateStats() // render formatted 0/0 · 0% at start, before the first stroke fires
    }

    private fun setupBottomSheet() {
        val bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.isHideable = false
    }

    private fun setupButtons() {
        binding.drillMenu.btnPauseResume.setOnClickListener { onToggleTraining() }
        binding.drillMenu.btnEndSession.setOnClickListener { showEndSessionDialog() }
        binding.root.findViewById<View>(R.id.fab_pause_play)?.setOnClickListener { onToggleTraining() }
    }

    private fun setupRecyclerView() {
        binding.drillMenu.rvFeedbackList.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = feedbackAdapter
        }
    }

    private fun setupFeedbackSettings() {
        val frequencies = listOf(3, 5, 10)
        val currentFreq = settingsManager.getFeedbackFrequency()
        val freqIndex = frequencies.indexOf(currentFreq).coerceAtLeast(0)
        binding.drillMenu.spinnerFrequency.setSelection(freqIndex)
        
        binding.drillMenu.spinnerFrequency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.setFeedbackFrequency(frequencies[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        setupCorrectionCheckbox(binding.drillMenu.cbWrist, CorrectionType.WRIST)
        setupCorrectionCheckbox(binding.drillMenu.cbBodyRotation, CorrectionType.BODY_ROTATION)
        setupCorrectionCheckbox(binding.drillMenu.cbFollowThrough, CorrectionType.FOLLOW_THROUGH)
        setupCorrectionCheckbox(binding.drillMenu.cbContactHeight, CorrectionType.CONTACT_HEIGHT)
        setupCorrectionCheckbox(binding.drillMenu.cbElbowPosition, CorrectionType.ELBOW_POSITION)
        setupCorrectionCheckbox(binding.drillMenu.cbStrokeSpeed, CorrectionType.STROKE_SPEED)
    }

    private fun setupCorrectionCheckbox(checkbox: CheckBox, type: CorrectionType) {
        checkbox.isChecked = settingsManager.isCorrectionTypeEnabled(type)
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setCorrectionTypeEnabled(type, isChecked)
        }
    }

    fun updateUIForTrainingState(isActive: Boolean) {
        val icon = if (isActive) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val text = activity.getString(if (isActive) R.string.btn_pause else R.string.btn_resume)
        
        binding.root.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_pause_play)?.setImageResource(icon)
        binding.drillMenu.btnPauseResume.text = text
        binding.drillMenu.btnPauseResume.setIconResource(icon)
    }

    fun updateStats() {
        val totalHits = stateManager.getTotalHits()
        val accuracy = if (totalHits > 0) (stateManager.getSuccessfulHits().toFloat() / totalHits * 100).toInt() else 0

        binding.root.findViewById<android.widget.TextView>(R.id.tv_hits_count)?.text = totalHits.toString()
        binding.root.findViewById<android.widget.TextView>(R.id.tv_accuracy_percent)?.text = activity.getString(R.string.format_percent_simple, accuracy)
        
        binding.drillMenu.tvTotalHits.text = totalHits.toString()
        binding.drillMenu.tvAccuracy.text = activity.getString(R.string.format_percent_simple, accuracy)
        
        val successfulHits = stateManager.getSuccessfulHits()
        val targetHits = 20
        binding.drillMenu.progressDrill.progress = (successfulHits.toFloat() / targetHits * 100).toInt()
        binding.drillMenu.tvDrillProgress.text = activity.getString(R.string.hits_progress_format, successfulHits, targetHits)
        
        feedbackAdapter.updateFeedback(stateManager.getLatestFeedbackItems())
    }

    fun showSummary(summary: String, tip: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.training_summary_title))
            .setMessage("$summary\n\n$tip")
            .setPositiveButton(activity.getString(R.string.btn_complete)) { _, _ -> activity.finish() }
            .setNegativeButton(activity.getString(R.string.btn_continue)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showEndSessionDialog() {
        val wasActive = stateManager.isTrainingActive
        if (wasActive) {
            activity.runOnUiThread { onToggleTraining() } // Pause
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.finish_training_title))
            .setMessage(activity.getString(R.string.finish_training_message))
            .setNeutralButton(activity.getString(R.string.dialog_no)) { _, _ ->
                if (wasActive) activity.runOnUiThread { onToggleTraining() } // Resume
            }
            .setNegativeButton(activity.getString(R.string.btn_discard)) { _, _ ->
                (activity as? TrainingActivity)?.let { it.javaClass.getDeclaredMethod("stopTraining", Boolean::class.java).apply { isAccessible = true }.invoke(it, true) }
            }
            .setPositiveButton(activity.getString(R.string.btn_finish_save)) { _, _ ->
                onStopTraining()
            }
            .setOnCancelListener {
                if (wasActive) activity.runOnUiThread { onToggleTraining() } // Resume
            }
            .show()
            .apply {
                // Discard is destructive → red, matching the drill-menu Delete action.
                getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
                    ?.setTextColor(activity.getColor(R.color.ttc_error))
            }
    }
}
