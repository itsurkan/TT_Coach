package com.ttcoachai.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.use
import com.ttcoachai.R

class TtcStepperView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val minusBtn: TextView
    private val plusBtn: TextView
    private val valueLabel: TextView
    private var range = StepperRange(0.0, 100.0, 1.0, 0)
    private var suffix = ""

    var onValueChanged: ((Double) -> Unit)? = null
    var value: Double = 0.0
        set(v) { field = range.clamp(v); render() }

    init {
        orientation = HORIZONTAL
        setBackgroundResource(R.drawable.bg_stepper_container)
        LayoutInflater.from(context).inflate(R.layout.view_ttc_stepper, this, true)
        minusBtn = findViewById(R.id.stepper_minus)
        plusBtn = findViewById(R.id.stepper_plus)
        valueLabel = findViewById(R.id.stepper_value)

        var min = 0.0; var max = 100.0; var step = 1.0; var decimals = 0
        context.obtainStyledAttributes(attrs, R.styleable.TtcStepperView).use { a ->
            min = a.getFloat(R.styleable.TtcStepperView_ttcMin, 0f).toDouble()
            max = a.getFloat(R.styleable.TtcStepperView_ttcMax, 100f).toDouble()
            step = a.getFloat(R.styleable.TtcStepperView_ttcStep, 1f).toDouble()
            decimals = a.getInt(R.styleable.TtcStepperView_ttcDecimals, 0)
            suffix = a.getString(R.styleable.TtcStepperView_ttcSuffix) ?: ""
        }
        range = StepperRange(min, max, step, decimals)

        minusBtn.setOnClickListener { setAndEmit(range.dec(value)) }
        plusBtn.setOnClickListener { setAndEmit(range.inc(value)) }
        render()
    }

    fun configure(range: StepperRange, unitSuffix: String, initial: Double) {
        this.range = range; this.suffix = unitSuffix; value = initial
    }

    private fun setAndEmit(v: Double) { value = v; onValueChanged?.invoke(v) }
    private fun render() { valueLabel.text = range.format(value, suffix) }
}
