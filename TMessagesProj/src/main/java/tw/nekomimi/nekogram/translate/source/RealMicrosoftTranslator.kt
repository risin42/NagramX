package tw.nekomimi.nekogram.translate.source

import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.translate.HTMLKeeper
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.translate.source.raw.MicrosoftTranslatorRaw
import java.io.IOException

object RealMicrosoftTranslator : Translator {

    private val rawTranslator = MicrosoftTranslatorRaw()

    override suspend fun doTranslate(
        from: String,
        to: String,
        query: String,
        entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {

        val fromLang = if (from == "auto") "" else from
        val toLang = when (to) {
            "zh" -> "zh-Hans"
            "zh-CN" -> "zh-Hans"
            "zh-TW" -> "zh-Hant"
            else -> to
        }

        if (toLang.lowercase() !in targetLanguages.map { it.lowercase() }) {
            throw UnsupportedOperationException(getString(R.string.TranslateApiUnsupported) + " " + to)
        }

        val originalText = TLRPC.TL_textWithEntities()
        originalText.text = query
        originalText.entities = entities

        val finalString = StringBuilder()

        val textToTranslate = if (entities.isNotEmpty()) HTMLKeeper.entitiesToHtml(
            query,
            entities,
            false
        ) else query

        try {
            val translatedText = rawTranslator.translate(textToTranslate, fromLang, toLang)
            finalString.append(translatedText)

            var finalText = TLRPC.TL_textWithEntities()
            if (entities.isNotEmpty()) {
                val resultPair = HTMLKeeper.htmlToEntities(finalString.toString(), entities, false)
                finalText.text = resultPair.first
                finalText.entities = resultPair.second
                finalText = TranslateAlert2.preprocess(originalText, finalText)
            } else {
                finalText.text = finalString.toString()
            }

            return finalText
        } catch (e: IOException) {
            error(e.message ?: "Failed to translate")
        }
    }

    private val targetLanguages = listOf(
        "af", "sq", "am", "ar", "az", "bn", "bs", "bg", "ca", "zh-Hans", "zh-Hant",
        "hr", "cs", "da", "nl", "en", "et", "fil", "fi", "fr", "de", "el", "gu",
        "ht", "he", "hi", "hu", "is", "id", "it", "ja", "kn", "kk", "km", "ko",
        "ku", "lo", "lv", "lt", "mg", "ms", "ml", "mt", "mr", "ne", "nb", "fa",
        "pl", "pt", "ro", "ru", "sr-Cyrl", "sr-Latn", "sk", "sl", "es", "sw",
        "sv", "ta", "te", "th", "tr", "uk", "ur", "vi"
    )
}
