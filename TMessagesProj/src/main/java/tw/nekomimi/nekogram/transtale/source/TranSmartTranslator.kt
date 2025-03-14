package tw.nekomimi.nekogram.transtale.source

import cn.hutool.http.HttpUtil
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.transtale.HTMLKeeper
import tw.nekomimi.nekogram.transtale.Translator
import java.util.Date
import java.util.UUID

object TranSmartTranslator : Translator {

    private fun getRandomBrowserVersion(): String {
        val majorVersion = (Math.random() * 17).toInt() + 100
        val minorVersion = (Math.random() * 20).toInt()
        val patchVersion = (Math.random() * 20).toInt()
        return "$majorVersion.$minorVersion.$patchVersion"
    }

    private fun getRandomOperatingSystem(): String {
        val operatingSystems = arrayOf("Mac OS", "Windows")
        val randomIndex = (Math.random() * operatingSystems.size).toInt()
        return operatingSystems[randomIndex]
    }

    override suspend fun doTranslate(
        from: String,
        to: String,
        query: String,
        entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {

        if (to !in targetLanguages) {
            error(getString(R.string.TranslateApiUnsupported))
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

        val source = JSONArray()
        for (s in textToTranslate.split("\n")) {
            source.put(s)
        }

        val response = HttpUtil.createPost("https://transmart.qq.com/api/imt")
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1")
            .body(JSONObject().apply {
                put("header", JSONObject().apply{
                    put("client_key", "browser-chrome-${getRandomBrowserVersion()}-${getRandomOperatingSystem()}-${UUID.randomUUID()}-${Date().time}")
                    put("fn", "auto_translation")
                    put("session", "")
                    put("user", "")
                })
                put("source", JSONObject().apply {
                    put("lang", if (targetLanguages.contains(from)) from else "en")
                    put("text_list", source)
                })
                put("target", JSONObject().apply {
                    put("lang", to)
                })
                put("model_category", "normal")
                put("text_domain", "")
                put("type", "plain")
            }.toString())
            .execute()

        if (response.status != 200) {
            error("HTTP ${response.status} : ${response.body()}")
        }

        val target: JSONArray = JSONObject(response.body()).getJSONArray("auto_translation")
        for (i in 0 until target.length()) {
            finalString.append(target.getString(i))
            if (i != target.length() - 1) {
                finalString.append("\n")
            }
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
        "ar", "fr", "fil", "lo", "ja", "it", "hi", "id", "vi", "de", "km", "ms", "th", "tr", "zh", "ru", "ko", "pt", "es"
    )
}
