package com.zfdang.chess.views

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import java.io.IOException
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.zfdang.chess.BuildConfig
import com.zfdang.chess.R
import com.zfdang.chess.engine.EngineAssets
import com.zfdang.chess.utils.WindowInsetsUtil

/** Offline reading pages. External project links open only when the user selects them. */
class InformationActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_information)
        WindowInsetsUtil.apply(this, findViewById(R.id.information_root))
        content = findViewById(R.id.information_content)
        findViewById<View>(R.id.information_back).setOnClickListener { finish() }
        savedInstanceState?.keySet()?.filter { it.startsWith("section:") }?.forEach {
            expandedSections[it.removePrefix("section:")] = savedInstanceState.getBoolean(it)
        }
        val page = intent.getStringExtra(EXTRA_PAGE) ?: HELP
        val title = when (page) {
            ABOUT -> getString(R.string.info_about_title)
            PRIVACY -> getString(R.string.info_privacy_title)
            else -> getString(R.string.info_help_title)
        }
        findViewById<TextView>(R.id.information_title).text = title
        when (page) { ABOUT -> about(); PRIVACY -> privacy(); else -> help() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun text(value: CharSequence, size: Float = 16f, color: Int = R.color.ui_ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(getColor(color))
            setLineSpacing(dp(5).toFloat(), 1f)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    private fun heading(title: String, subtitle: String) {
        content.addView(text(title, 32f, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            ViewCompat.setAccessibilityHeading(this, true)
        })
        content.addView(text(subtitle, 15f, R.color.ui_muted).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(24) }
        })
    }
    private fun card(title: String, body: CharSequence, key: String, collapsible: Boolean = false, initiallyOpen: Boolean = true) {
        val frame = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.ui_line)
            setCardBackgroundColor(getColor(R.color.ui_surface))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(16))
        }
        val label = text(title, 18f, bold = true).apply {
            minHeight = dp(48)
            gravity = android.view.Gravity.CENTER_VERTICAL
            ViewCompat.setAccessibilityHeading(this, true)
        }
        val paragraph = text(body).apply {
            setTextIsSelectable(true)
            setLinkTextColor(getColor(R.color.ui_red))
            movementMethod = LinkMovementMethod.getInstance()
        }
        if (collapsible) {
            val indicator = text("+", 24f, R.color.ui_muted).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                gravity = android.view.Gravity.CENTER
            }
            val header = LinearLayout(this).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                isFocusable = true
                contentDescription = title
                ViewCompat.setAccessibilityHeading(this, true)
                val background = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true)
                setBackgroundResource(background.resourceId)
                addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                addView(indicator, LinearLayout.LayoutParams(dp(32), -1))
            }
            label.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            column.addView(header)
            fun expanded(open: Boolean) {
                paragraph.visibility = if (open) View.VISIBLE else View.GONE
                indicator.text = if (open) "−" else "+"
                ViewCompat.setStateDescription(header, getString(if (open) R.string.info_expanded else R.string.info_collapsed))
                expandedSections[key] = open
            }
            expanded(expandedSections[key] ?: initiallyOpen)
            header.setOnClickListener { expanded(paragraph.visibility != View.VISIBLE) }
        } else column.addView(label)
        column.addView(paragraph)
        frame.addView(column)
        content.addView(frame)
    }
    private val expandedSections = mutableMapOf<String, Boolean>()

    override fun onSaveInstanceState(outState: Bundle) {
        expandedSections.forEach { (key, value) -> outState.putBoolean("section:$key", value) }
        super.onSaveInstanceState(outState)
    }
    private fun link(title: String, uri: String) {
        content.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "$title  ↗"
            minHeight = dp(52)
            cornerRadius = dp(16)
            setTextColor(getColor(R.color.ui_red))
            strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.ui_line))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            setOnClickListener {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, R.string.info_no_browser, Toast.LENGTH_SHORT).show() }
            }
        })
    }
    private fun help() {
        heading(getString(R.string.help_heading), getString(R.string.help_heading_sub))
        val sections = listOf(
            getString(R.string.help_play_title) to getString(R.string.help_play_body),
            getString(R.string.help_hint_title) to getString(R.string.help_hint_body),
            getString(R.string.help_search_title) to getString(R.string.help_search_body),
            getString(R.string.help_fen_title) to getString(R.string.help_fen_body),
            getString(R.string.help_manual_title) to getString(R.string.help_manual_body),
            getString(R.string.help_engine_title) to getString(R.string.help_engine_body),
            getString(R.string.help_feedback_title) to getString(R.string.help_feedback_body)
        )
        sections.forEachIndexed { index, (title, body) -> card(title, body, "help-$index", true, index == 0) }
        link(getString(R.string.link_feedback), "$PROJECT/issues")
    }
    private fun about() {
        heading(getString(R.string.about_heading), getString(R.string.about_heading_sub))
        card(getString(R.string.about_intro_title), getString(R.string.about_intro_body), "intro")
        card(getString(R.string.about_version_title), getString(R.string.about_version_body, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, EngineAssets.VERSION), "version")
        card(getString(R.string.about_credits_title), getString(R.string.about_credits_body), "credits")
        link(getString(R.string.link_homepage), "https://fish.zfdang.com/")
        link(getString(R.string.link_source), PROJECT)
        link(getString(R.string.link_releases), "$PROJECT/releases")
    }
    private fun privacy() {
        // The same document is published on the website and bundled by the build for offline use.
        val assetName = privacyAssetName()
        val html = try {
            assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: IOException) {
            Log.w("InformationActivity", "Cannot read bundled privacy policy", error)
            heading(getString(R.string.privacy_heading), getString(R.string.privacy_heading_sub))
            card(getString(R.string.privacy_unavailable_title), getString(R.string.privacy_unavailable_body), "privacy-unavailable")
            link(getString(R.string.link_privacy_web), "https://fish.zfdang.com/privacy.html")
            return
        }
        val updated = Regex("(?:更新日期|Ngày cập nhật)[：:\\s]+([^<]+)", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.trim().orEmpty()
        val date = if (updated.isNotEmpty()) getString(R.string.privacy_updated_suffix, updated) else ""
        heading(getString(R.string.privacy_heading), getString(R.string.privacy_heading_offline, date))
        val article = Regex("<section\\b[^>]*>(.*?)</section>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1) ?: html
        val titles = Regex("<h4\\b[^>]*>(.*?)</h4>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(article).toList()
        if (titles.isEmpty()) {
            card(getString(R.string.privacy_full_title), HtmlCompat.fromHtml(article, HtmlCompat.FROM_HTML_MODE_LEGACY).trim(), "privacy-full")
            return
        }
        titles.forEachIndexed { index, match ->
            val end = titles.getOrNull(index + 1)?.range?.first ?: article.length
            val body = article.substring(match.range.last + 1, end)
            card(HtmlCompat.fromHtml(match.groupValues[1], HtmlCompat.FROM_HTML_MODE_LEGACY).toString(),
                HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_LEGACY).trim(), "privacy-$index")
        }
    }
    private fun privacyAssetName(): String {
        val language = resources.configuration.locales[0].language
        return if (language.startsWith("zh")) "documents/privacy.html" else "documents/privacy-vi.html"
    }
    companion object {
        const val EXTRA_PAGE = "page"
        const val HELP = "help"
        const val ABOUT = "about"
        const val PRIVACY = "privacy"
        private const val PROJECT = "https://github.com/zfdang/chinese-chess-fish-android"
    }
}
