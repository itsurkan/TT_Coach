package com.ttcoachai.managers

import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.ttcoachai.LocaleHelper
import com.ttcoachai.R
import com.ttcoachai.TrainingActivity
import com.ttcoachai.adapters.FeedbackListAdapter
import com.ttcoachai.databinding.ActivityTrainingBinding
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.feedback.LiveFeedbackCatalog
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.FeedbackItem
import com.ttcoachai.ui.dialogs.FeedbackExplanationSheet

/**
 * Manages UI interactions and updates for TrainingActivity
 */
class TrainingUIController(
    private val activity: TrainingActivity,
    private val binding: ActivityTrainingBinding,
    private val settingsManager: SettingsManager,
    private val stateManager: TrainingStateManager,
    private val onToggleTraining: () -> Unit,
    private val onEndSession: () -> Unit
) {
    private val feedbackAdapter = FeedbackListAdapter(onRowClick = ::showFeedbackExplanation)

    fun setup() {
        setupBottomSheet()
        setupButtons()
        setupRecyclerView()
        setupFeedbackSettings()
        setupFeedbackSettingsCollapse()
        updateStats() // render formatted 0/0 · 0% at start, before the first stroke fires
    }

    private fun setupBottomSheet() {
        val bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.isHideable = false
    }

    private fun setupButtons() {
        binding.drillMenu.btnPauseResume.setOnClickListener { onToggleTraining() }
        binding.drillMenu.btnEndSession.setOnClickListener { onEndSession() }
        binding.drillMenu.cardFullReport.setOnClickListener { onEndSession() }
        binding.root.findViewById<View>(R.id.fab_pause_play)?.setOnClickListener { onToggleTraining() }
    }

    private fun setupRecyclerView() {
        binding.drillMenu.rvFeedbackList.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = feedbackAdapter
        }
    }

    private fun setupFeedbackSettings() {
        // Cues per session (3/5/10)
        val cuesButtons = listOf(
            binding.drillMenu.btnCues3,
            binding.drillMenu.btnCues5,
            binding.drillMenu.btnCues10
        )
        fun selectCues(count: Int, persist: Boolean) {
            val selected = when (count) {
                5 -> binding.drillMenu.btnCues5
                10 -> binding.drillMenu.btnCues10
                else -> binding.drillMenu.btnCues3
            }
            cuesButtons.forEach { styleSegment(it, it === selected) }
            if (persist) settingsManager.setFeedbackFrequency(count)
        }
        binding.drillMenu.btnCues3.setOnClickListener { selectCues(3, persist = true) }
        binding.drillMenu.btnCues5.setOnClickListener { selectCues(5, persist = true) }
        binding.drillMenu.btnCues10.setOnClickListener { selectCues(10, persist = true) }
        selectCues(settingsManager.getFeedbackFrequency(), persist = false)

        // Corrections
        val correctionChips = listOf(
            binding.drillMenu.chipWrist to CorrectionType.WRIST,
            binding.drillMenu.chipRotation to CorrectionType.BODY_ROTATION,
            binding.drillMenu.chipFollowThrough to CorrectionType.FOLLOW_THROUGH,
            binding.drillMenu.chipContactHeight to CorrectionType.CONTACT_HEIGHT,
            binding.drillMenu.chipElbow to CorrectionType.ELBOW_POSITION,
            binding.drillMenu.chipSpeed to CorrectionType.STROKE_SPEED,
            binding.drillMenu.chipKneeBend to CorrectionType.KNEE_BEND,
        )
        correctionChips.forEach { (chip, type) ->
            chip.isChecked = settingsManager.isCorrectionTypeEnabled(type)
            chip.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setCorrectionTypeEnabled(type, isChecked)
            }
        }
    }

    private fun setupFeedbackSettingsCollapse() {
        val content = binding.drillMenu.groupFeedbackSettingsContent
        val chevron = binding.drillMenu.ivFeedbackSettingsChevron
        content.visibility = View.GONE // collapsed by default each time the screen opens
        chevron.rotation = 0f

        binding.drillMenu.headerFeedbackSettings.setOnClickListener {
            val expanding = content.visibility != View.VISIBLE
            content.visibility = if (expanding) View.VISIBLE else View.GONE
            chevron.animate().rotation(if (expanding) 180f else 0f).setDuration(150).start()
        }
    }

    private fun styleSegment(btn: MaterialButton, active: Boolean) {
        val ctx = activity
        val bgColor = if (active) R.color.ttc_gold_container else android.R.color.transparent
        val textColor = if (active) R.color.ttc_gold_accent else R.color.ttc_text_2
        val font = if (active) R.font.inter_tight_bold else R.font.inter_tight_semibold
        btn.backgroundTintList = ContextCompat.getColorStateList(ctx, bgColor)
        btn.setTextColor(ContextCompat.getColor(ctx, textColor))
        btn.strokeColor = ContextCompat.getColorStateList(ctx, R.color.ttc_gold_container_outline)
        btn.strokeWidth = if (active) ctx.resources.displayMetrics.density.toInt().coerceAtLeast(1) else 0
        btn.typeface = ResourcesCompat.getFont(ctx, font)
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
        
        feedbackAdapter.updateFeedback(stateManager.getFeedbackCounts())
        binding.drillMenu.tvFlagged.text = activity.getString(R.string.live_flagged_count, stateManager.getFlaggedTotal())
    }

    private fun currentLang(): FeedbackLang =
        if (LocaleHelper.getSavedLanguage(activity) == "uk") FeedbackLang.UA else FeedbackLang.EN

    private fun showFeedbackExplanation(type: CorrectionType, count: Int) {
        val lang = currentLang()
        val recentMessages = stateManager.getRecentMessagesFor(type).map { message ->
            LiveFeedbackCatalog.resolve(message, short = false, lang = lang) ?: message
        }
        val sheet = FeedbackExplanationSheet.newInstance(
            type = type,
            flaggedCount = count,
            lang = lang,
            recentMessages = recentMessages
        )
        sheet.show(activity.supportFragmentManager, FeedbackExplanationSheet.TAG)
    }

}
