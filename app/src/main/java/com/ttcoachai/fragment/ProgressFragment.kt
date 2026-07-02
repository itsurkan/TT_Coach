package com.ttcoachai.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.tabs.TabLayout
import com.ttcoachai.R
import com.ttcoachai.TTCoachApplication
import com.ttcoachai.databinding.FragmentProgressBinding
import com.ttcoachai.databinding.ItemSkillProgressBinding
import kotlinx.coroutines.launch

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCharts()
        setupSegmentedButtons()
        loadCloudData()
    }
    
    private fun loadCloudData() {
        val app = requireActivity().application as TTCoachApplication
        val dataLoader = com.ttcoachai.helpers.ProgressDataLoader(app.cloudSyncManager)
        
        viewLifecycleOwner.lifecycleScope.launch {
            val progressData = dataLoader.loadProgressData()
            
            if (progressData == null) {
                if (!app.cloudSyncManager.isAuthenticated) {
                    loadDummyData()
                } else {
                    // Logged in but no data yet, keep empty/zero state
                    android.util.Log.d("ProgressFragment", "Authenticated but no progress data found. Showing zero state.")
                }
                return@launch
            }
            
            // Apply loaded data to UI
            applyHeaderStats(progressData)
            applyChartData(progressData.weeklyChartData)
            applySkillsData(progressData.skillsData)
        }
    }
    
    private fun applyHeaderStats(progressData: com.ttcoachai.helpers.ProgressData) {
        val progress = progressData.userProgress
        val milestones = progressData.milestonesData
        
        binding.tvDaysStreak.text = progress?.currentStreak?.toString() ?: "0"
        
        // Total Hours (formatted as 1 decimals if needed, or just integer)
        val totalMinutes = progress?.totalTrainingMinutes ?: 0
        val totalHours = totalMinutes / 60f
        binding.tvTotalHours.text = if (totalHours >= 10) {
            totalHours.toInt().toString()
        } else {
            String.format("%.1f", totalHours)
        }
        
        // Achievements (count of achieved milestones)
        val achievementsCount = milestones.count { it.achieved }
        binding.tvAchievements.text = achievementsCount.toString()
    }
    
    private fun applyChartData(chartData: com.ttcoachai.helpers.WeeklyChartData) {
        // Bar Chart Data (Training Time)
        val barEntries = chartData.trainingMinutes.mapIndexed { index, minutes ->
            BarEntry(index.toFloat(), minutes)
        }
        
        val barDataLabel = getString(R.string.tab_training_time)
        val barDataSet = BarDataSet(barEntries, barDataLabel).apply {
            color = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            setDrawValues(false)
        }
        
        binding.trainingBarChart.data = BarData(barDataSet)
        binding.trainingBarChart.invalidate()

        // Line Chart Data (Accuracy)
        val lineEntries = chartData.accuracyPercentages.mapIndexed { index, accuracy ->
            Entry(index.toFloat(), accuracy)
        }
        
        val lineDataLabel = getString(R.string.tab_accuracy)
        val lineDataSet = LineDataSet(lineEntries, lineDataLabel).apply {
            color = ContextCompat.getColor(requireContext(), R.color.colorTertiary)
            lineWidth = 3f
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.colorTertiary))
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawCircleHole(false)
        }

        binding.accuracyLineChart.data = LineData(lineDataSet)
        binding.accuracyLineChart.invalidate()
    }
    
    private fun applySkillsData(skillsData: Map<String, Int>) {
        binding.skillsContainer.removeAllViews()

        // Trend (rank change) is not carried by cloud progress data yet, so it is hidden here.
        addSkillRow(getString(R.string.exercise_forehand_name), skillsData["forehand_drive"] ?: 0, R.drawable.ic_skill_forehand, null)
        addSkillRow(getString(R.string.exercise_backhand_name), skillsData["backhand_drive"] ?: 0, R.drawable.ic_skill_backhand, null)
        addSkillRow(getString(R.string.exercise_service_name), skillsData["service"] ?: 0, R.drawable.ic_skill_topspin, null)
        addSkillRow(getString(R.string.exercise_footwork_name), skillsData["footwork"] ?: 0, R.drawable.ic_skill_footwork, null)
    }

    /** Inflates one skill row: icon tile + name + percentage + gold bar, with an optional green/red trend chip. */
    private fun addSkillRow(name: String, level: Int, iconRes: Int, trend: Int?) {
        val skillBinding = ItemSkillProgressBinding.inflate(layoutInflater, binding.skillsContainer, false)
        skillBinding.tvSkillName.text = name
        skillBinding.tvSkillLevel.text = "$level%"
        skillBinding.progressSkill.progress = level
        skillBinding.ivSkillIcon.setImageResource(iconRes)

        val trendView = skillBinding.tvSkillTrend
        if (trend == null || trend == 0) {
            trendView.visibility = View.GONE
        } else {
            trendView.visibility = View.VISIBLE
            val up = trend > 0
            trendView.text = kotlin.math.abs(trend).toString()
            val color = ContextCompat.getColor(
                requireContext(),
                if (up) R.color.ttc_success else R.color.ttc_error
            )
            trendView.setTextColor(color)
            trendView.setCompoundDrawablesWithIntrinsicBounds(
                if (up) R.drawable.ic_trend_up else R.drawable.ic_trend_down, 0, 0, 0
            )
            TextViewCompat.setCompoundDrawableTintList(trendView, ColorStateList.valueOf(color))
        }
        binding.skillsContainer.addView(skillBinding.root)
    }
    
    private fun setupCharts() {
        val days = listOf(
            getString(R.string.days_mon),
            getString(R.string.days_tue),
            getString(R.string.days_wed),
            getString(R.string.days_thu),
            getString(R.string.days_fri),
            getString(R.string.days_sat),
            getString(R.string.days_sun)
        )
        
        // Setup BarChart (Training Time)
        binding.trainingBarChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(false) // Disable interaction for simple view
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                valueFormatter = IndexAxisValueFormatter(days)
                textColor = ContextCompat.getColor(context, R.color.text_muted)
                textSize = 12f
            }
            
            axisLeft.apply {
                setDrawGridLines(false) // Clean look
                setDrawAxisLine(false)
                setDrawLabels(true)
                textColor = ContextCompat.getColor(context, R.color.text_muted)
            }
            axisRight.isEnabled = false
        }

        // Setup LineChart (Accuracy)
        binding.accuracyLineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false) // Vertical grid lines off
                setDrawAxisLine(false)
                valueFormatter = IndexAxisValueFormatter(days)
                textColor = ContextCompat.getColor(context, R.color.text_muted)
                textSize = 12f
            }

            axisLeft.apply {
                setDrawGridLines(true) // Horizontal grid lines on
                gridColor = ContextCompat.getColor(context, R.color.toolbar_background) // Light gray
                setDrawAxisLine(false)
                axisMinimum = 70f
                axisMaximum = 100f
                textColor = ContextCompat.getColor(context, R.color.text_muted)
            }
            axisRight.isEnabled = false
        }
    }

    private fun setupSegmentedButtons() {
        binding.btnTrainingTime.setOnClickListener { selectChartTab(training = true) }
        binding.btnAccuracy.setOnClickListener { selectChartTab(training = false) }
        selectChartTab(training = true)
    }

    private fun selectChartTab(training: Boolean) {
        styleSegment(binding.btnTrainingTime, training)
        styleSegment(binding.btnAccuracy, !training)

        if (training) {
            binding.trainingBarChart.visibility = View.VISIBLE
            binding.accuracyLineChart.visibility = View.GONE
            binding.tvChartTitle.text = getString(R.string.chart_training_title)
            binding.tvChartSubtitle.text = getString(R.string.chart_training_subtitle)
            binding.trainingBarChart.animateY(1000)
        } else {
            binding.trainingBarChart.visibility = View.GONE
            binding.accuracyLineChart.visibility = View.VISIBLE
            binding.tvChartTitle.text = getString(R.string.chart_accuracy_title)
            binding.tvChartSubtitle.text = getString(R.string.chart_accuracy_subtitle)
            binding.accuracyLineChart.animateX(1000)
        }
    }

    /** Active segment = bright-gold pill with dark text; inactive = transparent with muted text. */
    private fun styleSegment(btn: com.google.android.material.button.MaterialButton, active: Boolean) {
        val bg = if (active) R.color.ttc_gold_bright else android.R.color.transparent
        val txt = if (active) R.color.ttc_on_gold else R.color.ttc_text_2
        btn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), bg)
        btn.setTextColor(ContextCompat.getColor(requireContext(), txt))
    }

    private fun loadDummyData() {
        // Bar Chart Data (Training Time)
        val barEntries = listOf(
            BarEntry(0f, 25f),
            BarEntry(1f, 30f),
            BarEntry(2f, 0f),
            BarEntry(3f, 35f),
            BarEntry(4f, 28f),
            BarEntry(5f, 45f),
            BarEntry(6f, 23f)
        )
        
        val barDataLabel = getString(R.string.tab_training_time)
        val barDataSet = BarDataSet(barEntries, barDataLabel).apply {
            color = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            setDrawValues(false)
        }
        
        binding.trainingBarChart.data = BarData(barDataSet)
        binding.trainingBarChart.invalidate()


        // Line Chart Data (Accuracy)
        val lineEntries = listOf(
            Entry(0f, 82f),
            Entry(1f, 85f),
            Entry(2f, 0f), 
            Entry(3f, 88f),
            Entry(4f, 87f),
            Entry(5f, 91f),
            Entry(6f, 87f)
        )
        
        val lineDataLabel = getString(R.string.tab_accuracy)
        val lineDataSet = LineDataSet(lineEntries, lineDataLabel).apply {
            color = ContextCompat.getColor(requireContext(), R.color.colorTertiary)
            lineWidth = 3f
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.colorTertiary))
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawCircleHole(false)
        }

        binding.accuracyLineChart.data = LineData(lineDataSet)
        binding.accuracyLineChart.invalidate()


        // LOAD SKILLS
        binding.skillsContainer.removeAllViews()
        addSkillRow(getString(R.string.exercise_forehand_name), 85, R.drawable.ic_skill_forehand, 4)
        addSkillRow(getString(R.string.exercise_backhand_name), 78, R.drawable.ic_skill_backhand, 3)
        addSkillRow(getString(R.string.exercise_service_name), 92, R.drawable.ic_skill_topspin, -2)
        addSkillRow(getString(R.string.exercise_footwork_name), 73, R.drawable.ic_skill_footwork, 1)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
