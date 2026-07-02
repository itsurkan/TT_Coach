package com.ttcoachai.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.ttcoachai.R
import com.ttcoachai.databinding.FragmentSettingsBinding
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.models.CoachingStyle

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())
        setupAITrainerSettings()
        setupLanguageSettings()
        setupFeedbackDetectionLinks()
        setupCameraSettings()
    }

    private fun setupAITrainerSettings() {
        val coachButtons = listOf(binding.btnCoachVadym, binding.btnCoachIvan, binding.btnCoachAndriy)

        fun select(style: CoachingStyle, persist: Boolean) {
            val selected = when (style) {
                CoachingStyle.GENTLE_SUPPORTIVE -> binding.btnCoachVadym
                CoachingStyle.MOTIVATIONAL_ENERGETIC -> binding.btnCoachIvan
                CoachingStyle.PRECISE_TECHNICAL -> binding.btnCoachAndriy
            }
            coachButtons.forEach { styleSegment(it, it === selected) }
            updateCoachInfoCard(style)
            if (persist) settingsManager.setCoachingStyle(style)
        }

        binding.btnCoachVadym.setOnClickListener { select(CoachingStyle.GENTLE_SUPPORTIVE, persist = true) }
        binding.btnCoachIvan.setOnClickListener { select(CoachingStyle.MOTIVATIONAL_ENERGETIC, persist = true) }
        binding.btnCoachAndriy.setOnClickListener { select(CoachingStyle.PRECISE_TECHNICAL, persist = true) }

        select(settingsManager.getCoachingStyle(), persist = false)
    }

    /**
     * Active segment = muted gold-container pill (#221C0F) with a subtle gold-brown outline and
     * bold bright-gold text; inactive = transparent with muted secondary text. Each button keeps
     * its own 999dp corners (plain LinearLayout track, not MaterialButtonToggleGroup) so the
     * selected pill is fully rounded in every position.
     */
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

    private fun updateCoachInfoCard(coachStyle: com.ttcoachai.models.CoachingStyle) {
        binding.tvCoachAvatar.text = coachStyle.avatarInitial
        binding.tvCoachName.text = getString(coachStyle.displayNameResId)
        binding.tvCoachStyle.text = getString(coachStyle.subtitleResId)
        binding.tvCoachDesc.text = getString(coachStyle.descriptionResId)

        // Set avatar background color dynamically
        binding.tvCoachAvatar.backgroundTintList =
            requireContext().getColorStateList(coachStyle.avatarColor)
    }

    private fun setupLanguageSettings() {
        // Interface language (store-only, no recreate)
        val ifaceButtons = listOf(binding.btnIfaceEn, binding.btnIfaceUk)
        fun selectIface(uk: Boolean, persist: Boolean) {
            val selected = if (uk) binding.btnIfaceUk else binding.btnIfaceEn
            ifaceButtons.forEach { styleSegment(it, it === selected) }
            if (persist) settingsManager.setLanguageCode(if (uk) "uk" else "en")
        }
        binding.btnIfaceEn.setOnClickListener { selectIface(uk = false, persist = true) }
        binding.btnIfaceUk.setOnClickListener { selectIface(uk = true, persist = true) }
        selectIface(uk = settingsManager.getLanguageCode() == "uk", persist = false)

        // Coach language
        val coachLangButtons = listOf(binding.btnCoachLangEn, binding.btnCoachLangUk)
        fun selectCoachLang(uk: Boolean, persist: Boolean) {
            val selected = if (uk) binding.btnCoachLangUk else binding.btnCoachLangEn
            coachLangButtons.forEach { styleSegment(it, it === selected) }
            if (persist) settingsManager.setCoachLanguage(if (uk) "uk" else "en")
        }
        binding.btnCoachLangEn.setOnClickListener { selectCoachLang(uk = false, persist = true) }
        binding.btnCoachLangUk.setOnClickListener { selectCoachLang(uk = true, persist = true) }
        selectCoachLang(uk = settingsManager.getCoachLanguage() == "uk", persist = false)
    }

    private fun setupFeedbackDetectionLinks() {
        binding.cardFeedback.setOnClickListener {
            findNavController().navigate(R.id.navigation_feedback)
        }
        binding.cardDetection.setOnClickListener {
            findNavController().navigate(R.id.navigation_detection)
        }
    }

    private fun setupCameraSettings() {
        // Video quality exposed dropdown
        val resolutions = resources.getStringArray(R.array.camera_resolutions)
        val qualityAdapter = ArrayAdapter(requireContext(), R.layout.list_item_dropdown, resolutions)
        binding.autoCompleteVideoQuality.setAdapter(qualityAdapter)

        val currentResolutionIndex = settingsManager.getCameraResolution()
        if (currentResolutionIndex in resolutions.indices) {
            binding.autoCompleteVideoQuality.setText(resolutions[currentResolutionIndex], false)
        }

        binding.autoCompleteVideoQuality.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            settingsManager.setCameraResolution(position)
        }

        // FPS pill segmented control
        val fpsButtons = listOf(binding.btnFps24, binding.btnFps30, binding.btnFps60)
        fun selectFps(fps: Int, persist: Boolean) {
            val selected = when (fps) {
                24 -> binding.btnFps24
                30 -> binding.btnFps30
                else -> binding.btnFps60
            }
            fpsButtons.forEach { styleSegment(it, it === selected) }
            if (persist) settingsManager.setTargetFps(fps)
        }
        binding.btnFps24.setOnClickListener { selectFps(24, persist = true) }
        binding.btnFps30.setOnClickListener { selectFps(30, persist = true) }
        binding.btnFps60.setOnClickListener { selectFps(60, persist = true) }
        selectFps(settingsManager.getTargetFps(), persist = false)

        // Pose skeleton switch
        binding.switchPoseSkeleton.isChecked = settingsManager.isShowSkeleton()
        binding.switchPoseSkeleton.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setShowSkeleton(isChecked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
