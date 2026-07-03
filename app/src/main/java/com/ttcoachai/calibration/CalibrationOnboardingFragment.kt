package com.ttcoachai.calibration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ttcoachai.R
import com.ttcoachai.databinding.FragmentCalibrationOnboardingBinding
import com.ttcoachai.managers.CalibrationStateManager
import java.util.concurrent.TimeUnit

/**
 * Step 1 — camera setup guidance and the "Start Capture" CTA (FR-9).
 *
 * For MVP we show static text and trust the user to self-confirm position.
 * Live full-body visibility gating via pose landmarker is a follow-up after
 * the end-to-end flow is validated on a real device.
 */
class CalibrationOnboardingFragment : Fragment() {

    private var _binding: FragmentCalibrationOnboardingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalibrationOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvInstructions.text = getString(
            R.string.calibration_onboarding_instructions,
            CalibrationStateManager.DEFAULT_TARGET_REPS
        )
        binding.btnStartCapture.text = buttonLabelForExistingBaseline()
        binding.btnStartCapture.setOnClickListener {
            (activity as? CalibrationActivity)?.beginCapture()
        }
    }

    private fun buttonLabelForExistingBaseline(): String {
        val args = arguments ?: return getString(R.string.calibration_onboarding_start)
        val existingRepCount = args.getInt(ARG_EXISTING_REP_COUNT, -1)
        if (existingRepCount <= 0) return getString(R.string.calibration_onboarding_start)
        val createdAtMs = args.getLong(ARG_EXISTING_CREATED_AT_MS, 0L)
        val ageDays = TimeUnit.MILLISECONDS
            .toDays((System.currentTimeMillis() - createdAtMs).coerceAtLeast(0))
            .toInt()
        return getString(R.string.calibration_cta_recalibrate, existingRepCount, ageDays)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_EXISTING_REP_COUNT = "existing_rep_count"
        const val ARG_EXISTING_CREATED_AT_MS = "existing_created_at_ms"
    }
}
