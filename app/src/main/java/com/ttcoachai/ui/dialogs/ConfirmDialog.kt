package com.ttcoachai.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.ttcoachai.R

/**
 * Reusable centered confirm dialog (Dialog 14a). Bespoke icon-badge + button-row layout,
 * not a stock MaterialAlertDialogBuilder — see design spec for why. Parameterized so it's
 * reusable for future confirms beyond delete.
 */
object ConfirmDialog {
    fun show(
        context: Context,
        @DrawableRes iconRes: Int,
        title: String,
        body: String,
        confirmLabel: String,
        cancelLabel: String,
        destructive: Boolean = true,
        checkboxText: String? = null,
        onConfirm: (checked: Boolean) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ttc_confirm, null)
        val dialog = Dialog(context).apply {
            setContentView(view)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        view.findViewById<ImageView>(R.id.iv_confirm_icon).setImageResource(iconRes)
        view.findViewById<TextView>(R.id.tv_confirm_title).text = title
        view.findViewById<TextView>(R.id.tv_confirm_body).text = body

        val checkbox = view.findViewById<MaterialCheckBox>(R.id.cb_confirm_option)
        if (checkboxText == null) {
            checkbox.visibility = android.view.View.GONE
        } else {
            checkbox.text = checkboxText
            checkbox.visibility = android.view.View.VISIBLE
            checkbox.isChecked = true
        }

        val confirmButton = view.findViewById<MaterialButton>(R.id.btn_confirm_ok).apply {
            text = confirmLabel
        }

        view.findViewById<MaterialButton>(R.id.btn_confirm_cancel).apply {
            text = cancelLabel
            setOnClickListener { dialog.dismiss() }
        }
        confirmButton.setOnClickListener {
            dialog.dismiss()
            onConfirm(checkbox.isChecked)
        }

        dialog.show()
    }
}
