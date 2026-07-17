package com.ttcoachai.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.ttcoachai.R
import com.ttcoachai.databinding.FragmentFeedbackBinding
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.shared.models.CorrectionType

class FeedbackFragment : Fragment() {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sm = SettingsManager(requireContext())

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // ===== Coaching =====

        // Playing hand
        val handButtons = listOf(binding.btnHandRight, binding.btnHandLeft)
        fun selectHand(right: Boolean, persist: Boolean) {
            val selected = if (right) binding.btnHandRight else binding.btnHandLeft
            handButtons.forEach { styleSegment(it, it === selected) }
            if (persist) sm.setPlayingHandRight(right)
        }
        binding.btnHandRight.setOnClickListener { selectHand(right = true, persist = true) }
        binding.btnHandLeft.setOnClickListener { selectHand(right = false, persist = true) }
        selectHand(right = sm.isPlayingHandRight(), persist = false)

        // Voice cues + volume gating
        binding.switchVoiceCues.isChecked = sm.isAudioFeedbackEnabled()
        binding.sliderVoiceVolume.value = sm.getFeedbackVolume().toFloat()
        binding.sliderVoiceVolume.isEnabled = sm.isAudioFeedbackEnabled()
        binding.switchVoiceCues.setOnCheckedChangeListener { _, isChecked ->
            sm.setAudioFeedbackEnabled(isChecked)
            binding.sliderVoiceVolume.isEnabled = isChecked
        }
        binding.sliderVoiceVolume.addOnChangeListener { _, value, _ ->
            sm.setFeedbackVolume(value.toInt())
        }

        // ===== Corrections =====

        val correctionChips = listOf(
            R.id.chip_wrist to CorrectionType.WRIST,
            R.id.chip_rotation to CorrectionType.BODY_ROTATION,
            R.id.chip_follow_through to CorrectionType.FOLLOW_THROUGH,
            R.id.chip_contact_height to CorrectionType.CONTACT_HEIGHT,
            R.id.chip_elbow to CorrectionType.ELBOW_POSITION,
            R.id.chip_speed to CorrectionType.STROKE_SPEED,
            R.id.chip_knee_bend to CorrectionType.KNEE_BEND,
        )
        correctionChips.forEach { (chipId, type) ->
            val chip = binding.root.findViewById<com.google.android.material.chip.Chip>(chipId)
            chip.isChecked = sm.isCorrectionTypeEnabled(type)
            chip.setOnCheckedChangeListener { _, isChecked ->
                sm.setCorrectionTypeEnabled(type, isChecked)
            }
        }

        // ===== Cue zones =====

        // Zone width (float, x1 decimal)
        binding.sliderZoneWidth.value = sm.getFbZoneWidth()
        binding.tvZoneWidth.text = "×%.1f".format(sm.getFbZoneWidth())
        binding.sliderZoneWidth.addOnChangeListener { _, value, _ ->
            sm.setFbZoneWidth(value)
            binding.tvZoneWidth.text = "×%.1f".format(value)
        }

        // Significance (int degrees)
        binding.sliderSignificance.value = sm.getFbSignificanceDeg().toFloat()
        binding.tvSignificance.text = "${sm.getFbSignificanceDeg()}°"
        binding.sliderSignificance.addOnChangeListener { _, value, _ ->
            sm.setFbSignificanceDeg(value.toInt())
            binding.tvSignificance.text = "${value.toInt()}°"
        }

        // Alternate cues
        binding.switchAlternateCues.isChecked = sm.isFbAlternateCues()
        binding.switchAlternateCues.setOnCheckedChangeListener { _, isChecked ->
            sm.setFbAlternateCues(isChecked)
        }

        // ===== Cadence =====

        // Reminder interval (s <-> ms)
        binding.sliderReminder.value = sm.getFbReminderIntervalMs() / 1000f
        binding.tvReminder.text = "%.1fs".format(sm.getFbReminderIntervalMs() / 1000f)
        binding.sliderReminder.addOnChangeListener { _, value, _ ->
            sm.setFbReminderIntervalMs((value * 1000).toInt())
            binding.tvReminder.text = "%.1fs".format(value)
        }

        // Pause between cues (s <-> ms)
        binding.sliderPauseBetween.value = sm.getFbPauseBetweenMs() / 1000f
        binding.tvPauseBetween.text = "%.1fs".format(sm.getFbPauseBetweenMs() / 1000f)
        binding.sliderPauseBetween.addOnChangeListener { _, value, _ ->
            sm.setFbPauseBetweenMs((value * 1000).toInt())
            binding.tvPauseBetween.text = "%.1fs".format(value)
        }

        // Silence before praise (s <-> ms)
        binding.sliderSilencePraise.value = sm.getFbSilenceBeforePraiseMs() / 1000f
        binding.tvSilencePraise.text = "%.1fs".format(sm.getFbSilenceBeforePraiseMs() / 1000f)
        binding.sliderSilencePraise.addOnChangeListener { _, value, _ ->
            sm.setFbSilenceBeforePraiseMs((value * 1000).toInt())
            binding.tvSilencePraise.text = "%.1fs".format(value)
        }

        // Pause after stroke (s <-> ms)
        binding.sliderPauseAfter.value = sm.getFbPauseAfterStrokeMs() / 1000f
        binding.tvPauseAfter.text = "%.1fs".format(sm.getFbPauseAfterStrokeMs() / 1000f)
        binding.sliderPauseAfter.addOnChangeListener { _, value, _ ->
            sm.setFbPauseAfterStrokeMs((value * 1000).toInt())
            binding.tvPauseAfter.text = "%.1fs".format(value)
        }

        // Cues per session (3/5/10)
        val cuesButtons = listOf(binding.btnCues3, binding.btnCues5, binding.btnCues10)
        fun selectCues(count: Int, persist: Boolean) {
            val selected = when (count) {
                5 -> binding.btnCues5
                10 -> binding.btnCues10
                else -> binding.btnCues3
            }
            cuesButtons.forEach { styleSegment(it, it === selected) }
            if (persist) sm.setFeedbackFrequency(count)
        }
        binding.btnCues3.setOnClickListener { selectCues(3, persist = true) }
        binding.btnCues5.setOnClickListener { selectCues(5, persist = true) }
        binding.btnCues10.setOnClickListener { selectCues(10, persist = true) }
        selectCues(sm.getFeedbackFrequency(), persist = false)

        // ===== Praise =====

        binding.switchPraiseEnabled.isChecked = sm.isPraiseEnabled()
        applyPraiseEnabled(sm.isPraiseEnabled())
        binding.switchPraiseEnabled.setOnCheckedChangeListener { _, isChecked ->
            sm.setPraiseEnabled(isChecked)
            applyPraiseEnabled(isChecked)
        }

        binding.switchPraiseCorrections.isChecked = sm.isPraiseOnCorrections()
        binding.switchPraiseCorrections.setOnCheckedChangeListener { _, isChecked ->
            sm.setPraiseOnCorrections(isChecked)
        }

        binding.switchPraiseStreak.isChecked = sm.isPraiseOnStreak()
        binding.switchPraiseStreak.setOnCheckedChangeListener { _, isChecked ->
            sm.setPraiseOnStreak(isChecked)
        }

        binding.sliderStreakLen.value = sm.getPraiseStreakLen().toFloat()
        binding.tvStreakLen.text = sm.getPraiseStreakLen().toString()
        binding.sliderStreakLen.addOnChangeListener { _, value, _ ->
            sm.setPraiseStreakLen(value.toInt())
            binding.tvStreakLen.text = value.toInt().toString()
        }
    }

    private fun styleSegment(btn: MaterialButton, active: Boolean) {
        val ctx = requireContext()
        val bgColor = if (active) R.color.ttc_gold_container else android.R.color.transparent
        val textColor = if (active) R.color.ttc_gold_accent else R.color.ttc_text_2
        val font = if (active) R.font.inter_tight_bold else R.font.inter_tight_semibold
        btn.backgroundTintList = ContextCompat.getColorStateList(ctx, bgColor)
        btn.setTextColor(ContextCompat.getColor(ctx, textColor))
        btn.strokeColor = ContextCompat.getColorStateList(ctx, R.color.ttc_gold_container_outline)
        btn.strokeWidth = if (active) resources.displayMetrics.density.toInt().coerceAtLeast(1) else 0
        btn.typeface = ResourcesCompat.getFont(ctx, font)
    }

    private fun applyPraiseEnabled(enabled: Boolean) {
        setChildrenEnabled(binding.groupPraiseChildren, enabled)
    }

    private fun setChildrenEnabled(v: View, enabled: Boolean) {
        v.isEnabled = enabled
        v.alpha = if (enabled) 1f else 0.45f
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                setChildrenEnabled(v.getChildAt(i), enabled)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
