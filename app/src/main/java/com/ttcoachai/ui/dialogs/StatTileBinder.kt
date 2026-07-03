package com.ttcoachai.ui.dialogs

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.ttcoachai.R

/**
 * Binds the shared stat-tile layout (view_stat_tile.xml) used by both EndSessionSheet
 * and SessionSummarySheet (Dialogs 14b/14c). One layout, parameterized per-tile — do not
 * fork the layout, see docs/superpowers/specs/2026-07-03-dialogs-14-15-design.md.
 */
object StatTileBinder {
    fun bind(
        tileRoot: View,
        @DrawableRes iconRes: Int,
        value: String,
        label: String,
        @ColorRes valueColorRes: Int = R.color.ttc_text_1,
        @ColorRes iconTintRes: Int = R.color.ttc_text_2,
        valueTextSizeSp: Float = 16f
    ) {
        val context = tileRoot.context
        tileRoot.findViewById<ImageView>(R.id.iv_tile_icon).apply {
            setImageResource(iconRes)
            imageTintList = ContextCompat.getColorStateList(context, iconTintRes)
        }
        tileRoot.findViewById<TextView>(R.id.tv_tile_value).apply {
            text = value
            setTextColor(ContextCompat.getColor(context, valueColorRes))
            textSize = valueTextSizeSp
        }
        tileRoot.findViewById<TextView>(R.id.tv_tile_label).text = label
    }
}
