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

        // Ball detection FPS
        val fpsButtons = listOf(binding.btnBallFps10, binding.btnBallFps30, binding.btnBallFps60, binding.btnBallFps120)
        fun selectBallFps(fps: Int, persist: Boolean) {
            val selected = when (fps) {
                10 -> binding.btnBallFps10
                60 -> binding.btnBallFps60
                120 -> binding.btnBallFps120
                else -> binding.btnBallFps30
            }
            fpsButtons.forEach { styleSegment(it, it === selected) }
            if (persist) sm.setBallDetectionFps(fps)
        }
        binding.btnBallFps10.setOnClickListener { selectBallFps(10, persist = true) }
        binding.btnBallFps30.setOnClickListener { selectBallFps(30, persist = true) }
        binding.btnBallFps60.setOnClickListener { selectBallFps(60, persist = true) }
        binding.btnBallFps120.setOnClickListener { selectBallFps(120, persist = true) }
        selectBallFps(sm.getBallDetectionFps(), persist = false)

        binding.switchDistanceMode.isChecked = sm.isDistanceModeEnabled()
        binding.switchDistanceMode.setOnCheckedChangeListener { _, isChecked ->
            sm.setDistanceModeEnabled(isChecked)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
