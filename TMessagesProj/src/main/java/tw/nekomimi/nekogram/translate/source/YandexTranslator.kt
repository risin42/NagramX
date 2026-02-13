package tw.nekomimi.nekogram.translate.source

import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.translate.HTMLKeeper
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.utils.HttpClient
import java.io.IOException
import java.util.UUID

object YandexTranslator : Translator {

    val uuid: String = UUID.randomUUID().toString().replace("-", "")

    private val httpClient = HttpClient.instance

    override suspend fun doTranslate(
        from: String, to: String, query: String, entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {

        val uuid2 = UUID.randomUUID().toString().replace("-", "")

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

        val url = "https://translate.yandex.net/api/v1/tr.json/translate?srv=android&uuid=$uuid&id=$uuid2-9-0"

        val formBody = FormBody.Builder().add("text", textToTranslate)
            .add("lang", if (from == "auto") to else "$from-$to").build()

        val request = Request.Builder().url(url).header(
            "User-Agent",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1"
        ).post(formBody).build()

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
            error("Yandex API request failed due to network issue: ${e.message}")
        } catch (e: Exception) {
            error("An unexpected error occurred during Yandex API call: ${e.message}")
        }

        try {
            val respObj = JSONObject(responseBodyString)
            if (respObj.optInt("code", -1) != 200) {
                error(respObj.toString(4))
            }
            val translatedText = respObj.getJSONArray("text").getString(0)
            finalString.append(translatedText)
        } catch (e: JSONException) {
            error("Yandex API response parsing failed: ${e.message}")
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
}
