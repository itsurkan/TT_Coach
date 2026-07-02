package com.ttcoachai.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ttcoachai.R
import com.ttcoachai.databinding.FragmentSettingsBinding
import com.ttcoachai.managers.SettingsManager

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
        val currentCoachStyle = settingsManager.getCoachingStyle()

        // Check corresponding button
        when (currentCoachStyle) {
            com.ttcoachai.models.CoachingStyle.GENTLE_SUPPORTIVE -> binding.toggleCoachStyle.check(R.id.btn_coach_vadym)
            com.ttcoachai.models.CoachingStyle.MOTIVATIONAL_ENERGETIC -> binding.toggleCoachStyle.check(R.id.btn_coach_Ivan)
            com.ttcoachai.models.CoachingStyle.PRECISE_TECHNICAL -> binding.toggleCoachStyle.check(R.id.btn_coach_Andriy)
        }

        // Update coach info card initially
        updateCoachInfoCard(currentCoachStyle)

        binding.toggleCoachStyle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val selectedStyle = when (checkedId) {
                    R.id.btn_coach_vadym -> com.ttcoachai.models.CoachingStyle.GENTLE_SUPPORTIVE
                    R.id.btn_coach_Ivan -> com.ttcoachai.models.CoachingStyle.MOTIVATIONAL_ENERGETIC
                    else -> com.ttcoachai.models.CoachingStyle.PRECISE_TECHNICAL
                }
                settingsManager.setCoachingStyle(selectedStyle)
                updateCoachInfoCard(selectedStyle)
            }
        }
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
        binding.toggleInterfaceLang.check(
            if (settingsManager.getLanguageCode() == "uk") R.id.btn_iface_uk else R.id.btn_iface_en
        )
        binding.toggleInterfaceLang.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                settingsManager.setLanguageCode(if (checkedId == R.id.btn_iface_uk) "uk" else "en")
            }
        }

        // Coach language
        binding.toggleCoachLang.check(
            if (settingsManager.getCoachLanguage() == "uk") R.id.btn_coach_lang_uk else R.id.btn_coach_lang_en
        )
        binding.toggleCoachLang.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                settingsManager.setCoachLanguage(if (checkedId == R.id.btn_coach_lang_uk) "uk" else "en")
            }
        }
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

        // FPS Toggle Group
        val currentFps = settingsManager.getTargetFps()
        when (currentFps) {
            24 -> binding.toggleFps.check(R.id.btn_fps_24)
            30 -> binding.toggleFps.check(R.id.btn_fps_30)
            60 -> binding.toggleFps.check(R.id.btn_fps_60)
        }

        binding.toggleFps.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val fps = when (checkedId) {
                    R.id.btn_fps_24 -> 24
                    R.id.btn_fps_30 -> 30
                    else -> 60
                }
                settingsManager.setTargetFps(fps)
            }
        }

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
