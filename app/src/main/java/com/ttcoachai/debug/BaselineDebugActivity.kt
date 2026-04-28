package com.ttcoachai.debug

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.ttcoachai.BaseActivity
import com.ttcoachai.R
import com.ttcoachai.calibration.CalibrationActivity
import com.ttcoachai.databinding.ActivityBaselineDebugBinding
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.repository.PersonalBaselineRepository
import com.ttcoachai.shared.models.PersonalBaseline
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Dev-only inspector for the active PersonalBaseline.
 *
 * Gated with a runtime debuggable-APK check (the project doesn't enable
 * BuildConfig, so we fall back to `ApplicationInfo.FLAG_DEBUGGABLE`). Release
 * builds finish immediately with a toast instead of rendering the dump.
 *
 * Full histogram rendering (per tasks.md T029) is out of scope here — this
 * renders the plain-text dump used for adb-side verification; swap in a chart
 * view when the rule engine lands and the debug UX needs more than numbers.
 */
class BaselineDebugActivity : BaseActivity() {

    private lateinit var binding: ActivityBaselineDebugBinding
    private val repository by lazy {
        PersonalBaselineRepository(AppDatabase.getDatabase(this).personalBaselineDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBaselineDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!isDebuggable()) {
            Toast.makeText(this, R.string.debug_baseline_not_debuggable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val drillType = intent.getStringExtra(EXTRA_DRILL_TYPE)
            ?: CalibrationActivity.DRILL_FOREHAND_SHADOW
        binding.tvDrill.text = "drillType=$drillType"
        lifecycleScope.launch { loadAndRender(drillType) }
    }

    private suspend fun loadAndRender(drillType: String) {
        val baseline = repository.getActiveBaseline(drillType).first()
        if (baseline == null) {
            binding.tvEmpty.visibility = android.view.View.VISIBLE
            binding.tvMetricDump.visibility = android.view.View.GONE
            return
        }
        binding.tvMetricDump.text = renderDump(baseline)
    }

    private fun renderDump(b: PersonalBaseline): String = buildString {
        appendLine("createdAtMs=${b.createdAtMs}")
        appendLine("repCount=${b.repCount}")
        appendLine("qualityScore=${"%.3f".format(b.qualityScore)}")
        appendLine("handedness=${b.drillerHandedness ?: "—"}")
        appendLine("excludedRepIndices=${b.excludedRepIndices}")
        appendLine()
        appendLine("== Technique metrics ==")
        for ((k, s) in b.metricStats) {
            appendLine(
                "$k: mean=${"%.2f".format(s.mean)} std=${"%.2f".format(s.std)} " +
                    "min=${"%.2f".format(s.min)} max=${"%.2f".format(s.max)} n=${s.sampleCount}"
            )
        }
        appendLine()
        appendLine("== Phase durations (ms) ==")
        for ((k, s) in b.phaseDurationsMs) {
            appendLine(
                "$k: mean=${"%.1f".format(s.mean)} std=${"%.1f".format(s.std)} " +
                    "min=${"%.1f".format(s.min)} max=${"%.1f".format(s.max)} n=${s.sampleCount}"
            )
        }
    }

    private fun isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    companion object {
        const val EXTRA_DRILL_TYPE = "drill_type"
    }
}
