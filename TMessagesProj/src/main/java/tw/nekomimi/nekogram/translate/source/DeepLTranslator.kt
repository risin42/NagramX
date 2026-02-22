package tw.nekomimi.nekogram.translate.source

import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.translate.HTMLKeeper
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.translate.source.fallback.DeepLTranslatorNeko

object DeepLTranslator : Translator {

    override suspend fun doTranslate(
        from: String, to: String, query: String, entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {
        if (to.isEmpty()) {
            throw UnsupportedOperationException(getString(R.string.TranslateApiUnsupported) + " " + to)
        }

        try {
            val originalText = TLRPC.TL_textWithEntities().apply {
                this.text = query
                this.entities = entities
            }

            val textToTranslate = if (entities.isNotEmpty()) HTMLKeeper.entitiesToHtml(
                query, entities, false
            ) else query

            val translatedText = DeepLTranslatorNeko.translate(textToTranslate, from, to)

            val finalString = StringBuilder().append(translatedText)
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
        } catch (e: Exception) {
            error("DeepL API request failed: ${e.message}")
        }
    }

}
