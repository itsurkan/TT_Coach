package com.ttcoachai.calibration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ttcoachai.R
import com.ttcoachai.databinding.FragmentCalibrationCaptureBinding
import com.ttcoachai.managers.CalibrationStateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Step 2 — live capture. Binds the rep counter to
 * `CalibrationStateManager.acceptedRepCount`; auto-advances to review at the
 * target rep count (spec Key Decision #6); exposes "Finish Early" once the
 * floor (10 reps) is reached. US3: surfaces a transient outlier banner
 * whenever the state manager emits an `OutlierDetected` event and shows the
 * running excluded-count subtext under the rep counter.
 */
class CalibrationCaptureFragment : Fragment() {

    private var _binding: FragmentCalibrationCaptureBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalibrationCaptureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = activity as? CalibrationActivity ?: return
        val state = host.calibrationStateManager
        val target = state.targetRepCount

        updateCounter(0, target)

        binding.btnFinishEarly.setOnClickListener { host.finishCapture() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.acceptedRepCount.collect { accepted ->
                    updateCounter(accepted, target)
                    binding.btnFinishEarly.visibility =
                        if (accepted >= CalibrationStateManager.MIN_REPS_TO_PERSIST) View.VISIBLE else View.GONE
                    if (accepted >= target) host.finishCapture()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.excludedCount.collect { excluded -> updateExcludedSubtext(excluded) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.outlierEvents.collect { event -> showOutlierBanner(event) }
            }
        }
    }

    private fun updateCounter(accepted: Int, target: Int) {
        binding.tvRepCounter.text = getString(R.string.calibration_rep_counter, accepted, target)
    }

    private fun updateExcludedSubtext(excluded: Int) {
        if (excluded <= 0) {
            binding.tvExcludedSubtext.visibility = View.GONE
        } else {
            binding.tvExcludedSubtext.visibility = View.VISIBLE
            binding.tvExcludedSubtext.text =
                getString(R.string.calibration_capture_excluded_subtext, excluded)
        }
    }

    private fun showOutlierBanner(event: CalibrationStateManager.OutlierDetected) {
        val banner = binding.tvOutlierBanner
        banner.text = getString(
            R.string.calibration_outlier_banner,
            event.metricKey,
            event.deviationSigmas
        )
        banner.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            delay(BANNER_DISPLAY_MS)
            if (isActive && _binding != null) binding.tvOutlierBanner.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val BANNER_DISPLAY_MS = 2500L
    }
}
