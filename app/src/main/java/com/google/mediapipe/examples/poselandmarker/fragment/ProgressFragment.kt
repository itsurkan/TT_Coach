package com.google.mediapipe.examples.poselandmarker.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentProgressBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemMilestoneCardBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemSkillProgressBinding

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
        loadDummyData()
    }

    private fun setupCharts() {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        
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
        binding.toggleGroupChart.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_training_time -> {
                        binding.trainingBarChart.visibility = View.VISIBLE
                        binding.accuracyLineChart.visibility = View.GONE
                        binding.tvChartTitle.text = getString(R.string.chart_training_title)
                        binding.tvChartSubtitle.text = getString(R.string.chart_training_subtitle)
                        binding.trainingBarChart.animateY(1000)
                    }
                    R.id.btn_accuracy -> {
                        binding.trainingBarChart.visibility = View.GONE
                        binding.accuracyLineChart.visibility = View.VISIBLE
                        binding.tvChartTitle.text = getString(R.string.chart_accuracy_title)
                        binding.tvChartSubtitle.text = getString(R.string.chart_accuracy_subtitle)
                        binding.accuracyLineChart.animateX(1000)
                    }
                }
            }
        }
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
        
        val barDataSet = BarDataSet(barEntries, "Training Time").apply {
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
        
        val lineDataSet = LineDataSet(lineEntries, "Accuracy").apply {
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
        val skills = listOf(
            Triple("Forehand", 85, R.color.blue_600),
            Triple("Backhand", 78, R.color.blue_600),
            Triple("Serve", 92, R.color.blue_600),
            Triple("Footwork", 73, R.color.blue_600)
        )

        skills.forEach { (name, level, colorRes) ->
            val skillBinding = ItemSkillProgressBinding.inflate(layoutInflater, binding.skillsContainer, false)
            skillBinding.tvSkillName.text = name
            skillBinding.tvSkillLevel.text = "$level%"
            skillBinding.progressSkill.progress = level
            // We could change progress color dynamically but we set a drawable with gradient already.
            binding.skillsContainer.addView(skillBinding.root)
        }


        // LOAD MILESTONES
        data class Milestone(val title: String, val date: String, val iconRes: Int, val bgTint: Int, val iconTint: Int)
        
        val milestones = listOf(
            Milestone("100 Consecutive Hits", "2 days ago", R.drawable.ic_award, R.color.bg_card_target, R.color.text_card_target),
            Milestone("30-Day Streak", "5 days ago", R.drawable.ic_award, R.color.bg_card_calendar, R.color.text_card_calendar),
            Milestone("Master Server", "1 week ago", R.drawable.ic_award, R.color.bg_card_award, R.color.text_card_award)
        )

        milestones.forEach { milestone ->
            val msBinding = ItemMilestoneCardBinding.inflate(layoutInflater, binding.milestonesContainer, false)
            msBinding.tvMilestoneTitle.text = milestone.title
            msBinding.tvMilestoneDate.text = milestone.date
            msBinding.ivMilestoneIcon.setImageResource(milestone.iconRes)
            msBinding.ivMilestoneIcon.setColorFilter(ContextCompat.getColor(requireContext(), milestone.iconTint))
            msBinding.root.background.setTint(ContextCompat.getColor(requireContext(), milestone.bgTint))
            // Note: Background tint on layer-list might cover everything if not careful. 
            // The item_milestone_card uses bg_card_rounded which is a shape. setTint works on the drawable.
            // Better to find the shape and tint it, or set background color.
            // But we can just set backgroundTint if AP level allows or use ViewCompat.
             msBinding.root.backgroundTintList = ContextCompat.getColorStateList(requireContext(), milestone.bgTint)

            binding.milestonesContainer.addView(msBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
