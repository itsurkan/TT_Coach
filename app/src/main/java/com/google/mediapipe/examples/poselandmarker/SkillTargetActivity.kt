package com.google.mediapipe.examples.poselandmarker

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.mediapipe.examples.poselandmarker.databinding.ActivitySkillTargetBinding
import com.google.mediapipe.examples.poselandmarker.managers.SettingsManager

class SkillTargetActivity : BaseActivity() {

    private lateinit var binding: ActivitySkillTargetBinding
    private lateinit var settingsManager: SettingsManager
    private var selectedTarget: Int = 90
    private lateinit var targetButtons: List<Pair<MaterialCardView, Int>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySkillTargetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(true)
        }

        // Load current setting
        selectedTarget = settingsManager.getSkillTarget()
        
        setupSlider()
        setupQuickTargetButtons()
        setupSaveButton()
        updateUI()
    }

    private fun setupSlider() {
        binding.sliderTarget.value = selectedTarget.toFloat()
        
        binding.sliderTarget.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                selectedTarget = value.toInt()
                updateUI()
            }
        }
    }

    private fun setupQuickTargetButtons() {
        targetButtons = listOf(
            binding.btnTarget70 to 70,
            binding.btnTarget80 to 80,
            binding.btnTarget90 to 90,
            binding.btnTarget100 to 100
        )

        targetButtons.forEach { (button, value) ->
            button.setOnClickListener {
                selectedTarget = value
                binding.sliderTarget.value = value.toFloat()
                updateUI()
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            settingsManager.setSkillTarget(selectedTarget)
            Toast.makeText(this, getString(R.string.target_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateUI() {
        // Update current target display
        binding.tvCurrentTarget.text = selectedTarget.toString()
        binding.tvTargetValue.text = selectedTarget.toString()

        // Update target value color based on value
        val targetColor = when {
            selectedTarget >= 90 -> getColor(R.color.success_green)
            selectedTarget >= 80 -> getColor(R.color.primary_blue)
            else -> getColor(R.color.purple_500)
        }
        binding.tvTargetValue.setTextColor(targetColor)

        // Update quick target button states
        targetButtons.forEach { (button, value) ->
            updateTargetButtonState(button, selectedTarget == value)
        }

        // Update progress bar - show gap between current skill (86) and target
        val currentSkill = 86 // This would come from actual user data
        binding.tvCurrentSkill.text = currentSkill.toString()
        binding.progressSkill.max = selectedTarget
        binding.progressSkill.progress = currentSkill.coerceAtMost(selectedTarget)
    }

    private fun updateTargetButtonState(card: MaterialCardView, isSelected: Boolean) {
        if (isSelected) {
            card.strokeWidth = resources.getDimensionPixelSize(R.dimen.card_stroke_selected)
            card.strokeColor = getColor(R.color.primary_blue)
        } else {
            card.strokeWidth = resources.getDimensionPixelSize(R.dimen.card_stroke_normal)
            card.strokeColor = getColor(android.R.color.darker_gray)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
