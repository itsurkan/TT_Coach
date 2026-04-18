package com.ttcoachai.calibration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ttcoachai.R
import com.ttcoachai.databinding.FragmentCalibrationReviewBinding
import com.ttcoachai.shared.analysis.BaselineDeriver
import com.ttcoachai.shared.models.MetricStats
import com.ttcoachai.shared.models.PersonalBaseline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 3 — run BaselineDeriver on the captured snapshot, show metric summaries,
 * and let the user Save (persist via repository) or Redo.
 *
 * Low-quality gating (qualityScore < 0.6) surfaces a non-blocking banner per
 * spec Key Decision #5. Save is always available (FR-4 is gating, not blocking).
 */
class CalibrationReviewFragment : Fragment() {

    private var _binding: FragmentCalibrationReviewBinding? = null
    private val binding get() = _binding!!

    private var pendingBaseline: PersonalBaseline? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalibrationReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = activity as? CalibrationActivity ?: return
        val snapshot = host.calibrationStateManager.snapshot()

        if (snapshot == null || snapshot.strokes.isEmpty()) {
            showDeriveError(getString(R.string.calibration_insufficient_reps))
            return
        }

        val baseline = try {
            BaselineDeriver.derive(
                strokes = snapshot.strokes,
                analyses = snapshot.analyses,
                frameIntervalMs = snapshot.frameIntervalMs,
                drillType = snapshot.drillType,
                createdAtMs = System.currentTimeMillis(),
                minRepCount = CalibrationMinRepsForDerivation
            )
        } catch (e: IllegalArgumentException) {
            showDeriveError(getString(R.string.calibration_insufficient_reps))
            return
        }

        pendingBaseline = baseline
        renderBaseline(baseline)

        binding.btnSave.setOnClickListener { saveBaseline(host, baseline) }
        binding.btnRedo.setOnClickListener { host.redoCalibration() }
    }

    private fun renderBaseline(baseline: PersonalBaseline) {
        binding.tvQuality.text = getString(R.string.calibration_review_quality, baseline.qualityScore)
        binding.tvLowQualityBanner.visibility =
            if (baseline.qualityScore < QUALITY_LOW_THRESHOLD) View.VISIBLE else View.GONE

        binding.llMetrics.removeAllViews()
        for ((key, stats) in baseline.metricStats) addMetricRow(key, stats)
        for ((key, stats) in baseline.phaseDurationsMs) addMetricRow(key, stats)

        if (baseline.excludedRepIndices.isNotEmpty()) {
            binding.tvExcluded.visibility = View.VISIBLE
            binding.tvExcluded.text =
                getString(R.string.calibration_review_excluded, baseline.excludedRepIndices.size)
        }
    }

    private fun addMetricRow(key: String, stats: MetricStats) {
        val row = TextView(requireContext()).apply {
            setPadding(0, 4, 0, 4)
            text = getString(
                R.string.calibration_review_metric_row,
                key,
                stats.mean,
                stats.std,
                stats.sampleCount
            )
        }
        binding.llMetrics.addView(row)
    }

    private fun showDeriveError(message: String) {
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = message
        binding.btnSave.isEnabled = false
        binding.btnRedo.setOnClickListener {
            (activity as? CalibrationActivity)?.redoCalibration()
        }
    }

    private fun saveBaseline(host: CalibrationActivity, baseline: PersonalBaseline) {
        binding.btnSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { host.baselineRepository.saveBaseline(baseline) }
            host.exitAfterSave()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val QUALITY_LOW_THRESHOLD = 0.6

        // For Phase 1 we accept any session with ≥1 non-outlier rep so the review
        // screen can still surface low-quality sessions. The hard floor (10) is
        // enforced at the exit/back-navigation boundary in CalibrationActivity.
        private const val CalibrationMinRepsForDerivation = 1
    }
}
