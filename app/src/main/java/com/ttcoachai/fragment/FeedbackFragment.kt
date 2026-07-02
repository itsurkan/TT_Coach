package com.ttcoachai.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
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
        binding.togglePlayingHand.check(
            if (sm.isPlayingHandRight()) R.id.btn_hand_right else R.id.btn_hand_left
        )
        binding.togglePlayingHand.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                sm.setPlayingHandRight(checkedId == R.id.btn_hand_right)
            }
        }

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
        binding.toggleCuesPerSession.check(
            when (sm.getFeedbackFrequency()) {
                5 -> R.id.btn_cues_5
                10 -> R.id.btn_cues_10
                else -> R.id.btn_cues_3
            }
        )
        binding.toggleCuesPerSession.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                sm.setFeedbackFrequency(
                    when (checkedId) {
                        R.id.btn_cues_5 -> 5
                        R.id.btn_cues_10 -> 10
                        else -> 3
                    }
                )
            }
        }

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
