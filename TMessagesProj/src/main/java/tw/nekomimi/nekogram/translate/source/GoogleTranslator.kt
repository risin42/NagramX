package tw.nekomimi.nekogram.translate.source

import androidx.core.util.component1
import androidx.core.util.component2
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.translate.HTMLKeeper
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.translate.source.fallback.GoogleTranslatorNeko
import java.io.IOException
import java.util.concurrent.TimeUnit

object GoogleTranslator : Translator {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()

    override suspend fun doTranslate(
        from: String, to: String, query: String, entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {
        if (to.lowercase() !in targetLanguages.map { it.lowercase() }) {
            throw UnsupportedOperationException(getString(R.string.TranslateApiUnsupported) + " " + to)
        }

        val originalText = TLRPC.TL_textWithEntities().apply {
            text = query
            this.entities = entities
        }

        val textToTranslate = if (entities.isNotEmpty()) HTMLKeeper.entitiesToHtml(
            query,
            entities,
            false
        ) else query

        val translated = try {
            translate(textToTranslate, from, to)
        } catch (e: RuntimeException) {
            try {
                FileLog.e("Cloud Translation API request failed, trying to use Nekogram's translation API...", e)
                GoogleTranslatorNeko.translate(textToTranslate, from, to)
            } catch (e: Exception) {
                error("Cloud Translation API request failed: ${e.message}")
            }
        }

        val finalText: TLRPC.TL_textWithEntities
        if (entities.isNotEmpty()) {
            val (plain, newEntities) = HTMLKeeper.htmlToEntities(translated, entities, false)
            val tempEntitiesText = TLRPC.TL_textWithEntities().apply {
                text = plain
                this.entities = newEntities
            }
            finalText = TranslateAlert2.preprocess(originalText, tempEntitiesText)
        } else {
            finalText = TLRPC.TL_textWithEntities().apply { text = translated }
        }

        return finalText
    }

    private suspend fun translate(
        text: String, from: String, to: String
    ): String {
        val urlBuilder = HttpUrl.Builder().scheme("https").host("translate-pa.googleapis.com")
            .addPathSegments("v1/translate").addQueryParameter("params.client", "gtx")
            .addQueryParameter(
                "query.source_language", if (from.isEmpty() || from == "auto") "auto" else from
            ).addQueryParameter("query.target_language", to)
            .addQueryParameter("query.display_language", "en-US")
            .addQueryParameter("data_types", "TRANSLATION")
            .addQueryParameter("key", "AIzaSyDLEeFI5OtFBwYBIoK_jj5m32rZK5CkCXA")
            .addQueryParameter("query.text", text)
        val httpUrl = urlBuilder.build()

        val request: Request = Request.Builder().url(httpUrl).get().header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9").header(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
            ).build()

        try {
            val responseBodyString = httpClient.newCall(request).await().use { response ->
                val bodyString = response.body.string()
                if (!response.isSuccessful) {
                    throw RuntimeException("HTTP ${response.code} : $bodyString")
                }
                bodyString
            }

            val json = JSONObject(responseBodyString)
            if (json.isNull("translation")) {
                error("Google API response missing 'translation' field: ${json.toString(2)}")
            }
            return json.getString("translation")
        } catch (e: IOException) {
            throw RuntimeException("Google API request failed due to network issue: ${e.message}", e)
        } catch (e: JSONException) {
            throw RuntimeException("Google API response parsing failed: ${e.message}", e)
        }
    }

    private val targetLanguages = listOf(
        "aa", "ab", "ace", "ach", "af", "ak", "alz", "am", "ar", "as", "av", "awa", "ay", "az",
        "ba", "bal", "ban", "bbc", "bci", "be", "bem", "ber", "ber-Latn", "bew", "bg", "bho",
        "bik", "bm", "bm-Nkoo", "bn", "bo", "br", "bs", "bts", "btx", "bua", "ca", "ce",
        "ceb", "cgg", "ch", "chk", "chm", "ckb", "cnh", "co", "crh", "crh-Latn", "crs", "cs",
        "cv", "cy", "da", "de", "din", "doi", "dov", "dv", "dyu", "dz", "ee", "el", "en",
        "eo", "es", "et", "eu", "fa", "fa-AF", "ff", "fi", "fj", "fo", "fon", "fr", "fr-CA",
        "fur", "fy", "ga", "gaa", "gd", "gl", "gn", "gom", "gu", "gv", "ha", "haw", "hi",
        "hil", "hmn", "hr", "hrx", "ht", "hu", "hy", "iba", "id", "ig", "ilo", "is", "it",
        "iu", "iu-Latn", "iw", "ja", "jam", "jw", "ka", "kac", "kek", "kg", "kha", "kk",
        "kl", "km", "kn", "ko", "kr", "kri", "ktu", "ku", "kv", "ky", "la", "lb", "lg",
        "li", "lij", "lmo", "ln", "lo", "lt", "ltg", "lua", "luo", "lus", "lv", "mad", "mai",
        "mak", "mam", "mfe", "mg", "mh", "mi", "min", "mk", "ml", "mn", "mni-Mtei", "mr",
        "ms", "ms-Arab", "mt", "mwr", "my", "ndc-ZW", "ne", "new", "nhe", "nl", "no", "nr",
        "nso", "nus", "ny", "oc", "om", "or", "os", "pa", "pa-Arab", "pag", "pam", "pap",
        "pl", "ps", "pt", "pt-PT", "qu", "rn", "ro", "rom", "ru", "rw", "sa", "sah", "sat",
        "sat-Latn", "scn", "sd", "se", "sg", "shn", "si", "sk", "sl", "sm", "sn", "so",
        "sq", "sr", "ss", "st", "su", "sus", "sv", "sw", "szl", "ta", "tcy", "te", "tet",
        "tg", "th", "ti", "tiv", "tk", "tl", "tn", "to", "tpi", "tr", "trp", "ts", "tt",
        "tum", "ty", "tyv", "udm", "ug", "uk", "ur", "uz", "ve", "vec", "vi", "war", "wo",
        "xh", "yi", "yo", "yua", "yue", "zap", "zh", "zh-CN", "zh-TW", "zu"
    )
}