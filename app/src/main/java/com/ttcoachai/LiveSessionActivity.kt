package com.ttcoachai

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.ttcoachai.databinding.ActivityLiveSessionBinding
import com.ttcoachai.databinding.ItemLiveFeedbackRowBinding

/**
 * Static "Live Session" capture screen (gold-dark redesign, frame 1a/1e).
 *
 * NO camera, NO pose inference, NO real-time feedback plumbing — this screen only renders
 * static sample data over the Task 2 layout. Live capture wiring lands in a later phase.
 */
class LiveSessionActivity : BaseActivity() {

    private lateinit var binding: ActivityLiveSessionBinding
    private var isPaused = false

    companion object {
        const val EXTRA_EXERCISE_NAME = "EXERCISE_NAME"
    }

    private data class FeedbackSample(
        @StringRes val label: Int,
        val count: Int,
        @ColorRes val dotColor: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME)
        binding.tvTitle.text = if (exerciseName.isNullOrBlank()) {
            "Forehand Drive"
        } else {
            exerciseName
        }

        binding.tvCleanCount.text = getString(R.string.live_clean_count, 8, 20)
        binding.progressFocus.progress = 40
        binding.tvFlagged.text = getString(R.string.live_flagged_count, 5)

        populateFeedbackRows()
        setupClickListeners()
    }

    private fun populateFeedbackRows() {
        val samples = listOf(
            FeedbackSample(R.string.live_fb_wrist_angle, 3, R.color.ttc_gold_bright),
            FeedbackSample(R.string.live_fb_body_rotation, 2, R.color.ttc_gold_bright),
            FeedbackSample(R.string.live_fb_contact_height, 1, R.color.ttc_amber),
            FeedbackSample(R.string.live_fb_elbow_position, 2, R.color.ttc_gold_bright),
            FeedbackSample(R.string.live_fb_stroke_speed, 1, R.color.ttc_danger)
        )

        samples.forEachIndexed { index, sample ->
            val row = ItemLiveFeedbackRowBinding.inflate(
                layoutInflater,
                binding.containerFeedback,
                false
            )
            row.tvLabel.setText(sample.label)
            row.tvCount.text = "×${sample.count}"
            row.badgePremium.visibility = View.GONE
            row.dot.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, sample.dotColor))

            binding.containerFeedback.addView(row.root)

            if (index > 0) {
                val topMarginPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    8f,
                    resources.displayMetrics
                ).toInt()
                (row.root.layoutParams as? MarginLayoutParams)?.topMargin = topMarginPx
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnEndSession.setOnClickListener { finish() }
        binding.btnPause.setOnClickListener {
            isPaused = !isPaused
            binding.btnPause.setText(
                if (isPaused) R.string.live_resume else R.string.live_pause
            )
        }
    }
}
