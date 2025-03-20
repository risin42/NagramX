package tw.nekomimi.nekogram.transtale.source

import cn.hutool.http.HttpUtil
import org.json.JSONObject
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.transtale.HTMLKeeper
import tw.nekomimi.nekogram.transtale.Translator

object GoogleTranslator : Translator {

    override suspend fun doTranslate(
        from: String,
        to: String,
        query: String,
        entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {

        if (to.lowercase() !in targetLanguages.map { it.lowercase() }) {
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

        val translatedText = translate(textToTranslate, from, to)
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
    }

    private fun translate(
        textToTranslate: String,
        from: String,
        to: String
    ): String {
        val url = "https://translate-pa.googleapis.com/v1/translate"
        val params = mapOf(
            "params.client" to "gtx",
            "query.source_language" to if (from.isEmpty() || from == "auto") "auto" else from,
            "query.target_language" to to,
            "query.display_language" to "en-US",
            "data_types" to "TRANSLATION",
            "key" to "AIzaSyDLEeFI5OtFBwYBIoK_jj5m32rZK5CkCXA",
            "query.text" to textToTranslate
        )

        val response = HttpUtil.createGet(url)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
            )
            .form(params)
            .execute()

        if (response.status != 200) {
            error("HTTP ${response.status} : ${response.body()}")
        }

        val jsonObject = JSONObject(response.body())
        if (jsonObject.isNull("translation")) error(jsonObject.toString(4))
        val translatedText = jsonObject.getString("translation")

        return translatedText
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