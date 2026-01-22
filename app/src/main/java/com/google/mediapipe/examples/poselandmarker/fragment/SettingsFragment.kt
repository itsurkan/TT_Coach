package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentSettingsBinding
import com.google.mediapipe.examples.poselandmarker.managers.SettingsManager

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
        setupAudioSettings()
        setupCameraSettings()
    }

    private fun setupAITrainerSettings() {
        // Coach style spinner
        val coachingStyles = resources.getStringArray(R.array.coaching_styles)
        val coachAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, coachingStyles)
        coachAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCoachStyle.adapter = coachAdapter

        val currentCoachStyle = settingsManager.getCoachingStyle()
        binding.spinnerCoachStyle.setSelection(currentCoachStyle.ordinal)

        // Update coach info card
        updateCoachInfoCard(currentCoachStyle)

        binding.spinnerCoachStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedStyle = com.google.mediapipe.examples.poselandmarker.models.CoachingStyle.fromOrdinal(position)
                settingsManager.setCoachingStyle(selectedStyle)
                updateCoachInfoCard(selectedStyle)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateCoachInfoCard(coachStyle: com.google.mediapipe.examples.poselandmarker.models.CoachingStyle) {
        binding.tvCoachAvatar.text = coachStyle.avatarInitial
        binding.tvCoachName.text = coachStyle.displayName
        binding.tvCoachStyle.text = coachStyle.subtitle
        binding.tvCoachDesc.text = coachStyle.description
        
        // Set avatar background color dynamically
        binding.tvCoachAvatar.backgroundTintList = 
            requireContext().getColorStateList(coachStyle.avatarColor)
    }

    private fun setupAudioSettings() {
        // Audio feedback switch
        binding.switchAudioFeedback.isChecked = settingsManager.isAudioFeedbackEnabled()
        binding.switchAudioFeedback.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setAudioFeedbackEnabled(isChecked)
            binding.seekBarVolume.isEnabled = isChecked
        }

        // Volume slider (0-100)
        val currentVolume = settingsManager.getFeedbackVolume()
        binding.seekBarVolume.progress = currentVolume
        binding.tvVolumeValue.text = "$currentVolume%"
        binding.seekBarVolume.isEnabled = settingsManager.isAudioFeedbackEnabled()

        binding.seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvVolumeValue.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    settingsManager.setFeedbackVolume(it.progress)
                }
            }
        })
    }

    private fun setupCameraSettings() {
        // Video quality spinner (uses index)
        val resolutions = resources.getStringArray(R.array.camera_resolutions)
        val qualityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, resolutions)
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerVideoQuality.adapter = qualityAdapter

        val currentResolutionIndex = settingsManager.getCameraResolution()
        if (currentResolutionIndex in resolutions.indices) {
            binding.spinnerVideoQuality.setSelection(currentResolutionIndex)
        }

        binding.spinnerVideoQuality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.setCameraResolution(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // FPS spinner
        val fpsOptions = arrayOf("24 FPS - Standard", "30 FPS - Recommended", "60 FPS - Smooth")
        val fpsValues = intArrayOf(24, 30, 60)
        val fpsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, fpsOptions)
        fpsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFps.adapter = fpsAdapter

        val currentFps = settingsManager.getTargetFps()
        val fpsIndex = fpsValues.indexOf(currentFps)
        if (fpsIndex >= 0) {
            binding.spinnerFps.setSelection(fpsIndex)
        }

        binding.spinnerFps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.setTargetFps(fpsValues[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
