package com.ttcoachai

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.ttcoachai.databinding.ActivityHelpSupportBinding
import com.ttcoachai.databinding.ItemFaqEntryBinding
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.help.HelpContentCatalog

/**
 * In-app Help & Support screen: an expandable FAQ (content from the shared
 * [HelpContentCatalog], so it's ready to reuse on iOS later) plus a
 * "Contact support" row that opens an email intent pre-filled with the app
 * version and device info.
 */
class HelpSupportActivity : BaseActivity() {

    private lateinit var binding: ActivityHelpSupportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarHelpSupport.setNavigationOnClickListener {
            finish()
        }

        populateFaq()
        setupContactSupport()
    }

    /** Maps the app's current locale to the shared catalog's language enum ("uk" -> UA, else EN). */
    private fun currentLang(): FeedbackLang =
        if (LocaleHelper.getSavedLanguage(this) == "uk") FeedbackLang.UA else FeedbackLang.EN

    private fun populateFaq() {
        binding.layoutFaqContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val entries = HelpContentCatalog.faq(currentLang())

        entries.forEach { entry ->
            val itemBinding = ItemFaqEntryBinding.inflate(inflater, binding.layoutFaqContainer, false)
            itemBinding.tvFaqQuestion.text = entry.question
            itemBinding.tvFaqAnswer.text = entry.answer

            itemBinding.layoutFaqQuestion.setOnClickListener {
                toggleFaqEntry(itemBinding.tvFaqAnswer, itemBinding.ivFaqChevron)
            }

            binding.layoutFaqContainer.addView(itemBinding.root)
        }
    }

    private fun toggleFaqEntry(answerView: TextView, chevron: ImageView) {
        val expanding = answerView.visibility != View.VISIBLE
        answerView.visibility = if (expanding) View.VISIBLE else View.GONE
        chevron.setImageResource(if (expanding) R.drawable.icn_chevron_up else R.drawable.ic_chevron_down)
    }

    private fun setupContactSupport() {
        binding.layoutContactSupport.setOnClickListener {
            openSupportEmail()
        }
    }

    private fun openSupportEmail() {
        val subject = getString(R.string.help_support_email_subject)
        val body = getString(
            R.string.help_support_email_body,
            BuildConfig.VERSION_NAME,
            Build.VERSION.RELEASE ?: "",
            Build.VERSION.SDK_INT
        )

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.help_support_no_email_app, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val SUPPORT_EMAIL = "itsurkan.1@gmail.com"
    }
}
