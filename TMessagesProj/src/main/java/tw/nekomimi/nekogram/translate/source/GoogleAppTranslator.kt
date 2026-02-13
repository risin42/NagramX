package tw.nekomimi.nekogram.translate.source

import android.text.TextUtils
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.translate.HTMLKeeper
import tw.nekomimi.nekogram.translate.TransUtils
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.utils.HttpClient
import xyz.nextalone.nagram.NaConfig
import java.io.IOException

object GoogleAppTranslator : Translator {

    private val httpClient = HttpClient.instance

    override suspend fun doTranslate(
        from: String, to: String, query: String, entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {

        if (NaConfig.googleTranslateExp.Bool()) {
            return GoogleTranslator.doTranslate(
                from, to, query, entities
            )
        }

        if (!TextUtils.isEmpty(NekoConfig.googleCloudTranslateKey.String())) {
            return GoogleCloudTranslator.doTranslate(
                from, to, query, entities
            )
        }

        if (to.lowercase() !in targetLanguages.map { it.lowercase() }) {
            throw UnsupportedOperationException(getString(R.string.TranslateApiUnsupported) + " " + to)
        }

        val originalText = TLRPC.TL_textWithEntities().apply {
            this.text = query
            this.entities = entities
        }

        val finalString = StringBuilder()

        val textToTranslate = if (entities.isNotEmpty()) HTMLKeeper.entitiesToHtml(
            query,
            entities,
            false
        ) else query

        val url = "https://translate.google.com" + "/translate_a/single?dj=1" +
                "&q=" + TransUtils.encodeURIComponent(textToTranslate) +
                "&sl=auto" +
                "&tl=" + to +
                "&ie=UTF-8&oe=UTF-8&client=at&dt=t&otf=2"

        val request = Request.Builder().url(url).header(
            "User-Agent",
            "GoogleTranslate/6.14.0.04.343003216 (Linux; U; Android 10; Redmi K20 Pro)"
        ).get().build()

        val responseBodyString: String
        try {
            responseBodyString = httpClient.newCall(request).await().use { response ->
                val bodyString = response.body.string()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code} : $bodyString")
                }
                bodyString
            }
        } catch (e: IOException) {
            error("Google Translate API request failed due to network issue: ${e.message}")
        } catch (e: Exception) {
            error("An unexpected error occurred during Google Translate API call: ${e.message}")
        }

        try {
            val array = JSONObject(responseBodyString).getJSONArray("sentences")
            for (index in 0 until array.length()) {
                finalString.append(array.getJSONObject(index).getString("trans"))
            }
        } catch (e: JSONException) {
            error("Google Translate API response parsing failed: ${e.message}")
        }

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
    }

    private val targetLanguages = listOf(
            "sq", "ar", "am", "az", "ga", "et", "eu", "be", "bg", "is", "pl", "bs", "fa",
            "af", "da", "de", "ru", "fr", "tl", "fi", "fy", "km", "ka", "gu", "kk", "ht",
            "ko", "ha", "nl", "ky", "gl", "ca", "cs", "kn", "co", "hr", "ku", "la", "lv",
            "lo", "lt", "lb", "ro", "mg", "mt", "mr", "ml", "ms", "mk", "mi", "mn", "bn",
            "my", "hmn", "xh", "zu", "ne", "no", "pa", "pt", "ps", "ny", "ja", "sv", "sm",
            "sr", "st", "si", "eo", "sk", "sl", "sw", "gd", "ceb", "so", "tg", "te", "ta",
            "th", "tr", "cy", "ur", "uk", "uz", "es", "iw", "el", "haw", "sd", "hu", "sn",
            "hy", "ig", "it", "yi", "hi", "su", "id", "jw", "en", "yo", "vi", "zh-TW", "zh-CN", "zh")
}
