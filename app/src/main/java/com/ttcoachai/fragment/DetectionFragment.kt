package com.ttcoachai.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ttcoachai.R
import com.ttcoachai.databinding.FragmentDetectionBinding
import com.ttcoachai.managers.SettingsManager

class DetectionFragment : Fragment() {

    private var _binding: FragmentDetectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sm = SettingsManager(requireContext())

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.stepperCameraAngle.apply {
            value = sm.getDetCameraAngle().toDouble()
            onValueChanged = { sm.setDetCameraAngle(it.toInt()) }
        }

        binding.stepperPeakSpeed.apply {
            value = sm.getDetPeakSpeed().toDouble()
            onValueChanged = { sm.setDetPeakSpeed(it.toFloat()) }
        }

        binding.stepperMinInterval.apply {
            value = sm.getDetMinPeakIntervalMs().toDouble()
            onValueChanged = { sm.setDetMinPeakIntervalMs(it.toInt()) }
        }

        binding.stepperSmoothing.apply {
            value = sm.getDetSpeedSmoothingMs().toDouble()
            onValueChanged = { sm.setDetSpeedSmoothingMs(it.toInt()) }
        }

        binding.stepperWalkGate.apply {
            value = sm.getDetWalkGate().toDouble()
            onValueChanged = { sm.setDetWalkGate(it.toFloat()) }
        }

        binding.switchSkipStale.isChecked = sm.isDetSkipStaleReps()
        binding.switchSkipStale.setOnCheckedChangeListener { _, isChecked ->
            sm.setDetSkipStaleReps(isChecked)
        }

        binding.stepperPrestroke.apply {
            value = sm.getDetPreStrokeBufferMs().toDouble()
            onValueChanged = { sm.setDetPreStrokeBufferMs(it.toInt()) }
        }

        when (sm.getBallDetectionFps()) {
            10 -> binding.toggleBallFps.check(R.id.btn_ball_fps_10)
            30 -> binding.toggleBallFps.check(R.id.btn_ball_fps_30)
            60 -> binding.toggleBallFps.check(R.id.btn_ball_fps_60)
            120 -> binding.toggleBallFps.check(R.id.btn_ball_fps_120)
            else -> binding.toggleBallFps.check(R.id.btn_ball_fps_30)
        }

        binding.toggleBallFps.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val fps = when (checkedId) {
                    R.id.btn_ball_fps_10 -> 10
                    R.id.btn_ball_fps_30 -> 30
                    R.id.btn_ball_fps_60 -> 60
                    R.id.btn_ball_fps_120 -> 120
                    else -> 30
                }
                sm.setBallDetectionFps(fps)
            }
        }

        binding.switchDistanceMode.isChecked = sm.isDistanceModeEnabled()
        binding.switchDistanceMode.setOnCheckedChangeListener { _, isChecked ->
            sm.setDistanceModeEnabled(isChecked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
