package tw.nekomimi.nekogram.translate.source

import android.os.SystemClock
import android.util.Log
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.telegram.messenger.BuildVars
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.translate.HTMLKeeper
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.utils.HttpClient
import java.io.IOException

object LingoTranslator : Translator {

    private const val NAX = "LingoTranslator"

    private val httpClient = HttpClient.instance

    override suspend fun doTranslate(
        from: String, to: String, query: String, entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: from=$from, to=$to, query=$query")

        if (to !in listOf("zh", "en", "es", "fr", "ja", "ru")) {
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
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: source JSONArray: $sourceJsonArray")

        val requestJsonPayload = JSONObject().apply {
            put("source", sourceJsonArray)
            put("trans_type", "${from}2$to")
            put("request_id", SystemClock.elapsedRealtime().toString())
            put("detect", true)
        }.toString()
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: request body: $requestJsonPayload")

        val requestBody = requestJsonPayload.toRequestBody(HttpClient.MEDIA_TYPE_JSON)

        val request = Request.Builder().url("https://api.interpreter.caiyunai.com/v1/translator")
            .header("X-Authorization", "token 9sdftiq37bnv410eon2l").header(
                "User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1"
            ).post(requestBody).build()

        val responseString: String
        try {
            responseString = httpClient.newCall(request).await().use { response ->
                    val bodyString = response.body.string()
                    if (BuildVars.LOGS_ENABLED) Log.d(
                        NAX, "doTranslate: HTTP response status: ${response.code}"
                    )
                    if (BuildVars.LOGS_ENABLED) Log.d(
                        NAX, "doTranslate: HTTP response body: $bodyString"
                    )
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code} : $bodyString")
                    }
                    bodyString
                }
        } catch (e: IOException) {
            error("Lingo API request failed due to network issue: ${e.message}")
        } catch (e: Exception) {
            error("An unexpected error occurred during Lingo translation: ${e.message}")
        }

        try {
            val target: JSONArray = JSONObject(responseString).getJSONArray("target")
            if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: target JSONArray: $target")

            for (i in 0 until target.length()) {
                var translatedLine = target.getString(i)
                if (translatedLine == "\ud835") { // wtf
                    translatedLine = ""
                    if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: skip invalid character")
                }
                finalString.append(translatedLine)
                if (i != target.length() - 1) {
                    finalString.append("\n")
                }
            }
        } catch (e: JSONException) {
            if (BuildVars.LOGS_ENABLED) Log.e(NAX, "JSONException parsing Lingo API response", e)
            error("Lingo API response parsing failed: ${e.message}")
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
