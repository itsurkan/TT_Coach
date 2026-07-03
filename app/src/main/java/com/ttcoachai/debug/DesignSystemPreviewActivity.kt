package com.ttcoachai.debug

import android.content.pm.ApplicationInfo
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ttcoachai.R

/**
 * Dev-only visual gallery for the TTC gold-dark design system (Slice 1). Renders color
 * swatches, the type ramp, and each TTC.* component so the foundation can be eyeballed in
 * both light and dark. Exported so `adb shell am start` can launch it directly; runtime
 * FLAG_DEBUGGABLE gate keeps it inert on release builds.
 */
class DesignSystemPreviewActivity : AppCompatActivity() {

    private val tokens = listOf(
        "ttc_canvas" to R.color.ttc_canvas,
        "ttc_sink" to R.color.ttc_sink,
        "ttc_surface" to R.color.ttc_surface,
        "ttc_surface_elevated" to R.color.ttc_surface_elevated,
        "ttc_outline" to R.color.ttc_outline,
        "ttc_outline_strong" to R.color.ttc_outline_strong,
        "ttc_gold_bright" to R.color.ttc_gold_bright,
        "ttc_gold_accent" to R.color.ttc_gold_accent,
        "ttc_gold_deep" to R.color.ttc_gold_deep,
        "ttc_gold_container" to R.color.ttc_gold_container,
        "ttc_on_gold" to R.color.ttc_on_gold,
        "ttc_text_1" to R.color.ttc_text_1,
        "ttc_text_2" to R.color.ttc_text_2,
        "ttc_text_3" to R.color.ttc_text_3,
        "ttc_success" to R.color.ttc_success,
        "ttc_success_soft" to R.color.ttc_success_soft,
        "ttc_error" to R.color.ttc_error,
        "ttc_error_container" to R.color.ttc_error_container,
        "ttc_amber" to R.color.ttc_amber,
        "ttc_cream" to R.color.ttc_cream,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }
        setContentView(R.layout.activity_design_system_preview)
        val container = findViewById<LinearLayout>(R.id.swatchContainer)
        tokens.forEach { (name, colorRes) -> container.addView(swatchRow(name, colorRes)) }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun swatchRow(name: String, colorRes: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        val chipBg = GradientDrawable().apply {
            setColor(ContextCompat.getColor(this@DesignSystemPreviewActivity, colorRes))
            cornerRadius = dp(6).toFloat()
            setStroke(dp(1), ContextCompat.getColor(this@DesignSystemPreviewActivity, R.color.ttc_outline_strong))
        }
        val chip = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(24))
            background = chipBg
        }
        val label = TextView(this).apply {
            text = name
            setTextAppearance(R.style.TextAppearance_TTC_Mono_Meta)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(12) }
        }
        row.addView(chip); row.addView(label)
        return row
    }
}
