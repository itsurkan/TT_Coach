package com.google.mediapipe.examples.poselandmarker

import android.os.Bundle
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityWeeklySessionsBinding
import com.google.mediapipe.examples.poselandmarker.managers.SettingsManager

class WeeklySessionsActivity : BaseActivity() {

    private lateinit var binding: ActivityWeeklySessionsBinding
    private lateinit var settingsManager: SettingsManager
    private var selectedDays: Int = 7
    private lateinit var dayButtons: List<MaterialCardView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeeklySessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        // Setup back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Load current setting
        selectedDays = settingsManager.getWeeklySessionsGoal()
        
        setupDayButtons()
        setupPlanCards()
        setupSaveButton()
        updateUI()
    }

    private fun setupDayButtons() {
        dayButtons = listOf(
            binding.btnDay1,
            binding.btnDay2,
            binding.btnDay3,
            binding.btnDay4,
            binding.btnDay5,
            binding.btnDay6,
            binding.btnDay7
        )

        dayButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                selectedDays = index + 1
                updateUI()
            }
        }
    }

    private fun setupPlanCards() {
        binding.cardPlanBeginner.setOnClickListener {
            selectedDays = 3
            updateUI()
        }

        binding.cardPlanRegular.setOnClickListener {
            selectedDays = 5
            updateUI()
        }

        binding.cardPlanIntensive.setOnClickListener {
            selectedDays = 7
            updateUI()
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            settingsManager.setWeeklySessionsGoal(selectedDays)
            Toast.makeText(this, getString(R.string.goal_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateUI() {
        // Update current goal display
        binding.tvCurrentGoal.text = selectedDays.toString()

        // Update day button states
        val selectedBgColor = getColor(android.R.color.black)
        val normalBgColor = getColor(android.R.color.transparent)
        val selectedTextColor = getColor(android.R.color.white)
        val normalTextColor = getColor(R.color.text_primary)
        val borderColor = getColor(android.R.color.darker_gray)

        dayButtons.forEachIndexed { index, button ->
            val isSelected = index + 1 == selectedDays
            button.setCardBackgroundColor(if (isSelected) selectedBgColor else normalBgColor)
            button.strokeColor = if (isSelected) selectedBgColor else borderColor
            
            // Find TextView inside button
            val textView = button.getChildAt(0) as? android.widget.TextView
            textView?.setTextColor(if (isSelected) selectedTextColor else normalTextColor)
        }

        // Update plan card borders
        updatePlanCardState(binding.cardPlanBeginner, selectedDays == 3)
        updatePlanCardState(binding.cardPlanRegular, selectedDays == 5)
        updatePlanCardState(binding.cardPlanIntensive, selectedDays == 7)
    }

    private fun updatePlanCardState(card: MaterialCardView, isSelected: Boolean) {
        if (isSelected) {
            card.strokeWidth = resources.getDimensionPixelSize(R.dimen.card_stroke_selected)
            card.strokeColor = getColor(R.color.primary_blue)
        } else {
            card.strokeWidth = resources.getDimensionPixelSize(R.dimen.card_stroke_normal)
            card.strokeColor = getColor(android.R.color.darker_gray)
        }
    }
}
