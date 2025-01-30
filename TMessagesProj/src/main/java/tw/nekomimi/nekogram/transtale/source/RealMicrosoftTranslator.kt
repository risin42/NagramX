package tw.nekomimi.nekogram.transtale.source

import java.io.IOException
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import tw.nekomimi.nekogram.transtale.Translator
import tw.nekomimi.nekogram.transtale.source.raw.MicrosoftTranslatorRaw

object RealMicrosoftTranslator : Translator {

    private val targetLanguages = listOf(
        "af", "sq", "am", "ar", "az", "bn", "bs", "bg", "ca", "zh-Hans", "zh-Hant",
        "hr", "cs", "da", "nl", "en", "et", "fil", "fi", "fr", "de", "el", "gu",
        "ht", "he", "hi", "hu", "is", "id", "it", "ja", "kn", "kk", "km", "ko",
        "ku", "lo", "lv", "lt", "mg", "ms", "ml", "mt", "mr", "ne", "nb", "fa",
        "pl", "pt", "ro", "ru", "sr-Cyrl", "sr-Latn", "sk", "sl", "es", "sw",
        "sv", "ta", "te", "th", "tr", "uk", "ur", "vi"
    )

    private val rawTranslator = MicrosoftTranslatorRaw()

    override suspend fun doTranslate(from: String, to: String, query: String): String {

        var fromLang = if (from == "auto") "" else from
        var toLang =
            when (to) {
                "zh" -> "zh-Hans"
                "zh-CN" -> "zh-Hans"
                "zh-TW" -> "zh-Hant"
                else -> to
            }

        if (toLang.lowercase() !in targetLanguages.map { it.lowercase() }) {
            throw UnsupportedOperationException(getString(R.string.TranslateApiUnsupported) + " " + to)
        }

        try {
            return rawTranslator.translate(query, fromLang, toLang)
        } catch (e: IOException) {
            error(e.message ?: "Failed to translate")
        }
    }
}