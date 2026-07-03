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
        onConfirm: () -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ttc_confirm, null)
        val dialog = Dialog(context).apply {
            setContentView(view)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        view.findViewById<ImageView>(R.id.iv_confirm_icon).setImageResource(iconRes)
        view.findViewById<TextView>(R.id.tv_confirm_title).text = title
        view.findViewById<TextView>(R.id.tv_confirm_body).text = body

        val confirmButton = view.findViewById<MaterialButton>(R.id.btn_confirm_ok).apply {
            text = confirmLabel
        }
        // btn_confirm_ok is styled destructive (red) in XML by default. This plan's scope is
        // delete-only (always destructive=true); a future destructive=false caller needs the
        // neutral (gold) styling swapped in at runtime here.
        // TODO: when a non-destructive confirm caller is added, apply TTC.Button.Confirm.Neutral
        // colors (ttc_gold_bright / ttc_on_gold) to confirmButton when destructive == false.

        view.findViewById<MaterialButton>(R.id.btn_confirm_cancel).apply {
            text = cancelLabel
            setOnClickListener { dialog.dismiss() }
        }
        confirmButton.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
    }
}
