package tw.nekomimi.nekogram.utils

import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.URLSpan
import android.view.View
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.URLSpanNoUnderline
import java.io.File


object LocaleUtil {

    @JvmField
    val cacheDir = File(ApplicationLoader.applicationContext.cacheDir, "builtIn_lang_export")

    fun formatWithURLs(charSequence: CharSequence): CharSequence {
        val spannable: Spannable = SpannableString(charSequence)
        val spans = spannable.getSpans(0, charSequence.length, URLSpan::class.java)
        for (urlSpan in spans) {
            var span = urlSpan
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            spannable.removeSpan(span)
            span = object : URLSpanNoUnderline(span.url) {
                override fun onClick(widget: View) {
                    super.onClick(widget)
                }
            }
            spannable.setSpan(span, start, end, 0)
        }
        return spannable
    }

    fun htmlToString(text: String?): CharSequence {
        val htmlParsed: Spannable =
            SpannableString(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY))

        return formatWithURLs(htmlParsed)
    }
}
