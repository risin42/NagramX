package tw.nekomimi.nekogram.translate.source

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.translate.HTMLKeeper
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.utils.HttpClient
import java.io.IOException
import java.util.Date
import java.util.UUID

object TranSmartTranslator : Translator {

    private val httpClient = HttpClient.instance

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
        from: String, to: String, query: String, entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {

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

        val sourceJsonArray = JSONArray()
        for (s in textToTranslate.split("\n")) {
            sourceJsonArray.put(s)
        }

        val requestJsonPayload = JSONObject().apply {
            put("header", JSONObject().apply {
                put(
                    "client_key",
                    "browser-chrome-${getRandomBrowserVersion()}-${getRandomOperatingSystem()}-${UUID.randomUUID()}-${Date().time}"
                )
                put("fn", "auto_translation")
                put("session", "")
                put("user", "")
            })
            put("source", JSONObject().apply {
                put("lang", if (targetLanguages.contains(from)) from else "en")
                put("text_list", sourceJsonArray)
            })
            put("target", JSONObject().apply {
                put("lang", to)
            })
            put("model_category", "normal")
            put("text_domain", "")
            put("type", "plain")
        }.toString()

        val requestBody = requestJsonPayload.toRequestBody(HttpClient.MEDIA_TYPE_JSON)

        val request = Request.Builder().url("https://transmart.qq.com/api/imt").header(
            "User-Agent",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1"
        ).post(requestBody).build()

        try {
            val responseString = httpClient.newCall(request).await().use { response ->
                val bodyString = response.body.string()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code} : $bodyString")
                }
                bodyString
            }

            val targetJsonArray: JSONArray =
                JSONObject(responseString).getJSONArray("auto_translation")
            for (i in 0 until targetJsonArray.length()) {
                finalString.append(targetJsonArray.getString(i))
                if (i != targetJsonArray.length() - 1) {
                    finalString.append("\n")
                }
            }
        } catch (e: IOException) {
            error("TranSmart API request failed due to network issue: ${e.message}")
        } catch (e: JSONException) {
            error("TranSmart API response parsing failed: ${e.message}")
        } catch (e: Exception) {
            error("An unexpected error occurred during translation: ${e.message}")
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
